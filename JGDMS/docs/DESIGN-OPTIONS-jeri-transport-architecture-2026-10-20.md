# JERI transport architecture: options and the evidence for each

Status: **options only, nothing here is decided or scheduled.** Written 2026-10-20 for the maintainer, who is still
thinking about the architecture. It supersedes the proposal in `DESIGN-jeri-concurrent-transport-2026-10-20.md`
(a new provider, not approved) and collects what the measurements since then say. Every number is from one 4-core Linux
container (JDK 27 `27-internal`, server in its own JVM, small `ping`/`record` calls) unless it says otherwise; the
workstation (6 cores) and the 32- and 80-core machines have not been run on the code described here. Labels:
**[measured]** from `jgdms-benchmarks/serialization/RESULTS.md` (sections 23 to 30), **[reasoned]** inferred from code,
**[estimate]** a guess that nothing has measured.

## 1. Where things stand

Small DER calls, one caller, then 16 callers (calls per millisecond summed over callers). RMI has no UDS; its rows are
plain TCP (the reference for UDS and TCP) and TLS (the reference for TLS).

| | 1 caller, wall | thread hand-offs, both JVMs | CPU per call, both JVMs | 16 callers |
|---|---:|---:|---:|---:|
| JERI DER, UDS | 90 us | 4.1 | 80 to 100 us | 45 to 57 |
| JERI DER, TCP, channel I/O (`useNIO`, off by default) | 91 to 107 us | 4.1 | ~100 us | 37 to 41 |
| JERI DER, TCP, stream (default) | 126 to 145 us | 8.1 | 170 to 190 us | 41 to 53 |
| JERI DER, TLS (stream) | 200 to 230 us | 8.3 | 240 to 270 us | 25 to 32 |
| RMI, plain TCP | 53 to 80 us | 2 | ~58 us | ~121 |
| RMI over TLS | 56 us | 2 | ~58 us | ~108 |

Where JERI started today (the same container): UDS 132 us / 6.1 hand-offs / 33 to 40 calls/ms; TLS 16 callers 19 to 21.
Ratio to RMI now: UDS and TCP about 0.4 of RMI's throughput at 16 callers and 1.2 to 1.7 times its latency for one
caller; TLS about 0.28 of RMI over TLS at both. **[measured]**

What was done to get here (all committed on `claude/focused-bohr-t6bwal`, each small and reversible): one write per mux
message on the stream path (TCP +55%, TLS +35% at 16 callers); one batching drainer on the channel path (UDS +30%); the
`SelectionManager` loop on a platform thread instead of a virtual one (UDS one caller 132 to 90 us); the acknowledgment
listeners told when no acknowledgment will come. Not the cause of the remaining gap: the DER codec and the invocation
layer (the Java code on a call is 3 to 4 us of the 60 to 90 us each JVM spends), the remote context, stack traces.
**[measured]**

**Where a call's CPU goes.** An RMI call is one `write` and one `read` per side and almost no `futex`: each thread blocks
in its own socket and the kernel wakes it. A JERI call on UDS made 12 system calls per side before the platform-thread
change, 6 of them `futex` (cross-thread wake and park) and 3 `epoll_wait`. The remaining difference is thread hand-offs:
JERI crosses 4, RMI 2. **[measured, section 27]**

## 2. What any design has to keep

These are the rules earlier reviews established (`BOARD-REVIEW-jeri-concurrent-transport-2026-10-20.md`, the Fable gap
review, the UDS and DGC work). Options that bend one are marked.

1. **Frame bounds and no allocation sized from a peer's declared length** (board B1); a malformed frame closes the
   connection. Unaffected by anything below.
2. **Any write failure takes the connection down** before another frame is attempted (A4), including an interrupted but
   not failed write on a TLS socket (a partial record corrupts the stream).
3. **A peer that stops reading must not hold a caller or the reader hostage.** Bounded drains and a write timeout.
4. **The reader never blocks on the peer and never runs application code** (A3/B3/C-F6). *Option C2 below bends this.*
5. **Constraints and the subject are per connection**, decided by `Endpoint.connect(handle, active, idle)` for every
   reuse; SSL and Kerberos keep a pool of one until measured. UDS increment 2 will make a UDS connection
   identity-bearing (SVID), so UDS reuse must go through the same per-subject gate.
6. **The wire format is the existing mux protocol** (option A of the first design) so old and new peers interoperate.
7. **Production transports are TLS (internet) and UDS (process isolation); plain TCP is a test transport.** Gains that
   only help TCP are worth little.
8. **Extensibility:** `ConnectionIO`, `ConnManager`, `ServerEndpoint` are replaceable; the reviewers judged `ConnectionIO`
   (inside the mux) the right seam and a new provider unapproved.

## 3. Decision areas

### D1. Who reads the socket, and where does a request run

The hand-off floor comes from here. Today a dedicated reader (the selection thread, or a mux reader thread per stream
connection) is woken by the kernel, parses frames, and wakes the consumer: the waiting caller on the client, a new
virtual thread on the server. That is 2 hand-offs per JVM per call (4 in all) against RMI's 1 (2 in all), where each thread
blocks in its own socket. Each hand-off costs roughly 10 to 30 us of wall time and the futex and scheduler work behind it.

| Option | What changes | Hand-offs (both JVMs) | Evidence | Risk |
|---|---|---:|---|---|
| **C0** today | dedicated reader; request runs on a pool thread | 4 | [measured] | none |
| **C1** caller reads its own response | the waiting caller runs the select and read (leader/follower, which `SelectionManager` already partly supports); one waiter reads for the others under concurrency | 3 [estimate] | not prototyped | medium: a new read-role protocol in `SelectionManager`/`Session`/`MuxInputStream`; no rule bent |
| **C2** reader runs the request inline | after parsing a request the reader runs the handler itself; a slow handler must not stall the connection | 2 [estimate] | spike below | high: bends rule 4 |
| **C2a** with a custom virtual-thread scheduler | the handler is a virtual thread whose scheduler is the reader thread; a handler that blocks yields the reader back | 2 for short handlers [estimate] | spike [measured] | high: private JDK constructor, rule 4 reinterpreted |
| **C3** adaptive spin before parking | the waiter spins 10 to 30 us on a flag so the poller can skip the futex wake | count unchanged, cost of each falls from ~10 to 15 us to ~0.1 us | not prototyped | medium: burns CPU, harmful when threads outnumber cores |
| **C4** blocking read per connection on virtual threads | the RMI model with virtual threads | 2 per JVM | [measured against]: a virtual thread blocked in a read or select is woken through the JDK poller (kernel to poller thread to scheduler to carrier), which is the extra hop removed in section 28 | not worth it |

**Spike (section 30).** One platform reader in a `Selector`, Unix sockets, one-byte request and reply:

| | 1 client | 4 clients, quick handler | 4 clients, handler parks 500 us |
|---|---:|---:|---:|
| handler inline on the reader | 32.7 us | 79.5 calls/ms | 1.5 (reader blocked) |
| virtual thread, default scheduler (today) | 62.7 us | 40.5 | 4.0 |
| virtual thread scheduled on the reader | 43.0 us | 62.5 | 3.5 |

The reader-scheduled virtual thread recovers two thirds of the default scheduler's 30 us hand-off, is 54% faster with
four clients, and does not stall the reader when the handler blocks (inline does). It costs 12% against the default for a
handler that does block. It needs `java.lang.ThreadBuilders$VirtualThreadBuilder(Executor)`, package-private in this
JDK: reachable by reflection with `--add-opens`, or by exposing it in the DirtyChai JDK.

Questions this area poses:
* Is rule 4 ("the reader never runs application code") about not blocking the reader, or about not running application
  code on a thread the application cannot bound? C2a satisfies the first and not the second.
* A handler that blocks without yielding (native code, a pinned monitor) stalls the reader under C2/C2a. How is that
  bounded (a watchdog that promotes a new reader, a deadline, an opt-in per exported object)?
* Subject and access-control-context propagation to a continuation scheduled on the reader must match the current pool.
* C1 and C2 compose: C1 takes the client side from 2 to 1 and C2 the server side from 2 to 1.

### D2. Connection topology

One connection per endpoint serialises every call on one drainer and one mux lock; RMI uses one connection per concurrent
call. What opening costs **[measured, section 30]**:

| transport | opening a connection | in warm calls | extra threads | heap | resident |
|---|---:|---:|---|---:|---:|
| UDS | 2.3 ms | ~20 | none (shared selection thread) | 0.2 MB | ~0.8 MB |
| TCP, channel I/O | 2.3 ms | ~20 | none | 0.2 MB | ~0.8 MB |
| TCP, stream | 2.2 ms | ~20 | 2 platform | 0.2 MB | ~1.1 MB |
| TLS | 12 ms | ~45 | 2 platform | 0.6 MB | ~1.9 MB |

Striping (the in-flight threshold `K` in `ConnectionManager.connect`), repeated on top of the batching changes:
UDS +20% at 2 to 4 connections, TCP +10 to 17%, TLS **-17% (K=4) to -35% (K=2)**. Most of the first experiment's TCP gain
(+50%) was the double write that one write per message removed.

Options: **T0** one connection (today); **T1** a small lazy bound (2 to 4) for UDS only; **T2** one connection per
concurrent call with a cap (the RMI shape); **T3** striping that follows the identity-bearing UDS design from the start.
Related and independent of the choice: **the cold burst.** When N callers find no connection they each open one (the
current code opens about N), so 16 callers on TLS pay 16 handshakes of 12 ms where one open and waiting callers would pay
once. Serialising the open step per endpoint is small and removes that cost whichever topology is chosen. **[reasoned,
the burst itself measured]**

Security questions: stripes can end up satisfying different constraint sets; striping multiplies the server's connections
and TLS handshakes with no per-peer cap or handshake deadline (A6); credentials expire between selection and write.

### D3. The locks

Contended entries at 16 callers, 46.7 calls/ms, ~600 000 calls **[measured, section 29]**:

| lock | blocked time per call |
|---|---:|
| mux lock, server | 2.4 us |
| mux lock, client | 1.3 us |
| `ConnectionManager.connect` (`pendingConnects++`, the mux scan, three or four acquisitions per call) | 7.2 us |
| method-hash cache, proxy-method check | ~1 us |

Against 343 us of wall time per caller these are 0.4%, 0.4%, 2% and 0.3%: on 4 cores nothing here limits throughput;
per-call CPU does. Contention grows with the number of cores, so the 32- and 80-core machines are where one lock per
connection could become the ceiling (a serial section of S microseconds per call caps one connection at 1/S calls per
microsecond whatever the cores [reasoned]). `jfr-monitors.py` reads the answer from a recording on those machines.

Options: **L0** leave it; **L1** the cheap ones outside the mux (`ConnectionManager.connect` fast path with a
copy-on-write mux array and atomic request counts; `ConcurrentHashMap` for the per-call caches); **L2** a lock-free mux:
session table as an atomic array with a bitmap allocator, `muxDown` as a CAS state, the send queue as a multi-producer
queue with the single-drainer state in one atomic (the channel path already has the drainer protocol under the lock), and
`wait`/`notify` on the mux lock replaced by park/unpark. L2 is a board-level change (lock-free protocols are easy to get
subtly wrong; stress tests, fault injection, mutation testing). Evidence for doing it: none on 4 cores; unknown above.

### D4. The TLS path

TLS is the production transport and the largest gap (JERI over TLS reaches ~0.28 of RMI over TLS, 8.3 hand-offs, 240 to
270 us of CPU against 58). The stream path has no stage-1-sized lever left. The channel path cannot be used as it is:
`SslConnection.getChannel()` is deliberately null because the raw channel carries ciphertext, and the channel path would
read and write that directly.

**TLS-C: an `SSLEngine`-driven non-blocking `ConnectionIO`.** The mux hands the drainer plaintext frames; the drainer
`wrap`s them into ciphertext and gathers that into the channel write; the selection thread reads ciphertext, `unwrap`s it
and passes plaintext to `processIncomingData`. The handshake stays blocking on connect (`SslEngineChannel` already drives
it) and the engine is then handed over. Things a design must settle: the engine is not thread-safe for concurrent
`wrap`s, so the single drainer must also own `wrap`; `unwrap` runs on the selection thread; TLS 1.3 key updates and
post-handshake messages make `unwrap` ask for a `wrap`; `close_notify` and half-close; ciphertext backpressure when the
socket is full (the drainer's hand-over to the selector now carries ciphertext); a partial TLS record must never reach a
later writer after a failure; delegated tasks run inline. Security: identity and constraint checking stay where they
are (the handshake and `connect`); the new code is the record layer driver, which is the part to review hardest.
Expected gain: the stream path's 8 hand-offs to the channel path's 4 (UDS and TCP-NIO show 126 to 145 us to 90 to
107 us for one caller) plus whatever TLS-specific cost the single drainer removes. **[estimate]** Not prototyped. This
needs its own board review.

### D5. Thread model

Commit 1821b2f25 (2026-05-12) moved the system pool from a cached pool of platform daemon threads (stack hint 228,
tuned for memory) to a virtual thread per task. **[measured, section 28]** UDS one caller: all virtual 132 us, all
platform 92 us, select loop on a platform thread and the rest virtual 90 us; 16 callers: virtual 37 to 40, platform
35 to 37, hybrid 45 to 57 for UDS, and platform costs TCP 28 to 32 against 40 and TLS 21 against 24 to 25. The select loop
is now a platform thread. `-Dorg.apache.river.thread.platform=true` selects the old pool. The memory side the platform
pool was tuned for is not measured. A custom scheduler (D1) adds a dependency on a JDK-internal constructor.

### D6. The TCP and TLS I/O model

TCP has `-Dorg.apache.river.jeri.tcp.useNIO=true` (default false): one caller 28% faster (91 to 107 against 126 to 145 us),
16 callers about 8% lower on 4 cores (37 to 41 against 41 to 53; `record` equal). Whether the default should change
depends on which side of that the deployments are, and TCP is not a production transport, so this is a test-fixture
choice more than a design one. The TLS equivalent is D4.

### D7. In place or a new provider

The board's verdict (stage review, Fable gap review) was an in-place programme first and a new provider only if a gap is
left, with `ConnectionIO` as the seam. After the in-place work the gap that is left is the hand-off floor (D1), the
topology (D2) and TLS (D4); D1 and D4 sit inside the `ConnectionIO`/`Mux` seam, D2 inside `ConnectionManager`. A new
provider would still have to preserve every rule in section 2; the measurements do not show one that the seams cannot
reach.

## 4. Combinations

| Programme | Contents | Hand-offs | Estimated one-caller UDS latency | Notes |
|---|---|---:|---:|---|
| **P0** stop here | what is committed | 4 | 90 us | UDS about 1.2 to 1.7 times RMI for one caller, 0.4 of its throughput at 16 |
| **P1** conservative | P0 + L1 + serialised connection open + TCP/TLS defaults reviewed | 4 | 90 us | no rule bent; helps cold start and many callers |
| **P2** client reads | P1 + C1 | 3 | ~75 to 80 us [estimate] | no rule bent; medium |
| **P3** TLS channel | P1 + D4 (TLS-C) | 4 for TLS | TLS ~120 to 140 us [estimate] | the production gap; needs its own review |
| **P4** run to completion | P2 + C2a | 2 | ~60 to 65 us [estimate] | RMI's hand-off count; bends rule 4, private JDK API |
| **P5** high-core scaling | any of the above + L2 and, for UDS, T1 | unchanged | unchanged | only if 32/80-core data shows the lock or the single drainer as the ceiling |

The programmes are not exclusive; P3 is independent of P2 and P4. The estimates are mine: the single-caller figures take
10 to 15 us per removed hand-off, which the spike's 30 us (default scheduler) and 10 us (reader scheduler) bracket.

## 5. What is not known

1. **The 6-core workstation and the 32- and 80-core machines** on the code above. Everything quantitative here is one
   4-core container with small calls. In particular whether the single connection scales on many cores (D3, D2).
2. **Large payloads** (`blob64k`, `batch100`) after the batching changes: the stream gather copies up to 64 KiB per
   message and the channel drainer hands anything past eight rounds to the selector. Not reported.
3. **Memory** of the platform-thread pool against the virtual one (the reason it was tuned).
4. **The on-reader scheduler inside `MuxServer`**: only the isolated spike exists; subject propagation, interrupt and
   cancellation, and a blocking handler under real load are untested.
5. **A TLS channel prototype** (D4), and RMI-versus-JERI over TLS on the 32/80-core machines (the "JERI scales better
   under heavy contention" expectation has not been tested on the current code).
6. **The remaining, undiagnosed part of the 16-caller gap on UDS.** About a third of the CPU was idle in the earlier
   runs; the lock measurements say it is not lock contention on 4 cores, and the cause (the single drainer, scheduling,
   the load generator sharing cores) is not established.

## 6. Decisions for the maintainer

1. Is the target RMI parity for small calls (P4), or "close enough that multiplexing's benefits outweigh it" (P1 to P3)?
2. Does rule 4 stand as written, or as "the reader must stay responsive"?
3. Is a dependency on a package-private JDK constructor acceptable, given the DirtyChai JDK can expose it?
4. TLS: is a non-blocking `SSLEngine` `ConnectionIO` worth its review burden (D4)?
5. Is the UDS pool (T1) wanted before UDS increment 2 fixes the identity-bearing design?
6. Which machine runs the next measurements, and should the memory of the two pool modes be one of them?

## Appendix: reproducing

`jgdms-benchmarks/serialization`: `sweep.py` (JMH sweep), `ThreadProfile` (per-thread CPU and context switches, one or
many callers, server in its own JVM with `-Dthreadprofile.server=<dir> -Dthreadprofile.serverpid=<pid>`),
`ConnectionCost` (cold-connection latency and per-connection resources), `jfr-monitors.py` (contended monitors from a JFR
recording), `InlineVirtualThreadSpike` and `InlineVirtualThreadHeadOfLineSpike` (the D1 spike; run with
`--add-opens java.base/java.lang=ALL-UNNAMED`). Build and run with `JAVA_HOME=/opt/dirtychai/jdk-27` (the modules need
JDK 25 or later). Handover: `HANDOVER-der-codec-performance-2026-10-18.md` items 6 to 10.
