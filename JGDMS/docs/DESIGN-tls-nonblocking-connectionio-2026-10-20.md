# Design: a non-blocking, SSLEngine-driven ConnectionIO for the TLS transport

Status: **design for review; only S0 (section 4.6) is implemented; revised after a Fable-seat review (2026-10-20, section 10).** Written 2026-10-20 for the maintainer and a board review. It follows
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
| opening a connection | 12 ms cold, 5 ms hot (both a *resumed* handshake plus set-up, see below) | |

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

**Session tickets are already in use, and the 12 ms is mostly a JVM that has not finished compiling.** A
`-Djavax.net.debug=ssl:handshake` run of 19 consecutive reconnects (`ConnectionCost`) shows 19 ClientHellos offering
`pre_shared_key`, 19 `NewSessionTicket` messages and **one** server `Certificate`: 18 of 19 handshakes were TLS 1.3 PSK
resumptions, with stateless tickets (server log: `Produced NewSessionTicket stateless`). RESULTS section 34 measures the
open (server in its own JVM, 4 cores, JFR method traces and samples in both JVMs) **[measured]**:

* **The same resumed open costs 11.4 ms in the first 100 reconnects after ConnectionCost's 15 warm-up reconnects, 7.4 ms after
  600, 5.6 ms after 1 500 and 5.1 to 5.3 ms after 3 000 or more**; the 12 ms is the cold JIT. A hot open is about 4.7 ms over a
  0.2 ms warm call. A full handshake is 3.5 ms more cold (14.8 to 16.1 ms) and 2.7 to 3.4 ms more hot (7.6 ms).
* **Hot, resumed, the 5 ms is a chain on one critical path, nothing dominant:** JERI set-up before the socket 0.2, TCP connect
  0.14, engine set-up 0.13, ClientHello generation 0.7, wait for the server's flight 1.6 (the server's 1.2 of work), client
  processes it 0.6, mux handshake 1.2 (of which two `Thread.start` on each side 0.4 + 0.4), the call itself 0.65. About 1.6 ms is
  the key exchange, which JDK 27 does as a hybrid `X25519MLKEM768` even on a resumption (X25519 in Java field arithmetic is 42%
  of the client's sampled time in the open); `-Djdk.tls.namedGroups=x25519` saves 0.4 ms, `secp256r1` costs 0.5 ms more. It is
  not certificate cryptography, and **the JGDMS credential, permission and `AuthManager` work is under 0.1 ms.**
* **The `Selector` per handshake read is not the saving it looked like:** 0.02 to 0.05 ms per `Selector.open` (1 client read and
  3 server reads per open, 0.07 ms per open hot, 1 to 2%). S0 removed it from the server (plain blocking reads); the client
  keeps it as its handshake deadline. It is not where the 12 ms went. What is on the path and does disappear with the redesign: the two platform threads per
  connection per side (0.8 ms hot, 0.9 ms cold in total) and, on the server, the wait for the client's flights on the accept
  thread (1.5 ms hot).
* **Resumption and authentication, measured in memory and in the real stack's debug log:** (1) on a resumed handshake the server's
  trust manager is **not** called; the server sees the peer from the ticket. (2) `getCreationTime()` of a resumed session is the
  original full handshake's, on both sides, and does not change along a chain of resumptions; with stateless tickets the JDK
  does not enforce the ticket's lifetime on the server (only the ticket key's life, 1 h plus the session timeout), so a chain can
  go on for ever: JGDMS's `maxServerSessionDuration` and `maxClientSessionDuration`, which count from `getCreationTime()`, are
  what bound it, and they are anchored at the last full handshake. (3) A client the server has stopped trusting still resumes with
  its old ticket and the server sees the OLD identity even if the client's credential was switched; the guards are in the real
  stack's code, not in JSSE: `getClientSSLContextInfo` calls `checkAuthentication` and builds a new `SSLContext` (empty ticket
  cache) when the client's credential is destroyed, expired or changed, and `ServerAuthManager.checkCredentials` rejects a request
  whose client chain has passed its `notAfter` per request, even on a resumed session. A revoked certificate or removed trust
  anchor is not rechecked until `maxServerSessionDuration` (24 h) after the last full handshake. (4) `setEnableSessionCreation(false)`
  after the handshake does **not** make the client drop its ticket, in either order with the NewSessionTicket (the JDK's ticket
  consumer never reads the flag); set before the first handshake of an engine it would fail the handshake, so the current order
  must be kept. (5) `SSLSession.invalidate()` on the client stops resumption; on the server it does **not** with stateless tickets
  (the server's session cache is empty, so `ServerAuthManager`'s invalidation loop over `getIds()` revokes nothing), only with
  `-Djdk.tls.server.enableSessionTicketExtension=false`.

## 2. What exists today **[code]**

* **Client** (`SslConnection`): connects a `SocketChannel`, builds an `SSLEngine` (client mode, TLS 1.3 pinned,
  `setEnableSessionCreation(false)` after the handshake), wraps it in `SslEngineChannel` and runs the handshake inline on
  the caller's thread, bounded per read by the call's connection deadline or a 60 s default. `getInputStream()` and
  `getOutputStream()` return the engine channel's blocking plaintext streams; `getChannel()` returns null. An `SSLSocket`
  path remains for HTTP-proxy tunnels and custom socket factories.
* **Server** (`SslServerEndpointImpl.SslServerConnection`): the same, server mode with mandatory client authentication. Since
  S0 (section 4.6) the accept loop only accepts and starts a virtual thread per connection that does the handshake, with a total
  deadline, a cap on the handshakes that work at once, a queue for the rest (in which a connection reads its whole ClientHello,
  so that only a peer that has sent a complete one takes a place to work), a limit on the CPU of the first flight, a bound on
  how long the peer may take to answer the server's flight, TLS 1.3 only on both paths, and pinned key exchange groups, the budgets shared by all the listeners that use one copy of the classes (section 4.6). **Before S0** (RESULTS section 34, kept here because it is what the design above measured): the handshake ran
  **inside `acceptLoop`**, on the accept thread, one per listener, so server handshakes were serialised and a hot listener
  accepted about 385 resumed (210 full) connections a second on the 4-core container with the clients on the same cores (accept
  thread busy 97% of the time, about half of that on CPU: the rest was waiting for the client's handshake flights and starting two
  threads per connection; 80 a second was the cold-JIT figure), and from about 64 simultaneous connectors the 50-entry backlog
  (`bind` default) overflowed and connects took 1 to 9 s. A peer that connected and sent **nothing** held the accept loop for the
  full 60 s with no cryptography needed, N such connects serialised into N x 60 s with no accepts, and a handshake that failed
  counted as an accept failure, so 11 junk connections stopped the listener for 10 s. (The deadline was a total one on the
  engine path, not renewed by each read as this section first said; what was missing was cancellation, a bound on writes and a
  bound on the SSLSocket path.) This was a pre-authentication denial of service, not only a throughput limit. Measured
  evidence: the 4096-platform-caller TLS point on the maintainer's Windows workstation had 1900 `Connection refused` errors
  (the listen backlog overflowed while handshakes queued).
* **Mux**: `ConnectionManager`/`ServerConnectionManager` choose `Mux(OutputStream, InputStream, ...)` (a `StreamConnectionIO`:
  a writer thread and a reader thread per connection) when `getChannel()` is null and `Mux(SocketChannel, ...)` (a
  `SocketChannelConnectionIO`: the shared selection thread and a batching drainer) otherwise.
* `SslEngineChannel` handles `NEED_TASK` inline, `NEED_WRAP`/`NEED_UNWRAP` **during the handshake**, `close_notify`, a
  truncated record, a growing buffer, and a handshake deadline. Its application read path (`unwrapMore`) only checks
  `NEED_TASK`: a post-handshake `NEED_WRAP` (a peer `KeyUpdate` asking for ours) is not acted on and works today only because
  JSSE emits the reply with the next application `wrap` (permitted by RFC 8446). It serialises readers and writers with
  `readLock` and `writeLock`. The **client** handshake reads through `fillNetInByDeadline`, which opens a `Selector` for each
  read and toggles the channel's blocking mode (measured at 0.02 to 0.05 ms per read, 1 to 2% of an open, so a tidy-up, not
  the explanation of the 12 ms); a blocking `SocketChannel` has no read timeout, so that is the client's only handshake
  deadline. The **server** handshake (`handshakeServer`) uses plain blocking reads and writes and is ended, when it must be, by
  closing the raw channel.

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

### 4.6 The server accept path (S0: a pre-authentication denial-of-service fix, independent of the above; built)

S0 is built on this branch (the S0 board review and the maintainer's decisions are in
`docs/BOARD-REVIEW-S0-tls-accept-path-2026-10-20.md`; the measurements are in `jgdms-benchmarks/serialization/RESULTS.md`,
section 36). It moves the server handshake off the accept thread and bounds what a peer that has not authenticated can make a
listener do. Facts that the first version of this section had wrong, found by the board's probes and re-checked while building:

* the engine path's handshake deadline was already **total**, not renewed by every read, but it could only be checked while
  waiting for ciphertext: closing the socket did not wake the per-read `Selector`, a write to a peer that did not read had no
  bound at all, and the SSLSocket path (HTTPS, a custom `ServerSocketFactory`) had no bound whatever;
* a failed handshake counted as an accept failure, so 11 junk connections in 5 s put the accept thread to sleep for 10 s;
* a pre-authentication connection costs about 60 KiB of heap and a file descriptor (three with the old per-read `Selector`), not 16.6 KiB;
* the server mux had no start deadline, and `MuxServer`'s `0L` is not a deadline the server could use: the argument is only
  read by the client, because a server's `start()` waits for nothing.

**What is built.**

* **Accept loop.** It takes an admission permit, calls `accept()`, sets the socket options, registers the socket in `inProgress`
  (under the lock that guards `connections` and `closed`), arms the watchdog, and starts a virtual thread for the handshake. Only
  an `accept()` failure feeds the 10-failures-in-5-s throttle, and its 10 s sleep ends when the listener closes.
* **Handshake task.** A virtual thread created *from the accept thread* with `acceptThreadBuilder` under `Subject.callAs(null)`
  (never the system `ThreadPool`, which in platform mode is unbounded and does not make its tasks subject-neutral), so
  `Subject.current()` is null in it; `handleConnection` keeps its `doPrivileged` and `Subject.callAs(serverSubject)` wrapping, so
  the mux still captures the service subject. On completion the task removes the socket from `inProgress` and, atomically with
  the `closed` check, registers the connection; a connection that completes after close is closed, never registered. An
  exception or `Error` in the handshake or in `handleConnection` closes that connection and leaves nothing registered; it no
  longer closes the listener.
* **Total deadline and the minimum budget.** `org.apache.river.jeri.ssl.serverHandshakeTimeout` (10 s), counted from the
  accept and including the wait for a turn. A connection is given its turn to work only if at least
  `ssl.serverHandshakeMinBudget` (2 s, and at most half the timeout, so a short timeout still leaves time to work) is left;
  one that would have less is dropped at no cost, because a handshake begun then would be cut off after it had done its work and
  its client would have reconnected for nothing. It is counted as a queue timeout, apart from the working timeouts. While the
  connection waits, the watchdog is armed for the end of that wait (deadline minus the minimum budget); when the connection takes
  its turn the entry is replaced by one for the deadline. One shared platform daemon thread (`TimeoutScheduler`) serves all the
  watchdogs, the mux's start timers and its acknowledgment timers; it was created eagerly, privileged, under `callAs(null)` and
  without the inheritable thread locals of whatever thread first loaded the class. Its tasks only decide: they flip a state and
  hand everything that can block (closing a socket, which can wait for a linger time, taking a mux down, notifying listeners) to a
  virtual thread of its own, so that one task cannot delay another deadline. The handed-off close is an abortive one
  (`SO_LINGER` 0): a peer that missed its deadline is owed nothing. The class is public only because its callers are in three
  packages: it checks its caller's class and refuses any but the mux and the watchdog, with or without a security manager. The
  watchdog never calls `SslEngineChannel.close()`, which takes the write lock and can block writing `close_notify`. A single CAS
  per handshake (QUEUED to WORKING when it takes its turn, WORKING to DONE by the task before it registers the connection and
  disarms the timer; QUEUED to QUEUE_TIMED_OUT or WORKING to TIMED_OUT by the timer) means a late timeout cannot close a
  connection that was handed on and a handshake that timed out is never handed on. The permits and the `inProgress` entry are
  released exactly once, by the task, after its socket is closed or registered.
* **The ClientHello (`ClientHelloReader`; first built as a read of the first record only).** While a connection waits for its
  turn it reads, on its own virtual thread, its whole ClientHello: a handshake message that the peer may split over records.
  Every record header is checked as it arrives (handshake record, version 3.x, length 1 to 16384, no other record type in
  between), the first byte of the message must be the type of a ClientHello, the 4-byte message header (which may itself be split
  over records) gives its length, which must not exceed the 32768 that the JDK takes (`jdk.tls.maxHandshakeMessageSize`), a record
  may not carry more than the message has left, and the message may be in at most 8 records. Records are read whole and nothing
  after the last record of the message is read (the compatibility ChangeCipherSpec of a client that offers early data stays in the
  socket). The buffer is allocated as the bytes arrive (512 bytes to start with, doubling), so a queued connection holds at
  most 32 812 bytes (4 + 32768 + 8 headers of 5) besides its socket and its virtual thread, and nothing of the engine. Bytes that
  cannot be such a ClientHello (an HTTP request, SSL 2, an alert, an over-long length, a ninth record, bytes after the end of the
  message) close the connection at once and count as junk; end of file or a reset removes the connection at once and counts as a
  peer that left; a peer that has sent only part of its ClientHello is cut off when the wait ends and counts as a queue timeout.
  A peer that has sent a part of a ClientHello therefore holds a place in the queue and nothing else, on either path. The bytes are
  given to the engine's channel (`SslEngineChannel.prime`) when the connection takes its turn, unchanged, so none are lost. Why a
  bound on the number of records and not a re-framing into larger ones: the JDK copies everything it has of a fragmented
  handshake message for every record that follows (`SSLSocketInputRecord`, `SSLEngineInputRecord`), so a 32 KiB message in
  records of one byte cost a server about 190 ms of processor time (a probe: 192 KB on the wire, 60 times a gated flight),
  outside every permit; a bound of 8 records limits that to a few kilobytes of copying, takes no copy of the bytes and no
  re-written header, and a message of the longest size needs only three records of the longest length. A client that splits its
  ClientHello into more than 8 records is not served; the JDK client sends one record, and a split meant to slip past a filter
  is into two (other clients' stacks were not examined). The second ClientHello, which a HelloRetryRequest brings, is read the
  same way on both paths (`ClientHelloReader.readAfterRetry`; the compatibility ChangeCipherSpec that may precede it is accepted
  once), under the post-flight bound. The engine path did not at first: it put the second ClientHello together itself, a record
  at a time and before the gate, with no bound on the records, and the third cold review's M1 measured 32 KB in records of one
  byte at 210 ms to 3.2 s of server CPU for each connection (median 1.5 s; the same bytes in records of 16 KiB cost nothing),
  and 8 loops of it at about 27 Mbit/s used 3.78 of 4 cores: the bound of 8 records had closed the first ClientHello only, not
  that cost. Now the engine path reads it with the reader and gives the engine the bytes in one piece (0 to 20 ms of CPU in the
  same probe, refused as junk at the ninth record). Once the whole ClientHello is in, the
  connection waits for a place to work without reading, so a peer that leaves then is noticed when it has its turn. A socket with
  no channel (the SSLSocket path: HTTPS, a custom `ServerSocketFactory`) is read the same way, through its input stream, which is
  taken once (a factory's socket may return a new, buffering stream at each call); the bytes are given to the JDK with
  `SSLSocketFactory.createSocket(socket, consumed, autoClose)`, which makes a socket in server mode that reads `consumed` first
  and then the socket. The watchdog is armed before the read, as on the engine path. (The first version of this section, and of
  commit `752d6bfe3`, said the SSLSocket path could not read ahead; it can, and without the gate a peer that connected and sent
  nothing held one of the socket path's permits to compute for the whole deadline. The second version read one record, which a
  peer could make a fragment of one byte: 16 such connections held every place to work, and on the SSLSocket path `permits` of
  them held the permits, until the deadline; the second review's H1 and H2.)
* **Engine path.** The server uses plain blocking reads and writes (`SslEngineChannel.handshakeServer`), no `Selector`: a
  blocking `SocketChannel` read or write ends within about a millisecond of the raw channel being closed, on platform and
  virtual threads, which gives the deadline, the cancellation on listener close and the bound on a write to a peer that does not
  read with one mechanism. The **client** keeps `fillNetInByDeadline` (a blocking channel has no read timeout, so it is the
  client's only deadline, `handshakeTimeout`, 60 s, unchanged); an interrupt
  fails the handshake instead of spinning to the deadline.
* **SSLSocket path.** The ClientHello is read ahead as above; then `startHandshake()` under `setSoTimeout(time left)` plus the
  same watchdog, and inside a permit of its own (see CPU); TLS 1.3 is the only enabled protocol, as on the engine path, and the
  negotiated protocol is checked after the handshake ("Refusing non-TLS-1.3 connection"); the NULL-suite check stays as the backstop.
* **Admission, for the process.** The budgets belong to the process, which has the descriptors and the processors, so one
  `HandshakeLimits` instance (one for each distinct setting of the properties, which is one in a process whose properties do not
  change) is shared by every listener, HTTPS included. "The process" means the listeners that use one copy of the classes:
  the instance, the `TimeoutScheduler` thread and the `HandshakeCompute` pool are statics, so they belong to a class loader.
  Two copies in one JVM (an OSGi bundle beside a copy on the class path, two bundle revisions after a refresh, two web
  applications) have two budgets and two timer threads, and the limits hold for each copy, not for the JVM. A refreshed bundle
  leaves the timer thread of its old revision, with that revision's class loader, until the JVM ends (the thread is a daemon
  and has no stop; the leak predates this section, the budget sharing does not). At most N = min(512, maxFds/8, max(16, 4 x processors)) handshakes work at once in the JVM and at
  most Q = min(4096, maxFds/4 - N) accepted connections wait for a turn (`maxFds` from the JVM, 16384 where it cannot say), so
  the sockets that a process admits are at most a quarter of its descriptors however many listeners it has (and each listener may
  hold one more that it has accepted and cannot yet admit, see below: a quarter plus one for each listener). Each accept loop
  waits for a permit of the shared count to be free before it calls `accept()`, without taking it, and takes it when it has
  accepted a connection (an idle listener holds nothing; the first version held a permit for every loop that waited in
  `accept()`, so with as many listeners as places, a later listener never accepted and logged nothing). Another listener may take
  the last permit in between; then the accepted connection waits for one, so at most one connection per listener is accepted
  beyond the places; when the count is exhausted the accept loops stop calling `accept()`, each
  for its own listener, and the queue of the loops that wait is first in, first out, so no listener is passed over for ever (and
  none can hold another up for longer than the connections ahead of it live). On Linux the clients wait in the kernel's accept
  queue (`Integer.MAX_VALUE`, clamped to the system maximum), and nothing is accepted and closed. A listener's close gives back
  only the permits that its own connections hold. A waiting connection costs what the ClientHello bullet says, at most 32 812 bytes of heap besides its socket and its thread,
  so a full queue holds up to 32 812 x Q bytes in addition to the per-connection cost of the handshakes that work: 134 MB at Q =
  4096, the ceiling, which peers that each send a ClientHello less one byte and stop can hold for up to T minus the minimum budget
  (8 s) at a cost to them of about 16 MB a second, 512 connections a second; on a small `-Xmx`, lower
  `ssl.maxServerHandshakeQueue`. Its
  `SslEngineChannel` and buffers are built only when it holds a place to work. The counts are clamped in `long`, so a limit near
  the largest int cannot overflow them. The cap N is also at most four for each processor, and at least
  16 (the first version was 512 whatever the machine): a handshake on a local network is bound by the processor, so more
  handshakes at once only share the same processor time among more of them, each finishes later, and at the deadline many are cut
  off after they have done their work. Measured by the maintainer on a Windows machine of 6 cores and 12 logical processors, with
  `TlsAcceptRate` at 4096 connectors, full handshakes, the default timeout: cap 512 gave 314 and 620 connections a second with
  809 and 70 failures and a p99 of 12 s (the server's logs showed windows with no completion while hundreds of handshakes were in
  progress); cap 96 gave 414 a second with 298 failures and a p99 of 10.3 s; cap 48 gave 658 and 517 a second with no failures
  and a p99 of 8.0 to 8.2 s (maximum 8.3 s). On a link with a long round trip a handshake mostly waits for the peer and holds its place for one round
  trip, or two (a HelloRetryRequest, or a flight larger than the sender's initial window), so the rate of new connections is
  about N divided by one to two round trips (one round trip / two): N = 16: 160 / 80 a second at 100 ms, 53 / 27 at 300 ms;
  N = 48: 480 / 240 at 100 ms, 160 / 80 at 300 ms. Connections that arrive faster wait in the queue and are dropped, as queue
  timeouts, when less than the minimum budget is left, which their clients see as a failed connect and may retry (an HTTPS client
  may not): after a restart, 1000 clients 300 ms away reconnecting at once drain at 27 to 53 a second with N = 16, only about 200
  to 400 of them fit in the 8 s that the minimum budget leaves, and the rest retry. Operators on slow links should raise
  `ssl.maxServerHandshakes`; each place costs a descriptor and about 60 KiB, and the processor time is shared among more
  handshakes. What a hostile peer can do with the places is in the next bullet. Per-address caps: not built (off by decision:
  NAT and loopback).
* **CPU.** The cap bounds concurrency; the rate at which a peer can keep the places taken is the cap divided by the time it
  holds one. A peer that has sent a complete ClientHello and then stalls holds a place for the post-flight bound X (below) for each
  flight it is asked to answer, and never past the deadline T from its accept, so stopping every handshake costs about N / X
  connections a second, each with a complete ClientHello (replayable from any capture, no cryptography): 5 a second at N = 16
  (4 cores) and 16 a second at N = 48 (12 logical processors), X = 3 s. A peer whose key share is for a group that the server
  does not pin is sent a HelloRetryRequest, answers it with a second ClientHello (a cookie copied from the HelloRetryRequest and
  any 32 bytes as a key share: no cryptography) and is sent a second flight; the budget is refilled at each flight, so it holds
  its place for about 2X (5.8 s measured by the third cold review, for a peer that waited 2.8 s before its second ClientHello and
  then stalled for the 3.0 s that X allows) and the cost of keeping the places taken is N / (2X): 2.7 a second at N = 16, 8 a second at N = 48 (the
  figures that the earlier text gave, N / X, were right for a peer that does not make the server retry only). One that stalls before the ClientHello is complete holds a queue place only, for at most T minus the
  minimum budget (8 s): filling the default queue of 4096 takes about 512 such connections a second, and while it is full the
  listeners stop accepting and the clients wait in the operating system's backlog. A
  replayer that reads the server's first flight and closes frees its place in a few milliseconds, so what it can spend is
  bounded by the permits below and not by the cap. A permit of `max(2, cores/4)`, one set for each copy of the classes in the JVM, is held from the moment the server has consumed a
  ClientHello until its first flight has been computed, before it is written (a ClientHello that arrives in pieces holds nothing
  until it is complete; one that answers a HelloRetryRequest is counted again; the client's second flight is not in it; a peer
  that stops reading holds no permit, because the permit is given back after the first `wrap`, which only encrypts what the
  delegated tasks queued, and before the first `flush`). The server's named groups are pinned to
  `X25519MLKEM768, x25519, secp256r1` with `SSLParameters.setNamedGroups` (not the JVM-wide property), because the group is the
  client's choice among those enabled and the JVM default list costs the server from 2 ms (x25519) to 27 ms (ffdhe8192) per
  replayed ClientHello; the names are checked against the engine's at listen (unsupported names are dropped with a warning, and a
  list with none left is not applied, with a warning), and a peer that offers only secp384r1, as peers that follow the CNSA or
  FIPS profiles may, is refused with the default. The SSLSocket path (HTTPS, a custom `ServerSocketFactory`) cannot be split
  into its work and its waits inside `startHandshake()`, so its permit is taken when the whole ClientHello is in
  (never before: a peer that connects and sends nothing, a part of a ClientHello, or junk, takes none) and is given back by a thin socket that the
  SSLSocket is layered over (`HandshakeSocket`): it notes the first write after the ClientHello, the server's first flight, and
  ends the window at the first read after it, which is the JDK waiting for the peer's second flight. The task also gives it back
  in a `finally`, whatever happens, and at most once. A peer that replays a captured ClientHello and stalls therefore holds a
  permit for the work of the flight, as on the engine path, and not for the deadline; a peer that does not read the flight, so that
  the write of it blocks, holds it until the handshake times out (a flight is a few kilobytes, which the kernel's buffers take
  unless the peer's window is closed). These permits are a set of their own, as many as the others and shared in the JVM,
  so that such peers on an HTTPS listener cannot hold up the engine path. A HelloRetryRequest (the peer's key
  share is for a group the server does not pin) is a ServerHello whose random is the RFC 8446 constant; the wrapper recognises it
  in the bytes of the flight, gives the permit back with that flight (the wait for the second ClientHello holds none), reads the
  second ClientHello whole, like the queue phase reads the first (with the compatibility ChangeCipherSpec that precedes it
  accepted once), and takes a permit again before the JDK is given its first byte, for the work of the flight that follows it. (A
  hold of the permit from the first ClientHello to the flight after the second, which would be simpler, lets a peer that sends a
  ClientHello with an unpinned key share and stops hold a permit for the post-flight bound each: `permits / X` such connections a
  second stop every HTTPS handshake.)  The engine path enters its gate again for the second ClientHello: its sniff of the start of
  a ClientHello in the ciphertext sees the second hello after the ChangeCipherSpec, because the engine unwraps one record at a
  time (a test pins it: the gate is entered twice, with the ChangeCipherSpec before the hello in the same write, in a write of
  its own, and right behind the first ClientHello, and a mutant that enters it once fails). The JDK 27 client sends no
  ChangeCipherSpec before its second ClientHello (it sends one after the ServerHello of the second flight), so the tests send
  that record themselves. The SSLSocket path takes TLS 1.3 only,
  like the engine path (TLS 1.2 is used by no client; its second flight, in plain records that any capture can replay, was
  processed outside the permit). Residual: the peer's second flight (certificate chain and signature) is not within a permit on
  either path, and is bounded by the number of handshakes that work at once and by the count of its records (below); a peer must
  have done a key exchange of its own to send it at all. A replay that
  stalls holds one place to work (not a permit to compute) for the post-flight bound, below, and not until the deadline, on both
  paths.
* **Post-flight bound.** The server's flight is the last thing a replayer needs to pay for: it sends a captured ClientHello,
  reads the flight and stops, and the handshake then waits for an answer that never comes. Before this bound the place to work
  was held until the deadline T, so the places could be kept
  taken at N / T connections a second; N = 16 and T = 10 s is 1.6 a second, which is also what 16 six-byte fragments cost before
  the ClientHello was read whole (the second cold review, H2: with the cap lowered to follow the processors the attack was ten to
  thirty times cheaper than at the old cap). Now the peer has `ssl.serverHandshakeClientFlightTimeout` (X, 3 s, 0 removes it)
  for each flight it is asked to answer, whatever is left of T, and the places are held for X: N / X a second, which is 5 for
  N = 16 and 16 for N = 48 (N / (2X) for a peer that makes the server retry, see CPU). The time is added up over the handshake's reads that wait for the peer after the flight is
  written, so a peer that sends a byte now and then does not renew it (a bound on the arrival of the first byte of the answer
  would be met by one byte); time the server spends computing, or waiting for a compute thread, is not counted. A flight is
  "written" on the engine path when a `wrap` leaves the engine waiting to unwrap, after the flush (the same place the CPU gate
  ends, and again after a HelloRetryRequest), and on the SSLSocket path at the first read that follows a write, which
  `HandshakeSocket` sees. Mechanism: the watchdog, with a second entry for the read that is under way, scheduled before the read
  and cancelled after it, which closes the raw socket as the deadline's entry does and decides by the same CAS (WORKING to
  FLIGHT_TIMED_OUT). The alternatives were a socket timeout (it applies to one read, so a trickle renews it, and the JDK sets and
  restores `SO_TIMEOUT` itself on the SSLSocket path) and a per-phase absolute deadline (it would count the server's own compute
  time and the queue for a compute thread against the client, which cuts off legitimate clients on a busy server); the watchdog
  needs no change to the engine's reads or the JDK's and works the same way on both paths. A handshake that ends this way is
  counted as "client did not answer the server's flight", apart from a working timeout. The budget is that of each
  flight and is not shared by the handshake (`flightWritten` sets it afresh), by decision after the third cold review's L1: one
  budget for both flights would cut off a legitimate client that is sent a HelloRetryRequest at a long round trip, which needs two
  round trips; the price is that a peer that makes the server retry holds its place for 2X, which is in the figures above. What X
  must cover is everything the client does between the end of the server's flight and the end of its answer: the round trip; one
  or more round trips more when the server's flight (a long chain) or the client's answer is larger than the sender's initial
  congestion window (about 14 KB), and after a lost segment (a retransmission timeout of at least the round trip plus 200 ms); and
  the client's computation, which is the validation of the server's chain, including any online revocation check (OCSP or CRL; the
  JDK allows such a check 15 s by default), and the signature of its CertificateVerify, some milliseconds in software, 100 to 500
  ms or more with a hardware security module or a token, and as long as its user takes if the token asks for a PIN. Estimated, not
  measured: at a round trip of 300 ms about 0.35 to 0.8 s is needed, at 1000 ms about 1.5 to 2.5 s, so 3 s is safe for ordinary
  links and tight for satellite or lossy links, interactive-PIN clients and clients that check revocation online; operators raise
  the property for them (0 removes the bound, and with it the protection). It costs a legitimate client nothing it was not
  already paying, and a client later than X is refused (and reconnects). The third cold review's probe: a client whose answer was
  2.5 s late was served, one that was 3.2 s late was reset.
* **Record count (`RecordCounter`).** The ClientHello is read whole, but what the peer sends after the server's first flight is
  encrypted, and a peer that has done its own key exchange can frame it as it likes; the JDK does work for every record that does
  not complete a handshake message and copies what it has of the message. The bytes that follow the ClientHellos are therefore
  counted by record, on both paths (the engine path where it reads the channel, the SSLSocket path in `HandshakeSocket`'s input
  stream), by the five-byte headers alone, whatever the records carry: a peer is closed, counted as junk, when it has sent more
  than 16 records and one for every 256 bytes. Probe (an SSLEngine client with a certificate chain of 81 certificates, a
  Certificate message of 30 KB, made to send its second flight with `setMaximumPacketSize`, which gives 5100 records of six bytes:
  the JDK does not frame the first record of a message in fewer than five, so records of one byte could not be made without a
  record layer of the test's own, and were not measured): server CPU for that flight before, 82 ms to 2.2 s on the engine path
  (median of 8 runs 300 ms) and 270 ms to 3.3 s on the SSLSocket path (median 300 ms); after, 0 to 134 ms (median about 50) and
  0 to 57 ms (median about 9), the probe's own CPU subtracted, so roughly. This is the reviewer's "related, plausible" item, and
  it was real on both paths; a record of one byte costs the same per record, so the cost of the worst framing is several times
  the measured one (estimated). A legitimate framing is far within the bound: records of 16 KiB, the 512-byte records of a
  client that asked for the smallest size (RFC 6066), and the records of a small message (the bound allows a record for every 256
  bytes, and the smallest record of a legitimate large message is more than 512); a chain of 30 KB in 534-byte packets, 62
  records, is not refused for its framing.
* **Handshake compute threads.** The engine's delegated tasks (the ClientHello's key exchange and signature, the client's
  certificate chain and signature) go to a pool of platform daemon threads, one for each copy of the classes in the JVM,, created subject-neutral and not from the
  system `ThreadPool`, through the `SslEngineChannel.TaskRunner` seam; the handshake's virtual thread waits for the result and
  releases its carrier. The size is the most processors that handshakes can use for cryptography at once. A task that has not
  started when its handshake has timed out or its socket has closed is dropped. The client, tests of the engine channel and the
  SSLSocket path still run tasks inline.
* **Mux start deadline.** `org.apache.river.jeri.mux.serverStartTimeout` (15 s): a server mux whose client has not sent its
  connection header by then is taken down, which closes the connection and ends its two threads. Wire-format neutral; applies to
  every transport that serves a mux. The timer is scheduled before the I/O starts; its task reads the volatile flags (it takes no
  lock on the timer thread) and hands the take-down to another thread, which decides under the mux's lock, as the processing of
  the header does, so a header that arrives as the time runs out is not cut off.
* **Listener close.** Under one lock it marks the handle closed and snapshots `connections` and `inProgress`; it closes the
  server socket, then the raw sockets of the handshakes in progress (waking the tasks that are blocked reading or writing, and
  interrupting those that wait for a turn), then the live connections.
* **Logging and counters.** A failed handshake is logged at FINE; `HandshakeCounters` counts handshakes by outcome (including
  junk, peers that left, queue timeouts and working timeouts, each its own count) and writes
  one INFO line per 30 s at most (`handshakeSummaryInterval`): "failed N (reasons), accepted M, completed K in the last X s",
  three counts of the same time and no "of" (a failure counted now may belong to a connection accepted windows ago). A failure
  that comes when the interval has passed since the last line writes it at once; otherwise one timer task for the listener (on
  the shared `TimeoutScheduler`, which hands the writing to a virtual thread) writes it when the interval has passed, so the last
  failures of a burst are reported within about an interval although no more come, and closing the listener cancels the task and
  writes what is left. (The first version wrote only when a failure came after the interval, so a run's last failures could go
  unreported, and its line put counts of different sets against each other, "227 of 215".) The peer is named by address before
  it is authenticated: the SSLSocket factory host argument, `toString()` and the log call no longer make a reverse lookup.

| property (all `org.apache.river.jeri.`) | default |
|---|---|
| `ssl.serverHandshakeTimeout` | 10 000 ms, total, from the accept (client: `ssl.handshakeTimeout`, 60 s, unchanged) |
| `ssl.serverHandshakeMinBudget` | 2 000 ms, at most half the timeout: the time that must be left to be given a turn |
| `ssl.maxServerHandshakes` / `ssl.maxServerHandshakeQueue` | min(512, maxFds/8, max(16, 4 x processors)) / min(4096, maxFds/4 - N), for each copy of the classes in the JVM |
| `ssl.maxFileDescriptors` | what the JVM reports, else 16384 |
| `ssl.serverHandshakeClientFlightTimeout` | 3 000 ms, for each flight of the server, added up over the reads that wait for the answer; 0 removes it |
| `ssl.serverHandshakeCpuPermits` | max(2, cores/4), for each copy of the classes in the JVM; the SSLSocket path has as many of its own |
| `ssl.serverHandshakeComputeThreads` | max(1, cores/4); 0 runs the tasks inline on the handshake's virtual thread |
| `ssl.serverNamedGroups` | `X25519MLKEM768,x25519,secp256r1` (empty: do not pin) |
| `mux.serverStartTimeout` | 15 000 ms |
| `ssl.handshakeSummaryInterval` | 30 000 ms |
| `listenBacklog` | `Integer.MAX_VALUE` (the system maximum) |

**Measured (RESULTS section 36, 4-core container, clients on the same cores).** Accept rate with concurrent connectors 1.65 to
2.6 times (64 and 512 connectors; 1.2 times with one); 12 junk connections no longer delay the next open (8 s before, under
25 ms after); silent peers no longer deny the listener (every open failed before, 25 ms after); 600 silent peers (above the
cap) delay an open by the 10 s deadline. **A cost:** at the same offered rate of new full handshakes, the p99 of calls on a
connection that already exists rose from about 10 ms to 100 to 800 ms (30 and 60 new connections a second), and to 20 s when
the storm runs as fast as the listener accepts. It follows the number of handshakes running at once (with `maxServerHandshakes`
at 1 it is back to 11 to 19 ms), not the CPU permit, which covers the first flight only. **The cause (RESULTS section 37):** a
handshake's delegated tasks (key exchange and signatures, certificate verification, 2 to 4 ms each) ran inline on a carrier of
the virtual-thread scheduler, which also runs the threads that dispatch calls; with as many handshakes at work as carriers, a
dispatch thread waits for a carrier, not for the processor. The 30 and 60 a second figures above came largely from the first
seconds of a cold server and a cold storm client; with the storm started first and the server warm there is no difference from
before S0 at those rates, and the 20 s figure includes clients that took the processors the callers need. S0 now runs the delegated tasks on a shared pool of platform threads (`HandshakeCompute`, below), which brings the p99 back to
the figure before S0 at the accept rate of S0.

**Measured after the follow-up (RESULTS section 38).** The accept rate is unchanged within the spread. Silent peers no longer
starve a legitimate open: at 60 silent connections a second the open took a median of 1.5 s (12 opens in 20 s) before and 8.5 ms
(1 837 opens) after, and the first open behind 600 held silent peers waited 9.9 s before and 43 to 67 ms after (the paragraph
above that says 600 silent peers delay an open by the deadline describes the build before the first-record read). A replay flood
costs about 1.7 cores on the engine path in both builds, and on the SSLSocket path about 2.0 cores with its permits against 2.7
to 3.3 without (measured before the permit of that path was moved behind the first record and given back at the first flight;
the test of the current build compares the same flood with and without permits on both paths). The established-call p99 in a warm storm is not worse than before in the same session, but that session did not
reproduce section 37's quiet figures for either build (bimodal, hundreds of milliseconds at the open-loop rates).

**Measured after the second follow-up (RESULTS section 40).** The accept rate with full handshakes is unchanged beyond the spread
(64 connectors 236 a second before, 233 after, nine runs each; 512 connectors 312 before with one collapsed run, 329 after).
Fragment peers (six bytes of a ClientHello, 5 and 50 a second) left a legitimate open at 0 to 1 in 15 s before, 102 and 107 a
second after, the same as with no flood; a peer that replays a ClientHello, reads the flight and stops was held 10.0 s before and
3.0 s after, and at 3 connections a second a legitimate open went from 3 in 15 s to 104 a second, while at 8 a second (above N / X
= 5.3) both builds are stopped. Calls on established connections in a warm storm: p99 median 6.2 ms before and 6.9 ms after at 60
new connections a second (15 runs each; 1 and 3 runs with a tail over 30 ms, not established), 10 ms in both as fast as
accepted. Windows, macOS and a real network were not run.

**Not built, or not established.** Per-address caps (off by decision: NAT and loopback). The peer's second flight (certificate chain and signature) is not within a CPU permit on either path; the handshakes that work at once bound it. A read for a departed peer once its ClientHello is in and it waits for a place to work (it would need a peek at the
socket that Java does not offer without consuming a byte). A non-blocking handshake and any `SelectionManager` change (S1 and S2). Kerberos's deadline and its peer-sized allocation
before authentication. A lockstep storm of full handshakes is not cured by this section; the client's expiry jitter (commit
`f63b647fb`) spreads it. Windows (the listen queue is clamped near 200 and overflow is a reset, so the in-JVM queue is what
helps there; `UnixOperatingSystemMXBean` is absent, hence the assumed 16384), macOS, and real wide-area timings were not run.

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

* **S0** server handshake off the accept thread, total deadline, in-progress cap and queue, CPU permit, pinned groups, mux start
  deadline (4.6). **Built.** Independent, valuable alone, no new I/O code.
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
