# JGDMS — Security Weaknesses & Implementation Plan — AI Agent Context (v40)

**Purpose:** This document captures the security-weakness analysis and phased
implementation plan produced during the Copilot conversation dated 2026-05-12.
It continues from
[context_8 (v33)](AI_Agent_JGDMS-GrantPermission-RoleManagement-context_8.md)
and is the forward-reference added in §19 of that document.

**GitHub repositories:**
- JGDMS: https://github.com/pfirmstone/JGDMS
- DirtyChai: https://github.com/pfirmstone/DirtyChai

---

## v40 Change Summary

**DirtyChai gap analysis corrections + Work Item 58 (DirtyChai CodeSourceKey fix)**

Following a detailed analysis of the DirtyChai source and a clarifying exchange:

### Item 5 — CLOSED (not a gap)

A previous analysis raised a concern that SVID rotation could leave stale SPIFFE
principals baked into cached `ProtectionDomain` objects.  This is **not a gap**:

- The SPIFFE principal names stored in a `ProtectionDomain` (e.g.
  `X500Principal("CN=spiffe://trust.domain/path/...")`) are **stable across SVID
  rotation**.  Only the SVID certificate and private key rotate; the workload's
  identity path — and therefore its `Principal.getName()` — does not change.
- The `WorkerSubject` (which carries the current SVID certificate and key) is a
  different concept.  It is obtained fresh on each use by calling
  `Subject.getWorkerSubject()`.  It is not cached in the `ProtectionDomain` and
  must not be used in `Subject.doAs()` / `Subject.callAs()` calls.
- Consequently no `pdcache` invalidation is needed on SVID rotation, and no
  `@implNote` warning is required in `SpiffeCredentialManager` or
  `SecureClassLoader`.

### Item 6 — OPEN → Work Item 58 (DirtyChai)

`SecureClassLoader.CodeSourceKey` does not include digest fields from
`DigestCodeSource`, causing two correctness problems:

1. **Cache aliasing**: two `DigestCodeSource` values for the same URL + certs but
   different digests hash to the same cache slot.  The second caller receives the
   first caller's `ProtectionDomain`.
2. **Unnecessary re-download**: when the caller already supplies a
   `DigestCodeSource` (e.g. JGDMS JERI reconstructing an ACC from the wire),
   `getProtectionDomain` re-downloads the URL and creates a *new*
   `DigestCodeSource` whose digest may differ from the one in the cache key,
   producing a permanently dead cache entry.

Work Item 58 tracks the fix; see §7 for the complete patch specification.

---

## v39 Change Summary

**Work Item 47 completed — boot-window audit trail hardening**

- Completed Work Item 47 by upgrading the boot-window permissive-policy log in
  `PreferredProxyCodebaseProvider.resolve()` from `Level.FINE` to `Level.WARNING`
  and including SHA-256 hash reporting for non-directory codebase JARs.
- §6 Work Items table updated: Item 47 status changed from `🔲 Not started` to
  `✅ Completed`.
- §5 Phase 1 table updated: Phase 1.1 priority changed from `🔴 Immediate` to
  `✅ Completed`.
- Version header bumped from v38 → v39.

---

## v38 Change Summary

**Checked off completed items — no logic changes**

- §3 Weakness 2 "Mitigated?" updated from `Partially (strict policy required)` to
  `Partially (JwtVerifier SPI opt-in; DefaultJwtVerifier exp/iat/iss/aud; OIDC JWKS
  opt-in)` to reflect Work Item 44 completion.
- §5 Phase 3.1 priority changed from `🟡 Sprint 4` to `✅ Completed` to match the
  ✅ already present on Work Item 44 in §6.
- Version header bumped from v37 → v38.

---

## v37 Change Summary

**§4.4 Weakness 5 — Extended with Option E (event-sourced read replicas) + §4.4.1 deep-dive**

Following the observation that VerdictRegistry already has a complete Jini event
infrastructure (`VerdictEvent`, `VerdictEventLease`, `registerVerdictListener`,
`notifyListeners`), a deep-dive investigation was conducted into whether the
existing event model could be used as a replication channel, significantly reducing
the infrastructure complexity of the original Option C ("HA cluster").

The investigation confirmed that this is viable.  Every published `RegistryVerdict`
is already:

1. **Cryptographically signed** by the primary's private key.
2. **Serialised and delivered** to remote listeners via `VerdictEvent` using standard
   Jini `RemoteEventListener` / `Lease` / `LeaseRenewalManager` infrastructure.
3. **Self-authenticating**: a replica that receives a `VerdictEvent` can verify the
   embedded DER signature against the primary's public key, independently of any
   consensus protocol.

Because write authority is inherently asymmetric (only the primary holds the private
signing key), distributed consensus is not required.  The event stream *is* the
replication channel.  A new Option E has been added to the §4.4 options table and is
described in detail in §4.4.1.  Work Item 57 tracks the implementation.

---

## v36 Change Summary

**§4.2 Weakness 3 — Corrected risk model for INCONCLUSIVE verdict / GrantPermission interaction**

The previous version of §4.2 included a "remaining concern" about `doPrivileged` blocks inside
INCONCLUSIVE proxy code potentially allowing privilege self-amplification. This concern has been
retracted following a detailed analysis of the GrantPermission/DynamicPolicy intersection mechanics.

The corrected model is:

- `DynamicPolicyProvider.grant()` intersects the requested permissions with the **caller's**
  `GrantPermission` ceiling at grant time. The permissions that arrive in the proxy's
  `ProtectionDomain` are therefore already bounded by what the granting caller was permitted to
  delegate.
- A `doPrivileged` block inside the proxy stops the stack-walk at the proxy's `ProtectionDomain`
  boundary, but the proxy's PD **cannot hold `GrantPermission`** — proxies are never granted
  delegation authority. There is therefore nothing the proxy can use to self-amplify further.
- Consequently, the risk for **already-loaded** INCONCLUSIVE proxies is **low**: a new `grant()`
  call cannot push the proxy beyond what the granting caller could legitimately delegate, and
  the proxy cannot leverage `doPrivileged` to circumvent this ceiling.
- The **primary risk** remains fresh loads: a freshly-resolved INCONCLUSIVE proxy after a
  policy change starts life with the current (potentially widened) policy in effect, and the
  cached INCONCLUSIVE ClassLoader would allow it to bypass a re-audit.

This clarification also adjusts the relative priority of the two remediation items: Work Item 51
(`INCONCLUSIVEPermit` — explicit admin opt-in per codebase hash) addresses the structural
fresh-load scenario and therefore carries more long-term weight than Work Item 46 (ClassLoader
eviction on grant), which is still valuable for triggering VerdictRegistry re-checks but
addresses a narrower risk window.

---

## v35 Change Summary

**Work Item 44 — `JwtVerifier` SPI (Option D) — ✅ COMPLETED**

Option D was chosen over A/B/C for the following reasons:

- **Option A** (inline `exp/iat` check in `readUserSubjects` itself) is
  non-extensible: it bakes one validation strategy into the JERI core with no
  way to swap in JWKS signature verification, Kerberos ticket validation, or a
  custom revocation list later.

- **Option B** (static `JwksKeyCache` reference in `BasicInvocationDispatcher`)
  couples the JERI core to JWKS HTTP, adding an availability dependency and a
  DNS-resolution path into every server's hot request path. It also forces all
  deployments to provision an OIDC endpoint even when they use mTLS SPIFFE SVIDs
  as their primary identity.

- **Option C** (wire protocol v0x03 replacing v0x02) would break the unreleased
  but already-deployed wire format. v0x02 was extended in-place (adding
  `jwtCount:u8` + JWT bytes after the principals block per Subject) because the
  format had no prior public release and the extension is backward-compatible
  when `jwtCount = 0`.

- **Option D** (pluggable `JwtVerifier` SPI) is chosen because:
  - Zero network dependency in the default path (no verifier registered = accept
    on SPIFFE SVID trust alone — backward compatible).
  - `DefaultJwtVerifier` in `jgdms-security-jwt` checks `exp`/`iat`/`iss`/`aud`
    locally (Base64url decode + JSON scan, no JWKS call) for negligible cost.
  - Full OIDC JWKS signature verification is opt-in by supplying a custom
    `JwtVerifier` backed by `JwtValidator` + `JwksKeyCache`.
  - Connection-level cache (`ConcurrentHashMap<String, Instant>`, max 1024
    entries) amortises verifier cost over the token's full validity window.
  - `@FunctionalInterface` — a lambda is sufficient for simple deployments.

**Files created / modified:**

| File | Change |
|---|---|
| `jgdms-platform/.../jwt/JwtVerifier.java` | NEW — `@FunctionalInterface` SPI |
| `jgdms-platform/.../jwt/JwtVerificationException.java` | NEW — checked exception |
| `jgdms-platform/.../jwt/JwtRawToken.java` | NEW — public credential carrying raw JWT for wire transport |
| `jgdms-platform/bnd.bnd` | Added `net.jini.security.jwt` to OSGi export list |
| `jgdms-security-jwt/.../DefaultJwtVerifier.java` | NEW — claims-only verifier (exp/iat/iss/aud, no JWKS) |
| `jgdms-security-jwt/.../JwtLoginModule.java` | Stores `JwtRawToken` in public credentials on `commit()` |
| `jgdms-jeri/.../BasicInvocationHandler.java` | Extended `writeUserSubjects()` to write `jwtCount:u8` + JWT bytes; added `writeJwtBytes()` helper |
| `jgdms-jeri/.../BasicInvocationDispatcher.java` | Fixed `PRINCIPAL_CTORS` class names (were `net.jini.security.principal.*`, correct names are `net.jini.jeri.ssl.SpiffePrincipal` and `net.jini.security.jwt.JwtPrincipal`); added `jwtVerifier` volatile field + `setJwtVerifier()`; added `JWT_VERIFICATION_CACHE` + eviction logic; extended `readUserSubjects()` to read JWT block; added `readJwtBytes()`, `verifyJwtWithCache()`, `extractJwtExp()` helpers |
| `jgdms-jeri/src/test/.../MultiSubjectWireProtocolTest.java` | Updated existing tests for new wire format (jwtCount byte); added 5 new JWT wire tests |

**Wire format (v0x02, extended in-place):**
```
subjectCount      : u16
for each Subject:
  principalCount  : u16
  for each principal:
    classNameLength : u16
    classNameBytes  : UTF-8
    nameLength      : u16
    nameBytes       : UTF-8
  jwtCount        : u8    ← NEW (0 = none; preserves backward compat)
  for each JWT:
    jwtLength     : u32-BE ← NEW
    jwtBytes      : UTF-8  ← NEW (raw JWT compact serialization)
```

**PRINCIPAL_CTORS bug fix:** The allowlist previously referenced
`net.jini.security.principal.SpiffePrincipal` and
`net.jini.security.principal.JwtPrincipal` — packages that do not exist.
The correct names are `net.jini.jeri.ssl.SpiffePrincipal` and
`net.jini.security.jwt.JwtPrincipal`. This caused `SpiffeJwtDispatchIntegrationTest`
to fail (JwtPrincipal decoded as RemotePrincipal) — now fixed and passing.

---

## v34 Change Summary

This document is the first version of the security-weakness context.

**Contents:**
- §1 — Comparative architecture positioning (JGDMS vs peers)
- §2 — Wire-protocol & dynamic-code efficiency analysis
- §3 — Security weakness summary table (11 weaknesses)
- §4 — Per-weakness options analysis with pros/cons and recommendations
- §5 — Phased implementation plan (4 phases, dependency graph)
- §6 — Work items 44–54 (derived from the implementation plan)

---

## 1. JGDMS Architecture vs. Other Distributed Systems

### 1.1 Security Model

| System | Identity | Policy | Code Safety |
|---|---|---|---|
| **JGDMS** | SPIFFE/SPIRE SVIDs (workload) + JWT/OIDC (user); dual-Subject model baked into every ProtectionDomain | Three-layer dynamic policy stack; GrantPermission intersection enforcement | Static bytecode analysis (BAE) before any proxy is unmarshalled; RegistryVerdict quorum required |
| gRPC / Kubernetes | mTLS via SPIFFE/SPIRE (workload); OIDC/JWT (user) | RBAC via OPA/Istio; policy is external to the runtime | No equivalent — runtime trusts all deployed images |
| Apache Kafka | mTLS + SASL; single identity per connection | ACL-based; no dynamic grant delegation | No equivalent |
| Akka / Pekko Cluster | TLS + custom serialization filters | Role-based; no fine-grained per-proxy policy | No equivalent |
| OSGi | Code signing only | Static permission grants in bundle manifests | No runtime bytecode analysis pipeline |

JGDMS is significantly more security-layered than peers. The five-host SCAP pipeline
(BAE → VerdictRegistry → client) has no direct equivalent in any mainstream distributed
middleware. The ProtectionDomain-level identity injection (workload baked at class-load,
user via ScopedValue) is unique to the DirtyChai/JGDMS stack.

### 1.2 Service Discovery

| System | Discovery mechanism | Proxy delivery | Trust on discovery |
|---|---|---|---|
| JGDMS / Jini | Multicast + ServiceDiscoveryManager; smart proxies carry behavior | ClassLoader-per-endpoint; full JAR download with SHA-256 HTTPMD verification | BAE audit + VerdictRegistry verdict required before proxy is unmarshalled |
| Kubernetes / DNS-SD | DNS-based; service mesh (Istio/Envoy) sidecars | Client stubs are pre-compiled; no runtime code delivery | Implicit trust within cluster namespace |
| Apache Zookeeper / Consul | Centralized registry; address/port only | Clients hold pre-compiled stubs | No runtime code safety checks |
| OSGi / Eclipse Equinox | Bundle repository; remote bundle deployment | Full JAR delivery with code signing | Signing only; no dynamic behavioral analysis |

JGDMS's smart-proxy model (code + behavior delivered at discovery time) is closer to
OSGi remote bundles or Java RMI than to modern REST/gRPC microservices, but with far
stronger runtime trust verification than either.

### 1.3 Concurrency Model

| System | Thread model | Backpressure |
|---|---|---|
| **JGDMS (v32+)** | Virtual threads for all I/O dispatch (except NIO selector loops); Semaphore caps on event delivery | Semaphore-bounded per-executor; natural yield under load |
| Netty / gRPC-Java | NIO event-loop threads (platform); separate executor pool for handlers | Backpressure via flow control (HTTP/2 window) |
| Project Loom (standard Java) | Virtual threads; no built-in backpressure on executor | Application-level |
| Akka | Actor-per-mailbox; configurable dispatcher | Mailbox bounded queue; supervision for overflow |
| Vert.x | Event-loop + worker threads; explicit executeBlocking() | Circuit-breaker and rate-limiter extensions |

JGDMS's v32 migration to `newVirtualThreadPerTaskExecutor()` + Semaphore caps aligns it
with modern Loom idioms, though the NIO transport layer (Mux, SelectionManager) still
requires platform threads — the same constraint as Netty.

### 1.4 Wire Protocol & Identity Propagation

| System | Identity on wire | Context propagation |
|---|---|---|
| **JGDMS** | Serialized AccessControlContext (HTTPMD domains + DigestCodeSource + anonymous count); user Subject block (multi-Subject, v24); ~619 bytes overhead per call | Full ACC + user Subject propagated transitively through JERI calls; survives doPrivileged via domain enrichment |
| gRPC | Metadata headers (JWT Bearer token, mTLS cert); no serialized permission context | Single-hop; no automatic transitive propagation |
| Java RMI | No identity propagation by default | None |
| Quarkus + Panache | Security context via CDI @RequestScoped; not serialized | Thread-local / CDI scope; not cross-JVM |
| Spring Cloud | SecurityContext in thread-local; manual propagation needed for async | Requires explicit DelegatingSecurityContextExecutor |

JGDMS's transitive ACC propagation — where the caller's full permission ceiling travels
with every RPC hop — is unique among mainstream systems.

---

## 2. Wire-Protocol & Dynamic-Code Efficiency

### 2.1 Per-Call Overhead

**JGDMS JERI header budget (~619 bytes total):**

| Component | Size | Purpose |
|---|---|---|
| Protocol version + framing | ~7 bytes | JERI mux frame header |
| HTTPMD ProtectionDomain records | ~280 bytes typical | ACC caller identity |
| DigestCodeSource domains | ~80 bytes typical | SHA-256 code hash domains |
| anonCount field | 4 bytes | Anonymous domain ceiling (v23 privilege-escalation fix) |
| ACC subtotal | ~454 bytes | Full serialized AccessControlContext |
| subjectCount:u16 outer frame | 2 bytes | Multi-Subject wire protocol (v24) |
| User-principal block | ~158 bytes | SPIFFE X500Principal + JwtPrincipal per Subject |
| **Total** | **~619 bytes** | Complete security header |

**Comparison to peers:**

| System | Per-call identity overhead | Notes |
|---|---|---|
| JGDMS JERI | ~619 bytes (cold) / ~0 bytes extra (cache hit) | Full ACC + multi-Subject; AccSerialCache eliminates re-serialization at steady state |
| gRPC (HTTP/2 + HPACK) | ~20–80 bytes | HPACK header compression; JWT Bearer token only, single-hop |
| Java RMI | ~0 bytes identity | No identity propagation; serialization overhead comparable |
| Thrift / Avro RPC | ~0–30 bytes | No identity; optional custom header fields |
| REST/JSON (HTTPS) | ~200–500 bytes | TLS overhead + Authorization: Bearer header; no transitive propagation |
| Akka Remoting | ~30–80 bytes | Artery/Aeron framing; no per-call identity |

At steady-state with `AccSerialCache` (v27), the JERI sender cost drops from ~10–40 µs/call
(cache miss, double stack-walk) to ~10 ns/call (cache hit — one volatile read + pointer
compare). For 1,000 calls/s this is only ~10 µs/s total sender overhead, comparable to
gRPC's HPACK encoding cost.

### 2.2 Dynamic Code — Proxy JAR Delivery

Pack200 compression gives 40–60% reduction on `-dl` proxy JARs (e.g., 500 KB → 200–300 KB).
This is significantly better than generic gzip because it exploits the structure of Java
bytecode (constant pool reordering, shared string encoding, etc.).

**Download trust pipeline cost (one-time per codebase):**

| Step | Overhead |
|---|---|
| SHA-256 JAR hash | ~1–5 ms for a 300 KB JAR (hardware-accelerated) |
| VerdictRegistry RPC | ~1–3 ms (cached after first hit per codebase) |
| Pack200 decompression | ~10–50 ms first time |
| ClassLoader creation | ~0.1 ms |

Once the `PreferredClassLoader` is cached (keyed by `(InvocationHandler, codebase[], parent)`),
subsequent uses skip all of the above.

**Comparison — dynamic code delivery:**

| System | Code delivery | Runtime trust verification | Caching |
|---|---|---|---|
| JGDMS | Pack200-compressed JARs via httpmd: URL | SHA-256 + quorum VerdictRegistry verdict | ClassLoader cache; GC-scoped |
| OSGi Remote Services | Bundle JARs via OBR | Code signing only | Bundle cache |
| Java RMI | Codebase URL (plain HTTP); deprecated | None | Per-URLClassLoader |
| gRPC | Pre-compiled stubs | N/A | N/A |
| Kubernetes / Docker | Container image layers | Image signing (Cosign/Notary); no bytecode analysis | Layer cache |

---

## 3. Security Weakness Summary

*Weakness 1 (DirtyChai dependency) is a design constraint, not a fixable bug.*

| # | Weakness | Severity | Mitigated? |
|---|---|---|---|
| 1 | Full security requires DirtyChai (non-standard JDK) | 🔴 Critical | **By design — no fix** |
| 2 | Wire-asserted user principals are unverified | 🔴 Critical | Partially (JwtVerifier SPI opt-in; DefaultJwtVerifier exp/iat/iss/aud; OIDC JWKS opt-in) |
| 3 | INCONCLUSIVE verdict allows loading; no re-audit on permission change | 🟠 High | No |
| 4 | VerdictRegistry boot permissive window | 🟠 High | Acknowledged; no fix |
| 5 | VerdictRegistry outage blocks all new proxy loads | 🟠 High | No (fail-secure, but availability impact) |
| 6 | SPIRE single point of failure / SVID expiry gap | 🟠 High | Partially (backoff, but no stale-SVID fallback) |
| 7 | Executor tasks silently lose user identity | 🟠 High | Partially (documented pattern; not enforced) |
| 8 | Policy cannot deny, only relax | 🟡 Medium | No — Java platform limitation |
| 9 | doPrivileged migration incomplete in legacy services | 🟡 Medium | Partially (§11 audit ongoing) |
| 10 | CombinerSecurityManager recursion depth ceiling | 🟡 Medium | No (fixed at 7) |
| 11 | DiscoveryCredentialProvider unimplemented | 🟡 Medium | No |
| 12 | Pack200 full-JAR heap materialization | 🟡 Low | Partially (64 MB cap) |

---

## 4. Per-Weakness Options Analysis

### 4.1 Weakness 2 — Wire-Asserted User Principals Not Independently Verified

**Current state:** `BasicInvocationDispatcher.readUserSubjects()` reconstructs
`JwtPrincipal`, `KerberosPrincipal` etc. from the wire via the `PRINCIPAL_CTORS`
allow-list. The server accepts these principals solely on the vouching authority of the
presenting SPIFFE SVID — the raw JWT token is not re-verified against the OIDC issuer.
A compromised service with a valid SVID can assert any user identity.

**Source file:** `JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/BasicInvocationDispatcher.java`

| Option | Pros | Cons |
|---|---|---|
| **A** — Server-side JWT re-verification on every call | True end-to-end cryptographic verification; detects replayed tokens immediately | Requires wire protocol change; ~5–50 ms JWKS lookup on critical path; adds JWKS availability dependency |
| **B** — Connection-level JWT verification with cached result | Cryptographic verification without per-call cost; consistent with SPIFFE SVID trust model | JWT rotation on long-lived connections undetected; wire protocol change still needed |
| **C** — SVID-scoped trust assertion (policy-based, no protocol change) | Zero wire/runtime overhead; already partially supported via GrantPermission intersection | Trust delegated — compromised workload with broad SVID can impersonate any user; requires strict policy discipline |
| **D** — Pluggable `JwtVerifier` SPI with connection-level caching | Opt-in; backward compatible; pluggable (OIDC JWKS, Kerberos, custom); `exp/iat/iss/aud` free even without JWKS | New wire protocol version (0x03) needed; JWKS cache adds operational complexity |

**Recommendation:** Option D. Minimum viable implementation checks `exp/iat/iss/aud` claims
locally without JWKS (free); full OIDC JWKS verification is opt-in. See Work Item 44.

---

### 4.2 Weakness 3 — INCONCLUSIVE Verdict Allows Loading; No Re-Audit on Permission Change

**Current state:** `checkVerdictForJar()` in `PreferredProxyCodebaseProvider` explicitly
allows `INCONCLUSIVE` through with a `WARNING` log. Once loaded, the `ClassLoader` is
cached. If a permission is later granted that makes the guarded code path reachable
(e.g., `createVirtualThread`), no re-audit occurs.

**GrantPermission / `doPrivileged` interaction (corrected — v36):**

Proxy permissions are granted dynamically. The three-layer stack for a proxy's
`ProtectionDomain` is:

```
Effective permissions = (StaticPolicy ∪ DynamicGrants) ∩ GrantPermission ceiling
```

where the `GrantPermission` ceiling is determined by the **caller's** grants at the moment
`DynamicPolicyProvider.grant()` is called, not by the proxy's own PD. This has two
important consequences:

1. **Already-loaded INCONCLUSIVE proxies — low marginal risk.** A new `grant()` can only
   widen a proxy's dynamic grants up to the granting caller's `GrantPermission` ceiling.
   The proxy's PD itself can never hold `GrantPermission`, so a `doPrivileged` block inside
   the proxy code cannot self-amplify further: the stack-walk stops at the proxy's PD, but
   there is no `GrantPermission` there to delegate from. The proxy is bounded by what it
   was granted, full stop.

2. **Fresh proxy loads after a policy change — medium risk (primary concern).** A freshly-
   resolved INCONCLUSIVE proxy starts life with the current (potentially widened) policy.
   If the cached INCONCLUSIVE `ClassLoader` is reused, VerdictRegistry is not re-consulted,
   so the new grants take effect without re-audit.

**Corrected risk table:**

| Scenario | Actual risk | Primary guard |
|---|---|---|
| Already-loaded INCONCLUSIVE proxy; new `grant()` issued | **Low** — new grant bounded by granting caller's `GrantPermission` ceiling; proxy cannot self-amplify via `doPrivileged` | `GrantPermission` intersection enforced at grant time |
| Fresh proxy load after a `grant()` | **Medium** — new PD starts with current policy; re-audit bypassed if ClassLoader cached | Work Item 46 eviction + Work Item 51 `INCONCLUSIVEPermit` |
| Boot-window INCONCLUSIVE load | **Medium** — no VerdictRegistry check at all; runs under static floor only | Work Item 47 (log upgrade) + ServiceStarter ordering (Phase 4.2) |

| Option | Pros | Cons |
|---|---|---|
| **A** — Treat INCONCLUSIVE as DANGEROUS (strict mode) | Eliminates risk; one-line change | Could break existing deployments where some JARs legitimately produce INCONCLUSIVE |
| **B** — INCONCLUSIVE loads but ClassLoader evicted when policy changes | Closes the fresh-load re-audit gap; backward compatible | Requires `DynamicPolicyProvider` ↔ `VerdictRegistryHolder` cross-cutting linkage; eviction disconnects live proxies |
| **C** — INCONCLUSIVE loads into permission-restricted sandbox ClassLoader | Closes dangerous path regardless of future grants | Complex; requires CombinerSecurityManager domain-merge interception |
| **D** — INCONCLUSIVE requires explicit administrator opt-in per codebase hash (`INCONCLUSIVEPermit`) | Makes every INCONCLUSIVE load deliberate; audit trail in VerdictRegistry; addresses the fresh-load scenario structurally | New VerdictRegistry API; operational friction for legitimate INCONCLUSIVE JARs |

**Recommendation:** Option B short-term + Option D long-term. Evict INCONCLUSIVE
ClassLoaders on `DynamicPolicyProvider.grant()` (B) to force a VerdictRegistry re-check
on the next fresh proxy resolve. Note that Option B primarily addresses the fresh-load
risk window — the retroactive risk on already-loaded proxies is naturally bounded by the
`GrantPermission` intersection ceiling as described above. Option D (`INCONCLUSIVEPermit`)
carries more long-term structural weight because it addresses the fresh-load scenario
explicitly via an admin opt-in rather than reactively. See Work Items 46, 51.

---

### 4.3 Weakness 4 — VerdictRegistry Boot Permissive Window

**Current state:** In `PreferredProxyCodebaseProvider.resolve()`, when
`VerdictRegistryHolder.get() == null`, the verdict check is skipped with a `Level.FINE`
log. This is the deliberate bootstrap concession.

| Option | Pros | Cons |
|---|---|---|
| **A** — Record boot-window codebases; retroactively verify when registry injected | Closes window retroactively; automatic | Quarantining after service init may cause ClassCastException in live proxies; requires tracking ClassLoaders |
| **B** — Two-phase startup: VerdictRegistry client first via ServiceStarter | Eliminates window architecturally | Registry must be reachable at startup; hard failure if SPIRE/network unavailable |
| **C** — Configurable strict/permissive boot mode | Operators opt into strict boot | Blocking resolve() risks bootstrap circularity (VerdictRegistry proxy needs a ClassLoader) |
| **D** — Upgrade boot-window log to `Level.WARNING`; include codebase hash | Zero code risk; creates audit trail | Doesn't prevent exploitation; relies on operator review |

**Recommendation:** Option D immediately (trivial log-level change) + Option B as the
deployment-level control. Document `ServiceStarter` ordering as the recommended hardening
step. See Work Item 47.

---

### 4.4 Weakness 5 — VerdictRegistry Outage Blocks All New Proxy Loads

**Current state:** `checkVerdictForJar()` throws `IOException` on `RemoteException` from
the registry. Correct fail-secure behaviour, but means no new proxy codebase can be loaded
during a registry outage.

| Option | Pros | Cons |
|---|---|---|
| **A** — In-memory signed-verdict cache with configurable TTL | Outage only affects new codebases never seen before; `RegistryVerdict` already signed (offline integrity check); small, well-scoped change | Lost on JVM restart; stale SAFE verdicts cannot be invalidated during outage |
| **B** — Persistent local verdict cache (disk) | Survives JVM restart; offline operation | Filesystem becomes security-sensitive; async disk I/O needed |
| **C** — VerdictRegistry HA cluster (full distributed consensus) | Eliminates single point of failure entirely including new verdict issuance | Significant infrastructure complexity (Raft/Paxos, ZooKeeper/etcd, or equivalent); all nodes must hold the private signing key; out of JGDMS codebase scope |
| **D** — Grace period: retry with exponential backoff before failing | Handles transient connectivity blips; small code change | Blocks proxy-loading thread during retry window (acceptable with virtual threads) |
| **E** — Event-sourced read replicas: primary publishes `VerdictEvent` stream; replicas verify signature and cache; clients fall back to replicas on primary `RemoteException` | No consensus protocol; private key stays on primary only; self-authenticating verdicts (DER signature already on every `RegistryVerdict`); entirely within JGDMS codebase using existing `VerdictEvent` / `RemoteEventListener` / `LeaseRenewalManager` infrastructure; N-replica scale-out by starting a new JVM; replicas reconnect via burst-delivery on re-registration | Primary remains write SPOF for new verdict issuance (BAE/reporters still target primary exclusively); new `registerGlobalVerdictListener()` API needed (interface extension); brief startup bootstrap window; stale verdicts possible for JARs reclassified during primary outage |

**Recommendation:** Option A (in-memory signed-verdict cache) + Option D (retry backoff) for
near-term availability improvements. Option E (event-sourced read replicas) is the recommended
server-side HA path when infrastructure investment is justified; it eliminates Option C's
consensus-protocol complexity while remaining entirely within the JGDMS codebase. Option B is
the follow-on for hardened persistent caching at the client. See Work Items 45, 48, 57.

---

### 4.4.1 Option E Deep Dive — Event-Sourced Read Replicas

#### Core Insight

The VerdictRegistry event infrastructure already provides the necessary building blocks:

- `VerdictEvent` is an `@AtomicSerial` `RemoteEvent` subclass carrying a complete, signed
  `RegistryVerdict`.
- `VerdictRegistryImpl.notifyListeners()` fans out every newly published verdict to all
  registered `RemoteEventListener` instances.
- `VerdictEventLease` / `LeaseRenewalManager` keep subscriptions alive transparently.
- Every `RegistryVerdict` carries the primary's DER signature, making it self-authenticating
  without any consensus channel.

Because the private signing key lives only on the primary, write authority is
structurally asymmetric: only the primary can produce a valid `RegistryVerdict`.
Replicas verify each received verdict's signature before caching it — a compromised
event delivery path cannot inject a false SAFE verdict.

#### Architecture

```
BAE nodes  ──submitReport/submitVerdict──→  PRIMARY (VerdictRegistryImpl)
Phoenix    ──reportCrash───────────────────→   │  (private key, quorum logic,
Telemetry  ──reportPinning─────────────────→   │   votes, ReliableLog)
                                               │
                           ┌───────────────────┴────────────────────┐
                           │     VerdictEvent stream (Jini events)  │
                           ▼                                        ▼
             Replica 1 (ReadReplicaVerdictRegistry)    Replica 2 (...)
             - verifies DER signature on receipt        - verifies DER signature
             - publishedVerdicts map                    - publishedVerdicts map
             - hashPublishedVerdicts map                - hashPublishedVerdicts map
             - serves getVerdict / getVerdictByHash     - serves get*

Client A ──getVerdictByHash──→ Primary  (normal path)
Client B ──getVerdictByHash──→ Replica 1 (fallback when Primary unreachable)
```

**Write path:** All write methods (`registerAnalysisEngine`, `revokeAnalysisEngine`,
`submitVerdict`, `reportCrash`, `reportPinning`, `submitReport`) go exclusively to the
primary. BAE nodes and reporters are configured with the primary's address.

**Read path:** `getVerdict()` and `getVerdictByHash()` can be served by any node (primary
or any replica), because they are pure read operations against immutable signed objects.

#### Required Code Changes

**1. New `registerGlobalVerdictListener` method on the `VerdictRegistry` interface**

```java
// In VerdictRegistry (Remote interface)
EventRegistration registerGlobalVerdictListener(
    RemoteEventListener listener,
    MarshalledInstance handback,
    long leaseDuration) throws RemoteException;
```

In `VerdictRegistryImpl`, this stores a `ListenerRegistration` with `codebaseKey = null`
(wildcard sentinel). `notifyListeners()` is extended to fan out to wildcard registrations
as well as codebase-specific ones. On registration, all entries in `publishedVerdicts` and
`hashPublishedVerdicts` are delivered immediately as a burst so the replica reaches full
consistency without a separate bulk-fetch RPC call.

This is an additive interface extension that requires updating `VerdictRegistryProxy`,
`ConstrainableVerdictRegistryProxy`, `VerdictRegistryImpl`, and
`ActivatableVerdictRegistryImpl`.

**2. New `ReadReplicaVerdictRegistry` service class**

Fields:
- `publishedVerdicts`: `ConcurrentHashMap<String, RegistryVerdict>` (URL-keyed)
- `hashPublishedVerdicts`: `ConcurrentHashMap<String, RegistryVerdict>` (hash-keyed)
- `primaryRef`: exported `VerdictRegistry` proxy (discovered from registrar)
- `primaryPublicKey`: `PublicKey` used to verify each incoming `RegistryVerdict` signature
- `leaseRenewalManager`: keeps the global subscription lease alive

Startup sequence:
1. Discover the primary `VerdictRegistry` from the Jini lookup service (by service UUID
   attribute or `ServiceType`).
2. Call `primary.registerGlobalVerdictListener(self, null, Lease.ANY)`.
3. Receive the burst of all currently published verdicts as immediate `VerdictEvent`
   deliveries; set `ready = true` after the `EventRegistration` is returned (i.e., after
   the immediate delivery burst has been submitted — callers waiting on `ready` are not
   directed to the replica until then).

On `VerdictEvent` received:
1. Extract the `RegistryVerdict`.
2. Verify the embedded DER signature against `primaryPublicKey`; reject silently on
   failure (prevents injection via a compromised event delivery path).
3. Store by `codebaseKey` in `publishedVerdicts` and by content hash in
   `hashPublishedVerdicts` (the synthetic `urn:sha256:` URN form already embeds the hash).

Write methods: throw `UnsupportedOperationException` with a clear message directing the
caller to the primary. BAE nodes must always target the primary directly.

Read methods (`getVerdict`, `getVerdictByHash`): served lock-free from the local maps.

**3. `VerdictRegistryHolder` fallback list**

Change from a single `volatile VerdictRegistry instance` to an ordered
`volatile VerdictRegistry[] instances`. In `checkVerdictForJar()`, try each proxy in order,
moving to the next on `RemoteException`. A single-element list is backward compatible.

#### Pros in Detail

| Property | Notes |
|---|---|
| No consensus protocol | Write authority is asymmetric by construction (private key on primary only) |
| Private key on one node | Attack surface for the most critical secret is one JVM; replicas hold only public information |
| Self-authenticating replication | Signature verification on every received `VerdictEvent` prevents injection attacks even over a compromised event channel |
| Reuses existing infrastructure | `VerdictEvent`, `VerdictEventLease`, `AbstractLease`, `LeaseRenewalManager` — no new protocol, no external dependencies |
| N-replica scale-out | Starting a new `ReadReplicaVerdictRegistry` JVM and pointing it at the primary is all that is needed; primary requires no reconfiguration |
| Self-healing after disconnect | On re-subscription the burst of all current verdicts restores full consistency; no manual intervention or snapshot transfer |
| Read-path availability survives primary outage | Replicas serve `getVerdict`/`getVerdictByHash` from their local caches indefinitely (no TTL expiry) during a sustained primary outage |
| Complementary to Options A and D | Option D catches transient blips; Option A caches on the client; Option E is the server-side HA layer between them |

#### Cons in Detail

| Property | Notes |
|---|---|
| Write SPOF remains | New codebases cannot receive their first verdict during a primary outage; BAE submissions and crash/pinning reports are lost until the primary recovers |
| Interface extension | `VerdictRegistry` is a `Remote` interface; adding `registerGlobalVerdictListener` requires updating all implementations (proxy, impl, activatable wrapper) and any test mocks |
| Bootstrap window | Between replica startup and receipt of the initial burst, `getVerdict`/`getVerdictByHash` return `null`; a `ready` flag must gate client traffic |
| Stale verdicts during outage | If a JAR is reclassified as DANGEROUS while the primary is down, replicas continue to serve the old SAFE verdict until the primary recovers and emits a new `VerdictEvent`. Each served verdict carries a cryptographic timestamp so clients know how old the classification is. |
| No vote-state replication | Replicas hold only published verdicts, not the underlying vote accumulator. Disaster-recovery (standing up a new primary after permanent primary loss) still requires restoring from the `ReliableLog` snapshot |
| Lease liveness coupling | If `LeaseRenewalManager` fails to renew the subscription lease (replica overload), the primary evicts the subscription silently; a watchdog or periodic subscription heartbeat check is needed |

#### Comparison with Original Option C

| Dimension | Original Option C (HA cluster) | Option E (event-sourced replicas) |
|---|---|---|
| Consensus protocol | Required (Raft/Paxos) | **Not required** |
| Private key distribution | All cluster nodes need the key | **Primary only** — stronger security |
| Write-path HA | All nodes accept writes | Primary only (write SPOF remains) |
| Read-path HA | All nodes | ✅ All nodes |
| Infrastructure dependency | ZooKeeper/etcd or custom Raft | ✅ Existing Jini/JGDMS infrastructure |
| Codebase scope | External infrastructure | ✅ Within JGDMS codebase |
| Stale verdict risk | None | Yes, during primary outage |
| New codebase during outage | Handled (all nodes can issue) | ❌ Blocked (primary needed) |
| Implementation effort | Very high | Moderate (new API method + replica class + holder fallback list) |
| Self-validating replication | No (trust cluster protocol) | ✅ Yes (DER signature on each verdict) |

---

### 4.5 Weakness 6 — SPIRE Single Point of Failure / SVID Expiry Gap

**Current state:** `SpiffeCredentialManager.renewalTask()` retries every fixed 30 seconds
on failure. `scheduleRenewal()` fires `renewalLeadSeconds` (default 300 s = 5 min) before
expiry. If SPIRE is down for more than `renewalLeadSeconds`, the SVID expires before
renewal succeeds.

| Option | Pros | Cons |
|---|---|---|
| **A** — Exponential backoff in `renewalTask()` (bounded, max `renewalLeadSeconds/2`) | Reduces SPIRE load during outage; fast recovery after outage resolves; small code change | Slightly more complex scheduling logic |
| **B** — Increase default `renewalLeadSeconds` to 900 s | Trivial one-line change; immediate benefit | Doesn't help during multi-hour outages; reduces flexibility for short-TTL SVIDs |
| **C** — Credential serialization to secure local file (AES-256-GCM, TPM-derived key) | Handles SPIRE unavailability at startup; extends survival window significantly | Storing private key material on disk requires TPM or equivalent; increases attack surface |
| **D** — `isCredentialValid()` + `secondsUntilExpiry()` health endpoint; emit `Level.WARNING` when below threshold | No code complexity on credential path; enables operational alerting | Doesn't prevent failure; relies on operator monitoring |

**Recommendation:** Option A + Option D. Exponential backoff immediately; health metric
emission for operational visibility. See Work Item 49.

---

### 4.6 Weakness 7 — Executor Tasks Silently Lose User Identity

**Current state:** JGDMS-STD-003 v3 documents the explicit wrapper pattern but it is
unenforced. Service code that submits tasks to a bare executor silently drops the user
`Subject` with no compile-time or runtime warning. `TxnManagerImpl.settleTxns` and
`RegistrarImpl` discovery threads are noted as partially-resolved in §11 of context_8.

| Option | Pros | Cons |
|---|---|---|
| **A** — `SubjectAwareExecutor` wrapper class (`implements ExecutorService`) | Transparent to service code; incorrect usage visible in code review | Service code must still be updated to use the wrapper |
| **B** — `@SubjectPropagationRequired` annotation + SpotBugs plugin | Compile-time detection; zero runtime overhead | Requires custom SpotBugs rule; annotations add boilerplate |
| **C** — Complete §11 migration audit; migrate all remaining `doAs`/`doAsPrivileged` call sites | Full coverage; regression tests per site | High effort; disrupts multiple service modules |
| **D** — `SubjectAwareExecutor` (Option A) + targeted migration of known sites (Option C) | New code gets wrapper; known broken sites fixed | Requires both tracks in parallel |

**Recommendation:** Option D. `SubjectAwareExecutor` for all new code; migrate known
sites (`TxnManagerImpl`, `RegistrarImpl`, `AbstractActivationGroup`) as a targeted PR.
See Work Items 50, 52.

---

### 4.7 Weakness 8 — Policy Cannot Deny, Only Relax

**Current state:** `DynamicPolicyProvider.implies()` has no concept of negative grants.
The three-layer stack can only widen permissions, never narrow them at the dynamic layer.

| Option | Pros | Cons |
|---|---|---|
| **A** — `DenyPermission(Permission p)` wrapper in `DynamicPolicyProvider` | Strong deny semantics; evaluated before any positive grants | New permission type in the grant model; admin education needed |
| **B** — FROZEN grants (admin marks a set of permissions as immutable) | Prevents accidental over-grant; simpler than Option A | Weaker than deny; still can't actively block |
| **C** — Documentation + offline policy analysis tool | Zero code risk | Doesn't address the structural gap |
| **D** — Negative grants set in `DynamicPolicyProvider` with background sweeper (same model as void grants) | Most principled; leverages existing `DynamicPolicyProvider` infrastructure and `PermissionGrant` lifecycle | Requires careful ordering: positive grants evaluated first, negative grants second |

**Recommendation:** Option D. Negative grants in `DynamicPolicyProvider` with the same
concurrency model as positive grants. See Work Item 53.

---

### 4.8 Weakness 9 — doAs/doAsPrivileged Migration Incomplete

**Current state:** The §11 audit table in context_8 documents remaining `doAsPrivileged`
sites. `RegistrarImpl` discovery threads and `AbstractActivationGroup` executor paths are
explicitly flagged as high-priority but not yet migrated.

| Option | Pros | Cons |
|---|---|---|
| **A** — Migrate all remaining §11 sites in a single PR | Completes the audit | High risk — disrupts multiple service modules simultaneously |
| **B** — Add regression test per site, then migrate incrementally | Prevents regression; per-service mergeability | High effort, slower |
| **C** — SpotBugs / javaparser scan for remaining `doAsPrivileged` + migrate per STD-003 matrix | Gives confidence in completeness before migration | Scan may have false positives; requires custom rule |

**Recommendation:** Option C to find all sites, then Option B to migrate incrementally.
Scan gives completeness confidence; incremental migration with per-site tests prevents
regression. See Work Item 52.

---

### 4.9 Weakness 10 — CombinerSecurityManager Recursion Depth Ceiling

**Current state:** The recursion depth guard in `CombinerSecurityManager` is a fixed
constant (approximately 7). A configuration with more than three policy layers can exhaust
the guard.

| Option | Pros | Cons |
|---|---|---|
| **A** — Make depth limit a configurable system property (`jgdms.securityManager.maxRecursionDepth`, default 10) | Operators can tune; no logic change | Admin education needed |
| **B** — Lazy domain resolution (iterator-based, not recursive) | Eliminates per-layer stack-frame consumption | Complex refactoring of `CombinerSecurityManager`; high risk if guard logic is subtly changed |
| **C** — Document and freeze current limit; add `SEVERE` log on startup if config would exceed depth 5 | Zero code risk | Doesn't help operators needing more layers |

**Recommendation:** Option A + Option C. Configurable limit with startup validation warning.
See Work Item 54.

---

### 4.10 Weakness 11 — DiscoveryCredentialProvider Interface Not Implemented

**Current state:** The `DiscoveryCredentialProvider` interface is referenced in design
documents (§12 Work Item 25 of context_8) but was never started.

| Option | Pros | Cons |
|---|---|---|
| **A** — Implement `SpiffeDiscoveryCredentialProvider` backed by `SpiffeSubjectHolder` | Closes the gap; consistent with existing SPIFFE workload identity model | Discovery protocol needs to carry credentials |
| **B** — Define interface + `NoOpDiscoveryCredentialProvider` default | Documents and formalizes the gap; pluggable | Doesn't actually secure discovery |
| **C** — Defer as separate feature request | Keeps scope focused | Discovery remains an unsecured channel |

**Recommendation:** Option A. SPIFFE infrastructure is already in place; wiring
`SpiffeSubjectHolder` into the discovery credential path is a well-bounded change.
See Work Item 55.

---

### 4.11 Weakness 12 — Pack200 Full-JAR Heap Materialization

**Current state:** `HttpmdURLConnection` buffers the entire unpacked JAR before returning
the stream. The `CappedOutputStream(64 MB)` cap prevents unbounded amplification, but
N concurrent first-proxy loads can cause N × 64 MB heap pressure.

| Option | Pros | Cons |
|---|---|---|
| **A** — `Semaphore(maxConcurrentJarLoads)` around JAR download + decompression in `resolve()` (configurable, default 4) | Bounds peak heap to 4 × 64 MB = 256 MB; virtual threads yield naturally on semaphore wait; consistent with event-delivery and policy-service patterns | Adds latency for clients waiting for a JAR slot during burst |
| **B** — Streaming Pack200 decompression | Eliminates peak materialization entirely | Pack200 requires two-pass processing; high implementation risk |
| **C** — Reduce cap from 64 MB to 16 MB | Trivial constant change; most proxies < 10 MB | Breaks legitimately large proxy JARs |

**Recommendation:** Option A. The semaphore approach is consistent with other
bounded-resource patterns in the JGDMS architecture. See Work Item 56.

---

## 5. Phased Implementation Plan

### Phase 1 — Low Risk, High Impact (no API/protocol changes)

| # | Weakness | Action | Files | Priority |
|---|---|---|---|---|
| 1.1 | Boot window log level (W4) | Upgrade `Level.FINE` → `Level.WARNING` in boot-window path; include codebase hash | `PreferredProxyCodebaseProvider.java` | ✅ Completed |
| 1.2 | SVID renewal backoff (W6) | Replace fixed `RETRY_INTERVAL_SECONDS` with exponential backoff (cap at `renewalLeadSeconds/2`, min 30 s) | `SpiffeCredentialManager.java` | 🔴 Immediate |
| 1.3 | SVID health metric (W6) | Add `isCredentialValid()` + `secondsUntilExpiry()` to `SpiffeCredentialManager`; emit `Level.WARNING` when < `renewalLeadSeconds × 2` | `SpiffeCredentialManager.java` | 🔴 Immediate |
| 1.4 | VerdictRegistry retry backoff (W5) | Add 3-attempt exponential backoff (1 s → 2 s → 4 s) before failing in `checkVerdictForJar()` | `PreferredProxyCodebaseProvider.java` | 🔴 Immediate |
| 1.5 | Pack200 semaphore (W12) | Add `Semaphore(4)` (configurable `jgdms.proxy.maxConcurrentJarLoads`) around JAR download + decompression in `resolve()` | `PreferredProxyCodebaseProvider.java` | 🟠 Sprint 1 |
| 1.6 | Recursion depth configurable (W10) | Make `CombinerSecurityManager` depth limit a system property (default 10); add startup `SEVERE` warning | `CombinerSecurityManager.java` | 🟠 Sprint 1 |

### Phase 2 — Medium Effort, Targeted Bug Fixes

| # | Weakness | Action | Files | Priority |
|---|---|---|---|---|
| 2.1 | doAs migration — scan (W9) | Run SpotBugs/javaparser scan for all remaining `doAsPrivileged` in service code; produce migration list | All service modules | 🟠 Sprint 2 |
| 2.2 | doAs migration — RegistrarImpl (W9) | Migrate `RegistrarImpl` discovery/multicast threads per STD-003 decision matrix; add regression tests | `RegistrarImpl.java` | 🟠 Sprint 2 |
| 2.3 | doAs migration — AbstractActivationGroup (W9) | Migrate executor path; add regression tests | `AbstractActivationGroup.java` | 🟠 Sprint 2 |
| 2.4 | `SubjectAwareExecutor` (W7) | Implement `SubjectAwareExecutor implements ExecutorService`; update Javadoc in `AbstractJiniService` to recommend it | New class in `jgdms-platform` | 🟠 Sprint 2 |
| 2.5 | INCONCLUSIVE eviction (W3) | Track `ClassLoader` instances loaded under INCONCLUSIVE verdict; evict on `DynamicPolicyProvider.grant()` | `VerdictRegistryHolder.java`, `DynamicPolicyProvider.java` | 🟡 Sprint 3 |
| 2.6 | In-memory verdict cache (W5) | Add `ConcurrentHashMap<String, RegistryVerdict>` cache in `PreferredProxyCodebaseProvider`; use cached verdict on `RemoteException` if within TTL | `PreferredProxyCodebaseProvider.java` | 🟡 Sprint 3 |

### Phase 3 — Architectural Changes (new API/protocol)

| # | Weakness | Action | Files | Priority |
|---|---|---|---|---|
| 3.1 | `JwtVerifier` SPI (W2) | Define `JwtVerifier` SPI; wire into `BasicInvocationDispatcher`; add connection-level JWT verification cache; add wire protocol version 0x03 for raw JWT transport | `BasicInvocationDispatcher.java`, new `JwtVerifier.java` | ✅ Completed |
| 3.2 | `DiscoveryCredentialProvider` (W11) | Define interface; implement `SpiffeDiscoveryCredentialProvider` backed by `SpiffeSubjectHolder`; integrate into `AbstractLookupDiscovery` | New interface + impl; `AbstractLookupDiscovery.java` | 🟡 Sprint 4 |
| 3.3 | Negative grants (W8) | Add `negativeGrants` set to `DynamicPolicyProvider` with same background sweeper as void grants; update `implies()` | `DynamicPolicyProvider.java` | 🟡 Sprint 5 |
| 3.4 | Persistent verdict cache (W5) | Add disk-based signed `RegistryVerdict` cache to `PreferredProxyCodebaseProvider` | `PreferredProxyCodebaseProvider.java`, new `VerdictCache.java` | 🔵 Sprint 6 |
| 3.5 | `INCONCLUSIVEPermit` (W3) | Add `INCONCLUSIVEPermit` registry entry to `VerdictRegistry` API; require it for INCONCLUSIVE loads in strict mode | `VerdictRegistry.java`, `PreferredProxyCodebaseProvider.java` | 🔵 Sprint 6 |

### Phase 4 — Operational / Deployment

| # | Weakness | Action |
|---|---|---|
| 4.1 | SPIRE HA (W6) | Add SPIRE HA deployment topology to `spiffe-admin-deployment.md` |
| 4.2 | ServiceStarter ordering (W4) | Document recommended startup ordering (VerdictRegistry client first) as the hardened-boot pattern |
| 4.3 | Policy deny documentation (W8) | Document the negative grants feature (Phase 3.3) with worked examples in `security_architecture_feature_table.md` |

### Dependency Graph

```
Phase 1.1 (log)            → standalone
Phase 1.2 + 1.3 (SVID)    → standalone
Phase 1.4 (VR retry)       → standalone
Phase 1.5 (Pack200 sem.)   → standalone
Phase 1.6 (recursion)      → standalone

Phase 2.1 (scan)           → feeds 2.2, 2.3
Phase 2.4 (SubjectAware)   → feeds 2.2, 2.3
Phase 2.5 (INCONCLUSIVE)   → depends on Phase 1 being stable
Phase 2.6 (VR cache)       → standalone after Phase 1.4

Phase 3.1 (JwtVerifier)    → depends on JERI protocol stability
Phase 3.2 (DiscoveryCred)  → depends on SpiffeCredentialManager (Phase 1.2)
Phase 3.3 (neg grants)     → depends on DynamicPolicyProvider stability
Phase 3.4 (persist cache)  → Phase 2.6 must be complete first
Phase 3.5 (INCONCLUSIVE P) → Phase 2.5 must be complete first
```

---

## 6. Work Items 44–56

These extend the work-item table in §12 of
[context_8](AI_Agent_JGDMS-GrantPermission-RoleManagement-context_8.md).

| Item | Description | Phase | Status |
|---|---|---|---|
| **44** | `JwtVerifier` SPI — define interface; wire into `BasicInvocationDispatcher`; connection-level JWT cache; `jwtCount:u8` extension of v0x02 wire format; `DefaultJwtVerifier` (exp/iat/iss/aud, no JWKS); `JwtRawToken` public credential; fixed `PRINCIPAL_CTORS` class names | 3.1 | ✅ Completed |
| **45** | VerdictRegistry retry backoff (exponential, 1 s → 2 s → 4 s, 3 attempts) in `checkVerdictForJar()` | 1.4 | 🔲 Not started |
| **46** | INCONCLUSIVE ClassLoader eviction on `DynamicPolicyProvider.grant()` | 2.5 | 🔲 Not started |
| **47** | Boot-window log upgrade (`Level.FINE` → `Level.WARNING` + SHA-256 hash) | 1.1 | ✅ Completed |
| **48** | In-memory signed-verdict cache (`ConcurrentHashMap<String, RegistryVerdict>`, configurable TTL) | 2.6 | 🔲 Not started |
| **49** | SVID exponential-backoff renewal + `isCredentialValid()` / `secondsUntilExpiry()` health endpoint | 1.2 + 1.3 | 🔲 Not started |
| **50** | `SubjectAwareExecutor implements ExecutorService` — Subject[] capture-and-rebind wrapper | 2.4 | 🔲 Not started |
| **51** | `INCONCLUSIVEPermit` registry entry — require for INCONCLUSIVE loads in strict mode (next major version) | 3.5 | 🔲 Not started |
| **52** | doAs/doAsPrivileged migration: SpotBugs scan + incremental per-site migration (`RegistrarImpl`, `AbstractActivationGroup`) | 2.1–2.3 | 🔲 Not started |
| **53** | Negative grants in `DynamicPolicyProvider` — `negativeGrants` set + background sweeper + `implies()` update | 3.3 | 🔲 Not started |
| **54** | `CombinerSecurityManager` depth limit — configurable system property (default 10) + startup `SEVERE` warning | 1.6 | 🔲 Not started |
| **55** | `DiscoveryCredentialProvider` — interface + `SpiffeDiscoveryCredentialProvider` backed by `SpiffeSubjectHolder` | 3.2 | 🔲 Not started |
| **56** | Pack200 semaphore — `Semaphore(4)` (configurable) around JAR download + decompression in `PreferredProxyCodebaseProvider.resolve()` | 1.5 | 🔲 Not started |

| **57** | Event-sourced VerdictRegistry read replicas — new `VerdictRegistry.registerGlobalVerdictListener()` API (wildcard subscription with immediate burst delivery); `ReadReplicaVerdictRegistry` implementation (DER signature verification on receipt, `publishedVerdicts` + `hashPublishedVerdicts` caches, `ready` flag, `LeaseRenewalManager` subscription); `VerdictRegistryHolder` extended to fallback ordered list; client fallback on `RemoteException` | 3 (new) | 🔲 Not started |
| **58** | DirtyChai `SecureClassLoader.CodeSourceKey` digest fix (two-part) — see §7 | DirtyChai | 🔲 Not started |

---

## 7. Work Item 58 — DirtyChai `CodeSourceKey` Digest Fix

**File:** `src/java.base/share/classes/java/security/SecureClassLoader.java` in DirtyChai

This is a two-part change to `SecureClassLoader`:

### Part A — `CodeSourceKey` must include digest fields

**Why:** `CodeSourceKey` is the key type for `pdcache`.  When the incoming
`CodeSource` is a `DigestCodeSource`, the digest is part of its identity.  Two
`DigestCodeSource` values at the same URL with the same certificates but different
digests represent different code versions and must map to different cache slots.

**What to change:** add `digestAlgorithm` (String) and `digest` (byte[]) fields to
`CodeSourceKey`, populate them from `DigestCodeSource` when applicable, and include
them in `hashCode()` and `equals()`.

**Replacement `CodeSourceKey` inner class:**

```java
private static class CodeSourceKey {

    private final Uri uri;
    private final java.security.cert.Certificate[] certs;
    // Populated only when the incoming CodeSource is a DigestCodeSource.
    private final String digestAlgorithm;
    private final byte[] digest;
    private final int hashCode;
    final CodeSource cs; // package-private: used by resetArchivedStates

    private CodeSourceKey(CodeSource cs) throws URISyntaxException {
        this.cs = cs;
        certs = cs.getCertificates();
        this.uri = cs.getLocation() != null ? Uri.urlToUri(cs.getLocation()) : null;
        if (cs instanceof DigestCodeSource dcs) {
            this.digestAlgorithm = dcs.getDigestAlgorithm();
            this.digest = dcs.getDigest();   // defensive copy already made by getDigest()
        } else {
            this.digestAlgorithm = null;
            this.digest = null;
        }
        int hash = 7;
        hash = 23 * hash + (uri != null ? uri.hashCode() : 0);
        hash = 23 * hash + (certs != null ? Arrays.hashCode(certs) : 0);
        hash = 23 * hash + (digestAlgorithm != null ? digestAlgorithm.hashCode() : 0);
        hash = 23 * hash + Arrays.hashCode(digest);
        hashCode = hash;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeSourceKey that)) return false;
        // URI equality (RFC 3986, no DNS): handles null on both sides.
        if (uri == null ? that.uri != null : !uri.equals(that.uri)) return false;
        // Certificate equality.
        if (!Arrays.equals(certs, that.certs)) return false;
        // Digest equality: a plain-CS key (null digest) is NOT equal to a
        // DigestCS key (non-null digest) — they represent different identities.
        if (digestAlgorithm == null ? that.digestAlgorithm != null
                                    : !digestAlgorithm.equals(that.digestAlgorithm)) return false;
        return Arrays.equals(digest, that.digest);
    }
}
```

**Cache-slot behaviour after this change:**

| Caller passes | Key digest | Result |
|---|---|---|
| Plain `CodeSource(url, certs)` | `null` | Same slot as all previous plain-CS loads for this url+certs |
| `DigestCodeSource(url, certs, "SHA-256", H1)` | `H1` | Separate slot from the plain-CS entry and from any DigestCS with digest `H2 ≠ H1` |
| `DigestCodeSource(url, certs, "SHA-256", H2)` | `H2` | Separate slot from above |

### Part B — skip re-download when input is already a `DigestCodeSource`

**Why:** In `getProtectionDomain`, when the SecurityManager is active and the URL
is non-null, the current code **always** downloads the URL and computes a fresh
digest, regardless of whether the caller already supplied a `DigestCodeSource`.
This causes a key-value mismatch: the cache key carries the *input* digest (`H`)
while the cached `ProtectionDomain` carries the *downloaded* digest (`H'`).
When `H ≠ H'` the cache entry is permanently dead — `DigestGrant(H).implies(pd)`
will always be false because `pd.getCodeSource()` has `H'`, not `H`.

The digest carried by a `DigestCodeSource` is its identity.  The
`LoadClassPermission` check (which always runs) is the appropriate security gate;
re-downloading the URL is unnecessary and counter-productive.

**Replacement block inside `getProtectionDomain` (inside `if (sm != null)`):**

```java
if (sm != null) {
    URL codebase = cs.getLocation();
    if (codebase != null) {
        Permission checkURL = new URLPermission(key.uri.toString(), "GET:");
        sm.checkPermission(checkURL,
                AccessControlContext.create(new ProtectionDomain[]{pd}, false));

        if (cs instanceof DigestCodeSource) {
            // The caller already supplies a content-addressed DigestCodeSource.
            // Its digest IS its code identity; re-downloading the URL is not
            // required.  The LoadClassPermission check below still enforces
            // the security gate before any class is defined.
            perms = SecureClassLoader.this.getPermissions(cs);
            pd = new ProtectionDomain(cs, perms, SecureClassLoader.this, pals);
        } else {
            // Plain CodeSource: download the artifact and compute its digest.
            // Algorithm is "SHA-256" for now; will be made configurable.
            DigestCodeSource digest;
            try {
                digest = new DigestCodeSource(key.uri, key.certs, "SHA-256");
                perms = SecureClassLoader.this.getPermissions(digest);
            } catch (IOException ex) {
                throw new SecurityException("Unable to contact URL: ", ex);
            } catch (NoSuchAlgorithmException ex) {
                throw new SecurityException(
                        "URL Provider not loaded or unknown algorithm: ", ex);
            }
            pd = new ProtectionDomain(digest, perms, SecureClassLoader.this, pals);
        }
    }
    sm.checkPermission(LOAD_CLASS_ALLOW,
            AccessControlContext.create(new ProtectionDomain[]{pd}, false));
}
```

### Security analysis

| Property | How it is maintained after this fix |
|---|---|
| Re-download for plain `CodeSource` | Unchanged — plain CS still triggers URL download + digest computation |
| Digest-addressed code identity | Each `DigestCodeSource` with a unique digest occupies its own cache slot; the PD's `CodeSource` digest matches the key's digest exactly |
| `LoadClassPermission` gate | Unaffected — the check always runs for both plain and digest sources |
| `URLPermission` gate | Unaffected — the check always runs when a URL is present |
| DigestGrant matching | `DigestGrant(H).implies(pd)` → `pd.getCodeSource()` is `DigestCodeSource(H)` → `Arrays.equals(H, H)` → true (previously dead due to key/value mismatch) |
| Plain-CS repeated loads | Plain-CS key still matches the cached entry (null digest equals null digest) — no regression |

---

*Hand this document (along with context_8 and source files as needed) to a future AI agent to
continue without loss of context. This is version 40, updated with: Item 5 SVID-rotation gap
CLOSED (SPIFFE Principal names are stable; WorkerSubject obtained fresh via
`Subject.getWorkerSubject()`); Work Item 58 DirtyChai `SecureClassLoader.CodeSourceKey`
digest fix spec added in §7 (conversation dated 2026-05-13).*
