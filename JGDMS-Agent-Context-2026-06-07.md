# JGDMS QA / DirtyChai — Agent Context Document
**Date:** 2026-06-07  
**Repo:** `pfirmstone/JGDMS`, branch `copilot/the-granola-paper-benefits`  
**Environment:** Windows 10, DirtyChai JDK 27 (`C:\Program Files\DirtyChai\jdk`), Apache Ant 1.10.17, Maven, Cygwin  
**Working directory:** `C:\Users\peter\Documents\GitHub\JGDMS\qa`  
**Goal:** `AdminIfTest` (mahalo transaction manager) passing for both JERI and JSSE variants

---

## Current Status

### JERI variant — **PASSING**
`ant clean build policy-update-run-tests` with the JERI config passes as of fix43.

### JSSE variant — **IN PROGRESS**
`SslServerEndpointImpl.java` has been manually cleaned up after a bad formatter run. It is being compiled now. The JSSE test has not yet been run with all fixes in place.

### `SslServerEndpointImpl.java` — current state
The file has been manually formatted by Peter and contains all functional changes. One known remaining issue to fix before compiling:

**Line ~857:** Inside `listen()` (which is a method on `SslListenEndpoint`):
```java
listenHandle.listenEndpoint.initIfNeeded();  // WRONG — listenHandle doesn't exist yet
```
Should be:
```java
initIfNeeded();  // CORRECT — listen() is already on SslListenEndpoint, call directly
```

---

## Root Causes Found and Fixed This Session

### 1. DirtyChai Bug — `Subject.callAs(Subject, Callable)` with null subject
**Location:** DirtyChai `Subject.java`, `callAs(Subject subject, Callable action)`  
**Bug:** `rNull(subject)` where subject is null produces `new Subject[]{null}` (one-element array with null element) instead of `NO_SUBJECTS` (empty array). `SubjectDomainCombiner` later reads `subject[0]`, gets null, throws `NullPointerException`. This NPE fires from inside the blocking I/O path when a security manager is installed, causing `socket.read()` to silently never return — manifesting as "timeout waiting for server to respond to handshake".  
**Fix (in DirtyChai):** In `callAs(Subject subject, Callable action)`, change:
```java
return callNoCheck(action, rNull(subject));
```
to:
```java
return callNoCheck(action, subject != null ? new Subject[]{subject} : NO_SUBJECTS);
```
**Status:** Fix identified. Peter to apply to DirtyChai source.

### 2. DirtyChai Design Issue — Thread subject inheritance
**Location:** DirtyChai `Thread.java`, constructors at lines ~846 (platform) and ~894 (virtual)  
**Issue:** `this.scopedSubject = VM.isBooted() ? SubjectAccess.scoped() : null` captures `Subject.current()` at thread construction time. `Thread.runWith` then wraps the entire thread body in `SubjectAccess.callNoCheck(op, scopedSubject)` — a ScopedValue scope — for the whole thread lifetime. Infrastructure threads (accept loops, mux reader/writer) created during a request dispatch running as "Tester" therefore inherit "Tester" and run their entire I/O loop inside a "Tester" subject scope. This is a confused-deputy risk and, combined with bug #1, causes the mux handshake to hang.  
**Invariant established:** `Subject.callAs` scopes must be bounded. A scope must never enclose a blocking wait (socket.read, Object.wait, etc.) on any thread — platform or virtual. The ScopedValue scope produced by `Thread.runWith` wrapping an unbounded I/O loop violates this contract and causes the blocking operation to silently never complete.  
**Fix (in JGDMS):** Infrastructure threads are created via `Subject.callAs(null, () -> thread.create())` — wrapping only the **construction** (bounded), not the thread body. This ensures `scopedSubject` captures null, so `Thread.runWith` takes the no-scope `else` branch.  
**Status:** Applied in `SslServerEndpointImpl.java` and `ThreadPool.java`.

### 3. Mux handshake timeout — IPv6 link-local address
**Location:** `qa/src/org/apache/river/qa/harness/GroupImpl.java`  
**Bug:** `TcpServerEndpoint.getInstance(0)` resolved to `fe80::...%7` (IPv6 link-local with zone ID). Connecting to a link-local address with zone ID from another process on the same machine fails silently at the socket level.  
**Fix:** Use `System.getProperty("java.rmi.server.hostname", "localhost")` as the host:
```java
public GroupImpl() {
    this(new BasicJeriExporter(
        TcpServerEndpoint.getInstance(
            System.getProperty("java.rmi.server.hostname", "localhost"), 0),
        new AtomicILFactory(null, null, GroupImpl.class)));
}
```
And in `qaDefaults.properties` `globalvmargs`: `-Djava.rmi.server.hostname=localhost`  
With `preferIPv6Addresses=true`, localhost resolves to `::1` (IPv6 loopback) — correctly routable between processes.  
**Status:** Applied.

### 4. `SslServerEndpointImpl` — infrastructure thread safety
**Location:** `JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/ssl/SslServerEndpointImpl.java`

**a) Subject-neutral infrastructure threads:**  
The `acceptThreadBuilder` at line 98 was `Thread.ofVirtual()` — threads built from it inherited whatever subject was current at construction. Fixed by wrapping `.start()` and `.unstarted()` call sites in `Subject.callAs(null, ...)`:
```java
// scheduleSSLContextRebuild (line ~765):
Subject.callAs(null, () ->
    acceptThreadBuilder.name("SSL context rebuild after SPIFFE rotation").start(runnable));

// createAcceptThread() (line ~1072):
Subject.callAs(null, () ->
    acceptThreadBuilder.name(toString()).unstarted(this::acceptLoop));
```

**b) `this`-escape fix:**  
`SslListenHandle` constructor previously called `acceptThreadBuilder.name(toString()).unstarted(this::acceptLoop)` — both `toString()` and `this::acceptLoop` escape `this` before construction completes. Fixed by:
- Making `acceptThread` non-final, initialised to null in constructor
- Adding `Thread createAcceptThread()` method called after construction in `createListenHandle()`

**c) Eager SSL context initialisation:**  
`sslInit()` was lazy (double-checked lock in `getSSLSocketFactory()`), meaning it could fire on a subject-neutral infrastructure thread where `Subject.current()` returns null, leaving `serverSubject` null and producing an anonymous SSL context.  
Fixed by adding `initIfNeeded()` to `SslListenEndpoint` and calling it eagerly from `listen()` while the service subject is active:
```java
synchronized void initIfNeeded() {
    if (sslSocketFactory == null) sslInit();
}
```
And in `listen()`:
```java
initIfNeeded(); // call directly — listen() is a method on SslListenEndpoint
```

**Status:** Applied but `listenHandle.listenEndpoint.initIfNeeded()` needs correcting to `initIfNeeded()` (see Current Status above).

### 5. `StreamConnectionIO` — virtual thread race condition mitigation
**Location:** `JGDMS/jgdms-jeri/src/main/java/org/apache/river/jeri/internal/mux/StreamConnectionIO.java`  
**Change:** `mux.muxLock.wait()` → `mux.muxLock.wait(1000)` in `Writer.run()`. Defensive improvement: if the Reader calls `notifyAll()` before the Writer virtual thread has started (race between submission and scheduling), the Writer wakes after 1s and finds the queued data. The original REMIND comment in the code noted this timeout should be considered.  
**Status:** Applied.

### 6. `ThreadPool` redesign
**Location:** `JGDMS/jgdms-jeri/src/main/java/org/apache/river/thread/ThreadPool.java`  
**Change:** Now uses `Executors.newVirtualThreadPerTaskExecutor()`. The executor is created inside `Subject.callAs(null, ...)` so that threads submitted to it capture null as `scopedSubject`. `Task.run()` does NOT wrap in `callAs` — the null inheritance from construction is sufficient.  
**GetThreadPoolAction:** Both system and user pools are now plain `ThreadPool` — no subject propagation in either. `SubjectPropagatingThreadPool` exists separately for bounded per-request dispatch.  
**Note:** `ThreadPool` is considered legacy — for new code in `SslServerEndpointImpl`, threads are created directly via `Thread.Builder.OfVirtual`.  
**Status:** Applied.

### 7. QA harness fixes
**`QAConfig.parseArgList`:** Added `buffer = buffer.trim()` before the length check — strips leading/trailing whitespace including newlines from CRLF line-continuation in `qaDefaults.properties`.  
**`qaDefaults.properties`:**
- `nonActivatableGroup.serverjvmargs` — removed self-referential `${nonActivatableGroup.serverjvmargs}` and orphaned `-Djavax.security.debug=all` line.
- `globalvmargs` — changed `-Djava.security.manager=default` to `-Djava.security.manager=${org.apache.river.qa.harness.securitymanager}`.
- Added `-Djava.rmi.server.hostname=localhost` to `globalvmargs`.
- LF-only line endings.
**`GroupImpl.java`:** localhost hostname fix (see #3 above).  
**Status:** Applied.

---

## Key Files and Locations

| File | Path | Status |
|------|------|--------|
| `SslServerEndpointImpl.java` | `JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/ssl/` | Modified — one fix remaining (see above) |
| `StreamConnectionIO.java` | `JGDMS/jgdms-jeri/src/main/java/org/apache/river/jeri/internal/mux/` | Modified |
| `ThreadPool.java` | `JGDMS/jgdms-jeri/src/main/java/org/apache/river/thread/` | Modified |
| `GetThreadPoolAction.java` | `JGDMS/jgdms-jeri/src/main/java/org/apache/river/thread/` | Modified |
| `SubjectPropagatingThreadPool.java` | `JGDMS/jgdms-jeri/src/main/java/org/apache/river/thread/` | Modified |
| `GroupImpl.java` | `qa/src/org/apache/river/qa/harness/` | Modified |
| `QAConfig.java` | `qa/src/org/apache/river/qa/harness/` | Modified |
| `qaDefaults.properties` | `qa/src/org/apache/river/qa/resources/` | Modified |
| `jsselogins` | `qa/harness/trust/` | Modified (uses `file:${qa.home}/...`) |
| `TxnManagerImpl.java` | `JGDMS/services/mahalo/mahalo-service/src/main/java/org/apache/river/mahalo/` | Diagnostics to remove once JSSE passes |
| `ScopedValueBlockingIOTest.java` | (standalone test) | New — regression test for DirtyChai callAs(null) bug |

---

## DirtyChai Architecture (Critical Background)

- **Two-Subject model:** `WorkerSubject` (SPIFFE, ambient, process-wide, never pass to `callAs`) and `UserSubject` (per-request, set via `Subject.callAs()`, ScopedValue).
- **Thread subject inheritance:** `Thread` captures `Subject.current()` at construction as `scopedSubject`. `Thread.runWith` re-establishes it as a ScopedValue scope around the whole thread body. Consequence: thread inherits whatever subject was active when it was constructed.
- **`Subject.callAs` — ScopedValue based:** Bounded scopes only. Must never enclose unbounded blocking I/O.
- **`Subject.doAs` — legacy:** Same ScopedValue as `callAs` plus `AccessController.doPrivileged` with ACC. Used in `KerberosServerEndpoint` — not affected by `ThreadPool` changes since it establishes its own explicit scope per connection.
- **`polpAudit` = `SecurityPolicyWriter`:** Always returns `true` from `checkPermission`, records permissions. Does NOT enforce during recording. Standard security manager path is NOT bypassed — `AccessController.doPrivileged` with explicit ACC, or direct `Policy.implies()`, still enforces.
- **`Subject.callAs(null, ...)` bug:** With security manager present, `rNull(null)` → `Subject[]{null}` → `SubjectDomainCombiner` NPE inside blocking I/O path → silent hang. Fix: `subject != null ? new Subject[]{subject} : NO_SUBJECTS`.

## Build Commands
```bash
# QA harness rebuild only:
cd C:\Users\peter\Documents\GitHub\JGDMS\qa
ant clean build

# Full Maven build of jgdms-jeri:
cd C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\jgdms-jeri
mvn install

# Run JERI test:
cd C:\Users\peter\Documents\GitHub\JGDMS\qa
ant clean build policy-update-run-tests  # uses jeri config

# Run JSSE test:
cd C:\Users\peter\Documents\GitHub\JGDMS\qa
ant clean build policy-update-run-tests  # uses jsse config
```

## Pending Work
1. **[IMMEDIATE]** Fix `initIfNeeded()` call in `listen()` from `listenHandle.listenEndpoint.initIfNeeded()` to `initIfNeeded()`
2. **[IMMEDIATE]** Compile `jgdms-jeri` and run JSSE AdminIfTest
3. **[DirtyChai]** Fix `rNull(null)` in `Subject.callAs(Subject, Callable)`
4. **[Cleanup]** Remove diagnostics from `TxnManagerImpl.java` once JSSE passes
5. **[Future]** Full deprecation of `GetThreadPoolAction`/`ThreadPool` from `SslServerEndpointImpl` — replace with direct `Thread.Builder.OfVirtual` creation at each site
6. **[Future]** `KerberosServerEndpoint` — verify `Subject.doAs` still works correctly for JGSS in DirtyChai
7. **[Future]** Service configs — `SslServerEndpoint.getInstance(0)` → `SslServerEndpoint.getInstance(loginContext.getSubject(), 0)` for explicit subject binding

---

## Claude Code vs New Chat

**Recommendation: Claude Code** for the remaining work. Reasons:
- The remaining issues are surgical code changes (one-line fix, compilation check, test run) that benefit from direct filesystem access to the repo
- Claude Code can edit files in-place, run `mvn install` and `ant` directly, and iterate on compilation errors without file upload/download cycles
- The context document above gives a Code agent everything it needs to understand the codebase state
- The main risk with a new chat is context loss — the DirtyChai architecture details and the `callAs` invariant are subtle enough that re-explaining them takes time

Start a Claude Code session from `C:\Users\peter\Documents\GitHub\JGDMS` and point it at this context document.
