# JGDMS — GrantPermission, Role Management & Full Architecture — AI Agent Context (v19)

**Purpose:** This document captures the full conversation context for an AI agent to
continue work on JGDMS role management and `GrantPermission` design without loss of
context. It supersedes and extends v18.

**GitHub repositories:**
- JGDMS: https://github.com/pfirmstone/JGDMS
- DirtyChai: https://github.com/pfirmstone/DirtyChai

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

---

## 1. Documents Read This Session (cumulative)

*(All entries from v18 §1 are retained. New/updated entries below.)*

| Document | Location | Key contribution |
|---|---|---|
| `AccessController.java` | DirtyChai source | ✅ **Updated (v19):** `getContext()` iterates `Subject[]` from `SubjectAccess.SCOPED.get()`; skips `WorkerSubject`; per-UserSubject `SubjectDomainCombiner.combine()` loop accumulates all principals; `SubjectAccess.get()` returns `Subject[]` not `Subject` |
| `Subject.java` | DirtyChai source | ✅ **Updated (v19):** `sealed permits WorkerSubject, UserSubject`; `callAs(Subject, Callable)` runtime guard rejects `WorkerSubject`; `callAs(Callable, UserSubject...)` compile-time type-safe varargs; `SCOPED_SUBJECT` is `ScopedValue<Subject[]>`; `NoCheck.current()` returns `Subject[]`; `currentAll()` clones array; `processWorker()` delegates to `SpiffeCredentialManager.getInstance().getSubject()` |
| `Thread.java` | DirtyChai source | ✅ **Updated (v19):** `scopedSubject` field is now `Subject[]` (was single `Subject`); captured at construction via `SubjectAccess.SCOPED.get()`; `runWith()` passes full array to `SubjectAccess.callNoCheck(scopedSubjects, action)` |
| `WorkerSubject.java` | DirtyChai source (`javax.security.auth`) | ✅ **Reviewed (v19):** `sealed class WorkerSubject extends Subject permits SpiffeCredentialManager.SpiffeSubject`; public constructor (sealed enforcement, not constructor visibility); used only by DirtyChai bootstrap `SpiffeCredentialManager` |
| `UserSubject.java` | DirtyChai source (`javax.security.auth`) | ✅ **Reviewed (v19):** `final class UserSubject extends Subject`; public constructor; used by JERI dispatcher and application transaction code |
| `SpiffeCredentialManager.java` (JGDMS) | `net.jini.jeri.ssl` | ✅ **Reviewed (v19):** JGDMS implementation; uses standard Java APIs; constructs vanilla `Subject` with `X500Principal` + `SpiffePrincipal`; `updateSubjectCredentials()` atomically rotates principals + credentials under `synchronized (subject)`; `SpiffeSubjectHolder.set(subject)` on start; `ScheduledExecutorService` for renewal; `AutoCloseable` |
| `SpiffeCredentialManager.java` (DirtyChai) | `au.zeus.jdk.authorization.spire` | ✅ **Reviewed (v19):** JDK bootstrap implementation; constructs sealed `SpiffeSubject extends WorkerSubject`; bootstrap-safe (no lambdas, `sun.security.util.Debug`); module-private `SpiffeSubject` constructor; `Subject.processWorker()` delegates here |
| `LocalWorkerSubject.java` → `WorkerSubject.java` | DirtyChai source | ✅ **Renamed (v19):** Class renamed from `LocalWorkerSubject` to `WorkerSubject` throughout |

---

## 2. Full Architecture Summary

*(Sections 2.1–2.5 unchanged from v18.)*

---

## 3–5. DynamicPolicyProvider, RemotePolicyService, GrantPermission

*(Unchanged from v18.)*

---

## 6. DirtyChai Security Model

### 6.1–6.3

*(Unchanged from v18.)*

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

*(Unchanged from v18, except `scopedSubject` field is now `Subject[]`:)*

`Thread` captures `Subject[] scopedSubjects = SubjectAccess.SCOPED.get()` at construction
(guarded by `VM.isBooted()`). `Thread.runWith()` calls
`SubjectAccess.callNoCheck(scopedSubjects, action)` — the full array is re-established,
preserving all transaction participants for `Subject.currentAll()` in spawned threads.

---

## 7–9. Authentication, ServiceUI

*(Unchanged from v18.)*

---

## 10. Three-Layer JERI Implementation

### 10.1–10.6

*(Unchanged from v18.)*

### 10.7 `AccessController.getContext()` — Multi-Subject Injection Loop

The finalised implementation iterates over the full `Subject[]` array:

```java
Subject[] subject = SubjectAccess.SCOPED.get(); // Subject[] via NoCheck trust chain
if (subject != null) {
    DomainCombiner existing = acc.getCombiner(); // preserved throughout loop
    for (int i = 0, l = subject.length; i < l; i++) {
        if (subject[i] instanceof WorkerSubject) continue; // never inject WorkerSubject
        SubjectDomainCombiner sdc = new SubjectDomainCombiner(subject[i]);
        ProtectionDomain[] combined = sdc.combine(acc.getContext(), acc.getContext());

        // Enrich immediate privilegedContext — user survives doPrivileged
        AccessControlContext privileged = acc.privilegedContext();
        if (privileged != null) {
            ProtectionDomain[] combinedPrivileged = sdc.combine(
                privileged.getContext(), privileged.getContext());
            privileged = AccessControlContext.create(
                combinedPrivileged, privileged.privilegedContext(),
                privileged.getCombiner(), privileged.isPrivileged());
        }

        // Restore original combiner; preserve isPrivileged.
        // The last iteration produces the ACC with ALL principals from all subjects.
        acc = AccessControlContext.create(combined, privileged, existing, acc.isPrivileged());
    }
}
return acc;
```

**Key properties:**

- `existing` combiner is captured once before the loop and restored on every iteration —
  the original combiner is never displaced regardless of how many subjects are processed.
- `WorkerSubject` instances are skipped — their principals are already in every
  `ProtectionDomain` from class load time via `SecureClassLoader`.
- Each iteration accumulates principals progressively — the final ACC contains all
  principals from all `UserSubject` entries merged additively.
- `privilegedContext` is enriched per subject — each user's principals survive
  `doPrivileged` boundaries independently.
- `acc.isPrivileged()` is preserved on every iteration — erasing it would silently
  destroy `doPrivileged` boundaries (security error).
- Bootstrap guard: `SubjectAccess.SCOPED.get()` only called when `VM.isBooted()`.

### 10.8–10.11

*(Unchanged from v18.)*

---

## 11. Remaining `doAs` / `doAsPrivileged` Call Sites — Migration Audit

*(Unchanged from v18, with v19 note below.)*

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

*(Unchanged from v18.)*

---

## 13. Key Design Decisions — Cumulative

*(All rows from v18 §13 are retained. New rows below.)*

| Decision | Rationale |
|---|---|
| **Two `SpiffeCredentialManager` implementations** | ✅ **v19:** DirtyChai bootstrap implementation (`au.zeus.jdk.authorization.spire`) creates sealed `SpiffeSubject extends WorkerSubject` for `SecureClassLoader` domain baking; JGDMS implementation (`net.jini.jeri.ssl`) creates vanilla `Subject` with `X500Principal` + `SpiffePrincipal` for TLS credential management using standard Java APIs. `Subject.processWorker()` bridges both via `SpiffeSubjectHolder`. Separation of concerns: bootstrap identity vs TLS transport credentials |
| **`Thread.scopedSubjects` is `Subject[]` not single `Subject`** | ✅ **v19:** Mirrors `SCOPED_SUBJECT ScopedValue<Subject[]>`; full transaction array captured at construction; `runWith()` re-establishes all subjects via `callNoCheck(scopedSubjects, action)`; `Subject.currentAll()` works correctly in spawned threads for multi-party transactions |
| **`getContext()` multi-subject loop with per-iteration `SubjectDomainCombiner`** | ✅ **v19:** Each `UserSubject` in the array gets its own `combine()` pass; principals accumulate progressively; final ACC contains all merged; `WorkerSubject` skipped; `existing` combiner preserved across all iterations; `privilegedContext` enriched per subject |
| **`SubjectAccess.get()` returns `Subject[]`** | ✅ **v19:** `NoCheck.current()` returns `Subject[]`; `SubjectAccess.get()` delegates to `current()`; consistent with `SCOPED_SUBJECT` type; no single-subject extraction at this level |

---

## 14. SPIFFE Identity Scheme

*(Unchanged from v18.)*

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
