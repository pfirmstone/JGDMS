# Design: record entries, `MarshalledEntry` and the space API that returns it (DRAFT rev 3)

- **Status:** DRAFT rev 3, sections 1, 5, 8, 10 and 13 revised between 12:55 and 13:00 +1000 for RL-JGDMS-0192, RL-JGDMS-0194, RL-JGDMS-0196 and RL-JGDMS-0198 to RL-JGDMS-0207; section 8.2 added at 14:50 +1000. Not reviewed. Nothing in it is built. It replaces rev 2 (`docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-rev2-2026-11-21.md`), which a board of two reviewers found not ready to build from.
- **Date:** 2026-11-21. **Written by:** Claude Fable 5.2, proxy-board review session, at the operator's request (04:27 +1000).
- **Read against:** JGDMS trunk `21965190d`. Uncommitted.
- **Rulings it rests on:** RL-JGDMS-0134 to RL-JGDMS-0207, cited by ID only (RL-JGDMS-0192 to RL-JGDMS-0197 were registered by another session; this design uses RL-JGDMS-0192, RL-JGDMS-0194 and RL-JGDMS-0196); the register holds their wording and status. RL-JGDMS-0169, RL-JGDMS-0171 and RL-JGDMS-0176 were replaced after rev 2 and are not used here.
- **Review wanted:** a review by a model other than Fable before a scope is written from it.

## 0. How to read this

- **VERIFIED** means this session opened that line on 2026-11-20 or 2026-11-21. Nothing was compiled or run.
- **RELAYED** means taken from a review or agent report and not re-opened. The files are `docs/REVIEW-entry-records-design-rev2-opus-2026-11-21.md` (rev 2 Opus), `docs/REVIEW-entry-records-design-rev2-sonnet-2026-11-21.md` (rev 2 Sonnet), `docs/REVIEW-entry-records-design-opus-2026-11-21.md` (seat 1), `docs/REVIEW-entry-records-design-sonnet-2026-11-21.md` (seat 2) and `docs/REVIEW-entry-encoding-canonical-matching-sonnet-2026-11-21.md` (the agent).
- **PROPOSAL** is the author's design. Every class, method, annotation and token name in a proposal is illustrative.
- **REQUIRED WORK** marks something the design needs that the code does not do today.
- **NOT RULED** marks a recommendation of the author that the operator has not decided.
- Paths are relative to `JGDMS/JGDMS`. `DER/` is `jgdms-der/src/main/java/au/net/zeus/jgdms/der/`.

## 1. What is decided

Each row is a ruling. This section restates none of them; it says only which part of the design each one governs.

| Subject | Rulings |
|---|---|
| Fields need not be non-final; new entries preferred over mutation | RL-JGDMS-0134 |
| Version compatibility is the reader's choice at conversion; only added fields are compatible | RL-JGDMS-0135, RL-JGDMS-0137 |
| Specified in STD-006 version 1.0 | RL-JGDMS-0139, RL-JGDMS-0141 |
| Client interface installed locally; Java 21 at compile time; deployment on DirtyChai | RL-JGDMS-0142, RL-JGDMS-0159, RL-JGDMS-0179 |
| No live interoperation with older releases; old application code through resident wrappers | RL-JGDMS-0146, RL-JGDMS-0147 |
| Entry classes: download off by default; trusted by digest in the djinn or SPIFFE policy | RL-JGDMS-0155, RL-JGDMS-0156, RL-JGDMS-0158 |
| Coexisting versions of one class name; a cap on digests per name | RL-JGDMS-0157, RL-JGDMS-0165, RL-JGDMS-0181 |
| Constructor validates; template built with a builder; old interface decorates the new | RL-JGDMS-0160 |
| Validity is checked at conversion; the space checks form | RL-JGDMS-0178 |
| The six statements (records, no inheritance, identity, `MarshalledEntry`, marshalled by default) | RL-JGDMS-0161 to RL-JGDMS-0167 |
| Permitted component types; immutability recommended and not checked; no enums in the first version | RL-JGDMS-0185 |
| Primitives permitted, never null | RL-JGDMS-0180 |
| Cycles in the type graph refused | RL-JGDMS-0177 |
| Collection wrappers: immutable, defined in `jgdms-platform`, encoded by the DER module by declared token | RL-JGDMS-0182, RL-JGDMS-0186 |
| Two kinds of `MarshalledEntry`; records first, legacy kind and wrapper second | RL-JGDMS-0168 |
| No class-free `MarshalledEntry.with` | RL-JGDMS-0188 |
| Old interface: a conversion failure fails the call and carries the marshalled entry | RL-JGDMS-0189 |
| Matching on the marshalled form everywhere; server verifies canonical encoding at write | RL-JGDMS-0170 |
| DER is a configuration concern; canonical encoding is a constraint; one digest per type whatever the protocol | RL-JGDMS-0183, RL-JGDMS-0184, RL-JGDMS-0191 |
| A cross-language mapping from Java classes to schema is needed | RL-JGDMS-0172 |
| The nullable mark in the serial format; record components never null unless annotated | RL-JGDMS-0173, RL-JGDMS-0174 |
| `@AtomicSerial` nullability declared and checked; a separate piece of work | RL-JGDMS-0175, RL-JGDMS-0187 |
| No writer stamp in this revision | RL-JGDMS-0190 |
| The new space proxy is a dynamic proxy needing no downloaded code; the local client code is service API | RL-JGDMS-0198 |
| What service API means; the space's service API is `tuple-space-api.jar`; no binding interfaces, which remain only in the download jar for JOSS; decorators present the old interfaces from their own compatibility jar; a service registers a naked and a decorated proxy; decorators are stateless and may be trusted downloads | RL-JGDMS-0199 to RL-JGDMS-0207 |
| EntryRep v2 has no codebase field; interface identity is the name, hashes computed locally; the hash is SHA-256 | RL-JGDMS-0192, RL-JGDMS-0194, RL-JGDMS-0196 |

Everything from section 2 onward is PROPOSAL unless labelled otherwise.

## 2. What exists today that the design builds on

1. `@SerialEntry` documents a record as an entry, with a worked example (`jgdms-platform/src/main/java/net/jini/core/entry/SerialEntry.java:102-127`). VERIFIED. No record entry exists in the main sources (RELAYED).
2. The `@SerialEntry` encode path builds a schema chain of exactly one record, with no superclass link (`DER/entry/EntryRepV2Codec.java:299-315`; the "later refinement" wording is in its Javadoc at line 289). VERIFIED; line numbers corrected from the rev 2 reviews.
3. That path takes each wire field's type as a `Class` (`jgdms-platform/.../net/jini/core/entry/EntryWireField.java:64,74`; `EntryRepV2Codec.java:300,304`), so type arguments are lost before the schema is built. VERIFIED.
4. The schema generator records an element type only when the raw class is a `Collection` or `Map`; for any other parameterised class it maps the raw class and drops the arguments (`DER/schema/SchemaGenerator.java:607-627`). VERIFIED. An unbounded wildcard and a type variable become `any`; a bounded wildcard resolves to its bound's token; a bare `Object` field is refused (lines 637-662, RELAYED; rev 2 stated this too broadly).
5. Scalar tokens: boxed and primitive types share one token, for example `Integer` and `int` both give `int` (`SchemaGenerator.java:425-431,480-482`, RELAYED; rev 2 cited the Javadoc table).
6. A nested `@AtomicSerial`, interface or abstract field's token is the bare marker `@AtomicSerial` and names no class (`SchemaGenerator.java:484-507`). VERIFIED.
7. A collection cannot be the top-level value of a field: the object-stream layer refuses any object whose class is not `@AtomicSerial` (`DER/stream/DerObjectStreamCodec.java:1207-1211`, VERIFIED).
8. A typed top-level collection item exists in the stream format, tag `[16]`, carrying the collection's wire-type token and then the collection (`DerObjectStreamCodec.java:91-95,181-185`). VERIFIED that it is defined. Its writer `writeCollection` is package-private and has no caller in main sources; six tests call it (RELAYED).
9. Sets and maps declared with a hash-ordered or bare interface type are written sorted by encoded octets; lists keep their order (`DER/object/ObjectCodec.java:1586-1596`, VERIFIED approximately).
10. **The decoder loads the class named in the bytes before it checks that class against the declared slot.** `ObjectCodec.decodeHierarchy` takes the class name from the wire chain and calls `loadClass` at line 709; the admission check `admissibleConstructClass` follows at line 714; every class in the chain is loaded at line 737. The enum leaf reads a class name from the bytes and loads it at line 2124. VERIFIED on 2026-11-21 after the rev 2 Opus review reported it.
11. Outrigger matches by comparing each non-wildcard field's bytes (`services/outrigger/outrigger-dl/.../proxy/EntryRep.java:1222-1236`). VERIFIED. Type is decided by class name, not digest; there are six match sites (RELAYED).
12. The space keeps one hash per class name and refuses a second (`OutriggerServerImpl.typeCheck` at line 1495, `checkClass` at 1524; 17 call sites of `typeCheck`, all in that file). RELAYED for the line numbers and count.
13. `EntryRep.entry()` loads a class named by the writer from a codebase named by the writer (`EntryRep.java:837,846-849`, VERIFIED on 2026-11-20).
14. `net.jini.core.constraint.MarshallingFormat` is an invocation constraint that selects a wire format by name; its constants are `ATOMIC_DER` and `JOSS`, and its documentation ties the name to a `MarshalFactoryProvider` in `jgdms-der` (`jgdms-platform/src/main/java/net/jini/core/constraint/MarshallingFormat.java:35-79`). VERIFIED.
15. `jgdms-der` compiles for release 25 and its build file says it will not run on Java 21 (`jgdms-der/pom.xml:66-72`); it depends on `jgdms-platform` (`jgdms-der/pom.xml:45`). VERIFIED. `jgdms-platform` compiles for 21; `jgdms-lib-dl` and `outrigger-dl` compile for 8 (RELAYED).
16. Existing size limits: 1 MiB a field slice, 8 MiB an entry body, 256 schemas in an entry's schema table, nesting depth 16 (`EntryRepV2Codec.java:95-104`, RELAYED; `ObjectCodec.java:153,425`, VERIFIED).
17. Existing entry classes: 12 subclasses of `AbstractEntry` in `jgdms-lib-dl` with 28 public fields: 24 `String`, and one each of `Class`, `StatusType`, a raw `Set` and `MarshalledObject`. No enum and no array (RELAYED).

## 3. Record entries

### 3.1 Definition

A record entry is a Java record that implements `net.jini.core.entry.Entry`.

- It needs no annotation and no hand-written wire form. Its wire fields are its components, in declared order; a component's wire name is its name and its wire type is its declared generic type.
- **Order is part of the type.** Reordering components makes a new type with a new digest, as renaming one does.
- It is encoded once, from its accessors, when a `MarshalledEntry` is made from it (RL-JGDMS-0185). The marshalled form is the entry's value from then on. An accessor the author has overridden is called like any other; what it returns at that moment is what is stored.
- It is decoded through its canonical constructor, which is where validity is checked (RL-JGDMS-0178). The constructor runs on values a writer chose and must treat them as untrusted input.
- It is a top-level or member record. A local, anonymous or hidden class is refused, because its binary name is chosen by the compiler or the run and so its digest is not stable. PROPOSAL.
- It has at least one component. PROPOSAL; STD-006 already says so for a schema record (RELAYED).
- A record that also carries `@SerialEntry` or `@AtomicSerial`, or that declares `entryForm`, `serialize` or a `GetEntryArg` constructor, is refused. There is one path for records, so one record has one digest.
- A record entry is never used as a template (section 6).
- A record class that breaks a rule of this section or of 3.2 is refused with an unchecked exception that names the class and the component, the first time the codec meets the class.

REQUIRED WORK.
- A codec entry point that derives the wire fields from `Class.getRecordComponents()` using `RecordComponent.getGenericType()`, so that type arguments reach the schema.
- How the codec reaches the constructor and accessors of a record that is not public, under the security manager. Not designed; open question 6.

### 3.2 Permitted component types

RL-JGDMS-0185 and RL-JGDMS-0180. Immutability of a component type is recommended to authors and is not checked.

| Kind | Types | Token |
|---|---|---|
| Listed scalar | `String`; `Boolean`, `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `Character` and their primitives | existing scalar tokens; a primitive and its box share a token (item 5 of section 2) |
| Wrapper | the list, set, map and byte-sequence wrappers of section 4, with type arguments that are concrete and themselves permitted | section 4.1 |
| Nested record | a record that obeys 3.1 except that it need not implement `Entry` | REQUIRED WORK, section 3.6 |
| Final class | a final `@AtomicSerial` class, subject to the condition below | REQUIRED WORK: the token must name the class |

Refused: enums (RL-JGDMS-0185), arrays, JDK collections, interfaces, abstract and non-final classes, `Object`, type variables, wildcards, raw types, and a type that contains itself (RL-JGDMS-0177).

**Why the declared type must be final.** It is not for immutability. It makes the declared type the only type that can arrive, so a writer never names a class for the reader to load.

**Condition on a final `@AtomicSerial` class. NOT RULED; open question 1.** Its wire form comes from its serial form and its own `serialize` code, not from its Java fields. A serial-form field typed as an interface, an abstract class or `Object` is a slot in which the writer names a class. The author proposes that such a class is permitted as a component only when no field of its serial form, at any depth, is such a slot. The alternative is to leave final classes out of the first version and admit records only.

### 3.3 Cycles

RL-JGDMS-0177.

- The schema generator walks the component types, including wrapper type arguments, and refuses a type it reaches from itself. This also makes the walk terminate. REQUIRED WORK.
- The server needs no cycle check. A nested token contains the nested type's digest (3.4), and a digest cannot contain itself, so a cyclic type cannot be written down in a schema.
- A value is therefore always a finite tree of bounded depth (item 16 of section 2).

### 3.4 Schema and identity

- The schema chain of a record entry is one record: the type name, no superclass, and one field per component in declared order. `java.lang.Record` is not in the chain.
- The digest of that schema record in its DER form is the identity of the type, whichever protocol carries the entry (RL-JGDMS-0165, RL-JGDMS-0191).
- Two record classes with one name and different components are different types and may be stored side by side, up to the cap (RL-JGDMS-0181).
- The type name is the Java binary class name, carried as an opaque string. Another language copies it. NOT RULED; open question 2.
- Each token identifies the declared type: a scalar token by its name; a wrapper token by its kind and element tokens (4.1); a nested token by the nested type's name and digest (3.6).
- The nullable mark of each field is part of the schema record and so part of the identity (3.5).

### 3.5 Null

One rule, which replaces the statements rev 2 made in four places.

- A component is never null unless it carries the nullable annotation (RL-JGDMS-0174). A primitive component is never null and cannot carry the annotation (RL-JGDMS-0180).
- The annotation is one annotation defined by JGDMS in `jgdms-platform`, with a record-component target and run-time retention. No other annotation is read. PROPOSAL.
- The codec refuses a null in a never-null component, at encode and at decode, before the constructor is called. The constructor may also refuse a null in a nullable component.
- Nested records follow the same default. NOT RULED; open question 3.
- In the schema the mark is the third element of a field definition, `nullable BOOLEAN DEFAULT TRUE` (RL-JGDMS-0173). A field definition is today `SEQUENCE { wireName UTF8String, wireType UTF8String }` and its decoder refuses trailing content (`DER/schema/AtomicSerialFieldDef.java:35-37,137-138`, VERIFIED).
- DER omits an element equal to its default, so a nullable field is encoded as today. **The decoder must refuse an explicit TRUE.** Without that, one type has two schema encodings and two digests. REQUIRED WORK.
- Under RL-JGDMS-0174 most record fields are never null, so most record field definitions carry the element.
- At the top level a null field is the absent slice, which is also a template's wildcard (RELAYED). So a template cannot ask for "this field is null"; a filter can. Inside a nested value a null is a DER NULL (RELAYED) and cannot be a wildcard.
- On write the server refuses an entry in which a never-null field is absent (9.2). A template is exempt: any field of a template may be absent.
- Another language maps a never-null field to a plain type and a nullable field to its optional type.

The nullability of existing `@AtomicSerial` classes (RL-JGDMS-0175) is separate work (RL-JGDMS-0187) and is not designed in this document. What this design needs from it is only the field-definition element above.

### 3.6 Nested records

A nested record is a record used as a component of a record entry, of another nested record, or as an element of a wrapper.

```java
// illustrative
record Location(String building, int floor) {}
record Printer(String name, Location where, ValueSet<String> tags) implements Entry {}
```

- **On the wire.** No new structure. A field already carries a value's schema digest and its payload, with the value's schema held in the entry's schema table; a nested `@AtomicSerial` object travels that way today. A nested record uses the same slot. Its schema is one record derived from its components, and its payload is its components in declared order.
- **Token.** It names the nested type and includes its schema digest, for example `record:<typeName>#<digest in hex>`. The exact form is REQUIRED WORK; a field's token is limited to 1024 bytes (`AtomicSerialFieldDef`, VERIFIED), which bounds the type name.
- **Effect on identity.** The entry's digest pins the whole shape: a change to `Location` makes a new version of `Printer`.
- **Conversion is strict inside.** A nested value's digest must equal that of the reader's nested class. The lenient rule of 7.3 applies at the top level only. NOT RULED; open question 4.
- **Depth** is limited to the codec's existing 16 levels.
- **Templates.** A template field holding a nested value is matched as a whole. "Any building, floor 3" needs a filter. A template value for a nested field is an instance of the nested record, or, with no class, a nested value taken from an entry in hand.

REQUIRED WORK. Encode and decode of a nested record; the token form; class-free decode to a field map (7.2); a filter's class-free reader reaching into a nested value, which handles scalar fields only today (RELAYED).

### 3.7 Equality

A record's own `equals` compares components. It can differ from a comparison of the marshalled forms in three cases: `-0.0` and `0.0` are unequal as objects and equal once encoded (RELAYED); a component whose type is mutable can change after encoding; and an overridden accessor can return something other than the field. Code that must agree with the space compares `MarshalledEntry` values (RL-JGDMS-0170).

### 3.8 Other languages

RL-JGDMS-0172 states the need. The first version meets it only in part, and the parts not met are listed.

**What a schema gives another language.** A type name, an ordered list of fields, and for each a name, a token and a nullable mark. The tokens of record entries are scalars, wrappers and nested records; none is a Java object layout unless a final `@AtomicSerial` class is used (3.2).

**Token spellings.** Keep the present spellings as fixed strings whose meaning the standard defines without reference to Java; `java.lang.String` then means "UTF-8 string". No digest moves. New tokens (nested records, the byte wrapper if it needs one) take neutral names. NOT RULED; open question 5.

**`Character`.** The encoder already writes a `char` as a code point and refuses an unpaired surrogate (RELAYED), so it is a Unicode scalar value in the basic plane. The standard should define it that way. Rev 2 called it a UTF-16 code unit, which was wrong.

**What another language cannot do from this design.** Each needs normative text in STD-006 before a binding can be written:

- compute a digest: the hash, the exact bytes hashed;
- write a field slice: the item encodings for each token, including the typed collection item;
- parse the collection token grammar;
- call a space: the space interface of section 8 is a Java interface carried by JERI, and no protocol is defined for a caller that is not Java;
- express a type with alternatives (a Rust enum with data, a Haskell data type with several constructors): there is no mapping, because interfaces and enums are refused.

A published description of each type and test vectors with exact bytes are REQUIRED WORK, as in rev 2.

## 4. The collection and byte wrappers

RL-JGDMS-0182. They are defined in `jgdms-platform`, compile for Java 21, and refer to nothing in `jgdms-der`. Names are illustrative and carry no `Der` prefix (RL-JGDMS-0183).

| Wrapper | Implements | On the wire |
|---|---|---|
| `ValueList<E>` | `java.util.List<E>`, unmodifiable | order kept, duplicates kept |
| `ValueSet<E>` | `java.util.Set<E>`, unmodifiable | sorted by encoded octets, no duplicates |
| `ValueMap<K,V>` | `java.util.Map<K,V>`, unmodifiable | sorted by encoded key |
| `ValueBytes` | none | an octet string |

- Each is final, copies its input at construction, and refuses a null element, key or value. Mutator methods throw.
- `equals` and `hashCode` follow the `List`, `Set` and `Map` contracts; `ValueBytes` compares by content.
- Wrappers may be nested (`ValueList<ValueSet<String>>`) and may hold nested records, within the depth limit.
- **Elements equal once encoded.** A `ValueSet<Double>` can hold both `0.0` and `-0.0`, which encode to the same bytes. The wrapper cannot know this, since it has no codec. The codec refuses to encode a set with two elements, or a map with two keys, that encode alike. PROPOSAL.
- The author chooses matching behaviour by choosing the type: two lists with the same elements in a different order do not match; two sets do.

### 4.1 Encoding

RL-JGDMS-0186: by the declared token, as the typed top-level collection item (item 8 of section 2).

- **The mapping from wrapper to token is explicit:** list wrapper to `list:`, set wrapper to `set:`, map wrapper to `map:`, each followed by its element tokens. It does not go through the generator's classification of collection interfaces, which would change the token if a wrapper later implemented another interface (RELAYED).
- The byte wrapper takes the existing `byte[]` token and encoding. To be confirmed when the encoder is read.
- **The token inside the item must equal the token in the schema.** The item carries its own copy of the collection token. The encoder writes the same one, and the server and every decoder refuse a slice in which they differ. The same rule applies to scalars: the item's type must be the token's type.
- The decoder checks each element against the element token and builds the wrapper.

REQUIRED WORK. The record path calls the typed collection writer and reader, which are package-private in another package today. **The typed collection reader has no production caller and has not been read by this session or by either reviewer; it must be read and tested before anything depends on it.**

## 5. The codec as a configured provider

RL-JGDMS-0183, RL-JGDMS-0184, RL-JGDMS-0191. Requested by the operator because JERI is pluggable and another protocol may be needed.

- **The entry API names nothing from DER.** Record entries, the wrappers, `MarshalledEntry`, `EntryTemplate` and the space interface compile against Java 21 modules only (RL-JGDMS-0179).
- **A provider interface** in `jgdms-platform` states what the entry API needs of a codec:
  - encode a record to a body, and decode a body to a record of a class the caller supplies;
  - verify that a body is well formed and canonical;
  - give a class-free view of a body: type name, digest, field names, tokens and values;
  - compute a type's schema digest.
- **`jgdms-der` supplies the implementation**, found at deployment the way a marshal factory provider is found today (item 14 of section 2). How exactly was not read; REQUIRED WORK.
- **A new invocation constraint states the property** (illustrative: `CanonicalEncoding.YES`): each value has exactly one encoding and a receiver refuses any other. A space requires it. A protocol may carry entries only if it satisfies it. ATOMIC DER does; Java serialization does not.
- **`MarshallingFormat` still names the format.** The two constraints work together.
- **One space, one encoding.** Matching is on the marshalled form, so a space and all of its clients use the same format. Configuration chooses it for the deployment.
- **One digest per type.** The digest is SHA-256 (RL-JGDMS-0196), defined once, over the DER form of the schema record. A second protocol encodes values its own way and must still reproduce that digest, so it must implement that one DER structure.

## 6. Templates

A template is built, never instantiated (RL-JGDMS-0160).

```java
// PROPOSAL; illustrative
public final class EntryTemplate {
    public static <R extends Record & Entry> EntryTemplate of(Class<R> type);
    public static EntryTemplate like(MarshalledEntry example);   // class-free
    public EntryTemplate with(String field, Object value);       // returns a new template
    public MarshalledEntry.Kind kind();
    public byte[] schemaDigest();
}
```

- `with` checks that the field exists and that the value is of the field's type, and encodes it. A null value is refused; a field not given is a wildcard.
- A template matches entries of exactly its own schema digest. It never matches another version of the same name.
- `like` takes the type and schema from an entry in hand, so a client with no class can build a template.
- A template is immutable and is encoded as an entry body whose unset fields are absent.
- Stage 2 (RL-JGDMS-0168): for the legacy kind only, a template may be marked "this type and its subtypes".

## 7. `MarshalledEntry`

```java
// PROPOSAL; illustrative
public final class MarshalledEntry {                 // not an Entry; not Serializable
    public enum Kind { RECORD, LEGACY }              // LEGACY is stage 2

    public static <R extends Record & Entry> MarshalledEntry of(R entry);
    public static MarshalledEntry ofEncoded(MarshallingFormat format, byte[] body)
            throws EntryFormatException;             // verifies with the configured codec

    public Kind kind();
    public MarshallingFormat format();
    public String typeName();
    public byte[] schemaDigest();
    public List<String> fieldNames();
    public String fieldType(String name);            // the token
    public boolean isNullable(String name);
    public boolean isNull(String name);
    public Object get(String name);                  // class-free value, see 7.2

    public <R extends Record & Entry> R as(Class<R> type) throws EntryConversionException;
    public <R extends Record & Entry> Converted<R> as(Class<R> type, Compatibility c)
            throws EntryConversionException;

    public record Converted<R>(R entry, List<String> dropped, List<String> absent) {}
}
```

There is no method that changes a field without the class (RL-JGDMS-0188). A Java client makes a changed entry by constructing a new record.

### 7.1 Rules

1. **Verified when constructed** (RL-JGDMS-0166). Every way of obtaining one runs the codec's verification. An instance that exists is well formed. It is not thereby valid: no constructor has run (RL-JGDMS-0178).
2. **No Java serialization.** It crosses the wire only as its body inside the space protocol.
3. **Not an `Entry`.**
4. **Immutable.** It holds a private copy of the body.
5. **Equality is byte equality of the body**, with the format.
6. **No class is loaded from its contents.** See 7.4.
7. **Exceptions.** `EntryFormatException` (checked) for bytes that are malformed or not canonical. `EntryConversionException` (checked) when conversion fails; it carries the `MarshalledEntry` (RL-JGDMS-0189) and, where there is one, the constructor's exception as its cause.

### 7.2 What a reader with no class sees

`get` returns `String`, the boxed scalars, the byte wrapper, and list, set and map wrappers of such values. A nested record or object is returned as a read-only map from field name to value, decoded against the schema that travels in the entry. No class is loaded.

**Every such value is unvalidated.** A filter, a browser or a client in another language sees what the writer wrote, limited only by the form check. It must treat each value as untrusted input.

### 7.3 Conversion

- **Strict `as(Class)`:** succeeds only when the body's schema digest equals the digest of the caller's class. Each field is decoded and the canonical constructor is called. If the constructor refuses, the conversion fails.
- **Reader's choice `as(Class, Compatibility)`** (RL-JGDMS-0135). `Compatibility.EXACT` is the strict rule. `Compatibility.ADDED_FIELDS` requires equal type names and pairs fields by name:
  - a pair whose tokens differ, or whose nullable marks differ, is refused;
  - a field in the body that the class lacks is dropped and listed in `dropped`;
  - a component the body lacks is passed as null and listed in `absent` if the component is nullable, and the conversion fails if it is never null.
- **So a component added in a later version is compatible only if it is nullable.** This follows from RL-JGDMS-0135 and RL-JGDMS-0174 together and was not stated in rev 2.
- The lenient form never changes what is stored and is never applied by the space.

### 7.4 The decoder names no class

Rev 2 claimed that the sender chooses no class. Item 10 of section 2 shows the decoder does not enforce it. REQUIRED WORK, and a precondition for RL-JGDMS-0178 being safe:

- When a record entry or nested value is decoded to a class, the decoder compares the type name and digest in the bytes with those of the declared type **before any class is loaded**, and refuses a difference.
- The only classes it uses are the class the caller passed to `as` and the classes that class declares as its component types. It never passes a name taken from the bytes to a class loader.
- The same applies inside a final `@AtomicSerial` component, which is why 3.2 proposes its condition.

### 7.5 Entry classes the client does not have

Unchanged from rev 2: by default the client has the class or reads the entry class-free (RL-JGDMS-0155); a resolver that fetches classes accepts one only when its digest is in the djinn or SPIFFE policy (RL-JGDMS-0156) and is outside this design.

## 8. The space interface

Service API, installed locally at the client (RL-JGDMS-0142, RL-JGDMS-0198). The proxy behind it is a dynamic proxy that implements `Space` and the other service API interfaces the service offers, directly; there is no binding interface (RL-JGDMS-0201) and no code is downloaded. `Space` is itself the remote interface. The DER proxy item already carries a list of interface names and a handler, and the receiver resolves each name locally, dropping and logging any it lacks and failing only if none resolves (`DER/object/ProxyWireSupport.java:126-164`, VERIFIED). Illustrative; exceptions are those of the existing interface (`RemoteException`, `TransactionException`, `UnusableEntryException` replaced by `EntryConversionException` where it applies, `InterruptedException`) and are omitted here. Times are milliseconds, as today.

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
    EntryMatchSet contents(Collection<EntryTemplate> tmpls, Filter filter, Transaction txn,
                           long lease, long maxEntries);

    EventRegistration notify(EntryTemplate tmpl, Filter filter, Transaction txn,
                             RemoteEventListener listener, long lease);
    EventRegistration registerForAvailabilityEvent(Collection<EntryTemplate> tmpls, Filter filter,
                             Transaction txn, boolean visibilityOnly,
                             RemoteEventListener listener, long lease);
}
```

- **Every result is a `MarshalledEntry`** (RL-JGDMS-0167). `EntryMatchSet` is a new type whose `next()` returns a `MarshalledEntry`; rev 2 named the existing `MatchSet`, which returns objects.
- **One filter per operation.** `filter` may be null and is the server's existing filter type, not designed here.
- **Events.** A `notify` event carries no entry contents. An availability event carries the entry, as a `MarshalledEntry`.
- **`take` and validity.** `take` removes the entry before the caller converts it. A caller that must not lose an entry it cannot convert takes under a transaction and aborts on failure.
- **Limits.** The number of templates in one call and `maxEntries` are capped by the server. Values are not proposed here.
- **Transactions and leases** are unchanged and are not designed here.
- **REQUIRED WORK for a code-free client.** `Lease`, `EventRegistration` and `Transaction` objects are themselves proxies, and today their implementations come from download jars. Each must become service API or a dynamic proxy. Whatever the present smart proxy `SpaceProxy2` does at the client beyond forwarding calls must move into the service API or the server; it was not read.

### 8.1 The old interface (stage 2)

`JavaSpace` and `JavaSpace05` are presented by new decorators over `Space` (RL-JGDMS-0203); rev 2 called this the resident wrapper. The decorators are not contract and are not in `tuple-space-api.jar`; they go in their own small compatibility jar (RL-JGDMS-0204), which depends on `tuple-space-api.jar` and `jgdms-lib-dl`. Its name is not ruled. The old interfaces themselves stay in `jgdms-lib-dl`, which is both locally installed and a download jar (the operator, 13:08 +1000). A binding interface such as `OutriggerServer` is needed only by the JOSS and Atomic JOSS paths and stays in the download jar (RL-JGDMS-0202); the DER path does not use it.

- It encodes a legacy entry at the call and decodes a new object on return.
- It turns a legacy template instance into a template of the legacy kind.
- A conversion failure fails the call, and the failure carries the marshalled entry so the caller can record it or take it out of the space; an entry is never skipped (RL-JGDMS-0189).
- It needs the legacy kind of `MarshalledEntry`. The superclass chain of that kind must come from the verified body and not from a separate field the writer supplies. Until the legacy kind is designed the wrapper cannot be built.

### 8.2 How old code finds the service

RL-JGDMS-0205, RL-JGDMS-0206. The operator's example (14:43 +1000): `ServiceDiscoveryManager` is written against `ServiceRegistrar`; it does not depend on a decorator's implementation, but without one it is blind to a new service whose proxy implements only the new service API.

- **A service registers two proxies under different service interfaces:** the naked dynamic proxy, which implements `Space`, and a decorated proxy, which implements the old interfaces. A lookup for the old interface returns the decorated one.
- **The decorated proxy is deserializable locally.** PROPOSAL for what that requires: the decorator is a final `@AtomicSerial` class in the locally installed compatibility jar, with one final field holding the naked proxy; the decoder resolves its class name against installed code and refuses it if absent, or, where the client has turned it on, fetches the jar as a trusted download accepted by its jar digest (RL-JGDMS-0207), through the same resolver as entry classes, with the location taken from the client's configuration and never from the stream. The naked proxy still needs no code (RL-JGDMS-0198).
- **A decorator is stateless and has no shared static fields** (RL-JGDMS-0206), so one installed copy of the class can decorate proxies of different services without separate class loaders. The author reads "stateless" as permitting the one final field and forbidding mutable instance state.

- **Decorators are Java only.** A client in another language uses the naked proxy and skips, by interface name, any registration it does not know (the operator, 14:50 +1000; RL-JGDMS-0194). The cross-language description of 3.8 covers the service API alone.

Open, none ruled:

1. **One service, two registrations.** A registrar replaces an item registered again under the same service ID. Either the two proxies have two IDs (a client browsing all services then sees the space twice, and the service renews two leases) or the registrar holds several proxies under one ID.
2. **A client without the compatibility jar that receives the decorated item**, by asking for every service or by service ID. It cannot decode it. This should be harmless where the client decodes only what it chooses, and needs stating in the lookup design.
3. **The lookup service's own proxy** arrives by discovery, not by lookup, so there is nowhere to register a second proxy. Either the discovery response carries both, or discovery in `jgdms-platform` (`LookupDiscovery`, `LookupLocatorDiscovery`, `LookupDiscoveryManager`; location VERIFIED) applies the decorator through a provider interface that compatibility jars plug into. The operator gave the provider interface tentative assent at 14:46 +1000.

## 9. Server

REQUIRED WORK, all of it. The server loads no entry class.

### 9.1 Type and matching

- For the record kind, the stored entry's type is its schema digest. Entries are held and looked up by digest, and a template is compared only with entries of its own digest. The six match sites and the type table assume class names today (items 11 and 12 of section 2).
- The one-hash-per-name check is removed for the record kind and replaced by a cap on the number of digests held under one name (RL-JGDMS-0181). The size of the cap and whether it is configurable are not ruled.
- The server takes the type name from the verified schema chain and refuses an entry in which any other copy of the type name or digest differs.
- **Authorization.** The space authorization design keys on class name (RELAYED). With identity by digest, and writers who can claim any name, what a permission names must be decided. Not designed; open question 7.

### 9.2 The check at write

RL-JGDMS-0170. On write the server, with no class:

1. decodes the schema chain and refuses a record-kind schema that contains a token outside the record vocabulary (scalars, the wrapper tokens, the nested-record token, and the token of a permitted final class), or an explicit `nullable TRUE`;
2. decodes each field strictly against its token, refusing any encoding that is not canonical and any item whose own type or token differs from the schema's;
3. refuses an absent slice in a never-null field;
4. applies the existing size and depth limits.

- A template gets checks 1, 2 and 4.
- For scalars, wrappers and nested records the check is complete, because their encodings are derived and no user code produces bytes.
- For a final `@AtomicSerial` component the server checks structure only; it cannot check that equal values give equal bytes. A writer whose code is not deterministic harms only the matching of its own entries.
- Cost: one strict pass over each entry at write. Not measured.

### 9.3 Filters

A filter reads field values class-free after the byte match, as today. The schema view that filter authoring uses rejects `@SerialEntry` classes today (RELAYED) and must be taught record entries.

## 10. Where things live

| Where | What |
|---|---|
| `jgdms-platform` (Java 21) | The wrappers; the nullable annotation; the codec provider interface; the canonical-encoding constraint. |
| `tuple-space-api.jar`: the service API (Java 21, installed locally, used by clients and server alike; RL-JGDMS-0198 to RL-JGDMS-0200). It depends on `jgdms-platform` and holds nothing specific to one implementation. | `Space`, `EntryTemplate`, `MarshalledEntry`, `EntryMatchSet`, the two exceptions. |
| `jgdms-der` | The provider implementation; the record entry point; typed collection encode and decode for record components; the nullable element of a field definition; the decoder rule of 7.4. |
| Outrigger service | Section 9. |
| The new space proxy | A dynamic proxy built at the client from the service API (RL-JGDMS-0198). Nothing new goes into `outrigger-dl`, which compiles for release 8 (RELAYED) and stays only for the old smart proxy. |
| STD-006 version 1.0 | Record entries; the token vocabulary and grammar; the nullable mark; the digest rule; the wrapper encodings; the check at write. The freeze is held by another session (RL-JGDMS-0133), which has not been told. |
| Reflective entry paths (`LookupAttributes`, the schema generator's field walk) | Refuse a record until taught (RELAYED). |
| Lookup attributes and entry beans | No change (RL-JGDMS-0163). |
| `EntryRep.entry()` codebase path | Removed: EntryRep v2 has no codebase field (RL-JGDMS-0192). |

## 11. Tests the work must include

Rev 2 had no such section.

- A record class refused for each rule of 3.1 and 3.2, and for a cycle.
- For each token: a value round-trips; a non-canonical encoding is refused by the server; an item whose type differs from the token is refused.
- A never-null field: null refused at encode, absent slice refused at write, absent slice accepted in a template. An explicit `nullable TRUE` refused.
- A set with two elements that encode alike is refused at encode.
- Bytes that name a class other than the declared type are refused **and no class is loaded** (7.4), for a nested record and for a final class.
- Two digests under one name are stored and matched separately; the cap refuses the next.
- Conversion: strict; `ADDED_FIELDS` with a nullable and with a never-null added component; a constructor that refuses.
- Golden bytes and digests for a set of sample types, published for other languages.
- The typed collection reader, before any of the above depends on it.

## 12. The rev 2 reviews, finding by finding

| Finding | Answer in this revision |
|---|---|
| Opus F1, recursive types have no digest | 3.3; RL-JGDMS-0177. The tree example is gone. |
| Opus F2, the immutability analysis proves the wrong thing | The analysis is gone; RL-JGDMS-0185. The remaining point, a final class whose serial form has a polymorphic slot, is 3.2 and open question 1. |
| Opus F3, the decoder loads the class the writer names | 7.4; item 10 of section 2 |
| Opus F4, "stored data is always valid" not achieved | RL-JGDMS-0178, RL-JGDMS-0188, RL-JGDMS-0189; 7.1, 7.2, 8, 8.1 |
| Opus F5, null rules contradict | 3.5, 7.3 |
| Opus F6, the nullable mark's canonical form | 3.5 |
| Opus F7, schema name | 3.4; open question 2 |
| Opus F8, names that vary between builds | 3.1 |
| Opus F9, enums | RL-JGDMS-0185 |
| Opus F10, values equal once encoded in a set | 4 |
| Opus F11, token derivation and the token inside the item | 4.1 |
| Opus F12, server vocabulary and limits | 9.1, 9.2, 8; authorization is open question 7 |
| Opus F13, what another language cannot do | 3.8 |
| Opus F14, primitives | RL-JGDMS-0180 |
| Opus F15, `Character` | 3.8 |
| Opus F16, RL-JGDMS-0174 against RL-JGDMS-0175 | RL-JGDMS-0187 |
| Opus F17, nested null | 3.5 |
| Opus F18, earlier findings still open | Writer stamp: RL-JGDMS-0190. Zero components: 3.1. Access to non-public records: open question 6. Legacy chain from the body: 8.1. |
| Sonnet, Java release clash | RL-JGDMS-0179, RL-JGDMS-0182; section 10 |
| Sonnet, `MatchSet` | 8 |
| Sonnet, three wordings for the nested token | 3.4, 3.6 |
| Sonnet, wrapper token and "names the declared class" | 3.4, 4.1 |
| Sonnet, `Converted.defaulted` | 7: renamed `absent` |
| Sonnet, templates and the never-null check | 3.5, 9.2 |
| Sonnet, `@AtomicSerial` record | 3.1: refused |
| Sonnet, "no digest changes" against RL-JGDMS-0175 | RL-JGDMS-0187; 3.5 |
| Sonnet, wrong and drifted citations | Section 2 |
| Sonnet, no exception types, no tests | 7.1, 11 |
| Sonnet, component order | 3.1 |

Not answered: where the build-time tools live; `Filter`'s type; which remote methods carry a body; the cost threshold for the check at write.

## 13. Open questions for the operator

1. **Final `@AtomicSerial` classes as components.** Permit them only when their serial form has no slot in which a writer names a class (3.2), or leave them out of the first version and admit records only?
2. **Type name.** The Java binary class name as an opaque string, with no separate schema name in the first version (3.4). The author and the rev 2 Opus review recommend this.
3. **Nested records never null unless annotated** (3.5). Recommended.
4. **Strict conversion inside a nested record** (3.6). Recommended.
5. **Token spellings kept** (3.8). Recommended.
6. **Records that are not public.** Require a record entry and its nested records to be public, or state the permission the codec is granted to reach them?
7. **What a space permission names** once identity is the digest (9.1).
8. **Where the new space proxy lives.** ANSWERED by RL-JGDMS-0198: it is a dynamic proxy built from the service API.
9. **A wire name for a component**, separate from its Java name, so a component can be renamed without making a new type. The rev 2 Opus review recommends it; it would also let another language use its own field names.
10. **Further listed types** (an identifier, a timestamp, a big integer). None is needed for the first version; each needs a neutral encoding first.
11. **The size of the cap** on digests per name, and whether it is configurable (RL-JGDMS-0181).
12. **The `Space` name and package.** The jar is settled (RL-JGDMS-0200); the interface name and package are not.

Carried from rev 2 and answered by the author's recommendation only: wrappers are not also `@AtomicSerial`, so legacy entries do not use them; moving data from a legacy class to a record is left to client code; an explicit null in a template is left to filters.

## 14. Hazards, in the order the author would address them

1. The decoder rule of 7.4 is a precondition for everything else being safe, and it changes shared decode code that every DER wire uses.
2. The typed collection reader is unread and has no production caller, and RL-JGDMS-0186 depends on it.
3. The check at write (9.2) is the one new piece on the server's write path. Its cost is not measured.
4. Matching by digest changes how entries are indexed; the six match sites, 17 type-check call sites and the type table all assume class names.
5. The provider interface (section 5) is new and its shape is a guess until the existing marshal factory provider has been read.
6. RL-JGDMS-0175 moves the digests of existing classes (about 40 files, counted by regex, RELAYED) and is to be timed with the freeze holder (RL-JGDMS-0187).
7. STD-006 is frozen by another session while this design asks for new sections in it.
8. The stage 2 wrapper cannot be judged until the legacy kind is designed.

## 15. Not checked

- Nothing was compiled or run.
- The typed collection writer and reader, the collection classification, the class-free decode paths, the marshal factory provider and how it is found.
- Whether a Java 21 compile can read `jgdms-der`'s release 25 class files. The design does not rely on it.
- The findings of the two rev 2 reviews, except item 10 and item 15 of section 2 and the two rev 2 slips this session re-opened (`MatchSet`, the tree example).
- The six Outrigger match sites, `LookupAttributes`, `AbstractEntry`, the write path, and the space authorization design are RELAYED.
- Lease, transaction and event-registration objects, which are themselves proxies, are outside this design.
