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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link DefaultJwtVerifier}'s claims parser and the coherent
 * expiry extractor added by the 2026-07 board-review hardening (F2 element
 * fidelity, F3 single-parse cache TTL).
 */
public class DefaultJwtVerifierTest {

    private static String jwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    // ---- F3: TTL comes from the same parse the verifier/deriver use ----------

    @Test
    public void extractExpiryMatchesTheParsedExpClaim() {
        long exp = 1_899_999_999L;
        Instant got = DefaultJwtVerifier.extractExpiry(
                jwt("{\"sub\":\"a\",\"exp\":" + exp + "}"));
        Assert.assertEquals(Instant.ofEpochSecond(exp), got);
    }

    @Test
    public void extractExpiryIsCoherentWithParseClaimsJson() {
        // The TTL extractor must agree with the parser the verifier enforces:
        // no divergent ad-hoc scan that could cache a token past its true exp.
        String token = jwt("{\"iss\":\"https://issuer\",\"exp\":1750000000,"
                + "\"note\":\"contains the text exp inside a value\"}");
        String payload = new String(Base64.getUrlDecoder().decode(
                token.split("\\.")[1]), StandardCharsets.UTF_8);
        Map<String, String> claims = DefaultJwtVerifier.parseClaimsJson(payload);
        Instant fromParser = Instant.ofEpochSecond(Long.parseLong(claims.get("exp")));
        Assert.assertEquals(fromParser, DefaultJwtVerifier.extractExpiry(token));
    }

    @Test
    public void extractExpiryNullWhenNoExpOrMalformed() {
        Assert.assertNull(DefaultJwtVerifier.extractExpiry(jwt("{\"sub\":\"a\"}")));
        Assert.assertNull(DefaultJwtVerifier.extractExpiry(null));
        Assert.assertNull(DefaultJwtVerifier.extractExpiry("not-a-jwt"));
        Assert.assertNull(DefaultJwtVerifier.extractExpiry(
                jwt("{\"exp\":\"not-a-number\"}")));
    }

    // ---- F2: array elements keep their boundaries (comma fidelity) -----------

    @Test
    public void parseClaimsMultimapPreservesCommaInArrayElement() {
        Map<String, List<String>> claims = DefaultJwtVerifier.parseClaimsMultimap(
                "{\"groups\":[\"eng,ops\",\"users\"]}");
        List<String> groups = claims.get("groups");
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals("eng,ops", groups.get(0));
        Assert.assertEquals("users", groups.get(1));
    }

    @Test
    public void parseClaimsJsonRemainsCommaJoinedView() {
        // The flat view is retained for scalar/aud callers; a scalar is itself,
        // an array is comma-joined (lossy — hence parseClaimsMultimap for groups).
        Map<String, String> claims = DefaultJwtVerifier.parseClaimsJson(
                "{\"iss\":\"acme\",\"aud\":[\"svcA\",\"svcB\"]}");
        Assert.assertEquals("acme", claims.get("iss"));
        Assert.assertEquals("svcA,svcB", claims.get("aud"));
    }
}
