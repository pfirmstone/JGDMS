/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.jini.jeri.relay;

import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.AtomicInputValidation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MarshallingFormat;
import net.jini.io.context.ContextPermission;
import net.jini.io.context.ServerSubject;
import net.jini.jeri.OutboundRequest;

/**
 * One request over one relay connection.  See {@link RelayEndpoint} for the
 * wire layout.
 *
 * <p>The constraints are encoded with {@link DerMarshalOutputStream}: one
 * {@code writeObject} of the {@link InvocationConstraints}, then close.  The
 * result is a self-contained DER object stream that the relay reads back with
 * {@code DerMarshalInputStream.readObject}.  This was chosen over {@code
 * DerMarshalledInstance}, which yields a {@code MarshalledInstance} object that
 * would itself still need a stream to become bytes.
 *
 * <p>The opening exchange is bounded by a watchdog: a shared daemon timer
 * closes the channel at the deadline, which makes a blocked write or read
 * fail.  This works on any blocking {@code SocketChannel}, needs no change
 * of blocking mode, and costs no selector per request.
 */
final class RelayOutboundRequest implements OutboundRequest {

    static final int VERSION = 2;
    static final int MAX_CONSTRAINTS_LENGTH = 64 * 1024;
    static final int MAX_STRING_LENGTH = 1024;
    static final int MAX_PRINCIPALS = 16;
    private static final byte[] MAGIC = {'J', 'R', 'L', 'Y'};
    private static final byte[] NO_BYTES = new byte[0];

    private static final String X500_PRINCIPAL_CLASS =
        "javax.security.auth.x500.X500Principal";
    /** The class ssl.Utilities.spiffePrincipalsFromCertificate creates. */
    private static final String SPIFFE_PRINCIPAL_CLASS =
        "au.net.zeus.jgdms.spiffe.SpiffePrincipal";
    /**
     * The SpiffePrincipal(String) constructor, found by the fixed name above
     * through the system class loader as ssl.Utilities does, or null if that
     * class is not on the class path.
     */
    private static final Constructor<?> SPIFFE_PRINCIPAL_CTOR =
        AccessController.doPrivileged(new PrivilegedAction<Constructor<?>>() {
            public Constructor<?> run() {
                try {
                    Class<?> c = Class.forName(SPIFFE_PRINCIPAL_CLASS, false,
                        ClassLoader.getSystemClassLoader());
                    return c.getConstructor(String.class);
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    return null;
                }
            }
        });

    private static final ScheduledThreadPoolExecutor WATCHDOG =
        AccessController.doPrivileged(
            new PrivilegedAction<ScheduledThreadPoolExecutor>() {
                public ScheduledThreadPoolExecutor run() {
                    ScheduledThreadPoolExecutor e =
                        new ScheduledThreadPoolExecutor(1, r -> {
                            Thread t = new Thread(r, "RelayEndpoint handshake watchdog");
                            t.setDaemon(true);
                            t.setContextClassLoader(null);
                            return t;
                        });
                    e.setRemoveOnCancelPolicy(true);
                    return e;
                }
            });

    private final SocketChannel channel;
    private final InvocationConstraints unfulfilled;
    private final Subject serverSubject;
    private final OutputStream requestOut;
    private final InputStream responseIn;
    /** Set once any request byte may have been passed to the relay. */
    private volatile boolean mayHaveSent = false;

    private RelayOutboundRequest(SocketChannel channel,
                                 InvocationConstraints unfulfilled,
                                 Subject serverSubject)
    {
        this.channel = channel;
        this.unfulfilled = unfulfilled;
        this.serverSubject = serverSubject;
        this.requestOut = new RequestOutputStream();
        this.responseIn = new ResponseInputStream();
    }

    /**
     * Connects to the relay, sends the header and reads the reply.  The
     * channel is closed if this method throws.
     */
    static RelayOutboundRequest open(RelayChannels channels,
                                     String destination,
                                     InvocationConstraints constraints,
                                     byte[] token)
        throws IOException
    {
        byte[] header = header(destination, constraints, token);
        InvocationConstraints unfulfilled = invocationLayer(constraints);
        long timeout = channels.handshakeTimeoutMillis();
        final SocketChannel channel = channels.open();
        boolean ok = false;
        try {
            ScheduledFuture<?> watchdog = WATCHDOG.schedule(
                () -> closeQuietly(channel), timeout, TimeUnit.MILLISECONDS);
            Subject subject;
            try {
                writeFully(channel, header, 0, header.length);
                subject = readReply(channel);
            } catch (IOException e) {
                if (watchdog.cancel(false)) {
                    throw e;
                }
                throw timedOut(timeout, e);
            }
            if (!watchdog.cancel(false)) {
                /* The watchdog ran, or is running: the channel is closed. */
                throw timedOut(timeout, null);
            }
            ok = true;
            return new RelayOutboundRequest(channel, unfulfilled, subject);
        } finally {
            if (!ok) {
                closeQuietly(channel);
            }
        }
    }

    private static IOException timedOut(long timeout, IOException cause) {
        return new IOException("relay did not complete the opening exchange"
            + " within " + timeout + " ms", cause);
    }

    static byte[] header(String destination, InvocationConstraints constraints,
                         byte[] token)
        throws IOException
    {
        byte[] tok = token == null ? NO_BYTES : token;
        if (tok.length > RelayCallToken.MAX_LENGTH) {
            throw new IOException("call token too long");
        }
        byte[] dest = destination.getBytes(StandardCharsets.UTF_8);
        if (dest.length > 0xFFFF) {
            throw new IOException("destination too long");
        }
        byte[] cons = encodeConstraints(constraints);
        ByteBuffer buf = ByteBuffer.allocate(
            MAGIC.length + 1 + 2 + dest.length + 4 + cons.length
            + 1 + tok.length);
        buf.put(MAGIC);
        buf.put((byte) VERSION);
        buf.putShort((short) dest.length);
        buf.put(dest);
        buf.putInt(cons.length);
        buf.put(cons);
        buf.put((byte) tok.length);
        buf.put(tok);
        return buf.array();
    }

    static byte[] encodeConstraints(InvocationConstraints constraints)
        throws IOException
    {
        if (constraints.isEmpty()) {
            return NO_BYTES;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(bos)) {
            out.writeObject(constraints);
        }
        byte[] bytes = bos.toByteArray();
        if (bytes.length > MAX_CONSTRAINTS_LENGTH) {
            throw new IOException("encoded constraints exceed "
                + MAX_CONSTRAINTS_LENGTH + " bytes: " + bytes.length);
        }
        return bytes;
    }

    /**
     * Returns the constraints the invocation layer must implement, the same
     * kinds ssl.CallContext hands up: Integrity.YES, AtomicInputValidation.YES
     * and every MarshallingFormat.  A constraint that is both required and
     * preferred is reported only as required.
     */
    static InvocationConstraints invocationLayer(InvocationConstraints c) {
        List<InvocationConstraint> required = new ArrayList<InvocationConstraint>(2);
        List<InvocationConstraint> preferred = new ArrayList<InvocationConstraint>(2);
        for (InvocationConstraint k : c.requirements()) {
            if (isInvocationLayer(k)) {
                required.add(k);
            }
        }
        for (InvocationConstraint k : c.preferences()) {
            if (isInvocationLayer(k) && !required.contains(k)) {
                preferred.add(k);
            }
        }
        if (required.isEmpty() && preferred.isEmpty()) {
            return InvocationConstraints.EMPTY;
        }
        return new InvocationConstraints(required, preferred);
    }

    private static boolean isInvocationLayer(InvocationConstraint k) {
        return k == Integrity.YES || k == AtomicInputValidation.YES
            || k instanceof MarshallingFormat;
    }

    /**
     * Reads the relay's reply.  Returns the server subject, or null if the
     * relay accepted with no authenticated principals.
     */
    private static Subject readReply(SocketChannel channel) throws IOException {
        int status = readFully(channel, 1)[0] & 0xFF;
        if (status != 0) {
            String reason = readString(channel, "refusal reason");
            throw new IOException("relay refused request (status " + status
                + "): " + reason);
        }
        int count = readUnsignedShort(channel);
        if (count > MAX_PRINCIPALS) {
            throw new IOException("relay sent " + count
                + " principals, more than " + MAX_PRINCIPALS);
        }
        if (count == 0) {
            return null;
        }
        Set<Principal> principals = new LinkedHashSet<Principal>();
        for (int i = 0; i < count; i++) {
            String className = readString(channel, "principal class name");
            String name = readString(channel, "principal name");
            principals.add(principal(className, name));
        }
        return new Subject(true, Collections.unmodifiableSet(principals),
            Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Constructs a principal of an allowed class.  The class name from the
     * reply is only compared with fixed names, never loaded.
     */
    private static Principal principal(String className, String name)
        throws IOException
    {
        if (X500_PRINCIPAL_CLASS.equals(className)) {
            try {
                return new X500Principal(name);
            } catch (IllegalArgumentException e) {
                throw new IOException("malformed X500Principal name", e);
            }
        }
        if (SPIFFE_PRINCIPAL_CLASS.equals(className)) {
            if (SPIFFE_PRINCIPAL_CTOR == null) {
                throw new IOException(SPIFFE_PRINCIPAL_CLASS
                    + " is not available to construct a server principal");
            }
            try {
                return (Principal) SPIFFE_PRINCIPAL_CTOR.newInstance(name);
            } catch (InvocationTargetException e) {
                throw new IOException("malformed SpiffePrincipal name",
                    e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IOException("cannot construct SpiffePrincipal", e);
            }
        }
        throw new IOException("relay sent a principal of a class that is not"
            + " accepted: " + className);
    }

    private static int readUnsignedShort(SocketChannel channel)
        throws IOException
    {
        byte[] b = readFully(channel, 2);
        return ((b[0] & 0xFF) << 8) | (b[1] & 0xFF);
    }

    /** Reads a 2-byte length and that many bytes of well-formed UTF-8. */
    private static String readString(SocketChannel channel, String what)
        throws IOException
    {
        int length = readUnsignedShort(channel);
        if (length > MAX_STRING_LENGTH) {
            throw new IOException("relay " + what + " length " + length
                + " exceeds " + MAX_STRING_LENGTH);
        }
        byte[] bytes = readFully(channel, length);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("relay " + what + " is not valid UTF-8", e);
        }
    }

    private static byte[] readFully(SocketChannel channel, int n)
        throws IOException
    {
        ByteBuffer buf = ByteBuffer.allocate(n);
        while (buf.hasRemaining()) {
            if (channel.read(buf) < 0) {
                throw new EOFException(
                    "relay closed the connection during the opening exchange");
            }
        }
        return buf.array();
    }

    private static void writeFully(SocketChannel channel,
                                   byte[] b, int off, int len)
        throws IOException
    {
        ByteBuffer buf = ByteBuffer.wrap(b, off, len);
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    private static void closeQuietly(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
        }
    }

    /**
     * Adds a {@link ServerSubject} holding the principals the relay
     * authenticated for the server, if there were any.
     */
    public void populateContext(Collection context) {
        if (context == null) {
            throw new NullPointerException();
        }
        if (serverSubject != null) {
            context.add(new RelayServerSubject(serverSubject));
        }
    }

    /**
     * Returns the invocation-layer constraints of this request:
     * {@code Integrity.YES}, {@code AtomicInputValidation.YES} and any
     * {@code MarshallingFormat}, requirements and preferences kept apart.  The
     * relay applies the rest to the real connection.
     */
    public InvocationConstraints getUnfulfilledConstraints() {
        return unfulfilled;
    }

    public OutputStream getRequestOutputStream() {
        return requestOut;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closing the returned stream closes the whole connection, including
     * the request direction if it is still open, so it also ends the request
     * as {@link #abort} does.
     */
    public InputStream getResponseInputStream() {
        return responseIn;
    }

    public boolean getDeliveryStatus() {
        return mayHaveSent;
    }

    public void abort() {
        closeQuietly(channel);
    }

    /**
     * The server subject context element.  The ContextPermission guard is
     * the same as in ssl.SslConnection, which has no shared implementation to
     * reuse, so the check is repeated here.
     */
    private static final class RelayServerSubject implements ServerSubject {
        private static final Permission GET_SERVER_SUBJECT_PERM =
            new ContextPermission(
                "net.jini.io.context.ServerSubject.getServerSubject");

        private final Subject subject;

        RelayServerSubject(Subject subject) {
            this.subject = subject;
        }

        @Override
        public Subject getServerSubject() {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(GET_SERVER_SUBJECT_PERM);
            }
            return subject;
        }
    }

    /** Writes straight to the channel; close half-closes the connection. */
    private final class RequestOutputStream extends OutputStream {
        private boolean closed = false;

        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        public synchronized void write(byte[] b, int off, int len)
            throws IOException
        {
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            if (closed) {
                throw new IOException("request stream closed");
            }
            mayHaveSent = true;
            writeFully(channel, b, off, len);
        }

        public synchronized void flush() throws IOException {
            if (closed) {
                throw new IOException("request stream closed");
            }
            mayHaveSent = true;
        }

        public synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            mayHaveSent = true;
            channel.shutdownOutput();
        }
    }

    /**
     * Reads straight from the channel.  Closing it closes the whole
     * connection, request direction included.
     */
    private final class ResponseInputStream extends InputStream {

        public int read() throws IOException {
            byte[] b = new byte[1];
            int n;
            do {
                n = read(b, 0, 1);
            } while (n == 0);
            return n < 0 ? -1 : b[0] & 0xFF;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) {
                return 0;
            }
            return channel.read(ByteBuffer.wrap(b, off, len));
        }

        public void close() {
            closeQuietly(channel);
        }
    }
}
