/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.api.security;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Arrays;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import tests.support.DigestCodeSources;

/**
 * Signer handling of {@link CertificateGrant}, {@link URIGrant} and 
 * {@link DigestGrant}, and digest location independence.  The results must
 * be the same on DirtyChai and on a stock JDK, except that a stock JDK has no
 * {@code java.security.DigestCodeSource}, so a DigestGrant implies nothing
 * there, it must never throw.
 */
public class GrantSignerTest {

    private static final Principal[] NONE = new Principal[0];
    private static final Permission PERM = new RuntimePermission("grant.signer.test");
    private static final byte[] DIGEST_A = {1, 2, 3, 4};
    private static final byte[] DIGEST_B = {9, 8, 7, 6};
    private static final String ALG = "SHA-256";

    private Certificate alice;
    private Certificate bob;
    private URL location;
    private URL otherLocation;

    @Before
    public void setUp() throws Exception {
        alice = new FakeCertificate("alice");
        bob = new FakeCertificate("bob");
        location = new URL("http://localhost/a.jar");
        otherLocation = new URL("http://localhost/b.jar");
    }

    private PermissionGrant signedBy(Certificate... certs) {
        return PermissionGrantBuilder.newBuilder()
                .certificates(certs, new String[]{"alias"})
                .permissions(new Permission[]{PERM})
                .principals(NONE)
                .context(PermissionGrantBuilder.CODESOURCE_CERTS)
                .build();
    }

    private PermissionGrant codeBaseSignedBy(String uri, Certificate... certs) {
        PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder()
                .permissions(new Permission[]{PERM})
                .principals(NONE)
                .context(PermissionGrantBuilder.URI);
        if (uri != null) b.uri(uri);
        if (certs.length > 0) b.certificates(certs, new String[]{"alias"});
        return b.build();
    }

    private PermissionGrant digest(byte[] digest, Certificate... certs) {
        PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder()
                .permissions(new Permission[]{PERM})
                .principals(NONE)
                .digest(ALG, digest)
                .context(PermissionGrantBuilder.DIGEST);
        if (certs.length > 0) b.certificates(certs, new String[]{"alias"});
        return b.build();
    }

    // ---- T1: signedBy ------------------------------------------------------

    @Test
    public void signedByRequiresEverySigner() {
        PermissionGrant g = signedBy(alice, bob);
        assertTrue(g.implies(new CodeSource(location, new Certificate[]{alice, bob}), NONE));
        assertTrue(g.implies(new CodeSource(location, new Certificate[]{bob, alice}), NONE));
        assertFalse("one signer of two", 
                g.implies(new CodeSource(location, new Certificate[]{alice}), NONE));
        assertFalse("other signer", 
                g.implies(new CodeSource(location, new Certificate[]{bob}), NONE));
    }

    @Test
    public void signedByUnsignedAndNullCodeSourceNotImplied() {
        PermissionGrant g = signedBy(alice);
        // Used to throw NullPointerException from Arrays.asList(null).
        assertFalse(g.implies(new CodeSource(location, (Certificate[]) null), NONE));
        assertFalse(g.implies((CodeSource) null, NONE));
    }

    @Test
    public void noSignersRequiredImpliesAnyNonNullCodeSource() {
        PermissionGrant g = signedBy();
        assertTrue(g.implies(new CodeSource(location, (Certificate[]) null), NONE));
        assertTrue(g.implies(new CodeSource(location, new Certificate[]{bob}), NONE));
        assertFalse(g.implies((CodeSource) null, NONE));
    }

    @Test
    public void signedByNeverImpliedByClassLoaderAlone() {
        assertFalse(signedBy(alice).implies(getClass().getClassLoader(), NONE));
        assertFalse(signedBy(alice).implies((ClassLoader) null, NONE));
    }

    @Test
    public void signedByProtectionDomain() {
        PermissionGrant g = signedBy(alice);
        assertTrue(g.implies(domain(new CodeSource(location, new Certificate[]{alice}))));
        assertFalse(g.implies(domain(new CodeSource(location, new Certificate[]{bob}))));
        assertFalse(g.implies(domain(new CodeSource(location, (Certificate[]) null))));
    }

    // ---- codeBase + signedBy ----------------------------------------------

    @Test
    public void codeBaseWithSignedByEnforcesSigner() {
        PermissionGrant g = codeBaseSignedBy("http://localhost/a.jar", alice);
        assertTrue(g.implies(new CodeSource(location, new Certificate[]{alice}), NONE));
        assertFalse("right location, unsigned", 
                g.implies(new CodeSource(location, (Certificate[]) null), NONE));
        assertFalse("right location, other signer", 
                g.implies(new CodeSource(location, new Certificate[]{bob}), NONE));
        assertFalse("right signer, other location", 
                g.implies(new CodeSource(otherLocation, new Certificate[]{alice}), NONE));
        assertFalse(g.implies((CodeSource) null, NONE));
    }

    @Test
    public void codeBaseWithoutSignerIgnoresSigners() {
        PermissionGrant g = codeBaseSignedBy("http://localhost/a.jar");
        assertTrue(g.implies(new CodeSource(location, (Certificate[]) null), NONE));
        assertTrue(g.implies(new CodeSource(location, new Certificate[]{alice}), NONE));
        assertFalse(g.implies(new CodeSource(otherLocation, (Certificate[]) null), NONE));
    }

    @Test
    public void codeBaseWithSignedByNotImpliedByClassLoader() {
        assertFalse(codeBaseSignedBy("http://localhost/a.jar", alice)
                .implies(getClass().getClassLoader(), NONE));
        // Signer free, null location: a ClassLoader is implied.
        assertTrue(codeBaseSignedBy(null).implies(getClass().getClassLoader(), NONE));
    }

    @Test(expected = SecurityException.class)
    public void unparseableGrantUriFailsClosed() {
        // Silently skipping the only URI would apply the grant to every CodeSource.
        codeBaseSignedBy("http://[bad/a.jar");
    }

    // ---- digest + signedBy -------------------------------------------------

    @Test
    public void digestNeverThrowsOnPlainCodeSource() {
        PermissionGrant g = digest(DIGEST_A, alice);
        assertFalse(g.implies(new CodeSource(location, new Certificate[]{alice}), NONE));
        assertFalse(g.implies((CodeSource) null, NONE));
        assertFalse(g.implies(getClass().getClassLoader(), NONE));
    }

    // ---- T2: digest, location independent ---------------------------------

    @Test
    public void digestIgnoresLocationAndRequiresDigestAndSigners() throws Exception {
        Class<?> dcs = digestCodeSourceClass();
        Assume.assumeNotNull(dcs); // A JDK without the DigestCodeSource needed: fail closed, covered above.
        // Real jars, a digest the code source computed from the jar is what
        // a loader makes: a digest asserted by a caller is not identity.
        File dir = Files.createTempDirectory("digest-grant").toFile();
        try {
            File a1 = jar(dir, "a.jar", "a");
            File a2 = jar(dir, "moved.jar", "a");   // same content, other location
            File other = jar(dir, "other.jar", "b"); // other content
            CodeSource same = computed(dcs, a1, alice);
            byte[] digestA = (byte[]) dcs.getMethod("getDigest").invoke(same);
            PermissionGrant g = digest(digestA, alice);
            assertTrue(g.implies(same, NONE));
            assertTrue("same digest at another location", g.implies(computed(dcs, a2, alice), NONE));
            assertFalse("other digest", g.implies(computed(dcs, other, alice), NONE));
            assertFalse("other signer", g.implies(computed(dcs, a1, bob), NONE));
            assertFalse("unsigned", g.implies(computed(dcs, a1), NONE));
            assertFalse("plain CodeSource, same URL", 
                    g.implies(new CodeSource(a1.toURI().toURL(), new Certificate[]{alice}), NONE));
            assertTrue(digest(digestA).implies(computed(dcs, a2), NONE));
            // The same digest, asserted by a caller (forged), is not identity.
            Constructor<?> asserted = dcs.getConstructor(String.class, Certificate[].class, 
                    String.class, byte[].class);
            CodeSource forged = (CodeSource) asserted.newInstance(a1.toURI().toURL().toString(), 
                    new Certificate[]{alice}, ALG, digestA);
            assertFalse("digest asserted by the caller", g.implies(forged, NONE));
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    private static CodeSource computed(Class<?> dcs, File jar, Certificate... certs) throws Exception {
        return (CodeSource) dcs.getConstructor(String.class, Certificate[].class, String.class)
                .newInstance(jar.toURI().toURL().toString(), 
                        certs.length == 0 ? null : certs, ALG);
    }

    private static File jar(File dir, String name, String content) throws Exception {
        File f = new File(dir, name);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(f))) {
            out.putNextEntry(new JarEntry("x.txt"));
            out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return f;
    }

    /** The DigestCodeSource, if it has what this test needs, otherwise null. */
    private static Class<?> digestCodeSourceClass() {
        return DigestCodeSources.usable();
    }

    private static ProtectionDomain domain(CodeSource cs) {
        return new ProtectionDomain(cs, null, null, NONE);
    }

    /** Certificates are equal if their encodings are. */
    private static final class FakeCertificate extends Certificate {
        private static final long serialVersionUID = 1L;
        private final byte[] encoded;
        FakeCertificate(String id) {
            super("fake");
            encoded = id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        @Override public byte[] getEncoded() { return encoded.clone(); }
        @Override public void verify(PublicKey key) { }
        @Override public void verify(PublicKey key, String sigProvider) { }
        @Override public String toString() { return "FakeCertificate" + Arrays.toString(encoded); }
        @Override public PublicKey getPublicKey() { return null; }
    }
}
