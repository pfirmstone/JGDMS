# Scope of Work — Compatibility-gate CI enforcement: wiring the API-compat and serial-schema gates into the build, and fixing the fixed-baseline blind spot

- **Drafted:** 2026-07-19.
- **Status:** DRAFT — task breakdown for review, no implementation started.
- **Origin:** An adversarial review of an unrelated change (Reggie `-dl` grant tightening / `JoinManagerImpl`
  proxy-shape fix) surfaced two confirmed *process* gaps in the two compatibility gates this repo already
  ships but does not enforce:
  1. **Neither gate runs automatically.** The two check scripts (`check-api-compat.sh`,
     `check-serial-schema.sh`, both at repo root) are invoked by hand only. No `.github/workflows/`,
     no `.gitlab-ci.yml`, no `Jenkinsfile`, no git hook, and **no Maven `pom.xml` binds either gate to a
     lifecycle phase** — a plain `mvn install` / `mvn verify` does *not* run them. A breaking change lands
     unless a human remembers to run a script.
  2. **The japicmp baseline is fixed at `3.1.0`.** `check-api-compat.sh` hardcodes `BASELINE="3.1.0"` and
     diffs each in-scope artifact against that one released version fetched from Maven Central. A class made
     `public` on trunk *after* 3.1.0 and later flipped back to package-private is invisible to the gate:
     there is nothing to diff against for the "public on trunk" state, because the 3.1.0 baseline never had
     it public. **Observed, not hypothetical:** `MapSerializer` went `public` sometime after 3.1.0, then was
     flipped back to package-private in an unrelated recent change; the gate reported PASS purely because the
     class coincidentally landed back at its exact 3.1.0-released shape — the transition was never detected
     or validated.
- **Companions / prior art:**
  - Memory `jgdms-api-compat-tooling` — records both gates as "BOTH GATES LIVE TRUNK" (japicmp vs 3.1.0 +
    serial-schema + no-JOSS lint). "Live" there means *the tooling exists and runs when invoked*, not *the
    build enforces it*. This SOW closes exactly that gap between "exists" and "enforced."
  - `SOW-PreferredList-Analyzer.md` and `tools/preferred-list-analyzer/` — the closest existing pattern for
    a repo-local static-analysis gate delivered as a `tools/` Maven module. The serial-schema tracker
    (`tools/serial-schema-tracker/`) follows the same module shape.
- **Effort calibration (read this before prioritizing):** every task here is **process / build-tooling
  hardening, not a security mechanism.** These tasks must **not** be scheduled or resourced against the
  security-critical SOWs in this folder (`SOW-Smart-Proxy-Isolation-Wiring.md`,
  `SOW-BAE-Timing-Sidechannel-Denial.md`, `SOW-SubProcessDynamicPolicy.md`, the UDS increments), whose tasks
  are `XHIGH`/`MAX` with parallel adversarial boards. Nothing here is `XHIGH`. **MEDIUM is the ceiling** for
  the implementation tasks; the decision task is LOW–MEDIUM. Reviews are single-reviewer, not boards. Said
  explicitly so this does not get mis-prioritized.
- **No production code, and no CI configuration, was written or modified in producing this document.** It is
  a task breakdown only; all task-level status below is DRAFT / not started.

---

## 1. Current state, confirmed by inspection

Verified directly against `trunk` (`c7108864d`), not taken on faith from the origin summary:

### 1.1 The two gates and how they run today

| Gate | Entry point | Tooling it drives | Fails on |
|------|-------------|-------------------|----------|
| **Binary / public-API compat** (synchronic) | `check-api-compat.sh` (repo root) | Fetches `japicmp-0.26.1-jar-with-dependencies.jar` from Central; for each in-scope artifact fetches the `3.1.0` baseline jar, runs `japicmp -a public -b --ignore-missing-classes`, pipes the combined report through `classify_breaks.py` which classifies each break against `docs/JGDMS-API-Compatibility-Accepted-Breaks.md`. | **UNCLASSIFIED** (candidate-accidental) binary-incompatible breaks only. Intended/forced breaks (Serializable removal, `java.rmi.activation` migration, etc.) are in the accepted-breaks ledger and pass. |
| **Serial-schema evolution** (diachronic) | `check-serial-schema.sh` (repo root) | Runs `org.apache.river.tool.serial.SerialSchemaTracker` (built into `tools/serial-schema-tracker/target/classes`) in `check --golden serial-schema.golden --scan <every target/classes> --fail-on-change` mode. | **`@SerialEntry` registrar-hash change** (GATE — stored entries would become invisible, STD-005 RULE-7). `@AtomicSerial` `serialForm()` field changes are **INFORMATIVE only**, never fail the build. |

**In-scope artifacts for japicmp** (from the script): `jgdms-platform jgdms-lib jgdms-jeri jgdms-collections
jgdms-activation jgdms-discovery-providers jgdms-lib-dl jgdms-url-integrity`. Both scripts are designed to run
*after* a full build/install (they read module `target/` jars/classes, or `~/.m2`).

### 1.2 CI wiring — confirmed absent

- **No CI service config anywhere in the repo:** no `.github/` directory, no `.gitlab-ci.yml`, no
  `Jenkinsfile`, no `.circleci/`, no Travis config. (The doc `AI_Agent_SPIFFE-CI-context_2026-05-05.md`
  mentions "CI infrastructure," but it is about SPIFFE **unit-test** setup delivered in PR #229 — *not* a
  build pipeline, and *not* the compat gates. No signal anywhere of a CI service being adopted.)
- **No Maven lifecycle binding of either gate.** `tools/pom.xml` references `serial-schema-tracker` only as a
  build `<module>` (it *compiles the tracker tool*); it does not execute the `check`. `serial-schema-tracker/pom.xml`
  only sets the tool's `Main-Class` for its jar. No `pom.xml` invokes either script or the checks via
  `exec-maven-plugin`, `maven-antrun-plugin`, or a phase-bound execution. **A plain `mvn install` / `mvn
  verify` runs neither gate.**
- **No git hook** invokes either script.

Net: the gates are documentation-referenced, human-invoked tools. Enforcement today is "someone remembered."

### 1.3 Baseline mechanism — confirmed fixed

`check-api-compat.sh` line 29: `BASELINE="3.1.0"`, a hardcoded string. Baselines are fetched per-artifact from
Maven Central (`repo1.maven.org/.../$art/3.1.0/$art-3.1.0.jar`) and cached under `target/api-compat/`. **There
is no provision for a rolling baseline, a previous-tag baseline, or a previous-commit baseline** — the diff is
always current-build vs the single released 3.1.0 artifact. This is precisely the blind spot: any API surface
that appeared *and* disappeared entirely within the `3.1.0 → HEAD` window is undetectable, because neither
endpoint of the diff ever saw it. (`classify_breaks.py` does separately report "surface-hygiene widenings /
newly public" vs baseline, but that too is anchored to 3.1.0 and says nothing about a post-3.1.0 public →
package-private *reversion*.)

---

## 2. Recommended approach

### 2.1 CI wiring — **primary: bind both gates to the Maven `verify` phase**

Recommend making both gates run as part of `mvn verify` / `mvn install`, via a phase-bound execution in the
build (an aggregator-level profile that shells the existing scripts through `exec-maven-plugin`, or ports the
script bodies into a small verify-bound mojo/antrun step — see T2 for the shape decision). Reasoning:

- **Lowest friction, CI-service-agnostic.** There is no CI service today and no committed plan to adopt one.
  Binding to `verify` means the gate fires for *every* developer running `mvn install` locally **and** in
  *whatever* CI service is eventually chosen, with zero extra pipeline config — the build itself is the
  enforcement point. A future GitHub Actions / GitLab / Jenkins job only has to run `mvn verify`.
- **Matches the scripts' existing contract** — both already require a full build first and read `target/`
  outputs, so `verify` (after `package`, before `install`) is the natural home; the jars they diff already
  exist by then.
- **One caveat to design around (T2):** `check-api-compat.sh` needs network access to Central to fetch
  baselines and japicmp. A phase binding must **degrade sanely offline** (the script already `skip`s an
  artifact whose baseline it cannot fetch). Decision for T2: whether the compat gate is bound to `verify`
  unconditionally, or behind an active-by-default profile that a documented `-D` flag disables for offline
  builds, so a network outage cannot wedge every local `mvn install`. Recommend **active-by-default profile
  with a documented opt-out**, failing the build on a *detected unclassified break* but *skipping* (warn, not
  fail) when a baseline is genuinely unreachable — never fail-open on a *break*, never hard-fail on a
  *network outage*. The serial-schema gate is fully offline (local golden), so it binds unconditionally.

**Not recommended as primary:** authoring a `.github/workflows/*.yml` now. No evidence the repo is moving to
GitHub Actions; inventing that requirement picks a CI service the project has not chosen and leaves local
`mvn install` still unguarded. Note it as a trivial follow-on *once* `verify` binding exists (the workflow
would be a three-line "run `mvn verify`"), not as the mechanism.

### 2.2 Baseline strategy — **primary: dual-baseline (last release tag AND previous trunk snapshot)**

Recommend **option (a), dual-baseline:** diff current build against **both** (i) the last *released* version
(3.1.0 today, and whatever supersedes it) **and** (ii) the **immediately preceding trunk state** — the
previous release/RC tag if one exists, else a periodically-refreshed trunk snapshot baseline (an artifact
published to a local/snapshot repo, or a git-tag-anchored rebuild). Fail if *either* diff shows an unclassified
break. Reasoning on the false-positive / false-negative tradeoff:

- **Closes the observed blind spot.** The `MapSerializer` public→package-private reversion is invisible against
  3.1.0 but **visible against a previous-trunk baseline** that captured the post-3.1.0 public state. Catching
  transitions requires a baseline that saw the intermediate state; only a trunk-anchored second baseline does.
- **Keeps the release-contract check honest.** The 3.1.0 diff is what actually protects downstream consumers of
  the *released* API; dropping it (pure option (b) rolling baseline) would lose the "did we break the last
  thing we shipped to users" guarantee. Dual-baseline keeps both questions answered.
- **False-positive cost is bounded and already handled.** The extra trunk baseline will surface *intended*
  in-flight changes as breaks — but `classify_breaks.py` + the accepted-breaks ledger is the exact mechanism
  for annotating intended breaks, so a legitimately-changing surface gets one ledger entry, not a permanent
  red build. Recommend the trunk baseline **fail only on UNCLASSIFIED** (same policy as the release baseline),
  so noise is opt-in-to-silence, never silent-by-default.
- **False-negative residue, stated honestly:** dual-baseline still cannot see a surface that appeared *and*
  vanished entirely *between two consecutive trunk snapshots*. Mitigate by anchoring the trunk baseline to
  **every release/RC tag** (finer than "last release only") rather than a coarse periodic snapshot; the
  finer the snapshot cadence, the smaller the invisible window. This residue is acceptable for a
  process-hygiene gate and should be documented, not papered over.
- **Confirmed 2026-07-19 (T3 board reviewer, cross-checking this scoping against the actual repo history):**
  there are **zero version/RC tags between `3.1.0` and `HEAD`** (a 7-year gap) — the "previous release/RC tag
  if one exists" branch above is empty today, so the strategy as written would rest entirely on a
  not-yet-built periodic-snapshot mechanism with no immediate anchor. **But an existing tag already serves as
  a zero-cost trunk baseline: `baseline/pre-ai-agents`.** `git log -S` on `MapSerializer` confirms it went
  `public` on 2021-05-11 (`211eadc75`, well after the 2019 3.1.0 release) and stayed public for ~5 years
  until this session's revert — any trunk baseline in that window, including `baseline/pre-ai-agents`, would
  see the public state and flag the reversion. **T1 should lock this tag as the trunk-baseline source
  immediately** rather than waiting on a snapshot-provisioning mechanism to be built — it retroactively
  covers exactly the observed `MapSerializer` case at zero additional infrastructure cost, and a
  periodic-snapshot mechanism (for tightening the window going forward) can still be built as a later
  refinement, not a blocker for T1/T3.

**Rejected:** pure rolling baseline (option b) — loses the released-API contract check. Considered and folded in:
extending `classify_breaks.py`'s existing "newly public" surface-hygiene report to also flag *reversions* — a
useful **secondary** signal but it cannot substitute for a real second baseline (it still only knows the two
jars it is handed).

### 2.3 Explicit non-goal, stated plainly (not left implied) — the serial-schema gate's `@AtomicSerial` blind spot

**Confirmed 2026-07-19 (T3 board reviewer):** none of this SOW's tasks change the serial-schema gate's
existing semantics — `@AtomicSerial` `serialForm()` field changes remain **informative-only** (§1.1), never
build-failing, even after T1–T5 land. A genuinely breaking future field **removal or retype** on an
`@AtomicSerial` class would still pass `mvn verify` silently post-enforcement, exactly as it does today. This
SOW closes the *CI-wiring* gap and the *japicmp-baseline* gap; it does **not** close the *serial-schema
severity* gap (deciding whether field removal/retype should itself become gate-failing is a separate,
`@AtomicSerial`-wire-compatibility-policy decision, out of scope here — flag it as a candidate follow-up SOW,
not something this one silently fixes by association with "compat gate enforcement.")

---

## 3. Execution plan — task breakdown (agent type · effort · risk)

Orchestration follows the project norm (*fresh implement → single-reviewer gate*). **No parallel adversarial
boards** — this is tooling hardening, not a security mechanism. Effort ceiling is **MEDIUM**; see the calibration
note in the header.

### Summary

| Task | Deliverable | Implement (agent · effort) | Review | Depends |
|------|-------------|-----------------------------|--------|---------|
| **T1** · Decision / current-state ratification | Ratify the findings in §1 (already gathered) and lock the two decisions in §2: (a) Maven-`verify`-phase binding as the CI mechanism, active-by-default profile with documented offline opt-out; (b) dual-baseline (last release + per-release/RC-tag trunk baseline), fail-on-UNCLASSIFIED on both — **lock `baseline/pre-ai-agents` as the immediate trunk-baseline source (§2.2)**, since no version/RC tag exists between 3.1.0 and HEAD and this existing tag already retroactively covers the observed `MapSerializer` case at zero provisioning cost; a periodic-snapshot mechanism for tightening the window further is a later refinement, not a T1 blocker. Confirm the in-scope artifact list (§1.1) is still correct. Output: a short decision record appended here or as an ADR; **no code.** | general-purpose · **LOW–MEDIUM** (analysis/decision; most of the investigation is done in §1–§2) | single reviewer | none |
| **T2** · Maven `verify`-phase binding for both gates | Bind both gates so `mvn verify` / `mvn install` runs them. Decide script-shell (`exec-maven-plugin` invoking the existing `check-*.sh`) vs port-to-mojo/antrun — recommend **script-shell first** (reuses the audited scripts verbatim, smallest surface). Aggregator-level active-by-default profile; documented `-D` opt-out for offline. **Serial-schema gate binds unconditionally** (offline, local golden). **API-compat gate:** fail on unclassified break, **skip-with-warning (not fail) when a baseline is unreachable** (network outage must not wedge local builds); never fail-open on an actual break. Ensure ordering: gates run after the jars/classes they read exist. | general-purpose · **MEDIUM** (Maven lifecycle wiring; care around offline degradation and phase ordering, but mechanical) | single reviewer | T1 (decisions locked) |
| **T3** · Dual-baseline implementation in the API-compat gate | Extend `check-api-compat.sh` (+ its baseline-fetch/cache logic) to diff each in-scope artifact against **both** the released baseline (existing `3.1.0` path, keep as-is) **and** the previous-trunk baseline chosen in T1. Fail if either diff yields an unclassified break (reuse `classify_breaks.py` unchanged where possible; extend it only if the combined-report format needs a second section). Parameterize the release baseline version (stop hardcoding `3.1.0` so the next release rolls forward without a code edit). Optional secondary: extend the "newly public / surface-hygiene" report to also flag public→package-private reversions. | general-purpose · **MEDIUM** (shell + baseline sourcing; the trunk-baseline provisioning from T1's decision is the fiddly part) | single reviewer | T1 (baseline-location decision), and pairs with T2's binding |
| **T4** · Verification — deliberately break the build, confirm the gate catches it, revert | On a throwaway scratch branch: (i) introduce a real unclassified public-API break (e.g. remove/narrow a public method on an in-scope artifact) and confirm `mvn verify` now **fails** via the T2 binding — and that the **dual-baseline** specifically catches a post-release-then-reverted surface change that the 3.1.0-only gate missed (reproduce the `MapSerializer`-shaped case: make a class public, snapshot the trunk baseline, revert to package-private, confirm the trunk-baseline diff flags it). (ii) Introduce a breaking `@SerialEntry` registrar-hash change and confirm `mvn verify` fails via the serial-schema gate. (iii) Confirm an *intended* break, once ledgered, passes. Then **revert everything** — scratch branch discarded, nothing merged. Output: a short verification log proving each gate fires. | general-purpose · **MEDIUM** (test-debug against the real build path; project norm is `xhigh` for security test-debug, but this is process tooling — MEDIUM) | single reviewer | T2, T3 (both must be landed to verify against) |
| **T5** · Closeout / documentation | Update the docs that currently describe the gates as human-invoked to describe them as `verify`-bound and dual-baselined; update memory `jgdms-api-compat-tooling` note ("LIVE TRUNK" → "ENFORCED via `mvn verify`, dual-baseline"). Add a `.github/workflows` follow-on note (trivial `mvn verify` job) as an *optional* future step, explicitly not required for enforcement. Describe reality, not aspiration. | general-purpose · **MEDIUM** | single reviewer | T2–T4 landed |

### Sequencing

- **First:** **T1** — ratify state, lock both decisions, settle the one open sub-decision (trunk-baseline
  location). Cheap; unblocks everything.
- **Then, parallel:** **T2** (phase binding) and **T3** (dual-baseline) — largely independent; T3 changes the
  script the T2 binding invokes, so land T3's script changes and T2's binding together or T3 slightly ahead.
- **After T2 + T3:** **T4** — verification against the real, wired build.
- **Last:** **T5** — closeout once enforcement is real.

### Notes

- **Do not resource this against the security-critical SOWs.** Repeated from the header because it is the most
  likely mis-prioritization: MEDIUM ceiling, single-reviewer gates, no adversarial boards. The blast radius of
  a bug here is "a compat break slips through one more time" (the status quo), not a security regression.
- **Reuse the audited scripts.** The scripts and `classify_breaks.py` already encode the accepted-breaks
  policy and the gate semantics correctly; T2/T3 should wrap and extend them, not reimplement — smallest new
  surface, least chance of subtly changing what "a break" means.
- **The offline-degradation rule is load-bearing, not a formality (T2).** A gate that hard-fails every local
  `mvn install` when Central is unreachable will be disabled by developers within a day, and then enforces
  nothing. Skip-with-warning on unreachable baseline, fail only on a *detected* break.
- **State the residual false-negative (T1/T3).** Dual-baseline shrinks but does not eliminate the
  appeared-and-vanished-between-snapshots window; the finer the release/RC-tag baseline cadence, the smaller
  it gets. Document the residue rather than implying total coverage.

---

## 4. Explicit dependency ledger

| This SOW's task | Depends on / feeds | Direction |
|---|---|---|
| T1 decision record | §1 findings (current state), §2 recommendations | ratifies |
| T2 `verify` binding | T1 (mechanism decision + offline policy) | consumes |
| T3 dual-baseline | T1 (trunk-baseline location decision); existing `check-api-compat.sh` + `classify_breaks.py` | extends |
| T4 verification | T2, T3 (must be landed to break-and-confirm against the real build) | consumes |
| T5 closeout | T2–T4 landed; memory `jgdms-api-compat-tooling` | describes reality |
| (future) `.github/workflows` `mvn verify` job | T2 (`verify` binding exists) | optional follow-on, not required for enforcement |

---

*End of DRAFT. No production code, and no CI configuration, was written or modified in producing this
document.*
