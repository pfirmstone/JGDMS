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
package au.net.zeus.jgdms.bae.proxy;

import java.rmi.RemoteException;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import au.net.zeus.jgdms.api.codebase.AnalysisException;
import au.net.zeus.jgdms.api.codebase.AnalysisRequest;
import au.net.zeus.jgdms.api.codebase.BytecodeAnalysisEngine;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link BytecodeAnalysisEngineProxy} and its nested
 * {@link BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy}.
 *
 * <p>Mock server implementations are provided as inner classes so that the
 * tests are fully self-contained.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class BytecodeAnalysisEngineProxyTest {

    // =========================================================================
    // Mock server implementations
    // =========================================================================

    /**
     * Minimal mock that implements only {@link BytecodeAnalysisEngine}
     * (and {@link Administrable}).  Used to test the plain (non-constrainable)
     * proxy path.
     */
    static class MockBaeServer implements BytecodeAnalysisEngine, Administrable {

        final Object adminObject;

        MockBaeServer(Object adminObject) {
            this.adminObject = adminObject;
        }

        @Override
        public JarAnalysisReport analyzeJar(AnalysisRequest request)
                throws AnalysisException, RemoteException {
            return null;
        }

        @Override
        public Object getAdmin() throws RemoteException {
            return adminObject;
        }
    }

    /**
     * Mock that additionally implements {@link RemoteMethodControl},
     * used to test the constrainable proxy path.
     */
    static class MockRmcBaeServer extends MockBaeServer
            implements RemoteMethodControl {

        MethodConstraints appliedConstraints;

        MockRmcBaeServer(Object adminObject) {
            super(adminObject);
        }

        private MockRmcBaeServer(Object adminObject, MethodConstraints constraints) {
            super(adminObject);
            this.appliedConstraints = constraints;
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new MockRmcBaeServer(adminObject, constraints);
        }

        @Override
        public MethodConstraints getConstraints() {
            return appliedConstraints;
        }
    }

    // =========================================================================
    // Test fixtures
    // =========================================================================

    private Uuid serviceId;
    private MockBaeServer plainServer;
    private MockRmcBaeServer rmcServer;

    @Before
    public void setUp() {
        serviceId   = UuidFactory.generate();
        plainServer = new MockBaeServer("admin");
        rmcServer   = new MockRmcBaeServer("admin");
    }

    // =========================================================================
    // Factory method — type selection
    // =========================================================================

    @Test
    public void testCreateReturnsPlainProxyForNonConstrainableServer() {
        AbstractSmartProxy proxy = BytecodeAnalysisEngineProxy.create(plainServer, serviceId);
        assertTrue("create() with non-RMC server must return a BytecodeAnalysisEngineProxy",
                proxy instanceof BytecodeAnalysisEngineProxy);
        assertFalse("plain proxy must NOT be constrainable",
                proxy instanceof RemoteMethodControl);
    }

    @Test
    public void testCreateReturnsConstrainableProxyForRmcServer() {
        AbstractSmartProxy proxy = BytecodeAnalysisEngineProxy.create(rmcServer, serviceId);
        assertTrue("create() with RMC server must return a ConstrainableBytecodeAnalysisEngineProxy",
                proxy instanceof BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy);
        assertTrue("constrainable proxy must implement RemoteMethodControl",
                proxy instanceof RemoteMethodControl);
    }

    // =========================================================================
    // Construction guard tests
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructionNullServerThrowsIAE() {
        new BytecodeAnalysisEngineProxy(null, serviceId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructionNullProxyIdThrowsIAE() {
        new BytecodeAnalysisEngineProxy(plainServer, null);
    }

    // =========================================================================
    // ProxyAccessor
    // =========================================================================

    @Test
    public void testGetProxyReturnsServerStub() {
        BytecodeAnalysisEngineProxy proxy =
                new BytecodeAnalysisEngineProxy(plainServer, serviceId);
        assertSame("getProxy() must return the original server stub",
                plainServer, proxy.getProxy());
    }

    // =========================================================================
    // ReferentUuid
    // =========================================================================

    @Test
    public void testGetReferentUuidReturnsConstructorArgument() {
        BytecodeAnalysisEngineProxy proxy =
                new BytecodeAnalysisEngineProxy(plainServer, serviceId);
        assertEquals("getReferentUuid() must return the UUID supplied at construction",
                serviceId, proxy.getReferentUuid());
    }

    // =========================================================================
    // equals() / hashCode()
    // =========================================================================

    @Test
    public void testTwoProxiesWithSameUuidAreEqual() {
        BytecodeAnalysisEngineProxy a =
                new BytecodeAnalysisEngineProxy(new MockBaeServer("adminA"), serviceId);
        BytecodeAnalysisEngineProxy b =
                new BytecodeAnalysisEngineProxy(new MockBaeServer("adminB"), serviceId);
        assertEquals("proxies wrapping the same service UUID must be equal", a, b);
    }

    @Test
    public void testTwoProxiesWithSameUuidHaveSameHashCode() {
        BytecodeAnalysisEngineProxy a =
                new BytecodeAnalysisEngineProxy(new MockBaeServer("adminA"), serviceId);
        BytecodeAnalysisEngineProxy b =
                new BytecodeAnalysisEngineProxy(new MockBaeServer("adminB"), serviceId);
        assertEquals("proxies wrapping the same service UUID must share a hash code",
                a.hashCode(), b.hashCode());
    }

    @Test
    public void testProxiesWithDifferentUuidsAreNotEqual() {
        BytecodeAnalysisEngineProxy a =
                new BytecodeAnalysisEngineProxy(plainServer, UuidFactory.generate());
        BytecodeAnalysisEngineProxy b =
                new BytecodeAnalysisEngineProxy(plainServer, UuidFactory.generate());
        assertFalse("proxies with distinct UUIDs must not be equal", a.equals(b));
    }

    // =========================================================================
    // Administrable delegation
    // =========================================================================

    @Test
    public void testGetAdminDelegatesToServer() throws RemoteException {
        Object adminObj = new Object();
        MockBaeServer server = new MockBaeServer(adminObj);
        BytecodeAnalysisEngineProxy proxy =
                new BytecodeAnalysisEngineProxy(server, serviceId);
        assertSame("getAdmin() must delegate to the server stub",
                adminObj, proxy.getAdmin());
    }

    // =========================================================================
    // ConstrainableBytecodeAnalysisEngineProxy
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testConstrainableConstructionNullServerThrowsIAE() {
        new BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy(
                null, serviceId, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstrainableConstructionNullProxyIdThrowsIAE() {
        new BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy(
                rmcServer, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstrainableConstructionNonRmcServerThrowsIAE() {
        // plainServer does not implement RemoteMethodControl
        new BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy(
                plainServer, serviceId, null);
    }

    @Test
    public void testConstrainableGetProxyReturnsConstrainedServerStub() {
        BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy proxy =
                new BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy(
                        rmcServer, serviceId, null);
        // The stored server is rmcServer.setConstraints(null), a new MockRmcBaeServer
        assertNotNull("getProxy() must return a non-null server stub",
                proxy.getProxy());
        assertTrue("stored server stub must still implement RemoteMethodControl",
                proxy.getProxy() instanceof RemoteMethodControl);
    }

    @Test
    public void testConstrainableGetConstraintsInitiallyNull() {
        BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy proxy =
                new BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy(
                        rmcServer, serviceId, null);
        assertNull("getConstraints() returns null when constructed with null constraints",
                proxy.getConstraints());
    }

    @Test
    public void testConstrainableSetConstraintsReturnsNewInstance() {
        BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy proxy =
                new BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy(
                        rmcServer, serviceId, null);
        RemoteMethodControl constrained = proxy.setConstraints(null);
        assertNotSame("setConstraints() must return a new proxy instance",
                proxy, constrained);
        assertTrue("new proxy must still be a ConstrainableBytecodeAnalysisEngineProxy",
                constrained instanceof BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy);
    }

    @Test
    public void testConstrainableSetConstraintsPreservesServiceId() {
        BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy proxy =
                new BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy(
                        rmcServer, serviceId, null);
        BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy constrained =
                (BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy)
                        proxy.setConstraints(null);
        assertEquals("setConstraints() must preserve the service UUID",
                serviceId, constrained.getReferentUuid());
    }

    @Test
    public void testConstrainableImplementsServiceInterface() {
        BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy proxy =
                new BytecodeAnalysisEngineProxy.ConstrainableBytecodeAnalysisEngineProxy(
                        rmcServer, serviceId, null);
        assertTrue("constrainable proxy must implement BytecodeAnalysisEngine",
                proxy instanceof BytecodeAnalysisEngine);
        assertTrue("constrainable proxy must implement RemoteMethodControl",
                proxy instanceof RemoteMethodControl);
    }
}
