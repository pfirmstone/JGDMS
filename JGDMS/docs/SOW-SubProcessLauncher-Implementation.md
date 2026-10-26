# Scope of Work — SubProcessLauncher: real OS-process spawning for smart-proxy isolation

- **Drafted:** 2026-08-29.
- **Status:** DRAFT — not started. Scopes the one prerequisite gap `SOW-Smart-Proxy-Isolation-
  Remaining-Work.md` explicitly named as belonging to "whoever picks up building the real launcher
  next": the production `fork`/`exec` + UDS bring-up implementation of `SubProcessLauncher`, which
  today is `UnsupportedSubProcessLauncher` — a stub. T1–T5 of the smart-proxy isolation work (routing,
  pooling, authentication, grant application, wire handoff) are landed and board-reviewed, but tested
  against a launcher *abstraction*, never a real spawned OS process. Nothing in this document reopens
  that landed work; it closes the one thing left unbuilt underneath it.

- **Companions — read in this order before starting:**
  1. `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` — orientation and the four-document index.
  2. `SOW-Smart-Proxy-Isolation-Wiring.md` — T1/T2/T3 (landed): routing, subprocess spawn/pool/track
     scaffold, `ProxySerializer` wire hint.
  3. `SOW-SubProcessDynamicPolicy.md` — the post-SCAP-verdict permission-ceiling mechanism a freshly
     spawned process needs, which this launcher must supply the process for.
  4. `SOW-T4-Wire-Handoff-Protocol.md` — the byte-level handoff/authentication scaffold already built
     against the launcher abstraction; this document's launcher must satisfy that protocol, not
     redesign it.
  5. `SOW-Smart-Proxy-Isolation-Remaining-Work.md` — §"Open items carried forward" item 1 is this
     document's origin; item 3's unbounded generation-map residual is a candidate for this launcher's
     teardown hook (see Task 5).
  6. `DirtyChai/PROCESS_ISOLATION.md`, section "Activation Groups as OS Process Boundaries" and the
     newer "Proposed: Per-Remote-SPIFFE-ID One-Shot Activation Groups for Downloaded Proxy Execution."
     Phoenix already spawns activation-group JVMs via `systemd-run` (wrapping `ProcessBuilder`, not a
     direct fork), already applies cgroups and a seccomp profile that denies `setuid`/`setgid` outright,
     and already places groups in their own network namespace. This launcher is not a new mechanism —
     it is the same primitive, applied with the lifecycle policy the newer section describes.

## Background — why this is scoped separately from T1–T5

The existing landed machinery already answers more of the hard design questions than a fresh reader
might expect: subprocesses are pooled one-per-distinct remote SPIFFE principal (not per-object), the
local process never loads a proxy's bytecode at all (only a `java.lang.reflect.Proxy` stub forwarding
over UDS), and lifecycle already reuses JERI's DGC (dirty-set/lease) machinery — a subprocess lives as
long as something holds a live reference to what it hosts, then is torn down. That lease-based lifecycle
is confirmed to already be the right shape for "one subprocess serves one principal's active session,
never reused across a different principal or a new session" — this is not a new decision this SOW needs
to make; verify it against the landed T1–T5 code (Task 4) rather than redesigning it.

What is missing is the part underneath all of that: an actual OS process gets created, with a real UDS
endpoint the wire-handoff protocol can bring up, and — per the newer isolation-policy work — a distinct
uid so a compromise that bypasses SecurityManager entirely (a memory-safety bug, a JIT bug, an SM bypass
— not merely untrusted bytecode reaching a guarded API, which the guards already cover) is contained by
the kernel rather than depending on any in-process guard holding.

## Tasks

### Task 1 — Real fork/exec via the existing Phoenix launch pattern, not a new one

**Priority:** High
**Files:** the `SubProcessLauncher` implementation (currently `UnsupportedSubProcessLauncher`);
consult Phoenix's existing group-JVM launch code for the `systemd-run`-wrapping-`ProcessBuilder`
pattern already in production use.

Implement real process creation by wrapping the target `java` invocation in `systemd-run`, the same
way Phoenix already launches activation-group JVMs — not a bare `ProcessBuilder.start()`. This gets
cgroup resource limits and the existing seccomp profile applied for free, by reuse rather than
duplication.

### Task 2 — Distinct uid per launched subprocess, assigned externally

**Priority:** High
**Depends on:** Task 1.

Add a per-launch uid (e.g. `systemd-run --uid=<per-principal-uid>` / the transient unit's `User=`)
assigned by `systemd-run`'s own privilege at process-creation time. **The JVM launching the subprocess
must never itself call `setuid`/`setresuid` or request any elevated privilege to perform the switch —
that capability does not exist in stock OpenJDK, and this design deliberately does not require it.**
The existing Phoenix seccomp profile already denies `setuid`/`setgid` to group JVMs; confirm the same
profile is applied here rather than carved out.

Open question to resolve during implementation, not assumed here: where the uid pool/allocator lives
(a fixed range handed to `systemd-run` per launch vs. a small external allocator service) — pick
whichever integrates with existing deployment tooling rather than inventing a new one.

### Task 3 — UDS bring-up satisfying the existing wire-handoff protocol

**Priority:** High
**Depends on:** Task 1.
**Files:** integration point with `SOW-T4-Wire-Handoff-Protocol.md`'s existing scaffold.

Bring up the real UDS endpoint the already-built authentication/handoff scaffold expects. This task is
integration, not design — the wire protocol, the authentication scaffold, and the grant-application
path (`SubProcessDynamicPolicy`) are already built and board-reviewed against the launcher abstraction.
Do not redesign the handoff; make the real launcher satisfy the interface the existing tested code
already assumes. Flag explicitly (do not silently patch around) any point where the real launcher
cannot satisfy that existing interface as specified — that is a finding for review, not a launcher
implementation detail to route around quietly.

### Task 4 — Confirm the DGC lease-based lifecycle actually delivers one-subprocess-per-session

**Priority:** Medium
**Depends on:** Tasks 1–3 (needs a real process to observe against).

Verify, against the real launcher, that a subprocess is never reused across a different remote SPIFFE
principal, and never kept alive/reused for a *new* session from the same principal after the previous
session's leases have all expired — i.e. that "torn down once nothing holds a live reference" in
practice means what `PROCESS_ISOLATION.md`'s teardown-after-one-use analysis assumes. If the existing
DGC-based teardown allows any reuse pattern broader than that, document the actual behavior precisely
and flag the gap — do not assume alignment without checking.

### Task 5 — CDS/Leyden AOT cache for launch latency; explicit CRaC exclusion

**Priority:** Medium
**Depends on:** Task 1.

Session-scoped subprocess lifecycle (Task 4) means launch latency is on the critical path far more
often than a long-lived group JVM's would be. Build the `systemd-run` launch to use a CDS (or Leyden
AOT cache) archive, memory-mapped read-only and shared across launched subprocesses.

**Hard constraint, not a tuning choice:** the archive may contain only build-time-known JDK and
DirtyChai/JGDMS classes — never a downloaded proxy's classes. CDS's own mechanics support this (a
production run may only append to the training-run classpath, not substitute into it), but verify the
build/training-run configuration doesn't accidentally capture anything proxy-specific.

**Explicit non-goal:** do not use CRaC checkpoint/restore as a faster alternative. A CRaC checkpoint
captures live JVM memory — heap, open file descriptors, in-flight state — and restoring a checkpoint
taken from a subprocess that already served one principal would carry that principal's runtime state
into whatever restores from it, reintroducing exactly the cross-tenant leakage Task 4's teardown
guarantee exists to prevent. This is a correctness constraint on the implementation, not a performance
suggestion to weigh against others.

### Task 6 — Teardown hook for the unbounded generation-map residual (opportunistic, not required)

**Priority:** Low
**Depends on:** Tasks 1, 4.

`SOW-Smart-Proxy-Isolation-Remaining-Work.md` §"Open items carried forward" item 3 notes
`SubProcessGrantOrchestrator`'s anti-replay generation map has no hook to reclaim an entry when a
subprocess is torn down and unregistered from `SubProcessAdminRegistry` — accepted as a residual for
long-lived deployments. Since this launcher now owns real teardown, wire a reclaim call into it if it
fits naturally; if it would require touching `SubProcessGrantOrchestrator` itself, leave it as a
separately-flagged finding rather than pulling that file into this SOW's scope.

## Non-goals

- Do not modify T1–T5's landed routing, pooling, authentication, grant-application, or wire-handoff
  machinery except where Task 3 finds a genuine interface mismatch the real launcher cannot satisfy —
  and even then, flag it rather than patching it as part of this work.
- Do not redesign the wire-handoff protocol, the grant/permission-ceiling model, or the DGC lease
  mechanism. This SOW builds the process boundary underneath them.
- Do not add any new DirtyChai SecurityManager permission, guard, or Panama FFI capability. Task 2 is
  specifically designed to need none — uid assignment is external, via `systemd-run`'s existing
  privilege, not a new in-JVM capability. If implementation reveals that assumption doesn't hold,
  stop and flag it rather than adding a new privileged primitive to close the gap.

## Constraint on how this SOW is completed

This repository's AI contribution policy (`CLAUDE.md`, `AI_POLICY.md`) applies to whichever agent picks
this up, human or AI, without exception. An AI agent completing this SOW analyzes, verifies against the
real source, and produces a concrete implementation plan and findings — it does **not** generate or
commit the `SubProcessLauncher` implementation, touch any file on this repository's "do not modify"
list, or open a PR. The deliverable is the same shape as this document and its companions: verified
analysis, an explicit task/finding breakdown, and open questions honestly labeled as open — for a human
to implement and review.
