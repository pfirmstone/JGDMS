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
code. Except R16j's decision itself (RL-DIRTYCHAI-0016), all are proposals; none is
registered, and nothing in them has been measured.

Addition of 2026-11-22, same session: the section "Case topology" at the end, from the
operator's statements in conversation (wording preserved there, not registered) and the
session's proposals.

Revision of 2026-11-22, later the same day: the operator replaced RL-JGDMS-0037 and set
how a proxy JVM reaches the relay (RL-JGDMS-0211, -0212). The trust-boundary note, R16g,
K3 and K7 are amended to match, and open points 16 and 17 are added.

Second revision, 2026-11-22 evening: the operator dropped encryption on the internal links
(RL-JGDMS-0213) and chose slot ID pins on the caddy backplane (RL-JGDMS-0214). K1 to K4
are rewritten around one VLAN per slot, K7 notes what that replaces, K8 (hot swap) and
open points 18 to 20 are added, and open point 16 is closed.

Late evening, 2026-11-22: the operator reconsidered and kept R16j (RL-DIRTYCHAI-0016
reaffirmed); a dated note in R16j records why, and K9 (proxy JVM crashes reported into
SCAP) is added as a proposal.

Revision of 2026-11-23: the operator replaced the caddies and row switches with twelve
vertical cards of two modules on a backplane, a network controller port per module and one
PCIe cable to the relay (RL-JGDMS-0215 to -0217), set the cooling (RL-JGDMS-0218) and the
module requirements (RL-JGDMS-0219). K1 to K4 and K8 are rewritten, K10 and K11 added,
open points 18 and 19 closed and 22 to 26 added. K12 records the candidate network
controller. RL-JGDMS-0220 (modules on end) is in K1
and K10; a 3U case is the target (K1, open point 27).

Revision of 2026-11-23, early morning: the operator moved the network modules onto a
passive carrier on the right-hand wall with one MCIO cable per module and the LAN leads
routed behind it to the backplane (RL-JGDMS-0223, replacing -0221 and -0222), ruled out
activity LEDs (RL-JGDMS-0224), set three fans in series in the relay duct and front and
rear fans for the card bank, with speeds allowed to follow inlet temperature in coarse
steps (RL-JGDMS-0225, replacing -0218), and put the controller on its own board beside the
supplies with no wiring to the relay side (RL-JGDMS-0226). K1 to K4, K7, K8 and K10 to K12
are revised, K13 (the MSI D3061 as candidate relay board) and K14 (the controller) added,
open points 13, 22, 24, 26 and 27 revised and 28 to 30 added.

Revision of 2026-11-23, midday: the operator moved the relay's outward network off the
relay board's OCP 3.0 slot onto a NIC on its own MCIO connector (RL-JGDMS-0227), kept the
custom network carrier for production with off-the-shelf MCIO-to-M.2 adapters for the
prototype bench (RL-JGDMS-0228), and ruled that no MCIO cable carries SMBus
(RL-JGDMS-0229). K1, K2, K4, K12 and K13 are revised, K15 (relay management, the outward
NIC and the MCIO cables) is added, open point 26 is revised and 31 to 33 are added. Facts
read from the D3061 user guide, the XIC 0582 brief and the Innodisk and Intel pages are
CL-RELAYCASE-0032 to -0044.


## Trust boundaries

| Party | Trusted | Holds |
|---|---|---|
| Host (client application) | yes | caller Subjects, client constraints, the thin stub |
| Relay (on the proxy board; since 2026-11-22 its own board in the case, K1) | yes | TLS connections, destination table, tokens, minimum constraints |
| Signer (optional) | yes | node key |
| Proxy JVM (on the proxy board) | no | downloaded proxy code, marshalling, the arguments the host passes in; since 2026-11-22 a plain TCP connection to the relay on its slot's own controller port; no key (K3) |
| Service | authenticated, not trusted | wrote the proxy bytes, the codebase and every endpoint in them |

The proxy JVM's only channel out is its connection to the relay: a Unix socket while the
relay shared the board (RL-JGDMS-0096), and since 2026-11-22, with the relay on its own
board, an unencrypted TCP connection over the case's private network, identified by the
slot's own network controller port and the JVM's address (RL-JGDMS-0211, -0217; K3). Real
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
  *Note 2026-11-23:* 16 GB modules confirmed from several vendors (CL-RELAYCASE-0027).
- **R16g. No virtual machine** (RL-JGDMS-0108). The proxy JVMs and the relay run as
  processes on one kernel on the i.MX 95. A VM boundary and a mixed-core part with the
  proxies pinned to the in-order cores were considered and set aside in favour of the
  simpler arrangement. Consequence: the kernel is the single boundary between a proxy
  that has escaped its JVM and the relay, so the launcher controls (R14, R15), the
  syscall filter and board integrity (R20) carry that weight. The relay channel stays a
  Unix socket with peer credentials.
  (2026-11-22: replaced by the case topology; see K3 and K7.)
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
- **R16j. Proxy code is never compiled** (operator decision,
  RL-DIRTYCHAI-0016). HotSpot decides by the defining class loader, not by class name: a proxy
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
  *Reaffirmed 2026-11-22 (operator: "Ok, sounds like we need the permission check.").* The
  operator asked whether the restriction was needed, since the case topology limits what a
  JVM escape reaches (K3). It stays: the proxy writes the bytecode a JIT bug needs, and C2
  and C1 have both had such bugs recently, one fixed in October 2026 that affects JDK 27
  (CL-RELAYCASE-0021). Pack200 normalisation preserves what the bytecode does, so a
  compiler-hostile shape survives it (CL-RELAYCASE-0022), and SCAP's analysis does not
  look for such shapes (CL-RELAYCASE-0023); neither replaces R16j. SCAP's crash signal
  complements it (K9).
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

## Case topology (2026-11-22)

Proposed: the relay replaces the trusted host inside the rack case drawn in "Proxy host
board layouts" (sheets of 2026-11-15, variant V2), and the hosts move to racks of their
own. The case then holds everything that runs other people's code and the one component
that speaks for it; nothing from a proxy board leaves the case. Operator statements are
quoted below; the firm ones are RL-JGDMS-0208, -0209, -0211, -0214, -0215, -0217, -0219,
-0220 and -0223 to -0229 (-0210, -0212, -0213, -0216, -0218, -0221 and -0222 were ruled
and later replaced); everything else is a proposal of this session.

Operator statements, 2026-11-22:

- "the design should be the relay and the proxy boards, in one rack case. The hosts can
  then have their own rack cases."
- "There's one relay and 24 proxy boards"
- "The relay could have it's own spiffe server and manage the certs for the proxy boards,
  these wouldn't have any permission outside the relay."
- "The network boot images could also be stored on the relay."
- "There may be multiple jvms running on each board, holding proxy's for the same server
  certificate domain, I think we need a Spiffe cert per jvm." and "the certs can be stored
  in the trust zone"
- "The JVM still holds no key, that should be in the TrustZone, however a Unix socket is
  insufficient for communication with the relay as it's no longer collocated on the board,
  but must be contacted via the private network."
- "the caddy backplane giving each slot a fixed ID is better."
- "Yes, drop encryption on the internal links"

- **K1. Layout** (revised 2026-11-23; RL-JGDMS-0215, -0223). The rear single-socket server
  board of variant V2 remains the relay. The case holds twelve vertical cards, loaded from
  the top (the case slides out on rails and the lid opens) into a flat backplane on the
  case floor. Each card carries two SMARC i.MX 95 modules mounted directly on it, one on
  each face: the front module on one face near the front of the card, the rear module on
  the other face near the rear, so their connectors are never back to back. A "proxy
  board" elsewhere in this document is now one such module with its share of its card. The
  caddies, row boards and row switches of the earlier layouts are gone; the 2026-11-15 and
  2026-11-22 drawings in the repository show them.
  - Orientation (RL-JGDMS-0220): each module stands with its 82 mm side vertical, so the
    air crosses its 50 mm side: a shorter path through the fins and a larger face to the
    airflow. The card is then about 120 to 140 mm front to rear and 100 to 110 mm high
    (estimates).
  - Case height: 3U is the target (operator, 2026-11-23: "This would fit in a 3U case,
    which is more compact than the original 4U."; not registered as a ruling). Estimated
    stack from the floor: standoffs, backplane, connector and a card of about 100 mm,
    roughly 110 mm against about 125 mm inside 3U; front fans of 80 or 92 mm; the relay's
    CPU heatsink and full-height cards must fit too (open point 27).
  - Relay board and supplies (operator, 2026-11-23, in this document's terms: the relay
    board need not be large; the two hot-swap supplies go on the left side viewed from the
    front; not registered as a ruling). Candidate relay board: the MSI D3061 (K13), a
    DC-MHS board of 305.59 x 210 mm at the rear right, its CPU in the relay duct. The
    host-facing and service-facing ports are on an outward NIC on its own MCIO connector
    and CPU root port, its RJ45s on its own bracket at the rear of the relay zone; the
    board's OCP 3.0 slot stays empty (RL-JGDMS-0227, K15). The BMC port is on the board's
    DC-SCM module (K2, K13, K15). The two hot-swap CRPS supplies sit at the rear left,
    feed the case through the relay board's power distribution board, pull out from the
    rear and breathe card-bank exhaust. The controller board (K14) is on a panel beside
    them.
  - Network carrier (RL-JGDMS-0223, replacing the network card with its PCIe switch of
    RL-JGDMS-0222 and the single 8-lane link of RL-JGDMS-0221; kept for production by
    RL-JGDMS-0228). The six network modules (K12) stand on a passive carrier board on
    standoffs on the inside of the right-hand chassis wall, at the front of the relay
    duct, upstream of the CPU heatsink and away from the card bank's heat. The carrier has
    six M.2 sockets, six MCIO connectors on its rear edge, one per socket, a 3.3 V
    regulator, a power input and the status LED driver; no PCIe switch, no edge connector,
    no Ethernet signals and no SMBus to the sockets (RL-JGDMS-0229). Six MCIO cables, one
    per module and built without SMBus (K15), lie flat along the right-hand wall to six of
    the relay board's eight CPU-fed MCIO connectors (CL-RELAYCASE-0030), so each module
    has a PCIe root port of its own; each link trains at x2. Two cable lengths serve the
    board's near-side and far-side connectors. Off-the-shelf MCIO-to-dual-M.2 adapters
    (XIC 0582, CL-RELAYCASE-0042) were weighed instead: three of them need about 130 mm of
    duct width beside the card bank, which only a card pitch near 22 mm leaves
    (CL-RELAYCASE-0044), and any placed beside the relay board would crowd its memory
    (CL-RELAYCASE-0037). They carry the modules on the prototype bench (K15).
  - LAN leads (RL-JGDMS-0223). The carrier has a cut-out under each module's underside LAN
    connector, so its lead mates from behind, and a slot beside each module for the lead
    from its top-face connector (proposal). All leads run in the gap between the carrier
    and the wall, out of the airflow, then flat along the duct floor under a cover strip,
    and under the divider wall to connectors along the backplane's right-hand edge. Where
    they pass, the divider ends in a hemmed, folded flange that fixes the gap at about 1
    mm more than a lead, so the metal never presses on one, with soft closed-cell foam
    (low compression set, rated V-0 or HF-1) bonded to the flange closing round each lead
    and keeping the duct sealed. The leads are one length, clamped flat on both sides of
    the divider and numbered at both ends. If each module connector carries two ports
    (CL-RELAYCASE-0029), there are twelve leads, one per card, each with its front and
    rear module's ports. The leads end at the backplane, not the cards, so a card swap
    never moves them.
  - Card: for each module, its connector, a write-protected SPI boot flash, the
    boot-select straps and its power regulation, laid out once and placed twice, mirrored;
    and one strap per module position (front 0, rear 1). Only Ethernet pairs, power and
    slow control lines cross the card's backplane connector; no PCIe or USB does, so a
    taken module never reaches anything that can write into memory (proposal).
  - Backplane: per card connector, the fixed slot ID pins (four bits for twelve cards,
    five with the card strap, for 24 modules; RL-JGDMS-0214); per module the power switch
    (an eFuse per slot, K8), watchdog, presence, console, boot-select and recovery lines,
    brought to firmware-free I/O and UART expander chips (K14); the fan controller chips;
    the boot-flash write-protect gate; and the LAN lead connectors along its right-hand
    edge, routed to each card's connector. It carries no PCIe, no switch and no firmware
    (RL-JGDMS-0223, -0226): only Ethernet pairs, power and slow control lines, so it
    covers only the card connectors and the strip along its edge. Each module's PCIe 2.1
    x2 link carries its four gigabit ports with headroom.
  - Front panel (proposal; RL-JGDMS-0224 rules out activity LEDs): a status LED per module
    (24), driven by the relay through the carrier's LED driver, showing booting, running,
    fault or crashed; and a safe-to-remove LED per card (12), driven by the controller
    through the backplane expanders, lit once both of the card's modules are off. Both
    show through light pipes in the fan wall. They change only on events the relay or the
    controller decides, never on traffic. The network modules' LED connectors are
    unconnected.
  - Depth: about 550 to 600 mm with the D3061 behind the carrier (estimate, open point
    27), so a deep four-post rack and rails to suit.
  - Cards are keyed so they cannot go in reversed, and a retention bar under the lid holds
    them seated. The controller (K14) keeps per-module power, reset, presence and boot
    select and reads a lid intrusion switch (K8). The egress network is removed from the
    node side: only the relay faces services.
- **K2. Ports leaving the case.** The relay has host-facing and service-facing ports on
  separate physical interfaces, so no service reaches the host network through the case;
  the service side sits behind the egress allowlist (S2). The controller's management port
  and the relay's BMC are on a third network that no module reaches. Inside: seven MCIO
  cables, six from the relay board to the network carrier and one to the outward NIC, all
  without SMBus (RL-JGDMS-0229); no PCIe to the backplane, and no Ethernet uplinks
  (revised 2026-11-23 midday). The relay's ports are on the outward NIC on its own MCIO
  connector, and the OCP 3.0 slot, whose NC-SI bus reaches the BMC (CL-RELAYCASE-0034),
  stays empty, so the BMC has no path to them (RL-JGDMS-0227, K15); the controller's port
  leaves at the rear, physically apart from the relay's (K14).
- **K3. Slot identity inside the case** (RL-JGDMS-0217, -0223; replaces the VLAN design of
  2026-11-22 evening). The internal links are not encrypted. Each module's Ethernet port
  is wired, through the backplane, to one port of one network controller on the network
  carrier, by a numbered lead, and there is no switch, so no module's frames reach another
  module and the relay knows the slot from the interface a frame arrives on, fixed by
  wiring. The switch configuration the VLAN design trusted is gone, and CL-RELAYCASE-0018
  and -0019 no longer bear on the design. Proposals below except where a ruling is cited.
  - Addresses: slot N has a fixed subnet (for example 10.0.N.0/28, relay at .1). The
    module's bootloader derives its address from the slot pins (K4); the launcher gives
    each proxy JVM a network namespace and an address from that subnet. The relay binds
    tokens (R6), destination ids (R7) and host dispatch to the pair (interface, address),
    drops anything on interface N from outside slot N's subnet, and never keys its state
    on an address alone.
  - No key, no TLS and no SPIFFE identity in the proxy JVM: RL-JGDMS-0113 and -0211 stand.
    The JVM's one socket is a TCP connection to the relay; the module's packet filter,
    matched on the subprocess's cgroup, allows only the relay's address and port, and the
    syscall filter (R14) refuses `listen` and `accept`. There is no SPIRE server inside
    the case and no TrustZone or `/dev/tee0` access for proxies.
  - What this rests on: the backplane wiring, the numbered leads and MCIO cables, and the
    controller; the network controllers, whose configuration memory is read and checked
    before use and whose management interfaces (NC-SI, SMBus) are left unconnected; and
    the relay's IOMMU, kept on, with each network module on a CPU root port of its own
    (RL-JGDMS-0223, CL-RELAYCASE-0030), so each controller's memory access is kept apart
    with no switch to trust; each module is confirmed in an IOMMU group of its own when a
    board is first booted. A taken module can read and forge only its own slot's traffic
    and appear only as its own slot.
- **K4. Boot** (revised 2026-11-23). The boot images are on the relay, signed on the build
  machine with a key that never reaches the relay, carry no secrets, and are protected
  against rollback (B5). The i.MX 95 boot ROM cannot boot over Ethernet
  (CL-RELAYCASE-0010), so each module boots from the write-protected SPI flash on its
  card, chosen by the SMARC boot-select pins, which the controller drives (RL-JGDMS-0219,
  K11).
  - The bootloader reads the slot pins (RL-JGDMS-0214; CL-RELAYCASE-0020), sets its own
    address and the relay's from them, fetches the one image every slot shares over TFTP
    or HTTP, checks its signature and rollback version before running it, and passes the
    slot number and address to the kernel on its command line. No DHCP or BOOTP server is
    needed.
  - The relay serves the images from a minimal, read-only static-file process on each
    slot's interface, with no access to the node key or to slot state. It serves only the
    address that matches the interface; a mismatch means a miswired or tampered module,
    and it refuses and reports it to the management station, which tells the controller
    (K14; the two share no wiring). A swapped MCIO cable or LAN lead shows up the same
    way, because it moves a module to another interface; a swap between a module's cable
    and the outward NIC's is caught earlier, before any interface comes up, by the
    root-port check (K15). It checks the signature and rollback version of every image it
    receives before staging it.
  - The pins are trusted only by the signed bootloader at boot. Afterwards a taken module
    can change its address, and the relay relies on the interface, not the pins. No module
    boots while the relay is down (open point 14).
- **K5. Call path.** Host calls reach a proxy only through the relay, which delivers them;
  the proxy can answer the call it was given and call its own service through ids the
  relay issued, and can start nothing towards the host. The relay applies the tick of R8
  to host dispatches and service replies alike. A call making k service round trips costs
  about k + 1 ticks plus network time, which R16c should include when the tick is chosen.
- **K6. Capacity.** Connections to services number about services x trust domains x
  ceil(concurrent calls / sessions per connection), independent of the number of boards;
  they are kept long-lived, with TLS 1.3 resumption, because each full handshake costs a
  signature from the node-key device. Relay CPU per call is expected to be a small
  fraction of proxy CPU per call (the proxies marshal; the relay forwards, checks and
  encrypts): to be measured on the prototype, which decides how many boards one relay
  carries. Scaling (R16d): a saturated core calls for another board, saturated connections
  for another case.
- **K7. What this supersedes.** R16g (one kernel on one part, relay and proxies together):
  the relay is separate hardware, and an escaped proxy must cross a board, not a kernel,
  to reach it. R16i between relay and proxies: no cache, memory, clock domain or page is
  shared (R16i still applies among proxies on a board). C3 and C6: per K3. Unchanged: R14,
  R15 and R16h within each board; the residue among proxies, now including contention in a
  row's switch and uplink and in the relay, quantised by the tick.
  RL-JGDMS-0037 (each board wired to its host and to its service) is replaced by the
  operator (2026-11-22); its rule that the board never calls the host and that no
  call-back listeners are used carries on as K5, a proposal not separately registered.
  Revised 2026-11-22 evening: the mutual TLS of RL-JGDMS-0210 and -0212, the relay's SPIRE
  server for the case, the per-JVM certificates and MAC-stealing detection are replaced by
  K3's slot VLANs (RL-JGDMS-0213).
  Revised 2026-11-23: the slot VLANs and row switches are in turn replaced by a network
  controller port per module (RL-JGDMS-0216, -0217), and the caddies by cards
  (RL-JGDMS-0215).
  Revised 2026-11-23 early morning: the network card's PCIe switch and single link are
  replaced by a passive carrier with one MCIO cable per module (RL-JGDMS-0223).
- **K8. Hot swap** (revised 2026-11-23 early morning, proposal). The slot, not the module,
  carries the identity, and a card carries two slots, so a card is the unit swapped.
  - Sequence: the operator, through the management station, has the relay drain both slots
    of the card (the host sees calls in flight fail and fetches those proxies again
    elsewhere, open point 14) and tells the controller a swap is expected; the controller
    cuts both modules' power and lights the card's safe-to-remove LED; the case slides out
    on its rails and the lid opens; the card is swapped; the lid closes; the controller
    powers the slots, and the modules boot as in K4. The relay and the controller share no
    wiring (RL-JGDMS-0226), so the management station is the only path between them. Only
    Ethernet, power and slow lines cross the card connector, so a swap never touches PCIe.
  - Electrical: an eFuse per slot on the backplane limits inrush and isolates a short; the
    card connector has staggered pins, ground and power first, presence last on insertion
    and first on removal, so a module is never powered half-seated. The backplane, the
    carrier and the controller are fed from the relay board's power distribution board,
    which now supplies the whole case (open point 29); the controller runs from the
    supplies' 12 V standby rail, so it is up before any module and stays up while they are
    off.
  - On any presence change or link loss on a slot's interface the relay discards
    everything bound to that slot (tokens, destination ids, sessions, calls in flight) and
    starts it afresh on link-up. A module reset is treated the same way.
  - Lid switch: the controller logs and alerts on every opening, and the relay refuses to
    start a newly powered slot unless a swap was expected (set through the management
    station). Opening the lid is the only way to reach a module, and the only state in
    which the cards' boot flash can be written (K14).
  - The chassis needs rails and a cable-management arm for the relay's external cables and
    power; the internal cables move with the case. The SMARC connector is meant for a
    module fitted once, so modules are replaced on failure, not routinely.
  - Optional: attestation once per insertion, with a key in the module's EdgeLock enclave;
    traffic afterwards stays unencrypted. It catches a substituted module, not a tap.
- **K9. Proxy JVM crashes are reported into SCAP** (proposal, 2026-11-22 late evening). A
  JIT exploit that misfires usually crashes its JVM, and an attacker probing an unfamiliar
  build needs attempts. JGDMS-STD-002 already condemns a codebase whose analysis JVM exits
  abnormally; the boards feed the same signal, so each failed attempt burns the digests
  involved.
  - The board's launcher reports any abnormal exit of a proxy JVM (signal, VM error,
    watchdog kill) to the relay over the slot's link, with the digests of every jar that
    JVM had loaded.
  - The relay, not the board, files a `CrashReport` with the Verdict Registry (STD-002
    Host 3), naming the digests and the service whose proxies they were, since a rebuilt
    jar has a new digest; repeated crashes from one service can then be acted on. The
    board never talks to the registry.
  - The crash also counts as a K8 event for the slot: the relay drops the slot's state and
    the controller re-images it before it hosts proxies again. Other proxy JVMs on that
    board were exposed to whatever caused the crash, so they go with it.
  - Limit: an exploit tuned offline against the published DirtyChai build need not crash.
    K9 is a tripwire; R16j is the barrier.
- **K10. Cooling** (RL-JGDMS-0225, replacing -0218; details are proposals). Two sealed air
  zones run front to rear, with separate rear exhausts.
  - Fans and control: filtered fans at the front for both zones, split so no fan feeds
    both. The controller owns every case fan (RL-JGDMS-0226), reads every tachometer and
    alarms on a failure. Speeds never follow load; they may follow inlet air temperature
    only, in a few coarse steps with hysteresis (RL-JGDMS-0225), because the room's
    temperature says nothing about the workload, and a fan failure may step the remaining
    fans up for the same reason. The relay board's BMC does not drive the case fans: its
    usual control follows CPU temperature, which follows load. Filters are
    front-serviceable without lifting the lid.
  - Card bank: each card carries a duct on the face with its rear module. At the front the
    duct runs close to the card, leaving room for the next card's front module; at the
    rear it steps out to shroud its own rear module. Each gap between cards so holds two
    channels, every module breathes fresh air, and the staggered modules let the cards
    nest at about one module stack plus the narrow channel per pitch. The end positions
    use a fixed baffle on the side wall. Ducts are grounded and insulated on the face
    towards the neighbouring card.
    Pull fans at the rear of the cards and the filtered front fans are two stages in
    series, the front slightly stronger (RL-JGDMS-0225), so the bank sits just above
    outside pressure and a gap leaks clean air out rather than drawing dust in; the lid,
    the card openings and the retention bar are gasketed.
  - Relay zone: the free width beside the cards is a sealed duct with three fans in series
    (RL-JGDMS-0225): a filtered front fan; the network carrier on the right-hand wall; a
    92 mm middle fan centred on and ducted to the relay CPU's heatsink; the CPU, its
    memory and the board; and one large rear fan (120 mm if the 3U height allows,
    otherwise a thick 92 mm). With one fan per stage there is no parallel path, so a
    stopped fan cannot let air flow backwards and no flaps are needed: with any one
    stopped, the other two move air through it. The front stage is the strongest, so the
    duct stays just above outside pressure. With any one fan stopped, at 35 degrees C
    inlet and full load, the CPU stays below its throttle point; if it nears it, the BMC
    alarms and the relay is drained and shut down cleanly rather than left to throttle
    (K13). The MCIO cables and the carrier's power lead pass the middle fan along the
    right-hand wall in a channel sealed like the divider slot.
  - Power supplies: CRPS supplies set their own fan speed from their load, which would
    make them load-driven fans again. The BMC, which manages them through the power
    distribution board, sets both to a fixed speed over PMBus at power-on, high enough
    that a supply never needs to raise it at full load with 35 degrees C intake, and
    alarms if one reports a higher speed (check the chosen supply honours the command). A
    spare supply bay is closed with a blanking cover, since an open bay short-circuits the
    fans.
  - The carrier's heat in the relay duct: the six network modules dissipate at most about
    23 W, and their power follows the traffic the relay forwards anyway, so they add a few
    watts of swing to a coupling the relay's own load already has, about a degree at the
    CPU (estimate). Under the fixed relay clock and fan speeds that never follow load,
    that changes no timing. The duct still keeps the proxies' computing heat, which is
    independent of the relay's work, away from the relay.
  - Why this is a security item: air is shared hardware state (R16i). A proxy that varies
    its load changes the temperature of the air downstream and, through temperature-driven
    fans, the airflow over every module. Fan speeds that never follow load, fixed clocks
    on the relay and the modules with throttle points above anything normal running
    reaches, and the relay's own duct keep proxies from moving the relay's or each other's
    timing through heat.
  - Heatsinks: the module vendor's heat spreader with a fin block cut to the channel, its
    fins running front to rear across the module's 50 mm side (RL-JGDMS-0220).
    Off-the-shelf i.MX 95 SMARC heatsinks are 82 x 42 mm with fins of 5.5 to 14.7 mm, for
    modules dissipating about 3 to 10 W (CL-RELAYCASE-0028), but they are made for the
    module's usual orientation and may have their fins across the airflow once it stands
    on end. Rear modules are industrial grade. Estimated pitch 22 to 30 mm per card, so
    twelve cards take about 260 to 360 mm of the roughly 430 mm inside the case, the rest
    being the relay duct (open point 23).
- **K11. Proxy module requirements** (RL-JGDMS-0219; candidate modules CL-RELAYCASE-0027).
  - SMARC i.MX 95 short size (82 x 50 mm), 16 GB LPDDR5 with inline ECC enabled by the
    module's memory setup; usable memory is less by what ECC reserves (R16e).
  - No wireless variant: a radio would be a way out of the case that bypasses the relay.
  - Boots from its card's write-protected SPI flash through SMARC boot select (K4). Per
    vendor, check that this source is supported on the i.MX 95, and whether an on-module
    microcontroller translates the pins; its firmware is then trusted too.
  - The module's eMMC, and QSPI flash if present, is never executed and is wiped on every
    re-image unless the variant has none, so nothing left by one tenant survives to the
    next.
  - Secure boot fused closed with the project's key before first use; the controller holds
    FORCE_RECOV# inactive.
  - Five GPIO lines free for the slot pins. NXP's firmware (System Manager on the
    Cortex-M33, EdgeLock enclave) is trusted on every module alike; the kernel built for
    the modules leaves out GPU, NPU and video drivers. Industrial temperature grade at
    least in the rear position (K10).
  - Debug: each module's JTAG is left unconnected on the card and fused to disabled or
    secure mode when secure boot is fused, so neither an open lid nor a debug header left
    on the module bypasses secure boot.
  - Time: modules take time only from the relay, consistent with the timer restrictions of
    R20.
- **K12. Candidate network controller** (2026-11-23; a candidate, not a decision;
  CL-RELAYCASE-0026). Innodisk EGPL-G401, an M.2 2280 to four-port gigabit module, six per
  case on the network carrier in the relay duct (RL-JGDMS-0223), each on its own MCIO
  cable and CPU root port, one port per proxy module.
  - All six modules are on one face of the carrier, its back towards the right-hand wall,
    so the modules stand into the airflow rather than splitting it (operator, 2026-11-23,
    in this document's terms: all the network modules on one face of the network card, to
    disturb the duct's airflow as little as possible; not registered as a ruling). Lying
    with their 80 mm length front to rear, three high and two deep, they make the card
    about 170 to 190 mm long and 100 mm high (estimate), standing about 15 to 20 mm into
    the duct.
  - Controller Intel I350 (quad-port MAC and PHY, `igb` driver, SR-IOV not used; part
    facts in CL-RELAYCASE-0024). Host interface PCIe 2.1 x2; M.2 B+M key, which fits an
    M-key socket wired for two lanes. Four ports per module, so six modules give the 24
    ports; at about 500 MB/s each way per module an x2 link has headroom.
  - Power at most 3.8 W (1151 mA at 3.3 V); operating 0 to 55 degrees C ambient; 500 V
    HiPOT isolation; main board 22 x 80 x 15.3 mm.
  - Ports leave the M.2 board on a 25 cm micro-coaxial cable to a separate RJ45 daughter
    board (41.9 x 64.27 x 21 mm), offered with a mounting-hole option (EGPL-G401-C1) or a
    PCIe bracket (-C2 low profile, -C3 standard). In the case the RJ45 board is not
    wanted: custom leads run from the module's connectors to the backplane (K1,
    RL-JGDMS-0223), which needs Innodisk's mating connector and pinout, and magnetics or
    capacitive coupling at the backplane end if the magnetics are on Innodisk's RJ45 board
    (open point 22).
  - Port connectors (2026-11-23, from the datasheet the operator supplied;
    CL-RELAYCASE-0029): the four LAN ports most likely leave on two long micro-coaxial
    connectors in the half away from the M.2 fingers, one on each face, so one faces the
    carrier under at most 2.5 mm of clearance; the small two-row connector beside the
    fingers is most likely the LED connector. The operator proposed a small port board per
    bank of three modules, mating with their connectors and plugging into a backplane
    slot, to keep cables out of the airflow. With a connector under each module that board
    cannot mate; the operator's answer is cut-outs and slots in the carrier with the leads
    behind it (K1, RL-JGDMS-0223).
  - Proposal (2026-11-23 early morning, following from the lead routing; not registered as
    a ruling): EGPL-G401 for the prototype and production alike, so the carrier is the
    only custom board and carries no Ethernet signals or controllers; I350-AM4 laid out on
    a board stays as the fallback if Innodisk's answers on supply life or magnetics are
    poor. Questions for Innodisk, now needed before production: the LAN connectors' and
    LED header's part numbers and pinout (two ports per connector?); where the magnetics
    are; the mating connector, and whether Innodisk supplies custom-length leads or
    approves a cable house's; how long the product will be supplied; and how its
    configuration memory is protected.
  - Before use: read each module's I350 configuration memory, which Innodisk wrote, and
    confirm the management interfaces (NC-SI, SMBus) are off. They are also unconnected:
    the carrier wires no SMBus to its sockets and the MCIO cables carry none
    (RL-JGDMS-0229), because the I350 can share its ports with a BMC over SMBus as well as
    NC-SI (CL-RELAYCASE-0040). The relay's IOMMU stays on (K3).
  - Prototype and outward NIC (2026-11-23 midday): on the prototype bench the modules sit
    two to an XIC 0582 adapter (RL-JGDMS-0228, K15); the EGPL-G401 is M.2 2280 B+M key
    (CL-RELAYCASE-0043), which the adapter's M-key sockets take. The -C2 version, its RJ45
    daughter board on a low-profile bracket, is the second choice for the outward NIC
    (RL-JGDMS-0227).
  - Alternatives with the same controller: Commell M2-3504 and LR-Link LRES2030PT-4RJ45
    (M.2, I350, four ports), or I350-AM4 laid out on a board of its own (the fallback
    above).
  - Considered and set aside: LR-Link LRES2008PT (eight-port I350 PCIe card; three per
    case in the relay, too many slots, and its ports face outwards); an Ethernet switch
    with VLANs (needs trusted switch configuration; RL-JGDMS-0217); Broadcom BCM5719
    (quad-port, but its datasheet is confidential).
- **K13. Candidate relay board** (2026-11-23 early morning, revised midday; a candidate,
  not a decision; CL-RELAYCASE-0030 to -0039). MSI D3061, a DC-MHS M-DNO Type-2 board for
  one Intel Xeon 6 (socket E2), 305.59 x 210 mm (CL-RELAYCASE-0037).
  - PCIe: eight MCIO-8i connectors, each a PCIe 5.0 x8 half of a CPU root port with no
    switch (CL-RELAYCASE-0030); six carry one network module each (K1), one the outward
    NIC (RL-JGDMS-0227, K15) and one is spare. Each MCIO connector also carries SMBus,
    USB, FlexIO and standby lines (CL-RELAYCASE-0035), which the cables leave unwired
    (RL-JGDMS-0229). The board has no LAN controller of its own (CL-RELAYCASE-0033); its
    OCP 3.0 x16 slot stays empty because it has an NC-SI bus to the BMC
    (CL-RELAYCASE-0034). Two M.2 slots take the relay's boot drive.
  - Memory: 16 DDR5 RDIMM slots on eight channels, in two banks of eight standing front to
    back either side of the CPU (CL-RELAYCASE-0037); one ECC RDIMM per channel is enough.
  - Management and trust: the board cannot boot without its DC-SCM 2.0 module, the MGT1,
    which holds the BIOS and BMC flash (CL-RELAYCASE-0032): BMC (AST2600, AMI MegaRAC)
    with its own management port; dual BIOS and BMC flash; optional TPM 2.0 and AST1060
    root-of-trust modules, the root of trust being the route to open point 20 for the
    relay itself. The DC-SCM edge carries the BMC's JTAG and Intel At-Scale Debug lines,
    and the chassis intrusion header goes to the BMC (CL-RELAYCASE-0036). Settings and
    hardening: K15.
  - Power: from a power distribution board on 12-pin connectors, suited to hot-swap CRPS
    supplies; that board also feeds the backplane, the carrier and the controller (open
    point 29).
  - CPU at a fixed clock (proposal; operator, 2026-11-23: "We can underclock the CPU.").
    The lowest-power parts it takes, the Xeon 6505P and 6507P, are rated 150 W
    (CL-RELAYCASE-0031). The relay runs at one fixed core frequency with Turbo and dynamic
    frequency scaling off, chosen so it keeps up with peak load. A power cap is not used:
    it varies the frequency with power draw, which is the Hertzbleed channel, on the
    machine that does the TLS. Only the cores scale, so the saving has a floor; package
    power is to be measured on the relay's own workload. Xeon 6 parts include QAT
    accelerators that could take TLS work off the cores; check the chosen part.
  - Settings: IOMMU on, with each network module, the outward NIC and the DC-SCM's PCIe
    link in groups of their own; VMD off on the MCIO connectors in use; the OCP slot empty
    and the BMC on its dedicated port only (K15); the BMC's own fan control not used for
    case fans (K10); a written policy for BMC firmware updates, since BMC firmware has a
    long record of vulnerabilities.
  - Costs: roughly 150 W even underclocked (estimate), a deeper case (open point 27), and
    DC-MHS parts (DC-SCM module, power distribution board) sourced separately. Rated for 0
    to 35 degrees C ambient.
- **K14. Controller** (RL-JGDMS-0226; details are proposals). An off-the-shelf
  microcontroller board with Ethernet and secure boot, on a panel beside the supplies at
  the rear left, joined to the backplane by one harness carrying the buses, interrupt
  lines and 12 V standby power. Examples of the class: STM32H5 Nucleo or NXP MCX N FRDM
  boards for the prototype; for production an industrial module with the same
  microcontroller, since evaluation boards carry debug probes and are not made for long
  supply.
  - On the backplane, only parts with no firmware: I/O expanders for the roughly 200
    module lines (power enable, reset, presence, boot select, watchdog, FORCE_RECOV#),
    UART expanders for the 24 consoles, fan controller chips, and the write-protect gate;
    differential I2C buffers at each end if the harness runs near the supplies.
  - No wiring to the relay side (relay board, BMC, carrier). A compromised relay or BMC
    cannot power, reset, recover or reflash a module, and a compromised controller never
    sees proxy traffic. What the two must share goes through the management station: a
    swap expected (K8), boot mismatch reports (K4) and the heartbeat (open point 13).
  - Boot flash writes: each card's SPI flash write-protect line is released only when the
    controller asks and the lid switch reports open, through a logic gate on the
    backplane, so a compromised controller alone cannot plant module firmware; bootloader
    updates are done with the lid open (open point 30).
  - Consoles are output from modules that may be compromised: the controller logs the
    bytes raw to the management station and never interprets them.
  - Its own firmware is secure-booted with the project's key, its debug port permanently
    locked, and it is updated only with the lid open. Its management port leaves at the
    rear on the third network (K2). It owns every case fan (K10).
- **K15. Relay management, outward NIC and MCIO cables** (2026-11-23 midday; RL-JGDMS-0227
  to -0229; details are proposals). The relay side gets the same care as the card side: no
  sideband paths, a root of trust, and a lid switch of its own.
  - The MGT1 is the relay side's own management controller, kept apart from the controller
    (K14) as RL-JGDMS-0226 requires; the management station coordinates the two. Its AMI
    MegaRAC firmware is a closed component the relay side has to trust; MSI is to say
    whether OpenBMC is offered for it, and if not the operator records that trust as a
    ruling (open point 31).
  - Root of trust and TPM: fit the AST1060 module (ROT1), which checks the BMC and BIOS
    images before they run, the relay side's counterpart of the cards' write-protect gate.
    Whether the operator's own signing keys can be loaded and unsigned updates refused is
    for MSI, and whether the relay's node identity key is sealed in the TPM is for the
    operator (open point 31).
  - Relay lid switch: a switch of its own on the board's chassis intrusion header, read by
    the BMC (CL-RELAYCASE-0036), apart from the controller's lid switch, so no wire joins
    the two sides.
  - BMC settings: dedicated management port only, shared-NIC failover off; the Redfish
    host interface (a virtual USB network between the BMC and the relay's OS) off; KVM and
    virtual media off; IPMI over LAN off; Intel At-Scale Debug, which lets the BMC halt
    and inspect the host CPU, off permanently if MegaRAC allows it (CL-RELAYCASE-0036,
    open point 31). Setting the supplies' fans over PMBus still works.
  - Outward NIC (RL-JGDMS-0227): an MCIO-to-PCIe x8 slot adapter carrying a standard Intel
    I350-T4 card, its RJ45s on its own bracket in a slot opening at the rear of the relay
    zone beside the large rear fan; failing that, an XIC 0582 carrying an EGPL-G401-C2 on
    its low-profile bracket; then a U.2 or SlimSAS to M.2 adapter. A standard PCIe slot
    has no NC-SI pins but has SMBus (B5/B6), which the adapter leaves unconnected or which
    is taped on the card's fingers; the chosen card must have no NC-SI header. VMD is off
    on its connector, and it links at x4. Same controller and `igb` driver as the network
    modules.
  - MCIO cables (RL-JGDMS-0229): one part number for all seven. Wired: the PCIe lanes and
    grounds, reference clock (A11/A12), reset (B11) and cable-present (B12). Not wired:
    the BMC's SMBus (B8/B9), the CPU's SMBus (A26/A27) and its alert (A9), the USB pair
    (A29/A30), the four FlexIO lines (B26, B27, B29, B30) and 3.3 V standby (A8)
    (CL-RELAYCASE-0035). B12 is shared with PESTI; MSI is to confirm the link trains with
    B12 pulled low at the far end and nothing answering PESTI (CL-RELAYCASE-0039). The
    board-side break stays as well: the carrier has no SMBus pads and the outward
    adapter's slot SMBus pins are not connected. The outward NIC's cable is a different
    length or colour from the module cables, so a swap is hard to make and easy to see.
    Whether the I350 can carry MCTP to a BMC in-band over PCIe, which no cable spec would
    stop, is not yet checked (CL-RELAYCASE-0041).
  - Root-port check (proposal): before any interface comes up, the relay checks that each
    root port holds the device it expects, by link width (x4 for the outward NIC, x2 for a
    network module), PCIe subsystem vendor ID (the card vendor's or Intel's against
    Innodisk's) and MAC prefix, and brings up no interface if any differs. Without it, a
    cable swapped between a module and the outward NIC would put proxy traffic on the
    outward ports and treat an outward port as a slot's link (K3, K4).
  - Prototype bench (RL-JGDMS-0228): a D3061 with its MGT1 and ROT1; three XIC 0582
    adapters (CL-RELAYCASE-0042) carrying six EGPL-G401 modules on SMBus-free cables; and
    the outward NIC. It is to show: x4x4 on the MCIO connectors in use
    (CL-RELAYCASE-0038), and each module under its own root port (`lspci -t`) in an IOMMU
    group of its own; by continuity test, whether the XIC 0582 wires the MCIO's SMBus pins
    to the M.2 sockets' SMBus pins; the modules' lead clearance (about 5 mm between an
    adapter's two sockets); link training with B12 alone (CL-RELAYCASE-0039); and the
    root-port check failing closed when two cables are swapped.

Open points added by this section:

12. **One case per host, or shared.** If several hosts share a case, one node identity
    acts for all of them; per-call registration (R2) keeps caller identity apart, trust
    domains (open point 4) become per host at least, and the relay must authenticate each
    host.
13. **The controller's heartbeat (H4, F8).** With the host outside the case, or several
    hosts per case, whose heartbeat powers boards down, and per board or all at once.
    Revised 2026-11-23 early morning: the controller shares no wiring with the relay
    (K14), so the management station watches both.
14. **Relay availability.** One relay stops all 24 boards when it is down. Whether that is
    accepted, or a second case is the fallback; the V2 rear section probably holds one
    server board.
15. **Where the fetcher (C2) and the event mailbox run.** The fetcher as a keyless process
    on the relay hardware or on a board of its own; the mailbox (RL-JGDMS-0038) as a node
    type in one of the bays, reaching services through the relay.
16. **Keeping each proxy JVM to its own TrustZone key** (K3). One uid for all proxy JVMs
    (RL-JGDMS-0111) against OP-TEE's uid-or-gid client identity (CL-RELAYCASE-0015): a uid
    or supplementary gid per subprocess, which revises RL-JGDMS-0111 and the launcher
    (R14), or session key objects (CL-RELAYCASE-0016); and how many tokens the trusted
    application is built with. Closed 2026-11-22 evening: proxy JVMs hold no keys under
    RL-JGDMS-0213.
17. **Rulings worded for the Unix socket.** RL-JGDMS-0111 and -0114 (the socket identifies
    the subprocess), -0115 and -0116 (policy delivered over the Unix socket) and -0077 and
    -0078 (the cut at the byte stream, over Unix sockets) still name it. Under K3 as
    revised the subprocess is identified by its slot's interface and address and the
    transport is an unencrypted TCP connection; the operator to say whether each is
    restated or stands read that way. As with the Unix socket, nothing on this hop
    satisfies `Integrity.YES` by cryptography; the slot's wiring and the module's kernel
    stand in for it, which is the case RL-JGDMS-0116 already allows for.
18. **Switch chip** (K1, K3). Closed 2026-11-23: no Ethernet switch (RL-JGDMS-0216). A
    part that runs without firmware and whose registers the controller can set for
    per-port ingress tagging, dropping tagged frames on board ports, VLAN membership with
    egress filtering, per-port rate limits, and per-VLAN learning or fixed entries
    (CL-RELAYCASE-0018); and the controller's bus to it.
19. **Carrier hardware** (K1, K8). Closed 2026-11-23 by K1 and K11. Five free GPIO lines
    per slot on the chosen SMARC carrier for the ID pins, and a hot-swap controller per
    caddy.
20. **Attestation on insertion** (K8). Whether it is wanted depends on who can reach the
    case.
21. **Crash reports from boards** (K9). Whether a crash condemns only the digests or also
    suspends the service after a threshold, and whether the relay needs its own identity
    towards the Verdict Registry under STD-002's SPIFFE scheme to file reports.
22. **Network carrier** (K1, K3, K12). Settled in part 2026-11-23 early morning: no PCIe
    switch, one MCIO cable per module (RL-JGDMS-0223). Still open: Innodisk's answers
    (K12) on connector and LED header part numbers and pinout, magnetics, mating connector
    and custom leads, supply life and configuration memory; then the lead length, the
    backplane's lead connectors, and magnetics or capacitive coupling there if needed. The
    I350's expected discontinuance is 1H 2031.
23. **Card pitch** (K10). SMARC connector stack heights and module component heights from
    vendor drawings (CL-RELAYCASE-0028); the narrow channel width from the fan curve.
24. **Thermal test** (K10). 35 degrees C inlet, every module and the relay at full load, a
    filter partly blocked, and one fan stopped in turn at each stage (front, middle and
    rear of the relay duct; front and rear of the card bank; one supply); record CPU,
    module and carrier temperatures against their limits, confirm the CPU never reaches
    its throttle point, and set the coarse inlet-temperature steps.
25. **Module vendor checks** (K11). Boot from the card's SPI flash, any on-module
    microcontroller, variants without eMMC, five free GPIO lines.
26. **Relay board** (K13). The MSI D3061 is the candidate. To confirm: each network module
    in an IOMMU group of its own at x2 on a booted board; the CPU part, its fixed
    frequency and package power on the relay's workload; the outward NIC and its adapter
    (K15); the DC-SCM module with TPM and AST1060; the power distribution board.
27. **Case size** (K1). Confirm 3U: the card's height with the module on end and the
    connector seated, the retention bar and lid clearance, the 92 mm middle fan and the
    rear fan's height, and the relay's CPU heatsink. Confirm the depth, about 550 to 600
    mm with the D3061 behind the carrier (estimate), and a rack and rails to suit.
28. **Controller board** (K14). The board; the backplane's expander, UART and fan
    controller chips; the harness and its connectors; whether consoles go to UART
    expanders or stay as bench-only headers.
29. **Power distribution** (K8, K13). A power distribution board for the D3061 that also
    feeds the backplane, the carrier and the controller's 12 V standby; per-slot eFuses;
    the card connector's pin staggering.
30. **Boot flash updates** (K14). The procedure for updating the cards' bootloader with
    the lid open, and who authorises it.
31. **Questions for MSI, and the relay's trust** (K13, K15). OpenBMC for the MGT1; loading
    the operator's keys into the AST1060 and refusing unsigned updates; turning At-Scale
    Debug off permanently; turning the Redfish host interface off; x4x4 on JMCIO1-8
    (CL-RELAYCASE-0038); link training with B12 alone (CL-RELAYCASE-0039); whether the
    board boots without the MGT1 (CL-RELAYCASE-0032). For the operator: whether the
    relay's node identity key is sealed in the TPM, and the trust in the BMC firmware
    recorded as a ruling if OpenBMC is not offered.
32. **MCIO cables** (K15). A cable house to build seven to the RL-JGDMS-0229 pinout, with
    a drawing to check against; the outward cable's length or colour; whether the I350
    supports MCTP over PCIe (CL-RELAYCASE-0041).
33. **Prototype bench** (K15, RL-JGDMS-0228). Buy and run the bench in K15; its results
    decide the carrier's layout and, with the card pitch (open point 23), whether
    off-the-shelf adapters could replace the carrier.
