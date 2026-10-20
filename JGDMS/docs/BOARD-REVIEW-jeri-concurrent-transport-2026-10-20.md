# Board review of DESIGN-jeri-concurrent-transport-2026-10-20 (reports as received)

Three reviewers (all Opus), read-only, each given the design and the board guidance. Reports follow in the reviewers'
own findings, lightly condensed where noted. A Fable gap review follows in a later section.

---

## A. Adversarial-security seat: approve with changes

1. HIGH, CONFIRMED. The striped pool picks connections itself instead of letting the endpoint choose (confused deputy).
   Today `ConnectionManager` passes active and idle lists to `ep.connect(handle, active, idle)` (`ConnectionManager.java:205-220`).
   SSL reuses a connection only if `useFor` passes: same `clientSubject` (`==`), right client and server principals, an
   acceptable cipher suite, an unexpired session, live credentials (`SslConnection.java:873-959`), and runs
   `checkConnectPermission` against the caller's access context on every reuse (`SslEndpointImpl.java:1017-1069`). TCP does the
   same permission check (`TcpEndpoint.java:810-873`). "Less loaded of two random connections, no scan" sends subject B's
   call over subject A's mutual-TLS connection; the server sees A as the ClientSubject. Required: every reuse goes through
   `ep.connect(handle, candidates, idle)` on the caller's thread for every request (power-of-two only orders the candidates);
   if null, retry with the full list, then `ep.connect(handle)`; at the pool limit go over the limit or wait with a deadline,
   never use a connection the endpoint did not return; one subject's connections must not starve another's; negative test:
   two subjects on one endpoint reach the server as two different ClientSubjects.
2. HIGH, CONFIRMED. The per-request server checks live above `MuxServer` and the design drops them. The security context
   and `Subject.current()` are captured synchronously inside `handleConnection` (`MuxServer.java:112-113`); the SSL endpoint
   depends on it by wrapping the call in `callAs` (`SslServerEndpointImpl.java:1405-1429`). A reader thread has
   `Subject.current()==null` (`MuxServer.java:275-281`); lazy capture on a new virtual reader would dispatch with a null
   subject. Per request, `processRequestData` checks session expiry, re-checks TLS 1.3 and credentials
   (`SslServerEndpointImpl.java:1740-1830`), then `checkPermissions` runs `checkAccept` and the AuthenticationPermission check
   (`:1880-1901`); invoked by `ServerConnectionManager.Dispatcher` (`:139-147`); `Inbound` passes `populateContext` and
   `checkConstraints` on (`:184-200`). Required: reuse the existing Dispatcher/Inbound wrappers unchanged, capture the context
   synchronously, negative tests for an expired session and for denied accept/authentication permission.
3. HIGH (availability), CONFIRMED that the frames exist: `PingAck` (`Mux.java:1080`), Abort replies (`Session.java:360,402`),
   IncrementRation. If the reader drains them itself, an old peer can send PINGs and never read; the reader blocks in the write
   and every session on the connection stops; two new-code peers can deadlock, each reader writing, neither reading. If the reader
   only queues them, the queue is unbounded (credits do not cover control frames; today's queue is unbounded too,
   `StreamConnectionIO.java:90,118`) and frames can sit with no caller left to send them, starving credit and leaving a DGC
   acknowledgment unsent. "The queue is bounded by credits" is false for control frames. Required: the reader never blocks in a
   write; control frames go out through a non-blocking attempt or a small virtual thread on demand; pending control frames per
   connection are capped and a flood closes the connection; the drainer hand-off must not lose wakeups.
4. MEDIUM, PLAUSIBLE (JDK behaviour). Caller thread doing the writing: caller A held hostage writing other callers' frames past
   A's deadline; interrupting a caller while it drains (`Future.cancel(true)`, structured-concurrency shutdown) closes the shared
   channel, failing every caller's in-flight calls (today writes run on a system thread nobody interrupts); writes run under
   A's access context and Subject, so an endpoint socket factory (part of the TcpEndpoint serial form, possibly downloaded code)
   sees A's subject while writing B's bytes; a timed-out partial frame followed by another throws the framing off. Required:
   drain inside doPrivileged with the context captured at connection open; limit each drain to the caller's own frames plus a
   small batch; shield the drainer from interrupts; any write failure or timeout takes the connection down and every session
   with queued frames reports `getDeliveryStatus()==true`.
5. MEDIUM, CONFIRMED guards exist today: a slot is not freed until the acknowledgment has been sent (`Session.java:420-432`,
   bug 4827402); every message checks session state (`:312-315, 445-457, 481-484, 529-534`); ration overflow and
   input-ration-exceeded are rejected (`:317, :486`). A lock-free bitmap that frees a slot when the local side finishes lets a
   late Ack or Data frame land in the next caller's session (wrong DGC acknowledgment). Required: port every ProtocolException
   check with a negative test, free a slot only under the same conditions, check overflow inside the CAS loop.
6. MEDIUM. PLAUSIBLE: a window above 32 KiB raises server memory to 256 sessions x window per connection (8 MiB today) x
   connections a hostile client opens; keep the advertised window conservative and cap bytes buffered per connection.
   CONFIRMED: the server has no handshake timeout (`MuxServer.java:109` passes `0L`); PLAUSIBLE: a pooled direct buffer per
   reader means idle slow-handshake connections hold direct memory. Required: a server handshake deadline; release the buffer
   while the reader is blocked.
7. LOW, CONFIRMED. The premise about the seam is wrong: `TcpEndpoint` builds a `ConnectionManager` directly
   (`TcpEndpoint.java:152,215-244`) and `TcpServerEndpoint` uses a static `ServerConnectionManager` (`:124`); only SSL and
   Kerberos have `setConnManagerFactory`, gated by `EndpointInternalsPermission`. The benchmarks use TCP. Selecting the provider
   by system property must not become an unchecked way around that permission, and the choice must never be part of an
   endpoint's serial form or `equals`.
8. No reconstruction doors found. The design should state the reader only routes bytes and never calls `processRequestData`
   or unmarshals anything.

Outcome-to-mechanism: the endpoint's constraint/credential checks (design: "for each new connection" only; needs ep.connect with
candidates for every reuse); exporter context at dispatch (partly); per-request server checks (absent); "queue bounded by
credits" (false for control frames); malformed frame closes the connection (not stated); DGC ack and delivery status (silent);
frames/sessions bounded (stated, no negative test). Keeping the wire format is safe against old peers only if findings 3 and 5
are fixed.

Verdict: approve with changes: findings 1, 2, 3 as required mechanisms with negative tests; the drainer rules in 4; state
machine parity in 5; server handshake deadline and per-connection buffering cap in 6; state how DGC ack and delivery status
carry over; fix the TCP seam premise in 7.

---

## B. JERI-transport seat: approve with changes

Findings:

1. CRITICAL, CONFIRMED. Striping bypasses the per-request check on connection reuse (as A1; `ConnectionManager.java:220`;
   `TcpEndpoint.java:794-860,969`; `SslConnection.java:851-990,1004`; `SslEndpointImpl.java:1000-1075`). Required: pass sampled
   candidates through `ep.connect(handle, candidates, idle)`; key pools by the endpoint's choice.
2. CRITICAL, CONFIRMED. TCP and UDS do not go through the proposed seam: `TcpEndpoint.java:236-237`,
   `TcpServerEndpoint.java:124-125`, `UdsEndpoint.java:193`, `UdsServerEndpoint.java:129` construct `ConnectionManager` and
   `ServerConnectionManager` directly; only SSL and Kerberos implement `setConnManagerFactory` (`SslEndpoint.java:644`,
   `KerberosEndpoint.java:953`). The benchmarks use `TcpServerEndpoint`, so the measured path cannot select the new provider
   without editing the endpoints, which contradicts "a new component, not an edit".
3. HIGH, CONFIRMED. The reader thread must never become the drainer: it sends PingAck (`Mux.java:1080`), Abort
   (`Session.java:360,402`), Error (`StreamConnectionIO.java:306`); credits do not bound control frames; if both peers' readers
   block in writes into full TCP buffers, neither reads again.
4. HIGH, PLAUSIBLE (JDK interrupt semantics; old contract CONFIRMED). One caller's interrupt kills the whole connection if the
   drainer is a caller thread; today an interrupt takes down only that caller's session (`MuxOutputStream.java:147,307`). The
   drainer must shield interrupts.
5. HIGH, CONFIRMED. Session state and its frame must be emitted atomically. Today every state change and its enqueue happen
   under `sessionLock`; a lock-free version can put Data after EOF/Abort on the wire or an Ack onto a reused session ID; the peer
   throws ProtocolException (`Session.java:449-457,481,529`) and the whole connection is lost. Keeping the wire format needs
   per-session linearizability.
6. HIGH, CONFIRMED. The DGC Acknowledgment is absent from the proposal and its tests (AUDIT-1).
7. MEDIUM, CONFIRMED. A window above 64 KiB is silently corrupted today: IncrementRation is sent as `putShort((short)increment)`
   (`Mux.java:519,524`) but `inc` can equal the whole `initialInboundRation` (`MuxInputStream.java:73,156,226`). A larger window
   requires the shift encoding, which old peers already decode (`Mux.java:850`).
8. MEDIUM, CONFIRMED. The "never concurrent" guarantee is already broken: the javadoc says `connect(handle,active,idle)` is never
   called concurrently (`ConnectionEndpoint.java:155`); JGDMS calls it outside the lock (`ConnectionManager.java:205-220`); the
   current code also opens one connection per caller in a cold burst, which can skew the sweep's connection count (a confound
   for the measurements).
9. MEDIUM. Part of the motivation is already met or stale: with `useNIO=true`, `SocketChannelConnectionIO` already writes on the
   caller's thread when the queue is empty (`:132,157`); TCP defaults to false (`TcpEndpoint.java:128`), so this was never
   measured as a baseline. PLAUSIBLE: since JDK 24, wait/notify no longer pins a virtual thread's carrier, so cost row 7 does not
   apply on the target JDK.

AUDIT list (contract; severity; reproduced? ; must add):
1. Ack: server sets ACK_REQUIRED if listeners exist (`MuxOutputStream:345`); the client acks when it closes the response stream
   (`MuxInputStream:255`); the server fires listeners true on ack, false on abort (`Session:461,369`); the session ID is held until
   the ack is sent (`Session:426-432`); consumer `BasicObjectEndpoint:734-752`. CRIT; No; the full ack state machine and an
   interop test.
2. `AcknowledgmentSource` is put into the server's context (`Session:203-220`). CRIT; No.
3. Reuse checks per request (finding 1). CRIT; No.
4. Delivery status: true once data is queued (`MuxOutputStream:168,340`); server Abort sets PARTIAL (`Session:259,343`); false if
   readResponseData failed (`ConnectionManager:536`). HIGH; silent; set before the frame is visible to the drainer; fail-closed.
5. Early response: Close instead of Abort, and `fakeOKtoWrite` (`Session:249-257,379-434`). HIGH; silent; reproduce and test.
6. Role asymmetry: client refuses an Open from the server (`Mux:249`); Shutdown and Close server-to-client only, Ack and
   Data/ackRequired the other way (`Mux:1068`, `Session:380,440,474`). HIGH; silent; keep separate client and server roles.
7. `getUnfulfilledConstraints` comes from the Connection; the mux's own version throws (`Session:161-167`); hooks
   `writeRequestData`, `readResponseData`, `processRequestData`, `checkPermissions` (`ConnectionManager:731`,
   `ServerConnectionManager:141-147`). HIGH; partly; a naive EMPTY return would wrongly report every constraint as met.
8. Server captures `Subject.current()` and `Security.getContext()` synchronously in `handleConnection`; SSL binds the subject only
   around that call (`MuxServer:112-113`, `SslServerEndpointImpl:1405-1428`). HIGH; "as now"; capture before handing work to the
   reader's virtual thread.
9. Session IDs: lowest free first, 256 limit (`MuxClient:77-79`); reuse only after the old session terminates (`Session:529`).
   MED; partly; clear the bitmap bit only after the session's last frame is enqueued.
10. Initial ration 0 means unlimited (`Session:141,145`); window <= 0xFFFF00 and a multiple of 256 (`Mux:158`); exceeding inbound
    ration is a protocol error (`Session:486`). MED; silent; atomic counter must keep 0 as unlimited and the inbound bound.
11. Handshake: client timeout 15 s, started once lazily (`Mux:210-231`, `ConnectionManager:381-405`); server has no timeout;
    NEGOTIATE flag (`Mux:740`). MED; No.
12. Ping/PingAck: an unexpected cookie is a protocol error (`Mux:1089`); Error is sent and awaited before close
    (`StreamConnectionIO:306-319`). LOW; send Error first.
13. Graceful shutdown: Shutdown only when no session is busy (`MuxServer:164,182`); `shouldRetry` is false. LOW; document.
14. Reaper: two-phase 15 s idle; pending reservations protect a connection just picked (`ConnectionManager:428,438-453`). MED;
    "as now"; make reserve-or-close a single atomic action.
15. `getChannel()` is optional: SSL returns null even on the SSLEngine path (`SslConnection:736`). MED; partly; a stream-only
    path with no direct buffers or gathering writes.
16. Final server Data is sent synchronously (`MuxOutputStream:361`). LOW; silent; keep.
17. Pooled read buffers: today each frame gets its own buffer handed to the stream (`Mux:835`). MED; No; copy or reference-count
    payloads; reusing a buffer too early leaks bytes across sessions.

Three-axes check: (a) byte flow unchanged; (b) request origination implicitly preserved, but a shared client/server data path
puts the role asserts at risk (AUDIT-6); (c) authorization is broken by striping (finding 1). "Keep the wire format" is consistent
with in-thread draining and lock-free routing only with per-session atomic state-plus-enqueue, a reader that only enqueues, and ID
release ordered after the session's last frame. Stage problems: a stage 0 is missing (put TCP and UDS behind the seam, define
selection, add a useNIO=true baseline); stage 1 can only be built as an edit of `ConnectionIO`, inside a new provider it needs
stage 2's reader to be measurable; stage 3 is a security change gated only on throughput; stage 4's "as now" hides AUDIT-1, 2, 7, 8;
the larger window belongs in stage 2 and needs the shift encoding.

Verdict: approve with changes: route all reuse through `ep.connect(handle, candidates, idle)`; add stage 0; specify the
reader-never-drains rule, interrupt shielding and per-session atomic emission; reproduce AUDIT-1 to 17 each with a negative test,
including ack interop both ways and a delivery-status test after connection loss; shifted IncrementRation above 64 KiB; resolve
finding 8. Nothing old gets deleted until every audit item is reproduced or its removal explicitly accepted.

---

## C. Concurrency-and-performance reviewer: approve with changes

"The direction is right, but the drainer as specified has several correctness defects, the latency claim is overstated, and three
cheap experiments should run before anything new is built."

F1. HIGH, CONFIRMED (JDK 21 documented behaviour plus code). A caller-thread drainer turns one caller's interrupt into an outage:
interrupting a thread blocked in a blocking `SocketChannel` write closes the channel; interrupting a virtual thread blocked in
`Socket` I/O also closes the socket. Today nobody interrupts the writer (`StreamConnectionIO.java:100`, comment `:176-180`). Any
`Future.cancel(true)`/`shutdownNow` hitting a caller while it drains kills up to 255 other requests, whose partialDeliveryStatus is
already true so none can be retried. Clearing the interrupt before the write does not help. A non-blocking SocketChannel write does
not install the interruptor, which is why the existing NIO path (`SocketChannelConnectionIO.java:132-134,157-161`) is interrupt-safe.

F2. HIGH, CONFIRMED. The reader becomes a drainer and can deadlock both peers (PingAck `Mux.java:1080`, server header `:734`, Abort
`Session.java:360,402`, Error `StreamConnectionIO.java:306`). Rule: the reader only enqueues and wakes; it never drains. Control
frames are not bounded by credit, so "bounded by credits" is false; data alone is bounded by roughly 256 sessions x the window.

F3. HIGH, CONFIRMED. Lock-free session state breaks frame ordering and the DGC acknowledgment. Today "check state, take credit,
enqueue frame, change state" runs under `sessionLock` (`MuxOutputStream.java:297-379`, `Session.java:246-263`). Split into a CAS
then an enqueue: (a) a writer passes its check, `abort()` enqueues ABORT and frees the ID, then the stale DATA is enqueued; the
peer rejects "Data on TERMINATED session" (`Session.java:481`) and tears down the connection; if the ID was reused the stale DATA is
delivered into another request's stream. (b) `addAcknowledgmentListener` checks `outState<FINISHED` (`Session.java:212`) while
`writeBuffer` computes `ackRequired` from the listeners then sets FINISHED (`MuxOutputStream.java:345-376`); a listener added between
is never notified: a silent DGC lease bug. Must stay under a per-session lock (ReentrantLock + Condition, which does not pin):
in/out state, debiting and crediting the ration together with the frame enqueue, partialDeliveryStatus, the ack flags and
`removeLater`, and the `inBufRemaining`/`inRation` pair (`MuxInputStream.java:224`). Can be lock-free: the session table lookup, ID
allocation, `muxDown`, the send queue. One Dekker-style race: `setDown` snapshots the table (`Mux.java:286`); a lock-free allocator
must publish its slot then re-read `muxDown`, or a request that misses the snapshot waits forever (the `MuxInputStream` wait loop at
`:65` never exits while `sessionDown` is null).

F4. HIGH, reasoned. The drainer protocol is under-specified and "finds the queue empty" is a lost-wakeup bug: decide on an atomic
counter, never `queue.isEmpty()` (an MPSC queue can look empty while a producer is still linking its node). Required state machine:
`AtomicInteger ctl` = DOWN bit + work-in-progress count. Producer: `offer(frame)`, `c = getAndIncrement()`; if DOWN set, fail its own
future; if `c == 0` become the drainer. Drainer: `missed = 1`; loop: poll up to B frames, one gathering write, complete futures,
`missed = addAndGet(-missed)`, exit at 0. Handing the role on after B frames: the only safe recipient is an owned writer thread woken
with `unpark` which inherits the count, so the design needs a writer thread anyway. Write failure: set DOWN, `setDown`, fail
everything queued; `IOFuture.done(IOException)` is idempotent (`IOFuture.java:103`). Memory model: `unpark` gives no
happens-before; completion must publish through a volatile or a lock before a caller reuses its buffer.

F5. MEDIUM, CONFIRMED. Several pinning claims are wrong on JDK 21 (the container runs 21.0.12): "a blocking channel write unmounts
it" is false on the SSL engine path (`SslEngineChannel.write0` writes inside `synchronized(writeLock)` `:521`, reads the same
`:359,430`); the stream channel's `write` is `synchronized` (`StreamConnectionIO.java:411`); today the server dispatch threads are
virtual and already pin in `sessionLock.wait` (`MuxOutputStream.java:305`) and `IOFuture.wait` (`:61`); this pinning may itself
explain the drop from 24 to 48 callers. A JDK 24+ run or JFR `jdk.VirtualThreadPinned` would tell.

F6. MEDIUM, CONFIRMED. "A call on an idle connection costs no thread hand-off" is false. A `ping` crosses four hand-offs: (1) client
caller to writer; (2) server reader to dispatch thread (`MuxServer` execute); (3) server dispatch thread to writer (`futureSendData`
plus wait); (4) client reader to caller (`Session.java:491`). RMI has none. Stage 1 (client only) removes 1; stage 4 removes 3. The
two reader hand-offs remain by construction. Estimate (PLAUSIBLE): at about 10-15 us per platform-thread wake, hand-offs account for
40-60 of the 85 us container gap. On the workstation single-caller DER is 127 us against RMI 78 (7.90 against 12.89 calls/ms).
Expected: stage 1 about 10%, stages 1 and 4 together 20-30%, DER/RMI to roughly 0.75-0.8; more would need the reader side to change
too (for example a leader/follower reader).

F7. MEDIUM (code CONFIRMED, effect PLAUSIBLE). The throughput ceiling is mostly system calls and locking fixable in place: the
writer issues one `out.write` per buffer and every frame is two buffers (`StreamConnectionIO.java:129-130,196-203`) plus a flush; the
reader calls `in.available()` (an ioctl) on each iteration (`:372`), allocates a buffer per frame (`Mux.java:835`) and takes
`muxLock` for every frame (`:1191`). 0.80 of a core for one small frame per call means system calls and wakes, not queue logic; the
container may inflate system-call cost. Also the design's table is inaccurate: each session has its own `sessionLock`
(`Session.java:103`); lock order is `sessionLock` before `muxLock`.

F8. MEDIUM, CONFIRMED. Striping needs four fixes: (1) the pool may only choose among connections the endpoint approves
(`SslEndpointImpl.java:1010-1017`), pass candidates to the endpoint ordered by load; (2) a request must reserve a session slot at
selection time (CAS while the count is below 256, as `pendingNewRequests` does today, `ConnectionManager.java:222-230,426-429`),
else "no free sessions" (`MuxClient.java:76`); (3) opening a new connection is a check-then-act race: reserve pool size by CAS first;
(4) the reaper must CAS the in-flight count from 0 to a CLOSED sentinel since `checkIdle` relies on the manager lock today. Also test
that the asynchronous Acknowledgment (`MuxInputStream.java:256`) being overtaken by a later call on another connection does no harm
to DGC.

Cheaper experiments before building (run `sweep.py` on the workstation): E1. Run both JVMs with
`-Dorg.apache.river.jeri.tcp.useNIO=true`: `SocketChannelConnectionIO` already writes from the caller when its queue is empty (an
existing drainer prototype); if it does not beat the default, the drainer premise is weakened. E2. In the existing `Writer`, copy
each batch into one buffer and do a single write; drop `available()` in the reader. E3. Poor man's striping: change `n <
MAX_REQUESTS` at `ConnectionManager.java:212` to a threshold K (one line). E4. JMH micro-benchmark of hand-off cost (wait/notifyAll,
virtual-thread unpark, inline write); count park events per call with JFR; run the server on JDK 24+ to separate pinning from writer
saturation.

Tests: jcstress for the `ctl`/queue protocol, the allocate-versus-`setDown` race and publication of completions; Lincheck for the
session table, slot allocation and reaper states; extend `MuxBulkWriteTest` (interrupt the drainer, a reader-produced PingAck while
the socket is gated, abort racing data, ID reuse, ack-listener registration racing EOF); fail the build on any
`jdk.VirtualThreadPinned` event. Only JMH is in the repo today, so jcstress and Lincheck would be new test dependencies.

Required changes: (1) callers may do non-blocking channel writes only; blocking writes belong to an owned writer virtual thread woken
by `unpark`; stream and SSL transports always use the writer. (2) the reader never drains. (3) specify the `ctl` state machine of F4.
(4) keep per-session ReentrantLock critical sections and remove `synchronized` from the I/O path (including `SslEngineChannel`).
(5) power-of-two-choices goes through the endpoint's selection with CAS reservation of session slots and pool size and a reaper
sentinel. (6) correct the latency claim to "removes hand-offs 1 and 3". (7) run E1-E4 first, and add Gate 0: E2 plus E3 inside the
existing `Mux`: build the new provider only if that does not close most of the gap.

---

(Fable gap review: appended below when received.)
