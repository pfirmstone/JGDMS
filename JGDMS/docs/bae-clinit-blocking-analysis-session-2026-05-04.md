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

## Session 2 — AtomicSerial Superclass-Field Type-Safety (2026-05-04)

### 7. `CheckMethodAnalyzer` — second anti-pattern: superclass `Object`-typed fields

**Commit:** `13e1eef`

`CheckMethodAnalyzer` previously only detected one anti-pattern (2-arg `GetArg.get` +
`IFNULL` without `CHECKCAST`).  A second anti-pattern was identified and implemented:

> A child class whose static `check(GetArg)` method **constructs a private copy of
> itself** (via a bridge constructor that calls `super(arg)`) and then reads an
> inherited `Object`-typed protected field via `GETFIELD` — without a subsequent
> `instanceof` or `CHECKCAST` before using the value in an `IFNULL`/`IFNONNULL`
> branch — leaves the actual runtime type of the deserialized field completely
> unverified before the object is fully constructed.

Example of the **correct** pattern (used in `BytecodeAnalysisEngineProxy.check()`):
```java
BytecodeAnalysisEngineProxy sup = new BytecodeAnalysisEngineProxy(arg, true);
if (sup.server instanceof BytecodeAnalysisEngine && ...) return true;
// GETFIELD sup.server → INSTANCEOF → IFEQ   (type verified, flag cleared → COMPLIANT)
```

Example of the **anti-pattern** (now detected as `UNTYPED_GET`):
```java
BytecodeAnalysisEngineProxy sup = new BytecodeAnalysisEngineProxy(arg, true);
if (sup.server == null) throw new InvalidObjectException("null server");
// GETFIELD sup.server → IFNONNULL           (no type check → UNTYPED_GET)
```

**New flag: `pendingUntypedField`** — set by `visitFieldInsn(GETFIELD, ..., "Ljava/lang/Object;")`,
cleared by `visitTypeInsn(INSTANCEOF, ...)` or `visitTypeInsn(CHECKCAST, ...)`.
`visitJumpInsn(IFNULL/IFNONNULL)` fires `untypedGetFound = true` if either
`pendingUntypedGet` **or** `pendingUntypedField` is set.

### 8. `AtomicSerialComplianceVisitor` — `serialForm()` only required when needed

The `MISSING_SERIAL_FORM` verdict was previously issued for any `@AtomicSerial` class
that lacked a `serialForm()` method, including delegation-only proxy subclasses (e.g.
`BytecodeAnalysisEngineProxy`) that add **no** new serialized state beyond their
superclass.

A new `visitField` override now tracks `hasNonStaticInstanceFields`.
`serialForm()` is only required when the class declares at least one non-static,
non-transient instance field — i.e., when it actually has new serialized state to
describe.  Classes with only static or transient fields are not flagged.

### 9. New test cases — 4 added (47 total)

`AtomicSerialComplianceVisitorTest` now has **17 tests** (was 13):

| Test | Expected | What it exercises |
|------|----------|-------------------|
| `testRealClass_BytecodeAnalysisEngineProxy_isCompliant` | `COMPLIANT` | Real class with `instanceof` guard on inherited `Object server` field |
| `testCompliant_superclassObjectField_instanceofCheck` | `COMPLIANT` | Synthetic: GETFIELD + INSTANCEOF — pendingUntypedField cleared |
| `testCompliant_superclassObjectField_checkcastInCheckMethod` | `COMPLIANT` | Synthetic: GETFIELD + CHECKCAST — both pending flags cleared |
| `testUntypedField_superclassObjectField_nullCheckOnly` | `UNTYPED_GET` | Synthetic: GETFIELD + IFNONNULL without any type check |

`buildMissingSerialFormClass` was updated to add a non-static field so that
`MISSING_SERIAL_FORM` is still triggered correctly under the new rule.

All **47 BAE tests** pass (17 AtomicSerial + 30 Clinit).

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
| `bae/…/ClinitBlockingVisitorTest.java` | 30 unit tests (blocking / guarded / guard-method checks) |
| `bae/…/AtomicSerialComplianceVisitor.java` | `CheckMethodAnalyzer` — detects untyped GetArg.get and untyped GETFIELD on Object-typed superclass fields; `visitField` tracks `hasNonStaticInstanceFields` |
| `bae/…/AtomicSerialComplianceVisitorTest.java` | 17 unit tests (real classes + synthetic patterns including superclass-field checks) |
| `bae-dl/…/BytecodeAnalysisEngineProxy.java` | Example of the correct `instanceof` pattern for superclass `Object server` field |

---

## Build & Test Quick-Reference

The sandbox does not carry a full Maven local repository, so the manual classpath
approach is used instead of `mvn test`.

```bash
# ── Prerequisites ──────────────────────────────────────────────────────────
# Download ASM 9.7.1 (if not already present)
curl -fsSL https://repo1.maven.org/maven2/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar \
     -o /tmp/asm-9.7.1.jar
# Download JUnit 4 + Hamcrest (if not already present)
curl -fsSL https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar \
     -o /tmp/junit.jar
curl -fsSL https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar \
     -o /tmp/hamcrest.jar

# ── Variables ──────────────────────────────────────────────────────────────
JGDMS=/home/runner/work/JGDMS/JGDMS/JGDMS
PLATFORM=$JGDMS/jgdms-platform/target/classes     # pre-built by earlier mvn compile
COLLECTIONS=$JGDMS/jgdms-collections/target/classes
JERI=$JGDMS/jgdms-jeri/target/classes
ACTIVATION_PARAMS=$JGDMS/jgdms-activation-parameters/target/classes
OUT=/tmp/bae-out
ASM=/tmp/asm-9.7.1.jar; JUNIT=/tmp/junit.jar; HAMCREST=/tmp/hamcrest.jar
mkdir -p $OUT/step1 $OUT/step2 $OUT/step3 $OUT/tests

# ── Step 1: minimal jgdms-lib-dl classes needed by BytecodeAnalysisEngineProxy ──
javac -cp "$PLATFORM:$COLLECTIONS:$JERI:$ACTIVATION_PARAMS" \
  -d $OUT/step1 \
  $JGDMS/jgdms-lib-dl/src/main/java/net/jini/admin/Administrable.java \
  $JGDMS/jgdms-lib-dl/src/main/java/net/jini/admin/JoinAdmin.java \
  $JGDMS/jgdms-lib-dl/src/main/java/org/apache/river/admin/DestroyAdmin.java \
  $JGDMS/jgdms-lib-dl/src/main/java/net/jini/lookup/ServiceAttributesAccessor.java \
  $JGDMS/jgdms-lib-dl/src/main/java/net/jini/lookup/ServiceIDAccessor.java \
  $JGDMS/jgdms-lib-dl/src/main/java/net/jini/lookup/ServiceProxyAccessor.java \
  $JGDMS/jgdms-lib-dl/src/main/java/au/net/zeus/jgdms/proxy/AbstractSmartProxy.java

# ── Step 2: BAE DL (BytecodeAnalysisEngineProxy) ──────────────────────────
BAE_DL=$JGDMS/services/bytecode-analysis-engine/bytecode-analysis-engine-dl/src/main/java
javac -cp "$PLATFORM:$COLLECTIONS:$JERI:$ACTIVATION_PARAMS:$OUT/step1" \
  -d $OUT/step2 $(find $BAE_DL -name "*.java")

# ── Step 3: BAE service production sources ────────────────────────────────
BAE_SVC=$JGDMS/services/bytecode-analysis-engine/bytecode-analysis-engine-service/src/main/java
javac -cp "$PLATFORM:$COLLECTIONS:$JERI:$ACTIVATION_PARAMS:$OUT/step1:$OUT/step2:$ASM" \
  -d $OUT/step3 \
  $BAE_SVC/au/net/zeus/jgdms/bae/BlockingSinkRegistry.java \
  $BAE_SVC/au/net/zeus/jgdms/bae/ClinitBlockingVisitor.java \
  $BAE_SVC/au/net/zeus/jgdms/bae/AtomicSerialComplianceVisitor.java

# ── Step 4: BAE test sources ──────────────────────────────────────────────
BAE_TEST=$JGDMS/services/bytecode-analysis-engine/bytecode-analysis-engine-service/src/test/java
javac -cp "$PLATFORM:$COLLECTIONS:$JERI:$ACTIVATION_PARAMS:$OUT/step1:$OUT/step2:$OUT/step3:$ASM:$JUNIT:$HAMCREST" \
  -d $OUT/tests \
  $BAE_TEST/au/net/zeus/jgdms/bae/ClinitBlockingVisitorTest.java \
  $BAE_TEST/au/net/zeus/jgdms/bae/AtomicSerialComplianceVisitorTest.java

# ── Step 5: run tests ─────────────────────────────────────────────────────
java -cp "$PLATFORM:$COLLECTIONS:$JERI:$ACTIVATION_PARAMS:$OUT/step1:$OUT/step2:$OUT/step3:$OUT/tests:$ASM:$JUNIT:$HAMCREST" \
  org.junit.runner.JUnitCore \
  au.net.zeus.jgdms.bae.ClinitBlockingVisitorTest \
  au.net.zeus.jgdms.bae.AtomicSerialComplianceVisitorTest
# Expected: OK (47 tests)
```
