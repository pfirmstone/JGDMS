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
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.PublicKey;
import java.util.Set;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
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
        PreferredProxyCodebaseProvider.resetVerdictRetryBaseDelayMs();
        PreferredProxyCodebaseProvider.clearVerdictCache();
        PreferredProxyCodebaseProvider.resetInconclusiveStrictMode();
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
    // jgdms.proxy.maxConcurrentJarLoads parsing
    // -------------------------------------------------------------------------

    @Test
    public void parseMaxConcurrentJarLoads_null_usesDefault() {
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_MAX_CONCURRENT_JAR_LOADS,
                PreferredProxyCodebaseProvider.parseMaxConcurrentJarLoads(null));
    }

    @Test
    public void parseMaxConcurrentJarLoads_invalid_usesDefault() {
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_MAX_CONCURRENT_JAR_LOADS,
                PreferredProxyCodebaseProvider.parseMaxConcurrentJarLoads("not-a-number"));
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_MAX_CONCURRENT_JAR_LOADS,
                PreferredProxyCodebaseProvider.parseMaxConcurrentJarLoads("0"));
    }

    @Test
    public void parseMaxConcurrentJarLoads_valid_usesConfiguredValue() {
        assertEquals(7, PreferredProxyCodebaseProvider.parseMaxConcurrentJarLoads("7"));
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
        assertFalse(PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH));
    }

    // -------------------------------------------------------------------------
    // checkVerdictForJar — INCONCLUSIVE verdict
    // -------------------------------------------------------------------------

    @Test
    public void checkVerdictForJar_inconclusive_proceeds() throws Exception {
        RegistryVerdict verdict = newVerdict(VerdictType.INCONCLUSIVE);
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(verdict);

        // Disable strict mode: INCONCLUSIVE should proceed (log WARNING but not throw).
        PreferredProxyCodebaseProvider.setInconclusiveStrictMode(false);
        assertTrue(PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH));
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

        assertFalse(PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH));

        assertEquals("Should retry until the registry responds",
                3, stub.getGetVerdictByHashCalls());
    }

    // -------------------------------------------------------------------------
    // WI48 — in-memory verdict cache tests
    // -------------------------------------------------------------------------

    @Test
    public void parseVerdictCacheTtlMs_null_usesDefault() {
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_VERDICT_CACHE_TTL_MS,
                PreferredProxyCodebaseProvider.parseVerdictCacheTtlMs(null));
    }

    @Test
    public void parseVerdictCacheTtlMs_invalid_usesDefault() {
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_VERDICT_CACHE_TTL_MS,
                PreferredProxyCodebaseProvider.parseVerdictCacheTtlMs("not-a-number"));
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_VERDICT_CACHE_TTL_MS,
                PreferredProxyCodebaseProvider.parseVerdictCacheTtlMs("-1"));
    }

    @Test
    public void parseVerdictCacheTtlMs_zero_disablesCache() {
        assertEquals(0L,
                PreferredProxyCodebaseProvider.parseVerdictCacheTtlMs("0"));
    }

    @Test
    public void parseVerdictCacheTtlMs_valid_usesConfiguredValue() {
        assertEquals(60_000L,
                PreferredProxyCodebaseProvider.parseVerdictCacheTtlMs("60000"));
    }

    /**
     * On a successful verdict lookup the verdict should be stored in the cache
     * so it can be served during a future registry outage.
     */
    @Test
    public void verdictCache_successfulLookupPopulatesCache() throws Exception {
        RegistryVerdict verdict = newVerdict(VerdictType.SAFE);
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(verdict);

        PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);

        // Verify the cache was populated: now make the registry fail entirely and
        // confirm the fallback serves the cached verdict.
        StubVerdictRegistry alwaysFails = new StubVerdictRegistry();
        alwaysFails.setFailTimes(PreferredProxyCodebaseProvider.VERDICT_RETRY_ATTEMPTS + 1);

        // Should NOT throw because we have a valid cached verdict.
        assertFalse(PreferredProxyCodebaseProvider.checkVerdictForJar(
                alwaysFails, FAKE_HASH, PATH));
    }

    /**
     * When the VerdictRegistry is unreachable on all attempts AND a fresh cache
     * entry exists, the cached verdict is returned instead of throwing.
     */
    @Test
    public void verdictCache_registryOutage_freshentryShouldNotThrow() throws Exception {
        // Pre-populate the cache with a SAFE verdict.
        PreferredProxyCodebaseProvider.CachedVerdict cached =
                new PreferredProxyCodebaseProvider.CachedVerdict(
                        newVerdict(VerdictType.SAFE),
                        System.currentTimeMillis());
        PreferredProxyCodebaseProvider.VERDICT_CACHE.put(FAKE_HASH, cached);

        StubVerdictRegistry alwaysFails = new StubVerdictRegistry();
        alwaysFails.setFailTimes(PreferredProxyCodebaseProvider.VERDICT_RETRY_ATTEMPTS + 1);

        // Should not throw.
        assertFalse(PreferredProxyCodebaseProvider.checkVerdictForJar(
                alwaysFails, FAKE_HASH, PATH));
    }

    /**
     * When the VerdictRegistry is unreachable AND the cache entry is expired
     * (older than the TTL), the load must be refused (IOException).
     */
    @Test
    public void verdictCache_registryOutage_expiredEntryThrows() throws Exception {
        // Pre-populate the cache with an old verdict (captured in the past).
        long expiredTime = System.currentTimeMillis()
                - PreferredProxyCodebaseProvider.DEFAULT_VERDICT_CACHE_TTL_MS - 1_000L;
        PreferredProxyCodebaseProvider.CachedVerdict cached =
                new PreferredProxyCodebaseProvider.CachedVerdict(
                        newVerdict(VerdictType.SAFE),
                        expiredTime);
        PreferredProxyCodebaseProvider.VERDICT_CACHE.put(FAKE_HASH, cached);

        StubVerdictRegistry alwaysFails = new StubVerdictRegistry();
        alwaysFails.setFailTimes(PreferredProxyCodebaseProvider.VERDICT_RETRY_ATTEMPTS + 1);

        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(alwaysFails, FAKE_HASH, PATH);
            fail("Expected IOException: cache entry is expired");
        } catch (IOException ex) {
            assertTrue("Exception should mention registry unavailable",
                    ex.getMessage().contains("VerdictRegistry unavailable"));
        }
    }

    /**
     * A {@link PreferredProxyCodebaseProvider.CachedVerdict} is alive when
     * its age is within the TTL and expired when outside it.
     */
    @Test
    public void cachedVerdict_isAlive_withinTtl() throws Exception {
        long now = System.currentTimeMillis();
        PreferredProxyCodebaseProvider.CachedVerdict cv =
                new PreferredProxyCodebaseProvider.CachedVerdict(
                        newVerdict(VerdictType.SAFE), now - 1_000L);
        assertTrue("1 s old entry with 5 min TTL should be alive",
                cv.isAlive(now, 300_000L));
    }

    @Test
    public void cachedVerdict_isAlive_expiredTtl() throws Exception {
        long now = System.currentTimeMillis();
        PreferredProxyCodebaseProvider.CachedVerdict cv =
                new PreferredProxyCodebaseProvider.CachedVerdict(
                        newVerdict(VerdictType.SAFE), now - 400_000L);
        assertFalse("400 s old entry with 5 min TTL should be expired",
                cv.isAlive(now, 300_000L));
    }

    @Test
    public void cachedVerdict_isAlive_zeroTtlAlwaysExpired() throws Exception {
        long now = System.currentTimeMillis();
        PreferredProxyCodebaseProvider.CachedVerdict cv =
                new PreferredProxyCodebaseProvider.CachedVerdict(
                        newVerdict(VerdictType.SAFE), now);
        assertFalse("TTL=0 means cache is disabled, should always be expired",
                cv.isAlive(now, 0L));
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

    // -------------------------------------------------------------------------
    // BootstrapPermission tests
    // -------------------------------------------------------------------------

    @Test
    public void bootstrapPermission_defaultConstructorHasTargetName() {
        BootstrapPermission bp = new BootstrapPermission();
        assertEquals(BootstrapPermission.TARGET_NAME, bp.getName());
    }

    @Test
    public void bootstrapPermission_nameConstructor() {
        BootstrapPermission bp = new BootstrapPermission("loadCodebase");
        assertEquals("loadCodebase", bp.getName());
    }

    @Test
    public void bootstrapPermission_impliesSameName() {
        BootstrapPermission a = new BootstrapPermission("loadCodebase");
        BootstrapPermission b = new BootstrapPermission("loadCodebase");
        assertTrue("identical BootstrapPermissions should imply each other",
                a.implies(b));
    }

    @Test
    public void bootstrapPermission_doesNotImplyOtherPermission() {
        BootstrapPermission bp = new BootstrapPermission("loadCodebase");
        assertFalse("BootstrapPermission should not imply a RuntimePermission",
                bp.implies(new RuntimePermission("exitVM")));
    }

    // -------------------------------------------------------------------------
    // computeIndividualJarDigests tests
    // -------------------------------------------------------------------------

    @Test
    public void computeIndividualJarDigests_singleJar_matchesDirectDigest()
            throws Exception {
        java.io.File tmp = java.io.File.createTempFile("testjar", ".jar");
        tmp.deleteOnExit();
        byte[] content = new byte[]{10, 20, 30, 40};
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
            fos.write(content);
        }
        // codebase[0] = dummy dir (skipped), codebase[1] = the JAR
        java.net.URL[] codebase = new java.net.URL[]{
            new java.net.URL("file:///dummy/dir/"),
            tmp.toURI().toURL()
        };

        byte[][] result = PreferredProxyCodebaseProvider
                .computeIndividualJarDigests(codebase, "SHA-256");

        assertNotNull(result);
        assertEquals("only one non-directory JAR", 1, result.length);
        assertEquals("SHA-256 digest is 32 bytes", 32, result[0].length);

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        assertArrayEquals("digest must equal direct SHA-256 of file content",
                md.digest(content), result[0]);
    }

    @Test
    public void computeIndividualJarDigests_multipleJars_eachDigestIndependent()
            throws Exception {
        java.io.File tmp1 = java.io.File.createTempFile("jar1", ".jar");
        java.io.File tmp2 = java.io.File.createTempFile("jar2", ".jar");
        tmp1.deleteOnExit();
        tmp2.deleteOnExit();
        byte[] content1 = new byte[]{1, 2, 3};
        byte[] content2 = new byte[]{4, 5, 6, 7};
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp1)) {
            fos.write(content1);
        }
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp2)) {
            fos.write(content2);
        }
        java.net.URL[] codebase = new java.net.URL[]{
            tmp1.toURI().toURL(),
            tmp2.toURI().toURL()
        };

        byte[][] result = PreferredProxyCodebaseProvider
                .computeIndividualJarDigests(codebase, "SHA-256");

        assertEquals("two JARs → two digests", 2, result.length);
        assertFalse("digests for different JARs must differ",
                java.util.Arrays.equals(result[0], result[1]));

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        assertArrayEquals("digest[0] must match SHA-256 of jar1",
                md.digest(content1), result[0]);
        md.reset();
        assertArrayEquals("digest[1] must match SHA-256 of jar2",
                md.digest(content2), result[1]);
    }

    @Test
    public void computeIndividualJarDigests_emptyCodebase_returnsEmptyArray()
            throws Exception {
        java.net.URL[] codebase = new java.net.URL[0];
        byte[][] result = PreferredProxyCodebaseProvider
                .computeIndividualJarDigests(codebase, "SHA-256");
        assertNotNull(result);
        assertEquals("empty codebase yields empty result", 0, result.length);
    }

    @Test
    public void computeIndividualJarDigests_directoryUrlSkipped()
            throws Exception {
        java.io.File tmp = java.io.File.createTempFile("testjar", ".jar");
        tmp.deleteOnExit();
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
            fos.write(new byte[]{9, 8, 7});
        }
        // Only a directory URL — should produce zero digests
        java.net.URL[] codebaseOnlyDir = new java.net.URL[]{
            new java.net.URL("file:///some/dir/")
        };
        byte[][] result = PreferredProxyCodebaseProvider
                .computeIndividualJarDigests(codebaseOnlyDir, "SHA-256");
        assertEquals("directory-only codebase yields empty result", 0, result.length);
    }

    @Test
    public void computeIndividualJarDigests_unknownAlgorithm_throwsIOException()
            throws Exception {
        java.io.File tmp = java.io.File.createTempFile("jar", ".jar");
        tmp.deleteOnExit();
        tmp.createNewFile();
        java.net.URL[] codebase = new java.net.URL[]{tmp.toURI().toURL()};
        try {
            PreferredProxyCodebaseProvider
                    .computeIndividualJarDigests(codebase, "NO-SUCH-ALGO");
            fail("Expected IOException for unknown algorithm");
        } catch (IOException expected) {
            // pass
        }
    }





    // -------------------------------------------------------------------------
    // validateDigestOffsets tests
    // -------------------------------------------------------------------------

    @Test
    public void validateDigestOffsets_validSingleEntry_noException() throws Exception {
        byte[] flat = new byte[32]; // 32-byte SHA-256 digest
        int[] offsets = new int[]{0};
        PreferredProxyCodebaseProvider.validateDigestOffsets(flat, offsets);
    }

    @Test
    public void validateDigestOffsets_validTwoEntries_noException() throws Exception {
        byte[] flat = new byte[64]; // two 32-byte SHA-256 digests
        int[] offsets = new int[]{0, 32};
        PreferredProxyCodebaseProvider.validateDigestOffsets(flat, offsets);
    }

    @Test
    public void validateDigestOffsets_negativeOffset_throwsIOException() {
        byte[] flat = new byte[32];
        int[] offsets = new int[]{-1};
        try {
            PreferredProxyCodebaseProvider.validateDigestOffsets(flat, offsets);
            fail("Expected IOException for negative offset");
        } catch (IOException expected) {
            assertTrue("Message should mention negative",
                    expected.getMessage().contains("negative"));
        }
    }

    @Test
    public void validateDigestOffsets_offsetOutOfBounds_throwsIOException() {
        byte[] flat = new byte[32];
        int[] offsets = new int[]{0, 64}; // 64 > flat.length=32
        try {
            PreferredProxyCodebaseProvider.validateDigestOffsets(flat, offsets);
            fail("Expected IOException for out-of-bounds offset");
        } catch (IOException expected) {
            assertTrue("Message should mention out of bounds",
                    expected.getMessage().contains("out of bounds"));
        }
    }

    @Test
    public void validateDigestOffsets_nonMonotonicOffsets_throwsIOException() {
        byte[] flat = new byte[64];
        int[] offsets = new int[]{32, 16}; // non-monotonic: 32 > 16
        try {
            PreferredProxyCodebaseProvider.validateDigestOffsets(flat, offsets);
            fail("Expected IOException for non-monotonic offsets");
        } catch (IOException expected) {
            assertTrue("Message should mention monoton",
                    expected.getMessage().contains("monoton"));
        }
    }

    @Test
    public void validateDigestOffsets_tooManyOffsets_throwsIOException() {
        int limit = PreferredProxyCodebaseProvider.DEFAULT_MAX_CODEBASE_JARS + 1;
        byte[] flat = new byte[limit * 32];
        int[] offsets = new int[limit];
        for (int i = 0; i < limit; i++) {
            offsets[i] = i * 32;
        }
        try {
            PreferredProxyCodebaseProvider.validateDigestOffsets(flat, offsets);
            fail("Expected IOException for too many offsets (> default max)");
        } catch (IOException expected) {
            assertTrue("Message should mention too many",
                    expected.getMessage().contains("too many") ||
                    expected.getMessage().contains("max"));
        }
    }

    @Test
    public void validateDigestOffsets_flatArrayTooLarge_throwsIOException() {
        // Construct flat array larger than maxCodebaseJars * 512
        int maxFlat = PreferredProxyCodebaseProvider.DEFAULT_MAX_CODEBASE_JARS * 512 + 1;
        byte[] flat = new byte[maxFlat];
        int[] offsets = new int[]{0};
        try {
            PreferredProxyCodebaseProvider.validateDigestOffsets(flat, offsets);
            fail("Expected IOException for flat array too large");
        } catch (IOException expected) {
            assertTrue("Message should mention too large",
                    expected.getMessage().contains("too large"));
        }
    }

    // -------------------------------------------------------------------------
    // computeIndividualJarDigests DoS-guard tests
    // -------------------------------------------------------------------------

    @Test
    public void parseMaxJarBytes_validValue_returnsParsed() {
        assertEquals(1_048_576L,
                PreferredProxyCodebaseProvider.parseMaxJarBytes("1048576"));
    }

    @Test
    public void parseMaxJarBytes_nullValue_returnsDefault() {
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_MAX_JAR_BYTES,
                PreferredProxyCodebaseProvider.parseMaxJarBytes(null));
    }

    @Test
    public void parseMaxJarBytes_invalidValue_returnsDefault() {
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_MAX_JAR_BYTES,
                PreferredProxyCodebaseProvider.parseMaxJarBytes("not-a-number"));
    }

    @Test
    public void parseMaxCodebaseJars_validValue_returnsParsed() {
        assertEquals(42, PreferredProxyCodebaseProvider.parseMaxCodebaseJars("42"));
    }

    @Test
    public void parseMaxCodebaseJars_nullValue_returnsDefault() {
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_MAX_CODEBASE_JARS,
                PreferredProxyCodebaseProvider.parseMaxCodebaseJars(null));
    }

    @Test
    public void parseJarReadTimeoutMs_validValue_returnsParsed() {
        assertEquals(5_000, PreferredProxyCodebaseProvider.parseJarReadTimeoutMs("5000"));
    }

    @Test
    public void parseJarReadTimeoutMs_nullValue_returnsDefault() {
        assertEquals(PreferredProxyCodebaseProvider.DEFAULT_JAR_READ_TIMEOUT_MS,
                PreferredProxyCodebaseProvider.parseJarReadTimeoutMs(null));
    }

    /**
     * Verifies that {@code computeIndividualJarDigests} refuses a codebase
     * with more entries than the configured maximum (JAR count DoS guard).
     * The URLs need not be reachable — the check fires before any I/O.
     */
    @Test
    public void computeIndividualJarDigests_exceedsMaxJarCount_throwsIOException()
            throws Exception {
        int limit = PreferredProxyCodebaseProvider.DEFAULT_MAX_CODEBASE_JARS + 1;
        java.net.URL[] codebase = new java.net.URL[limit];
        for (int i = 0; i < limit; i++) {
            // Use file:// to avoid any actual network activity; the check
            // fires before any URL is opened.
            codebase[i] = new java.net.URL("file:///fake-jar-" + i + ".jar");
        }
        try {
            PreferredProxyCodebaseProvider
                    .computeIndividualJarDigests(codebase, "SHA-256");
            fail("Expected IOException when codebase exceeds maxCodebaseJars");
        } catch (IOException expected) {
            assertTrue("Message should mention max or exceeds",
                    expected.getMessage().contains("max") ||
                    expected.getMessage().contains("exceeds"));
        }
    }

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
        public EventRegistration registerGlobalVerdictListener(
                RemoteEventListener listener, MarshalledInstance handback,
                long leaseDuration) throws RemoteException {
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

    // -------------------------------------------------------------------------
    // mergePrincipals tests
    // -------------------------------------------------------------------------

    private static java.security.Principal makePrincipal(final String name) {
        return new java.security.Principal() {
            public String getName() { return name; }
            public String toString() { return "TestPrincipal(" + name + ")"; }
            public boolean equals(Object o) {
                if (!(o instanceof java.security.Principal)) return false;
                return name.equals(((java.security.Principal) o).getName());
            }
            public int hashCode() { return name.hashCode(); }
        };
    }

    @Test
    public void mergePrincipals_bothNull_returnsNull() {
        assertNull("both null should return null",
                PreferredProxyCodebaseProvider.mergePrincipals(null, null));
    }

    @Test
    public void mergePrincipals_bothEmpty_returnsNull() {
        assertNull("both empty should return null",
                PreferredProxyCodebaseProvider.mergePrincipals(
                        new java.security.Principal[0],
                        new java.security.Principal[0]));
    }

    @Test
    public void mergePrincipals_firstNull_returnsSecond() {
        java.security.Principal b = makePrincipal("spiffe://trust/svc/b");
        java.security.Principal[] result =
                PreferredProxyCodebaseProvider.mergePrincipals(
                        null, new java.security.Principal[]{b});
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("spiffe://trust/svc/b", result[0].getName());
    }

    @Test
    public void mergePrincipals_secondNull_returnsFirst() {
        java.security.Principal a = makePrincipal("spiffe://trust/svc/a");
        java.security.Principal[] result =
                PreferredProxyCodebaseProvider.mergePrincipals(
                        new java.security.Principal[]{a}, null);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("spiffe://trust/svc/a", result[0].getName());
    }

    @Test
    public void mergePrincipals_disjointArrays_mergesAll() {
        java.security.Principal a = makePrincipal("spiffe://trust/client/node1");
        java.security.Principal b = makePrincipal("spiffe://trust/svc/serviceA");
        java.security.Principal[] result =
                PreferredProxyCodebaseProvider.mergePrincipals(
                        new java.security.Principal[]{a},
                        new java.security.Principal[]{b});
        assertNotNull(result);
        assertEquals("should have 2 principals", 2, result.length);
        // First principal must be from the first array
        assertEquals("spiffe://trust/client/node1", result[0].getName());
        assertEquals("spiffe://trust/svc/serviceA", result[1].getName());
    }

    @Test
    public void mergePrincipals_duplicateDropped() {
        java.security.Principal shared = makePrincipal("spiffe://trust/shared");
        java.security.Principal extra  = makePrincipal("spiffe://trust/extra");
        java.security.Principal[] result =
                PreferredProxyCodebaseProvider.mergePrincipals(
                        new java.security.Principal[]{shared, extra},
                        new java.security.Principal[]{shared});
        assertNotNull(result);
        assertEquals("duplicate should be removed", 2, result.length);
    }

    @Test
    public void mergePrincipals_serverAndLocalDiffer_produceDistinctGrants() {
        // Verify that two calls with different server principals produce
        // different merged sets — this is the core of Option 1.
        java.security.Principal local   = makePrincipal("spiffe://trust/client/node");
        java.security.Principal serverA = makePrincipal("spiffe://trust/svc/serviceA");
        java.security.Principal serverB = makePrincipal("spiffe://trust/svc/serviceB");

        java.security.Principal[] grantsA =
                PreferredProxyCodebaseProvider.mergePrincipals(
                        new java.security.Principal[]{local},
                        new java.security.Principal[]{serverA});
        java.security.Principal[] grantsB =
                PreferredProxyCodebaseProvider.mergePrincipals(
                        new java.security.Principal[]{local},
                        new java.security.Principal[]{serverB});

        assertFalse("different servers should produce different grant principal sets",
                java.util.Arrays.equals(grantsA, grantsB));
        assertEquals("serviceA grant should require 2 principals", 2, grantsA.length);
        assertEquals("serviceB grant should require 2 principals", 2, grantsB.length);
        // serverA principal present only in grantsA
        boolean aHasServerA = false;
        boolean bHasServerA = false;
        for (java.security.Principal p : grantsA)
            if (serverA.equals(p)) aHasServerA = true;
        for (java.security.Principal p : grantsB)
            if (serverA.equals(p)) bHasServerA = true;
        assertTrue("grantsA should contain serverA principal", aHasServerA);
        assertFalse("grantsB should NOT contain serverA principal", bHasServerA);
    }

    // -------------------------------------------------------------------------
    // INCONCLUSIVEPermit tests
    // -------------------------------------------------------------------------

    @Test
    public void inconclusivePermit_nameConstructorPreservesName() {
        INCONCLUSIVEPermit p = new INCONCLUSIVEPermit(FAKE_HASH);
        assertEquals("name should be the supplied hash", FAKE_HASH, p.getName());
    }

    @Test
    public void inconclusivePermit_impliesSameHash() {
        INCONCLUSIVEPermit a = new INCONCLUSIVEPermit(FAKE_HASH);
        INCONCLUSIVEPermit b = new INCONCLUSIVEPermit(FAKE_HASH);
        assertTrue("same-hash permissions should imply each other", a.implies(b));
    }

    @Test
    public void inconclusivePermit_wildcardImpliesAnyHash() {
        INCONCLUSIVEPermit wildcard = new INCONCLUSIVEPermit("*");
        INCONCLUSIVEPermit specific = new INCONCLUSIVEPermit(FAKE_HASH);
        assertTrue("wildcard should imply any specific hash", wildcard.implies(specific));
    }

    @Test
    public void inconclusivePermit_specificDoesNotImplyWildcard() {
        INCONCLUSIVEPermit specific = new INCONCLUSIVEPermit(FAKE_HASH);
        INCONCLUSIVEPermit wildcard = new INCONCLUSIVEPermit("*");
        assertFalse("specific hash should not imply wildcard", specific.implies(wildcard));
    }

    @Test
    public void inconclusivePermit_doesNotImplyDifferentType() {
        INCONCLUSIVEPermit p = new INCONCLUSIVEPermit(FAKE_HASH);
        assertFalse("INCONCLUSIVEPermit should not imply RuntimePermission",
                p.implies(new RuntimePermission("exitVM")));
    }

    @Test
    public void inconclusivePermit_twoArgConstructorPreservesName() {
        INCONCLUSIVEPermit p = new INCONCLUSIVEPermit(FAKE_HASH, null);
        assertEquals("two-arg constructor: name should be the supplied hash",
                FAKE_HASH, p.getName());
    }

    @Test
    public void inconclusivePermit_differentHashesDoNotImply() {
        String otherHash = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccdd";
        INCONCLUSIVEPermit a = new INCONCLUSIVEPermit(FAKE_HASH);
        INCONCLUSIVEPermit b = new INCONCLUSIVEPermit(otherHash);
        assertFalse("different hashes should not imply each other", a.implies(b));
    }

    // -------------------------------------------------------------------------
    // parseInconclusiveStrictMode tests
    // -------------------------------------------------------------------------

    @Test
    public void parseInconclusiveStrictMode_trueString_returnsTrue() {
        assertTrue(PreferredProxyCodebaseProvider.parseInconclusiveStrictMode("true"));
    }

    @Test
    public void parseInconclusiveStrictMode_trueUpperCase_returnsTrue() {
        assertTrue(PreferredProxyCodebaseProvider.parseInconclusiveStrictMode("TRUE"));
    }

    @Test
    public void parseInconclusiveStrictMode_trueMixedCase_returnsTrue() {
        assertTrue(PreferredProxyCodebaseProvider.parseInconclusiveStrictMode("True"));
    }

    @Test
    public void parseInconclusiveStrictMode_trueWithWhitespace_returnsTrue() {
        assertTrue(PreferredProxyCodebaseProvider.parseInconclusiveStrictMode("  true  "));
    }

    @Test
    public void parseInconclusiveStrictMode_null_returnsTrue() {
        assertTrue(PreferredProxyCodebaseProvider.parseInconclusiveStrictMode(null));
    }

    @Test
    public void parseInconclusiveStrictMode_empty_returnsTrue() {
        assertTrue(PreferredProxyCodebaseProvider.parseInconclusiveStrictMode(""));
    }

    @Test
    public void parseInconclusiveStrictMode_falseString_returnsFalse() {
        assertFalse(PreferredProxyCodebaseProvider.parseInconclusiveStrictMode("false"));
    }

    @Test
    public void parseInconclusiveStrictMode_otherString_returnsTrue() {
        assertTrue(PreferredProxyCodebaseProvider.parseInconclusiveStrictMode("yes"));
    }

    // -------------------------------------------------------------------------
    // checkVerdictForJar — strict mode tests
    // -------------------------------------------------------------------------

    /**
     * In strict mode (the default), when {@code INCONCLUSIVEPermit} is absent
     * from the effective policy, the INCONCLUSIVE load is refused with an
     * {@link IOException} whose cause is a {@link SecurityException}.
     *
     * <p>On DirtyChai, {@link java.security.AccessController#checkPermission}
     * enforces the policy directly via the ACC even when
     * {@link System#getSecurityManager()} returns {@code null}.
     */
    @Test
    public void checkVerdictForJar_inconclusive_strictMode_withoutPermission_throwsIOException()
            throws Exception {
        RegistryVerdict verdict = newVerdict(VerdictType.INCONCLUSIVE);
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(verdict);

        // Strict mode is on by default; no need to set it explicitly.
        assertTrue("inconclusiveStrictMode should default to true",
                PreferredProxyCodebaseProvider.inconclusiveStrictMode);

        // The test policy does not grant INCONCLUSIVEPermit, so strict mode
        // must refuse the load.
        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
            fail("Expected IOException: no INCONCLUSIVEPermit granted in test policy");
        } catch (IOException ex) {
            assertTrue("Exception should mention the hash",
                    ex.getMessage().contains(FAKE_HASH));
            assertTrue("Exception should mention 'strict'",
                    ex.getMessage().toLowerCase().contains("strict"));
            assertNotNull("Exception should have a non-null cause", ex.getCause());
            assertTrue("Cause should be SecurityException",
                    ex.getCause() instanceof SecurityException);
        }
    }

    /**
     * When strict mode is explicitly disabled, an INCONCLUSIVE verdict
     * proceeds regardless of any policy configuration.
     */
    @Test
    public void checkVerdictForJar_inconclusive_nonStrictMode_proceedsWithoutPermissionCheck()
            throws Exception {
        RegistryVerdict verdict = newVerdict(VerdictType.INCONCLUSIVE);
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(verdict);

        // Explicitly disable strict mode for this test.
        PreferredProxyCodebaseProvider.setInconclusiveStrictMode(false);

        assertTrue(PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH));
    }

    /**
     * Verifies that toggling inconclusiveStrictMode via the test helper is
     * visible to subsequent calls.
     */
    @Test
    public void setInconclusiveStrictMode_togglesField() {
        assertTrue("initial value should be true (strict mode is the default)",
                PreferredProxyCodebaseProvider.inconclusiveStrictMode);

        PreferredProxyCodebaseProvider.setInconclusiveStrictMode(false);
        assertFalse("after setInconclusiveStrictMode(false) field should be false",
                PreferredProxyCodebaseProvider.inconclusiveStrictMode);

        PreferredProxyCodebaseProvider.resetInconclusiveStrictMode();
        assertTrue("after reset field should be true again",
                PreferredProxyCodebaseProvider.inconclusiveStrictMode);
    }
}
