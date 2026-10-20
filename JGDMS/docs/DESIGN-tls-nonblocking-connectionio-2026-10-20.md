# Design: a non-blocking, SSLEngine-driven ConnectionIO for the TLS transport

Status: **design for review, nothing implemented; revised after a Fable-seat review (2026-10-20, section 10).** Written 2026-10-20 for the maintainer and a board review. It follows
`DESIGN-OPTIONS-jeri-transport-architecture-2026-10-20.md` (section D4 sketched this; the Fable-seat review of that
sketch is folded in here). Labels: **[measured]** from `jgdms-benchmarks/serialization/RESULTS.md` or a scratch
measurement named here, **[code]** checked in the source on `claude/focused-bohr-t6bwal`, **[estimate]** a guess.
Measurements are from one 4-core Linux container (JDK 27 `27-internal`), small calls.

## 1. Why

The maintainer's target is low latency and high scalability; TLS is the production transport for internet traffic. TLS is
also the transport with the largest gap to RMI over TLS **[measured]**:

| DER `ping` over TLS, server in its own JVM | JERI now | RMI over TLS |
|---|---:|---:|
| 1 caller, wall | 200 to 230 us | 56 us |
| thread hand-offs per call, both JVMs | 8.3 | 2 |
| CPU per call, both JVMs | 240 to 270 us | 58 us |
| 16 callers | 25 to 32 calls/ms | ~108 calls/ms |
| opening a connection | 12 ms (a *resumed* handshake, see below) | |

The same call over TCP with channel I/O (`useNIO`) takes 91 to 107 us with 4.1 hand-offs; the stream path with a mux reader
and writer thread per connection takes 126 to 145 us with 8.1. TLS cannot use the channel path today because
`SslConnection.getChannel()` is deliberately null: the raw channel carries ciphertext and the channel path would read and
write it as mux frames. So TLS is stuck on the thread-per-connection stream shape.

**The cost is not the cryptography.** A scratch measurement (`SSLEngine` pair in memory, TLS 1.3, AES-256-GCM, warmed up,
no sockets): `wrap` of a 64 B to 1 KiB record costs 1.3 to 2.6 us and `unwrap` 1.4 to 2.7 us; a 16 000 B record 3.3 us and
4.7 us. A call does one wrap and one unwrap on each side, so crypto is about 8 us of the roughly 70 us wall time and 70 us of
CPU that TLS adds over TCP. Where the rest goes is **not established**: the per-thread table (RESULTS section 23) shows the
mux reader and writer threads cost 56 and 66 us over TLS against 49 and 61 over TCP, so the TLS-specific cost in those
threads is only about 10 to 15 us; most of the TLS stream path's cost is the thread-per-connection shape that TCP pays too,
which the stream-to-channel move already measured (about 40 us for one caller on TCP). The `SslEngineChannel` layer
(`doPrivileged` and `readLock`/`writeLock` per read and write, copies, a `Selector` opened per handshake read) adds
something on top. A profile of the stream TLS path (question 5) would say how much. **[measured, the rest reasoned]** The
single-thread crypto ceiling feared in the first review is not the constraint it looked like (section 4.2).

**Session tickets are already in use.** A `-Djavax.net.ssl.debug=ssl:handshake` run of 19 consecutive reconnects (client JVM,
`ConnectionCost`) shows 19 ClientHellos offering `pre_shared_key`, 19 `NewSessionTicket` messages, and **one** server
`Certificate` message in all: only the first connection did a full handshake, the other 18 were TLS 1.3 PSK resumptions (no
certificate exchange, no signatures). The client creates its engine with host and port (`createSSLEngine(serverHost, port)`), so
the JDK's client session cache is eligible. So the 12 ms above is the cost of a **resumed** connection open; a first connection
to a peer, and the server's accept-loop rate for new clients (section 4.6), is slower than the 12 ms and 80 per second
quoted. What the 12 ms is made of is therefore not certificate cryptography: the candidates are JSSE's PSK processing,
`AuthManager`/credential and permission checks, engine and thread creation, the `Selector` per handshake read, the mux
handshake (2.3 ms on UDS), and JIT warm-up; a profile of a connection open would say. Open for the board: resumption
skips the server's re-validation of the client certificate, so credential freshness rests on `maxServerSessionDuration`, the
per-request `checkCredentials`, and invalidation when a credential is removed (`ServerAuthManager.chooseServerAlias`); check that
chains of resumptions cannot outlive a rotated or expired SPIFFE credential (a resumed session's creation time is not the
original's), and that `setEnableSessionCreation(false)` after the handshake does not make the client drop the tickets it
needs.

## 2. What exists today **[code]**

* **Client** (`SslConnection`): connects a `SocketChannel`, builds an `SSLEngine` (client mode, TLS 1.3 pinned,
  `setEnableSessionCreation(false)` after the handshake), wraps it in `SslEngineChannel` and runs the handshake inline on
  the caller's thread, bounded per read by the call's connection deadline or a 60 s default. `getInputStream()` and
  `getOutputStream()` return the engine channel's blocking plaintext streams; `getChannel()` returns null. An `SSLSocket`
  path remains for HTTP-proxy tunnels and custom socket factories.
* **Server** (`SslServerEndpointImpl.SslServerConnection`): the same, server mode with mandatory client authentication;
  the handshake runs **inside `acceptLoop`**, on the accept thread (`serverConnection(socket)` is called before the
  connection is handed on). One accept thread per listener, so server handshakes are serialised: at 12 ms each, about 80 new
  connections a second per listener. A peer that connects and sends **nothing** holds the accept loop for the full 60 s
  with no cryptography needed, N such connects serialise into N x 60 s with no accepts, and a peer that trickles a byte
  every 59 s holds it for ever (the 60 s bound is per read with progress). This is a pre-authentication denial of
  service, not only a throughput limit. Measured evidence: the 4096-platform-caller TLS point on the maintainer's Windows
  workstation had 1900 `Connection refused` errors (the listen backlog overflowed while handshakes queued).
* **Mux**: `ConnectionManager`/`ServerConnectionManager` choose `Mux(OutputStream, InputStream, ...)` (a `StreamConnectionIO`:
  a writer thread and a reader thread per connection) when `getChannel()` is null and `Mux(SocketChannel, ...)` (a
  `SocketChannelConnectionIO`: the shared selection thread and a batching drainer) otherwise.
* `SslEngineChannel` handles `NEED_TASK` inline, `NEED_WRAP`/`NEED_UNWRAP` **during the handshake**, `close_notify`, a
  truncated record, a growing buffer, and a handshake deadline. Its application read path (`unwrapMore`) only checks
  `NEED_TASK`: a post-handshake `NEED_WRAP` (a peer `KeyUpdate` asking for ours) is not acted on and works today only because
  JSSE emits the reply with the next application `wrap` (permitted by RFC 8446). It serialises readers and writers with
  `readLock` and `writeLock`. Each handshake read opens a `Selector` and toggles the channel's blocking mode
  (`readWithDeadline`), which is part of the 12 ms open cost and an easy saving.

## 3. Goals, non-goals, invariants

Goals: the channel path's shape for TLS (no per-connection threads, caller-side batching drainer, the selection thread
reads), keeping every guarantee the stream path gives; a measurable gain in single-caller latency and in CPU per call; no
wire change.

Non-goals: TLS 1.2 or any renegotiation; HTTPS-proxy tunnels and custom socket factories (they keep the `SSLSocket` path);
changing authentication, constraints or the credentials model; DTLS or QUIC.

Invariants (from the board reviews; each is a test in section 8):

1. **Identity and constraints are decided where they are now:** the handshake (the `AuthManager`, SPIFFE/X.500 matching),
   `connect`/`useFor` on every reuse, and the per-request session re-check in `SslServerEndpointImpl` and `SslConnection`.
   The new code is only the record layer driver; the engine it drives is the one the handshake authenticated.
2. **Plaintext never reaches the socket and ciphertext never reaches `Mux.processIncomingData`.** Today the null
   `getChannel()` enforces this; here the type of `ConnectionIO` must (it owns the engine and nothing else can write the
   channel), and a wiretap test proves it.
3. **TLS 1.3 only, no new session on the engine after the handshake, no post-handshake handshake message other than
   `KeyUpdate` and `NewSessionTicket`.**
4. **Any failure takes the connection down before another frame is written** (A4), including a failed or partial wrap or
   write: a partial TLS record must never be followed by another.
5. **A peer that stops reading must not hold a caller or the reader** (A3): bounded drains, ciphertext backpressure, a write
   deadline that tears the connection down.
6. **No allocation sized from a peer's declared length** (B1) beyond what the engine itself bounds (record size 16 KiB plus
   expansion), and a bound on buffered ciphertext per connection.
7. **The wire is unchanged.** TLS records and mux frames are what the stream path sends, so old and new peers interoperate in
   both directions.

## 4. The design

### 4.1 The seam

A new `ConnectionIO` subclass, `SslEngineConnectionIO`, in `org.apache.river.jeri.internal.mux`, built from the engine, the
channel and the bytes already buffered by the handshake driver. `Connection.getChannel()` stays null (it is public API and
its meaning "a channel carrying mux frames" must not change). `SslConnection` and `SslServerConnection` additionally
implement a small **internal** interface (in an `internal` package, not exported), say `EngineHandoff`, with one method
`detachEngine()` returning the engine, the channel and the leftovers; `ConnectionManager.newOutboundMux` and
`ServerConnectionManager` test for it before choosing the stream constructor, and a new `Mux(EngineHandoff, ...)`
constructor installs the new `ConnectionIO`. A connection that does not implement it behaves exactly as today, so the
`SSLSocket` fallback and every other transport are untouched, and the feature is a system property
(`org.apache.river.jeri.ssl.channelIO`, off at first).

**The hand-over must be secured, not just described (review).** `detachEngine()` on a public class would give anyone holding
the `Connection` the engine *and the raw channel*, and the property of invariant 2 would then depend on who holds a
reference. Required: (i) it succeeds **exactly once**; afterwards the connection's `getInputStream`/`getOutputStream` throw
(two drivers on one engine must be impossible); (ii) it is callable only by the connection manager, through an internal
entry point guarded by a dedicated permission or a capability token that only the manager holds; (iii) the connection keeps
its reference to the live engine for the per-request session checks (`decacheSession` reads the engine's current session,
and the protocol, after detach); (iv) it takes `readLock` and `writeLock` so the non-final buffer fields are safely
published to the new owner (the mux's final `connectionIO` field then publishes them onward); (v) what transfers is `netIn`
unread ciphertext, `appIn` plaintext, `inboundClosed`/`closed`; `netOut` is always flushed in blocking mode, so nothing is
pending there. Placement: the interface must be visible to `net.jini.jeri.ssl` and `net.jini.jeri.connection` without being
exported; check the OSGi packaging (`internal.connection` is exported, `internal.mux` is private).

**Buffer convention.** `Mux.processIncomingData` expects one persistent buffer in fill state, consumes whole frames, keeps a
partial frame's state internally and compacts the buffer. The new IO must `unwrap` into the mux's buffer after any partial
bytes, in fill mode (not into a separate drained buffer), or a frame split across two TLS records is corrupted. The leftover
`appIn` from the handshake driver is fed through the same path first.

### 4.2 Who does what, on which thread

`SSLEngine` allows one thread in `wrap` and one in `unwrap` at the same time, never two in the same method. That maps onto
the two roles the channel path already has:

* **Writer role (`wrap`)**: the batching drainer of `SocketChannelConnectionIO`, unchanged in protocol (one drainer at a time,
  `writing`/`awaitingWriteReady`, writes outside the mux lock, bounded rounds, hand-over to the selector). It gathers
  plaintext buffers and calls `wrap(ByteBuffer[] srcs, ByteBuffer netOut)` repeatedly (each call is one record, at most 16 KiB
  of plaintext) until `netOut` holds a bounded amount (say 64 KiB, four records) or the queue is empty, then writes `netOut`
  to the channel with one non-blocking write.
* **Reader role (`unwrap`)**: the selection thread: read ciphertext into `netIn`, `unwrap` every complete record into a
  plaintext buffer, hand that buffer to `mux.processIncomingData`, repeat while `netIn` yields records, then re-arm.

**Where `unwrap` runs (the question the first review called first).** On the selection thread there is one reader per JVM
today (`SelectionManager.concurrency` is 1, the instance is static), so every connection's decryption would run on it.
Measured above, an `unwrap` of a small record costs 1.4 to 2.7 us, so one thread can decrypt on the order of 400 000 small
records a second, an order of magnitude beyond the 25 to 32 calls/ms JERI reaches over TLS today and beyond RMI's 108. For
small-call workloads the single reader is therefore **not** a crypto bottleneck **[measured, estimate for the ceiling]**.
It remains one for (a) bulk transfer: 16 000 B records cost 4.7 us, about 3.4 GB/s of plaintext for the whole JVM; (b) a peer
that sends large forged records (each fails once, fatally, so the cost per attacker connection is one failed `unwrap`, but
many connections multiply it); (c) the shared-thread DoS and head-of-line concerns of the options document. So:

| Option | Description | Verdict |
|---|---|---|
| **U1** | `unwrap` on the single selection thread | enough for the target workloads; the first implementation |
| **U3** | N selection threads with connection affinity (an event-loop group; `SelectionManager` becomes an instance, a connection is bound to one) | the scalability answer for bulk and many-core; a separate, reusable change that also serves UDS and C2a; not needed to start |
| **U4** | the poller reads ciphertext only and the consumer (caller or dispatch thread) unwraps | removes the single-thread limit without affinity, but moves decrypt onto application threads under their ACC and subject and adds a hand-off; not recommended |

A per-event read budget (say 64 KiB of ciphertext per connection per selection round) keeps one busy connection from
starving the others whichever option is used.

**The real budget of U1 (review).** The selection thread also does the `read` and `epoll` system calls, the
`processIncomingData` routing, and the reads of **every channel connection in the JVM** (UDS and TCP with `useNIO`), so its
budget is I/O events per second for the whole process, not "400 000 unwraps". JSSE also takes the engine's own monitor in
`wrap` and `unwrap` (JDK-internal, `SSLEngineImpl`), so the permitted concurrent `wrap` and `unwrap` on one connection do not
run in parallel: the selection thread's `unwrap` can wait a few microseconds behind a drainer's `wrap`, and per-connection
crypto parallelism is not gained. U1 is therefore conditional on a **measured gate** before the default is flipped (S4): the
selection thread's CPU share (`ThreadProfile` reports it) at 16, 64 and 512 callers must stay well below one core; if not,
U3 comes first.

### 4.3 Read path in detail

1. `OP_READ` fires; read into `netIn` (a direct or heap buffer sized `getPacketBufferSize()`; see 4.7).
2. Loop `engine.unwrap(netIn, app)` under `doPrivileged` with the transport's context (as `SslEngineChannel` does, so JSSE's
   incidental permission checks do not see an application caller's frames):
   * `OK` with plaintext: pass `app` to `processIncomingData` (it consumes whole frames and keeps a partial one, as for the
     plain channel path).
   * `OK` without plaintext and handshake status `NEED_TASK`: run the delegated tasks inline (rare after the handshake).
   * `OK` and handshake status `NEED_WRAP`: a `KeyUpdate` with `update_requested` needs our own `KeyUpdate` back. **Every
     `wrap` goes through the drainer role**: set `wrapRequested` and take or wake the drainer by the existing protocol
     (`writing` set under `muxLock`); the reader never calls `wrap` itself. The same holds for our own key-limit
     `KeyUpdate` (JSSE inserts it into the next `wrap`'s output, so the record(s) a future waits for may include a
     handshake record), for `close_notify` and for the Error frame.
   * `BUFFER_UNDERFLOW`: need more ciphertext; leave `netIn` compacted and re-arm `OP_READ`.
   * `BUFFER_OVERFLOW`: grow `app` to the engine's current `getApplicationBufferSize()` (bounded) and retry.
   * `CLOSED`: `close_notify` received: treat as end of input; the mux's own Shutdown framing makes truncation detection
     secondary, but a connection that ends without `close_notify` mid-record is an error.
   * any `SSLException`: tear the connection down (invariant 4).
3. The engine does not expose handshake message types, so "refuse any message but `KeyUpdate`/`NewSessionTicket`" is not
   observable. The enforceable rule: TLS 1.3 only, `setEnableSessionCreation(false)`, any handshake status after the
   handshake other than `NOT_HANDSHAKING`, `NEED_WRAP` or `NEED_TASK` is fatal, and `engine.getSession()` identity and
   protocol are unchanged (the `SSLSocket` path's identity-swap guard, which the engine path dropped; add it back as at least a
   test, after a forced `KeyUpdate`).

### 4.4 Write path in detail

The drainer owns `wrap` and `netOut`. Backpressure is on **ciphertext**: if the channel write leaves bytes in `netOut`, the
drainer hands over to the selector (`OP_WRITE`, as the channel path does) and wraps **no more plaintext** until `netOut` is
empty, so neither `netOut` nor the plaintext queue's wrap output can grow without bound (control frames are unbounded by A3,
which is why they stay plaintext-side). A future completes when the record(s) containing its bytes are fully in the kernel,
the same meaning it has on the channel path (buffers may be reused, delivery status may be set). A write deadline (no
*channel* progress for T seconds with `netOut` non-empty) tears the connection down. A `wrap` failure part-way through a batch
tears the connection down **without writing `netOut` at all** (the simplest safe rule: no partial record follows a failure). `wrap` and the channel write run under
`doPrivileged` with the transport context, because the drainer is often a caller's thread.

### 4.5 Handshake and hand-over

The handshake stays blocking and unchanged in `SslEngineChannel` (client: the caller's thread; server: see 4.6). When it
finishes, `detachEngine()` returns the engine, the channel, and **everything the driver still holds**: unread ciphertext in
`netIn` (a TLS 1.3 `NewSessionTicket` or the first application record may already have arrived with the last handshake
flight) and plaintext in `appIn`. The new `ConnectionIO` processes those first, then switches the channel to non-blocking
mode and registers `OP_READ`. The blocking driver is retired without closing the channel. Setting the channel
non-blocking only after the hand-over means the handshake's per-read deadline logic is untouched.

### 4.6 The server accept path (S0: a pre-authentication denial-of-service fix, independent of the above)

Moving the server handshake off the accept thread is both a prerequisite for scaling new connections and a security fix: a
peer that connects and sends nothing, or trickles, holds the accept loop (section 2). Do it as its own step, first:

* run `SslServerConnection` construction (the handshake) as a task on the system pool, not in `acceptLoop`, **subject-neutral**
  as the accept thread is (`Subject.callAs(null)` in `createAcceptThread`) so credential selection reads the
  `ServerAuthManager`'s captured subject; track the tasks so that closing the listener cancels handshakes in progress
  (today a connection is added to `connections` only after its handshake);
* give the whole handshake a **total** deadline (a few seconds), which needs a small change in `SslEngineChannel`, whose
  deadline is reset on every read with progress;
* cap the handshakes in progress globally. At the cap, **wait** (a bounded queue, bounded by the total deadline) rather than
  refuse at once, because immediate refusal amplifies an attack against legitimate clients; refuse only when the queue is
  full;
* per-remote-address caps only as an optional override (weak against many addresses, harmful behind NAT); the global cap and
  the total deadline are the primary control;
* set the accept backlog explicitly; SYN-level protection is the operating system's and out of scope;
* the pre-authentication memory per connection is bounded by the TLS record size (about 16.6 KiB; `growNetIn` doubles but
  the record size bounds it); state the bound;
* cheap addition: `readWithDeadline` opens a `Selector` for every handshake read; use one per connection (or the
  `SelectionManager`).

### 4.7 Memory

Per connection the engine path needs `netIn` and `netOut` of `getPacketBufferSize()` (about 16.6 KiB each) and a plaintext
buffer of `getApplicationBufferSize()` (about 16 KiB), roughly 50 KiB if allocated eagerly. Allocate them lazily and release
the plaintext and `netOut` buffers when a connection goes idle (keep only `netIn`'s unread tail), so thousands of mostly idle
connections cost little; a bound on buffered ciphertext per connection (invariant 6). The stream path's measured 0.6 MB heap and
two threads per TLS connection is the number to beat **[measured]**.

### 4.8 `close_notify`, errors, interrupts

Inbound `CLOSED` (a received `close_notify`): `mux.setDown`, a best-effort `close_notify` back **through the drainer**, close;
no half-close (the mux has no use for it). On `Mux.shutdown` or an Error frame: the same sequence, run by the drainer role
(`closeOutbound()` from any other thread while a `wrap` is in progress would be two threads in `wrap`), one non-blocking
best-effort write, close. Non-blocking writes are not interrupted into closing the channel, unlike a blocking stream (the
board's C-F1 concern does not apply to this path); a caller that is the drainer and is interrupted only has its flag set,
and a drainer blocked on the engine's monitor is not interruptible, which is acceptable. A post-handshake `NEED_TASK` does
not occur in JSSE's TLS 1.3 record layer; if it does, run the task inline, log it, and continue.

## 5. Expected gains **[estimate]**

Basis: TCP stream to channel I/O measured 8.1 to 4.1 hand-offs and 126 to 145 us to 91 to 107 us for one caller, and
about 50 us of CPU per call; the TLS-specific cost in the mux threads is only 10 to 15 us (section 1), so the gain is mostly
the stream-to-channel gain (do not count it twice) plus whatever the `SslEngineChannel` layer costs, which a profile would
say.
Expected for one caller: roughly 130 to 160 us (from 200 to 230), 4 hand-offs, CPU per call 130 to 160 us (from 240 to 270).
At 16 callers the gain should follow the TCP stream-to-channel pattern plus the batching a single drainer gives (stream TLS
already batches per message; the channel drainer batches across callers). These are guesses to be replaced by a prototype
measurement; the design should be judged on the invariants first.

## 6. Alternatives considered

| Alternative | Why not |
|---|---|
| Keep the stream path, remove its writer thread (caller-thread writes into `SslEngineChannel`) | an interrupt during a blocking write into the channel closes it (board C-F1), a slow peer blocks every caller inside the write monitor (so it needs a write timeout and teardown), and the reader thread and its hop remain |
| Let `getChannel()` return a plaintext view channel | breaks the public meaning and invites misuse; the internal hand-off interface keeps the trust boundary in one place |
| Offload `unwrap` to a pool | adds a hand-off per record and moves decryption onto threads that carry application context |
| Kernel TLS (kTLS) | not available through the JDK; out of scope |
| QUIC | the keystone `SslEngineChannel` was written to share this engine work; a separate project |

## 7. Staging

* **S0** server handshake off the accept thread, total deadline, in-progress and per-address caps (4.6). Independent, valuable
  alone, no new I/O code.
* **S1** stop sharing the one static `SelectionManager` instance (`SelectionManager` already has a public constructor; the
  singleton is `SocketChannelConnectionIO`'s static) and keep read interest registered (the permanent `OP_READ` change in the options
  document); N readers can come later without redesign.
* **S2** `SslEngineConnectionIO`, `EngineHandoff`, client and server, behind the property, U1.
* **S3** measure (single caller, 16, 64, 512, 4096 callers; `ConcurrencyCurve` over TLS; the workstation and the 32/80-core
  machines); decide on U3 from the data.
* **S4** flip the default once soak and interoperability tests pass.

## 8. Test plan

* Unit, in-memory: an engine pair over in-process pipe channels so partial writes, short reads and closes are controllable;
  every `unwrap`/`wrap` status; a frame split across records and records split across reads; a record exactly at, and one
  byte over, every buffer size; many small writes batched into records.
* Hand-over: `NewSessionTicket` and application data arriving with the last handshake flight are neither lost nor reordered.
* `KeyUpdate`: force it with a small `jdk.tls.keyLimits` security property (for example `AES/GCM/NoPadding KeyUpdate 4096`)
  and run a long exchange; assert both directions keep working and the `NEED_WRAP` path runs.
* Negative: a wiretap channel proves no application plaintext marker reaches the socket; a fake engine proves no
  ciphertext reaches `processIncomingData`; a post-handshake handshake message other than the two allowed is fatal.
* Failure: a forged record, a truncated record, `close_notify` mid-frame, a peer that stops reading (backpressure, write
  deadline), an exception from the channel write or from `wrap` mid-batch (the next write must not happen), the mux going down
  while the drainer is wrapping.
* Interoperability: new client with old server and the reverse, over real TCP, calls of every size.
* Concurrency: many threads on one connection, many connections on one reader, the drainer hand-over under a full socket
  buffer (use pipe channels, not loopback TCP with tiny buffers: those stall the sender for tens of seconds and look like a
  deadlock). Mutation testing of the drain protocol, the backpressure rule and the hand-over, as for the channel path.
* Security regression: the existing `net.jini.jeri.ssl` suites (identity, constraints, SPIFFE) run once with the property on.
* Performance: `ThreadProfile` (hand-offs, CPU), `ConcurrencyCurve` (latency tails and many callers), `ConnectionCost`.

## 9. Questions for the board

1. Is a new internal hand-off acceptable as the way to give the mux the engine, with `getChannel()` staying null, provided
   it is one-shot, invalidates the connection's streams, and is callable only by the connection manager (section 4.1)?
2. Is U1 (unwrap on the single selection thread) acceptable for the first release, given 1.4 to 4.7 us per record, provided
   the measured gate on the selection thread's CPU share holds before the default is flipped, with U3 as the follow-up? Or
   must N readers with affinity come first?
3. The server accept-path changes (S0): deadline, caps, and the default numbers.
4. Memory policy: lazy buffers and release when idle, and the per-connection bound on buffered ciphertext.
5. Should the first step be a profile of where the 60 us of layer overhead in the stream TLS path goes? If a large share is
   removable inside the stream path (for example `doPrivileged` and `synchronized` per read), part of the gain may be
   available without the new driver; the prototype's number would then be the better comparison.

## 10. Review by the Fable seat (2026-10-20) and what changed

Verdict: well framed, honest labels, the right invariants; not board-ready until three things changed. All three and the
small misreads are now in the text above:
1. the engine hand-over is secured (exactly once, streams invalidated, guarded entry, engine kept for the session checks,
   locks for publication, mux buffer convention) - section 4.1;
2. every `wrap` goes through the drainer role, JSSE's own monitor serialisation is stated, and the unobservable "refuse other
   handshake messages" is replaced by the enforceable status and session-identity rule - sections 4.3, 4.4, 4.8;
3. U1's ceiling is the selection thread's total budget with a measured gate before S4 - section 4.2; and the gain basis no
   longer double counts the stream threads - sections 1 and 5.
Also corrected: `SslEngineChannel` handles `NEED_WRAP` only during the handshake; S0 is labelled a pre-authentication DoS fix
and its details expanded (silent connects, bounded wait, per-address caps as an override, total deadline needing an
`SslEngineChannel` change, listener close cancelling handshakes, subject-neutral tasks, backlog); the S1 wording.
Not to do (reviewer): U4, half-close support, a park-the-record mechanism for delegated tasks, per-address caps as the
primary S0 control. Recommended before any prototype: answer question 5 with one `ThreadProfile` run on the stream TLS path.
