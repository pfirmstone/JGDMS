claude-sonnet-5-5
# Opus lint findings 2 and 3, flake guard, operator rulings A1-A4 (2026-11-02, base 1704c83f0, nothing committed)
Evidence: my runs, `JAVA_HOME` = DirtyChai image, `mvn -o -pl jgdms-platform,tools/policy-condenser test`, Windows only. jgdms-security-jwt, DirtyChai and E: untouched.
## Changes per file
- `PermissionSets.java` (jgdms-platform): `doAs`, `setLoginConfiguration` HIGH (extraDanger); `FilePermission` read of `/-` or `C:\-` and of the whole user home HIGH (`[H]` marker in fileReach/fileDanger); `*.com` (one-label TLD wildcard) MEDIUM; `authProvider.*`, `getProperty.*` MEDIUM; probes for doAs, setLoginConfiguration, home read.
- `PolicyLintCorpusTest.java`: exact-path file references only (glob and non-fixture paths fail, no exemption); `requireRightProviderAndParser` in both corpus tests (PROVIDER line must be `au.zeus...ConcurrentPolicyFile`, else fail naming `--add-exports` and the pom profile; probe `principal *` must give P1, else "accepts a wildcard principal class: wrong or stale JDK"); automatic rule in `judgeListed` (catalogue MEDIUM and the lint reports D5 for the file; a MEDIUM listing in a non-D5 file is a `ratingMismatch` failure); HIGH is reported before and whatever any row or the rule says. 6 new unit tests.
- `cross-check-allow.tsv`: 49 rows -> 39; header rewritten. `corpus-expectations.tsv`, `PolicyLintGateTest` (n26), both `PermissionSetsTest`, `docs/ProxyPolicyGenerator.md` catalogue text.
## Fixtures whose outcome changed (default/strict exit, kinds)
| fixture | before | after |
|---|---|---|
| r8/f18-cdrive-dash-read, n/n26-home-read-high, r8/u02-auth-doAs, r8/u06-auth-setLoginConfiguration | 0/1 D5 | 1/1 D1 |
| r8/m03-split-read-write (read half) | 1/1 D5 D1 | 1/1 D1 D1 |
| r8/c12-sec-authProvider_S, r8/c04-sec-getProperty_S | 0/0 clean | 0/1 D5 |
Kept: exitVM, exitVM.0, setIO, f05 readlink, s04 `*.com`, u07 (all 0/1 D5). r8/s04 needed a catalogue rule: `danger()` rated `*.com` nothing; the lint's D5 came from a probe.
## Allow-list numbers
Explicit rows 49 -> 39 (removed: f18, n26, u02, u06 (no longer clean), c12, c04 (wildcards, now rated), f05, s04, r14, u07 (auto)). Rule-accepted: 19 (file, permission) pairs in the run, 6 of them formerly rows (f05, s04, r14, u07, c12, c04). Run line: `files covered=84 of clean=84`, `allow-listed=39`, `files-with-nothing-listed=26`, provider `au.zeus...ConcurrentPolicyFile`.
## Fail-first
| test | trunk / wrong setup | after |
|---|---|---|
| platform `testIdentityAndBroadReadRatings`, `testDangerSeverities`, `testFileTargets...`; tool `testIdentityAndBroadReadAreHighToTheLint`; corpus n26 and c04 | with trunk PermissionSets.java: 3 + 1 + 2 failures (`expected:<2> but was:<1>`) | pass |
| corpus tests, no add-exports (`-DargLine=-Dnoexport=1`) | trunk test: provider silently falls back (no check; not re-run) | 3 failures, "provider that answered is org.apache.river.api.security.ConcurrentPolicyFile, not au.zeus...: pass --add-exports" |
Unit tests of A1, A2, A4 are in the changed class, so they have no trunk run: their evidence is the mutants.
## Mutants (private `git archive` copy + my files; `mvn -o -pl jgdms-platform,tools/policy-condenser test -Dtest=PolicyLintCorpusTest,PermissionSetsTest`; script `lint8\mut.sh`; sha256 restored equal, all `True`; baseline B0: 19 tests, 0 failures)
| id | mutant | Tests run (corpus) | killed by |
|---|---|---|---|
| M1 | glob reference accepted | 16, 1 failure | `globFileReferencesAreRejected` |
| M2 | provider-class check returns early | 16, 1 failure | `aRunOnTheWrongProviderFailsLoudly` (a first form, `return;`, did not compile and is not counted) |
| M3 | `doAs` MEDIUM again | 14+3+16, 1+1+1 failures | platform test, tool test, corpus u02 |
| M4 | rule accepts MEDIUM in a non-D5 file | 16, 1 failure | `aMediumPermissionIsAcceptedOnlyWhereTheLintReportsD5` |
| M5 | HIGH not reported when a row allows it | 16, 1 failure | `aHighRatedPermissionIsNeverAcceptedByARowOrTheRule` |
| M6 | stale explicit row accepted | 16, 1 failure | `aStaleFileReferenceFails` |
| M7 (ML-C) | exitVM's file set to `*/*` in the tsv | 16, 3 failures | `allowList()` ("is a glob") |
## Regression run
`PolicyLint*`, `ListPromotionTest`, `ProxyPolicyGeneratorTest`, `PolicyCondenserTest`, `PolicyUpdatePostProcessorTest`, both `PermissionSetsTest`, platform `*Policy*Test`: 213 tests, 1 failure (PolicyLintGateTest n26, a missed expectation). After the fix: PolicyLintGateTest 30, Corpus 16, both PermissionSetsTest 14 + 3, 0 failures.
## Not verified
Linux; the full platform suite; whether a stale DirtyChai image without #236 is caught (the parser probe is unit-tested with a synthetic result only); effect of HIGH on proxy-policy generation of `doAs`/`setLoginConfiguration` (not run; they were FLAG, are now HIGH in `danger()` but still not HOLD).
## Questions
1. 25 rows with reason "D5 in the catalogue" (exitVM, setContextClassLoader, ...) are now redundant with the rule; I kept them (you named 11). Drop them?
2. c14 getDomainCombiner and r15 shutdownHooks are rated clean, so the rule cannot take them and their rows stay (reason is the rating only). Rate them MEDIUM, or keep the rows?
3. Rename fixture `n26-home-read-high` (now HIGH)? I did not.
