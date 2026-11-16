# Relay isolation: security design

Written by the design session (Claude Fable 5.2) on 2026-11-16 for the implementing agent
and the operator. It records what a hostile smart proxy can still attempt once it runs in
its own JVM behind the relay, and the requirements that follow. Nothing here has been built
or tested unless a section says so. Operator rulings are cited by register id; everything
else is a design-session proposal and is marked as such.

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
