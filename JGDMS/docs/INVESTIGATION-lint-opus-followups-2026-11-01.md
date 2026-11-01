claude-sonnet-5-5
# Lint follow-ups to the Opus review of 2026-11-01 (findings 1, 2, 6)

Population: JGDMS trunk 5b649a558 plus the uncommitted patch `scratchpad\lint5\lint-fixes.patch`. Tests ran on `C:\Program Files\DirtyChai\jdk`, the OLDER installed image: `C:\Users\peter\Documents\GitHub\DirtyChai\build\windows-x86_64-server-release\images\jdk` does not exist, and the `...\jdk` folder next to it had no `java.exe` (a rebuild was running, `build.log` still compiling modules at 22:15). Nothing was run on the fresh image.

## Operator correction applied (L2)
A loadable class the catalogue does not know is a `NOTE` line only; it never changes the exit status in default or `--strict`, in any scope. No D7 kind and no allow-list were added. D6 and HIGH for a class the lint cannot LOAD or BUILD are untouched (n50, n51 re-asserted).

## Changes per file (all under `JGDMS/`)
- `jgdms-platform/.../PermissionSets.java`: L1 entries in `extraDanger`, `catalogued(type)` (the set of classes the catalogue has an opinion on), probes for 7 of the 8 new classes.
- `tools/policy-condenser/.../PolicyLint.java`: `Linted.notes`; `symbolic` notes an uncatalogued loadable class; new P1 text when the parser accepts and the provider cannot load; NOTE lines printed.
- `.../PolicyVerdict.java`: `LoadFailure`, `Subject`, `grantedToEach` (one load, many subjects); `grantedTo` throws instead of returning "nothing"; `Answer.providerOnly`.
- `.../PolicyScope.java`: `file:` host (`//host`, `////host`, `\\host`) other than localhost is `REMOTE_WILD`; any scheme outside file/jrt/jar/http/https/ftp is `REMOTE_WILD`; `jar:` loops over nested wrappers.
- Tests: `PermissionUniverse` (new), `stub/{NothingGrantedPolicy,RefusingPolicy,DefaultPolicyParser}` (new), `PolicyLintCorpusTest` (cross-check rewritten, 2 new tests), `PolicyLintGateTest` (+7 tests), platform `PermissionSetsTest` (+1), 23 fixtures n70-n92 and 5 changed rows in `corpus-expectations.tsv` (j17 now `0 1 D5`). Java files CRLF, fixtures LF (checked).

## Catalogue additions (severity, reason as printed in the report table)
| class | sev | reason |
|---|---|---|
| au.zeus.jdk.authorization.guards.DefineClassPermission (any name or none) | HIGH | lets code define classes (Lookup.defineClass) |
| ...guards.NativeMemoryPermission | HIGH | off-heap memory: raw access or exhaustion |
| javax.management.remote.SubjectDelegationPermission | HIGH | acts over JMX as another subject |
| jdk.jfr.FlightRecorderPermission | HIGH | reads the JVM event stream, registers events |
| com.sun.jdi.JDIPermission | HIGH | attaches a debugger to JVMs |
| java.nio.file.LinkPermission (and the name java.lang.LinkPermission from the brief; that class does not exist) | MEDIUM | links can point outside the granted tree |
| jdk.net.NetworkPermission (and java.net.NetworkPermission, same note) | MEDIUM | changes or reads socket options |
| java.sql.SQLPermission setLog, setSyncFactory, setNetworkTimeout, callAbort, deregisterDriver, `*` | MEDIUM | JDBC global state or abort of connections |
Default-mode decision: MEDIUM for links, socket options, JDBC. Each is a global-state or reach issue that needs another bug to become an escape, so they join the other D5 permissions (strict only). `r8/j17` now fails `--strict` (0/1 D5). The Opus wide run granted exactly one uncatalogued class in the 78 clean files (SQLPermission setLog); no other class qualified. AWT, Audio, Card and InquireSecContext permissions were not granted anywhere and I did not catalogue them (they now yield a NOTE).

## Allow-list
None (dropped by the ruling). The pinned `EXPECTED_UNCATALOGUED` array in the corpus test names the one uncatalogued grant that clean files may hold (n80, ExportPermission); a new miss in the corpus fails the test until someone adds it knowingly.

## L3 cross-check (`PolicyLintCorpusTest`)
- Questions come from `PermissionUniverse`: 56 non-abstract Permission classes of the JDK image (jrt, all modules) and jgdms-platform, 48 constructible, 6105 permissions (23 names x 15 actions); not from `probes()`.
- Per clean file: 6 subjects x 6105 = 36,630 (class, subject) questions, plus 48 subjects x the HIGH probes. Run: files covered 86 of 86 clean, 3,352,452 questions, 4086 grants, 0 bad.
- Asserts: covered == clean (a file the provider cannot load throws `LoadFailure`, it is not "nothing"); a control file granting `FilePermission <<ALL FILES>> read` must be seen as granted; grants > 0; no HIGH grant; uncatalogued grants equal the pinned set; universe floors (>=40 classes, >=30 built, >=1000 permissions, SQLPermission, LinkPermission, ExportPermission present).
- M7 stub (`NothingGrantedPolicy`) and a refusing stub each make `assertCrossCheck` fail: tests `crossCheckFailsAgainstAProviderThat{GrantsNothing,CannotLoad}` pass.
- Lint side: a file the parser loads and the provider cannot is P1 "the policy provider cannot load a file the parser accepts" (test with `RefusingPolicy`).

## Fail-first
Trunk copy (git archive of 5b649a558 plus the new fixtures, stubs and Gate test; old JDK), PolicyLintGateTest 23 run, 7 failed:
| test | trunk result |
|---|---|
| classesTheCatalogueMissedAreCatalogued | n81 DefineClassPermission read 0/0 |
| uncataloguedLoadableClassIsInformationOnly | no NOTE line |
| providerThatCannotLoadWhatTheParserAcceptsIsAFinding | old message only |
| grantedToRefusesAFileTheProviderCannotLoad | returned empty list |
| remoteShareCodeBasesAreStrangers | n70 `file://host/share/-` 0/0 (n71 AllPermission 0/1) |
| unrecognisedCodeBaseSchemesFailClosed | n75 `netdoc:` 0/0 |
| wildcardPrincipalsOfIssuedClassesFail | NOT mine: old-JDK scanner (also fails after) |
Not runnable on trunk (new API): PolicyLintCorpusTest cross-check and its 2 stub tests, platform `testClassesTheCatalogueMissed`; they fail under mutants M1, M3, M4 below. On trunk the old cross-check printed `files covered=78 failures=0` (Opus M7).

## Hand mutants (private archive copy `lint5\mut`, one at a time, restore byte-compared)
Command: `python lint5\tools\mutate.py Mn` which runs `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=PolicyLintGateTest,PolicyLintCorpusTest,PolicyLintTest,PermissionSetsTest -Dsurefire.failIfNoSpecifiedTests=false` (JAVA_HOME the old image). Baseline M0 (no-op): `Tests run: 78, Failures: 4, Errors: 0, Skipped: 2` (the four old-JDK scanner failures).
| id | mutant | result |
|---|---|---|
| M1 | DefineClassPermission entry disabled | KILLED: `Tests run: 78, Failures: 52, Errors: 4` (also `Tests run: 13, Failures: 1, Errors: 1` in platform); targeted: PermissionSetsTest.testClassesTheCatalogueMissed, Gate classesTheCatalogueMissedAreCatalogued. Noisy because `probes()` throws |
| M2 | uncatalogued loadable class raises D1 (fails the run) | KILLED: `Tests run: 78, Failures: 8`; Gate uncataloguedLoadableClassIsInformationOnly, cleanCasesStayClean, PolicyLintTest.deployPoliciesStayClean |
| M3 | grantedTo swallows a load failure (empty answers) | KILLED: `Failures: 5` (4 baseline +1): Gate grantedToRefusesAFileTheProviderCannotLoad |
| M4 | cross-check enumerates `probes()` only | KILLED: `Failures: 5` (+1): cleanFilesGrantNoStrangerAHighPermission (universe floors) |
| M5 | `file://host` treated as local | KILLED: `Failures: 5` (+1): Gate remoteShareCodeBasesAreStrangers |
| M6 | unknown scheme read as clean (OTHER) | KILLED: `Failures: 5` (+1): Gate unrecognisedCodeBaseSchemesFailClosed |
M3 also leaves `crossCheckFailsAgainstAProviderThatCannotLoad` passing by design (the stub there throws, not swallows). Restore: M1 printed `restored=True`; for M2-M6 the first driver's stdout was lost, I compared hashes afterwards: PermissionSets, PolicyVerdict, PermissionUniverse equal the worktree, PolicyLint equals its pre-edit hash; PolicyScope differs only by the javadoc I added after copying.

## Corpus, trunk vs now (old JDK, default/strict exit)
Only new rows and j17 changed; no pre-existing row moved: j17 0/0 -> 0/1 D5; n70, n72, n75-n78, n81-n85, n91 0/0 -> 1/1 D1; n71 0/1 S1 -> 1/1 U3 S1; n86-n89 0/0 -> 0/1 D5; n79 1/1 D3 -> 1/1 D1; n90 0/0 -> 0/0 I1. deploy/policy host1 0/0, host2-5 0/1 (D5 only), identical before and after. Final run: PolicyLint/Corpus/Gate classes `Tests run: 193, Failures: 4, Skipped: 2` plus platform `Tests run: 30, Failures: 0`.

## Not verified
- Anything on the fresh DirtyChai image. The four failures (r6/a04, a33, r8/q03, z02 expectations P1; PolicyLintTest f06 and s3WildcardPrincipals) are the old image's scanner and fail identically on the trunk copy; re-run on the fresh one.
- n81 on the old image is read 0/0 on trunk only because I changed the fixture to the no-name form; with `"ALLOW"` the class rejects the name and trunk already answers D1 (so Opus's PLAUSIBLE `DefineClassPermission "*"` claim was wrong for that form; the no-name and pinned forms were the real misses).
- SubjectDelegationPermission has no probe: both providers list nothing for it (tried two names), so only the symbolic half sees it. Its HIGH rating is by class name, not measured.
- Linux/macOS, the `qa/build.xml` targets, the full platform suite.

## Questions
1. Should LinkPermission, NetworkPermission and the SQLPermission names be HIGH in default mode instead?
2. Is `file://127.0.0.1/...` local? I treat only empty and `localhost` as local.
3. A plain `//opt/x` (no scheme) now reads as host "opt", a share; fail-closed, OK?
