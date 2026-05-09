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
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Validates a compact-serialized JWT string (header.payload.signature).
 *
 * <p>Supported algorithms:
 * <ul>
 *   <li>RS256, RS384, RS512 (RSASSA-PKCS1-v1_5)</li>
 *   <li>ES256, ES384, ES512 (ECDSA)</li>
 *   <li>PS256, PS384, PS512 (RSASSA-PSS)</li>
 * </ul>
 *
 * <p>Explicitly rejected algorithms: {@code none}, {@code HS256}, {@code HS384},
 * {@code HS512} (symmetric — unsuitable for distributed verification).
 *
 * <p>This class is package-private and is used by {@link JwtLoginModule}.
 *
 * @since 3.1.1
 */
final class JwtValidator {

    private static final Logger LOG = Logger.getLogger(JwtValidator.class.getName());

    /** Clock skew tolerance when checking the {@code exp} claim. */
    private static final long CLOCK_SKEW_SECONDS = 30L;

    /** Algorithms that map to JCA algorithm names. */
    private static final Map<String, String> ALG_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("RS256", "SHA256withRSA");
        m.put("RS384", "SHA384withRSA");
        m.put("RS512", "SHA512withRSA");
        m.put("ES256", "SHA256withECDSA");
        m.put("ES384", "SHA384withECDSA");
        m.put("ES512", "SHA512withECDSA");
        m.put("PS256", "SHA256withRSAandMGF1");
        m.put("PS384", "SHA384withRSAandMGF1");
        m.put("PS512", "SHA512withRSAandMGF1");
        ALG_MAP = Collections.unmodifiableMap(m);
    }

    /** Algorithms explicitly rejected (symmetric / unsigned). */
    private static final Set<String> REJECTED_ALGS =
            Set.of("none", "HS256", "HS384", "HS512");

    /**
     * Standard JWT claim names that are always included in {@link ParsedJwt#allClaims()}
     * when present in the token payload.
     */
    private static final Set<String> WELL_KNOWN_CLAIMS =
            Set.of("iss", "sub", "aud", "exp", "iat", "email", "jti", "nbf");

    private final JwksKeyCache keyCache;
    private final String expectedIssuer;
    private final String expectedAudience; // null means no audience check
    private final Set<String> additionalClaimNames;

    /**
     * Constructs a {@code JwtValidator}.
     *
     * @param keyCache          JWKS key cache used to look up public keys
     * @param expectedIssuer    required value of the {@code iss} claim
     * @param expectedAudience  required value of the {@code aud} claim, or null to skip check
     */
    JwtValidator(JwksKeyCache keyCache, String expectedIssuer, String expectedAudience) {
        this(keyCache, expectedIssuer, expectedAudience, Collections.emptySet());
    }

    /**
     * Constructs a {@code JwtValidator} with additional claim names to extract.
     *
     * @param keyCache              JWKS key cache used to look up public keys
     * @param expectedIssuer        required value of the {@code iss} claim
     * @param expectedAudience      required value of the {@code aud} claim, or null to skip check
     * @param additionalClaimNames  extra claim names to include in {@link ParsedJwt#allClaims()}
     */
    JwtValidator(JwksKeyCache keyCache, String expectedIssuer, String expectedAudience,
                 Set<String> additionalClaimNames) {
        if (keyCache == null) throw new NullPointerException("keyCache");
        if (expectedIssuer == null) throw new NullPointerException("expectedIssuer");
        this.keyCache = keyCache;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.additionalClaimNames = additionalClaimNames == null
                ? Collections.emptySet() : additionalClaimNames;
    }

    /**
     * Validates a compact-serialized JWT string.
     *
     * @param token the JWT string in {@code header.payload.signature} format
     * @return a {@link ParsedJwt} record containing the verified claims
     * @throws JwtValidationException if the token is malformed, uses a rejected
     *                                algorithm, has an invalid signature, or fails
     *                                claims validation
     */
    ParsedJwt validate(String token) throws JwtValidationException {
        if (token == null || token.isEmpty())
            throw new JwtValidationException("JWT token must not be null or empty");

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3)
            throw new JwtValidationException(
                    "JWT must have exactly 3 parts (header.payload.signature), got: "
                            + parts.length);

        // ---- 1. Decode and parse header ----
        Map<String, String> header = decodeJsonPart(parts[0], "header");
        String alg = header.get("alg");
        String kid = header.get("kid");

        if (alg == null)
            throw new JwtValidationException("JWT header missing 'alg' field");
        if (REJECTED_ALGS.contains(alg))
            throw new JwtValidationException(
                    "JWT algorithm '" + alg + "' is not permitted; "
                            + "symmetric and unsigned algorithms are rejected");
        String jcaAlg = ALG_MAP.get(alg);
        if (jcaAlg == null)
            throw new JwtValidationException("Unsupported JWT algorithm: " + alg);

        // ---- 2. Look up public key ----
        PublicKey publicKey = resolveKey(kid);
        if (publicKey == null)
            throw new JwtValidationException(
                    "No public key found for kid=" + kid + " in JWKS cache");

        // ---- 3. Verify signature ----
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException("JWT signature is not valid Base64URL", e);
        }

        boolean valid;
        try {
            Signature sig = Signature.getInstance(jcaAlg);
            sig.initVerify(publicKey);
            sig.update(signingInput);
            valid = sig.verify(signature);
        } catch (Exception e) {
            throw new JwtValidationException("JWT signature verification failed: " + e.getMessage(), e);
        }
        if (!valid)
            throw new JwtValidationException("JWT signature verification failed: signature mismatch");

        // ---- 4. Decode and parse payload ----
        Map<String, String> payload = decodeJsonPart(parts[1], "payload");

        // ---- 5. Validate standard claims ----
        validateClaims(payload);

        // ---- 6. Build ParsedJwt ----
        return buildParsedJwt(payload);
    }

    // -----------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------

    private PublicKey resolveKey(String kid) {
        PublicKey key = keyCache.getKey(kid);
        if (key == null) {
            // Key not found — may be a key rotation; refresh and retry once
            LOG.fine("Key not found in cache for kid=" + kid + "; refreshing JWKS");
            keyCache.refresh();
            key = keyCache.getKey(kid);
        }
        return key;
    }

    private void validateClaims(Map<String, String> payload) throws JwtValidationException {
        // Validate exp
        String expStr = payload.get("exp");
        if (expStr != null) {
            try {
                long expEpoch = Long.parseLong(expStr);
                Instant exp = Instant.ofEpochSecond(expEpoch);
                if (Instant.now().isAfter(exp.plusSeconds(CLOCK_SKEW_SECONDS))) {
                    throw new JwtValidationException(
                            "JWT has expired at " + exp + " (clock skew tolerance: "
                                    + CLOCK_SKEW_SECONDS + "s)");
                }
            } catch (NumberFormatException e) {
                throw new JwtValidationException("JWT 'exp' claim is not a valid number: " + expStr, e);
            }
        }

        // Validate iss
        String iss = payload.get("iss");
        if (!expectedIssuer.equals(iss))
            throw new JwtValidationException(
                    "JWT issuer mismatch: expected '" + expectedIssuer + "', got '" + iss + "'");

        // Validate aud (if configured)
        if (expectedAudience != null) {
            String aud = payload.get("aud");
            if (aud == null || !aud.contains(expectedAudience))
                throw new JwtValidationException(
                        "JWT audience mismatch: expected '" + expectedAudience
                                + "', got '" + aud + "'");
        }
    }

    private ParsedJwt buildParsedJwt(Map<String, String> payload) {
        String subject = payload.get("sub");
        String email   = payload.get("email");

        // Parse groups array from the raw payload string (stored with special marker)
        List<String> groups = parseGroups(payload.get("__groups__"));

        // Build allClaims map from well-known + additional claims
        Map<String, String> allClaims = new HashMap<>();
        for (String key : WELL_KNOWN_CLAIMS) {
            String val = payload.get(key);
            if (val != null) allClaims.put(key, val);
        }
        for (String extra : additionalClaimNames) {
            String val = payload.get(extra);
            if (val != null) allClaims.put(extra, val);
        }
        return new ParsedJwt(subject, email, groups, allClaims);
    }

    private static List<String> parseGroups(String groupsMarker) {
        if (groupsMarker == null || groupsMarker.isEmpty()) return Collections.emptyList();
        // groupsMarker is a comma-separated list built by decodeJsonPart
        List<String> result = new ArrayList<>();
        for (String g : groupsMarker.split(",", -1)) {
            if (!g.isEmpty()) result.add(g);
        }
        return result;
    }

    /**
     * Base64URL-decodes a JWT part and parses it as a flat JSON object.
     * Array fields named {@code groups} or {@code aud} are concatenated with
     * commas and stored as strings.  The {@code groups} value is stored under
     * the key {@code "__groups__"} to distinguish it from a string-valued claim.
     */
    private static Map<String, String> decodeJsonPart(String b64url, String partName)
            throws JwtValidationException {
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(b64url);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException(
                    "JWT " + partName + " is not valid Base64URL", e);
        }
        String json = new String(decoded, StandardCharsets.UTF_8);
        try {
            return parseClaimsJson(json);
        } catch (Exception e) {
            throw new JwtValidationException(
                    "Failed to parse JWT " + partName + " JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a flat JWT claims / header JSON object into a string map.
     * Numeric values are kept as their raw decimal string representation.
     * The {@code groups} array is stored as a comma-joined string under
     * {@code "__groups__"}; the {@code aud} array is stored as a
     * comma-joined string under {@code "aud"}.
     */
    static Map<String, String> parseClaimsJson(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;

        int[] pos = {0};
        JwksKeyCache.skipWhitespace(json, pos);
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '{') return result;
        pos[0]++; // consume '{'

        while (pos[0] < json.length()) {
            JwksKeyCache.skipWhitespace(json, pos);
            if (pos[0] >= json.length()) break;
            char c = json.charAt(pos[0]);
            if (c == '}') break;
            if (c == ',') { pos[0]++; continue; }

            String key = JwksKeyCache.readString(json, pos);
            if (key == null) break;
            JwksKeyCache.skipWhitespace(json, pos);
            if (pos[0] >= json.length() || json.charAt(pos[0]) != ':') break;
            pos[0]++; // consume ':'
            JwksKeyCache.skipWhitespace(json, pos);

            if (pos[0] >= json.length()) break;
            char v = json.charAt(pos[0]);

            if (v == '"') {
                String value = JwksKeyCache.readString(json, pos);
                if (value != null) result.put(key, value);
            } else if (v == '[') {
                // Array: collect string elements, join with comma
                String joined = readStringArray(json, pos);
                String storeKey = "groups".equals(key) ? "__groups__" : key;
                result.put(storeKey, joined);
            } else {
                // Number, boolean, null, nested object
                String raw = readRawScalar(json, pos);
                if (raw != null) result.put(key, raw);
            }
        }
        return result;
    }

    /** Reads a JSON array of strings/numbers and returns them joined by commas. */
    private static String readStringArray(String json, int[] pos) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '[') return "";
        pos[0]++; // consume '['
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        while (pos[0] < json.length()) {
            JwksKeyCache.skipWhitespace(json, pos);
            if (pos[0] >= json.length()) break;
            char c = json.charAt(pos[0]);
            if (c == ']') { pos[0]++; break; }
            if (c == ',') { pos[0]++; continue; }
            String item;
            if (c == '"') {
                item = JwksKeyCache.readString(json, pos);
            } else {
                item = readRawScalar(json, pos);
            }
            if (item != null) {
                if (!first) sb.append(',');
                sb.append(item);
                first = false;
            }
        }
        return sb.toString();
    }

    /**
     * Reads a raw scalar JSON value (number, boolean, null) stopping before
     * {@code ,}, {@code ]}, or {@code }}.
     */
    private static String readRawScalar(String json, int[] pos) {
        int start = pos[0];
        while (pos[0] < json.length()) {
            char d = json.charAt(pos[0]);
            if (d == ',' || d == '}' || d == ']' || d == ' ' || d == '\t'
                    || d == '\r' || d == '\n') break;
            pos[0]++;
        }
        if (pos[0] > start) return json.substring(start, pos[0]);
        // Skip unexpected tokens
        pos[0]++;
        return null;
    }

    // Package-private accessors for package-level helpers used in parseClaimsJson
    static void skipWhitespace(String json, int[] pos) {
        JwksKeyCache.skipWhitespace(json, pos);
    }

    static String readString(String json, int[] pos) {
        return JwksKeyCache.readString(json, pos);
    }
}
