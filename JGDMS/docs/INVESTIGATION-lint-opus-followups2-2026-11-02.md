claude-sonnet-5-5
# Opus findings 2 and 3 of the lint review: fixes (2026-11-02, base 473d246ea, nothing committed)

Evidence: every test count below is from my own run with `JAVA_HOME` = the DirtyChai image, offline, `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=...`. The provider in all runs is `au.zeus.jdk.authorization.policy.ConcurrentPolicyFile`. Windows only.

## Changes per file (tools/policy-condenser)
- `PolicyScope.java`: the `file()` slash loop is rewritten; javadoc updated.
- `PolicyVerdict.java`: new `listedToEach` (the elements of `Policy.getPermissions(pd)`; `LoadFailure` if the provider throws or returns null). `grantedToEach` and `listedToEach` share the new `eachSubject` (same load, property and restore code as before).
- `PolicyLintCorpusTest.java`: `judgeListed` (the listing judgement), `checkFile`, `crossCheckSetUp`, three more UNC subjects, canary listing, per-file no-listing check, tests `aSpecificNameGrantOfAnUncataloguedClassIsReported` (AWTPermission "createRobot") and `crossCheckFailsAgainstAProviderThatListsNothing`.
- `PolicyLintGateTest.java`: `slashRunsOfAFileCodeBaseDecideLocalOrShare`, `slashRunTable`.
- New: `stub/ImpliesButListsNothingPolicy.java`; fixtures `corpus/n/n93..n101`; 9 rows in `corpus-expectations.tsv` (CRLF kept, trunk columns left empty); `cross-check-allow.tsv` (49 entries); `cross-check-no-listing.tsv` (25 files). New resources are LF.

## S1: slashes after `file:` (the text after the scheme, backslashes read as slashes)
| leading slashes | treated as | basis |
|---|---|---|
| 1 | local `/path` | Java: `\host\share\x.jar` on the current drive (run) |
| 2 | share on the first segment as host; `localhost` is local; `localhost//h` stays a share | Java: `new File(URI)` refuses an authority; localhost kept as before |
| 3 | local (empty authority) | Java: `\host\share\x.jar` (run) |
| 4, 5, 6, 7 | REMOTE_WILD (stranger) | Java: `\\host\share\x.jar` for all four (run, this JDK) |
| slashes only | local root | nothing to resolve as a host |
`jar:file:` and a scheme-less `//host/...` or `\\host\share` go through the same rule. On trunk 5 and 7 were pinned (the loop strips one empty host per pass); 6 only looked right by parity.

## S2: the provider cross-check now judges what the provider lists
For every clean file (87: the 86 plus n93) and 57 subjects (9 asked of the questions, plus 48 probe subjects; includes `file://`, `file:////` and `file://///` hosts), the test reads `getPermissions`. A listed permission passes if it is on the allow-list (class, name regex, actions regex, reason) or implies one of the 6105 universe questions (judged by the old loop and `PermissionSets.danger`); else the test names file, subject and permission. A listed HIGH permission always fails. Also asserted: canary file listed, every allow entry used, and the files with nothing listed equal the pinned list.
Numbers: 4064 listings (file x subject x permission); 63 distinct (file, permission); 49 imply no question (Opus: 46 of 53 for one subject; mine is 87 files, 57 subjects); 49 allow-listed; 14 covered by questions; 25 files list nothing (all pinned by codeBase, signer, digest, named principal or a dropped permission; reasons in the tsv); 62 list something.
Allow-list (49): 16 clean in both runs (narrow reads, names that imply only themselves such as `set*`, `define*`, `setu0053ecurityManager`, one exact principal or class) and 33 rated MEDIUM (D5) by the lint, so exit 0 by default and reported by `--strict`: exitVM, getClassLoader, modifyThread, AuthPermission doAs and others, `SocketPermission *.com connect`, FilePermission reads of a drive root or the home. Each line carries the fixture that justifies it.

## Fail-first (trunk logic) and run
| test | trunk / mutant | after |
|---|---|---|
| Corpus `corpusExitCodesAndKinds` | n94, n96, n99, n100 got 0/0 instead of 1/1 D1 (trunk, run) | pass |
| Gate `slashRunTable`, `slashRunsOf...` | fail on trunk (run) | pass |
| Corpus `aSpecificNameGrantOf...` | S2a (listing dropped = trunk logic): `reported:` empty | pass |
Full: PolicyLintTest 49 (2 skipped), Gate 25, Corpus 6, ListPromotion 46, ProxyPolicyGenerator 45, PolicyCondenser 13, PostProcessor 11, tools PermissionSetsTest 2, platform PermissionSetsTest 13, DefaultPolicyScanner 12, UnresolvedPrincipalImplies 6: 197 run, 0 failures, BUILD SUCCESS.

## Mutants (private `git archive` copy plus my files; one at a time; sha256 restored for all; script `lint6\mut.py`, logs `lint6\mut-*.log`, `mutants.txt`, `mut2a.txt`)
Command: `python mut.py <id>` runs `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=PolicyLintGateTest,PolicyLintCorpusTest`.
| id | mutant | Tests run | killed by |
|---|---|---|---|
| S1a | five slashes local again | 31 run, 3 failures | Corpus n94; Gate `slashRunTable`, `slashRunsOf...` |
| S1b | six slashes local | 31, 3 failures | n95; the same two Gate tests |
| S2a | `judgeListed` call removed | 31, 2 failures | `cleanFiles...` (no-listing list differs), `aSpecificName...` |
| S2b | allow-list emptied | 31, 1 failure | `cleanFiles...`: names n20-tmpdir-star-write, the cdn subject, FilePermission write |
| S2c | default provider = stub that lists nothing | 31, 5 failures | `cleanFiles...` ("provider listed nothing for a control file"), others |

## Not verified
- Linux: allow-list regexes allow `/` or `\` but ran only on Windows. The platform provider's own listing was not run through the corpus.
- Whether the provider matches a UNC codeBase to code loaded from the share; the lint now calls it a stranger regardless.
- Gate showed 1 skipped test in the mutant copy and 0 in the worktree; not looked into.
- The earlier shell used a different `JAVA_HOME` (an older DirtyChai install) for two runs; all numbers above are from the explicit image.

## Questions
1. Should a clean (exit 0) file be allowed to grant D5 permissions such as `exitVM` to a stranger? I allow-listed them as the lint's existing ruling; say if the default run should instead fail on them.
2. Should the allow-list also name the files that may grant each entry?
