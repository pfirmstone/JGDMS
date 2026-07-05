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
package au.net.zeus.jgdms.api.hello;

import au.net.zeus.jgdms.proxy.AdminProxy;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.export.CodebaseAccessor;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.apache.river.api.io.AtomicMarshalledInstance;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.DynamicILFactory;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Round-trip / contract test for the Hello World DYNAMIC service proxy and its
 * {@code getAdmin()} admin facet (JGDMS @JiniService admin-proxy design, unit 2).
 *
 * <p>The framework default installs a {@link net.jini.jeri.DynamicILFactory} with
 * <em>two disjoint</em> extra-interface sets:
 * <ul>
 *   <li>{@code castAndDispatch = {Administrable}} — appended to BOTH the client stub
 *       cast set and the server dispatcher, so the exported stub is
 *       {@link Administrable};</li>
 *   <li>{@code dispatchOnly = {JoinAdmin, DestroyAdmin}} — registered on the server
 *       invocation dispatcher only and <em>stripped</em> from the client stub, so the
 *       thin service stub is NOT {@code JoinAdmin}/{@code DestroyAdmin}.</li>
 * </ul>
 * The admin interfaces are reached instead through a SEPARATE facet proxy (as
 * {@code Administrable.getAdmin()} returns): a second {@link java.lang.reflect.Proxy}
 * built over the SAME invocation handler (hence the same endpoint) that implements the
 * admin interfaces, wrapped in the {@code @AtomicSerial} {@link AdminProxy}.  Because
 * the admin methods' JRMP hashes are registered on the one server dispatcher (they are
 * in {@code dispatchOnly}), an admin call through the facet dispatches over the single
 * shared export — no second endpoint (JGDMS-STD-009 §6 shapes 1 &amp; 2 + §14).
 *
 * <p>These are real in-JVM JERI loopback exports over TCP (no mocking), so the test
 * needs the JDK default {@code RMIClassLoaderSpi} — configured via the surefire
 * {@code argLine} in this module's pom.
 */
public class HelloServiceProxyRoundTripTest {

    /** A distinctive value the admin fixture returns so a dispatched admin call is observable. */
    private static final String[] ADMIN_GROUPS = { "admin-dispatch-ok" };

    /**
     * The exported DYNAMIC stub must be a {@link java.lang.reflect.Proxy} — the whole
     * point of shape 1 is that there is no generated proxy class; the runtime dynamic
     * proxy is the client proxy.
     */
    @Test
    public void testExportedStubIsDynamicProxy() throws Exception {
        Exporter exporter = newExporter();
        try {
            Object proxy = exporter.export(new HelloServiceFixture());
            assertTrue("DYNAMIC export must yield a java.lang.reflect.Proxy stub",
                    Proxy.isProxyClass(proxy.getClass()));
        } finally {
            exporter.unexport(true);
        }
    }

    /**
     * The exported DYNAMIC stub carries the CLIENT-facing set: the public API, the
     * cast-and-dispatch admin gateway {@link Administrable}, the {@link Remote}
     * bootstrap accessors, and {@link RemoteMethodControl}.
     */
    @Test
    public void testExportedStubImplementsClientFacingSet() throws Exception {
        Exporter exporter = newExporter();
        try {
            Object proxy = exporter.export(new HelloServiceFixture());
            for (Class<?> required : new Class<?>[]{
                    HelloService.class,
                    Administrable.class,
                    ServiceProxyAccessor.class, ServiceAttributesAccessor.class,
                    ServiceIDAccessor.class, CodebaseAccessor.class,
                    RemoteMethodControl.class}) {
                assertTrue(required.getName()
                        + " must be implemented by the exported DYNAMIC stub",
                        required.isInstance(proxy));
            }
        } finally {
            exporter.unexport(true);
        }
    }

    /**
     * The exported client stub must NOT be {@link JoinAdmin}/{@link DestroyAdmin}:
     * those are {@code dispatchOnly}, stripped from the client cast set.  Administration
     * is reached only through the separate {@code getAdmin()} facet.
     */
    @Test
    public void testExportedStubIsNotAdminEnabled() throws Exception {
        Exporter exporter = newExporter();
        try {
            Object proxy = exporter.export(new HelloServiceFixture());
            assertFalse("dispatch-only JoinAdmin must be stripped from the client stub",
                    proxy instanceof JoinAdmin);
            assertFalse("dispatch-only DestroyAdmin must be stripped from the client stub",
                    proxy instanceof DestroyAdmin);
        } finally {
            exporter.unexport(true);
        }
    }

    /**
     * A loopback call on the exported DYNAMIC stub forwards
     * {@link HelloService#sayHello} to the backing implementation over a real in-JVM
     * JERI round-trip.
     */
    @Test
    public void testStubForwardsSayHello() throws Exception {
        Exporter exporter = newExporter();
        try {
            HelloService proxy = (HelloService) exporter.export(new HelloServiceFixture());
            assertEquals("Hello, World!", proxy.sayHello("World"));
        } finally {
            exporter.unexport(true);
        }
    }

    /**
     * The heart of the design: the {@code getAdmin()} facet — a second
     * {@link java.lang.reflect.Proxy} over the SAME invocation handler as the stub,
     * carrying the admin interfaces, wrapped in {@link AdminProxy} — IS
     * {@code JoinAdmin}/{@code DestroyAdmin}/{@code RemoteMethodControl}, and an admin
     * call through it dispatches over the single shared export to the backing impl.
     */
    @Test
    public void testAdminFacetIsAdminEnabledAndDispatches() throws Exception {
        Exporter exporter = newExporter();
        try {
            Object stub = exporter.export(new HelloServiceFixture());

            // Build the admin facet exactly as AbstractJiniService.createAdminProxy
            // does: a second Proxy over the SAME handler (hence same endpoint), then
            // the @AtomicSerial AdminProxy wrapper.
            Object admin = adminFacet(stub, UuidFactory.generate(),
                    new Class<?>[]{ JoinAdmin.class, DestroyAdmin.class });

            assertTrue("admin facet must be JoinAdmin", admin instanceof JoinAdmin);
            assertTrue("admin facet must be DestroyAdmin", admin instanceof DestroyAdmin);
            assertTrue("admin facet must be constrainable (RemoteMethodControl)",
                    admin instanceof RemoteMethodControl);

            // An admin call must dispatch over the shared backend to the fixture.
            String[] groups = ((JoinAdmin) admin).getLookupGroups();
            assertArrayEquals("admin call must dispatch over the shared export to the impl",
                    ADMIN_GROUPS, groups);
        } finally {
            exporter.unexport(true);
        }
    }

    /**
     * The admin factory fails CLOSED (like reggie): when the facet is not a
     * {@link RemoteMethodControl}, {@link AdminProxy#create(Remote, Uuid, Class[])}
     * throws rather than degrading to a non-constrainable proxy.
     */
    @Test
    public void testAdminFactoryFailsClosedWhenNotConstrainable() {
        // A Remote (so it fits AdminProxy.create's parameter) admin proxy that is
        // deliberately NOT a RemoteMethodControl.
        Remote notConstrainable = (Remote) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ Remote.class, JoinAdmin.class, DestroyAdmin.class },
                THROWING);
        try {
            AdminProxy.create(notConstrainable, UuidFactory.generate(),
                    new Class<?>[]{ JoinAdmin.class, DestroyAdmin.class });
            fail("admin factory must fail closed when the facet is not RemoteMethodControl");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("RemoteMethodControl"));
        }
    }

    /**
     * Wire-form gate (a): the COMMON {@code {JoinAdmin, DestroyAdmin}} admin facet —
     * the fixed {@code @AtomicSerial} {@code ConstrainableAdminProxy} — round-trips
     * through {@link AtomicMarshalInputStream} after dropping {@code Serializable}, is
     * reconstructed as the same constrainable admin proxy (same UUID identity), and is
     * NOT a {@link Serializable} nor a {@link ProxyAccessor}.
     */
    @Test
    public void testCommonAdminProxyRoundTrips() throws Exception {
        Exporter exporter = newExporter();
        try {
            Object stub = exporter.export(new HelloServiceFixture());
            Uuid id = UuidFactory.generate();
            Object admin = adminFacet(stub, id,
                    new Class<?>[]{ JoinAdmin.class, DestroyAdmin.class });

            assertFalse("admin proxy must NOT implement java.io.Serializable "
                    + "(severed-JOSS rule)", admin instanceof Serializable);
            assertFalse("common admin proxy must NOT be a ProxyAccessor",
                    admin instanceof ProxyAccessor);

            Object rt = roundTrip(admin);

            assertTrue("round-tripped admin proxy must be JoinAdmin", rt instanceof JoinAdmin);
            assertTrue("round-tripped admin proxy must be DestroyAdmin", rt instanceof DestroyAdmin);
            assertTrue("round-tripped admin proxy must be constrainable",
                    rt instanceof RemoteMethodControl);
            assertFalse("round-tripped admin proxy must NOT be Serializable",
                    rt instanceof Serializable);
            assertTrue("UUID identity must survive the round-trip", admin.equals(rt));
        } finally {
            exporter.unexport(true);
        }
    }

    /**
     * Wire-form gate (b): a LARGER admin set {@code {JoinAdmin, DestroyAdmin, FooAdmin}}
     * — the dynamic {@link java.lang.reflect.Proxy} admin stub backed by the
     * {@code @AtomicSerial} {@code DynamicAdminProxy} handler — round-trips through
     * {@link AtomicMarshalInputStream}: it is reconstructed as an equivalent
     * {@code java.lang.reflect.Proxy} carrying the admin interface set +
     * {@link RemoteMethodControl} + {@link ReferentUuid} (same UUID identity), and is
     * deliberately NOT a {@link ProxyAccessor} (so it is not diverted through the
     * smart-proxy codebase-download substitution).
     */
    @Test
    public void testLargerAdminSetRoundTrips() throws Exception {
        Exporter exporter = newExporter();
        try {
            Object stub = exporter.export(new HelloServiceFixture());
            Uuid id = UuidFactory.generate();
            // A constrainable facet over the shared export that ALSO carries FooAdmin.
            Object admin = adminFacet(stub, id,
                    new Class<?>[]{ JoinAdmin.class, DestroyAdmin.class, FooAdmin.class });

            assertTrue("larger admin set must yield a java.lang.reflect.Proxy admin stub",
                    Proxy.isProxyClass(admin.getClass()));
            assertFalse("dynamic admin proxy must NOT be a ProxyAccessor",
                    admin instanceof ProxyAccessor);
            assertTrue("dynamic admin proxy must be FooAdmin", admin instanceof FooAdmin);
            assertTrue("dynamic admin proxy must be constrainable",
                    admin instanceof RemoteMethodControl);

            Object rt = roundTrip(admin);

            assertTrue("round-tripped dynamic admin proxy must still be a "
                    + "java.lang.reflect.Proxy", Proxy.isProxyClass(rt.getClass()));
            assertTrue("round-tripped stub must be JoinAdmin", rt instanceof JoinAdmin);
            assertTrue("round-tripped stub must be DestroyAdmin", rt instanceof DestroyAdmin);
            assertTrue("round-tripped stub must be FooAdmin", rt instanceof FooAdmin);
            assertTrue("round-tripped stub must be constrainable",
                    rt instanceof RemoteMethodControl);
            assertTrue("round-tripped stub must be ReferentUuid", rt instanceof ReferentUuid);
            assertFalse("round-tripped stub must NOT be a ProxyAccessor",
                    rt instanceof ProxyAccessor);
            assertEquals("UUID identity must survive the round-trip",
                    id, ((ReferentUuid) rt).getReferentUuid());
        } finally {
            exporter.unexport(true);
        }
    }

    // --------------------------------------------------------------- helpers

    /** Round-trips {@code o} through the atomic wire engine (AtomicMarshalInputStream). */
    private static Object roundTrip(Object o) throws Exception {
        return new AtomicMarshalledInstance(o).get(false);
    }

    /**
     * Builds the admin facet the way {@code AbstractJiniService.createAdminProxy}
     * does: a second {@link java.lang.reflect.Proxy} over the exported stub's
     * invocation handler (so it shares the endpoint), implementing
     * {@code Remote + RemoteMethodControl + adminIfaces}, wrapped in {@link AdminProxy}.
     */
    private static Object adminFacet(Object stub, Uuid id, Class<?>[] adminIfaces) {
        InvocationHandler handler = Proxy.getInvocationHandler(stub);
        ClassLoader cl = stub.getClass().getClassLoader();
        Class<?>[] facetIfaces = new Class<?>[adminIfaces.length + 2];
        facetIfaces[0] = Remote.class;
        facetIfaces[1] = RemoteMethodControl.class;
        System.arraycopy(adminIfaces, 0, facetIfaces, 2, adminIfaces.length);
        Remote facet = (Remote) Proxy.newProxyInstance(cl, facetIfaces, handler);
        return AdminProxy.create(facet, id, adminIfaces);
    }

    /**
     * Builds a plain-TCP {@link BasicJeriExporter} whose invocation-layer factory is a
     * {@link net.jini.jeri.DynamicILFactory} with the two disjoint extra sets the
     * framework default installs for the DYNAMIC shape (see {@code JiniServiceParameters}):
     * {@code castAndDispatch = {Administrable}} and {@code dispatchOnly = {JoinAdmin,
     * DestroyAdmin}}.
     */
    private static Exporter newExporter() {
        return new BasicJeriExporter(
                TcpServerEndpoint.getInstance(0),
                new DynamicILFactory(
                        null, null,
                        HelloServiceProxyRoundTripTest.class.getClassLoader(),
                        new Class[]{ Administrable.class },                 // castAndDispatch
                        new Class[]{ JoinAdmin.class, DestroyAdmin.class }), // dispatchOnly
                false, true);
    }

    /** A handler for facets whose methods must never be invoked in a test. */
    private static final InvocationHandler THROWING = new InvocationHandler() {
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            throw new UnsupportedOperationException("handler method not invoked: " + method);
        }
    };

    /** A custom non-{@code Remote} admin interface used to drive the larger-admin-set
     *  (dynamic {@code @AtomicSerial} admin proxy) round-trip test. */
    public interface FooAdmin {
        void foo() throws RemoteException;
    }

    /**
     * Minimal remote fixture implementing the public {@link HelloService} API, the
     * admin interfaces, and the {@link Remote} bootstrap accessors — the interface set
     * a real {@code AbstractJiniService} exposes.  {@code sayHello} returns a greeting
     * and {@code getLookupGroups} returns {@link #ADMIN_GROUPS} so the forwarding and
     * admin-dispatch tests can observe the loopback call; the remaining infrastructure
     * methods are never invoked by these tests and throw.
     */
    public static final class HelloServiceFixture
            implements HelloService,
                       Administrable, JoinAdmin, DestroyAdmin,
                       ServiceProxyAccessor, ServiceAttributesAccessor,
                       ServiceIDAccessor, CodebaseAccessor {

        private static UnsupportedOperationException nope() {
            return new UnsupportedOperationException("fixture method not invoked");
        }

        @Override public String sayHello(String name) { return "Hello, " + name + "!"; }
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
        // Observable admin method: a dispatched getLookupGroups() reaches here.
        @Override public String[] getLookupGroups() { return ADMIN_GROUPS.clone(); }
        @Override public Entry[] getLookupAttributes() { throw nope(); }
        @Override public void addLookupAttributes(Entry[] a) { throw nope(); }
        @Override public void modifyLookupAttributes(Entry[] t, Entry[] a) { throw nope(); }
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
