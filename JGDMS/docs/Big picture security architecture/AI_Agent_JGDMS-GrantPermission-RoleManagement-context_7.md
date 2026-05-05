# JGDMS — GrantPermission, Role Management & Full Architecture — AI Agent Context

**Purpose:** This document captures the full conversation context for an AI agent to
continue work on JGDMS role management and `GrantPermission` design without loss of
context. It supersedes and extends the two previous context documents:
- `AI_Agent_JGDMS-RemotePolicyService-context.md`
- `AI_Agent_JGDMS-DynamicPolicyProvider-context_1.md`

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
| `Subject.java` | DirtyChai source | ✅ **Updated:** Class javadoc now documents two-Subject identity model (workload on ACC, user on ScopedValue); additive principal merging by SubjectDomainCombiner; ServiceUI use case example; structural discipline for daemon threads |
| `SubjectDomainCombiner.java` | DirtyChai source | ✅ **Implemented:** `getMergedPrincipals()` reads SCOPED_SUBJECT on every `combine()` call; additively merges ACC Subject principals + human user principals; no AuthPermission check (trusted java.base); null-safe via `ScopedValue.isBound()` |

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
- `JarAnalyzer` post-processing — upgrades `BLOCKING_GUARDED` → `BLOCKING_DECLARED` (`DANGEROUS`) when the guarding permission class is declared in `PERMISSIONS.LIST`; this represents a **Denial of Service** risk via virtual-thread carrier pinning
- `JarAnalyzer` reads `META-INF/PERMISSIONS.LIST` — non-blank, non-comment lines stored in `JarAnalysisReport.getDeclaredPermissions()` and included in the engine signature

**`SINK_TO_PERMISSION_CLASS` and the `className#action` syntax:** The upgrade from
`BLOCKING_GUARDED` to `BLOCKING_DECLARED` is driven by
`BlockingSinkRegistry.SINK_TO_PERMISSION_CLASS`, which maps each blocking sink to the
permission class (or `className#action`-qualified permission) that guards it.  The
`#action` suffix restricts matching to a specific permission action, preventing false
positives for broad permission classes — a JAR declaring `RuntimePermission "getenv"`
must not trigger `BLOCKING_DECLARED` for thread-creation sinks.  Example: the map key
`RuntimePermission#createVirtualThread` matches a `PERMISSIONS.LIST` line such as:

```
RuntimePermission "createVirtualThread"
```

and triggers `BLOCKING_DECLARED` only for virtual-thread-creation sinks.  The full
`SINK_TO_PERMISSION_CLASS` mapping table is in §13.

### 2.2 @AtomicSerial Compliance (JGDMS-STD-001)

Every `Serializable` class crossing a JERI wire must comply:
- RULE-1: Annotated `@AtomicSerial`
- RULE-2: Public `(GetArg)` constructor
- RULE-3: Static check method called **before** any field is set (`INVOKESTATIC` before `INVOKESPECIAL`)
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
3. Bootstrap proxies prepared via `bootstrapProxyPreparer.prepareProxy()` — **authentication happens here** (MinPrincipal, SPIFFE identity established)
4. `ServiceItemFilter.check()` → `getServiceProxy()` downloads full smart proxy
5. `ProxyCodebaseSpi.resolve()` provisions ClassLoader, verifies JAR integrity, unmarshals typed proxy
6. Constraints merged; proxy stored as `filteredItem`

**ProxyCodebaseSpi ClassLoader resolution priority:**
1. Boomerang (proxy returning to export node) → use export ClassLoader (`SERVICES_EXP`)
2. Self-unmarshal (loaderAnnotation == codebase) → use parent ClassLoader
3. Previously seen (CACHE hit) → use cached ClassLoader
4. New → verify JAR (certs or `Security.verifyCodebaseIntegrity`), create `PreferredClassLoader`

### 2.5 ClassLoader Identity — Critical Correction

**`AdvisoryDynamicPermissions` is implemented by the ClassLoader, not the proxy class.**
The ClassLoader reads `META-INF/PERMISSIONS.LIST` from the JAR and returns those
permissions from `getPermissions()`.

The ClassLoader cache key in `PreferredProxyCodebaseProvider` is:

```java
Key = (InvocationHandler, List<Uri> codebase, ClassLoader parent)
```

The `InvocationHandler` encodes the authenticated remote endpoint identity. Therefore:

- Two instances of the same service JAR on **different hosts** → different
  `InvocationHandler`s → **different ClassLoaders** → **independent ProtectionDomains**
  → **independent dynamic grants**.
- Two proxies from the same endpoint cannot share a ClassLoader accidentally — the
  `putIfAbsent` in `record()` throws `ExportException` if an endpoint tries to export twice.
- This was a deliberate fix over the earlier `ClassLoading.getClassLoader(path)` approach
  that keyed only on the codebase URL string, which incorrectly shared ClassLoaders
  between unrelated services using the same maven codebase.

**GC chain:** proxy abandoned → ClassLoader weakly reachable (CACHE uses `Ref.WEAK` +
60s TTL) → ProtectionDomain weakly reachable → `DynamicPolicyProvider` grant becomes
void → swept by single background daemon thread (`JGDMS-DynamicPolicyProvider-VoidGrantSweeper`,
default period 60 s). Fully GC-driven; eviction is asynchronous and never on the
`checkPermission` hot path.

---

## 3. DynamicPolicyProvider — Confirmed Decisions

### 3.1 No ReferenceQueue, No clearCache()

**Decision confirmed:** `DynamicPolicyProvider` does NOT need a
`ReferenceQueue<ProtectionDomain>` and does NOT need to call
`CachingSecurityManager.clearCache()` on void grant detection.

**Reason:** The ACC cache (`ContextCache`) is weakly-valued. The reachability chain is:
```
Live code → strong ref → AccessControlContext → strong ref → ProtectionDomain
```
When a proxy is abandoned, the PD becomes weakly reachable. The ACC holding that PD is
no longer referenced by live code, so the ACC cache entry clears **before or concurrent
with** the PD weak ref being enqueued. By the time any WeakReference fires, the ACC
cache entry is already gone. `clearCache()` would be a no-op.

### 3.2 Void Eviction — Single Background Sweeper

**Rejected design — `it.remove()` on `checkPermission` hot path:**
The original plan was to call `it.remove()` on every iterator over `dynamicPolicyGrants`
as void grants were encountered. This was explicitly rejected: `ConcurrentHashMap`
`it.remove()` acquires the bin-head monitor, making it a blocking write on the hot path.
Under high `checkPermission` concurrency, contention is proportional to
`checkPermission rate × void grants present`. On JDK ≤ 23 (pre-JEP 491) the
`synchronized` bin-head monitor also pins virtual-thread carriers. Accepting this
violates the non-blocking, write-free `checkPermission` invariant.

**Chosen design — single background daemon thread:**
A single daemon thread named `JGDMS-DynamicPolicyProvider-VoidGrantSweeper` wakes
every 60 seconds (configurable) and iterates `dynamicPolicyGrants` once, calling
`it.remove()` on any grant for which `isVoid()` returns true.

- **Hot path (`implies()`, `getPermissionGrants()`, `getGrants()`) remains purely
  read-only** — no `it.remove()`, no writes, no bin-lock acquisition.
- **One write source** — all void-grant removals are concentrated in a single thread.
- **Period configurable** via Security property
  `net.jini.security.policy.DynamicPolicyProvider.voidGrantSweepPeriodSeconds`.
  Default 60 s. Setting `<= 0` disables (useful for tests).
- **Fault isolation** — sweeper catches `Throwable` around loop body; caught exceptions
  logged at `Level.WARNING`; eviction count logged at `Level.FINE` when non-zero.

**`refresh()` one-shot eviction retained:**
`refresh()` retains its existing `LinkedList` accumulator + `removeAll` pattern for
administrator-triggered cleanup. The sweeper handles steady-state eviction.

---

## 4. RemotePolicyService Wire Interface

```java
public interface RemotePolicyService extends Remote {
    void replace(String[] grants) throws RemoteException;
    String[] getCurrentGrants() throws RemoteException;
    EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                               MarshalledInstance handback,
                                               long duration) throws IOException;
}
```

**Server-side parsing:** Feed each string directly into `DefaultPolicyScanner.scanStream()`
via a `StringReader` wrapped in an `InputStream` adaptor — bypasses URL machinery.
Requires `DefaultPolicyParser.scanner` to be made `protected` (currently `private final`).

**Validation is always server-side** after parsing — same pattern as
`RemotePolicyProvider.replace()` GrantPermission validation.

---

## 5. GrantPermission & Role Management — Core Design

### 5.1 The Three-Way Intersection

The effective dynamic grant for a proxy is the intersection of three independent constraints:

```
What the proxy's ClassLoader declares       (AdvisoryDynamicPermissions.getPermissions()
  it needs                                   reading META-INF/PERMISSIONS.LIST;
                                             keyed by authenticated endpoint identity)
        ∩
What the client is authorised to give       (GrantPermission entries in
                                             RemotePolicyProvider djinn-session grants)
        ∩
What the proxy's SPIFFE principal is        (principal scoping in DynamicPolicyProvider
                                             grant, derived from preparing thread's Subject
                                             or explicit Principal[] in preparer constructor)
        =
Effective dynamic grant, scoped to this specific authenticated endpoint instance
```

No single party controls the outcome unilaterally.

### 5.2 VerifyingProxyPreparer — The Grant Site

`VerifyingProxyPreparer.prepareProxy()` is where the grant occurs. Two mutually
exclusive paths:

**Explicit permissions path** (preparer constructed with non-empty `Permission[]`):
```java
Security.grant(proxy.getClass(), permissions);
// UnsupportedOperationException → rethrown as SecurityException (hard failure)
```

**Advisory permissions path** (preparer constructed with `null` or empty `Permission[]`):
```java
ClassLoader ldr = proxy.getClass().getClassLoader();
if (ldr instanceof AdvisoryDynamicPermissions) {
    perms = ((AdvisoryDynamicPermissions) ldr).getPermissions();
    Security.grant(klass, perms);
    // UnsupportedOperationException → logged at config level, silently swallowed
}
```

**The intersection enforcement** happens inside `Security.grant()` →
`DynamicPolicyProvider.grant()`. The preparer is NOT on the security-critical path.

### 5.3 GrantPermission Self-Limiting Property

`GrantPermission` is self-limiting: a caller can only grant permissions it itself holds
a `GrantPermission` for.

### 5.4 Administrator Role Levers

**Lever 1 — `GrantPermission` entries in `RemotePolicyProvider` djinn-session grants.**
These define the ceiling on what `Security.grant()` will apply.

**Lever 2 — `VerifyingProxyPreparer` constructor choice.**
- Explicit permissions → locks the grant, ignores advisory
- Null/empty permissions → defers to `PERMISSIONS.LIST`

**Lever 3 — The `principals` parameter in `VerifyingProxyPreparer`.**
- `null` → uses principals of the preparing thread's Subject
- Non-null → explicit `Principal[]` → scopes grants independently of who runs the preparer thread

### 5.5 Natural Role Structure

| Layer | Role surface | Who controls |
|---|---|---|
| `SpiffePolicyFile` | Structural grants: SPIRE socket, reach `InMemoryPolicyService` | Operator; changes only on SVID rotation |
| `RemotePolicyProvider` | Category grants: what SPIFFE-identified service categories may do; `GrantPermission` delegation ceiling | Administrator via `InMemoryPolicyService.replace()` |
| `DynamicPolicyProvider` | Per-proxy instance grants: actual runtime authority of specific authenticated proxies | Client code via `VerifyingProxyPreparer`; bounded by djinn-session layer |

### 5.6 SCAP / GrantPermission Relationship

SCAP and `GrantPermission` are complementary controls at different phases. SCAP validates
*code safety*; policy validates *runtime authority*.

**Implemented (v3):** The BAE now parses `META-INF/PERMISSIONS.LIST` during Phase 1
JAR scanning and includes the declared permissions in `JarAnalysisReport`.

### 5.7 Risk Categorisation for GrantPermission

| Category | Examples | Recommendation |
|---|---|---|
| Safe to delegate | `FilePermission` to specific data directories | Include in djinn-session `GrantPermission` grants; advisory path appropriate |
| Requires care | `createVirtualThread` | Include only for specific SPIFFE identities |
| **DoS risk if declared in `PERMISSIONS.LIST`** | `SocketPermission`, `NativeInvocationPermission`, `NativeMemoryPermission`, `RuntimePermission("createPlatformThread")`, `RuntimePermission("createVirtualThread")` | BAE will flag as `BLOCKING_DECLARED` / `DANGEROUS`; do not grant unless `<clinit>` audited |
| Requires care — scoped only | `SocketPermission` to specific known internal hosts (no `<clinit>` blocking path) | Include only if BAE verdict is `SAFE` or `INCONCLUSIVE` |
| Never delegate | `PolicyPermission("Remote")`, `GrantPermission` itself, `AllPermission` | Must not appear in dynamically delegatable grants |
| Structurally unsafe | `LoadClassPermission`, `DefineClassPermission`, `NativeMemoryPermission` | SCAP gate necessary but not sufficient; tight scoping required |

---

## 6. DirtyChai Security Model — Relevant Constraints

### 6.1 Guard Inventory (relevant to policy design)

| Permission | Guard site | Notes |
|---|---|---|
| `LoadClassPermission` | `SecureClassLoader.defineClass()` | Coarse binary gate on classpath class loading |
| `SerialObjectPermission` | `ObjectInputStream.readOrdinaryObject()` | Per-class; fires before construction |
| `NativeInvocationPermission` | `NativeLibraries`, `SymbolLookup` | Guards native symbol resolution |
| `NativeMemoryPermission` | All four `Arena` factories + `reinterpret-memory-segment` | Off-heap DoS prevention |
| `DefineClassPermission` | `MethodHandles.Lookup.defineClass()` | Dynamic class injection prevention |
| `RuntimePermission("createPlatformThread")` | `ThreadBuilders` + all `Thread` constructors | Thread-bomb DoS prevention |
| `RuntimePermission("createVirtualThread")` | `ThreadBuilders` | Virtual thread-bomb DoS prevention |

### 6.2 Virtual Thread Blocking in `<clinit>` — Process Isolation Boundary

**JDK 21–23 (pre-JEP 491):** Carrier platform thread pinned when virtual thread blocked
inside `synchronized` (including class-loading lock held during `<clinit>`). Exhausting
carriers with blocked class-loads was a realistic DoS.

**JDK 24+ (JEP 491):** Carrier pinning from `synchronized` eliminated. However, a
blocking `<clinit>` still holds the class-loading lock, serialising every thread
attempting to load the same class — a class-loading starvation / deadlock risk JEP 491
does not address.

**Layered defence:**
1. **Prevention** — deny `createVirtualThread` to untrusted code via policy
2. **Containment** — route trusted-but-suspicious code through a bounded, isolated
   `ForkJoinPool` with a caller-side deadline
3. **Acceptance** — on pre-JEP 491 JVMs a stuck thread consumes its carrier slot
   until JVM exit; on JDK 24+ it serialises class loading of the affected class

### 6.3 CombinerSecurityManager — Recursion Depth

The recursion guard (`ScopedValue<Integer> TRUSTED_RECURSIVE_CALL`) has a limit of 7.
The three-layer policy stack uses 3. Headroom of 4. A fourth policy layer would need
careful analysis.

### 6.4 Subject API — Two Carriers, One Combined View

DirtyChai maintains and extends both the traditional JAAS Subject API and the modern
`callAs`/`current()` API, assigning them to distinct identity namespaces.

#### Two carriers

| Carrier | Identity type | Established by | Lifetime |
|---|---|---|---|
| `AccessControlContext` + `SubjectDomainCombiner` | SPIFFE workload identity | `doAsPrivileged` at service start | JVM / SVID rotation |
| `ScopedValue` (`SCOPED_SUBJECT`) | Human user identity (Kerberos, etc.) | `callAs` at request boundary | Duration of `Callable` |

#### Public API surface

| Method | Guard | Retrieves |
|---|---|---|
| `Subject.doAs(Subject, PrivilegedExceptionAction)` | none (caller must hold appropriate permissions) | Installs Subject onto ACC via `SubjectDomainCombiner` |
| `Subject.doAsPrivileged(Subject, PrivilegedExceptionAction, AccessControlContext)` | none | As above with explicit ACC; pass `null` for fresh empty ACC |
| `Subject.getSubject(AccessControlContext)` | `AuthPermission("getSubject")` | Retrieves SPIFFE Subject from ACC |
| `Subject.callAs(Subject, Callable)` | none | Binds Subject to `SCOPED_SUBJECT` ScopedValue for duration of Callable |
| `Subject.current()` | `AuthPermission("getSubject")` | Retrieves human Subject from `SCOPED_SUBJECT` |

Both `getSubject(ACC)` and `current()` are guarded by `AuthPermission("getSubject")` —
retrieving either form of identity is a privileged operation. DirtyChai retains
`getSubject(ACC)` (deprecated in standard OpenJDK) intentionally.

#### SubjectDomainCombiner — combined view on every checkPermission

On every `checkPermission` call the combiner:
1. Reads the ACC-bound SPIFFE principals (as today)
2. Reads `SCOPED_SUBJECT` directly from the `ScopedValue` — **no `AuthPermission` check**
   (combiner is trusted `java.base` infrastructure; the permission guard lives at the
   public `current()` API boundary, not inside the combiner)
3. If `SCOPED_SUBJECT` is non-null, **additively injects** the human user's principals
   into the combined domain set alongside the SPIFFE principals — neither replaces the
   other; both must be satisfied for a grant conditioned on both
4. If `SCOPED_SUBJECT` is null (daemon threads, sweeper, SPIRE watcher, etc.) — no
   change; `ScopedValue.orElse(null)` read is cheap on the hot path and does not
   trigger recursion

#### What this enables at the policy layer

Principal-scoped grants in `DynamicPolicyProvider` can now condition on both identities
simultaneously:

```
grant principal SpiffePrincipal "spiffe://.../svc/order-processor"
      principal KerberosPrincipal "alice@EXAMPLE.ORG" {
    permission ...;
};
```

Both must be present. A grant requiring only SPIFFE still fires without a user present.
A grant requiring only Kerberos requires the SPIFFE workload context to also be active
(since all service code runs inside `doAsPrivileged`).

#### Structural discipline for infrastructure threads

Daemon threads (sweeper, SPIRE watcher, log writer, metric emitter) must **not** be
spawned from within a `callAs` scope. If they were, human principals would affect their
security decisions unintentionally. `ScopedValue` structured scoping enforces this
naturally — the binding closes when the `Callable` returns — as long as no
`Thread.ofPlatform().start()` or executor submission escapes the `callAs` boundary.

#### Interaction with the recursion guard

The combiner's direct `ScopedValue` field read inside `java.base` does not itself
trigger `checkPermission` and therefore does not consume the recursion budget (§6.3).

---

## 7. VerifyingProxyPreparer — Constructor Detail

Three constructors determine which grant path `prepareProxy()` takes.

**Two-argument** `(Object[] contextElements, Permission[] permissions)` — most common.
Always `SET_CONSTRAINTS` mode. Pass `null` permissions for advisory path; pass explicit
`Permission[]` to override.

**Four-argument** `(ClassLoader loader, Object[] contextElements, Principal[] principals,
Permission[] permissions)` — same but allows explicit `ClassLoader` and fixed
`Principal[]`. Use for service-to-service grants.

**Five-argument** `(boolean addProxyConstraints, ClassLoader loader, Object[] contextElements,
Principal[] principals, Permission[] permissions)` — the flexible constructor.
- `addProxyConstraints = true` → `ADD_CONSTRAINTS`
- `addProxyConstraints = false` → `AS_IS`; only constructor accepting `null` contextElements

**Failure asymmetry:**
- Explicit path failure → hard `SecurityException` (deployment misconfiguration)
- Advisory path failure → logged and swallowed (graceful degradation)

---

## 8. Authentication Model

### 8.1 SPIFFE/SPIRE — Workload Identity

JGDMS has no username/password login. Workload authentication is entirely
**SPIFFE/SPIRE X.509 SVIDs** — cryptographic identity certificates provisioned by SPIRE
at workload startup. Identity is ambient, not interactive.

`SpiffeCredentialManager` (implemented — see companion SpiffePolicyFile context doc)
manages the current `SSLContext` and calls `SpiffePolicyFile.refresh()` on SVID rotation
(~1 hour lifetime).

`Subject.doAsPrivileged(spiffeSubject, action, null)` is used at service start
(`AbstractJiniService.start()`) to run the entire service under the SPIFFE Subject with
a fresh empty ACC.

### 8.2 Human User Identity — Kerberos and Traditional JAAS

Human users authenticate via traditional JAAS mechanisms (Kerberos, smart card, etc.).
The resulting Subject is installed per-request via `Subject.callAs(userSubject, () -> ...)`.

`Subject.current()` retrieves the human Subject within the `callAs` scope.
`SubjectDomainCombiner` reads `SCOPED_SUBJECT` internally (no `AuthPermission` check)
and injects the human principals into every `checkPermission` call for the duration of
the request — see §6.4 for full detail.

### 8.3 Traditional Jini LoginContext

`AbstractJiniService` also supports the traditional Jini pattern: if a `LoginContext` is
configured, `start()` calls `loginContext.login()` and runs `doStart()` inside
`Subject.doAsPrivileged`. `destroy()` calls `loginContext.logout()`. This path
co-exists with SPIFFE — the two are not mutually exclusive.

For SPIFFE-authenticated services (e.g. `InMemoryPolicyService`), `loginContext` is
`null` and `start()` calls `doStart()` directly. The `onStart(Subject subject)` hook
receives `null` — the SPIFFE identity is ambient via the SVID, not via a LoginContext.

### 8.4 DirtyChai Administrator

DirtyChai runs as a workload with `spiffe://.../admin/policy` SVID. The human
authenticates to the machine running DirtyChai via OS/SSH. DirtyChai's SPIFFE identity
then gates access to `InMemoryPolicyService.replace()`.

---

## 9. ServiceUI — Design Now Resolved

ServiceUI introduces a human-facing UI component downloaded and run on the client side.

**The human identity bridging question (previously open) is now resolved by §6.4:**
A ServiceUI operation runs inside `callAs(kerberosSubject, () -> ...)` which is itself
inside the `doAsPrivileged(spiffeSubject, ...)` workload context already established by
`AbstractJiniService`. The `SubjectDomainCombiner` sees both Subjects and injects both
principal sets into `checkPermission` decisions. Policy can therefore condition grants
on both the workload identity (which ServiceUI JAR) and the human identity (which user).

**Codebase and SCAP:** The ServiceUI JAR is a separate codebase from the service proxy
JAR. Independent BAE audit, `RegistryVerdict`, and `ProxyCodebaseSpi` gate. UI grants
should be scoped more narrowly than proxy grants.

**GrantPermission and the UI ClassLoader:** The ServiceUI runs in its own `ClassLoader`
keyed by its own `(InvocationHandler, codebase[], parent)` triple — independent dynamic
grants from the service proxy.

**Status:** Human identity bridging design resolved. Implementation deferred.

---

## 10. Architecture Diagrams Produced

| Diagram | Focus | Key content |
|---|---|---|
| Diagram 1 — SCAP five-host pipeline | SCAP five-host pipeline | Host roles, trust levels, permitted/forbidden connections, data objects, SPIFFE IDs, quorum policy |
| Diagram 2 — Three-layer policy stack | Three-layer policy stack | Nested layers with implementation detail, IMS wire format, ACC/PD chain callout, recursion guard, bootstrap prereqs |
| Diagram 3 — Proxy lifecycle | Proxy lifecycle | Six phases top-to-bottom with method-level detail, four-path ClassLoader resolution, GC eviction chain |
| Diagram 4 — GrantPermission & role management | GrantPermission & role management | Three-way intersection, delegation chain by role, three administrator levers, risk categorisation table, full SPIFFE scheme |

---

## 11. Files Still To Read Before Implementing

| File | Reason |
|---|---|
| `DynamicPolicy.java` (interface) | Confirm interface contract before finalising `DynamicPolicyProvider` update |
| `PolicyParser.java` (interface) | Confirm `parse(URL, Properties)` signature before implementing `HttpsClientAuthPolicyParser` |
| `LandlordLease` / lease infrastructure | Review before implementing `InMemoryPolicyService` lease management |

### 11.1 Files Already Read

| File | Where cited |
|---|---|
| `DefaultPolicyScanner.java` | §4 wire format, §12 item 7 |
| `Security.java` (`grant()` methods) | §3, §7 |
| `AbstractJiniService.java` | §8.3 LoginContext integration; `onStart(Subject)` hook |

---

## 12. Remaining Work Items (in order) — Updated

1. **✅ `DynamicPolicyProvider.java` — single background sweeper for void eviction** *(completed v6)*
2. **✅ `DefaultPolicyParser.scanner` — `private` → `protected`** *(completed v6, both repos)*
3. **✅ `HttpsClientAuthPolicyParser`** *(completed v6 — see SpiffePolicyFile context doc)*
4. **✅ `SpiffePolicyFile`** *(completed v6 — see SpiffePolicyFile context doc)*
5. **✅ `SpiffeCredentialManager`** *(completed v6 — see SpiffePolicyFile context doc)*
6. **✅ `SubjectDomainCombiner` — inject `SCOPED_SUBJECT` principals** *(completed v7 — see §6.4)*
7. **✅ `Subject.java` class javadoc update** *(completed v7 — documents two-Subject model)*
8. **✅ `SpiffeCredentialManager` — trust bundle support** *(completed v8)*
9. **✅ `SpiffeX509TrustManager` — full chain verification** *(completed v8)*
10. **✅ `SpiffeCredentialManager` — exponential backoff reconnection** *(completed v8)*
11. **✅ `FilterX509TrustManager` — design analysis and documentation** *(completed v8 — dual-role pattern intentional)*
12. **`RemotePolicyService` interface** — new JERI wire interface (Section 4).
13. **`InMemoryPolicyService`** — JERI service skeleton
   - Extends `AbstractJiniService`
   - Implements `RemotePolicyService`
   - Server-side `String[]` → `PermissionGrant[]` parsing via `DefaultPolicyScanner.scanStream()`
   - `replace()` permission validation after parsing
   - `PolicyUpdateEvent extends RemoteEvent`
   - Lease management via `LandlordLease`
   - Async event dispatch via bounded queue + dispatcher thread
   - SPIFFE SVID: `spiffe://jgdms.example.org/host/policy`
   - Only hosts with admin SVID (`spiffe://jgdms.example.org/admin/policy`) may call `replace()`
14. **DirtyChai smart proxy client for `RemotePolicyService`**
15. **Client-side `RemoteEventListener`**
16. **✅ `PERMISSIONS.LIST` BAE integration** *(completed v3)*

---

## 13. Key Design Decisions — Cumulative

| Decision | Rationale |
|---|---|
| No `ReferenceQueue` in `DynamicPolicyProvider` | ACC is collected before or concurrent with PD weak ref enqueue; `clearCache()` would be a no-op |
| No `clearCache()` on dynamic grant GC | ACC self-evicts; no stale entries remain by the time void is detected |
| Single background sweeper for void grant eviction | Keeps hot path read-only; one write source ⇒ no contention; aligns with 60 s ClassLoader CACHE TTL |
| `it.remove()` on `checkPermission` hot path explicitly rejected | Acquires bin-head monitor and writes; invalidates cache lines under load; violates non-blocking invariant |
| `RemotePolicy` wire format is `String[]` | DirtyChai cannot implement `@AtomicSerial`; `String[]` needs no `@AtomicSerial` at either end |
| Policy file syntax as wire format | `PermissionGrant.toString()` already emits it; `DefaultPolicyScanner` already parses it |
| Validation is server-side after parsing | Client smart proxy cannot be trusted; `InMemoryPolicyService` validates `GrantPermission` after parse |
| Server-side parsing via `scanStream()` + `StringReader` | Bypasses URL machinery; same reason `scanner` must be `protected` |
| `getCurrentGrants()` also returns `String[]` | Symmetric with `replace()`; client parses back without `@AtomicSerial` |
| `MarshalledInstance` not `MarshalledObject` | Carries codebase annotations, AtomicSerial-aware, correct class loader context |
| Bootstrap policy fetched via HTTPS, no local cache | Fail-secure: node does not start if server unreachable |
| CA pinning on HTTPS fetch | Compromised system CA cannot serve malicious bootstrap policy |
| Pull-on-notification for policy events | `getCurrentGrants()` is always source of truth |
| Three-layer decoration stack | Clean separation of grant lifetimes; each layer revokes independently |
| `AdvisoryDynamicPermissions` on ClassLoader, not proxy class | ClassLoader is the natural owner of codebase-wide permission declarations |
| ClassLoader keyed by `(InvocationHandler, codebase[], parent)` | Endpoint identity (not just codebase URL) determines ClassLoader |
| Intersection enforced in `DynamicPolicyProvider.grant()` | `Security.grant()` enforces `GrantPermission` ceiling |
| Advisory grants are best-effort | `UnsupportedOperationException` in advisory path → logged, not rethrown |
| Explicit permissions in preparer override advisory | Administrator can lock grant to specific list |
| `createVirtualThread` in `GrantPermission` requires care | Grants make blocking `<clinit>` paths reachable — DoS risk on pre-JEP 491 JVMs |
| `PolicyPermission("Remote")` and `GrantPermission` itself must never be delegatable | Would allow proxies to participate in policy machinery |
| SCAP validates code safety; policy validates runtime authority | Complementary controls at different phases |
| `JarAnalysisReport` carries `String[] declaredPermissions` (`serialVersionUID=2L`) | Enables cross-referencing declared needs against `GrantPermission` ceiling |
| `parsePermissionsList()` strips blank lines and `#`-comments | Matches de-facto convention; comment support allows copyright headers |
| Old 3-arg `JarAnalysisReport` constructor delegates to 4-arg with empty array | Backward compatible |
| Explicit preparer path → hard `SecurityException` on grant failure | Administrator made a positive decision; failure = deployment misconfiguration |
| Advisory preparer path → soft failure (logged only) | Grant is best-effort; proxy still usable without it |
| Authentication is SPIFFE/SPIRE workload identity; no traditional login at JGDMS layer | Identity is ambient — provisioned by SPIRE at workload startup |
| `AbstractJiniService` `loginContext` is null for SPIFFE services | SPIFFE identity is ambient; `onStart(Subject)` receives null; no JAAS login step |
| `BLOCKING_GUARDED` is `INCONCLUSIVE`, not `DANGEROUS` | Blocking path only reachable if guarding permission is granted |
| `BLOCKING_DECLARED` is `DANGEROUS` | JAR's own `PERMISSIONS.LIST` declares the guarding permission — DoS risk if granted |
| `SINK_TO_PERMISSION_CLASS` maps direct per-call guards | Sinks guarded at construction time only are excluded |
| `className#action` in `SINK_TO_PERMISSION_CLASS` values | Prevents false positives for broad permission classes |
| SPIFFE workload identity on ACC (`doAs`); human identity on ScopedValue (`callAs`) | Clean namespace separation: workload identity is ambient and long-lived; user identity is request-scoped |
| `getSubject(ACC)` and `current()` both guarded by `AuthPermission("getSubject")` | Retrieving either identity is privileged; unified guard keeps policy simple |
| `SubjectDomainCombiner` reads `SCOPED_SUBJECT` in `combine()`, not constructor | Correct lifecycle — SCOPED_SUBJECT changes per request; combiner is constructed once per ACC |
| `getMergedPrincipals()` additively merges principals from both Subjects | Neither replaces the other; grants conditioned on both require both |
| No `AuthPermission` check in `getMergedPrincipals()` | Combiner is trusted `java.base` infrastructure; permission guard lives at public `current()` API boundary |
| `ScopedValue.isBound()` check before `get()` | Null-safe; cheap hot-path check; daemon threads have no SCOPED_SUBJECT |
| `Subject.java` class javadoc documents two-Subject model | Minimal merge-safe insertion before "Deprecated Methods" section; includes identity carriers table, combined view explanation, ServiceUI example, structural discipline note |
| `FilterX509TrustManager` extends `X509ExtendedKeyManager` intentionally | Dual-role pattern for `AuthManager` (provides both key manager and trust manager roles); `X509ExtendedKeyManager` provides boilerplate methods; design predates Java 7 `X509ExtendedTrustManager`; not a bug |
| Trust bundle stored separately from SVID credentials | Trust bundle rotates independently; used by `SpiffeX509TrustManager` for peer verification; defensive copying prevents external modification |
| Trust bundle parsed from `X509SVID.bundle` field | Per SPIRE Workload API spec: bundle is per-SVID (enables federated trust), not per-response |
| Exponential backoff for SPIRE watcher reconnection | Production resilience without manual intervention; 1s → 2s → 4s → ... → 5min max; configurable via system properties |
| Raw `Thread` + `Thread.sleep` for reconnection scheduling | Bootstrap-safe; `ScheduledExecutorService` initialization not audited for invokedynamic |
| Reset `reconnectAttempts` on successful update | Fresh backoff sequence after transient failures; distinguishes permanent vs transient failures |
| SHA-256 fingerprint comparison for trust bundle verification | Cryptographically strong; immune to certificate field modifications; standard practice |

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
All other authenticated JERI clients may call `getCurrentGrants()` and
`registerForPolicyUpdates()`.

---

*Hand this document (along with source files as needed) to a future AI agent to
continue without loss of context. The two previous context documents are superseded
by this one for all topics covered here. This is version 7, updated to add:
`Subject.java` and `SubjectDomainCombiner.java` to §1 documents read;
§12 items 6 and 7 marked ✅ completed;
§13 cumulative decisions table extended with five new rows covering
`SubjectDomainCombiner` implementation and `Subject.java` javadoc update.*