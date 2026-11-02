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
 *   <tr><td>{@code responseBodyTimeoutMs}</td><td>no</td>
 *       <td>Total time allowed for reading one response body from the token endpoint or the
 *           JWKS endpoint, 100 to 120000, default 10000.  A server that sends its body more
 *           slowly is a transient failure (a failed login at first login).</td></tr>
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
 * (i.e. when 20% of the lifetime remains).
 *
 * <p><b>Security manager.</b>  {@code commit} creates and starts the thread in a
 * privileged block ({@link NewDaemonThread}), so a policy grants the thread
 * permissions to this module's codebase and not to the code that calls the
 * module.  The thread does not inherit the caller's context: it carries only the
 * protection domain of this module (and the platform base), so the refresh (the
 * HTTP calls to the token endpoint and the JWKS, and the update of the Subject) needs
 * its permissions from this module's codebase alone.  The context class loader is the system
 * class loader.  If the thread cannot be started, {@code commit} undoes itself and
 * throws a {@link LoginException}.
 *
 * <p><b>Policy for the module's codebase.</b>  The refresh thread contacts the URIs
 * it is given in the login options ({@code refreshTokenUri}, {@code jwksUri}), and
 * it does so with this module's permissions, not the caller's.  Code that may choose
 * the login configuration (for example one holding {@code createLoginContext} with its
 * own {@code Configuration}) thereby chooses those URIs.  A deployment policy must
 * therefore grant this module's codebase {@code URLPermission} for only the exact token
 * endpoint URL with method {@code POST} and the exact JWKS URL with method {@code GET},
 * never a wildcard such as {@code https://idp/-}, {@code https://*} or {@code *:*}.
 * Callers need no token endpoint permission.  The jgdms-collections jar needs none of
 * the thread, HTTP or Subject permissions of this module (it is not on the thread's
 * context since {@link NewDaemonThread} creates the threads).
 *
 * <p><b>Permanent failures</b> log at SEVERE level, stop the thread and remove
 * the token, expiry claim and principals this module installed, so the Subject
 * fails closed at once:
 * <ul>
 *   <li>HTTP 400 (with {@code "error":"invalid_grant"} or without), HTTP 401
 *       and HTTP 403 from the token endpoint (the refresh token or the client
 *       credentials are rejected);</li>
 *   <li>an HTTP 200 whose body is not one JSON object, or has no
 *       {@code access_token} (the identity provider is malfunctioning);</li>
 *   <li>a refreshed token that fails validation: a bad signature, a
 *       {@code kid} that the reachable JWKS does not list (the JWKS is fetched
 *       again once before this is decided), another {@code iss}, a wrong
 *       {@code aud}, expired, missing {@code exp}, malformed claims, or not a
 *       JWT at all;</li>
 *   <li>a refreshed token with another {@code sub}, or either token without a
 *       {@code sub} (see below);</li>
 *   <li>an {@code Error} in the thread (an {@code OutOfMemoryError} excepted).</li>
 * </ul>
 * <p><b>Transient failures</b> keep the token, log at WARNING level and retry
 * with exponential back-off (starting at 5 s, doubling up to 5 min): a network
 * error or timeout, a response over the size limit, and any other HTTP status
 * (including 5xx, 408 and 429), and a JWKS endpoint that cannot be read (a
 * connection error or timeout, an HTTP status other than 200, a response over
 * the size limit, or a body that is not a JWKS).  The old token is still valid
 * until it expires, so it stays; {@code lastRefreshFailure} holds the class name
 * {@code JwksUnavailableException} only.  A first login with the JWKS unreadable
 * fails with a {@link LoginException}: there is no old token to keep.
 *
 * <p>A refresh replaces the {@link JwtPrincipal}s, the {@link JwtRawToken} and
 * the {@link JwtExpiryClaim} in one step under the Subject's monitor.  A
 * refreshed token is refused when its {@code sub} or {@code iss} differs from
 * the login token, or when either token has no {@code sub}.  A token already
 * past its expiry, inside the validator's clock skew, is refreshed once and
 * then no more often than every 5 s.
 *
 * <p>The client does not enforce the server's maximum token lifetime (default
 * 1 hour in {@code DefaultJwtVerifier}).  A deployment whose identity provider
 * issues longer-lived tokens must raise {@code maxLifetime} on the server
 * verifier, or the server refuses a token this module accepted.
 *
 * <p>The module removes only what it actually added to the Subject: a
 * principal or credential that was already present (installed by another
 * module) is never removed.  A second {@code commit} and an {@code abort}
 * after a {@code commit} first undo the previous commit.  Logout removes
 * whatever is installed at that time.
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

    /**
     * The first {@link ExceptionInInitializerError} (or, if the class was already unusable
     * when this module first looked, the {@link NoClassDefFoundError}) from starting the
     * body-deadline watchdog, or null.  Holds the permission failure that explains it; no
     * token text can be in it.
     */
    private static volatile Throwable watchdogFailure;

    // ---- JAAS state --------------------------------------------------------

    private Subject subject;
    private CallbackHandler callbackHandler;
    private Map<String, ?> options;

    /**
     * Principals this module added to the Subject (Set.add returned true),
     * refreshed values included.  Guarded by the monitor of {@link #installedOn}.
     */
    private final List<JwtPrincipal> addedPrincipals = new ArrayList<>();
    /** Public credentials this module added, same rule. */
    private final List<Object> addedCredentials = new ArrayList<>();

    /**
     * The Subject the last commit installed into; undo and logout lock and
     * clean this one even if {@link #initialize} has since been given another.
     */
    private volatile Subject installedOn;

    /** Claim names the {@code additionalClaims} option asks for; the refresh reads tokens with the same set. */
    private Set<String> additionalClaims = Collections.emptySet();
    /** Total time allowed for reading one response body (token endpoint or JWKS). */
    private long bodyDeadlineMs = BoundedBody.DEFAULT_DEADLINE_MS;

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

    /**
     * The validated result of the last refresh that was installed, or null.  Package-private
     * for tests, which read {@link ParsedJwt#allClaims()}; nothing in the module reads it.
     */
    volatile ParsedJwt lastRefreshed;

    // ---- Validated token result --------------------------------------------

    private ParsedJwt parsedJwt;
    private Instant tokenExpiry;
    /** The raw JWT compact-serialization string saved between login() and commit(). */
    private String rawToken;

    // ---- Background refresh ------------------------------------------------

    private volatile RefreshThread refreshThread;

    /** Test hook: how commit creates and starts the refresh thread (privileged, see {@link BoundedBody#startThread}). */
    static volatile java.util.function.BiFunction<Runnable, String, Thread> threadStarter =
            new java.util.function.BiFunction<Runnable, String, Thread>() {
                // Not a lambda: loading this class must not need the lambda (DefineClass) grant.
                @Override
                public Thread apply(Runnable r, String name) {
                    return BoundedBody.startThread(r, name);
                }
            };

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

        try {
            bodyDeadlineMs = BoundedBody.parseDeadline(options.get("responseBodyTimeoutMs"));
        } catch (IllegalArgumentException e) {
            throw new LoginException(e.getMessage());
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // The body-deadline watchdog thread could not be started (under a SecurityManager:
            // the module's codebase lacks RuntimePermission createPlatformThread or
            // setContextClassLoader).  Reading bodies without a deadline is not an option, so fail.
            // BoundedBody fails to initialise once; every later use only throws NoClassDefFoundError,
            // which names nothing.  So the first failure is kept and is the cause of every login that follows.
            Throwable first = watchdogFailure;
            if (first == null) {
                synchronized (JwtLoginModule.class) {
                    if (watchdogFailure == null) watchdogFailure = e;
                    first = watchdogFailure;
                }
            }
            LoginException le = new LoginException("JWT login unavailable: the response-body deadline "
                    + "watchdog could not be started (" + (first.getCause() != null ? first.getCause() : first) + ")");
            le.initCause(first);
            throw le;
        }

        Set<String> additionalClaims = Collections.emptySet();
        if (additionalClaimsOpt != null && !additionalClaimsOpt.isBlank()) {
            String[] claimNames = additionalClaimsOpt.split(",\\s*");
            if (claimNames.length > MAX_ADDITIONAL_CLAIMS)
                throw new LoginException("additionalClaims option specifies more than "
                        + MAX_ADDITIONAL_CLAIMS + " claim names");
            additionalClaims = new HashSet<>(Arrays.asList(claimNames));
        }
        this.additionalClaims = additionalClaims;

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

        JwksKeyCache cache = new JwksKeyCache(jwksUriParsed, bodyDeadlineMs);
        JwtValidator validator = new JwtValidator(cache, issuer, audience, additionalClaims);
        try {
            parsedJwt = validator.validate(token);
        } catch (JwksUnavailableException e) {
            // No old token to keep at first login: fail, and say it is the keys, not the token.
            throw new LoginException("JWT validation failed: the JWKS endpoint could not be read ("
                    + e.getMessage() + ")");
        } catch (JwtValidationException e) {
            throw new LoginException("JWT validation failed: " + e.getMessage());
        } finally {
            // The cache served this one validation; its HTTP client must not outlive it.
            cache.close();
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
            clearLoginState();
            return false;
        }

        // A second commit on this instance (a LoginContext reuses its modules)
        // first undoes the previous one: its thread and what it installed.
        Subject previous = installedOn;
        if (previous != null) {
            synchronized (previous) {
                uninstallLocked();
            }
        }

        // --- Build principals ---
        List<JwtPrincipal> principals = new ArrayList<>();
        addJwtPrincipals(principals, parsedJwt);

        // --- Public credentials ---
        Instant expiry = tokenExpiry != null ? tokenExpiry : Instant.now().plusSeconds(3600);
        List<Object> credentials = new ArrayList<>();
        credentials.add(new JwtExpiryClaim(expiry));
        // JwtRawToken carries the compact-serialization for JERI wire transmission
        // (BasicInvocationHandler.writeUserSubjects) so the server-side JwtVerifier
        // can independently verify the token.  It is stored as a public credential
        // because writeUserSubjects executes outside a Subject.doAs context.
        if (rawToken != null) {
            credentials.add(new JwtRawToken(rawToken));
        }

        String refreshTokenUri = (String) options.get("refreshTokenUri");
        String refreshToken    = (String) options.get("refreshToken");
        boolean refresh = refreshTokenUri != null && !refreshTokenUri.isBlank()
                && refreshToken != null && !refreshToken.isBlank();

        // --- Populate Subject ---
        // Under the Subject's monitor, the same lock a refresh swaps under, so
        // a reader that locks the Subject sees principals and token together.
        // Only what Set.add reports as added is ours to remove later.
        Subject target = subject;
        synchronized (target) {
            installedOn = target;
            for (JwtPrincipal p : principals)
                if (target.getPrincipals().add(p)) addedPrincipals.add(p);
            for (Object c : credentials)
                if (target.getPublicCredentials().add(c)) addedCredentials.add(c);
            installedSub = parsedJwt.subject();
            installedIss = parsedJwt.allClaims().get("iss");
            commitSucceeded = true;

            // --- Start background refresh thread if configured ---
            if (refresh) {
                Object clientIdObj     = options.get("clientId");
                Object clientSecretObj = options.get("clientSecret");
                String clientId        = clientIdObj     != null ? clientIdObj.toString()     : "";
                String clientSecret    = clientSecretObj != null ? clientSecretObj.toString() : "";
                RefreshThread rt = new RefreshThread(
                        this, target, refreshTokenUri, refreshToken,
                        clientId, clientSecret, expiry,
                        (String) options.get("jwksUri"),
                        (String) options.get("issuer"),
                        (String) options.get("audience"),
                        additionalClaims, bodyDeadlineMs);
                refreshThread = rt;
                try {
                    // Privileged: the thread permissions are the module's, not the caller's.
                    rt.thread = threadStarter.apply(rt, "JwtLoginModule-refresh");
                } catch (RuntimeException | Error e) {
                    // No refresh thread means a token nobody refreshes: undo the commit and say so.
                    uninstallLocked();
                    LoginException le = new LoginException(
                            "JWT refresh thread could not be started: " + e.getClass().getName());
                    le.initCause(e);
                    throw le;
                }
            }
        }
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        if (!loginSucceeded) return false;
        Subject target = installedOn;
        if (target != null) {
            // abort after a successful commit: undo it
            synchronized (target) {
                uninstallLocked();
            }
        }
        clearLoginState();
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        // Stop the refresh thread and remove what is installed, under one lock:
        // a refresh in flight checks 'running' under the same lock, so it cannot
        // install a token after this point.  addedPrincipals / addedCredentials
        // always hold what is installed now, refreshed values included.
        Subject target = installedOn != null ? installedOn : subject;
        synchronized (target) {
            uninstallLocked();
            clearLoginState();
        }
        return true;
    }

    /**
     * Stops the refresh thread and removes what this module added.  The caller
     * holds the monitor of {@link #installedOn}.
     */
    private void uninstallLocked() {
        RefreshThread rt = refreshThread;
        if (rt != null) {
            rt.shutdown();
            refreshThread = null;
        }
        removeInstalledLocked();
        installedSub = null;
        installedIss = null;
        commitSucceeded = false;
    }

    /** Removes exactly what this module added; the caller holds the Subject's monitor. */
    private void removeInstalledLocked() {
        Subject target = installedOn;
        if (target == null) return;
        target.getPrincipals().removeAll(addedPrincipals);
        target.getPublicCredentials().removeAll(addedCredentials);
        addedPrincipals.clear();
        addedCredentials.clear();
    }

    /**
     * Fails closed: stops the thread and removes the token, the expiry claim
     * and the principals this module installed.  The caller holds the Subject's
     * monitor.  Does nothing if the thread was already stopped (logout, undo).
     */
    private void failClosedLocked(RefreshThread caller) {
        if (!caller.running) return;
        caller.shutdown();
        commitSucceeded = false;
        removeInstalledLocked();
    }

    /** {@link #failClosedLocked} for the thread's own failure path; records {@code why}. */
    private void failClosed(RefreshThread caller, String why) {
        synchronized (caller.subject) {
            lastRefreshFailure = why;
            try {
                failClosedLocked(caller);
            } catch (RuntimeException e) {
                // A read-only Subject, or a SecurityManager refusing the removal:
                // the token cannot be taken out; say so, without token text.
                LOG.log(Level.SEVERE, "JWT refresh stopped but the token could not be removed: "
                        + e.getClass().getName());
            }
        }
    }

    /**
     * Test hook: performs one refresh now, on the calling thread.
     *
     * @return false if the identity provider rejected the refresh token
     * @throws IllegalStateException if no refresh is configured
     */
    boolean refreshNow() throws Exception {
        RefreshThread rt = refreshThread;
        if (rt == null) throw new IllegalStateException("no refresh configured");
        return rt.refreshOnce();
    }

    /**
     * Replaces the Subject's principals, {@link JwtRawToken} and expiry claim
     * with those of a refreshed token, in one step under the Subject's monitor.
     * A token with another {@code sub} or {@code iss}, or a login or refreshed
     * token without {@code sub}, is refused and the module fails closed (see
     * {@link #failClosedLocked}).
     */
    private void installRefreshed(RefreshThread caller, ParsedJwt newParsed,
                                  String newToken, Instant newExpiry)
            throws FatalRefreshException {
        Subject target = caller.subject;
        synchronized (target) {
            if (!caller.running || !commitSucceeded)
                throw new FatalRefreshException("login module is logged out", true);
            String newSub = newParsed.subject();
            if (installedSub == null || installedSub.isEmpty() || newSub == null || newSub.isEmpty()) {
                failClosedLocked(caller);
                throw new FatalRefreshException(
                        "refresh refused: the login token or the refreshed token has no sub", false);
            }
            if (!installedSub.equals(newSub)
                    || !java.util.Objects.equals(installedIss, newParsed.allClaims().get("iss"))) {
                failClosedLocked(caller);
                throw new FatalRefreshException(
                        "refreshed token has a different sub or iss; refused", false);
            }
            if (target.isReadOnly())
                throw new FatalRefreshException("Subject is read-only; refresh not installed", false);

            List<JwtPrincipal> newPrincipals = new ArrayList<>();
            addJwtPrincipals(newPrincipals, newParsed);
            List<Object> newCredentials = new ArrayList<>();
            newCredentials.add(new JwtExpiryClaim(newExpiry));
            newCredentials.add(new JwtRawToken(newToken));

            // Only what this module added is removed, not principals other
            // login modules installed (including equal ones).
            removeInstalledLocked();
            for (JwtPrincipal p : newPrincipals)
                if (target.getPrincipals().add(p)) addedPrincipals.add(p);
            for (Object c : newCredentials)
                if (target.getPublicCredentials().add(c)) addedCredentials.add(c);
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

    /** Forgets the login-time values; what a commit installed is {@link #uninstallLocked}'s. */
    private void clearLoginState() {
        parsedJwt     = null;
        tokenExpiry   = null;
        rawToken      = null;
        loginSucceeded = false;
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
    private static final class RefreshThread implements Runnable {

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
        private final Set<String> additionalClaims;
        private final long     bodyDeadlineMs;

        volatile boolean running = true;

        /** The thread running this; set once by commit, which holds the Subject's monitor. */
        volatile Thread thread;

        private final HttpClient httpClient = JwksKeyCache.newHttpClient();

        RefreshThread(JwtLoginModule owner, Subject subject, String refreshTokenUri, String refreshToken,
                      String clientId, String clientSecret, Instant currentExpiry,
                      String jwksUri, String issuer, String audience,
                      Set<String> additionalClaims, long bodyDeadlineMs) {
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
            this.additionalClaims = additionalClaims;
            this.bodyDeadlineMs = bodyDeadlineMs;
        }

        void shutdown() {
            running = false;
            Thread t = thread;
            if (t != null) t.interrupt();
            // Closes the client without waiting (BoundedBody.closeAsync): logout must not block on it.
            JwksKeyCache.closeClient(httpClient);
        }

        @Override
        public void run() {
            try {
                loop();
            } catch (OutOfMemoryError oom) {
                throw oom;
            } catch (Throwable t) {
                // An Error (or other non-Exception Throwable) must not leave the
                // Subject holding a token nobody refreshes: record the class
                // name only (no message, no token text), log, fail closed.
                String name = t.getClass().getName();
                if (running)
                    LOG.log(Level.SEVERE, "JWT refresh thread died with " + name
                            + "; removing the token and principals");
                owner.failClosed(this, name);
            } finally {
                // However the thread ends (logout, a permanent failure, an Error) its client goes too.
                JwksKeyCache.closeClient(httpClient);
            }
        }

        private void loop() {
            boolean attempted = false;
            while (running) {
                // Determine when to wake up: at 80% of lifetime elapsed
                long sleepMs = computeSleepMs();
                // A token already past its expiry (inside the clock skew) gives 0;
                // never attempt again sooner than the first back-off step.
                if (attempted) sleepMs = Math.max(sleepMs, MIN_BACKOFF_MS);
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
                            // failClosed (in refreshOnce) already stopped the thread and removed
                            // our token; say so once, without token text.
                            LOG.severe("JWT refresh token has been revoked or is invalid; "
                                    + "marking session as logged-out");
                            running = false;
                            return;
                        }
                        attempted = true;
                    } catch (FatalRefreshException e) {
                        // loggedOut: logout or an undo won the race; nothing to report.
                        if (!e.loggedOut) {
                            LOG.severe("JWT refresh permanently failed: " + e.getMessage()
                                    + "; marking session as logged-out");
                            // A permanent failure (HTTP 400 other than invalid_grant, or a refusal)
                            // also takes our token out: a stopped thread must not leave a token behind.
                            owner.failClosed(this, "FatalRefreshException: " + e.getMessage());
                        }
                        running = false;
                        return;
                    } catch (Exception e) {
                        // A logout interrupts an in-flight send: not a failure.
                        if (!running) return;
                        attempted = true;
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
                boolean ok = doRefresh();
                // invalid_grant: the identity provider revoked or expired the refresh token, so the
                // session is over.  Fail closed now (take our token and principals out) instead of
                // serving an identity that nobody can refresh until the access token expires.
                if (!ok) owner.failClosed(this, "invalid_grant: the refresh token was rejected");
                return ok;
            } catch (JwksUnavailableException e) {
                // Class name only: the message is for the log, the record is for callers.
                owner.lastRefreshFailure = e.getClass().getSimpleName();
                throw e;
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
            // One total time limit for the whole body (a SocketTimeoutException is transient):
            // the request timeout above covers only the headers.
            byte[] bytes = BoundedBody.read(response.body(), MAX_TOKEN_RESPONSE_BYTES, bodyDeadlineMs);
            if (bytes.length > MAX_TOKEN_RESPONSE_BYTES) {
                throw new Exception("Token endpoint response exceeds size limit of "
                        + MAX_TOKEN_RESPONSE_BYTES + " bytes");
            }
            String responseBody = new String(bytes, StandardCharsets.UTF_8);

            if (status == 400) {
                // Check for invalid_grant error (permanent failure)
                if (responseBody.contains("\"invalid_grant\""))
                    return false; // fatal — refresh token revoked
                throw new FatalRefreshException("Token endpoint returned HTTP 400", false);
            }
            if (status == 401 || status == 403) {
                // The client credentials are rejected (RFC 6749 5.2, invalid_client); retrying
                // cannot succeed.
                throw new FatalRefreshException("Token endpoint returned HTTP " + status, false);
            }
            if (status != 200) {
                throw new Exception("Token endpoint returned HTTP " + status);
            }

            // Parse response JSON for access_token, refresh_token, expires_in
            Map<String, String> tokenResponse;
            try {
                tokenResponse = JwtValidator.parseTokenResponse(responseBody);
            } catch (IllegalArgumentException e) {
                // The body holds the new tokens; say nothing about its content.  A 200 that is
                // not a token response is an identity-provider malfunction, not a passing outage.
                throw new FatalRefreshException("Token endpoint response is not a valid JSON object", false);
            }
            String newAccessToken = tokenResponse.get("access_token");
            if (newAccessToken == null || newAccessToken.isBlank())
                throw new FatalRefreshException("Token endpoint response missing 'access_token'", false);

            // Update refresh_token if a new one was provided (token rotation)
            String newRefreshToken = tokenResponse.get("refresh_token");
            if (newRefreshToken != null && !newRefreshToken.isBlank())
                this.refreshToken = newRefreshToken;

            // Validate and install the new token
            // The same rules as login, additionalClaims included: a refreshed token is held to
            // what the first token was.
            JwksKeyCache cache = new JwksKeyCache(URI.create(jwksUri), bodyDeadlineMs);
            JwtValidator validator = new JwtValidator(cache, issuer, audience, additionalClaims);
            ParsedJwt newParsed;
            try {
                newParsed = validator.validate(newAccessToken);
            } catch (JwksUnavailableException e) {
                // Transient: the keys could not be read, which says nothing about the token.  The
                // old token is still valid until it expires; keep it and let the loop back off.
                // The new refresh_token (rotation) is already held, so the retry uses it.
                throw e;
            } catch (JwtValidationException e) {
                // A new token that fails validation is permanent: keeping the old one would serve
                // an identity the provider no longer vouches for.  The message names a claim, never a value.
                throw new FatalRefreshException("refreshed token failed validation: " + e.getMessage(), false);
            } finally {
                cache.close();
            }

            // Compute new expiry
            String expStr = newParsed.allClaims().get("exp");
            Instant newExpiry = expStr != null
                    ? Instant.ofEpochSecond(Long.parseLong(expStr))
                    : Instant.now().plusSeconds(3600);

            // Replace principals, token and expiry together; refuses another identity.
            owner.installRefreshed(this, newParsed, newAccessToken, newExpiry);
            owner.lastRefreshed = newParsed;
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
        /** True when logout or an undo stopped the thread first: not worth a log line. */
        final boolean loggedOut;
        FatalRefreshException(String message, boolean loggedOut) {
            super(message);
            this.loggedOut = loggedOut;
        }
    }
}
