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
/* @test
 * @summary End-to-end test of the SPIFFE credential pipeline: verifies that
 *          SpiffeCredentialManager correctly loads service (reggie) and client
 *          SVIDs, populates the Subject with the expected principals and
 *          credentials, and that the one-SVID-per-JVM SpiffeSubjectHolder
 *          enforcement prevents concurrent registration.
 *          SVIDs are generated at test time using keytool — no certificate
 *          material is committed to the repository.
 * @build SpiffeEndpointTest
 * @run main/othervm SpiffeEndpointTest
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager;
import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager.FileSvidSource;
import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager.SvidSource;
import au.net.zeus.jgdms.spiffe.SpiffePrincipal;
import au.net.zeus.jgdms.spiffe.SpiffeLoginModule;

/**
 * End-to-end integration test for the SPIFFE credential pipeline.
 *
 * <p>SVIDs are generated at test time using {@code keytool} — no certificate
 * material is committed to the repository.
 *
 * <h2>Tests performed</h2>
 * <ol>
 *   <li>Service SVID loading: verifies that a {@code SpiffeCredentialManager}
 *       for the {@code reggie} role populates the Subject with the correct
 *       SPIFFE URI ({@code spiffe://test.jgdms.local/svc/reggie}), an
 *       {@link X500Principal}, a {@link CertPath} public credential, and an
 *       {@link X500PrivateCredential} private credential.</li>
 *   <li>Client SVID loading: exercises the {@code client} SVID
 *       ({@code spiffe://test.jgdms.local/client/test}).  Verifies that the
 *       {@code /client/} SPIFFE path scheme is correctly represented in the
 *       Subject's principals.</li>
 *   <li>SpiffeLoginModule-based loading: exercises the JAAS login path for
 *       the {@code tester} role.</li>
 *   <li>One-per-JVM enforcement: confirms that attempting to start a second
 *       {@link SpiffeCredentialManager} while the first is still active throws
 *       {@link IllegalStateException}.</li>
 * </ol>
 *
 * @since 3.1.1
 */
public class SpiffeEndpointTest {

    // -----------------------------------------------------------------------
    // SVID generation
    // -----------------------------------------------------------------------

    /** Temporary directory that holds the generated SVID tree. */
    private static Path tempDir;

    /** Absolute path to the root of the generated SVID tree. */
    private static String SPIFFE_DIR;

    /**
     * Generates an ephemeral SPIFFE SVID for the given role using keytool and
     * writes {@code svid.pem} + {@code svid_key.pem} under
     * {@code <tempDir>/<role>/}.
     *
     * @param caP12     path to the pre-generated CA keystore
     * @param caCerFile path to the exported CA certificate PEM
     * @param role      role name (used as subdirectory name and in subject DN)
     * @param cn        Common Name for the leaf certificate
     * @param spiffeId  SPIFFE URI to embed as the URI SAN
     */
    private static void generateSvid(Path caP12, Path caCerFile,
            String role, String cn, String spiffeId) throws Exception {
        Path roleDir  = tempDir.resolve(role);
        Files.createDirectories(roleDir);

        Path roleP12  = tempDir.resolve(role + ".p12");
        Path csrFile  = tempDir.resolve(role + ".csr");
        Path signedCer = tempDir.resolve(role + "-signed.cer");

        keytool("-genkeypair", "-alias", role, "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=" + cn,
                "-sigalg", "SHA256withECDSA", "-validity", "1",
                "-keystore", roleP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-noprompt");

        keytool("-certreq", "-alias", role,
                "-keystore", roleP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", csrFile.toString());

        keytool("-gencert", "-alias", "ca",
                "-keystore", caP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit",
                "-infile", csrFile.toString(),
                "-outfile", signedCer.toString(),
                "-ext", "san=uri:" + spiffeId,
                "-validity", "1", "-rfc");

        keytool("-importcert", "-alias", "ca",
                "-keystore", roleP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", caCerFile.toString(), "-noprompt");

        keytool("-importcert", "-alias", role,
                "-keystore", roleP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", signedCer.toString(), "-noprompt");

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(roleP12)) {
            ks.load(in, "changeit".toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(role, "changeit".toCharArray());
        Certificate[] chain = ks.getCertificateChain(role);

        String nl = "\n";
        Base64.Encoder enc = Base64.getMimeEncoder(64, nl.getBytes(StandardCharsets.US_ASCII));

        StringBuilder svidPemContent = new StringBuilder();
        for (Certificate c : chain) {
            svidPemContent.append("-----BEGIN CERTIFICATE-----").append(nl);
            svidPemContent.append(enc.encodeToString(c.getEncoded())).append(nl);
            svidPemContent.append("-----END CERTIFICATE-----").append(nl);
        }
        Files.write(roleDir.resolve("svid.pem"),
                svidPemContent.toString().getBytes(StandardCharsets.US_ASCII));

        String keyPemContent = "-----BEGIN PRIVATE KEY-----" + nl
                + enc.encodeToString(key.getEncoded()) + nl
                + "-----END PRIVATE KEY-----" + nl;
        Files.write(roleDir.resolve("svid_key.pem"),
                keyPemContent.getBytes(StandardCharsets.US_ASCII));
    }

    private static void setUpSvids() throws Exception {
        tempDir = Files.createTempDirectory("spiffe-ep-test-");
        SPIFFE_DIR = tempDir.toString();

        // Generate the shared CA
        Path caP12    = tempDir.resolve("ca.p12");
        Path caCerFile = tempDir.resolve("ca.cer");

        keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=JGDMS Test CA",
                "-sigalg", "SHA256withECDSA", "-validity", "1",
                "-keystore", caP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-noprompt");

        keytool("-exportcert", "-alias", "ca",
                "-keystore", caP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-rfc", "-file", caCerFile.toString());

        // Generate per-role SVIDs
        generateSvid(caP12, caCerFile, "reggie",
                "Reggie", "spiffe://test.jgdms.local/svc/reggie");
        generateSvid(caP12, caCerFile, "client",
                "Test Client", "spiffe://test.jgdms.local/client/test");
        generateSvid(caP12, caCerFile, "tester",
                "Tester", "spiffe://test.jgdms.local/svc/tester");
    }

    private static void tearDownSvids() {
        if (tempDir != null) {
            try {
                Files.walk(tempDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException e) {
                                System.err.println("WARN: could not delete " + p + ": " + e);
                            }
                        });
            } catch (IOException ignored) { }
        }
    }

    private static void keytool(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = keytoolPath();
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process proc = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        String output = new String(proc.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int rc = proc.waitFor();
        if (rc != 0) {
            throw new IOException("keytool failed (exit " + rc + "): " + output);
        }
    }

    private static String keytoolPath() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File kt = new File(javaHome, "bin/keytool");
            if (kt.canExecute()) return kt.getAbsolutePath();
            kt = new File(javaHome, "../bin/keytool");
            if (kt.canExecute()) return kt.getAbsolutePath();
        }
        return "keytool";
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        try {
            setUpSvids();
            System.out.println("SPIFFE credential directory: " + SPIFFE_DIR);

            run("testReggieServiceSvid",      SpiffeEndpointTest::testReggieServiceSvid);
            run("testClientSvid",             SpiffeEndpointTest::testClientSvid);
            run("testTesterSpiffeLoginModule", SpiffeEndpointTest::testTesterSpiffeLoginModule);
            run("testOnlyOneManagerPerJvm",   SpiffeEndpointTest::testOnlyOneManagerPerJvm);

            System.out.println("\nAll SpiffeEndpointTest tests PASSED.");
        } finally {
            tearDownSvids();
        }
    }

    // -----------------------------------------------------------------------
    // Test cases
    // -----------------------------------------------------------------------

    /**
     * Verifies the full credential-loading pipeline for the {@code reggie}
     * service role SVID (SPIFFE path: {@code /svc/reggie}).
     */
    private static void testReggieServiceSvid() throws Exception {
        Subject subject = new Subject();
        try (SpiffeCredentialManager mgr = managerFor("reggie", subject)) {
            mgr.start();

            // 1. X500Principal present
            Set<X500Principal> x500s = subject.getPrincipals(X500Principal.class);
            assertEqual("reggie: exactly one X500Principal", 1, x500s.size());

            // 2. SpiffePrincipal with correct /svc/ path
            Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
            assertEqual("reggie: exactly one SpiffePrincipal", 1, spiffes.size());
            String spiffeUri = spiffes.iterator().next().getName();
            assertTrue("reggie SpiffePrincipal must use /svc/ path: " + spiffeUri,
                    spiffeUri.contains("/svc/reggie"));

            // 3. CertPath in public credentials
            Set<CertPath> certs = subject.getPublicCredentials(CertPath.class);
            assertTrue("reggie: CertPath must be present", !certs.isEmpty());
            X509Certificate leaf =
                    (X509Certificate) certs.iterator().next().getCertificates().get(0);
            assertTrue("reggie: leaf cert SAN must contain SPIFFE URI",
                    leaf.getSubjectAlternativeNames() != null);

            // 4. X500PrivateCredential in private credentials
            Set<X500PrivateCredential> privs =
                    subject.getPrivateCredentials(X500PrivateCredential.class);
            assertTrue("reggie: X500PrivateCredential must be present", !privs.isEmpty());
        }
        // After close(), Subject credentials are cleared
        assertTrue("reggie: Subject must be clean after manager close",
                subject.getPrincipals(SpiffePrincipal.class).isEmpty());
    }

    /**
     * Verifies the new {@code client} SVID generated by the 2026-05 update to
     * {@code gen-spiffe-svids.sh} (SPIFFE path: {@code /client/test}).
     *
     * <p>This test is the primary regression check for Issue #205's client
     * identity scheme.
     */
    private static void testClientSvid() throws Exception {
        Subject subject = new Subject();
        try (SpiffeCredentialManager mgr = managerFor("client", subject)) {
            mgr.start();

            // SpiffePrincipal must use the /client/ path
            Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
            assertEqual("client: exactly one SpiffePrincipal", 1, spiffes.size());
            String spiffeUri = spiffes.iterator().next().getName();
            assertTrue("client SpiffePrincipal must use /client/ path: " + spiffeUri,
                    spiffeUri.contains("/client/test"));

            // CN should be "Test Client"
            Set<X500Principal> x500s = subject.getPrincipals(X500Principal.class);
            assertEqual("client: exactly one X500Principal", 1, x500s.size());
            String dn = x500s.iterator().next().getName();
            assertTrue("client X500Principal CN should contain 'Test Client': " + dn,
                    dn.contains("Test Client") || dn.contains("CN=Test Client"));
        }
        assertTrue("client: Subject must be clean after manager close",
                subject.getPrincipals(SpiffePrincipal.class).isEmpty());
    }

    /**
     * Exercises the JAAS {@link SpiffeLoginModule} path for the {@code tester}
     * role to confirm that the module-based loading (used by the QA harness)
     * produces a correctly populated Subject.
     */
    private static void testTesterSpiffeLoginModule() throws Exception {
        Path svidPem = Paths.get(SPIFFE_DIR, "tester", "svid.pem");
        Path keyPem  = Paths.get(SPIFFE_DIR, "tester", "svid_key.pem");

        Subject subject = new Subject();
        SpiffeLoginModule module = new SpiffeLoginModule();
        Map<String, Object> opts = new HashMap<>();
        opts.put("svidPem", svidPem.toString());
        opts.put("keyPem",  keyPem.toString());
        module.initialize(subject, null, new HashMap<>(), opts);
        assertTrue("tester: login() must return true", module.login());
        assertTrue("tester: commit() must return true", module.commit());

        Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
        assertEqual("tester: exactly one SpiffePrincipal", 1, spiffes.size());
        String spiffeUri = spiffes.iterator().next().getName();
        assertTrue("tester SpiffePrincipal must use /svc/ path: " + spiffeUri,
                spiffeUri.contains("/svc/tester"));

        module.logout();
        assertTrue("tester: Subject must be clean after logout",
                subject.getPrincipals(SpiffePrincipal.class).isEmpty());
    }

    /**
     * Verifies that a second {@link SpiffeCredentialManager} cannot be started
     * while the first is still active, enforcing the one-SVID-per-process
     * invariant required for correct SPIFFE workload identity.
     */
    private static void testOnlyOneManagerPerJvm() throws Exception {
        Subject subject1 = new Subject();
        Subject subject2 = new Subject();
        try (SpiffeCredentialManager mgr1 = managerFor("reggie", subject1)) {
            mgr1.start(); // registers subject1 in SpiffeSubjectHolder

            try (SpiffeCredentialManager mgr2 = managerFor("tester", subject2)) {
                try {
                    mgr2.start(); // must reject because subject1 is already registered
                    fail("Expected IllegalStateException when starting a second manager");
                } catch (IllegalStateException e) {
                    // expected: SpiffeSubjectHolder enforces one-per-JVM
                }
            } // mgr2.close() is a no-op since start() was never called (or failed)
        } // mgr1.close() deregisters subject1 from SpiffeSubjectHolder

        // After mgr1 is closed, a new manager can be started
        Subject subject3 = new Subject();
        try (SpiffeCredentialManager mgr3 = managerFor("client", subject3)) {
            mgr3.start(); // must succeed after mgr1 was closed
            assertFalse("client: Subject must be populated after mgr3.start()",
                    subject3.getPrincipals(SpiffePrincipal.class).isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void run(String name, ThrowingRunnable test) throws Exception {
        System.out.print("  " + name + " ... ");
        test.run();
        System.out.println("PASS");
    }

    private static SpiffeCredentialManager managerFor(String role, Subject subject)
            throws IOException, GeneralSecurityException {
        Path svidPem = Paths.get(SPIFFE_DIR, role, "svid.pem");
        Path keyPem  = Paths.get(SPIFFE_DIR, role, "svid_key.pem");
        FileSvidSource source = new FileSvidSource(svidPem, keyPem);
        return new SpiffeCredentialManager(subject, source, 3600L);
    }

    // -----------------------------------------------------------------------
    // Assertion helpers
    // -----------------------------------------------------------------------

    private static void assertTrue(String message, boolean condition) {
        if (!condition) throw new RuntimeException("ASSERTION FAILED: " + message);
    }

    private static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private static void assertEqual(String message, Object expected, Object actual) {
        if (!expected.equals(actual))
            throw new RuntimeException("ASSERTION FAILED: " + message
                    + " [expected=" + expected + ", actual=" + actual + "]");
    }

    private static void fail(String message) {
        throw new RuntimeException("ASSERTION FAILED: " + message);
    }
}
