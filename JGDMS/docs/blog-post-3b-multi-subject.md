# Part 3b — Multi-Subject Dispatch and Distributed Transaction Authorization

*This is the third post (part b) in a six-part series on JGDMS and DirtyChai.
Prerequisite: [Part 3a](blog-post-3a-identity-model.md).*
*The series index is at the bottom of this post.*

---

Part 3a described the `WorkerSubject` (the ambient process identity baked into every
`ProtectionDomain`) and the remote process identity (the caller's *reducing* ACC — codebases only,
no principals on the wire; the peer's worker principals are stamped from the authenticated mTLS
connection at the receiver). This post covers the third layer: `UserSubject` — the human user's
JWT/OIDC identity — and the wire protocol that transmits up to 16 such Subjects per call.

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

`Subject.current()` returns only the first Subject bound via `callAs`; it reads the
`SCOPED_SUBJECT` `ScopedValue` directly. There is no user Subject stored *in* the
`AccessControlContext` to fall back to — `getSubject(acc)` is a deprecated shim that ignores its
`acc` and just returns `current()`. This lets the server always distinguish TLS-verified machine
identity from wire-asserted human identity.

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
    permission net.jini.core.transaction.SettleTransactionPermission "commit";
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

### `sudo`, done right: one person, two roles

The example above is two *different* people (four-eyes / dual control). The same machinery also
covers the everyday case of *one* person holding two roles. You log in as your ordinary user account,
and only when you need to do something privileged do you authenticate *again* as your administrator
role — Unix `sudo`. Multi-Subject `callAs` expresses it directly:

```java
Subject.callAs(() -> performAdminOp(), aliceUser, aliceAdminRole);
```

Both subjects are present for the duration of the call, and that buys two things Unix `root` does not:

- **Your identity is never lost.** `sudo` makes the process *become* root — the kernel sees uid 0,
  and your real identity survives only in a log. Here you *add* the admin role on top of your user
  identity; both principals are in the authorization context, so the action is attributable to
  *Alice acting as admin* structurally, not by correlating logs.
- **Admin authority is gated, and can be bound to the person.** Because a grant fires only when *all*
  its principals are present, you can write `grant principal "Alice", principal "AdminRole" { … }` —
  admin authority only Alice, in her admin role, can wield — or `grant principal "AdminRole" { … }`
  for any admin while still carrying Alice's identity for accountability. The elevation is *scoped*:
  Alice keeps only her ordinary authority everywhere the policy doesn't ask for the admin principal,
  not blanket root.

This is also why the *varargs* overload matters rather than being mere convenience: you cannot get
co-presence by nesting single-Subject calls. `callAs(aliceUser, () -> callAs(aliceAdminRole, …))`
*replaces* Alice with the admin role inside, so you would lose exactly the user identity you wanted to
keep. Binding both at once is the only correct way to hold them together — and it is why the stock
JDK, with only single-Subject `callAs`, cannot express this at all.

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
(using the DirtyChai varargs overload when available, single-subject otherwise). The SPIFFE
`WorkerSubject` does not need to be captured or restored at all — it is **ambient on every
thread**, stamped into `ProtectionDomain`s at class-load time (and reconstructed from the TLS
peer chain for a remote caller), so it is structurally present rather than carried on the
restored `AccessControlContext`.

This ensures that authorization decisions made on worker threads are evaluated against the same
identity context that was present when the task was submitted, not against whatever identity
happens to be ambient on the pool thread.

## Why `callAs`, not `doAs`

You may have noticed every example here uses `Subject.callAs(...)`, never the older
`Subject.doAs(...)`. That is deliberate — DirtyChai is deprecating `doAs`/`doAsPrivileged`.

The reason is that two things classic JAAS fused are actually orthogonal:

- **Who you are** — identity. Bound with `callAs`, it rides a `ScopedValue`, so it **survives
  `doPrivileged`**.
- **What privileges the code runs with** — the boundary. That is `AccessController.doPrivileged(...)`,
  which truncates the call stack.

These are already handled by two separate, correct mechanisms. A remote caller's `WorkerSubject`
rides the dispatch frames on the call stack, so a `doPrivileged` correctly **sheds** it — the server
can perform a privileged action without the remote caller's authority gating it — while the user
identity, on the `ScopedValue`, stays in effect. That is exactly what you want.

`Subject.doAs(...)` couples the two: it binds an identity *and* installs a `doPrivileged` boundary in
one call. So if you reach for `doAs` merely to *run privileged*, you also **drop the user** — `doAs`
rebinds the subject and suppresses the enclosing one, and the identity that should have survived is
silently gone. Dropping the user should be a deliberate choice — `callAs` with no user subject, i.e.
run as the process only — never a side effect of wanting a code boundary.

So the rule is simple: `callAs` for *who*, `doPrivileged` for the *boundary*, composed explicitly
when you need both. `doAs`/`doAsPrivileged` remain only for legacy JAAS and Kerberos GSS interop, and
are on their way out.

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
