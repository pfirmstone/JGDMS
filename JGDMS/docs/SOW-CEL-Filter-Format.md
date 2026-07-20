# Scope of Work — CEL-shaped filter/predicate format for `@RemoteFunction` (`FUNCTION` form), DER-native

- **Drafted:** 2026-07-17.
- **Status:** JAVA SIDE COMPLETE (2026-07-20) — the format recommendation was **ratified
  by Peter** (STD-009 §8). Landed on trunk, each board-reviewed: **T1** (STD-011 + all
  ratifications), **T2** (Appendix B DER wire encoding), **T3 phase 1** (`jgdms-cel`
  decoder/cost/evaluator), **T6** (CelVerifier gate + the §5.3 redundancy ruling,
  resolved in STD-011 §12.2/§12.3), **T5** (428-vector implementation-neutral
  conformance corpus + oracle), **T3 phase 2** (`CrMath` correctly-rounded
  transcendentals per §7.5 — all 188 gated vectors live; 685 module tests green).
  **Remaining:** **T4 (Rust evaluator) deferred, not dropped** — sequenced behind a Rust
  JERI DER implementation, which follows the JERI-DER join-manager QA work in flight
  2026-07-20; it validates against T5's committed corpus. **T7** (isolation posture)
  open. **T8** (documentation reconciliation) appropriate once T7 resolves and the
  Outrigger integration (`SOW-Entry-ATOMIC-DER-Migration.md`) begins consuming the
  primitive.
- **Origin:** `JGDMS-STD-009-Service-RemoteFunction-Annotation-Model-v0.1-DRAFT.md` §8's 2026-07-17
  decision note (filters are not standard JVM bytecode; the same mechanism must serve both Rust and Java
  services) and the format-recommendation note that follows it (CEL grammar/semantics, custom
  self-written evaluators over the existing DER encoding — not `cel-java`/`cel-rust` as dependencies).
  This SOW is the task breakdown for building that primitive.
- **Companions:** `JGDMS-STD-009-...-DRAFT.md` §8/§11 (the consumer-facing contract and the two first
  consumers, Outrigger and Reggie — read first, this SOW assumes the decision history there);
  `JGDMS-STD-006-DER-WireFormat-*-DRAFT.md` (the wire-encoding substrate this extends);
  `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 and `SOW-SubProcessDynamicPolicy.md` (the smart-proxy
  isolation machinery this SOW deliberately does *not* assume applies here — see §3);
  `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` §1a (why CEL cannot substitute for genuinely
  stateful smart proxies — the boundary this SOW's format must not cross);
  `DESIGN-CorroborationFramework.md` §8's cross-pointer (the confirmed Survey-zoot application, informing
  §2's custom-function requirement below, not itself in scope here).

---

## 1. What this closes, precisely

STD-009 §8's original filter model assumed a filter is a real, compiled, downloaded Java class
(`@AtomicSerial @Stateless`, RULE-F1/F3, BAE bytecode load-verification). That model was superseded
2026-07-17: filters are not bytecode at all, and the same execution format must work for a Rust host and
a JVM host without divergence. Nothing currently exists to realize that decision — no grammar spec, no
DER wire-encoding, no evaluator in either language, no verification story for the new format. This SOW
builds the core primitive: **a small, non-Turing-complete, side-effect-free expression format, its
DER wire encoding, and two independently-written, spec-conformant evaluators (Java, Rust).**

---

## 2. Design shape (as decided/recommended so far — see STD-009 §8 for full provenance)

- **Grammar/semantics: modeled on CEL, not adopted as a dependency.** Comparison operators
  (`<`/`>`/`<=`/`>=`/`==`/`!=`), boolean combinators (`&&`/`||`/`!`), field access, string operations
  (`contains`/`startsWith`/`endsWith`), arithmetic on numeric types, and a **small, fixed, closed set of
  platform-provided custom functions** (not user-extensible) — no loops, no recursion, no assignment, no
  user-defined functions. Bounded-by-construction, not bounded-by-runtime-gate (§8's own reasoning).
  Confirmed by research (STD-009 §11's note) that this vocabulary already covers everything found in
  Outrigger/Reggie's real matching code — no grammar gap discovered.
- **"Computed" (STD-009 §11's originally-vague term) is now scoped as bounded, pure, fixed-formula
  value transforms, not just boolean predicates** — confirmed via the Survey-zoot application discussion
  (`DESIGN-CorroborationFramework.md` §8). Concrete example: converting raw bearing/elevation/distance
  into vector components using a small set of platform-audited trig-adjacent custom functions. **This
  SOW's grammar spec (T1) must accommodate value-returning expressions, not only boolean-returning
  ones** — a scope expansion from "filter" (boolean) to "filter/transform" (typed value), worth naming
  explicitly since it's easy to under-scope T1 around booleans only.
- **DER-native wire encoding, reusing STD-006, not a separate encoding scheme.** The expression tree
  rides the existing DER stream (RULE-D1's hard DER requirement already applies to `@RemoteFunction`
  generally); this SOW extends STD-006 with the tags/grammar needed for expression nodes, not a bolt-on
  format.
- **Two independent evaluator implementations (Java, Rust), not one shared codebase.** Deliberate,
  following the research finding that CEL's own two implementations (`cel-java`, `cel-rust`) are
  independently-maintained against a shared spec and don't guarantee behavioral parity for free — this
  SOW must not repeat that gap silently. A shared, versioned **conformance test suite** (T5) is not
  optional polish; it is how the "same mechanism for Rust and Java" claim (STD-009's own framing) becomes
  actually true rather than aspirational.
- **Verification role, likely much lighter than BAE's bytecode analysis, but still a real gate.** A
  restricted format doesn't need `ClinitBlockingVisitor`-style control-flow analysis, but still needs:
  wire-form well-formedness checking, confirming every custom-function reference resolves within the
  fixed platform allow-list (never a wire-supplied/dynamic function name), and confirming no construct
  outside the grammar's declared bounds was smuggled through a malformed encoding. Scope as its own task
  (T6), not assumed to be "basically free" because the format is small.

---

## 3. Non-goals

- **Not building or assuming UDS-sidecar/process isolation for filters.** `SOW-Unix-Domain-Socket-JERI-
  Transport.md` §12 and `SOW-SubProcessDynamicPolicy.md` were built for genuinely stateful,
  potentially-I/O-capable smart proxies — a CEL-shaped filter's safety comes from being bounded and
  side-effect-free *by construction*, a different property. Whether a process boundary is *additionally*
  wanted for defense-in-depth (e.g. resource/DoS containment, not code-trust containment) is a real, open
  question this SOW does not resolve — see §5 and T7.
- **Not building Outrigger's or Reggie's typed-field-projection matching-runtime work.** STD-009 §11's
  research found this is the *larger* real cost of the whole filter effort (Outrigger: no field is
  typed today, everything is opaque `MarshalledInstance` bytes; Reggie: partial typed carve-out already
  exists for immutable attribute types). That is substantial, per-service, existing-code-touching
  engineering — scope it as its own follow-on SOW once this one's primitive exists, not folded in here.
  *Update 2026-07-20:* that follow-on now exists — `SOW-Entry-ATOMIC-DER-Migration.md` — and it
  **corrects this bullet's framing**: the typed-projection substrate already exists on trunk in
  `jgdms-der` (class-free `ObjectCodec.decodeToFieldMap` over the schema embedded in every `ATOMIC_DER`
  `MarshalledInstanceRecord`; STD-009 §8's 2026-07-20 note has the evidence), so the real per-service
  work is `ATOMIC_DER` adoption by the entry-marshalling paths plus a lazy field projector and
  matching-path wiring — not inventing typed field storage. That SOW also scopes the Outrigger
  filter-integration design (the next bullet's deferred wiring) at Peter's request, 2026-07-20.
- **Not building the Reggie/Outrigger integration wiring itself** (routing a matched-against-a-filter
  candidate through this new evaluator in each service's real matching path) — depends on both this SOW
  and the typed-field-projection follow-on above.
- **Not re-litigating the CEL-vs-custom-AST format recommendation** — that's STD-009 §8's decision
  surface; this SOW assumes it (modeled-on-CEL, self-written evaluators) and builds it.
- **Not a general-purpose scripting/query engine.** Scope stays deliberately narrow — filter/transform
  over declared candidate fields, nothing that reopens the "does this become smart-proxy-shaped again"
  question from `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` §1a.

---

## 4. Execution plan — task breakdown (agent type + effort)

Effort follows the project convention (security → `xhigh`, class/wire-resolution → `max`,
mechanical → `medium`, test-debug → `xhigh`); orchestration follows *fresh implement → review gate*,
with a **parallel review board** for high-blast-radius/security work. This format is, by design, the
*sole* safety boundary around untrusted, adversary-authored input reaching two different production
runtimes (Java and Rust) — hold the grammar spec and both evaluators to the same adversarial standard
this session's BAE and `SubProcessDynamicPolicy` work already established.

### Summary

| Task | Deliverable | Implement (agent · effort) | Review | Depends |
|------|-------------|------------------------------|--------|---------|
| **T1** · Grammar/semantics spec | Formal spec of the expression format: value types, operators, the closed custom-function set (including the value-transform/"computed" case, §2), explicit non-Turing-completeness argument (why no construct in the grammar can fail to terminate or produce a side effect). This is the document every other task implements against — get it wrong and both evaluators inherit the mistake identically. | general-purpose · **XHIGH** (defines what "safe by construction" means for this whole mechanism — a foundational security decision, not just a document) | **parallel board** (2-3 adversarial) — specifically probe for any construct, combination, or custom-function interaction that could be non-terminating or side-effecting despite the design intent | none — can start immediately |
| **T2** · DER wire encoding | Extend STD-006 with tags/grammar for expression-tree nodes (operators, field references, literals, custom-function calls) — reuse existing DER machinery, no new encoding scheme. Must reject (not silently coerce) any wire content outside T1's declared grammar. | general-purpose · **XHIGH** (wire-format bugs in this codebase's history have been real deserialization-class security bugs — DER encoding is not a mechanical task here) | **parallel board** | T1 |
| **T3** · Java evaluator | Tree-walking evaluator over the T2 wire form, in Java, `jgdms-der`/`jgdms-jeri`-adjacent module. Bounded, side-effect-free by construction per T1; the fixed custom-function implementations live here (platform-audited, not user-suppliable). | general-purpose · **XHIGH** (this is the actual trusted-computing-base component — a bug here is a sandbox escape) | **parallel board**, adversarial-probing mandate — build and run real probes against the implementation (crafted wire forms, boundary values, custom-function edge cases), not just design review | T1, T2 |
| **T4** · Rust evaluator | The same evaluator, independently written in Rust, for non-JVM hosts (the Rust JERI / GLS-instrument-federation case this SOW's origin discussion named). Same T1 spec, same T2 wire form. | general-purpose · **XHIGH** | **parallel board**, same adversarial-probing mandate as T3 — additionally probe for **behavioral divergence from T3** on the same inputs, not just internal correctness | T1, T2 |
| **T5** · Cross-language conformance suite | A shared, versioned test corpus run against **both** T3 and T4, asserting identical results for every case — this is what makes "same mechanism for Rust and Java" true rather than assumed. Include boundary/edge cases (numeric overflow, string edge cases, custom-function argument edges) specifically because CEL's own two implementations don't guarantee this for free (research finding, §2). | general-purpose · **XHIGH** (test-debug) | **parallel board** | T3, T4 |
| **T6** · Verification/load-gate | The (lighter than BAE, but real) gate: wire-form well-formedness, custom-function-reference allow-list enforcement, rejection of any out-of-grammar construct. Decide where this lives (a BAE extension, or a narrower purpose-built checker) and whether it's needed *in addition to* T2's own encode/decode-time rejection or is redundant with it — don't assume redundant without checking. | general-purpose · **HIGH** | single reviewer (security-literate) + adversarial spot-check | T1, T2 |
| **T7** · Sidecar/process-boundary applicability decision | Resolve the open question from STD-009 §8.5: does a CEL-shaped filter need any process isolation at all, given its safety is by construction, or does defense-in-depth (resource/DoS containment, not code-trust containment) still argue for one? If yes, scope how it reuses (or deliberately doesn't reuse) `SOW-Unix-Domain-Socket-JERI-Transport.md` §12's machinery. This is a decision-and-design task, not much new code either way. | general-purpose · **MEDIUM** | single reviewer | T1 (needs the format's real shape before this is answerable) |
| **T8** · Documentation reconciliation | Update STD-009 §8's decision-note scaffolding to describe the *finished* mechanism (currently describes the plan); retire the still-present pre-2026-07-17 bytecode-model text (RULE-F1/F3's old mechanism, §8.1/§8.3) properly rather than leaving it flagged-but-unedited; fold in the Survey-zoot "computed" resolution as a worked example. | general-purpose · **MEDIUM** | single reviewer | T1-T7 substantially landed (should describe reality, not aspiration) |

### Sequencing

- **Now:** **T1** — the long pole, and nothing else can meaningfully start without it.
- **After T1:** **T2** (wire encoding), **T7** (isolation-posture decision — can run in parallel with T2,
  doesn't block it).
- **After T1, T2:** **T3**, **T4** (the two evaluators — can run in parallel with each other), **T6**
  (verification gate — can run in parallel with T3/T4).
- **After T3, T4:** **T5** — cross-language conformance, the check that actually validates the
  "same mechanism" claim.
- **Last:** **T8**, once the mechanism is real, not planned.

### Notes

- **T1, T3, T4 are the ones to guard hardest** — T1 because it's the actual security-defining artifact
  (get the "safe by construction" argument wrong here and every downstream task inherits it silently);
  T3/T4 because they're the literal trusted-computing-base code standing between adversarial input and
  two production runtimes. Same standard as this session's BAE/`SubProcessDynamicPolicy` adversarial
  rounds: build-and-run probes against the actual implementation, not "the design reads sound."
- **T5 is not optional test coverage — it is the mechanism's central claim, made falsifiable.** Don't let
  it be scoped down to a token check once T3/T4 are "done."
- **T7's answer could retroactively matter to `SOW-BAE-Timing-Sidechannel-Denial.md`** — if T7 concludes
  "no isolation needed, evaluate in-process," that's a new co-residency case (untrusted filter expression
  evaluated inside a service's own process) not previously analyzed under that SOW's threat model. Worth
  a cross-check against §1b's reasoning once T7 lands, not assumed automatically fine because the format
  is "safe by construction" — construction-safety and timing-side-channel-safety are different
  properties, the same distinction this whole document set has been careful to hold elsewhere.
- **Outrigger/Reggie integration (this SOW's non-goals) is the natural next SOW once T1-T8 land** — don't
  let this SOW's completion be read as "filters are done"; the primitive existing and two services
  actually using it are different milestones.

---

## 5. Open questions, not resolved here

1. **Does T7 conclude a process boundary is still wanted, and if so, does it reuse
   `SOW-Unix-Domain-Socket-JERI-Transport.md`'s machinery or need something lighter-weight of its own?**
   Not guessed at here — the machinery built for stateful smart proxies (per-SPIFFE-principal pooling,
   DGC lifecycle, `SubProcessDynamicPolicy`) may be substantial overkill for a bounded, stateless
   evaluation, or may turn out to still be the right reusable shape for unrelated reasons (resource
   accounting, defense-in-depth). Genuinely open.
2. **Exact custom-function set** — T1 needs to enumerate the *closed* list (comparison helpers, string
   helpers, the Survey-zoot-motivated value-transform functions, whatever else Outrigger/Reggie's
   eventual integration needs) rather than leave it open-ended/extensible, but the concrete list isn't
   fixed by anything decided so far.
3. **Whether T6's verification gate is genuinely additive to T2's own decode-time rejection, or
   redundant with it** — flagged in T6's own row, not resolved here; needs checking once T2's actual
   decode behavior exists, not assumed either way.

---

*End of DRAFT. No production code was written or modified in producing this document.*
