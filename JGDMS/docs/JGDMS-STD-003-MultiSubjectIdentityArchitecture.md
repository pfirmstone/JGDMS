# JGDMS-STD-003: Multi-Subject Identity Architecture

**Status:** SUPERSEDED — see `JGDMS-STD-003-MultiSubjectIdentityArchitecture-v3.md` (v3.3+).  
**Version:** 1.0 (historical)  
**Applies to:** DirtyChai (JDK fork), JGDMS  
**Supersedes:** Ad-hoc two-Subject model documented in AI Agent context v8–v16  

> ⚠️ **SUPERSEDED.** This is the original draft; the current model is in **v3**. Kept for history only.

---

## 1. Purpose and Scope

This specification defines the multi-Subject identity architecture for JGDMS and its
underlying DirtyChai JDK fork. It specifies:

- The sealed `Subject` type hierarchy and the semantics of each subtype
- How each Subject type is established, carried, and propagated
- How each Subject type participates in permission checks
- How `doPrivileged` boundaries interact with each Subject type
- Thread propagation rules for spawned threads and executor tasks
- Transport endpoint Subject selection rules
- The migration path toward `DomainCombiner` retirement

This specification does not cover SPIRE provisioning, the three-layer policy stack, or
the SCAP bytecode analysis pipeline, which are covered by JGDMS-STD-001 and
JGDMS-STD-002 respectively.

---

## 2. Motivation

### 2.1 Insufficiency of the Single-Subject ACC Model

The standard JDK security model associates at most one `Subject` with an
`AccessControlContext` (ACC) via a `SubjectDomainCombiner`. This model has several
fundamental limitations in a distributed, multi-party system:

**Identity conflation.** A single Subject cannot cleanly represent the distinct
identities of the local process, the remote peer process, and the human user
simultaneously. Earlier JGDMS versions attempted a two-Subject model (workload on ACC,
user on `ScopedValue`) but this still conflates local and remote process identity.

**`doPrivileged` drops Subject identity.** A plain `doPrivileged` call drops the
`SubjectDomainCombiner` from the ACC. This means policy grants conditioned on a SPIFFE
workload principal are not enforced inside `doPrivileged` blocks — a significant
security gap when the goal is mandatory enforcement of execution environment identity
(e.g. requiring SELinux).

**No trust tier for execution environment.** The standard model has no mechanism to
distinguish a process running on a hardened SELinux host from one running on a home
device. All processes are treated identically regardless of their execution environment.

**No multi-party transaction support.** A single Subject cannot represent multiple
simultaneously-required identities (e.g. two parties to a financial transaction both
of whom must be present for a commit permission to be granted).

**No type-level distinction between identity classes.** Without type-level distinctions,
routing logic (SSL endpoint uses workload Subject; Kerberos endpoint uses user Subject)
must rely on principal inspection rather than type dispatch, which is fragile and
error-prone.

### 2.2 Design Goals

1. **Mandatory environmental enforcement.** The local process identity (SELinux, OpenBSD,
   Windows, home device) must be enforced at every permission check including inside
   `doPrivileged` blocks. It must be impossible for application code to shed or bypass
   this constraint.

2. **Correct `doPrivileged` semantics.** `doPrivileged` asserts "this code takes
   responsibility." Caller-side identities (remote peer, user) are correctly shed at
   `doPrivileged` boundaries. Environmental identity (local process) is not.

3. **Multi-party transaction support.** Multiple remote workers and users must be
   expressible simultaneously, with policy grants conditioned on all of them being
   present.

4. **Type safety.** Each identity class is a distinct sealed type. Routing, injection,
   and policy enforcement rules are expressed as type dispatch, not principal inspection.

5. **Backwards compatibility.** Existing code using vanilla `Subject`, `Subject.doAs()`,
   and `Subject.current()` continues to work unchanged.

6. **Migration path.** The architecture provides a clear path toward retirement of
   `DomainCombiner` and direct principal injection in permission checks.

---

## 3. The Four Identity Layers

### 3.1 Overview

| Layer | Type | Carrier | `doPrivileged` | Lifetime | Constructed by |
|---|---|---|---|---|---|
| Local process worker | `LocalWorkerSubject` | ACC (`doAs`) | **Survives** | JVM lifetime | SPIRE only (sealed) |
| Remote process worker | `RemoteWorkerSubject` | `SCOPED_SUBJECTS` ScopedValue | Shed | Per-connection | JERI dispatcher |
| User | `RemoteUserSubject` | `SCOPED_SUBJECTS` ScopedValue | Shed | Per-request | JERI dispatcher |
| Local user (legacy) | `Subject` (vanilla) | `SCOPED_SUBJECTS` ScopedValue or ACC | Shed (ScopedValue path) | Per-session | JAAS `LoginContext` |

### 3.2 Local Process Worker

The local process worker represents the execution environment of the JVM itself — the
machine, its operating system, and its security posture. It is provisioned by SPIRE at
workload startup and rotated automatically on SVID expiry.

**Key properties:**
- Static for the JVM lifetime (SVID rotation updates credentials, not identity)
- Always present at every permission check — there is no execution context in which
  the local worker is absent
- Injected into both stack domains AND `privilegedContext` — survives `doPrivileged`
- Used for outbound TLS connections — `SslEndpointImpl` accepts only `LocalWorkerSubject`
  as the TLS credential source
- Never passed to `Subject.callAs()` — it is ambient, not caller-supplied
- Can only be constructed by SPIRE infrastructure code (enforced by sealed hierarchy
  and package-private constructor)

**Trust tiers expressed via SPIFFE SVID:**
```
spiffe://jgdms.example.org/host/selinux/svc-name    — hardened SELinux host
spiffe://jgdms.example.org/host/openbsd/svc-name    — hardened OpenBSD host
spiffe://jgdms.example.org/host/windows/svc-name    — Windows host (lower trust)
spiffe://jgdms.example.org/host/home/svc-name       — home device (lowest trust)
```

Policy grants can condition permissions on the local worker's SPIFFE principal,
enforcing that sensitive operations are only permitted on appropriately hardened hosts.

### 3.3 Remote Process Worker

The remote process worker represents the SPIFFE identity of a remote machine that has
established a connection to this JVM. It is reconstructed from the peer's TLS certificate
during JERI dispatch.

**Key properties:**
- Per-connection lifetime
- Carried as a `ScopedValue` (`SCOPED_SUBJECTS`) — not embedded in ACC
- Injected into stack domains only — shed at `doPrivileged` boundaries
- Represents the peer machine's trust tier
- May be absent (legacy clients without SPIFFE identity)
- Multiple `RemoteWorkerSubject` instances may be present simultaneously
  (transaction context)

### 3.4 Remote User Subject

The remote user subject represents an authenticated human user whose request has been
dispatched to this JVM. It is reconstructed from the JERI wire header (protocol v0x02).

**Key properties:**
- Per-request lifetime
- Carried as a `ScopedValue` (`SCOPED_SUBJECTS`) — not embedded in ACC
- Injected into stack domains only — shed at `doPrivileged` boundaries
- Read-only, no credentials
- May be absent (service-to-service calls with no user context)
- Multiple `RemoteUserSubject` instances may be present simultaneously
  (transaction context — multiple users must authorise a single operation)

### 3.5 Local User Subject (Legacy)

The vanilla `Subject` class (non-sealed base) represents a locally-authenticated user,
typically via JAAS `LoginContext` (Kerberos, etc.). This is the pre-existing Subject
type and retains its existing semantics for backwards compatibility.

**Key properties:**
- `Subject.doAs()` routes to this type — unchanged legacy semantics
- `Subject.current()` returns this type (or `RemoteUserSubject`) — backwards compatible
- Used by `KerberosEndpoint` for GSS credential acquisition
- May be carried on `SCOPED_SUBJECTS` ScopedValue or on ACC via `doAs`

---

## 4. The Sealed Subject Hierarchy

### 4.1 Class Definitions

```java
/**
 * Base class for all Subject types. Vanilla Subject represents a locally-
 * authenticated user (Kerberos, JAAS). Sealed to permit only the defined
 * identity subtypes.
 */
public sealed class Subject
    permits LocalWorkerSubject, RemoteWorkerSubject, RemoteUserSubject {
    // existing Subject implementation
}

/**
 * Represents the local JVM process identity, provisioned by SPIRE.
 * Injected into both stack domains and privilegedContext — survives
 * doPrivileged boundaries. Used for outbound TLS. Never passed to callAs().
 * Cannot be constructed by application code.
 */
public final class LocalWorkerSubject extends Subject {
    // Package-private constructor — only constructible by SpiffeCredentialManager
    LocalWorkerSubject(Set<Principal> principals,
                       Set<Object> pubCredentials,
                       Set<Object> privCredentials) {
        super(true, principals, pubCredentials, privCredentials);
    }
}

/**
 * Represents the SPIFFE identity of a remote peer process. Carried as a
 * ScopedValue. Injected into stack domains only — shed at doPrivileged
 * boundaries. Represents the remote machine's trust tier.
 */
public final class RemoteWorkerSubject extends Subject {
    public RemoteWorkerSubject(Set<Principal> principals) {
        super(true, principals, emptySet(), emptySet());
    }
}

/**
 * Represents a remotely-asserted human user identity. Carried as a
 * ScopedValue. Injected into stack domains only — shed at doPrivileged
 * boundaries. Read-only, no credentials.
 */
public final class RemoteUserSubject extends Subject {
    public RemoteUserSubject(Set<Principal> principals) {
        super(true, principals, emptySet(), emptySet());
    }
}
```

### 4.2 Construction Constraints

| Type | Constructor access | Rationale |
|---|---|---|
| `Subject` | Public | Backwards compatibility; JAAS LoginContext |
| `LocalWorkerSubject` | Package-private (`javax.security.auth`) | Only SPIRE infrastructure may create a local worker identity |
| `RemoteWorkerSubject` | Public | JERI dispatcher constructs from peer TLS certificate |
| `RemoteUserSubject` | Public | JERI dispatcher constructs from wire header |

### 4.3 No Further Subclassing

`LocalWorkerSubject`, `RemoteWorkerSubject`, and `RemoteUserSubject` are all `final`.
No further subclassing is permitted unless a concrete need is identified, at which point
the sealed hierarchy will be extended with a new `permits` entry. This ensures all
identity routing and injection rules remain exhaustive and compiler-verified.

---

## 5. `Subject::callAs(Subject... subjects)`

### 5.1 Signature

```java
/**
 * Executes a Callable with the provided subjects as the current scoped
 * identity for the duration of the call on the current thread.
 *
 * <p> The provided subjects are bound to the SCOPED_SUBJECTS ScopedValue
 * for the duration of {@code action}. They are injected into the
 * AccessControlContext's ProtectionDomain array at getContext() time,
 * enabling principal-scoped policy grants.
 *
 * <p> LocalWorkerSubject must not be passed to this method — the local
 * process identity is always ambient and is never caller-supplied.
 *
 * <p> Calls may be nested; each nested call shadows the previous bindings
 * for its duration, restoring them when action completes, whether normally
 * or exceptionally.
 *
 * @param subjects the Subject instances to bind. Must not contain
 *                 LocalWorkerSubject. Must not be null or empty.
 * @param action   the code to execute. Must not be null.
 */
public static <T> T callAs(Callable<T> action, Subject... subjects)
    throws CompletionException { ... }
```

### 5.2 Varargs Contract

- `LocalWorkerSubject` instances in the array cause `IllegalArgumentException` — the
  local worker is ambient and must never be caller-supplied
- Empty array is permitted — equivalent to executing with no scoped identity
- `null` array or `null` elements cause `NullPointerException`
- Order within the array is not significant for permission checks — all principals
  from all Subjects are merged additively
- Order is significant for `Subject.current()` — the first `RemoteUserSubject` or
  vanilla `Subject` in the array is returned

### 5.3 Common Call Patterns

```java
// Service-to-service — remote peer only
Subject.callAs(action, remoteWorkerSubject);

// Typical request — remote peer + authenticated user
Subject.callAs(action, remoteWorkerSubject, remoteUserSubject);

// Two-party transaction — two peers, two users
Subject.callAs(action,
    remoteWorkerA, remoteWorkerB,
    remoteUserAlice, remoteUserBob);

// Legacy JAAS user only
Subject.callAs(action, jaasUserSubject);

// Local operation — local worker always implicit, no callAs needed
```

### 5.4 Nesting and Shadowing

Nested `callAs` invocations shadow the outer bindings for their duration:

```java
Subject.callAs(action1, remoteWorkerA, userAlice);    // outer scope
    Subject.callAs(action2, remoteWorkerB, userBob);  // inner scope — shadows outer
    // action2 sees only remoteWorkerB and userBob
// outer scope restored — action1 sees remoteWorkerA and userAlice again
```

This enables per-operation identity scoping within a transaction without the outer
identity leaking into nested operations.

---

## 6. `Subject::doAs` Routing

### 6.1 Type Constraints

```java
public static <T> T doAs(Subject subject, PrivilegedAction<T> action) {
    if (subject instanceof RemoteWorkerSubject
            || subject instanceof RemoteUserSubject) {
        throw new IllegalArgumentException(
            "RemoteWorkerSubject and RemoteUserSubject must use Subject.callAs()");
    }
    if (subject instanceof LocalWorkerSubject) {
        throw new IllegalArgumentException(
            "LocalWorkerSubject is established by SPIRE infrastructure, " +
            "not by application doAs() calls");
    }
    // existing doAs implementation — vanilla Subject and null only
}
```

### 6.2 Retained Uses

`Subject.doAs()` is retained for:

- **`LocalWorkerSubject` establishment** — called by `SpiffeCredentialManager` at
  workload startup to install the local worker identity into the ACC. This is the
  one legitimate `doAs` call that installs a Subject into `privilegedContext`.
- **Kerberos GSS-API** — `KerberosUtil.getGSSCredential()` and
  `KerberosServerEndpoint` require a Subject in the ACC for GSS context
  establishment. The JDK GSS-API implementation reads the Subject from the ACC
  internally. This is a JDK constraint and cannot be changed without patching
  `sun.security.jgss`.
- **Legacy JAAS** — `AbstractJiniService` supports traditional JAAS `LoginContext`
  subjects for non-SPIFFE services.

### 6.3 Deprecation Intent

`Subject.doAs()` and `Subject.doAsPrivileged()` are retained but their scope of use
is intentionally narrowed. New code should use `Subject.callAs()` for all identities
other than the local worker and Kerberos GSS-API. These methods may be formally
deprecated in a future version once OpenJDK provides a `callAs`-based GSS-API path.

---

## 7. Subject Retrieval API

### 7.1 `Subject.current()`

Returns the first `RemoteUserSubject` or vanilla `Subject` found in `SCOPED_SUBJECTS`,
or `null` if none is bound. Backwards-compatible with existing callers.

```java
public static Subject current() {
    if (!SCOPED_SUBJECTS.isBound()) return null;
    for (Subject s : SCOPED_SUBJECTS.get()) {
        if (s instanceof RemoteUserSubject || s.getClass() == Subject.class)
            return s;
    }
    return null;
}
```

### 7.2 `Subject.currentAll()`

Returns all Subjects currently bound in `SCOPED_SUBJECTS`, or an empty array if none
are bound. Never returns `null`. Never includes `LocalWorkerSubject`.

```java
public static Subject[] currentAll() {
    if (!SCOPED_SUBJECTS.isBound()) return new Subject[0];
    return SCOPED_SUBJECTS.get().clone();
}
```

### 7.3 `Subject.currentRemoteWorker()`

Returns the first `RemoteWorkerSubject` found in `SCOPED_SUBJECTS`, or `null`.

```java
public static RemoteWorkerSubject currentRemoteWorker() {
    if (!SCOPED_SUBJECTS.isBound()) return null;
    for (Subject s : SCOPED_SUBJECTS.get()) {
        if (s instanceof RemoteWorkerSubject rws) return rws;
    }
    return null;
}
```

### 7.4 `Subject.getLocalWorker()`

Returns the `LocalWorkerSubject` from the ACC, or `null` if none is installed.
This is the only way to retrieve the local worker Subject — it is never available
via `current()` or `currentAll()`.

```java
public static LocalWorkerSubject getLocalWorker() {
    AccessControlContext acc = AccessController.getContext();
    Subject s = Subject.getSubject(acc);
    return s instanceof LocalWorkerSubject lws ? lws : null;
}
```

---

## 8. `AccessController.getContext()` — Injection Rules

### 8.1 Overview

After `optimize()`, `getContext()` reads `SCOPED_SUBJECTS` and injects the principals
of each scoped Subject into the ACC's `ProtectionDomain` array. The injection is
type-dispatched:

```java
Subject[] scoped = SCOPED_SUBJECTS.isBound() ? SCOPED_SUBJECTS.get() : null;
if (scoped != null && scoped.length > 0) {
    // Collect all principals from all scoped Subjects
    Set<Principal> allPrincipals = new LinkedHashSet<>();
    for (Subject s : scoped) {
        // LocalWorkerSubject never appears in SCOPED_SUBJECTS — assertion only
        assert !(s instanceof LocalWorkerSubject);
        allPrincipals.addAll(s.getPrincipals());
    }
    
    DomainCombiner existing = acc.getCombiner();
    SubjectDomainCombiner sdc = new SubjectDomainCombiner(
        subjectFromPrincipals(allPrincipals)); // transient Subject for combine()
    
    // Bake principals into stack domains
    ProtectionDomain[] combined = sdc.combine(
        acc.getContext(), acc.getContext());
    
    // Bake principals into immediate privilegedContext domains
    // (only immediate — nested chain predates this callAs scope)
    AccessControlContext privileged = acc.privilegedContext;
    if (privileged != null) {
        ProtectionDomain[] combinedPrivileged = sdc.combine(
            privileged.getContext(), privileged.getContext());
        privileged = AccessControlContext.create(
            combinedPrivileged,
            privileged.privilegedContext,   // preserve nested chain as-is
            privileged.getCombiner(),        // preserve existing combiner
            privileged.isPrivileged());      // preserve privileged flag
    }
    
    // Restore original combiner; preserve all ACC structural properties
    acc = AccessControlContext.create(
        combined,
        privileged,
        existing,
        acc.isPrivileged());  // MUST preserve — erasing this is a security error
}
return acc;
```

### 8.2 Injection Rules by Type

| Subject type | Stack domains | `privilegedContext` | Rationale |
|---|---|---|---|
| `LocalWorkerSubject` | Never in `SCOPED_SUBJECTS` | In ACC via `doAs` | Environmental — already in ACC; survives `doPrivileged` naturally |
| `RemoteWorkerSubject` | Injected | Not injected | Caller-side — shed at `doPrivileged` boundary |
| `RemoteUserSubject` | Injected | Not injected | Caller-side — shed at `doPrivileged` boundary |
| Vanilla `Subject` | Injected | Not injected | Caller-side — JAAS user; shed at `doPrivileged` boundary |

### 8.3 `isPrivileged` Preservation

The `isPrivileged` flag on the optimised ACC MUST be preserved in the reconstructed
ACC. Setting this to `false` unconditionally would silently erase `doPrivileged`
boundaries established by the calling code, eliminating stack truncation in permission
checks. This is a security error.

### 8.4 Bootstrap Safety

`SCOPED_SUBJECTS.isBound()` is only called when `VM.isBooted()`. During JVM bootstrap,
before `ScopedValue` infrastructure is initialised, the injection step is skipped
entirely. No `callAs` scope can be active during bootstrap, so this is always correct.

---

## 9. `SubjectDomainCombiner.combine()` — Type-Dispatch Rules

### 9.1 Current Implementation (Phase 1)

`SubjectDomainCombiner.combine()` is responsible for merging the local worker's
principals (from the ACC-bound Subject) with any scoped Subjects from `SCOPED_SUBJECTS`:

```java
@Override
public ProtectionDomain[] combine(ProtectionDomain[] current,
                                   ProtectionDomain[] assigned) {
    // Local worker principals — from ACC-bound Subject (LocalWorkerSubject)
    Set<Principal> localWorkerPrincipals = subject != null
        ? subject.getPrincipals() : Collections.emptySet();

    // Scoped principals — from SCOPED_SUBJECTS (remote worker + user)
    // Note: injection already happened in getContext(); this handles the
    // checkPermission path that calls optimize() → goCombiner() directly
    Set<Principal> scopedPrincipals = new LinkedHashSet<>();
    if (SCOPED_SUBJECTS.isBound()) {
        for (Subject s : SCOPED_SUBJECTS.get()) {
            scopedPrincipals.addAll(s.getPrincipals());
        }
    }

    // Merge all principals additively — neither displaces the other
    Set<Principal> merged = new LinkedHashSet<>(localWorkerPrincipals);
    merged.addAll(scopedPrincipals);

    return buildCombinedDomains(current, assigned, merged);
}
```

### 9.2 `neverPrivileged` Daemon Thread Guard

`AccessControlContext.neverPrivileged()` returns a context containing a static
unprivileged `ProtectionDomain` that `SubjectDomainCombiner` is coded to detect and
skip. When this domain is present, no Subject principals are injected regardless of
what is bound in `SCOPED_SUBJECTS` or the ACC.

This provides a hard barrier for daemon threads — even if a daemon thread is
accidentally constructed inside a `callAs` scope, it cannot be enriched with caller
identity:

```java
// In SubjectDomainCombiner.combine():
if (containsNeverPrivilegedDomain(current) || containsNeverPrivilegedDomain(assigned)) {
    return buildCombinedDomains(current, assigned, Collections.emptySet());
}
```

---

## 10. `doPrivileged` Boundary Semantics

### 10.1 What Survives `doPrivileged`

| Identity | Survives `doPrivileged` | Mechanism | Rationale |
|---|---|---|---|
| Local process worker | **Yes** | In `privilegedContext` via `doAs` | Environmental — this IS the process; cannot be shed |
| Remote process worker | **No** | `SCOPED_SUBJECTS` not in `privilegedContext` | Caller-side — code takes responsibility |
| User | **No** | `SCOPED_SUBJECTS` not in `privilegedContext` | Caller-side — code takes responsibility |

### 10.2 Security Significance

A `doPrivileged` block asserts: *"I, this code, take responsibility for this
operation. Check only my ProtectionDomain."* This is a privilege assertion by the
code itself, not a relaxation of environmental constraints.

The local worker principal survives because it answers the question: *"What environment
is this code actually running in?"* A `doPrivileged` block on a Windows machine cannot
claim SELinux-level trust. The execution environment constrains the ceiling of what any
`doPrivileged` block can be granted, regardless of what code it contains.

### 10.3 Policy Implications

```
// This grant requires SELinux even inside doPrivileged blocks:
grant codeBase "file:/opt/jgdms/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/order-processor" {
    permission SensitiveOperationPermission "execute";
};

// A Windows host running the same code is denied — even inside doPrivileged:
// spiffe://.../host/windows/order-processor does not match the grant above
```

### 10.4 `doPrivilegedWithCombiner`

`AccessController.doPrivilegedWithCombiner()` explicitly carries the current
`DomainCombiner` forward across the `doPrivileged` boundary. For `LocalWorkerSubject`
this is not needed — the local worker is already in `privilegedContext`. For remote
workers and users, `doPrivilegedWithCombiner` should not be used to carry them forward
— doing so would violate the intended `doPrivileged` semantics of shedding caller
identity.

---

## 11. Thread Propagation

### 11.1 Spawned Threads (`new Thread(...).start()`)

When a thread is constructed inside a `callAs` scope:

1. `AccessController.getContext()` is called at construction time, producing an ACC
   with scoped Subject principals already baked into the domain array.
2. This enriched ACC is stored as `inheritedAccessControlContext`.
3. The `SCOPED_SUBJECTS` array is captured into `Thread.scopedSubjects` at
   construction time (guarded by `VM.isBooted()`).
4. `Thread.runWith()` (`final` — applies to both platform and virtual threads)
   re-establishes the `SCOPED_SUBJECTS` binding via `Subject.SubjectAccess.callNoCheck()`
   before invoking the task, ensuring `Subject.current()` and `Subject.currentAll()`
   work correctly in the spawned thread.

**This diverges from OpenJDK**, where `Subject.callAs` identity does not propagate to
threads started outside `StructuredTaskScope`. In JGDMS, propagation occurs for all
spawned threads. This is intentional — code spawning threads during request processing
should naturally inherit the authenticated identity without requiring structured
concurrency.

### 11.2 Executor-Submitted Tasks

Tasks submitted to an `Executor` do **not** automatically inherit scoped identity. The
`ScopedValue` binding is not in effect in the worker thread. Callers who need identity
to cross executor boundaries must use the explicit wrapper:

```java
public static Runnable wrap(Runnable task) {
    Subject[] captured = Subject.currentAll();
    AccessControlContext acc = AccessController.getContext();
    if (captured.length == 0) return task; // no identity to propagate
    return () -> AccessController.doPrivileged(
        (PrivilegedAction<Void>) () -> {
            Subject.callAs(() -> { task.run(); return null; }, captured);
            return null;
        }, acc);
}
```

This makes executor identity propagation **explicit and deliberate**. Daemon threads
and background tasks that must not carry caller identity simply do not use the wrapper.

### 11.3 Daemon Thread Discipline

Long-lived daemon threads (sweeper, SPIRE watcher, log writer, event dispatcher) must
be constructed outside any `callAs` scope, OR must use `AccessControlContext.neverPrivileged()`
to permanently prevent Subject injection:

```java
// Preferred — construct outside callAs scope
Thread sweeper = new Thread(this::sweep);
sweeper.setDaemon(true);

// Alternative — neverPrivileged prevents any Subject injection
Thread sweeper = new Thread(
    AccessControlContext.neverPrivileged(),
    this::sweep,
    "JGDMS-Sweeper");
```

---

## 12. Transport Endpoint Subject Selection

### 12.1 `SslEndpointImpl.getCallContext()` — TLS/SPIFFE

TLS requires an X.509 certificate. Only `LocalWorkerSubject` carries SPIFFE SVID
credentials suitable for TLS.

```
Priority 1: LocalWorkerSubject from ACC          — primary; SPIFFE SVID credentials
Priority 2: SpiffeSubjectHolder.get()            — fallback; process-wide SPIFFE Subject
Priority 3: Subject.current() filtered           — last resort; accepted only if
                                                   X500Principal or SpiffePrincipal present;
                                                   RemoteUserSubject always rejected
```

Type check:
```java
Subject s = Subject.getSubject(acc);
if (s instanceof LocalWorkerSubject lws) {
    // use lws for TLS — definitive
} else if (s != null) {
    // not a LocalWorkerSubject — fall through to SpiffeSubjectHolder
}
```

### 12.2 `KerberosEndpoint.newRequest()` — Kerberos GSS

Kerberos requires a per-user TGT. `RemoteUserSubject` or vanilla `Subject` carries
Kerberos credentials.

```
Priority 1: Subject.current()                    — RemoteUserSubject or vanilla Subject;
                                                   accepted only if KerberosPrincipal present
Priority 2: Subject.getSubject(acc) filtered     — ACC Subject fallback;
                                                   accepted only if KerberosPrincipal present;
                                                   LocalWorkerSubject always rejected for Kerberos
```

Type check:
```java
Subject current = Subject.current();
if (current instanceof RemoteUserSubject rus && hasKerberosPrincipal(rus)) {
    // use rus — per-request user Kerberos context
} else if (current != null && current.getClass() == Subject.class
           && hasKerberosPrincipal(current)) {
    // use current — legacy JAAS user
}
```

---

## 13. Policy Grant Examples

### 13.1 Single-Party Request — All Four Layers

```
grant codeBase "file:/opt/jgdms/order-processor/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/order-processor"
      principal SpiffePrincipal "spiffe://.../svc/trusted-client"
      principal KerberosPrincipal "alice@EXAMPLE.ORG" {
    permission OrderPermission "submit";
};
```

This grant requires simultaneously:
- Code from the order-processor codebase
- Running on an SELinux host (local worker — mandatory, survives `doPrivileged`)
- Called by the trusted-client service (remote worker — shed at `doPrivileged`)
- On behalf of Alice (user — shed at `doPrivileged`)

### 13.2 Trust Tier Differentiation

```
// SELinux host — full financial operations
grant principal SpiffePrincipal "spiffe://.../host/selinux/payment-svc" {
    permission FinancialPermission "execute,read,audit";
};

// Windows host — read only
grant principal SpiffePrincipal "spiffe://.../host/windows/payment-svc" {
    permission FinancialPermission "read";
};

// Home device — no access to payment service
// (no grant — deny-all baseline applies)
```

### 13.3 Multi-Party Transaction

```
// Both parties must be present simultaneously for commit
grant principal SpiffePrincipal "spiffe://.../host/selinux/txn-svc"
      principal SpiffePrincipal "spiffe://.../svc/party-a"
      principal SpiffePrincipal "spiffe://.../svc/party-b"
      principal KerberosPrincipal "alice@EXAMPLE.ORG"
      principal KerberosPrincipal "bob@EXAMPLE.ORG" {
    permission TransactionPermission "commit";
};
```

Neither party alone can commit. Both remote workers and both users must be
simultaneously present in the `SCOPED_SUBJECTS` binding. This is enforced at the JVM
level — no framework bypass is possible.

### 13.4 Service-to-Service (No User)

```
// Peer service can read audit log without a user Subject
grant principal SpiffePrincipal "spiffe://.../host/selinux/audit-svc"
      principal SpiffePrincipal "spiffe://.../svc/trusted-auditor" {
    permission AuditPermission "read";
};
```

---

## 14. Migration Path

### 14.1 Phase 1 — Current State (Multi-Subject model implemented)

- Sealed Subject hierarchy in place
- `Subject.callAs(Callable, Subject...)` implemented
- `SCOPED_SUBJECTS` ScopedValue carries `Subject[]`
- `AccessController.getContext()` bakes scoped principals into domain array
- `SubjectDomainCombiner.combine()` reads both ACC-bound and scoped Subjects
- `Thread.runWith()` re-establishes `SCOPED_SUBJECTS` for spawned threads
- `DomainCombiner` retained; `CombinerSecurityManager` unchanged

### 14.2 Phase 2 — Direct Principal Injection in `checkPermission`

Replace `SubjectDomainCombiner` combiner dispatch with a direct private path in
`checkPermission` that reads `SCOPED_SUBJECTS` and the local worker Subject from
the ACC without combiner invocation:

- Eliminates `goCombiner()` dispatch overhead
- Eliminates `combine()` virtual call
- Eliminates `ProtectionDomain[]` array allocation per `checkPermission` call
- One `CombinerSecurityManager` cache (the combiner dispatch cache) can be removed
- `DomainCombiner` interface retained for external implementations but no longer
  used internally for Subject injection

### 14.3 Phase 3 — `DomainCombiner` Retirement

Once all internal uses of `DomainCombiner` for Subject injection have been replaced
by the direct path:

- `DomainCombiner` deprecated
- `SubjectDomainCombiner` deprecated
- `Subject.doAs()` and `Subject.doAsPrivileged()` deprecated (except Kerberos GSS path)
- ACC becomes purely a code-privilege-boundary mechanism with no Subject entanglement
- `CombinerSecurityManager` simplified — parallel permission check cache for combiner
  path removed

### 14.4 Phase 4 — Kerberos GSS-API (OpenJDK dependency)

Once OpenJDK provides a `callAs`-based GSS-API path (anticipated as part of the ongoing
Loom/structured concurrency work):

- `KerberosUtil.getGSSCredential()` migrated to `callAs` path
- `Subject.doAs()` fully deprecated with no active uses in JGDMS
- `LocalWorkerSubject` establishment migrated to a dedicated API distinct from `doAs`

---

## 15. Security Properties

### 15.1 What Each Layer Guarantees

| Property | Guarantee |
|---|---|
| Local worker is mandatory | Every permission check in the JVM includes the local worker's principals. There is no execution context in which they are absent. |
| Local worker is unforgeable | `LocalWorkerSubject` has a package-private constructor. Only `SpiffeCredentialManager` in `javax.security.auth` can construct one. SPIRE attests the identity. |
| `doPrivileged` cannot shed environmental identity | `LocalWorkerSubject` is in `privilegedContext`. Plain `doPrivileged` drops the `SubjectDomainCombiner` but `privilegedContext` remains. The local worker principals persist through every `doPrivileged` boundary. |
| Remote identity cannot escalate to environmental trust | `RemoteWorkerSubject` and `RemoteUserSubject` are injected into stack domains only. They can never appear in `privilegedContext`. A compromised remote peer cannot elevate its identity to local worker trust. |
| Trust tier is machine-attested | The local worker's SPIFFE SVID is provisioned by SPIRE based on platform attestation. Application code cannot influence it. A Windows host cannot claim an SELinux SVID. |
| Multi-party grants are atomic | All principals from all `SCOPED_SUBJECTS` entries are merged additively. A grant requiring principals from both parties is only satisfied when both are simultaneously present. Neither party can satisfy it alone. |
| Executor tasks do not accidentally inherit identity | `ScopedValue` does not propagate to executor tasks. Explicit wrapping is required. Silent propagation failures (the `doAs` executor problem) cannot occur. |
| Daemon threads are identity-free by construction | `neverPrivileged()` ACC provides a hard barrier. Even accidental construction inside a `callAs` scope cannot enrich a daemon thread with caller identity. |

### 15.2 What Cannot Be Bypassed

The following security properties are enforced at the JVM level and cannot be bypassed
by application code regardless of how it is structured:

1. Local worker identity at every permission check
2. Trust tier ceiling on `doPrivileged` grants
3. `LocalWorkerSubject` construction (package-private, SPIRE only)
4. `neverPrivileged` domain barrier on daemon threads
5. Multi-party transaction atomicity (all principals must be simultaneously present)

---

## Appendix A: Relationship to Existing Standards

| Standard | Relationship |
|---|---|
| JGDMS-STD-001 (@AtomicSerial) | Unchanged — wire format compliance is independent of identity model |
| JGDMS-STD-002 (SCAP Pipeline) | SCAP verifies code safety; this standard verifies runtime identity authority. Complementary. |
| OpenJDK `Subject.callAs()` (JEP 411) | JGDMS diverges deliberately — thread propagation extended to `new Thread(...).start()`; varargs multi-Subject extension; sealed hierarchy |

## Appendix B: Glossary

| Term | Definition |
|---|---|
| Local worker | The `LocalWorkerSubject` representing this JVM's SPIFFE identity, provisioned by SPIRE |
| Remote worker | A `RemoteWorkerSubject` representing a peer process's SPIFFE identity |
| User | A `RemoteUserSubject` or vanilla `Subject` representing an authenticated human |
| Environmental identity | Identity that reflects the execution environment (machine, OS, security posture) — the local worker |
| Caller identity | Identity that reflects who initiated a call — remote worker and user |
| Trust tier | The level of trust granted to a host based on its OS/security posture, expressed via its SPIFFE SVID path |
| `doPrivileged` boundary | A point at which caller identity is shed and only code identity + environmental identity is checked |
| `neverPrivileged` | An ACC containing a static unprivileged `ProtectionDomain` that prevents Subject injection — used for daemon threads |
