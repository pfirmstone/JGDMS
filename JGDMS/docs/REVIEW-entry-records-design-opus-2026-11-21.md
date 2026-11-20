# Review: entry records, MarshalledEntry and the space API (adversarial seat)

- **Seat:** 1 of 2, ADVERSARIAL. Blind: I have not seen the other seat's review.
- **Reviewer model:** Claude Opus 5.5 (as I understand it; model ID claude-opus-5-5).
- **Date:** 2026-11-21.
- **Commit read:** `83c4d4c7af7bcb14bd33e07a68bbaa4ee83f36d3` (the same trunk the draft names).
- **Under review:** `docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-2026-11-20.md`, DRAFT rev 1 plus section 10.
- **Read:** `docs/JGDMS-Board-Reviewer-Guidance.md` (Part I, section 2.2); the draft in full;
  `docs/JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md` in full; STD-006 v0.13 section 7.7.1;
  `DESIGN-SpaceChannelAuthorization.md` sections 2 to 10; B1 memo section 10.
  Code: `Entry`, `SerialEntry`, `GetEntryArg`, `EntryRep` (outrigger-dl), `OutriggerGetEntryArgImpl`,
  `OutriggerServer`, `EntryV2Codec`, `EntrySchemaGenerator`, `EntryRepV2Codec`, `SchemaGenerator.toWireType`,
  `AtomicSerialSchemaRecord` (header), `ResolutionContext`, `DerObjectStreamCodec` (enum and class-free
  parts), `AbstractEntry` (field skip rule), `FilterSet`, `FilterEval`, `FilterAdmission` (schema step),
  `EntryProjection.projectReusing`, `EntryHolder.hasMatch`, `OutriggerServerImpl.typeCheck`/`checkClass`,
  `TypeTree.addTypes`, and the list of `.matches(` call sites in outrigger-service.
- **Not done:** nothing was compiled or run. No adversarial input was executed against built classes
  (guidance G13). Every HIGH below rests on reading, not on an observed failure. Say so when weighing them.
- **Operator update received mid-review:** new releases do not interoperate live with old ones; no new
  downloadable code; DER registrar only. Section 4 finding H2 and section 6 apply it.
- Line numbers are from the commit above. "L" means line.

## 1. Verdict (ten lines)

1. Do not adopt as written. The core idea (immutable record entries, a class-free `MarshalledEntry`, a
   space API that never loads the sender's class) is sound and fits the no-download ruling.
2. The identity story is not yet true in code. Five copies of "what type is this entry" travel in
   `EntryRep` (className, superclasses, legacy hash, routing digest, body chain) and only the body chain is
   verified. The "same digest" rule is unsafe unless the digest it uses is the one derived from the body.
3. The per-template filter (`Query`) breaks a live server invariant (one filter expression per operation).
4. The null-accepting canonical constructor ruling removes the only place a record can enforce its own
   invariants; hostile writers then hand readers invariant-violating objects.
5. `MarshalledEntry` "verified once" is overstated: today's decoder checks structure, not payload type or
   canonical form. The writer-chosen class download path should be removed, not kept for "legacy hosts".
6. Findings: 4 HIGH, 10 MEDIUM, 5 LOW. All fixable before code exists.

## 2. Section 2 findings check

| # | Draft claim (short) | Result | Evidence (VERIFIED unless marked) |
|---|---|---|---|
| 1 | `Entry` is empty, extends `Serializable` | CONFIRMED | `Entry.java` L77 `public interface Entry extends java.io.Serializable` |
| 2 | Usable = public, non-static, non-transient, non-final; final skipped; rule in three places | CONFIRMED | Appendix L303-306; `EntrySchemaGenerator.isUsable` L234-249; `EntryRep.usableField` L1024-1040 |
| 3 | `AbstractEntry` keeps final fields for `@SerialEntry` | CONFIRMED | `AbstractEntry.java` ~L260-268 "final fields are legitimate" |
| 4 | `@SerialEntry` separates wire schema from Java fields | CONFIRMED, but incomplete | `SerialEntry.java` javadoc; ALSO: `EntrySchemaGenerator.generate` L153-157 REFUSES `@SerialEntry` ("unit 2"); the space encodes it through a separate path `EntryRepV2Codec.encodeSerialEntry` L299-317 with a single-record chain and no superclass link |
| 5 | `GetEntryArg` defaults absent fields | CONFIRMED (API). The NOT CHECKED part: the v2 path cannot reach it | `EntryRep.entry()` L850 refuses any version whose legacy hash differs, before decode. `entryViaSerialEntry` L938-958 zips the LOCAL `entryForm()` names with stored slices by POSITION, not by stored name, and turns a per-field decode failure into `null` (L958 `rawValues[i] = null`) |
| 6 | `EntryRepV2Body` shape | CONFIRMED | Appendix A.1; `EntryRepV2Codec.decode` L338-485 |
| 7 | Matching compares slices only; digest outside; class names route | CONFIRMED, and worse | `EntryRep.matches` L1222-1240; `isAtLeastA` L1252-1265. Also: the `entrySchemaDigest` FIELD of `EntryRep` is a separate serial field (L695, L719) never compared with the body's digest (see H1) |
| 8 | Strict decode: digest/chain mismatch and field-count skew refused | CONFIRMED | `EntryRepV2Codec` L395 and L478. Note: decode does not parse or type-check slice payloads (L425-447) |
| 9 | Order: super first, alphabetical within class (reflective); `entryForm()` order (`@SerialEntry`) | CONFIRMED | `EntrySchemaGenerator` L197, L252-263; `encodeSerialEntry` positional |
| 10 | Fixed type table | CONFIRMED with a gap the draft relies on | `SchemaGenerator.toWireType` L423-512: an `@AtomicSerial` class, any interface and any abstract class all map to the bare token `"@AtomicSerial"` with NO class name (L439, L504-506). See finding H3/C below |
| 11 | Two entry records, distinct | CONFIRMED (text) | Appendix A.0 |
| 12 | `EntryRep.entry()` loads by codebase in the data | CONFIRMED (now VERIFIED, not relayed) | `EntryRep.java` L846-848 `CodebaseProvider.loadClass(codebase, className, ...)`; both are sender-supplied serial fields (L699-700) |
| 13 | Class-free decode exists | CONFIRMED (signatures) | `decodeEntrySchemaChain` L517, `decodeScalarSliceValueClassFree` L560, `ObjectCodec.decodeToFieldMap` L998. Class-free scalar decode excludes enum, char and `Class` (`DerObjectStreamCodec` L1929-1934) |
| 14 | Client interfaces return `Entry`; `FilteredTupleSpace` eight filter methods; `snapshot` | CONFIRMED with one count off | `JavaSpace` L91, L154, L286; `MatchSet` L143; `AvailabilityEvent` L197, L225. `grep -c "byte[] filter"` on `FilteredTupleSpace.java` gives 9 (one may be javadoc); I did not resolve the ninth |
| 15 | `OutriggerServer` 20 methods; read/take return `Object`; eight carry `filterEnvelope` | PARTLY CHECKED | Eight `byte[] filterEnvelope` methods confirmed (L492-568); `Object read/take` confirmed (L32-45). The count of 20 I did not reproduce exactly (NOT CHECKED) |
| 16 | No source file mentions records | WRONG in substance | The draft's grep found no record API calls, true. But `SerialEntry.java` L102-133 already documents "Java records" as `@SerialEntry` entries, with an example whose `GetEntryArg` constructor calls `requireNonNull`. A record entry path is specified today (untested: no test record entry found under the five modules) |
| 17 | Release levels | CONFIRMED | poms: platform 21, der 25, cel 25, lib-dl 8, lib 21, jeri 21, outrigger-dl 8, outrigger-service 21 |
| 18 | STD-006 freeze | NOT CHECKED | register not read (brief forbids writing; I did not need it) |

## 3. Findings, most severe first

Labels: VERIFIED = I opened the line in this session. INFERRED = reasoned from verified lines. NOT CHECKED = not looked at.

### H1. Five copies of the entry's type travel in `EntryRep`; only one is verified

- **Severity:** HIGH. **Status:** CONFIRMED (the gap); the exploit is PLAUSIBLE (depends on authorization that is designed but which I did not find built).
- **Evidence (VERIFIED):** `EntryRep.serialForm` L692-702 carries `body`, `entrySchemaDigest`, `superclasses`, `hashes`, `hash`, `className`, `codebase` as separate fields. The `GetArg` constructor L717-735 takes each from the wire and decodes only `body`; nothing compares the field `entrySchemaDigest` with the body's own digest, nor `className` with the leaf `className` of the body chain. `restore` L1344-1350 does the same from storage.
  Who reads which copy: `FilterEval.matches` L177 selects filters with `candidate.entrySchemaDigest()` (the unverified field); `EntryProjection.projectReusing` L398 projects with `decoded.entrySchemaDigest` (the body's). `OutriggerServerImpl.typeCheck` L1495-1510 and `TypeTree.addTypes` L216-235 use `className`, `superclasses`, `hash`. `EntryRep.entry()` L846-850 loads `className` from `codebase` and checks the legacy `hash`. `DESIGN-SpaceChannelAuthorization.md` section 3 keys authorization on `className`.
- **Scenario:** a writer allowed to write channel `X` sends an `EntryRep` whose `className` is `X` and whose body chain is the schema of `com.bank.Secret`. It lands in holder `X`. A reader with a null template (or the proposed `MarshalledEntry` API) receives it; `typeName()` (from the body) says `com.bank.Secret`; strict `as(Secret.class)` succeeds because the identities are equal. The reader now holds a `Secret` that did not come through the `Secret` channel. Second scenario: the field digest says schema `A`, the body is schema `B`; the reader's filter compiled for `A` is selected and then evaluated over `B`'s fields by name.
- **Why it matters for this design:** section 3.6's "same `entrySchemaDigest`" rule and section 4.6's "always computed from the entry's real `schemaDigest`" are only true if every one of these values is derived from the verified body. Open question 7 (keep class and superclass names on the wire) is exactly this hazard.
- **Smallest fix:** the server derives `className`, the superclass list and the digest from the body chain at decode and refuses an `EntryRep` whose carried values disagree (or stops carrying them). The new wire record carries the body and nothing else. Add a negative test: a rep whose `className` or digest field disagrees with its body is refused at write.

### H2. The reader still downloads and runs a class the writer names (answer to the operator update)

- **Severity:** HIGH. **Status:** CONFIRMED (path exists).
- **Evidence (VERIFIED):** `EntryRep.entry()` L846-848: `CodebaseProvider.loadClass(codebase, className, null, integrity, null)`. `codebase` and `className` are writer-supplied serial fields (L699-700, L723-724) with no binding to the body (H1). The only identity gate after the load is the legacy 64-bit `hash` (L850), which the same writer also supplies.
- **Scenario:** writer W writes an entry naming a class and a codebase of W's choice. Any reader that calls the legacy `read`/`take` without that class locally downloads it from W's codebase. Integrity and policy are the remaining gates (NOT CHECKED how strong `integrity` is per deployment).
- **The design keeps this path** for "legacy hosts" (sections 4.4 and 5.2).
- **Recommendation under the new ruling:** the design does not need a class-download path at all. The rule can be exactly: "the entry class is local, or the caller uses `MarshalledEntry`." Legacy interfaces through resident wrappers decode with a local class or throw `UnusableEntryException`. Remove `codebase` from the new wire record, and stop honouring it in the wrappers. Nothing in the draft depends on download except the two rows that describe it.

### H3. A filter per template (`Query`) breaks a live server invariant

- **Severity:** HIGH. **Status:** CONFIRMED (the invariant); the misapplication is INFERRED from the code.
- **Evidence (VERIFIED):** `FilterSet.Builder.assertOneEnvelope` throws if two filters in one operation have different expressions ("would silently misapply one template's predicate to another template's candidates"). `FilterSet.applicableTo` picks "any concrete filter" as a correct representative when the candidate digest has no key, justified only by that invariant. The fan-out sites pass no template hint: `ReadWatcher` L64 and L81, `ReadIfExistsWatcher` L117 and L164, `EventRegistrationWatcher` L216, `AvailabilityRegistrationWatcher` L217 call `FilterEval.matches(filters, rep)` without the matched template. `FilterSet` keys filters by schema digest, so two `Query` values with the same template schema and different filters cannot even be stored apart.
- **Scenario:** `take([Query(T, owner=="me"), Query(T, priority>5)])`. Both templates have schema `T`; the map keeps one filter. A candidate that matched the second template is tested with whichever filter survived, and may be taken (destroyed) although its own query's filter would have excluded it. The B1 memo section 10 item 4 left the wire shape open; it did not analyse the server.
- **Smallest fix:** keep one filter per call in the first release (the `Query` record can carry it at the call level). If per-template filters are wanted, every match and fan-out site must carry the index of the template that matched, and `FilterSet` must key by template index, not by digest. That is new server work, not an API change.

### H4. A null-accepting canonical constructor removes the record's own validation

- **Severity:** HIGH. **Status:** PLAUSIBLE (design-level; nothing built).
- **Evidence:** draft section 3.2 rule 4 and RL-JGDMS-0138 (register text not read; I take the brief's word that it is a ruling). Guidance G5: semantic checks live in the constructor (Layer 2). The existing record example in `SerialEntry.java` L122-125 (VERIFIED) validates in its `GetEntryArg` constructor with `requireNonNull`; the ruling forbids that in the canonical constructor of a record entry.
- **Scenario:** a record `Grant(String owner, Set<String> allowedPrincipals)`. A hostile writer stores `allowedPrincipals = null`. The reader's `as(Grant.class)` calls the canonical constructor, which must accept null. Reader code that treats null as "no restriction" now grants to everyone; code that does not, throws later. A second path: a lenient conversion defaults a missing field to null, the result is used as a template, and null is a wildcard, so the template matches more than intended (the draft notes this and leaves it "on the caller", which is outcome without mechanism, G2).
- **Smallest fix:** templates are never instances of the record (draft option B): a template is a `Query` built from a type and named values, or a `MarshalledEntry`. The record's canonical constructor may then validate, and decode calls it, so every typed entry a reader holds has passed its type's checks. I advise the board to recommend that the operator revisit RL-JGDMS-0138 on this ground.

### M1. Where the "same digest" rule must live

- **Severity:** MEDIUM. **Status:** CONFIRMED (sites).
- **Evidence (VERIFIED):** template matching is called from at least six places: `EntryHolder.hasMatch` L196; `EntryHolder.handleMatch` L915 (multi-template); `OutriggerServerImpl` L2731 and L3178 (inside transaction scans) and L4223 (`nextReps`, the contents iterator); `TemplateHandle.matches` L90, which the notify, availability and read watchers reach through `WatchersForTemplateClass` L113.
- **Constructed cross-identity match (INFERRED; valid only if two versions of one class name can coexist, see M2):** stored `R` v1 = `(String a, String b)`, template `R` v2 = `(String a, String c)` with `a` null and `c = "x"`. Same holder, slice 1 of both is `value{empty digest, "x"}`, so `matches` returns true across identities.
- **Smallest fix:** put the rule inside `EntryRep.matches` (it is `final`, one chokepoint), comparing body-derived digests (H1). The server must also know class-free that a template is a record entry; nothing on the wire says so today. Either mark it in the schema (STD change) or apply "same digest" to every template whose chain has one class record (and fix M3 so records have one).

### M2. The server today refuses a second version of a class name, forever, first caller wins

- **Severity:** MEDIUM. **Status:** CONFIRMED.
- **Evidence (VERIFIED):** `OutriggerServerImpl.checkClass` L1524-1540 stores the first `(className, hash)` it sees in `typeHashes` (L264, a plain `HashMap`) and throws "Class mismatch" for any other hash later. Nothing removes entries. `typeCheck` runs on writes AND on templates (L1608, L1944, L2370-2414 and more).
- **Consequences:** (a) operator statement 4 and draft 3.3 assume versions of one class name can coexist; the server forbids it and the draft does not mention this. (b) Squatting: any client that can send a template first can pin a bogus hash to `com.bank.Payment` and lock the real class out of the space. (c) Memory: every distinct class name in any template adds a permanent entry; the same holds for `TypeTree`.
- **Smallest fix:** decide the policy (one version per name per space, or many). Either way key on the body digest, not the writer's legacy hash, do not record anything from a template, and bound both maps.

### M3. A record's chain is two records long today, and the same record can get two identities

- **Severity:** MEDIUM. **Status:** CONFIRMED by reading.
- **Evidence (VERIFIED):** `EntrySchemaGenerator.generate` L162 walks `c != Object.class`; the superclass of every record is `java.lang.Record`, so the chain gets a second record `java.lang.Record` with no fields and the leaf gets a `parentSchemaHash`. `EntryRep` L546-557 adds `java.lang.Record` to `superclasses`, so `TypeTree` files every record entry under one supertype. Meanwhile the documented record pattern (`SerialEntry.java` L102-133) goes through `encodeSerialEntry`, which builds a single-record chain with no parent. One record class, two digests, depending on an annotation.
- **Smallest fix:** the specification states that the record chain stops at `java.lang.Record` (no parent hash, not in `superclasses`), and states which rule applies to a record that also carries `@SerialEntry` (I suggest: refuse `@SerialEntry` on a record once the record branch exists, and update the `SerialEntry` javadoc).

### M4. The digest does not cover the declared class of reference-typed components

- **Severity:** MEDIUM. **Status:** CONFIRMED.
- **Evidence (VERIFIED):** `SchemaGenerator.toWireType` L439 (`@AtomicSerial` class), L486-489 (registered replacement), L504-506 (interface or abstract) all return the bare token `"@AtomicSerial"`. Only arrays add the component name (L471-472).
- **Consequence:** retyping a component from `Address` to `Invoice` (both `@AtomicSerial`), or from one interface to another, keeps the identity. Draft 3.3 says retyping gives a new identity; for these types it does not. Draft 4.6 falls back on the per-value `valueSchemaDigest`, which is absent for a null value and names the runtime class the writer chose.
- **Smallest fix:** the record branch emits the declared class name for these components (as arrays already do). That is a token change in a standard being frozen, so it must go to the STD-006 owner. Otherwise the draft must state the limit plainly.

### M5. "Verified once" is structural only; payloads are neither typed nor checked canonical

- **Severity:** MEDIUM. **Status:** decoder gap CONFIRMED; the evasion is PLAUSIBLE.
- **Evidence (VERIFIED):** `EntryRepV2Codec.decode` L425-447 reads each payload as an opaque OCTET STRING: length and digest presence only. No check that the payload's type matches the field's declared `wireType`, and no check that it is the canonical encoding.
- **Consequences:** (a) `MarshalledEntry.get` and `as` must parse payloads later, contrary to section 4.4 "nothing is lazily parsed". (b) A writer can store a value whose slice is not the canonical bytes of its value (if any non-canonical form decodes; NOT CHECKED). Byte matching then never finds it with an exact-value template (for example a clean-up `take` by value), while class-free filters and typed readers still see the value. (c) A slice can hold an `Integer` under a `String` declaration; matching treats it as just bytes.
- **Smallest fix:** at the server on write, and in `MarshalledEntry` construction, decode every payload class-free under a fixed budget, check it against the declared type, re-encode, and compare bytes. If that cost is refused, the draft must say payloads are parsed lazily, each call under a budget.

### M6. `MarshalledEntry` can exist unverified, and "implements Entry" leaks it into old paths

- **Severity:** MEDIUM. **Status:** PLAUSIBLE.
- **Evidence:** `Entry extends Serializable` (VERIFIED, L77). Default Java deserialization of a final non-record class does not run its constructor (Java platform rule, not checked here). `EntryRep.ensureValidClass` L763-787 (VERIFIED) requires a public no-arg constructor for any non-`@SerialEntry` entry class, so passing a `MarshalledEntry` to the legacy write path is refused loudly if it has none (INFERRED); lookup attribute sets were not checked.
- **Scenario:** a `MarshalledEntry` arrives through Java serialization (any JOSS path still live) with arbitrary bytes; introspection then parses unverified octets.
- **Smallest fix:** make `MarshalledEntry` `@AtomicSerial` with a `GetArg` constructor that runs the full check, and make Java deserialization refuse it (`readObject` throws). Consider not implementing `Entry` at all: the new API can accept `Entry` or `MarshalledEntry` explicitly, and old APIs then cannot be handed one by accident.

### M7. The sender still chooses classes that load and run on the receiver inside `as()`

- **Severity:** MEDIUM. **Status:** CONFIRMED (load order and context), impact INFERRED.
- **Evidence (VERIFIED):** `EntryRepV2Codec.decodeFieldValue` decodes with `ResolutionContext.NONE` (L609); `ResolutionContext` L44-46 says NONE falls back to "the platform default (the RMI/context fallback)", acceptable only when "there is no federation boundary". An entry written by another principal is a federation boundary. An enum item carries its own class name; `DerObjectStreamCodec` L1724 loads it before `decodeFieldValue` checks the declared type (L612-613). For an `@AtomicSerial`, interface or abstract slot, the concrete class is named in the slice's schema chain and reconstructed (L621-626). Whether that path runs the `DeSerializationPermission("ATOMIC")` gate: NOT CHECKED.
- **Smallest fix:** `as(type)` resolves only through `type`'s own loader; an enum item's class name must equal the declared enum type before any load; refuse `java.lang.Class` components and `any` element types in record entries; state that nested polymorphic slots name sender-chosen classes and are gated like any other `@AtomicSerial` decode.

### M8. Lenient conversion can confuse types and silently downgrade data

- **Severity:** MEDIUM. **Status:** PLAUSIBLE.
- (a) Same name, different nested type: both sides are `"@AtomicSerial"` (M4), so "same name, different wire type is refused" does not fire.
- (b) Downgrade by round trip: a v2 entry adds `classification`. An old client converts with `ADDITIVE` to its v1 class, edits and writes back. The classification is gone; `dropped` reported it, but nothing stopped the write. New readers converting the v1 entry see `classification = null`.
- (c) Widening: covered in H4.
- (d) Today's `@SerialEntry` decode turns a failed field into `null` (`EntryRep` L958, VERIFIED). If lenient conversion reuses it, a decode error becomes a wildcard.
- **Smallest fix:** `ADDITIVE` refuses to drop any non-null field unless the caller names it; a converted object is never accepted as a template; field decode errors fail the conversion.

### M9. Events that carry entry contents become a read channel

- **Severity:** MEDIUM. **Status:** PLAUSIBLE.
- **Evidence:** today a `notify` event carries no entry; only availability events offer `getEntry()` (`AvailabilityEvent` L197, VERIFIED). `DESIGN-SpaceChannelAuthorization.md` section 5 requires a read check at registration and again at delivery (whether built: NOT CHECKED).
- **Questions the draft must answer:** the server delivers another writer's bytes to a `RemoteEventListener` that may be any third-party endpoint, under the server's authority; whose read right is checked at delivery (the registrant's, re-checked); entries written under a transaction must not appear in events to outsiders before commit.
- **Smallest fix:** keep `notify` payload-free; carry `MarshalledEntry` only in availability events, after a delivery-time read check made as the registrant.

### M10. Immutability is shallow; the "cache the wire form with the instance" claim is unsafe

- **Severity:** MEDIUM. **Status:** INFERRED (Java language rules; nothing built).
- **Scenario:** a record with a `byte[]`, array, `ArrayList` or mutable `@AtomicSerial` component can be changed after construction. Draft 3.7 caches the wire form with the instance, so a written entry can differ from the object that was validated, and two equal-content records with array components are not `equals` (records compare arrays by reference).
- **Smallest fix:** the specification says "shallowly immutable"; refuse array components other than `byte[]`; require collection components to be declared as `List`/`Set`/`Map` and copied in the constructor; drop the caching claim, or cache only after an encode that the codec itself performs and never reuse it across a mutation it cannot see.

### L1. Re-injecting another client's entry bytes

- **Severity:** LOW. **Status:** INFERRED. Possible today with typed entries; `MarshalledEntry` makes it exact and class-free. Harmless only if authority (writer provenance, capability offer terms) comes from the server's stamp and never from body bytes. `MarshalledEntry` equality on the full body makes a copy equal to the original. **Fix:** expose the server's writer stamp beside the entry, outside equality; say so in section 5.

### L2. Entry and value schemas share one digest namespace; spec and code disagree on empty field lists

- **Severity:** LOW. **Status:** CONFIRMED. The entry chain is built from `AtomicSerialSchemaRecord` (generator L189-209), the same record type as value schemas; no domain tag. STD-006 section 7.7.1 says `fields SIZE(1..maxFields)`; `AtomicSerialSchemaRecord` L44 allows 0, and records with no components would be invalid under the standard. **Fix:** align the standard with the code (guidance G8); decide whether a zero-component record entry is allowed.

### L3. `matches` does not check slice counts

- **Severity:** LOW. **Status:** PLAUSIBLE, not run. `EntryRep.matches` L1230-1236 indexes `theirs[f]` for every template field. Since `superclasses` is writer-chosen (H1), a short entry can claim to be a subtype of a long template's class and sit in its traversal; the loop could throw inside the scan. A quick-reject (`EntryHolder` L190) may prevent it: NOT CHECKED. **Fix:** return false on a shorter candidate.

### L4. A record written today through the reflective path silently stores nothing

- **Severity:** LOW (needs an added public no-arg constructor). **Status:** CONFIRMED by reading. Record fields are private final; `getFields()` returns public fields only; `isUsable` skips final. The entry is written with zero fields, and as a template it matches every entry of its class. **Fix:** the generator refuses records now, before the record branch exists.

### L5. Introspection cost per call

- **Severity:** LOW. **Status:** INFERRED. `fieldNames`, `fieldType`, `get` each need the entry chain (up to 64 records, 64 KiB) or a payload decode. **Fix:** decode the chain once at construction (it is already decoded to verify), keep the decoded view, and budget nested decodes in `get`.

## 4. Recommendations on the six operator statements (section 1 of the draft)

The board advises; the operator decides.

| # | Statement (short) | Recommendation | Reason |
|---|---|---|---|
| 1 | Entry fields final by default, immutable | ADOPT WITH A CHANGE: say "shallowly immutable" and restrict component types (no arrays except `byte[]`, collections copied in the constructor) | Final fields stop reassignment, not mutation of what they hold (M10). The promise must be one the codec can keep. |
| 2 | Use records; null fields are wildcards in lookup | ADOPT WITH A CHANGE: records yes; express wildcards in a `Query`, not as nulls inside a record instance | Null-as-wildcard inside the value type forces the null-accepting constructor (H4) and lets converted entries widen templates. Keeping templates out of the record lets the record validate. Reconcile with the record pattern already documented in `SerialEntry.java`. |
| 3 | No inheritance; less is more | ADOPT | Records cannot extend classes anyway. The specification must stop the chain at `java.lang.Record`, drop it from `superclasses`, and skip subtype traversal for record templates (M3, M1). |
| 4 | Identity more than the class name | ADOPT WITH A CHANGE: identity is the body-derived digest; state exactly what it covers, extend it to declared component classes, and settle whether two versions of one name may coexist in one space | The digest does cover the class name, component names, coarse types and order (section 5, C). It does not cover declared `@AtomicSerial`/interface component classes (M4). The server today refuses a second version per name (M2), and the routing copies of identity are unbound (H1). |
| 5 | Return a `MarshalledEntry` (introspectable DER) when no local class | ADOPT WITH A CHANGE: verified by an `@AtomicSerial` constructor, refused by Java deserialization, preferably not an `Entry`, and with payload checking at construction or honest lazy budgets | The type is the right answer to "no class, no download" (H2). The changes close M5 and M6. |
| 6 | Return all entries marshalled by default; client decodes what it wants | ADOPT for the new interface only, WITH A CHANGE: events stay payload-free except availability events after a delivery-time read check | Receiving bytes and choosing the class locally is what removes the sender-chosen download. Legacy interfaces must keep returning typed entries through the wrappers with local classes only. Events need the M9 rules. |

## 5. Identity claim (brief item C), answered

From `EntrySchemaGenerator` L148-209, `AtomicSerialSchemaRecord` header L41-44 and `SchemaGenerator.toWireType` L423-512 (VERIFIED):

| Input | In `entrySchemaDigest`? |
|---|---|
| Leaf class name | Yes (`className` of the leaf record) |
| Superclass chain | Yes, by recursive `parentSchemaHash`; absent when the parent is `Object` |
| Field names | Yes |
| Field types | Yes, as `wireType` tokens: boxed scalars, `String`, `Class`, `byte[]` exact; enum with class name; arrays with component (and class name for `@AtomicSerial` components); collections with discipline and element tokens; `@AtomicSerial` classes, interfaces and abstract classes collapse to `"@AtomicSerial"` with NO class name |
| Field order | Yes (order-significant list). Reflective: alphabetical within a class, so declaration order does NOT matter today. `@SerialEntry`: `entryForm()` order |
| Nullability, validation, code bytes, codebase | No |
| Whether the class is a record, or `@SerialEntry` | No marker. The same record gets different digests through the two encode paths (M3) |

How a chain with no superclass is encoded: one record with `parentSchemaHash` absent (tag inspection, `AtomicSerialSchemaRecord` L49-60). A record is not such a chain today: its parent is `java.lang.Record` (M3). The draft's claim that identity covers retyping is false for the collapsed reference types; the draft's claim about order needs a choice for records (open question 4).

## 6. Open questions (section 7), where I have a grounded view

| Q | View |
|---|---|
| 3 | Option B (see H4). I recommend the board ask the operator to revisit RL-JGDMS-0138. |
| 4 | Yes, order is part of identity, and for records it should be component declaration order, because that is the canonical constructor's order and matching is positional anyway. State that this differs from the reflective rule (alphabetical). |
| 5 | Scalars: the value. Enum: the pair (declaring class name, constant name) read from the `[7]` item without loading anything (the item carries both, `DerObjectStreamCodec` L80). `Class`: refuse as a record component. Arrays and collections: a list or map of class-free values under a size and depth budget. `@AtomicSerial`: the field map from `decodeToFieldMap`, under the same budget. Anything else: an opaque holder. |
| 6 | Prefer no wire mark (the standard is freezing). Use the reader-side rule in M8: lenient conversion refuses to drop a non-null field unless the caller names it. |
| 7 | No. Derive class name and superclass names from the verified body chain on the server; carry nothing that can disagree with the body (H1). |
| 8 | (Not asked; noted) the release-8 modules cannot name records; put the new interface in a release-21+ module. |
| 9 | No, not in this design. With the DER registrar the only registrar, its record could later be unified, but the two records were ratified distinct and this is new work. |
| 10 | Yes for `MarshalledEntry`: refuse Java deserialization (M6). For record entries, Java deserialization runs the canonical constructor, which under the current ruling accepts anything; refuse it there too unless H4 is fixed. |
| 11 | Not in the first version. A nested record that is not an entry must be `@AtomicSerial` to be encodable; whether a record can carry `@AtomicSerial` with a `GetArg` constructor I did not check. |

## 7. What I tried and could not break

- **Body decode bounds.** I looked for unmetered loops in `EntryRepV2Codec.decode`: table count (256), slice count (65535), slice payload (1 MiB), body (8 MiB), strict ascending table, orphan rejection, digest binding, field-count guard all present (L338-485). I found no recursion in the body decode itself; nested payloads are not decoded there (which is M5's point, not a DoS).
- **Slice context-freedom.** I could not make a slice's bytes depend on its enclosing entry: `encodeFieldSlice` is called per value and the table is keyed by content digest (appendix A.4.1, codec L141). So a record's slices match a legacy entry's slices for equal values, which is exactly why M1's rule is needed.
- **Filter admission digest.** `FilterAdmission` L310-320 takes the applicability digest from the template's decoded body, not from the unverified field. Admission is sound; the evaluation side is not (H1).
- **`decoded()` reuse.** `EntryRep.decoded()` and the chain cache are bound to the body by identity; I found no way to pair a chain with a foreign body.
- **Hostile nulls in matching.** A stored all-null entry only matches wildcard template fields; nulls cannot make a stored entry match more templates. The harm from nulls is in the reader's typed object (H4), not in matching.

## 8. Not checked

- Nothing was compiled or run; no adversarial input was executed (guidance G13). H1 to H4 rest on reading.
- Whether channel authorization (`DESIGN-SpaceChannelAuthorization.md`) is built, and so whether H1's first scenario is live today.
- Whether `MarshalledInstanceCodec.decodeMarshalledInstance` runs the ATOMIC gate for nested entry field values.
- Whether the DER object-stream reader accepts any non-canonical item that decodes to the same value (M5b).
- `CodebaseProvider.loadClass` integrity behaviour; `SnapshotRep`; `MatchSetData`; `OutriggerAvailabilityEvent`; `SpaceProxy2`.
- The exact method count of `OutriggerServer`; the ninth `byte[] filter` hit in `FilteredTupleSpace`.
- The register text of RL-JGDMS-0133 to 0140 and of the design memo section 12 and 13 rulings.
- Lookup (registrar) attribute paths for a `MarshalledEntry` passed as an attribute.
- The B3 memo beyond what `FilterSet` and `FilterEval` show.
