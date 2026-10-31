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
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link JwtVerifier} that validates JWT claims and, when constructed with
 * keys, the JWS signature.
 *
 * <h2>Signature mode</h2>
 * {@link #DefaultJwtVerifier(String, String, String, Map)} pins one algorithm
 * ({@code RS256} or {@code ES256}) and a set of trusted public keys indexed
 * by {@code kid}.  The header {@code alg} must equal the pinned algorithm
 * exactly (so {@code none}, {@code None} and {@code HS256} are refused, and a
 * public key can never be used as an HMAC secret), the {@code kid} must name
 * a configured key (a token with no {@code kid} is accepted only if exactly
 * one key is configured), the signature must be present and valid, and a
 * {@code crit} header is refused.  The signature is checked before the
 * payload is parsed.
 *
 * <h2>Claims-only mode</h2>
 * The constructors without keys check claims only.  A claims-only verifier
 * authenticates nothing: anyone can write a token it accepts.  It is suitable
 * only where another mechanism has already authenticated the sender.
 *
 * <h2>Checks performed</h2>
 * <ul>
 *   <li><b>{@code exp}</b> — required; token must not have expired (30-second
 *       clock-skew tolerance applied).  A token without {@code exp} never
 *       expires, which is not acceptable for a user-authentication
 *       credential.</li>
 *   <li><b>{@code nbf}</b> — if present, token must already be valid (30-second
 *       skew).</li>
 *   <li><b>{@code iat}</b> — if present, token must not have been issued in the
 *       future (30-second skew).</li>
 *   <li><b>{@code iss}</b> — if a non-null expected issuer was provided at
 *       construction time, the claim must match.</li>
 *   <li><b>{@code aud}</b> — if a non-null expected audience was provided at
 *       construction time, the claim must contain the expected value.</li>
 * </ul>
 *
 * <h2>No JWKS</h2>
 * Keys are supplied by the caller; this class performs no network fetch.
 *
 * <h2>Thread safety</h2>
 * Instances are immutable and safe for concurrent use by multiple threads.
 *
 * @since 3.1.1
 */
public final class DefaultJwtVerifier implements JwtVerifier {

    /** Clock-skew tolerance in seconds. */
    private static final long CLOCK_SKEW_SECONDS = 30L;

    /**
     * Maximum length of a JWT token string accepted by this verifier.
     * Tokens longer than this are rejected before any parsing.
     */
    static final int MAX_TOKEN_LENGTH = 65_536;

    private final String expectedIssuer;   // null → skip iss check
    private final String expectedAudience; // null → skip aud check
    private final String algorithm;        // null → claims-only mode
    private final Map<String, PublicKey> keys;

    /**
     * Constructs a {@code DefaultJwtVerifier} that performs only
     * expiry and issued-at checks (no issuer or audience check).
     */
    public DefaultJwtVerifier() {
        this(null, null);
    }

    /**
     * Constructs a {@code DefaultJwtVerifier} with optional issuer and
     * audience constraints.
     *
     * @param expectedIssuer    required value of the {@code iss} claim, or
     *                          {@code null} to skip the check
     * @param expectedAudience  required value (exact element match) of the
     *                          {@code aud} claim, or {@code null} to skip
     */
    public DefaultJwtVerifier(String expectedIssuer, String expectedAudience) {
        this(expectedIssuer, expectedAudience, null, null);
    }

    /**
     * Constructs a signature-verifying {@code DefaultJwtVerifier}.
     *
     * @param expectedIssuer   required {@code iss}, or {@code null} to skip
     * @param expectedAudience required {@code aud} element, or {@code null}
     * @param algorithm        the only accepted JWS algorithm: {@code RS256}
     *                         (RSA keys of at least 2048 bits) or
     *                         {@code ES256} (P-256 keys)
     * @param keysByKid        trusted public keys indexed by {@code kid};
     *                         must be non-empty and each key must suit
     *                         {@code algorithm}
     * @throws IllegalArgumentException if an argument is unusable, including
     *         an empty issuer or audience string
     */
    public DefaultJwtVerifier(String expectedIssuer, String expectedAudience,
                              String algorithm, Map<String, PublicKey> keysByKid) {
        if ("".equals(expectedIssuer) || "".equals(expectedAudience))
            throw new IllegalArgumentException("issuer/audience must not be empty");
        if ((algorithm == null) != (keysByKid == null))
            throw new IllegalArgumentException("algorithm and keys go together");
        if (algorithm != null) {
            if (!"RS256".equals(algorithm) && !"ES256".equals(algorithm))
                throw new IllegalArgumentException("unsupported algorithm");
            if (keysByKid.isEmpty())
                throw new IllegalArgumentException("no keys");
            for (PublicKey k : keysByKid.values()) {
                boolean ok = "RS256".equals(algorithm)
                    ? (k instanceof RSAPublicKey
                        && ((RSAPublicKey) k).getModulus().bitLength() >= 2048)
                    : (k instanceof ECPublicKey
                        && ((ECPublicKey) k).getParams().getCurve().getField()
                            .getFieldSize() == 256);
                if (!ok) throw new IllegalArgumentException("key unsuitable for " + algorithm);
            }
            this.keys = Map.copyOf(keysByKid);
        } else {
            this.keys = null;
        }
        this.expectedIssuer   = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.algorithm        = algorithm;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Parses the JWT payload (Base64url-decode + minimal JSON parse) and
     * validates {@code exp}, {@code iat}, {@code iss}, and {@code aud} claims
     * per the rules documented on the class.
     *
     * @throws JwtVerificationException if any claim check fails or the JWT
     *         cannot be parsed
     */
    @Override
    public void verify(String rawJwt) throws JwtVerificationException {
        if (rawJwt == null || rawJwt.isBlank())
            throw new JwtVerificationException("JWT token must not be null or blank");

        if (rawJwt.length() > MAX_TOKEN_LENGTH)
            throw new JwtVerificationException(
                    "JWT token exceeds maximum length of " + MAX_TOKEN_LENGTH + " characters");

        String[] parts = rawJwt.split("\\.", -1);
        if (parts.length != 3)
            throw new JwtVerificationException(
                    "JWT must have exactly 3 parts (header.payload.signature), got: "
                            + parts.length);

        if (algorithm != null) verifySignature(parts);

        Map<String, String> payload;
        try {
            payload = parseClaimsJson(
                    new String(Base64.getUrlDecoder().decode(parts[1]),
                               StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new JwtVerificationException(
                    "Failed to decode JWT payload: " + e.getMessage(), e);
        }

        try {
            validateClaims(payload);
        } catch (JwtVerificationException e) {
            throw e;
        } catch (RuntimeException e) {
            // e.g. an exp so large that Instant arithmetic overflows
            throw new JwtVerificationException("JWT claims out of range", e);
        }
    }

    private void verifySignature(String[] parts) throws JwtVerificationException {
        Map<String, String> header;
        byte[] sig;
        try {
            header = parseClaimsJson(new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            sig = parts[2].isEmpty() ? null : Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtVerificationException("JWT header or signature is not Base64url");
        }
        if (!algorithm.equals(header.get("alg")))
            throw new JwtVerificationException("JWT alg is not the configured algorithm");
        if (header.containsKey("crit"))
            throw new JwtVerificationException("JWT crit header is not supported");
        if (sig == null || sig.length == 0)
            throw new JwtVerificationException("JWT signature is missing");
        String kid = header.get("kid");
        PublicKey key = kid != null ? keys.get(kid)
                : (keys.size() == 1 ? keys.values().iterator().next() : null);
        if (key == null)
            throw new JwtVerificationException("JWT kid is unknown or missing");
        try {
            boolean es = "ES256".equals(algorithm);
            if (es) {
                sig = rawToDer(sig);
                if (sig == null)
                    throw new JwtVerificationException("JWT signature is malformed");
            }
            Signature v = Signature.getInstance(es ? "SHA256withECDSA" : "SHA256withRSA");
            v.initVerify(key);
            v.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!v.verify(sig))
                throw new JwtVerificationException("JWT signature is invalid");
        } catch (java.security.GeneralSecurityException e) {
            throw new JwtVerificationException("JWT signature is invalid");
        }
    }

    /** JWS ES256 carries R||S as 64 bytes; the JCA wants DER.  Null if not 64 bytes. */
    private static byte[] rawToDer(byte[] raw) {
        if (raw.length != 64) return null;
        byte[] r = derInt(raw, 0), s = derInt(raw, 32);
        byte[] out = new byte[2 + r.length + s.length];
        out[0] = 0x30;
        out[1] = (byte) (r.length + s.length);
        System.arraycopy(r, 0, out, 2, r.length);
        System.arraycopy(s, 0, out, 2 + r.length, s.length);
        return out;
    }

    private static byte[] derInt(byte[] raw, int off) {
        int start = off;
        while (start < off + 31 && raw[start] == 0) start++;
        boolean pad = (raw[start] & 0x80) != 0;
        int len = off + 32 - start;
        byte[] out = new byte[2 + (pad ? 1 : 0) + len];
        out[0] = 0x02;
        out[1] = (byte) (len + (pad ? 1 : 0));
        System.arraycopy(raw, start, out, 2 + (pad ? 1 : 0), len);
        return out;
    }

    // -------------------------------------------------------------------------

    private void validateClaims(Map<String, String> payload) throws JwtVerificationException {
        Instant now = Instant.now();

        // exp — token must not have expired
        String expStr = payload.get("exp");
        if (expStr == null)
            throw new JwtVerificationException("JWT has no 'exp' claim");
        {
            try {
                Instant exp = Instant.ofEpochSecond(Long.parseLong(expStr));
                if (now.isAfter(exp.plusSeconds(CLOCK_SKEW_SECONDS))) {
                    throw new JwtVerificationException(
                            "JWT has expired at " + exp
                                    + " (clock skew tolerance: " + CLOCK_SKEW_SECONDS + "s)");
                }
            } catch (NumberFormatException e) {
                throw new JwtVerificationException(
                        "JWT 'exp' claim is not a valid number: " + expStr, e);
            }
        }

        // nbf — token must already be valid
        String nbfStr = payload.get("nbf");
        if (nbfStr != null) {
            try {
                Instant nbf = Instant.ofEpochSecond(Long.parseLong(nbfStr));
                if (now.plusSeconds(CLOCK_SKEW_SECONDS).isBefore(nbf)) {
                    throw new JwtVerificationException("JWT is not yet valid");
                }
            } catch (NumberFormatException e) {
                throw new JwtVerificationException(
                        "JWT 'nbf' claim is not a valid number: " + nbfStr, e);
            }
        }

        // iat — token must not have been issued in the future
        String iatStr = payload.get("iat");
        if (iatStr != null) {
            try {
                Instant iat = Instant.ofEpochSecond(Long.parseLong(iatStr));
                if (now.plusSeconds(CLOCK_SKEW_SECONDS).isBefore(iat)) {
                    throw new JwtVerificationException(
                            "JWT issued-at (" + iat + ") is in the future");
                }
            } catch (NumberFormatException e) {
                throw new JwtVerificationException(
                        "JWT 'iat' claim is not a valid number: " + iatStr, e);
            }
        }

        // iss — must match configured issuer
        if (expectedIssuer != null) {
            String iss = payload.get("iss");
            if (!expectedIssuer.equals(iss))
                throw new JwtVerificationException(
                        "JWT issuer mismatch: expected '" + expectedIssuer
                                + "', got '" + iss + "'");
        }

        // aud — must contain configured audience
        if (expectedAudience != null) {
            String aud = payload.get("aud");
            if (aud == null || !containsExactAudience(aud, expectedAudience))
                throw new JwtVerificationException(
                        "JWT audience mismatch: expected '" + expectedAudience
                                + "', got '" + aud + "'");
        }
    }

    /**
     * Returns {@code true} if {@code rawAud} contains {@code expected} as an
     * exact element.  The {@code aud} claim may be a scalar string or a
     * comma-separated list (as produced by {@link #parseClaimsJson} for JSON
     * arrays).  This method handles both by splitting on {@code ,} and
     * comparing each trimmed token with {@link String#equals}.
     *
     * <p>Exact matching prevents false positives: {@code "example.com"} would
     * NOT match {@code "notexample.com"} even though it is a substring.
     */
    private static boolean containsExactAudience(String rawAud, String expected) {
        if (rawAud.equals(expected)) return true;
        for (String part : rawAud.split(",", -1)) {
            if (part.trim().equals(expected)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Minimal JSON claims parser — no external dependencies, JDK only.
    // Handles the flat object structure of a JWT payload; parses string scalar
    // values and arrays (joined with commas) sufficient for exp/iat/iss/aud.
    // -------------------------------------------------------------------------

    /**
     * Parses a flat JWT claims JSON object into a string map.
     * Numeric values are kept as their raw decimal string representation.
     * JSON arrays are stored as comma-joined strings under the claim key.
     */
    static Map<String, String> parseClaimsJson(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;

        int[] pos = {0};
        skipWhitespace(json, pos);
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '{') return result;
        pos[0]++; // consume '{'

        while (pos[0] < json.length()) {
            skipWhitespace(json, pos);
            if (pos[0] >= json.length()) break;
            char c = json.charAt(pos[0]);
            if (c == '}') break;
            if (c == ',') { pos[0]++; continue; }

            String key = readString(json, pos);
            if (key == null) break;
            skipWhitespace(json, pos);
            if (pos[0] >= json.length() || json.charAt(pos[0]) != ':') break;
            pos[0]++; // consume ':'
            skipWhitespace(json, pos);

            if (pos[0] >= json.length()) break;
            char v = json.charAt(pos[0]);

            if (v == '"') {
                String value = readString(json, pos);
                if (value != null) result.put(key, value);
            } else if (v == '[') {
                result.put(key, readStringArray(json, pos));
            } else {
                String raw = readRawScalar(json, pos);
                if (raw != null) result.put(key, raw);
            }
        }
        return result;
    }

    private static String readStringArray(String json, int[] pos) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '[') return "";
        pos[0]++; // consume '['
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        while (pos[0] < json.length()) {
            skipWhitespace(json, pos);
            if (pos[0] >= json.length()) break;
            char c = json.charAt(pos[0]);
            if (c == ']') { pos[0]++; break; }
            if (c == ',') { pos[0]++; continue; }
            String item = (c == '"') ? readString(json, pos) : readRawScalar(json, pos);
            if (item != null) {
                if (!first) sb.append(',');
                sb.append(item);
                first = false;
            }
        }
        return sb.toString();
    }

    private static String readRawScalar(String json, int[] pos) {
        int start = pos[0];
        while (pos[0] < json.length()) {
            char d = json.charAt(pos[0]);
            if (d == ',' || d == '}' || d == ']' || d == ' ' || d == '\t'
                    || d == '\r' || d == '\n') break;
            pos[0]++;
        }
        if (pos[0] > start) return json.substring(start, pos[0]);
        pos[0]++;
        return null;
    }

    private static String readString(String json, int[] pos) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '"') return null;
        pos[0]++; // consume opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]++);
            if (c == '"') return sb.toString();
            if (c == '\\' && pos[0] < json.length()) {
                char esc = json.charAt(pos[0]++);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (pos[0] + 4 <= json.length()) {
                            try {
                                int cp = Integer.parseInt(json, pos[0], pos[0] + 4, 16);
                                sb.append((char) cp);
                            } catch (NumberFormatException ignored) { }
                            pos[0] += 4;
                        }
                        break;
                    default: sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString(); // unterminated string — return what we have
    }

    private static void skipWhitespace(String json, int[] pos) {
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos[0]++;
            else break;
        }
    }
}
