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

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.jini.security.jwt.DefaultJwtVerifier;
import net.jini.security.jwt.JwtVerificationException;
import net.jini.security.jwt.VerifiedJwt;

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
 * <p>It does not enforce the server's maximum token lifetime (default 1 hour
 * in {@code DefaultJwtVerifier}); a deployment whose identity provider issues
 * longer-lived tokens must raise {@code maxLifetime} on the server verifier.
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

    /**
     * Maximum length of a JWT token string in characters (64 KiB).
     * Tokens larger than this are rejected before any parsing to prevent
     * memory exhaustion via large Base64 decodes and string allocations.
     */
    static final int MAX_TOKEN_LENGTH = 65_536;

    /**
     * Most principals one token may name; the value the server enforces, so
     * a token the server would refuse fails the login instead.
     */
    static final int MAX_PRINCIPALS = DefaultJwtVerifier.DEFAULT_MAX_PRINCIPALS;

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

        // Reject oversized tokens before any allocation to prevent memory exhaustion.
        if (token.length() > MAX_TOKEN_LENGTH)
            throw new JwtValidationException(
                    "JWT token exceeds maximum length of " + MAX_TOKEN_LENGTH + " characters");

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3)
            throw new JwtValidationException(
                    "JWT must have exactly 3 parts (header.payload.signature), got: "
                            + parts.length);

        // ---- 1. Decode and parse header ----
        Map<String, Object> header = decodeJsonPart(parts[0], "header");
        Object algObj = header.get("alg");
        Object kidObj = header.get("kid");
        if (algObj != null && !(algObj instanceof String))
            throw new JwtValidationException("JWT header 'alg' is not a string");
        if (kidObj != null && !(kidObj instanceof String))
            throw new JwtValidationException("JWT header 'kid' is not a string");
        if (header.containsKey("crit"))
            throw new JwtValidationException("JWT crit header is not supported");
        String alg = (String) algObj;
        String kid = (String) kidObj;

        if (alg == null)
            throw new JwtValidationException("JWT header missing 'alg' field");
        if (REJECTED_ALGS.contains(alg))
            throw new JwtValidationException(
                    "JWT algorithm '" + alg + "' is not permitted; "
                            + "symmetric and unsigned algorithms are rejected");
        String jcaAlg = ALG_MAP.get(alg);
        if (jcaAlg == null)
            throw new JwtValidationException("Unsupported JWT algorithm");

        // ---- 2. Look up public key ----
        PublicKey publicKey = resolveKey(kid);
        if (publicKey == null)
            throw new JwtValidationException("No public key found for the JWT key id");

        // ---- 3. Verify signature ----
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException("JWT signature is not valid Base64URL");
        }

        boolean valid;
        try {
            Signature sig = Signature.getInstance(jcaAlg);
            sig.initVerify(publicKey);
            sig.update(signingInput);
            valid = sig.verify(signature);
        } catch (Exception e) {
            throw new JwtValidationException("JWT signature verification failed");
        }
        if (!valid)
            throw new JwtValidationException("JWT signature verification failed: signature mismatch");

        // ---- 4. Decode and parse payload ----
        Map<String, Object> payload = decodeJsonPart(parts[1], "payload");

        // ---- 5. Derive the identity exactly as the server does ----
        VerifiedJwt identity;
        try {
            identity = DefaultJwtVerifier.identityFromClaims(payload, MAX_PRINCIPALS);
        } catch (JwtVerificationException | RuntimeException e) {
            // The message names a claim and never a value.
            throw new JwtValidationException("JWT claims refused: " + e.getMessage());
        }

        // ---- 6. Validate time, issuer and audience ----
        validateClaims(identity);

        // ---- 7. Build ParsedJwt ----
        return buildParsedJwt(payload, identity);
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

    private void validateClaims(VerifiedJwt identity) throws JwtValidationException {
        Instant exp = identity.getExpiry();
        if (Instant.now().isAfter(exp.plusSeconds(CLOCK_SKEW_SECONDS))) {
            throw new JwtValidationException(
                    "JWT has expired at " + exp + " (clock skew tolerance: "
                            + CLOCK_SKEW_SECONDS + "s)");
        }

        if (!expectedIssuer.equals(identity.getIssuer()))
            throw new JwtValidationException(
                    "JWT issuer mismatch: expected '" + expectedIssuer + "'");

        // aud is a string or an array of strings; the element must match exactly.
        if (expectedAudience != null && !identity.getAudience().contains(expectedAudience))
            throw new JwtValidationException(
                    "JWT audience mismatch: expected '" + expectedAudience + "'");
    }

    private ParsedJwt buildParsedJwt(Map<String, Object> payload, VerifiedJwt identity) {
        Map<String, String> allClaims = new HashMap<>();
        for (String key : WELL_KNOWN_CLAIMS) {
            String val = scalar(payload.get(key));
            if (val != null) allClaims.put(key, val);
        }
        for (String extra : additionalClaimNames) {
            String val = scalar(payload.get(extra));
            if (val != null) allClaims.put(extra, val);
        }
        // exp as the server reads it (floored), so a fractional exp still parses.
        allClaims.put("exp", Long.toString(identity.getExpiry().getEpochSecond()));
        return new ParsedJwt(identity.getSubject(), identity.getEmail(),
                identity.getGroups(), allClaims, identity.getPrincipals());
    }

    /** A top-level string, number or boolean as text; arrays and objects are not claims here. */
    private static String scalar(Object v) {
        if (v instanceof String || v instanceof Boolean) return v.toString();
        if (v instanceof BigDecimal) return ((BigDecimal) v).toPlainString();
        return null;
    }

    /**
     * Base64URL-decodes a JWT part and parses it with the server's strict
     * RFC 8259 parser; only the top-level members are returned.  The failure
     * message carries nothing from the token.
     */
    private static Map<String, Object> decodeJsonPart(String b64url, String partName)
            throws JwtValidationException {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(b64url);
            return DefaultJwtVerifier.parseObject(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException("JWT " + partName + " is not a valid JSON object");
        }
    }

    /**
     * Parses an OAuth2 token endpoint response with the same strict parser
     * and returns its top-level string, number and boolean members as text.
     *
     * @throws IllegalArgumentException if {@code json} is not one JSON object
     */
    static Map<String, String> parseTokenResponse(String json) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Object> e : DefaultJwtVerifier.parseObject(json).entrySet()) {
            String v = scalar(e.getValue());
            if (v != null) out.put(e.getKey(), v);
        }
        return out;
    }
}
