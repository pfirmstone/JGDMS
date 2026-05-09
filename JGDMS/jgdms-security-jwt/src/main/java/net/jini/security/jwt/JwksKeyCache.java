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

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.EllipticCurve;
import java.security.spec.ECFieldFp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches a JWKS (JSON Web Key Set) from an HTTPS URI, caches the public keys
 * by {@code kid}, and refreshes on cache miss (key rotation) or TTL expiry.
 *
 * <p>Only RSA and EC public keys are stored; symmetric ({@code kty=oct}) keys
 * are logged at WARNING level and discarded.  HTTPS is required; plain HTTP
 * URIs throw {@link IllegalArgumentException}.
 *
 * <p>This class is package-private and is used by {@link JwtValidator}.
 *
 * @since 3.1.1
 */
final class JwksKeyCache {

    private static final Logger LOG = Logger.getLogger(JwksKeyCache.class.getName());

    /** Default TTL for the JWKS cache (1 hour). */
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    /**
     * Maximum JWKS HTTP response body size in bytes (1 MiB).
     * Responses larger than this are rejected to prevent memory exhaustion.
     */
    static final int MAX_JWKS_RESPONSE_BYTES = 1 * 1024 * 1024;

    /**
     * Maximum length (in characters) of any single JSON string value.
     * Rejects oversized Base64 key material or claim values that would
     * exhaust heap memory.  RSA-8192 moduli Base64-encode to ≈ 1 366 chars,
     * so 8 192 chars is well above any legitimate value.
     */
    static final int MAX_JSON_STRING_LENGTH = 8192;

    /**
     * Maximum number of public keys accepted from a single JWKS document.
     * Prevents memory exhaustion from a JWKS with pathologically many keys.
     */
    static final int MAX_JWKS_KEYS = 100;

    /**
     * Maximum RSA key modulus size in bytes (= 8 192-bit RSA).
     * Keys with a larger modulus are rejected to prevent CPU/memory exhaustion.
     */
    static final int MAX_RSA_MODULUS_BYTES = 1024;

    /** Maximum RSA key modulus size in bits, derived from {@link #MAX_RSA_MODULUS_BYTES}. */
    private static final int MAX_RSA_MODULUS_BITS = MAX_RSA_MODULUS_BYTES * 8;

    private final URI jwksUri;
    private final HttpClient httpClient;
    private final Duration ttl;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Map<String, PublicKey> cache = Collections.emptyMap();
    private Instant cacheExpiry = Instant.MIN;

    /**
     * Constructs a {@code JwksKeyCache} for the given HTTPS JWKS URI.
     *
     * @param jwksUri the JWKS endpoint URI; must use the {@code https} scheme
     * @throws IllegalArgumentException if the URI does not use the {@code https} scheme
     * @throws NullPointerException     if {@code jwksUri} is null
     */
    JwksKeyCache(URI jwksUri) {
        this(jwksUri, DEFAULT_TTL);
    }

    /**
     * Constructs a {@code JwksKeyCache} with a custom TTL.
     *
     * @param jwksUri the JWKS endpoint URI; must use the {@code https} scheme
     * @param ttl     cache time-to-live; must be positive
     * @throws IllegalArgumentException if the URI does not use the {@code https} scheme
     */
    JwksKeyCache(URI jwksUri, Duration ttl) {
        if (jwksUri == null) throw new NullPointerException("jwksUri");
        if (!"https".equalsIgnoreCase(jwksUri.getScheme()))
            throw new IllegalArgumentException(
                    "JWKS URI must use https scheme, got: " + jwksUri);
        this.jwksUri = jwksUri;
        this.ttl = ttl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Returns the public key for the given {@code kid}, fetching from the
     * JWKS endpoint if the cache is empty or expired.
     *
     * @param kid the key ID to look up; may be null (returns null)
     * @return the public key, or {@code null} if not found in the current cache
     */
    PublicKey getKey(String kid) {
        if (kid == null) return null;
        ensureFresh();
        lock.readLock().lock();
        try {
            return cache.get(kid);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Forces an immediate refresh of the JWKS cache from the remote endpoint.
     * Concurrent refresh calls are serialised; only the first caller fetches,
     * subsequent callers wait and then read the updated cache.
     */
    void refresh() {
        lock.writeLock().lock();
        try {
            fetchAndUpdate();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -----------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------

    private void ensureFresh() {
        // Fast path: cache is valid
        lock.readLock().lock();
        try {
            if (Instant.now().isBefore(cacheExpiry)) return;
        } finally {
            lock.readLock().unlock();
        }
        // Slow path: refresh under write lock
        lock.writeLock().lock();
        try {
            if (Instant.now().isBefore(cacheExpiry)) return; // double-check
            fetchAndUpdate();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Must be called while holding the write lock. */
    private void fetchAndUpdate() {
        try {
            HttpRequest request = HttpRequest.newBuilder(jwksUri)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .build();
            // Use InputStream to enforce a hard response-size limit before
            // allocating a large String, defending against memory-exhaustion.
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try { response.body().close(); } catch (Exception ignore) {}
                LOG.warning("JWKS fetch returned HTTP " + response.statusCode()
                        + " from " + jwksUri);
                return;
            }
            String body;
            try (InputStream is = response.body()) {
                byte[] bytes = is.readNBytes(MAX_JWKS_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_JWKS_RESPONSE_BYTES) {
                    LOG.warning("JWKS response from " + jwksUri + " exceeds size limit of "
                            + MAX_JWKS_RESPONSE_BYTES + " bytes; ignoring");
                    return;
                }
                body = new String(bytes, StandardCharsets.UTF_8);
            }
            Map<String, PublicKey> newCache = parseJwks(body);
            cache = Collections.unmodifiableMap(newCache);
            cacheExpiry = Instant.now().plus(ttl);
            LOG.fine("JWKS cache refreshed: " + newCache.size() + " key(s) from " + jwksUri);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "JWKS fetch interrupted", e);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch JWKS from " + jwksUri, e);
        }
    }

    /**
     * Parses a JWKS JSON body and returns a map of kid → PublicKey.
     * Uses a hand-rolled parser to avoid third-party JSON library dependencies.
     */
    private static Map<String, PublicKey> parseJwks(String json) {
        Map<String, PublicKey> result = new HashMap<>();
        List<Map<String, String>> keys = parseJwksJson(json);
        for (Map<String, String> jwk : keys) {
            String kty = jwk.get("kty");
            String kid = jwk.get("kid");
            if (kty == null) {
                LOG.warning("Skipping JWK with missing 'kty' field");
                continue;
            }
            try {
                PublicKey key = buildPublicKey(kty, jwk);
                if (key != null) {
                    if (kid != null) {
                        result.put(kid, key);
                    } else {
                        LOG.fine("JWK has no 'kid' field; skipping (kid is required for lookup)");
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Skipping JWK kid=" + kid + " kty=" + kty
                        + " due to key construction failure", e);
            }
        }
        return result;
    }

    private static PublicKey buildPublicKey(String kty, Map<String, String> jwk) throws Exception {
        switch (kty) {
            case "RSA": {
                String nB64 = jwk.get("n");
                String eB64 = jwk.get("e");
                if (nB64 == null || eB64 == null) {
                    LOG.warning("RSA JWK missing 'n' or 'e' field; skipping");
                    return null;
                }
                byte[] nBytes = Base64.getUrlDecoder().decode(nB64);
                if (nBytes.length > MAX_RSA_MODULUS_BYTES) {
                    LOG.warning("RSA JWK modulus exceeds maximum of " + MAX_RSA_MODULUS_BYTES
                            + " bytes (" + MAX_RSA_MODULUS_BITS + " bits); skipping");
                    return null;
                }
                byte[] eBytes = Base64.getUrlDecoder().decode(eB64);
                BigInteger modulus  = new BigInteger(1, nBytes);
                BigInteger exponent = new BigInteger(1, eBytes);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePublic(new RSAPublicKeySpec(modulus, exponent));
            }
            case "EC": {
                String crv = jwk.get("crv");
                String xB64 = jwk.get("x");
                String yB64 = jwk.get("y");
                if (crv == null || xB64 == null || yB64 == null) {
                    LOG.warning("EC JWK missing 'crv', 'x', or 'y' field; skipping");
                    return null;
                }
                ECParameterSpec params = ecParams(crv);
                if (params == null) {
                    LOG.warning("Unsupported EC curve: " + crv + "; skipping");
                    return null;
                }
                byte[] xBytes = Base64.getUrlDecoder().decode(xB64);
                byte[] yBytes = Base64.getUrlDecoder().decode(yB64);
                BigInteger x = new BigInteger(1, xBytes);
                BigInteger y = new BigInteger(1, yBytes);
                ECPoint point = new ECPoint(x, y);
                KeyFactory kf = KeyFactory.getInstance("EC");
                return kf.generatePublic(new ECPublicKeySpec(point, params));
            }
            case "oct":
                LOG.warning("Symmetric key (kty=oct) rejected; only asymmetric keys "
                        + "are supported for distributed JWT verification");
                return null;
            default:
                LOG.warning("Unknown key type: " + kty + "; skipping");
                return null;
        }
    }

    /**
     * Returns the JCA {@link ECParameterSpec} for the named NIST curve, or
     * {@code null} if the curve is not supported.
     */
    private static ECParameterSpec ecParams(String crv) {
        // Use SunEC's named-curve facility via AlgorithmParameters
        try {
            java.security.AlgorithmParameters ap =
                    java.security.AlgorithmParameters.getInstance("EC");
            String oid;
            switch (crv) {
                case "P-256": oid = "secp256r1"; break;
                case "P-384": oid = "secp384r1"; break;
                case "P-521": oid = "secp521r1"; break;
                default: return null;
            }
            ap.init(new java.security.spec.ECGenParameterSpec(oid));
            return ap.getParameterSpec(ECParameterSpec.class);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to obtain EC params for curve " + crv, e);
            return null;
        }
    }

    // -----------------------------------------------------------------
    // Minimal hand-rolled JSON parser for JWKS structure
    // -----------------------------------------------------------------

    /**
     * Parses a JWKS JSON document and returns the list of key objects.
     * Only handles the flat JWKS structure; does not support nested objects or
     * arrays as field values (other than the top-level "keys" array itself).
     */
    static List<Map<String, String>> parseJwksJson(String json) {
        List<Map<String, String>> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        int[] pos = {0};
        skipWhitespace(json, pos);
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '{') return result;
        pos[0]++; // consume '{'

        // Find the "keys" field
        while (pos[0] < json.length()) {
            skipWhitespace(json, pos);
            if (pos[0] >= json.length()) break;
            char c = json.charAt(pos[0]);
            if (c == '}') break;
            if (c == ',') { pos[0]++; continue; }

            // Read key name
            String fieldName = readString(json, pos);
            if (fieldName == null) break;
            skipWhitespace(json, pos);
            if (pos[0] >= json.length() || json.charAt(pos[0]) != ':') break;
            pos[0]++; // consume ':'
            skipWhitespace(json, pos);

            if ("keys".equals(fieldName)) {
                // Parse the array of key objects
                if (pos[0] < json.length() && json.charAt(pos[0]) == '[') {
                    pos[0]++; // consume '['
                    while (pos[0] < json.length()) {
                        skipWhitespace(json, pos);
                        if (pos[0] >= json.length()) break;
                        char d = json.charAt(pos[0]);
                        if (d == ']') { pos[0]++; break; }
                        if (d == ',') { pos[0]++; continue; }
                        if (d == '{') {
                                Map<String, String> jwk = readStringObject(json, pos);
                                if (jwk != null) {
                                    if (result.size() >= MAX_JWKS_KEYS) {
                                        LOG.warning("JWKS contains more than " + MAX_JWKS_KEYS
                                                + " keys; ignoring remaining keys");
                                        // Skip to the closing ']' of the keys array.
                                        // Track depth so nested '[' or '{' within any
                                        // remaining key objects do not confuse the scan.
                                        int depth = 1; // we are inside the '['
                                        while (pos[0] < json.length() && depth > 0) {
                                            char s = json.charAt(pos[0]);
                                            if (s == '"') {
                                                readString(json, pos); // skip quoted value
                                            } else {
                                                pos[0]++;
                                                if (s == '[' || s == '{') depth++;
                                                else if (s == ']') { if (--depth == 0) break; }
                                                else if (s == '}') depth--;
                                            }
                                        }
                                        break;
                                    }
                                    result.add(jwk);
                                }
                        } else {
                            // Skip unexpected token
                            pos[0]++;
                        }
                    }
                }
            } else {
                // Skip value for other fields
                skipValue(json, pos);
            }
        }
        return result;
    }

    /**
     * Reads a JSON object whose values are all strings (or can be treated as
     * strings).  Arrays and nested objects within the key object are skipped.
     */
    private static Map<String, String> readStringObject(String json, int[] pos) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '{') return null;
        pos[0]++; // consume '{'
        Map<String, String> map = new HashMap<>();
        while (pos[0] < json.length()) {
            skipWhitespace(json, pos);
            if (pos[0] >= json.length()) break;
            char c = json.charAt(pos[0]);
            if (c == '}') { pos[0]++; break; }
            if (c == ',') { pos[0]++; continue; }

            String key = readString(json, pos);
            if (key == null) { skipToEndOfObject(json, pos); return map; }
            skipWhitespace(json, pos);
            if (pos[0] >= json.length() || json.charAt(pos[0]) != ':') break;
            pos[0]++; // consume ':'
            skipWhitespace(json, pos);

            if (pos[0] < json.length() && json.charAt(pos[0]) == '"') {
                String value = readString(json, pos);
                if (value != null) map.put(key, value);
            } else {
                // Non-string value (number, boolean, array, nested object): skip
                skipValue(json, pos);
            }
        }
        return map;
    }

    /** Reads a JSON string starting at pos[0] (which must point at the opening '"'). */
    static String readString(String json, int[] pos) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '"') return null;
        pos[0]++; // consume opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]++);
            if (c == '"') return sb.toString();
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
                    case 'u':
                        if (pos[0] + 4 <= json.length()) {
                            String hex = json.substring(pos[0], pos[0] + 4);
                            pos[0] += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        break;
                    default:   sb.append(esc); break;
                }
            } else {
                sb.append(c);
            }
            if (sb.length() > MAX_JSON_STRING_LENGTH)
                throw new IllegalStateException(
                        "JSON string value exceeds maximum length of "
                        + MAX_JSON_STRING_LENGTH + " characters");
        }
        return sb.toString();
    }

    /** Skips whitespace characters. */
    static void skipWhitespace(String json, int[] pos) {
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos[0]++;
            else break;
        }
    }

    /** Skips a JSON value (string, number, boolean, null, object, or array). */
    private static void skipValue(String json, int[] pos) {
        if (pos[0] >= json.length()) return;
        char c = json.charAt(pos[0]);
        if (c == '"') {
            readString(json, pos); // consume it
        } else if (c == '{') {
            skipToEndOfObject(json, pos);
        } else if (c == '[') {
            skipToEndOfArray(json, pos);
        } else {
            // number, boolean, null
            while (pos[0] < json.length()) {
                char d = json.charAt(pos[0]);
                if (d == ',' || d == '}' || d == ']') break;
                pos[0]++;
            }
        }
    }

    private static void skipToEndOfObject(String json, int[] pos) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '{') return;
        pos[0]++;
        int depth = 1;
        while (pos[0] < json.length() && depth > 0) {
            char c = json.charAt(pos[0]++);
            if (c == '"') { pos[0]--; readString(json, pos); }
            else if (c == '{') depth++;
            else if (c == '}') depth--;
        }
    }

    private static void skipToEndOfArray(String json, int[] pos) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != '[') return;
        pos[0]++;
        int depth = 1;
        while (pos[0] < json.length() && depth > 0) {
            char c = json.charAt(pos[0]++);
            if (c == '"') { pos[0]--; readString(json, pos); }
            else if (c == '[') depth++;
            else if (c == ']') depth--;
        }
    }
}
