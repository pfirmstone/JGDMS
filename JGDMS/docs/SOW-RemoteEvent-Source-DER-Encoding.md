# Scope of Work — `RemoteEvent.source` DER-encoding fix

*Draft SOW — 2026-07-19. Root-caused during adversarial delegation of the item-4 DER
write-site test-coverage task (`task-der-testcov-mercury`, see
`docs/SOW-Remaining-DER-ProxyTrust-Followups.md` item 4). Not yet a formal task-tracker
entry — create one before starting.*

**STATUS: IMPLEMENTED 2026-07-19 (branch `task-remoteevent-source-any-form`, local, not pushed/
merged — awaiting the standing separately-dispatched adversarial review before merge per this
doc's own "Constraint" section).** All 5 "Concrete change" items are built and tested; the
Layer-2 narrowing pattern is documented on `RemoteEvent`'s class javadoc and applied to the
`RemoteEvent` subclasses it actually reaches (see below). Full test-plan results, deviations, and
review-focus notes are in the implementing agent's final report; summarized here:

- **The core fix works exactly as designed** and is empirically verified (not just read): a bare
  `Integer`/`String`/other-boxed-scalar source travels via the `[3]`/`[8]` scalar arms, an
  `@AtomicSerial` object source and a dynamic `Proxy` source both travel via `[20]` (the latter
  wrapping a `[8]` proxy record) — all through the real `SchemaGenerator`/`ObjectCodec`/
  `DerFieldStore`/`DerGetArg` pipeline, byte-identically deterministic, and adversarial/malformed
  `Any` field TLVs (reserved tag, form mismatch, malformed `[20]` body, >`MAX_NESTING` chain) are
  all rejected loudly, never silently.
- **The `handback` blocker (found and RESOLVED during implementation).** Immediately after the
  `source` fix, a SEPARATE, independent, pre-existing blocker was found empirically:
  `RemoteEvent` also declared `handback: java.rmi.MarshalledObject.class`, a type
  `SchemaGenerator` has never supported (`MarshalledObject` is deliberately not in the active
  `jgdms-der/.../META-INF/jgdms/der-serializers` registry). This was masked because `source`
  threw FIRST in `serialForm()` field order, so the SOW's own source-reading investigation never
  reached it; it meant `RemoteEvent` (and every subclass, and mercury's `EventWriter.write()`)
  STILL could not be schema-generated end-to-end even after the `source` fix landed. **Resolved
  by removing `handback` from `RemoteEvent.serialForm()`/`serialize()`** (it was already
  `@Deprecated`, superseded by `miHandback: MarshalledInstance`; the deprecated field/accessors/
  deprecated constructor remain for source compatibility, and `check(GetArg)`/the `(GetArg)`
  constructor still read it by name for dual-read compatibility with OLD wire data that carries
  it in its own transmitted schema — STD-006 §7.8). With both blockers gone, the real,
  fully-automatic `SchemaGenerator.generateChain(RemoteEvent.class)` path — and mercury's real
  `EventWriter.write()`/`EventReader.read()` — now succeed end-to-end. One real, pre-existing,
  independent regression surfaced by this removal was found and fixed in the same pass: the
  deprecated `getRegistrationObject()`/`handback` accessor no longer survives an `@AtomicSerial`
  round trip via ANY marshalling format (not just DER) — `jgdms-url-integrity`'s
  `RemoteEventTest.testSerialization` asserted the opposite and has been updated to the new,
  intended contract (accepted consequence of a `@Deprecated(forRemoval = true)` field leaving the
  wire form, not a bug).
- **A second, real, independent bug was found and fixed while implementing the Layer-2 narrowing
  pattern**: `ConstrainableRegistrarEvent.check(GetArg)` (reggie) read `arg.get("source", null)`
  directly from its own (pre-`super`) static method — the exact `GetArg` cross-namespace trap this
  doc's narrowing-pattern section warns about — so it ALWAYS resolved to the wrong (empty)
  namespace and ALWAYS threw "Registrar not an instanceof RemoteMethodControl", regardless of the
  real source. Fixed to `new RemoteEvent(arg).getSource()`, per the documented pattern; regression
  test confirms it reproduces the bug against the pre-fix code and passes against the fix.
  `BasicRenewalFailureEvent.check(GetArg)` was ALSO found to be dead code (defined, never
  invoked) — wired up via `super(check(arg))` while adding its own narrowing.
- **Reality diverged from the SOW's own site count**: the "Blast radius" list undercounts by 2 —
  an exhaustive reactor scan found 12 total `Object.class` serial-field sites, not 10
  (`net.jini.core.event.EventRegistration.source` and `net.jini.core.lookup.ServiceItem.service`,
  both in `jgdms-platform`, were missed). The reach-widening scanner (item 5) uses the verified
  12-site list. Also: the doc's claim "zero `Object[].class` serial fields exist on trunk today"
  is incorrect (4 exist) but immaterial — the chosen patch location (`deriveFieldWireType`, not
  `toWireType(Class,...)`) is unaffected either way, exactly as the doc's own reasoning predicts.
- **Test coverage**: build-verified (new tests, green) for the core mechanism (`jgdms-der`,
  including a full real-chain round trip for `RemoteEvent` itself), `ConstrainableRegistrarEvent`
  (reggie-dl), `ExpirationWarningEvent`/`BasicRenewalFailureEvent` (jgdms-lib-dl), and mercury's
  real `EventWriter`/`EventReader` end-to-end (scalar source, locally-resolvable `@AtomicSerial`
  source, and a deliberately-unresolvable source that fails loudly with a
  `ClassNotFoundException`). Confirmed via full schema-chain generation (not build-verified with
  dedicated round-trip tests) for `ConstrainableOutriggerAvailabilityEvent`, `RemoteDiscoveryEvent`,
  `PolicyUpdateEvent`, and the remaining blast-radius sites (`RegistrarEvent.serviceItem`,
  `RegistrarImpl.EventReg.handback`, norm's `EventType.handback`, mercury-dl's
  `RemoteEventData.cookie`, `MapSerializer`, `EventRegistration`, `ServiceItem`,
  `ConsistentMapEntry`, `AbstractSmartProxy.server`) — all 12 sites' `SchemaGenerator.generate`
  output was directly inspected and confirmed correct. See the final report for the complete
  per-site disposition and the exact schema outputs observed.

## Problem

`net.jini.core.event.RemoteEvent.EventWriter.write(RemoteEvent, LogOutputStream)` — mercury's
core event-persistence path (`PersistentEventLog.add(RemoteEvent)`) — **cannot succeed for any
event, at all**. Root cause, confirmed via source inspection and a pinned regression test
(`services/mercury/mercury-service/src/test/java/org/apache/river/mercury/EventWriterDerFormatTest.
writeFailsBecauseRemoteEventSourceFieldIsUnsupportedByDer`):

`RemoteEvent.serialForm()` (`jgdms-platform/src/main/java/net/jini/core/event/RemoteEvent.java:107`)
declares:
```java
new SerialForm("source", Object.class)
```
`SchemaGenerator.toWireType()` (`jgdms-der/src/main/java/au/net/zeus/jgdms/der/schema/
SchemaGenerator.java:396-400`) unconditionally rejects raw `Object.class` — every other supported
shape (scalar, array, enum, `@AtomicSerial`, or an interface/abstract "polymorphic slot") is
excluded by exactly this one concrete, non-abstract type. Commit `221fe251c`'s own round-trip
probes covered outrigger/mahalo/norm/reggie/fiddler but never mercury, so this has been broken
since `RemoteEvent` became `@AtomicSerial`-only and nobody caught it until this delegation round's
test-coverage pass.

Existing persisted (legacy JOSS) log entries still read fine (`EventReader`'s dual-read is
unaffected) — only *new* DER writes of any `RemoteEvent` are dead.

## Design alternatives considered and rejected

Four alternatives were evaluated and rejected before settling on the fix below (see conversation
record and the board-review/resolution sections for full reasoning; summarized here for anyone
picking this up cold):

1. **Narrow the field's declared type.** Not possible: `source` isn't `RemoteEvent`'s own field —
   it's inherited from `java.util.EventObject` (JDK, `protected transient Object source`, `@since
   1.1`), not JGDMS code. Even setting inheritance aside, there is no narrower common type: `source`
   is a documented, decades-stable public contract ("an Object representing the event source") with
   no shared marker interface across real Jini event sources.
2. **Generics (`RemoteEvent<T>`).** Rejected on two independent grounds: type erasure means
   `serialForm()` still can't reference `T.class` (illegal in Java) — the schema generator would see
   the identical erased `Object.class` regardless — and even a bounded `T extends X` just reduces to
   alternative 1's "no real common bound exists" problem, while making one of the most universally
   subclassed classes in the whole Jini API surface generic, an ecosystem-wide break, for zero actual
   gain.
3. **Widen `SchemaGenerator` to treat `Object.class` as a polymorphic `"@AtomicSerial"` slot**,
   identical to how it already treats interfaces/abstract classes (e.g. `BasicObjectEndpoint.ep`
   declared as `Endpoint`). Rejected on two grounds, both confirmed by investigation, not assumed:
   - **Security**: unlike `Endpoint` (a narrow, reviewed domain interface implemented by a bounded
     set of classes), `Object` is the universal supertype — this would admit *any* locally-resolvable
     `@AtomicSerial`-shaped class into *every* `Object`-declared field platform-wide, not just this
     one, with the only remaining gates being class-resolvability and a `DeSerializationPermission`
     check that is a documented no-op without an active SecurityManager
     (`ObjectCodec.java:529-531`).
   - **It wouldn't even fix the demonstrated real case.** JGDMS's own QA suite
     (`qa/src/.../BadEventCodebaseTest.java:89`) constructs a `RemoteEvent` with a bare
     non-`@AtomicSerial` `Integer` as `source` and sends it into mercury's live `notify()`. Even with
     `SchemaGenerator` widened, `ObjectCodec.encodeNested`'s runtime gate
     (`ObjectCodec.java:1621-1626`, `nearestAtomicSerial(cls) == null`) would still reject it — DER's
     `DerReplacer` substitution registry is closed to exactly one entry (`X500PrincipalSerializer`).

4. **Wrap `source` in a `MarshalledInstance` with a hard-coded `MarshallingFormat.ATOMIC_DER`
   constraint, mirroring `EventID.java`** — this document's *original* chosen fix, withdrawn
   2026-07-19 after the board review below. Rejected on three grounds, all CONFIRMED against
   source: (i) it fails its own acceptance test — the hard-coded format means construction never
   falls back to JOSS, so a bare non-`@AtomicSerial` `Integer` source (the live
   `BadEventCodebaseTest` case) throws `UnsupportedOperationException` at `MarshalledInstance`
   construction time, relocating rather than fixing the original symptom (Finding 2); (ii) it
   replicates `EventID.java`'s live wire-controlled-`payloadFormat` JOSS-downgrade
   deserialization hazard onto every DER-encoded `RemoteEvent` platform-wide (Finding 3); and
   (iii) its claimed security benefit was overstated — the wrap's own payload decode is
   `Object`-typed internally, so it never narrowed the admitted-class surface, it only contained
   blast radius (Finding 1). See "Board review findings" and "Resolution of board findings"
   below for the full mechanics.

## Chosen fix (revised 2026-07-19 — supersedes the withdrawn MarshalledInstance wrap)

Route `source` through the STD-006 **`Any` form**
(`jgdms-der/src/main/java/au/net/zeus/jgdms/der/object/AnyCodec.java`) by extending the
type-model rule so that a serial **field** declared exactly `Object.class` derives the `"any"`
wire-type — the same closed, self-describing, context-tagged CHOICE the rule already selects for
a genuinely unresolvable collection **element** type (`Set<Object>`, raw `Set`, `Set<?>`,
`Set<T>`). `AnyElement ::= CHOICE { scalarBoolean [0] .. scalarBytes [9], atomicSerialObject
[20], canonicalCollection [30], orderedCollection [31] }` (AnyCodec.java:50-68); a bare
`Integer` travels as `[3] IMPLICIT INTEGER`, an `@AtomicSerial` service proxy as a `[20]`
EXPLICIT nested record through the identical gated `ObjectCodec.decodeNested` path as any typed
`@AtomicSerial` field, and a dynamic `Proxy` (the APT-generated `PolicyUpdateEvent`/
`VerdictEvent` sources) via `encodeNested`'s existing `Proxy.isProxyClass` → `[8]` proxy-record
path (`ObjectCodec.java:1578-1587`; AnyCodec's `[20]` javadoc explicitly anticipates a `[8]`
proxy record as its inner TLV).

This is **not** the rejected Alternative 3, and it is not a new admission mechanism:

- **The normative type-model memo already sanctions exactly this (G8 — all sources of truth
  must agree).** `docs/der-type-model-and-element-rule.md` §2.1 exclusion 1 states: *"A field
  typed `Object` … is **not** silently promoted to 'carry anything'. It is admissible **only**
  through the `Any` form (§4), which is itself constrained to the closed subset."* And §3:
  *"The rule is **total**: every field resolves either to a concrete closed-subset wire-type or
  to `Any`."* The current field-level hard-reject
  (`SchemaGenerator.toWireType(Class,...)`:395-401) is an incomplete increment — `Any` was
  built for the collection-element rule first — not a deliberate security boundary: the
  element-side rule (`ruleForElement`, SchemaGenerator.java:539-551) already maps
  `Object.class` → `ANY` with the explicit comment *"resolves to Any (E12), NOT a hard
  error"*. The code currently disagrees with the memo at field level; this fix makes them
  agree.
- **Half the machinery already exists at field level.** `ObjectCodec.encodeValue` — the
  top-level *per-field* encoder (invoked from the field loop at ObjectCodec.java:794) —
  already dispatches wireType `"any"` → `AnyCodec.encode` (ObjectCodec.java:1053-1055). Only
  the schema-generation field rule and the decode-side field dispatch
  (`DerFieldStore.decodeAllFields`:338-378, which has `@AtomicSerial`/array/collection arms
  but no `any` arm) are missing. `WireTypes.decode`'s default arm hard-rejects `"any"` today
  (WireTypes.java:190-191) — fail-secure, so adding the arm is a deliberate opt-in of the
  token, not a behaviour flip.
- **Unlike Alternative 3, it fixes the demonstrated case and admits less.** Alternative 3
  (`Object.class` → `"@AtomicSerial"` polymorphic slot) was rightly rejected because it still
  could not carry an `Integer` and admitted every locally-resolvable `@AtomicSerial` class.
  The `Any` form's scalar arms carry the `Integer` canonically, and its `[20]` arm is
  documented and conformance-tested as *"the same `@AtomicSerial` reconstruction gate — `Any`
  is not a second door"* (AnyCodec fence (b), :100-105): the `DeSerializationPermission
  ("ATOMIC")` gate, the endpoint `ResolutionContext`, and each class's `check(GetArg)` run
  identically to a typed field, and every recursion is `MAX_NESTING`-bounded (fence (a)).
  JOSS/arbitrary-`Serializable` content is *unrepresentable* inside `Any` (memo §2.1
  exclusion 2) — there is no arm for it.
- **Determinism is by construction, stronger than the wrap ever offered.** Fence (d): every
  value maps to exactly one category tag and one canonical encoding — an `Integer` `source`
  is byte-identical from every sender, with no format toggle, no `codebaseAnnotation`
  environment residual, and no sender-dependent JOSS/DER split (the EntryRep/Item hazard this
  SOW was originally trying to dodge simply does not arise).

### Concrete change

All in `jgdms-der`; **zero code change to `RemoteEvent.java`** — `serialForm()` keeps
`new SerialForm("source", Object.class)`, `serialize()` keeps `arg.put("source", r.source)`,
`check()` keeps `Valid.notNull(arg.get("source", null), ...)`, and the public constructors and
`getSource()` are untouched (the "public API unchanged" claim becomes trivially true):

1. **`SchemaGenerator`**: map a serial field declared exactly `Object.class` → `ANY`. **Patch
   location matters and is more specific than "intercept in `toWireType(Class,...)`"**: that
   method is also called recursively for array *component* types and independently for
   top-level array derivation (`ObjectCodec.java:1474`), so patching inside it would silently
   also flip any `Object[]`-typed serial field to `array:any`, beyond the documented 9-site
   blast radius below. Intercept instead in `deriveFieldWireType`'s **non-collection branch**,
   before it falls through to `toWireType(Class,...)` — mirroring how `ruleForElement` already
   intercepts `element == Object.class` before recursing, at the element level. (Currently
   inert either way — zero `Object[].class` serial fields exist on trunk today — but pin the
   correct location now rather than let an implementing agent pick the convenient one.) Every
   other concrete non-`@AtomicSerial` class remains hard-rejected exactly as today — the rule
   keys on the declaration the developer already wrote (G4), no annotation, no opt-in marker
   (an opt-in would restate the fact `Object.class` already declares).
2. **`DerFieldStore.decodeAllFields`** (:338-378): add an `ANY` arm alongside the existing
   `"@AtomicSerial"`/`array:@AtomicSerial:`/collection arms — read the field's complete raw
   `AnyElement` TLV without decoding (mirroring `readNestedRawTlv`), deferring actual decode
   to `DerGetArg.get()` so `AnyCodec.decode(reader, depth, decodeUnit, resolution)` runs with
   the threaded nesting depth, the `DeserializationCompletion` unit, and the endpoint
   `ResolutionContext` — and so `der.getarg` stays dependency-cycle-free of `der.object`,
   same as the nested-record arm.
3. **`DerGetArg`**: decode the deferred raw `Any` TLV on `get()` exactly as nested
   `@AtomicSerial` fields defer to `ObjectCodec.decodeNested`. `RemoteEvent.check()` calls
   the untyped `arg.get("source", null)` — confirm the untyped overload triggers the deferred
   decode the same way the typed one does, and cache the decoded value on first `get()`
   (whatever the nested-record arm already does): the subclass narrowing pattern below reads
   `source` twice per reconstruction (once via its defensive base-instance construction, once
   via the real super-chain), and the two reads must observe one decode, not two.
4. **Docs + conformance sync (G8/G9)**: update the STD-006 draft and memo §3 rule table so the
   "anything unresolvable → `Any`" row explicitly includes a bare `Object`-declared *field*
   (the §2.1/§3 prose already implies it; the rule table is framed around collection
   elements), and add field-position conformance tests for all four AnyCodec fences (a)–(d) —
   reserved-tag hard-reject, constructed/primitive mismatch, depth bound, canonical-tag —
   at field position, not just element position.
5. **Reach-widening forcing function (second board review, MEDIUM, required — not just
   recommended)**: today, landing on the vacuous `Object`-typed `admissibleConstructClass`
   residual requires an unusual, code-review-visible declaration (a raw `Set`, `Set<Object>`,
   `Set<?>`, or type-variable `Set<T>`) — rare enough that it's effectively self-reviewing.
   This change makes the identical residual reachable from **any** ordinary `Object`-typed
   field platform-wide, and removes the one thing that made today's 9 broken sites visible at
   all: `Object foo;` in a `serialForm()` currently throws a loud `DerException` at
   schema-generation time — an accidental but real forcing function. After this SOW that same
   declaration silently compiles and works. Add a source-scanning conformance test (same style
   as this project's existing declaration-usage scanners, e.g. the `hashCode()`-usage one, G4)
   that enumerates every `SerialForm(..., Object.class)` site in `src/main` and fails the build
   if the count/site-list changes without the test itself being updated — so a *future*
   `Object.class` serial field still forces an explicit, reviewed acknowledgment, even though
   it no longer fails to compile. Seed the allowlist with exactly the 9 sites in "Blast radius"
   below.

**Hardening (recommended, small, not a blocker)**: `AnyCodec.decode`'s `[20]` arm calls the
4-arg `ObjectCodec.decodeNested` *without* an expected-supertype, unlike the typed-element path
which threads `expectedElementType` into the pre-construction admission gate (security review
R2 F1; `decodeElementValue`, ObjectCodec.java:2608-2618). For a genuine `Any` slot the expected
type is `Object` — the gate is vacuous either way, and the identical vacuous-gate residual
already exists on trunk for every `Any` collection element (and existed inside the withdrawn
`MarshalledInstance` wrap, per Finding 1) — so this is not a new door. But thread the
receiver's declared type through anyway where available, so the F1 gate stays uniform across
all `decodeNested` entry points.

**Layer-2 narrowing pattern for `RemoteEvent` subclasses (the prescribed complement to the
vacuous base-class slot).** `RemoteEvent` itself genuinely cannot narrow `source` — "any
Object" *is* its decades-stable contract — but almost every concrete subclass knows exactly
what its source really is (reggie's `RegistrarEvent` source is the lookup service, norm/
outrigger/fiddler's events likewise name their service). Per memo §8.2, an `Any`-resolved
field shifts type validation entirely onto `check(GetArg)`; the pattern for subclasses is to
**defensively construct a plain `RemoteEvent` instance from the `GetArg` inside their own
static `check(GetArg)` method (`new RemoteEvent(arg)` — validated, side-effect-free), call
`getSource()` on it, and type-check/narrow the result to the subclass's specific expected
type — rejecting with `InvalidObjectException` on mismatch — before constructing their own
instance.** Constructing the base instance (rather than reading `arg.get("source")` directly
from the subclass frame) also sidesteps the known GetArg class-namespace trap: a pre-super
`arg.get(...)` from a subclass reads the *subclass's* serial-field namespace, not
`RemoteEvent`'s (the root cause of the reggie `JoinManager` proxy NPE regression), whereas
`RemoteEvent(GetArg)` reads its own namespace correctly. The narrowing runs after the `Any`
decode (so the ATOMIC gate, depth bounds and canonical-form checks have already passed) but
before the subclass instance exists — the standard `@AtomicSerial` fail-before-construction
discipline — and it converts the base class's unavoidable `Object`-width admission into a
per-subclass closed contract at zero wire-format cost.
Document this pattern in `RemoteEvent`'s class javadoc as part of this change.

**Where this pattern does and doesn't apply (corrected by second board review, LOW, confirmed):
it is specifically for a subclass narrowing an *ancestor's* `Object`-typed slot, not a class's
own directly-declared field.** `RegistrarEvent.serviceItem` and `AbstractSmartProxy.server`
were originally listed here as examples — both are wrong: `RegistrarEvent` and
`AbstractSmartProxy` are themselves `abstract`, and `serviceItem`/`server` are each class's
*own* field, not an ancestor's slot being read from a subclass frame — `new RegistrarEvent(arg)`
does not compile, there being no `arg` constructor invocable that way for an abstract type in
this shape, and no cross-namespace risk exists to defend against in the first place, since the
read already happens in the class's own `check(GetArg)` frame. `RegistrarEvent` already handles
this correctly today via ordinary same-class `check(GetArg)` narrowing (no pattern change
needed there). Apply the defensive-base-instance pattern only where a *concrete* subclass reads
an *ancestor's* `Object`-typed field from outside that ancestor's own declaring frame — i.e.
genuinely for `RemoteEvent` subclasses narrowing `RemoteEvent.source` itself (reggie/norm/
outrigger/fiddler's event subclasses). For `AbstractSmartProxy.server` and any other
class's-own-field site in the blast-radius list below, the correct fix is an ordinary `check()`
assertion of the expected shape, not this pattern.

### Blast radius — the other `Object.class` serial fields (in scope for the test plan)

A rule keyed on the declaration flips **every** currently-unschemable `Object.class` serial
field from schema-generation error to `Any`. Grep of `src/main` (2026-07-19) found, besides
`RemoteEvent.source`:

- `reggie-dl/.../RegistrarEvent.java:50` (`serviceItem`) and `reggie-service/.../RegistrarImpl.java:890` (`handback`)
- `norm-service/.../event/EventType.java:137` (`handback`)
- `mercury-dl/.../RemoteEventData.java:57` (`cookie`)
- `jgdms-platform/.../MapSerializer.java:138-139` (`key`/`value`)
- `jgdms-lib-dl/.../ConsistentMapEntry.java:71-72` (`key`/`value`)
- `jgdms-lib-dl/.../AbstractSmartProxy.java:175` (`server`)

None of these can DER-encode today (the field rule throws at schema generation), so **no
existing wire behaviour and no existing schema digest changes** — the flip is
broken→working only, which is why an automatic rule is acceptable here where a
behaviour-changing widening would not be. Each site is an acceptance-criteria item: (a) a DER
round-trip test, and (b) a memo-§8.2 review of the class's `check(GetArg)` — an
`Any`-resolved field carries no wire-level type commitment, so type validation shifts
entirely onto Layer 2, and several of these classes (notably `AbstractSmartProxy.server`,
which is expected to hold a remote-interface `Proxy`) should assert their real expected shape
in `check()` rather than accepting any closed-subset value. If a per-site review concludes a
site should *not* accept `Any` content, the correct fix at that site is narrowing its declared
`SerialForm` type — not carving exceptions into the rule.

Adversarial-schema note (for the reviewing agent): the decode side dispatches on the
**transmitted** at-marshal-time schema (`DerFieldStore.decodeAllFields` reads
`def.wireType()` off the wire; the schema digest is self-consistent-only, G12), so a hostile
sender can already declare `"@AtomicSerial"` — and, after this change, `"any"` — for any
field position; the receiver's enforcement is, and remains, Layer 2's typed
`GetArg.get(name, default, type)` checks plus the ATOMIC admission gate. This change adds no
new reconstruction door (fence (b)); it adds one more token whose decode path is
identically gated. State this explicitly in the implementation PR so the review doesn't have
to rediscover it.

## Scope boundaries

**In scope**: `jgdms-der` only (`SchemaGenerator`, `DerFieldStore`, `DerGetArg`, AnyCodec
field-position conformance tests) plus the STD-006/memo doc sync. `RemoteEvent.java` gets **no
code change** — verification only (its existing `serialForm`/`serialize`/`check` are already
correct under the revised design). Round-trip tests for the other `Object.class` field sites
listed above are in scope; *code* changes to those sites (narrowing a declared type, tightening
a `check()`) are follow-up items raised by the per-site §8.2 review, not this item's job.

**New follow-up items created by the board review (this SOW no longer depends on them, but
they must be tracked — see "Resolution of board findings", Finding 3):**
- **`MarshalledInstance` wire-controlled `payloadFormat` downgrade (HIGH, pre-existing on
  trunk, NOT introduced or widened by this SOW as revised).** Fail-closed API + consumer
  retrofit; concrete design in the Finding 3 resolution below. Live exposure today:
  `EventID.readSource()`/`readObject` (`EventID.java:91,222`), every consumer of
  `RemoteEvent.getRegistrationInstance().get(...)` (the `miHandback` field is already
  wire-declared `MarshalledInstance.class`, RemoteEvent.java:111), and an audit surface of
  ~18 `src/main` files across mercury/outrigger/fiddler/norm/mahalo/reggie/platform/lib-dl
  that call `MarshalledInstance.get(...)`.
- **`EventID` migration off the `MarshalledInstance` wrap.** Once field-level `Any` exists,
  `EventID.source` should adopt it too — which *also* fixes `EventID`'s own latent copy of
  Finding 2: `EventID.serialize()` (EventID.java:71-72) and `writeObject` (:197-198) construct
  a hard-coded-`ATOMIC_DER` `MarshalledInstance` around `source` today, so mercury persisting
  an `EventID` for an `Integer`-source event (exactly `BadEventCodebaseTest`'s good events)
  throws `UnsupportedOperationException` at write time. Same root defect as this SOW's
  original chosen fix, already live in mercury; fold into the `MarshalledInstance` follow-up
  or track separately, but do not lose it.

**Explicitly out of scope — separate items, do not fold in:**
- **Mercury's independent `-dl` dependency gap**: `mercury-service/pom.xml` depends only on
  `mercury-dl`/`jgdms-lib`, not `reggie-dl`/`norm-dl`/`outrigger-dl`/`fiddler-dl`. Under DER's
  no-codebase-download rule, mercury cannot locally resolve those services' own concrete
  `RemoteEvent` subclasses (`ConstrainableRegistrarEvent`, `ExpirationWarningEvent`,
  `BasicRenewalFailureEvent`, `ConstrainableOutriggerAvailabilityEvent`) even after this fix. A
  second, independent blocker on full end-to-end mercury relay, found by the same investigation.
  Needs its own design decision (does a generic relay service take a compile dependency on every
  other service's `-dl` jar? that's architecturally heavy) — not a quick fix, not this item's job.
- **The two other real production gaps** found by this delegation round — norm's
  `ClientLeaseWrapper.getClientLease()` uncaught `IllegalStateException`, reggie's `EventReg`
  `@AtomicSerial`-path `NullPointerException` instead of graceful listener-drop — unrelated to
  `RemoteEvent.source`, already flagged separately.

**In scope for this item's test plan, not a separate item**: the two APT-generated dynamic-`Proxy`
source cases (`PolicyUpdateEvent`, `VerdictEvent`) — architecturally expected to work under the
`Any` approach (`AnyCodec`'s `[20]` arm delegates to `ObjectCodec.encodeNested`, whose
`Proxy.isProxyClass` → `[8]` proxy-record path is at ObjectCodec.java:1578-1587, and the `[20]`
javadoc explicitly anticipates a `[8]` proxy record as its inner TLV), but not directly
confirmed against generated sources in this investigation (unbuilt tree). Verify with an
explicit test rather than assuming.

## Test plan / acceptance criteria

1. Round-trip `RemoteEvent` (or a minimal concrete `@AtomicSerial` subclass) through DER
   encode/decode with each real production `source` shape found this session:
   - A service's own `@AtomicSerial` proxy (matches reggie/outrigger/norm/fiddler's real
     production sites) — travels as `Any` `[20]`.
   - A plain non-`@AtomicSerial` JDK type (`Integer`, matching `BadEventCodebaseTest`'s existing,
     already-in-tree usage) — **must succeed** via the `[3]` scalar arm, not just degrade
     gracefully to a documented failure; this is the demonstrated real case the fix exists for.
     Also cover `String` (`[8]`) and at least one more boxed scalar.
   - A dynamic `Proxy` (matching `PolicyUpdateEvent`/`VerdictEvent`'s shape) — `[20]` wrapping
     a `[8]` proxy record.
2. Confirm mercury's `EventWriter.write()`/`PersistentEventLog.add()` — the originally broken path —
   now succeeds end-to-end for the `Integer`-source case, without requiring the separate `-dl`
   dependency fix above (a scalar `Any` source needs **no class resolution at all** on decode,
   so this case is fully `-dl`-independent by construction).
3. Confirm `getSource()` still returns an object `.equals()` to the original after a full
   encode→decode round trip, for each shape in (1).
4. Confirm the 4 known-good production `RemoteEvent` subclasses' existing round-trip behavior is
   unchanged (reggie/outrigger/norm/fiddler).
5. **Determinism**: two independent encodes of `.equals()` `RemoteEvent`s (same values,
   separately constructed) produce byte-identical `source` field TLVs, for the `Integer` and
   `String` shapes at minimum (fence (d) at field position).
6. **`EventReader` replay (Finding 4)**: write a `RemoteEvent` to a DER log via
   `PersistentEventLog.add()` and replay it via `EventReader`:
   - `Integer` source — replay **must succeed** (no class resolution involved).
   - A locally-resolvable `@AtomicSerial` source — replay must succeed.
   - A deliberately-unresolvable `@AtomicSerial`-shaped source (the `-dl`-gap stand-in) —
     replay must fail **loudly and cleanly** (a `ClassNotFoundException`-rooted
     `IOException`/`DerException`, not a masked `"source cannot be null"` and not log
     corruption), and the failure must be documented as the `-dl` follow-up item's case, so
     the two items' boundary is pinned by a test instead of asserted.
7. **Failure mode**: a malformed/hostile `source` TLV (reserved `Any` tag, over-depth nesting,
   non-canonical scalar) surfaces as a loud decode failure from `RemoteEvent(GetArg)`
   construction — never a silent `null` source (the board's loud-fail G6 resolution, inherited
   by construction since `arg.get("source")` propagates the decode exception and `check()`'s
   `Valid.notNull` only guards a genuinely-null wire value). Include one G13-style *run* (not
   reasoned-about) adversarial input: a deeply-nested `Any` collection chain at field position
   confirming the fence (a) depth bound triggers before stack exhaustion.
8. **Blast-radius sites**: a DER round-trip test per `Object.class` serial-field site listed
   above (reggie `RegistrarEvent.serviceItem` / `RegistrarImpl` `handback`, norm
   `EventType.handback`, mercury-dl `RemoteEventData.cookie`, platform `MapSerializer`,
   lib-dl `ConsistentMapEntry`, lib-dl `AbstractSmartProxy.server`), each with its §8.2
   `check(GetArg)` review recorded in the PR (finding-or-clean, per site).

## Constraint

Touches `jgdms-der` — the platform's wire-format core, shared by every DER-encoded type in the
reactor, not scoped to mercury alone. Per this project's standing review discipline: isolated
`git worktree`, fresh agent to implement, a separately-dispatched agent for adversarial review
before merge — do not merge to trunk without review. Review should explicitly verify: (a) the
"zero `RemoteEvent.java` change / public API unchanged" claim (`getSource()`'s return type and
behavior for every shape in the test plan); (b) that the new field-level `ANY` arm threads
depth/`DeserializationCompletion`/`ResolutionContext` identically to the element arm (G10 —
check what the guarded call recurses *into*, at the frame that actually recurses); (c) the
adversarial-schema note above (transmitted-schema-controlled dispatch, Layer-2 enforcement
unchanged); and (d) the per-site §8.2 reviews for the blast-radius field list.

## Board review findings (2026-07-19, pre-implementation design review)

*[Editorial note, added with the 2026-07-19 revision: this section reviews the original,
now-withdrawn `MarshalledInstance`-wrap design (preserved as rejected Alternative 4 above) and
is kept unedited for provenance. Its "two explicit decisions" and `EventID`-mirroring
references point at text that has since been replaced. Each finding's disposition under the
revised design is in "Resolution of board findings" below.]*

Reviewed against `JGDMS-Board-Reviewer-Guidance.md` Part I + §2.1/§2.2/§2.4. Citations above were
independently re-verified and found accurate (one immaterial ~5-line offset on the
`BadEventCodebaseTest.java` reference). Two of the three design justifications above did **not**
survive verification:

**Finding 1 (HIGH, CONFIRMED) — the "Security" justification is narrower than claimed.**
`MarshalledInstance.get()`'s own internal payload decode is *also* effectively `Object`-typed
internally (`DerMarshalInstanceInput.readObjectImpl` decodes at `Object.class` unconditionally,
type-checks only post-construction; `ObjectCodec.admissibleConstructClass`'s own javadoc,
`ObjectCodec.java:535-540`, documents this as the same "Object-typed slot residual" the rejected
Alternative 3 would have created directly). The actual benefit of the `MarshalledInstance` wrap is
**blast-radius containment** — it avoids widening `SchemaGenerator` for every other `Object`-typed
field platform-wide — not a narrower admitted-class surface for `source` itself, which was never
narrowable given what the field means. Fix: rewrite the "Security" bullet above to claim only scope
containment, not admission narrowing.

**Finding 2 (HIGH, CONFIRMED) — internal contradiction; the design as specified does not pass its
own acceptance test.** Hard-coding `MarshallingFormat.ATOMIC_DER` on construction
(`MarshalledInstance.chooseMarshalFactory`, `MarshalledInstance.java:588-602`) means construction
**never** falls back to JOSS — that path is reached only when the format is `null`/`FORMAT_JOSS`.
A bare `Integer` `source` (exactly `BadEventCodebaseTest.java`'s live, already-in-tree usage) has no
`@AtomicSerial` ancestor and isn't in the closed `DerReplacer` registry, so `DerObjectStreamCodec.
writeObject`'s final branch throws `UnsupportedOperationException` (`DerObjectStreamCodec.java:
428-432`) **at `MarshalledInstance` construction time**. `EventWriter.write()` would still fail for
that exact demonstrated case — the original symptom, relocated, not fixed. Determinism (hard-coded
format) and graceful JOSS fallback for non-`@AtomicSerial` sources are mutually exclusive with this
mechanism as specified; the SOW cannot have both for free. **Must decide explicitly before
implementation**: (a) drop the `BadEventCodebaseTest`/`Integer` acceptance criterion and scope
`source` support to `@AtomicSerial`-only, (b) drop hard-coded-format determinism and accept
EntryRep/Item-style sender-dependent non-determinism for this field, or (c) add an explicit
boxed-scalar/String encoding path alongside the `@AtomicSerial` one.

**Finding 3 (HIGH, CONFIRMED, pre-existing in `EventID.java` today — would be significantly
widened by this SOW) — wire-controlled `payloadFormat` allows an unguarded-JOSS-deserialization
downgrade.** `MarshalledInstance`'s `payloadFormat` field is read straight off the wire
(`MarshalledInstance.java:238-239`) and never cross-checked against any format the receiver
expects; `get()` dispatches purely on the wire-decoded string, and a `JOSS`-format payload decodes
via bare `MarshalInputStream` — no `ObjectInputFilter`, no allowlist, no per-class
`DeSerializationPermission` gate (unlike `AtomicMarshalInputStream`). A peer legitimate enough to
be an accepted event source (authenticated, but not necessarily fully trusted for this specific
action — the "authenticated ≠ trusted" confused-deputy shape, §2.2) can send a `source` whose
`MarshalledInstance` declares `payloadFormat=JOSS` carrying a serialized gadget chain; the honest
sender's `ATOMIC_DER` constraint constrains only the honest sender's own encoder, never a hostile
sender's wire bytes. This already exists today via `EventID.java`'s live `mi.get(false)` (mercury's
internal bookkeeping, narrow blast radius); this SOW's "mirror `EventID`" instruction would
replicate the identical unguarded pattern onto `RemoteEvent.source` — reconstructed on every
DER-encoded `RemoteEvent` flowing through the platform's entire event-notification subsystem, a far
larger blast radius. **Treat as a blocking prerequisite, not a footnote**: add fail-closed
`payloadFormat` enforcement (reject a decoded format that doesn't match what the call site
requires) before this SOW — or continued reliance on `EventID`'s existing pattern — ships.

**G6 resolutions (supersede the "two explicit decisions" section above):**
- `verifyCodebaseIntegrity`: use **`true`**. Moot on the intended `ATOMIC_DER` path
  (`DerMarshalInstanceInput.java:107-108`, "carried for fidelity"), but strictly safer on the
  (currently unguarded, see Finding 3) JOSS-downgrade fallback path, at no cost to the intended path.
- Unmarshal failure: **loud-fail**, not `EventID`'s tolerant-null. `getSource()` is public,
  client-inspected API, unlike `EventID`'s internal bookkeeping copy; masking a real
  `IOException`/`ClassNotFoundException` as `"source cannot be null"` hides the actual cause.

**Finding 4 (MEDIUM, PLAUSIBLE, not independently run) — may not be as separable from the "out of
scope" `-dl` gap as claimed above.** The unwrap happens eagerly inside `check(GetArg)`, so every
`RemoteEvent(GetArg)` reconstruction — including `EventReader`'s replay of DER-persisted log
entries — resolves `source`'s concrete class synchronously at construction time. The test plan's
own primary case (a foreign service's own `@AtomicSerial` proxy) is exactly the shape mercury's
`-dl` gap blocks it from resolving locally, and DER's no-codebase-download rule offers no fallback
`RMIClassLoader` download path the way legacy JOSS had. Once this SOW ships, `EventWriter.write()`
may succeed while `EventReader`'s replay of a persisted foreign-service-sourced event still fails —
add an explicit test exercising this before treating the two items as fully independent.

**Sound, no findings**: G1/G5 layering (unwrap correctly placed in Layer 2, no bypass of
`RemoteEvent`'s own admission gate); the §2.4 determinism sweep (broadened beyond `EventID.equals()`
to the whole reactor — no byte-level comparison of `RemoteEvent`/`EventID`/`source` found anywhere;
mercury's `EventWriter`/`EventReader`/`PersistentEventLog`/`TransientEventLog` have no `.equals()`
or hash-dedup logic at all) — the original determinism reasoning in this doc holds as stated.

## Resolution of board findings (2026-07-19, design revision)

*Resolved by re-reading the actual sources (not the board's citations alone):
`MarshalledInstance.java` in full, `AnyCodec.java` in full, `SchemaGenerator.java:330-560`,
`ObjectCodec.java` (field-encode loop :770-800, `encodeValue` :1027-1110, `encodeNested`
Proxy path :1578-1626, `decodeElementValue` :2596-2640), `DerFieldStore.java:300-420`,
`WireTypes.java` dispatch, `RemoteEvent.java` in full, `EventID.java` in full,
`qa/src/org/apache/river/test/impl/mercury/BadEventCodebaseTest.java` and
`MyLocalRemoteEvent.java` in full, and `docs/der-type-model-and-element-rule.md` §2–§3/§8.
Nothing was built or executed; every "must verify empirically" flag below is deliberate.*

### Finding 2 — RESOLVED: none of the three offered options; a fourth, strictly better one

The finding offered (a) drop the `Integer` criterion, (b) drop determinism, or (c) bolt a
boxed-scalar path onto the wrapper. All three were rejected in favour of the `Any`-form design
above, on grounds each option's own examination surfaced:

- **(a) is not available.** `BadEventCodebaseTest`'s *intent* was checked, not assumed: the
  "bad" events are `MyLocalRemoteEvent`s — whose source file says, verbatim, *"Dummy class
  used to induce class not found exceptions on the client-side"* — i.e. the deliberately-
  rejected thing is an **unresolvable event class** (a codebase failure), not the `Integer`
  source. The very same test then sends six **plain `RemoteEvent`s with the identical
  `Integer(0)` source** as its "good events" leg and **fails unless all six are delivered**
  (BadEventCodebaseTest.java:119-141, plus 3 more in the mixed leg). A bare boxed-scalar
  `source` is therefore a must-work case in the project's own live QA contract, not an
  acceptable-to-reject edge. Dropping it would break the good-events leg of an in-tree test.
- **(b) buys nothing worth its price.** Sender-dependent JOSS/DER splitting re-imports the
  exact EntryRep/Item non-determinism class this project already paid to remove once — and,
  worse, the JOSS fallback path is the same unguarded `MarshalInputStream` surface Finding 3
  condemns. Accepting (b) would resolve Finding 2 by *enlarging* Finding 3.
- **(c) as stated (a scalar side-path on the wrapper) duplicates machinery that already
  exists, canonically, in-tree.** The task's own pointer was followed: `AnyCodec` already
  encodes every boxed scalar, `String` and `byte[]` deterministically and canonically
  (`[0]`–`[9]` IMPLICIT arms, fence (d): one category tag and one byte-form per value), and
  its `[20]` arm reconstructs `@AtomicSerial` objects through the *identical* gated
  `decodeNested` path as any typed field (fence (b): "`Any` is not a second door").
- **Is `Any` reusable for a scalar *field*, not just a collection element?** Verified: yes,
  and it is half-wired already. The codec layer is field-ready — `ObjectCodec.encodeValue`,
  the top-level per-field encoder (field loop at :794), already dispatches wireType `"any"`
  to `AnyCodec.encode` (:1053-1055), and `AnyCodec.decode` takes only a reader/depth/context,
  nothing collection-specific. The two genuinely missing pieces are the `SchemaGenerator`
  field rule (which today hard-rejects `Object.class` at :395-401) and a decode-side arm in
  `DerFieldStore.decodeAllFields`. Critically, the normative memo **already specifies the
  intended outcome**: §2.1 exclusion 1 — an `Object`-typed *field* "is admissible **only**
  through the `Any` form" — and §3 — "the rule is **total**". The element-only scope of
  today's code is an unfinished increment, and the field-level hard-reject is a
  code-vs-memo divergence (G8), resolved here in the memo's favour. This is why the revised
  design is not the rejected Alternative 3 wearing a hat: Alternative 3 (`Object` →
  `"@AtomicSerial"` slot) could not carry an `Integer` and had no scalar story; `Any` was
  built for exactly this "genuinely unresolvable declared type" case, carries the scalars,
  and closes JOSS/arbitrary-`Serializable` out by construction.
- **Determinism is not traded away — it improves.** The wrap's determinism claim needed a
  hard-coded format *and* a hand-wave over `codebaseAnnotation` environment-dependence. An
  `Any` scalar has neither: fence (d) gives one byte-form per value, from every sender,
  with no format field to toggle. Test plan item 5 pins this.

### Finding 3 — RESOLVED for this SOW by construction; residual platform defect split out with a concrete design

- **For `RemoteEvent.source`**: the revised design puts **no `MarshalledInstance` on the wire
  for `source` at all** — there is no `payloadFormat` field to lie about, and a JOSS payload
  is *unrepresentable* inside `Any` (memo §2.1 exclusion 2: no arm exists for arbitrary
  `Serializable`; `AnyCodec.encode`'s fall-through requires an `@AtomicSerial`-shaped value
  and `encodeNested` fails fast otherwise). This is G7's "construction beats vigilance":
  rather than adding a format check the downgrade attack must now get past, the attack has no
  encoding. The board's "would be significantly widened by this SOW" clause is therefore
  fully discharged — the revised SOW widens nothing.
- **The underlying `MarshalledInstance` defect is real, pre-existing, and stays open** — it
  must not silently vanish because this SOW stopped depending on it. Verified against
  `MarshalledInstance.java`: `payloadFormat` is read straight off the wire in the `GetArg`
  constructor (:238-239, defaulting to `FORMAT_JOSS`), there is no accessor and no
  cross-check, and `get()` dispatches purely on it via `getMarshalFactory()` →
  `factoryForFormat()` (:529-551) — the JOSS branch yielding a bare
  `MarshalInputStream`-based `MarshalledInstanceInputStream` (:1076-1119) with no
  `ObjectInputFilter`/allowlist. The asymmetry is the tell: the **encode** side already has
  constraint enforcement (`chooseMarshalFactory`/`requiredFormat`, :588-632) but the
  **decode** side has no counterpart, so an honest sender's `ATOMIC_DER` constraint binds
  only the honest sender. **Chosen fix shape: option (a), a `MarshalledInstance`-level API**
  (a call-site check in `RemoteEvent` — option (b) — is moot with no unwrap in `RemoteEvent`,
  and would fix one of ~18 consumers anyway):
  1. `public String getPayloadFormat()` — trivial accessor, lets any call site audit.
  2. Fail-closed decode overloads symmetric with the encode side, e.g.
     `get(ClassLoader, boolean, ClassLoader, Collection, Class<T>, InvocationConstraints)`
     (with convenience forms): resolve the required format via the existing
     `requiredFormat(constraints)` and throw `InvalidObjectException` **before**
     `factoryForFormat` is consulted and before any payload byte is parsed, when the
     wire-declared `payloadFormat` does not match. Constraint-less `get()` keeps current
     behaviour (compatibility), so migration is per-call-site and mechanical.
  3. Retrofit `EventID.readSource()`/`readObject` to require `ATOMIC_DER` on the wire path
     (the legacy JOSS-log dual-read can stay scoped to bytes read from mercury's own local
     log where that provenance is actually established — decide in the follow-up, fail-closed
     if in doubt).
- **Scope decision, explicit**: fixing only `RemoteEvent` never addressed the exposure anyway
  — grep shows ~18 `src/main` files across mercury (`EventID`, `EventReader`,
  `MailboxImpl`, `ServiceRegistration`, `RemoteEventData`), outrigger (`EntryRep`,
  `StorableReference`, `JoinStateManager`), fiddler, norm, mahalo, reggie, platform
  (`RemoteDiscoveryEvent`) and lib-dl (`UIDescriptor`) calling `MarshalledInstance.get(...)`,
  plus every consumer of `RemoteEvent.getRegistrationInstance()` (`miHandback` is *already*
  wire-declared `MarshalledInstance.class` today — RemoteEvent.java:111 — so this hazard
  already rides on every wire-received `RemoteEvent` regardless of `source`). That is a
  platform-wide audit, not a `RemoteEvent` footnote: folding it in would balloon this SOW
  across six services. **Acceptable to split because the revised SOW neither introduces nor
  widens the exposure** (the board's blocking language was conditioned on replicating the
  pattern, which no longer happens); tracked as the HIGH follow-up in "Scope boundaries",
  with the API design above so it is actionable, and with `EventID`'s latent Finding-2 twin
  (its `serialize()` hard-codes `ATOMIC_DER` around `source` and so throws today for an
  `Integer`-source event, EventID.java:71-72) recorded in the same item.

### Finding 4 — RESOLVED: the recommended replay test is added, sharpened by the revised design

The finding stands under any design in which `source` is reconstructed during
`RemoteEvent(GetArg)` — and it still is here: `check()` calls `arg.get("source")` at
construction, so `EventReader`'s replay of a DER-persisted event resolves `source`'s content
eagerly. The revised design changes the *shape* of the coupling, for the better: a **scalar**
`Any` source involves no class resolution at all, so the originally-broken mercury path
(`Integer` source, write → replay) becomes verifiable fully independent of the `-dl` gap —
while a foreign service's `@AtomicSerial` proxy source (`[20]` → `decodeNested`) still
requires local resolvability, exactly the `-dl` gap's territory. Test plan item 6 therefore
runs all three replay cases (scalar must-pass, resolvable-proxy must-pass, unresolvable
must-fail-loudly-and-cleanly), pinning the boundary between this item and the out-of-scope
`-dl` item with a test instead of an assertion. Not running it would leave "the two items are
independent" as exactly the kind of unverified self-report (G3) the board flagged.

### Finding 1 — RESOLVED: superseded

The finding demanded the "Security" bullet claim be cut down from admission-narrowing to
blast-radius containment. The wrap and its bullet are withdrawn entirely; the revised design
makes no narrowing claim for the base-class slot (`Object`-width admission is inherent to what
`source` means), states the actual admitted set (`Any`'s closed subset — strictly smaller than
the wrap's, which carried the same `@AtomicSerial` residual *plus* an entire JOSS arm), and
adds the subclass `check(GetArg)` narrowing pattern as the place where real type enforcement
belongs (Layer 2, per memo §8.2).

## Second board review findings (2026-07-19, independent re-review of the revised `Any`-form design)

*Second, independent reviewer — different pass than the one that produced the findings above.
Reviewed against the same guidance (Part I + §2.1/§2.2/§2.4), instructed to treat this as a
fresh adversarial pass, not a checklist confirming the first design's fixes. Re-derived every
load-bearing claim in this document against current source rather than trusting citations.*

**Verdict: SOUND**, with one real MEDIUM finding this document's own framing had understated,
and two LOW precision gaps that would have caused a real implementation mistake. Not a
rejection — the crux question (below) was run down fully rather than assumed either way.

**Verification (G3/G8)**: every citation re-checked against trunk — the memo quotes, the
`ObjectCodec.encodeValue`/`AnyCodec`/`SchemaGenerator`/`WireTypes` claims, the "8 other sites"
list (spot-checked 4 of 8 directly, confirmed all currently hit the schema-generation hard-reject),
the `EventID.java` live-bug claim, and the `miHandback`-already-`MarshalledInstance` decoupling
claim — all **CONFIRMED accurate**. One doc-hygiene nit found in passing: `docs/
der-type-model-and-element-rule.md` §8.3 still says "design-only, nothing built," which is
stale — `AnyCodec` and the collection-element `Any` rule are fully implemented on trunk; fold a
one-line correction into this SOW's own memo-sync task (item 4 above).

**The crux question, run down fully**: is the `[20]` `atomicSerialObject` reconstruction gate,
reached via `Any`, actually different from the vacuous `admissibleConstructClass(Object.class,
X)` residual the withdrawn wrap had (Finding 1 above)? **Traced concretely: no, it is the
identical gate** — `AnyCodec.decode`'s `[20]` arm calls the 3-arg `ObjectCodec.decodeNested`,
which hard-defaults `expectedSupertype = Object.class` (it has no parameter to pass anything
narrower); tracing the typed-field path for comparison confirms it would resolve to the exact
same vacuous check for a genuinely `Object`-declared field regardless of which door reaches it.
This is not a flaw specific to this design — it is unavoidable given what `Object`-typed means,
and this document's own "Hardening" note above already discloses it rather than hiding it. The
real, confirmed difference from the withdrawn wrap is that `Any` closes the *additional*,
separate JOSS-downgrade hole (Finding 3) by construction — no arm exists for arbitrary
`Serializable` — which the wrap did not.

**MEDIUM finding, folded into "Concrete change" item 5 above**: reach-widening loses an
accidental forcing function. Today, hitting the vacuous residual requires an unusual,
effectively self-reviewing declaration (a raw/unbounded `Set`); after this SOW it's reachable
from any ordinary `Object`-typed field, and the thing that made today's 9 sites visible at all
(a loud `DerException` at schema-generation time) is gone. Mitigated by the source-scanning
conformance test now specified in item 5.

**Two LOW precision gaps, already corrected inline above**:
1. **Patch location.** "Intercept in `toWireType(Class,...)`" would also silently flip
   `Object[]`-typed fields to `array:any` (that method is shared with array-component
   derivation) — corrected to specify `deriveFieldWireType`'s non-collection branch, item 1.
2. **Narrowing-pattern misapplication.** The subclass-narrowing pattern was said to apply to
   `RegistrarEvent.serviceItem`/`AbstractSmartProxy.server` — both are abstract classes'
   *own* fields, not an ancestor's slot; `new RegistrarEvent(arg)` doesn't compile, and no
   cross-namespace risk exists there to defend against. Corrected in the "Layer-2 narrowing
   pattern" section above; those two sites need only an ordinary `check()` shape assertion.

**Also independently confirmed (not new work, but the doc slightly overstated these as
open)**: the "read `source` twice, decode once" concern in "Concrete change" item 3 is already
handled by `AtomicSerial.GetArg`'s existing `cachedLookup`/`callerClass()` memoization — traced
mechanistically, both the defensive base-instance read and the real super-chain read resolve to
the same `(RemoteEvent.class, "source")` cache bucket, so this is verification work for the
implementing agent, not new machinery to build. The GetArg cross-namespace-trap avoidance claim
for the narrowing pattern was also traced mechanistically (not just asserted) and holds for the
cases it actually applies to (per the LOW-2 correction above).
