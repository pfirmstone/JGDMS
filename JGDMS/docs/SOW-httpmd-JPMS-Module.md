# SOW — httpmd as a JPMS Module

*Prerequisite/consumer relationship with
[SOW-Role-Neutral-Worker-Bootstrap.md](SOW-Role-Neutral-Worker-Bootstrap.md) §4, and
context in [DESIGN-attested-role-neutral-worker.md](DESIGN-attested-role-neutral-worker.md).
Status: SOW / not started.*

## 1. Problem

`jgdms-url-integrity` (the `net.jini.url.{file,httpmd,https}` URL providers, incl. the
content-integrity httpmd handler) is today a plain jar with a bnd/OSGi manifest and **no
`module-info`**; its handlers are discovered via the legacy
`-Djava.protocol.handler.pkgs=net.jini.url` package-prefix mechanism. We want httpmd to
be an explicit **JPMS module** with **JPMS-native handler registration**
(`java.net.spi.URLStreamHandlerProvider`), so it can be resolved into the static-root
module layer (the role-neutral-worker bootstrap), off the deprecated `handler.pkgs`
mechanism, with proper encapsulation for a TCB-adjacent integrity verifier. Valuable on
its own for any modern/modular JGDMS deployment.

## 2. Scope / deliverables

1. A `URLStreamHandlerProvider` (one provider covering the schemes this jar owns —
   `httpmd`, `https`, `file` — returning the existing `Handler`s by scheme, `null`
   otherwise), registered via **both** `provides java.net.spi.URLStreamHandlerProvider
   with …` in `module-info` **and** `META-INF/services/java.net.spi.URLStreamHandlerProvider`
   (so it also works on the classpath via `ServiceLoader`). Retire reliance on
   `handler.pkgs`.
2. A `module-info.java` for the jar (module name **`au.net.zeus.jgdms.url.integrity`**).
3. **Non-breaking:** `module-info` is ignored on the classpath and coexists with the
   bnd/OSGi manifest, so existing classpath/OSGi deployments are unaffected.

## 3. The crux — self-containment vs. the one split package (AUDIT 2026-07-02)

httpmd's own packages `net.jini.url.{file,httpmd,https}` are clean (single-owner, no
split). The problem is its dependencies. Audit result:

- httpmd's *only* couplings into `jgdms-platform` are:
  - `net.jini.security.IntegrityVerifier` — a **clean SPI interface** (sole import
    `java.net.URL`); `HttpmdIntegrityVerifier implements` it.
  - `net.jini.security.Security` — used **only** for `Security.doPrivileged(...)` in
    `HttpmdUtil` (the `verifyCodebaseIntegrity` mention is javadoc — httpmd *is* the
    verifier, not its caller).
  - `org.apache.river.logging.{LogManager, Levels}` — trivial.
- **`net.jini.security.Security` imports the split package `org.apache.river.api.security`**
  (`PermissionGrant`, `PermissionGrantBuilder`, `RevocablePolicy`, `SubjectDomain`, …),
  so it is **not** cleanly extractable, and `jgdms-platform` (which owns both
  `net.jini.security` and the split `org.apache.river.api.security`) is **not
  modularisable** — a module cannot `requires jgdms-platform` on the module path without
  dragging the split package into a collision with `java.base`.
- Constraint: `net.jini.security` **must not itself become split** (only
  `org.apache.river.api.security` may be split — the migration-compat bridge). So we
  cannot simply lift `IntegrityVerifier` into a new module and leave `Security` behind.

**Two resolutions, decide at P0:**

- **(A) Full clean module (stretch).** Remove httpmd's `Security` use (swap
  `Security.doPrivileged` → the `java.base`/DirtyChai SM-aware `doPrivileged`), then move
  the **whole** `net.jini.security` package into a split-free module that `jgdms-platform`
  also depends on — but this only resolves cleanly if that module's own dependence on
  `org.apache.river.api.security` (via `Security`) is satisfied from `java.base` on
  DirtyChai and is acceptable off-DirtyChai. This is a `jgdms-platform` refactor with real
  scope; do it only if the payoff (a pure module-path static root) justifies it.
- **(B) Hybrid (recommended pragmatic path).** Ship the `module-info` +
  `URLStreamHandlerProvider` (items §2 — the immediately-valuable, non-breaking win), but
  in the worker static root resolve httpmd **alongside `jgdms-platform` on the classpath**
  (system loader), not as a pure module-path module. **Non-overridability still holds**:
  downloaded code loads into child/codebase classloaders, and parent-first delegation
  makes the system-loader httpmd/verifier win regardless. This sidesteps the
  split-package-on-module-path problem entirely while still getting off `handler.pkgs`
  and gaining `URLStreamHandlerProvider`. The bootstrap SOW §4's "resolved module"
  launch would then apply to the bootstrap module, with httpmd on the app classpath.

Either way: **swap `Security.doPrivileged` for the `java.base` `doPrivileged`** (httpmd
does not need the `Security` facade), which removes the deepest coupling regardless of
A/B.

## 4. Module name / packages / exports

Packages `net.jini.url.{file,httpmd,https}` (verified clean). Module
`au.net.zeus.jgdms.url.integrity`. Exports: likely **none** required publicly — the
handlers are reached through the URL machinery + the provider, not imported directly;
export a package only if a consumer imports its types. `opens` nothing.

## 5. Verification / acceptance

- An `httpmd://…;sha-256=…` URL resolves and content-verifies **with no
  `-Djava.protocol.handler.pkgs` set**, via the `URLStreamHandlerProvider`, on the
  classpath (ServiceLoader) — and, under resolution (A), as a resolved module with **no
  split-package error on DirtyChai**.
- The `Security.doPrivileged` → `java.base` `doPrivileged` swap preserves behaviour under
  the DirtyChai SecurityManager (privileged-action semantics unchanged).
- Existing `jgdms-url-integrity` tests green; classpath and OSGi deployments unaffected.
- No public API change beyond the added provider (api-compat gate clean).

## 6. Phasing

- **P0** — confirm the audit; choose **A vs B**; verify the `doPrivileged` swap is
  semantically safe under DirtyChai's SM.
- **P1** — add the `URLStreamHandlerProvider` + `META-INF/services`; retire the
  `handler.pkgs` reliance; classpath tests green (works both ways during transition).
- **P2** — swap `Security.doPrivileged` → `java.base` `doPrivileged`; drop the `Security`
  import.
- **P3** — add `module-info` (`au.net.zeus.jgdms.url.integrity`); verify classpath use
  unaffected. If **(A)**: perform the `net.jini.security` relocation and verify clean
  module-path resolution on DirtyChai. If **(B)**: document the classpath placement in
  the bootstrap static root.
- **P4** — hand off to the bootstrap static-root wiring (bootstrap SOW §4).

## 7. Caveats / non-goals

- Does **not** modularise `jgdms-platform` or the rest of JGDMS — blocked by the
  deliberate `org.apache.river.api.security` compat split; those stay non-JPMS and load
  in the unnamed module.
- Must not create a **new** split package (`net.jini.security` stays single-owner).
- Fully non-breaking for existing classpath/OSGi consumers.

## 8. Relationship / dependencies

Consumer: [SOW-Role-Neutral-Worker-Bootstrap.md](SOW-Role-Neutral-Worker-Bootstrap.md)
§4 (needs httpmd resolvable in the static root). No DirtyChai source change required —
httpmd stays a JGDMS artifact; it merely *uses* the `java.base` `doPrivileged` and
(under A on DirtyChai) sources `org.apache.river.api.security` from `java.base`.
