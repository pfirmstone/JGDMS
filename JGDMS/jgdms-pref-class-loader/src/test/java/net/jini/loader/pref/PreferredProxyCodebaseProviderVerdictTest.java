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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.io.MarshalledInstance;
import org.apache.river.api.net.Uri;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
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

    private static final String SIG_ALG = "SHA256withRSA";

    /**
     * The registry identity key pair used by the default {@code @Before}
     * configuration.  Verdicts produced by {@link #newVerdict} /
     * {@link #newVerdictForHash} are genuinely signed with
     * {@link #REGISTRY_KEYS} so that, under the secure-by-default (mandatory
     * signature) gate, SAFE/INCONCLUSIVE verdicts are accepted.
     */
    private static final KeyPair REGISTRY_KEYS = generateRsaKeyPair();

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Reset shared state between tests
    // -------------------------------------------------------------------------

    @Before
    public void disableVerdictRetrySleep() {
        PreferredProxyCodebaseProvider.setVerdictRetryBaseDelayMs(0L);
        // Secure by default: a known-good registry key is REQUIRED for any
        // verdict to be accepted.  Configure the shared test key so the
        // SAFE/INCONCLUSIVE/cache tests (whose verdicts are genuinely signed by
        // REGISTRY_KEYS) can proceed.  Fail-closed behaviour without a key is
        // covered by directPath_noKeyConfigured_failsClosed().
        PreferredProxyCodebaseProvider.setVerdictRegistryPublicKey(
                REGISTRY_KEYS.getPublic(), SIG_ALG);
    }

    @After
    public void resetRegistry() {
        // Clear the VerdictRegistry so tests do not interfere with each other.
        VerdictRegistryHolder.set(null);
        PreferredProxyCodebaseProvider.resetVerdictRetryBaseDelayMs();
        PreferredProxyCodebaseProvider.clearVerdictCache();
        PreferredProxyCodebaseProvider.resetInconclusiveStrictMode();
        // Clear the configured key so state does not leak between tests, and
        // clear the cache again so a signed verdict from one test cannot leak
        // into another under the same content hash.
        PreferredProxyCodebaseProvider.setVerdictRegistryPublicKey(null, null);
        PreferredProxyCodebaseProvider.clearVerdictCache();
    }

    /** Signs the canonical bytes for {@code (urls, type, ts)} with {@code signer}. */
    private static byte[] signVerdict(Uri[] urls, VerdictType type, long ts,
                                      PrivateKey signer) throws Exception {
        Signature sig = Signature.getInstance(SIG_ALG);
        sig.initSign(signer);
        sig.update(canonicalBytes(urls, type, ts));
        return sig.sign();
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
        // A constrainable stub returns a distinct constrained copy from
        // setConstraints(); setVerdictRegistry stores THAT constrained copy.
        ConstrainableStubVerdictRegistry stub = new ConstrainableStubVerdictRegistry();
        PreferredProxyCodebaseProvider.setVerdictRegistry(stub);
        VerdictRegistry stored = VerdictRegistryHolder.get();
        assertSame("holder should hold the constrained copy",
                stub.constrainedCopy, stored);
        assertNotNull("constraints must have been applied at the boundary",
                stub.constrainedCopy.appliedConstraints);
    }

    // -------------------------------------------------------------------------
    // STD-006 §7.4 — channel constraints (Integrity + ServerAuthentication)
    // -------------------------------------------------------------------------

    /**
     * The injection boundary must apply Integrity.YES + ServerAuthentication.YES
     * to the registry proxy (belt half of belt-and-braces).
     */
    @Test
    public void constrainRegistryProxy_appliesIntegrityAndServerAuth() {
        ConstrainableStubVerdictRegistry stub = new ConstrainableStubVerdictRegistry();
        VerdictRegistry constrained =
                PreferredProxyCodebaseProvider.constrainRegistryProxy(stub);

        assertSame("returns the constrained copy", stub.constrainedCopy, constrained);
        MethodConstraints mc = stub.constrainedCopy.appliedConstraints;
        assertNotNull("constraints applied", mc);
        java.lang.reflect.Method m =
                VerdictRegistry.class.getMethod("getVerdictByHash", String.class);
        java.util.Set<net.jini.core.constraint.InvocationConstraint> reqs =
                mc.getConstraints(m).requirements();
        assertTrue("Integrity.YES required on getVerdictByHash",
                reqs.contains(net.jini.core.constraint.Integrity.YES));
        assertTrue("ServerAuthentication.YES required on getVerdictByHash",
                reqs.contains(net.jini.core.constraint.ServerAuthentication.YES));
    }

    /**
     * Secure by default: a proxy that cannot carry constraints (not a
     * RemoteMethodControl) is REJECTED rather than trusted.
     */
    @Test
    public void constrainRegistryProxy_unconstrainableProxy_rejected() {
        StubVerdictRegistry plain = new StubVerdictRegistry(); // not RemoteMethodControl
        try {
            PreferredProxyCodebaseProvider.constrainRegistryProxy(plain);
            fail("Expected IllegalArgumentException for unconstrainable proxy");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("RemoteMethodControl"));
        }
    }

    @Test
    public void constrainRegistryProxy_null_passesThrough() {
        assertNull("null proxy passes through unchanged",
                PreferredProxyCodebaseProvider.constrainRegistryProxy(null));
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
    // STD-006 §7.4 — direct-path MANDATORY inline signature verification
    //                (secure by default / fail-closed)
    // -------------------------------------------------------------------------

    /** Reproduces the registry's authoritative canonical signing bytes. */
    private static byte[] canonicalBytes(Uri[] urls, VerdictType type, long ts)
            throws IOException {
        Uri[] sorted = urls.clone();
        Arrays.sort(sorted, new Comparator<Uri>() {
            public int compare(Uri a, Uri b) {
                return a.toString().compareTo(b.toString());
            }
        });
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(baos);
        for (Uri uri : sorted) {
            byte[] b = uri.toString().getBytes(StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(type.ordinal());
        dos.writeLong(ts);
        dos.flush();
        return baos.toByteArray();
    }

    private static RegistryVerdict signedHashVerdict(VerdictType type,
                                                     PrivateKey signer)
            throws Exception {
        Uri[] urls = new Uri[]{ new Uri("urn:sha256:" + FAKE_HASH) };
        long ts = System.currentTimeMillis();
        return new RegistryVerdict(urls, type, ts,
                signVerdict(urls, type, ts, signer));
    }

    /**
     * With the (default) configured key, a SAFE verdict bearing a genuine
     * registry signature is accepted on the direct path.
     */
    @Test
    public void directPath_genuineSignature_safeProceeds() throws Exception {
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(
                signedHashVerdict(VerdictType.SAFE, REGISTRY_KEYS.getPrivate()));

        assertFalse("genuinely signed SAFE verdict must proceed",
                PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH));
    }

    /**
     * Secure by default: with NO registry key configured, NO verdict can be
     * trusted — even a genuine SAFE verdict is refused (fail-closed).  This is
     * the behaviour change from the previous opt-in design.
     */
    @Test
    public void directPath_noKeyConfigured_failsClosed() throws Exception {
        // Clear the key configured by @Before.
        PreferredProxyCodebaseProvider.setVerdictRegistryPublicKey(null, null);
        assertNull("precondition: no verdict key configured",
                PreferredProxyCodebaseProvider.getVerdictRegistryPublicKey());

        StubVerdictRegistry stub = new StubVerdictRegistry();
        // Even a genuinely-signed SAFE verdict must be refused with no key.
        stub.setVerdictToReturn(
                signedHashVerdict(VerdictType.SAFE, REGISTRY_KEYS.getPrivate()));

        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
            fail("Expected IOException: no configured key must fail closed");
        } catch (IOException ex) {
            assertTrue("message should mention the missing key / fail-closed",
                    ex.getMessage().toLowerCase().contains("public key"));
            assertTrue("message should contain the hash",
                    ex.getMessage().contains(FAKE_HASH));
        }
    }

    /**
     * The core of the fix: a verdict whose signature does NOT verify against
     * the configured key is refused (fail-closed) even when its VerdictType is
     * SAFE — a substituted/forged verdict cannot green-light a codebase.
     */
    @Test
    public void directPath_forgedSignature_safeRefused() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair attacker = kpg.generateKeyPair();

        // Client trusts REGISTRY_KEYS (from @Before); verdict is signed by 'attacker'.
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(signedHashVerdict(VerdictType.SAFE, attacker.getPrivate()));

        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
            fail("Expected IOException: forged SAFE verdict must be refused");
        } catch (IOException ex) {
            assertTrue("message should mention signature",
                    ex.getMessage().toLowerCase().contains("signature"));
            assertTrue("message should contain the hash",
                    ex.getMessage().contains(FAKE_HASH));
        }
    }

    /**
     * A DUMMY-signature verdict is refused under the configured key — proving
     * the gate is live on the SAFE path regardless of VerdictType.
     */
    @Test
    public void directPath_unverifiableDummySignature_refused() throws Exception {
        Uri[] urls = new Uri[]{ new Uri("urn:sha256:" + FAKE_HASH) };
        RegistryVerdict dummy = new RegistryVerdict(
                urls, VerdictType.SAFE, System.currentTimeMillis(), DUMMY_SIG);

        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(dummy);

        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
            fail("Expected IOException: unverifiable signature must be refused");
        } catch (IOException ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("signature"));
        }
    }

    /**
     * A genuine DANGEROUS verdict passes the signature gate and is then refused
     * as DANGEROUS — confirming verification runs before type dispatch and does
     * not mask the DANGEROUS refusal.
     */
    @Test
    public void directPath_genuineDangerous_refusedAsDangerous() throws Exception {
        StubVerdictRegistry stub = new StubVerdictRegistry();
        stub.setVerdictToReturn(
                signedHashVerdict(VerdictType.DANGEROUS, REGISTRY_KEYS.getPrivate()));

        try {
            PreferredProxyCodebaseProvider.checkVerdictForJar(stub, FAKE_HASH, PATH);
            fail("Expected IOException for DANGEROUS verdict");
        } catch (IOException ex) {
            assertTrue("genuine DANGEROUS verdict refused as DANGEROUS",
                    ex.getMessage().contains("DANGEROUS"));
        }
    }

    @Test
    public void setVerdictRegistryPublicKey_nullSigAlg_throwsIae() {
        try {
            PreferredProxyCodebaseProvider.setVerdictRegistryPublicKey(
                    REGISTRY_KEYS.getPublic(), null);
            fail("Expected IllegalArgumentException for null sigAlgorithm with a key");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    @Test
    public void decodePublicKey_roundTrips() throws Exception {
        PublicKey original = REGISTRY_KEYS.getPublic();
        byte[] der = original.getEncoded(); // X.509 SubjectPublicKeyInfo
        PublicKey decoded = PreferredProxyCodebaseProvider.decodePublicKey(der, "RSA");
        assertArrayEquals("decoded key must round-trip to identical encoding",
                original.getEncoded(), decoded.getEncoded());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Constructs a minimal {@link RegistryVerdict} with the given type, GENUINELY
     * SIGNED by {@link #REGISTRY_KEYS} so it passes the mandatory signature gate
     * when the shared key is configured (the default in {@code @Before}).
     */
    private static RegistryVerdict newVerdict(VerdictType type)
            throws URISyntaxException {
        try {
            Uri[] urls = new Uri[]{new Uri("urn:sha256:" + FAKE_HASH)};
            long ts = System.currentTimeMillis();
            byte[] sig = signVerdict(urls, type, ts, REGISTRY_KEYS.getPrivate());
            return new RegistryVerdict(urls, type, ts, sig);
        } catch (URISyntaxException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
    private static class StubVerdictRegistry implements VerdictRegistry {

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

    // -------------------------------------------------------------------------
    // computeJarHash — JGDMS-STD-002 v1.3: client hashes RAW downloaded bytes
    // -------------------------------------------------------------------------

    /**
     * Writes the given bytes to a temp file and returns its URL.
     */
    private static java.net.URL writeTempJar(byte[] rawJarBytes) throws Exception {
        java.io.File tmp = java.io.File.createTempFile("scaptest", ".jar");
        tmp.deleteOnExit();
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
            fos.write(rawJarBytes);
        }
        return tmp.toURI().toURL();
    }

    /** Builds raw bytes of a minimal valid JAR containing a single entry. */
    private static byte[] buildMinimalJarBytes() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.jar.JarOutputStream jos =
                new java.util.jar.JarOutputStream(baos)) {
            java.util.zip.ZipEntry e = new java.util.zip.ZipEntry("a/b/Hello.txt");
            jos.putNextEntry(e);
            jos.write("hello scap".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** Plain lowercase-hex SHA-256 of the given bytes. */
    private static String rawHash(byte[] bytes) throws Exception {
        java.security.MessageDigest md =
                java.security.MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(bytes);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * JGDMS-STD-002 v1.3: the client computes {@code SHA-256(rawDownloadedBytes)}
     * directly — no normalisation step.  Producers run
     * {@code pack200-normalize-maven-plugin} at build time so the published
     * artifact IS the canonical form C and rawBytesHash == contentHash that
     * Host 4 recorded.
     */
    @Test
    public void computeJarHash_returnsRawBytesHash() throws Exception {
        byte[] rawJar = buildMinimalJarBytes();
        java.net.URL jarUrl = writeTempJar(rawJar);

        String actual = PreferredProxyCodebaseProvider.computeJarHash(jarUrl);

        assertEquals("client hash must equal SHA-256(rawDownloadedBytes) per v1.3",
                rawHash(rawJar), actual);
    }

    /**
     * A non-JAR artifact (garbage bytes) is still hashed — the client no longer
     * parses the JAR and therefore cannot tell that the artifact is malformed.
     * The registry lookup will simply miss and the fail-closed gate in
     * {@link PreferredProxyCodebaseProvider#checkVerdictForJar} will refuse the
     * load.  This test asserts that {@code computeJarHash} returns
     * {@code SHA-256(bytes)} and does not throw.
     */
    @Test
    public void computeJarHash_arbitraryBytes_returnsRawBytesHash() throws Exception {
        byte[] garbage = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        java.net.URL jarUrl = writeTempJar(garbage);
        String h = PreferredProxyCodebaseProvider.computeJarHash(jarUrl);
        assertEquals("hash must be over the raw downloaded bytes",
                rawHash(garbage), h);
    }

    /**
     * End-to-end of the verdict lookup hashing path: a request for a known JAR
     * queries the {@link VerdictRegistry} with {@code SHA-256(rawBytes)} —
     * the value a build-time-normalised producer would have published as the
     * stamp's declared hash (and the value Host 4 has recorded).
     */
    @Test
    public void resolveVerdictPath_queriesRegistryWithRawBytesHash()
            throws Exception {
        byte[] rawJar = buildMinimalJarBytes();
        java.net.URL jarUrl = writeTempJar(rawJar);
        String expectedHash = rawHash(rawJar);

        HashCapturingStubRegistry stub = new HashCapturingStubRegistry();
        stub.setVerdictToReturn(newVerdictForHash(VerdictType.SAFE, expectedHash));

        String computed = PreferredProxyCodebaseProvider.computeJarHash(jarUrl);
        PreferredProxyCodebaseProvider.checkVerdictForJar(stub, computed, PATH);

        assertEquals("registry must be queried with SHA-256(rawDownloadedBytes)",
                expectedHash, stub.getLastQueriedHash());
    }

    // -------------------------------------------------------------------------
    // #7 verdict matrix — INCONCLUSIVE with INCONCLUSIVEPermit -> load
    // -------------------------------------------------------------------------

    /**
     * Verifies the gate's permission shape: a {@link java.security.ProtectionDomain}
     * granting {@link INCONCLUSIVEPermit} for the specific hash implies it (and
     * the wildcard form implies the specific hash).  This is the contract the
     * strict-mode gate exercises via
     * {@link java.security.AccessController#checkPermission(java.security.Permission)};
     * coverage of the AccessController stack-walk itself belongs in an
     * integration test with a configured policy file.  Coverage of the
     * fail-closed path lives in
     * {@link #checkVerdictForJar_inconclusive_strictMode_withoutPermission_throwsIOException};
     * coverage of the non-strict bypass in
     * {@link #checkVerdictForJar_inconclusive_nonStrictMode_proceedsWithoutPermissionCheck}.
     */
    @Test
    public void inconclusivePermit_grantedDomain_impliesSpecificHash() {
        java.security.Permissions perms = new java.security.Permissions();
        perms.add(new INCONCLUSIVEPermit(FAKE_HASH));
        java.security.ProtectionDomain pd = new java.security.ProtectionDomain(
                new java.security.CodeSource(null,
                        (java.security.cert.Certificate[]) null),
                perms);
        assertTrue("PD granting the specific INCONCLUSIVEPermit should imply it",
                pd.implies(new INCONCLUSIVEPermit(FAKE_HASH)));

        java.security.Permissions wildcardPerms = new java.security.Permissions();
        wildcardPerms.add(new INCONCLUSIVEPermit("*"));
        java.security.ProtectionDomain wildcardPd = new java.security.ProtectionDomain(
                new java.security.CodeSource(null,
                        (java.security.cert.Certificate[]) null),
                wildcardPerms);
        assertTrue("PD granting INCONCLUSIVEPermit \"*\" should imply specific hash",
                wildcardPd.implies(new INCONCLUSIVEPermit(FAKE_HASH)));
    }

    /**
     * Builds a {@link RegistryVerdict} for a specific content hash, GENUINELY
     * SIGNED by {@link #REGISTRY_KEYS} so it passes the mandatory signature gate.
     */
    private static RegistryVerdict newVerdictForHash(VerdictType type, String hash)
            throws URISyntaxException {
        try {
            Uri[] urls = new Uri[]{new Uri("urn:sha256:" + hash)};
            long ts = System.currentTimeMillis();
            byte[] sig = signVerdict(urls, type, ts, REGISTRY_KEYS.getPrivate());
            return new RegistryVerdict(urls, type, ts, sig);
        } catch (URISyntaxException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A {@link StubVerdictRegistry} variant that records the content hash passed
     * to {@link #getVerdictByHash(String)} so tests can assert which hash the
     * client used for the lookup.
     */
    private static final class HashCapturingStubRegistry extends StubVerdictRegistry {
        private volatile String lastQueriedHash;

        String getLastQueriedHash() {
            return lastQueriedHash;
        }

        @Override
        public RegistryVerdict getVerdictByHash(String contentHash)
                throws RemoteException {
            this.lastQueriedHash = contentHash;
            return super.getVerdictByHash(contentHash);
        }
    }

    /**
     * A {@link StubVerdictRegistry} that is also a {@link RemoteMethodControl},
     * so {@code constrainRegistryProxy} can attach method constraints.
     * {@link #setConstraints} returns a distinct copy (as a real constrainable
     * proxy would) recording the constraints that were applied.
     */
    private static class ConstrainableStubVerdictRegistry
            extends StubVerdictRegistry implements RemoteMethodControl {

        /** The copy returned by {@link #setConstraints}. */
        final ConstrainableStubVerdictRegistry constrainedCopy;
        /** The constraints applied to this instance (null until set). */
        volatile MethodConstraints appliedConstraints;

        ConstrainableStubVerdictRegistry() {
            this.constrainedCopy = new ConstrainableStubVerdictRegistry(true);
        }

        private ConstrainableStubVerdictRegistry(boolean isCopy) {
            this.constrainedCopy = this; // copy of a copy is itself
        }

        @Override
        public RemoteMethodControl setConstraints(MethodConstraints constraints) {
            constrainedCopy.appliedConstraints = constraints;
            return constrainedCopy;
        }

        @Override
        public MethodConstraints getConstraints() {
            return appliedConstraints;
        }
    }
}
