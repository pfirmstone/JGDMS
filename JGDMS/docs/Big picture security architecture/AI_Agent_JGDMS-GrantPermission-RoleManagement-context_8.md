# JGDMS — GrantPermission, Role Management & Full Architecture — AI Agent Context (v10)

**Purpose:** This document captures the full conversation context for an AI agent to
continue work on JGDMS role management and `GrantPermission` design without loss of
context. It supersedes and extends the previous context documents.

**GitHub repositories:**
- JGDMS: https://github.com/pfirmstone/JGDMS
- DirtyChai: https://github.com/pfirmstone/DirtyChai

---

## 1. Documents Read This Session

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
| `SubjectDomainCombiner.java` | DirtyChai source | ✅ **Implemented:** `getMergedPrincipals()` reads SCOPED_SUBJECT on every `combine()` call; additively merges ACC Subject principals + human user principals; no AuthPermission check (trusted java.base); null-safe via `ScopedValue.isBound()` |
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

### 5.4.1 `Security.getCurrentPrincipals()` — User-Subject-First Resolution

When `principals == null` in `VerifyingProxyPreparer`, `Security.grant(Class, Permission[])` is
called, which invokes the private `Security.getCurrentPrincipals()` helper.  As of this release,
that helper resolves principals in the following priority order:

1. **`Subject.current()`** — the user `Subject` bound via `Subject.callAs()` (a `ScopedValue`).
   This is non-null when the grant occurs from inside a JERI dispatch thread (server-side) or
   any other `callAs` scope.
2. **`Subject.getSubject(AccessController.getContext())`** — the workload (SPIFFE) `Subject`
   on the `AccessControlContext`.  Used as a fallback when no user Subject is bound.

**Consequence:** `VerifyingProxyPreparer` used from a JERI dispatch thread now automatically
scopes grants to the authenticated remote *user's* principals (not the server's workload
principals), with zero call-site changes.  Service code that calls `prepareProxy()` inside a
`callAs(userSubject, ...)` scope inherits this behaviour transparently.

### 5.4.2 `GrantPermission.checkGuard(Object)` — User-Subject-Aware Guard

`GrantPermission` now overrides `Permission.checkGuard()` with a `final` method:

```java
@Override
public final void checkGuard(Object object) throws SecurityException {
    SecurityManager sm = System.getSecurityManager();
    if (sm == null) return;
    Subject user = Subject.current();
    if (user != null) {
        Subject.doAs(user, (PrivilegedAction<Void>) () -> {
            sm.checkPermission(this);
            return null;
        });
    } else {
        sm.checkPermission(this);
    }
}
```

When a user Subject is bound (`Subject.current() != null`), the check is performed inside
`Subject.doAs(user, …)` so the user's principals are injected into the `AccessControlContext`
for the duration of `checkPermission`.  This allows `GrantPermission` checks to honour
policy grants that are scoped to user principals (e.g., a grant scoped to both a SPIFFE
principal and a `KerberosPrincipal`).

Falls back to a direct `checkPermission` call when no user Subject is bound (daemon threads,
non-request contexts).

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

## 6. DirtyChai Security Model — Relevant Constraints

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

### 6.4 Subject API — Two Carriers, One Combined View

| Carrier | Identity type | Established by | Lifetime |
|---|---|---|---|
| `AccessControlContext` + `SubjectDomainCombiner` | SPIFFE workload identity | `doAsPrivileged` at service start | JVM / SVID rotation |
| `ScopedValue` (`SCOPED_SUBJECT`) | Human user identity (Kerberos, etc.) | `callAs` at request boundary | Duration of `Callable` |

**Public API surface:**

| Method | Guard | Effect |
|---|---|---|
| `Subject.doAs(Subject, PrivilegedExceptionAction)` | none | Installs Subject onto ACC via `SubjectDomainCombiner` |
| `Subject.doAsPrivileged(Subject, PrivilegedExceptionAction, AccessControlContext)` | none | As above with explicit ACC |
| `Subject.getSubject(AccessControlContext)` | `AuthPermission("getSubject")` | Retrieves SPIFFE Subject from ACC |
| `Subject.callAs(Subject, Callable)` | none | Binds Subject to `SCOPED_SUBJECT` for duration of Callable |
| `Subject.current()` | `AuthPermission("getSubject")` | Retrieves human Subject from `SCOPED_SUBJECT` |

**SubjectDomainCombiner — combined view on every checkPermission:**
1. Reads ACC-bound SPIFFE principals
2. Reads `SCOPED_SUBJECT` directly — no `AuthPermission` check (trusted `java.base`)
3. Additively injects human user principals alongside SPIFFE principals
4. If `SCOPED_SUBJECT` unbound — no change; daemon threads are unaffected

**What this enables:**

```
grant principal SpiffePrincipal "spiffe://.../svc/order-processor"
      principal KerberosPrincipal "alice@EXAMPLE.ORG" {
    permission ...;
};
```

Both must be present. A grant requiring only SPIFFE still fires without a user present.

**Structural discipline:** Daemon threads (sweeper, SPIRE watcher, log writer) must not be spawned from within a `callAs` scope.

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

### 8.2 Human User Identity — Kerberos and Traditional JAAS

Human users authenticate via traditional JAAS mechanisms. The resulting Subject is installed per-request via `Subject.callAs(userSubject, () -> ...)`. `SubjectDomainCombiner` reads `SCOPED_SUBJECT` internally and injects human principals into every `checkPermission` for the duration of the request.

### 8.3 Traditional Jini LoginContext

`AbstractJiniService` supports traditional Jini pattern: if a `LoginContext` is
configured, `start()` calls `loginContext.login()` and runs `doStart()` inside
`Subject.doAsPrivileged`. For SPIFFE-authenticated services, `loginContext` is `null`.

### 8.4 DirtyChai Administrator

DirtyChai runs as a workload with `spiffe://.../admin/policy` SVID. Human authenticates to the machine via OS/SSH. DirtyChai's SPIFFE identity gates access to `InMemoryPolicyService.replace()`.

---

## 9. ServiceUI — Design Now Resolved

ServiceUI runs inside `callAs(kerberosSubject, () -> ...)` which is itself inside the
`doAsPrivileged(spiffeSubject, ...)` workload context. `SubjectDomainCombiner` sees both
Subjects and injects both principal sets into `checkPermission`. Policy can condition
grants on both the workload identity (which ServiceUI JAR) and the human identity (which user).

ServiceUI JAR is a separate codebase with independent BAE audit, `RegistryVerdict`,
`ProxyCodebaseSpi` gate, and ClassLoader.

---

---

## 10. Two-Subject JERI Implementation — Worker and User Subjects

This section documents the completed implementation of the two-Subject identity model
across `BasicInvocationHandler`, `BasicInvocationDispatcher`, `ClientUserSubject`, and
`SubjectDomainCombiner`.  All code listed here is **implemented and reviewed** as of v8.

### 10.1 The Two Identities

| Identity | Name | Established by | Carried to server | Lifetime |
|---|---|---|---|---|
| Workload/process identity | **worker Subject** | `Subject.doAs(subject, privilegedAction)` at service startup | TLS certificate chain (SPIFFE SVID X.509) | JVM / SVID rotation |
| Human user identity | **user Subject** | `Subject.callAs(userSubject, callable)` per request | In-band in JERI wire header (protocol v`0x02`) | Duration of the `Callable` |

The worker Subject is TLS-verified by mutual authentication. The user Subject is
*asserted* by the authenticated client worker — trust is transitive: trust the worker →
trust its user assertion.

### 10.2 Client Side — `BasicInvocationHandler`

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

### 10.3 Server Side — `BasicInvocationDispatcher`

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

### 10.7 `SubjectDomainCombiner` — Combined Policy View

DirtyChai's `SubjectDomainCombiner.combine()` reads the `SCOPED_SUBJECT` ScopedValue on
**every** `checkPermission` call:

1. Reads ACC-bound principals from the worker Subject.
2. Reads `SCOPED_SUBJECT` directly (no `AuthPermission` check — trusted `java.base`).
3. Additively merges both sets; neither replaces the other.
4. If `SCOPED_SUBJECT` is unbound (daemon thread, non-request context): no change.

This enables policy grants that require **both** identities simultaneously:
```
grant principal SpiffePrincipal "spiffe://jgdms.example.org/svc/order-processor"
      principal KerberosPrincipal "alice@EXAMPLE.ORG" {
    permission ...;
};
```
A grant requiring only the SPIFFE principal still fires in the absence of a user Subject.

### 10.8 Structural Rules for Server Code

1. **Virtual threads and user identity:** Virtual threads spawned inside `invoke()` see the
   worker identity from the ACC.  If user-identity propagation across a thread boundary is
   needed, capture `Subject.current()` before spawning and re-establish with a nested
   `Subject.callAs` inside the spawned thread.

2. **Daemon threads:** Long-lived daemon threads (sweeper, SPIRE watcher, log writer) must
   NOT be created from within a `callAs` scope.  ScopedValue does not propagate to threads
   started after the `callAs` returns.

3. **Trust model:** User principals are not independently TLS-verified.  They should be
   treated as being vouched for by the authenticated worker identity.  A server
   may refuse requests whose worker SPIFFE identity is not trusted to assert user principals
   (e.g., by requiring a specific SPIFFE workload principal alongside any human principal).

### 10.9 `AbstractJiniService` — SPIFFE vs. Traditional JAAS

| Path | What happens |
|---|---|
| `loginContext == null` (SPIFFE path) | `doStart()` called directly; SPIFFE Subject is already ambient via `SpiffeCredentialManager`; `Subject.callAs(spiffeSubject, callable)` used at remote-call boundaries |
| `loginContext != null` (traditional path) | `loginContext.login()` called; `Subject.doAsPrivileged(subject, action, null)` used to run `doStart()` |

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

---

## 11. Remaining `doAs` / `doAsPrivileged` Call Sites — Migration Audit

This section inventories every call site of `Subject.doAs`, `Subject.doAsPrivileged`,
and `Subject.getSubject(AccessControlContext)` found in the JGDMS codebase (excluding
tests and generated code).  Each site is classified by purpose and migration priority.

### 11.1 Background — The Problem

`Subject.doAs` and `Subject.doAsPrivileged` install a Subject into the `AccessControlContext`
(ACC) via `SubjectDomainCombiner`.  The ACC is inherited by child threads created from
platform threads, but is **NOT** propagated by `ScopedValue` and is **NOT** automatically
visible inside a `Callable` submitted to an `Executor` or `ForkJoinPool`.

`Subject.callAs` (JDK 18+) binds the Subject to a `ScopedValue` (`SCOPED_SUBJECT`).
ScopedValues are **not** propagated to new threads by default either — but they can be
propagated explicitly via `StructuredTaskScope` (JEP 453) or captured and re-bound manually.

OpenJDK has added no automatic executor propagation for user Subjects.  The risk
identified is that services using `doAsPrivileged` for JAAS `LoginContext` subjects at
startup time also spawn threads (for event delivery, lease renewal, etc.) that carry the
ACC forward — but if those threads are replaced with virtual threads or executor-submitted
tasks, the user Subject may be invisible unless actively propagated.

The correct modern pattern is:
```java
Subject userSubject = Subject.current();   // captured on the request thread
executor.submit(() -> Subject.callAs(userSubject, () -> { ... }));
```

### 11.2 Call Sites — Classification Table

| File | Line(s) | Current API | Subject source | Purpose | Migration priority |
|---|---|---|---|---|---|
| `AbstractJiniService.start()` | 263 | `Subject.callAs` | `LoginContext.getSubject()` | Run `doStart()` under JAAS login Subject so `BasicInvocationHandler` detects it via `Subject.current()` | **✅ Already correct** — uses `callAs` |
| `FiddlerImpl.initWithLogin()` | 5121 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Run service initialisation under JAAS Subject | **Medium** — init-time only; no thread crossings expected in constructor; replace with `callAs` for consistency |
| `TxnManagerImpl` constructor | 283 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Run `TxnManagerImplInitializer` construction under JAAS Subject | **Medium** — init-time; internal `settleTxns` thread created inside; `callAs` needed if thread uses Subject |
| `MailboxImpl.init()` | 547 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Run `MailboxImplInit` under JAAS Subject | **Medium** — init-time; event delivery threads may need propagation |
| `NormServerBaseImpl.init()` | 1840 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Run `initAsSubject(config)` under JAAS Subject | **Medium** — init-time; lease renewal threads may need propagation |
| `OutriggerServerImpl` constructor | 578 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Run `init(config, persistent, activationID)` under JAAS Subject | **Medium** — init-time |
| `RegistrarImpl` constructor | 505 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Run `new Initializer(...)` under JAAS Subject | **Medium** — init-time; discovery/multicast threads spawned inside |
| `SharedGroupImpl.createWithLogin()` | 277 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Run service group activation under JAAS Subject | **Low** — activation framework; activation threads carry ACC |
| `ServiceStarter.createWithLogin()` | 238 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Start service descriptors under JAAS Subject | **Low** — starter is not a persistent service |
| `DestroySharedGroup.destroyWithLogin()` | 322 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Destroy services under JAAS Subject | **Low** — destroy path; one-shot |
| `Browser.main()` | 481, 1862 | `Subject.doAsPrivileged(..., null)` | `LoginContext.getSubject()` | Launch Browser GUI under JAAS Subject | **Low** — example application, not production service |
| `AbstractActivationGroup.doAction()` | 998 | `Subject.doAsPrivileged(..., null)` | `login.getSubject()` | Call `monitor.activeObject()` under group Subject | **Medium** — remote call from activation system; spawns an executor task |
| `AbstractActivationGroup` executor path | 913 | `Subject.doAsPrivileged(login.getSubject(), new GetThreadPoolAction(false), null)` | `login.getSubject()` | Obtain a thread pool running under group Subject | **High** — executor tasks submitted to this pool do NOT inherit ACC in virtual thread model |
| `Activation.doAsPrivileged()` | 2165 | `Subject.doAsPrivileged(..., null)` | `login.getSubject()` | Run phoenix activation actions under admin Subject | **Medium** — phoenix infrastructure |
| `KerberosUtil.getGSSCredential()` | 493 | `Subject.doAs(subj, ...)` | explicitly passed `Subject` | Acquire Kerberos GSS credential from Subject's private credential set | **Keep as `doAs`** — GSS-API requires Subject in ACC; this is workload (TLS/Kerberos) identity, not user identity |
| `KerberosServerEndpoint` connection thread | 1794 | `Subject.doAs(serverSubject, ...)` | `serverSubject` field | Establish Kerberos GSSContext during TLS handshake | **Keep as `doAs`** — comment: "JDK1.4.2 jgss requires current subject to be set right during the whole process of context establishment"; workload identity, not user identity |
| `SslEndpointImpl.getCallContext()` | 285 | `Subject.getSubject(acc)` | ACC | Retrieve worker Subject for outbound TLS call | **Keep** — worker Subject retrieval from ACC; falls back to `SpiffeSubjectHolder` |
| `SslServerEndpointImpl.SslListenEndpoint` | 587 | `Subject.getSubject(acc)` | ACC | Retrieve worker Subject for inbound TLS listen | **Keep** — same reason |
| `X500Provider` | 191 | `Subject.getSubject(acc)` | ACC | Retrieve Subject for X.500 principal matching | **Keep** — workload identity retrieval from ACC |
| `TlsRMIClientSocketFactory` | 46 | `Subject.getSubject(acc)` | ACC | Retrieve Subject for TLS RMI client socket | **Keep** — workload identity |
| `TlsRMIServerSocketFactory` | 39 | `Subject.getSubject(acc)` | ACC | Retrieve Subject for TLS RMI server socket | **Keep** — workload identity |
| `AbstractDgcClient` | 402 | `Subject.getSubject(cont)` | ACC | Retrieve Subject for DGC lease management | **Medium** — DGC leases are renewed by background threads; if user Subject is relevant here it may not propagate |
| `Security.doAs()` (two overloads) | 634, 691 | Custom `doAs` wrapper | caller-provided | JGDMS custom SubjectDomainCombiner semantics (CodeSource+Principal separation) | **Keep as custom `doAs`** — intentionally different semantics from JDK `Subject.doAs` |
| `Security.doAsPrivileged()` (two overloads) | 723, 759 | Custom `doAsPrivileged` wrapper | caller-provided | Same as above with explicit `SecurityContext` | **Keep as custom `doAsPrivileged`** |

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
from within the `Subject.doAs(workerSubject, ...)` scope so that the ACC is inherited
(platform threads) or captured explicitly (virtual threads via `AccessController.getContext()`
+ `AccessController.doPrivileged(..., capturedAcc)`).

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
| JAAS user Subject (human identity, `LoginContext`) | Initialisation only (no cross-thread calls) | `Subject.callAs` — makes Subject visible via `Subject.current()` |
| JAAS user Subject | Executor-submitted background task | Capture `Subject.current()` before submit; wrap task in `Subject.callAs(captured, ...)` |
| Workload Subject (SPIFFE/Kerberos, used by TLS/GSS) | Anywhere | Keep `Subject.doAs` — ACC inheritance by platform threads is correct; virtual-thread version requires capturing ACC with `AccessController.getContext()` |
| Subject needed for `Subject.getSubject(acc)` check | Existing ACC-based check | Keep `Subject.getSubject(acc)` — this reads the workload Subject from the ACC |

---

## 12. Remaining Work Items (in order) — Updated v9

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
21. **Client-side `RemoteEventListener`** — pull-on-notification for policy updates *(still open)*
    - Must subscribe via `RemotePolicyServiceProxy.registerForPolicyUpdates()`
    - On event receipt: call `getCurrentGrants()`, parse `String[]` back to `PermissionGrant[]`, call `RemotePolicyProvider.replace()`
    - Must track sequence numbers to detect gaps and re-pull
    - Must renew lease before expiry
22. **Unit tests for `policy-service`** — *(still open; no test directory exists under `policy-service-service/src/test/` or `policy-service-dl/src/test/`)*
23. **Host 4 — Codebase Downloader Service** — *(not yet started; no Maven module exists)*
    - Only host with outbound internet access
    - Fetches JAR bytes for codebase URLs discovered from lookup service registrations
    - Pushes `AnalysisRequest` (containing raw JAR bytes) to BAE pool via `BytecodeAnalysisEngine.analyzeJar()`
    - SPIFFE SVID: `spiffe://jgdms.example.org/host/downloader`
24. **`ProxyCodebaseSPI` integration with `VerdictRegistry`** — *(not yet started)*
    - `PreferredProxyCodebaseProvider` must compute SHA-256 hash of each JAR before creating a `PreferredClassLoader`
    - Must call `VerdictRegistry.getVerdictByHash(contentHash)` or `getVerdict(codebaseUrls)`
    - Must refuse to unmarshal if verdict is `DANGEROUS` or absent (absent = not yet audited; policy decision on absent)
25. **`DiscoveryCredentialProvider` interface** — *(not yet started; referenced in design docs only)*

---

## 13. Key Design Decisions — Cumulative

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
| **`Security.getCurrentPrincipals()` checks `Subject.current()` first** | ✅ **v10:** `Security.grant(Class, Permission[])` now prefers the user Subject bound via `Subject.callAs()` (ScopedValue) over the ACC Subject. Grants from JERI dispatch threads are automatically scoped to the remote user's principals. Falls back to ACC Subject when no user Subject is bound. |
| **`GrantPermission.checkGuard()` wraps `checkPermission` in `Subject.doAs(user)`** | ✅ **v10:** When `Subject.current()` is non-null, the guard check runs inside `Subject.doAs(user, ...)` so the user's principals are in the ACC for the duration of `checkPermission`. Daemon threads (no user Subject) use the direct path unchanged. |
| **`jgdms-platform` compiler release bumped to 21** | ✅ **v10:** Required to call `Subject.current()` and `Subject.doAs()` directly (not via reflection) in `jgdms-platform` source. |

---

## 14. SPIFFE Identity Scheme (full system)

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
continue without loss of context. This is version 10, updated to add:*
- *`SpiffeCredentialManager.java`, `SpireConnection.java`, `SpireProtobuf.java` to §1 documents read*
- *§1 extended with 13 new JGDMS source files reviewed in v9 deep-dive analysis*
- *§4 updated to show actual 5-method `RemotePolicyService` interface; corrected note that `DefaultPolicyParser.scanner` is already `protected`*
- *§5.4 updated — `principals == null` now resolves via `Subject.current()` first, then ACC Subject*
- *§5.4.1 (new) — `Security.getCurrentPrincipals()` user-Subject-first resolution documented*
- *§5.4.2 (new) — `GrantPermission.checkGuard()` user-Subject-aware guard documented with full implementation*
- *§10 — complete two-Subject JERI implementation documented*
- *§11 — `doAs`/`doAsPrivileged` call-site audit documented*
- *§12 items 14, 15, 16, 18, 19, 20 all marked ✅ completed; items 21–25 added*
- *§13 decisions table extended with v10 rows covering `Security.getCurrentPrincipals()`, `GrantPermission.checkGuard()`, and `jgdms-platform` release=21 bump*
- *§8.1 updated to document empty SVID handling behaviour*

