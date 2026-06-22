# Code Review — @AtomicSerial MarshalDelegate, Phase A

**Scope reviewed:** Phase A of the per-package `MarshalDelegate` work against the acceptance spec in
`SOW-AtomicSerial-Delegate-Marshalling.md` §11.
**Commits:** `24eb29484` (A1–A3: SPI + engine dispatch), `3af27e795` (A4: per-package delegates),
`f1cd2c889` (A5: strict mode) — branch `feature/atomicserial-marshal-delegate`, off `c2c87ab2f`.
**Date:** 2026-06.

---

## Verdict

High quality and faithful to the design. The hard parts — loader-scoped resolution, codec-agnostic
dispatch, strict-mode gating, and no field access — are all correct. **One must-fix gap** (the mahalo
delegate) prevents §11's verification from passing under strict mode; **two minor non-blocking notes**;
and the **strict-mode verification run** still needs to be demonstrated.

## What is correct

- **SPI + resolver (A1).** `MarshalDelegate` and `MarshalDelegates` are faithful. `delegateFor(Class)`
  resolves via `Service.providers(MarshalDelegate.class, c.getClassLoader())` and selects the provider
  by **defining-loader identity *and* package name** — the §3.1/§4 rule, which simultaneously yields
  correct package-private access (same runtime package), the correct version, and robustness to
  parent-loader visibility. Results are memoised in a `ClassValue` with a `NONE` sentinel for negative
  caching; `ServiceConfigurationError` is swallowed to the reflective fallback.
- **Codec-agnostic dispatch (A2 + A3).** Delegate-first at **every** reflective site in **both**
  engines: JOSS `ObjOutputStream.fields` (serialForm) and serialize, JOSS read construction via
  `AtomicSerial.Factory.instantiate`; DER `SchemaGenerator.invokeSerialForm`,
  `ObjectCodec.invokeSerialize`, and **all three** DER construction paths (single-class, hierarchy,
  nested decode). Reflective fallback preserved at each.
- **Strict mode (A5).** Every fallback `setAccessible` is gated: `strictBlockClass`
  (serialForm/serialize) and `strictBlockCtor` (the `(GetArg)` ctor, in both `AtomicSerial.Factory`
  and DER `findGetArgConstructor`) throw `InvalidClassException`/`DerException` instead of widening
  access. Enabled with `-Dorg.apache.river.api.io.marshalDelegate.strict=true` (or
  `MarshalDelegates.setStrict`).
- **Per-package delegates (A4).** `NormProxyMarshalDelegate` (and the fiddler/mercury peers) are pure
  in-package direct dispatch — **no reflection, no field access** — and correctly model the contract:
  abstract levels get serialForm/serialize but no `create`; `@Stateless` levels get an empty
  serialForm but still `create` for the read constructor. Registered via
  `META-INF/services/org.apache.river.api.io.MarshalDelegate`.
- **Principles upheld:** no field access introduced anywhere; resolution strictly by defining loader;
  discovery through `Service` (OSGi-bridged), no `exports`/`opens`.

## Gap (must fix): `org.apache.river.mahalo.proxy` has no delegate

mahalo-dl contains two **package-private** `@AtomicSerial` classes — `ConstrainableTxnMgrProxy` and
`ConstrainableTxnMgrAdminProxy` (package-private nested, `@Stateless`) — and there is **no
`MahaloProxyMarshalDelegate`**. `@Stateless` exempts a class from serialForm/serialize but **not** from
read-side construction, whose package-private `(GetArg)` ctor still needs the fallback. Under strict
mode that ctor hits `strictBlockCtor`, so the **mahalo `PrepareAndCommitExceptionTest` — one of the
three verification tests — fails** (it passes today only because non-strict mode uses the
`setAccessible` fallback). §11/A4 explicitly lists mahalo.

**Fix:** add a `MahaloProxyMarshalDelegate` for `org.apache.river.mahalo.proxy`, mirroring
`NormProxyMarshalDelegate` — handle the public `TxnMgrProxy`/`TxnMgrAdminProxy` levels and the two
package-private constrainable leaves (their `serialForm`/`serialize` are no-ops as `@Stateless`, but
`create` is required) — plus its `META-INF/services` entry.

**(outrigger correctly needs none** — all its `@AtomicSerial` proxies are public; `ConstrainableIteratorProxy`
is package-private but is not `@AtomicSerial`.)

## Minor (non-blocking)

- `AtomicExternal.Factory.instantiate` — the **@AtomicExternal** (Externalizable-style) ctor path, a
  different annotation with no serialForm/serialize and outside the `MarshalDelegate` model — still
  does an ungated `setAccessible`. So "strict forbids the fallback" is not *total* if a package-private
  `@AtomicExternal` class exists. Worth a one-line `strictBlockCtor` gate later for completeness.
- The read-side `AtomicMarshalInputStream.fields(Class)` serialForm dispatch the change adds is dead
  code (its only caller is commented out; the read path takes field descriptors from the wire).
  Harmless and consistent, just inert.

## Verification (the real A5 gate — still to demonstrate)

Run with the fallback disabled: `-Dorg.apache.river.api.io.marshalDelegate.strict=true`, the three qa
tests (`mahalo PrepareAndCommitExceptionTest`, `norm renewalservice/EqualsTest`,
`mercury MercuryProxyEqualityTest`) plus the platform and der unit suites. Expectation: **norm /
mercury / fiddler green**, and **mahalo red until its delegate lands** — at which point a fully-strict
run staying green is the proof the delegate path carries marshalling, not the fallback.

## §11 acceptance checklist

| Item | Status |
|---|---|
| A1 — `MarshalDelegate` SPI + `Service`-based resolver | ✅ |
| A2 — JOSS dispatch (serialForm / serialize / construct) | ✅ |
| A3 — DER dispatch (serialForm / serialize / all construct paths) | ✅ |
| A4 — per-package delegates (non-public packages) | ⚠️ partial — **mahalo missing** |
| A5 — strict mode retires the fallback | ✅ (modulo the @AtomicExternal note) |
| Verification — strict-mode qa + unit suites green | ⏳ pending; mahalo will fail until A4 is completed |
