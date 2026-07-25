# SOW — Outrigger CEL Filter Pushdown: Next-Session Handoff (2026-07-25)

Drafted end of a long, productive multi-thread session, for continuation after `/clear`.
Companion memory (READ FIRST): `jgdms-outrigger-cel-filter-pushdown-design`,
`jgdms-collection-valued-entry-fields-design`. B1 design detail:
`docs/DESIGN-Outrigger-CEL-Filter-B1.md`.

---

## 0. Goal & where we are

**Goal:** demonstrate CEL filtering in Outrigger — a client authors a text predicate, it
travels as canonical DER, and the server evaluates it **class-free** over entry fields at the
match chokepoints (filter what byte-equality template matching cannot express), fail-closed.

**MERGED + PUSHED to `origin/trunk` (tip `bc59fd28f` at handoff):**
- **CEL authoring module** (`jgdms-cel-authoring`): text parser + canonical-DER encoder +
  schema-aware overloads. (origin/trunk 3b981dd2c earlier.)
- **EntryRep-v2** (units 1+2): whole-entry DER wire format — one `body` per entry, schema
  chain carries field NAMES+types, `EntrySchemaGenerator`, `EntryV2Codec` SPI, Outrigger
  integration (`matches()` over slice bytes). Flag-day (born-v2, no v1 read-back) RATIFIED.
  (merged at 87932373c.)
- **COLL-1**: first-class top-level collection VALUES in jgdms-der (`[16]` stream item,
  `SequencedSet`/`SequencedMap` order-preserving). (merged at 87932373c.)
- **Showcase**: demo-3 relabel "plain text"→"JSON w/ names"; demo-6 collection-equality added;
  single-command parent `run-demos.ps1/.sh` (all 6 demos), hardened (clean build + fail-fast).

**IN FLIGHT (NOT merged):**
- **B1** (CEL filter **API + envelope wire + admission seam**): board-clean (both seats),
  all fixes applied, `mvn clean test` GREEN. Branch `feat/cel-filter-b1` tip **`fed5d8f6d`**
  (worktree `wt-b1`). **One decision pending before merge — see §2.1.**

---

## 1. Next-session plan (follow these recommendations, in order)

### 1.1 Finish B1 — fold in the JavaSpace05 query methods, then merge
Per the recommendation Peter leaned toward: add filtered equivalents of the JavaSpace05
*query* methods B1 didn't cover — **`contents(...)`** (the marquee class-free filtered
iterator) and **bulk `take(Collection templates, ...)`** — to `FilteredJavaSpace`
(`net.jini.space` in `jgdms-lib-dl`), with their `OutriggerServer` overloads + admission,
throwing `EVALUATION_NOT_WIRED` until B3 (loud-break). `registerForAvailabilityEvent` is
already done; `write`/`snapshot` aren't matches (no filter). Multi-template ops need the F3
per-template `CompiledFilter` (one filter, all-templates-must-pass) — B3 wires the eval.
Then light re-verify + **merge B1 to trunk**.
- Resume B1 implementer **`a2819783520c8ac0e`** if resumable post-`/clear`; else re-dispatch
  fresh from `docs/DESIGN-Outrigger-CEL-Filter-B1.md` + this SOW.

### 1.2 B2 — class-free projection
Implement `EntryProjection implements CandidateProjection` (contract pinned in B1 memo §6):
reads ONLY predicate-referenced field values from the candidate's own v2 schema, class-free
(via `ObjectCodec.decodeToFieldMap`, no `Class.forName`/ctor/`check`), fail-closed, absent-vs-
undeclared distinction, `entrySchemaDigest`-keyed. Then parallel board review.

### 1.3 B3 — evaluation (confused-deputy safe)
Wire CEL eval at the match chokepoints per the design: **eval INSIDE the confirm window**
(after `canPerform`, BEFORE `grab` — predicate-false ⇒ never captured, no un-take primitive);
fan-out (journal thread) per-txn **entitlement gate** (`transition.getTxn()==null || ==
registrant's txn`) + **catch-everything** (escaping exception kills all event delivery);
per-template multi-filter (F3); the **null-key ⇒ applies-to-all-candidates, per-candidate name
resolution, missing-field ⇒ fail-closed exclusion** rule (F1 option-b, Peter-ratified). Board
review; board ratifies the per-eval **cost ceiling** under the handle monitor (D2, deferred).

### 1.4 The demo (`demo7`)
`WeatherReading{Double temperatureCelsius; String stationName}` ABSENT from the server
classpath; author text `temperatureCelsius > 20.0 && stationName.startsWith("North")`; filtered
`contents`/`take`; assert exactly the warm-northern entries return (server-side, class-free) +
the B3 confused-deputy proof (a txn-private entry not matched by a non-participant; predicate
never ran against it; matches after commit).

### Parallel / optional
- **COLL-2** — collection-valued entry fields (EntryRep integration): interface-only decls +
  `SequencedSet`/`SequencedMap` ordered (Q2/Q3 RATIFIED). Not needed for the scalar demo. Design:
  `jgdms-collection-valued-entry-fields-design` memo.

---

## 2. Outstanding decisions / items

1. **[PETER] B1 JavaSpace05 fold-in** — Peter leans YES (fold `contents`+bulk-`take` in before
   merging B1, for a complete permanent interface). Confirm + execute (§1.1).
2. **qa deployment matching suite** — the multi-JVM Ant suite (`…outrigger.matching.*`) is a
   REQUIRED **pre-RELEASE** gate (board ruling), NOT pre-merge. Needs a built `dist/` + polpAudit
   regen of `qa/harness/policy/defaultsecuretest.policy` (currently the emptied-form landmine) +
   run. **BEWARE the jtreg/qa vacuous-pass landmine** — confirm tests actually execute.
   (qa harness lives at the git-ROOT `<clone>/qa`, a sibling of the reactor — see onboarding memory.)
3. **[BOARD] B3 per-eval cost ceiling** under the handle monitor (D2, deferred to B3).
4. **Non-blocking board follow-ups**: dead `superclassNames` SPI param (EntryRep-v2); `entry()`
   javadoc re local-only field-value resolution; vestigial JOSS-named tests; `orderedset:`/
   `orderedmap:` duplicate-retention (COLL-1 hardening); seal `ImmutableSet`/`ImmutableMap`;
   `COST_EXCEEDED` live test probe; `EVALUATION_NOT_WIRED` full-exported-server test.
5. **CEL OQ-1 spec note** — document in STD-011/Appendix-B that type-dispatched overloads
   (size/abs/min/max, §B.8.3) are the deliberate exception to the "unknown ⇒ defer" discipline.
6. **Board Reviewer Guidance war story** — append the recursion-meter-bypass lesson to §2.2/G10
   (`review-recursion-meter-bypass-lesson`).
7. **jgdms-der cosmetic tidy** — `StreamSchemaDedup.meterReconstitution` reject messages
   interpolate static default factors, not instance factors (capture the two factors into fields).

---

## 3. Key state / branches / commits

- `origin/trunk` tip at handoff: **`bc59fd28f`** (EntryRep-v2 + COLL-1 + demos + pref-class-loader).
- **`feat/cel-filter-b1`** @ **`fed5d8f6d`** — B1 board-clean, UNMERGED (worktree `wt-b1`).
- Archived branches (merged): `archive/feat/entryrep-v2`, `archive/feat/collection-values`,
  and the CEL-authoring archive.
- **Board/agent IDs (this session — resume via SendMessage if alive post-`/clear`, else
  re-dispatch fresh):**
  - B1 implementer (for §1.1 JavaSpace05 fold-in): **`a2819783520c8ac0e`** (feat/cel-filter-b1).
  - B1 board reviewers (done): Fable `a8335339ba5f497af`, Opus `a32c48e6dbc640901`.
  - Outrigger design/scoping agent: `acb9ab98b9c8e4bd3`.
  - NOTE: per-unit board reviewers were spawned FRESH each unit; the pattern for B2/B3 is to
    re-dispatch fresh reviewers (Fable=canonical/design seat, Opus=adversarial seat) — do NOT
    rely on resuming old review agents. Agent IDs may not survive `/clear`; treat this list as
    provenance, and re-dispatch from the design memos + this SOW if resume fails.

---

## 4. Standing method (how we work — from this session)
- **Build → dual-model board review → fix → (re-verify) → merge**, per unit. Fable = canonical-
  form / design / rule-fidelity seat; Opus = adversarial/exploit-execution seat (they catch
  different things — keep both). Cite `Board-Reviewer-Guidance.md` in every review dispatch.
- Each unit on its own branch + isolated worktree; commit-only, board reviews in detached
  worktrees; merge on Peter's go; archive-tag+delete.
- **Verify merges with `mvn clean` / fresh worktree, never incremental** (stale-target and
  stale-`~/.m2`-dep both masquerade as false failures / ClassNotFound — see appendix).

---

## Appendix A — What we completed this session (2026-07-25)

1. **CEL authoring module** (`jgdms-cel-authoring`): text parser + canonical-DER wire encoder +
   schema-aware overload resolution (reusing the verifier's `StaticTypeChecker`). Dual-model
   board review → MERGE-WITH-FIXES → fixed (surrogate-canonicality G1, parser recursion SOE,
   formatVersion, min/max overload, quadratic caps, 0X; then F1 flat-binary-chain SOE found by
   the re-review and fixed with a completeness audit that caught a sibling). **MERGED+PUSHED.**
2. **Showcase**: demo-3 "plain text"→"JSON w/ names" (audience-correct: standard `ObjectOutput
   Stream`, not Atomic JOSS); NEW demo-6 collection-equality (2 collection classes → identical
   canonical DER; cross-lang Rust/Haskell table grounded in the mapping docs); single-command
   parent `run-demos.ps1/.sh`; demo-6 stale-dep ClassNotFound fixed + both scripts hardened
   (clean build + fail-fast) + a pre-existing Git-Bash classpath bug fixed. **MERGED+PUSHED.**
3. **Service-API-compat-boundary principle** established (Peter): services + proxies are
   implementation (free to change); ONLY the Service API is a compatibility boundary. Drove the
   EntryRep-v2 flag-day.
4. **EntryRep-v2** whole-entry DER wire format — **unit 1** (codec core: STD-006 amendment +
   `EntrySchemaGenerator` + `EntryRepV2Codec`; the crux — a field slice is a PURE FUNCTION of the
   value, context-free — proven, board-verified against the real v1 encode path) + **unit 2**
   (Outrigger integration: `EntryRep` rework, `matches()` over slice bytes, SPI seam, flag-day
   guards, golden update). Two dual board reviews + fix rounds. **MERGED+PUSHED.**
5. **COLL-1** first-class top-level collection values (`[16]` item reusing `CollectionWireTypes`;
   `SequencedSet`/`SequencedMap` ordered; dedup-excluded interior = required for the pure-function
   property). Dual board review. **MERGED+PUSHED.**
6. **B1** CEL filter API + envelope wire + fail-closed admission seam. Dual board review →
   MERGE-WITH-FIXES → fixed (F1 schema-less contract option-b; `FilteredJavaSpace`/
   `FilterRejectedException` relocated to `net.jini.space`/`jgdms-lib-dl`; defensive verify catch;
   others). Board-clean, GREEN, **UNMERGED** (pending §1.1).
7. **COLL-2 design** scoped (interface-only + SequencedSet ratified).
8. **Lessons captured to memory** (see Appendix B).

## Appendix B — Lessons learned (recorded in memory)
- `build-stale-target-shadow-trap` — stale orphaned `target/test-classes` resource shadows the
  real one on the flat classpath (`getResourceAsStream` first-match) → false test failures;
  verify with `mvn clean`. (This session: 13 phantom X500 failures during the EntryRep-v2 merge.)
- Stale `~/.m2` dependency masking: a pre-merge `jgdms-der` jar broke the showcase compile of a
  new demo while leaving older `.class` files → demo-6 ClassNotFound. Same theme; `clean` fixes.
- `review-recursion-meter-bypass-lesson` — a recursion-frame guard is bypassed by loop-built
  depth + by anything that runs before the ceiling (CEL F1; Opus caught, Fable missed).
- `defensive-security-topic-model-switch` (refined) — security *design* review is Fable's lane;
  only offensive *exploit-generation* framing gates it to Opus. Split design(Fable)/exploit(Opus).
- `showcase-audience-java-serialization-framing` — "Java Serialization" = stock `ObjectOutput
  Stream` for the wider audience, never Atomic JOSS; no-acronym viewer text.
- `jgdms-service-api-only-compat-boundary` — the standing architectural rule (see §3 item 3 above).
- Client-compile-time API placement: a public client interface (`FilteredJavaSpace`) can't live in
  a downloaded `-dl` proxy package; it belongs in the API namespace the client depends on directly
  (`net.jini.space` in `jgdms-lib-dl`).

*End. Trunk `bc59fd28f`; B1 `fed5d8f6d` unmerged.*
