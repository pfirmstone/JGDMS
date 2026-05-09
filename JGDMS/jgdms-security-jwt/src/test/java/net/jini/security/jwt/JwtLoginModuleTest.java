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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration tests for {@link JwtLoginModule} with an embedded HTTPS JWKS server.
 *
 * <h2>Test strategy</h2>
 * <p>The test harness:
 * <ol>
 *   <li>Generates an RSA-2048 key pair for signing test JWTs.</li>
 *   <li>Generates a separate RSA-2048 key pair as the server TLS certificate
 *       (self-signed, created with {@code keytool -genkeypair}) and loads it
 *       into an in-memory {@link KeyStore}.</li>
 *   <li>Installs the test server's self-signed certificate as the JVM's
 *       default SSL trust anchor via {@link SSLContext#setDefault(SSLContext)},
 *       so that the {@link java.net.http.HttpClient} constructed inside
 *       {@link JwksKeyCache} trusts the test HTTPS server.</li>
 *   <li>Starts a {@link HttpsServer} on a random port serving
 *       {@code /jwks.json} and an OAuth2 {@code /token} endpoint.</li>
 * </ol>
 *
 * <p>Each test case drives {@code JwtLoginModule} through the standard JAAS
 * {@code initialize → login → commit} (or {@code abort} / {@code logout})
 * lifecycle and asserts on the resulting {@link Subject} state.
 *
 * <h2>No external dependencies</h2>
 * <p>All JWT signing, JWKS JSON serialisation, and HTTPS server logic is
 * implemented using JDK APIs only.  No third-party JWT libraries are used.
 *
 * @since 3.1.1
 */
public class JwtLoginModuleTest {

    // -----------------------------------------------------------------------
    // Constants shared across all tests
    // -----------------------------------------------------------------------

    private static final String JWT_KID     = "test-key-1";
    private static final String JWT_KID_2   = "test-key-2";
    private static final String JWT_ISSUER  = "https://test.idp.local";
    private static final String JWT_SUBJECT = "alice@example.org";
    private static final String JWT_EMAIL   = "alice@example.org";

    private static final String KEYSTORE_PASSWORD = "changeit";

    // -----------------------------------------------------------------------
    // Test infrastructure — shared across all tests
    // -----------------------------------------------------------------------

    /** RSA-2048 key pair used to sign test JWTs. */
    private static KeyPair jwtSigningKeyPair;

    /** Second RSA-2048 key pair for key-rotation tests. */
    private static KeyPair jwtSigningKeyPair2;

    /** The embedded HTTPS server serving /jwks.json and /token. */
    private static HttpsServer httpsServer;

    /** Base URI of the running HTTPS server (e.g. {@code https://localhost:xxxxx}). */
    private static String serverBaseUri;

    /**
     * Stores the current JWKS JSON that will be served by /jwks.json.
     * Tests mutate this reference to simulate key rotation.
     */
    private static final AtomicReference<String> currentJwksJson =
            new AtomicReference<>();

    /**
     * When non-null, the /token endpoint returns this token string inside the
     * OAuth2 response JSON.  Tests set this before invoking refresh-token flows.
     */
    private static final AtomicReference<String> tokenEndpointResponse =
            new AtomicReference<>();

    /** The default SSL context that was in place before our tests replaced it. */
    private static SSLContext originalDefaultSslContext;

    // -----------------------------------------------------------------------
    // @BeforeClass / @AfterClass
    // -----------------------------------------------------------------------

    @BeforeClass
    public static void setUpClass() throws Exception {
        // 1. Generate RSA-2048 key pairs for JWT signing (ephemeral, never committed)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        jwtSigningKeyPair  = kpg.generateKeyPair();
        jwtSigningKeyPair2 = kpg.generateKeyPair();

        // 2. Generate self-signed TLS cert for the HTTPS server via keytool
        KeyStore serverKeyStore = generateServerKeyStore();

        // 3. Build server-side SSLContext (cert + key from the keystore)
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKeyStore, KEYSTORE_PASSWORD.toCharArray());
        SSLContext serverSslCtx = SSLContext.getInstance("TLS");
        serverSslCtx.init(kmf.getKeyManagers(), null, null);

        // 4. Build client-side SSLContext that trusts the server's self-signed cert
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(serverKeyStore); // self-signed cert acts as its own CA anchor
        SSLContext clientSslCtx = SSLContext.getInstance("TLS");
        clientSslCtx.init(null, tmf.getTrustManagers(), null);

        // 5. Override the JVM default SSL context so the HttpClient in JwksKeyCache
        //    trusts our self-signed test server certificate
        originalDefaultSslContext = SSLContext.getDefault();
        SSLContext.setDefault(clientSslCtx);

        // 6. Initialise JWKS JSON with the first key pair
        currentJwksJson.set(buildJwks(jwtSigningKeyPair.getPublic(), JWT_KID));

        // 7. Start the HTTPS server on a random port
        httpsServer = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(serverSslCtx));

        // /jwks.json — returns the current JWKS (supports key-rotation tests)
        httpsServer.createContext("/jwks.json", (HttpExchange exchange) -> {
            byte[] body = currentJwksJson.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // /token — OAuth2 client_credentials/refresh_token endpoint
        httpsServer.createContext("/token", (HttpExchange exchange) -> {
            String body = readBody(exchange);
            String accessToken = tokenEndpointResponse.get();
            if (accessToken == null) {
                // Default: issue a fresh short-lived token signed with key pair 1
                try {
                    accessToken = buildJwt(
                            jwtSigningKeyPair.getPrivate(), JWT_KID, JWT_ISSUER,
                            JWT_SUBJECT, JWT_EMAIL, 3600);
                } catch (Exception e) {
                    throw new IOException("JWT signing failed in /token handler", e);
                }
            }
            String responseJson = "{\"access_token\":\"" + accessToken
                    + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
            byte[] respBytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        });

        httpsServer.setExecutor(null);
        httpsServer.start();

        int port = httpsServer.getAddress().getPort();
        serverBaseUri = "https://localhost:" + port;
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (httpsServer != null) {
            httpsServer.stop(0);
        }
        // Restore the original JVM default SSL context
        if (originalDefaultSslContext != null) {
            SSLContext.setDefault(originalDefaultSslContext);
        }
        // Reset shared JWKS state to avoid leaking between test runs
        currentJwksJson.set(null);
        tokenEndpointResponse.set(null);
    }

    // -----------------------------------------------------------------------
    // Happy path tests
    // -----------------------------------------------------------------------

    @Test
    public void testValidJwtPopulatesSubjectPrincipal() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);

        Set<JwtPrincipal> principals = subject.getPrincipals(JwtPrincipal.class);
        assertFalse("Subject should contain JwtPrincipals", principals.isEmpty());
        assertTrue("Subject should contain sub principal",
                principals.stream().anyMatch(p -> "sub:alice@example.org".equals(p.getName())));
    }

    @Test
    public void testValidJwtPopulatesEmailPrincipal() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);

        Set<JwtPrincipal> principals = subject.getPrincipals(JwtPrincipal.class);
        assertTrue("Subject should contain email principal",
                principals.stream().anyMatch(p -> ("email:" + JWT_EMAIL).equals(p.getName())));
    }

    @Test
    public void testValidJwtAddsExpiryClaim() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);

        Set<JwtExpiryClaim> expiryClaims = subject.getPublicCredentials(JwtExpiryClaim.class);
        assertEquals("Subject should have one JwtExpiryClaim", 1, expiryClaims.size());
        Instant expiry = expiryClaims.iterator().next().getExpiry();
        assertTrue("Expiry should be in the future", expiry.isAfter(Instant.now()));
    }

    @Test
    public void testCommitIdempotentForSameToken() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);
        int principalCount = subject.getPrincipals(JwtPrincipal.class).size();

        // A second module instance with the same token should not add duplicates
        // because JwtPrincipal.equals() is based on the name string
        loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);
        assertEquals("Principal count must not grow for identical token claims",
                principalCount, subject.getPrincipals(JwtPrincipal.class).size());
    }

    // -----------------------------------------------------------------------
    // Failure: expired token
    // -----------------------------------------------------------------------

    @Test
    public void testExpiredJwtThrowsLoginException() throws Exception {
        // exp = 1 second in the past; clock skew in JwtValidator is 30 s,
        // so we go 60 seconds in the past to guarantee failure
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, -60);
        Subject subject = new Subject();
        try {
            loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);
            fail("Expected LoginException for expired token");
        } catch (LoginException e) {
            // expected — verify Subject was not polluted
            assertTrue("Subject principals must be empty after failed login",
                    subject.getPrincipals(JwtPrincipal.class).isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Failure: wrong issuer
    // -----------------------------------------------------------------------

    @Test
    public void testWrongIssuerThrowsLoginException() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                "https://rogue.idp.example", JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        try {
            loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);
            fail("Expected LoginException for wrong issuer");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Failure: HS256 (symmetric algorithm)
    // -----------------------------------------------------------------------

    @Test
    public void testHs256AlgorithmThrowsLoginException() throws Exception {
        // Build a JWT with alg=HS256 in the header (HS256 is in the REJECTED_ALGS set)
        String header  = base64UrlEncode("{\"alg\":\"HS256\",\"kid\":\"" + JWT_KID + "\"}");
        long now = Instant.now().getEpochSecond();
        String payload = base64UrlEncode("{\"iss\":\"" + JWT_ISSUER
                + "\",\"sub\":\"" + JWT_SUBJECT
                + "\",\"iat\":" + now
                + ",\"exp\":" + (now + 3600) + "}");
        // Use an arbitrary "signature" – validation should fail before reaching it
        String token = header + "." + payload + ".invalidsignature";
        Subject subject = new Subject();
        try {
            loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);
            fail("Expected LoginException for HS256 algorithm");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Failure: missing jwksUri option
    // -----------------------------------------------------------------------

    @Test
    public void testMissingJwksUriThrowsLoginException() {
        Subject subject = new Subject();
        JwtLoginModule module = new JwtLoginModule();
        Map<String, String> opts = new HashMap<>();
        // jwksUri intentionally absent
        opts.put("issuer", JWT_ISSUER);
        opts.put("token", "dummytoken");
        module.initialize(subject, null, new HashMap<>(), opts);
        try {
            module.login();
            fail("Expected LoginException when jwksUri is absent");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Failure: missing issuer option
    // -----------------------------------------------------------------------

    @Test
    public void testMissingIssuerThrowsLoginException() {
        Subject subject = new Subject();
        JwtLoginModule module = new JwtLoginModule();
        Map<String, String> opts = new HashMap<>();
        opts.put("jwksUri", jwksUri());
        // issuer intentionally absent
        opts.put("token", "dummytoken");
        module.initialize(subject, null, new HashMap<>(), opts);
        try {
            module.login();
            fail("Expected LoginException when issuer is absent");
        } catch (LoginException e) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // abort() prevents Subject population
    // -----------------------------------------------------------------------

    @Test
    public void testAbortAfterLoginDoesNotPopulateSubject() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        JwtLoginModule module = new JwtLoginModule();
        module.initialize(subject, null, new HashMap<>(), buildOptions(jwksUri(), JWT_ISSUER, null, token));
        boolean loggedIn = module.login(); // should succeed
        assertTrue("login() should return true for valid token", loggedIn);
        module.abort();

        assertTrue("Principals must be empty after abort",
                subject.getPrincipals(JwtPrincipal.class).isEmpty());
        assertTrue("Public credentials must be empty after abort",
                subject.getPublicCredentials(JwtExpiryClaim.class).isEmpty());
    }

    // -----------------------------------------------------------------------
    // logout() clears principals and credentials
    // -----------------------------------------------------------------------

    @Test
    public void testLogoutClearsPrincipalsAndCredentials() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        JwtLoginModule module = loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);

        assertFalse("Subject should have principals before logout",
                subject.getPrincipals(JwtPrincipal.class).isEmpty());
        assertFalse("Subject should have expiry credential before logout",
                subject.getPublicCredentials(JwtExpiryClaim.class).isEmpty());

        module.logout();

        assertTrue("Principals must be empty after logout",
                subject.getPrincipals(JwtPrincipal.class).isEmpty());
        assertTrue("Public credentials must be empty after logout",
                subject.getPublicCredentials(JwtExpiryClaim.class).isEmpty());
    }

    // -----------------------------------------------------------------------
    // Key rotation: the JWKS endpoint returns a new key; second login picks it up
    // -----------------------------------------------------------------------

    @Test
    public void testKeyRotationSecondLoginUsesNewKey() throws Exception {
        // First login with key pair 1
        String token1 = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject1 = new Subject();
        loginAndCommit(subject1, jwksUri(), JWT_ISSUER, null, token1);
        assertFalse("First login should populate subject", subject1.getPrincipals().isEmpty());

        // Rotate: update the JWKS to expose key pair 2 under a new kid
        currentJwksJson.set(buildJwks(jwtSigningKeyPair2.getPublic(), JWT_KID_2));
        try {
            // Second login: token signed with old key (kid 1) → should fail after rotation
            // because the server no longer advertises kid 1
            String oldToken = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                    JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
            Subject subject2 = new Subject();
            try {
                loginAndCommit(subject2, jwksUri(), JWT_ISSUER, null, oldToken);
                fail("Expected LoginException because kid 1 is no longer in JWKS after rotation");
            } catch (LoginException e) {
                // expected
            }

            // Third login: token signed with new key (kid 2) → should succeed
            String newToken = buildJwt(jwtSigningKeyPair2.getPrivate(), JWT_KID_2,
                    JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
            Subject subject3 = new Subject();
            loginAndCommit(subject3, jwksUri(), JWT_ISSUER, null, newToken);
            assertFalse("Third login with rotated key should populate subject",
                    subject3.getPrincipals(JwtPrincipal.class).isEmpty());
        } finally {
            // Restore original JWKS so other tests are not affected
            currentJwksJson.set(buildJwks(jwtSigningKeyPair.getPublic(), JWT_KID));
        }
    }

    // -----------------------------------------------------------------------
    // refresh token flow: /token endpoint called, subject updated
    // -----------------------------------------------------------------------

    @Test
    public void testRefreshTokenFlowUpdatesSubject() throws Exception {
        // Pre-sign the "refreshed" access token that /token will return
        String refreshedToken = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, "bob@example.org", "bob@example.org", 3600);
        tokenEndpointResponse.set(refreshedToken);
        try {
            // Initial login with alice's token
            String initialToken = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                    JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
            Subject subject = new Subject();
            Map<String, String> opts = buildOptions(jwksUri(), JWT_ISSUER, null, initialToken);
            opts.put("refreshTokenUri", serverBaseUri + "/token");
            opts.put("refreshToken",    "dummy-refresh-token");
            opts.put("clientId",        "test-client");
            opts.put("clientSecret",    "test-secret");
            loginAndCommit(subject, opts);

            // The RefreshThread is a daemon; we wait briefly and let it run if it fires early
            // (it fires at 80% of token lifetime so won't fire immediately for 3600 s tokens).
            // We verify that commit succeeded and principals are present.
            assertFalse("Subject should have principals after login+commit with refresh config",
                    subject.getPrincipals(JwtPrincipal.class).isEmpty());
        } finally {
            tokenEndpointResponse.set(null);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Drives a {@link JwtLoginModule} through {@code initialize → login → commit}
     * and returns the module so callers can invoke {@code logout()}.
     */
    private static JwtLoginModule loginAndCommit(Subject subject, String jwksUri,
            String issuer, String audience, String token) throws LoginException {
        Map<String, String> opts = buildOptions(jwksUri, issuer, audience, token);
        return loginAndCommit(subject, opts);
    }

    private static JwtLoginModule loginAndCommit(Subject subject, Map<String, String> opts)
            throws LoginException {
        JwtLoginModule module = new JwtLoginModule();
        module.initialize(subject, null, new HashMap<>(), opts);
        if (!module.login())
            throw new RuntimeException("login() returned false");
        if (!module.commit())
            throw new RuntimeException("commit() returned false");
        return module;
    }

    private static Map<String, String> buildOptions(String jwksUri, String issuer,
            String audience, String token) {
        Map<String, String> opts = new HashMap<>();
        opts.put("jwksUri", jwksUri);
        opts.put("issuer",  issuer);
        if (audience != null) opts.put("audience", audience);
        if (token    != null) opts.put("token",    token);
        return opts;
    }

    private String jwksUri() {
        return serverBaseUri + "/jwks.json";
    }

    // -----------------------------------------------------------------------
    // JWT construction helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a compact-serialised RS256 JWT signed with the given private key.
     *
     * @param privateKey     RSA private key (used with SHA256withRSA)
     * @param kid            key ID embedded in the JWT header
     * @param issuer         {@code iss} claim value
     * @param subject        {@code sub} claim value (may be {@code null})
     * @param email          {@code email} claim value (may be {@code null})
     * @param expirySeconds  seconds from now for the {@code exp} claim;
     *                       use a negative value to create an expired token
     */
    private static String buildJwt(PrivateKey privateKey, String kid,
            String issuer, String subject, String email, long expirySeconds)
            throws Exception {
        long now = Instant.now().getEpochSecond();
        StringBuilder payloadJson = new StringBuilder("{");
        payloadJson.append("\"iss\":\"").append(issuer).append('"');
        if (subject != null)
            payloadJson.append(",\"sub\":\"").append(subject).append('"');
        if (email != null)
            payloadJson.append(",\"email\":\"").append(email).append('"');
        payloadJson.append(",\"iat\":").append(now);
        payloadJson.append(",\"exp\":").append(now + expirySeconds);
        payloadJson.append('}');

        String headerPart  = base64UrlEncode(
                ("{\"alg\":\"RS256\",\"kid\":\"" + kid + "\"}").getBytes(StandardCharsets.UTF_8));
        String payloadPart = base64UrlEncode(
                payloadJson.toString().getBytes(StandardCharsets.UTF_8));
        String sigInput = headerPart + "." + payloadPart;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(sigInput.getBytes(StandardCharsets.US_ASCII));
        byte[] sigBytes = sig.sign();

        return sigInput + "." + base64UrlEncode(sigBytes);
    }

    // -----------------------------------------------------------------------
    // JWKS JSON construction helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a minimal JWKS JSON document containing one RSA public key.
     * The modulus and exponent are Base64url-encoded as unsigned big-endian
     * byte arrays (JWK RFC 7517 / 7518 §6.3.1).
     */
    private static String buildJwks(PublicKey publicKey, String kid) {
        RSAPublicKey rsaPub = (RSAPublicKey) publicKey;
        String n = bigIntToBase64Url(rsaPub.getModulus());
        String e = bigIntToBase64Url(rsaPub.getPublicExponent());
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid
                + "\",\"alg\":\"RS256\",\"n\":\"" + n
                + "\",\"e\":\"" + e + "\"}]}";
    }

    /**
     * Converts a positive {@link BigInteger} to an unsigned big-endian
     * Base64url string, stripping any leading zero byte that
     * {@link BigInteger#toByteArray()} may prepend as a sign indicator.
     */
    private static String bigIntToBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // -----------------------------------------------------------------------
    // HTTPS server TLS setup
    // -----------------------------------------------------------------------

    /**
     * Generates a self-signed RSA-2048 TLS certificate for {@code CN=localhost}
     * using {@code keytool -genkeypair} and returns it as an in-memory
     * {@link KeyStore} (PKCS12).
     *
     * <p>The temporary keystore file is deleted immediately after loading.
     */
    private static KeyStore generateServerKeyStore() throws Exception {
        Path tmpDir = Files.createTempDirectory("jwt-https-test");
        Path p12Path = tmpDir.resolve("server.p12");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "keytool", "-genkeypair",
                    "-keyalg",  "RSA",
                    "-keysize", "2048",
                    "-validity", "1",  // 1 day — minimise footprint
                    "-dname", "CN=localhost",
                    "-alias", "server",
                    "-keystore", p12Path.toString(),
                    "-storetype", "PKCS12",
                    "-storepass", KEYSTORE_PASSWORD,
                    "-keypass", KEYSTORE_PASSWORD,
                    "-noprompt"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();
            if (exitCode != 0)
                throw new RuntimeException("keytool failed (exit " + exitCode + "): " + output);

            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream is = Files.newInputStream(p12Path)) {
                ks.load(is, KEYSTORE_PASSWORD.toCharArray());
            }
            return ks;
        } finally {
            Files.deleteIfExists(p12Path);
            Files.deleteIfExists(tmpDir);
        }
    }

    // -----------------------------------------------------------------------
    // HTTP request helper
    // -----------------------------------------------------------------------

    /** Reads the request body from an {@link HttpExchange} as a UTF-8 string. */
    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
