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
/* @test
 * @summary Integration test for JwtLoginModule: full JAAS login/commit/abort/
 *          logout lifecycle with an embedded HTTPS JWKS server.  All key
 *          material is generated ephemerally at test start (no checked-in
 *          private keys).  Requires JDK 21 and the jdk.httpserver module.
 * @modules jdk.httpserver
 * @build JwtLoginModuleTest
 * @run main/othervm --add-modules jdk.httpserver JwtLoginModuleTest
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

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
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import au.zeus.jgdms.security.jwt.JwtExpiryClaim;
import au.zeus.jgdms.security.jwt.JwtLoginModule;
import au.zeus.jgdms.security.jwt.JwtPrincipal;

/**
 * jtreg integration test for {@code JwtLoginModule}.
 *
 * <p>The test harness mirrors the Maven Surefire test in
 * {@code JGDMS/jgdms-security-jwt/src/test/java/net/jini/security/jwt/JwtLoginModuleTest.java}.
 * All assertion failures are reported by throwing {@link RuntimeException}.
 *
 * <h2>Classpath requirements</h2>
 * <p>This test must be run with {@code jgdms-security-jwt.jar} (and its
 * dependency {@code jgdms-platform.jar}) on the classpath.  When using the
 * JGDMS QA harness or a manual jtreg invocation, add the JARs to the
 * {@code -classpath} argument.  The JWT workflow in
 * {@code .github/workflows/jwt-integration-tests.yml} builds these JARs
 * before running jtreg.
 *
 * @since 3.1.1
 */
public class JwtLoginModuleTest {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private static final String JWT_KID     = "test-key-1";
    private static final String JWT_KID_2   = "test-key-2";
    private static final String JWT_ISSUER  = "https://test.idp.local";
    private static final String JWT_SUBJECT = "alice@example.org";
    private static final String JWT_EMAIL   = "alice@example.org";
    private static final String KEYSTORE_PASSWORD = "changeit";

    // -----------------------------------------------------------------------
    // Shared test state
    // -----------------------------------------------------------------------

    private static KeyPair jwtSigningKeyPair;
    private static KeyPair jwtSigningKeyPair2;
    private static HttpsServer httpsServer;
    private static String serverBaseUri;
    private static final AtomicReference<String> currentJwksJson =
            new AtomicReference<>();
    private static SSLContext originalDefaultSslContext;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        setUp();
        try {
            run("testValidJwtPopulatesSubjectPrincipal",  JwtLoginModuleTest::testValidJwtPopulatesSubjectPrincipal);
            run("testValidJwtAddsExpiryClaim",            JwtLoginModuleTest::testValidJwtAddsExpiryClaim);
            run("testExpiredJwtThrowsLoginException",     JwtLoginModuleTest::testExpiredJwtThrowsLoginException);
            run("testWrongIssuerThrowsLoginException",    JwtLoginModuleTest::testWrongIssuerThrowsLoginException);
            run("testHs256AlgorithmRejected",             JwtLoginModuleTest::testHs256AlgorithmRejected);
            run("testAbortDoesNotPopulateSubject",        JwtLoginModuleTest::testAbortDoesNotPopulateSubject);
            run("testLogoutClearsPrincipals",             JwtLoginModuleTest::testLogoutClearsPrincipals);
            run("testKeyRotation",                        JwtLoginModuleTest::testKeyRotation);
            System.out.println("\nAll JWT integration tests PASSED.");
        } finally {
            tearDown();
        }
    }

    // -----------------------------------------------------------------------
    // Setup / teardown
    // -----------------------------------------------------------------------

    private static void setUp() throws Exception {
        // Generate RSA-2048 key pairs for JWT signing
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        jwtSigningKeyPair  = kpg.generateKeyPair();
        jwtSigningKeyPair2 = kpg.generateKeyPair();

        // Generate self-signed TLS cert for the HTTPS server
        KeyStore serverKeyStore = generateServerKeyStore();

        // Server-side SSLContext (provides the certificate to TLS clients)
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKeyStore, KEYSTORE_PASSWORD.toCharArray());
        SSLContext serverSslCtx = SSLContext.getInstance("TLS");
        serverSslCtx.init(kmf.getKeyManagers(), null, null);

        // Client-side SSLContext (trusts the self-signed server cert)
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(serverKeyStore);
        SSLContext clientSslCtx = SSLContext.getInstance("TLS");
        clientSslCtx.init(null, tmf.getTrustManagers(), null);

        // Override JVM default SSL context so JwksKeyCache's HttpClient trusts
        // the test server cert
        originalDefaultSslContext = SSLContext.getDefault();
        SSLContext.setDefault(clientSslCtx);

        // Initialise JWKS JSON
        currentJwksJson.set(buildJwks(jwtSigningKeyPair.getPublic(), JWT_KID));

        // Start the HTTPS server
        httpsServer = HttpsServer.create(
                new InetSocketAddress("localhost", 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(serverSslCtx));
        httpsServer.createContext("/jwks.json", (HttpExchange exchange) -> {
            byte[] body = currentJwksJson.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        httpsServer.setExecutor(null);
        httpsServer.start();

        int port = httpsServer.getAddress().getPort();
        serverBaseUri = "https://localhost:" + port;
        System.out.println("HTTPS JWKS server started at " + serverBaseUri);
    }

    private static void tearDown() throws Exception {
        if (httpsServer != null) httpsServer.stop(0);
        if (originalDefaultSslContext != null)
            SSLContext.setDefault(originalDefaultSslContext);
        currentJwksJson.set(null);
    }

    // -----------------------------------------------------------------------
    // Test cases
    // -----------------------------------------------------------------------

    private static void testValidJwtPopulatesSubjectPrincipal() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);

        Set<JwtPrincipal> principals = subject.getPrincipals(JwtPrincipal.class);
        assertFalse("Subject should contain JwtPrincipals", principals.isEmpty());
        assertTrue("Subject should contain sub principal",
                principals.stream().anyMatch(p -> "sub:alice@example.org".equals(p.getName())));
    }

    private static void testValidJwtAddsExpiryClaim() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);

        Set<JwtExpiryClaim> expiryClaims = subject.getPublicCredentials(JwtExpiryClaim.class);
        assertEqual("Subject should have one JwtExpiryClaim", 1, expiryClaims.size());
        Instant expiry = expiryClaims.iterator().next().getExpiry();
        assertTrue("Expiry should be in the future", expiry.isAfter(Instant.now()));
    }

    private static void testExpiredJwtThrowsLoginException() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, -60);
        Subject subject = new Subject();
        try {
            loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);
            fail("Expected LoginException for expired token");
        } catch (LoginException e) {
            assertTrue("Subject must be clean after failed login",
                    subject.getPrincipals(JwtPrincipal.class).isEmpty());
        }
    }

    private static void testWrongIssuerThrowsLoginException() throws Exception {
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

    private static void testHs256AlgorithmRejected() throws Exception {
        String header  = base64UrlEncode(
                ("{\"alg\":\"HS256\",\"kid\":\"" + JWT_KID + "\"}").getBytes(StandardCharsets.UTF_8));
        long now = Instant.now().getEpochSecond();
        String payload = base64UrlEncode(
                ("{\"iss\":\"" + JWT_ISSUER + "\",\"sub\":\"" + JWT_SUBJECT
                 + "\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".invalidsig";
        Subject subject = new Subject();
        try {
            loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);
            fail("Expected LoginException for HS256 algorithm");
        } catch (LoginException e) {
            // expected
        }
    }

    private static void testAbortDoesNotPopulateSubject() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        JwtLoginModule module = new JwtLoginModule();
        module.initialize(subject, null, new HashMap<>(),
                buildOptions(jwksUri(), JWT_ISSUER, null, token));
        assertTrue("login() should return true", module.login());
        module.abort();
        assertTrue("Principals must be empty after abort",
                subject.getPrincipals(JwtPrincipal.class).isEmpty());
        assertTrue("Public credentials must be empty after abort",
                subject.getPublicCredentials(JwtExpiryClaim.class).isEmpty());
    }

    private static void testLogoutClearsPrincipals() throws Exception {
        String token = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject = new Subject();
        JwtLoginModule module = loginAndCommit(subject, jwksUri(), JWT_ISSUER, null, token);
        assertFalse("Subject should have principals before logout",
                subject.getPrincipals(JwtPrincipal.class).isEmpty());
        module.logout();
        assertTrue("Principals must be empty after logout",
                subject.getPrincipals(JwtPrincipal.class).isEmpty());
    }

    private static void testKeyRotation() throws Exception {
        // First login with key-1
        String token1 = buildJwt(jwtSigningKeyPair.getPrivate(), JWT_KID,
                JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
        Subject subject1 = new Subject();
        loginAndCommit(subject1, jwksUri(), JWT_ISSUER, null, token1);
        assertFalse("First login should populate subject",
                subject1.getPrincipals().isEmpty());

        // Rotate JWKS to expose only key-2
        String savedJwks = currentJwksJson.get();
        currentJwksJson.set(buildJwks(jwtSigningKeyPair2.getPublic(), JWT_KID_2));
        try {
            // Token signed with old key (kid-1) must fail after rotation
            try {
                loginAndCommit(subject1, jwksUri(), JWT_ISSUER, null, token1);
                fail("Old token should be rejected after key rotation");
            } catch (LoginException e) {
                // expected
            }
            // Token signed with new key (kid-2) must succeed
            String token2 = buildJwt(jwtSigningKeyPair2.getPrivate(), JWT_KID_2,
                    JWT_ISSUER, JWT_SUBJECT, JWT_EMAIL, 3600);
            Subject subject2 = new Subject();
            loginAndCommit(subject2, jwksUri(), JWT_ISSUER, null, token2);
            assertFalse("Login with new key must populate subject",
                    subject2.getPrincipals(JwtPrincipal.class).isEmpty());
        } finally {
            currentJwksJson.set(savedJwks);
        }
    }

    // -----------------------------------------------------------------------
    // JAAS helpers
    // -----------------------------------------------------------------------

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void run(String name, ThrowingRunnable test) throws Exception {
        System.out.print("  " + name + " ... ");
        test.run();
        System.out.println("PASS");
    }

    private static JwtLoginModule loginAndCommit(Subject subject, String jwksUri,
            String issuer, String audience, String token) throws LoginException {
        return loginAndCommit(subject, buildOptions(jwksUri, issuer, audience, token));
    }

    private static JwtLoginModule loginAndCommit(Subject subject,
            Map<String, String> opts) throws LoginException {
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

    private static String buildJwks(PublicKey publicKey, String kid) {
        RSAPublicKey rsaPub = (RSAPublicKey) publicKey;
        String n = bigIntToBase64Url(rsaPub.getModulus());
        String e = bigIntToBase64Url(rsaPub.getPublicExponent());
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid
                + "\",\"alg\":\"RS256\",\"n\":\"" + n
                + "\",\"e\":\"" + e + "\"}]}";
    }

    private static String bigIntToBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0)
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // -----------------------------------------------------------------------
    // HTTPS server TLS setup
    // -----------------------------------------------------------------------

    private static KeyStore generateServerKeyStore() throws Exception {
        Path tmpDir = Files.createTempDirectory("jwt-https-test");
        Path p12Path = tmpDir.resolve("server.p12");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "keytool", "-genkeypair",
                    "-keyalg",   "RSA",
                    "-keysize",  "2048",
                    "-validity", "1",
                    "-dname",    "CN=localhost",
                    "-alias",    "server",
                    "-keystore", p12Path.toString(),
                    "-storetype", "PKCS12",
                    "-storepass", KEYSTORE_PASSWORD,
                    "-keypass",   KEYSTORE_PASSWORD,
                    "-noprompt"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();
            if (exitCode != 0)
                throw new RuntimeException(
                        "keytool failed (exit " + exitCode + "): " + output);
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
    // Assertion helpers (jtreg uses plain main(), no JUnit)
    // -----------------------------------------------------------------------

    private static void assertTrue(String message, boolean condition) {
        if (!condition) throw new RuntimeException("ASSERTION FAILED: " + message);
    }

    private static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private static void assertEqual(String message, Object expected, Object actual) {
        if (!expected.equals(actual))
            throw new RuntimeException("ASSERTION FAILED: " + message
                    + " [expected=" + expected + ", actual=" + actual + "]");
    }

    private static void fail(String message) {
        throw new RuntimeException("ASSERTION FAILED: " + message);
    }
}
