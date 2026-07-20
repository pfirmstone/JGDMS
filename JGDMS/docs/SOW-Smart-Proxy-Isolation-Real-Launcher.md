# Scope of Work — the real `SubProcessLauncher`: OS-process spawn + UDS bring-up

- **Drafted:** 2026-07-20.
- **Status: DRAFT — task breakdown for scoping/review, no implementation started.** This document
  contains **no production code changes**. It exists to hand off a well-scoped, board-reviewable
  task set to whoever builds the real launcher next, per `SOW-Smart-Proxy-Isolation-Remaining-
  Work.md` §6 item 1 ("`SubProcessLauncher`'s real OS-process spawning is unbuilt... it belongs to
  whoever picks up building the real launcher next").
- **Origin:** `SOW-Smart-Proxy-Isolation-Remaining-Work.md` §6 (open items carried forward, item 1)
  and `SOW-T4-Wire-Handoff-Protocol.md` §1 ("out of scope, explicitly, not silently assumed: real
  OS-process spawning... that is a *separate* task, not scoped to T4") and §11 ("known issue for
  whoever builds the real subprocess launcher"). Both documents deliberately left this gap for a
  dedicated SOW rather than folding it in; this is that SOW.
- **Companions — read in this order:**
  1. `SOW-Smart-Proxy-Isolation-Wiring.md` — the routing/pool/authority-binding architecture this
     launcher plugs into (T1/T2 landed).
  2. `SOW-T4-Wire-Handoff-Protocol.md` — the byte/framing protocol the wire-handoff channel this
     launcher must supply a live `ByteChannel` for (§5, §11).
  3. `SOW-Smart-Proxy-Isolation-Remaining-Work.md` — the closeout document whose §6 names this gap.
  4. `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 and `SOW-UDS-JERI-Increment-2-Peer-
     Authentication.md` — the existing, general-purpose UDS JERI transport this document assesses
     for reuse, and the **still-unbuilt, deferred** peer-authentication increment (§2 below).
  5. `JGDMS-Board-Reviewer-Guidance.md` — cited throughout by G-number, not re-derived.
  6. This document.
- **What this is not:** an implementation. Every task below is scoped, not built. Several design
  questions below are stated as **open, for Peter to rule on**, not answered by this document —
  see §7. Do not read any task's "recommendation" language as a settled decision unless it is
  explicitly marked as one.
- **No production code was written or modified in producing this document.**

---

## 1. What this closes, precisely

Confirmed by direct code reading (`au.net.zeus.jgdms.loader.isolation.SubProcessLauncher`,
`jgdms-pref-class-loader`): the only production implementation of `SubProcessLauncher` is
`UnsupportedSubProcessLauncher`, whose `launch(IsolationPoolingKey)` unconditionally throws
`UnsupportedOperationException`, and whose `Spawned.openWireChannel()` default likewise fails
closed. Every other piece of the smart-proxy-isolation architecture — `SubProcessPool`,
`SubProcessHandle`, `SubProcessAdminRegistry`, `SubProcessAdministrable`/`SubProcessPolicyAdmin`/
`AdminPrincipalAuthenticator`, `SubProcessLocalPolicyAdmin`, `SubProcessGrantOrchestrator`,
`SubProcessWireHandoffImpl`/`SubProcessReconstructionServer`/`WireFraming`/`WireHandoffCodec`/
`DecodeDepthGuard`, `HostedProxyGuard` — is real, tested, and board-reviewed, but tested entirely
against a `SubProcessLauncher` **fake** (an in-process `Spawned` test double). Nothing in
production ever constructs a real OS process for this architecture to isolate work inside; a
repo-wide grep confirms zero production callers of `SubProcessGrantOrchestrator`,
`SubProcessPolicyAdmin`, or `SubProcessLocalPolicyAdmin` regardless.

This document scopes:

1. **How a per-principal isolated subprocess is actually spawned** — the concrete `ProcessBuilder`
   invocation: classpath/module-path construction, which main class the child JVM runs, how it
   learns which `IsolationPoolingKey` it serves (T1).
2. **How the wire-handoff (business) channel is actually stood up as a live Unix-Domain-Socket** —
   socket-path lifecycle, owner-only permission application, reuse-vs-reinvent against the existing
   `net.jini.jeri.uds` transport (T2). **The admin channel is not a UDS channel** — see item 4 and
   §5.2: it is exported over the SSLEngine/TLS+SPIFFE keystone instead, for reasons T2's original
   scope did not anticipate.
3. **How the parent authenticates the child is not an impostor, and vice versa, before either side
   trusts a byte on the wire-handoff channel** (T3; the admin channel's own authentication is
   handled by the SSLEngine/TLS+SPIFFE keystone's SPIFFE-SVID validation, §5.2 — not T3's concern).
4. **How `SubProcessAdministrable`/`PolicyAdmin` — already built as a `RemoteMethodControl`-
   bearing dynamic proxy with real `MethodConstraints` — actually gets exported onto a live
   transport** (T4): the SSLEngine/TLS+SPIFFE keystone, `net.jini.jeri.ssl` — not
   `net.jini.jeri.uds`, which cannot satisfy this proxy's fixed `Integrity.YES` requirement by
   permanent design (§5.2). This is the task that makes the Remaining-Work §6 `getGrants()`
   marshalability residual load-bearing for the first time (§6 below).
5. **The concrete `SubProcessLauncher`/`Spawned` implementation** gluing T1–T4 together, and the
   subprocess-side bootstrap `main()` that starts `SubProcessReconstructionServer` and the admin
   surface inside the spawned JVM (T5).
6. **Process lifecycle beyond the DGC-analogue reference model `SubProcessHandle` already
   implements**: crash detection, zombie reaping, and how an OS-level process death is fed back
   into `SubProcessPool`'s bookkeeping — a distinct signal from `referenceRetired` (T6).
7. **Platform scope and module placement** — both stated as explicit decisions this document
   recommends against but does not itself finalize (T7).
8. **A system-level adversarial pass** across the whole spawn→authenticate→serve→teardown chain,
   the same standard every other XHIGH item in this architecture has been held to (T8).
9. **QA implications and documentation closeout**, deferred until something concrete exists to
   investigate against, per this document set's own established convention (T9).

It does **not** re-scope anything T1–T5 of the wiring SOW or T1–T6 of the remaining-work SOW
already built and landed — the pool, the authority-binding, the wire-handoff byte protocol, and
the grant-application backend are all reused as-is. This document is additive: it plugs a real
process and two real sockets into seams those documents already built and deliberately left open.

---

## 2. Security lens carried forward, plus what's genuinely new here

### 2.1 Carried forward unchanged (cite, don't re-derive)

- **S1 (`SOW-Smart-Proxy-Isolation-Wiring.md`'s canonical S1 — admin authority proven by an
  enforced mechanism, not the business stub's interface set / declaration, G4).** Cited by its
  home document's tag here, not re-minted: an earlier draft of this section defined its own,
  differently-worded local "S1" ("declaration-is-not-authority"), which collided with the Wiring
  SOW's actual S1 (a distinct concept, "policy-grant-push authority is a mechanism gap, not a
  convention") — a board-review finding, fixed by this edit; see §3's T2 row for the related fix to
  the *channel-separation* claim, which this document previously also mislabeled "S1" but which
  actually belongs to `SOW-T4-Wire-Handoff-Protocol.md` §8's S3. The admin channel's
  `MethodConstraints`-based authentication
  (`SubProcessPolicyAdmin`, `AdminPrincipalAuthenticator`) is already built and already
  board-reviewed for the confused-deputy/forged-`Subject` finding (`AdminPrincipalAuthenticator`'s
  own javadoc, "NECESSARY, not SUFFICIENT" section). This document's T4 must **export** that
  already-correct mechanism onto a live transport — now settled as the SSLEngine/TLS 1.3 + SPIFFE
  keystone, `net.jini.jeri.ssl`, see §5 — without weakening it — the temptation to re-derive
  authentication logic at the transport layer instead of reusing the existing gate is exactly the
  "two places deciding the same thing" anti-pattern `SOW-Smart-Proxy-Isolation-Remaining-Work.md`
  §2 already warned T3(c) against for grant application; the same discipline applies here for
  admin-channel export.
- **S2/S3 (decode-surface concentration; three-axes channel check, G10 boundary-vs-interior).**
  `SubProcessReconstructionServer`/`SubProcessWireHandoffImpl` already implement and test the
  three-axes property (byte flow, request origination, authorization) for the wire-handoff
  channel — see `SOW-T4-Wire-Handoff-Protocol.md` §8. **This document's T2/T3 must re-derive the
  same three-axes check for the *transport bring-up itself*** (who connects to whom first, who
  authenticates first, whether the admin channel and the wire-handoff channel can be confused for
  each other) — a genuinely new instance of the check, not a restatement of the existing one,
  because bring-up happens *before* either existing gate runs. Post-§5's admin-channel resolution
  the two channels no longer even share a socket layer (admin: SSLEngine/TLS+SPIFFE over
  `net.jini.jeri.ssl`; wire-handoff: UDS over `net.jini.jeri.uds`/hand-rolled `WireFraming`), which
  removes one confusion vector by construction but does not remove the need to re-derive the check
  for each transport's own bring-up sequencing.
- **S4 (DGC-ack, not connection-close, `SubProcessHandle`'s own javadoc).** `SubProcessHandle`
  already deliberately treats `connectionClosed()` as inert with respect to teardown. **T6 below
  needs a second, genuinely different signal** (OS process death) that is emphatically not the
  same event as a DGC-ack, and must not be wired to `referenceRetired` as if it were — see §2.2.
- **G6 (fail-closed on ambiguity, *and* check the fail-closed decision has no fail-open side
  effect).** This is the single most load-bearing lesson to carry into T2/T3: the UDS transport's
  own hardening history (`SOW-Unix-Domain-Socket-JERI-Transport.md`, `UdsServerEndpoint`'s
  `checkPermissions`/owner-only-mode machinery) already contains a war story of exactly this
  shape — a permissions gate that failed closed but whose cleanup path left the already-bound
  socket file world-accessible on the way out. **Any socket-file creation this document's T1/T2
  add must be reviewed against that exact failure mode**: trace every error path from "create the
  socket file" through "apply owner-only mode/ACL" through "fail and clean up" and confirm no exit
  leaves an unprotected or dangling socket file on disk.

### 2.2 Genuinely new security surface this document introduces

None of the following were reviewed by any prior board round in this architecture, because none
of the code they apply to exists yet:

- **Impostor-child / impostor-parent at first connection.** `SubProcessReconstructionServer`
  already re-validates the *pooling key* asserted inside a wire-handoff request (defence in depth
  against a misdirected/replayed request, `SOW-T4-Wire-Handoff-Protocol.md` §4.2) — but that check
  runs *after* a channel is already open and trusted enough to read a frame from. **Nothing today
  establishes that the process on the other end of a freshly-opened socket is the specific child
  this specific parent just spawned**, as opposed to some other local process that raced to
  connect the socket path first, or a process that inherited/guessed the path. This is squarely
  T3's job, and squarely the reason `SOW-UDS-JERI-Increment-2-Peer-Authentication.md` exists as a
  **separate, still-unbuilt** increment on top of the general-purpose UDS transport — see §7 Q1.
- **Crash-vs-clean-teardown is a new event, not a relabelled old one (S4 extended).** An OS process
  crash is neither a DGC-acknowledged clean call nor a connection close in the sense
  `SubProcessHandle.connectionClosed()` already treats as inert — it is proof the subprocess is
  *actually gone*, which the existing model has no representation for at all. Wiring it in wrong
  (e.g., treating any socket EOF as equivalent to `RetirementReason.LEASE_EXPIRED`) would
  re-introduce exactly the connection-close-as-teardown-signal bug S4 was written to prevent, via
  the back door of "but this time it's *probably* a real crash." T6 must derive this signal from
  the OS (`Process.isAlive()`/exit-code observation, mirroring the Phoenix Activation `Watchdog`
  precedent — see §3 T6 and §7 Q3), never from channel behaviour.
- **Resource exhaustion via spawn itself.** `SubProcessPool.obtain()` spawns unconditionally on a
  pool miss; nothing today bounds the number of concurrently live subprocesses. Once `launch()` is
  real, an attacker able to present many distinct SPIFFE/X.500 identities (each a legitimate,
  distinct `IsolationPoolingKey` — pooling is deliberately per-principal, so this is not a
  keying bug) could drive unbounded child-JVM spawning, a real OS-level resource-exhaustion vector
  that the in-process test fake could never exercise. Flagged for T8's adversarial pass; whether
  the fix belongs in this document's scope or a follow-on (a spawn-rate/concurrent-subprocess
  ceiling) is itself an open question — see §7 Q5.

### 2.3 POLP / `doPrivileged` conventions this document must follow, grounded in this codebase

`PreferredProxyCodebaseProvider` — the codebase this whole architecture routes out of — already
establishes the convention this document's child-JVM bring-up code must follow: privileged
operations (its own codebase-jar HTTP fetch, `:911-916`; per-URI digest-grant application,
`:1924-1941`) are wrapped in `AccessController.doPrivileged`/`Security.doPrivileged` **at the
narrowest possible frame**, with an explicit comment naming *why* the privileged frame must stop
the stack walk there (so a caller with fewer permissions cannot ride a broader ambient privilege
than the specific operation needs — POLP by construction, not by policy-file diligence alone).
Spawning a subprocess (`ProcessBuilder.start()`, `RuntimePermission("...")`-gated file/socket
creation for the UDS bring-up) is exactly this shape of narrowly-scoped privileged operation and
must follow the same pattern: `doPrivileged` wrapping only the OS-level spawn/socket-create call
itself, not the surrounding pool/policy logic, so a hosted (untrusted) proxy's own call stack can
never ride the launcher's privilege to spawn or connect on its own behalf. This is a concrete
acceptance criterion for T1/T2 below, not a general reminder.

---

## 3. Execution plan — task breakdown (agent type · effort · risk)

Effort follows the project convention (security → `xhigh`, mechanical → `medium`); orchestration
follows *fresh implement → adversarial board for XHIGH*, per the precedent every other task in
this architecture's build-out was held to (a real defect was found by board review, not by the
implementing agent's own testing, in every XHIGH item so far — T1/T2/T4 of the wiring SOW, T1/T2
of the remaining-work SOW). There is no reason to expect this task set to be the first exception;
do not skip the board step on that theory.

### Summary

| Task | Deliverable | Effort · risk | Review | Depends |
|------|-------------|----------------|--------|---------|
| **T1** · Child-JVM bootstrap & command-line construction | Concrete `ProcessBuilder` invocation spawning a child JVM per `IsolationPoolingKey`: classpath/module-path construction (see §7 Q4 — full application classpath vs. a curated, minimal isolation-subprocess classpath; **recommend the latter**, unresolved), the child's bootstrap main class (new, e.g. `SubProcessBootstrapMain` in whatever module hosts the real launcher — see T7), and how the child learns which key it serves (a command-line argument is simplest and matches `IsolationPoolingKey.value()`'s plain-string shape, but a command-line argument is visible to any local user via `/proc/<pid>/cmdline` on Linux — an information-disclosure question worth an explicit acceptance criterion: does leaking *which principal* a given PID is isolating for constitute a problem? Provisionally low severity — pooling keys are already derived from certificate-public identity, not a secret — but state this explicitly rather than silently accepting it). **Direct precedent to reuse, not reinvent:** `org.apache.river.phoenix.Activation.buildGroupProcess`/`getInstantiator` (`phoenix-activation/phoenix/.../Activation.java:1526-1835`) already does this exact shape for RMI activation groups — `ProcessBuilder` construction from a command-environment descriptor, `-D` property forwarding, optional working-directory/environment isolation, and (`:1805-1815`) explicit `-Djava.security.manager=` propagation from parent to child so the child runs under the same SM posture as the parent. Follow this precedent's shape rather than designing a parallel one; deviate only where the isolation architecture's own requirements (§4 of the wiring SOW: the child must start with `net.jini.loader.pref.smartProxyIsolation.enabled` **unset**, already defensively re-checked by `SubProcessReconstructionServer`'s constructor) demand it. | **XHIGH** | **parallel board** — probe: can a hosted/untrusted proxy's own call stack influence the constructed command line (argument injection into the child's classpath/main-class/properties); does the child inherit more environment/classpath than the isolation architecture's own trust model requires | none — can start immediately |
| **T2** · UDS socket bring-up for the wire-handoff channel | Concrete Unix-Domain-Socket creation, binding, and owner-only permission application for the **wire-handoff (business) channel only** — the admin channel no longer uses UDS at all; see §5.2 below, it moved to the SSLEngine/TLS+SPIFFE keystone (`net.jini.jeri.ssl`), so the "kept structurally separate per S3" property (`SOW-T4-Wire-Handoff-Protocol.md` §8's own explicit caveat about a hypothetical future multiplex) now holds by construction — the two channels do not even share a transport, let alone a socket. **Reuse `net.jini.jeri.uds.UdsPaths`' validation** (`jgdms-jeri`) as the canonical path-safety check (abstract-namespace rejection, embedded-NUL rejection, `sun_path` byte-ceiling enforcement) — a small, self-contained, already-hardened utility with no reason to be reimplemented. **Acceptance criterion, corroborated by both board seats: `UdsPaths` and its `validate` method are package-private** (`net.jini.jeri.uds`, `UdsPaths.java`) — they do not compile from a new module as-is. T2 must either promote `UdsPaths`/`validate` to public, or add an equivalent public accessor in `net.jini.jeri.uds`; this is required regardless of T7's module-placement answer, since the real launcher's module is not `net.jini.jeri.uds` itself. **Reuse `UdsServerEndpoint`'s owner-only POSIX-mode/ACL-application sequencing** (`jgdms-jeri/.../UdsServerEndpoint.java:658-753`) as the model for correct fail-closed-with-no-fail-open-side-effect socket-file handling (G6, §2.1 above) — do not re-derive this sequencing from scratch; it already encodes a real, hard-won lesson (the unlink-on-error gap named in Board Guidance G6's own worked example). Socket-path lifecycle: a fresh, unpredictable per-spawn path (e.g. under a private, mode-0700 runtime directory keyed by pooling key + a random suffix — never a path derivable from the pooling key alone, which would let a co-resident process pre-create/pre-bind it) is an explicit acceptance criterion, not a detail to leave implicit. **Stated design fact, not left implicit: the child listens/accepts for the wire-handoff channel; the parent is the connecting client.** Confirmed by reading `SubProcessReconstructionServer.java` (subprocess-side, `serve()` reads-then-writes on an already-open channel, never dials out) and `SubProcessLauncher.Spawned.openWireChannel()`'s own javadoc ("a freshly-opened, **connected** channel to the subprocess", called by the parent-side `SubProcessHandle`) — matches `SOW-T4-Wire-Handoff-Protocol.md` §8's own three-axes finding ("request origination: client-initiated only... never accepts an inbound connection [client-side]... never opens an outbound connection back to a client [subprocess-side]"). T2's socket bring-up therefore binds/listens in the **child's** bootstrap (T5), not the parent's. **Gated on a dependency-ledger item, not assumed clean: `UdsEndpointRoundTripTest` is currently fully disabled** (every `@Test` commented out, `jgdms-jeri/src/test/java/net/jini/jeri/uds/UdsEndpointRoundTripTest.java`) — see §4's explicit ledger line; T2 must not model its UDS hardening/sequencing on a test suite that is not actually running. | **XHIGH** | **parallel board** — probe: TOCTOU between socket-file creation and owner-only mode application (can a window exist where the file is briefly world-accessible); path predictability (can a local attacker pre-bind or symlink-race the socket path before the real child does) | T1 (needs the child process to connect the far end) |
| **T3** · Parent↔child mutual authentication for the **wire-handoff channel** (scope narrowed — see note) | **Scope narrowed by §5.2's resolution of the admin-channel transport question:** the admin channel's parent↔child (client↔server-principal) authentication is now handled by the SSLEngine/TLS+SPIFFE keystone's own SPIFFE-SVID cross-validation (§5.2) — T3 no longer needs to solve authentication for that channel. What remains is the mechanism that lets the parent trust "the process on the other end of this wire-handoff socket is the child I just spawned, not an impostor that raced onto the same path" — and symmetrically, lets the child trust it is talking to its actual parent, not a local process that discovered the socket path. **Stated design fact (see T2): for the wire-handoff channel the child listens/accepts and the parent connects** — so, concretely, it is the **parent**, on its own connecting client socket, that would query `SO_PEERCRED`-equivalent credentials of the peer it just connected to (the child) to cross-check against the child PID it already holds from `ProcessBuilder.start()`'s returned `Process`; an earlier draft of this row described this the other way around ("the socket's connecting peer PID" checked as if the parent were the acceptor) — corroborated by both board seats as backwards for this channel, fixed here. **This is still the task most directly blocked on an open question — see §7 Q1.** Candidate mechanisms, **none selected by this document**: (a) OS process ancestry as the trust anchor via `SO_PEERCRED`-equivalent (strongest available proof but exactly what `SOW-UDS-JERI-Increment-2-Peer-Authentication.md` scoped and left **unbuilt, deferred, Linux-only, needs Panama FFI or a DirtyChai JDK API**); (b) a one-time shared secret handed to the child out-of-band at spawn time (e.g. via the child's own `stdin`, mirroring Phoenix `Activation`'s existing pattern of writing the `ActivationGroupID`/`ActivationGroupDesc` down the child's stdin pipe via `AtomicMarshalOutputStream` *before* the group ever touches a socket, `Activation.java:1555-1579`) and presented back over the wire-handoff socket as the first bytes of the connection; (c) owner-only socket-file permissions alone, accepted as the *entire* trust boundary (matching UDS increment 1's own accepted baseline per `SOW-Unix-Domain-Socket-JERI-Transport.md`) on the reasoning that only the parent's own OS user can open the socket file at all, so "some other local process of the same user" is the only residual impostor class, and that class already has ambient access to everything the parent process does. **Recommendation, not a decision:** (b) is the cheapest correct-today option that does not block on increment 2's Panama-FFI work, and has a direct, already-reviewed precedent in this same codebase (Phoenix's stdin handoff) — but this document explicitly does not settle it; a real security trade-off exists between (b)'s bespoke-protocol surface and (a)'s eventual stronger guarantee, and Peter should rule on it before implementation starts. | **XHIGH** | **parallel board** — probe: can a co-resident process of the same OS user (the (c)-baseline's own stated residual) do anything with a stolen/observed shared secret that it could not already do; does the chosen mechanism actually run *before* `SubProcessReconstructionServer`'s own gates, or could a message be crafted that reaches it before authentication completes | T1, T2 |
| **T4** · `SubProcessAdministrable`/`PolicyAdmin` real export onto the SSLEngine/TLS+SPIFFE keystone (decided — see §5.2) | Wires the already-built `SubProcessPolicyAdmin`/`GuardedPolicyAdminHandler`/`AdminPrincipalAuthenticator` machinery (a `RemoteMethodControl`-bearing `java.lang.reflect.Proxy` with real, fixed `MethodConstraints`, `jgdms-pref-class-loader`) onto a live transport for the first time. **No longer an open module/transport question (§7 Q2 resolved — Peter's ruling, §5.2):** the admin channel is exported over `net.jini.jeri.ssl.SslServerEndpoint` (`BasicJeriExporter`/`AtomicILFactory`, the same pattern `net.jini.jeri.uds` would have used) — **not** `net.jini.jeri.uds.UdsServerEndpoint`, because `SubProcessPolicyAdmin.buildAdminConstraints()` (`jgdms-pref-class-loader/.../isolation/SubProcessPolicyAdmin.java:96-106`) fixes `Integrity.YES`, `ClientAuthentication.YES`, and `ServerAuthentication.YES` as un-weakenable requirements, and `net.jini.jeri.uds.Constraints` (`jgdms-jeri/.../uds/Constraints.java`) only ever supports the negative variants of exactly those three (`Integrity.NO` at `:160`, `ClientAuthentication.NO`/`ServerAuthentication.NO`/`Delegation.NO` at `:179-181`) — **by permanent design** (the class's own javadoc: transport-claimed `Integrity.YES` would let the object layer skip the DER/JOSS decode-integrity gate; `ClientAuthentication.YES`/`ServerAuthentication.YES` are deferred because UDS increment 1 surfaces no authenticated peer principal at all), not a temporary gap Increment 2's `SO_PEERCRED` work will close. Exporting `SubProcessPolicyAdmin` over `UdsServerEndpoint` therefore throws `UnsupportedConstraintException` immediately on `Integrity.YES` and always will. `net.jini.jeri.ssl.ConnectionContext` (`jgdms-jeri/.../ssl/ConnectionContext.java:264-306`), by contrast, satisfies all three today — this is a merged, already-validated transport (`SslEngineRoundTripTest`, live/enabled, not the disabled state of `UdsEndpointRoundTripTest`), not new work T4 has to build. **Confirmed by reading the code, not assumed:** the keystone already produces a bound caller `Subject` during dispatch — `net.jini.jeri.BasicInvocationDispatcher.invokeWithClientSubject` (`:2124-2246`) binds the TLS/SPIFFE-verified caller identity (obtained server-side via the `ClientUserSubject` context element, populated by `SslServerEndpointImpl`'s certificate verification) into the executing thread via `Subject.callAs`/`Subject.doAs` before `AdminPrincipalAuthenticator.requireAdmin(...)` ever runs — so this likely resolves, for the admin channel specifically, the separate T3-adjacent finding that "neither T3 candidate produces the bound admin `Subject`" (the wire-handoff channel's own authentication need is unaffected — see T3's narrowed row). Exporting still inherits JERI's own DGC/at-most-once semantics and **real wire marshalling for `PolicyAdmin.getGrants()`'s `PermissionGrant[]` return value**, closing the Remaining-Work §6 residual (§6 below) as a natural consequence of using a real RMI-shaped transport, exactly as reusing `net.jini.jeri.uds` would have given, without inheriting UDS's disabled-test-suite risk. **New reverse dependency, unavoidable either way:** `jgdms-pref-class-loader` does not depend on `jgdms-jeri` today; T7's "new module depending on both" recommendation is unaffected by which JERI transport package T4 uses. **`getGrants()`'s `PermissionGrant[]` marshalability (Remaining-Work §6 residual) is a real, testable acceptance criterion of this task**, unchanged by the transport swap: it is this task's job to actually marshal a `PermissionGrant[]` across the SSL admin channel and confirm it round-trips, including for every concrete `PermissionGrant` subtype `SubProcessLocalPolicyAdmin`'s `getGrants()` can now return (`LeasedPermissionGrant`/`OneShotLeasedPermissionGrant` wrapping a `DigestGrant`-context content template, per that class's own javadoc). | **XHIGH** | **parallel board** — probe: does exporting the admin proxy over the SSL/SPIFFE transport change any of `AdminPrincipalAuthenticator`'s "necessary but not sufficient" preconditions (does the keystone's own bound-`Subject` mechanism interact with, weaken, or duplicate the existing `Subject`-based check — two gates agreeing is fine, two gates *disagreeing silently* is not); can `getGrants()`'s marshalled reply leak more than the caller was already authorized to see (e.g. does a `DigestGrant`'s marshalled form ever carry a `ClassLoader`/`ProtectionDomain` reference it shouldn't cross the wire) | **no longer depends on T2/T3** (those now scope to the wire-handoff channel only) — T4 needs the SSL/SPIFFE keystone's own SVID provisioning for the admin principal (a T5/T7 bring-up detail, not a T4 unknown) and can proceed in parallel with T1/T2/T3; informs T7 |
| **T5** · Concrete `SubProcessLauncher` + subprocess-side bootstrap | The actual `SubProcessLauncher implements` class replacing `UnsupportedSubProcessLauncher` in production wiring, and the new subprocess-side `main()` the child JVM (T1) actually runs: starts `SubProcessReconstructionServer` on the wire-handoff listener, starts the T4 admin export on the admin listener, establishes the subprocess's own local `DynamicPolicyProvider` (the object `SubProcessLocalPolicyAdmin`'s constructor requires — this task decides how that provider is seeded at subprocess bring-up: an initial, minimal, deny-by-default policy that `SubProcessGrantOrchestrator`'s later `applyVerdictCeiling` calls widen, never a policy that starts already broad), and defensively re-confirms `net.jini.loader.pref.smartProxyIsolation.enabled` is unset in its own process (the check `SubProcessReconstructionServer`'s constructor already performs — this task is what actually makes that check reachable in a real process for the first time). `Spawned.openWireChannel()` connects to T2's wire-handoff socket per hosted-object handoff (matching the "one connection per handoff" scope decision `SOW-T4-Wire-Handoff-Protocol.md` §5 already made); `adminSurface()` returns whatever T4 produces; `shutdown()` performs graceful termination (see T6 for the crash/force-kill path this must be distinct from). **Known issue to account for, already flagged and waiting**: `SOW-T4-Wire-Handoff-Protocol.md` §11 — `org.apache.river.concurrent.ReferenceProcessor`'s non-daemon cleaner thread (spawned as a side effect of first using `AtomicMarshalOutputStream`/`AtomicMarshalInputStream`, `jgdms-collections/.../ReferenceProcessor.java`) prevents a bare child JVM from exiting on its own after a graceful `shutdown()`; this task must add an explicit shutdown path (a shutdown hook, or an explicit daemon-thread/exit call) rather than rediscovering the hang under time pressure. | **XHIGH** | **parallel board** — probe: does the initial local policy seeded at bring-up actually start minimal (an adversarial check: can the freshly-spawned, not-yet-granted-anything subprocess's hosted proxy do anything beyond the JDK/JGDMS bootstrap floor before any `applyVerdictCeiling` call ever lands); does `shutdown()` actually terminate the process (not merely close the sockets, leaving an orphaned JVM) | T1–T4 |
| **T6** · Process-lifecycle integration: crash detection, zombie reaping, pool eviction | A genuinely new signal, distinct from `SubProcessHandle`'s existing DGC-analogue model (§2.2 above): observing that the OS process itself has died (crashed, killed, OOM-killed) and feeding that into `SubProcessPool` so a dead entry is not left in `live` forever (today, nothing evicts a `SubProcessPool` entry except `SubProcessHandle.referenceRetired`'s last-reference teardown — a crash while references are still live has no eviction path at all today). **Explicit T6 deliverable, corroborated by both board seats: `SubProcessHandle` has no method to mark "torn down due to an observed OS crash."** Its `RetirementReason` enum today has exactly two values, `CLEAN_CALL_PROCESSED` and `LEASE_EXPIRED` (`jgdms-pref-class-loader/.../isolation/SubProcessHandle.java`); T6 must add a third — an OS-crash-observed retirement reason — rather than overloading either existing value (S4, §2.1 above: a crash is not a DGC ack and must not be mistaken for one). **Direct precedent to reuse the shape of, not the mechanism:** Phoenix `Activation`'s `Watchdog` thread (`Activation.java:1617-1733`) already does exactly this for activation groups — blocks on `Process.waitFor()`, logs abnormal exit codes with the OS PID for `hs_err_pid<N>.log` correlation, and drives group-entry reset/restart under a write lock. **Explicit, non-obvious acceptance criterion this task must resolve, not silently inherit from Phoenix:** Phoenix's groups auto-*restart* on crash (`restartServices()`); **this architecture's `SubProcessGrantOrchestrator.applyVerdictCeiling` has an anti-replay generation-map keyed on `(targetKey, contentHash)` that only accepts a *strictly newer* verdict timestamp than the last one it successfully applied** (its own javadoc, "Anti-replay / freshness gate") — if a crashed-and-respawned subprocess needs its prior ceiling re-applied and the orchestrator is a long-lived singleton (its own javadoc already flags unbounded generation-map growth as a known residual, Remaining-Work §6), the *same*, already-applied verdict would now be rejected as stale by the very mechanism designed to stop a replay, because the orchestrator has no way to distinguish "this is the same subprocess re-asking" from "this is a replay attack" — a crashed subprocess is not "the same subprocess" from the orchestrator's point of view, it's a new one that happens to reuse the same pooling key. This is a real, previously-unconsidered interaction between two independently-correct mechanisms (SubProcessGrantOrchestrator's anti-replay design and any auto-restart policy this task might add) and must be explicitly resolved — see §7 Q3 — not silently papered over by, e.g., clearing the generation map entry on respawn (which would itself need its own careful justification, since it reopens exactly the replay window the gate exists to close, just scoped to "immediately after an observed crash" rather than always-open). | **HIGH** | single reviewer + adversarial pass on the crash/anti-replay interaction specifically (not merely "does the watchdog detect a crash") | T5; informs whether/how T3(SubProcessDynamicPolicy)/`SubProcessGrantOrchestrator`'s caller needs to re-drive verdict application after a detected crash-respawn |
| **T7** · Platform scope & module placement (design decisions, not code) | Two decisions this document deliberately does not finalize (§7 Q4, Q6): **(a) Linux-only vs cross-platform for v1.** `java.nio`'s `StandardProtocolFamily.UNIX` is itself cross-platform (macOS, Windows since the supporting JDK version), and `UdsPaths`/`UdsServerEndpoint`'s own POSIX-mode-or-ACL-fallback split already shows the existing UDS transport was built with cross-platform intent — but `SO_PEERCRED`-based peer authentication (the strongest T3 candidate, §7 Q1) is explicitly **Linux-only, needs Panama FFI or a DirtyChai JDK API** per `SOW-UDS-JERI-Increment-2-Peer-Authentication.md`, and this repo's own convention elsewhere in the UDS story is Linux-first. **Recommendation, not a decision:** scope T1–T6 as Linux-first for v1 (matching the repo's established UDS convention and avoiding a false cross-platform claim while T3's strongest option remains Linux-only), with cross-platform support an explicit, separately-scoped follow-on once T3's mechanism is settled — but state this as a recommendation for Peter to confirm or override, not as already decided. **(b) New module vs. existing.** T4's now-settled transport (§5.2: the SSLEngine/TLS+SPIFFE keystone, `net.jini.jeri.ssl`) needs a home that depends on both `jgdms-pref-class-loader` and `jgdms-jeri`, which are today dependency-siblings (both depend only on `jgdms-platform`; neither depends on the other) — placing the concrete launcher in either existing module would be a reverse-layering smell, the same class of concern `SOW-T4-Wire-Handoff-Protocol.md` §6 point 1 already raised and deliberately avoided for the business channel. **Recommendation, not a decision:** a new module (name TBD, e.g. `jgdms-subprocess-launcher`) housing the concrete `SubProcessLauncher`, the subprocess bootstrap `main()`, and T4's SSL/SPIFFE admin export — consistent with this codebase's existing pattern of a thin interface in a low-level module (`SubProcessLauncher` itself, already in `jgdms-pref-class-loader`) and a concrete, dependency-heavier implementation injected at a higher layer. | **MEDIUM** (design/analysis pass) | single reviewer + explicit sign-off from Peter on both (a) and (b) before T1–T6 code is built against either assumption | independent; should land *before* T1/T2/T4 code starts, since it constrains where that code lives and what platform guarantees it may assume |
| **T8** · System-level adversarial pass | Dedicated adversarial probing of T1→T2→T3→T4→T5→T6 as one chain, once substantially built — the same standard every other XHIGH milestone in this architecture was held to, and the same reason it mattered every previous time: T2(wiring)'s TOCTOU bug and the UDS transport's own unlink-on-error gap were both caught this way, not by component-level testing alone. Explicit probes, in addition to each task's own row above: (i) can a hosted/untrusted proxy, given only the deliberately narrow permissions a freshly-spawned subprocess starts with (T5's seeded-minimal-policy acceptance criterion), ever influence its own spawn arguments, socket paths, or the parent's trust decision about it; (ii) does the whole chain actually degrade to a clean, reported failure — never a hang, a leaked socket file, or an orphaned process — on every injected failure point (spawn failure, socket-bind failure, authentication failure, crash mid-handoff); (iii) re-run the three-axes check (byte flow / origination / authorization) across the *combined* system now that two real sockets and a real process exist, not just the already-tested in-process wire-handoff logic in isolation. | **XHIGH** | **parallel board** (2–3 adversarial) | T1–T6 substantially implemented |
| **T9** · QA implications + documentation closeout | Deferred until T1–T8 produce something concrete to investigate against, per this document set's own established convention (`SOW-Smart-Proxy-Isolation-Wiring.md`'s Notes: "QA implications... are not scoped here — deliberately. They need something concrete to investigate against"). Update this document's own status header, and the "genuinely open" bullet in `SOW-Smart-Proxy-Isolation-Remaining-Work.md` §6 item 1 once this document's own work lands, so a future reader is not misled by a now-stale "unbuilt" claim (the same discipline that document's own T7 applied to its predecessors). | **MEDIUM** | single reviewer | T1–T8 substantially landed |

### Sequencing

- **First, and blocking everything else:** **T7**'s two design decisions need Peter's sign-off
  before T1/T2/T4 code is written against an assumption that might be overturned later — this is
  explicit per T7's own row, not a formality.
- **Then, largely parallel:** **T1** (child bootstrap), **T2** (wire-handoff socket bring-up), and
  **T4** (admin export onto the SSL/SPIFFE keystone) — T1/T2 are independent of each other
  structurally, though T2's socket needs *somewhere* for a real child to connect from, so
  integration testing needs both; T4 is independent of T1/T2 entirely now that the admin channel no
  longer shares a transport with the wire-handoff channel (§5.2).
- **After T1+T2:** **T3** (mutual authentication, now scoped to the wire-handoff channel only —
  §5.2) — needs a real child process and a real wire-handoff socket to authenticate across. **T3
  is the task most likely to need Peter's direct input beyond T7**, per §7 Q1 — do not treat its
  "recommendation" language as license to proceed without that sign-off.
- **T4 (admin export) no longer waits on T1/T2/T3** — the admin-channel transport question is
  resolved (§5.2: SSLEngine/TLS+SPIFFE keystone, not UDS), so T4 has no wire-handoff-channel
  dependency left and can be built and board-reviewed in parallel with T1/T2/T3, gated only on T7's
  module-placement sign-off. **After T3 and T4 both land:** the bulk of **T5** (concrete launcher +
  subprocess bootstrap) — needs both a real wire-handoff channel (T2/T3) and the real admin export
  (T4) to glue together.
- **After T5:** **T6** (process lifecycle) — needs a real, running subprocess lifecycle to observe
  crashes against.
- **After T1–T6 substantially exist:** **T8** (system-level adversarial pass) — not optional, not
  replaceable by "the component-level boards already covered it," per the precedent stated in this
  task's own row.
- **Last:** **T9**, once the above is real, not planned — matching every prior closeout task in
  this document set.

### Notes

- **This is the highest-cumulative-risk task set in the smart-proxy-isolation architecture so
  far**, not because any one task is individually harder than, say, T2(wiring)'s authority-binding
  mechanism, but because it is the first point where the architecture's security properties are
  tested against a *real* adversary-reachable OS boundary (real process, real sockets, real
  filesystem permissions) rather than an in-process fake that cannot exhibit TOCTOU, race, or
  resource-exhaustion classes of defect at all. Hold every XHIGH row to the board standard without
  exception.
- **Do not let T3's open question (§7 Q1) become a silent default.** Shipping option (c) — bare
  owner-only socket permissions, no additional authentication — without an explicit sign-off that
  this is an *accepted*, not merely *convenient*, posture would repeat the exact "outcome is a
  claim, mechanism is the proof" gap Board Guidance G2 exists to catch: "the parent trusts the
  child" is the outcome; which concrete mechanism proves it is the open question, and defaulting
  to whichever option required the least new code is not the same as choosing the right one.
- **T7's module-placement decision affects more than aesthetics.** T4 now unconditionally depends
  on `jgdms-jeri` (the SSL/SPIFFE keystone lives there, §5.2 — not an open option any more), so the
  new module T7(b) recommends must also be where T1's `ProcessBuilder` invocation and T2's socket
  bring-up live, since they're the same "concrete launcher" implementation — decide T7 before
  task-assigning T1/T2/T4 to avoid rework.

---

## 4. Explicit dependency ledger

| This document's task | Depends on / feeds | Direction |
|---|---|---|
| T1 child-JVM bootstrap | `SubProcessLauncher.launch()` (interface, landed); Phoenix `Activation.buildGroupProcess` (precedent, not a dependency) | implements the seam |
| T2 UDS socket bring-up (wire-handoff channel only — admin channel moved off UDS, §5.2) | `net.jini.jeri.uds.UdsPaths`/`UdsServerEndpoint` (reused utilities, landed); `Spawned.openWireChannel()` (interface, landed); **`UdsPaths`/`validate` promoted to public or an equivalent public accessor added (not landed — new T2 acceptance criterion, package-private today)** | extends |
| **`UdsEndpointRoundTripTest` currently fully disabled** (every `@Test` commented out, `jgdms-jeri/src/test/java/net/jini/jeri/uds/UdsEndpointRoundTripTest.java`) — re-disabled by commit `415e81570` (an unrelated DER-serializer/AtomicSerial fix pass) after having been re-enabled and fixed by commit `bf5f1db95` (root cause: `UdsServerEndpoint.restrictPermissions`'s `NOFOLLOW` chmod cannot `open()` an `AF_UNIX` socket special file, `ENXIO`). `SOW-Remaining-DER-ProxyTrust-Followups.md` still claims `UdsEndpointRoundTripTest` 17/17 — **that claim is stale**, corroborated by both board seats reading the actual file state. | **gates T2** — T2 models its UDS hardening/sequencing on `UdsServerEndpoint`'s behaviour; needs root-cause-and-fix (why the `bf5f1db95` fix didn't survive `415e81570`, and re-verification it doesn't regress again), not a re-run-and-hope check |
| T3 mutual authentication for the wire-handoff channel (scope narrowed, §5.2) | T1 (needs a real child), T2 (needs a real wire-handoff socket); `SOW-UDS-JERI-Increment-2-Peer-Authentication.md` (candidate mechanism (a), itself unbuilt) | consumes / may block on |
| T4 admin export onto the SSLEngine/TLS+SPIFFE keystone (decided, §5.2) | `net.jini.jeri.ssl.SslServerEndpoint`/`ConnectionContext`/`AuthManager` (reused, landed, already-validated — `SslEngineRoundTripTest`); `SubProcessPolicyAdmin`/`AdminPrincipalAuthenticator` (landed, reused unchanged); Remaining-Work §6 `getGrants()` residual; **no longer depends on T2/T3** | **resolves** that residual as a side effect |
| T5 concrete launcher + bootstrap | T1–T4; `SubProcessReconstructionServer`/`SubProcessWireHandoffImpl` (landed, reused unchanged); `SubProcessLocalPolicyAdmin` (landed, reused unchanged) | glues together |
| T6 process lifecycle | T5; `SubProcessHandle`'s existing DGC-analogue model (landed, extended with a new signal, not replaced — **new `RetirementReason` constant for an OS-observed crash is an explicit T6 deliverable**, since `SubProcessHandle`'s `RetirementReason` enum today has only `CLEAN_CALL_PROCESSED`/`LEASE_EXPIRED`, no "torn down due to an observed OS crash" value); `SubProcessGrantOrchestrator`'s anti-replay gate (landed) | **new interaction, not yet resolved** — see §7 Q3 |
| T7 platform/module scope | none — should land first | **gates** T1, T2, T4 |
| T8 system-level adversarial pass | T1–T6 | verifies |
| T9 documentation closeout | T1–T8; `SOW-Smart-Proxy-Isolation-Remaining-Work.md` §6 item 1 (the "genuinely open" bullet this document, once landed, closes) | describes reality |

---

## 5. Transport reuse assessment: wire-handoff channel (unchanged) and admin channel (resolved)

### 5.1 Wire-handoff channel (T2/T5): `net.jini.jeri.uds` reuse assessment, unchanged by this edit

Investigated, not assumed: how much of the real launcher's UDS bring-up can reuse
`net.jini.jeri.uds` versus needs its own lighter-weight handling. **This subsection is unaffected
by §5.2's admin-channel resolution below** — the wire-handoff channel was never a candidate for the
SSLEngine/TLS+SPIFFE keystone move; it keeps its existing hand-rolled protocol for the reasons
already settled when T4(wiring) built it.

**What `net.jini.jeri.uds` already provides, confirmed by reading `UdsEndpoint.java`,
`UdsServerEndpoint.java`, `UdsPaths.java`:**

- A full JERI `Endpoint`/`ServerEndpoint` pair (`UdsEndpoint`/`UdsServerEndpoint`) — connection
  management, `ConnectionManager`-mediated pooling/reuse, the full JERI multiplexing/invocation
  stack above the byte transport, `@AtomicSerial` wire form for the endpoint objects themselves.
- Already-hardened socket-path validation (`UdsPaths.validate` — abstract-namespace rejection,
  embedded-NUL rejection, `sun_path` byte-ceiling measured in the platform-native encoding) shared
  identically by client and server so the two checks cannot drift (that class's own stated
  purpose). **Corroborated by both board seats: `UdsPaths` and `validate` are package-private**
  (`net.jini.jeri.uds`) — T2 cannot call this utility from a new module as-is; promoting it to
  public (or adding an equivalent public accessor) is now an explicit T2 acceptance criterion (§3,
  §4).
- Already-hardened owner-only permission application on the server side (`UdsServerEndpoint`,
  POSIX mode `rwx------` with an ACL fallback, applied fail-closed with the unlink-on-error
  discipline G6 names) — real, adversarially-relevant machinery, not boilerplate.
- A **defined but explicitly unbuilt** peer-authentication seam (the class javadoc's own "Identity
  seam (Phase 3 item 2 — RESOLVED per Peter)" section): the connection-setup hooks
  (`writeRequestData`/`processRequestData`) exist and are named as where SPIFFE workload-SVID
  presentation/verification belongs, but are no-ops today, "exactly as they are for plaintext
  TCP." (This is the general-purpose UDS transport's own increment-2 seam — orthogonal to §5.2's
  admin-channel move, and still relevant to T3's wire-handoff-channel authentication question,
  §7 Q1.)
- **`UdsEndpointRoundTripTest` is currently fully disabled**, not merely "disabled in this
  environment" as an earlier draft of this section characterized it: every `@Test` is commented
  out (`jgdms-jeri/src/test/java/net/jini/jeri/uds/UdsEndpointRoundTripTest.java`), and it was
  disabled *again* by commit `415e81570` after having been re-enabled and genuinely fixed by
  commit `bf5f1db95` (root cause: a `NOFOLLOW` chmod cannot `open()` an `AF_UNIX` socket special
  file — `ENXIO` — so the owner-only permission step always failed on a real bound socket).
  `SOW-Remaining-DER-ProxyTrust-Followups.md` §"known accepted..." still claims `UdsEndpointRoundTripTest`
  17/17 and `UdsConstraintEnforcementTest` 7/7 — **that claim is stale**; both board seats
  independently confirmed the actual file state by reading it, not by trusting the other document.
  This is now its own explicit dependency-ledger line (§4), gating T2: it needs root-cause-and-fix
  (why the `bf5f1db95` fix didn't survive `415e81570`, a commit whose stated purpose — DER
  serializer/AtomicSerial fixes — had no obvious reason to touch this file at all), not a
  re-run-and-hope check.

**Recommendation, stated as a recommendation, not a decision, for the wire-handoff channel only:**

- **Path validation and owner-only permission logic (`UdsPaths`, and the POSIX-mode/ACL-fallback
  sequencing in `UdsServerEndpoint`): reuse directly, low risk, no reason to reimplement.** These
  are self-contained utilities, not the full `Endpoint` stack, and already encode a real, tested
  lesson (the G6 unlink-on-error war story).
- **The wire-handoff channel (business calls): keep the existing hand-rolled `WireFraming`/
  `WireHandoffCodec` protocol, unchanged.** `SubProcessWireHandoffImpl`/
  `SubProcessReconstructionServer` already implement and adversarially test this over a bare
  `ByteChannel`; T5's job is only to supply that channel a live socket, not to change the protocol
  riding it. Re-deciding this would be re-opening a settled, board-reviewed decision for no new
  reason.

### 5.2 Admin channel (T4): resolved — SSLEngine/TLS 1.3 + SPIFFE keystone, not UDS/JERI

**§7 Q2 as originally framed ("reuse `jgdms-jeri`'s full JERI/UDS export stack, or extend the
hand-rolled `WireFraming` protocol") was itself the wrong question.** The security-seat board
review found a structural reason `net.jini.jeri.uds` can never serve the admin channel, UDS-vs-
hand-rolled framing notwithstanding — and Peter has ruled on the resolution.

**Why UDS/JERI-stack reuse is ruled out, not merely deprioritized:**
`SubProcessPolicyAdmin.buildAdminConstraints()`
(`jgdms-pref-class-loader/src/main/java/au/net/zeus/jgdms/loader/isolation/SubProcessPolicyAdmin.java:96-106`)
fixes the admin channel's `MethodConstraints` as a **requirement**, not a preference:

```java
InvocationConstraints ic = new InvocationConstraints(
    new InvocationConstraint[] {
        Integrity.YES,
        ClientAuthentication.YES,
        new ClientMinPrincipal(adminPrincipal),
        ServerAuthentication.YES
    },
    null);
```

`net.jini.jeri.uds.Constraints` (`jgdms-jeri/src/main/java/net/jini/jeri/uds/Constraints.java`)
only ever registers the **negative** variants of exactly those three constraints as supported —
`Integrity.NO` (`:160`), and `ClientAuthentication.NO`/`ServerAuthentication.NO`/`Delegation.NO`
(`:179-181`). `Integrity.YES`/`ClientAuthentication.YES`/`ServerAuthentication.YES` have no entry
in `supportedValues` at all, so `Constraints.Distilled.addConstraint` (`:325-364`) hits
`NO_SUPPORT` for each and throws `UnsupportedConstraintException` on the very first one checked
(`Integrity.YES`, per the fixed iteration order of `buildAdminConstraints`'s array). **This is by
permanent design, per the class's own javadoc, not a temporary Increment-1 gap Increment 2's
`SO_PEERCRED` work will close:** `Integrity.YES` is deliberately *never* claimed by this transport
even in a hypothetical future increment, because a transport-level `FULL_SUPPORT` claim would tell
the object layer decode-integrity is already handled and could let it bypass the DER/JOSS gate
(`Constraints.java:69-80`) — UDS's integrity story is permanently "the object layer already
provides it, the transport does not separately claim it," which is architecturally incompatible
with a caller that *requires* `Integrity.YES` be satisfiable as a transport-negotiable constraint.
`ClientAuthentication.YES`/`ServerAuthentication.YES` are documented as deferred to Increment 2
(`:93-96`, `:99-106`), but Increment 2 (`SOW-UDS-JERI-Increment-2-Peer-Authentication.md`) adds
`SO_PEERCRED`/SPIFFE-SVID *local-peer* verification — it does not add a `ClientMinPrincipal`-
checkable authenticated principal into the JERI constraint model the way TLS client-certificate
authentication does, so even a completed Increment 2 would not by itself make
`buildAdminConstraints()`'s exact requirement set satisfiable over UDS. **Net effect: exporting
`SubProcessPolicyAdmin` over `UdsServerEndpoint` throws `UnsupportedConstraintException`
immediately on `Integrity.YES`, today and permanently** — this was the wrong framing to leave open
in §7 Q2 as originally drafted.

**Resolution (Peter's ruling): the admin channel reuses this repo's existing SSLEngine/TLS 1.3 +
SPIFFE keystone**, `net.jini.jeri.ssl` (`SslEndpoint`/`SslServerEndpoint`/`SslEndpointImpl`/
`SslServerEndpointImpl`, `jgdms-jeri`) — a merged, already-validated transport, not new work T4
has to build from scratch:

- **It actually satisfies the admin channel's exact fixed requirements.**
  `net.jini.jeri.ssl.ConnectionContext` (`jgdms-jeri/.../ssl/ConnectionContext.java:264-306`)
  registers `Integrity.YES`, `ClientAuthentication.YES`, and `ServerAuthentication.YES` as
  satisfiable constraints (backed by real TLS 1.3 handshake integrity and mutual X.509/SPIFFE
  certificate authentication), and `Utilities.java:503-509` names the module's own
  `INTEGRITY_REQUIRED` constraint set built from `Integrity.YES`. No constraint `buildAdminConstraints()`
  fixes is unsupported.
- **It is exportable the ordinary way.** `SslServerEndpoint` (`jgdms-jeri/.../ssl/SslServerEndpoint.java:318`)
  is a `public final class ... implements ServerEndpoint`, built for exactly the
  `BasicJeriExporter`/`AtomicILFactory` pattern the wiring SOW's own admin-export reasoning already
  assumed — the same shape `UdsServerEndpoint` would have offered, minus the constraint mismatch.
- **It already produces a bound caller `Subject` during dispatch, confirmed by reading the code, not
  assumed.** Server-side, `net.jini.jeri.BasicInvocationDispatcher.invokeWithClientSubject`
  (`jgdms-jeri/.../BasicInvocationDispatcher.java:2124-2246`) binds the TLS/SPIFFE-authenticated
  caller identity into the executing thread — via `Subject.doAs` (standard-JDK worker subject,
  `:2215-2230`) or directly against the process-ambient `WorkerSubject` established by
  `Subject.processWorker` on DirtyChai (`:2231-2246`) — *before* the dispatched method body (and
  therefore `AdminPrincipalAuthenticator.requireAdmin(...)`) ever runs. The caller's own principals
  arrive via the server-side `ClientUserSubject` context element, populated during
  `SslServerEndpointImpl`'s certificate verification (`AuthManager.getSubject()`,
  `jgdms-jeri/.../ssl/AuthManager.java:130-132`, itself SPIFFE-aware via
  `Utilities.spiffeWorkerSubject()`). **This likely resolves, or substantially narrows, the
  separate board finding that "neither T3 candidate produces the bound admin `Subject`" — but only
  for the admin channel.** The wire-handoff channel's own authentication need (§5.1, T3's narrowed
  row) is a distinct problem with its own answer still open at §7 Q1; the keystone's bound-`Subject`
  property does not extend to a channel that does not use it.
- **It closes the `getGrants()` marshalability residual the same way UDS reuse would have.**
  Exporting over a real JERI `ServerEndpoint` still inherits JERI's own DGC/at-most-once semantics
  and real wire marshalling for `PolicyAdmin.getGrants()`'s `PermissionGrant[]` return value
  (§6 below) — the property that made JERI-stack reuse attractive in the first place is preserved,
  just via `net.jini.jeri.ssl` instead of `net.jini.jeri.uds`.
- **It does not inherit UDS's disabled-test-suite risk.** `SslEngineRoundTripTest`
  (`jgdms-jeri/src/test/java/net/jini/jeri/ssl/SslEngineRoundTripTest.java`) has its `@Test`
  methods live and enabled, unlike `UdsEndpointRoundTripTest` (§5.1, §4). T4's own board review
  should still confirm this transport's tests actually cover the specific constraint combination
  `buildAdminConstraints()` requires, but T4 is not starting from a known-broken test suite the way
  admin-over-UDS would have.
- **Cost, carried forward unchanged from the original framing:** exporting over `net.jini.jeri.ssl`
  still requires a new reverse dependency — `jgdms-pref-class-loader` does not depend on
  `jgdms-jeri` today — same as UDS reuse would have needed; T7(b)'s "new module depending on both"
  recommendation is unaffected by which `net.jini.jeri` sub-package T4 targets (§3 T7 row, Notes).

**What does *not* change:** the wire-handoff channel (T2, business calls) keeps the existing
hand-rolled `WireFraming`/`WireHandoffCodec` protocol exactly as §5.1 describes — that part of the
original reasoning (`net.jini.jeri.uds`'s own disabled test suite made hand-rolling the safer
choice for the business channel) is untouched by this admin-channel decision. Only the admin
channel moved, from an option this document previously left open to a specific, ruled-on transport.

---

## 6. What this makes load-bearing, from the three residual items flagged in Remaining-Work §6

`SOW-Smart-Proxy-Isolation-Remaining-Work.md` §6 named three "non-blocking residuals, explicitly
flagged and accepted by the relevant boards, not silently dropped." This document's work changes
the status of each:

1. **`getGrants()` marshalability gap.** Remaining-Work §6 states this explicitly could not be
   evaluated because "there is no such transport yet." **This document's T4 is the task that
   creates that transport** — now specifically the SSLEngine/TLS+SPIFFE keystone (§5.2) — and per
   §5.2 above, this residual becomes a **real, testable acceptance criterion of T4**, not a
   separate follow-on task, but a required part of T4's own board review. A T4 that lands without
   confirming `PermissionGrant[]` actually round-trips across the live admin channel has not
   actually closed this residual, whatever else it accomplishes.
2. **`DecodeDepthGuard` blind spot on proxy-shaped payload prefixes.** Judged acceptable in
   Remaining-Work §6 because the `catch (Throwable)` backstop degrades a worst-case miss to a
   clean, reported failure rather than a crash — a property of the wire-handoff *codec*, not of
   the *transport* carrying it. **This document's work does not change that judgment**: T5 supplies
   a real `ByteChannel` to the same, unchanged `WireFraming`/`WireHandoffCodec`/`DecodeDepthGuard`
   stack, so the blind spot's severity and mitigation are unaffected by whether the channel is a
   test fake or a real UDS socket. Carried forward unchanged, not newly load-bearing.
3. **Anti-replay generation-map race/growth in `SubProcessGrantOrchestrator`.** Judged acceptable
   in Remaining-Work §6 because "verdict application is not high-frequency" and the realistic
   threat is sequential replay, not concurrent racing — a judgment about *call pattern*, made
   before any real process lifecycle existed to stress it. **This document's T6 makes the
   *growth* half of this residual newly load-bearing in a way Remaining-Work §6 did not
   anticipate**: a real subprocess can now crash and be respawned under the same pooling key,
   and §3's T6 row identifies a genuinely new interaction (crash-respawn indistinguishable from
   replay to the orchestrator's freshness gate) that Remaining-Work §6's original acceptance
   judgment did not consider, because no code existed yet that could crash. T6 must either extend
   that original judgment with an explicit rationale covering the crash-respawn case, or the
   orchestrator's own anti-replay mechanism needs a narrow, deliberately-scoped extension (e.g. an
   explicit "this pooling key's subprocess was just observed to restart" event that resets *only*
   that key's generation entries) — **not decided here, see §7 Q3.**

---

## 7. Open items / open questions — for Peter to rule on, not guessed at here

This document deliberately states these as open rather than picking an answer and presenting it
as settled, per the standing instruction that pre-release design decisions are provisional and
revisable on good cause, and per this document set's own convention of flagging genuine
uncertainty rather than rounding it up to a recommendation-as-decision.

1. **Parent↔child mutual authentication mechanism for the wire-handoff channel (T3, §3's most
   consequential open row — scope narrowed by item 2's resolution below: this question no longer
   covers the admin channel, only the wire-handoff channel).**
   Three candidates identified — (a) `SO_PEERCRED`-equivalent process-ancestry proof (strongest,
   but the exact mechanism `SOW-UDS-JERI-Increment-2-Peer-Authentication.md` already scoped and
   left unbuilt/deferred/Linux-only) — concretely, since the child listens/accepts and the parent
   connects for this channel (T2's stated design fact), it is the connecting **parent** that would
   query the credential of the child it just connected to and cross-check it against the PID
   `ProcessBuilder.start()` already gave it, (b) a one-time shared secret handed to the child via
   stdin at spawn time (cheapest, has a direct precedent in this same codebase's Phoenix
   `Activation` code, but is a new bespoke protocol surface), (c) bare owner-only socket-file
   permissions as the entire trust boundary (matches UDS increment 1's own accepted baseline, adds
   no new mechanism, but was explicitly scoped as an *interim* posture there, not a permanent one).
   **Does this document's launcher need to wait for increment 2, or is (b) or (c) an acceptable
   interim posture for a first real launcher, explicitly flagged as interim the way increment 1's
   own baseline was?**
2. **RESOLVED — admin-channel transport (T4, §5.2). Peter's ruling: the admin channel reuses this
   repo's existing SSLEngine/TLS 1.3 + SPIFFE keystone (`net.jini.jeri.ssl`), not
   `net.jini.jeri.uds`.** The question as originally framed here ("reuse `jgdms-jeri`'s full
   JERI/UDS export stack, or extend the hand-rolled `WireFraming` protocol") was itself wrong: the
   security-seat board review found `SubProcessPolicyAdmin.buildAdminConstraints()`'s fixed
   `Integrity.YES` requirement is permanently unsatisfiable over `net.jini.jeri.uds` by that
   transport's own by-design constraint table (`Constraints.java`, only the `.NO` variants of
   `Integrity`/`ClientAuthentication`/`ServerAuthentication` are ever registered), so UDS reuse was
   never actually a live option — see §5.2 for the full evidence trail (exact file/line citations,
   what the SSL/SPIFFE keystone already provides including a bound caller `Subject` via
   `Subject.doAs`/`Subject.callAs`, and what stays the same for the wire-handoff channel). Left in
   this list, marked resolved rather than deleted, so a reader following a numbered citation
   elsewhere in this document (or a sibling document) to "§7 Q2" still finds the question and its
   answer in place.
3. **Crash-respawn vs. anti-replay generation-map interaction (T6, §3's T6 row, §6 item 3).** Should
   a crash-respawned subprocess automatically have its prior verdict ceiling re-applied (requiring
   some form of generation-map reset scoped to the restart event), or should a crash simply leave
   the freshly-spawned subprocess at its minimal seeded policy (T5's acceptance criterion) until
   whatever caller originally drove `applyVerdictCeiling` notices the subprocess is new and
   re-drives it on its own initiative? The second option is simpler and arguably more correct
   (a crash-respawned subprocess *is*, architecturally, a new instance; treating it as entitled to
   its predecessor's grants without a fresh verdict check could itself be a privilege-restoration
   bug of a different kind), but has an availability cost (a caller that doesn't proactively
   re-verdict leaves a respawned subprocess at minimal privilege, possibly breaking the business
   flow it was spawned to serve) that needs an explicit owner decision, not a default.
4. **Child-JVM classpath/module-path scope: full application classpath, or a curated, minimal
   isolation-subprocess-only classpath (T1)?** A minimal classpath shrinks the subprocess's own
   TCB (fewer classes available to whatever gets reconstructed/loaded inside it) but needs its own
   provisioning story distinct from the parent's — related to, but not identical to, the T5
   interface-jar-distribution question `SOW-Smart-Proxy-Isolation-Wiring.md` T5 already resolved
   for the *client's* classpath (deploy-time-provisioned `-api.jar` dependencies); the subprocess's
   own classpath needs whatever the reused legacy `resolve()`/codebase-download machinery
   (`SubProcessReconstructionServer.reconstruct()`, which literally calls
   `PreferredProxyCodebaseProvider.resolve()`) requires to keep working unchanged, which this
   document has not fully inventoried against a minimal-classpath hypothesis.
5. **Spawn-rate / concurrent-subprocess resource ceiling (§2.2, resource exhaustion).** Is a bound
   on concurrently-live subprocesses in this document's own scope (a T1/T5 acceptance criterion),
   or a deliberately separate follow-on once the real launcher's actual resource footprint (memory/
   startup-time per child JVM) is empirically known? This document does not have that empirical
   data yet — a real number is needed before a sensible ceiling can be chosen, which argues for
   deferring the *specific bound* but not for deferring the *decision that a bound will be needed*.
6. **Platform scope: Linux-only v1, or cross-platform from the start (T7(a))?** This document
   recommends Linux-first given T3's strongest candidate mechanism's own Linux-only status and this
   repo's established UDS-story convention, but recommends, does not decide — see T7's row for the
   full reasoning either way.
7. **New module vs. existing module placement (T7(b))?** This document recommends a new module
   depending on both `jgdms-pref-class-loader` and `jgdms-jeri` — now unconditionally, since T4's
   admin-channel transport is settled as `net.jini.jeri.ssl` (§5.2, item 2 above), not a choice
   between a `jgdms-jeri`-dependent option and a `jgdms-jeri`-independent one — but this document
   still recommends, does not decide, and the module's actual name/coordinates are entirely
   unstated here.

---

*Status: DRAFT. No production code was written or modified in producing this document. Every
"recommendation" above is explicitly non-binding language, not a decision recorded on Peter's
behalf — §7 exists precisely so a future reader (including a future AI session with no memory of
this one) does not mistake this document's own reasoning-toward-a-recommendation for an actual
ruling.*
