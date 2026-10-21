# Board review of S0 (server TLS handshake off the accept thread), 2026-10-20

Two Opus seats (adversarial security, JERI transport) reviewed S0 from `docs/DESIGN-tls-nonblocking-connectionio-2026-10-20.md`
section 4.6, each read-only with probes outside the main tree; a Fable gap review followed. Reports are reproduced as received
after the synthesis. Status: **approve with changes**; nothing built yet. The maintainer decides the open points at the end.

## Synthesis

### Facts the design had wrong
* The engine path's handshake deadline is already **total**, not reset on every read (`SslEngineChannel.handshake`, probe: a
  byte every 400 ms was cut off at 3001 ms with a 3000 ms limit). Design section 2 and the javadoc say otherwise. The method is
  `fillNetInByDeadline`.
* What is missing is **cancellation** (closing the socket does not wake the per-read `Selector`; the handshake runs to its
  deadline), **write deadlines**, a server-specific default (60 s, and the property is shared with the client).
* Pre-authentication memory is about 56 to 66 KiB per waiting connection (four eager buffers) plus file descriptors, not 16.6 KiB.
* The system `ThreadPool` does not make tasks subject-neutral (its javadoc promises `callAs(null)`; commit 69966ece4 removed it).

### Defects that exist today, independent of S0
1. **A failed handshake counts as an accept failure** (`SslServerEndpointImpl` around line 1258 and `continueAfterAcceptFailure`):
   10 failures in 5 s put the accept thread to sleep for 10 s. About 11 junk connections per 10 s deny the listener (probes: 9.9 s
   and 8.0 s for the next legitimate open). Load-balancer health checks and scanners trigger it.
2. **The SSLSocket path (HTTPS, custom `ServerSocketFactory`) has no handshake bound**: one silent connection blocks all accepts.
3. **`HttpsServerEndpoint.createListenHandle` never starts the accept thread** (regression since `fd07efe91`, 2026-06-07); no test
   listens over HTTPS.
4. **The attacker chooses the handshake CPU cost**: server named groups are not pinned; a replayed ClientHello costs 2.7 ms
   (x25519), 3.2 ms (X25519MLKEM768), 10.8 ms (secp521r1), 28.6 ms (ffdhe8192).
5. Handshake failures log at INFO with a stack trace (302 lines for 11 failures); pre-authentication reverse DNS lookups.
6. The accept backlog is 50 on every listener (TLS, TCP, UDS, Kerberos).

### Rulings on points where the seats differed (Fable gap review)
* **Waiting at the cap:** neither alone. A bounded in-JVM admission queue sized by the descriptor budget, nothing allocated while
  queued, total deadline counted from accept; **pause `accept()` when it is full** rather than accept and close.
* **Cap** `min(512, maxFds/8)`; **backlog** the OS maximum (`Integer.MAX_VALUE`) with a property to lower it.
* **Pin named groups** to `X25519MLKEM768, x25519, secp256r1` on the engine, **and** a CPU permit `max(2, cores/4)` from
  ClientHello consumed to flight written (the cap bounds concurrency, not rate).
* **Handshake threads** are created from the accept thread with `acceptThreadBuilder` under `Subject.callAs(null)`, never via
  `ThreadPool`; `handleConnection` keeps its wrapping unchanged.
* **Watchdog** closes the raw channel (never `SslEngineChannel.close()`, which blocks on `close_notify`), with a CAS against
  completion and a once-flag for the permit.

### Missed by both Opus seats (Fable)
* Removing the per-read `Selector` is **not server-only**: it is the client's only handshake deadline (`SslConnection`), so the
  server uses blocking reads plus the watchdog and the client keeps `fillNetInByDeadline` with the cancel and interrupt fixes.
* The **server mux has no start deadline**: an authenticated peer can hold a connection and two threads for ever.
* The **18-minute client expiry** puts a fleet into lockstep full handshakes (creation time does not advance on resumption;
  server-side `invalidate()` is inert with stateless tickets): jitter the client limit by [0.8, 1.0] at connection creation.
* On Windows the listen queue is clamped near 200 and overflow is RST (the `Connection refused`); client **ephemeral-port
  exhaustion** (TIME_WAIT) is a second candidate the Linux probes cannot show.

### Recommended shape
**Before S0, separate commits:** the HTTPS accept-thread fix with a smoke test; the explicit backlog on TLS, HTTPS, TCP, UDS
and Kerberos; `getHostAddress()` for pre-authentication lookups.
**S0 in:** accept loop only accepts and starts a handshake virtual thread; only `accept()` failures feed the throttle;
failures at FINE with a rate-limited summary and counters; cap, queue and pause-accept; CPU permit and pinned groups; total
deadline 10 s from accept on its own server property, watchdog, SSLSocket path via `startHandshake()` under `setSoTimeout`;
server mux start deadline; listener close cancels in-progress raw sockets; client expiry jitter; tests (both seats' lists plus
the client deadline test, replay-flood CPU, descriptor baseline after every failure kind).
**Out:** non-blocking handshake, `SelectionManager` changes, SYN-level protection, the Kerberos pre-authentication allocation
(`KerberosServerEndpoint` `establishContext` reads a peer-chosen length), first-byte permit, `ThreadPool` reconciliation.
**Defaults:** deadline 10 s; cap 512; queue `min(4096, maxFds/4 - N)`; CPU permits `max(2, cores/4)`; backlog OS maximum;
per-address cap off; jitter 0.8 to 1.0.

### Maintainer's decisions (2026-10-21)
1. The shape above is accepted, including pause-accept when the queue is full and the CPU permit.
2. The server's named groups are pinned to `X25519MLKEM768, x25519, secp256r1`.
3. The server mux start deadline is in S0.
4. Client expiry jitter was done separately (commit `f63b647fb`: client limit 15 to 30 minutes per session, server 32 minutes).
Sequence: the HTTPS accept-thread fix and the explicit backlog land first as separate commits; S0 follows because both edit
`SslServerEndpointImpl`.

---

## Report: security seat

### Board review, adversarial-security seat: S0 (server TLS handshake off the accept thread)

Reviewed: `docs/DESIGN-tls-nonblocking-connectionio-2026-10-20.md` sections 2, 4.6 and 7, against HEAD `dc3603a97` on `claude/focused-bohr-t6bwal`. Code: `jgdms-jeri/.../ssl/SslServerEndpointImpl.java`, `SslEngineChannel.java`, `ServerAuthManager.java`, `AuthManager.java`, `HttpsServerEndpoint.java`, `mux/MuxServer.java`, `jgdms-collections/.../thread/ThreadPool.java`, and the DirtyChai JDK 27 sources (`Thread`, `VirtualThread`, `AccessController`, `EPollSelectorImpl`).

Probes ran on the DirtyChai JDK 27, 4-core container, loopback, outside the main tree:
* Unit probes: the HEAD copy of `SslEngineChannel.java` compiled alone, with raw-socket attackers.
* End-to-end probes: the real `SslServerEndpoint`, `BasicJeriExporter` and SVID subjects, from copies of the `target/*.jar` files.

Probe sources are in /tmp/claude-0/-home-user/d4500893-c6b9-5565-960c-ab4381350b7d/scratchpad/probe.

## Verdict: APPROVE WITH CHANGES

S0 is the right step, and it is urgent: two pre-authentication denials of service are live today and are worse than section 2 says (H1, H2).

As written, though, S0 would change "an attacker can stop the accept loop" into "an attacker can take every core". The design also rests on three premises that are wrong against the code:
* the handshake deadline is already a total deadline (L1);
* the system pool does not make tasks subject-neutral (M3);
* pre-authentication memory per connection is about 56 KiB plus 3 file descriptors, not 16.6 KiB (L2).

S0 must contain H1 to H3 and M1 to M4 before it is built.

## Findings, by severity

### H1. HIGH, CONFIRMED (end-to-end probe). Handshake failures trip the accept-failure throttle: 11 cheap connects stop the listener for 10 s, repeatable
* **Where:** `SslServerEndpointImpl.java:1258` (`serverConnection(socket)` sits inside the try) → `:1281` catch → `:1321` `continueAfterAcceptFailure` → `:1355-1377`. Ten failures within 5 s make the thread sleep 10 s.
* **Failure:** an exception from a handshake counts as an accept failure. An attacker sends 11 connections of 18 bytes each (`GET / HTTP/1.0`). Each one fails at once with `Unrecognized SSL message`. The accept thread then sleeps.
* **Probe (real stack):**
  * first new legitimate connection after the burst: **9 868 ms**;
  * baseline without the burst: 393 ms;
  * repeating the burst every 10 s, about 1 connection a second, keeps the listener down indefinitely.
* **Required:**
  * S0 must state that only `ServerSocket.accept()` failures feed the throttle.
  * A handshake task's failure must never delay the accept loop.
  * Negative test: an 11-connection garbage burst, then a legitimate connect, must take under 1 s.

### H2. HIGH, CONFIRMED (probe of the same calls). The SSLSocket path has no handshake bound at all; one silent connect stops accepts for ever
* **Where:** `SslServerEndpointImpl.java:1652-1659`. `createSocket(...)` is followed by `sslSocket.getSession()`, with no `setSoTimeout` and no deadline. This path is used by `HttpsServerEndpoint` (`HttpsServerEndpoint.java:888-891`, a plain `ServerSocket`) and by any `SslServerEndpoint` with a custom `ServerSocketFactory`.
* **Probe:** `getSession()` was still blocked after 20 s. It returned `SSL_NULL_WITH_NULL_NULL` only when the client closed.
* **Failure:**
  * One TCP connect that sends nothing stops every accept on that listener until the attacker disconnects. Keep-alive does not help: a live attacker host answers the probes.
  * The same path performs a pre-authentication reverse DNS lookup (`socket.getInetAddress().getHostName()`, `:1653`), so an attacker who controls the PTR zone adds a resolver timeout on top. That part is PLAUSIBLE: the lookup is CONFIRMED in the code, the delay was not measured.
* **Required:**
  * S0 covers both server paths: the same off-thread task, cap and total deadline (enforced as in M1).
  * Use `getHostAddress()`.
  * Section 2 of the design describes only the engine path, and the doc should say the SSLSocket path is the worse one.

### H3. HIGH, CONFIRMED (probe). The attacker chooses the server's handshake CPU cost and pays nothing; S0 removes the only thing that bounds it
* **Probe:** one ClientHello captured once, then replayed over N raw connections. The attacker does no cryptography: it sends the bytes, reads the server's flight and closes. Every attempt produced a full server flight (ServerHello through Finished, including the CertificateVerify signature). Server CPU on the handshake thread, P-256 ECDSA certificate, mean of 600 attempts including warm-up:

| client's only offered group | server CPU per replayed ClientHello |
|---|---:|
| x25519 | 2.7 ms |
| X25519MLKEM768 (JDK default) | 3.2 ms |
| secp256r1 | 4.0 ms |
| secp521r1 | 10.8 ms |
| **ffdhe8192** | **28.6 ms** |

* **Why the attacker can choose:** the server engine pins protocol and cipher suites (`:1617-1620`) but not named groups, so the JDK default list, including ffdhe8192, is accepted.
* **Today:** the cost is limited to the one accept thread (about one core), and that is exactly what makes H1 and H2 possible.
* **After S0 as written:** the only limit is the in-progress cap. About 140 replayed ffdhe8192 hellos a second saturate 4 cores. The handshakes run their delegated tasks inline on the shared virtual-thread carriers, so they also starve authenticated dispatch.
* **Required:**
  1. Pin the server's named groups in the same place as the protocol, for example `X25519MLKEM768, x25519, secp256r1`, with a property to override. Drop ffdhe* and secp521r1/x448 by default.
  2. Add a separate **CPU-concurrency limit** for handshakes: a semaphore around `runDelegatedTasks`, `wrap` and `unwrap` in the handshake loop, not around I/O waits. Default `max(2, cores/4)`. The in-progress cap bounds memory and file descriptors; it does not bound CPU.
  3. Acceptance test: under a replay flood, handshake CPU stays at or below P cores, and the p99 of calls on established connections stays within an agreed bound.

### M1. MEDIUM, CONFIRMED (probes). The total deadline cannot be enforced inside the read loop: writes are unbounded, close does not cancel, and interrupt spins
* **Write side** (`SslEngineChannel.flush`, `:552-559`, a blocking write with no deadline):
  * With a 16 KB certificate (600 SANs), server `SO_SNDBUF` 4096 and a client that sends a ClientHello and never reads, the handshake was **still blocked at 15 s with a 3 s deadline**.
  * With default loopback buffers the 16 KB flight fitted and the read deadline fired at 3 s.
  * So this is exploitable when the server's flight exceeds the send buffer plus the peer's window. Post-quantum (ML-DSA) chains are around 15-25 KB.
* **Close does not cancel a waiting read:** `fillNetInByDeadline` (`:586-631`) opens a local `Selector`. Closing the channel from another thread does not wake it. In the probe the handshake returned **2 558 ms after `channel.close()`, at the deadline**. "Closing the listener cancels handshakes" therefore does not work by closing sockets.
* **Interrupt busy-spins:** after an interrupt, `select` returns 0 at once and the loop `continue`s until the deadline.
  * Platform thread: 4 626 ms of CPU during a 4 702 ms wait.
  * Virtual thread: 4 540 ms of process CPU during a 4 543 ms wait.
  * `Future.cancel(true)` or `shutdownNow` (the obvious cancel) would pin one core per cancelled handshake for up to T, and all carriers at once when a listener with N handshakes in progress is unexported.
  * A channel close did abort a blocked write (6 ms), so close works for writes only.
* **Required:**
  * Enforce T with a watchdog per handshake, one shared timer, armed at accept. When it fires it closes the channel and wakes or closes the handshake's selector.
  * Make the selector a field: one per connection, as the "cheap addition" already proposes. That also gives a handle to `wakeup()` or `close()`.
  * In `fillNetInByDeadline`, treat interrupt or cancel as fatal (`InterruptedIOException`); never `continue`.
  * Apply the same watchdog to the SSLSocket path.

### M2. MEDIUM, reasoned with the measured costs. At the cap, starvation is inherent; the cap and the bounded wait limit resources, not denial of service
* **Cost to the attacker:** holding every slot permanently costs N/T connections a second, with no cryptography: silent connects, or a replayed ClientHello and then a stall waiting for the client's certificate. For N = 512 and T = 10 s that is about 51 connects a second, plus 51 × 3 ms of server CPU.
* **Effect on legitimate clients:** they get roughly legit/(legit + attack) of the slot turnover. The design should say this plainly.
* **The wait queue:**
  * An in-JVM queue of accepted sockets duplicates the kernel's accept queue and costs a file descriptor and socket buffers per entry.
  * If its wait time does not count toward T, every attacker connection is held for 2T.
  * Recommendation: **the kernel backlog is the queue.** At the cap the accept thread stops accepting: it blocks on the permit, and listener close interrupts that wait. The backlog is set explicitly (M4).
  * If an in-JVM queue is built anyway, bound it to N/4 or less and start T at `accept()`, not at slot acquisition.
* **Mitigations that change the ratio:**
  * per-source caps (TCP sources cannot be spoofed off-path);
  * a large N (cheap once the selector fix lands);
  * optionally, evicting the oldest handshake that has not yet delivered a complete ClientHello when the cap is full.

### M3. MEDIUM, CONFIRMED (probes, and code for the consequence). "Subject-neutral" is not delivered by the system pool, and the design gives the wrong reason for needing it
* **Pool behaviour:**
  * `ThreadPool`'s javadoc (`ThreadPool.java:41-62`, `:218-227`) says every task runs under `Subject.callAs(null, ...)`. `Task.run` (`:239-250`) does not do it; the wrapper was removed in commit 69966ece4 ("DIAGNOSTIC").
  * Probe: a task submitted to a ThreadPool-style executor under `callAs(Alice)` sees `Subject.current()` = CN=Alice. A virtual thread created under Alice sees Alice. Only a body wrapped in `callAs(null)` sees null.
  * `createAcceptThread`'s construction (doPrivileged + `callAs(null)`, `:1225-1231`) gives null, which is fine.
* **The stated reason does not hold:** credential selection does **not** depend on the thread. `AuthManager.getSubject()` (`AuthManager.java:130-133`) reads the process worker subject or the constructor-captured subject. The `AuthenticationPermission("listen")` check in `ServerAuthManager.getPrivateCredential` runs under `SslEngineChannel`'s `doPrivileged` (`:236`), and DirtyChai's `checkPermission` does not inject subjects.
* **What does depend on the thread's subject:**
  * `handleConnection`'s WorkerSubject branch (`:1416-1425`) binds no subject. `MuxServer` then captures `this.serverSubject = Subject.current()` and `this.securityContext = Security.getContext()` (`MuxServer.java:112-113`).
  * DirtyChai's `AccessController.getContext()` injects the scoped subject's principals into the captured context, including its privileged context (JDK `AccessController.java:991-1013`).
  * So a handshake task that carries a subject would dispatch every request on that connection under that subject and with its principal-based grants: a confused deputy.
  * Today this is safe only because the accept thread is the creator.
  * The probe also contradicts `MuxServer`'s comment that `doPrivileged(action, acc)` restores the subject in `acc`: it left `Subject.current()` as the thread's.
* **Required:**
  * The handshake task body itself runs inside `AccessController.doPrivileged(..., <accept-thread context>)` and `Subject.callAs(null, ...)`. Do not rely on the pool or on who submits.
  * Run handshakes on virtual threads regardless of `org.apache.river.thread.platform`. With that property set, the pool is an unbounded cached pool of platform threads: one per in-progress handshake, and M1's spin per thread.
  * Test: warm the pool from a subject-bearing submitter, export under another subject, then assert inside the handshake task `Subject.current() == null`, and assert that `MuxServer`'s subject and context principals are unchanged from today.
  * Fix the ThreadPool doc-and-code drift; suggested as a separate task.

### M4. MEDIUM, CONFIRMED (code and log count). Log volume under attack
* **Where:** `:1291-1301`. Every failed handshake is logged at INFO with a full stack trace: 11 failures produced 302 lines.
* **Failure:** today the serial accept thread limits the rate. After S0 a flood logs about 27 lines per connection at whatever rate the CPU allows.
* **Required:**
  * Pre-authentication failures go to FINE.
  * A rate-limited INFO summary, at most once per 30 to 60 s, gives counts by cause (first-flight timeout, total timeout, protocol alert, untrusted certificate, cap wait) and the top source addresses.
  * Counters or JMX for: in progress, permit waiters, accepted, completed (full or resumed), failed by cause, and backlog drops (cannot be seen from Java; document `nstat ListenOverflows`).

### L1. LOW, CONFIRMED (probe). The design's premise is wrong: the engine path's deadline is already total
* **Code:** `handshakeReadDeadline` is set once at `:221` and never reset; reads only check it (`:570`, `:594`).
* **Probe:** a client sending a valid ClientHello one byte every 700 ms was cut off at **3 036 ms with a 3 000 ms deadline**, after 5 bytes.
* **Docs that are wrong:** the javadoc of `handshake(long)` ("inactivity per read, not total"), the field comment, and design section 2 ("trickles a byte every 59 s holds it for ever").
* **Required:** correct them. The `SslEngineChannel` change S0 needs is M1 (writes and cancel), not a read-deadline change.

### L2. LOW, CONFIRMED (probe). Pre-authentication memory per connection is understated
* **Measured:** 300 handshakes stalled after the ClientHello held **55.9 KiB of heap each**, against the design's "about 16.6 KiB".
* **Where it goes:** `netOut`, `netIn`, `appIn` and `handshakeScratch` are all allocated in the constructor (`:160-163`). `handshakeScratch` is kept for the connection's whole life.
* **File descriptors:** **3 server file descriptors per waiting handshake** (socket, epoll, eventfd), measured as 200 silent connections giving 3.01 each, because of the `Selector` opened per read.
* **Also unbounded by S0:** kernel socket buffers, and JSSE's handshake reassembly, which is bounded by `jdk.tls.maxHandshakeMessageSize` (32 KiB default) and `jdk.tls.maxCertificateChainLength` (10).
* **Required:**
  * State the bound as heap plus file descriptors plus kernel buffers, and state the JSSE properties it relies on.
  * Allocate `appIn` lazily and drop `handshakeScratch` after the handshake.
  * Use one selector per connection.

### L3. LOW, CONFIRMED (code). Fail-closed and slot accounting rules S0 must state
* **(a) Error handling.**
  * Today an Error other than OOM or NoClassDefFoundError thrown in a handshake **closes the listener** (`:1317-1334`).
  * In the task, an Error or RuntimeException closes only that socket. `ThreadPool.Task` rethrows without closing anything, so the task needs its own `finally`.
  * Example: `getServerCertificate` dereferences `certs[0]` without a null check (`ServerAuthManager.java:107-108`).
  * Whether any Error is reachable before authentication was not verified.
* **(b) Close race.** The rule is: no registration after close.
  * In one `synchronized (listenHandle)` block: remove from in-progress; if `closed`, close and return; otherwise add to `connections`.
  * `close()` snapshots both sets under the same lock.
  * Today's code does the check and add atomically (`:1259-1269`); keep that.
  * The accept thread registers the in-progress entry, checking `closed`, before it starts the task.
* **(c) Permit release.**
  * Release exactly once: use an `AtomicBoolean` per handshake, because the cancel path and the task's `finally` both run.
  * A failed submission (`RejectedExecutionException` after the pool's shutdown hook) closes the socket and releases the permit on the accept thread.
* **(d) Existing leak.** Today an exception from `handleConnection` after registration closes the raw socket (`:1302`) but leaves the connection in `connections`. In S0, use `closeInternal(true)`.

### L4. LOW, CONFIRMED (code). Smaller items S0 should fix
* **Shared deadline property.** `org.apache.river.jeri.ssl.handshakeTimeout` sets the default for both client and server (`SslEngineChannel.java:129-130`). In the end-to-end probe, lowering it made the legitimate client fail at 3 s. Use a separate server property.
* **Eager reverse DNS.** `logger.log(FINE, "created {0}", toString())` (`:1691`) builds the string eagerly whatever the log level, and `toString()` calls `peerAddress.getHostName()`: a reverse lookup for every connection. Pass `this` and use `getHostAddress()`.
* **Backlog is the default 50 in all three listener constructions:** `ssc.socket().bind(addr)` (`:983`), HTTPS `new ServerSocket(port)`, and the custom factory's `createServerSocket(port)` (use the two-argument overload).
* **The server's mux has no start deadline** (`MuxServer` passes `handshakeTimeout` 0). An *authenticated* peer can hold connections that never send a mux header. Outside S0, but S0's deadline ends at TLS Finished, so say so; follow-up.

### INFO
* **No pre-handshake `checkAccept` exists.** The only call is per request in `checkPermissions` (`:1894`), so there is nothing to reorder. Do not add one on the handshake thread: it would be checked against the transport's privileged context and mean nothing.
* **Thread safety:** `ServerAuthManager.chooseServerAlias` serialises on `credentialCache`, and `FilterX509TrustManager` is synchronized. Concurrent server handshakes look safe, with a short serialisation point.
* **Virtual threads:** on JDK 27 a virtual thread in `Selector.select` parks (`EPollSelectorImpl.timedPoll`) instead of pinning, and `synchronized(readLock)` no longer pins (JDK 24+). One virtual thread per in-progress handshake is therefore cheap to schedule; the costs are memory and file descriptors (L2).
* **Session resumption** (RESULTS C1-C5):
  * Resumed handshakes are cheaper: no client-certificate validation, no CertificateVerify.
  * A replayed resumption ClientHello still needs the client's Finished, so it is no authentication bypass.
  * Two full-handshake storms to size P and N for:
    * a server restart (new ticket keys, so every client does a full handshake on a cold JIT, 15-16 ms each);
    * the 55-minute `maxClientSessionDuration` with no jitter (`SslConnection.java:973-985`), which keeps a fleet that started together in step every hour.
  * Recommend jitter of -10% to 0 on the client's limit.
  * Under the cap, storm handshakes that time out are wasted work, so prefer kernel-backlog waiting (M2).

## Recommended defaults

| knob | default | reasoning | evidence that would justify changing it |
|---|---|---|---|
| Server handshake total deadline T, from accept to TLS Finished, enforced by a watchdog | **10 s** (separate server property) | A hot handshake is 5 ms and a cold full one 16 ms on loopback. WAN is 2-3 RTT plus about 1 s per lost segment (initial RTO), so 10 s covers three losses. Netty's SslHandler default is 10 s. | Production p99.9 handshake time; keep T at least 10× it |
| In-progress cap N | **min(512, maxFileDescriptors/8)** | 512 × 56 KiB is about 28 MiB of heap, and 1 536 fds (512 after the selector fix). It serves about 1 000 handshakes a second at 0.5 s each. | Peak legitimate rate × p99 latency × 4 |
| CPU permits P | **max(2, cores/4)** | Bounds pre-authentication CPU (H3) and leaves at least 3/4 of the cores to dispatch. At 1.2-3 ms per handshake that is 700 to 1 600 handshakes a second per permit pair, above today's 385/210. | Replay-flood test: established-call p99 against P |
| Wait | **the kernel backlog** (stop accepting at the cap) | No extra fds, no double queue, the client's own connect timeout decides | |
| Accept backlog | **1024** (property) | Overflow measured from 64 connectors with 50. Linux clamps to `somaxconn` (4096 here). Windows clamps lower and answers overflow with RST. | `ListenOverflows` at peak; a Windows run |
| Per-address cap | **off**; when on, IPv4 /32 and IPv6 /64, IPv4-mapped normalised, default max(8, N/16), loopback not exempt | NAT, Kubernetes SNAT, sidecars and unix-socket proxies, and loopback benchmarks put many legitimate clients on one address. Off-path TCP sources cannot be spoofed, so the cap does work against a single host. | Deployment topology |
| Server named groups | **X25519MLKEM768, x25519, secp256r1** | H3: ffdhe8192 costs about 10× x25519 | |
| Log rate | failures at FINE, one INFO summary per 30-60 s | M4 | |

## What to test (negative tests first)
1. **H1 regression:** an 11-connection garbage burst, then a legitimate connect, under 1 s. Also 10 000 garbage connects with no accept stall.
2. **H2:** a silent connect to an `HttpsServerEndpoint` listener and to an `SslServerEndpoint` with a custom factory. A second client is not blocked, and the silent one is closed at T.
3. **Deadlines:**
   * silent: closed at T;
   * trickle at one byte per 700 ms: closed at T;
   * stalled write (large flight, small send buffer, client never reads): closed at T, on both paths.
4. **Cancel:** close the listener with N handshakes in progress.
   * Every socket is closed within 1 s and no handshake thread survives.
   * **Process CPU during the close stays about 0** (M1 spin regression).
   * Race: a test hook between handshake completion and registration, looped against `close()`. No connection is registered or served after close.
5. **Accounting:** after M failures of every kind (timeout, alert, injected RuntimeException, injected Error, RejectedExecution, cap wait then close), permits equal N, the in-progress set is empty, `connections` is empty and the fd count is back at baseline. An Error in a handshake leaves the listener open.
6. **H3:** a replayed ffdhe8192 or secp521r1 ClientHello is refused with no key exchange. Under an x25519 replay flood, handshake CPU stays at or below P cores and established-call p99 stays within its bound.
7. **M3 context:** the `Subject.current()` and `MuxServer` subject and context assertions above, with the pool warmed by a subject-bearing submitter. Also with `org.apache.river.thread.platform=true`.
8. **Cap and starvation:** N silent connections. A legitimate client completes once a slot frees, and the (N+1)th waits in the backlog, not in the JVM. With the per-address cap on, the /64 aggregation and IPv4-mapped addresses are tested.
9. **Logging:** 10 000 failures produce at most K lines.
10. **Backlog** is applied (`ss -ltn` shows Send-Q equal to the backlog).
11. **Restart storm:** 1 000 clients reconnecting with full handshakes after a server restart. Record timeouts at the defaults.
12. **Security regression:** the existing ssl, SPIFFE and constraint suites, unchanged.

## Not verified
* S0 itself; nothing was built. All findings are against HEAD or isolated probes.
* Windows behaviour (backlog overflow RST, the maintainer's `Connection refused` counts).
* Any real WAN or loss timings.
* Runs with a SecurityManager or polpAudit policy installed. In particular, whether a policy or SecurityManager that is a `SecurityContextSource` restores subjects in `doPrivileged(action, acc)`, as the `MuxServer` comment claims. My probe with plain `AccessController` did not see it.
* Whether any Error is reachable before authentication in JSSE or JGDMS.
* The stalled-write attack with real production chain sizes and default buffers; reproduced only with a 16 KB certificate and `SO_SNDBUF` 4096.
* Per-connection kernel memory.
* The brief's 4.7 ms full-handshake figure; my replay numbers are server flight only and include warm-up.
* The CPU of the virtual-thread spin case per thread (`ThreadMXBean` does not report it for virtual threads). Process CPU was measured instead.

---

## Report: transport seat

### JERI-transport seat: review of S0 (server TLS handshake off the accept thread)

Reviewed against HEAD 14de223c2 (branch claude/focused-bohr-t6bwal) in a detached worktree. The design doc was written against dc3603a97; the only change between the two in `net/jini/jeri/ssl` is the session-duration defaults, which do not touch S0.

## Verdict: APPROVE WITH CHANGES

S0 is the right change, the right size and the right thing to do first. As written it rests on two wrong premises about the code, and it leaves out three problems that are worse than the one it fixes:

1. The handshake deadline is already a total deadline for reads.
2. Closing a socket does not cancel a handshake that is waiting in the per-read Selector.
3. Any failed handshake feeds the accept-failure throttle: 11 junk connections stall the listener for 10 s.
4. The SSLSocket path has no handshake deadline at all.
5. The HTTPS listener never starts its accept thread.

The required changes are below. All five points were confirmed by probes run against built classes.

## Findings, by severity

### F1 HIGH, CONFIRMED (probe): any failed handshake trips the accept-failure throttle and puts the listener to sleep for 10 s
- **Where:** `SslServerEndpointImpl.java:1234-1339` and `:1348-1386`.
- **Code:** `serverConnection(socket)` (the handshake, `:1258`) is inside the same `try` as `accept()`. Every exception from a handshake goes to `continueAfterAcceptFailure`. Ten failures within 5 s make the accept thread call `Thread.sleep(10000)`. Each failure is also logged at INFO with a stack trace (`:1291-1300`), which an attacker can use to flood the log.
- **Probe:** `TransportS0Listener junk`, a real `SslServerEndpoint` with mutual TLS. 12 connections that each send `GARBAGE!\r\n` take 2.0 s. The next legitimate full handshake then takes **8013 ms**, against 20 ms warm.
- **Failure scenario:** about 11 junk connections every 10 s keep a TLS listener unavailable indefinitely. This needs no cryptography and far less effort than the silent-peer attack. Benign sources trip it too: TCP health checks from a load balancer (connect then close gives an `EOFException` during the handshake), port scanners, and clients with expired or untrusted certificates.
- **Required:** in S0, a handshake failure must never reach `continueAfterAcceptFailure`. Only failures of `accept()` itself count. Log handshake failures at `Levels.HANDLED`/FINE, rate-limited. Add a regression test.

### F2 HIGH, CONFIRMED (code + probe): the SSLSocket fallback (custom `ServerSocketFactory`, and HTTPS) has no handshake deadline
- **Where:** `SslServerEndpointImpl.java:1646-1660`. `sslSocket.getSession()` runs the handshake on the accept thread, and nothing in `net.jini.jeri.ssl` calls `setSoTimeout` (grep). The `org.apache.river.jeri.ssl.handshakeTimeout` property only applies to the engine path.
- **Probe:** `TransportS0Listener silent ssf` uses `SslServerEndpoint` with `ServerSocketFactory.getDefault()` and `handshakeTimeout=5000`. One silent connection makes a legitimate handshake fail after **30 034 ms** (the client's own timeout). The listener is held indefinitely.
- **Required:** S0 must cover both server paths, not only `SslEngineChannel`. Use the same total deadline from F4. For the socket path: `setSoTimeout(remaining)` around an explicit `startHandshake()`, restored to 0 afterwards, plus the close-based watchdog for writes. `startHandshake()` gives the cause; `getSession()` swallows it into `SSL_NULL_WITH_NULL_NULL`. The NULL-suite check at `:1663` stays as the fail-closed backstop.

### F3 HIGH, CONFIRMED (probe + code): the HTTPS server endpoint never accepts any connection (a regression since fd07efe91, 2026-06-07)
- **Where:** `SslServerEndpointImpl.java:1068-1079` starts the accept thread in `createListenHandle`. `HttpsServerEndpoint.java:894-900` overrides `createListenHandle`, returns `new HttpsListenHandle(...)`, and never calls `createAcceptThread()`/`start()`. The `SslListenHandle` constructor leaves `acceptThread` null (`:1215-1217`).
- **Probe:** `TransportS0Listener silent https`. Even the warm-up handshakes time out (20 s each). A JSON thread dump with `-Djdk.trackAllThreads=true` shows no accept thread at all, and connections pile up in the kernel backlog. Users include `jgdms-discovery-providers` `org/apache/river/discovery/https/Server.java`. No test exercises `HttpsServerEndpoint` listen.
- **Required:** S0 rewrites exactly this code. Start the accept loop in one non-overridable place, for example `listen()` calling a final `start(handle)` after `createListenHandle`. Add an HTTPS listen smoke test. The fix can also land on its own ahead of S0.

### F4 HIGH, CONFIRMED (probe): the design's deadline premise is wrong. Reads already have a total deadline; writes and cancellation have none
- **Where:** `SslEngineChannel.java:220-223` sets `handshakeReadDeadline` once, as an absolute time, at handshake start. `fillNetInByDeadline` (`:586-631`) computes the remaining time from that fixed value; nothing resets it.
- **Probe:** `trickle` sends a 16 KiB record header, then 1 byte every 400 ms, with timeout 3000 ms. The handshake fails at **3001-3002 ms**.
- **What is wrong in the documents:**
  - Design §2 says "a peer that trickles a byte every 59 s holds it for ever"; the brief says the deadline "resets on every read with progress". Both are false.
  - The javadoc at `:207-218` ("per-read progress", "not total handshake time") is also wrong.
  - Design §4.6 cites `readWithDeadline`; the method is `fillNetInByDeadline`.
- **What is really missing:**
  - (a) Writes. `flush()` (`:552-559`) is a blocking `channel.write` with no deadline, and the handshake holds `writeLock` while it writes. This is PLAUSIBLE only, and needs a server flight larger than the socket buffers plus a peer that never reads, so it matters little with normal certificate chains. Not run.
  - (b) Cancellation. Probe `cancel`: closing the raw channel (or `SslEngineChannel.close()`) 0.5 s into a 20 s handshake that is waiting for a silent peer. The handshake thread stays in `Selector.select` until the deadline, **19.5 s after the close**, on both platform and virtual threads. The client sees EOF at once, but the server thread, its Selector fds and (under S0) its cap slot are held for the full deadline. So "closing the listener cancels handshakes" cannot be built by closing sockets while the Selector wait remains.
  - (c) The 60 s value, and the fact that the property is shared with the client (`SslConnection.java:437`, `:481-489`).
- **Required:**
  - Replace the per-read Selector with plain blocking channel I/O.
  - Enforce one total deadline, starting at accept and covering the queue wait plus reads plus writes, by a watchdog that closes the raw channel. Probe `blockingcancel`: a blocking `SocketChannel.read` ends **0-1 ms** after close with `AsynchronousCloseException`, on both platform and virtual threads. One mechanism gives the deadline, cancellation and write coverage together, by construction rather than by a check per read.
  - Make the watchdog-vs-completion hand-off a single CAS, so a deadline that fires as the handshake finishes cannot close a connection already handed on (this matters again for S2's `detachEngine()`).
  - Split the property: a server value (default 10 s from accept) and the client's existing value.
  - Correct the javadoc and design §2.

### F5 MEDIUM, CONFIRMED (code): cancel-on-close must not go through `SslEngineChannel.close()`
- **Where:** `SslEngineChannel.java:666-705` takes `writeLock` and does a blocking `close_notify` write. A handshake thread blocked in `handshakeWrap` holds `writeLock`, so `ListenHandle.close()` would block behind it.
- **Pre-existing problem:** `SslListenHandle.close()` (`:1468-1482`) already calls `closeInternal` on each live connection, which does a blocking `close_notify` write, so one non-reading peer can stall unexport.
- **Required:** cancel in-progress handshakes by closing the raw socket or channel. Consider making the listener-close `close_notify` non-blocking or best effort.

### F6 MEDIUM, CONFIRMED (code): lifecycle and concurrency rules S0 must state
- **Where:** `:1259-1269`, `:1449-1483`, `:1489-1493`.
- **What is sound today:** a connection is added to `connections` only after its handshake. The add is atomic with the `closed` check under `synchronized(this)`, and a connection completed after close is closed there. That race is correct today and stays correct if kept.
- **Out-of-order completion is harmless (CONFIRMED by reading):**
  - `connections` is an unordered `HashSet`.
  - `ServerConnectionManager.handleConnection` (`ServerConnectionManager.java:349-356`) and `BasicServerConnManager` are stateless. `handleConnection` returns immediately; the stream mux starts two platform threads, `StreamConnectionIO.java:100-101`.
  - `MuxServer` has no cross-connection registration.
- **Required shape:**
  - Keep a set of in-progress raw sockets under the same lock as `connections`.
  - The accept thread does `lock{ if closed -> close socket, return; inProgress.add }`, then starts the task.
  - The task does the handshake inside `try`. In `finally`: release the permit, then `lock{ inProgress.remove; if (closed || failed) close raw; else connections.add }`. `handleConnection` runs outside the lock.
  - `close()` does `lock{ closed = true; snapshot both sets }`, closes the server socket, then closes the raw in-progress sockets and `closeInternal`s the live connections.
  - The `break` at `:1266` becomes `return` in the task.
  - `handleConnection` keeps exactly today's wrapping: `doPrivileged(securityContext.wrap(..), securityContext.getAccessControlContext())` and then `Subject.callAs(serverSubject)` (`:1411-1441`). That is what makes the mux threads capture the service subject at construction (DirtyChai `Thread` captures `Subject.current()`).
  - Kerberos already does this (`KerberosServerEndpoint.java:1565-1577`: the connection is added to `connections` at accept, then `establishContext` runs on the system pool). Cite it as the in-tree precedent.

### F7 MEDIUM, CONFIRMED (code + probe): thread model. Use virtual threads created like the accept thread, not the system `ThreadPool` in platform mode
- **The accept thread is already virtual** (`:118`, `:1225-1231`; RESULTS 34 agrees). The brief's "single accept thread" picture is right, but it is not a platform thread.
- **The system pool is unbounded either way** (`ThreadPool.java:106-158`): a virtual thread per task by default, or with `org.apache.river.thread.platform=true` an unbounded `newCachedThreadPool`. If the handshake, and especially the waiting stage of the bounded queue, runs on that pool in platform mode, a burst of N connectors needs N platform threads.
- **Probe:** `many 2000` runs 2000 concurrent silent handshakes, each on its own virtual thread: **13 platform threads** in total, an unrelated virtual-thread start+join at worst 4.3 ms, and all 2000 timing out cleanly at 5 s. With today's Selector it costs 2 extra fds per waiting handshake (4.0 fds per connection pair in the probe).
- **JDK 27's Linux `EPollSelectorImpl.doSelect` parks a virtual thread** (`timedPoll`) rather than pinning a carrier. Windows was not verified, which is one more reason to drop the Selector (F4).
- **Required:** create each handshake thread with `acceptThreadBuilder` under `Subject.callAs(null)`, from the accept thread. That matches today's subject and ACC exactly, because today's handshake runs on that thread. Use virtual threads regardless of the platform property (the pool's own doc already exempts the selector thread). The cap is a `Semaphore`, not a pool size.
- **Is a blocking handshake on a virtual thread acceptable?** Yes. A non-blocking handshake belongs, if ever, with S2, which itself keeps the handshake blocking (§4.5).

### F8 MEDIUM, PLAUSIBLE: S0 removes an implicit CPU limiter
- **Today:** server handshake CPU is capped at one carrier, because the delegated tasks run inline (1.11 ms per connection, RESULTS 34 D).
- **After S0:** with a cap of 256 permits on 4 cores, a reconnect storm (for example after a server restart) can occupy every carrier. That raises dispatch and call latency on existing connections; the user pool is virtual by default and shares the carriers.
- **The cap cannot be about cores:** with a WAN round trip it must be at least the accept rate times the handshake wall time, so 16 permits at 100 ms gives only 160/s.
- **Required:** a measurement, not a mechanism. Measure p99 call latency on established connections during a storm of new connections, before and after S0.

### F9 MEDIUM, CONFIRMED (code + JDK source + probe): the backlog
- **TLS:** `SslServerEndpointImpl.java:983` `ssc.socket().bind(new InetSocketAddress(port))` and `:977` `serverSocketFactory.createServerSocket(port)` both give the JDK default of 50 (`ServerSocketChannelImpl.java:342`: `backlog < 1 ? 50 : backlog`).
- **The factory abstraction already carries a backlog:** `javax.net.ServerSocketFactory.createServerSocket(int port, int backlog)`. Call that overload, and `bind(addr, backlog)` on the channel path.
- **Probe:** `backlog 50 200` against a listener that accepts nothing: **53 of 200** connects completed at once. The rest hung and failed after 134 s with `Connection timed out`: Linux drops the SYN (`tcp_abort_on_overflow=0`) and clients retransmit at 1, 3 and 7 s, which is RESULTS' "1 to 9 s". With `backlog 4096 200`, all 200 completed at once. This kernel has `net.core.somaxconn=4096`.
- **Windows (NOT verified):** it sends RST on overflow, hence `Connection refused`. In Winsock `SOMAXCONN == 0x7fffffff`, meaning "maximum reasonable value". Java cannot pass `SOMAXCONN_HINT(N)` (negative values become 50). Explicit large values are reportedly clamped to about 200.
- **Number:** request `Integer.MAX_VALUE` by default, which every OS clamps to its own maximum (Linux `somaxconn`, macOS `kern.ipc.somaxconn`, Windows SOMAXCONN). Expose a property to lower it, for example `org.apache.river.jeri.backlog`. A custom factory that ignores the 2-argument overload is the factory's choice; document it.

### F10 LOW, CONFIRMED (code): pre-authentication memory is larger than the design states
- `SslEngineChannel.java:150-166` allocates four buffers eagerly: `netIn` and `netOut` at the packet size (about 16.7 KiB each), `appIn` and `handshakeScratch` at the application size (about 16.4 KiB each). That is about 66 KiB per connection plus the engine's handshake state, not 16.6 KiB.
- `growNetIn` is indeed bounded by the record size.
- **Required:** state roughly 70-100 KiB per in-progress handshake, so a cap of 256 is at most about 25 MB. Allocate nothing (no engine, no buffers) for a socket that is only waiting in the queue, so the queue costs a socket plus a parked virtual thread.

### F11 LOW, PLAUSIBLE: a global cap can be filled cheaply
- With cap C and total deadline D, silent peers at C/D per second (25/s for 256 and 10 s) keep every permit held, and legitimate clients wait in the queue.
- **Recommended, not required:** take the permit only after the first ciphertext byte arrives. The pre-read happens under a shorter first-flight deadline, about 5 s, and the bytes are handed to `SslEngineChannel` as initial `netIn`. A silent peer then costs only a queue slot.
- Per-address caps stay an opt-in override, as the design says.

### F12 INFO, CONFIRMED (code): other server endpoints
- **TCP** (`TcpServerEndpoint.java:637-646`, `:767-795`): no handshake. `handleConnection` runs on the accept thread (two platform `Thread.start`, about 0.44 ms, so a ceiling of about 2000/s). Backlog is 50 on all three bind paths.
- **UDS** (`UdsServerEndpoint.java:689`, `:807`, `:811`, `:1222-1250`): no handshake; `serverChannel.bind(addr)` gives backlog 50.
- **Kerberos** (`KerberosServerEndpoint.java:1401-1407`): backlog 50. The GSS handshake is already off the accept thread, but:
  - it has no deadline and no cap: each silent peer holds a pool thread and a `connections` entry for ever;
  - `establishContext` (`:2094`) does `new byte[dis.readInt()]` before authentication, so the allocation size comes from the peer (up to 2 GiB per connection, an OOM). Read, not run: it needs a KDC.
- **HTTPS:** see F3; it shares `SslListenHandle.acceptLoop` and the SSLSocket path (F2).

### F13 INFO: S0 and S1-S4
- **Order:** staging S0 first and independently is right. S0 is server-side and wire-neutral, and it does not depend on `SelectionManager`.
- **No conflict with `detachEngine()`,** provided:
  - (i) the handshake stays the blocking `SslEngineChannel` loop;
  - (ii) the watchdog is disarmed by the completion CAS (F4) before any hand-over, so it can never close a channel the new `ConnectionIO` owns;
  - (iii) S0 does not move the handshake wait onto `SelectionManager`. That would collide with S1's de-singleton change; blocking I/O on a virtual thread avoids it.
- **Removing the Selector makes S2 simpler:** the channel stays blocking until the hand-over switches it.
- **Write the cap, queue and deadline in terms of "handshakes in progress", not threads,** so they survive a later non-blocking handshake.

## Recommended shape of S0

**In:**
1. Accept loop: `accept`, socket options, register in `inProgress`, start a handshake virtual thread. Nothing else runs on the accept thread.
2. Handshake virtual thread: `acceptThreadBuilder` under `Subject.callAs(null)`. Steps:
   - acquire a permit with `tryAcquire(remaining deadline)`;
   - build the `SslServerConnection` (engine or SSLSocket path);
   - `finally` releases the permit and moves the socket from `inProgress` to `connections` under the lock;
   - `handleConnection` keeps today's wrapping.
3. One total server deadline from accept, enforced by a close-the-raw-channel watchdog. One shared, subject-neutral scheduler with remove-on-cancel and a CAS against completion. Applies to both the engine and SSLSocket paths, and to reads, writes and the queue wait. The per-read Selector is removed.
4. Handshake failures never reach `continueAfterAcceptFailure`; they are logged at HANDLED/FINE.
5. A global cap (`Semaphore`) and a bounded queue (a counter of sockets waiting for a permit). Queue full means close the socket.
6. Listener close closes the in-progress raw sockets.
7. An explicit backlog on the TLS/HTTPS listener, plus the same small change for TCP, UDS and Kerberos, as a separate commit with a shared property.
8. Fix the HTTPS accept-thread start (F3) in or before S0.
9. Correct the deadline javadoc and design §2 and §4.6.

**Defaults:**

| Setting | Default |
|---|---|
| Server handshake deadline | 10 s from accept (`...ssl.serverHandshakeTimeout`) |
| Client deadline | unchanged |
| Cap | 256 |
| Queue | 4096 (bounded by the fd budget; nothing allocated while queued) |
| Backlog | `Integer.MAX_VALUE`, meaning the OS maximum |
| Per-address cap | off |
| First-byte permit (F11) | optional |

**Out:**
- non-blocking handshake;
- `SelectionManager` changes;
- SYN-flood protection;
- the Kerberos deadline and allocation fix (separate task);
- the TCP accept path's `handleConnection` move (not needed: no handshake).

## Expected gain, and how to prove or refute it
- **Accept-thread cost:** today 2.6 ms per connection (RESULTS 34 D: `serverConnection` 2.79 traced, of which 1.13 ms is waiting for the peer; `handleConnection` 0.57). After S0 it should be about 0.03-0.05 ms, so it stops being the limit.
- **Rate on the shared 4-core box:** the limit becomes total CPU. Hot resumed CPU per reconnect is about 2.6 ms on the server plus 2.65 ms on the client, plus JIT. Expect about 700-900 resumed per second (2x or more over 385) and about 400 full (from 210).
- **Rate on a dedicated server:** about cores / 2.6 ms, so thousands per second on the 32 and 80-core machines, against a flat 385 today.
- **64 connectors:** no `ListenOverflows`, no 1-9 s connects; p99 at tens of ms.
- **512:** everything accepted at once; the burst is served in about 0.6-0.8 s, with the queue holding the excess over the cap.
- **4096 (Windows):** `Connection refused` should mostly disappear if accept keeps ahead and cap + queue covers the burst. Otherwise the excess turns into immediate closes (EOF or reset) instead of refusals, which is still a failure. A burst also makes client full handshakes more likely (RESULTS 34 "not established" (viii)).
- **Proof:**
  - `TlsAcceptRate` at 1, 64, 512 and 4096 connectors, before and after: accepted per second, p50/p99/max connect-to-mux-header, kernel `ListenOverflows`, refused and reset counts on Windows;
  - accept-thread busy share (JFR);
  - server thread count and heap at the peak;
  - an adversarial run: K silent plus J junk connections while timing a legitimate open;
  - established-connection call p99 during a connection storm (F8).
- **Refutation:** the gain is refuted if the rate does not rise above about 1.3x on the 4-core box or does not scale with cores.
- **Risks:**
  - CPU contention with dispatch (F8);
  - memory, capped at cap times about 100 KiB;
  - unbounded platform threads if the pool is used (F7);
  - a changed rejection mode (close instead of refuse);
  - no ordering is lost (F6).

## Tests S0 needs
1. A real `SslServerEndpoint` with K < cap silent connections: a legitimate handshake completes in under 1 s. Today it takes the full deadline: probe 4.8 s at a 5 s timeout.
2. 12 junk connections, then a legitimate handshake in under 1 s (today 8 s), with no 10 s sleep.
3. A trickling peer is cut off at the total deadline, which includes the queue wait.
4. A peer that never reads: a large server flight or small buffers make the handshake write block, and the deadline still fires. Use a pipe channel or a fake rather than loopback TCP.
5. Listener close: in-progress handshakes end within 100 ms; permits return to the cap; no fd leak (`/proc/self/fd`); a handshake completing concurrently with close never reaches `handleConnection` after close, or is closed. Mutation-test the permit `finally` and the CAS.
6. Cap + 1 and queue-full behaviour; permits are restored after deadlines.
7. Subject and ACC: the handshake thread has `Subject.current()==null`. After it, mux threads still capture the service subject; rerun `SslEngineRoundTripTest` and the identity and SPIFFE suites.
8. The SSLSocket path (custom `ServerSocketFactory`) with a silent peer is bounded.
9. HTTPS listen smoke test (F3).
10. Backlog: with accept paused, 200 simultaneous TCP connects all establish. The probe shows 200 of 200 at 4096 against 53 of 200 at 50.
11. With `org.apache.river.thread.platform=true` and a burst of 2000, the platform thread count stays bounded.
12. Keep `SslEngineChannelTest.testHandshakeReadDeadlineRejectsSilentPeer` (client) passing under the new mechanism.

## Not verified
- Windows backlog clamping and refusal behaviour, and Selector-on-virtual-thread behaviour on Windows. Unverified knowledge only.
- macOS.
- Real WAN round trips.
- The handshake-write stall (F4a, not run).
- The Kerberos pre-auth allocation (read, not run).
- The RESULTS 34 numbers themselves (taken as given; the sections and figures quoted match the file).
- Any S0 implementation, so the gains in the table above are estimates.

## Probes
All in `/tmp/claude-0/-home-user/d4500893-c6b9-5565-960c-ab4381350b7d/scratchpad/probe-transport-s0/`:
- `src/net/jini/jeri/ssl/TransportS0Probe.java` (modes `trickle`, `cancel`, `blockingcancel`, `many N`, `backlog B N`) runs against a copy of HEAD's `SslEngineChannel`.
- `src/net/jini/jeri/ssl/TransportS0Listener.java` (`junk`, `silent`, `silent ssf`, `silent https`) runs against the installed `jgdms-*-4.0.0-SNAPSHOT` jars and jgdms-jeri test-classes, snapshot-copied into `lib/` and `tc/`.

Run with the DirtyChai JDK 27, with `JAVA_TOOL_OPTIONS` unset.

---

## Report: fable seat

### Gap review (Fable seat): S0, server TLS handshake off the accept thread

Scope: what the security seat (`board-s0-security.md`) and the transport seat (`board-s0-transport.md`) both missed or got
wrong, checked against the code at `74d1f7f9f` on `claude/focused-bohr-t6bwal` (read-only; no build). Anchors are
`SslServerEndpointImpl.java` unless named. CONFIRMED = read in source; PLAUSIBLE = inferred.

## Verdict

Both reports are sound and largely agree; neither contradicts the code where I checked. Together they miss four things:
(1) **removing the per-read `Selector` is not a server-only change** — it is the client's only handshake deadline too, so
"drop the Selector" (transport F4) silently alters the client path S0 is meant not to touch; (2) **the cap bounds
concurrency, not CPU rate**: a replayer that lets the server finish its flight frees its slot in ~5 ms, so without a CPU
bound the cap is no defence against the H3 attack even with groups pinned; (3) **the mux connection header on the server has
no deadline** (`MuxServer.java:109,122,133` pass `0L`), so S0's deadline ends at TLS Finished and an authenticated peer can
hold a `connections` slot and two platform threads for ever — mentioned by security L4 as "follow-up", not weighed; (4) the
**client session expiry (18 min, `SslConnection.java:869,973-985`) forces a full handshake in lockstep across a fleet** and
the server's `invalidate()` at expiry is a no-op with stateless tickets (RESULTS §34 C5), so jitter is required, not nice to
have. Rulings on the seven disputed points follow; the recommended S0 shape is at the end.

## Findings the seats missed or got wrong

### G1. HIGH, CONFIRMED (code). "Replace the per-read Selector with blocking I/O" changes the client, and a blocking `SocketChannel` has no read timeout
`fillNetInByDeadline` (`SslEngineChannel.java:586-631`) is the **only** deadline mechanism for the client handshake:
`SslConnection.establishEngine` calls `ec.handshake(clientHandshakeTimeout(callContext.connectionTime))`
(`SslConnection.java:435`, `:472-487`). A blocking `SocketChannel.read` honours no `SO_TIMEOUT`, and nothing in the ssl
package calls `setSoTimeout` (grep: none). Transport F4 "replace the per-read Selector with plain blocking channel I/O" and
F13 (ii) therefore either break `SslEngineChannelTest.testHandshakeReadDeadlineRejectsSilentPeer` (`:367`) and the client's
connection deadline, or require a client-side watchdog as well — a change to the client path that S0's scope excludes.
Ruling: on the **server** use blocking reads plus the close-the-channel watchdog (transport's `blockingcancel` probe: 0-1 ms);
on the **client** keep `fillNetInByDeadline` unchanged, but apply security M1's two fixes to it (one `Selector` field per
connection so close/`wakeup` can cancel it; interrupt is fatal, never `continue`). The method must then be chosen by the
caller (a flag or two entry points), not removed.

### G2. HIGH, PLAUSIBLE (arithmetic on the seats' own measurements). The in-progress cap does not bound handshake CPU, so pinning groups alone leaves H3 open
Security H3 measured 2.7-4.0 ms of server CPU per replayed ClientHello with x25519/secp256r1 (the groups it proposes to
keep). A replayer that reads the server's flight and closes frees its slot as soon as the flight is written (~5 ms), so the
cap N limits concurrency to N but the **rate** to N/5 ms — unbounded in practice. Transport F8 says "measurement, not
mechanism"; that is right for a storm of legitimate full handshakes (whose CPU is work the server must do anyway) and wrong
for a replay flood, which costs the attacker nothing. Ruling: a CPU-concurrency bound is needed (point 3 below), but
simpler than the security seat's three fine-grained points: one permit held from "ClientHello consumed" to "server flight
written" (the whole `handshake0` loop minus the blocking waits is acceptable as a first cut; the waits are the only long
part). Default P = max(2, cores/4). Acceptance test as security H3.3.

### G3. MEDIUM, CONFIRMED (code). After TLS Finished the server mux has no start deadline; S0's deadline must say where it ends and what follows
`MuxServer` constructors pass `handshakeTimeout = 0L` (`:109`, `:122`, `:133`); the client uses 15 s. After S0, a peer that
completes mutual TLS and never sends the 8-byte mux header holds a `connections` entry, and on the stream path two platform
threads (`StreamConnectionIO.java:100-101`), for ever, outside the cap. It is an **authenticated** DoS (the peer passed
client-auth), so lower severity than H1/H2, but S0 is the moment to decide: either pass a server mux start deadline (one
constant, the mux already implements it for the client) in the same change, or state in §4.6 that the handshake deadline
ends at TLS Finished and the mux header is the next stage's bound. Recommend the former; it is a one-line, wire-neutral
change with its own test (silent peer after TLS → connection closed and `connections` empty).

### G4. MEDIUM, CONFIRMED (code + RESULTS §34 C2/C5). The 18-minute client expiry is a fleet-wide full-handshake storm with no jitter, and the server's 20-minute `invalidate()` does not revoke tickets
`checkSessionExpired` (`SslConnection.java:973-985`) is called from `useFor` (`:869`): on reuse, a session older than
`maxClientSessionDuration` is invalidated (client-side `invalidate()` does stop resumption, C5) and the next connection is a
**full** handshake. Creation time does not advance along a resumption chain (C2), so every client does a full handshake
every 18 minutes of wall time, anchored at its last full handshake. A fleet that connected together (after a server restart,
the common case) stays in step for ever. Server side, `processRequestData` invalidates and throws at 20 minutes
(`:1756-1769`), but with stateless tickets the `invalidate()` is a no-op (C5); the throw is the real mechanism and only fires
if a client somehow out-lives its own 18 minutes. Sizing: hot full handshake server CPU 3.8 ms (§34 B); on 4 cores with
P = 2 permits, 1 000 clients in step need ~2 s every 18 min; 10 000 need ~20 s, during which the cap fills, new legitimate
connections queue, and dispatch runs on the remaining cores. Ruling (point 7): **jitter is required**: pick the connection's
own limit once, at creation, uniformly in [0.8, 1.0] × `maxClientSessionDuration`; keep server ≥ client maximum (20 ≥ 18
holds). Both seats recommend jitter (security INFO) or are silent (transport); neither weighed that resumption gives no
relief here, which is what makes the storm periodic rather than a one-off.

### G5. MEDIUM, CONFIRMED (code). The transport seat's in-JVM queue of 4096 ignores the fd budget; the security seat's "kernel backlog" ignores Windows
Each accepted-but-queued socket costs one server fd before any permit. Default `ulimit -n` is 1024 on many Linux desktops
and 4096 here before HotSpot raises the soft limit to the hard one; 4096 queued sockets plus N handshakes plus the live
connections can exhaust fds on a small host — the failure mode is then `accept()` throwing, which **does** feed
`continueAfterAcceptFailure` and sleeps the listener 10 s (`:1313-1330`), the H1 outage by another route. Conversely,
stopping `accept()` at the cap leaves the kernel backlog as the queue; Linux overflows by dropping SYNs (clients retry at
1, 3, 7 s), **Windows answers overflow with RST** (`Connection refused`, the maintainer's symptom), and its listen queue is
clamped to about 200 whatever `backlog` says. Ruling at point 1.

### G6. LOW, CONFIRMED (code). Thread and subject model: the seats' two answers are compatible, and one hygiene point is missing
`ThreadPool.Task.run` (`ThreadPool.java:239-250`) does not wrap in `callAs(null)`; the factories do create *threads* under
`callAs(null)` (`:135`, `:154`), which covers the platform pool but not `newVirtualThreadPerTaskExecutor`, whose threads are
created by the submitter and (DirtyChai) capture the submitter's `Subject.current()`. So security M3 is right that the pool
is not subject-neutral, and transport F7 is right that creating handshake threads from the accept thread with
`acceptThreadBuilder` under `Subject.callAs(null)` gives null (the accept thread itself was created that way, `:1225-1231`).
Both must hold: F7's mechanism plus M3's assertion test. Missing from both: the **watchdog scheduler thread** has the same
problem as the `AckTimer` I flagged in the stage-2 review — if it is created lazily from a dispatch thread it inherits that
thread's subject and context class loader for the JVM's life. Create it eagerly, privileged, under `callAs(null)`, or use
`org.apache.river.thread.WakeupManager`.

### G7. LOW, CONFIRMED (code). F3 (HTTPS never accepts) is real, dates from `fd07efe91`, and is independent of S0
`createListenHandle` in `SslServerEndpointImpl` starts the accept thread (`:1068-1079`, added in `fd07efe91`:
`+listenHandle.acceptThread = listenHandle.createAcceptThread(); +listenHandle.acceptThread.start()`);
`HttpsServerEndpoint.HttpsListenEndpoint.createListenHandle` (`HttpsServerEndpoint.java:895-900`) overrides it, returns a
new `HttpsListenHandle` and never starts anything; `HttpsServerEndpoint` has not been touched since except for constants
(`a863c0f40`, `dc3603a97`, `14de223c2`). No test lists HTTPS. Ruling at point 4.

### G8. LOW, CONFIRMED (code). Two more pre-authentication reverse DNS lookups and an eager `toString()`
`:1653` (`getHostName()` for the SSLSocket factory host argument), `:1703` and `:1707` (`toString()`), and `:1691` logs
`toString()` eagerly at FINE whatever the level. Security L4 has `:1691`; transport is silent; add `:1653`/`:1703`/`:1707`.
Use `getHostAddress()` throughout and pass `this` to the logger.

### INFO. Smaller points neither seat states
- `continueAfterAcceptFailure` **swallows the interrupt** (`:1375-1380` re-sets the flag but sleeps anyway): a listener
  `close()` during the 10 s sleep waits the full 10 s. After S0 the throttle only sees `accept()` failures; keep the sleep
  interruptible.
- Security's "3 fds per waiting handshake" is the per-read `Selector` (epoll + eventfd); with G1's server-side blocking read it
  becomes 1, which changes the N and queue arithmetic in both reports (N = min(512, maxFds/8) assumed 3).
- Backlog overflow on Linux with a large backlog: up to `somaxconn` (4096 here) established-but-unaccepted sockets, each
  with a receive buffer an attacker can fill; kernel memory, the OS's problem, but state it when recommending the OS maximum.
- `createListenHandle` is package-private and overridable; S0 should make the start step `final` in `listen()` so the HTTPS
  regression cannot recur (transport F3 says this; security is silent).

## Rulings on the seven points

1. **Waiting at the cap.** Neither alone. Use a **bounded in-JVM admission queue sized by the fd budget**, default
   `min(4096, maxFds/4 − N)` (from `UnixOperatingSystemMXBean.getMaxFileDescriptorCount()`; Windows has no equivalent, use
   4096), with **nothing allocated while queued** (no engine, no buffers) and the total deadline T **starting at accept**.
   When that queue is full, **pause `accept()`** (the security seat's mechanism) rather than accept-and-close: pausing lets
   Linux clients wait in the kernel queue and succeed, where accept-and-close is a certain failure on every OS. The reason
   to have the in-JVM queue at all is Windows: its listen queue is clamped (~200) and overflow is RST, so a pure
   stop-accept design reproduces the maintainer's `Connection refused` at 4096 connectors. Transport's "close when full"
   is rejected; security's "no in-JVM queue" is rejected for Windows.
2. **Cap and backlog.** Cap **512**, as `min(512, maxFds/8)`: the cap must cover accept rate × p99 handshake wall time on a
   WAN (400/s × 1 s = 400), and after G1 a waiting handshake costs 1 fd and ~56-66 KiB, so 512 is ~30 MB. CPU is bounded by
   P, not N (G2). Backlog: **`Integer.MAX_VALUE` meaning the OS maximum**, with a property to lower it (transport). Linux
   clamps to `somaxconn`, macOS to `kern.ipc.somaxconn` (128 by default — document it), Windows treats `0x7fffffff` as
   SOMAXCONN. 1024 is only safe where `somaxconn ≥ 1024`, and it is lower than this container's 4096 for no gain.
3. **Named groups and the CPU semaphore.** **Pin the server's groups** via `SSLParameters.setNamedGroups` on the engine (not
   the JVM-wide `jdk.tls.namedGroups`): `X25519MLKEM768, x25519, secp256r1`, property-overridable. Interoperability: TLS 1.3
   makes secp256r1 mandatory-to-implement and x25519 near-universal; Go `crypto/tls` (SPIFFE agents, go-spiffe) offers
   X25519 and P-256; OpenSSL/BoringSSL likewise; a client that offers only ffdhe* or secp521r1 gets a HelloRetryRequest and
   can switch. The hybrid group is only JDK-to-JDK today; keep it first in the list, it is harmless to non-JDK peers. **The
   CPU bound is needed in addition** (G2): one permit around the server's flight computation, P = max(2, cores/4), with the
   replay-flood acceptance test. Transport F8's "measure first" is right for legitimate storms and wrong for replay.
4. **F3.** Real and confirmed (G7). **Separate fix, first**, with an HTTPS listen smoke test: it is a one-line regression that
   has disabled every HTTPS listener since 2026-06-07 (`jgdms-discovery-providers` HTTPS discovery server included), it has
   no dependency on S0, and S0 should then build on the fixed shape (`listen()` calls a `final` start after
   `createListenHandle`). Landing it inside S0 would make a security refactor carry an unrelated bug fix and delay the fix.
5. **M3 vs F7.** Both, they are not in conflict (G6): create handshake threads from the accept thread with
   `acceptThreadBuilder` inside `Subject.callAs(null)`; wrap `handleConnection` exactly as today
   (`doPrivileged(securityContext.wrap(..), acc)` then `callAs(serverSubject)` / WorkerSubject branch, `:1270-1278`,
   `:1411-1441`); never route the handshake through `ThreadPool` (platform mode makes it unbounded, F7); assert
   `Subject.current()==null` in the task and the unchanged `MuxServer` capture (M3's test). The `ThreadPool` doc-vs-code
   drift is a separate task. Add the watchdog thread's own subject/context hygiene (G6).
6. **Watchdog with a single CAS.** Sound, with these conditions: (a) it closes the **raw** channel/socket, never
   `SslEngineChannel.close()` (which takes `writeLock` and does a blocking `close_notify`, transport F5); no `close_notify`
   is owed to a peer that failed to finish a handshake; (b) the server handshake uses blocking reads so the close is
   observed in 0-1 ms (G1) — with the current per-read `Selector` the close is **not** observed until the deadline (both
   seats' probes), so the CAS alone does not deliver cancellation; (c) the CAS is `RUNNING→DONE` by the handshake thread
   before `connections.add`, `RUNNING→TIMED_OUT` by the watchdog before `close()`, and permit release/`inProgress` removal
   is guarded by a separate once-flag because both the timed-out task and the watchdog reach the `finally` (security L3c);
   (d) after DONE the watchdog is disarmed before any hand-over, which is what S2's `detachEngine()` will rely on (transport
   F13 ii); (e) a raw close leaves `SslEngineChannel.closed=false`, so a later `closeInternal` attempts a `close_notify` on a
   dead channel — harmless but logs; set the channel's state or make `closeInternal` tolerate it; (f) the hole that remains
   is G3: DONE only means TLS Finished, and the next stage has no deadline.
7. **Session limits and the reconnect storm.** The full-handshake rate is not a problem on average (1 000 clients → ~1/s),
   but the fleet is in lockstep (G4): 1 000 clients → ~2 s every 18 min at P = 2 on 4 cores, 10 000 → ~20 s with the cap
   full and new legitimate connections queued. **Jitter the client expiry** ([0.8, 1.0] × limit, fixed per connection at
   creation); keep server ≥ client maximum. Also note in the design that resumption does not spread this load (creation time
   is anchored at the last full handshake, C2), and that the server-side `invalidate()` at expiry is inert with stateless
   tickets (C5) — the `SecurityException` is the mechanism.

## What S0 can break

- **Existing tests:** `SslEngineChannelTest.testHandshakeReadDeadlineRejectsSilentPeer` (client) if the Selector read is
  removed globally (G1); any ssl/SPIFFE test that times a listener close (the 10 s throttle sleep is interrupt-swallowing);
  tests that rely on handshake failures appearing at INFO.
- **Other endpoints:** HTTPS shares `SslListenHandle.acceptLoop` and the SSLSocket path, so S0 must cover both (security
  H2, transport F2) and the F3 fix must precede it. No QUIC server endpoint exists in `net.jini.jeri` (packages:
  connection, http, kerberos, ssl, tcp, uds); Kerberos has its own accept path with no deadline and a peer-sized
  pre-auth allocation (transport F12) — out of S0, but the shared backlog property should reach it, TCP and UDS.
- **Behaviour change visible to clients:** a cap/queue overflow turns a Linux client's delayed connect into either a wait
  (pause-accept) or, if accept-and-close were chosen, an EOF during the handshake; JERI's `ConnectionManager` does not retry
  a failed connect, so the call fails. This is why point 1 rules for pause-accept.

## Windows, which the Linux probes cannot show

- Listen queue clamped to about 200 and RST on overflow: the `Connection refused` at 4096 connectors is almost certainly
  this; S0's in-JVM queue is the mitigation, and `TlsAcceptRate` on the workstation before/after is the proof.
- **Client-side ephemeral port exhaustion** is a second candidate for the same symptom: Windows' default dynamic range is
  16 384 ports and TIME_WAIT is 120 s; a benchmark that opens and closes thousands of TLS connections can run out
  (`netstat -an | find /c "TIME_WAIT"` during the run). Not a server bug, and S0 will not fix it; check it so the S0
  measurement is not misread.
- `Selector.select` on a virtual thread with `WEPoll` (parking vs pinning) is unverified; G1's server-side blocking read
  avoids the question for the server, and the client keeps today's behaviour.
- `UnixOperatingSystemMXBean` is absent on Windows: the fd-budget formulas need a Windows fallback (handles are not fds).
- `AsynchronousCloseException` on a cross-thread close of a blocking `SocketChannel` read works on Windows NIO; the
  transport probe confirmed Linux only — re-run `blockingcancel` on the workstation.

## Recommended final S0 shape

**Before S0 (separate commits):** F3 HTTPS accept-thread start + smoke test; explicit backlog (OS maximum, property) on
TLS/HTTPS and the same small change for TCP, UDS, Kerberos; `getHostAddress()` for the pre-auth lookups (G8).

**In:**
1. Accept loop does only `accept`, socket options, `inProgress` registration under the lock, and starts a handshake virtual
   thread via `acceptThreadBuilder` under `Subject.callAs(null)`. Only `accept()` failures feed the throttle; the sleep is
   interruptible. Handshake failures log at FINE with a rate-limited INFO summary and counters (security M4).
2. Admission: global in-progress cap N = min(512, maxFds/8) (`Semaphore`); bounded in-JVM queue min(4096, maxFds/4 − N),
   nothing allocated while queued, T from accept; pause `accept()` when the queue is full; per-address caps off (override
   only).
3. CPU bound: P = max(2, cores/4) permits around the server flight computation; server named groups pinned
   (`X25519MLKEM768, x25519, secp256r1`, property).
4. Total deadline T = 10 s from accept, separate server property (`org.apache.river.jeri.ssl.serverHandshakeTimeout`),
   enforced by a shared, eagerly created, subject-neutral watchdog that closes the raw channel/socket, CAS against
   completion, once-only release; applies to the engine path (blocking reads on the server; the client's `Selector` path
   kept, with M1's cancel/interrupt fixes) and to the SSLSocket path (`startHandshake()` under `setSoTimeout(remaining)`
   plus the same watchdog).
5. Server mux start deadline (G3), one constant, with a silent-after-TLS test — or an explicit statement that it is the next
   stage.
6. Listener close: snapshot `inProgress` and `connections` under one lock; close raw in-progress sockets; `closeInternal` on
   live ones; the completion-vs-close race kept atomic as today (`:1259-1269`).
7. Client session expiry jitter [0.8, 1.0] (G4), server ≥ client maximum unchanged.
8. Tests: the union of both seats' lists, plus: client `SslEngineChannelTest` deadline test still passing; `MuxServer`
   subject capture unchanged; replay-flood CPU ≤ P cores; silent-after-TLS; fd count back to baseline after every failure
   kind; `TlsAcceptRate` on the Windows workstation before/after with refused/reset counts.

**Out:** non-blocking handshake; `SelectionManager` changes; SYN-level protection; Kerberos deadline/allocation fix (own
task); first-byte permit (transport F11, optional later); `ThreadPool` doc/code reconciliation (own task).

**Defaults:** T 10 s; N 512 (fd-bounded); queue min(4096, maxFds/4 − N); P max(2, cores/4); backlog OS maximum; per-address
off; groups as above; failures at FINE, summary every 30-60 s; client expiry jitter 0.8-1.0.

---

