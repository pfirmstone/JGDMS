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
package au.zeus.jgdms.security.jwt;

import net.jini.security.jwt.JwtPrincipal;
import org.junit.Test;

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
    // DoS-mitigation: JWT token length check
    // -----------------------------------------------------------------------

    /**
     * A token larger than {@link JwtValidator#MAX_TOKEN_LENGTH} must be
     * rejected before any parsing occurs.
     */
    @Test
    public void validateRejectsOversizedToken() {
        // Construct a minimal "JwksKeyCache" bypass by using a dummy validator
        // config pointing at a real-but-unused URI.  We only need to reach the
        // length check, which happens before any network or crypto operation.
        JwksKeyCache cache = new JwksKeyCache(java.net.URI.create("https://example.com/jwks"));
        JwtValidator validator = new JwtValidator(cache, "https://example.com", null);

        // Build a string longer than MAX_TOKEN_LENGTH
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= JwtValidator.MAX_TOKEN_LENGTH; i++) sb.append('A');
        String hugeToken = sb.toString();

        try {
            validator.validate(hugeToken);
            fail("Expected JwtValidationException for oversized token");
        } catch (JwtValidationException e) {
            assertTrue("Message should mention maximum length",
                    e.getMessage().contains("maximum length"));
        }
    }
}
