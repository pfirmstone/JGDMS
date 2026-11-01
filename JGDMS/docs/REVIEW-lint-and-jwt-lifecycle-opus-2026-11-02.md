claude-opus-5-5
# Review of 79b560986, 93abc815b, 1a1bdfa32, 11b04d7f9, 7cd3f0a24 (2026-11-02)

CONFIRMED = I ran it in a private copy and saw the output. PLAUSIBLE = reasoned from the code, not run. READ = read in the code.
SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r12`. Copy: `git archive --format=zip HEAD` (eeef2a01f, which contains all five commits), extracted with Windows `tar.exe` into `SP\src`. Fixtures are LF. Nothing in the live JGDMS or DirtyChai trees was edited; this file is the only write. I never ran `install`.
JDK: `DirtyChai\build\windows-x86_64-server-release\images\jdk` (`bin\java.exe` present, `27-internal-adhoc.peter.DirtyChai`). Logs: `SP\run1.log`, `SP\probe-jwt.log`, `SP\probe-lint.log`, `SP\mutants.txt`, `SP\mut-*.log`. Mutation script: `SP\..\r12tools\mut.py`.

## Tests (one run, `mvn -o test -pl jgdms-platform,jgdms-der,jgdms-jeri,jgdms-security-jwt,tools/policy-condenser -Dtest=<the 20 classes>`): 398 run, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS
PolicyLintTest 49 (2 skipped), PolicyLintGateTest 23, PolicyLintCorpusTest 4, ListPromotionTest 46, ProxyPolicyGeneratorTest 45, PermissionSetsTest 2 (tools) + 13 (platform), UnresolvedPrincipalImpliesTest 6, DefaultPolicyScannerTest 12, JwtLoginModuleTest 37, WriteUserSubjectsLockTest 1, JwtPrincipalBindingTest 27, JwtDispatcherFailClosedTest 15, MultiSubjectWireProtocolTest 12, BasicInvocationDispatcherMultiSubjectTest 5, JwtPrincipalTest 21, JwksJsonParserTest 16, AssertPrincipalPermissionTest 14, DefaultJwtVerifierClaimsTest 24, DefaultJwtVerifierNegativeTest 23, VerifiedJwtApiTest 3. All counts match the commit messages.
The corpus claim is reproducible. The test printed: `files covered=86 of clean=86 permission-classes=56 constructed=48 questions-per-file=36630 (6 subjects x 6105 permissions) questions-total=3352452 grants=4086 provider=au.zeus.jdk.authorization.policy.ConcurrentPolicyFile`. The test passed, so it found 0 bad grants.

## Earlier findings: addressed? (file:line is in the HEAD copy)
| finding | status | where | pinning test |
|---|---|---|---|
| Lint 1: cross-check depends on the catalogue; catalogue gaps | **PARTLY** | gaps added (PermissionSets.java `extraDanger`, `CATALOGUED`, probes). The question CLASSES now come from the JDK image (PermissionUniverse). The question NAMES are still a fixed list of 23, and "bad" is still decided by `PermissionSets.danger` (PolicyLintCorpusTest.java:266). See finding 2 | Gate `classesTheCatalogueMissedAreCatalogued`; platform `testClassesTheCatalogueMissed` (my L1 is killed) |
| Lint 2: cross-check passes when the provider loads nothing | ADDRESSED | `grantedToEach` throws `LoadFailure` (PolicyVerdict.java:367-390); `covered == clean` and a canary file are asserted (Corpus:289-293) | `crossCheckFailsAgainstAProvider{ThatGrantsNothing,ThatCannotLoad}`, Gate `grantedToRefusesAFileTheProviderCannotLoad`. Residual: `grants > 0` is counted over all files, not per file (Corpus:294) |
| Lint 6: UNC and unknown-scheme codeBases count as clean | **PARTLY** | PolicyScope.java:201-204 (unknown scheme) and :261-269 (host). `file://///host/share/-` is still pinned (finding 3). The cross-check subjects include no UNC or unknown-scheme URL | Gate `remoteShareCodeBasesAreStrangers`, `unrecognisedCodeBaseSchemesFailClosed` |
| JWT 1: removal by equality | ADDRESSED | commit and refresh record only when `Set.add` returns true (JwtLoginModule.java commit, `installRefreshed`) | `testPrincipalsAnotherModuleInstalledAreNeverRemoved`. Credentials have no collision test |
| JWT 2: second commit orphans the first commit | ADDRESSED | `commit` runs `uninstallLocked` on `installedOn` first | `testSecondCommitOnTheSameModuleReplacesTheFirstIdentity` |
| JWT 3: abort after commit | ADDRESSED | `abort` runs `uninstallLocked` | `testAbortAfterCommitUndoesTheCommit` |
| JWT 4: a refresh without `sub` | ADDRESSED | `installRefreshed` refuses a null or empty sub on either token | `testSubLess...` (2 tests), `testRefreshThatAddsOrDropsTheSubIsRefused` |
| JWT 6: thread lifecycle | **PARTLY** | Error, invalid_grant record, minimum delay, logout noise and `commitSucceeded` under the lock are all fixed. **Not addressed:** the non-transactional swap under a partial SecurityManager grant; logout racing `installRefreshed` (flag only, no test); HTTP 400 without invalid_grant fails closed only in `loop` (:626), and no test reaches that line (my J2 survives) | `testAnErrorInTheRefreshThreadFailsClosed`, `testInvalidGrantIsRecorded`, `testExpiredWithinSkewDoesNotSpin`, `testLogoutDuringAnInFlightRefresh...` |
| JWT 7: unlocked readers | no action asked | readers can see fewer principals, never a mix (fails closed) | none |
| JWT 10: PeerPolicyFixture SecurityManager leaks into later classes | ADDRESSED | jgdms-jeri/pom.xml `peer-policy-fork` (`reuseForks=false`) plus excludes. The list is maintained by hand (the javadoc says to add new users) | none checks a missing entry |

## Findings, most important first
1. **A refreshed token the validator refuses is treated as transient: the token stays and the thread retries forever.** This covers another `iss`, a bad signature, an expired token and a non-JWT. CONFIRMED by a probe on the real thread (`SP\probe-jwt.log`). Login used a token 10 s past `exp`; the endpoint returned a token with `iss=https://rogue.example`.
   - At t+1.5 s: `names=[group:a, sub:s] tokens=1 expiries=1 alive=true hits=1 why=JwtValidationException: JWT issuer mismatch`.
   - At t+7.5 s: the same, with `hits=2`.
   - `validator.validate` (:742) throws a plain Exception, so the `catch (Exception)` back-off branch handles it. The iss check in `installRefreshed` never fires, because the issuer is required (`login`), so the validator pins it.
   - The class javadoc says a refused iss "removes what this module installed". `testRefreshWithADifferentIssIsRefused` and `testRefreshResponseThatIsNotAValidToken...` pin the opposite (`assertUnchanged`).
   - The result fails closed on the wire (the server refuses the token once it expires), but the Subject keeps it.
2. **The cross-check is independent in classes, not in names.** CONFIRMED by a probe (`SP\probe-lint.log`, test class since deleted).
   - For the 86 clean files and the stranger subject `https://cdn.attacker-evil.example/...` with no principal, ConcurrentPolicyFile lists 53 resolved permissions.
   - **46 of the 53 imply none of the 6105 universe questions.** Examples: `RuntimePermission "exitVM"`, `"accessDeclaredMembers"`, `SecurityPermission "set*"`, `FilePermission "C:\-" read`.
   - A question only sees a grant that is at least as wide as one of its 23 names (mostly wildcards). In this corpus all 46 are catalogued classes, so nothing is missed today.
   - A specific-name grant of an uncatalogued class (e.g. `AWTPermission "createRobot"`) would be invisible to both halves: the symbolic half gives a NOTE (by ruling), and the cross-check asks no matching question. PLAUSIBLE: I wrote no policy file.
   - Comparing `getPermissions` elements against the catalogue and `catalogued()` would remove the dependence on names.
3. **`file://///host/share/-` (five slashes) is read as a pinned local directory, but Java opens it as a UNC share.** CONFIRMED.
   - The probe calls `PolicyScope.classify`: `file://///host/share/-` gives FILE_PINNED, and so does `jar:file://///host/share/x.jar!/`. `file:////host/...`, `file://host/...`, `file://127.0.0.1/...` and `//opt/x` give REMOTE_WILD; `file://localhost/...` and `file:///...` give FILE_PINNED, as intended.
   - jshell on this JDK: `new File(URI.create("file://///host/share/x.jar"))` gives `\\host\share\x.jar`.
   - Cause (:261-269): the loop strips one empty authority per pass, so five slashes go to `///host`, then to `/host`.
   - Whether ConcurrentPolicyFile matches such a codeBase to a class loaded from the share is PLAUSIBLE (that needs a policy file).
4. **HTTP 400 without invalid_grant fails closed only on the thread's own path, and nothing tests it.** The probe on the real thread CONFIRMS the behaviour is right: `400-other: names=[] tokens=0 expiries=0 alive=false`. Mutant J2 (drop the `failClosed` at :626) SURVIVES all 37 tests, because the test hook `refreshNow` bypasses `loop`. Probes also confirm: invalid_grant gives `names=[] tokens=0 alive=false`; a sub-less refresh the same; a network error keeps the token and the thread (`ConnectException`, `tokens=1 alive=true`), as specified.
5. **HTTP 401 and 403 from the token endpoint are transient** (:718-719), so they retry forever and keep the token. RFC 6749 §5.2 allows 401 for `invalid_client`. PLAUSIBLE.
6. **The NOTE path is sound.** CONFIRMED. A grant holding `ExportPermission "*"` and `RuntimePermission "createClassLoader"` gives D1 for createClassLoader plus one NOTE for ExportPermission. The `continue` at PolicyLint.java:714 is per permission, notes never enter `res`, and the exit status counts only findings. Exceptions in `ask` become P1 (:303). `collect` silently skips a subject whose URL has no handler (:402); the symbolic half still applies there.
7. **The lock and race review found no new hole.** READ.
   - `installRefreshed` and `failClosed` lock `caller.subject`; commit, abort and logout lock `installedOn`.
   - A stale thread is stopped (`running=false`, volatile) under the old Subject's monitor before a new commit installs. Its later `failClosedLocked` returns early, so it cannot touch the new Subject.
   - Cosmetic: a stopped thread may still overwrite `lastRefreshFailure`.
   - An Error thrown by the removal itself, or an OutOfMemoryError (rethrown on purpose), leaves the token.
   - No token text appears in any new log line or exception: I grepped the diffs. The messages are class names or fixed text, and validator messages quote the expected issuer only.
8. **DefaultJwtVerifier.** READ.
   - The only public constructors require a non-null, non-empty issuer and audience, an algorithm of RS256 or ES256, and non-empty suitable keys (:158-183).
   - There are no production callers; the only callers are the tests listed by the investigation.
   - The lifetime check is `exp.isAfter(now.plus(maxLifetime).plusSeconds(30))` (:319), which is right. A huge configured `maxLifetime` would throw DateTimeException from `now.plus`.
   - Leftover null guards remain at :335 and :341, harmless. The javadoc "if an expected issuer was configured" is stale.
   - The client `JwtValidator` has no maximum lifetime, so a 90-minute IdP token logs in and is then refused by every server at the 1-hour default.

## Mutation sample (one at a time; each restored and checked by sha256, `restored=True` for all)
| id | mutant | result |
|---|---|---|
| L1 | `PermissionSets.catalogued` returns true for every class (NOTE never printed) | KILLED: platform `testClassesTheCatalogueMissed` (the reactor stopped before the Gate tests ran) |
| L2 | `ask` no longer marks a provider-only load failure (`providerOnly`) | KILLED: Gate `providerThatCannotLoadWhatTheParserAcceptsIsAFinding` |
| J1 | invalid_grant records the reason but does not fail closed | KILLED: `testInvalidGrantIsRecorded` |
| J2 | a permanent FatalRefreshException in `loop` does not fail closed | **SURVIVED** (37 run, 0 failures) |
| J3 | an Error in the thread records the reason but does not fail closed | KILLED: `testAnErrorInTheRefreshThreadFailsClosed` |
| J4 | lifetime skew 300 s instead of 30 s | KILLED: `expiryBeyondTheMaximumLifetimeIsRefused` (its 120 s margin) |

## Not checked
- ConcurrentPolicyFile matching on a five-slash or specific-name policy (no new policy files, per the brief).
- A full jeri or platform run without `-Dtest`.
- Linux; qa/jtreg; an end-to-end JERI call with a refreshed token.
- SecurityManager-installed runs of JwtLoginModule.
- HTTP 5xx (the same code branch as the network error I ran).
- Whether `-Dtest` runs the fork-listed classes twice.

## For the operator to settle
1. Is a refreshed token that fails validation (another iss, a bad signature, expired, not a JWT) a permanent failure that must remove the token (finding 1)? The current tests pin "unchanged".
2. Are HTTP 401 and 403 from the token endpoint permanent?
3. Should the cross-check list `getPermissions` and judge each element, instead of asking a fixed name list (finding 2)?
4. Is `file://///host/...` a remote share for the lint (finding 3)?
5. Should the client also refuse tokens that live longer than the server's 1-hour default, or should deployments raise `maxLifetime`?
