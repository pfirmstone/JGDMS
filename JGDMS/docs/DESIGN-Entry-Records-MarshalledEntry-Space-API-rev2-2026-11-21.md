# Design: record entries, `MarshalledEntry` and the space API that returns it (DRAFT rev 2)

- **Status:** DRAFT rev 2, section 3.2 revised at 02:50 +1000 for RL-JGDMS-0171; sections 3.6 and 3.7 added at 03:10 +1000; 3.7 extended at 03:18 +1000 for RL-JGDMS-0173. Not reviewed. Nothing in it is built. It replaces rev 1 (`docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-2026-11-20.md`), which a board reviewed and found not to be adopted as written.
- **Date:** 2026-11-21. **Written by:** Claude Fable 5.2, proxy-board review session, at the operator's request (02:35 +1000).
- **Read against:** JGDMS trunk `83c4d4c7a`. Uncommitted.
- **Rulings it rests on:** RL-JGDMS-0134 to RL-JGDMS-0175, cited by ID only; the register holds their wording and status.
- **Review wanted:** a blind review by a model other than Fable before any of it is acted on.

## 0. How to read this

- **VERIFIED** means this session opened that line on 2026-11-20 or 2026-11-21. Nothing was compiled or run.
- **RELAYED** means taken from a board seat's review or the Sonnet agent's report and not re-opened. The three files are `docs/REVIEW-entry-records-design-opus-2026-11-21.md` (seat 1), `docs/REVIEW-entry-records-design-sonnet-2026-11-21.md` (seat 2) and `docs/REVIEW-entry-encoding-canonical-matching-sonnet-2026-11-21.md` (the agent).
- **PROPOSAL** is the author's design. Every class, method and token name in a proposal is illustrative.
- **REQUIRED WORK** marks something the design needs that the code does not do today.
- Paths are relative to `JGDMS/JGDMS`. `DER/` is `jgdms-der/src/main/java/au/net/zeus/jgdms/der/`.

## 1. What is decided

Each row is a ruling. This section restates none of them; it says only which part of the design each one governs.

| Subject | Rulings |
|---|---|
| Fields need not be non-final; new entries preferred over mutation | RL-JGDMS-0134 |
| Version compatibility is the reader's choice at conversion; only added fields are compatible | RL-JGDMS-0135, RL-JGDMS-0137 |
| Specified in STD-006 version 1.0 | RL-JGDMS-0139, RL-JGDMS-0141 |
| Client interface installed locally, not in a download jar; built for release 21 | RL-JGDMS-0142, RL-JGDMS-0159 |
| No live interoperation with older releases; old application code through resident wrappers | RL-JGDMS-0146, RL-JGDMS-0147 |
| Entry classes: download off by default; trusted by digest in the djinn or SPIFFE policy | RL-JGDMS-0155, RL-JGDMS-0156, RL-JGDMS-0158 |
| Coexisting versions of one class name | RL-JGDMS-0157, RL-JGDMS-0165 |
| Constructor validates; template built with a builder; old interface decorates the new | RL-JGDMS-0160 |
| Component types immutable; collections by DER-defined wrappers; no arrays | RL-JGDMS-0161, RL-JGDMS-0169 |
| Immutability established by a listed type or by bytecode analysis | RL-JGDMS-0171 |
| The six statements (immutability, records, no inheritance, identity, `MarshalledEntry`, marshalled by default) | RL-JGDMS-0162 to RL-JGDMS-0167 |
| Two kinds of `MarshalledEntry`; records first, legacy kind and wrapper second | RL-JGDMS-0168 |
| Matching on the marshalled form everywhere; server verifies canonical encoding at write | RL-JGDMS-0170 |
| A cross-language mapping from Java classes to schema is needed | RL-JGDMS-0172 |
| The serial format gains a boolean saying null is possible | RL-JGDMS-0173 |
| A record entry component is never null unless annotated nullable | RL-JGDMS-0174 |
| `@AtomicSerial`: nullability declared in the serial form and checked against the constructor; either mismatch is an error | RL-JGDMS-0175 |

Everything from section 2 onward is PROPOSAL unless labelled otherwise.

## 2. What exists today that the design builds on

1. `@SerialEntry` already documents a record as an entry, with a worked example (`jgdms-platform/.../net/jini/core/entry/SerialEntry.java:102-127`). VERIFIED. No record entry exists in the tree (seat 2, RELAYED).
2. The `@SerialEntry` encode path builds a schema chain of exactly one record, with no superclass link (`DER/entry/EntryRepV2Codec.java:299-312`; its comment says superclass linking "is a later refinement"). VERIFIED.
3. That path takes each wire field's type as a `Class` (`EntryWireField.java:64,74`; `EntryRepV2Codec.java:300,305`), so type arguments are lost before the schema is built. VERIFIED.
4. The schema generator records an element type only when the raw class is a `Collection` or `Map`; for any other parameterised class it maps the raw class and drops the arguments (`DER/schema/SchemaGenerator.java:607-625`). A type variable, wildcard or `Object` becomes the token `any`. VERIFIED.
5. An enum field's token names its class: `enum:<className>` (`SchemaGenerator.java:443`). VERIFIED. A nested `@AtomicSerial`, interface or abstract field's token is the bare marker `@AtomicSerial` and names no class (`SchemaGenerator.java:484-507`). VERIFIED.
6. A collection cannot be the top-level value of a field: the object-stream layer refuses any object whose class is not `@AtomicSerial` (`DER/stream/DerObjectStreamCodec.java:1207-1209`, VERIFIED), and the draft standard states the restriction and its two exceptions (`docs/JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md:222-229`, VERIFIED).
7. A typed top-level collection item exists in the stream format, tag `[16]`, carrying the collection's wire-type token and then the collection (`DerObjectStreamCodec.java:91-93,181-184`). VERIFIED that it is defined. The agent found no caller outside that class (RELAYED).
8. Sets and maps declared with a hash-ordered or bare interface type are written sorted by encoded octets; lists keep their order (`DER/object/ObjectCodec.java:1586-1593`, VERIFIED; the full classification is RELAYED).
9. Outrigger matches by comparing each non-wildcard field's bytes (`services/outrigger/outrigger-dl/.../proxy/EntryRep.java:1226-1236`). VERIFIED. The schema digest is not compared there; type is decided by class name (RELAYED).
10. The space keeps one hash per class name and refuses a second (`OutriggerServerImpl.typeCheck`, VERIFIED on 2026-11-21 when seat 2 reported it).
11. The server does not parse a field's bytes when an entry arrives (RELAYED, labelled INFERRED by the agent).
12. `EntryRep.entry()` loads a class named by the writer from a codebase named by the writer (`EntryRep.java:837,846-849`, VERIFIED on 2026-11-20).

## 3. Record entries

### 3.1 Definition

A record entry is a Java record that implements `net.jini.core.entry.Entry`.

- It needs no annotation and no hand-written wire form. Its wire fields are its components, in declared order; a component's wire name is its name and its wire type is its declared generic type.
- It is encoded from its accessors and decoded through its canonical constructor. The constructor may validate and may refuse (RL-JGDMS-0160).
- A record that also carries `@SerialEntry`, or that declares `entryForm`, `serialize` or a `GetEntryArg` constructor, is refused when first used as an entry. There is one path for records, so one record has one digest. This answers seat 1's M3.
- A record entry is never used as a template (section 5).

REQUIRED WORK. A codec entry point that derives the wire fields from `Class.getRecordComponents()` using `RecordComponent.getGenericType()`, so that type arguments reach the schema (item 3 of section 2 shows the present path cannot carry them).

### 3.2 Permitted component types

A component's declared type is permitted if the codec can encode it and it is one of the following (RL-JGDMS-0171). The check is made once per record class, when it is first used as an entry, and a failure names the component.

| Kind | Types | Token |
|---|---|---|
| Listed scalar | `String`, `Boolean`, `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `Character` | existing scalar tokens |
| Enum | any enum type | `enum:<className>` |
| Wrapper | the DER byte-sequence and collection wrappers (section 4), with type arguments that are concrete and themselves permitted | REQUIRED WORK |
| Proven | a record, or a final class the codec can encode, that bytecode analysis proves deeply immutable | REQUIRED WORK: the token must name the class |

Refused: primitives (already refused for entries, RELAYED), arrays, JDK collections, interfaces, abstract and non-final classes, `Object`, type variables, wildcards, raw types, and any class that is not proven.

#### 3.2.1 The analysis

It proves a class deeply immutable when all of these hold:

- the class is a record or is final;
- every instance field is final and is written only in a constructor;
- no constructor lets `this` escape;
- every field's declared type is permitted by this section, recursively, with concrete type arguments.

Its limits:

- **It is conservative.** The answer is "proven" or "not proven". A class that is immutable in fact but not provably so is refused. An array held inside a class is provable only when it is copied on the way in and never handed out, and will often be refused; the wrappers are the alternative.
- **JDK types are listed, not analysed.** `String` has a non-final field for its cached hash and would fail the analysis.
- **It runs where the class is:** at the client when a record class is first used as an entry (the result cached per class), and as a build-time tool. The server holds no entry classes and relies on none of this; its guarantees come from the schema digest and the check of section 8.2.
- **Assumption (the operator, 02:47 +1000):** a security manager is always installed and reflection is controlled, so code without `ReflectPermission("suppressAccessChecks")` cannot write a final field.

REQUIRED WORK. The analyser. Whether JGDMS already has one was not checked. A nested record is not encodable today; it would be encoded as section 3.1 derives a record's form. A nested final `@AtomicSerial` class is encoded by its own `serialize` code, as today.

#### 3.2.2 Consequences

- For code without that permission, a record entry is immutable in fact. RL-JGDMS-0162 says "shallowly" so that the specification stays true if a later version admits a type that is not.
- No component is a polymorphic slot. A proven type is final, so the declared type is always the type that arrives and the sender chooses no class. This answers seat 1's M7 for record entries.
- The token of a nested component names its class, so the class is in the digest (RL-JGDMS-0165). Today the token is a bare marker (item 5 of section 2). This answers seat 1's M4.

#### 3.2.3 Other languages

Asked by the operator at 02:48 +1000. Not ruled; clients in other languages are entry 2 of `docs/SUGGESTIONS-requiring-further-review.md`.

- Immutability is a property of a client's own representation of an entry, not of the bytes. The wire form and the matching rules belong in the normative part of STD-006 and must not depend on bytecode analysis. The listed types and the analysis of 3.2.1 are the Java binding's way of meeting the requirement.
- Each other binding meets it by its own means, normally at compile time: a generated implementation that accepts only field types carrying a marker the binding defines for permitted types.
- What another language needs from the standard is that everything in the schema is computable without Java: the type name and the tokens are plain strings, the digest is computed from the schema alone, an enum is its constant name, and a nested type's fields are described by the schema that travels in the entry.
- A nested record is fully described by its schema, so any language can write one. A nested `@AtomicSerial` class is encoded by Java code; another language can read it from the schema, and can write it only if that class's encoding is published.

### 3.3 Schema and identity

- The schema chain of a record entry is one record: the record's binary class name, no superclass, and one field per component in declared order. `java.lang.Record` is not in the chain. This is the shape the `@SerialEntry` path already produces (item 2 of section 2).
- The schema digest is the identity of the type (RL-JGDMS-0165). Two record classes with one name and different components are different types and may be stored side by side.
- Every token names the declared class in full: the scalar tokens and `enum:<className>` already do; the wrapper tokens of section 4 carry their element types.

### 3.4 Null

A component is never null unless it is annotated as nullable (RL-JGDMS-0174); the codec refuses a null in a never-null component before the constructor is called, and the constructor may still refuse a null in a nullable one (RL-JGDMS-0160). An entry field that is null and a template field that is a wildcard have the same encoding today (the agent, RELAYED: both are the single absent slice). So, as in the existing specification, a template cannot ask for "this field is null"; a filter can.

### 3.5 Equality

A record's own `equals` compares components. For the permitted types this agrees with a comparison of the encoded forms, with one exception: `-0.0` and `0.0` are unequal as `Float` or `Double` objects and equal once encoded, because the encoder normalises negative zero (RELAYED). Code that must agree with the space compares `MarshalledEntry` values (RL-JGDMS-0170).

### 3.6 Nested records

Admitted by RL-JGDMS-0171; the operator asked for this section at 03:07 +1000. A nested record is a record used as a component of a record entry or of another nested record. It need not implement `Entry`.

```java
// illustrative
record Location(String building, Integer floor) {}
record Printer(String name, Location where, DerSet<String> tags) implements Entry {}
```

**On the wire.** No new structure. A field of an entry already carries a value's schema digest and its payload, with the value's schema held in the entry's schema table; a nested `@AtomicSerial` object travels that way today. A nested record uses the same slot. Its schema is one record derived from its components, as in 3.1 and 3.3, and its payload is its components in declared order.

**Token.** The outer field's token names the nested type and includes its schema digest. The entry's digest then pins the whole shape: a change to `Location` makes a new version of `Printer` (RL-JGDMS-0165).

**What it gives.**

- Composition in place of inheritance: fields shared by several entry types go in one nested record.
- No cycles: a record is built from its parts, so a value can be a tree (through a list wrapper) and never a circular graph.
- Derived bytes: no user code encodes it, so the server's class-free check (8.2) stays complete and any language can read and write it.

**What it does not give.**

- A wildcard inside a nested value. A template field is matched as a whole. "Any building, floor 3" needs a filter, and the filter's class-free reader handles scalar fields only today (RELAYED), so reaching into a nested record is REQUIRED WORK.
- A query across types. Two entry types that share a nested record are still two types; a template names one.

**Rules proposed.**

- A nested value is converted strictly: its digest must equal that of the reader's nested class. The lenient rule of 6.3 applies at the top level only. See open question 7.
- Nesting depth is limited. The codec already limits nested `@AtomicSerial` objects to 16 levels (`DER/object/ObjectCodec.java:153,425`, VERIFIED); nested records take the same limit.
- A nested value may be null if the outer constructor accepts null.
- A template value for a nested field is an instance of the nested record, or, with no class, a nested value taken from an entry in hand.

REQUIRED WORK. Encode and decode of a nested record; the token form; class-free decode to a field map for `MarshalledEntry.get` (6.2).

### 3.7 Mapping Java classes to schema for other languages

Needed by RL-JGDMS-0172. What follows is PROPOSAL.

**What exists.** A schema record is already language-neutral in structure: a DER SEQUENCE of a type name, an optional parent digest, and an ordered list of fields, each a name and a wire-type token (`DER/schema/AtomicSerialSchemaRecord.java:41-44`, VERIFIED). The digest is computed over those bytes. What is Java-shaped is the vocabulary inside it:

| Part | Today | Why it matters to another language |
|---|---|---|
| Type name | the Java binary class name | an opaque string elsewhere; nested classes carry `$` |
| Scalar tokens | `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `java.lang.String`, `byte[]` (`SchemaGenerator.java:403-410`, VERIFIED for those shown there) | Java names and Java meanings: signed bytes, a `char` that is one UTF-16 unit |
| Enum token | `enum:<className>` | a Java class name inside the token |
| Nested object token | `@AtomicSerial` | names a Java annotation and no type |
| Collection tokens | `set:`, `list:`, `map:` and others with element tokens (RELAYED) | neutral in form |

**The mapping, in three parts.**

1. **A schema name for every type.** Each record entry, nested record and enum has a schema name: a dotted identifier chosen by its author. In Java it defaults to the binary class name and can be set by an annotation; in another language the binding's attribute states it. The schema name, not the class name, goes in the schema record. Two types in two languages are the same type when their schema records are byte-equal.
2. **A closed token vocabulary defined by the standard, with its meaning stated without reference to Java.** For each token: the value set, the DER encoding, and the canonical rules (for example an integer token states its range, and the decoder refuses a value outside it). Each binding then publishes one table from its own types to tokens. The Java table is section 3.2's listed types and the wrappers.
3. **A published description per type, and test vectors.** A tool emits, for each record entry class, its schema records in a readable form, its digest, and sample values with their exact bytes. Another binding generates its types from the description and proves itself against the vectors. The same description can be written first and Java records generated from it; the schema record is the pivot either way.

**Points where Java's types do not map cleanly.**

- `Character` is one UTF-16 code unit, which is not a character in Rust or Haskell. For entries meant to cross languages it is best left out or defined as a 16-bit unsigned number.
- Every component is a reference and may be null, so every field would map to an optional in other languages (neither Rust nor Haskell has a null reference; absence is a separate optional type). RL-JGDMS-0173 adds a mark to the schema so a field can be declared never null; see "The nullable mark" below.
- `String` is already safe: the encoder refuses an unpaired surrogate and the decoder accepts only strict UTF-8 (RELAYED).

**The nullable mark (RL-JGDMS-0173).** PROPOSAL for how it is carried.

- A field definition is today `SEQUENCE { wireName UTF8String, wireType UTF8String }`, and its decoder refuses trailing content (`DER/schema/AtomicSerialFieldDef.java:35-37,138`, VERIFIED). The mark becomes a third element: `nullable BOOLEAN DEFAULT TRUE`.
- DER omits an element equal to its default. A field that may be null is therefore encoded exactly as today, so no existing schema's bytes or digest change. Only a field declared never null carries the extra element, and its type gets a different digest from the nullable form.
- The mark is in the field definition, so it applies to every `@AtomicSerial` schema, not only to entries.
- In an entry, an absent slot in a never-null field is a fault: the server refuses the write (8.2) and a reader refuses the conversion. In a template any field may be absent, whatever its mark, because absence there is the wildcard.
- Another language maps a never-null field to a plain type and a nullable field to its optional type.
- For an `@AtomicSerial` class the mark is declared in the serial form, and that declaration alone decides the schema bytes. Bytecode analysis of the constructor checks the declaration, at build time and when the class is first used; a mismatch in either direction is an error (RL-JGDMS-0175). An existing class whose constructor checks a field for null therefore fails until its serial form is updated, which is how the existing cases are found; its digest changes when it is updated.
- Idioms the analysis must recognise: `Valid.notNull(` (the project's convenience method, `jgdms-platform/src/main/java/org/apache/river/api/io/Valid.java`; named by the operator at 03:28 +1000), `Objects.requireNonNull`, and an explicit comparison with null followed by a throw. A count on 2026-11-21 found `Valid.notNull(` in 12 files (16 calls) across the main sources of `jgdms-platform`, `jgdms-lib`, `jgdms-lib-dl` and `jgdms-jeri`; the services and other modules were not searched, and the other two idioms were not counted.
- Not settled: what the analysis does with a check made in a helper method it cannot follow.
- REQUIRED WORK: the field definition and its codec; the declaration for a record component and for a serial form; the analysis; the checks above.

**The choice for the operator (open question 8).** Part 2 can be done two ways:

- **Keep the present token spellings** and define them in the standard as fixed strings with neutral meanings. `java.lang.String` then simply means "UTF-8 string". Nothing built changes and no digest moves.
- **Rename the tokens** to neutral names. Cleaner to read, but every existing digest and the frozen test bytes change, and STD-006 version 1.0 is under a freeze held by another session (RL-JGDMS-0133).

The author would keep the spellings for existing tokens and use neutral forms only for tokens that are new (nested records, the wrappers, the schema-named enum).

Not checked: the field-definition record's own layout; whether STD-006 already states the token vocabulary normatively; the collection token grammar beyond the agent's report.

## 4. Collection and byte wrappers in the DER module

RL-JGDMS-0169 requires them. Names are illustrative.

| Wrapper | Implements | On the wire |
|---|---|---|
| `DerList<E>` | `java.util.List<E>`, unmodifiable | order kept, duplicates kept |
| `DerSet<E>` | `java.util.Set<E>`, unmodifiable | sorted by encoded octets, no duplicates |
| `DerMap<K,V>` | `java.util.Map<K,V>`, unmodifiable | sorted by encoded key |
| `DerBytes` | none | an octet string |

- Each is final, copies its input at construction, and refuses a null element or key.
- Each implements the JDK interface so that existing code can read it without conversion. Mutator methods throw.
- The developer chooses matching behaviour by choosing the type: two `DerList` values with the same elements in a different order do not match; two `DerSet` values do.

### 4.1 Encoding: two options

**Option W1 (recommended): encode by the declared token.** A component declared `DerSet<String>` gets the token the generator already produces for `Set<String>`, and its value is written as the existing typed top-level collection item (tag `[16]`, item 7 of section 2).

- The bytes are those STD-006 already defines for a sorted set of strings. A client in another language needs nothing new.
- The element type is in the token and so in the digest, with no change to the token grammar.
- No element is ever of type `any`.
- REQUIRED WORK: the record path calls the typed collection encoder and decoder; the wrappers are classified (list keeps order, set and map sort); the decoder checks each element against the token and builds the wrapper.

**Option W2: encode as an `@AtomicSerial` object.** The wrapper is `@AtomicSerial` with one field holding its contents, and travels as any nested object does.

- It works today as the value of a legacy entry field, with no codec change.
- Its contents are declared with a type variable, so the element type is `any` (item 4 of section 2). The wrapper's own digest is then the same for every element type; the element type would have to be added to the entry's token by a new grammar, and checked by new code at decode.

The author recommends W1 for record components. A wrapper can additionally be `@AtomicSerial` so that legacy entries may use it; that is a separate decision (open question 2).

## 5. Templates

A template is built, never instantiated (RL-JGDMS-0160).

```java
// PROPOSAL; illustrative
public final class EntryTemplate {
    public static <R extends Record & Entry> EntryTemplate of(Class<R> type);
    public static EntryTemplate like(MarshalledEntry example);   // class-free: type and schema from an entry in hand
    public EntryTemplate with(String field, Object value);       // returns a new template
    public MarshalledEntry.Kind kind();
    public byte[] schemaDigest();
}
```

- `with` checks that the field exists in the schema and that the value is of the field's type. A field not given is a wildcard.
- A template matches entries of exactly its own schema digest (RL-JGDMS-0164, RL-JGDMS-0165). It never matches another version of the same name.
- `like` answers seat 2's F8: a client with no class can still build a template, from an entry it has read.
- A template is immutable and is encoded as an entry body whose unset fields are absent, which is the form the server already matches.
- Stage 2 (RL-JGDMS-0168): for the legacy kind only, a template may be marked "this type and its subtypes".

## 6. `MarshalledEntry`

```java
// PROPOSAL; illustrative
public final class MarshalledEntry {                 // not an Entry; not Serializable
    public enum Kind { RECORD, LEGACY }              // LEGACY is stage 2

    public static <R extends Record & Entry> MarshalledEntry of(R entry);
    public static MarshalledEntry ofEncoded(byte[] body);   // verifies; throws on any fault

    public Kind kind();
    public String typeName();
    public byte[] schemaDigest();
    public List<String> fieldNames();
    public String fieldType(String name);            // the wire-type token
    public boolean isNull(String name);
    public Object get(String name);                  // class-free value, see 6.2

    public <R extends Record & Entry> R as(Class<R> type);                       // strict
    public <R extends Record & Entry> Converted<R> as(Class<R> type, Compatibility c);  // reader's choice
    public MarshalledEntry with(String field, Object value);                     // a new entry, same type

    public record Converted<R>(R entry, List<String> dropped, List<String> defaulted) {}
}
```

### 6.1 Rules

1. **Verified when constructed** (RL-JGDMS-0166). Every way of obtaining one runs the strict body decode and the canonical check of section 8.2. An instance that exists is well formed.
2. **No Java serialization.** The class is not `Serializable`. It crosses the wire only as its body inside the space protocol.
3. **Not an `Entry`.** It cannot be passed to a reflective entry path, which would see it as an entry with no fields (seat 2's F2).
4. **Immutable.** It holds a private copy of the body; accessors return copies or immutable values.
5. **Equality is byte equality of the body.** This is the comparison the space makes.
6. **No class is loaded from its contents.** `as` takes a class the caller already holds. The type name inside the body is data.

### 6.2 What a reader with no class sees

`get` returns `String`, the boxed scalars, `DerBytes`, and `DerList`/`DerSet`/`DerMap` of permitted values. An enum value is returned as its constant name, a `String`, because no enum class is loaded. A nested record or object is returned as a read-only map from field name to value, decoded against the schema that travels in the entry. No class is loaded for any of them.

### 6.3 Conversion

- **Strict `as(Class)`:** succeeds only when the body's schema digest equals the digest of the caller's class. Then each field is decoded, an enum constant is resolved against the component's declared enum type only, and the canonical constructor is called. If the constructor refuses, the conversion fails.
- **Reader's choice `as(Class, Compatibility)`** (RL-JGDMS-0135): the type names must be equal. Fields are paired by name. A pair with different tokens is refused. A field in the body that the class lacks is dropped and reported. A component the body lacks is passed as null and reported; if the constructor refuses null, the conversion fails. `Compatibility` has two values, `EXACT` and `ADDED_FIELDS`.
- The lenient form never changes what is stored and is never applied by the space. Seat 1's M8 (a reader silently accepting a different type) is answered by the name and token checks and by the report in `Converted`; whether that is enough is for the next review.

### 6.4 Entry classes the client does not have

By default the client has the class or reads the entry class-free (RL-JGDMS-0155). A client that wants classes fetched configures a resolver; the resolver accepts a class only when its digest is in the djinn policy or the SPIFFE policy (RL-JGDMS-0156). The resolver is outside this design. `MarshalledEntry` has no method that takes a class name.

## 7. The space interface

Installed locally at the client, built for release 21 (RL-JGDMS-0142, RL-JGDMS-0159). Illustrative:

```java
// PROPOSAL; illustrative
public interface Space {
    <R extends Record & Entry> Lease write(R entry, Transaction txn, long lease);
    Lease write(MarshalledEntry entry, Transaction txn, long lease);

    MarshalledEntry read(EntryTemplate tmpl, Filter filter, Transaction txn, long timeout);
    MarshalledEntry take(EntryTemplate tmpl, Filter filter, Transaction txn, long timeout);
    MarshalledEntry readIfExists(EntryTemplate tmpl, Filter filter, Transaction txn, long timeout);
    MarshalledEntry takeIfExists(EntryTemplate tmpl, Filter filter, Transaction txn, long timeout);

    Collection<MarshalledEntry> take(Collection<EntryTemplate> tmpls, Filter filter,
                                     Transaction txn, long timeout, long maxEntries);
    MatchSet contents(Collection<EntryTemplate> tmpls, Filter filter, Transaction txn,
                      long lease, long maxEntries);

    EventRegistration notify(EntryTemplate tmpl, Filter filter, Transaction txn,
                             RemoteEventListener listener, long lease);
    EventRegistration registerForAvailabilityEvent(Collection<EntryTemplate> tmpls, Filter filter,
                             Transaction txn, boolean visibilityOnly,
                             RemoteEventListener listener, long lease);
}
```

- **Every result is a `MarshalledEntry`** (RL-JGDMS-0167). The client converts what it wants.
- **One filter per operation.** `filter` may be null. Where an operation takes several templates, the one filter applies to the whole operation, which is the server's existing rule. Rev 1's filter-per-template `Query` is gone. This answers seat 1's H3.
- **Events.** A `notify` event carries no entry contents. An availability event carries the entry, as a `MarshalledEntry` (RL-JGDMS-0167).
- **Transactions and leases** are unchanged and are not designed here.
- **`snapshot`** has no counterpart: a `MarshalledEntry` or an `EntryTemplate` is already the encoded form and can be reused.

### 7.1 The old interface (stage 2)

`JavaSpace` and `JavaSpace05` are presented by a resident wrapper that decorates `Space` (RL-JGDMS-0160, RL-JGDMS-0167, RL-JGDMS-0168).

- It encodes a legacy entry at the call and decodes a new object on return. Mutation of either object afterwards changes nothing in the space.
- It turns a legacy template instance into a template of the legacy kind: non-null fields are values, null fields are wildcards, and the "subtypes" mark is set.
- A conversion failure fails the call.
- A legacy field whose class is not available locally, or cannot be encoded over DER, is refused.
- It needs the legacy kind of `MarshalledEntry`, which carries the class name and superclass chain. Until that exists the wrapper cannot be built.

## 8. Server

### 8.1 Type and matching

REQUIRED WORK, all of it.

- For the record kind, the stored entry's type is its schema digest. Entries are held and looked up by digest, and a template is compared only with entries of its own digest (seat 1's M1; item 9 of section 2).
- The one-hash-per-name check is removed for the record kind (RL-JGDMS-0165; item 10 of section 2). The legacy kind keeps it.
- For the record kind the server takes the type name from the verified schema chain and ignores, or refuses on mismatch, every other copy of the type that travels with the entry (seat 1's H1).
- The server loads no entry class.

### 8.2 Canonical check at write

RL-JGDMS-0170 requires it. On write the server decodes each field of a record-kind entry class-free with the strict decoder, against the token in the entry's own schema, and refuses the write on any fault. For listed scalars, enums, the wrappers and nested records the class-free check is complete, because their encodings are derived and no user code produces bytes. For a nested `@AtomicSerial` class the server checks the structure against the schema but cannot check that equal values give equal bytes, since the class's own `serialize` code produced them; a writer whose code is not deterministic harms only the matching of its own entries.

- A template gets the same check.
- Cost: one strict pass over each entry at write. Not measured.
- This closes seat 1's M5 for record entries. Legacy entries with nested `@AtomicSerial` values are a stage 2 question.

### 8.3 Filters

A filter reads field values class-free after the byte match, as today. REQUIRED WORK: the schema view that filter authoring uses rejects `@SerialEntry` classes today (seat 2's F6, RELAYED) and must be taught record entries.

## 9. What changes elsewhere

| Where | Change |
|---|---|
| STD-006 version 1.0 | A section for record entries: definition, permitted types, the one-record chain, identity, the wrappers and their encoding, the canonical check at write. The freeze is held by another session (RL-JGDMS-0133); that session has not been told. |
| `jgdms-der` | The wrappers; a record entry point in the entry codec; typed collection encode and decode for record components. |
| New local client module | `Space`, `EntryTemplate`, `MarshalledEntry`. |
| Outrigger service | Section 8. |
| Reflective entry paths (`LookupAttributes`, `EntryRep` construction from fields, the schema generator's field walk) | Refuse a record until taught, because reflection sees no public fields in a record (seat 2's F2, RELAYED). |
| Client library comparisons | `LookupAttributes.equal` and `AbstractEntry.equals` compare objects (RELAYED). RL-JGDMS-0170 requires the marshalled form; how, for legacy entries, is not designed here. |
| Lookup attributes and entry beans | No change; they stay legacy (RL-JGDMS-0163). |
| `EntryRep.entry()` codebase path | Removed or made subject to RL-JGDMS-0155 and RL-JGDMS-0156 (item 12 of section 2). |

## 10. The board's findings, one by one

HIGH findings are answered in rev 1's section 11.1 and above. MEDIUM findings:

| Finding | Answer in this revision |
|---|---|
| Seat 1 M1, digest not enforced in matching | 8.1 |
| Seat 1 M2, server keeps the first hash for a name forever | 8.1; RL-JGDMS-0165 |
| Seat 1 M3, two-record chain and two digests for one record | 3.1, 3.3 |
| Seat 1 M4, digest does not name a nested component's class | 3.2.2: the token names the class (REQUIRED WORK) |
| Seat 1 M5, decoder checks structure only | 8.2 |
| Seat 1 M6, `MarshalledEntry` unverified by Java deserialization, and an `Entry` | 6.1 |
| Seat 1 M7, the sender still chooses classes | 3.2, 6.2, 6.3 |
| Seat 1 M8, lenient conversion | 6.3; left for the next review to judge |
| Seat 1 M9, events as a read channel | 7 |
| Seat 1 M10, immutability only shallow | 3.2, 3.2.1 |
| Seat 2 F4, `GetEntryArg` default unreachable | Not used: 3.1 decodes through the canonical constructor |
| Seat 2 F5, release 16 | RL-JGDMS-0159 |
| Seat 2 F6, filter authoring | 8.3 |
| Seat 2 F7, lookup attributes | RL-JGDMS-0163 |
| Seat 2 F8, no template without a class | 5 |
| Seat 2 F9, no migration from a legacy class to a record | Not designed. A legacy class and a record of the same name have different digests and can coexist. See open question 4; the guide itself waits for 4.0.0 (RL-JGDMS-0148). |
| Seat 2 F10, wire names tied to component names | Accepted as written in 3.1. See open question 3. |

The LOW findings of both seats are not taken one by one here.

## 11. Open questions for the operator

1. **Further listed types.** Which JDK value types join the listed set, for example an identifier, a timestamp or a decimal? None is encodable today (the agent, RELAYED), and the analysis cannot admit them (3.2.1).
2. **Wrappers in legacy entries.** Should the wrappers also be `@AtomicSerial`, so a legacy entry can hold one? It costs a second encoding of the same class (4.1, option W2).
3. **Renaming a component.** As written, renaming a component makes a new type. An annotation giving a component a separate wire name would avoid that at the cost of one more thing to specify.
4. **Moving data from a legacy class to a record.** Is a server-side "take and write the converted entry" operation wanted, or is this left to client code?
5. **Explicit null in a template.** Left to filters (3.4). Confirm.
6. **The `Space` name and package**, and whether the old interface names are kept only on the wrapper.
7. **Lenient conversion inside a nested record.** Proposed: strict inside, lenient at the top level only (3.6).
8. **Token spellings.** Keep the present spellings as fixed strings with neutral meanings, or rename them (3.7).
9. **`Character` in entries meant for other languages.** Leave out, or define as a 16-bit unsigned number (3.7).
11. **Default for the nullable mark.** ANSWERED for record entry components by RL-JGDMS-0174: never null unless annotated nullable. Still open: whether nested records follow the same default (the author would say yes) `@AtomicSerial` classes are settled by RL-JGDMS-0175: nullable unless declared never null in the serial form.
10. **Schema name.** Confirm that a type carries a schema name separate from its Java class name, defaulting to the class name (3.7). This would also answer question 3 for type names, though not for component names.

## 12. Hazards, in the order the author would address them

1. The canonical check (8.2) is the one new piece on the server's write path. Its cost is not measured.
1a. The analyser (3.2.1) is new code whose mistakes are silent: a wrong "proven" admits a mutable type. It needs its own tests, including classes built to deceive it.
2. Option W1 depends on the typed top-level collection item, which is defined but, as reported, unused. Its decoder has not been read.
3. Matching by digest (8.1) changes how entries are indexed; the six match sites and the type table all assume class names.
4. The stage 2 wrapper cannot be judged until the legacy kind is designed; nothing here proves it fits.
5. STD-006 is frozen by another session at the moment this design asks for a new section in it.

## 13. Not checked

- Nothing was compiled or run.
- The typed collection encoder and decoder, the collection classification for a class the classifier does not know, and the class-free decode paths were not read by this session.
- The six Outrigger match sites, `LookupAttributes` and `AbstractEntry` are RELAYED.
- Whether a record's canonical constructor can be invoked by the codec across module boundaries without an `opens` was not examined; there is no `module-info.java` in the tree (RELAYED).
- Lease, transaction and event-registration objects, which are themselves proxies, are outside this design.
- The Outrigger and Mahalo investigation scope (`docs/SOW-Outrigger-Mahalo-CEL-Code-Free-Investigation-2026-11-20.md`) predates most of these rulings and has not been edited.
