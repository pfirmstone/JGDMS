# JGDMS-STD-010: QUIC-TLS JERI Transport

**Status:** Draft (working scaffold for review-board critique)
**Version:** 0.1-DRAFT
**Applies to:** JGDMS, DirtyChai (JDK fork), and non-JVM JGDMS participants that speak QUIC
**Depends on:** JGDMS-STD-003 (Multi-Subject Identity), JGDMS-STD-006 (Language-Neutral DER Wire Format)
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

**Genuinely new (this standard's subject):** the stream↔request mapping (§4), the DGC
acknowledgment carriage (§5), half-close (§4.5), 0-RTT prohibition (§6), the
connection-migration identity invariant (§6-migration), anti-amplification (§7), and the
QUIC-specific flow-control reliance (§9.5).

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

### 4.1 The one-to-one mapping (NORMATIVE)

A JGDMS QUIC transport **MUST** map exactly **one JERI request to exactly one
client-initiated QUIC bidirectional stream**. One `OutboundRequest` (client) /
`InboundRequest` (server) corresponds to one and only one bidirectional stream opened by
the requesting endpoint:

- The request `OutputStream` (`OutboundRequest.getRequestOutputStream`) writes the
  **send side** of that stream.
- The response `InputStream` (`OutboundRequest.getResponseInputStream`) reads the
  **receive side** of that stream.
- On the server, the accepted stream's receive side is the `InboundRequest` request body
  and its send side is the response body.

An endpoint **MUST NOT** multiplex more than one JERI request onto a single QUIC stream,
and **MUST NOT** split one JERI request across multiple streams. Stream identifiers follow
RFC 9000 §2.1 (client-initiated bidirectional streams have the two least-significant bits
`0x0`). This is the "retire the mux" mapping: QUIC's independent, head-of-line-blocking-free
streams replace `org.apache.river.jeri.internal.mux` wholesale.

**[BOARD] Server-initiated requests.** JERI requests are client-initiated in every current
transport, so this standard specifies only client-initiated bidirectional streams. If any
JGDMS subsystem requires a server-initiated request over an established connection, that is
a separate mapping (server-initiated bidirectional streams, `0x1`) and is **[OPEN]** — flag
for the board. The mux audit (below) MUST confirm no such reverse-request path exists before
the mux is deleted.

### 4.2 Mux-retirement audit (NORMATIVE precondition)

Before the mux is deleted, an implementation **MUST** audit `org.apache.river.jeri.internal.mux`
for any contract beyond multiplexing that this standard does not otherwise reproduce —
specifically its request framing, its abort semantics, its `AcknowledgmentSource`
signal (reproduced in §5), and the `ServerConnection` request-dispatch model — and **MUST**
reproduce every such contract from QUIC primitives or document its deliberate removal. A
QUIC endpoint MUST NOT ship with the mux "half-retired" (some invocations over QUIC streams,
some still framed by mux logic).

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
sets the mux `ackRequired` bit — DGC dirty/clean calls in particular), the server endpoint
**MUST** emit an application-level acknowledgment **only after** its `RequestDispatcher`
has **processed** the request (returned from dispatch for that `InboundRequest`), and the
client endpoint **MUST** deliver that acknowledgment to the DGC layer through the same
`AcknowledgmentSource` callback contract that `BasicObjectEndpoint` consumes today. FIN
alone **MUST NOT** be reported as an acknowledgment. Emitting the ack before dispatch
completes is a conformance violation.

### 5.3 How the ack rides the stream (NORMATIVE)

The acknowledgment **MUST** ride the **same bidirectional stream** as the request it
acknowledges — it is an application byte written on the server's send side of that stream
**after** the response body and **before** the server's stream FIN — so that it is ordered
after the response and needs no second stream, no separate connection-level frame, and no
QUIC extension:

```
server send side of request-stream S:
    [ response-body bytes ] [ DGC-ACK marker ] FIN
                            ^^^^^^^^^^^^^^^^^^
                            present iff the request was ack-required,
                            written only after RequestDispatcher returns
```

**Normative rules:**

1. The ack marker is a single, self-delimiting application token in the response
   byte-stream framing (its exact octet encoding is **[OPEN — PROPOSED]**: a reserved
   trailer tag in the JERI response framing, DER-encoded per STD-006 conventions so a
   non-JVM peer can parse it; see §11). It carries no data beyond "processed"; it is
   **not** the response payload.
2. Because it is written after the response body but before FIN, the client reads the full
   response, then the ack marker, then observes FIN — an unambiguous, in-order sequence on
   one stream. QUIC's ordered, reliable per-stream delivery (RFC 9000 §2.2) guarantees the
   client sees response-then-ack-then-FIN.
3. If the request was **not** ack-required, no marker is written; FIN immediately follows
   the response body. A client MUST NOT infer an acknowledgment from FIN alone (§5.1).
4. If the server's `RequestDispatcher` fails or the connection/stream is reset before the
   ack marker is written, the client MUST treat the request as **un-acknowledged** for DGC
   purposes (fail-closed: no lease state advance), consistent with the §4.4 reset-code
   classification.

**[BOARD] Interpretation note.** The pin says "reproduce the mux's `AcknowledgmentSource`/
`ackRequired` signal" and "specify how it rides the stream". The mux delivered the ack as a
distinct framed message on the shared connection; QUIC's per-request stream lets us ride it
in-band on the request's own stream (option chosen above) rather than on a separate
control stream. An alternative — a dedicated unidirectional ack stream per connection — is
possible but reintroduces cross-stream ordering concerns and a mux-like control channel, so
the in-band trailer is preferred. If the board prefers the separate-stream design (e.g. to
decouple ack from response backpressure), that is a design fork flagged in §11.

---

## 6. Handshake, Early Data, and Migration (PINNED POINTS 3, 4, 5)

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
   employ it when under load or facing suspected address-spoofing. Retry tokens **MUST** be
   integrity-protected and **MUST** be bound to the client address and a freshness window so
   a token cannot be replayed from a different address or after expiry.
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
the receiver stamps.

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

---

## 10. Conformance and Interoperability

### 10.1 Conformance

A conforming JGDMS QUIC-TLS transport endpoint:

1. Maps exactly one JERI request to one client-initiated QUIC bidirectional stream, never
   multiplexing or splitting a request across streams; retires the mux only after the §4.2
   audit (§4.1, §4.2).
2. Signals normal per-direction completion with the QUIC stream **FIN** and never conflates
   FIN with request processing (§4.3).
3. Aborts with **`RESET_STREAM`** carrying the §4.4 application-error-code convention, and
   maps it to `OutboundRequest.getDeliveryStatus()` so at-most-once retry is safe: retries
   only on the retry-safe (`false`, not-yet-processed) classification, never on the
   retry-unsafe one; resolves ambiguity to retry-unsafe (§4.4).
4. Emits the DGC application-level acknowledgment **only after** the `RequestDispatcher`
   processes an ack-required request, delivers it through the `AcknowledgmentSource`
   contract, rides it in-band after the response body and before FIN, and never reports FIN
   alone as an acknowledgment (§5).
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
   §4.5 size-bound-before-allocation ceiling, never substituting QUIC flow control for the
   decode bounds (§8).
10. Establishes peer identity by handshake-time mTLS/SPIFFE (certificate identity, not
    hostname), re-imposes algorithm constraints equivalent to the `ssl` transport, and does
    not treat this as a layer-2 identity (§9.1, §9.2).
11. Relies solely on QUIC `MAX_STREAM_DATA`/`MAX_DATA`/`MAX_STREAMS` for back-pressure, with
    no application-level flow-control rationing (§9.5).
12. Conforms to RFC 9000, RFC 9001, and RFC 9002 for all transport behaviour not otherwise
    constrained here, and to RFC 8446 for the TLS 1.3 handshake.

### 10.2 Interoperability matrix (NORMATIVE — required for release)

Interoperability is **unverifiable without this normative spec**, and this standard exists
so that a non-JVM peer can be built and audited from the document alone. A JGDMS QUIC
endpoint (both as client and as server) **MUST** be interop-tested against the following
polyglot QUIC implementations, exercising the RFC 9000/9001/9002 baseline **and** the
JGDMS-specific behaviours of §§4–9 (stream↔request mapping, FIN/RESET completion, the DGC
ack marker, half-close, 0-RTT refusal, migration path-validation, anti-amplification), each
paired with a DER payload smoke test (§8):

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
JGDMS QUIC-JERI protocol is **[OPEN]** (proposed: a registered `jgdms-jeri`-class token) and
flagged in §11; interop peers must be drivable at the QUIC-stream layer with that ALPN.

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

---

## 11. Open Questions Carried Forward

Consolidated list of every **[OPEN]** / **[BOARD]** above, for the next working session and
the board's adjudication:

1. **§4.1 — server-initiated requests.** This standard maps only client-initiated
   bidirectional streams. Confirm no JGDMS subsystem needs a server-initiated request over
   an established connection (the mux audit, §4.2, must verify this before deleting the mux).
2. **§4.4 — application error-code allocation.** The symbolic codes are pinned; the actual
   QUIC application-error-code varint values (and the JGDMS-reserved range) are unallocated.
   Assign concrete numbers.
3. **§4.4 — reset-code granularity.** JERI `getDeliveryStatus()` is binary; this standard
   collapses "partially processed" and "processed-but-response-lost" into one retry-unsafe
   class. Confirm the board does not need a finer signal (if so, it belongs to the §5 ack,
   not the reset code).
4. **§5.3 — DGC ack marker octet encoding.** The in-band trailer marker's exact wire form
   (a reserved DER-encoded response-framing trailer per STD-006) is proposed but not fixed.
   Define it so a non-JVM peer can produce/consume it.
5. **§5 — ack carriage design fork.** In-band trailer on the request's own stream (chosen)
   vs a dedicated per-connection ack stream. Ratify the in-band choice or flag the
   alternative.
6. **§6.3 — migration path-validation strictness.** This standard forbids *all* stream
   traffic on an unvalidated path (stricter than RFC 9000, which permits limited
   anti-amplification-bounded traffic). Confirm the stricter reading.
7. **§9.2 — algorithm-constraint enforcement mechanism.** The "Path A" trust relaxation
   drops SunJSSE algorithm-constraint enforcement; this standard requires JGDMS re-impose
   equivalents but leaves the mechanism engine-dependent. Pin the mechanism.
8. **§10.2 — ALPN token.** The JGDMS QUIC-JERI ALPN token (for raw-stream, non-HTTP/3
   interop) is unallocated. Register a `jgdms-jeri`-class token.
9. **§1 [BOARD] — Path A vs Path B.** The wire contract is path-independent, but the build
   still needs the path decision (own the QUIC transport on the DirtyChai-exposed engine vs
   Kwik + auth bridge). Out of scope for this standard; tracked here for the board.
10. **Cross-transport endpoint selection.** Tri-export across TCP/UDS/QUIC with graceful
    degradation (UDP-blocked networks fall back to TCP) generalises the UDS SOW §7 concern;
    it is an `Endpoint`-selection question, not a QUIC-wire question, but affects how a QUIC
    endpoint advertises and degrades. Flag for a companion note.

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
- `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` — the DER object wire this transport carries
  unchanged (§8); **§4.5** size-bound-before-allocation; **§7.1** `UserSubjectBlock`;
  **§7.2** `AccessControlContextRecord` / `ReducingDomainRecord` (the ACC reducing-domain
  transport — the correct citation, correcting the SOWs' "§7.3").
- `JGDMS-STD-003-MultiSubjectIdentityArchitecture` — the multi-subject / SPIFFE two-gate
  identity model (§3.8 / §7.6 cross-referenced by STD-006 §7.2 and by §9 here).
- Source (to be built): the proposed `net.jini.jeri.quic.{QuicEndpoint,QuicServerEndpoint}`
  pair on the JERI `Endpoint`/`ServerEndpoint`/`connection.Connection` SPI; the retired
  `org.apache.river.jeri.internal.mux.*`; the reused `net.jini.jeri.ssl.{AuthManager,
  ClientAuthManager,ServerAuthManager,SubjectCredentials,Utilities}` auth logic;
  `net.jini.jeri.BasicObjectEndpoint` (the DGC client, §5).
- RFCs: **9000** (QUIC transport), **9001** (using TLS to secure QUIC), **9002** (loss
  detection / congestion control), **8446** (TLS 1.3), **2119**/**8174** (requirements
  language), **6125** (endpoint identification — deliberately *not* used, §9.2). JEP **517**
  (HTTP/3 for the HTTP Client API — the SunJSSE QUIC-TLS engine, JDK 26).
- Memory: `jgdms-uds-jeri-transport`, `jgdms-spiffe-auth`, `no-threadlocal-virtual-threads`,
  `spiffe-halow-mesh-federation`, `gls-instrument-federation`, `jgdms-der-std006`.

---

## Changelog

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
