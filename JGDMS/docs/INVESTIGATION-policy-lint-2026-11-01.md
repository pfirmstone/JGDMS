claude-sonnet-5-5
# Investigation: PolicyLint (AllPermission scope lint), 2026-11-01

Status words: VERIFIED = I saw the output this session; UNDETERMINED = not checked.

## What was built (all VERIFIED present, nothing committed or pushed)
- `JGDMS/tools/policy-condenser/src/main/java/org/apache/river/tool/PolicyLint.java` (new, CRLF).
  Kinds U1, U2, S1, S2, P1, exit codes 0/1/2, `--strict`, `--audit-input`, `--glob`, `--format=tsv`, `--fix-hint`.
- `ProxyPolicyGenerator.java` (CRLF restored, +62/-8 in git diff): the existing grant parser was extended, not
  duplicated: `GrantBlock.line`, `Perm.line`, a `Problem` class, `parseGrants(text, problems)` (the old
  one-argument method delegates with null, same grants returned), `readText` made package-private.
  `keystorePasswordURL` is now skipped like `keystore` (previously ignored token by token; same grants).
- `PolicyLintTest.java` (new, CRLF, 19 tests).
- `qa/build.xml`: targets `policy-lint-args` and `policy-lint` after `policy-promote-apply`; `git diff --stat`
  shows `61 insertions(+)`, 0 deletions, file still LF.
- `JGDMS/docs/ProxyPolicyGenerator.md`: new section "Linting policies for AllPermission" (+47 lines).
- Skill `.claude/skills/jgdms-polp-audit-policy-gen/SKILL.md` (tracked, CRLF kept): the "created if it doesn't
  exist" comment now says the file must exist (pre-create an EMPTY file), plus one paragraph with the observed
  `SecurityException: Unable to load policy url` and the rule that the starting policy has no unscoped
  AllPermission, pointing to `--audit-input`. Diff: 3 insertions, 1 deletion.

## Tests (VERIFIED)
`mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest='ProxyPolicyGeneratorTest,PolicyUpdatePostProcessorTest,ListPromotionTest,PolicyCondenserTest,PolicyLintTest' -Dsurefire.failIfNoSpecifiedTests=false`
=> 35+13+11+36 = 95 existing tests plus 19 new = 114, 0 failures, BUILD SUCCESS (final run, after all edits).
First run of the new class: 18 of 19 passed; `p1Unparseable` failed (one stray word produced 3 P1 findings);
fixed by reporting once per run of stray words; the next run was green.
"Fail first": the class did not exist before; each rule's failure is demonstrated by the mutants below.

## Mutants, run one at a time on PolicyLintTest, each restored with cp and `cmp` identical (VERIFIED)
| Mutant | Result (failures of 19) | Tests that failed |
|---|---|---|
| M1 U1 off | 8 | u1*, caseCrlfBomAndKeystore, exitCodeMatrix, tsvFormat, fixHint, quotedBraces, directoryWalk |
| M2 U2 off | 2 | u2UnscopedMetaGrants, exitCodeMatrix |
| M3 S1 reported as S2 | 4 | s1CodebaseOnly..., caseCrlfBom, controlCharacters, exitCodeMatrix |
| M4 S2 reported as S1 | 2 | s2DigestAndPrincipalScoped, exitCodeMatrix |
| M5 P1 off | 2 | p1Unparseable, exitCodeMatrix |
| M6 `.proxy-permissions` not skipped | 1 | directoryWalkSelectsAndSkips |
| M7 `.grantperm` not excluded | 1 | directoryWalkSelectsAndSkips |
| M8 escaping off | 1 | controlCharactersAreEscaped |
| M9 `--strict` ignored | 1 | exitCodeMatrix |
| M10 `--audit-input` ignored | 1 | exitCodeMatrix |
| M11 comments not honoured (parser) | 2 | commentedAllPermissionIsNotAFinding, cleanFileCountsAsLinted |
| M12 GrantPermission target check always false | 2 | u2UnscopedMetaGrants, exitCodeMatrix |
The first M5 attempt did not compile (my sed was wrong, so no test result); it was redone as a valid mutant above.

## One wrong output that would still pass each test (what the test does not defend)
- u1UnscopedAllPermission: a tool that always reports line 1 (other tests cover lines 2 and 5 only for BOM/braces fixtures).
- u1WithNameAndActions: a tool that reports the permission line instead of the grant line.
- u2UnscopedMetaGrants: a tool that also flags a non-covering wildcard such as `java.io.*`.
- s1: a tool that treats `codebase ""` as S1 (empty codeBase) instead of U1.
- s2: a tool that reports S2 for `digest ""` (empty digest).
- p1Unparseable: a tool reporting the P1 at line 0.
- commentedAllPermission: a tool that treats `#` as a comment start (not policy syntax).
- falsePositiveFixtures: a tool that drops any grant whose codeBase contains the word AllPermission.
- caseCrlfBom: a tool that treats a lone CR as a line break the wrong way (old-Mac files untested).
- exitCodeMatrix: a tool that ignores `--strict` when `--format=tsv` is also given.
- controlCharactersAreEscaped: a tool that escapes the reason but not the file-name column (a control
  character in a file name cannot be created on this Windows host).
- directoryWalk: a tool that matches `.POLICY` upper-case or follows symlinks.
- fixHint, tsvFormat, outputSorted, cleanFile, usage: hints also for U2; TOTAL without exit=; text sort of lines 1 vs 10; file counted but not parsed; exit 2 for an unreadable file.

## Run on COPIES (VERIFIED)
Copies in `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\lint-run\{harness,deploy,jinitest,jeri}`.
Command, per directory (cwd = lint-run): `java -cp "<repo>/JGDMS/tools/policy-condenser/target/classes;<repo>/JGDMS/jgdms-platform/target/classes" org.apache.river.tool.PolicyLint --format=tsv <dir>`
| Directory | files | U1 | U2 | S1 | S2 | P1 | exit | Files with U1/U2 |
|---|---|---|---|---|---|---|---|---|
| qa/harness/policy | 41 | 10 | 0 | 165 | 0 | 0 | 1 | all.policy:1, policy.all:1, defaultfiddler/group/mahalo/mercury/norm/outrigger/reggie.policy:6, defaultphoenix.policy:76 |
| deploy/policy | 5 | 0 | 0 | 0 | 0 | 0 | 0 | none |
| jinitest.policy | 1 | 1 | 0 | 10 | 0 | 0 | 1 | jinitest.policy:78 |
| jeri test.policy | 1 | 1 | 0 | 0 | 0 | 0 | 1 | test.policy:12 |
Cross-checks by reading: defaultmahalo.policy (S1 at line 1 for `jrt:/jdk.localedata`, U1 at 6; matches lines 1-8);
defaultphoenix.policy:76 and jeri test.policy:12 and jinitest.policy:78 are unscoped AllPermission blocks;
deploy host1-lookup.policy mentions AllPermission only in a comment and is clean (as required); I also compared
a plain `grep -c java.security.AllPermission` per harness file with U1+S1+S2: all equal except
defaultsecuretest.policy.old (15 vs 13), where lines 73 and 116 are `GrantPermission "java.security.AllPermission..."`
inside codeBase-scoped blocks (correctly not U1; U2 applies only to unscoped blocks). No misclassification found.
The harness file count 41 equals an independent `find` (policy, .old, .new, policy.all; proxy-permissions and .grantperm excluded).

## Ant (VERIFIED)
`ant -Dpolicy.lint.paths="<R>/harness;<R>/deploy;<R>/jinitest;<R>/jeri" -Dpolicy.condenser.jar=<repo>/JGDMS/tools/policy-condenser/target/classes -Dlib-ext.dir=<empty dir> -Driver.lib.dir=<empty dir> policy-lint`
(run in `qa`, Windows-style `C:/` paths; an msys `/c/...` path was mangled by Ant's path splitting and gave exit 2).
Output: per-file SUMMARY lines, `TOTAL files=48 U1=12 U2=0 S1=175 S2=0 P1=0 exit=1`, `BUILD FAILED ... Java returned: 1`.
The platform jar was not needed on the classpath. `--strict`/`--audit-input` through Ant were not run.

## Not verified
- Linux/macOS behaviour; a real polpAudit run against a linted starting policy; the policy-condenser JAR build (classes dir used).
- That the `${property}` text in codeBase is never expanded: by construction (strings are only printed), not by a test of an expanding parser.
- Ant `-Dpolicy.lint.strict=true`, `-Dpolicy.lint.audit.input=true`, `-Dpolicy.lint.glob`, paths containing spaces.
- Scoped meta-grants (a codeBase-scoped `GrantPermission "...AllPermission"`) are not reported at all; neither are broad
  codeBases like `file:/-`, a wildcard `principal * *` (counted as principal-scoped, S2), or permissions implying AllPermission by other means.
- Two files in the working tree were changed/added by someone else, not by me: `JGDMS/docs/HANDOVER-2026-11-01-code-free-proxy-window.md`
  (modified) and `JGDMS/docs/REVIEW-proxy-policy-tooling-round4-opus-2026-10-31.md` (untracked).

## Operator decisions needed
1. S1 noise: 139 of the 175 S1 findings (VERIFIED by grep) have a `jrt:/` codeBase (local platform modules). Choose whether S1 should
   skip `jrt:` codeBases; as built they are reported and only `--strict` makes them fail.
2. Whether `policy-update` should call the lint on the starting policy automatically (not wired; only the stand-alone target exists).
3. Zero found files exits 2 (so a mistyped path cannot pass a gate); confirm that is wanted.
4. The 12 U1 files above are existing QA/test policies; whether to fix them is out of scope here.

## Re-check after the coordinator's warning (VERIFIED 2026-11-01)
Reviewer mutations may have touched ProxyPolicyGenerator.java. I diffed it against `git show ae004e42f:...`: the only
base lines removed are my intended parser-signature changes; all my additions are present; `listSource` uses
`line.startsWith(OLD_HEADER_PREFIX)` (line 1250), not `contains`. Nothing needed re-applying. Reran all five classes:
114 tests, 0 failures, BUILD SUCCESS; file still CRLF.
