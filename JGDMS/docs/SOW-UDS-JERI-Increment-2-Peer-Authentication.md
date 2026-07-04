# SOW: Unix Domain Socket JERI Transport — Increment 2: Per-Endpoint Peer Authentication

Status: **design / captured 2026-07-03**. Companion to
`docs/SOW-Unix-Domain-Socket-JERI-Transport.md` (increment 1). JGDMS-side, committable, no source
changed by this document. The design below is **decided** (Peter); this SOW specifies it precisely
against the actual JERI seams — it does not re-open the design.

Decisions carried in as settled:
- The handshake belongs at **connection establishment** (in the UDS `Connection` / `ServerConnection`),
  it **populates the verified peer `Subject` into the invocation context**, and it **upgrades the
  transport's constraint support to the authentication-`YES` variants**.
- Identity is **two-factor**: `SO_PEERCRED` (kernel-vouched `{uid,gid,pid}`) **cross-validated against
  a SPIFFE SVID presented at connection open**.
- **`SO_PEERCRED` is deferred and platform-specific** (Linux-only, needs Panama FFI or a DirtyChai JDK
  API). The platform-agnostic baseline (no `SO_PEERCRED`) falls back to socket-file permissions
  (owner-only `0600`) as the trust boundary. DirtyChai is the SM-capable JDK JGDMS runs on.
- **No crypto proof-of-possession, no `SSLEngine`.** The kernel binding replaces a PoP challenge (see §4).
- **Two-gate model** (Peter's ruling): the process SPIFFE *workload* identity is sufficient for the
  *connection*; JWT *user* subjects are transmitted and verified separately at the JERI application
  layer by `RemoteContextCodec`. Increment 2 is about the *connection/workload* gate only.

---

## 1. Motivation and relation to increment 1

Increment 1 (`SOW-Unix-Domain-Socket-JERI-Transport.md`, and the plaintext transport now in
`net.jini.jeri.uds`) delivered the **byte transport and the constraint claims that locality earns for
free**, but deliberately left peer identity out:

- **`Confidentiality.YES` by locality** — a local, kernel-mediated, owner-only (`0600`) socket is not
  visible to an off-host eavesdropper or an unprivileged local process, so the transport fully
  provides confidentiality. This is the one genuine transport upgrade over TCP
  (`net/jini/jeri/uds/Constraints.java:150-159`, `supportedValues.put(Confidentiality.YES, Boolean.FALSE)`
  where `FALSE` means FULL_SUPPORT).
- **`Integrity.YES` owned by the object layer, NOT claimed by the transport** — the atomic/DER codec
  (`AtomicILFactory` + `net.jini.io.context.IntegrityEnforcement`) achieves full stream/codebase
  integrity over plaintext; the transport deliberately declines to claim it so it cannot signal the
  object layer to bypass the DER/JOSS gate (`net/jini/jeri/uds/Constraints.java:73-85, 136-149`).
- **No peer principal.** With `SO_PEERCRED` deferred, increment 1 surfaces *no authenticated peer
  identity* inside the JERI constraint model, so it supports only the `.NO` auth variants exactly like
  TCP (`net/jini/jeri/uds/Constraints.java:87-94, 161-167`). Local peer trust is provided out-of-band
  by the socket file's filesystem permissions, which is defence-in-depth, not an identity.

The two connection-setup seams are already present in the tree as **clean no-ops**, explicitly
reserved for this increment:

- Client presents identity: `UdsEndpoint.ConnectionImpl.writeRequestData(...)`
  (`net/jini/jeri/uds/UdsEndpoint.java:549-555`) — today a no-op, as for plaintext TCP.
- Server verifies identity: `UdsServerEndpoint.LH.ServerConnectionImpl.processRequestData(...)`
  (`net/jini/jeri/uds/UdsServerEndpoint.java:579-583`) — today returns a bare `InboundRequestHandle`.
- Server injects the verified `Subject`:
  `UdsServerEndpoint.LH.ServerConnectionImpl.populateContext(...)`
  (`net/jini/jeri/uds/UdsServerEndpoint.java:627-634`) — today a no-op, and the source already carries
  the target line as a comment: `// Next increment: Util.populateContext(context, verifiedWorkerSubject);`
  (`UdsServerEndpoint.java:633`).

**Increment 1 answered "is this channel private?" (yes, by locality). Increment 2 answers "who is the
peer?"** — it turns the reserved no-op seams into a real, kernel-anchored workload authentication and
promotes the transport's constraint support to the auth-`YES` variants.

---

## 2. Goals / non-goals

- **Goal**: a per-endpoint, connection-establishment peer-authentication handshake that (a)
  cross-validates a kernel `SO_PEERCRED` `{uid,gid,pid}` against a SPIFFE SVID presented at open, (b)
  populates the verified peer `Subject` (carrying `SpiffePrincipal` + a kernel `{uid,gid}` principal)
  into the per-invocation context, and (c) upgrades `net.jini.jeri.uds.Constraints` to
  `ClientAuthentication.YES` / `ServerAuthentication.YES` plus the principal-constraint classes.
- **Goal**: keep the object-layer integrity ownership and locality-confidentiality of increment 1
  unchanged; add identity, do not disturb the rest of the constraint model.
- **Non-goal (unchanged from increment 1, stays as-is)**: JWT *user*-subject transmission and
  verification. That is the JERI application layer's job via `RemoteContextCodec` and rides this UDS
  connection unchanged; it is **not** re-implemented here (§7).
- **Non-goal (explicitly not needed)**: `SSLEngine` / a TLS-like crypto handshake over UDS. The kernel
  peer-cred binding replaces the proof-of-possession a TLS handshake would provide (§4). This is the
  crux of why increment 2 is small.
- **Non-goal**: off-host reachability. UDS is host-local; a peer-cred is only meaningful for a
  same-host peer.

---

## 3. Design, against the code seams

### 3(a) Where the handshake runs — `Connection` / `ServerConnection` at connection open

The Jini ERI connection contract already provides the exact hooks; increment 1 wired them as no-ops.
Increment 2 fills them in. **No new method surface, no new interface** — the handshake is data
exchanged on the connection streams before the mux carries invocation traffic:

| Role | Method | File:line (current no-op) | Increment-2 behaviour |
|---|---|---|---|
| Client presents SVID | `Connection.writeRequestData(handle, out)` | `UdsEndpoint.java:549-555` | Read the process's ambient workload SVID (the same source SSL uses — see §3b) and write it on the connection stream at open. |
| Server reads + verifies | `ServerConnection.processRequestData(in, out)` | `UdsServerEndpoint.java:579-583` | Read the client SVID, read `SO_PEERCRED` from the accepted channel, cross-validate (§3b), and stash the resulting verified worker `Subject` on the `ServerConnectionImpl` (as SSL stashes `clientSubject` — see `SslServerEndpointImpl.java:1474, 1514`). Return an `InboundRequestHandle` that carries/keys that subject. |
| Server injects subject | `ServerConnection.populateContext(handle, context)` | `UdsServerEndpoint.java:627-634` | Call `Util.populateContext(context, verifiedWorkerSubject)` — the line already stubbed in the source. |

**Symmetry note (server authentication of itself to the client).** `SO_PEERCRED` is symmetric — the
kernel vouches for *both* ends of an `AF_UNIX` stream — so the client's `readResponseData(...)`
(`UdsEndpoint.java:557-564`) can equally read the server's peer-cred and SVID and cross-validate, which
is what lets the transport honestly claim `ServerAuthentication.YES` (§3d), not just client auth. The
client-side context injection of the *server* `Subject` (for `ServerMinPrincipal` scoping and
codebase-grant scoping) mirrors `SslConnection.populateContext(...)`
(`SslConnection.java:496-547`), which adds a `ServerSubject` context element carrying the server's
`X500Principal` + `SpiffePrincipal` set.

**Why connection-open, not per-request.** A UDS connection is a single kernel-mediated pipe between two
fixed processes; the peer-cred and the SVID do not change over the life of the connection, so the
handshake is done once at establishment and the verified `Subject` is bound to the connection object —
exactly the SSL shape (`clientSubject` is a per-`SslServerConnection` field, established during the TLS
handshake at `SslServerEndpointImpl.java:1514-1515`, then replayed on every `populateContext`).

### 3(b) The two-factor identity — `SO_PEERCRED` attests the process, SVID names the workload, cross-validation binds them

Two independent facts are combined:

1. **`SO_PEERCRED` — kernel-vouched `{uid,gid,pid}`.** On Linux, `getsockopt(fd, SOL_SOCKET,
   SO_PEERCRED, …)` returns the credentials of the peer process *as the kernel knows them*, unforgeable
   from user space. There is no Java API for this; it requires **Panama FFI** (`getsockopt` via a
   downcall handle) or a **DirtyChai JDK API**. This factor attests *which OS process/uid* is on the
   other end.
2. **SPIFFE SVID presented at open.** The peer writes its X.509 SVID (SPIFFE Verifiable Identity
   Document) on the connection stream in `writeRequestData`. The server extracts the SPIFFE ID(s) via
   the **existing** `SpiffePrincipal.fromCertificate(X509Certificate)` →
   `List<SpiffePrincipal>` (`net/jini/jeri/ssl/SpiffePrincipal.java:154-179`), which reads the
   `spiffe://` URI SubjectAlternativeNames. This factor names *which workload identity* the peer claims.

**Cross-validation is the whole point.** A bare SVID over plaintext proves nothing (anyone who can read
a public SVID can present it — see §5). What makes it sound is binding it to the kernel fact:
`processRequestData` cross-validates the presented SVID against **the SPIRE registration keyed by the
`SO_PEERCRED` `{uid,pid}`** — i.e. "does SPIRE agree that a process running as *this uid/pid* is
entitled to *this SPIFFE ID*?" This is precisely how SPIRE's own workload attestation binds a process
to its SVID (the `unix` workload attestor keys on uid/gid/path; SPIRE hands the SVID to the workload
over its own UDS Workload API after attesting the caller). **The kernel binding replaces a
cryptographic proof-of-possession**: with a normal TLS handshake, possession of the private key proves
the presenter *is* the SVID's subject; here, the kernel's uid/pid + the SPIRE registration prove the
same thing without a crypto challenge, because the presenter cannot lie to the kernel about its uid/pid.
**This is why UDS peer-auth needs no TLS-like handshake and stays off `SSLEngine`** — the crux of the
whole increment.

The resulting verified peer `Subject` carries **both** identities as principals:
- one or more `SpiffePrincipal` (the workload identity), and
- a kernel-credential principal carrying `{uid,gid}` (a new small `Principal` type, e.g.
  `UnixPeerCredPrincipal`, in `net.jini.jeri.uds` — see §3c for why it cannot ride the existing
  `ClientHost` element).

Authorization downstream is against these principals (`BasicInvocationDispatcher.checkClientPermission`
builds a `ProtectionDomain` from `clientSubject.getPrincipals()` —
`BasicInvocationDispatcher.java:1579-1583`), so both the SPIFFE identity and the uid/gid become
policy-addressable.

### 3(c) Injecting the verified peer `Subject` into the invocation context — the exact confirmed seam

This is the load-bearing seam; it was verified end-to-end in the source, and it matches the design
assumption. The chain is:

```
UdsServerEndpoint.ServerConnectionImpl.populateContext(handle, context)   [UdsServerEndpoint.java:627]
   -> Util.populateContext(context, verifiedWorkerSubject)                [Util.java:738-742]
        -> context.add(new ClientSubjectImpl(subject))                    [Util.java:742, 761-773]
BasicInvocationDispatcher (server dispatch) reads it:
   -> Util.getClientSubject()                                             [Util.java:815-818]
        -> ServerContext.getServerContextElement(ClientSubject.class)     [Util.java:816-817]
             .getClientSubject()                                          [ClientSubject.java:53]
   consumed by checkClientPermission(...)                                 [BasicInvocationDispatcher.java:1551-1584]
   and invokeWithClientSubject(...)                                       [BasicInvocationDispatcher.java:1149, 2107-2114]
```

- `Util` is `org.apache.river.jeri.internal.runtime.Util`
  (`jgdms-jeri/src/main/java/org/apache/river/jeri/internal/runtime/Util.java`). The overload
  `populateContext(Collection context, Subject s)` (line 738-742) wraps the subject in a
  private `ClientSubjectImpl` (line 761-773) that implements
  `net.jini.io.context.ClientSubject` and guards `getClientSubject()` with
  `ContextPermission("net.jini.io.context.ClientSubject.getClientSubject")` (line 765-772).
- `net.jini.io.context.ClientSubject` (`jgdms-platform/.../ClientSubject.java:31-54`) is the interface
  `BasicInvocationDispatcher` looks up via `ServerContext.getServerContextElement(...)`.
- **SSL uses exactly this call** on its server side:
  `SslServerEndpointImpl.SslServerConnection.populateContext(...)` calls
  `Util.populateContext(context, clientSubject)` at `SslServerEndpointImpl.java:1770` (having also
  populated `ClientHost` from the socket's `InetAddress` at line 1769). **The UDS design mirrors this
  one-for-one**, minus the `InetAddress` line (next paragraph).

> **Seam correction / difference to flag — `ClientHost` cannot carry the UDS peer.** The prompt's
> assumed design cited "how SSL populates `ClientSubject`/`ClientHost` into the context". The
> `ClientSubject` half is exactly right and is the injection point for the peer `Subject`. But the
> `ClientHost` half **does not carry over to UDS**: `net.jini.io.context.ClientHost.getClientHost()`
> returns a `java.net.InetAddress` (`jgdms-platform/.../ClientHost.java:38`), and a UDS peer has **no
> `InetAddress`** (increment-1 SOW §4: `udsChannel.socket()` throws, there is no `InetAddress`). SSL
> gets its value from `sslSocket.getInetAddress()` (`SslServerEndpointImpl.java:1769`), which has no
> UDS analogue. **Therefore the kernel `{uid,gid,pid}` peer-cred must NOT be shoehorned into
> `ClientHost`.** It is carried as a principal inside the injected `ClientSubject` (§3b), and, if a
> caller ever needs the raw peer-cred as a distinct context element (parallel to how `ClientHost`
> exposes the network address), that is a *new* `net.jini.io.context`-style element
> (e.g. `ClientCredentials` returning `{uid,gid,pid}`), not a reuse of `ClientHost`. The minimal
> increment adds only the principal-in-Subject path; the standalone context element is optional and
> called out in §8.

### 3(d) The `Constraints` upgrade — auth-`YES` + principal-constraint support

Increment-1 `net.jini.jeri.uds.Constraints` is modelled on `net.jini.jeri.tcp.Constraints`: a **static**
`supportedValues` / `supportedClasses` map with two simplifying assumptions ("all supported constraints
are always satisfied by all connections; no supported constraints conflict",
`Constraints.java:52-57`). Today it registers only the `.NO` auth variants and treats the principal
classes as trivially-supported-because-YES-is-unsupported (`Constraints.java:161-192`).

Increment 2 adds authenticated-peer support. The concrete edits to
`net/jini/jeri/uds/Constraints.java`:

**In the `supportedValues` static block (currently lines 135-168) — add the `YES` variants
(FULL_SUPPORT = `Boolean.FALSE`):**

```java
supportedValues.put(ClientAuthentication.YES,   Boolean.FALSE);
supportedValues.put(ServerAuthentication.YES,   Boolean.FALSE);
// Delegation stays .NO only: a UDS peer-cred/SVID does not carry delegation
// credentials, so Delegation.YES is NOT added (see below).
```

Keep the existing `ClientAuthentication.NO` / `ServerAuthentication.NO` / `Delegation.NO` entries
(lines 165-167): the transport still *permits* an unauthenticated request when the constraints allow it,
and `Delegation.NO` remains the only delegation value it can satisfy.

**In the `supportedClasses` static block (currently lines 176-192) — the principal-constraint classes
must move from "trivially supported" to "value-aware supported".** The classes to register
(FULL_SUPPORT, and note `ClientMinPrincipalType` / a `clientMinPrincipalType` prototype are needed for
the type constraints, mirroring SSL — `ConnectionContext.java:69-71`,
`new ClientMinPrincipalType(SpiffePrincipal.class)` or the UDS peer-cred principal type):

```java
supportedClasses.put(ClientMinPrincipal.class,       Boolean.FALSE);
supportedClasses.put(ClientMinPrincipalType.class,   Boolean.FALSE);
supportedClasses.put(ClientMaxPrincipal.class,       Boolean.FALSE);
supportedClasses.put(ClientMaxPrincipalType.class,   Boolean.FALSE);
supportedClasses.put(ServerMinPrincipal.class,       Boolean.FALSE);
```

> **Design decision to flag — the static map is not sufficient for principal *values*.** In the
> increment-1 (TCP-style) model these classes are listed *only because* `…Authentication.YES` is
> unsupported, so any principal constraint is satisfied by "no peer principal ever" and the map never
> inspects the constraint's *value* (`Constraints.java:180-192`, and the getSupport lookup at
> `Constraints.java:198-205` keys on `c.getClass()`, ignoring which principal is demanded). Once the
> transport authenticates a *specific* peer, `ClientMinPrincipal("spiffe://…/reggie")` must be checked
> against the *actual* verified peer principal, which a class-keyed static map cannot do.
>
> **This is the one place increment 2 must grow the `Constraints` model beyond a static-map edit**, and
> the SSL transport is the template: SSL does **not** use the static map for auth — it uses a dynamic,
> per-connection `ConnectionContext.isSupported(...)` that inspects the constraint value against the
> connection's actual principals (`ConnectionContext.java:287-308`, e.g.
> `ClientAuthentication.YES` at 288, `ClientMinPrincipal` element-membership at 294-296,
> `ServerMinPrincipal` at 307-308). The UDS analogue is narrower than SSL's (no cipher-suite
> negotiation, a *single fixed* peer identity per connection, so the two increment-1 simplifying
> assumptions largely still hold), but the principal-constraint *check* must be evaluated against the
> connection's verified peer `Subject`, not answered statically. Practically: the `Constraints.distill`
> path stays static for the value constraints (auth-YES is now globally supportable), but the
> **server-side** `ServerConnectionImpl.checkConstraints(handle, constraints)`
> (`UdsServerEndpoint.java:602-614`) and the **client-side** connection-reuse/`getUnfulfilledConstraints`
> path must consult the verified peer principals for `ClientMinPrincipal` / `ServerMinPrincipal` /
> `Client{Min,Max}PrincipalType`. No claim of a purely-static solution is made here.

Confirmed baseline for the edit: **no existing static-map transport emits `ClientAuthentication.YES`** —
`tcp`, `http`, and increment-1 `uds` all only ever comment that YES is *un*supported
(`tcp/Constraints.java:109`, `http/Constraints.java:109`, `uds/Constraints.java:88, 182`); SSL is the
only transport that supports auth-YES and it does so via the dynamic `ConnectionContext` model, not the
static map. So increment 2 is the first static-map-family transport to carry auth-YES, and inherits
the value-awareness obligation above.

---

## 4. Why no crypto proof-of-possession / no `SSLEngine` (the crux)

Restating the settled decision precisely against the mechanism:

- A TLS handshake exists to (1) establish a confidential channel and (2) prove the peer *possesses the
  private key* bound to the presented certificate (proof-of-possession). Over UDS, **(1) is already
  provided by kernel locality** (increment 1's `Confidentiality.YES`), and **(2) is provided by the
  kernel's `SO_PEERCRED` + the SPIRE registration** instead of a key challenge: the peer cannot forge
  its uid/pid to the kernel, and SPIRE's registration says which uid/pid is entitled to which SVID, so
  the kernel fact + the registration together prove the presenter is the SVID's rightful holder.
- Therefore UDS peer-auth needs **no** `SSLEngine`, no `SSLSocket`, no cipher-suite negotiation, and no
  handshake state machine. This is what keeps increment 2 small and keeps the UDS transport off the
  `SSLSocket ↔ java.net.Socket` coupling that the increment-1 SOW §4 identified as the reason SSL
  cannot simply run over a UDS channel. The `SSLEngine` migration debated in increment-1 SOW §4(i) is
  **not required** for UDS peer-auth (it remains an independent, orthogonal item for the *TCP* SSL
  transport's virtual-threads story).
- **The dependency this trades for**: correctness now rests on the SPIRE registration being the source
  of truth for uid/pid → SVID, and on `SO_PEERCRED` being available. Both are Linux-and-DirtyChai
  concerns (§6), which is why the non-`SO_PEERCRED` fallback (§5) is materially weaker.

---

## 5. Security model and threat analysis

**What each factor buys:**

| Factor | Attests | Forgeable by a local unprivileged peer? |
|---|---|---|
| Socket-file perms (`0600`, increment 1) | *Some* process running as the owner uid may connect | No (kernel-enforced), but coarse: any process of that uid, no per-workload distinction |
| `SO_PEERCRED` `{uid,gid,pid}` | The exact OS identity of the connected process | No — kernel-vouched, unforgeable from user space |
| SPIFFE SVID at open | A claimed workload identity | The *bytes* yes (a public SVID is copyable); the *binding* to uid/pid, no — see cross-validation |
| Cross-validation (SVID ↔ SPIRE-by-uid/pid) | This uid/pid is entitled to this SVID | No — requires both lying to the kernel *and* a matching SPIRE registration |

**Honest caveats (stated, not hidden):**

1. **Without `SO_PEERCRED` (non-Linux / platform-agnostic baseline), a bare SVID over plaintext is
   replayable.** An SVID presented on the stream with no kernel binding proves only "the presenter has
   a copy of this SVID", which for a public document is nothing. **On the non-`SO_PEERCRED` path the
   transport MUST NOT claim `…Authentication.YES` from the SVID alone.** The fallback trust boundary
   reverts to increment 1: the socket file's `0600` owner-only permission — i.e. *same-user only*,
   authorized as "the owner uid", not as a SPIFFE workload. If a cross-uid authenticated peer is
   required on a platform without `SO_PEERCRED`, the honest options are (a) require a real
   proof-of-possession — i.e. TLS/`SSLEngine` over the UDS channel, the increment-1 §4(i) path, at
   which point you are paying for the crypto handshake the `SO_PEERCRED` path avoided — or (b) do not
   authenticate cross-uid and rely on socket-perms. This SOW's auth-`YES` claim (§3d) is therefore
   **conditional on the `SO_PEERCRED` factor being present**; the `Constraints` upgrade must be gated
   so that on a build/platform without peer-cred the transport advertises only the increment-1 `.NO`
   variants. (Implementation: the auth-YES entries are registered only when the peer-cred provider is
   available, or `checkConstraints` fails auth-YES requirements when no peer-cred was obtained.)

2. **PID-reuse hazard.** A `pid` is reused by the OS after a process exits; an authorization decision
   keyed on `pid` could, in a race, bind to a *different* later process. **Mitigation: authorize on
   `{uid,gid}` (stable identity), not `pid`.** The `pid` is used only transiently for the SPIRE lookup
   at attestation time, not stored as the authorization key. If `pid` must be used (e.g. to disambiguate
   two workloads under one uid), adopt SPIRE's own mitigation: pair the `pid` with the process
   **start-time** from `/proc/<pid>/stat` (field 22), so a reused pid with a different start-time is
   rejected. The verified `Subject` therefore carries `{uid,gid}` as its kernel principal, not `pid`.

3. **Abstract-namespace sockets have no filesystem permissions.** Linux abstract-namespace sockets
   (`@`-prefixed) are *not* filesystem objects and carry **no fs perms**, so the increment-1 `0600`
   fallback trust boundary evaporates for them. **Use pathname sockets only** (increment-1 SOW §6
   already forbids abstract-namespace paths; increment 2 restates it as a *security* requirement, not
   just a portability one, because the whole non-`SO_PEERCRED` fallback depends on the socket file's
   mode).

4. **Unlink/bind race (carried from increment 1 §8).** The listen path `deleteIfExists` → `bind` →
   `restrictPermissions` (`UdsServerEndpoint.java:239-241`) has a window in which the socket file exists
   before its mode is tightened; the caller must place it in a private, non-world-writable directory.
   Peer-auth does not remove this requirement. *(Increment-1 hardening note: the review closed this in
   the transport itself — bind-first with no pre-delete, owner-matched stale reclaim, fail-closed
   owner-only mode/ACL, and world-writable-non-sticky parent rejection — so the window is now covered by
   the private-parent requirement in code, not only by caller discipline.)*

5. **SVID freshness / revocation is a REQUIREMENT, not an open question (Open-Question 3).** The
   `SO_PEERCRED`×SVID cross-validation in `processRequestData` assumes a same-host SPIRE agent as the
   source of truth for "uid/pid ⊢ SVID". Whichever lookup form is chosen — an authoritative per-connection
   SPIRE Workload/Admin-API query, or a local registration/SVID cache validated against the signature
   chain — **the verification MUST carry an explicit freshness/revocation bound.** A stale cache silently
   authenticates a *revoked* workload: an SVID that SPIRE has since revoked (or a registration that has
   been removed) would still pass a cache-only check and mint a verified `Subject` for a peer that is no
   longer entitled to it. Therefore: (a) a cached uid/pid⊢SVID decision MUST be treated as valid only
   within a bounded freshness TTL and revalidated against the SPIRE agent (or a revocation feed) before
   expiry; (b) SVID expiry (`notAfter`) MUST be enforced at verify time; (c) on any inability to confirm
   freshness within the bound, `processRequestData` MUST fail closed (reject the connection), not fall
   back to a stale positive. The per-connection-vs-cached-SVID choice (Open-Question 3) is thus
   constrained: the cache is permissible only with this freshness/revocation contract; without it, the
   authoritative per-connection query is mandatory.

6. **Peer cred is read BEFORE any request data, and the connection dies with the peer.** The accepted
   fd's `SO_PEERCRED` and the presented SVID MUST be read and cross-validated in `processRequestData`
   **before any application request bytes are processed and before any `Subject` is populated into an
   invocation context** — the verified `Subject` gates the very first request, never trailing a request
   already dispatched. Correspondingly, the authenticated `Subject` is bound to the *connection*, not
   cached beyond it: if the peer process exits (the `AF_UNIX` stream is torn down, or a subsequent
   liveness check on the fd fails), the connection MUST be closed and its `Subject` discarded. A dead
   peer's authenticated `Subject` MUST NOT be reused for a later connection — combined with the §5.2
   PID-reuse mitigation (authorize on `{uid,gid}`, not `pid`; pair `pid` with process start-time when it
   must be used) this prevents a later, different process from inheriting the exited peer's authenticated
   identity.

**Net position.** On Linux+DirtyChai with `SO_PEERCRED`, increment 2 delivers genuine per-workload
authentication anchored in the kernel with no crypto handshake — strong and cheap. On every other
platform it delivers *same-user* trust via socket perms and must not overclaim; the constraint model is
gated accordingly.

---

## 6. Platform matrix

| Concern | Linux + `SO_PEERCRED` (target) | Platform-agnostic baseline |
|---|---|---|
| Kernel peer-cred `{uid,gid,pid}` | Yes — `getsockopt(SO_PEERCRED)` via Panama FFI **or** a DirtyChai JDK API | No |
| Cross-validation vs SPIRE | Yes (keyed on kernel uid/pid) | N/A |
| Auth-`YES` constraint claim (§3d) | Registered / honoured | **Not** registered — advertises `.NO` only |
| Trust boundary | Verified SPIFFE workload + `{uid,gid}` principal | Socket-file `0600` perms = same-uid only (increment 1) |
| Peer `Subject` in context | `SpiffePrincipal` + `{uid,gid}` principal | Empty / no `ClientSubject` populated (as increment 1) |
| Abstract-namespace sockets | Forbidden (no fs-perm fallback) | Forbidden |

**Dependency**: the `SO_PEERCRED` read is the single platform-specific primitive. It is delivered
either by a **Panama FFI** downcall to `getsockopt(2)` (Linux; the transport builds a `Linker`
downcall handle for `SO_PEERCRED` / `struct ucred`) **or** by a **DirtyChai JDK API** that exposes peer
credentials on a `SocketChannel`. DirtyChai is the SM-capable JDK JGDMS runs on and is the natural home
for a first-class `SocketChannel` peer-cred accessor; the Panama route keeps the transport
vanilla-JDK-buildable at the cost of a small native-descriptor extraction. Pick at build time; isolate
the primitive behind a small `UnixPeerCredentials` provider interface with a Linux/Panama
implementation and a no-op default, so the transport core stays platform-agnostic (mirroring how
increment 1 isolates POSIX perms behind `restrictPermissions` with a Windows no-op,
`UdsServerEndpoint.java:269-286`).

---

## 7. Out of scope (unchanged / explicitly excluded)

- **JWT *user*-subject verification stays as-is.** User identity travels over this same UDS connection
  via `net.jini.jeri.RemoteContextCodec` (`jgdms-jeri/.../RemoteContextCodec.java`), an **invocation-layer**
  helper (package `net.jini.jeri`, operating on the injected `ObjectOutput`/`ObjectInput` above the
  transport — `RemoteContextCodec.java:19, 23-24, 61, 152, 213`; its `unmarshal(in, workerSubject)` is
  driven from `BasicInvocationDispatcher.java:2535`). It reconstructs the remote caller's
  `AccessControlContext`/`ProtectionDomain[]` from DER over the codec and is already transport-agnostic,
  so it works over UDS with **no change**. The verified *workload* `Subject` from §3 is the
  `workerSubject` argument that `RemoteContextCodec.unmarshal` stamps the user domains against — i.e.
  increment 2 supplies the *connection gate*, and the existing codec supplies the *user gate*; the two
  gates compose exactly as Peter's two-gate model requires (workload ≠ user; reducing domain set).
  Re-implementing or altering user-subject transmission is **out of scope**.
- **`SSLEngine` / TLS over UDS is explicitly NOT needed** for peer-auth (§4). It is not built, not
  required, and not a dependency of this increment. (It remains a separate consideration only for the
  weaker non-`SO_PEERCRED` cross-uid case in §5, and for the independent TCP-SSL virtual-threads item.)
- **Delegation.** `Delegation.YES` is not supported: a peer-cred/SVID handshake conveys no delegation
  credentials. Only `Delegation.NO` remains (§3d).
- **Off-host reachability / dual-export** — unchanged from increment 1 §7.

---

## 8. Task breakdown / increments

1. **Peer-cred provider primitive.** `UnixPeerCredentials` provider interface in `net.jini.jeri.uds`
   returning `{uid,gid,pid}` for an accepted/connected `SocketChannel`; Linux/Panama implementation
   (`getsockopt(SO_PEERCRED)`) or DirtyChai-API implementation; no-op default that reports "unavailable".
   Isolated exactly like `restrictPermissions` (`UdsServerEndpoint.java:269-286`).
2. **Kernel principal type.** A small `Principal` (e.g. `UnixPeerCredPrincipal`) carrying `{uid,gid}`
   (not `pid`, per §5 mitigation), for inclusion in the peer `Subject` and for policy `grant principal`
   clauses. Serializable, validated like `SpiffePrincipal` (`SpiffePrincipal.java:127-137`).
3. **Client presents SVID** — fill in `UdsEndpoint.ConnectionImpl.writeRequestData(...)`
   (`UdsEndpoint.java:549-555`): write the ambient workload SVID at connection open. Mirror with the
   server presenting its own SVID for the client's symmetric check in `readResponseData`.
4. **Server verifies + cross-validates** — fill in
   `UdsServerEndpoint.ServerConnectionImpl.processRequestData(...)` (`UdsServerEndpoint.java:579-583`):
   read client SVID → `SpiffePrincipal.fromCertificate(...)` (`SpiffePrincipal.java:154`); read
   `SO_PEERCRED` (task 1); cross-validate SVID ↔ SPIRE-registration-by-uid/pid; build the verified
   read-only worker `Subject` ({`SpiffePrincipal`…, `UnixPeerCredPrincipal`}); stash on the connection
   (as `SslServerEndpointImpl.java:1474, 1514` stashes `clientSubject`).
5. **Inject into context** — fill in `UdsServerEndpoint.ServerConnectionImpl.populateContext(...)`
   (`UdsServerEndpoint.java:627-634`, replacing the stub comment at line 633) with
   `Util.populateContext(context, verifiedWorkerSubject)` (`Util.java:738`); client-side, mirror
   `SslConnection.populateContext` (`SslConnection.java:496-547`) to add the server `Subject` /
   `ServerSubject` element.
6. **`Constraints` upgrade** — add the auth-`YES` `supportedValues` entries and the principal-class
   `supportedClasses` entries (§3d), **gated on peer-cred availability** (§5 caveat 1); implement the
   value-aware principal-constraint check in `ServerConnectionImpl.checkConstraints`
   (`UdsServerEndpoint.java:602-614`) and the client reuse/unfulfilled path against the verified peer
   principals (the SSL `ConnectionContext.isSupported` model, `ConnectionContext.java:287-308`, is the
   template — *not* a pure static-map extension).
7. **(Optional) standalone peer-cred context element** — a `net.jini.io.context`-style
   `ClientCredentials` element exposing `{uid,gid,pid}` distinctly (since `ClientHost` cannot carry it,
   §3c). Only if a consumer needs the raw peer-cred outside the `Subject`.
8. **QA** — a UDS variant of the matching test with two per-service VMs of *different uid* on Linux,
   asserting: (a) authorized workload → verified `Subject` with the right `SpiffePrincipal` in
   `ClientSubject`; (b) a mismatched SVID/uid pair (SVID not registered to that uid) → rejected at
   `processRequestData`; (c) auth-`YES` constraint satisfied on Linux, *unsatisfiable* on the
   no-peer-cred build; (d) PID-reuse does not grant (authorization on `{uid,gid}`). Reuses the mock
   SPIRE agent (memory `jgdms-qa-mock-spire-agent`).

---

## 9. Open questions

1. **`SO_PEERCRED` delivery** — Panama FFI downcall vs a DirtyChai `SocketChannel` peer-cred API. The
   DirtyChai API is cleaner and SM-friendly; Panama keeps the transport vanilla-buildable. Decide at
   build time; the task-1 provider interface makes it a swap.
2. **SVID transport encoding at open** — how the SVID bytes are framed on the connection stream before
   the mux takes over (length-prefixed DER? a tiny pre-mux frame?), and whether the server's own SVID
   is offered eagerly or on demand for the symmetric server-auth check.
3. **SPIRE lookup at verify time** — does `processRequestData` query the SPIRE agent's Workload/Admin
   API to confirm "uid/pid ⊢ SVID", or does it trust the SVID's signature chain + a local
   registration cache? The former is authoritative but adds a per-connection round-trip; the latter is
   faster but needs a freshness/revocation story.
4. **Gating the auth-`YES` claim** — register the YES entries conditionally at class-init based on
   provider availability, or always register and fail in `checkConstraints` when no peer-cred was
   obtained? The former makes `checkConstraints` on a fresh endpoint honest before any connection
   exists; the latter is simpler. (§5 caveat 1.)
5. **Cross-uid on non-Linux** — accept "socket-perms / same-uid only" as the permanent answer, or
   provide the TLS-over-UDS (`SSLEngine`, increment-1 §4(i)) path as the cross-uid authenticated option
   where `SO_PEERCRED` is absent? This is the only place the deferred `SSLEngine` work re-enters.

---

## 10. References

- `docs/SOW-Unix-Domain-Socket-JERI-Transport.md` — increment 1 (transport, locality-confidentiality,
  object-layer integrity, the reserved identity seams).
- Current UDS transport: `net/jini/jeri/uds/UdsEndpoint.java` (client `Connection`, seams at 549-564),
  `net/jini/jeri/uds/UdsServerEndpoint.java` (server `ServerConnection`, seams at 579-583, 602-614,
  627-634), `net/jini/jeri/uds/Constraints.java` (static-map constraint model, 106-192).
- SSL template: `net/jini/jeri/ssl/SslServerEndpointImpl.java` (server `populateContext` +
  `clientSubject`, 1474, 1514-1515, 1765-1771), `net/jini/jeri/ssl/SslConnection.java` (client
  `populateContext` → `ServerSubject`, 496-547), `net/jini/jeri/ssl/ServerAuthManager.java`
  (client-subject credential check, 122-197), `net/jini/jeri/ssl/ConnectionContext.java` (dynamic
  value-aware constraint model, 287-308; `clientMinPrincipalType` 69-71).
- Context-injection machinery: `org/apache/river/jeri/internal/runtime/Util.java`
  (`populateContext(Collection,Subject)` 738-742; `ClientSubjectImpl` 761-773; `getClientSubject`
  815-818), `jgdms-platform/.../net/jini/io/context/ClientSubject.java` (31-54),
  `jgdms-platform/.../net/jini/io/context/ClientHost.java` (returns `InetAddress`, 38 — the seam that
  does *not* carry to UDS), `net/jini/jeri/BasicInvocationDispatcher.java` (`checkClientPermission`
  1551-1584, `invokeWithClientSubject` 2107-2114, `RemoteContextCodec.unmarshal` at 2535).
- Identity pieces: `net/jini/jeri/ssl/SpiffePrincipal.java` (`fromCertificate` 154-179; note it lives
  in `net.jini.jeri.ssl` — see memory `jgdms-spiffeprincipal-dl-relocation` for the planned move to
  `jgdms-lib-dl`), `net/jini/jeri/RemoteContextCodec.java` (invocation-layer user-ACC transmission,
  19/23-24/152/213; driven from `BasicInvocationDispatcher.java:2535`).
- Memory: `jgdms-uds-jeri-transport`, `jgdms-spiffe-auth` (two-gate model), `jgdms-remotecontextcodec-acc-transport`,
  `dirtychai-roleneutral-worker` (SecureClassLoader + attested worker), `jgdms-qa-mock-spire-agent`.
