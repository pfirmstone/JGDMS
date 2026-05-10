# JGDMS — GrantPermission, Role Management & Full Architecture — AI Agent Context (v20)

**Purpose:** This document captures the full conversation context for an AI agent to
continue work on JGDMS role management and `GrantPermission` design without loss of
context. It supersedes and extends v19.

**GitHub repositories:**
- JGDMS: https://github.com/pfirmstone/JGDMS
- DirtyChai: https://github.com/pfirmstone/DirtyChai

---

## v20 Change Summary

This version documents the **`DigestGrant` implementation** in DirtyChai — a new
`PermissionGrant` subtype that conditions grants on the SHA-256 content hash of a
JAR file, complementing the existing `URIGrant` and `CertificateGrant` hierarchy.
All seven files touched are complete and consistent.

**New/changed in v20:**

- **`DigestCodeSource`** — new `CodeSource` subclass carrying `byte[] digest` and
  `String digestAlgorithm`; used by `SecureClassLoader` when loading from a
  content-verified JAR; `implies()` delegates to `DigestGrant` for hash comparison.
- **`DigestGrant extends URIGrant`** — new grant type; `implies(CodeSource, Principal[])`
  checks URI first then verifies `DigestCodeSource.getDigest()` matches; `toString()`
  prepends `digest "algorithm:hexValue",\n` before the `URIGrant` chain.
- **`PermissionGrantBuilder.DIGEST` context constant** — new int constant; `build()`
  switch routes to `new DigestGrant(...)`.
- **`PermissionGrantBuilder.digest(String, byte[])` method** — new builder method
  storing algorithm + raw bytes for `DigestGrant` construction.
- **`PermissionGrantBuilderImp`** — implements `digest()` and `DIGEST` case in `build()`.
- **`DefaultPolicyScanner.GrantEntry`** — added `private final String digest` field
  (raw `"algorithm:hexValue"` string), updated constructor, added `getDigest()` accessor;
  `toString()` updated to include digest.
- **`DefaultPolicyScanner.readGrantEntry()`** — added `"digest"` keyword recognition
  alongside `"codebase"` and `"signedby"`; passes `digest` to `GrantEntry` constructor.
- **`DefaultPolicyParser.resolveGrant()`** — reads `ge.getDigest()`; if non-null,
  splits on `:`, hex-decodes value via `hexDecode()`, calls
  `pgb.digest(algorithm, bytes).context(DIGEST)`; otherwise `context(URI)`.
- **`DefaultPolicyParser.hexDecode()`** — new private static helper; validates even
  length and valid hex characters; throws `InvalidFormatException` on error.
- **`SecurityPolicyWriter`** — emits `digest "algorithm:hexValue",\n` clause when the
  `CodeSource` is a `DigestCodeSource`; added `hexEncode(byte[])` private static helper;
  `hasDigest || hasPrincipals` controls comma placement after `codebase`.
- **`DefaultPolicyParser` missing methods restored** — `getURI()`, `segment()`,
  `expandURLs()`, `resolvePermission()`, `PermissionExpander`, `resolveSigners()`
  (both overloads) were accidentally dropped during editing and have been restored.
- **§1 document table** updated with all new/modified files.
- **§12 work items** updated — `DigestGrant` plan items marked complete.
- **§13** four new design decisions added.

---

## v19 Change Summary

This version documents the **two-implementation model** for `SpiffeCredentialManager`,
the `Subject[]` field change in `Thread`, and the finalised
`AccessController.getContext()` multi-subject injection loop.

**New/changed in v19:**

- **Two `SpiffeCredentialManager` implementations** — JGDMS (`net.jini.jeri.ssl`) uses
  standard Java APIs and constructs a vanilla `Subject` with `X500Principal` +
  `SpiffePrincipal`; DirtyChai (`au.zeus.jdk.authorization.spire`) constructs a sealed
  `SpiffeSubject extends WorkerSubject` for JDK bootstrap use.  `Subject.processWorker()`
  bridges both via `SpiffeSubjectHolder`.
- **`Thread.scopedSubject` field is now `Subject[]`** — mirrors
  `SCOPED_SUBJECT ScopedValue<Subject[]>`; the full array is captured at thread
  construction time and passed to `SubjectAccess.callNoCheck()` in `runWith()`.
- **`AccessController.getContext()` multi-subject loop finalised** — iterates over all
  subjects in `SCOPED_SUBJECT`, skips `WorkerSubject` instances, creates a new
  `SubjectDomainCombiner` per `UserSubject`, accumulates principals progressively
  (last iteration produces ACC with all principals merged), enriches both stack domains
  and immediate `privilegedContext` per subject.
- **`AccessController.SubjectAccess.get()` returns `Subject[]`** — not a single
  `Subject`; the loop processes the full array.
- **§1 document table** updated with latest files reviewed.
- **§6.4 and §10.7** updated to reflect the multi-subject loop and `Subject[]` array.
- **§13** new decisions added for two-implementation model and `Subject[]` thread field.

---

## v18 Change Summary

*(unchanged — JWT/OIDC primary user identity; Kerberos legacy; `jgdms-security-jwt`
module with `JwtPrincipal`, `JwtLoginModule`, etc.)*

- **Sealed Subject hierarchy formalised** — `WorkerSubject` (sealed, `SpiffeSubject`
  only), `UserSubject` (final, public), vanilla `Subject` (legacy).  Removed stale
  references to `LocalWorkerSubject`/`RemoteWorkerSubject`/`DistributedUserSubject`.
- **`WorkerSubject` is ambient, not per-request** — baked into every `ProtectionDomain`
  by `SecureClassLoader` at class load time.  `Subject.doAs(workerSubject, ...)` is NOT
  the modern JERI dispatch path and is explicitly rejected.
- **Remote process identity via serialized ACC** — remote worker identity travels as
  `WorkerSubject` principals in serialized ACC `ProtectionDomain`s, filtered on receipt
  by a `DomainCombiner`.  Not a separate Subject subtype.
- **`UserSubject` varargs `callAs`** — preferred multi-user path is
  `Subject.callAs(Callable, UserSubject...)`.  `WorkerSubject` cannot be passed at
  compile time.  OpenJDK-compatible `callAs(Subject, Callable)` retains a runtime guard.
- **`doAs` routing stricter** — accepts only vanilla `Subject` and `null`.  `UserSubject`
  must use `callAs`.  `WorkerSubject` must be rejected with `IllegalArgumentException`.
- **SSL/TLS endpoint Subject selection updated** — `SslEndpointImpl` priority is now
  (1) direct `SpiffeCredentialManager.getInstance().getSubject()`, (2)
  `Subject.getWorker()`, (3) reject — `UserSubject` and vanilla `Subject` are never TLS
  identities.  `Subject.current()` is no longer a fallback for TLS.
- **`DomainCombiner` retention rationale clarified** — retained as API compatibility
  layer for ACC serializer receiving-side domain verification.
  `SubjectDomainCombiner` deprecated.
- **§6.4 table** revised from "Two Carriers" to the three-layer identity model
  (process worker, remote process, user/legacy).
- **§9 ServiceUI** revised — worker identity is ambient; no outer `doAs(spiffeSubject)`
  wrapper is needed or correct.
- **§10 / §10.11** dispatch example rewritten — removed the
  `Subject.doAs(workerSubject, ...)` outer wrapper; updated dispatch pattern and SSL
  priority table.
- **Remote ACC serialization discussion added** — §10 now cross-references STD-003 §10
  on `@AtomicSerial` ACC serialization and receiving-side domain verification.

---

## v17 Change Summary

This version aligns the document with **JGDMS-STD-003 v3** (Multi-Subject Identity
Architecture).  The sealed Subject hierarchy, strict `doAs` routing, remote ACC
transmission, `DomainCombiner` retention rationale, and SSL/TLS endpoint Subject
selection have all been revised to reflect the current standard.

**New/changed in v17:**

- **Sealed Subject hierarchy formalised** — `WorkerSubject` (sealed, `SpiffeSubject`
  only), `UserSubject` (final, public), vanilla `Subject` (legacy).  Removed stale
  references to `LocalWorkerSubject`/`RemoteWorkerSubject`/`DistributedUserSubject`.
- **`WorkerSubject` is ambient, not per-request** — baked into every `ProtectionDomain`
  by `SecureClassLoader` at class load time.  `Subject.doAs(workerSubject, ...)` is NOT
  the modern JERI dispatch path and is explicitly rejected.
- **Remote process identity via serialized ACC** — remote worker identity travels as
  `WorkerSubject` principals in serialized ACC `ProtectionDomain`s, filtered on receipt
  by a `DomainCombiner`.  Not a separate Subject subtype.
- **`UserSubject` varargs `callAs`** — preferred multi-user path is
  `Subject.callAs(Callable, UserSubject...)`.  `WorkerSubject` cannot be passed at
  compile time.  OpenJDK-compatible `callAs(Subject, Callable)` retains a runtime guard.
- **`doAs` routing stricter** — accepts only vanilla `Subject` and `null`.  `UserSubject`
  must use `callAs`.  `WorkerSubject` must be rejected with `IllegalArgumentException`.
- **SSL/TLS endpoint Subject selection updated** — `SslEndpointImpl` priority is now
  (1) direct `SpiffeCredentialManager.getInstance().getSubject()`, (2)
  `Subject.getWorker()`, (3) reject — `UserSubject` and vanilla `Subject` are never TLS
  identities.  `Subject.current()` is no longer a fallback for TLS.
- **`DomainCombiner` retention rationale clarified** — retained as API compatibility
  layer for ACC serializer receiving-side domain verification.
  `SubjectDomainCombiner` deprecated.
- **§6.4 table** revised from "Two Carriers" to the three-layer identity model
  (process worker, remote process, user/legacy).
- **§9 ServiceUI** revised — worker identity is ambient; no outer `doAs(spiffeSubject)`
  wrapper is needed or correct.
- **§10 / §10.11** dispatch example rewritten — removed the
  `Subject.doAs(workerSubject, ...)` outer wrapper; updated dispatch pattern and SSL
  priority table.
- **Remote ACC serialization discussion added** — §10 now cross-references STD-003 §10
  on `@AtomicSerial` ACC serialization and receiving-side domain verification.

---

## v16 Change Summary

This version documents a significant architectural refinement to how the scoped user
Subject participates in permission checks and propagates to spawned threads.  The
combiner-injection approach used in earlier versions has been replaced by direct
principal baking into `ProtectionDomain` arrays, with separate `ScopedValue`
re-establishment for `Subject.current()` in spawned threads.

**New/changed in v16:**

- **`Subject.hashCode()`** — cached for read-only Subjects; `volatile` field; correctly
  restored in `readObject()` after deserialisation.
- **`SubjectDomainCombiner`** — `equals()` and `hashCode()` now implemented using the
  previously unused `hashCode` field (initialised to `Subject.hashCode()` in constructor).
- **`ContextKey.equals()`** — inverted `!` security bug fixed (was returning `true` when
  `privilegedContext` fields differed; now correctly returns `true` when they are equal).
- **`AccessController.getContext()`** — scoped user Subject injection mechanism changed
  from placing a `Scoped` combiner on the ACC to directly baking user Subject principals
  into the `ProtectionDomain` array via `SubjectDomainCombiner.combine()`.  The original
  combiner is preserved.  The immediate `privilegedContext` domains are also enriched so
  that principal-scoped grants survive `doPrivileged` boundaries.
- **`Thread.runWith()`** — `scopedSubject` field captured at thread construction time
  (guarded by `VM.isBooted()`); re-established via `Subject.SubjectAccess.callNoCheck()`
  in `runWith()` so that `Subject.current()` works correctly in spawned threads.
- **Thread propagation divergence from OpenJDK** — documented explicitly in
  `Subject.callAs()` javadoc.
- **`AccessControlContext.create()` three-arg trusted overload** — used in `getContext()`
  injection path to avoid `checkAuthorized()` re-entrancy.
- **`doAs`, `doAsPrivileged`, `callAs` javadoc** — fully rewritten to document the
  two-Subject model, propagation semantics, `doPrivileged` boundary behaviour, and
  OpenJDK divergence.

---

## 1. Documents Read This Session (cumulative)

*(All entries from v15 §1 are retained. New entries below.)*

| Document | Location | Key contribution |
|---|---|---|
| `AI_Agent_JGDMS-RemotePolicyService-context.md` | uploaded | Remote policy service design, three-layer policy stack, bootstrap trust chain |
| `AI_Agent_JGDMS-DynamicPolicyProvider-context_1.md` | uploaded | DynamicPolicyProvider void-grant eviction, ACC/PD reachability, no ReferenceQueue needed, String[] wire format |
| `standard-atomic-serial-compliance.md` | JGDMS repo | JGDMS-STD-001: @AtomicSerial rules, BAE verdicts, compliant patterns |
| `standard-safe-codebase-audit-pipeline.md` | JGDMS repo | JGDMS-STD-002: five-host SCAP architecture, BAE pool, VerdictRegistry |
| `SERVICE_AND_PROXY_LIFE_CYCLES.md` | JGDMS repo | Three interlocking lifecycles: service start/runtime/shutdown, service discovery, ProxyCodebaseSpi |
| `SECURITY_MODEL_ANALYSIS.md` | DirtyChai repo | DirtyChai security model: CombinerSecurityManager, guard inventory, virtual thread pinning, process isolation boundaries |
| `AdvisoryDynamicPermissions.java` | JGDMS source | Interface: ClassLoader-implemented; META-INF/PERMISSIONS.LIST; advisory permission declaration |
| `VerifyingProxyPreparer.java` | JGDMS source | ProxyPreparer implementation: explicit vs advisory grant paths, Security.grant() call site |
| `PreferredProxyCodebaseProvider.java` | JGDMS source | ProxyCodebaseSpi implementation: ClassLoader cache keyed by (InvocationHandler, codebase[], parent) |
| `JarAnalysisReport.java` | JGDMS source | `serialVersionUID=2L`; new `String[] declaredPermissions` field from `META-INF/PERMISSIONS.LIST`; backward-compat 3-arg constructor delegates to 4-arg; `getDeclaredPermissions()` returns defensive copy; declared permissions included in canonical signing bytes |
| `JarAnalyzer.java` | JGDMS source | Phase 1 JAR scan now detects `META-INF/PERMISSIONS.LIST`; `parsePermissionsList()` helper strips blank and `#`-comment lines; sorted permissions fed into engine signature |
| `AbstractJiniService.java` | JGDMS source | Abstract base for all Jini services: export, JoinManager, ServiceID, ReliableLog, LoginContext integration, ReadyState, template methods |
| `Subject.java` | DirtyChai source | ✅ **Updated:** Class javadoc now documents two-Subject identity model (workload on ACC, user on ScopedValue); additive principal merging by SubjectDomainCombiner; ServiceUI use case example; structural discipline for daemon threads; **ClassSet uses LinkedHashSet for certificate chain ordering** |
| `SubjectDomainCombiner.java` | DirtyChai source | ✅ **Updated:** `getMergedPrincipals()` now merges ACC Subject principals only; SCOPED_SUBJECT is captured earlier in `AccessController.getContext()` (via `Subject.NoCheck` / `AccessController.SubjectAccess`) and installed as a combiner-bound Subject in the returned ACC |
| `SpiffeCredentialManager.java` | DirtyChai source | ✅ **Fixed (v7):** backoff race fixed; empty SVID no longer wipes valid Subject; subjectBundle private final |
| `SpireConnection.java` | DirtyChai source | ✅ **Fixed (v7):** frame size cap; UTF-8 charset; nextStreamId volatile; blocking channel documented |
| `SpireProtobuf.java` | DirtyChai source | ✅ **Fixed (v7):** varint boundary corrected; StandardCharsets.UTF_8 used |
| `SpiffeCredentialManager.java` | JGDMS `jgdms-jeri` source | ✅ **v9 deep-dive:** 795-line JGDMS-side impl; `FileSvidSource`; `updateSubjectCredentials()` adds `X500Principal` + `SpiffePrincipal`; exponential backoff; unit tests at `SpiffeCredentialManagerTest` |
| `RemotePolicyService.java` | JGDMS platform source | ✅ **v9 deep-dive:** Interface fully implemented — 5 methods: `replace()`, `getCurrentGrants()`, `registerForPolicyUpdates()`, `renewPolicyLease()`, `cancelPolicyLease()` |
| `InMemoryPolicyServiceImpl.java` | JGDMS policy-service source | ✅ **v9 deep-dive:** 453-line core POJO; `PolicyUpdateEvent`, `PolicyEventLease`, `LandlordLease`, async event dispatch; no unit tests exist yet |
| `ActivatableInMemoryPolicyServiceImpl.java` | JGDMS policy-service source | ✅ **v9 deep-dive:** Extends `AbstractJiniService`; delegates all `RemotePolicyService` calls to `InMemoryPolicyServiceImpl`; Phoenix-activatable + `NonActivatableServiceDescriptor` constructors |
| `RemotePolicyServiceProxy.java` | JGDMS policy-service-dl source | ✅ **v9 deep-dive:** `@AtomicSerial` smart proxy; `ConstrainableRemotePolicyServiceProxy` inner class for full `RemoteMethodControl` |
| `VerdictRegistryImpl.java` | JGDMS verdict-registry source | ✅ **v9 deep-dive:** 1488-line server impl; quorum policy; signature verification; persistence via `ReliableLog` |
| `ActivatableVerdictRegistryImpl.java` | JGDMS verdict-registry source | ✅ **v9 deep-dive:** Extends `AbstractJiniService`; Phoenix-activatable and `NonActivatableServiceDescriptor` constructors |
| `VerdictRegistryProxy.java` | JGDMS verdict-registry-dl source | ✅ **v9 deep-dive:** `@AtomicSerial` smart proxy with constrainable variant |
| `BytecodeAnalysisEngineImpl.java` | JGDMS BAE service source | ✅ **v9 deep-dive:** Full push-model `analyzeJar(AnalysisRequest)` implementation; signs `JarAnalysisReport` |
| `ClinitBlockingVisitor.java` | JGDMS BAE service source | ✅ **v9 deep-dive:** 511-line ASM visitor; BFS call-graph; `detectClinitCycles()` via Tarjan SCC (`TarjanScc` inner class — no separate `ClinitCycleVisitor` class) |
| `AtomicSerialComplianceVisitor.java` | JGDMS BAE service source | ✅ **v9 deep-dive:** 537-line ASM visitor; tests at `AtomicSerialComplianceVisitorTest` |
| `JarAnalyzer.java` | JGDMS BAE service source | ✅ **v9 deep-dive:** 469 lines; phases 1 (index), 2a (cycle detect), 2b (per-class analysis); `declaresPermissionClass()` handles `className#action` |
| `AccessController.java` | DirtyChai source | ✅ **Updated (v16):** `getContext()` now injects scoped user Subject principals directly into domain array via `SubjectDomainCombiner.combine()` rather than placing a `Scoped` combiner on the ACC; immediate `privilegedContext` enriched too; uses `create(acc, combiner, true)` to avoid re-entrant `checkAuthorized()`; `SubjectAccess` inner class bridges `Subject.NoCheck` sealed trust chain |
| `AccessControlContext.java` | DirtyChai source | ✅ **Updated (v16):** `ContextKey.equals()` inverted `!` bug fixed; `ContextKey` includes `DomainCombiner` in equality/hash so cache correctly differentiates ACCs by user Subject; `ContextCache` uses `Ref.WEAK` + 2000ms cycle |
| `Subject.java` | DirtyChai source | ✅ **Updated (v16):** `hashCode()` cached for read-only Subjects (`volatile int hashCode` field); `computeHashCode()` extracted; `setReadOnly()` computes and caches hash under `synchronized`; `readObject()` restores cached hash after deserialisation; `callAs()`, `doAs()`, `doAsPrivileged()` javadoc fully rewritten |
| `SubjectDomainCombiner.java` | DirtyChai source | ✅ **Updated (v16):** `equals()` and `hashCode()` now implemented using previously unused `hashCode` field |
| `Thread.java` | DirtyChai source | ✅ **Updated (v16):** `scopedSubject` field captured at construction (guarded by `VM.isBooted()`); `runWith()` re-establishes user Subject via `Subject.SubjectAccess.callNoCheck()` for `Subject.current()` in spawned threads; `SubjectAccess` inner class extends `Subject.NoCheck` |
| `VirtualThread.java` | DirtyChai source | ✅ **Confirmed (v16):** `VirtualThread.run()` calls `Thread.runWith()` (inherited `final` method); no separate changes required; user Subject propagation works identically for virtual threads |
| `ScopedValue.java` | DirtyChai source | ✅ **Reviewed (v16):** `Snapshot` / `Carrier` immutable linked-list binding chain confirmed; `NEW_THREAD_BINDINGS` sentinel set unconditionally at end of `Thread` constructor — pre-seeding `scopedValueBindings` in constructor is not possible; `scopedSubject` field approach confirmed correct |
| `CombinerSecurityManager.java` | DirtyChai source | ✅ **Reviewed (v16):** `Action.run()` calls `AccessController.getContext()` for ACC optimisation; with domain-enrichment approach (no `Scoped` combiner on ACC) this no longer risks baking a user Subject into a shared `contextCache` entry |
| `ContextCache.java` | DirtyChai source | ✅ **Reviewed (v16):** Weak-valued `ConcurrentSkipListMap`; `ContextKey` equality includes combiner — ACCs with different user Subject principals produce different `ProtectionDomain` sets and therefore different `ContextKey` entries |
| `AccessController.java` | DirtyChai source | ✅ **Updated (v19):** `getContext()` iterates `Subject[]` from `SubjectAccess.SCOPED.get()`; skips `WorkerSubject`; per-UserSubject `SubjectDomainCombiner.combine()` loop accumulates all principals; `SubjectAccess.get()` returns `Subject[]` not `Subject` |
| `Subject.java` | DirtyChai source | ✅ **Updated (v19):** `sealed permits WorkerSubject, UserSubject`; `callAs(Subject, Callable)` runtime guard rejects `WorkerSubject`; `callAs(Callable, UserSubject...)` compile-time type-safe varargs; `SCOPED_SUBJECT` is `ScopedValue<Subject[]>`; `NoCheck.current()` returns `Subject[]`; `currentAll()` clones array; `processWorker()` delegates to `SpiffeCredentialManager.getInstance().getSubject()` |
| `Thread.java` | DirtyChai source | ✅ **Updated (v19):** `scopedSubject` field is now `Subject[]` (was single `Subject`); captured at construction via `SubjectAccess.SCOPED.get()`; `runWith()` passes full array to `SubjectAccess.callNoCheck(scopedSubjects, action)` |
| `WorkerSubject.java` | DirtyChai source (`javax.security.auth`) | ✅ **Reviewed (v19):** `sealed class WorkerSubject extends Subject permits SpiffeCredentialManager.SpiffeSubject`; public constructor (sealed enforcement, not constructor visibility); used only by DirtyChai bootstrap `SpiffeCredentialManager` |
| `UserSubject.java` | DirtyChai source (`javax.security.auth`) | ✅ **Reviewed (v19):** `final class UserSubject extends Subject`; public constructor; used by JERI dispatcher and application transaction code |
| `SpiffeCredentialManager.java` (JGDMS) | `net.jini.jeri.ssl` | ✅ **Reviewed (v19):** JGDMS implementation; uses standard Java APIs; constructs vanilla `Subject` with `X500Principal` + `SpiffePrincipal`; `updateSubjectCredentials()` atomically rotates principals + credentials under `synchronized (subject)`; `SpiffeSubjectHolder.set(subject)` on start; `ScheduledExecutorService` for renewal; `AutoCloseable` |
| `SpiffeCredentialManager.java` (DirtyChai) | `au.zeus.jdk.authorization.spire` | ✅ **Reviewed (v19):** JDK bootstrap implementation; constructs sealed `SpiffeSubject extends WorkerSubject`; bootstrap-safe (no lambdas, `sun.security.util.Debug`); module-private `SpiffeSubject` constructor; `Subject.processWorker()` delegates here |
| `LocalWorkerSubject.java` → `WorkerSubject.java` | DirtyChai source | ✅ **Renamed (v19):** Class renamed from `LocalWorkerSubject` to `WorkerSubject` throughout |
| `DigestCodeSource.java` | DirtyChai source | ✅ **New (v20):** `CodeSource` subclass; carries `byte[] digest` + `String digestAlgorithm`; `implies()` delegates hash comparison to `DigestGrant`; used by `SecureClassLoader` for content-verified JARs |
| `DigestGrant.java` | DirtyChai source (`org.apache.river.api.security`) | ✅ **New (v20):** Extends `URIGrant`; `implies(CodeSource, Principal[])` checks URI then digest match; `implies(ClassLoader, Principal[])` returns `false` unconditionally (no content hash from ClassLoader); `toString()` prepends `digest "alg:hex",\n` |
| `PermissionGrantBuilder.java` | DirtyChai source | ✅ **Updated (v20):** New `DIGEST` int constant; new `digest(String algorithm, byte[] digest)` method |
| `PermissionGrantBuilderImp.java` | DirtyChai source | ✅ **Updated (v20):** Implements `digest()` builder method; `DIGEST` case in `build()` constructs `DigestGrant` |
| `DefaultPolicyScanner.java` | DirtyChai source | ✅ **Updated (v20):** `GrantEntry` has new `String digest` field + `getDigest()` + updated constructor + `toString()` includes digest; `readGrantEntry()` recognises `"digest"` keyword |
| `DefaultPolicyParser.java` | DirtyChai source | ✅ **Updated (v20):** `resolveGrant()` reads `ge.getDigest()`, hex-decodes, selects `DIGEST` vs `URI` context; new `hexDecode()` helper; missing methods `getURI()`, `segment()`, `expandURLs()`, `resolvePermission()`, `PermissionExpander`, `resolveSigners()` restored |
| `SecurityPolicyWriter.java` | DirtyChai source | ✅ **Updated (v20):** Emits `digest "alg:hex",\n` clause for `DigestCodeSource`; new `hexEncode(byte[])` helper; hasDigest \|\| hasPrincipals controls comma placement after codebase |

---

## 2. Full Architecture Summary

### 2.1 The Five-Host SCAP Pipeline (JGDMS-STD-002)

```
Host 1 — Jini Lookup Service       (passive registry; stores opaque marshalled items)
Host 2 — BAE Pool                  (SELinux-isolated; stateless; analyses bytecode)
Host 3 — Verdict Registry          (holds client-trusted signing key; no bytecode parsing)
Host 4 — Codebase Downloader       (proactive; only host with outbound internet)
Host 5 — JFR Telemetry Service     (reactive; receives VirtualThreadPinned events)
```

**Key isolation invariants:**
- Host 2 → Host 3: NO direct connection. Compromised BAE cannot write verdicts directly.
- Host 4 ↔ Host 5: NO connection. JFR flood cannot DoS the proactive pipeline.
- Clients never interact with Host 2 or Host 4.

**Before any proxy is unmarshalled:** `ProxyCodebaseSpi` hashes the JAR and queries
Host 3 for a `RegistryVerdict`. A single `DANGEROUS` verdict from any BAE instance
immediately condemns the codebase. A quorum of `SAFE` verdicts is required to proceed.

**BAE analyses per JAR:**
- `ClinitBlockingVisitor` — BFS from `<clinit>` to blocking sinks; produces `BLOCKING`, `BLOCKING_GUARDED`, `BLOCKING_DECLARED`, `NATIVE_OPACITY`, `CLEAN`, or `CYCLE`
- `AtomicSerialComplianceVisitor` — @AtomicSerial protocol adherence
- Cycle detector — circular `<clinit>` dependencies
- `JarAnalyzer` post-processing — upgrades `BLOCKING_GUARDED` → `BLOCKING_DECLARED` (`DANGEROUS`) when the guarding permission class is declared in `PERMISSIONS.LIST`
- `JarAnalyzer` reads `META-INF/PERMISSIONS.LIST` — non-blank, non-comment lines stored in `JarAnalysisReport.getDeclaredPermissions()` and included in the engine signature

**`SINK_TO_PERMISSION_CLASS` and the `className#action` syntax:** The upgrade from
`BLOCKING_GUARDED` to `BLOCKING_DECLARED` is driven by
`BlockingSinkRegistry.SINK_TO_PERMISSION_CLASS`, which maps each blocking sink to the
permission class (or `className#action`-qualified permission) that guards it. The
`#action` suffix restricts matching to a specific permission action, preventing false
positives for broad permission classes.

### 2.2 @AtomicSerial Compliance (JGDMS-STD-001)

Every `Serializable` class crossing a JERI wire must comply:
- RULE-1: Annotated `@AtomicSerial`
- RULE-2: Public `(GetArg)` constructor
- RULE-3: Static check method called **before** any field is set
- RULE-4: All Object-returning `GetArg.get()` calls must be type-checked
- RULE-5: `serialForm()` + `serialPersistentFields` declared for any class with instance fields
- RULE-6: `serialize(PutArg, T)` static method

Violations yield `DANGEROUS` verdict → codebase refused by clients.

**Why this matters for policy:** `PermissionGrant` implementations in JGDMS use
`@AtomicSerial`. DirtyChai's grant implementations do not and cannot. This is why
the `RemotePolicyService` wire format uses `String[]` (policy file syntax) not
`PermissionGrant[]`.

### 2.3 The Three-Layer Policy Stack

```
DynamicPolicyProvider          (outermost — per-proxy, GC-scoped grants)
    └── RemotePolicyProvider   (djinn-wide grants from InMemoryPolicyService)
            └── SpiffePolicyFile       (bootstrap grants from HTTPS server)
                    └── (minimal local grant: SPIRE socket only)
```

Each layer implements `ScalableNestedPolicy`. `getPermissionGrants(ProtectionDomain)`
flows efficiently up through the stack without redundant reconstruction.

| Layer | Grant issuer | Grant lifetime | Revocation mechanism |
|---|---|---|---|
| `SpiffePolicyFile` | Bootstrap HTTPS server (operator) | JVM lifetime | `refresh()` on SVID rotation |
| `RemotePolicyProvider` | `InMemoryPolicyService` via administrator | Djinn session | `replace()` via `RemoteEvent` |
| `DynamicPolicyProvider` | Any caller holding `GrantPermission` | Proxy reachability | Automatic on GC |

### 2.4 Service & Proxy Lifecycle (Abbreviated)

**Service start:** `ServiceStarter` → `NonActivatableServiceDescriptor.create()` →
`Startable.start()` → `serverExporter.export()` → `JoinManager` registers in lookup services.

**Service discovery (client side):**
1. `ServiceDiscoveryManager` discovers lookup service → `registrarPreparer.prepareProxy()`
2. `RegisterListenerTask` → `proxy.notify()` + initial `cache.lookup()` snapshot
3. Bootstrap proxies prepared via `bootstrapProxyPreparer.prepareProxy()` — authentication happens here
4. `ServiceItemFilter.check()` → `getServiceProxy()` downloads full smart proxy
5. `ProxyCodebaseSpi.resolve()` provisions ClassLoader, verifies JAR integrity, unmarshals typed proxy
6. Constraints merged; proxy stored as `filteredItem`

**ProxyCodebaseSpi ClassLoader resolution priority:**
1. Boomerang → use export ClassLoader
2. Self-unmarshal → use parent ClassLoader
3. Previously seen (CACHE hit) → use cached ClassLoader
4. New → verify JAR, create `PreferredClassLoader`

### 2.5 ClassLoader Identity — Critical Correction

**`AdvisoryDynamicPermissions` is implemented by the ClassLoader, not the proxy class.**
The ClassLoader cache key in `PreferredProxyCodebaseProvider` is:

```java
Key = (InvocationHandler, List<Uri> codebase, ClassLoader parent)
```

The `InvocationHandler` encodes the authenticated remote endpoint identity. Therefore:
- Two instances of the same service JAR on **different hosts** → different ClassLoaders → independent ProtectionDomains → independent dynamic grants.
- `putIfAbsent` in `record()` throws `ExportException` if an endpoint tries to export twice.

**GC chain:** proxy abandoned → ClassLoader weakly reachable (CACHE uses `Ref.WEAK` +
60s TTL) → ProtectionDomain weakly reachable → `DynamicPolicyProvider` grant becomes
void → swept by single background daemon thread.

---

## 3. DynamicPolicyProvider — Confirmed Decisions

### 3.1 No ReferenceQueue, No clearCache()

The ACC cache (`ContextCache`) is weakly-valued. When a proxy is abandoned, the ACC
cache entry clears before or concurrent with the PD weak ref being enqueued.
`clearCache()` would be a no-op.

### 3.2 Void Eviction — Single Background Sweeper

A single daemon thread named `JGDMS-DynamicPolicyProvider-VoidGrantSweeper` wakes
every 60 seconds (configurable) and iterates `dynamicPolicyGrants` once, calling
`it.remove()` on any grant for which `isVoid()` returns true.

- **Hot path remains purely read-only** — no writes, no bin-lock acquisition.
- **Period configurable** via `net.jini.security.policy.DynamicPolicyProvider.voidGrantSweepPeriodSeconds`. Default 60 s.
- **`refresh()` one-shot eviction retained** for administrator-triggered cleanup.

---

## 4. RemotePolicyService Wire Interface

The interface is **fully implemented** in `jgdms-platform/.../RemotePolicyService.java`:

```java
public interface RemotePolicyService extends Remote {
    void replace(String[] grants) throws RemoteException;
    String[] getCurrentGrants() throws RemoteException;
    EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                               MarshalledInstance handback,
                                               long duration) throws IOException;
    long renewPolicyLease(Uuid leaseId, long duration)
            throws UnknownLeaseException, RemoteException;
    void cancelPolicyLease(Uuid leaseId)
            throws UnknownLeaseException, RemoteException;
}
```

**Server-side parsing:** Each `String` element is fed directly into `DefaultPolicyScanner.scanStream()`
via a `StringReader` wrapped in an `InputStream` adaptor — bypasses URL machinery.
`DefaultPolicyParser.scanner` is already `protected final` in the current codebase — no visibility change required.

**Validation is always server-side** after parsing.

---

## 5. GrantPermission & Role Management — Core Design

### 5.1 The Three-Way Intersection

```
What the proxy's ClassLoader declares       (AdvisoryDynamicPermissions.getPermissions())
        ∩
What the client is authorised to give       (GrantPermission entries in RemotePolicyProvider)
        ∩
What the proxy's SPIFFE principal is        (principal scoping in DynamicPolicyProvider)
        =
Effective dynamic grant, scoped to this specific authenticated endpoint instance
```

No single party controls the outcome unilaterally.

### 5.2 VerifyingProxyPreparer — The Grant Site

**Explicit permissions path:** `Security.grant(proxy.getClass(), permissions)` → `UnsupportedOperationException` rethrown as `SecurityException` (hard failure).

**Advisory permissions path:** `((AdvisoryDynamicPermissions) ldr).getPermissions()` → `Security.grant(klass, perms)` → `UnsupportedOperationException` logged only (soft failure).

### 5.3 GrantPermission Self-Limiting Property

A caller can only grant permissions it itself holds a `GrantPermission` for.

### 5.4 Administrator Role Levers

| Lever | Effect |
|---|---|
| `GrantPermission` entries in `RemotePolicyProvider` | Ceiling on what `Security.grant()` will apply |
| `VerifyingProxyPreparer` constructor choice | Explicit permissions lock grant; null/empty defers to `PERMISSIONS.LIST` |
| `principals` parameter in `VerifyingProxyPreparer` | null = current user Subject (via `Subject.current()`) or ACC Subject as fallback; non-null = explicit scope |

### 5.4.1 `Security.getCurrentPrincipals()` — Union of User + Worker Principals

When `principals == null` in `VerifyingProxyPreparer`, `Security.grant(Class, Permission[])` is
called, which invokes the private `Security.getCurrentPrincipals()` helper.  As of this release,
that helper returns the **union** of principals from both active Subjects:

1. **`Subject.current()`** — the user `Subject` bound via `Subject.callAs()` (a `ScopedValue`).
   Non-null when the grant occurs from inside a JERI dispatch thread or any other `callAs` scope.
2. **`Subject.getSubject(AccessController.getContext())`** — the workload (SPIFFE) `Subject`
   on the `AccessControlContext`.

| Subjects present | Principals returned |
|---|---|
| User only | User principals only |
| Worker only | Worker principals only |
| Both | **Union** of user + worker principals |
| Neither | `null` (grant applies to any principal) |

#### Security foundation — why the union is mandatory

1. **`SpiffePolicyFile` cannot pre-assign permissions to unknown proxy classes.**  Bootstrap policy
   knows which workload principals are expected at startup, but it does not know which proxy
   `ClassLoader` instances will be created later at runtime.  Dynamic grants via
   `Security.grant()` at proxy-preparation time are therefore the only mechanism that can scope a
   permission to a specific proxy `ProtectionDomain` / `ClassLoader`.

2. **Policy files can only relax permissions; they cannot deny them.**  Java security starts from a
   deny-all baseline.  There is no policy-level "deny once granted".  Restricting a grant to a
   specific identity combination requires naming all required principals in that grant, so the grant
   simply does not apply when any named principal is absent.

3. **POLP requires user + workload + code simultaneously.**  If a dynamic grant names only user
   principals (user-first / worker-fallback), then a grant intended for "alice using
   order-processor" can also apply to "alice using some other process".  With the union, grants are
   constrained across all three axes at once: authenticated user principal, authenticated worker
   SPIFFE principal, and the proxy code's `ProtectionDomain` / `ClassLoader`.

**Impersonation prevention:** If a worker process impersonates a legitimate service (for example,
running outside the hardened SELinux environment and presenting a different SPIFFE SVID), the
union-scoped dynamic grant does not match.  Without the union, a user-only dynamic grant would
still apply regardless of which process is acting for that user.

**Single-Subject fallback:** On daemon threads or pre-login contexts where only one Subject is
present, behaviour is identical to the previous single-subject case.

**Previous behaviour:** Earlier versions preferred the user Subject and fell back to the worker
Subject (either/or, not union).  The union approach tightens this by requiring both.

**Why no explicit `Subject.doAs` wrapper?** `AccessController.getContext()` now reads
`SCOPED_SUBJECT` directly (without an `AuthPermission` guard — trusted `java.base`
infrastructure via `Subject.NoCheck` / `AccessController.SubjectAccess`) and directly
bakes the user Subject's principals into the returned `AccessControlContext`'s
`ProtectionDomain` array via `SubjectDomainCombiner.combine()`.  The original combiner
is preserved.  The immediate `privilegedContext` domains are also enriched.

Consequence: when `sm.checkPermission(this)` is called, the internal
`AccessController.getContext()` call automatically incorporates the user Subject's
principals into the domain array.  Policy grants scoped to user principals (e.g. a
grant requiring both a SPIFFE workload principal **and** a `JwtPrincipal`) are
therefore honoured transparently, whether the check occurs on the dispatch thread or
on a spawned thread that inherited the enriched ACC.

The explicit `Subject.doAs(user, …)` workaround is not required and has not been used
since v12.

### 5.5 Natural Role Structure

| Layer | Role surface | Who controls |
|---|---|---|
| `SpiffePolicyFile` | Structural grants: SPIRE socket, reach `InMemoryPolicyService` | Operator |
| `RemotePolicyProvider` | Category grants; `GrantPermission` delegation ceiling | Administrator via `InMemoryPolicyService.replace()` |
| `DynamicPolicyProvider` | Per-proxy instance grants | Client code via `VerifyingProxyPreparer` |

### 5.6 Risk Categorisation for GrantPermission

| Category | Examples | Recommendation |
|---|---|---|
| Safe to delegate | `FilePermission` to specific data directories | Advisory path appropriate |
| Requires care | `createVirtualThread` | Include only for specific SPIFFE identities |
| **DoS risk if declared in `PERMISSIONS.LIST`** | `SocketPermission`, `NativeInvocationPermission`, `RuntimePermission("createVirtualThread")` | BAE will flag as `DANGEROUS` |
| Never delegate | `PolicyPermission("Remote")`, `GrantPermission` itself, `AllPermission` | Must not appear in delegatable grants |

---

## 6. DirtyChai Security Model

### 6.1 Guard Inventory (relevant to policy design)

| Permission | Guard site |
|---|---|
| `LoadClassPermission` | `SecureClassLoader.defineClass()` |
| `SerialObjectPermission` | `ObjectInputStream.readOrdinaryObject()` |
| `NativeInvocationPermission` | `NativeLibraries`, `SymbolLookup` |
| `NativeMemoryPermission` | All four `Arena` factories |
| `DefineClassPermission` | `MethodHandles.Lookup.defineClass()` |
| `RuntimePermission("createPlatformThread")` | `ThreadBuilders` + all `Thread` constructors |
| `RuntimePermission("createVirtualThread")` | `ThreadBuilders` |

### 6.2 Virtual Thread Blocking in `<clinit>` — Process Isolation Boundary

**JDK 21–23:** Carrier pinned when virtual thread blocked inside `synchronized`. Exhausting carriers with blocked class-loads was a realistic DoS.

**JDK 24+ (JEP 491):** Carrier pinning from `synchronized` eliminated. However, a blocking `<clinit>` still holds the class-loading lock — class-loading starvation risk remains.

**Layered defence:** Prevention (policy) → Containment (bounded ForkJoinPool with deadline) → Acceptance (accept residual risk with monitoring).

### 6.3 CombinerSecurityManager — Recursion Depth

Recursion guard limit: 7. Three-layer policy stack uses 3. Headroom of 4.

### 6.4 Subject API — Three Identity Layers

JGDMS formalises three distinct identity layers, each with its own carrier and lifetime.
See **JGDMS-STD-003 v3.1 §3.1** for the normative table.

| Layer | Type | Carrier | Survives `doPrivileged` | Lifetime | Established by |
|---|---|---|---|---|---|
| Process worker | `WorkerSubject` (sealed, `SpiffeSubject` only) | Baked into `ProtectionDomain` at class load time by `SecureClassLoader` | **Yes** — in every domain | JVM lifetime | DirtyChai `SpiffeCredentialManager` only |
| Remote process | `WorkerSubject` principals in remote PDs | Serialized ACC domains over JERI | **No** — not in `privilegedContext` | Per-connection | JERI dispatcher (receiving side) |
| User | `UserSubject` (final, public) | `SCOPED_SUBJECT ScopedValue<Subject[]>` | **Yes** — injected into `privilegedContext` | Per-request / transaction | JERI dispatcher / application |
| Local user (legacy) | `Subject` (vanilla) | `SCOPED_SUBJECT ScopedValue<Subject[]>` or ACC | **Yes** (ScopedValue path) | Per-session | JAAS `LoginContext` |

**`doPrivileged` boundary rule:**
```
Survives doPrivileged:  who you are  (WorkerSubject + UserSubject)
Shed at doPrivileged:   where you came from (remote WorkerSubject domains)
```

**How each layer reaches permission checks:**

1. **Process worker (`WorkerSubject`)** — baked into every `ProtectionDomain` by
   `SecureClassLoader` at class load time. Always present at every `checkPermission`
   regardless of `doPrivileged` nesting. Never passed to `callAs` or `doAs`.
   Provided by the **DirtyChai** `SpiffeCredentialManager` as a sealed `SpiffeSubject`.

2. **Remote process identity** — not a separate Subject subtype. Travels as
   `WorkerSubject` principals in `ProtectionDomain`s inside a serialized
   `AccessControlContext` over JERI. A `DomainCombiner` on the receiving JVM strips
   unverifiable domains before the ACC is placed on the call stack. Shed at
   `doPrivileged` boundaries on the receiving JVM.

3. **User (`UserSubject`)** — bound via `callAs` → `AccessController.getContext()` reads
   `SCOPED_SUBJECT` (`Subject[]`) and iterates over all non-`WorkerSubject` entries,
   baking each subject's principals directly into the `ProtectionDomain` array
   (and into the immediate `privilegedContext`) via `SubjectDomainCombiner.combine()` →
   survives `doPrivileged` boundaries. Multiple users may be present simultaneously
   in a transaction context.

**Two `SpiffeCredentialManager` implementations:**

| Implementation | Package | Subject type constructed | Purpose |
|---|---|---|---|
| JGDMS `SpiffeCredentialManager` | `net.jini.jeri.ssl` | Vanilla `Subject` with `X500Principal` + `SpiffePrincipal` | TLS credential management for JERI transport; standard Java APIs; `AutoCloseable` |
| DirtyChai `SpiffeCredentialManager` | `au.zeus.jdk.authorization.spire` | `SpiffeSubject extends WorkerSubject` (sealed) | JDK bootstrap; baked into `ProtectionDomain`s by `SecureClassLoader`; `sun.security.util.Debug`; bootstrap-safe |

`Subject.processWorker()` bridges both via `SpiffeSubjectHolder`:
```java
public Subject processWorker() {
    // AuthPermission("getSubject") check
    return SpiffeCredentialManager.getInstance().getSubject();
}
```

**`Subject.current()` in spawned threads:**

`Thread` captures `SCOPED_SUBJECT` at construction time into a `Subject[] scopedSubjects`
field (guarded by `VM.isBooted()`). `Thread.runWith()` (`final`, platform and virtual)
re-establishes this as a `callAs` scope via `SubjectAccess.callNoCheck(scopedSubjects, action)`
before invoking the task. The full `Subject[]` array is re-established — all transaction
participants are available via `Subject.currentAll()` in spawned threads.

**Public API surface (sealed hierarchy):**

| Method | Guard | Effect | Routing rule |
|---|---|---|---|
| `Subject.doAs(Subject, PrivilegedAction)` | `AuthPermission("doAs")` | Vanilla `Subject` onto ACC via `SubjectDomainCombiner`; privileged boundary | Vanilla `Subject` and `null` only; **`UserSubject` → `callAs`**; **`WorkerSubject` → `IllegalArgumentException`** |
| `Subject.callAs(Subject, Callable)` | `AuthPermission("doAs")` | OpenJDK-compatible; binds single Subject to `SCOPED_SUBJECT` as `Subject[1]`; runtime guard rejects `WorkerSubject` | `WorkerSubject` rejected at runtime; `UserSubject` and vanilla `Subject` accepted |
| `Subject.callAs(Callable, UserSubject...)` | `AuthPermission("doAs")` | **Preferred multi-user path**; binds `UserSubject[]` to `SCOPED_SUBJECT`; compile-time exclusion of `WorkerSubject` | Type system enforces correctness; supports multi-party transactions |
| `Subject.current()` | `AuthPermission("getSubject")` | Returns `SCOPED_SUBJECT[0]`; `null` if empty | Never returns `WorkerSubject` |
| `Subject.currentAll()` | `AuthPermission("getSubject")` | Returns defensive copy of full `SCOPED_SUBJECT Subject[]` | Never includes `WorkerSubject` |
| `Subject.processWorker()` | `AuthPermission("getSubject")` | Returns current `WorkerSubject` from `SpiffeCredentialManager` | Bridges DirtyChai and JGDMS implementations via `SpiffeSubjectHolder` |
| `Subject.getSubject(AccessControlContext)` | `AuthPermission("getSubject")` | Retrieves Subject from ACC combiner (legacy path) | Workload Subject from ACC |

### 6.5 Thread Propagation — Divergence from OpenJDK

In OpenJDK, `Subject.callAs` user identity does **not** propagate to threads started
with `new Thread(...).start()` outside a `StructuredTaskScope`. In DirtyChai:

`Thread` captures `Subject[] scopedSubjects = SubjectAccess.SCOPED.get()` at construction
(guarded by `VM.isBooted()`). `Thread.runWith()` calls
`SubjectAccess.callNoCheck(scopedSubjects, action)` — the full array is re-established,
preserving all transaction participants for `Subject.currentAll()` in spawned threads.

Both mechanisms are active for all spawned threads (platform and virtual), regardless of
whether `StructuredTaskScope` is used. This is intentional: in JGDMS, code that spawns
threads during request processing should naturally inherit the authenticated user's
identity without requiring structured concurrency.

**Executor-submitted tasks still do not propagate** — consistent with OpenJDK and with
the `callAs` javadoc.

---

## 7. VerifyingProxyPreparer — Constructor Detail

**Two-argument** `(Object[] contextElements, Permission[] permissions)` — most common. Always `SET_CONSTRAINTS` mode. Pass `null` for advisory path.

**Four-argument** `(ClassLoader loader, Object[] contextElements, Principal[] principals, Permission[] permissions)` — explicit ClassLoader and fixed Principal[].

**Five-argument** `(boolean addProxyConstraints, ...)` — `ADD_CONSTRAINTS` or `AS_IS`; only constructor accepting `null` contextElements.

**Failure asymmetry:**
- Explicit path failure → hard `SecurityException`
- Advisory path failure → logged and swallowed

---

## 8. Authentication Model

### 8.1 SPIFFE/SPIRE — Workload Identity

JGDMS has no username/password login. Workload authentication is entirely
**SPIFFE/SPIRE X.509 SVIDs** provisioned by SPIRE at workload startup.

`SpiffeCredentialManager` manages the current Subject and calls `SpiffePolicyFile.refresh()` on SVID rotation (~1 hour lifetime). Background watcher detects rotation via streaming gRPC; exponential backoff reconnects after transient failures. On an empty SVID response the existing valid Subject is retained (not overwritten) — transient SPIRE outages cannot destroy unexpired credentials.

### 8.2 Human User Identity — JWT/OIDC (Kerberos is Legacy)

Human users authenticate via **JWT/OIDC** using `JwtLoginModule` from the
`jgdms-security-jwt` module.  The resulting `UserSubject` carries `JwtPrincipal`
instances (e.g. `"sub:alice@example.org"`, `"group:admins"`) and is installed
per-request via `Subject.callAs(jwtUserSubject, () -> ...)`.
`SubjectDomainCombiner` reads `SCOPED_SUBJECT` internally and injects JWT principals
into every `checkPermission` for the duration of the request.

**Kerberos** (`KerberosPrincipal` / `KerberosEndpoint`) is retained for **legacy
support only**.  New deployments must use JWT/OIDC.  The policy grant pattern is:

```
// JWT/OIDC — preferred
grant principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org" {
    permission net.jini.security.AccessPermission "*";
};

// Kerberos — legacy only
grant principal javax.security.auth.kerberos.KerberosPrincipal "alice@REALM.EXAMPLE.ORG" {
    permission net.jini.security.AccessPermission "*";
};
```

### 8.3 Traditional Jini LoginContext

`AbstractJiniService` supports traditional Jini pattern: if a `LoginContext` is
configured, `start()` calls `loginContext.login()` and runs `doStart()` inside
`Subject.callAs(loginSubject, callable)`. For SPIFFE-authenticated services,
`loginContext` is `null`.

### 8.4 DirtyChai Administrator

DirtyChai runs as a workload with `spiffe://.../admin/policy` SVID. Human authenticates to the machine via OS/SSH. DirtyChai's SPIFFE identity gates access to `InMemoryPolicyService.replace()`.

---

## 9. ServiceUI — Design Now Resolved

ServiceUI runs inside `Subject.callAs(jwtUserSubject, () -> ...)`.  The SPIFFE workload
identity (`WorkerSubject`) is **ambient** — it is already baked into every
`ProtectionDomain` by `SecureClassLoader` at class load time and does not require any
`Subject.doAs(spiffeSubject, ...)` wrapper.  Policy can therefore condition grants on the
workload identity (which process), codebase (which ServiceUI JAR), and the human identity
(which user) simultaneously.

**`doAs(spiffeSubject, ...)` is NOT used here.**  `WorkerSubject` must be rejected by
`doAs` (see §6.4).  The workload principal is in every `ProtectionDomain` regardless of
`doPrivileged` nesting.

**Clarifying note — checks vs dynamic grant scoping:** `SubjectDomainCombiner.combine()` is the
additive merge used for `checkPermission` calls (static policy grants), while
`Security.getCurrentPrincipals()` returns the union used for `Security.grant()` calls (dynamic
grants during proxy preparation).  Both paths use additive/union semantics, so static policy checks
and dynamic grant scoping stay aligned.

ServiceUI JAR is a separate codebase with independent BAE audit, `RegistryVerdict`,
`ProxyCodebaseSpi` gate, and ClassLoader.

---

## 10. Three-Layer JERI Implementation

### 10.1 The Three Identity Layers

JGDMS JERI dispatch handles three identity layers as specified in **JGDMS-STD-003 v3 §3**:

1. **Process worker identity** (`WorkerSubject`) — ambient; baked into every
   `ProtectionDomain` at class load time.  Never reinstalled per-request.
2. **Remote process identity** — `WorkerSubject` principals travelling in serialized ACC
   `ProtectionDomain`s over JERI.  Verified by a `DomainCombiner` on the receiving JVM.
   Shed at `doPrivileged`.
3. **User identity** (`UserSubject` or vanilla `Subject`) — carried in `SCOPED_SUBJECT`;
   injected into stack domains and `privilegedContext`; survives `doPrivileged`.

### 10.2 Remote ACC Serialization and `DomainCombiner` Retention

Remote process identity travels as `ProtectionDomain`s in a serialized
`AccessControlContext`.  `AccessControlContext` is serialized using `@AtomicSerial`.
The serialized form contains `ProtectionDomain[]` with `httpmd:` codebase URIs (SHA-256
verifiable) and `WorkerSubject` principals baked in at class load time on the remote JVM.

A `DomainCombiner` on the receiving JVM:
- Strips any domain whose `httpmd:` SHA-256 does not verify
- Strips any domain whose `SpiffePrincipal` is outside the trusted SPIFFE trust domain
- Verified domains participate in `RemotePolicy` checks as call stack domains

`DomainCombiner` is retained as a **Java API compatibility layer** specifically for this
receiving-side verification role.  `SubjectDomainCombiner` is deprecated.
`CombinerSecurityManager` refactoring is deferred.

**`RemotePolicy` grant example (remote code + remote process):**
```
grant codeBase "httpmd://repo.example.org/client-stub.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../svc/trusted-client" {
    permission OrderPermission "submit";
};
```
### 10.3 Invocation Layer Handling and Dispatch
#### 10.3.1 Client Side — `BasicInvocationHandler`

**`getUserPrincipals()` (private static):**
```java
private static Set<Principal> getUserPrincipals() {
    Subject subject = Subject.current();  // reads ScopedValue set by callAs
    if (subject == null) return Collections.emptySet();
    return Collections.unmodifiableSet(new HashSet<>(subject.getPrincipals()));
}
```
- Reads `Subject.current()` directly (not via reflection) — requires `--release 21`.
- The worker Subject's principals are **not** included here; they reach the server
  through the TLS handshake.

**Wire protocol version selection:**
```
if (!userPrincipals.isEmpty())  → write 0x02 + integrity + atomicValidation + user-principal block
else if (atomicValidation)      → write 0x01 + integrity + atomicValidation
else                            → write 0x00 + integrity   (legacy compatibility)
```

**`writeUserPrincipals()` (package-private static) — wire encoding:**
```
principalCount        : u16 big-endian  (max 65535, capped to actual size)
for each principal:
  classNameLength     : u16 big-endian
  classNameBytes      : UTF-8
  nameLength          : u16 big-endian
  nameBytes           : UTF-8
```
Each principal serialises to `(p.getClass().getName(), p.getName())`.

#### 10.3.2 Server Side — `BasicInvocationDispatcher`

**Security limits (constants):**
- `MAX_USER_PRINCIPALS = 64` — rejects requests claiming more principals.
- `MAX_STRING_BYTES = 8192` — rejects any single class-name or principal-name field
  exceeding this byte length.

**`readUserPrincipals()` — wire parsing:**
- Reads count; throws `IOException` if `> MAX_USER_PRINCIPALS`.
- For each principal: reads `className` + `name` (both length-prefixed, bounded by
  `MAX_STRING_BYTES`).
- Calls `instantiatePrincipal(className, name)`.
- Collects into `LinkedHashSet` (insertion order preserved).

**`instantiatePrincipal()` — classloading restriction:**
- Step 1: `Class.forName(className, false, null)` — bootstrap classloader only.
- Step 2 (if `ClassNotFoundException`): `Class.forName(className, false, ClassLoader.getSystemClassLoader())`.
- Class must be assignable to `Principal` and have a public `(String)` constructor.
- **On any failure:** returns `RemotePrincipal(className, name)` placeholder — unknown
  principal class names from the wire **never** cause arbitrary code to be loaded.

**`RemotePrincipal` (static final inner class):**
```java
static final class RemotePrincipal implements Principal {
    final String className;
    final String principalName;
    // getName() returns principalName
    // toString() returns "RemotePrincipal[className:principalName]"
}
```
Preserves the wire data for logging / auditing without loading untrusted code.

**`addUserSubjectToContext()` — context injection:**
```java
Subject userSubject = new Subject(
    true,              // read-only
    userPrincipals,    // from readUserPrincipals()
    emptySet(),        // no public credentials
    emptySet());       // no private credentials
context.add(new UserSubjectImpl(userSubject));
```
The user Subject is read-only, contains no credentials, and is kept completely separate
from the worker Subject in the existing `ClientSubject` context element.

**`invokeWithClientSubject()` — dispatch nesting:**
```
workerSubject  = ClientSubject context element  (TLS-verified)
userSubject    = ClientUserSubject context element  (wire-asserted)

case: both present
    Subject.doAs(workerSubject, () -> {           // ACC; virtual threads inherit
        Subject.callAs(userSubject, () -> {        // ScopedValue; dispatch thread only
            invoke(impl, method, args, context)
        });
    });

case: worker only
    Subject.doAs(workerSubject, () -> invoke(...));

case: user only
    Subject.callAs(userSubject, () -> invoke(...));

case: neither
    invoke(impl, method, args, context);
```
Throwable and return value are captured in `Object[1]` / `Throwable[1]` holders to
escape the lambda boundary; the original `Throwable` is re-thrown unchanged.

**Why `doAs` for the worker, not `callAs`:**
`Subject.doAs` installs the Subject into the `AccessControlContext` via
`SubjectDomainCombiner`.  Virtual threads spawned during `invoke()` inherit the ACC and
therefore observe the server's worker identity.  `Subject.callAs` (ScopedValue-based)
does NOT propagate to new threads.

### 10.4 ServerContext API — Retrieving Both Subjects

From within a JERI service method (server side):

```java
// Worker Subject — TLS-verified SPIFFE workload identity
ClientSubject cs = (ClientSubject)
    ServerContext.getServerContextElement(ClientSubject.class);
Subject workerSubject = cs != null ? cs.getClientSubject() : null;

// User Subject — wire-asserted human identity (v0x02 only, may be null)
ClientUserSubject cus = (ClientUserSubject)
    ServerContext.getServerContextElement(ClientUserSubject.class);
Subject userSubject = cus != null ? cus.getUserSubject() : null;
```

### 10.5 `ClientUserSubject` Interface

**Package:** `net.jini.io.context`  **Access:** public  **Since:** 3.1

```java
public interface ClientUserSubject {
    /** Returns the user Subject (read-only, no credentials), or null. */
    Subject getUserSubject();
}
```

The implementing class `UserSubjectImpl` is a private static inner class of
`BasicInvocationDispatcher` — not part of the public API.

### 10.6 `MutableClientSubject` Status

`MutableClientSubject` (extends `ClientSubject`) is **`@Deprecated`** and its
`mergeUserPrincipals(Set)` method is **no longer called** by the dispatcher.
`Util.ClientSubjectImpl` now implements `ClientSubject` directly (subject field final,
no `mergeUserPrincipals`).  Kept for source compatibility only.

### 10.7 `AccessController.getContext()` — Scoped User Subject Injection

The mechanism by which the scoped user Subject participates in permission checks:

```java
// In AccessController.getContext() — after acc.optimize():
Subject subject = SubjectAccess.SCOPED.get(); // reads SCOPED_SUBJECT via NoCheck trust chain
if (subject != null && subject.isReadOnly()) {
    DomainCombiner existing = acc.getCombiner();
    SubjectDomainCombiner sdc = new SubjectDomainCombiner(subject); // Scoped subclass
    // Bake user Subject principals directly into domain array
    ProtectionDomain[] combined = sdc.combine(acc.getContext(), acc.getContext());
    // Also enrich privilegedContext so principals survive doPrivileged boundaries
    AccessControlContext privileged = acc.privilegedContext;
    if (privileged != null) {
        ProtectionDomain[] combinedPrivileged =
            sdc.combine(privileged.getContext(), privileged.getContext());
        privileged = AccessControlContext.create(
            combinedPrivileged, privileged.privilegedContext,
            privileged.getCombiner(), privileged.isPrivileged());
    }
    // Restore original combiner; preserve isPrivileged and privilegedContext.
    // Four arguments:
    //   combined          — enriched ProtectionDomain[]: stack domains with user
    //                       Subject principals baked in
    //   privileged        — enriched or original privilegedContext: immediate
    //                       doPrivileged boundary preserved
    //   existing          — original DomainCombiner: workload SubjectDomainCombiner
    //                       or DelegateDomainCombiner restored unchanged
    //   acc.isPrivileged()— isPrivileged flag preserved from optimised ACC: correctly
    //                       reflects whether we are inside a doPrivileged boundary;
    //                       setting this to false unconditionally would silently erase
    //                       the privileged boundary and is a security error
    acc = AccessControlContext.create(combined, privileged, existing, acc.isPrivileged());
}
return acc;
```

**Key properties of this approach:**

- The original combiner (workload `SubjectDomainCombiner`, `DelegateDomainCombiner`,
  or `null`) is **never displaced** — it is restored on the enriched ACC.
- User Subject principals are in the domain array itself, not in the combiner, so they
  are present regardless of which combiner fires.
- Only the **immediate** `privilegedContext` is enriched — nested `privilegedContext`
  references were established before the `callAs` scope and predate the user Subject.
- Only the immediate `privilegedContext` is checked during `checkPermission`
  (`optimize()` only consults `acc.privilegedContext`), so enriching it is both
  necessary and sufficient.
- `AccessControlContext.create(acc, combiner, true)` (the `isAuthorized=true` overload)
  is used to avoid re-entrant `checkAuthorized()` calls.
- The injection is guarded by `subject.isReadOnly()` — mutable Subjects are never
  injected (their hash and equality are unstable).
- Bootstrap safety: `SubjectAccess.SCOPED.get()` is only called when `VM.isBooted()`.

**Previous approach (v12–v15, now superseded):**
Earlier versions placed an `AccessController.Scoped` (subclass of
`SubjectDomainCombiner`) directly on the ACC as the combiner. This displaced the
original combiner, required `Thread.runWith()` to detect and strip the `Scoped`
combiner, and risked interaction with `CombinerSecurityManager`'s
`DelegateDomainCombiner`. The domain-enrichment approach eliminates all of these
concerns.

### 10.8 Structural Rules for Server Code

1. **Virtual threads and user identity:** Virtual threads spawned inside `invoke()` see
   the worker identity from the ACC (via `SubjectDomainCombiner` combiner) and the user
   identity via both enriched domains (for policy checks) and `Subject.current()` (for
   explicit identity retrieval). Both are re-established automatically.

2. **Executor tasks:** Long-lived executor-submitted tasks do NOT inherit user identity.
   Callers must explicitly capture `Subject.current()` before submission and wrap the
   task in `Subject.callAs(captured, ...)`.

3. **Daemon threads:** Long-lived daemon threads (sweeper, SPIRE watcher, log writer)
   must NOT be created from within a `callAs` scope unless user identity propagation to
   those threads is desired and correct. If they are created inside a `callAs` scope,
   they will inherit the user Subject at construction time.

4. **Trust model:** User principals are not independently TLS-verified. They are vouched
   for by the authenticated worker identity. A server may refuse requests whose worker
   SPIFFE identity is not trusted to assert user principals by requiring a specific SPIFFE
   workload principal alongside any human principal.

### 10.9 `AbstractJiniService` — SPIFFE vs. Traditional JAAS

| Path | What happens |
|---|---|
| `loginContext == null` (SPIFFE path) | `doStart()` called directly; SPIFFE Subject registered by `SpiffeCredentialManager.start()` via `SpiffeSubjectHolder` — outbound TLS calls use `SpiffeSubjectHolder` automatically (no explicit `callAs` or `doAs` needed); server-side dispatch establishes workload Subject on ACC per-request via `Subject.doAs(workerSubject, …)` in `BasicInvocationDispatcher` |
| `loginContext != null` (traditional path) | `loginContext.login()` called; `Subject.callAs(loginSubject, callable)` used to run `doStart()` |

JGDMS services use the SPIFFE path.  The traditional path is supported for legacy
Jini services.

### 10.10 Key Files (User/Worker Subject Implementation)

| File | Role |
|---|---|
| `JGDMS/jgdms-jeri/.../BasicInvocationHandler.java` | Client: `getUserPrincipals()`, `writeUserPrincipals()`, wire version selection |
| `JGDMS/jgdms-jeri/.../BasicInvocationDispatcher.java` | Server: `readUserPrincipals()`, `instantiatePrincipal()`, `addUserSubjectToContext()`, `invokeWithClientSubject()`, `RemotePrincipal`, `UserSubjectImpl` |
| `JGDMS/jgdms-platform/.../net/jini/io/context/ClientUserSubject.java` | Public interface: `getUserSubject()` |
| `JGDMS/jgdms-platform/.../net/jini/io/context/ClientSubject.java` | Public interface: `getClientSubject()` (worker Subject) |
| `JGDMS/jgdms-platform/.../net/jini/io/context/MutableClientSubject.java` | `@Deprecated`, `mergeUserPrincipals()` no longer called |
| `DirtyChai/.../SubjectDomainCombiner.java` | `getMergedPrincipals()` reads `SCOPED_SUBJECT` additively |
| `DirtyChai/.../Subject.java` | Javadoc documents two-Subject model; ClassSet uses `LinkedHashSet` |

### 10.11 SSL vs. Kerberos Endpoints — Subject Lookup Differences

JERI's SSL and Kerberos transport endpoints each locate the client Subject in a
**different order**, reflecting the different credential types each transport needs.

#### `SslEndpointImpl.getCallContext()` — TLS/SPIFFE workload identity first

```
Priority 1: Subject.getSubject(acc)        — ACC Subject, accepted only if it has
            X500Principal or SpiffePrincipal (TLS-usable identity)
Priority 2: SpiffeSubjectHolder.get()      — process-wide SPIFFE Subject registered by
            SpiffeCredentialManager.start() (used when no doAs() wraps the call)
Priority 3: Subject.current()             — ScopedValue, LAST RESORT ONLY
            accepted only if it contains X500Principal or SpiffePrincipal;
            a Kerberos-only Subject is REJECTED (cannot authenticate TLS)
```

**Rationale:** TLS requires an X.509 certificate (or SPIFFE SVID) in the Subject's
private credential set.  After DirtyChai `2d26e787`, `AccessController.getContext()`
can capture `Subject.current()` into the ACC.  Therefore `SslEndpointImpl` must filter
the ACC-derived Subject too: Kerberos-only/non-TLS user Subjects are ignored so they do
not displace workload/SPIFFE TLS identity.  `Subject.current()` remains a legacy fallback
only when it carries X.500/SPIFFE principals.

#### `KerberosEndpoint.newRequest()` — user/human identity first

```
Priority 1: Subject.current()             — user Subject from Subject.callAs() (ScopedValue)
            accepted only if it contains KerberosPrincipal
Priority 2: Subject.getSubject(acc)       — ACC Subject fallback, accepted only if it
            contains KerberosPrincipal
```

**Rationale:** Kerberos GSS-API requires that the Kerberos TGT and service ticket be
acquired on behalf of a specific human user (`KerberosPrincipal`).  The dispatch layer
installs the per-request user Subject via `Subject.callAs()` (ScopedValue), making it
visible as `Subject.current()` on the dispatch thread.  Checking it first means that
every outbound Kerberos call is automatically associated with the authenticated user,
not the server's workload identity.

**`CacheKey` is per-Subject:** The Kerberos connection cache key includes the `Subject`
reference.  Different users never share Kerberos connections.  Workload connections
(ACC Subject) form their own cache entry, isolated from all user connections.

#### Side-by-side comparison

| | SSL (`SslEndpointImpl`) | Kerberos (`KerberosEndpoint`) |
|---|---|---|
| **Credential needed** | X.509 certificate / SPIFFE SVID | Kerberos TGT (`KerberosPrincipal`) |
| **Primary Subject source** | `Subject.getSubject(acc)` (filtered to TLS principals) | `Subject.current()` (user, `callAs`) |
| **Fallback Subject source** | `SpiffeSubjectHolder` → `Subject.current()` | `Subject.getSubject(acc)` (KerberosPrincipal filter) |
| **`Subject.current()` filter** | Must have `X500Principal` or `SpiffePrincipal`; Kerberos-only → rejected | Must have `KerberosPrincipal`; X500-only → rejected |
| **Connection cache isolation** | Not per-user (workload identity is shared) | Per-Subject; different users never share a connection |
| **Why** | TLS is a machine/workload concern; human Subject has no TLS credentials | Kerberos is a per-user concern; per-user credentials must not be mixed |

#### Interaction with JERI Dispatch

When `BasicInvocationDispatcher.invokeWithClientSubject()` runs inside a server:

```
Subject.doAs(workerSubject, () -> {          // worker on ACC — SSL/TLS uses this
    Subject.callAs(userSubject, () -> {      // user on ScopedValue — Kerberos uses this
        invoke(impl, method, args, context)
    });
});
```

An outbound TLS call made from inside `invoke()` will use `workerSubject` (from the ACC),
and may also use `userSubject` (from `Subject.current()`) when `userSubject` carries an
`X500Principal` or `SpiffePrincipal` and the ACC subject is absent — `SslEndpointImpl`
checks ACC first, then `SpiffeSubjectHolder`, then `Subject.current()` (X500/SPIFFE only).
An outbound Kerberos call made from inside `invoke()` will use `userSubject` (from
`Subject.current()`), so the GSS context is established as the authenticated client user.
This means the server naturally acts on behalf of the user for Kerberos connections but
uses its own workload certificate for TLS connections — no impersonation occurs.

---

## 11. Remaining `doAs` / `doAsPrivileged` Call Sites — Migration Audit

### 11.1 Background

*(Unchanged from v15, except notes below.)*

**v16 update:** DirtyChai's `Thread.runWith()` now automatically re-establishes
`Subject.current()` for spawned threads when the parent thread was inside a `callAs`
scope at construction time. The "ScopedValues are not propagated to new threads by
default" statement in §11.1 no longer applies to `new Thread(...).start()` in DirtyChai
— it still applies to executor-submitted tasks.

**v17 update (STD-003 v3):** `Subject.doAs()` now enforces strict routing — it accepts
only vanilla `Subject` and `null`.  Any call site that passes a `WorkerSubject` must be
removed; any call site that passes a `UserSubject` must be migrated to `Subject.callAs`.
The `WorkerSubject` is ambient (baked into `ProtectionDomain`s) and must never be
passed to `doAs` or `callAs`.

### 11.2–11.6

*(Unchanged from v15, with v17 note: `doAs` is now strictly for vanilla `Subject` and
`null` only; see §11.1.)*

The correct modern pattern is:
```java
Subject userSubject = Subject.current();   // captured on the request thread
executor.submit(() -> Subject.callAs(userSubject, () -> { ... }));
```

Or using the preferred varargs overload for `UserSubject`:
```java
UserSubject userSubject = (UserSubject) Subject.current();
executor.submit(() -> Subject.callAs(() -> { ... }, userSubject));
```

### 11.2 Call Sites — Classification Table

#### JAAS Service Initialisation (`LoginContext` Subject)

| File | Line(s) | Current API | Subject source | Purpose | Status |
|---|---|---|---|---|---|
| `AbstractJiniService.start()` | 263 | `Subject.callAs` | `LoginContext.getSubject()` | Run `doStart()` under JAAS login Subject so `BasicInvocationHandler` detects it via `Subject.current()` | **✅ Correct** |
| `FiddlerImpl.initWithLogin()` | 5122 | `Subject.callAs` | `LoginContext.getSubject()` | Run service initialisation under JAAS Subject | **✅ Migrated** |
| `TxnManagerImpl` constructor | 284 | `Subject.callAs` | `LoginContext.getSubject()` | Run `TxnManagerImplInitializer` construction under JAAS Subject | **✅ Migrated** — `settleTxns` thread spawned inside initialiser; daemon thread intentionally does not inherit Subject |
| `MailboxImpl.init()` | 548 | `Subject.callAs` | `LoginContext.getSubject()` | Run `MailboxImplInit` under JAAS Subject | **✅ Migrated** |
| `NormServerBaseImpl.init()` | 1841 | `Subject.callAs` | `LoginContext.getSubject()` | Run `initAsSubject(config)` under JAAS Subject | **✅ Migrated** |
| `OutriggerServerImpl` constructor | 579 | `Subject.callAs` | `LoginContext.getSubject()` | Run `init(config, persistent, activationID)` under JAAS Subject | **✅ Migrated** |
| `RegistrarImpl` constructor | 506 | `Subject.callAs` | `LoginContext.getSubject()` | Run `new Initializer(...)` under JAAS Subject | **✅ Migrated** |
| `SharedGroupImpl.createWithLogin()` | 278 | `Subject.callAs` | `LoginContext.getSubject()` | Run service group activation under JAAS Subject | **✅ Migrated** |
| `ServiceStarter.createWithLogin()` | 238 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Start service descriptors under JAAS Subject | **Low** — starter is not a persistent service; not yet migrated |
| `DestroySharedGroup.destroyWithLogin()` | 322 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Destroy services under JAAS Subject | **Low** — destroy path; one-shot; not yet migrated |
| `Browser.main()` | 481, 1862 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Launch Browser GUI under JAAS Subject | **Low** — example application, not production service |

#### Phoenix / Activation Framework

| File | Line(s) | Current API | Subject source | Purpose | Status |
|---|---|---|---|---|---|
| `AbstractActivationGroup.doAction()` | 998 | `Subject.doAsPrivileged(..., null)` | `login.getSubject()` | Call `monitor.activeObject()` under group Subject | **Medium** — remote call from activation system; spawns an executor task |
| `AbstractActivationGroup` executor path | 913 | `Subject.doAsPrivileged(login.getSubject(), new GetThreadPoolAction(false), null)` | `login.getSubject()` | Obtain a thread pool running under group Subject | **High** — executor tasks submitted to this pool do NOT inherit ACC in virtual thread model |
| `Activation.doAsPrivileged()` | 2165 | `Subject.doAsPrivileged(..., null)` | `login.getSubject()` | Run phoenix activation actions under admin Subject | **Medium** — phoenix infrastructure |

#### Kerberos Transport — Credential Acquisition and GSS Establishment

| File | Line(s) | Current API | Subject source | Purpose | Status |
|---|---|---|---|---|---|
| `KerberosEndpoint.newRequest()` | 642–662 | `Subject.current()` → filtered `Subject.getSubject(acc)` fallback | `Subject.current()` (user, ScopedValue) then ACC (only if it has KerberosPrincipal) | Locate Kerberos Subject for outbound request; prefer per-user Subject from `callAs` scope, ignore non-Kerberos ACC Subjects | **✅ Revised for DirtyChai `2d26e787`** — prevents ACC-captured non-Kerberos user Subjects from being selected |
| `KerberosUtil.getGSSCredential()` | 493 | `Subject.doAs(subj, ...)` | explicitly passed `Subject` | Acquire Kerberos GSS credential from Subject's private credential set | **Keep as `doAs`** — GSS-API requires Subject in ACC; workload identity, not user identity |
| `KerberosServerEndpoint` connection thread | 1794 | `Subject.doAs(serverSubject, ...)` | `serverSubject` field | Establish Kerberos GSSContext during TLS handshake | **Keep as `doAs`** — GSS context establishment requires Subject in ACC; workload (server) identity |

#### SSL / TLS Transport — Subject Lookup

| File | Line(s) | Current API | Subject source | Purpose | Status |
|---|---|---|---|---|---|
| `SslEndpointImpl.getCallContext()` | 298–332 | (1) filtered `Subject.getSubject(acc)` → (2) `SpiffeSubjectHolder.get()` → (3) `Subject.current()` (X500/SPIFFE filter) | ACC subject accepted only when TLS-usable; then process SPIFFE holder; then ScopedValue last-resort | Retrieve worker Subject for outbound TLS call; reject Kerberos-only/non-TLS Subjects regardless of source | **✅ Revised for DirtyChai `2d26e787`** — avoids ACC-captured user Subject from displacing TLS identity |
| `SslServerEndpointImpl.SslListenEndpoint` | 587 | `Subject.getSubject(acc)` | ACC | Retrieve worker Subject for inbound TLS listen | **Keep** — workload identity from ACC |
| `X500Provider` | 191 | `Subject.getSubject(acc)` | ACC | Retrieve Subject for X.500 principal matching | **Keep** — workload identity |
| `TlsRMIClientSocketFactory` | 46 | `Subject.getSubject(acc)` | ACC | Retrieve Subject for TLS RMI client socket | **Keep** — workload identity |
| `TlsRMIServerSocketFactory` | 39 | `Subject.getSubject(acc)` | ACC | Retrieve Subject for TLS RMI server socket | **Keep** — workload identity |

#### DGC / Other

| File | Line(s) | Current API | Subject source | Purpose | Status |
|---|---|---|---|---|---|
| `AbstractDgcClient` | 402 | `Subject.getSubject(cont)` | ACC | Retrieve Subject for DGC lease management | **Medium** — DGC leases are renewed by background threads; if user Subject is relevant here it may not propagate |

#### JGDMS Custom Policy Helpers (`Security.*`)

| File | Line(s) | Current API | Subject source | Purpose | Status |
|---|---|---|---|---|---|
| `Security.doAs()` (two overloads) | 634, 691 | Custom `doAs` wrapper | caller-provided | JGDMS custom SubjectDomainCombiner semantics (CodeSource+Principal separation) | **Keep** — intentionally different semantics from JDK `Subject.doAs` |
| `Security.doAsPrivileged()` (two overloads) | 723, 759 | Custom `doAsPrivileged` wrapper | caller-provided | Same as above with explicit `SecurityContext` | **Keep** |

### 11.3 Thread-Crossing Analysis — Event Delivery Pattern

The classic Jini pattern for event delivery to `RemoteEventListener` is:

```java
// Old pattern (ACC-based — breaks with virtual threads / executors)
Subject.doAsPrivileged(loginContext.getSubject(), () -> {
    // background thread spawned here inherits ACC iff platform thread
    eventDispatcher.submit(() -> listener.notify(event));
    return null;
}, null);
```

With virtual threads or unbounded executor pools the spawned thread does **not**
automatically carry the ACC.  The JDK has added no transparent Subject propagation
for executors (confirmed by OpenJDK, JEP 428/429, Loom design docs).

**Correct migration pattern for event delivery:**

```java
// Capture user Subject before submitting to executor
Subject userSubject = Subject.current();   // only non-null if inside callAs scope
executor.submit(() -> {
    if (userSubject != null) {
        Subject.callAs(userSubject, () -> { listener.notify(event); return null; });
    } else {
        listener.notify(event);
    }
});
```

For the workload (SPIFFE/Kerberos) Subject, the executor task should be submitted
from within the workload's ACC context (platform threads inherit ACC; virtual threads
require capturing `AccessController.getContext()` + `AccessController.doPrivileged(..., capturedAcc)`).
**Note:** `Subject.doAs(workerSubject, ...)` is no longer used for this — the
`WorkerSubject` is ambient in every `ProtectionDomain`.

### 11.4 High-Priority Sites — Detailed Notes

**`AbstractActivationGroup` — executor acquisition (line 913):**
```java
Executor systemThreadPool = Subject.doAsPrivileged(
    login.getSubject(),
    new GetThreadPoolAction(false),  // returns a Executor
    null);
systemThreadPool.execute(action, "UnexportGroup");
```
The `doAsPrivileged` here retrieves an `Executor` while running as the Subject — but
tasks *submitted* to that executor later run under the submitter's context, not the
Subject's.  This is a silent propagation failure.  The `action` passed to `execute`
needs to re-establish the Subject context via `callAs` or `doAs`.

**`TxnManagerImpl` — `settleTxns` thread (line 291):**
An `InterruptedStatusThread` for `settleTxns()` is constructed inside the
`doAsPrivileged` action.  Whether it inherits the ACC depends on whether platform thread
creation preserves the parent's ACC (it does on JDK 11–21 for platform threads only).
On virtual threads this is not guaranteed.  The `settleTxns` method should either not
need the Subject (preferred) or explicitly capture and re-bind it.

**`RegistrarImpl` — discovery/multicast threads:**
Discovery threads (multicast announcements, lookup) are spawned during
`new Initializer(...)` inside the `doAsPrivileged` scope.  If these threads perform
operations that require the JAAS Subject (e.g. constraint checking), they may silently
use the wrong Subject after migration to virtual threads.

### 11.5 Confirmed Correct / Keep As-Is

The following sites use `doAs`/`doAsPrivileged`/`getSubject` for workload (TLS/Kerberos)
identity, not user (human JAAS login) identity, and should remain unchanged:

- `KerberosUtil.getGSSCredential()` — GSS-API mandate
- `KerberosServerEndpoint` connection thread — GSS context establishment
- `SslEndpointImpl.getCallContext()` / `SslServerEndpointImpl` — TLS worker Subject lookup
- `X500Provider` — TLS principal matching
- `TlsRMIClientSocketFactory` / `TlsRMIServerSocketFactory` — TLS RMI
- `Security.doAs()` / `Security.doAsPrivileged()` — JGDMS custom SubjectDomainCombiner

### 11.6 Migration Decision Matrix

| If the Subject is… | And the scope is… | Use… |
|---|---|---|
| `WorkerSubject` (SPIFFE workload identity) | Anywhere | **Do NOT pass to `doAs` or `callAs`** — it is ambient; baked into every `ProtectionDomain` at class load time; `doAs(WorkerSubject, ...)` is rejected with `IllegalArgumentException` |
| `UserSubject` from JWT/OIDC login (`JwtPrincipal`) | Any scope | `Subject.callAs(Callable, UserSubject...)` — preferred varargs overload; `WorkerSubject` excluded at compile time; **this is the preferred path** |
| `UserSubject` (authenticated user identity, any type) | Any scope | `Subject.callAs(Callable, UserSubject...)` — preferred varargs overload; `WorkerSubject` excluded at compile time |
| `UserSubject` or vanilla user `Subject` | Executor-submitted background task | Capture `Subject.current()` before submit; wrap task in `Subject.callAs(captured, ...)` or `Subject.callAs(() -> ..., (UserSubject) captured)` |
| JAAS vanilla `Subject` (human identity, `LoginContext`) | Initialisation only (no cross-thread calls) | `Subject.callAs(loginSubject, action)` — makes Subject visible via `Subject.current()` |
| Vanilla `Subject` (GSS/Kerberos internal use only — **legacy**) | GSS credential acquisition | Keep `Subject.doAs` — GSS-API JDK constraint; vanilla `Subject` only, not `WorkerSubject` or `UserSubject` |
| Subject needed for `Subject.getSubject(acc)` check | Existing ACC-based check | Keep `Subject.getSubject(acc)` — reads the workload `WorkerSubject` from the ACC |

---

**v19 update:** `Thread.scopedSubject` is now `Subject[]`. The migration pattern for
executor tasks captures and re-establishes the full array:

```java
// Capture full Subject array before submitting to executor
Subject[] subjects = Subject.currentAll();
executor.submit(() -> {
    if (subjects.length > 0) {
        Subject.callAs(() -> { task.run(); return null; }, (UserSubject[]) subjects);
    } else {
        task.run();
    }
});
```

---

## 12. Remaining Work Items

1. **✅ `DynamicPolicyProvider.java` — single background sweeper for void eviction** *(completed)*
2. **✅ `DefaultPolicyParser.scanner` — `private` → `protected`** *(completed — already `protected final` in codebase)*
3. **✅ `HttpsClientAuthPolicyParser`** *(completed)*
4. **✅ `SpiffePolicyFile`** *(completed)*
5. **✅ `SpiffeCredentialManager` (DirtyChai)** *(completed)*
6. **✅ `SubjectDomainCombiner` — inject `SCOPED_SUBJECT` principals** *(completed)*
7. **✅ `Subject.java` class javadoc update** *(completed)*
8. **✅ `SpiffeCredentialManager` — trust bundle support** *(completed)*
9. **✅ `SpiffeX509TrustManager` — full chain verification** *(completed)*
10. **✅ `SpiffeCredentialManager` — exponential backoff reconnection** *(completed)*
11. **✅ `FilterX509TrustManager` — design analysis and documentation** *(completed)*
12. **✅ Code review v6 — 10 issues fixed** *(completed)*
13. **✅ Code review v7 — 10 further issues fixed** *(completed)*
14. **✅ `RemotePolicyService` interface** *(completed — 5 methods: `replace`, `getCurrentGrants`, `registerForPolicyUpdates`, `renewPolicyLease`, `cancelPolicyLease`; in `jgdms-platform/.../RemotePolicyService.java`)*
15. **✅ `InMemoryPolicyService`** *(completed)*
    - Implemented as two classes: `InMemoryPolicyServiceImpl` (453-line core POJO) + `ActivatableInMemoryPolicyServiceImpl` (extends `AbstractJiniService`, Jini lifecycle wrapper)
    - `PolicyUpdateEvent extends RemoteEvent` — implemented
    - `PolicyEventLease` — implemented
    - Lease management via `LandlordLease` — implemented
    - Async event dispatch via bounded queue + dispatcher thread — implemented
    - SPIFFE SVID `spiffe://jgdms.example.org/host/policy` — documented in class Javadoc
    - `PolicyPermission("Remote")` enforcement on `replace()` — implemented
16. **✅ DirtyChai smart proxy client for `RemotePolicyService`** *(completed — `RemotePolicyServiceProxy` + `ConstrainableRemotePolicyServiceProxy` inner class in `policy-service-dl`)*
17. **✅ `PERMISSIONS.LIST` BAE integration** *(completed)*
18. **✅ VerdictRegistry service** *(completed — `VerdictRegistryImpl` 1488 lines, `ActivatableVerdictRegistryImpl` extends `AbstractJiniService`, `VerdictRegistryProxy` smart proxy, full test coverage)*
19. **✅ BAE rewrite — `ClinitBlockingVisitor`, `AtomicSerialComplianceVisitor`, `JarAnalyzer`, `BytecodeAnalysisEngineImpl`** *(completed — push-model `analyzeJar(AnalysisRequest)` API; `<clinit>` cycle detection via Tarjan SCC embedded in `ClinitBlockingVisitor.detectClinitCycles()`)*
20. **✅ `SpiffeCredentialManager` (JGDMS `jgdms-jeri`)** *(completed — 795-line implementation with `FileSvidSource`, `updateSubjectCredentials()`, `SpiffeSubjectHolder`, unit tests)*
21. **✅ Client-side `RemoteEventListener`** — pull-on-notification for policy updates *(completed — `PolicyUpdateListener` in `policy-service-dl` module, `au.net.zeus.jgdms.policy.proxy`, with full unit test coverage: 9 tests covering happy path, gap detection, re-subscribe on lease loss, stop/unexport)*
    - Subscribes via `RemotePolicyServiceProxy.registerForPolicyUpdates()`
    - On event receipt: calls `getCurrentGrants()`, parses `String[]` → `PermissionGrant[]` via `DefaultPolicyParser`, calls `RemotePolicyProvider.replace()`
    - Tracks sequence numbers to detect gaps and logs WARNING on gap
    - Uses `LeaseRenewalManager` for automatic lease renewal; re-subscribes on `UnknownLeaseException`
22. **✅ Unit tests for `policy-service`** — *(completed; test coverage added for `InMemoryPolicyServiceImpl`, `RemotePolicyServiceProxy`, `PolicyEventLease`, and `PolicyUpdateEvent`)*
23. **✅ Host 4 — Codebase Downloader Service`** — COMPLETED
    - Maven module: codebase-downloader / codebase-downloader-service
    - Core POJO: CodebaseDownloaderImpl (worker pool, SHA-256 dedup, HTTP fetch,
      BAE pool dispatch, VerdictRegistry submission)
    - Jini wrapper: ActivatableCodebaseDownloaderImpl (AbstractJiniService,
      activatable + non-activatable constructors, ServiceDiscoveryManager)
    - Unit tests: CodebaseDownloaderImplTest (loopback HTTP server, stub BAE/VR)
    - SPIFFE SVID: spiffe://jgdms.example.org/host/downloader
24. **`ProxyCodebaseSPI` integration with `VerdictRegistry`** — ✅ *completed*
    - `PreferredProxyCodebaseProvider` computes SHA-256 hash of each JAR via `computeJarHash()` before creating a `PreferredClassLoader`
    - Calls `VerdictRegistry.getVerdictByHash(contentHash)` for each JAR (injected via `setVerdictRegistry()`)
    - Refuses to create a ClassLoader (throws `IOException`) if verdict is `DANGEROUS` or absent (null = not yet audited)
    - `INCONCLUSIVE` verdict proceeds with a `WARNING` log; `SAFE` proceeds silently at `FINEST`
    - Registry unreachable → fail-secure `IOException`
    - `VerdictRegistryHolder` package-private helper holds the volatile registry reference
    - Boot-time permissive: when registry is not yet injected (`null`), check is skipped
    - Unit tests in `jgdms-pref-class-loader/src/test/` cover all verdict outcomes and null-registry case
25. **`DiscoveryCredentialProvider` interface** — *(not yet started; referenced in design docs only)*
26. **`DigestGrant` plan — DirtyChai** — ✅ *completed (v20)*
    - `DigestCodeSource` — content-hash-carrying `CodeSource` subclass
    - `DigestGrant extends URIGrant` — new grant type; URI checked first, digest second
    - `PermissionGrantBuilder.DIGEST` constant + `digest(String, byte[])` method
    - `PermissionGrantBuilderImp` — wired up
    - `DefaultPolicyScanner` — `digest "alg:hex"` token recognised
    - `DefaultPolicyParser` — `hexDecode()` + `DIGEST` context routing
    - `SecurityPolicyWriter` — `hexEncode()` + `DigestCodeSource` detection
    - Policy file round-trip: write → parse → `DigestGrant` fully functional

---

## 13. Key Design Decisions — Cumulative

*(All rows from v18 §13 are retained. New rows below.)*

| Decision | Rationale |
|---|---|
| No `ReferenceQueue` in `DynamicPolicyProvider` | ACC self-evicts; `clearCache()` would be a no-op |
| Single background sweeper for void grant eviction | Keeps hot path read-only; one write source |
| `RemotePolicy` wire format is `String[]` | DirtyChai cannot implement `@AtomicSerial`; `String[]` needs no `@AtomicSerial` |
| Policy file syntax as wire format | `PermissionGrant.toString()` already emits it; `DefaultPolicyScanner` already parses it |
| Validation is server-side after parsing | Client smart proxy cannot be trusted |
| `getCurrentGrants()` returns `String[]` | Symmetric with `replace()`; client parses back without `@AtomicSerial` |
| `MarshalledInstance` not `MarshalledObject` | Carries codebase annotations, AtomicSerial-aware |
| Bootstrap policy fetched via HTTPS, no local cache | Fail-secure: node does not start if server unreachable |
| CA pinning on HTTPS fetch | Compromised system CA cannot serve malicious bootstrap policy |
| Pull-on-notification for policy events | `getCurrentGrants()` is always source of truth |
| Three-layer decoration stack | Clean separation of grant lifetimes; each layer revokes independently |
| `AdvisoryDynamicPermissions` on ClassLoader, not proxy class | ClassLoader is natural owner of codebase-wide permission declarations |
| ClassLoader keyed by `(InvocationHandler, codebase[], parent)` | Endpoint identity determines ClassLoader |
| Intersection enforced in `DynamicPolicyProvider.grant()` | `Security.grant()` enforces `GrantPermission` ceiling |
| Advisory grants are best-effort | `UnsupportedOperationException` in advisory path → logged, not rethrown |
| `createVirtualThread` in `GrantPermission` requires care | Grants make blocking `<clinit>` paths reachable — DoS risk |
| `PolicyPermission("Remote")` and `GrantPermission` itself must never be delegatable | Would allow proxies to participate in policy machinery |
| SCAP validates code safety; policy validates runtime authority | Complementary controls at different phases |
| `JarAnalysisReport` carries `String[] declaredPermissions` (`serialVersionUID=2L`) | Enables cross-referencing declared needs against `GrantPermission` ceiling |
| Authentication is SPIFFE/SPIRE workload identity; no traditional login | Identity is ambient — provisioned by SPIRE at workload startup |
| `AbstractJiniService` `loginContext` is null for SPIFFE services | SPIFFE identity is ambient; no JAAS login step |
| `BLOCKING_GUARDED` is `INCONCLUSIVE`, not `DANGEROUS` | Blocking path only reachable if guarding permission is granted |
| `BLOCKING_DECLARED` is `DANGEROUS` | JAR's own `PERMISSIONS.LIST` declares the guarding permission — DoS risk |
| SPIFFE workload identity on ACC (`doAs`); human identity on ScopedValue (`callAs`) | Clean namespace separation |
| `getSubject(ACC)` and `current()` both guarded by `AuthPermission("getSubject")` | Unified guard keeps policy simple |
| `SubjectDomainCombiner` reads `SCOPED_SUBJECT` in `combine()`, not constructor | SCOPED_SUBJECT changes per request; combiner is constructed once |
| `getMergedPrincipals()` additively merges principals from both Subjects | Neither replaces the other; grants conditioned on both require both |
| No `AuthPermission` check in `getMergedPrincipals()` | Combiner is trusted `java.base` infrastructure |
| `ScopedValue.isBound()` check before `get()` | Null-safe; cheap hot-path check |
| `FilterX509TrustManager` extends `X509ExtendedKeyManager` intentionally | Dual-role pattern for `AuthManager`; predates Java 7 `X509ExtendedTrustManager` |
| Trust bundle stored separately from SVID credentials | Rotates independently; defensive copying prevents external modification |
| Trust bundle parsed from `X509SVID.bundle` field | Per SPIRE Workload API spec; enables federated trust |
| Exponential backoff for SPIRE watcher reconnection | Production resilience; 1s → 2s → 4s → ... → 5min max |
| Raw `Thread` + `Thread.sleep` for reconnection scheduling | Bootstrap-safe; `ScheduledExecutorService` not audited for invokedynamic |
| `AtomicReference<R>` instead of three volatile fields | Single atomic swap eliminates read-tear window |
| Upfront overflow cap (62) instead of inline | Prevents misconfiguration at initialization |
| `createCallback()` factory method | Eliminates duplicate code; single source of truth |
| Trust domain enforcement enabled by default | Secure default; cross-trust requires explicit modification |
| `UriCodeSource` serialization explicitly forbidden | Prevents accidental serialization of DNS-avoiding identity |
| **`incrementAndGet()` before backoff computation** | ✅ **v7:** Eliminates get/increment race; correct 1x/2x/4x progression |
| **Empty SVID response does not overwrite valid Subject** | ✅ **v7:** Transient SPIRE outages cannot destroy valid unexpired credentials |
| **MAX_FRAME_SIZE = 1 MB cap in SpireConnection** | ✅ **v7:** Bounds memory allocation against malformed agent response |
| **StandardCharsets.UTF_8 throughout SPIRE client** | ✅ **v7:** Eliminates platform charset dependency |
| **Varint boundary `>= 35` (was `> 35`)** | ✅ **v7:** Correct rejection at 5-byte/32-bit limit |
| **`nextStreamId` is `volatile`** | ✅ **v7:** Ensures visibility between constructor thread and watcher thread |
| **`subjectBundle` is `private final`** | ✅ **v7:** Security-critical singleton field must not be package-accessible |
| **`MAX_USER_PRINCIPALS = 64` + `MAX_STRING_BYTES = 8192`** | ✅ **v8:** Bounds wire-asserted user principal block; prevents memory exhaustion from malformed input |
| **`RemotePrincipal` placeholder for unknown principal classes** | ✅ **v8:** Unknown class names from wire never cause arbitrary code to be loaded |
| **`instantiatePrincipal()` restricted to bootstrap + system classloader** | ✅ **v8:** Only JDK-bundled or system Principal classes accepted from wire |
| **`invokeWithClientSubject()` uses `Object[]/Throwable[]` holders** | ✅ **v8:** Throwable and return value escape lambda boundary without re-wrapping |
| **User Subject is read-only, no credentials** | ✅ **v8:** Wire-reconstructed user Subject is immutable and credential-free |
| **`InMemoryPolicyServiceImpl` is a standalone POJO; `ActivatableInMemoryPolicyServiceImpl` extends `AbstractJiniService`** | ✅ **v9:** Clean separation: core logic unit-testable without Jini infrastructure; activatable wrapper adds export/join/lifecycle |
| **`VerdictRegistryImpl` uses same two-class pattern** | ✅ **v9:** `VerdictRegistryImpl` (core) + `ActivatableVerdictRegistryImpl` (`AbstractJiniService`) |
| **`ClinitCycleVisitor` is not a separate class** | ✅ **v9:** Cycle detection is `ClinitBlockingVisitor.detectClinitCycles()` static method + `TarjanScc` private inner class — better cohesion, no separate file needed |
| **`DefaultPolicyParser.scanner` is already `protected final`** | ✅ **v9:** No change required; `HttpsClientAuthPolicyParser` can subclass directly |
| **`SUBJECT_CALL_AS`, `SUBJECT_DO_AS` (Dispatcher) and `SUBJECT_CURRENT` (Handler) are still reflective `Method` fields** | ✅ **v9 verified:** Resolved at class-init via `Subject.class.getMethod(...)`; invoked via `Method.invoke()`; null on JDK < 18 |
| **`Security.getCurrentPrincipals()` union confirmed as mandatory for POLP** | ✅ **v15 confirmed:** The v14 union is the correct and mandatory design. Three security premises require it: (1) `SpiffePolicyFile` cannot pre-assign to unknown proxy ClassLoaders; (2) policy files can only relax permissions — deny-all baseline; (3) POLP requires user + workload + code simultaneously. Impersonation scenario: a worker running on an insecure environment (e.g. Windows without SELinux) has a different SPIFFE principal — the union-scoped grant will not apply. |
| **`GrantPermission.checkGuard()` wraps `checkPermission` in `Subject.doAs(user)`** | ✅ **v10 → simplified in v12:** Originally added a `Subject.doAs(user, ...)` wrapper so the user's principals were in the ACC for `checkPermission`. **Removed in v12** after DirtyChai commit `2d26e787` — `AccessController.getContext()` now captures `SCOPED_SUBJECT` automatically (via `Subject.NoCheck` / `AccessController.SubjectAccess`), making the wrapper redundant. `checkGuard()` now calls `sm.checkPermission(this)` directly. |
| **`jgdms-platform` compiler release bumped to 21** | ✅ **v10:** Required to call `Subject.current()` and `Subject.doAs()` directly (not via reflection) in `jgdms-platform` source. |
| **`SslEndpointImpl` checks ACC (`doAs`) first, `Subject.current()` last** | ✅ **v11:** TLS requires X.509/SPIFFE credentials in the workload Subject. A Kerberos-only `Subject.current()` is explicitly rejected. `Subject.current()` is only accepted as last resort when it carries X500Principal or SpiffePrincipal. |
| **`KerberosEndpoint` checks `Subject.current()` first, ACC second** | ✅ **v11:** Kerberos GSS-API requires per-user KerberosPrincipal. The dispatch-installed user Subject (ScopedValue) is checked first; only falls back to ACC Subject when no KerberosPrincipal is bound. Connection cache (`CacheKey`) is per-Subject, preventing cross-user session reuse. |
| **JWT/OIDC (`JwtPrincipal`) is the primary user identity; Kerberos is legacy** | ✅ **v18:** JWT/OIDC is the modern standard for federated identity; stateless; no Kerberos KDC infrastructure required; `JwtPrincipal` integrates directly into the existing `writeUserPrincipals`/`instantiatePrincipal` JERI wire protocol via a single `(String)` constructor |
| **`ContextKey.equals()` — `!` removed from final return** | ✅ **v16:** Inverted logic was security-critical bug — caused cache collisions between ACCs with different `privilegedContext` and cache misses for identical ones |
| **`Subject.hashCode()` cached for read-only Subjects** | ✅ **v16:** Read-only Subjects are immutable; `synchronized` on every `hashCode()` call was unnecessary; `volatile` field + lazy init in `setReadOnly()` + restore in `readObject()` |
| **`SubjectDomainCombiner.equals()/hashCode()` implemented** | ✅ **v16:** Previously unused `hashCode` field (initialised to `Subject.hashCode()`) was clearly intended to be used; completing the implementation ensures `ContextKey` cache correctly shares ACCs across threads with the same user Subject |
| **User Subject principals baked into domain array, not combiner** | ✅ **v16:** Replaces `Scoped` combiner approach; original combiner is never displaced; works correctly alongside `DelegateDomainCombiner` and workload `SubjectDomainCombiner`; no special-casing needed |
| **Immediate `privilegedContext` enriched alongside stack domains** | ✅ **v16:** `optimize()` only consults immediate `privilegedContext` — enriching it is necessary and sufficient for user Subject principals to survive `doPrivileged` boundaries; nested `privilegedContext` chain predates the `callAs` scope |
| **`create(acc, combiner, true)` used in `getContext()` injection** | ✅ **v16:** Avoids re-entrant `checkAuthorized()` call that would occur with the public `create(acc, combiner)` overload |
| **`Thread.scopedSubject` field captured at construction** | ✅ **v16:** `NEW_THREAD_BINDINGS` sentinel is set unconditionally at end of `Thread` constructor — pre-seeding `scopedValueBindings` is not possible; `scopedSubject` field is the correct approach; guarded by `VM.isBooted()` for bootstrap safety |
| **`Thread.runWith()` re-establishes user Subject via `callNoCheck`** | ✅ **v16:** `runWith()` is `final` and called for both platform and virtual threads; `callNoCheck` bypasses `AuthPermission("doAs")` check (already checked when parent called `callAs`); re-establishes `Subject.current()` for the duration of the task |
| **User Subject propagates to `new Thread(...).start()` (diverges from OpenJDK)** | ✅ **v16:** Intentional divergence; in JGDMS, code spawning threads during request processing should naturally inherit authenticated user identity; executor-submitted tasks still do not propagate |
| **`SubjectDomainCombiner` (workload) and domain enrichment (user) are independent** | ✅ **v16:** The two mechanisms do not interact; workload combiner fires at `checkPermission` time via `goCombiner()`; user principals are already in the domain array before the combiner fires |
| **`doAs` javadoc documents `doPrivilegedWithCombiner` requirement** | ✅ **v16:** `SubjectDomainCombiner` is dropped at plain `doPrivileged` boundaries; `doPrivilegedWithCombiner` explicitly carries it forward; documented in all `doAs`/`doAsPrivileged` overloads |
| **`callAs` javadoc documents OpenJDK divergence explicitly** | ✅ **v16:** Future maintainers must not treat thread propagation behaviour as a bug to be "fixed" back to OpenJDK semantics |
| **Two `SpiffeCredentialManager` implementations** | ✅ **v19:** DirtyChai bootstrap implementation (`au.zeus.jdk.authorization.spire`) creates sealed `SpiffeSubject extends WorkerSubject` for `SecureClassLoader` domain baking; JGDMS implementation (`net.jini.jeri.ssl`) creates vanilla `Subject` with `X500Principal` + `SpiffePrincipal` for TLS credential management using standard Java APIs. `Subject.processWorker()` bridges both via `SpiffeSubjectHolder`. Separation of concerns: bootstrap identity vs TLS transport credentials |
| **`Thread.scopedSubjects` is `Subject[]` not single `Subject`** | ✅ **v19:** Mirrors `SCOPED_SUBJECT ScopedValue<Subject[]>`; full transaction array captured at construction; `runWith()` re-establishes all subjects via `callNoCheck(scopedSubjects, action)`; `Subject.currentAll()` works correctly in spawned threads for multi-party transactions |
| **`getContext()` multi-subject loop with per-iteration `SubjectDomainCombiner`** | ✅ **v19:** Each `UserSubject` in the array gets its own `combine()` pass; principals accumulate progressively; final ACC contains all merged; `WorkerSubject` skipped; `existing` combiner preserved across all iterations; `privilegedContext` enriched per subject |
| **`SubjectAccess.get()` returns `Subject[]`** | ✅ **v19:** `NoCheck.current()` returns `Subject[]`; `SubjectAccess.get()` delegates to `current()`; consistent with `SCOPED_SUBJECT` type; no single-subject extraction at this level |
| **`DigestGrant extends URIGrant`** | ✅ **v20:** `DigestGrant` is a URI grant with an additional content-hash constraint; URI match must pass before digest is checked; extending `URIGrant` reuses all URI/certificate/principal matching without duplication; `toString()` naturally prepends `digest` before `codebase` following the hierarchy prepend pattern |
| **`digest "algorithm:hexValue"` single-token policy syntax** | ✅ **v20:** Algorithm and hex value colon-separated inside one quoted string; mirrors `httpmd:` URL convention; scanner stores raw string, parser splits on first `:` and hex-decodes; no grammar ambiguity — colon cannot appear unquoted in a grant header |
| **`DigestGrant.implies(ClassLoader, Principal[])` returns `false`** | ✅ **v20:** A `ClassLoader` carries no content hash; returning `false` (indeterminate) rather than delegating to `URIGrant` is the correct fail-secure behaviour; `URIGrant`'s existing override already handles that path correctly for plain URI grants |
| **`SecurityPolicyWriter` detects `DigestCodeSource` at write time** | ✅ **v20:** `DigestCodeSource instanceof` check at write time enables policy round-trip; `hasDigest \|\| hasPrincipals` controls comma placement ensuring valid grant header syntax regardless of which optional clauses are present |

---

## 14. SPIFFE Identity Scheme

```
spiffe://jgdms.example.org/host/lookup          → Host 1 (Lookup Service)
spiffe://jgdms.example.org/host/bae/engine-N    → Host 2 (BAE instance N)
spiffe://jgdms.example.org/host/registry        → Host 3 (Verdict Registry)
spiffe://jgdms.example.org/host/downloader      → Host 4 (Codebase Downloader)
spiffe://jgdms.example.org/host/telemetry       → Host 5 (JFR Telemetry)
spiffe://jgdms.example.org/host/policy          → InMemoryPolicyService
spiffe://jgdms.example.org/admin/policy         → djinn administrator (DirtyChai)
spiffe://jgdms.example.org/client/<id>          → client nodes
spiffe://jgdms.example.org/svc/<name>           → service proxies (example pattern)
```

Only hosts with the admin SVID (`admin/policy`) may call `InMemoryPolicyService.replace()`.

---

*Hand this document (along with source files as needed) to a future AI agent to
continue without loss of context. This is version 19, updated to document:*

- *Two `SpiffeCredentialManager` implementations (JGDMS standard API vs DirtyChai
  bootstrap sealed `SpiffeSubject`)*
- *`Thread.scopedSubjects` field changed from `Subject` to `Subject[]`*
- *`AccessController.getContext()` multi-subject injection loop finalised*
- *`SubjectAccess.get()` returns `Subject[]`*
- *`Subject.processWorker()` bridges both implementations via `SpiffeSubjectHolder`*

---

*Previous version (v18) notes:*
- *JWT/OIDC (`JwtPrincipal` / `JwtLoginModule`) primary user identity; Kerberos legacy*
- *New module `jgdms-security-jwt`*

---

*Previous version (v17) notes:*
- *Alignment with JGDMS-STD-003 v3*
- *Sealed Subject hierarchy: `WorkerSubject`, `UserSubject`, vanilla `Subject`*
- *`WorkerSubject` ambient — baked into `ProtectionDomain`s at class load time*

---

*Previous version (v16) notes:*
- *Domain-enrichment approach replacing `Scoped` combiner*
- *`ContextKey.equals()` security bug fix*
- *`Subject.hashCode()` read-only caching*
- *Thread propagation divergence from OpenJDK*

---

*Previous version (v15) notes:*
- *§5.4.1 security rationale substantially expanded — three security premises documented*
- *§13 updated — `Security.getCurrentPrincipals()` union confirmed as mandatory*
- *§9 and §10.7 clarified — both `checkPermission` and `Security.grant()` use additive semantics*

---

*Previous version (v14) notes:*
- *§5.4.1 revised — `Security.getCurrentPrincipals()` returns union of user and worker Subject principals*
- *§13 updated — union semantics and POLP rationale documented*

---

*Previous version (v13) notes:*
- *§1 DirtyChai `SubjectDomainCombiner.java` note corrected — SCOPED_SUBJECT capture in `AccessController.getContext()`*
- *§10.11 updated — SSL and Kerberos endpoint ACC fallbacks explicitly principal-filtered*

---

*Previous version (v12) notes:*
- *§5.4.2 updated — `GrantPermission.checkGuard()` simplified; explicit `Subject.doAs` wrapper removed*

---

*Previous version (v11) notes:*
- *`SpiffeCredentialManager`, `SpireConnection`, `SpireProtobuf` reviewed*
- *§10 complete two-Subject JERI implementation documented*
- *§10.11 SSL vs Kerberos endpoint Subject lookup differences documented*