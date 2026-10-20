/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import javax.security.auth.kerberos.KerberosPrincipal;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequestIterator;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A KerberosEndpoint is carried in a proxy (BasicObjectEndpoint), so it must
 * marshal under the atomic formats.  Before it was {@code @AtomicSerial} both
 * codecs refused it.  Authentication is not exercised (no KDC): marshalling,
 * equality, the decode checks, and that a decoded endpoint can set up a request.
 */
public class KerberosEndpointAtomicRoundTripTest {

    private static final KerberosPrincipal SERVER = 
            new KerberosPrincipal("service/host.example.org@EXAMPLE.ORG");

    private static KerberosEndpoint original() {
        return KerberosEndpoint.getInstance("host.example.org", 4321, SERVER);
    }

    @Test
    public void joss() throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream out = new AtomicMarshalOutputStream(bout, null);
        out.writeObject(original());
        out.flush();
        ObjectInputStream in = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(bout.toByteArray()), null, false, null, null, false);
        check((KerberosEndpoint) in.readObject());
    }

    @Test
    public void der() throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(bout)) {
            out.writeObject(original());
        }
        DerMarshalInputStream in = new DerMarshalInputStream(
                new ByteArrayInputStream(bout.toByteArray()));
        KerberosEndpoint decoded = (KerberosEndpoint) in.readObject();
        check(decoded);
    }

    private static void check(KerberosEndpoint decoded) throws Exception {
        assertEquals(original(), decoded);
        assertEquals(original().hashCode(), decoded.hashCode());
        assertEquals("host.example.org", decoded.getHost());
        assertEquals(4321, decoded.getPort());
        assertEquals(SERVER, decoded.getPrincipal());
        // A decoded instance is usable: the soft cache and connection manager 
        // exist, so a request can be set up (no connection is made until next()).
        OutboundRequestIterator it = decoded.newRequest(InvocationConstraints.EMPTY);
        assertNotNull(it);
    }

    @Test
    public void decodeRejectsWhatReadObjectRejects() throws Exception {
        assertRejected(null, 4321, SERVER);
        assertRejected("host", 0, SERVER);
        assertRejected("host", 65536, SERVER);
        assertRejected("host", 4321, null);
        assertRejectedName("host", 4321, "no-realm-but-bad\u0000name@");
    }

    private static void assertRejected(String host, int port, KerberosPrincipal p) throws Exception {
        assertRejectedName(host, port, p == null ? null : p.getName());
    }

    private static void assertRejectedName(String host, int port, String name) throws Exception {
        Constructor<KerberosEndpoint> c = KerberosEndpoint.class.getDeclaredConstructor(GetArg.class);
        c.setAccessible(true);
        try {
            c.newInstance(new Fake(host, port, name));
            fail("accepted " + host + ":" + port + " " + name);
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(String.valueOf(e.getCause()), 
                    e.getCause() instanceof InvalidObjectException);
        }
    }

    private static final class Fake extends GetArg {
        private final String host;
        private final int port;
        private final String principal;

        Fake(String host, int port, String principal) {
            super();
            this.host = host;
            this.port = port;
            this.principal = principal;
        }

        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            switch (name) {
                case "serverHost": return host == null ? ABSENT : host;
                case "serverPort": return port;
                case "serverPrincipalName": return principal == null ? ABSENT : principal;
                case "serverPrincipalNameType": return KerberosPrincipal.KRB_NT_PRINCIPAL;
                default: return ABSENT;
            }
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            return lookup(callerClass, name) == ABSENT;
        }

        @Override
        public Class[] serialClasses() {
            return new Class[]{ KerberosEndpoint.class };
        }

        @Override
        public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }
}
