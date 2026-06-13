# JGDMS-STD-003: Multi-Subject Identity Architecture

**Status:** Draft  
**Version:** 2.1  
**Applies to:** DirtyChai (JDK fork), JGDMS  
**Supersedes:** JGDMS-STD-003 v1.0  

---

## Change Log

### v2.0 changes from v1.0

- **`RemoteUserSubject` renamed to `UserSubject`** — reflects that the user
  may be local (SSH admin) or remote; the distinction is that it is a principals-only
  identity assertion that travels with a distributed transaction, not a JAAS session
  with credentials. Multiple instances may be present simultaneously in a transaction.

- **`doPrivileged` semantics for user identity revised** — `UserSubject`
  principals now survive `doPrivileged` boundaries alongside `WorkerSubject`.
  The correct split is environmental+operational identity (survives) vs network
  topology identity (shed). Allowing `doPrivileged` to shed user identity would
  permit buggy privileged code to bypass user-principal grants accidentally, which
  is a security weakness. Only `WorkerSubject` (process/platform of the
  caller's peer) is shed at `doPrivileged` boundaries.

- **`WorkerSubject` travels via reconstructed ACC, not `SCOPED_SUBJECTS`** —
  Remote process identity belongs on the call stack as `ProtectionDomain`s, not
  injected into local domain arrays. `WorkerSubject` principals are baked into
  remote `ProtectionDomain`s at class load time on the remote JVM and transmitted
  via serialized `AccessControlContext` over JERI endpoints. This prevents
  cross-contamination between local code grants and remote process identity.

- **`WorkerSubject` principals baked into `ProtectionDomain` at class load time**
  via `SecureClassLoader` — not injected by `getContext()`. This enables
  `LoadClassPermission` checking at class load time, restricting which classes may
  be loaded into a given process based on SPIFFE identity.

- **`getContext()` injection simplified** — only `UserSubject` principals
  are injected, into both stack domains and `privilegedContext`. `WorkerSubject`
  is never in `SCOPED_SUBJECTS`. `WorkerSubject` is already in every
  `ProtectionDomain` from class load time.

- **`Subject.callAs()` varargs overload added** — new overload
  `callAs(Callable<T> action, Subject... subjects)` supports multi-party
  transactions. The existing OpenJDK-compatible `callAs(Subject, Callable)` is
  retained unchanged and delegates to the new overload. `null` elements throw
  `NullPointerException`. `WorkerSubject` elements throw
  `IllegalArgumentException`.

- **`@AtomicSerial` ACC serialization** — `AccessControlContext` serialization
  added to transmit remote call stack identity over JERI endpoints, enabling
  `RemotePolicy` grants against `httpmd:` URIs and `WorkerSubject` principals.

- **`URIGrant` / `ScalableNestedPolicy` unchanged** — existing grant hierarchy
  handles SPIFFE principals correctly. `SpiffePrincipal` URI stability (guaranteed
  by SPIFFE specification) means `SubjectGrant` is not needed.

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
simultaneously.

**`doPrivileged` drops Subject identity.** A plain `doPrivileged` call drops the
`SubjectDomainCombiner` from the ACC. This means policy grants conditioned on a SPIFFE
workload principal are not enforced inside `doPrivileged` blocks — a significant
security gap when the goal is mandatory enforcement of execution environment identity
(e.g. requiring SELinux) or user authorisation identity.

**`doPrivileged` can accidentally bypass user grants.** If user identity is shed at
`doPrivileged` boundaries, buggy privileged code can inadvertently satisfy grants that
should require an admin or root principal. The correct model requires user identity to
persist through `doPrivileged` — only the calling process's network topology identity
should be shed.

**No trust tier for execution environment.** The standard model has no mechanism to
distinguish a process running on a hardened SELinux host from one running on a home
device. All processes are treated identically regardless of their execution environment.

**No multi-party transaction support.** A single Subject cannot represent multiple
simultaneously-required identities (e.g. two parties to a financial transaction both
of whom must be present for a commit permission to be granted).

**No type-level distinction between identity classes.** Without type-level distinctions,
routing logic must rely on principal inspection rather than type dispatch, which is
fragile and error-prone.

**Remote process identity contaminates local grants.** Injecting remote process
principals into local `ProtectionDomain`s would cause grants to require those remote
principals permanently, breaking policy stability as remote workers change.

### 2.2 Design Goals

1. **Mandatory environmental enforcement.** The local process identity must be enforced
   at every permission check including inside `doPrivileged` blocks. It must be
   impossible for application code to shed or bypass this constraint.

2. **Mandatory user enforcement.** User identity (who authorised this operation) must
   survive `doPrivileged` boundaries. `doPrivileged` asserts code trustworthiness,
   not operational authority. A privileged library performing a file read on behalf of
   a user must still require the user principal — otherwise any caller of that library
   can trigger the operation without being an authenticated user.

3. **Correct `doPrivileged` semantics for network topology.** Only the remote peer's
   process identity is shed at `doPrivileged` boundaries — it is a caller-stack
   concern, not an authorisation concern.

4. **Remote process identity travels as call stack domains.** Remote `ProtectionDomain`s
   with `WorkerSubject` principals baked in at class load time on the remote JVM
   are transmitted via serialized ACC and participate in local permission checks as
   stack domains. This avoids contaminating local domain construction.

5. **Class loading gated by SPIFFE identity.** `SecureClassLoader` bakes `WorkerSubject`
   principals into every `ProtectionDomain` at class load time, enabling
   `LoadClassPermission` checks that restrict which classes may load into a given process.

6. **Multi-party transaction support.** Multiple users must be expressible
   simultaneously, with policy grants conditioned on all of them being present.

7. **Type safety.** Each identity class is a distinct sealed type.

8. **Backwards compatibility.** Existing code using vanilla `Subject`, `Subject.doAs()`,
   and `Subject.current()` continues to work unchanged.

9. **Migration path.** The architecture provides a clear path toward retirement of
   `DomainCombiner` and direct principal injection in permission checks.

---

## 3. The Identity Layers

### 3.1 Overview

| Layer | Type | Carrier | Survives `doPrivileged` | Lifetime | Constructed by |
|---|---|---|---|---|---|
| Local process worker | `WorkerSubject` | Baked into `ProtectionDomain` at class load time | **Yes** — in every domain | JVM lifetime | SPIRE only (sealed) |
| Remote process worker | `WorkerSubject` | Serialized ACC domains over JERI | **No** — not in `privilegedContext` | Per-connection | JERI dispatcher |
| Distributed user | `UserSubject` | `SCOPED_SUBJECTS` ScopedValue | **Yes** — injected into `privilegedContext` | Per-request / transaction | JERI dispatcher |
| Local user (legacy) | `Subject` (vanilla) | `SCOPED_SUBJECTS` ScopedValue or ACC | **Yes** (ScopedValue path) | Per-session | JAAS `LoginContext` |

### 3.2 The `doPrivileged` Boundary Rule

```
Survives doPrivileged:  who you are (local worker + distributed user)
Shed at doPrivileged:   where you came from (remote worker)
```

`doPrivileged` asserts: *"this code takes responsibility — check only my
`ProtectionDomain`."* This is a code trustworthiness assertion, not an authorisation
bypass. The local worker answers "what machine is this running on" — a `doPrivileged`
block on Windows cannot claim SELinux trust. The distributed user answers "who
authorised this operation" — a `doPrivileged` block cannot shed that authorisation
without creating a bypass vulnerability.

Only the remote worker — the network origin of the call — is correctly shed, because
`doPrivileged` legitimately says "I, this code, vouch for this operation regardless of
which remote peer called me."

### 3.3 Local Process Worker

The local process worker represents the execution environment of the JVM itself — the
machine, its operating system, and its security posture. It is provisioned by SPIRE at
workload startup and rotated automatically on SVID expiry.

**Key properties:**
- Static for the JVM lifetime (SVID rotation updates credentials, not identity)
- Baked into every `ProtectionDomain` by `SecureClassLoader` at class load time
- Always present at every permission check — in the domain itself, not the combiner
- Used for outbound TLS connections
- Never passed to `Subject.callAs()` — it is ambient, not caller-supplied
- Can only be constructed by SPIRE infrastructure (`SpiffeCredentialManager.SpiffeSubject`)
- If unavailable at class load time, `ProtectionDomain` has no principals — 
  `LoadClassPermission` check fails, class is rejected (fail-secure)

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

### 3.4 Remote Process Worker

The remote process worker represents the SPIFFE identity of a remote machine that has
established a connection to this JVM. Its principals are baked into remote
`ProtectionDomain`s at class load time on the remote JVM.

**Key properties:**
- Per-connection lifetime
- Travels as `ProtectionDomain`s in a serialized `AccessControlContext` over JERI
- Participates in permission checks as stack domains — not injected locally
- Does NOT appear in `SCOPED_SUBJECTS`
- Shed at `doPrivileged` boundaries (not in `privilegedContext`)
- Represents the peer machine's trust tier
- `httpmd:` URLs carry SHA-256 signatures — code identity verified without loading

**ACC serialization via JERI:**

The remote thread's `AccessControlContext` is serialized using `@AtomicSerial` and
transmitted over the JERI endpoint. The receiving JVM reconstitutes the ACC with
remote `ProtectionDomain`s containing:
- `httpmd:` `CodeSource` URLs with SHA-256 signatures (verifiable without loading)
- `WorkerSubject` principals baked in at remote class load time

A `DomainCombiner` on the receiving side strips any domain it cannot verify before
placing the remote ACC on the local call stack. The reconstituted domains participate
in `RemotePolicy` checks directly.

**`RemotePolicy` grant example:**
```
grant codeBase "httpmd://repo.example.org/client-stub.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../svc/trusted-client" {
    permission OrderPermission "submit";
};
```

### 3.5 Distributed User Subject

The distributed user subject represents an authenticated user identity that travels
with a distributed transaction. It may be a local administrator (SSH session), a
remote user, or one of several parties in a multi-party transaction. The name reflects
the distributed transaction semantics rather than network topology.

**Key properties:**
- Per-request or per-transaction lifetime
- Carried as a `ScopedValue` (`SCOPED_SUBJECTS`)
- Injected into both stack domains AND `privilegedContext` — **survives `doPrivileged`**
- Read-only, no credentials (principals only)
- May be absent (service-to-service calls with no user context)
- Multiple instances may be present simultaneously (transaction context)
- `Subject.current()` returns the first `UserSubject` in the array
- `Subject.callAs(Callable, Subject...)` varargs overload supports multiple instances

**Why it survives `doPrivileged`:**
If a grant requires `admin@EXAMPLE.ORG`, no amount of `doPrivileged` nesting can
satisfy it without that principal being present. Buggy privileged code that calls
`doPrivileged` cannot accidentally bypass a grant requiring admin identity. The security
guarantee is unconditional rather than contingent on every `doPrivileged` call in the
chain being correctly written.

### 3.6 Local User Subject (Legacy)

The vanilla `Subject` class represents a locally-authenticated user, typically via
JAAS `LoginContext` (Kerberos, etc.). Retains existing semantics for backwards
compatibility.

**Key properties:**
- `Subject.doAs()` routes to this type — unchanged legacy semantics
- `Subject.current()` returns this type if no `UserSubject` present
- Used by `KerberosEndpoint` for GSS credential acquisition
- Survives `doPrivileged` (carried on `SCOPED_SUBJECTS` ScopedValue path)

---

## 4. The Sealed Subject Hierarchy

### 4.1 Class Definitions

```java
/**
 * Base class. Vanilla Subject represents a locally-authenticated user
 * (Kerberos, JAAS). Sealed to permit only the defined identity subtypes.
 */
public sealed class Subject
    permits WorkerSubject, UserSubject {
    // existing Subject implementation
}

/**
 * Represents the local JVM process identity, provisioned by SPIRE.
 * Baked into every ProtectionDomain at class load time by SecureClassLoader.
 * Survives doPrivileged — present in every domain. Never passed to callAs().
 * Cannot be constructed by application code.
 */
public sealed class WorkerSubject extends Subject
    permits SpiffeCredentialManager.SpiffeSubject {
    protected WorkerSubject(boolean readOnly,
                                  Set<? extends Principal> principals,
                                  Set<?> pubCredentials,
                                  Set<?> privCredentials) {
        super(readOnly, principals, pubCredentials, privCredentials);
    }
}

/**
 * Represents an authenticated user identity in a distributed transaction.
 * May be local (SSH admin) or remote. Multiple instances may be present
 * simultaneously. Carried as a ScopedValue. Survives doPrivileged boundaries.
 * Read-only, no credentials.
 */
public final class UserSubject extends Subject {
    public UserSubject(Set<Principal> principals) {
        super(true, principals, emptySet(), emptySet());
    }
}

/**
 * Represents the SPIFFE identity of a remote peer process.
 * NOT a Subject subtype — travels as ProtectionDomain principals
 * in a serialized AccessControlContext over JERI endpoints.
 * Shed at doPrivileged boundaries.
 */
public final class WorkerSubject extends Subject {
    public WorkerSubject(Set<Principal> principals) {
        super(true, principals, emptySet(), emptySet());
    }
}
```

**Note on `WorkerSubject`:** Although `WorkerSubject` extends `Subject`
for type hierarchy purposes, it is never placed in `SCOPED_SUBJECTS` and is never
passed to `callAs()`. Its principals travel exclusively via serialized ACC domains.
It is `final` and cannot be subclassed further.

### 4.2 Construction Constraints

| Type | Constructor access | Rationale |
|---|---|---|
| `Subject` | Public sealed | Backwards compatibility; JAAS LoginContext |
| `WorkerSubject` | `Public` (`javax.security.auth`) sealed | Only `SpiffeSubject` (sealed permit) may be constructed |
| `SpiffeCredentialManager.SpiffeSubject` | Package-private (`au.zeus.jdk.authorization.spire`) | Only SPIRE infrastructure constructs local worker identity |
| `UserSubject` | Public final | JERI dispatcher constructs from wire header |

### 4.3 No Further Subclassing

`UserSubject` is `final`. `WorkerSubject`
is sealed with only `SpiffeSubject` permitted. No further subclassing is permitted
unless a concrete need is identified.

---

## 5. `Subject::callAs` — Both Overloads

### 5.1 OpenJDK-Compatible Overload (unchanged)

```java
/**
 * Executes a Callable with subject as the current scoped identity.
 * OpenJDK-compatible signature. Delegates to the varargs overload.
 *
 * @param subject the Subject to bind; must not be null or WorkerSubject
 * @param action  the code to execute; must not be null
 */
public static <T> T callAs(Subject subject, Callable<T> action)
    throws CompletionException {
    Objects.requireNonNull(subject, "subject");
    return callAs(action, subject); // delegates to varargs overload
}
```

### 5.2 Multi-Subject Varargs Overload (JGDMS extension)

```java
/**
 * Executes a Callable with the provided subjects as the current scoped
 * identity for the duration of the call on the current thread.
 *
 * <p> Subjects are bound to SCOPED_SUBJECTS for the duration of action.
 * UserSubject principals are injected into the
 * AccessControlContext's ProtectionDomain array at getContext() time,
 * including into privilegedContext, ensuring user identity survives
 * doPrivileged boundaries.
 *
 * <p> WorkerSubject must not be passed — the local process identity
 * is always ambient, baked into ProtectionDomains at class load time.
 *
 * <p> WorkerSubject must not be passed — remote process identity
 * travels via serialized AccessControlContext domains, not ScopedValue.
 *
 * <p> Calls may be nested; each nested call shadows the previous bindings
 * for its duration, restoring them when action completes whether normally
 * or exceptionally.
 *
 * @param action   the code to execute; must not be null
 * @param subjects zero or more Subject instances; must not be null;
 *                 no element may be null; WorkerSubject and
 *                 WorkerSubject elements are rejected
 *
 * @throws NullPointerException     if action is null, subjects is null,
 *                                  or any element of subjects is null
 * @throws IllegalArgumentException if any element is a WorkerSubject
 *                                  or WorkerSubject
 */
public static <T> T callAs(Callable<T> action, Subject... subjects)
    throws CompletionException {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(subjects, "subjects");
    for (int i = 0; i < subjects.length; i++) {
        Objects.requireNonNull(subjects[i], "subjects[" + i + "] must not be null");
        if (subjects[i] instanceof WorkerSubject)
            throw new IllegalArgumentException(
                "WorkerSubject must not be passed to callAs() — " +
                "local process identity is ambient");
        if (subjects[i] instanceof WorkerSubject)
            throw new IllegalArgumentException(
                "WorkerSubject must not be passed to callAs() — " +
                "remote process identity travels via serialized ACC domains");
    }
    // ScopedValue.where(SCOPED_SUBJECTS, subjects).call(action)
}
```

### 5.3 Common Call Patterns

```java
// Service-to-service — no user context
Subject.callAs(action); // zero subjects — valid

// Single user (common case, also OpenJDK-compatible)
Subject.callAs(userSubject, action);           // existing overload
Subject.callAs(action, userSubject);           // new overload — equivalent

// Two-party transaction
Subject.callAs(action, userAlice, userBob);

// Multi-party transaction with multiple users
Subject.callAs(action, partyA, partyB, partyC);
```

### 5.4 `Subject.current()` Semantics

Returns the first `UserSubject` or vanilla `Subject` found in
`SCOPED_SUBJECTS`, or `null`. Backwards-compatible — existing callers expecting
a single user Subject get the primary user identity.

```java
public static Subject current() {
    if (!SCOPED_SUBJECTS.isBound()) return null;
    Subject[] all = SCOPED_SUBJECTS.get();
    for (int i = 0; i < all.length; i++) {
        if (all[i] instanceof UserSubject
                || all[i].getClass() == Subject.class)
            return all[i];
    }
    return null;
}
```

### 5.5 `Subject.currentAll()`

Returns all Subjects currently bound in `SCOPED_SUBJECTS`, or empty array.
Never includes `WorkerSubject` or `WorkerSubject`.

---

## 6. `Subject::doAs` Routing

### 6.1 Type Constraints

```java
public static <T> T doAs(Subject subject, PrivilegedAction<T> action) {
    if (subject instanceof UserSubject
            || subject instanceof WorkerSubject) {
        throw new IllegalArgumentException(
            "UserSubject and WorkerSubject must use Subject.callAs()");
    }
    if (subject instanceof WorkerSubject) {
        throw new IllegalArgumentException(
            "WorkerSubject is established by SPIRE infrastructure");
    }
    // existing doAs implementation — vanilla Subject and null only
}
```

### 6.2 Retained Uses

`Subject.doAs()` is retained for:
- **Kerberos GSS-API** — JDK GSS-API reads Subject from ACC internally
- **Legacy JAAS** — `AbstractJiniService` traditional JAAS `LoginContext` subjects

---

## 7. Subject Retrieval API

### 7.1 `Subject.current()` — primary user, backwards-compatible
### 7.2 `Subject.currentAll()` — full Subject array for transaction context
### 7.3 `Subject.getLocalWorker()` — retrieves `WorkerSubject` from ACC - Maybe can just get the global scope instead?

```java
public static WorkerSubject getLocalWorker() {
    Subject s = Subject.getSubject(AccessController.getContext());
    return s instanceof WorkerSubject lws ? lws : null;
}
```

Note: `Subject.getSubject(acc)` also returns the `WorkerSubject` naturally
when no `DomainCombiner` is present (e.g. inside a `doPrivileged` block), since
the local worker principals are in `privilegedContext`.

---

## 8. `AccessController.getContext()` — Injection Rules

### 8.1 Overview

After `optimize()`, `getContext()` reads `SCOPED_SUBJECTS` and injects
`UserSubject` principals into the ACC's `ProtectionDomain` array.
`WorkerSubject` and `WorkerSubject` are never in `SCOPED_SUBJECTS`
and are never injected here.

```java
Subject[] scoped = SCOPED_SUBJECTS.isBound() ? SCOPED_SUBJECTS.get() : null;
if (scoped != null && scoped.length > 0) {
    Set<Principal> userPrincipals = new LinkedHashSet<>();
    for (int i = 0; i < scoped.length; i++) {
        // Only UserSubject and vanilla Subject are injected
        if (scoped[i] instanceof UserSubject
                || scoped[i].getClass() == Subject.class) {
            userPrincipals.addAll(scoped[i].getPrincipals());
        }
        // WorkerSubject: never in SCOPED_SUBJECTS — assertion only
        assert !(scoped[i] instanceof WorkerSubject);
        // WorkerSubject: never in SCOPED_SUBJECTS — assertion only
        assert !(scoped[i] instanceof WorkerSubject);
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

        // Restore original combiner; preserve all ACC structural properties
        acc = AccessControlContext.create(
            combined,
            privileged,
            existing,
            acc.isPrivileged()); // MUST preserve — erasing this is a security error
    }
}
return acc;
```

### 8.2 Injection Rules by Type

| Subject type | Stack domains | `privilegedContext` | Rationale |
|---|---|---|---|
| `WorkerSubject` | Already in every domain from class load | Already in every domain | Environmental — baked at load time |
| `WorkerSubject` | Not injected — travels via ACC domains | Not injected | Network topology — shed at `doPrivileged` |
| `UserSubject` | Injected | **Injected** | Operational authority — survives `doPrivileged` |
| Vanilla `Subject` | Injected | **Injected** | Legacy user — same semantics as `UserSubject` |

### 8.3 Bootstrap Safety

`SCOPED_SUBJECTS.isBound()` is only called when `VM.isBooted()`.

---

## 9. `SecureClassLoader` — SPIFFE Identity at Class Load Time

`SecureClassLoader.getProtectionDomain()` calls
`SpiffeCredentialManager.getInstance().getSubject()` and bakes the
`WorkerSubject` principals into every constructed `ProtectionDomain`.

**Fail-secure behaviour:** If `getSubject()` returns `null` (SPIRE not yet connected
at bootstrap), `pals` is `null` and the `ProtectionDomain` has no principals. The
`LoadClassPermission` check then fails for any grant that requires a SPIFFE principal,
preventing class loading until SPIRE provides credentials. This is the correct
fail-secure behaviour.

**`LoadClassPermission` as a class load gate:**
```
grant codeBase "httpmd://repo.example.org/asm.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../host/selinux/bytecode-svc" {
    permission LoadClassPermission;
};
```
Only `bytecode-svc` on an SELinux host can load ASM. A different workload — even on
the same machine — cannot. A compromised workload that finds a vulnerability in ASM
cannot exploit it in a different workload because ASM's `ProtectionDomain` will not
satisfy grants requiring a different SPIFFE principal.

---

## 10. Serialized ACC Transmission over JERI

### 10.1 Overview

Remote process identity travels as `ProtectionDomain`s in a serialized
`AccessControlContext` transmitted over JERI endpoints. This enables
`RemotePolicy` grants to be checked against the remote call stack directly,
without contaminating local domain construction.

### 10.2 Remote Domain Construction

On the remote JVM, `SecureClassLoader` bakes `WorkerSubject` principals
 into each `ProtectionDomain` at class
load time. The remote ACC therefore contains domains of the form:

```
ProtectionDomain(
    codeSource = httpmd://repo.example.org/client-stub.jar#SHA256:abc123,
    principals = [
        SpiffePrincipal("spiffe://.../host/selinux/client-svc"),  // Local WorkerSubject
    ]
)
```

### 10.3 `@AtomicSerial` ACC Serialization

`AccessControlContext` implements `@AtomicSerial` serialization. The serialized form
contains:
- `ProtectionDomain[]` — domains with codebase URIs and principals
- `isPrivileged` flag
- `privilegedContext` reference (serialized recursively if non-null)

The `DomainCombiner` is NOT serialized — it is local infrastructure.

### 10.4 Receiving Side Verification

On the receiving JVM, a `DomainCombiner` strips any domain whose:
- `httpmd:` URL SHA-256 does not match the jar content
- `SpiffePrincipal` is not in the trusted SPIFFE trust domain
- Domain cannot be independently verified

Stripped domains are replaced with a minimal unprivileged domain. The verified
domains are placed on the call stack ACC and participate in `RemotePolicy` checks.

### 10.5 `RemotePolicy` Grant Model

```
// Local code grant — SpiffePrincipal from WorkerSubject in local ProtectionDomain
grant codeBase "file:/opt/jgdms/order-processor/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/order-processor" {
    permission OrderPermission "read";
};

// Remote code grant — verified by httpmd: SHA-256 + WorkerSubject principal
grant codeBase "httpmd://repo.example.org/client-stub.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../svc/trusted-client" {
    permission OrderPermission "submit";
};

// User elevation — UserSubject injected into privilegedContext
grant codeBase "file:/opt/jgdms/order-processor/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/order-processor"
      principal KerberosPrincipal "admin@EXAMPLE.ORG" {
    permission OrderPermission "delete";
};
```

---

## 11. Thread Propagation

### 11.1 Spawned Threads

When a thread is constructed inside a `callAs` scope:

1. `AccessController.getContext()` produces an ACC with `UserSubject`
   principals baked into the domain array (including `privilegedContext`).
2. This enriched ACC is stored as `inheritedAccessControlContext`.
3. `Subject[]` is captured into `Thread.scopedSubjects` at construction time
   (guarded by `VM.isBooted()`).
4. `Thread.runWith()` (`final`) re-establishes `SCOPED_SUBJECTS` via
   `Subject.SubjectAccess.callNoCheck(scopedSubjects, action)` before invoking
   the task.

**OpenJDK divergence:** In OpenJDK, `Subject.callAs` identity does not propagate
to threads started outside `StructuredTaskScope`. In JGDMS, propagation occurs for
all spawned threads. This is intentional.

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
            Subject.callAs(() -> { task.run(); return null; }, captured);
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
Priority 1: WorkerSubject from ACC  — definitive; type-checked
Priority 2: SpiffeSubjectHolder.get()    — process-wide fallback
Priority 3: Subject.current() filtered   — last resort; SpiffePrincipal required;
                                           UserSubject always rejected
```

### 12.2 `KerberosEndpoint` — Kerberos GSS

```
Priority 1: Subject.current() — UserSubject or vanilla Subject;
                                 KerberosPrincipal required
Priority 2: Subject.getSubject(acc) — fallback; WorkerSubject rejected
```

---

## 13. Policy Grant Examples

### 13.1 Single-Party Request

```
grant codeBase "file:/opt/jgdms/order-processor/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/order-processor"
      principal KerberosPrincipal "alice@EXAMPLE.ORG" {
    permission OrderPermission "submit";
};
```

The `SpiffePrincipal` is satisfied by `WorkerSubject` baked into the domain at
class load time. The `KerberosPrincipal` is satisfied by `UserSubject`
injected by `getContext()`. Both survive `doPrivileged` — buggy privileged code
cannot bypass either constraint.

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
// Remote code verified by SHA-256; remote process verified by SPIFFE
grant codeBase "httpmd://repo.example.org/client-stub.jar#SHA256:abc123"
      principal SpiffePrincipal "spiffe://.../svc/trusted-client" {
    permission OrderPermission "submit";
};
```

### 13.4 Multi-Party Transaction

```
// Both users must be present simultaneously — neither can commit alone
grant codeBase "file:/opt/jgdms/txn-svc/-"
      principal SpiffePrincipal "spiffe://.../host/selinux/txn-svc"
      principal KerberosPrincipal "alice@EXAMPLE.ORG"
      principal KerberosPrincipal "bob@EXAMPLE.ORG" {
    permission TransactionPermission "commit";
};
```

The remote workers travel via ACC domains and are checked by `RemotePolicy` against
their respective `httpmd:` codebase grants separately. The user principals are checked
by the local policy after `UserSubject` injection.

### 13.5 Class Load Gate

```
// Only this specific workload on SELinux may load ASM bytecode manipulation
grant codeBase "httpmd://repo.example.org/asm.jar#SHA256:def456"
      principal SpiffePrincipal "spiffe://.../host/selinux/bytecode-svc" {
    permission LoadClassPermission;
};
```

---

## 14. Migration Path

### 14.1 Phase 1 — Current State

- Sealed Subject hierarchy: `WorkerSubject`, `UserSubject`
- `SecureClassLoader` bakes `WorkerSubject` principals into every `ProtectionDomain`
- `Subject.callAs(Callable, UserSubject...)` varargs overload implemented
- `SCOPED_SUBJECTS` ScopedValue carries `Subject[]`
- `getContext()` injects `UserSubject` principals into stack domains
  and `privilegedContext`
- `@AtomicSerial` ACC serialization for JERI remote identity transmission
- `Thread.runWith()` re-establishes `SCOPED_SUBJECTS` for spawned threads
- `DomainCombiner` retained; `CombinerSecurityManager` unchanged

### 14.2 Phase 2 — Direct Principal Injection in `checkPermission`

Replace `SubjectDomainCombiner` combiner dispatch with a direct private path that
reads `SCOPED_SUBJECTS` without combiner invocation:
- Eliminates `goCombiner()` dispatch, `combine()` virtual call,
  `ProtectionDomain[]` array allocation per `checkPermission` call
- One `CombinerSecurityManager` cache removed

### 14.3 Phase 3 — `DomainCombiner` Retirement

- `DomainCombiner` and `SubjectDomainCombiner` deprecated
- `Subject.doAs()` and `Subject.doAsPrivileged()` deprecated (except Kerberos GSS)
- ACC becomes purely a code-privilege-boundary mechanism

### 14.4 Phase 4 — Kerberos GSS-API (OpenJDK dependency)

- `KerberosUtil.getGSSCredential()` migrated to `callAs` path
- `Subject.doAs()` fully deprecated

---

## 15. Security Properties

### 15.1 What Each Layer Guarantees

| Property | Guarantee |
|---|---|
| Local worker is mandatory and unforgeable | Baked into every `ProtectionDomain` at class load time by `SecureClassLoader`. Only `SpiffeCredentialManager.SpiffeSubject` (sealed, package-private) can construct a `WorkerSubject`. SPIRE platform attestation prevents forgery. |
| Local worker survives `doPrivileged` | It is in the domain itself — not the combiner. Plain `doPrivileged` cannot shed it. |
| User identity survives `doPrivileged` | `UserSubject` principals are injected into `privilegedContext` at `getContext()` time. Buggy privileged code cannot accidentally bypass user-principal grants. |
| Remote identity cannot escalate to local trust | Remote `WorkerSubject` travels via serialized ACC domains only. It never appears in `privilegedContext` on the local JVM. A compromised remote peer cannot elevate to local worker trust. |
| Trust tier is machine-attested | SPIRE provisions SVID based on platform attestation. A Windows host cannot claim an SELinux SVID. Application code cannot influence it. |
| Class loading is SPIFFE-gated | `LoadClassPermission` check at class load time. If SPIRE is unavailable, `pals` is null, grant fails, class is rejected. Fail-secure. |
| Multi-party grants are atomic | All principals from all `SCOPED_SUBJECTS` entries are merged additively. A grant requiring multiple parties is only satisfied when all are simultaneously present. |
| Executor tasks do not accidentally inherit identity | `ScopedValue` does not propagate to executor tasks. Explicit wrapping required. |
| Daemon threads are identity-free by construction | `neverPrivileged()` ACC prevents Subject injection regardless of construction context. |

### 15.2 What Cannot Be Bypassed

1. Local worker identity at every permission check
2. Trust tier ceiling on `doPrivileged` grants
3. User identity requirement through `doPrivileged` boundaries
4. `WorkerSubject` construction (sealed, SPIRE only)
5. `neverPrivileged` domain barrier on daemon threads
6. Multi-party transaction atomicity
7. Class load gate via `LoadClassPermission`

---

## Appendix A: Relationship to Existing Standards

| Standard | Relationship |
|---|---|
| JGDMS-STD-001 (@AtomicSerial) | `AccessControlContext` serialization uses `@AtomicSerial` format defined here |
| JGDMS-STD-002 (SCAP Pipeline) | SCAP verifies code safety; this standard verifies runtime identity authority. Complementary — SCAP-approved code + SPIFFE identity together constitute full trust |
| OpenJDK `Subject.callAs()` (JEP 411) | JGDMS diverges deliberately — thread propagation extended; varargs multi-Subject; sealed hierarchy; `UserSubject` survives `doPrivileged` |

## Appendix B: Glossary

| Term | Definition |
|---|---|
| Local worker | `WorkerSubject` — this JVM's SPIFFE identity, provisioned by SPIRE, baked into every `ProtectionDomain` at class load time |
| Remote worker | `WorkerSubject` — peer process SPIFFE identity, travels via serialized ACC domains |
| Distributed user | `UserSubject` — authenticated user in a distributed transaction; may be local or remote; survives `doPrivileged` |
| Environmental identity | Local worker — what machine/OS/security posture; cannot be shed |
| Operational authority | Distributed user — who authorised this operation; cannot be shed |
| Network topology identity | Remote worker — where this call came from; shed at `doPrivileged` |
| Trust tier | Level of trust granted to a host based on OS/security posture, expressed via SPIFFE SVID path |
| `doPrivileged` boundary | Code vouches for itself; network topology identity shed; environmental and operational identity preserved |
| `neverPrivileged` | ACC containing static unprivileged `ProtectionDomain` — prevents all Subject injection; used for daemon threads |
| `LoadClassPermission` | Permission checked at class load time gating whether a class may be loaded into a given SPIFFE workload |
| `httpmd:` URL | `httpmd://host/path#SHA256:hash` — code identity verifiable by hash without loading; used in `RemotePolicy` grants |
