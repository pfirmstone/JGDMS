# Part 3a — The Identity Model: Three Principals on Every Dispatch Thread

*This is the third post (part a) in a six-part series on JGDMS and DirtyChai.
Recommended prerequisite: [Part 1](blog-post-1-why-fork.md).*
*Part 3b continues with multi-Subject dispatch and distributed transactions.*
*The series index is at the bottom of this post.*

---

Most web frameworks have one identity per request. A servlet container binds a `Principal` to a
thread at the start of a request and clears it at the end. A gRPC server reads a credential from
the channel metadata and exposes it to interceptors. The model is simple: one caller, one identity,
one authorization check.

JGDMS — running on DirtyChai — has three simultaneously active identities on every dispatch thread.
Each has a different carrier, a different lifetime, and a different revocation path. Understanding
this model is the key to understanding how authorization in JGDMS actually works.

---

## SPIFFE/SPIRE: Zero-Touch Certificate Management

Before diving into the identity model itself, it is worth understanding how *process* identities
are issued and managed, because that foundation shapes everything above it.

In a fleet of services, long-lived keystores are a management and security liability. JGDMS and
DirtyChai integrate [SPIFFE](https://spiffe.io/) workload identity via SPIRE. Each host process and
client JVM receives a short-lived (~1 hour) X.509 SVID (SPIFFE Verifiable Identity Document) from
a local SPIRE agent.

There are in fact **two** `SpiffeCredentialManager`s, at different tiers of the stack:

- **DirtyChai** (`au.zeus.jdk.authorization.spire.SpiffeCredentialManager`, in `java.base`) is the
  zero-keystore one. Its singleton opens the SPIRE Workload API socket on startup, holds an
  in-memory `SpiffeSubject` with the current X.509 certificate and private key (no filesystem
  keystore), rotates on the SPIRE watcher callback as the SVID nears expiry, and **refreshes policy
  on each rotation** via its registered `SvidRotationListener`s.
- **JGDMS** (`net.jini.jeri.ssl.SpiffeCredentialManager`, in `jgdms-jeri`) is the JERI/TLS-facing
  one. By default it reads PEM credentials from disk (the Workload API socket is opt-in via a custom
  `SvidSource`), rotates on a scheduled lead-time before expiry, and registers the local SPIFFE
  principals as a `LocalPrincipalProvider` (see below). It does not itself refresh policy.

With the DirtyChai manager there is no `keytool`, no PKCS#12 files, no manual certificate renewal:
each service's identity is managed by the SPIRE control plane — revocation and rotation happen
without JVM restarts. SPIFFE IDs are human-readable:
`spiffe://jgdms.example.org/host/bae/engine-2` immediately tells an operator which component this
identity belongs to.

---

## The Sealed Subject Hierarchy

DirtyChai introduces a sealed `Subject` hierarchy with three distinct identity layers:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DirtyChai Subject Hierarchy                      │
│                                                                     │
│  Subject (vanilla, legacy)                                          │
│   ├── WorkerSubject  (sealed, permits SpiffeSubject, RemoteSubject) │
│   │     • Process workload identity (SPIFFE SVID)                   │
│   │     • Baked into every ProtectionDomain at class-load time      │
│   │     • AMBIENT — survives all doPrivileged boundaries            │
│   │     • Passing it to callAs()/doAs() is rejected (throws)        │
│   │                                                                 │
│   └── UserSubject  (final)                                          │
│         • Human user identity (JWT/OIDC)                            │
│         • Carried in SCOPED_SUBJECT ScopedValue<Subject[]>          │
│         • Installed per-request via Subject.callAs(...)             │
│         • Injected into ProtectionDomain array by AccessController  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│               Three Identity Layers on a Dispatch Thread            │
│                                                                     │
│  Layer 1 — Process Worker  (WorkerSubject, ambient)                 │
│    RFC3986URLClassLoader / PreferredClassLoader inject the          │
│    server's Principal[]; DirtyChai SecureClassLoader injects the    │
│    client's Principal[] + DigestCodeSource at class-load time.      │
│    Always present at every checkPermission. Never reinstalled.      │
│                                                                     │
│  Layer 2 — Remote Process  (reducing codebase set; no principals)   │
│    The remote caller's reducing ProtectionDomain set — codebases    │
│    ONLY, no principals — is sent over JERI by RemoteContextCodec.   │
│    Worker principals are stamped at the receiver from the verified  │
│    mTLS RemoteSubject, never the wire. Shed at doPrivileged.        │
│                                                                     │
│  Layer 3 — User  (UserSubject via callAs)                           │
│    Per-request human identity bound by JERI dispatcher via          │
│    Subject.callAs(userSubject, () -> invoke(...)).                  │
│    AccessController.getContext() bakes user principals directly     │
│    into the ProtectionDomain array. Survives doPrivileged.          │
└─────────────────────────────────────────────────────────────────────┘
```

![The Thread Identity Stack: decorative illustration of three simultaneous principals on every dispatch thread as a layered cake](images/three-layer-thread-stack.svg)

---

## Layer 1 — WorkerSubject: The Ambient Process Identity

`WorkerSubject` represents the JVM process itself. With SPIFFE/SPIRE, DirtyChai's
`SpiffeCredentialManager` constructs a `SpiffeSubject` — a `final` leaf class nested inside the
manager, extending the sealed `WorkerSubject` (which `permits SpiffeSubject, RemoteSubject`) — and
bakes it into every `ProtectionDomain` at class-load time. This Subject contains:

- A `SpiffePrincipal` derived from the SVID URI SAN (e.g.
  `spiffe://jgdms.example.org/host/bae/engine-2`)
- An `X500Principal` derived from the certificate Subject DN
- The short-lived X.509 credential (certificate chain + private key) — never written to disk

The key property of `WorkerSubject` is that it is **ambient**: present in every `ProtectionDomain`
regardless of `doPrivileged` nesting. A servlet container's `Principal` would be cleared or
shadowed by a `doPrivileged` call — the ambient identity would be gone for the duration of that
block. The `WorkerSubject` is not installed via `callAs` or `doAs`; it is injected at class-load
time and is therefore visible at every `AccessController.checkPermission()` call throughout the
life of the JVM. The server never needs to reinstall it per request.

This distinction matters for authorization: a policy grant conditioned on a `SpiffePrincipal`
cannot be bypassed by wrapping a block in `doPrivileged`. The workload identity is structural, not
contextual.

---

## Layer 2 — Remote Process Identity

This layer used to carry the remote caller's `WorkerSubject` *principals* over the wire inside a
serialized `AccessControlContext`. It no longer does — and that change is the point. **Workload
principals are never transmitted.** They are authenticated by the mutually-authenticated TLS
connection itself (the peer's SVID), so asserting them again on the wire would be both redundant and
a trust hole. What travels is only the caller's *reducing* codebase set; the principals are stamped
back on at the receiver from the verified connection.

`RemoteContextCodec` (`net.jini.jeri`) is the shipping implementation — `marshal` on the client
(`BasicInvocationHandler`), `unmarshal` on the server (`BasicInvocationDispatcher`). It is the
*subtractive* half of the two-gate workload model: it transmits the caller's reducing
`ProtectionDomain` set as **codebase identities only, no principals**:

- **`DigestCodeSource` domains** are written with their codebase URI, digest algorithm, digest
  bytes, and signing certificates — a self-describing, content-pinned identity. (The digest is
  re-verified later, at policy / class-load time via `DigestGrant`, not inside the codec.)
- **Other located domains** are written as a codebase URL plus any signing certificates.
- **Null-`CodeSource` domains** (a dynamic proxy, lambda, or bootstrap frame) are transmitted too —
  they are genuine *reducers*, and dropping one would silently *elevate* authority. Each is
  reconstructed codebase-less and principal-bearing, so it reduces to whatever the worker principals
  are granted (a principal-only grant) and never confers codebase-scoped authority.
- **The one domain that is dropped** — and refused if a peer sends it — is the platform
  `jrt:/java.base` module. Unlike the others it is *not* a reducer: it always holds `AllPermission`,
  carries native code, and is unstamped (no content digest), so reconstructing it with the remote
  principals would inherit the receiver's unconditional platform grant — an escalation, not a
  reduction. Other `jrt:` modules are kept. (This exclusion was missing from the codec until
  recently — a genuine privilege-escalation bug, now fixed and enforced on both the encoder and the
  decoder.)

Every reducing domain *except* `jrt:/java.base` travels inline; there is no verifiable-versus-anonymous
split, no `anonCount`, and no anonymous placeholder domains. On receipt each domain is rebuilt as a `ProtectionDomain` with
its real `CodeSource`, **no static permissions** (so the server's policy alone decides what it
grants), and the principals of the authenticated `RemoteSubject` stamped on. The whole payload is a
single length-bounded block decoded in its own isolated codec stream — it cannot share decode state,
a handle table, or a DoS budget with the application arguments — under fixed ceilings (≤ 4096
domains, ≤ 100 certs/domain, ≤ 512-byte digests, ≤ 64 KiB/cert).

These reconstructed domains are shed at `doPrivileged` boundaries — they represent the *caller's*
context, not the server's. No session state, no thread-local leakage between calls, no boilerplate
in service code.

This is the *code/privilege boundary* axis, and DirtyChai keeps it deliberately separate from *who*
the call is for: the user identity (Part 3b) rides a `ScopedValue` and **survives** `doPrivileged`,
whereas these caller domains are shed by it. That separation is exactly why DirtyChai is deprecating
the old `Subject.doAs(...)` (steered to `callAs`, though not yet marked for removal), which fused
identity with the boundary in a single call — `callAs` handles *who*, `doPrivileged` handles the
*boundary*.

Note the contrast with the **user** identity covered in Part 3b. A human `UserSubject`'s JWT *is* a
bearer token, so it travels on the wire and is independently verified at the receiver (the `0x02`
multi-Subject block; each raw JWT checked by a `JwtVerifier`, with expiry enforced even when no
custom verifier is registered). The channel authenticates the *worker*, not the *user* — which is
precisely why workload principals can be stamped from the connection while user tokens must be
carried and re-verified at each hop. Parts 3b and 4 pick this up.

---

## ProtectionDomain Principal Injection

JGDMS and DirtyChai have two complementary injection paths that combine to populate every proxy `ProtectionDomain`
with the full trust context:

| Class | What it injects | How |
|---|---|---|
| JGDMS `RFC3986URLClassLoader` (5-arg) / `PreferredClassLoader` (7-arg) | Server's `Principal[]` | `final` field passed at construction; injected into each `ProtectionDomain` at `defineClass` time |
| DirtyChai `SecureClassLoader` | Client's process `Principal[]` + `DigestCodeSource` (codebase SHA-256) | Called by the JDK's class-loading machinery at class-load time |

The combined result is a `ProtectionDomain` that carries both sides of the call and the codebase
content hash, so a policy grant can simultaneously scope on the server's SPIFFE workload, the
client's SPIFFE workload, and the exact JAR content — no single axis alone is sufficient.

---

## BootstrapPermission and LocalPrincipalProvider

During the boot window — before the `VerdictRegistry` is reachable (concretely, while
`VerdictRegistryHolder.get()` still returns `null`) — codebase loading is gated by
`BootstrapPermission` (`net.jini.loader.pref`, target `"loadCodebase"`). Only callers whose
`ProtectionDomain` holds this permission (i.e. code already trusted by the bootstrap policy) can
trigger a SPIFFE-principal-based codebase load before the registry is online.

`LocalPrincipalProvider` is a SPI in `org.apache.river.api.security` that bridges `jgdms-platform`
and `jgdms-jeri`. The JGDMS `SpiffeCredentialManager.start()` registers the managed `Subject`'s
principals via `net.jini.security.Security.registerLocalPrincipalProvider()`. When
`Security.currentPrincipals()` is called outside a `callAs` scope (e.g. during bootstrapping or on a
plain thread) with no worker `Subject` active, it falls back to the registered provider to retrieve
the local SPIFFE principals rather than reporting none.

---

## What Part 3b Covers

Part 3a has described the first two layers of identity (the process `WorkerSubject`, and the remote
caller's reducing codebase set with the authenticated worker principals stamped on at the receiver).
The third layer — `UserSubject` carrying a human user's
JWT/OIDC identity, the wire protocol that transmits up to 16 such Subjects per call, and how
`SettleTransactionPermission` extends this model to distributed transactions — is covered in
[Part 3b](blog-post-3b-multi-subject.md).

---

## Series Index

| # | Title | Depends on |
|---|---|---|
| 1 | [Overview: why fork both Apache River and OpenJDK?](blog-post-1-why-fork.md) | — |
| 2 | [SCAP: auditing JARs before they are loaded](blog-post-2-scap.md) | — (standalone) |
| **3a** | **The identity model: three principals on every dispatch thread** *(this post)* | 1 |
| 3b | [Multi-Subject dispatch and distributed transaction authorization](blog-post-3b-multi-subject.md) | 3a |
| 4 | [Authorization without a single point of trust](blog-post-4-authorization.md) | 2, 3a |
| 5 | [Virtual threads, lock-free policy, and why security does not have to be slow](blog-post-5-performance.md) | 1–4 |

---

*GitHub repositories:*
- *JGDMS: <https://github.com/pfirmstone/JGDMS>*
- *DirtyChai: <https://github.com/pfirmstone/DirtyChai>*
