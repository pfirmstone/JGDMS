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
package net.jini.jeri.kerberos;

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.kerberos.KerberosPrincipal;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequestIterator;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link KerberosEndpoint} is {@code @AtomicSerial}: it can be written and read by the DER and atomic Java
 * serialization streams (before, both refused it), a stream-built instance validates what
 * {@code readObject} validates, and it is usable without having been interned.
 */
public class KerberosEndpointAtomicTest {

    private static final Collection<?> NO_CONTEXT = Collections.unmodifiableCollection(Collections.emptyList());
    private static final ClassLoader LOADER = KerberosEndpointAtomicTest.class.getClassLoader();

    private static KerberosEndpoint endpoint() {
        return KerberosEndpoint.getInstance("127.0.0.1", 1, new KerberosPrincipal("svc@EXAMPLE.ORG"));
    }

    private static Object derRoundTrip(Object o) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new DerMarshalOutputStream(bos, NO_CONTEXT, LOADER)) {
            out.writeObject(o);
        }
        try (ObjectInput in = new DerMarshalInputStream(new ByteArrayInputStream(bos.toByteArray()),
                new ResolutionContext(LOADER, false, LOADER), DerInputLimits.DEFAULT)) {
            return in.readObject();
        }
    }

    private static Object jossRoundTrip(Object o) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new AtomicMarshalOutputStream(bos, LOADER, NO_CONTEXT, false)) {
            out.writeObject(o);
        }
        try (ObjectInput in = AtomicMarshalInputStream.create(new ByteArrayInputStream(bos.toByteArray()),
                LOADER, false, LOADER, NO_CONTEXT, false)) {
            return in.readObject();
        }
    }

    private static void assertUsable(KerberosEndpoint e) {
        try {
            OutboundRequestIterator it = e.newRequest(InvocationConstraints.EMPTY);
            assertNotNull(it);
            if (it.hasNext()) {
                it.next();
            }
        } catch (NullPointerException npe) {
            throw new AssertionError("a stream-built endpoint is not set up: " + npe, npe);
        } catch (Exception expected) {
            // no KDC, no server, no Subject: anything but an unset-up endpoint is fine
        }
    }

    @Test
    public void derRoundTripKeepsTheEndpoint() throws Exception {
        KerberosEndpoint original = endpoint();
        Object back = derRoundTrip(original);
        assertTrue(back instanceof KerberosEndpoint);
        assertEquals(original, back);
        assertEquals(original.getPrincipal().getName(), ((KerberosEndpoint) back).getPrincipal().getName());
        assertEquals(original.getPrincipal().getNameType(), ((KerberosEndpoint) back).getPrincipal().getNameType());
        assertUsable((KerberosEndpoint) back);
    }

    @Test
    public void atomicJossRoundTripKeepsTheEndpoint() throws Exception {
        KerberosEndpoint original = endpoint();
        Object back = jossRoundTrip(original);
        assertTrue(back instanceof KerberosEndpoint);
        assertEquals(original, back);
        assertUsable((KerberosEndpoint) back);
    }

    /** A GetArg over a map of field values, the one class that may read them. */
    private static final class MapGetArg extends GetArg {
        private final Map<String, Object> values;

        MapGetArg(Map<String, Object> values) {
            this.values = values;
        }

        @Override protected Object lookup(Class<?> c, String name) {
            return values.containsKey(name) ? values.get(name) : ABSENT;
        }

        @Override protected boolean isDefaulted(Class<?> c, String name) {
            return !values.containsKey(name);
        }

        @Override public Class[] serialClasses() {
            return new Class[] {KerberosEndpoint.class};
        }

        @Override public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    private static Map<String, Object> good() {
        Map<String, Object> m = new HashMap<>();
        m.put("serverHost", "127.0.0.1");
        m.put("serverPort", 88);
        m.put("principalName", "svc@EXAMPLE.ORG");
        m.put("principalNameType", KerberosPrincipal.KRB_NT_PRINCIPAL);
        m.put("csf", null);
        return m;
    }

    private static void assertRejected(Map<String, Object> m, String what) throws Exception {
        try {
            new KerberosEndpoint(new MapGetArg(m));
            fail(what + " must be rejected");
        } catch (InvalidObjectException expected) {
            // as readObject rejects it
        }
    }

    @Test
    public void aStreamBuiltEndpointIsValidatedAsReadObjectValidates() throws Exception {
        assertEquals("127.0.0.1", new KerberosEndpoint(new MapGetArg(good())).getHost());
        Map<String, Object> m = good();
        m.remove("serverHost");
        assertRejected(m, "a missing host");
        m = good();
        m.put("serverPort", 0);
        assertRejected(m, "port 0");
        m.put("serverPort", 65536);
        assertRejected(m, "port 65536");
        m = good();
        m.remove("principalName");
        assertRejected(m, "a missing principal");
        m = good();
        m.put("principalName", "");
        assertRejected(m, "an empty principal name");
    }
}
