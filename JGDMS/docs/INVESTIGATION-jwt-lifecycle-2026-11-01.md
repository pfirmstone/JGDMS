claude-sonnet-5-5
# JWT client lifecycle fixes (Opus review findings 1-4, 6, 7, 10), 2026-11-01

Base: trunk 79b560986. JDK: the built DirtyChai image (`build\windows-x86_64-server-release\images\jdk`). Nothing committed or installed.
Command: `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false`.
Final run, 12 classes, BUILD SUCCESS, 0 failures: JwtLoginModuleTest 37 (was 27: -1 replaced, +11), JwtPrincipalBindingTest 27, JwtDispatcherFailClosedTest 15, MultiSubjectWireProtocolTest 12, BasicInvocationDispatcherMultiSubjectTest 5, WriteUserSubjectsLockTest 1 (new), JwtPrincipalTest 21, JwksJsonParserTest 16, AssertPrincipalPermissionTest 14, DefaultJwtVerifierClaimsTest 25, DefaultJwtVerifierNegativeTest 24, VerifiedJwtApiTest 3.

## Changes per file
- `JwtLoginModule.java`
  - K1: `addedPrincipals`/`addedCredentials` hold only what `Set.add` reported as added (commit and refresh). Refresh and logout remove only those.
  - K2: new `installedOn` Subject. `commit` first undoes a previous commit (`uninstallLocked`: stop thread, remove ours); `abort` after a commit does the same; `logout` uses it. `clearState` became `clearLoginState` (login-time fields only; it used to orphan installed values).
  - K3: `installRefreshed` refuses if the login token's or the refreshed token's `sub` is null/empty, or differs, or `iss` differs.
  - K4: such a refusal calls `failClosedLocked`: stop thread, remove token, expiry claim and our principals under the Subject lock. `installRefreshed` locks the thread's own Subject (`caller.subject`), not the module's current one.
  - K5a: `run()` catches Throwable (OutOfMemoryError rethrown): `lastRefreshFailure` = class name, SEVERE log without message or stack, `failClosed`. K5b: `invalid_grant` sets `lastRefreshFailure`. K5c: after the first attempt the next wait is at least `MIN_BACKOFF_MS`.
  - Logout noise: `FatalRefreshException(loggedOut)` is silent; an exception after `running=false` returns without WARNING or back-off sleep.
  - `commitSucceeded` and `refreshThread` are now set under the lock before `rt.start()` (review finding 6, last-but-two bullet).
  - `refreshNow()` (package-private test hook) now returns the refresh result.
- `JwtLoginModuleTest.java`: +11 tests, 1 replaced (`testRefreshWithADifferentSubIsRefused` -> `...FailsClosed`: K4 changes its expected outcome from "Subject unchanged" to "token removed"); /token endpoint gained a hit counter, a 400 override and a latch gate.
- `WriteUserSubjectsLockTest.java` (new, jeri): K6. A swap thread holds the Subject monitor half way (old values removed, new not added); the reader must reach `BLOCKED`, then write the complete new pair. No timing: it waits for BLOCKED or TERMINATED.
- `jgdms-jeri/pom.xml`: K7. Default surefire execution excludes JwtPrincipalBindingTest and BasicInvocationDispatcherMultiSubjectTest; new execution `peer-policy-fork` runs them with `reuseForks=false`. `PeerPolicyFixture.java`: javadoc says so.

## Fail-first (new tests against the unmodified trunk JwtLoginModule + a 2-line shim: `refreshNow` returns boolean)
Run: 37 run, 11 FAILED, i.e. every new or changed test: AbortAfterCommitUndoesTheCommit (principals left), SecondCommit...ReplacesTheFirstIdentity (first thread alive), PrincipalsAnotherModuleInstalledAreNeverRemoved (expected [sub:s, group:b], was []), SubLessRefreshIntoAnotherIdentity / ...EvenForTheSameEmail ("refresh was accepted"), RefreshThatAddsOrDropsTheSub, RefreshWithADifferentSubFailsClosed (principals left), InvalidGrantIsRecorded, AnErrorInTheRefreshThread (principals left), ExpiredWithinSkewDoesNotSpin (127 attempts in 1.5 s), LogoutDuringAnInFlightRefresh (thread slept out the back-off). K6 has no trunk failure (jeri main is already correct); mutant M5 stands for it.

## Hand mutants (private `git archive` copy, one at a time, restore byte-compared: all True; logs `jwt6\mut-*.log`)
| id | mutant | result |
|---|---|---|
| K1 | record every principal offered | KILLED, 37 run 1 failure: PrincipalsAnotherModuleInstalledAreNeverRemoved |
| K2a | second commit without undo | KILLED, 37 run 1 failure: SecondCommit...ReplacesTheFirstIdentity |
| K2b | abort without undo | KILLED, 37 run 1 failure: AbortAfterCommitUndoesTheCommit |
| K3 | accept when either sub null | KILLED, 37 run 2 failures: both SubLess tests |
| K4 | stop without removing | KILLED, 37 run 5 failures |
| K5c | no minimum delay | KILLED, 37 run 1 failure: ExpiredWithinSkewDoesNotSpin |
| M5 | `writeUserSubjects` without the lock | KILLED, `WriteUserSubjectsLockTest` 1 run 1 failure |
The first K1 try was a bad mutant (did not compile) and is not counted; the second is the row above.

## K7 evidence (`jwt6\jeri2x.log`, `mvn -X`, `-Dtest=` three classes)
`default-test`: one fork (surefirebooter ..._3.jar) ran all three classes. `peer-policy-fork`: three forks (_6, _9, _12), one per class.

## Not verified
- A full jeri module run without `-Dtest` (platform's 16 known failures stop the reactor). With `-Dtest`, surefire ignores includes/excludes, so a named fixture class runs in BOTH executions (once in the shared JVM as before, once forked) and the fork execution forks every named class. A plain build does not.
- The thread-timing tests (ExpiredWithinSkew 1.5 s, Error test 3 s token) passed in 2 clean runs on this machine, not a loaded one.
- Logout racing `installRefreshed` (the old spurious SEVERE) has no test; it is covered by the `loggedOut` flag only. Partial SecurityManager grant (non-transactional swap) is untouched.
- Credentials follow the same Set.add rule as principals, but only principals have a collision test.
- qa/jtreg, Linux, a real end-to-end JERI call.

## Questions
1. A thread stopped by `invalid_grant` or HTTP 400 still leaves the token until expiry (K4 named only identity refusals and Errors). Should those fail closed too?
2. Report file: written in the worktree so the patch carries it; not written to the live docs folder to avoid a clash when the patch is applied.
