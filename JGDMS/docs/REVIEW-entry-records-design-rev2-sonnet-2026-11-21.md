# Fact-check and implementability review: DESIGN-Entry-Records-MarshalledEntry-Space-API-rev2-2026-11-21, Sonnet seat

**Status of this file.** Report of a Sonnet review agent, asked for by the operator on 2026-11-21 03:38 +1000 as part of a board review of `docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-rev2-2026-11-21.md`. Saved as received by the proxy-board review session (Claude Fable 5.2). The agent was read-only and compiled nothing. Everything below is RELAYED unless the saving session's own note says otherwise. An agent's report carries no operator authority.

Marks: VERIFIED = the agent opened the file or ran a scoped search. INFERRED = reasoned from what it read. Paths are relative to `JGDMS/JGDMS`.

## 1. Citation audit

The agent read all 449 lines of the document. Statements in sections 2, 3.6, 3.7 and 13 that cite code or counts:

| Doc ref | Claim | Result | Notes |
|---|---|---|---|
| s2.1 | `SerialEntry.java:102-127` worked record example | CONFIRMED (VERIFIED) | Path is `jgdms-platform/src/main/java/net/jini/core/entry/SerialEntry.java`. The example runs a few lines past 127 (the `serialize` method sits at 125-131). |
| s2.1 | No record entry exists in the tree | CONFIRMED for src of jgdms-platform, jgdms-lib-dl, jgdms-der, outrigger-dl, outrigger-service | The only hit is the Javadoc example (`SerialEntry.java:113`). |
| s2.2 | `EntryRepV2Codec.java:299-312`, one-record chain, no superclass link | CONFIRMED | The method `encodeSerialEntry` spans 299-315. The "later refinement" wording is in its Javadoc at line 289, outside the cited range. |
| s2.3 | `EntryWireField.java:64,74` | CONFIRMED | The file is `jgdms-platform/.../net/jini/core/entry/EntryWireField.java`, not under `DER/` as a bare name suggests. Line 64 is the field `Class<?> type`, line 74 is the constructor. |
| s2.3 | `EntryRepV2Codec.java:300,305` | DRIFT | Line 300 is `Class<?>[] wireTypes`. The `toWireType(wireTypes[i], …)` call is at 304, not 305. |
| s2.4 | `SchemaGenerator.java:607-625`: only Collection/Map keep element types, other parameterised classes map the raw class | CONFIRMED | The `ParameterizedType` branch is 607-627. |
| s2.4 | "A type variable, wildcard or `Object` becomes the token `any`" | PARTLY WRONG | Only `?`, `? extends Object` and `? super X` give `any` (lines 637-641). `? extends B` with a concrete B resolves to B's token. A `TypeVariable` gives `any` (644-645). `Object` gives `any` only as a collection element, key or value (`ruleForElement`, 657-662). A bare `Object` field is rejected by `toWireType`, which throws. The wildcard and type-variable lines (637-645) are outside the cited range. |
| s2.5 | `enum:<className>` at `SchemaGenerator.java:443` | CONFIRMED | |
| s2.5 | Nested `@AtomicSerial`, interface or abstract gives the bare marker `@AtomicSerial` (484-507) | CONFIRMED for interface/abstract (489-507) and the DerReplacer case (484-487) | The concrete `@AtomicSerial` class row is in the Javadoc table at about line 410. |
| s2.6 | `DerObjectStreamCodec.java:1207-1209` refuses a non-`@AtomicSerial` object | CONFIRMED | The check is at 1207-1208 and the throw runs to 1211. |
| s2.6 | STD-006 draft lines 222-229 | CONFIRMED (±2) | The text says a collection travels "only as a field inside an `@AtomicSerial` value … or via a `@SerialEntry` wire field". See s5 item 11 below. |
| s2.7 | Tag `[16]` at `DerObjectStreamCodec.java:91-93,181-184` | CONFIRMED | The comment blocks are 91-95 and 181-185; the tag constant is at 185. |
| s2.7 | "No caller outside that class" (RELAYED) | CONFIRMED and sharpened | `writeCollection(Object, String)` at line 1270 is package-private (`void`, no modifier) in `der.stream`. No main-source caller found anywhere in jgdms-der, jgdms-platform, jgdms-lib-dl or outrigger. Six jgdms-der tests under `src/test/.../stream/` call it. |
| s2.8 | `ObjectCodec.java:1586-1593`: sets and maps sorted, lists keep order | CONFIRMED (approx.) | `isCanonicalise(wireType)` is at line 1543. The `beginSortedSet` / `SEQUENCE` branch is at about 1591-1596. The classification table itself was not read (INFERRED). |
| s2.9 | `EntryRep.java:1226-1236` compares non-wildcard bytes | CONFIRMED | `matches` is at 1222-1236. File: `services/outrigger/outrigger-dl/.../proxy/EntryRep.java`. No digest comparison inside it. Type is decided by `rep.isAtLeastA(tmpl.classFor())` at `OutriggerServerImpl` 2731 and 3178. |
| s2.10 | One hash per class name, second refused | CONFIRMED | `OutriggerServerImpl.typeCheck` is at 1495 and `checkClass` at 1524 (map `typeHashes`). A mismatch throws `UnmarshalException`. |
| s2.11 | Server does not parse field bytes on write | INFERRED, not contradicted | The write path was not read. `EntryProjection` (outrigger-service) is a class-free decoder for the filter path only. |
| s2.12 | `EntryRep.entry()` at 837, 846-849 loads the writer-named class from the writer-named codebase | CONFIRMED | Method at 837. `realClass == null` is at 846 and `CodebaseProvider.loadClass(codebase, className, …)` at 847-848. |
| s3.2.1 | Security-manager assumption (reflection controlled) | Consistent with `DerReplacer.java:92`, which mentions `ReflectPermission("suppressAccessChecks")` | |
| s3.5 | Encoder normalises -0.0 | PARTLY VERIFIED | The decoder rejects -0.0 bits (`getarg/WireTypes.java:58,422-426`). The encoder-side normalisation was not located. |
| s3.5 | String encoder refuses an unpaired surrogate | CONFIRMED | `DerUtf8.java:90` (encoder side). |
| s3.6 | Depth limit 16 at `ObjectCodec.java:153,425` | CONFIRMED | `MAX_NESTING = 16` at 153, the check at 425. |
| s3.7 | `AtomicSerialSchemaRecord.java:41-44` | CONFIRMED | The ASN.1 block is 41-45. |
| s3.7 | Scalar tokens at `SchemaGenerator.java:403-410` | PARTLY WRONG | Lines 403-411 are a Javadoc table with only `boolean`, `byte`, `short`, `int`, `long`, `java.lang.String`, `byte[]` and `@AtomicSerial`. The `float`, `double` and `char` tokens are in code at 480-482. Boxed and primitive types share one token (`Integer` gives `"int"`, `Character` gives `"char"`). |
| s3.7 | `AtomicSerialFieldDef.java:35-37,138` | CONFIRMED | The ASN.1 block is at 35-37. The trailing-bytes refusal is at 137-138. |
| s3.7 | `Valid.java` path | CONFIRMED | `Valid.notNull(T, String)` is at line 222 and throws `InvalidObjectException` (checked). |
| s3.7 | `Valid.notNull(` in 12 files, 16 calls, in platform/lib/lib-dl/jeri | CONFIRMED (VERIFIED) | platform 8 files / 11 calls; lib-dl 3 / 3; jeri 1 / 2; lib 0. The document did not search the services. reggie-dl adds 2 files / 3 calls (`reggie/proxy/EntryRep`, `Item`). Total is 14 files / 19 calls. der, outrigger-dl, outrigger-service and reggie-service have 0. |
| s13 | No `module-info.java` | CONFIRMED for jgdms-der, jgdms-platform and jgdms-lib-dl | |

## 2. Ruling coverage

Wording read from `claim_register/RULING_INDEX.md`.

| Ruling | Verdict | Detail |
|---|---|---|
| 0134 | Full | Records have final components (s3.1). `MarshalledEntry.with` and `EntryTemplate.with` return new objects. |
| 0135 | Partly | s6.3 implements the reader's choice, refusal as default, added-fields-only, and a same-name different-type refusal. "Missing field decodes as null" conflicts with 0174 (see s5 item 4). `Compatibility.EXACT` is undefined. |
| 0137 | Full | s6 and s7 return the marshalled form everywhere, and lenient conversion is an explicit act (s6.3). |
| 0139, 0141 | Partly | s9 lists the STD-006 content. No normative text or vectors exist yet. |
| 0142, 0159 | Partly | s7 says "locally installed, release 21". The module name, artifact and package are unspecified. The wrappers live in jgdms-der, which compiles for release 25 (`jgdms-der/pom.xml:72`, with a comment that it "will not run on Java 21"). A release-21 client cannot use them (VERIFIED, see s4). |
| 0146, 0147 | Partly | s7.1 describes the wrapper. It is stage 2 and depends on an undesigned legacy kind. |
| 0155, 0156, 0158 | Partly | s6.4 says the resolver is "outside this design". s9 says the `EntryRep.entry()` codebase path is "removed or made subject to" the rulings, which leaves the choice open. |
| 0157 | Partly | The space side is covered in s8.1. The client side (class loaders for `as(Class)`) is not discussed. |
| 0160 | Full | s3.1 (constructor validates), s5 (builder), s7.1 (decorating wrapper). |
| 0161 | Partly | s3.2 refuses JDK collections (following 0169), which narrows 0161's "List, Set or Map" wording. It keeps the refusals of wildcard, type variable and raw type, per-element check (s4.1 W1), and unmodifiable results. |
| 0162 | Full | s3.2.2 keeps "shallowly". |
| 0163 | Full | s9. |
| 0164 | Full | s3.3 and s5. |
| 0165 | Partly | The digest is the identity and the space check is removed (s8.1). A wrapper's token under W1 is `set:<elem>` or `list:<elem>` and does not name the wrapper class, but 0165 and s3.3 say the digest covers the declared class of each component (see s5 item 2). |
| 0166 | Full | s6.1. |
| 0167 | Contradicted | s7's `MatchSet contents(...)` returns a `MatchSet`. The same section says "Every result is a `MarshalledEntry`". The existing `MatchSet` carries `Entry` objects. |
| 0168 | Full | s6, s5 and s7.1. |
| 0169 | Partly | Wrappers and `DerBytes` are named. The `DerBytes` token and wire form are not specified. |
| 0170 | Partly | s8.2 covers the server for the record kind. s9 admits client-library comparison for legacy entries is "not designed here". |
| 0171 | Full | s3.2 and s3.2.1. |
| 0172 | Partly | s3.7 is a proposal with open questions 8 and 10. No vocabulary or schema-name mechanism is fixed. |
| 0173 | Full | `nullable BOOLEAN DEFAULT TRUE` (s3.7). The "no digest changes" claim is qualified by 0175 (see s5 item 13). |
| 0174 | Partly | s3.4 holds the rule. s3.6 ("A nested value may be null if the outer constructor accepts null") and s6.3 ("A component the body lacks is passed as null") conflict with it. The nullable annotation is never named. |
| 0175 | Partly | s3.7 states the rule and says the analysis for helper methods is "not settled". Survey 3b shows most null checks sit in helpers. |

## 3. Impact survey (main sources, VERIFIED unless marked)

### 3a. Classes annotated `@AtomicSerial`

Lines starting with `@AtomicSerial` in `src/main`. Hits in comments and strings were excluded.

| Module | Files | Annotations |
|---|---|---|
| jgdms-der | 2 | 2 |
| jgdms-platform | 78 | 82 |
| jgdms-lib | 0 | 0 |
| jgdms-lib-dl | 23 | 27 |
| jgdms-jeri | 12 | 12 |
| outrigger-dl | 13 | 13 |
| outrigger-service | 0 | 0 |
| reggie-dl | 23 | 23 |
| reggie-service | 1 | 2 |
| Total | 152 | 161 |

### 3b. Null checks

These come from a heuristic script (comments stripped, braces matched, idioms found by regex). There are 160 constructors taking `GetArg` in `@AtomicSerial`-annotated files (der 2, platform 82, lib-dl 26, jeri 12, outrigger-dl 13, reggie-dl 23, reggie-service 2). Constructor-level counts:

| Form | Constructors with it directly in the body | Calls |
|---|---|---|
| `Valid.notNull(` | 8 (platform 5, lib-dl 2, jeri 1) | 11 |
| `Objects.requireNonNull(` / `requireNonNull(` | 0 | 0 |
| Explicit `== null` then `throw` | 1 (`BasicObjectEndpoint`, jeri, which also uses `Valid.notNull`) | 1 |
| Delegates to an in-file static helper that holds a check (`this(check(arg))`) | 45 (der 1, platform 21, lib-dl 9, jeri 1, outrigger-dl 5, reggie-dl 8) | n/a |
| `this(...)` or `super(...)` delegation with no check found in the constructor or an in-file helper | 107 | n/a |

- Null checks are overwhelmingly in helpers, not in the `GetArg` constructor. In the `@AtomicSerial` files, occurrences inside `GetArg` helpers (`check(GetArg)`, `checkFields`, `checkFacet`, `checkServer`, `checkIntegrity`):
  - `Valid.notNull(`: 7 (platform 3, lib-dl 1, reggie-dl 3).
  - Explicit null-then-throw: about 63 (der 3, jeri 1, lib-dl 13, platform 28, outrigger-dl 7, reggie-dl 11).
  - `requireNonNull`: 0.
- `requireNonNull` appears only in non-`GetArg` constructors (der 3, jeri 1) and one platform method.
- Files containing a check of any of the three forms in a `GetArg` constructor or helper (the files RL-0175 would flag at minimum): der 1, jeri 2, lib-dl 8, platform 18, outrigger-dl 3, reggie-dl 8, which is 40 files. The regex may over- or under-count (INFERRED). Each such class's digest changes when its serial form gains the never-null mark.
- The 107 delegating constructors may check in the canonical constructor they call. Those were not traced (INFERRED).

### 3c. `Entry` implementors

- jgdms-platform: `Entry` and `CloneableEntry`, both interfaces.
- jgdms-lib-dl: `AbstractEntry` (abstract, implements `CloneableEntry`) and 12 subclasses. Ten of them are annotated `@SerialEntry`; `ServiceType` and `Status` are abstract and unannotated.
  - Address, Comment, Host, Location, Name, ServiceInfo, ServiceTypes, UIDescriptor and jmx.JMXProperty / JMXProtocolType are the ten.
- outrigger-dl: `SnapshotRep implements Entry`. Its only field is `private final EntryRep` (not a public entry field).
- No implementors in jgdms-der, jgdms-lib, jgdms-jeri, outrigger-service, reggie-dl or reggie-service.

Public entry field types across the 12 subclasses (28 fields):

| Field type | Count | Used by | Design s3.2 |
|---|---|---|---|
| `String` | 24 | Address 7, Comment 1, Host 1, JMXProperty 2, JMXProtocolType 1, Location 3, Name 1, ServiceInfo 6, UIDescriptor 2 | Permitted |
| `java.lang.Class` (raw) | 1 | `ServiceTypes.serviceType` | Refused (JDK type, not listed) |
| `StatusType` | 1 | `Status.severity`; `StatusType` is a non-final class (`implements Serializable, Resolve`) | Refused (non-final) |
| `java.util.Set` (raw) | 1 | `UIDescriptor.attributes` | Refused (raw JDK collection) |
| `java.rmi.MarshalledObject` | 1 | `UIDescriptor.factory` | Refused (JDK type, not listed) |

### 3d. Arrays and `java.util` collections as entry fields

- Arrays: none among the public fields of these classes.
- Collections: only `UIDescriptor.attributes` (raw `Set`).
- The existing `@SerialEntry` path declares `Set.class`, `Class.class` and `MarshalledObject.class` wire fields (`UIDescriptor.java:57-58`, `ServiceTypes.java:40`). `Class` and `MarshalledObject` have no token in `SchemaGenerator` unless a replacer is registered (INFERRED, not checked). A raw `Set` bare value is refused at encode (`EntryRepV2Codec.java:164-180`).

### 3e. Outrigger call sites

- `typeCheck(EntryRep)` is defined at `OutriggerServerImpl.java:1495`. `checkClass` is defined at 1524 and called only from `typeCheck` (1504, 1511). There are 17 `typeCheck` call sites, all in `OutriggerServerImpl.java`: 1608, 1680, 1944, 2086, 2370, 2381, 2392, 2403, 2414, 2442, 2501, 2522, 2563, 2990, 3360, 3671, 4366.
- `EntryRep.matches` has 6 call sites in outrigger-service:
  - `EntryHolder.java` 196 and 915.
  - `OutriggerServerImpl.java` 2731, 3178 and 4223. At 2731 and 3178 it is guarded by `rep.isAtLeastA(tmpl.classFor())`.
  - `TemplateHandle.java:90`, reached from `WatchersForTemplateClass.java:113`.
- `EntryRep.equals` also compares `hash` and slice bytes (`EntryRep.java:1006`). It is not a match site.
- `FilterEval.matches` is a separate, filter-only path.

## 4. Implementability gaps

Build and module facts (VERIFIED, from pom files):
- jgdms-der compiles for release 25 (`jgdms-der/pom.xml:72`) and says it will not run on Java 21.
- jgdms-platform, jgdms-lib, jgdms-jeri and outrigger-service compile for 21. jgdms-lib-dl and outrigger-dl compile for 8.
- The wrappers (s4, s9) go in jgdms-der. The client module (s7, RL-0159) is release 21. A release-21 client cannot load jgdms-der classes.
- `EntryRep` and `SpaceProxy2` are in outrigger-dl (release 8). Records and the new types cannot be referenced from there. The document does not say where the new proxy and server-side types live.

Gaps by section:

| Sec | Question with no answer |
|---|---|
| 3.1 | How does the codec call the canonical constructor and the accessors under the security manager? Which permissions apply, and what about non-public, nested or local records and generic records? Which exception is thrown by "refused when first used", and what counts as "first used"? What happens with a record with zero components, and is there a limit on component count? |
| 3.1 | Component order is "declared order". The legacy reflective path uses `FieldComparator` order (`EntryRepV2Codec.java` Javadoc near 195). The digest depends on order, so reordering components makes a new type. Only renaming is mentioned (open question 3). |
| 3.2, 3.4 | The nullable annotation is never named (RL-0174). Which type, which package or module, what target and retention? |
| 3.2.1 | Where does the analyser read bytecode from under a security manager? Is the build-time tool a Maven plugin or something else, and what are its output and failure format? What is "this escapes", and is a static factory or a lambda in a constructor covered? How are recursive types handled, e.g. `record Node(DerList<Node> kids)`? Does the analysis follow calls to overridable methods? |
| 3.2.1 | "A final class the codec can encode" is, in practice, only a final `@AtomicSerial` class. Are its serial-form fields held to the "permitted recursively" rule, given that many existing ones hold arrays? |
| 3.2, 4 | Are nested wrappers permitted (`DerList<DerSet<String>>`)? Are record elements permitted in a wrapper (`DerList<Location>`), and how is each element's schema carried in the typed collection? s3.6 says a tree is built "through a list wrapper" but W1's element encoding for a record is unspecified. |
| 3.2, 3.6 | The nested-record token is "names the nested type and includes its schema digest". Its exact text form is unspecified. Schema limits are `wireType` 1..1024 bytes and names 1..255 bytes (`AtomicSerialFieldDef`). Does the token fit? |
| 3.3 | Enum with constant bodies, enum nested in a class (`$`), enum constant validity at the server (the server has no enum class): what is checked? |
| 3.7 | The schema-name annotation is unnamed. What does a binding without Java publish? How is the closed token vocabulary written down? Where does the description and test-vector tool live? |
| 3.7 | How does the serial form declare never-null (a new `SerialForm` overload, a flag, a second array)? How does the analysis relate a `Valid.notNull(x)` to a particular `arg.get(name, …)`? s3.7 itself marks helper methods as "not settled". Survey 3b shows 45 of 160 `GetArg` constructors do the check in a helper and 107 delegate. |
| 4 | `DerBytes` token and wire form are not given. Behaviour on duplicate elements at construction (error or collapse), equality, `hashCode`, `toString`, `Serializable`, `DerMap` null values, and size limits are not specified. |
| 4.1 | W1 needs `writeCollection` (package-private, `der.stream`) to be callable from `der.entry`. The decoder side of tag `[16]` was not read (s13). The current slice encoder throws for a bare collection value (`EntryRepV2Codec.java:164-180`). |
| 5 | `EntryTemplate.with` for a nested-record or wrapper value: what is checked? What does `with(field, null)` mean (wildcard or error)? How is the template digest obtained in `like(...)`? What exactly is "a nested value taken from an entry in hand"? |
| 6 | The signatures of `of(R)` and `ofEncoded(byte[])` have no exception types, but encoding and "throws on any fault" imply one. Which exception, and what is the body format (an `EntryRepV2Body`)? What is the size limit? What are `Compatibility.EXACT` and `ADDED_FIELDS`, and how does `EXACT` differ from strict `as(Class)`? What do `Converted.dropped` and `Converted.defaulted` contain, given s6.3 says the missing component is passed as null? |
| 6 | The class-free `MarshalledEntry.with(String, Object)` produces bytes without a class. How does it encode a nested record? |
| 7 | `Filter` is undefined (a type, package and relationship to the existing `FilterSet`). Exceptions (`RemoteException`, `TransactionException`, `InterruptedException`) are omitted. The units of `timeout` and `lease` and the meaning of `maxEntries` are not stated. The relation between `Space` and the server proxy (`SpaceProxy2`, `OutriggerServer`) is not given. |
| 7 | "It crosses the wire only as its body inside the space protocol": which remote methods carry the body? The existing `OutriggerServer` methods take `EntryRep`. |
| 8.1 | Digest-keyed indexing has no limit on distinct digests per name. RL-0165 makes this unbounded, which is an attack surface. The record kind never matches by superclass. How the six match sites choose between kinds is not stated. |
| 8.2 | The "strict decoder" checks and the closed token vocabulary the server accepts are not listed. A client can send any token (`any`, bare `@AtomicSerial`, `enum:...`). The document says the server "relies on none" of the client's checks, but does not say it rejects those tokens. The exception type on refusal is not given. |
| 8.2 | The document says the check cost is "not measured". There is no acceptance threshold. |
| 8.3 | The "schema view" that filter authoring uses is cited only through seat 2 (RELAYED). The nested-record reach-in is REQUIRED WORK with no design. |
| Whole doc | There is no test-obligation section. Nothing says what tests prove the analyser (s12 item 1a mentions "classes built to deceive it" in passing), the canonical check (including negative cases), nullability enforcement, coexistence of two digests under one name, or the golden vectors. |
| Whole doc | "Refused" is used throughout, but no exception types are defined. |

## 5. Internal contradictions

1. **Token content for nested components.** s3.2 table says "the token must name the class". s3.2.2 says "the token of a nested component names its class". s3.6 says the token "names the nested type and includes its schema digest". Three formulations, one token.
2. **Wrapper tokens versus 3.3 and RL-0165.** s3.3 says "Every token names the declared class in full … the wrapper tokens of section 4 carry their element types". s4.1 W1 says the token is "the token the generator already produces for `Set<String>`". That token (`SchemaGenerator.java:607-625`) is a discipline token (`set:`, `list:`, `map:`) and carries no wrapper class name. `DerSet` and any other sorted-set wrapper would then share a token.
3. **Wrapper token status.** s3.2 table row for wrappers says "REQUIRED WORK" for the token. s4.1 says it already exists. The generator maps `DerSet<String>` via its raw class, and how that class is classified is not read (s13).
4. **Null in 3.4 versus 3.6 and 6.3.** s3.4 says the codec refuses null in a never-null component before the constructor is called. s3.6 says a nested value "may be null if the outer constructor accepts null". s6.3 says a component the body lacks "is passed as null and reported". The document's own open question 11 leaves the nested default unresolved.
5. **`Converted` naming.** The record has `dropped` and `defaulted`, but s6.3 says the missing component is passed as null, not defaulted.
6. **`MatchSet`.** s7 `contents(...)` returns `MatchSet`, but "Every result is a `MarshalledEntry`".
7. **Templates and the canonical check.** s8.2 says "A template gets the same check". s3.7 says a template field may be absent whatever its never-null mark. The two need an explicit exemption.
8. **Nested `@AtomicSerial` record.** s3.2.1 says a nested final `@AtomicSerial` class is encoded by its own `serialize` code. s3.6 says a nested record is encoded by the derived path. A record annotated `@AtomicSerial` has no stated path. s3.1 refuses `@SerialEntry` records but says nothing about `@AtomicSerial` ones.
9. **"No cycles" and trees.** s3.6 says a value can be a tree "through a list wrapper". The type itself can be recursive (`record Node(DerList<Node>)`). The analysis in s3.2.1 ("recursively") and schema generation would not terminate. Neither refusal nor support is stated.
10. **Digest stability.** s3.7 says no existing schema's bytes or digest change, and later says an existing class whose constructor checks null "fails until its serial form is updated … its digest changes". Survey 3b gives about 40 files affected. s3.7's argument against renaming tokens ("every existing digest and the frozen test bytes change") applies equally to RL-0175.
11. **s2.6 versus the code.** The document (via STD-006 lines 222-229) says a collection may travel via a `@SerialEntry` wire field. `EntryRepV2Codec.java:164-180` throws for a bare top-level collection value. The shipped `@SerialEntry` class `UIDescriptor` declares `Set attributes`. Which is right is unresolved (INFERRED).
12. **Header and open-question numbering.** The status line mentions revisions up to RL-0173 (03:18), but the body cites RL-0174 and RL-0175 and an operator message at 03:28. Open questions are listed 1-9, 11, 10. Question 11 runs two statements together.
13. **s3.4 `-0.0` exception.** It is the only equality difference listed. `Float` and `Double` encoded NaN is canonicalised to one bit pattern (`WireTypes.java:402-403`), so NaN equality agrees. No contradiction, only a note that the list is complete for these two.

## Not checked
- JGDMS trunk `83c4d4c7a` and the uncommitted state (no git commands run).
- The two earlier reviews and the full STD-006 draft beyond lines 218-232.
- RL-JGDMS-0133 and other rulings outside 0134-0175.
- Test sources in every module, and modules outside the nine listed.
- The typed collection decoder, the collection classification (`CollectionWireTypes`), `EntryProjection` internals, `LookupAttributes`, `FieldComparator`.
- Encoder-side -0.0 normalisation.
- Whether a replacer exists for `Class` or `MarshalledObject`.
- The 3b counts come from regexes, not a compiler. Constructors that delegate through `this(...)` or `super(...)` were not traced into the canonical constructor.
