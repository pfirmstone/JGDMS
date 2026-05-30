# JGDMS — Security Weaknesses & Implementation Plan — AI Agent Context (v64)

**Purpose:** This document captures the security-weakness analysis and phased
implementation plan produced during the Copilot conversation dated 2026-05-12.
It continues from
[context_8 (v33)](AI_Agent_JGDMS-GrantPermission-RoleManagement-context_8.md)
and is the forward-reference added in §19 of that document.

**GitHub repositories:**
- JGDMS: https://github.com/pfirmstone/JGDMS
- DirtyChai: https://github.com/pfirmstone/DirtyChai

## v64 Change Summary

**Reverted `getPrincipals()` runtime additions — PolicyCondenser remains independent**

`PolicyCondenser` is an auditing tool, not a runtime component.  The `getPrincipals()`
no-arg default method added to `PermissionGrant` and the corresponding override in
`PrincipalGrant` (both from v63) have been removed.  Those additions were introduced
to support an earlier API-based filtering approach that was superseded by the
text-preprocessing implementation.  Keeping them in the runtime API would create a
path for incorporating PolicyCondenser-style filtering into runtime policy parsing,
which violates the design principle that PolicyCondenser remains a standalone offline
auditing tool.

The text-preprocessing implementation in `PolicyCondenser` works entirely on policy
file text (via `StreamTokenizer`) before parsing, so it has no dependency on the
runtime principal API and requires no changes to `PermissionGrant` or `PrincipalGrant`.

**Source files changed:**
- `jgdms-platform/.../PermissionGrant.java` — `getPrincipals()` no-arg default method
  removed (was added in v63; not used by the text-preprocessing approach)
- `jgdms-platform/.../PrincipalGrant.java` — `getPrincipals()` override removed
  (was added in v63; not used by the text-preprocessing approach)

**Files changed:**
- `docs/.../context_10.md` — this update; v63 → v64

---

## v63 Change Summary

**WI65 completed — JWT role-claim filter moved to PolicyCondenser (text-preprocessing)**

`SecurityPolicyWriter` cannot be the correct home for this filter: on the DirtyChai JDK
the runtime built-in `PrincipalGrant` classes (in `jrt:/java.base`) lack the new API and
`SecurityPolicyWriter` has no knowledge of `JwtPrincipal`.  The filter was implemented
in `PolicyCondenser` instead.

**Key discovery:** The DirtyChai JDK builds `org.apache.river.api.security.PermissionGrant`,
`PrincipalGrant`, `PermissionGrantBuilderImp`, and `UnresolvedPrincipal` directly into
`java.base`.  Source changes to those files do not affect the running JDK; the module
system also blocks `setAccessible` on their fields.  A post-parse approach calling
`grant.getPrincipals()` therefore cannot work on the current DirtyChai JDK.

**Implementation approach — text preprocessing via `StreamTokenizer`:**

Before handing the policy file to `DefaultPolicyParser.parse()`, `PolicyCondenser` now
calls `filterJwtFromText(text, roleClaims)`:

1. Tokenises the input with `StreamTokenizer` (same settings as `DefaultPolicyScanner`)
2. For each `grant` block, reads the header principal list and the permission body
3. Drops any `au.zeus.jgdms.security.jwt.JwtPrincipal "claim:value"` whose claim name
   is **not** in `roleClaims`
4. Drops the entire grant block when all its principals were JWT and all were filtered
5. Reconstructs the filtered policy text in a temp file, which is then parsed and
   condensed as usual

`SecurityPolicyWriter` is unchanged.

**Source files changed:**
- `tools/policy-condenser/.../PolicyCondenser.java` — text-preprocessing approach;
  `preprocessJwtFilter`, `filterJwtFromText`, `appendFilteredGrant`,
  `readPermissionEntry`, `isNonRoleJwtByName`, `escapePolicy` added;
  `applyJwtRoleFilter` and `isNonRoleJwtPrincipal` removed; `condense()` calls
  preprocessor before parser; system property is `PolicyCondenser.jwt.roleClaims`
- `tools/policy-condenser/.../PolicyCondenserTest.java` — 6 JWT filter tests;
  assertions use grant count and file-text checks (no `getPrincipals()` calls)

**Sections updated:**
- §3 row 14 → status changed to ✅ Completed (WI65)
- §4.12 — recommendation updated to PolicyCondenser; explanation of DirtyChai constraint added
- §5 row 3.6 → ✅ Completed; file updated to `PolicyCondenser.java`
- §6 WI65 → ✅ Completed
- §10 — spec updated to describe the PolicyCondenser text-preprocessing implementation

**Files changed:**
- `docs/.../context_10.md` — this update; v62 → v63

---

## v62 Change Summary

**WI65 replanned — SecurityPolicyWriter JWT role-claim filter (no new types)**

*Note: v59 and v60 were completed in a separate branch session covering Granola
transaction optimisations (WI63 — Opt-3 single-round commit via
`LamportClock.prepareWithTimestamp`; WI64 — Opt-4C `readOnly` hint via
`TransactionConfig.setReadOnly`). Those items are not present in this clone's
context_10.md. WI numbering in this branch resumes at WI65.*

The v61 plan (WI65 `RoleMatchingPrincipal` interface, WI66 `JwtRolePrincipal`
class, WI67 `SecurityPolicyWriter` rewrite) was revised following review feedback
that it introduced unnecessary complexity.  The correct approach is simpler: no new
principal types are needed, `PrincipalGrant.implies` is unchanged, and only
`SecurityPolicyWriter` needs a small filter to capture role-level JWT principals
using the existing `JwtPrincipal` format.

**Weakness description updated:**
- §3 row 14 — `SecurityPolicyWriter` captures all JWT principals (including
  individual-user claims like `sub`, `email`) rather than restricting to
  role-grain claim names (e.g., `group:administrators`) (🟡 Medium).

**Options analysis revised:**
- §4.12 — removed over-engineered Option A; recommendation is now the targeted
  `SecurityPolicyWriter` filter (Option B in the revised table).

**Work items revised:**
- WI65 — `SecurityPolicyWriter` JWT role-claim filter (single system property
  `SecurityPolicyWriter.jwt.roleClaims`; no new classes).
- WI66 and WI67 removed (subsumed into WI65 as originally numbered).

**Phase plan updated:**
- §5 Phase 3 rows 3.6–3.8 replaced by single row 3.6 (WI65).

**§10 replaced:**
- Simple spec for the `SecurityPolicyWriter` filter; removed all
  `RoleMatchingPrincipal`/`JwtRolePrincipal` content.

**Files changed:**
- `docs/.../context_10.md` — this update; v61 → v62

---

## v58 Change Summary

**WI57 completed; §2.3 DefaultJwtVerifier default; §2.5 DigestCodeSource boot guard; §2.7 Won't Fix; §2.10 Won't Implement**

Implemented in this version:

1. **WI57 — Event-sourced VerdictRegistry read replicas** — `ReadReplicaVerdictRegistry`
   implements `VerdictRegistry` + `RemoteEventListener` + `LeaseListener`; subscribes
   to primary via new `registerGlobalVerdictListener()` API; verifies DER signatures;
   re-subscribes on `UnknownLeaseException` with exponential backoff.
   `VerdictRegistryHolder` extended with `setInstances()`/`getAll()` ordered fallback
   list; `getVerdictByHashWithRetry()` iterates all configured registries before
   falling back to TTL cache.

2. **§2.3 DefaultJwtVerifier default** — `BasicInvocationDispatcher` now holds a
   `DEFAULT_JWT_VERIFIER` constant; `verifyJwtWithCache()` uses it as fallback when
   no operator verifier is configured.  `jgdms-security-jwt` promoted to compile
   scope in jgdms-jeri POM.

3. **§2.5 DigestCodeSource boot guard** — `PreferredProxyCodebaseProvider` probes for
   `java.security.DigestCodeSource` (DirtyChai) at class-load time; on DirtyChai
   deployments the boot-window else-branch now demands `BootstrapPermission` against
   a `DigestCodeSource`-backed `ProtectionDomain` rather than proceeding permissively.

4. **§2.7 SPIRE SVID expiry** — closed Won't Fix; deploy SPIRE HA.

5. **§2.10 Negative grants** — closed Won't Implement; POLP + `SecurePolicyWriter`
   address this; negative grants risk blacklist whack-a-mole policies.

**Files changed:**
- `docs/.../context_10.md` — §6 WI57 row updated (🔲 → ✅ Completed); §9
  closing note updated; v57 → v58
- `docs/.../AI_Agent_JGDMS-SecurityAssessment-context_11.md` — §2.3, §2.5, §2.6,
  §2.7, §2.8, §2.10, §2.11, §4 priority table, §5 status table updated; v3 → v4
- `jgdms-jeri/pom.xml` — `jgdms-security-jwt` test → compile scope
- `jgdms-jeri/.../BasicInvocationDispatcher.java` — `DEFAULT_JWT_VERIFIER` constant + fallback
- `jgdms-pref-class-loader/.../PreferredProxyCodebaseProvider.java` — DigestCodeSource boot guard + WI57 client fallback
- `jgdms-pref-class-loader/.../VerdictRegistryHolder.java` — `setInstances()` / `getAll()`
- `jgdms-platform/.../VerdictRegistry.java` — `registerGlobalVerdictListener()` API
- `verdict-registry-service/.../VerdictRegistryImpl.java` — global listener + burst delivery
- `verdict-registry-service/.../ReadReplicaVerdictRegistry.java` — new file
- `verdict-registry-service/.../ActivatableVerdictRegistryImpl.java` — delegation
- `verdict-registry-dl/.../VerdictRegistryProxy.java` — delegation

---

## v57 Change Summary

**WI62 DirtyChai side confirmed complete; G-3 confirmed complete**

Both items tracked in context_11.md were confirmed complete by @pfirmstone:

1. **WI62 DirtyChai side** — `SecureClassLoader.defineClass(…,Principal[])`
   overloads (two variants) are now present in DirtyChai.  The JGDMS reflection
   probe in `RFC3986URLClassLoader` will now find and use them, correctly
   stamping server SPIFFE principals into the `ProtectionDomain` of loaded
   proxy classes.  Work Item 61's cross-service grant-reuse defence is fully
   operative end-to-end.

2. **G-3** — `SerialObjectPermission` guard has been extended to cover
   `readProxyDesc()` in DirtyChai's `ObjectInputStream` (line 1976).  Dynamic-
   proxy deserialization gadget chains are now blocked symmetrically with the
   existing `readOrdinaryObject()` guard.

**Files changed:**
- `docs/.../context_10.md` — §6 WI62 row updated (🟡 → ✅ Complete); §9
  closing note updated; v56 → v57
- `docs/.../AI_Agent_JGDMS-SecurityAssessment-context_11.md` — §2.1, §2.2,
  §3.1, §3.2, §4 priority table, §5 status table updated; v2 → v3

---

## v56 Change Summary

**Work Item 48 — In-memory signed-verdict cache**

Implements the in-memory signed-verdict cache in `PreferredProxyCodebaseProvider`
(Work Item 48 / Phase 2.6).

**What changed:**

1. **`VERDICT_CACHE`** — a new `ConcurrentHashMap<String, CachedVerdict>` keyed
   by SHA-256 hex digest string, storing verdicts alongside their capture
   timestamp.

2. **`CachedVerdict`** — a package-accessible inner class holding a
   `RegistryVerdict` and the `capturedAtMs` timestamp.  `isAlive(nowMs, ttlMs)`
   returns `true` iff `ttlMs > 0 && (nowMs - capturedAtMs) < ttlMs`.

3. **`verdictCacheTtlMs`** — configurable via system property
   `jgdms.proxy.verdictCacheTtlMs` (default 300 000 ms = 5 min).
   Setting it to `0` disables the fallback entirely.

4. **`getVerdictByHashWithRetry`** updated:
   - On a successful lookup the returned `RegistryVerdict` is inserted into
     `VERDICT_CACHE`.
   - On exhaustion of all retry attempts, before throwing `IOException`, the
     method checks `VERDICT_CACHE` for a fresh entry; if one exists it is
     returned with a `Level.WARNING` log.  If no fresh entry exists, the original
     `IOException` is thrown unchanged.

5. **`clearVerdictCache()`** — package-private test helper, clears `VERDICT_CACHE`.

6. **Tests** — 8 new tests added to
   `PreferredProxyCodebaseProviderVerdictTest`, covering `parseVerdictCacheTtlMs`,
   cache population, cache-hit fallback, cache-expiry, and `CachedVerdict.isAlive`.

**Security property after this change:**

A `VerdictRegistry` outage no longer blocks codebase loads for JARs whose
verdicts were successfully fetched within the last TTL window.  DANGEROUS
verdicts cannot be cached (they cause an immediate `IOException` before the
cache is written).  SAFE and INCONCLUSIVE verdicts are eligible for caching.
Operators can disable the cache entirely by setting
`jgdms.proxy.verdictCacheTtlMs=0`.

**Files changed:**
- `jgdms-pref-class-loader/.../PreferredProxyCodebaseProvider.java` — cache
  field, `CachedVerdict`, `loadVerdictCacheTtlMs`, `parseVerdictCacheTtlMs`,
  `clearVerdictCache`, `getVerdictByHashWithRetry` updated
- `jgdms-pref-class-loader/.../PreferredProxyCodebaseProviderVerdictTest.java` — 8 new tests

---

**Work Item 50 — `SubjectAwareExecutor implements ExecutorService`**

Implements the `SubjectAwareExecutor` wrapper (Work Item 50 / Phase 2.4).

**What changed:**

1. **New class `net.jini.security.SubjectAwareExecutor`** in `jgdms-platform`.
   - Constructor: `SubjectAwareExecutor(ExecutorService delegate)`; null-guards.
   - At task submission (`execute`, `submit`, `invokeAll`, `invokeAny`):
     captures `Subject.current()` (user identity from `Subject.callAs()`) and
     `Security.getContext()` (full `AccessControlContext` including DirtyChai
     SPIFFE `ProtectionDomain`s).
   - At task execution on the worker thread:
     - The captured `SecurityContext` is restored via
       `AccessController.doPrivileged(ctx.wrap(action), ctx.getAccessControlContext())`.
     - If the captured user `Subject` is non-null, the task additionally runs
       inside `Subject.callAs(capturedSubject, task)`.
   - SPIFFE/SPIRE principals propagate via the `AccessControlContext` path and
     are NOT re-bound via `Subject.callAs()`.
   - All `ExecutorService` lifecycle methods (`shutdown`, `shutdownNow`,
     `isShutdown`, `isTerminated`, `awaitTermination`) delegate directly.

2. **Tests** — 13 new tests in `SubjectAwareExecutorTest` covering: constructor
   null-guard, Subject propagation via `execute`/`submit`/`invokeAll`, no-Subject
   path, checked and runtime exception propagation, lifecycle delegation.

**Files changed:**
- `jgdms-platform/.../net/jini/security/SubjectAwareExecutor.java` — new class
- `jgdms-platform/.../net/jini/security/SubjectAwareExecutorTest.java` — new tests
- `docs/.../context_10.md` — this update (v55 → v56)

---

## v55 Change Summary

**Work Item 62 (JGDMS side) — `RFC3986URLClassLoader` Principal-aware `loadClass` / `defineClass`**

Implements the JGDMS half of Work Item 62.  The DirtyChai half (adding the
`defineClass(String, byte[], int, int, CodeSource, Principal[])` and
`defineClass(String, ByteBuffer, CodeSource, Principal[])` overloads to
`SecureClassLoader`) is tracked in the DirtyChai repository and is out of
scope for this PR.

**What changed in `RFC3986URLClassLoader`:**

1. **Reflection probe** — at class initialisation the static block probes
   `java.security.SecureClassLoader` for the two DirtyChai `defineClass`
   overloads:
   - `defineClass(String, byte[], int, int, CodeSource, Principal[])`
   - `defineClass(String, ByteBuffer, CodeSource, Principal[])`

   The results are stored in `DIRTY_CHAI_DEFINE_BYTES` and
   `DIRTY_CHAI_DEFINE_BUFFER` (`null` on a standard JDK).

2. **`ThreadLocal<Principal[]> SERVER_PRINCIPALS`** — a package-accessible
   thread-local that carries the server principals from the new
   `loadClass(String, boolean, Principal[])` entry point down to the inner
   `URLHandler.createClass()` and `URLJarHandler.createClass()` calls, without
   changing the `URLHandler` API.

3. **`loadClass(String, boolean, Principal[])` (new public method)** — sets the
   ThreadLocal with the supplied server principals, delegates to the standard
   `loadClass(String, boolean)`, and clears the ThreadLocal in a `finally`
   block.  When DirtyChai is absent (`DIRTY_CHAI_DEFINE_BYTES == null`) or
   principals are null/empty, delegates to `loadClass(String, boolean)` directly
   with no overhead.

4. **`defineClassWithPrincipals(…)` (new package-private method)** — called by
   both `URLHandler.createClass()` and `URLJarHandler.createClass()` instead of
   the raw `defineClass(…)`.  When `DIRTY_CHAI_DEFINE_BYTES != null` and
   `SERVER_PRINCIPALS.get()` is non-empty, it invokes the DirtyChai overload via
   the cached `Method` reference; otherwise it falls back to the standard
   `defineClass(name, b, off, len, cs)`.

**Security property after this change:**

When running on DirtyChai and the caller passes server principals via
`loadClass(String, boolean, Principal[])`, the `ProtectionDomain` of every
class loaded from the remote codebase will carry both the client's SPIFFE
principals (stamped by DirtyChai's `SecureClassLoader`) **and** the server's
SPIFFE principals (injected by this change).  Policy `principal` clauses can
then match both identities simultaneously, enabling per-service access control
on the loaded code.

**Graceful degradation:**

On a standard JDK (no DirtyChai), the reflection probe finds nothing, all the
new code paths are bypassed at zero cost, and class loading behaviour is
identical to the previous version.  All 51 existing tests pass unchanged.

**Files changed:**
- `jgdms-platform/.../RFC3986URLClassLoader.java` — reflection probe, ThreadLocal,
  `loadClass(String, boolean, Principal[])`, `defineClassWithPrincipals(…)`,
  two `createClass` call-sites updated
- `docs/.../context_10.md` — this update (v54 → v55)

---

## v54 Change Summary

**Documentation corrections to §9.4 (Work Item 61 Limitations) — DirtyChai SubjectDomainCombiner and ProtectionDomain enrichment**

Two inaccuracies identified by @pfirmstone in the §9.4 Limitations section have been corrected:

1. **SubjectDomainCombiner scope** — the previous text implied that DirtyChai's
   `SubjectDomainCombiner` enrichment would make the server SPIFFE principal
   available in the grant evaluation context.  DirtyChai's `SECURITY_MODEL.md`
   (§7) clarifies that `AccessController.getContext()` injects only
   **non-`WorkerSubject`** scoped Subjects; worker threads — the typical threads
   that execute proxy class loads — are explicitly excluded.

2. **ProtectionDomain enrichment scope** — DirtyChai's `SecureClassLoader`
   currently stamps `ProtectionDomain`s with the **client's** SPIFFE principals
   (from the local SPIRE SVID), not the peer/server's.  For the server SPIFFE
   principal to appear in the `ProtectionDomain` of the loaded code, two changes
   are required (tracked as new Work Item 62):
   - DirtyChai must add a `protected loadClass(String, boolean, Principal[])`
     overload to `SecureClassLoader` so callers can supply extra principals to
     embed in the resulting `ProtectionDomain`.
   - JGDMS `RFC3986URLClassLoader` must implement the same signature, using
     reflection to detect whether the DirtyChai `SecureClassLoader` carries that
     overload and calling it when present (graceful degradation on a
     non-DirtyChai JDK).

Work Item 62 added to §6 table.

---

## v53 Change Summary

**Work Item 61 completed — Digest-codesource hijacking defence (Option 1)**

Closes a security gap where a second authenticated service that ships a JAR
with the same content bytes as a legitimately-loaded service could reuse the
`DigestGrant` issued for that digest, thereby gaining `DownloadPermission` and
`LoadClassPermission` without the administrator ever auditing its code.

**Root cause of the gap:**
The `tryGrantPerUriDigestGrants` helper in
`PreferredProxyCodebaseProvider` previously bound per-JAR `DigestGrant`s
only to the **local** SPIFFE principal (from `Security.currentPrincipals()`).
Two distinct services on the same node sharing the same JAR bytes (same
SHA-256 digest) would produce the same grant, so Service B could benefit from
a grant that was issued when connecting to Service A.

**Fix — Option 1:**

1. `tryGrantPerUriDigestGrants` now accepts a second principal array
   (`serverPrincipals`) alongside `localPrincipals`.
2. A new package-private helper `mergePrincipals(Principal[], Principal[])` 
   merges the two arrays into a de-duplicated, insertion-ordered set.
3. Each per-JAR `DigestGrant` is now built with the **union of local and
   server principals**.  The grant fires only when the policy evaluation
   context carries **all** of those principals — i.e. both the local
   workload identity and the specific server that attested to those JAR
   bytes.
4. The call site in `resolve()` passes `serverPrincipals` (already extracted
   from the `ServerMinPrincipal` constraints in the `MethodConstraints`) to
   the helper.
5. Seven new unit tests in
   `PreferredProxyCodebaseProviderVerdictTest` cover `mergePrincipals`
   exhaustively (both-null, both-empty, first-null, second-null, disjoint,
   duplicate-dropped, and the key property that different server principals
   produce distinct grant sets).

**Security gap addressed:**
A second authenticated service (Service B) that deploys a JAR with the same
content digest as Service A will receive a `DigestGrant` bound to
`{local, serverB}`, which is different from Service A's grant
`{local, serverA}`.  Neither grant fires for the other service's code,
because the server principal required by each grant is not present in the
other service's execution context.

**Files changed:**
- `jgdms-pref-class-loader/.../PreferredProxyCodebaseProvider.java`
  — `mergePrincipals`, updated `tryGrantPerUriDigestGrants`, updated call
  site in `resolve()`
- `jgdms-pref-class-loader/…/PreferredProxyCodebaseProviderVerdictTest.java`
  — 7 new `mergePrincipals` tests (51 tests total, all pass)
- §3 weakness table — new row for the digest-codesource hijacking gap
- §6 work items table — new row Work Item 61 (`✅ Completed`)

---

## v51 Change Summary

**Work Item 4.1 (SPIRE HA) completed — HA deployment topology added to `spiffe-admin-deployment.md`**

- Added a new `## High Availability Deployment` section to
  `docs/spiffe-admin-deployment.md` covering:
  - HA architecture diagram (multiple SPIRE servers → shared PostgreSQL → TCP LB →
    SPIRE agents → JGDMS JVMs).
  - HA prerequisites (SPIRE 1.8+, PostgreSQL 14+, TCP load balancer).
  - Shared datastore configuration (`DataStore "sql"` PostgreSQL plugin with
    connection-string, pool, and TLS guidance).
  - CA options: shared `disk` `keys.json` (simplest) vs Vault `UpstreamAuthority`
    (recommended for production).
  - Load balancer configuration (HAProxy example + AWS/GCP guidance; health check
    on SPIRE's `/health/live` HTTP endpoint).
  - Full `spire-server.conf` HA instance example.
  - SPIRE agent configuration pointing at the LB VIP (no code change required).
  - Note that `SpiffeCredentialManager` requires no changes — it is topology-agnostic.
  - Failure mode analysis table (single-server failure, both-server failure, datastore
    failure, agent failure, full-site failure).
  - HA operational checklist (infrastructure, server deployment, agent deployment,
    JGDMS services, failover test).
- §5 Phase 4 table updated: Status column added; Item 4.1 marked `✅ Completed`.
- §6 Work Items table updated: Item 59 added and marked `✅ Completed`.
- Version header bumped from v50 → v51.

---

## v50 Change Summary

**Work Item 55 completed — DiscoveryCredentialProvider with SPIFFE-backed Subject propagation**

- Added `DiscoveryCredentialProvider` and default `NoOpDiscoveryCredentialProvider`
  to formalize discovery credential sourcing.
- Added `SpiffeDiscoveryCredentialProvider` backed by `SpiffeSubjectHolder`.
- Integrated discovery credential provider wiring in `AbstractLookupDiscovery`
  so outbound multicast/unicast discovery operations use the provider `Subject`'s
  SPIFFE principals (embedded in ACC `ProtectionDomain`s by DirtyChai);
  `Subject.doAs(...)` is **not** used — it is incompatible with SPIFFE `Subject`s
  (`Subject.callAs` rejects them, and `SubjectDomainCombiner` cannot obtain
  them).
- Added focused unit tests for SPIFFE discovery credential provider behavior.
- §5 Phase 3 table updated: Phase 3.2 priority changed from `🟡 Sprint 4` to
  `✅ Completed`.
- §6 Work Items table updated: Item 55 status changed from `🔲 Not started` to
  `✅ Completed`.
- Version header bumped from v49 → v50.

---

## v49 Change Summary

**Work Item 56 completed — Pack200/JAR load concurrency bound in preferred-proxy resolve path**

- Completed Work Item 56 by adding a bounded concurrency gate around new-loader
  JAR download/hash/integrity work in
  `PreferredProxyCodebaseProvider.resolve(...)`.
- Added configurable system property
  `jgdms.proxy.maxConcurrentJarLoads` (default `4`) with safe parsing and
  default fallback for invalid or inaccessible values.
- Added focused unit tests for max-concurrent-jar-loads property parsing in
  `PreferredProxyCodebaseProviderVerdictTest`.
- §5 Phase 1 table updated: Phase 1.5 priority changed from `🟠 Sprint 1` to
  `✅ Completed`.
- §6 Work Items table updated: Item 56 status changed from `🔲 Not started` to
  `✅ Completed`.
- Version header bumped from v48 → v49.

---

## v48 Change Summary

**Work Item 46 follow-up — decision record updated with option selection**

- Added an explicit Work Item 46 option record in §8 covering Options 1–5,
  including why Option 4 was selected and what remains for future Option 5.
- Option 4 is now recorded as the implemented baseline: keep preferred-proxy
  `ClassLoader`s cached and use retained, externally-voidable loader-scoped
  grants for `INCONCLUSIVE` loaders.
- Option 5 is recorded as the future structural hardening path: require an
  explicit `INCONCLUSIVEPermit` flow (Work Item 51) so `INCONCLUSIVE` loads are
  administrator-authorized rather than reactively repaired.
- §5 and §6 now mark Work Item 46 as completed for the Option 4 scope.
- Version header bumped from v47 → v48.

---

## v47 Change Summary

**Work Item 46 follow-up — analyze GC/null-strong-reference mitigation**

- Added a new deep-dive section on whether simply dropping strong references to
  a proxy / its proxy-defined classes is a practical mitigation.
- The analysis concludes that GC-based cleanup is only a best-effort secondary
  effect: it may eventually unload a proxy `ClassLoader`, but it is
  nondeterministic, depends on no other strong references surviving, and cannot
  be treated as a security boundary or primary mitigation.
- The report now records that JGDMS' preferred-proxy caches use weak loader
  values, so the caches alone do not intentionally pin the loader, but boomerang
  `SERVICES_EXP`, parent-loader reuse, and ordinary live-object reachability
  still limit practicality.
- Version header bumped from v46 → v47.

---

## v46 Change Summary

**Work Item 46 follow-up — clarify retained grant handles and external voiding**

- Per further maintainer clarification, the revocation model is more capable
  than the v45 wording suggested: `RevocablePolicy.grant(PermissionGrant)` /
  `Security.grant(PermissionGrant)` allow the caller to retain the granted
  `PermissionGrant` object at grant time and later void it.
- `PermissionGrant` already documents decorator-based event notification using a
  transient volatile field plus `Policy.refresh()`, so externally voidable grant
  objects are consistent with the existing design.
- The report now narrows the remaining gap: the codebase lacks a **stock
  externally-voidable loader-scoped grant path in the current proxy-preparer
  flow**, not the underlying revocation concept itself.
- Version header bumped from v45 → v46.

---

## v45 Change Summary

**Work Item 46 follow-up — cache eviction reverted; revocation redesign becomes the new direction**

- Per further maintainer review, the preferred-proxy ClassLoaders should remain
  cached for class-resolution stability; the v43/v44 cache-eviction
  implementation has therefore been reverted from the branch.
- The follow-up analysis confirms that JGDMS has revocation-oriented policy
  primitives (`RevocablePolicy`, dynamic `PermissionGrant`s, void-grant
  sweeping), but no actual public or internal revoke/remove API for a specific
  previously-added loader grant.
- Work Item 46 is therefore reframed from "evict cached INCONCLUSIVE
  ClassLoaders" to "design revocable loader-scoped grants for INCONCLUSIVE
  proxies while keeping ClassLoaders cached".
- §4.2, §5, §6 and §8 are updated to reflect that cache eviction is no longer
  the recommended mitigation; a revocation-based redesign plus Work Item 51
  (`INCONCLUSIVEPermit`) is now the preferred direction.
- Version header bumped from v44 → v45.

---

## v44 Change Summary

**Work Item 46 deep-dive — v43 completion claim retracted; mitigation is only partial**

- Per maintainer review, a deeper analysis was performed on the actual runtime
  behavior of INCONCLUSIVE proxy loads after `DynamicPolicyProvider.grant(...)`.
- The v43 implementation only evicts entries from
  `PreferredProxyCodebaseProvider.CACHE`; it does **not** affect already-loaded
  proxies, `SERVICES_EXP` boomerang reuse, or the parent-loader self-unmarshal
  path.
- `Security.grant(...)` applies to the class loader of the already-loaded proxy
  class (including future `ProtectionDomains` for that loader), so live proxies
  continue to run under the existing loader even after cache eviction.
- `RevocablePolicy` is not a complete answer here: the interface exposes
  `grant(PermissionGrant)` and `revokeSupported()`, but no revoke API, and its
  own documentation warns that many granted capabilities cannot be fully
  revoked once references escape.
- Therefore Work Item 46 should be treated as a **partial mitigation only** and
  is re-opened in §5/§6. A new §8 documents the deep-dive findings and
  recommends shifting the structural mitigation emphasis to Work Item 51
  (`INCONCLUSIVEPermit`).
- Version header bumped from v43 → v44.

---

## v43 Change Summary

**Work Item 46 completed — INCONCLUSIVE ClassLoader eviction on dynamic grant**

- Completed Work Item 46 by tracking `PreferredClassLoader` instances created
  under `INCONCLUSIVE` `VerdictRegistry` results and evicting the corresponding
  preferred-proxy cache entries whenever `DynamicPolicyProvider.grant(...)`
  adds a new dynamic grant.
- Added focused regression tests covering direct cache eviction and the
  `DynamicPolicyProvider.grant(...)` integration path.
- §5 Phase 2.5 priority changed from `🟡 Sprint 3` to `✅ Completed`.
- §6 Work Items table updated: Item 46 status changed from `🔲 Not started` to
  `✅ Completed`.
- Version header bumped from v42 → v43.

---

## v42 Change Summary

**Work Item 58 — fully ✅ completed in DirtyChai (confirmed by @pfirmstone)**

After verifying the latest `pfirmstone/DirtyChai` `SecureClassLoader.java`
(SHA `98e1e31`):

- Work Item 58 (both parts) is **✅ fully complete**.  The repository maintainer
  confirmed: "Item 6 is completed in DirtyChai."
- The updated `SecureClassLoader.java` (SHA `98e1e31`) adds clear in-code
  documentation describing the design intent: `getProtectionDomain` promotes a
  plain `CodeSource` to a `DigestCodeSource` by downloading the artifact and
  computing its content digest, anchoring the trusted digest in the two-layer
  internal cache (`JarResponseCache` + `digestCache`).  The `digestCache` ensures
  that once a digest is established for a `(URI, algorithm)` pair it is used
  consistently throughout the JVM session, making repeated calls idempotent and
  preventing TOCTOU substitution attacks.
- §6 Work Item 58 entry updated to `✅ Complete`.
- §7 updated to reflect the completed state with the authoritative DirtyChai SHA.
- Version header bumped from v41 → v42.

---

## v41 Change Summary

**Work Item 49 completed — SVID exponential-backoff renewal + health endpoint**

- Implemented exponential backoff in `SpiffeCredentialManager.renewalTask()`.
  Initial retry delay is `MIN_RETRY_INTERVAL_SECONDS` (30 s); doubles on each
  successive failure; capped at `max(30, renewalLeadSeconds / 2)` seconds.
  Resets to minimum after a successful refresh.
- Added public `isCredentialValid()` and `secondsUntilExpiry()` health-query
  methods (lock-free volatile reads, safe from any thread).
- Added `volatile Date managedCertExpiry` field to track SVID expiry in O(1).
- `renewalTask()` emits `Level.WARNING` with seconds-until-expiry when the
  credential is below the `renewalLeadSeconds` threshold during a retry.
- Old `RETRY_INTERVAL_SECONDS` private constant renamed to
  `MIN_RETRY_INTERVAL_SECONDS` and made `public static final`.
- 8 new unit tests added to `SpiffeCredentialManagerTest` covering health
  methods and backoff behaviour.
- §4.5 Weakness 6 recommendation marked as completed (Option A + Option D).
- §5 Phase table updated: Phase 1.2 + 1.3 item 49 now `✅ Completed`.
- §6 Work Items table updated: Item 49 status changed to `✅ Completed`.
- Version header bumped from v40 → v41.

---

## v40 Change Summary

**Work Item 45 completed — VerdictRegistry retry backoff**

- Completed Work Item 45 by adding exponential backoff retry to
  `PreferredProxyCodebaseProvider.checkVerdictForJar()` for transient
  `RemoteException` failures from `VerdictRegistry`.
- Added focused unit tests covering retry-then-success and retry exhaustion in
  `PreferredProxyCodebaseProviderVerdictTest`.
- §5 Phase 1 table updated: Phase 1.4 priority changed from `🔴 Immediate` to
  `✅ Completed`.
- §6 Work Items table updated: Item 45 status changed from `🔲 Not started` to
  `✅ Completed`.
- Version header bumped from v39 → v40.

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
| 3 | INCONCLUSIVE verdict allows loading; no re-audit on permission change | 🟠 High | Partially (retained, externally-voidable loader-scoped grants for INCONCLUSIVE loaders — WI46 Option 4; structural fix via INCONCLUSIVEPermit pending — WI51) |
| 4 | VerdictRegistry boot permissive window | 🟠 High | Acknowledged; no fix |
| 5 | VerdictRegistry outage blocks all new proxy loads | 🟠 High | No (fail-secure, but availability impact) |
| 6 | SPIRE single point of failure / SVID expiry gap | 🟠 High | Partially (backoff, but no stale-SVID fallback) |
| 7 | Executor tasks silently lose user identity | 🟠 High | Partially (documented pattern; not enforced) |
| 8 | Policy cannot deny, only relax | 🟡 Medium | No — Java platform limitation |
| 9 | doPrivileged migration incomplete in legacy services | 🟡 Medium | Partially (§11 audit ongoing) |
| 10 | CombinerSecurityManager recursion depth ceiling | 🟡 Medium | No (fixed at 7) |
| 11 | DiscoveryCredentialProvider unimplemented | 🟡 Medium | Yes (SpiffeDiscoveryCredentialProvider backed by SpiffeSubjectHolder; AbstractLookupDiscovery integration — WI55) |
| 12 | Pack200 full-JAR heap materialization | 🟡 Low | Partially (64 MB cap) |
| 13 | Digest-codesource hijacking — second authenticated service with same JAR bytes reuses DigestGrant | 🟠 High | ✅ Yes (WI61 — Option 1: DigestGrants now bound to both local and server SPIFFE principals) |
| 14 | `SecurityPolicyWriter` captures all JWT principals including individual-user claims (`sub`, `email`) rather than restricting to role-grain claim names (e.g., `group:administrators`); policy files must be regenerated whenever group membership changes | 🟡 Medium | ✅ Completed (WI65) |

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
| Fresh proxy load after a `grant()` | **Medium** — new PD starts with current policy; re-audit bypassed if an existing ClassLoader is reused | Work Item 46 Option 4 baseline + Work Item 51 `INCONCLUSIVEPermit` |
| Boot-window INCONCLUSIVE load | **Medium** — no VerdictRegistry check at all; runs under static floor only | Work Item 47 (log upgrade) + ServiceStarter ordering (Phase 4.2) |

| Option | Pros | Cons |
|---|---|---|
| **A** — Treat INCONCLUSIVE as DANGEROUS (strict mode) | Eliminates risk; one-line change | Could break existing deployments where some JARs legitimately produce INCONCLUSIVE |
| **B** — INCONCLUSIVE loads but ClassLoader evicted when policy changes | Forces a new-loader path on some future resolves | **No longer recommended** — may disrupt class resolution and still misses live proxies / non-`CACHE` reuse paths |
| **C** — INCONCLUSIVE loads into permission-restricted sandbox ClassLoader | Closes dangerous path regardless of future grants | Complex; requires CombinerSecurityManager domain-merge interception |
| **D** — INCONCLUSIVE requires explicit administrator opt-in per codebase hash (`INCONCLUSIVEPermit`) | Makes every INCONCLUSIVE load deliberate; audit trail in VerdictRegistry; addresses the fresh-load scenario structurally | New VerdictRegistry API; operational friction for legitimate INCONCLUSIVE JARs |

**Recommendation (updated — v48):** Do **not** evict cached preferred-proxy
ClassLoaders. The deeper analysis in §8, plus maintainer review, indicates that
cache eviction risks class-resolution instability while still failing to address
live proxies and other reuse paths. Work Item 46 is now implemented using
**retained, externally-voidable loader-scoped grants** while keeping loaders
cached (Option 4 in §8.8), and should be paired with Option 5
(`INCONCLUSIVEPermit`) as the structural long-term guard. See Work Items 46,
51.

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
emission for operational visibility. ✅ **Completed** in Work Item 49.

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

### 4.12 Weakness 14 — JWT Principals Not Filtered to Role-Grain During Policy Condensation

**Status: ✅ Completed (WI65 — implemented in `PolicyCondenser`)**

**Background:** `SecurityPolicyWriter` (the shutdown-hook policy-capture tool in
`tools/security-policy-debug`) writes all JWT principals verbatim, including
individual-user claims such as:

```
principal au.zeus.jgdms.security.jwt.JwtPrincipal "sub:alice@example.org"
principal au.zeus.jgdms.security.jwt.JwtPrincipal "email:alice@example.org"
principal au.zeus.jgdms.security.jwt.JwtPrincipal "group:administrators"
```

A role-based policy should reference only role-grain claims (e.g., `group:administrators`)
and omit individual-user identity claims (`sub`, `email`).

**Why `SecurityPolicyWriter` is not the right fix:** On the DirtyChai JDK the runtime
built-in `PrincipalGrant` and related classes (inside `jrt:/java.base`) have no
`getPrincipals()` no-arg method, and the module system prevents `setAccessible` on
their internal fields.  `SecurityPolicyWriter` also has no classpath access to
`au.zeus.jgdms.security.jwt.JwtPrincipal` at capture time.

**Implemented fix — `PolicyCondenser.jwt.roleClaims` system property:**

The filter is applied during condensation, before `DefaultPolicyParser.parse()`.
`PolicyCondenser` reads the policy text, tokenises it with `StreamTokenizer` (same
configuration as `DefaultPolicyScanner`), filters non-role `JwtPrincipal` entries
from each grant header, drops grants whose entire principal set is removed, and
passes the filtered text to the parser.

| Option | Summary | Pros | Cons |
|---|---|---|---|
| **A** | `SecurityPolicyWriter` filter | Simple | DirtyChai JDK blocks runtime access to principal data; `JwtPrincipal` not on classpath |
| **B** ✅ | `PolicyCondenser.jwt.roleClaims` text-preprocessing filter | Works on any JDK; no runtime API dependency; `jgdms-security-jwt` not needed at condense time | Applies only at condense time, not at capture time |

**Property:** `PolicyCondenser.jwt.roleClaims` (comma-separated claim names, e.g. `group,role`).
When absent, all principals are written verbatim (backward-compatible default).

Example condensed output with `-DPolicyCondenser.jwt.roleClaims=group`:

```
grant codebase "file:/opt/jgdms/order-svc/-"
      principal net.jini.jeri.ssl.SpiffePrincipal "spiffe://.../host/selinux/order-svc"
      principal au.zeus.jgdms.security.jwt.JwtPrincipal "group:administrators" {
    permission OrderPermission "submit";
};
```

`sub` and `email` claims are omitted from the condensed file.

See Work Item 65.

---

### Phase 1 — Low Risk, High Impact (no API/protocol changes)

| # | Weakness | Action | Files | Priority |
|---|---|---|---|---|
| 1.1 | Boot window log level (W4) | Upgrade `Level.FINE` → `Level.WARNING` in boot-window path; include codebase hash | `PreferredProxyCodebaseProvider.java` | ✅ Completed |
| 1.2 | SVID renewal backoff (W6) | Replace fixed `RETRY_INTERVAL_SECONDS` with exponential backoff (cap at `renewalLeadSeconds/2`, min 30 s) | `SpiffeCredentialManager.java` | ✅ Completed |
| 1.3 | SVID health metric (W6) | Add `isCredentialValid()` + `secondsUntilExpiry()` to `SpiffeCredentialManager`; emit `Level.WARNING` when < `renewalLeadSeconds × 2` | `SpiffeCredentialManager.java` | ✅ Completed |
| 1.4 | VerdictRegistry retry backoff (W5) | Add 3-attempt exponential backoff (1 s → 2 s → 4 s) before failing in `checkVerdictForJar()` | `PreferredProxyCodebaseProvider.java` | ✅ Completed |
| 1.5 | Pack200 semaphore (W12) | Add `Semaphore(4)` (configurable `jgdms.proxy.maxConcurrentJarLoads`) around JAR download + decompression in `resolve()` | `PreferredProxyCodebaseProvider.java` | ✅ Completed |
| 1.6 | Recursion depth configurable (W10) | Make `CombinerSecurityManager` depth limit a system property (default 10); add startup `SEVERE` warning | `CombinerSecurityManager.java` | 🟠 Sprint 1 |

### Phase 2 — Medium Effort, Targeted Bug Fixes

| # | Weakness | Action | Files | Priority |
|---|---|---|---|---|
| 2.1 | doAs migration — scan (W9) | Run SpotBugs/javaparser scan for all remaining `doAsPrivileged` in service code; produce migration list | All service modules | 🟠 Sprint 2 |
| 2.2 | doAs migration — RegistrarImpl (W9) | Migrate `RegistrarImpl` discovery/multicast threads per STD-003 decision matrix; add regression tests | `RegistrarImpl.java` | 🟠 Sprint 2 |
| 2.3 | doAs migration — AbstractActivationGroup (W9) | Migrate executor path; add regression tests | `AbstractActivationGroup.java` | 🟠 Sprint 2 |
| 2.4 | `SubjectAwareExecutor` (W7) | Implement `SubjectAwareExecutor implements ExecutorService`; update Javadoc in `AbstractJiniService` to recommend it | New class in `jgdms-platform` | ✅ Completed (WI50) |
| 2.5 | INCONCLUSIVE grant revocation redesign (W3) | Keep `ClassLoader`s cached; add revocable loader-scoped grants / revoke hook on policy change for INCONCLUSIVE proxies | `DynamicPolicyProvider.java`, `Security.java`, grant wrapper classes | ✅ Completed (Option 4 baseline) |
| 2.6 | In-memory verdict cache (W5) | Add `ConcurrentHashMap<String, RegistryVerdict>` cache in `PreferredProxyCodebaseProvider`; use cached verdict on `RemoteException` if within TTL | `PreferredProxyCodebaseProvider.java` | ✅ Completed (WI48) |

### Phase 3 — Architectural Changes (new API/protocol)

| # | Weakness | Action | Files | Priority |
|---|---|---|---|---|
| 3.1 | `JwtVerifier` SPI (W2) | Define `JwtVerifier` SPI; wire into `BasicInvocationDispatcher`; add connection-level JWT verification cache; add wire protocol version 0x03 for raw JWT transport | `BasicInvocationDispatcher.java`, new `JwtVerifier.java` | ✅ Completed |
| 3.2 | `DiscoveryCredentialProvider` (W11) | Define interface; implement `SpiffeDiscoveryCredentialProvider` backed by `SpiffeSubjectHolder`; integrate into `AbstractLookupDiscovery` | New interface + impl; `AbstractLookupDiscovery.java` | ✅ Completed |
| 3.3 | Negative grants (W8) | Add `negativeGrants` set to `DynamicPolicyProvider` with same background sweeper as void grants; update `implies()` | `DynamicPolicyProvider.java` | 🟡 Sprint 5 |
| 3.4 | Persistent verdict cache (W5) | Add disk-based signed `RegistryVerdict` cache to `PreferredProxyCodebaseProvider` | `PreferredProxyCodebaseProvider.java`, new `VerdictCache.java` | 🔵 Sprint 6 |
| 3.5 | `INCONCLUSIVEPermit` (W3) | Add `INCONCLUSIVEPermit` registry entry to `VerdictRegistry` API; require it for INCONCLUSIVE loads in strict mode | `VerdictRegistry.java`, `PreferredProxyCodebaseProvider.java` | 🔵 Sprint 6 |
| 3.6 | `PolicyCondenser` JWT role-claim filter (W14) | `PolicyCondenser.jwt.roleClaims` system property; text-preprocessing via `StreamTokenizer` strips non-role `JwtPrincipal` entries before `DefaultPolicyParser.parse()`; grants left with no principals are dropped; 6 unit tests | `tools/policy-condenser`: `PolicyCondenser.java` | ✅ Completed (WI65) |

### Phase 4 — Operational / Deployment

| # | Weakness | Action | Status |
|---|---|---|---|
| 4.1 | SPIRE HA (W6) | Add SPIRE HA deployment topology to `spiffe-admin-deployment.md` | ✅ Completed |
| 4.2 | ServiceStarter ordering (W4) | Document recommended startup ordering (VerdictRegistry client first) as the hardened-boot pattern | ✅ Completed |
| 4.3 | Policy deny documentation (W8) | Document the negative grants feature (Phase 3.3) with worked examples in `security_architecture_feature_table.md` | 🔲 Not started |

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
Phase 3.6 (PolicyWriter)   → standalone (no interface/class dependency)
```

---

## 6. Work Items 44–67

These extend the work-item table in §12 of
[context_8](AI_Agent_JGDMS-GrantPermission-RoleManagement-context_8.md).

| Item | Description | Phase | Status |
|---|---|---|---|
| **44** | `JwtVerifier` SPI — define interface; wire into `BasicInvocationDispatcher`; connection-level JWT cache; `jwtCount:u8` extension of v0x02 wire format; `DefaultJwtVerifier` (exp/iat/iss/aud, no JWKS); `JwtRawToken` public credential; fixed `PRINCIPAL_CTORS` class names | 3.1 | ✅ Completed |
| **45** | VerdictRegistry retry backoff (exponential, 1 s → 2 s → 4 s, 3 attempts) in `checkVerdictForJar()` | 1.4 | ✅ Completed |
| **46** | INCONCLUSIVE grant revocation redesign while keeping `ClassLoader`s cached | 2.5 | ✅ Completed (Option 4 baseline) |
| **47** | Boot-window log upgrade (`Level.FINE` → `Level.WARNING` + SHA-256 hash) | 1.1 | ✅ Completed |
| **48** | In-memory signed-verdict cache (`ConcurrentHashMap<String, RegistryVerdict>`, configurable TTL) | 2.6 | ✅ Completed |
| **49** | SVID exponential-backoff renewal + `isCredentialValid()` / `secondsUntilExpiry()` health endpoint | 1.2 + 1.3 | ✅ Completed |
| **50** | `SubjectAwareExecutor implements ExecutorService` — Subject[] capture-and-rebind wrapper | 2.4 | ✅ Completed |
| **51** | `INCONCLUSIVEPermit` registry entry — require for INCONCLUSIVE loads in strict mode (next major version) | 3.5 | 🔲 Not started |
| **52** | doAs/doAsPrivileged migration: SpotBugs scan + incremental per-site migration (`RegistrarImpl`, `AbstractActivationGroup`) | 2.1–2.3 | 🔲 Not started |
| **53** | Negative grants in `DynamicPolicyProvider` — `negativeGrants` set + background sweeper + `implies()` update | 3.3 | 🔲 Not started |
| **54** | `CombinerSecurityManager` depth limit — configurable system property (default 10) + startup `SEVERE` warning | 1.6 | 🔲 Not started |
| **55** | `DiscoveryCredentialProvider` — interface + `SpiffeDiscoveryCredentialProvider` backed by `SpiffeSubjectHolder` | 3.2 | ✅ Completed |
| **56** | Pack200 semaphore — `Semaphore(4)` (configurable) around JAR download + decompression in `PreferredProxyCodebaseProvider.resolve()` | 1.5 | ✅ Completed |
| **57** | Event-sourced VerdictRegistry read replicas — new `VerdictRegistry.registerGlobalVerdictListener()` API (wildcard subscription with immediate burst delivery); `ReadReplicaVerdictRegistry` implementation (DER signature verification on receipt, `publishedVerdicts` + `hashPublishedVerdicts` caches, `ready` flag, `LeaseRenewalManager` subscription); `VerdictRegistryHolder` extended to fallback ordered list; client fallback on `RemoteException` | 3 (new) | ✅ Completed |
| **58** | DirtyChai `SecureClassLoader.CodeSourceKey` digest fix — `CodeSourceKey` includes `digestAlgorithm`+`digest` fields from `DigestCodeSource` in `hashCode()`/`equals()`; `getProtectionDomain` promotes plain `CodeSource` to content-addressed `DigestCodeSource` (SHA-256) with two-layer cache (`JarResponseCache` + `digestCache`) — see §7 | DirtyChai | ✅ Complete |
| **59** | SPIRE HA deployment documentation — `## High Availability Deployment` section in `docs/spiffe-admin-deployment.md`: HA architecture diagram; shared PostgreSQL datastore; `disk` CA vs Vault `UpstreamAuthority`; HAProxy/NLB TCP load balancer config; agent VIP config; failure-mode analysis table; HA operational checklist | 4.1 | ✅ Completed |
| **60** | ServiceStarter hardened boot ordering documentation — `## Hardened Boot Pattern — ServiceStarter Ordering` section in `docs/standard-safe-codebase-audit-pipeline.md`: VerdictRegistry client first, then inject/register, then start all remaining service descriptors; fail-fast guidance when VerdictRegistry is unreachable at startup | 4.2 | ✅ Completed |
| **61** | Digest-codesource hijacking defence (Option 1) — `mergePrincipals` helper + `serverPrincipals` parameter added to `tryGrantPerUriDigestGrants`; per-JAR `DigestGrant` now bound to union of local and server SPIFFE principals; 7 unit tests added; security docs updated | 1.7 | ✅ Completed |
| **62** | DirtyChai `SecureClassLoader` Principal-aware `defineClass` + JGDMS `RFC3986URLClassLoader` adoption — DirtyChai adds `protected final defineClass(String, byte[], int, int, CodeSource, Principal[])` and `defineClass(String, ByteBuffer, CodeSource, Principal[])` overloads to `SecureClassLoader`; JGDMS `RFC3986URLClassLoader` probes for these overloads at class init via reflection and, when found, uses them to embed server SPIFFE principals in the loaded code's `ProtectionDomain`; a new `loadClass(String, boolean, Principal[])` entry point carries principals via a `ThreadLocal` down to the `defineClass` call sites | DirtyChai + JGDMS | ✅ Complete (both JGDMS and DirtyChai sides) |
| **65** | `PolicyCondenser` JWT role-claim filter — `PolicyCondenser.jwt.roleClaims` system property (comma-separated claim names); text-preprocessing step using `StreamTokenizer` filters non-role `au.zeus.jgdms.security.jwt.JwtPrincipal` entries from each grant header before `DefaultPolicyParser.parse()`; grants whose entire principal list is filtered are dropped entirely (to avoid creating unconstrained grants); `jgdms-security-jwt` not required on the classpath; backward-compatible default (absent property writes all principals); 6 unit tests: role claim kept, non-role claim dropped, non-JWT always kept, property absent writes all, multiple claim names, mixed principals partial filter.  No runtime changes needed — PolicyCondenser operates as a standalone offline auditing tool. | 3.6 | ✅ Completed |

---

## 7. Work Item 58 — DirtyChai `CodeSourceKey` Digest Fix ✅ Complete

**File:** `src/java.base/share/classes/java/security/SecureClassLoader.java` in DirtyChai

**Status:** ✅ Fully completed as of DirtyChai SHA `98e1e31` (confirmed by @pfirmstone).

### What was changed

The fix was implemented as two complementary changes that together eliminate the
original cache-aliasing and dead-cache-entry bugs.

#### `CodeSourceKey` — digest fields added

The `CodeSourceKey` inner class now includes `digestAlgorithm` (String) and
`digest` (byte[]) fields populated from a `DigestCodeSource` when present, and
included in both `hashCode()` and `equals()`:

```java
// Populated only when the incoming CodeSource is a DigestCodeSource.
private final String digestAlgorithm;
private final byte[] digest;

// In constructor:
if (cs instanceof DigestCodeSource dcs) {
    this.digestAlgorithm = dcs.getDigestAlgorithm();
    this.digest = dcs.getDigest();   // defensive copy already made by getDigest()
} else {
    this.digestAlgorithm = null;
    this.digest = null;
}

// In hashCode():
hash = 23 * hash + (digestAlgorithm != null ? digestAlgorithm.hashCode() : 0);
hash = 23 * hash + Arrays.hashCode(digest);

// In equals():
if (digestAlgorithm == null ? that.digestAlgorithm != null
                            : !digestAlgorithm.equals(that.digestAlgorithm)) return false;
return Arrays.equals(digest, that.digest);
```

A plain-CS key (`digest=null`) is not equal to any `DigestCodeSource` key — they
represent different code identities.

#### `getProtectionDomain` — plain `CodeSource` promoted to `DigestCodeSource`

When `sm != null` and `cs.location != null`, a plain `CodeSource` is promoted to a
content-addressed `DigestCodeSource` by downloading the artifact and computing its
SHA-256 digest.  The two-layer internal cache in `DigestCodeSource` ensures this is
idempotent and TOCTOU-safe:

- **Layer 1 — `JarResponseCache`**: caches the raw artifact bytes so repeated calls
  for the same URL within the same JVM session do not trigger network I/O.
- **Layer 2 — `digestCache`**: records the first-computed trusted digest per
  `(URI, algorithm)`.  Any future call for the same URL returns the same digest; a
  different computed digest causes an immediate `SecurityException`.

Together, these caches guarantee that the digest stored in the `ProtectionDomain` is
stable and consistent — any `DigestGrant` specifying that URL's digest will correctly
match the cached `ProtectionDomain`.

#### Additional improvements in the same change

1. **Cache-first**: `pdcache.get(key)` is now the *first* thing `getProtectionDomain`
   does — before the SPIFFE subject lookup, permission checks, or any URL download.
   Permission checks are performed only on the *first* construction of a
   `ProtectionDomain` for a given key; subsequent `defineClass` calls for the same
   source return the already-vetted domain directly.
2. **SM-only SPIFFE**: The `SpiffeCredentialManager.getInstance().getSubject()` call
   (and the `VM.isBooted()` guard around it) are now inside `if (sm != null)`, so the
   no-SM path is a clean `new ProtectionDomain(cs, perms, this, null)`.
3. **No-SM branch**: A clean `else` clause returns a plain `ProtectionDomain` when
   there is no `SecurityManager`, suitable for standard JDK deployments without
   DirtyChai security infrastructure.

### Security properties (post-fix)

| Property | How it is maintained |
|---|---|
| Re-download for plain `CodeSource` | Plain CS still triggers URL download + SHA-256 digest computation via `DigestCodeSource` |
| Digest-addressed code identity | Each distinct digest occupies its own `pdcache` slot; `DigestGrant(H).implies(pd)` returns true when `pd` was built from the same URL |
| TOCTOU defence | `digestCache` layer 2 prevents a second download from replacing the first-trusted digest |
| `LoadClassPermission` gate | Always runs for all paths (URL and no-URL) |
| `URLPermission` gate | Always runs when a URL is present |
| Plain-CS repeated loads | Plain-CS key (`digest=null`) still matches the cached entry — no regression |

---

## 8. Work Item 46 Deep Dive — Why v43 Was Reverted and What Replaces It

This section records the post-PR analysis requested after the v43 implementation
and the subsequent maintainer feedback. The conclusion is now twofold:

1. v43 did **not** fully close the "fresh load after a grant()" gap described
   in §4.2; it only narrowed one specific reuse path, and
2. evicting cached preferred-proxy `ClassLoader`s is not desirable anyway,
   because it risks class-resolution instability.

For that reason the v43 cache-eviction implementation was reverted from this
branch, and Work Item 46 moved to a revocation-based redesign that is now
implemented as the Option 4 baseline in §8.8.

### 8.1 What the reverted v43 code actually changed

`PreferredProxyCodebaseProvider.resolve()` checks `SERVICES_EXP` first, then
reuses the parent loader if the annotation matches, then checks `CACHE`, and
only on a complete miss does it create a new `PreferredClassLoader` and consult
`VerdictRegistry` (`jgdms-pref-class-loader/src/main/java/net/jini/loader/pref/PreferredProxyCodebaseProvider.java:371-529`).

The reverted v43 hook recorded loaders that were created after an
`INCONCLUSIVE` verdict and later removed matching entries from `CACHE` when
`DynamicPolicyProvider.grant(...)` succeeded.

That means the hook only affects the `CACHE` lookup branch. It does **not**
remove entries from `SERVICES_EXP`, does not change the parent-loader reuse
path, and does not revoke any permissions already associated with a live proxy
loader.

### 8.2 Why already-loaded proxies are mostly unaffected

`Security.grant(Class, Principal[], Permission[])` delegates to the installed
`DynamicPolicy` and explicitly applies the grant to the **class loader of the
given class**, including protection domains "not yet created" for that loader
(`jgdms-platform/src/main/java/net/jini/security/Security.java:1067-1118`).

Both `BasicProxyPreparer` and `VerifyingProxyPreparer` grant permissions by
calling `Security.grant(proxy.getClass(), ...)` during `prepareProxy()`
(`jgdms-platform/src/main/java/net/jini/security/BasicProxyPreparer.java:360-413`;
`jgdms-platform/src/main/java/net/jini/security/VerifyingProxyPreparer.java:262-297`).

So once a proxy class has already been loaded, a later `grant()` still widens
permissions for that loader. Evicting `PreferredProxyCodebaseProvider.CACHE`
does not revoke those grants and does not detach the already-loaded proxy from
its existing loader.

### 8.3 Why a subsequent unmarshal can still bypass re-audit

The `ProxySerializer.readResolve()` unmarshal path only calls `resolve()`; it
does **not** perform any grant logic
on its own (`jgdms-platform/src/main/java/org/apache/river/api/io/ProxySerializer.java:232-235`).
A grant only happens later if some caller explicitly runs a proxy preparer.

More importantly, `resolve()` performs the verdict check only inside the "new
loader" branch (`jgdms-pref-class-loader/src/main/java/net/jini/loader/pref/PreferredProxyCodebaseProvider.java:394-529`).
If reuse happens through:

1. `SERVICES_EXP` (boomerang/local-export path, populated by
   `AtomicILFactory.createInstances()` calling `provider.record(...)`:
   `jgdms-jeri/src/main/java/net/jini/jeri/AtomicILFactory.java:442-449`,
   `jgdms-pref-class-loader/src/main/java/net/jini/loader/pref/PreferredProxyCodebaseProvider.java:603-625`),
2. the parent-loader annotation match
   (`jgdms-pref-class-loader/src/main/java/net/jini/loader/pref/PreferredProxyCodebaseProvider.java:376-386`), or
3. a surviving `CACHE` hit,

then VerdictRegistry is not re-consulted.

Therefore v43 only helps the third branch, and only if the caller later
unmarshals again in a context that would otherwise hit `CACHE`.

### 8.4 Would `RevocablePolicy` solve this?

Not as a drop-in fix, but it **is** the right architectural direction, and the
existing revocation model is closer than the v45 wording implied.
Current JGDMS semantics already have several useful building blocks:

- `DynamicPolicy.grant(...)` and `Security.grant(...)` grant at **class-loader
  granularity**, so loader-scoped control is the right abstraction
  (`jgdms-platform/src/main/java/net/jini/security/policy/DynamicPolicy.java:56-98`;
  `jgdms-platform/src/main/java/net/jini/security/Security.java:1067-1118`).
- `ClassLoaderGrant` already models grants in terms of class-loader identity
  (`jgdms-platform/src/main/java/org/apache/river/api/security/ClassLoaderGrant.java:68-99`).
- `RevocablePolicy` and `Security.grant(PermissionGrant)` provide an API shape
  for revocation-aware grant objects, and let the caller keep a reference to
  the exact `PermissionGrant` instance that was granted
  (`jgdms-platform/src/main/java/org/apache/river/api/security/RevocablePolicy.java:76-97`;
  `jgdms-platform/src/main/java/net/jini/security/Security.java:974-989`).
- `PermissionGrant` already has `isVoid()` and decorator support, and
  `DynamicPolicyProvider` already has a background void-grant sweeper
  (`jgdms-platform/src/main/java/org/apache/river/api/security/PermissionGrant.java:40-45,170-187,321-326`;
  `jgdms-platform/src/main/java/net/jini/security/policy/DynamicPolicyProvider.java:401-428,575-598`).

The remaining gap is narrower than "no revoke/remove mechanism exists". The
main issue is that the **current proxy-preparer flow** uses
`Security.grant(Class, Principal[], Permission[])`, which internally creates
the loader grant and does not hand the caller back a retained grant handle
(`jgdms-platform/src/main/java/net/jini/security/Security.java:1067-1118`).
So there is no stock path today for a proxy preparer to later void the exact
loader grant it created.

In addition, while `PermissionGrant` explicitly anticipates externally-driven
decorators with a transient volatile state field, the codebase does not yet
appear to ship a concrete **externally-voidable loader-scoped grant**
implementation for this use case. Today:

- there is no convenience `revoke(...)` API on `RevocablePolicy`,
- the common `Security.grant(Class, ...)` path does not return the internally
  created `PermissionGrant`, and
- a new externally-voidable loader grant would still need wiring for
  `Policy.refresh()` / cache clearing so the voided state becomes effective and
  is swept promptly.

So "use RevocablePolicy and void the retained grants later" is not merely
directionally correct — it is compatible with the current design — but it still
requires a small amount of new plumbing in the proxy-preparer/grant path.

That plumbing is now implemented as Work Item 46 Option 4: externally-voidable
loader-scoped grants are retained per INCONCLUSIVE loader, invalidated on later
grant/policy changes, and tracked using RC weak-reference concurrent maps.

### 8.5 Would GC after clearing strong references help?

Possibly as a **best-effort cleanup effect**, but not as a primary mitigation.

There are two different questions here:

1. can the preferred-proxy `ClassLoader` eventually become unreachable and be
   garbage collected, and
2. if so, is that reliable enough to serve as the security fix for Work Item 46?

The answer to (1) is "sometimes yes"; the answer to (2) is "no".

Why it can sometimes work:

- both preferred-proxy caches use **weak values** for the cached
  `ClassLoader`s, so the caches are explicitly designed not to retain the loader
  by themselves
  (`jgdms-pref-class-loader/src/main/java/net/jini/loader/pref/PreferredProxyCodebaseProvider.java:94-115`).
- `PreferredClassProvider.loaderTable` is also a weak-value loader cache
  (`jgdms-pref-class-loader/src/main/java/net/jini/loader/pref/PreferredClassProvider.java:335-342,352-362`).

So if the application truly drops all strong references to:

- the proxy instance,
- objects reachable from its invocation handler / endpoint,
- classes defined by the proxy loader, and
- the loader itself,

then the JVM may eventually collect the proxy objects and unload the loader.

Why that is not a dependable mitigation:

- **GC and class unloading are nondeterministic.** They happen only when/if the
  JVM decides to collect, not at a security-relevant boundary.
- **It depends on global reachability, not just the holder's local variable.**
  Any surviving strong reference anywhere in the process can keep the proxy
  loader alive: other proxy instances, invocation handlers, endpoint/transport
  state, thread context class loaders, static fields, reflective caches, etc.
- The `SERVICES_EXP` path is specifically a **boomerang reuse** path for proxies
  exported from the same node, so while the service remains exported it is
  entirely plausible that the relevant loader or associated handler/endpoint
  graph remains live for legitimate reasons
  (`jgdms-pref-class-loader/src/main/java/net/jini/loader/pref/PreferredProxyCodebaseProvider.java:349-351,570-592`;
  `jgdms-jeri/src/main/java/net/jini/jeri/AtomicILFactory.java:446-448`).
- Even if a cached loader does disappear, this does **not** retroactively revoke
  permissions from already-escaped references or capabilities.
- Even if a cached loader disappears, reuse can still happen through the
  **parent-loader self-resolution path**, which bypasses creation of a new
  preferred proxy loader entirely when the annotation already matches the parent
  loader
  (`jgdms-pref-class-loader/src/main/java/net/jini/loader/pref/PreferredProxyCodebaseProvider.java:350-360`).

So the most accurate framing is:

- dropping strong references may reduce the lifetime of some proxy loaders in
  practice,
- that could slightly reduce the exposure window in favorable cases,
- but it is too opportunistic and environment-dependent to be the Work Item 46
  fix.

At best, GC-based disappearance of an unused proxy loader is a **helpful side
effect after explicit revocation / discard**, not a substitute for explicit
revocation-aware grant handling.

### 8.6 Smallest viable redesign

The smallest plausible replacement for v43 is:

1. keep preferred-proxy `ClassLoader`s cached,
2. switch the relevant proxy-preparer path from `Security.grant(Class, ...)` to
   constructing an explicit loader-scoped `PermissionGrant`,
3. retain that `PermissionGrant` handle at grant time,
4. make the retained grant externally voidable via a small decorator/flagged
   implementation consistent with `PermissionGrant`'s documented model,
5. call `Policy.refresh()` / clear caches when the grant is voided so the
   existing sweeper can remove it promptly.

Even that redesign would still have an unavoidable semantic limit: references
or capabilities that have already escaped cannot be retroactively clawed back.
It would, however, improve the next deserialized proxy instance that reuses the
same cached loader, provided the guarded capability has not already escaped.

### 8.7 Final assessment

**Conclusion:** the v43 cache-eviction implementation should **not** be kept.
It was both incomplete and potentially harmful to class resolution, so it has
been reverted from this branch.

**Updated recommendation:**

- Keep preferred-proxy `ClassLoader`s cached.
- Treat the Option 4 implementation (retained, externally-voidable
  loader-scoped grants) as the completed baseline for Work Item 46.
- Continue with Option 5 (`INCONCLUSIVEPermit`) as the primary structural
  follow-on, because it makes INCONCLUSIVE loads explicit instead of trying to
  repair them reactively after grants have already changed.

### 8.8 Work Item 46 Option Record (Options 1–5)

| Option | Summary | Decision |
|---|---|---|
| **1** | Keep current behavior; no INCONCLUSIVE-specific mitigation | Rejected — leaves fresh-load trust widening unaddressed |
| **2** | Evict cached preferred-proxy loaders on later grant/policy changes (v43 approach) | Rejected — incomplete coverage and class-resolution instability risk |
| **3** | Rely on GC/null strong-reference disappearance of loader graph | Rejected as primary fix — best-effort only, nondeterministic |
| **4** | Keep loaders cached; add retained, externally-voidable loader-scoped grants for INCONCLUSIVE loaders | **Chosen and implemented** (Work Item 46 baseline) |
| **5** | Require explicit `INCONCLUSIVEPermit` authorization flow for INCONCLUSIVE loads | Planned future hardening (Work Item 51) |

**Why Option 4 was chosen**

- It directly targets the grant-lifecycle problem without depending on
  classloader eviction behavior.
- It preserves preferred-proxy classloader caching for class-resolution
  stability.
- It fits existing JGDMS grant lifecycle semantics (`PermissionGrant.isVoid()`,
  `Policy.refresh()`, background sweep) with minimal architectural disruption.

**What Option 5 still needs (future work)**

- Add/complete explicit `INCONCLUSIVEPermit` policy and registry flow in strict
  mode so INCONCLUSIVE loads require administrator authorization.
- Define migration/compatibility behavior for existing deployments that
  currently allow INCONCLUSIVE loads without explicit permit.
- Add operator-facing guidance and audit tooling so permit decisions and
  exceptions are visible and reviewable.

---

## 9. Work Item 61 — Digest-Codesource Hijacking Defence (Option 1)

### 9.1 The security gap

`PreferredProxyCodebaseProvider.tryGrantPerUriDigestGrants` issues per-JAR
`DigestGrant`s during the boot window, after verifying the server's codebase
bytes against the server-attested digests.  Before this fix, each grant was
bound only to:

- the **content digest** of the JAR (matches any `DigestCodeSource` with that
  digest), and
- the **local** SPIFFE principals from `Security.currentPrincipals()`.

Both conditions depend solely on the **client side**.  If two distinct
services — Service A and Service B — deploy JARs with identical content
(same SHA-256 digest), both services would produce the same `DigestGrant` on
the same client node (same local SPIFFE identity, same digest bytes).

**Attack scenario:**
1. Service A (legitimate) connects to a client.  The client issues a
   `DigestGrant` for digest `D` bound to `{localPrincipal}`.
2. Service B (different logical service, but shares some library JAR with
   Service A) also authenticates to the same client.
3. Service B presents code with digest `D`.
4. The existing `DigestGrant` fires: digest matches, local principal present.
5. Service B's code gains `DownloadPermission` and (on DirtyChai)
   `LoadClassPermission` without Service A's administrator ever auditing it.

### 9.2 Fix — Option 1

The fix adds the **authenticated server's SPIFFE principals** (extracted from
the `ServerMinPrincipal` constraint via `extractServerPrincipals(mc)`) to the
per-JAR `DigestGrant`'s principal requirements alongside the local principals.

**New helper:** `mergePrincipals(Principal[] first, Principal[] second)`

- Merges two principal arrays into a de-duplicated, insertion-ordered set.
- Returns `null` when both inputs are null/empty (backward-compatible: grant
  then applies to any principal, same as before this fix).
- A clone is returned when one side is null/empty so the caller cannot mutate
  the original array.

**Updated `tryGrantPerUriDigestGrants`:**
- Accepts `Principal[] serverPrincipals` in addition to `Principal[] localPrincipals`.
- Calls `mergePrincipals(localPrincipals, serverPrincipals)` to produce the
  combined principal set.
- Passes the combined set to `PermissionGrantBuilder.principals(...)`.

**Updated call site in `resolve()`:**
```java
Principal[] localPrincipals = Security.currentPrincipals();
tryGrantPerUriDigestGrants(algo, localDigests, localPrincipals, serverPrincipals);
```

### 9.3 Security properties after the fix

| Property | Before | After (Option 1) |
|---|---|---|
| Grant bound to local SPIFFE | ✅ | ✅ (unchanged) |
| Grant bound to server SPIFFE | ❌ | ✅ |
| Same JAR bytes from different services | Same grant | Distinct grants |
| Service B reuses Service A's DigestGrant | Possible | Not possible — server principal mismatch |
| Non-SPIFFE deployments (no serverPrincipals) | Unchanged | Unchanged — `mergePrincipals` with null server returns local only |
| DirtyChai grant evaluation | Works when local principal in context | Works when both local AND server principal in context |

### 9.4 Limitations

- The server's SPIFFE principal must be present in the grant evaluation context
  for the grant to fire.  On a standard JDK without DirtyChai, the grant does
  not fire at all (the `DigestCodeSource` type check fails first), so the
  defense is additive and not regressive.
- **DirtyChai `SubjectDomainCombiner` does not help here.**  DirtyChai's
  `AccessController.getContext()` injects scoped Subjects into the evaluation
  context only for **non-`WorkerSubject`** Subjects (see §7 of DirtyChai's
  `SECURITY_MODEL.md`).  Worker threads — the typical executor threads used to
  load proxy classes — are explicitly excluded.  Their context does **not**
  automatically carry any scoped Subject, so the server SPIFFE principal cannot
  reach the grant evaluation context via `SubjectDomainCombiner`.
- If the client is not configured with `ServerMinPrincipal` constraints
  (`serverPrincipals == null`), the grant falls back to local-principal-only
  binding.  Deployments without SPIFFE are not affected.
- **DirtyChai currently enriches `ProtectionDomain`s with the client's SPIFFE
  principals only.**  DirtyChai's `SecureClassLoader.defineClass()` stamps each
  `ProtectionDomain` with principals from the local SPIRE SVID (the workload's
  own `SpiffeSubject`), not with the peer/server's identity.  For the server
  SPIFFE principal to appear in the `ProtectionDomain` of the loaded proxy code,
  two changes are required (tracked as **Work Item 62**):
  1. **DirtyChai** must add
     `protected final Class<?> defineClass(String name, byte[] b, int off, int len, CodeSource cs, Principal[] p)`
     and
     `protected final Class<?> defineClass(String name, ByteBuffer b, CodeSource cs, Principal[] p)`
     overloads to `SecureClassLoader` so that callers can pass the server's
     SPIFFE principals, which are then embedded in the resulting
     `ProtectionDomain`.  (**Not yet implemented in DirtyChai.**)
  2. **JGDMS `RFC3986URLClassLoader`** — ✅ **Implemented (v55).**
     A new `loadClass(String, boolean, Principal[])` public method sets a
     `ThreadLocal<Principal[]>`, delegates to the standard
     `loadClass(String, boolean)`, and clears the ThreadLocal in a `finally`
     block.  Both `URLHandler.createClass()` and `URLJarHandler.createClass()`
     now call `defineClassWithPrincipals(…)` which invokes the DirtyChai
     `defineClass` overload via reflection when available, falling back to the
     standard path on a standard JDK.  The reflection probe (`DIRTY_CHAI_DEFINE_BYTES`
     / `DIRTY_CHAI_DEFINE_BUFFER`) runs once at class initialisation and returns
     `null` on a standard JDK, so the fallback path has zero overhead.
  Until DirtyChai implements step 1, the JGDMS caller can supply server
  principals through `loadClass(String, boolean, Principal[])` but they will be
  silently ignored (DirtyChai overload not present → fallback → standard
  `defineClass`).  The `DigestGrant` still prevents cross-service grant reuse at
  issue time (§9.3 security properties hold at grant construction time), but the
  grant cannot yet fire at class-load time on the current DirtyChai build because
  the server principal is absent from the `ProtectionDomain`.

### 9.5 Files changed

| File | Change |
|---|---|
| `jgdms-pref-class-loader/.../PreferredProxyCodebaseProvider.java` | `mergePrincipals` helper (package-private); `tryGrantPerUriDigestGrants` takes `serverPrincipals`; call site updated; Javadoc updated |
| `jgdms-pref-class-loader/.../PreferredProxyCodebaseProviderVerdictTest.java` | 7 new `mergePrincipals` tests |
| `jgdms-platform/.../RFC3986URLClassLoader.java` | DirtyChai reflection probe; `SERVER_PRINCIPALS` ThreadLocal; `loadClass(String,boolean,Principal[])`; `defineClassWithPrincipals(…)`; `createClass` call-sites updated |
| `docs/.../AI_Agent_JGDMS-SecurityWeaknesses-ImplementationPlan-context_10.md` | §3 row 13 added; §6 WI61+WI62 rows added; §9 new section; v55 update |
| `docs/.../JGDMS-STD-003-MultiSubjectIdentityArchitecture-v3.md` | §DigestGrant note updated |

---

## 10. Work Item 65 — `PolicyCondenser` JWT Role-Claim Filter ✅ Completed

### 10.1 Motivation

Policy files generated by `SecurityPolicyWriter` (the shutdown-hook capture tool)
contain every `JwtPrincipal` in the captured Subject verbatim, including
individual-user claims (`sub`, `email`) that make the grant user-specific.
Role-based grants should reference only role-grain claims (e.g., `group:administrators`).

The filter cannot be placed in `SecurityPolicyWriter` on the DirtyChai JDK:
- The DirtyChai JDK bundles `org.apache.river.api.security.PermissionGrant`,
  `PrincipalGrant`, and related classes directly inside `jrt:/java.base`.  Source
  changes to those files have no runtime effect until the JDK is rebuilt.
- The module system blocks `setAccessible` on the internal `pals` field of
  `PrincipalGrant`, so the principal list cannot be read reflectively.
- `SecurityPolicyWriter` does not have `au.zeus.jgdms.security.jwt.JwtPrincipal`
  on its classpath.

The correct place is `PolicyCondenser`, which processes the policy file as text
before handing it to the parser.

### 10.2 Implementation

**Module:** `tools/policy-condenser`  
**File:** `tools/policy-condenser/src/main/java/org/apache/river/tool/PolicyCondenser.java`

**System property:**

| Property | Type | Default | Meaning |
|---|---|---|---|
| `PolicyCondenser.jwt.roleClaims` | comma-separated strings | *(absent — all principals written)* | When present, any `au.zeus.jgdms.security.jwt.JwtPrincipal` entry whose claim name is **not** in the set is removed; grants left with no principals are dropped |

When the property is absent the condensed output is unchanged (backward-compatible).

**Text-preprocessing approach:**

`PolicyCondenser.condense()` now calls `preprocessJwtFilter(File, Set<String>)` before
`DefaultPolicyParser.parse()`.  The preprocessor:

1. Reads the policy file as UTF-8 text.
2. Calls `filterJwtFromText(text, roleClaims)`.
3. Writes the filtered text to a temp file and returns it.
4. After parsing, deletes the temp file.

`filterJwtFromText` uses `StreamTokenizer` (with the same `slashSlashComments`,
`slashStarComments`, `wordChars('_','_')`, `wordChars('$','$')` settings as
`DefaultPolicyScanner`) to tokenise the input.  For each `grant` block it:

- Collects `signedby`, `codebase`, `digest`, and `principal` header clauses.
- Collects permission entries from the body.
- Removes any `principal au.zeus.jgdms.security.jwt.JwtPrincipal "claim:value"`
  entry whose `claim` (part before first `:`) is not in `roleClaims`.
- Drops the entire grant block when it had principals and all were removed.
- Reconstructs and emits the filtered grant in canonical form.

No runtime dependency on `jgdms-security-jwt` is required; the JWT principal class
name is compared as a plain string.

**Source files changed:**

| File | Change |
|---|---|
| `tools/policy-condenser/.../PolicyCondenser.java` | `preprocessJwtFilter`, `filterJwtFromText`, `appendFilteredGrant`, `readPermissionEntry`, `isNonRoleJwtByName`, `escapePolicy` added; `applyJwtRoleFilter`/`isNonRoleJwtPrincipal` removed; `condense()` restructured |
| `tools/policy-condenser/.../PolicyCondenserTest.java` | 6 JWT filter tests; verified by grant count and file-text inspection |

### 10.3 Testing Checklist

| Test method | Coverage |
|---|---|
| `testJwtRoleClaimKept` | `JwtPrincipal "group:admins"` with `roleClaims=group` → grant retained |
| `testNonRoleJwtClaimDropped` | `JwtPrincipal "sub:alice"` with `roleClaims=group` → grant dropped |
| `testNonJwtPrincipalAlwaysKept` | `X500Principal` → grant retained regardless |
| `testPropertyAbsentWritesAllJwtPrincipals` | Property absent → all JWT grants kept |
| `testMultipleClaimNamesInProperty` | `roleClaims=role,group` → role + group kept, sub dropped |
| `testMixedPrincipalsPartialFilter` | `X500Principal` + `group:admins` + `sub:alice` → `sub:alice` removed, grant retained |

---

*Hand this document (along with context_8 and source files as needed) to a
future AI agent to continue without loss of context. This is version 63.
Version 63 completes WI65: the JWT role-claim filter is implemented in
PolicyCondenser using text-preprocessing (StreamTokenizer), not in
SecurityPolicyWriter. The DirtyChai JDK constraint that prevents runtime
getPrincipals() access was the key design driver.*
