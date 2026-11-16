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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;

/**
 * Test fixture standing in for the relay process: listens on a Unix domain
 * socket in a temporary directory, parses the opening header, records what it
 * received, and then replies as its mode says.  It is not the real relay.
 */
final class RelayEndpointTestRelay implements Closeable {

    /** Handles an accepted connection after the acceptance status is sent. */
    interface Handler {
        void handle(Received header, InputStream request, OutputStream response)
            throws IOException;
    }

    /** What the relay received on one connection. */
    static final class Received {
        /** Every header byte as read, magic through token bytes. */
        final byte[] rawHeader;
        final String destination;
        final byte[] constraintBytes;
        /** The call token bytes; empty if the header carried none. */
        final byte[] token;
        /** Request bytes up to the client's half-close, if read. */
        volatile byte[] request;
        /** True once the client's end of the connection reached end of stream. */
        volatile boolean sawEndOfStream;

        Received(byte[] rawHeader, String destination, byte[] constraintBytes,
                 byte[] token) {
            this.rawHeader = rawHeader;
            this.destination = destination;
            this.constraintBytes = constraintBytes;
            this.token = token;
        }
    }

    private enum Mode { ECHO, REFUSE, RAW, HOLD, HANDLER, SILENT }

    /** The acceptance reply with no principals. */
    private static final byte[] ACCEPT = {0, 0, 0};

    private final Path dir;
    private final Path socket;
    private final ServerSocketChannel server;
    private final Mode mode;
    private final String reason;
    private final byte[] raw;
    private final byte[] accept;
    private final Handler handler;
    private final BlockingQueue<Received> received =
        new LinkedBlockingQueue<Received>();
    private final BlockingQueue<Received> finished =
        new LinkedBlockingQueue<Received>();
    private final Thread acceptor;

    /** Accepts, then echoes the request bytes back as the response. */
    static RelayEndpointTestRelay echo() throws IOException {
        return new RelayEndpointTestRelay(Mode.ECHO, null, null, ACCEPT, null);
    }

    /**
     * Accepts with the given principals, as alternating class name and
     * principal name, then echoes.
     */
    static RelayEndpointTestRelay echoAs(String... classAndName)
        throws IOException
    {
        return new RelayEndpointTestRelay(Mode.ECHO, null, null,
            acceptReply(classAndName), null);
    }

    /** Reads the header, then never replies; ends when the client closes. */
    static RelayEndpointTestRelay silent() throws IOException {
        return new RelayEndpointTestRelay(Mode.SILENT, null, null, null, null);
    }

    /**
     * Returns the acceptance reply: status 0, principal count, then each
     * class name and principal name with a 2-byte length.
     */
    static byte[] acceptReply(String... classAndName) {
        if (classAndName.length % 2 != 0) {
            throw new IllegalArgumentException("pairs expected");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0);
        int count = classAndName.length / 2;
        bos.write(count >>> 8);
        bos.write(count);
        for (String str : classAndName) {
            writeString(bos, str);
        }
        return bos.toByteArray();
    }

    /** Writes a 2-byte length and the UTF-8 bytes of {@code str}. */
    static void writeString(ByteArrayOutputStream bos, String str) {
        byte[] b = str.getBytes(StandardCharsets.UTF_8);
        bos.write(b.length >>> 8);
        bos.write(b.length);
        bos.write(b, 0, b.length);
    }

    /** Refuses with status 1 and the given reason. */
    static RelayEndpointTestRelay refuse(String reason) throws IOException {
        return new RelayEndpointTestRelay(Mode.REFUSE, reason, null, null, null);
    }

    /** Replies with exactly these bytes after the header, then closes. */
    static RelayEndpointTestRelay rawReply(byte[] reply) throws IOException {
        return new RelayEndpointTestRelay(Mode.RAW, null, reply.clone(), null, null);
    }

    /** Accepts, reads the request to end of stream, sends no response. */
    static RelayEndpointTestRelay hold() throws IOException {
        return new RelayEndpointTestRelay(Mode.HOLD, null, null, ACCEPT, null);
    }

    /** Accepts and passes the connection to the handler. */
    static RelayEndpointTestRelay handler(Handler h) throws IOException {
        return new RelayEndpointTestRelay(Mode.HANDLER, null, null, ACCEPT, h);
    }

    private RelayEndpointTestRelay(Mode mode, String reason, byte[] raw,
                                   byte[] accept, Handler handler)
        throws IOException
    {
        this.mode = mode;
        this.reason = reason;
        this.raw = raw;
        this.accept = accept;
        this.handler = handler;
        ServerSocketChannel ssc;
        try {
            ssc = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        } catch (UnsupportedOperationException e) {
            Assume.assumeNoException("Unix domain sockets not supported", e);
            throw e;
        }
        dir = Files.createTempDirectory("relay");
        socket = dir.resolve("r.sock");
        ssc.bind(UnixDomainSocketAddress.of(socket));
        server = ssc;
        acceptor = new Thread(this::acceptLoop, "RelayEndpointTestRelay");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** The socket path the relay listens on. */
    Path socketPath() {
        return socket;
    }

    /** Opens a new client channel to this relay. */
    SocketChannel connect() throws IOException {
        SocketChannel c = SocketChannel.open(StandardProtocolFamily.UNIX);
        c.connect(UnixDomainSocketAddress.of(socket));
        return c;
    }

    /** A private channel source whose factory connects to this relay. */
    RelayChannels channels() {
        return channels("net.jini.jeri.relay.test.timeout.unused");
    }

    /**
     * A private channel source whose factory connects to this relay, with
     * its handshake time limit read from the named property.
     */
    RelayChannels channels(String timeoutProperty) {
        RelayChannels ch = new RelayChannels(
            "net.jini.jeri.relay.test.unused", timeoutProperty);
        ch.install(this::connect);
        return ch;
    }

    /** The next header received, or null after the timeout. */
    Received nextReceived() throws InterruptedException {
        return received.poll(10, TimeUnit.SECONDS);
    }

    /** The next connection the relay has finished with, or null after the timeout. */
    Received nextFinished() throws InterruptedException {
        return finished.poll(10, TimeUnit.SECONDS);
    }

    private void acceptLoop() {
        while (server.isOpen()) {
            final SocketChannel c;
            try {
                c = server.accept();
            } catch (IOException e) {
                return;
            }
            Thread t = new Thread(() -> serve(c), "RelayEndpointTestRelay-conn");
            t.setDaemon(true);
            t.start();
        }
    }

    private void serve(SocketChannel c) {
        Received r = null;
        try (SocketChannel ch = c) {
            r = readHeader(ch);
            received.add(r);
            OutputStream out = Channels.newOutputStream(ch);
            switch (mode) {
            case REFUSE: {
                byte[] b = reason.getBytes(StandardCharsets.UTF_8);
                out.write(1);
                out.write(b.length >>> 8);
                out.write(b.length);
                out.write(b);
                break;
            }
            case RAW:
                out.write(raw);
                break;
            case ECHO: {
                out.write(accept);
                byte[] req = readToEnd(ch);
                r.request = req;
                r.sawEndOfStream = true;
                out.write(req);
                break;
            }
            case HOLD:
                out.write(accept);
                r.request = readToEnd(ch);
                r.sawEndOfStream = true;
                break;
            case SILENT:
                r.request = readToEnd(ch);
                r.sawEndOfStream = true;
                break;
            case HANDLER:
                out.write(accept);
                handler.handle(r, Channels.newInputStream(ch), out);
                break;
            }
        } catch (IOException e) {
            if (r != null) {
                r.sawEndOfStream = true;
            }
        } finally {
            if (r != null) {
                finished.add(r);
            }
        }
    }

    private static Received readHeader(SocketChannel ch) throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] fixed = readFully(ch, 7, all);
        int destLen = ((fixed[5] & 0xFF) << 8) | (fixed[6] & 0xFF);
        byte[] dest = readFully(ch, destLen, all);
        byte[] lenBytes = readFully(ch, 4, all);
        int consLen = ByteBuffer.wrap(lenBytes).getInt();
        if (consLen < 0 || consLen > 64 * 1024) {
            throw new IOException("bad constraints length " + consLen);
        }
        byte[] cons = readFully(ch, consLen, all);
        int tokenLen = readFully(ch, 1, all)[0] & 0xFF;
        byte[] token = readFully(ch, tokenLen, all);
        return new Received(all.toByteArray(),
            new String(dest, StandardCharsets.UTF_8), cons, token);
    }

    private static byte[] readFully(SocketChannel ch, int n,
                                    ByteArrayOutputStream copy)
        throws IOException
    {
        ByteBuffer buf = ByteBuffer.allocate(n);
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) {
                throw new EOFException();
            }
        }
        copy.write(buf.array(), 0, n);
        return buf.array();
    }

    private static byte[] readToEnd(SocketChannel ch) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ByteBuffer buf = ByteBuffer.allocate(8192);
        while (ch.read(buf) >= 0) {
            buf.flip();
            bos.write(buf.array(), 0, buf.limit());
            buf.clear();
        }
        return bos.toByteArray();
    }

    public void close() throws IOException {
        server.close();
        try {
            acceptor.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Files.deleteIfExists(socket);
        Files.deleteIfExists(dir);
    }
}
