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

package org.apache.river.api.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.SecurityPermission;
import java.security.cert.Certificate;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import tests.support.MyPrincipal;

/**
 * Tests for {@link DigestGrant} — equality, hashing, implication, and
 * round-trip serialisation through the builder proxy.
 *
 * <p>Because {@code java.security.DigestCodeSource} is only present in the
 * DirtyChai JDK (not on standard JDK 17/21), the {@link #implies} tests use
 * plain {@link CodeSource} and verify the fail-secure behaviour
 * ({@code implies} must return {@code false} when DigestCodeSource is absent).
 */
public class DigestGrantTest {

    private static final byte[] DIGEST_A = {0x01, 0x02, 0x03, 0x04};
    private static final byte[] DIGEST_B = {0x0a, 0x0b, 0x0c, 0x0d};
    private static final String ALG = "SHA-256";

    private PermissionGrant grant;
    private PermissionGrant grantSame;
    private PermissionGrant grantDiffDigest;
    private PermissionGrant grantDiffAlg;
    private PermissionGrant uriGrant;

    @Before
    public void setUp() {
        PermissionGrantBuilder b = PermissionGrantBuilder.newBuilder();
        grant = b.uri("file:/foo/bar.jar")
                 .permissions(new Permission[]{ new SecurityPermission("test") })
                 .principals(new Principal[0])
                 .certificates(new Certificate[0], new String[0])
                 .digest(ALG, DIGEST_A)
                 .context(PermissionGrantBuilder.DIGEST)
                 .build();

        grantSame = PermissionGrantBuilder.newBuilder()
                 .uri("file:/foo/bar.jar")
                 .permissions(new Permission[]{ new SecurityPermission("test") })
                 .principals(new Principal[0])
                 .certificates(new Certificate[0], new String[0])
                 .digest(ALG, DIGEST_A)
                 .context(PermissionGrantBuilder.DIGEST)
                 .build();

        grantDiffDigest = PermissionGrantBuilder.newBuilder()
                 .uri("file:/foo/bar.jar")
                 .permissions(new Permission[]{ new SecurityPermission("test") })
                 .principals(new Principal[0])
                 .certificates(new Certificate[0], new String[0])
                 .digest(ALG, DIGEST_B)
                 .context(PermissionGrantBuilder.DIGEST)
                 .build();

        grantDiffAlg = PermissionGrantBuilder.newBuilder()
                 .uri("file:/foo/bar.jar")
                 .permissions(new Permission[]{ new SecurityPermission("test") })
                 .principals(new Principal[0])
                 .certificates(new Certificate[0], new String[0])
                 .digest("MD5", DIGEST_A)
                 .context(PermissionGrantBuilder.DIGEST)
                 .build();

        uriGrant = PermissionGrantBuilder.newBuilder()
                 .uri("file:/foo/bar.jar")
                 .permissions(new Permission[]{ new SecurityPermission("test") })
                 .principals(new Principal[0])
                 .certificates(new Certificate[0], new String[0])
                 .context(PermissionGrantBuilder.URI)
                 .build();
    }

    // -----------------------------------------------------------------------
    // Sanity: builder produces a DigestGrant (not a plain URIGrant)
    // -----------------------------------------------------------------------

    @Test
    public void testBuilderProducesDigestGrant() {
        System.out.println("testBuilderProducesDigestGrant");
        assertTrue("DIGEST context must produce DigestGrant",
                grant instanceof DigestGrant);
    }

    @Test
    public void testUriContextDoesNotProduceDigestGrant() {
        System.out.println("testUriContextDoesNotProduceDigestGrant");
        assertFalse(uriGrant instanceof DigestGrant);
    }

    // -----------------------------------------------------------------------
    // equals / hashCode
    // -----------------------------------------------------------------------

    @Test
    public void testEqualsSameGrant() {
        System.out.println("testEqualsSameGrant");
        assertEquals(grant, grantSame);
    }

    @Test
    public void testHashCodeSameGrant() {
        System.out.println("testHashCodeSameGrant");
        assertEquals(grant.hashCode(), grantSame.hashCode());
    }

    @Test
    public void testNotEqualDifferentDigest() {
        System.out.println("testNotEqualDifferentDigest");
        assertFalse(grant.equals(grantDiffDigest));
    }

    @Test
    public void testNotEqualDifferentAlgorithm() {
        System.out.println("testNotEqualDifferentAlgorithm");
        assertFalse(grant.equals(grantDiffAlg));
    }

    @Test
    public void testNotEqualDifferentGrantType() {
        System.out.println("testNotEqualDifferentGrantType");
        assertFalse("DigestGrant must not equal URIGrant", grant.equals(uriGrant));
    }

    @Test
    public void testNotEqualNull() {
        System.out.println("testNotEqualNull");
        assertFalse(grant.equals(null));
    }

    @Test
    public void testEqualsSelf() {
        System.out.println("testEqualsSelf");
        assertEquals(grant, grant);
    }

    // -----------------------------------------------------------------------
    // impliesEquivalent
    // -----------------------------------------------------------------------

    @Test
    public void testImpliesEquivalentSameGrant() {
        System.out.println("testImpliesEquivalentSameGrant");
        assertTrue(grant.impliesEquivalent(grantSame));
    }

    @Test
    public void testImpliesEquivalentDifferentDigest() {
        System.out.println("testImpliesEquivalentDifferentDigest");
        assertFalse(grant.impliesEquivalent(grantDiffDigest));
    }

    @Test
    public void testImpliesEquivalentDifferentAlgorithm() {
        System.out.println("testImpliesEquivalentDifferentAlgorithm");
        assertFalse(grant.impliesEquivalent(grantDiffAlg));
    }

    @Test
    public void testImpliesEquivalentDifferentGrantType() {
        System.out.println("testImpliesEquivalentDifferentGrantType");
        assertFalse(grant.impliesEquivalent(uriGrant));
    }

    // -----------------------------------------------------------------------
    // implies(ProtectionDomain) — fail-secure on standard JDK
    // -----------------------------------------------------------------------

    @Test
    public void testImpliesNullPdReturnsFalse() throws Exception {
        System.out.println("testImpliesNullPdReturnsFalse");
        assertFalse(grant.implies((ProtectionDomain) null));
    }

    @Test
    public void testImpliesPlainCodeSourceReturnsFalse() throws Exception {
        System.out.println("testImpliesPlainCodeSourceReturnsFalse");
        // A plain CodeSource (no DigestCodeSource) must never be implied —
        // this enforces the fail-secure behaviour on standard JDK.
        CodeSource cs = new CodeSource(
                new java.net.URL("file:/foo/bar.jar"), (Certificate[]) null);
        ProtectionDomain pd = new ProtectionDomain(cs, null);
        assertFalse("Plain CodeSource must NOT be implied by DigestGrant",
                grant.implies(pd));
    }

    @Test
    public void testImpliesClassLoaderReturnsFalse() {
        System.out.println("testImpliesClassLoaderReturnsFalse");
        // ClassLoader-based implication is always false for DigestGrant
        assertFalse(grant.implies(getClass().getClassLoader(), new Principal[0]));
    }

    // -----------------------------------------------------------------------
    // implies(ClassLoader, Principal[]) — always false
    // -----------------------------------------------------------------------

    @Test
    public void testImpliesNullClassLoaderReturnsFalse() {
        System.out.println("testImpliesNullClassLoaderReturnsFalse");
        assertFalse(grant.implies((ClassLoader) null, new Principal[0]));
    }

    // -----------------------------------------------------------------------
    // getBuilderTemplate round-trip
    // -----------------------------------------------------------------------

    @Test
    public void testGetBuilderTemplateReproducesEqualGrant() {
        System.out.println("testGetBuilderTemplateReproducesEqualGrant");
        PermissionGrantBuilder pgb = grant.getBuilderTemplate();
        PermissionGrant rebuilt = pgb.build();
        assertEquals(grant, rebuilt);
        assertEquals(grant.hashCode(), rebuilt.hashCode());
    }

    // -----------------------------------------------------------------------
    // Serialisation round-trip (via PermissionGrantBuilder proxy)
    // -----------------------------------------------------------------------

    @Test
    public void testSerializationRoundTrip() throws Exception {
        System.out.println("testSerializationRoundTrip");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(grant);
        }
        Object deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = ois.readObject();
        }
        assertEquals("Deserialized grant must equal original", grant, deserialized);
        assertEquals(grant.hashCode(), deserialized.hashCode());
    }

    @Test
    public void testSerializationRoundTripDifferentDigest() throws Exception {
        System.out.println("testSerializationRoundTripDifferentDigest");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(grantDiffDigest);
        }
        Object deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = ois.readObject();
        }
        assertEquals(grantDiffDigest, deserialized);
        assertFalse("Round-tripped grants with different digests must not be equal",
                grant.equals(deserialized));
    }

    // -----------------------------------------------------------------------
    // getPermissions
    // -----------------------------------------------------------------------

    @Test
    public void testGetPermissions() {
        System.out.println("testGetPermissions");
        assertTrue(grant.getPermissions().contains(new SecurityPermission("test")));
    }

    // -----------------------------------------------------------------------
    // Null digest bytes — defensive copy
    // -----------------------------------------------------------------------

    @Test
    public void testNullDigestBytesDoNotThrow() {
        System.out.println("testNullDigestBytesDoNotThrow");
        // Building with null digest bytes must not throw
        PermissionGrant g = PermissionGrantBuilder.newBuilder()
                .uri("file:/bar.jar")
                .permissions(new Permission[0])
                .principals(new Principal[0])
                .certificates(new Certificate[0], new String[0])
                .digest(ALG, null)
                .context(PermissionGrantBuilder.DIGEST)
                .build();
        assertNotNull(g);
    }
}
