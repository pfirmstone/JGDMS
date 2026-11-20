# Design: record entries, `MarshalledEntry` and the space API that returns it (DRAFT)

- **Status:** DRAFT rev 1, REVIEWED BY A BOARD ON 2026-11-21: NOT TO BE ADOPTED AS WRITTEN; see section 11. REPLACED BY REV 2: `docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-rev2-2026-11-21.md`. Nothing in it is built. Nothing outside section 1 is a decision.
- **Date:** 2026-11-20. **Written by:** Claude Fable 5.2, proxy-board review session, at the operator's
  request ("please review and design the new API's", 23:52 +1000).
- **Read against:** JGDMS trunk `83c4d4c7a`. Uncommitted.
- **Review wanted:** a blind review by a different model (Opus) before any of it is acted on.

## 0. How to read this

- **VERIFIED** means the session opened that line on 2026-11-20. Nothing was compiled or run.
- **RELAYED** means taken from another document and not re-opened.
- **PROPOSAL** is the author's design. Every name in a proposal is illustrative.
- Rulings are cited by register ID only; the register holds their status.
- Paths are relative to `JGDMS/JGDMS`.

## 1. What the operator has said

Registered: RL-JGDMS-0134, RL-JGDMS-0135.

Said in chat on 2026-11-20 between 23:30 and 23:52 +1000 and **not registered**, because each was worded
as a view. They are the brief for this design:

1. "I think we need a new Entry specification, personally I would like to see the Entry fields final by
   default, it should be immutable. That reflects its true behaviour and prevents developers
   misunderstanding."
2. "We could use Records. Yes lookup attributes have some fields null as wildcards."
3. "Once an Entry is published, it is a very bad idea to modify it and inheritance allows that, however I
   have found through experience that we shouldn't always add additional functionality because it seems
   like a good idea, often less is more. Serialization with circular references is a good example."
4. "It's possible that developers may add fields without changing names, so identity should be more than
   just the class name."
5. "Perhaps what we can return when there is no local Entry class is a MarshalledEntry, which contains
   introspectible DER?"
6. "In fact we could return all Entry's in marshalled form by default and let the client deserialize what
   it wants?"

## 2. Review: what exists today

| # | Finding | Where | Label |
|---|---|---|---|
| 1 | `Entry` is an empty interface that extends `java.io.Serializable`. | `jgdms-platform/.../net/jini/core/entry/Entry.java` | VERIFIED |
| 2 | A usable entry field is public, non-static, non-transient and non-final. A final field is skipped, not refused. The rule is in three places. | `docs/JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md:305`; `jgdms-der/.../entry/EntrySchemaGenerator.java:235`; `services/outrigger/outrigger-dl/.../proxy/EntryRep.java:1027` | VERIFIED |
| 3 | For `@SerialEntry` classes final fields are already treated as legitimate: `AbstractEntry` does not skip them, "they are written in a real constructor". | `jgdms-lib-dl/.../net/jini/entry/AbstractEntry.java:260-267` | VERIFIED |
| 4 | `@SerialEntry` already separates the wire schema from the Java fields: `entryForm()`, a static `serialize(PutEntryArg, T)`, and a constructor taking `GetEntryArg`. | `jgdms-platform/.../net/jini/core/entry/SerialEntry.java`; `Name.java` uses it | VERIFIED |
| 5 | `GetEntryArg.get(name, defaultValue, type)` returns the default when the stored entry lacks the field, and `defaulted(name)` tells absent from null. So "a missing field decodes as a default" is already in the API. | `GetEntryArg.java:43-58, 91, 107` | VERIFIED (the API). Whether the v2 DER entry path honours it: NOT CHECKED. Finding 8 suggests it cannot. |
| 6 | The entry wire and storage record is `EntryRepV2Body`: `version 2`, `entrySchemaDigest` (32 octets), a `schemaTable`, and one `FieldSlice` per field, each `absent` or a value. Field names and declared types travel in the `EntrySchemaRecord` chain. | Appendix A.1, A.3 | VERIFIED (the text) |
| 7 | Matching compares the field slices only. The digest and schema table are outside the compared region. Type routing uses the class name and superclass names that `EntryRep` also carries. | Appendix A.4.6; `EntryRep.java:232-236` (`superclasses`, `hashes`, `hash`, `className`, `codebase`) | VERIFIED |
| 8 | Decode is strict: a schema entry whose digest does not match its chain is refused, and a field count that differs from the schema is refused. | `EntryRepV2Codec.java:394-395, 478` | VERIFIED |
| 9 | Field order is superclass first, then alphabetical within a class, for reflective entries; declared `entryForm()` order for `@SerialEntry`. | Appendix A.3, A.5 | VERIFIED (the text) |
| 10 | Declared field types and their wire types are a fixed table: boxed primitives, `String`, `Class`, `byte[]`, `@AtomicSerial` classes, enums, one-dimensional arrays, collection and map types. A bare collection value at the top of a slice and a live proxy value are refused at encode. | Appendix A.5, A.4.2 | VERIFIED (the text) |
| 11 | There are two entry records. The lookup ("sidecar") `EntryRecord` of STD-006 section 7.7.2 is scalar-only with one schema hash. The Outrigger record is richer. They were ratified as distinct and do not interoperate. | Appendix A.0 | VERIFIED (the text) |
| 12 | `EntryRep.entry()` loads the entry class by the codebase carried in the data. | `EntryRep.java:837` onwards; the 2026-10-31 Outrigger report Q6 | RELAYED for the line of the load |
| 13 | Class-free decode exists: the entry's schema chain, a scalar field value, and a field map of an `@AtomicSerial` value. | `EntryRepV2Codec.decodeEntrySchemaChain` (517), `decodeScalarSliceValueClassFree` (560); `ObjectCodec.decodeToFieldMap` (998) | VERIFIED (signatures only) |
| 14 | The client interfaces are `JavaSpace`, `JavaSpace05`, `TupleSpace` and `FilteredTupleSpace` (eight methods with a trailing `byte[] filter`). All return `Entry`. `MatchSet.next()` and `AvailabilityEvent.getEntry()` return `Entry`. `JavaSpace.snapshot(Entry)` returns an opaque pre-marshalled `Entry`. | `jgdms-lib-dl/.../net/jini/space/` | VERIFIED |
| 15 | The wire interface `OutriggerServer` has 20 methods. `read` and `take` return `Object` (an `EntryRep` or a query cookie). Eight carry `byte[] filterEnvelope`. | `outrigger-dl/.../proxy/OutriggerServer.java` | VERIFIED |
| 16 | No source file in `jgdms-der`, `jgdms-platform`, `jgdms-cel`, `jgdms-lib-dl` or `services/outrigger` mentions records. | grep for `isRecord`, `getRecordComponents`, `RecordComponent` | VERIFIED |
| 17 | Compiler release levels: `jgdms-platform`, `jgdms-jeri`, `jgdms-lib`, `outrigger-service` 21; `jgdms-der`, `jgdms-cel` 25; `jgdms-lib-dl`, `outrigger-dl` 8. | the module poms | VERIFIED |
| 18 | STD-006 is being frozen at 1.0 by another session. | RL-JGDMS-0133 | RELAYED |

Three consequences of the review for the brief:

- **Finding 7 means "the space matches on the exact digest" is not what the code does today.** Today the
  digest identifies; class names route; slices match. For record entries the design must state the rule
  (section 3.6).
- **Findings 3 to 5 mean most of RL-JGDMS-0134 is already true for `@SerialEntry`.** The reflective path
  is the only place that still requires non-final fields.
- **Finding 8 means lenient decode cannot be a switch on the existing decoder.** It is a projection by
  field name performed after a strict decode (section 4.4).

## 3. Part 1: record entries (the new Entry specification)

PROPOSAL throughout.

### 3.1 Definition

A **record entry** is a Java record that implements `Entry`. No annotation is needed; `Class.isRecord()`
identifies it. Legacy entries (reflective and `@SerialEntry`) are unchanged and keep every rule they have.

### 3.2 Components

| Rule | Reason |
|---|---|
| Every component is a reference type. A primitive component is refused. | A null component is a wildcard in a template (operator item 2). Same rule as today. |
| Component types come from the existing table (finding 10). | One type table, no second one. |
| The record is public, in a package the codec can reach, with its canonical constructor public. | Decode calls the canonical constructor. No field is ever assigned by reflection. |
| The canonical constructor must accept null for every component. | The same constructor builds templates. Validation that rejects null cannot live there (see open question 3). |
| A record entry that breaks a rule is **refused** by the schema generator. | Skipping, which is what happens to a final field today, hides the mistake. |

### 3.3 Schema and identity

- One `EntrySchemaRecord`, with no superclass record. The chain has length one.
- Fields are the record components in declared order, as `entryForm()` order is used today.
- `wireName` is the component name; `wireType` comes from the existing table.
- **Identity is `entrySchemaDigest`.** It covers the record's name and each component's name and type, in
  order (operator item 4). Adding, removing, renaming, retyping or reordering a component gives a new
  identity under the same class name. NOT CHECKED: the exact hash inputs of STD-006 section 7.7.1 and how
  a chain with no superclass is encoded.

### 3.4 No inheritance

A record is final. Interfaces a record implements take no part in matching. A template matches entries of
its own identity only. This is operator item 3 applied: the feature is left out, and can be added later
if a need appears.

### 3.5 Encode and decode

- Encode reads each component through its accessor.
- Decode calls the canonical constructor with the decoded values.
- `EntrySchemaGenerator` gains a record branch and stays the single source (appendix A.5 requires one).
  Filter authoring (`EntrySchemaView`) uses the generator, so record entries get filter authoring from the
  same change. RELAYED from the B1 memo section 7; not re-read in code.

### 3.6 Templates and matching

- A template is a record entry with null components as wildcards, or a `MarshalledEntry` (section 4), or
  absent (match any).
- The space applies a record template only to stored entries with the **same `entrySchemaDigest`**, then
  compares slices as today. This is the strict rule of RL-JGDMS-0135 row 1, stated against finding 7.
- A query across versions or types is a null template plus a predicate on field names. The filter already
  resolves names per candidate for a null template (B1 memo section 6.1). RELAYED.

### 3.7 Change and equality

- A record entry is immutable. An author writes "with" methods that return a new record.
- Equality is the record's own. `AbstractEntry` is not needed for record entries.
- Because the value cannot change, the wire form and its hash can be cached with the instance.

## 4. Part 2: `MarshalledEntry`

PROPOSAL throughout.

### 4.1 What it is

A final, immutable class in `net.jini.core.entry` that holds one verified entry body and lets a caller
read it without the entry's class. It implements `Entry`, so it can be passed anywhere an `Entry` is
accepted. It is the public, introspectable form of what `JavaSpace.snapshot` returns today (finding 14).

It can hold a record entry or a legacy entry. For a legacy entry the schema chain has more than one level.

### 4.2 Contents

- The entry body (`EntryRepV2Body` octets).
- Nothing else. **No codebase annotation and no class to load.** The type name is already in the schema
  record. This is the difference from `MarshalledInstance`.

### 4.3 Sketch

```java
public final class MarshalledEntry implements Entry {
    static MarshalledEntry of(Entry e);              // marshal a record or legacy entry
    byte[] schemaDigest();                           // identity
    String typeName();
    List<String> fieldNames();                       // declared order
    String fieldType(String name);                   // wire type from the schema
    boolean has(String name);
    boolean isNull(String name);                     // null field, or wildcard in a template
    Object get(String name);                         // class-free value, see 4.5
    <T extends Entry> T as(Class<T> type);           // strict: identities must be equal
    <T extends Entry> Converted<T> as(Class<T> type, Compatibility c);   // lenient, see 4.4
    MarshalledEntry with(String name, Object value); // a new entry; every other field kept
}
public record Converted<T>(T entry, List<String> dropped, List<String> defaulted) { }
```

### 4.4 Rules

| Rule | Detail |
|---|---|
| Verified once | The constructor and the deserialization path run the strict decoder (finding 8) and the size ceilings. Later introspection never parses unverified octets. |
| Receiver names the class | `as(type)` is the only way to get a typed object. The class comes from the caller, never from the data. |
| Strict by default | `as(type)` requires the entry's identity to equal the identity the generator computes for `type`. |
| Lenient on request | `as(type, ADDITIVE)` projects by field name. A field the class lacks is dropped; a field the entry lacks becomes null; the same name with a different wire type is refused. The result reports both lists. |
| Dropped fields are skipped as octets | They are never decoded. |
| No class download | On a code-free host a class that is not local is an error. A legacy host may keep today's digest-gated download for legacy entries. RELAYED: design memo section 13 answer 4. |
| `with` keeps unknown fields | An old client can change one field of a newer entry and write it back without loss. |

RL-JGDMS-0135 row 5 ("lenient decode applies to read and not to take") was written before `MarshalledEntry`
was proposed. If every operation returns a `MarshalledEntry`, `take` loses nothing and the restriction has
no purpose. Replacing that row needs a new ruling; the statement of RL-JGDMS-0135 is not changed here.

### 4.5 What a class-free reader can see

| Field value | `get(name)` returns |
|---|---|
| Boxed primitive, `String`, `byte[]` | The value. The codec has a class-free scalar decode (finding 13). |
| Enum | The constant's name. NOT CHECKED against the payload shape. |
| `@AtomicSerial` object | A field map by class and field name (finding 13), or an opaque value holder. |
| Collection, map, array | Open question 5. |

### 4.6 Security rules

| Risk | Rule |
|---|---|
| A missing field became null, and the entry is reused as a template, where null is a wildcard | `Converted` reports `defaulted`. A plain record cannot carry the mark, so the rule is on the caller; `MarshalledEntry` itself never widens, because it keeps the real fields. |
| The dropped field was the important one | `Converted` reports `dropped`. Open question 6: a "must be understood" mark set by the writer. |
| Authorisation or filter applicability tied to identity | Always computed from the entry's real `schemaDigest`, never from the reader's class. |
| Nested type changed shape under the same name | Compatibility compares the nested value's schema digest, which each slice already carries (finding 6). |
| Duplicates collapse after a lenient conversion | Documented; `MarshalledEntry` equality is on the full body. |
| Hostile octets | Strict decode and ceilings at construction; nothing is lazily parsed later. |

## 5. Part 3: the space API

PROPOSAL throughout. This part follows rulings already recorded for the other services (design memo
section 12 rulings 2, 3 and 6: a record-style `Remote` interface, a resident wrapper, applied at the
client). RELAYED; check their register status before relying on them.

### 5.1 Client interface

A new interface beside `TupleSpace` in `net.jini.space`. It returns `MarshalledEntry` everywhere.

```java
public interface MarshalledSpace {                       // name illustrative
    Lease write(Entry entry, Transaction txn, long lease);
    List<Lease> write(List<? extends Entry> entries, Transaction txn, List<Long> leases);

    MarshalledEntry read(Query q, Transaction txn, long timeout);
    MarshalledEntry readIfExists(Query q, Transaction txn, long timeout);
    MarshalledEntry take(Query q, Transaction txn, long timeout);
    MarshalledEntry takeIfExists(Query q, Transaction txn, long timeout);
    Collection<MarshalledEntry> take(Collection<Query> qs, Transaction txn, long timeout, long max);
    MarshalledMatchSet contents(Collection<Query> qs, Transaction txn, long lease, long max);

    EventRegistration notify(Query q, Transaction txn, RemoteEventListener l,
                             long lease, MarshalledInstance handback);
    EventRegistration registerForAvailabilityEvent(Collection<Query> qs, Transaction txn,
                             boolean visibilityOnly, RemoteEventListener l,
                             long lease, MarshalledInstance handback);
}
public record Query(Entry template, byte[] filter) { }   // either may be null; both null = match any
```

Points of difference from today:

| Point | Today | Proposed |
|---|---|---|
| Return type | `Entry`, decoded by the proxy, which may load a class | `MarshalledEntry`; the caller decodes if it wants to |
| Template and filter | Separate parameters; one filter per call | One `Query` per template. This allows a filter per template, which B1 left open (its section 10 item 4). RELAYED. |
| `write` argument | `Entry` | `Entry`: a record entry, a legacy entry or a `MarshalledEntry` |
| `snapshot` | A method on the space | Not needed: `MarshalledEntry.of(entry)` |
| `MarshalledObject` overloads | Present, deprecated | Absent. RL-JGDMS-0132 says this for the lookup API only; the same for the space needs the operator. |
| Event payload | `getEntry()` returns a decoded `Entry` | A new event type whose entry is a `MarshalledEntry` |

### 5.2 Legacy interfaces

`JavaSpace`, `JavaSpace05`, `TupleSpace` and `FilteredTupleSpace` stay. The resident wrapper presents them
and decodes eagerly, as the proxy does today, because existing clients cast the result to their class.

| Host | Legacy call | New call |
|---|---|---|
| Legacy host | Entry class may be downloaded, digest-gated | `MarshalledEntry`; no class load |
| Code-free host | Entry class must be local, else `UnusableEntryException` | `MarshalledEntry`; no class load |

### 5.3 Wire interface

A new `Remote` interface in a resident jar, in the style of the existing `OutriggerServer` (finding 15),
with these changes:

- The entry travels as a body record without a codebase. Open question 7: whether the class name and
  superclass names stay for routing legacy entries.
- `read` and `take` return a typed result record (an entry, or a cookie to continue the query), not `Object`.
- The filter travels per template.
- No `MarshalledObject`.

The query loop, cookies, lease construction and the match-set iterator stay in the resident wrapper.

### 5.4 Server

Storage is unchanged: the body is stored as received. For a record entry the match rule is section 3.6.
The filter's handling of subclass candidates (B3) applies to legacy entries only.

### 5.5 Left to the investigation already scoped

Projection, a server-side update, and write admission are questions E.2, E.4 and E.5 of
`docs/SOW-Outrigger-Mahalo-CEL-Code-Free-Investigation-2026-11-20.md`. This design changes how E.4 should
be read: with immutable entries an update is "take the matched entry and write a derived one, atomically",
with a new identity and lease, not a change in place.

## 6. What changes in other documents and code, if adopted

| Item | Change |
|---|---|
| STD-006 appendix A.5 | A record branch in the field rules. The non-final rule stays for reflective legacy entries, so no existing digest changes. |
| STD-006 section 7.7.1 | Confirm the hash inputs cover what section 3.3 claims. |
| STD-006 freeze (RL-JGDMS-0133) | This is an addition. It goes to the STD-006 owner session; this session edits no standard. |
| `EntrySchemaGenerator` | Record branch; refuse an invalid record entry. |
| `EntryRep.usableField`, `AbstractEntry` | Unchanged for legacy entries. |
| `jgdms-platform` | `MarshalledEntry`, `Converted`, `Compatibility`. The codec is reached through the existing `EntryV2Codec` seam. |
| `jgdms-lib-dl` (release 8) | The new space interface uses records and generics. Either the module's release rises or the interface lives elsewhere. Open question 8. |
| Outrigger | New wire interface, wrapper, digest rule for record templates, new event type. |
| Lookup (Derek) | Open question 9. |
| The Outrigger and Mahalo scope | Its sections A2, C.4, C.5 and E should take this design as input. The scope file has not been edited. |

## 7. Open questions for the operator

1. Are operator items 1 to 6 in section 1 rulings? If so they will be registered as worded.
2. Replace RL-JGDMS-0135 row 5 as section 4.4 describes?
3. Null in the canonical constructor. Option A: every record entry accepts null everywhere (simple; the
   same type is entry and template). Option B: a template is never a record instance, only a `Query` built
   from field names and values, so a record's constructor may validate. A is the proposal.
4. Is component order part of identity? The proposal is yes.
5. What a class-free reader sees for collections, maps, arrays and nested objects.
6. A writer-set "must be understood" mark on a field, so a lenient reader that lacks it refuses.
7. Does the new wire record keep the class name and superclass names for legacy routing?
8. Where the new space interface lives, given `jgdms-lib-dl` is release 8.
9. Does the lookup API also present its entries as `MarshalledEntry`? The two wire records were ratified
   as distinct (finding 11); one public type over both is possible but is new work.
10. Does a record entry also need a marker beyond `Entry`, for example to keep `Serializable` (finding 1)
    from implying that Java serialization of a record entry is supported?
11. May record components be nested records, and how is a record that is not an entry encoded?

## 8. Hazards, in the order the author would address them

1. **The standard is being frozen** (finding 18). Decide whether record entries are in 1.0 or after it
   before anything else.
2. **Finding 7.** The digest rule for record templates is new server behaviour and needs a test that fails
   without it: a template of one identity must not match an entry of another identity with equal slices.
3. **Two Java classes of one name.** A host can hold one version of a record. `as(type)` must fail closed
   on any other version unless the caller asked for lenient conversion.
4. **Null and validation** (open question 3).
5. **Release 8 modules** (open question 8).

## 9. Not checked

- Whether the v2 entry path honours `GetEntryArg`'s default for an absent field (finding 5).
- The hash inputs of STD-006 section 7.7.1; the encoding of a chain with no superclass.
- Enum, collection and array payload shapes for class-free reading.
- Whether `@AtomicSerial` can be applied to a record, and how a nested record would be encoded.
- `EntryRep.entry()` beyond its signature; `SnapshotRep`; `MatchSetData`; `OutriggerAvailabilityEvent`.
- The lookup result's section E (`EntryRecord` in the Derek sketch) beyond a keyword search.
- The register status of the design memo's section 12 and 13 rulings cited in section 5.
- Nothing was compiled or run.

## 10. Operator answers after rev 1 (added 2026-11-21 00:10 +1000; sections 1 to 9 are unchanged)

Registered since rev 1: RL-JGDMS-0136, RL-JGDMS-0137, RL-JGDMS-0138, RL-JGDMS-0139, RL-JGDMS-0140. The
register holds their text and status. What they bear on:

| Ruling | Bears on |
|---|---|
| RL-JGDMS-0136 | Section 7 question 1: the six statements of section 1 go to a review board. |
| RL-JGDMS-0137 | Section 4.4, the paragraph on RL-JGDMS-0135 row 5, and section 7 question 2. |
| RL-JGDMS-0138 | Section 3.2 (null in the canonical constructor) and section 7 question 3. |
| RL-JGDMS-0139 | Section 6 (STD-006) and section 8 hazard 1. Whether 1.0 or a later version is not yet answered. |
| RL-JGDMS-0140 | Section 6 (`jgdms-lib-dl`) and section 7 question 8. The module is not yet answered. |

The board was convened on 2026-11-21: two blind seats on models other than the author's. Their reviews are
`docs/REVIEW-entry-records-design-opus-2026-11-21.md` and `docs/REVIEW-entry-records-design-sonnet-2026-11-21.md`.

## 11. Board review, 2026-11-21 (two blind seats; added by the author, sections 1 to 10 unchanged)

The reviews are the record; this section does not replace them and edits no finding away.

| Seat | Model | File | Verdict | Findings |
|---|---|---|---|---|
| 1, adversarial | Opus | `docs/REVIEW-entry-records-design-opus-2026-11-21.md` | Do not adopt as written; the core idea is sound | 4 HIGH, 10 MEDIUM, 5 LOW |
| 2, compatibility | Sonnet | `docs/REVIEW-entry-records-design-sonnet-2026-11-21.md` | Feasible, and less new than the draft says | 3 HIGH, 7 MEDIUM, 5 LOW |

Neither seat compiled or ran anything. Both read commit `83c4d4c7a`. Both were told during their work of RL-JGDMS-0146 and seat 2 of RL-JGDMS-0142.

### 11.1 HIGH findings and the author's reply

"Re-opened" means the author opened the cited code on 2026-11-21 after the seat reported. Otherwise the finding is RELAYED from the seat.

| Finding | Seat | Author's reply |
|---|---|---|
| H1. The entry's type travels five times in `EntryRep` and only the body chain is checked; the filter selects by the unchecked digest field and channel authorisation by the unchecked class name. | 1 | Accepted as a lead. Not re-opened. It is a finding about existing code and stands whatever happens to this draft. |
| H2. `EntryRep.entry()` loads a class named by the writer from a codebase named by the writer; the design needs no class download at all. | 1 | Accepted. Sections 4.4 and 5.2 of this draft keep a download path for legacy hosts; under RL-JGDMS-0146 that path should go. Needs the operator's answer to question 4 of the lookup memo's section 17.4. |
| H3. A filter per template (`Query`) breaks the server's one-filter-per-operation rule; fan-out sites pass no template hint. | 1 | Accepted. Section 5.1's `Query` is withdrawn pending a design that either keeps one filter per operation or changes the fan-out sites. |
| H4. A canonical constructor that must accept null removes the record's own validation. | 1 | The seats disagree (11.2). This is RL-JGDMS-0138; only the operator can change it. |
| F1. The space already refuses a second hash for a class name (`OutriggerServerImpl.typeCheck`), so "strict digest" is not new, and versions of one name cannot coexist today. | 2 | Re-opened and confirmed: `typeCheck` and `checkClass` record the first hash per class name and throw on a different one. Section 2 finding 7 and section 3.6 of this draft missed it. RL-JGDMS-0135 assumes versions can sit side by side; today they cannot. |
| F2. Every reflective path sees a record as having zero fields, so `LookupAttributes.matches` would match any two records of one class. | 2 | Accepted as a lead. Not re-opened. Record entries must be refused by those paths until they are taught about records. |
| F3. `MarshalledEntry` holds the body only, but legacy routing data (class name, superclass names, hash) is outside the body. | 2 | Accepted. Section 4.2 is wrong for legacy entries. Either `MarshalledEntry` is limited to record entries first, or it carries the routing data. |

### 11.2 Where the seats disagree

| Point | Seat 1 | Seat 2 |
|---|---|---|
| Null in the canonical constructor (RL-JGDMS-0138) | Revisit: put wildcards in the query, not in the record | Keep: accept null, check invariants elsewhere |
| Length of a record's schema chain | Two records, because the generator walks up to `java.lang.Record`; and the digest differs with and without `@SerialEntry` | One record with no superclass on the existing `@SerialEntry` encode path |

On the second point the author re-opened `EntrySchemaGenerator.java:162`: the reflective walk stops only at `Object`, so it would include `java.lang.Record`. Both seats can be right, each about a different path. The specification must pick one path for records.

### 11.3 Corrections to this draft's section 2

- Finding 16 is wrong in substance (seat 1). The `@SerialEntry` documentation has a "Java records" section with a worked example (`SerialEntry.java:102-127`; re-opened and confirmed). Nothing in the tree implements or tests one (seat 2: 0 records).
- Finding 5: the default for an absent field is unreachable on the DER path today (seat 2, F4). RELAYED.
- Section 3.5's claim that filter authoring comes free is wrong (seat 2, F6): the generator and `EntrySchemaView` reject `@SerialEntry`. RELAYED.
- Finding 15's method count was not reproduced by seat 1.

### 11.4 The seats' advice on the six operator statements (RL-JGDMS-0136)

| Statement | Seat 1 | Seat 2 |
|---|---|---|
| 1. Fields final, immutable | Adopt with a change: "shallowly immutable", restrict component types | Adopt with a change: a new record kind only; legacy stays |
| 2. Records; nulls as wildcards | Adopt records; wildcards in the query | Adopt; constructor accepts null; lookup stays legacy |
| 3. No inheritance | Adopt | Adopt, scoped to record entries |
| 4. Identity beyond the class name | Adopt with a change: digest from the body, extended to declared component classes; decide on coexisting versions | Adopt with a change: define its relation to the existing hash and `typeCheck` |
| 5. `MarshalledEntry` | Adopt with a change: verified in its constructor, Java deserialization refused, preferably not an `Entry` | Adopt with a change: routing data for legacy or records first; a template builder |
| 6. Marshalled by default | Adopt for the new interface only; events stay free of contents except availability events | Adopt for the new interface only; a decode failure fails the call |

Neither seat recommends rejecting any statement. The operator decides.

### 11.5 Survey figures (seat 2; RELAYED; method and caveats are in its file)

- Entry classes: 15 in main sources, 3 in examples, 54 in `qa`. `@SerialEntry`: 10 in main, none in `qa`. Records: none.
- Inheritance between entry classes: none between concrete classes in main; 29 `qa` classes in 6 families; about 55 test files use superclass templates.
- Public final fields: none in main or `qa`. Mutating methods: none in main; 13 `qa` classes. The lookup entry beans assign fields in 8 beans.
- Casts of `read` or `take` results: none in main or examples; 57 of 66 calling files in the `qa` space specification tests.
- `snapshot`: no callers in main; 42 `qa` files.
- No `module-info.java` anywhere. 36 poms depend on `jgdms-lib-dl`.
- Release levels: a client needs Java 21 to load the platform and Java 25 to encode or decode DER. Release 16 is not a real floor (seat 2, F5). Seat 2 recommends a new local module at release 21 and leaving `jgdms-lib-dl` alone.

### 11.6 What a rev 2 of this draft must do

1. Base record entries on the existing `@SerialEntry` path, not a new branch, and say which schema chain a record has.
2. State identity against the existing per-name hash check, and decide whether two versions of one name may coexist in a space.
3. Remove every class-download path, if the operator confirms.
4. Replace `Query` with a design that fits the server's filter rule.
5. Limit `MarshalledEntry` to record entries first, verify it in its constructor, and refuse Java deserialization.
6. Restrict component types so "immutable" is true, or say "shallowly immutable".
7. Keep the standard lookup attributes and beans on legacy entries.
8. Take the medium findings in both review files one by one.
