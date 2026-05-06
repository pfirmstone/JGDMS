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
package net.jini.jeri.ssl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.security.Principal;
import java.security.cert.CertPath;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link SpiffeLoginModule}.
 *
 * Stage 3: full JAAS login-commit-logout cycle using the test SVID
 * resources. The module is driven directly (no {@code LoginContext}) to keep
 * the tests self-contained.
 */
public class SpiffeLoginModuleTest {

    private Subject subject;
    private String savedDirProperty;

    @Before
    public void setUp() {
        subject = new Subject();
        savedDirProperty = System.getProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY);
    }

    @After
    public void tearDown() {
        // Restore the system property to avoid polluting other tests.
        if (savedDirProperty != null) {
            System.setProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY, savedDirProperty);
        } else {
            System.clearProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String resourceFilePath(String classpathResource) throws Exception {
        URI uri = SpiffeLoginModuleTest.class.getResource(classpathResource).toURI();
        return Paths.get(uri).toString();
    }

    /**
     * Runs the full login → commit cycle using the given options map.
     * Returns the module after committing so callers can invoke logout().
     */
    private SpiffeLoginModule loginAndCommit(Map<String, ?> options) throws LoginException {
        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), options);
        assertTrue("login() should return true", module.login());
        assertTrue("commit() should return true", module.commit());
        return module;
    }

    // -----------------------------------------------------------------------
    // Happy path: explicit svidPem + keyPem options
    // -----------------------------------------------------------------------

    @Test
    public void loginWithExplicitPathsPopulatesPrincipals() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", resourceFilePath("/spiffe/reggie/svid.pem"));
        opts.put("keyPem",  resourceFilePath("/spiffe/reggie/svid_key.pem"));

        loginAndCommit(opts);

        // Should have exactly one X500Principal (CN=Reggie) and one SpiffePrincipal.
        Set<X500Principal> x500s = subject.getPrincipals(X500Principal.class);
        assertEquals(1, x500s.size());
        String dn = x500s.iterator().next().getName();
        assertTrue("DN should contain Reggie: " + dn, dn.contains("Reggie"));

        Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
        assertEquals(1, spiffes.size());
        assertEquals("spiffe://test.jgdms.local/svc/reggie",
                spiffes.iterator().next().getName());
    }

    @Test
    public void loginWithExplicitPathsPopulatesPublicCredential() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", resourceFilePath("/spiffe/reggie/svid.pem"));
        opts.put("keyPem",  resourceFilePath("/spiffe/reggie/svid_key.pem"));

        loginAndCommit(opts);

        Set<CertPath> certPaths = subject.getPublicCredentials(CertPath.class);
        assertEquals("Subject should have exactly one CertPath", 1, certPaths.size());
        assertFalse("CertPath should not be empty",
                certPaths.iterator().next().getCertificates().isEmpty());
    }

    @Test
    public void loginWithExplicitPathsPopulatesPrivateCredential() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", resourceFilePath("/spiffe/reggie/svid.pem"));
        opts.put("keyPem",  resourceFilePath("/spiffe/reggie/svid_key.pem"));

        loginAndCommit(opts);

        Set<X500PrivateCredential> privs =
                subject.getPrivateCredentials(X500PrivateCredential.class);
        assertEquals("Subject should have exactly one X500PrivateCredential", 1, privs.size());
        assertNotNull(privs.iterator().next().getPrivateKey());
    }

    // -----------------------------------------------------------------------
    // Happy path: serviceRole + system property
    // -----------------------------------------------------------------------

    @Test
    public void loginWithServiceRolePopulatesSubject() throws Exception {
        // Point the system property at the directory containing the test SVID.
        String spiffeDir = resourceFilePath("/spiffe/reggie/svid.pem");
        // The directory is the parent of svid.pem, so strip the role subdir too:
        // resources/spiffe/reggie/svid.pem → resources/spiffe/
        String baseDir = Paths.get(spiffeDir).getParent().getParent().toString();
        System.setProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY, baseDir);

        Map<String, String> opts = new HashMap<>();
        opts.put("serviceRole", "reggie");

        loginAndCommit(opts);

        Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
        assertEquals(1, spiffes.size());
        assertEquals("spiffe://test.jgdms.local/svc/reggie",
                spiffes.iterator().next().getName());
    }

    // -----------------------------------------------------------------------
    // abort() prevents principals from being added
    // -----------------------------------------------------------------------

    @Test
    public void abortAfterLoginDoesNotPopulateSubject() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", resourceFilePath("/spiffe/reggie/svid.pem"));
        opts.put("keyPem",  resourceFilePath("/spiffe/reggie/svid_key.pem"));

        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), opts);
        module.login();
        module.abort();

        assertTrue("Principals should be empty after abort",
                subject.getPrincipals().isEmpty());
        assertTrue("Public credentials should be empty after abort",
                subject.getPublicCredentials().isEmpty());
        assertTrue("Private credentials should be empty after abort",
                subject.getPrivateCredentials().isEmpty());
    }

    // -----------------------------------------------------------------------
    // logout() removes principals and credentials
    // -----------------------------------------------------------------------

    @Test
    public void logoutClearsPrincipals() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", resourceFilePath("/spiffe/reggie/svid.pem"));
        opts.put("keyPem",  resourceFilePath("/spiffe/reggie/svid_key.pem"));

        SpiffeLoginModule module = loginAndCommit(opts);
        assertFalse("Subject should have principals before logout",
                subject.getPrincipals().isEmpty());

        module.logout();

        assertTrue("Principals should be empty after logout",
                subject.getPrincipals().isEmpty());
        assertTrue("Public credentials should be empty after logout",
                subject.getPublicCredentials().isEmpty());
        assertTrue("Private credentials should be empty after logout",
                subject.getPrivateCredentials().isEmpty());
    }

    // -----------------------------------------------------------------------
    // commit() on a second call after prior commit behaves cleanly
    // -----------------------------------------------------------------------

    @Test
    public void commitIdempotentPrincipalCount() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", resourceFilePath("/spiffe/reggie/svid.pem"));
        opts.put("keyPem",  resourceFilePath("/spiffe/reggie/svid_key.pem"));

        // Two separate login modules writing to the same Subject should each
        // add their own copies — the Set deduplicates equal principals.
        loginAndCommit(opts);
        int principals1 = subject.getPrincipals().size();

        // A second independent commit (fresh module, same Subject)
        SpiffeLoginModule module2 = new SpiffeLoginModule();
        module2.initialize(subject, null, new HashMap<>(), opts);
        module2.login();
        module2.commit();

        // X500Principal and SpiffePrincipal are equal by value, so the Set
        // should not grow.
        assertEquals("Principal count should not grow for identical SVIDs",
                principals1, subject.getPrincipals().size());
    }

    // -----------------------------------------------------------------------
    // Error: missing serviceRole without explicit paths
    // -----------------------------------------------------------------------

    @Test
    public void missingServiceRoleAndPaths_throwsLoginException() {
        Map<String, String> opts = new HashMap<>();
        // No serviceRole, no svidPem, no keyPem
        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), opts);
        try {
            module.login();
            fail("Expected LoginException when neither serviceRole nor explicit paths given");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Error: serviceRole set but SPIFFE_DIR_PROPERTY absent
    // -----------------------------------------------------------------------

    @Test
    public void missingSystemProperty_throwsLoginException() {
        System.clearProperty(SpiffeLoginModule.SPIFFE_DIR_PROPERTY);
        Map<String, String> opts = new HashMap<>();
        opts.put("serviceRole", "reggie");

        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), opts);
        try {
            module.login();
            fail("Expected LoginException when system property is absent");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Error: explicit path points to a non-existent file
    // -----------------------------------------------------------------------

    @Test
    public void nonExistentSvidPem_throwsLoginException() {
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", "/nonexistent/svid.pem");
        opts.put("keyPem",  "/nonexistent/svid_key.pem");

        SpiffeLoginModule module = new SpiffeLoginModule();
        module.initialize(subject, null, new HashMap<>(), opts);
        try {
            module.login();
            fail("Expected LoginException for non-existent SVID PEM");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // logout() clears credentials atomically (#2, #4)
    // -----------------------------------------------------------------------

    @Test
    public void logoutAfterCommitLeavesSubjectClean() throws Exception {
        Map<String, String> opts = new HashMap<>();
        opts.put("svidPem", resourceFilePath("/spiffe/reggie/svid.pem"));
        opts.put("keyPem",  resourceFilePath("/spiffe/reggie/svid_key.pem"));

        SpiffeLoginModule module = loginAndCommit(opts);
        // Verify the subject is populated before logout.
        assertFalse("Subject must have principals after commit",
                subject.getPrincipals().isEmpty());
        assertFalse("Subject must have public credentials after commit",
                subject.getPublicCredentials().isEmpty());
        assertFalse("Subject must have private credentials after commit",
                subject.getPrivateCredentials().isEmpty());

        module.logout();

        // After logout all three sets must be empty (#2 — svid cleared, so
        // no stale private-key reference).
        assertTrue("Principals must be empty after logout",
                subject.getPrincipals().isEmpty());
        assertTrue("Public credentials must be empty after logout",
                subject.getPublicCredentials().isEmpty());
        assertTrue("Private credentials must be empty after logout",
                subject.getPrivateCredentials().isEmpty());
    }
}
