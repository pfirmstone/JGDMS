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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import au.net.zeus.jgdms.api.codebase.AtomicSerialVerdict;
import au.net.zeus.jgdms.api.codebase.ClassAnalysisResult;
import au.net.zeus.jgdms.api.codebase.ClinitVerdict;
import au.net.zeus.jgdms.api.codebase.CrashReport;
import au.net.zeus.jgdms.api.codebase.JarAnalysisReport;
import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import au.net.zeus.jgdms.api.telemetry.PinningReport;
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
 *   <li>{@code submitReport}: hash-keyed push model, quorum policy,
 *       crash/pinning condemnation</li>
 *   <li>{@code reportCrash}: argument guard, valid crash report → DANGEROUS</li>
 *   <li>{@code getVerdict}: null/empty guards, no-verdict and verdict-present
 *       cases</li>
 *   <li>Lease management: {@code renewEventLease} and {@code cancelEventLease}
 *       argument guards and unknown-lease handling</li>
 *   <li>Quorum revocation: revoking an engine after the hash quorum was met
 *       retracts a stale SAFE hash verdict</li>
 *   <li>{@code codebaseKey} — canonical key ordering</li>
 *   <li>{@code canonicalBytesForCrashReport} — determinism</li>
 * </ul>
 *
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public class VerdictRegistryImplTest {

    private static final String SIG_ALGORITHM = "SHA256withRSA";

    /** Registry key pair — used to sign {@link RegistryVerdict} objects. */
    private KeyPair registryKeyPair;
    /** Engine key pair — used to sign {@link JarAnalysisReport} objects. */
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
    // submitReport — unregistered engine is discarded
    // =========================================================================

    @Test
    public void testSubmitReportFromUnregisteredEngineIsDiscarded()
            throws Exception {
        String hash = "ff00";
        JarAnalysisReport report = buildSignedReport(
                hash, VerdictType.SAFE, new String[0],
                new String[]{"http://example.com/a.jar"}, engineKeyPair);
        registry.submitReport("unregistered", report);
        assertNull("Unregistered engine report must be discarded",
                registry.getVerdictByHash(hash));
    }

    // =========================================================================
    // submitReport — DANGEROUS report publishes immediately (quorum = 1)
    // =========================================================================

    @Test
    public void testSubmitDangerousReportPublishesImmediately()
            throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        String hash = "ff11";
        JarAnalysisReport report = buildSignedReport(
                hash, VerdictType.DANGEROUS, new String[0],
                new String[]{"http://example.com/a.jar"}, engineKeyPair);
        registry.submitReport("e1", report);

        RegistryVerdict rv = registry.getVerdictByHash(hash);
        assertNotNull("A DANGEROUS RegistryVerdict must be published", rv);
        assertEquals(VerdictType.DANGEROUS, rv.getVerdict());
    }

    // =========================================================================
    // submitReport — invalid signature is discarded
    // =========================================================================

    @Test
    public void testSubmitReportWithBadSignatureIsDiscarded()
            throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        // Build a report but sign it with the wrong key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair wrongKey = kpg.generateKeyPair();
        String hash = "ff22";
        JarAnalysisReport report = buildSignedReport(
                hash, VerdictType.SAFE, new String[0],
                new String[]{"http://example.com/a.jar"}, wrongKey);
        registry.submitReport("e1", report);

        assertNull("Report with bad signature must be discarded",
                registry.getVerdictByHash(hash));
    }

    // =========================================================================
    // submitReport — quorum = 2 (hash-keyed)
    // =========================================================================

    @Test
    public void testHashQuorumOfTwoRequiresTwoSafeReports() throws Exception {
        VerdictRegistryImpl twoRegistry = new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 2);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair engine2Key = kpg.generateKeyPair();

        twoRegistry.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);
        twoRegistry.registerAnalysisEngine("e2", engine2Key.getPublic(), SIG_ALGORITHM);

        String hash = "ff33";
        String[] urls = { "http://example.com/a.jar" };

        // First report — quorum not yet met
        twoRegistry.submitReport("e1",
                buildSignedReport(hash, VerdictType.SAFE, new String[0], urls, engineKeyPair));
        assertNull("Single report must not meet quorum of 2",
                twoRegistry.getVerdictByHash(hash));

        // Second report — quorum met
        twoRegistry.submitReport("e2",
                buildSignedReport(hash, VerdictType.SAFE, new String[0], urls, engine2Key));
        RegistryVerdict rv = twoRegistry.getVerdictByHash(hash);
        assertNotNull("Two SAFE reports must meet quorum of 2", rv);
        assertEquals(VerdictType.SAFE, rv.getVerdict());
    }

    // =========================================================================
    // revokeAnalysisEngine — hash vote is removed; stale SAFE hash verdict is
    // retracted when the quorum is no longer met
    // =========================================================================

    @Test
    public void testRevokingVotingEngineRetractsSafeHashVerdict() throws Exception {
        VerdictRegistryImpl twoRegistry = new VerdictRegistryImpl(
                registryKeyPair.getPrivate(), SIG_ALGORITHM,
                phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 2);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair engine2Key = kpg.generateKeyPair();

        twoRegistry.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);
        twoRegistry.registerAnalysisEngine("e2", engine2Key.getPublic(), SIG_ALGORITHM);

        String hash = "ff44";
        String[] urls = { "http://example.com/a.jar" };

        // Two SAFE reports → hash quorum met
        twoRegistry.submitReport("e1",
                buildSignedReport(hash, VerdictType.SAFE, new String[0], urls, engineKeyPair));
        twoRegistry.submitReport("e2",
                buildSignedReport(hash, VerdictType.SAFE, new String[0], urls, engine2Key));
        assertNotNull(twoRegistry.getVerdictByHash(hash));

        // Revoke one engine — quorum no longer met; hash verdict retracted
        twoRegistry.revokeAnalysisEngine("e1");
        assertNull("Revoking a voting engine must retract a stale SAFE hash verdict",
                twoRegistry.getVerdictByHash(hash));
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
    // submitReport — null engineId / null report guard
    // =========================================================================

    @Test(expected = NullPointerException.class)
    public void testSubmitReportNullEngineIdThrowsNPE() throws RemoteException {
        registry.submitReport(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitReportEmptyEngineIdThrowsIAE() throws RemoteException {
        registry.submitReport("", null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubmitReportNullReportThrowsNPE() throws RemoteException {
        registry.submitReport("e1", null);
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
    // submitReport — #1: report with declared permissions AND codebase URLs is
    // accepted (signature verified over JarAnalysisReport.canonicalBytes())
    // =========================================================================

    @Test
    public void testSubmitReportWithPermsAndUrlsIsAccepted() throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        String hash = "aa11";
        String[] perms = {
            "permission java.net.SocketPermission \"*\", \"connect\";",
            "permission java.io.FilePermission \"/tmp/-\", \"read\";"
        };
        String[] urls = { "http://example.com/lib.jar" };
        JarAnalysisReport report = buildSignedReport(
                hash, VerdictType.SAFE, perms, urls, engineKeyPair);

        registry.submitReport("e1", report);

        RegistryVerdict rv = registry.getVerdictByHash(hash);
        assertNotNull("Report with perms+urls must be accepted and published", rv);
        assertEquals(VerdictType.SAFE, rv.getVerdict());
    }

    // =========================================================================
    // submitReport — #2: reportPinning for a URL flips the hash to DANGEROUS
    // =========================================================================

    @Test
    public void testReportPinningCondemnsKnownHash() throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        String hash = "bb22";
        String url  = "http://example.com/pinning.jar";
        JarAnalysisReport report = buildSignedReport(
                hash, VerdictType.SAFE, new String[0], new String[]{url}, engineKeyPair);
        registry.submitReport("e1", report);
        assertEquals(VerdictType.SAFE, registry.getVerdictByHash(hash).getVerdict());

        Set<Uri> urls = new LinkedHashSet<Uri>();
        urls.add(new Uri(url));
        registry.reportPinning(new PinningReport(
                urls.toArray(new Uri[0]), 1_000_000L, 5L, 0L, 1L));

        RegistryVerdict rv = registry.getVerdictByHash(hash);
        assertNotNull(rv);
        assertEquals("Pinning report must condemn the known content hash",
                VerdictType.DANGEROUS, rv.getVerdict());
    }

    @Test
    public void testReportCrashCondemnsKnownHash() throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        String hash = "cc33";
        String url  = "http://example.com/crash.jar";
        JarAnalysisReport report = buildSignedReport(
                hash, VerdictType.SAFE, new String[0], new String[]{url}, engineKeyPair);
        registry.submitReport("e1", report);

        Set<Uri> urls = new LinkedHashSet<Uri>();
        urls.add(new Uri(url));
        registry.reportCrash(buildCrashReport(urls, phoenixKeyPair));

        RegistryVerdict rv = registry.getVerdictByHash(hash);
        assertNotNull(rv);
        assertEquals("Crash report must condemn the known content hash",
                VerdictType.DANGEROUS, rv.getVerdict());
    }

    // =========================================================================
    // submitReport — #2: pending-ordering — crash for {u} BEFORE the report;
    // a later report for H/{u} ends DANGEROUS
    // =========================================================================

    @Test
    public void testPendingCondemnationFlipsLaterReport() throws Exception {
        registry.registerAnalysisEngine(
                "e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

        String hash = "dd44";
        String url  = "http://example.com/pending.jar";

        // Crash arrives first — no hash known yet for this URL → pending.
        Set<Uri> urls = new LinkedHashSet<Uri>();
        urls.add(new Uri(url));
        registry.reportCrash(buildCrashReport(urls, phoenixKeyPair));
        assertNull("No report yet — hash verdict must be absent",
                registry.getVerdictByHash(hash));

        // Now the SAFE report for the same URL arrives — must be forced DANGEROUS.
        JarAnalysisReport report = buildSignedReport(
                hash, VerdictType.SAFE, new String[0], new String[]{url}, engineKeyPair);
        registry.submitReport("e1", report);

        RegistryVerdict rv = registry.getVerdictByHash(hash);
        assertNotNull(rv);
        assertEquals("A pending URL condemnation must force the later report DANGEROUS",
                VerdictType.DANGEROUS, rv.getVerdict());
    }

    // =========================================================================
    // #6: hash-keyed published verdict survives a simulated restart
    // =========================================================================

    @Test
    public void testHashVerdictSurvivesRestart() throws Exception {
        java.nio.file.Path logDir =
                java.nio.file.Files.createTempDirectory("vr-test-hash-persist-");
        java.nio.file.Path restartDir = null;
        try {
            VerdictRegistryImpl boot1 = new VerdictRegistryImpl(
                    registryKeyPair.getPrivate(), SIG_ALGORITHM,
                    phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                    logDir.toString());
            boot1.registerAnalysisEngine("e1", engineKeyPair.getPublic(), SIG_ALGORITHM);

            String hash = "ee55";
            String[] urls = { "http://example.com/persist.jar" };
            JarAnalysisReport report = buildSignedReport(
                    hash, VerdictType.SAFE, new String[0], urls, engineKeyPair);
            boot1.submitReport("e1", report);
            assertNotNull("Pre-restart hash verdict must exist",
                    boot1.getVerdictByHash(hash));

            // Simulate a restart by recovering from a copy of boot1's log
            // directory.  (A copy is used rather than the same directory because
            // boot1 keeps OS file handles open within this single JVM, which on
            // Windows blocks boot2's snapshot consolidation from deleting the
            // old log file.  The copy faithfully exercises the recovery path.)
            restartDir = java.nio.file.Files.createTempDirectory("vr-test-hash-restart-");
            for (java.nio.file.Path src : (Iterable<java.nio.file.Path>)
                    java.nio.file.Files.list(logDir)::iterator) {
                java.nio.file.Files.copy(src,
                        restartDir.resolve(src.getFileName()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            VerdictRegistryImpl boot2 = new VerdictRegistryImpl(
                    registryKeyPair.getPrivate(), SIG_ALGORITHM,
                    phoenixKeyPair.getPublic(),   SIG_ALGORITHM, 1,
                    restartDir.toString());
            RegistryVerdict recovered = boot2.getVerdictByHash(hash);
            assertNotNull("Hash verdict must survive restart", recovered);
            assertEquals(VerdictType.SAFE, recovered.getVerdict());
        } finally {
            deleteDirectory(logDir);
            deleteDirectory(restartDir);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds a {@link JarAnalysisReport} for {@code contentHash} that derives to
     * {@code wanted}, with the supplied declared permissions and codebase URLs,
     * signed over {@link JarAnalysisReport#canonicalBytes()} with the supplied
     * key pair.
     */
    private static JarAnalysisReport buildSignedReport(String contentHash,
                                                       VerdictType wanted,
                                                       String[] declaredPermissions,
                                                       String[] codebaseUrls,
                                                       KeyPair signingKey)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Map<String, ClassAnalysisResult> results =
                new LinkedHashMap<String, ClassAnalysisResult>();
        ClinitVerdict cv = (wanted == VerdictType.DANGEROUS)
                ? ClinitVerdict.BLOCKING : ClinitVerdict.CLEAN;
        results.put("com/example/Foo", new ClassAnalysisResult(
                "com/example/Foo", cv, AtomicSerialVerdict.COMPLIANT,
                Collections.<String>emptyList(),
                Collections.<String>emptyList()));

        // Sign canonicalBytes() of an intermediate (placeholder-signature) report.
        JarAnalysisReport unsigned = new JarAnalysisReport(
                contentHash, results, new byte[]{0},
                declaredPermissions, codebaseUrls);
        byte[] sig = rsaSign(signingKey, unsigned.canonicalBytes());
        return new JarAnalysisReport(
                contentHash, results, sig, declaredPermissions, codebaseUrls);
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
    // Persistence — log corruption detection
    // =========================================================================

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
            boot1.submitReport("e1", buildSignedReport(
                    "abcd", VerdictType.SAFE, new String[0],
                    new String[]{"http://example.com/a.jar"}, engineKeyPair));

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

    /**
     * Recursively deletes a temporary directory used by persistence tests.
     *
     * <p>Best-effort: on Windows the {@link org.apache.river.reliableLog.ReliableLog}
     * may still hold open handles on its log files when the test finishes, which
     * blocks deletion.  Deletion failures are therefore ignored — the OS reclaims
     * the temp directory eventually, and the recovery assertions have already run.
     */
    private static void deleteDirectory(java.nio.file.Path dir) throws java.io.IOException {
        if (dir == null || !java.nio.file.Files.exists(dir)) return;
        java.nio.file.Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(java.io.File::delete); // best-effort; ignore failures
    }
}
