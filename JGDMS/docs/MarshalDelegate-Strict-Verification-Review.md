# Code Review + Strict-Mode Verification — @AtomicSerial MarshalDelegate (re-review)

**Scope:** the updated `feature/atomicserial-marshal-delegate` branch — reviewed/tested at tip
`32947bdb4` (everything after the Phase A review doc `5d02e126d`). **Re-checked at `68dc29b40`** (2
commits later — `BasicMethodConstraints.MethodDesc` and fiddler `RegistrationInfo` completed): the
`org.apache.river.api.io` gap below is **still open** there. Companion to
`MarshalDelegate-PhaseA-Review.md`.
**Date:** 2026-06-23. **Reviewer:** independent (Opus 4.8).

---

## Verdict

The code is **approved** — the Phase A must-fix is resolved and the branch went well beyond it
(Phase B processor + a broad contract sweep), and a normal-mode build is green. **But strict mode is
NOT green:** an independent strict-mode qa run surfaced one concrete, foundational gap — the platform
package `org.apache.river.api.io` has no `MarshalDelegate` for its built-in serializer helpers, so
strict marshalling fails on the first `Map`/collection/throwable in any object graph. That gap is the
#1 remaining task to reach the strict-green acceptance.

> **UPDATE 2026-06-24 — the gap is RESOLVED.** Verified at tip `d28afdf26` (branch has since continued).
> The agent enabled MarshalDelegate **generation for jgdms-platform** (`026f66e41`), producing
> `org.apache.river.api.io.GeneratedMarshalDelegate` (+ `net.jini.id` / `net.jini.security` /
> `net.jini.core.constraint` / `org.apache.river.discovery` / `org.apache.river.logging`), registered in
> `META-INF/services`, guarded by a regression test (`GeneratedMarshalDelegateTest`, 4/4 — it cites
> "the review's exact failure case"). The setAccessible fallback is **removed entirely** (`f1fe9d21b`):
> the delegate path is now the only path, so there is no longer a "strict" toggle. My two minor notes
> were also addressed (dead code `c1fa42d25`; `@AtomicExternal` documented as JOSS-only/outside the model
> `a5e3527ab`), and the `@ReadInput`/`getReader()` back-door was removed (`ad25b4e67`).
>
> **Independent verification (delegate-only dist, Zulu-21 build):** green — **platform 301 · der 388 ·
> jeri 96**, BUILD SUCCESS, 0 fail; the io `GeneratedMarshalDelegate` is in the platform jar and
> registered; and qa runs of mercury/norm show **0 `MapSerializer$Ent` hits** — the block this review
> reported is gone.
>
> **New remaining blocker for the qa end-to-end pass (separate from the delegate work).** mercury
> `MercuryProxyEqualityTest` and norm `renewalservice/EqualsTest` now get *past* marshalling but fail in
> the **harness itself**: `ClassNotFoundException: net.jini.activation.arg.MarshalledObject`, thrown by the
> harness master VM while atomic-marshalling the test object to the child VM
> (`MasterHarness.runTestOtherVM:893` → `AtomicMarshalOutputStream` → `ObjOutputStream.writeNewClassDesc`),
> then a hang to the timeout. The class **exists** in `jgdms-activation-parameters.jar` and **is on the
> harness classpath**, so this is a classloader-resolution issue during class-descriptor writing — not a
> missing jar — and it coincides with the in-flight `MarshalledObject`→`MarshalledInstance` migration.
> Strong suspicion: the **qa harness jars are stale** vs the new API (the qa ant build is migrated
> separately and lags). **To get real mercury/norm/mahalo pass-fail:** rebuild the qa harness against the
> new dist, then re-run. Until then the delegate-only qa pass is unconfirmed end-to-end (the unit-level
> `GeneratedMarshalDelegateTest` does cover the io delegate).

---

## What is correct (code review)

- **Mahalo must-fix (`dea34317e`).** `MahaloProxyMarshalDelegate` serves all five package classes;
  the public `TxnMgrProxy`/`TxnMgrAdminProxy` (package-private ctors) and the two `@Stateless`
  constrainable leaves all dispatch in-package; leaves get empty serialForm + no-op serialize but still
  `create`. Resolves the Phase A gap exactly.
- **`serves()` scoping (`c4de8b1d3`).** Replacing the resolver's `packageName()` match with explicit
  `servedClasses()` / `serves(Class)` (identity `==`) is sound *and an improvement*: same `Class`
  object ⇒ same defining loader, so the runtime-package rule is preserved **and tightened**, while
  mixed/partially-migrated packages are unblocked (public/incomplete classes fall back to reflection
  instead of being forced onto the delegate). Loader pre-filter correctly retained for the OSGi
  registry-wide path. Note: `packageName()` is now dead for resolution — keep for diagnostics or drop.
- **Phase B processor (`13017a289` + `138f6ae8a` + `1a227f6f6`).** `MarshalDelegateProcessor`
  *dispatches* to each class's author-written `serialForm()`/`serialize()`/`(GetArg)` ctor — it never
  generates the authored contract and never touches fields; excludes private nested classes;
  `handWrittenDelegateExists()` lets hand-written delegates win; `validate()` always runs. Wired
  reactor-first as a non-failing `-Amarshaldelegate.validateOnly=true` warning gate — the right call
  for an in-progress migration (full-reactor sweep = 37 warnings = the worklist).
- **Contract completions** (reggie/mercury/outrigger/mahalo/norm/lookup.util) spot-checked: notably
  mercury `EventID`→`AtomicMarshalledInstance` (preserves DER `schemaBytes`/`schemaDigest`;
  `MarshalledObject` would silently drop them — a sharp catch), lib-dl `ConstrainableAdminProxy`
  `@Stateless` (constraints re-derived from `server.getConstraints()`), outrigger `EntryRep` 7-field
  wire contract. All correct.

**Independent normal-mode build (Zulu 21, processor installed first):**
`mvn -o -pl jgdms-platform,jgdms-der,jgdms-jeri -am test` → **platform 297 · der 388 · jeri 96, BUILD
SUCCESS, 0 fail/err.** The gate also correctly flagged a genuine half-migrated class
(`net.jini.constraint.BasicMethodConstraints`) and an intentional test fixture
(`der SerializeEnforcementTest`).

---

## The gap (must fix for strict-green): `org.apache.river.api.io` has no delegate

A strict-mode qa run of **mercury `MercuryProxyEqualityTest`** and **norm
`renewalservice/EqualsTest`** (DirtyChai JDK, fresh dist) **both fail identically**:

```
java.io.InvalidClassException: org.apache.river.api.io.MapSerializer$Ent;
strict MarshalDelegate mode: (GetArg) constructor of @AtomicSerial class
org.apache.river.api.io.MapSerializer$Ent is not reachable without setAccessible,
and no MarshalDelegate is registered for package org.apache.river.api.io;
add a MarshalDelegate for this package (or make the class and member public).
```

The mercury/norm **proxy delegates resolve fine** — the blocker is the foundational platform package
`org.apache.river.api.io`, whose built-in `@AtomicSerial` serializer helpers have **no delegate**:

> `MapSerializer` (+ package-private `Ent`), `ListSerializer`, `SetSerializer`, `FileSerializer`,
> `ProxySerializer`, `PermissionSerializer`, `PropertiesSerializer`, `URISerializer`, `URLSerializer`,
> `ThrowableSerializer`, `StackTraceElementSerializer`, `X500PrincipalSerializer`,
> `AccessControlContextSerializer`, `MarshalledObjectSerializer`

These are the `@AtomicSerial` replacements for JDK `Map`/`List`/`Set`/`Throwable`/etc. — present in
nearly every object graph — so strict dies on the first one hit (`MapSerializer$Ent`). The earlier
"fiddler atomic round-trip passes under strict" check was too narrow (it didn't serialize a Map).
The `validateOnly` gate's 37 warnings already pointed here; strict turns the warning into a hard,
reproducible runtime failure.

**Fix:** add a `MarshalDelegate` for `org.apache.river.api.io` covering these helpers (their nested
classes such as `MapSerializer.Ent` are already package-private/delegatable per `31386febb`). Either
hand-write it (mirroring `NormMarshalDelegate`) or enable the processor for the platform io package.
Then re-run the strict tests below.

---

## How to reproduce the strict run (and a gotcha)

1. **Fresh dist** (qa reads it directly — `dist` resolves to `JGDMS/dist/target/JGDMS-3.1.1-SNAPSHOT`,
   so no staging copy): build with Zulu 21 — `mvn -o -DskipTests install` from the reactor root.
2. **Injection gotcha:** `ant -Dorg.apache.river.qa.harness.globalvmargs=…` does **not** work —
   `qa/build.xml` line 421 uses `$${…}` (an escaped literal), so the ant `-D` is not forwarded; only
   the built-in defaults reach the spawned VMs. Instead, temporarily set the flag in
   `qa/src/org/apache/river/test/resources/qaHarness.prop` (loaded from `src/`, not the jar):
   ```
   org.apache.river.qa.harness.globalvmargs=-Dorg.apache.river.api.io.marshalDelegate.strict=true,\
   ```
   `globalvmargs` reaches both service VMs (`AbstractServiceAdmin`) **and** the test VM
   (`TestDescription:593`), which is where the proxy-equality tests marshal.
3. **Run** on the DirtyChai JDK (`JAVA_HOME=C:\Program Files\DirtyChai\jdk`), output to a file:
   ```
   ant -Drun.tests=org/apache/river/test/impl/mercury/MercuryProxyEqualityTest.td policy-update-tests
   ant -Drun.tests=org/apache/river/test/spec/renewalservice/EqualsTest.td      policy-update-tests
   ```
4. **Verify strict was actually active** (don't trust the exit code): grep the run log for the spawned
   VM command line and confirm `-Dorg.apache.river.api.io.marshalDelegate.strict=true` is present.
   A green pass without that flag in the command line is meaningless.
5. **Clean up:** revert `qaHarness.prop`; `policy-update-tests` also rewrites ~124 `qa/**/*.policy`
   files and emits untracked `.grantperm`/`.proxy-permissions/` byproducts — revert/remove those too.
   (mahalo `PrepareAndCommitExceptionTest` will hit the same `MapSerializer$Ent` wall and also risks
   the known DigestGrant pathological slowness — skip it until the io delegate lands.)

---

## Acceptance status

| Item | Status |
|---|---|
| Phase A must-fix (mahalo delegate) | ✅ resolved |
| `serves()` per-class scoping | ✅ sound / improvement |
| Phase B processor (generate + validate, validate-only gate) | ✅ correct |
| Contract completions (reggie/mercury/outrigger/mahalo/norm/lookup.util) | ✅ correct |
| Normal-mode build (platform/der/jeri) | ✅ 297 / 388 / 96 green |
| **Strict-mode qa (delegate path, not fallback)** | ✅ io gap **RESOLVED 2026-06-24** (see Update); ⚠️ qa end-to-end blocked by a *separate* harness CNFE (`net.jini.activation.arg.MarshalledObject`) |

**Bottom line:** ship-quality code, but the migration is incomplete at the foundation. Add the
`org.apache.river.api.io` delegate, re-run the strict tests, and this clears.

### Minor / still open
- `packageName()` is now dead for resolution.
- `AtomicExternal.Factory.instantiate` ctor `setAccessible` is still ungated under strict (the
  `@AtomicExternal` path, a different annotation).
- `AtomicMarshalInputStream.fields(Class)` serialForm dispatch is dead code (harmless).
