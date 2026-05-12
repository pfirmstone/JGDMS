# JGDMS — GrantPermission, Role Management & Full Architecture — AI Agent Context (v27)

**Purpose:** This document captures the full conversation context for an AI agent to
continue work on JGDMS role management and `GrantPermission` design without loss of
context. It supersedes and extends v26.

**GitHub repositories:**
- JGDMS: https://github.com/pfirmstone/JGDMS
- DirtyChai: https://github.com/pfirmstone/DirtyChai

---

## v27 Change Summary

This version documents two independent changes:

1. **Security fix for a TOCTOU context-confusion race** in the Work Item 28 ACC
   serialisation cache — marks Work Item 28 and §16.5 complete.
2. **ThreadGroup removal plan** — a full architectural analysis of every JGDMS site
   that currently holds a `ThreadGroup` reference for security or isolation purposes,
   and a concrete migration plan.

**Platform context (ThreadGroup / SecurityManager):**  `SecurityManager` is deprecated
for removal in JDK 17–23 and is fully disabled in JDK 24+, so neither
`modifyThreadGroup` nor `createPlatformThread` is enforced at runtime on standard JDK
builds.  **DirtyChai** is the preferred high-security platform, where `SecurityManager`
remains active; `Thread.ofPlatform().unstarted()` / `Thread.ofVirtual().unstarted()` are
guarded by `RuntimePermission("createPlatformThread")` / `"createVirtualThread"`
respectively, already tracked by `BlockingSinkRegistry`.  The ThreadGroup removal is
code hygiene on standard JDK and a real security-gate improvement on DirtyChai.

**New/changed in v27:**

- **§12 Work Item 28** — marked ✅ completed (v27).  Implementation uses a single
  `volatile AccSerialCache` holder (immutable inner class bundling `acc`,
  `transportBytes`, `digestBytes`) instead of three separate `volatile` fields.
  Three-field "publish last" pattern had a JMM TOCTOU race; full details in §16.5.

- **§16.5** — marked ✅ COMPLETED (HIGH Security Bug, v27).  Documents the
  context-confusion / impersonation vulnerability in the previous three-field design
  and describes the `AccSerialCache` holder fix.

- **§17 ThreadGroup Removal — Security Enforcement Migration** — new section with
  per-site analysis, three architecture options per site, ranked recommendations, and
  an updated policy-file change table.  §17.1 explicitly documents the JDK 17–23
  (deprecated) / JDK 24+ (disabled) / DirtyChai (active) enforcement distinction.
- **§12 work item 33** — new work item for the ThreadGroup removal.
- **§13** — one new design-decision row (`ThreadGroup` not a security boundary;
  `createPlatformThread` gate on DirtyChai).

---

## v26 Change Summary

This version documents several DoS/security fixes completed in v26.

**New/changed in v26:**
- *§16.1 ✅ completed: `InMemoryPolicyServiceImpl` — virtual-thread executor + `Semaphore(500)` cap*
- *§16.2 ✅ completed: `InMemoryPolicyServiceImpl` — `MAX_LISTENER_REGISTRATIONS=1000` cap + daemon sweep*
- *§16.3 ✅ completed: `HttpmdURLConnection` — `CappedOutputStream(64 MB)` wrapping Pack200 output*
- *§16.4 ✅ completed: `BasicInvocationDispatcher` — `PRINCIPAL_CTORS` allowlist + constructor cache*
- *§16.6 ✅ completed: `AccessControlContextSerializer.marshalForTransport()` — `anonCount` now encoded even when no HTTPMD records*
- *§16.5 (MEDIUM) and §16.7 (MEDIUM) remained outstanding at end of v26*

---

## v25 Change Summary

This version documents an **in-depth review of DoS vectors and bugs** identified in
the codebase, along with **architectural fix options and recommendations** for each.
Virtual-thread opportunities (JDK 21+) are evaluated for the policy-service event
dispatcher and JERI dispatch.  Seven distinct issues are covered across three modules:
`InMemoryPolicyServiceImpl`, `BasicInvocationHandler/Dispatcher`, and
`AccessControlContextSerializer`.

**New/changed in v25:**

- **§16 DoS Vectors and Architectural Fixes** — new section documenting seven issues
  with multiple architectural fix options and a ranked recommendation per issue.
- **§12 work items 30–32** — three new work items derived from the review.
- **§13** — four new design-decision rows (principal-string overflow, ACC ceiling
  drop, virtual-thread event dispatch, bounded listener registrations).

---



This version documents the **multi-Subject JERI wire-protocol implementation** —
extending `BasicInvocationHandler` and `BasicInvocationDispatcher` to correctly
send and dispatch all user Subjects on both DirtyChai and standard JDK targets.

**New/changed in v24:**

- **§10.3.1** (Client Side) — `getUserPrincipals()`/`writeUserPrincipals()` replaced
  by `getAllUserSubjects()`/`writeUserSubjects()`.  `CURRENT_ALL_METHOD` (static final
  `Method`, null on standard JDK) cached once at class-load via reflection.
  `getAllUserSubjects()` uses the cached field: zero-reflection cost on standard JDK
  (returns `Subject.current()` as a one-element array), full `Subject[]` array on
  DirtyChai.  The "known wire-protocol limitation" note removed — multi-Subject
  transmission is now fully implemented.

- **§10.3.2** (Server Side) — `CALL_AS_MULTI_SUBJECT` (static final `Method`, null on
  standard JDK) cached at class-load.  `invokeWithClientSubject()` now:
  - on DirtyChai with >1 Subject: single `Subject.callAs(Callable, Subject[])` varargs
    call passes all user Subjects simultaneously;
  - on standard JDK (or ≤1 Subject): falls back to `Subject.callAs(first, action)`;
  - `IllegalAccessException` from `Method.invoke()` re-thrown as `IllegalStateException`
    (should never occur — the method is public).

- **§10.10** key files table — updated method names.

- **§12 work item 29** added — multi-Subject JERI dispatch (✅ completed).

- **§13** new design decision rows added for `CURRENT_ALL_METHOD` caching,
  `CALL_AS_MULTI_SUBJECT` dispatch path, and `AccessControlContextSerializer`
  unreachable-catch fix.

- **Bug fix:** removed unreachable outer `catch (ClassNotFoundException)` in
  `AccessControlContextSerializer.unmarshalDigestFromTransport()` — the exception is
  already caught by the inner per-domain try-catch; the outer catch was a compile error
  on JDK 27.

- **Tests:** `MultiSubjectWireProtocolTest` extended with two new tests:
  `testCurrentAllMethodCachedCorrectly` and `testGetAllUserSubjectsReturnsCurrentSubject`
  (6 tests total pass).

---

## v23 Change Summary

This version documents two groups of changes:

1. The **domain-stripping privilege-escalation fix** in JERI ACC transport: the
   `marshalForTransport()` binary format now appends an `anonCount` field so that
   anonymous (unverifiable) domains are preserved as placeholder `ProtectionDomain`s on
   the receiver rather than being silently dropped — an implicit privilege escalation.
   Administrator guidance on minimal-permission policy for unverifiable domains and the
   `URLPermission → DigestCodeSource → LoadPermission` bootstrapping sequence is also
   captured.

2. A **deep-dive performance analysis** of the two major security architecture costs:
   transmitting the `AccessControlContext` on every outbound JERI call, and Pack200
   compression of service proxy JARs served via `httpmd:` URLs.

**New/changed in v23:**

- **§10.2** — renamed to "Remote ACC Serialization and Anonymous Domain Preservation";
  new **§10.2.1** documents the domain-stripping fix (anonymous count transport format).
- Administrator guidance: `URLPermission → DigestCodeSource → LoadPermission`
  bootstrapping sequence; `jrt:/java.base` excluded from `anonCount`.
- **§15 Performance Analysis — ACC Transmission & Pack200** — new section covering:
  - Wire-format byte budget for protocol-version `0x02` headers (~444 bytes typical)
  - CPU cost of the `extractDomains()` security stack walk (5–20 µs/call) and
    deserialization on the receiver (10–50 µs/call)
  - Pack200 compression ratios for `-dl` JAR files (40–60% reduction) and the
    `AnalysisRequest` BAE pipeline path
  - Cross-cutting interactions: ACC cache (`ContextCache`), `equals`/`hashCode`
    deduplication, `DigestCodeSource` dual-path single-pass opportunity
  - Identified performance gap: per-call stack walk; connection-level ACC cache proposed
- **§12 work item 28** added — connection-level serialized-ACC cache
- **§13** — five new design-decision rows (two security, three performance)
- `AccessControlContextSerializer.java` — Javadoc updated on `marshalForTransport()`
  and `recordsFromContext()` documenting the anonymous count format and the
  privilege-escalation fix rationale

---

## v22 Change Summary

This version documents the **`equals` / `hashCode` additions** to
`AccessControlContextSerializer` and `DomainIdentityRecord`.  Implementing
logical equality allows the serialization stream's back-reference handle table
to deduplicate equal serializer instances that are encountered more than once
during a single `writeObject` pass, reducing wire-format redundancy.

**New/changed in v22:**

- **`DomainIdentityRecord.equals()`** — compares `location`, `principalTypes`,
  and `principalNames` fields; consistent with the record's persistent state.
- **`DomainIdentityRecord.hashCode()`** — hash of `location` combined with
  `Arrays.hashCode(principalTypes)` and `Arrays.hashCode(principalNames)`.
- **`AccessControlContextSerializer.cachedDigestBytes`** — new
  `private transient volatile byte[]` field; lazily populated by `digestBytes()`
  and reused by `equals` / `hashCode` so that `marshalDigestForTransport` is
  called at most once per instance.
- **`AccessControlContextSerializer.digestBytes()`** — private helper that
  computes and caches the digest transport bytes; returns `new byte[0]` if
  `marshalDigestForTransport` throws.
- **`AccessControlContextSerializer.equals()`** — compares `domains` arrays
  (HTTPMD part, via `DomainIdentityRecord.equals`) and the cached digest bytes
  (digest part).
- **`AccessControlContextSerializer.hashCode()`** — `31 * Arrays.hashCode(domains)
  + Arrays.hashCode(digestBytes())`.
- `java.util.Arrays` import added.
- **§1 document table** row for `AccessControlContextSerializer.java` updated.
- **§12 work items** — item 27 note extended.
- **§13** one new design-decision row added.

---

## v21 Change Summary

This version documents the **JERI `AccessControlContextSerializer` extension** for
`DigestCodeSource` domains.  When a JERI endpoint transmits an
`AccessControlContext`, it now includes `DigestCodeSource`-backed `ProtectionDomain`s
so that the remote end can apply `DigestGrant` policy grants.  No compile-time
dependency on DirtyChai is introduced.

**New/changed in v21:**

- **`AccessControlContextSerializer`** — new serial field `digestTransportBytes`
  (`byte[]`) stores `DigestCodeSource` domains separately from the existing
  `transportBytes` (HTTPMD-URL-only) field.
- **`marshalDigestForTransport()`** — detects `DigestCodeSource` by
  `cs instanceof Externalizable` + class-name check; calls
  `AtomicMarshalOutputStream.writeObject(cs)` which invokes `writeExternal()` via
  the `Externalizable` protocol — no reflection.
- **`unmarshalDigestFromTransport()`** — reads via
  `AtomicMarshalInputStream.create(…, readAnnotations=false)` (secure, DOS-resistant);
  `ClassNotFoundException` → `break` (fail-secure domain drop on standard JDK).
- **`DomainIdentityRecord.from()`** — now explicitly skips `DigestCodeSource`
  instances before the httpmd URL test, preventing a `DigestCodeSource` whose
  location happens to be an httpmd URL from being duplicated in both transport fields.
- **`buildContext()`** — merges HTTPMD and `DigestCodeSource` `ProtectionDomain[]`
  arrays into a single `AccessControlContext`.
- **`AccessControlContextSerializerTest`** — two new regression tests:
  `testDigestTransportFieldEmptyForStandardJdkAcc` and `testHttpmdTransportRoundTrip`.
- **§1 document table** updated with `AccessControlContextSerializer.java`.
- **§12 work items** — item 27 added (JERI ACC transport for `DigestCodeSource`).
- **§13** three new design-decision rows added.

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
  (1) `Subject.getSubject(acc)` (accepted only if it has `X500Principal` or
  `SpiffePrincipal`), (2) `SpiffeSubjectHolder.get()`, (3) `Subject.current()` (last
  resort, accepted only with `X500Principal` or `SpiffePrincipal`).  `UserSubject` and
  Kerberos-only `Subject` instances are never TLS identities.  See §10.11.

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
  (1) `Subject.getSubject(acc)` (accepted only if it has `X500Principal` or
  `SpiffePrincipal`), (2) `SpiffeSubjectHolder.get()`, (3) `Subject.current()` (last
  resort, accepted only with `X500Principal` or `SpiffePrincipal`).  `UserSubject` and
  Kerberos-only `Subject` instances are never TLS identities.  See §10.11.

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
| `AccessControlContextSerializer.java` | `JGDMS/jgdms-platform/src/main/java/org/apache/river/api/io/` | ✅ **Updated (v21/v22):** New `digestTransportBytes` serial field carries `DigestCodeSource` domains via `AtomicMarshalOutputStream`/`AtomicMarshalInputStream`; `DomainIdentityRecord.from()` now skips `DigestCodeSource` to prevent httpmd-URL duplication; `buildContext()` merges HTTPMD + digest `ProtectionDomain[]` arrays; `DomainIdentityRecord.equals/hashCode` + `AccessControlContextSerializer.equals/hashCode` added (v22); `cachedDigestBytes` transient cache avoids recomputing digest bytes on each equality check |

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
public WorkerSubject processWorker() {
    // AuthPermission("getSubject") check
    // The cast is safe: SpiffeSubjectHolder.get() holds the Subject registered by
    // whichever SpiffeCredentialManager.start() was called first — always a WorkerSubject
    // subtype (SpiffeSubject for the DirtyChai bootstrap implementation, or an equivalent
    // WorkerSubject for the JGDMS implementation).  If no SpiffeCredentialManager has
    // started, this returns null.
    return (WorkerSubject) SpiffeCredentialManager.getInstance().getSubject();
}
```

**`Subject.current()` in spawned threads:**

`Thread` captures `SCOPED_SUBJECT` at construction time into a `Subject[] scopedSubject`
field (guarded by `VM.isBooted()`). `Thread.runWith()` (`final`, platform and virtual)
re-establishes this as a `callAs` scope via `SubjectAccess.callNoCheck(scopedSubject, action)`
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

`Thread` captures `Subject[] scopedSubject = SubjectAccess.SCOPED.get()` at construction
(guarded by `VM.isBooted()`). `Thread.runWith()` calls
`SubjectAccess.callNoCheck(scopedSubject, action)` — the full array is re-established,
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

### 10.2 Remote ACC Serialization and Anonymous Domain Preservation

Remote process identity travels as `ProtectionDomain`s in a serialized
`AccessControlContext`.  `AccessControlContext` is serialized using `@AtomicSerial`.
The serialized form contains `ProtectionDomain[]` with `httpmd:` codebase URIs (SHA-256
verifiable) and `WorkerSubject` principals baked in at class load time on the remote JVM.

A `DomainCombiner` on the receiving JVM:
- Strips any domain whose `httpmd:` SHA-256 does not verify
- Strips any domain whose `SpiffePrincipal` is outside the trusted SPIFFE trust domain
- Verified domains participate in `RemotePolicy` checks as call stack domains

**⚠ Security background — why domain stripping was a privilege escalation:**

Every domain in a caller's `AccessControlContext` acts as a permission ceiling: for a
`checkPermission` call to succeed, **every** domain in the intersection must hold the
permission.  When an unverifiable domain was silently dropped during JERI transport, that
ceiling disappeared.  In the worst case an ACC that was entirely unprivileged on the
sender side — because it contained at least one domain with no permissions at all —
could become fully privileged on the receiver side.  This is structurally identical to an
unchecked `doPrivileged` call.

This risk was addressed in v23 — see **§10.2.1** below for the anonymous domain count
transport format that preserves the ceiling effect without requiring a verifiable identity.

**Unverifiable domains** are `ProtectionDomain`s whose `CodeSource` has:
- a plain (non-`httpmd:`) URL, or
- no codebase URL at all (e.g. domains created with `new ProtectionDomain(null, ...)`).

**Administrator guidance — `LoadPermission` bootstrapping:**

If a domain must eventually obtain `LoadPermission` (to load classes from a remote
codebase), the safe bootstrapping sequence is:

   | Step | What happens |
   |---|---|
   | 1 | Grant `URLPermission` (or `java.net.URLPermission`) to the unverified domain so it can fetch the codebase URL over HTTP/HTTPS. |
   | 2 | The fetch succeeds; `SecureClassLoader` computes the SHA-256 content hash and records it in a `DigestCodeSource`. |
   | 3 | The `DigestCodeSource`-backed domain is verifiable and survives JERI transport via `digestTransportBytes`. |
   | 4 | Only **after** step 3 is complete may `LoadPermission` be granted, scoped to the verified `DigestCodeSource` identity. |

Granting `LoadPermission` **before** the digest is established is unsafe: the domain
travels only as an anonymous placeholder on the receiver, and the resulting grant is
effectively unconstrained by codebase identity.

`DomainCombiner` is retained as a **Java API compatibility layer** specifically for this
receiving-side verification role.  `SubjectDomainCombiner` is deprecated.
`CombinerSecurityManager` refactoring is deferred.

#### 10.2.1 Domain Stripping Removed — Anonymous Count Transport (v23)

Prior to v23 the sender silently dropped every non-HTTPMD, non-`DigestCodeSource` domain
from the ACC before transmission.  Because those domains act as **permission ceilings**
in the sender's ACC their removal was an implicit privilege escalation.

`AccessControlContextSerializer.marshalForTransport()` now uses a single binary format:

```
[httpmdCount: 4 B BE][DomainIdentityRecord…][anonCount: 4 B BE]
```

`anonCount` is the number of non-HTTPMD, non-`DigestCodeSource` domains in the sender's
ACC that could not be transported with a verifiable identity.  On the receiver,
`unmarshalHttpmdDomains()` reconstructs one placeholder `ProtectionDomain(null CS, null
perms)` per anonymous domain.  Placeholder permissions are resolved by the server's
security policy at `checkPermission` time.

**`jrt:/java.base` exclusion:** The `java.base` JDK module domain is present in every
running JVM and carries no useful diagnostic identity.  It is excluded from `anonCount`
to avoid inflating the transport payload.  All other `jrt:` module domains (e.g.
`jrt:/jdk.crypto.ec`) **are retained** because their presence can identify processes
running a specific (potentially vulnerable) JDK module version.

**`RemotePolicy` grant example (remote code + remote process):**
```
grant codeBase "httpmd://repo.example.org/client-stub.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../svc/trusted-client" {
    permission OrderPermission "submit";
};
```
### 10.3 Invocation Layer Handling and Dispatch
#### 10.3.1 Client Side — `BasicInvocationHandler`

**`CURRENT_ALL_METHOD` (static final, cached at class-load):**
```java
private static final Method CURRENT_ALL_METHOD;
static {
    Method m = null;
    try {
        m = Subject.class.getMethod("currentAll");
    } catch (NoSuchMethodException | SecurityException ignored) { }
    CURRENT_ALL_METHOD = m;
}
```
- `null` on a standard JDK (no `Subject.currentAll()`).
- Non-null on DirtyChai JDK 27 (where `Subject.currentAll()` exists).

**`getAllUserSubjects()` (private static):**
```java
private static Subject[] getAllUserSubjects() {
    if (CURRENT_ALL_METHOD != null) {
        try {
            Subject[] arr = (Subject[]) CURRENT_ALL_METHOD.invoke(null);
            if (arr != null && arr.length > 0) return arr;
        } catch (Exception ignored) { }
    }
    Subject single = Subject.current();
    return single != null ? new Subject[]{single} : new Subject[0];
}
```
- On DirtyChai: calls the cached `Subject.currentAll()` — returns all user Subjects
  bound via `Subject.callAs`, outermost-first, with zero per-call reflection lookup.
- On standard JDK: `Subject.current()` is called directly; returns single-element array.
- Worker Subject is **not** included; its principals reach the server via the TLS
  certificate chain.

**`writeUserSubjects()` (static, package-private) — wire encoding:**
```
subjectCount          : unsigned 16-bit big-endian  (number of user Subjects; max MAX_USER_SUBJECTS=16)
for each Subject:
  principalCount      : unsigned 16-bit big-endian
  for each principal:
    classNameLength   : unsigned 16-bit big-endian
    classNameBytes    : UTF-8
    nameLength        : unsigned 16-bit big-endian
    nameBytes         : UTF-8
```
Each principal serialises to `(p.getClass().getName(), p.getName())`.

**Wire protocol version selection** (unchanged):**
```
if (!userSubjects.isEmpty())  → write 0x02 + integrity + atomicValidation + user-Subject block
else if (atomicValidation)    → write 0x01 + integrity + atomicValidation
else                          → write 0x00 + integrity   (legacy compatibility)
```

#### 10.3.2 Server Side — `BasicInvocationDispatcher`

**`CALL_AS_MULTI_SUBJECT` (static final, cached at class-load):**
```java
private static final Method CALL_AS_MULTI_SUBJECT;
static {
    Method m = null;
    try {
        m = Subject.class.getMethod("callAs", Callable.class, Subject[].class);
    } catch (NoSuchMethodException | SecurityException ignored) { }
    CALL_AS_MULTI_SUBJECT = m;
}
```
- `null` on a standard JDK.
- Non-null on DirtyChai JDK 27 (where `Subject.callAs(Callable, Subject[])` exists).

**Security limits (constants):**
- `MAX_USER_SUBJECTS = 16` — rejects requests claiming more Subjects.
- `MAX_USER_PRINCIPALS = 64` — rejects requests claiming more principals per Subject.
- `MAX_STRING_BYTES = 8192` — rejects any single class-name or principal-name field
  exceeding this byte length.

**`readUserSubjects()` — wire parsing:**
- Reads `subjectCount`; throws `IOException` if `> MAX_USER_SUBJECTS`.
- For each Subject: reads `principalCount`; throws `IOException` if `> MAX_USER_PRINCIPALS`.
- For each principal: reads `className` + `name` (both length-prefixed, bounded by
  `MAX_STRING_BYTES`).
- Calls `instantiatePrincipal(className, name)`.
- Collects into `List<Subject>` (one read-only `Subject` per Subject block).

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

**`addUserSubjectsToContext()` — context injection:**
```java
// One UserSubjectImpl per request; holds all wire-reconstructed user Subjects.
// Wire-reconstructed user identity is a vanilla Subject (not UserSubject).
// The principals were asserted over the wire by the remote client and are not
// independently verified; they are vouched for by the TLS-authenticated workerSubject
// only.  Policy should condition grants on both a trusted SpiffePrincipal (worker) AND
// the asserted user principal to prevent impersonation.
context.add(new UserSubjectImpl(userSubjects));  // userSubjects is List<Subject>
```
Each Subject in the list is read-only, contains no credentials, and is kept completely
separate from the worker Subject in the `ClientSubject` context element.  Multiple
Subjects represent multiple authenticated users in a multi-party transaction context.
**`invokeWithClientSubject()` — dispatch nesting:**

The `workerSubject` retrieved from the `ClientSubject` context element on the server side
is the TLS-verified remote client's `Subject` (a vanilla `Subject` containing
`SpiffePrincipal` for v17+ connections — **not** a `WorkerSubject`).  The local server's
`WorkerSubject` is **ambient** (baked into every `ProtectionDomain` at class load time)
and is never reinstalled per-request.  `Subject.doAs(workerSubject, ...)` is explicitly
rejected for any `WorkerSubject` instance (`IllegalArgumentException`).

```
workerSubject  = ClientSubject context element  (TLS-verified remote client Subject;
                 vanilla Subject with SpiffePrincipal — NOT a WorkerSubject)
userSubjects[] = ClientUserSubject context element  (wire-asserted human identities;
                 may be empty)

case: >1 user Subjects AND DirtyChai JDK (CALL_AS_MULTI_SUBJECT != null)
    Subject.doAs(workerSubject, () -> {                          // ACC; thread-inherited
        Subject.callAs(dispatchAction, userSubjects[0..n]);      // all Subjects at once
    });

case: exactly 1 user Subject (any JDK)
    Subject.doAs(workerSubject, () -> {                          // ACC; thread-inherited
        Subject.callAs(userSubjects[0], () -> {                  // ScopedValue
            invoke(impl, method, args, context)
        });
    });

case: 0 user Subjects, workerSubject present
    Subject.doAs(workerSubject, () -> { invoke(...); });

case: no workerSubject, no userSubjects
    invoke(impl, method, args, context);
```

On DirtyChai the single-varargs `callAs(Callable, Subject[])` call passes all user
Subjects simultaneously; nesting individual `callAs` calls (as on a standard JDK) is
incorrect because each inner call shadows the outer, leaving only the innermost Subject
visible via `Subject.current()`.

The remote client's `workerSubject` (from `ClientSubject.getClientSubject()`) is
available via `ServerContext` for auditing and authorisation decisions; it is not
reinstalled on the ACC per-request — the remote process identity travels in the
serialized ACC `ProtectionDomain`s received over the wire (§10.2).

Throwable and return value are captured in `Object[1]` / `Throwable[1]` holders to
escape the lambda boundary; the original `Throwable` is re-thrown unchanged.

### 10.4 ServerContext API — Retrieving Both Subjects

From within a JERI service method (server side):

```java
// Worker Subject — TLS-verified SPIFFE workload identity
ClientSubject cs = (ClientSubject)
    ServerContext.getServerContextElement(ClientSubject.class);
Subject workerSubject = cs != null ? cs.getClientSubject() : null;

// User Subjects — wire-asserted human identities (v0x02 only; empty array if absent)
ClientUserSubject cus = (ClientUserSubject)
    ServerContext.getServerContextElement(ClientUserSubject.class);
Subject[] userSubjects = cus != null ? cus.getUserSubjects() : new Subject[0];
// For single-Subject callers, the convenience default method:
Subject userSubject = cus != null ? cus.getUserSubject() : null; // = userSubjects[0]
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
Subject[] subjects = SubjectAccess.SCOPED.get(); // returns Subject[] via NoCheck trust chain
if (subjects != null && subjects.length > 0) {
    DomainCombiner existing = acc.getCombiner();
    ProtectionDomain[] combined = acc.getContext();
    AccessControlContext privileged = acc.privilegedContext;

    for (Subject subject : subjects) {
        if (subject instanceof WorkerSubject) continue; // WorkerSubject is ambient — skip
        if (!subject.isReadOnly()) continue;            // mutable Subjects are unstable — skip

        SubjectDomainCombiner sdc = new SubjectDomainCombiner(subject);
        // Bake this subject's principals into the domain array (accumulates progressively)
        combined = sdc.combine(combined, combined);

        // Enrich privilegedContext so principals survive doPrivileged boundaries
        if (privileged != null) {
            ProtectionDomain[] enrichedPriv =
                sdc.combine(privileged.getContext(), privileged.getContext());
            privileged = AccessControlContext.create(
                enrichedPriv, privileged.privilegedContext,
                privileged.getCombiner(), privileged.isPrivileged());
        }
    }
    // Restore original combiner; preserve isPrivileged and privilegedContext.
    // Four arguments:
    //   combined          — enriched ProtectionDomain[]: stack domains with all
    //                       UserSubject principals baked in (accumulated across loop)
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

- `SubjectAccess.SCOPED.get()` returns `Subject[]` — the full multi-subject array from
  `SCOPED_SUBJECT`.
- `WorkerSubject` instances are **always skipped** — the worker identity is ambient,
  already baked into every `ProtectionDomain` at class load time.
- Only **read-only** Subjects are injected — mutable Subjects have unstable hash and
  equality and must not be baked into domains.
- Each `UserSubject` gets its own `SubjectDomainCombiner.combine()` pass; principals
  **accumulate progressively** — the final `combined` array contains all principals from
  all loop iterations merged together.
- The original combiner (workload `SubjectDomainCombiner`, `DelegateDomainCombiner`,
  or `null`) is **never displaced** — it is restored on the enriched ACC via the four-arg
  `AccessControlContext.create(combined, privileged, existing, acc.isPrivileged())`.
- The four-arg `create()` form is used (not `create(acc, combiner, true)`) — it directly
  constructs the ACC from the already-enriched domain array without triggering
  `checkAuthorized()` re-entrancy.
- Only the **immediate** `privilegedContext` is enriched per subject — nested
  `privilegedContext` references were established before the `callAs` scope and predate
  the user Subject.
- Only the immediate `privilegedContext` is checked during `checkPermission`
  (`optimize()` only consults `acc.privilegedContext`), so enriching it is both
  necessary and sufficient.
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
   the worker identity from their `ProtectionDomain`s (ambient — baked in at class load
   time by `SecureClassLoader`; no `SubjectDomainCombiner` combiner is required at
   dispatch time) and the user identity via both enriched domains (for policy checks) and
   `Subject.current()` (for explicit identity retrieval).  Both are re-established
   automatically — the worker because it is ambient in every domain, the user because
   `Thread.runWith()` re-establishes `SCOPED_SUBJECT` via `callNoCheck`.

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
| `loginContext == null` (SPIFFE path) | `doStart()` called directly; SPIFFE Subject registered by `SpiffeCredentialManager.start()` via `SpiffeSubjectHolder` — outbound TLS calls use `SpiffeSubjectHolder` automatically (no explicit `callAs` or `doAs` needed); server-side dispatch establishes user Subject per-request via `Subject.callAs(userSubject, …)` in `BasicInvocationDispatcher`; the local `WorkerSubject` is ambient in every `ProtectionDomain` — no per-request reinstall is needed |
| `loginContext != null` (traditional path) | `loginContext.login()` called; `Subject.callAs(loginSubject, callable)` used to run `doStart()` |

JGDMS services use the SPIFFE path.  The traditional path is supported for legacy
Jini services.

### 10.10 Key Files (User/Worker Subject Implementation)

| File | Role |
|---|---|
| `JGDMS/jgdms-jeri/.../BasicInvocationHandler.java` | Client: `CURRENT_ALL_METHOD`, `getAllUserSubjects()`, `writeUserSubjects()`, wire version selection |
| `JGDMS/jgdms-jeri/.../BasicInvocationDispatcher.java` | Server: `CALL_AS_MULTI_SUBJECT`, `readUserSubjects()`, `instantiatePrincipal()`, `addUserSubjectsToContext()`, `invokeWithClientSubject()`, `RemotePrincipal`, `UserSubjectImpl` |
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
Subject.callAs(userSubject, () -> {      // user on ScopedValue — Kerberos uses this
    invoke(impl, method, args, context)
});
```

The local server's `WorkerSubject` is **ambient** — baked into every `ProtectionDomain`
at class load time by `SecureClassLoader`; no per-request `doAs(workerSubject, ...)` is
needed or performed.

An outbound TLS call made from inside `invoke()` will use the process-wide SPIFFE workload
Subject via `SpiffeSubjectHolder` (or the filtered ACC Subject), and may also check
`Subject.current()` (X500/SPIFFE only) — `SslEndpointImpl` checks ACC first, then
`SpiffeSubjectHolder`, then `Subject.current()` (X500/SPIFFE only).
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
executor tasks captures and re-establishes the full array.  Because `Subject.currentAll()`
may return a mix of `UserSubject` and legacy vanilla `Subject` instances,
**do not cast the array directly to `UserSubject[]`** — this will throw
`ClassCastException` whenever a legacy vanilla `Subject` is present.  Instead, use one
of the following safe patterns:

```java
// Pattern A — filter to UserSubject, use varargs callAs
// Use when: all Subjects in the transaction context are expected to be UserSubject.
// Non-UserSubject (legacy vanilla Subject) instances are dropped — use Pattern B if
// vanilla Subject context must be preserved.
Subject[] subjects = Subject.currentAll();
UserSubject[] userSubjects = Arrays.stream(subjects)
    .filter(s -> s instanceof UserSubject)
    .toArray(UserSubject[]::new);
executor.submit(() -> {
    if (userSubjects.length > 0) {
        Subject.callAs(() -> { task.run(); return null; }, userSubjects);
    } else {
        task.run();
    }
});
```

```java
// Pattern B — re-establish each Subject in reverse order (PREFERRED for mixed contexts)
// Use when: the array may contain a mix of UserSubject and legacy vanilla Subject.
// All non-WorkerSubject entries are re-established; first subject ends up as
// Subject.current().  Vanilla Subject instances (e.g. from JAAS LoginContext) are
// preserved.  WorkerSubject is skipped because it is ambient.
Subject[] subjects = Subject.currentAll();
executor.submit(() -> {
    try {
        Callable<Void> wrapped = () -> { task.run(); return null; };
        // Re-establish each Subject in reverse so first Subject is outermost (current())
        for (int i = subjects.length - 1; i >= 0; i--) {
            final Callable<Void> prev = wrapped;
            final Subject s = subjects[i];
            if (!(s instanceof WorkerSubject)) {
                wrapped = () -> Subject.callAs(s, prev);
            }
        }
        wrapped.call();
    } catch (Exception e) { throw new RuntimeException(e); }
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
27. **JERI ACC transport for `DigestCodeSource` domains** — ✅ *completed (v21/v22)*
28. **Connection-level serialized-ACC cache** — ✅ *completed (v27); security fix applied*
    - `BasicInvocationHandler.invoke()` previously called `marshalForTransport(currentAcc)` on
      **every** outbound call, triggering a JVM security stack walk (~5–20 µs) each time.
    - **Implementation:** a private immutable inner class `AccSerialCache` bundles the three
      related values — `AccessControlContext acc`, `byte[] transportBytes`, `byte[] digestBytes`
      — into a single object published via one `volatile AccSerialCache accSerialCache` field.
      `invokeRemoteMethodOnce()` does one volatile read into a local variable, checks `cache.acc
      != currentAcc` (reference comparison), and on a miss recomputes both byte arrays and
      publishes a new holder with one volatile write.
    - **Security rationale (TOCTOU race in prior three-field design):** The previous design held
      three separate `volatile` fields (`cachedAccRef`, `cachedTransportBytes`,
      `cachedDigestBytes`) and relied on "publish last" ordering (writing `cachedAccRef` after
      the byte arrays).  The JMM's synchronisation order applies *per field*; it is **not**
      jointly atomic across fields.  A concurrent thread could overwrite `cachedTransportBytes`
      after Thread B's identity check but before Thread B's subsequent read of
      `cachedTransportBytes`, causing Thread B to send Thread C's (potentially
      higher-privilege) serialised ACC bytes to the server — a context-confusion /
      impersonation vulnerability.  A single `volatile` holder reference eliminates the window.
    - Full vulnerability analysis and code details: §16.5.
29. **Multi-Subject JERI dispatch** — ✅ *completed (v24)*
    - `BasicInvocationHandler`: `CURRENT_ALL_METHOD` (static final `Method`) cached at class-load
      via `Subject.class.getMethod("currentAll")`; null on standard JDK.
    - `getAllUserSubjects()` uses the cached field (zero per-call reflection on std JDK);
      returns `Subject[]` outermost-first; falls back to `Subject.current()` single-element
      array on standard JDK.
    - `writeUserSubjects()` encodes `subjectCount:u16` + per-Subject `principalCount:u16 +
      (className:u16-prefixed-UTF8 + name:u16-prefixed-UTF8)` per principal.
    - `BasicInvocationDispatcher`: `CALL_AS_MULTI_SUBJECT` (static final `Method`) cached at
      class-load via `Subject.class.getMethod("callAs", Callable.class, Subject[].class)`;
      null on standard JDK.
    - `invokeWithClientSubject()`: on DirtyChai with >1 Subject, single varargs
      `CALL_AS_MULTI_SUBJECT.invoke(null, action, userSubjects)` call; on std JDK or 1
      Subject, `Subject.callAs(first, action)`.  `IllegalAccessException` re-thrown as
      `IllegalStateException` (should never occur — method is public).
    - `readUserSubjects()` decodes `subjectCount:u16` + per-Subject principal list; bounded
      by `MAX_USER_SUBJECTS=16` and `MAX_USER_PRINCIPALS=64`.
    - `UserSubjectImpl` now holds `Subject[]` (all wire-reconstructed user Subjects);
      `getUserSubjects()` returns a clone.
    - Bug fix: removed unreachable outer `catch (ClassNotFoundException)` in
      `AccessControlContextSerializer.unmarshalDigestFromTransport()`.
    - Tests: `MultiSubjectWireProtocolTest` — 6 tests pass (wire-protocol round-trip +
      `CURRENT_ALL_METHOD` caching + `getAllUserSubjects()` under `Subject.callAs`).
30. **Policy-service DoS hardening (v25)** — *(✅ completed — v26)*
    - Fix 1 (§16.1): Replaced `ThreadPoolExecutor` + unbounded `LinkedBlockingQueue` with
      `Executors.newVirtualThreadPerTaskExecutor()` + `Semaphore(500)`.  `dispatchUpdateEvent()`
      does `tryAcquire()` per listener; saturation logs WARNING and skips that listener.
      Module `pom.xml` updated to `<release>21</release>`.
    - Fix 2 (§16.2): Added `MAX_LISTENER_REGISTRATIONS = 1000` cap in
      `registerForPolicyUpdates()` (race-free via `registrationLock`); daemon virtual-thread
      sweep (`JGDMS-PolicyService-LeaseSweep`) removes expired registrations every 60 s;
      interrupted cleanly in `shutdown()`.
31. **`instantiatePrincipal()` allowlist + constructor cache (v25)** — *(✅ completed — v26)*
    - Replaced per-request `Class.forName` + `Constructor.newInstance` with static
      `PRINCIPAL_CTORS: Map<String, Constructor<? extends Principal>>` populated eagerly at
      class-load for `X500Principal`, `KerberosPrincipal`, `SpiffePrincipal`, `JwtPrincipal`.
      Unknown class names return `RemotePrincipal` immediately — zero per-request class-loading.
      (§16.4 Option A implemented.)
32. **Correctness fixes from v25 review** — *(partially completed — v26)*
    - Fix 1 (§16.6, HIGH ✅): `marshalForTransport()` guard changed from `records.isEmpty()`
      to `records.isEmpty() && anonCount == 0` — anonymous domain ceilings are now always
      encoded, closing the privilege-escalation window.
    - Fix 2 (§16.7, MEDIUM ✅): `writeUtf8Prefixed()` now throws `IOException`
      instead of silently truncating strings that exceed 65 535 UTF-8 bytes
      (§16.7 Option A implemented).
    - Fix 3 (§16.3, HIGH ✅): `HttpmdURLConnection.getInputStream()` now wraps `ByteArrayOutputStream`
      in a `CappedOutputStream(64 MB)` — Pack200 unpacking throws `IOException` if the
      decompressed JAR exceeds 64 MiB, preventing heap amplification.
    - `BasicInvocationHandler`: `CURRENT_ALL_METHOD` (static final `Method`) cached at class-load
      via `Subject.class.getMethod("currentAll")`; null on standard JDK.
    - `getAllUserSubjects()` uses the cached field (zero per-call reflection on std JDK);
      returns `Subject[]` outermost-first; falls back to `Subject.current()` single-element
      array on standard JDK.
    - `writeUserSubjects()` encodes `subjectCount:u16` + per-Subject `principalCount:u16 +
      (className:u16-prefixed-UTF8 + name:u16-prefixed-UTF8)` per principal.
    - `BasicInvocationDispatcher`: `CALL_AS_MULTI_SUBJECT` (static final `Method`) cached at
      class-load via `Subject.class.getMethod("callAs", Callable.class, Subject[].class)`;
      null on standard JDK.
    - `invokeWithClientSubject()`: on DirtyChai with >1 Subject, single varargs
      `CALL_AS_MULTI_SUBJECT.invoke(null, action, userSubjects)` call; on std JDK or 1
      Subject, `Subject.callAs(first, action)`.  `IllegalAccessException` re-thrown as
      `IllegalStateException` (should never occur — method is public).
    - `readUserSubjects()` decodes `subjectCount:u16` + per-Subject principal list; bounded
      by `MAX_USER_SUBJECTS=16` and `MAX_USER_PRINCIPALS=64`.
    - `UserSubjectImpl` now holds `Subject[]` (all wire-reconstructed user Subjects);
      `getUserSubjects()` returns a clone.
    - Bug fix: removed unreachable outer `catch (ClassNotFoundException)` in
      `AccessControlContextSerializer.unmarshalDigestFromTransport()`.
    - Tests: `MultiSubjectWireProtocolTest` — 6 tests pass (wire-protocol round-trip +
      `CURRENT_ALL_METHOD` caching + `getAllUserSubjects()` under `Subject.callAs`).
33. **ThreadGroup removal — migrate to `createPlatformThread`/`createVirtualThread`** — *(not yet started; documented in §17)*
    - Remove `systemThreadGroup` / `userThreadGroup` static fields from `NewThreadAction`;
      replace `new Thread(group, …)` in `run()` with `Thread.ofPlatform().name(…).stackSize(…).daemon(…).unstarted(runnable)`.
    - Drop `ThreadGroup` parameter from `TPThreadFactory` and `ThreadPool(ThreadGroup)` constructor;
      `GetThreadPoolAction` constructs `ThreadPool()` directly.
    - Delete `ThreadGroupAction` + `CreateThread` inner classes from `ReferenceProcessor.SystemThreadFactory`;
      replace with a single `AccessController.doPrivileged` block using `Thread.ofPlatform()`.
    - Remove `ThreadGroup group` field, `ThreadDesc(ThreadGroup, boolean[, int])` constructors, and `getGroup()`
      from `WakeupManager.ThreadDesc` (all callers already pass `null`).
    - Deprecate (`forRemoval=true`) the four `ThreadGroup`-accepting constructors in `InterruptedStatusThread`.
    - In all 16 QA harness `.policy` files: replace `RuntimePermission "modifyThreadGroup"` grants on
      `collections.jar` / `jeri.jar` with `RuntimePermission "createPlatformThread"`.
    - Update `ThreadPoolPermission` Javadoc — remove the never-implemented claim about
      `SecurityManager.checkAccess(ThreadGroup)`.

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
| **`Thread.scopedSubject` is `Subject[]` not single `Subject`** | ✅ **v19:** Mirrors `SCOPED_SUBJECT ScopedValue<Subject[]>`; full transaction array captured at construction; `runWith()` re-establishes all subjects via `callNoCheck(scopedSubject, action)`; `Subject.currentAll()` works correctly in spawned threads for multi-party transactions |
| **`getContext()` multi-subject loop with per-iteration `SubjectDomainCombiner`** | ✅ **v19:** Each `UserSubject` in the array gets its own `combine()` pass; principals accumulate progressively; final ACC contains all merged; `WorkerSubject` skipped; `existing` combiner preserved across all iterations; `privilegedContext` enriched per subject |
| **`SubjectAccess.get()` returns `Subject[]`** | ✅ **v19:** `NoCheck.current()` returns `Subject[]`; `SubjectAccess.get()` delegates to `current()`; consistent with `SCOPED_SUBJECT` type; no single-subject extraction at this level |
| **`DigestGrant extends URIGrant`** | ✅ **v20:** `DigestGrant` is a URI grant with an additional content-hash constraint; URI match must pass before digest is checked; extending `URIGrant` reuses all URI/certificate/principal matching without duplication; `toString()` naturally prepends `digest` before `codebase` following the hierarchy prepend pattern |
| **`digest "algorithm:hexValue"` single-token policy syntax** | ✅ **v20:** Algorithm and hex value colon-separated inside one quoted string; mirrors `httpmd:` URL convention; scanner stores raw string, parser splits on first `:` and hex-decodes; no grammar ambiguity — colon cannot appear unquoted in a grant header |
| **`DigestGrant.implies(ClassLoader, Principal[])` returns `false`** | ✅ **v20:** A `ClassLoader` carries no content hash; returning `false` (indeterminate) rather than delegating to `URIGrant` is the correct fail-secure behaviour; `URIGrant`'s existing override already handles that path correctly for plain URI grants |
| **`SecurityPolicyWriter` detects `DigestCodeSource` at write time** | ✅ **v20:** `DigestCodeSource instanceof` check at write time enables policy round-trip; `hasDigest \|\| hasPrincipals` controls comma placement ensuring valid grant header syntax regardless of which optional clauses are present |
| **Separate `digestTransportBytes` serial field for DigestCodeSource** | ✅ **v21:** Keeping `DigestCodeSource` domains in a dedicated field leaves the existing HTTPMD `transportBytes` wire format completely unchanged; older peers that do not understand the field receive `null` and silently skip it (fail-secure) |
| **`AtomicMarshalOutputStream`/`AtomicMarshalInputStream` for DigestCodeSource transport** | ✅ **v21:** `DigestCodeSource` implements `Externalizable` and writes only strings and bytes with built-in DOS guards; `AtomicMarshalInputStream` is the project-standard secure deserializer — `ObjectInputStream` is explicitly avoided everywhere in JGDMS |
| **`DomainIdentityRecord.from()` skips `DigestCodeSource` before httpmd URL test** | ✅ **v21:** A `DigestCodeSource` with an httpmd location must travel exclusively via `digestTransportBytes`; checking for `DigestCodeSource` first (via `cs instanceof Externalizable` + class-name) prevents it from being duplicated into `transportBytes` while adding zero overhead for ordinary `CodeSource` instances |
| **`equals`/`hashCode` on `AccessControlContextSerializer` + `DomainIdentityRecord`** | ✅ **v22:** Java serialization's handle table deduplicates by reference identity; implementing logical equality allows equal serializer instances to be recognised as the same object once a deduplication layer is applied, avoiding repeated full serialisation of identical ACCs; `cachedDigestBytes` ensures the relatively expensive `marshalDigestForTransport` is called at most once per instance across all `equals`/`hashCode` invocations |
| **Domain stripping is an implicit `doPrivileged`; fixed via `anonCount`** | ✅ **v23:** Every unverifiable `ProtectionDomain` stripped from a transmitted ACC removes a permission ceiling; the receiving JVM sees a strictly wider effective permission set — equivalent to an unchecked `doPrivileged` call.  Fixed in v23: `marshalForTransport()` appends `anonCount` so the receiver reconstructs anonymous placeholder domains that preserve the ceiling without asserting a specific identity claim.  `jrt:/java.base` excluded from `anonCount`. |
| **Unverifiable domains must have minimal permissions; `URLPermission` before `LoadPermission`** | ✅ **v23:** An unverifiable domain (plain URL or no codebase) cannot survive JERI transport with a verified identity; it is counted as anonymous on the wire.  The safe bootstrapping order is: (1) grant `URLPermission` so the code can be fetched; (2) let `SecureClassLoader` compute the SHA-256 into a `DigestCodeSource`; (3) grant `LoadPermission` only after the verified digest identity is established.  Granting `LoadPermission` before the digest exists is unsafe because the domain travels only as an anonymous placeholder on the receiver. |
| **ACC binary transport is a purpose-built compact encoding, not Java object serialization** | ✅ **v23:** The `transportBytes` format (4-byte count + per-domain records) and `writeUserSubjects()` format (u16 subjectCount + per-Subject u16 principalCount + u16-prefixed UTF-8 fields) avoid ObjectOutputStream overhead entirely; typical payload ~444 bytes/call; wire overhead is minor compared to TLS record framing |
| **`extractDomains()` uses a side-effect `doPrivileged`+`checkPermission` to drive `DomainCombiner.combine()`** | ✅ **v23:** The JVM only invokes `DomainCombiner.combine()` during a security stack walk; the innocuous `RuntimePermission("accessClassInPackage...")` check inside a restricted `doPrivileged` is the only portable way to trigger the walk without JDK internals; the `SecurityException` is intentionally swallowed; cost is 5–20 µs per call and is the dominant serialization overhead |
| **Pack200 (`.pack.gz`) for `-dl` proxy JAR download** | ✅ **v23:** Pack200+gzip achieves 40–60% size reduction over deflate-only JAR for class-file-heavy proxy JARs; decompression cost (~5–20 ms) is paid once per JVM lifetime per proxy class; `HttpmdURLConnection` SHA-256 verifies the packed stream before unpacking; the `httpmd:` URL in `DomainIdentityRecord` therefore references the packed artifact |
| **`CURRENT_ALL_METHOD` cached at class-load; null on standard JDK** | ✅ **v24:** `Subject.class.getMethod("currentAll")` cached once in a static initializer block; `NoSuchMethodException` → null (standard JDK). Zero per-call reflection cost on standard JDK; `Subject.current()` called directly. On DirtyChai the cached method returns the full `Subject[]` without allocation overhead. |
| **`getAllUserSubjects()` falls back to `Subject.current()` on std JDK** | ✅ **v24:** On a standard JDK where `CURRENT_ALL_METHOD == null`, returns `new Subject[]{Subject.current()}` (or empty array if null). Correct single-Subject behaviour maintained without code duplication. |
| **`CALL_AS_MULTI_SUBJECT` cached at class-load; null on standard JDK** | ✅ **v24:** `Subject.class.getMethod("callAs", Callable.class, Subject[].class)` cached once at class-load; null on standard JDK. Single-Subject path (`Subject.callAs(first, action)`) is used when the method is absent or there is only one Subject. |
| **Multi-Subject dispatch on DirtyChai uses single varargs `callAs`; nesting is wrong** | ✅ **v24:** Each nested `Subject.callAs(Subject, Callable)` call shadows the outer one; only the innermost Subject is visible via `Subject.current()`. DirtyChai's `Subject.callAs(Callable, Subject[])` varargs call passes all Subjects simultaneously to the JVM so `Subject.currentAll()` returns the full array. |
| **`IllegalAccessException` from `CALL_AS_MULTI_SUBJECT.invoke()` wrapped as `IllegalStateException`** | ✅ **v24:** The method is public; `IllegalAccessException` should never occur. Re-wrapping it as `IllegalStateException` (an `Exception`) honours the `Callable<Void>` contract and preserves the original cause in the stack trace. |
| **Unreachable outer `catch (ClassNotFoundException)` removed from `unmarshalDigestFromTransport()`** | ✅ **v24:** The exception is already caught per-domain by the inner try-catch around `amis.readObject()`; an outer catch is unreachable and is a compile error on JDK 27 (`-Werror`). Fixed by removing the outer try-wrapper; method already declares `throws IOException`. |
| **`writeUtf8Prefixed()` must reject strings > 65 535 UTF-8 bytes** | ✅ **v26:** Silent `Math.min` truncation removed; `IOException` thrown instead (§16.7 Option A).  No legitimate `Principal` implementation produces a name this long. |
| **`marshalForTransport()` early-return must check `anonCount == 0`** | ✅ **v25 (pending):** Returning empty bytes when `records.isEmpty()` but `anonCount > 0` silently drops anonymous-domain permission ceilings — same class of privilege escalation as §10.2.1.  Fix: guard on `records.isEmpty() && anonCount == 0`. |
| **Virtual-thread executor + semaphore cap for policy-service event delivery** | ✅ **v25 (recommended, pending):** `newVirtualThreadPerTaskExecutor()` prevents I/O blocking on platform threads; semaphore cap (500 permits) bounds in-flight deliveries; requires `<release>21</release>` in module `pom.xml`. |
| **`MAX_LISTENER_REGISTRATIONS` cap in `registerForPolicyUpdates()`** | ✅ **v25 (recommended, pending):** Any authenticated caller can flood the listener map; a hard cap (1 000) plus a daemon virtual-thread lease-expiry sweep are the two necessary controls. |
| **`ThreadGroup` is not a security boundary; `createPlatformThread` replaces `modifyThreadGroup` on DirtyChai** | ✅ **v27:** `ThreadGroup` was designed for applet sandbox isolation and was never an effective security boundary. On JDK 17–23, `SecurityManager` is deprecated for removal; on JDK 24+, `SecurityManager` is disabled — neither `modifyThreadGroup` nor `createPlatformThread` is checked at runtime on standard JDK builds; the cleanup is code hygiene. On **DirtyChai** (the preferred high-security platform), `SecurityManager` is still active; `Thread.ofPlatform().unstarted()` triggers `RuntimePermission("createPlatformThread")` and `Thread.ofVirtual().unstarted()` triggers `RuntimePermission("createVirtualThread")`. `BlockingSinkRegistry` already maps both `ThreadBuilders$PlatformThreadBuilder/unstarted` and `ThreadBuilders$VirtualThreadBuilder/unstarted` to those permissions. Replacing `new Thread(group, …)` with `Thread.ofPlatform().unstarted(…)` removes dead applet-era boilerplate on all platforms and creates the explicit DirtyChai policy gate. |

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

## 15. Performance Analysis — ACC Transmission & Pack200

### 15.1 ACC Transmission on Outbound JERI Calls

#### 15.1.1 When and why the ACC is sent

Every outbound JERI request uses protocol version `0x02` when the caller's
`AccessControlContext` contains at least one HTTPMD-verifiable `ProtectionDomain`.
The serialized ACC conveys the *remote process identity* of the caller — the
`WorkerSubject` principals baked into `ProtectionDomain`s at class-load time —
so the receiving server can evaluate `RemotePolicy` grants conditioned on both a
`SpiffePrincipal` (verified at the TLS layer) and a codebase URI (verified by the
SHA-256 HTTPMD hash).

#### 15.1.2 Wire payload size budget

The binary transport format in `AccessControlContextSerializer` is a purpose-built,
compact encoding — **not** Java object serialization.

**HTTPMD stream (`transportBytes`) layout:**
```
4 bytes  : domain count (big-endian int)
per domain:
  2 bytes : location URL byte length  (UTF-8, max 4 096 bytes)
  N bytes : location URL bytes
  2 bytes : principal count
  per principal:
    2 bytes : type class name byte length
    N bytes : type class name bytes
    2 bytes : principal name byte length
    N bytes : principal name bytes
```

**Typical real payload — 2 domains, 1 `SpiffePrincipal` each:**
- Location URL ≈ 115 bytes (`httpmd://repo.example.org/client-stub.jar#SHA256:…`)
- `SpiffePrincipal` class name ≈ 40 bytes; SPIFFE URI ≈ 60 bytes
- Per-domain size ≈ 2 + 115 + 2 + 2 + 40 + 2 + 60 = **223 bytes**
- 2 domains → 4-byte count prefix + 446 bytes ≈ **450 bytes** (`transportBytes`)

**User-principal block** (separate from ACC; only present when `Subject.current()` is
set via `Subject.callAs()`):
```
2 bytes  : principal count (u16, max 65 535; capped by MAX_USER_PRINCIPALS = 64)
per principal:
  2 bytes : class name byte length
  N bytes : class name bytes (UTF-8)
  2 bytes : principal name byte length
  N bytes : principal name bytes (UTF-8)
```
Typical JWT user: 1 principal, class name ≈ 50 bytes, JWT claim ≈ 100 bytes → ~154 bytes.

**Complete protocol-`0x02` header per request (example):**
```
1 byte  : version (0x02)
1 byte  : integrity flag
1 byte  : atomicValidation flag
154 bytes : user-principal block (1 JWT principal)
4 bytes : ACC block length prefix
450 bytes : ACC binary payload
= ~611 bytes added before the method + arguments
```

At 10 KB method arguments this is < 6% wire overhead.  At 100-byte micro-RPC arguments
it doubles the frame, but the absolute extra bytes are still within a single TLS record.

`MAX_ACC_BLOCK_BYTES = 1 MB` on the receiver side prevents denial-of-service
amplification from malformed senders.

**`digestTransportBytes`** (DigestCodeSource only): Absent on standard OpenJDK; adds
zero overhead unless the DirtyChai JDK's `DigestCodeSource` is in the ACC.

#### 15.1.3 CPU cost — sender (serialization)

`BasicInvocationHandler.invoke()` calls `AccessControlContextSerializer.marshalForTransport(currentAcc)`
once per outbound call.  The dominant cost is `extractDomains(acc)`, which forces a
JVM **security stack walk** to drive `DomainCombiner.combine()`:

```java
// extractDomains() — the stack walk
AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
    try {
        AccessController.checkPermission(
            new RuntimePermission("accessClassInPackage.java.lang"));
    } catch (SecurityException ignore) { }
    return null;
}, wrapped);   // wrapped ACC carries ExtractingDomainCombiner
```

The `SecurityException` is expected and intentionally swallowed.  The `checkPermission`
call is the only JVM-portable way to trigger the stack walk that invokes
`DomainCombiner.combine()` without JDK-internal reflection.

Measured cost of `AccessController.checkPermission` on HotSpot JDK 17–21:
- Shallow call stack (≤ 20 frames): ~1–3 µs
- Typical JERI dispatch thread (30–50 frames): ~5–15 µs
- Deep reflection-heavy stacks (> 80 frames): ~15–30 µs

The byte-encoding loop (O(domains × principals), pure array copies) is sub-microsecond.

**Total serialization CPU: ~5–20 µs per outbound call.**

#### 15.1.4 CPU cost — receiver (deserialization)

`BasicInvocationDispatcher` calls
`AccessControlContextSerializer.unmarshalForTransport(accBytes, getClientSubject())`
per inbound request.  Key costs:

1. **Bounds checking** — `MAX_TOTAL_PAYLOAD_BYTES` (16 MB), `MAX_DOMAIN_COUNT` (4 096),
   `MAX_PRINCIPALS_PER_DOMAIN` (256): O(1), negligible.
2. **Binary record parsing** — sequential `ByteArrayInputStream` reads + UTF-8 string
   decoding; `Uri.parseAndCreate(location)` for syntactic HTTPMD validation: ~2–5 µs
   per domain.
3. **`ProtectionDomain` / `CodeSource` / `URL` construction** — object allocation per
   domain; URL creation with the `IDENTITY_HANDLER` fallback when the httpmd: URL
   handler is not registered: ~1–3 µs per domain.
4. **`AccessControlContext` construction** from the domain array: O(N), ≤ 1 µs.

**Total deserialization CPU: ~10–50 µs for 2–4 domains.**

SHA-256 integrity verification for each `httpmd:` domain is **deferred** to the moment
the code is actually loaded by the classloader — it is not computed during per-call
deserialization.  The per-call deserialization path incurs no hash-computation cost.

#### 15.1.5 `doPrivileged(action, remoteIdentityContext)` dispatch overhead

In `invokeWithClientSubject`, when a valid ACC was received, the dispatch nesting is:

```java
// Outer: Subject.doAs(workerSubject, ...)   → installs worker identity on ACC
//   Middle: Subject.callAs(userSubject, ...) → ScopedValue for dispatch thread
//     Inner: AccessController.doPrivileged(action, remoteIdentityContext)
//              → restricts call stack to remote code domains
```

Each lambda is a small heap allocation; on JDK 21+ HotSpot they are typically
escape-analysed away.  The `doPrivileged` scope push/pop costs < 1 µs.

#### 15.1.6 Throughput summary

| Dimension | Cost | Notes |
|-----------|------|-------|
| Wire bytes added per request | ~450 bytes (ACC) + ~154 bytes (user principal) | < 1 TLS record overhead |
| Sender CPU — stack walk + encode | 5–20 µs/call | Stack walk dominates |
| Receiver CPU — parse + construct | 10–50 µs/call | URI parsing dominates |
| `doPrivileged` dispatch nesting | < 1 µs/call | JIT-optimized |
| `digestTransportBytes` (DigestCodeSource) | Cached after first call | Zero marginal cost |
| At 1 000 calls/s per thread | ~5–20 ms/s sender CPU | Acceptable |
| At 10 000 calls/s per thread | ~50–200 ms/s sender CPU | Noticeable; cache needed |

#### 15.1.7 `ContextCache` interaction (DirtyChai)

DirtyChai's `AccessControlContext.ContextCache` caches enriched ACCs keyed by
`ContextKey` (domain-array identity + `DomainCombiner` equality).  The v16 fix to
`ContextKey.equals()` (removed the inverted `!`) and the completion of
`SubjectDomainCombiner.equals()/hashCode()` ensure that `AccessController.getContext()`
returns the **cached** enriched ACC on subsequent calls from the same thread with the
same user Subject — not a freshly-walked one.

This indirectly reduces the cost of `extractDomains()`: the ACC passed to
`marshalForTransport()` is already the cached enriched form, so the stack walk inside
`extractDomains()` operates on a minimal, already-computed domain set rather than
triggering a second full enrichment.

#### 15.1.8 `equals`/`hashCode` deduplication (v22 optimization)

`AccessControlContextSerializer.equals()` compares the `domains[]` array
(via `DomainIdentityRecord.equals`) and the cached `digestBytes`.
`cachedDigestBytes` ensures `marshalDigestForTransport()` — which involves
`AtomicMarshalOutputStream` allocation and stream writing — is called **at most once
per ACC instance** regardless of how many `equals`/`hashCode` invocations occur.

Logical equality enables a deduplication layer above the raw stream to write
back-references instead of full records when equal `AccessControlContextSerializer`
instances are encountered more than once in a `writeObject` pass (e.g., multiple
proxies sharing the same codebase ACC).

#### 15.1.9 Identified performance gap — connection-level ACC cache

**Problem:** `extractDomains()` triggers a security stack walk on every outbound
JERI call, even when the caller's ACC is identical across calls.  For a long-lived
connection carrying many RPCs from the same client process, the ACC identity is stable
and the stack walk is redundant work.

**Proposed mitigation (Work Item 28):** Cache the serialized ACC bytes alongside a
weak reference to the source `AccessControlContext` in the connection/session state.
Invalidate only when the ACC reference changes (which happens when the user Subject
changes via `Subject.callAs()`).  This reduces per-call overhead to a single reference
comparison in the common case.

---

### 15.2 Pack200 Influence on Service Proxy JAR Size

#### 15.2.1 Where Pack200 is used

Pack200 appears in two distinct contexts:

**Context A — Service proxy download JARs (`httpmd:` codebase, `HttpmdURLConnection`):**
All service `-dl` JARs (reggie-dl, outrigger-dl, mahalo-dl, fiddler-dl, mercury-dl,
norm-dl, bytecode-analysis-engine-dl, verdict-registry-dl) are packed at build time by
the Maven `antrun` plugin invoking `au.net.zeus.util.jar.pack.Driver`.  The resulting
`*.jar.pack.gz` files are served at the `httpmd:` codebase URL.
`HttpmdURLConnection.getInputStream()` auto-detects `pack.gz` suffixes and unpacks
transparently after SHA-256 verification:

```java
if (pack200) {
    Pack200.Unpacker unpacker = Pack200.newUnpacker();
    ByteArrayOutputStream baos = new ByteArrayOutputStream(102400);
    JarOutputStream jout = new JarOutputStream(baos);
    unpacker.unpack(result, jout);          // result is the already-verified stream
    result = new JarInputStream(new ByteArrayInputStream(baos.toByteArray()));
}
```

The SHA-256 in the `httpmd:` URL is computed over the **packed** bytes, so the
`DomainIdentityRecord.location` stored in the ACC refers to the pack.gz artifact.
This is correct — `HttpmdURLConnection` verifies the packed stream, then unpacks it;
the code-source identity is preserved.

**Context B — `AnalysisRequest` (BAE pipeline):**
`AnalysisRequest` uses Pack200 to compress JAR bytes in-memory before JERI transport
from the Codebase Downloader (Host 4) to a BAE instance (Host 2).  Compression and
decompression are transparent via `@AtomicSerial`'s `serialize`/`check(GetArg)` hooks;
callers always see raw bytes via `getJarBytes()`.

#### 15.2.2 Pack200 compression ratios and proxy-JAR size impact

Pack200 exploits Java class-file structure (constant-pool sharing, bytecode token
encoding, repeated name patterns).  For Java `-dl` proxy JARs, which are
overwhelmingly `.class` files with minimal non-class resources:

| Artifact type | Deflate-only (plain JAR) | Pack200 + gzip | Reduction |
|---|---|---|---|
| Pure class-file proxy JAR | 100% (baseline) | ~40–55% | **45–60% savings** |
| JAR with non-trivial resources | 100% | ~60–75% | **25–40% savings** |
| `AnalysisRequest` (sent over JERI) | 100% | ~40–60% | **40–60% savings** |

`AnalysisRequest` documents these ratios via its constants:
```java
// Pack200 compresses by ~40–60%; half the input is a good initial buffer estimate.
private static final int PACK_INIT_CAPACITY_MARGIN = 256;
// Pack200 expands by roughly 2–3×; 3× is a conservative upper bound.
private static final int UNPACK_EXPANSION_FACTOR = 3;
```

**Concrete proxy-JAR example (illustrative):**
- `reggie-dl.jar` ≈ 45 KB plain → `reggie-dl.jar.pack.gz` ≈ 18–22 KB
- This represents a ~50% reduction in bytes transferred at first lookup.

#### 15.2.3 Performance implications of Pack200 in proxy download

**Bandwidth benefit:**
50% reduction is significant over any non-LAN path (WAN, edge, constrained IoT).
Over a 100 Mbit LAN the absolute saving (~24 KB) is < 2 ms; over a 1 Mbit WAN link
it is ~192 ms — a material improvement for first-use latency.

**CPU cost of Pack200 decompression:**
Decompressing a 20 KB `.pack.gz` → 45 KB JAR takes approximately 5–20 ms on modern
server hardware.  This cost is paid **once per JVM lifetime per proxy class** because
`ClassLoader` caches loaded classes.  Compared to the network RTT (1–100 ms on LANs,
50–500 ms on WANs), decompression is not the bottleneck.

**Peak memory during decompression:**
`HttpmdURLConnection.getInputStream()` materializes the entire decompressed JAR in a
`ByteArrayOutputStream(102400)` before returning a stream.  Using 3× packed size as
the expansion factor, a 1 MB `pack.gz` requires up to ~3 MB peak heap during
decompression.  For typical proxy JARs (< 200 KB packed), peak allocation is < 600 KB
— acceptable.

**Note:** `HttpmdURLConnection` buffers the entire unpacked JAR before returning the
stream.  A streaming unpacker that lazily decompresses would reduce peak memory for
large proxy JARs.  This is a quality-of-implementation opportunity, not a correctness
issue (Work Item 28 is higher priority).

#### 15.2.4 JDK compatibility — `pfirmstone/Pack200-ex-openjdk`

Pack200 was removed from the standard JDK in Java 14 (JEP 367).  JGDMS uses the
`au.net.zeus.pack200-ex-openjdk:Pack200-ex-openjdk:1.26.1` library
(`pfirmstone/Pack200-ex-openjdk`) as a drop-in replacement under the
`net.pack200.Pack200` API namespace.

- Declared in root `pom.xml` as `<pack200.version>1.26.1</pack200.version>`
- Compile-scope dependency in `jgdms-platform` and `jgdms-url-integrity`
- Build-time dependency in each `-dl` module's `antrun` pack step
- Published as a GitHub Release asset (not Maven Central); CI installs it via
  `mvn install:install-file` before the main build

#### 15.2.5 `AnalysisRequest` one-shot decompression pattern

`AnalysisRequest`'s `@AtomicSerial` deserialization constructor avoids double
decompression via the bridge-constructor pattern:

```java
// check(GetArg) decompresses exactly once; the unpacked bytes are passed directly
// to AnalysisRequest(GetArg, byte[]) — no second decompression needed.
public AnalysisRequest(GetArg arg) throws IOException, ClassNotFoundException {
    this(arg, check(arg));   // check() returns unpacked bytes
}
```

`getJarBytes()` always returns the raw, uncompressed bytes; Pack200 is invisible to
callers.

---

### 15.3 Cross-Cutting Observations

1. **ACC identity and `httpmd:` codebase URL are inseparable.**
   The `DomainIdentityRecord.location` stored in the ACC is the **pack.gz** URL.
   Any future change to the distribution format (e.g., switching to a plain JAR URL)
   would change the `ProtectionDomain` identity and require policy-file updates for
   all `grant codeBase "httpmd://..."` stanzas that reference the packed path.

2. **`DigestCodeSource` dual-path traversal.**
   `marshalForTransport()` and `marshalDigestForTransport()` both call
   `extractDomains(acc)`, each triggering one security stack walk per call.  On JVMs
   with `DigestCodeSource` domains in the ACC, this means two stack walks per outbound
   call.  Merging into a single pass (partition the domain list once, then write both
   streams) would halve the stack-walk overhead.  This is a straightforward
   optimization left for Work Item 28 / a future session.

3. **First-proxy-lookup cost profile.**
   The most expensive moment in a client's lifecycle is the first proxy lookup from the
   LUS.  At that point the client pays: (a) proxy JAR download + Pack200 decompression
   (~5–20 ms), (b) httpmd SHA-256 verification (amortized over one connection),
   (c) `VerifyingProxyPreparer` ACC serialization (~5–20 µs), and (d) a
   `RemotePolicyService.replace()` grant call.  All of these are one-time costs;
   steady-state per-call overhead is the ACC stack-walk only.

---

## 16. DoS Vectors and Architectural Fixes (v25 Analysis)

This section documents seven issues identified during an in-depth security and
correctness review of the v24 codebase.  For each issue multiple architectural fix
options are presented with their trade-offs; the **recommended** option is highlighted.

---

### 16.1 Unbounded Event Dispatch Queue (Policy Service) — ✅ COMPLETED (HIGH)

**Location:** `InMemoryPolicyServiceImpl` constructor, line 237–241.

**Problem:** The `ThreadPoolExecutor` uses `new LinkedBlockingQueue<Runnable>()`
(unbounded).  Every call to `replace()` enqueues one task per registered listener.
If listeners are slow (slow network path to remote `RemoteEventListener`), tasks
accumulate without limit, exhausting heap memory and hiding the true backlog depth.

**Option A — Bounded queue + `CallerRunsPolicy`**
Replace with `new LinkedBlockingQueue<>(MAX_DISPATCH_QUEUE)` (e.g. 10 000) and set
`RejectedExecutionHandler` to `CallerRunsPolicy`.  When the queue is full the calling
thread (the `replace()` caller's JERI dispatch thread) performs the delivery inline,
applying natural back-pressure to the `replace()` rate.

- **Pro:** simple, auto-throttles callers.
- **Con:** ties up a JERI dispatch thread during slow delivery; potential priority inversion.

**Option B — Bounded queue + `DiscardOldestPolicy` + warning log**
Use a bounded queue; silently drop the oldest pending task when the queue fills.
This is appropriate when events are purely notifications (clients poll `getCurrentGrants()`
anyway) and losing a notification is safe — clients will catch up on the next event.

- **Pro:** caller is never blocked; pool threads are never starved by slow receivers.
- **Con:** a slow listener may miss multiple notifications (acceptable given the
  pull-on-notification design: the next event will still trigger a `getCurrentGrants()` call).

**Option C — Virtual-thread-per-task executor (JDK 21+)**
Replace `ThreadPoolExecutor` with `Executors.newVirtualThreadPerTaskExecutor()`.
Each delivery task runs on a dedicated virtual thread; blocking I/O inside
`RemoteEventListener.notify()` does not pin a platform thread.  The executor's
internal queue is still unbounded in the default JDK implementation, but each
virtual thread is cheap (≈200 B heap) so the practical DoS threshold is much higher.

- **Pro:** maximally concurrent; no I/O blocking on platform threads; idiomatic JDK 21+.
- **Con:** unbounded virtual-thread count can still exhaust heap on a genuine flood;
  requires JDK 21 source/runtime compatibility in this module (currently `<release>8</release>`
  in root pom; override in module pom required as done for `jfr-telemetry-service`).

**Option D — Virtual-thread executor + semaphore limit (JDK 21+) — RECOMMENDED**
Use `Executors.newVirtualThreadPerTaskExecutor()` with an in-flight-task semaphore
(e.g. 500 permits).  The semaphore is acquired before `submit()`; if full,
`dispatchUpdateEvent()` skips that listener and logs a `WARNING`.

```java
// Sketch
private static final int MAX_IN_FLIGHT_DELIVERIES = 500;
private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT_DELIVERIES);
// ExecutorService eventDispatcher = Executors.newVirtualThreadPerTaskExecutor();

private void dispatchUpdateEvent() {
    for (ListenerRegistration reg : listenerRegistrations.values()) {
        if (!inFlight.tryAcquire()) {
            logger.warning("Event dispatch overloaded; skipping listener " + reg.leaseId);
            continue;
        }
        long seqNum = reg.seqNum.incrementAndGet();
        eventDispatcher.submit(() -> {
            try { new SendPolicyUpdateTask(reg, seqNum).run(); }
            finally { inFlight.release(); }
        });
    }
}
```

- **Pro:** bounded in-flight count; I/O does not pin platform threads; straightforward.
- **Con:** requires JDK 21 module compatibility.  Module `pom.xml` compiler release
  must be updated to 21 (same pattern as `jfr-telemetry-service`).

**Recommendation:** Implement Option D in a JDK-21-targeted sub-module or behind a
`Runtime.version()` guard.  As an interim for Java-8-compatible builds, use Option B
(bounded + `DiscardOldestPolicy`) since events are already pull-on-notification.

---

### 16.2 Unbounded Listener Registrations + Stale Expiry Retention — ✅ COMPLETED (HIGH)

**Location:** `InMemoryPolicyServiceImpl.registerForPolicyUpdates()`, line 348–364;
`listenerRegistrations` field, line 129–130.

**Problem:**
1. Any authenticated caller may register unlimited listeners.  Each registration
   occupies a `ConcurrentHashMap` entry.  A malicious or buggy client can call
   `registerForPolicyUpdates()` in a tight loop to exhaust heap; this does not require
   `PolicyPermission("Remote")` — only `replace()` does.
2. Expired registrations are only evicted when `SendPolicyUpdateTask.run()` finds
   `System.currentTimeMillis() > reg.leaseExpiration`.  If updates stop, the map retains
   expired entries indefinitely.

**Option A — Hard registration cap — RECOMMENDED**
Add `private static final int MAX_LISTENER_REGISTRATIONS = 1000;` and check at
entry to `registerForPolicyUpdates()`.  If the cap is reached, throw
`RemoteException("Too many listener registrations")`.

```java
if (listenerRegistrations.size() >= MAX_LISTENER_REGISTRATIONS) {
    throw new RemoteException("listener registration limit reached");
}
```

- **Pro:** immediate DoS bound; no background thread needed.
- **Con:** legitimate high-fan-out deployments may need a larger cap (should be configurable).

**Option B — Scheduled expiry-sweep virtual thread**
Add a daemon virtual thread (`Thread.ofVirtual().daemon(true).start(runnable)`) that
sweeps `listenerRegistrations` every 60 seconds and removes entries where
`leaseExpiration < System.currentTimeMillis()`.

```java
Thread.ofVirtual().daemon(true).name("JGDMS-PolicyService-LeaseSweep").start(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        try { Thread.sleep(Duration.ofMinutes(1)); } catch (InterruptedException e) { return; }
        long now = System.currentTimeMillis();
        listenerRegistrations.values().removeIf(r -> r.leaseExpiration < now);
    }
});
```

- **Pro:** handles stale-expiry retention cleanly; idiomatic JDK 21+.
- **Con:** the registration-flood DoS remains without Option A.

**Option C — Require `PolicyPermission("Remote")` for registration too**
Restrict `registerForPolicyUpdates()` to callers that hold `PolicyPermission("Remote")`.

- **Pro:** minimal code change; aligns permissions with operational intent.
- **Con:** may be too restrictive — service-tier clients legitimately register for
  updates and may not hold `PolicyPermission("Remote")`.

**Recommendation:** Implement **both A and B** (cap + sweep).  The cap is the primary
DoS defence; the sweep is hygiene.  Option C is a supplementary hardening step
appropriate once operational roles are clarified.

---

### 16.3 Pack200 Decompression Heap Amplification — ✅ COMPLETED (HIGH)

**Location:** `HttpmdURLConnection.getInputStream()`, line 140–146.

**Problem:** After SHA-256 verification passes, `Pack200.Unpacker.unpack()` writes the
decompressed JAR into a `ByteArrayOutputStream(102400)` with no upper bound.  A
legitimately-signed but large `.pack.gz` file can expand 3–5× on unpack, causing a
multi-hundred-megabyte heap spike during class loading.

**Option A — Capped `OutputStream` wrapper — RECOMMENDED**
Wrap `baos` in a size-capping `OutputStream` that throws `IOException` if the total
bytes written exceed `MAX_UNPACKED_JAR_BYTES` (e.g. 64 MB).

```java
private static final int MAX_UNPACKED_JAR_BYTES = 64 * 1024 * 1024; // 64 MB

static final class CappedOutputStream extends OutputStream {
    private final OutputStream delegate;
    private long written;
    private final long cap;
    CappedOutputStream(OutputStream delegate, long cap) {
        this.delegate = delegate; this.cap = cap;
    }
    @Override public void write(int b) throws IOException {
        if (++written > cap)
            throw new IOException("Unpacked JAR exceeds " + cap + " bytes");
        delegate.write(b);
    }
    @Override public void write(byte[] b, int off, int len) throws IOException {
        if ((written += len) > cap)
            throw new IOException("Unpacked JAR exceeds " + cap + " bytes");
        delegate.write(b, off, len);
    }
}
// Usage:
ByteArrayOutputStream baos = new ByteArrayOutputStream(102400);
JarOutputStream jout = new JarOutputStream(new CappedOutputStream(baos, MAX_UNPACKED_JAR_BYTES));
unpacker.unpack(result, jout);
```

- **Pro:** minimal change; fail-secure on oversized input.
- **Con:** cap must be generous enough for legitimate proxy JARs (typical < 5 MB packed → < 20 MB unpacked; 64 MB is safe headroom).

**Option B — Pre-check Content-Length before download**
Reject if `Content-Length > MAX_PACKED_JAR_BYTES` (e.g. 20 MB) before allocating.

- **Pro:** early rejection without allocating memory.
- **Con:** `Content-Length` is not guaranteed; server may not send it.

**Option C — Streaming unpacker (avoid full materialisation)**
Pipe the unpack output to a `PipedOutputStream`; return a `PipedInputStream`.

- **Pro:** eliminates full-JAR materialisation.
- **Con:** complex threading; deadlock risk; SHA-256 verification upstream already
  materialises the packed bytes.  Deferred to a later quality-of-implementation session.

**Recommendation:** Implement Option A immediately; add Option B as a cheap pre-check.

---

### 16.4 Remote-Controlled Principal Class Instantiation (CPU DoS) — ✅ COMPLETED (MEDIUM/HIGH)

**Location:** `BasicInvocationDispatcher.instantiatePrincipal()`, line 1867–1893.

**Problem:** For each principal in the wire block the server calls
`Class.forName(className, false, classLoader)` then `cls.getConstructor(String.class)
.newInstance(name)`.  With `MAX_USER_PRINCIPALS = 64` and `MAX_USER_SUBJECTS = 16`,
a single request can trigger up to 1 024 constructor invocations.  If any
bootstrap/system-classpath `Principal` implementation has an expensive `(String)`
constructor (e.g. one that performs hostname resolution or cryptographic parsing), an
attacker causes disproportionate CPU work per connection.

Additionally, `Class.forName` acquires the class-loading lock even for already-loaded
classes on some JVM implementations, creating contention under high concurrency.  On
JDK < 24, class-loading is `synchronized` and will pin a virtual-thread carrier if the
dispatch thread runs as a virtual thread.

**Option A — Allowlist + constructor cache — RECOMMENDED**
Maintain a `Map<String, Constructor<? extends Principal>>` populated eagerly at
class-load time from a set of permitted class names.  Wire lookups hit the map; unknown
names fall back to `RemotePrincipal` immediately with no `Class.forName` call.

```java
private static final Map<String, Constructor<? extends Principal>> PRINCIPAL_CTORS;
static {
    Set<String> allowed = Set.of(
        "javax.security.auth.x500.X500Principal",
        "javax.security.auth.kerberos.KerberosPrincipal",
        "net.jini.security.principal.SpiffePrincipal",
        "net.jini.security.principal.JwtPrincipal"
    );
    Map<String, Constructor<? extends Principal>> map = new HashMap<>();
    for (String name : allowed) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Principal> cls =
                (Class<? extends Principal>) Class.forName(name, false,
                    ClassLoader.getSystemClassLoader());
            map.put(name, cls.getConstructor(String.class));
        } catch (Exception ignored) { /* class not present on this JDK/classpath */ }
    }
    PRINCIPAL_CTORS = Collections.unmodifiableMap(map);
}

private static Principal instantiatePrincipal(String className, String name) {
    Constructor<? extends Principal> ctor = PRINCIPAL_CTORS.get(className);
    if (ctor == null) return new RemotePrincipal(className, name);
    try { return ctor.newInstance(name); }
    catch (Exception e) { return new RemotePrincipal(className, name); }
}
```

- **Pro:** zero per-request class-loading; no virtual-thread carrier pinning;
  `RemotePrincipal` fallback preserves wire data for auditing.
- **Con:** administrators using custom `Principal` types must add names to the set
  (can be made configurable via a system property).

**Option B — Drop class instantiation entirely; always use `RemotePrincipal`**
Never attempt `Class.forName`.  Return `RemotePrincipal(className, name)` always.

- **Pro:** zero class-loading risk; simplest code.
- **Con:** **Breaking policy change** — existing policy files that match on
  `principal X500Principal "..."` will not match `RemotePrincipal` instances.

**Recommendation:** Implement **Option A**.  It eliminates the DoS vector and virtual-
thread pinning risk while preserving full policy compatibility.

---

### 16.5 Per-Call ACC Stack-Walk (CPU Amplification) — ✅ COMPLETED (HIGH Security Bug, v27)

**Location:** `BasicInvocationHandler.invokeRemoteMethodOnce()`;
both `marshalForTransport()` and `marshalDigestForTransport()` in
`AccessControlContextSerializer` independently call `extractDomains()`.

**Performance problem (extends §15.1.9):** On DirtyChai JVMs with `DigestCodeSource`
domains in the ACC, two independent stack walks occur per outbound call.  At 10 000
calls/s per thread this costs 100–400 ms/s of stack-walk CPU.

**Security problem (TOCTOU context-confusion / impersonation):** The initial
implementation of Option B below used three separate `volatile` fields:

```java
// ❌ VULNERABLE — three-field "publish last" design
private transient volatile AccessControlContext cachedAccRef;
private transient volatile byte[] cachedTransportBytes;
private transient volatile byte[] cachedDigestBytes;
```

The "publish last" idiom (writing `cachedAccRef` after the byte arrays) is **not**
sufficient to prevent a race.  The JMM synchronisation order applies *per field*; it
is not jointly atomic across reads of distinct volatile fields.  The following
interleaving is permitted:

| Step | Thread B (cache reader) | Thread C (concurrent writer, different ACC) |
|------|------------------------|---------------------------------------------|
| 1 | — | writes `cachedTransportBytes = tb_C` |
| 2 | reads `cachedAccRef == ACC_A` → **hit** (Thread C's `cachedAccRef` not yet written) | — |
| 3 | reads `cachedTransportBytes` → gets **tb_C** (Thread C's bytes) | — |
| 4 | sends Thread C's ACC bytes to server | writes `cachedAccRef = ACC_C` |

Thread B passes the identity check against `ACC_A` but sends `ACC_C`'s (possibly
higher-privilege) bytes.  The server reconstructs Thread C's permission ceiling and
applies it to Thread B's request — **privilege escalation via context confusion**.

**Option A — Single-pass domain partition**
Merge `marshalForTransport` and `marshalDigestForTransport` into a single
`marshalAllForTransport(acc)` that calls `extractDomains(acc)` once, partitions
the resulting array, then writes both byte arrays.

- **Pro:** halves stack-walk cost; small, focused refactor.
- **Con:** does not eliminate repeated walks across calls; no help for stable-ACC case;
  does not address the TOCTOU race if three separate volatile fields are used.

**Option B — Connection-level ACC cache with immutable holder — IMPLEMENTED ✅**
Replace the three `volatile` fields with a single `volatile` reference to an
immutable `AccSerialCache` holder:

```java
// ✅ SAFE — single volatile reference to immutable holder
private static final class AccSerialCache {
    final AccessControlContext acc;
    final byte[] transportBytes;
    final byte[] digestBytes;
    AccSerialCache(AccessControlContext acc, byte[] tb, byte[] db) {
        this.acc = acc; this.transportBytes = tb; this.digestBytes = db;
    }
}
private transient volatile AccSerialCache accSerialCache;
```

In `invokeRemoteMethodOnce()`:

```java
final AccessControlContext currentAcc = AccessController.getContext();
AccSerialCache cache = accSerialCache;          // one volatile read → coherent triple
final byte[] serializedAcc;
if (cache == null || cache.acc != currentAcc) {
    byte[] tb = AccessControlContextSerializer.marshalForTransport(currentAcc);
    byte[] db = AccessControlContextSerializer.marshalDigestForTransport(currentAcc);
    accSerialCache = new AccSerialCache(currentAcc, tb, db); // one volatile write
    serializedAcc = tb;                         // always use local — never re-read cache
} else {
    serializedAcc = cache.transportBytes;       // same coherent snapshot
}
```

A single volatile read returns a fully consistent `(acc, transportBytes, digestBytes)`
triple; no concurrent writer can insert a partial update between the check and the use.
At 1 000 calls/s steady state: one stack walk per ACC change (rare) vs. 1 000/s
previously.  The volatile read + reference comparison costs < 10 ns.

---

### 16.6 ACC Ceiling Dropped When No Verifiable Domain Exists — ✅ COMPLETED (HIGH Security Bug)

**Location:** `AccessControlContextSerializer.marshalForTransport()`, lines 207–211.

**Problem:** When no HTTPMD-verifiable domain exists (e.g. the caller's ACC has only
unverifiable domains), `marshalForTransport()` returns an empty byte array early,
**before writing `anonCount`**.  The receiver sees empty bytes and calls
`unmarshalForTransport` → returns `null` → `invokeWithClientSubject` receives
`remoteIdentityContext = null` → no `doPrivileged` restriction applied.  The
`anonCount` anonymous-domain placeholder domains are silently lost, opening the same
privilege-escalation window that §10.2.1 closed.

**Early-return guard (the bug):**
```java
if (records.isEmpty()) {
    // No HTTPMD-verifiable domains to anchor the remote identity.
    return new byte[0];   // ← anonCount is never written
}
```

**Option A — Encode `anonCount` even when no HTTPMD records — RECOMMENDED**
Change the guard to only return early when **both** `records` and `anonCount` are zero:

```java
if (records.isEmpty() && anonCount == 0) {
    return new byte[0];  // truly empty — nothing to send
}
// Fall through: encode [httpmdCount=0][anonCount=N] or [httpmdCount=M][...][anonCount=N]
ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
writeInt(baos, records.size());
for (DomainIdentityRecord r : records) r.writeTo(baos);
writeInt(baos, anonCount);
return baos.toByteArray();
```

The receiver's `unmarshalHttpmdDomains()` already handles `count == 0, anonCount > 0`
(reconstructs N placeholder domains).

- **Pro:** closes the privilege-escalation gap; backward compatible (old receivers
  already return empty payloads when no HTTPMD records exist — this is additive).
- **Con:** adds ≤ 8 bytes to the wire payload in the uncommon case; negligible.

**Option B — Transmit `anonCount` as a new always-present field**
Wire-format change adding a mandatory anonymous-count field.

- **Pro:** explicit and clean.
- **Con:** wire format change; backward-compatibility complexity; strictly more work
  than Option A.

**Recommendation:** Implement **Option A** as a high-priority security fix (same
class as the §10.2.1 bug).

---

### 16.7 Silent Wire Truncation of Principal/Class-Name Strings — MEDIUM Bug

**Location:** `BasicInvocationHandler.writeUtf8Prefixed()`, line 1800–1804.

**Problem:** `Math.min(bytes.length, 0xFFFF)` silently truncates strings longer than
65 535 UTF-8 bytes.  The receiver reconstructs a different string, potentially causing
a wrong `RemotePrincipal` class name or matching an unintended policy principal.

**Current code:**
```java
int len = Math.min(bytes.length, 0xFFFF);
out.write((len >>> 8) & 0xFF);
out.write(len & 0xFF);
out.write(bytes, 0, len);        // truncated if bytes.length > 65535
```

**Option A — Reject at write time — RECOMMENDED**
```java
if (bytes.length > 0xFFFF) {
    throw new IOException(
        "Principal field too long for wire encoding (" + bytes.length + " bytes): "
        + s.substring(0, Math.min(40, s.length())) + "...");
}
```

- **Pro:** fail-fast; no ambiguous state.  All known `Principal` implementations use
  short names; the scenario cannot arise with legitimate data.
- **Con:** outbound call fails — appropriate because silent truncation is more dangerous.

**Option B — Sanitise (truncate + marker)**
Truncate to 65 500 bytes and append a `…` marker.

- **Pro:** call succeeds.
- **Con:** a truncated class name may silently match an unrelated class.  Worse than
  Option A from a security standpoint.

**Recommendation:** Implement **Option A**.

---

### 16.8 Virtual Thread Opportunities Summary (JDK 21+)

| Area | Current design | Virtual-thread option |
|---|---|---|
| Policy-service event delivery | Platform `ThreadPoolExecutor` (max 10 threads) | `newVirtualThreadPerTaskExecutor()` + semaphore cap (§16.1 Option D) |
| JERI dispatch thread | Platform thread pool (JERI-managed) | Transparent; blocking inside `invokeWithClientSubject` does not pin a carrier on JDK 24+ |
| `instantiatePrincipal()` class-loading | Blocks class-loading lock (pinning carrier on JDK < 24) | Eliminated by allowlist + constructor cache (§16.4 Option A) |
| Lease expiry sweep | Not implemented | Daemon virtual thread: `Thread.ofVirtual().daemon(true).start(sweepRunnable)` |
| Pack200 decompression | Full materialisation (`ByteArrayOutputStream`) | Bounded output stream cap (§16.3); streaming deferred |

**Module compatibility note:** To use `Executors.newVirtualThreadPerTaskExecutor()`
and `Thread.ofVirtual()`, the policy-service Maven module must declare
`<release>21</release>` in its `maven-compiler-plugin` configuration, overriding
the root pom's `<release>8</release>`, matching the pattern used by
`jfr-telemetry-service/pom.xml`.

---

## 17. ThreadGroup Removal — Security Enforcement Migration

### 17.1 Background and Motivation

`ThreadGroup` was designed for applet sandbox isolation and was **never an effective
security boundary**.  The relevant method, `SecurityManager.checkAccess(ThreadGroup)`,
has been a no-op in every standard JDK since JDK 17 (and is only non-trivial if a
`SecurityManager` is installed).  `ThreadGroup.getParent()` traversal (used in
`NewThreadAction` and `ReferenceProcessor`) requires `RuntimePermission("modifyThreadGroup")`,
but the only thing that privilege buys is the ability to name the thread group that a
new thread is placed in — a completely irrelevant capability for a middleware security
framework.

**Platform context: three distinct states across JDK versions**

| Deployment target | SecurityManager status | `createPlatformThread` enforced? |
|---|---|---|
| **JDK 17–23** | Deprecated for removal (present but no-op unless explicitly installed) | **No** — permission is never checked; cleanup is code hygiene only |
| **JDK 24+** | Disabled — `SecurityManager` API throws `UnsupportedOperationException` | **No** — permission is never checked; cleanup is code hygiene only |
| **DirtyChai (preferred high-security platform)** | Active; all `AccessController` and `SecurityManager` checks are functional | **Yes** — `Thread.ofPlatform().unstarted()` triggers `RuntimePermission("createPlatformThread")` via DirtyChai's SecurityManager |

JGDMS's high-security deployment model targets **DirtyChai** as the primary platform.
Standard JDK deployments still benefit from removing the dead `ThreadGroup` code
(simpler, no root-group walk, no applet-era `PrivilegedAction` boilerplate), but the
permission-gate motivation below applies to DirtyChai deployments.

**Security gates in JGDMS for thread creation**

- `GetThreadPoolAction` and `ThreadPoolPermission` enforce that only code holding
  `ThreadPoolPermission("getSystemThreadPool")` or `ThreadPoolPermission("getUserThreadPool")`
  can obtain a thread pool.  That gate is `AccessController`-based and is enforced on
  both DirtyChai and vanilla JDK deployments.
- On **DirtyChai**: JDK 21 `Thread.ofPlatform()` / `Thread.ofVirtual()` builders call
  `ThreadBuilders$PlatformThreadBuilder.unstarted()` /
  `ThreadBuilders$VirtualThreadBuilder.unstarted()`, which DirtyChai guards with
  `RuntimePermission("createPlatformThread")` and `RuntimePermission("createVirtualThread")`
  respectively.
- `BlockingSinkRegistry` (bytecode-analysis-engine) already maps both sink methods to
  those permissions (see `SINK_TO_PERMISSION_CLASS`), so the static-analysis pipeline
  flags code that calls them without the matching grant.

Replacing `new Thread(group, …)` with `Thread.ofPlatform().unstarted(…)` therefore
**removes dead applet-era boilerplate** on all platforms and **automatically creates
an explicit DirtyChai policy gate** without any additional change.  No
`modifyThreadGroup` grant is needed in any policy file once this migration is complete.

---

### 17.2 Affected Sites

| File | ThreadGroup role | Line(s) |
|---|---|---|
| `jgdms-collections/…/thread/NewThreadAction.java` | `systemThreadGroup` / `userThreadGroup` static fields (root-group walk at class-load); `group` instance field; `new Thread(group, r, name, stackSize)` | 49–71, 77, 140 |
| `jgdms-collections/…/thread/ThreadPool.java` | `TPThreadFactory(ThreadGroup)` constructor; `threadGroup` field; delegates to `NewThreadAction(threadGroup, r, …)` | 97, 197–208 |
| `jgdms-collections/…/thread/GetThreadPoolAction.java` | `new ThreadPool(NewThreadAction.systemThreadGroup)` / `new ThreadPool(NewThreadAction.userThreadGroup)` | 53, 57 |
| `jgdms-collections/…/thread/ThreadPoolPermission.java` | Javadoc: *"permission to access the thread group"* / *"SecurityManager.checkAccess(ThreadGroup)"* | 36–38 |
| `jgdms-collections/…/concurrent/ReferenceProcessor.java` | `SystemThreadFactory`: `ThreadGroupAction` (privileged root-group walk) + `CreateThread` (group-bound `new Thread`) | 267–322 |
| `jgdms-platform/…/thread/wakeup/WakeupManager.java` | `ThreadDesc.group` field; `ThreadDesc(ThreadGroup, boolean)` / `ThreadDesc(ThreadGroup, boolean, int)` constructors; `getGroup()` method; `ThreadDesc.thread()` branches on `getGroup() == null` | 179–268 |
| `jgdms-collections/…/thread/InterruptedStatusThread.java` | Public `InterruptedStatusThread(ThreadGroup, …)` constructors | (public API — deprecate only) |
| 16 × `qa/harness/policy/*.policy` | `permission java.lang.RuntimePermission "modifyThreadGroup"` on `collections.jar` / `jeri.jar` | see §17.7 |

---

### 17.3 Site-by-Site Analysis and Options

#### 17.3.1 `NewThreadAction` + `ThreadPool` + `GetThreadPoolAction`

These three classes form a single unit.  `NewThreadAction.run()` is the only place
the actual `Thread` is constructed.

**Option A — `Thread.ofPlatform()` builder (recommended)**

Replace the `new Thread(group, r, name, stackSize)` call in `NewThreadAction.run()` with:

```java
Thread t = Thread.ofPlatform()
    .name(NAME_PREFIX + name)
    .stackSize(stackSize)
    .daemon(daemon)
    .unstarted(runnable);
t.setContextClassLoader(ClassLoader.getSystemClassLoader());
```

- The `Thread.ofPlatform()` call triggers `RuntimePermission("createPlatformThread")`
  in DirtyChai — the correct explicit gate.
- Remove `systemThreadGroup` / `userThreadGroup` static fields.  The root-group walk
  `AccessController.doPrivileged` block in their initializers is eliminated.
- Remove the `ThreadGroup group` instance field and the two package-private
  `NewThreadAction(ThreadGroup, …)` constructors.  The two public constructors
  retain their signatures unchanged; the `user` boolean parameter becomes a no-op
  (document in Javadoc for backward source-compatibility).
- Remove `getClassLoaderPermission` field and the `sm.checkPermission(…)` call in
  `run()` — it existed solely to satisfy the `ThreadGroup` access check and
  duplicates the `createPlatformThread` guard now provided by the builder.
- `TPThreadFactory.newThread(r)` removes the `ThreadGroup threadGroup` field and
  constructor parameter; delegates to the parameterless two-arg form.
- `ThreadPool(ThreadGroup)` → replaced by `ThreadPool()` (calls the private
  `ThreadPool(ExecutorService)` with `Executors.newCachedThreadPool(new TPThreadFactory())`).
- `GetThreadPoolAction`: both `systemPool` and `userPool` singletons call `new ThreadPool()`.

*Pros:* Minimal change; two-pool split and `ThreadPoolPermission` gate preserved;
no caller API changes; `jgdms-collections` module release stays at Java 8 (builder
API is back-compiled-compatible if the JDK 21 method reference is behind a conditional
or detected via reflection; alternatively bump the module release to 21).

*Cons:* `jgdms-collections` must target Java 21 (or use a reflection shim) for
`Thread.ofPlatform()`.

**Option B — Remove `user` boolean and merge into single pool**

Abolish the two-pool distinction entirely: both `getSystemThreadPool` and
`getUserThreadPool` return the same `ExecutorService` backed by a single
`newCachedThreadPool`.  The `ThreadPoolPermission` names are kept for policy
compatibility.

*Pros:* Simplest code; no ThreadGroup or group walk at all.
*Cons:* Loses the observable naming distinction in thread dumps.  The `user` flag was
never a security boundary so this is a no-op security-wise, but it is a visible
behavioral change.

**Option C — Reflect `Thread.ofPlatform()` for JDK 8–20 compatibility**

Use `Method` reflection (`Thread.class.getMethod("ofPlatform")`) at class-load,
falling back to `new Thread(r, name)` on older JDKs.

*Pros:* Zero module-release bump.
*Cons:* Adds complexity; JGDMS already requires JDK 21 for the `jgdms-platform`
module; inconsistency is confusing.  Not recommended.

**Recommendation: Option A.**  Bump `jgdms-collections` module `pom.xml` from
`<release>8</release>` to `<release>21</release>` matching the pattern already used
by `jgdms-platform` and `policy-service`.

---

#### 17.3.2 `ReferenceProcessor.SystemThreadFactory`

`SystemThreadFactory` contains two private inner classes — `ThreadGroupAction`
(performs a privileged root-group `getParent()` walk) and `CreateThread` (constructs
`new Thread(g, r, "Reference collection cleaner")`).

**Option A — `Thread.ofPlatform()` (recommended)**

Delete `ThreadGroupAction` and `CreateThread`.  Replace `newThread(r)` with:

```java
public Thread newThread(Runnable r) {
    return AccessController.doPrivileged((PrivilegedAction<Thread>) () -> {
        Thread t = Thread.ofPlatform()
            .name("Reference collection cleaner")
            .priority(Thread.MAX_PRIORITY)
            .unstarted(r);
        t.setContextClassLoader(null);
        return t;
    });
}
```

The `AccessController.doPrivileged` is still required so that the
`createPlatformThread` check uses the system domain (not caller code's domain).
The `SecurityException` catch blocks in `CreateThread.run()` become unnecessary
because `Thread.ofPlatform()` does not throw them on attribute setting.

**Option B — Delegate to `NewThreadAction`**

Use `AccessController.doPrivileged(new NewThreadAction(r, "GC", false))`.  This
keeps all thread-creation logic in one place.

*Pros:* Consistent; any future changes to `NewThreadAction` propagate automatically.
*Cons:* `NewThreadAction` is in a different package; requires a cross-package
dependency that already exists.

**Recommendation: Option B.**  Delegating to `NewThreadAction` keeps thread-creation
policy centralised and ensures both the `ReferenceProcessor` and the JERI thread pool
go through exactly the same `createPlatformThread` gate.  Update `priority` and
`contextClassLoader` on the returned `Thread` after the `doPrivileged` call.

---

#### 17.3.3 `WakeupManager.ThreadDesc`

`ThreadDesc` exposes a `ThreadGroup group` field (always `null` in every call site
in the codebase) and two constructors that accept it.  The `thread(Runnable)` method
branches on `getGroup() == null`:

```java
if (getGroup() == null)
    thr = new Thread(r);
else
    thr = new Thread(getGroup(), r);
```

Since every call site passes `null`, the `getGroup()` branch is never taken in
production.

**Option A — Remove `ThreadGroup` field and dead constructors (recommended)**

- Remove `ThreadGroup group` field, `ThreadDesc(ThreadGroup, boolean)`,
  `ThreadDesc(ThreadGroup, boolean, int)`, and `getGroup()`.
- Replace both `new Thread(…)` lines with:
  ```java
  Thread thr = Thread.ofPlatform()
      .name("WakeupManager-kicker")
      .daemon(isDaemon())
      .priority(getPriority())
      .unstarted(r);
  ```
- The no-arg `ThreadDesc()` constructor and the `isDaemon()` / `getPriority()`
  accessors remain unchanged.

*Pros:* Dead code eliminated; `thread(Runnable)` becomes a single-branch method;
`createPlatformThread` gate is enforced where previously there was no check at all.

**Option B — Keep constructors, deprecate `ThreadGroup` parameter**

Annotate the two `ThreadGroup`-accepting constructors with
`@Deprecated(since="3.1.0", forRemoval=true)`, ignore the `group` parameter
internally, and add a Javadoc note.

*Pros:* Binary + source compatibility if external code uses these constructors.
*Cons:* Leaves dead code; still needs `Thread.ofPlatform()` for the `thread()`
method.

**Option C — Virtual thread**

Replace `Thread.ofPlatform()` with `Thread.ofVirtual()` for kicker threads.
Kicker threads are short-lived and mostly sleeping; virtual threads are ideal.
The gate becomes `createVirtualThread`.

*Pros:* Lower memory footprint for many timers.
*Cons:* Changes observable behavior (thread dump appearance, thread type).

**Recommendation: Option A** — remove dead code; use `Thread.ofPlatform()`.
Option C (virtual threads) is a desirable follow-on but should be a separate
work item.

---

#### 17.3.4 `InterruptedStatusThread`

`InterruptedStatusThread` has four public constructors that accept a `ThreadGroup`:

```java
public InterruptedStatusThread(ThreadGroup group, Runnable target, String name)
public InterruptedStatusThread(ThreadGroup group, String name)
// … two more
```

No production code in JGDMS calls these constructors.  They are part of the public
API surface.

**Option A — Deprecate (`forRemoval=true`) immediately; remove in next major version**

Annotate each constructor with `@Deprecated(since="3.1.0", forRemoval=true)` and
document the group-free alternatives.

*Pros:* Standard deprecation cycle; no binary break.
*Cons:* Dead code persists for one release cycle.

**Option B — Remove immediately**

No known external consumers; `grep` of the entire JGDMS tree confirms zero calls.

*Pros:* Clean.
*Cons:* Potentially breaks third-party code compiled against JGDMS.

**Recommendation: Option A** — deprecate now, remove in the next major API version.

---

### 17.4 Policy File Changes

Every `.policy` file that grants `RuntimePermission "modifyThreadGroup"` to
`collections.jar` and/or `jeri.jar` must be updated.  The grant is replaced by
`RuntimePermission "createPlatformThread"`.  The `RuntimePermission "modifyThread"`
grants (for `Thread.interrupt()` / priority adjustment) are **not changed**.

| Policy file | Current grant | Replacement |
|---|---|---|
| `qa/harness/policy/defaultsecuretest.policy` (lines 155, 515, 974) | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultsecuregroup.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultsharedvm.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultsecuresharedvm.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaulttest.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultnonactvm.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultsecuremahalo.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultsecurephoenix.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultsecureoutrigger.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultspiffesharedvm.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultspiffetest.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultspiffegroup.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultspiffemahalo.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultspiffephoenix.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `defaultspiffeoutrigger.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |
| `qa.policy` | `"modifyThreadGroup"` | `"createPlatformThread"` |

---

### 17.5 What Does NOT Change

| Component | Reason |
|---|---|
| `ThreadPoolPermission` permission names (`getSystemThreadPool`/`getUserThreadPool`) | These are the real ACC-based security gates; they are preserved unchanged. |
| Two-pool split (`systemPool` / `userPool`) | Still useful for thread-dump diagnostics; the naming is preserved via `Thread.ofPlatform().name(…)`. |
| `RuntimePermission "modifyThread"` grants | Unrelated — covers `Thread.interrupt()` and `Thread.setPriority()`; nothing in this plan requires changing them. |
| JERI caller sites (`JvmLifeSupport`, `AbstractDgcClient`, `ImplRefManager`, `ObjectTable`) | All already call the group-free two-arg `NewThreadAction(Runnable, String, boolean)` public constructor; no caller-side changes needed. |
| `jgdms-platform` module release | Already at 21; no change needed. |

---

### 17.6 Module Release Requirements

| Module | Current `<release>` | Required after change |
|---|---|---|
| `jgdms-collections` | 8 | **21** (for `Thread.ofPlatform()`) |
| `jgdms-platform` | 21 | 21 (unchanged) |
| All service modules using `jgdms-collections` | depends | 21 via transitive dep; no source change |

---

### 17.7 Summary of Permission Change

On **DirtyChai** deployments, code that formerly required:
```
permission java.lang.RuntimePermission "modifyThreadGroup";
```
will instead require:
```
permission java.lang.RuntimePermission "createPlatformThread";
```

This is strictly more expressive: `modifyThreadGroup` described an applet-era
capability that was never a meaningful security gate in this context.
`createPlatformThread` describes exactly what the code does — creates a new
OS-scheduled thread — and is enforced by DirtyChai's SecurityManager.

On **standard JDK 17–23** deployments, `SecurityManager` is deprecated for removal and
neither `modifyThreadGroup` nor `createPlatformThread` is checked at runtime.  On
**JDK 24+**, `SecurityManager` is fully disabled and throws `UnsupportedOperationException`
if instantiated.  The policy-file change is nonetheless made for consistency and
correctness on all standard JDK builds: the grants reflect actual capabilities rather
than obsolete applet-era constructs.

For future virtual-thread adoption (e.g., kicker threads in `WakeupManager`,
lease-expiry sweepers), the matching DirtyChai permission is
`RuntimePermission "createVirtualThread"`.

---


- *§17 new: ThreadGroup removal plan — three architecture options per site, ranked recommendations, policy-file change table*
- *§12 work item 33 added — ThreadGroup removal (not yet started)*
- *§13 new row — `ThreadGroup` is not a security boundary; `createPlatformThread` replaces `modifyThreadGroup`*

*Hand this document (along with source files as needed) to a future AI agent to
continue without loss of context. This is version 27, updated to document:*

- *§16.5 ✅ completed: `BasicInvocationHandler` — `AccSerialCache` immutable holder fixes TOCTOU context-confusion race; Work Item 28 complete*
- *§12 Work Item 28 — marked ✅ completed (v27) with full security rationale*

---

*Previous version (v26) notes:*
- *§16.1 ✅ completed: `InMemoryPolicyServiceImpl` — virtual-thread executor + `Semaphore(500)` cap*
- *§16.2 ✅ completed: `InMemoryPolicyServiceImpl` — `MAX_LISTENER_REGISTRATIONS=1000` cap + daemon sweep*
- *§16.3 ✅ completed: `HttpmdURLConnection` — `CappedOutputStream(64 MB)` wrapping Pack200 output*
- *§16.4 ✅ completed: `BasicInvocationDispatcher` — `PRINCIPAL_CTORS` allowlist + constructor cache*
- *§16.6 ✅ completed: `AccessControlContextSerializer.marshalForTransport()` — `anonCount` now encoded even when no HTTPMD records*
- *§16.7 ✅ completed: `BasicInvocationHandler.writeUtf8Prefixed()` — throws `IOException` instead of silently truncating strings > 65 535 UTF-8 bytes (§16.7 Option A)*
- *§16.5 (MEDIUM) remains not yet started*
- *Work items 30 (partially), 31, and 32 (partially) marked complete in §12*

---

*Previous version (v23) notes:*
- *`AccessControlContextSerializer.marshalForTransport()` — `anonCount` transport for non-verifiable domains*
- *`jrt:/java.base` excluded from anonCount; other jrt: domains retained*
- *§15 Performance Analysis added (ACC transmission, Pack200)*
- *Work Item 28 — connection-level ACC cache identified*

---

*Previous version (v19) notes:*
- *Two `SpiffeCredentialManager` implementations (JGDMS standard API vs DirtyChai
  bootstrap sealed `SpiffeSubject`)*
- *`Thread.scopedSubject` field changed from `Subject` to `Subject[]`*
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