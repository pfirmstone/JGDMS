# AI Agent Context: SPIFFE + JWT HTTPS Integration Test Infrastructure

**Branch:** `copilot/setup-spiffe-jwt-servers`  
**PR:** [#256](https://github.com/pfirmstone/JGDMS/pull/256)  
**Date:** 2026-05-11  
**Status:** Updated with high-priority SPIFFE JERI integration coverage and QA end-to-end SPIFFE category work

---

## 1. What Was Done in This Session

This session implemented the SPIFFE and JWT HTTPS integration test infrastructure described in the investigation document. All changes are on branch `copilot/setup-spiffe-jwt-servers`.

### 1.1 SPIFFE — client SVID + SpiffeEndpointTest

| File | Change |
|---|---|
| `qa/harness/trust/gen-spiffe-svids.sh` | Added `client` role: emits `spiffe://test.jgdms.local/client/test` SVID using the `/client/` path (not `/svc/`). All ten roles now generated. Summary output lists service and client roles separately. |
| `qa/harness/trust/spiffe/client/` | New directory: `svid.pem` + `svid_key.pem` (EC P-256, signed by test CA). |
| `qa/harness/trust/spiffe/*/` | All nine service-role SVIDs regenerated with a fresh CA (new serial). |
| `qa/harness/trust/spiffetrust.jks` | Regenerated to match new CA. |
| `qa/jtreg/net/jini/jeri/ssl/SpiffeLoginModule/reggie/` | Fresh `svid.pem` + `svid_key.pem` (copied from new harness output). |
| `JGDMS/jgdms-jeri/src/test/resources/spiffe/` | Fresh `reggie/svid.pem`, `reggie/svid_key.pem`, `ca/ca.pem` (for Maven Surefire tests). |
| `qa/jtreg/net/jini/jeri/ssl/SpiffeEndpointTest/SpiffeEndpointTest.java` | **New jtreg test** (see §2.1). |
| `.github/workflows/spiffe-unit-tests.yml` | Updated trigger paths to include the new jtreg directories. Updated header comment. |

### 1.2 JWT — embedded HTTPS JWKS server + integration tests

| File | Change |
|---|---|
| `JGDMS/jgdms-security-jwt/src/test/java/net/jini/security/jwt/JwtLoginModuleTest.java` | **New Maven Surefire test** (see §2.2). |
| `JGDMS/jgdms-security-jwt/pom.xml` | Added `maven-surefire-plugin` with `<argLine>--add-modules jdk.httpserver</argLine>` so `com.sun.net.httpserver.HttpsServer` is available to Surefire's unnamed-module JVM. |
| `.github/workflows/jwt-integration-tests.yml` | **New CI workflow** (see §2.3). |
| `qa/jtreg/net/jini/security/jwt/JwtLoginModuleTest/JwtLoginModuleTest.java` | **New jtreg variant** (see §2.4). |

### 1.3 High-priority follow-up — SPIFFE JERI end-to-end + JWT user dispatch

| File | Change |
|---|---|
| `qa/src/org/apache/river/test/impl/end2end/e2etest/SpiffeSubjectProvider.java` | **New QA subject provider** that logs in `tester` + `reggie` via `SpiffeLoginModule` and supplies `SpiffePrincipal`-based client/server constraints to the end-to-end harness. |
| `qa/src/org/apache/river/test/impl/end2end/e2etest/ProviderManager.java` | Added `end2end.spiffe` mode selection and provider initialization. |
| `qa/src/org/apache/river/test/impl/end2end/e2etest/Driver.java` | Added support for propagating `-Dend2end.spiffe=true` into the standalone end-to-end JVM. |
| `qa/src/org/apache/river/test/impl/end2end/End2EndTestSpiffe.td` | **New QA test descriptor** creating a SPIFFE-specific secure end-to-end category. |
| `JGDMS/jgdms-jeri/src/test/java/net/jini/jeri/ssl/SpiffeTestSvidFactory.java` | Extended with a shared tester+reggie fixture set and generated truststore support for two-party SPIFFE TLS integration tests. |
| `JGDMS/jgdms-jeri/src/test/java/net/jini/jeri/ssl/SpiffeJwtDispatchIntegrationTest.java` | **New Maven transport-level integration test** covering TLS-capable SPIFFE worker Subject selection and JWT user Subject wire propagation. |
| `JGDMS/jgdms-jeri/pom.xml` | Added `jgdms-security-jwt` as a test-scoped dependency so the JERI integration test can assert `JwtPrincipal` dispatch. |

---

## 2. New Files — What Each Does

### 2.1 `SpiffeEndpointTest.java` (jtreg)

**Location:** `qa/jtreg/net/jini/jeri/ssl/SpiffeEndpointTest/SpiffeEndpointTest.java`

**Tests:**
1. `testReggieServiceSvid` — `SpiffeCredentialManager` for `reggie` role; verifies `X500Principal`, `SpiffePrincipal` with `/svc/reggie` URI, `CertPath` public credential, `X500PrivateCredential` private credential, and that Subject is cleared after `close()`.
2. `testClientSvid` — **primary regression check for the new `/client/` path**; verifies `SpiffePrincipal` URI contains `/client/test` and CN = `Test Client`.
3. `testTesterSpiffeLoginModule` — exercises the JAAS `SpiffeLoginModule` path (used by QA harness); verifies `/svc/tester` URI and that `logout()` cleans the Subject.
4. `testOnlyOneManagerPerJvm` — verifies `SpiffeSubjectHolder` enforcement: a second `SpiffeCredentialManager.start()` throws `IllegalStateException` while the first is active; after `close()` a new manager can start.

**SPIFFE_DIR resolution (three-step fallback):**
1. System property `net.jini.jeri.ssl.spiffe.dir` (set by QA harness).
2. `test.src`-relative path: `<test.src>/../../../../../../../harness/trust/spiffe`.
3. Current working directory (last resort, prints a WARNING).

**jtreg annotations:** `@build SpiffeEndpointTest` / `@run main/othervm SpiffeEndpointTest`

### 2.2 `JwtLoginModuleTest.java` (Maven Surefire)

**Location:** `JGDMS/jgdms-security-jwt/src/test/java/net/jini/security/jwt/JwtLoginModuleTest.java`

**Infrastructure (@BeforeClass):**
- Generates two ephemeral RSA-2048 key pairs: `jwtSigningKeyPair` (kid = `test-key-1`) and `jwtSigningKeyPair2` (kid = `test-key-2`). **Never committed.**
- Runs `keytool -genkeypair` to produce a self-signed PKCS12 keystore for the HTTPS server TLS cert. Temp file deleted immediately after loading.
- Builds server-side and client-side `SSLContext` from the same keystore (the self-signed cert acts as its own CA anchor).
- Calls `SSLContext.setDefault(clientSslCtx)` so `JwksKeyCache`'s `HttpClient` trusts the test cert. Original default is restored in `@AfterClass`.
- Starts `com.sun.net.httpserver.HttpsServer` on a random port (0 → OS assigns).
  - `GET /jwks.json` → returns `currentJwksJson.get()` (an `AtomicReference<String>`, mutated by rotation tests).
  - `POST /token` → returns an OAuth2 response containing `tokenEndpointResponse.get()` (for refresh-token tests).

**Test cases:**
| Test | Expected outcome |
|---|---|
| `testValidJwtPopulatesSubjectPrincipal` | `sub:alice@example.org` JwtPrincipal present |
| `testValidJwtPopulatesEmailPrincipal` | `email:alice@example.org` JwtPrincipal present |
| `testValidJwtAddsExpiryClaim` | `JwtExpiryClaim.getExpiry()` is in the future |
| `testCommitIdempotentForSameToken` | Second login with same claims does not grow principal count |
| `testExpiredJwtThrowsLoginException` | `exp = now − 60s` → `LoginException`, Subject stays clean |
| `testWrongIssuerThrowsLoginException` | Wrong `iss` claim → `LoginException` |
| `testHs256AlgorithmThrowsLoginException` | `alg=HS256` in header → `LoginException` (HS256 is in rejected-algs set) |
| `testMissingJwksUriThrowsLoginException` | No `jwksUri` option → `LoginException` |
| `testMissingIssuerThrowsLoginException` | No `issuer` option → `LoginException` |
| `testAbortAfterLoginDoesNotPopulateSubject` | `login()` succeeds, `abort()` → Subject clean |
| `testLogoutClearsPrincipalsAndCredentials` | `logout()` removes all `JwtPrincipal` and `JwtExpiryClaim` |
| `testKeyRotationSecondLoginUsesNewKey` | After JWKS swap, old kid rejected; new kid accepted |
| `testRefreshTokenFlowUpdatesSubject` | `refreshTokenUri` + `refreshToken` options → commit succeeds; RefreshThread starts |

**JWT signing:** JDK `Signature.SHA256withRSA`, compact serialisation hand-rolled (no third-party library).  
**JWKS JSON:** hand-rolled using `RSAPublicKey.getModulus()` / `getPublicExponent()` + Base64url encoding.

### 2.3 `.github/workflows/jwt-integration-tests.yml`

Mirrors `spiffe-unit-tests.yml` structure:
- **Triggers:** pushes/PRs touching `JGDMS/jgdms-security-jwt/**`, `JGDMS/jgdms-platform/**`, or the workflow file itself.
- **Matrix:** Temurin 21 (always) + DirtyChai JDK (when `dirty-chai-latest` release is available at `pfirmstone/DirtyChai`; degrades gracefully if absent).
- **Permissions:** `contents: read` (least-privilege; required by CodeQL).
- **Stages:**
  1. `JwksJsonParserTest` — JSON parsing, no HTTP.
  2. `JwtPrincipalTest` — principal equality/serialisation.
  3. `JwtLoginModuleTest` — full embedded HTTPS JWKS server integration.
- **Artifact upload:** Surefire reports on every run.

### 2.4 `JwtLoginModuleTest.java` (jtreg variant)

**Location:** `qa/jtreg/net/jini/security/jwt/JwtLoginModuleTest/JwtLoginModuleTest.java`

Mirrors the Maven Surefire test but uses a plain `main()` runner (no JUnit). Test outcomes are reported by throwing `RuntimeException`.

**jtreg annotations:** `@modules jdk.httpserver` / `@run main/othervm --add-modules jdk.httpserver JwtLoginModuleTest`

**Classpath requirement:** needs `jgdms-security-jwt.jar` + `jgdms-platform.jar` on the classpath at jtreg invocation time.

### 2.5 `End2EndTestSpiffe.td` + `SpiffeSubjectProvider.java` (QA harness)

**Location:**  
- `qa/src/org/apache/river/test/impl/end2end/End2EndTestSpiffe.td`  
- `qa/src/org/apache/river/test/impl/end2end/e2etest/SpiffeSubjectProvider.java`

**What it covers:**
1. The existing end-to-end secure transport harness now has a dedicated SPIFFE mode (`-Dend2end.spiffe=true`).
2. Client and server Subjects are loaded from the QA harness SPIFFE JAAS contexts:
   - `org.apache.river.Test`
   - `org.apache.river.Reggie`
3. Principal constraints used by the harness are now expressed with `SpiffePrincipal`, so the secure transport path exercises the `Utilities.getPrincipals()` SPIFFE branch in a real SSL/JERI call path rather than only via unit tests.
4. The new descriptor establishes a reusable QA category for future secure JERI SPIFFE regressions.

### 2.6 `SpiffeJwtDispatchIntegrationTest.java` (Maven Surefire)

**Location:** `JGDMS/jgdms-jeri/src/test/java/net/jini/jeri/ssl/SpiffeJwtDispatchIntegrationTest.java`

**What it covers:**
1. Generates a shared ephemeral SPIFFE CA and both `tester` + `reggie` SVIDs at test time (no committed private keys).
2. Builds a read-only SPIFFE worker Subject for the `tester` role and a separate user Subject containing `JwtPrincipal("sub:alice@example.org")`.
3. Verifies that the SPIFFE worker Subject is TLS-capable (`SslEndpointImpl.hasTlsIdentity(...) == true`) while the JWT-only user Subject is not.
4. Executes the client-side identity nesting used by secure JERI:
   - outer worker Subject via `Subject.doAsPrivileged(...)`
   - inner user Subject via `Subject.callAs(...)`
5. Reflectively exercises `BasicInvocationHandler.getAllUserSubjects()`, `writeUserSubjects(...)`, and `BasicInvocationDispatcher.readUserSubjects(...)` to prove the JWT principal survives the JERI user-Subject wire path without being confused with the TLS worker Subject.

---

## 3. SPIFFE Trust Domain and SVID Naming Scheme

```
TRUST_DOMAIN = test.jgdms.local

Service roles (9):    spiffe://test.jgdms.local/svc/<role>
  reggie fiddler mahalo norm mercury outrigger tester phoenix group

Client role (new, 1): spiffe://test.jgdms.local/client/test
```

Key files under `qa/harness/trust/spiffe/`:
```
ca/
  ca.pem           — test CA certificate (self-signed, EC P-256)
  ca_key.pem       — test CA private key (PKCS#8)
  serial.txt       — serial counter (auto-incremented by openssl)
<role>/
  svid.pem         — leaf SVID + CA cert (PEM chain)
  svid_key.pem     — SVID private key (PKCS#8)
```

JKS truststore: `qa/harness/trust/spiffetrust.jks` (password: `spiffetrustpw`)  
JAAS config: `qa/harness/trust/spiffelogins`

---

## 4. Key Architectural Facts (Verified in This Session)

### Subject lookup priority in `SslEndpointImpl.getCallContext()`
1. `Subject.getSubject(AccessControlContext)` — if it has `X500Principal` or `SpiffePrincipal`.
2. `SpiffeSubjectHolder.get()` — process-wide fallback (set by `SpiffeCredentialManager.start()`).
3. `Subject.current()` — only if it has `X500Principal` or `SpiffePrincipal`.

**Source:** `JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/ssl/SslEndpointImpl.java:278-325`

### `SpiffeSubjectHolder` one-per-JVM invariant
- `SpiffeCredentialManager.start()` calls `SpiffeSubjectHolder.set(subject)`.
- `set()` uses `AtomicReference.compareAndSet(null, subject)`; throws `IllegalStateException` if a *different* Subject is already registered.
- `close()` calls `SpiffeSubjectHolder.clear(owner)` (CAS to null).
- Only one `SpiffeCredentialManager` may be active per JVM.

**Source:** `JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/ssl/SpiffeSubjectHolder.java`

### `JwksKeyCache` HTTPS requirement
- Constructor validates `jwksUri.getScheme().equalsIgnoreCase("https")` — plain HTTP throws `IllegalArgumentException`.
- Uses JDK `HttpClient.newBuilder().connectTimeout(10s).build()` — **uses the JVM default `SSLContext`**.
- Tests override `SSLContext.setDefault()` in `@BeforeClass` and restore it in `@AfterClass`.

**Source:** `JGDMS/jgdms-security-jwt/src/main/java/net/jini/security/jwt/JwksKeyCache.java:117-133`

### `JwtExpiryClaim` API
- Method is `getExpiry()` (not `expiry()`).
- **Source:** `JGDMS/jgdms-security-jwt/src/main/java/net/jini/security/jwt/JwtExpiryClaim.java:56`

### Build requirements
- All JGDMS modules require Maven compiler `--release 21`.
- The `jdk.httpserver` module (`com.sun.net.httpserver.HttpsServer`) is **not** in `java.se`; Surefire's unnamed-module JVM needs `--add-modules jdk.httpserver` in `argLine`.

---

## 5. What's Not Yet Done

These items were identified in the problem statement but are **not** in PR #256:

| Item | Reason not done |
|---|---|
| **Mock SPIRE Workload API socket (`SpireAgentStub`)** | Still needed for realistic hot-rotation testing without a live SPIRE deployment. |
| **`SpiffeCredentialManagerIntegrationTest`** (hot-rotation via mock SPIRE socket) | Still blocked on `SpireAgentStub`. |
| **QA/CI execution of `End2EndTestSpiffe.td`** | The descriptor exists now, but there is not yet a CI workflow that provisions QA harness SPIFFE fixtures and runs the end-to-end suite automatically. |
| **Policy-layer integration (`SpiffePolicyFile` + `RemotePolicyProvider` + `JwtPrincipal`)** | The transport path is now covered, but the three-layer policy stack still lacks an end-to-end regression that combines SPIFFE workload and JWT user principals. |

---

## 6. How to Run the New Tests Locally

### Maven Surefire — JWT integration test
```bash
cd JGDMS
mvn test -pl jgdms-security-jwt -Dtest=JwtLoginModuleTest --no-transfer-progress
```
Requires JDK 21. The `--add-modules jdk.httpserver` argLine is configured in `jgdms-security-jwt/pom.xml`.

### Maven Surefire — SPIFFE unit tests (existing + unchanged)
```bash
cd JGDMS
mvn test -pl jgdms-jeri -Dtest=SpiffePrincipalTest,FileSvidSourceTest,SpiffeCredentialManagerTest --no-transfer-progress
```

### Maven Surefire — SPIFFE TLS + JWT JERI dispatch integration
```bash
cd JGDMS
mvn test -pl jgdms-jeri -am \
  -Dtest=SpiffeJwtDispatchIntegrationTest \
  --no-transfer-progress
```

### QA harness — SPIFFE secure end-to-end category
```bash
# First generate the QA harness SPIFFE test material:
cd /absolute/path/to/JGDMS/qa/harness/trust
bash gen-spiffe-svids.sh

# Then run the new descriptor under the spiffe config set using the QA harness.
# The exact harness invocation depends on the local QA runner setup.
```

### Regenerate SPIFFE SVIDs (after changing roles or trust domain)
```bash
cd qa/harness/trust
bash gen-spiffe-svids.sh
# Then copy fresh SVIDs:
cp spiffe/reggie/svid.pem ../../../JGDMS/jgdms-jeri/src/test/resources/spiffe/reggie/
cp spiffe/reggie/svid_key.pem ../../../JGDMS/jgdms-jeri/src/test/resources/spiffe/reggie/
cp spiffe/ca/ca.pem ../../../JGDMS/jgdms-jeri/src/test/resources/spiffe/ca/
cp spiffe/reggie/svid.pem ../../jtreg/net/jini/jeri/ssl/SpiffeLoginModule/reggie/
cp spiffe/reggie/svid_key.pem ../../jtreg/net/jini/jeri/ssl/SpiffeLoginModule/reggie/
```

### jtreg tests (when jtreg is available)
```bash
# JWT jtreg test — requires jgdms-security-jwt.jar + jgdms-platform.jar on classpath
jtreg -verbose:all \
  -cp JGDMS/jgdms-security-jwt/target/jgdms-security-jwt-3.1.1-SNAPSHOT.jar:JGDMS/jgdms-platform/target/jgdms-platform-3.1.1-SNAPSHOT.jar \
  qa/jtreg/net/jini/security/jwt/JwtLoginModuleTest/JwtLoginModuleTest.java

# SPIFFE endpoint jtreg test — requires jgdms-jeri.jar + jgdms-platform.jar
jtreg -verbose:all \
  -Dnet.jini.jeri.ssl.spiffe.dir=$(pwd)/qa/harness/trust/spiffe \
  -cp JGDMS/jgdms-jeri/target/jgdms-jeri-3.1.1-SNAPSHOT.jar:JGDMS/jgdms-platform/target/jgdms-platform-3.1.1-SNAPSHOT.jar \
  qa/jtreg/net/jini/jeri/ssl/SpiffeEndpointTest/SpiffeEndpointTest.java
```

---

## 7. CI Workflows

| Workflow | File | Triggers |
|---|---|---|
| SPIFFE Unit Tests | `.github/workflows/spiffe-unit-tests.yml` | `JGDMS/jgdms-jeri/**`, `qa/harness/trust/spiffe/**`, jtreg SPIFFE test dirs |
| JWT Integration Tests (NEW) | `.github/workflows/jwt-integration-tests.yml` | `JGDMS/jgdms-security-jwt/**`, `JGDMS/jgdms-platform/**` |

Both workflows use the same two-leg matrix:
- **Temurin 21** — always runs.
- **DirtyChai JDK** — downloads `dirty-chai-latest` release from `pfirmstone/DirtyChai`; skips gracefully if not available.

Both upload Surefire reports as GitHub Actions artifacts on every run.

---

## 8. PR #256 Review Notes

The parallel validation (Code Review + CodeQL) passed with the following minor items addressed:

- ✅ `permissions: contents: read` added to `jwt-integration-tests.yml`
- ✅ `SpiffeEndpointTest.SPIFFE_DIR` resolution now has three-step fallback with directory existence check
- ✅ Spelling: `minimise` → `minimize` in `JwtLoginModuleTest.java`
- ⚠️ Code review noted `base64UrlEncode(String)` in jtreg variant could call the `byte[]` overload for consistency — low priority, no functional impact

---

## 9. Testing TODO Action List

- [ ] Run `End2EndTestSpiffe.td` locally under the QA harness after generating `qa/harness/trust/spiffe/` with `gen-spiffe-svids.sh`.
- [ ] Add a CI workflow that provisions the QA harness SPIFFE fixtures and executes the SPIFFE end-to-end QA category automatically.
- [ ] Extend the SPIFFE/JWT JERI integration test to cover multiple transmitted user Subjects on DirtyChai (`Subject.currentAll()` / `ClientUserSubject.getUserSubjects()` > 1).
- [ ] Add a negative SPIFFE principal constraint test (wrong `ServerMinPrincipal`) to confirm the call fails for the expected reason.
- [ ] Implement `SpireAgentStub` and a hot-rotation integration test that proves new SVID material is picked up before expiry.
- [ ] Add a policy-stack regression that conditions a server-side permission check on both `SpiffePrincipal` and `JwtPrincipal`.
