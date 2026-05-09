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

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for the hand-rolled JSON parsers in {@link JwksKeyCache} and
 * {@link JwtValidator}.
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
}
