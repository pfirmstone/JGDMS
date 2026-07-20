# Scope of Work — Smart-proxy isolation: remaining work after the 2026-07-19 landing wave

- **Drafted:** 2026-07-19.
- **Status (updated 2026-07-20): T1-T6 LANDED, T7 (this document's own closeout task) in progress —
  this edit is part of executing it.** T1 (grant-application backend), T2 (wire-protocol handoff), T3
  (caller-side wiring), T4 (system-level adversarial pass), and T5 (`DerProxySerializer` follow-up) all
  landed 2026-07-20, each board- or single-reviewer-cleared per its own effort/risk rating below. T6 (QA
  implications investigation) landed 2026-07-20, confirming Peter's "mostly invisible" assessment for
  this session's own new machinery, with one lead — whether the T2 platform-level fix affects existing
  QA test correctness via `qa/.../FakeArgument.java` — flagged as a separate, narrower question still
  being chased as of this writing (see §5 below). **T4's adversarial pass additionally found and closed
  three real defects not originally scoped by this document at all** — a pre-existing confused-deputy
  bypass in `AdminPrincipalAuthenticator`, a missing grant-revocation mechanism, and a missing replay
  protection — plus a QA-policy hardening pass the first of those three findings required. These are
  documented as genuine additions, not retroactively folded into the original T1-T7 breakdown below; see
  §"Additions found during T4, not originally scoped" for the full account. **What remains genuinely
  open, not overclaimed as done:** see §"Open items carried forward" at the end of this document —
  `SubProcessLauncher`'s real OS-process spawning is unbuilt, and zero production code currently
  constructs any of this session's new isolation machinery. This status line summarizes; the task table
  in §3 is authoritative for each item's specifics.
- **Origin:** A single long session landed T1 (routing branch), T3 (`ProxySerializer` interface-name
  wire hint), and T2 (subprocess spawn/pool/track, including a full authentication/dispatch scaffold)
  from `SOW-Smart-Proxy-Isolation-Wiring.md`, plus `SubProcessDynamicPolicy` T1 (namespace relocation)
  and T2 (verdict-to-permission-set function), plus two adjacent security/process fixes (a decode-DoS
  bug in `AtomicMarshalInputStream`, and CI enforcement for the API-compat/serial-schema gates). This
  document scopes what's left, verified against the actual code as it stands on trunk — not assumed
  from the design docs alone (see §1 for exactly how each item was confirmed).
- **Companions — read in this order:**
  1. `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` — orientation, if you haven't read the whole
     document set before.
  2. `SOW-Smart-Proxy-Isolation-Wiring.md` — T1/T2/T3 status (landed), T4/T5/T6 (T5 resolved, T4 and T6
     are this document's items 2 and 6).
  3. `SOW-SubProcessDynamicPolicy.md` — T1/T2 status (landed), T3/T4/T5/T6 (this document's items 1, 3,
     4, and part of 6). **Its status header and T3 row, once stale, were fixed 2026-07-20 as part of
     this document's own T7** — no longer a trap for a fresh reader.
  4. This document.
- **What "landed" means here, precisely (verified by direct code reading, not inferred) — as of
  2026-07-19, i.e. this is this document's own starting point, describing what T1-T6 below closed.
  Every bullet immediately below is now historical: it describes the gap this document's T1-T6 existed
  to close, not the state of the code after they landed.** See §3's task table (the `LANDED` annotations
  on each row) for the 2026-07-20 state these bullets predate, and §5/§6 near the end of this document
  for what T4 additionally found and what remains genuinely open.
  - `PolicyAdmin`/`SubProcessPolicyAdmin`/`AdminPrincipalAuthenticator`/`HostedProxyGuard`/
    `SubProcessAdminRegistry`/`SubProcessAdministrable` (all in
    `jgdms-pref-class-loader/src/main/java/au/net/zeus/jgdms/loader/isolation/`) implement
    **authentication and re-gating only** — `SubProcessPolicyAdmin.getSubProcessPolicyAdmin()` returns a
    dynamic proxy (`GuardedPolicyAdminHandler`) that re-checks the admin principal on every call, then
    does `method.invoke(backing, args)` where `backing` is an **injected `PolicyAdmin` with no real
    implementation anywhere in the codebase** — the only `implements PolicyAdmin` in the whole repo is a
    test double (`IsolationSecurityCriticalTest.TrustedPolicyBacking`) that increments a counter.
  - `VerdictPermissionMapper` (`jgdms-platform/src/main/java/au/net/zeus/jgdms/api/policy/`) is a pure,
    tested, **completely unwired** function — repo-wide grep finds exactly two references (the class and
    its own test). Nothing calls it.
  - `SubProcessWireHandoff` is a plain interface with an `UnsupportedOperationException`-throwing default,
    explicitly mirroring T1's placeholder pattern — no wire-handoff logic exists.
  - No production code anywhere references `DynamicPolicyProvider` from the isolation package.
- **No production code was written or modified in producing this document.**

---

## 1. What this closes, precisely

1. **The real grant-application backend behind `PolicyAdmin`** (`SubProcessDynamicPolicy` T3, part (c) —
   the only unbuilt part of that task; parts (a)/(b) landed in Wiring T2, see the header above).
2. **The wire-protocol handoff** (`SOW-Smart-Proxy-Isolation-Wiring.md` T4) — the actual UDS
   `MarshalledInstance` handoff `SubProcessWireHandoff` is a placeholder for.
3. **The caller-side glue** connecting a known SCAP/BAE verdict → `VerdictPermissionMapper` →
   `PolicyAdmin.grant(...)` (`SubProcessDynamicPolicy` T4).
4. **System-level adversarial verification** of the whole T2(wiring)→T3(c)→T4 chain as one mechanism,
   not each piece in isolation (`SubProcessDynamicPolicy` T5).
5. **The `DerProxySerializer` visibility + interface-hint follow-up** — flagged during T3's own
   adversarial review, still entirely unstarted, confirmed distinct from unrelated concurrent work
   already touching `jgdms-der/` files.
6. **QA implications investigation** and **documentation closeout** (both SOWs' own T6, plus fixing
   `SOW-SubProcessDynamicPolicy.md`'s stale status header and T3 row so a future reader isn't misled
   into thinking nothing has landed).

---

## 2. Security lens carried forward (not re-derived, cross-referenced)

The S1-S6 findings baked into the landed work (declaration-is-not-authority, decode-surface bounds,
DGC-ack-not-connection-close teardown, canonical fail-closed pooling keys, reject-on-load by type
identity not name) all still apply to the tasks below, since T4(wiring) and T3(c)/T4(SubProcessDynamicPolicy)
extend the same mechanisms rather than replacing them. Specifically:

- **T4(wiring)'s reconstruction path is where the SCAP/BAE verdict gate, `DeSerializationPermission
  ("ATOMIC")`, and endpoint-assigned `ResolutionContext` must all still run *inside* the subprocess** —
  moving reconstruction across a process boundary must not move it outside any gate the in-process path
  enforced before this architecture existed (G13, already stated as T4's own acceptance criterion in the
  wiring SOW — repeated here because it's the single highest-risk property in this whole remaining-work
  set).
- **T3(c)'s grant-application backend inherits T2's authentication boundary, and must not weaken it.**
  The backend applies a grant "verbatim, performing no independent judgment about what to grant" (T3's
  own acceptance criterion) — the actual permission-computation judgment already happened in
  `VerdictPermissionMapper` (T2 of that SOW, landed); T3(c) must resist the temptation to add its own
  policy logic at the application layer, which would create two places deciding "what" instead of one.
- **T4(SubProcessDynamicPolicy)'s caller-side wiring must target the handle via
  `SubProcessAdminRegistry`'s canonical pooling key** (already built), never a spoofable target-id
  parameter — this was T2(wiring)'s whole point in exposing that registry.

---

## 3. Execution plan — task breakdown (agent type · effort · risk)

Effort follows the project convention (security → `xhigh`, mechanical → `medium`); orchestration follows
*fresh implement → adversarial board for XHIGH*, matching the precedent set by this session's T1/T2/T3
and the readNewArray fix — all of which had a confirmed, real defect caught only by board review, not by
the implementing agent's own testing. Do not skip the board step for T2 or T4 below on the theory that
"the pattern is now established" — the TOCTOU bug in `SubProcessPool` was exactly this kind of
false confidence.

### Summary

| Task | Deliverable | Effort · risk | Review | Depends |
|------|-------------|----------------|--------|---------|
| **T1** · `PolicyAdmin` real grant-application backend (`SubProcessDynamicPolicy` T3(c)) | **LANDED 2026-07-20** (`SubProcessLocalPolicyAdmin`, commits `3711727b0`/`e0abc35fc`, merged `36ca3291b`). Board (3 seats) returned BLOCK on the first commit; `e0abc35fc` fixed a visibility gap, a HIGH universal-`Principal`-implies escalation, and a T1/T3 (this table's T1/T3) lease-wrapping shape mismatch before re-review passed clean. All 152 `jgdms-pref-class-loader` tests pass under the DirtyChai SM-capable JDK. Implement a real `PolicyAdmin` for the `backing` object `SubProcessPolicyAdmin` currently receives only a test double for. `grant(PermissionGrant)` calls the subprocess's own local `DynamicPolicyProvider`/`LeasedDelegation` (lease-scoped by default, per `SubProcessDynamicPolicy` §2's existing decision) verbatim — no independent judgment about *what* to grant, that's already `VerdictPermissionMapper`'s job. `refresh()`/`getGrants()` need real implementations too (find the existing `DynamicPolicyProvider` surface this should delegate to — don't invent a parallel grant-tracking structure). | **XHIGH** — novel authority-application mechanism, same class as the admin-authentication work T2 already got board-reviewed for | **parallel board** (2-3 adversarial) — probe: can a caller reach the real grant-application path without the T2 authentication gate in front of it (i.e. confirm this backend is *only* ever reached through `GuardedPolicyAdminHandler`, never directly); does a lease-scoped grant actually expire/revoke per `LeasedPermissionGrant`'s existing semantics; can `refresh()`/`getGrants()` leak grant state to an unauthenticated caller | none — can start immediately, independent of T2 below |
| **T2** · Wire-protocol handoff (`SOW-Smart-Proxy-Isolation-Wiring.md` T4) | **LANDED 2026-07-20** (`SubProcessWireHandoff`/`WireFraming`/`WireHandoffCodec`/`SubProcessReconstructionServer`, commit `967a59799`, merged `10ab8690f`). Two board-review rounds returned BLOCK before clean: round 1 found an uncaught client-side decode-failure path and an unbounded decode-recursion depth (a live `StackOverflowError`), fixed with a `catch(Throwable)` backstop and a new `DecodeDepthGuard`; round 2, while re-verifying that fix, found a separate and more severe bug — silent `Externalizable` field-loss in the shared `jgdms-platform` codec (`AtomicMarshalInputStream`/`ObjOutputStream`, two compounding root causes), root-caused and fully fixed, not merely contained (commit `8f3874a9b`, upgraded regression test `f32696b69`). Full detail in `SOW-T4-Wire-Handoff-Protocol.md` §10, the authoritative built-state document for this task. Implement `SubProcessWireHandoff` for real: client forwards the raw, still-marshalled `MarshalledInstance` over UDS to the principal's subprocess (never deserializing it, even partially); subprocess reconstructs it — behind the SCAP/BAE verdict gate, `DeSerializationPermission("ATOMIC")`, and endpoint-assigned `ResolutionContext`, all still enforced *inside* the subprocess — and replies with enough for the client to build its thin stub. Byte/framing-level specification, not just the mechanism-level description already in the wiring SOW. Must also enforce T2(landed)'s reject-on-load check (`HostedProxyGuard`) at this exact boundary, since this is where a hosted proxy's interface closure first becomes a resolved `Class` set. | **XHIGH** — the single concentrated object-reconstruction door for this whole architecture; same risk class as this session's BAE-adjacent decode-DoS work | **parallel board** (2-3 adversarial) — probe: a `MarshalledInstance` crafted to reconstruct outside a gate; a subprocess-originated call back toward the client (three-axes check: byte flow, request origination, authorization — state explicitly, don't assume); oversized/deeply-nested marshalled payload (the same allocate-before-validate class of bug T1's readNewArray fix just closed elsewhere — check this new path doesn't reintroduce it) | T2(wiring)'s subprocess machinery (landed) |
| **T3** · Caller-side wiring: verdict → ceiling → grant (`SubProcessDynamicPolicy` T4) | **LANDED 2026-07-20** (`SubProcessGrantOrchestrator`, commit `62e002668`). Single reviewer, clean, merged with no changes needed at build time — see T4's row below for two real defects a *later*, system-level pass found in this same class. The orchestrating logic that, once a verdict is known for a smart proxy about to run in a given subprocess, calls `VerdictPermissionMapper.computeSubProcessCeiling(...)` and pushes the result through `SubProcessAdminRegistry` → the target subprocess's `PolicyAdmin.grant(...)` (T1 above). Can be built and tested against a stub `PolicyAdmin`/stub subprocess before T1/T2 above are fully real, per the SOW's own note — but full end-to-end integration needs both, since "a verdict is known" is a fact T2 above's reconstruction path produces. | **HIGH** | single reviewer + integration test against a real T1 backend once it exists | T1 (needs a real `PolicyAdmin` to push into); T2 informs where "verdict known" actually fires from, for full integration (not for the stub-based build/test path) |
| **T4** · System-level adversarial pass | **LANDED 2026-07-20 — and not a clean pass: three real, previously-undiscovered defects found and fixed, one of them (below) outside this document's own original scope entirely.** (1) **Confused-deputy bypass in `AdminPrincipalAuthenticator`** (pre-existing code, outside this SOW's own original scope, fixed per explicit user direction, commits `408080146`/`6e184aec2`, merged `071440105`): the class authenticated purely by `Subject`-name matching, forgeable without a correctly-configured `SecurityManager`; fixed to fail closed without an installed SM. Board review found the guarantee also needs `RuntimePermission("setSecurityManager")` denied, not just `AuthPermission`, and found this precondition did NOT hold in 7 QA-harness policy files — fixed separately (see below). (2) **No grant-revocation mechanism in `SubProcessLocalPolicyAdmin`**: a narrower re-verdict didn't supersede an earlier broader grant, which lingered until its own lease TTL; fixed via `impliesEquivalent()`-based supersession (commit `86d56add1`). (3) **No replay protection in `SubProcessGrantOrchestrator`**: closed in two passes — first added a timestamp-based freshness generation (`86d56add1`), but board review proved it never verified `RegistryVerdict`'s cryptographic signature, so a forged verdict with a fabricated timestamp defeated it; second pass added real `verifySignature()` before trusting the timestamp at all (`17b0e0551`). **Also landed, T4-adjacent:** removed unqualified `AllPermission`/`AuthPermission("*")` scaffolding from 7 `qa/harness/policy/defaultspiffe*.policy` files (mahalo, outrigger, reggie, fiddler, group, mercury, norm) that would have defeated finding (1)'s fix precondition — replaced with a narrow least-privilege baseline, added a static regression-guard script (`qa/harness/policy/check-spiffe-policy-no-broad-grants.sh`); commits `edb5ff072`/`5b9b31147`, merged `265d10aca`. **See §"Additions found during T4, not originally scoped" below for why these four items are documented as additions, not retrofitted into this document's original T1-T7 breakdown.** Dedicated adversarial probing of T1→T2→T3 above as one chain, not each piece in isolation — e.g. can the hosted smart proxy itself, given only its own (deliberately narrow) permissions, ever reach or influence the grant-application path; can a grant be replayed or applied to the wrong subprocess (re-verify `SubProcessAdminRegistry`'s canonical-key targeting holds under the real wire handoff, not just the stubbed one T2(landed)'s reviewers tested); does a lease-scoped grant actually expire/revoke correctly end-to-end. Same standing brief as every prior adversarial round this session: build and run real probes, don't accept "the design reads sound." | **XHIGH** | **parallel board** | T1, T2, T3 substantially implemented |
| **T5** · `DerProxySerializer` visibility + interface-hint follow-up | **LANDED 2026-07-20.** Part (a) — package-private visibility — landed via a 2-line static-dispatch seam on `ProxyWireSupport` (`public static substituteDownloadableProxy(...)`), not a class move, per this row's own recommendation (commit `faa70751d`). The individual commit's own message left part (b) as "investigated but deliberately left unimplemented pending board sign-off"; the **merge commit `a141012a0`'s own message records that sign-off happened before merge** ("reviewed and verified independently — rebuild, test rerun against parent commit to confirm pre-existing failures, adversarial crafted TLV proof of the 127-interface decode bound. T5(b) resolved: DER does not need a new interface-name field") — **note for a future reader: that verification is recorded in the merge commit message, not as a persisted automated test in `jgdms-der/src/test`; `MAX_PROXY_INTERFACES=127` itself pre-dates this session (commit `c28fa44ab`).** Part (b)'s resolution: **"DER doesn't need a new field"** — interface names already ride the wire at the `[8]` bare-proxy-item layer via `ProxyWireSupport`, decode-bounded at 127 by that pre-existing mechanism, a layer difference from JOSS's opaque blob rather than a missing field; no DER-board escalation needed since the answer turned out to require no wire-schema change. **Original task text below, describing what was planned before either part landed:** Two coupled changes, confirmed still fully unstarted: (a) make `DerProxySerializer` package-private to match `ProxySerializer`'s shape — the minimal fix is a 2-line static-dispatch seam via a `public static Object substituteDownloadableProxy(...)` forwarder on `ProxyWireSupport` (already public, already imported by both current callers `DerObjectStreamCodec`/`DerMarshalInstanceOutput`), not a class move (a class move would introduce reverse package-coupling edges — see the original scoping finding); (b) add the equivalent decode-bounded interface-name field DER's own `ProxyWireSupport.resolveTolerant`/`MAX_PROXY_INTERFACES=127` mechanism suggests is the natural DER-side analogue — **or confirm, as the original follow-up scoping suggested, that DER doesn't need it** because the interface names already ride the DER wire at the `[8]` bare-proxy-item codec layer via `ProxyWireSupport`, a layer difference from JOSS's opaque `serviceProxy` blob, not a missing field. Resolve this question explicitly, don't assume either answer. | **MEDIUM**, may escalate — this touches the ASN.1/DER cross-language wire schema (a Rust JERI peer must be able to read it), so if (b) turns out to need a real field addition, treat that specific sub-decision as XHIGH and route it through the DER/module-boundary board domain (`JGDMS-Board-Reviewer-Guidance.md` §2.1) | design decision (a) is low-risk mechanical; (b)'s decision needs the DER-domain reviewer's sign-off before implementing, not just code review | none — independent of T1-T4 above |
| **T6** · QA implications investigation | **LANDED 2026-07-20 — confirms Peter's "mostly invisible" assessment for this session's own new machinery.** Traced the actual gating: the isolation routing branch (Wiring T1) is behind `net.jini.loader.pref.smartProxyIsolation.enabled`, a system property unset everywhere in the QA harness (confirmed by grep); a repo-wide grep additionally found zero QA references to any of T1-T5's new classes. **One lead surfaced, not fully closed as of this writing:** whether the T2 platform-level fix (commit `8f3874a9b`, the `Externalizable` field-loss bug) affects existing QA test *correctness* via `qa/src/org/apache/river/test/spec/{io,jeri}/util/FakeArgument.java`, which shares the exact bug shape (a plain, non-`@AtomicExternal` `Externalizable`). This was being separately chased by a concurrent agent session as this document was being written; as of this edit that session's own branch (`task-t6-qa-implications`) shows no committed findings yet — treat as **still open, not resolved**, until a follow-up confirms one way or the other. Dedicated pass now that T1-T4 (this document) exist to investigate against — deliberately deferred until now per the wiring SOW's own note. Peter's initial assessment was "mostly invisible"; confirm or refute against the real QA harness. | **MEDIUM** | single reviewer | T1-T4 substantially landed (needs something concrete to run against) |
| **T7** · Documentation closeout | **IN PROGRESS 2026-07-20 — this edit and its sibling edits are this task.** Updated: this document's own status header/table (above) and the two new sections at the end (`Additions found during T4, not originally scoped`, `Open items carried forward`); `SOW-SubProcessDynamicPolicy.md`'s status header and T3/T4/T5/T6 rows; `SOW-Smart-Proxy-Isolation-Wiring.md`'s status header and T1-T4/T6 rows; `SOW-BAE-Timing-Sidechannel-Denial.md`'s T7 row and §1b point 3; `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 3's status; `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` §1/§4; `spiffe-admin-deployment.md`'s now-closed "known pre-existing gap" note. Reviewed by one fresh reviewer per this task's own single-reviewer gate (see final report). **Original task text below, describing what was planned — executed as described, see the updates listed above for what actually changed in each file:** Update `SOW-SubProcessDynamicPolicy.md`'s status header (which read "DRAFT — task breakdown for review, no implementation started," stale even before this pass started — T1/T2 landed and T3(a)/(b) landed via Wiring T2) and its T3 row (should say parts (a)/(b) are done, only (c) remains, cross-referencing this document's T1). Update `SOW-Smart-Proxy-Isolation-Wiring.md`'s own T6 checklist items once T2/T4 above land. Flip `SOW-BAE-Timing-Sidechannel-Denial.md` T7's gate once the whole chain is real, not just the routing/pooling half. Describe reality, not aspiration. | **MEDIUM** | single reviewer | T1-T6 substantially landed |

### Sequencing

- **Now, parallel:** **T1** (grant-application backend — independent), **T2** (wire handoff — independent,
  the long pole given its risk class), **T5** (DerProxySerializer — fully independent of the rest), and
  **T3**'s stub-based build (against a stub `PolicyAdmin`/stub subprocess — independent of T1/T2 for this
  part only).
- **Before T3 can integrate for real:** its full integration test needs T1 landed (a real `PolicyAdmin`
  target) and ideally T2 landed too (for a genuine "verdict becomes known" trigger, rather than a manually
  injected one).
- **After T1, T2, T3 substantially done:** **T4** — system-level adversarial pass, not just per-component
  (a system-level pass looks for different failure modes than a component-level one — the TOCTOU bug in
  T2(wiring) was actually caught at component-level board review, not a system-level pass, but that
  doesn't make the system-level pass optional; both are warranted, and each catches classes of defect the
  other can miss).
- **After T1-T4:** **T6** (QA investigation — needs something concrete to run against).
- **Last:** **T7**, once the above is real, not planned.

### Notes

- **T1 and T2 are the ones to guard hardest.** Both are genuinely novel security mechanisms (real
  cross-process authority application; the concentrated object-reconstruction door), in the same risk
  class as everything else this session's adversarial boards caught real defects in. Do not let "the
  design reads sound" substitute for build-and-run adversarial probes against the actual implementation
  for either.
- **Do not build T1's grant-application backend inside `SubProcessPolicyAdmin`/`GuardedPolicyAdminHandler`
  themselves.** Those classes are the authentication/dispatch layer, already adversarially reviewed and
  landed — the real backend should be a separate class implementing `PolicyAdmin` and injected as
  `backing`, exactly the shape the test double (`TrustedPolicyBacking`) already demonstrates. Keep the
  layers separate; don't collapse authentication and grant-application into one class.
- **T5 is fully independent** — don't let it block or be blocked by T1-T4, but don't let it quietly drop
  off the list either; it's been carried forward, unstarted, across two rounds of scoping already.
- **`SOW-SubProcessDynamicPolicy.md`'s stale status line is a real trap for a future reader** (including
  a future AI session with no memory of this one) — a doc that says "no implementation started" next to
  code that's substantially implemented will cause someone to either redo landed work or misjudge what's
  actually safe to build on. Fix this early in T7, not last, if a fresh session picks this up before T1-T6
  are otherwise done. **Fixed 2026-07-20, as T7's own first action** — see that document's status header
  and T3 row. This note is kept, not deleted, as a record of why T7 treated that fix as urgent rather than
  incidental.

---

## 4. Explicit dependency ledger

| This document's task | Depends on / feeds | Direction |
|---|---|---|
| T1 grant-application backend | `SubProcessAdministrable`/`PolicyAdmin`/`GuardedPolicyAdminHandler` (landed) | extends |
| T2 wire handoff | `SubProcessPool`/`SubProcessHandle` (landed), `HostedProxyGuard` (landed) | extends |
| T3 caller-side wiring | T1 (needs a real target); `VerdictPermissionMapper` (landed); `SubProcessAdminRegistry` (landed) | consumes |
| T4 system-level adversarial pass | T1, T2, T3 | verifies |
| T5 DerProxySerializer follow-up | `ProxyWireSupport` (existing); independent of T1-T4 | resolves a standing open question |
| T6 QA investigation | T1-T4 landed | needs concrete artifact to investigate |
| T7 documentation closeout | T1-T6 landed; `SOW-BAE-Timing-Sidechannel-Denial.md` T7 gate | describes reality |

---

## 5. Additions found during T4, not originally scoped

T4's adversarial pass found three real defects and required one QA-policy hardening pass that this
document's own T1-T7 breakdown (§3 above, as drafted 2026-07-19) did not anticipate or scope. They are
recorded here explicitly, as real additions this session made per direct user instruction, not
retroactively woven into the original task descriptions above (which are left as originally written,
plus their own `LANDED` annotations) — doing otherwise would misrepresent what was actually planned
versus what was actually found:

1. **`AdminPrincipalAuthenticator` confused-deputy fix.** `AdminPrincipalAuthenticator` is *pre-existing*
   code (landed in the 2026-07-19 wave, as part of Wiring T2's authentication/dispatch scaffold) — not
   itself a T1-T4 deliverable of this document. T4's board found it authenticated purely by `Subject`-name
   matching against the ambient `Subject`, forgeable by hosted/untrusted code without a correctly-
   configured `SecurityManager` actively enforcing `AuthPermission`/`RuntimePermission` denials. This is a
   confused-deputy bypass in already-landed code, outside this document's own original scope entirely —
   fixed per Peter's explicit direction once found, not because T1-T4 called for touching that class.
2. **QA-policy hardening.** A direct consequence of finding (1): the fix's fail-closed guarantee depends
   on the deployed policy denying `AuthPermission("callAs"/"doAs")` and `RuntimePermission
   ("setSecurityManager")` to hosted/business code, and board review found 7 `qa/harness/policy/
   defaultspiffe*.policy` files did not — they granted unconditional `AllPermission`/`AuthPermission("*")`
   to every protection domain, pre-existing QA bring-up scaffolding that had never been cleaned up under
   the established "AllPermission grants are throwaway" convention. Removed and replaced with a narrow
   baseline; a regression-guard script was added so it cannot silently regress
   (`qa/harness/policy/check-spiffe-policy-no-broad-grants.sh`). Not a T1-T7 deliverable either — a direct
   consequence of finding (1), fixed for the same reason.
3. **Grant-revocation mechanism** (`SubProcessLocalPolicyAdmin`) and **4. replay protection**
   (`SubProcessGrantOrchestrator`) — these two *are* squarely within T4's own mandate ("can a grant be
   replayed or applied to the wrong subprocess... does a lease-scoped grant actually expire/revoke
   correctly end-to-end" — T4's row above, as originally drafted, already asked exactly this). Listed here
   for completeness of the "what T4 actually found" account, not because they were out of scope — unlike
   items 1-2 above, these were squarely what T4 was commissioned to look for, and finding them is T4
   succeeding at its job, not scope creep.

## 6. Open items carried forward (genuinely open — not overclaimed as complete)

This document's own T1-T6 landing, and the adversarial boards that reviewed each XHIGH item, are real —
verified by direct code reading and, for the security-critical items, by build-and-run adversarial
probes, not merely "the design reads sound." That is not the same claim as "this is deployed" or "this
is finished, full stop." Three things remain genuinely open, none of them in this document's own T1-T7
scope, all confirmed by direct investigation (repo-wide grep, multiple independent board reviewers) and
not by inference or omission:

1. **`SubProcessLauncher`'s real OS-process spawning is unbuilt.** The production `fork`/`exec` + UDS
   bring-up implementation remains `UnsupportedSubProcessLauncher` — confirmed by multiple board
   reviewers across this session's T2 (wiring) and T4 (wire-handoff) work. Everything T1-T5 above built
   (routing, pooling, authentication, grant application, wire handoff) is real and tested *against a
   launcher abstraction*, not against a real spawned OS process. This is a genuine prerequisite gap for
   any production use of this architecture, and it was never in this document's own scope — it belongs to
   whoever picks up building the real launcher next.
2. **Zero production callers.** A repo-wide grep, run independently by multiple reviewers across this
   session, finds no production code anywhere that constructs a `SubProcessGrantOrchestrator`,
   `SubProcessPolicyAdmin`, or `SubProcessLocalPolicyAdmin`. The entire T1-T4 chain this document closes
   is real, tested, board-reviewed machinery — and nothing in production wires it together yet. Until (1)
   above is also closed, nothing could wire it together end-to-end even if it tried; but the absence of
   any caller is worth stating plainly rather than letting "landed" be read as "in service."
3. **Non-blocking residuals, explicitly flagged and accepted by the relevant boards, not silently
   dropped:**
   - **`getGrants()` marshalability gap, once a real wire transport carries `PolicyAdmin` calls.** The
     current grant-application path (`SubProcessLocalPolicyAdmin`/`SubProcessGrantOrchestrator`) is
     same-JVM only — confirmed: `SubProcessPolicyAdmin.getSubProcessPolicyAdmin()` returns an ordinary
     `java.lang.reflect.Proxy` (`GuardedPolicyAdminHandler`) dispatching to an in-process `backing`
     object, not a JERI-exported remote object. This is architecturally separate from T2's wire handoff,
     which carries business-call/reconstruction traffic only, never `PolicyAdmin` administration traffic
     (§S1's own explicit separation). If `PolicyAdmin` is ever exported over a real remote transport,
     `getGrants()`'s `PermissionGrant[]` return value will need to actually marshal across that
     transport — not evaluated here because there is no such transport yet.
   - **`DecodeDepthGuard` blind spot on proxy-shaped payload prefixes** — a CPU-cost asymmetry, not a
     safety gap: the guard's own javadoc documents that it does not model dynamic-proxy class descriptors
     or `Externalizable` content precisely (to avoid false-positive rejection of legitimate traffic), so a
     payload using those shapes to disguise depth is not caught by the pre-scan — but the `catch
     (Throwable)` backstop (§Finding 1 fix, `SOW-T4-Wire-Handoff-Protocol.md` §10) confirmed to hold
     through it regardless, so a worst-case miss still degrades to a clean, reported failure, not a crash.
   - **A narrow check-then-act race in `SubProcessGrantOrchestrator`'s anti-replay generation map**,
     documented directly in that class's own javadoc: two genuinely concurrent calls carrying the
     identical verdict timestamp for the identical target+content pair can both pass the freshness check
     before either records its generation. Judged acceptable for the current single-orchestrating-
     controller call pattern (verdict application is not high-frequency, and the realistic adversarial
     replay scenario is sequential resend of captured bytes, not a true concurrent race) — a benign
     double-apply, not an authority escalation.
   - **Unbounded generation-map growth** in the same class, for long-lived deployments — no hook exists
     to reclaim an entry when a subprocess is torn down and unregistered from `SubProcessAdminRegistry`.
     Flagged in the class's own javadoc as a reasonable, currently-unbuilt follow-on (bounded/LRU map, or
     an unregistration callback) if this orchestrator is ever deployed as a long-lived singleton across
     many subprocess lifecycles.
   - **The `AtomicMarshalInputStream` Externalizable-array stream-desync bug: CLOSED, not a residual —
     correcting an earlier, now-superseded caveat.** The first fix for this bug class (T2's Finding 1,
     `c6136c7d6`) closed only the crash (a null-guard on the `StreamCorruptedException` branch), leaving
     the underlying stream desynchronization — and the specific "non-last-`Externalizable`-array-element"
     edge case — reported cleanly but not actually resolved. A *later*, separate finding in the same T2
     review round (Finding 3, `SOW-T4-Wire-Handoff-Protocol.md` §10) traced the desync to its true root
     cause (two compounding bugs in `ObjOutputStream.writeNewClassDesc`/`AtomicMarshalInputStream
     .readyPrimitiveData`) and fixed it fully — confirmed by an upgraded end-to-end test
     (`SubProcessWireHandoffEndToEndTest#nonLastExternalizableArrayElement_roundTripsCorrectly_
     throughFullStack`, commit `f32696b69`) that now asserts success, not merely "fails cleanly." Any
     doc or memory note still citing the first fix's narrower caveat is itself stale as of `8f3874a9b`;
     this bullet is the corrected account.

---

*Status: LANDED (T1-T6), documentation closeout in progress (T7) — see the status line at the top of
this document, §3's task table, §5 (real additions found during T4), and §6 (genuinely open items) for
the full account. No production code was written or modified in producing this document; §5/§6 describe
code landed by the tasks this document scoped, not code this document itself changes.*
