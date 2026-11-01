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

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.security.jwt.JwtRawToken;
import net.jini.security.jwt.JwtPrincipal;

/**
 * A JAAS {@link LoginModule} that authenticates a user by validating a JWT
 * (JSON Web Token) against a JWKS (JSON Web Key Set) endpoint.
 *
 * <h2>Configuration options</h2>
 * <table border="1" summary="LoginModule options">
 *   <tr><th>Option</th><th>Required</th><th>Description</th></tr>
 *   <tr><td>{@code jwksUri}</td><td>yes</td>
 *       <td>HTTPS URI of the JWKS endpoint, e.g.
 *           {@code https://idp.example.org/.well-known/jwks.json}</td></tr>
 *   <tr><td>{@code issuer}</td><td>yes</td>
 *       <td>Expected value of the JWT {@code iss} claim</td></tr>
 *   <tr><td>{@code audience}</td><td>no</td>
 *       <td>Expected value of the JWT {@code aud} claim; if omitted the check
 *           is skipped</td></tr>
 *   <tr><td>{@code token}</td><td>no</td>
 *       <td>Static JWT access token; if provided, callbacks are not invoked</td></tr>
 *   <tr><td>{@code refreshTokenUri}</td><td>no</td>
 *       <td>OAuth2 token endpoint for background token refresh</td></tr>
 *   <tr><td>{@code refreshToken}</td><td>no</td>
 *       <td>OAuth2 refresh token value</td></tr>
 *   <tr><td>{@code clientId}</td><td>no</td>
 *       <td>OAuth2 client identifier</td></tr>
 *   <tr><td>{@code clientSecret}</td><td>no</td>
 *       <td>OAuth2 client secret</td></tr>
 *   <tr><td>{@code additionalClaims}</td><td>no</td>
 *       <td>Comma-separated list of additional claim names to extract into
 *           {@link ParsedJwt#allClaims()}</td></tr>
 * </table>
 *
 * <h2>Subject population</h2>
 * On successful commit, the following are added to the {@link Subject}:
 * <ul>
 *   <li>The {@link JwtPrincipal}s the server derives from the same token
 *       (see {@code VerifiedJwt#derivePrincipals}): {@code sub:}, {@code email:}
 *       <em>only if</em> the token has {@code email_verified} equal to JSON
 *       {@code true}, and one {@code group:} per element of {@code groups}.  A
 *       token the server would refuse (empty {@code sub}, {@code email} or
 *       group, a non-string group, or more than 64 principals) fails the
 *       login.</li>
 *   <li>One {@link JwtExpiryClaim} public credential recording the token
 *       expiry instant.</li>
 * </ul>
 *
 * <h2>Background refresh</h2>
 * If {@code refreshTokenUri} and {@code refreshToken} are configured, a daemon
 * thread proactively fetches a new access token at 80% of the token lifetime
 * (i.e. when 20% of the lifetime remains).  On failure the thread retries with
 * exponential back-off (starting at 5 s, doubling up to 5 min).  If the
 * identity provider returns a fatal error (HTTP 400 with
 * {@code "error":"invalid_grant"}) the thread logs at SEVERE level and stops,
 * effectively marking the module as logged-out.
 *
 * <p>A refresh replaces the {@link JwtPrincipal}s, the {@link JwtRawToken} and
 * the {@link JwtExpiryClaim} in one step under the Subject's monitor.  A
 * refreshed token whose {@code sub} or {@code iss} differs from the login
 * token is refused: the Subject keeps the old identity and the thread stops.
 * Logout removes whatever is installed at that time.
 *
 * @since 3.1.1
 */
public final class JwtLoginModule implements LoginModule {

    private static final Logger LOG = Logger.getLogger(JwtLoginModule.class.getName());

    /**
     * Maximum number of additional claim names accepted from the
     * {@code additionalClaims} configuration option.
     */
    private static final int MAX_ADDITIONAL_CLAIMS = 50;

    // ---- JAAS state --------------------------------------------------------

    private Subject subject;
    private CallbackHandler callbackHandler;
    private Map<String, ?> options;

    /** Principals added to the Subject on commit. */
    private final List<JwtPrincipal> addedPrincipals = new ArrayList<>();
    /** Public credentials added to the Subject on commit. */
    private final List<Object> addedCredentials = new ArrayList<>();

    private boolean loginSucceeded = false;
    private boolean commitSucceeded = false;

    /**
     * {@code sub} and {@code iss} of the token the Subject was populated from.
     * A refreshed token must carry the same pair.  Guarded by the Subject's monitor.
     */
    private String installedSub;
    private String installedIss;

    /**
     * Why the last refresh was refused or failed (no token text), or null.
     * Package-private for tests.
     */
    volatile String lastRefreshFailure;

    // ---- Validated token result --------------------------------------------

    private ParsedJwt parsedJwt;
    private Instant tokenExpiry;
    /** The raw JWT compact-serialization string saved between login() and commit(). */
    private String rawToken;

    // ---- Background refresh ------------------------------------------------

    private volatile RefreshThread refreshThread;

    // -----------------------------------------------------------------------
    // LoginModule interface
    // -----------------------------------------------------------------------

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler,
                           Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject     = subject;
        this.callbackHandler = callbackHandler;
        this.options     = options;
    }

    @Override
    public boolean login() throws LoginException {
        loginSucceeded = false;

        String jwksUri  = requireOption("jwksUri");
        String issuer   = requireOption("issuer");
        String audience = (String) options.get("audience");
        String additionalClaimsOpt = (String) options.get("additionalClaims");

        Set<String> additionalClaims = Collections.emptySet();
        if (additionalClaimsOpt != null && !additionalClaimsOpt.isBlank()) {
            String[] claimNames = additionalClaimsOpt.split(",\\s*");
            if (claimNames.length > MAX_ADDITIONAL_CLAIMS)
                throw new LoginException("additionalClaims option specifies more than "
                        + MAX_ADDITIONAL_CLAIMS + " claim names");
            additionalClaims = new HashSet<>(Arrays.asList(claimNames));
        }

        // Obtain the JWT access token
        String token = (String) options.get("token");
        if (token == null || token.isBlank()) {
            token = obtainTokenFromCallbacks();
        }
        if (token == null || token.isBlank())
            throw new LoginException("No JWT access token available (neither 'token' option nor callback provided a value)");

        // Validate
        URI jwksUriParsed;
        try {
            jwksUriParsed = URI.create(jwksUri);
        } catch (IllegalArgumentException e) {
            throw new LoginException("Invalid jwksUri: " + jwksUri);
        }

        JwksKeyCache cache = new JwksKeyCache(jwksUriParsed);
        JwtValidator validator = new JwtValidator(cache, issuer, audience, additionalClaims);
        try {
            parsedJwt = validator.validate(token);
        } catch (JwtValidationException e) {
            throw new LoginException("JWT validation failed: " + e.getMessage());
        }

        // Extract expiry instant from allClaims
        String expStr = parsedJwt.allClaims().get("exp");
        if (expStr != null) {
            try {
                tokenExpiry = Instant.ofEpochSecond(Long.parseLong(expStr));
            } catch (NumberFormatException ignore) {
                tokenExpiry = null;
            }
        }

        loginSucceeded = true;
        rawToken = token;
        return true;
    }

    @Override
    public boolean commit() throws LoginException {
        if (!loginSucceeded) {
            clearState();
            return false;
        }

        // --- Build principals ---
        addedPrincipals.clear();
        addedCredentials.clear();

        addJwtPrincipals(addedPrincipals, parsedJwt);

        // --- Public credentials ---
        Instant expiry = tokenExpiry != null ? tokenExpiry : Instant.now().plusSeconds(3600);
        JwtExpiryClaim expiryClaim = new JwtExpiryClaim(expiry);
        addedCredentials.add(expiryClaim);
        // JwtRawToken carries the compact-serialization for JERI wire transmission
        // (BasicInvocationHandler.writeUserSubjects) so the server-side JwtVerifier
        // can independently verify the token.  It is stored as a public credential
        // because writeUserSubjects executes outside a Subject.doAs context.
        if (rawToken != null) {
            addedCredentials.add(new JwtRawToken(rawToken));
        }

        // --- Populate Subject ---
        // Under the Subject's monitor, the same lock a refresh swaps under, so
        // a reader that locks the Subject sees principals and token together.
        synchronized (subject) {
            subject.getPrincipals().addAll(addedPrincipals);
            subject.getPublicCredentials().addAll(addedCredentials);
            installedSub = parsedJwt.subject();
            installedIss = parsedJwt.allClaims().get("iss");
        }

        // --- Start background refresh thread if configured ---
        String refreshTokenUri = (String) options.get("refreshTokenUri");
        String refreshToken    = (String) options.get("refreshToken");
        if (refreshTokenUri != null && !refreshTokenUri.isBlank()
                && refreshToken != null && !refreshToken.isBlank()) {
            Object clientIdObj     = options.get("clientId");
            Object clientSecretObj = options.get("clientSecret");
            String clientId        = clientIdObj     != null ? clientIdObj.toString()     : "";
            String clientSecret    = clientSecretObj != null ? clientSecretObj.toString() : "";
            RefreshThread rt = new RefreshThread(
                    this, subject, refreshTokenUri, refreshToken,
                    clientId, clientSecret, expiry,
                    (String) options.get("jwksUri"),
                    (String) options.get("issuer"),
                    (String) options.get("audience"));
            rt.setDaemon(true);
            rt.setName("JwtLoginModule-refresh");
            rt.start();
            refreshThread = rt;
        }

        commitSucceeded = true;
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        if (!loginSucceeded) return false;
        clearState();
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        // Stop the refresh thread and remove what is installed, under one lock:
        // a refresh in flight checks 'running' under the same lock, so it cannot
        // install a token after this point.  addedPrincipals / addedCredentials
        // always hold what is installed now, refreshed values included.
        synchronized (subject) {
            RefreshThread rt = refreshThread;
            if (rt != null) {
                rt.shutdown();
                refreshThread = null;
            }
            if (commitSucceeded) {
                subject.getPrincipals().removeAll(addedPrincipals);
                subject.getPublicCredentials().removeAll(addedCredentials);
            }
            clearState();
        }
        return true;
    }

    /**
     * Test hook: performs one refresh now, on the calling thread.
     *
     * @throws IllegalStateException if no refresh is configured
     */
    void refreshNow() throws Exception {
        RefreshThread rt = refreshThread;
        if (rt == null) throw new IllegalStateException("no refresh configured");
        rt.refreshOnce();
    }

    /**
     * Replaces the Subject's principals, {@link JwtRawToken} and expiry claim
     * with those of a refreshed token, in one step under the Subject's monitor.
     * Refuses a token whose {@code sub} or {@code iss} differs from the one
     * installed; the Subject is left unchanged in every refusal.
     */
    private void installRefreshed(RefreshThread caller, ParsedJwt newParsed,
                                  String newToken, Instant newExpiry)
            throws FatalRefreshException {
        synchronized (subject) {
            if (!caller.running || !commitSucceeded)
                throw new FatalRefreshException("login module is logged out");
            if (!java.util.Objects.equals(installedSub, newParsed.subject())
                    || !java.util.Objects.equals(installedIss, newParsed.allClaims().get("iss")))
                throw new FatalRefreshException(
                        "refreshed token has a different sub or iss; refused");
            if (subject.isReadOnly())
                throw new FatalRefreshException("Subject is read-only; refresh not installed");

            List<JwtPrincipal> newPrincipals = new ArrayList<>();
            addJwtPrincipals(newPrincipals, newParsed);
            List<Object> newCredentials = new ArrayList<>();
            newCredentials.add(new JwtExpiryClaim(newExpiry));
            newCredentials.add(new JwtRawToken(newToken));

            // Only what this module installed is removed, not principals other
            // login modules added.
            subject.getPrincipals().removeAll(addedPrincipals);
            subject.getPublicCredentials().removeAll(addedCredentials);
            subject.getPrincipals().addAll(newPrincipals);
            subject.getPublicCredentials().addAll(newCredentials);
            addedPrincipals.clear();
            addedPrincipals.addAll(newPrincipals);
            addedCredentials.clear();
            addedCredentials.addAll(newCredentials);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static void addJwtPrincipals(List<JwtPrincipal> out, ParsedJwt parsed) {
        for (Principal p : parsed.principals()) out.add((JwtPrincipal) p);
    }

    private String requireOption(String name) throws LoginException {
        Object val = options.get(name);
        if (val == null || val.toString().isBlank())
            throw new LoginException("Required JwtLoginModule option '" + name + "' is missing or blank");
        return val.toString();
    }

    private String obtainTokenFromCallbacks() throws LoginException {
        if (callbackHandler == null) return null;
        PasswordCallback pwCb = new PasswordCallback("JWT access token: ", false);
        try {
            callbackHandler.handle(new Callback[]{pwCb});
        } catch (IOException | UnsupportedCallbackException e) {
            throw new LoginException("Callback failed: " + e.getMessage());
        }
        char[] pwd = pwCb.getPassword();
        if (pwd == null) return null;
        String token = new String(pwd);
        pwCb.clearPassword();
        return token;
    }

    private void clearState() {
        parsedJwt     = null;
        tokenExpiry   = null;
        rawToken      = null;
        loginSucceeded = false;
        commitSucceeded = false;
        addedPrincipals.clear();
        addedCredentials.clear();
    }

    // -----------------------------------------------------------------------
    // Background refresh thread
    // -----------------------------------------------------------------------

    /**
     * Daemon thread that proactively refreshes the OAuth2 access token.
     *
     * <p>The thread wakes up at 80% of the token's lifetime before expiry,
     * calls the token endpoint with the refresh token, validates the new
     * access token, and updates the Subject.
     */
    private static final class RefreshThread extends Thread {

        private static final long MIN_BACKOFF_MS  =  5_000L;
        private static final long MAX_BACKOFF_MS  = 300_000L; // 5 minutes

        /**
         * Maximum size of the OAuth2 token endpoint HTTP response body in bytes
         * (64 KiB).  Token endpoint responses are typically well under 4 KiB;
         * this limit prevents memory exhaustion from a rogue or misconfigured server.
         */
        private static final int MAX_TOKEN_RESPONSE_BYTES = 65_536;

        /**
         * Fraction of the remaining token lifetime at which proactive refresh
         * is triggered.  A value of 0.8 means the thread wakes when 80% of
         * the remaining lifetime has elapsed (i.e. 20% is left before expiry).
         */
        private static final double REFRESH_LIFETIME_FRACTION = 0.8;

        private final JwtLoginModule owner;
        private final Subject  subject;
        private final String   refreshTokenUri;
        private volatile String refreshToken;
        private final String   clientId;
        private final String   clientSecret;
        private volatile Instant currentExpiry;
        private final String   jwksUri;
        private final String   issuer;
        private final String   audience;

        volatile boolean running = true;

        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        RefreshThread(JwtLoginModule owner, Subject subject, String refreshTokenUri, String refreshToken,
                      String clientId, String clientSecret, Instant currentExpiry,
                      String jwksUri, String issuer, String audience) {
            this.owner          = owner;
            this.subject        = subject;
            this.refreshTokenUri = refreshTokenUri;
            this.refreshToken   = refreshToken;
            this.clientId       = clientId;
            this.clientSecret   = clientSecret;
            this.currentExpiry  = currentExpiry;
            this.jwksUri        = jwksUri;
            this.issuer         = issuer;
            this.audience       = audience;
        }

        void shutdown() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            while (running) {
                // Determine when to wake up: at 80% of lifetime elapsed
                long sleepMs = computeSleepMs();
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (!running) return;
                    }
                }
                if (!running) return;

                // Attempt refresh with exponential back-off
                long backoffMs = MIN_BACKOFF_MS;
                boolean refreshed = false;
                while (running && !refreshed) {
                    try {
                        refreshed = refreshOnce();
                        if (!refreshed) {
                            // Fatal failure (invalid_grant etc.)
                            LOG.severe("JWT refresh token has been revoked or is invalid; "
                                    + "marking session as logged-out");
                            running = false;
                            return;
                        }
                    } catch (FatalRefreshException e) {
                        LOG.severe("JWT refresh permanently failed: " + e.getMessage()
                                + "; marking session as logged-out");
                        running = false;
                        return;
                    } catch (Exception e) {
                        LOG.log(Level.WARNING,
                                "JWT refresh failed (will retry in " + backoffMs + "ms)", e);
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            if (!running) return;
                        }
                        backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                    }
                }
            }
        }

        /** {@link #doRefresh()}, recording any failure on the module (no token text). */
        boolean refreshOnce() throws Exception {
            try {
                return doRefresh();
            } catch (Exception e) {
                owner.lastRefreshFailure = e.getClass().getSimpleName() + ": " + e.getMessage();
                throw e;
            }
        }

        /** Computes how long to sleep before attempting a proactive refresh. */
        private long computeSleepMs() {
            Instant expiry = currentExpiry;
            if (expiry == null) return MIN_BACKOFF_MS;
            Instant now = Instant.now();
            long remainingMs = expiry.toEpochMilli() - now.toEpochMilli();
            if (remainingMs <= 0) return 0; // already expired, refresh immediately
            // Refresh at REFRESH_LIFETIME_FRACTION of remaining lifetime
            return (long) (remainingMs * REFRESH_LIFETIME_FRACTION);
        }

        /**
         * Performs the OAuth2 refresh_token grant, validates the new token, and
         * updates the Subject.
         *
         * @return {@code true} if refresh succeeded; {@code false} if the refresh
         *         token is permanently invalid (fatal error)
         * @throws FatalRefreshException on a fatal error that should stop the thread
         * @throws Exception             on transient errors (caller will retry)
         */
        private boolean doRefresh() throws Exception {
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + urlEncode(refreshToken)
                    + "&client_id="     + urlEncode(clientId)
                    + "&client_secret=" + urlEncode(clientSecret);

            HttpRequest request = HttpRequest.newBuilder(URI.create(refreshTokenUri))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .build();

            // Use InputStream to enforce a hard size limit before allocating a
            // large String, defending against memory-exhaustion from a rogue
            // token endpoint.
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            String responseBody;
            try (InputStream is = response.body()) {
                byte[] bytes = is.readNBytes(MAX_TOKEN_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_TOKEN_RESPONSE_BYTES) {
                    throw new Exception("Token endpoint response exceeds size limit of "
                            + MAX_TOKEN_RESPONSE_BYTES + " bytes");
                }
                responseBody = new String(bytes, StandardCharsets.UTF_8);
            }

            if (status == 400) {
                // Check for invalid_grant error (permanent failure)
                if (responseBody.contains("\"invalid_grant\""))
                    return false; // fatal — refresh token revoked
                throw new FatalRefreshException("Token endpoint returned HTTP 400");
            }
            if (status != 200) {
                throw new Exception("Token endpoint returned HTTP " + status);
            }

            // Parse response JSON for access_token, refresh_token, expires_in
            Map<String, String> tokenResponse;
            try {
                tokenResponse = JwtValidator.parseTokenResponse(responseBody);
            } catch (IllegalArgumentException e) {
                // The body holds the new tokens; say nothing about its content.
                throw new Exception("Token endpoint response is not a valid JSON object");
            }
            String newAccessToken = tokenResponse.get("access_token");
            if (newAccessToken == null || newAccessToken.isBlank())
                throw new Exception("Token endpoint response missing 'access_token'");

            // Update refresh_token if a new one was provided (token rotation)
            String newRefreshToken = tokenResponse.get("refresh_token");
            if (newRefreshToken != null && !newRefreshToken.isBlank())
                this.refreshToken = newRefreshToken;

            // Validate and install the new token
            JwksKeyCache cache = new JwksKeyCache(URI.create(jwksUri));
            JwtValidator validator = new JwtValidator(cache, issuer, audience);
            ParsedJwt newParsed = validator.validate(newAccessToken);

            // Compute new expiry
            String expStr = newParsed.allClaims().get("exp");
            Instant newExpiry = expStr != null
                    ? Instant.ofEpochSecond(Long.parseLong(expStr))
                    : Instant.now().plusSeconds(3600);

            // Replace principals, token and expiry together; refuses another identity.
            owner.installRefreshed(this, newParsed, newAccessToken, newExpiry);
            currentExpiry = newExpiry;

            LOG.fine("JWT access token refreshed; new expiry: " + newExpiry);
            return true;
        }

        private static String urlEncode(String value) {
            if (value == null) return "";
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }

    /** Thrown to signal a permanent refresh failure that should stop the refresh thread. */
    private static final class FatalRefreshException extends Exception {
        FatalRefreshException(String message) { super(message); }
    }
}
