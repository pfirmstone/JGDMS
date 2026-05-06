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
 *          login/commit/abort/logout cycle using checked-in test SVIDs.
 * @build SpiffeLoginModuleTest
 * @run main/othervm SpiffeLoginModuleTest
 */

import java.nio.file.Paths;
import java.security.cert.CertPath;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;
import net.jini.jeri.ssl.SpiffeLoginModule;
import net.jini.jeri.ssl.SpiffePrincipal;

/**
 * Integration tests for {@link SpiffeLoginModule}.
 *
 * Covers the full JAAS login / commit / abort / logout cycle using the
 * test SVIDs checked in under {@code reggie/} in this test directory.
 * Each test method is run sequentially from {@link #main(String[])};
 * failures are reported by throwing {@link RuntimeException}.
 */
public class SpiffeLoginModuleTest {

    /** jtreg populates this with the test-source directory. */
    private static final String TEST_SRC =
            System.getProperty("test.src", ".");

    /** Absolute path to the reggie SVID PEM (leaf + intermediate chain). */
    private static final String SVID_PEM =
            Paths.get(TEST_SRC, "reggie", "svid.pem").toString();

    /** Absolute path to the reggie private-key PEM (PKCS#8 format). */
    private static final String KEY_PEM =
            Paths.get(TEST_SRC, "reggie", "svid_key.pem").toString();

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        String savedProp = System.getProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY);
        try {
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
        }
        System.out.println("All SpiffeLoginModule integration tests PASSED.");
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
        assertEquals("SPIFFE URI",
                "spiffe://test.jgdms.local/svc/reggie",
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
        // Point the property at the test-source directory; the module then
        // looks for {dir}/{serviceRole}/svid.pem and svid_key.pem.
        System.setProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY, TEST_SRC);
        Map<String, String> opts = new HashMap<>();
        opts.put("serviceRole", "reggie");

        loginAndCommit(subject, opts);

        Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
        assertEquals("Should have one SpiffePrincipal", 1, spiffes.size());
        assertEquals("SPIFFE URI",
                "spiffe://test.jgdms.local/svc/reggie",
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
