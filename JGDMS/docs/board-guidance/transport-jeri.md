# Board-Review Guidance — JERI Transport

*Distilled for future board members reviewing JERI transport work (new endpoints, the mux
retirement, QUIC/UDS/SSL, connection and stream mappings). Written by the JERI-transport
reviewer who did the UDS hardening, the pre-deletion mux audit, the server-init
investigation, and the STD-010 verdict. Every claim below is anchored to source you can
re-verify; do not trust the prose over the code.*

---

## 0. The one thing to internalise first

**A JERI transport is thin at the SPI but load-bearing in its contracts.** `net.jini.jeri.tcp`
is three classes. That thinness is a trap: it makes a new transport *look* like "swap the byte
layer, done." It is not. The `Endpoint`/`ServerEndpoint`/`Connection`/`ServerConnection` SPI
carries **invisible obligations** — at-most-once delivery, the DGC acknowledgment signal,
delivery-status-for-retry, per-direction half-close, request-origination directionality — that
are enforced *by the old implementation you are replacing*, not by the interface signatures. The
entire discipline of reviewing transport work is: **find every contract the old layer silently
honoured, and prove the new layer honours it too, before anything is deleted.**

If you remember nothing else: **byte-flow bidirectionality is not request-origination
bidirectionality, and neither is authorization directionality.** Those three are independent
axes, and every serious transport bug I found lived in the gap between them.

---

## 1. Recurring transport hazards / traps (with source anchors)

### 1.1 Request-origination direction is uni-directional even though byte-flow is not
A JERI connection carries bytes both ways, but **request origination flows client→server only.**
Evidence:
- `MuxClient.newRequest()` allocates a session ID and creates `new Session(this, sessionID,
  Session.CLIENT)` → `session.getOutboundRequest()` (`MuxClient.java:66-79`). **`MuxServer` has no
  `newRequest`** — it only ever calls `session.getInboundRequest()` reactively (`MuxServer.java`).
- `Session.getOutboundRequest()` asserts `role == CLIENT`; `getInboundRequest()` asserts
  `role == SERVER` (`Session.java:154,188`). Session IDs are **client-allocated**.
- **Trap:** a reviewer who checks "is the connection bidirectional?" (yes) and concludes "so the
  server can push a request back" (no, not in classic JERI) will mis-scope a QUIC stream mapping.
  The server side is *purely an `InboundRequest` dispatcher*.

### 1.2 The `Connection` / `ServerConnection` SPI asymmetry is deliberate and load-bearing
`ServerConnection` exposes only `processRequestData` + `InboundRequest` hooks
(`checkPermissions`/`checkConstraints`/`populateContext`) — **no `newRequest`, no
`OutboundRequest`** (`connection/ServerConnection.java:125,145,170,195`). The client-side
`Connection` is the only side that feeds `OutboundRequest`. **This asymmetry is the structural
proof of §1.1.** When you review a new transport, read *both* SPI interfaces and confirm the new
code respects the asymmetry — a server endpoint that grows an origination path is either a bug or
a genuinely new capability that needs its own security analysis (see the QUIC server-initiated
stream, §3.4).

### 1.3 The DGC acknowledgment contract — the highest-value, easiest-to-miss trap
The mux carries an **application-visible acknowledgment** distinct from byte delivery:
`sentAckRequired` / `receivedAcknowledgment` / `AcknowledgmentSource.Listener` /
`notifyAcknowledgmentListeners` (`Session.java:117-121,439-463,510-513`). It fires
`acknowledgmentReceived(true)` **only after the receiver's `RequestDispatcher` processed the
request**, not when the stream/data arrived.
- **Consumer: `BasicObjectEndpoint` — the DGC client** (`BasicObjectEndpoint.java:48` imports
  `AcknowledgmentSource`; the class is the DGC dirty/clean caller). DGC lease accounting depends on
  knowing a `clean`/`dirty` was *acted on*, not merely delivered.
- **Independent re-implementation by the HTTP transport** (`Request.java`, `HttpClientConnection`,
  `HttpServerConnection`) — this proves it is a **transport-SPI obligation, not a mux quirk**. Any
  new transport must reproduce it.
- **The trap:** a stream FIN (or TCP close, or QUIC FIN) means "all my bytes were delivered,"
  which is **strictly weaker** than "the far end processed them." Naively equating FIN with
  acknowledgment is a *silent* DGC lease-correctness bug — no crash, no test failure in the happy
  path, just slowly-wrong distributed GC. (This was the mux-audit CRITICAL finding; see War Story
  §5.1.)

### 1.4 `getDeliveryStatus()` — the at-most-once / retry-safety signal
`OutboundRequest.getDeliveryStatus()` (`OutboundRequest.java:181-197`) is how the **invocation
layer decides whether a failed call is safe to transparently retry**. The mux computes it
precisely: server-side abort sends `ABORT | ABORT_PARTIAL` (`Session.java:259-261`), the client
records `partialDeliveryStatus` on `handleAbort(partial)` (`Session.java:343`). `false` = "known
not processed, safe to retry"; `true`/unknown = "may have been processed, MUST NOT auto-retry."
- **Trap:** a new transport's abort primitive (QUIC `RESET_STREAM`, a socket reset) does **not**
  carry a standardized "did you start processing" bit. You must invent an explicit convention and
  wire it to `getDeliveryStatus()`, **fail-closed toward "maybe processed"** on ambiguity. Get it
  wrong and you either break at-most-once (retry a processed non-idempotent call) or over-suppress
  legitimate retries.

### 1.5 Per-direction half-close (the "early response" contract)
`OutboundRequest`'s javadoc promises the response is readable **while the request is still being
written** (`OutboundRequest.java:149-159`), and the mux implements the asymmetric
`Close`-instead-of-`Abort` dance so a server can finish and send its full response while the
client keeps writing (`Session.java:249-257,379-434`). A transport that couples the two
directions' end-of-stream state (tears down on receive-FIN, or buffers the response until request
FIN) **breaks this**. Confirm independent per-direction close.

### 1.6 Flow-control double-accounting
The mux has per-session rations (`inRation`/`outRation`, `IncrementRation`,
`Session.java:107-113,310-330`). A new transport with its own flow control (QUIC
`MAX_STREAM_DATA`/`MAX_DATA`, TCP window) must **delete the mux rationing entirely** — never stack
two credit schemes, which deadlocks. This is a "delete cleanly, don't half-migrate" hazard: the
danger is a *partial* retirement where some paths use QUIC credit and some still consult a ration.

### 1.7 Connection concurrency limits ⇄ request concurrency
The mux muxes unlimited concurrent requests over one connection; QUIC caps concurrency via
`MAX_STREAMS`. When the cap is hit the transport must define a policy (block/back-pressure into
`newRequest`, or open a second connection), and it interacts with connection reuse and DGC
lifecycle. Don't let this go unstated.

### 1.8 Connection-migration / identity pinning (new adversary class)
QUIC connections are keyed by Connection ID, not the 4-tuple, so a peer roams across IP changes
without teardown. The security invariant: **identity is pinned at the handshake; migration changes
the *path*, not the *peer*.** A migrated/validated path must complete `PATH_CHALLENGE`/
`PATH_RESPONSE` before carrying traffic, and a migrated path must not be able to splice a
*different* peer onto an authenticated connection. TCP/UDS never faced this; a QUIC review must.

### 1.9 Reachable-vs-ephemeral client topology (the reframe that changes everything)
See §3.3 and War Story §5.2. This is the deepest trap because it is *invisible in the code* — the
code works perfectly for reachable clients and the assumption is never written down.

---

## 2. Review heuristics — how to actually do the audit

### 2.1 The pre-deletion "what does the old layer do beyond its headline job?" audit
When new work says "retire/replace layer X" (mux, `SSLSocket` record layer, etc.), **do not review
the new layer in isolation.** Instead:
1. Read the *old* layer's core state machine end-to-end (for the mux that is `Session.java` — the
   per-request state machine, not `Mux.java` the framing). Enumerate **every** state transition and
   every message type it sends/handles.
2. For each, ask: "Is this pure {multiplexing / framing / byte-transport} — the headline job — or
   is it a *contract* the layer above depends on?" The tell for a hidden contract: it has an
   **application-visible consumer** (grep the message/interface name across the whole module).
   `AcknowledgmentSource` had 13 consumers including `BasicObjectEndpoint` and the HTTP transport —
   that breadth is the signal it's a contract, not an internal detail.
3. Produce a numbered list (AUDIT-1..N) with a severity and, for each, either "the new design
   reproduces it here" or "this will be silently dropped." **Nothing gets deleted until every item
   is either reproduced or its removal is explicitly documented and accepted.**
4. Insist the new spec makes "no half-retirement" a MUST — the worst outcome is some requests on
   the new path and some still framed by the old logic.

### 2.2 Spotting a hidden origination / reverse-request path
- Grep the server-side SPI for origination verbs (`newRequest`, `OutboundRequest`, `newCall`). Their
  **absence** on `ServerConnection`/`MuxServer` is the proof origination is client-only. Their
  presence anywhere unexpected is a finding.
- Trace every "callback"/"listener"/"event"/"notification" mechanism to the actual invocation. In
  JERI they *all* reduce to: the callee is an **exported `Remote` object**, and the notifier
  invokes it as an ordinary **client** call via `BasicObjectEndpoint.newCall` → `Endpoint.newRequest`
  (`BasicObjectEndpoint.java:490-491`). Confirm this per callback type (`RemoteEventListener`,
  `LeaseListener`, lookup `ServiceEvent`) — don't assume.
- Check `ServerContext`: it exposes *inbound* context only and **never** originates
  (`grep newRequest ServerContext.java` → zero hits). Good negative control.

### 2.3 Spotting a broken authorization assumption
The authz "caller" identity is sourced from **the connection's authenticated peer**:
`clientSubject = getClientSubject(sslSocket)` at handshake (`SslServerEndpointImpl.java:1514`),
injected via `populateContext` (`:1770`), consumed by
`BasicInvocationDispatcher.invokeWithClientSubject` / `checkClientPermission` (`:1163,1530`),
read back via `Util.getClientSubject()` (`:1573`). The standing assumption baked in everywhere is
**caller = client = connection-initiator**.
- **Heuristic:** whenever a design changes *who opens the stream/connection* relative to *who is
  the logical caller* — a server-push, a reverse stream, a relay — that assumption inverts and you
  have a confused-deputy risk. Ask: "on this path, whose authenticated identity does the dispatcher
  use, and is that the actual caller?" If the dispatcher would stamp the *client's* identity on a
  *server-originated* request, a remote service could act with the client's authority just by
  pushing over the client's connection. That is the attack; the fix is **direction-aware caller
  resolution** (caller = stream initiator, not connection initiator).

### 2.4 The "three independent axes" check
For any new transport, separately verify: (a) which directions bytes flow, (b) which direction(s)
*requests* originate, (c) which peer's identity *authorizes* a given request. Never let an answer
to one silently stand in for another. Most of my findings were an (a)⇒(b) or (b)⇒(c) conflation.

### 2.5 Read the javadoc contracts as normative, then check the implementation honours them
`OutboundRequest`/`InboundRequest` javadoc is unusually precise (at-most-once, early-response,
close-vs-abort, stream identity on repeat calls). It *is* the spec. Diff the new transport's
behaviour against each paragraph. The half-close and delivery-status contracts are written there in
plain English and are easy to violate.

---

## 3. JGDMS-specific gotchas

### 3.1 The mux role model is hard-asymmetric
`Session.CLIENT` originates, `Session.SERVER` dispatches, enforced by asserts
(`Session.java:154,188`). Client allocates session IDs. Do not reason about the mux as symmetric.

### 3.2 Listener callbacks = **new client connections with reversed roles** (classic model)
A `RemoteEventListener extends java.rmi.Remote` (`RemoteEventListener.java:38`) is *exported*; its
proxy bakes in a **reachable dial-back address** — `TcpEndpoint` serializes `host`+`port`
(`TcpEndpoint.java:98-102,134,141`) and its own javadoc says the host/port is "the remote address
**to connect to** when making socket connections" (`:74-75`). The notifying service invokes
`listener.notify(event)` as a fresh **client** call to that address. So "service notifies client"
is physically *the service acting as a client, dialing the client's exported server*.

### 3.3 Ephemeral clients ⇒ dial-back breaks ⇒ **server-initiated streams are mandatory**
The §3.2 model **assumes the client is reachable.** Under QUIC's topology the client/server split
is *topological* (server has a fixed IP; client is NAT'd/mobile/migrating — QUIC connection
migration exists precisely because client addresses move). The listener proxy's baked-in `host:port`
is then **unreachable**, and dial-back has no route. The only viable delivery is over the connection
**the client already opened**, via a **server-initiated QUIC bidi stream (`0x01`)**. Consequence,
and it is a *win*: **the client accepts inbound STREAMS, never inbound CONNECTIONS** — no accept
loop, no reachable port, no inbound firewall hole, no client-side `ServerEndpoint`. Make the
server-initiated model **primary**, dial-back a reachable-only compat path. (STD-010 §4.0/§4.6.)

### 3.4 On a server-initiated stream, the caller is the SERVER — authz inverts
The dispatcher (now the *client*) must authorize the pushed event against the **server's**
authenticated workload identity (available client-side from `getSession().getPeerCertificates()`,
handshake-pinned), **never** anonymous and **never** the client's own privileges (§2.3 attack).
Generalise `getClientSubject` → `getCallerSubject(streamInitiator)`. And the STD-006 §7.2 ACC
reducing-domain block *mirrors direction* on a server-pushed on-behalf-of event (server→client),
validated at the client against the server's identity — same gate, made direction-aware. (STD-010
§4.7, §4.7.1.)

### 3.5 The DGC-ack itself is direction-sensitive (subtle)
The ack contract (§1.3) is written "server emits after dispatch." On a `0x01` server-push stream
the **dispatcher is the client**, so an ack-required push would need the ack to ride the *client's*
send side. Today this is latent (only client-initiated DGC dirty/clean is ack-required; plain
`notify` is not), but any spec that hard-codes "server emits the ack" is inconsistent with a
symmetric caller model. Make the ack "the *dispatcher* emits after its `RequestDispatcher` returns."

### 3.6 Identity lineage: QUIC-TLS follows `net.jini.jeri.ssl`, NOT the shipped UDS inc-1
The shipped UDS inc-1 is plaintext + **layer-2** SPIFFE/JWT identity carried *inside request data*
via `writeRequestData`/`processRequestData`, with socket-file perms as the access gate — and
**deliberately no `SSLEngine`.** QUIC-TLS integrates TLS 1.3 (RFC 9001) and authenticates the peer
**at the handshake**, before request data. These are different layers of authentication.
- **Gotcha:** the "UDS is the SSLEngine stepping-stone" thesis assumed UDS would exercise
  `SSLEngine`-over-a-channel. The UDS that *actually shipped skipped SSLEngine.* So the shipped UDS
  did **not** bank the SSLEngine keystone QUIC needs. Do not credit it. The keystone
  (`SslConnection` `SSLSocket`→`SSLEngine`-over-channel; `getChannel()` returns `null` today,
  `SslConnection.java:491-493`) must land on its own (validated over TCP with the full SPIFFE
  regression) before QUIC.

### 3.7 The `getChannel()` optional-channel SPI is the seam that makes non-Socket transports possible
`Connection.getChannel()` returns an *optional* `SocketChannel` (the mux's non-blocking path). The
SSL transport returns `null` (blocking, `SSLSocket`-bound). UDS/QUIC return a real channel. This
optionality is *why* the SPI is already the right shape for channel-backed transports — but it also
means "does this transport support non-blocking I/O / virtual threads?" is answered by whether
`getChannel()` is non-null, and the `SSLSocket` coupling is the whole obstacle to reusing SSL over a
channel.

---

## 4. The principles that made these reviews effective

1. **See the topology, not just the code.** The single highest-leverage move was recognising that
   client/server is *topological* under QUIC (reachable vs ephemeral), which the code never states.
   Reshaping a design by seeing the deployment reality clearly beats any amount of line-by-line
   diffing. Always ask: "who can dial whom, in the real network this will run on?"
2. **Separate the axes.** Byte-flow direction ≠ request-origination direction ≠ authorization
   direction. Almost every real finding was a conflation of two of these.
3. **Fail-closed on ambiguity, always.** Delivery status unknown → "maybe processed" (no retry).
   Peer identity unprovable → reject. Can't confirm a socket is ours → don't unlink. The security
   default is refusal, and the review's job is to check the *default* branch, not the happy path.
4. **Nothing is deleted until its every contract is reproduced or its removal documented.** The mux
   retirement is safe only after the audit reproduces DGC-ack, delivery-status, half-close, and the
   server-caller path. "Half-retired" is the worst state.
5. **Grep for the consumer to find the contract.** An internal detail has no cross-module consumers;
   a contract does. Breadth of consumers = load-bearingness.
6. **The javadoc is the spec.** JERI's request/connection interfaces encode the hard guarantees in
   prose. Treat each paragraph as a normative requirement and check it.
7. **Be direct where you disagree with prior advice — including your own.** My "all requests are
   client-initiated, no reverse path needed" answer was *correct for reachable topology and wrong as
   QUIC guidance.* Say so plainly and supersede it. A review that won't overturn its own earlier
   conclusion is worthless.
8. **Anchor every claim to `file:line`.** Prose drifts; source doesn't. The board should be able to
   re-verify without trusting the reviewer.

---

## 5. War stories — the catch and the tell

### 5.1 The DGC-ack catch (mux audit, CRITICAL)
**Situation:** the QUIC design proposed "1 request ⇄ 1 QUIC stream, retire the mux," treating the
mux as pure multiplexing.
**The tell:** while reading `Session.java` end-to-end for the pre-deletion audit, the state machine
had message handlers that were clearly **not** about multiplexing — `handleAcknowledgment`,
`sentAckRequired`, `notifyAcknowledgmentListeners` (`Session.java:439-463`). A multiplexer does not
need an *application-visible acknowledgment*. That smell — "why does a mux have an ack protocol?" —
was the thread to pull.
**The catch:** grepping `AcknowledgmentSource` across the module found 13 consumers, including
`BasicObjectEndpoint` (the DGC client) and an *independent* re-implementation in the HTTP transport.
That breadth proved it was a **transport-SPI contract**, and the fact it fires only *after dispatch
processing* (not after byte delivery) proved that mapping it to QUIC stream FIN would be a **silent
DGC lease-correctness bug** — no crash, passing happy-path tests, slowly-wrong distributed GC.
**Lesson:** the most dangerous findings make no noise. A naive mux deletion would have shipped and
"worked." The tell was an internal mechanism whose *shape* (post-processing ack) didn't match its
supposed job (multiplexing).

### 5.2 The dial-back-breaks-for-ephemeral-clients reframe (the standout)
**Situation:** I had already answered the server-init question definitively: "all JERI requests are
client-initiated; callbacks are new client connections with reversed roles; the mux can be retired
without a reverse-request path." Code-correct, `file:line`-anchored, and — as QUIC guidance —
**wrong.**
**The tell:** the reframing prompt pointed at the *topology*: in QUIC/HTTP-3 the client address is
ephemeral by design (that's what connection migration is *for*). I re-read my own evidence:
`TcpEndpoint` bakes `host:port` into the listener proxy as the dial-back address
(`TcpEndpoint.java:98-102,74-75`). The instant you hold "callback = dial the client's baked-in
address" next to "the client has no reachable address," the classic model **collapses** — the
notifier literally cannot route to the client.
**The catch:** the correct mapping inverts — events must ride a **server-initiated stream** over the
connection the client already opened (WebTransport/gRPC-server-streaming precedent), the client
never accepts inbound connections, and the authorization caller **inverts** to the server's identity
(confused-deputy risk if you stamp the client's). This reshaped the entire QUIC event model and the
STD-010 §4.6/§4.7 normative text.
**Lesson:** the deepest bug was *invisible in the code* — the reachable-client assumption is never
written down because it's always been true. Only holding the code against the *deployment topology*
surfaced it. And: be willing to overturn your own correct-but-mis-framed conclusion the moment the
frame changes.

### 5.3 The UDS HIGH-1 leak (fail-closed vs fail-open under a security throw)
**Situation:** the UDS F1 hardening made `restrictPermissions` throw fail-closed if the owner-only
gate couldn't be applied.
**The tell:** the panel asked "does the throw leave anything behind?" I traced the `listen()`
try/finally: `bind` succeeded, then `restrictPermissions` threw — and closing a UDS
`ServerSocketChannel` does **not** unlink the pathname socket on Linux. So the fail-closed throw
left exactly the world-accessible control socket F1 existed to prevent. **Fail-closed on the gate
had a fail-open on the artifact.**
**The catch:** track `boundHere` and `deleteIfExists` the socket in the `!ok` branch — but only when
*we* bound it (never delete a live incumbent's file). And a subtler follow-on: on Windows the
`fileKey` is null, so the close-time "is this still our socket?" identity check needed an
owner-match + is-socket fallback rather than an unconditional path delete.
**Lesson:** a fail-closed *decision* can still leave a fail-open *side effect*. Check the cleanup
path of every security throw, on every platform (the Windows null-`fileKey` and null-`InetAddress`
cases repeatedly hid gaps the POSIX path didn't).

### 5.4 The Confidentiality over-claim (constraint honesty)
**Situation:** UDS `Constraints` claimed `Confidentiality.YES` as FULL_SUPPORT ("by locality").
**The tell:** the claim was made **statically, by the client endpoint, from a deserialized path**,
with no verification the far end was actually a local owner-only socket — and it stood even when the
server's owner-only gate hadn't been applied. Same over-claim class as a previously-removed
`Integrity.YES`.
**The catch:** a transport must not assert a security property it hasn't verified. Drop it to
`Confidentiality.NO` (byte-identical to plaintext TCP), defer confidentiality-by-locality to the
increment where `SO_PEERCRED`/SVID *actually verifies* the peer is local.
**Lesson:** a constraint claim is a *promise the object layer relies on to skip its own checks.* An
unverified transport self-description that can be forged by a deserialized value is worse than no
claim. Ask of every `FULL_SUPPORT`: "who verified this, and could a hostile serialized form lie?"

---

## 6. Quick reference — the transport reviewer's checklist

- [ ] Which directions do **bytes** flow? Which direction do **requests** originate? Which peer
      **authorizes** each request? (Three separate answers.)
- [ ] Does the new layer reproduce the **DGC AcknowledgmentSource** contract (post-*processing*
      ack, delivered via `AcknowledgmentSource`, never inferred from FIN)?
- [ ] Is `getDeliveryStatus()` wired to the abort primitive, **fail-closed to "maybe processed"**?
- [ ] Is **per-direction half-close** preserved (early response readable while request still
      writing)?
- [ ] Is old-layer **flow control fully deleted** (no double-accounting with the new layer's)?
- [ ] Any **server-caller / reverse** path? If so, is caller-subject **direction-aware** (caller =
      stream initiator, not connection initiator)? Confused-deputy guard explicit?
- [ ] **Reachable vs ephemeral** client topology stated? Does callback delivery survive an ephemeral
      client (server-initiated streams, client accepts streams not connections)?
- [ ] Connection **migration**: identity pinned at handshake, path validated before traffic, no
      peer-splice?
- [ ] Every **security throw**: does its cleanup path leave a fail-open artifact? Checked on every
      platform (Windows null-`fileKey`/null-`InetAddress`)?
- [ ] Every **constraint claim**: who verified it, and can a deserialized value forge it?
- [ ] **Nothing deleted** until every audited contract is reproduced or its removal documented, and
      **no half-retirement**.

---

*Anchors to re-verify (trunk):* `Session.java`, `MuxClient.java`, `MuxServer.java`,
`net/jini/jeri/{OutboundRequest,InboundRequest,BasicObjectEndpoint,BasicInvocationDispatcher}.java`,
`net/jini/jeri/connection/{Connection,ServerConnection}.java`,
`net/jini/jeri/ssl/{SslConnection,SslServerEndpointImpl}.java`,
`net/jini/jeri/tcp/TcpEndpoint.java`, `net/jini/core/event/RemoteEventListener.java`;
`docs/JGDMS-STD-010-QUIC-JERI-Transport-*.md` (§4.0/§4.2/§4.6/§4.7/§5).
