claude-sonnet-5-5
# jgdms-security-jwt: own thread factory, jgdms-collections no longer needs the module's permissions (2026-11-02)
Base: worktree of trunk at e246a687b. Nothing committed. JDK: DirtyChai release image.

## Design
- New package-private `NewDaemonThread` (PrivilegedAction<Thread>) in the jwt module: `new Thread(r, "(JSK) " + name)`, `setDaemon(true)`, `setContextClassLoader(system loader)`, optional `start()`. Same observable behaviour as `NewThreadAction`. Calls nothing outside the module and the JDK.
- `BoundedBody.newThread` (watchdog and closer pools) and `startThread` (refresh thread, via `JwtLoginModule.threadStarter`) use it; `StartAction` is gone. The privileged stack is now Thread, NewDaemonThread, BoundedBody: jwt domain only, so the new thread inherits only that domain.
- Pom: jgdms-security-jwt declares no jgdms-collections dependency (it came transitively through jgdms-platform), and `git grep org.apache.river` in the module is empty after the change. Nothing to remove; pom unchanged.

## Recorded policy after the change (polpAudit, empty start; refresh child; abridged from the recorded file)
`grant codebase ".../jgdms-security-jwt/target/classes/" { LoadClass; createInnocuousThread; createPlatformThread; createVirtualThread; setContextClassLoader; AuthPermission modifyPrincipals, modifyPublicCredentials; URLPermission https://localhost:PORT/jwks.json GET, /token POST; ... }`
jgdms-collections jar: one grant block, `FilePermission <its own jar> read` (from JDK classloader) and nothing else; no createPlatformThread, setContextClassLoader, URLPermission, AuthPermission. On trunk the same run recorded createPlatformThread and setContextClassLoader for it (mutant M1 reproduces that).

## Tests
- `WatchdogSecurityManagerTest.polpAuditRecordsTheThreadPermissionsForTheModuleAndNotForJgdmsCollections` and `JwtLoginModuleTest.testPolpAuditRecordsTheRefreshThread...` run the child under polpAudit with an empty policy into a temp file, parse it and assert: module domain has the names; no jgdms-collections grant contains the four. (`-Dwatchdog.audit.copy=FILE` keeps the recorded file.)
- The SecurityManager child tests no longer grant jgdms-collections anything (`runChild` lost its collectionsGrant parameter; the jar gets LoadClass/read only).
- `NewDaemonThreadTest`: prefix, daemon, system loader with a foreign context loader, started and unstarted.
- `runChild` read the child's output before waiting, so a child that never exits hung the test; it now reads on a daemon thread and times out at 120 s.

## Fail-first (trunk main, new tests): 6 failures
Watchdog: theFirstRead..., aDeadlineThatPasses..., polpAudit...; JwtLoginModuleTest: login, refresh under SM, polpAudit refresh. Child output: AccessControlException "createPlatformThread" from NewThreadAction.run (jgdms-collections, no grant) in the BoundedBody static initialiser; polpAudit tests: collections recorded with the permissions.

## After: `mvn -o -pl jgdms-security-jwt,jgdms-platform,jgdms-der,jgdms-jeri test -Dtest='au.zeus.jgdms.security.jwt.*Test'`: Tests run: 102, Failures: 0 (JwtLoginModuleTest 54, Watchdog 4, NewDaemonThread 2, BoundedBody 5).

## Mutants (private git-archive copy, NewDaemonThread.java byte-compared after)
| mutant (tests: Watchdog, NewDaemonThread, JwtLoginModuleTest) | result |
|---|---|
| M1 `new NewThreadAction(r,name,true).run()` inside the action | KILLED, 6 failures (3 child SM tests: createPlatformThread denied; 2 polpAudit tests: collections recorded) ; `Tests run: 60, Failures: 6` |
| M2 `setDaemon(true)` removed | KILLED, `Tests run: 54, Failures: 4` (JwtLoginModuleTest), 2 (NewDaemonThreadTest), 3 (Watchdog, children never exit: 120 s timeout each) |
| M3 `setContextClassLoader` removed | KILLED, `Tests run: 60, Failures: 5` (NewDaemonThread 2, daemon/CCL refresh test, 2 polpAudit: setContextClassLoader not recorded) |

## Not verified
Linux; the full platform suite; a live service with a real policy. A policy for a deployment whose polpAudit data was recorded on trunk still lists the jgdms-collections grants: the condenser output must be regenerated.

## Questions
None open. The "(JSK) " prefix is kept as ruled.
