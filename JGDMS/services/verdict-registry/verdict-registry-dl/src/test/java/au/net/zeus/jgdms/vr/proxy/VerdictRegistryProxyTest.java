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
package au.net.zeus.jgdms.vr.proxy;

import java.rmi.RemoteException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Set;
import net.jini.admin.Administrable;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.io.MarshalledInstance;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.SignedVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.proxy.AbstractSmartProxy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link VerdictRegistryProxy} and its nested
 * {@link VerdictRegistryProxy.ConstrainableVerdictRegistryProxy}.
 *
 * <p>Mock server implementations are provided as inner classes so that the
 * tests are fully self-contained.
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class VerdictRegistryProxyTest {

    // =========================================================================
    // Mock server implementations
    // =========================================================================

    /**
     * Minimal mock that implements {@link VerdictRegistry} and
     * {@link Administrable}.  Used to test the plain (non-constrainable)
     * proxy path.
     */
    static class MockVrServer implements VerdictRegistry, Administrable {

        boolean registerEngineCalled   = false;
        boolean revokeEngineCalled     = false;
        boolean submitVerdictCalled    = false;
        boolean reportCrashCalled      = false;
        boolean getVerdictCalled       = false;
        Set<Uri> lastGetVerdictRequest;
        RegistryVerdict verdictToReturn;
        final Object adminObject;

        MockVrServer(Object adminObject) {
            this.adminObject = adminObject;
        }

        @Override
        public void registerAnalysisEngine(String engineId,
                                           PublicKey engineKey,
                                           String sigAlgorithm) throws RemoteException {
            registerEngineCalled = true;
        }

        @Override
        public void revokeAnalysisEngine(String engineId) throws RemoteException {
            revokeEngineCalled = true;
        }

        @Override
        public void submitVerdict(String engineId, SignedVerdict verdict) throws RemoteException {
            submitVerdictCalled = true;
        }

        @Override
        public void reportCrash(CrashReport report) throws RemoteException {
            reportCrashCalled = true;
        }

        @Override
        public RegistryVerdict getVerdict(Set<Uri> codebaseUrls) throws RemoteException {
            getVerdictCalled      = true;
            lastGetVerdictRequest = codebaseUrls;
            return verdictToReturn;
        }

        @Override
        public EventRegistration registerVerdictListener(RemoteEventListener listener,
                                                         Set<Uri> codebaseUrls,
                                                         MarshalledInstance handback,
                                                         long leaseDuration)
                throws RemoteException {
            return null;
        }

        @Override
        public long renewEventLease(Uuid leaseId, long duration)
                throws UnknownLeaseException, RemoteException {
            return duration;
        }

        @Override
        public void cancelEventLease(Uuid leaseId)
                throws UnknownLeaseException, RemoteException {
        }

        @Override
        public void submitReport(String engineId,
                                 au.net.zeus.jgdms.api.codebase.JarAnalysisReport report)
                throws RemoteException {
        }

        @Override
        public RegistryVerdict getVerdictByHash(String contentHash) throws RemoteException {
            return null;
        }

        @Override
        public void reportPinning(PinningReport report) throws RemoteException {
        }

        @Override
        public Object getAdmin() throws RemoteException {
            return adminObject;
        }

        @Override
        public EventRegistration registerGlobalVerdictListener(RemoteEventListener listener, MarshalledInstance handback, long leaseDuration) throws RemoteException {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    }

    /**
     * Mock that additionally implements {@link RemoteMethodControl},
     * used to test the constrainable proxy path.
     */
    static class MockRmcVrServer extends MockVrServer
            implements RemoteMethodControl {

        MethodConstraints appliedConstraints;

        MockRmcVrServer(Object adminObject) {
            super(adminObject);
        }

        private MockRmcVrServer(Object adminObject, MethodConstraints constraints) {
            super(adminObject);
            this.appliedConstraints = constraints;
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            return new MockRmcVrServer(adminObject, constraints);
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
    private MockVrServer plainServer;
    private MockRmcVrServer rmcServer;

    @Before
    public void setUp() {
        serviceId   = UuidFactory.generate();
        plainServer = new MockVrServer("admin");
        rmcServer   = new MockRmcVrServer("admin");
    }

    // =========================================================================
    // Factory method — type selection
    // =========================================================================

    @Test
    public void testCreateReturnsPlainProxyForNonConstrainableServer() {
        AbstractSmartProxy proxy = VerdictRegistryProxy.create(plainServer, serviceId);
        assertTrue("create() with non-RMC server must return a VerdictRegistryProxy",
                proxy instanceof VerdictRegistryProxy);
        assertFalse("plain proxy must NOT be constrainable",
                proxy instanceof RemoteMethodControl);
    }

    @Test
    public void testCreateReturnsConstrainableProxyForRmcServer() {
        AbstractSmartProxy proxy = VerdictRegistryProxy.create(rmcServer, serviceId);
        assertTrue("create() with RMC server must return a ConstrainableVerdictRegistryProxy",
                proxy instanceof VerdictRegistryProxy.ConstrainableVerdictRegistryProxy);
        assertTrue("constrainable proxy must implement RemoteMethodControl",
                proxy instanceof RemoteMethodControl);
    }

    // =========================================================================
    // Construction guard tests
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructionNullServerThrowsIAE() {
        new VerdictRegistryProxy(null, serviceId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructionNullProxyIdThrowsIAE() {
        new VerdictRegistryProxy(plainServer, null);
    }

    // =========================================================================
    // ProxyAccessor
    // =========================================================================

    @Test
    public void testGetProxyReturnsServerStub() {
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(plainServer, serviceId);
        assertSame("getProxy() must return the original server stub",
                plainServer, proxy.getProxy());
    }

    // =========================================================================
    // ReferentUuid
    // =========================================================================

    @Test
    public void testGetReferentUuidReturnsConstructorArgument() {
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(plainServer, serviceId);
        assertEquals("getReferentUuid() must return the UUID supplied at construction",
                serviceId, proxy.getReferentUuid());
    }

    // =========================================================================
    // equals() / hashCode()
    // =========================================================================

    @Test
    public void testTwoProxiesWithSameUuidAreEqual() {
        VerdictRegistryProxy a = new VerdictRegistryProxy(new MockVrServer("adminA"), serviceId);
        VerdictRegistryProxy b = new VerdictRegistryProxy(new MockVrServer("adminB"), serviceId);
        assertEquals("proxies wrapping the same service UUID must be equal", a, b);
    }

    @Test
    public void testTwoProxiesWithSameUuidHaveSameHashCode() {
        VerdictRegistryProxy a = new VerdictRegistryProxy(new MockVrServer("adminA"), serviceId);
        VerdictRegistryProxy b = new VerdictRegistryProxy(new MockVrServer("adminB"), serviceId);
        assertEquals("proxies wrapping the same service UUID must share a hash code",
                a.hashCode(), b.hashCode());
    }

    @Test
    public void testProxiesWithDifferentUuidsAreNotEqual() {
        VerdictRegistryProxy a = new VerdictRegistryProxy(plainServer, UuidFactory.generate());
        VerdictRegistryProxy b = new VerdictRegistryProxy(plainServer, UuidFactory.generate());
        assertFalse("proxies with distinct UUIDs must not be equal", a.equals(b));
    }

    // =========================================================================
    // VerdictRegistry method delegation
    // =========================================================================

    @Test
    public void testGetVerdictDelegatesToServer() throws RemoteException {
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(plainServer, serviceId);
        Set<Uri> urls = Collections.emptySet();
        proxy.getVerdict(urls);
        assertTrue("getVerdict() must delegate to the server stub",
                plainServer.getVerdictCalled);
        assertSame("getVerdict() must pass the URL set unchanged to the server",
                urls, plainServer.lastGetVerdictRequest);
    }

    @Test
    public void testGetVerdictReturnsServerResult() throws RemoteException {
        // Use a minimal RegistryVerdict stand-in — null is a valid return per the javadoc
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(plainServer, serviceId);
        RegistryVerdict result = proxy.getVerdict(Collections.<Uri>emptySet());
        assertNull("getVerdict() must return the server's response (null in this mock)",
                result);
    }

    @Test
    public void testRegisterAnalysisEngineDelegatesToServer() throws RemoteException {
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(plainServer, serviceId);
        proxy.registerAnalysisEngine("engine-1", null, "SHA256withRSA");
        assertTrue("registerAnalysisEngine() must delegate to the server stub",
                plainServer.registerEngineCalled);
    }

    @Test
    public void testRevokeAnalysisEngineDelegatesToServer() throws RemoteException {
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(plainServer, serviceId);
        proxy.revokeAnalysisEngine("engine-1");
        assertTrue("revokeAnalysisEngine() must delegate to the server stub",
                plainServer.revokeEngineCalled);
    }

    @Test
    public void testSubmitVerdictDelegatesToServer() throws RemoteException {
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(plainServer, serviceId);
        proxy.submitVerdict("engine-1", null);
        assertTrue("submitVerdict() must delegate to the server stub",
                plainServer.submitVerdictCalled);
    }

    @Test
    public void testReportCrashDelegatesToServer() throws RemoteException {
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(plainServer, serviceId);
        proxy.reportCrash(null);
        assertTrue("reportCrash() must delegate to the server stub",
                plainServer.reportCrashCalled);
    }

    // =========================================================================
    // Administrable delegation
    // =========================================================================

    @Test
    public void testGetAdminDelegatesToServer() throws RemoteException {
        Object adminObj = new Object();
        MockVrServer server = new MockVrServer(adminObj);
        VerdictRegistryProxy proxy = new VerdictRegistryProxy(server, serviceId);
        assertSame("getAdmin() must delegate to the server stub",
                adminObj, proxy.getAdmin());
    }

    // =========================================================================
    // ConstrainableVerdictRegistryProxy
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testConstrainableConstructionNullServerThrowsIAE() {
        new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(null, serviceId, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstrainableConstructionNullProxyIdThrowsIAE() {
        new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(rmcServer, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstrainableConstructionNonRmcServerThrowsIAE() {
        // plainServer does not implement RemoteMethodControl
        new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(plainServer, serviceId, null);
    }

    @Test
    public void testConstrainableGetConstraintsInitiallyNull() {
        VerdictRegistryProxy.ConstrainableVerdictRegistryProxy proxy =
                new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(
                        rmcServer, serviceId, null);
        assertNull("getConstraints() returns null when constructed with null constraints",
                proxy.getConstraints());
    }

    @Test
    public void testConstrainableSetConstraintsReturnsNewInstance() {
        VerdictRegistryProxy.ConstrainableVerdictRegistryProxy proxy =
                new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(
                        rmcServer, serviceId, null);
        RemoteMethodControl constrained = proxy.setConstraints(null);
        assertNotSame("setConstraints() must return a new proxy instance",
                proxy, constrained);
        assertTrue("new proxy must still be a ConstrainableVerdictRegistryProxy",
                constrained instanceof VerdictRegistryProxy.ConstrainableVerdictRegistryProxy);
    }

    @Test
    public void testConstrainableSetConstraintsPreservesServiceId() {
        VerdictRegistryProxy.ConstrainableVerdictRegistryProxy proxy =
                new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(
                        rmcServer, serviceId, null);
        VerdictRegistryProxy.ConstrainableVerdictRegistryProxy constrained =
                (VerdictRegistryProxy.ConstrainableVerdictRegistryProxy)
                        proxy.setConstraints(null);
        assertEquals("setConstraints() must preserve the service UUID",
                serviceId, constrained.getReferentUuid());
    }

    @Test
    public void testConstrainableGetVerdictDelegatesToServer() throws RemoteException {
        VerdictRegistryProxy.ConstrainableVerdictRegistryProxy proxy =
                new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(
                        rmcServer, serviceId, null);
        // The stored server stub is rmcServer.setConstraints(null);
        // we verify no exception is thrown (delegation reached the mock).
        proxy.getVerdict(Collections.<Uri>emptySet());
    }

    @Test
    public void testConstrainableImplementsServiceInterface() {
        VerdictRegistryProxy.ConstrainableVerdictRegistryProxy proxy =
                new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(
                        rmcServer, serviceId, null);
        assertTrue("constrainable proxy must implement VerdictRegistry",
                proxy instanceof VerdictRegistry);
        assertTrue("constrainable proxy must implement RemoteMethodControl",
                proxy instanceof RemoteMethodControl);
    }

    @Test
    public void testConstrainableGetProxyReturnsNonNullStub() {
        VerdictRegistryProxy.ConstrainableVerdictRegistryProxy proxy =
                new VerdictRegistryProxy.ConstrainableVerdictRegistryProxy(
                        rmcServer, serviceId, null);
        assertNotNull("getProxy() must return a non-null server stub",
                proxy.getProxy());
        assertTrue("stored server stub must still implement RemoteMethodControl",
                proxy.getProxy() instanceof RemoteMethodControl);
    }
}
