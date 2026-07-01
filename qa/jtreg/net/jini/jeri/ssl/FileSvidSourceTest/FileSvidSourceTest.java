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
 * @summary Unit tests for SpiffeCredentialManager.FileSvidSource: constructor
 *          null-checks, the single-directory convenience constructor, a
 *          successful fetch() (cert chain, leaf DN, SPIFFE URI SAN, EC key),
 *          Svid.leafCertificate() and Svid constructor null-checks, error
 *          handling for missing/empty/malformed PEM files, and the
 *          decodePemBlock helper.  SVIDs are generated at test time using
 *          SpiffeTestSvidFactory (which shells out to keytool) — no
 *          certificate material is committed to the repository.
 * @build FileSvidSourceTest
 * @run main/othervm FileSvidSourceTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.util.List;

import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager.FileSvidSource;
import au.net.zeus.jgdms.spiffe.SpiffeCredentialManager.Svid;
import au.net.zeus.jgdms.spiffe.SpiffePrincipal;
import au.net.zeus.jgdms.spiffe.SpiffeTestSvidFactory;

/**
 * Unit tests for {@link au.net.zeus.jgdms.spiffe.SpiffeCredentialManager.FileSvidSource}.
 *
 * <p>SVID certificates are generated at test time using
 * {@link SpiffeTestSvidFactory} — no certificate material is committed to
 * the repository.
 */
public class FileSvidSourceTest {

    private static SpiffeTestSvidFactory.Fixture fixture;

    // -----------------------------------------------------------------------
    // Fixture setup / teardown
    // -----------------------------------------------------------------------

    private static void setUpFixture() throws Exception {
        Path tempDir = Files.createTempDirectory("spiffe-test-");
        fixture = SpiffeTestSvidFactory.createReggieFixture(tempDir);
    }

    private static void tearDownFixture() throws Exception {
        if (fixture != null) {
            // Delete all files in temp dir
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

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        try {
            setUpFixture();

            // Constructor null-checks
            run("constructNullSvid",              FileSvidSourceTest::constructNullSvid);
            run("constructNullKey",               FileSvidSourceTest::constructNullKey);
            run("constructDirNull",               FileSvidSourceTest::constructDirNull);

            // Single-directory convenience constructor
            run("dirConstructorResolvesDefaultNames",
                    FileSvidSourceTest::dirConstructorResolvesDefaultNames);

            // Successful fetch() — certificate chain
            run("fetchReturnsSvid",               FileSvidSourceTest::fetchReturnsSvid);
            run("fetchCertPathContainsLeafAndCA", FileSvidSourceTest::fetchCertPathContainsLeafAndCA);
            run("fetchLeafCertIsReggie",          FileSvidSourceTest::fetchLeafCertIsReggie);
            run("fetchLeafCertHasSpiffeUri",      FileSvidSourceTest::fetchLeafCertHasSpiffeUri);
            run("fetchPrivateKeyAlgorithmIsEC",   FileSvidSourceTest::fetchPrivateKeyAlgorithmIsEC);

            // Svid.leafCertificate()
            run("leafCertificateOnEmptyChainThrows",
                    FileSvidSourceTest::leafCertificateOnEmptyChainThrows);

            // Svid constructor null-checks
            run("svidNullCertPath",               FileSvidSourceTest::svidNullCertPath);
            run("svidNullPrivateKey",             FileSvidSourceTest::svidNullPrivateKey);

            // Error cases — missing / malformed files
            run("fetchMissingSvidPemThrows",      FileSvidSourceTest::fetchMissingSvidPemThrows);
            run("fetchMissingKeyPemThrows",       FileSvidSourceTest::fetchMissingKeyPemThrows);
            run("fetchEmptySvidPemThrows",        FileSvidSourceTest::fetchEmptySvidPemThrows);
            run("fetchUnrecognizedKeyFormatThrows",
                    FileSvidSourceTest::fetchUnrecognizedKeyFormatThrows);

            // decodePemBlock
            run("decodePemBlockHappyPath",        FileSvidSourceTest::decodePemBlockHappyPath);
            run("decodePemBlockMissingHeader",    FileSvidSourceTest::decodePemBlockMissingHeader);
            run("decodePemBlockMissingFooter",    FileSvidSourceTest::decodePemBlockMissingFooter);

            System.out.println("\nAll FileSvidSourceTest tests PASSED.");
        } finally {
            tearDownFixture();
        }
    }

    // -----------------------------------------------------------------------
    // Constructor null-checks
    // -----------------------------------------------------------------------

    private static void constructNullSvid() throws Exception {
        assertThrows("constructNullSvid", NullPointerException.class,
                () -> new FileSvidSource(null, Paths.get("/tmp/key.pem")));
    }

    private static void constructNullKey() throws Exception {
        assertThrows("constructNullKey", NullPointerException.class,
                () -> new FileSvidSource(Paths.get("/tmp/svid.pem"), null));
    }

    private static void constructDirNull() throws Exception {
        assertThrows("constructDirNull", NullPointerException.class,
                () -> new FileSvidSource((Path) null));
    }

    // -----------------------------------------------------------------------
    // Single-directory convenience constructor
    // -----------------------------------------------------------------------

    private static void dirConstructorResolvesDefaultNames() throws Exception {
        FileSvidSource src = new FileSvidSource(fixture.dir);
        Svid svid = src.fetch();
        assertNotNull("svid must not be null", svid);
        assertNotNull("certPath must not be null", svid.certPath);
        assertNotNull("privateKey must not be null", svid.privateKey);
    }

    // -----------------------------------------------------------------------
    // Successful fetch() — certificate chain
    // -----------------------------------------------------------------------

    private static void fetchReturnsSvid() throws Exception {
        Svid svid = reggieSource().fetch();
        assertNotNull("svid must not be null", svid);
        assertNotNull("certPath must not be null", svid.certPath);
        assertNotNull("privateKey must not be null", svid.privateKey);
    }

    private static void fetchCertPathContainsLeafAndCA() throws Exception {
        Svid svid = reggieSource().fetch();
        CertPath certPath = svid.certPath;
        List<? extends java.security.cert.Certificate> certs = certPath.getCertificates();
        // svid.pem contains leaf + CA (2 certificates)
        assertEqual("certPath should contain 2 certs (leaf + CA)", 2, certs.size());
    }

    private static void fetchLeafCertIsReggie() throws Exception {
        Svid svid = reggieSource().fetch();
        X509Certificate leaf = svid.leafCertificate();
        // Subject DN should be CN=Reggie
        String dn = leaf.getSubjectX500Principal().getName();
        assertTrue("Leaf cert subject should contain Reggie; got: " + dn,
                dn.contains("Reggie") || dn.contains("reggie"));
    }

    private static void fetchLeafCertHasSpiffeUri() throws Exception {
        Svid svid = reggieSource().fetch();
        X509Certificate leaf = svid.leafCertificate();
        List<SpiffePrincipal> principals = SpiffePrincipal.fromCertificate(leaf);
        assertEqual("exactly one SpiffePrincipal on the leaf", 1, principals.size());
        assertEqual("SPIFFE URI", SpiffeTestSvidFactory.REGGIE_SPIFFE_ID,
                principals.get(0).getName());
    }

    private static void fetchPrivateKeyAlgorithmIsEC() throws Exception {
        Svid svid = reggieSource().fetch();
        assertEqual("private key algorithm", "EC", svid.privateKey.getAlgorithm());
    }

    // -----------------------------------------------------------------------
    // Svid.leafCertificate()
    // -----------------------------------------------------------------------

    private static void leafCertificateOnEmptyChainThrows() throws Exception {
        assertThrows("leafCertificateOnEmptyChainThrows", IllegalStateException.class,
                () -> {
                    // Construct an Svid with an empty CertPath using CertificateFactory
                    java.security.cert.CertificateFactory cf =
                            java.security.cert.CertificateFactory.getInstance("X.509");
                    CertPath empty = cf.generateCertPath(java.util.Collections.emptyList());
                    new Svid(empty, reggieSource().fetch().privateKey).leafCertificate();
                });
    }

    // -----------------------------------------------------------------------
    // Svid constructor null-checks
    // -----------------------------------------------------------------------

    private static void svidNullCertPath() throws Exception {
        assertThrows("svidNullCertPath", NullPointerException.class,
                () -> new Svid(null, reggieSource().fetch().privateKey));
    }

    private static void svidNullPrivateKey() throws Exception {
        assertThrows("svidNullPrivateKey", NullPointerException.class,
                () -> {
                    CertPath cp = reggieSource().fetch().certPath;
                    new Svid(cp, null);
                });
    }

    // -----------------------------------------------------------------------
    // Error cases — missing / malformed files
    // -----------------------------------------------------------------------

    private static void fetchMissingSvidPemThrows() throws Exception {
        FileSvidSource src = new FileSvidSource(
                Paths.get("/nonexistent/svid.pem"),
                fixture.svidKeyPem);
        try {
            src.fetch();
            fail("Expected IOException for missing SVID PEM");
        } catch (IOException e) {
            // expected
        }
    }

    private static void fetchMissingKeyPemThrows() throws Exception {
        FileSvidSource src = new FileSvidSource(
                fixture.svidPem,
                Paths.get("/nonexistent/svid_key.pem"));
        try {
            src.fetch();
            fail("Expected IOException for missing key PEM");
        } catch (IOException e) {
            // expected
        }
    }

    private static void fetchEmptySvidPemThrows() throws Exception {
        Path emptyPem = Files.createTempFile("svid-empty", ".pem");
        try {
            FileSvidSource src = new FileSvidSource(
                    emptyPem,
                    fixture.svidKeyPem);
            src.fetch();
            fail("Expected IOException for empty SVID PEM");
        } catch (IOException e) {
            // expected
        } finally {
            Files.deleteIfExists(emptyPem);
        }
    }

    private static void fetchUnrecognizedKeyFormatThrows() throws Exception {
        Path badKey = Files.createTempFile("bad-key", ".pem");
        try {
            Files.write(badKey, "-----BEGIN SOMETHING-----\naGVsbG8=\n-----END SOMETHING-----\n"
                    .getBytes("UTF-8"));
            FileSvidSource src = new FileSvidSource(
                    fixture.svidPem,
                    badKey);
            src.fetch();
            fail("Expected IOException for unrecognised key PEM");
        } catch (IOException e) {
            // expected
        } finally {
            Files.deleteIfExists(badKey);
        }
    }

    // -----------------------------------------------------------------------
    // decodePemBlock (package-accessible static helper)
    // -----------------------------------------------------------------------

    private static void decodePemBlockHappyPath() throws Exception {
        String pem = "-----BEGIN CERTIFICATE-----\nYWJj\n-----END CERTIFICATE-----\n";
        byte[] decoded = decodePemBlock(pem,
                "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
        assertNotNull("decoded bytes must not be null", decoded);
        // "YWJj" base64-decodes to "abc"
        assertEqual("decoded content", "abc", new String(decoded));
    }

    private static void decodePemBlockMissingHeader() throws Exception {
        assertThrows("decodePemBlockMissingHeader", IllegalArgumentException.class,
                () -> decodePemBlock("-----END CERTIFICATE-----\n",
                        "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----"));
    }

    private static void decodePemBlockMissingFooter() throws Exception {
        assertThrows("decodePemBlockMissingFooter", IllegalArgumentException.class,
                () -> decodePemBlock("-----BEGIN CERTIFICATE-----\nYWJj\n",
                        "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----"));
    }

    /**
     * Invokes {@code FileSvidSource.decodePemBlock}, which is a package-private
     * static helper in {@code au.net.zeus.jgdms.spiffe}.  This jtreg test lives
     * in the default package and cannot call it by package access, so reflect
     * on it; the reflected member resolves at runtime from jgdms-jeri.jar on the
     * classpath.  {@link java.lang.reflect.InvocationTargetException} is
     * unwrapped so that a thrown {@link IllegalArgumentException} propagates
     * unchanged.
     */
    private static byte[] decodePemBlock(String pem, String header, String footer)
            throws Exception {
        java.lang.reflect.Method method = FileSvidSource.class.getDeclaredMethod(
                "decodePemBlock", String.class, String.class, String.class);
        method.setAccessible(true);
        try {
            return (byte[]) method.invoke(null, pem, header, footer);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
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
