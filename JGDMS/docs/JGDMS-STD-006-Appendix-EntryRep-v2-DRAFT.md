# JGDMS-STD-006 Amendment — Outrigger EntryRep v2 Whole-Entry DER Record (DRAFT v0.1)

Status: DRAFT for parallel board review (canonical-form seat + adversarial seat).
Amends: `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` (ATOMIC DER Wire Format).
Companion SOW: `SOW-Entry-ATOMIC-DER-Migration.md` Part B (first unit); see also
`jgdms-outrigger-cel-filter-pushdown-design` memory.

This amendment specifies the **Outrigger** whole-entry storage/wire record that
replaces the v1 `EntryRep.values : MarshalledInstance[]` array with one canonical
DER record. It is the first unit of the CEL filter-pushdown work: it puts field
**names+types** onto the wire (in the entry's own schema chain) so a later unit can
resolve name-referencing filter predicates server-side, and it collects the schema
dedup win. **The load-bearing contract is per-field byte-equality template matching**
(§A.4); everything else in this amendment exists to preserve it.

Migration is a **flag-day** (RATIFIED, Peter): a v2-born space reads and writes v2
only. There is no v1 read-back and no migration tool. This is admissible because the
ATOMIC DER wire format is unreleased (no v1-DER store can exist in the field) and a
v1→v2 conversion is impossible in principle (v1 carries no field names). Loud guards
enforce the flag-day (§A.8).

---

## A.0 Relationship to §7.7 (the sidecar `EntryRecord`)

STD-006 v0.13 §7.7.2 already drafts an `EntryRecord`/`EntryTemplate` for the
**Reggie / non-JVM sidecar Registrar** cross-runtime case: `@SerialEntry` attribute
sets whose fields are §7.6 **scalars**, matched by a non-JVM peer as a byte-string
comparison, with a single `schemaHash` and no dedup table. That record is
**scalar-only** and is retained unchanged for that subsystem.

The Outrigger `EntryRepV2Body` defined here is a **distinct, richer record** for a
different subsystem (the JavaSpace store):

- Outrigger entry fields may hold **arbitrary object values** (any `@AtomicSerial`
  graph, boxed primitives, `String`, `byte[]`, arrays, enums, collections), not only
  §7.6 scalars.
- It carries a **`schemaTable`** that de-duplicates schema chains across fields (the
  ~4.3% dedup win, §A.7), which the flat §7.7.2 `fieldValues` list does not.
- Its per-field slice carries a **`valueSchemaDigest`** (the value's own runtime
  class-chain digest), so matching distinguishes value class identity (§A.4.4).

`EntryRepV2Body` and §7.7.2 `EntryRecord` do **not** interoperate and are never
byte-compared against each other; they are two records for two subsystems. **[BOARD
Q1]** whether to relocate §7.7.2 under an explicit "sidecar" heading to remove the
name-adjacency confusion, or to fold the Outrigger record into §7.7 as §7.7.2a. This
amendment keeps the brief's names (`EntryRepV2Body`, `SchemaEntry`, `FieldSlice`).

---

## A.1 Grammar

```asn1
-- Outrigger whole-entry storage/wire record (JavaSpace).
EntryRepV2Body ::= SEQUENCE {
    version            INTEGER (2),                  -- exactly 2; MANDATORY, no DEFAULT (§4.5).
                                                     -- A decoder MUST reject any other value LOUDLY
                                                     -- (flag-day skew guard, §A.8); 1 never appears
                                                     -- on the wire (v1 was never DER).
    entrySchemaDigest  OCTET STRING (SIZE(32)),      -- SHA-256(DER(leaf EntrySchemaRecord)) of the
                                                     -- entry's own declared class chain (§A.3).
                                                     -- Routing/identity only; OUTSIDE the matched
                                                     -- region (§A.4).
    schemaTable        SEQUENCE (SIZE(0..maxSchemaTable)) OF SchemaEntry,
                                                     -- ASCENDING by digest, NO duplicates (§A.2).
                                                     -- MUST contain the entry chain's leaf digest
                                                     -- AND every non-empty valueSchemaDigest any
                                                     -- FieldSlice references (§A.2 completeness).
    fields             SEQUENCE (SIZE(0..maxFields)) OF FieldSlice }
                                                     -- FieldComparator order (super-first, then
                                                     -- alphabetical within a class); positionally
                                                     -- aligned to the entry chain's field list (§A.3).

SchemaEntry ::= SEQUENCE {
    digest      OCTET STRING (SIZE(32)),             -- SHA-256(DER(leaf record of chainBytes)).
    chainBytes  OCTET STRING (SIZE(1..maxChainBytes)) }
                                                     -- Concatenated leaf-first DER of each
                                                     -- AtomicSerialSchemaRecord / EntrySchemaRecord
                                                     -- in the chain (identical framing to
                                                     -- MarshalledInstanceRecord.schemaBytes, §7.8).

FieldSlice ::= CHOICE {
    absent  [0] IMPLICIT NULL,                       -- entry-null field OR template wildcard (§A.4.5).
    value   [1] IMPLICIT SEQUENCE {
        valueSchemaDigest  OCTET STRING (SIZE(0..32)),  -- the value's runtime class-chain leaf digest,
                                                        -- OR a 0-length OCTET STRING when the value has
                                                        -- no @AtomicSerial class in its hierarchy
                                                        -- (String/boxed/byte[]/enum/collection/bare
                                                        -- proxy — the self-describing payload case, §A.4.2).
        payload            OCTET STRING (SIZE(0..maxSlicePayload)) } }
                                                        -- Canonical ATOMIC DER of the value ALONE:
                                                        -- byte-identical to a per-value DER
                                                        -- MarshalledInstance's payloadBytes (§A.4.1).
```

Ceilings are named in §A.6 and registered against §4.5.

---

## A.2 Canonicity of `schemaTable`

The `schemaTable` is a **content-addressed set**, encoded canonically so two
independent encoders of the same entry produce byte-identical bodies:

1. **Sort:** entries appear in **ascending lexicographic order of `digest`** (32-byte
   unsigned big-endian byte comparison). A decoder MUST verify strict ascending order.
2. **No duplicates:** two entries with equal `digest` are a decode error. Because the
   order is strictly ascending, "not strictly increasing" and "duplicate" are the same
   check.
3. **Completeness (decode-time):** every `digest` referenced by a `value` FieldSlice's
   **non-empty** `valueSchemaDigest`, **and** the `entrySchemaDigest`, MUST be present
   in the `schemaTable`. A referenced digest not present is a decode error. (An
   empty `valueSchemaDigest` references nothing — the payload is self-describing.)
4. **No orphans (encode-time SHOULD; decode-time MAY):** an encoder MUST NOT emit a
   `SchemaEntry` that is neither the entry chain nor referenced by any slice. A decoder
   MAY reject an orphan table entry; the reference implementation rejects it
   (fail-closed, tightens canonicity so the body is a pure function of the entry).
5. **`digest` binds `chainBytes`:** for each `SchemaEntry`, `digest` MUST equal
   `SHA-256(DER(leaf record of chainBytes))`, and `chainBytes` MUST decode as a valid
   schema chain under §7.8 (leaf-first, `parentSchemaHash` cross-checked, chain
   ceilings metered, no dangling terminal parent hash). A `digest`/`chainBytes`
   mismatch is a decode error (a sender-supplied digest authenticates nothing until
   verified — §7.8 "schemaDigest is a routing hint, verified before use").

**Why sort+dedup by digest and NOT by table index in the slices (the crux, §A.4):**
the slice references the schema by its **content digest**, which is a pure function of
the value's class. It never references the `schemaTable` **position**. Position would
vary with what else is in the table (i.e. with the other fields and the entry class),
making a field's bytes context-dependent and breaking matching. See §A.4.3.

---

## A.3 `EntrySchemaRecord` — the entry's own declared-class chain (names on the wire)

The entry's declared class chain travels as a chain of `EntrySchemaRecord` (§7.7.1),
generated by **`EntrySchemaGenerator`** (the single deterministic reflective source,
§A.5). Each record carries, per class namespace, one `EntryWireFieldDef {wireName,
wireType}` for each **usable field** (§A.5) of that class, in `FieldComparator` order.
`entrySchemaDigest` = the leaf record's `SHA-256(DER(EntrySchemaRecord))`, with
recursive 32-byte `superclassHash` (§7.7.1, `hashAlgorithm = 1` / ATOMIC DER).

The entry chain exists to carry **field names and declared types** for later
server-side filter name-resolution and projection, and to give the whole entry a
content-addressed identity (`entrySchemaDigest`). It is carried for names/identity
**only**. It is **never woven into a field value's payload** (§A.4.3).

**Positional alignment.** `EntryRepV2Body.fields[i]` corresponds to the i-th usable
field in the flattened `FieldComparator` order of the entry's declared class chain
(super-first, then alphabetical within a class) — the same order v1 used for
`values[]`. A subclass adds its fields **after** all superclass fields; a same-named
field declared in two different namespaces occupies two distinct positions (no
shadowing collapse). The count `fields.size()` MUST equal the total usable-field count
implied by the entry chain, else decode fails (§A.8 skew guard).

---

## A.4 THE MATCH CONTRACT — per-field byte-equality (normative, load-bearing)

`EntryRep.matches(other)` (outrigger-dl) compares fields **positionally by slice
bytes**. Wildcard slices (`absent`) are skipped; every non-wildcard template slice
MUST be **byte-equal** to the corresponding stored slice. This preserves v1 semantics
(v1 compared `MarshalledInstance.equals`, i.e. payload bytes only). For v2 to be
correct, a `FieldSlice`'s encoding MUST satisfy:

### A.4.1 A `FieldSlice` is a pure function of the field value ALONE

A non-null field's slice is:

```
value [1] { valueSchemaDigest = D(v), payload = P(v) }
```

where `D(v)` and `P(v)` depend on **nothing** except the value `v` itself:

- `P(v)` = the canonical ATOMIC DER of `v` alone. It is **byte-identical** to the
  `payloadBytes` of a per-value DER `MarshalledInstance` built from `v`
  (`ObjectCodec.encodeHierarchy` for an `@AtomicSerial` graph; a self-describing,
  context-tagged object-stream item for a `String`/boxed/`byte[]`/enum/collection/bare
  proxy). This is exactly the byte-string v1's `matches()` compared, so matching parity
  is preserved by construction.
- `D(v)` = the leaf digest of `v`'s **runtime** class chain
  (`SHA-256(DER(leaf AtomicSerialSchemaRecord of v.getClass()))`), or a **0-length**
  OCTET STRING when `v` has no `@AtomicSerial` class in its hierarchy (the
  self-describing payload case — the value's type is already inside `P(v)`).

`D(v)` and `P(v)` depend on: **NOT** the enclosing entry, **NOT** its declared class
chain, **NOT** the `entrySchemaDigest`, **NOT** the `schemaTable`, **NOT** the other
fields, **NOT** the template-vs-stored role. Therefore the **same value produces
byte-identical slice bytes in every context** — template role or stored role, this
entry class or a subclass, encoder instance A or encoder instance B (DER canonicity +
deterministic schema generation ⇒ same-class-same-digest across senders).

### A.4.2 The two payload shapes

| value's runtime hierarchy | `valueSchemaDigest` | `payload` |
|---|---|---|
| contains an `@AtomicSerial` class | 32-byte leaf digest of the value's class chain | `ObjectCodec.encodeHierarchy(v, chain)` |
| no `@AtomicSerial` class (`String`, `Integer`, …, `byte[]`, enum, collection, bare proxy) | 0-length | self-describing DER object-stream item (context-tagged, §15.2) |

Both shapes are produced by encoding `v` through the identical `net.jini.io.Marshalled­Instance` DER path v1 used, then lifting out `(schemaDigest, payloadBytes)`. The
value's schema chain bytes (when non-empty) become the `SchemaEntry.chainBytes` keyed
by `D(v)` in the `schemaTable`.

### A.4.3 REJECTED TRAP — no chain-relative references in payloads

An encoder MUST NOT encode a field payload that references the shared entry chain or
the `schemaTable` (e.g. a class-index into the table, or a field payload whose bytes
depend on the entry's `superclassHash`). Doing so would make a `Double(1.0)`'s slice
bytes differ when the field is declared in class `T` versus a subclass `S` (different
entry chains ⇒ different indices/hashes), **breaking matching between a `T`-typed
template and an `S`-typed stored entry**. The entry chain is carried for names/identity
only; a value's payload is self-contained.

### A.4.4 Intended semantic delta vs v1 (DOCUMENT, not a bug)

The matched region is the whole `value [1]` SEQUENCE, i.e. `(valueSchemaDigest ‖
payload)`, **not payload alone**. v1 compared payload only (`MarshalledInstance.equals`
excludes schema/digest/annotation). Consequences:

- For values with **no** `@AtomicSerial` class (`String`, boxed primitives, `byte[]`,
  enum, collection): `valueSchemaDigest` is empty for both sides, and the value's type
  is already inside the self-describing `payload`. **No behavioural change** — an
  `Integer(5)` and a `Long(5)` had different self-describing payloads under v1 too and
  still do not match.
- For `@AtomicSerial` values: v2 additionally requires the **runtime value class chain
  digest** to match. Two values of **different** `@AtomicSerial` classes that happen to
  encode to identical `payload` bytes matched in v1 (payload-only) but do **not** match
  in v2. This is an intended **tightening** — it adds value-class identity to the match,
  restoring the intuitive "same value ⇒ same class" expectation. **[BOARD Q2]** confirm
  this tightening is acceptable; it is stricter than v1 and cannot produce a v1 match
  that v2 rejects **for equal-class values** (the only affected case is
  different-class-coincidental-payload, which is vanishingly rare and arguably a v1 bug).

### A.4.5 Wildcard / null parity (bit-for-bit v1)

There is **one** `absent [0] NULL` marker. Its meaning is role-dependent, exactly as
v1's `values[i] == null`:

- **Template side** (`this` is the template): `absent` = **wildcard** — skipped in
  `matches()` (matches any stored value, including a stored null).
- **Stored side** (`other`): `absent` = **null field value** — matches only a template
  wildcard (a non-wildcard template slice is a `value [1]`, never byte-equal to a
  stored `absent`, so a null stored field fails a non-wildcard template — v1 parity).

The `absent` marker is context-free (it is the same two bytes everywhere), so it also
satisfies §A.4.1.

### A.4.6 The compared region excludes table/identity

`matches()` and `equals()` compare **only** the `fields` slices (positionally).
`version`, `entrySchemaDigest`, and `schemaTable` live **outside** the compared region.
This mirrors v1 comparing `MarshalledInstance` payload bytes while excluding the schema,
digest, and codebase annotation. Implementations MUST cache the per-slice canonical
bytes (`byte[][] sliceBytes`) at build/decode time and compare those, so `matches()`
never re-parses the body and never touches the table (which would reintroduce context).

---

## A.5 `EntrySchemaGenerator` — the single deterministic reflective source

`EntrySchemaGenerator` produces the entry's `EntrySchemaRecord` chain and the per-field
declared-type list. It is **deterministic** (same class ⇒ byte-identical chain ⇒
identical `entrySchemaDigest` on every JVM) and **`ClassValue`-cached**. It MUST be the
**single** reflective view of an entry class used by both record-building and (later)
filter authoring — there is no second reflective enumeration of entry fields.

**Field rules (identical to v1 `EntryRep`, the single source):**

- **Usable field** = a `public`, non-`static`, non-`transient`, non-`final` field
  (`EntryRep.usableField`). A `public` non-ignored field of **primitive** type is
  illegal in an Entry (`IllegalArgumentException`) — Entry fields are reference types.
- **Order** = `FieldComparator`: superclass fields before subclass fields; within one
  declaring class, ascending by field name (`String.compareTo`).
- **Namespace** = declaring class. A field named `x` in class `A` and a field named `x`
  in subclass `B` are two distinct usable fields at two distinct positions.
- `@SerialEntry` classes use `entryForm()`'s `EntryWireField[]` (wireName/wireType) in
  declared order instead of reflected fields (STD-005); the generator uses that list
  verbatim so `@SerialEntry` and reflective entries share one code path downstream.

**Pinned declared-Java-type → `wireType` mapping (entry-schema field types).** The
`wireType` in each `EntryWireFieldDef` describes the **declared** field type (for later
name-resolution/type-checking), and reuses `SchemaGenerator.toWireType` verbatim (one
source of truth for the type table). Entry fields are reference types, so:

| Declared field Java type | `wireType` string |
|---|---|
| `Boolean` | `boolean` |
| `Byte` | `byte` |
| `Short` | `short` |
| `Integer` | `int` |
| `Long` | `long` |
| `Float` | `float` |
| `Double` | `double` |
| `Character` | `char` |
| `String` | `java.lang.String` |
| `Class` | `java.lang.Class` |
| `byte[]` | `byte[]` (OCTET STRING; NOT `array:byte`) |
| an `@AtomicSerial` class | `@AtomicSerial` |
| an `enum` type | `enum:<fqcn>` |
| single-dim `T[]` (T ≠ byte) | `array:<componentWireType>` (`array:@AtomicSerial:<fqcn>` for `@AtomicSerial` components) |
| `Collection`/`Map` subtype | discipline token from `CollectionWireTypes` (`set:`/`orderedset:`/`list:`/`map:`/`orderedmap:` + element/key/value derived by the element-derivation rule over the declared generic signature; `any` when unresolvable) |
| an interface or abstract class (a polymorphic slot, e.g. `Number`, `Object`, a service interface) | `@AtomicSerial` (the runtime value's concrete class travels in its own slice's `valueSchemaDigest`/chain) |
| a concrete non-`@AtomicSerial`, non-listed type | **rejected** at encode: `MarshalException` (the value cannot be canonically DER-encoded — §3.6 loud-failure rule) |

Multi-dimensional arrays and `array:byte` are rejected (as in `SchemaGenerator`).
Note the declared type (e.g. `Number` → `@AtomicSerial` slot) is decoupled from the
**runtime** value class captured in the slice's `valueSchemaDigest`; the entry-schema
`wireType` is declared-type metadata, never part of a value payload (§A.4.3).

---

## A.6 Ceilings (fail-closed at decode, G7/G10)

All enforced at decode **before allocation**, metered during the relevant loop (the
ceiling-breaching element is the last one parsed). Registered against §4.5.

| Name | Value | Surface |
|---|---|---|
| `version` | exactly `2` | `EntryRepV2Body.version`; any other value rejected LOUDLY (§A.8) |
| `maxFields` | 65535 (existing §4.5) | `EntryRepV2Body.fields` count |
| `maxSchemaTable` | **256** [PROPOSED — pin with Peter] | `EntryRepV2Body.schemaTable` entry count. Rationale: an entry has ≤ `maxFields` fields but distinct value **classes** are far fewer; 256 distinct schema chains per entry is generous and bounds the sort/dedup work and the completeness cross-check. |
| `maxChainBytes` | 65536 (existing §4.5) | each `SchemaEntry.chainBytes` (one chain), and the entry chain |
| `maxChainRecords` | 64 (existing §4.5) | records within any one `chainBytes` chain |
| `maxSlicePayload` | **1048576** (1 MiB) [PROPOSED — pin with Peter] | `FieldSlice.value.payload` length. Rationale: a single entry field value; 1 MiB bounds a hostile payload while admitting realistic large fields (small blobs). |
| `maxBodyBytes` | **8388608** (8 MiB) [PROPOSED — pin with Peter] | total `EntryRepV2Body` DER length, checked before/at the outer SEQUENCE. Backstops the product of the per-element ceilings so a body cannot be enormous even within per-element bounds. |

`valueSchemaDigest` is 0 or exactly 32 bytes; `entrySchemaDigest` and
`SchemaEntry.digest` are exactly 32 bytes; any other length is a decode error.

---

## A.7 Dedup (the win; measured, not assumed — G13)

v1 stored each field as its own `MarshalledInstance`, each carrying a **full schema
chain**. Two fields of the same value class carried two copies of that class's chain.
v2 stores each distinct value-class chain **once** in the `schemaTable` (keyed by
digest); slices carry only the 32-byte digest. The measured saving is reported in the
implementation notes (target: at least the ~4.3% U1c baseline; **G13: measure the
actual number on a representative entry, do not assume**).

---

## A.8 Flag-day guards (loud, never silent)

- **`version` check.** A decoded `EntryRepV2Body.version ≠ 2` is rejected with a loud
  `DerException`/`InvalidObjectException` naming the version — never a silent skip.
- **Recovery guard.** Snapshot/log recovery (snaplogstore) refuses a non-v2 stored
  `EntryRep` **loudly** (an explicit version probe on the stored form), never a silent
  best-effort skip that would drop entries.
- **`@AtomicSerial` skew guard.** The `EntryRep` serial form changes (new fields;
  `values : MarshalledInstance[]` removed). An old-proxy/new-service (or vice-versa)
  skew therefore fails **loudly** at `GetArg` decode (a missing/renamed serial field),
  not silently. `matchAnyRep` (never marshalled) gets a schema-less v2 form and a
  comment so nobody wires it into the DER guard path.
- **Field-count guard.** `fields.size()` MUST equal the usable-field count implied by
  the entry chain (§A.3); a mismatch is a loud decode error.
- **Serial-schema compat gate.** The golden serial-schema record for `EntryRep`
  changes; the compat gate **fails by design** and is updated as an explicit,
  commented, reviewed diff (the deliberate reviewed act — not a silent regeneration).

---

## A.9 Adversarial decode — all fail-closed (G7/G10, adversarial seat)

A conformant `EntryRepV2Body` decoder MUST reject, before acting on any partial result:

1. `version ≠ 2`.
2. `entrySchemaDigest` / `SchemaEntry.digest` not exactly 32 bytes; `valueSchemaDigest`
   neither 0 nor 32 bytes.
3. `schemaTable` not strictly ascending by `digest` (catches unsorted **and**
   duplicate).
4. Any `SchemaEntry` whose `digest ≠ SHA-256(DER(leaf of chainBytes))`, or whose
   `chainBytes` is not a valid §7.8 chain (bad parent-hash cross-check, dangling
   terminal parent, chain-ceiling breach).
5. A `value` slice whose non-empty `valueSchemaDigest` is **absent** from the
   `schemaTable` (completeness), or the `entrySchemaDigest` absent from it.
6. An orphan `schemaTable` entry (referenced by nothing) — reference impl rejects.
7. Any ceiling breach (§A.6), metered before allocation.
8. **Trailing bytes** after the outer SEQUENCE, or after any inner SEQUENCE/CHOICE
   (fail-secure; the record is exactly its declared components).
9. A `FieldSlice` CHOICE tag other than `[0]`/`[1]`; a `value` slice with the wrong
   inner component count/tags.

A decoder MUST NOT attempt to reconstruct the entry, run `matches()`, or index the
entry from a body that fails any of the above.

---

## A.10 Determinism / cross-JVM canonicity (argument)

`EntryRepV2Body` is a pure function of the entry (its class + field values):

- `EntrySchemaGenerator` is deterministic per class (§A.5) — same class ⇒ byte-identical
  entry chain ⇒ identical `entrySchemaDigest` on every JVM (`SchemaGenerator`/
  `SchemaChain` determinism, already established in STD-006 §7.8).
- Each slice's `(D(v), P(v))` is DER-canonical for `v` (§A.4.1) and independent of
  context.
- `schemaTable` order is total (ascending digest) and dedup is exact; slice order is
  the total `FieldComparator` order.

Therefore two independent encoders, on any two DER-capable JVMs, produce byte-identical
bodies for the same entry — and, more narrowly but load-bearingly, byte-identical
**slices** for the same field value regardless of enclosing entry or role (§A.4).
```
