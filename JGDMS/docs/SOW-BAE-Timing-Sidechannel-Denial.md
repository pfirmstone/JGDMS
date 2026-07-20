# Scope of Work — BAE categorical denial for `System.nanoTime`-class timing side channels

- **Drafted:** 2026-07-17.
- **Status:** DRAFT — task breakdown for review, no implementation started. **This line describes T1-T9
  below (this SOW's own bytecode-level BAE hardening tasks) and is not re-audited by this note — that
  is out of scope for the 2026-07-20 documentation-closeout pass that added this bullet.** What *is*
  confirmed as of 2026-07-20, and is what T7's row (below) actually gates on: the **primary defense**
  this SOW is secondary to — `SOW-Smart-Proxy-Isolation-Wiring.md`'s T1/T2/T4 (routing, subprocess
  pooling, wire handoff) and `SOW-SubProcessDynamicPolicy.md`'s T3(c)/T4 (real grant-application
  backend, caller-side grant wiring) — has landed and been through a system-level adversarial pass; see
  the T7 row for exactly what claim strength that supports and does not support.
- **Origin:** `pron98` r/java debate point ("no true sandboxing without limiting resource usage and
  access to `System.nanoTime`"), Peter's private working-note proposal (categorical bytecode-level
  rejection of mobile code calling `System.nanoTime`/`System.currentTimeMillis`, extended by him to
  native memory access and lock contention in static initializers), and the adversarial investigation
  this SOW is the direct output of (verdict summarized in §1 — not re-derived here).
- **Companions:** `No-SM-Permanently-Ungated-Primitives.md` (the `AttachPermission`/JMX policy-audit
  gap this SOW's T1 closes — flagged there as "not yet spot-checked"); `security-articles/
  attack-model-vs-kernel-isolation.md` (the public-facing article T7 updates); BAE's existing
  `ClinitBlockingVisitor`/`BlockingSinkRegistry` (this session's prior hardening — the infrastructure
  T2–T4 extend, not replace); **`SOW-Unix-Domain-Socket-JERI-Transport.md` §12 (2026-07-17 decision:
  mandatory UDS-isolated per-principal-process routing for downloaded/mobile smart-proxy execution) —
  now the *primary* answer to the pron98 timing side-channel case; this SOW's T1–T7 are repositioned
  as secondary defense-in-depth (see §1a below), not superseded or made worthless.** Also:
  `SOW-Smart-Proxy-Isolation-Wiring.md` (the task breakdown that makes the primary-defense claim above
  something real rather than decided-on-paper — T7 gates on its status, not just the UDS SOW's design
  decision); `SOW-SubProcessDynamicPolicy.md` (§1b point 3's "SM/POLP is complementary defense-in-depth"
  claim below assumes the isolated process's own permission ceiling was delivered soundly — that's this
  SOW's mechanism, refined 2026-07-18 to the `SubProcessAdministrable`/`PolicyAdmin` fail-closed
  admin-authentication shape; T7 gates on its status too, see the T7 row).

---

## 1. Verdict this SOW executes against (not re-argued here)

Investigation finding, in one sentence: **categorical rejection of direct `nanoTime`-family calls
closes the cheapest attack path, not the attack surface** — a timerless clock built from two ordinary
cooperating `Thread`s and a shared counter gives GHz-scale timing resolution using zero flaggable
API calls, and the security community's own conclusion (V8/Chrome, after Site Isolation) is that no
software mitigation inside a shared address space is sufficient; the only mechanism they consider
genuinely effective is process-level separation. This SOW is **defense-in-depth, done honestly** — it
raises the cost of exploitation and closes several concretely-verified gaps (reflection indirection,
lock-contention-in-`<clinit>`, an unaudited policy assumption more structurally important than the
`nanoTime` fix itself) — it is explicitly **not** commissioned or to be described as "solving" the
timing side-channel problem. See §7 for the language constraint this places on T7.

### 1a. Reprioritization (2026-07-17) — process separation is no longer "structurally unavailable"

The line above originally read "...which is structurally unavailable to JGDMS's in-process
multi-principal design by choice." That is now out of date: `SOW-Unix-Domain-Socket-JERI-Transport.md`
§12 records Peter's decision to make UDS-isolated, per-principal-process execution **mandatory** for
downloaded/mobile smart-proxy execution — the exact category this SOW's `nanoTime`-family concern is
about. Genuine OS-process separation is the one mechanism the field actually considers sufficient
(§ References); if that routing is real and default for this case, it is the **primary** defense
against pron98's point, not this SOW.

This SOW is **not superseded** — it remains real, independently-justified work:
- It is the correct posture for **whatever legitimately still executes in-process** (T9 below is
  scoped precisely to that residue) — UDS routing is a decision for the downloaded/mobile smart-proxy
  case specifically; it is not a claim that nothing runs in-process anywhere in JGDMS.
- It is the posture for the **gap between "decided" and "deployed"** — per the UDS SOW's own status
  correction, the exporter/`service-starter` wiring to make mandatory routing real is unimplemented as
  of this writing. Until it exists, in-process execution is not a residual case, it is the default one.
- Several of its findings (reflection/indirection denial, `<clinit>` lock-contention detection, the
  `AttachPermission`/JMX policy audit) are valuable independent of the timing question — a reflective
  indirection bypass or an unaudited attach-permission gap matters for reasons that have nothing to do
  with cache timing.

### 1b. Second rescoping (2026-07-17) — the UDS SOW's design deepened; here's how that narrows T2–T9

Since §1a was written, `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 gained two further decisions that
change *what* the remaining in-process threat actually is, not just *whether* it exists:

1. **Isolation is now unconditional for every smart proxy** (no trust-tier classification — see UDS
   SOW §12 point 3), and **the local client process never loads a smart proxy's implementation bytecode
   at all** — it holds only known interface types + a dynamic-proxy stub forwarding over UDS. This
   closes, by construction, the *severe* original form of the threat: one untrusted principal's proxy
   reading another untrusted principal's or the client's own memory via cache-timing. That specific case
   no longer needs a bytecode-level defense in the client process, because there is no longer any
   downloaded bytecode in the client process to defend, full stop — not "hardened," *absent*.
2. **What's left is narrower and better-understood: each isolated (per-remote-SPIFFE-principal) process
   still co-resides a downloaded proxy's code with *some* first-party JGDMS platform/hosting code**
   (the UDS server plumbing, DER codec, SPIFFE/TLS material for that process's own connections). A
   Spectre-class attack from the downloaded proxy against *that* co-resident trusted code is still
   theoretically live — same mechanism, much smaller and more auditable target (JGDMS's own code, not
   an arbitrary other principal's secrets).
3. **SecurityManager/POLP within the isolated process is real, complementary defense-in-depth — but it
   is not itself a timing defense, and must not be described as one.** SM/POLP constrains what the
   downloaded proxy's code can *do* via legitimate permission-checked APIs (deny network egress, file
   access, native-library loading, `AttachPermission`, reflection-escalation permissions, etc.) — this
   reduces blast radius if a side-channel leak *does* occur, and specifically matters for denying
   permissions that could otherwise be used to attack or escape the process boundary itself (native
   memory access is exactly Peter's own "also needs categorical rejection" callout in the SOW header).
   It does **not** gate `System.nanoTime`/`currentTimeMillis` — those calls have zero permission checks
   in DirtyChai, in any JDK, ever (the original finding this whole SOW exists because of) — so SM/POLP
   provides no coverage for the specific mechanism T3/T5 address. The two layers are complementary, not
   substitutable for one another. **This claim has its own dependency, worth stating explicitly: the
   permission ceiling SM/POLP enforces has to actually be delivered to the isolated process soundly** —
   that delivery mechanism is `SOW-SubProcessDynamicPolicy.md`, refined 2026-07-18 to a dedicated
   `SubProcessAdministrable`/`PolicyAdmin` admin surface with fail-closed authentication as the
   orchestrating admin principal (not merely a construction-time convention about which stub carries the
   grant-push interface). If that mechanism were bypassable — e.g. the hosted proxy itself reaching the
   policy-admin surface and altering its own ceiling — this point's "SM/POLP constrains what the
   downloaded proxy can do" claim would not hold. T7 must not assert this complementary-defense claim
   independent of that mechanism's own status.
   **Status update (2026-07-20): the delivery mechanism this point depends on has landed and been
   through a system-level adversarial pass** — `SubProcessDynamicPolicy` T3(c) (the real grant-
   application backend, `SubProcessLocalPolicyAdmin`) and T4 (caller-side wiring,
   `SubProcessGrantOrchestrator`) are built, board-reviewed, and the specific bypass this point worries
   about — "the hosted proxy itself reaching the policy-admin surface" — was one of three things the T5
   system-level adversarial pass explicitly probed for (see `SOW-SubProcessDynamicPolicy.md` §4's T5
   row); it was not found reachable, though two *other* real defects were (grant revocation, replay —
   both fixed). **This still does not make the claim in this point unconditionally true, for two
   independent reasons, neither of which this landing changes:** (a) the confused-deputy fix this
   mechanism itself depends on (`AdminPrincipalAuthenticator`, closed 2026-07-20) requires the deployed
   policy to deny `AuthPermission("callAs"/"doAs")` and `RuntimePermission("setSecurityManager")` to
   every hosted/business protection domain — a precondition, not an automatic property of installing
   this mechanism, and one the QA harness's own default policies were found *not* to satisfy until
   separately corrected (see `spiffe-admin-deployment.md`'s "Hard prerequisite" section); (b) zero
   production code anywhere in the repo currently constructs `SubProcessGrantOrchestrator`/
   `SubProcessPolicyAdmin`/`SubProcessLocalPolicyAdmin` (confirmed by repo-wide grep), and the real
   OS-process launcher this whole chain assumes remains `UnsupportedSubProcessLauncher` — so this is
   real, tested, board-reviewed machinery with no production wiring yet, not a deployed guarantee. T7's
   own wording must carry both qualifications, not just cite "landed."

**Net effect on T2–T9, stated plainly**: their *purpose* narrows from "defend arbitrary co-resident
principals and the client" to "defend the isolated process's own first-party hosting code from the one
principal it's now scoped to." They remain worth doing — the mechanism (a malicious proxy timing-attacking
co-resident trusted code) is unchanged — but the urgency drops relative to §1a's framing, because the
blast radius of *not* doing them has shrunk from "any principal, anywhere" to "one process's own
platform code." See each task row below for the specific adjustment; T8/T9 are the most affected.

---

## 2. Objective

Implement Peter's proposed categorical-rejection direction as far as it can honestly be taken, close
the concrete gaps the adversarial investigation found in getting there (reflection/indirection bypass,
missing lock-contention detection, an unaudited and higher-priority policy-scope assumption), and
correct the public/internal framing so nothing overclaims what remains an open, actively-researched
problem for any in-process multi-tenant JVM design.

---

## 3. Non-goals

- **Not attempting to close the timerless-clock (counting-thread) bypass.** No task in this SOW
  claims to. Banning ordinary `Thread` + shared-field concurrency is not a viable mitigation — it
  breaks legitimate code at a scale disqualifying it as an option. T8 exists to scope whether any
  *structural* (not bytecode-denylist) answer is worth pursuing later; it is explicitly speculative
  and not committed.
- **Not a GraalVM migration.** JIT-level speculative-barrier insertion (GraalVM's actual mitigation
  mechanism) is out of scope — noted in §7's framing as the more complete alternative that exists,
  not something this SOW builds.

---

## 4. Execution plan — task breakdown (agent type + effort)

Effort follows the project convention (security → `xhigh`, class/wire-resolution → `max`,
mechanical → `medium`, test-debug → `xhigh`); orchestration follows the norm *fresh implement →
review gate*, with a **parallel review board** for high-blast-radius/security work and a **single
reviewer** for contained changes, matching this session's own BAE review history (four independent
adversarial rounds already found and closed real bypasses in adjacent BAE mechanisms — the same
scrutiny applies here). All models = opus unless noted.

### Summary

| Task | Deliverable | Implement (agent · effort) | Review | Depends |
|------|-------------|------------------------------|--------|---------|
| **T1** · Policy audit | Confirm no JGDMS production/example policy grants `AttachPermission("attachVirtualMachine")` or the three JMX permissions (`MBeanPermission`/`MBeanTrustPermission`/`MBeanServerPermission`) to untrusted codebases. **Scope expanded (§1b):** with per-remote-SPIFFE-principal isolated processes, there may be *N* distinct process policies to audit, not one — each isolated process's own policy needs this same confirmation, not just the client's. Still closes the doc's own "not yet spot-checked" flag; **still higher structural priority than T2–T5** — if wrong for even one isolated process, `redefineClasses` can undo that process's own SM/POLP layer (§1b point 3) from inside. | general-purpose · **HIGH** | single reviewer (security-literate) | none — run first/parallel |
| **T2** · Generalize call-graph engine | Extend `ClinitBlockingVisitor`'s registry-driven, allowlist-based BFS from `<clinit>`-rooted to whole-method-body-rooted. **Priority downgraded for *this SOW's* purpose (§1b)** — the thing it protects narrowed from "arbitrary co-resident principals" to "one isolated process's own first-party hosting code." Retained at unchanged priority for BAE's *independent* purposes (vacuous-check/deserialization safety, §1a) — do not drop it, just don't treat it as timing-critical-path anymore. **Zero behavior change to existing `<clinit>` verdicts.** | general-purpose · **HIGH** (was XHIGH — still core-engine surgery, still needs the same rigor, but no longer the timing-defense keystone it was under §1a's framing) | **parallel board** (2–3 adversarial, same rigor as this session's prior BAE rounds) | T1 can run in parallel; no code dependency |
| **T3** · `nanoTime`-family denylist | Using T2's engine: reject `LoadClassPermission` for mobile code with a statically-resolvable direct call to `System.nanoTime`/`System.currentTimeMillis`/`Instant.now()` etc. **Rescoped (§1b): protects the isolated process's own hosting/platform code specifically**, not cross-principal data (that case is now closed by construction, not by this task). Still worth doing — SM/POLP does not gate these calls (§1b point 3) — but no longer the primary line of defense it was under §1a. | general-purpose · **MEDIUM** (unchanged — mechanical once T2 exists) | single reviewer + adversarial spot-check | T2 |
| **T4** · Lock-contention detection | Add `MONITORENTER`/`ACC_SYNCHRONIZED` instruction-level detection — confirmed absent today. **Rescoped (§1b): now a DoS-availability concern for one isolated process at a time**, not a system-wide one; a DoS'd isolated process no longer threatens other principals' processes, just its own. Scope to `<clinit>` first per Peter's wording. | general-purpose · **MEDIUM** (was HIGH — same detection work, smaller blast radius if imperfect) | single reviewer + adversarial spot-check (was: parallel board) | T2 |
| **T5** · Reflection/indirection categorical denial | Reject mobile code containing any `Method.invoke`/`MethodHandle.invoke*`/`VarHandle` call, or `Class.forName`/`ClassLoader.loadClass` with a non-compile-time-constant argument. **Rescoped (§1b) the same way as T3** — protects the isolated process's own hosting code from its one resident principal, not the general case. Still closes a confirmed bypass of T3, still worth the adversarial rigor (novel mechanism), just narrower stakes. | general-purpose · **HIGH** (was XHIGH — same mechanism risk, smaller blast radius) | **parallel board**, adversarial-probing mandate unchanged (novel-mechanism risk doesn't shrink just because the target does) | independent of T2–T4; can run in parallel |
| **T6** · Legitimate-timing-use pattern | Document Peter's "local trusted code calls `nanoTime` itself, hands the result to the proxy as ordinary data" pattern. **Gets cleaner under §1b**, not harder: the process boundary now gives a crisp architectural line — the isolated process's own first-party hosting code may call `nanoTime`; the downloaded proxy object it hosts may not, directly. Easier to state and enforce than the old "somewhere in one shared JVM" version. | general-purpose · **MEDIUM** | single reviewer | T3 |
| **T7** · Public/internal framing pass | **Gate status (updated 2026-07-20): the dependency chain this row gates on is now landed, not merely decided.** `SOW-Smart-Proxy-Isolation-Wiring.md`'s T1/T2/T4 (routing insertion, subprocess pooling, wire-protocol handoff) are built and board-reviewed; `SOW-SubProcessDynamicPolicy.md`'s T3(c)/T4 (real grant-application backend, caller-side grant wiring, this row's own point (d)) are also built and board-reviewed, including a system-level adversarial pass across the whole chain (`SubProcessDynamicPolicy` T5) that found and closed two real defects (grant revocation, replay protection) — not a clean pass, a verified one. **What this changes about points (a)/(d) below, precisely:** point (a)'s "decided, wiring in progress" language is now itself stale in the other direction — the wiring is no longer merely "in progress," it is built and tested. Point (d)'s "not yet built/verified" fallback no longer applies to `SubProcessDynamicPolicy`'s status. **What has NOT changed, and point (a)/(d)'s replacement wording must still carry:** this is real, tested, board-reviewed *mechanism*, confirmed by direct code reading — it is not a *deployed* guarantee. `SubProcessLauncher`'s real OS-process spawning remains `UnsupportedSubProcessLauncher` (confirmed by multiple board reviewers across sessions), and zero production code anywhere in the repo constructs `SubProcessGrantOrchestrator`/`SubProcessPolicyAdmin`/`SubProcessLocalPolicyAdmin` (confirmed by repo-wide grep) — so the correct replacement framing is closer to "built and adversarially verified, not yet deployed in any running service" than either "wiring in progress" or "deployed." The confused-deputy fix this mechanism's own soundness depends on (`AdminPrincipalAuthenticator`) additionally requires a deployed policy denying `AuthPermission("callAs"/"doAs")`/`RuntimePermission("setSecurityManager")` to hosted code — a precondition the QA harness's own default policies did not originally satisfy (separately corrected, see `spiffe-admin-deployment.md`) and any real deployment must independently confirm. **This row remains gated on Peter's own final review for the public-facing wording itself** (not delegated here) — this update only confirms the underlying facts the wording must be built from are now landed, verified by direct code/commit reading, not that the public copy has been rewritten. Update `attack-model-vs-kernel-isolation.md` and any r/java-debate-facing material per §1/§1a/§1b. **The claim strength for the specific pron98 case goes up**: cross-principal timing attacks are now closed by construction (absence of downloaded bytecode in the client process), not by classification or bytecode-level heuristics — a materially stronger and more defensible claim than "we detect and reject dangerous calls." Still must state precisely: (a) "decided, wiring in progress" not "deployed" until `SOW-Smart-Proxy-Isolation-Wiring.md`'s T1/T2/T4 land (that SOW is where §12 point 3's wiring decision was actually broken into buildable tasks — cite it by name, not the design-level UDS SOW section, once it exists); (b) the CPU-affinity requirement (§8 of the UDS SOW) as a condition of completeness; (c) the *residual* claim for T1–T6 is now "defends the isolated process's own hosting code" (§1b), not "defends arbitrary co-resident principals" — don't let the stronger primary claim cause the secondary one to be overstated by association; (d) **the §1b point 3 "SM/POLP is complementary defense-in-depth" claim must not be asserted independent of `SubProcessDynamicPolicy` SOW's own status** — specifically its T3 (`SubProcessAdministrable`/`PolicyAdmin`, refined 2026-07-18 to fail-closed admin-principal authentication) and the wiring SOW's own T2 third layer (reject-on-load if a hosted proxy's interface closure declares the admin interface) — if either is unbuilt or unverified, say "SM/POLP is designed to be complementary defense-in-depth, permission-ceiling delivery not yet built/verified," not "is." | general-purpose · **MEDIUM** | **Peter, final review** (public voice/claims, not delegable) | T1–T6 substantially landed; `SOW-Smart-Proxy-Isolation-Wiring.md`'s wiring status known (primary claim, point a/b); `SOW-SubProcessDynamicPolicy.md`'s T3 status known (secondary POLP-complementary claim, point d) |
| **T8** · Structural-answer scoping (backlog, speculative) | Is there a viable longer-term architectural answer beyond UDS-isolated routing (e.g. GraalVM Truffle isolates) for anything UDS-isolation doesn't cover. **Narrows further under §1b**: with isolation now unconditional and bytecode never loaded client-side, the remaining gap to scope is specifically the *within-isolated-process* residue (§1b point 2), not a general "should we adopt GraalVM" question. | general-purpose/Plan · **MEDIUM** | single reviewer | none; can start anytime |
| **T9** · In-process scheduler-level defense (backlog, speculative) | Scoping only, same honesty constraints as T8. **Substantially downgraded under §1b, likely near-moot for smart proxies specifically**: T9 was scoped to "whatever proxy execution legitimately remains in-process" defending against *other untrusted principals or the client* sharing that process — but per §1b, each isolated process now hosts exactly *one* remote SPIFFE principal's proxies (possibly pooled, but single-trust-domain by construction). There is no other untrusted principal within that process to defend against via scheduling tricks; the only remaining question is the same narrow one T2–T5 now address (protecting first-party hosting code from its one resident principal), for which a scheduler-level defense is a heavier, less-targeted tool than T3/T5's bytecode denial. Keep on the backlog only if a *specific* future need for intra-process multi-object pooling from one principal turns out to want it — do not carry it forward as general-purpose work. | general-purpose/Plan · **LOW** (was MEDIUM; scoping only, and increasingly looks like it may not be needed at all for the smart-proxy case) | single reviewer for scoping | none; low priority to even start |

### Sequencing

- **Now, parallel:** **T1** (independent, higher structural priority — closes a gap that could
  otherwise silently undo T2–T6 post-load), **T2** (keystone — gate everything else behind it
  landing clean), **T5** (independent mechanism, no dependency on T2).
- **After T2:** **T3 ∥ T4** (both consume T2's generalized engine).
- **After T3:** **T6** (needs T3's final denial shape to document the escape hatch correctly).
- **Last:** **T7**, once T1–T6 have actually landed — framing must describe what's true, not
  what's planned.
- **Anytime, non-blocking:** **T8 ∥ T9** — pure scoping, doesn't gate or depend on the rest; T9
  benefits from knowing how much in-process residue actually remains once §12's wiring lands, but
  isn't blocked on it.

### Notes

- **T2 and T5 are still the ones to guard hardest on mechanism, even though §1b lowered their stakes.**
  Both are new or substantially-rewritten security mechanisms in BAE, the exact class of change that
  produced real, adversarially-found bypasses four times running earlier this session
  (laundering-through-external-call, same-package no-op decoy, `isSelfValidatingGetForm`'s
  field-absence gap, `pendingNewDepth`'s orphaned-`NEW` counter defeat). The *blast radius* if wrong
  shrank under §1b (one isolated process's hosting code, not arbitrary principals) — the *probability*
  of a real bypass existing if under-scrutinized did not. Do not treat "the design reads sound" as
  sufficient for either — require build-and-run adversarial probes against the actual compiled analyzer
  before sign-off, same standing brief as those rounds.
- **T1 is cheap and should not wait on anything** — it's confirmation/audit work against existing
  policy files, not new design, and it's the one item whose absence could make every other task in
  this SOW moot in a real deployment — now scoped to potentially *N* isolated-process policies (§1b).
- **T8/T9 are explicitly not commitments** — scope them, report back, let a future SOW decide. T9 in
  particular should not be built on spec; §1b's finding that each isolated process is now
  single-trust-domain by construction means the case T9 was originally justified by (another untrusted
  principal sharing the same in-process residue) may simply not exist for smart proxies going forward.
  Do not let either scope-creep into an implementation task under this SOW.

---

## References

Grounding for §1's verdict and §1a's reprioritization — not exhaustively re-derived in this doc, kept
here as citable material for T7's public-facing pass and for anyone auditing the claims later.

- **GraalVM's own Spectre mitigation, and its own admitted limit** — the specific point that preempts
  an obvious counter-argument ("GraalVM already solved this"): GraalVM's masking + speculative-barrier
  + constant-blinding defenses are explicitly scoped to Spectre-PHT/v1 (bounds-check bypass) only, not
  the branch-predictor-injection family (Spectre-BTB/v2 and the 2025 Training Solo/Branch Privilege
  Injection lineage below) — and GraalVM's own security guide states plainly that within its *default*
  shared-process isolate model, "guest code may time its execution, potentially discovering secret
  information if the host code performs secret-depending processing," with the documented *complete*
  fix being `engine.IsolateMode=external` — genuinely separate OS processes. Same conclusion as V8/
  Chrome's Site Isolation, from an independent source: even the most serious production JIT-hardening
  effort in a managed runtime does not claim to close in-process timing side channels, only to raise
  the bar on the specific gadget shape it targets. (`graalvm.org/latest/security-guide/sandboxing/`,
  `graalvm.org/.../reference-manual/java/options/` for the Spectre-PHT-scoped mitigation strategies.)
- **V8/Chrome Site Isolation** — `v8.dev/blog/spectre` ("software mitigations... are not efficient or
  comprehensive... the only effective mitigation is to move sensitive data out of the process's
  address space"); `security.googleblog.com/2018/07/mitigating-spectre-with-site-isolation.html`.
- **Timerless clock construction** — iLeakage: Browser-based Timerless Speculative Execution Attacks
  (ACM CCS 2023) and the general "counting thread" technique: two ordinary cooperating threads and a
  shared counter give GHz-scale timing resolution with zero timer-API calls. This is the concrete basis
  for §1's "closes the cheapest attack path, not the attack surface" verdict and for T3/T5's scope
  limits.
- **Browsers disabled `SharedArrayBuffer`, not just coarsened `performance.now()`** — corroborating
  precedent that timer-API restriction alone was judged insufficient even by teams with far more
  resources than this project; they had to additionally kill an orthogonal shared-memory-timer
  construction path.
- **OpenJDK's own JVM-level Spectre mitigation was withdrawn** — JEP 342 ("Limit Speculative
  Execution"), status Withdrawn, never shipped. Vanilla HotSpot/OpenJDK ships no JVM-native Spectre
  mitigation of its own; GraalVM's is a separate, non-default runtime.
- **The threat class is not closed at the hardware level as of 2026** — Training Solo (May 2025,
  affects current Intel silicon including chips with the `BHI_NO` mitigation feature, and selected ARM
  cores); Branch Privilege Injection (May 2025, ETH Zürich COMSEC, all Intel x86 from 9th-gen Coffee
  Lake Refresh onward); Transient Scheduler Attacks (July 2025, AMD, discovered by Microsoft); FP-DSS
  (April 2026, AMD). New variants landing on current-generation hardware within months of "today," not
  a settled 2018-era problem.
- **Published, quantified-cost scheduler-level defense (grounds T9 and the UDS SOW's CPU-affinity
  requirement)** — dynamic task migration as a cache-side-channel defense for many-core systems reports
  ~1.6% average / 9% worst-case performance overhead; this is the citation behind "not novel or
  speculative" in both this SOW's T9 and `SOW-Unix-Domain-Socket-JERI-Transport.md` §8's CPU-affinity
  requirement.
- **Loom/virtual-thread scheduling baseline (grounds T9's "new engineering, not stock behavior")** —
  vanilla Loom's virtual-thread scheduler is cooperative for compute-bound code; a busy-loop virtual
  thread that never blocks is not forcibly preempted by the JVM today, and behaves like an ordinary
  platform thread on its carrier until the OS itself preempts the carrier.

---

*End of DRAFT. No production code was written or modified in producing this document.*
