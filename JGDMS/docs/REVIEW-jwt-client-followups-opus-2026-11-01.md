claude-opus-5-5
# Review of c8a1c9d05 (JWT refresh swap, crit tests, fail-closed Guard, writeUserSubjects lock), 2026-11-01

CONFIRMED = I ran it in a private copy and saw the output. PLAUSIBLE = reasoned from the code, not run.
SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r11`.
- Copy: `git archive --format=zip HEAD` (c8a1c9d05) extracted with Windows `tar.exe` into `SP\src` (the archive writes CRLF where `.gitattributes` asks). Nothing in the live JGDMS or DirtyChai trees was edited; this file is the only write. I never ran `install`.
- JDK: the installed `C:\Program Files\DirtyChai\jdk` (`27-internal-adhoc.peter.DirtyChai`). `build\windows-x86_64-server-release\images\jdk\bin\java.exe` does not exist.
- Command: `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false`. Logs `SP\base.log`, `SP\probe.log`, `SP\wire1.log`, `SP\wire2.log`, `SP\mut-M*.log`, `SP\mutants.txt`. Script `SP\..\r11mut\mutants.py`.

## Tests (one run at HEAD, all 0 failures, 0 errors, BUILD SUCCESS): 189 run
JwtLoginModuleTest 27, JwtPrincipalBindingTest 27, JwtDispatcherFailClosedTest 15, MultiSubjectWireProtocolTest 12, BasicInvocationDispatcherMultiSubjectTest 5, JwtPrincipalTest 21, JwksJsonParserTest 16, AssertPrincipalPermissionTest 14, DefaultJwtVerifierClaimsTest 25, DefaultJwtVerifierNegativeTest 24, VerifiedJwtApiTest 3. Every count matches the commit message.

## Findings, most important first
The commit does what it says for one module on one Subject. Every finding below is about the lifecycle around that: other modules, a second commit, abort.
1. **Removal is by equality, so a principal that another module installed and that equals one of ours is removed.** CONFIRMED (probe `probeR11EqualForeignPrincipal`, `SP\probe.log`).
   - A Subject pre-holds `sub:s` and `group:b` from "another module". Login is `sub:s, group:a`, then a refresh to `group:b`. After logout the probe prints `PROBE equal-foreign after logout: []`, so both foreign principals are gone.
   - Cause: `commit` records every principal it offers in `addedPrincipals`, even when it was already present. `installRefreshed`/`logout` then `removeAll` by `equals`. The new test uses `other:module`, which never collides.
   - The javadoc claim "only what this module installed is removed" is therefore true only when no names collide. This is fail-closed (fewer principals), but it breaks a second JwtLoginModule (or a SPIFFE/other module issuing `JwtPrincipal`) on the same Subject.
2. **A second commit on the same module instance orphans the first refresh thread and the first identity.** CONFIRMED (`probeR11SecondCommitSameModule`).
   - Login as `s`, then re-initialize and log in as `bob` on the same Subject. The probe shows `names=[group:a, group:c, sub:bob, sub:s] tokens=2 rt1Alive=true`.
   - After `logout` it shows `names=[group:a, sub:s] tokens=1 rt1Alive=true`. Alice's principals and token stay behind, and the first thread is still alive.
   - `commit` clears `addedPrincipals` without removing them and overwrites `refreshThread`. JDK `LoginContext` reuses module instances across `login()` calls, so this is reachable with a callback-supplied token.
   - Fail-closed on the wire: the server refuses two users' tokens in one Subject (Binding 1). The orphan thread stops at its next refresh (`commitSucceeded` false or sub differs). Pre-existing in `commit`, a path this commit did not touch.
3. **`abort()` after a successful commit leaves principals, token and a live thread.** CONFIRMED (`probeR11AbortAfterCommit`: `names=[group:a, sub:s] tokens=1 threadAlive=true`). `LoginContext` calls `abort` on every module when a later REQUIRED module's commit fails, and `abort` only calls `clearState`. The thread stops at its first refresh; the valid token stays until it expires. Pre-existing, outside the commit's "logout clears them".
4. **A token without `sub` can change identity on refresh.** CONFIRMED (`probeR11SubLessIdentityChange`).
   - Login token: `email:alice@x` (verified), no sub. Refresh response: `email:mallory@x` plus `group:admin`. Result: `PROBE subless refresh ACCEPTED: [email:mallory@x, group:admin]`.
   - The check is `Objects.equals(null, null)`. `sub` is optional in `identityFromClaims`. For a sub-ful token the comparison is made on the VERIFIED claims (`newParsed` comes out of `validator.validate`), and that is correct.
5. **With sub and iss kept, a refresh may change `groups` and `email` freely; this looks intended.** READ.
   - Principals are rebuilt wholly from the new verified token (`ParsedJwt.principals()`), the same set the server derives.
   - Carried across a refresh: the rotated refresh token, clientId/secret and the jwksUri/issuer/audience options. Not carried: `additionalClaims`, but it feeds only `allClaims`, not principals, so it is harmless.
   - The iss comparison is redundant (the validator pins the configured issuer), as the author notes.
6. **Thread lifecycle details.** PLAUSIBLE.
   - `catch (Exception)` covers RuntimeException: it is logged WARNING and retried with backoff, not lost.
   - An `Error` kills the thread silently: `lastRefreshFailure` is not set and nothing is logged SEVERE. The old token then expires and the server refuses it, which is fail-closed.
   - Logout during an in-flight HTTP send: the send's InterruptedException is caught as transient, the thread then sleeps the full 5 s backoff with the flag cleared, and it logs a spurious WARNING. Logout racing `installRefreshed` logs a spurious SEVERE "permanently failed".
   - `invalid_grant` (return false) does not set `lastRefreshFailure`.
   - `commitSucceeded` is written after `rt.start()` and outside the lock. An immediate refresh (a token inside the 30 s skew) could in principle read false and stop the thread. That is fail-closed, and in practice the HTTP round trip covers the window.
   - A refreshed token with exp ≤ now (within skew) gives `computeSleepMs` = 0, which re-refreshes at once with no backoff (an IdP-driven tight loop).
   - A partial SecurityManager grant (modifyPrincipals yes, modifyPublicCredentials no) would leave the swap half done: the swap is four calls and is not transactional.
7. **Readers.** READ.
   - `git grep` finds one main-code reader of `JwtRawToken` from a Subject: `writeUserSubjects`, now locked. No main code reads `JwtPrincipal` from a Subject.
   - Unlocked readers (SubjectDomainCombiner permission checks, `SslEndpointImpl`'s X500/SPIFFE read) can see the gap between `removeAll` and `addAll`. They see fewer principals, never a mix: fail-closed.
8. **The lock.** READ. No deadlock found.
   - DirtyChai `Subject` takes `this` only in `setReadOnly` (this → set monitors). `isReadOnly` is unsynchronized, so nothing takes set → Subject.
   - `SpiffeCredentialManager`/`SpiffeLoginModule` use the same `synchronized(subject)` swap pattern, with no I/O under the lock. No HTTP is done under the lock in JwtLoginModule.
   - Residual: AuthPermission checks run under the Subject monitor, so an application that holds it for a long time stalls refresh and `writeUserSubjects`.
9. **writeUserSubjects.** CONFIRMED: the wire bytes are unchanged.
   - Probe `SP\R11WireProbeTest.java`: two mutable and immutable Subjects with X500 + JwtPrincipals, two `JwtRawToken`s and a non-token credential, plus an empty Subject.
   - HEAD and the pre-commit `BasicInvocationHandler` (`c8a1c9d05^`) both give 311 bytes, identical hex (`SP\wire-head.hex`, `SP\wire-pre.hex`). The lock adds no ordering problem with the dispatcher, which reads server-side Subjects it builds itself.
   - Mutant M5 (lock removed) SURVIVES: nothing tests the writer's lock.
10. **The static Guard.** READ, plus M4.
    - With a SecurityManager installed, `sm.checkPermission(perm, ctx)` is asked with `ctx = {peerDomain(peer)}` only. `peerDomain` is cached per peer Subject (weak identity), so nothing is shared across peers. The previous review's M2 (check the stack instead) showed this is pinned.
    - The only caller is `peerMayAssert`, which returns false before the Guard when there is no SecurityManager and maps any RuntimeException to false. So the throw changes no behaviour.
    - `PeerPolicyFixture` installs `new SecurityManager()` and never removes it. `close()` installs an AllPermission policy. The jeri surefire config has no forkCount/reuseForks, so all classes share one JVM, and every class after the first fixture runs with an SM and a permissive policy.
    - The no-SM assertions are safe from this because they run in a child JVM (`runChild`, which checks `getSecurityManager()==null`). An in-process no-SM test added later would silently get an SM.
11. **Messages and `lastRefreshFailure`.** READ. The record is the class name plus the message: fixed text or JDK I/O text. `exp` is normalized by the validator, so no NFE carries a claim. The refresh token and client secret never reach an exception; `JwtRawToken.toString` shows the header only; the refusal test checks the record holds no `marker` and no token segment.

## Mutation sample (one at a time, `SP\mutants.txt`; each restored and then byte-compared with the archive, all identical)
| id | mutant | result |
|---|---|---|
| M1 | refresh keeps the old JwtRawToken | KILLED: `testRefreshReplaces...`, `testAReaderUnderTheSubjectLock...` |
| M2 | `installRefreshed` accepts any sub | KILLED: `testRefreshWithADifferentSubIsRefused` |
| M3 | `installRefreshed` without `synchronized(subject)` | KILLED: reader test (probabilistic; it failed this run) |
| M4 | Guard `if (sm == null) return;` | KILLED: `theGuardItselfDeniesWithoutASecurityManager` |
| M5 | `writeUserSubjects` without the Subject lock | **SURVIVED** (jeri 4 classes: 59 run, 0 failures) |
| M6 | refresh does not update `addedPrincipals/addedCredentials` | KILLED: 3 JwtLoginModuleTest |

## Not checked
The real refresh thread timing (tests call `refreshNow()` on the test thread); an end-to-end JERI call with a refreshed token; the full platform suite; `qa/jtreg` JwtLoginModuleTest; Linux; the rebuilt DirtyChai image (absent); SM-installed runs of JwtLoginModule (AuthPermission under the lock); whether DirtyChai's SubjectDomainCombiner caches a PD built during the gap.

## For the operator to settle
1. When a refresh token has no `sub`, should the refresh be refused, or must `email` (or the whole principal set) stay equal?
2. Should `commit` record only the principals it actually added (`Set.add` returned true)? Or is a principal shared between modules owned by both, so logout must not remove it while the other is still logged in?
3. Should `abort()` after a successful commit undo the commit (remove the values and stop the thread)? Should a second `commit` on the same instance first undo the previous one?
4. Is a test lock-pin for `writeUserSubjects` wanted (M5 survives), or is the reader test in jgdms-security-jwt enough?
5. Should `PeerPolicyFixture` restore "no SecurityManager", e.g. by running in its own fork, so test order cannot matter?
6. The pending ruling (a different-identity refresh stops the thread) stands as implemented. Should a stopped thread also remove the token, instead of letting it run to expiry?
