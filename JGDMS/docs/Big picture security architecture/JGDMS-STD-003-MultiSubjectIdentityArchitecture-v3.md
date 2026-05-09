# JGDMS-STD-003: Multi-Subject Identity Architecture

**Status:** Draft  
**Version:** 3.0  
**Applies to:** DirtyChai (JDK fork), JGDMS  
**Supersedes:** JGDMS-STD-003 v2.0  

---

## Change Log

### v3.0 changes from v2.0

- **Naming finalised:** `WorkerSubject` (process/platform identity, sealed, SPIRE-only)
  and `UserSubject` (authenticated user identity, final, public). All stale references
  to `LocalWorkerSubject`, `RemoteWorkerSubject`, `DistributedUserSubject` removed.

- **`callAs(Callable, UserSubject...)` uses `UserSubject` varargs type** — the type
  system enforces correctness directly; no `IllegalArgumentException` for
  `WorkerSubject` is needed since `WorkerSubject` cannot be passed to the varargs
  overload at compile time. The existing `callAs(Subject, Callable)` retains a runtime
  guard against `WorkerSubject` for completeness.

- **`WorkerSubject` has public constructor** — `WorkerSubject` is `sealed` and only
  `SpiffeCredentialManager.SpiffeSubject` is a permitted subtype, so the constructor
  being `public` does not weaken the construction constraint. Application code cannot
  instantiate `WorkerSubject` directly; it can only instantiate `SpiffeSubject` if it
  has access to `au.zeus.jdk.authorization.spire` (which is not exported).

- **`DomainCombiner` retained as Java API compatibility layer** — `DomainCombiner`
  is retained as the extension point for the ACC serializer's domain verification
  logic over JERI endpoints. `SubjectDomainCombiner` is deprecated — signalling the
  direction of travel — but `DomainCombiner` itself remains. `CombinerSecurityManager`
  refactoring is deferred.

- **`Subject.current()` implementation clarified** — returns `subject[0]` from the
  `SCOPED_SUBJECT` array (the primary user). The `NoCheck.current()` delegation
  pattern is documented.

- **§4 class definitions corrected** — removed duplicate `WorkerSubject` definition
  that appeared in place of the remote worker description. The remote worker concept
  is now correctly described as `WorkerSubject` travelling via serialized ACC domains,
  not as a separate class.

- **§6 `doAs` routing simplified** — only vanilla `Subject` and `null` are accepted;
  `UserSubject` uses `callAs`; `WorkerSubject` is rejected with `IllegalArgumentException`.

- **§7.3 `getLocalWorker()`** — noted as potentially replaceable by direct access to
  `SpiffeCredentialManager.getInstance().getSubject()` from trusted infrastructure code,
  avoiding the ACC combiner lookup path entirely.

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
- `SecureClassLoader` integration with SPIFFE identity
- Serialized ACC transmission over JERI endpoints
- The migration path toward `SubjectDomainCombiner` deprecation

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
identities of the local process and the human user simultaneously.

**`doPrivileged` drops Subject identity.** A plain `doPrivileged` call drops the
`SubjectDomainCombiner` from the ACC. Policy grants conditioned on a SPIFFE workload
principal are not enforced inside `doPrivileged` blocks — a significant security gap
when the goal is mandatory enforcement of execution environment identity (e.g. requiring
SELinux) or user authorisation identity.

**`doPrivileged` can accidentally bypass user grants.** If user identity is shed at
`doPrivileged` boundaries, buggy privileged code can inadvertently satisfy grants that
should require an admin or root principal. The correct model requires user identity to
persist through `doPrivileged` — it is an authorisation concern, not a caller-stack
concern.

**No trust tier for execution environment.** The standard model has no mechanism to
distinguish a process running on a hardened SELinux host from one running on a home
device.

**No multi-party transaction support.** A single Subject cannot represent multiple
simultaneously-required user identities.

**No type-level distinction between identity classes.** Without type-level distinctions,
routing logic must rely on principal inspection rather than type dispatch.

**Remote process identity contaminates local grants.** Injecting remote process
principals into local `ProtectionDomain`s would cause grants to require those remote
principals permanently, breaking policy stability as remote workers change.

### 2.2 Design Goals

1. **Mandatory environmental enforcement.** The local process identity must be enforced
   at every permission check including inside `doPrivileged` blocks.

2. **Mandatory user enforcement.** User identity must survive `doPrivileged` boundaries.
   A privileged library performing an operation on behalf of a user must still require
   the user principal.

3. **Correct `doPrivileged` semantics.** `doPrivileged` asserts code trustworthiness.
   It does not shed environmental or operational identity.

4. **Remote process identity travels as call stack domains.** Remote `ProtectionDomain`s
   with `WorkerSubject` principals baked in at class load time on the remote JVM are
   transmitted via serialized ACC and participate in local permission checks as stack
   domains. This avoids contaminating local domain construction.

5. **Class loading gated by SPIFFE identity.** `SecureClassLoader` bakes `WorkerSubject`
   principals into every `ProtectionDomain` at class load time, enabling
   `LoadClassPermission` checks that restrict which classes may load into a given process.

6. **Multi-party transaction support.** Multiple `UserSubject` instances must be
   expressible simultaneously, with policy grants conditioned on all being present.

7. **Type safety.** Each identity class is a distinct sealed type. `WorkerSubject`
   cannot be passed to `callAs()` — the type system enforces this.

8. **Backwards compatibility.** Existing code using vanilla `Subject`, `Subject.doAs()`,
   and `Subject.current()` continues to work unchanged.

9. **`DomainCombiner` retained as API compatibility layer.** `SubjectDomainCombiner`
   is deprecated. `DomainCombiner` is retained for the ACC serializer and external
   implementations.

---

## 3. The Identity Layers

### 3.1 Overview

| Layer | Type | Carrier | Survives `doPrivileged` | Lifetime | Constructed by |
|---|---|---|---|---|---|
| Process worker | `WorkerSubject` (sealed, `SpiffeSubject` only) | Baked into `ProtectionDomain` at class load time | **Yes** — in every domain | JVM lifetime | SPIRE only (`SpiffeSubject`) |
| Remote process | `WorkerSubject` principals in remote PDs | Serialized ACC domains over JERI | **No** — not in `privilegedContext` | Per-connection | JERI dispatcher |
| User | `UserSubject` (final, public) | `SCOPED_SUBJECT` ScopedValue | **Yes** — injected into `privilegedContext` | Per-request / transaction | JERI dispatcher / application |
| Local user (legacy) | `Subject` (vanilla) | `SCOPED_SUBJECT` ScopedValue or ACC | **Yes** (ScopedValue path) | Per-session | JAAS `LoginContext` |

### 3.2 The `doPrivileged` Boundary Rule

```
Survives doPrivileged:  who you are (WorkerSubject + UserSubject)
Shed at doPrivileged:   where you came from (remote WorkerSubject domains)
```

`doPrivileged` asserts: *"this code takes responsibility — check only my
`ProtectionDomain`."* This is a code trustworthiness assertion, not an authorisation
bypass. The `WorkerSubject` answers "what machine is this running on" — a `doPrivileged`
block on Windows cannot claim SELinux trust. The `UserSubject` answers "who authorised
this operation" — `doPrivileged` cannot shed that authorisation without creating a
bypass vulnerability. Only remote ACC domains — the network origin of the call — are
correctly shed at `doPrivileged` boundaries.

### 3.3 Process Worker (`WorkerSubject`)

Represents the execution environment of the JVM — the machine, OS, and security
posture. Provisioned by SPIRE. Rotated automatically on SVID expiry (rotation updates
credentials, not identity).

**Key properties:**
- Baked into every `ProtectionDomain` by `SecureClassLoader` at class load time
- Always present at every permission check — in the domain itself, not the combiner
- Used for outbound TLS connections
- Never passed to `Subject.callAs()` — it is ambient, not caller-supplied
- Sealed: only `SpiffeCredentialManager.SpiffeSubject` is a permitted subtype
- If `SpiffeCredentialManager.getSubject()` returns `null` at class load time,
  the `ProtectionDomain` has no principals — `LoadClassPermission` check fails,
  class is rejected (fail-secure)

**Trust tiers expressed via SPIFFE SVID:**
```
spiffe://jgdms.example.org/host/selinux/svc-name    — hardened SELinux host
spiffe://jgdms.example.org/host/openbsd/svc-name    — hardened OpenBSD host
spiffe://jgdms.example.org/host/windows/svc-name    — Windows host (lower trust)
spiffe://jgdms.example.org/host/home/svc-name       — home device (lowest trust)
```

**`SecureClassLoader` integration:**
```java
// In SecureClassLoader.getProtectionDomain(CodeSource):
Subject sub = SpiffeCredentialManager.getInstance().getSubject();
Principal[] pals = null;
if (sub != null && sub.isReadOnly()) {
    Set<Principal> prin = sub.getPrincipals();
    pals = prin.toArray(new Principal[prin.size()]);
}
ProtectionDomain pd = new ProtectionDomain(key.cs, perms, this, pals);
// LoadClassPermission check gates whether the class may load at all
if (sm != null) {
    sm.checkPermission(LOAD_CLASS_ALLOW,
        AccessControlContext.create(new ProtectionDomain[]{pd}, false));
}
```

### 3.4 Remote Process Identity

Remote process identity is not a separate `Subject` subtype. It is represented by
`WorkerSubject` principals baked into `ProtectionDomain`s at class load time on the
remote JVM. These domains travel via a serialized `AccessControlContext` over JERI
endpoints and participate in `RemotePolicy` checks on the receiving JVM as call stack
domains.

**Key properties:**
- Travels as `ProtectionDomain`s in a serialized ACC — not via `SCOPED_SUBJECT`
- Shed at `doPrivileged` boundaries on the receiving JVM (not in `privilegedContext`)
- `httpmd:` URLs carry SHA-256 signatures — code identity verified without loading
- A `DomainCombiner` on the receiving side strips unverifiable domains before the ACC
  is placed on the call stack

**`RemotePolicy` grant example:**
```
grant codeBase "httpmd://repo.example.org/client-stub.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../svc/trusted-client" {
    permission OrderPermission "submit";
};
```

### 3.5 User Subject (`UserSubject`)

Represents an authenticated user identity that travels with a distributed transaction.
May be a local administrator (SSH session), a remote user, or one of several parties
in a multi-party transaction.

**Key properties:**
- Carried as a `ScopedValue` (`SCOPED_SUBJECT` — `ScopedValue<Subject[]>`)
- Injected into both stack domains AND `privilegedContext` — **survives `doPrivileged`**
- Read-only, principals only — no credentials
- May be absent (service-to-service calls with no user context)
- Multiple instances may be present simultaneously (transaction context)
- `Subject.current()` returns `subject[0]` from the scoped array (primary user)
- `Subject.callAs(Callable, UserSubject...)` varargs overload supports multiple instances

**Why it survives `doPrivileged`:**
If a grant requires `admin@EXAMPLE.ORG`, no amount of `doPrivileged` nesting can
satisfy it without that principal being present. Buggy privileged code cannot
accidentally bypass a grant requiring admin identity.

### 3.6 Local User Subject (Legacy)

The vanilla `Subject` class represents a locally-authenticated user via JAAS
`LoginContext` (Kerberos, etc.). Retains existing semantics for backwards compatibility.

**Key properties:**
- `Subject.doAs()` routes to this type — unchanged legacy semantics
- `Subject.current()` returns `subject[0]` which may be a vanilla `Subject`
- Used by `KerberosEndpoint` for GSS credential acquisition
- Survives `doPrivileged` when carried on `SCOPED_SUBJECT`

---

## 4. The Sealed Subject Hierarchy

### 4.1 Class Definitions

```java
/**
 * Base class. Vanilla Subject represents a locally-authenticated user
 * (Kerberos, JAAS). Sealed to permit only the defined identity subtypes.
 */
public sealed class Subject implements Serializable
    permits WorkerSubject, UserSubject {
    // existing Subject implementation
    // SCOPED_SUBJECT is ScopedValue<Subject[]>
}

/**
 * Represents the local JVM process identity, provisioned by SPIRE.
 * Baked into every ProtectionDomain at class load time by SecureClassLoader.
 * Survives doPrivileged — present in every domain. Never passed to callAs().
 * Sealed: only SpiffeCredentialManager.SpiffeSubject is permitted.
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
 * SpiffeSubject — the sole concrete WorkerSubject.
 * Package-private in au.zeus.jdk.authorization.spire — cannot be
 * instantiated by application code.
 */
public static final class SpiffeSubject extends WorkerSubject {
    // package-private constructor — only SpiffeCredentialManager constructs this
}

/**
 * Represents an authenticated user identity in a distributed transaction.
 * May be local (SSH admin) or remote. Multiple instances may be present
 * simultaneously. Carried as a ScopedValue. Survives doPrivileged boundaries.
 * Read-only, no credentials.
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

| Type | Constructor access | Who can construct | Rationale |
|---|---|---|---|
| `Subject` | Public | Any code | Backwards compatibility; JAAS `LoginContext` |
| `WorkerSubject` | Public | Only `SpiffeSubject` (sealed) | `WorkerSubject` is abstract in effect — no direct instantiation is possible since it is sealed and `SpiffeSubject` is the only permit |
| `SpiffeCredentialManager.SpiffeSubject` | Package-private (`au.zeus.jdk.authorization.spire`) | Only `SpiffeCredentialManager` | Module not exported — SPIRE infrastructure only |
| `UserSubject` | Public | Any code | JERI dispatcher, application transaction code |

**Note on `WorkerSubject` constructor visibility:** The constructor is `public` for
JDK internal use but the class is `sealed` — only `SpiffeSubject` can extend it, and
`SpiffeSubject` is not accessible outside its module. Application code cannot
instantiate `WorkerSubject` directly.

### 4.3 No Further Subclassing

`UserSubject` is `final`. `WorkerSubject` is `sealed` with only `SpiffeSubject`
permitted. No further subclassing is intended unless a concrete need is identified,
at which point the sealed hierarchy will be extended explicitly.

---

## 5. `Subject::callAs` — Both Overloads

### 5.1 OpenJDK-Compatible Overload (unchanged)

```java
/**
 * Executes a Callable with subject as the current scoped identity.
 * OpenJDK-compatible signature.
 *
 * @param subject the Subject to bind; WorkerSubject is rejected
 * @param action  the code to execute; must not be null
 */
@SuppressWarnings("removal")
public static <T> T callAs(final Subject subject,
        final Callable<T> action) throws CompletionException {
    Objects.requireNonNull(action);
    if (subject instanceof WorkerSubject)
        throw new IllegalArgumentException(
            "WorkerSubject must not be passed to callAs() — " +
            "process identity is ambient");
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) sm.checkPermission(AuthPermissionHolder.CALL_AS_PERMISSION);
    return callNoCheck(action, subject);
}
```

### 5.2 Multi-Subject Varargs Overload (JGDMS extension)

The varargs type is `UserSubject` — the type system prevents `WorkerSubject` from
being passed at compile time. No runtime `instanceof` check is needed.

```java
/**
 * Executes a Callable with the provided UserSubjects as the current scoped
 * identity for the duration of the call on the current thread.
 *
 * <p> Supports multi-party transactions — multiple UserSubjects may be bound
 * simultaneously. Subject.current() returns subject[0] (the primary user).
 *
 * <p> WorkerSubject cannot be passed — the type parameter enforces this at
 * compile time. WorkerSubject process identity is ambient, baked into
 * ProtectionDomains at class load time by SecureClassLoader.
 *
 * @param action  the code to execute; must not be null
 * @param subject zero or more UserSubject instances; must not be null;
 *                no element may be null
 *
 * @throws NullPointerException if action is null, subject is null,
 *         or any element of subject is null
 */
@SuppressWarnings("removal")
public static <T> T callAs(Callable<T> action, UserSubject... subject)
    throws CompletionException {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(subject, "subjects");
    for (int i = 0; i < subject.length; i++) {
        Objects.requireNonNull(subject[i], "subjects[" + i + "] must not be null");
    }
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) sm.checkPermission(AuthPermissionHolder.CALL_AS_PERMISSION);
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

### 5.4 Common Call Patterns

```java
// Service-to-service — no user context (zero subjects)
Subject.callAs(action);

// Single user — OpenJDK-compatible overload
Subject.callAs(userSubject, action);

// Single UserSubject — new overload
Subject.callAs(action, userSubject);

// Two-party transaction
Subject.callAs(action, aliceSubject, bobSubject);

// Multi-party transaction
Subject.callAs(action, partyA, partyB, partyC);
```

### 5.5 `Subject.current()` Semantics

Returns `subject[0]` from the `SCOPED_SUBJECT` array — the primary user Subject.
Backwards-compatible. Delegates to `NoCheck.current()` which reads `SCOPED_SUBJECT`
without an `AuthPermission` check (for JDK-internal callers).

```java
public static Subject current() {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) sm.checkPermission(AuthPermissionHolder.GET_SUBJECT_PERMISSION);
    return NoCheck.current(); // reads SCOPED_SUBJECT[0]
}
```

### 5.6 `NoCheck` Trust Chain

```java
public static abstract sealed class NoCheck
        permits AccessController.SubjectAccess, Thread.SubjectAccess {

    protected static Subject current() {
        Subject[] subject = SCOPED_SUBJECT.isBound() ? SCOPED_SUBJECT.get() : null;
        return subject != null && subject.length > 0 ? subject[0] : null;
    }

    protected static Subject getSubject(SubjectDomainCombiner sdc) {
        return sdc.subject();
    }

    protected static <T> T callAs(final Subject subject,
            final Callable<T> action) throws CompletionException {
        return callNoCheck(action, subject);
    }
}
```

`NoCheck` is `sealed` permitting only `AccessController.SubjectAccess` and
`Thread.SubjectAccess` — both in trusted JDK packages. The sealed constraint is
enforced by the compiler.

---

## 6. `Subject::doAs` Routing

### 6.1 Type Constraints

`doAs` accepts only vanilla `Subject` and `null`. `UserSubject` must use `callAs`.
`WorkerSubject` is rejected — it is established by SPIRE infrastructure only.

```java
public static <T> T doAs(Subject subject, PrivilegedAction<T> action) {
    if (subject instanceof WorkerSubject)
        throw new IllegalArgumentException(
            "WorkerSubject is established by SPIRE infrastructure");
    if (subject instanceof UserSubject)
        throw new IllegalArgumentException(
            "UserSubject must use Subject.callAs()");
    // existing doAs implementation — vanilla Subject and null only
}
```

### 6.2 Retained Uses

`Subject.doAs()` is retained for:
- **Kerberos GSS-API** — JDK GSS-API reads `Subject` from ACC internally;
  this is a JDK constraint, not a JGDMS one
- **Legacy JAAS** — `AbstractJiniService` `LoginContext` subjects

---

## 7. Subject Retrieval API

### 7.1 `Subject.current()`

Returns `SCOPED_SUBJECT[0]` — the primary user Subject — or `null`. Backwards-
compatible. Performs `AuthPermission("getSubject")` check.

### 7.2 `Subject.currentAll()`

Returns a defensive copy of the full `SCOPED_SUBJECT` array, or empty array.
Never includes `WorkerSubject`.

```java
public static Subject[] currentAll() {
    if (!SCOPED_SUBJECT.isBound()) return new Subject[0];
    Subject[] all = SCOPED_SUBJECT.get();
    return all.clone();
}
```

### 7.3 `Subject.getWorker()`

Retrieves the `WorkerSubject` from the ACC via `Subject.getSubject(acc)`, or
directly from `SpiffeCredentialManager` for trusted infrastructure code.

```java
// Standard API path — available to any code with AuthPermission("getSubject")
public static WorkerSubject getWorker() {
    Subject s = Subject.getSubject(AccessController.getContext());
    return s instanceof WorkerSubject ws ? ws : null;
}
```

For trusted infrastructure code (e.g. `SslEndpointImpl`) that has direct access
to `SpiffeCredentialManager`, it is preferable to call
`SpiffeCredentialManager.getInstance().getSubject()` directly, avoiding the ACC
combiner lookup path. The ACC path is provided for general API use.

---

## 8. `AccessController.getContext()` — Injection Rules

### 8.1 Overview

After `optimize()`, `getContext()` reads `SCOPED_SUBJECT` and injects `UserSubject`
(and vanilla `Subject`) principals into the ACC's `ProtectionDomain` array.
`WorkerSubject` is never in `SCOPED_SUBJECT` — its principals are already in every
`ProtectionDomain` from class load time.

```java
Subject[] scoped = SCOPED_SUBJECT.isBound() ? SCOPED_SUBJECT.get() : null;
if (scoped != null && scoped.length > 0) {
    Set<Principal> userPrincipals = new LinkedHashSet<>();
    for (int i = 0; i < scoped.length; i++) {
        // WorkerSubject never appears here — assertion only
        assert !(scoped[i] instanceof WorkerSubject);
        userPrincipals.addAll(scoped[i].getPrincipals());
    }

    if (!userPrincipals.isEmpty()) {
        DomainCombiner existing = acc.getCombiner();
        SubjectDomainCombiner sdc = new SubjectDomainCombiner(
            subjectFromPrincipals(userPrincipals));

        // Bake into stack domains
        ProtectionDomain[] combined = sdc.combine(
            acc.getContext(), acc.getContext());

        // Bake into immediate privilegedContext — user survives doPrivileged
        AccessControlContext privileged = acc.privilegedContext;
        if (privileged != null) {
            ProtectionDomain[] combinedPrivileged = sdc.combine(
                privileged.getContext(), privileged.getContext());
            privileged = AccessControlContext.create(
                combinedPrivileged,
                privileged.privilegedContext,   // preserve nested chain
                privileged.getCombiner(),        // preserve existing combiner
                privileged.isPrivileged());
        }

        // Restore original combiner; preserve all ACC structural properties.
        // acc.isPrivileged() MUST be preserved — setting false would erase
        // the doPrivileged boundary, which is a security error.
        acc = AccessControlContext.create(
            combined, privileged, existing, acc.isPrivileged());
    }
}
return acc;
```

### 8.2 Injection Rules by Type

| Subject type | Stack domains | `privilegedContext` | Rationale |
|---|---|---|---|
| `WorkerSubject` | Already in every domain from class load | Already in every domain | Environmental — baked at load time |
| `UserSubject` | Injected | **Injected** | Operational authority — survives `doPrivileged` |
| Vanilla `Subject` | Injected | **Injected** | Legacy user — same semantics as `UserSubject` |

### 8.3 Bootstrap Safety

`SCOPED_SUBJECT.isBound()` is only called when `VM.isBooted()`. During bootstrap,
the injection step is skipped — no `callAs` scope can be active during bootstrap.

---

## 9. `SecureClassLoader` — SPIFFE Identity at Class Load Time

`SecureClassLoader.getProtectionDomain()` calls
`SpiffeCredentialManager.getInstance().getSubject()` and bakes the `WorkerSubject`
principals into every constructed `ProtectionDomain`. This is implemented in
`SecureClassLoader.java` (DirtyChai fork).

**Fail-secure behaviour:** If `getSubject()` returns `null` (SPIRE not yet connected),
`pals` is `null` and the `ProtectionDomain` has no principals. The
`LoadClassPermission` check fails for any grant requiring a SPIFFE principal,
preventing class loading until SPIRE provides credentials.

**`LoadClassPermission` as a class load gate:**
```
// Only bytecode-svc on SELinux may load ASM
grant codeBase "httpmd://repo.example.org/asm.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../host/selinux/bytecode-svc" {
    permission LoadClassPermission;
};
```

---

## 10. Serialized ACC Transmission over JERI

### 10.1 Overview

Remote process identity travels as `ProtectionDomain`s in a serialized
`AccessControlContext` transmitted over JERI endpoints. The `DomainCombiner` is
retained as an API compatibility layer and is used by the ACC serializer's domain
verification logic on the receiving JVM.

### 10.2 Remote Domain Construction

On the remote JVM, `SecureClassLoader` bakes `WorkerSubject` principals into each
`ProtectionDomain` at class load time:

```
ProtectionDomain(
    codeSource = httpmd://repo.example.org/client-stub.jar#SHA256:abc123,
    principals = [SpiffePrincipal("spiffe://.../host/selinux/client-svc")]
)
```

### 10.3 `@AtomicSerial` ACC Serialization

`AccessControlContext` is serialized using `@AtomicSerial`. The serialized form
contains `ProtectionDomain[]` with codebase URIs and principals. The `DomainCombiner`
is not serialized — it is local infrastructure on the receiving JVM.

### 10.4 Receiving Side Verification

A `DomainCombiner` on the receiving JVM strips any domain whose `httpmd:` SHA-256
does not verify, or whose `SpiffePrincipal` is outside the trusted SPIFFE trust domain.
The verified domains participate in `RemotePolicy` checks directly.

### 10.5 `DomainCombiner` Retention Rationale

`DomainCombiner` is retained as a Java API compatibility layer:
- The ACC serializer uses it for receiving-side domain verification
- External implementations may depend on it
- `SubjectDomainCombiner` is deprecated — signalling that `Subject`-carrying via
  combiner is superseded by the `WorkerSubject`/`UserSubject` model
- `CombinerSecurityManager` refactoring is deferred — the current architecture is
  already a substantial improvement and `CombinerSecurityManager` is not on the
  critical path

---

## 11. Thread Propagation

### 11.1 Spawned Threads

When a thread is constructed inside a `callAs` scope:

1. `AccessController.getContext()` produces an ACC with `UserSubject` principals
   baked into the domain array (including `privilegedContext`).
2. This enriched ACC is stored as `inheritedAccessControlContext`.
3. `Subject[]` is captured into `Thread.scopedSubjects` at construction time
   (guarded by `VM.isBooted()`).
4. `Thread.runWith()` (`final` — applies to both platform and virtual threads)
   re-establishes `SCOPED_SUBJECT` via `Thread.SubjectAccess.callNoCheck()` before
   invoking the task, ensuring `Subject.current()` works correctly.

**OpenJDK divergence:** In OpenJDK, `Subject.callAs` identity does not propagate to
threads started outside `StructuredTaskScope`. In JGDMS, propagation occurs for all
spawned threads. This is intentional.

### 11.2 Executor-Submitted Tasks

Tasks submitted to an `Executor` do **not** automatically inherit scoped identity.
Use the explicit wrapper:

```java
public static Runnable wrap(Runnable task) {
    Subject[] captured = Subject.currentAll();
    AccessControlContext acc = AccessController.getContext();
    if (captured.length == 0) return task;
    return () -> AccessController.doPrivileged(
        (PrivilegedAction<Void>) () -> {
            Subject.callAs(() -> { task.run(); return null; },
                Arrays.copyOf(captured, captured.length, UserSubject[].class));
            return null;
        }, acc);
}
```

### 11.3 Daemon Thread Discipline

Daemon threads must be constructed outside any `callAs` scope, or must use
`AccessControlContext.neverPrivileged()` to permanently prevent Subject injection.

---

## 12. Transport Endpoint Subject Selection

### 12.1 `SslEndpointImpl` — TLS/SPIFFE

```
Priority 1: SpiffeCredentialManager.getInstance().getSubject()
            — direct access; definitive; instanceof WorkerSubject check
Priority 2: Subject.getWorker()
            — ACC combiner lookup fallback
Priority 3: (reject) — UserSubject and vanilla Subject are never used for TLS
```

### 12.2 `KerberosEndpoint` — Kerberos GSS

```
Priority 1: Subject.current()
            — UserSubject or vanilla Subject; KerberosPrincipal required
Priority 2: Subject.getSubject(acc) filtered
            — ACC fallback; WorkerSubject always rejected for Kerberos
```

---

## 13. Policy Grant Examples

### 13.1 Single-Party Request

```
// WorkerSubject principal from ProtectionDomain (baked at class load time)
// UserSubject principal injected by getContext() — survives doPrivileged
grant codeBase "file:/opt/jgdms/order-processor/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/order-processor"
      principal KerberosPrincipal "alice@EXAMPLE.ORG" {
    permission OrderPermission "submit";
};
```

### 13.2 Trust Tier Differentiation

```
// SELinux host — full financial operations
grant codeBase "file:/opt/jgdms/payment-svc/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/payment-svc" {
    permission FinancialPermission "execute,read,audit";
};

// Windows host — read only
grant codeBase "file:/opt/jgdms/payment-svc/-"
      principal SpiffePrincipal "spiffe://.../host/windows/payment-svc" {
    permission FinancialPermission "read";
};

// Home device — no access (no grant — deny-all baseline)
```

### 13.3 Remote Code + Remote Process Grant

```
// httpmd: SHA-256 verifies code identity; SpiffePrincipal verifies process identity
grant codeBase "httpmd://repo.example.org/client-stub.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../svc/trusted-client" {
    permission OrderPermission "submit";
};
```

### 13.4 Multi-Party Transaction

```
// Both users must be present simultaneously via callAs(action, alice, bob)
grant codeBase "file:/opt/jgdms/txn-svc/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/txn-svc"
      principal KerberosPrincipal "alice@EXAMPLE.ORG"
      principal KerberosPrincipal "bob@EXAMPLE.ORG" {
    permission TransactionPermission "commit";
};
```

### 13.5 Class Load Gate

```
// Only this specific workload on SELinux may load ASM
grant codeBase "httpmd://repo.example.org/asm.jar#SHA256:def456"
      principal SpiffePrincipal "spiffe://.../host/selinux/bytecode-svc" {
    permission LoadClassPermission;
};
```

---

## 14. Migration Path

### 14.1 Phase 1 — Current State

- Sealed Subject hierarchy: `WorkerSubject` (sealed, `SpiffeSubject` only),
  `UserSubject` (final, public)
- `SecureClassLoader` bakes `WorkerSubject` principals into every `ProtectionDomain`
- `Subject.callAs(Subject, Callable)` — OpenJDK-compatible, guards against `WorkerSubject`
- `Subject.callAs(Callable, UserSubject...)` — multi-Subject varargs, type-safe
- `SCOPED_SUBJECT` ScopedValue carries `Subject[]`
- `getContext()` injects `UserSubject` principals into stack domains and `privilegedContext`
- `@AtomicSerial` ACC serialization for JERI remote identity transmission
- `Thread.runWith()` re-establishes `SCOPED_SUBJECT` for spawned threads
- `SubjectDomainCombiner` deprecated
- `DomainCombiner` retained as API compatibility layer for ACC serializer
- `CombinerSecurityManager` refactoring deferred

### 14.2 Phase 2 — Direct Principal Injection in `checkPermission`

Replace `SubjectDomainCombiner` combiner dispatch with a direct private path in
`checkPermission` that reads `SCOPED_SUBJECT` without combiner invocation:
- Eliminates `goCombiner()` dispatch, `combine()` virtual call,
  and `ProtectionDomain[]` array allocation per `checkPermission` call
- One `CombinerSecurityManager` cache removed

### 14.3 Phase 3 — `DomainCombiner` Retirement (long term)

Once all internal Subject-injection uses are replaced by the direct path, and
the ACC serializer no longer requires `DomainCombiner` as a verification hook:
- `DomainCombiner` deprecated
- `Subject.doAs()` and `Subject.doAsPrivileged()` deprecated (except Kerberos GSS)
- ACC becomes purely a code-privilege-boundary mechanism

### 14.4 Phase 4 — Kerberos GSS-API (OpenJDK dependency)

Once OpenJDK provides a `callAs`-based GSS-API path:
- `KerberosUtil.getGSSCredential()` migrated to `callAs` path
- `Subject.doAs()` fully deprecated

---

## 15. Security Properties

### 15.1 What Each Layer Guarantees

| Property | Guarantee |
|---|---|
| `WorkerSubject` is mandatory and unforgeable | Baked into every `ProtectionDomain` at class load time. `WorkerSubject` is sealed; only `SpiffeSubject` (module-private) can be constructed. SPIRE platform attestation prevents forgery. |
| `WorkerSubject` survives `doPrivileged` | It is in the domain itself — not the combiner. Plain `doPrivileged` cannot shed it. |
| User identity survives `doPrivileged` | `UserSubject` principals are injected into `privilegedContext` at `getContext()` time. Buggy privileged code cannot accidentally bypass user-principal grants. |
| Remote identity cannot escalate to local trust | Remote `WorkerSubject` domains are shed at `doPrivileged` — they are not in `privilegedContext` on the local JVM. |
| Trust tier is machine-attested | SPIRE provisions SVID based on platform attestation. A Windows host cannot claim an SELinux SVID. |
| Class loading is SPIFFE-gated | `LoadClassPermission` check at class load time. Fail-secure on SPIRE unavailability. |
| Multi-party grants are atomic | All principals from all `SCOPED_SUBJECT` entries are merged additively. A grant requiring multiple users is only satisfied when all are simultaneously present. |
| Executor tasks do not accidentally inherit identity | `ScopedValue` does not propagate to executor tasks. Explicit wrapping required. |
| Daemon threads are identity-free by construction | `neverPrivileged()` ACC prevents Subject injection regardless of construction context. |
| `WorkerSubject` cannot be passed to `callAs(Callable, UserSubject...)` | Enforced at compile time by the `UserSubject` varargs type — no runtime check needed. |

### 15.2 What Cannot Be Bypassed

1. `WorkerSubject` identity at every permission check (in domain, not combiner)
2. Trust tier ceiling on `doPrivileged` grants
3. User identity requirement through `doPrivileged` boundaries
4. `WorkerSubject` construction (sealed + module-private)
5. `neverPrivileged` domain barrier on daemon threads
6. Multi-party transaction atomicity
7. Class load gate via `LoadClassPermission`
8. `WorkerSubject` exclusion from `callAs` varargs (compile-time type safety)

---

## Appendix A: Relationship to Existing Standards

| Standard | Relationship |
|---|---|
| JGDMS-STD-001 (@AtomicSerial) | `AccessControlContext` serialization uses `@AtomicSerial` format defined in STD-001 |
| JGDMS-STD-002 (SCAP Pipeline) | SCAP verifies code safety; this standard verifies runtime identity authority. Complementary — SCAP-approved code + SPIFFE identity together constitute full trust |
| OpenJDK `Subject.callAs()` (JEP 411) | JGDMS diverges deliberately — thread propagation extended to all spawned threads; `UserSubject` varargs; sealed hierarchy; `UserSubject` survives `doPrivileged` |

---

## Appendix B: Glossary

| Term | Definition |
|---|---|
| `WorkerSubject` | Sealed `Subject` subtype representing the JVM's SPIFFE identity, provisioned by SPIRE, baked into every `ProtectionDomain` at class load time |
| `UserSubject` | Final `Subject` subtype representing an authenticated user in a distributed transaction; may be local or remote; multiple instances may be present simultaneously |
| `SpiffeSubject` | The sole concrete `WorkerSubject` — `SpiffeCredentialManager.SpiffeSubject`; module-private |
| Remote process identity | `WorkerSubject` principals baked into remote `ProtectionDomain`s; travels via serialized ACC — not a separate type |
| Environmental identity | `WorkerSubject` — what machine/OS/security posture; cannot be shed |
| Operational authority | `UserSubject` — who authorised this operation; survives `doPrivileged` |
| Network topology identity | Remote process identity — where this call came from; shed at `doPrivileged` |
| Trust tier | Level of trust granted to a host based on OS/security posture, expressed via SPIFFE SVID path |
| `doPrivileged` boundary | Code vouches for itself; remote process identity shed; environmental and operational identity preserved |
| `neverPrivileged` | ACC containing static unprivileged `ProtectionDomain` — prevents all Subject injection; used for daemon threads |
| `LoadClassPermission` | Permission checked at class load time gating whether a class may be loaded into a given SPIFFE workload |
| `httpmd:` URL | `httpmd://host/path#SHA256:hash` — code identity verifiable by hash without loading; used in `RemotePolicy` grants |
| `DomainCombiner` | Retained as Java API compatibility layer for ACC serializer; `SubjectDomainCombiner` deprecated |
