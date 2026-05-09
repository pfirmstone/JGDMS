# JGDMS-STD-003: Multi-Subject Identity Architecture

**Status:** Draft  
**Version:** 3.1  
**Applies to:** DirtyChai (JDK fork), JGDMS  
**Supersedes:** JGDMS-STD-003 v3.0  

---

## Change Log

### v3.1 changes from v3.0

- **Two `SpiffeCredentialManager` implementations documented** — the bootstrap JDK
  implementation (DirtyChai, `au.zeus.jdk.authorization.spire`) constructs a sealed
  `SpiffeSubject extends WorkerSubject` for use by `SecureClassLoader`. The JGDMS
  application implementation (`net.jini.jeri.ssl`) constructs a vanilla `Subject` with
  `X500Principal` + `SpiffePrincipal` using standard Java APIs for TLS credential
  management. `Subject.processWorker()` bridges both via `SpiffeSubjectHolder`.

- **`Thread.scopedSubjects` field is `Subject[]`** — mirrors `SCOPED_SUBJECT
  ScopedValue<Subject[]>`; the full array is captured at thread construction and
  re-established by `Thread.runWith()` via `SubjectAccess.callNoCheck(scopedSubjects,
  action)`. All transaction participants are available via `Subject.currentAll()` in
  spawned threads.

- **`AccessController.getContext()` multi-subject loop finalised** — iterates over all
  subjects in the `Subject[]` array, skips `WorkerSubject` instances, applies
  `SubjectDomainCombiner.combine()` per `UserSubject`, accumulates principals
  progressively (final iteration produces ACC with all merged), preserves original
  combiner across all iterations, enriches `privilegedContext` per subject.

- **`SubjectAccess.get()` returns `Subject[]`** — consistent with
  `SCOPED_SUBJECT ScopedValue<Subject[]>`; `NoCheck.current()` returns `Subject[]`.

- **`Subject.processWorker()`** — new standard API method returning the current
  `WorkerSubject` from `SpiffeCredentialManager`; guarded by
  `AuthPermission("getSubject")`.

- **§4 `WorkerSubject` constructor visibility** — constructor is `public` on
  `WorkerSubject` because the class is `sealed`. The only permitted subtype
  (`SpiffeSubject`) is package-private in an unexported module. Application code
  cannot instantiate `WorkerSubject` via the type system, not via constructor
  visibility.

---

## 1. Purpose and Scope

This specification defines the multi-Subject identity architecture for JGDMS and its
underlying DirtyChai JDK fork. It specifies:

- The sealed `Subject` type hierarchy and the semantics of each subtype
- The two `SpiffeCredentialManager` implementations and their respective roles
- How each Subject type is established, carried, and propagated
- How each Subject type participates in permission checks
- How `doPrivileged` boundaries interact with each Subject type
- Thread propagation rules — including `Subject[]` array propagation
- Transport endpoint Subject selection rules
- `SecureClassLoader` integration with SPIFFE identity
- Serialized ACC transmission over JERI endpoints
- The migration path toward `SubjectDomainCombiner` deprecation

---

## 2. Motivation

*(Unchanged from v3.0 §2.)*

---

## 3. The Identity Layers

### 3.1 Overview

| Layer | Type | Carrier | Survives `doPrivileged` | Lifetime | Constructed by |
|---|---|---|---|---|---|
| Process worker | `WorkerSubject` (sealed, `SpiffeSubject` only) | Baked into `ProtectionDomain` at class load time | **Yes** — in every domain | JVM lifetime | DirtyChai `SpiffeCredentialManager.SpiffeSubject` only |
| Remote process | `WorkerSubject` principals in remote PDs | Serialized ACC domains over JERI | **No** — not in `privilegedContext` | Per-connection | JERI dispatcher (receiving side) |
| User | `UserSubject` (final, public) | `SCOPED_SUBJECT ScopedValue<Subject[]>` | **Yes** — injected into `privilegedContext` | Per-request / transaction | JERI dispatcher / application |
| Local user (legacy) | `Subject` (vanilla) | `SCOPED_SUBJECT ScopedValue<Subject[]>` or ACC | **Yes** (ScopedValue path) | Per-session | JAAS `LoginContext` |

### 3.2 The `doPrivileged` Boundary Rule

*(Unchanged from v3.0.)*

### 3.3 Process Worker (`WorkerSubject`)

*(Unchanged from v3.0, with addition:)*

**Two-implementation model:**

The `WorkerSubject` identity is provisioned by two distinct `SpiffeCredentialManager`
implementations that serve different purposes:

| Implementation | Package | Subject constructed | Role |
|---|---|---|---|
| DirtyChai bootstrap | `au.zeus.jdk.authorization.spire` | `SpiffeSubject extends WorkerSubject` (sealed) | JDK bootstrap use; baked into every `ProtectionDomain` by `SecureClassLoader`; `sun.security.util.Debug`; module-private constructor |
| JGDMS application | `net.jini.jeri.ssl` | Vanilla `Subject` with `X500Principal` + `SpiffePrincipal` | TLS credential management; standard Java APIs; `AutoCloseable`; `ScheduledExecutorService` for SVID rotation |

`Subject.processWorker()` provides a standard API to retrieve the current
`WorkerSubject`, bridging both implementations via `SpiffeSubjectHolder`:

```java
public Subject processWorker() {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) sm.checkPermission(AuthPermissionHolder.GET_SUBJECT_PERMISSION);
    return SpiffeCredentialManager.getInstance().getSubject();
}
```

The JGDMS `SpiffeCredentialManager` constructs a vanilla `Subject` (not a
`WorkerSubject` subtype) because it uses standard Java APIs — `WorkerSubject` is
in the DirtyChai JDK and is not part of the standard Java platform. Policy grants
that match against `SpiffePrincipal` work correctly with both Subject types since
principal matching is by principal type and name, not by Subject subtype.

### 3.4 Remote Process Identity

*(Unchanged from v3.0.)*

### 3.5 User Subject (`UserSubject`)

*(Unchanged from v3.0, with addition:)*

Multiple `UserSubject` instances are carried as a `Subject[]` in
`SCOPED_SUBJECT ScopedValue<Subject[]>`. All entries are available via
`Subject.currentAll()`. `Subject.current()` returns the first entry in the array
(the primary user). When threads are spawned inside a `callAs` scope, the full
`Subject[]` is captured in `Thread.scopedSubjects` and re-established in
`Thread.runWith()` — all transaction participants are visible in spawned threads.

---

## 4. The Sealed Subject Hierarchy

### 4.1 Class Definitions

```java
public sealed class Subject implements Serializable
    permits WorkerSubject, UserSubject {
    // SCOPED_SUBJECT is ScopedValue<Subject[]>
}

/**
 * Sealed: only SpiffeCredentialManager.SpiffeSubject (module-private) is permitted.
 * Constructor is public because sealed enforcement is by the type system, not
 * constructor visibility. Application code cannot instantiate WorkerSubject
 * directly — SpiffeSubject is the only concrete subtype and is inaccessible.
 */
public sealed class WorkerSubject extends Subject
    permits SpiffeCredentialManager.SpiffeSubject {
    public WorkerSubject(boolean readOnly,
                         Set<? extends Principal> principals,
                         Set<?> pubCredentials,
                         Set<?> privCredentials) {
        super(readOnly, principals, pubCredentials, privCredentials);
    }
}

/**
 * Sole concrete WorkerSubject. Package-private in au.zeus.jdk.authorization.spire
 * (unexported module) — cannot be instantiated by application code.
 * Constructed only by SpiffeCredentialManager for JDK bootstrap use.
 */
public static final class SpiffeSubject extends WorkerSubject {
    SpiffeSubject(...) { /* package-private constructor */ }
}

/**
 * Final class. Represents an authenticated user in a distributed transaction.
 * May be local or remote. Multiple instances simultaneously supported.
 */
public final class UserSubject extends Subject {
    public UserSubject(boolean readOnly,
                       Set<? extends Principal> principals,
                       Set<?> pubCredentials,
                       Set<?> privCredentials) {
        super(readOnly, principals, pubCredentials, privCredentials);
    }
}
```

### 4.2 Construction Constraints

| Type | Constructor | Who can construct | Mechanism |
|---|---|---|---|
| `Subject` | `public` | Any code | Backwards compatibility |
| `WorkerSubject` | `public` | Only `SpiffeSubject` | Sealed — no other subtype permitted |
| `SpiffeSubject` | Package-private | Only `SpiffeCredentialManager` | Module unexported — inaccessible to application code |
| `UserSubject` | `public` | Any code | JERI dispatcher, application transaction code |

### 4.3 No Further Subclassing

`UserSubject` is `final`. `WorkerSubject` is `sealed` permitting only `SpiffeSubject`.

---

## 5. `Subject::callAs` — Both Overloads

*(Unchanged from v3.0 §5.1–5.4, with clarification:)*

### 5.1 OpenJDK-Compatible Overload

Binds the single subject as a `Subject[1]` array to `SCOPED_SUBJECT`:
```java
public static <T> T callAs(final Subject subject, final Callable<T> action)
    throws CompletionException {
    Objects.requireNonNull(action);
    if (subject instanceof WorkerSubject)
        throw new IllegalArgumentException(
            "WorkerSubject must not be passed to callAs()");
    // ... security check ...
    return callNoCheck(action, subject); // wraps as Subject[]{subject}
}
```

### 5.2 Multi-Subject Varargs Overload

```java
public static <T> T callAs(Callable<T> action, UserSubject... subject)
    throws CompletionException {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(subject, "subjects");
    for (int i = 0; i < subject.length; i++)
        Objects.requireNonNull(subject[i], "subjects[" + i + "] must not be null");
    // ... security check ...
    return callNoCheck(action, subject);
}
```

### 5.3 Internal `callNoCheck`

```java
private static <T> T callNoCheck(final Callable<T> action,
        final Subject... subject) throws CompletionException {
    try {
        return ScopedValue.where(SCOPED_SUBJECT, subject).call(action::call);
    } catch (Exception e) {
        throw new CompletionException(e);
    }
}
```

### 5.4 `NoCheck.current()` Returns `Subject[]`

```java
protected static Subject[] current() {
    return SCOPED_SUBJECT.isBound() ? SCOPED_SUBJECT.get() : new Subject[0];
}
```

`Subject.current()` returns `current()[0]` (or `null` if empty).
`Subject.currentAll()` returns `current().clone()`.

---

## 6. `Subject::doAs` Routing

*(Unchanged from v3.0.)*

---

## 7. Subject Retrieval API

### 7.1 `Subject.current()` — primary user, backwards-compatible
### 7.2 `Subject.currentAll()` — full `Subject[]` array
### 7.3 `Subject.processWorker()` — current `WorkerSubject`

```java
public Subject processWorker() {
    // AuthPermission("getSubject") check
    return SpiffeCredentialManager.getInstance().getSubject();
}
```

Note: Returns a vanilla `Subject` from JGDMS's `SpiffeCredentialManager`, or a
`WorkerSubject` from DirtyChai's bootstrap implementation, depending on which is active.
For infrastructure code that specifically needs the `WorkerSubject` type, use
`Subject.getSubject(AccessController.getContext())` which retrieves from the ACC combiner.

---

## 8. `AccessController.getContext()` — Multi-Subject Injection Loop

### 8.1 Implementation

```java
Subject[] subject = SubjectAccess.SCOPED.get(); // Subject[] via NoCheck trust chain
if (subject != null) {
    DomainCombiner existing = acc.getCombiner(); // captured once — never displaced
    for (int i = 0, l = subject.length; i < l; i++) {
        if (subject[i] instanceof WorkerSubject) continue; // already in domains
        SubjectDomainCombiner sdc = new SubjectDomainCombiner(subject[i]);
        ProtectionDomain[] combined = sdc.combine(acc.getContext(), acc.getContext());

        AccessControlContext privileged = acc.privilegedContext();
        if (privileged != null) {
            ProtectionDomain[] combinedPrivileged = sdc.combine(
                privileged.getContext(), privileged.getContext());
            privileged = AccessControlContext.create(
                combinedPrivileged, privileged.privilegedContext(),
                privileged.getCombiner(), privileged.isPrivileged());
        }

        // existing combiner restored every iteration.
        // acc.isPrivileged() preserved every iteration — erasing is a security error.
        // Last iteration produces ACC with ALL subjects' principals merged.
        acc = AccessControlContext.create(combined, privileged, existing, acc.isPrivileged());
    }
}
return acc;
```

### 8.2 Injection Rules by Type

| Subject type | Stack domains | `privilegedContext` | Rationale |
|---|---|---|---|
| `WorkerSubject` | Skipped (already in every domain from class load) | Skipped | Environmental — baked at load time |
| `UserSubject` | Injected per entry | **Injected per entry** | Operational authority — survives `doPrivileged` |
| Vanilla `Subject` | Injected | **Injected** | Legacy user — same semantics |

### 8.3 Multi-Subject Accumulation

Each loop iteration operates on the ACC produced by the previous iteration, so
principals accumulate. For a two-party transaction `[alice, bob]`:
- After iteration 0: ACC contains Alice's principals
- After iteration 1: ACC contains Alice's + Bob's principals combined

The `existing` combiner is preserved in every iteration — it is never displaced by
any of the injections.

### 8.4 Bootstrap Safety

`SubjectAccess.SCOPED.get()` is only called when `VM.isBooted()`.

---

## 9. `SecureClassLoader` — SPIFFE Identity at Class Load Time

*(Unchanged from v3.0. The `WorkerSubject` principals baked in come from
`SpiffeCredentialManager.getInstance().getSubject()` which may return either
a `WorkerSubject` subtype (DirtyChai) or a vanilla `Subject` (JGDMS). Both
carry `SpiffePrincipal` and are used identically by `SecureClassLoader`.)*

---

## 10. Serialized ACC Transmission over JERI

*(Unchanged from v3.0.)*

---

## 11. Thread Propagation

### 11.1 Spawned Threads — Full `Subject[]` Propagation

When a thread is constructed inside a `callAs` scope:

1. `AccessController.getContext()` produces an ACC with all `UserSubject` principals
   baked into the domain array (including `privilegedContext`).
2. This enriched ACC is stored as `inheritedAccessControlContext`.
3. The full `Subject[]` is captured into `Thread.scopedSubjects` at construction time
   via `SubjectAccess.SCOPED.get()` (guarded by `VM.isBooted()`).
4. `Thread.runWith()` (`final` — platform and virtual threads) re-establishes
   `SCOPED_SUBJECT` via `SubjectAccess.callNoCheck(scopedSubjects, action)`,
   passing the full array — all transaction participants are visible via
   `Subject.currentAll()` in the spawned thread.

### 11.2 Executor-Submitted Tasks

Tasks submitted to an `Executor` do not automatically inherit scoped identity.
The explicit wrapper must capture and pass the full array:

```java
Subject[] captured = Subject.currentAll();
AccessControlContext acc = AccessController.getContext();
Runnable wrapped = () -> AccessController.doPrivileged(
    (PrivilegedAction<Void>) () -> {
        Subject.callAs(() -> { task.run(); return null; }, (UserSubject[]) captured);
        return null;
    }, acc);
```

### 11.3 Daemon Thread Discipline

*(Unchanged from v3.0.)*

---

## 12. Transport Endpoint Subject Selection

*(Unchanged from v3.0.)*

---

## 13. Policy Grant Examples

*(Unchanged from v3.0.)*

---

## 14. Migration Path

*(Unchanged from v3.0 §14, with addition:)*

### 14.0 Two-Implementation Coexistence (current state)

The DirtyChai `SpiffeCredentialManager` (bootstrap, `WorkerSubject`) and the JGDMS
`SpiffeCredentialManager` (TLS transport, vanilla `Subject`) coexist without conflict:

- DirtyChai's implementation runs earlier (bootstrap); its `WorkerSubject` is baked
  into `ProtectionDomain`s by `SecureClassLoader` before JGDMS code loads.
- JGDMS's implementation manages TLS credentials for JERI transport using standard
  Java APIs, independent of the sealed hierarchy.
- `SpiffeSubjectHolder` provides the shared reference point between them.
- When JGDMS eventually runs on a non-DirtyChai JDK, only the JGDMS implementation
  is active; the `WorkerSubject` type is absent but `SpiffePrincipal` matching in
  policy grants continues to work correctly via the vanilla `Subject`.

---

## 15. Security Properties

*(Unchanged from v3.0 §15, with addition:)*

| Property | Guarantee |
|---|---|
| Two-implementation coexistence is safe | DirtyChai `SpiffeSubject` and JGDMS vanilla `Subject` both carry `SpiffePrincipal`; policy grants match by principal type/name; Subject subtype does not affect grant matching |
| Full `Subject[]` propagated to spawned threads | `Thread.scopedSubjects` captures the full array; `Subject.currentAll()` returns all transaction participants in spawned threads; no partial propagation |
| `WorkerSubject` skipped in injection loop | Explicit `instanceof WorkerSubject` check before each `combine()` call; principals already present from class load time; no double-injection |
| Original combiner preserved across multi-subject loop | `existing = acc.getCombiner()` captured once before loop; restored in every `AccessControlContext.create()` call within the loop |

---

## Appendix A: Relationship to Existing Standards

*(Unchanged from v3.0.)*

## Appendix B: Glossary

*(Unchanged from v3.0, with additions:)*

| Term | Definition |
|---|---|
| DirtyChai `SpiffeCredentialManager` | Bootstrap JDK implementation in `au.zeus.jdk.authorization.spire`; constructs sealed `SpiffeSubject extends WorkerSubject`; used by `SecureClassLoader` |
| JGDMS `SpiffeCredentialManager` | Application implementation in `net.jini.jeri.ssl`; constructs vanilla `Subject` with `X500Principal` + `SpiffePrincipal`; manages TLS credentials; `AutoCloseable` |
| `SpiffeSubjectHolder` | Static bridge between the two `SpiffeCredentialManager` implementations; `Subject.processWorker()` delegates here |
| `SCOPED_SUBJECT` | `ScopedValue<Subject[]>` carrying all active `UserSubject` instances for the current thread; empty array when no `callAs` scope is active |
| `Subject.processWorker()` | Standard API method returning the current `WorkerSubject`; guarded by `AuthPermission("getSubject")`; bridges both implementations |
