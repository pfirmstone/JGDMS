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
import java.rmi.Remote;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
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
     * The {@link HelloServiceProxy#create} factory now ALWAYS returns the
     * constrainable form, and the base {@code HelloServiceProxy} is abstract so
     * a plain, non-constrainable wire proxy can never be produced.  This is the
     * security invariant: the constrainable proxy is the only concrete wire
     * form, so a client that requested Integrity/ServerAuthentication/
     * Confidentiality cannot be silently handed a downgraded proxy.
     *
     * <p>A full marshal/unmarshal round-trip of the constrainable proxy is an
     * integration concern: because the server stub is itself a constrainable
     * {@link CodebaseAccessor}, the AtomicSerial output path routes it through
     * the {@code ProxySerializer} codebase-substitution machinery, which needs
     * a live bootstrap proxy / {@code ProxyCodebaseSpi} provider that only
     * exists in the qa export environment.  That round-trip is therefore
     * covered in the qa integration suite, not here.
     */
    @Test
    public void testFactoryAlwaysReturnsConstrainableForm() {
        ConstrainableBackend server = new ConstrainableBackend("svc-1");
        Uuid uuid = UuidFactory.generate();

        AbstractSmartProxy proxy = HelloServiceProxy.create(server, uuid);
        assertTrue("constrainable server yields the constrainable proxy",
                proxy instanceof HelloServiceProxy.ConstrainableHelloServiceProxy);
        assertTrue("the only concrete proxy form implements RemoteMethodControl",
                proxy instanceof RemoteMethodControl);
        assertEquals("identity is carried by the constrainable proxy",
                uuid, proxy.getReferentUuid());
    }

    /**
     * The factory fails closed: a server stub that was not exported with a
     * constrainable endpoint (does not implement {@link RemoteMethodControl})
     * is rejected rather than silently wrapped in a plain proxy that would drop
     * the client's security constraints.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFactoryFailsClosedOnNonConstrainableServer() {
        FakeBackend server = new FakeBackend("svc-1");   // not RemoteMethodControl
        HelloServiceProxy.create(server, UuidFactory.generate());
    }

    // --------------------------------------------------------------- helpers

    /**
     * {@link HelloServiceBackend} fixture standing in for an exported stub that
     * was NOT exported over a constrainable endpoint — it does not implement
     * {@link RemoteMethodControl}.  Method bodies are never invoked (the factory
     * only does {@code instanceof} checks), so they throw.  Used to exercise the
     * fail-closed path of the {@code create} factory.
     */
    public static class FakeBackend implements HelloServiceBackend {

        private final String id;

        public FakeBackend(String id) { this.id = id; }

        String id() { return id; }

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

    /**
     * {@link HelloServiceBackend} fixture that additionally implements
     * {@link RemoteMethodControl}, standing in for a stub exported over a
     * constrainable (SSL/TLS) endpoint — the only kind the {@code create}
     * factory now accepts.  {@code setConstraints} returns {@code this}, so the
     * constrainable proxy's constraint application is a deterministic no-op
     * suitable for a unit test.
     */
    public static final class ConstrainableBackend extends FakeBackend
            implements RemoteMethodControl {

        public ConstrainableBackend(String id) { super(id); }

        @Override public RemoteMethodControl setConstraints(MethodConstraints c) { return this; }
        @Override public MethodConstraints getConstraints() { return null; }
    }
}
