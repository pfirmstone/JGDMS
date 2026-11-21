# Relay isolation: security design

Written by the design session (Claude Fable 5.2) on 2026-11-16 for the implementing agent
and the operator. It records what a hostile smart proxy can still attempt once it runs in
its own JVM behind the relay, and the requirements that follow. Nothing here has been built
or tested unless a section says so. Operator rulings are cited by register id; everything
else is a design-session proposal and is marked as such.

Additions of 2026-11-21, made by a Claude Opus 5.5 session at the operator's request: from
a review of the timing attacks that remain, R16h, R16i, open points 10 and 11, and dated
paragraphs in R8, R16, R16e and R20; then R16j (the operator's decision that HotSpot never
compiles proxy code), R16k, and an amendment to R13 after reading the landed hand-off
code. Except R16j's decision itself, all are proposals; none is registered, and nothing in
them has been measured.

## Trust boundaries

| Party | Trusted | Holds |
|---|---|---|
| Host (client application) | yes | caller Subjects, client constraints, the thin stub |
| Relay (on the proxy board) | yes | TLS connections, destination table, tokens, minimum constraints |
| Signer (optional) | yes | node key |
| Proxy JVM (on the proxy board) | no | downloaded proxy code, marshalling, the arguments the host passes in |
| Service | authenticated, not trusted | wrote the proxy bytes, the codebase and every endpoint in them |

The proxy JVM's only channel out is the Unix socket to the relay (RL-JGDMS-0096). Real
endpoints are replaced in the DER bytes by a trusted process before the proxy JVM
deserializes them, so the proxy never holds a host or port (RL-JGDMS-0083, RL-JGDMS-0088).

## Requirements

### Relay

- **R1. Server authentication is mandatory** on every connection the relay makes for a
  hosted proxy (RL-JGDMS-0097). It is enforced in the relay. The check in
  `BasicInvocationHandler.authenticateServer` now runs in the untrusted JVM and is advisory;
  whether it accepts `RelayEndpoint` is a compatibility question only.
- **R2. The host's client constraints bind the relay** (RL-JGDMS-0098). The host registers
  them with the relay in the same trusted registration that issues the per-call token
  (RL-JGDMS-0101). For a request carrying that token the registered constraints are
  requirements. The constraints blob in the relay endpoint header comes from the proxy JVM
  and may only add requirements; it never removes or relaxes one.
- **R3. Principal pin** (proposal). Each destination id records the server principals that
  authenticated the service when the proxy was obtained (`resolve()` already derives them).
  The relay requires those principals for that id, including for ids issued for proxies
  that arrive later in replies. With R1 this means caller credentials and request bytes go
  only to the service that supplied the proxy.
- **R4. Requests without a token** (proposal) carry no caller identity and are still held
  to R1 and R3.
- **R5. Delegation** (proposal) is refused unless the host's registered constraints ask
  for it.
- **R6. Token limits** (proposal). A token is bound to one subprocess and its destination
  ids, has a short expiry and a request-count limit, and is revoked when the host call
  returns. Reason: the proxy composes the request bytes, so within a token's life it can
  make calls as the caller that the host did not ask for.
- **R7. Destination ids** (proposal) are unguessable, bound to the subprocess they were
  issued to (checked by peer credentials on each connection), and never reused. The proxy
  can construct a `RelayEndpoint` with any id string; the binding is what stops it using
  another proxy's id.
- **R8. Uniform failures** (proposal). A refused or failed destination returns the same
  reply regardless of cause, released on the relay's tick, so the proxy cannot use connect
  outcomes or timing to probe addresses.
  *Added 2026-11-21 (proposal):* the tick applies to everything that reaches the proxy
  JVM, not only refusals: relay replies, calls dispatched by the host over the hand-off
  socket, and grant deliveries on the policy socket. Reason: a colluding service controls
  when its reply arrives, and each arrival the proxy can see is a clock edge to count loop
  iterations against. On the grid the service can still choose how many ticks a reply
  takes; that is a channel to the proxy, which the reply's content already is, and no
  longer a fine clock.
- **R9. Robust input handling** (proposal). Constraints are decoded with local classes
  only; every length is capped; the timeout covers connect and the whole request, not only
  the opening header; concurrent connections per subprocess are capped; the relay never
  sends file descriptors and reads without accepting ancillary data.
- **R10. Audit record** (proposal). Per token the relay records destination ids used,
  request count, object id and method hash of each request, byte counts and refusals, and
  returns a summary to the host with the call.

### Rewriter (issue time)

- **R11.** Refuse unknown endpoint classes (RL-JGDMS-0085), socket factories that need
  downloaded code (RL-JGDMS-0089), JOSS carriers and opaque bytes (RL-JGDMS-0084,
  RL-JGDMS-0088). Optional hardening, not required: an address policy limiting ids to the
  host the service was authenticated from plus an operator allowlist. Without it, the only
  residue after R1, R3 and R8 is that the relay opens a TCP connection and starts a
  handshake to an address the service named.

### Host

- **R12.** The thin stub captures constraints set through `setConstraints`, uses them for
  the registration in R2, and also forwards them to the real proxy, so existing client code
  and proxy preparers work unchanged.
- **R13. Return path** (proposal). Return values and exceptions are accepted only if every
  class is local to the host. A returned smart proxy stays hosted in the sandbox. A relay
  endpoint in a reply is converted back only if its id was issued to that subprocess.
  *Amended 2026-11-21 (proposal), after reading the landed hand-off code:* exceptions are
  never reconstructed on the host. `SubProcessReconstructionServer` reports only the
  thrown class's name and its message, as text, and `SubProcessWireHandoffImpl` creates a
  fresh `RemoteException` (when the method declares one) or `RuntimeException` carrying
  that text; decode failures become the same. The rule above, "accepted only if every
  class is local to the host", is weaker than the code: it would admit a real
  `OutOfMemoryError` or `StackOverflowError` into a host thread. For exceptions this
  paragraph replaces it. Further:
  - the reported class name and message are the proxy's text. Control characters are
    stripped, the length is capped and the text is marked as the hosted proxy's report, so
    it cannot forge log lines or pass for the host's own error;
  - the hop from host to subprocess does not move to plain JERI without a host-side
    filter: `BasicInvocationHandler` rethrows any `Error` or `RuntimeException` it
    decodes, and the `ServerError` wrapping in `BasicInvocationDispatcher` would run in
    the untrusted JVM;
  - declared application exceptions (`TransactionException`, for example) also arrive as
    `RemoteException` or `RuntimeException` today. If they are ever restored, an `Error`
    is still never rebuilt.

### Launcher and proxy JVM

- **R14.** Empty network namespace (RL-JGDMS-0096), own uid, seccomp.
- **R15. Socket files** (proposal). An empty network namespace does not block path-based
  Unix sockets. Either the subprocess's mount namespace exposes only the relay's socket, or
  seccomp limits it to inherited descriptors. To be checked on Linux when a launcher
  exists; only `UnsupportedSubProcessLauncher` is in main today.
- **R16. No second thread of control and no fine clock** (proposal). Beyond denying thread
  creation and the common pool: management beans unavailable. The operator has already
  stated that no finalizer is run and that the coarsened clock adds no jitter. Open
  question for the operator: a single thread can interpolate between coarse clock edges
  by counting loop iterations, which jitter would blunt and coarsening alone does not.
  *Added 2026-11-21 (proposal):* coarsening lowers the bandwidth of a timing channel and
  does not close it. A cache-timing measurement needs no fine clock when the victim
  repeats itself: the proxy repeats its probe until the difference crosses a tick. What
  bounds the attack is the number of samples. The proxy runs only while a call is in
  progress (R16a: one dispatch thread, a deadline, a kill), so its measuring time is at
  most the calls it is given times the call deadline. That bound, and the number of
  samples an attack on the shared L3 needs on this board, are the inputs to the
  certificate-lifetime relation (CL-PROXYBOARD-0020) and are to be measured (open point
  11).
- **R16c. Capacity model** (design-session estimate, not measured). A hosted instance has
  one dispatch thread that spends most of each call blocked on the relay round trip, so
  an instance's queue fills on call latency (service round trip plus the relay's release
  tick) while its core is mostly idle. Expected order of limits on a six-core board:
  per-instance latency first; then memory, which bounds how many JVMs exist; then cores,
  which bound how many run at once; network connections last, because the relay
  multiplexes calls to a service over few TLS connections. Starting a JVM is itself
  CPU-heavy, so scale-out under load should be capped or pre-warmed. To be measured on
  the board.
- **R16d. Scaling rule** (RL-JGDMS-0103). A saturated single core calls for a second
  process on the same board; saturated network connections call for another proxy board.
  Design-session additions (proposals): a full per-instance queue with an idle core is
  treated like a saturated core (second process); exhausted board memory or all cores
  busy is treated like saturated network (another board). The host therefore needs, per
  board, queue depth and core use per instance, free memory, and the relay's connection
  and throughput figures, and it places new stubs using them. A stub stays on the board
  and instance it was given (R16b). Each board has its own node identity, relay,
  destination ids and distributed-GC leases, so the service sees each board as a
  separate node acting for the host.
- **R16a. Single dispatch thread** (RL-JGDMS-0100). Concurrent callers are queued and
  dispatched to the proxy by one thread. Design-session reading, to be confirmed: the
  queue is per subprocess, not per proxy object, because proxies sharing a pooled JVM
  would otherwise run concurrently with each other; and the same thread performs
  reconstruction and retirement in that JVM. Consequences (proposals):
  - the host or supervisor enforces it by keeping at most one invocation outstanding on
    the wire to a subprocess, so it does not rely on code inside the untrusted JVM;
  - one token is live per subprocess at a time, and the relay refuses any other (tightens
    R6);
  - every call has a deadline; on expiry the subprocess is killed and the queued callers
    fail, because a proxy that blocks or spins holds up everyone behind it;
  - the queue is bounded and callers beyond the bound fail at once.
- **R16b. Sharing and scale-out** (RL-JGDMS-0102). Clients on the host that use the same
  service may share one hosted proxy, and more instances may be created when one is
  saturated. This is safe for caller identity because Subjects never enter the proxy JVM
  and constraints are registered per call (R2). Proposals for how:
  - a client stub stays bound to the instance it was given; a new instance takes new
    stubs only. Smart proxies can hold client-side state (caches, sessions, cursors,
    leases), so moving a stub between independent copies could break them;
  - the trusted side keeps the rewritten proxy bytes so that it can reconstruct another
    instance in a new subprocess, with its own destination ids (R7) and relay state;
  - relay state and the hosted proxy are released when the last stub bound to an
    instance is retired;
  - the queue rotates between callers so one client cannot starve the rest;
  - saturation means queue depth from demand. A call that exceeds its deadline leads to
    a kill (R16a), never to another instance, and instances per service and in total are
    capped, so a proxy cannot make the board spawn JVMs by stalling;
  - instances of the same service run on different cores. Two copies of hostile code in
    separate processes can still signal each other through contention on shared
    hardware, at far lower resolution than a second thread in one JVM. This is a known
    residue, not closed.
- **R16e. Board memory** (RL-JGDMS-0104). Boards are specified with as much memory as
  the part allows. Design-session notes (proposals): the boards have no writable storage,
  so there is no swap and memory is a hard limit; each subprocess needs a memory limit so
  one proxy cannot take another's share or the relay's; and per-JVM cost should be
  measured and reduced (small fixed heap, a shared read-only class archive, few compiler
  and collector threads) before concluding how many instances a board holds.
  *Added 2026-11-21 (proposal), revised the same day:* the JIT stays on in proxy JVMs, but
  it never compiles or inlines proxy code (R16j). The JIT's risk is escape, not learning:
  a compile decision depends only on that JVM's own state, so it carries no other party's
  secret, while a miscompilation gives memory corruption that no permission stops. Trusted
  code is compiled: the JDK, JGDMS and the DER codec, which carries most of the
  marshalling cost. So that the compiler thread is not a second, concurrent reference for
  the proxy, compilation runs in the foreground (`-Xbatch`), with C1 only and one compiler
  thread, to be measured against C2.
  Trusted code may also be compiled ahead of time with Leyden (JEP 544, JDK 28), from a
  training run on a trusted build machine that loads no proxy code. The cache is hashed,
  part of the measured read-only image, and separate from the relay's (R16i). JEP 544 does
  not let the JIT be switched off, so the foreground setting still applies. Proxy code is
  never AOT-compiled: the training run would have to execute it, and the cache holds
  native code that nothing verifies, so a cache written by a compromised training JVM
  would compromise every run that maps it.
  The relay keeps its full JIT: HotSpot's hardware AES and GHASH routines are used by
  compiled code, not by the interpreter. HotSpot's code cache is writable and executable
  whatever is compiled, so no setting here allows a syscall filter that forbids executable
  mappings; only a Zero build might, untested.
- **R16f. Hardware** (RL-JGDMS-0105, RL-JGDMS-0107, RL-JGDMS-0108). Chosen processor: NXP i.MX 95
  (six Cortex-A55, no out-of-order cores, ECC memory, 16 GB the largest configuration
  found on sale; from an unchecked subagent report). The board has no swap device: swap
  was considered and rejected because page-fault delays would give proxies a timing
  source. Memory is therefore a hard limit (R16e).
- **R16g. No virtual machine** (RL-JGDMS-0108). The proxy JVMs and the relay run as
  processes on one kernel on the i.MX 95. A VM boundary and a mixed-core part with the
  proxies pinned to the in-order cores were considered and set aside in favour of the
  simpler arrangement. Consequence: the kernel is the single boundary between a proxy
  that has escaped its JVM and the relay, so the launcher controls (R14, R15), the
  syscall filter and board integrity (R20) carry that weight. The relay channel stays a
  Unix socket with peer credentials.
- **R16h. No second thread, enforced below the JVM** (proposal, added 2026-11-21). A
  thread that does nothing but count is a fine clock, and coarsening does not survive it.
  Every route to a second thread of control in the proxy JVM is closed:
  - the routes recorded as open in `docs/SUMMARY-proxy-board-design-2026-11-15.md` section
    5. The virtual-thread carrier factory creates threads with no check, because the new
    checks were limited to the common pool. The finalizer thread is removed only by a
    launcher flag the VM does not enforce, so the statement in R16 that no finalizer runs
    depends on the launcher;
  - after start-up, and before any proxy class is loaded, the JVM installs a syscall
    filter that refuses new threads: `clone` with `CLONE_THREAD`, and `clone3` outright,
    since a filter cannot read its flags. A JVM defect alone then does not give native
    code a counting thread. Threads the JVM creates lazily must be found first and either
    started before the filter or disabled (the attach listener, for example, by
    `-XX:+DisableAttachMechanism`);
  - each subprocess's unit has its own task limit (`TasksMax`, C4), set at the JVM's
    measured thread count. A per-uid process limit does not serve: every proxy JVM shares
    one uid (C3), so it would be one limit for the whole board.
- **R16i. No hardware state shared between the relay and the proxies beyond what the part
  forces** (proposal, added 2026-11-21). Each proxy core keeps its L1 and L2 to itself
  (R17); the cluster's L3, memory and clock are shared.
  - No physical page is mapped by both the relay and a proxy JVM. The relay runs its own
    copy of the JDK image and its own class archive, so the text of `libjvm` and the
    archive are not shared, and kernel same-page merging is off. Otherwise a proxy can
    tell which shared code the relay just ran by evicting and reloading those cache lines
    from Java. The archive shared among proxy JVMs (R16e, and the agreements under the
    conflicts section) stays: between colluding proxies it is one more covert channel of
    the kind R16b already accepts.
  - The processor runs at a fixed frequency, below the point where it throttles. The six
    cores are expected to share one clock domain, so one core's load would change the
    speed the others run at, and a proxy counting loops per tick would read the relay's
    activity from it.
  - Interrupts are routed to the relay's core, and the proxy cores are excluded from the
    scheduler tick where the kernel allows, so network activity does not take cycles from
    a proxy's core in a pattern the proxy can count.
  - R16f relies on the A55 not speculating in an exploitable way. To be checked against
    Arm's published list for the core revision in the i.MX 95.
- **R16j. Proxy code is never compiled** (operator decision 2026-11-21, not yet
  registered). HotSpot decides by the defining class loader, not by class name: a proxy
  chooses its own class names, and the name-based flags (`CompileOnly`, `CompileCommand`,
  compiler directives) are dodged by a class declared in a trusted package. The change is
  DirtyChai source and is written by the operator. Design-session proposals for it:
  - a flag on the class loader, set by trusted code through a permission-guarded internal
    API before the loader defines any class, and never cleared. Every
    `PreferredClassLoader` that holds downloaded code is marked;
  - the check reads the defining loader of the method's holder, so a proxy's hidden
    classes (its lambdas) and reflection proxies defined in a marked loader are covered
    without name patterns;
  - two choke points rather than a flag on each method: the check that admits a method to
    compilation (ordinary and on-stack replacement, every tier), and the inlining decision
    in both compilers. A marked method is then never compiled and never inlined into
    trusted compiled code, which calls it through the interpreter;
  - residues: trusted compiled code still speculates on proxy receiver types and on
    class-hierarchy facts, and the JDK's method-handle classes (`LambdaForm`) are compiled
    with shapes a proxy can drive. Whether proxies may use method handles at all is a
    question for admission (R18) or policy;
  - tests (AI-authored under DirtyChai's Zone T, if the operator wants them): methods of a
    marked loader, including inner classes, lambdas and reflection proxies, stay
    interpreted after any number of calls and never appear in the inlining log of a hot
    trusted caller; the flag cannot be set without the permission or after the loader has
    defined a class. Each test is shown to fail with the check disabled.
- **R16k. Exhaustion inside trusted code fails closed** (proposal, added 2026-11-21). A
  proxy with no permissions can fill its heap, or its stack to the frame, and then call
  into JDK, DirtyChai or JGDMS code, so that an `OutOfMemoryError` or `StackOverflowError`
  fires part way through trusted code. A permission check, policy evaluation or class
  definition interrupted that way denies, never grants (DirtyChai HC-2, P6). Test: drive
  each permission-check and policy path with the stack nearly exhausted and the heap
  nearly full, and assert a denial.
- **R17. Resource limits** (proposal). Memory, CPU and thread-count limits per subprocess;
  the proxy JVM pinned to its own core; the relay given reserved memory and CPU so the
  proxy cannot starve it into a watchdog reset.

### Admission, supervision, board

- **R18. Static admission scan** (proposal) of downloaded jars with a default-deny
  allowlist of the JDK surface. It can find references to forbidden APIs, non-standard
  `invokedynamic` bootstraps, `META-INF/services` entries, native libraries, agent or
  `Class-Path` manifests, classes in protected packages, and size violations. It cannot
  find reflection on computed strings, permitted library calls that do a forbidden thing
  internally, timing loops, triggers for JIT defects, false results or encrypted
  exfiltration. It is admission control; the policy and the operating system enforce.
- **R19. Supervisor** (proposal). Reads policy denials, seccomp kills, resource counters
  and exit status; on the first denial it kills the subprocess and blocklists the codebase
  digest.
- **R20. Board integrity** (proposal). A JVM defect followed by a kernel defect gives the
  whole board. Read-only image with periodic reboot or measured boot, and the node key in
  a signer the relay cannot export from, limit what that yields and how long it lasts.
  *Added 2026-11-21 (proposals):*
  - The node key is an elliptic-curve key, or is held in the signer, so that a service
    timing the relay's handshakes is not timing an operation that leaks; the JDK's RSA
    uses `BigInteger`, which is not constant-time. The i.MX 95's on-chip secure enclave is
    a candidate for the signer (open point 3).
  - Linux on arm64 lets user space read the generic timer's counter by default (for the
    vDSO). Its resolution is typically tens of nanoseconds, so a JVM escape alone would
    give a fine timer. The board kernel denies user space that access, with
    `clock_gettime` falling back to a system call that can be coarsened or refused, and
    the syscall filter refuses `perf_event_open`. A fine timer then needs a JVM defect and
    a kernel defect. To be confirmed against the board kernel.

## Open points for the operator

1. **Queue details** (the concurrency question itself is settled by RL-JGDMS-0100):
   whether the queue is per subprocess as R16a reads it; the call deadline and queue
   bound; whether blocking calls (long waits for events or leases) need more subprocesses
   for the same service so one waiter does not stall other callers.
2. Whether the address policy in R11 is wanted.
3. Where the signer lives (carried over).
4. Sharing boundary. A shared proxy sees every sharer's arguments and can keep them, so
   it could hand one client's data to another or issue one client's request during
   another's call. Proposal awaiting a ruling: share only within a trust domain, with the
   pooling key made of the service principals, the codebase digest and a domain label the
   host supplies, defaulting to one domain per host process.
5. Who fetches the codebase for the proxy JVM, and under what destination rules.

Added 2026-11-21 (open points 6 to 9 are in the conflicts section below):

10. **Connections across trust domains.** R16c has the relay multiplex calls to one
    service over few TLS connections. A proxy's call latency then depends on other
    proxies' traffic to that service, including other trust domains'. The tick quantises
    this; repetition over many calls still reads it. Whether the relay keeps separate
    connections per trust domain (open point 4), at some cost to R16c's capacity.
11. **Measurement on the board.** The samples an attack on the shared L3 needs under the
    call-time bound in R16; the cost of foreground C1 compilation against C2 (R16e); the
    threads the JVM creates after start-up (R16h).

## Conflicts with the landed isolation code and the two launcher documents

Added 2026-11-16 after reading `docs/SOW-Smart-Proxy-Isolation-Real-Launcher.md` and
`docs/SOW-SubProcessLauncher-Implementation.md`, which arrived by merge after the
requirements above were written. The design session has read those two documents, not the
code they describe, so each item is "the document says", to be checked against the source.
Every item needs an operator ruling; none is settled here. Until they are settled, no
work order will touch the launcher, the subprocess pool or the hand-off.

- **C1. The subprocess is given a TLS listener and an identity.** The Real-Launcher
  document (section 5.2, recorded there as the operator's ruling) exports the
  subprocess's policy administration object over `net.jini.jeri.ssl` with SPIFFE
  authentication, because the Unix-socket transport cannot satisfy `Integrity.YES`. That
  puts a network listener and a private key or SVID inside the JVM that hosts the
  untrusted proxy. It conflicts with RL-JGDMS-0096 (the Unix socket to the relay is the
  only channel) and with keeping keys out of that JVM. Ways to reconcile, for the
  operator to choose: (a) the administration object is exported by the relay or a
  supervisor on the subprocess's behalf and reaches the subprocess over a local channel;
  (b) grants are delivered over the existing hand-off channel, authenticated by how the
  channel was created rather than by TLS; (c) RL-JGDMS-0096 is narrowed to allow one
  loopback listener, which reintroduces a key in the untrusted JVM and is the weakest.
  **Ruled 2026-11-16 (RL-JGDMS-0109):** the TLS export was decided before the key was
  removed from the proxy JVM; the key stays out, so (c) is excluded. Which of (a) or (b)
  carries the administration calls is not yet ruled. Design-session recommendation: (a),
  with the relay as the TLS end. The relay already authenticates the host, so it can
  enforce the same administration constraints (integrity, client and server
  authentication, the administrator principal) and pass the grant to the subprocess over
  a local channel that only the relay can open. The existing constraint check then moves
  to the relay unchanged, instead of being re-invented for the hand-off channel.
- **C2. The subprocess downloads the codebase itself.** The same document says
  `SubProcessReconstructionServer.reconstruct()` calls
  `PreferredProxyCodebaseProvider.resolve()` in the subprocess, which fetches jars over
  HTTP. With no network in the subprocess that cannot work. This is open point 5 made
  concrete: a trusted process must fetch, hash and hand over the jars.
- **C3. User ids.** Both documents cite a standing ruling of 2026-10-27, "two uids, no
  per-principal uid": one uid for the local side and one shared by every proxy JVM. R14
  says "own uid" and R7 binds a destination id to a subprocess by peer credentials. With
  one shared uid, peer credentials (user and group) cannot tell two subprocesses apart,
  and processes with the same uid can by default inspect and signal each other.
  Proposal: read R14 as "the proxy uid"; give each subprocess its own relay socket
  (inherited, or the only socket visible in its mount namespace) so that the connection
  itself identifies the subprocess for R7; and add a process-id namespace and a ptrace
  restriction so that subprocesses sharing the uid cannot see each other.
- **C4. Spawn mechanism.** The two documents disagree (bare `ProcessBuilder` against a
  `systemd-run` wrapper) and leave it to the operator. R14, R15 and R17 need a network
  namespace, a mount namespace, a syscall filter and resource limits. A bare
  `ProcessBuilder` gives none of these, so this design needs some wrapper or helper that
  applies them before the JVM starts. Which one is the operator's decision; the board
  image decides what is available.
- **C5. Pooling key.** The landed pool keeps one subprocess per remote SPIFFE principal.
  R16b (more instances when saturated) and open point 4 (sharing only within a trust
  domain) both need more in the key: a domain label and an instance number.
- **C6. Peer authentication of the hand-off channel** is still open in the Real-Launcher
  document (its question 1). Whatever is chosen there should be the same mechanism that
  identifies the subprocess to the relay in C3, so that there is one answer to "which
  subprocess is on this socket", not two.
- **Agreements, no action needed.** A class-data archive shared read-only between
  subprocesses, owned by a trusted uid, mode 0444 and loaded with `-Xshare:on`, fits R16e.
  Excluding checkpoint and restore fits the no-reuse rule. Detecting process death from
  the operating system and not from a closed connection fits the deadline kill in R16a.
  A ceiling on live subprocesses fits the caps in R16b.

Open points added by this section: 6 (C1), 7 (C3), 8 (C4), 9 (C5). Open point 5 is now C2.

### Rulings on the conflicts, 2026-11-16 21:06

- **C2** (RL-JGDMS-0110): the subprocess cannot download the codebase. Still open: which
  trusted process fetches it. Design-session proposal: a separate fetcher process on the
  board with no key, not the relay, so that the process holding the node key never parses
  HTTP replies from an address the service chose. It fetches, hashes, and hands the jars
  to the launcher, which exposes them read-only to the subprocess.
- **C3** (RL-JGDMS-0111): as proposed above. One proxy uid, one relay socket per
  subprocess, process-id namespace, ptrace restriction.
- **C4** (RL-JGDMS-0112): a `systemd-run` wrapper. Consequences: the board image needs
  systemd; this is new code (nothing in the repository uses it today); and of the two
  launcher documents, `SOW-SubProcessLauncher-Implementation.md` is the one whose
  mechanism was chosen, less its per-launch uid (C3).
- **C5** (RL-JGDMS-0113): the smart proxy JVM does not use SPIFFE. With C1 this means the
  subprocess holds no identity of its own at all: the relay presents the node identity to
  services and to the host. The pooling key is not settled by this. Design-session
  reading: the key may still be built from the *service's* authenticated principal,
  which is a value the trusted side holds and not a credential in the proxy JVM; the
  domain label and instance number of open points 4 and R16b are still wanted.

Still open after these rulings: who fetches the codebase (C2); the pooling key (C5).

- **C1, second ruling** (RL-JGDMS-0115): the relay JVM manages the subprocess's policy,
  as a dynamic policy delivered over the Unix socket to the subprocess. Design-session
  notes: the administrator is authenticated at the relay, which is where the TLS
  connection from the host ends; the socket to the subprocess is trusted because of how
  it was created (C3, C6), so the fixed `Integrity.YES` and authentication constraints
  of `SubProcessPolicyAdmin` cannot be what protects that last hop, since the Unix-socket
  transport does not support them. How the existing administration classes are re-used
  is to be worked out against `SOW-SubProcessDynamicPolicy.md` (first half read by the
  design session on 2026-11-16).
- **Constraints at different levels** (RL-JGDMS-0116). Design-session reading: the
  administration constraints (integrity, client and server authentication, the
  administrator principal) are met on the TLS connection that ends at the relay; the hop
  from relay to subprocess is protected by the socket. That SOW's finding S1 says
  authority must be proven by an enforced mechanism and not by which channel a call
  arrived on. To keep that true here (proposals): the policy socket is separate from the
  hand-off (business) socket; only the relay holds its other end; the subprocess accepts
  grants on that socket and nowhere else; and the object answering on it is never the
  hosted proxy. The subprocess still refuses to host a proxy whose interfaces include
  the administration interfaces, as that SOW already requires.

- **C6** (RL-JGDMS-0114): as proposed. The hand-off channel is authenticated by the same
  per-subprocess socket arrangement as C3. This answers question 1 of the Real-Launcher
  document; that document has not been edited to say so.
