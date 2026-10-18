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

import java.lang.reflect.Constructor;
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
        Constructor<?> c = digestCodeSourceConstructor();
        Assume.assumeNotNull(c); // Stock JDK: fail closed, covered above.
        CodeSource same = (CodeSource) c.newInstance("http://localhost/a.jar", 
                new Certificate[]{alice}, ALG, DIGEST_A);
        CodeSource moved = (CodeSource) c.newInstance("http://localhost/moved/z.jar", 
                new Certificate[]{alice}, ALG, DIGEST_A);
        CodeSource otherDigest = (CodeSource) c.newInstance("http://localhost/a.jar", 
                new Certificate[]{alice}, ALG, DIGEST_B);
        CodeSource otherSigner = (CodeSource) c.newInstance("http://localhost/a.jar", 
                new Certificate[]{bob}, ALG, DIGEST_A);
        CodeSource unsigned = (CodeSource) c.newInstance("http://localhost/a.jar", 
                (Certificate[]) null, ALG, DIGEST_A);
        PermissionGrant g = digest(DIGEST_A, alice);
        assertTrue(g.implies(same, NONE));
        assertTrue("same digest at another location", g.implies(moved, NONE));
        assertFalse(g.implies(otherDigest, NONE));
        assertFalse(g.implies(otherSigner, NONE));
        assertFalse(g.implies(unsigned, NONE));
        assertFalse("plain CodeSource, same URL", 
                g.implies(new CodeSource(location, new Certificate[]{alice}), NONE));
        assertTrue(digest(DIGEST_A).implies(moved, NONE));
    }

    private static Constructor<?> digestCodeSourceConstructor() {
        try {
            return Class.forName("java.security.DigestCodeSource").getConstructor(
                    String.class, Certificate[].class, String.class, byte[].class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
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
