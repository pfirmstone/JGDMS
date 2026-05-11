# JGDMS JWT/OIDC Server — Architecture Specification

**Document ID:** JGDMS-ARCH-004  
**Status:** Draft  
**Date:** 2026-05-09  
**Scope:** Minimal native JWT/OIDC Authorization Server implemented on DirtyChai for JGDMS regression testing and production use

---

## 1. Purpose and Context

JGDMS already has a complete JWT *consumer* stack in `jgdms-security-jwt`:

| Class | Role |
|---|---|
| `JwtPrincipal` | `java.security.Principal` wrapping a `"claim:value"` pair |
| `JwtLoginModule` | JAAS `LoginModule` validating an inbound JWT against a JWKS endpoint |
| `JwtValidator` | Cryptographic signature + claims verification (RS256/ES256/PS256) |
| `JwksKeyCache` | TTL-based HTTPS cache of the JWKS key set |

What is **missing** is the issuing side: a minimal Authorization Server that:

1. Manages one or more EC/RSA signing key pairs with automatic rotation.
2. Issues signed JWTs (`client_credentials` and `authorization_code` grant types).
3. Exposes a JWKS endpoint (`/.well-known/jwks.json`) consumed by `JwksKeyCache`.
4. Exposes an OIDC discovery document (`/.well-known/openid-configuration`).
5. Integrates with the JGDMS `UserSubject` identity model (`JwtPrincipal` → `Subject`).
6. Operates under DirtyChai's `SecurityManager`/`AccessController` regime.
7. Survives realistic DoS attack patterns without service degradation.

This specification defines the design choices, the recommended minimal implementation, and the JGDMS/DirtyChai integration points.

---

## 2. Design Choices, Pros/Cons, and Recommendations

### 2.1 Transport Layer

#### Option A — JDK built-in `HttpsServer` (`com.sun.net.httpserver`)

**Pros:**
- Zero new dependencies; already available on every JDK 11+ build.
- Runs under DirtyChai's `SecurityManager` without additional `AllPermission` grants.
- `HttpsServer` supports TLS configuration via `SSLContext`; can be wired to SPIFFE SVIDs.
- Thread pool is configurable; virtual-thread executor pluggable in JDK 21+.
- Simple integration test surface — start/stop in a single JVM.

**Cons:**
- `com.sun.net.httpserver` is a supported but non-standard API (stable since JDK 6, documented since JDK 18 as `jdk.httpserver` module).
- HTTP/2 is not supported (HTTP/1.1 only).
- No built-in connection-level rate limiting or HTTP/2 GOAWAY framing.
- Graceful shutdown requires manual `HttpServer.stop(delay)` call.

#### Option B — JERI (Jini Extensible Remote Invocation) over SSL

**Pros:**
- Fully authenticated transport with mutual TLS and SPIFFE SVID identity.
- Integrates natively with JGDMS `RemotePolicy` and `ProtectionDomain` grants.
- Subject/principal propagation (SPIFFE + `JwtPrincipal`) works transparently.

**Cons:**
- JERI is not an HTTP transport; clients cannot use standard OIDC/OAuth2 libraries.
- The JWT consumer stack (`JwtLoginModule`) already performs HTTPS to a JWKS endpoint; a JERI endpoint breaks that assumption.
- Unsuitable for interoperability with external services, CI runners, or browser-based flows.

#### Option C — Third-party HTTP server (Jetty, Netty, Undertow)

**Pros:**
- HTTP/2 support; mature connection-throttling plugins; extensive middleware.
- Well-understood DoS mitigations (Jetty `DoSFilter`, Netty `ChannelTrafficShapingHandler`).

**Cons:**
- Adds significant transitive dependency surface — security audit and `LoadClassPermission` grant complexity.
- Against the JGDMS philosophy of zero third-party dependencies in the security layer.
- DirtyChai `SecurityManager` policy grants need to cover every class in the dependency graph, which is impractical for large HTTP frameworks.

#### ✅ Recommendation: Option A

Use `com.sun.net.httpserver.HttpsServer` for all three endpoints. HTTP/1.1 is sufficient for JWKS and token exchange. Rate limiting and DoS controls are implemented at the application layer (see §4). The JERI constraint model is preserved for all other JGDMS communication; the JWT server is the sole HTTPS boundary for human/service authentication tokens.

---

### 2.2 Signing Key Algorithm

#### Option A — RSA (RS256 / PS256)

**Pros:**
- Universally supported by every OIDC client library and JWT library.
- RS256 keys can be generated with standard `KeyPairGenerator` on any JDK.
- `JwksKeyCache` and `JwtValidator` already support RS256/PS256.

**Cons:**
- RSA-2048 key generation is CPU-intensive (~10 ms per key pair); high-frequency rotation causes measurable overhead.
- RSA-2048 signatures are 256 bytes; RSA-4096 are 512 bytes — larger than ECDSA equivalents.
- Private key operations are slower than ECDSA on modern hardware.

#### Option B — ECDSA (ES256 using P-256)

**Pros:**
- ES256 keys are tiny (32-byte private scalar) and fast to generate (< 1 ms).
- ES256 signatures are 64 bytes — about 4× smaller than RS256.
- Equal or better security margin compared to RSA-2048 at 128-bit security level.
- `JwtValidator` already supports ES256/ES384/ES512.
- Key rotation every hour introduces negligible CPU cost.

**Cons:**
- A small number of older OIDC clients may not support ECDSA JWKs (rare in 2026).
- P-256 point validation must be explicit (JDK `ECPublicKeySpec` performs this correctly).

#### Option C — Dual-key (RS256 primary + ES256 secondary)

**Pros:**
- Maximum client compatibility.
- Clients that support ES256 can prefer the smaller token.

**Cons:**
- Doubles key management complexity.
- JWKS endpoint grows; `JwksKeyCache` fetches more material.
- Two key rotation schedules to manage.

#### ✅ Recommendation: Option B (ES256 primary), with RS256 fallback JWKS entry

Issue tokens with ES256 for all internal JGDMS services. Also include an RS256 key in the JWKS set for interoperability with external consumers that do not yet support ECDSA. The `alg` field in the JWT header drives `JwtValidator`'s algorithm selection; both are already supported.

---

### 2.3 Token Statefulness (Revocation)

#### Option A — Stateless JWTs only (no revocation)

**Pros:**
- No server-side state; horizontally scalable without shared storage.
- Validation is pure cryptography + clock check — constant time O(1) per token.
- No database, no cache, no distributed lock.

**Cons:**
- Tokens cannot be revoked before their `exp` claim.
- A compromised token is valid for its full lifetime (typically 1–15 minutes for short-lived access tokens).
- Subject change (role removed) is not reflected until token expires.

#### Option B — Stateful (token store with revocation list)

**Pros:**
- Immediate revocation (`jti` blocklist lookup).
- Can issue longer-lived tokens without increased risk window.

**Cons:**
- Requires a persistent or distributed store (Redis, database, or Jini `JavaSpace`).
- Every verification requires a network lookup or local cache read.
- Jini `JavaSpace` (Outrigger) could serve as the revocation store, but adds availability dependency.
- DoS amplification: flooding with invalid JWTs causes store lookups.

#### Option C — Short-lived access tokens + refresh tokens

**Pros:**
- Access token lifetime (1–15 min) limits the impact of a compromised token without requiring revocation infrastructure.
- Refresh tokens can be revoked server-side.
- This is the OIDC best practice model.

**Cons:**
- Refresh tokens require server-side state for the token-to-principal binding.
- `JwtLoginModule` already supports refresh (`refreshTokenUri`, `refreshToken` options).

#### ✅ Recommendation: Option C for production; Option A for regression testing

**Regression tests:** stateless, 5-minute access tokens. No refresh infrastructure needed.  
**Production:** short-lived access tokens (5 min) + refresh tokens stored in a `ConcurrentHashMap` keyed by `jti`, written to a JGDMS `AtomicSerial`-serialized snapshot file on rotation. This avoids an external database dependency while supporting revocation.

---

### 2.4 Grant Types

#### For regression testing (minimum viable set)

| Grant type | Use case | Include? |
|---|---|---|
| `client_credentials` | Service-to-service; workload identity | **Yes** |
| `authorization_code` + PKCE | Interactive user login | **Yes (minimal redirect)** |
| `refresh_token` | Token renewal without re-login | **Yes** |
| `password` (ROPC) | Direct username/password | **No** — insecure; deprecated in OAuth 2.1 |
| `device_code` | Headless device flows | **No** — out of scope |

---

### 2.5 Key Storage and Rotation

#### Option A — Ephemeral in-memory keys (no persistence)

**Pros:**
- Simplest implementation: `KeyPairGenerator.generateKeyPair()` at server start.
- No key material written to disk — no risk of theft via file system access.
- Under DirtyChai: no `FilePermission` grants needed for key material.
- On server restart, old tokens are invalidated automatically (new keys = old signatures fail).

**Cons:**
- Server restart invalidates all outstanding tokens — clients must re-authenticate.
- Multiple server instances cannot share the same signing key set.

#### Option B — File-backed key store (PKCS#12 or PEM)

**Pros:**
- Keys survive restart.
- Can be pre-provisioned by SPIRE or a key management service.

**Cons:**
- File permission grants in DirtyChai policy; key material at rest is a risk.
- Rotation requires atomic file swap and `FilePermission` for the key directory.

#### Option C — SPIFFE SVID-derived signing key

**Pros:**
- Signing key is cryptographically bound to the workload's SPIFFE identity.
- Automatic rotation by SPIRE; no separate key management.
- No key material on disk beyond what SPIRE already manages.

**Cons:**
- SVID private key was designed for TLS, not JWT signing; reuse is non-standard.
- SVID lifespan (1 hour) is short for a JWT signing key — rapid rotation invalidates tokens frequently.
- Not recommended by SPIFFE specifications.

#### ✅ Recommendation: Option A for regression testing; Option B (PEM rotation) for production

**Regression tests:** ephemeral in-memory key pairs. Server and tests share a JVM or the test harness reads the JWKS endpoint during the test run — no persistence needed.  
**Production:** PKCS#12 file with `AtomicSerial`-safe file replacement on rotation. Rotation every 24 hours; previous key retained in JWKS for `exp`-lifetime of tokens signed by it (typically 1 day overlap window).

---

### 2.6 DirtyChai SecurityManager Integration

The JWT server runs under DirtyChai's `SecurityManager`. All network and crypto operations require explicit policy grants. The recommended minimal grant:

```
grant codeBase "httpmd://repo.example.org/jgdms-jwt-server-3.1.1.jar;SHA-256=<hash>"
      principal SpiffePrincipal "spiffe://<trust-domain>/host/jwt-server" {

    // HTTPS server socket
    permission java.net.SocketPermission "*:443", "listen,accept,resolve";

    // JWKS outbound (self-referential for OIDC discovery validation)
    permission java.net.SocketPermission "localhost:*", "connect,resolve";

    // Key generation
    permission java.security.SecurityPermission "insertProvider";

    // OIDC response writing
    permission java.io.FilePermission "${jwt.server.keystore}", "read,write";

    // Scheduled executor thread creation
    permission java.lang.RuntimePermission "modifyThread";

    // Virtual thread executor (DirtyChai JDK 21+)
    permission java.lang.RuntimePermission "modifyThreadGroup";
};
```

`LoadClassPermission` ensures the server's dependencies cannot be class-loaded from untrusted codebases. The JWT server module must declare a `module-info.java` with minimal `requires` declarations to constrain the class-loading surface.

---

## 3. Minimal Implementation Design

### 3.1 Module Structure

New Maven module: `jgdms-jwt-server`  
Package root: `net.jini.security.jwt.server`

```
jgdms-jwt-server/
  src/main/java/net/jini/security/jwt/server/
    JwtAuthorizationServer.java   — lifecycle: start()/stop()
    JwtIssuer.java                — signs and issues JWTs
    JwksEndpoint.java             — handles GET /.well-known/jwks.json
    OidcDiscoveryEndpoint.java    — handles GET /.well-known/openid-configuration
    TokenEndpoint.java            — handles POST /token
    AuthorizationEndpoint.java    — handles GET/POST /authorize (code flow)
    SigningKeyManager.java        — key pair lifecycle + rotation
    ClientRegistry.java           — in-memory OAuth2 client store
    RateLimiter.java              — token-bucket per remote IP
    RequestSizeGuard.java         — rejects oversized request bodies
  src/main/resources/
    jwt-server-default.policy     — DirtyChai SecurityManager policy
  src/test/java/net/jini/security/jwt/server/
    JwtAuthorizationServerTest.java
    TokenEndpointTest.java
    JwksEndpointTest.java
    RateLimiterTest.java
```

No new external dependencies. Only JDK classes + `jgdms-security-jwt` (for `JwtPrincipal`, `ParsedJwt` claim model) + `jgdms-platform` (for `AtomicSerial`-safe persistence).

### 3.2 Key Lifecycle (`SigningKeyManager`)

```
┌─────────────────────────────────────────┐
│  SigningKeyManager                       │
│                                         │
│  activeKeyPair (ES256)  ← signs new JWTs│
│  previousKeyPair        ← validates old │
│  rsaKeyPair (RS256)     ← interop only  │
│                                         │
│  ScheduledExecutorService               │
│    rotates activeKeyPair every 24 h     │
│    demotes current → previousKeyPair    │
│    generates new activeKeyPair          │
│    notifies JwksEndpoint to rebuild     │
└─────────────────────────────────────────┘
```

- Key IDs (`kid`) are `UUID.randomUUID().toString()` assigned at generation time.
- JWKS endpoint always exposes all three key pairs (active + previous + RSA).
- `JwtValidator` selects key by `kid` header claim → `JwksKeyCache.getKey(kid)`.

### 3.3 Token Endpoint (`/token`)

Accepts `application/x-www-form-urlencoded` POST only. Supported grant types:

**`client_credentials`:**
```
POST /token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=<id>&client_secret=<secret>&scope=<scope>
```

Response:
```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "<scope>"
}
```

**`authorization_code`:**
```
POST /token
grant_type=authorization_code&code=<code>&redirect_uri=<uri>&code_verifier=<pkce>
```

**`refresh_token`:**
```
POST /token
grant_type=refresh_token&refresh_token=<token>&client_id=<id>&client_secret=<secret>
```

### 3.4 JWT Payload Structure

```json
{
  "iss": "https://jwt-server.jgdms.example.org",
  "sub": "alice@example.org",
  "aud": ["jgdms-service"],
  "iat": 1714000000,
  "exp": 1714000300,
  "jti": "a1b2c3d4-...",
  "scope": "jgdms.read jgdms.write",
  "groups": ["admins", "users"],
  "spiffe": "spiffe://example.org/client/alice"
}
```

The `spiffe` claim (optional) allows cross-referencing the user's authenticated SPIFFE identity when the token request was made via a SPIFFE-mTLS client connection. `JwtPrincipal` instances on the consumer side are derived from `sub`, `email`, and each entry in `groups`.

### 3.5 OIDC Discovery Document

`GET /.well-known/openid-configuration` returns:

```json
{
  "issuer": "https://jwt-server.jgdms.example.org",
  "authorization_endpoint": "https://jwt-server.jgdms.example.org/authorize",
  "token_endpoint": "https://jwt-server.jgdms.example.org/token",
  "jwks_uri": "https://jwt-server.jgdms.example.org/.well-known/jwks.json",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code", "client_credentials", "refresh_token"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["ES256", "RS256"],
  "code_challenge_methods_supported": ["S256"]
}
```

### 3.6 Client Registry

For regression testing, clients are registered via a `ClientRegistry` loaded from a JGDMS configuration file at server start. The configuration format follows the JGDMS `net.jini.config.Configuration` API:

```java
// jwt-server.config
import net.jini.security.jwt.server.*;

JwtAuthorizationServer {
    issuer          = "https://localhost:8443";
    port            = 8443;
    sslContextFile  = "/etc/jgdms/jwt-server.p12";
    keyRotationHours = 24;

    clients = new ClientDescriptor[] {
        new ClientDescriptor("reggie-client", "secret-abc", new String[]{"jgdms.lookup"}),
        new ClientDescriptor("bae-client",    "secret-def", new String[]{"jgdms.bae"}),
        new ClientDescriptor("test-client",   "secret-test", new String[]{"jgdms.read", "jgdms.write"}),
    };
}
```

---

## 4. DoS Resilience

DoS attacks are the primary operational risk for a public-facing authorization server. The following controls are layered to address different attack vectors.

### 4.1 Attack Surface Analysis

| Attack vector | Risk without mitigation |
|---|---|
| Token endpoint flood (high-rate POST) | CPU exhaustion from crypto (ECDSA signing) |
| JWKS endpoint flood (high-rate GET) | I/O and serialization overhead |
| Oversized request body | Heap exhaustion |
| Slow-loris (slow partial HTTP requests) | Thread/connection exhaustion |
| Replayed valid tokens | No risk for stateless model; risk for refresh tokens |
| Invalid JWT verification flood | CPU from ECDSA verify operations |
| Credential stuffing (client_secret brute force) | Account compromise |

### 4.2 Control: Token-Bucket Rate Limiting per Source IP

`RateLimiter` maintains a `ConcurrentHashMap<InetAddress, TokenBucket>` with a scheduled sweeper to evict stale entries. Default limits:

| Endpoint | Requests per second | Burst |
|---|---|---|
| `/token` (client_credentials) | 10 | 20 |
| `/token` (authorization_code) | 5 | 10 |
| `/token` (refresh_token) | 20 | 40 |
| `/.well-known/jwks.json` | 60 | 120 |
| `/authorize` | 5 | 10 |

Requests that exceed the bucket return HTTP 429 `Too Many Requests` with `Retry-After` header. No crypto operations are performed for rejected requests.

**DoS concern:** the `ConcurrentHashMap` itself is a DoS vector if attackers rotate source IPs at high volume, causing unbounded map growth. Mitigation: cap the map at 10,000 entries; entries beyond the cap receive an immediate 429 without map insertion. Sweeper runs every 60 seconds.

### 4.3 Control: Request Body Size Limits

`RequestSizeGuard` wraps each `HttpExchange.getRequestBody()` with a `LimitedInputStream` that throws `IOException` after reading more than the configured limit. Defaults:

| Endpoint | Max body size |
|---|---|
| `/token` | 4 096 bytes (4 KiB) |
| `/authorize` | 8 192 bytes (8 KiB) |
| All others (GET) | 0 bytes (body rejected) |

This prevents heap exhaustion from oversized POST bodies before any parsing begins.

### 4.4 Control: Connection Read Timeout

`HttpServer.setExecutor(executor)` is not sufficient to set per-connection timeouts on the JDK built-in server. To enforce read timeouts, the `ServerSocket` is configured with `SO_TIMEOUT` and the `HttpsServer` handler runs in a virtual-thread executor. Requests that do not deliver headers within 10 seconds are dropped at the socket level. This defeats slow-loris attacks.

Implementation note: `com.sun.net.httpserver.HttpServer` does not expose socket-level timeout configuration directly. The workaround is to use `HttpsConfigurator.configure(HttpsParameters)` to set `SSLParameters` with a handshake timeout, and wrap the executor with a timeout-enforcing decorator that cancels the `Future` after 10 seconds.

### 4.5 Control: Constant-Time Client Secret Comparison

`ClientRegistry.authenticate(clientId, clientSecret)` uses `MessageDigest.isEqual(hash1, hash2)` for secret comparison, not `String.equals()`. This prevents timing-oracle attacks that could enumerate valid client IDs.

Client secrets are stored as PBKDF2-SHA256 hashes (100,000 iterations) in the registry configuration file. Clear-text secrets are never stored at rest.

### 4.6 Control: PKCE Enforcement

All `authorization_code` flows require `code_challenge` + `code_verifier` (PKCE, RFC 7636, S256 method). Authorization codes without a PKCE verifier are rejected. This prevents authorization code injection attacks even on confidential clients.

Authorization codes expire after 60 seconds. A `ConcurrentHashMap<String, AuthCode>` with a sweeper handles code storage; codes are removed on first use (one-time use).

### 4.7 Control: DirtyChai `LoadClassPermission` Boundary

By running under DirtyChai, the JWT server gains the `LoadClassPermission` boundary. Any exploit that attempts to load a malicious class into the server JVM (e.g., via a crafted token body or JWKS response) is blocked at the class-loading gate before the class can execute. This is the most powerful DoS-and-escalation prevention available: an attacker cannot exploit the token endpoint to execute arbitrary code in the server process.

### 4.8 Control: Virtual Thread Executor with Bounded Queue

The `HttpsServer` is given a virtual-thread executor:
```java
Executors.newVirtualThreadPerTaskExecutor()
```

Virtual threads are cheap but carriers are not. To prevent carrier-pinning DOS (see DirtyChai context §2):
- All I/O in handlers uses non-blocking or interruptible JDK APIs (no `synchronized` on I/O paths).
- ECDSA signing (`Signature.sign()`) does not pin carriers.
- `RateLimiter` bucket update is `ConcurrentHashMap.compute()` — non-blocking.
- A semaphore (`Semaphore(maxConcurrentCrypto)`) bounds concurrent signing operations to `4 * Runtime.availableProcessors()`. Excess requests wait on the semaphore (virtual thread parks — no carrier pin).

### 4.9 Control: JWKS Response Caching

The JWKS document is pre-serialized to a `byte[]` at server start and on each key rotation. `JwksEndpoint` serves the cached bytes directly with no per-request computation. `Cache-Control: public, max-age=3600` header is set so that well-behaved clients cache the JWKS and reduce JWKS endpoint load organically.

---

## 5. JGDMS Integration Points

### 5.1 Consumer Side (existing — `jgdms-security-jwt`)

`JwtLoginModule` already configures against an OIDC-compliant server:
```
jgdms-jwt {
    net.jini.security.jwt.JwtLoginModule required
        jwksUri="https://jwt-server.jgdms.example.org/.well-known/jwks.json"
        issuer="https://jwt-server.jgdms.example.org"
        audience="jgdms-service"
        refreshTokenUri="https://jwt-server.jgdms.example.org/token"
        clientId="reggie-client"
        clientSecret="secret-abc";
};
```

No changes to the consumer module are needed.

### 5.2 Subject Population on Consumer Side

After a successful `JwtLoginModule.commit()`, the `Subject` contains:
- `JwtPrincipal("sub:alice@example.org")`
- `JwtPrincipal("group:admins")`
- `JwtPrincipal("group:users")`
- `ParsedJwt` public credential (claims map, expiry, scopes)

The `UserSubject` wrapping and `Subject.callAs()` invocation follow the existing JGDMS multi-subject model (JGDMS-STD-003 v3).

### 5.3 Policy Grant Example

```
grant codeBase "httpmd://repo.example.org/order-svc-3.1.1.jar;SHA-256=<hash>"
      principal SpiffePrincipal "spiffe://example.org/host/selinux/order-svc"
      principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org"
      principal net.jini.security.jwt.JwtPrincipal "group:admins" {

    permission net.jini.security.AccessPermission "submitOrder";
};
```

### 5.4 SPIFFE Federation (optional)

When a client JVM already has a SPIFFE SVID (from SPIRE), the JWT server can:
1. Accept the SVID as the TLS client certificate on the `/token` endpoint.
2. Extract the `SpiffePrincipal` from the TLS peer certificate.
3. Issue a JWT whose `spiffe` claim mirrors the authenticated SVID URI.
4. Require no `client_secret` for SPIFFE-authenticated clients (`private_key_jwt` or `tls_client_auth` per RFC 8705).

This makes the JWT server a bridge between the SPIFFE workload identity layer and the user JWT identity layer, enabling zero-secret service-to-service token exchange.

---

## 6. Regression Testing Integration

### 6.1 Test Server Lifecycle

The `JwtAuthorizationServer` is designed to start in-process for unit/integration tests:

```java
JwtAuthorizationServer server = JwtAuthorizationServer.builder()
    .issuer("https://localhost:" + port)
    .port(port)
    .ephemeralKeys()                     // in-memory, no file I/O
    .client("test-client", "secret", "jgdms.read jgdms.write")
    .build();
server.start();

// ... run tests using JwtLoginModule configured against localhost ...

server.stop();
```

### 6.2 CI Workflow Integration

A new workflow `.github/workflows/jwt-server-tests.yml` should follow the pattern of `spiffe-unit-tests.yml`:

- Triggered on changes to `JGDMS/jgdms-jwt-server/**`, `JGDMS/jgdms-security-jwt/**`, `JGDMS/jgdms-platform/**`.
- Two matrix variants: Temurin 21 (system) and DirtyChai (graceful skip if unavailable).
- DirtyChai variant activates the `dirtychai` Maven profile to run with `SecurityManager` enabled.
- Tests cover: token issuance, JWKS fetch, token validation end-to-end, rate limiting, key rotation, refresh token flow.

### 6.3 Test Policy File

`src/test/resources/jwt-server-test.policy` grants the server and test classes the minimal permissions needed:

```
grant codeBase "file:${project.build.outputDirectory}/-" {
    permission java.net.SocketPermission "localhost:*", "listen,accept,connect,resolve";
    permission java.security.SecurityPermission "insertProvider";
    permission java.lang.RuntimePermission "modifyThread";
};
```

---

## 7. Implementation Phases

| Phase | Deliverable | Scope |
|---|---|---|
| 1 | `SigningKeyManager` + `JwksEndpoint` | Key generation, JWKS JSON serialization, HTTPS GET |
| 2 | `TokenEndpoint` — `client_credentials` only | JWT issuance, `RateLimiter`, `RequestSizeGuard` |
| 3 | `OidcDiscoveryEndpoint` | Static JSON document |
| 4 | `TokenEndpoint` — `refresh_token` | Refresh token store, `AtomicSerial` snapshot |
| 5 | `AuthorizationEndpoint` — `authorization_code` + PKCE | Interactive flow, code store |
| 6 | SPIFFE mTLS client authentication on `/token` | `tls_client_auth` per RFC 8705 |
| 7 | Regression test suite + CI workflow | End-to-end JwtLoginModule ↔ server tests |

Phases 1–3 constitute the **minimum viable** implementation for regression testing (`client_credentials` is the only grant type needed by JGDMS services). Phases 4–7 extend to production readiness.

---

## 8. Open Questions

1. **Trust domain for `iss` claim:** Should the issuer URI be configurable at runtime (from a JGDMS `Configuration` object) or compile-time? Recommend runtime to support test vs production environments.

2. **Refresh token persistence format:** `AtomicSerial` snapshot file vs Jini `JavaSpace` (Outrigger). The snapshot file approach avoids a service dependency but is not HA. For HA deployments, Outrigger is the natural choice.

3. **Admin API for client management:** The initial design uses a static registry loaded at startup. A dynamic admin API (add/remove clients, rotate secrets) would require additional endpoints and policy grants. Defer to Phase 8.

4. **Audit logging:** Token issuance events should be logged to JFR (`jdk.jfr.Event`) for observability under DirtyChai, following the JFR telemetry host pattern from the SCAP pipeline. Severity of audit log for failed authentication attempts.

5. **Algorithm agility vs rigidity:** Should the server's signing algorithm be configurable, or should ES256 be hardcoded? Recommend hardcoding ES256 + RS256 (dual) to prevent downgrade attacks via misconfiguration.

---

## 9. Summary of Recommendations

| Decision | Recommendation |
|---|---|
| Transport | JDK `HttpsServer` (zero dependencies, DirtyChai compatible) |
| Primary signing algorithm | ES256 (P-256 ECDSA) |
| Interop signing algorithm | RS256 (included in JWKS, optional for external consumers) |
| Token statefulness | Stateless access tokens (5 min TTL) + stateful refresh tokens |
| Key storage (regression) | Ephemeral in-memory |
| Key storage (production) | PKCS#12 file with atomic rotation |
| Grant types (regression) | `client_credentials` only (MVP) |
| Grant types (production) | `client_credentials` + `authorization_code`/PKCE + `refresh_token` |
| DoS: rate limiting | Token-bucket per source IP, capped at 10,000 entries |
| DoS: body size | 4 KiB hard limit on `/token`; 0 on GET endpoints |
| DoS: connection timeout | 10-second handshake + header delivery timeout |
| DoS: crypto concurrency | Semaphore at `4 × availableProcessors()` |
| DoS: JVM-level | DirtyChai `LoadClassPermission` blocks class-loading exploits |
| DirtyChai integration | `httpmd:` codebase grant + `SpiffePrincipal` principal constraint |
| JWKS caching | Pre-serialized `byte[]`, rebuilt only on key rotation |
| SPIFFE integration (optional) | `tls_client_auth` client authentication for workload-to-server flows |
