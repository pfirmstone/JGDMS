# SOW: Unix Domain Socket JERI Transport (local IPC)

Status: **Increment 1 built** (`net.jini.jeri.uds.{UdsEndpoint,UdsServerEndpoint,Constraints,
UdsPaths}` — the actual class names; this doc's earlier `UnixServerEndpoint`/`UnixEndpoint` naming
in §3/§9 below is now stale, left unedited for history). Increment 2 (`SO_PEERCRED`/SPIFFE-SVID peer
authentication) **not built**. JGDMS-side, committable. Decisions taken with Peter:
- **Identity model: SPIFFE/TLS-over-UDS first.** Reuse the existing SPIFFE mTLS identity; UDS is
  the byte transport. (`SO_PEERCRED` kernel-attested identity is a *deferred* later increment.)
- **Platform-agnostic is a firm constraint** ("for now"): `java.net` UDS only — no `SO_PEERCRED`
  (Linux/macOS-only), no abstract-namespace paths (Linux-only), mind Windows AF_UNIX path limits.
- **Sequence:** capture the design now (this doc); build after the matching test is green on SSL/TCP.

### Implementation-status correction (2026-07-17)

Verified directly against source, not assumed:

- **Increment 1 exists and is substantial**, not a stub — fail-closed bind (no pre-delete; a
  `BindException` triggers a short-timeout probe-connect before ever unlinking, so a live server is
  never silently taken over), owner-only `0700`/ACL enforcement (fails `listen` rather than degrade,
  unless the caller opts in via `allowUnprotectedSocket=true`), parent-directory hardening against
  symlink/rename swap, and inode-checked unlink-on-close.
- **§8 below overstates Increment 1's security posture as written.** The transport does **not** yet
  authenticate the peer at all — confirmed in `Constraints.java`'s own class javadoc: only
  `Confidentiality.NO`/`ClientAuthentication.NO`/`ServerAuthentication.NO` are claimed, and the
  `Confidentiality.YES`("confidentiality by locality") claim was explicitly *removed* as an
  over-claim ("made statically by the client endpoint... with no verification that the far end is a
  local, owner-only socket"). Increment 1's *entire* peer-access gate is the filesystem permission on
  the socket file (owner/uid-scoped), not an authenticated principal. SPIFFE mTLS-over-UDS, described
  above as an Increment-1 decision, has **not** been wired in — it is deferred to Increment 2
  alongside `SO_PEERCRED`, per the same source.
- **Nothing routes real proxy traffic through it today.** Repo-wide search: the only consumers of
  `UdsServerEndpoint`/`UdsEndpoint` anywhere in the codebase are their own unit tests
  (`UdsConstraintEnforcementTest`, `UdsEndpointRoundTripTest`). No service, `InvocationLayerFactory`/
  exporter, or `service-starter` config instantiates or references either class. The transport is
  built and correct in isolation; it is not yet the thing carrying any proxy's traffic, downloaded or
  otherwise. **Wiring it into an actual downloaded-proxy-hosting exporter path is unimplemented work**,
  not a configuration flip — see the new §12 below.

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
  `connect` at the OS layer) **+** (2) SPIFFE mTLS (authenticates the peer identity, Increment 2 —
  see the status correction above: **not yet true of Increment 1 as built**).
- Codebase / deserialization grants apply as over TCP (dynamic codebase-download grant,
  `DeSerializationPermission("ATOMIC")`, etc.).
- **Socket-file lifecycle**: create in a private directory with restrictive perms; `deleteIfExists`
  before bind but guard against hijack (private dir, not a world-writable `/tmp` root); unlink on
  close; handle stale files. (As-built, this is stricter than described here — see status correction:
  bind-first/no-pre-delete, probe-connect-before-unlink, inode-checked unlink.)
- **CPU/core-affinity — confirmed open in both increments, orthogonal to peer authentication.**
  Process separation alone (different address space) defeats Flush+Reload/Evict+Reload-class
  cache-timing attacks (no shared page to probe) but does **not** defeat Prime+Probe-class attacks,
  which only need shared cache *hardware* — two processes' threads landing on hyperthread siblings of
  the same physical core still share L1/L2 regardless of address-space separation, and same-socket
  processes still share the LLC. Closing this needs an explicit deployment requirement alongside "own
  process": **disjoint physical-core pinning (cpuset/`taskset`, hyperthread-aware — never place two
  mutually-distrusting principals' processes on sibling hyperthreads of the same core) and, where the
  host topology allows it, disjoint NUMA nodes.** Not addressed by Increment 1 or Increment 2's design
  as currently scoped; needs to be stated as a requirement on whatever deploys/schedules the isolated
  proxy processes (the exporter/service-starter wiring in §12), not left implicit. Grounding: this is
  a real, published, quantified-cost mitigation approach (dynamic task migration as a cache-side-
  channel defense reports ~1.6% average / 9% worst-case overhead in the literature — see
  `SOW-BAE-Timing-Sidechannel-Denial.md` References), not a novel or speculative requirement.

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
- `SOW-BAE-Timing-Sidechannel-Denial.md` — the timing-side-channel investigation this decision (§12)
  is the primary answer to; BAE's bytecode-level `nanoTime` denial work is repositioned there as
  secondary defense-in-depth for whatever legitimately still executes in-process.

---

## 12. Decision (2026-07-17): mandatory UDS-isolated routing for downloaded/mobile smart-proxy execution

**Decided (Peter):** routing through a UDS-isolated, per-principal process becomes **mandatory** for
downloaded/mobile smart-proxy execution — not merely an available high-risk-surface option per §1's
original "backstop for residual risk" framing. This directly answers the pron98 r/java timing
side-channel point (`SOW-BAE-Timing-Sidechannel-Denial.md` §1): genuine OS-process separation is the
one mechanism the field (Chrome/V8 Site Isolation, GraalVM's own external-isolate mode, the general
Spectre literature) converges on as actually closing same-address-space cache-timing side channels,
and — important, checked directly rather than assumed — **this property does not wait on Increment 2**.
Confidentiality/authentication-by-locality (Increment 2's job) and address-space separation (what
defeats Flush+Reload/Evict+Reload) are different properties; the latter is true the moment a proxy's
real bytecode executes in a different OS process at all, regardless of whether the channel to it is
peer-authenticated yet.

**What "mandatory" requires, precisely — not just "uses UDS":**
1. **Decided (Peter, 2026-07-17): one isolated local process per distinct *remote SPIFFE principal*,
   pooled across that principal's smart proxies — not per proxy instance, not per object endpoint, not
   per local consumer.** The threat model this SOW answers is remote, mutually-distrusting principals'
   downloaded code sharing a local address space; the isolation boundary that actually matches it is the
   *exporting* principal's SPIFFE identity, not the specific remote object/endpoint a given proxy talks
   to. Two smart proxies from the *same* remote SPIFFE principal (e.g. two different objects that
   principal exported) can share one local isolated process — same trust domain, no isolation need
   between them; two smart proxies from *different* remote SPIFFE principals must never share one.
   **Cheap to wire**: the exporting server's authenticated principals are already extracted at the
   `resolve()` call site (`serverPrincipals`/`serverSubjectFromContext`, derived from the TLS-layer
   `ServerSubject` in `resolve()`'s own `context` parameter, `PreferredProxyCodebaseProvider.java:
   1692-1704`) — no new plumbing needed to read the key, only new logic to route on it.
   **Correction to an earlier draft of this point** (a dedicated agent pass, 2026-07-17, caught an
   imprecision before it went further): `PreferredProxyCodebaseProvider`'s existing ClassLoader-cache
   `Key` class (`:2139-2168`) folds in `InvocationHandler`/`ObjectEndpoint` equality, which identifies
   the remote *object/export* being called, not the remote *principal* — a finer, and for this purpose
   wrong, grain (it would fragment one principal's proxies across many processes unnecessarily). It is
   not the pattern to mirror for this routing key; the SPIFFE-principal keying above is the actual
   decision.
   Separately: the OSGi `ProxyBundleProvider`'s own equivalent `Key` class (`:289-314`, confirmed by the
   same verification pass) keys on the *same* `(handler, codebase)` shape as `PreferredProxyCodebaseProvider`,
   minus the `parent` ClassLoader dimension — i.e. it is **not** meaningfully different on this specific
   axis, and neither is the pattern to reuse. The real, independently double-confirmed OSGi mistake is
   not a ClassLoader-keying gap — it's the missing SCAP/BAE verdict gate; see the new point 5 below.
   **Follow-up question answered (2026-07-17): pooled proxies within one subprocess still need
   ClassLoader isolation from each other — yes, retain it, but for a more precise reason than "so they
   can have distinct permissions" alone.** Permission differentiation *by itself* doesn't strictly
   require separate `ClassLoader`s — a `ProtectionDomain` is keyed by `(CodeSource, ClassLoader,
   Principal[])`, and JGDMS's existing `DigestGrant` mechanism already grants **per-JAR/per-digest**
   (§12 point 6 above), which a `SecureClassLoader`-style loader honours per-`CodeSource` even for
   classes it loads itself, without needing a *separate* `ClassLoader` instance per source. The
   stronger, correctness-grade reason to keep it is **class-namespace and static-state hygiene between
   logically-unrelated proxy objects**: two smart proxies from the *same* principal (hence pooled
   together, point 1) are still independent objects that may each ship a class of the same fully-
   qualified name, or transitively share a library class with mutable static state — sharing one
   `ClassLoader` between them risks name collisions or unintended state coupling between proxies that
   have no actual relationship to each other beyond sharing an exporter. This is exactly what
   `PreferredProxyCodebaseProvider`'s existing `Key`-based per-object ClassLoader caching already does
   today (point 1's correction above), just previously analysed for the wrong axis (endpoint identity as
   a *routing* key, which was wrong) rather than as an *intra-process* isolation mechanism (where it's
   right). **Decision: the subprocess's own internal proxy-hosting logic reuses this same `Key`-based
   per-proxy-object `ClassLoader` separation internally**, now scoped *within* one principal's subprocess
   rather than across the whole JVM — proven, already-built machinery, not something new to invent.
   **Residual flagged above — resolved (Peter, 2026-07-17): same-principal intra-subprocess timing
   side-channel leakage is harmless, and this follows necessarily from point 1's own pooling decision,
   not as a separate new argument.** Reasoning: `DynamicPolicyProvider.grant(...)` is gated by the
   caller itself holding `GrantPermission` for whatever it grants (§12 point 6 above) — a hard ceiling
   nobody's grant can ever exceed — combined with the grant being scoped to what that specific principal
   asked for/was found to need. Two proxies from the *same* principal are both bounded above by
   *the same* client-imposed ceiling and both ultimately act on behalf of *the same* remote party. If
   proxy A manages to timing-read something from co-resident proxy B, the information doesn't cross any
   trust boundary the client's own design treats as meaningful — the client already decided (point 1)
   that "principal" is the isolation granularity that matters, not "proxy object within a principal";
   whatever A learns from B was already, in aggregate, something the client was willing to expose to
   that principal. This isn't a new finding so much as making explicit what point 1's pooling decision
   already implied.
   **What per-proxy `ClassLoader`/permission separation is actually still for, precisely — two reasons,
   not one:** (1) POLP hygiene / class-namespace correctness, as above; (2) **real defense-in-depth
   against injection attacks**, independent of the timing question entirely — narrow, per-proxy-object
   permission ceilings bound the blast radius of any code that ends up executing somewhere it
   shouldn't (a deserialization bug, a crafted/malicious payload exploiting some other flaw) to
   *that specific object's* narrow grant, rather than inheriting whatever broader ceiling the
   subprocess's *other*, legitimately-more-trusted proxy objects happen to hold. This is the same
   POLP logic that motivates fine-grained scoping generally, applied specifically to the
   "something got in that shouldn't have" case rather than the "legitimate code learned something via
   a side channel" case this residual was originally about — worth keeping distinct in mind, since they
   have different threat models even though both argue for the same mechanism (retain per-proxy
   `ClassLoader`/`ProtectionDomain` separation within the subprocess).
2. **CPU/core-affinity, per §8 above.** Mandatory routing without disjoint physical-core pinning closes
   the Flush+Reload/Evict+Reload class and leaves Prime+Probe open. Both are needed for the claim to be
   complete.
3. **Implementation-status correction (2026-07-20): the wiring below describes what was true when this
   point was drafted (2026-07-17) — it is now built.** `SOW-Smart-Proxy-Isolation-Wiring.md` T1
   (routing insertion), T2 (subprocess spawn/pool/track, including the `SubProcessAdministrable`/
   `PolicyAdmin` authentication scaffold), and T3 (`ProxySerializer` interface-name field) landed
   2026-07-19; T4 (the wire-protocol handoff mechanics described in this point's own prose below) landed
   2026-07-20 after two board-review rounds found and fixed real defects — see
   `SOW-T4-Wire-Handoff-Protocol.md` for the byte/framing-level built state, which supersedes this
   point's own prose as the authoritative reference for T4. **What remains true of this point's original
   "unimplemented" framing, narrowly:** the underlying transport this whole design assumes is still the
   plain `java.nio.channels.ByteChannel` framing T4 built directly (not literally a
   `UdsServerEndpoint`/`UdsEndpoint` pair from §1-§11 above — that choice was made and documented in
   `SOW-T4-Wire-Handoff-Protocol.md` §6, "why not reuse JERI/UDS directly"), and **`SubProcessLauncher`'s
   real OS-process `fork`/`exec` implementation remains `UnsupportedSubProcessLauncher`** — confirmed by
   multiple board reviewers across the T2 and T4 work — so the mechanism this point describes is real,
   tested, and board-reviewed, but not yet backed by a real spawned OS process in any running deployment.
   **Original point text below, kept for design-history record — read as "this is what was planned,"
   now realized as described except where the correction above says otherwise:**
   **The actual wiring is unimplemented — candidate insertion point identified (2026-07-17).** Per the
   status correction above, nothing today constructs a `UdsServerEndpoint`/`UdsEndpoint` pair for any
   real service. **`net.jini.loader.pref.PreferredProxyCodebaseProvider.resolve(CodebaseAccessor,
   MarshalledInstance, ClassLoader, ClassLoader, Collection)`** (the `ProxyCodebaseSpi` implementation,
   `jgdms-pref-class-loader`) is a strong candidate for the routing decision: it is already the single
   choke point every downloaded/mobile proxy's `MarshalledInstance` passes through before becoming a
   live local object, and it already gates on the SCAP/BAE `VerdictRegistry` verdict
   (`checkVerdictForJar`, SAFE/INCONCLUSIVE/DANGEROUS, fail-closed on signature/registry failure)
   *before* any classloading happens — the natural place to branch "route to a UDS-isolated
   per-principal process and return the thin `AtomicDerInvocationHandler`-wrapped stub" instead of (or
   as well as) constructing a local `ClassLoader` and deserializing in-process.
   **Superseded (Peter, 2026-07-17): the "which proxies need isolation" question is closed —
   isolation is unconditional for every smart proxy, no trust-tier signal needed.** This replaces the
   "must still be designed" signal-hunt below (kept, struck through in spirit, for the record of what
   was ruled out and why — still useful if a *narrower* future exemption is ever considered): a signal
   distinguishing first-party from downloaded proxies is no longer necessary because there is no
   longer a non-isolated path for smart proxies to take.
   **The bigger decision this unlocks: the local *client* process never loads a smart proxy's actual
   implementation bytecode at all.** ("Locally" here means the local client process specifically — the
   isolated proxy-hosting process is still same-host, UDS-reachable, per this whole SOW's local-IPC
   premise; it is a different OS *process*, not a different *machine*.) The client process only ever
   needs the proxy's public remote interface type(s) — already locally known, not downloaded
   per-service — wrapped in an ordinary `java.lang.reflect.Proxy` whose `InvocationHandler` (the
   existing `AtomicDerInvocationHandler` is the precedent to reuse or extend, `jgdms-jeri`) forwards
   every call over UDS to the real object living in the per-remote-SPIFFE-principal isolated process
   (point 1). The *entire* codebase-download/classload/BAE-verdict-gating pipeline —
   `PreferredProxyCodebaseProvider.resolve()`, the `Key`-class ClassLoader cache, `checkVerdictForJar`,
   all of it — moves into the isolated child process, which is the only place that ever downloads or
   executes a smart proxy's actual bytecode. This is a stronger and simpler property than anything a
   trust-tier signal could deliver: not "isolate untrusted smart proxies," but "no smart-proxy
   implementation bytecode is ever co-resident with the client's own code, full stop" — closing this
   SOW's core concern structurally rather than by classification, and shrinking the client process's
   attack surface to zero downloaded bytecode for this category entirely.
   What the previous draft of this point had found and ruled out, now superseded by the above but kept
   for the record: `resolve()` is invoked uniformly, with no trust-tier/SPIFFE-identity/first-party
   pre-filter anywhere upstream; verdict type is content-hash-scoped, not origin-scoped; the `Key` class
   identifies remote object/export, not first-party-vs-downloaded. **The `@SmartProxy`/
   `AbstractSmartProxy`-vs-Fiddler/Mahalo/Mercury/Reggie claim is now closed, not merely moot: the
   re-verification agent confirmed Fiddler/Mahalo/Mercury/Reggie do NOT use `@SmartProxy`/
   `AbstractSmartProxy`, correcting this doc's own earlier wrong claim to the contrary. Peter confirmed
   2026-07-17 this thread can be closed** — both because the finding itself is now settled and because,
   per the unconditional-isolation decision above, nothing in this SOW depends on the answer any longer.
   (The narrower, still-open question of which *objects* are smart proxies at all — needed as an ordinary
   implementation detail when building point 3's routing logic, not as a research question — is left for
   that task's own design work, not tracked here as an open investigation.)
   This is a `jgdms-pref-class-loader`/`jgdms-jeri`/exporter-layer task, not a documentation change —
   scope it as its own SOW/task (candidate: extend `SOW-BAE-Timing-Sidechannel-Denial.md`'s task list,
   or a new dedicated
   SOW).
   **Mechanics of the client-side stub construction and the client/subprocess split — clarified (Peter,
   2026-07-17), three parts:**
   (i) **Where unmarshalling happens.** A `MarshalledInstance` arriving at the client (from a
   lookup-service match or a remote-call return value) is not deserialized in the client process at all,
   even partially. The classic Jini smart-proxy shape typically encodes *two* nested objects in the same
   marshalled bytes — the smart proxy itself and the "backend" object it wraps (often just a thin
   JERI-generated stub over an `ObjectEndpoint`, carrying no downloaded bytecode of its own) — and both
   must be reconstructed as one unit, since the smart proxy's fields reference the backend object
   directly. Reconstructing that unit requires loading and running the smart proxy's own class — exactly
   what the paragraph above keeps out of the client process — so **the unmarshalling itself (the actual
   `resolve()`/`AtomicMarshalInputStream` work, `checkVerdictForJar`, classloading, all of it) happens
   inside the isolated per-principal subprocess, not the client.** The client's role is narrower than
   "call `resolve()` then wrap the result": it recognizes, from context (the interface type it searched
   for, the channel/principal the bytes arrived over), that a given `MarshalledInstance` needs subprocess
   routing, forwards the *raw, still-marshalled* bytes over UDS to the subprocess for that principal
   (spawning one if needed, point 7 below), and only then builds its own local `Proxy` once the subprocess
   confirms a live object exists — the client never obtains the smart proxy's bytecode or its deserialized
   field state, only an object reference to forward calls to.
   (ii) **Which interfaces the client's stub implements.** `Proxy.newProxyInstance` requires every listed
   interface to be resolvable by the supplied `ClassLoader`; the client cannot list an interface it
   doesn't have locally, so it doesn't try — **interfaces the client doesn't locally know about are
   silently dropped from the stub's interface set, not treated as an error.** This isn't a capability loss
   introduced by the isolation architecture; it's how Jini service matching already works: the client
   finds the proxy in the first place by searching a lookup service using `Entry`-based attribute matching
   or a specific service-API interface type it already has locally (`ServiceTemplate`) — the act of
   matching only ever uses types the client already possesses, so the interface(s) driving the match are
   never missing. Downloaded bytecode was never how the client learned an interface's shape; it was
   always local, by the nature of Jini lookup.
   **Open question raised and answered in the same breath (Peter, 2026-07-17): where does the *set of
   candidate interface names* actually come from, concretely, given the wire shape identified above (point
   (i) is `ProxySerializer`, `org.apache.river.api.io.ProxySerializer.java`)?** Today `ProxySerializer`'s
   wire form (`serialForm()`, `:52-57`) carries exactly two fields — `bootstrapProxy`
   (`CodebaseAccessor`, already a live local `Proxy` limited to the two fixed interfaces
   `BOOTSTRAP_PROXY_INTERFACES`, `:68-72`) and `serviceProxy` (the smart proxy, still wrapped in an
   unresolved `MarshalledInstance`, deliberately not touched until `readResolve()`/`resolve()` fires,
   `:226-229`). Neither field tells the client (or the subprocess-routing logic ahead of `resolve()`) what
   interfaces the smart proxy inside `serviceProxy` actually implements — finding out today means doing
   the exact thing this architecture exists to avoid: unmarshalling `serviceProxy` to look. **Decided: a
   new field must be added to `ProxySerializer`'s wire form carrying the smart proxy's declared interface
   set, populated at `create()` time (`:132-178`) from the live sender-side proxy object — before it is
   wrapped into `serviceProxy` — via its interface closure (`proxyClass.getInterfaces()`, walked to the
   full closure the same way `Proxy.newProxyInstance` itself requires).** This travels as an ordinary,
   eagerly-deserialized field of `ProxySerializer` itself (unlike `serviceProxy`, which stays lazy), so
   it's available to routing/stub-construction logic *before* any decision about `serviceProxy` is made —
   this is the concrete mechanism that makes (ii)'s and (iii)'s "which interfaces" reasoning actually
   computable rather than aspirational.
   **Second point, same breath (Peter, 2026-07-17): the new field must be designed for the case where
   some or all of the named interface classes are not available/resolvable at the reading process at
   all** — this is not an edge case, it's the expected case for exactly the downloaded/mobile-code
   scenario this whole SOW is about (a third-party smart proxy may implement interfaces the client, or
   even the orchestrating process, has never heard of). Concretely: **the field must be represented on
   the wire as class names (`String[]`, canonical/binary names), not as `Class[]` objects.** Encoding it
   as `Class[]` would force *ordinary AtomicSerial field deserialization* — which happens automatically,
   before any of this SOW's routing logic gets a say — to resolve every named class through the platform's
   normal codebase-aware class-resolution path, which is exactly the download-capable mechanism this
   architecture removes from the client process; an unresolvable or deliberately hostile class name in
   that position could otherwise force an attempted download or throw an uncatchable-at-the-right-layer
   `ClassNotFoundException` deep inside ordinary deserialization. With `String[]`, resolution is a
   **separate, explicit, application-controlled step** performed only when and where needed: a
   local-only, non-downloading lookup (e.g. `Class.forName(name, false, aLocalOnlyClassLoader)` against a
   loader that never delegates to any `ProxyCodebaseSpi`/download-capable mechanism), attempted
   independently per name, with `ClassNotFoundException`/`NoClassDefFoundError` on any individual name
   caught and treated as "not available here" — that name is dropped from *this reading process's*
   resulting interface set, per (ii) above, not treated as a fatal error for the whole object. Each
   process performing this lookup (client, or a downstream ServiceAPI consumer per (iii)) does so against
   its *own* local classpath, so the same wire-carried name list can legitimately resolve to different
   local interface subsets in different consuming processes.
   **Fallback for senders that don't populate the new field (wire-schema evolution).** An empty/absent
   field means "no advance interface hint available" — routing still proceeds (the raw `serviceProxy`
   bytes are still forwarded to the subprocess unconditionally per (i), that part never depended on
   knowing the interfaces up front), and the client-side stub gets built *after* the subprocess reports
   back the real interface set it observed once it actually resolved the object — a strictly worse
   (blocking, one extra round-trip) but still-correct path, not a hard failure.
   **Security note, not to be skipped at implementation time:** this field is attacker-influenced wire
   content (it comes from whatever served/marshalled the proxy) and must be treated accordingly — it may
   only ever be used to select which of the smart proxy's *own* business interfaces a given reading
   process exposes on its stub. It must **never** be a channel through which a privileged/administrative
   interface gets bundled onto a stub — most concretely, `SubProcessAdministrable`
   (`SOW-SubProcessDynamicPolicy.md` §2, refined 2026-07-18 to this dedicated interface name — see that
   SOW for the full mechanism) must never be added to a stub's interface set because a name resembling it
   appeared in this field; that interface is added only by the client's own local orchestration logic
   deciding it is the legitimate owning/administering party, entirely independent of wire content. **This
   exclusion is construction-time hygiene, not the enforcement boundary** — the actual authority proof is
   `SubProcessAdministrable.getSubProcessPolicyAdmin()`'s returned `PolicyAdmin` proxy failing closed to
   any caller that cannot authenticate as the orchestrating admin principal, so even a crafted name list
   that somehow got the interface bundled in anyway would still get nothing usable from it. Worth an
   explicit adversarial check at T3 design time (of whichever task ends up owning this): can a crafted
   interface-name list cause a stub to be built with more than its intended business interfaces.
   **Wire-schema/back-compat discipline applies for real here, not just as a formality.** This project has
   a live `japicmp` + serial-schema CI gate (memory: `jgdms-api-compat-tooling`) specifically to catch
   incompatible `@AtomicSerial` wire-form changes; adding a third field to `ProxySerializer.serialForm()`
   is exactly the class of change that gate exists to review, not a silent one-line edit even though the
   class itself is package-private.
   This is a `jgdms-platform`/`org.apache.river.api.io` change, same layer/task as point 3's own wiring
   (`resolve()`/`PreferredProxyCodebaseProvider`) — scope it alongside that task, not as a separate SOW.
   (iii) **When the client isn't the real consumer at all.** Some deployments have the process holding the
   local delegate stub be a different party than the process that actually wants to call the smart
   proxy's full API — e.g. a routing/relay process that doesn't know the service's real interfaces, versus
   a separate process that consumes it through its own "ServiceAPI" (which may be third-party-defined, not
   part of JGDMS or this deployment at all). In that shape, the local delegate proxy is a
   **per-consumer-JVM construct, not a single universal stub**: the relay process may hold a stub with a
   minimal/empty interface set, while the actual ServiceAPI-consuming process builds its *own* separate
   local delegate stub, over its *own* UDS connection to the same per-principal subprocess, using whatever
   interfaces its ServiceAPI defines. Multiple distinct local stubs, with different interface sets, can
   legitimately point at the same subprocess-hosted object — each built independently by whichever process
   did the matching for its own purposes. **Direct consequence for `SubProcessAdministrable` (point 6
   below / `SOW-SubProcessDynamicPolicy.md` §2, refined 2026-07-18): that interface must only be bundled
   onto the stub held by the orchestrating party entitled to administer the subprocess's policy — never
   onto a stub handed to a downstream ServiceAPI consumer, third-party or otherwise. When a proxy is
   serialized/exposed to a ServiceAPI-consumer's subprocess or JVM, `SubProcessAdministrable` must not be
   included in that stub's interface set at all** — that consumer's stub should carry only its own
   business interfaces, built independently as in (iii) above. **As with the security note in (ii): this
   is construction-time hygiene, not the sole enforcement mechanism.** Even a downstream consumer's stub
   built (maliciously or by bug) with `SubProcessAdministrable` bundled in gains nothing usable from it —
   `getSubProcessPolicyAdmin()`'s returned `PolicyAdmin` proxy authenticates the caller as the
   orchestrating admin principal via its own `MethodConstraints`, independent of which stub or channel the
   call arrived over. Channel/construction-time exclusion narrows casual reach; admin-principal
   authentication is what actually holds the line. See `SOW-SubProcessDynamicPolicy.md` §2 for the full
   mechanism, its residual-hazard note, and `SOW-Smart-Proxy-Isolation-Wiring.md` T2's third layer
   (reject-on-load if a hosted proxy's own resolved interface closure declares `SubProcessAdministrable`/
   `PolicyAdmin` — a defense-in-depth backstop for this same boundary, owned by that SOW not this one).
4. **Increment 2 stays on the roadmap, reframed, not blocking.** SO_PEERCRED/SPIFFE-SVID peer
   authentication remains the right next increment — for anti-impersonation on the channel and for
   honestly claiming `Confidentiality.YES` — but per point 0 above it is not a precondition for the
   side-channel-defeating property this decision is about.
5. **Re-verified 2026-07-18, downgraded: `ProxyBundleProvider` has no verdict-gate references, but is not
   currently reachable through this codebase's own provider-selection logic.** `jgdms-osgi-proxy-bundle-
   provider`'s `ProxyBundleProvider` (a second `ProxyCodebaseSpi` implementation) genuinely has zero
   references to `VerdictRegistry`/`checkVerdictForJar`/`DownloadPermission`/`BootstrapPermission`/
   `DigestGrant` — that part of the original finding holds. But a follow-up code-level check found the
   selection mechanism doesn't work the way the original note assumed:
   - `Service.providers()` (`jgdms-platform/.../resource/Service.java`) does consult the OSGi service
     registry (via `OSGiServiceIterator`) in addition to a classpath `META-INF/services` scan — that much
     is real, JGDMS-authored bridging, confirmed in code.
   - But **nothing anywhere in this repo calls `BundleContext.registerService(...)`** for either
     `ProxyBundleProvider` or `PreferredProxyCodebaseProvider` — grep confirms zero hits repo-wide — so
     the registry arm can never yield either candidate, regardless of ranking.
   - `ProxyBundleProvider` also lacks the `META-INF/services/net.jini.loader.ProxyCodebaseSpi` file that a
     real OSGi Service Loader Mediator extender (e.g. Aries SPI Fly) would need in order to register it —
     it only carries the bnd `@Capability`/`@Requirement` manifest headers, which are necessary but not
     sufficient on their own.
   - Net effect: as currently packaged, `ProxyBundleProvider` is not discoverable through
     `Service.providers()` in any topology examined — not because `PreferredProxyCodebaseProvider` wins a
     priority contest, but because `ProxyBundleProvider` was never actually wired to be found at all.
   - **Made inert 2026-07-18 (Peter's decision):** `jgdms-osgi-proxy-bundle-provider` removed from the
     root reactor `pom.xml` `<modules>` list, and its dependency declarations removed from `dist/pom.xml`
     and `services/reggie/reggie-service-subsystem/pom.xml`. It is no longer compiled or packaged into any
     build artifact — source retained on disk (matching `SOW-AtomicSerial-Delegate-Marshalling.md`'s
     "failed/dead module... retained, not to be used" framing) but nothing pulls it into the reactor
     anymore, so the "one missing `META-INF/services` file away from live" risk noted above is now moot
     unless someone re-adds the module to the build.
6. **How the isolated process's own `SecurityManager`/policy gets bootstrapped and updated — investigated
   (dedicated agent pass, 2026-07-17), and this is a real, separately-scoped gap in point 3's wiring
   task, not something to assume away.** Directly relevant to
   `SOW-BAE-Timing-Sidechannel-Denial.md` §1b point 3 (SM/POLP as the isolated process's complementary
   defense layer) — this is *how* that layer actually gets its permission ceiling.
   **What already exists and is directly reusable (mature, local-to-a-JVM primitives):**
   `DynamicPolicy`/`DynamicPolicyProvider` (`jgdms-platform/.../net/jini/security/policy/
   DynamicPolicyProvider.java`) support runtime grants to a JVM's own `ProtectionDomain`s, gated by the
   caller already holding `GrantPermission` for what it grants; `PermissionGrant`/`PermissionGrantBuilder`
   can scope a grant by class loader, `ProtectionDomain`, principal, certificates, codebase URI, *or*
   content digest, in any combination; `DigestGrant` (used today by
   `PreferredProxyCodebaseProvider.tryGrantPerUriDigestGrants`, `:1395-1451`) already grants **per-JAR**,
   not per-JVM — i.e. two smart proxies pooled behind the same remote SPIFFE principal (point 1) can
   already get *different* ceilings if their own verdicts differ, the matching granularity is not the
   gap; `LeasedPermissionGrant`/`OneShotLeasedPermissionGrant`/`LeasedDelegation` give a mature,
   directly-reusable time-bounded/revocable grant mechanism — `RevocablePolicy`'s own javadoc names
   exactly this "smart proxy given temporary trust" use case.
   **What's genuinely missing (do not assume this is solved by the above):**
   (a) **No existing pattern for provisioning a freshly-spawned child JVM's policy *after* launch.**
   The two existing process-shapes are both wrong for this: in-process service loading
   (`NonActivatableServiceDescriptor`) shares one dynamic policy across everything in *one* JVM by
   design (its own javadoc: "all services share the same security policy within a single JVM") — not
   what per-principal isolated processes need; RMI Activation (`phoenix-activation/.../Activation.java:
   1752` `buildGroupProcess`) *does* spawn real child OS processes, but only ever bakes a static
   `-Djava.security.policy=file` into the launch command — nothing reaches back into a spawned group
   process afterward to push a grant. **A verdict may not even be known yet at spawn time** (the
   isolated process is what does the actual codebase download/classload per the new architecture), so a
   static bake-at-launch approach doesn't fit either — the isolated process needs to receive/pull its
   ceiling *after* it has its own verdict, not before.
   (b) **No runtime SCAP-verdict → permission-set computation exists.** `ProxyPolicyGenerator`
   (`tools/policy-condenser/.../ProxyPolicyGenerator.java`) is an **offline, human/CI-driven** tool
   operating on `polpAudit`-recorded observations (the producer, `SecurityPolicyWriter`, lives in the
   separate DirtyChai repo, advise-only per that repo's own no-AI-contribution policy) — there is no
   `f(VerdictType, contentHash) → Permission[]` callable at spawn/verdict-known time.
   (c) **The closest existing cross-process delivery mechanism is unwired and the wrong shape.**
   `RemotePolicyService`/`RemotePolicyProvider` (`services/policy-service/`) is a real, working
   JERI-remote, lease-subscribable policy-distribution service — but it does a **whole-djinn-set
   replace**, administered by one fixed admin SPIFFE identity, pulled by nodes that opt in; nothing
   wires it into any process-bootstrap path (`service-starter`/`ActivateWrapper`/
   `NonActivatableServiceDescriptor`/`Activation.java` — none reference it), and it has no per-spawned-
   process, per-verdict push API today.
   **Decided (Peter, 2026-07-17), two parts:**
   1. **Namespace relocation.** `org.apache.river.api.security.RemotePolicyProvider` (the real, working
      implementation, §12 point 6 above) was written into the shared legacy `org.apache.river.api.security`
      namespace; it belongs in the `au.zeus` namespace instead — **relocate it to replace the incomplete
      stub already sitting at `au.net.zeus.jgdms.api.policy.RemotePolicyProvider`**
      (`jgdms-platform/src/main/java/au/net/zeus/jgdms/api/policy/RemotePolicyProvider.java:28-35`, the
      NetBeans-template leftover whose sole method throws `UnsupportedOperationException("Not supported
      yet.")`, confirmed dead). The legacy `org.apache.river.api.security` copy's callers need
      re-pointing as part of this move, not a parallel copy left behind.
      **Rationale, general to this codebase, not specific to this one class**: `org.apache.river.api.security`
      is a *shared* legacy package (standard-JDK-compatible, Apache River-heritage) that this project is
      progressively deprecating and emptying over time, with the end state being either removal or a
      package that contains only whatever must remain DirtyChai-only. Adding new classes there works
      directly against that trajectory. **This applies to all new work going forward, not just this one
      relocation** — `SubProcessDynamicPolicy` (part 2 below) and anything else new in this space belongs
      in the `au.zeus` namespace from the start; nothing new should be added to
      `org.apache.river.api.security`, only removed or migrated out of it.
   2. **New component: `SubProcessDynamicPolicy`.** A new service, calling `DynamicPolicyProvider.grant(...)`
      *inside the target subprocess JVM* — this is the concrete answer to gaps (a)/(c) above: not a
      whole-djinn pull-on-notification like `RemotePolicyService`, but a targeted, per-spawned-process
      push. Shape to work out during design, not resolved here: it needs to run reachably *from* wherever
      the SCAP verdict becomes known (the isolated process itself, once it completes its own codebase
      download — see point 3) *to* that same process's own local `DynamicPolicyProvider` — most naturally
      as a small in-process component the isolated JVM hosts alongside its `UdsServerEndpoint` (point 3),
      exported over the same UDS channel or a narrow sibling one, so the party driving the grant (plausibly
      the parent/orchestrating process, once it forwards the verdict) never needs direct access to the
      subprocess's memory or classpath — only a JERI call in, exactly the same shape as everything else
      this SOW already routes over UDS. Should still use `LeasedPermissionGrant`/`LeasedDelegation`
      underneath for the actual grant application (time-bounded/revocable, §12 point 6 above), and the
      relocated `au.zeus`-namespace `RemotePolicyProvider`/`RemotePolicyService` pair from part 1 may be
      directly reusable as `SubProcessDynamicPolicy`'s wire-level transport rather than inventing a new
      remote interface from scratch — worth checking at design time before building something new. Still
      needs (b)'s verdict-to-permission-set function from wherever this is called from; that piece remains
      unbuilt. Scope as its own task alongside point 3's wiring (same module family:
      `jgdms-pref-class-loader`, `services/policy-service`, `jgdms-platform/.../au/net/zeus/jgdms/api/policy`,
      and whatever new process-spawning code the isolated-JVM architecture introduces).

7. **Subprocess lifecycle — decided (Peter, 2026-07-17): reuse JERI's own DGC (Distributed Garbage
   Collection), don't invent new lifecycle management.** A per-principal isolated subprocess (point 1) is
   spawned on first need and torn down once **all references to the proxy objects it hosts have been
   released**, tracked via the same dirty-set/clean-call/leased-liveness DGC machinery JERI already uses
   for ordinary remote-object liveness (Birrell 1993, SRC-RR-116 — see this project's own reference to it
   as the model behind JERI/RMI DGC, `STD-008 §6`). This fits directly: each client-side local delegate
   stub (point 3, including the multiple-stubs-per-consumer shape in point 3(iii)) is, from the
   subprocess's perspective, just another remote reference needing the usual dirty-call/lease-renewal/
   clean-call lifecycle; once the last lease across all of a given subprocess's hosted objects expires or
   is explicitly released, the subprocess has no remaining reason to exist and can shut down. No new
   liveness-tracking mechanism is needed — this reuses an existing, mature primitive at a coarser grain
   (whole-subprocess teardown gated on the union of its hosted objects' DGC state, rather than per-object
   collection). Scope as part of point 3's wiring task: the subprocess-spawning/tracking logic needs to
   subscribe to (or poll) DGC state for everything it hosts, not just export objects normally.
8. **QA implications — flagged, not yet investigated.** Peter's initial assessment (2026-07-17): this
   architecture shift should be **mostly invisible to QA** — from a test's perspective a smart proxy still
   behaves like a smart proxy, just reached over UDS instead of in-process — but this needs dedicated
   investigation before being taken as settled, not assumed true by default. Candidate areas to check when
   that investigation happens: whether any existing QA harness relies on in-process proxy state inspection
   or reflection that would break once the proxy genuinely lives in a separate process; whether test
   doubles/mocks that currently substitute for the download/classload pipeline still apply once that
   pipeline moves into the subprocess (point 3); and whether DGC-driven subprocess teardown (point 7)
   introduces timing sensitivity a test harness needs to account for (e.g. a subprocess disappearing
   between test steps if leases aren't held explicitly). Not scoped as a task here — needs its own pass
   once points 3/6/7's wiring exists to investigate against something concrete rather than the design
   alone.

**Public-claim honesty note (carried from `SOW-BAE-Timing-Sidechannel-Denial.md` §1/§7's constraint):**
until the wiring in point 3 exists and is the default (or enforced) path for downloaded/mobile
smart-proxy execution, "mandatory" is a decision on record, not yet a deployed guarantee. Don't let
public-facing language describe this as already true of any running JGDMS deployment before it is.
