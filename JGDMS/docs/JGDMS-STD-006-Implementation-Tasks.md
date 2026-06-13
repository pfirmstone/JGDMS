# STD-006 Implementation — Task Breakdown for Claude Code

*Companion to JGDMS-STD-006-DER-WireFormat-v0.11-DRAFT.md*
*Each task is scoped to a single, verifiable Claude Code session.*

---

## How to use this document

Each task is small enough to run, review, and verify in one sitting. Run them in
order within a phase; phases are mostly sequential because later layers depend on
earlier ones. Every task lists explicit acceptance criteria — when you start a
Claude Code session, paste the task's scope and acceptance criteria as the brief,
and do not let the session expand past them.

**Before each session:** state the spec section(s) as required reading. Tell the
agent to read them first and restate the rules back to you before writing code.
This catches the misinterpretations that need your eye (the namespace model
especially — see the four corrections in the design history).

**After each session:** run the task's tests yourself. Do not approve a merge on
the agent's say-so that tests pass; run them.

**Blocked tasks** depend on open questions in the spec that need your field-level
knowledge first. They are marked 🔒 and listed separately at the end. Don't start
them until the open question is resolved.

---

## Phase 0 — Project Scaffold

### Task 0.1 — Module skeleton and build
**Scope:** Create the Maven/Gradle module for the DER codec. Package layout only,
no logic. Set up the test harness (JUnit 5), the property-test library (jqwik),
and a `byte[]` hex-dump test utility.
**Dependencies:** none.
**Acceptance criteria:**
- Module builds clean with `mvn test` (one trivial passing test).
- jqwik is wired and a sample property test runs.
- Hex-dump utility round-trips `byte[] ↔ String` and is unit-tested.
**Spec refs:** none (scaffold only).
**Session size:** small.

---

## Phase 1 — DER Primitives Codec

This is the pure foundation: no @AtomicSerial knowledge, just DER TLV encoding.
Fully testable in isolation. Get this rock-solid before building anything on it.

### Task 1.1 — DER length and tag encoding
**Scope:** Encode/decode DER tag bytes and the definite-length form (short and long
form). No value types yet — just the TLV framing primitives.
**Dependencies:** 0.1.
**Acceptance criteria:**
- Short-form lengths (0–127) round-trip.
- Long-form lengths (128–2^31) round-trip.
- Property test: random length → encode → decode → equals original.
- Rejects indefinite-length form (not valid in DER).
**Spec refs:** §4.1.
**Session size:** small.

### Task 1.2 — DER INTEGER and BOOLEAN
**Scope:** Encode/decode `INTEGER` (minimal two's-complement, no leading redundant
bytes — DER canonical form) and `BOOLEAN` (0x00 / 0xFF only).
**Dependencies:** 1.1.
**Acceptance criteria:**
- Canonical minimal-byte INTEGER encoding verified against known DER vectors.
- Negative, zero, and large (>8 byte) integers round-trip.
- BOOLEAN rejects any value other than 0x00 / 0xFF on decode (DER strictness).
- Property test: random BigInteger round-trips.
**Spec refs:** §4.1.
**Session size:** small.

### Task 1.3 — DER OCTET STRING and UTF8String
**Scope:** Encode/decode `OCTET STRING` (raw bytes) and `UTF8String` (UTF-8 text).
**Dependencies:** 1.1.
**Acceptance criteria:**
- Empty, ASCII, and multi-byte UTF-8 (e.g. emoji, CJK) round-trip.
- OCTET STRING preserves arbitrary bytes including nulls.
- Property test: random String round-trips through UTF8String.
**Spec refs:** §4.1, §4.2.
**Session size:** small.

### Task 1.4 — DER SEQUENCE (constructed)
**Scope:** Encode/decode `SEQUENCE` as a constructed TLV wrapping ordered child
TLVs. Provide a reader that iterates children and a writer that accepts ordered
children and computes the wrapping length.
**Dependencies:** 1.2, 1.3.
**Acceptance criteria:**
- Nested SEQUENCEs round-trip (SEQUENCE of SEQUENCE).
- Child order is preserved exactly (order is significant — §3.8).
- Reader correctly identifies the SEQUENCE boundary and stops there even when
  trailing bytes exist in the buffer (this is the substrate for decoding case (b)).
- Property test: list of random primitives → SEQUENCE → decode → same list, same order.
**Spec refs:** §4.1, §3.8.
**Session size:** medium.

### Task 1.5 — Canonical encoding conformance vectors
**Scope:** Add a test suite of known-answer DER vectors (hand-computed or from an
ASN.1 reference) covering each primitive and a nested SEQUENCE. No new production
code — this locks the codec against an external truth source.
**Dependencies:** 1.4.
**Acceptance criteria:**
- At least 10 known-answer vectors pass byte-for-byte.
- Two encodings of the same logical value produce identical bytes (canonical form
  property — the basis for content-hash identity).
**Spec refs:** §4.1.
**Session size:** small.

---

## Phase 2 — Schema Representation

### Task 2.1 — AtomicSerialFieldDef and AtomicSerialSchemaRecord types
**Scope:** Java types for `AtomicSerialFieldDef` (wireName, wireType) and
`AtomicSerialSchemaRecord` (className, parentSchemaHash, fields). DER encode/decode
for each, built on Phase 1.
**Dependencies:** 1.4.
**Acceptance criteria:**
- Both types round-trip through DER.
- `parentSchemaHash` absent (Object root) and present cases both encode/decode.
- `fields` order is preserved (positional significance — §3.9).
**Spec refs:** §7.8 (ASN.1 modules), §3.9.
**Session size:** medium.

### Task 2.2 — Schema digest computation
**Scope:** Compute `SHA-256(DER(AtomicSerialSchemaRecord))` as the schema version.
Implement the Merkle chain: a leaf's digest is computed over a record whose
`parentSchemaHash` is the parent's digest.
**Dependencies:** 2.1.
**Acceptance criteria:**
- Identical schema records produce identical digests (determinism).
- Changing any field name, type, or order changes the digest.
- Changing a parent's schema changes the leaf digest (chain property).
- Digest is stable across JVM runs (no HashMap iteration in the DER encoding path).
**Spec refs:** §7.8.
**Session size:** medium.

---

## Phase 3 — GetArg DER Adapter

This is the heart of the model and the part most worth your review. The three
decoding cases and the complete-field-store semantics live here.

### Task 3.1 — GetArg complete field store
**Scope:** Implement the `GetArg` DER adapter as a complete field store: given a
schema and the DER bytes of one class's SEQUENCE, decode *all* fields into a
name→value map before any constructor runs. Implement `get(name, defaultValue)`
as a selection over the populated map.
**Dependencies:** 2.1.
**Acceptance criteria:**
- All fields decoded and present in the store before any `get()` call.
- `get(name, default)` returns the decoded value when present.
- `get(name, default)` returns `default` when the field is absent from the schema.
- Unrequested fields remain in the store (verify they are present but untouched).
- A field present in the store but never requested is released when the store goes
  out of scope (no leak — verify via a reference/teardown test).
**Spec refs:** §3.9 (decoding model, complete field store).
**Session size:** medium.

### Task 3.2 — The three decoding cases
**Scope:** Implement and test the three cases explicitly, using a hand-built schema
and hand-built DER payloads (no real @AtomicSerial classes yet — those come in
Phase 4).
**Dependencies:** 3.1.
**Acceptance criteria:**
- **Case (a)** schema matches payload: every byte consumed, all fields present.
- **Case (b)** schema has more fields than payload (payload is an old/narrower
  encoding): missing fields return defaults via `get()`.
- **Case (c)** payload has more fields than the decoder's schema: extra trailing
  fields within the SEQUENCE are read and discarded to the SEQUENCE boundary;
  known fields decode correctly.
- A test asserts that "the schema" used is the one passed in (simulating the
  MarshalledInstance embedded schema), not any ambient/global schema.
**Spec refs:** §3.9 (three decoding cases), §11.8.
**Session size:** medium.

---

## Phase 4 — Object Encoder / Decoder

### Task 4.1 — Single-class encode/decode
**Scope:** Encode one `@AtomicSerial` class (no inheritance) to its private SEQUENCE
using `serialForm()`, and decode it back through `GetArg` + the `(GetArg)`
constructor. Use a small fixture class with a few primitive fields.
**Dependencies:** 3.1, 2.2.
**Acceptance criteria:**
- Fixture object → DER → object round-trips with field equality.
- The encoded form matches the schema generated from `serialForm()`.
- `check(GetArg)` is invoked before construction; a constructed invariant-violating
  fixture throws from `check()`, not after.
**Spec refs:** §3.9, §7.8; STD-001 for `@AtomicSerial` contract.
**Session size:** medium.

### Task 4.2 — Schema generation from serialForm()
**Scope:** Walk a single `@AtomicSerial` class's `serialForm()` and produce its
`AtomicSerialSchemaRecord`. Map each `SerialForm(wireName, type)` to an
`AtomicSerialFieldDef`.
**Dependencies:** 4.1.
**Acceptance criteria:**
- Generated schema's field names and order match `serialForm()` exactly.
- Re-generating from the same class produces a byte-identical schema record
  (and therefore identical digest).
- Type mapping table covers all primitive wire types plus OCTET STRING and
  UTF8String; unknown types raise a clear error rather than guessing.
**Spec refs:** §7.8, §3.9.
**Session size:** medium.

### Task 4.3 — Hierarchy: nested per-class SEQUENCEs
**Scope:** Extend encode/decode to a hierarchy of `@AtomicSerial` classes. Each
class gets its own private SEQUENCE; the same `GetArg` is passed up the constructor
chain but dispatches by calling-class identity to the correct SEQUENCE level.
**Dependencies:** 4.1, 4.2.
**Acceptance criteria:**
- Two-level (`Beta extends Alpha`, both `@AtomicSerial`) round-trips.
- Three-level round-trips.
- A field named `"x"` in both `Alpha` and `Beta` namespaces stays independent —
  test that `Alpha`'s `x` and `Beta`'s `x` carry different values through a
  round-trip without collision.
- The `GetArg` passed to `Alpha(GetArg)` cannot see `Beta`'s namespace (assert that
  `Beta`'s fields are not visible from `Alpha`'s `get()` calls).
**Spec refs:** §3.9 (private namespace), §3.10.
**Session size:** large — consider splitting two-level and three-level into 4.3a/4.3b.

### Task 4.4 — Non-@AtomicSerial sub/superclass behaviour
**Scope:** Implement and test the wire-visibility rules: a non-`@AtomicSerial`
subclass is dropped (produces its `@AtomicSerial` superclass on the wire); a
non-`@AtomicSerial` superclass's state is the responsibility of the lowest
`@AtomicSerial` class.
**Dependencies:** 4.3.
**Acceptance criteria:**
- `Bar extends Foo` (only `Foo` is `@AtomicSerial`): serialising a `Bar` produces a
  `Foo` on the wire; deserialising produces a `Foo`; `Bar` does not exist in the
  result (assert the decoded type is exactly `Foo`).
- `Beta extends Alpha` (only `Beta` is `@AtomicSerial`): `Alpha`'s state carried in
  `Beta`'s namespace round-trips; no separate `Alpha` SEQUENCE exists on the wire.
**Spec refs:** §3.10.
**Session size:** medium.

---

## Phase 5 — MarshalledInstance

### Task 5.1 — MarshalledInstanceRecord encode/decode
**Scope:** Implement `MarshalledInstanceRecord` (payloadBytes, schemaBytes,
schemaDigest, codebaseAnnotation OPTIONAL, payloadFormat). Schema bytes are the
full leaf-to-root chain, concatenated.
**Dependencies:** 4.2, 2.2.
**Acceptance criteria:**
- Round-trips with and without the optional codebaseAnnotation.
- `schemaDigest` equals `SHA-256` of the leaf record in `schemaBytes`.
- The embedded schema chain decodes back to the per-class schema records, leaf first.
**Spec refs:** §7.8.
**Session size:** medium.

### Task 5.2 — Decode-using-embedded-schema
**Scope:** Wire the decoder so that decoding a `MarshalledInstanceRecord` payload
uses the *embedded* schema, not the receiver's current `serialForm()`. Add a test
where the receiver's class has evolved (extra field) and confirm the embedded
schema still drives decoding, with `GetArg` supplying the default for the receiver's
new field.
**Dependencies:** 5.1, 3.2, 4.3.
**Acceptance criteria:**
- Decode uses embedded schema digest; if it differs from local `serialForm()`
  digest, the embedded schema is used (case (b) or (c)) and the test asserts which.
- Receiver-newer (case (c) from receiver's perspective): receiver's added field
  gets its default; round-trip succeeds.
- Sender-newer (case (b)): extra payload field is read and discarded; round-trip
  succeeds.
**Spec refs:** §7.8, §3.11 (@AtomicSerial as compatibility layer), §3.9.
**Session size:** medium.

---

## Phase 6 — Evolution Test Matrix

### Task 6.1 — Class hierarchy evolution scenarios
**Scope:** A dedicated test class encoding each row of the §11.9 table. No new
production code expected — this is a verification harness that proves the
implementation matches the spec's evolution rules. If a scenario fails, the bug is
in Phase 3–5 code, not here.
**Dependencies:** 5.2.
**Acceptance criteria:** one passing test per §11 scenario —
- 11.1 add non-`@AtomicSerial` subclass → no wire impact.
- 11.2 remove non-`@AtomicSerial` subclass → no wire impact.
- 11.3 add non-`@AtomicSerial` superclass → child adds fields, defaults handle old data.
- 11.4 add `@AtomicSerial` to superclass → existing child SEQUENCE unchanged; new
  parent SEQUENCE present but not consumed by unmodified child.
- 11.5 remove `@AtomicSerial` from class → SEQUENCE disappears; child unaffected.
- 11.6 insert new `@AtomicSerial` class → new SEQUENCE; old data gets defaults.
- 11.7 remove `@AtomicSerial` class → SEQUENCE disappears; neighbours unaffected.
**Spec refs:** §11 (all), §3.9, §3.10.
**Session size:** large — split per scenario group if needed.

### Task 6.2 — Field-level evolution
**Scope:** Test the field-level symmetric degradation: add a field, drop a field,
stop requesting a field. Verify the §11.8 "uniform across granularities" claim.
**Dependencies:** 6.1.
**Acceptance criteria:**
- Add field at end of `serialForm()`: old data → default; new data → value.
- Stop requesting a field: still encoded, decoded into store, not accessed, GC'd.
- Field present on wire, absent from schema: discarded to boundary.
**Spec refs:** §3.9, §11.8.
**Session size:** medium.

---

## Phase 7 — Schema Registry Service

### Task 7.1 — ServiceSchemaEntry
**Scope:** Implement the `ServiceSchemaEntry` Jini Entry (`@SerialEntry`,
primitive/byte[]/String fields only). Encode/decode and a round-trip test.
**Dependencies:** 2.2.
**Acceptance criteria:**
- All fields are primitive, String, or byte[] (no service objects) — assert via a
  reflective field-type test.
- Round-trips as an Entry; `schemaDigest` field matches a generated schema's digest.
**Spec refs:** §12.3.
**Session size:** small.

### Task 7.2 — SchemaRegistry interface and in-memory implementation
**Scope:** Implement the `SchemaRegistry` interface (register, getSchema,
getSchemaChain, isCompatible) with an in-memory append-only store. Bytes/primitives
only across the interface. No Jini wiring yet — pure logic + tests.
**Dependencies:** 2.2.
**Acceptance criteria:**
- `register` is idempotent (same bytes → same digest, no duplicate).
- Append-only: a second `register` of different bytes under the same logical class
  does not overwrite; both digests are retrievable.
- `getSchemaChain` returns leaf-to-root order.
- `isCompatible(A, B)` returns true iff B's fields are a superset of A's, same order,
  same prefix.
**Spec refs:** §12.1, §12.2.
**Session size:** medium.

### Task 7.3 — Schema resolution decision tree
**Scope:** Implement the §12.4 resolution order: local digest match → embedded
schema → registry lookup → defaults. Test each branch.
**Dependencies:** 7.2, 5.2.
**Acceptance criteria:**
- Local match → no registry call (assert registry not invoked).
- Mismatch → embedded schema used (assert).
- Embedded absent (synthetic test) → registry queried by digest.
- Registry miss → local schema + defaults, no exception.
**Spec refs:** §12.4.
**Session size:** medium.

---

## Phase 8 — Conformance

### Task 8.1 — Conformance test suite
**Scope:** Assemble the §9 conformance checks into a single runnable suite that a
third-party implementation could run against. Includes the canonical-encoding
property, the data-independence property (decode to named map without the class),
and the round-trip properties.
**Dependencies:** all prior.
**Acceptance criteria:**
- Data independence: a payload + schema decodes to a complete named field map with
  the originating class *absent from the classpath* (run in an isolated classloader
  without the fixture class).
- Canonical: identical values → identical bytes, across two independent encoder
  instances.
- All §9 numbered conformance points have a corresponding assertion.
**Spec refs:** §9, §3.11.
**Session size:** large.

---

## 🔒 Blocked Tasks — Need Open Questions Resolved First

These depend on field-level detail not yet in the spec (marked `[OPEN]`). Resolve
the spec question first, then they become normal Phase-4-style tasks.

### 🔒 B.1 — §7.3 DigestCodeSourceRecord
Needs the exact field layout of `DigestCodeSource` in the implementation.
**Unblock by:** filling in §7.3 with the real field list.

### 🔒 B.2 — §7.4 SCAP data objects
Needs STD-002 field-level detail for `AnalysisRequest`, `JarAnalysisReport`,
`SignedVerdict`, `RegistryVerdict`, `CrashReport`. (STD-002 v1.2 has the high-level
shape; the wire-field detail still needs confirming.)
**Unblock by:** completing §7.4 from the STD-002 `@AtomicSerial` classes.

### 🔒 B.3 — §7.5 PermissionGrant / DigestGrant
Needs the exact serial form of the grant types.
**Unblock by:** filling in §7.5.

### 🔒 B.4 — §7.7 Jini discovery/registration wire types (subset)
Several `[OPEN]` items: `getTrustBundle()` method name, `UnicastResponse`
construction for the DER client path, the `SslEndpoint` + `AtomicILFactory` stub
factory path. These need confirmation against the JGDMS source.
**Unblock by:** confirming the open items noted in §7.7 / §10.

### 🔒 B.5 — §4.4 Type discrimination (OID vs ENUMERATED)
The top-level wire-object type-discriminator question is still open. Needed before a
polymorphic top-level decoder can be finalised.
**Unblock by:** deciding the §4.4 discriminator scheme.

---

## Suggested Ordering Summary

```
Phase 0  →  Phase 1  →  Phase 2  →  Phase 3  →  Phase 4  →  Phase 5  →  Phase 6
                                                    │
                              Phase 7 (after 5.2) ──┘
                                                    │
                              Phase 8 (last)  ──────┘
```

Phases 1–3 are pure and have no external dependencies — they can be done entirely
offline and are the safest to run with the lightest review. Phase 4 onward touches
real `@AtomicSerial` semantics and warrants the closest attention, particularly
4.3 (namespace isolation) and 5.2 (embedded-schema decoding) — those encode the
rules that were corrected repeatedly during design, so verify the agent restates
them correctly before it writes code.

The blocked tasks (B.1–B.5) can be tackled in parallel with Phases 6–8 once their
spec sections are filled in, since they are additional wire types rather than
changes to the core engine.
