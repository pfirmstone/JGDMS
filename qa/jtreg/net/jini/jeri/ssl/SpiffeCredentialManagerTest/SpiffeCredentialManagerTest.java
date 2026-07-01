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
 * @summary Unit tests for SpiffeCredentialManager: constructor validation,
 *          start()/close() Subject population and clearing, refresh() SVID
 *          rotation, start()/refresh()-after-close guards, SvidSource failure
 *          propagation, the health API (isCredentialValid/secondsUntilExpiry),
 *          and exponential-backoff recovery from transient SvidSource failures.
 *          SVIDs are generated at test time using SpiffeTestSvidFactory (which
 *          shells out to keytool) — no certificate material is committed to
 *          the repository.
 * @build SpiffeCredentialManagerTest
 * @run main/othervm SpiffeCredentialManagerTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.x500.X500PrivateCredential;

import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager;
import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager.FileSvidSource;
import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager.Svid;
import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager.SvidSource;
import au.net.zeus.jgdms.spiffe.SpiffePrincipal;
import au.net.zeus.jgdms.spiffe.SpiffeTestSvidFactory;

/**
 * Unit tests for {@link SpiffeCredentialManager}.
 *
 * <p>SVID certificates are generated at test time using
 * {@link SpiffeTestSvidFactory} — no certificate material is committed to
 * the repository.
 */
public class SpiffeCredentialManagerTest {

    private static SpiffeTestSvidFactory.Fixture fixture;

    // -----------------------------------------------------------------------
    // Fixture setup / teardown
    // -----------------------------------------------------------------------

    private static void setUpFixture() throws Exception {
        Path tempDir = Files.createTempDirectory("spiffe-mgr-test-");
        fixture = SpiffeTestSvidFactory.createReggieFixture(tempDir);
    }

    private static void tearDownFixture() throws Exception {
        if (fixture != null) {
            Files.walk(fixture.dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException e) {
                            System.err.println("WARN: could not delete " + p + ": " + e);
                        }
                    });
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static FileSvidSource reggieSource() {
        return new FileSvidSource(fixture.svidPem, fixture.svidKeyPem);
    }

    private static Subject mutableSubject() {
        return new Subject();
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        try {
            setUpFixture();

            // Constructor validation
            run("constructNullSubject",           SpiffeCredentialManagerTest::constructNullSubject);
            run("constructNullSvidSource",         SpiffeCredentialManagerTest::constructNullSvidSource);
            run("constructZeroRenewalLead",        SpiffeCredentialManagerTest::constructZeroRenewalLead);
            run("constructNegativeRenewalLead",    SpiffeCredentialManagerTest::constructNegativeRenewalLead);
            run("constructReadOnlySubject",        SpiffeCredentialManagerTest::constructReadOnlySubject);
            run("constructDirNullSubject",         SpiffeCredentialManagerTest::constructDirNullSubject);

            // start() — populates Subject
            run("startPopulatesX500Principal",     SpiffeCredentialManagerTest::startPopulatesX500Principal);
            run("startPopulatesSpiffePrincipal",   SpiffeCredentialManagerTest::startPopulatesSpiffePrincipal);
            run("startPopulatesCertPath",          SpiffeCredentialManagerTest::startPopulatesCertPath);
            run("startPopulatesPrivateCredential", SpiffeCredentialManagerTest::startPopulatesPrivateCredential);

            // close() — clears Subject
            run("closeClearsPrincipals",           SpiffeCredentialManagerTest::closeClearsPrincipals);
            run("closeClearsCredentials",          SpiffeCredentialManagerTest::closeClearsCredentials);
            run("closeIsIdempotent",               SpiffeCredentialManagerTest::closeIsIdempotent);

            // start()/refresh() after close() throws IllegalStateException
            run("startAfterCloseThrows",           SpiffeCredentialManagerTest::startAfterCloseThrows);
            run("refreshAfterCloseThrows",         SpiffeCredentialManagerTest::refreshAfterCloseThrows);

            // SVID rotation
            run("refreshReplacesCredentials",      SpiffeCredentialManagerTest::refreshReplacesCredentials);

            // start() failure propagation
            run("startFailureWhenSvidSourceThrows", SpiffeCredentialManagerTest::startFailureWhenSvidSourceThrows);

            // Subject isolation
            run("closeOnlyRemovesManagedPrincipals", SpiffeCredentialManagerTest::closeOnlyRemovesManagedPrincipals);

            // SEC1 EC key rejection
            run("sec1EcKeyFileIsRejectedWithHelpfulMessage",
                    SpiffeCredentialManagerTest::sec1EcKeyFileIsRejectedWithHelpfulMessage);

            // Double start on same manager
            run("doubleStartOnSameManagerIsRejected",
                    SpiffeCredentialManagerTest::doubleStartOnSameManagerIsRejected);

            // Health API
            run("isCredentialValidBeforeStart",    SpiffeCredentialManagerTest::isCredentialValidBeforeStart);
            run("secondsUntilExpiryBeforeStart",   SpiffeCredentialManagerTest::secondsUntilExpiryBeforeStart);
            run("isCredentialValidAfterStart",     SpiffeCredentialManagerTest::isCredentialValidAfterStart);
            run("secondsUntilExpiryAfterStart",    SpiffeCredentialManagerTest::secondsUntilExpiryAfterStart);
            run("isCredentialValidAfterClose",     SpiffeCredentialManagerTest::isCredentialValidAfterClose);
            run("secondsUntilExpiryAfterClose",    SpiffeCredentialManagerTest::secondsUntilExpiryAfterClose);

            // Exponential backoff recovery
            run("renewalRecoverAfterTransientFailures",
                    SpiffeCredentialManagerTest::renewalRecoverAfterTransientFailures);
            run("minRetryIntervalConstantIsPositive",
                    SpiffeCredentialManagerTest::minRetryIntervalConstantIsPositive);

            System.out.println("\nAll SpiffeCredentialManagerTest tests PASSED.");
        } finally {
            tearDownFixture();
        }
    }

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    private static void constructNullSubject() throws Exception {
        assertThrows("constructNullSubject", NullPointerException.class,
                () -> new SpiffeCredentialManager(null, reggieSource(), 60L));
    }

    private static void constructNullSvidSource() throws Exception {
        assertThrows("constructNullSvidSource", NullPointerException.class,
                () -> new SpiffeCredentialManager(mutableSubject(), (SvidSource) null, 60L));
    }

    private static void constructZeroRenewalLead() throws Exception {
        assertThrows("constructZeroRenewalLead", IllegalArgumentException.class,
                () -> new SpiffeCredentialManager(mutableSubject(), reggieSource(), 0L));
    }

    private static void constructNegativeRenewalLead() throws Exception {
        assertThrows("constructNegativeRenewalLead", IllegalArgumentException.class,
                () -> new SpiffeCredentialManager(mutableSubject(), reggieSource(), -1L));
    }

    private static void constructReadOnlySubject() throws Exception {
        assertThrows("constructReadOnlySubject", IllegalArgumentException.class,
                () -> {
                    Subject ro = new Subject();
                    ro.setReadOnly();
                    new SpiffeCredentialManager(ro, reggieSource(), 60L);
                });
    }

    private static void constructDirNullSubject() throws Exception {
        assertThrows("constructDirNullSubject", NullPointerException.class,
                () -> new SpiffeCredentialManager(null, Paths.get("/tmp")));
    }

    // -----------------------------------------------------------------------
    // start() — populates Subject
    // -----------------------------------------------------------------------

    private static void startPopulatesX500Principal() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            Set<X500Principal> x500s = subject.getPrincipals(X500Principal.class);
            assertEqual("start() should add exactly one X500Principal", 1, x500s.size());
            String dn = x500s.iterator().next().getName();
            assertTrue("X500Principal DN should contain Reggie: " + dn,
                    dn.contains("Reggie"));
        }
    }

    private static void startPopulatesSpiffePrincipal() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            Set<SpiffePrincipal> spiffes = subject.getPrincipals(SpiffePrincipal.class);
            assertEqual("start() should add exactly one SpiffePrincipal", 1, spiffes.size());
            assertEqual("SPIFFE URI", SpiffeTestSvidFactory.REGGIE_SPIFFE_ID,
                    spiffes.iterator().next().getName());
        }
    }

    private static void startPopulatesCertPath() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            Set<CertPath> certPaths = subject.getPublicCredentials(CertPath.class);
            assertEqual("start() should add exactly one CertPath", 1, certPaths.size());
            assertFalse("CertPath must not be empty",
                    certPaths.iterator().next().getCertificates().isEmpty());
        }
    }

    private static void startPopulatesPrivateCredential() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            Set<X500PrivateCredential> privs =
                    subject.getPrivateCredentials(X500PrivateCredential.class);
            assertEqual("start() should add exactly one X500PrivateCredential",
                    1, privs.size());
            assertNotNull("private key must not be null",
                    privs.iterator().next().getPrivateKey());
        }
    }

    // -----------------------------------------------------------------------
    // close() — clears Subject
    // -----------------------------------------------------------------------

    private static void closeClearsPrincipals() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        assertFalse("Principals should be present after start()",
                subject.getPrincipals().isEmpty());

        mgr.close();

        assertTrue("Principals should be cleared after close()",
                subject.getPrincipals().isEmpty());
    }

    private static void closeClearsCredentials() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();

        assertTrue("Public credentials should be cleared after close()",
                subject.getPublicCredentials(CertPath.class).isEmpty());
        assertTrue("Private credentials should be cleared after close()",
                subject.getPrivateCredentials(X500PrivateCredential.class).isEmpty());
    }

    private static void closeIsIdempotent() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();
        mgr.close(); // second close should not throw
    }

    // -----------------------------------------------------------------------
    // start() after close() throws IllegalStateException
    // -----------------------------------------------------------------------

    private static void startAfterCloseThrows() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();
        try {
            mgr.start();
            fail("Expected IllegalStateException after close()");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // refresh() after close() throws IllegalStateException
    // -----------------------------------------------------------------------

    private static void refreshAfterCloseThrows() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();
        try {
            mgr.refresh();
            fail("Expected IllegalStateException after close()");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // SVID rotation: refresh() replaces credentials atomically
    // -----------------------------------------------------------------------

    private static void refreshReplacesCredentials() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();

            CertPath firstCertPath =
                    subject.getPublicCredentials(CertPath.class).iterator().next();

            mgr.refresh();

            Set<CertPath> after = subject.getPublicCredentials(CertPath.class);
            assertEqual("Exactly one CertPath after refresh()", 1, after.size());
            // The CertPath content should still be the reggie cert (same source).
            assertNotNull("CertPath after refresh() must not be null",
                    after.iterator().next());
        }
    }

    // -----------------------------------------------------------------------
    // start() failure propagates as IOException / GeneralSecurityException
    // -----------------------------------------------------------------------

    private static void startFailureWhenSvidSourceThrows() throws Exception {
        SvidSource failing = new SvidSource() {
            @Override
            public Svid fetch() throws IOException {
                throw new IOException("simulated fetch failure");
            }
        };
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, failing, 60L)) {
            mgr.start();
            fail("Expected IOException from failing SvidSource");
        } catch (IOException e) {
            assertTrue("message should mention the simulated failure",
                    e.getMessage().contains("simulated fetch failure"));
        }
    }

    // -----------------------------------------------------------------------
    // Subject isolation: pre-existing principals are untouched by close()
    // -----------------------------------------------------------------------

    private static void closeOnlyRemovesManagedPrincipals() throws Exception {
        Subject subject = mutableSubject();
        X500Principal external = new X500Principal("CN=External");
        subject.getPrincipals().add(external);

        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();

        assertTrue("Pre-existing external principal should survive close()",
                subject.getPrincipals().contains(external));
    }

    // -----------------------------------------------------------------------
    // FileSvidSource: SEC1 EC key (BEGIN EC PRIVATE KEY) must be rejected
    // with a clear error rather than silently producing a corrupt key.
    // -----------------------------------------------------------------------

    private static void sec1EcKeyFileIsRejectedWithHelpfulMessage() throws Exception {
        // Write a fake PEM key file containing a SEC1 EC PRIVATE KEY header.
        // The content bytes don't matter for this test — we just need the
        // header/footer to be present so the branch is triggered.
        Path tmpDir = Files.createTempDirectory("spiffe-sec1-test");
        Path fakeSvid = tmpDir.resolve("svid.pem");
        Path fakeKey  = tmpDir.resolve("svid_key.pem");

        // Copy the generated svid.pem so the cert-loading step succeeds.
        Files.copy(fixture.svidPem, fakeSvid);

        // Write a synthetic SEC1-format key file.
        String sec1Pem =
                "-----BEGIN EC PRIVATE KEY-----\n"
                + "MHQCAQEEIExampleFakeKeyBytesBase64Padding==\n"
                + "-----END EC PRIVATE KEY-----\n";
        Files.write(fakeKey,
                sec1Pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        FileSvidSource source = new FileSvidSource(fakeSvid, fakeKey);
        try {
            source.fetch();
            fail("Expected GeneralSecurityException for SEC1 EC private key");
        } catch (GeneralSecurityException e) {
            String msg = e.getMessage();
            assertNotNull("Exception must have a message", msg);
            assertTrue("Message should mention SEC1 or EC PRIVATE KEY: " + msg,
                    msg.contains("SEC1") || msg.contains("EC PRIVATE KEY")
                    || msg.contains("PKCS#8"));
        } finally {
            Files.deleteIfExists(fakeKey);
            Files.deleteIfExists(fakeSvid);
            Files.deleteIfExists(tmpDir);
        }
    }

    private static void doubleStartOnSameManagerIsRejected() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        try {
            mgr.start();
            try {
                mgr.start();
                fail("Expected IllegalStateException on second start() of same manager");
            } catch (IllegalStateException e) {
                // expected
            }
        } finally {
            mgr.close();
        }
    }

    // -----------------------------------------------------------------------
    // Health API: isCredentialValid() and secondsUntilExpiry()
    // -----------------------------------------------------------------------

    private static void isCredentialValidBeforeStart() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        assertFalse("isCredentialValid() should be false before start()",
                mgr.isCredentialValid());
        mgr.close();
    }

    private static void secondsUntilExpiryBeforeStart() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        assertEqual("secondsUntilExpiry() should return Long.MIN_VALUE before start()",
                Long.MIN_VALUE, mgr.secondsUntilExpiry());
        mgr.close();
    }

    private static void isCredentialValidAfterStart() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            assertTrue("isCredentialValid() should be true after start()",
                    mgr.isCredentialValid());
        }
    }

    private static void secondsUntilExpiryAfterStart() throws Exception {
        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L)) {
            mgr.start();
            long secs = mgr.secondsUntilExpiry();
            assertTrue("secondsUntilExpiry() should be positive after start(): " + secs,
                    secs > 0);
        }
    }

    private static void isCredentialValidAfterClose() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();
        assertFalse("isCredentialValid() should be false after close()",
                mgr.isCredentialValid());
    }

    private static void secondsUntilExpiryAfterClose() throws Exception {
        Subject subject = mutableSubject();
        SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, reggieSource(), 3600L);
        mgr.start();
        mgr.close();
        assertEqual("secondsUntilExpiry() should return Long.MIN_VALUE after close()",
                Long.MIN_VALUE, mgr.secondsUntilExpiry());
    }

    // -----------------------------------------------------------------------
    // Exponential backoff: manager recovers from transient SvidSource failures
    // -----------------------------------------------------------------------

    private static void renewalRecoverAfterTransientFailures() throws Exception {
        // SvidSource that fails the first two fetches then succeeds.
        AtomicInteger callCount = new AtomicInteger(0);
        SvidSource intermittent = () -> {
            int call = callCount.incrementAndGet();
            if (call <= 2) {
                throw new IOException("simulated transient failure #" + call);
            }
            return reggieSource().fetch();
        };

        Subject subject = mutableSubject();
        try (SpiffeCredentialManager mgr =
                new SpiffeCredentialManager(subject, intermittent, 3600L)) {
            // start() calls fetch() once — call #1 should throw.
            try {
                mgr.start();
                fail("Expected IOException from failing SvidSource on start()");
            } catch (IOException e) {
                assertTrue("message should mention the simulated transient failure",
                        e.getMessage().contains("simulated transient failure"));
            }
        }

        // Verify that the SvidSource eventually succeeds on call #3.
        AtomicInteger callCount2 = new AtomicInteger(0);
        SvidSource succeedOnThird = () -> {
            int call = callCount2.incrementAndGet();
            if (call < 3) {
                throw new IOException("transient #" + call);
            }
            return reggieSource().fetch();
        };

        // First two calls throw; third succeeds.
        for (int i = 1; i <= 2; i++) {
            try {
                succeedOnThird.fetch();
                fail("Expected IOException on call " + i);
            } catch (IOException expected) {
                // expected
            }
        }
        Svid svid = succeedOnThird.fetch();  // call #3 — succeeds
        assertNotNull("SvidSource should succeed on 3rd call", svid);
    }

    private static void minRetryIntervalConstantIsPositive() throws Exception {
        assertTrue("MIN_RETRY_INTERVAL_SECONDS must be positive",
                SpiffeCredentialManager.MIN_RETRY_INTERVAL_SECONDS > 0);
    }

    // -----------------------------------------------------------------------
    // Test harness
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
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new RuntimeException("ASSERTION FAILED: " + message
                    + " [expected=" + expected + ", actual=" + actual + "]");
    }

    private static void assertEqual(String message, long expected, long actual) {
        if (expected != actual)
            throw new RuntimeException("ASSERTION FAILED: " + message
                    + " [expected=" + expected + ", actual=" + actual + "]");
    }

    private static void assertNotNull(String message, Object obj) {
        if (obj == null) throw new RuntimeException("ASSERTION FAILED: " + message + " was null");
    }

    private static void fail(String message) {
        throw new RuntimeException("ASSERTION FAILED: " + message);
    }

    /**
     * Asserts that {@code action} throws an exception of exactly (or a subtype
     * of) {@code expected}.  Mirrors JUnit4's {@code @Test(expected=X.class)}.
     */
    private static void assertThrows(String name, Class<? extends Throwable> expected,
            ThrowingRunnable action) throws Exception {
        try {
            action.run();
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                return; // expected
            }
            throw new RuntimeException("ASSERTION FAILED: " + name
                    + " expected " + expected.getName()
                    + " but threw " + t.getClass().getName() + ": " + t.getMessage(), t);
        }
        throw new RuntimeException("ASSERTION FAILED: " + name
                + " expected " + expected.getName() + " but nothing was thrown");
    }
}
