claude-sonnet-5-5
# Lint followups 5: operator decisions M1 to M7 of 2026-11-02/03 applied (base 3cf1f31d9, nothing committed)
Evidence: my runs on Windows, `JAVA_HOME` = DirtyChai image, `mvn -o -pl jgdms-platform,tools/policy-condenser test`. Untouched: DirtyChai, E:, jgdms-security-jwt. The real class is `javax.security.auth.AuthPermission` (the brief said `java.security.`).
## Classification (PermissionSets: HOLD = refused for a proxy list, FLAG = written live and reported)
| permission | lint | set | reason |
|---|---|---|---|
| AuthPermission `doAs`, `doAsPrivileged`, `setLoginConfiguration`, `modifyPrincipals`, `modifyPrivateCredentials`, `*` | HIGH | HOLD (doAs and setLoginConfiguration new) | run as, become, or choose the login modules that decide who a caller is |
| AuthPermission `modifyPublicCredentials` | MEDIUM | FLAG | public credentials carry no authority by themselves; a login module in the client needs it, a proxy should be asked about it |
| AuthPermission `createLoginContext.*`, `getSubject`, `getLoginConfiguration`, `refreshLoginConfiguration` | MEDIUM | FLAG | start a login or read identity state; acting as the result still needs `doAs` (HOLD) |
| FilePermission `<<ALL FILES>>` with `read` (any order, `read,readlink`) | HIGH (was MEDIUM) | FLAG | unscoped whole-tree read like `/-`; `readlink` alone stays MEDIUM (links, not contents) |
| FilePermission `/-`, `C:\-` (any case, slash, action order), UNC `\\h\s\-` `\\h\-` `//h/s/-`, `/home/-`, `/Users/-`, home tree | HIGH (UNC and `/home/-` were unrated on Windows) | FLAG (the generator also holds absolute names as host-specific) | whole trees; `\\h\s\dir\-`, a pinned directory and a named file stay clean |
| SecurityPermission `getDomainCombiner` | MEDIUM (was clean) | none | reaches protection-domain information of an access-control context |
| RuntimePermission `shutdownHooks` | MEDIUM (was clean) | none | runs code at JVM exit and can delay it |
| RuntimePermission bare `exitVM` | MEDIUM (was clean, the row hid it) | FLAG | the JDK renames it to `exitVM.*` |
M7, ratings ab94352a6 added without naming them (now in the docs/ProxyPolicyGenerator.md catalogue): SocketPermission `*.<tld>` (`*.com`) MEDIUM, it reaches every host of a top-level domain; SecurityPermission `authProvider.*` MEDIUM, it obtains any named authentication provider; `getProperty.*` MEDIUM, it reads any Security property by name.
## Changes
M1/M2 `PermissionSets.java` (`FilePath.unc`, `wholeTree`, HOLD/FLAG). M3 `PolicyLint.Finding.permissions` (every permission of a D5 finding, also beyond the six in its text; provider hits carry their Permission) and `Finding.names(Permission)`; the corpus test accepts a MEDIUM listing only if a D5 finding names it. M4 `allowListProblems`: every file of a row must be cited by its stem as a whole word. M5/M6/M7 tsv, docs.
## Fixtures whose outcome changed (default/strict exit, kinds)
r8/f01 `<<ALL FILES>>` read 0/1 D5 -> 1/1 D1; r8/f06 and r6/a22 D1 D5 -> D1; r8/c14 getDomainCombiner and r8/r15 shutdownHooks 0/0 clean -> 0/1 D5; top-level g08 D5 0/1 -> D1 1/1, g07 D1 D5 -> D1. New (16): n/n110 to n/n125 (c:\-, C:/-, read/readlink orders, read, write, UNC share/host/forward, /home, /Users, home read,readlink orders, readlink only, all-files read,readlink: all 1/1 D1; n125 `/etc/hosts` read: 0/0 clean).
## Allow-list: explicit rows 39 -> 13 (final count 13)
Dropped 27: 25 whose reason only repeated the D5 rating, plus getDomainCombiner, shutdownHooks. Kept 12 whose reason says more (named SerialObject, pinned reads, relative write, property-expanded names, wildcard-lookalikes, exact principal, GrantPermission ceiling); added 1 (n125). Rule-accepted pairs 19 -> 45; files covered 84 of clean 84.
## Renames
`git mv` n/n26-home-read-medium.policy -> n/n26-home-read-high.policy; references changed in corpus-expectations.tsv, PolicyLintGateTest, the INVESTIGATION followups3 and followups4 docs. Left as written: HANDOVER-2026-11-01 (its pending-question line names the old name).
## Fail-first (trunk PermissionSets.java swapped in, my tests; 15 failures, 0 for PolicyLintGateTest)
platform PermissionSetsTest 18 run, 6 fail (testDangerSeverities, testIdentityAndBroadReadRatings, testWholeTreeReadVariantsAreHigh, testWholeTreeReadsAreFlagged, testIdentityChangingAuthPermissionsAreHeld, testGetDomainCombinerAndShutdownHooksAreMedium); tools PermissionSetsTest 4/1; ListPromotionTest 47/2; PolicyLintCorpusTest 19/3; PolicyLintTest 49/1; ProxyPolicyGeneratorTest 47/2. M3 and M4 have no trunk run (their API and tests changed together): the mutants below are their evidence.
## Mutants (private `git archive` copy plus my files; baseline B0: 102 run, 0 fail; `PermissionSetsTest,PolicyLintCorpusTest,PolicyLintTest,PolicyLintGateTest`; sha256 restored equal, all True; logs lint9\logs)
| id | mutant | Tests run, failures | killed by |
|---|---|---|---|
| M1 | `<<ALL FILES>>` read back to MEDIUM | 18/3, 4/1, 19/1, 49/1 | testWholeTreeReadVariantsAreHigh, corpus n124 |
| M2 | UNC read unrated (write kept) | 18/2, 4/1, 19/1 | testWholeTreeReadVariantsAreHigh, corpus n115 |
| M3 | `/home/-` unrated | 18/2, 4/1, 19/1 | same test, corpus n119 |
| M4 | doAs out of HOLD | 18/2, 4/1, 19/1 | testIdentityChangingAuthPermissionsAreHeld, corpus u02 |
| M5 | rule per file | 19/1 | aMediumPermissionInAFileWhoseD5NamesAnotherPermissionFails |
| M6 | repeated cited stem accepted | 19/1 | aReasonMustCiteEveryFixtureOfItsRowByItsStem |
| M7 | getDomainCombiner unrated | 18/1, 19/2 | testGetDomainCombinerAndShutdownHooksAreMedium, corpus c14 |
Final regression in the worktree: `*Policy*Test,PermissionSetsTest,*Verdict*Test,ListPromotionTest,Lint*`: platform 89 run, tools 220 run, 0 failures (2 skipped, as before).
## Not verified
Linux; the full platform suite; `readlink` alone on `/-` stays HIGH while on `<<ALL FILES>>` it is MEDIUM (unchanged, inconsistent); `//host/-` is HIGH on Linux too (there it is `/host/-`); `\\h\s\dir\-` and `/root/-` unrated.
## Questions
1. Keep `readlink` alone MEDIUM on `<<ALL FILES>>` while HIGH on `/-`? 2. Rate a UNC share subtree (`\\h\s\dir\-`) read and `/root/-`? 3. Is FLAG enough for `modifyPublicCredentials` in a proxy list, or HOLD?
