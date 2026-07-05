# ADVICE — DirtyChai QUIC-TLS exposure: per-location modification map (for human maintainers)

- **Date:** 2026-07-05
- **Target:** DirtyChai (OpenJDK 27 fork) — the SunJSSE QUIC-TLS engine (JEP 517).
- **Purpose:** A concrete, re-verified **location map** for the human DirtyChai maintainers to
  implement Path A (expose SunJSSE's QUIC-TLS engine for a custom-auth JGDMS QUIC transport).
  This document supersedes the *change-set sketch* in `ADVICE-quic-tls-exposure-2026-06-30.md`
  §2 with (a) **re-verified `file:line` citations against current DirtyChai source**, and (b) the
  **ratified trust-dispatch decision** (Peter, 2026-07-05): the QUIC trust dispatch uses the
  `SSLEngine`-adapter / 3-arg extended path; the raw 2-arg branch is **not** used.
- **Status:** **Analysis and advice only.** Produced under the OpenJDK Interim Policy on
  Generative AI adopted by DirtyChai (`DirtyChai/CLAUDE.md`: *"Analyze and advise only. Humans
  write the fix. Do NOT generate code."*). **No DirtyChai source, JavaDoc, tests, or build files
  were created or modified.** Every item below is a recommendation for a human contributor to
  implement. This document lives on the writable JGDMS side.
- **Ratified board decisions this map assumes (2026-07-05, Peter):** Path A + the re-sequencing;
  **algorithm constraints are RE-IMPOSED in JGDMS**; the QUIC trust dispatch **uses the
  3-arg `SSLEngine`-adapter path (the raw 2-arg branch is NOT used)**; this de-risk is a location
  map (not a code change).
- **Source verification baseline:** DirtyChai HEAD **`a5f70fec85f`** (read-only). Citations
  below were opened and confirmed at the stated lines on this baseline; where the 06-30 ADVICE's
  inherited numbers still hold they are marked **[verified unchanged]**.
- **JGDMS-side baseline:** trunk `c0e20eb30` (this worktree, branch `quic-tls-dirtychai-locations`).

---

## 0. THE CRUX (verify-first) — does the JGDMS 2-arg trust method actually do SPIFFE/X.500? **YES.**

**Verified answer:** the JGDMS `FilterX509TrustManager` 2-arg `checkClientTrusted(chain,authType)`
/ `checkServerTrusted(chain,authType)` method **bodies perform the FULL peer-auth evaluation** —
PKIX chain-path validation + auth-type allow-list + SPIFFE/X.500 principal matching. They are
**NOT** a stub, **NOT** chain-only, and **NOT** an "already-validated" no-op. **There is no latent
peer-auth-bypass in the 2-arg body.**

Evidence — `jgdms-jeri/src/main/java/net/jini/jeri/ssl/FilterX509TrustManager.java`:

- **`:130-147` `checkServerTrusted(X509Certificate[] chain, String authType)`** (client-validates-server)
  and **`:111-128` `checkClientTrusted(...)`** (server-validates-client) each do three real steps:
  1. **`:137` (server) / `:118` (client) — `check(authType)`** → **`:73-82`**: validates the auth key
     type against an allow-list (`DHE_RSA`, `ECDHE_ECDSA`, `ECDSA`, `EdDSA`, …), throwing
     `CertificateException("Unsupported authentication key Type: …")` otherwise. (`"UNKNOWN"`
     normalises to `"RSA"`, `:134-136`/`:115-117`.)
  2. **`:139` (server) / `:120` (client) — `trustManager.check{Server,Client}Trusted(chain, authType)`**:
     delegates to the **standard JSSE `X509TrustManager`** built from the truststore
     (`trustManager()`, `:197-270`) → full **PKIX chain-path validation** against the CA truststore.
  3. **`:140` (server) / `:121` (client) — `check(chain)`** → **`:176-190`**: the **SPIFFE/X.500
     principal evaluation** — matches the leaf cert Subject DN against the permitted `X500Principal`
     set (`:180`) **and** checks `spiffe://` URI-SAN entries via
     `Utilities.permittedViaSpiffeSan(principals, chain[0])` (`:187`); throws
     `CertificateException("Remote principal is not trusted")` (`:188`) if neither matches.

**Consequence for the trust-dispatch decision (§2 below):** because the 2-arg body is a
complete peer-auth, routing a JGDMS custom manager through the QUIC 2-arg path *would have been*
functionally safe for peer trust — but it drops the **connection-tied endpoint-identification and
algorithm-constraint enforcement** that the 3-arg extended path performs (SPIFFE auth is by
identity not hostname, so endpoint-ID is a no-loss for JGDMS; **algorithm constraints are NOT
enforced by the 2-arg body** — see §0.1). That single gap, plus Peter's ratified decision that
JGDMS re-imposes algorithm constraints, is why the **DECISION (2026-07-05, Peter) is the 3-arg
`SSLEngine`-adapter path — the 2-arg branch is NOT used**: the adapter carries algorithm
constraints and endpoint-ID through the standard SPI **and** the 2-arg body's SPIFFE/X.500 check
still runs (the adapter's 3-arg override delegates to the same JGDMS logic). The 2-arg finding is
retained below only to record that it *was* safe (no bypass) — it was sound but not chosen.

### 0.1 What the 2-arg body does NOT do (the gap the 3-arg path closes)

The 2-arg body validates the chain and the principal, but it **does not** apply TLS algorithm
constraints (permitted signature schemes / key sizes / named curves for the cert chain) or
endpoint identification — those are supplied by SunJSSE **from the connection object** in the 3-arg
`X509ExtendedTrustManager` overloads. In DirtyChai the QUIC 3-arg path builds them explicitly:
`X509TrustManagerImpl.checkTrusted(chain, authType, QuicTLSEngineImpl, …)` **`:241-258`** derives
`constraints = SSLAlgorithmConstraints.forQUIC(quicTLSEngine, …)` (`:251-252`) and
`identityAlg = quicTLSEngine.getSSLParameters().getEndpointIdentificationAlgorithm()`
(`:253-254`), then enforces them in `findTrustedCertificate` (`:284-291`). That machinery **needs
the engine object** — which is exactly why a custom manager can't reach it via the public SPI, and
why the adapter (which re-presents the QUIC engine as an `SSLEngine`) is the clean route.

---

## 1. Map summary (one line per location)

| # | File (DirtyChai, `src/java.base/share/classes/…`) | Line(s) | Current state | Recommended change |
|---|---|---|---|---|
| A | `module-info.java` | **197-198** [verified] | `exports jdk.internal.net.quic to java.net.http;` | **Spike:** add JGDMS module to targets. **Production:** do NOT export; add facade (item C). |
| B1 | `sun/security/ssl/SSLContextImpl.java` | **485-486** [verified] | `isUsableWithQuic()` → `trustManager instanceof X509TrustManagerImpl` | Widen to accept any `X509ExtendedTrustManager` (JGDMS's wrapped manager). |
| B2 | `sun/security/ssl/CertificateMessage.java` | **1228-1234** (server-validates-client), **1289-1295** (client-validates-server) [verified] | QUIC branch: SunJSSE-internal 3-arg overload, else `throw CertificateException("QUIC only supports SunJSSE trust managers")` | **DECISION (Peter, 2026-07-05):** implement the **3-arg `SSLEngine`-adapter** branch (wrap the QUIC engine as `SSLEngine`; SPIFFE + algorithm constraints + endpoint-ID all carry). **Do NOT** implement a 2-arg branch. Fence to non-`X509TrustManagerImpl`. |
| B3 | `sun/security/ssl/CertificateMessage.java` | **1216-1241** (server block, no final `else`) | server block silently falls through for an unknown transport → **fail-open: client cert admitted UNVALIDATED** (no `else`, unlike client block `:1296-1298`) | **REQUIRED:** add a fail-secure final `else throw` to the server block, matching the client block. Fail-open peer-cert validation is unacceptable on this untrusted-network mTLS path. |
| C | *(new)* `au/zeus/jdk/net/ssl/…` (java.base) + `module-info.java` exports | new | none | Supported facade: `SSLContext → QUIC engine` factory only; export the package. |
| D | `test/jdk/java/net/httpclient/quic/tls/…` (+ lib `…/test/lib/quic/`) | new test | server-mode driven but **no client-auth test** | Human-written server-mode mTLS handshake test: POSITIVE + NEGATIVE (SPIFFE-SAN reject). |
| E1 | `sun/security/ssl/SSLExtension.java` | **316-318** [verified] | `CH_/EE_/NST_EARLY_DATA` enum entries declared **id+name only** (no producer/consumer) → `early_data` null-wired | Keep null-wired; do not wire producers. If ever wired, add explicit max-early-data=0 / disable. |
| E2 | `sun/security/ssl/QuicTLSEngineImpl.java` | **279** (`keysAvailable(ZERO_RTT)→false`), **483-485** (`consumeHandshakeBytes` throws on `ZERO_RTT`) [verified] | 0-RTT refused by absence | Keep the refusal; make it an explicit invariant (see §6, and the JGDMS-side positive guard). |
| E3 | `sun/security/ssl/QuicKeyManager.java` | imports **61-63** `INITIAL/HANDSHAKE/ONE_RTT` only (no `ZERO_RTT`) [verified] | no ZeroRtt key manager | Keep; adding a ZeroRtt key manager is a security-review trigger. |
| F | `sun/security/ssl/X509TrustManagerImpl.java` | **241-258** (QUIC 3-arg `checkTrusted`), **131-141** (public `SSLEngine` 3-arg) [verified] | algorithm constraints + endpoint-ID enforced on the 3-arg QUIC path via `SSLAlgorithmConstraints.forQUIC` | Reference for §6: the chosen adapter route (B2) reuses the `SSLEngine` 3-arg overload so constraints **carry** at the JSSE layer, defense-in-depth with JGDMS's ratified algorithm-constraint re-imposition. |

---

## 2. Trust dispatch — the security core (items B1/B2/B3)

### 2.1 B1 — widen the fail-fast gate (`SSLContextImpl.java:485-486`)

**Current [verified]:**
```java
public boolean isUsableWithQuic() {
    return trustManager instanceof X509TrustManagerImpl;
}
```
This gates `QuicTLSContext` construction. **Recommend:** widen to accept any
`X509ExtendedTrustManager`. JGDMS's `AuthManager`/`FilterX509TrustManager` implements the 2-arg
`javax.net.ssl.X509TrustManager` (verified: `FilterX509TrustManager.java:50` —
`implements X509TrustManager`, **not** `X509ExtendedTrustManager`), so on `SSLContext.init()` it is
wrapped by `SSLContextImpl.chooseTrustManager` in an `AbstractTrustManagerWrapper` (an
`X509ExtendedTrustManager`). The widened predicate must therefore test the *wrapped* type —
`instanceof X509ExtendedTrustManager` — not the raw JGDMS class. **Necessary but not sufficient**
(the handshake still hard-fails at B2 without B2).

### 2.2 B2 — the QUIC trust-dispatch branch (`CertificateMessage.java:1228-1234` and `:1289-1295`)

**Current [verified]** (server-validates-client, `:1227-1235`; client-validates-server,
`:1289-1295` is the mirror):
```java
} else if (shc.conContext.transport instanceof QuicTLSEngineImpl qtlse) {
    if (tm instanceof X509TrustManagerImpl tmImpl) {
        tmImpl.checkClientTrusted(certs.clone(), authType, qtlse);   // SunJSSE-internal overload
    } else {
        throw new CertificateException("QUIC only supports SunJSSE trust managers");
    }
}
```
The standard branches immediately above (`:1217-1226` server, `:1279-1288` client) call the
**public 3-arg** `X509ExtendedTrustManager.check{Client,Server}Trusted(certs, authType, engine)` for
an `SSLEngine` transport and `(…, socket)` for an `SSLSocket`. The QUIC branch instead calls the
**SunJSSE-internal 3-arg** `X509TrustManagerImpl.check…Trusted(chain, authType, QuicTLSEngineImpl)`
(`X509TrustManagerImpl.java:143-151`), which has **no equivalent on the public
`X509ExtendedTrustManager` SPI** because `QuicTLSEngineImpl` is not a `javax.net.ssl.SSLEngine`
(JEP 517's *"would require adding methods to the provider SPI"* caveat, manifesting inside the cert
path). That is why B1 alone relocates the failure from construction to the handshake.

**DECISION (2026-07-05, Peter — RATIFIED): use the 3-arg `SSLEngine`-adapter path. The raw
2-arg branch is NOT used.** The QUIC trust-dispatch branch routes a custom
`X509ExtendedTrustManager` through the **standard 3-arg `X509ExtendedTrustManager` dispatch** by
wrapping the QUIC engine as an `SSLEngine`, so SPIFFE/X.500 validation **and** algorithm-constraint
enforcement **and** endpoint-identification all run. This is now the design, not a recommendation
among alternatives.

**Concrete human-developer instruction — implement the `SSLEngine` adapter (do NOT implement a
2-arg branch):**
1. Add a **thin `javax.net.ssl.SSLEngine` adapter** over `QuicTLSEngineImpl` that surfaces at least
   `getHandshakeSession()` (→ `qtlse.getHandshakeSession()`, `QuicTLSEngineImpl.java:192`),
   `getSSLParameters()` (→ `qtlse.getSSLParameters()`, `:214`), and
   `getSSLParameters().getAlgorithmConstraints()` / `.getEndpointIdentificationAlgorithm()`
   (`:209`, `:214`) so `SSLAlgorithmConstraints` and the identity check see the intended values.
2. In the QUIC dispatch branch at `CertificateMessage.java:1227-1235` (server-validates-client) and
   `:1289-1295` (client-validates-server), for a `tm` that is an `X509ExtendedTrustManager` but
   **not** an `X509TrustManagerImpl`, call the **public 3-arg** overload
   `((X509ExtendedTrustManager) tm).check{Client,Server}Trusted(certs.clone(), authType, adapter)` —
   i.e. dispatch exactly as the standard `SSLEngine` branch immediately above (`:1217-1226` server,
   `:1279-1288` client) does, passing the adapter as the `SSLEngine`.
3. Keep the existing `X509TrustManagerImpl` branch (the SunJSSE-internal
   `check…Trusted(chain, authType, QuicTLSEngineImpl)` overload) exactly as-is for SunJSSE's own
   managers.
4. **Do NOT** add a `((X509ExtendedTrustManager) tm).check…Trusted(certs, authType)` 2-arg branch.

*Why 3-arg wins (and why 2-arg, though safe, was not chosen):*
- The custom manager's **public 3-arg override** runs, so JGDMS's `AuthManager` (whose 3-arg
  wrapper delegates to its 2-arg logic) still executes the **full SPIFFE/X.500 peer-auth of §0** —
  the same validation the 2-arg branch would have run.
- **Additionally**, the standard 3-arg path derives **algorithm constraints + endpoint-ID from the
  adapter's session/params** (mirroring `X509TrustManagerImpl.checkTrusted(...SSLEngine…)`
  `:131-141` and the QUIC constraint derivation `:241-258`), so algorithm constraints are
  **carried at the JSSE layer**, defense-in-depth with Peter's separate ratified decision that
  **JGDMS re-imposes algorithm constraints** in its own trust evaluation.
- The raw 2-arg branch was **verified peer-auth-safe** (the §0 body validates chain + auth-type +
  SPIFFE/X.500, no bypass) — so it was a *sound* option, **but it drops algorithm-constraint
  enforcement** (endpoint-ID is a no-loss for SPIFFE identity-based auth; algorithm constraints are
  a real loss). That single gap is why the 3-arg adapter is chosen: it keeps the constraints that
  the 2-arg path would have dropped, and it aligns the JSSE layer with the JGDMS re-imposition
  decision rather than relying on JGDMS alone.

**Fence:** gate the new adapter branch to `!(tm instanceof X509TrustManagerImpl)` so SunJSSE's own
managers keep their exact current path untouched (P2: widen narrowly, remove nothing). Fail-secure
is retained: an unrecognised manager (neither `X509TrustManagerImpl` nor a custom
`X509ExtendedTrustManager`) still throws.

### 2.3 B3 — REQUIRED fail-secure fix: server block's missing final `else` (`CertificateMessage.java:1216-1241`)

**MANDATORY human-developer fix — not advisory.** The server-side certificate-validation block
(`CertificateMessage.java:1216-1241`, which validates the **client** certificate) has **NO final
`else`**: if `shc.conContext.transport` is none of `SSLEngine` / `SSLSocket` / `QuicTLSEngineImpl`,
control falls straight through to `setPeerCertificates` (`:1245`) with **no certificate validation
performed at all**. This is a **fail-open gap on peer-cert validation** — an unrecognised or
unexpected transport is admitted *unvalidated*. The **client**-side block does not have this gap: it
ends with `else throw new AssertionError("Unexpected transport type")` (`:1296-1298`).

**The human developer MUST add a fail-secure final `else throw` to the server block**, matching the
client block's pattern, so an unrecognised transport is **rejected** rather than passed unvalidated:

- **Location:** the `if (tm instanceof X509ExtendedTrustManager) { … }` transport dispatch in the
  server `checkClientCerts` path, `CertificateMessage.java:1216-1235` — add a terminating
  `else { throw new CertificateException("Unexpected transport type"); }` (or the client block's
  `AssertionError` form) after the `QuicTLSEngineImpl` branch at `:1235`, before the outer
  `else` that already handles the non-extended-manager case (`:1236-1241`).
- **Rationale (why this is required, not optional):** silently skipping client-certificate
  validation is a **peer-authentication bypass**, and this transport is an **untrusted-network**
  path where mandatory mTLS is the trust gate — a fail-open on peer-cert validation is
  **unacceptable** here (DirtyChai `CLAUDE.md` P2 "never weaken/remove a security validation layer"
  and P6 "fail-secure defaults / deny on error"). The pre-existing SunJSSE shape is a latent gap;
  the new custom-manager adapter branch (B2) touches exactly this block, so the fix lands with the
  same edit and MUST be included.
- **Independent of B2:** even for the SunJSSE-only path this `else` should exist; it is a hardening
  of the validation dispatch, mandatory regardless of which trust managers are in play.

---

## 3. The facade (item C) — `au.zeus.jdk.net.ssl`, java.base-only, split-package-free

**Home:** a **new package `au.zeus.jdk.net.ssl` in `java.base`**, exported unqualified (or qualified
to the JGDMS module) via `module-info.java`. It **must** live in `java.base` — only there can it
reach `jdk.internal.net.quic` and `new QuicTLSContext(...)` — and `au.zeus.jdk.net.*` is DirtyChai's
established convention beside the existing `au.zeus.jdk.net.Uri` (java.base-only ⇒ **no split
package**). **Do NOT** use `org.apache.river.api.security` (split-package landmine — that package
exists in **both** `java.base` and `jgdms-platform`, the `--release 21 → NoSuchMethodError` trap;
plus category mismatch: authorization ≠ TLS plumbing) and **do NOT** squat `javax.net.ssl`.

**Exposes ONLY (facade surface):**
- `boolean isQuicCompatible(SSLContext)` → delegates to `QuicTLSContext.isQuicCompatible(SSLContext)`
  (`QuicTLSContext.java:55`).
- a factory to construct the QUIC context from a JGDMS `SSLContext` → wraps `new
  QuicTLSContext(SSLContext)` (`:97`) and `createEngine()` / `createEngine(peerHost, peerPort)`
  (`:113`, `:129`).
- **JSSE-shaped session accessors** the transport needs to build the authenticated `Subject`:
  `getSession()` / `getHandshakeSession()` (returning `javax.net.ssl.SSLSession`,
  `QuicTLSEngineImpl.java:187`, `:192`) and the handshake-drive surface
  (`consume/getHandshakeBytes`, `setUseClientMode`, `getDelegatedTask`) — exposed via a
  facade-owned interface, **not** by re-exporting the type `jdk.internal.net.quic.QuicTLSEngine`.

**Does NOT expose:** `QuicTLSEngine` as a *named structural type* JGDMS compiles against, nor
`sun.security.ssl.*`, nor the `VarHandle` reach into `SSLContext.contextSpi`
(`QuicTLSContext.java` internal — keep it behind the facade). This lets DirtyChai evolve the engine
impl without breaking JGDMS, and bundles the B1/B2 relaxation (and any future 0-RTT control) behind
one reviewed surface. **Spike:** the raw `module-info` export (item A) is acceptable for a
throwaway de-risk only; the facade is the production boundary.

---

## 4. The #1 gate — server-mode mTLS handshake test (item D, human-written)

**Where:** a new JTReg test in `test/jdk/java/net/httpclient/quic/tls/` (beside the existing
`QuicTLSEngineMissingParametersTest.java` etc.), using the QUIC server test-lib at
`test/jdk/java/net/httpclient/lib/jdk/httpclient/test/lib/quic/` (`QuicServer.java`,
`QuicServerConnection.java`, `QuicStandaloneServer.java`) and the accessor
`…/quic/tls/java.base/sun/security/ssl/QuicTLSEngineImplAccessor.java`.

**Gap [verified]:** the existing QUIC-TLS tests drive server mode (`setUseClientMode(false)`,
e.g. `QuicTLSEngineMissingParametersTest.java:91-92`) and client mode (`:102-103`) but a grep of the
whole `quic/tls/` dir finds **no** `setNeedClientAuth` / `setWantClientAuth` / `CLIENT_AUTH` — so
**server-requested client auth (mTLS) is present-but-unexercised.** Since JGDMS mTLS is mandatory,
this is the single highest-priority pre-Path-A item.

**Assertions the test must make:**
- **POSITIVE:** two `QuicTLSEngineImpl`s, server in server mode with **client auth required**
  (`SSLParameters.setNeedClientAuth(true)` on the server engine), driven through a full mutual
  handshake with a valid client cert; assert the handshake **completes** and **both ends'**
  `getSession().getPeerCertificates()` populate (client cert visible server-side, server cert
  client-side). This exercises the `CertificateRequest` → client `Certificate` →
  server-validates-client path through B2.
- **NEGATIVE (the peer-auth teeth):** repeat with a client cert whose SPIFFE/X.500 identity is
  **wrong or whose `spiffe://` URI-SAN is absent**, against a trust manager that (like JGDMS's)
  restricts permitted principals; assert the QUIC **server handshake is REJECTED**
  (`CertificateException` / fatal `CERTIFICATE_UNKNOWN`), **not** silently accepted. This is what
  proves the B2 branch actually enforces the §0 principal check in QUIC server mode (and, with B3,
  that no transport falls through unvalidated).
- **(SSLEngine-adapter path — the chosen B2 design)** add a case asserting an
  **algorithm-constrained** reject (e.g. a signature scheme excluded by `SSLParameters`), to prove
  algorithm constraints carry through the adapter into the 3-arg dispatch (§6).

---

## 5. 0-RTT fail-closed points (item E) — keep refused; make the refusal an invariant

0-RTT is unimplemented and **refused by absence** today [all verified against current source]:
- `SSLExtension.java:316-318` — `CH_/EE_/NST_EARLY_DATA` are declared **id+name only** (no
  producer/consumer/stringizer; contrast the fully-wired `SUPPORTED_VERSIONS` at `:320+`), so the
  `early_data` extension is **never produced or consumed** → 0-RTT is never negotiated.
- `QuicTLSEngineImpl.java:279` — `keysAvailable(ZERO_RTT)` returns `false`.
- `QuicTLSEngineImpl.java:483-485` — `consumeHandshakeBytes(ZERO_RTT, …)` throws
  `IllegalArgumentException("Crypto in zero-rtt")`.
- `QuicKeyManager.java:61-63` — only `INITIAL`/`HANDSHAKE`/`ONE_RTT` key spaces exist; **no ZeroRtt
  key manager**.

**Recommend (DirtyChai side):** do **not** wire the `early_data` producers/consumers, do **not** add
a ZeroRtt key manager, and if a future upstream rebase does either, treat it as a **mandatory
security-review trigger** — there is no explicit `disableEarlyData()` / `maxEarlyDataSize(0)` switch
on the engine, so the refusal currently rests on the scaffolding above. When an `early_data` control
surface is added, it should default to **max-early-data = 0 (disabled)**.

**Recommend (JGDMS side — the load-bearing guard, per this reviewer's angle-1 ruling):** the JGDMS
QUIC transport MUST add a **positive fail-closed assertion** that the invocation envelope (request
preamble: user `Subject`s + the STD-006 §7.2 reducing-domain ACC block) is **never** placed in any
packet whose `KeySpace` is `ZERO_RTT` — an explicit send-path check, independent of engine support,
so the ACC-block/early-data replay invariant does not rest on DirtyChai happening to throw. *(Note:
the ACC reducing-domain block is STD-006 **§7.2** `AccessControlContextRecord`/`ReducingDomainRecord`;
the QUIC design docs cite it as "§7.3" — §7.3 is `DigestCodeSourceRecord`, the per-domain record
carried inside a `ReducingDomainRecord.digest` arm. Cross-reference to fix in the design docs.)*

---

## 6. Algorithm-constraint re-imposition (item F) — Peter's ratified decision

**Fact [verified]:** the QUIC 3-arg path enforces algorithm constraints and endpoint-ID:
`X509TrustManagerImpl.checkTrusted(chain, authType, QuicTLSEngineImpl, …)`
(`X509TrustManagerImpl.java:241-258`) builds `constraints = SSLAlgorithmConstraints.forQUIC(
quicTLSEngine, …)` (`:251-252`) and `identityAlg = quicTLSEngine.getSSLParameters().
getEndpointIdentificationAlgorithm()` (`:253-254`), applied in `findTrustedCertificate`
(`:269-291` — chain validation with `constraints`, then `checkIdentity`). The public `SSLEngine`
3-arg overloads (`:131-141`) do the same from an `SSLEngine`'s session/params.

- **With the chosen SSLEngine-adapter path (B2 decision):** algorithm constraints **are carried at
  the JSSE layer** — the standard 3-arg dispatch derives them from the adapter's
  `getSSLParameters()` / `getHandshakeSession()`, exactly as for a real `SSLEngine`. **Confirm** the
  adapter faithfully surfaces `getSSLParameters().getAlgorithmConstraints()` and the endpoint-ID
  algorithm from the QUIC engine (`QuicTLSEngineImpl.java:209`, `:214` expose these), so
  `SSLAlgorithmConstraints` sees the intended set.
- **This is defense-in-depth with Peter's separate ratified decision that JGDMS re-imposes
  algorithm constraints.** JGDMS still enforces its own algorithm profile in its trust evaluation —
  i.e. `AuthManager` / `FilterX509TrustManager.check(...)` should reject a cert chain whose
  signature scheme / key size / named curve falls outside JGDMS's permitted set (the
  `AlgorithmConstraints` JGDMS defines for its TLS profile). With the adapter, the JSSE layer and
  the JGDMS layer both enforce constraints (belt-and-braces); the JGDMS re-imposition is **not**
  contingent on the adapter and remains a required JGDMS-side work item regardless.
- **The 2-arg branch is not used** (B2 decision), so the transport does **not** rely on JGDMS being
  the *sole* enforcer of algorithm constraints — the failure mode where the JSSE layer silently
  drops them (which the 2-arg path would have introduced) does not arise.

---

## 7. Verification log (what was opened and confirmed, DirtyChai `a5f70fec85f`)

| Citation | Inherited (06-30) | Verified now | Note |
|---|---|---|---|
| `module-info.java` quic export | :197 | **:197-198** | unchanged |
| `SSLContextImpl.isUsableWithQuic` | :485-486 | **:485-486** | body unchanged (`instanceof X509TrustManagerImpl`) |
| `CertificateMessage` server QUIC branch | ~:1228-1234 | **:1227-1235** (throw at :1232-1233) | unchanged |
| `CertificateMessage` client QUIC branch | ~:1289-1295 | **:1289-1295** (throw at :1293-1294) | unchanged |
| `CertificateMessage` server block final-else | (not cited) | **:1216-1241** — NO final else | new finding (asymmetry vs client :1296-1298) |
| `X509TrustManagerImpl` QUIC 3-arg overload | :143-149 | **:143-151** | present; `checkServerTrusted` is package-private (:148) |
| `X509TrustManagerImpl` QUIC constraints/endpoint-ID | (implied) | **:241-258** (`forQUIC` :251; endpoint-ID :253-254) | confirms §6 |
| `X509TrustManagerImpl` public SSLEngine 3-arg | (implied) | **:131-141** | adapter target |
| `QuicTLSContext` factory surface | :55,97,113,129 | **:55, :97, :113, :129** | unchanged |
| `QuicTLSEngineImpl` session/params | :187-188, get*Params | **:187, :192, :214** | `getSession`/`getHandshakeSession`/`getSSLParameters` |
| 0-RTT: `keysAvailable(ZERO_RTT)` | :279 | **:279** → `false` | unchanged |
| 0-RTT: `consumeHandshakeBytes` throw | :483-485 | **:483-485** | unchanged |
| 0-RTT: `early_data` extension null-wired | :554-565 | **SSLExtension.java:316-318** (id+name only) | numbers differ from 06-30; substance confirmed |
| 0-RTT: no ZeroRtt key manager | QuicKeyManager | **:61-63** import INITIAL/HANDSHAKE/ONE_RTT only | confirmed |
| mTLS server-mode test gap | Q7 | **`quic/tls/` has server-mode but no CLIENT_AUTH** | confirmed |
| **JGDMS crux — 2-arg body does SPIFFE** | (not previously body-verified) | **FilterX509TrustManager.java:111-147, :73-82, :176-190** | **verified: full peer-auth, no bypass** |

---

*Produced as analysis and advice for human implementation, per the OpenJDK Interim Policy on
Generative AI adopted by DirtyChai. No DirtyChai source, tests, JavaDoc, build files, or commit
messages were generated or modified, and no pull request was created. All DirtyChai citations are
read-only against HEAD `a5f70fec85f`.*
