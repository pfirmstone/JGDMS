# Scope of Work — Smart-proxy isolation: remaining work after the 2026-07-19 landing wave

- **Drafted:** 2026-07-19.
- **Status:** DRAFT — task breakdown for review, no implementation started (this document only;
  the prior wave it follows is already landed on trunk, see below).
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
     4, and part of 6). **This doc's own status header and T3 row are stale as of this writing** — see
     item 6.
  4. This document.
- **What "landed" means here, precisely (verified by direct code reading, not inferred):**
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
| **T1** · `PolicyAdmin` real grant-application backend (`SubProcessDynamicPolicy` T3(c)) | Implement a real `PolicyAdmin` for the `backing` object `SubProcessPolicyAdmin` currently receives only a test double for. `grant(PermissionGrant)` calls the subprocess's own local `DynamicPolicyProvider`/`LeasedDelegation` (lease-scoped by default, per `SubProcessDynamicPolicy` §2's existing decision) verbatim — no independent judgment about *what* to grant, that's already `VerdictPermissionMapper`'s job. `refresh()`/`getGrants()` need real implementations too (find the existing `DynamicPolicyProvider` surface this should delegate to — don't invent a parallel grant-tracking structure). | **XHIGH** — novel authority-application mechanism, same class as the admin-authentication work T2 already got board-reviewed for | **parallel board** (2-3 adversarial) — probe: can a caller reach the real grant-application path without the T2 authentication gate in front of it (i.e. confirm this backend is *only* ever reached through `GuardedPolicyAdminHandler`, never directly); does a lease-scoped grant actually expire/revoke per `LeasedPermissionGrant`'s existing semantics; can `refresh()`/`getGrants()` leak grant state to an unauthenticated caller | none — can start immediately, independent of T2 below |
| **T2** · Wire-protocol handoff (`SOW-Smart-Proxy-Isolation-Wiring.md` T4) | Implement `SubProcessWireHandoff` for real: client forwards the raw, still-marshalled `MarshalledInstance` over UDS to the principal's subprocess (never deserializing it, even partially); subprocess reconstructs it — behind the SCAP/BAE verdict gate, `DeSerializationPermission("ATOMIC")`, and endpoint-assigned `ResolutionContext`, all still enforced *inside* the subprocess — and replies with enough for the client to build its thin stub. Byte/framing-level specification, not just the mechanism-level description already in the wiring SOW. Must also enforce T2(landed)'s reject-on-load check (`HostedProxyGuard`) at this exact boundary, since this is where a hosted proxy's interface closure first becomes a resolved `Class` set. | **XHIGH** — the single concentrated object-reconstruction door for this whole architecture; same risk class as this session's BAE-adjacent decode-DoS work | **parallel board** (2-3 adversarial) — probe: a `MarshalledInstance` crafted to reconstruct outside a gate; a subprocess-originated call back toward the client (three-axes check: byte flow, request origination, authorization — state explicitly, don't assume); oversized/deeply-nested marshalled payload (the same allocate-before-validate class of bug T1's readNewArray fix just closed elsewhere — check this new path doesn't reintroduce it) | T2(wiring)'s subprocess machinery (landed) |
| **T3** · Caller-side wiring: verdict → ceiling → grant (`SubProcessDynamicPolicy` T4) | The orchestrating logic that, once a verdict is known for a smart proxy about to run in a given subprocess, calls `VerdictPermissionMapper.computeSubProcessCeiling(...)` and pushes the result through `SubProcessAdminRegistry` → the target subprocess's `PolicyAdmin.grant(...)` (T1 above). Can be built and tested against a stub `PolicyAdmin`/stub subprocess before T1/T2 above are fully real, per the SOW's own note — but full end-to-end integration needs both, since "a verdict is known" is a fact T2 above's reconstruction path produces. | **HIGH** | single reviewer + integration test against a real T1 backend once it exists | T1 (needs a real `PolicyAdmin` to push into); T2 informs where "verdict known" actually fires from, for full integration (not for the stub-based build/test path) |
| **T4** · System-level adversarial pass | Dedicated adversarial probing of T1→T2→T3 above as one chain, not each piece in isolation — e.g. can the hosted smart proxy itself, given only its own (deliberately narrow) permissions, ever reach or influence the grant-application path; can a grant be replayed or applied to the wrong subprocess (re-verify `SubProcessAdminRegistry`'s canonical-key targeting holds under the real wire handoff, not just the stubbed one T2(landed)'s reviewers tested); does a lease-scoped grant actually expire/revoke correctly end-to-end. Same standing brief as every prior adversarial round this session: build and run real probes, don't accept "the design reads sound." | **XHIGH** | **parallel board** | T1, T2, T3 substantially implemented |
| **T5** · `DerProxySerializer` visibility + interface-hint follow-up | Two coupled changes, confirmed still fully unstarted: (a) make `DerProxySerializer` package-private to match `ProxySerializer`'s shape — the minimal fix is a 2-line static-dispatch seam via a `public static Object substituteDownloadableProxy(...)` forwarder on `ProxyWireSupport` (already public, already imported by both current callers `DerObjectStreamCodec`/`DerMarshalInstanceOutput`), not a class move (a class move would introduce reverse package-coupling edges — see the original scoping finding); (b) add the equivalent decode-bounded interface-name field DER's own `ProxyWireSupport.resolveTolerant`/`MAX_PROXY_INTERFACES=127` mechanism suggests is the natural DER-side analogue — **or confirm, as the original follow-up scoping suggested, that DER doesn't need it** because the interface names already ride the DER wire at the `[8]` bare-proxy-item codec layer via `ProxyWireSupport`, a layer difference from JOSS's opaque `serviceProxy` blob, not a missing field. Resolve this question explicitly, don't assume either answer. | **MEDIUM**, may escalate — this touches the ASN.1/DER cross-language wire schema (a Rust JERI peer must be able to read it), so if (b) turns out to need a real field addition, treat that specific sub-decision as XHIGH and route it through the DER/module-boundary board domain (`JGDMS-Board-Reviewer-Guidance.md` §2.1) | design decision (a) is low-risk mechanical; (b)'s decision needs the DER-domain reviewer's sign-off before implementing, not just code review | none — independent of T1-T4 above |
| **T6** · QA implications investigation | Dedicated pass now that T1-T4 (this document) exist to investigate against — deliberately deferred until now per the wiring SOW's own note. Peter's initial assessment was "mostly invisible"; confirm or refute against the real QA harness. | **MEDIUM** | single reviewer | T1-T4 substantially landed (needs something concrete to run against) |
| **T7** · Documentation closeout | Update `SOW-SubProcessDynamicPolicy.md`'s status header (currently reads "DRAFT — task breakdown for review, no implementation started," which is stale — T1/T2 landed and T3(a)/(b) landed via Wiring T2) and its T3 row (should say parts (a)/(b) are done, only (c) remains, cross-referencing this document's T1). Update `SOW-Smart-Proxy-Isolation-Wiring.md`'s own T6 checklist items once T2/T4 above land. Flip `SOW-BAE-Timing-Sidechannel-Denial.md` T7's gate once the whole chain is real, not just the routing/pooling half. Describe reality, not aspiration. | **MEDIUM** | single reviewer | T1-T6 substantially landed |

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
  are otherwise done.

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

*End of DRAFT. No production code was written or modified in producing this document.*
