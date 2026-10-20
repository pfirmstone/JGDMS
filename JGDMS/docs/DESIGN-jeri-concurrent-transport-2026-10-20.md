# Design: a concurrent JERI connection/mux provider

Status: PROPOSAL for the maintainer's decision (2026-10-20). Nothing in this document is implemented. Container
numbers are marked; re-measure on the workstation machines before relying on them.

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
