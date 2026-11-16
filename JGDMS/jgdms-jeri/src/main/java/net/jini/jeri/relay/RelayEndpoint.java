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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.NoSuchElementException;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.Endpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.security.proxytrust.TrustEquivalence;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * An {@link Endpoint} that sends each request through a trusted relay process
 * on the same host instead of connecting to the server itself.
 *
 * <p>The endpoint holds only an opaque destination identifier issued by the
 * relay.  It has no host, port or socket factory.  Each request opens a new
 * connection to the relay (see {@link RelayChannels}), names the destination
 * and the request's constraints in a short header, and once the relay accepts,
 * carries the raw request and response bytes.  The relay owns the real
 * connection to the server and enforces the constraints on it.
 *
 * <p>Wire layout, all integers big-endian:
 * <pre>
 * client: 'J' 'R' 'L' 'Y'  version(1) = 2
 *         destination length(2)  destination (UTF-8)
 *         constraints length(4)  constraints (DER, see below; 0 = EMPTY)
 *         token length(1)  token (0 to 64 raw bytes; 0 = no token)
 * relay:  status(1)
 *         0 = accepted, followed by
 *             principal count(2), at most 16, then for each principal
 *             class name length(2)  class name (UTF-8)
 *             name length(2)  name (UTF-8)
 *         otherwise refused, followed by
 *             reason length(2)  reason (UTF-8)
 * then:   request bytes client to relay, ended by a half-close;
 *         response bytes relay to client.
 * </pre>
 * Every string in the reply is at most 1024 bytes of well-formed UTF-8.  The
 * principals are those the relay authenticated for the server; a count of 0
 * means the server was not authenticated.  Only {@code
 * javax.security.auth.x500.X500Principal} and {@code
 * au.net.zeus.jgdms.spiffe.SpiffePrincipal} are accepted; any other class
 * name fails the request.  The constraints are written as a single object
 * with {@link au.net.zeus.jgdms.der.stream.DerMarshalOutputStream} and are at
 * most 64 KiB.  The header write and the reply read must finish within the
 * time set by {@value RelayChannels#HANDSHAKE_TIMEOUT_PROPERTY} (milliseconds,
 * default 30000).  The token is the one bound by {@link
 * RelayCallToken#callWith} on the thread that called {@code newRequest}.
 *
 * <p>The relay applies the constraints to the real connection.  The request
 * reports as unfulfilled only the constraints the invocation layer must
 * implement: {@code Integrity.YES}, {@code AtomicInputValidation.YES} and
 * {@code MarshallingFormat}.
 *
 * <p>Two endpoints are equal if their destinations are equal.
 *
 * @since 4.0
 */
@AtomicSerial
public final class RelayEndpoint implements Endpoint, TrustEquivalence {

    /** Maximum length of a destination identifier. */
    static final int MAX_DESTINATION_LENGTH = 128;

    public static SerialForm[] serialForm() {
        return new SerialForm[]{
            new SerialForm("destination", String.class)
        };
    }

    public static void serialize(PutArg arg, RelayEndpoint ep)
        throws IOException
    {
        arg.put("destination", ep.destination);
        arg.writeArgs();
    }

    /**
     * The relay-issued destination identifier.
     *
     * @serial
     */
    private final String destination;

    /**
     * Returns an endpoint for the given relay destination.
     *
     * @param destination the identifier the relay issued: 1 to 128 characters
     *        from {@code A-Z a-z 0-9 . _ -}
     * @return the endpoint
     * @throws NullPointerException if {@code destination} is {@code null}
     * @throws IllegalArgumentException if {@code destination} is not valid
     */
    public static RelayEndpoint getInstance(String destination) {
        return new RelayEndpoint(check(destination));
    }

    RelayEndpoint(GetArg arg) throws IOException, ClassNotFoundException {
        this(checkSerial(arg.get("destination", null, String.class)));
    }

    private RelayEndpoint(String destination) {
        this.destination = destination;
    }

    private static String checkSerial(String destination)
        throws InvalidObjectException
    {
        try {
            return check(destination);
        } catch (RuntimeException e) {
            InvalidObjectException ex = new InvalidObjectException(
                "Invariants not satisfied: " + e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }

    private static String check(String destination) {
        if (destination == null) {
            throw new NullPointerException("destination is null");
        }
        int len = destination.length();
        if (len == 0 || len > MAX_DESTINATION_LENGTH) {
            throw new IllegalArgumentException(
                "destination length must be 1 to " + MAX_DESTINATION_LENGTH
                + ": " + len);
        }
        for (int i = 0; i < len; i++) {
            char c = destination.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                  || (c >= '0' && c <= '9') || c == '.' || c == '_'
                  || c == '-'))
            {
                throw new IllegalArgumentException(
                    "illegal character in destination at index " + i);
            }
        }
        return destination;
    }

    /**
     * Returns the relay-issued destination identifier.
     *
     * @return the destination identifier
     */
    public String getDestination() {
        return destination;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned iterator yields one request.  Its {@code next} method
     * connects to the relay and completes the opening exchange; it throws
     * {@code IOException} if the relay cannot be reached, refuses the
     * destination, replies with bad framing or an unaccepted principal class,
     * or does not complete the exchange in time.  The call token bound to
     * the calling thread, if any, is captured now and sent by {@code next}.
     *
     * @throws NullPointerException {@inheritDoc}
     */
    public OutboundRequestIterator newRequest(InvocationConstraints constraints) {
        return newRequest(constraints, RelayChannels.SYSTEM);
    }

    OutboundRequestIterator newRequest(final InvocationConstraints constraints,
                                       final RelayChannels channels)
    {
        if (constraints == null) {
            throw new NullPointerException();
        }
        final byte[] token = RelayCallToken.current();
        return new OutboundRequestIterator() {
            private boolean nextCalled = false;

            public synchronized boolean hasNext() {
                return !nextCalled;
            }

            public synchronized OutboundRequest next() throws IOException {
                if (nextCalled) {
                    throw new NoSuchElementException();
                }
                nextCalled = true;
                return RelayOutboundRequest.open(
                    channels, destination, constraints, token);
            }
        };
    }

    public int hashCode() {
        return destination.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof RelayEndpoint)) {
            return false;
        }
        return destination.equals(((RelayEndpoint) obj).destination);
    }

    public boolean checkTrustEquivalence(Object obj) {
        return equals(obj);
    }

    public String toString() {
        return "RelayEndpoint[" + destination + "]";
    }
}
