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
import static org.junit.Assert.assertNull;
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

    /** Requests the /token endpoint has served. */
    private static final java.util.concurrent.atomic.AtomicInteger tokenHits =
            new java.util.concurrent.atomic.AtomicInteger();

    /** When non-null, /token answers (HTTP 400 unless {@link #tokenEndpointStatus}) with this body. */
    private static final AtomicReference<String> tokenEndpointRaw = new AtomicReference<>();

    /** The status /token answers with when {@link #tokenEndpointRaw} is set; null means 400. */
    private static final AtomicReference<Integer> tokenEndpointStatus = new AtomicReference<>();

    /** When non-null, /jwks.json answers with this status and {@link #jwksRaw} instead of the key set. */
    private static final AtomicReference<Integer> jwksStatus = new AtomicReference<>();

    /** The body /jwks.json sends while {@link #jwksStatus} is set. */
    private static final AtomicReference<String> jwksRaw = new AtomicReference<>();

    /** When non-null, /token holds each request on this latch (a request in flight). */
    private static volatile java.util.concurrent.CountDownLatch tokenGate;
    private static volatile java.util.concurrent.CountDownLatch tokenGateEntered =
            new java.util.concurrent.CountDownLatch(1);

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
            Integer forced = jwksStatus.get();
            String raw = jwksRaw.get();
            byte[] body = (forced != null && raw != null ? raw : currentJwksJson.get())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(forced == null ? 200 : forced, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // /token — OAuth2 client_credentials/refresh_token endpoint
        httpsServer.createContext("/token", (HttpExchange exchange) -> {
            String body = readBody(exchange);
            tokenHits.incrementAndGet();
            java.util.concurrent.CountDownLatch gate = tokenGate;
            if (gate != null) {
                tokenGateEntered.countDown();
                try {
                    gate.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            String raw = tokenEndpointRaw.get();
            if (raw != null) {
                byte[] rb = raw.getBytes(StandardCharsets.UTF_8);
                Integer st = tokenEndpointStatus.get();
                exchange.sendResponseHeaders(st == null ? 400 : st, rb.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rb);
                }
                return;
            }
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
        String token = signPayload(JWT_KID, payloadJson(
                "\"sub\":\"" + JWT_SUBJECT + "\",\"email\":\"" + JWT_EMAIL
                + "\",\"email_verified\":true"));
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
    // Client principals equal what the server derives; strict claims parsing
    // -----------------------------------------------------------------------

    private static final String AUD = "svc-a";

    /** Payload with iss, aud "svc-a" (unless {@code extra} sets aud) and a valid exp. */
    private static String payloadJson(String extra) {
        long exp = Instant.now().getEpochSecond() + 3600;
        return "{\"iss\":\"" + JWT_ISSUER + "\",\"exp\":" + exp
                + (extra.contains("\"aud\"") ? "" : ",\"aud\":\"" + AUD + "\"")
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static String signPayload(String kid, String payload) throws Exception {
        String headerPart  = base64UrlEncode("{\"alg\":\"RS256\",\"kid\":\"" + kid + "\"}");
        String sigInput = headerPart + "." + base64UrlEncode(payload);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(jwtSigningKeyPair.getPrivate());
        sig.update(sigInput.getBytes(StandardCharsets.US_ASCII));
        return sigInput + "." + base64UrlEncode(sig.sign());
    }

    private static String groupsJson(int n, String prefix) {
        StringBuilder b = new StringBuilder("\"groups\":[");
        for (int i = 0; i < n; i++) b.append(i > 0 ? "," : "").append("\"").append(prefix).append(i).append("\"");
        return b.append(']').toString();
    }

    /** Names of the JwtPrincipals a login of {@code token} puts in the Subject; LoginException if refused. */
    private static Set<String> clientPrincipals(String token) throws LoginException {
        Subject subject = new Subject();
        loginAndCommit(subject, jwksUri(), JWT_ISSUER, AUD, token);
        Set<String> out = new java.util.TreeSet<>();
        for (JwtPrincipal p : subject.getPrincipals(JwtPrincipal.class)) out.add(p.getName());
        return out;
    }

    /** Names the server verifier derives from {@code token}; null if it refuses. */
    private static Set<String> serverPrincipals(String token) {
        try {
            net.jini.security.jwt.DefaultJwtVerifier v = new net.jini.security.jwt.DefaultJwtVerifier(
                    JWT_ISSUER, AUD, "RS256", Map.of(JWT_KID, jwtSigningKeyPair.getPublic()));
            Set<String> out = new java.util.TreeSet<>();
            for (java.security.Principal p : v.verify(token).getPrincipals()) out.add(p.getName());
            return out;
        } catch (net.jini.security.jwt.JwtVerificationException e) {
            return null;
        }
    }

    private static void assertSameAsServer(String label, String payload) throws Exception {
        String token = signPayload(JWT_KID, payload);
        Set<String> server = serverPrincipals(token);
        Set<String> client;
        try {
            client = clientPrincipals(token);
        } catch (LoginException e) {
            client = null;
        }
        assertEquals(label + ": client " + client + " vs server " + server, server, client);
    }

    @Test
    public void testEmailPrincipalOnlyWhenEmailVerifiedIsJsonTrue() throws Exception {
        String e = "\"sub\":\"s\",\"email\":\"a@b.c\"";
        assertFalse(clientPrincipals(signPayload(JWT_KID, payloadJson(e + ",\"email_verified\":false")))
                .contains("email:a@b.c"));
        assertFalse(clientPrincipals(signPayload(JWT_KID, payloadJson(e))).contains("email:a@b.c"));
        assertFalse(clientPrincipals(signPayload(JWT_KID, payloadJson(e + ",\"email_verified\":\"true\"")))
                .contains("email:a@b.c"));
        assertTrue(clientPrincipals(signPayload(JWT_KID, payloadJson(e + ",\"email_verified\":true")))
                .contains("email:a@b.c"));
    }

    @Test
    public void testClientPrincipalsEqualServerDerivedSet() throws Exception {
        String[][] table = {
            {"verified email + groups", "\"sub\":\"s\",\"email\":\"a@b.c\",\"email_verified\":true,\"groups\":[\"g1\",\"g2\"]"},
            {"unverified email", "\"sub\":\"s\",\"email\":\"a@b.c\",\"email_verified\":false"},
            {"email_verified absent", "\"sub\":\"s\",\"email\":\"a@b.c\""},
            {"email_verified string", "\"sub\":\"s\",\"email\":\"a@b.c\",\"email_verified\":\"true\""},
            {"email_verified 1", "\"sub\":\"s\",\"email\":\"a@b.c\",\"email_verified\":1"},
            {"no sub", "\"groups\":[\"g\"]"},
            {"empty sub", "\"sub\":\"\",\"groups\":[\"g\"]"},
            {"empty group", "\"sub\":\"s\",\"groups\":[\"\"]"},
            {"empty email", "\"sub\":\"s\",\"email\":\"\",\"email_verified\":true"},
            {"duplicate groups", "\"sub\":\"s\",\"groups\":[\"g\",\"g\"]"},
            {"non-string group", "\"sub\":\"s\",\"groups\":[\"g\",1]"},
            {"groups not array", "\"sub\":\"s\",\"groups\":\"g\""},
            {"63 groups + sub", "\"sub\":\"s\"," + groupsJson(63, "g")},
            {"64 groups + sub (65)", "\"sub\":\"s\"," + groupsJson(64, "g")},
            {"64 groups no sub", groupsJson(64, "g")},
            {"65 groups no sub", groupsJson(65, "g")},
            {"aud array with match", "\"aud\":[\"x\",\"svc-a\"],\"sub\":\"s\""},
            {"aud comma flaw array", "\"aud\":[\"evil,svc-a\"],\"sub\":\"s\""},
            {"aud comma flaw string", "\"aud\":\"evil,svc-a\",\"sub\":\"s\""},
            {"nested sub not lifted", "\"x\":{\"sub\":\"evil\",\"groups\":[\"root\"]}"},
            {"duplicate member", "\"sub\":\"a\",\"sub\":\"b\""},
        };
        for (String[] row : table) assertSameAsServer(row[0], payloadJson(row[1]));
    }

    @Test
    public void testNestedClaimsAreNotLiftedToTheTopLevel() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        // no top-level exp: the nested one must not stand in for it
        String noExp = "{\"iss\":\"" + JWT_ISSUER + "\",\"aud\":\"svc-a\",\"x\":{\"exp\":" + exp + "}}";
        try {
            clientPrincipals(signPayload(JWT_KID, noExp));
            fail("nested exp was lifted");
        } catch (LoginException expected) { }
        // wrong top-level aud: the nested one must not satisfy the check
        try {
            clientPrincipals(signPayload(JWT_KID, payloadJson("\"aud\":\"other\",\"x\":{\"aud\":\"svc-a\"}")));
            fail("nested aud was lifted");
        } catch (LoginException expected) { }
        // a nested sub gives no principal
        assertTrue(clientPrincipals(signPayload(JWT_KID, payloadJson("\"x\":{\"sub\":\"evil\"}"))).isEmpty());
    }

    @Test
    public void testAudienceIsAnExactElementNotASubstring() throws Exception {
        for (String aud : new String[] {"\"evil,svc-a\"", "[\"evil,svc-a\"]", "\"xsvc-ay\"", "[\"svc-ab\"]"}) {
            try {
                clientPrincipals(signPayload(JWT_KID, payloadJson("\"aud\":" + aud)));
                fail("audience accepted: " + aud);
            } catch (LoginException expected) { }
        }
        clientPrincipals(signPayload(JWT_KID, payloadJson("\"aud\":[\"a\",\"svc-a\"]")));
    }

    @Test
    public void testSixtyFiveGroupsFailsTheLogin() throws Exception {
        try {
            clientPrincipals(signPayload(JWT_KID, payloadJson(groupsJson(65, "g"))));
            fail("65 groups accepted");
        } catch (LoginException expected) { }
        assertEquals(64, clientPrincipals(signPayload(JWT_KID, payloadJson(groupsJson(64, "g")))).size());
    }

    @Test
    public void testEmptySubFailsTheLogin() throws Exception {
        try {
            clientPrincipals(signPayload(JWT_KID, payloadJson("\"sub\":\"\"")));
            fail("empty sub accepted");
        } catch (LoginException expected) { }
    }

    @Test
    public void testFailedLoginMessageHasNoTokenFragment() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String hdr = "\"alg\":\"RS256\"";
        String[] tokens = {
            // too many principals, marker in sub
            signPayload(JWT_KID, payloadJson("\"sub\":\"sub-leak-marker\"," + groupsJson(70, "grp-leak-marker"))),
            // issuer mismatch with a marker issuer
            signPayload(JWT_KID, "{\"iss\":\"iss-leak-marker\",\"exp\":" + exp + ",\"aud\":\"svc-a\"}"),
            // audience mismatch with a marker audience
            signPayload(JWT_KID, payloadJson("\"aud\":\"aud-leak-marker\"")),
            // unknown key id carrying a marker
            signPayload("kid-leak-marker", payloadJson("")),
            // payload that is not a JSON object, marker inside
            signPayload(JWT_KID, "[\"json-leak-marker\"]"),
            // duplicate member name, marker as the name
            signPayload(JWT_KID, "{\"dup-leak-marker\":1,\"dup-leak-marker\":2}"),
            // expired
            signPayload(JWT_KID, "{\"iss\":\"" + JWT_ISSUER + "\",\"exp\":1,\"sub\":\"exp-leak-marker\"}"),
            // signature that does not verify
            signPayload(JWT_KID, payloadJson("\"sub\":\"sig-leak-marker\"")).replaceAll("\\.[^.]*$", ".AAAA"),
        };
        for (String token : tokens) {
            String[] parts = token.split("\\.", -1);
            try {
                clientPrincipals(token);
                fail("token was accepted");
            } catch (LoginException e) {
                StringBuilder all = new StringBuilder();
                for (Throwable t = e; t != null; t = t.getCause()) all.append(t.getMessage()).append('\n');
                String msg = all.toString();
                assertFalse("token in message", msg.contains(token));
                for (String part : parts)
                    if (part.length() > 3) assertFalse("token segment in message: " + msg, msg.contains(part));
                assertFalse("claim value in message: " + msg, msg.contains("leak-marker"));
            }
        }
    }

    // -----------------------------------------------------------------------
    // crit header refusal
    // -----------------------------------------------------------------------

    private static String signWithHeader(String headerJson, String payload) throws Exception {
        String sigInput = base64UrlEncode(headerJson) + "." + base64UrlEncode(payload);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(jwtSigningKeyPair.getPrivate());
        sig.update(sigInput.getBytes(StandardCharsets.US_ASCII));
        return sigInput + "." + base64UrlEncode(sig.sign());
    }

    @Test
    public void testCritHeaderIsRefusedWhateverItsValue() throws Exception {
        String base = "\"alg\":\"RS256\",\"kid\":\"" + JWT_KID + "\"";
        // control: the same header without crit is accepted
        clientPrincipals(signWithHeader("{" + base + "}", payloadJson("\"sub\":\"s\"")));
        for (String crit : new String[] {"null", "[]", "[\"x\"]", "[\"exp\"]", "\"x\"", "1", "{}"}) {
            try {
                clientPrincipals(signWithHeader("{" + base + ",\"crit\":" + crit + "}",
                        payloadJson("\"sub\":\"s\"")));
                fail("crit header accepted: " + crit);
            } catch (LoginException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("crit"));
            }
        }
    }

    // -----------------------------------------------------------------------
    // refresh replaces principals, token and expiry together, same sub and iss
    // -----------------------------------------------------------------------

    private static String tokenFor(String sub, String iss, long exp, String groups) throws Exception {
        return signPayload(JWT_KID, "{\"iss\":\"" + iss + "\",\"aud\":\"" + AUD + "\",\"sub\":\""
                + sub + "\",\"exp\":" + exp + (groups.isEmpty() ? "" : "," + groups) + "}");
    }

    private static long inSeconds(long s) {
        return Instant.now().getEpochSecond() + s;
    }

    /** Logs in with a refresh endpoint configured (the thread sleeps; tests refresh by hand). */
    private static JwtLoginModule loginWithRefresh(Subject subject, String token) throws LoginException {
        Map<String, String> opts = buildOptions(jwksUri(), JWT_ISSUER, AUD, token);
        opts.put("refreshTokenUri", serverBaseUri + "/token");
        opts.put("refreshToken", "dummy-refresh-token");
        opts.put("clientId", "test-client");
        opts.put("clientSecret", "test-secret");
        return loginAndCommit(subject, opts);
    }

    private static Set<String> jwtNames(Subject s) {
        Set<String> out = new java.util.TreeSet<>();
        for (JwtPrincipal p : s.getPrincipals(JwtPrincipal.class)) out.add(p.getName());
        return out;
    }

    private static Set<String> tokens(Subject s) {
        Set<String> out = new java.util.TreeSet<>();
        for (net.jini.security.jwt.JwtRawToken t : s.getPublicCredentials(net.jini.security.jwt.JwtRawToken.class))
            out.add(t.getToken());
        return out;
    }

    private static Set<Instant> expiries(Subject s) {
        Set<Instant> out = new java.util.HashSet<>();
        for (JwtExpiryClaim c : s.getPublicCredentials(JwtExpiryClaim.class)) out.add(c.getExpiry());
        return out;
    }

    /** What a refusal must leave behind: exactly the state before. */
    private static void assertUnchanged(Subject s, Set<String> names, Set<String> toks, Set<Instant> exps) {
        assertEquals(names, jwtNames(s));
        assertEquals(toks, tokens(s));
        assertEquals(exps, expiries(s));
    }

    @Test
    public void testRefreshReplacesPrincipalsTokenAndExpiryTogether() throws Exception {
        long exp1 = inSeconds(1000), exp2 = inSeconds(5000);
        String t1 = tokenFor("s", JWT_ISSUER, exp1, "\"groups\":[\"a\"]");
        String t2 = tokenFor("s", JWT_ISSUER, exp2, "\"groups\":[\"b\"]");
        Subject subject = new Subject();
        JwtPrincipal foreign = new JwtPrincipal("other:module");
        subject.getPrincipals().add(foreign);
        JwtLoginModule m = loginWithRefresh(subject, t1);
        try {
            assertEquals(Set.of("sub:s", "group:a", "other:module"), jwtNames(subject));
            assertEquals(Set.of(t1), tokens(subject));
            tokenEndpointResponse.set(t2);
            m.refreshNow();
            assertEquals(Set.of("sub:s", "group:b", "other:module"), jwtNames(subject));
            assertEquals("the raw token follows the principals", Set.of(t2), tokens(subject));
            assertEquals(Set.of(Instant.ofEpochSecond(exp2)), expiries(subject));
            assertNull(m.lastRefreshFailure);
        } finally {
            tokenEndpointResponse.set(null);
            m.logout();
        }
        assertEquals("a principal of another module survives logout", Set.of("other:module"), jwtNames(subject));
    }

    /**
     * Operator ruling 2026-11-02: a refreshed token that fails validation is a
     * permanent failure.  The thread stops and the Subject fails closed; before
     * this the old token stayed and the thread retried forever.  The thread
     * starts with a token inside the validator's skew, so it refreshes at once.
     */
    @Test
    public void testRefreshWithADifferentIssFailsClosed() throws Exception {
        String t1 = tokenFor("alice", JWT_ISSUER, inSeconds(-10), "");
        String t2 = tokenFor("alice", "https://rogue-marker.example", inSeconds(5000), "");
        assertThreadFailsClosed("another iss", t1, null, null, t2);
    }

    @Test
    public void testRefreshResponseThatIsNotAValidTokenFailsClosed() throws Exception {
        String t1 = tokenFor("alice", JWT_ISSUER, inSeconds(-10), "\"groups\":[\"a\"]");
        String bad = tokenFor("alice", JWT_ISSUER, inSeconds(5000), "").replaceAll("\\.[^.]*$", ".AAAA");
        String noExp = signPayload(JWT_KID, "{\"iss\":\"" + JWT_ISSUER + "\",\"aud\":\"" + AUD
                + "\",\"sub\":\"alice\"}");
        String wrongAud = signPayload(JWT_KID, "{\"iss\":\"" + JWT_ISSUER + "\",\"aud\":\"other-marker\","
                + "\"sub\":\"alice\",\"exp\":" + inSeconds(5000) + "}");
        String[] responses = {"not-a-jwt-marker", bad, tokenFor("alice", JWT_ISSUER, inSeconds(-600), ""),
                noExp, wrongAud};
        String[] labels = {"not a JWT", "bad signature", "expired", "no exp", "wrong aud"};
        for (int i = 0; i < responses.length; i++)
            assertThreadFailsClosed(labels[i], t1, null, null, responses[i]);
    }

    /** A 200 whose body is not a token response is an identity-provider malfunction: permanent. */
    @Test
    public void testMalformedTokenResponseFailsClosed() throws Exception {
        String t1 = tokenFor("alice", JWT_ISSUER, inSeconds(-10), "");
        assertThreadFailsClosed("not JSON", t1, 200, "<html>gateway-marker</html>", null);
        assertThreadFailsClosed("no access_token", t1, 200, "{\"token_type\":\"Bearer\"}", null);
    }

    /**
     * HTTP 400 (with or without invalid_grant), 401 and 403 on the real thread:
     * permanent.  {@code refreshNow} bypasses the thread loop, whose own
     * permanent-failure branch these reach.
     */
    @Test
    public void testPermanentHttpStatusesFailClosedOnTheRealThread() throws Exception {
        String t1 = tokenFor("alice", JWT_ISSUER, inSeconds(-10), "\"groups\":[\"a\"]");
        assertThreadFailsClosed("400 invalid_grant", t1, 400, "{\"error\":\"invalid_grant\"}", null);
        assertThreadFailsClosed("400 other", t1, 400, "{\"error\":\"invalid_request\"}", null);
        assertThreadFailsClosed("401", t1, 401, "{\"error\":\"invalid_client\"}", null);
        assertThreadFailsClosed("403", t1, 403, "{\"error\":\"access_denied\"}", null);
    }

    /** 5xx, 408 and 429 are transient: the token stays, the thread lives and retries. */
    @Test
    public void testTransientHttpStatusesKeepTheTokenAndTheThread() throws Exception {
        for (int status : new int[] {503, 500, 408, 429}) {
            String t1 = tokenFor("alice", JWT_ISSUER, inSeconds(-10), "\"groups\":[\"a\"]");
            Subject subject = new Subject();
            tokenHits.set(0);
            tokenEndpointStatus.set(status);
            tokenEndpointRaw.set("{\"error\":\"temporarily_unavailable\"}");
            JwtLoginModule m = null;
            try {
                m = loginWithRefresh(subject, t1);
                Thread rt = refreshThreadOf(m);
                long deadline = System.currentTimeMillis() + 10000;
                while ((tokenHits.get() < 1 || m.lastRefreshFailure == null)
                        && System.currentTimeMillis() < deadline) Thread.sleep(20);
                String why = m.lastRefreshFailure;
                assertNotNull("HTTP " + status + ": the attempt was made and recorded", why);
                assertTrue(why, why.contains(Integer.toString(status)));
                assertTrue("HTTP " + status + ": the thread is retrying", rt.isAlive());
                assertEquals("HTTP " + status + ": the token stays", Set.of(t1), tokens(subject));
                assertEquals(Set.of("sub:alice", "group:a"), jwtNames(subject));
                assertEquals(1, expiries(subject).size());
            } finally {
                tokenEndpointRaw.set(null);
                tokenEndpointStatus.set(null);
                if (m != null) {
                    Thread rt = refreshThreadOf(m);
                    m.logout();
                    rt.join(5000);
                    assertFalse("logout stopped the thread", rt.isAlive());
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Operator ruling 2026-11-02 (second): an unreadable JWKS is transient
    // -----------------------------------------------------------------------

    private static void jwksAnswers(Integer status, String raw) {
        jwksStatus.set(status);
        jwksRaw.set(raw);
    }

    /**
     * A JWKS endpoint that fails at refresh time says nothing about the token:
     * the old token and principals stay, the thread retries, and once the JWKS
     * is back the new token is installed.  The /token gate holds the first
     * request so the JWKS can be broken after the login read it.
     */
    @Test
    public void testJwksDownDuringRefreshKeepsTheTokenAndRecovers() throws Exception {
        String big = "x".repeat(JwksKeyCache.MAX_JWKS_RESPONSE_BYTES + 10);
        Object[][] modes = {
            {500, "{\"error\":\"down\"}", "HTTP 500"},
            {200, "<html>gateway-marker</html>", "garbage 200"},
            {200, big, "over the size limit"},
        };
        for (int i = 0; i < modes.length; i++) {
            Integer status = (Integer) modes[i][0];
            String raw = (String) modes[i][1];
            String label = (String) modes[i][2];
            String t1 = tokenFor("alice", JWT_ISSUER, inSeconds(-10), "\"groups\":[\"a\"]");
            String t2 = tokenFor("alice", JWT_ISSUER, inSeconds(5000), "\"groups\":[\"b\"]");
            Subject subject = new Subject();
            tokenEndpointResponse.set(t2);
            tokenGateEntered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
            tokenGate = gate;
            JwtLoginModule m = null;
            try {
                m = loginWithRefresh(subject, t1);
                Thread rt = refreshThreadOf(m);
                assertTrue(label + ": the refresh reached the token endpoint",
                        tokenGateEntered.await(15, java.util.concurrent.TimeUnit.SECONDS));
                jwksAnswers(status, raw);
                tokenGate = null;
                gate.countDown();
                long deadline = System.currentTimeMillis() + 15000;
                while (m.lastRefreshFailure == null && System.currentTimeMillis() < deadline)
                    Thread.sleep(20);
                assertEquals(label + ": the failure is recorded as a class name only",
                        "JwksUnavailableException", m.lastRefreshFailure);
                assertTrue(label + ": the thread is retrying", rt.isAlive());
                assertEquals(label + ": the old token stays", Set.of(t1), tokens(subject));
                assertEquals(Set.of("sub:alice", "group:a"), jwtNames(subject));
                assertEquals(1, expiries(subject).size());
                if (i == 0) {
                    jwksAnswers(null, null);
                    deadline = System.currentTimeMillis() + 25000;
                    while (!tokens(subject).equals(Set.of(t2)) && System.currentTimeMillis() < deadline)
                        Thread.sleep(50);
                    assertEquals("the JWKS is back: the new token is installed", Set.of(t2), tokens(subject));
                    assertEquals(Set.of("sub:alice", "group:b"), jwtNames(subject));
                    assertTrue("the thread lives on", rt.isAlive());
                }
            } finally {
                tokenGate = null;
                gate.countDown();
                jwksAnswers(null, null);
                tokenEndpointResponse.set(null);
                if (m != null) m.logout();
            }
        }
    }

    /** A reachable JWKS that lacks the token's kid is a bad token, not an outage: permanent. */
    @Test
    public void testRefreshWithAKidTheReachableJwksLacksFailsClosed() throws Exception {
        String t1 = tokenFor("alice", JWT_ISSUER, inSeconds(-10), "");
        String t2 = signPayload("unlisted-kid-marker", "{\"iss\":\"" + JWT_ISSUER + "\",\"aud\":\"" + AUD
                + "\",\"sub\":\"alice\",\"exp\":" + inSeconds(5000) + "}");
        assertThreadFailsClosed("kid not in the JWKS", t1, null, null, t2);
    }

    /** First login has no old token to keep: an unreadable JWKS fails the login, at once. */
    @Test
    public void testFirstLoginWithTheJwksUnreadableFailsWithALoginException() throws Exception {
        String token = tokenFor("alice", JWT_ISSUER, inSeconds(1000), "");
        jwksAnswers(500, "{\"error\":\"down\"}");
        try {
            Subject subject = new Subject();
            try {
                loginAndCommit(subject, jwksUri(), JWT_ISSUER, AUD, token);
                fail("login must fail while the JWKS is unreadable");
            } catch (LoginException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("JWKS"));
                for (String part : token.split("\\."))
                    assertFalse("token text in the message", e.getMessage().contains(part));
            }
            assertNothingOfOurs(subject);
        } finally {
            jwksAnswers(null, null);
        }
        // Nothing listens on the port: connection refused.
        int dead;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            dead = ss.getLocalPort();
        }
        Subject subject = new Subject();
        try {
            loginAndCommit(subject, "https://localhost:" + dead + "/jwks.json", JWT_ISSUER, AUD, token);
            fail("login must fail while the JWKS refuses connections");
        } catch (LoginException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("JWKS"));
        }
        assertNothingOfOurs(subject);
    }

    /**
     * Drives the real refresh thread against the test endpoint (a status and
     * body, or a token response) and asserts it ends with nothing of ours in
     * the Subject: no token, no expiry claim, only a foreign principal left.
     */
    private static void assertThreadFailsClosed(String label, String t1, Integer status, String raw,
            String tokenResponse) throws Exception {
        Subject subject = new Subject();
        subject.getPrincipals().add(new JwtPrincipal("other:module"));
        tokenHits.set(0);
        tokenEndpointStatus.set(status);
        tokenEndpointRaw.set(raw);
        tokenEndpointResponse.set(tokenResponse);
        JwtLoginModule m = null;
        try {
            m = loginWithRefresh(subject, t1);
            Thread rt = refreshThreadOf(m);
            rt.join(10000);
            assertFalse(label + ": the refresh thread stopped", rt.isAlive());
            assertTrue(label + ": the endpoint was asked", tokenHits.get() >= 1);
            assertEquals(label + ": only the foreign principal stays",
                    Set.of("other:module"), jwtNames(subject));
            assertTrue(label + ": tokens " + tokens(subject), tokens(subject).isEmpty());
            assertTrue(label + ": expiry claims", expiries(subject).isEmpty());
            String why = m.lastRefreshFailure;
            assertNotNull(label + ": the failure is recorded", why);
            assertFalse(label + ": no claim value in the record: " + why, why.contains("marker"));
            if (tokenResponse != null)
                for (String part : tokenResponse.split("\\."))
                    if (part.length() > 3) assertFalse("token segment in the record", why.contains(part));
        } finally {
            tokenEndpointResponse.set(null);
            tokenEndpointRaw.set(null);
            tokenEndpointStatus.set(null);
            if (m != null) m.logout();
        }
    }

    @Test
    public void testLogoutAfterRefreshRemovesEverythingAndStopsTheThread() throws Exception {
        String t1 = tokenFor("s", JWT_ISSUER, inSeconds(1000), "\"groups\":[\"a\"]");
        String t2 = tokenFor("s", JWT_ISSUER, inSeconds(5000), "\"groups\":[\"b\"]");
        Subject subject = new Subject();
        JwtLoginModule m = loginWithRefresh(subject, t1);
        java.lang.reflect.Field f = JwtLoginModule.class.getDeclaredField("refreshThread");
        f.setAccessible(true);
        Thread rt = (Thread) f.get(m);
        assertTrue(rt.isAlive());
        tokenEndpointResponse.set(t2);
        try {
            m.refreshNow();
        } finally {
            tokenEndpointResponse.set(null);
        }
        m.logout();
        assertTrue(jwtNames(subject).isEmpty());
        assertTrue(tokens(subject).isEmpty());
        assertTrue(expiries(subject).isEmpty());
        assertTrue(subject.getPublicCredentials().isEmpty());
        rt.join(5000);
        assertFalse("the refresh thread stopped", rt.isAlive());
    }

    /** Probabilistic on its own; the deterministic pins are the tests above. */
    @Test
    public void testAReaderUnderTheSubjectLockNeverSeesHalfARefresh() throws Exception {
        String ta = tokenFor("s", JWT_ISSUER, inSeconds(1000), "\"groups\":[\"a\"]");
        String tb = tokenFor("s", JWT_ISSUER, inSeconds(2000), "\"groups\":[\"b\"]");
        Subject subject = new Subject();
        JwtLoginModule m = loginWithRefresh(subject, ta);
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        AtomicReference<String> torn = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            while (!stop.get() && torn.get() == null) {
                synchronized (subject) {
                    Set<String> names = jwtNames(subject);
                    Set<String> toks = tokens(subject);
                    String want = toks.contains(ta) ? "group:a" : "group:b";
                    if (toks.size() != 1 || names.size() != 2 || !names.contains(want))
                        torn.set(names + " with " + toks.size() + " token(s)");
                }
            }
        });
        reader.start();
        try {
            for (int i = 0; i < 60 && torn.get() == null; i++) {
                tokenEndpointResponse.set(i % 2 == 0 ? tb : ta);
                m.refreshNow();
            }
        } finally {
            stop.set(true);
            reader.join();
            tokenEndpointResponse.set(null);
            m.logout();
        }
        assertNull("torn view: " + torn.get(), torn.get());
    }

    // -----------------------------------------------------------------------
    // Lifecycle: what the module owns, second commit, abort, fail-closed stop
    // -----------------------------------------------------------------------

    private static Thread refreshThreadOf(JwtLoginModule m) throws Exception {
        java.lang.reflect.Field f = JwtLoginModule.class.getDeclaredField("refreshThread");
        f.setAccessible(true);
        return (Thread) f.get(m);
    }

    private static int liveRefreshThreads() {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet())
            if (t.isAlive() && "JwtLoginModule-refresh".equals(t.getName())) n++;
        return n;
    }

    private static void assertNothingOfOurs(Subject s) {
        assertTrue("principals " + jwtNames(s), jwtNames(s).isEmpty());
        assertTrue("tokens", tokens(s).isEmpty());
        assertTrue("expiry claims", expiries(s).isEmpty());
    }

    /** A token with no {@code sub}; identity is the verified email. */
    private static String subLess(String email, String extra) throws Exception {
        return signPayload(JWT_KID, "{\"iss\":\"" + JWT_ISSUER + "\",\"aud\":\"" + AUD
                + "\",\"exp\":" + inSeconds(1000) + ",\"email\":\"" + email
                + "\",\"email_verified\":true" + extra + "}");
    }

    /** Collects what the module logs. */
    private static final class Capture extends java.util.logging.Handler {
        final java.util.List<java.util.logging.LogRecord> records =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        @Override public void publish(java.util.logging.LogRecord r) { records.add(r); }
        @Override public void flush() { }
        @Override public void close() { }
        java.util.List<java.util.logging.LogRecord> atLeast(java.util.logging.Level l) {
            java.util.List<java.util.logging.LogRecord> out = new java.util.ArrayList<>();
            synchronized (records) {
                for (java.util.logging.LogRecord r : records)
                    if (r.getLevel().intValue() >= l.intValue()) out.add(r);
            }
            return out;
        }
    }

    private static java.util.logging.Logger moduleLogger() {
        return java.util.logging.Logger.getLogger(JwtLoginModule.class.getName());
    }

    /**
     * A refusal on identity fails closed: the refreshed token is not installed,
     * and the token, expiry claim and our principals are removed (a foreign
     * principal stays), the failure is recorded and the thread stops.
     */
    private static void assertRefreshFailsClosed(String t1, String response) throws Exception {
        Subject subject = new Subject();
        JwtPrincipal foreign = new JwtPrincipal("other:module");
        subject.getPrincipals().add(foreign);
        JwtLoginModule m = loginWithRefresh(subject, t1);
        Thread rt = refreshThreadOf(m);
        try {
            assertEquals(Set.of(t1), tokens(subject));
            tokenEndpointResponse.set(response);
            try {
                m.refreshNow();
                fail("refresh was accepted");
            } catch (Exception expected) {
                // refused
            }
            assertEquals("only the foreign principal stays", Set.of("other:module"), jwtNames(subject));
            assertTrue("tokens", tokens(subject).isEmpty());
            assertTrue("expiry claims", expiries(subject).isEmpty());
            String why = m.lastRefreshFailure;
            assertNotNull("the failure is recorded", why);
            assertFalse("no claim value in the record: " + why, why.contains("marker"));
            rt.join(5000);
            assertFalse("the refresh thread stopped", rt.isAlive());
        } finally {
            tokenEndpointResponse.set(null);
            m.logout();
        }
    }

    // K1: only what we added is ours to remove

    @Test
    public void testPrincipalsAnotherModuleInstalledAreNeverRemoved() throws Exception {
        String t1 = tokenFor("s", JWT_ISSUER, inSeconds(1000), "\"groups\":[\"a\"]");
        String t2 = tokenFor("s", JWT_ISSUER, inSeconds(5000), "\"groups\":[\"b\"]");
        Subject subject = new Subject();
        subject.getPrincipals().add(new JwtPrincipal("sub:s"));
        subject.getPrincipals().add(new JwtPrincipal("group:b"));
        JwtLoginModule m = loginWithRefresh(subject, t1);
        try {
            assertEquals(Set.of("sub:s", "group:a", "group:b"), jwtNames(subject));
            tokenEndpointResponse.set(t2);
            m.refreshNow();
            assertEquals("group:a was ours and goes", Set.of("sub:s", "group:b"), jwtNames(subject));
        } finally {
            tokenEndpointResponse.set(null);
            m.logout();
        }
        assertEquals("foreign sub:s and group:b survive logout",
                Set.of("sub:s", "group:b"), jwtNames(subject));
        assertTrue(tokens(subject).isEmpty());
        assertTrue(expiries(subject).isEmpty());
    }

    // K2: second commit and abort undo the previous commit

    private static JwtLoginModule callbackModule(Subject s, AtomicReference<String> tok) {
        Map<String, String> opts = buildOptions(jwksUri(), JWT_ISSUER, AUD, null);
        opts.put("refreshTokenUri", serverBaseUri + "/token");
        opts.put("refreshToken", "dummy-refresh-token");
        opts.put("clientId", "test-client");
        opts.put("clientSecret", "test-secret");
        javax.security.auth.callback.CallbackHandler h = callbacks -> {
            for (javax.security.auth.callback.Callback c : callbacks)
                if (c instanceof javax.security.auth.callback.PasswordCallback)
                    ((javax.security.auth.callback.PasswordCallback) c)
                            .setPassword(tok.get().toCharArray());
        };
        JwtLoginModule m = new JwtLoginModule();
        m.initialize(s, h, new HashMap<>(), opts);
        return m;
    }

    @Test
    public void testSecondCommitOnTheSameModuleReplacesTheFirstIdentity() throws Exception {
        String ta = tokenFor("alice", JWT_ISSUER, inSeconds(1000), "\"groups\":[\"a\"]");
        String tb = tokenFor("bob", JWT_ISSUER, inSeconds(1000), "\"groups\":[\"c\"]");
        AtomicReference<String> tok = new AtomicReference<>(ta);
        Subject subject = new Subject();
        JwtLoginModule m = callbackModule(subject, tok);
        assertTrue(m.login());
        assertTrue(m.commit());
        Thread rt1 = refreshThreadOf(m);
        assertTrue(rt1.isAlive());
        int before = liveRefreshThreads();
        tok.set(tb);
        assertTrue(m.login());
        assertTrue(m.commit());
        Thread rt2 = refreshThreadOf(m);
        try {
            rt1.join(5000);
            assertFalse("the first refresh thread stopped", rt1.isAlive());
            assertTrue(rt2.isAlive());
            assertEquals("one live thread, as before", before, liveRefreshThreads());
            assertEquals(Set.of("sub:bob", "group:c"), jwtNames(subject));
            assertEquals(Set.of(tb), tokens(subject));
            assertEquals(1, expiries(subject).size());
        } finally {
            m.logout();
        }
        assertNothingOfOurs(subject);
        rt2.join(5000);
        assertFalse(rt2.isAlive());
    }

    @Test
    public void testAbortAfterCommitUndoesTheCommit() throws Exception {
        String ta = tokenFor("alice", JWT_ISSUER, inSeconds(1000), "\"groups\":[\"a\"]");
        Subject subject = new Subject();
        JwtLoginModule m = callbackModule(subject, new AtomicReference<>(ta));
        assertTrue(m.login());
        assertTrue(m.commit());
        Thread rt = refreshThreadOf(m);
        assertFalse(tokens(subject).isEmpty());
        assertTrue(m.abort());
        assertNothingOfOurs(subject);
        rt.join(5000);
        assertFalse("the refresh thread stopped", rt.isAlive());
    }

    // K3 and K4: sub is required on both sides; a refusal fails closed

    @Test
    public void testSubLessRefreshIntoAnotherIdentityIsRefused() throws Exception {
        // the review's probe: alice's verified email, refreshed to mallory plus group admin
        assertRefreshFailsClosed(subLess("alice@x", ""), subLess("mallory-marker@x", ",\"groups\":[\"admin\"]"));
    }

    @Test
    public void testSubLessRefreshIsRefusedEvenForTheSameEmail() throws Exception {
        assertRefreshFailsClosed(subLess("alice@x", ""), subLess("alice@x", ""));
    }

    @Test
    public void testRefreshThatAddsOrDropsTheSubIsRefused() throws Exception {
        assertRefreshFailsClosed(subLess("alice@x", ""), tokenFor("alice", JWT_ISSUER, inSeconds(5000), ""));
        assertRefreshFailsClosed(tokenFor("alice", JWT_ISSUER, inSeconds(1000), ""), subLess("alice@x", ""));
    }

    @Test
    public void testRefreshWithADifferentSubFailsClosed() throws Exception {
        assertRefreshFailsClosed(
                tokenFor("alice", JWT_ISSUER, inSeconds(1000), "\"groups\":[\"a\"]"),
                tokenFor("mallory-marker", JWT_ISSUER, inSeconds(5000), "\"groups\":[\"admin\"]"));
    }

    // K5: robustness of the thread

    /** An Error in the thread: caught, recorded by class name, logged SEVERE, token removed. */
    @Test
    public void testAnErrorInTheRefreshThreadFailsClosed() throws Exception {
        // Subject is final: a foreign principal whose equals() throws an Error
        // once, armed after commit, fires inside the refresh swap (the Subject's
        // sets call equals() on their elements) before anything is changed.
        final java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean();
        Subject subject = new Subject();
        subject.getPrincipals().add(new java.security.Principal() {
            @Override public String getName() { return "trap"; }
            @Override public boolean equals(Object o) {
                if (armed.compareAndSet(true, false)) throw new AssertionError("boom-marker");
                return o == this;
            }
            @Override public int hashCode() { return 1; }
        });
        String t1 = tokenFor("s", JWT_ISSUER, inSeconds(3), "\"groups\":[\"a\"]");
        String t2 = tokenFor("s", JWT_ISSUER, inSeconds(5000), "\"groups\":[\"b\"]");
        Capture log = new Capture();
        moduleLogger().addHandler(log);
        tokenEndpointResponse.set(t2);
        JwtLoginModule m = loginWithRefresh(subject, t1);
        Thread rt = refreshThreadOf(m);
        try {
            assertEquals(Set.of(t1), tokens(subject));
            armed.set(true);
            rt.join(15000);
            assertFalse("the thread ended", rt.isAlive());
            assertNothingOfOurs(subject);
            assertEquals("class name only", "java.lang.AssertionError", m.lastRefreshFailure);
            java.util.List<java.util.logging.LogRecord> severe = log.atLeast(java.util.logging.Level.SEVERE);
            assertFalse("logged at SEVERE", severe.isEmpty());
            for (java.util.logging.LogRecord r : severe) {
                assertFalse(r.getMessage().contains("boom-marker"));
                assertFalse("token text", r.getMessage().contains("eyJ"));
                assertNull("no stack trace with the Error message", r.getThrown());
            }
        } finally {
            tokenEndpointResponse.set(null);
            moduleLogger().removeHandler(log);
            m.logout();
        }
    }

    @Test
    public void testInvalidGrantIsRecorded() throws Exception {
        String t1 = tokenFor("s", JWT_ISSUER, inSeconds(1000), "\"groups\":[\"a\"]");
        Subject subject = new Subject();
        JwtLoginModule m = loginWithRefresh(subject, t1);
        tokenEndpointRaw.set("{\"error\":\"invalid_grant\"}");
        try {
            assertFalse("a revoked refresh token is a fatal result", m.refreshNow());
            String why = m.lastRefreshFailure;
            assertNotNull("recorded", why);
            assertTrue(why, why.contains("invalid_grant"));
            // operator ruling 2026-11-02: a revoked refresh token fails closed at once.
            assertNothingOfOurs(subject);
        } finally {
            tokenEndpointRaw.set(null);
            m.logout();
        }
    }

    /** A token past its expiry but inside the skew: one refresh, then not a tight loop. */
    @Test
    public void testExpiredWithinSkewDoesNotSpin() throws Exception {
        String expired = tokenFor("s", JWT_ISSUER, inSeconds(-10), "\"groups\":[\"a\"]");
        tokenHits.set(0);
        tokenEndpointResponse.set(expired);
        Subject subject = new Subject();
        JwtLoginModule m = loginWithRefresh(subject, expired);
        try {
            long deadline = System.currentTimeMillis() + 15000;
            while (tokenHits.get() < 1 && System.currentTimeMillis() < deadline) Thread.sleep(20);
            assertTrue("the thread refreshed at once", tokenHits.get() >= 1);
            Thread.sleep(1500);
            int hits = tokenHits.get();
            assertTrue("refresh attempts in 1.5 s: " + hits, hits <= 2);
        } finally {
            tokenEndpointResponse.set(null);
            m.logout();
        }
    }

    /** Logout while a refresh request is in flight: no WARNING, no SEVERE, thread gone promptly. */
    @Test
    public void testLogoutDuringAnInFlightRefreshLogsNothingAlarming() throws Exception {
        String expired = tokenFor("s", JWT_ISSUER, inSeconds(-10), "");
        Capture log = new Capture();
        moduleLogger().addHandler(log);
        tokenGateEntered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        tokenGate = gate;
        tokenEndpointResponse.set(expired);
        try {
            Subject subject = new Subject();
            JwtLoginModule m = loginWithRefresh(subject, expired);
            Thread rt = refreshThreadOf(m);
            assertTrue("the request reached the endpoint",
                    tokenGateEntered.await(15, java.util.concurrent.TimeUnit.SECONDS));
            m.logout();
            rt.join(2000);
            assertFalse("the thread did not sleep out a back-off", rt.isAlive());
            assertNothingOfOurs(subject);
            assertTrue("alarming lines: " + log.atLeast(java.util.logging.Level.WARNING).size(),
                    log.atLeast(java.util.logging.Level.WARNING).isEmpty());
        } finally {
            tokenGate = null;
            gate.countDown();
            tokenEndpointResponse.set(null);
            moduleLogger().removeHandler(log);
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

    private static String jwksUri() {
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

    private static String base64UrlEncode(String text) {
        return base64UrlEncode(text.getBytes(StandardCharsets.UTF_8));
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
                    "-validity", "1",  // 1 day — minimize footprint
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
