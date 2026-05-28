# JGDMS + DirtyChai — Independent Security Assessment — AI Agent Context (v6)

- **Version:** 6
- **Date:** 2026-05-28
- **Produced by:** GitHub Copilot Agent (independent assessment pass)
- **Assessed against:** context_10.md v59, DirtyChai SECURITY_MODEL.md v2.4
- **Repositories:**
  - JGDMS: https://github.com/pfirmstone/JGDMS
  - DirtyChai: https://github.com/pfirmstone/DirtyChai

---

## Change Summary

- **v6 (2026-05-28):** §2.4 updated: WI51 (`INCONCLUSIVEPermit`) completed —
  new `BasicPermission` subclass gates INCONCLUSIVE-verdict JAR loads when
  `jgdms.proxy.inconclusiveStrictMode=true`; `checkVerdictForJar()` enhanced
  with strict-mode branch.  §4 priority table row 4 updated to ✅ Completed.
  §5 status table row 51 updated to ✅ Completed.  context_10.md bumped v58 → v59.
- **v5 (2026-05-27):** §2.8 updated: `SubjectAwareExecutor` enhanced to support
  multi-Subject contexts using `Subject.currentAll()` + varargs
  `Subject.callAs(Callable, Subject...)` (DirtyChai extensions), enabling full
  propagation of multi-principal transaction contexts across thread boundaries.
  §2.9 updated: WI52 partial completion — `AbstractActivationGroup` two
  `doAsPrivileged` call sites migrated to `Subject.callAs` via reflective probe
  (`SUBJECT_CALL_AS`; falls back to `doAsPrivileged` on Java < 18).  §4 and §5
  tables updated accordingly.
- **v4 (2026-05-26):** §2.3 marked ✅ Resolved (`DefaultJwtVerifier`
  auto-registered as fallback in `BasicInvocationDispatcher` when no explicit
  verifier is set; `jgdms-security-jwt` promoted to compile scope in jgdms-jeri
  pom).  §2.5 updated with structural partial fix (DigestCodeSource +
  `BootstrapPermission` now enforced reflectively for non-SPIFFE boot-window
  loads when DirtyChai is present).  §2.6 updated: WI48 ✅ Completed (v2),
  WI57 ✅ Completed (v4, `ReadReplicaVerdictRegistry` + `registerGlobalVerdictListener`
  API + `VerdictRegistryHolder` ordered fallback list).  §2.7 closed Won't Fix
  — deploy SPIRE HA.  §2.8 marked ✅ Resolved (WI50).  §2.10 closed Won't
  Implement — POLP and `SecurePolicyWriter` address this concern.  §2.11
  marked ✅ Resolved (WI48).  §4 and §5 tables updated accordingly.
  context_10.md bumped v57 → v58.
- **v3 (2026-05-26):** WI62 DirtyChai side confirmed complete
  (`SecureClassLoader.defineClass(…,Principal[])` overloads added).  G-3
  (`SerialObjectPermission` guard for `readProxyDesc()`) confirmed complete
  (DirtyChai `ObjectInputStream.java` line 1976).  §2.1, §2.2, §3.1, §3.2,
  §4 priority table, and §5 status table updated accordingly.  WI61 defence
  (WI62-dependent) is now fully operative.
- **v2 (2026-05-26):** WI48 (in-memory signed-verdict cache in
  `PreferredProxyCodebaseProvider`) and WI50 (`SubjectAwareExecutor`) marked
  ✅ Completed; §4 priority table and §5 status table updated.
- **v1 (2026-05-26):** Initial independent security assessment document.

---

## Purpose

This document records an independent security-assessment pass over the full
JGDMS + DirtyChai stack as of 2026-05-26, reading context_10.md (v55),
DirtyChai `SECURITY_MODEL.md` (v2.4), and cross-referencing committed source.
It is intended as a standalone hand-off document for future AI agents, auditors,
or contributors who need a consolidated, honest view of what is strong, what is
open, and what should be done next.

It deliberately does not repeat the full implementation history recorded in
context_10.md.  Instead it provides:

- §1 — Architecture strengths (genuine, rare capabilities)
- §2 — Open vulnerabilities and gaps, scored by severity
- §3 — Cross-cutting observations not captured elsewhere
- §4 — Priority recommendations for the next sprint
- §5 — Quick-reference status table for all tracked Work Items

---

## 1. Architecture Strengths

### 1.1 Content-Addressed Code Trust (`DigestCodeSource` / `DigestGrant`)

DirtyChai promotes every network-loaded `CodeSource` to a SHA-256
`DigestCodeSource` at class-load time.  Policy grants that use a `digest`
clause fire **only** on code whose bytes match the pinned hash.  TOCTOU
substitution attacks on JAR URLs are defeated at the platform level, not the
application level.  This is a real, well-designed defence with no mainstream
Java equivalent.

The two-layer cache (`JarResponseCache` + `digestCache`) in
`SecureClassLoader` (Work Item 58, ✅ complete) makes the digest computation
idempotent across the JVM session: once a `(URI, algorithm)` pair is resolved
the digest is pinned and reused, preventing a URL from serving different bytes
on a second download.

### 1.2 SPIFFE Workload Identity Baked into `ProtectionDomain`

`SecureClassLoader` stamps every loaded class's `ProtectionDomain` with the
current SPIRE SVID principals.  Policy `principal` clauses can therefore
enforce service-to-service identity at the per-call-site level — not just at
the TLS handshake.  The fail-secure boot deferral (`VM.isBooted()` guard) is
correct: if SPIRE is not yet connected, no principals are injected rather than
forged ones.

### 1.3 Multi-Layer Proxy Trust Pipeline (BAE → VerdictRegistry → DigestGrant)

Static bytecode analysis is completed before any proxy is unmarshalled.  Quorum
verdict signing means a single compromised BAE node cannot produce a valid
`SAFE` verdict.  Every `RegistryVerdict` carries the primary's DER signature,
making it self-authenticating.  Per-JAR `DigestGrant`s are now bound to both
local **and** server SPIFFE principals (Work Item 61, ✅ complete), closing the
cross-service grant-reuse gap.

### 1.4 `GrantPermission` Delegation Ceiling

Dynamic grants are bounded at issuance time by the granting caller's own
`GrantPermission`.  Proxy code can never receive permissions beyond what the
granting caller was itself authorised to delegate.  A `doPrivileged` block
inside proxy code stops the stack-walk at the proxy's `ProtectionDomain`, but
that PD can never hold `GrantPermission`, so self-amplification is
structurally impossible.

### 1.5 Transitive ACC Propagation

The full `AccessControlContext` travels with every JERI RPC hop.  This is
unique among mainstream Java distributed systems and is the correct
architectural foundation for distributed POLP enforcement.

### 1.6 DoS Hardening on JAR Downloads

Configurable connect/read timeouts (`jgdms.proxy.jarReadTimeoutMs`, default
30 s), per-JAR byte limit (`jgdms.proxy.maxJarBytes`, default 512 MiB), JAR
count cap (`jgdms.proxy.maxCodebaseJars`, default 100), concurrency gate
(`jgdms.proxy.maxConcurrentJarLoads`, default 4), and `validateDigestOffsets()`
before hash extraction (Work Items 45, 49, 56, ✅ complete) form a solid
multi-layer DoS defence.

---

## 2. Open Vulnerabilities and Gaps

### 2.1 ✅ Resolved — Proxy Deserialization Gap (G-3, DirtyChai §13)

`SerialObjectPermission` previously guarded `ObjectInputStream.readOrdinaryObject()` but
**not** `readProxyDesc()`.  Streams reaching object creation through
`TC_PROXYCLASSDESC` did not hit the `SerialObjectPermission` guard.

**Status:** ✅ Completed in DirtyChai (`ObjectInputStream.java` line 1976).
The guard has been extended to cover `readProxyDesc()`, symmetric to the
existing `readOrdinaryObject()` guard.

---

### 2.2 ✅ Resolved — Work Item 62 DirtyChai Side Complete

The JGDMS side of Work Item 62 was already complete: `RFC3986URLClassLoader` accepts
`serverPrincipals` via a new constructor argument (not a `ThreadLocal` — the
field is `final`) and calls `defineClassWithPrincipals(…)` which invokes the
DirtyChai overload via a reflection probe cached at class-init time.

The **DirtyChai side is now also implemented**: the two
`defineClass(…, Principal[])` overloads in `SecureClassLoader` have been added.
Server principals are now correctly stamped into the `ProtectionDomain` of
loaded proxy classes.  Work Item 61's cross-service grant-reuse defence is
fully operative.

**Status:** ✅ Complete (both JGDMS and DirtyChai sides) (context_10 §6 row 62).

---

### 2.3 ✅ Resolved — Wire-Asserted User Principals — Auto-Verified (Weakness 2)

`BasicInvocationDispatcher.readUserSubjects()` reconstructs `JwtPrincipal`,
`KerberosPrincipal`, etc. from the wire via a `PRINCIPAL_CTORS` allow-list.
The server accepts these principals solely on the vouching authority of the
peer's SPIFFE SVID.  The `JwtVerifier` SPI (Work Item 44, ✅ complete) enables
cryptographic JWT verification.

**Fix (v4):** `BasicInvocationDispatcher` now holds a `DEFAULT_JWT_VERIFIER`
constant (`new DefaultJwtVerifier()`).  `verifyJwtWithCache()` falls back to
`DEFAULT_JWT_VERIFIER` when no operator-configured verifier is set.  The
`exp`/`iat`/`iss`/`aud` check is performed automatically on every `JwtRawToken`
present on the wire, at zero additional operational cost.  An operator-supplied
`JwtVerifier` (e.g. with JWKS validation) overrides the default.  The
`jgdms-security-jwt` dependency was promoted from `test` to `compile` scope in
the jgdms-jeri POM to make `DefaultJwtVerifier` available at runtime.

---

### 2.4 🟠 High — INCONCLUSIVE Verdict — Structural Fix Pending (Weakness 3)

Work Item 46 (Option 4 baseline, ✅ complete) keeps preferred-proxy
`ClassLoader`s cached and uses retained, externally-voidable loader-scoped
grants for `INCONCLUSIVE` loaders.  `DynamicPolicyProvider.grant()` invalidates
retained INCONCLUSIVE grants before issuing new ones.

The residual gap has now been closed structurally by WI51:

- `INCONCLUSIVEPermit` (`net.jini.loader.pref.INCONCLUSIVEPermit`, Work Item 51)
  is a new `BasicPermission` subclass whose target name is the SHA-256 hex
  digest of the specific JAR.  When the system property
  `jgdms.proxy.inconclusiveStrictMode=true` is set,
  `PreferredProxyCodebaseProvider.checkVerdictForJar()` calls
  `AccessController.checkPermission(new INCONCLUSIVEPermit(contentHash))`
  before allowing any INCONCLUSIVE-verdict JAR to load; if not granted,
  `IOException` is thrown.  The default (strict mode off) preserves
  backward-compatible behaviour for deployments that have not yet
  configured their policies for per-digest grants.

**Status:** ✅ Resolved — WI46 Option 4 (loader-scoped grants) + WI51 strict mode gate both implemented.

---

### 2.5 🟡 Medium — VerdictRegistry Boot Permissive Window — Partial Structural Fix (Weakness 4)

When `VerdictRegistryHolder.get() == null` at startup, `checkVerdictForJar()`
skips all bytecode verification.  Work Item 47 (log-level upgrade to
`Level.WARNING` + codebase hash, ✅ complete) and Work Item 60 (ServiceStarter
hardened boot ordering documentation, ✅ complete) are sensible operational
guidance.

**Partial structural fix (v4):** `PreferredProxyCodebaseProvider` now probes
for `java.security.DigestCodeSource` reflectively at class-load time
(`probeDigestCodeSourceCtor()`).  When DirtyChai is present and
`VerdictRegistryHolder.get() == null`, the boot-window else-branch constructs a
`DigestCodeSource` with the JAR digest (and server principal if available) and
demands `BootstrapPermission("loadCodebase")` against that domain, rather than
proceeding permissively.  On a standard JDK without DirtyChai the behaviour
degrades gracefully (permissive with a `WARNING` log).

This closes the non-SPIFFE boot-window gap on DirtyChai deployments.  A
deployment that starts services in the wrong order will now require an explicit
`BootstrapPermission` grant rather than silently loading unaudited code.

---

### 2.6 ✅ Resolved — VerdictRegistry Availability — HA and Cache Implemented (Weakness 5)

Work Item 45 (exponential retry backoff, ✅ complete) is necessary but not
sufficient.  A `RemoteException` from `VerdictRegistry` that outlasts the
retry window causes all new proxy loads to fail.

- Work Item 48 (in-memory signed-verdict cache, `ConcurrentHashMap<String,
  RegistryVerdict>`, configurable TTL) — ✅ Completed (v2)
- Work Item 57 (event-sourced read replicas — `ReadReplicaVerdictRegistry`
  backed by `registerGlobalVerdictListener` push stream with DER signature
  verification; `VerdictRegistryHolder` ordered fallback list; re-subscribe
  on `UnknownLeaseException` with exponential backoff) — ✅ Completed (v4)

`VerdictRegistryHolder` now supports an ordered list of registries
(`setInstances()` / `getAll()`); `getVerdictByHashWithRetry()` iterates all
configured registries before falling back to the in-memory TTL cache.  A
`VerdictRegistry` outage no longer causes a hard availability ceiling as long
as at least one healthy read replica or a live cache entry exists.

---

### 2.7 🚫 Won't Fix — SPIRE SVID Expiry — No Stale-SVID Fallback (Weakness 6)

Work Item 49 (exponential-backoff renewal + `isCredentialValid()` /
`secondsUntilExpiry()` health endpoint, ✅ complete) and Work Item 59 (SPIRE HA
deployment documentation, ✅ complete) are solid.

The remaining gap: if SPIRE is unreachable for longer than the SVID validity
window, all mTLS connections requiring a valid SVID fail.  There is no
stale-SVID survival path.  In single-SPIRE-server deployments this is an
operational risk.

**Resolution:** Deploy SPIRE HA.  There is no safe alternative — a stale SVID
fallback would undermine the SPIFFE workload identity guarantees that the rest
of the architecture depends on.  This item is closed Won't Fix.

---

### 2.8 ✅ Resolved — `SubjectAwareExecutor` Multi-Subject Support (Weakness 7)

Work Item 50 (`SubjectAwareExecutor`) was initially completed in v2 with single-Subject
propagation via `Subject.current()`.  Enhanced in v5 to support multi-Subject contexts:

- **`captureUserSubjects()`**: On a DirtyChai JDK, invokes `Subject.currentAll()` via
  reflection to obtain the full outermost-first `Subject[]` stack active at submission
  time.  Falls back to `Subject.current()` on a standard JDK.
- **`callAsSubjects(Subject[], Callable)`**: On a DirtyChai JDK with more than one
  captured Subject, invokes `Subject.callAs(Callable, Subject...)` (the varargs form)
  via reflection in a single call, so `Subject.currentAll()` on the worker thread
  returns the full set.  For a single Subject or on a standard JDK the standard
  `Subject.callAs(Subject, Callable)` API is used.

This enables multi-principal contexts such as distributed transactions where several
parties are simultaneously active to propagate faithfully across thread boundaries
when tasks are submitted to a `SubjectAwareExecutor`.

`SubjectAwareExecutor` is in `net.jini.security` (jgdms-platform).

---

### 2.9 🟡 Medium — `doAsPrivileged` Migration Partial (Weakness 9)

Work Item 52 (SpotBugs/javaparser scan + incremental per-site migration of
`RegistrarImpl` and `AbstractActivationGroup`) is partially complete as of v5.

- **`RegistrarImpl`**: Already uses `Subject.callAs` — no change needed.
- **`AbstractActivationGroup`**: Two `Subject.doAsPrivileged` call sites migrated to
  use `Subject.callAs` via a static reflective probe (`SUBJECT_CALL_AS`).  The probe
  targets `Subject.callAs(Subject, Callable)` (added in Java 18).  On Java 8–17 where
  the method is absent the probe is `null` and the original `doAsPrivileged` path is
  taken as a safe fallback, preserving backward compatibility for activation deployments
  that still run on older JDKs.

Remaining open sites in service code (if any) identified by the §11 audit in context_8
have not yet been migrated.

---

### 2.10 🚫 Won't Implement — Negative Grants (Weakness 8)

Work Item 53 (`negativeGrants` set in `DynamicPolicyProvider` + background
sweeper + `implies()` update) will not be implemented.  POLP (Principle of
Least Privilege) and `SecurePolicyWriter` address this concern at policy
authoring time.  Implementing negative grants in `DynamicPolicyProvider` risks
encouraging blacklist whack-a-mole policies instead of tight positive
(whitelist) grants.  This item is closed Won't Implement.

---

### 2.11 ✅ Resolved — In-Memory Verdict Cache Implemented (Weakness 5 sub-item)

Work Item 48 (in-memory signed-verdict cache, `ConcurrentHashMap<String,
CachedVerdict>` keyed by SHA-256 JAR hash, configurable TTL via
`jgdms.proxy.verdictCacheTtlMs`, default 300 s) — ✅ Completed (v2).

`PreferredProxyCodebaseProvider` caches SAFE/INCONCLUSIVE verdicts on
successful registry lookup.  On all-registry outage, `getVerdictByHashWithRetry`
falls back to the TTL-guarded cache entry rather than immediately failing the
load.  TTL = 0 disables the cache for strict deployments.

---

### 2.12 🟡 Low — Trusted-Code Confused-Deputy (DirtyChai §13, structural)

Any trusted class that calls `AccessController.doPrivileged(...)` on a path
reachable from untrusted code can perform operations the untrusted caller could
not.  DirtyChai correctly labels this as a design obligation for library
authors rather than a runtime enforcement gap.  Auditing is procedural (WI52
partially covers it) and there is no static-analysis enforcement today.

---

### 2.13 🟡 Low — Finalizer/Cleaner Context Escape (DirtyChai §13)

Finalizer threads now run with `AccessControlContext.neverPrivileged()` (an
improvement), but the creator's ACC is not propagated to `Cleaner` callbacks.
If a trusted object's cleanup path relies on the creator's privileges, this is
a latent gap.  The recommended mitigation is `close()` patterns and process
isolation (`PROCESS_ISOLATION.md` N-9), which require developer discipline.

---

## 3. Cross-Cutting Observations

### 3.1 The DirtyChai Dependency Is the Deepest Structural Risk

Weakness 1 (full security requires DirtyChai — a non-standard JDK fork) is
correctly classified as a design constraint in context_10.md.  However, its
downstream effects are broader than a single weakness:

- Weaknesses 2.2 (WI62 DirtyChai side, now resolved) and 2.1 (G-3 proxy
  deserialization, now resolved) were DirtyChai-internal gaps that have been
  addressed.  The `SubjectDomainCombiner` exclusion of `WorkerSubject`s
  (§9.4 of context_10) remains a DirtyChai-internal design constraint.
- Deployments on a standard JDK forfeit content-addressed trust, SPIFFE
  principal stamping, `LoadClassPermission`, and the `SerialObjectPermission`
  guard.  The security posture difference between DirtyChai and stock JDK
  deployments is very large.

Future AI agents should note that the critical DirtyChai gaps tracked in
§2.1 and §2.2 are now resolved as of DirtyChai commit 17e8e37 / the
SecureClassLoader update.

### 3.2 Work Item 61 Defence Is Correct and Now Fully Operative

The `mergePrincipals` helper and the updated `tryGrantPerUriDigestGrants` call
site (WI61) correctly bind each per-JAR `DigestGrant` to `{local, server}`
principals at construction time.  Cross-service grant reuse is therefore
prevented **at the point the grant is issued**.

With WI62 now complete on the DirtyChai side, the server principal is present
in the `ProtectionDomain` of loaded proxy classes.  The `DigestGrant` policy
evaluation correctly requires `{local, server}` principals to match, and the
WI61 cross-service hardening is fully operative end-to-end.

### 3.3 `BootstrapPermission` Is a Useful Transitional Guard

`BootstrapPermission` (gates SPIFFE-principal-based codebase loading during the
boot window, `vr == null`) is a correct partial structural guard.  It does not
block non-SPIFFE loads during the boot window, but it does prevent an
unauthenticated actor from exploiting the boot window to load arbitrary SPIFFE-
gated codebases.  This gap is narrower than the bare "boot window is open"
framing in context_10.md suggests.

### 3.4 `LocalPrincipalProvider` SPI Is Correctly Placed

The `LocalPrincipalProvider` SPI bridges `jgdms-platform` and `jgdms-jeri`
without a compile-time dependency.  `SpiffeCredentialManager.start()` registers
the managed `Subject` via `Security.registerLocalPrincipalProvider()`;
`Security.getCurrentPrincipals()` falls back to it when the ACC has no
`Subject`.  This is a clean design that avoids coupling the platform to the
JERI SSL module.

### 3.5 `RC.concurrentMap` With Weak Keys Is the Correct Pattern

The `retainedInconclusiveLoaderGrants` map in `Security.java` uses
`RC.concurrentMap` with `Ref.WEAK` keys and `Ref.WEAK` values.  This ensures
the tracking map does not pin `ClassLoader` or `PermissionGrant` objects beyond
their natural reachability.  New code that tracks `ClassLoader`-keyed state
should follow this pattern rather than using `synchronized WeakHashMap`.

---

## 4. Priority Recommendations for the Next Sprint

Listed by impact-per-effort ratio.  All items are well-specified in context_10.

| # | Work Item | Why this sprint | Effort |
|---|---|---|---|
| 1 | **WI62 — DirtyChai `defineClass(…,Principal[])` overloads** | Without this, WI61 cross-service defence is latent and server principals are never in `ProtectionDomain` | ✅ Completed (DirtyChai) |
| 2 | **G-3 — Extend `SerialObjectPermission` to `readProxyDesc()`** | Confirmed open attack surface; symmetric to existing guard; small change | ✅ Completed (DirtyChai) |
| 3 | **WI48 — In-memory signed-verdict cache** | Outage resilience with minimal complexity; prerequisite for WI57 | ✅ Completed (v2) |
| 4 | **WI51 — `INCONCLUSIVEPermit`** | Structural fix for fresh INCONCLUSIVE loads after policy change; closes the primary residual gap from WI46 | ✅ Completed (v6) |
| 5 | **WI50 — `SubjectAwareExecutor`** | Prevents silent identity loss in executor tasks; small, self-contained | ✅ Completed (v2); enhanced multi-Subject (v5) |
| 6 | **WI52 — `doAsPrivileged` scan + migration** | Closes residual POLP gaps in `RegistrarImpl` / `AbstractActivationGroup` | 🟡 Partial (v5) — `AbstractActivationGroup` migrated; `RegistrarImpl` already uses `callAs` |
| 7 | **Make `DefaultJwtVerifier` the default** | Closes Weakness 2 default-path gap with zero operational cost | ✅ Completed (v4) |
| 8 | **WI53 — Negative grants in `DynamicPolicyProvider`** | Enables policy deny; blocks privilege re-grant after revocation | 🚫 Won't Implement — POLP + SecurePolicyWriter address this |
| 9 | **WI57 — Event-sourced read replicas** | Eliminates VerdictRegistry as availability bottleneck | ✅ Completed (v4) |

---

## 5. Work Item Quick-Reference Status Table

This table summarises all Work Items from context_10.md §6 for quick status
lookup.  Future agents should update this table as items complete.

| Item | Description (brief) | Status |
|---|---|---|
| 44 | `JwtVerifier` SPI + `DefaultJwtVerifier` + `JwtRawToken` + wire format v0x02 extended | ✅ Complete |
| 45 | VerdictRegistry retry backoff (3-attempt exponential) | ✅ Complete |
| 46 | INCONCLUSIVE grant revocation redesign (Option 4 baseline — externally-voidable loader grants) | ✅ Complete (partial) |
| 47 | Boot-window log upgrade (`Level.WARNING` + SHA-256 hash) | ✅ Complete |
| 48 | In-memory signed-verdict cache (`ConcurrentHashMap<String, RegistryVerdict>`, TTL) | ✅ Completed (v2) |
| 49 | SVID exponential-backoff renewal + health endpoint | ✅ Complete |
| 50 | `SubjectAwareExecutor implements ExecutorService` | ✅ Completed (v2); multi-Subject (v5) |
| 51 | `INCONCLUSIVEPermit` registry entry — require for INCONCLUSIVE loads in strict mode | ✅ Completed (v6) |
| 52 | `doAsPrivileged` scan + migration (`RegistrarImpl`, `AbstractActivationGroup`) | 🟡 Partial (v5) |
| 53 | Negative grants in `DynamicPolicyProvider` | 🚫 Won't Implement |
| 54 | `CombinerSecurityManager` configurable recursion depth (default 10) + startup `SEVERE` | ✅ Complete |
| 55 | `DiscoveryCredentialProvider` + `SpiffeDiscoveryCredentialProvider` + `AbstractLookupDiscovery` integration | ✅ Complete |
| 56 | Pack200 concurrency semaphore (default 4) | ✅ Complete |
| 57 | Event-sourced VerdictRegistry read replicas (`ReadReplicaVerdictRegistry`) | ✅ Completed (v4) |
| 58 | DirtyChai `SecureClassLoader.CodeSourceKey` digest fix + two-layer cache | ✅ Complete (DirtyChai) |
| 59 | SPIRE HA deployment documentation | ✅ Complete |
| 60 | ServiceStarter hardened boot ordering documentation | ✅ Complete |
| 61 | Digest-codesource hijacking defence — `mergePrincipals` + server principals in `DigestGrant` | ✅ Complete (grant construction + DirtyChai WI62 complete; fully operative) |
| 62 | DirtyChai `defineClass(…,Principal[])` overloads + JGDMS `RFC3986URLClassLoader` adoption | ✅ Complete (both JGDMS and DirtyChai sides) |
| G-3 | `SerialObjectPermission` guard extension to `readProxyDesc()` | ✅ Complete (DirtyChai) |

---

## 6. Security Invariants to Preserve in All Future Changes

These invariants are drawn from DirtyChai `SECURITY_MODEL.md` §15 and
context_10.md.  Any change that could violate them requires explicit review.

1. Validation failures must be fail-secure (under-privilege, not over-privilege).
2. `GrantPermission` intersection must be enforced at grant-issuance time, not
   at check time.
3. A `DigestGrant` must never imply a domain whose `CodeSource` is a plain
   (non-digest) `CodeSource`.
4. `SecureClassLoader` must store a `ProtectionDomain` in `pdcache` only after
   all permission checks have passed and the digest has been computed.
5. `WorkerSubject` / `SpiffeSubject` must never be bound via `Subject.callAs()`
   or `Subject.doAs()`; SPIFFE workload identity propagates only via
   `ProtectionDomain` principal stamping.
6. The `CombinerSecurityManager` recursion guard must decrement correctly in
   all exit paths (normal + exception) to prevent permanent trusted-call
   suppression.
7. INCONCLUSIVE loaders must be tracked in `Security.retainedInconclusiveLoaderGrants`
   using `RC.concurrentMap` with `Ref.WEAK` keys and values to avoid pinning.
8. `setAccessible` must not be called on DirtyChai reflection probes in
   `RFC3986URLClassLoader`; the probes must use the public API and return `null`
   gracefully when the overload is absent.

---

*Hand this document to a future AI agent or reviewer as a consolidated
security-assessment snapshot dated 2026-05-26.  Cross-reference with
context_10.md v56 for full implementation history and context_8.md v33 for
earlier `GrantPermission` and role-management decisions.*
