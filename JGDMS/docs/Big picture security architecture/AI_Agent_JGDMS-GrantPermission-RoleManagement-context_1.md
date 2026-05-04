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
void → lazy `it.remove()` eviction on next iterator pass. Fully GC-driven, no
coordination needed.

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

The `ReferenceQueue` approach described in `AI_Agent_JGDMS-RemotePolicyService-context.md`
Section 3 is **superseded and confirmed unnecessary**.

### 3.2 Lazy Void Eviction — Agreed, Not Yet Implemented

**Decision:** All iterators over `dynamicPolicyGrants` must call `it.remove()` on void
grants as encountered. The `ConcurrentHashMap` iterator is weakly consistent and
supports `remove()` — safe for concurrent access. Benign race: two threads removing
the same void grant — one remove is a no-op.

**Four sites to update:**
1. `getPermissionGrants(ProtectionDomain)` — both privileged and non-privileged passes
2. `implies(ProtectionDomain, Permission)` — iterator pass over `dynamicPolicyGrants`
3. `getGrants(Class, Principal[])` — iterator pass
4. `refresh()` — simplify to `it.remove()`, eliminating the `LinkedList` accumulator

**Updated iterator pattern:**
```java
Iterator<PermissionGrant> it = dynamicPolicyGrants.iterator();
while (it.hasNext()){
    PermissionGrant pg = it.next();
    if (pg.isVoid()) {
        it.remove();
        continue;
    }
    // ... existing logic
}
```

**Status: Agreed, not yet implemented.** Producing the complete updated
`DynamicPolicyProvider.java` remains an immediate next task.

---

## 4. RemotePolicyService Wire Format

**Wire format:** `String[]` — each element is a single `PermissionGrant` serialised
to policy file syntax via `PermissionGrant.toString()`.

**Reason:** DirtyChai's `PermissionGrant` implementations cannot satisfy `@AtomicSerial`.
`String[]` requires no `@AtomicSerial` at either end. Plain Java serialisation of
`String[]` is safe.

```
DirtyChai (administrator side)              JGDMS node (JERI server side)
    PermissionGrant[]                           PermissionGrant[]
         │  PermissionGrant.toString()               ▲  DefaultPolicyScanner
         ▼                                           │  + PermissionGrantBuilder
    String[] ─────────── JERI wire ──────────► String[]
```

**Wire interface:**
```java
public interface RemotePolicyService {
    void replace(String[] policyStrings) throws IOException;
    String[] getCurrentGrants() throws IOException;
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

No single party controls the outcome unilaterally. The proxy cannot social-engineer
a privileged client into over-granting; a privileged client cannot extract more than
the proxy declared it needs; neither can exceed what the djinn-session grants permit.

### 5.2 VerifyingProxyPreparer — The Grant Site

`VerifyingProxyPreparer.prepareProxy()` is where the grant occurs. Two mutually
exclusive paths:

**Explicit permissions path** (preparer constructed with non-empty `Permission[]`):
```java
Security.grant(proxy.getClass(), permissions);        // or with principals
// UnsupportedOperationException → rethrown as SecurityException (hard failure)
```
Administrator has made an explicit decision that overrides the proxy's self-declaration.

**Advisory permissions path** (preparer constructed with `null` or empty `Permission[]`):
```java
ClassLoader ldr = proxy.getClass().getClassLoader();
if (ldr instanceof AdvisoryDynamicPermissions) {
    perms = ((AdvisoryDynamicPermissions) ldr).getPermissions();
    Security.grant(klass, perms);   // or with principals
    // UnsupportedOperationException → logged at config level, silently swallowed
}
```
Falls through to the proxy's declared requirements. Graceful degradation if the
runtime doesn't support dynamic grants — advisory grants are best-effort.

**The intersection enforcement** happens inside `Security.grant()` →
`DynamicPolicyProvider.grant()`. The preparer passes the full advisory set; the
policy layer enforces what the caller actually holds `GrantPermission` for.
The preparer is NOT on the security-critical path for what gets granted.

### 5.3 GrantPermission Self-Limiting Property

`GrantPermission` is self-limiting by design: a caller can only grant permissions
it itself holds a `GrantPermission` for. An administrator holding
`GrantPermission(SocketPermission("db.internal:5432","connect"))` can delegate
exactly that — not `SocketPermission("*","connect")` unless they also hold
`GrantPermission` for that broader permission.

### 5.4 Administrator Role Levers

**Lever 1 — `GrantPermission` entries in `RemotePolicyProvider` djinn-session grants.**
These define the ceiling on what `Security.grant()` will apply, regardless of what
the advisory path requests. This is the primary role definition surface in
`InMemoryPolicyService`.

**Lever 2 — `VerifyingProxyPreparer` constructor choice.**
- Explicit permissions → locks the grant, ignores advisory (for tightly controlled services)
- Null/empty permissions → defers to `PERMISSIONS.LIST` (default for well-audited services)

**Lever 3 — The `principals` parameter in `VerifyingProxyPreparer`.**
- `null` → uses principals of the preparing thread's Subject (authenticated client SPIFFE identity)
- Non-null → explicit `Principal[]` → allows scoping grants to a specific minimum
  principal set independently of who runs the preparer thread

### 5.5 Natural Role Structure

| Layer | Role surface | Who controls |
|---|---|---|
| `SpiffePolicyFile` | Structural grants: SPIRE socket, reach `InMemoryPolicyService` | Operator; changes only on SVID rotation |
| `RemotePolicyProvider` | Category grants: what SPIFFE-identified service categories may do; `GrantPermission` delegation ceiling | Administrator via `InMemoryPolicyService.replace()` |
| `DynamicPolicyProvider` | Per-proxy instance grants: actual runtime authority of specific authenticated proxies | Client code via `VerifyingProxyPreparer`; bounded by djinn-session layer |

### 5.6 SCAP / GrantPermission Relationship

SCAP and `GrantPermission` are complementary controls at different phases:

```
Codebase audited by SCAP (BAE quorum SAFE)
    → ProxyCodebaseSpi: ClassLoader provisioned, keyed by (handler, codebase, parent)
    → client unmarshals proxy
    → VerifyingProxyPreparer: Security.verifyObjectTrust() + advisory grant path
    → DynamicPolicyProvider: grant enforced at intersection of advisory ∩ GrantPermission
    → Proxy operates with effective dynamic grant
```

A JAR can be SCAP-`SAFE` but still be granted too much authority at the
`RemotePolicyProvider` layer. A JAR can pass SCAP but have its permissions tightly
scoped. SCAP validates *code safety*; policy validates *runtime authority*.

**Implemented (v3):** The BAE now parses `META-INF/PERMISSIONS.LIST` during Phase 1
JAR scanning and includes the declared permissions in `JarAnalysisReport` (field
`String[] declaredPermissions`, `serialVersionUID=2L`).  Declared permissions are
also included in the canonical signing bytes so that a compromised pipeline cannot
strip or alter the declared set without invalidating the engine signature.  The
`JarAnalysisReport.getDeclaredPermissions()` accessor returns a defensive copy.

The Verdict Registry (Host 3) can surface these declared permissions to administrators
in DirtyChai, enabling cross-referencing: "this service declares `createVirtualThread`
in PERMISSIONS.LIST — do your djinn-session grants include a `GrantPermission` for it?"

### 5.7 Risk Categorisation for GrantPermission

| Category | Examples | Recommendation |
|---|---|---|
| Safe to delegate | `FilePermission` to specific data directories | Include in djinn-session `GrantPermission` grants; advisory path appropriate |
| Requires care | `createVirtualThread` | Include only for specific SPIFFE identities; once granted, carrier saturation is a containment problem (see Section 6.2) |
| **DoS risk if declared in `PERMISSIONS.LIST`** | `SocketPermission`, `NativeInvocationPermission`, `NativeMemoryPermission`, `RuntimePermission("createPlatformThread")`, `RuntimePermission("createVirtualThread")` — any permission that directly guards a known blocking sink | If a JAR declares this in `PERMISSIONS.LIST` **and** has a `<clinit>` path guarded by that permission, granting it makes the blocking path reachable — on pre-JEP 491 JVMs (JDK ≤ 23) the carrier is pinned; on JDK 24+ the class-loading lock is held, serialising all threads loading the same class — the BAE will flag this as `BLOCKING_DECLARED` / `DANGEROUS`; do **not** grant unless the `<clinit>` has been audited (→ `BLOCKING_DECLARED` verdict, see §2.1 and §13) |
| Requires care — scoped only | `SocketPermission` to specific known internal hosts (no `<clinit>` blocking path) | Include only if the BAE verdict is `SAFE` or `INCONCLUSIVE`; never grant if verdict is `DANGEROUS` (→ `BLOCKING_GUARDED` verdict, see §2.1 and §13) |
| Never delegate | `PolicyPermission("Remote")`, `GrantPermission` itself, `AllPermission` | Must not appear in dynamically delegatable grants |
| Structurally unsafe | `LoadClassPermission`, `DefineClassPermission`, `NativeMemoryPermission` | SCAP gate is necessary but not sufficient; tight `GrantPermission` scoping required |

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

**JDK 21–23 (pre-JEP 491):** When a virtual thread blocked inside a `synchronized`
block (including the JVM-internal class-loading lock held during `<clinit>` execution),
the carrier platform thread was pinned and could not be reused for other virtual threads.
Exhausting `Runtime.availableProcessors()` carriers with blocked class-loads was a
realistic Denial of Service.

**JDK 24+ (JEP 491 — "Synchronize Virtual Threads without Pinning"):** The JVM now
*can* unmount virtual threads from their carriers even when they are blocked inside
`synchronized` blocks — carrier pinning from `synchronized` is eliminated.  However,
a blocking `<clinit>` still holds the **class-loading lock** (monitor), serialising
every other thread that attempts to load the same class until the blocking call
returns.  This is a class-loading starvation / deadlock risk that JEP 491 does not
address.

The `createVirtualThread` permission check fires at creation — this remains the correct
prevention boundary.  After creation, the residual availability threat is class-loading
starvation rather than carrier saturation on JDK 24+ runtimes.

**Layered defence:**
1. **Prevention** — deny `createVirtualThread` to untrusted code via policy
2. **Containment** — route trusted-but-suspicious code through a bounded, isolated
   `ForkJoinPool` with a caller-side deadline
3. **Acceptance** — on pre-JEP 491 JVMs a stuck thread consumes its carrier slot
   until JVM exit; on JDK 24+ it serialises class loading of the affected class

**Impact on GrantPermission design:** `GrantPermission(RuntimePermission("createVirtualThread"))`
should only appear in djinn-session grants for SPIFFE identities whose codebase has
been carefully reviewed and whose `<clinit>` paths contain no blocking operations.

### 6.3 CombinerSecurityManager — Recursion Depth

The recursion guard (`ScopedValue<Integer> TRUSTED_RECURSIVE_CALL`) has a limit of 7.
The three-layer policy stack uses 3. This leaves headroom of 4. Deep role delegation
chains that cause deep policy stack traversal eat into this headroom. The current
stack depth is safe; a fourth policy layer would need careful analysis.

---

## 7. VerifyingProxyPreparer — Constructor Detail

Three constructors determine which grant path `prepareProxy()` takes.

**Two-argument** `(Object[] contextElements, Permission[] permissions)` — most common.
Always `SET_CONSTRAINTS` mode: returns proxy with constraints set to the first
`MethodConstraints` in `contextElements`. Requires a `MethodConstraints` element —
throws `IllegalArgumentException` if none found. Pass `null` permissions for advisory
path; pass explicit `Permission[]` to override.

**Four-argument** `(ClassLoader loader, Object[] contextElements, Principal[] principals,
Permission[] permissions)` — same as above but allows explicit `ClassLoader` for trust
verifier lookup and a fixed `Principal[]` to scope the grant independently of the
preparing thread's Subject. Use for service-to-service grants.

**Five-argument** `(boolean addProxyConstraints, ClassLoader loader, Object[] contextElements,
Principal[] principals, Permission[] permissions)` — the flexible constructor.
- `addProxyConstraints = true` → `ADD_CONSTRAINTS`: proxy's own existing constraints
  prepended to `contextElements` before trust verification; original proxy returned.
- `addProxyConstraints = false` → `AS_IS`: no constraint manipulation; original proxy
  returned. Only constructor that accepts `null` for `contextElements`.

**What controls the grant path** — solely whether `permissions.length > 0` after
`checkPermissions()`. Both `null` and empty array map to `DEFAULT_PERMISSIONS` (length 0)
and route to the advisory path. The branch in `prepareProxy()`:

```java
if (permissions.length > 0) {
    Security.grant(klass, permissions);   // explicit — UnsupportedOperationException
                                          // rethrown as SecurityException (hard fail)
} else {
    ClassLoader ldr = proxy.getClass().getClassLoader();
    if (ldr instanceof AdvisoryDynamicPermissions) {
        perms = ((AdvisoryDynamicPermissions) ldr).getPermissions();
        Security.grant(klass, perms);     // advisory — UnsupportedOperationException
                                          // logged at config level only (soft fail)
    }
}
```

**Failure asymmetry is deliberate:**
- Explicit path failure = hard `SecurityException` — administrator made a positive
  decision; failure means deployment misconfiguration, not something to degrade past.
- Advisory path failure = logged and swallowed — proxy still works without the grant;
  graceful degradation is correct when grants are best-effort.

**Practical decision rule for administrators:**
- Advisory path (null/empty permissions) — correct default for well-audited, SCAP-verified
  services. The proxy declares what it needs in `PERMISSIONS.LIST`; `DynamicPolicyProvider`
  silently enforces the `GrantPermission` ceiling regardless.
- Explicit path — use when overriding what the proxy declared. Example: service ships
  `PERMISSIONS.LIST` with `SocketPermission("*:*","connect")` but this deployment should
  only reach one host; administrator passes `new SocketPermission("db.internal:5432","connect")`.

---

## 8. Authentication Model — No Traditional Login

JGDMS has no username/password login. Authentication is entirely **SPIFFE/SPIRE X.509 SVIDs**
— cryptographic identity certificates provisioned by SPIRE to each workload at startup.

**How a workload establishes identity:**
1. SPIRE is deployed in the environment; each node runs a SPIRE agent.
2. Workload connects to local SPIRE agent via Unix domain socket (the one the bootstrap
   policy grants `java.net.NetPermission "accessUnixDomainSocket"` for; note: there is
   no `UnixDomainSocketPermission` class in the JDK — the JDK uses `NetPermission` with
   action `"accessUnixDomainSocket"` for this guard).
3. SPIRE attests workload identity via OS-level signals (process ID, UID, Kubernetes pod
   metadata, etc.) and issues an X.509 SVID with SPIFFE ID in the SAN field.
4. `SpiffeCredentialManager` (Issue #205, not yet implemented) manages the current
   `SSLContext` and calls `SpiffePolicyFile.refresh()` on SVID rotation (~1 hour lifetime).
5. The certificate is presented automatically on every JERI/TLS connection — identity
   is ambient, not interactive.

**DirtyChai administrator:** DirtyChai runs as a workload with
`spiffe://.../admin/policy` SVID. The human authenticates to the machine running
DirtyChai via OS/SSH. After that, DirtyChai's SPIFFE identity gates access to
`InMemoryPolicyService.replace()`. No separate JGDMS login step exists.

---

## 9. ServiceUI — Deferred Design Item

ServiceUI introduces a human-facing UI component downloaded and run on the client side,
discovered alongside the service proxy and rendered in a Jini browser or equivalent
container. Full design is deferred but the following considerations are flagged:

**Codebase and SCAP:** The ServiceUI JAR is a separate codebase from the service proxy
JAR. It goes through the full SCAP pipeline independently — its own BAE audit,
`RegistryVerdict`, and `ProxyCodebaseSpi` gate before unmarshal. UI code typically needs
display-related permissions (`AWTPermission`, etc.) that proxy code does not — keeping
codebases separate ensures independent `PERMISSIONS.LIST` declarations and independent
verdicts. A `DANGEROUS` verdict on the UI JAR does not condemn the service proxy JAR.
**`META-INF/PERMISSIONS.LIST` parsing by the BAE is now implemented** — `JarAnalysisReport`
carries the declared permissions for every JAR the BAE processes, including ServiceUI JARs
(see Section 5.6 and Section 12 item 10).

**GrantPermission and the UI ClassLoader:** The ServiceUI runs in its own `ClassLoader`
keyed by its own `(InvocationHandler, codebase[], parent)` triple — independent from the
service proxy ClassLoader and therefore independent dynamic grants. UI code is generally
less trusted than service proxy code (more exposed to user-driven inputs) so
`GrantPermission` entries for UI codebases should be scoped more narrowly.

**Human identity threading:** A human user interacting with a ServiceUI may have a
Subject authenticated through Kerberos, smart card, or another mechanism. That Subject's
principals could be threaded into the `Principal[]` scoping of any grants the UI makes.
How human identity maps onto the SPIFFE/djinn identity model is an open design question —
the two identity namespaces (SPIFFE workload identity vs human user identity) need a
clear bridging strategy before ServiceUI grants can be fully specified.

**Status:** Flagged for future design. No implementation decisions made yet.

---

## 10. Architecture Diagrams Produced

Four detailed SVG diagrams were produced this session, each clickable for elaboration.
All four diagrams are committed alongside this document in the same directory:

| Diagram | Focus | Key content |
|---|---|---|
| [Diagram 1 — SCAP five-host pipeline](./diagram1_scap_five_hosts.svg) | SCAP five-host pipeline | Host roles, trust levels, permitted/forbidden connections, data objects, SPIFFE IDs, quorum policy |
| [Diagram 2 — Three-layer policy stack](./diagram2_three_layer_policy_stack.svg) | Three-layer policy stack | Nested layers with implementation detail, IMS wire format, ACC/PD chain callout, recursion guard, bootstrap prereqs |
| [Diagram 3 — Proxy lifecycle](./diagram3_proxy_lifecycle.svg) | Proxy lifecycle | Six phases top-to-bottom with method-level detail, four-path ClassLoader resolution, GC eviction chain |
| [Diagram 4 — GrantPermission & role management](./diagram4_grantpermission_role_management.svg) | GrantPermission & role management | Three-way intersection, delegation chain by role, three administrator levers, risk categorisation table, full SPIFFE scheme |

---

## 11. Files Still To Read Before Implementing

> **Note:** This list reflects the state at the time of writing (context v4). A fresh
> agent should re-verify which files have since been read; citations elsewhere in this
> document are a reliable indicator.

| File | Reason |
|---|---|
| `DynamicPolicy.java` (interface) | Confirm interface contract before finalising `DynamicPolicyProvider` update |
| `PolicyParser.java` (interface) | Confirm `parse(URL, Properties)` signature before implementing `HttpsClientAuthPolicyParser` |
| `LandlordLease` / lease infrastructure | Review before implementing `InMemoryPolicyService` lease management |

### 11.1 Files Already Read

| File | Where cited in this document |
|---|---|
| `DefaultPolicyScanner.java` | §4 (wire format diagram), §12 item 7 (`scanStream()` call site), §13 ("Policy file syntax as wire format", "Server-side parsing via `scanStream()`") |
| `Security.java` (`grant()` methods) | §3.3 (`Security.grant()` intersection enforcement, code examples), §7 (VerifyingProxyPreparer call sites), §13 ("Intersection enforced in `DynamicPolicyProvider.grant()`") |

---

## 12. Remaining Work Items (in order)

1. **✅ `DynamicPolicyProvider.java` — lazy void eviction** *(completed)*
   `it.remove()` pattern applied to all four iterator sites (Section 3.2).
   The old `LinkedList` accumulator + `removeAll()` approach in `refresh()` and
   equivalent iterator loops elsewhere has been replaced with inline `it.remove()`
   calls so void grants are evicted as discovered.

2. **✅ `DefaultPolicyParser.scanner` — `private` → `protected`** *(completed, both repos)*
   Prerequisite for `HttpsClientAuthPolicyParser` and `InMemoryPolicyService` string
   parsing now satisfied; subclasses can access the scanner directly.

3. **`HttpsClientAuthPolicyParser`** — HTTPS + SPIFFE client cert URL opening.
   Subclass of `DefaultPolicyParser`. Depends on `SpiffeCredentialManager` interface.

4. **`SpiffePolicyFile`** — trivial wrapper once `HttpsClientAuthPolicyParser` exists.

5. **`SpiffeCredentialManager`** — Issue #205
   SPIRE Workload API socket integration. Must be instantiable before `SecurityManager`
   is fully active. Provides `getCurrentSSLContext()`. Calls `SpiffePolicyFile.refresh()`
   on SVID rotation.

6. **`RemotePolicyService` interface** — new JERI wire interface (Section 4).

7. **`InMemoryPolicyService`** — JERI service skeleton
   - Implements `RemotePolicyService`
   - Server-side `String[]` → `PermissionGrant[]` parsing via `DefaultPolicyScanner.scanStream()`
   - `replace()` permission validation after parsing
   - `PolicyUpdateEvent extends RemoteEvent`
   - Lease management via `LandlordLease`
   - Async event dispatch via bounded queue + dispatcher thread
   - SPIFFE SVID: `spiffe://jgdms.example.org/host/policy`
   - Only hosts with admin SVID (`spiffe://jgdms.example.org/admin/policy`) may call `replace()`

8. **DirtyChai smart proxy client for `RemotePolicyService`**
   - `PermissionGrant.toString()` → `String[]` → JERI wire
   - `getCurrentGrants()` response: `String[]` → parse back to `PermissionGrant[]`
   - No `@AtomicSerial` dependency at either end

9. **Client-side `RemoteEventListener`**
   Sequence number tracking, gap detection, pull-on-notification, lease renewal.

10. **✅ `PERMISSIONS.LIST` BAE integration** *(completed v3)*
    `JarAnalyzer` now detects `META-INF/PERMISSIONS.LIST` during Phase 1 JAR scanning.
    Non-blank, non-`#`-comment lines are stored in `JarAnalysisReport.getDeclaredPermissions()`
    (`String[]`, `serialVersionUID=2L`).  Declared permissions are sorted and included in the
    canonical signing bytes.  Old v1 reports remain deserializable (null-tolerant `check()`).
    **Remaining:** surface declared permissions through `VerdictRegistry` to DirtyChai UI
    for administrator cross-referencing against `GrantPermission` policy.

---

## 13. Key Design Decisions — Cumulative

| Decision | Rationale |
|---|---|
| No `ReferenceQueue` in `DynamicPolicyProvider` | ACC is collected before or concurrent with PD weak ref enqueue; `clearCache()` would be a no-op |
| No `clearCache()` on dynamic grant GC | ACC self-evicts; no stale entries remain by the time void is detected |
| Lazy void eviction via `it.remove()` on all iterators | Void grants evicted as discovered; no separate cleanup pass |
| `refresh()` simplified to `it.remove()` | Eliminates `LinkedList` accumulator; same correctness guarantee |
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
| `AdvisoryDynamicPermissions` on ClassLoader, not proxy class | ClassLoader is the natural owner of codebase-wide permission declarations; reads `META-INF/PERMISSIONS.LIST` |
| ClassLoader keyed by `(InvocationHandler, codebase[], parent)` | Endpoint identity (not just codebase URL) determines ClassLoader; two services sharing a JAR get independent ClassLoaders and independent grants |
| Intersection enforced in `DynamicPolicyProvider.grant()` | Preparer is not security-critical; `Security.grant()` enforces `GrantPermission` ceiling regardless of what advisory path requests |
| Advisory grants are best-effort | `UnsupportedOperationException` in advisory path → logged, not rethrown; proxy still usable with reduced permissions |
| Explicit permissions in preparer override advisory | Administrator can lock grant to specific list regardless of `PERMISSIONS.LIST` |
| `createVirtualThread` in `GrantPermission` requires care | Once granted, blocking `<clinit>` paths become reachable — on pre-JEP 491 JVMs (JDK ≤ 23) the carrier is pinned; on JDK 24+ the class-loading lock is held; either way it is a Denial of Service risk; process isolation is the backstop |
| `PolicyPermission("Remote")` and `GrantPermission` itself must never be delegatable | Would allow proxies to participate in policy machinery or expand their own delegation rights |
| SCAP validates code safety; policy validates runtime authority | Complementary controls at different phases; SCAP-SAFE does not imply well-scoped grants |
| `JarAnalysisReport` carries `String[] declaredPermissions` from `META-INF/PERMISSIONS.LIST` (`serialVersionUID=2L`) | Enables cross-referencing declared needs against `GrantPermission` ceiling without re-downloading the JAR; sorted permissions included in signing bytes so the declared set cannot be silently stripped by a compromised pipeline |
| `parsePermissionsList()` strips blank lines and `#`-comments | Matches the de-facto convention used in existing JGDMS `PERMISSIONS.LIST` files; comment support allows copyright/authorship headers in the file |
| Old 3-arg `JarAnalysisReport` constructor delegates to 4-arg with empty array | Backward compatible — all existing callers continue to compile and run; v1 serialized reports also deserialize cleanly (null-tolerant `check()`) |
| Explicit preparer path → hard `SecurityException` on grant failure | Administrator made a positive decision; failure = deployment misconfiguration, not graceful degradation |
| Advisory preparer path → soft failure (logged only) | Grant is best-effort; proxy still usable without it; graceful degradation is correct |
| Authentication is SPIFFE/SPIRE workload identity; no traditional login | Identity is ambient — provisioned by SPIRE at workload startup; no interactive credential step at JGDMS layer |
| ServiceUI JAR is a separate codebase from service proxy JAR | Independent SCAP audit, verdict, ClassLoader, and dynamic grants; UI grants should be scoped more narrowly than proxy grants |
| Human identity threading into ServiceUI grants is an open design question | SPIFFE workload identity and human user identity namespaces need a bridging strategy before ServiceUI GrantPermission design can be finalised |
| `BLOCKING_GUARDED` is `INCONCLUSIVE`, not `DANGEROUS` | The blocking path is only reachable if the guarding permission is granted; policy authors can choose not to grant it |
| `BLOCKING_DECLARED` is `DANGEROUS` | The JAR's own `PERMISSIONS.LIST` declares the guarding permission, signalling developer intent to request it; if a client grants it, the blocking `<clinit>` path becomes reachable on a virtual thread — on pre-JEP 491 JVMs (JDK ≤ 23) the carrier is pinned; on JDK 24+ the class-loading lock is held — either way a Denial of Service; administrators must treat any `BLOCKING_DECLARED` verdict as a strong signal **not to grant** the declared permission |
| `SINK_TO_PERMISSION_CLASS` maps direct per-call guards (JDK + DirtyChai) | Sinks guarded at construction time only are excluded. Covered: network I/O (`SocketPermission`), file locking (`FilePermission`), native library loading (`NativeInvocationPermission`), FFM arena allocation (`NativeMemoryPermission`), thread creation (`RuntimePermission#createPlatformThread` / `#createVirtualThread`) |
| `className#action` in `SINK_TO_PERMISSION_CLASS` values | Used for broad permission classes (e.g. `RuntimePermission`) to avoid false positives: a JAR declaring `RuntimePermission "getenv"` must not trigger `BLOCKING_DECLARED` for thread-creation sinks; both class name and quoted action must appear on the same `PERMISSIONS.LIST` line |

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
by this one for all topics covered here. This is version 4, updated to add:
Section 7 (VerifyingProxyPreparer constructor detail), Section 8 (authentication model),
Section 9 (ServiceUI deferred design), Section 10 (diagrams produced), and additional
entries in the key design decisions table (Sections 7, 8, 9) — in version 2; and
in version 3: `JarAnalysisReport.declaredPermissions` implementation (Section 2.1,
Section 5.6 "Implemented", Section 9, Section 12 item 10 marked ✅, Section 13 new rows);
and in version 4: `BLOCKING_DECLARED` verdict (Section 2.1 BAE analyses, Section 5.7
risk categorisation updated — `SocketPermission` moved to DoS-risk tier, Section 13 new
rows for `BLOCKING_DECLARED`, `BLOCKING_GUARDED` distinction, and `SINK_TO_PERMISSION_CLASS`).*
