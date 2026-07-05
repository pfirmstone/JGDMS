# QUIC-TLS JERI Endpoint — Investigation Briefing + Decision Record

**Status:** investigation briefing **plus a ratified decision record**, 2026-07-05. JGDMS-side,
committable. §§0–5 are the investigation the review board critiqued; **§6 is the DECISION
RECORD** — the board's verdict and Peter's ratified decisions, and the authoritative "what was
decided and why" for this workstream. DirtyChai is **advise-only** (OpenJDK no-AI-contribution
policy) — every DirtyChai item here is a recommendation for the human maintainers, verified
read-only.

**Scope note:** Tasks 1–5 of the commissioning brief, plus the board's decision. Sections map:
§1 prior investigation (with source paths) · §2 current SSL-endpoint characterisation · §3
QUIC-TLS design surface + JERI mapping + security (incl. §3.2a pre-deletion mux audit) · §4
DirtyChai recommended-export list · §5 open questions the board adjudicated · **§6 DECISION
RECORD (ratified)** · §7 references.

---

## 0. TL;DR

The prior investigation is **substantial and largely complete**, not a blank slate. It reached
a firm verdict — **GO WITH CAVEATS on "Path A"** (JGDMS builds the QUIC transport on top of a
DirtyChai-exposed SunJSSE QUIC-TLS engine; TLS stays JSSE so the existing SPIFFE/mTLS auth
logic is reused unchanged). The dominant remaining cost is **relocated to JGDMS**: a
from-scratch RFC 9000/9002 QUIC *transport* (~30k LOC), because the JDK's own QUIC transport is
client-only and fully encapsulated. The DirtyChai side is small (one package export or facade +
a two-edit trust-manager relaxation). The board's real decision is **appetite for building and
owning a security-critical QUIC wire** vs. adopting a third-party stack (Kwik) with an auth
bridge, and the increment sequencing (does the `SSLEngine` keystone + UDS stepping-stone come
first?).

---

## 1. Prior investigation — what exists, where, and what it concluded

**The prior work was located in full.** It lives entirely in `JGDMS/JGDMS/docs/` as four
mutually-cross-referencing markdown documents (no dedicated STD-* spec exists yet; a keyword
sweep of the 89 docs and the whole tree found the QUIC design surface confined to these four).
DirtyChai's own repo carries no QUIC design doc — by explicit decision the artifact was placed
on the writable JGDMS side to avoid the DirtyChai AI-contribution-policy question.

| Document | Path | Role |
|---|---|---|
| **QUIC JERI Transport SOW** | `docs/SOW-QUIC-JERI-Transport.md` | The primary design SOW — thesis, phased plan, two architectures (A/B), de-risk spike |
| **DirtyChai QUIC-TLS Exposure SOW** | `docs/SOW-DirtyChai-QUIC-TLS-Investigation.md` | The handoff investigation brief into DirtyChai — the Q1–Q7 questions, the source landmines |
| **ADVICE (the verdict)** | `docs/ADVICE-quic-tls-exposure-2026-06-30.md` | The **completed** investigation: GO-WITH-CAVEATS, minimal DirtyChai change-set, Q1–Q7 answers with `file:line` evidence |
| **UDS JERI Transport SOW** | `docs/SOW-Unix-Domain-Socket-JERI-Transport.md` | The stepping-stone / prior-art transport; the shared `SSLSocket`→`SSLEngine` keystone |

### 1.1 The thesis (SOW-QUIC §0)

QUIC (RFC 9000) subsumes in one standardised protocol the three things JERI builds in separate
layers: **TLS** (`net.jini.jeri.ssl`), **multiplexing** (the hand-rolled
`org.apache.river.jeri.internal.mux` — confirmed present: `Mux`, `MuxClient`, `MuxServer`,
`Mux{Input,Output}Stream`, `Session`, `SocketChannelConnectionIO`), and **connection
management** over TCP. So QUIC is less "another transport" than an opportunity to **retire the
mux** and unify the stack.

### 1.2 The two candidate architectures (SOW-QUIC §3a/§3b/§5)

- **Path A — "clean" / DirtyChai (recommended):** DirtyChai exposes SunJSSE's *existing*
  QUIC-mode TLS engine; JGDMS builds the RFC 9000/9002 QUIC *transport* on it. TLS stays JSSE
  ⇒ the JGDMS `AuthManager` (custom X509 Key/Trust manager) + `SubjectCredentials` + SPIFFE
  matching are **reused directly** — no bridge.
- **Path B — Kwik:** adopt Kwik (pure-Java, server-capable QUIC) for transport **and** TLS, and
  **bridge** the JGDMS auth logic onto Kwik's non-JSSE TLS 1.3 (`agent15`). More external
  dependency, less new code, but the auth layer is re-hosted.
- (A `quiche`/BoringSSL native option is noted but disfavoured — it fights the pure-Java +
  Rust-static-musl "no native" story.)

### 1.3 The completed verdict (ADVICE-quic-tls-exposure-2026-06-30)

**GO WITH CAVEATS on Path A.** Verified against DirtyChai source (2026-06-30). Headlines:

1. **The QUIC-TLS engine is real, server-capable, and JSSE-based.** JEP 517 ("HTTP/3 for the
   HTTP Client API", delivered JDK 26; DirtyChai is 27) shipped a SunJSSE TLS-1.3-in-QUIC-mode
   engine. Interface `jdk.internal.net.quic.QuicTLSEngine`; factory `QuicTLSContext`
   (constructed from an `SSLContext`); impl `sun.security.ssl.QuicTLSEngineImpl` — **all in
   `java.base`**, cleanly separated from the HttpClient transport in `java.net.http`. The
   engine has explicit **server-mode** states and the mTLS surface (`setUseClientMode`,
   `getSSLParameters`/`setSSLParameters`, `getSession()` peer-certs). JEP 517's "client-only"
   limit is the **transport** (HttpClient), *not* the TLS engine.

2. **The one true blocker is load-bearing, but bounded.** A JGDMS custom-`AuthManager`
   `SSLContext` fails at two coupled sites:
   - `SSLContextImpl.isUsableWithQuic()` (`:485-486`) — `return trustManager instanceof
     X509TrustManagerImpl;` — a fail-fast gate at `QuicTLSContext` construction.
   - **and, deeper**, `sun.security.ssl.CertificateMessage` (~`:1228-1234` server-validates-
     client, `~:1289-1295` client-validates-server) hard-throws
     `CertificateException("QUIC only supports SunJSSE trust managers")` for any non-
     `X509TrustManagerImpl` trust manager, because the QUIC dispatch calls a **SunJSSE-internal**
     overload `X509TrustManagerImpl.check{Client,Server}Trusted(chain, authType,
     QuicTLSEngineImpl)` (`X509TrustManagerImpl.java:143-149`) that has **no equivalent on the
     public `X509ExtendedTrustManager` SPI** — `QuicTLSEngineImpl` is not a
     `javax.net.ssl.SSLEngine`. This is exactly JEP 517's "would require adding methods to the
     provider SPI" caveat, manifesting *inside* the engine's cert path.

   So relaxing the `isUsableWithQuic` `instanceof` alone does **not** work; the fix is that gate
   **plus** a `CertificateMessage` branch. That is caveat #1 (two coupled edits).

3. **The key-manager side is NOT gated (asymmetry).** No `"QUIC only supports SunJSSE"` throw
   and no `instanceof X509KeyManagerImpl` gate on the key path. Local-alias selection
   (`X509Authentication.java:315-337` server, `:221-243` client) falls back to the **standard**
   legacy `chooseServerAlias`/`chooseClientAlias(keyType, issuers, null)` for non-SunJSSE key
   managers. JGDMS implements its constraint-driven selection *in* exactly those legacy
   overloads (`ClientAuthManager.java:312`, `ServerAuthManager.java:333`) and its engine
   overloads forward to them with a `null` transport (`ClientAuthManager.java:378-379`,
   `ServerAuthManager.java:409-410`) — so **constraint-driven cert selection works unchanged in
   QUIC mode, no DirtyChai change** (Q2).

4. **Peer-cert extraction works both ends** (Q3): `QuicTLSEngineImpl.getSession()` returns a
   real `SSLSessionImpl`; `getPeerCertificates()`/`getLocalCertificates()`/`getPeerPrincipal()`
   populate on client and server, so JGDMS can build the authenticated `Subject`.

5. **0-RTT is unimplemented** (Q5): `KeySpace.ZERO_RTT` is enum-only scaffolding, no ZeroRtt key
   manager, `early_data` extension null-wired, entry points throw/return-false for `ZERO_RTT`;
   the record layer says outright "JDK does not support 0-RTT yet". So the **early-data replay
   hazard is moot today** — the requirement (never carry auth-bearing payload in 0-RTT) is
   satisfied by absence. **Forward caveat:** if a future rebase wires `early_data`, re-run the
   analysis (no explicit `disableEarlyData()` switch exists).

6. **The dominant cost is relocated to JGDMS** (Q6): the JDK QUIC *transport*
   (`jdk.internal.net.http.quic.*`, ~32k LOC / 83 files) is **client-only**
   (`QuicConnectionImpl.isClientConnection()` hardcoded `true` `:3712-3714`; no accept/listen;
   no `QuicServer`), **not exported at all**, so JGDMS must **build its own** QUIC transport.

7. **Top pre-Path-A risk (Q7): mTLS over QUIC *server* mode is wired but UNTESTED.** No JDK test
   sets client-auth on a QUIC server. Since JGDMS mTLS is mandatory, a client-auth QUIC
   handshake test is the first thing to write. Also `getDelegatedTask()` is a TODO no-op
   (returns `null`, `:757-758`), so handshake tasks run inline — acceptable on a virtual thread,
   but it qualifies any reliance on `NEED_TASK` offload.

### 1.4 Open threads the prior work explicitly left (SOW-QUIC §6, ADVICE §6)

- Sequencing: do the P1 `SSLEngine` migration now (independently valuable — banks the
  virtual-threads win) or gate it on the spike? (Prior recommendation: P1 regardless.)
- Endpoint selection across TCP/UDS/QUIC in a multi-endpoint proxy (tri-export, preference
  order, graceful degradation).
- QUIC idle-timeout + connection migration vs JERI connection reuse + DGC lifecycle.
- Does retiring the mux lose anything beyond multiplexing (its request framing, abort
  semantics, the `ServerConnection` model)? — audit before building.
- Facade vs. raw export (settled recommendation: a `java.base` facade in `au.zeus.jdk.net.*`).
- `getDelegatedTask()` no-op: does the vthread model need real task delegation?

**Nothing material was un-locatable.** The only "absence" is deliberate: there is no dedicated
STD-* specification for a QUIC transport, and no QUIC code — this is still pure design.

---

## 2. Current SSL endpoint — characterisation (verified against source)

All paths under `jgdms-jeri/src/main/java/`.

### 2.1 The JERI transport SPI (what a new endpoint must implement)

JERI layers a transport under the invocation stack via two public interfaces and a shared
connection SPI:

- **Public pair:** `net.jini.jeri.Endpoint` (client) + `net.jini.jeri.ServerEndpoint`
  (server). A transport is thin — `net.jini.jeri.tcp` is 3 classes (`TcpEndpoint`,
  `TcpServerEndpoint`, `Constraints`).
- **Request abstraction:** `Endpoint.newRequest(constraints)` →
  `OutboundRequestIterator` → `OutboundRequest` (each carries a request `OutputStream` + a
  response `InputStream`). Server side: a `RequestDispatcher` receives `InboundRequest`s.
- **Shared connection layer:** `net.jini.jeri.connection.*` — `Connection`,
  `ServerConnection`, `ConnectionManager`, `ServerConnectionManager`, `ConnectionEndpoint`,
  `In/OutboundRequestHandle`. Both `tcp` and `ssl` build on this.

**Crucially, `Connection`/`ServerConnection` are transport-shape-agnostic**
(`connection/Connection.java:46-75`, `connection/ServerConnection.java:47-76`): they expose
`InputStream getInputStream()`, `OutputStream getOutputStream()`, and an **optional**
`SocketChannel getChannel()` (returns `null` if no channel — the mux uses it for non-blocking
I/O when present). Per-request hooks (`writeRequestData`/`readResponseData`/
`processRequestData`, `checkConstraints`, `checkPermissions`, `populateContext`) are where auth,
constraint, and context wiring live. **This SPI does not bake in `Socket` or host:port** — it is
already the right shape for a channel- or stream-backed transport.

### 2.2 The SSL provider today — `SSLSocket`-based, blocking

- `SslConnection` (client) holds a **`volatile SSLSocket sslSocket`**
  (`SslConnection.java:117`), created via `sslSocketFactory.createSocket(plainSocket, host,
  port, autoClose)` layered over a `java.net.Socket` (`:250-255`). `getInputStream`/
  `getOutputStream` delegate straight to the socket (`:473-488`); **`getChannel()` returns
  `null`** (`:491-493`) — so the SSL path is blocking, one platform thread per connection.
- `SslServerEndpointImpl` mirrors this with ~14 `SSLSocket` references and the accept loop;
  `getClientSubject(SSLSocket)` extracts the peer chain from
  `session.getPeerCertificates()` (`:1652-1655`), and there is explicit "subject-neutral
  infrastructure thread (accept loop, mux reader)" handling (`:909`) plus `Subject.current()`
  / `callAs` wiring at the `MuxServer` boundary (`:1331`, `:1354`).
- **There is no `SSLEngine` anywhere in the SSL transport** — confirmed. The `SSLSocket →
  java.net.Socket` coupling is the whole obstacle to reusing this over a non-socket channel.

### 2.3 The durable asset — `SSLContext` + `AuthManager`, not `SSLSocket`

The identity/constraint machinery is JSSE-SPI callbacks, independent of how TLS is driven:

- `AuthManager extends FilterX509TrustManager implements X509KeyManager`
  (`AuthManager.java:52`); `FilterX509TrustManager extends X509ExtendedKeyManager implements
  X509TrustManager`. `ClientAuthManager`/`ServerAuthManager` add the constraint-driven
  `chooseClientAlias`/`chooseServerAlias` selection and `checkServerTrusted` validation.
- `Utilities.SSLContextInfo` bundles an `SSLContext` **initialised with those managers**; the
  socket factory is merely `sslContext.getSocketFactory()`. `Utilities` even carries a
  commented-out `// extends X509ExtendedKeyManager // Do we want or need to support?`
  (`Utilities.java:76`) — the engine-overload migration was already anticipated.
- `SubjectCredentials` maps cert ⇄ Subject; `Utilities.spiffePrincipalsFromCertificate` pulls
  SPIFFE `SpiffePrincipal`s from URI-SAN(s) in the peer cert. `SslConnection.populateContext`
  (`:496-552`) adds a TLS-verified `ServerSubject` (X.500 + SPIFFE principals) into the request
  context, guarded by `ContextPermission` — this is how the SPIFFE identity flows up to
  `PreferredProxyCodebaseProvider` for codebase-download grant scoping.

So **constraint-driven client-cert selection, peer validation, cert↔Subject mapping, and SPIFFE
matching are all JSSE-SPI callbacks invoked by the TLS engine** — identical whether TLS is
driven by `SSLSocket`, `SSLEngine`, or a QUIC-mode engine that accepts an `SSLContext`.

### 2.4 Constraint mapping (`ConnectionContext.java`)

The SSL provider maps JERI `InvocationConstraint`s onto TLS cipher-suite selection +
upper-layer flags (`ConnectionContext.java:264-330`):

- `Integrity.YES` → an **upper-layer** requirement (independent of the suite — `:272-277`),
  returning the `INTEGRITY` sentinel so codebase integrity is enforced above the transport;
  `ATOMICITY` similarly for atomic deserialization input validation.
- `Confidentiality.YES` → a non-`NULL`-cipher suite (`:278-280`); `ConfidentialityStrength.
  {WEAK,STRONG}` → suite strength (`:281-287`).
- `ClientAuthentication.YES` / `ServerAuthentication.YES` → suite + principal performs
  authentication (`:287-306`).
- `ClientMinPrincipal`/`ClientMaxPrincipal`/`ServerMinPrincipal` → X.500 principal selection,
  deliberately decoupled from suite negotiation ("does not support heterogeneous constraint
  alternatives … so principals/Integrity can be picked before negotiating the suite" `:48-51`).

### 2.5 What UDS did differently (the nearest prior-art transport)

The UDS SOW (`SOW-Unix-Domain-Socket-JERI-Transport.md`) is the closest model for adding a new
JERI transport. Its findings that carry directly to QUIC:

- **The connection layer fits a channel naturally** — UDS provides streams via
  `Channels.new{Input,Output}Stream(udsChannel)` and can return the raw `SocketChannel` from
  `getChannel()`.
- **The crux was the same `SSLSocket` coupling.** UDS has no `java.net.Socket`
  (`SocketChannel.open(UNIX)` only), so SPIFFE-over-UDS is **not** a socket-factory swap. Two
  options: **(i)** migrate the SSL record layer to `SSLEngine` over a channel (clean, bigger,
  the production path, unlocks non-blocking + virtual threads); **(ii)** a thin `java.net.Socket`
  -over-UDS-channel adapter (pragmatic prototype). Recommendation: prototype (ii), land (i).
- **Identity is layer-2 SPIFFE/mTLS**, byte transport swapped underneath; constraints reused
  unchanged; peer authenticated by **SPIFFE cert identity, not hostname** (no hostname
  verification), which is why the absence of a real `InetAddress` doesn't break auth. This
  hostname-independence is directly relevant to QUIC (see §3.6 endpoint-identification).

**Note the divergence for the board:** the recent *live* `net.jini.jeri.uds` inc-1 (per memory
`jgdms-uds-jeri-transport`) was built **plaintext + DER with SPIFFE/JWT identity at layer-2 and
deliberately NO SSLEngine** — a different structural choice from the SOW's SSLEngine keystone.
QUIC-TLS, by contrast, *must* run TLS 1.3 (RFC 9001 integrates it), so QUIC-TLS is closer to the
SOW's Path A / SSLEngine-keystone line than to the shipped UDS inc-1. The board should reconcile
which UDS lineage QUIC follows.

---

## 3. QUIC-TLS design surface + JERI mapping + security

### 3.1 What carries over, what is genuinely new

| Concern | TCP+TLS (`ssl`, today) | UDS (prior art) | **QUIC-TLS (new)** |
|---|---|---|---|
| Byte layer | `java.net.Socket` (blocking) | `SocketChannel(UNIX)` | **UDP `DatagramChannel`** inside a QUIC stack |
| TLS | `SSLSocket` record layer | `SSLEngine` over channel (SOW) | QUIC's **integrated TLS 1.3** (RFC 9001) via `QuicTLSEngine` |
| Auth/identity | `SSLContext`+`AuthManager` (JSSE) | same, over `SSLEngine` | **same** (Path A) — reused over the QUIC-mode engine, with the §4 relaxation as the *only* QUIC-specific auth difference |
| Multiplexing | hand-rolled `mux` over one connection | mux as today | **1 JERI request ⇄ 1 QUIC stream; retire the mux** |
| Connection object | `SslConnection` (`getChannel()`=null) | channel-backed `Connection` | a QUIC stream-pair behind `Connection`'s streams |
| Mobility | none | none | **connection migration** (roaming — the HaLOW mesh case) |
| Handshake | 2-RTT TCP + TLS | 1 channel + TLS | **1-RTT** (0-RTT disabled — §3.5) |

**Carries over:** the entire JERI `Endpoint`/`ServerEndpoint`/`Connection` SPI (already
channel-agnostic §2.1); the whole auth/identity/constraint layer (§2.3, Path A); everything
above the transport (`AtomicILFactory`, dispatchers, the STD-006 §7.2 reducing-context ACC block, DER,
DGC) is untouched — each phase is additive on the `Endpoint` SPI.

**Genuinely new:** the RFC 9000/9002 transport itself (packets, frames, streams, flow control,
loss detection, congestion control, pacing, retry, **connection migration**, an accept loop);
the stream↔request mapping; QUIC-specific security (UDP amplification, connection-ID/migration,
path validation, 0-RTT).

### 3.2 Stream ↔ request mapping (the core new design question)

QUIC gives many **independent bidirectional streams** per connection with no cross-stream
head-of-line blocking. The natural mapping: **one JERI request (one `OutboundRequest`/
`InboundRequest`) ⇄ one client-initiated bidirectional QUIC stream.** The request `OutputStream`
writes the stream's send side; the response `InputStream` reads its receive side. This
**retires the mux** — QUIC's transport-native multiplexing replaces `org.apache.river.jeri.
internal.mux` entirely.

Design consequences the board must weigh (SOW-QUIC §6.4):
- The mux today also provides request framing, abort semantics, and the `ServerConnection`
  request-dispatch model. A QUIC-stream mapping must reproduce these from QUIC primitives
  (stream FIN/RESET_STREAM for normal/abort completion; `processRequestData` per accepted
  stream). **Audit the mux for anything beyond multiplexing before deleting it.**
- `Connection`/`ServerConnection`'s current contract is streams for the *whole connection*; a
  QUIC endpoint instead has a stream *per request*. Either (a) implement `Connection` such that
  each `newRequest` opens a fresh QUIC stream and hands its streams to the `OutboundRequest`
  (bypassing the shared `ConnectionManager`/mux), or (b) keep the `ConnectionManager` but make
  its "connection" a QUIC-stream. Option (a) is cleaner and is the "retire the mux" path.
- Flow-control and stream limits (QUIC `MAX_STREAMS`) bound in-flight request concurrency —
  maps onto JERI connection reuse / back-pressure.

### 3.2a Pre-deletion mux audit — retiring the mux is NOT a pure deletion

The transport reviewer audited what the hand-rolled `org.apache.river.jeri.internal.mux`
actually carries beyond stream multiplexing. Several load-bearing behaviours are **not** free
side-effects of QUIC's native streams; they are transport-SPI obligations that must be
re-implemented on QUIC **before** any mux code is deleted. Deleting first = silent regressions.

- **[CRITICAL] AUDIT-1 — DGC acknowledgment contract.** The mux's `ackRequired` /
  `AcknowledgmentSource` fires an **application-level** acknowledgment when the receiver has
  *processed* a request — **not** when the stream merely reaches FIN. `BasicObjectEndpoint` (the
  DGC client) depends on this ack for **lease correctness**. QUIC stream FIN is a byte-level
  signal, not a "request processed" signal, so the ack must be **re-implemented on QUIC as an
  explicit app-level acknowledgment before any mux code is deleted**. Dropping it is a **silent
  DGC lease bug** (leases mis-renewed / prematurely expired). That the HTTP transport
  *independently* reimplements the same acknowledgment confirms this is a **transport-SPI
  obligation, not a mux quirk** — QUIC must carry it too.

- **[HIGH] AUDIT-2 — `getDeliveryStatus()` / partial-delivery / `ABORT_PARTIAL`.** The mux
  supplies the **at-most-once idempotency signal**: whether a request may have *started
  processing* before it aborted (`getDeliveryStatus()` / `partialDeliveryStatus` /
  `ABORT_PARTIAL`), which the invocation layer needs to decide whether a failed call is safe to
  retry. QUIC `RESET_STREAM` carries no "did the peer begin processing" bit. The transport must
  **define an application-error-code convention** (a QUIC application error code carrying the
  did-not-start vs. may-have-started distinction) to reconstruct this signal.

- **[HIGH] AUDIT-3 — half-close asymmetry.** The mux implements a Close-vs-Abort state machine
  handling asymmetric shutdown: **server done while the client is still writing**, and
  **early-response** (server responds before the request body is fully sent). This must be
  **ported onto independent per-direction QUIC stream FINs** — a QUIC bidirectional stream's two
  directions FIN independently, which maps the asymmetry directly, but the state machine
  (which side may still write, when Abort vs. orderly Close applies) must be reproduced, not
  assumed.

- **[MED] AUDIT-4 — flow-control rations.** **Delete the mux's flow-control rations entirely**
  and rely on QUIC `MAX_STREAM_DATA` / `MAX_DATA`. Do **not** stack a second application-level
  flow-control window on top of QUIC's — double-accounting deadlocks.

- **[MED] AUDIT-5 — `MAX_STREAMS` back-pressure vs. connection reuse.** QUIC `MAX_STREAMS`
  back-pressure interacts with **JERI connection reuse and the DGC idle-timeout**: exhausting the
  stream credit stalls new requests on a reused connection, and the interplay with when JERI
  considers a connection idle (and eligible for DGC-driven teardown) needs an **explicit
  policy** — not left to defaults.

**Gate:** AUDIT-1 and AUDIT-2 re-designs (the DGC app-level ack and the delivery-status
convention) MUST be designed and landed on QUIC **before any mux code is deleted.**

### 3.3 Virtual-thread fit

The `QuicTLSEngine` is engine-shaped (`consumeHandshakeBytes`/`getHandshakeBytes` per
`KeySpace`, a `NEED_TASK`/`getDelegatedTask` pattern like `SSLEngine`) and the `getChannel()`
non-blocking path is what the JGDMS virtual-thread direction wants (`no-threadlocal-virtual-
threads`). Caveats: `getDelegatedTask()` is a no-op today (inline-on-vthread is fine); the whole
transport event loop must avoid `ThreadLocal` (banned) and use `ScopedValue`/explicit passing.

### 3.4 Connection migration (mobility — HaLOW mesh federation)

QUIC connections survive an IP/port change (client roams networks) because they are keyed by
**Connection ID**, not the 4-tuple. This is a real win for the roaming instrument-federation /
HaLOW-mesh case (`spiffe-halow-mesh-federation`, `gls-instrument-federation`): a node keeps its
JERI connections across a network change. **Security tension to adjudicate (§3.6):** migration
must not become an identity-swap or an off-path injection vector — the peer identity is pinned by
the completed mTLS/SPIFFE handshake, and QUIC path validation (PATH_CHALLENGE/PATH_RESPONSE) must
gate any new path before it carries traffic.

### 3.5 0-RTT / early-data replay (flag → disable)

**0-RTT is replayable and not forward-secret.** JGDMS must **never** carry the STD-006 §7.2
reducing-context ACC block, or any auth-bearing / non-idempotent payload, in 0-RTT. Today this is
**moot by absence** — the DirtyChai engine does not implement 0-RTT (ADVICE Q5). **Recommendation
for the board: disable 0-RTT explicitly (fail-closed) even once available**, or gate it to
provably-idempotent calls only, and treat any future `early_data` wiring as a security-review
trigger.

### 3.6 QUIC-specific security surface (new vs TCP+TLS/UDS)

- **UDP amplification / anti-amplification (RFC 9000 §8):** an unvalidated client address lets an
  attacker use the server as a reflector. The server must enforce the **3× anti-amplification
  limit** before address validation, and should use Retry tokens. This is transport code JGDMS
  writes — it has no analogue in the TCP/UDS transports (TCP's handshake validates the path).
- **Path validation & connection migration (RFC 9000 §8–9):** PATH_CHALLENGE/PATH_RESPONSE must
  validate a new path before migrating; connection-ID rotation (RFC 9000 §5.1) resists linkage.
  Under SPIFFE, the authenticated identity is fixed at handshake — migration changes the *path*,
  not the *peer* — but the JGDMS transport must ensure a migrated path cannot be hijacked to
  splice a different peer onto an authenticated connection.
- **Endpoint-identification gap (from the §4 relaxation):** option (c)'s 2-arg trust path drops
  SunJSSE's engine-tied endpoint-identification (RFC 6125 / SNI hostname check) and
  algorithm-constraint enforcement. For JGDMS this loses nothing — its `AuthManager`
  self-validates by **SPIFFE/X.500 identity, not hostname** (§2.5), consistent with how the SSL
  and UDS transports already authenticate. The board must confirm JGDMS re-imposes (or knowingly
  forgoes) algorithm constraints, and that the DirtyChai branch is **fenced** so SunJSSE's own
  managers keep the strict path.
- **QUIC-stack maturity / disciplined-decoder:** owning the wire means owning its decode bugs
  (the blog-6 "disciplined decoder" caution at full force). This is the same posture JGDMS took
  for the DER codec — deliberate, but a real cost.
- **UDP middlebox reachability:** some networks throttle/block QUIC/UDP. Keep `SslEndpoint`/
  `TcpEndpoint` (and UDS local) as fallbacks; a proxy can carry multiple endpoints and degrade.

---

## 4. DirtyChai recommended export surface (ADVISE ONLY — read-only analysis)

**Constraint honoured:** DirtyChai's `CLAUDE.md` adopts the OpenJDK Interim Policy on Generative
AI — *analyze and advise only; humans write all source/JavaDoc/tests/build files*. The items
below are a **recommendation list for the human DirtyChai maintainers.** No DirtyChai source was
or will be modified. All `file:line` citations are into the DirtyChai repo
(`C:\Users\peter\Documents\GitHub\DirtyChai`, `src/java.base/share/classes/...`).

### 4.1 Recommended export list

| # | Item | Where | Necessity |
|---|---|---|---|
| 1 | Expose the QUIC-TLS engine to the JGDMS module | `module-info.java:197` (`exports jdk.internal.net.quic to java.net.http;` — add JGDMS module) **or** a facade (item 4) | **Necessary** (reachability) |
| 2a | Widen the fail-fast gate | `SSLContextImpl.isUsableWithQuic()` `:485-486` — accept any `X509ExtendedTrustManager` (JGDMS's `AuthManager` is wrapped in an `AbstractTrustManagerWrapper` on `init()`), not just `X509TrustManagerImpl` | **Necessary** (blocker part 1) |
| 2b | Add a custom-trust-manager branch in the QUIC cert dispatch | `CertificateMessage.java` server `~:1228-1234`, client `~:1289-1295` — for a non-`X509TrustManagerImpl` extended manager, validate **without** the `QuicTLSEngineImpl` (2-arg `checkServerTrusted(chain,authType)` **or** a thin `SSLEngine` adapter exposing `getHandshakeSession()`/`getSSLParameters()`) | **Necessary** (blocker part 2 — coupled to 2a) |
| 3 | *(No change needed)* key-manager side | `X509Authentication.java:315-337`/`:221-243` already falls back to standard legacy overloads | Not required |
| 4 | **Preferred: a supported facade** instead of raw `jdk.internal` export | new `au.zeus.jdk.net.ssl` (or `…net.quic`) in `java.base`, exported — a public `SSLContext → QUIC engine` factory hiding the `VarHandle`, the `sun.security.ssl` dependency, and bundling the item-2 relaxation behind a reviewed surface | **Recommended** (production) |
| 5 | *(Optional hygiene)* replace the `VarHandle` reach into `SSLContext.contextSpi` | `QuicTLSContext.java:60,133-150` — a package-private `SSLContextSpi` accessor | Advisory only |

**Namespace guidance (settled in the ADVICE):** put the facade in **`au.zeus.jdk.net.*`**
(java.base-only, DirtyChai's established convention beside `au.zeus.jdk.net.Uri`). **Do NOT**
use `org.apache.river.api.security` (it is the split-package landmine — 12 classes embedded in
`java.base` *and* in `jgdms-platform`; source of the `--release 21 → NoSuchMethodError` trap) and
**do NOT** squat `javax.net.ssl` (collides with a future upstream JEP exposing the QUIC-TLS SPI).

### 4.2 P2 security justification for the maintainers (why the relaxation preserves the threat model)

- It **widens an existing capability narrowly** — it does not remove a validation layer. SunJSSE's
  own `X509TrustManagerImpl` branch is untouched; only previously-*rejected* custom managers gain
  a path.
- The 2-arg path drops engine-tied endpoint-identification + algorithm-constraint enforcement.
  **For JGDMS this loses nothing** — `AuthManager` self-validates by SPIFFE/X.500 and ignores the
  connection object (it already forwards engine overloads with a `null` transport).
- **Fence** the new branch to non-`X509TrustManagerImpl` extended managers (and/or use the
  `SSLEngine`-adapter variant so endpoint-ID still runs), so no *other* caller silently loses
  validation. Fail-secure retained: an unrecognised manager still throws.

### 4.3 The most important pre-Path-A DirtyChai item (a test, human-written)

**mTLS over QUIC server mode is wired but untested** (ADVICE Q7). A DirtyChai human should add a
client-auth QUIC handshake test (two `QuicTLSEngineImpl`, server in server mode with client-auth
required, driven through a full mutual handshake, asserting both ends'
`getSession().getPeerCertificates()` populate). This is the single highest-priority item before
JGDMS relies on Path A.

---

## 5. Open questions for the board to adjudicate

1. **Path A (own the QUIC transport) vs Path B (Kwik + auth bridge) vs defer.** The TLS side is
   feasible and cheap either way; the real question is appetite for building/owning a
   security-critical RFC 9000/9002 wire (~30k LOC) vs. taking an external dependency and bridging
   auth onto a non-JSSE stack. The ADVICE recommends A; confirm.
2. **Increment / sequencing.** Do we land the **P1 `SSLEngine` migration of `net.jini.jeri.ssl`**
   first (independently valuable — banks virtual-threads, produces the one auth-over-engine asset
   UDS + QUIC + classic TLS all share), then a UDS stepping-stone, then QUIC? Or gate P1 on the
   spike? And **which UDS lineage does QUIC follow** — the SOW's SSLEngine keystone, or the
   shipped `net.jini.jeri.uds` inc-1 (plaintext+DER, layer-2 identity, no SSLEngine)? (§2.5.)
3. **Stream ↔ request mapping.** One request ⇄ one QUIC stream, retiring the mux — accept? And
   the pre-deletion **mux audit** (framing/abort/`ServerConnection` semantics beyond
   multiplexing) — who owns it? (§3.2.)
4. **Connection-migration security under SPIFFE.** Accept migration for the roaming/mesh case;
   ratify that identity is pinned at handshake and path validation gates new paths — and that a
   migrated path cannot splice a different peer. (§3.4, §3.6.)
5. **0-RTT policy.** Ratify **disable / fail-closed** (moot today by absence); treat any future
   `early_data` wiring as a mandatory security-review trigger. (§3.5.)
6. **Constraint mapping.** Confirm the SSL constraint set (`Integrity` as upper-layer,
   `Confidentiality`/`ConfidentialityStrength` → suite, `Client`/`ServerAuthentication`,
   principal min/max) maps onto QUIC's TLS 1.3 suites unchanged, and decide how
   algorithm-constraint enforcement (dropped by the 2-arg trust path) is re-imposed. (§2.4, §3.6.)
7. **DirtyChai vs JGDMS split of responsibilities.** DirtyChai (human-written): the item-1/2
   export+relaxation (facade preferred) and the item-4.3 mTLS-server test. JGDMS: the entire QUIC
   transport, the endpoint pair, the stream↔request mapping, the anti-amplification/path-
   validation/migration logic, and the QUIC variant of the matching test. Ratify the boundary and
   the facade-vs-raw-export decision (a maintenance-vs-coupling call reserved for the project
   lead).
8. **`getDelegatedTask()` no-op.** Does the vthread model need real task delegation, or is
   inline-on-vthread sufficient? (If the former, a second small DirtyChai item.) (§3.3, ADVICE Q7.)
9. **Tri-export & graceful degradation.** Endpoint selection across TCP/UDS/QUIC in a
   multi-endpoint proxy (UDP-blocked networks fall back to TCP; UDS only same-host). Generalises
   UDS SOW §7.

---

## 6. DECISION RECORD — 2026-07-05 (ratified by Peter)

The review board convened on the §§0–5 briefing; the decisions below are **ratified by Peter**
and are authoritative for this workstream. They supersede the "open question" framing of §5 —
each item resolves one or more of those questions.

### 6.1 Path A ratified; Path B rejected

**Path A is RATIFIED.** JGDMS owns the RFC 9000/9002 QUIC *transport* built on a
**DirtyChai-exposed SunJSSE QUIC-TLS engine**; **TLS stays in JSSE**, so the existing
SPIFFE/mTLS auth logic (`AuthManager` + `SubjectCredentials` + SPIFFE matching) is **reused
unchanged**.

**Path B (Kwik + a non-JSSE TLS bridge) is REJECTED.** Bridging JGDMS auth onto Kwik's
`agent15` TLS would **re-host the auth layer as a second, unaudited implementation of
security-critical trust logic on a TLS stack JGDMS does not control** — an unacceptable
duplication of the trust core. (Resolves §5 Q1.)

### 6.2 Re-sequenced plan ratified

- **P1 = the TRUE keystone: migrate `net.jini.jeri.ssl` from `SSLSocket` to
  `SSLEngine`-over-channel (over TCP), with the full SPIFFE regression suite.** This is the real
  keystone asset that classic TLS, UDS, and QUIC all share. **The shipped `net.jini.jeri.uds`
  inc-1 did NOT bank it** — it skipped `SSLEngine` for plaintext + layer-2 identity — so P1 is
  still owed and is done first.
- **P1.5 = the DirtyChai mTLS-over-QUIC-*server* handshake test** — the **cheapest Path-A
  disproof**. Must include a **POSITIVE** case (correct mutual handshake, both ends' peer certs
  populate) **and a NEGATIVE** case (a wrong-SPIFFE-identity peer is **rejected**).
- **P3 = QUIC**, built **only after P1 is green and P1.5 passes.**

(Resolves §5 Q2.)

### 6.3 Lineage settled

QUIC-TLS is the **`net.jini.jeri.ssl` sibling** — handshake-time mTLS/SPIFFE identity — **NOT**
the shipped-UDS layer-2-identity sibling. This is **forced by RFC 9001** (TLS 1.3 is integrated
into the QUIC handshake). A second, layer-2 identity stacked on the handshake identity would be
a **confused-deputy risk**. (Resolves §5 Q2's "which UDS lineage" and §2.5's reconciliation
note.)

### 6.4 Algorithm constraints re-imposed in JGDMS

The algorithm constraints that the 2-arg trust path (§4.2 / §3.6) would drop are **NOT
forgone.** Peter's decision: **JGDMS's own trust evaluation MUST enforce those algorithm
constraints.** (Resolves §5 Q6's algorithm-constraint half.)

### 6.5 DirtyChai export (advise-only for the human maintainers)

A **facade is REQUIRED for production** (not merely preferred): a **`au.zeus.jdk.net.ssl`**
package, **java.base-only, split-package-free**, exposing **only the `SSLContext` → QUIC-engine
factory + the JSSE session** — and **NOT** the engine type itself. A **raw
`jdk.internal.net.quic` export is SPIKE-ONLY** and must not ship. (Firms up §4.1 item 4 and its
namespace guidance from "Preferred/Recommended" into a production requirement.)

### 6.6 Normative STD-* QUIC-transport spec chartered

A **normative STD-\* QUIC-transport specification is CHARTERED** and **GATES the P3 build** —
QUIC is not built until the spec exists. (Resolves the §1.4 / §5 "no dedicated STD-* spec"
thread — the absence is now a scheduled deliverable, not a gap.)

### 6.7 Board conditions / gates

The build proceeds under these gates:

1. **Gate #1 = the mTLS-over-QUIC-*server* handshake test** (P1.5) — the first thing that must
   pass.
2. **Trust dispatch MUST prefer the `SSLEngine`-adapter / 3-arg path** (endpoint-ID + algorithm
   constraints still run) over the bare 2-arg path; the **fence** keeping SunJSSE's own managers
   on the strict path is **mechanized by a negative test.**
3. **0-RTT fail-closed by construction** — **no application data in 0-RTT, never negotiate
   `early_data`.** (Ratifies §3.5 / §5 Q5.)
4. **The migration identity invariant is normative** — a migrated QUIC path cannot splice a
   different peer onto an authenticated connection; identity is pinned at handshake, path
   validation gates any new path. (Ratifies §3.4 / §3.6 / §5 Q4.)
5. **Anti-amplification** (RFC 9000 §8) **plus a conformance / fuzz harness** are a **first-class
   deliverable**, not an afterthought.
6. **The §3.2a mux-audit re-designs (DGC app-level ack AUDIT-1, delivery-status AUDIT-2) are
   done BEFORE any mux code is deleted.** (Ratifies §3.2a's gate and resolves §5 Q3.)

### 6.8 Follow-on workstreams now in flight

- **DirtyChai location map** — branch `quic-tls-dirtychai-locations` (the read-only map of the
  DirtyChai edit sites).
- **STD-\* QUIC spec draft** — branch `std-quic-jeri-transport` (the chartered §6.6 spec).
- **P1 (the `SSLEngine` keystone)** — pending Peter's go.

---

## 7. References

- `docs/SOW-QUIC-JERI-Transport.md` — primary design SOW (thesis, §3a/§3b two architectures, §4
  phased plan, §5 de-risk spike).
- `docs/ADVICE-quic-tls-exposure-2026-06-30.md` — the completed DirtyChai investigation
  (GO-WITH-CAVEATS, minimal change-set, Q1–Q7 with source evidence).
- `docs/SOW-DirtyChai-QUIC-TLS-Investigation.md` — the handoff brief (Q1–Q7, source landmines).
- `docs/SOW-Unix-Domain-Socket-JERI-Transport.md` — the stepping-stone transport + the
  `SSLSocket`→`SSLEngine` keystone.
- Source (verified): `jgdms-jeri/src/main/java/net/jini/jeri/{Endpoint,ServerEndpoint,
  OutboundRequest,InboundRequest,RequestDispatcher}.java`; `.../connection/{Connection,
  ServerConnection}.java`; `.../ssl/{SslConnection,SslServerEndpointImpl,ConnectionContext,
  AuthManager,ClientAuthManager,ServerAuthManager,FilterX509TrustManager,Utilities,
  SubjectCredentials}.java`; `.../tcp/*` (template); `org/apache/river/jeri/internal/mux/*`
  (the mux to be retired).
- DirtyChai (read-only, advise-only per `DirtyChai/CLAUDE.md`):
  `src/java.base/share/classes/jdk/internal/net/quic/{QuicTLSEngine,QuicTLSContext}.java`;
  `sun/security/ssl/{QuicTLSEngineImpl,CertificateMessage,X509TrustManagerImpl,SSLContextImpl,
  X509Authentication}.java`; `module-info.java:197`.
- RFCs: **9000** (QUIC transport), **9001** (TLS for QUIC), **9002** (loss detection /
  congestion control). JEP **517** (HTTP/3 for the HTTP Client API — JDK 26).
- Memory: `jgdms-uds-jeri-transport`, `jgdms-spiffe-auth`, `no-threadlocal-virtual-threads`,
  `spiffe-halow-mesh-federation`, `gls-instrument-federation`, `dirtychai-ai-contribution-policy`,
  `dirtychai-build`, `dirtychai-security-model`.
