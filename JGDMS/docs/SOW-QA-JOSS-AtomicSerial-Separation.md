# SOW — Separate JOSS and `@AtomicSerial` implementations (production classes + qa suite)

Status: DRAFT for review · 2026-06-19
Standard: realises JGDMS-STD-008 §9.1 (transition / dual-path comparative testing) across both the
production `@AtomicSerial` classes and the qa suite, and discharges the "A1 — coordinated session
against qa" work that STD-008 §14.8–§14.9 explicitly hold for a session like this.

---

## 1. Purpose

Separate the two serialization implementations — **JOSS** (`writeObject`/`readObject` +
`serialPersistentFields`) and **`@AtomicSerial`** (`serialForm()` + `serialize(PutArg)` /
`(GetArg)`) — so each can be exercised, validated, and ultimately **removed (JOSS at 4.0.0)**
independently of the other.

Separation has two halves:

- **Production classes (Phase 0).** Every `@AtomicSerial` class that *also* carries a JOSS path
  must express its JOSS serial form and its `@AtomicSerial` `SerialForm` as **two independent
  definitions** with **two parallel field-handlers** — never one derived from the other. This is
  the precondition that makes comparative testing meaningful: comparing JOSS↔atomic for a class
  whose `serialPersistentFields = serialForm()` compares a definition against itself and proves
  nothing (and, post-uncouple, no longer even compiles — `SerialForm` no longer extends
  `ObjectStreamField`).
- **qa suite (Phases 1–2).** Give the legacy River harness (`<repo-root>/qa`) the ability to
  exercise each codec separately and comparably, at unit/spec scale (Phase 1) and at integration
  /over-the-wire scale (Phase 2), so DER↔JOSS equivalence is provable end-to-end and the JOSS path
  is a cleanly deletable unit.

Scope boundary: the Phase 0 sweep touches only serialization members. For a correctly-separated
class there is **no wire-format or behavioural change** — both forms must already describe the same
fields in the same order. The qa work adds tests plus one small, reversible harness-config
indirection. No production *behaviour* changes.

---

## 2. Baseline — the seam as it exists today (grounded)

**Production `@AtomicSerial` census** (main source across the reactor, incl. `services/*`):

| | Count | Disposition |
|---|---|---|
| `@AtomicSerial` production classes | **151** | — |
| …that **also carry a JOSS path** (`writeObject`/`readObject`/`serialPersistentFields`) | **84** | **dual-mode → in scope for Phase 0 separation** |
| …pure-`@AtomicSerial` (no JOSS path) | ~67 | already separated → **no-op; do NOT add a JOSS path** |

The coupled `serialPersistentFields = serialForm()` antipattern no longer appears in compiled code
(it cannot — `SerialForm` ≠ `ObjectStreamField` post-uncouple), **but it is still *recommended* by
the canonical `AtomicSerial` javadoc** (`jgdms-platform/.../api/io/AtomicSerial.java`, lines
121–129: *"the static field serialPersistentFields should also be defined: `= serialForm();` … to
avoid duplication"*). The reference doc is teaching the forbidden idiom — a defect in this sweep.

**qa suite seam:**

| Axis | Today | Implication |
|---|---|---|
| **Wire codec** | ILFactory chosen *inline* per config — e.g. `new BasicILFactory()` hardcoded in `qa/src/.../outrigger/configs/jeri/default.config`. ~117 `BasicILFactory` (JOSS) vs ~38 `AtomicILFactory` (atomic) call sites. | No single switch flips the suite between codecs; the codec is baked into ~155 sites in a fixed mix. |
| **Spec/unit io tests** | `io.marshalledinstance` (5), `io.marshalinputstream` (9), `io.marshaloutputstream` (1), `io.util` fixtures (e.g. `FakeObject` — JOSS-shaped). All tagged `testCategories=io,io_spec`. | Existing serialization coverage is **JOSS-only, untagged by codec**; no `@AtomicSerial` counterpart. |
| **Categories** | Domain-based only (`javaspace`, `jeri`, `loader`, …). No codec axis. | `io_joss`/`io_atomic` and a suite-wide `marshalProtocol` axis are genuinely new. |
| **`MarshalledInstance`** | Dual-form artifact (carries the §7.8 record; `payloadFormat` discriminates JOSS-default vs DER via `ServiceLoader`). | Natural **unit-level** comparison point. |

---

## 3. Phase 0 — Production `@AtomicSerial` serial-form separation (prerequisite sweep)

Separate the two serial forms in all **84 dual-mode** classes. This resolves STD-008 §9.1's
`[OPEN]` ("which classes are worth the parallel handler") in favour of **all dual-mode classes get
independent parallel handlers** — not a representative sample.

### 3.1 Inventory (first deliverable, bounds the sweep)
Generate the classified list of the 151 `@AtomicSerial` classes → the **84 dual-mode** (separate)
vs ~67 pure-atomic (no-op). This list is the work-tracking artifact and the §9.1 resolution record.

### 3.2 Per-class recipe (normative, from §9.1) — for each of the 84:
1. `public static SerialForm[] serialForm()` — the `@AtomicSerial` wire form (already present).
2. `private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[]{…}`
   — **independent**, duplicated from the field declarations, **NOT** `= serialForm()`. The
   duplication is deliberate: independent definitions are what let the comparison detect a
   divergence; shared code masks it.
3. **Atomic write** via `serialize(PutArg, T)`; **JOSS write** via `writeObject(ObjectOutputStream)`
   using `ObjectOutputStream.PutField`. **Two parallel handlers — no shared helper** (§9.1: the two
   encodings can no longer share a field-handler).
4. **Atomic read** via the `(GetArg)` constructor; **JOSS read** via `readObject(ObjectInputStream)`
   using `ObjectInputStream.GetField`. Parallel.
5. Confirm both forms enumerate the **same logical fields in the same order** — a mismatch is either
   a real bug or exactly the divergence the qa comparison (Phases 1–2) is built to catch at runtime.

A class whose `serialForm` field would resolve to a non-`@AtomicSerial` superclass is a contract
violation (each class owns its own namespace) — fix the class, do not paper over it.

### 3.3 Per-class option: separate vs drop
For each dual-mode class the default is **separate** (retain JOSS for comparative testing; removed
at the 4.0.0 end state). Where a class demonstrably needs **no** JOSS interop (no external
persistence, no legacy wire peer), the JOSS path may instead be **dropped now** rather than
maintained as dual — avoiding busywork on a path that would only be deleted later. The inventory
(§3.1) should tag each of the 84 as *separate* or *drop-now*.

### 3.4 Canonical-doc fix
Rewrite `AtomicSerial.java` javadoc (lines 121–129) to **mandate** the independent
`ObjectStreamField[]` form and explain why the duplication is deliberate (§9.1) — replacing the
current "avoid duplication via `= serialForm()`" guidance, which is both uncompilable and the
security-relevant antipattern.

### 3.5 Optional: regression gate
A build-time check (grep gate, or ASM lint in the `preferred-list-analyzer` mould) that fails if
`serialPersistentFields = serialForm()` (or any single definition serving both paths) reappears, so
the coupling cannot creep back during the transition.

### 3.6 Dependency
Phase 0 is the precondition for *meaningful* per-class comparison. It may **interleave** with
Phase 1 (the comparative fixture test only needs the specific fixtures it exercises to be
dual-form), but Phase 2's whole-suite comparison needs at least the **wire-critical subset** (the
`-dl`/proxy types) separated first.

---

## 4. Phase 1 — qa spec/unit axis (isolated; no network, no dist)

Split the existing `io.*` serialization coverage into a JOSS set and an `@AtomicSerial` set, and add
a **comparative** test that round-trips one object through both and asserts equivalence.

1. **Category split.** Retag existing JOSS tests with `io_joss`; add `io_atomic` for the new
   counterparts. Both stay under `io`, so existing category runs are unaffected.
2. **`@AtomicSerial` counterparts.** For each JOSS io spec test on a dual-form artifact, add the
   atomic counterpart against the atomic codec / `MarshalledInstance` DER `payloadFormat`. Start
   with `io.marshalledinstance` (highest value), then the input/output-stream equivalents against
   `AtomicMarshalInputStream`/`AtomicMarshalOutputStream`.
3. **Comparative round-trip test** (core deliverable). For a representative fixture set: encode →
   JOSS bytes and → DER/atomic bytes; decode both; assert the decoded graphs are `equals`-equivalent
   **and** that each path produced the expected `payloadFormat` (the discriminating assertion, §6).
4. **Fixture discipline (§9.1).** `io.util` fixtures shared by both sets become dual-mode by the same
   §3.2 recipe (independent `serialPersistentFields`).
5. **Fail-fast fixture.** An `@AtomicSerial` type with **no** `serialize(PutArg)` → assert the atomic
   encoder **throws** (never field-grabs). Locks the mandatory-`serialize` rule in at qa level.

**Open (placement):** keep the comparative round-trip in the legacy harness `io_*` package
(co-located with the JOSS reference tests, same fixtures, same `MarshalledInstance`) — recommended —
vs JUnit in the modules. Module JUnit already covers pure-codec round-trips; don't duplicate.

---

## 5. Phase 2 — qa integration axis (coordinated; network, dist)

Make the wire codec **selectable suite-wide** so the integration suite runs once over JOSS and once
over `@AtomicSerial`, and the pass/fail sets are diffed. This is STD-008 §14.9's "A1, coordinated qa."

1. **Harness property** `org.apache.river.qa.harness.marshalProtocol = joss | atomic` (default §7.2).
2. **Config indirection.** Replace inline `new BasicILFactory(...)` / `new AtomicILFactory(...)` in
   the service/test config templates with a single entry resolving the ILFactory from the property
   (a helper mirroring the existing `getStringConfigVal`/exporter-resolution pattern). Scope: the
   ~155 inline sites, by template edit, not per-test.
3. **Per-test override + category.** Codec-specific tests (a protocol that *must* be `Basic`, or an
   atomic-only feature) pin their factory and gain a `marshalProtocol` tag so the "other" profile
   excludes them. The switch sets the *default*; configs retain the last word.
4. **Two profile runs.** Script `-DmarshalProtocol=joss` and `=atomic` over a **bounded** service set
   (§7.1 — full-suite runs are pathological) and diff the result sets; any test that passes under one
   codec and fails under the other is a DER↔JOSS divergence to triage.

---

## 6. Discriminating-test requirement (STD-008 §13/§14.8 discipline) — normative

Every comparative or atomic test MUST fail if the object silently travelled by JOSS when DER/atomic
was intended:

- assert the observed `payloadFormat` / codec actually used (not merely that a round-trip succeeded);
- the fail-fast fixture (§4.5) proves the encoder throws rather than field-grabs when
  `serialize(PutArg)` is absent;
- comparative runs are **re-run independently** (separate invocations), not asserted from one shared
  encode.

A green round-trip that cannot distinguish "DER drove it" from "JOSS fallback drove it" is not
acceptance evidence.

---

## 7. Constraints (must be honoured)

### 7.1 Build & run
- **JDK:** build/test the platform/der modules with **vanilla Zulu 21** (`C:\Program Files\Zulu\jdk-21`)
  — DirtyChai shadows `java.base` classes → silent fake-greens. Module builds are `mvn -o`,
  `-pl`-scoped, never reactor-wide.
- **qa run cost is pathological** (~16h/4GB whole-suite + DigestGrant boot-window cascade). Phase 2's
  two-profile comparison MUST be scoped to a **bounded service set per run**, never the whole suite in
  one shot. Deliverable = a repeatable scoped recipe.
- **One `mvn` at a time across agents** — check for other agents' running maven first.
- **`serialize(PutArg)` is mandatory**; the encoder throws if absent; never imitate Java
  Serialization field-grabbing.

### 7.2 Open decisions for Peter (surface, don't guess)
1. **Scope cut.** Phase 0 + 1 + 2, or a subset? (Phase 0 is the production precondition; Phase 1 is
   self-contained qa value; Phase 2 is the larger coordinated network piece.)
2. **Default `marshalProtocol`.** Keep `joss` default during transition (lowest blast radius), or flip
   default to `atomic` to keep the new path under constant CI pressure? When does the default flip
   ahead of the 4.0.0 JOSS removal?
3. **§9.1 [OPEN] — now resolved** to "all 84 dual-mode classes get independent handlers." Residual:
   which of the 84 are *separate* vs *drop-now* (§3.3).
4. **Phase 2 diff granularity.** Per-test pass/fail diff only, or also per-call wire-byte
   capture/compare (stronger evidence, heavier to wire)?

---

## 8. Deliverables

**Phase 0** — (a) classified inventory of the 151 → 84 dual-mode (tagged separate/drop-now) + 67
pure; (b) the 84 classes with independent `serialPersistentFields` + parallel handlers (or JOSS
dropped where tagged); (c) `AtomicSerial.java` javadoc fix; (d) *optional* regression gate.

**Phase 1** — (e) `io_joss`/`io_atomic` category split; (f) `@AtomicSerial`/DER counterpart tests
(`marshalledinstance` + stream equivalents); (g) one comparative DER↔JOSS round-trip with
discriminating `payloadFormat` assertions; (h) dual-mode `io.util` fixtures + one fail-fast
no-`serialize` fixture.

**Phase 2** — (i) `marshalProtocol` harness property + config-template indirection replacing inline
ILFactory construction; (j) two-profile scoped run recipe + result-diff procedure.

**Cross-cutting** — (k) update STD-008 §9.1 `[OPEN]` and §14.8 with the realised mechanism + the
"all 84" resolution.

---

## 9. Acceptance criteria

- **Phase 0:** all 84 dual-mode classes carry **independent** `serialPersistentFields` (none derived
  from `serialForm()`); both forms enumerate the same logical fields/order; the inventory is complete;
  the optional gate (if built) is green; the `AtomicSerial` javadoc no longer shows the coupled idiom.
  Reactor compiles and a spot-check class round-trips through **both** codecs to equal objects.
- **Phase 1:** `io` still green unchanged; `io_joss` and `io_atomic` independently selectable and each
  green; the comparative test proves representative fixtures round-trip equivalently through JOSS and
  DER and **fails** if the codec is swapped underneath it; the no-`serialize` fixture makes the atomic
  encoder throw.
- **Phase 2:** a single `-DmarshalProtocol=atomic|joss` flips the wire codec for a bounded service set
  with no per-test edits; the two-profile diff is reproducible.
- **End-state proof:** deleting the `io_joss` category + JOSS fixtures **and** the JOSS members of the
  84 classes leaves `io_atomic` and the atomic suite green — i.e. the JOSS path is proven separable for
  4.0.0.

---

## 10. References

- `JGDMS-STD-008-AtomicSerial-Serialization-Uncoupling-v0.1-DRAFT.md` — §9.1 (transition / dual-path,
  the per-class normative rules this SOW's §3.2 implements), §13 (MarshalledInstance hybrid +
  discriminating-test discipline), §14.8–§14.9 (held "A1, coordinated qa" work).
- `jgdms-platform/src/main/java/org/apache/river/api/io/AtomicSerial.java` (lines 121–129 — the
  coupled-idiom javadoc to fix; the `serialForm()`/`PutArg`/`GetArg` contract).
- qa suite: `<repo-root>/qa/src/org/apache/river/test/spec/io/` (existing JOSS coverage);
  `<repo-root>/qa/src/org/apache/river/qa/harness/QAConfig.java` (config-val resolution; itself
  `@AtomicSerial`).
- `jgdms-der` module JUnit fixtures (`Holder`, `MarkerSerializer`, …) — the per-module pure-codec
  round-trip layer this SOW complements, not duplicates.
- `tools/preferred-list-analyzer` — model for the optional §3.5 ASM regression gate.
