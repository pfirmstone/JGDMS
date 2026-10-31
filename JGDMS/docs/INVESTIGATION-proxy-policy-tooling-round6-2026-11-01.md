claude-sonnet-5-5

# Round 6: one shared permission-set definition, and a reviewed allow-list for the CI gate, 2026-11-01

Nothing committed or pushed. E:\ and DirtyChai not touched. VERIFIED = run or read this session. Scratch: `...\scratchpad\round6\` (`base` = `git archive HEAD` plus my 9 files, mutants run there; `head` = pristine HEAD build; `mut.sh`, `real.sh`, `real-head\`, `real-cand\`). Live files never mutated.

## Result
- VERIFIED: ProxyPolicyGeneratorTest 45, PolicyUpdatePostProcessorTest 11, PolicyCondenserTest 13, PolicyLintTest 19, ListPromotionTest 46 (40 + 6 new), tools PermissionSetsTest 1 (was 5; 4 moved), plus platform PermissionSetsTest 7 (new) and VerdictPermissionMapperTest 17: 0 failures. Condenser 135, platform 24. Also SubProcessGrantOrchestratorTest (the other mapper user) 23/23.
- VERIFIED before and after, VerdictPermissionMapperTest: 17/17 at HEAD, 17/17 now (one file untouched). Condenser's 133 at HEAD: now 129 old-count tests (4 moved verbatim to platform) + 6 new.
- VERIFIED "fails first": new tests were written before the code and did not compile (no `PermissionSets` in the platform, no 4-arg `check`). The behavioural red is the mutants below.
- `git diff --stat`: 9 tracked files, +281/-334 (334 is the deleted 236-line tools copy), plus 2 new platform files. `qa/build.xml` 13 lines, LF. No line-ending noise in the diff.

## 1. Single definition
- New `au.net.zeus.jgdms.api.policy.PermissionSets` (jgdms-platform, public final; same package as the mapper, the least disruptive place: the platform already holds every class name except the DirtyChai/pref-loader strings, which stay exactly as they were). Contents are the tools class moved, only `public` added and the javadoc re-pointed.
- `VerdictPermissionMapper.META_PERMISSION_CLASSES` is now `PermissionSets.META` (package-private, was private, so the test can reach it). It thereby gains `PolicyPermission`. Public behaviour otherwise identical.
- Tools copy deleted (no delegating class); ProxyPolicyGenerator, ListPromotion, PolicyLint gain one import line each. PermissionSetsTest (tools) now only checks the generator reads the shared sets.
- Tests (platform `PermissionSetsTest`): `assertSame(PermissionSets.META, mapper field)`; every class of META, as an `UnresolvedPermission` under a caller that may grant anything, is excluded from the ceiling; a resolved `PolicyPermission` is excluded; the HOLD/FLAG/entry-line tests moved here.
- Wrong output that still passes: `testTheMapperExcludesEveryClassOfTheSharedMetaSet` alone passes for a mapper holding its own copy that has all four names; only `assertSame` catches that (mutant M1b).
- VERIFIED mutants: M1 mapper keeps its own 3-name copy: KILLED (2 tests). M1b own copy with all four names: KILLED by assertSame only. M2 generator ignores PolicyPermission: KILLED. M3 shared META drops PolicyPermission: KILLED.

## 2. `--promote-plan-check --allow-held a[,b]` (ant: `-Dpromote.check.allow.held=a,b`)
- Exact artefact names, same strings the plan prints and `--approve` takes. Default (none) fails as before. `ListPromotion.check(gen, roots, map, Set)`; the 3-arg form delegates with an empty set.
- Output: `check ALLOWED <artefact>: N live entr(y|ies) of the HOLD set, allowed by --allow-held <name>:` then `  ~ <entry> (reason)` lines. An unmatched name: `check FAIL --allow-held <name>: no artefact of that name in the plan (artefacts: [...])`. A name whose artefact has no live HOLD entry: a note, verdict unchanged.
- Not excused for an allowed artefact: REMOVED, unparseable list, META/GATE class (edited list), another artefact's HOLD entry. CLI: a bare `--allow-held` or an empty list on the gate is usage, exit 2; names on `--promote-plan` or on `--promote-apply` are usage, exit 2 (apply keeps its bare flag). Documented in `docs/ProxyPolicyGenerator.md` and the `qa/build.xml` comment block; `ant -p` parses the file (the target itself was not executed).
- 6 new ListPromotionTest tests: allowed artefact passes and is reported; other artefact still fails (wrong output that passes: "everything allowed once the list is non-empty" would pass the first test, not this one); 5 typo forms (prefix, case, version-less, extra char, unknown), alone and beside real names; META/GATE x3, REMOVED, unparseable each still fail; named-but-clean note; CLI exit codes 0/1/2.
- VERIFIED mutants, all KILLED: M4 any non-empty list allows all; M5 unknown name ignored; M6 allowed skips the META/GATE failure; M7 skips REMOVED; M8 skips unparseable; M9 bare flag accepted on the gate; M10 allowed entries not reported; M11 plan accepts `--allow-held`; M12 apply accepts names.

## Real harness policies versus round 5
- VERIFIED: 44 files of `qa/harness/policy` (not `.grantperm`/`.proxy-permissions`) through generate, plan and plan-check on copies, HEAD build versus this build: all outputs, rc, 22 generated lists (sha256) and the 3 plan files identical after masking temp paths and timestamps. No change versus round 5: lists 2/1/19 etc. as before; `defaultsecuretest.policy.old` still REFUSED; `defaultsecuresharedvm.policy.old` check still FAILED (+1 -1, a REMOVED entry).

## Not verified
- The ant target was parsed, not run (no built condenser jar on the ant classpath here). Windows only. `PolicyPermission` now leaving the runtime ceiling changes a runtime result, but no test of the sub-process path declares one besides mine.
- jgdms-platform has 16 unrelated failing tests (DigestDomainGrantTest, GrantSignerTest); only the named classes were run there.

## Decisions needed
1. The brief says an ambiguous target "still fails" the gate. It does not today: the gate fails on REMOVED, unparseable, edited-list and live HOLD only; `REFUSED: ambiguous target` (and no-module) artefacts pass it (docs already say so). I left that alone. Should the gate fail on any refused artefact?
2. `PolicyPermission` now being excluded from the runtime ceiling is the intended consequence you asked for; confirm it is wanted for SubProcess grants too.
3. The allow-list is exact-name only (no wildcard, no version-stripping), so a version bump of an artefact needs the list updated, deliberately; say if you want otherwise.
