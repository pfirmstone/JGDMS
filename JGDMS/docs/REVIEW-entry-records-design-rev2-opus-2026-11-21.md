# Review: DESIGN-Entry-Records-MarshalledEntry-Space-API rev 2 (2026-11-21), Opus seat

**Status of this file.** Report of an Opus review agent, asked for by the operator on 2026-11-21 03:38 +1000 as a board review of `docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-rev2-2026-11-21.md`. Saved as received by the proxy-board review session (Claude Fable 5.2). The agent was read-only and compiled nothing. Everything below is RELAYED: the saving session has not re-opened any of the cited lines. An agent's report carries no operator authority.

**Verdict.** Rev 2 is not ready to build from. The overall direction holds up: record-only entries, digest identity, class-free `MarshalledEntry`, one filter per operation, and W1. The rev-1 findings it lists in section 10 are mostly answered on paper. Four problems block it:
1. Its own tree example (3.6) is a recursive type, and recursive types cannot get a digest under the rule that a token embeds the nested type's digest.
2. The immutability "proof" (3.2.1) checks Java fields. It does not check what the codec actually reads (record accessors) or writes (an `@AtomicSerial` class's serial form and `serialize`), so it admits types whose encoding is mutable or polymorphic.
3. Seat 1's M7 is marked answered but is not. I verified that today's decoder loads the class named on the wire before it checks that class against the declared slot. Rev 2 lists no REQUIRED WORK to change that.
4. RL-JGDMS-0160 says stored data is always valid. Nothing in the design achieves that for entries made with `MarshalledEntry.with`, `ofEncoded`, or by a client in another language. The result is entries that match templates but cannot be converted ("poison" entries).

There are also several internal contradictions around null (3.4, 3.6, 3.7, 6.3), and gaps in the cross-language story. The token vocabulary is neutral only in form: enum values on the wire still carry the Java class name.

## Findings

**F1 — High — 3.6, 3.2.1, 3.3. Recursive types have no digest, yet the design uses one as its example.**
- 3.6 says a value "can be a tree (through a list wrapper)". A tree is `record Tree(String v, DerList<Tree> kids)`.
- Under 3.6, the token of `kids` includes `Tree`'s own schema digest, which depends on that same token. The fixed point does not exist.
- The recursive "permitted" check in 3.2.1 also never terminates on such a type.
- The same applies to `record Node(String v, @Nullable Node next)`.
- Evidence: reasoning from the document.
- Direction: either refuse recursive type graphs explicitly (detect cycles in the analysis and in schema decode on the server), or define a back-reference token (for example "self" or a depth index) that is not a digest. Then fix the tree claim.

**F2 — High — 3.1, 3.2.1. The analysis proves the wrong object.** Each of the following would be wrongly accepted:
- **(a) Overridden record accessor.** 3.1 encodes "from its accessors". Take `record R(String a) implements Entry { public String a() { return Registry.current; } }`. Every field is final, so it is "proven", but its encoding reads mutable global state. It is not deterministic, and `R.equals` (which compares fields) disagrees with the marshalled form.
- **(b) Final `@AtomicSerial` class.** Its wire form comes from its serial form and its `serialize` method, not from its Java fields. A class with all-final, permitted Java fields can declare a serial-form field typed `Object` or an interface, which is a polymorphic slot that names a class on the wire. Its `serialize` can also write anything.
- **(c) Inherited fields.** "Every instance field is final" does not say "including inherited". A final class can extend a non-final base with mutable protected fields.
- **(d) Mutable enums.** Enums are admitted as a category and never analysed. `enum Mode { A; public int hits; }` is shared mutable state reachable from the record. This also goes beyond RL-JGDMS-0171, which admits only listed types or proven final classes and records.

Two of the stated rules add little:
- "Written only in a constructor" is already enforced by the JVM for final fields.
- A `this` escape does not threaten immutability after construction.

What the analysis trusts is not stated: class bytes from `getResourceAsStream`, or the defined class. The two can differ (custom loaders, retransformation of method bodies).
- Evidence: document; reasoning; my knowledge of the JDK.
- Direction:
  - For records, require compiler-generated accessors (body is `getfield`/`areturn`), or read component fields through a privileged `Lookup`.
  - Check structure by reflection on the defined `Class` (finality and modifiers), and use bytecode only for accessor bodies.
  - For `@AtomicSerial` classes, analyse the serial-form types, not the Java fields. Or admit records only in 1.0. Records are also the only classes whose final fields `setAccessible` cannot write, so the security-manager assumption matters less for them.
  - Include inherited fields.
  - Analyse enums, or state that only the constant name is the value.
  - Key the cache by `Class`, not name, because RL-JGDMS-0157 allows coexisting versions.

**F3 — High — 3.2.2, 6.1 rule 6, 6.3; seat 1 M7 marked answered in section 10. The writer still names classes that the reader loads.**
- Verified in source:
  - `ObjectCodec.decodeHierarchy` loads `leafFirst.get(0).className()` from the wire chain at `ObjectCodec.java:709`. Only after that does it call `admissibleConstructClass` (line 714). It loads every chain class at line 737.
  - The enum leaf reads the class name from the bytes and loads it at `ObjectCodec.java:2124`, before its admission gate.
  - `EntryRepV2Codec.decodeFieldValue` decodes with `ResolutionContext.NONE` (`EntryRepV2Codec.java:609`).
- 3.2.2 says "the declared type is always the type that arrives and the sender chooses no class". That is a property the decoder must enforce, and today it does not.
- Direction: add REQUIRED WORK. For record entries, compare the wire class name (or digest) with the declared final type before any `loadClass`. Resolve only through the loader of the caller's class. Add negative tests.

**F4 — High — 6 (`MarshalledEntry.with`, `ofEncoded`), 8.2; RL-JGDMS-0160. "Stored data is always valid" is not implemented.**
- The server checks form only.
- `MarshalledEntry.with(field, value)` builds "a new entry, same type" without the class, so the canonical constructor never runs.
- Clients in other languages bypass the constructor by definition.
- Result: entries of type T that match templates for T but fail `as(T)`. `read` can keep returning the same poison entry and starve readers. `take` removes it, and the caller gets a conversion failure.
- Evidence: document, reasoning.
- Direction:
  - State plainly that validity is checked at conversion, not at storage.
  - Drop `MarshalledEntry.with`, or make it require the class and run the constructor.
  - Specify reader behaviour when conversion fails, for example skipping the entry or an option on `read`.
  - Ask the operator whether RL-0160's "stored data is always valid" is meant to apply to writers outside Java. This is a gap between the ruling and the design, not an argument against the ruling.

**F5 — Medium — 3.4, 3.6, 3.7, 6.3; RL-JGDMS-0135, RL-JGDMS-0174. Null rules contradict each other.**
- 3.6: "A nested value may be null if the outer constructor accepts null." This contradicts RL-0174 and 3.4, where the codec refuses null in a never-null component before the constructor runs.
- 3.7: "Every component is a reference and may be null" is stale since RL-0174.
- 6.3 passes null to the constructor for a missing component, while 3.4 says the codec refuses it first.
- Combined effect of RL-0135 ("a missing field decodes as null") and RL-0174 (never null by default): adding a never-null component is always incompatible. `ADDED_FIELDS` therefore helps only when the added component is annotated nullable. The design does not say this.
- The mismatch case is unspecified: a body field marked nullable while the reader's component is never null, with the same token. Is that "a different type"?
- Direction: make 3.4 the single rule; correct 3.6 and 3.7; state the consequence for `ADDED_FIELDS`; say whether the nullable mark takes part in "same type" for pairing.

**F6 — Medium — 3.7 (nullable mark), RL-JGDMS-0173. The canonical form of the mark is underspecified, and digests are identity.**
- `nullable BOOLEAN DEFAULT TRUE` is only canonical if decoders refuse an explicit TRUE. The document does not say so.
- If explicit TRUE is accepted, one type has two schema encodings and two digests. A writer can then mint a look-alike type under the same name.
- RL-0174 makes never-null the default for records, so nearly every record field definition will carry the element. That is fine but should be stated.
- The nullable annotation must be one specific JGDMS annotation, read from one fixed place. If it is not, two builds compute different digests: a TYPE_USE `@Nullable` (JSpecify style) appears on `getAnnotatedType()`, not `getAnnotation()`.
- Direction: specify that the decoder refuses explicit TRUE; name the annotation and where it is read from; refuse foreign `@Nullable` annotations with an error rather than ignoring them.

**F7 — Medium — 3.7 point 1 (schema name), RL-JGDMS-0165. Schema names conflict with the ruling and with the decoder.**
- RL-0165: "the digest covers the declared class of each component". An author-chosen schema name replaces the class with a free string. Two unrelated classes with the same schema name and shape then get the same digest.
- The decoder loads `schemaRecord.className()` (`ObjectCodec.java:737`, verified). A schema name that is not a class name needs a schema-name-to-class mapping, which becomes a new ambiguity and attack surface.
- Enum values in a slice carry the Java class name in the payload as well (see F9), so a schema name in the schema record would not reach those bytes.
- Direction: for 1.0, the schema name is the Java binary name, carried as an opaque string; other bindings copy it. If a separate name is adopted later, conversion must map only from the class the caller supplies and never look a class up by name.

**F8 — Medium — 3.3, 3.6. Type names that vary between builds or runs.**
- Local records get compiler-chosen binary names (`Outer$1Loc`); the numbering can differ between javac and ecj or after a source reordering.
- Hidden classes have per-run names.
- Either case gives a different digest for the same source.
- Direction: refuse local, anonymous and hidden classes as record entries and nested records.

**F9 — Medium — 3.2 (enum row), 8.2, 3.7. Enums undermine digest identity, the server's check, and neutrality.**
- **Constants are not in the digest.** The token is `enum:<className>`. Adding or removing a constant leaves the digest unchanged, so strict `as` passes the digest check and then fails at decode.
- **The server cannot validate.** With no class it cannot tell whether a constant name is valid. 8.2's claim that the class-free check is "complete" for enums is false: it accepts any UTF-8 string.
- **Java class names travel in the bytes.** A slice is "a pure function of the field value" and goes through the object-stream path for non-`@AtomicSerial` values (`EntryRepV2Codec.java:141-170`, verified). An enum item there carries the class name and the constant (`ObjectCodec.decodeEnumLeaf`, about lines 2117-2124, verified). So matching bytes include the Java class name, and a Rust writer must emit it. 3.7's statement "an enum is its constant name" is wrong for the slice.
- Direction: an enum schema record that lists the constants (pinning them in the digest and letting the server validate); a slice encoding of the constant only, with the type taken from the token.

**F10 — Medium — 4, 4.1. Set and map wrappers versus `-0.0` and other values that are equal on the wire but not in Java.**
- `DerSet<Double>` holding `0.0` and `-0.0` is a valid two-element Java set. The encoder normalises both to the same bytes (verified, `ObjectCodec.java:1340-1368`).
- The set decoder requires strictly ascending elements with no duplicates (seat 2 and the agent, relayed). The value is therefore unencodable or not canonical.
- The same holds for `DerMap` keys, and for nested records containing such values.
- Direction: the wrapper constructors encode their elements and deduplicate by encoded form. Define `contains`/`get` by encoded equality and document the difference from `equals`, or refuse `-0.0` in set elements and map keys.

**F11 — Medium — 4.1 W1. How the token is derived, and the token duplicated in the bytes.**
- Today the token for a `Set` subtype comes from `CollectionWireTypes.disciplineFor`, which tests `SortedSet`, `SequencedSet` and similar interfaces (verified, `CollectionWireTypes.java:303-400`). `DerSet` gets `set:` only because it happens not to implement `SequencedSet`. Adding that interface later would silently switch it to `orderedset:` and change matching.
- The `[16]` item carries the collection token in its content (`DerObjectStreamCodec.java:91-93`, verified), while the schema also carries a token. The design does not say the server refuses a slice whose in-band token differs from the schema token, for example a `list:` item in a `set:` field.
- More generally, 8.2's "decode against the token" must include "the self-describing item's type equals the token". Otherwise a `long` field can hold an `int` item and never match.
- Direction: map the wrapper types to tokens explicitly (`DerList`→`list:`, `DerSet`→`set:`, `DerMap`→`map:`, `DerBytes`→`byte[]`), bypassing `disciplineFor`; require in-band token = schema token on both encode and verify.

**F12 — Medium — 8.1, 8.2. Server-side vocabulary and resource limits.**
- The server must refuse record-kind schemas containing tokens outside the record vocabulary: bare `@AtomicSerial`, `any`, `array:`, `java.lang.Class`, and polymorphic element tokens. 8.2 checks values against tokens, not the tokens themselves. Without that check, a writer outside Java can store polymorphic or `any` slots that class-free readers and filters then traverse.
- Removing the one-hash-per-name check lets any writer create unlimited digests under one name, so the type table and per-digest index grow without bound. A quota is needed.
- 8.1 says "ignores, or refuses on mismatch". It should be "refuses" (seat 1 H1).
- `DESIGN-SpaceChannelAuthorization` keys authorization on `className` (seat 1 H1, relayed). The design does not say how authorization works when identity is the digest, even though writers outside Java can claim any name.
- Existing caps apply (verified): 1 MiB per slice, 8 MiB per body, 256 schemas in the schema table, `MAX_FIELDS` 65535, nesting 16 (`EntryRepV2Codec.java:95-104`; `ObjectCodec.java:153`).
- Not covered by any cap:
  - the number of templates in `take(Collection)` and `registerForAvailabilityEvent`;
  - client memory for `maxEntries` results of up to 8 MiB each, each fully verified;
  - object growth in class-free `get()` maps.

**F13 — Medium — 3.7 and generally. What an implementer in Rust or Haskell cannot do from this document.**
- Compute a digest: the algorithm, the exact bytes hashed, and the parent Merkle rule are not stated.
- Write a slice: it is a Java object-stream item with context tags (`[7]` enum, `[16]` collection), not defined here.
- Parse the collection token grammar (`map:{k}{v}` with nesting).
- Find the nested-record token form, or how it embeds a digest.
- Find the valid enum constants (F9).
- Call the space: `Space` is a Java interface carried over JERI, and no language-neutral protocol is defined.
- Write a nested `@AtomicSerial` value (acknowledged in the document).
- Express a sum type: sealed interfaces are refused as interfaces, so a Rust enum with data or a Haskell ADT has no mapping.
- Direction: put a normative appendix in STD-006 (grammar, the digest algorithm, slice encodings per token, test vectors). The space protocol is out of scope here but should be noted as a dependency of RL-0172.

**F14 — Low — 3.2 (primitives refused).** Primitives are refused because of the legacy reflective path (`EntrySchemaGenerator.java:244`, verified), where null serves as the wildcard. For records, templates come from a builder and RL-0174 makes components never null, so `int` is the natural form. Its token is the same as `Integer`'s (`SchemaGenerator.java:425-431`, verified). Suggest permitting primitives as never-null.

**F15 — Low — 3.7, open question 9. `Character` is mischaracterised.** The wire already encodes `char` as a code point and refuses unpaired surrogates (`ObjectCodec.java:1376-1388`, verified). It is therefore a Unicode scalar value in the BMP, not "one UTF-16 code unit". The "16-bit unsigned" option would contradict the existing encoder.

**F16 — Low — rulings conflict (RL-JGDMS-0174 vs RL-JGDMS-0175).** RL-0174 says "legacy entry classes stay nullable throughout". RL-0175 makes an `@AtomicSerial` class whose constructor checks for null an error unless it declares the field never null. A legacy entry that is also `@AtomicSerial` and null-checks falls under both. Ask the operator which ruling wins. The design also understates RL-0175's reach: it changes the digests of every such `@AtomicSerial` class on all DER wires, not only entries, and only 4 modules were counted.

**F17 — Low — 3.4, 3.6. Nested null is not "absent".** Inside a nested value, null is DER NULL (agent, relayed: `ObjectCodec.java:1262`), not the absent slice. That is consistent, but the standard should state it, along with the fact that a nested field cannot be wildcarded.

**F18 — Low — earlier findings still open.**
- Seat 1 L1: a writer stamp outside equality.
- Seat 1 L2: whether a record with zero components is allowed. STD-006 says `SIZE(1..)`; the code allows 0.
- Seat 2 F15: under a security manager, the codec needs access to non-public records' constructors and accessors. Either require `public`, or state the codec's grant.
- Stage 2: the legacy kind's superclass chain must come from the body, not from writer-supplied fields (the H1 pattern again).

## Citation checks (all opened by the agent)

| Citation | Result |
|---|---|
| `EntryRepV2Codec.java:299-312`; comment "later refinement" | Confirmed. The comment is at line 289. |
| `EntryRepV2Codec.java:300,305` | Confirmed. The `toWireType` call is at 304, not 305 (off by one). |
| `EntryWireField.java:64,74` | Confirmed (field `type` at 64, constructor at 74). |
| `SchemaGenerator.java:607-625` | Confirmed. |
| `SchemaGenerator.java:443` (`enum:`) | Confirmed. |
| `SchemaGenerator.java:484-507` (bare `@AtomicSerial`) | Confirmed (489, 504-506). |
| `SchemaGenerator.java:403-410` (scalar tokens) | Partly wrong. Those lines are the Javadoc table. The returns are at 425-431, and `float`/`double`/`char` at 480-482 are not in that range. |
| `DerObjectStreamCodec.java:1207-1209` | Confirmed. |
| `DerObjectStreamCodec.java:91-93,181-184` (`[16]`) | Confirmed. |
| `ObjectCodec.java:1586-1593` (sorted set) | Confirmed. |
| `ObjectCodec.java:153,425` (`MAX_NESTING`) | Confirmed. |
| `EntryRep.java:1226-1236` (slice match) | Confirmed. |
| `EntryRep.java:837,846-849` (codebase `loadClass`) | Confirmed. |
| `OutriggerServerImpl.typeCheck` | Confirmed at line 1495. |
| `AtomicSerialSchemaRecord.java:41-44` | Confirmed. |
| `AtomicSerialFieldDef.java:35-37,138` | Confirmed. |
| `SerialEntry.java:102-127` | Confirmed. |
| STD-006 Appendix `:222-229` | Confirmed. |
| `Valid.notNull` | Exists (`Valid.java:222`). |

## Open questions: recommended answers

1. **Further listed types.** Add a type only together with a normative, language-neutral encoding and canonical rule. Candidates: UUID (16 octets), Instant (seconds plus nanos), BigInteger (DER INTEGER). For BigDecimal, decide whether scale is part of the value; byte equality will follow `equals`, not `compareTo`. None is needed for 1.0.
2. **Wrappers in legacy entries.** No. A second encoding gives one class two digests and two matching behaviours, and legacy `@AtomicSerial` values already carry JDK collections as fields.
3. **Renaming a component.** Yes, provide a wire-name annotation (seat 2 F10). It also lets bindings in other languages use their own field naming.
4. **Moving data from a legacy class to a record.** Leave it to client code. A server-side conversion would need classes or a class-free mapping language, which is out of scope for 1.0.
5. **Explicit null in a template.** Confirm: filters only.
6. **The `Space` name and package.** No technical stake. Keep the old names on the wrapper only.
7. **Lenient conversion inside a nested record.** Agree: strict inside. Note F5: under RL-0174, top-level leniency rarely applies anyway.
8. **Token spellings.** Keep the existing spellings, defined as fixed strings, and make only new tokens neutral. This avoids moving digests during the freeze (RL-0133). The cost is an irregular vocabulary, which normative definitions absorb.
9. **`Character`.** Define it as a Unicode scalar value in the BMP, which matches today's encoder (F15), or leave it out of the cross-language profile. Not "16-bit unsigned".
10. **Schema name.** Do not adopt for 1.0. Use the binary name as an opaque string (F7).
11. **Nullable default for nested records.** Yes, never null unless annotated, for consistency with RL-0174 and so that other languages map to plain types. Fix 3.6 to match.

## W1 vs W2

W1, with the conditions in F11:
- explicit mapping from wrapper to token;
- in-band token equals schema token, enforced on both encode and server verify;
- element tokens for nested records carry the digest;
- the duplicate-normalisation rule of F10.

Reasons:
- W1's bytes are already defined by STD-006, so bindings in other languages need nothing new.
- The element type lands in the digest, as RL-0165 requires, without changing the grammar.
- W2 gives a digest that is independent of the element type (`any`). It needs a new grammar plus a decode check to recover what W1 has for free. It also brings back an `@AtomicSerial` decode path, which loads the class named on the wire (F3).

W1's real risk is the one in hazard 2 of section 12: the `[16]` decoder has no callers and has not been reviewed. Read and test it before committing.

## Not checked

- The `[16]` typed-collection decoder and `writeCollection`.
- Whether schema-table chain bytes are hash-verified against their digests at decode. Orphan refusal is verified: `EntryRepV2Codec.java:457-460`.
- Whether `ResolutionContext.loadClass` initialises classes.
- The `DeSerializationPermission` gating on nested decode.
- `DerUtf8`.
- Reggie.
- `LookupAttributes` and `AbstractEntry`.
- The CEL filter projection beyond the head of `FilterEval`.
- The JERI method encoding a non-Java client would need.
- Anything under `services/*` beyond the files cited above.
- Nothing was compiled or run.
