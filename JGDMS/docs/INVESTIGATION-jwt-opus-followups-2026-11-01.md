claude-sonnet-5-5
# JWT follow-ups to the Opus review: findings 4, 5, 7 (2026-11-01)

Worktree `...\JGDMS\.claude\worktrees\agent-a8b43fe0277943354`, detached at 5b649a558, uncommitted. Patch: `SP\jwt-fixes.patch`, SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\jwt5`.
JDK: `build\windows-x86_64-server-release` had no `java.exe` (no images dir, bin held only DLLs), so every run used the installed `C:\Program Files\DirtyChai\jdk`, which is the older one.
Command: `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false`. Scripts and logs: `SP\mutants.ps1`, `SP\mutants.log`.

## Changes per file
- `JwtLoginModule`: refresh now goes through `installRefreshed`, under `synchronized(subject)`. It checks `running`/`commitSucceeded`, `sub` and `iss` equal to the login token's, and `!subject.isReadOnly()`, then swaps principals, `JwtRawToken` and `JwtExpiryClaim` together. It removes only what this module installed (not every `JwtPrincipal`, so other modules' principals survive). `addedPrincipals/addedCredentials` always hold what is installed now, so `logout` (also under the lock, stopping the thread first) removes refreshed values. Failures are recorded in `lastRefreshFailure` (class name and fixed message, no token text). Added package-private `refreshNow()` (test hook, one synchronous refresh). A sub/iss mismatch is a `FatalRefreshException`: the thread stops, the Subject keeps the old identity.
- `BasicInvocationHandler.writeUserSubjects`: snapshots principals and tokens inside `synchronized(subject)`, the lock the swap uses; otherwise "atomic" would bind only the writer.
- `BasicInvocationDispatcher`: `PEER_ASSERT_GUARD` throws `SecurityException` when `sm == null`. `peerMayAssert` keeps its own pre-check (both layers now refuse).
- Tests: `JwtLoginModuleTest` +7 (crit, replace-together, different sub, different iss, invalid refresh responses, logout after refresh, reader-under-lock); `JwtPrincipalBindingTest` +1 (child JVM, `main guard`, Guard called by reflection with no SecurityManager).

## Fail-first (copy of HEAD, new tests, JwtLoginModule given only the `refreshNow`/`lastRefreshFailure` shim)
| test | result on unmodified main |
|---|---|
| `theGuardItselfDeniesWithoutASecurityManager` | FAIL "the Guard passed silently" |
| `testRefreshReplacesPrincipalsTokenAndExpiryTogether` | FAIL (foreign principal removed, token not replaced) |
| `testRefreshWithADifferentSubIsRefused` | FAIL "refresh was accepted" |
| `testLogoutAfterRefreshRemovesEverythingAndStopsTheThread` | FAIL |
| `testAReaderUnderTheSubjectLockNeverSeesHalfARefresh` | FAIL (principals gap seen) |
| crit, different-iss, invalid-response tests | pass: trunk already refuses these (crit and iss are guarded by the validator). Their value is the mutant below |
Counts: Binding 27 run 1 failure; LoginModule 27 run 4 failures.

## Hand mutants (private copy, one at a time, restore byte-compared)
| id | mutant | result |
|---|---|---|
| M1 | `JwtValidator`: `if (false && header.containsKey("crit"))` | KILLED: JwtLoginModuleTest `Tests run: 27, Failures: 1` |
| M2 | refresh keeps the old `JwtRawToken`, swaps principals and expiry | KILLED: `Tests run: 27, Failures: 2` |
| M3 | `installRefreshed` accepts any sub (`false` for the sub test) | KILLED: `Tests run: 27, Failures: 1` |
| M4 | Guard `if (sm == null) return;` again | KILLED: JwtPrincipalBindingTest `Tests run: 27, Failures: 1` |
All four `restored=True`. Baseline (fixed code in the copy): both classes 27 run, 0 failures.
Worktree regression run: platform 63, jeri 59 (Binding, FailClosed, MultiSubject x2), jwt 64: 0 failures, BUILD SUCCESS.

## Not verified
- The newest DirtyChai image (not present); the platform suite and other modules beyond the classes above.
- The iss check cannot be killed by a mutant: `JwtValidator` already refuses a foreign issuer first, so the explicit `iss` comparison is defence in depth. The different-iss test pins the outcome only.
- The reader test is probabilistic for a missing lock; M2 and the replace test are the deterministic pins.
- Other readers of a Subject (not `writeUserSubjects`) are not locked and could still see a half-swap.
- A rejected refresh stops the thread permanently; the token then runs out. Chosen as fail-closed; not discussed with the operator.

## Questions
1. Should a different-identity refresh stop the thread (current), or retry with backoff in case the IdP is transiently wrong?
2. Is locking the Subject in `writeUserSubjects` acceptable (it touches BasicInvocationHandler, outside the three named files)?
