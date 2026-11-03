# Review: module thread factory, bounded close pool, whole-tree lint ratings

claude-opus-5-5, 2026-11-03

- **Reviewer:** claude-opus-5-5. **Producer:** Claude Sonnet 5.5, from the `Co-Authored-By` trailer of all four commits (`git log --no-walk --format=%B 3cf1f31d9 ca58e7430 7652bc63f a003c0172`). Different models, so this is a cross-model review.
- **Skills loaded:** handover-composition, production-code, code-comments. rag-file does not apply. `E:\Claude\TRAINING_DOCS_INDEX.md` was not read because the brief forbids `E:\`. No physics, instrument or file-format claim is under review.
- **Commits reviewed:** `3cf1f31d9`, `ca58e7430`, `7652bc63f`, `a003c0172`. trunk HEAD was `a003c0172` at review time.
- **Main checkout:** untouched. `git status --short` gave 0 lines at the end of the review.
- **Worktree:** `C:/Users/peter/Documents/GitHub/JGDMS-review-20261103`, detached at `a003c0172`. It holds these uncommitted probes, which are reproductions and not proposed tests:
  - three `reviewProbe*` methods added to `JwtLoginModuleTest`;
  - `jgdms-platform/.../ReviewProbe1103Test.java`;
  - `tools/policy-condenser/.../ReviewProbe1103LintTest.java`.
- **JDK for every result below:** DirtyChai `27-internal-adhoc.peter.DirtyChai`, with `JAVA_HOME=C:/Users/peter/Documents/GitHub/DirtyChai/build/windows-x86_64-server-release/images/jdk`, on Windows 10.
- **How tests were run:** `mvn -o -B ... -am test` from the reactor root `JGDMS/JGDMS`.
- **Discarded run:** one run without `-am` resolved a stale `jgdms-platform` from `~/.m2` and failed with `NoSuchMethodError DefaultJwtVerifier.parseObject`. It is not counted anywhere below.

## Verdict

| # | Grade | Commit | One line |
|---|---|---|---|
| F1 | CONFIRMED (precondition simulated) | ca58e7430 | If a closer thread cannot be created, logout, commit-undo and the permanent-failure path all leave the JWT and its principals on the Subject. Fail-open. |
| F2 | CONFIRMED end to end | 7652bc63f | `"///server/share/-"` grants a whole-share write on Windows. The catalogue does not rate it, the proxy flag misses it, and the lint exits 0 even with `--strict`. |
| F3 | CONFIRMED (catalogue, proxy flag); HYPOTHESIS (gate on a non-Windows host) | 7652bc63f | `"/C:/-"` is the whole C: drive on Windows. The catalogue does not rate it and the proxy flag misses it. Only the provider probe on a Windows lint host catches it. |
| F4 | CONFIRMED rating; intent is an operator question | 7652bc63f, a003c0172 | A named user's whole home (`/home/alice/-`, `C:\Users\alice\-`, `/var/root/-`) is not rated, and the gate exits 0, although `/root/-` and `/home/-` are HIGH. |
| F5 | CONFIRMED; LOW to MEDIUM | pre-existing, untouched by 7652bc63f | An undefined `${property}` name is "treated as the root", yet a read of it rates MEDIUM (D5, exit 0) and is not flagged. |
| F6 | CONFIRMED; LOW | ca58e7430 | A comment premise is false on the deployed JDK: a later `NoClassDefFoundError` does name the first failure. |
| F7 | CONFIRMED by source trace; LOW | ca58e7430 | The class comment on dropped closes is inaccurate in both directions. |
| F8 | PLAUSIBLE (mutant not run); LOW | 3cf1f31d9 | A polpAudit assertion's name claims more than it checks. |

- **Baseline:** green.
- **Test teeth:** 4 of 4 mutants of the new mechanisms were caught by the commits' own tests (see "Tests that pass either way").
- **Coverage gap:** no test covers F1. The full suite passed at baseline with F1 present.

## Baseline (MEASURED)

Command, from `JGDMS/JGDMS` in the worktree:

```
mvn -o -B -pl jgdms-security-jwt,tools/policy-condenser,jgdms-platform -am test \
  -Dtest=BoundedBodyTest,NewDaemonThreadTest,WatchdogSecurityManagerTest,JwtLoginModuleTest,PermissionSetsTest,PolicyLintCorpusTest,PolicyLintGateTest,PolicyLintTest,ListPromotionTest,ProxyPolicyGeneratorTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Log: `C:/Users/peter/AppData/Local/Temp/claude/C--Users-peter-Documents-GitHub-DirtyChai/757702ae-f74f-45fd-95a6-a302242c73cd/scratchpad/review1103-baseline.log`.

Results, as "tests run / failures":

| Module | Test class | Run / failed |
|---|---|---|
| jgdms-platform | PermissionSetsTest | 18 / 0 |
| jgdms-security-jwt | BoundedBodyTest | 8 / 0 |
| jgdms-security-jwt | JwtLoginModuleTest | 58 / 0 |
| jgdms-security-jwt | NewDaemonThreadTest | 2 / 0 |
| jgdms-security-jwt | WatchdogSecurityManagerTest | 5 / 0 |
| policy-condenser | ListPromotionTest | 47 / 0 |
| policy-condenser | PermissionSetsTest | 4 / 0 |
| policy-condenser | PolicyLintCorpusTest | 19 / 0 |
| policy-condenser | PolicyLintGateTest | 30 / 0 |
| policy-condenser | PolicyLintTest | 49 / 0 (2 skipped) |
| policy-condenser | ProxyPolicyGeneratorTest | 47 / 0 |

These counts match the commit messages.

The log contains `SEVERE: JWT refresh thread died with java.lang.AssertionError`. It is not a hidden failure: `JwtLoginModuleTest.testAnErrorInTheRefreshThreadFailsClosed` (JwtLoginModuleTest.java:1632) injects it deliberately.

## Findings, most exploitable first

### F1. A closer-thread failure leaves the session credentials installed (ca58e7430)

**Grade:** CONFIRMED under a simulated precondition. Reachability of the precondition in production is PLAUSIBLE.

**What changed.** `ca58e7430` added a client close to `JwtLoginModule.RefreshThread.shutdown()` (jgdms-security-jwt):

```java
        void shutdown() {
            running = false;
            Thread t = thread;
            if (t != null) t.interrupt();
            // Closes the client without waiting (BoundedBody.closeAsync): logout must not block on it.
            JwksKeyCache.closeClient(httpClient);
        }
```

**Why it can throw.** `JwksKeyCache.closeClient` calls `BoundedBody.closeAsync`, which catches only `RejectedExecutionException`:

```java
        try {
            CLOSERS.execute(guarded);
        } catch (RejectedExecutionException e) {
```

- `BoundedBody.CLOSERS` is `core = max = 4`, with `allowCoreThreadTimeOut(true)` and a 30 s keep-alive (BoundedBody.java:109-111).
- A `ThreadPoolExecutor` starts a new thread whenever fewer than core threads exist. So most `execute` calls go through the thread factory: idle closers have usually timed out by the time a logout or a revocation happens.
- A factory failure propagates out of `execute`. That failure can be `OutOfMemoryError: unable to create native thread`, or a `SecurityException` from `NewDaemonThread` if the module's thread grant is revoked.

**Where the throw lands.** Every caller of `shutdown()` runs it before removing what the module installed:

- `JwtLoginModule.failClosedLocked`: `caller.shutdown(); commitSucceeded = false; removeInstalledLocked();`
- `JwtLoginModule.uninstallLocked`, used by logout, abort and the commit-undo: `rt.shutdown(); refreshThread = null; } removeInstalledLocked();`
- `JwtLoginModule.failClosed` catches the `RuntimeException`. It then logs `"JWT refresh stopped but the token could not be removed"`, attributes it to a read-only Subject, and returns with the token still in place.

**Failure scenarios, measured.** Each probe replaces `CLOSERS`' thread factory to model a JVM that cannot create a thread. The probe calls the production entry point, then counts `JwtPrincipal` and `JwtRawToken` on the Subject.

| Entry point (probe method) | Closer threads alive before | Thrown | JWT principals / raw tokens after |
|---|---|---|---|
| `commit()`, threadStarter and factory both fail (`reviewProbeCommitWhenNoThreadCanBeCreated`) | 1 | `OutOfMemoryError` (not `LoginException`) | **2 / 1** |
| `refreshNow()` with HTTP 400 `invalid_grant`, factory throws `SecurityException` (`reviewProbeInvalidGrantWhenNoCloserThreadCanBeCreated`) | 3 | nothing; `refreshNow` = false; SEVERE "could not be removed: java.lang.SecurityException" | **2 / 1** |
| `logout()`, factory fails, fresh JVM (`reviewProbeLogoutWhenNoCloserThreadCanBeCreated`) | 1 | `OutOfMemoryError` | **2 / 1** (before: 2 / 1) |
| `logout()`, same probe, sharing a JVM with the others | 4 | none | 0 / 0. Precondition not met: 4 live closers means the factory is never called. This is the healthy reading. |

Logs: `review1103-probes2.log` and `review1103-probes3.log` in the scratchpad named above.

**Causal control (mutant M0).** I removed only the `JwksKeyCache.closeClient(httpClient);` line from `shutdown()` and re-ran the probes:

- commit: `LoginException`, Subject 0 / 0, with 1 closer thread alive;
- invalid_grant: Subject 0 / 0, with 2 alive;
- logout: 4 alive, so this result does not discriminate.

The added line is the cause. The parent `3cf1f31d9` had no thread creation in `shutdown()`. This is a regression.

**Consequences.**

- *Permanent refresh failure.* `invalid_grant`, 400, 401, 403, a malformed 200 and a validation failure all route through `failClosed`, so each one keeps the token installed. This breaks the ruling "fail closed (token removed)" (handover REVISION 4n §4).
- *Nothing removes it later.* The refresh thread has stopped, so nothing removes the token at its expiry.
- *The token keeps travelling.* `JwtRawToken` is a public credential that `BasicInvocationHandler.writeUserSubjects` sends. Calls made with that Subject therefore keep authenticating as the revoked or logged-out user until `exp`. The server checks only `exp` and the signature.
- *Commit and logout.* A commit that fails to start its thread throws an `Error` and leaves the identity installed. A logout throws and leaves it installed.

**Exploitability.** An attacker must make thread creation fail at the moment of revocation or logout. Two ways:

- exhaust native threads or the container's pids limit;
- change the policy at run time so that the jwt codebase loses `createPlatformThread` or `setContextClassLoader`.

Under polpAudit this never happens. Rated MEDIUM: the impact is a fail-open session, but the precondition is resource exhaustion or a policy change.

**Advice (description only).** Four changes:

1. In `failClosedLocked` and `uninstallLocked`, remove what the module installed before any step that can fail.
2. Make the client close in `shutdown()` unable to throw. Either `closeAsync` treats any failure of `execute` as a dropped close (counted and logged), or `shutdown()` guards the close and then re-raises a `VirtualMachineError` only after the removal is done.
3. Correct the `failClosed` log text: it should not blame a read-only Subject for this case.
4. Add a test that injects a failing closer factory and asserts an empty Subject after logout, after `invalid_grant` and after a failed commit. The probes above show the shape of that test.

### F2. A three-separator UNC spelling passes the lint gate (7652bc63f)

**Grade:** CONFIRMED end to end.

**The code.** `PermissionSets.filePath` (jgdms-platform) recognises a UNC name only with exactly two leading separators:

```java
        f.unc = p.startsWith("//") && p.length() > 2 && p.charAt(2) != '/';
```

**What the JDK does instead.** On Windows, `FilePermission` builds its path through `java.io.File` (DirtyChai FilePermission.java:309, :382). `WinNTFileSystem.normalizePrefix` keeps the first slash and collapses the rest into a UNC prefix (DirtyChai WinNTFileSystem.java:244). So `///server/share/-` and `\\\server\share\-` both mean `\\server\share\-`.

**Rating probe** (`ReviewProbe1103Test`; JDK ground truth is `FilePermission.implies` on this host):

| Granted | Actions | JDK implies `\\server\share\x\y` | `danger` | `flagReason` |
|---|---|---|---|---|
| `\\server\share\-` (control) | read | true | HIGH | flagged |
| `///server/share/-` | read | true | UNRATED | null |
| `///server/share/-` | write | true | UNRATED | null |
| `\\\server\share\-` | write | true | UNRATED | null |

**Gate probe** (`ReviewProbe1103LintTest`). It calls `PolicyLint.run` with default options, which use provider `auto` (here `ConcurrentPolicyFile`, the DirtyChai production provider):

- `///server/share/-` write: **exit 0**, no finding.
- `///server/share/-` read: **exit 0**.
- `///server/share/-` write with `--strict`: **exit 0**.
- Control, `//server/share/-` write: exit 1, D1.

**Why the provider does not catch it.** `PermissionSets.probes()` has no UNC probe, so the provider check is blind to shares on every host. In the five-link vocabulary, the first link to go to zero is SCENE: the probe set contains 0 samples on a share.

**Scenario.** A policy under review contains `permission java.io.FilePermission "///fileserver/deploy/-", "write";` for a broadly scoped grant. The gate passes it. Deployed on Windows, it lets that code write anywhere in the share.

**Exploitability.** It needs a policy author, an insider or a contributed policy, to choose the spelling. The gate is the control against exactly that author. Rated MEDIUM.

**Note.** The corpus test's HIGH-first rule (PolicyLintCorpusTest.java:622-626) uses the same `PermissionSets.danger`. An allow-list row for this spelling would therefore be accepted. None exists today: I checked all 13 rows.

### F3. `/C:/-` (a slash before the drive letter) is not rated (7652bc63f)

**Grade:** CONFIRMED for the catalogue and the proxy flag. The bypass of the gate on a non-Windows lint host is a HYPOTHESIS.

**What the JDK does.** `WinNTFileSystem.normalizePrefix` drops leading slashes before a drive letter. The source comment, at DirtyChai WinNTFileSystem.java:232-235, says that "/c:/path" can be used as an alternative to "c:/path".

**What the catalogue does.** `PermissionSets.filePath` takes the drive only from `p.charAt(1) == ':'`. For `/C:/-` the drive stays null and the segment list is `["C:"]`. That matches none of the whole-tree rules in `PermissionSets.fileReach`.

**Rating probe:**

| Granted | Actions | JDK implies `C:\Windows\win.ini` (or a home file) | `danger` | `flagReason` |
|---|---|---|---|---|
| `C:\-` (control) | read | true | HIGH | flagged |
| `/C:/-` | read | true | UNRATED | null |
| `/C:/-` | write | true | UNRATED | null |
| `/c:/Users/-` | read | true | UNRATED | null |

**Gate probe on this Windows host:** `/C:/-` read and write give exit 1, D1. The provider caught it, through the root probe and the user-home probe. The catalogue did not.

**What remains open:**

- `ListPromotion` flags only through `flagReason` (ListPromotion.java:428-431). A proxy list containing `/C:/-` therefore reaches the reviewer unflagged on every host. MEASURED: `flagReason` returned null.
- On a Linux or macOS lint host, the provider evaluates `/C:/-` with Unix semantics, where it is a directory named `C:`. I expect the gate to pass it. HYPOTHESIS: this needs a non-Windows run of `ReviewProbe1103LintTest`.

**Note.** `java.io.FilePermission`'s own comment says JDK code generates such names ("Some JDK codes generate such illegal names", the `new File(...)` normalisation). This is a realistic spelling in recorded policies, not only an adversarial one.

### F4. A named user's whole home is not rated (operator question)

**Measured.** All three of these are UNRATED by `danger` and `flagReason`, and the gate gives exit 0 for `/home/alice/-` read on this host:

- `/home/alice/-` read
- `C:\Users\alice\-` read
- `/var/root/-` read (macOS root's home)

**Why.** `PermissionSets.fileReach` rates a home tree HIGH only when it equals the lint process's `user.home` (or `--define user.home`). It rates `/home/-`, `/Users/-` and `/root/-` HIGH on every host.

**Scenario.** The service runs as `svc` with home `/home/svc`. Its policy grants `"/home/svc/-", "read"`, which covers keystores and `.ssh`, to a broadly scoped codebase. The lint, run as another user without `--define`, reports nothing.

**Settle by.** Read the a003c0172 ruling text ("the root user's home is a whole tree of credentials and keys"). Does it extend to one segment below `/home`, `/Users` and `C:\Users`? The REVISION 4n ruling keeps only "a UNC subtree read of a named directory" unrated; user homes are not mentioned.

### F5. An undefined property "treated as the root" rates MEDIUM for read (LOW to MEDIUM; pre-existing)

**The rule.** `PermissionSets.fileDanger` labels an undefined `${property}` name "treated as the root". Its severity rule, `(write && !medium) || high`, still gives MEDIUM for read. By contrast `/-` read is HIGH (ruling 2026-11-02).

**Proxy flag.** `PermissionSets.wholeTree` returns false for the same name ("cannot be judged here"), so it is not flagged.

**Measured.** `"${review.no.such.property}${/}-", "read"` gives D5 and **exit 0** in the default run. Write gives HIGH.

**Scenario.** A policy uses `"${deploy.root}${/}-"`, the lint host does not define `deploy.root`, and the deployment sets it to `/` or to an empty value. A whole-root read passes the default gate.

**Settle by.** Should an undefined property follow the root rule, which would make read HIGH? Or should it be refused under `--require-defined`?

### F6. Comment premise false on the deployed JDK (LOW; ca58e7430)

**The comment.** `JwtLoginModule.login` (JwtLoginModule.java:290-291) says "every later use only throws NoClassDefFoundError, which names nothing."

**Measured on DirtyChai JDK 27** (`scratchpad/ncdfe/NcdfeCause.java`). The second use throws `NoClassDefFoundError` with cause `ExceptionInInitializerError: Exception java.lang.SecurityException: access denied (createPlatformThread) marker [in thread "main"]`. JDK 18 and later attach the first failure.

**Impact.** The code is harmless: keeping the first `ExceptionInInitializerError` gives a full stack. Per code-comments, a claim about another component's behaviour should cite its source or go.

### F7. `BoundedBody` class comment on dropped closes is inaccurate both ways (LOW; ca58e7430)

The class comment says: "A dropped close leaks only that exchange until its HttpClient is closed at logout." Two problems:

- **For the JDK body stream, nothing leaks.** An interrupted reader closes the stream itself (DirtyChai ResponseSubscribers.java:487-493: `catch (InterruptedException ex) { try { close(); } ...`). The pooled close is then redundant.
- **For a stream whose close is dropped, the client may not be closed either.** The client close at logout goes through the same droppable pool (`JwksKeyCache.closeClient` calls `BoundedBody.closeAsync`), so "until ... logout" is not guaranteed.

The pool matters only when the interrupt is refused or the stream is not the JDK's.

### F8. A polpAudit assertion checks one foreign domain only (LOW; HYPOTHESIS; 3cf1f31d9)

**The gap.** `WatchdogSecurityManagerTest.assertOnlyTheModuleIsRecorded` checks two things: that the module domain is recorded, and that domains whose codebase contains `jgdms-collections` are not recorded with `VIRAL`. It does not check the caller (`test-classes`) or any other domain.

**Expected behaviour of a mutant.** If `doPrivileged` were removed from `BoundedBody.newThread`, the caller would be recorded with `createPlatformThread`, and I expect this test to stay green.

**Why it is LOW.** `theFirstReadSucceedsWhenTheCallerLacksCreatePlatformThread` uses a denying policy and covers that invariant. The gap is in the test's name, not in the suite.

**Not run.** Mutant M7, which would confirm this.

## Checks that found nothing

For each null: what I checked, and where the check would first go blind.

- **Deadlock or thread leak in `BoundedBody` under the drop policy.** None found.
  - *How checked:* static trace.
    - `closeAsync` cannot block: bounded `LinkedBlockingQueue(16)`, and REE is caught.
    - The watchdog task never waits.
    - The `Reading` monitor is held only to set a flag and interrupt.
    - Closer threads end after 30 s idle; the single watchdog thread lives for the JVM.
  - *How measured:* `theCloseQueueAndPoolAreBounded`, with 40 blocked closes, 4 threads, 16 queued and the rest dropped, passed at baseline.
  - *Where it would go blind:* only the JDK-stream and SlowClose paths were exercised. A stream whose `close()` takes a lock that its blocked read holds was not.
- **Interrupt leaking to later code.** None found.
  - `expire` interrupts only while `!done`, under the same monitor `finish` takes, and the reader clears the interrupt only when `finish` says it expired.
  - Residual (LOW): when `reader.interrupt()` is refused with a `SecurityException`, `Thread.interrupted()` still runs and can clear an unrelated pending interrupt. In the refresh thread, `running` covers this.
- **Allow-list masking a HIGH.** None found.
  - `PolicyLintCorpusTest.judgeListed` rates HIGH before any row is consulted (PolicyLintCorpusTest.java:622-626).
  - Rows: 39 before 7652bc63f and 13 after (`git show 7652bc63f~1:...cross-check-allow.tsv` and the worktree file, counting non-comment non-blank lines). I read all 13.
  - *Where it would go blind:* the same catalogue that misses F2, F3 and F4.
  - One pre-existing row deserves a look but is not part of these commits: `java.io.FilePermission lib/- write` (f14-rel-sub-write). When the service runs from its install directory, `lib/-` is its own jar directory.
- **Lint bypasses by case, action order, `.` or `..` segments, doubled separators, `${/}`, `READ` or `C:/-`.** None found. The commit's own tests cover them, and my reading of `filePath` and `hasAction` agrees.
- **Percent-encoding.** Not a bypass. `FilePermission` names are not URL-decoded (FilePermission.java:309, :382 use `java.io.File`), so `%2F` is a narrow literal name in the JDK and in the catalogue alike.
- **`NewDaemonThread` context.** Correct.
  - `doPrivileged` without a context truncates the inherited ACC at `BoundedBody.newThread` and `startThread`. Only module frames are on the privileged stack, and there is no combiner, so the caller's Subject is not inherited.
  - Confirmed by mutant M1 below and by the commits' denying-policy tests.

## Tests that pass either way (mutants)

Each mutant was applied to the worktree's main source, run with the named test, and then reverted with `git checkout`.

- Runner: `scratchpad/review1103-mutants.ps1`.
- Summary: `scratchpad/review1103-mutants-summary.txt`.
- JDK: DirtyChai 27-internal-adhoc.peter.DirtyChai.

| Mutant | Test | Result, with the failure message |
|---|---|---|
| M1: `BoundedBody.newThread` uses `NewThreadAction` (jgdms-collections) | `WatchdogSecurityManagerTest.polpAuditRecordsTheThreadPermissionsForTheModuleAndNotForJgdmsCollections` | FAILED: "createPlatformThread recorded for file:/.../jgdms-collections/target/classes/" |
| M2: no `Thread.interrupted()` after a timeout | `BoundedBodyTest.theCallersInterruptFlagIsClearAfterATimeout` | FAILED |
| M3: no `reader.interrupt()` in `Reading.expire` | `BoundedBodyTest.blockedClosesDelayNoDeadlineAndNoReader`, `theCloseQueueAndPoolAreBounded` | Both FAILED: "unrelated 100 ms deadline took 5814 ms"; "a reader took 3100 ms" |
| M4: no `cache.close()` in `login` | `JwtLoginModuleTest.testTheLoginsJwksClientIsClosedAfterValidation` | FAILED: "selector threads alive after login ... HttpClient-1-Worker-0, -1" |
| M0: no `closeClient` in `shutdown()` (F1 control) | `reviewProbe*` | The Subject is clean. No committed test distinguishes M0 from the original. |

**Summary.** The new mechanisms each have a test that fails when the mechanism is removed. F1's ordering defect has none.

`testTheRefreshChildsCallerHoldsNoTokenEndpointPermission` asserts only on the fixture string. It guards the fixture and cannot detect a production regression. That is acceptable, because the polpAudit leg of `testPolpAuditRecordsTheRefreshThreadForTheModuleAndNotForJgdmsCollections` does test production.

## Operator questions

Each can be settled by reading an existing artefact.

1. **F4.** Does the `/root/-` ruling (a003c0172 commit message; handover REVISION 4n §2) extend to every single-segment home: `/home/<user>/-`, `/Users/<user>/-`, `C:\Users\<user>\-`, `/var/root/-`?
2. **F5.** Should an undefined `${property}` "treated as the root" follow the root rule (read HIGH), or be refused outright?
3. **Allow-list row `lib/-` write.** In `cross-check-allow.tsv`, is a relative `lib/-` write meant to stay clean, given that services run from their install directory?

## Reproduction

All commands run from `C:/Users/peter/Documents/GitHub/JGDMS-review-20261103/JGDMS`, with `JAVA_HOME` set to the DirtyChai image.

| What | Command | Log in the scratchpad |
|---|---|---|
| Rating probe | `mvn -o -B -pl jgdms-platform -am test -Dtest=ReviewProbe1103Test -Dsurefire.failIfNoSpecifiedTests=false` | `review1103-probes.log`; grep `PROBE1103` |
| Ordering probes (F1) | `mvn -o -B -pl jgdms-security-jwt -am test "-Dtest=JwtLoginModuleTest#reviewProbe*" -Dsurefire.failIfNoSpecifiedTests=false`. Run the logout probe alone for a fresh JVM. | `review1103-probes2.log`, `review1103-probes3.log` |
| Lint gate probe (F2 to F5) | `mvn -o -B -pl tools/policy-condenser -am test -Dtest=ReviewProbe1103LintTest -Dsurefire.failIfNoSpecifiedTests=false` | `review1103-lintprobe.log` |

The scratchpad is `C:/Users/peter/AppData/Local/Temp/claude/C--Users-peter-Documents-GitHub-DirtyChai/757702ae-f74f-45fd-95a6-a302242c73cd/scratchpad/`.

## Not verified

- **UNVERIFIED-NEEDS-BUILD:** F3 and F2 on a non-Windows lint host. Run `ReviewProbe1103LintTest` on Linux.
- **UNVERIFIED-NEEDS-BUILD:** F8, mutant M7.
- **Not traced:** F1 through `abort()`. It is the same `uninstallLocked` path, so the result is a HYPOTHESIS by code identity.
- **Not done:** I did not read the prior Opus review beyond one keyword search. The search terms were `///`, `/C:/`, `alice`, `var/root`, `undefined`, `treated as the root`, `closeClient`, `shutdown()`, `removeInstalled`, `thread exhaustion` and `unable to create`; together they gave 0 hits. So the review does not use these terms. That does not prove the issues are absent from it.
