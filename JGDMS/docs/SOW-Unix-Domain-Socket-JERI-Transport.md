# SOW: Unix Domain Socket JERI Transport (local IPC)

Status: **design / captured 2026-06-24** (build deferred until after the matching-test SPIFFE
wiring). JGDMS-side, committable. Decisions taken with Peter:
- **Identity model: SPIFFE/TLS-over-UDS first.** Reuse the existing SPIFFE mTLS identity; UDS is
  the byte transport. (`SO_PEERCRED` kernel-attested identity is a *deferred* later increment.)
- **Platform-agnostic is a firm constraint** ("for now"): `java.net` UDS only — no `SO_PEERCRED`
  (Linux/macOS-only), no abstract-namespace paths (Linux-only), mind Windows AF_UNIX path limits.
- **Sequence:** capture the design now (this doc); build after the matching test is green on SSL/TCP.

## 1. Motivation
The per-service-VM SPIFFE topology (Peter's choice 2026-06-24 — see memory
`jgdms-qa-mock-spire-agent`) puts co-located service processes on one host talking JERI. UDS is the
natural *local* transport: network-isolated, filesystem-permission-scoped, no loopback ports, lower
overhead. The plumbing is already proven — the mock SPIRE agent runs a UDS server over
`UnixDomainSocketAddress` / `ServerSocketChannel(UNIX)` today. SPIRE itself uses UDS (the Workload
API), and node-local UDS IPC is the mesh model (`real-world-data-system`), so a JGDMS UDS transport
sits squarely between both.

**Process isolation / privilege separation.** Beyond efficiency, UDS is the IPC fabric that makes
*process isolation* practical in JGDMS — and that lands on JGDMS's core threat model. JGDMS runs
downloaded code under a *layered* trust model. First, **SCAP** — the Safe Codebase Audit Pipeline
(JGDMS-STD-001/002) — **pre-audits the bytecode before execution**: code that follows the rules earns
a SAFE verdict → `DigestGrant` → `LoadClassPermission`, i.e. it is vetted *into* trust, not merely
contained ("SCAP produces the advice; policy enforces the consequence"). SPIFFE identity (STD-003)
establishes who it runs as — SCAP-approved code + SPIFFE identity together constitute full trust (a
two-axis model: code-safety × identity) — and DirtyChai's `SecurityManager`/POLP enforces that earned
authority at runtime. **Process isolation is the OS-level containment *backstop* for residual risk** —
code that passed audit but is still buggy, INCONCLUSIVE-verdict code an admin elected to run, or
defense-in-depth against an audit gap or a JVM memory-safety bug: run such high-risk surfaces —
downloaded-proxy execution, deserialization of untrusted data
(`@AtomicSerial`/`DeSerializationPermission`), foreign-protocol brokers
(memory `spiffe-halow-mesh-federation`) — in a separate low-privilege process (own address space,
uid, policy, SPIFFE identity), reached over UDS, so even a compromise that slips past the audit and
runtime gates (a memory-safety bug, a native crash, an SM bypass) is OS-contained and can't read the
trusted core's memory or inherit its privileges. Defense in depth across independent layers:
pre-execution audit (SCAP) **+** runtime authority (SPIFFE identity + POLP) **+** OS process
isolation. Note the *mix* shifts by node type: JVM/DirtyChai nodes lean on SCAP's bytecode audit;
Rust static-musl nodes and foreign-protocol brokers (no bytecode to audit) lean correspondingly more
on attestation, the broker model, and process isolation. The per-service-VM QA topology is exactly this shape; the mesh broker is a
privilege-separated process by design. UDS socket-file permissions (and the deferred `SO_PEERCRED`)
put OS-level access control on the channel itself.

## 2. Goals / non-goals
- **Goal**: a first-class JERI transport over UDS carrying the existing SPIFFE mTLS identity;
  portable across Win10+/Linux/macOS.
- **Non-goal (deferred)**: `SO_PEERCRED` kernel-attested peer identity (uid/gid/pid). It is the
  SPIRE workload-attestation primitive and the answer to no-cert-yet bootstrap, but it is
  Linux/macOS-only and absent from standard Java (needs Panama FFI on DirtyChai 27 or a DirtyChai
  JDK API) — a future mesh-node increment, out of scope here.
- **Non-goal**: off-host reachability. UDS is host-local by nature (see §7 dual-export).

## 3. Where it plugs into JERI (grounded in the existing transports)
- **Public pair**: `net.jini.jeri.ServerEndpoint` + `net.jini.jeri.Endpoint`. Implement a
  `UnixServerEndpoint` / `UnixEndpoint` pair, analogous to `net.jini.jeri.tcp.TcpServerEndpoint` /
  `TcpEndpoint` (a transport is a thin layer — `tcp` is 3 classes incl. `Constraints`).
- **Shared connection layer**: `net.jini.jeri.connection.*` — `Connection`, `ServerConnection`,
  `ConnectionEndpoint`, `ConnectionManager`, `ServerConnectionManager`, `In/OutboundRequestHandle`.
  Both `tcp` and `ssl` build on this; the UDS transport implements `Connection`/`ServerConnection`
  over a UDS channel.
- **SSL reuse**: `net.jini.jeri.ssl.*` — `SslServerEndpoint`/`SslEndpoint` + `SslConnection` +
  `AuthManager`/`ServerAuthManager`/`ClientAuthManager` + `SubjectCredentials` drive the SPIFFE
  mTLS. SSL-over-UDS reuses this machinery with the byte transport swapped.

## 4. The critical finding — the SSL/UDS adaptation is the crux
Verified against current source:
- **The connection layer fits UDS naturally.** `net.jini.jeri.connection.Connection` is
  stream-based — `InputStream getInputStream()`, `OutputStream getOutputStream()` — with an
  *optional* `SocketChannel getChannel()` (the mux uses it for non-blocking I/O when present, null
  otherwise). A UDS connection provides streams via `Channels.newInputStream/newOutputStream(udsCh)`
  and can return the UDS `SocketChannel` from `getChannel()` directly. **The JERI + connection
  layers are transport-shape-agnostic; no friction there.**
- **The SSL layer is `SSLSocket`-based, not `SSLEngine`-based.** `SslConnection` holds a
  `volatile SSLSocket sslSocket` (created via `sslSocketFactory.createSocket(...)` layered over a
  plain `java.net.Socket` from `createPlainSocket`); `SslServerEndpointImpl` has ~14 `SSLSocket`
  references. There is no `SSLEngine` anywhere in the SSL transport.
- **UDS in Java has no `java.net.Socket`.** UDS is `SocketChannel.open(StandardProtocolFamily.UNIX)`
  only; `udsChannel.socket()` throws `UnsupportedOperationException`, and there is no `InetAddress`.

So SPIFFE/TLS-over-UDS is **not** a socket-factory swap. The `SSLSocket → java.net.Socket` coupling
is the whole problem, and there are two ways to break it:

### Option (i) — migrate the SSL record layer to `SSLEngine` (clean, bigger)
`SSLEngine` is transport-agnostic (it operates on `ByteBuffer`s, not sockets), so it drives TLS over
*any* byte channel — TCP `SocketChannel`, UDS `SocketChannel`, anything. Rewrite `SslConnection`'s
`SSLSocket` usage (and the server side) as an `SSLEngine` wrap/unwrap loop over the channel.
- **Pros**: the *right* long-term shape; works for UDS and TCP uniformly; **enables non-blocking I/O
  → aligns with the virtual-threads direction** (`no-threadlocal-virtual-threads`) — the
  `SSLSocket` model blocks a platform thread per connection, `SSLEngine` + the `getChannel()` mux
  path does not. UDS could be the forcing function for a migration that's independently on the
  roadmap.
- **Cons**: a substantial rewrite of the core SSL transport; must re-validate the full SPIFFE
  handshake + constraints over the new path.

### Option (ii) — thin `java.net.Socket`-over-UDS-channel adapter (pragmatic, brittle)
Keep `SSLSocket`. Write a minimal `java.net.Socket` subclass whose `getInputStream`/`getOutputStream`
delegate to the UDS channel (`Channels.new*Stream`), and feed it to the existing
`SSLSocketFactory.createSocket(Socket, host, port, autoClose)` path (which layers TLS over an
already-connected `Socket`).
- **Viability note**: the placeholder `host`/`port` are acceptable because JGDMS authenticates the
  peer by **SPIFFE cert identity, not hostname** (`FilterX509TrustManager` + SPIFFE principal
  matching, no hostname verification) — so the absence of a real `InetAddress` doesn't break auth.
- **Pros**: reuses the entire SPIFFE `SSLSocket` machinery; far less code; fast path to a working
  prototype + the per-service-VM UDS QA.
- **Cons**: brittle — the adapter must cover whatever `Socket` surface `SSLSocket` actually touches
  (timeouts, `shutdownOutput`, `getInetAddress`, etc.); a JSSE internals change could break it.

**Recommendation (for build time, not now):** prototype with **(ii)** to validate the transport and
the UDS QA quickly, then land **(i)** as the production path and bank the virtual-threads /
non-blocking win. Final pick deferred to build time.

## 5. Architecture
- **Address model**: endpoint address = a filesystem socket path (`UnixDomainSocketAddress`),
  replacing host:port. Serialized endpoint form = the path (+ constraints). A UDS endpoint is only
  actionable by a *same-host* client (see §7).
- **Transport (byte layer)**: server
  `ServerSocketChannel.open(UNIX).bind(UnixDomainSocketAddress.of(path))` → `accept()` →
  `SocketChannel`; client `SocketChannel.open(UNIX).connect(UnixDomainSocketAddress.of(path))`.
  Reuse the mock's proven plumbing.
- **SPIFFE/TLS layer**: per §4, over the UDS channel — same identity source as TCP-SSL
  (`AuthManager` → the ambient `WorkerSubject` / SVID per
  `SPIFFE-WorkerSubject-Integration-Contract.md`). No new identity code.
- **Connection layer**: `SslConnection`-shaped — `getInputStream`/`getOutputStream` are the TLS
  streams; `getChannel()` returns null when TLS hides the channel (as today), or the raw UDS channel
  if/when the `SSLEngine` path exposes it.
- **Constraints**: reuse the SSL constraint set (`Integrity`, `ServerAuthentication`,
  `ClientAuthentication`, `Confidentiality`, …) unchanged — mTLS authenticates the peer SPIFFE
  identity exactly as over TCP.

## 6. Platform-agnosticism (firm constraint)
- Use **only** `java.net` UDS: `UnixDomainSocketAddress`,
  `SocketChannel`/`ServerSocketChannel(StandardProtocolFamily.UNIX)` — present on Win10+/Linux/macOS
  (Java 16+; vanilla Zulu build JDK *and* DirtyChai 27).
- **No `SO_PEERCRED`** (Linux/macOS only) — deferred increment.
- **No abstract-namespace paths** (Linux `@`-prefixed) — filesystem paths only.
- **Windows AF_UNIX caveats**: ~108-char `sun_path` limit (use short paths, e.g. a per-run temp
  dir); no peer-cred; otherwise functional. No platform branches in the transport.

## 7. Off-host / dual-export
UDS is host-local. A service reachable both locally and remotely exports **both** a UDS endpoint and
a TCP/SSL endpoint; a JERI proxy can carry multiple endpoints and the client uses the UDS one only
when same-host. Open: same-host detection + endpoint selection (advertise both; client prefers UDS
when the path is accessible, else TCP). A UDS-only endpoint's serialized form travelling off-host in
a proxy is non-actionable — must degrade, not fail.

## 8. Security
- **Defense in depth**: (1) UDS socket-file permissions (restrictive owner/group mode gates who can
  `connect` at the OS layer) **+** (2) SPIFFE mTLS (authenticates the peer identity).
- Codebase / deserialization grants apply as over TCP (dynamic codebase-download grant,
  `DeSerializationPermission("ATOMIC")`, etc.).
- **Socket-file lifecycle**: create in a private directory with restrictive perms; `deleteIfExists`
  before bind but guard against hijack (private dir, not a world-writable `/tmp` root); unlink on
  close; handle stale files.

## 9. Build plan (after the matching test)
1. UDS byte transport (`ServerSocketChannel`/`SocketChannel` UNIX) — from the mock plumbing.
2. `UnixServerEndpoint` / `UnixEndpoint` (the JERI pair) + `Connection`/`ServerConnection` over the
   UDS channel (connection layer).
3. SPIFFE/TLS over the channel — prototype via the §4(ii) `Socket` adapter, then §4(i) `SSLEngine`.
4. Constraints mapping + the serialized endpoint form (path) + endpoint equality/`hashCode`.
5. QA: a UDS variant of the matching test (per-service VMs talk UDS) — end-to-end validation.
6. *(Deferred)* `SO_PEERCRED` increment (Linux/mesh, Panama FFI or DirtyChai API).

## 10. Open questions
1. §4 (i) `SSLEngine` migration vs (ii) `Socket` adapter — pick at build time; lean (ii)-prototype →
   (i)-production. Does pursuing (i) now to capture the virtual-threads win change the sequencing?
2. UDS-only endpoint inside an off-host proxy: representation, same-host selection, dual-export, and
   graceful degradation when the path isn't reachable.
3. Socket path conventions + permissions + lifecycle (private dir, mode, stale-file handling,
   Windows path-length).
4. Any current JERI assumption that bakes in host:port (discovery, endpoint equality, constraint
   reasoning) that a path-addressed endpoint would break.
5. **Cross-language local IPC**: the HaLow mesh broker model (memory `spiffe-halow-mesh-federation`)
   co-locates JVM (DirtyChai) and Rust static-musl JERI on a node, so a UDS hop may be JVM↔Rust, and
   the deferred `SO_PEERCRED` increment is precisely that broker's on-node kernel-attested local trust
   (broker ↔ foreign-device-proxy ↔ JERI services). Does the UDS JERI wire need a Rust-implementable
   profile, or do cross-language local hops use a narrower broker-IPC contract? Keep the wire
   language-portable either way.

## 11. References
- `net.jini.jeri.tcp` (`TcpServerEndpoint`/`TcpEndpoint`) — transport template.
- `net.jini.jeri.connection.*` — shared connection layer (`Connection` = streams + optional
  `SocketChannel getChannel()`).
- `net.jini.jeri.ssl.*` (`SslConnection`, `SslServerEndpointImpl`, `AuthManager`,
  `SubjectCredentials`) — SPIFFE/TLS to reuse; **`SSLSocket`-based today**.
- `JGDMS/docs/SPIFFE-WorkerSubject-Integration-Contract.md` — the identity source.
- Mock SPIRE agent (proven UDS plumbing) — see memory `jgdms-qa-mock-spire-agent`.
- Memory: `no-threadlocal-virtual-threads` (the `SSLEngine` synergy), `real-world-data-system`
  (mesh node-local IPC), `dirtychai-subject-model`.
