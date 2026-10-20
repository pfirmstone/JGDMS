# Design: a concurrent JERI connection/mux provider

Status: SUPERSEDED IN PART (2026-10-20). After the board review and the Fable gap review
(`docs/BOARD-REVIEW-jeri-concurrent-transport-2026-10-20.md`) the plan below is NOT approved as written: the
board's verdict is an in-place programme first (section 9), and a new provider only if that leaves a gap. The
design text is kept as the record of the proposal. Nothing in this document is implemented. Container
numbers are marked; re-measure on the workstation machines before relying on them.

## 0. Production transports (maintainer, 2026-10-20)

TLS is the default for all internet-based communication; plain TCP is not expected in production; UDS is used for
process isolation. Consequences for this design, to be taken into every stage and gate:

* The production targets are `net.jini.jeri.ssl` (mutual TLS over blocking `SSLSocket`/`SslEngineChannel`; its
  `Connection.getChannel()` is null) and `net.jini.jeri.uds` (plaintext, channel-based). Plain TCP is a test and
  benchmark transport only.
* All measurements so far (`RESULTS.md`) are plain-TCP loopback. They show the codec and the mux path, but not what a
  production call costs. TLS adds record encryption on every frame and a handshake per connection; UDS removes the
  network stack and the TLS cost. The design must be re-baselined on both before any stage is judged.
* Striping (several connections per endpoint) costs a TLS handshake and a session per connection and multiplies
  credential state; for TLS the default pool size is 1 until measured, and every reuse must go through the endpoint's
  per-subject/per-constraint choice (board findings A1, B1). For UDS, extra connections are cheap and the pool matters
  most for many local callers.
* The seam: SSL and Kerberos accept `setConnManagerFactory`; `UdsEndpoint` and `TcpEndpoint` construct their
  managers directly (board finding B2). UDS must be brought behind the seam in stage 0.
* The comparison with Java RMI over secure sockets is like for like: RMI can use SSL socket factories with client
  authentication, and the maintainer's DirtyChai changes let RMI run requests under the user's `Subject`. The
  comparable pairs are therefore JERI over TLS against RMI over TLS (mutual authentication, requests run as the caller's
  Subject on both), and JERI over UDS (JOSS against DER); RMI has no UDS. The RMI benchmark must be configured exactly
  that way, not as the plain-socket RMI measured so far.

## 1. Why

The 6-core sweep (`jgdms-benchmarks/serialization/RESULTS.md`, twentieth section) shows JERI DER at 0.4 to 0.6 of
RMI's throughput for small calls at every thread count, and the small-call curve flattening at 24 callers
(`ping` 49 calls/ms, then 38 at 48). A container measurement (4 cores, 16 callers, DER `ping`) found the mux
writer thread at 0.80 of a core and the reader at 0.64. What the current design costs, read from the code and
the measurements:

| Cost | Where | Measured / evidence |
|---|---|---|
| One writer thread and one reader thread per connection | `StreamConnectionIO` | writer 0.80 core, reader 0.64 core at 16 callers |
| A thread hand-off for every message (caller to writer through a queue and `notifyAll`) | `StreamConnectionIO.asyncSend/futureSend`, `Writer.run` | about 85 us fixed per call against RMI's 53 (container) |
| One `muxLock` guards the queue, the session table and every session's state | `Mux`, `Session`, `MuxOutputStream` | all writers and the reader contend on it |
| One connection per endpoint until 256 requests are in flight | `ConnectionManager.connect` | a sequential or concurrent caller set shares one writer |
| A 4-byte header buffer allocated per message; header and payload written as two buffers | `Mux.asyncSendData`, `futureSendData` | allocation and two writes per message |
| 1 KiB fragments written one at a time, each awaited | `MuxOutputStream.writeBuffer` | fixed for bulk writes (twelfth section: `blob64k` 6 times faster) |
| `wait/notify` on monitors for credit and for data | `MuxOutputStream`, `MuxInputStream` | parks carriers of virtual threads that could unmount |

## 2. The seam: JERI is extensible, so this is a new component, not an edit

`ConnManagerFactory.create(ConnectionEndpoint) -> ConnManager` (client) and
`ServerConnManager.handleConnection(ServerConnection, RequestDispatcher)` (server) are the replaceable parts.
`EndpointInternals.setConnManagerFactory` already installs a different manager into an endpoint (discovery
uses it). A new provider implements these two interfaces and everything below them: its own connection pool,
its own multiplexer, its own threads. The existing `ConnectionManager`/`Mux` stay as the default and as the
interoperable fallback; the new one is selected by configuration, endpoint by endpoint, so it can be tried on
one machine and compared against the old one with the same benchmarks (`sweep.py`).

Security semantics stay with the caller of the seam and must not change: the endpoint's `connect(handle)` does
the transport's constraint and credential checks for each new connection; the server captures the exporter's
security context and subject and dispatches each request under them; a decoded endpoint is interned
(`Resolve`) so connection state is shared.

## 3. The data path

**Writer: no writer thread.** Producers append a frame to a lock-free multi-producer queue. The producer that
finds the queue empty becomes the drainer: it writes a bounded batch with a gathering write, then, if frames
arrived meanwhile, continues or hands the role on (single-drainer state in one atomic). A call on an idle
connection then costs no thread hand-off, and a busy connection batches many frames per system call. The
drainer is a caller thread (often a virtual thread); a blocking channel write unmounts it. Back-pressure is
the credit scheme below, so the queue is bounded by credits.

**Framing: one buffer.** Header and payload are written into one pooled direct buffer (or the header into the
pooled buffer and a large payload gathered), with no per-frame allocation. Large writes go straight from the
caller's array in ration-sized pieces, as `MuxOutputStream.writeBulk` does now (waiting once, never releasing
the array early).

**Reader: one virtual thread per connection.** It reads into a pooled direct buffer, parses frames, and routes
by session index into a fixed 256-entry array. It takes no global lock: each session's state is a small
atomic/`VarHandle`-managed object. Waking a consumer uses `LockSupport.unpark`, which a virtual thread
handles without pinning, instead of `Object.wait/notifyAll`.

**Sessions and credit.** A session is an array slot allocated from an atomic bitmap. The ration (credit) is an
atomic counter; an increment is sent once half the window is consumed, batched into one frame (as now). The
window can be larger than 32 KiB by default, since the fragment limit is no longer a hand-off cost.

**Striping.** A connection pool per endpoint, created lazily: a request takes the less loaded of two random
connections (power of two choices) using atomic in-flight counters, no scan under a lock. A new connection is
opened only when every connection's load is above a threshold and the pool is below its limit, so a sequential
caller never opens a second one. The limit defaults to a function of the core count and is configurable.
Idle connections are reaped as now.

**Limits kept from the DER work.** No buffer is sized from a declared length (B1); frame sizes, sessions per
connection and connections per endpoint are bounded; a malformed frame closes the connection.

## 4. Wire protocol

Two options; the decision is the maintainer's.

* **A. Keep the JERI mux wire format** (recommended for the first cut). The new provider interoperates with
  `TcpServerEndpoint`/`MuxServer` peers in both directions, so it can be deployed gradually and tested against
  the old implementation. The format caps a data message at 0xFFFF bytes and uses a 4-byte header; that is
  acceptable once headers are not separate allocations and writes are gathered.
* **B. A new, versioned wire format** negotiated in the connection header, with the old format as the fallback.
  More freedom (larger frames, 32-bit credit, a request identifier in every frame), more review.

## 5. Stages, each with a measurement gate

1. **Writer path.** Lock-free queue, in-thread drain, single-buffer frames. Gate: DER `ping` single-caller
   latency and the 6-core `ping`/`record` curves against the current implementation.
2. **Reader and sessions.** Lock-free routing, atomic credit, `unpark`. Gate: throughput at 24 and 48 callers.
3. **Striping.** The pool and load-based selection. Gate: the 32- and 80-core sweeps; sequential callers must
   still use one connection.
4. **Server side.** `ServerConnManager` with the same data path; request dispatch on virtual threads under the
   exporter's captured context, as now.

Each stage is merged only if it is no slower on `ping`, `record`, `batch100` and `blob64k` for one caller, and
faster on the curve it targets.

## 6. Tests

* The existing JERI and DER round-trip suites, run with the new provider selected.
* A protocol test class like `MuxBulkWriteTest`: a real client against a real server over loopback, sizes
  around every limit, concurrent requests, ordering within a session, credit exhaustion, abort, interrupt,
  connection loss mid-write and mid-read, a peer that never reads.
* Interoperability (option A): new client against the old server and the reverse.
* Stress and fault injection for the lock-free parts (the single-drainer hand-off, the credit counter, session
  allocation and reuse); mutation testing of each as for the earlier work.
* Constraint and subject tests: the same request-context assertions the current provider satisfies.

## 7. Risks

Lock-free queues and single-drainer protocols are easy to get subtly wrong; they need the stress tests above
and a conservative first cut. A caller thread doing the write means a slow peer can block a caller thread in
the write (today the writer thread blocks instead); credits bound that, and a write timeout is needed.
Transports with costly connections (TLS/Kerberos handshakes) should keep a pool of one by default.

## 8. Decisions needed

1. Wire format: keep (A) or version it (B)?
2. Scope of the first cut: client and server data path with the pool (stages 1 to 4), or stages 1 and 2 first
   and measure before striping?
3. Selection: a system property/configuration entry per endpoint, with the old provider the default until the
   new one has passed the gates?

## 9. Revised plan after the board and the gap review (replaces sections 5 and 8 until the maintainer decides)

The reviewers (three Opus seats and a Fable gap review) disagreed on the mechanism but agreed on the direction:
measure the central hypothesis cheaply, and fix the mux in place before replacing it. Key corrections to this
document: the right seam is `ConnectionIO` inside the mux (it leaves `Session` and `Mux`, and therefore every
contract the old layer honours, untouched), not `ConnManagerFactory`; a caller-thread drainer is unsound for
virtual-thread callers and blocking stream transports (an interrupt closes the socket), so channels get
non-blocking caller writes and streams keep an owned writer; the reader never writes; striping must go through
`ep.connect(handle, candidates, idle)` and a pool of one for TLS; the larger window is not wanted.

Production transports are TLS and UDS (section 0), so the measurements below are taken on those, with plain TCP
only as the cross-check against the earlier numbers.

0. **Evidence first.** Add UDS and mutual-TLS variants of `JeriBenchmark`, and RMI over TLS using
   `jgdms-rmi-tls` (`TlsRMIClientSocketFactory`/`TlsRMIServerSocketFactory`, Subject credentials, client
   authentication). Single-caller JFR of DER `ping` on each transport; per-thread CPU at the knee of the sweep on the
   workstation (Windows, 12 logical CPUs, JDK 27); the connection threshold experiment (`ConnectionManager.connect`,
   `n < K`); connection counts during the sweep; JFR `VirtualThreadPinned` on JDK 27. Decision gate: where do the
   127 us go?
1. **One write per frame** in `StreamConnectionIO.Writer` (header and payload coalesced; the reader's `available()`
   dropped). Gate: single-caller `ping`/`record` and the sweep curve.
2. **Acknowledgment listeners notified on connection loss**, and a mux acknowledgment timeout (a defect found by the
   gap review: listeners are only notified on abort and ack).
3. **Striping inside `ConnectionManager`**, only if stage 0's threshold experiment is positive: endpoint-approved
   candidates, CAS slot reservation, handshake-aware, a pool of one for SSL/Kerberos.
4. **A new `ConnectionIO`** only if stages 1 to 3 leave a measured gap.

## 10. Stage 0 outcome (2026-10-20, container; the workstation and the 32/80-core machines still to run)

Full tables are in the twenty-third section of `jgdms-benchmarks/serialization/RESULTS.md`. In short:

* (CORRECTED by section 10.1 and the RESULTS correction: with the server in its own JVM UDS and TCP take the same 123 us for one caller; hand-offs 6.1 against 8.1, CPU 137 against 167 us.) The UDS transport (channel I/O) already needs 5.5 thread hand-offs and 129 us of CPU per call against 8.2 and 195 us for TCP (stream I/O) and 8.3 and 260 us for TLS; RMI over TLS needs 2.0 and 66 us. The per-connection writer and reader threads of `StreamConnectionIO` cost about 2.7 hand-offs and 65 us.
* At 16 callers on 4 cores JERI leaves 30 to 40% of the CPU idle and RMI over TLS uses 93%: the limit is waiting on one connection, not CPU.
* E3 (connection threshold) is positive: TCP +50%, UDS +16%, TLS +14% (noisy), with 2 to 4 connections capturing it. Stage 3 may proceed for TCP and UDS; TLS keeps a pool of one until a handshake-aware measurement exists.
* No steady-state virtual-thread pinning on JDK 27.
* Decision: proceed to stage 1 (stream path: no writer thread hand-off, one write per frame) and stage 2, then stage 3 with a bound of 2 to 4 and endpoint-approved reuse. The new-provider design stays unapproved.

### 10.1 Fable review of stage 0 (2026-10-20) and corrections to section 10

* The UDS rows were measured in one JVM, where the single `SelectionManager` poller (`concurrency = 1`) serves both ends of every connection; production never has that. Re-run UDS with the separate server JVM (`sweep.py` does) and read the client alone before quoting 5.5 hand-offs or "65 us for the stream threads".
* E3 spread calls over 16 connections that were already open; "2 to 4 connections capture it" is inferred from fill order, not counted (log `using {0}` at FINEST and count per K). Only TCP's +50% is clear; UDS +16% and TLS +14% are within about 1.5 sigma of their baselines, and UDS K=1 (33.5) and TLS K=2 (15.0) were worse than today.
* "The limit is waiting on one connection" is not established for UDS: the single poller and the common wake-up chain explain the data as well. A two-poller experiment (`SelectionManager` concurrency 2) is needed before striping UDS.
* Stage order proposed by the reviewer: stage 2 first (a correctness fix that striping and write timeouts would make worse); stage 1 is a TLS lever only (`SslConnection.getChannel()` is always null) and, for TLS, means coalesced single writes from an owned writer, about the 10% of board finding C-F6; a stage 1b for UDS (poller concurrency) before stage 3; stage 3 TCP-first, with the lazy-open rule serialising only the open step so the 16-connection cold burst goes away.
* Security items not in the board review: UDS increment 2 makes connections identity-bearing (SVID), so a UDS pool must use the same per-subject reuse gate as SSL from the start; stripes can diverge in unfulfilled constraints; striping multiplies server-side connections and handshakes with no per-peer cap or handshake deadline (A6); an interrupted or timed-out write into an `SSLSocket` leaves a partial record, so any write failure, including the interrupted-but-not-failed case, must tear the connection down before another frame; a caller-thread write under `StreamConnectionIO`'s monitor lets a peer that stops reading block every caller, so a write timeout plus teardown comes before removing the writer thread.
