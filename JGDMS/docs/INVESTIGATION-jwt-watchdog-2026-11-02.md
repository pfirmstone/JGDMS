claude-sonnet-5-5
# JWT body watchdog under a SecurityManager (Opus findings 1, 4, 5), 2026-11-02
Base: worktree of trunk at 1704c83f0 (main code identical at a92d6db90). Nothing committed. JDK: DirtyChai release image, CombinerSecurityManager (`-Djava.security.manager=default`) and polpAudit.

## Design (BoundedBody, JwksKeyCache.newHttpClient, JwtLoginModule.login)
- Threads come from `AccessController.doPrivileged(new NewThreadAction(r, name, true))` (jgdms-collections, the idiom of AbstractDgcClient/ImplRefManager), called by the thread factory each time an executor needs a thread, so no thread keeps a caller's context. NewThreadAction sets name and daemon flag and gives the SYSTEM class loader as context loader, not the caller's. I kept that: it is the JGDMS idiom and cannot pin an application or webapp loader (null or BoundedBody's own loader would also work).
- The scheduler thread is started in the static initialiser (`prestartAllCoreThreads`). Any failure there becomes `ExceptionInInitializerError`; `login()` turns that (or NoClassDefFoundError on a later touch) into a LoginException "response-body deadline watchdog could not be started". No path reads a body without a deadline.
- B2: `WATCHDOG` (1 thread) only fires deadlines; the close runs on `CLOSERS` (ThreadPoolExecutor, 4 daemon core threads, created on demand, 30 s idle timeout). More than 4 simultaneously blocked closes still queue.
- Beyond the brief, needed for B4(b): the HttpClient is now built in a privileged block (`JwksKeyCache.newHttpClient`, also used by RefreshThread). Built unprivileged, the JDK 27 client demands createPlatformThread, createInnocuousThread, createVirtualThread, suppressAccessChecks and putProviderProperty of the CALLER's stack (first audit run recorded them against the test-classes domain). Sending stays unprivileged: the caller still needs URLPermission.

## B5 (polpAudit, empty starting policy, child does 2 reads; `-Dwatchdog.audit.policy=FILE` in WatchdogSecurityManagerTest)
createPlatformThread was recorded for the jwt codebase and the jgdms-collections jar (NewThreadAction is on the stack) and NOT for the calling domain (test-classes got only LoadClassPermission and tmp-read). Recorded, abridged (full: scratchpad jwt10\audit-read.policy and audit-login2.policy):
`grant codebase "file:/.../jgdms-security-jwt/target/classes/" { LoadClassPermission "ALLOW"; createPlatformThread; setContextClassLoader; DefineClassPermission "ALLOW"; }`
`grant codebase "file:/.../jgdms-collections-4.0.0-SNAPSHOT.jar" { LoadClassPermission "ALLOW"; createPlatformThread; setContextClassLoader; }`
Login run (audit-login2): test-classes domain recorded only URLPermission GET:Accept, AuthPermission modifyPrincipals/modifyPublicCredentials, tmp read, LoadClass. polpAudit also writes odd per-class codebases (`.../classes/au.zeus...BoundedBody`) and `${java.io.tmpdir}` for the worktree path; the condenser must merge them.

## Fail-first (new tests on trunk main; JwtLoginModuleTest 2 new + 3 new classes) and after
| test | trunk | after |
|---|---|---|
| theFirstReadSucceedsWhenTheCallerLacksCreatePlatformThread | FAIL createPlatformThread denied | pass |
| aDeadlineThatPassesWorks... (closer thread under SM) | FAIL | pass |
| withoutTheGrantLoginFailsWithAClearMessage | FAIL | pass |
| testLoginUnderASecurityManager... | FAIL (HttpClient needs caller permissions; the watchdog alone is not the first failure) | pass |
| aBlockingCloseDoesNotDelayAnotherDeadline | FAIL 2081 ms | pass |
| theThreadsAreDaemonsWithAFixedContextClassLoader | FAIL | pass |
| EOF-after-close timeout, option range, throwing stream | pass (pins existing behaviour; killed MJ-C/MJ-D below) | pass |
After: 95 tests in jgdms-security-jwt (JwtLoginModuleTest 50), 43 jeri, 50 platform, all 0 failures (command in the report).

## Mutants (private copy wtb, one at a time, cmp-restored; same 4 classes + 2 JwtLoginModuleTest methods)
| mutant | result |
|---|---|
| M1 lazy `new Thread` unprivileged | KILLED 5 failures (3 SM child tests, login, CCL) |
| M2 prestart privileged, factory unprivileged | SURVIVED: equivalent here, later threads are created on pool threads whose inherited context is already privileged; a replacement from a caller thread cannot be provoked |
| M3 close on the scheduler thread | KILLED (aBlockingClose, CCL) |
| M4 post-read timeout check removed | KILLED 3 (EOF test, aBlockingClose, expire child) |
| M5 minimum deadline 99 | KILLED (option-range test) |

## Not verified / questions
- Brief says '' and null deadline give a LoginException; the module doc says blank or absent = default 10000 and the code does that, so I pinned the default instead. Reject blank?
- RefreshThread itself is still created unprivileged at commit (`new RefreshThread`, `rt.start()`): same defect class, not fixed. Do it?
- The test policy needs `jrt:/-` AllPermission, LoadClassPermission and DefineClassPermission (any lambda of the module) per codebase; real policies must carry them. Linux, the full platform suite, and a live refresh under a SecurityManager not run.
