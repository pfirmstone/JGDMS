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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link JwtVerifier} that validates JWT claims and, when constructed with
 * keys, the JWS signature, and returns the verified claims as a
 * {@link VerifiedJwt}.
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
 * payload is parsed.  Construction in this mode <b>requires</b> a non-empty
 * issuer and a non-empty audience: a token minted by the same issuer for
 * another service must not be accepted here.  The principals of the result
 * are derived from the signed claims ({@link VerifiedJwt}).
 *
 * <h2>Checks performed</h2>
 * <ul>
 *   <li>The header and payload must each be one well-formed JSON object with
 *       no duplicate member name.  Only top-level members are read; the
 *       members of nested objects are never promoted.</li>
 *   <li><b>{@code typ}</b> (header) &mdash; if present must be {@code JWT},
 *       compared ignoring case.</li>
 *   <li><b>{@code exp}</b> &mdash; required, a JSON number; the token must not
 *       have expired (30-second clock-skew tolerance) and must not expire
 *       more than the maximum lifetime (default {@link #DEFAULT_MAX_LIFETIME},
 *       1 hour) after now.</li>
 *   <li><b>{@code nbf}</b>, <b>{@code iat}</b> &mdash; if present must be JSON
 *       numbers and not in the future (30-second skew).</li>
 *   <li><b>{@code iss}</b> &mdash; must equal the configured issuer.</li>
 *   <li><b>{@code aud}</b> &mdash; the claim (a string or an array of strings)
 *       must contain the configured audience as an element; elements are
 *       compared whole and never split.</li>
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

    /** Default maximum time an accepted token may still have to live. */
    public static final Duration DEFAULT_MAX_LIFETIME = Duration.ofHours(1);

    /** Default maximum number of principals one accepted token may name. */
    public static final int DEFAULT_MAX_PRINCIPALS = 64;

    /** Clock-skew tolerance in seconds. */
    private static final long CLOCK_SKEW_SECONDS = 30L;

    /** Maximum nesting accepted while parsing a header or payload. */
    private static final int MAX_JSON_DEPTH = 16;

    /**
     * Maximum length of a JWT token string accepted by this verifier.
     * Tokens longer than this are rejected before any parsing.
     */
    static final int MAX_TOKEN_LENGTH = 65_536;

    private final String expectedIssuer;
    private final String expectedAudience;
    private final String algorithm;
    private final Map<String, PublicKey> keys;
    private final Duration maxLifetime;
    private final int maxPrincipals;

    /**
     * Constructs a signature-verifying {@code DefaultJwtVerifier} with the
     * default maximum lifetime.
     *
     * @param expectedIssuer   required {@code iss}; not null or empty
     * @param expectedAudience required {@code aud} element; not null or empty
     * @param algorithm        the only accepted JWS algorithm: {@code RS256}
     *                         (RSA keys of at least 2048 bits) or
     *                         {@code ES256} (P-256 keys)
     * @param keysByKid        trusted public keys indexed by {@code kid};
     *                         must be non-empty and each key must suit
     *                         {@code algorithm}
     * @throws IllegalArgumentException if an argument is unusable, including
     *         a null or empty issuer or audience
     */
    public DefaultJwtVerifier(String expectedIssuer, String expectedAudience,
                              String algorithm, Map<String, PublicKey> keysByKid) {
        this(expectedIssuer, expectedAudience, algorithm, keysByKid, DEFAULT_MAX_LIFETIME);
    }

    /**
     * Constructs a signature-verifying {@code DefaultJwtVerifier}.
     *
     * @param expectedIssuer   required {@code iss}; not null or empty
     * @param expectedAudience required {@code aud} element; not null or empty
     * @param algorithm        {@code RS256} or {@code ES256}
     * @param keysByKid        trusted public keys indexed by {@code kid}
     * @param maxLifetime      the longest time after now that an accepted
     *                         token's {@code exp} may lie; positive
     * @throws IllegalArgumentException if an argument is unusable
     */
    public DefaultJwtVerifier(String expectedIssuer, String expectedAudience,
                              String algorithm, Map<String, PublicKey> keysByKid,
                              Duration maxLifetime) {
        this(expectedIssuer, expectedAudience, algorithm, keysByKid, maxLifetime,
             DEFAULT_MAX_PRINCIPALS);
    }

    /**
     * Constructs a signature-verifying {@code DefaultJwtVerifier}.
     *
     * @param maxPrincipals the most principals (one for {@code sub}, one for
     *                      a verified {@code email}, one per element of
     *                      {@code groups}) a token may name; a token naming
     *                      more is refused.  Positive.
     * @see #DEFAULT_MAX_PRINCIPALS
     */
    public DefaultJwtVerifier(String expectedIssuer, String expectedAudience,
                              String algorithm, Map<String, PublicKey> keysByKid,
                              Duration maxLifetime, int maxPrincipals) {
        if (maxPrincipals <= 0)
            throw new IllegalArgumentException("maxPrincipals must be positive");
        this.maxPrincipals = maxPrincipals;
        if ("".equals(expectedIssuer) || "".equals(expectedAudience))
            throw new IllegalArgumentException("issuer/audience must not be empty");
        if (maxLifetime == null || maxLifetime.isZero() || maxLifetime.isNegative())
            throw new IllegalArgumentException("maxLifetime must be positive");
        if (algorithm == null || keysByKid == null)
            throw new IllegalArgumentException("algorithm and keys are required");
        if (expectedIssuer == null || expectedAudience == null)
            throw new IllegalArgumentException(
                "an expected issuer and audience are required");
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
        this.expectedIssuer   = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.algorithm        = algorithm;
        this.maxLifetime      = maxLifetime;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The principals of the result are derived from the signed claims.
     *
     * @throws JwtVerificationException if any check fails or the JWT cannot
     *         be parsed
     */
    @Override
    public VerifiedJwt verify(String rawJwt) throws JwtVerificationException {
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

        Map<String, Object> header = decodeObject(parts[0], "header");
        verifySignature(header, parts);
        checkType(header);

        Map<String, Object> payload = decodeObject(parts[1], "payload");
        try {
            return validateClaims(payload);
        } catch (JwtVerificationException e) {
            throw e;
        } catch (RuntimeException e) {
            // e.g. an exp so large that Instant arithmetic overflows
            throw new JwtVerificationException("JWT claims out of range", e);
        }
    }

    private static Map<String, Object> decodeObject(String part, String what)
            throws JwtVerificationException {
        try {
            return parseObject(new String(Base64.getUrlDecoder().decode(part),
                                          StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new JwtVerificationException("JWT " + what + " is not valid: " + e.getMessage());
        }
    }

    private static void checkType(Map<String, Object> header) throws JwtVerificationException {
        if (!header.containsKey("typ")) return;
        Object typ = header.get("typ");
        if (!(typ instanceof String) || !"JWT".equalsIgnoreCase((String) typ))
            throw new JwtVerificationException("JWT typ header is not JWT");
    }

    private void verifySignature(Map<String, Object> header, String[] parts)
            throws JwtVerificationException {
        byte[] sig;
        try {
            sig = parts[2].isEmpty() ? null : Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtVerificationException("JWT signature is not Base64url");
        }
        if (!algorithm.equals(header.get("alg")))
            throw new JwtVerificationException("JWT alg is not the configured algorithm");
        if (header.containsKey("crit"))
            throw new JwtVerificationException("JWT crit header is not supported");
        if (sig == null || sig.length == 0)
            throw new JwtVerificationException("JWT signature is missing");
        Object kidObj = header.get("kid");
        if (kidObj != null && !(kidObj instanceof String))
            throw new JwtVerificationException("JWT kid is not a string");
        String kid = (String) kidObj;
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

    private VerifiedJwt validateClaims(Map<String, Object> payload) throws JwtVerificationException {
        Instant now = Instant.now();

        Instant exp = timeClaim(payload, "exp", true);
        if (now.isAfter(exp.plusSeconds(CLOCK_SKEW_SECONDS))) {
            throw new JwtVerificationException(
                    "JWT has expired at " + exp
                            + " (clock skew tolerance: " + CLOCK_SKEW_SECONDS + "s)");
        }
        if (exp.isAfter(now.plus(maxLifetime).plusSeconds(CLOCK_SKEW_SECONDS))) {
            throw new JwtVerificationException(
                    "JWT exp is more than the maximum lifetime of " + maxLifetime + " away");
        }

        Instant nbf = timeClaim(payload, "nbf", false);
        if (nbf != null && now.plusSeconds(CLOCK_SKEW_SECONDS).isBefore(nbf)) {
            throw new JwtVerificationException("JWT is not yet valid");
        }

        Instant iat = timeClaim(payload, "iat", false);
        if (iat != null && now.plusSeconds(CLOCK_SKEW_SECONDS).isBefore(iat)) {
            throw new JwtVerificationException("JWT issued-at (" + iat + ") is in the future");
        }

        String iss = stringClaim(payload, "iss");
        if (!expectedIssuer.equals(iss))
            throw new JwtVerificationException(
                    "JWT issuer mismatch: expected '" + expectedIssuer
                            + "', got '" + iss + "'");

        List<String> aud = audienceClaim(payload);
        if (!aud.contains(expectedAudience))
            throw new JwtVerificationException(
                    "JWT audience mismatch: expected '" + expectedAudience
                            + "', got " + aud);

        String sub = stringClaim(payload, "sub");
        if (sub != null && sub.isEmpty())
            throw new JwtVerificationException("JWT 'sub' claim is empty");
        return identityFromClaims(payload, maxPrincipals);
    }

    /**
     * The one mapping from the top-level members of an authenticated payload
     * to a {@link VerifiedJwt}; the server verifier and the client login
     * module both call it, so what a client sends is what the server derives.
     * It checks only the shape of {@code exp}, {@code iss}, {@code aud},
     * {@code sub}, {@code email}, {@code email_verified} and {@code groups};
     * the caller checks signature, time and expected issuer/audience.
     *
     * @param payload       the map returned by {@link #parseObject}
     * @param maxPrincipals the most principals the token may name
     * @return the claims and the principals derived from them
     * @throws JwtVerificationException if a claim has the wrong type, is
     *         empty, or the token names more than {@code maxPrincipals}
     */
    public static VerifiedJwt identityFromClaims(Map<String, Object> payload, int maxPrincipals)
            throws JwtVerificationException {
        if (maxPrincipals <= 0)
            throw new IllegalArgumentException("maxPrincipals must be positive");
        Instant exp = timeClaim(payload, "exp", true);
        String iss = stringClaim(payload, "iss");
        List<String> aud = audienceClaim(payload);
        String sub = stringClaim(payload, "sub");
        if (sub != null && sub.isEmpty())
            throw new JwtVerificationException("JWT 'sub' claim is empty");
        String email = stringClaim(payload, "email");
        if (email != null && email.isEmpty())
            throw new JwtVerificationException("JWT 'email' claim is empty");
        // Only a JSON true counts; the string "true", 1 and absence do not.
        boolean emailVerified = Boolean.TRUE.equals(payload.get("email_verified"));
        List<String> groups = stringList(payload, "groups");
        for (String g : groups) {
            if (g.isEmpty())
                throw new JwtVerificationException("JWT 'groups' claim has an empty element");
        }
        long count = (long) groups.size() + (sub != null ? 1 : 0)
                + (emailVerified && email != null ? 1 : 0);
        if (count > maxPrincipals)
            throw new JwtVerificationException(
                "JWT names " + count + " principals, more than the limit of " + maxPrincipals);
        return new VerifiedJwt(sub, iss, aud, exp, email, emailVerified, groups);
    }

    private static Instant timeClaim(Map<String, Object> payload, String name, boolean required)
            throws JwtVerificationException {
        Object v = payload.get(name);
        if (v == null) {
            if (required) throw new JwtVerificationException("JWT has no '" + name + "' claim");
            return null;
        }
        if (!(v instanceof BigDecimal))
            throw new JwtVerificationException("JWT '" + name + "' claim is not a number");
        BigDecimal n = (BigDecimal) v;
        // Bound the magnitude before setScale: "1e999999999" would otherwise
        // allocate a gigantic BigInteger.
        if (n.precision() - n.scale() > 19)
            throw new JwtVerificationException("JWT '" + name + "' claim is out of range");
        try {
            return Instant.ofEpochSecond(n.setScale(0, java.math.RoundingMode.FLOOR)
                                          .longValueExact());
        } catch (ArithmeticException | java.time.DateTimeException e) {
            throw new JwtVerificationException("JWT '" + name + "' claim is out of range");
        }
    }

    private static String stringClaim(Map<String, Object> payload, String name)
            throws JwtVerificationException {
        Object v = payload.get(name);
        if (v == null) return null;
        if (!(v instanceof String))
            throw new JwtVerificationException("JWT '" + name + "' claim is not a string");
        return (String) v;
    }

    /** {@code aud} is a string (a list of one) or an array of strings. */
    private static List<String> audienceClaim(Map<String, Object> payload)
            throws JwtVerificationException {
        Object v = payload.get("aud");
        if (v == null) return List.of();
        if (v instanceof String) return List.of((String) v);
        return stringList(payload, "aud");
    }

    private static List<String> stringList(Map<String, Object> payload, String name)
            throws JwtVerificationException {
        Object v = payload.get(name);
        if (v == null) return List.of();
        if (!(v instanceof List))
            throw new JwtVerificationException("JWT '" + name + "' claim is not an array");
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) v) {
            if (!(o instanceof String))
                throw new JwtVerificationException(
                        "JWT '" + name + "' claim has an element that is not a string");
            out.add((String) o);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Strict JSON object parser, JDK only.  Values: String, BigDecimal,
    // Boolean, NULL (JSON null), List<Object>, Map<String,Object>.  Only the
    // top-level map is consulted by this class; nested values are parsed so
    // that they are skipped correctly and never lifted.  A duplicate member
    // name in any object, trailing text, or malformed input is an error.
    // -------------------------------------------------------------------------

    private static final Object NULL = new Object();

    /**
     * Parses a JSON object into a map of its top-level members; a member
     * whose value is JSON {@code null} is treated as absent.
     *
     * @throws IllegalArgumentException if the text is not one JSON object
     */
    public static Map<String, Object> parseObject(String json) {
        int[] pos = {0};
        skipWhitespace(json, pos);
        Object v = parseValue(json, pos, 0);
        skipWhitespace(json, pos);
        if (pos[0] != json.length() || !(v instanceof Map))
            throw new IllegalArgumentException("not a single JSON object");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        // A null member is absent, except 'crit': a null crit must still trip the
        // crit refusal, not be read as no crit header.
        m.entrySet().removeIf(e -> e.getValue() == NULL && !"crit".equals(e.getKey()));
        return m;
    }

    private static Object parseValue(String json, int[] pos, int depth) {
        if (depth > MAX_JSON_DEPTH) throw new IllegalArgumentException("JSON nested too deeply");
        char c = peek(json, pos);
        switch (c) {
            case '{': return parseMap(json, pos, depth);
            case '[': return parseList(json, pos, depth);
            case '"': return readString(json, pos);
            case 't': return literal(json, pos, "true", Boolean.TRUE);
            case 'f': return literal(json, pos, "false", Boolean.FALSE);
            case 'n': return literal(json, pos, "null", NULL);
            default:  return readNumber(json, pos);
        }
    }

    private static Map<String, Object> parseMap(String json, int[] pos, int depth) {
        pos[0]++; // '{'
        Map<String, Object> out = new LinkedHashMap<>();
        skipWhitespace(json, pos);
        if (peek(json, pos) == '}') { pos[0]++; return out; }
        while (true) {
            skipWhitespace(json, pos);
            if (peek(json, pos) != '"') throw new IllegalArgumentException("expected member name");
            String key = readString(json, pos);
            skipWhitespace(json, pos);
            if (peek(json, pos) != ':') throw new IllegalArgumentException("expected ':'");
            pos[0]++;
            skipWhitespace(json, pos);
            Object val = parseValue(json, pos, depth + 1);
            if (out.containsKey(key))
                throw new IllegalArgumentException("duplicate member '" + key + "'");
            out.put(key, val);
            skipWhitespace(json, pos);
            char d = peek(json, pos);
            pos[0]++;
            if (d == '}') return out;
            if (d != ',') throw new IllegalArgumentException("expected ',' or '}'");
        }
    }

    private static List<Object> parseList(String json, int[] pos, int depth) {
        pos[0]++; // '['
        List<Object> out = new ArrayList<>();
        skipWhitespace(json, pos);
        if (peek(json, pos) == ']') { pos[0]++; return out; }
        while (true) {
            skipWhitespace(json, pos);
            out.add(parseValue(json, pos, depth + 1));
            skipWhitespace(json, pos);
            char d = peek(json, pos);
            pos[0]++;
            if (d == ']') return out;
            if (d != ',') throw new IllegalArgumentException("expected ',' or ']'");
        }
    }

    private static char peek(String json, int[] pos) {
        if (pos[0] >= json.length()) throw new IllegalArgumentException("unexpected end of JSON");
        return json.charAt(pos[0]);
    }

    private static Object literal(String json, int[] pos, String word, Object value) {
        if (!json.startsWith(word, pos[0])) throw new IllegalArgumentException("bad literal");
        pos[0] += word.length();
        return value;
    }

    /** RFC 8259 number: -? (0 | [1-9][0-9]*) (. [0-9]+)? ([eE] [+-]? [0-9]+)? */
    private static Object readNumber(String json, int[] pos) {
        int start = pos[0];
        int i = start;
        int n = json.length();
        if (i < n && json.charAt(i) == '-') i++;
        if (i >= n) throw new IllegalArgumentException("bad number");
        if (json.charAt(i) == '0') {
            i++;
        } else if (json.charAt(i) >= '1' && json.charAt(i) <= '9') {
            while (i < n && isDigit(json.charAt(i))) i++;
        } else {
            throw new IllegalArgumentException("unexpected character");
        }
        if (i < n && json.charAt(i) == '.') {
            i++;
            int d = i;
            while (i < n && isDigit(json.charAt(i))) i++;
            if (i == d) throw new IllegalArgumentException("bad number");
        }
        if (i < n && (json.charAt(i) == 'e' || json.charAt(i) == 'E')) {
            i++;
            if (i < n && (json.charAt(i) == '+' || json.charAt(i) == '-')) i++;
            int d = i;
            while (i < n && isDigit(json.charAt(i))) i++;
            if (i == d) throw new IllegalArgumentException("bad number");
        }
        pos[0] = i;
        try {
            return new BigDecimal(json.substring(start, i));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number");
        }
    }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    /** Exactly four ASCII hex digits at {@code at}; no sign, no other character. */
    private static char readHex4(String json, int at) {
        if (at + 4 > json.length()) throw new IllegalArgumentException("bad \\u escape");
        int v = 0;
        for (int k = at; k < at + 4; k++) {
            char h = json.charAt(k);
            int d;
            if (h >= '0' && h <= '9') d = h - '0';
            else if (h >= 'a' && h <= 'f') d = h - 'a' + 10;
            else if (h >= 'A' && h <= 'F') d = h - 'A' + 10;
            else throw new IllegalArgumentException("bad \\u escape");
            v = v * 16 + d;
        }
        return (char) v;
    }

    private static String readString(String json, int[] pos) {
        pos[0]++; // opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]++);
            if (c == '"') return sb.toString();
            if (c < 0x20) throw new IllegalArgumentException("control character in string");
            if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("lone surrogate");
            if (Character.isHighSurrogate(c)) {
                if (pos[0] >= json.length() || !Character.isLowSurrogate(json.charAt(pos[0])))
                    throw new IllegalArgumentException("lone surrogate");
                sb.append(c).append(json.charAt(pos[0]++));
                continue;
            }
            if (c == '\\') {
                if (pos[0] >= json.length()) break;
                char esc = json.charAt(pos[0]++);
                switch (esc) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u': {
                        char u = readHex4(json, pos[0]);
                        pos[0] += 4;
                        if (Character.isLowSurrogate(u))
                            throw new IllegalArgumentException("lone surrogate");
                        if (Character.isHighSurrogate(u)) {
                            // must be followed by an escaped low surrogate
                            if (!json.startsWith("\\u", pos[0]))
                                throw new IllegalArgumentException("lone surrogate");
                            char lo = readHex4(json, pos[0] + 2);
                            if (!Character.isLowSurrogate(lo))
                                throw new IllegalArgumentException("lone surrogate");
                            pos[0] += 6;
                            sb.append(u).append(lo);
                        } else {
                            sb.append(u);
                        }
                        break;
                    }
                    default: throw new IllegalArgumentException("bad escape");
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private static void skipWhitespace(String json, int[] pos) {
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos[0]++;
            else break;
        }
    }
}
