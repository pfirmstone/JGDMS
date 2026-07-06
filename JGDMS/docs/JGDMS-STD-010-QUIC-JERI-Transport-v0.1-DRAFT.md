# JGDMS-STD-010: QUIC-TLS JERI Transport

**Status:** Draft (working scaffold for review-board critique)
**Version:** 0.1-DRAFT
**Applies to:** JGDMS, DirtyChai (JDK fork), and non-JVM JGDMS participants that speak QUIC
**Depends on:** JGDMS-STD-003 (Multi-Subject Identity), JGDMS-STD-006 (Language-Neutral DER Wire Format) **≥ v0.12** — the version in which §7.2 became the codebase-only reducing-domain / `RemoteContextCodec` model. This standard's §4.7.1 ACC mirror and §9 depend on that model; the stale in-tree v0.10 (and earlier) copies of STD-006 do **not** satisfy this dependency.
**Normative external references:** RFC 9000 (QUIC transport), RFC 9001 (TLS for QUIC), RFC 9002 (loss detection / congestion control), RFC 8446 (TLS 1.3), JEP 517 (HTTP/3 for the HTTP Client API — the SunJSSE QUIC-TLS engine)
**Supersedes:** nothing (additive `Endpoint`/`ServerEndpoint` SPI transport alongside `tcp`, `ssl`, and `uds`)

> **Editorial note (v0.1-DRAFT):** This is a working scaffold prepared to **gate the
> P3 QUIC transport build** — the same "trust the documented artifact" rationale that
> STD-006 holds for the DER wire. A hand-owned, security-critical, cross-language wire
> (§3.6 of the investigation briefing: JGDMS builds its own RFC 9000/9002 transport,
> ~30k LOC) requires a normative specification before it ships, so that a non-JVM peer
> can interoperate with — and a security reviewer can audit — a JGDMS QUIC endpoint
> **from this document alone**. It does not describe implemented code: no QUIC
> transport exists in JGDMS at the time of writing (the prior work is pure design —
> four cross-referencing SOW/ADVICE documents, no STD-* spec and no code).
>
> Sections marked **[OPEN]** require a board decision or defer to a companion standard.
> Sections marked **[PROPOSED]** are design suggestions for discussion, not settled
> decisions. This draft takes the board's nine **pinned points** as normative and
> makes each auditable in isolation; where a pin required interpretation or is
> under-specified, an inline **[BOARD]** note flags it for follow-up (collected in §11).
>
> **Provenance.** Synthesised from `docs/quic-tls-jeri-endpoint-investigation.md` (the
> review-board briefing), `docs/SOW-QUIC-JERI-Transport.md`,
> `docs/ADVICE-quic-tls-exposure-2026-06-30.md`,
> `docs/SOW-DirtyChai-QUIC-TLS-Investigation.md`, and
> `docs/SOW-Unix-Domain-Socket-JERI-Transport.md`. **Correction carried into this
> standard:** the design SOWs cite the ACC reducing-domain transport as STD-006 "§7.3";
> the correct citation is **STD-006 §7.2** (§7.3 is `DigestCodeSourceRecord`). This
> standard cites §7.2 throughout and records the correction here so the discrepancy is
> not silently propagated.
>
> **Requirements language.** The key words **MUST**, **MUST NOT**, **SHOULD**,
> **SHOULD NOT**, and **MAY** are to be interpreted as described in RFC 2119 / RFC 8174
> when in capitals. "Fail closed" / "fail secure" means: on any ambiguity, breach, or
> unrecognised input, construct no object, carry no traffic, and refuse — never fall
> back to a permissive default.
>
> **Implementation vehicle (2026-07-06 decision).** The editorial note above assumes a
> from-scratch ~30k-LOC RFC 9000/9002 build. That assumption is **superseded** for the
> implementation: the transport is instead a fork of **kwik** (`github.com/pfirmstone/kwik`,
> fork of `ptrd/kwik`) whose bundled TLS stack (agent15) is replaced by the JDK's JSSE
> QUIC-TLS engine (`jdk.internal.net.quic.QuicTLSEngine`, JEP 517), so JGDMS's JSSE
> SPIFFE/X.500 auth is reused unchanged and traffic secrets stay inside the engine. The
> scope of that work is `SOW-Kwik-JSSE-QUIC-TLS-Transport.md` in the kwik fork root. **This
> standard is unchanged by that choice:** the normative wire format and behaviour below are
> implementation-independent and govern interop and audit regardless of which codebase
> carries them. Runtime target is DirtyChai only (like JGDMS): the fork depends on
> DirtyChai's trust-dispatch relaxation (B1/B2/B3) plus a qualified export of
> `jdk.internal.net.quic` to the `tech.kwik.core` module.

---

## 1. Purpose and Scope

This specification defines a **QUIC-TLS JERI transport**: a JGDMS `net.jini.jeri.Endpoint` /
`net.jini.jeri.ServerEndpoint` pair (proposed `net.jini.jeri.quic`) that carries JERI
invocation requests over QUIC (RFC 9000) with QUIC-integrated TLS 1.3 (RFC 9001). It
specifies the **wire-visible contract** of that transport — the parts a non-JVM peer must
reproduce and a security reviewer must audit — and nothing above the transport.

QUIC subsumes, in one standardised protocol, the three concerns JERI builds in separate
layers: TLS (the `net.jini.jeri.ssl` machinery), multiplexing (the hand-rolled
`org.apache.river.jeri.internal.mux`), and connection management over a reliable byte
stream. A QUIC transport therefore **retires the mux**: QUIC's transport-native stream
multiplexing replaces it. This is the load-bearing structural change and the source of
most of the pinned points below.

The goals are:

- **Cross-runtime participation and audit-from-the-document.** A non-JVM QUIC
  implementation (Rust `quinn`, Go `quic-go`, C `quiche`/`ngtcp2`+`nghttp3`, Mozilla
  `Neqo`) can interoperate with a JGDMS QUIC endpoint using only RFC 9000/9001/9002 plus
  this standard, and a reviewer can reason about the security properties without reading
  JGDMS source. Interoperability is otherwise unverifiable (§10).
- **Retire the mux without losing its contract.** The stream↔request mapping (§4)
  reproduces the mux's request framing, completion, and abort semantics from QUIC stream
  primitives, and the DGC acknowledgment signal (§5) that `BasicObjectEndpoint` relies on
  for lease correctness.
- **Preserve the JGDMS identity and authorization model unchanged above the transport.**
  The identity model is handshake-time mTLS/SPIFFE (§9); the STD-006 §7.2 ACC
  reducing-domain block, DER payloads (§8), the DGC lifecycle, and `AtomicILFactory`
  ride over QUIC exactly as over TCP/SSL and UDS. Each phase is additive on the
  `Endpoint` SPI.
- **Fail closed on the QUIC-specific attack surface.** 0-RTT early data (§6),
  UDP amplification (§7), and connection migration identity (§6-migration) are the new
  surfaces relative to TCP+TLS; each is pinned to a fail-closed rule.

This specification does **not** define: the QUIC congestion controller or loss-recovery
algorithm (RFC 9002 governs those, and they are not wire-visible in a way a peer must
match beyond RFC conformance); the DirtyChai QUIC-TLS engine exposure (that is a
DirtyChai maintainer decision — see the ADVICE document, summarised in §9.4 as context
only); the `@AtomicSerial` validation semantics (STD-001), the identity model internals
(STD-003), or the DER object encoding (STD-006), each of which it depends on unchanged.

**[BOARD] Path selection is out of scope but assumed.** This standard specifies the wire
contract independently of whether the TLS engine is DirtyChai's exposed SunJSSE
QUIC-TLS engine (investigation "Path A", recommended) or a third-party stack such as Kwik
with an auth bridge ("Path B"). Both paths MUST produce the wire behaviour specified here;
the choice affects only where the JGDMS auth logic is hosted, not what appears on the wire.

---

## 2. Motivation

### 2.1 Why a normative spec gates the build

STD-006 established the discipline: a hand-owned, security-critical, cross-language wire
is specified normatively **before** it ships, because "interop is unverifiable without a
normative spec" and because the security properties must be auditable from the document.
The QUIC transport is exactly that class of artifact — arguably more so, because unlike
the DER object layer (which rides an existing reliable transport) the QUIC transport is
itself the security-critical wire: packet protection, flow control, path validation,
amplification limits, migration, and the stream state machine are all JGDMS's to
implement correctly. The investigation briefing sizes it at ~30k LOC of new
RFC 9000/9002 transport and repeatedly invokes the "disciplined decoder" caution at full
force. A spec that pins the wire-visible contract is the gate that lets the build proceed
against a fixed, reviewed target rather than an emergent one.

### 2.2 What QUIC changes relative to the SSL and UDS transports

| Concern | TCP+TLS (`ssl`, today) | UDS (prior art) | **QUIC-TLS (this standard)** |
|---|---|---|---|
| Byte layer | `java.net.Socket` (blocking) | `SocketChannel(UNIX)` | **UDP `DatagramChannel`** inside a QUIC stack |
| TLS | `SSLSocket` record layer | `SSLEngine` over channel / layer-2 | QUIC-integrated **TLS 1.3** (RFC 9001) |
| Auth / identity | `SSLContext` + `AuthManager` (JSSE) | SPIFFE/mTLS or layer-2 | **handshake-time mTLS/SPIFFE** (§9) — the `net.jini.jeri.ssl` lineage |
| Multiplexing | hand-rolled `mux` over one connection | mux as today | **1 JERI request ⇄ 1 QUIC bidirectional stream; mux retired** (§4) |
| Callback / event push | dial-back: fresh client call to listener's exported `host:port` | dial-back | **server-initiated stream (`0x01`) over the client's open connection** (§4.6); dial-back kept as compat |
| Caller authorization | caller = client (connection peer) | caller = client | **direction-aware: caller = stream initiator** (§4.7); server-initiated ⇒ caller = authenticated server peer |
| Request completion | mux framing | mux framing | **stream FIN** per direction (§4.3) |
| Request abort | mux abort | mux abort | **`RESET_STREAM` with an application error code** (§4.4) |
| DGC ack signal | mux `AcknowledgmentSource` | mux | **application-level ack after dispatch** (§5) |
| Flow control | TCP window | channel | **QUIC `MAX_STREAM_DATA` / `MAX_DATA`** (§9.5) |
| Mobility | none | none | **connection migration, path-only** (§6-migration) |
| Handshake | 2-RTT TCP + TLS | 1 channel + TLS | **1-RTT; 0-RTT PROHIBITED** (§6-0rtt) |
| Amplification | (path validated by TCP handshake) | (local) | **RFC 9000 §8 3× limit + Retry** (§7) |

**Carries over unchanged:** the whole JERI `Endpoint`/`ServerEndpoint`/`Connection` SPI
(already transport-shape-agnostic); the JGDMS identity/constraint layer; and everything
above the transport — `AtomicILFactory`, the invocation dispatchers, the STD-006 §7.2
reducing-context ACC block, the DER object encoding, and the DGC lifecycle.

**Genuinely new (this standard's subject):** the bidirectional stream↔request mapping (§4),
including **server-initiated event push** (§4.6) and **direction-aware caller-subject
resolution** with the ACC mirror (§4.7); the DGC acknowledgment carriage (§5), half-close
(§4.5), 0-RTT prohibition (§6), the connection-migration identity invariant (§6-migration),
anti-amplification (§7), and the QUIC-specific flow-control reliance (§9.5).

---

## 3. Design Principles

These govern every normative statement below; they are the QUIC-specific extension of the
STD-006 principles (fail-secure decode, values-not-behaviour, bounded-before-allocation).

1. **The wire contract is auditable from this document alone.** Anything a peer must
   reproduce or a reviewer must check is stated here, not left to JGDMS source. Where an
   RFC already mandates a behaviour, this standard cites it as a conformance requirement
   rather than restating it.
2. **Fail closed by construction, not by absence.** Where a QUIC feature is a hazard
   (0-RTT, §6), the endpoint MUST refuse to negotiate it — the refusal MUST NOT depend on
   the underlying engine happening not to implement it. A feature that becomes available
   in a future engine rebase MUST NOT silently become reachable.
3. **Identity is pinned at the TLS 1.3 handshake; the transport only moves bytes.**
   Migration changes the path, never the peer (§6-migration). The additive principal
   stamped at the receiver is always the carrying connection's handshake identity (§9).
4. **QUIC bounds bytes; the decoder still bounds elements.** QUIC flow control limits
   bytes in flight, not decoded object counts. The STD-006 size-bound-before-allocation
   discipline (`maxCollection` et al., §8) is undiminished by QUIC and MUST be retained.
5. **Byte-identical behaviour across runtimes.** JVM and non-JVM QUIC endpoints MUST make
   the same stream-completion, abort-code, ack, migration, amplification, and
   flow-control decisions for the same logical exchange, so that the retry contract (§4.4)
   and the DGC lease correctness (§5) hold uniformly.
6. **No application-level re-implementation of a QUIC transport service.** Where QUIC
   already provides a service (multiplexing, flow control, path validation, ordered
   reliable stream delivery), the transport relies on it and MUST NOT layer a parallel
   mechanism (§9.5 forbids application-level flow-control rationing).

---

## 4. Stream ↔ Request Mapping (PINNED POINT 1)

### 4.0 Two request directions on one connection (NORMATIVE overview)

A JERI request is a directed exchange (a caller invokes a dispatcher). QUIC gives four
stream types (RFC 9000 §2.1) discriminated by the two least-significant bits of the stream
ID: **client-initiated bidirectional `0x00`**, **server-initiated bidirectional `0x01`**,
client-initiated unidirectional `0x02`, server-initiated unidirectional `0x03`. This
standard uses the two **bidirectional** types and maps **request direction onto stream
initiator**:

- **Client-initiated bidirectional stream (`0x00`)** carries a request whose **caller is the
  client** — the ordinary case: application invocations, DGC dirty/clean calls, and the
  *dial-back* form of callbacks (§4.6, secondary path). This is §4.1.
- **Server-initiated bidirectional stream (`0x01`)** carries a request whose **caller is the
  server** — event/callback **push** to an ephemeral client, over the connection the client
  already opened. This is §4.6, the primary callback model.

The caller of a request is therefore **the party that opened the stream**, and authorization
follows the stream initiator, not the connection initiator (§4.7 — the direction-aware
caller-subject resolution, the must-solve). Both directions reuse the same per-request
machinery of §§4.3–4.5 (FIN completion, `RESET_STREAM` abort, half-close): a
server-initiated stream is a full JERI request with request body, response body, FIN, and
reset semantics, merely opened by the server.

**Resolution of the former §4.1 [OPEN].** An earlier draft left "does JERI have a
server-initiated request path?" open, pending the mux audit. **The investigation resolved it
definitively:** JERI *does* have a server→client request path, but today it is implemented
as **dial-back** — the listener/callback proxy bakes in a reachable `host:port`
(`net.jini.jeri.tcp.TcpEndpoint`), and the notifying service invokes the listener as a
**fresh client call** (`BasicObjectEndpoint.newCall` → `newRequest` — a new
client-initiated connection *back to the client's exported endpoint*). That works only when
the client is reachable at a fixed address. QUIC's ephemeral-client topology — a NAT'd,
mobile, or migrating client behind no reachable port, talking to a fixed-address server —
**voids dial-back**: the client's baked-in address is unreachable. This standard therefore
maps the event/listener pattern onto **server-initiated streams** (§4.6) as the primary
model, keeps dial-back as a secondary/compat path (valid only for reachable, fixed-IP
clients), and the mux (§4.2) retires only once **this** server-initiated path is specified —
which it now is.

### 4.1 Client-initiated requests — the one-to-one mapping (NORMATIVE)

A JGDMS QUIC transport **MUST** map exactly **one client-caller JERI request to exactly one
client-initiated QUIC bidirectional stream (`0x00`)**. One `OutboundRequest` (client) /
`InboundRequest` (server) corresponds to one and only one such stream opened by the client:

- The request `OutputStream` (`OutboundRequest.getRequestOutputStream`) writes the
  **send side** of that stream.
- The response `InputStream` (`OutboundRequest.getResponseInputStream`) reads the
  **receive side** of that stream.
- On the server, the accepted stream's receive side is the `InboundRequest` request body
  and its send side is the response body.

An endpoint **MUST NOT** multiplex more than one JERI request onto a single QUIC stream,
and **MUST NOT** split one JERI request across multiple streams. Stream identifiers follow
RFC 9000 §2.1. This is the "retire the mux" mapping: QUIC's independent,
head-of-line-blocking-free streams replace `org.apache.river.jeri.internal.mux` wholesale —
for both stream directions (§4.6 covers the server-initiated one).

### 4.2 Mux-retirement audit (NORMATIVE precondition)

Before the mux is deleted, an implementation **MUST** audit `org.apache.river.jeri.internal.mux`
for any contract beyond multiplexing that this standard does not otherwise reproduce —
specifically its request framing, its abort semantics, its `AcknowledgmentSource`
signal (reproduced in §5), and the `ServerConnection` request-dispatch model — and **MUST**
reproduce every such contract from QUIC primitives or document its deliberate removal. A
QUIC endpoint MUST NOT ship with the mux "half-retired" (some invocations over QUIC streams,
some still framed by mux logic). The mux may be retired **only** once **both** request
directions are specified — client-caller (§4.1) and server-caller event push (§4.6) — with
direction-aware caller-subject resolution (§4.7); this standard now specifies both, closing
the precondition that formerly held the mux audit open.

### 4.3 Normal completion — stream FIN (NORMATIVE)

Normal completion of each direction is signalled by the QUIC stream **FIN** bit (RFC 9000
§2.2, §3.2), not by any application sentinel:

- The client sends **FIN** on its send side to mark the **request body complete**. After
  FIN the client MUST NOT write further request bytes on that stream.
- The server sends **FIN** on its send side to mark the **response body complete**. After
  FIN the server MUST NOT write further response bytes on that stream.

A well-formed, fully processed request therefore terminates with FIN in **both** directions.
Receipt of FIN on the receive side means "the peer has finished writing this
direction's body"; it does **not**, by itself, mean the request was processed — that is the
DGC acknowledgment's job (§5), and the two MUST NOT be conflated.

### 4.4 Abort — `RESET_STREAM` with a defined error-code convention (NORMATIVE)

An abort of a request is signalled by QUIC **`RESET_STREAM`** (RFC 9000 §3.2, §19.4),
carrying an **application protocol error code**. This standard defines a JGDMS QUIC
application-error-code convention that is **load-bearing for the at-most-once retry
contract** and MUST be implemented identically by every conformant endpoint. The codes are
wired to `OutboundRequest.getDeliveryStatus()` (RFC/JERI semantics: `false` means the
request is known **not** to have been processed and is therefore safe to retry under
at-most-once; `true`/unknown means it may have been processed and MUST NOT be silently
retried).

**[PROPOSED] Application error code registry** (values are QUIC application error codes,
a 62-bit varint per RFC 9000 §19.4; the JGDMS range and exact numbers are **[OPEN]** pending
allocation — see §11):

| Symbolic code | Meaning | `getDeliveryStatus()` mapping |
|---|---|---|
| `JGDMS_NO_ERROR` | Not an abort; graceful (use FIN, not RESET). Reserved so `0` is never an implicit "processed". | n/a |
| `JGDMS_REQUEST_NOT_PROCESSED` | The receiver aborted **before** the `RequestDispatcher` began processing (queue-full, admission-refused, decode-refused pre-dispatch, shutdown before dispatch). The request had **no** side effects. | `getDeliveryStatus()` → **`false`** (safe to retry: not-yet-processed) |
| `JGDMS_REQUEST_MAYBE_PROCESSED` | The receiver aborted **after** the `RequestDispatcher` began processing (dispatch in flight, partial response, mid-processing failure). Side effects may have occurred. | `getDeliveryStatus()` → **`true`** (partially processed: MUST NOT auto-retry) |
| `JGDMS_CLIENT_ABORT` | The client aborted its own request (cancellation, timeout, caller-driven). | Client-side; server treats the dispatch as cancelled. |

**Normative rules:**

1. A receiver that aborts a stream **MUST** select `JGDMS_REQUEST_NOT_PROCESSED` **only**
   when it can guarantee the request produced no application-visible side effect — i.e. the
   `RequestDispatcher` had **not** been entered for that request. If it cannot make that
   guarantee, it **MUST** use `JGDMS_REQUEST_MAYBE_PROCESSED` (fail-closed toward "maybe
   processed"). Ambiguity resolves to the retry-unsafe code.
2. A client receiving `RESET_STREAM` **MUST** map the carried code to
   `getDeliveryStatus()` per the table, and the invocation layer's at-most-once retry logic
   **MUST** honour it: only a `false` delivery status permits transparent retry.
3. `STOP_SENDING` (RFC 9000 §3.5) sent by a peer that no longer wishes to read a direction
   requests the sender to `RESET_STREAM` that direction; the reset that follows carries the
   convention above. A bare `STOP_SENDING` without a subsequent classifiable `RESET_STREAM`
   MUST be treated as `JGDMS_REQUEST_MAYBE_PROCESSED` (fail-closed).
4. The `0` code (`JGDMS_NO_ERROR`) MUST NOT be used to abort a request that may have been
   processed; graceful completion is FIN (§4.3), never a zero-code reset.

**[BOARD] Interpretation note.** The pin says "partially processed vs not-yet-processed";
the JERI `getDeliveryStatus()` contract is binary (`false` = definitely not processed).
This standard therefore maps "not-yet-processed" → `false` and "partially processed" →
`true`, and treats "partially processed" and "fully processed but reset before FIN" as the
same retry-unsafe class, since JERI cannot distinguish them and both are unsafe to retry.
If the board wants a finer-grained "fully processed, response lost" signal (distinct from
"partially processed"), that is the DGC ack's domain (§5), not the reset code — flagged in
§11.

### 4.6 Event / callback delivery — server-initiated streams (NORMATIVE)

The JERI event/callback pattern — `net.jini.core.event.RemoteEventListener.notify`,
lease-renewal / expiration-warning notifications, lookup-service `ServiceEvent` delivery,
and any service→listener push — is a **server-caller request**: the notifying service is
the caller, the client-held listener is the dispatcher. This standard maps it onto QUIC as
follows. The connection these pushes ride is kept alive as a **leased resource** — see **§6.4
Connection liveness**, which pins how the client-opened connection resists QUIC's idle timeout
via Jini leasing rather than a separate keepalive.

#### 4.6.1 Primary model — server-initiated bidirectional stream (NORMATIVE)

**Push-primary default (F4, NORMATIVE).** Server-initiated-stream push (§4.6.1) is the
**default** callback model for **every** client, not only ephemeral ones. Dial-back (§4.6.2)
is used **only** when the listener-holder has **explicitly advertised a reachable
`ServerEndpoint`** for the listener (§4.6.2). An implementation **MUST NOT** default to
dial-back: absent an explicit reachability advertisement, the callback rides a
server-initiated `0x01` stream. This prevents implementers from silently selecting the
classic path, which is broken for the ephemeral (NAT'd, mobile, migrating) topology.

For an **ephemeral client** (NAT'd, mobile, migrating, or otherwise not reachable at a fixed
address), event delivery **MUST** use a **server-initiated bidirectional QUIC stream
(`0x01`)** opened on the connection **the client already established** to the service. The
notifying service opens the `0x01` stream; the JERI request rides it exactly as a
client-initiated request rides a `0x00` stream (§4.1, §4.3–4.5): request body = the event
invocation (the marshalled `RemoteEvent` / notify arguments), response body = the listener's
return/exception, FIN per direction, `RESET_STREAM` per §4.4. The client's decode of that
pushed request body is a **DER decode surface subject to the STD-006 §4.5 ceilings in full**
(§8.2) — peer authentication does **not** exempt the pushed payload from size-bounding.

**The client accepts inbound STREAMS, never inbound CONNECTIONS.** A conformant client
endpoint **MUST** accept server-initiated streams on connections it opened, and **MUST NOT**
require (and SHOULD NOT run) any inbound-connection accept loop, reachable listen port, or
inbound firewall opening to receive events. This is both a **security win** (no inbound
attack surface on the client; no dial-back address leaked or reachable) and a **simplicity
win** (no client-side `ServerEndpoint` export for callbacks). It is the reason
server-initiated streams are the primary model rather than an optimisation: for the
ephemeral topology, dial-back (§4.6.2) is *impossible*, not merely slower.

**Stream credit as back-pressure (F3, NORMATIVE).** Server-initiated-stream concurrency is
governed by the QUIC `MAX_STREAMS` (server-initiated bidirectional) limit **advertised by
the client** (RFC 9000 §4.6, §19.11). The client **MUST** advertise credit **greater than
or equal to its outstanding subscriptions** (§4.6.3), so a subscribed event always has a
stream to arrive on. When that credit is **momentarily exhausted**, the server **MUST BLOCK**
the pushed event (await a `MAX_STREAMS` increase) and **MUST NOT drop** it: the client's
`MAX_STREAMS` extension is the back-pressure signal, consistent with §9.5 (rely on QUIC flow
control; no application-level rationing). Blocking-not-dropping preserves at-most-once event
delivery under transient credit exhaustion.

**Precedent.** Server-initiated bidirectional streams for server→client push over a
client-opened connection are established practice: **WebTransport** (server-initiated
streams over an HTTP/3 QUIC connection) and **gRPC server-streaming / bidirectional
streaming** (the server pushes messages over the client-opened HTTP/2/HTTP/3 connection)
are the direct analogues. JGDMS uses raw QUIC streams (§10.2) rather than HTTP/3, but the
topology — client opens the connection, server pushes over it — is identical.

#### 4.6.2 Secondary / compat model — dial-back (NORMATIVE, restricted)

The classic JERI **dial-back** callback — the notifying service makes a **fresh
client-initiated connection** to the listener's exported endpoint (`BasicObjectEndpoint.
newCall` → `newRequest` against the listener proxy's baked-in `host:port`) — is permitted
**only when the listener-holder has explicitly advertised a reachable `ServerEndpoint`** for
that listener (F4, §4.6.1 push-primary default), i.e. only for fixed-IP peers /
server-to-server callbacks that are actually reachable at their exported address. Over QUIC
this is an ordinary client-initiated request (§4.1) from the notifier to the listener's
endpoint. A conformant implementation **MUST NOT** rely on dial-back for an ephemeral client,
**MUST NOT** default to dial-back absent an explicit reachability advertisement, and **MUST**
use the §4.6.1 server-initiated-stream model whenever the callback target is reached over a
connection the target opened. Dial-back is compat, not the default.

**Selection rule.** The dial-back-vs-push selection is keyed to an **explicit reachability
advertisement** on the listener export (a listener-proxy capability flag / a reachable
`ServerEndpoint` in the exported form): advertised-reachable ⇒ dial-back permitted;
otherwise ⇒ server-initiated-stream push. The exact **advertisement encoding** in the
exported listener form is **[OPEN]** and flagged in §11 (item 1(a)); it interacts with the
tri-export selection of §11 item 9. The **default** (F4) is settled: push, not dial-back.

#### 4.6.3 Subscription-correlation fence (HIGH, NORMATIVE)

A pushed event delivered over a server-initiated `0x01` stream (§4.6.1) **MUST** be
dispatched **only** to a listener for which the client holds an **outstanding subscription**
established over **that same connection / authenticated peer**. Authentication of the server
peer (§4.7) is **necessary but not sufficient**: an authenticated-but-hostile server **MUST
NOT** be able to invoke arbitrary client listeners merely by virtue of a valid handshake.
The client's policy admission gate is keyed to the **subscription**, not merely to
"authenticated peer".

**Normative rules:**

1. **Subscription binding.** When a client registers a listener with a service (e.g. an
   event-registration / `EventMailbox` / lookup-`notify` call), the client **MUST** record a
   binding `⟨authenticated server peer, connection, listener⟩` for the resulting outstanding
   subscription. A server-initiated push is dispatched **only** if it targets a listener
   whose binding matches the pushing connection's authenticated server peer.
2. **Unsubscribed push rejected (fail-closed).** A pushed event that does **not** correspond
   to an outstanding subscription on that connection/peer **MUST** be rejected — the stream
   reset (`JGDMS_REQUEST_NOT_PROCESSED`, §4.4) and the listener **not** invoked. The client
   **MUST NOT** dispatch a push to a listener the pushing peer was never authorized (by an
   outstanding subscription) to invoke.
3. **Scope of the binding.** The binding is to the *specific* listener(s) named in the
   subscription, not to "any listener the client holds". A server authorized to push events
   for subscription A **MUST NOT** thereby be able to invoke the listener of subscription B
   (even A and B on the same connection). This is the admission control that enforces the
   §4.7 authorization model at the granularity of the subscription.
4. **Interaction with §9.5.** This subscription-layer admission control is explicitly
   **permitted** and is **not** the "application-level flow-control rationing" §9.5 forbids
   (§9.5 clarified accordingly). It is a *security* gate keyed to subscription identity, not
   a *rate/credit* scheme layered on QUIC flow control.

### 4.7 Direction-aware caller-subject resolution (NORMATIVE — the must-solve)

JERI's dispatcher today authorizes a request against the **connection's authenticated peer**
— `ServerConnection.getClientSubject()` → the `BasicInvocationDispatcher` client-permission
check (`checkClientPermission` / the `ClientAuthentication`/principal constraint gate) —
under the standing assumption **caller = client = connection initiator**. On a
server-initiated stream (§4.6.1) that assumption **inverts**: the caller is the *server*, yet
the connection was opened by the *client*. Resolving the caller subject by stream initiator
is therefore mandatory, and getting it wrong is a confused-deputy vulnerability.

**Normative rules:**

1. **Caller = stream initiator, not connection initiator.** For authorization and
   caller-subject resolution, the caller of a request is the party that **opened the stream**:
   - On a **client-initiated stream (`0x00`)**, the caller is the **authenticated client
     peer** — the connection's mTLS-verified client identity, exactly as today.
   - On a **server-initiated stream (`0x01`)**, the caller is the **authenticated server
     peer** — the connection's mTLS-verified **server** identity (the SPIFFE/X.500 workload
     identity the client already holds and validated at handshake, §9). The dispatcher of a
     pushed event **MUST** resolve the caller subject to the server's authenticated workload
     identity.
2. **Both identities come from the one handshake.** QUIC mutual TLS authenticates **both**
   ends at the single connection handshake (§9). The server's identity is available to the
   client from `getSession().getPeerCertificates()` on the client side of the QUIC-mode
   engine; it is handshake-pinned (§6.3) and unaffected by migration. No second handshake,
   no per-stream authentication, and no wire-carried identity is used to establish the
   caller of a server-initiated stream — it is the connection's already-verified server peer.
3. **Confused-deputy guard (explicit).** A pushed event **MUST** be authorized against the
   **server's** workload SPIFFE/X.500 identity. It **MUST NOT** be dispatched as **anonymous**
   (that would drop the caller identity entirely), and it **MUST NOT** be authorized with the
   **client's own** privileges (that would let a remote service act with the client's
   authority merely by pushing an event over the client's connection — the confused-deputy
   attack this rule exists to prevent). The listener dispatch runs with the caller subject =
   the authenticated server identity, and the client's local policy decides what that server
   identity is permitted to invoke on the listener.
4. **`getClientSubject` generalises to `getCallerSubject(streamInitiator)`.** The transport
   **MUST** supply the dispatcher with the correct authenticated peer per rule 1. Where the
   existing SPI name (`getClientSubject`) is retained, it **MUST** return the stream-initiator
   peer for server-initiated streams (the server), not the connection's client peer. The
   permission check (`checkClientPermission` and the principal/`ClientAuthentication`
   constraint evaluation) then runs against that direction-correct subject.
5. **Execution-subject swap (HIGH — NORMATIVE).** It is **not** sufficient that the inbound
   permission *check* uses the server identity (rule 3); the pushed-event listener dispatch
   **MUST EXECUTE under the authenticated server's subject** — a `Subject.doAs`/`callAs`
   boundary established with the **server's** principals and a **correspondingly reduced
   `AccessControlContext`** (the §4.7.1 mirror reducing-domain set stamped with the server's
   workload identity). Both the **authorization decision AND any downstream authority** the
   listener exercises therefore derive from the **server**, not the client. Specifically:
   - The client's **ambient `Subject` / `AccessControlContext`** (the client process's own
     identity and privileges) **MUST NOT** leak into the pushed-event dispatch. The dispatch
     does not run "as the client with a check against the server"; it runs **as the server**.
   - Any privileged action the listener takes while handling the push is attributed to, and
     bounded by, the server's subject + reduced ACC — so a downstream call the listener makes
     cannot silently borrow the client's authority (the execution-time form of the
     confused-deputy guard in rule 3).
   - This mirrors how a server today dispatches a client-initiated request under the client's
     subject (`Subject.doAs`/`callAs` with the client's principals): §4.7 makes the *dispatch
     execution* direction-aware, not merely the *check*. On a `0x00` stream the dispatch
     executes as the client; on a `0x01` stream it executes as the server.
   A conformance test (§10.1 item 15) and a harness assertion (§10.3 item 6) verify the
   **execution** subject — not just the check subject — is the server's.

#### 4.7.1 The ACC reducing-context mirror (NORMATIVE)

The STD-006 **§7.2** ACC reducing-domain block (the caller's *reducing* codebase-domain set,
**codebases only, no principals on the wire**) normally flows **client → server**, and the
server stamps the additive workload principal from the authenticated **client** peer (§9.3,
STD-006 §7.2). On a **server-pushed event that carries an on-behalf-of user ACC** (a service
delivering an event under a user's reduced authority), that block flows in the **mirror
direction — server → client**, and the gate mirrors:

1. The client (now the receiver of the pushed request) **MUST** validate the received STD-006
   §7.2 ACC reducing-domain block against the **server's authenticated workload identity**
   (the §4.7 rule-1 server-initiated caller subject), exactly as a server today validates the
   client's ACC against the client's authenticated identity. The additive principal stamped
   onto the reconstructed reducing domains at the client **MUST** be the server's
   authenticated workload identity — never taken from the wire, never anonymous, never the
   client's own (the §4.7 rule-3 guard applied to the ACC path).
2. The `jrt:/java.base` exclusion and every other STD-006 §7.2 invariant (codebases only,
   order-preserving, size-bounded) apply identically in the mirror direction: encoder (server)
   drops `jrt:/java.base`, decoder (client) refuses it.
3. This is a symmetric application of the one gate, not a new mechanism: STD-006 §7.2 already
   says the additive principal is stamped at the receiver from the authenticated peer, never
   from the wire; §4.7 only makes "the authenticated peer" direction-aware. (Note: the design
   SOWs miscite this block as STD-006 "§7.3"; the correct citation is **§7.2** — see the
   editorial note and §9.4.)

---

## 5. DGC Acknowledgment Contract (PINNED POINT 2, NORMATIVE MUST)

### 5.1 Why an application-level ack is required

The retired mux carried an `AcknowledgmentSource` / `ackRequired` signal:
`BasicObjectEndpoint` — the DGC client — relies on an acknowledgment emitted **after the
receiver has processed the request**, not merely after the request body arrived, to keep
distributed-GC lease accounting correct (a "dirty" call must be known to have been
*acted on* at the server before the client updates its lease state). QUIC stream FIN
(§4.3) signals "the request body is complete", which is strictly weaker than "the
`RequestDispatcher` processed the request". Conflating the two would break DGC lease
correctness. Therefore this standard mandates an **application-level acknowledgment**
distinct from any QUIC-level signal.

### 5.2 The contract (NORMATIVE)

When a request is marked ack-required (the JERI `OutboundRequest`/dispatch path that today
sets the mux `ackRequired` bit — DGC dirty/clean calls in particular), the **dispatcher
endpoint** — the endpoint whose `RequestDispatcher` handles the request — **MUST** emit an
application-level acknowledgment **only after** its `RequestDispatcher` has **processed** the
request (returned from dispatch for that `InboundRequest`), and the **caller endpoint** —
the endpoint that opened the stream — **MUST** deliver that acknowledgment to the DGC layer
through the same `AcknowledgmentSource` callback contract that `BasicObjectEndpoint` consumes
today. FIN alone **MUST NOT** be reported as an acknowledgment. Emitting the ack before
dispatch completes is a conformance violation.

**Direction-aware (F1, NORMATIVE — mirrors the §4.7 symmetry).** "Dispatcher" and "caller"
are resolved by stream initiator (§4.7 rule 1), not by connection role:
- On a **client-initiated `0x00` stream**, the dispatcher is the **server** (its send side
  carries the ack) and the caller is the client — the ordinary DGC case.
- On a **server-initiated `0x01` stream** (a server-pushed event, §4.6), the dispatcher is
  the **client** (its `RequestDispatcher` runs the listener), so the ack rides the
  **client's send side** of that `0x01` stream, emitted after the client's listener dispatch
  returns; the server, as caller, consumes it. The ack always rides the **dispatcher's send
  side of the request's own stream**, whichever endpoint that is.

### 5.3 How the ack rides the stream (NORMATIVE)

The acknowledgment **MUST** ride **in-band on the same bidirectional stream** as the request
it acknowledges — an application marker written on the **dispatcher's send side** of that
stream **after** the response body and **before** the dispatcher's stream FIN. A conformant
implementation **MUST NOT** use a separate ack stream (unidirectional or otherwise) or a
separate connection-level frame; the in-band trailer is the adopted design, not a preference
(the separate-stream alternative is **rejected** — it reintroduces cross-stream ordering and
a mux-like control channel). It needs no second stream and no QUIC extension:

```
dispatcher send side of request-stream S:
    [ DER response object ] [ DGC-ACK marker ] FIN
                            ^^^^^^^^^^^^^^^^^^
                            present iff the request was ack-required,
                            written only after RequestDispatcher returns,
                            recognized ONLY at this exact position
```

(On a `0x00` stream the dispatcher is the server; on a `0x01` push stream it is the client —
§5.2 direction-aware.)

**Normative rules:**

1. **Position-defined marker (NORMATIVE).** The ack marker is recognized **only at the exact
   position immediately after the DER response object and before FIN** — the caller
   determines response-object completeness from the **DER structure itself** (STD-006:
   the response object is self-delimiting), then, and only then, looks for the marker at that
   position. The marker **MUST NOT** be recognized by scanning the response byte-content for
   a sentinel; a byte sequence identical to the marker occurring *inside* the DER response
   object is response data, never an ack. This position-defined rule holds **even though the
   exact octet allocation of the marker stays [OPEN]** (§11 item 4): the *recognition rule*
   (position after the complete DER object) is normative now; only the octet value is
   deferred to before interop.
2. The marker carries no data beyond "processed"; it is **not** the response payload.
3. Because it is written after the complete DER response object but before FIN, the caller
   reads the full response object (bounded per §8.2), then checks the post-object position for
   the marker, then observes FIN — an unambiguous, in-order sequence on one stream. QUIC's
   ordered, reliable per-stream delivery (RFC 9000 §2.2) guarantees response-then-ack-then-FIN.
4. If the request was **not** ack-required, no marker is written; FIN immediately follows
   the DER response object. A caller MUST NOT infer an acknowledgment from FIN alone (§5.1).
5. If the dispatcher's `RequestDispatcher` fails or the stream is reset before the marker is
   written, the caller MUST treat the request as **un-acknowledged** for DGC purposes
   (fail-closed: no lease state advance), consistent with the §4.4 reset-code classification.

---

## 6. Handshake, Early Data, Migration, and Connection Liveness (PINNED POINTS 3, 4, 5)

### 6.1 Half-close — independent per-direction FIN (PINNED POINT 3, NORMATIVE)

Each direction of a request stream **MUST** be independently half-closeable via that
direction's own FIN (RFC 9000 §2.2 — the two directions of a bidirectional stream have
independent flow-control and end-of-stream state). Specifically, the transport **MUST**
preserve the **early-response** behaviour: **the server's response side is readable by the
client while the client is still writing its request side.** A server MAY begin writing
(and MAY FIN) its response before it has received the client's request FIN; a client MUST
be able to read response bytes before it has finished, or FIN-ed, its request. An
implementation **MUST NOT** require the client's request FIN before delivering response
bytes, and **MUST NOT** couple the two directions' FIN state.

This mirrors the JERI/TCP behaviour where a server can respond (or fault) before consuming
the entire request body; QUIC's independent per-direction stream termination supports it
natively, and the transport MUST NOT undo it by buffering the response until request FIN.

### 6.2 0-RTT prohibition (PINNED POINT 4, NORMATIVE MUST, fail-closed by construction)

0-RTT early data (RFC 9001 §4.6) is **replayable and not forward-secret**. A JGDMS QUIC
endpoint **MUST NOT** carry **any** application data in 0-RTT early data. In particular it
**MUST NOT** carry: the JERI invocation envelope, any user `Subject` (STD-006 §7.1
`UserSubjectBlock`), the STD-006 **§7.2** ACC reducing-domain block, or any DER object
payload.

The prohibition **MUST be enforced by construction, fail-closed — not by relying on the
underlying engine's absence of a 0-RTT implementation**:

1. The endpoint **MUST NOT** negotiate `early_data`. On the server, it **MUST** assert a
   **`max_early_data_size` of 0** (equivalently: it MUST NOT include the `early_data`
   extension permitting non-zero early data in the TLS 1.3 `NewSessionTicket`, RFC 8446
   §4.6.1 / RFC 9001 §4.6.1). On the client, it **MUST NOT** send 0-RTT packets and MUST
   NOT offer early data even if a resumption ticket would permit it.
2. This refusal **MUST hold independently of whether the TLS engine implements 0-RTT.**
   The investigation confirms the current DirtyChai SunJSSE QUIC-TLS engine does **not**
   implement 0-RTT (`KeySpace.ZERO_RTT` is enum-only scaffolding), so today the hazard is
   moot by absence — but this standard **forbids depending on that absence**. A conformant
   endpoint asserts `max_early_data 0` and refuses early-data key spaces regardless, so a
   future engine rebase that wires `early_data` cannot silently make early data reachable.
3. Any future wiring of `early_data` in an underlying engine is a **mandatory
   security-review trigger** and MUST NOT be enabled for JGDMS traffic without an amendment
   to this standard.

This is a fail-closed-by-construction requirement: the endpoint's own configuration refuses
early data, so the property is a JGDMS invariant, not an accident of engine maturity.

### 6.3 Connection-migration identity invariant (PINNED POINT 5, NORMATIVE)

QUIC connections are keyed by **Connection ID**, not the UDP 4-tuple, so a peer may migrate
across an IP/port change (the roaming HaLOW-mesh / instrument-federation case) without
tearing down the connection (RFC 9000 §5.1, §9). This standard permits migration for that
mobility case, under a strict identity invariant:

1. **Identity is pinned at the TLS 1.3 handshake.** The authenticated peer identity (the
   mTLS/SPIFFE principal set, §9) is established **once**, at handshake completion, for the
   life of the connection. Migration **MUST NOT** re-run identity establishment and **MUST
   NOT** change the authenticated peer.
2. **Migration is PATH-ONLY.** Migration changes the network path (addresses/ports), never
   the peer. The additive principal stamped at the receiver for any request on the
   connection is **ALWAYS the carrying connection's handshake identity** — never derived
   from the current path, source address, or any post-handshake signal.
3. **Path validation gates new-path traffic (MUST).** Before a new path may carry stream
   (request/response) traffic, the endpoint **MUST** complete QUIC path validation:
   **`PATH_CHALLENGE` / `PATH_RESPONSE`** (RFC 9000 §8.2, §9.3) MUST succeed on the new path
   first. Stream traffic on an unvalidated path is a conformance violation. Until validation
   completes, the endpoint MUST also apply the anti-amplification limit to the new path (§7).
4. **AEAD prevents splicing.** QUIC packet protection (RFC 9001 §5) is AEAD keyed from the
   handshake secrets. An off-path attacker cannot inject or splice packets attributable to
   a different peer onto an authenticated connection, because it lacks the AEAD keys — a
   migrated path carries the *same* cryptographic peer by construction. This standard relies
   on that property: the migration invariant is enforced by AEAD, not merely by policy.

**Consequence for §9:** because identity is handshake-pinned and migration is path-only, the
receiver's principal-stamping (§9.2) reads the handshake `SSLSession`, not any per-packet or
per-path source, and is unaffected by migration.

**[BOARD] Interpretation note.** The pin says PATH_CHALLENGE/PATH_RESPONSE "MUST gate a new
path before it carries stream traffic". RFC 9000 also allows a limited amount of
non-probing traffic on a new path during migration under the anti-amplification limit;
this standard takes the stricter reading — **no stream (request/response) traffic** until
validation completes — and permits only QUIC's own path-validation frames beforehand. If the
board wants the RFC-permitted (looser) reading, flag in §11.

### 6.4 Connection liveness — the connection is a leased resource (NORMATIVE)

§4.6 establishes that server-pushed events ride the **connection the client already opened**,
but a QUIC connection has an **idle timeout** (RFC 9000 §10.1): with no traffic for the
negotiated idle period, either endpoint may silently discard connection state, which would
tear down the very path the event channel depends on. This section pins how that connection
is kept alive. The mechanism (Peter's design) is that **the connection is a LEASED resource**:
its liveness is driven by Jini's existing leasing, **not** by a separate QUIC PING keepalive.
Jini leases are the natural liveness primitive — a lease is already a periodically-renewed,
mTLS-authenticated, server-granted, revocable claim on a resource, which is exactly the shape
of "keep this connection open".

#### 6.4.1 Liveness invariant (NORMATIVE)

1. **Leased-liveness invariant.** A QUIC connection carrying JGDMS traffic **stays alive if
   and only if the UNION of live leases riding it is non-empty.** The union includes **DGC
   reference leases** (the `BasicObjectEndpoint` distributed-GC leases on live remote
   references reached over the connection), **event-registration leases** (the "I want events"
   subscriptions of §4.6.3), and **any other Jini lease** whose renewal traffic rides the
   connection. While at least one such lease is live, the connection **MUST** be held open (the
   endpoint keeps it out of idle-close). When the **last** live lease over the connection is
   cleaned or expires, the connection **MUST** be allowed to idle-close (§6.4.4).

2. **Union, not any single lease.** No single lease type is privileged: a connection with only
   DGC leases and no subscriptions stays alive (for the references); a connection with only an
   event-registration lease and no DGC references stays alive (for the events). Liveness is the
   disjunction over all live leases.

#### 6.4.2 DGC dirty-renewal as the keepalive; liveness ≠ authorization (NORMATIVE)

3. **DGC dirty-renewal naturally serves as the keepalive.** The DGC **dirty**-renewal call —
   the authenticated, periodic client→server call that `BasicObjectEndpoint` already makes to
   keep a remote reference live (STD-006 §7, the DGC lifecycle; §5 here for the ack) — is a
   `0x00` client-initiated request on the connection, so it is **connection traffic** and
   **defeats the QUIC idle timeout for free**. No separate PING is needed while any DGC lease
   is being renewed. The **event-registration lease** is the semantically-exact "I want events"
   driver: its renewal is what a client with *only* a subscription (no DGC references) uses to
   hold the pipe open.

4. **Liveness and event-push authorization are SEPARATE (NORMATIVE — do not conflate).**
   *Liveness* (any live lease keeps the pipe open, §6.4.1) and *event-push authorization* (the
   **specific subscription** lease, per the §4.6.3 subscription-correlation fence) are distinct
   gates and **MUST** be kept separate:
   - A **DGC reference lease on an unrelated proxy** keeps the connection alive but **does NOT
     authorize event pushes.** Once the *subscription* lease expires, the server **MUST NOT**
     push events for that subscription (§4.6.3), **even if** the connection is still alive
     because some other (DGC or unrelated) lease is holding it open.
   - Conversely, a live subscription authorizes pushes (§4.6.3) *and* contributes to liveness
     (§6.4.1), but its authorization scope is the subscription, never "any listener on a live
     connection".
   This separation is the confused-deputy / over-authorization guard at the lease layer: a
   connection being open is necessary but **not sufficient** for a push; the matching live
   subscription is the sufficient condition (§4.6.3, §4.7).

#### 6.4.3 Timing (NORMATIVE)

5. **Renewal cadence < idle timeout.** The lease **renewal interval MUST be strictly less than
   the QUIC connection idle timeout**, with margin for renewal latency, jitter, and one missed
   renewal. Equivalently, a JGDMS QUIC endpoint **MUST** set (advertise/negotiate) its
   `max_idle_timeout` (RFC 9000 §10.1, transport parameter) **above the lease-renewal cadence**
   (renewal interval + margin), so that a connection kept alive by an actively-renewed lease is
   never idle-closed between renewals. The **`LeaseRenewalManager` owns renewal timing** (it is
   the JGDMS component that schedules renewals); the endpoint's idle-timeout configuration is
   derived from, and MUST accommodate, that cadence — not the other way round. An endpoint
   **MUST NOT** rely on QUIC PING keepalive frames as the primary liveness mechanism for a
   JGDMS connection; lease renewal is the primary keepalive, and any PING use is at most a
   redundant transport-level backstop.

#### 6.4.4 Teardown (NORMATIVE)

6. **Lease end ⇒ graceful idle-close.** A **DGC clean** call, an explicit **lease cancel**, or
   **lease expiry** removes that lease from the connection's live-lease union (§6.4.1). When the
   removal empties the union (no live lease remains over the connection), the endpoint **MUST**
   let the connection **idle-close gracefully** (allow the QUIC idle timeout to elapse, or send
   a QUIC `CONNECTION_CLOSE` — RFC 9000 §10.2 — for a prompt close), and **event pushes stop**.
   A teardown of the subscription lease specifically stops pushes for that subscription
   immediately (§4.6.3/§6.4.2 rule 4), independently of whether the connection itself closes
   (it stays up if another lease still holds it).

#### 6.4.5 Security — bounded, authenticated keepalive; the connection-holding-DoS bound (NORMATIVE)

7. **The keepalive is mTLS-authenticated and BOUNDED — a client cannot hold a connection
   indefinitely.** Every renewal is an authenticated call over the connection's mutual-TLS
   identity (§9), so the peer holding a connection open is always a known, authenticated
   workload — there is no anonymous keepalive. Crucially, **the server grants lease durations
   and controls renewal**, which bounds how long any client can hold a connection:
   - The **server sets each lease's granted duration** and **MAY refuse renewal** (deny or
     shorten) at any renewal call. A client cannot self-extend; it can only *request* renewal,
     which the server grants or refuses. So a connection's lifetime is the server-granted
     lease duration, repeatedly, at the server's discretion — **lease-grant-bounded**, never
     client-dictated.
   - The server **caps subscriptions and connections per authenticated identity** — the same
     admission control as the §4.6.1 `MAX_STREAMS` limit and the §4.6.3 subscription fence,
     applied to the *connection/lease* resource. An identity that reaches its cap is refused
     new leases/connections.

   **Connection-holding-DoS bound (NORMATIVE statement).** A malicious (even
   fully-authenticated) client **CANNOT** hold a JGDMS QUIC connection open indefinitely or
   exhaust server connection resources: the maximum a given authenticated identity can hold is
   **(per-identity connection cap) × (server-granted lease duration)**, renewable only with
   continued server consent, and revocable by the server at the next renewal (refuse) or
   immediately (cancel the grant / close the connection). Because the server owns the grant, the
   refuse, and the cap, the client's connection-holding power is server-bounded on every axis —
   count (cap), duration (grant), and continuation (renewal consent). This is the same
   least-privilege, server-owns-the-grant posture as the leased-permission-grant model
   elsewhere in JGDMS.

#### 6.4.6 Migration (NORMATIVE)

8. **A lease-kept-alive connection migrates with the roaming client.** Because liveness is a
   property of the *connection* (identified by Connection ID, not the 4-tuple) and migration is
   path-only (§6.3), a connection held open by a live lease **survives a client address change**:
   the roaming ephemeral / mesh node keeps its event channel across network moves. Lease renewal
   continues over the migrated path (after §6.3 path validation), so the leased-liveness
   invariant (§6.4.1) holds across migration unchanged — the union of live leases is unaffected
   by which path currently carries their renewal traffic. This is what lets a mobile HaLOW-mesh
   node (§6.3, the roaming case) retain server-pushed events while roaming: the event channel is
   the leased connection, and the lease — not the address — is what keeps it alive.

**Cross-references.** This section is the liveness backing for **§4.6** (events ride the
client-opened connection — this is what keeps that connection alive) and **§4.6.3** (the
subscription lease both authorizes pushes *and* contributes to liveness, but liveness ≠
authorization — §6.4.2 rule 4). The keepalive traffic is the **§5** DGC dirty-renewal (its ack
contract) and ordinary `0x00` requests. Migration interoperates with **§6.3**.

---

## 7. Anti-Amplification (PINNED POINT 6, NORMATIVE)

Because QUIC runs over unauthenticated UDP, an unvalidated client address lets a server be
used as a reflection/amplification vector. A JGDMS QUIC server **MUST** implement RFC 9000
§8 anti-amplification as a conformance requirement:

1. **3× limit (MUST).** Before validating a peer's address, the server **MUST NOT** send
   more than **three times** the number of bytes it has received from that peer, on that
   path (RFC 9000 §8.1). This limit applies to the initial handshake path and to any new
   path during migration until path validation (§6-migration) completes.
2. **Stateless Retry tokens (MUST support; SHOULD use under load).** The server **MUST**
   support the QUIC stateless **Retry** mechanism (RFC 9000 §8.1, §17.2.5) — issuing a Retry
   packet with a token that the client echoes in a subsequent Initial — as a means of
   validating the client's address before committing handshake resources, and **SHOULD**
   employ it when under load or facing suspected address-spoofing. The Retry token **MUST**
   be a **keyed MAC or AEAD over at least `(client-address, timestamp)` under a server-held
   secret** — **unforgeable without that secret** — with a **bounded freshness window**. A
   token **MUST** be rejected if its MAC/AEAD does not verify, if the presenting client
   address does not match the address bound in the token, or if the timestamp is outside the
   freshness window. This makes the token stateless (no per-client server state) yet
   unspoofable and non-replayable from a different address or after expiry.
3. Address validation via a validated Retry token or a completed handshake lifts the 3×
   limit for that path (RFC 9000 §8.1).

These have no analogue in the TCP/UDS transports (TCP's handshake validates the path), so
this is new transport code JGDMS writes and a reviewer must audit from this section.

---

## 8. DER Payload Carriage (PINNED POINT 7, NORMATIVE)

### 8.1 The DER wire rides the QUIC stream unchanged (NORMATIVE)

The STD-006 DER object wire **rides the QUIC bidirectional stream byte-for-byte unchanged**.
The request body written to a stream's send side and the response body read from its receive
side are exactly the DER-encoded JERI payloads STD-006 defines; QUIC is a transparent,
ordered, reliable byte transport for them. No QUIC-specific transformation, re-framing, or
re-encoding of the DER payload is permitted. This is the same layering STD-006 assumes over
any reliable transport.

### 8.2 Size-bound-before-allocation is retained (NORMATIVE — the critical carve-out)

QUIC flow control (§9.5) bounds **bytes in flight**, not **decoded element counts**. These
are different quantities: a small number of flow-controlled bytes can decode into a large
number of elements (or a deeply nested structure). Therefore the STD-006 §4.5
**size-bound-before-allocation** discipline is **undiminished by QUIC and MUST be retained
in full**. A conformant QUIC-transport decoder **MUST** enforce every STD-006 §4.5 profile
ceiling — `maxCollection`, `maxFields`, `maxStackFrames`, `maxCauseDepth`, `maxDomains`,
`maxCerts`, `maxCertLen`, `maxDigestLen`, and the rest — **before allocation**, exactly as
over any other transport. An implementation **MUST NOT** treat QUIC flow control, stream
data limits, or `MAX_DATA` as a substitute for the STD-006 decode bounds. QUIC caps the
bytes; STD-006 caps the decoded structure; both are required.

**Both directions — every DER decode surface (F1, NORMATIVE).** The §4.5 ceilings apply to
**every** DER decode surface in **both** directions, without exception:
- a **server** decoding a client-initiated request body (`0x00`);
- a **client** decoding a server response body (`0x00`);
- a **client** decoding a **server-pushed request body** (`0x01`, §4.6) — a new decode
  surface introduced by server-initiated streams;
- a **server** decoding the push response / listener return (`0x01`);
- and, specifically, the **client-side decode of the §7.2 reducing-domain ACC block** on a
  server→client mirror push (§4.7.1) — see §10.3.

**Authentication of the peer does NOT exempt any payload from the ceilings.** A pushed
request body arriving from an authenticated (even fully trusted) server is still an
untrusted-*structure* decode surface: the peer's identity bounds *who* may send, not *how
large* the decoded structure may be. The client MUST size-bound a server-pushed payload
exactly as a server size-bounds a client request. A hostile-but-authenticated server
(§4.6.3, §4.7) that attempts resource exhaustion via an oversized pushed structure MUST hit
the §4.5 ceilings and be rejected before allocation.

**Rationale (auditor-facing):** the DER decoder's resource-exhaustion defence lives in
STD-006 §4.5, not in the transport. A reviewer auditing a JGDMS QUIC endpoint MUST confirm
that moving from TCP to QUIC did **not** delete or weaken those bounds on the theory that
"QUIC already limits bytes". It does not limit decoded counts; the bounds stay.

---

## 9. Identity Model (PINNED POINT 8, NORMATIVE)

### 9.1 Handshake-time mTLS/SPIFFE, explicitly not a layer-2 identity (NORMATIVE)

The QUIC transport's peer identity is established by **mutual TLS 1.3 at the QUIC handshake**
— the `net.jini.jeri.ssl` lineage: the JGDMS `AuthManager` (custom X509 Key/Trust manager),
`SubjectCredentials` cert⇄Subject mapping, and SPIFFE principal matching from URI-SAN(s),
driven by the QUIC-mode TLS engine (RFC 9001 integrates TLS 1.3 into the handshake). The
authenticated peer is the certificate identity — **SPIFFE `SpiffePrincipal`s and/or X.500
principals** from the peer certificate chain.

This is **explicitly a handshake-time transport identity, NOT a layer-2 identity.** It is
distinct from, and MUST NOT be confused with, the layer-2 identity carriage that some other
JGDMS transports use (e.g. the shipped `net.jini.jeri.uds` inc-1, which deliberately carries
SPIFFE/JWT identity as a layer-2 exchange with no TLS). QUIC-TLS runs real TLS 1.3 (RFC 9001
makes it non-optional), so its identity is the completed mutual handshake, in the same class
as the `ssl` transport — not an application-layer token.

### 9.2 Peer authentication is by certificate identity, not hostname (NORMATIVE)

Consistent with the `ssl` and UDS transports, the peer is authenticated by its **SPIFFE/X.500
certificate identity, not by hostname/SNI**. The JGDMS `AuthManager` self-validates the peer
chain (SPIFFE URI-SAN / X.500 subject) and does not perform RFC 6125 endpoint-identification.
A conformant endpoint **MUST** authenticate the peer by certificate identity and **MUST NOT**
make request delivery contingent on hostname verification of the QUIC peer address. (This is
why connection migration across address changes, §6-migration, does not disturb identity: the
identity was never the address.)

**[BOARD] Algorithm-constraint enforcement.** The investigation's DirtyChai "Path A" trust
relaxation (2-arg trust path) drops SunJSSE's engine-tied endpoint-identification **and
algorithm-constraint enforcement**. Endpoint-identification is intentionally not used
(above). Algorithm-constraint enforcement, however, is a real security control (it forbids
weak signature/curve/suite algorithms). This standard **requires** that a conformant JGDMS
QUIC endpoint re-impose algorithm constraints equivalent to the `ssl` transport's — either
via the TLS engine's own constraints or in the `AuthManager` — and MUST NOT silently forgo
them. The exact mechanism is **[OPEN]** (engine-dependent) and flagged in §11.

### 9.3 Additive principal stamping at the receiver (NORMATIVE)

The additive workload principal stamped at the receiver — as STD-006 §7.2 specifies for the
ACC reducing-domain transport (the additive principals are stamped **at the receiver from the
authenticated mTLS peer identity, never from the wire**) — is, for the QUIC transport,
**always the carrying connection's handshake identity** (§6-migration invariant). The
receiver reads the authenticated peer from the QUIC handshake `SSLSession`
(`getSession().getPeerCertificates()` on the QUIC-mode engine) and stamps that identity;
it MUST NOT derive the stamped principal from the current path, source address, or any
post-handshake or per-packet signal.

### 9.4 Relationship to STD-006 §7.2 and the ACC transport (NORMATIVE cross-reference)

The STD-006 **§7.2** `AccessControlContextRecord` / `ReducingDomainRecord` block (the
caller's *reducing* domain set, **codebases only, no principals on the wire**) rides the
QUIC DER payload (§8) exactly as over any transport. The QUIC transport supplies the
authenticated peer identity (§9.1–9.3) that the receiver uses to stamp the additive
workload principals onto the reconstructed reducing domains, per STD-006 §7.2 and the
two-gate SPIFFE model (STD-003 §3.8 / §7.6). The QUIC transport changes **where the TLS
runs** (integrated into the QUIC handshake) but **not what STD-006 §7.2 transmits** or how
the receiver stamps. On a **server-initiated stream** (§4.6), the same §7.2 block flows in
the mirror direction (server → client) and the client validates it against the server's
authenticated workload identity — see §4.7.1. In both directions the additive principal is
the direction-correct authenticated peer, never taken from the wire.

> **Correction (normative):** the QUIC design SOWs cite this block as STD-006 "§7.3". The
> correct citation is **STD-006 §7.2**; §7.3 is `DigestCodeSourceRecord` (code identity),
> a different record. This standard cites §7.2. See the editorial note.

**Context only (not normative here):** whether the QUIC-mode TLS engine accepts the JGDMS
custom `AuthManager` is a DirtyChai maintainer concern (the ADVICE document's "Path A"
trust-manager relaxation), out of scope for this wire standard. It is noted so a reviewer
knows the identity plumbing has an engine-side dependency; it does not change the wire
contract specified here.

### 9.5 Flow control — rely on QUIC, no application-level rationing (PINNED POINT 9, NORMATIVE)

The transport **MUST** rely on QUIC's own flow control — **`MAX_STREAM_DATA`** (per-stream)
and **`MAX_DATA`** (per-connection) credit, plus `MAX_STREAMS` for stream concurrency
(RFC 9000 §4, §19.9–19.11) — for all back-pressure. A conformant endpoint **MUST NOT**
implement any application-level flow-control rationing, windowing, or credit scheme layered
on top of QUIC's. Back-pressure on a request's request/response bodies is expressed by
QUIC flow-control frames; the JERI streams block/resume according to QUIC's stream-data and
connection-data credit. (This is design principle 6: do not re-implement a service QUIC
already provides. Note the distinction from §8: QUIC flow control governs *bytes*, and is
sufficient for back-pressure; it is **not** sufficient for decode bounds, which remain
STD-006 §4.5's job.)

**Clarification (NORMATIVE) — subscription admission control is permitted.** "No
application-level rationing" forbids a parallel *rate/credit/window* scheme layered on QUIC
flow control. It does **not** forbid the client refusing a pushed event that has **no
matching outstanding subscription** (§4.6.3): subscription-correlation is a **security
admission gate** keyed to subscription identity, not a flow-control rationing scheme, and it
is **required** as the enforcement mechanism for the §4.6.3 fence. Likewise, the client's
`MAX_STREAMS` credit for server-initiated streams (§4.6.1, F3) *is* QUIC's own flow control
being used as designed — advertising it, and the server blocking when it is momentarily
exhausted, is not application-level rationing.

---

## 10. Conformance and Interoperability

### 10.1 Conformance

A conforming JGDMS QUIC-TLS transport endpoint:

1. Maps exactly one JERI request to one QUIC bidirectional stream, never multiplexing or
   splitting a request across streams, for **both** directions — client-caller requests on
   client-initiated streams (`0x00`, §4.1) and server-caller event push on server-initiated
   streams (`0x01`, §4.6) — and retires the mux only after the §4.2 audit and only once both
   directions are specified (which this standard does) (§4.0, §4.1, §4.2, §4.6).
2. Signals normal per-direction completion with the QUIC stream **FIN** and never conflates
   FIN with request processing (§4.3).
3. Aborts with **`RESET_STREAM`** carrying the §4.4 application-error-code convention, and
   maps it to `OutboundRequest.getDeliveryStatus()` so at-most-once retry is safe: retries
   only on the retry-safe (`false`, not-yet-processed) classification, never on the
   retry-unsafe one; resolves ambiguity to retry-unsafe (§4.4).
4. Emits the DGC application-level acknowledgment **only after** the **dispatcher endpoint's**
   `RequestDispatcher` processes an ack-required request, delivers it through the
   `AcknowledgmentSource` contract, rides it **in-band on the dispatcher's send side**
   (server's on a `0x00` stream, **client's on a `0x01` push stream** — direction-aware)
   **at the position immediately after the complete DER response object and before FIN**
   (position-defined, never content-scanned), never uses a separate ack stream, and never
   reports FIN alone as an acknowledgment (§5).
5. Preserves independent per-direction half-close, keeping the server response readable
   while the client is still writing its request (§6.1).
6. Refuses 0-RTT early data **by construction, fail-closed** — asserts `max_early_data 0`,
   never negotiates `early_data`, never carries the invocation envelope / user `Subject` /
   STD-006 §7.2 ACC block / DER payload in early data — independently of whether the engine
   implements 0-RTT (§6.2).
7. Pins identity at the TLS 1.3 handshake; treats migration as path-only; gates any new
   path with `PATH_CHALLENGE`/`PATH_RESPONSE` before stream traffic; and always stamps the
   carrying connection's handshake identity, never a path-derived one (§6.3, §9.3).
8. Enforces the RFC 9000 §8 **3× anti-amplification limit** before address validation and
   **supports stateless Retry tokens** (address-bound, freshness-bound, integrity-protected)
   (§7).
9. Carries the STD-006 DER payload byte-for-byte unchanged **and** retains every STD-006
   §4.5 size-bound-before-allocation ceiling **on every DER decode surface in both
   directions** — including a **client decoding a server-pushed request body** and the
   **client-side §7.2 reducing-domain ACC decode** — never substituting QUIC flow control for
   the decode bounds and never exempting a payload because the peer is authenticated (§8, §8.2).
10. Establishes peer identity by handshake-time mTLS/SPIFFE (certificate identity, not
    hostname), re-imposes algorithm constraints equivalent to the `ssl` transport, and does
    not treat this as a layer-2 identity (§9.1, §9.2).
11. Relies solely on QUIC `MAX_STREAM_DATA`/`MAX_DATA`/`MAX_STREAMS` for back-pressure, with
    no application-level flow-control rationing (§9.5).
12. Conforms to RFC 9000, RFC 9001, and RFC 9002 for all transport behaviour not otherwise
    constrained here, and to RFC 8446 for the TLS 1.3 handshake.
13. Delivers events/callbacks over a **server-initiated bidirectional stream (`0x01`)** on the
    connection the client already opened **by default (push-primary, F4)**; **accepts inbound
    streams, never inbound connections** (no client accept loop, listen port, or inbound
    firewall hole); uses classic dial-back **only** when the listener-holder has explicitly
    advertised a reachable `ServerEndpoint`, and never defaults to dial-back (§4.6). Advertises
    server-initiated-bidi `MAX_STREAMS` credit **≥ its outstanding subscriptions**, and (as
    server) **blocks — never drops** — a pushed event when that credit is momentarily
    exhausted, using the client's `MAX_STREAMS` extension as back-pressure (§4.6.1 F3).
14. Resolves the caller subject **direction-aware — by stream initiator, not connection
    initiator**: client-initiated ⇒ caller = authenticated client peer; server-initiated ⇒
    caller = authenticated **server** peer (the connection's mTLS-verified workload identity).
    Authorizes a pushed event against the server's SPIFFE/X.500 workload identity — **never
    anonymous, never the client's own privileges** (the confused-deputy guard) — and mirrors
    the STD-006 §7.2 ACC gate in the server→client direction, the client validating the
    on-behalf-of ACC against the server's authenticated identity (§4.7, §4.7.1).
15. **Executes** a pushed-event dispatch **under the authenticated server's subject** — a
    `Subject.doAs`/`callAs` boundary with the server's principals and a correspondingly
    reduced ACC — so that **both the authorization decision and any downstream authority
    derive from the server, not the client**; the client's ambient `Subject`/`AccessControl-
    Context` **MUST NOT** leak into the dispatch. Conformance verifies the **execution**
    subject (not merely the check subject) is the server's (§4.7 rule 5).
16. Dispatches a pushed event **only** to a listener for which the client holds an
    **outstanding subscription** bound to the pushing connection's authenticated server peer;
    **rejects an unsubscribed push** (stream reset, listener not invoked); and does not let a
    server authorized for subscription A invoke the listener of subscription B. An
    authenticated-but-hostile server cannot invoke arbitrary client listeners (§4.6.3).
17. Uses a Retry token that is a **keyed MAC/AEAD over `(client-address, timestamp)` under a
    server secret**, unforgeable without it, with a bounded freshness window; rejects a token
    that fails MAC/AEAD verification, address match, or freshness (§7).
18. Treats the connection as a **leased resource**: holds it open **iff the union of live
    leases riding it (DGC reference + event-registration + any Jini lease) is non-empty**, and
    lets it **idle-close gracefully** when the last lease is cleaned/cancelled/expires;
    uses **DGC dirty-renewal / lease renewal as the keepalive** (no separate PING as the
    primary mechanism); and sets `max_idle_timeout` **above** the `LeaseRenewalManager` renewal
    cadence (renewal interval strictly less than the idle timeout, with margin) (§6.4.1–6.4.4).
19. Keeps **liveness and event-push authorization separate**: an unrelated (e.g. DGC) lease
    keeps the connection alive but does **not** authorize pushes once the subscription lease
    expires — the server MUST stop pushes for an expired subscription even on a still-live
    connection (§6.4.2 rule 4, §4.6.3).
20. Bounds connection-holding: the keepalive is **mTLS-authenticated**, the **server grants
    lease durations, may refuse renewal, and caps subscriptions/connections per identity**, so
    a malicious client **cannot hold a connection indefinitely** — the holding bound is
    **(per-identity connection cap) × (server-granted lease duration)**, renewable only with
    continued server consent (§6.4.5). A lease-kept-alive connection **migrates with the
    roaming client** (Connection-ID-keyed, path-only migration), preserving the event channel
    across address changes (§6.4.6, §6.3).

### 10.2 Interoperability matrix (NORMATIVE — required for release)

Interoperability is **unverifiable without this normative spec**, and this standard exists
so that a non-JVM peer can be built and audited from the document alone. A JGDMS QUIC
endpoint (both as client and as server) **MUST** be interop-tested against the following
polyglot QUIC implementations, exercising the RFC 9000/9001/9002 baseline **and** the
JGDMS-specific behaviours of §§4–9 (client- **and** server-initiated stream↔request mapping,
server-pushed event delivery over a server-initiated `0x01` stream with direction-aware
caller-subject resolution, FIN/RESET completion, the DGC ack marker, half-close, 0-RTT
refusal, migration path-validation, anti-amplification), each paired with a DER payload smoke
test (§8):

| Implementation | Language | Role in matrix |
|---|---|---|
| **quic-go** | Go | client ⇄ JGDMS server, and JGDMS client ⇄ quic-go server |
| **quiche** (Cloudflare) | Rust / C API | client ⇄ JGDMS server; C-API reach for `ngtcp2`-class peers |
| **quinn** | Rust | the embedded-JERI federation case (Rust static-musl mesh nodes) — client ⇄ JGDMS server |
| **Neqo** (Mozilla) | Rust | independent stack, client ⇄ JGDMS server |
| **nghttp3** / ngtcp2 | C | C-stack conformance peer |

**[BOARD] Scope note.** Several of these implementations expose QUIC primarily beneath an
HTTP/3 API; the interop tests MUST exercise the **raw QUIC stream** layer (custom
ALPN, not `h3`), since JGDMS uses QUIC streams directly, not HTTP/3. The ALPN token for the
JGDMS QUIC-JERI protocol is an **[INTEROP-GATE] [OPEN]** item (proposed: a registered
`jgdms-jeri`-class token) — it must be fixed **before interop (§11 item 8), not before build
start**; interop peers must be drivable at the QUIC-stream layer with that ALPN.

### 10.3 Conformance + fuzz harness (NORMATIVE — required deliverable)

A **conformance and fuzz harness is a required deliverable** of the P3 build, on the same
footing STD-006 places its ASN.1/conformance suite. It **MUST**:

1. Assert every §10.1 conformance item against a running JGDMS QUIC endpoint (both roles).
2. Drive the §10.2 interop matrix end-to-end with DER payload round-trips.
3. **Fuzz the QUIC transport decode surface** — packet/frame parsing, the stream state
   machine, RESET/STOP_SENDING handling, the ack-marker framing, and (critically) the DER
   payload decoder's §8.2 size bounds under adversarial stream/flow-control inputs — with the
   fail-closed expectation: malformed or oversized input constructs no object and carries no
   traffic. This is the "disciplined decoder" caution (STD-006 lineage) applied to a
   JGDMS-owned QUIC wire.
4. Include the pre-Path-A **mTLS-over-QUIC-server-mode** test the ADVICE flags as
   wired-but-untested (a client-auth QUIC handshake asserting both ends'
   `getSession().getPeerCertificates()` populate) — the single highest-priority test before
   the build relies on the engine.
5. Exercise **server-initiated event push and its authorization inversion (§4.6, §4.7)**:
   assert an event delivered over a `0x01` stream is authorized against the **server's**
   authenticated workload identity, and assert the confused-deputy negatives — that the
   pushed event is **not** dispatched anonymously and **not** with the client's own
   privileges — plus the ACC-mirror gate (client validates a server→client on-behalf-of ACC
   against the server identity, §4.7.1).
6. **Assert the EXECUTION subject, not merely the check subject (§4.7 rule 5).** Have the
   pushed listener perform a privileged downstream action and assert it is attributed to, and
   bounded by, the **server's** subject + reduced ACC — and, as a negative, assert the
   client's ambient `Subject`/`AccessControlContext` does **not** leak in (a listener that
   probes for the client's own privileges during a push must fail to obtain them).
7. **Assert the subscription-correlation fence (§4.6.3) — negatives.** With an
   authenticated server, push to a listener the client **has** subscribed → dispatched; push
   to a listener the client has **not** subscribed (or subscription-B's listener under
   subscription-A's authority) → **rejected** (stream reset, listener not invoked). An
   authenticated-but-hostile server cannot invoke an unsubscribed listener.
8. **Fuzz the client-side §7.2 reducing-domain decoder under a hostile server (§4.7.1, §8.2).**
   The ACC mirror makes the **client** a §7.2 decode surface for server-supplied
   reducing-domain blocks; fuzz it under the same STD-006 §4.5 bounds as the server side —
   `maxDomains`, `maxCerts`, `maxCertLen`, codebase-URI well-formedness, and the
   **`jrt:/java.base` exclusion** (client decoder refuses it) — with the fail-closed
   expectation: oversized/malformed/`jrt:`-bearing input constructs no object.
9. **Assert connection liveness / leased-teardown (§6.4).** Assert that (a) a connection with
   at least one live lease is not idle-closed across the renewal cadence; (b) cleaning/cancelling
   the **last** lease lets the connection idle-close and stops pushes (§6.4.4); (c) an expired
   **subscription** lease stops pushes for that subscription **even while** an unrelated (DGC)
   lease keeps the connection alive (§6.4.2 rule 4); and (d) the **connection-holding-DoS
   bound** — a client denied renewal cannot hold the connection past the server-granted lease
   duration, and per-identity connection/subscription caps are enforced (§6.4.5). Include a
   migration case: a lease-kept-alive connection survives a simulated client address change and
   keeps delivering pushes (§6.4.6, §6.3).

---

## 11. Open Questions Carried Forward

Consolidated list of every **[OPEN]** / **[BOARD]** above, for the next working session and
the board's adjudication:

Three items (2, 4, 8) are **interop-gates** — they must be closed **before interoperability
testing (§10.2), not before build start** — since they concern exact wire-octet allocations a
JGDMS-to-JGDMS build does not need but a polyglot peer does. The rest are design/audit items.

1. **§4.6 / §4.7 — server-initiated event push (RESOLVED into normative text; residual
   items follow).** The former "does JERI have a server-initiated request path?" open item is
   **closed**: it does (dial-back today), and this standard now maps event/callback delivery
   onto server-initiated `0x01` streams with direction-aware caller-subject resolution
   (execution-subject swap, §4.7 rule 5), the subscription-correlation fence (§4.6.3), and the
   ACC mirror (§4.7.1). Residual sub-items: (a) the dial-back-vs-push selection is settled
   (push-primary default, F4/§4.6.1), but the **advertisement encoding** in the exported
   listener form (§4.6.2) is [OPEN] and interacts with item 9 (tri-export); (b) **F2 — the
   server-init investigation found NO non-callback server-caller path** (nothing beyond the
   listener/event pattern needs a `0x01` stream); this is downgraded from [OPEN] to a
   **verification obligation**: the implementer **MUST re-verify against the exact mux build**
   (the §4.2 audit) that no such path exists **before deleting the mux**.
2. **[INTEROP-GATE] §4.4 — application error-code allocation.** The symbolic codes are pinned;
   the actual QUIC application-error-code varint values (and the JGDMS-reserved range) are
   unallocated. Assign concrete numbers **before interop**, not before build.
3. **§4.4 — reset-code granularity.** JERI `getDeliveryStatus()` is binary; this standard
   collapses "partially processed" and "processed-but-response-lost" into one retry-unsafe
   class. Confirm the board does not need a finer signal (if so, it belongs to the §5 ack,
   not the reset code).
4. **[INTEROP-GATE] §5.3 — DGC ack marker octet encoding.** The **recognition rule** is now
   normative — in-band, position-defined (recognized only at the position immediately after
   the complete DER response object, never content-scanned; §5.3 rule 1). Only the **exact
   octet allocation** of the marker remains [OPEN]; fix it **before interop** so a non-JVM
   peer can produce/consume it. (The separate-stream alternative is **rejected**, closing the
   former item-5 design fork.)
5. **§6.3 — migration path-validation strictness.** This standard forbids *all* stream
   traffic on an unvalidated path (stricter than RFC 9000, which permits limited
   anti-amplification-bounded traffic). Confirm the stricter reading.
6. **§9.2 — algorithm-constraint enforcement mechanism.** The "Path A" trust relaxation
   drops SunJSSE algorithm-constraint enforcement; this standard requires JGDMS re-impose
   equivalents but leaves the mechanism engine-dependent. Pin the mechanism.
7. **§1 [BOARD] — Path A vs Path B.** The wire contract is path-independent, but the build
   still needs the path decision (own the QUIC transport on the DirtyChai-exposed engine vs
   Kwik + auth bridge). Out of scope for this standard; tracked here for the board.
8. **[INTEROP-GATE] §10.2 — ALPN token.** The JGDMS QUIC-JERI ALPN token (for raw-stream,
   non-HTTP/3 interop) is unallocated. Register a `jgdms-jeri`-class token **before interop**.
9. **Cross-transport endpoint selection.** Tri-export across TCP/UDS/QUIC with graceful
   degradation (UDP-blocked networks fall back to TCP) generalises the UDS SOW §7 concern;
   it is an `Endpoint`-selection question, not a QUIC-wire question, but affects how a QUIC
   endpoint advertises and degrades (and the §4.6.2(a) reachability advertisement). Flag for a
   companion note.

---

## 12. References

- `docs/quic-tls-jeri-endpoint-investigation.md` — the review-board investigation briefing
  (consolidated prior work; §2 SSL-endpoint characterisation, §3 QUIC design surface + JERI
  mapping + security, §4 DirtyChai export list, §5 open questions).
- `docs/SOW-QUIC-JERI-Transport.md` — primary design SOW (thesis, §3a/§3b two architectures,
  §4 phased plan P1/P2/P3, §5 de-risk spike).
- `docs/ADVICE-quic-tls-exposure-2026-06-30.md` — the completed DirtyChai investigation
  (GO-WITH-CAVEATS on Path A; the trust-manager relaxation; Q1–Q7 with source evidence;
  0-RTT unimplemented; mTLS-server-mode wired-but-untested).
- `docs/SOW-DirtyChai-QUIC-TLS-Investigation.md` — the DirtyChai handoff brief (Q1–Q7,
  source landmines).
- `docs/SOW-Unix-Domain-Socket-JERI-Transport.md` — the stepping-stone transport and the
  shared `SSLSocket`→`SSLEngine` keystone; the certificate-identity-not-hostname property.
- `JGDMS-STD-006-DER-WireFormat` **≥ v0.12** (current: `…-v0.13-DRAFT.md`) — the DER object
  wire this transport carries unchanged (§8); **§4.5** size-bound-before-allocation; **§7.1**
  `UserSubjectBlock`; **§7.2** `AccessControlContextRecord` / `ReducingDomainRecord` (the ACC
  reducing-domain / `RemoteContextCodec` model — the correct citation, correcting the SOWs'
  "§7.3"). **The v0.12 floor is load-bearing (F5):** §7.2 became the codebase-only
  reducing-domain model at v0.12; the §4.7.1 ACC mirror and §9 depend on it. Do **not** read
  this standard against the stale in-tree **v0.10** (or earlier) STD-006 copy — those predate
  the §7.2 reducing-domain model and will mislead an implementer of the mirror gate.
- `JGDMS-STD-003-MultiSubjectIdentityArchitecture` — the multi-subject / SPIFFE two-gate
  identity model (§3.8 / §7.6 cross-referenced by STD-006 §7.2 and by §9 here).
- Source (to be built / cited as evidence): the proposed
  `net.jini.jeri.quic.{QuicEndpoint,QuicServerEndpoint}` pair on the JERI
  `Endpoint`/`ServerEndpoint`/`connection.Connection` SPI; the retired
  `org.apache.river.jeri.internal.mux.*`; the reused `net.jini.jeri.ssl.{AuthManager,
  ClientAuthManager,ServerAuthManager,SubjectCredentials,Utilities}` auth logic;
  `net.jini.jeri.BasicObjectEndpoint` (the DGC client, §5; `newCall`→`newRequest` — the
  dial-back callback path, §4.6.2); `net.jini.jeri.BasicInvocationDispatcher`
  (`getClientSubject`→`checkClientPermission` — the caller-subject/authorization path
  generalised in §4.7); `net.jini.jeri.connection.ServerConnection.getClientSubject`;
  `net.jini.core.event.RemoteEventListener.notify` and the lookup `ServiceEvent` /
  lease-notification listeners (the event pattern mapped in §4.6);
  `net.jini.jeri.tcp.TcpEndpoint` (the host:port baked into the dial-back listener proxy);
  `net.jini.core.lease.Lease` / `net.jini.lease.LeaseRenewalManager` (the leasing primitive
  and its renewal-timing owner — §6.4 connection liveness); `net.jini.jeri.BasicObjectEndpoint`
  DGC reference leases + dirty-renewal (the natural keepalive, §6.4.2).
- Precedent for server-initiated push over a client-opened connection: **WebTransport**
  (server-initiated streams over an HTTP/3 QUIC connection) and **gRPC** server-streaming /
  bidirectional streaming (server pushes over the client-opened connection) — §4.6.1.
- RFCs: **9000** (QUIC transport), **9001** (using TLS to secure QUIC), **9002** (loss
  detection / congestion control), **8446** (TLS 1.3), **2119**/**8174** (requirements
  language), **6125** (endpoint identification — deliberately *not* used, §9.2). JEP **517**
  (HTTP/3 for the HTTP Client API — the SunJSSE QUIC-TLS engine, JDK 26).
- Memory: `jgdms-uds-jeri-transport`, `jgdms-spiffe-auth`, `no-threadlocal-virtual-threads`,
  `spiffe-halow-mesh-federation`, `gls-instrument-federation`, `jgdms-der-std006`.

---

## Changelog

- **v0.1-DRAFT rev.4 (2026-07-05)** — added a normative **Connection liveness** section
  (**§6.4**, cross-referenced from §4.6), closing the gap that §4.6 required events to ride the
  **client-opened connection** but never said what keeps that connection alive against QUIC's
  idle timeout (RFC 9000 §10.1). Design (Peter, approved): the connection is a **leased
  resource** — kept alive by **Jini leasing**, not a separate QUIC PING. Pinned: **§6.4.1**
  leased-liveness invariant (connection alive **iff** the union of live leases — DGC reference
  + event-registration + any Jini lease — is non-empty; empties ⇒ idle-close); **§6.4.2** DGC
  **dirty-renewal as the natural keepalive** (defeats the idle timeout for free), and the
  **liveness ≠ authorization** separation (an unrelated DGC lease keeps the pipe open but does
  **not** authorize pushes once the subscription lease expires — §4.6.3); **§6.4.3** timing
  (renewal interval **<** idle timeout with margin; `max_idle_timeout` set above the
  `LeaseRenewalManager` cadence; no PING as primary); **§6.4.4** teardown (DGC clean / cancel /
  expiry ⇒ graceful idle-close, pushes stop); **§6.4.5** security — mTLS-authenticated,
  **server-granted-and-refusable, per-identity-capped** keepalive, with an explicit
  **connection-holding-DoS bound** ((per-identity connection cap) × (server-granted lease
  duration), renewable only with server consent — a client cannot hold a connection
  indefinitely); **§6.4.6** migration (a lease-kept-alive connection migrates with the roaming
  client per §6.3). §6 retitled to include "Connection Liveness". Conformance items **18–20**
  and harness item **9** added; References gain `Lease`/`LeaseRenewalManager` and the DGC-lease
  keepalive. No change to any earlier normative text.
- **v0.1-DRAFT rev.3 (2026-07-05)** — Peter adopted **all** board recommendations; this
  revision folds them in as normative text. **Two HIGH server-push authorization fences:**
  **(1) execution-subject swap (§4.7 rule 5)** — a pushed-event dispatch **MUST execute under
  the authenticated server's subject** (`Subject.doAs`/`callAs` + reduced ACC), not merely
  have its inbound check use the server identity; the client's ambient `Subject`/ACC MUST NOT
  leak in, and both the authorization decision and any downstream authority derive from the
  server. **(2) subscription-correlation (§4.6.3)** — a push is dispatched only to a listener
  the client holds an **outstanding subscription** for on that connection/peer; an
  authenticated-but-hostile server cannot invoke arbitrary listeners; unsubscribed push
  rejected. **F1 both-directions:** §5 DGC-ack made **direction-aware** (the *dispatcher*
  emits the ack on its send side — the **client** on a `0x01` push stream); §8.2 restated to
  apply to **every DER decode surface in both directions** (a client decoding a server-pushed
  body is a decode surface; authentication exempts nothing from the §4.5 ceilings). **In-band
  ack adopted, separate stream rejected;** the marker is **position-defined** (recognized only
  at the exact post-DER-response-object position, never content-scanned), even though the
  octet allocation stays [OPEN]. **Mediums:** §10.3 adds fuzzing of the **client-side §7.2
  reducing-domain decoder under a hostile server** (same §4.5 bounds + `jrt:` exclusion); §7
  Retry token pinned to a **keyed MAC/AEAD over (client-address, timestamp)** under a server
  secret, bounded freshness; §9.5 clarified that **subscription admission control is
  permitted** (it is the §4.6.3 enforcement, not flow-control rationing); **F3** (§4.6.1) the
  client advertises server-init `MAX_STREAMS` **≥ outstanding subscriptions** and the server
  **blocks (not drops)** on momentary exhaustion; **F4** (§4.6.1/§4.6.2) **push-primary
  default** — dial-back only when a reachable `ServerEndpoint` is explicitly advertised;
  **F2** (§11 item 1(b)) the non-callback-server-caller residual **downgraded from [OPEN]** —
  investigation found **no** such path; implementer **MUST re-verify against the exact mux
  build before deleting the mux**; **F5** STD-006 dependency pinned **≥ v0.12** (header +
  references), warning off the stale in-tree v0.10 copy. Conformance gains items **15
  (execution-subject), 16 (subscription fence), 17 (Retry-token MAC/AEAD)** and updates items
  4 (direction-aware/position-defined ack), 9 (both-directions bounds), 13 (push-primary +
  F3). Harness gains items 6 (execution-subject assertion), 7 (subscription negatives), 8
  (client-side §7.2 fuzz). §11 restructured: the three **interop-gates** (ack octets §4,
  RESET codes §2, ALPN §8) explicitly marked *deferred to before interop, not before build*;
  the ack design-fork item removed (in-band settled). §11 renumbered (10→9 items).
- **v0.1-DRAFT rev.2 (2026-07-05)** — added the **server-initiated stream** mapping and the
  **authorization role-reversal**, resolving the former §4.1 [OPEN]. The investigation
  established (with code evidence) that JERI's server→client path is today **dial-back** (the
  listener proxy bakes in a reachable `TcpEndpoint` host:port; the notifier invokes it as a
  fresh client call via `BasicObjectEndpoint.newCall`→`newRequest`), which QUIC's
  ephemeral-client topology voids. New/changed normative content: **§4.0** (two request
  directions on one connection; QUIC stream-type discrimination `0x00`/`0x01`; resolution of
  the former open item); **§4.6** — Event/callback delivery: server-initiated bidirectional
  `0x01` stream as the **primary** model for ephemeral clients (client accepts inbound
  *streams*, never inbound *connections* — a security + simplicity win), dial-back demoted to
  a restricted compat path (§4.6.2), WebTransport / gRPC server-streaming cited as precedent;
  **§4.7** — direction-aware caller-subject resolution (the must-solve): caller = **stream
  initiator** not connection initiator, so a server-initiated stream's caller is the
  authenticated **server** peer, with an explicit **confused-deputy guard** (never anonymous,
  never the client's own privileges); **§4.7.1** — the STD-006 §7.2 ACC **mirror** (a
  server-pushed on-behalf-of ACC flows server→client and is validated by the client against
  the server's authenticated workload identity). §2.2 table gains callback + caller-authz
  rows; §4.1 retitled to client-initiated; §4.2 mux-audit precondition updated (mux retires
  only once both directions are specified — now done); §9.4 cross-refs the mirror; conformance
  gains items 13–14; the interop matrix and harness (§10.2/§10.3) add server-push +
  authorization-inversion tests; §11 item 1 rewritten (resolved, with residual sub-items:
  dial-back-vs-push selection rule, and the remaining mux-audit check for any non-callback
  server-caller path).
- **v0.1-DRAFT (2026-07-05)** — initial draft. Establishes the normative wire contract for
  the QUIC-TLS JERI transport to gate the P3 build. Pins the review board's nine
  load-bearing points: (1) one request ⇄ one client-initiated bidi stream with FIN
  completion and a `RESET_STREAM` application-error-code convention wired to
  at-most-once/`getDeliveryStatus()` (§4); (2) the DGC application-level acknowledgment
  emitted after `RequestDispatcher` processing, riding in-band on the request stream (§5);
  (3) independent per-direction half-close preserving early-response (§6.1); (4) 0-RTT
  prohibition, fail-closed by construction via `max_early_data 0`, not by engine absence
  (§6.2); (5) the connection-migration identity invariant — handshake-pinned identity,
  path-only migration, PATH_CHALLENGE/PATH_RESPONSE gating, AEAD anti-splicing (§6.3, §9.3);
  (6) RFC 9000 §8 3× anti-amplification + stateless Retry (§7); (7) DER payload carried
  unchanged with STD-006 §4.5 size-bound-before-allocation retained against QUIC's
  bytes-not-elements flow control (§8); (8) handshake-time mTLS/SPIFFE identity, explicitly
  not layer-2, cross-referencing STD-006 §7.2 (correcting the SOWs' "§7.3") and STD-003
  §3.8/§7.6 (§9); (9) reliance on QUIC `MAX_STREAM_DATA`/`MAX_DATA` with no application-level
  rationing (§9.5). Adds a conformance section (§10.1), the polyglot interop matrix
  (§10.2 — quic-go, quiche, quinn, Neqo, nghttp3) and the conformance+fuzz harness as a
  required deliverable (§10.3), and carries ten open/board items forward (§11). Corrects the
  ACC-transport citation from STD-006 §7.3 (as the design SOWs had it) to §7.2 throughout.
