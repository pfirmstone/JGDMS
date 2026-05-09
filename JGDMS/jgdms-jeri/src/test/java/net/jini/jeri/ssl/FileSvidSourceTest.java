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

import net.jini.jeri.ssl.SpiffeCredentialManager.FileSvidSource;
import net.jini.jeri.ssl.SpiffeCredentialManager.Svid;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link SpiffeCredentialManager.FileSvidSource}.
 *
 * SVID certificates are generated at test time using
 * {@link SpiffeTestSvidFactory} — no certificate material is committed to
 * the repository.
 */
public class FileSvidSourceTest {

    private static SpiffeTestSvidFactory.Fixture fixture;

    @BeforeClass
    public static void setUpFixture() throws Exception {
        Path tempDir = Files.createTempDirectory("spiffe-test-");
        fixture = SpiffeTestSvidFactory.createReggieFixture(tempDir);
    }

    @AfterClass
    public static void tearDownFixture() throws Exception {
        if (fixture != null) {
            // Delete all files in temp dir
            Files.walk(fixture.dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
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
    // Constructor null-checks
    // -----------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void constructNullSvid() {
        new FileSvidSource(null, Paths.get("/tmp/key.pem"));
    }

    @Test(expected = NullPointerException.class)
    public void constructNullKey() {
        new FileSvidSource(Paths.get("/tmp/svid.pem"), null);
    }

    @Test(expected = NullPointerException.class)
    public void constructDirNull() {
        new FileSvidSource((Path) null);
    }

    // -----------------------------------------------------------------------
    // Single-directory convenience constructor
    // -----------------------------------------------------------------------

    @Test
    public void dirConstructorResolvesDefaultNames() throws Exception {
        FileSvidSource src = new FileSvidSource(fixture.dir);
        Svid svid = src.fetch();
        assertNotNull(svid);
        assertNotNull(svid.certPath);
        assertNotNull(svid.privateKey);
    }

    // -----------------------------------------------------------------------
    // Successful fetch() — certificate chain
    // -----------------------------------------------------------------------

    @Test
    public void fetchReturnsSvid() throws Exception {
        Svid svid = reggieSource().fetch();
        assertNotNull(svid);
        assertNotNull("certPath must not be null", svid.certPath);
        assertNotNull("privateKey must not be null", svid.privateKey);
    }

    @Test
    public void fetchCertPathContainsLeafAndCA() throws Exception {
        Svid svid = reggieSource().fetch();
        CertPath certPath = svid.certPath;
        List<? extends java.security.cert.Certificate> certs = certPath.getCertificates();
        // svid.pem contains leaf + CA (2 certificates)
        assertEquals("certPath should contain 2 certs (leaf + CA)", 2, certs.size());
    }

    @Test
    public void fetchLeafCertIsReggie() throws Exception {
        Svid svid = reggieSource().fetch();
        X509Certificate leaf = svid.leafCertificate();
        // Subject DN should be CN=Reggie
        String dn = leaf.getSubjectX500Principal().getName();
        assertTrue("Leaf cert subject should contain Reggie; got: " + dn,
                dn.contains("Reggie") || dn.contains("reggie"));
    }

    @Test
    public void fetchLeafCertHasSpiffeUri() throws Exception {
        Svid svid = reggieSource().fetch();
        X509Certificate leaf = svid.leafCertificate();
        List<SpiffePrincipal> principals = SpiffePrincipal.fromCertificate(leaf);
        assertEquals(1, principals.size());
        assertEquals(SpiffeTestSvidFactory.REGGIE_SPIFFE_ID,
                principals.get(0).getName());
    }

    @Test
    public void fetchPrivateKeyAlgorithmIsEC() throws Exception {
        Svid svid = reggieSource().fetch();
        assertEquals("EC", svid.privateKey.getAlgorithm());
    }

    // -----------------------------------------------------------------------
    // Svid.leafCertificate()
    // -----------------------------------------------------------------------

    @Test(expected = IllegalStateException.class)
    public void leafCertificateOnEmptyChainThrows() throws Exception {
        // Construct an Svid with an empty CertPath using CertificateFactory
        java.security.cert.CertificateFactory cf =
                java.security.cert.CertificateFactory.getInstance("X.509");
        CertPath empty = cf.generateCertPath(java.util.Collections.emptyList());
        new Svid(empty, reggieSource().fetch().privateKey).leafCertificate();
    }

    // -----------------------------------------------------------------------
    // Svid constructor null-checks
    // -----------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void svidNullCertPath() throws Exception {
        new Svid(null, reggieSource().fetch().privateKey);
    }

    @Test(expected = NullPointerException.class)
    public void svidNullPrivateKey() throws Exception {
        CertPath cp = reggieSource().fetch().certPath;
        new Svid(cp, null);
    }

    // -----------------------------------------------------------------------
    // Error cases — missing / malformed files
    // -----------------------------------------------------------------------

    @Test
    public void fetchMissingSvidPemThrows() throws Exception {
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

    @Test
    public void fetchMissingKeyPemThrows() throws Exception {
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

    @Test
    public void fetchEmptySvidPemThrows() throws Exception {
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

    @Test
    public void fetchUnrecognizedKeyFormatThrows() throws Exception {
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

    @Test
    public void decodePemBlockHappyPath() {
        String pem = "-----BEGIN CERTIFICATE-----\nYWJj\n-----END CERTIFICATE-----\n";
        byte[] decoded = FileSvidSource.decodePemBlock(pem,
                "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
        assertNotNull(decoded);
        // "YWJj" base64-decodes to "abc"
        assertEquals("abc", new String(decoded));
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodePemBlockMissingHeader() {
        FileSvidSource.decodePemBlock("-----END CERTIFICATE-----\n",
                "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodePemBlockMissingFooter() {
        FileSvidSource.decodePemBlock("-----BEGIN CERTIFICATE-----\nYWJj\n",
                "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
    }
}

