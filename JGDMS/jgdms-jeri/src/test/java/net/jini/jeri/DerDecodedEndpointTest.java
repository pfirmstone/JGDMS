/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.jeri;

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.http.HttpEndpoint;
import net.jini.jeri.tcp.TcpEndpoint;
import net.jini.jeri.uds.UdsEndpoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * An endpoint decoded from a DER stream must be usable. The `getInstance` factories intern an
 * endpoint and set up its connection state; Java serialization reaches the same state through
 * `readResolve()`, but the DER (`@AtomicSerial`) path builds the endpoint with its constructor and never
 * calls it, so a decoded endpoint used to throw a NullPointerException from its first
 * `newRequest`. Found by calling a proxy that had been decoded from a DER stream.
 */
public class DerDecodedEndpointTest {

    private static final Collection<?> NO_CONTEXT = Collections.unmodifiableCollection(Collections.emptyList());

    private static Object roundTrip(Object o) throws Exception {
        ClassLoader loader = DerDecodedEndpointTest.class.getClassLoader();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(bos, NO_CONTEXT, loader)) {
            out.writeObject(o);
        }
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bos.toByteArray()),
                new ResolutionContext(loader, false, loader), DerInputLimits.DEFAULT)) {
            return in.readObject();
        }
    }

    /** Asks for a request and tries to start it: a refused connection is an IOException, a missing set-up is an NPE. */
    private static void assertUsable(Endpoint decoded) {
        try {
            OutboundRequestIterator it = decoded.newRequest(InvocationConstraints.EMPTY);
            assertNotNull(it);
            if (it.hasNext()) {
                it.next();
            }
        } catch (NullPointerException e) {
            throw new AssertionError("a decoded endpoint is not set up: " + e, e);
        } catch (Exception expected) {
            // the peer does not exist: any failure but an unset-up endpoint is fine
        }
    }

    @Test
    public void aDecodedTcpEndpointIsUsable() throws Exception {
        TcpEndpoint original = TcpEndpoint.getInstance("127.0.0.1", 1);
        Object decoded = roundTrip(original);
        assertTrue(decoded instanceof TcpEndpoint);
        assertEquals(original, decoded);
        assertUsable((TcpEndpoint) decoded);
    }

    @Test
    public void aDecodedUdsEndpointIsUsable() throws Exception {
        UdsEndpoint original = UdsEndpoint.getInstance("/tmp/jgdms-no-such.sock");
        Object decoded = roundTrip(original);
        assertTrue(decoded instanceof UdsEndpoint);
        assertEquals(original, decoded);
        assertUsable((UdsEndpoint) decoded);
    }

    @Test
    public void aDecodedHttpEndpointIsUsable() throws Exception {
        HttpEndpoint original = HttpEndpoint.getInstance("127.0.0.1", 1);
        Object decoded = roundTrip(original);
        assertTrue(decoded instanceof HttpEndpoint);
        assertEquals(original, decoded);
        assertUsable((HttpEndpoint) decoded);
    }
}
