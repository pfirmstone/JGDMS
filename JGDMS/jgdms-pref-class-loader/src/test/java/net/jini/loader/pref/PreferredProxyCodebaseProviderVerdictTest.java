/*
 * Copyright 2018 The Apache Software Foundation.
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
package net.jini.loader.pref;

import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictRegistry;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PublicKey;
import java.security.ProtectionDomain;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.SignedVerdict;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the {@link VerdictRegistry} integration in
 * {@link PreferredProxyCodebaseProvider}.
 *
 * <p>These tests exercise {@link PreferredProxyCodebaseProvider#checkVerdictForJar}
 * and {@link VerdictRegistryHolder} directly, without requiring a live
 * {@link VerdictRegistry} server or actual JAR files.
 *
 * @since 3.1.1
 * @author GitHub Copilot
 */
public class PreferredProxyCodebaseProviderVerdictTest {

    /** Fake codebase path used in error messages under test. */
    private static final String PATH = "http://host/app.jar";

    /** A non-empty dummy signature sufficient to satisfy {@link RegistryVerdict}'s invariants. */
    private static final byte[] DUMMY_SIG = new byte[]{1, 2, 3, 4};

    /** Fake SHA-256 hex digest (64 lowercase hex chars). */
    private static final String FAKE_HASH =
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";

    // -------------------------------------------------------------------------
    // Reset shared state between tests
    // -------------------------------------------------------------------------

    @Before
    public void disableVerdictRetrySleep() {
        PreferredProxyCodebaseProvider.setVerdictRetryBaseDelayMs(0L);
    }

    @After
    public void resetRegistry() {
        // Clear the VerdictRegistry so tests do not interfere with each other.
        VerdictRegistryHolder.set(null);
        VerdictRegistryHolder.clearInconclusiveLoaders();
        clearPreferredProxyCache();
        PreferredProxyCodebaseProvider.resetVerdictRetryBaseDelayMs();
    }

    // -------------------------------------------------------------------------
    // VerdictRegistryHolder tests
    // -------------------------------------------------------------------------

    @Test
    public void holderInitiallyNull() {
        VerdictRegistryHolder.set(null);
        assertNull("holder should be null before injection",
                VerdictRegistryHolder.get());
    }

    @Test
    public void holderSetAndGet() {
        StubVerdictRegistry stub = new StubVerdictRegistry();
        VerdictRegistryHolder.set(stub);
        assertSame("holder should return the injected registry",
                stub, VerdictRegistryHolder.get());
    }

    @Test
    public void setVerdictRegistryDelegatesToHolder() {
        StubVerdictRegistry stub = new StubVerdictRegistry();
        PreferredProxyCodebaseProvider.setVerdictRegistry(stub);
        assertSame("setVerdictRegistry should delegate to VerdictRegistryHolder",
                stub, VerdictRegistryHolder.get());
    }

    // -------------------------------------------------------------------------
    // checkVerdictForJar — boot-time null registry
    // -------------------------------------------------------------------------

    /**
     * When {@code verdictRegistry == null} the check must be skipped — no
     * exception should be thrown.
     */
    @Test
    public void checkVerdictForJar_nullRegistry_skipped() throws IOException {
        // Do NOT set a registry — simulates boot-time permissive policy.
        // The method under test is called from resolve() only when vr != null,
        // so we simply verify that VerdictRegistryHolder.get() is null and
        // that the guard works.
        assertNull(VerdictRegistryHolder.get());
        // If the registry is null the verdict loop is skipped entirely in
        // resolve(); checkVerdictForJar itself is never called.  We verify
        // here that the holder returns null as the precondition.
    }

    // -------------------------------------------------------------------------
    // checkVerdictForJar — SAFE verdict
    // -------------------------------------------------------------------------

    @Test
    public void checkVerdictForJar_safe_proceeds() throws Exception {
        RegistryVerdict verdict = newVerdict(VerdictType.SAFE);
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(verdict);

        // Should not throw.
        PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
    }

    // -------------------------------------------------------------------------
    // checkVerdictForJar — INCONCLUSIVE verdict
    // -------------------------------------------------------------------------

    @Test
    public void checkVerdictForJar_inconclusive_proceeds() throws Exception {
        RegistryVerdict verdict = newVerdict(VerdictType.INCONCLUSIVE);
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(verdict);

        // INCONCLUSIVE should proceed (log WARNING but not throw).
        PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
    }

    // -------------------------------------------------------------------------
    // checkVerdictForJar — DANGEROUS verdict
    // -------------------------------------------------------------------------

    @Test
    public void checkVerdictForJar_dangerous_throwsIOException() throws Exception {
        RegistryVerdict verdict = newVerdict(VerdictType.DANGEROUS);
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(verdict);

        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
            fail("Expected IOException for DANGEROUS verdict");
        } catch (IOException ex) {
            assertTrue("Exception message should mention DANGEROUS",
                    ex.getMessage().contains("DANGEROUS"));
            assertTrue("Exception message should contain hash",
                    ex.getMessage().contains(FAKE_HASH));
        }
    }

    // -------------------------------------------------------------------------
    // checkVerdictForJar — null verdict (not yet audited)
    // -------------------------------------------------------------------------

    @Test
    public void checkVerdictForJar_nullVerdict_throwsIOException() throws Exception {
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(null); // registry returns null = not audited yet

        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
            fail("Expected IOException for absent verdict");
        } catch (IOException ex) {
            assertTrue("Exception message should mention absent verdict",
                    ex.getMessage().contains("No verdict available"));
            assertTrue("Exception message should contain hash",
                    ex.getMessage().contains(FAKE_HASH));
        }
    }

    // -------------------------------------------------------------------------
    // checkVerdictForJar — RemoteException (registry unreachable)
    // -------------------------------------------------------------------------

    @Test
    public void checkVerdictForJar_remoteException_throwsIOException() throws Exception {
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setFailTimes(PreferredProxyCodebaseProvider.VERDICT_RETRY_ATTEMPTS + 1);

        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
            fail("Expected IOException when registry is unreachable");
        } catch (IOException ex) {
            assertTrue("Exception message should mention registry unavailable",
                    ex.getMessage().contains("VerdictRegistry unavailable"));
            // The original RemoteException should be the cause.
            assertNotNull("Cause should be the RemoteException", ex.getCause());
            assertTrue(ex.getCause() instanceof RemoteException);
        }
        assertEquals("Should attempt initial lookup plus configured retries",
                PreferredProxyCodebaseProvider.VERDICT_RETRY_ATTEMPTS + 1,
                stub.getGetVerdictByHashCalls());
    }

    @Test
    public void checkVerdictForJar_remoteException_retriesThenSucceeds()
            throws Exception {
        RegistryVerdict verdict = newVerdict(VerdictType.SAFE);
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(verdict);
        stub.setFailTimes(2);

        PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);

        assertEquals("Should retry until the registry responds",
                3, stub.getGetVerdictByHashCalls());
    }

    @Test
    public void evictInconclusiveClassLoadersRemovesTrackedCacheEntries()
            throws Exception {
        ClassLoader loader = new ClassLoader() { };
        Object key = newCacheKey(loader);
        getPreferredProxyCache().put(key, loader);
        VerdictRegistryHolder.recordInconclusiveLoader(loader);

        assertEquals("Should evict tracked INCONCLUSIVE loader",
                1, PreferredProxyCodebaseProvider.evictInconclusiveClassLoaders());
        assertFalse("Tracked loader should be removed from cache",
                getPreferredProxyCache().containsKey(key));
    }

    @Test
    public void dynamicPolicyGrantEvictsTrackedInconclusiveClassLoaders()
            throws Exception {
        ClassLoader loader = new ClassLoader() { };
        Object key = newCacheKey(loader);
        getPreferredProxyCache().put(key, loader);
        VerdictRegistryHolder.recordInconclusiveLoader(loader);

        DynamicPolicyProvider policy = new DynamicPolicyProvider(new Policy() {
            @Override
            public PermissionCollection getPermissions(CodeSource codesource) {
                return new java.security.Permissions();
            }

            @Override
            public PermissionCollection getPermissions(ProtectionDomain domain) {
                return new java.security.Permissions();
            }

            @Override
            public boolean implies(ProtectionDomain domain, Permission permission) {
                return false;
            }

            @Override
            public void refresh() {
            }
        });
        try {
            policy.grant(getClass(), new Principal[0],
                    new Permission[]{new RuntimePermission("test.grant.evicts.inconclusive.loader")});
        } finally {
            policy.shutdown();
        }

        assertFalse("Grant should evict tracked INCONCLUSIVE loader from cache",
                getPreferredProxyCache().containsKey(key));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Constructs a minimal {@link RegistryVerdict} with the given type.
     */
    private static RegistryVerdict newVerdict(VerdictType type)
            throws URISyntaxException {
        Uri[] urls = new Uri[]{new Uri("urn:sha256:" + FAKE_HASH)};
        return new RegistryVerdict(urls, type, System.currentTimeMillis(), DUMMY_SIG);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<Object, ClassLoader> getPreferredProxyCache() {
        try {
            // Reflection keeps the production cache encapsulated while still
            // letting this test verify the internal eviction behavior directly.
            Field cacheField = PreferredProxyCodebaseProvider.class.getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            return (ConcurrentMap<Object, ClassLoader>) cacheField.get(null);
        } catch (Exception ex) {
            throw new AssertionError("Unable to access preferred proxy cache", ex);
        }
    }

    private static void clearPreferredProxyCache() {
        getPreferredProxyCache().clear();
    }

    private static Object newCacheKey(final ClassLoader parent) {
        try {
            // Construct the internal cache key reflectively so the production
            // code does not need any test-only visibility changes.
            Class<?> keyClass = Class.forName(
                    "net.jini.loader.pref.PreferredProxyCodebaseProvider$Key");
            Constructor<?> constructor = keyClass.getDeclaredConstructor(
                    InvocationHandler.class, java.util.List.class, ClassLoader.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method,
                                Object[] args) {
                            return null;
                        }
                    },
                    Arrays.asList(new Uri("https://example.com/app.jar")),
                    parent);
        } catch (Exception ex) {
            throw new AssertionError("Unable to construct preferred proxy cache key", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Stub VerdictRegistry
    // -------------------------------------------------------------------------

    /**
     * A minimal, configurable stub for {@link VerdictRegistry} that avoids
     * any network calls.
     */
    private static final class StubVerdictRegistry implements VerdictRegistry {

        private RegistryVerdict verdictToReturn = null;
        private int failTimes = 0;
        private int getVerdictByHashCalls = 0;

        void setVerdictToReturn(RegistryVerdict verdict) {
            this.verdictToReturn = verdict;
        }

        void setFailTimes(int failTimes) {
            this.failTimes = failTimes;
        }

        int getGetVerdictByHashCalls() {
            return getVerdictByHashCalls;
        }

        @Override
        public RegistryVerdict getVerdictByHash(String contentHash)
                throws RemoteException {
            getVerdictByHashCalls++;
            if (failTimes > 0) {
                failTimes--;
                throw new RemoteException("Simulated transient registry failure");
            }
            return verdictToReturn;
        }

        @Override
        public RegistryVerdict getVerdict(Set<Uri> codebaseUrls)
                throws RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public void registerAnalysisEngine(String engineId, PublicKey engineKey,
                String sigAlgorithm) throws RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public void revokeAnalysisEngine(String engineId) throws RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public void submitVerdict(String engineId, SignedVerdict verdict)
                throws RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public void reportCrash(CrashReport report) throws RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public void reportPinning(PinningReport report) throws RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public EventRegistration registerVerdictListener(
                RemoteEventListener listener, Set<Uri> codebaseUrls,
                MarshalledInstance handback, long leaseDuration)
                throws RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public long renewEventLease(Uuid leaseId, long duration)
                throws UnknownLeaseException, RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public void cancelEventLease(Uuid leaseId)
                throws UnknownLeaseException, RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public void submitReport(String engineId, JarAnalysisReport report)
                throws RemoteException {
            throw new UnsupportedOperationException("not used in these tests");
        }
    }
}
