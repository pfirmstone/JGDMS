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
package net.jini.security.jwt;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link JwtPrincipal}.
 */
public class JwtPrincipalTest {

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Test
    public void constructValidSubClaim() {
        JwtPrincipal p = new JwtPrincipal("sub:alice@example.org");
        assertEquals("sub:alice@example.org", p.getName());
    }

    @Test
    public void constructValidGroupClaim() {
        JwtPrincipal p = new JwtPrincipal("group:admins");
        assertEquals("group:admins", p.getName());
    }

    @Test
    public void constructValidEmailClaim() {
        JwtPrincipal p = new JwtPrincipal("email:alice@example.org");
        assertEquals("email:alice@example.org", p.getName());
    }

    @Test(expected = NullPointerException.class)
    public void constructNullThrows() {
        new JwtPrincipal(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructBlankThrows() {
        new JwtPrincipal("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructNoColonThrows() {
        new JwtPrincipal("subAlice");
    }

    @Test
    public void constructColonInValue() {
        // Value contains colon (e.g. email-like sub)
        JwtPrincipal p = new JwtPrincipal("sub:alice:extra");
        assertEquals("sub", p.getClaimName());
        assertEquals("alice:extra", p.getClaimValue());
    }

    // -----------------------------------------------------------------------
    // getClaimName / getClaimValue
    // -----------------------------------------------------------------------

    @Test
    public void getClaimNameExtractsPrefix() {
        JwtPrincipal p = new JwtPrincipal("group:engineers");
        assertEquals("group", p.getClaimName());
    }

    @Test
    public void getClaimValueExtractsSuffix() {
        JwtPrincipal p = new JwtPrincipal("group:engineers");
        assertEquals("engineers", p.getClaimValue());
    }

    // -----------------------------------------------------------------------
    // equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Test
    public void equalsSameInstance() {
        JwtPrincipal p = new JwtPrincipal("sub:alice@example.org");
        assertEquals(p, p);
    }

    @Test
    public void equalsSymmetric() {
        JwtPrincipal a = new JwtPrincipal("sub:alice@example.org");
        JwtPrincipal b = new JwtPrincipal("sub:alice@example.org");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void equalsHashCodeConsistent() {
        JwtPrincipal a = new JwtPrincipal("sub:alice@example.org");
        JwtPrincipal b = new JwtPrincipal("sub:alice@example.org");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualsOtherName() {
        JwtPrincipal a = new JwtPrincipal("sub:alice@example.org");
        JwtPrincipal b = new JwtPrincipal("sub:bob@example.org");
        assertNotEquals(a, b);
    }

    @Test
    public void notEqualsNull() {
        assertFalse(new JwtPrincipal("sub:alice@example.org").equals(null));
    }

    @Test
    public void notEqualsOtherType() {
        assertFalse(new JwtPrincipal("sub:alice@example.org").equals("sub:alice@example.org"));
    }

    @Test
    public void toStringContainsName() {
        JwtPrincipal p = new JwtPrincipal("sub:alice@example.org");
        assertTrue(p.toString().contains("sub:alice@example.org"));
    }

    // -----------------------------------------------------------------------
    // Serialization
    // -----------------------------------------------------------------------

    @Test
    public void serializationRoundTrip() throws Exception {
        JwtPrincipal original = new JwtPrincipal("sub:alice@example.org");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }
        JwtPrincipal deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (JwtPrincipal) ois.readObject();
        }
        assertEquals(original, deserialized);
        assertEquals(original.getName(), deserialized.getName());
    }

    @Test
    public void deserializationRejectsNoColon() throws Exception {
        // Serialize valid, then patch to remove ':'
        JwtPrincipal valid = new JwtPrincipal("sub:alice");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(valid);
        }
        byte[] bytes = baos.toByteArray();
        // Replace "sub:alice" with "subalice_" (same length, no colon)
        byte[] find    = "sub:alice".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] replace = "subalice_".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tampered = patchFirstOccurrence(bytes, find, replace);

        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(tampered))) {
            ois.readObject();
            fail("Expected IOException for invalid JwtPrincipal name on deserialization");
        } catch (IOException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // JwtExpiryClaim
    // -----------------------------------------------------------------------

    @Test
    public void jwtExpiryClaimGetExpiry() {
        Instant now = Instant.now();
        JwtExpiryClaim claim = new JwtExpiryClaim(now);
        assertEquals(now, claim.getExpiry());
    }

    @Test(expected = NullPointerException.class)
    public void jwtExpiryClaimNullThrows() {
        new JwtExpiryClaim(null);
    }

    @Test
    public void jwtExpiryClaimEqualsHashCode() {
        Instant t = Instant.ofEpochSecond(9999999L);
        JwtExpiryClaim a = new JwtExpiryClaim(t);
        JwtExpiryClaim b = new JwtExpiryClaim(t);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void jwtExpiryClaimToString() {
        Instant t = Instant.ofEpochSecond(9999999L);
        JwtExpiryClaim c = new JwtExpiryClaim(t);
        assertTrue(c.toString().contains("JwtExpiryClaim"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static byte[] patchFirstOccurrence(byte[] src, byte[] find, byte[] replace) {
        if (find.length != replace.length)
            throw new IllegalArgumentException("find and replace must be same length");
        outer:
        for (int i = 0; i <= src.length - find.length; i++) {
            for (int j = 0; j < find.length; j++) {
                if (src[i + j] != find[j]) continue outer;
            }
            byte[] result = src.clone();
            System.arraycopy(replace, 0, result, i, replace.length);
            return result;
        }
        throw new IllegalStateException("Pattern not found in serialized bytes");
    }
}
