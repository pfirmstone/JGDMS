# Preferred-Class Analyzer

Build-time ASM bytecode analyzer that decides, objectively and repeatably, which
classes in a JGDMS module should be `Preferred: true` (isolate) vs
`Preferred: false` (share) in that module's `META-INF/PREFERRED.LIST`, replacing
the stale hand-maintained lists. See `docs/SOW-PreferredList-Analyzer.md`.

## Status

| Layer | State |
|-------|-------|
| ASM signal scanner, decision engine, override mechanism, `PREFERRED.LIST` parse/render/diff, report | **done** |
| `PreferredListTool` CLI (`generate` / `check` subcommands, runnable `Main-Class`) | **done** |
| Build integration — `check` runs in this module's `verify` and **fails on drift** | **done** |
| Unit fixtures + CLI tests + override mechanism + `jgdms-platform` ground-truth acceptance | **done — 43 tests green** |

Validated against the compiled `jgdms-platform`: it reproduces the 2026-06-17
audit ground truth and replaced the stale 879-line hand-maintained list (which
had drifted on 256 classes). The two flagged isolation hazards were then fixed at
the source: `ProxyTrustExporter` made lock-free with `java.lang.ref.Cleaner`, and
`UuidFactory`'s redundant lazy-init lock dropped (its shared `SecureRandom` kept
by design and recorded as a deliberate share in `jgdms-platform`'s
`META-INF/preferred-overrides.txt`); and `DelegationAbsoluteTime`'s `static
synchronized getFormatter()`/`SimpleDateFormat` replaced with a shared immutable
`DateTimeFormatter` (so it is no longer a CONFLICT — the CONFLICT bucket is now
empty). So the **emitted `PREFERRED.LIST` is now empty** (no `jgdms-platform`
class needs isolating), and the build enforces that with `check --overrides
--fail-on-drift`. Snapshot outputs and the overrides are in [`samples/`](samples)
and [the platform overrides file](../../jgdms-platform/src/main/resources/META-INF/preferred-overrides.txt).

## Decision model

A class is **cross-boundary** (must share, single `<C,L>` identity) if it is a
wire value type (`Serializable` / `@AtomicSerial`) or a bootstrap-proxy interface
(`ProxyAccessor` / `CodebaseAccessor` / `DynamicProxyCodebaseAccessor`, or a class
implementing one). Then:

- **CONFLICT** — cross-boundary **and** carries any criterion-(b) lock coupling:
  emitted `false`, reported as needing a lock-free remedy (e.g. `ThreadLocal`).
- **PREFER** — not cross-boundary and carries a *strong* criterion-(b) hazard:
  a static field of a contended/blocking type (`SecureRandom`, a `Lock`,
  `Semaphore`, a bounded/River `Executor`/pool, `BlockingQueue`, …) **or** a
  blocking call made under a static lock.
- **SHARE + review** — only a *weak* criterion-(b) hazard (a bare static lock
  guarding a read-mostly cache, e.g. `Security` / `ClassLoading` / `ServerContext`)
  or a criterion-(a) candidate (mutable static registry/singleton). Defaults to
  share; surfaced in the report so a human can override to prefer for a deliberate
  isolation decision.
- **SHARE** — everything else (instance-only state, benign read-mostly static:
  loggers, reflected `Method` caches, lazy SPI/format caches, constants — a
  `serialVersionUID` is just a constant and is **not** a hazard).

Key configurable sets (`AnalyzerConfig`): contended field types (incl. River's
`org.apache.river.thread.{Executor,ThreadPool,TaskManager,WakeupManager}`),
benign field types, mutable-container types, blocking-method catalog (a subset of
the bytecode-analysis-engine `BlockingSinkRegistry`), bootstrap interfaces, and
`preferOnCriterionA` (default off). A virtual-thread executor field
(`Executors.newVirtualThreadPerTaskExecutor()`) is excluded from the contended
set (no thread-starvation coupling; SOW §9.2).

## Override file (SOW §6)

`OverrideFile` parses `fully.qualified.Class = true|false  # reason` lines (a
checked-in file in the **analyzed** module) and applies them over the analysis,
reporting every override, every analysis-vs-override divergence, and any stale
entry that matched no analyzed class. This is the home for the human judgement
calls (chiefly cross-boundary determinations and deliberate SPI isolation).

## CLI

```
generate <classesDirOrJar> [--out <file>] [--report <file>] [--overrides <file>]
                           [--default-prefer] [--prefer-on-a]
check    <classesDirOrJar> --list <PREFERRED.LIST> [--report <file>]
                           [--overrides <file>] [--prefer-on-a] [--fail-on-drift]
```

`check` exits 0 (report-only) unless `--fail-on-drift` is given, then 1 on drift.
A missing classes path is a graceful skip (exit 0). Run via the built jar (`asm`
on the classpath) or `org.apache.river.tool.preferred.PreferredListTool`.

## Build integration

`mvn -pl tools/preferred-list-analyzer verify` runs `check --fail-on-drift`
against the compiled `jgdms-platform` (this module builds after it in the reactor)
and writes the report to `target/preferred-list/`. **The build fails if the
checked-in list drifts from the analysis**, so the derived list cannot silently
rot. If the target module is not compiled the tool skips gracefully.

After an intended change to the module that alters classification, regenerate the
live list and commit it:

```
java ... org.apache.river.tool.preferred.PreferredListTool generate \
     ../../jgdms-platform/target/classes \
     --out ../../jgdms-platform/src/main/resources/META-INF/PREFERRED.LIST
```

Skip the gate with `-Dpreferred.check.skip=true`; retarget another module with
`-Dpreferred.target.classes=...` / `-Dpreferred.target.list=...`.

## Limitations / follow-ups

- **Cross-boundary detection resolves supertypes only within the analyzed
  module.** A class that is `Serializable` solely via a base in another module
  (e.g. `java.security.Permission`, `Throwable`, `EventObject`) is not seen as a
  wire type. As a safety net, a class that would otherwise PREFER on a strong (b)
  hazard but whose superclass chain leaves the analyzed set is **downgraded to
  SHARE + review** (with a note) rather than silently preferred — preferring a
  hidden wire type would risk a `ClassCastException` across the loader divide.
  The fuller fix (resolve supertypes on the classpath, or seed known Serializable
  bases) is warranted **before pointing the tool at the `-dl`/proxy modules**,
  where proxy classes routinely extend Serializable bases.
- **Contended/blocking field types are matched exactly** against the configurable
  set; a static field declared as a custom subtype of a lock/pool not in the set
  is missed. Add it to `AnalyzerConfig`, or use the override file.

## Programmatic use

```java
List<ClassDecision> decisions =
        new PreferredAnalyzer().analyzeDirectory(new File("target/classes"));
String listText = PreferredList.render(decisions);                 // generate
List<PreferredList.Drift> drift =
        PreferredList.parse(existing).diff(decisions);             // check
String report = AnalysisReport.render(decisions, /*overrides*/ null);
```
