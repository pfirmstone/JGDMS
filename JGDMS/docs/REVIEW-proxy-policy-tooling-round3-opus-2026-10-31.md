claude-opus-5-5

# Review: round-3 promotion/generation fixes (uncommitted), 2026-10-31

Reviewer claude-opus-5-5; producer claude-sonnet-5-5 (line 1 of `docs/INVESTIGATION-proxy-policy-tooling-round3-2026-10-31.md`): cross-model. Skills loaded: handover-composition, production-code. E:\ (incl. TRAINING_DOCS_INDEX) not read, per brief. No Python used.
Scratch: `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r3\` (below `OR`). Scripts `attack.sh`, `attack2.sh`, `kill.sh`, `mutants.sh`; outputs `*.out`, `*.log`. The CLI ran from a snapshot of `target/classes` taken after the baseline build (`OR\classes`), never from a mutant.
Repo: the 9 scene files match their pre-review md5s (`md5sum -c OR\bak\pre.md5`: 9 OK); `git status --porcelain` hash b4560f33753c (25 entries) before and after my real-tree plan; nothing committed. Labels: VERIFIED = I ran it or read the line; PLAUSIBLE = read, not run; UNDETERMINED = needs data or the operator. LP = `ListPromotion.java`, PPG = `ProxyPolicyGenerator.java`, PUPP = `PolicyUpdatePostProcessor.java` (all `tools/policy-condenser/src/main/java/org/apache/river/tool/`), BX = `qa/build.xml`.

## Verdict
The plan-as-record design now holds. All 7 round-2 attack shapes and 17 new variants were refused or harmless through the production CLI, the `render` equality is load-bearing, and the three requested mutants die. Two defects remain. (1) The artefact-name fix broke generation of a real harness policy (R1). (2) The `--clean` path contradicts the B2 claim (R2). Separately, plan, check and apply do not re-apply the generator's META/GATE exclusions (R3), so the CI gate reads "ok" for an AllPermission entry.

## Findings (most severe first)
**R1 (MEDIUM-HIGH) The artefact-name fix makes two codebases of one real policy collide; generation crashes part-way. VERIFIED, run.**
`PPG.artefactName` (:1362) now maps both `httpmd://${HOST}:9080/${reggie-dl.jar}` and the `${HOST}:${reggie-dl.jar}` codebase of `qa/harness/policy/defaultsecuretest.policy.old` (md5 bd869b39) to `reggie-dl`. `generate` stages both groups to one path. The first `moveInto` (:732 → :744) moves the second group's file; the second move throws `NoSuchFileException`, uncaught, with a stack trace and rc 1. Result in `OR\w4\out`: 3 artefact dirs (`_`, `phoenix-dl`, `reggie-dl` holding one group's entries); `jsk-dl` never moved; no `.grantperm`.
- Discriminating run, same input bytes: round-2 classes (`review-fable\classes`) give rc 0 and 5 lists (`_`, `__HOST____jsk-dl.jar_`, `__HOST____reggie-dl.jar_`, `__phoenix-dl.jar_`, `__reggie-dl.jar_`).
- Population: 8 inputs (the 7 `*.policy.old|new` plus `qa/src/.../jinitest.policy`, md5s in `OR\w4\in.md5`); this is 1 of the 4 that have proxy grants. The producer did not re-run real policies (round-3 doc :34).
- In `policy-update`, `PUPP.generateProxyArtifacts` :359-362 logs WARNING and continues, leaving the partial `--out`. This also breaks generate's own contract (javadoc :571-576) through an exception that is not I/O bad luck but a plan collision.
- Fix (describe only): before staging, refuse when two groups get one artefact name, naming both codebases. Do not merge silently: a union widens a list.

**R2 (MEDIUM) `--clean` overwrites a hand-written list that the run replaces, while printing `KEPT`. VERIFIED, run (`OR\attack2.out` n15).**
`PPG.generate` :683 `if (!clean) {` skips the foreign check. `removeLists` (:1141) keeps the no-provenance list and notes "KEPT ... --clean does not delete it". `moveInto` (:744, REPLACE_EXISTING) then replaces it. Run: `# my own list` → after `--clean`, first line is the licence header and `grep -c 'my own list'` = 0.
This contradicts round-3 doc :39 ("`--clean` will not delete it either") and `docs/ProxyPolicyGenerator.md` :44 and :125-128. Without `--clean` the run is refused, as claimed.

**R3 (MEDIUM) Plan, check and apply do not re-apply the generator's META and GATE exclusions. VERIFIED, run (`OR\attack2.out` n11 and a phoenix-only rerun in `OR\w3`).**
`PPG.plan` drops GATE classes (:216-221, `DROPPED`) and META classes (:198-205); I confirmed the generator never emits `AllPermission` or `LoadClassPermission "*"`. But `LP.item` (:248) only checks a `# Source policy:` text line (forgeable) and `validateList` parses. So a list in `--out` that appends `AllPermission`, `GrantPermission "java.security.AllPermission"` or `LoadClassPermission "*"` is PROMOTABLE. `--promote-plan-check` exits 0 with `check: ok` (rejection 0 of 3 META/GATE cases; LP.check :185-210 fails only on REMOVED or unparsable), and apply prints `WROTE ... +2 -0` with no flag. `URLPermission` and `UmbrellaGrantPermission` behaved the same in n11.
The human reading one `+` line is the only barrier, and the CI gate is blind under that null. Fix: the planner refuses any live entry of a META/GATE class (a generated list cannot hold one, so its presence means an edited list), and check fails on it.

**R4 (LOW-MEDIUM) BX line endings: a whole-file rewrite that git will not normalise. VERIFIED.**
- HEAD blob is LF. The working copy is CRLF, with a bare-LF line (:729) and a CR CR LF line (:732 `</condition>`), both found with `cat -A`.
- The lone CR makes git classify the file binary: `git ls-files --eol` gives `i/lf w/-text attr/text=auto`, and `git hash-object --path` equals `--no-filters` (0eeef6c0). So `text=auto` will not normalise it, and a commit stores 2674 CRLF lines.
- `git diff --stat` shows 5205 changed lines; `--ignore-cr-at-eol` shows the real 143.
- Gate under the null: the producer's EOL check "CR count equals LF count" (round-3 doc :5, applied to 5 code files) also reads equal for this broken file (`tr -cd` counts CR=2674, LF=2674), so it cannot detect this defect.
- Fix: restore LF, then re-apply the 143 lines.

**R5 (LOW) Two artefacts can target one module; apply writes both and the last approved wins. VERIFIED, run (n5 map, n13 version suffix).**
`LP.item` refuses two modules for one name (:273) but not two artefacts for one target. `mahalo-dl-4.0.0` and a stale `mahalo-dl-4.0.1` in one `--out` are both PROMOTABLE onto `mahalo-dl` through `moduleKey`'s version strip. `--approve 4.0.1,4.0.0` prints WROTE twice and leaves 4.0.0's list (`createClassLoader` count 0).
This is not a widening, because the final file equals one reviewed diff, but the first WROTE line is false and the outcome depends on approval order. Fix: refuse `ambiguous target`.

**R6 (LOW) Atomicity: a mid-apply kill leaves a half-applied group; rollback misreports. VERIFIED, run.**
- Kill test (`OR\kill.out`): a jdb breakpoint on `LP$1.move`, then `Runtime.halt` (no finally) at the second hit, on approve `mahalo,phoenix`. Result: mahalo replaced (setIO removed); phoenix holds an empty `src/main/resources/META-INF` chain and no list; `.promote-staging-N` beside the plan holds `backup-1 new-2`.
- Nothing stray under any module root or META-INF, so no Maven packaging risk (F3 fixed).
- A re-plan shows `mahalo +0 -0`, `phoenix NEW FILE` and says nothing of the interrupted apply or the leftover staging dir. Suggest that plan and apply warn when `.promote-staging-*` exists beside the plan.
- Injected failure (n14, read-only second target): mahalo restored, reggie unchanged, rc 1. But the message says `NOT restored: [reggie-dl ...]; backups are kept` because the failed item is already in `attempted` (LP :726) and its rollback copy fails on the read-only file. Safe but alarming; staging kept.
- Cross-volume staging (`Files.move` falls back to copy and can leave a partial target): PLAUSIBLE, not run.

**R7 (LOW) Entries read from `--out` are not re-checked for control or format characters. VERIFIED, run (n12).**
An entry with U+202E is PROMOTABLE and written. `--promote-plan-check` prints an ESC sequence verbatim to the CI log (1 ESC byte counted). Generation refuses both (`PPG.requireNoControl` :810), so this needs an edited `--out`, the same root as R3.

**R8 (LOW, cosmetic) Uncaught exceptions print stack traces instead of REFUSED.** `LP.realRoot` :240-245 catches only IOException, so a root containing `"` throws `InvalidPathException` (n8 output). R1 is a second instance. VERIFIED.

**R9 (LOW) `--clean` cannot clean a zero-proxy policy.** The early return (`PPG.generate` :621-636) skips `removeLists`. On copies of the 4 real `qa/harness/policy/*.policy` (all md5 8a224f88, no proxy grants), the 14 committed pre-provenance lists stay `STALE` under `--clean`. VERIFIED, run (`OR\w5`).

## Item 1: attacks through the production CLI (all on copies; full output in `OR\attack.out`, `OR\attack2.out`)
Each line gives the attack and its result:
- Refused, nothing written: p1 (outside every root, full suffix), p2 (pom.xml with its true sha), p3 (`generated:` swapped to an AllPermission superset), p4 (refused artefact flipped), p5 (no-provenance list flipped), p6 (relative plan path inside the root), p7 (junction `jsk-dl`, nothing created behind it).
- `--approve` with `..\..\x`, a path separator, an absolute path, or a trailing `/`: refused, "not in the plan". A plan block renamed to `..\..\outside`: refused.
- Format-1 plan: refused. Stale SHA, a 63-character prefix, or an empty SHA: refused.
- Artefact map values `..\..\x`, `../x`, an absolute path, `.`, `a/../b`, or a key `../orphan`: refused at load (`LP.loadArtefactMap` :416). The map can redirect only to a discovered module, and the plan shows the target (see R5).
- Case variants `SRC\Main`, or a trailing space: refused by `render`. A trailing dot: refused by the suffix check.
- An 8.3 short-name root: works and resolves to the long real path. A junction root, or a plan file reached through a junction into the root: refused.
- A hard-linked target: the move replaces the directory entry and the outside link is unchanged.
- An artefact approved twice: harmless. An empty `--out`: refused.
- BOM in a list: refused (unparsable). CRLF in a list: written as LF.
- A 40001-entry list: plan 0.87 s, apply 0.81 s.
- The 3 META/GATE cases: WROTE (R3).
- Real tree, read-only: `--promote-plan` with `JGDMS\JGDMS` as the root and the real jinitest lists takes 3.8 s and matches round 2 (`reggie-dl` -2, `jsk-dl` refused); git status unchanged.

## Item 3: did it fix F1-F6 and the artefact name? (one reproduction each)
- **F1: FIXED.** p1-p5 refused. `render(d).equals(block)` (LP :617) is the load-bearing check: with the root check mutated away, the same p1 is refused by `render`.
- **F2: FIXED** for a junction module, a junction root and a hard link. Symlinks were not run (no privilege test).
- **F3: FIXED** for placement (staging beside the plan). Group atomicity still fails (R6).
- **F4: FIXED** at generation (principal U+202E and U+FEFF refused). Not applied at plan or check (R7). U+E000 is accepted, as admitted.
- **F5:** the `.grantperm` half is FIXED (hand-written ceiling refused with proxies, KEPT without). The list half is NOT fixed (R2).
- **F6: FIXED** (stale, prefix and empty SHA refused). PLAUSIBLE residual: a window between the staging re-hash (LP :699-703) and the move.
- **Artefact names:** FIXED as strings, but this introduced R1.

## Item 4: B2 judgement
Measured state: without `--clean` the run refuses; with `--clean` it overwrites and says KEPT (R2). The "always needs a manual delete" experience is therefore not what happens.
The realistic case is the 14 committed lists. They carry the old line `# Generated by ProxyPolicyGenerator (do not edit by hand).` (HEAD PPG :445) but no `# Source policy:`, so the first proxy-bearing re-audit will hit every one of them.
Recommendation:
(a) Count a list with that old generator line as tool-written (pre-provenance). Replace or clean it, with a `REPLACED pre-provenance list` note.
(b) Treat a list with neither marker as hand-written. Refuse it in both modes and make the KEPT note true.
(c) Let `--clean` also act on the zero-proxy path (R9).
Then a re-audit works by default, and a hand-written file is never touched.

## Item 5: mutants (`OR\mutants.sh`, `OR\mut-*.log`; null: restored source gives 28/0 for `ListPromotionTest`)
Instrument: `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=ListPromotionTest -Dsurefire.failIfNoSpecifiedTests=false`. Scoring rule: KILLED = at least one failure. Each restore was `cp` followed by an identical `cmp`.
- ROOT (`LP.targetProblem` :646, `canon.startsWith(r)` replaced by `true`): KILLED, 1 failure (`testApplyRefusesATargetRedirectedOutsideTheRoots`). It is killed only through the message: that test's target lacks the full suffix. Through the CLI the mutant is not exploitable, because `render` refuses both an outside target and a sibling `tree-evil` target.
- RENDER (LP :617 replaced by `if (false)`): KILLED, 1 failure (`testApplyRefusesAPlanThatHidesAnAddedEntry`).
- SHA (LP :549, the comparison dropped): KILLED, 2 failures (`testApplyRequiresTheApprovedPlanSha`, `testCliPlanThenApply`).
- Extra, ROOTSTR (`String.startsWith`): SURVIVED, as the producer predicted. It is not exploitable through the CLI (`render`).
- Gate validity: the SHA check is the only defence against "edit `--out`, then re-plan", because `render` re-derives from the edited `--out`. It holds only if the operator records the SHA that `--promote-plan` printed rather than recomputing it at apply time.

## Item 6: destructive operations, release 8, CRLF
- Writes outside `--out`, the staging dir beside the plan, and the module roots: only `<policy>.grantperm` beside the policy (deleted or overwritten only with the marker, `PPG.isOurCeiling`) and the generator's `.<out>.staging-N` beside `--out`.
- `deleteTree` (PPG :752) follows junctions. It is reached only on directories this run created, so a deletion through a planted junction is a PLAUSIBLE race, not run.
- Release 8: VERIFIED. Class major version 52 for LP, PPG and PUPP; javac `[debug release 8]` in `OR\mut-SHA.log` and `OR\final-mvn.log`.
- CRLF: apply keeps the target's majority line ending (`LP.eolOf`) and writes LF for a new file. The plan file is LF. Reading accepts CR, LF or CRLF (`split("\r?\n")`). The only defect is R4.
- Final suite: the brief's command gives 82 run, 0 failures, BUILD SUCCESS (`OR\final-mvn.log`), after a recompile, so `target/classes` is no longer compiled from a mutant.

## The fix agent's five operator decisions
1. Use the B2 recommendation above (Item 4), and fix R2 first.
2. Agree with `--module-roots`. Use it in plan and check too (the parser already accepts it in every mode), so all three ant targets name the roots one way.
3. Agree that `source kind` stays advisory. The polpAudit-comment test matches any comment containing the word, so label it a hint. An origin line in `SecurityPolicyWriter` is a DirtyChai change and must be human-written there.
4. Exempt REFUSED artefacts from the REMOVED failure (they can never be applied), but list them. Keep failing on unparsable lists, and add a failure for META/GATE entries (R3).
5. Agree: a link module fails the whole plan, loudly.

## Not verified
- Linux; symlinks.
- The real `ant policy-promote-*` targets.
- A cross-volume staging move; the TOCTOU window; a `deleteTree` race.
- The producer's other 25 mutants.
- `PolicyCondenser` (unchanged).
- Whether `jsk-dl` is `jgdms-lib-dl`: UNDETERMINED.
