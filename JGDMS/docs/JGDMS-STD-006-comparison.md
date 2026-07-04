# JGDMS-STD-006 (DER Wire Format) — Feature Comparison

**Status:** informational (companion to JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT).
**Purpose:** position STD-006 honestly against widely-used serialization frameworks.

> **Fairness note.** This is a repo docs artifact. Accuracy and fairness matter more
> than making STD-006 look good. Where a competitor matches or beats STD-006 on an
> axis, the table says so plainly; STD-006's own costs are stated in the
> *Tradeoffs / caveats* section. STD-006 is an **unreleased draft** (v0.13-DRAFT,
> "working scaffold"), with a JVM/DirtyChai-only reference codec today; its distinctive
> value-equality property is now built and tested but **unmerged/unreleased** (and not
> yet auto-wired for plain `Set`/`Map` fields) — read that section before quoting the
> table.

## What STD-006 is

JGDMS-STD-006 is a **language-neutral, DER-encoded (ASN.1 / X.690) wire format** for
the data objects JGDMS transmits over JERI endpoints, replacing Java Object
Serialization (JOSS) beneath the unchanged `@AtomicSerial` validation contract. It
pairs a canonical, deterministic byte encoding with an embedded, digest-identified
schema so data is legible without the originating class.

The **one property that most sets it apart** is **value-equality preservation on
unordered collections**: STD-006 fixes a collection field's wire order from the
declared type's own `equals` contract — canonicalising (octet-sorting per X.690
§11.6) the order for types where order is *not* part of the value (`HashSet`,
`HashMap`, `ConcurrentHashMap`, …) and preserving it for types where order *is* the
value (`List`, arrays, `LinkedHashSet`, `SortedSet`/`TreeSet`, `EnumSet`, …). The
result: two objects that are `.equals` encode to **byte-identical** octets, so
byte-equality mirrors the type's `equals` contract. This is the correspondence JOSS
structurally breaks (JOSS serialises implementation identity, so a `HashSet` and a
`TreeSet` of the same elements — `.equals` as `Set`s — produce different bytes), and
it is the property that lets DER bytes serve as a value-equality proxy for Jini Entry
byte-matching, content-address digests, and signature stability. **No other framework
surveyed here canonicalises unordered-collection element order to preserve
value-equality** — including generic ASN.1 DER (see the `VEq` column and footnote 4).

---

## Comparison matrix

Frameworks are rows; axes are columns. Legend below the table. `✓` = yes / by design;
`✗` = no; `~` = partial / conditional (see footnote). Footnotes carry every nuanced
cell — read them; the single glyphs are lossy.

| Framework | XLang | Schema | Canon | **VEq** | ColOrd | SecDec | Cyclic | Evolve | Sign | Compact | Stream/ZC | Standard |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **JGDMS-STD-006 (DER)** | ✓¹ | ✓ | ✓ | **✓⁴** | ✓ | ✓² | ✗³ | ✓⁴² | ✓ | ~ | ~ | ✓ (X.690) |
| ASN.1 DER (generic X.690) | ✓ | ✓ | ✓ | ✗⁴ | ~⁵ | n/a⁶ | ✗ | ~⁷ | ✓ | ~ | ✗ | ✓ (X.690) |
| Java Serialization (JOSS) | ✗⁸ | ~⁹ | ✗¹⁰ | ✗¹⁰ | ~ | ✗¹¹ | ✓ | ~¹² | ✗¹⁰ | ~ | ✗ | ✗¹³ |
| Protocol Buffers (proto3) | ✓ | ✓ | ✗¹⁴ | ✗¹⁴ | ✗ | ✓¹⁵ | ✗ | ✓ | ~¹⁴ | ✓ | ~¹⁶ | ✗¹⁷ |
| Apache Avro | ✓ | ✓ | ✗¹⁸ | ✗¹⁸ | ✗ | ✓¹⁵ | ✗ | ✓¹⁹ | ~¹⁸ | ✓ | ~ | ✗¹⁷ |
| Apache Thrift | ✓ | ✓ | ✗²⁰ | ✗²⁰ | ✗ | ✓¹⁵ | ✗ | ✓ | ✗²⁰ | ✓ | ~ | ✗¹⁷ |
| Cap'n Proto | ✓ | ✓ | ✗²¹ | ✗²¹ | ✗ | ✓¹⁵ | ✓²² | ✓ | ~²¹ | ~²³ | ✓²⁴ | ✗¹⁷ |
| FlatBuffers | ✓ | ✓ | ✗²¹ | ✗²¹ | ✗ | ✓¹⁵ | ✗ | ✓ | ✗²¹ | ~²³ | ✓²⁴ | ✗¹⁷ |
| MessagePack | ✓ | ✗²⁵ | ✗²⁶ | ✗²⁶ | ✗ | ~²⁷ | ✗ | ~²⁸ | ✗²⁶ | ✓ | ~ | ~²⁹ |
| CBOR (RFC 8949) | ✓ | ✗²⁵ | ~³⁰ | ✗³⁰ | ✗ | ~²⁷ | ~³¹ | ~²⁸ | ~³⁰ | ✓ | ~ | ✓ (RFC 8949) |
| JSON | ✓ | ✗²⁵ | ✗³² | ✗³² | ✗ | ✓³³ | ✗³⁴ | ~²⁸ | ✗³² | ✗ | ~ | ✓ (RFC 8259) |
| Kryo | ✗³⁵ | ~³⁶ | ✗³⁷ | ✗³⁷ | ✗ | ✗³⁸ | ✓ | ~³⁶ | ✗³⁷ | ✓ | ✗ | ✗ |
| Apache Fory (Fury) | ✓³⁹ | ~³⁶ | ✗⁴⁰ | ✗⁴⁰ | ✗ | ~⁴¹ | ✓ | ~³⁶ | ✗⁴⁰ | ✓ | ~ | ✗ |

### Legend (axes)

- **XLang** — Language-neutral / cross-language: a non-JVM runtime can encode/decode
  with standard tooling.
- **Schema** — Schema required (`✓`) vs self-describing tag-length-value (`✗`).
- **Canon** — Canonical / deterministic encoding: one byte-for-byte form per value,
  reproducible across conforming implementations.
- **VEq** — **Value-equality preservation**, *including unordered collections*: two
  objects that are `.equals` (a `HashSet` vs a `TreeSet` of the same elements; two
  `HashSet`s built in different insertion orders) produce identical bytes. This is the
  distinctive STD-006 axis; it is strictly stronger than "Canon".
  > **Peter's call (do not decide without you):** STD-006's VEq cell is kept at `✓`
  > because the property is now implemented and directly tested (footnote 4), per the
  > board recommendation. Because that implementation is **built-but-unmerged/
  > unreleased**, whether the cell should stay `✓` or move to `~` (with footnote 4) is
  > **your decision** — this doc does not settle it. The reviewer's recommendation was
  > `✓` + caveat; the honest alternative is `~` + caveat.
- **ColOrd** — Collection-order semantics tied to the type's value contract: order
  preserved where it is part of the value, canonicalised where it is not.
- **SecDec** — Deserialization security model: `✓` = decode does not, by design,
  instantiate arbitrary types or run type-supplied hooks from the byte stream; `✗` =
  the format's canonical decode path instantiates attacker-chosen types / runs hooks.
- **Cyclic** — Object-graph / cyclic-reference handling *supported* by the format.
  (For STD-006 the `✗` is a deliberate security choice, not a limitation — see
  footnote 3.)
- **Evolve** — Schema evolution / versioning support.
- **Sign** — Signature- & hash-friendly: is a given value's encoding stable enough to
  sign/digest safely without re-canonicalisation surprises?
- **Compact** — Rough wire size: `✓` compact, `~` moderate/verbose, `✗` verbose.
- **Stream/ZC** — Streaming and/or zero-copy read.
- **Standard** — Formal, published standard basis for the *encoding*.

---

## Footnotes

**STD-006**

1. **XLang — goal, not yet a shipped non-JVM codec.** The format is language-neutral
   by construction (ASN.1/X.690, decodable by any DER toolchain given the embedded
   schema). The *only reference implementation today is JVM/DirtyChai*; no non-JVM
   codec ships yet. The design removes the JOSS-specific barriers (§2.1), but
   first-class non-JVM participation is a design target, not a delivered artifact.
2. **SecDec — gated capability, with two live caveats.** By design, decode is a
   single schema-defined path with no in-stream class-instantiation side channels; it
   is gated by `DeSerializationPermission("ATOMIC")`, checked against the
   `ProtectionDomain` of each `@AtomicSerial` class in the hierarchy *before*
   construction, and every value passes the STD-001 `check(GetArg)` invariant test
   before field assignment. **Caveat (a):** STD-008 §4.4 records that enforcing this
   gate in the DER engine (`ObjectCodec.decode()`) is *currently a gap* — the JOSS
   path enforces it; the DER path is specified to but does not yet. **Caveat (b):**
   the permission check is a no-op absent a SecurityManager/DirtyChai policy runtime.
   The structural gadget-freedom (no `readObject`/`readResolve`, no arbitrary
   instantiation, one decode path) holds regardless; the *permission* layer needs the
   gate wired in and a policy runtime present.
3. **Cyclic — intentionally unsupported (security requirement).** STD-006 mandates an
   **acyclic** object graph (§3.7): no handle table, no back-reference mechanism.
   This is a *feature* — it eliminates reference-theft-from-partial-construction and
   cyclic-reference DoS at the format level and is the precondition for
   validate-before-construct. A framework needing cyclic graphs (JOSS, Kryo, Fory)
   cannot offer that guarantee. Scored `✗` on the "supports cycles" axis, but the
   sign is doing the opposite of the usual work here.
4. **VEq — the distinctive cell; note the generic-DER contrast.** Generic ASN.1 DER
   canonicalises *within a given ASN.1 value* (definite lengths, `SET OF` element
   sort, minimal integer encoding), but it does **not** know that a `HashSet` and a
   `TreeSet` are the same logical value — an application that maps both to a bare
   `SEQUENCE OF` in iteration order gets different bytes. STD-006 adds the
   *type→ordering-discipline* rule on top of DER: canonicalise (octet-sort per X.690
   §11.6) the order for types whose iteration order is not part of the value,
   preserve it for those where it is, so `.equals` ⇒ byte-equal. **This octet-sort
   canonicalisation is now built and tested** — branch `der-collection-codec` (codec
   `9f0d1e581`; ASN.1 module + normative spec synced at `d955f0fe0`; full `jgdms-der`
   suite 468/468 green) adds the `set:`/`bag:`/`orderedset:`/`list:`/`map:`/
   `orderedmap:` wire tokens, the X.690 §11.6 octet-sort comparator (Option A: `SET OF`
   tag `0x31` for the canonicalise disciplines, `SEQUENCE OF` `0x30` for preserve, so a
   stock DER decoder enforces §11.6 order), reject-non-canonical (strictly-ascending,
   mandatory-DER) decode enforcement, the `maxCollection`=65536 decode cap on all six
   loops, and a discriminator covering the full JDK collection taxonomy. The two-
   `.equals`-`HashSet`s→byte-identical-DER value-equality property is asserted
   directly; the `LinkedHashSet` stricter-than-`equals` §3a tension is tested too.
   **Residual, stated honestly:** the branch is **unmerged and unreleased**, and one
   completeness gap remains — the *automatic* schema generator does not yet emit these
   tokens for a plain `Set`/`Map`/`Collection` field (a schema author opts in via
   `SchemaGenerator.collectionWireType`/`mapWireType`). That gap **fails closed** (an
   un-tokenised concrete-collection field is rejected at schema generation; an
   interface-typed one fails at encode), so **nothing is silently non-canonical**; it
   is deferred pending element-type conveyance under erasure and will be folded into
   the §7.6 `MapSerializer`/`SetSerializer`/`ListSerializer` replacement. So the design
   provides VEq for unordered collections — which no surveyed competitor does — and a
   tested reference implementation now exists, pending merge/release and transparent
   auto-wiring. Preserve-side value-equality (`List`, `SortedSet`, arrays) holds today.
42. **Evolve — imperative, code-driven `@AtomicSerial` reconciliation (STD-001).** A
   record decodes into a `GetArg` of **named, typed** fields; the deserializing
   constructor reads them with **explicit defaults for absent fields** — the typed
   3-arg form `arg.get("name", default, Type.class)` (and primitive forms
   `arg.get("name", default)`) verified against the source: `GetArg` in
   `org.apache.river.api.io.AtomicSerial` declares `get(String, boolean|byte|…|long)`
   and `<T> T get(String, T, Class<T>)`. Because reconciliation is ordinary Java in
   the constructor, evolution is essentially **unbounded**: add/remove fields with
   defaults (STD-006 §3.9 cases (b)/(c) handle new-data/old-code and old-data/new-code
   both ways), **compute new fields from old ones**, change representation, and — per
   STD-001 RULE-3 — run a static `check(GetArg)` **before any field is set**, so
   **cross-field invariants are enforced at construction** (the spec's own example
   rejects an out-of-range `port` and empty `serviceId`/`host` before the object
   exists). The schema-digest (§7.8) identifies the *shape*; the migration logic lives
   in *code*. This is strictly more expressive than the declarative reader/writer
   models and, unlike them, validates invariants during decode — but it costs
   per-version constructor code, which Avro's automatic resolution does not (see the
   Tradeoffs section and footnote 19).

**ASN.1 DER (generic X.690)**

5. **ColOrd `~`.** DER `SET OF` sorts elements by encoding (X.690 §11.6) and
   `SEQUENCE OF` preserves order — the raw machinery STD-006 builds on. But *generic*
   DER leaves the SET-vs-SEQUENCE choice to the schema author; it does not tie the
   choice to a language's collection-type contract, so value-equality on unordered
   collections is not automatic (footnote 4).
6. **SecDec `n/a`.** X.690 is an encoding, not an object-instantiation runtime;
   "deserialization security" is a property of the *codec/application* that consumes
   it, not of DER itself. STD-006 is one such secured application of DER.
7. **Evolve `~`.** ASN.1 has extensibility markers (`...`) and versioned modules, but
   schema evolution is a per-module discipline, not an automatic reader/writer
   resolution like Avro/Protobuf.

**JOSS**

8. **XLang `✗`.** The stream grammar is defined in Java class descriptors; a faithful
   non-JVM decoder is impractical (this is a stated STD-006 motivation, §2.1).
9. **Schema `~`.** Self-describing via embedded class descriptors, but tied to Java
   classes — not a language-neutral published schema.
10. **Canon / VEq / Sign `✗`.** Not byte-canonical; the same object can serialise
    differently (JVM/version dependent), and collection bytes reflect implementation
    identity, not value — so JOSS bytes are unsafe as a value-equality or signing
    basis on `Set`/`Map` fields. This is precisely what STD-006's §0.1 / §2.1
    motivation targets.
11. **SecDec `✗` — the canonical insecure-deserialization example.** Multiple
    object-creation pathways (`TC_OBJECT`, `TC_PROXYCLASSDESC`, …), `readObject`/
    `readResolve` gadget execution, arbitrary instantiation from the stream. The
    industry's reference case for deserialization vulnerabilities.
12. **Evolve `~`.** `serialVersionUID` + `ObjectStreamField` give some
    compatibility, but it is brittle and class-coupled.
13. **Standard `✗`.** A published Java-specific protocol spec exists, but it is not a
    language-neutral encoding standard.

**Protocol Buffers (proto3)**

14. **Canon / VEq / Sign `✗`/`~`.** Protobuf's own docs state *"Proto Serialization
    Is Not Canonical"*: default serialization is non-deterministic, map field order
    is undefined, and logically-equal messages can produce different bytes across
    implementations/languages. A deterministic *option* exists but is explicitly *not
    canonical* across languages and unknown-field retention breaks it — so signing
    raw bytes is discouraged (`~`). No value-equality on unordered collections.
15. **SecDec `✓`.** Schema-driven decode into generated types populates fields; it
    does not instantiate arbitrary attacker-named classes or run type-supplied decode
    hooks from the wire. (Application code acting on the decoded message is a separate
    concern.)
16. **Stream/ZC `~`.** Length-delimited streaming is common; not zero-copy in the
    Cap'n Proto/FlatBuffers sense.
17. **Standard `✗`.** Widely-used and well-documented, but a vendor/community
    specification, not an ISO/IETF/ITU standard for the encoding.

**Apache Avro**

18. **Canon / VEq / Sign `✗`/`~`.** Avro's *Parsing Canonical Form* + fingerprint is
    a canonicalisation of the **schema** (for schema resolution and identity), **not**
    of the **data encoding**. The binary *data* encoding is compact and
    schema-driven but is not defined to be byte-canonical across encoders (e.g. no
    mandated map-entry ordering), so it is not a per-value canonical form and not a
    reliable signing basis without extra discipline. No unordered-collection
    value-equality.
19. **Evolve `✓`.** Reader/writer schema resolution is a first-class, well-specified
    strength — arguably best-in-class on this axis.

**Apache Thrift**

20. **Canon / VEq / Sign `✗`.** Multiple protocols (binary, compact, JSON); none is
    defined as a per-value canonical form, and field/element ordering is not
    canonicalised for value-equality. Not a signing-stable encoding without extra
    discipline.

**Cap'n Proto**

21. **Canon / VEq / Sign `✗`/`~`.** The wire layout is a fixed memory image (fast,
    zero-copy) but is explicitly *not canonical*: padding, default-value elision
    choices, and pointer layout admit multiple valid encodings of the same value.
    Cap'n Proto provides *canonicalization* as an explicit, opt-in pass for signing —
    hence Sign `~` (possible, but only via that pass), VEq `✗` (no
    unordered-collection value canonicalisation).
22. **Cyclic `✓`.** The pointer/segment model can express shared and cyclic
    references.
23. **Compact `~`.** Optimised for access speed and zero-copy, not smallest wire;
    the memory-image layout includes padding. (Both Cap'n Proto and FlatBuffers offer
    optional packing to shrink this.)
24. **Stream/ZC `✓`.** Zero-copy random access without a parse step is the headline
    feature — STD-006 does not attempt this.

**FlatBuffers**

    (Canon/VEq/Sign/Compact/Stream same rationale as Cap'n Proto — footnotes 21, 23,
    24 — except FlatBuffers does not model cyclic references, hence Cyclic `✗`, and
    has no standardised canonicalization pass, hence Sign `✗`.)

**MessagePack**

25. **Schema `✗`.** Self-describing (tag-length-value); no schema required. Compact
    and simple, but the receiver cannot statically bound structure before decode
    (a property STD-006 specifically wants — §2.1 "bounded before allocation").
26. **Canon / VEq / Sign `✗`.** No canonical form is mandated; map key order and
    integer/format width choices vary by encoder. Not a signing-stable or
    value-equality basis.
29. **Standard `~`.** A published community spec exists; not an ISO/IETF standard.

**CBOR (RFC 8949)**

27. **SecDec `~` (MessagePack and CBOR).** As a data model these do not instantiate
    typed objects from the wire, which is safer than JOSS; but they are commonly used
    with *codec libraries* that map to application types (and, in some ecosystems,
    polymorphic type tags), which can reintroduce instantiation risk. Safe-by-default
    for plain data, risk lives in the binding layer — hence `~`.
28. **Evolve `~` (self-describing formats).** Tolerant of added/removed fields at the
    data-model level, but there is no built-in reader/writer schema-resolution
    contract as in Avro/Protobuf; evolution is an application convention.
30. **Canon / VEq / Sign `~` — deterministic profile only.** RFC 8949 §4.2 defines a
    *Deterministically Encoded CBOR* profile (shortest-form integers, definite
    lengths, sorted map keys) that IS canonical and signing-safe — but only when that
    profile is used; baseline CBOR is not canonical. Even under the deterministic
    profile, CBOR canonicalises *map key order*, not *application-collection element
    order tied to a type's value contract*, so it does not deliver STD-006's
    unordered-collection value-equality (VEq `✗`). Deterministic-profile CBOR is the
    closest competitor on the Canon/Sign axes.
31. **Cyclic `~`.** CBOR has an (optional, tag-based) mechanism for shared/cyclic
    references (RFC 8746 / tag 28/29 extensions), not part of the core model.

**JSON**

32. **Canon / VEq / Sign `✗`.** No canonical form in the base standard (key order,
    whitespace, number formatting all vary). Canonicalisation profiles exist (e.g.
    JCS, RFC 8785) but are not JSON itself. Verbose and lossy on numeric/binary types.
33. **SecDec `✓`.** Pure JSON parsing produces a data tree, not typed objects; the
    canonical decode path instantiates nothing. (As with CBOR, unsafe
    polymorphic-type-binding libraries are a *binding-layer* risk, not a JSON
    property.)
34. **Cyclic `✗`.** JSON has no reference mechanism (cyclic data must be encoded by an
    application convention).

**Kryo**

35. **XLang `✗`.** JVM-specific by design.
36. **Schema / Evolve `~`.** Optional class registration and an optional
    `CompatibleFieldSerializer` give schema-ish behaviour and limited evolution, but
    it is Java-class-coupled and configuration-dependent, not a language-neutral
    published schema.
37. **Canon / VEq / Sign `✗`.** Optimised for speed/compactness; no canonical form,
    no value-equality guarantee, not a signing basis.
38. **SecDec `✗`.** Without careful registration and disabled defaults, Kryo
    instantiates arbitrary classes from the stream — a known deserialization-gadget
    surface, the same class of risk as JOSS.

**Apache Fory (Fury)**

39. **XLang `✓`.** Genuinely multi-language (Java, Python, Go, C++, JS, Rust) — a real
    strength; a design goal STD-006 shares but has not yet delivered a non-JVM codec
    for (footnote 1).
40. **Canon / VEq / Sign `✗`.** Optimised for throughput; no canonical byte form, no
    unordered-collection value-equality, not a signing basis.
41. **SecDec `~` — controls exist, but same *class* of risk.** Fory provides real
    security controls STD-006-adjacent in spirit — class/type **registration**, a
    strict mode, a type checker, and a disallow-list — a meaningful improvement over
    unrestricted Kryo/JOSS. **But** its decode path still *instantiates registered
    types and honours object hooks*, and it has shipped recent deserialization-bypass
    CVEs where those hooks or policy checks were sidestepped (e.g. CVE-2026-50076 in
    the Java `ReplaceResolverSerializer` honouring `readResolve`/`Externalizable`
    without the checks; CVE-2026-48207 bypassing PyFory's DeserializationPolicy).
    That is the structural risk class STD-006 removes by construction (no
    `readResolve`/`Externalizable`/`readObject`, one schema-defined decode path). Fory
    mitigates the risk with policy; STD-006 designs it out — hence Fory `~`,
    STD-006 `✓` (subject to footnote 2's enforcement caveats).

---

## What makes STD-006 distinctive

Evidence-based; each claim maps to the matrix and spec sections.

- **Value-equality preservation on unordered collections is unique in this survey
  (the standout).** By binding wire element-order to the declared collection type's
  `equals` contract — octet-sort (X.690 §11.6) where order is not part of the value,
  preserve where it is — STD-006 makes `.equals` objects encode to byte-identical
  octets. This restores the value/serial-equality correspondence JOSS breaks (spec
  §0.1, §2.1) and underpins Jini Entry byte-matching, content-address digests, and
  signature stability on `Set`/`Map` fields. Generic ASN.1 DER canonicalises *within*
  a value but does not tie collection ordering to a type's value contract, so it does
  not deliver this (footnote 4). *Status:* the octet-sort canonicalisation is now
  **implemented and tested** (branch `der-collection-codec`, codec `9f0d1e581` +
  module/spec `d955f0fe0`, 468/468 `jgdms-der` tests; the two-`.equals`-`HashSet`s→
  identical-DER property asserted directly), but is **unmerged/unreleased** and not yet
  auto-emitted for plain `Set`/`Map` fields (opt-in via the schema-generator builders;
  the un-opted path fails closed, never silently non-canonical — footnote 4).
- **The receiver chooses the implementation — the decode-side face of value-equality.**
  Because DER serialises the *value*, not the implementation, the `@AtomicSerial`
  deserializing constructor materialises the logical value into whatever concrete
  collection is most appropriate at the receiver: a sender's `HashMap` can be
  reconstructed as a `TreeMap`, an immutable `Map.copyOf`, an `EnumMap`, or a
  domain-specific type — the receiver decides. The choice is **bounded correctly by the
  discipline**: for a canonicalise field (order-not-part-of-value) the receiver has full
  freedom of implementation; for a preserve field (order-is-value) it is free only among
  order-respecting implementations. This distinguishes STD-006 from *both* JOSS — which
  pins the sender's concrete class and its private serialized layout onto the receiver —
  *and* the schema formats (Protobuf/Avro/Thrift/Cap'n Proto/FlatBuffers), which hand
  the receiver a generated struct rather than a choice of collection type. It is the same
  value/serial-equality property viewed from the decode side.
- **Canonical bytes on a formal ITU standard basis (X.690 DER).** Deterministic,
  one-encoding-per-value, on a mature multi-language standard with tooling in every
  serious language. Only ASN.1 DER (its own basis) and deterministic-profile CBOR
  match the canonical/signing axes; STD-006 adds the value-equality layer on top.
- **Deserialization designed as a gated capability, not ambient instantiation.** One
  schema-defined decode path, no `readObject`/`readResolve`/`Externalizable` hooks, no
  arbitrary instantiation, and an **acyclic** graph (no handle table) that eliminates
  reference-theft and cyclic-DoS at the format level (§3.7). The nearest
  security-conscious competitor, Fory, mitigates the *same* risk class with policy and
  has still shipped bypass CVEs; STD-006 removes the class structurally — subject to
  the honest caveats in footnote 2 (DER-engine gate not yet wired; no-op without a
  policy runtime).
- **Data outlives code: schema-identified, class-independent decoding.** The
  DER-encoded schema (`serialForm()`) travels embedded in `MarshalledInstance`,
  digest-identified (SHA-256 Merkle chain over the class hierarchy). Given bytes +
  schema, every field is decodable with no class loading and no JVM (§2.3, §7.8) —
  enabling migration, archival, forensic reading, and polyglot access years later.
  Avro/Protobuf/Thrift also decouple data from code via schema; STD-006's addition is
  that the schema is *self-carried and content-addressed* in the instance itself.
- **Evolution is code-driven and invariant-enforcing, not just declarative.** Records
  decode into a `GetArg` of named, typed fields; the `@AtomicSerial` deserializing
  constructor reconciles versions in ordinary Java — explicit defaults for absent
  fields, new fields computed from old, representation changes, cross-field
  reconciliation — and a static `check(GetArg)` **enforces invariants before the
  object is constructed** (STD-001 RULE-3). This is strictly more expressive than the
  declarative reader/writer models (Avro/Protobuf/Thrift), and it catches
  invariant-violating data *at decode*, which those models structurally cannot. The
  cost is per-version constructor code where Avro resolves automatically — an honest
  two-sided tradeoff, detailed in the Tradeoffs section and footnote 42.

---

## Tradeoffs / caveats / where others win

Stated honestly — this is where STD-006 costs something or a competitor is simply
better.

- **Unreleased draft.** STD-006 is v0.13-**DRAFT** ("working scaffold"). The ASN.1
  modules are, by the spec's own note, *not yet validated against an ASN.1 compiler*,
  and several cells (`Permission`, `Date`, `File`, `MarshalledObject` nesting, type
  discriminator) are `[OPEN]`/`[PROPOSED]`. Every framework it is compared against
  here is shipped and battle-tested. This is the single biggest honest caveat.
- **The distinctive value-equality property is now built and tested, but unmerged and
  not yet auto-wired for plain `Set`/`Map` fields.** The unordered-collection octet-sort
  canonicalisation — the standout column — has been **implemented and tested** on branch
  `der-collection-codec` (codec `9f0d1e581`; ASN.1 module + normative spec synced at
  `d955f0fe0`; full `jgdms-der` suite 468/468 green). Concretely built + tested: the
  `set:`/`bag:`/`orderedset:`/`list:`/`map:`/`orderedmap:` tokens; the X.690 §11.6
  octet-sort (Option A `SET OF` `0x31` for canonicalise / `SEQUENCE OF` `0x30` for
  preserve, so a stock DER decoder enforces the order); reject-non-canonical
  (strictly-ascending, mandatory-DER) decode enforcement; the `maxCollection`=65536
  decode cap on all six loops; the two-`.equals`-`HashSet`s→byte-identical-DER property
  asserted directly; and the `LinkedHashSet` stricter-than-`equals` §3a tension. Two
  honest residuals remain: (1) the branch is **unmerged and unreleased**, so this is not
  yet a shipped-release capability; and (2) the *automatic* schema generator does not yet
  emit collection tokens for a plain `Set`/`Map`/`Collection` field — a schema author
  opts in via `SchemaGenerator.collectionWireType`/`mapWireType`. That gap **fails
  closed** (an un-tokenised concrete-collection field is rejected at schema generation, an
  interface-typed one fails at encode), so no field silently receives non-canonical
  treatment; it is deferred pending element-type conveyance under erasure and folds into
  the §7.6 `MapSerializer`/`SetSerializer`/`ListSerializer` replacement. Preserve-side
  value-equality (`List`, arrays, `SortedSet`/`TreeSet`, `EnumSet`) is likewise built and
  tested.
- **JVM/DirtyChai-only reference runtime today.** Language-neutrality is real *in the
  design* (ASN.1/X.690 + embedded schema), but the only working codec is JVM/DirtyChai.
  Protobuf, Avro, Thrift, Cap'n Proto, FlatBuffers, MessagePack, CBOR, and Fory all
  ship mature multi-language implementations *now*. On delivered cross-language reach,
  they win outright.
- **The security gate has enforcement gaps to close.** Per STD-008 §4.4, the
  `DeSerializationPermission("ATOMIC")` gate is not yet enforced in the DER engine's
  decode path, and the check is a no-op without a SecurityManager/policy runtime. The
  *structural* gadget-freedom (no hooks, one decode path, acyclic) is real regardless;
  the *permission* layer is not fully wired.
- **Not optimised for the smallest wire.** DER's tag-length-value framing plus an
  embedded schema chain (carried in every `MarshalledInstance`) is more verbose than
  Protobuf/Avro/MessagePack/CBOR/FlatBuffers for the same payload. STD-006 optimises
  for canonical determinism, value-equality, and self-description — not byte count.
  If wire size is the priority, the compact binary formats win.
- **No zero-copy / random-access reads.** Cap'n Proto and FlatBuffers read fields
  in-place with no parse step; STD-006 (like DER generally) requires a decode pass.
  For latency-critical zero-copy access, they win.
- **Schema required, and it must travel.** Unlike JSON/MessagePack/CBOR (self-
  describing, zero schema management), STD-006 mandates a schema and embeds it in every
  `MarshalledInstance`. That buys data-independence and bounded-before-allocation
  decoding, but it is a management and wire-size cost the self-describing formats avoid.
- **No cyclic graphs.** Deliberate (footnote 3), but a real functional limitation if
  an application genuinely needs shared/cyclic object graphs. JOSS, Kryo, Fory, and
  (via pointers) Cap'n Proto support them; STD-006 forbids them by design.
- **Schema evolution — Avro wins on *zero-code automatic* resolution, not on
  evolution overall.** The declarative models (Avro reader/writer schema resolution
  with defaults/aliases/type-promotion, Protobuf field numbers, Thrift field ids)
  reconcile reader≠writer differences **automatically, with no per-version code** —
  Avro's is the strongest of these, and STD-006 does **not** do that: its
  `@AtomicSerial` reconciliation is *imperative*, so a developer writes the
  deserializing constructor. In exchange, STD-006's evolution is strictly more
  **expressive** (arbitrary code-driven migration — compute new fields from old,
  change representation, cross-field reconciliation) and it **enforces invariants at
  construction**, which the declarative models structurally cannot (a decoded
  Avro/Protobuf message can violate application invariants with nothing catching it
  until later). The honest tradeoff is two-sided: **expressiveness +
  invariant-enforcement-at-decode (STD-006 wins) vs zero-code automatic reader/writer
  resolution for the declarative subset (Avro wins).** Calling either "a stronger
  evolution model" outright would be one-sided.

---

## Reviewer sanity-check flags

Cells a domain reviewer should double-check before this doc is quoted externally:

- **STD-006 VEq (footnote 4)** — the built-but-unmerged split, and the `✓`-vs-`~` cell
  decision (Peter's call, noted at the VEq legend entry). The octet-sort codec is built
  and tested on branch `der-collection-codec` (`9f0d1e581` / `d955f0fe0`, 468/468); a
  reviewer should confirm merge/release status at their HEAD and whether the automatic
  schema generator still requires opt-in for plain `Set`/`Map` fields.
- **STD-006 SecDec (footnote 2)** — confirm the DER-engine `DeSerializationPermission`
  gap and the SecurityManager-dependence are still accurate against STD-008 and the
  live `ObjectCodec`.
- **Avro Canon (footnote 18)** — the schema-canonical-form vs data-canonical-form
  distinction is subtle; confirm no Avro binding mandates canonical *data* bytes that
  would upgrade this to `~`/`✓`.
- **Cap'n Proto / FlatBuffers Sign (footnote 21)** — Cap'n Proto's opt-in
  canonicalization pass exists; confirm FlatBuffers still has no standardised
  equivalent.
- **CBOR Canon (footnote 30)** — deterministic-profile CBOR is the closest competitor
  on canonical/signing; confirm the map-key-order-vs-collection-element-order
  distinction is the right basis for the VEq `✗`.
- **Fory SecDec (footnote 41)** — CVE identifiers and the "controls exist but same
  risk class" framing; confirm the CVEs and that strict-mode still instantiates
  registered types with hooks.
