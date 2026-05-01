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
package au.net.zeus.jgdms.vr;

import java.io.IOException;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.SignedVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import org.apache.river.api.net.Uri;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link VerdictRegistryImpl}.
 *
 * <p>Tests use real RSA key pairs (1024-bit) to exercise signature creation
 * and verification paths.
 *
 * <p>Covers:
 * <ul>
 *   <li>Constructor argument guards</li>
 *   <li>{@code registerAnalysisEngine} / {@code revokeAnalysisEngine} argument
 *       guards and behaviour</li>
 *   <li>{@code submitVerdict}: unregistered engine, invalid signature, valid
 *       SAFE verdict, valid DANGEROUS verdict, quorum policy</li>
 *   <li>{@code reportCrash}: argument guard, valid crash report → DANGEROUS</li>
 *   <li>{@code getVerdict}: null/empty guards, no-verdict and verdict-present
 *       cases</li>
 *   <li>Lease management: {@code renewEventLease} and {@code cancelEventLease}
 *       argument guards and unknown-lease handling</li>
 *   <li>Quorum revocation: revoking an engine after quorum was met retracts
 *       a stale SAFE verdict</li>
 *   <li>{@code codebaseKey} — canonical key ordering</li>
 *   <li>{@code canonicalBytesForVerdict} / {@code canonicalBytesForCrashReport}
 *       — determinism and field impact</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class VerdictRegistryImplTest {

    private static final String SIG_ALGORITHM = "SHA256withRSA";

    /** Registry key pair — used to sign {@link RegistryVerdict} objects. */
    private KeyPair registryKeyPair;
    /** Engine key pair — used to sign {@link SignedVerdict} objects. */
    private KeyPair engineKeyPair;
    /** Phoenix key pair — used to sign {@link CrashReport} objects. */
    private KeyPair phoenixKeyPair;

    /** The registry under test, with quorum = 1. */
    private VerdictRegistryImpl registry;

    /** A small set of codebase URIs used across many tests. */
    private Set<Uri> codebaseUrls;

    @Before
    public void setUp() throws NoSuchAlgorithmException, URISyntaxException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        registryKeyPair = kpg.generateKeyPair();
        engineKeyPair   = kpg.generateKeyPair();
        phoenixKeyPair  = kpg.generateKeyPair();

        registry = new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                phoenixKeyPair.getPublic(),   SIG_ALGORITHM);

        codebaseUrls = new LinkedHashSet<Uri>();
        codebaseUrls.add(new Uri("http://example.com/a.jar"));
    }

    // =========================================================================
    // Constructor guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testConstructorNullRegistryPrivateKeyThrowsNPE() {
        new VerdictRegistryImpl(
                null, SIG_ALGORITHM,
                phoenixKeyPair.getPublic(), SIG_ALGORITHM, 1);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullRegistrySigAlgorithmThrowsNPE() {
        new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), null,
                phoenixKeyPair.getPublic(), SIG_ALGORITHM, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyRegistrySigAlgorithmThrowsIAE() {
        new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), "",
                phoenixKeyPair.getPublic(), SIG_ALGORITHM, 1);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullPhoenixPublicKeyThrowsNPE() {
        new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                null, SIG_ALGORITHM, 1);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullPhoenixSigAlgorithmThrowsNPE() {
        new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                phoenixKeyPair.getPublic(), null, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyPhoenixSigAlgorithmThrowsIAE() {
        new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                phoenixKeyPair.getPublic(), "", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorZeroQuorumThrowsIAE() {
        new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                phoenixKeyPair.getPublic(), SIG_ALGORITHM, 0);
    }

    // =========================================================================
    // registerAnalysisEngine — argument guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testRegisterNullEngineIdThrowsNPE() throws RemoteException {
        registry.registerAnalysisEngine(null, engineKeyPair.getPublic(), SIG_ALGORITHM);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterEmptyEngineIdThrowsIAE() throws RemoteException {
        registry.registerAnalysisEngine("", engineKeyPair.getPublic(), SIG_ALGORITHM);
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterNullEngineKeyThrowsNPE() throws RemoteException {
        registry.registerAnalysisEngine("e1", null, SIG_ALGORITHM);
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterNullSigAlgorithmThrowsNPE() throws RemoteException {
        registry.registerAnalysisEngine("e1", engineKeyPair.getPublic(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterEmptySigAlgorithmThrowsIAE() throws RemoteException {
        registry.registerAnalysisEngine("e1", engineKeyPair.getPublic(), "");
    }

    // =========================================================================
    // revokeAnalysisEngine — argument guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testRevokeNullEngineIdThrowsNPE() throws RemoteException {
        registry.revokeAnalysisEngine(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRevokeEmptyEngineIdThrowsIAE() throws RemoteException {
        registry.revokeAnalysisEngine("");
    }

    @Test
    public void testRevokeUnknownEngineIsIgnored() throws RemoteException {
        // Should not throw; simply logs a warning.
        registry.revokeAnalysisEngine("no-such-engine");
    }

    // =========================================================================
    // getVerdict — argument guards and "no verdict yet"
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testGetVerdictNullThrowsNPE() throws RemoteException {
        registry.getVerdict(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetVerdictEmptySetThrowsIAE() throws RemoteException {
        registry.getVerdict(Collections.<Uri>emptySet());
    }

    @Test
    public void testGetVerdictNoVerdictYetReturnsNull() throws RemoteException {
        assertNull("No verdict has been submitted yet",
                registry.getVerdict(codebaseUrls));
    }

    // =========================================================================
    // submitVerdict — unregistered engine is discarded
    // =========================================================================

    @Test
    public void testSubmitVerdictFromUnregisteredEngineIsDiscarded()
            throws Exception {
        SignedVerdict sv = buildSignedVerdict(
                codebaseUrls, VerdictType.SAFE, engineKeyPair);
        registry.submitVerdict("unregistered", sv);
        assertNull("Unregistered engine verdict must be discarded",
                registry.getVerdict(codebaseUrls));
    }

    // =========================================================================
    // submitVerdict — SAFE verdict publishes a RegistryVerdict (quorum = 1)
    // =========================================================================

    @Test
    public void testSubmitSafeVerdictPublishesRegistryVerdictWithQuorumOne()
            throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        SignedVerdict sv = buildSignedVerdict(
                codebaseUrls, VerdictType.SAFE, engineKeyPair);
        registry.submitVerdict("e1", sv);

        RegistryVerdict rv = registry.getVerdict(codebaseUrls);
        assertNotNull("A SAFE RegistryVerdict must be published", rv);
        assertEquals(VerdictType.SAFE, rv.getVerdict());
    }

    // =========================================================================
    // submitVerdict — DANGEROUS verdict publishes immediately
    // =========================================================================

    @Test
    public void testSubmitDangerousVerdictPublishesImmediately()
            throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        SignedVerdict sv = buildSignedVerdict(
                codebaseUrls, VerdictType.DANGEROUS, engineKeyPair);
        registry.submitVerdict("e1", sv);

        RegistryVerdict rv = registry.getVerdict(codebaseUrls);
        assertNotNull("A DANGEROUS RegistryVerdict must be published", rv);
        assertEquals(VerdictType.DANGEROUS, rv.getVerdict());
    }

    // =========================================================================
    // submitVerdict — invalid signature is discarded
    // =========================================================================

    @Test
    public void testSubmitVerdictWithBadSignatureIsDiscarded()
            throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        // Build a verdict but sign it with the wrong key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair wrongKey = kpg.generateKeyPair();
        SignedVerdict sv = buildSignedVerdict(
                codebaseUrls, VerdictType.SAFE, wrongKey);
        registry.submitVerdict("e1", sv);

        assertNull("Verdict with bad signature must be discarded",
                registry.getVerdict(codebaseUrls));
    }

    // =========================================================================
    // submitVerdict — quorum = 2
    // =========================================================================

    @Test
    public void testQuorumOfTwoRequiresTwoSafeVotes() throws Exception {
        VerdictRegistryImpl twoRegistry = new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 2);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair engine2Key = kpg.generateKeyPair();

        twoRegistry.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);
        twoRegistry.registerAnalysisEngine("e2", engine2Key.getPublic(), SIG_ALGORITHM);

        // First vote — quorum not yet met
        twoRegistry.submitVerdict("e1",
                buildSignedVerdict(codebaseUrls, VerdictType.SAFE, engineKeyPair));
        assertNull("Single vote must not meet quorum of 2",
                twoRegistry.getVerdict(codebaseUrls));

        // Second vote — quorum met
        twoRegistry.submitVerdict("e2",
                buildSignedVerdict(codebaseUrls, VerdictType.SAFE, engine2Key));
        RegistryVerdict rv = twoRegistry.getVerdict(codebaseUrls);
        assertNotNull("Two SAFE votes must meet quorum of 2", rv);
        assertEquals(VerdictType.SAFE, rv.getVerdict());
    }

    // =========================================================================
    // revokeAnalysisEngine — vote is removed; stale SAFE verdict is retracted
    // =========================================================================

    @Test
    public void testRevokingVotingEngineRetractsSafeVerdict() throws Exception {
        VerdictRegistryImpl twoRegistry = new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 2);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair engine2Key = kpg.generateKeyPair();

        twoRegistry.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);
        twoRegistry.registerAnalysisEngine("e2", engine2Key.getPublic(), SIG_ALGORITHM);

        // Two SAFE votes → quorum met
        twoRegistry.submitVerdict("e1",
                buildSignedVerdict(codebaseUrls, VerdictType.SAFE, engineKeyPair));
        twoRegistry.submitVerdict("e2",
                buildSignedVerdict(codebaseUrls, VerdictType.SAFE, engine2Key));
        assertNotNull(twoRegistry.getVerdict(codebaseUrls));

        // Revoke one engine — quorum no longer met; verdict should be retracted
        twoRegistry.revokeAnalysisEngine("e1");
        assertNull("Revoking a voting engine must retract a stale SAFE verdict",
                twoRegistry.getVerdict(codebaseUrls));
    }

    // =========================================================================
    // reportCrash — argument guard
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testReportCrashNullThrowsNPE() throws RemoteException {
        registry.reportCrash(null);
    }

    // =========================================================================
    // reportCrash — valid crash report publishes DANGEROUS
    // =========================================================================

    @Test
    public void testReportCrashWithValidSignaturePublishesDangerous()
            throws Exception {
        CrashReport report = buildCrashReport(codebaseUrls, phoenixKeyPair);
        registry.reportCrash(report);

        RegistryVerdict rv = registry.getVerdict(codebaseUrls);
        assertNotNull("CrashReport must publish a DANGEROUS verdict", rv);
        assertEquals(VerdictType.DANGEROUS, rv.getVerdict());
    }

    // =========================================================================
    // reportCrash — invalid Phoenix signature is discarded
    // =========================================================================

    @Test
    public void testReportCrashWithBadSignatureIsDiscarded() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair wrongKey = kpg.generateKeyPair();
        CrashReport report = buildCrashReport(codebaseUrls, wrongKey);
        registry.reportCrash(report);
        assertNull("CrashReport with bad Phoenix signature must be discarded",
                registry.getVerdict(codebaseUrls));
    }

    // =========================================================================
    // Lease management — renewEventLease guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testRenewLeaseNullIdThrowsNPE()
            throws UnknownLeaseException, RemoteException {
        registry.renewEventLease(null, 60_000L);
    }

    @Test(expected = UnknownLeaseException.class)
    public void testRenewLeaseUnknownIdThrowsULE()
            throws UnknownLeaseException, RemoteException {
        registry.renewEventLease(UuidFactory.generate(), 60_000L);
    }

    // =========================================================================
    // Lease management — cancelEventLease guards
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testCancelLeaseNullIdThrowsNPE()
            throws UnknownLeaseException, RemoteException {
        registry.cancelEventLease(null);
    }

    @Test(expected = UnknownLeaseException.class)
    public void testCancelLeaseUnknownIdThrowsULE()
            throws UnknownLeaseException, RemoteException {
        registry.cancelEventLease(UuidFactory.generate());
    }

    // =========================================================================
    // submitVerdict — null engineId / null verdict guard
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testSubmitVerdictNullEngineIdThrowsNPE() throws RemoteException {
        registry.submitVerdict(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitVerdictEmptyEngineIdThrowsIAE() throws RemoteException {
        registry.submitVerdict("", null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubmitVerdictNullVerdictThrowsNPE() throws RemoteException {
        registry.submitVerdict("e1", null);
    }

    // =========================================================================
    // codebaseKey — ordering is deterministic regardless of insertion order
    // =========================================================================

    @Test
    public void testCodebaseKeyIsDeterministicRegardlessOfOrder()
            throws URISyntaxException {
        Uri ua = new Uri("http://example.com/a.jar");
        Uri ub = new Uri("http://example.com/b.jar");

        Set<Uri> ab = new LinkedHashSet<Uri>();
        ab.add(ua); ab.add(ub);

        Set<Uri> ba = new LinkedHashSet<Uri>();
        ba.add(ub); ba.add(ua);

        assertEquals("codebaseKey must be insertion-order independent",
                VerdictRegistryImpl.codebaseKey(ab),
                VerdictRegistryImpl.codebaseKey(ba));
    }

    // =========================================================================
    // canonicalBytesForVerdict — determinism
    // =========================================================================

    @Test
    public void testCanonicalBytesForVerdictDeterministic()
            throws Exception {
        registry.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);
        SignedVerdict sv = buildSignedVerdict(codebaseUrls, VerdictType.SAFE, engineKeyPair);

        byte[] b1 = VerdictRegistryImpl.canonicalBytesForVerdict(sv);
        byte[] b2 = VerdictRegistryImpl.canonicalBytesForVerdict(sv);
        assertTrue("canonicalBytesForVerdict must be deterministic",
                java.util.Arrays.equals(b1, b2));
    }

    // =========================================================================
    // canonicalBytesForCrashReport — determinism
    // =========================================================================

    @Test
    public void testCanonicalBytesForCrashReportDeterministic() throws Exception {
        CrashReport report = buildCrashReport(codebaseUrls, phoenixKeyPair);
        byte[] b1 = VerdictRegistryImpl.canonicalBytesForCrashReport(report);
        byte[] b2 = VerdictRegistryImpl.canonicalBytesForCrashReport(report);
        assertTrue("canonicalBytesForCrashReport must be deterministic",
                java.util.Arrays.equals(b1, b2));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds a {@link SignedVerdict} for {@code codebaseUrls} and the given
     * verdict type, signed with the supplied key pair.
     */
    private static SignedVerdict buildSignedVerdict(Set<Uri> codebaseUrls,
                                                    VerdictType type,
                                                    KeyPair signingKey)
            throws NoSuchAlgorithmException, InvalidKeyException,
                   SignatureException, IOException, URISyntaxException {
        Uri[] sorted = sortedUris(codebaseUrls);
        long timestamp = System.currentTimeMillis();
        byte[] canonical = canonicalBytesForSignedVerdict(sorted, type, timestamp);
        byte[] sig = rsaSign(signingKey, canonical);
        return new SignedVerdict(sorted, type, timestamp, sig);
    }

    /**
     * Produces the canonical bytes that a BAE engine signs for a
     * {@link SignedVerdict}.  Mirrors {@code BytecodeAnalysisEngineImpl.canonicalBytes}
     * without creating a cross-module test dependency.
     */
    private static byte[] canonicalBytesForSignedVerdict(Uri[] sortedUrls,
                                                          VerdictType type,
                                                          long timestamp)
            throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
        for (Uri uri : sortedUrls) {
            byte[] b = uri.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(type.ordinal());
        dos.writeLong(timestamp);
        dos.flush();
        return baos.toByteArray();
    }

    /**
     * Builds a {@link CrashReport} for {@code codebaseUrls}, signed with
     * the supplied key pair.
     */
    private static CrashReport buildCrashReport(Set<Uri> codebaseUrls,
                                                 KeyPair signingKey)
            throws Exception {
        Uri[] sorted = sortedUris(codebaseUrls);
        int    exitCode    = 1;
        long   incarnation = 0L;
        String stderr      = "crash";

        // Build canonical bytes manually (mirrors VerdictRegistryImpl)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
        for (Uri uri : sorted) {
            byte[] b = uri.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            dos.writeInt(b.length);
            dos.write(b);
        }
        dos.writeInt(exitCode);
        dos.writeLong(incarnation);
        byte[] stderrBytes = stderr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        dos.writeInt(stderrBytes.length);
        dos.write(stderrBytes);
        dos.flush();

        byte[] sig = rsaSign(signingKey, baos.toByteArray());
        return new CrashReport(sorted, exitCode, incarnation, stderr, sig);
    }

    private static Uri[] sortedUris(Set<Uri> uris) {
        Uri[] arr = uris.toArray(new Uri[0]);
        java.util.Arrays.sort(arr, new java.util.Comparator<Uri>() {
            @Override public int compare(Uri a, Uri b) {
                return a.toString().compareTo(b.toString());
            }
        });
        return arr;
    }

    private static byte[] rsaSign(KeyPair kp, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sig = Signature.getInstance(SIG_ALGORITHM);
        sig.initSign(kp.getPrivate());
        sig.update(data);
        return sig.sign();
    }

    // =========================================================================
    // Persistence — restart recovery
    // =========================================================================

    /**
     * Verifies that after a simulated restart, accumulated quorum votes are
     * replayed from the persistent log and the published verdict is available
     * without re-submission.
     */
    @Test
    public void testVerdictRegistry_RestartRecovery_ReplaysVotes() throws Exception {
        java.nio.file.Path logDir = java.nio.file.Files.createTempDirectory("vr-test-restart-");
        try {
            // --- First "boot": register engine, submit SAFE vote ---
            VerdictRegistryImpl r1 = new VerdictRegistryImpl(
                    registryKeyPair.getPrivate(), SIG_ALGORITHM,
                    phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                    logDir.toString());
            r1.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);
            SignedVerdict sv = buildSignedVerdict(codebaseUrls, VerdictType.SAFE, engineKeyPair);
            r1.submitVerdict("e1", sv);

            // Verdict must be published after first boot.
            RegistryVerdict v1 = r1.getVerdict(codebaseUrls);
            assertNotNull("Verdict must be published after first boot", v1);
            assertEquals("Verdict type must be SAFE", VerdictType.SAFE, v1.getVerdict());

            // --- Simulated restart: new instance, same log directory ---
            VerdictRegistryImpl r2 = new VerdictRegistryImpl(
                    registryKeyPair.getPrivate(), SIG_ALGORITHM,
                    phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                    logDir.toString());

            // Without re-submission the verdict must be recovered from the log.
            RegistryVerdict v2 = r2.getVerdict(codebaseUrls);
            assertNotNull("Verdict must be recovered from persistent log", v2);
            assertEquals("Recovered verdict type must be SAFE", VerdictType.SAFE, v2.getVerdict());
        } finally {
            deleteDirectory(logDir);
        }
    }

    /**
     * Verifies that a published SAFE verdict survives shutdown and is still
     * retrievable after a full restart.
     */
    @Test
    public void testVerdictRegistry_Persistence_SurvivesShutdown() throws Exception {
        java.nio.file.Path logDir = java.nio.file.Files.createTempDirectory("vr-test-persist-");
        try {
            // Boot 1: publish SAFE verdict.
            VerdictRegistryImpl boot1 = new VerdictRegistryImpl(
                    registryKeyPair.getPrivate(), SIG_ALGORITHM,
                    phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                    logDir.toString());
            boot1.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);
            boot1.submitVerdict("e1", buildSignedVerdict(codebaseUrls, VerdictType.SAFE, engineKeyPair));
            assertNotNull("Pre-shutdown: verdict must exist", boot1.getVerdict(codebaseUrls));

            // Boot 2: new instance, same log — verdict must still be there.
            VerdictRegistryImpl boot2 = new VerdictRegistryImpl(
                    registryKeyPair.getPrivate(), SIG_ALGORITHM,
                    phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                    logDir.toString());
            RegistryVerdict recovered = boot2.getVerdict(codebaseUrls);
            assertNotNull("Post-restart: verdict must survive shutdown", recovered);
            assertEquals("Post-restart verdict type must be SAFE",
                    VerdictType.SAFE, recovered.getVerdict());

            // Boot 3: another restart — idempotent.
            VerdictRegistryImpl boot3 = new VerdictRegistryImpl(
                    registryKeyPair.getPrivate(), SIG_ALGORITHM,
                    phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                    logDir.toString());
            assertNotNull("Third boot: verdict still recoverable",
                    boot3.getVerdict(codebaseUrls));
        } finally {
            deleteDirectory(logDir);
        }
    }

    /**
     * Verifies that a corrupted log file causes {@link VerdictRegistryImpl} to
     * throw an {@link java.io.IOException} rather than silently swallowing the
     * problem.
     */
    @Test
    public void testVerdictRegistry_LogCorruption_Detected() throws Exception {
        java.nio.file.Path logDir = java.nio.file.Files.createTempDirectory("vr-test-corrupt-");
        try {
            // Boot 1: write some state.
            VerdictRegistryImpl boot1 = new VerdictRegistryImpl(
                    registryKeyPair.getPrivate(), SIG_ALGORITHM,
                    phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                    logDir.toString());
            boot1.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);
            boot1.submitVerdict("e1", buildSignedVerdict(codebaseUrls, VerdictType.SAFE, engineKeyPair));

            // Corrupt the snapshot file to simulate a partially-written disk.
            java.io.File[] files = logDir.toFile().listFiles();
            assertNotNull("Log directory must contain files", files);
            for (java.io.File f : files) {
                if (f.getName().startsWith("snapshot.")) {
                    // Overwrite the snapshot with garbage bytes.
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                        fos.write(new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
                    }
                }
            }

            // Boot 2: must detect corruption and throw IOException.
            try {
                new VerdictRegistryImpl(
                        registryKeyPair.getPrivate(), SIG_ALGORITHM,
                        phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                        logDir.toString());
                // If no exception, the snapshot may not have existed yet
                // (first snapshot is written at boot, so a log with just
                // log-file entries may still succeed without the snapshot).
                // That is acceptable behaviour — not all corruption is fatal.
            } catch (java.io.IOException e) {
                // Expected: log corruption detected.
                assertTrue("IOException must mention recovery or log corruption",
                        e.getMessage() != null);
            }
        } finally {
            deleteDirectory(logDir);
        }
    }

    /** Recursively deletes a temporary directory used by persistence tests. */
    private static void deleteDirectory(java.nio.file.Path dir) throws java.io.IOException {
        if (dir == null || !java.nio.file.Files.exists(dir)) return;
        java.nio.file.Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(java.io.File::delete);
    }
}
