# Part 3b — Multi-Subject Dispatch and Distributed Transaction Authorization

*This is the third post (part b) in a six-part series on JGDMS and DirtyChai.
Prerequisite: [Part 3a](blog-post-3a-identity-model.md).*
*The series index is at the bottom of this post.*

---

Part 3a described the `WorkerSubject` (the ambient process identity baked into every
`ProtectionDomain`) and remote process identity (the serialized ACC transmitted over the wire).
This post covers the third layer: `UserSubject` — the human user's JWT/OIDC identity — and the
wire protocol that transmits up to 16 such Subjects per call.

Why would a single RPC carry more than one user identity? Consider a financial transfer: the
initiating user submits the request, but the organization's policy requires a second user (an
approver) to be simultaneously authenticated before the transaction can commit. In a conventional
framework this requires out-of-band coordination or a session-state lookup. In JGDMS, both
identities travel on the same call, and `SettleTransactionPermission` enforces the quorum at
transaction commit time — no out-of-band mechanism needed.

---

## UserSubject and JWT/OIDC Identity

`UserSubject` represents a human user. User identity is established via **JWT/OIDC** using
`JwtLoginModule` from the `jgdms-security-jwt` module. The resulting `UserSubject` carries
`JwtPrincipal` instances (e.g. `"sub:alice@example.org"`, `"group:admins"`) and is installed
per-request via `Subject.callAs(jwtUserSubject, () -> ...)`. Kerberos is supported for legacy
deployments.

Unlike `WorkerSubject`, `UserSubject` is request-scoped: it is installed at the start of a
dispatch and is not present outside that call's `callAs` scope. It is not ambient.

---

## Multi-Subject Wire Protocol

The JERI layer transmits user Subjects in-band in **JERI wire protocol version `0x02`** — a
channel distinct from and independent of the TLS handshake. The protocol supports up to
**16 user `Subject`s per call**, each carrying up to **64 principals**:

```
0x02 user-Subject block:
  subjectCount : u16          (max 16)
  per Subject:
    principalCount : u16      (max 64)
    per Principal:
      className : UTF-8
      name      : UTF-8
```

### Client-Side (BasicInvocationHandler)

`CURRENT_ALL_METHOD` — a `static final Method` field — is cached once at class-load time via
reflection. On **DirtyChai** it resolves to `Subject.currentAll()`, a DirtyChai extension that
returns all currently active user Subjects so every delegation layer is transmitted. On a
**standard JDK** the field is `null` and `getAllUserSubjects()` falls back to `Subject.current()`
wrapped in a one-element array. This means there is zero per-call reflection overhead on a
standard JDK.

### Server-Side (BasicInvocationDispatcher)

`CALL_AS_MULTI_SUBJECT` — also a `static final Method` field, `null` on a standard JDK — is cached
once at class-load time via reflection. The dispatch strategy differs by JDK:

- **DirtyChai** (`Subject.callAs(Callable, Subject...)` varargs exists): all user Subjects are
  passed in a **single** `callAs` call, so the JVM establishes them simultaneously.
- **Standard JDK** (no varargs `callAs`): only `userSubjects[0]` is used with
  `Subject.callAs(first, action)`. Nesting multiple single-Subject `callAs` calls is *incorrect*
  on a standard JDK because each inner call shadows the outer one, leaving only the innermost
  Subject visible via `Subject.current()`.

> **Correctness hazard:** The standard-JDK fallback is not a graceful degradation — it silently
> loses all but the innermost Subject. If your service logic checks for the presence of multiple
> Subjects (e.g. to enforce a dual-authorization policy), running on a standard JDK will silently
> bypass that check. DirtyChai is required for multi-Subject dispatch to be correct.

`Subject.current()` returns only the first Subject bound via `callAs` — it never falls back to the
`AccessControlContext`. This ensures the server can always distinguish TLS-verified machine identity
from wire-asserted human identity.

### Retrieving Subjects in Service Code

```java
// inside a dispatched service method:
ClientUserSubject cus = (ClientUserSubject)
    ServerContext.getServerContextElement(ClientUserSubject.class);
Subject[] users = cus.getUserSubjects();   // all wire-transferred Subjects, outermost-first
Subject primary = cus.getUserSubject();    // subjects[0] — convenience for single-user callers
```

This makes it possible for a single RPC to carry both an end-user's JWT identity and a
delegation-chain Subject, without any out-of-band negotiation.

![JERI multi-Subject dispatch: decorative illustration of multiple user identities arriving at a service endpoint](images/multi-subject-party-bus.svg)

---

## SettleTransactionPermission: Quorum Authorization at Commit Time

The multi-Subject model extends to distributed transactions via `SettleTransactionPermission`.

At **join time**, `TxnManagerImpl` captures the `Subject[]` for each participating endpoint — the
full set of user Subjects that were present when each participant joined the transaction.

At **commit or abort time**, `checkAllParticipantsPermission` checks that every captured subject
set holds the required `SettleTransactionPermission`.

### Worked Example: Dual-Authorization Financial Transfer

Policy:

```
grant principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org"
      principal net.jini.security.jwt.JwtPrincipal "sub:bob@example.org" {
    permission net.jini.core.transaction.SettleTransactionPermission "transfer";
};
```

Flow:
1. Alice initiates the transfer. Her `UserSubject` (carrying `"sub:alice@example.org"`) is present
   when the order-service joins the transaction. `TxnManagerImpl` records `[alice]` for that
   endpoint.
2. Bob approves. His `UserSubject` (carrying `"sub:bob@example.org"`) is added to the call via
   the multi-Subject wire protocol. Both Subjects are present when the approval-service joins.
   `TxnManagerImpl` records `[alice, bob]` for that endpoint.
3. At commit, `checkAllParticipantsPermission` verifies that every captured subject set satisfies
   the policy. The grant above requires both `alice` and `bob` to be present. If the approval
   endpoint's Subject set does not include both, the commit is denied.

This ensures that a transaction cannot be settled by a subset of the parties that initiated it,
and that policy-mandated quorums are enforced at the transaction boundary — not just at the initial
RPC — with no out-of-band session state.

---

## SubjectAwareExecutor: Propagating Identity Across Thread Boundaries

When work is handed off to a thread pool, the `Subject` context established by `callAs` does not
automatically cross the thread boundary. `SubjectAwareExecutor` (in `net.jini.security`) solves
this.

It wraps any `ExecutorService`. At **task submission** time it captures:
- The current `Subject[]` (via `Subject.currentAll()` on DirtyChai, or `Subject.current()` on a
  standard JDK wrapped in a one-element array)
- The current `AccessControlContext` (via `Security.getContext()`)

On the **worker thread** it restores them via `AccessController.doPrivileged` + `Subject.callAs`
(using the DirtyChai varargs overload when available, single-subject otherwise). SPIFFE
`WorkerSubject` propagation happens automatically through the restored `AccessControlContext` —
it does not need to be explicitly captured or restored.

This ensures that authorization decisions made on worker threads are evaluated against the same
identity context that was present when the task was submitted, not against whatever identity
happens to be ambient on the pool thread.

---

## Series Index

| # | Title | Depends on |
|---|---|---|
| 1 | [Overview: why fork both Apache River and OpenJDK?](blog-post-1-why-fork.md) | — |
| 2 | [SCAP: auditing JARs before they are loaded](blog-post-2-scap.md) | — (standalone) |
| 3a | [The identity model: three principals on every dispatch thread](blog-post-3a-identity-model.md) | 1 |
| **3b** | **Multi-Subject dispatch and distributed transaction authorization** *(this post)* | 3a |
| 4 | [Authorization without a single point of trust](blog-post-4-authorization.md) | 2, 3a |
| 5 | [Virtual threads, lock-free policy, and why security does not have to be slow](blog-post-5-performance.md) | 1–4 |

---

*GitHub repositories:*
- *JGDMS: <https://github.com/pfirmstone/JGDMS>*
- *DirtyChai: <https://github.com/pfirmstone/DirtyChai>*
