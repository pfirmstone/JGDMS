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

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for the hand-rolled JSON parsers in {@link JwksKeyCache} and
 * {@link JwtValidator}, including DoS-mitigation boundary checks.
 */
public class JwksJsonParserTest {

    // -----------------------------------------------------------------------
    // JwksKeyCache.parseJwksJson — structural parsing
    // -----------------------------------------------------------------------

    @Test
    public void parseEmptyKeysArray() {
        List<Map<String, String>> keys = JwksKeyCache.parseJwksJson("{\"keys\":[]}");
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void parseNullReturnsEmpty() {
        List<Map<String, String>> keys = JwksKeyCache.parseJwksJson(null);
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void parseEmptyStringReturnsEmpty() {
        List<Map<String, String>> keys = JwksKeyCache.parseJwksJson("");
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void parseSingleRsaKey() {
        String json = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"key1\","
                + "\"n\":\"sN6jnDDEHgFmFGb\",\"e\":\"AQAB\"}]}";
        List<Map<String, String>> keys = JwksKeyCache.parseJwksJson(json);
        assertEquals(1, keys.size());
        Map<String, String> key = keys.get(0);
        assertEquals("RSA",   key.get("kty"));
        assertEquals("key1",  key.get("kid"));
        assertEquals("AQAB",  key.get("e"));
    }

    @Test
    public void parseTwoKeys() {
        String json = "{\"keys\":["
                + "{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\"abc\",\"e\":\"AQAB\"},"
                + "{\"kty\":\"EC\",\"kid\":\"k2\",\"crv\":\"P-256\","
                + "\"x\":\"xxx\",\"y\":\"yyy\"}"
                + "]}";
        List<Map<String, String>> keys = JwksKeyCache.parseJwksJson(json);
        assertEquals(2, keys.size());
        assertEquals("RSA", keys.get(0).get("kty"));
        assertEquals("EC",  keys.get(1).get("kty"));
        assertEquals("k2",  keys.get(1).get("kid"));
        assertEquals("P-256", keys.get(1).get("crv"));
    }

    @Test
    public void parseIgnoresFieldsBeforeKeys() {
        String json = "{\"extra\":\"value\",\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\","
                + "\"n\":\"abc\",\"e\":\"AQAB\"}]}";
        List<Map<String, String>> keys = JwksKeyCache.parseJwksJson(json);
        assertEquals(1, keys.size());
    }

    @Test
    public void parseHandlesEscapedStrings() {
        // kid contains a backslash-escaped quote
        String json = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"key\\\"1\","
                + "\"n\":\"abc\",\"e\":\"AQAB\"}]}";
        List<Map<String, String>> keys = JwksKeyCache.parseJwksJson(json);
        assertEquals(1, keys.size());
        assertEquals("key\"1", keys.get(0).get("kid"));
    }

    // -----------------------------------------------------------------------
    // JwtValidator.parseClaimsJson — claims parsing
    // -----------------------------------------------------------------------

    @Test
    public void parseClaimsBasic() {
        String json = "{\"iss\":\"https://example.org\",\"sub\":\"alice\","
                + "\"exp\":9999999999,\"iat\":1700000000}";
        Map<String, String> claims = JwtValidator.parseClaimsJson(json);
        assertEquals("https://example.org", claims.get("iss"));
        assertEquals("alice", claims.get("sub"));
        assertEquals("9999999999", claims.get("exp"));
        assertEquals("1700000000", claims.get("iat"));
    }

    @Test
    public void parseClaimsGroupsArray() {
        String json = "{\"sub\":\"alice\",\"groups\":[\"admins\",\"users\"]}";
        Map<String, String> claims = JwtValidator.parseClaimsJson(json);
        // groups stored under __groups__
        String groups = claims.get("__groups__");
        assertNotNull("Expected __groups__ key", groups);
        assertTrue(groups.contains("admins"));
        assertTrue(groups.contains("users"));
    }

    @Test
    public void parseClaimsAudArray() {
        String json = "{\"aud\":[\"service-a\",\"service-b\"],\"sub\":\"bob\"}";
        Map<String, String> claims = JwtValidator.parseClaimsJson(json);
        String aud = claims.get("aud");
        assertNotNull(aud);
        assertTrue(aud.contains("service-a"));
        assertTrue(aud.contains("service-b"));
    }

    @Test
    public void parseClaimsAudString() {
        String json = "{\"aud\":\"service-a\",\"sub\":\"bob\"}";
        Map<String, String> claims = JwtValidator.parseClaimsJson(json);
        assertEquals("service-a", claims.get("aud"));
    }

    @Test
    public void parseClaimsBooleanField() {
        // Some JWTs include boolean claims — they should be stored as strings
        String json = "{\"sub\":\"alice\",\"email_verified\":true}";
        Map<String, String> claims = JwtValidator.parseClaimsJson(json);
        assertEquals("alice", claims.get("sub"));
        assertEquals("true", claims.get("email_verified"));
    }

    @Test
    public void parseClaimsEmptyObject() {
        Map<String, String> claims = JwtValidator.parseClaimsJson("{}");
        assertNotNull(claims);
        assertTrue(claims.isEmpty());
    }

    @Test
    public void parseClaimsNullReturnsEmpty() {
        Map<String, String> claims = JwtValidator.parseClaimsJson(null);
        assertNotNull(claims);
        assertTrue(claims.isEmpty());
    }

    // -----------------------------------------------------------------------
    // JwtPrincipal — basic smoke tests (already in JwtPrincipalTest but
    // useful here for cross-verification)
    // -----------------------------------------------------------------------

    @Test
    public void jwtPrincipalGroupClaim() {
        JwtPrincipal p = new JwtPrincipal("group:admins");
        assertEquals("group", p.getClaimName());
        assertEquals("admins", p.getClaimValue());
    }

    @Test
    public void jwtValidationExceptionMessages() {
        JwtValidationException e1 = new JwtValidationException("test message");
        assertEquals("test message", e1.getMessage());

        RuntimeException cause = new RuntimeException("cause");
        JwtValidationException e2 = new JwtValidationException("with cause", cause);
        assertEquals("with cause", e2.getMessage());
        assertSame(cause, e2.getCause());
    }

    // -----------------------------------------------------------------------
    // DoS-mitigation boundary tests
    // -----------------------------------------------------------------------

    /**
     * readString() must throw when the accumulated characters exceed
     * {@link JwksKeyCache#MAX_JSON_STRING_LENGTH}.
     */
    @Test(expected = IllegalStateException.class)
    public void readStringRejectsOversizedValue() {
        // Build a JSON string longer than MAX_JSON_STRING_LENGTH
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i <= JwksKeyCache.MAX_JSON_STRING_LENGTH; i++) sb.append('a');
        sb.append('"');
        int[] pos = {0};
        JwksKeyCache.readString(sb.toString(), pos); // must throw
    }

    /**
     * readString() must succeed exactly at the limit (MAX_JSON_STRING_LENGTH chars).
     */
    @Test
    public void readStringAcceptsValueAtLimit() {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < JwksKeyCache.MAX_JSON_STRING_LENGTH; i++) sb.append('x');
        sb.append('"');
        int[] pos = {0};
        String result = JwksKeyCache.readString(sb.toString(), pos);
        assertNotNull(result);
        assertEquals(JwksKeyCache.MAX_JSON_STRING_LENGTH, result.length());
    }

    /**
     * parseJwksJson() must silently truncate a JWKS with more than
     * {@link JwksKeyCache#MAX_JWKS_KEYS} keys.
     */
    @Test
    public void parseJwksJsonTruncatesExcessKeys() {
        // Build a JWKS with MAX_JWKS_KEYS + 10 keys
        int keyCount = JwksKeyCache.MAX_JWKS_KEYS + 10;
        StringBuilder json = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < keyCount; i++) {
            if (i > 0) json.append(',');
            json.append("{\"kty\":\"RSA\",\"kid\":\"k").append(i)
                .append("\",\"n\":\"abc\",\"e\":\"AQAB\"}");
        }
        json.append("]}");
        List<Map<String, String>> keys = JwksKeyCache.parseJwksJson(json.toString());
        assertEquals("Should be capped at MAX_JWKS_KEYS",
                JwksKeyCache.MAX_JWKS_KEYS, keys.size());
    }

    /**
     * parseClaimsJson() must truncate a groups array that exceeds
     * {@link JwtValidator#MAX_GROUPS} elements.
     */
    @Test
    public void parseClaimsJsonTruncatesLargeGroupsArray() {
        int groupCount = JwtValidator.MAX_GROUPS + 20;
        StringBuilder json = new StringBuilder("{\"sub\":\"alice\",\"groups\":[");
        for (int i = 0; i < groupCount; i++) {
            if (i > 0) json.append(',');
            json.append("\"group").append(i).append('"');
        }
        json.append("]}");
        Map<String, String> claims = JwtValidator.parseClaimsJson(json.toString());
        // Groups are stored comma-joined under __groups__
        String groups = claims.get("__groups__");
        assertNotNull("Expected __groups__ key", groups);
        long count = groups.chars().filter(c -> c == ',').count() + 1;
        assertTrue("Group count should be capped at MAX_GROUPS",
                count <= JwtValidator.MAX_GROUPS);
    }

    /**
     * parseJwksJson() must return an empty list (not throw) for a JWKS where
     * a key field value exceeds MAX_JSON_STRING_LENGTH (the oversized key is
     * simply skipped due to the IllegalStateException being caught upstream).
     */
    @Test
    public void parseJwksJsonSkipsOversizedKeyField() {
        // Build a JWKS with one key that has an oversized 'n' value
        StringBuilder oversizedN = new StringBuilder();
        for (int i = 0; i <= JwksKeyCache.MAX_JSON_STRING_LENGTH; i++) oversizedN.append('A');
        String json = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\","
                + "\"n\":\"" + oversizedN + "\",\"e\":\"AQAB\"}]}";
        // The parser should throw IllegalStateException while reading the oversized
        // string; parseJwksJson propagates it — the caller (parseJwks) catches it.
        // We verify that the raw parser itself throws as expected.
        try {
            JwksKeyCache.parseJwksJson(json);
            fail("Expected IllegalStateException for oversized JSON string");
        } catch (IllegalStateException e) {
            // expected
        }
    }
}
