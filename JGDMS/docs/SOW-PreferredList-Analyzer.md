# SOW — Preferred-Class Analyzer (Maven build tool)

*Scope for a fresh agent. Self-contained. Filed 2026-06-17.*

## 1. Purpose

Determine, **objectively and repeatably at build time**, which classes in a JGDMS module
should be marked `Preferred: true` vs `Preferred: false` in that module's
`META-INF/PREFERRED.LIST`, replacing today's stale, hand-maintained lists. Ship it as a
**Maven tool** developers run during their build (and that we run to regenerate/lint the
checked-in lists).

**Background on "preferred".** The Jini preferred-class mechanism
(`net.jini.loader.pref.PreferredClassProvider` / `PreferredResources`; see Warres,
*Class Loading Issues in Java RMI and Jini*, SMLI TR-2006-149, PDF at
`JGDMS/jgdms-pref-class-loader/src/main/javadoc/smli_tr-2006-149.pdf`, §4.5/§5) controls
codebase download: a **preferred** class is loaded from the downloaded codebase **in
preference to** a same-named local class — i.e. a downloaded proxy gets its **own** copy
(separate type identity, separate static state, separate locks). A **non-preferred** class
is shared with the client (one copy). Preferred is only relevant when the jar is served as
a codebase.

**Why this is subtle (and why a tool).** The mechanism is mostly vestigial, so the bias is
**share by default**; but a few classes genuinely must be isolated, and a few must never be.
A manual audit of `jgdms-platform` (2026-06-17) established the criteria below and the
ground-truth cases in §8. Hand analysis is error-prone — e.g. a regex scan false-flagged
every class with `private final static long serialVersionUID` (the `final static` token
order) and missed `static final` locks/pools entirely. **Use ASM bytecode analysis, not
source regex.**

## 2. Classification criteria (the decision the tool must make)

For each class, choose `true` (prefer/isolate) or `false` (share):

**A class MUST be shared (`false`) — overrides everything — if it is a cross-boundary type**,
because a downloaded preferred copy would be a distinct `<C,L>` type and break with a
`ClassCastException` across the loader divide (Warres §4.1). Signals: it is a wire value type
(`Serializable` / `@AtomicSerial`), a public API type that appears in proxy/remote method
signatures, or one of the bootstrap-proxy interfaces (`net.jini.export.ProxyAccessor`,
`CodebaseAccessor`, `DynamicProxyCodebaseAccessor` — cast on both sides of the bootstrap path).

**Otherwise a class SHOULD be preferred (`true`) only if it carries a real isolation hazard
in shared static state** — one of:
- **(a) Semantic co-mingling.** Mutable static state whose contents are per-deployment
  meaningful and must not be shared between the client and a downloaded proxy (a registry,
  a counter, a mutable singleton with identity). *(In `jgdms-platform` this came up ~empty.)*
- **(b) Lock / blocking coupling (the important one).** Sharing a class shares its monitors,
  locks, and scarce resources, so a downloaded proxy's liveness/throughput becomes coupled to
  the client's. Signals: a `synchronized` **static** method; a `synchronized(...)` block on a
  **static** field; a static field of a contended/blocking type (`SecureRandom`, `Lock`/
  `ReentrantLock`/`ReadWriteLock`, `Semaphore`, bounded `Executor`/`ThreadPoolExecutor`,
  `BlockingQueue`, `CountDownLatch`, `Phaser`, `Condition`), **especially** a blocking call
  made while holding a static lock.

**Everything else shares** (`false`): instance-only state, and benign read-mostly static
(loggers, lazily-cached reflected `Method`s, lazy SPI/format caches, constants).

**The conflict bucket.** A class that is **must-share (cross-boundary) AND has a contention
hazard** cannot be fixed by preferring (it must share). The tool must **not** prefer it; it
must **report it** as needing a lock-free remedy (e.g. `ThreadLocal`), as a separate follow-up.

## 3. What the tool can and cannot decide

- **Objective, automatable (the tool's core value):** the isolation **hazards** of §2(a)/(b)
  — purely local bytecode facts. The tool finds the small candidate set with evidence, so
  humans stop auditing hundreds of classes by hand.
- **Heuristic / needs human confirmation:** "cross-boundary / must-share." Precise detection
  needs whole-program reachability. Use heuristics (`Serializable`/`@AtomicSerial`, public type
  in an exported API package, known bootstrap interfaces) **plus an override file** (§6). When
  uncertain, **default to share** and surface the class for review — never silently prefer.

So the emitted decision per class:
1. hazard present AND not-cross-boundary → `true` (with evidence);
2. hazard present AND cross-boundary → `false` + **CONFLICT report** (lock-free TODO);
3. no hazard → `false`.

## 4. Detection signals to compute per class (via ASM)

- Static fields: name, type, `final`?, mutable-container? (`Map`/`Collection`/array/known
  mutable), written outside `<clinit>`?
- Methods with `ACC_STATIC | ACC_SYNCHRONIZED`.
- `monitorenter`/`monitorexit` whose operand traces to a `GETSTATIC`.
- Static fields whose type ∈ the contended/blocking-resource set (configurable list).
- Blocking-call-under-static-lock: an `INVOKE*` to a blocking method (e.g. `SecureRandom`,
  `BlockingQueue.take`, `Lock.lock`, `Future.get`) on a code path holding a static monitor.
- Cross-boundary heuristics: `implements Serializable`, `@AtomicSerial`, package exported,
  name ∈ bootstrap-interface set.
- Inner classes inherit/are analyzed with their enclosing class (current lists are per-inner).

Reuse the ASM infrastructure already in the repo
(`JGDMS/services/bytecode-analysis-engine/...`, the dangerous-pattern engine) rather than a
new ASM setup.

## 5. Tool form & Maven integration

A **Maven plugin** (packaging `maven-plugin`), recommended at `tools/preferred-list-maven-plugin`,
with two goals:
- **`generate`** — analyze `${project.build.outputDirectory}`, write/refresh
  `META-INF/PREFERRED.LIST` (global default `Preferred: false`; explicit `true` only for the
  prefer set; keep the `PreferredResources-Version` header and wildcard form where it reduces
  noise).
- **`check`** (bind to `verify`) — parse the checked-in list, recompute, **fail the build on
  drift** (a linter, so lists can't silently rot). Emit the same report.

**Override file** (checked-in, e.g. `src/main/resources/META-INF/preferred-overrides.txt` or a
plugin-config block): `fully.qualified.Class = true|false  # reason`. The tool applies overrides
over its analysis, **reports every override and every analysis-vs-override divergence**, and the
override is the home for the human judgment calls (the cross-boundary determinations).

## 6. Output

1. The `PREFERRED.LIST` (generate) or a pass/fail diff (check).
2. A **human-readable report**: per preferred class, the evidence (which static field / lock /
   blocking resource); the CONFLICT list (must-share-yet-contends → lock-free TODO); the
   overrides applied; the uncertain/needs-review set. This report is the artifact a security
   reviewer reads — make the *why* legible.

## 7. Scope

**In:** the analyzer, the §2 decision logic, the Maven plugin (`generate` + `check`), the
override mechanism, the report, and validation against `jgdms-platform` (§8).
**Out (follow-ups):** actually applying lock-free fixes to the conflict classes; rolling the
tool across every module (start with `jgdms-platform`, then `-dl` modules); any change to the
preferred-class runtime mechanism itself.

## 8. Acceptance tests (ground truth from the 2026-06-17 manual audit)

The tool, run on `jgdms-platform`, must reproduce these:
- **PREFER (isolate):** `net/jini/id/UuidFactory` — `synchronized(lock){ secureRandom.nextLong() }`
  serializes all UUID generation and can block on entropy ⇒ a shared copy couples proxy↔client
  (criterion (b)). `net/jini/security/proxytrust/ProxyTrustExporter` — `static final Executor
  systemThreadPool` (shared pool) ⇒ prefer candidate **iff** proxy-reachable.
- **SHARE:** the `org/apache/river/api/io/*Serializer` classes (only `final static
  serialVersionUID` — no hazard); `net/jini/export/{ProxyAccessor,CodebaseAccessor,
  DynamicProxyCodebaseAccessor}` (bootstrap interfaces — type identity); cross-boundary value
  types `net/jini/io/MarshalledInstance`, `net/jini/core/event/EventRegistration`,
  `net/jini/discovery/RemoteDiscoveryEvent`; benign-cache holders (`net/jini/security/Security`
  loggers, `ProxyTrustVerifier`/`Valid` reflected-`Method` caches, `ClassLoading`/`ServerContext`
  lazy SPI caches — unless a deliberate SPI-isolation decision says otherwise, via override).
- **CONFLICT (must-share, lock-free TODO — do NOT prefer):** `net/jini/core/constraint/
  DelegationAbsoluteTime` — `private static synchronized SimpleDateFormat getFormatter()` on a
  cross-boundary constraint type.

Plus unit fixtures: one class per hazard kind (non-final static, static-final mutable map,
static synchronized method, synchronized-on-static block, `SecureRandom` field, bounded-pool
field, blocking-call-under-static-lock) and per share kind (instance-only, `serialVersionUID`-
only, `Serializable` cross-boundary, bootstrap interface).

## 9. Open decisions for the implementer (surface, don't guess)

1. Does criterion (b) include **`static final`** locks/pools? (Yes — they are shared state; the
   audit's strongest case, `ProxyTrustExporter`, is `static final`. Non-final is *not* the line.)
2. Virtual-thread executors (`newVirtualThreadPerTaskExecutor`) — treat as **non**-blocking
   (no thread-starvation) ⇒ not a hazard. Confirm.
3. Wildcard vs per-class output granularity; how inner classes are emitted.
4. Where the override file lives and its precedence/reporting.
5. `generate` (regenerate) vs `check`-only first; fail-build vs warn for `check`.

## 10. References
- Warres, SMLI TR-2006-149 (preferred classes), in `jgdms-pref-class-loader/src/main/javadoc/`.
- Runtime mechanism: `net.jini.loader.pref.PreferredResources` / `PreferredClassProvider`.
- ASM infra to reuse: `JGDMS/services/bytecode-analysis-engine/`.
- Current example list: `JGDMS/jgdms-platform/src/main/resources/META-INF/PREFERRED.LIST`.
- Memory: `jgdms-dl-jars-not-osgi-bundles`, `jgdms-securitymanager-removal` (the audit context).
