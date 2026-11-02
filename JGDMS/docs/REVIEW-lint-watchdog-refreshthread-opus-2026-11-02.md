claude-opus-5-5
# Review of ab94352a6 (PolicyLint), 2c1d903c3 (JWT watchdog) and a21acb2df (refresh thread) (2026-11-02)
CONFIRMED = I ran it and saw the output. PLAUSIBLE = reasoned from the code, not run. SP = `C:\Users\peter\AppData\Local\Temp\claude\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd\scratchpad\opus-r15`. Copy: `git archive` of HEAD e246a687b (the three commits plus a docs commit), extracted with Windows `tar.exe` into `SP\src`. This file is my only write outside SP; no `install`. JDK: `DirtyChai\build\windows-x86_64-server-release\images\jdk` (`bin\java.exe` present, `27-internal-adhoc.peter.DirtyChai`). Logs in SP: `jwt-run1.log`, `jwt-run2.log`, `lint-run1.log`, `probe-*.log`, `mut-M*.log`, `probe\variants.log`.

## Tests (`mvn -o test`, `-Dtest` limited, reactors as briefed): 0 failures, 0 errors
- JWT run 1, 176 tests: JwtLoginModuleTest 53, BoundedBodyTest 5, WatchdogSecurityManagerTest 3, JwksJsonParserTest 16, JwtPrincipalTest 21, JwtPrincipalBindingTest 27, WriteUserSubjectsLockTest 1, DefaultJwtVerifierClaimsTest 24, DefaultJwtVerifierNegativeTest 23, VerifiedJwtApiTest 3.
- Child-JVM SecurityManager tests run twice (run 1 inside the full set; run 2 alone: WatchdogSecurityManagerTest 3 plus the 4 SM/daemon/commit-fails tests of JwtLoginModuleTest). Both green, no difference. MD and ME (below) are two more runs of the same 7, with the expected results.
- Lint, 203 tests (2 skipped): PolicyLintTest 49 (2 skipped), PolicyLintGateTest 30, PolicyLintCorpusTest 16, ListPromotionTest 46, ProxyPolicyGeneratorTest 45, PermissionSetsTest 3 (tools) + 14 (platform). Corpus: `files covered=84 of clean=84`, `allow-listed=39 auto-accepted(MEDIUM+D5)=19 explicit-rows=39`, provider `au.zeus.jdk.authorization.policy.ConcurrentPolicyFile`.

## Earlier Opus findings 1 to 5 (REVIEW-deadline-and-lint-opus-2026-11-02.md)
| # | status | where | pinning test |
|---|---|---|---|
| 1 watchdog needs caller's createPlatformThread; inherits caller CCL | ADDRESSED | BoundedBody.java:83-100 (eager start), :109-111 (`newThread` privileged), NewThreadAction sets the system CCL | WatchdogSecurityManagerTest x3; JwtLoginModuleTest `testLoginUnderASecurityManagerWhenTheCallerLacksCreatePlatformThread`; BoundedBodyTest `theThreadsAreDaemonsWithAFixedContextClassLoader`. Mutant MD killed |
| 2 glob `*/*` defeats the per-file rule | ADDRESSED | PolicyLintCorpusTest.java:353-360 (glob refused; must be a corpus fixture), `names` :307-309 is `files.contains` | `globFileReferencesAreRejected`, `aFileReferenceMustBeAnExistingFixture`. Mutant MA killed |
| 3 allow-list rules are textual | PARTLY | :362-364: citing one stem among several files still suffices; a reason made of the stem repeated still passes. The citation rule now applies to every entry (no glob escape) | none new for the residuals |
| 4 post-read check unpinned; one thread serialises closes | ADDRESSED, residual (finding 1) | BoundedBody:171; CLOSERS pool :88-90, :155 | `aStreamThatReturnsEofAfterTheWatchdogClosedItIsATimeout` (BoundedBodyTest:69), `aBlockingCloseDoesNotDelayAnotherDeadline` (:113) |
| 5 deadline range unpinned | ADDRESSED | BoundedBody:188-200 | `testResponseBodyTimeoutMsRangeIsEnforcedAtLogin` (JwtLoginModuleTest:1917, includes 99 and 120001) |

## Findings, most important first
1. **Four blocked closes delay every later deadline, without bound.** CONFIRMED (`probe-closers.log`, probe test in SP, deleted after). Closes whose `close()` blocks 3 s, then an unrelated 100 ms read: 3 blockers give 101 ms; 4 give 2799 ms; 6 give 2801 ms. The watchdog sets `expired` on time, but the reader stays blocked until a closer is free, and the queue is an unbounded `LinkedBlockingQueue`. If closes never return, later reads never time out. Nothing bounds it. The real HttpResponseInputStream.close does not block (PLAUSIBLE, from the previous review), so this is theoretical today.
2. **jgdms-collections gets the refresh permissions only because NewThreadAction is on the stack.** CONFIRMED by reading `jwt11\audit-final.policy`. The jar is granted modifyPrincipals, modifyPublicCredentials, the token POST and JWKS GET URLPermissions, createPlatformThread and setContextClassLoader.
   - Exploitable? PLAUSIBLE: low. Other threads that inherit only collections' domain are the platform-mode ThreadPool (ThreadPool.java:150-155) and ReferenceProcessor's cleaner (:274). Tasks on them are still intersected with their own frames, so the grant widens only stacks made of collections and fully trusted frames. The cost is coupling: every deployment must put its IdP URLs on a generic library jar.
   - Narrower: create the thread in jwt code (`Thread.ofPlatform().daemon().unstarted(r)` plus `setContextClassLoader` inside BoundedBody's own privileged action). Then the inherited context is the jwt domain alone.
   - The audit also records the same six permissions for a third domain, `.../target/classes/au.zeus.jgdms.security.jwt.BoundedBody`. The audit (19:18) postdates the named StartAction (worktree BoundedBody 18:57). The INVESTIGATION lists only two codebases. I cannot tell which frame contributes it.
   - Caller (test-classes) record: modifyPrincipals, modifyPublicCredentials, JWKS GET only, LoadClass, tmp read, suppressAccessChecks (harness reflection). No thread or token permission, as claimed.
3. **The refresh thread no longer carries the caller's restrictions.** PLAUSIBLE (read). Its context is {jwt, collections} (BoundedBody:109-110, NewThreadAction.run), plus the domain in 2. It used to be the caller's whole context, captured by `new RefreshThread` in commit. The Runnable is module code (RefreshThread); no caller code runs inside either doPrivileged.
   - Caller-influenced inputs: `refreshTokenUri`, `jwksUri`, `issuer`, `clientSecret` (options, :361-392) and the Subject.
   - Code holding only `createLoginContext.<name>` can give LoginContext its own Configuration. That chooses the URIs the module then contacts with the jwt grant. This is an escalation only if the jwt URLPermission is wider than the real IdP endpoints. The INVESTIGATION offers `https://idp/-` or `*:*`, and the child test grants `https://localhost:port/-`, `*:*`.
   - Subject: no gain. Commit adds unprivileged (caller needs modify*), and a refresh refuses another sub or iss (:519-529).
   - (b) CONFIRMED by reading JwksKeyCache:157-160: only `HttpClient.newBuilder()...build()` is privileged; `send` (:798, JwksKeyCache fetch) is not.
4. **Lint catalogue variants.** CONFIRMED (`probe\variants.log`, `PermissionSets.danger` on the copy):
   - HIGH: `C:\-`, `c:\-`, `c:/-`, `/-`, `/tmp/../-`, `C:\Users\-`, and `${user.home}${/}-` with `read,readlink`, `readlink,read` or `readlink`.
   - `<<ALL FILES>> read` is MEDIUM, though broader than `/-` read (HIGH). The platform test pins this as intended (PermissionSetsTest:225).
   - `\\server\share\-` read AND write, `\\server\-` and `//server/share/-` write all give **null**: a whole-UNC-share write is not rated at all. This predates the diff (fileReach :731, :745-773).
   - `/home/-` read is null on Windows; it is HIGH only where user.home is under /home.
   - Pinned: `C:\-`, `d:/-`, `/-`, `${user.home}${/}-` read (platform :220-223; tools `testIdentityAndBroadReadAreHighToTheLint`). Not pinned: lowercase `c:\-`, the action orderings, UNC, `/home/-`, the parent of home.
5. **The automatic MEDIUM+D5 rule is per file, not per permission.** CONFIRMED (`probe-rule.log`). In a file marked D5 for setIO, `exitVM.1` and `SocketPermission * connect` were also auto-accepted. HIGH is checked first and independently (judgeListed :605-609; pinned by `aHighRatedPermissionIsNeverAcceptedByARowOrTheRule`). So the gap is only lost detection: a lint regression that drops D5 on one of two MEDIUMs in a file goes unnoticed.
6. **(c) Login after a failed watchdog.** CONFIRMED (`probe-login2.log`, a child JVM without thread grants, three logins). Attempt 1 gives a LoginException caused by ExceptionInInitializerError (AccessControlException createPlatformThread). Attempts 2 and 3 give a LoginException caused by NoClassDefFoundError. The later messages lose the root cause (only "ExceptionInInitializerError ... in thread main"). No committed test makes a second attempt (mutant MF survived).
7. **(c/d) Privileged paths, provider gate, shutdown.** Item by item:
   - Equivalent mutant: the outer `doPrivileged(StartAction)` (BoundedBody:123) is redundant. Creation is already privileged in `newThread`, and `Thread.start` needs no permission (ME survived; CONFIRMED).
   - Logout under the SecurityManager stops the thread with no modifyThread for the caller (`CHILD-LOGOUT alive=false`, CONFIRMED). Logout, abort and a second commit all go through `uninstallLocked`, which interrupts and does not join (PLAUSIBLE; the Sonnet M4 mutant killed 6).
   - Each JwksKeyCache and RefreshThread builds an HttpClient that is never closed. Its selector thread outlives logout, and a failed commit, until GC (PLAUSIBLE).
   - (e) Threads: `(JSK) JwtLoginModule-refresh`, `-body-watchdog`, `-body-closer`, all daemon (asserted by tests that pass).
   - (f) grep of jgdms-security-jwt main: `new Thread` 0, `Thread.of` 0, `Executors.` 0, `Timer` 0, ForkJoinPool 0, `sendAsync` 0. `ScheduledThreadPoolExecutor(` 1 and `ThreadPoolExecutor(` 1, both with the privileged factory. `HttpClient.new` 1, privileged.
   - Provider gate: `requireRightProviderAndParser` runs in `corpusExitCodesAndKinds`, `cleanFilesGrantNoStrangerAHighPermission` and `theLoadedProviderAndParserAreDirtyChais`. It does not run in `aSpecificNameGrantOfAnUncataloguedClassIsReported`, which also opens `auto`. No system property selects the provider (PolicyVerdict.open :199-218).
   - The exact-path rule is plain `String` equality against the listed fixture names. Case, `..`, backslash and a trailing slash are all refused. The corpus has 0 symlinks.
8. **(e) Proxy-policy generation is unchanged.** CONFIRMED.
   - `holdReason` does not hold doAs, setLoginConfiguration, `/-` or home-tree reads. doAs and setLoginConfiguration are only flagged (AUTH), and `/-` and home reads are neither held nor flagged.
   - ab94352a6 changed no ListPromotion or ProxyPolicyGenerator code or test (only ProxyPolicyGenerator.md).
   - The commit also added ratings its message does not name: SocketPermission `*.<tld>` MEDIUM (`*.com.au` is null), and SecurityPermission `authProvider.*` and `getProperty.*` MEDIUM (c04 and c12 now D5).
9. **Test fidelity.** PLAUSIBLE.
   - The refresh child test grants the caller `URLPermission https://localhost:port/- *:*`, so it cannot catch a regression that POSTs to the token endpoint on the caller's context. Only the polpAudit record shows the caller does not need it.
   - `threadStarter` is an anonymous class "so loading needs no DefineClass grant". But BoundedBody and JwksKeyCache use lambdas, so the module needs DefineClassPermission anyway (the tests grant DEFINE).

## Mutation sample (one at a time; each file restored, byte-equal to its pre-mutation backup, `sha256sum -c` OK)
| id | commit | mutant | result |
|---|---|---|---|
| MA | ab94352a6 | glob check in `allowListProblems` disabled | KILLED: `globFileReferencesAreRejected` ("glob r8/r16-rt-exitVM.policy;*/* without the corpus") |
| MB | ab94352a6 | user-home whole-tree read no longer `[H]` | KILLED: platform `testIdentityAndBroadReadRatings`, `testFileTargetsAreExpandedAndNormalised` |
| MC | ab94352a6 | auto-accept without the D5 condition | KILLED: `aMediumPermissionIsAcceptedOnlyWhereTheLintReportsD5` (the corpus run itself stayed green) |
| MD | 2c1d903c3 | `newThread` unprivileged | KILLED: 4 (2 WatchdogSecurityManagerTest, 2 JwtLoginModuleTest SM tests) |
| ME | a21acb2df | `startThread` without the outer doPrivileged | SURVIVED: equivalent (finding 7) |
| MF | 2c1d903c3 | login catches only ExceptionInInitializerError | SURVIVED (finding 6) |

## Not checked
Linux; the full platform, jeri and der suites; a fresh polpAudit run (I read the recorded files only); which frame produces the third audited domain; whether a codebase grant on `target/classes/` covers it; ConcurrentPolicyFile matching of UNC FilePermissions; HttpClient selector-thread lifetime after logout. Note: I restored PolicyLintCorpusTest in the copy from `git show HEAD` (LF), not from the archive bytes; this has no effect on compilation.

## For the operator to settle
1. Should the refresh thread be created in jwt code, so that jgdms-collections needs no URLPermission or AuthPermission (finding 2)? Or is a broad grant to jgdms-collections acceptable?
2. Must the deployment policy grant the jwt codebase only exact token and JWKS URLs and methods? The INVESTIGATION offers `https://idp/-` and `*:*` (finding 3).
3. Is `<<ALL FILES>> read` an "unscoped whole-tree read" under the 2026-11-02 ruling (MEDIUM today), and should a UNC whole-share read or write be rated (finding 4)?
4. Should proxy generation HOLD or flag what the lint now calls HIGH (doAs, setLoginConfiguration, `/-` and home reads) (finding 8)?
5. Were the SocketPermission `*.<tld>` and SecurityPermission `authProvider.*` and `getProperty.*` ratings part of the 2026-11-02 ruling (finding 8)?
6. Should the closer queue be bounded, or a timed-out reader be released without waiting for its close (finding 1)?
7. Should the auto-accept rule be per permission instead of per file (finding 5)?
