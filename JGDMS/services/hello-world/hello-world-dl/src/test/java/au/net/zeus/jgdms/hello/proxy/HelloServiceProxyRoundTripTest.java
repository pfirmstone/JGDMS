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
package au.net.zeus.jgdms.hello.proxy;

import au.net.zeus.jgdms.api.hello.HelloService;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.Remote;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.export.CodebaseAccessor;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The regression test the example was missing: it round-trips a
 * {@link HelloServiceProxy} through the <em>AtomicSerial</em> codec
 * ({@link AtomicMarshalOutputStream} / {@link AtomicMarshalInputStream}),
 * which is the only path that exercises both the output contract
 * ({@code serialize}/{@code serialForm} on {@link AbstractSmartProxy}) and the
 * deserialization validation ({@code AbstractSmartProxy.checkServer}).  A plain
 * {@code ObjectInputStream} bypasses both, which is why the original
 * construction-only mock tests never surfaced the breakage.
 *
 * <p>The {@code server} is a {@link HelloServiceBackend}-implementing fixture
 * rather than a live JERI stub, so the test stays a deterministic unit test:
 * it needs no socket and no {@code PreferredClassProvider} RMI class-loader
 * environment.  A full export-and-marshal round-trip belongs in the qa
 * integration suite.  The type-level guarantee that a real JERI stub also
 * satisfies {@code checkServer} is pinned by
 * {@link #testBackendAggregatesCheckServerContract()}.
 */
public class HelloServiceProxyRoundTripTest {

    /**
     * The backend interface must aggregate exactly the contract
     * {@code AbstractSmartProxy.checkServer} requires of a server stub:
     * {@link Remote}, the client service interface, the four {@code Remote}
     * bootstrap accessors, and {@link Administrable}/{@link JoinAdmin}/
     * {@link DestroyAdmin}.  This is what guarantees a JERI stub for the
     * backend transitively implements them all and so passes {@code checkServer}.
     */
    @Test
    public void testBackendAggregatesCheckServerContract() {
        Class<?> b = HelloServiceBackend.class;
        for (Class<?> required : new Class<?>[]{
                Remote.class, HelloService.class,
                ServiceProxyAccessor.class, ServiceAttributesAccessor.class,
                ServiceIDAccessor.class, CodebaseAccessor.class,
                Administrable.class, JoinAdmin.class, DestroyAdmin.class}) {
            assertTrue(required.getName()
                    + " must be aggregated by HelloServiceBackend",
                    required.isAssignableFrom(b));
        }
    }

    /**
     * A {@link HelloServiceProxy} wrapping a backend-implementing server
     * survives an AtomicSerial marshal/unmarshal round-trip: the output path
     * runs {@code AbstractSmartProxy.serialize}, and the input path runs the
     * {@code (GetArg)} constructor and {@code checkServer} on the deserialized
     * server.  Identity (the {@link Uuid}) is preserved.
     */
    @Test
    public void testProxyAtomicRoundTrip() throws Exception {
        FakeBackend server = new FakeBackend("svc-1");
        Uuid uuid = UuidFactory.generate();

        AbstractSmartProxy proxy = HelloServiceProxy.create(server, uuid);
        assertTrue("non-constrainable server yields a plain proxy",
                proxy instanceof HelloServiceProxy);

        AbstractSmartProxy back = (AbstractSmartProxy) atomicRoundTrip(proxy);

        assertEquals("identity preserved across round-trip",
                uuid, back.getReferentUuid());
        assertEquals("round-tripped proxy equals the original", proxy, back);
        assertTrue("server survives and still implements the backend",
                back.getProxy() instanceof HelloServiceBackend);
    }

    // --------------------------------------------------------------- helpers

    private static Object atomicRoundTrip(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(o);
        oos.flush();
        oos.close();
        ObjectInputStream ois = AtomicMarshalInputStream.create(
                new ByteArrayInputStream(baos.toByteArray()),
                null, false, null, null, false);
        return ois.readObject();
    }

    /**
     * Serializable {@link HelloServiceBackend} fixture standing in for an
     * exported stub.  It is {@code @AtomicSerial} so it marshals through the
     * atomic codec without the JERI/RMI runtime; method bodies are never
     * invoked by serialization or {@code checkServer} (which only does
     * {@code instanceof} checks), so they throw.
     */
    @AtomicSerial
    public static final class FakeBackend
            implements HelloServiceBackend, Serializable {

        private static final long serialVersionUID = 1L;

        private final String id;

        public FakeBackend(String id) { this.id = id; }

        public FakeBackend(GetArg arg) throws IOException, ClassNotFoundException {
            this.id = (String) arg.get("id", null);
        }

        public static SerialForm[] serialForm() {
            return new SerialForm[]{ new SerialForm("id", String.class) };
        }

        public static void serialize(PutArg arg, FakeBackend o) throws IOException {
            arg.put("id", o.id);
            arg.writeArgs();
        }

        private static UnsupportedOperationException nope() {
            return new UnsupportedOperationException("fixture method not invoked");
        }

        @Override public String sayHello(String name) { throw nope(); }
        @Override public Object getServiceProxy() { throw nope(); }
        @Override public Entry[] getServiceAttributes() { throw nope(); }
        @Override public ServiceID serviceID() { throw nope(); }
        @Override public String getClassAnnotation() { throw nope(); }
        @Override public String getCertFactoryType() { throw nope(); }
        @Override public String getCertPathEncoding() { throw nope(); }
        @Override public byte[] getEncodedCerts() { throw nope(); }
        @Override public String getCodebaseDigestAlgorithm() { throw nope(); }
        @Override public byte[] getCodebaseDigest() { throw nope(); }
        @Override public int[] getDigestOffsets() { throw nope(); }
        @Override public Object getAdmin() { throw nope(); }
        @Override public Entry[] getLookupAttributes() { throw nope(); }
        @Override public void addLookupAttributes(Entry[] a) { throw nope(); }
        @Override public void modifyLookupAttributes(Entry[] t, Entry[] a) { throw nope(); }
        @Override public String[] getLookupGroups() { throw nope(); }
        @Override public void addLookupGroups(String[] g) { throw nope(); }
        @Override public void removeLookupGroups(String[] g) { throw nope(); }
        @Override public void setLookupGroups(String[] g) { throw nope(); }
        @Override public LookupLocator[] getLookupLocators() { throw nope(); }
        @Override public void addLookupLocators(LookupLocator[] l) { throw nope(); }
        @Override public void removeLookupLocators(LookupLocator[] l) { throw nope(); }
        @Override public void setLookupLocators(LookupLocator[] l) { throw nope(); }
        @Override public void destroy() { throw nope(); }
    }
}
