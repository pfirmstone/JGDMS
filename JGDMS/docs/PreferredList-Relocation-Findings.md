# Preferred-Class Analyzer — platform findings & relocation analysis

*Filed 2026-06-18. Follow-up to `SOW-PreferredList-Analyzer.md` and the
`tools/preferred-list-analyzer` build (branch `preferred-list-analyzer`).*

## 1. Key finding: jgdms-platform needs no PREFERRED.LIST

The preferred-class mechanism only takes effect when a jar is **served as a
downloaded codebase** (Warres SMLI TR-2006-149 §4.5). In the dist/qa,
`jgdms-platform` is a **local/shared classpath jar**: it is referenced only by
`file:` codebase grants (e.g. `qa/harness/policy/*.policy`), never `httpmd`-served
for download. So its `META-INF/PREFERRED.LIST` is **never consulted at runtime** —
flipping it to share-by-default is inert, and qa cannot exercise it (a run would
be pure DigestGrant/mahalo noise, see `jgdms-qa-digestgrant-bootstrap-gate`).

Consequence: the committed share-by-default flip + `--fail-on-drift` gate on
`jgdms-platform` are harmless but cosmetic. The analyzer's real value is:
1. **identifying genuine isolation/contention hazards** (it did — see §2); and
2. the **`-dl` modules**, whose jars *are* served as codebases, where the list
   actually governs download behaviour. The `--fail-on-drift` gate and finding
   #1's classpath cross-boundary resolution matter there, not here.

## 2. Hazard classes and the relocation remedy

For a class that genuinely needs **per-codebase isolation**, the architecturally
correct fix is not `Preferred: true` in a local jar (inert) but to place it in a
**served** jar (`jgdms-lib-dl`), where each downloading codebase gets its own
copy. A class can move downstream to `-dl` only if nothing in `jgdms-platform`
depends on it (an incoming dep would force a platform→-dl build edge, the wrong
direction).

Incoming intra-`platform` dependencies (`jdeps -verbose:class -filter:none`,
excluding a class's own inner classes):

| Class | Hazard (analyzer verdict) | Incoming platform deps | Relocation verdict |
|---|---|---|---|
| `net.jini.security.proxytrust.ProxyTrustExporter` | ~~static `Executor` pool + mutable statics (PREFER)~~ **RESOLVED 2026-06-18** | **0** | **fixed lock-free** (see below) — now SHARE, no relocation needed |
| `net.jini.id.UuidFactory` | ~~`synchronized(lock)` + `SecureRandom` (PREFER)~~ **RESOLVED 2026-06-18** | 1 — `ConfigUtil` (`create(String)` only) | **lock dropped; shared `SecureRandom` kept by design; deliberate-share override** (§2b) |
| `net.jini.core.constraint.DelegationAbsoluteTime` | `static synchronized getFormatter()` (**CONFLICT**, `@AtomicSerial` wire type) | 3 (`ConstraintTrustVerifier`, `DelegationRelativeTime`, `Plaintext`) | **stays** — must keep wire identity; remedy is lock-free (`ThreadLocal<SimpleDateFormat>`) |
| `net.jini.export.ServerContext`, `net.jini.security.policy.PolicyFileProvider`, `org.apache.river.api.security.DelegatePermission` | benign bare-lock cache / (a) (**review**) | 0 | movable but **not motivated** (no real isolation hazard) |
| `Constants`(8), `ClassLoading`(5), `Security`(15), `Service`(9), `DiscoveryV2`(1), `ObjectStreamClassContainer`(3), `OSGiServiceIterator`(1) | review | ≥1 | stay (core infra) |

## 2a. ProxyTrustExporter — resolved lock-free (2026-06-18)

The topology question below was answered "lock-free, not relocation":
`ProxyTrustExporter`'s hazard was a hand-rolled
`WeakReference`/`ReferenceQueue`/reaper-thread auto-unexport cleaner, whose
`synchronized(refs)` guarded the reaper start-on-first / stop-on-empty lifecycle
against a shared static thread pool. It was replaced with
`java.lang.ref.Cleaner` (platform compiles at release 21) — `Cleaner` owns the
ReferenceQueue, the daemon thread, and the strong-reachability of registrations,
so all four static hazard fields (`systemThreadPool`, `refs`, `queue`, `reaper`)
and the `synchronized` block were deleted. A `BootHolder` cleaning action keeps
the bootstrap impl reachable while the main object is, releasing it on GC; it
holds the bootstrap impl (whose back-reference to the main object is weak) and so
never pins the registered object. The analyzer now classifies it **SHARE** (no
hazard) — the hazard is dissolved, not relocated. It stays in `jgdms-platform`.

## 2b. UuidFactory — lock dropped, recorded as a deliberate share (2026-06-18)

`UuidFactory.generate()`'s only hazard left was a shared static `SecureRandom`
guarded by a lazy-init lock (the `nextLong()` calls were already outside the
lock). The lock was redundant, so it was dropped — `secureRandom` is now an eager
`private static final SecureRandom`. The generator is **kept shared on purpose**:
that is the correct design for a virtual-thread system. `SecureRandom` is
thread-safe and, on JDK 24+ (JEP 491), its internal lock no longer pins virtual
threads; a per-thread `ThreadLocal<SecureRandom>` would explode to one instance
per virtual thread; `ThreadLocalRandom` is not cryptographically secure (and
these `Uuid`s must be unguessable); and `ScopedValue` doesn't fit a leaf utility
with no enclosing binding scope.

The analyzer still flags the shared `SecureRandom` field conservatively
(`CONTENDED_STATIC_FIELD` → PREFER). That is precisely an override case, so it is
recorded in `jgdms-platform/src/main/resources/META-INF/preferred-overrides.txt`
(`net.jini.id.UuidFactory = false`). The build gate runs `check --overrides
--fail-on-drift`, so it enforces end-to-end that the override demotes it.

**End state:** with both `ProxyTrustExporter` (lock-free) and `UuidFactory`
(deliberate share) resolved, no class in `jgdms-platform` is preferred — the
emitted `PREFERRED.LIST` is empty (header + `Preferred: false`), confirming
platform needs no preferred classes. A refinement for the `-dl` rollout: "static
`SecureRandom` field ⇒ prefer" is override territory in a vthread world, not an
automatic prefer.

## 4. Recommended next steps

1. **`ProxyTrustExporter`**: ~~confirm topology / relocate~~ **done** — fixed
   lock-free with `java.lang.ref.Cleaner` (§2a); now SHARE, stays in platform.
2. **`UuidFactory`**: ~~assess / move~~ **done** — lock dropped, shared
   `SecureRandom` kept by design, recorded as a deliberate-share override (§2b).
   Platform's emitted `PREFERRED.LIST` is now empty.
3. **Platform list**: now empty and override-backed; the gate enforces it. Could
   still be deleted entirely once the tool is also pointed at the `-dl` modules.
4. **`DelegationAbsoluteTime`**: apply the lock-free `getFormatter()` fix
   (`ThreadLocal<SimpleDateFormat>`; SOW §2 conflict bucket) — the last open
   platform hazard, a CONFLICT (cross-boundary wire type, must stay shared).
5. **Roll the analyzer across the `-dl` modules** (SOW §7) with finding #1's
   fuller cross-boundary resolution (classpath supertype lookup) in place, and
   treat a shared `SecureRandom` field as override territory, not auto-prefer.
