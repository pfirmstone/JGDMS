# Design memo: the STD-006 (DER) closed-subset wire type model, the element-derivation RULE, and the self-describing "any" element form

**Status:** DESIGN / RULE for board review. **No code is proposed for merge by this memo.** It
formalises a type MODEL, an element-derivation RULE (no annotations), a self-describing "any"
element form, and names the one enabling code hook. A board reviews this before any codec change,
exactly as the collection-*ordering* determinism rule (`docs/der-collection-ordering-research.md`)
was documented, board-reviewed, and only then built.

**Author:** design pass, 2026-07-05.
**Parallels:** `docs/der-collection-ordering-research.md` (the determinism discriminator this
mirrors — a RULE keyed on the *declared type*, no annotation, no extra schema token).
**Anchors read (trunk `c0e20eb30`):** STD-006 v0.13 §3.8 ("Values and Bounds, Never Behaviour" +
"Encounter order and serialized equality" + "Wire tag and decoder obligations"), §7.6 ("Substituted
Standard Types" — "Collections are NOT in this catalogue", the six discipline ASN.1 productions),
§4.5 (`maxCollection` = 65536).
**Codec inspected (trunk):**
`jgdms-der/src/main/java/au/net/zeus/jgdms/der/getarg/CollectionWireTypes.java` (the built
`set:`/`bag:`/`orderedset:`/`list:`/`map:`/`orderedmap:` token grammar + `disciplineFor(Class)` +
`OCTET_SORT`), `schema/SchemaGenerator.java` (`toWireType`, and the collection builders
`collectionWireType`/`mapWireType` that today take a **developer-supplied** element wire-type
string), `org.apache.river.api.io.AtomicSerial.SerialForm` (holds only `Class<?> type`;
`getType()` returns the RAW class — the mechanism gap).
**Cross-language evidence:** `docs/der-rust-collection-mapping.md` (branch
`der-rust-collection-mapping`, not yet on trunk; maps the same §3.8 discipline set onto Rust
collections) — cited as corroboration that the closed model is language-neutral.
**Feasibility verified empirically** (JDK reflection probe, results in §7.1): `Field.getGenericType()`
recovers `Set<Foo>`'s `Foo`, nested `Map<String,List<Set<Foo>>>` in full, wildcard bounds, and
distinguishes raw / `Object` / type-variable — i.e. **erasure erases instances, not declarations.**

---

## 0. The problem, restated precisely

A collection field's wire token needs the ELEMENT type. The built grammar
(`CollectionWireTypes`) already encodes it — `set:<elemWT>`, `list:<elemWT>`,
`map:{<keyWT>}{<valWT>}`, etc. — and the *ordering discipline* half is solved: `disciplineFor(Class)`
derives preserve-vs-canonicalise purely from the declared collection **class**
(`HashSet`→`set:`, `LinkedHashSet`→`orderedset:`, `TreeMap`→`orderedmap:`…), a pure function of the
type, no annotation. See `SchemaGenerator.java` lines 415–435 and `CollectionWireTypes.setToken`.

The *element type* half is **not** solved. Read the comment the built code carries at
`SchemaGenerator.java:393`:

> *"Java generics are erased, so a declared field type of `Set<X>` gives `Set.class` with no
> element type at runtime — `toWireType` alone cannot derive the element wire-type. … The developer
> supplies the element/key/value wire-type(s)."*

So today a plain `Set<Foo>`/`Map<K,V>` field cannot be auto-wired: the schema generator has no way
to fill `<elemWT>` and the developer would have to hand it in. That is the **deferred auto-wiring
item** this memo resolves. The premise "erasure means we can't get `Set<X>`'s `X`" is **the bug in
the premise**, not a real limitation — see §1.

**Peter's directive (governing this design):** *do not add a new annotation.* A `@DerElement(Foo.class)`
would just restate type information the developer already declared as `Set<Foo>`, and any place a
developer restates a fact is a place they can get it wrong (the two drift; the annotation lies). Use
a RULE keyed on the declaration the developer already writes — exactly as the ordering discriminator
keys on the declared class, not on an `@Ordered` annotation.

---

## 1. The key insight (verified): erasure erases INSTANCES, not DECLARATIONS

Type erasure removes generic type arguments from **runtime instances**: `new HashSet<Foo>()` and
`new HashSet<Bar>()` are the same class at runtime, and `someSet.getClass()` cannot tell you `Foo`.
That is the erasure everyone knows.

But a **declaration** — a field `Set<Foo> tags;` — retains `Foo` in the class file's generic
**Signature** attribute (JVMS §4.7.9), and the reflection API reads it back:

- `java.lang.reflect.Field.getGenericType()` returns a `java.lang.reflect.Type`. For `Set<Foo>` it
  is a `ParameterizedType` whose `getActualTypeArguments()[0]` is `Foo`. **Not erased.**
- This is exactly how Jackson (`TypeFactory`/`JavaType`), Gson (`TypeToken`/`$Gson$Types`), and
  Spring (`ResolvableType`) resolve generic element types for `Set`/`List`/`Map` fields. It is
  established, portable JDK behaviour, not a trick.

The element type is therefore **derivable BY RULE from the declared generic type the developer
already writes** — no annotation, no restated fact. The full empirical confirmation (a reflection
probe over `Set<String>`, `Map<String,List<Set<Foo>>>`, wildcards, raw, `Object`, and a
type-variable field) is in §7.1; every claim below is backed by that probe.

The only thing genuinely unavailable is a *type variable* in a generic `@AtomicSerial` class
(`class Box<T> { Set<T> vals; }` → the arg is a `TypeVariable`, not a concrete type). That is a true
boundary, not an erasure artefact — and an annotation **could not fix it either** (there is no
concrete type to name at the declaration site). It resolves, by rule, to "any" (§4, §5).

---

## 2. The closed-subset type MODEL (PINNED NORMATIVE — the STD-006 "Wire Type Model")

> **Board disposition (adopted).** This section is the **normative type algebra** the STD-006 spec
> is to carry as a new **"Wire Type Model"** section (pinning target #1). It is written as the
> authoritative intent, not a research sketch. Two artefacts are to be added *at implementation*
> (flagged, not authored here): **(P1)** the STD-006 spec §-section stating this model, the exclusion
> boundary, the distinct-tag/§4.5 + `SIZE`/depth-bound discipline, and the `Any` tag registry; and
> **(P2)** the `.asn1` module's `AnyElement` **CHOICE** production (§4.1) plus the `Any` tag-registry
> comment block. The `Any` context tags **are** the registry — see §4.5.

STD-006's wire type system is a **CLOSED SUBSET**, closed under recursive composition — a type algebra
over three base categories plus the composition forms and the `Any` self-describing form:

```
WireType  ::=  Scalar
            |  AtomicSerialObject          -- a schema-digest-identified @AtomicSerial record
            |  Collection(WireType…)       -- set/bag/orderedset/list/map/orderedmap over WireType(s)
            |  Any                          -- the self-describing CHOICE over the three above (§4)

Scalar             ::= boolean | byte | short | int | long | float | double | char
                     | java.lang.String | byte[]                      -- the built scalar set (WireTypes/ObjectCodec)
AtomicSerialObject ::= "@AtomicSerial"                                -- concrete class travels in the embedded schema chain
Collection(E)      ::= "set:"E | "bag:"E | "orderedset:"E | "list:"E  -- element E is itself a WireType
Collection(K,V)    ::= "map:{"K"}{"V"}" | "orderedmap:{"K"}{"V"}"     -- K, V each a WireType, resolved independently
Any                ::= [n] IMPLICIT/EXPLICIT CHOICE over Scalar | AtomicSerialObject | Collection  (§4)
```

**Closure.** The set is closed under `Collection`: `Set<Map<String,List<Foo>>>` is a legal
`WireType` because each nesting level is again one of the categories. Recursion **bottoms out** at a
scalar, an `@AtomicSerial` object, or `Any`. This is the "closed under composition" property.

**Normative discipline (pinned).** Every collection carries a `SIZE(0..maxCollection)` bound (§4.5,
`maxCollection`=65536) *and* every recursion — including every `Any`→collection and
`Any`→`atomicSerialObject` step — is bounded by a decoder `MAX_NESTING` depth (fences, §4.3). Every
distinct category is a distinct context tag (STD-006 §4.5 distinct-tag discipline; the `Any` registry,
§4.5 of this memo), so the type of an `Any` element is decided by its tag, never inferred. These are
decoder obligations, not encoder conveniences.

### 2.1 The exclusion boundary (why the subset is closed — the "less is more" discipline)

The model **deliberately excludes**, and a conformant schema/encoder MUST NOT admit:

1. **Raw arbitrary `Object` graphs.** A field typed `Object` (or `Set<Object>`, `List<Object>`) has
   no closed-subset structural type. It is **not** silently promoted to "carry anything". It is
   admissible *only* through the `Any` form (§4), which is itself constrained to the closed subset —
   an `Any` payload is still one of {scalar, @AtomicSerial object, collection}, never an arbitrary
   graph. (Contrast: an interface/abstract-typed field is *not* a raw `Object` — it is a polymorphic
   `@AtomicSerial` slot whose concrete class self-identifies; that is already handled by
   `toWireType` returning `"@AtomicSerial"`, `SchemaGenerator.java:377`.)
2. **Arbitrary `Serializable`.** JOSS's "any object with a class descriptor" is exactly what STD-006
   refuses: it serialises the *implementation*, defeats cross-language and value-equality, and is a
   gadget surface. Only the `@AtomicSerial` closed record form is admitted.
3. **Cycles.** The model is a finite tree (DAG-free by construction): no back-references, no object
   identity on the wire. STD-006 §3.7 already states acyclicity; the type model is its structural
   counterpart.

These three exclusions are **what make three properties simultaneously achievable** — cross-language
production/consumption, byte-level value-equality (§7.7.2 Entry matching, §7.8 digests), and DER
canonicity. Admitting any of the three back would forfeit at least one. This is the same discipline
recorded in the ordering memo's §0.1 ("DER restores the value/serial-equality correspondence that
JOSS breaks"): the closed subset is the price and the payoff.

### 2.2 Cross-language soundness

The three base categories map cleanly to non-JVM type systems, which is why the model is
language-neutral (a first-class STD-006 goal, v0.13 §7.6 language-neutrality note). Scalars are
primitive DER (INTEGER/BOOLEAN/UTF8String/OCTET STRING/IEEE-754 octets). `@AtomicSerial` objects are
schema-digest-addressed records — a content address, not a Java class name (the class name is a
label; the digest is the identity). Collections are the six §3.8 ASN.1 productions
(`CanonicalSet`/`CanonicalMultiset`/`CanonicalMap`/`OrderedSetField`/`ListField`/`OrderedMapField`).
`docs/der-rust-collection-mapping.md` (branch `der-rust-collection-mapping`) already demonstrates the
collection layer mapping onto Rust's `BTreeSet`/`HashSet`/`Vec`/`BTreeMap`… under the same
determinism discipline — evidence the model survives translation. The `Any` tag set (§4.5) is
specified as a small language-neutral enumeration for the same reason.

---

## 3. The element-derivation RULE (no annotation)

**Governing rule.** For a collection field, the element wire-type is `rule(E)` where `E` is the
element type recovered from the field's **declared generic Type** (`Field.getGenericType()`), applied
**recursively**. For a map, the key and value types are recovered and resolved **independently** as
`rule(K)` and `rule(V)`. No annotation is read; the declaration the developer already wrote *is* the
signal, precisely as the declared collection *class* is the signal for the ordering discipline.

`rule(t)` is defined by cases on the reflected `Type` `t`:

| Reflected `Type` of the element | Recovered element | `rule(t)` = |
|---|---|---|
| `Class` that is a scalar (`String`, boxed prim, `byte[]`…) | that class | the scalar wire-type (`toWireType`) |
| `Class` that is `@AtomicSerial` (or interface/abstract/registered-serializer) | that class | `"@AtomicSerial"` (polymorphic slot; concrete class self-identifies via schema digest) |
| `Class` that is an `enum` | that class | `"enum:<name>"` |
| `Class` that is an array `T[]` | that class | `"array:<comp>"` (arrays are reified — see §3.3) |
| `ParameterizedType` `C<…>` where `C` is a collection/map | recurse | `rule` on the nested collection (§3.1) |
| `WildcardType` `? extends B` (upper bound `B ≠ Object`) | `B` | `rule(B)` — the **upper bound** (§3.2) |
| **anything unresolvable** (raw, `?`/`? extends Object`, `Object`, `? super X`, `TypeVariable`) | — | **`Any`** (§3.4, §4) |

The rule is **total**: every field resolves either to a concrete closed-subset wire-type or to `Any`.
It never asks the developer for input and never emits an error for "can't tell" — "can't tell"
*is* the well-defined `Any` outcome.

### 3.1 Concrete resolvable element → `rule(E)`, recursively

`Set<Foo>` → `set:@AtomicSerial` (if `Foo` is `@AtomicSerial`) or `set:java.lang.String` (scalar).
`List<Integer>` → `list:int`. `Map<String,Foo>` → `map:{java.lang.String}{@AtomicSerial}`. The
recursion is the same tree-walk `toWireType` already does for arrays, extended to read the
`ParameterizedType` arguments instead of `Class.getComponentType()`.

Deeply nested generics resolve fully because `getGenericType()` returns the whole nested
`ParameterizedType` tree (verified §7.1): `Map<String,List<Set<Foo>>>` →
`map:{java.lang.String}{list:set:@AtomicSerial}`. Each level's discipline still comes from that
level's declared class (`disciplineFor` on the raw type of each `ParameterizedType`), so a
`LinkedHashMap<String, HashSet<Foo>>` yields `orderedmap:{java.lang.String}{set:@AtomicSerial}` — the
outer map preserves (insertion), the inner set canonicalises. Discipline and element-type derivations
compose independently and orthogonally.

### 3.2 Bounded wildcard `? extends B` → `rule(B)` (upper bound)

A field `Set<? extends Shape>` declares "elements are some subtype of `Shape`". The **structural**
element type is `Shape` (the upper bound), so the wire-type is `rule(Shape)` — typically
`set:@AtomicSerial` (`Shape` being an interface/abstract `@AtomicSerial` slot). Concrete subtype
elements (`Circle`, `Square`) **self-identify at the value level** via their `@AtomicSerial` schema
digest in the embedded chain — polymorphism is already handled by the existing "@AtomicSerial"
polymorphic-slot mechanism (`SchemaGenerator.java:377`, and `encodeNested` using `value.getClass()`).
So the upper bound is exactly the right structural type: it names the slot; the digest names the
instance. This is **not** the `Any` fallback and must not be — a `Set<? extends Shape>` retains full
schema-digest coverage of `Shape` and its subtypes.

*(Verified §7.1: `Set<? extends Number>` → `WildcardType`, `getUpperBounds()==[Number]`,
`getLowerBounds()==[]`.)*

### 3.3 Arrays are reified — the contrast with collections

Arrays are **not** erased: `Foo[].class.getComponentType()` returns `Foo` at runtime, and
`SchemaGenerator.toWireType` already handles `array:<comp>` from the raw `Class` alone (lines
320–347). So an `array:`-typed field needs no generic-signature reading; the component is reified.
`Set<Foo[]>` composes: the element is the reified array `Foo[]`, so `rule` = `set:array:@AtomicSerial:Foo`.
`Foo[]` where the array itself is the field is unchanged from today. The distinction to state in the
spec: **collections carry their element type in the generic *signature* (read by rule §3); arrays
carry it in the reified *component type* (read from the `Class`).** Both are declaration-time, neither
is instance-erased; they are simply read through different reflection doors.

Arrays-of-collections (`Set<Foo>[]`) — a field whose reified component is itself a parameterised
collection — reflect as a `GenericArrayType` whose `getGenericComponentType()` is the
`ParameterizedType Set<Foo>`. `rule` recurses into it → `array:set:@AtomicSerial`. This composes but
is an edge worth a conformance test (§6).

### 3.4 Unresolvable declared element → `Any` (reached BY RULE, never chosen)

When the reflected element `Type` carries no concrete structural type, the rule yields `Any` (§4):

- **Raw collection** `Set tags;` — `getGenericType()` returns a plain `Class` (not a
  `ParameterizedType`); no element type exists. → `Any`.
- **Unbounded wildcard** `Set<?>` / `Set<? extends Object>` — `WildcardType` with upper bound
  `Object`; the only structural bound is `Object`, which is excluded (§2.1). → `Any`.
- **`Object` element** `Set<Object>` / `List<Object>` — arg is `Class Object`; no closed-subset
  structural type. → `Any`.
- **Lower-bounded wildcard** `Set<? super Integer>` — `WildcardType` with a *lower* bound; the
  element's static upper bound is still `Object` (a `? super Integer` element may be any supertype of
  `Integer` up to `Object`), so there is no usable structural type. → `Any`. *(The lower bound
  constrains what may be **added**, not what an **element is**; it gives the reader no type.)*
- **Type variable** `Set<T>` in a generic `@AtomicSerial class Box<T>` — arg is a `TypeVariable`; the
  concrete type is supplied by the *instantiation*, which the schema (a per-class artefact) cannot
  see. → `Any`. **This is the honest boundary: an annotation could not fix it either** — there is no
  concrete type at `Box`'s declaration site to name; only `Box<Foo>`'s *use site* knows `Foo`, and
  the schema is generated per-class, not per-use.

`Any` is **always rule-selected** on unresolvability and is **never the default and never
developer-chosen**. A homogeneous `Set<Foo>` with a concrete `Foo` must resolve to `set:@AtomicSerial`
and MUST NOT land in `Any` (§4.4 states why that would be a regression, and §6 makes it a conformance
assertion).

---

## 4. The self-describing "ANY" element form (an `[n] IMPLICIT`-tagged CHOICE)

`Any` is a **context-tagged CHOICE** over the closed-subset categories — one context tag per category.
It is the element form used when — and only when — §3.4 makes the declared element type unresolvable.
There is **no** `tag`+`body` `SEQUENCE` wrapper and **no** `ENUMERATED` discriminator: the CHOICE's
context tag **is** the category discriminator, carried in-place on the element's own encoding.

> **Board disposition (adopted): re-encode `Any` as a CHOICE, not a `SEQUENCE{tag,body}`.** The earlier
> draft modelled `Any` as `SEQUENCE { tag ENUMERATED, body Element }`. That is superseded. The reasons
> the CHOICE form is required, not merely nicer (§4.1 realises them):
> - **One discriminator, not two.** A `SEQUENCE{tag,body}` has *two* things that could claim the
>   element's category — the `ENUMERATED tag` and the `body`'s own type/tag — which can disagree: a
>   `tag=collection` over a `body` that is actually a scalar is a **lying encoding**. The CHOICE has a
>   *single* discriminator (the context tag), so that hazard is eliminated **by construction** —
>   there is no second field to lie.
> - **No spurious `OCTET STRING` wrapper / value-equality-of-payload preserved.** The old `body Element`
>   modelled the payload as the `Element` **`OCTET STRING` stub**, which would wrap the real value in an
>   extra OCTET STRING layer — breaking the claim that a value's `body` bytes are identical whether it
>   appears in a declared-element collection or in `Any`. In the CHOICE, the value is carried **verbatim
>   under the context tag** (an `[n] IMPLICIT` tag merely *replaces the leading tag octet* of the value's
>   own DER — it adds no wrapper), so the payload bytes past the tag are unchanged. Value-equality of the
>   payload is preserved (§4.2).
> - **Distinct-tag-clean (§4.5).** Every category is a distinct context tag; the categories are
>   syntactically disjoint on the wire.
> - **Smaller.** No `SEQUENCE` header, no separate `ENUMERATED` TLV — one tag octet replaces both.
> - **Tooling-checkable.** A CHOICE with fixed context tags is a first-class ASN.1 construct any
>   compiler/validator checks; a hand-rolled `SEQUENCE{tag,body}` open type is not.
> - **Octet-sort category-grouping becomes PROVABLE.** Because the context tag is the *leading* octet
>   of every `AnyElement` and tags are distinct and ordered, X.690 §11.6 octet-sort groups elements by
>   category *as a theorem about the tag*, not as an accident of where a `tag` field happened to sit in
>   a `SEQUENCE` framing (§4.2).

### 4.1 Structure (design-level ASN.1 — pinning target P2)

```asn1
-- Any element: a CHOICE keyed on the closed-subset category. The context tag IS the
-- discriminator (no ENUMERATED tag field, no SEQUENCE{tag,body} wrapper). The scalar arms
-- are [n] IMPLICIT (the value's own tag is replaced in-place; a scalar's universal tag
-- carries no further meaning once the category is known from the context tag). The
-- atomicSerialObject AND collection arms MUST be EXPLICIT so the inner tag SURVIVES: for
-- atomicSerialObject the inner AtomicSerialRecord's own tag (a SEQUENCE, a [8] proxy record,
-- or DER NULL, depending on the concrete @AtomicSerial class) is itself load-bearing —
-- IMPLICIT would overwrite it in place, exactly as it would the collection's SET OF/SEQUENCE
-- OF outer tag (0x31 / 0x30, the Option-A discipline discriminator, STD-006 §3.8). You cannot
-- IMPLICIT-tag an alternative whose own outer tag carries meaning (X.680 §31.2.7).

AnyElement ::= CHOICE {
    scalarBoolean        [0]  IMPLICIT BOOLEAN,
    scalarByte           [1]  IMPLICIT INTEGER,
    scalarShort          [2]  IMPLICIT INTEGER,
    scalarInt            [3]  IMPLICIT INTEGER,
    scalarLong           [4]  IMPLICIT INTEGER,
    scalarFloat          [5]  IMPLICIT OCTET STRING,   -- IEEE-754 32-bit, strict-canonical (STD-008 §17.3.1)
    scalarDouble         [6]  IMPLICIT OCTET STRING,   -- IEEE-754 64-bit, strict-canonical
    scalarChar           [7]  IMPLICIT INTEGER,         -- Unicode codepoint (BMP non-surrogate)
    scalarString         [8]  IMPLICIT UTF8String,
    scalarBytes          [9]  IMPLICIT OCTET STRING,    -- a byte[] element
    -- gap [10..19] RESERVED for future scalar categories (§4.5 registry)
    atomicSerialObject   [20] EXPLICIT AtomicSerialRecord,  -- concrete class + schema digest travel in the embedded chain; EXPLICIT preserves the record's own tag
    -- gap [21..29] RESERVED (§4.5 registry)
    canonicalCollection  [30] EXPLICIT CanonicalCollection, -- SET OF 0x31: set:/bag:/map: -- outer 0x31 preserved under EXPLICIT
    orderedCollection    [31] EXPLICIT OrderedCollection    -- SEQUENCE OF 0x30: orderedset:/list:/orderedmap: -- outer 0x30 preserved
}
-- CanonicalCollection / OrderedCollection are the §7.6 productions
-- (CanonicalSet/CanonicalMultiset/CanonicalMap resp. OrderedSetField/ListField/OrderedMapField),
-- unchanged; their own token/discipline is intrinsic to the encoding under the EXPLICIT tag.
```

- **Scalars** are `[0]`–`[9]` `IMPLICIT` so a cross-language reader knows the primitive category from
  the context tag alone, without a Java class; the value bytes past the tag are the scalar's ordinary
  canonical DER.
- **`@AtomicSerial` objects** are `[20] EXPLICIT`; the concrete class identity is the **schema digest**
  already embedded in the record — the tag says only "this is a closed record", the digest says which.
  **EXPLICIT is required, not merely conservative:** the inner `AtomicSerialRecord`'s own tag (a
  `SEQUENCE` for most records, a `[8]` proxy record for a substituted/proxy-form
  `@AtomicSerial` class, or DER `NULL` for a stateless no-field record) is itself load-bearing — it is
  part of how a decoder (JVM or non-JVM) recognises the record's shape before it ever reads the schema
  digest. An `IMPLICIT` tag *replaces* the leading tag octet in place, exactly as it would a
  collection's outer tag; on `atomicSerialObject` that overwrite would destroy the inner record's own
  tag the same way it would destroy the collection discriminator below (X.680 §31.2.7 forbids
  IMPLICIT-tagging an alternative whose own outer tag carries meaning). `EXPLICIT` wraps the tag
  instead, so the inner record's tag survives underneath it. `Any` therefore keeps *most* of the
  schema-digest machinery: an `@AtomicSerial`-valued `Any` element is fully digest-covered inside its
  record. (Reconstruction of that record is gated identically to a typed element — fence (b), §4.3.)
- **Collections** split into two `EXPLICIT` arms — `canonicalCollection [30]` (`SET OF`, `0x31`) and
  `orderedCollection [31]` (`SEQUENCE OF`, `0x30`) — **because the inner outer tag `0x31`/`0x30` is the
  Option-A preserve-vs-canonicalise discriminator (§3.8) and MUST survive.** An `IMPLICIT` tag would
  *overwrite* that outer tag and destroy the discriminator (X.680 §31.2.7 forbids IMPLICIT-tagging a
  type whose outer tag is load-bearing); `EXPLICIT` wraps it, keeping the inner `SET OF`/`SEQUENCE OF`
  tag intact. This gives **one discipline discriminator** (the inner tag), not two, and the nested
  collection's discipline/token (`set:`/`list:`/`map:`…) remains intrinsic to `body`'s own encoding.
  The `atomicSerialObject [20]` arm above is the identical fence applied to the record's own tag instead
  of a collection's outer tag — same X.680 §31.2.7 rule, same reason.

### 4.2 Compatibility with canonical / value-equality machinery (NORMATIVE)

`Any` must not break the properties the closed model exists to guarantee:

- **X.690 §11.6 octet-sort still applies, and category-grouping is now a THEOREM.** The sort orders
  **complete encoded element forms** — `CollectionWireTypes.OCTET_SORT` / `compareOctets` sorts `byte[]`
  of whole TLVs, comparing byte-by-byte as unsigned with the shorter operand conceptually padded with
  trailing `0x00` (see the built `compareOctets`,
  `CollectionWireTypes.java:385`, whose prefix/trailing-zero tie-break is the exact comparator used
  here). An `AnyElement` is a complete TLV whose **leading octet is its context tag**, so a canonicalise
  collection **of `Any` elements** octet-sorts by tag first, then by value within a category — a *total,
  deterministic* order, and the category-grouping is a **provable property of the leading tag** (not, as
  in the old `SEQUENCE{tag,body}` framing, an accident of the `tag` field's position). Distinct values →
  distinct canonical `AnyElement` encodings (context tag + canonical value is injective on value).
- **Reject-non-canonical decode still applies.** A decoder rejects an out-of-order `SET OF` of
  `AnyElement`s, a duplicate `AnyElement` encoding in a `set:`/`orderedset:` of `Any`, a wrong
  container tag, and a value that is not itself canonical for its context tag — the same fail-secure
  obligations §3.8 already imposes, applied under the tag.
- **Value-equality of the payload is preserved (no wrapper leak).** Under an `[n] IMPLICIT` scalar tag
  the value's bytes past the leading tag octet are **unchanged** from how they would encode with a
  declared type; under the `atomicSerialObject`/collection `EXPLICIT` arms the inner record/collection
  encoding is byte-identical to the declared-element form, wrapped rather than overwritten. So a value
  that appears both in a declared-element collection and in an `Any` collection has the **same**
  post-tag bytes — the CHOICE adds no `OCTET STRING` or `SEQUENCE` wrapper to leak. (This is the
  concrete gain over the superseded `SEQUENCE{tag,body Element}` form, whose `Element`-`OCTET STRING`
  stub *would* have wrapped and broken this.)

**F3 caveat — the value-equality claim above holds PER DISCIPLINE CLASS, not across disciplines.**
The preceding bullet's "same post-tag bytes" claim is precise for a fixed collection **discipline**
(§2, §3.8): byte-equality through `Any` holds between equal values *of the same collection-discipline
class* — two `HashSet`-typed elements, or two `LinkedHashSet`-typed elements, that are `.equals` and
carry the same discipline encode to identical bytes under `Any`, exactly as they would as declared
elements. It does **not** hold, and is not claimed to hold, **across** differently-disciplined
collections that happen to be `.equals`. `HashSet` and `LinkedHashSet` holding the same elements are
`.equals()` (`Set.equals` is order-independent), but `disciplineFor(Class)` (§0) derives the wire
discipline from the **runtime class**, not from `.equals`-identity: a `HashSet` is `canonicalCollection`
(`set:`, octet-sorted, tag `[30]`), a `LinkedHashSet` is `orderedCollection` (`orderedset:`,
insertion-order-preserved, tag `[31]`). The two therefore encode under **different** `Any` context tags
and, in general, **different byte sequences** — a `.equals` pair that is *not* byte-equal through `Any`.
This is not a defect in the CHOICE design; it is the same discipline-follows-runtime-class rule §3.8
already states for declared-element collections (a `HashSet` field and a `LinkedHashSet` field are
never claimed to be byte-interchangeable either), restated here so "value-equality through `Any`" is
not misread as "value-equality regardless of collection discipline." The precise, conformance-testable
statement: for `Any` elements `a`, `b` of the **same** discipline class, `a.equals(b) ⟹` (with
canonical/octet-sorted encoding) `encode(a) == encode(b)`; no such implication is made or holds **across**
discipline classes, even when the JDK-level `.equals` happens to return `true`.

### 4.3 The four SECURITY FENCES (NORMATIVE decoder obligations, each conformance-tested)

`Any` moves decode dispatch from the fixed, digest-covered **schema token** to **attacker-controlled
per-element bytes**: with a declared element type the schema commits what each element is, but an `Any`
collection lets the *wire* choose each element's category (and, via the collection arms, its nesting).
That shift is safe **only** behind four decoder fences. Each is a MUST for every conformant decoder
(JVM and non-JVM) and each ships with a conformance test.

- **(a) `MAX_NESTING` threaded through every `Any` recursion.** The decoder MUST carry a depth counter
  and decrement/bound it on **every** `Any`→`canonicalCollection`/`orderedCollection` step **and** every
  `Any`→`atomicSerialObject` step (whose record may itself contain `Any`-typed fields), rejecting before
  the bound is exceeded. *Rationale:* with a declared type the schema token bounded structural depth;
  under `Any`, nesting depth (`Any`-of-collection-of-`Any`-of-…) is **attacker-controlled**, so an
  unbounded recursion is a StackOverflow DoS. *Conformance test:* a deeply-nested `Any` (collection of
  `Any` of collection of … past `MAX_NESTING`) is **rejected before** the bound, not on stack exhaustion.
- **(b) Same `@AtomicSerial` reconstruction gate — `Any` is not a second door.** An `Any`
  `atomicSerialObject [20]` body MUST be reconstructed through the **identical** path as a typed
  `@AtomicSerial` element: the `DeSerializationPermission("ATOMIC")` gate, the endpoint
  `ResolutionContext`, and the class's `check(GetArg)`/deserialising constructor. `Any` MUST NOT provide
  an ungated or differently-gated route to object reconstruction. *Rationale:* otherwise `Any` is a
  bypass of the ATOMIC gate that the typed path enforces. *Conformance test:* a class **denied** by the
  ATOMIC gate, presented inside an `Any` element, is **rejected** exactly as it would be as a typed
  element.
- **(c) Unknown / unregistered tag → HARD REJECT (fail-secure).** A context tag not in the pinned
  registry (§4.5) — including a reserved-gap tag (`[10..19]`, `[21..29]`) and any tag `> [31]` — MUST be
  a decode error. The decoder MUST NOT skip the element, MUST NOT default it to any category, MUST NOT
  continue. *In the CHOICE form this is largely automatic* (an unlisted alternative is a CHOICE decode
  error), but it is stated **normatively** so no implementation "tolerantly" skips. *Conformance test:*
  an element with an unknown or reserved context tag (and a tag that mismatches its payload) is rejected.
- **(d) Canonical minimal tag encoding + deterministic value→category mapping.** The context tag MUST
  be in **minimal (definite short/long-form) DER tag encoding**, and every value MUST map to **exactly
  one** category tag (a `String` value is *always* `[8]`, never `[9]`; an `int` is always `[3]`; a
  canonicalise collection is always `[30]`, an ordered one always `[31]`). *Rationale:* in the CHOICE
  form the context tag **is** the discriminator that octet-sort and value-equality depend on; a
  non-minimal tag encoding or a value encodable under two tags would give two byte forms for one value,
  breaking both. *Conformance test:* a non-canonical (non-minimal) tag encoding, and a value under the
  wrong category tag, are both rejected.

**Developer-guidance corollary (§4.3 → §8.2).** Because an `Any` field's schema no longer commits
element *types* (only the container is coerced; the elements are not), **per-element type validation
shifts entirely onto the developer's `check(GetArg)`** for any field that resolves to `Any` (E10–E14).
The fences above make `Any` *safe to decode*; they do not make its elements *the types the developer
expects* — that is Layer 2's job, and for an `Any` field it is the developer's sole responsibility. See
§8.2.

### 4.4 The honest trade-off (why `Any` is the fallback, not the default)

`Any` costs, relative to a declared element type:

- **Some schema-digest coverage at the collection level.** A `set:@AtomicSerial` field commits, in
  the *schema*, that every element is a closed record of a known structural shape; the collection's
  own schema digest covers that. A `set:` of `Any` commits only "elements are closed-subset values";
  the per-element `@AtomicSerial` digests are still present *inside each* `Any` record, but the
  collection-level schema is looser. (It is a *reduction* of coverage, not a loss — the record
  digests survive in the element.)
- **Compactness.** Every element pays one context-tag octet it would not pay with a declared
  homogeneous element type (the collection arms also pay the `EXPLICIT` wrapper's few header octets).
  This is *smaller* than the superseded `SEQUENCE{tag,body}` form (which paid a `SEQUENCE` header plus
  a separate `ENUMERATED` TLV) — one tag octet replaces both.

Therefore `Any` is **precisely the rule-selected fallback for an unresolvable declared type**, never
the default. A homogeneous `Set<Foo>` with concrete `Foo` MUST resolve to `set:@AtomicSerial` and get
full coverage + compactness; landing it in `Any` would be a correctness/efficiency regression and is
a conformance failure (§6). The design principle: **`Any` buys expressiveness for the genuinely
unresolvable at a stated cost, and the rule guarantees you pay that cost only when you must.**

### 4.5 The `Any` TAG REGISTRY (PINNED NORMATIVE — the CHOICE context tags ARE the registry)

There is no separate `ENUMERATED` to maintain: **the CHOICE's context tags (§4.1) *are* the `Any` tag
registry.** A tag is "registered" iff it is a listed alternative; the registry is pinned in two places
at implementation — **(P1)** the STD-006 §-section and **(P2)** the `.asn1` `AnyElement CHOICE`
production. The pinned assignment:

| Context tag | Category | Payload | Tagging |
|---|---|---|---|
| `[0]`–`[9]` | scalars (bool, byte, short, int, long, float, double, char, String, byte[]) | the scalar's canonical DER | `IMPLICIT` |
| `[10]`–`[19]` | **RESERVED** (future scalar categories) | — | — |
| `[20]` | `@AtomicSerial` object | `AtomicSerialRecord` (schema-digest-identified) | `EXPLICIT` (record's own tag load-bearing) |
| `[21]`–`[29]` | **RESERVED** | — | — |
| `[30]` | canonicalise collection (`set:`/`bag:`/`map:`) | `SET OF` (`0x31`) | `EXPLICIT` (outer tag load-bearing) |
| `[31]` | ordered collection (`orderedset:`/`list:`/`orderedmap:`) | `SEQUENCE OF` (`0x30`) | `EXPLICIT` |
| `> [31]`, or any reserved-gap tag | **UNREGISTERED** | — | **HARD REJECT (fence (c), §4.3)** |

**Registry rules (normative):**
- **Closed + versioned.** The tag set is closed and versioned with the format. There are exactly three
  base categories (scalar, `@AtomicSerial` object, collection) and the closed model (§2) forbids a
  fourth, so the *shape* of the registry is fixed; only *within-category* refinements (a new scalar,
  a future collection discipline) could ever consume a reserved gap, and only by a spec revision.
- **Extension gaps reserved.** `[10]`–`[19]` and `[21]`–`[29]` are reserved so a future scalar or
  object-family refinement lands in a gap without renumbering the collection arms. A reserved tag is
  **not** decodable until a spec revision lists it — until then it is UNREGISTERED and hard-rejected.
- **Unknown tag = hard reject.** Any context tag not in the pinned table (a reserved gap, or `> [31]`)
  is a fail-secure decode error — fence (c), §4.3. The decoder never skips, never defaults.

**Cross-language.** A non-JVM reader dispatches on the small context-tag integer: `[0]`–`[9]` → its
native scalar of that category; `[20]` → its `@AtomicSerial`-record decoder (which then reads the
embedded schema digest); `[30]`/`[31]` → its canonicalise / ordered collection decoder (the inner
`SET OF`/`SEQUENCE OF` outer tag, preserved by the `EXPLICIT` wrapping, carries the discipline). It
needs only *the pinned tag table and the six collection productions*, never the JDK type taxonomy —
the same language-neutrality the rest of STD-006 has.

---

## 5. The TWO-LAYER check (restated precisely — already settled)

Erasure never enters the encode/decode path because two independent layers do the work, and neither
reads *instance* generics:

- **Layer 1 — WIRE/SCHEMA carries the STRUCTURAL type.** Derived from the **declared generic
  signature** (`Field.getGenericType()`, a declaration-time artefact — §1), baked into the schema
  token (`set:@AtomicSerial`, `map:{…}{…}`, or `…:Any`), and **digest-covered** (the token is part of
  the schema bytes the `schemaDigest` commits). This is structural: "a set of closed records", "a map
  from strings to lists of Foo". It is a pure function of the class's declarations, so it is
  reproducible and digest-stable.
- **Layer 2 — the `@AtomicSerial` `check(GetArg)` / deserialising constructor enforces SEMANTIC
  validity + the final typed coercion.** `GetArg.get("tags", null, Set.class)` (and the memoised
  typed accessors) hand the constructor a validated, correctly-typed collection; the constructor
  imposes uniqueness/null-policy/domain invariants (§3.8 "behaviour belongs in `check()`"). The
  *typed* coercion to `Set<Foo>` happens here, in code the developer wrote, against the concrete type
  the constructor knows — not against reflected instance generics.

**Neither layer depends on reflected instance generics.** Layer 1 reads *declarations*; Layer 2 reads
*the developer's own typed constructor*. Erasure — which only hides *instance* type arguments — is
never on the path. This is the settled architecture; this memo only adds that Layer 1's structural
element type is now derived by the §3 rule rather than hand-supplied.

---

## 6. Edge-case table (normative outcomes + conformance assertions)

| # | Declared field | Reflected `Type` (verified §7.1) | Rule outcome | Notes / conformance |
|---|---|---|---|---|
| E1 | `Set<Foo>` (Foo @AtomicSerial) | `ParameterizedType`, arg `Class Foo` | `set:@AtomicSerial` | full digest coverage; MUST NOT be `Any` |
| E2 | `Set<String>` | arg `Class String` | `set:java.lang.String` | scalar element |
| E3 | `List<Integer>` | arg `Class Integer` | `list:int` | boxed→scalar; discipline preserve (List) |
| E4 | `Map<String,Foo>` | args `String`, `Foo` | `map:{java.lang.String}{@AtomicSerial}` | K,V resolved independently |
| E5 | `Map<String,List<Set<Foo>>>` | nested `ParameterizedType` | `map:{java.lang.String}{list:set:@AtomicSerial}` | deep nesting fully recovered |
| E6 | `LinkedHashMap<String,HashSet<Foo>>` | nested | `orderedmap:{java.lang.String}{set:@AtomicSerial}` | discipline per-level; type per-level; orthogonal |
| E7 | `Set<? extends Shape>` | `WildcardType`, upper `[Shape]`, lower `[]` | `set:@AtomicSerial` (=`rule(Shape)`) | upper bound; subtypes self-identify by digest; **NOT `Any`** |
| E8 | `Set<Foo[]>` | arg `GenericArrayType`(Foo) | `set:array:@AtomicSerial:Foo` | array element is reified |
| E9 | `Set<Foo>[]` (array field) | `GenericArrayType`, comp `Set<Foo>` | `array:set:@AtomicSerial` | array-of-collection; conformance test |
| E10 | `Set` (raw) | plain `Class` (not Parameterized) | **`Any`** | no element type exists |
| E11 | `Set<?>` / `Set<? extends Object>` | `WildcardType`, upper `[Object]` | **`Any`** | only bound is excluded `Object` |
| E12 | `Set<Object>` / `List<Object>` | arg `Class Object` | **`Any`** | `Object` excluded (§2.1) |
| E13 | `Set<? super Integer>` | `WildcardType`, lower `[Integer]`, upper `[Object]` | **`Any`** | lower bound gives no element type |
| E14 | `Set<T>` in `class Box<T>` (generic @AtomicSerial) | `TypeVariable` | **`Any`** | **honest boundary; annotation can't fix it either** |
| E15 | `Foo[]` (plain array field) | reified `Class Foo[]` | `array:@AtomicSerial:Foo` | unchanged; arrays reified, not erased |

**Highlights the board should note:**
- E7 (bounded wildcard) is the subtle *win*: it stays fully typed (`set:@AtomicSerial` of `Shape`),
  NOT `Any`, because polymorphism is a solved problem — the digest identifies the subtype.
- E10–E14 are the *only* paths to `Any`, and every one is genuinely unresolvable (no concrete
  structural type exists at the declaration). E14 is the single case where developer intent could in
  principle add information but **cannot** at the declaration site — and there it degrades safely to
  `Any` rather than failing.
- E8/E9/E15 show arrays and collections composing through *different reflection doors* (reified
  component vs generic signature) but the **same** recursive `rule`.

---

## 7. The enabling MECHANISM (design-level; not implemented)

The single concrete change: **the schema-generation path must SEE the declared generic `Type`, not
only the raw `Class`.** Today the chain is:

```
SchemaGenerator.generate(clazz)
  → serialForm()  →  SerialForm{ name, Class<?> type }      // type is the RAW class — the gap
  → toWireType(sf.getType(), clazz)                          // sees Set.class, never Set<Foo>
```

`SerialForm` (`AtomicSerial.java:808`) holds only `Class<?> type`; `getType()` returns the raw class;
`toWireType` (`SchemaGenerator.java:297`) is `Class`-only. There is nowhere for `Set<Foo>`'s `Foo` to
enter. Two design options for the hook (board to choose; both are pure additions, back-compatible):

### Option A — `SchemaGenerator` reflects the declared `Field`/accessor by name (RULE-side, zero API change)

For each `SerialForm sf`, look up the declaring class's `Field` named `sf.getName()` and call
`field.getGenericType()`; feed that `Type` to a new `toWireType(Type, Class)` overload that
implements §3's `rule`. `serialForm()` field names already correspond to real fields in the
overwhelming majority of `@AtomicSerial` classes (they mirror `ObjectStreamField`/
`serialPersistentFields`), so the `Field` is present. Where no matching `Field` exists (a synthesised
serial field with no backing field), the rule falls to `Any` by §3.4 — safe. **No public API change;**
purely internal to `SchemaGenerator`. Risk: a serial field name that does not match a declared field
(rare; handled by `Any`).

### Option B — `SerialForm` exposes the declared generic `Type` (descriptor-side, explicit)

Add an optional `Type genericType` to `SerialForm` (new constructor + `getGenericType()` defaulting to
the raw `type` when absent), populated by the delegate/`serialForm()` author via
`field.getGenericType()`. `SchemaGenerator` then reads `sf.getGenericType()` instead of reflecting the
field itself. This is explicit and keeps `SchemaGenerator` from reflecting arbitrary fields, at the
cost of a `SerialForm` API addition and asking `serialForm()` authors to pass the generic type. Note:
this is **not** a new *annotation* — it is the descriptor carrying a `Type` the JDK already computed;
the author still writes only `Set<Foo> tags;` and a mechanical `field.getGenericType()`, restating
nothing.

**Recommendation for the board:** Option A (no API surface, the rule reads the declaration directly,
`Any` covers the no-backing-field gap) as the primary, with Option B available if the board prefers
the generic type be explicit in the descriptor contract. Either way, the `rule(Type)` logic (§3) and
the `Any` form (§4) are identical; only the *door* through which the `Type` arrives differs.

### 7.1 Feasibility verification (empirical)

A reflection probe (JDK, run 2026-07-05) over the exact edge cases confirms `Field.getGenericType()`
delivers everything §3 needs. Verbatim results:

```
tags   : Set<String>                 → ParameterizedType; arg = Class String            (E2 ✓ resolvable)
nested : Map<String,List<Set<Foo>>>  → ParameterizedType; args String, List<Set<Foo>>   (E5 ✓ deep nesting recovered)
bounded: Set<? extends Number>       → WildcardType; upper=[Number] lower=[]             (E7 ✓ upper bound)
arrElem: Set<Foo[]>                   → ParameterizedType; arg = Class Foo[]  (reified)  (E8 ✓ reified-array element)
setArr : Set<Foo>[]                   → GenericArrayType; comp = ParameterizedType Set<Foo> (E9 ✓ array-of-collection)
wild   : Set<?>                       → WildcardType; upper=[Object] lower=[]             (E11 ✓ → Any)
raw    : Set                          → plain Class (NOT ParameterizedType)              (E10 ✓ → Any)
objs   : List<Object>                 → arg = Class Object                               (E12 ✓ → Any)
lower  : Set<? super Integer>         → WildcardType; lower=[Integer] upper=[Object]      (E13 ✓ → Any)
Box<T>.vals : Set<T>                  → arg = TypeVariable                                (E14 ✓ → Any, honest boundary)
```

Every resolvable case yields a concrete `Class`/`ParameterizedType`/upper-bounded `WildcardType`, and
the two array cases confirm the reflection-door split of §3.3: **E8** `Set<Foo[]>` reflects with a
reified `Class Foo[]` argument (`rule` → `set:array:@AtomicSerial:Foo`), while **E9** `Set<Foo>[]`
reflects as a `GenericArrayType` whose component is the `ParameterizedType Set<Foo>` (`rule` recurses
→ `array:set:@AtomicSerial`) — both MUST be in the conformance probe/tests. Every unresolvable case
yields exactly the raw/`Object`/unbounded-wildcard/lower-bounded/`TypeVariable` shape that the rule
sends to `Any`. **The insight is confirmed: erasure erases instances, not declarations.** This is the
same mechanism Jackson/Gson/Spring rely on.

---

## 8. How this resolves the deferred auto-wiring — with NO annotation

The deferred item was: *a plain `Set<Foo>`/`Map<K,V>` field cannot be auto-wired because the schema
generator has no element type* (`SchemaGenerator.java:393` comment; the builders take a
developer-supplied `elementWireType` string). This memo closes it:

1. The **ordering discipline** was already auto-derived by rule from the declared collection class
   (`disciplineFor`) — no annotation.
2. The **element type** is now auto-derived by rule (§3) from the declared *generic signature*
   (`Field.getGenericType()`) — no annotation. The developer writes only `Set<Foo> tags;`, which they
   would write anyway; the rule reads it.
3. Where the element is genuinely unresolvable (§3.4 / E10–E14), the rule selects the `Any` form
   (§4) — automatically, safely, and never as a silent default for a resolvable type.

So both halves of a collection field's wire-type — discipline and element — are derived **by rule
from declarations the developer already makes**, with `Any` as the rule-selected honest fallback.
**No new annotation is introduced.** An annotation would (a) restate `Set<Foo>` as
`@DerElement(Foo.class)` — a fact that can drift and lie — and (b) *still* not solve the one case it
might seem to (E14, the type variable), because a generic class's declaration site has no concrete
type to name. The rule is therefore both sufficient and strictly better than an annotation.

### 8.1 The one irreducible boundary (flagged, as requested)

There is **exactly one** case where the rule cannot avoid a loss of information, and it is **not**
fixable by developer input: **E14 — a type variable in a generic `@AtomicSerial` class**
(`class Box<T> { Set<T> vals; }`). The concrete `T` is known only at the *use site* (`Box<Foo>`), but
the schema is a *per-class* artefact generated from `Box`, which sees only `T`. No annotation on
`Box` can name a concrete type (there isn't one), so this degrades — safely and by rule — to `Any`.
Every other case (E1–E13, E15) resolves to a fully typed, digest-covered wire-type with no developer
input beyond the ordinary generic declaration. **There is essentially no case requiring developer
input; the sole boundary resolves to `Any`.**

### 8.2 Developer-guidance note — an `Any`-resolved field shifts per-element type validation onto `check()`

A field that resolves to `Any` (E10–E14) is decoded safely (the four fences, §4.3) and the **container**
is coerced to the declared collection type (Layer 2), **but the schema no longer commits the element
*types*** — a `set:` of `Any` says only "closed-subset values", not "each element is a `Foo`". So for an
`Any`-resolved field, **per-element type validation shifts entirely onto the developer's `check(GetArg)`
/ deserialising constructor.** Where a `Set<Foo>` field gets its element type asserted by the schema
(Layer 1, digest-covered), a raw `Set` / `Set<?>` / `Set<Object>` / `Set<T>` field gets no such
assertion; the constructor MUST itself verify each element is the type it expects before trusting it.
This is the practical cost of `Any` beyond §4.4's coverage/compactness note, and the reason a developer
who wants schema-enforced element typing should declare a concrete `Set<Foo>` (E1) rather than a raw or
`Object`-element collection. The rule never *chooses* `Any` for a resolvable field, so a developer only
inherits this obligation when the declaration genuinely gave the schema nothing to commit.

### 8.3 Mechanism confirmed: Option A

The board confirms **Option A** (§7) as the mechanism: `SchemaGenerator` reflects the declaring class's
`Field` by `sf.getName()` and reads `Field.getGenericType()`, feeding it to a `rule(Type)` overload of
`toWireType`. **Zero public API change** (`SerialForm` is untouched); the no-backing-field gap falls to
`Any` by §3.4. Option B (`SerialForm.getGenericType()`) remains recorded as an alternative but is not
the adopted path.

**Status (accurate): design-only.** Nothing in this memo is built. The `.asn1` `AnyElement CHOICE`
production, the STD-006 §-section, the codec `Any` encode/decode + the four fences, the auto-wiring hook
(Option A), and the conformance tests (including E8/E9) are the implementation work that follows a
build-time board pass — no code is proposed for merge here.

---

## 9. Summary for the board

- **Model (PINNED NORMATIVE, §2):** closed subset `Scalar ∪ @AtomicSerial-object ∪ Collection`, closed
  under composition, plus the `Any` CHOICE over those three; exclusion of raw `Object` graphs (except
  within `Any`) / arbitrary `Serializable` / cycles is the normative design principle that makes
  cross-language + value-equality + canonicity simultaneously achievable. Pinning targets: STD-006
  §-section (P1) + `.asn1` `AnyElement CHOICE` production (P2).
- **Rule:** element wire-type = `rule(E)` from `Field.getGenericType()`, recursive, Map K/V
  independent; bounded `? extends B` → `rule(B)`; everything unresolvable → `Any`. No annotation.
- **`Any` (§4):** an **`[n] IMPLICIT`/`EXPLICIT`-tagged CHOICE** keyed on category — scalars `[0]`–`[9]`
  `IMPLICIT`; `atomicSerialObject [20]`, collections `[30]`/`[31]` **EXPLICIT** so the inner record's own
  tag / `SET OF`/`SEQUENCE OF` `0x31`/`0x30` discipline discriminator survives (X.680 §31.2.7). The
  context tag **is** the single discriminator (no lying-encoding hazard) and the tag registry;
  octet-sort category-grouping is a
  *provable* property of the leading tag; no `OCTET STRING` wrapper (value-equality-of-payload
  preserved); the built `compareOctets` prefix/trailing-zero comparator applies unchanged. Rule-selected
  fallback only, at a stated coverage/compactness cost. **F3 caveat (§4.2):** the value-equality claim
  is scoped **per collection-discipline class** — two `.equals` collections of *different* discipline
  (e.g. `HashSet` vs `LinkedHashSet`) do **not** encode byte-identically through `Any`, because the
  discipline is derived from the runtime class, not from `.equals`.
- **Four fences (NORMATIVE, §4.3, each conformance-tested):** (a) `MAX_NESTING` threaded through every
  `Any`→collection / `Any`→object recursion; (b) `Any` object body reconstructed through the SAME
  `DeSerializationPermission("ATOMIC")` gate + endpoint `ResolutionContext` + `check(GetArg)` — `Any` is
  not a second door; (c) unknown/mismatched/reserved tag = HARD REJECT, fail-secure; (d) canonical
  minimal tag encoding + deterministic value→category-tag mapping.
- **Tag registry (§4.5):** the CHOICE context tags ARE the registry; gaps `[10]`–`[19]` / `[21]`–`[29]`
  reserved; unknown tag hard-rejected; closed + versioned.
- **Two-layer check:** schema carries the declared *structural* type (digest-covered); `check(GetArg)`
  enforces *semantics* + typed coercion; neither reads instance generics, so erasure never enters. For
  an `Any`-resolved field, per-element type validation shifts entirely onto `check()` (§8.2).
- **Mechanism (confirmed, §8.3):** **Option A** — `SchemaGenerator` reflects `Field.getGenericType()`,
  **zero API change**. Feasibility empirically verified (§7.1, incl. E8/E9 arrays).
- **Status:** design-only, nothing built (§8.3).
- **Deferred auto-wiring:** resolved for plain `Set`/`Map` fields with NO annotation; the single
  irreducible boundary (type variable in a generic `@AtomicSerial` class) degrades safely to `Any`.
