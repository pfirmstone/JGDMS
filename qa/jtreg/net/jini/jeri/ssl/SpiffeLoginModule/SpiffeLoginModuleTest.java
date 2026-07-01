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
 * @summary Integration tests for SpiffeLoginModule: full JAAS
 *          login/commit/abort/logout cycle. SVIDs are generated at test
 *          time using keytool — no certificate material is committed to
 *          the repository.
 * @build SpiffeLoginModuleTest
 * @run main/othervm SpiffeLoginModuleTest
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import java.security.cert.CertPath;
import au.net.zeus.jgdms.spiffe.SpiffeLoginModule;
import au.net.zeus.jgdms.spiffe.SpiffePrincipal;

/**
 * Integration tests for {@link SpiffeLoginModule}.
 *
 * Covers the full JAAS login / commit / abort / logout cycle.
 * SVIDs are generated at test time using {@code keytool} — no certificate
 * material is committed to the repository.
 */
public class SpiffeLoginModuleTest {

    /** SPIFFE ID embedded in the generated reggie SVID. */
    private static final String REGGIE_SPIFFE_ID =
            "spiffe://test.jgdms.local/svc/reggie";

    // Populated by setUpSvids()
    private static Path tempDir;
    private static String SVID_PEM;
    private static String KEY_PEM;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        String savedProp = System.getProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY);
        try {
            setUpSvids();
            testLoginWithExplicitPathsPopulatesPrincipals();
            testLoginWithExplicitPathsPopulatesPublicCredential();
            testLoginWithExplicitPathsPopulatesPrivateCredential();
            testLoginWithServiceRolePopulatesSubject();
            testAbortAfterLoginDoesNotPopulateSubject();
            testLogoutClearsPrincipals();
            testCommitIdempotentPrincipalCount();
            testMissingServiceRoleAndPaths_throwsLoginException();
            testMissingSystemProperty_throwsLoginException();
            testNonExistentSvidPem_throwsLoginException();
            testLogoutAfterCommitLeavesSubjectClean();
        } finally {
            restoreProperty(savedProp);
            tearDownSvids();
        }
        System.out.println("All SpiffeLoginModule integration tests PASSED.");
    }

    // -----------------------------------------------------------------------
    // SVID generation helpers
    // -----------------------------------------------------------------------

    /**
     * Generates an ephemeral SPIFFE SVID for {@code reggie} using keytool
     * and writes {@code svid.pem} + {@code svid_key.pem} into a temporary
     * directory laid out as {@code <tempDir>/reggie/}.  The static fields
     * {@link #SVID_PEM} and {@link #KEY_PEM} are set to those paths.
     */
    private static void setUpSvids() throws Exception {
        tempDir = Files.createTempDirectory("spiffe-login-test-");
        Path reggieDir = tempDir.resolve("reggie");
        Files.createDirectories(reggieDir);

        Path caP12     = tempDir.resolve("ca.p12");
        Path reggieP12 = tempDir.resolve("reggie.p12");
        Path csrFile   = tempDir.resolve("reggie.csr");
        Path signedCer = tempDir.resolve("reggie-signed.cer");
        Path caCerFile = tempDir.resolve("ca.cer");

        keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=JGDMS Test CA",
                "-sigalg", "SHA256withECDSA", "-validity", "1",
                "-keystore", caP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-noprompt");

        keytool("-genkeypair", "-alias", "reggie", "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=Reggie",
                "-sigalg", "SHA256withECDSA", "-validity", "1",
                "-keystore", reggieP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-noprompt");

        keytool("-certreq", "-alias", "reggie",
                "-keystore", reggieP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", csrFile.toString());

        keytool("-gencert", "-alias", "ca",
                "-keystore", caP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit",
                "-infile", csrFile.toString(),
                "-outfile", signedCer.toString(),
                "-ext", "san=uri:" + REGGIE_SPIFFE_ID,
                "-validity", "1", "-rfc");

        keytool("-exportcert", "-alias", "ca",
                "-keystore", caP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-rfc", "-file", caCerFile.toString());

        keytool("-importcert", "-alias", "ca",
                "-keystore", reggieP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", caCerFile.toString(), "-noprompt");

        keytool("-importcert", "-alias", "reggie",
                "-keystore", reggieP12.toString(), "-storetype", "PKCS12",
                "-storepass", "changeit", "-file", signedCer.toString(), "-noprompt");

        // Extract cert chain + private key from the PKCS12 keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(reggieP12)) {
            ks.load(in, "changeit".toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey("reggie", "changeit".toCharArray());
        Certificate[] chain = ks.getCertificateChain("reggie");

        String nl = "\n";
        Base64.Encoder enc = Base64.getMimeEncoder(64, nl.getBytes(StandardCharsets.US_ASCII));

        StringBuilder svidPemContent = new StringBuilder();
        for (Certificate c : chain) {
            svidPemContent.append("-----BEGIN CERTIFICATE-----").append(nl);
            svidPemContent.append(enc.encodeToString(c.getEncoded())).append(nl);
            svidPemContent.append("-----END CERTIFICATE-----").append(nl);
        }
        Path svidPemPath = reggieDir.resolve("svid.pem");
        Files.write(svidPemPath,
                svidPemContent.toString().getBytes(StandardCharsets.US_ASCII));

        String keyPemContent = "-----BEGIN PRIVATE KEY-----" + nl
                + enc.encodeToString(key.getEncoded()) + nl
                + "-----END PRIVATE KEY-----" + nl;
        Path svidKeyPemPath = reggieDir.resolve("svid_key.pem");
        Files.write(svidKeyPemPath,
                keyPemContent.getBytes(StandardCharsets.US_ASCII));

        SVID_PEM = svidPemPath.toString();
        KEY_PEM  = svidKeyPemPath.toString();
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
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Drives the module through {@code initialize → login → commit} and
     * returns it so the caller can invoke {@code logout()}.
     */
    private static SpiffeLoginModule loginAndCommit(
            Subject subject, Map<String, ?> options) throws LoginException {
        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), options);
        if (!module.login())
            throw new RuntimeException("login() returned false");
        if (!module.commit())
            throw new RuntimeException("commit() returned false");
        return module;
    }

    private static void restoreProperty(String saved) {
        if (saved != null)
            System.setProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY, saved);
        else
            System.clearProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY);
    }

    private static void assertTrue(String msg, boolean cond) {
        if (!cond) throw new RuntimeException("FAIL: " + msg);
    }

    private static void assertFalse(String msg, boolean cond) {
        if (cond) throw new RuntimeException("FAIL: " + msg);
    }

    private static void assertEquals(String msg, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new RuntimeException("FAIL: " + msg
                    + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void assertEquals(String msg, int expected, int actual) {
        if (expected != actual)
            throw new RuntimeException("FAIL: " + msg
                    + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void assertNotNull(String msg, Object obj) {
        if (obj == null) throw new RuntimeException("FAIL: " + msg + " was null");
    }

    // -----------------------------------------------------------------------
    // Happy path: explicit svidPem + keyPem options
    // -----------------------------------------------------------------------

    static void testLoginWithExplicitPathsPopulatesPrincipals() throws Exception {
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", SVID_PEM);
        opts.put("keyPem",  KEY_PEM);

        loginAndCommit(subject, opts);

        Set<X500Principal> x500s = subject.getPrincipals(X500Principal.class);
        assertEquals("Should have one X500Principal", 1, x500s.size());
        String dn = x500s.iterator().next().getName();
        assertTrue("DN should contain Reggie: " + dn, dn.contains("Reggie"));

        Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
        assertEquals("Should have one SpiffePrincipal", 1, spiffes.size());
        assertEquals("SPIFFE URI", REGGIE_SPIFFE_ID,
                spiffes.iterator().next().getName());
    }

    static void testLoginWithExplicitPathsPopulatesPublicCredential() throws Exception {
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", SVID_PEM);
        opts.put("keyPem",  KEY_PEM);

        loginAndCommit(subject, opts);

        Set<CertPath> certPaths = subject.getPublicCredentials(CertPath.class);
        assertEquals("Should have one CertPath", 1, certPaths.size());
        assertFalse("CertPath should not be empty",
                certPaths.iterator().next().getCertificates().isEmpty());
    }

    static void testLoginWithExplicitPathsPopulatesPrivateCredential() throws Exception {
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", SVID_PEM);
        opts.put("keyPem",  KEY_PEM);

        loginAndCommit(subject, opts);

        Set<X500PrivateCredential> privs =
                subject.getPrivateCredentials(X500PrivateCredential.class);
        assertEquals("Should have one X500PrivateCredential", 1, privs.size());
        assertNotNull("PrivateKey must not be null",
                privs.iterator().next().getPrivateKey());
    }

    // -----------------------------------------------------------------------
    // Happy path: serviceRole + system property
    // -----------------------------------------------------------------------

    static void testLoginWithServiceRolePopulatesSubject() throws Exception {
        Subject subject = new Subject();
        // Point the property at tempDir; the module then looks for
        // {dir}/{serviceRole}/svid.pem and svid_key.pem.
        System.setProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY,
                tempDir.toString());
        Map<String, String> opts = new HashMap<>();
        opts.put("serviceRole", "reggie");

        loginAndCommit(subject, opts);

        Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
        assertEquals("Should have one SpiffePrincipal", 1, spiffes.size());
        assertEquals("SPIFFE URI", REGGIE_SPIFFE_ID,
                spiffes.iterator().next().getName());
    }

    // -----------------------------------------------------------------------
    // abort() prevents principals from being added
    // -----------------------------------------------------------------------

    static void testAbortAfterLoginDoesNotPopulateSubject() throws Exception {
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", SVID_PEM);
        opts.put("keyPem",  KEY_PEM);

        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), opts);
        module.login();
        module.abort();

        assertTrue("Principals empty after abort",
                subject.getPrincipals().isEmpty());
        assertTrue("Public credentials empty after abort",
                subject.getPublicCredentials().isEmpty());
        assertTrue("Private credentials empty after abort",
                subject.getPrivateCredentials().isEmpty());
    }

    // -----------------------------------------------------------------------
    // logout() removes principals and credentials
    // -----------------------------------------------------------------------

    static void testLogoutClearsPrincipals() throws Exception {
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", SVID_PEM);
        opts.put("keyPem",  KEY_PEM);

        SpiffeLoginModule module = loginAndCommit(subject, opts);
        assertFalse("Subject has principals before logout",
                subject.getPrincipals().isEmpty());

        module.logout();

        assertTrue("Principals empty after logout",
                subject.getPrincipals().isEmpty());
        assertTrue("Public credentials empty after logout",
                subject.getPublicCredentials().isEmpty());
        assertTrue("Private credentials empty after logout",
                subject.getPrivateCredentials().isEmpty());
    }

    // -----------------------------------------------------------------------
    // commit() is idempotent for equal SVIDs
    // -----------------------------------------------------------------------

    static void testCommitIdempotentPrincipalCount() throws Exception {
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", SVID_PEM);
        opts.put("keyPem",  KEY_PEM);

        loginAndCommit(subject, opts);
        int count1 = subject.getPrincipals().size();

        // A second independent commit with identical credentials: the Set
        // deduplicates equal principals so the count must not grow.
        SpiffeLoginModule module2 = new SpiffeLoginModule();
        module2.initialize(subject, null, new HashMap<>(), opts);
        module2.login();
        module2.commit();

        assertEquals("Principal count must not grow for identical SVIDs",
                count1, subject.getPrincipals().size());
    }

    // -----------------------------------------------------------------------
    // Error: missing serviceRole without explicit paths
    // -----------------------------------------------------------------------

    static void testMissingServiceRoleAndPaths_throwsLoginException() {
        Subject subject = new Subject();
        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), new HashMap<>());
        try {
            module.login();
            throw new RuntimeException(
                    "FAIL: Expected LoginException when no paths or serviceRole given");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Error: serviceRole set but SPIFFE_DIR_PROPERTY absent
    // -----------------------------------------------------------------------

    static void testMissingSystemProperty_throwsLoginException() {
        System.clearProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY);
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("serviceRole", "reggie");

        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), opts);
        try {
            module.login();
            throw new RuntimeException(
                    "FAIL: Expected LoginException when system property absent");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Error: explicit path points to a non-existent file
    // -----------------------------------------------------------------------

    static void testNonExistentSvidPem_throwsLoginException() {
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", "/nonexistent/svid.pem");
        opts.put("keyPem",  "/nonexistent/svid_key.pem");

        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), opts);
        try {
            module.login();
            throw new RuntimeException(
                    "FAIL: Expected LoginException for non-existent SVID PEM");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // logout() clears all three Subject components atomically
    // -----------------------------------------------------------------------

    static void testLogoutAfterCommitLeavesSubjectClean() throws Exception {
        Subject subject = new Subject();
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", SVID_PEM);
        opts.put("keyPem",  KEY_PEM);

        SpiffeLoginModule module = loginAndCommit(subject, opts);
        assertFalse("Subject must have principals after commit",
                subject.getPrincipals().isEmpty());
        assertFalse("Subject must have public credentials after commit",
                subject.getPublicCredentials().isEmpty());
        assertFalse("Subject must have private credentials after commit",
                subject.getPrivateCredentials().isEmpty());

        module.logout();

        assertTrue("Principals must be empty after logout",
                subject.getPrincipals().isEmpty());
        assertTrue("Public credentials must be empty after logout",
                subject.getPublicCredentials().isEmpty());
        assertTrue("Private credentials must be empty after logout",
                subject.getPrivateCredentials().isEmpty());
    }
}
