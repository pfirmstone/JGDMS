claude-sonnet-5-5
# jgdms-security-jwt: Opus review findings 1, 3, 6, 7, 9 (E1 to E5), 2026-11-03
Base: worktree of trunk at 3cf1f31d9. Nothing committed. JDK: DirtyChai release image. Maven: `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest=<classes> -Dsurefire.failIfNoSpecifiedTests=false`.
After: `-Dtest='au.zeus.jgdms.security.jwt.*Test'`: Tests run: 110, Failures: 0 (BoundedBodyTest 8, JwtLoginModuleTest 58, WatchdogSecurityManagerTest 5, NewDaemonThreadTest 2, JwksJsonParserTest 16, JwtPrincipalTest 21).

## E1 design (BoundedBody)
- The reader is released by the watchdog INTERRUPTING it (Reading.expire, under a monitor shared with finish(), so the interrupt cannot land after the read ended; the reader clears the flag only if the deadline fired). It throws the timeout at once and never closes an expired stream itself.
- The close goes to CLOSERS: 4 threads, queue 16 (LinkedBlockingQueue(16)), via closeAsync. A full queue DROPS the close, SEVERE log (first and every 100th, no token text), counter `droppedCloses()`. Chosen over a fresh thread per close (a peer that never answers a close could exhaust threads) and over running on the scheduler (stalls every deadline). A dropped close leaks only that exchange until its HttpClient is closed at logout (E2).
- Bound: deadline + scheduling for any number of blocked closes, for streams whose read is interruptible (the JDK response body stream is). A read that ignores interrupts is still freed only by the close (limit stated in the class comment).
- Interrupt needs `modifyThread` only for threads in a system group; a SecurityException is caught and the close remains the wake-up.
## E2 / E3 / E4 / E5
- E2: JwksKeyCache.close()/closeClient (shutdownNow + close, on a closer thread). Closed: login cache after validate (finally), each refresh cache (finally), the RefreshThread client in shutdown() (logout, abort, undo, failed commit) and in run()'s finally.
- E3: static volatile Throwable `watchdogFailure` (first ExceptionInInitializerError); every later LoginException has it as cause and the same message.
- E4: refresh child: caller holds the exact JWKS GET only (`GET:*`), module holds exact token POST and JWKS GET (`POST:*`/`GET:*`); policy-text test; polpAudit test asserts the test-classes domain was not recorded with /token or POST.
- E5: class javadoc of JwtLoginModule, "Policy for the module's codebase" (exact URLs and methods, never /-, https://*, *:*; jgdms-collections needs none).

## Fail-first (new tests on trunk main, BoundedBody trunk plus 4 hook methods only)
| test | trunk result |
|---|---|
| BoundedBodyTest.blockedClosesDelayNoDeadlineAndNoReader (8 closes block 3 s, unrelated 100 ms read) | FAIL, unrelated read took 5805 ms |
| BoundedBodyTest.theCloseQueueAndPoolAreBounded | FAIL, queue capacity expected 16 but was 2147483647 |
| JwtLoginModuleTest.testLogoutClosesTheHttpClients / testTheLoginsJwksClientIsClosedAfterValidation / testAFailedCommitAndAbortCloseTheHttpClients | FAIL x3, HttpClient-N-Worker threads alive (15 s) |
| WatchdogSecurityManagerTest.everyLoginAfterAFailedWatchdogReportsTheFirstRootCause | FAIL, second login differs from the first |
E4 has no fail-first on trunk (trunk code is right; the old policy was too wide): see M6.
## Mutants (private git-archive copy plus the patched files; file byte-compared after each; logs mut-M*.log in scratchpad/jwt13)
| mutant | result |
|---|---|
| M1 queue unbounded | KILLED `Tests run: 71, Failures: 1` (theCloseQueueAndPoolAreBounded) |
| M2 expired reader also closes the stream itself | KILLED `Failures: 3` (reader waited 3113 ms, 3100 ms, closer-thread test) |
| M3a only shutdown() stops closing the RefreshThread client | SURVIVED, equivalent: run()'s finally closes it when the interrupted thread ends |
| M3b both closes of the RefreshThread client removed | KILLED `Failures: 1` (testLogoutClosesTheHttpClients) |
| M3c cache.close() removed (login and refresh) | KILLED `Failures: 3` |
| M4 first failure not kept (later logins use their own error) | KILLED `Failures: 1` (everyLoginAfterAFailedWatchdog...) |
| M5 caller policy gets the token POST | KILLED `Failures: 1`, only by testTheRefreshChildsCallerHoldsNoTokenEndpointPermission (the child tests stay green, as expected) |
| M6 refresh run inside doPrivileged(.., contextCapturedAtCommit) | KILLED `Failures: 2` (child refresh test: no refresh; polpAudit: caller recorded with /token) |
## Not verified
Linux; the full platform/jeri/der suites; non-interruptible streams under a stalled close queue; interrupt on a thread in a system thread group under a SecurityManager; what polpAudit records for HttpClient.close()/shutdownNow() on the closer thread (assertions only cover the pre-existing names); a live IdP. The flood test bounds readers at 1000 ms and may be slow on a loaded machine.
## Questions
None open. The JDK HttpClient worker threads are virtual threads on this JDK; the E2 tests count `HttpClient-` threads, not a selector thread.
