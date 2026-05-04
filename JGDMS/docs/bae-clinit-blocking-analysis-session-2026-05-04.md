# BAE `<clinit>` Blocking Analysis — Session Notes (2026-05-04)

**Branch:** `copilot/bae-asm-verdictregistry-issue-206`  
**PR:** open on branch above — check https://github.com/pfirmstone/JGDMS/pulls

---

## Context

This session extended the **Bytecode Analysis Engine (BAE)** to detect and classify
blocking call paths reachable from `<clinit>` (static initialisers) that an attacker
could exploit to pin virtual-thread carrier threads.

The threat model:
> An attacker ships a class whose `<clinit>` calls a blocking Java or DirtyChai API.
> When a virtual thread first loads that class, its OS carrier thread is pinned for
> the duration of the blocking call.  If the call blocks indefinitely the carrier
> thread is permanently occupied, degrading throughput or enabling a DoS.

---

## What Was Done

### 1. New `ClinitVerdict` value — `BLOCKING_GUARDED`

`JGDMS/jgdms-platform/src/main/java/au/net/zeus/jgdms/api/codebase/ClinitVerdict.java`

| Verdict | Meaning | `deriveVerdictType()` |
|---------|---------|----------------------|
| `CLEAN` | No blocking path found | `SAFE` |
| `BLOCKING` | Blocking path, **no** permission guard | `DANGEROUS` |
| `BLOCKING_GUARDED` | Blocking path, **preceded by** `SecurityManager.checkXxx` or `AccessController.checkPermission` | `INCONCLUSIVE` |
| `NATIVE_OPACITY` | Native method, cannot determine statically | `INCONCLUSIVE` |
| `CYCLE` | Circular `<clinit>` dependency | `DANGEROUS` |

`BLOCKING_GUARDED` is `INCONCLUSIVE` because:
- With a `SecurityManager` that *denies* the guarding permission → `SecurityException`
  thrown before the blocking call → no pin.
- With no `SecurityManager` (or a permissive one) → the blocking call *is* reached →
  behaves like `BLOCKING`.
- Policy authors can use this signal to decide whether to grant the permission.

### 2. `BlockingSinkRegistry` — expanded sink list

`JGDMS/services/bytecode-analysis-engine/bytecode-analysis-engine-service/src/main/java/au/net/zeus/jgdms/bae/BlockingSinkRegistry.java`

Added/confirmed the following categories of blocking sinks:

#### Java Standard API (previously missing)
| Category | Methods |
|----------|---------|
| `FileInputStream` / `FileOutputStream` | `read`, `write` |
| `PipedInputStream` / `PipedOutputStream` | `read`, `write` |
| `Reader` / `Writer` | `read`, `write` |
| `DatagramSocket` | `receive` |
| `java.nio.channels.Selector` | `select`, `select(long)`, `select(Consumer)`, `select(Consumer,long)`, `selectNow` |
| `SocketChannel` / `ServerSocketChannel` | `read`, `write`, `accept` |
| `FileChannel` | `read`, `write`, `lock` |
| `Process` | `waitFor`, `waitFor(long, TimeUnit)` |
| `CompletableFuture` | `get`, `get(long,TimeUnit)`, `join` |
| `StampedLock` | `readLock`, `writeLock`, `readLockInterruptibly`, `writeLockInterruptibly` |
| `LinkedTransferQueue` | `take`, `put`, `transfer`, `tryTransfer(E,long,TimeUnit)` |
| `DelayQueue` | `take`, `put` |

#### DirtyChai-specific (new)
| Class | Method | Descriptor | Blocking sink |
|-------|--------|-----------|--------------|
| `sun.nio.ch.Poller` | `poll` | `(IIJLjava/util/function/BooleanSupplier;)V` | `LockSupport.parkNanos` / `LockSupport.park` |
| `sun.nio.ch.Poller` | `pollSelector` | `(IJ)V` | `LockSupport.park` |

**Why Poller is dangerous from `<clinit>`:** `Poller` is a `public` class and its static
`poll`/`pollSelector` methods have no permission guard.  A `<clinit>` that calls one of
them will park the calling thread (the virtual thread's carrier) until an I/O event
arrives.

### 3. Permission-guard infrastructure

Two new methods on `BlockingSinkRegistry`:

```java
// Three-arg form: decomposed owner / name / descriptor
static boolean isPermissionGuard(String owner, String name, String descriptor)

// Single composite key "owner/name/(descriptor)" — uses lastIndexOf("/(") to
// correctly handle '/' inside type descriptors, e.g. (Ljava/security/Permission;)V
static boolean isPermissionGuardKey(String calleeKey)
```

Recognised guards:
- Any method on `java.lang.SecurityManager` whose name starts with `"check"` (prefix
  match — covers all current and future `checkXxx` variants).
- `java.security.AccessController.checkPermission(Permission)` (exact match).

`AccessController.doPrivileged()` is intentionally **not** a guard — it elevates
privilege but does not prevent the blocking call from being reached.

### 4. BFS guard detection in `ClinitBlockingVisitor`

`JGDMS/services/bytecode-analysis-engine/bytecode-analysis-engine-service/src/main/java/au/net/zeus/jgdms/bae/ClinitBlockingVisitor.java`

New method `hasPermissionGuardOnPath(path, callGraph)`:
- Iterates over every method on the BFS path **except the final blocking sink**.
- For each method, inspects its sibling callees in the call graph.
- If any sibling callee is a permission guard → return `BLOCKING_GUARDED`; otherwise
  return `BLOCKING`.

This is a **conservative sibling-call heuristic**: static analysis cannot determine
whether the guard appears before or after the blocking call at runtime.  The `GUARDED`
classification therefore means "a guard exists somewhere in the same method body or on
the path", not "the guard is definitely sequenced before the block".

### 5. `JarAnalysisReport.deriveVerdictType()`

`JGDMS/jgdms-platform/src/main/java/au/net/zeus/jgdms/api/codebase/JarAnalysisReport.java`

Added `BLOCKING_GUARDED → INCONCLUSIVE` alongside the existing `NATIVE_OPACITY` case.

### 6. New test class — `ClinitBlockingVisitorTest`

`JGDMS/services/bytecode-analysis-engine/bytecode-analysis-engine-service/src/test/java/au/net/zeus/jgdms/bae/ClinitBlockingVisitorTest.java`

30 new tests covering:
- Regression: existing sinks (`Thread.sleep`, `Object.wait`, `LockSupport.park`)
  still return `BLOCKING`.
- All newly added Java API sinks return `BLOCKING`.
- The two DirtyChai `Poller` sinks return `BLOCKING`.
- Four `BLOCKING_GUARDED` scenarios (Thread.sleep, Socket.connect, Selector.select,
  Poller.poll each preceded by `SecurityManager.checkPermission`).
- Six `BlockingSinkRegistry.isPermissionGuard` / `isPermissionGuardKey` unit checks.

All 43 BAE tests pass.

---

## What Is Still Open / Next Steps

### A. Deeper DirtyChai API audit
The analysis above focused on `sun.nio.ch.Poller` (the most obvious case) and the
`au.zeus.jdk.*` packages.  A more thorough scan of all `public` DirtyChai API classes
(beyond `au.zeus.jdk.concurrent.RC`, `ReferenceBlockingQueue`, `ReferenceBlockingDeque`)
may reveal additional sinks that should be added to `BlockingSinkRegistry`.

Key classes to revisit:
- `au.zeus.jdk.concurrent.RC` — factory methods return decorated `BlockingQueue`/
  `BlockingDeque`.  The `put`/`take` operations on the wrappers are already in the sink
  list via the `BlockingQueue` interface entries; confirm the concrete wrapper classes
  are also covered.
- `au.zeus.jdk.thread.*` — check for any public methods that ultimately park.
- `au.zeus.jdk.net.*` — check for socket/channel wrappers.

### B. Heuristic precision for `BLOCKING_GUARDED`
The current heuristic is *conservative*: it calls a path guarded if a `checkXxx` call
appears anywhere in the same method body (sibling call), not necessarily sequenced
before the blocking call.  A more precise flow-sensitive analysis would:
1. Track basic-block ordering within each bytecode method (using ASM's `AnalyzerAdapter`
   or a simple control-flow graph).
2. Only mark `BLOCKING_GUARDED` if the permission check **dominates** the blocking call
   (i.e., every execution path that reaches the blocking call must pass through the
   check).

This is substantially more complex; the current heuristic is a safe starting point
since it errs toward `BLOCKING_GUARDED` rather than `BLOCKING`.

### C. `AccessController.doPrivileged` disambiguation
Some `<clinit>` paths use `doPrivileged` to wrap both a guard and a blocking call
inside the same action.  The current logic ignores `doPrivileged` (correctly, because
it does not prevent the call).  However, if the `PrivilegedAction` itself contains a
`checkPermission` call *and* a blocking call, the inner calls will not appear in the
outer method's callee set.  A future improvement could recurse into `doPrivileged`
lambda targets.

### D. Integration with `VerdictRegistry` / policy tooling
The `BLOCKING_GUARDED` verdict is now surfaced as `INCONCLUSIVE` in the aggregate
`VerdictType`.  Policy tooling (e.g., `ConcurrentPolicyFile`) could consume this to
generate a more informative warning: "class X blocks only if permission Y is granted".

---

## Key Files

| File | Purpose |
|------|---------|
| `jgdms-platform/…/ClinitVerdict.java` | Enum: CLEAN, BLOCKING, **BLOCKING_GUARDED**, NATIVE_OPACITY, CYCLE |
| `jgdms-platform/…/JarAnalysisReport.java` | `deriveVerdictType()` — BLOCKING_GUARDED → INCONCLUSIVE |
| `jgdms-platform/…/ClassAnalysisResult.java` | Javadoc: `blockingCallPath` populated for both BLOCKING and BLOCKING_GUARDED |
| `bae/…/BlockingSinkRegistry.java` | Sink list + guard list + `isPermissionGuard` / `isPermissionGuardKey` |
| `bae/…/ClinitBlockingVisitor.java` | BFS + `hasPermissionGuardOnPath` heuristic |
| `bae/…/ClinitBlockingVisitorTest.java` | 30 new unit tests |

---

## Build & Test Quick-Reference

```bash
# From JGDMS/ directory

# 1. Build platform module (needed first — ClinitVerdict lives here)
mvn install -pl jgdms-collections,jgdms-activation-parameters,jgdms-platform \
  -Ddependency-check.skip=true -DskipTests -am -q

# 2. Download ASM 9.7.1 if not already cached
ASM_JAR=~/.m2/repository/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar
mkdir -p $(dirname $ASM_JAR)
[ -f $ASM_JAR ] || curl -fsSL \
  https://repo1.maven.org/maven2/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar \
  -o $ASM_JAR

# 3. Compile & run BAE tests
PLATFORM_JAR=~/.m2/repository/au/net/zeus/jgdms/jgdms-platform/3.1.1-SNAPSHOT/jgdms-platform-3.1.1-SNAPSHOT.jar
JUNIT_JAR=~/.m2/repository/junit/junit/4.13.2/junit-4.13.2.jar
HAMCREST_JAR=$(find ~/.m2/repository -name "hamcrest-core-*.jar" | head -1)
BAE_SRC=services/bytecode-analysis-engine/bytecode-analysis-engine-service/src/main/java
BAE_TEST=services/bytecode-analysis-engine/bytecode-analysis-engine-service/src/test/java

mkdir -p /tmp/bae-out
javac -cp "$PLATFORM_JAR:$ASM_JAR" -d /tmp/bae-out \
  $BAE_SRC/au/net/zeus/jgdms/bae/BlockingSinkRegistry.java \
  $BAE_SRC/au/net/zeus/jgdms/bae/ClinitBlockingVisitor.java \
  $BAE_SRC/au/net/zeus/jgdms/bae/AtomicSerialComplianceVisitor.java

javac -cp "$PLATFORM_JAR:$ASM_JAR:$JUNIT_JAR:$HAMCREST_JAR:/tmp/bae-out" \
  -d /tmp/bae-out \
  $BAE_TEST/au/net/zeus/jgdms/bae/ClinitBlockingVisitorTest.java \
  $BAE_TEST/au/net/zeus/jgdms/bae/AtomicSerialComplianceVisitorTest.java

java -cp "$PLATFORM_JAR:$ASM_JAR:$JUNIT_JAR:$HAMCREST_JAR:/tmp/bae-out" \
  org.junit.runner.JUnitCore \
  au.net.zeus.jgdms.bae.ClinitBlockingVisitorTest \
  au.net.zeus.jgdms.bae.AtomicSerialComplianceVisitorTest
# Expected: OK (43 tests)
```
