# SOW — Always-on, bootstrap-safe security logging for DirtyChai java.base

**Status:** Deferred (2026-06-27)
**Applies to:** DirtyChai (JDK fork) — `java.base` security/policy bootstrap code
**Authorship constraint:** DirtyChai source is advise-only under the OpenJDK Interim Policy
on Generative AI; any implementation is human-written. This SOW is an analysis/planning artifact.

---

## Objective

Give java.base bootstrap security code — `SpiffePolicyFile`, `RefreshingParserDecorator`,
`HttpsClientAuthPolicyParser`, `SpiffeCredentialManager` (all in
`au.zeus.jdk.authorization.{policy,spire}`) — an **always-on**, bootstrap-safe way to surface
operational events (policy fail-static fallback, SVID fetch failures, bootstrap milestones)
**without** requiring `-Djava.security.debug`.

---

## Background (established 2026-06-27)

- java.base **cannot** use `java.util.logging` — it lives in the `java.logging` module. This is
  why the security subsystem historically logs through `sun.security.util.Debug`, which is
  **gated**: `Debug.getInstance(option)` returns `null` unless `-Djava.security.debug` includes
  that option, so those messages are **silent in production**.
- `System.getLogger` / `java.lang.System.Logger` (JEP 264) **is** java.base-resident and
  bootstrap-safe:
  - `System.getLogger(name)` → `LazyLoggers.getLogger(name, callerModule)` → a lazy wrapper.
  - Pre-boot, `jdk.internal.logger.BootstrapLogger.getLogger(...)` returns a `BootstrapLogger`
    that **queues** events on a `LinkedBlockingQueue` (no `ServiceLoader`, no `LoggerFinder`
    resolution, no permission check, no throw) and **flushes** them after `VM.isBooted()`.
  - Post-boot, the wrapper resolves the real `LoggerFinder` under `AccessController.doPrivileged`
    (java.base context), so the `RuntimePermission("loggerFinder")` check does not depend on
    in-flux policy grants.
  - The default finder emits `WARNING`+ to `stderr` with no configuration.
- Net: `System.Logger` already provides the "queue during bootstrap, flush after boot"
  decoupling for the bootstrap window for free; JGDMS `org.apache.river.logging.LogDispatch`
  reinvented a slice of this (and is not portable into java.base — see Traps 3/4).

---

## Scope

**In scope**
- Standardize `System.Logger` for always-on `WARNING`+ security events in java.base bootstrap
  code, replacing/augmenting `Debug`-gated logging where always-on visibility is required.
- A usage matrix: `Debug` (gated verbose tracing) vs `System.Logger` (always-on operational
  WARNING+) vs thread hand-off (reentrancy-prone sites only).
- *Optional:* a bootstrap-safe, post-boot thread hand-off for log sites that are
  reentrancy/deadlock-prone (logging from inside a held lock or a permission-check critical path).

**Out of scope**
- Porting JGDMS `LogDispatch` as-is — it uses `java.util.logging`, a lambda, and a static
  virtual-thread executor; none are java.base/bootstrap-safe.
- Changing the logging backend.
- Audit-grade, guaranteed-delivery logging (see Trap 6/8).

---

## Traps / landmines

1. **Module boundary** — java.base code must use `System.Logger` only, never
   `java.util.logging.*` (`java.logging` module). This is the deeper reason `Debug` was used,
   independent of timing.
2. **`Debug` is gated/silent** — `Debug.getInstance(x)` is `null` without `-Djava.security.debug`
   including `x`. Unsuitable for must-see operational events.
3. **Virtual threads are post-boot only** — `VirtualThread` statically initializes a
   `ForkJoinPool` default scheduler and a `ContinuationScope`, and every start builds a
   `Continuation`. Do **not** initialize a virtual-thread executor in a `static` field of a class
   that loads during bootstrap (`RefreshingParserDecorator` loads during the initial parse) — it
   can drag scheduler/continuation init in too early. Create lazily and gate on `VM.isBooted()`.
4. **No lambdas / invokedynamic in bootstrap-reachable methods** — `parse()` runs during the
   initial bootstrap parse, so a `submit(() -> …)` there triggers `LambdaMetafactory` during
   bootstrap. Use anonymous `Runnable`/inner classes. (`LogDispatch`'s `submit(() -> logger.log)`
   is exactly this trap.)
5. **`System.getLogger` is `@CallerSensitive`** — throws `IllegalCallerException` only if there
   is no Java caller frame (e.g. a JNI/no-frame context). Fine from normal java.base code; don't
   call it from a frameless context.
6. **`BootstrapLogger` flush timing** — queued bootstrap events "may not flush until the next
   message is logged after boot" (activity-nudged), so the *last* buffered events can lag. Not an
   audit channel.
7. **Reentrancy** — a thread hand-off is needed only for sites that log from inside a held lock or
   a permission-check critical path. The fail-static fallback site is **not** one: pre-boot it
   enqueues; post-boot the finder resolution is `doPrivileged` and the call holds no policy lock
   (`ConcurrentPolicyFile` reads are lock-free; `grantArray` is not swapped until the parse
   completes).
8. **Hand-off loses events on JVM exit** — events queued but not yet emitted are lost if the JVM
   exits first. Acceptable for warnings; not for audit.
9. **`RuntimePermission("loggerFinder")`** on first finder resolution — wrapped in `doPrivileged`
   as java.base, so independent of in-flux policy grants; confirm java.base's domain is privileged
   in the bootstrap policy (normally is).

---

## Pros

- Operational visibility of bootstrap security events (policy fallback, SVID failures) **without**
  a debug flag → faster diagnosis of misconfiguration/outage.
- One consistent java.base-safe logging story for the security subsystem; less ad-hoc
  `System.err`/`Debug` sprinkling.
- Deferred-until-boot comes free via `BootstrapLogger`; minimal new infrastructure.
- An optional hand-off cleanly isolates the few reentrancy-prone sites.

## Cons / why deferred

- The immediate need (fail-static fallback warning visibility) is **already met** by
  `System.Logger` at that one site — no broad refactor is required now.
- A general hand-off helper adds complexity and a lost-on-exit failure mode for marginal benefit;
  most sites do not need it.
- Touches multiple HIGH-criticality java.base security classes → review cost and
  bootstrap-destabilization risk for a logging improvement.
- The `BootstrapLogger` flush-timing quirk means it is not a clean audit channel; audit-grade
  delivery is a separate, larger design.

---

## Recommended phasing (when undeferred)

- **P1 (small):** standardize `System.Logger` for always-on `WARNING`+ in java.base bootstrap
  security code; keep `Debug` for gated verbose tracing. *(The fail-static fix already does this
  at one site.)*
- **P2 (optional, medium):** a bootstrap-safe, post-boot, lambda-free, lazily-created thread
  hand-off **only** for audited reentrancy-prone sites; ship the use-it/don't matrix.
- **P3 (future):** evaluate **JFR events** for structured, always-on, low-overhead bootstrap
  security milestones (no logging reentrancy, post-boot).

---

## References

- `JGDMS-STD-003` §10; `docs/DESIGN-spiffe-authorization-acc-transmission-2026-06-27.md`
- DirtyChai: `jdk.internal.logger.BootstrapLogger`, `jdk.internal.logger.LazyLoggers`,
  `java.lang.System#getLogger`, `java.lang.VirtualThread`, `sun.security.util.Debug`
- JGDMS: `org.apache.river.logging.LogDispatch` (the non-portable reference pattern)
