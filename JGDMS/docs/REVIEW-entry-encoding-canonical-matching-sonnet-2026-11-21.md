# Review: is the entry encoding canonical, and where is matching done? (Sonnet agent, 2026-11-21)

**Status.** Read-only investigation by a Sonnet subagent of the proxy-board review session, asked for by the operator at 2026-11-21 02:13 +1000 ("So for consistency, matching is best performed with the marshalled form. Please delegate to a sonnet agent to check."). The text from "1. Summary answer" onward is the agent's report, reformatted only. It is RELAYED evidence. The review session re-opened four of the cited places and found them as reported: `EntryRep.matches` in outrigger-dl (lines 1226 to 1236), `EntryRep.matchEntry` in reggie-dl (389 to 398), the sorted-set branch in `ObjectCodec` (1586 to 1593) and the non-`@AtomicSerial` branch in `EntryRepV2Codec` (157 to 170). Nothing else was re-opened. No test was run. Not registered in the claim register.

Paths are relative to the repository root. DER module root: `jgdms-der/src/main/java/au/net/zeus/jgdms/der/` (abbreviated `DER/`). Labels: VERIFIED (the agent opened the line), INFERRED (reasoned from nearby code), NOT FOUND (searched, with what was searched).

## 1. Summary answer

Mostly yes for what can be encoded today, with four caveats.

- **Encoder.** The byte-level encoder is built to be canonical. Scalars are minimal DER. NaN and -0.0 are normalised. Booleans use 0xFF. Hash-ordered sets and maps are octet-sorted. The decoder is strict DER and rejects non-canonical forms.
- **Server matching.** Outrigger matches by `Arrays.equals` on cached slice bytes, so it is a pure encoded-form match.
- **What breaks the premise:**
  1. Reggie does not use slices. It compares per-field `MarshalledInstance.payloadBytes`, in a different structure.
  2. Collection-typed entry fields are accepted by the schema but their values cannot be encoded at all (Q1).
  3. Canonicity is only as good as the client's encoder. The server never parses slice payloads at ingest, so a non-conforming client can store non-canonical bytes.
  4. Matching on bytes is stricter than `equals` for preserve-order collections, and includes the value's class digest for `@AtomicSerial` values. Library code in `LookupAttributes` and `AbstractEntry` still compares as objects (Q7).

## Q1. Collection-typed fields

- The schema generator **accepts** `List`/`Set`/`Map`/`Collection`/`ArrayList`/`HashSet`/`HashMap` as the declared type.
  - `EntrySchemaGenerator.java:205` calls `SchemaGenerator.toWireType(f.getGenericType(), c)`. VERIFIED.
  - `SchemaGenerator.java:599-625` handles raw classes (element type `any`) and `ParameterizedType`, resolving key, value and element types independently. VERIFIED.
  - Result tokens are `set:`, `bag:`, `orderedset:`, `list:`, `map:{k}{v}`, `orderedmap:{k}{v}` (`DER/getarg/CollectionWireTypes.java:161-186`). VERIFIED.
- The element or generic type **is recorded** in the token (for example `list:java.lang.String`), and the token is inside the schema record, hence inside the digest. `EntryRepV2MatchContractTest.java:366-392` asserts this. VERIFIED. No `RecordComponent` use was found in these classes (NOT FOUND, grep of the entry and schema code).
- Wildcards, `Object`, and type variables resolve to `any` (`SchemaGenerator.java:657-664`, `:644`). VERIFIED.
- **The value cannot be encoded.**
  - `EntryRepV2Codec.encodeFieldSlice` uses `ObjectCodec` only for `@AtomicSerial` values. Everything else goes through `encodeObjectStreamItem`, which calls the untyped `writeObject` (`EntryRepV2Codec.java:157-170`, `:660`).
  - A bare `ArrayList`/`HashSet` is not `@AtomicSerial` and hits `UnsupportedOperationException` (`DER/stream/DerObjectStreamCodec.java:1209`). This is wrapped as `IOException` at `EntryRepV2Codec.java:175-182`.
  - The typed `writeCollection` (`DerObjectStreamCodec.java:1270`, `[16]` tag) exists, but nothing calls it outside that class (NOT FOUND, grep of the DER module). VERIFIED for the callers, INFERRED for the consequence.
  - The spec says this is deliberate: Appendix A.4.2, `docs/JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md:222-229`. A collection works only inside an `@AtomicSerial` field value.
- `@SerialEntry` classes take a different path. `EntrySchemaGenerator.java:154` rejects them. `EntryRepV2Codec.encodeSerialEntry` (`:299-312`) calls the `Class` overload `toWireType(Class,...)` at `:305`. For a `List.class` wire type that overload returns `@AtomicSerial` (interface branch, `SchemaGenerator.java:505-507`), and for a concrete `ArrayList` it throws (`:510`). INFERRED from overload resolution; not executed.

## Q2. Canonical order

- **Set/Map in `ObjectCodec`.** The order is decided by the declared type, not the runtime instance (`CollectionWireTypes.disciplineFor`, `:303-400`; Javadoc `:40-62`). VERIFIED.
  - CANONICALISE covers `HashSet`, `HashMap`, `ConcurrentHashMap`, `CopyOnWriteArraySet`, bare `Set`/`Map`, and so on.
    - Sets and bags are written as SET OF (0x31) via `enc.beginSortedSet(CollectionWireTypes.OCTET_SORT)`, sorted by X.690 section 11.6 octets (`ObjectCodec.java:1588-1592`).
    - Maps are sorted by encoded **key** only (`beginSortedMap`, `ObjectCodec.java:1560-1563`).
    - Bottom-up recursion is covered by `CollectionOrderingTest`.
  - PRESERVE covers `List`, `ArrayDeque`, `LinkedHashSet`/`LinkedHashMap`, `Sorted*`/`Tree*`, `EnumSet`/`EnumMap`, and `Sequenced*` (SEQUENCE OF, 0x30, iteration order kept).
- **List.** Order is preserved and duplicates are kept (`disciplineFor` List branch; `CollectionOrderingTest.java:248-263`). VERIFIED.
- **Decode side.** Strictly ascending order is enforced for canonicalise sets and maps (non-decreasing for bags). Duplicates are rejected (`ObjectCodec.java:2927-2928`, `:3019`; `CollectionOrderingTest.java:490`). VERIFIED.
- **Tension.**
  - Two `.equals` `LinkedHashSet`s with different insertion order encode to different bytes. The test names this "intended tension" (`CollectionOrderingTest.java:177-196`). VERIFIED.
  - A canonicalise map with two distinct keys that encode identically would be rejected at decode (strictly ascending). INFERRED.

## Q3. Other sources of non-canonical bytes

| Item | Encoder | Decoder | Status |
|---|---|---|---|
| NaN, -0.0 | Any NaN becomes `0x7FC00000`/`0x7FF8...`; -0.0 becomes +0.0 (`ObjectCodec.java:1340-1368`; stream path `DerObjectStreamCodec.java:1184-1190`) | Rejects other NaN patterns and -0.0 (`DerObjectStreamCodec.java:1367-1403`; tests in `FloatDoubleCharCanonicalTest`) | VERIFIED |
| `BigDecimal`, `BigInteger`, `Date`, other JDK types | Not supported as entry values. The schema rejects them (`SchemaGenerator.java:510`) and the stream rejects them (`DerObjectStreamCodec.java:1209`), so the question is moot. | n/a | VERIFIED. No `BigDecimal` or `Date` support found in `ObjectCodec`/stream. |
| `String` | UTF8String, no normalisation (no NFC). An unpaired surrogate is refused (`DER/DerUtf8.java:93-102`). | Strict UTF-8: overlong forms and encoded surrogates are rejected (`DerUtf8.java:63-78`). | VERIFIED |
| Enum | Field: by constant name (`ObjectCodec.encodeEnumTo`, around `:1390`). Top level: declaring class plus name (`DerObjectStreamCodec.java:1089`). | Unknown constant is rejected (`:1730`). | VERIFIED |
| Boolean | 0xFF for true (`DerObjectStreamCodec.java:1170`, `DerEncoder.bool`). | Only 0x00 or 0xFF accepted (`DER/DerReader.java:237-250`; `DerObjectStreamCodec.java:1858-1860`). | VERIFIED |
| INTEGER | Minimal two's complement (`DerEncoder.java:154-170`). | Rejects non-minimal 0x00/0xFF prefix (`DerReader.java:278-290`). | VERIFIED |
| Length | Minimal definite. | Rejects indefinite, 0xFF, and non-minimal long form (`DerReader.java:465-520`). | VERIFIED |
| Absent vs null | Entry null and template wildcard share the single `[0]` NULL slice (`EntryRepV2Codec.java:143`, `:417-421`). A nested null field is DER NULL (`ObjectCodec.java:1262`). | Absent must be empty (`:420`). | VERIFIED |
| Nested `@AtomicSerial` | Field order is the `serialForm()` schema order, and every declared field must be put (`ObjectCodec.java:872-885`). The value is the output of the class's own `serialize()` (user code). | No defaulted or optional fields. | VERIFIED. Whether user `serialize` code is deterministic is NOT FOUND (not checkable generically). |

- **Ingest gap.** `EntryRepV2Codec.decode` (`:424-447`) treats each slice payload as an opaque OCTET STRING (size limit and digest membership only). Slice payload canonicity is not re-validated when an Outrigger `EntryRep` is built (`outrigger-dl/.../EntryRep.java:~725-735`). The spec calls payload canonicity a "precondition" inherited from the codec (Appendix A.4.1). INFERRED, from reading decode and the `EntryRep` constructor. No separate ingest validator was found (NOT FOUND, grep for `decodeFieldValue|decodeScalarSlice` in Outrigger and Reggie service code).
- **Retained wire form.** `RawWireFormRetaining` re-emits the original `[8]` proxy bytes verbatim (`DerObjectStreamCodec.java:~1136-1148`). That is not re-derived, but proxies are refused as entry values (`EntryRepV2Codec.rejectProxyValue`, `:643`).

## Q4. Arrays

- `byte[]` is accepted. The schema type is `byte[]` (`SchemaGenerator.java:423-445`). The slice payload is a top-level `[5]` OCTET STRING (`DerObjectStreamCodec.java:1020-1025`). VERIFIED.
- Single-dimension `String[]`, `int[]` and `@AtomicSerial[]` are accepted. The schema type is `array:<component>` (`SchemaGenerator.java:447-476`). The value is a top-level `[9]` item via `ObjectCodec.encodeTopLevelArrayTo` (`DerObjectStreamCodec.java:1029-1046`). Order is preserved. VERIFIED.
- Multi-dimensional arrays and `array:byte` are rejected (`SchemaGenerator.java:451-465`). VERIFIED.
- Primitive-typed entry fields are rejected (`EntrySchemaGenerator.java:245`). VERIFIED.

## Q5. Outrigger matching sites

All are on encoded slice bytes. None decodes to objects or calls the entry's `equals`.

- `services/outrigger/outrigger-dl/src/main/java/org/apache/river/outrigger/proxy/EntryRep.java:1222-1238` is `EntryRep.matches`.
  - It is positional.
  - Template wildcards (absent) are skipped.
  - Every non-wildcard slice must satisfy `Arrays.equals(mine[f], theirs[f])` (`:1233`).
  - Schema digest, table and class hash are outside the compared region (Javadoc `:1209-1220`; spec A.4.6). VERIFIED.
- Callers of `matches`, all in `outrigger-service/src/main/java/org/apache/river/outrigger/`:
  1. `EntryHolder.java:196`, in `hasMatch`. It is preceded by the hash quick-reject (`:191`) and by `EntryFieldIndex.candidates` (`EntryFieldIndex.java:198`), which looks up by `slice.hashCode()` (`EntryHandle.java:252`, `EntryRep.sliceHash` `:1071`).
  2. `EntryHolder.java:915`, in `handleMatch`.
  3. `OutriggerServerImpl.java:2731`, a watcher catch-up over multiple templates. It also calls `rep.isAtLeastA(tmpl.classFor())`.
  4. `OutriggerServerImpl.java:3178`, the single-template version of site 3, with the same `isAtLeastA` check.
  5. `OutriggerServerImpl.java:4223`, an iterator over a stored array.
  6. `TemplateHandle.java:90`, called from `WatchersForTemplateClass.java:113`.
- Two further byte comparisons exist: `EntryRep.equals` (`EntryRep.java:984-1010`, which also compares the class `hash`) and its use at `WatchersForTemplateClass.java:73`.
- **Class and superclass handling.** The schema digest is **not** compared in `matches`. Class identity comes from class name and holder selection: `OutriggerServerImpl.find` iterates `types.subTypes(templateClass)` and calls `holder.hasMatch` for each (`:3291-3306`). `isAtLeastA` is name-based (`EntryRep.java:1252-1262`). A superclass template matches a subclass entry because fields are ordered super-first and compared positionally over the template's slice count. The field comparator is at `EntryRep.java:1267`, and the position test is `EntryRepV2MatchContractTest.subclassPositionalAlignment_andShadowedFields` (`:116`). VERIFIED for the mechanism, INFERRED for the alignment guarantee.
- CEL filters (`FilterEval`, `EntryProjection`) run **after** the byte match as a second gate on candidates. They decode scalar slices class-free (`EntryProjection.java`; `EntryHolder.java:427`). They are a separate semantic comparison, not template matching. VERIFIED.

## Q6. Lookup (Reggie) matching

- Reggie does **not** use `EntryRepV2Body`. `reggie-dl/.../proxy/EntryRep.java` holds `MarshalledInstance[] fields`, one per field (`:77`, `:110`, fields built `:214-232`; DER or JOSS depending on `useDer`).
- `EntryRep.matchEntry` (`:389-398`) checks `tmpl.eclass.isAssignableFrom(eclass)`, the field count, and then `MarshalledInstance.equals` per non-null template field. `MarshalledInstance.equals` is `hash` equality plus `Arrays.equals(payloadBytes)` (`jgdms-platform/src/main/java/net/jini/io/MarshalledInstance.java:993-1001`). So it is an encoded-form match on payload bytes. VERIFIED.
- Caller chain: `RegistrarImpl.matchEntry` (`reggie-service/.../RegistrarImpl.java:4377`) from `matchAttributes` (`:4385`) from `matchItem` (`:4355`) and the lookup sites (`:2161`, `:2168`, `:2202`, `:2242`). `attrMatch` (`:4406`) also uses `matchEntry`. VERIFIED.
- Because the payload is a per-field `MarshalledInstance` with the same DER stream layer, canonicity is inherited from the same object-stream codec. INFERRED. In JOSS mode (`AtomicMarshalledInstance`) it compares JOSS bytes, which are not canonical in general. INFERRED.
- **`EntryRecord` does not exist as a Java type.** It is only a draft in STD-006 v0.13 section 7.7.2, a scalar-only "sidecar" record, ratified as distinct from `EntryRepV2Body` (`docs/JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md:24-55`). Searches of `jgdms-der`, `jgdms-lib`, `jgdms-lib-dl`, `jgdms-platform` and `services` found no class (NOT FOUND). The v0.13 text says byte equality is load-bearing for `EntryRecord` matching (`docs/JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md:1146`).

## Q7. Object-form matching outside the servers

- `jgdms-lib-dl/src/main/java/org/apache/river/lookup/entry/LookupAttributes.java`:
  - `matches` (`:261`) compares non-null template fields by `equal(val, efields[i].get(e))`. Its only caller found is `modify` (`:195`).
  - `equal(Object,Object)` (`:470-486`) uses `o1.equals(o2)` for String and boxed types. For anything else it uses `new MarshalledInstance(o1).equals(new MarshalledInstance(o2))` with the default marshalling format (INFERRED to be JOSS from the `MarshalledInstance(Object)` constructor, `:382`).
  - Callers: `JoinManagerImpl.java:2238` (`add`) and `:2426` (`modify`); `LookupCacheImpl.java:1671` and `ServiceDiscoveryManagerImpl.java:2303` (`equal`).
  - The answer can differ from the server's. JOSS bytes of a `HashSet` or `HashMap` value are order-dependent, so equal values can compare unequal here, while a DER server match would sort them or reject them.
- `jgdms-lib-dl/src/main/java/net/jini/entry/AbstractEntry.java:104-130` (`equals`) calls `tv.equals(ov)` per field. `hashCode` (`:163-173`) XORs field hashes. This is pure object `equals`. It can differ from the server in both directions.
  - It is stricter or looser depending on each class's `equals`.
  - It treats different insertion orders of equal `LinkedHashSet`s as equal, whereas bytes differ.
  - It ignores the `@AtomicSerial` class-digest component of a slice.
- `ServiceDiscoveryManager` and `LookupCache` apply the server-side template through Reggie and only a user `ServiceItemFilter` client-side (`LookupCacheImpl.java:2126`), which is arbitrary user code. No other client-side entry matcher was found in `jgdms-lib`.

## Q8. Round trip and determinism

- **Re-encode after decode:** NOT FOUND as an assertion on whole bodies.
  - `EntryRepV2MatchContractTest.roundTrip_bodyDecodePreservesSlices` (`:166-176`) asserts decoded slices byte-equal the encoded slices. This is decode of stored bytes, not decode then re-encode of objects.
  - `roundTrip_fieldValues_*` and `roundTrip_selfDescribingScalarValue` (`:179-200`) round-trip values only.
- **Same entry encoded twice, or across senders:**
  - `sliceIsPureFunctionOfValue_scalar` (`:73`) and `sliceIsContextIndependent_acrossEnclosingEntries` (`:86`).
  - `matchIndexParity_equalEntriesEqualSlices` (`:206`).
  - `EntryRepV2GoldenBytesTest` freezes entry bytes against files in `src/test/resources/golden-entry` (`:45-117`).
- **Canonicity tests in `jgdms-der`:**
  - `CollectionOrderingTest` has insertion-order independence for hash sets and maps (`:104-132`), recursion, and duplicate rejection (`:490`).
  - `FloatDoubleCharCanonicalTest` (`:154-261`) covers NaN variants, -0.0, and decoder rejection.
  - The Reggie-side tests are `SerialEntryRoundTripTest` and `EntryRepDerFormatTest`.
- **Spec:** Appendix A.4.1 requires the slice to be a pure function of the value alone and says payload canonicity is inherited as a precondition (`docs/JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md:176-200`). STD-008 section 17.3 is cited in code as requiring "encoder canonicalizes; decoder rejects non-canonical" (`SchemaGenerator.java:~480`). v0.13 `:351` and `:1140-1146` state the byte-equality requirement.

## Could not determine

- Whether Reggie DER-mode `MarshalledInstance` payloads share exactly the same top-level byte layout as an Outrigger slice payload. The `MarshalledInstance`/`DerMarshalFactory` encode path was not opened.
- Whether any Outrigger or Reggie ingest path re-validates payloads. A grep for `decodeFieldValue|decodeScalarSlice` in the services found them used only by `EntryRep` (client-side `entry()` and lazy decode) and `EntryProjection`.
- Which format `MarshalledInstance(Object)` uses by default. The constructor Javadoc says the default is Java Object Serialization; `MarshalFactoryInstance` was not read.
- No tests were run. The `@SerialEntry` collection mapping inferred in Q1 was not executed.
- Method bodies beyond the cited lines (for example `AnyCodec`, `encodeArrayTo`, `decodeCollection`) were skimmed for structure only.
