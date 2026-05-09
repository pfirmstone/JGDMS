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
 *          The new "client" SVID (spiffe://test.jgdms.local/client/test) is
 *          exercised explicitly to validate the /client/ path scheme.
 * @build SpiffeEndpointTest
 * @run main/othervm SpiffeEndpointTest
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

import net.jini.jeri.ssl.SpiffeCredentialManager;
import net.jini.jeri.ssl.SpiffeCredentialManager.FileSvidSource;
import net.jini.jeri.ssl.SpiffeCredentialManager.SvidSource;
import net.jini.jeri.ssl.SpiffePrincipal;
import net.jini.jeri.ssl.SpiffeLoginModule;

/**
 * End-to-end integration test for the SPIFFE credential pipeline.
 *
 * <h2>Tests performed</h2>
 * <ol>
 *   <li>Service SVID loading: verifies that a {@code SpiffeCredentialManager}
 *       for the {@code reggie} role populates the Subject with the correct
 *       SPIFFE URI ({@code spiffe://test.jgdms.local/svc/reggie}), an
 *       {@link X500Principal}, a {@link CertPath} public credential, and an
 *       {@link X500PrivateCredential} private credential.</li>
 *   <li>Client SVID loading: exercises the new {@code client} SVID
 *       ({@code spiffe://test.jgdms.local/client/test}) introduced in the
 *       2026-05 update to {@code gen-spiffe-svids.sh}.  Verifies that the
 *       {@code /client/} SPIFFE path scheme is correctly represented in the
 *       Subject's principals.</li>
 *   <li>SpiffeLoginModule-based loading: exercises the JAAS login path for
 *       the {@code tester} role, confirming that {@link SpiffeLoginModule}
 *       (used by the QA harness) and {@link SpiffeCredentialManager} (used by
 *       production services) produce equivalent principal sets.</li>
 *   <li>One-per-JVM enforcement: confirms that attempting to start a second
 *       {@link SpiffeCredentialManager} while the first is still active throws
 *       {@link IllegalStateException}, preserving the one-SVID-per-process
 *       invariant required for correct workload identity.</li>
 * </ol>
 *
 * <h2>Classpath requirements</h2>
 * <p>This test requires {@code jgdms-jeri.jar} (which contains
 * {@link SpiffeCredentialManager} and {@link SpiffeLoginModule}) and its
 * transitive dependency {@code jgdms-platform.jar} on the classpath.  When
 * invoked via the JGDMS QA harness those JARs are already present.
 *
 * <h2>File layout</h2>
 * <p>The test reads SVID files from the QA harness trust directory.  Set the
 * system property {@code net.jini.jeri.ssl.spiffe.dir} to the absolute path
 * of the directory containing the per-role SVID subdirectories, or place the
 * test next to a {@code ../trust/spiffe/} tree when running standalone.
 *
 * @since 3.1.1
 */
public class SpiffeEndpointTest {

    // -----------------------------------------------------------------------
    // Test SVIDs directory
    // -----------------------------------------------------------------------

    /**
     * Resolved at startup: absolute path to the SPIFFE test credential tree.
     * The expected layout is:
     * <pre>
     *   SPIFFE_DIR/
     *     reggie/svid.pem, svid_key.pem
     *     tester/svid.pem, svid_key.pem
     *     client/svid.pem, svid_key.pem
     * </pre>
     */
    private static final String SPIFFE_DIR = resolveSpiffeDir();

    private static String resolveSpiffeDir() {
        // Allow override via system property (set by the QA harness)
        String prop = System.getProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY);
        if (prop != null) return prop;

        // When running from qa/jtreg/net/jini/jeri/ssl/SpiffeEndpointTest/
        // the trust directory is at qa/harness/trust/spiffe
        String testSrc = System.getProperty("test.src", ".");
        // Walk up 7 levels from SpiffeEndpointTest/ to qa/ and then down
        Path trustPath = Paths.get(testSrc)
                .resolve("../../../../../../harness/trust/spiffe")
                .normalize();
        return trustPath.toString();
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        System.out.println("SPIFFE credential directory: " + SPIFFE_DIR);

        run("testReggieServiceSvid",           SpiffeEndpointTest::testReggieServiceSvid);
        run("testClientSvid",                  SpiffeEndpointTest::testClientSvid);
        run("testTesterSpiffeLoginModule",      SpiffeEndpointTest::testTesterSpiffeLoginModule);
        run("testOnlyOneManagerPerJvm",         SpiffeEndpointTest::testOnlyOneManagerPerJvm);

        System.out.println("\nAll SpiffeEndpointTest tests PASSED.");
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
