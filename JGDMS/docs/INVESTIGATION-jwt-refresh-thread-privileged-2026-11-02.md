claude-sonnet-5-5
# JwtLoginModule refresh thread: privileged creation and start (2026-11-02)
Base: worktree of trunk at 2c1d903c3. Nothing committed. JDK: DirtyChai release image, CombinerSecurityManager and polpAudit.

## Design
- `BoundedBody.startThread(r, name)`: `doPrivileged(StartAction)` that calls the existing `newThread` (`doPrivileged(new NewThreadAction(r, name, true))`) and starts the thread. One helper, shared with the watchdog; `JwtLoginModule.commit` calls it through the test hook `threadStarter`.
- `RefreshThread` is now a `Runnable` (it cannot extend Thread and be built by NewThreadAction); it holds its `thread` for `shutdown()`'s interrupt. Module field `refreshThread` keeps its name; the test helper reads `.thread`.
- Failure to create or start: `commit` undoes itself (`uninstallLocked`) and throws `LoginException("JWT refresh thread could not be started: <class>")` with the cause. Never a session without a refresh.
- Operator-visible: the thread name is now `(JSK) JwtLoginModule-refresh` (NewThreadAction prefix, as for the watchdog); it is daemon; its context class loader is the system class loader, not the caller's. The refresh needs no CCL for anything (HttpClient, TLS, hand-written JSON/JWKS parsers); an application-loader-only security provider would not be found by a ServiceLoader on this thread.

## Permissions a deployment policy must grant (polpAudit, empty start policy)
The thread inherits only the context of the privileged block: the jwt codebase and jgdms-collections (NewThreadAction). Not the caller's. So the refresh needs these from BOTH codebases (this is a change: before, the caller's grants were what counted):
- `RuntimePermission createPlatformThread`, `setContextClassLoader`
- `URLPermission` POST for the token endpoint and GET for the JWKS (`https://idp/-`, `*:*` or the exact URLs), plus the SocketPermission connect the JDK client asks for
- `AuthPermission modifyPrincipals`, `modifyPublicCredentials` (installRefreshed)
- jwt codebase only (HttpClient built privileged): `createInnocuousThread`, `createVirtualThread`, `suppressAccessChecks`, `jdk.*` property reads, `putProviderProperty.*`, `getProxySelector`, plus `LoadClassPermission` and `DefineClassPermission`.
The CALLER no longer needs any thread permission, nor the token-endpoint URLPermission. It still needs modifyPrincipals/modifyPublicCredentials for the commit itself and the JWKS URLPermission for its own login.
polpAudit run (`-Dwatchdog.audit.policy=FILE`, test testRefreshThreadUnder...), recorded: jwt `target/classes/`: modifyPrincipals, modifyPublicCredentials, URLPermission jwks GET and token POST, createInnocuousThread, createPlatformThread, createVirtualThread, setContextClassLoader; jgdms-collections jar: modifyPrincipals, modifyPublicCredentials, both URLPermissions, createPlatformThread, setContextClassLoader; test-classes (the caller): only modifyPrincipals, modifyPublicCredentials, jwks URLPermission, suppressAccessChecks (the harness), tmp read, LoadClass. No thread permission against the caller.

## Fail-first (trunk copy + the new tests; the hook field and an unprivileged startThread shim only so they compile)
| test | trunk | after |
|---|---|---|
| testRefreshThreadUnderASecurityManager... (a,b,d: child JVM, caller lacks createPlatformThread, real refresh, logout) | FAIL AccessControlException createPlatformThread | pass |
| testCommitFailsClearlyWhenTheRefreshThreadCannotBeStarted (c) | FAIL "commit succeeded without a refresh thread" | pass |
| testRefreshThreadIsADaemonWithTheSystemClassLoaderNotTheCallers | FAIL (name has no prefix; CCL next) | pass |
After: jgdms-security-jwt 98 tests, 0 failures (JwtLoginModuleTest 53).

## Mutants (private copy `mut`, one at a time, cmp-restored)
| mutant | result |
|---|---|
| M1 default starter = unprivileged `new Thread` | KILLED 2 failures (child JVM test, CCL test) |
| M2 thread takes the caller's CCL | KILLED 1 (CCL test) |
| M3 failed start swallowed (logged, commit succeeds) | KILLED 1 (testCommitFails..., "commit succeeded without a refresh thread") |
| M4 uninstall does not call `rt.shutdown()` | KILLED 6 failures (logout/abort/fail-closed thread-stop tests, child JVM test) |

## Not verified / questions
- (c) is tested by replacing `threadStarter`: a grant cannot be withdrawn between login and commit in a child JVM (policy refresh and a delegating Policy had no effect on the caching CombinerSecurityManager). Without any grant, login fails earlier (watchdog), covered by WatchdogSecurityManagerTest.
- jgdms-collections needs URLPermission and modify* only because NewThreadAction is in the inherited context; in JGDMS deployments it is a platform jar with broad grants. Acceptable, or give the refresh thread a narrower factory?
- The thread name changed. Keep the NewThreadAction prefix?
- Not run: the full platform suite, jeri, Linux. SocketPermission connect was not separately recorded against jwt (the JDK client holds it in its own privileged block).
