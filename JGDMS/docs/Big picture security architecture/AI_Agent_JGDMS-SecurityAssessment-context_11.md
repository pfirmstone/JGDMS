# JGDMS + DirtyChai — Independent Security Assessment — AI Agent Context (v3)

- **Version:** 3
- **Date:** 2026-05-26
- **Produced by:** GitHub Copilot Agent (independent assessment pass)
- **Assessed against:** context_10.md v56, DirtyChai SECURITY_MODEL.md v2.4
- **Repositories:**
  - JGDMS: https://github.com/pfirmstone/JGDMS
  - DirtyChai: https://github.com/pfirmstone/DirtyChai

---

## Change Summary

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

### 2.3 🟠 High — Wire-Asserted User Principals — Opt-In Verification (Weakness 2)

`BasicInvocationDispatcher.readUserSubjects()` reconstructs `JwtPrincipal`,
`KerberosPrincipal`, etc. from the wire via a `PRINCIPAL_CTORS` allow-list.
The server accepts these principals solely on the vouching authority of the
peer's SPIFFE SVID.  The `JwtVerifier` SPI (Work Item 44, ✅ complete) enables
cryptographic JWT verification, but it is **opt-in**: no verifier is registered
by default, so the default deployment path accepts unverified user assertions.

A compromised service with a valid SVID can assert any user identity and the
server will accept it without challenge unless the operator has explicitly
deployed a `JwtVerifier` (or `DefaultJwtVerifier`).

**Recommended fix:** Register `DefaultJwtVerifier` automatically when a
`JwtRawToken` is present on the wire.  The `exp`/`iat`/`iss`/`aud` check is
free (no JWKS call); there is no operational cost to making it the default.

---

### 2.4 🟠 High — INCONCLUSIVE Verdict — Structural Fix Pending (Weakness 3)

Work Item 46 (Option 4 baseline, ✅ complete) keeps preferred-proxy
`ClassLoader`s cached and uses retained, externally-voidable loader-scoped
grants for `INCONCLUSIVE` loaders.  `DynamicPolicyProvider.grant()` invalidates
retained INCONCLUSIVE grants before issuing new ones.

The residual gap:

- A **fresh proxy load after a policy change** reuses the cached INCONCLUSIVE
  `ClassLoader` and does not re-consult `VerdictRegistry`.  The new grants take
  effect without re-audit.
- `INCONCLUSIVEPermit` (Work Item 51), which would require explicit
  administrator authorization per codebase hash for INCONCLUSIVE loads, is
  `🔲 Not started`.

**Status:** Partial mitigation in place; structural fix (WI51) pending.

---

### 2.5 🟠 High — VerdictRegistry Boot Permissive Window — No Structural Fix (Weakness 4)

When `VerdictRegistryHolder.get() == null` at startup, `checkVerdictForJar()`
skips all bytecode verification.  Work Item 47 (log-level upgrade to
`Level.WARNING` + codebase hash, ✅ complete) and Work Item 60 (ServiceStarter
hardened boot ordering documentation, ✅ complete) are sensible operational
guidance.

However neither enforces correct behaviour.  A deployment that encounters
transient `VerdictRegistry` unavailability at startup, or starts services
in the wrong order, silently loads unaudited code under the static policy floor
with no defensive fallback.  `BootstrapPermission` (gates SPIFFE-principal-based
codebase loading during the boot window) provides a partial structural guard
but does not block non-SPIFFE loads.

---

### 2.6 🟠 High — VerdictRegistry Availability — No HA or Cache (Weakness 5)

Work Item 45 (exponential retry backoff, ✅ complete) is necessary but not
sufficient.  A `RemoteException` from `VerdictRegistry` that outlasts the
retry window causes all new proxy loads to fail.

- Work Item 48 (in-memory signed-verdict cache, `ConcurrentHashMap<String,
  RegistryVerdict>`, configurable TTL) — `🔲 Not started`
- Work Item 57 (event-sourced read replicas — `ReadReplicaVerdictRegistry`
  backed by `VerdictEvent` stream with DER signature verification) — `🔲 Not
  started`

Both are designed (§4.4, §4.4.1 of context_10).  Neither is implemented.
Until at least WI48 is done, any VerdictRegistry outage is a hard availability
ceiling on new proxy loads.

---

### 2.7 🟠 High — SPIRE SVID Expiry — No Stale-SVID Fallback (Weakness 6)

Work Item 49 (exponential-backoff renewal + `isCredentialValid()` /
`secondsUntilExpiry()` health endpoint, ✅ complete) and Work Item 59 (SPIRE HA
deployment documentation, ✅ complete) are solid.

The remaining gap: if SPIRE is unreachable for longer than the SVID validity
window, all mTLS connections requiring a valid SVID fail.  There is no
stale-SVID survival path.  In single-SPIRE-server deployments this is an
operational risk.

---

### 2.8 🟡 Medium — `SubjectAwareExecutor` Not Implemented (Weakness 7)

Work Item 50 (`SubjectAwareExecutor implements ExecutorService` — captures
the active `Subject[]` at submission and rebinds it on the worker thread) is
`🔲 Not started`.

Executor tasks submitted without explicit identity binding silently run without
the user identity that was active when the task was submitted.  This is a
latent confused-deputy risk in any service that submits sensitive work via
`ExecutorService`.  The Javadoc in `AbstractJiniService` recommends the pattern
but there is nothing to enforce it.

---

### 2.9 🟡 Medium — `doAsPrivileged` Migration Incomplete (Weakness 9)

Work Item 52 (SpotBugs/javaparser scan + incremental per-site migration of
`RegistrarImpl` and `AbstractActivationGroup`) is `🔲 Not started`.  Legacy
`doAsPrivileged` call sites in service code may run with incorrectly broad
privilege, undermining POLP in those paths.  The §11 audit table in context_8
identifies the sites; they have not been migrated.

---

### 2.10 🟡 Medium — Negative Grants Not Implemented (Weakness 8)

Work Item 53 (`negativeGrants` set in `DynamicPolicyProvider` + background
sweeper + `implies()` update) is `🟡 Sprint 5` (not yet implemented).  Until
this is done, `DynamicPolicyProvider` can only *add* permissions during a JVM
session; it cannot remove them.  The underlying Java platform limitation is
real, but the JGDMS-level negative-grant design (§4.7 of context_10) is
well-specified.  Phase 4.3 (policy-deny documentation) is also `🔲 Not
started`.

---

### 2.11 🟡 Medium — In-Memory Verdict Cache Missing (Weakness 5 sub-item)

Work Item 48 is called out separately from the full HA path (WI57) because it
is a small, well-scoped change with a significant availability improvement: a
`ConcurrentHashMap<String, RegistryVerdict>` keyed by JAR hash, with
configurable TTL, in `PreferredProxyCodebaseProvider`.  Its absence means the
first `RemoteException` that outlasts the retry window blocks the load
regardless of whether a valid SAFE verdict was recently received.

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
| 4 | **WI51 — `INCONCLUSIVEPermit`** | Structural fix for fresh INCONCLUSIVE loads after policy change; closes the primary residual gap from WI46 | Medium (VerdictRegistry API extension + `PreferredProxyCodebaseProvider` enforcement) |
| 5 | **WI50 — `SubjectAwareExecutor`** | Prevents silent identity loss in executor tasks; small, self-contained | ✅ Completed (v2) |
| 6 | **WI52 — `doAsPrivileged` scan + migration** | Closes residual POLP gaps in `RegistrarImpl` / `AbstractActivationGroup` | Medium (SpotBugs scan + per-site review) |
| 7 | **Make `DefaultJwtVerifier` the default** | Closes Weakness 2 default-path gap with zero operational cost | Trivial (register in `BasicInvocationDispatcher` if `JwtRawToken` present) |
| 8 | **WI53 — Negative grants in `DynamicPolicyProvider`** | Enables policy deny; blocks privilege re-grant after revocation | Medium (background sweeper + `implies()` change) |
| 9 | **WI57 — Event-sourced read replicas** | Eliminates VerdictRegistry as availability bottleneck | Large (new `ReadReplicaVerdictRegistry` + API extension) |

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
| 50 | `SubjectAwareExecutor implements ExecutorService` | ✅ Completed (v2) |
| 51 | `INCONCLUSIVEPermit` registry entry — require for INCONCLUSIVE loads in strict mode | 🔲 Not started |
| 52 | `doAsPrivileged` scan + migration (`RegistrarImpl`, `AbstractActivationGroup`) | 🔲 Not started |
| 53 | Negative grants in `DynamicPolicyProvider` | 🔲 Not started |
| 54 | `CombinerSecurityManager` configurable recursion depth (default 10) + startup `SEVERE` | ✅ Complete |
| 55 | `DiscoveryCredentialProvider` + `SpiffeDiscoveryCredentialProvider` + `AbstractLookupDiscovery` integration | ✅ Complete |
| 56 | Pack200 concurrency semaphore (default 4) | ✅ Complete |
| 57 | Event-sourced VerdictRegistry read replicas (`ReadReplicaVerdictRegistry`) | 🔲 Not started |
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
