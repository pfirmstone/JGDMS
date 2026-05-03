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
//package au.net.zeus.jgdms.proxy;
//
//import java.io.IOException;
//import java.rmi.RemoteException;
//import net.jini.admin.Administrable;
//import net.jini.core.constraint.MethodConstraints;
//import net.jini.core.constraint.RemoteMethodControl;
//import net.jini.id.Uuid;
//import net.jini.id.UuidFactory;
//import org.apache.river.api.io.AtomicSerial;
//import org.apache.river.api.io.AtomicSerial.GetArg;
//import org.junit.Test;
//
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertFalse;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertNotSame;
//import static org.junit.Assert.assertNull;
//import static org.junit.Assert.assertSame;
//import static org.junit.Assert.assertTrue;
//
///**
// * Unit tests for {@link AbstractSmartProxy} and its nested
// * {@link AbstractSmartProxy.ConstrainableSmartProxy}.
// *
// * <p>A minimal concrete proxy ({@link ConcreteProxy}) and mock server
// * implementations are defined as package-private inner classes to keep
// * the tests self-contained.
// *
// * @author Peter Firmstone
// * @author GitHub Copilot
// */
//public class AbstractSmartProxyTest {
//
//    // =========================================================================
//    // Helper interfaces / inner classes
//    // =========================================================================
//
//    /** Minimal dummy service interface used by the test proxy. */
//    interface DummyService {
//        void doSomething() throws RemoteException;
//    }
//
//    /**
//     * Minimal concrete, non-constrainable proxy used to exercise
//     * {@link AbstractSmartProxy} behaviour.
//     */
//    @AtomicSerial
//    static class ConcreteProxy extends AbstractSmartProxy implements DummyService {
//
//        private static final long serialVersionUID = 1L;
//
//        ConcreteProxy(Object server, Uuid proxyID) {
//            super(server, proxyID);
//        }
//
//        ConcreteProxy(GetArg arg) throws IOException {
//            super(arg);
//        }
//
//        @Override
//        public void doSomething() throws RemoteException {
//            ((DummyService) server).doSomething();
//        }
//    }
//
//    /**
//     * Minimal concrete constrainable proxy used to exercise
//     * {@link AbstractSmartProxy.ConstrainableSmartProxy} behaviour.
//     */
//    @AtomicSerial
//    static final class ConcreteConstrainableProxy
//            extends AbstractSmartProxy.ConstrainableSmartProxy
//            implements DummyService {
//
//        private static final long serialVersionUID = 1L;
//
//        ConcreteConstrainableProxy(Object server, Uuid proxyID,
//                                   MethodConstraints constraints) {
//            super(server, proxyID, constraints);
//        }
//
//        ConcreteConstrainableProxy(GetArg arg) throws IOException {
//            super(arg);
//        }
//
//        @Override
//        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
//            return new ConcreteConstrainableProxy(server, getReferentUuid(), constraints);
//        }
//
//        @Override
//        public void doSomething() throws RemoteException {
//            ((DummyService) server).doSomething();
//        }
//    }
//
//    /**
//     * Minimal mock server that implements {@link DummyService} and
//     * {@link Administrable} so that both service calls and
//     * {@link AbstractSmartProxy#getAdmin()} can be exercised.
//     */
//    static class MockServer implements DummyService, Administrable {
//
//        boolean doSomethingCalled = false;
//        final Object adminObject;
//
//        MockServer(Object adminObject) {
//            this.adminObject = adminObject;
//        }
//
//        @Override
//        public void doSomething() throws RemoteException {
//            doSomethingCalled = true;
//        }
//
//        @Override
//        public Object getAdmin() throws RemoteException {
//            return adminObject;
//        }
//    }
//
//    /**
//     * Mock server that additionally implements {@link RemoteMethodControl},
//     * required for the constrainable proxy constructor.
//     */
//    static class MockRmcServer extends MockServer
//            implements RemoteMethodControl {
//
//        MethodConstraints appliedConstraints;
//
//        MockRmcServer(Object adminObject) {
//            super(adminObject);
//        }
//
//        MockRmcServer(Object adminObject, MethodConstraints constraints) {
//            super(adminObject);
//            this.appliedConstraints = constraints;
//        }
//
//        @Override
//        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
//            return new MockRmcServer(adminObject, constraints);
//        }
//
//        @Override
//        public MethodConstraints getConstraints() {
//            return appliedConstraints;
//        }
//    }
//
//    // =========================================================================
//    // Construction guard tests
//    // =========================================================================
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testConstructionNullServerThrowsIAE() {
//        new ConcreteProxy(null, UuidFactory.generate());
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testConstructionNullProxyIdThrowsIAE() {
//        new ConcreteProxy(new MockServer("admin"), null);
//    }
//
//    // =========================================================================
//    // ProxyAccessor
//    // =========================================================================
//
//    @Test
//    public void testGetProxyReturnsServerStub() {
//        MockServer server = new MockServer("admin");
//        Uuid uuid = UuidFactory.generate();
//        ConcreteProxy proxy = new ConcreteProxy(server, uuid);
//        assertSame("getProxy() must return the original server stub",
//                server, proxy.getProxy());
//    }
//
//    // =========================================================================
//    // ReferentUuid
//    // =========================================================================
//
//    @Test
//    public void testGetReferentUuidReturnsConstructorArgument() {
//        Uuid uuid = UuidFactory.generate();
//        ConcreteProxy proxy = new ConcreteProxy(new MockServer("admin"), uuid);
//        assertEquals("getReferentUuid() must return the UUID supplied at construction",
//                uuid, proxy.getReferentUuid());
//    }
//
//    // =========================================================================
//    // equals() / hashCode()
//    // =========================================================================
//
//    @Test
//    public void testTwoProxiesWithSameUuidAreEqual() {
//        Uuid uuid = UuidFactory.generate();
//        ConcreteProxy a = new ConcreteProxy(new MockServer("admin1"), uuid);
//        ConcreteProxy b = new ConcreteProxy(new MockServer("admin2"), uuid);
//        assertEquals("proxies with the same UUID must be equal", a, b);
//    }
//
//    @Test
//    public void testTwoProxiesWithSameUuidHaveSameHashCode() {
//        Uuid uuid = UuidFactory.generate();
//        ConcreteProxy a = new ConcreteProxy(new MockServer("admin1"), uuid);
//        ConcreteProxy b = new ConcreteProxy(new MockServer("admin2"), uuid);
//        assertEquals("proxies with the same UUID must have equal hash codes",
//                a.hashCode(), b.hashCode());
//    }
//
//    @Test
//    public void testProxiesWithDifferentUuidsAreNotEqual() {
//        ConcreteProxy a = new ConcreteProxy(new MockServer("admin"), UuidFactory.generate());
//        ConcreteProxy b = new ConcreteProxy(new MockServer("admin"), UuidFactory.generate());
//        assertFalse("proxies with distinct UUIDs must not be equal", a.equals(b));
//    }
//
//    @Test
//    public void testProxyEqualsItself() {
//        ConcreteProxy proxy = new ConcreteProxy(new MockServer("admin"), UuidFactory.generate());
//        assertEquals("a proxy must equal itself", proxy, proxy);
//    }
//
//    @Test
//    public void testProxyNotEqualToNull() {
//        ConcreteProxy proxy = new ConcreteProxy(new MockServer("admin"), UuidFactory.generate());
//        assertFalse("a proxy must not equal null", proxy.equals(null));
//    }
//
//    // =========================================================================
//    // Administrable
//    // =========================================================================
//
//    @Test
//    public void testGetAdminDelegatesToServer() throws RemoteException {
//        Object adminObj = new Object();
//        MockServer server = new MockServer(adminObj);
//        ConcreteProxy proxy = new ConcreteProxy(server, UuidFactory.generate());
//        assertSame("getAdmin() must delegate to the server stub",
//                adminObj, proxy.getAdmin());
//    }
//
//    // =========================================================================
//    // ConstrainableSmartProxy construction guard tests
//    // =========================================================================
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testConstrainableConstructionNullServerThrowsIAE() {
//        new ConcreteConstrainableProxy(null, UuidFactory.generate(), null);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testConstrainableConstructionNullProxyIdThrowsIAE() {
//        new ConcreteConstrainableProxy(new MockRmcServer("admin"), null, null);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testConstrainableConstructionNonRmcServerThrowsIAE() {
//        // MockServer does not implement RemoteMethodControl
//        new ConcreteConstrainableProxy(new MockServer("admin"), UuidFactory.generate(), null);
//    }
//
//    // =========================================================================
//    // ConstrainableSmartProxy.getConstraints() / setConstraints()
//    // =========================================================================
//
//    @Test
//    public void testConstrainableGetConstraintsDelegatesToServer() {
//        MockRmcServer server = new MockRmcServer("admin", null);
//        Uuid uuid = UuidFactory.generate();
//        ConcreteConstrainableProxy proxy =
//                new ConcreteConstrainableProxy(server, uuid, null);
//        // The server stored in the proxy is the result of server.setConstraints(null),
//        // which is a new MockRmcServer with null constraints.
//        assertNull("getConstraints() returns null when no constraints are set",
//                proxy.getConstraints());
//    }
//
//    @Test
//    public void testConstrainableSetConstraintsReturnsNewProxyInstance() {
//        MockRmcServer server = new MockRmcServer("admin");
//        Uuid uuid = UuidFactory.generate();
//        ConcreteConstrainableProxy proxy =
//                new ConcreteConstrainableProxy(server, uuid, null);
//        RemoteMethodControl constrained = proxy.setConstraints(null);
//        assertNotSame("setConstraints() must return a new proxy instance", proxy, constrained);
//        assertSame("new proxy must carry the same UUID",
//                uuid, ((ConcreteConstrainableProxy) constrained).getReferentUuid());
//    }
//
//    @Test
//    public void testConstrainableProxyIsAlsoConcreteProxy() {
//        MockRmcServer server = new MockRmcServer("admin");
//        Uuid uuid = UuidFactory.generate();
//        ConcreteConstrainableProxy proxy =
//                new ConcreteConstrainableProxy(server, uuid, null);
//        assertTrue("constrainable proxy must be an AbstractSmartProxy",
//                proxy instanceof AbstractSmartProxy);
//        assertTrue("constrainable proxy must implement RemoteMethodControl",
//                proxy instanceof RemoteMethodControl);
//    }
//}
