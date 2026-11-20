# Review of the entry-records / MarshalledEntry / space-API design (seat 2: compatibility and feasibility)

## 1. Header

- Reviewer: Claude Sonnet 5.5 (model id claude-sonnet-5-5), seat 2 of a two-seat blind board. I did not read the other seat's review.
- Date: 2026-11-21.
- Repository commit (git rev-parse HEAD): 83c4d4c7af7bcb14bd33e07a68bbaa4ee83f36d3.
- Design reviewed: docs/DESIGN-Entry-Records-MarshalledEntry-Space-API-2026-11-20.md (DRAFT rev 1 plus section 10).
- Read: docs/JGDMS-Board-Reviewer-Guidance.md (Part 0 and Part I, first 150 lines; the rest NOT CHECKED); the design draft (all); docs/JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md (all); STD-006 v0.13 section 7.7.1 and 7.7.2 (lines 2282-2412); docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md (only decision D14 and its neighbours, by grep).
- Code opened: net/jini/core/entry (Entry, SerialEntry, GetEntryArg); EntryV2Codec; EntrySchemaGenerator (all); EntryRepV2Codec (encode and field-count guard parts); DerEntryV2Codec; Outrigger EntryRep (constructor, findHash, ensureValidClass, entry, entryViaSerialEntry, isAtLeastA, usableField); OutriggerGetEntryArgImpl; OutriggerServerImpl.typeCheck; LookupAttributes (matches, update, getFields); the lookup entry beans; SnapshotRep; the poms listed in section 4.3.
- Method for counts: a read-only Python scan (a regex-based Java declaration parser, run from standard input; no file was written on the device other than this one) over every `.java` file under jgdms-platform/src, jgdms-lib/src, jgdms-lib-dl/src, jgdms-der/src, jgdms-cel/src, jgdms-jeri/src, services, examples, jgdms-benchmarks, jgdms-discovery-providers and ../qa/src, skipping `target`. It finds every type whose extends/implements clause names `Entry` (not `Map.Entry`), `AbstractEntry` or `CloneableEntry`, then closes over subclasses by simple name. It is BEST EFFORT: it matches by simple name (name clashes produce false positives, which I list), and it does not resolve imports. Where I corrected a count by hand I say so.
- Operator updates received during the review, both applied: (1) the new client interface goes in a locally installed module, not a -dl jar; (2) no live interoperation with older releases, nothing for download, no module needs to stay at release 8, so source compatibility and migration are what matter.

## 2. Verdict

1. Record entries are feasible, and are less new than the draft says: the existing `@SerialEntry` path already produces the exact schema the draft proposes (one record, no superclass, declared order). VERIFIED (EntryRepV2Codec.encodeSerialEntry).
2. Nothing implements `Entry` as a record anywhere in the tree today (0 hits in main, tests, examples, qa). So record support is untested. VERIFIED by the scan.
3. The draft's central compatibility claim (finding 7: "digest identifies, class names route, slices match") is incomplete. Outrigger already refuses a second hash for the same class name (typeCheck). The proposed strict-digest rule changes a loud failure into a silent non-match, and that is the surprise for developers in scenarios (i) and (ii).
4. Every reflective path that looks at `getFields()` (LookupAttributes, Outrigger EntryRep, Reggie EntryRep, the generator) sees a record as an entry with zero fields. Some refuse it; LookupAttributes.matches would treat any two records of one class as equal. A record entry needs an explicit gate in all of them first.
5. `MarshalledEntry` as "body only" cannot carry legacy routing data (class name, hash, superclass names live outside the body). Fine for record entries, not for legacy entries.
6. The "absent field -> default" behaviour of GetEntryArg is not reachable on the DER path today (hash gate, positional decode), and the same code turns decode failures into null silently.
7. Release 16 is not a floor: jgdms-platform is release 21 (class files major 65, VERIFIED) and the DER codec is release 25.
8. Standard lookup attributes (12 classes) and their beans (23 field assignments) must stay legacy.
9. Recommendation: ADOPT WITH A CHANGE for statements 1, 2, 4, 5, 6; ADOPT (scoped to record entries) for 3. Details in section 9.

## 3. Survey (part A)

All counts: classes found by the scan described in section 1. "repo main" = main source trees of the repository (platform, lib, lib-dl, der, cel, jeri, services, discovery providers, benchmarks) without examples and tests. "repo tests" = `src/test` trees. qa = ../qa/src (1952 java files).

### A.1 Entry classes

| Group | Classes that implement Entry (direct, AbstractEntry, or subclass) | `@SerialEntry` | Records | Notes |
|---|---|---|---|---|
| repo main | 15 (after removing 9 scan false positives, see below) | 10 | 0 | 12 in jgdms-lib-dl net/jini/lookup/entry (Address, Comment, Host, Location, Name, ServiceInfo, ServiceType (abstract), ServiceTypes, Status (abstract), UIDescriptor, JMXProperty, JMXProtocolType); BasicServiceType (extends ServiceType); FiddlerStatus (extends Status); SnapshotRep (outrigger-dl, an opaque holder, not a user entry) |
| examples | 3 | 0 | 0 | demo2 SensorReadingEntry; demo7 WeatherReading, NorthStationReading |
| repo tests | 50 (noisy, includes name clashes such as `Base`) | 11 | 0 | |
| qa | 54 | 0 | 0 | 22 files declare one directly; the rest are subclasses |

Scan false positives removed by hand from "repo main": MapSerializer.Ent and AbstractIDLeaseMap.LeaseEntry (java.util.Map.Entry), and 7 benchmark state classes in jgdms-benchmarks/cel/.../TrigFunctionBenchmark.java (name clash with a test class `Base`). Command to repeat: the plain grep `grep -rlE '(implements|extends)[^{;]*[ ,]((net\.jini\.core\.entry\.)?Entry|(net\.jini\.entry\.)?AbstractEntry)\b'` per tree gives the direct implementers (repo main 6+15+24+2+2 files before filtering; qa 22 files).

Which 10 are `@SerialEntry`: VERIFIED by `grep -rn '@SerialEntry'`: Address, Comment, Host, Location, Name, ServiceInfo, ServiceTypes, UIDescriptor, JMXProperty, JMXProtocolType. The two abstract bases (ServiceType, Status) are not.

### A.2 Inheritance and superclass templates

| Group | Entry classes that extend another entry class | Of which extend a concrete class |
|---|---|---|
| repo main | 2 (BasicServiceType -> abstract ServiceType; FiddlerStatus -> abstract Status) | 0 |
| examples | 1 (NorthStationReading -> WeatherReading, demo7) | 1 |
| repo tests | up to 7 (Sub/Base, Derived/Base, DetailedReading/Reading, TestStatus -> Status; one hit may be a name clash) | most |
| qa | 29 classes in six families | 29 |

The qa families: matching/ (UniqueEntry -> StringMatch, IntegerMatch, UserDefMatch, RemoteMatch -> ChildOf..., GrandChildOf...; NBEUniqueEntry -> NBEComplex, NBEEmpty), javaspace05/ (A0 -> A00, A01 -> A000, A001, A010, A011; B0 -> B00, B01; C0), spec/lookupservice/attribute (Attr00 -> Attr01; Attr02 -> Attr03; Attr04 -> Attr05; Attr09 -> Attr10 -> Attr11 -> Attr12), spec/joinmanager (TestServiceIntAttr -> two subclasses), share (AttrOne -> SunOfAttrOne), impl/outrigger/api AbstractEntryTest (NameAE, NameE and their Friendly subclasses).

Places that match with a superclass template to get subclass entries. Best effort, by reading the matching code, not by grep alone:
- Production: Outrigger `EntryRep.isAtLeastA` (EntryRep.java:1252-1264, VERIFIED: matches the template's class name against the entry's own class name and its superclass-name array) called at OutriggerServerImpl.java:2731 and :3178 (VERIFIED lines). Lookup attributes: `LookupAttributes.isAssignableFrom` (LookupAttributes.java:502-512, VERIFIED) lets a template match a subclass entry (also by class name). So superclass templates are a live, built-in feature of both the space and the lookup service.
- Callers in repo main: I found no main-source caller that deliberately queries with an abstract base (the abstract `Status` and `ServiceType` bases exist for exactly that purpose; whether any client code uses them as templates: NOT CHECKED).
- qa, files that mention a class from a hierarchy (grep -rlE with the class names): matching/ 21 files, javaspace05 12 files, lookupservice attribute classes 22 files, AttrOne/AttrTwo 4 files. These overlap; they are the superclass-template tests.

### A.3 Public final fields (silently skipped today) and non-public fields

| Group | Classes with a public final instance field | Classes with a non-public instance field |
|---|---|---|
| repo main | 0 | 2 (BasicServiceType: bundle, inited; SnapshotRep: rep) |
| examples | 0 | 0 |
| repo tests | 11 (10 `@SerialEntry` fixtures LocationEntry, LocationEntryRenamed, ServiceEntry, ..., plus Fiddler TestAttr; AbstractEntrySerialEntryTest.PlainEntry has `finalIgnored` on purpose) | 0 |
| qa | 0 | 1 (GrandChildOfStringMatch.ANOTHERFIELD) |

So no production entry class relies on the "final field is skipped" rule. The only users of final fields are `@SerialEntry` test fixtures. The 10 `@SerialEntry` lookup entries in lib-dl keep public NON-final fields (the scan lists 12 with usable fields in repo main). VERIFIED by the scan.

### A.4 Mutators and later assignment

| Item | Count | Where |
|---|---|---|
| Entry classes with an instance method that assigns an own wire field | repo main 0; examples 0; repo tests 0; qa 13 | qa: A0, B0, C0 (setUuid, setColor); Attr03 to Attr12 (setDefaults, 10 classes). BasicServiceType.init assigns non-wire fields only |
| Field assignments by other code: the entry beans | 23 assignments in 8 bean classes | VERIFIED by `grep -nE 'assoc\.[a-zA-Z]+ *=[^=]' *Bean.java` in net/jini/lookup/entry: AddressBean 7, ServiceInfoBean 6, UIDescriptorBean 4, LocationBean 3, CommentBean 1, NameBean 1, StatusBean 1 (=23; AddressBean 7+ServiceInfoBean 6+UIDescriptorBean 4+LocationBean 3+Comment 1+Name 1+Status 1 = 23) |
| Field.set on entries by library code | 1 reflective loop | LookupAttributes.update, LookupAttributes.java:389-412 (legacy path: `getDeclaredConstructor().newInstance()` then `Field.set`) |
| Entry fields assigned after construction in qa test bodies (`tmpl.field = x`) | NOT COUNTED | the idiom is pervasive for templates; I could not count it reliably |

### A.5 Field types (usable fields = public, non-static, non-final, non-transient)

| Type | repo main (12 classes) | examples | repo tests | qa |
|---|---|---|---|---|
| String | 25 | 3 | 36 | 38 |
| Boxed Integer | 0 | 2 | 9 | 28 |
| Boxed Long | 0 | 0 | 5 | 16 |
| Boxed Boolean | 0 | 0 | 5 | 19 |
| Boxed Double / Float | 0 | 1 / 0 | 2 / 0 | 0 / 2 |
| Arrays | 0 | 0 | 0 | 2 |
| Collection or map | 1 (UIDescriptor.attributes, a Set) | 0 | 0 | 2 |
| `Class` | 1 | 0 | 0 | 0 |
| Other object types | 2 (Status.severity: StatusType; UIDescriptor.factory: MarshalledObject) | 0 | 5 (PlainValue, Stamp, Point, HasColor, Payload) | 6 (Uuid x3, ARemoteInterface, RandomUserDefinedClass, SlowAttrField) |

Reading: strings and boxed numbers dominate. Three of the 12 standard attribute classes use a type outside the draft's component table or a non-scalar (MarshalledObject, StatusType, Set).

### A.6 `JavaSpace.snapshot` and casts of read/take

- `snapshot`: declared at jgdms-lib-dl .../net/jini/space/JavaSpace.java:286 and implemented at outrigger-dl .../SpaceProxy2.java:604 (VERIFIED, `grep -rnE '\bsnapshot\(' ...`). No other main-source caller in lib-dl, outrigger, examples or platform. (Other services' `snapshot(` hits are reliable-log snapshots, unrelated.) qa: 42 files (41 under spec/javaspace/conformance/snapshot, 1 under impl/outrigger/matching).
- `SnapshotRep` is the existing precedent for `MarshalledEntry`: an `@AtomicSerial` class that `implements Entry` and wraps an `EntryRep` (SnapshotRep.java:83 onward, VERIFIED). Its javadoc says a snapshot works only inside one VM; `MarshalledEntry` is meant to be portable, so it is a stronger contract.
- Casts: regex `\(\s*[A-Z][A-Za-z0-9_.]*\s*\)\s*\(?[A-Za-z_.()]*\.(read|take|readIfExists|takeIfExists)\(`. repo main (services/outrigger, examples, jgdms-lib-dl/src): 0 files. qa: spec/javaspace 152 files, 66 of them call a space read/take, 57 of those match the cast regex; impl/outrigger 163 files, 19 call, 1 matches. (The demo7 example assigns results to `Entry` without a cast.) JavaSpace users in repo main: jgdms-lib-dl main 10 files (the interface types), services 20, examples 4.

### A.7 Source-compatibility summary (the question the operator now cares about)

| Thing that cannot move by configuration | repo main | examples | qa |
|---|---|---|---|
| Downloaded entry classes (the space loads the entry class by codebase, EntryRep.entry() calls CodebaseProvider.loadClass, EntryRep.java:849, VERIFIED) | standard attributes in lib-dl are the downloadable ones; no application entry classes | demo2 shows the class-free case; others use local classes | entries are loaded locally in QA; how many depend on download: NOT CHECKED |
| Entry inheritance with superclass templates | 0 concrete families; 2 abstract-base families | 1 pair | 29 classes, about 55 test files |
| Mutation after construction | beans: 23 assignments in 8 classes; LookupAttributes.update | 0 | 13 classes |
| Cast of read/take result | 0 files | 0 | 57 + 1 files |
| `snapshot` callers | 0 | 0 | 42 files |
| Smart-proxy-specific types (imports of `org.apache.river.*.proxy.`) | NOT CHECKED (my grep timed out on the repo tree) | 2 files in examples (one imports EntryRep) | 3 files (share/LookupServices.java and 2 under spec/lookupservice) |

Reading: production code in the repository does not depend on inheritance, casts or snapshot. The cost sits in qa (about 200 files that mention entries, 210 files import `net.jini.core.entry.Entry` or its package) and in the lookup entries and beans. If qa is the test of "existing application code", the legacy interface with its cast-friendly eager decode has to stay, which the draft already says (5.2).

## 4. Feasibility of records (part B)

### B.1 `Entry extends java.io.Serializable`

- Entry is `public interface Entry extends java.io.Serializable` with no members. VERIFIED (Entry.java).
- JDK specification (INFERRED from the Java Object Serialization Specification for records, not checked in this session): a record that implements Serializable is serialized by its components only; writeObject, readObject, serialPersistentFields and serialVersionUID matching are ignored; deserialization calls the canonical constructor. So a record entry is Serializable for free.
- Is Java serialization of entries refused? No. In AtomicMarshalInputStream, a plain Serializable class that is an Entry is let through under a permission: `classDesc.deSerializationPermitted(ENTRY)` where `ENTRY = new DeSerializationPermission("ENTRY")` (AtomicMarshalInputStream.java:211 and :2777-2778, VERIFIED). So it is permission-gated, not refused. Whether DER refuses Java-serialised entries: NOT CHECKED. Draft question 10 (marker interface) therefore has a code answer: nothing refuses it today; a new marker would not change that. Smallest fix: a test that a record entry cannot be smuggled through the JOSS gate without the ENTRY permission.

### B.2 Reflection on records, modules, class loaders

| Point | Requirement | Label |
|---|---|---|
| `Class.isRecord`, `getRecordComponents`, `RecordComponent.getAccessor` | Java 16 API. Accessors are public methods. | INFERRED (JDK spec) |
| Canonical constructor | An implicit canonical constructor has the same access as the record class; so a public record has a public canonical constructor. An explicit one must be at least as accessible. `getConstructor(types)` finds it. | INFERRED (JLS 8.10.4) |
| Record fields | private final. `getFields()` returns none; `Field.set` on them fails even after setAccessible. | INFERRED (JDK) |
| Under a SecurityManager | `getRecordComponents` follows the declared-member access checks (accessDeclaredMembers or same-loader rule). JGDMS runs with security managers, so the codec needs that permission or must use public API only. | INFERRED; NOT CHECKED |
| JPMS | There is no `module-info.java` anywhere in the repository (`find . -name module-info.java -not -path '*/target/*'` returned nothing). Packaging is OSGi/bnd (jgdms-platform has bnd-maven-plugin). So everything runs on the class path, in the unnamed module: no exports or opens are needed. A user who puts a record in a named module must `exports` the package (public accessors and constructor are enough; `opens` is not needed). | VERIFIED (find) / INFERRED (JPMS rules) |
| Class loaders | The record class is resolved by the caller-named loader (the draft's `as(type)`), so a record has one `Class` per loader and strict identity compares digests, not classes. Two loaders with different versions of one record are the draft's hazard 3 and are real. | INFERRED |

Critical code fact (see finding 2): no record-aware code exists (VERIFIED: grep for `isRecord|getRecordComponents|RecordComponent` in jgdms-der, jgdms-platform, jgdms-cel, outrigger finds nothing). Today a record is treated as a class with no public fields.

### B.3 Release levels and where the new interface sits

Compiler release per module (`<release>` in the pom, VERIFIED unless marked):

| Module | Release | Anchor |
|---|---|---|
| parent pom (default for all modules) | 8 | pom.xml:397 |
| jgdms-platform | 21 | jgdms-platform/pom.xml:127 |
| jgdms-lib | 21 | jgdms-lib/pom.xml:84 |
| jgdms-lib-dl | 8 | jgdms-lib-dl/pom.xml:99 |
| jgdms-der | 25 | jgdms-der/pom.xml:72 |
| jgdms-cel | 25 | jgdms-cel/pom.xml:84 |
| jgdms-jeri | 21 | jgdms-jeri/pom.xml:123 |
| outrigger-dl | 8 | outrigger-dl/pom.xml:98 (jgdms-der is test scope only, :55-70) |
| outrigger-service | 21 | outrigger-service/pom.xml:126 |
| outrigger-cel-authoring | 25 | outrigger-cel-authoring/pom.xml:111 |
| outrigger-snaplogstore | no setting found, so the parent's 8 | INFERRED |
| mahalo-dl | 8 | mahalo-dl/pom.xml:56 |
| mahalo-service | 21 | mahalo-service/pom.xml:61 |

Class-file level of what is built: jgdms-platform `Entry.class` and `EntryV2Codec.class` are major version 65 (Java 21); jgdms-der `EntrySchemaGenerator.class` is major 69 (Java 25). VERIFIED by `od -j6 -N2` on jgdms-platform/target/classes and jgdms-der/target/classes. jgdms-lib-dl has no built classes in this working tree (NOT CHECKED).

Who depends on jgdms-lib-dl (from poms; `grep -rl --include=pom.xml '<artifactId>jgdms-lib-dl</artifactId>'`): 36 poms, among them jgdms-lib, jgdms-jeri, jgdms-activation, jgdms-activation-parameters, jgdms-collections, jgdms-discovery-providers, jgdms-iiop, jgdms-rmi-tls, jgdms-ui-factory, jgdms-url-integrity, jini-2.1-compat, extra, browser, compat-gate, velocity-config, the phoenix modules, the service archetype, and the -dl and -service modules of reggie, outrigger, mahalo, mercury, fiddler, norm, policy-service, verdict-registry, hello-world, codebase-downloader and jgdms-service-support.

Answers to the operator's items:

(a) Existing locally installed client library. `jgdms-lib` (release 21) is the nearest: its pom describes it as "JGDMS utility APIs that are not tied to a specific service implementation", it has no -dl suffix, and it holds the resident implementations of JoinManager, ServiceDiscoveryManager, LookupCache and lease renewal (`au/net/zeus/jgdms/discovery/*Impl.java`, `.../lease/*Impl.java`, VERIFIED by listing). That it is installed on the client rather than downloaded is INFERRED from its name and use (service modules depend on it). jgdms-platform (21) and jgdms-jeri (21) are also non-dl.
(b) A new locally installed module needs: `Transaction`, `Lease`, `EventRegistration`, `RemoteEventListener` (all in jgdms-platform: net/jini/core/...), `MarshalledInstance` (platform, net/jini/io), `Entry` and `MarshalledEntry` (platform, net/jini/core/entry), and the codec through the `EntryV2Codec` seam (platform) with the provider in jgdms-der. The `net.jini.space` legacy types (JavaSpace and so on) live in jgdms-lib-dl, but the new interface does not need them. Consequences: jgdms-platform is release 21, so any client that loads it needs Java 21 or newer; the DER codec (`DerEntryV2Codec`, found by ServiceLoader) is release 25 and the SPI javadoc says a v2-born space "requires a DER-capable JVM" (EntryV2Codec.java:35, VERIFIED), so a client that actually encodes or decodes entries needs Java 25. Release 16 is therefore a legal compile level for the new interface but not a meaningful floor: nothing a client can do with it runs below 21, and encoding runs on 25. Recommendation: put the interface, `MarshalledEntry`, `Converted`, `Compatibility` and the wrapper in a new module (suggested name jgdms-space-client; the name is illustrative), release 21, depending on jgdms-platform only plus jgdms-der at runtime; compile it at 25 only if it links jgdms-der types directly (the `EntryV2Codec` seam makes that unnecessary). Put `MarshalledEntry` itself in jgdms-platform (as the draft proposes) so lookup and space can share it; the draft's "platform (release 21)" fits.
(c) What a Java 8 client that only downloads -dl jars can use: with these artefacts, nothing useful. The -dl jars compile at release 8 but reference platform types (Entry, Lease, ...) whose class files are major 65. A Java 8 JVM cannot even load `net.jini.core.entry.Entry` from the built platform. VERIFIED for Entry.class; the claim for -dl behaviour is INFERRED. The operator's second update makes this moot.
(d) D14 (docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md, table row D14, VERIFIED by grep) puts the registrar wrapper in jgdms-lib-dl because lib-dl depends on platform and jeri. The same reasoning applies in reverse: the wrapper uses records and DER, which need release 21 or higher, and the operator now says dl means download and the wrapper must be installed locally. So the wrapper would move to the same new local module (or jgdms-lib). The D14 "Preferred: false" analysis (types shared across proxy and client) still matters for `RemoteRegistrar` and the record types, but it concerns the interface jar, which is a different artefact from the local wrapper. The registrar reaches clients through lib-dl today (jgdms-discovery-providers depends on lib-dl).

Recommendation for B.3: do NOT raise jgdms-lib-dl; put new client-side code in a new local module at release 21 (or in jgdms-lib). Since the operator no longer needs release 8 for old clients, raising lib-dl is allowed but would still make a -dl jar mean "installed locally" and re-open the naming question; a separate module avoids that.

### B.4 `EntrySchemaGenerator` and what a record branch must do

What the code does today (EntrySchemaGenerator.java, VERIFIED):
- `generate` rejects `@SerialEntry` classes (lines 153-157) with "reflective schema generation is not valid for it (unit 2)". The @SerialEntry schema is built elsewhere, in `EntryRepV2Codec.encodeSerialEntry` (lines ~299-316), not by the generator. So appendix A.5's claim "single reflective view used by record-building and filter authoring" is not true for @SerialEntry today.
- Reflective chain: one `AtomicSerialSchemaRecord(className, null, fields)` per class from the leaf up to but excluding `Object` (line 162 loop). For a record the superclass is `java.lang.Record`, so the loop would add a second record named `java.lang.Record` with no fields. A record branch must stop the chain at the record (a chain of length one, as the draft says). The loop is the "superclass slot" assumption.
- Field selection uses `entryClass.getFields()` (line 217) then keeps public, non-static, non-transient, non-final fields (isUsable, lines 234-249); record fields are private final, so a record yields zero fields and an empty schema. Order is the FieldComparator, alphabetical inside a class (lines 252-263).
- Wire type: `SchemaGenerator.toWireType(f.getGenericType(), c)` (line 205). Wire name: `f.getName()`.
- Digest: `SchemaChain.linkAndGetLeafDigest(leafFirst)`; `entrySchemaDigest` = leaf record digest (SHA-256 of the DER of the leaf record). The record contains className, optional parent hash, and the ordered field defs. So className, names, types and order are all inside the digest, as the draft claims. STD-006 section 7.7.1 says the same for EntrySchemaRecord (lines 2282-2300).

What a record branch must do:
1. Detect `entryClass.isRecord()` before the `@SerialEntry` check.
2. Build one record: `className`, no parent, one field def per component in declared order (`getRecordComponents()` order, NOT alphabetical), wire name = component name, wire type from the generic type of the component (`RecordComponent.getGenericType()`; for the accessor type use the component type).
3. Return a positional field list that is accessors, not `Field` objects: `EntrySchema.orderedFields` is a `List<Field>` (line 100-102) and encodeReflective calls `fields.get(i).get(entryInstance)` (EntryRepV2Codec.java ~273-276). That needs a new representation (accessor `Method` or a functional list).
4. Refuse (not skip) a primitive component, a non-public record, a record without a public canonical constructor, nested record types not in the type table.
5. Keep EntrySchemaView in step: it calls `forClass` and rejects @SerialEntry (EntrySchemaView.java:70-81, VERIFIED), so filter authoring for @SerialEntry does not exist today; the draft's 3.5 statement that records get it "from the same change" holds only if the generator gets the branch.
6. Check STD-006 7.7.1: `fields SEQUENCE (SIZE(1..maxFields))` (line ~2298) forbids a zero-field record; Appendix A.1 allows 0..maxFields (and the code permits an empty record). Pick one.

### B.5 Is a record entry more than `@SerialEntry` with the boilerplate derived?

Short answer: in the wire and storage, no. VERIFIED facts:
- `@SerialEntry` already has final-field support, a constructor-based decode, a declared field order and an explicit wire schema (SerialEntry.java; AbstractEntry.java:236-267, the draft's finding 3).
- SerialEntry.java:104-135 already contains a record example (`@SerialEntry public record LocationRecord(String host, Integer floor) implements Entry`). So the design intent of "records as entries" is already documented, and the example calls `Objects.requireNonNull(arg.get("host"...))` in the constructor, which contradicts the wildcard rule (draft question 3). No test uses a record (0 records in the scan).
- The DER encoding of @SerialEntry is a single record with no superclass link, in declared order (EntryRepV2Codec.encodeSerialEntry; its javadoc even says superclass linking is "a later refinement"). That is exactly the draft's section 3.3.
- `GetEntryArg` is implemented in outrigger-dl as OutriggerGetEntryArgImpl (and in reggie-dl as GetEntryArgImpl). Its `get` returns the default for a name not in `presentNames` (OutriggerGetEntryArgImpl.java:58-60).

Can the "absent field -> default" be delivered on the DER path today? No, for two reasons, both VERIFIED:
- Gate: EntryRep.entry() compares the local class hash with the stored hash first and throws UnusableEntryException on a difference (EntryRep.java:850). A changed class never reaches the default logic.
- Positional decode: `entryViaSerialEntry` pairs local `entryForm()` entry `i` with stored slice `i` (EntryRep.java:953-960) and builds the GetEntryArg from the local names (OutriggerGetEntryArgImpl.java:41-50). "Present" means "among the first N local names". If the gate were relaxed, a reordered or renamed field would be silently assigned to the wrong name. Lenient decode must therefore be by name using the stored schema chain, as the draft says (4.4).
- The v2 decoder's field-count guard (EntryRepV2Codec ~466-478) compares the slice count to the entry's OWN on-wire chain, not to the local class; so it is not what refuses a version mismatch.

What a thin layer over `@SerialEntry` would give and not give:

| Carries over | Does not carry over |
|---|---|
| Wire schema (one record, declared order, names and types) | Decoupling of Java names from wire names (record components are the names; a rename changes identity) |
| Constructor-based decode and final fields | `@SerialEntry` still allows a superclass (the hash includes it); records forbid it (intended) |
| Outrigger index and match code on slices | Default for absent fields (not reachable, see above) |
| `LookupAttributes.updateSerialEntry` merge by name (lines 423-445) | A shared record/template type with null components needs a null-accepting constructor |

So I recommend implementing record entries as a derivation (entryForm, serialize and the `(GetEntryArg)` constructor derived from the record components) in one place, in jgdms-der behind the `EntryV2Codec` seam, rather than a second encode/decode path. That is the smallest change and it keeps one schema source. The cost: the derivation has to be repeated in four reflective copies (Outrigger EntryRep, Reggie EntryRep and ClassMapper, LookupAttributes, the generator), all of which currently use `getFields()`.

## 5. Lookup attributes (part C)

What the lookup path does (VERIFIED):
- Templates: `LookupAttributes.matches(tmpl, e)` requires `isAssignableFrom(tmpl.getClass(), e.getClass())` (by name, so subclass entries match, lines 261-262, 502-512) then compares every non-null public field (lines 264-270). Null template fields are wildcards.
- `modifyAttributes`: `LookupAttributes.modify` and `update` (lines 143-200, 389-412): for a legacy class, `newInstance()` of the entry and `Field.set` of each non-null modification field; null modification fields mean "no change". For a `@SerialEntry` class, `updateSerialEntry` (lines 423-445) serializes both, merges (non-null values in `mods` override) and rebuilds through the `(GetEntryArg)` constructor.
- Entry beans: `EntryBean.makeLink(Entry)` casts to the concrete class (for example `assoc = (Name) e`, NameBean.java) and 23 setters assign public fields (A.4).
- Reggie is not carried forward (operator update). The service-side modify code in Reggie therefore does not need to handle records; the client-side `LookupAttributes` and `JoinManager` code that builds modifications does, and the DER registrar's own handling of entries is NOT CHECKED.

Would record entries work as lookup attributes?
- As templates and attribute sets: only if every one of `matches`, `equal`, `check`, `update` gets a record branch. Today `getFields()` of a record is empty, so `matches(tmpl, e)` for two records of one class is vacuously true (any record template matches any record of that class): a silent wildcard-all. `update` falls into the legacy branch, `getDeclaredConstructor().newInstance()` fails (no no-arg constructor) and ends as an IllegalArgumentException. VERIFIED by reading; NOT run.
- `modifyAttributes` semantics (null = no change) need a null-accepting canonical constructor; with option B of draft question 3 (templates are never record instances) the lookup API would need the `Query`-like type too.
- What must stay legacy: the 12 standard attribute classes and their beans (public mutable fields assigned 23 times by beans; `ServiceControlled` marker for Status/ServiceInfo; UIDescriptor with a MarshalledObject and a Set; Status with a StatusType). They are `@SerialEntry` already, with public non-final fields, and the beans and user code write those fields. Converting them to records breaks source compatibility (`name.name = x`, `makeLink`).
- Abstract bases: `Status` and `ServiceType` exist to be matched by superclass. Records cannot do that. So "find any Status" has no record equivalent except the null-template-plus-filter route of draft 3.6.

Is one public `MarshalledEntry` over both realistic? Partly. The sidecar `EntryRecord` is scalar-only, has `hashAlgorithm` (1 DER or 2 legacy), a 32-byte `schemaHash` and a flat value list (STD-006 7.7.2, lines 2384-2412, VERIFIED). The Outrigger `EntryRepV2Body` has a version tag 2, a schema table and per-field slices carrying value digests (Appendix A.1). They are ratified as distinct and never compared (A.0). One public Java type can hide both only with two internal forms and a discriminator, and the lookup service matches by the hash-algorithm tag within one algorithm. I would not make lookup a first-version target; keep `MarshalledEntry` Outrigger-body-only (version 2) in 1.0 and let lookup attributes stay legacy entries.

## 6. STD-006 (part D)

Which sections a record branch would add to or change (documents in docs: JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md, JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md; the others, Appendix-C, Implementation-Tasks and comparison, need no normative change; Implementation-Tasks would need a new task line):

| Section | Change |
|---|---|
| Appendix A.0 | State that a record entry uses the Outrigger record and is not carried in the sidecar record |
| A.3 (`EntrySchemaRecord` chain, positional alignment) | A record entry has a chain of length one (no superclass record; `java.lang.Record` is not a chain member). Positional order is declared component order, not FieldComparator |
| A.5 (field rules) | New branch: record components, declared order, refuse primitive components, wire type table unchanged; the legacy rules (usable field, alphabetical order) stay for non-record classes |
| A.5 type table | Decide whether a record-typed component is allowed (draft question 11) |
| A.9 item 10 and A.8 field-count guard | Unchanged in rule; add that the count is the component count |
| A.10 (determinism) | Add: record schema is a function of className, component names, declared types and order |
| Main spec 7.7.1 | `fields SIZE(1..maxFields)` versus A.1's `0..maxFields`; `superclassHash` absent rule (superclass `Object`) must say that `java.lang.Record` also counts as no superclass; wire-name rule for records |
| 7.7.2 and 7.7.5 (sidecar) | No change unless lookup is brought in (not recommended) |
| 4.5 ceilings registry and the open-items list | Add the record rules if new ceilings are named |

Would any change alter bytes that existing legacy entries produce? No, provided the record branch is gated by `Class.isRecord()` and the generator order of checks keeps non-record classes on the existing path. A legacy class cannot be a record, so its schema chain, digest and slices are untouched. Caveats: (1) if `EntryRep` gains a field (for example a "kind" flag) its serial form changes and `serial-schema.golden` must be updated as a reviewed diff (A.8 last bullet); (2) a new wire interface adds new serial forms; (3) one narrow overlap (INFERRED): a legacy entry class with no superclass and alphabetically ordered public fields produces the same leaf schema record as a record with the same name and the same component names, types and order, because both are `AtomicSerialSchemaRecord(className, null, defs)` (EntrySchemaGenerator.recordFor, EntryRepV2Codec.encodeSerialEntry). That is an accident the spec should either name (a feature for migration) or forbid.

## 7. Scenarios (part E)

Setup: record `R(a, b)` is version 1. Version 2 is `R(a, b, c)` (c added at the end). Same class name. Facts used: digest covers names, types, order (draft 3.3); strict digest match in the space (draft 3.6); lenient conversion is explicit and by name (draft 4.4); missing component becomes null.

### (i) Writer has version 2, reader has version 1

| Party | What it sees |
|---|---|
| Writer | write succeeds; the stored identity is digest2 |
| Reader with typed template `new R_v1(x, null)` | The template has identity digest1. Under the strict rule it never matches a digest2 entry. The read returns nothing (or times out). The reader cannot tell "no such entry" from "entry of another version". |
| Reader with a null template plus a filter on names | Gets a `MarshalledEntry` with digest2. `as(R_v1.class)` strict fails. `as(R_v1.class, ADDITIVE)` drops `c` and returns `R(a,b)` with `dropped=[c]`. |
| Reader that takes the converted record and writes it back | `c` is lost unless it used `MarshalledEntry.with(...)` on the original. |
| What today's code does instead | A running Outrigger throws `UnmarshalException("Class mismatch: <name>")` the first time a second hash appears for one class name (OutriggerServerImpl.java:1527-1540, typeHashes at :264, typeCheck called from the write and template paths at :1608, :1680, :1944, :2370-2414; VERIFIED). So today the second version is rejected loudly, and which version wins depends on who arrives first in that server run. |

Surprising for a developer: the silent empty read; an old reader that is "compatible" by the additive rule is invisible to new data unless it chooses the lenient route by hand; and in today's code the same situation is a loud error, so the draft changes the failure mode.

### (ii) Writer has version 1, reader has version 2

| Party | What it sees |
|---|---|
| Writer | writes digest1 entries |
| Reader, typed template of version 2 | never matches digest1 entries (strict rule) |
| Reader, null template + filter + `as(R_v2.class, ADDITIVE)` | gets `R(a,b,null)` with `defaulted=[c]` |
| Reader that reuses that converted record as a template | `c = null` is a wildcard, so the template is wider than the data suggested (draft 4.6 notes it; the record cannot carry the mark). With a null-accepting constructor (draft option A) nothing stops it. |
| Reader that writes it back | The entry is now digest2 with `c = null`, which is not the same fact as "c was never set". |

Surprising: `null` means three things at once: wildcard, stored null, and absent-in-old-version. The `defaulted` list exists only on the `Converted` wrapper.

### (iii) A legacy host and a code-free host share one space, one legacy entry class L and one record entry class R

Taking "legacy host" to mean a new-release host running legacy-API code through the resident wrapper (the operator ruled out live interoperation with older releases).

| Party | L (legacy) | R (record) |
|---|---|---|
| Legacy-API caller (wrapper decodes eagerly, casts to its class) | Works as today; may download L's class by codebase (EntryRep.entry() loads by codebase, line 849) | Works only if the wrapper has the record branch and R is local; it must not download |
| Code-free host, new interface | `MarshalledEntry` of L: schema chain longer than one if L has a superclass; the host reads fields by name, cannot get a typed L; no download | `MarshalledEntry` of R: chain length one, class-free introspection of scalar fields |
| Superclass template (a query for a base type of L) | Works for legacy callers (isAtLeastA); a code-free host has no way to ask for a base type unless the draft keeps class names and superclass names in the new wire record (draft question 7) | Not possible (no inheritance) |
| Template built without the class | The draft sketch has `MarshalledEntry.of(Entry)` and `with(name, value)` on an existing entry; there is no way to build a wildcard template from a schema alone. A code-free host that does not hold the class cannot express "all L with field x = y" except through a filter | same |

Surprising: the two kinds of entry are matched by two different rules in one space (class name plus superclass names plus slices for L; digest plus slices for R), and the legacy server check on class-name hash applies to L but would not apply to R unless the new wire record keeps it.

## 8. Findings (most severe first)

Counts: HIGH 3, MEDIUM 7, LOW 5.

### F1 (HIGH, CONFIRMED by reading): the strict-digest rule is described as new space behaviour, but the space already enforces one hash per class name, loudly
- Scenario: scenarios (i) and (ii) above.
- Evidence: OutriggerServerImpl.java:264 (`typeHashes`), :1495-1540 (`typeCheck`, "Class mismatch" UnmarshalException), call sites :1608, :1680, :1944, :2370-2414. EntryRep.findHash (EntryRep.java:382-420) computes the 64-bit hash from field names and types plus the superclass hash. The draft's finding 7 and hazard 2 describe class name as routing only.
- Smallest fix: state in the design which of the two identities governs (the 64-bit hash plus `typeHashes`, or the 32-byte digest), whether two record versions may coexist in one space (then drop `typeCheck` for records and say so), and add a test for "a v1 template does not match a v2 entry" and one for "v2 write after v1 write".

### F2 (HIGH, CONFIRMED by reading; behaviour not run): a record is seen as an entry with zero fields by every reflective path, and some of them accept it silently
- Evidence: EntrySchemaGenerator.java:217 (`getFields()`) and :162 (chain would include `java.lang.Record`); LookupAttributes.java:261-270 (`matches`: empty field list, so any two records of one class match), :389-412 (`update`: `newInstance` fails); EntryRep.java:763-790 (`ensureValidClass` needs a public no-arg constructor, so a record with components is refused in Outrigger, but a record with no components passes); EntryRep.java:545-554 (superclass loop adds `java.lang.Record`). No code anywhere calls `isRecord` (grep).
- Smallest fix: before any other work, add an explicit `isRecord` refusal to every legacy path (generator, both EntryRep classes, LookupAttributes, ClassMapper) so a record cannot be mis-handled, then add the record branch behind the same check. Test: a record template must not match a record entry with different values.

### F3 (HIGH, CONFIRMED by reading): `MarshalledEntry` holds the body only, but legacy routing data lives outside the body
- Evidence: EntryRep carries `superclasses`, `hashes`, `hash`, `className`, `codebase` as separate fields (EntryRep.java:232-236); `isAtLeastA` and `typeCheck` use them (EntryRep.java:1252-1264; OutriggerServerImpl.java:1495-1515). Draft 4.2: "the entry body ... nothing else".
- Scenario: a code-free host reads a legacy entry L as `MarshalledEntry`, then passes it as a template or writes it back: the superclass names, the legacy hash and the class name are gone; `as()` for a legacy class cannot check the hash.
- Smallest fix: either `MarshalledEntry` holds the whole record (body plus class name, superclass names, hash) for legacy entries, or `MarshalledEntry` holds record entries only in 1.0 and legacy entries stay on `Entry` with eager decode.

### F4 (MEDIUM, CONFIRMED): the "absent field -> default" behaviour is not reachable and the existing decode swallows failures
- Evidence: EntryRep.java:850 (hash gate), :953-960 (positional decode by local `entryForm()` index), :957-959 (`catch (Throwable e) { rawValues[i] = null; }`), OutriggerGetEntryArgImpl.java:41-50. EntryRepV2Codec field-count guard compares against the body's own chain (~466-478).
- Scenario: a field that fails to decode becomes null; if that entry is reused as a template, null is a wildcard (a widening). A relaxed hash gate would assign shifted fields to wrong names.
- Smallest fix: the record decode must be by name against the stored chain, and a failed component must fail the whole decode, not become null. Do not copy lines 957-959.

### F5 (MEDIUM, CONFIRMED): release 16 is not a floor; the codec needs Java 25
- Evidence: poms (section 4.3); `od` major versions 65 (platform) and 69 (der); EntryV2Codec.java:23-37 (provider in jgdms-der, JDK 25, through ServiceLoader; "a v2-born space requires a DER-capable JVM").
- Smallest fix: state the real floor (21 to load the platform, 25 to encode or decode) in the design and in the module's documentation; do not advertise 16.

### F6 (MEDIUM, CONFIRMED): the draft's statement that filter authoring comes "from the same change" is not true today, and the single-schema-source claim is false for @SerialEntry
- Evidence: EntrySchemaGenerator.java:153-157 rejects @SerialEntry; EntrySchemaView.java:70-81 documents the rejection; EntryRepV2Codec.encodeSerialEntry builds its own schema. Appendix A.5 says one source.
- Smallest fix: decide whether record support goes into the generator (and then @SerialEntry should too, so there is one source) or stays a derivation of @SerialEntry; update Appendix A.5 to say what is true.

### F7 (MEDIUM, CONFIRMED): the standard lookup attributes and beans cannot become record entries
- Evidence: 12 classes (A.1); 23 bean assignments in 8 beans (A.4); three use non-table types (A.5); `makeLink` casts (NameBean.java). Superclass bases Status and ServiceType (A.2).
- Smallest fix: say in the design that the standard attributes and beans stay legacy and the record rule applies to new classes; record that "any Status" queries need a different mechanism.

### F8 (MEDIUM, PLAUSIBLE): no way to build a template without the class
- Evidence: the sketch in draft 4.3 (`of(Entry)`, `with(name, value)`); no builder from a schema. A code-free host must be able to say "all entries of identity D where field x = v".
- Smallest fix: add a `MarshalledEntry.template(schemaDigest or typeName, Map<String,Object>)` (or a `Query` built from names, which draft option B already implies) and say how its identity is checked.

### F9 (MEDIUM, PLAUSIBLE): no migration path for an existing entry class, and the identity systems differ
- Evidence: legacy hash is the 64-bit "SHA" form (EntryRep.findHash:382-420) while `@SerialEntry` uses a SHA-256 64-bit form and the DER digest is 32 bytes (STD-006 7.7.1, lines ~2300-2370); a stored entry decodes through `entry()` only if the local hash equals the stored hash (EntryRep.java:850), else UnusableEntryException; the flag-day appendix (A.8) says there is no migration tool.
- Scenario: a team turns class `Foo` (legacy) into record `Foo`. In the narrow case of no superclass and alphabetical fields the DER digest may not change (section 6, caveat 3, INFERRED), but the 64-bit hash does, so stored `Foo` entries become unusable through the legacy interface and are a second identity in the space.
- Smallest fix: write the migration recipe: (1) new record gets a new class name and both are written during transition; or (2) a one-shot "take as MarshalledEntry, convert, write" job; leases expire the rest; say what happens to entries that stay.

### F10 (MEDIUM, CONFIRMED): record entries lose the @SerialEntry decoupling of Java names from wire names
- Evidence: SerialEntry.java header (wire names independent of Java names); draft 3.3 (wire name = component name).
- Scenario: renaming a component for tidiness changes the identity.
- Smallest fix: accept it explicitly (the board guidance G4 argues against annotations that restate a declaration) and document that a rename is a new type, or provide one derived mechanism.

### F11 (LOW, CONFIRMED): STD-006 7.7.1 and Appendix A.1 disagree on zero fields; `superclassHash` rule needs `java.lang.Record`
- Evidence: 7.7.1 `fields SEQUENCE (SIZE(1..maxFields))`; Appendix A.1 `SIZE(0..maxFields)`.
- Smallest fix: one rule for both; record that `java.lang.Record` is not a chain member.

### F12 (LOW, CONFIRMED): Java serialization of entries is permission-gated, not refused
- Evidence: AtomicMarshalInputStream.java:211, :2777-2778.
- Smallest fix: answer draft question 10 with this fact and add a test.

### F13 (LOW, CONFIRMED): the record example in SerialEntry's javadoc contradicts the wildcard rule and no test covers records
- Evidence: SerialEntry.java:104-135 (`Objects.requireNonNull` in the GetEntryArg constructor); scan: 0 records implement Entry anywhere.
- Smallest fix: fix the javadoc once draft question 3 is settled; add a record round-trip test before design work continues.

### F14 (LOW, PLAUSIBLE): `MarshalledEntry implements Entry` can be passed to the legacy lookup code
- Evidence: LookupAttributes.matches/equal use `getFields()` (lines 261-270, 523-545); a class with no public fields compares equal.
- Smallest fix: make the legacy paths refuse `MarshalledEntry` (like finding F2) until the lookup question is settled.

### F15 (LOW, UNVERIFIED): reflective access to record components under a SecurityManager
- Evidence: none opened; JDK rule recalled.
- Smallest fix: a test with a policy that grants only what the codec needs.

## 9. Recommendations on the six operator statements (section 1 of the draft)

| # | Statement (short) | Recommendation | Reason |
|---|---|---|---|
| 1 | New Entry spec; fields final by default; immutable | ADOPT WITH A CHANGE: apply it to a new kind of entry (record entries) and keep legacy entries as they are | No production entry depends on non-final fields except the 12 standard attributes and the beans (23 assignments); `@SerialEntry` already allows final fields and the javadoc already shows a record. Retrofitting would break the beans and 13 qa classes with setters. |
| 2 | Use records; lookup attributes have nulls as wildcards | ADOPT WITH A CHANGE: allow records, but the canonical constructor must accept null (option A of question 3) and invariants must be checked elsewhere | `modifyAttributes` and every template idiom rely on null; the javadoc example that rejects null in the constructor must change. Lookup attributes stay legacy in 1.0. |
| 3 | Once published, do not modify; inheritance allows it; less is more | ADOPT, scoped to record entries | Fits the evidence: no production concrete hierarchy exists. But superclass templates are a built-in feature (isAtLeastA; LookupAttributes.isAssignableFrom; Status and ServiceType), so legacy keeps inheritance and the design must say so. |
| 4 | Identity more than the class name | ADOPT WITH A CHANGE: say how the new digest relates to the existing 64-bit hash and `typeCheck` | The legacy hash already covers field names and types (EntryRep.findHash), and the space already refuses a second hash per name. The new rule must say whether versions can coexist (finding F1) and whether a component rename is a new identity (F10). |
| 5 | Return a `MarshalledEntry` when there is no local class | ADOPT WITH A CHANGE: hold record entries first; for legacy entries keep routing data with the value or leave them on `Entry`; add a template builder from names | Body-only loses class name, superclass names and hash (F3); no template from a schema (F8); do not let it flow into the legacy lookup code (F14). |
| 6 | Return all entries marshalled by default | ADOPT WITH A CHANGE: marshalled by default on the NEW interface only; the legacy interfaces keep eager decode through the wrapper | 57 qa files cast results of read/take; the cast-friendly legacy interface must stay. Decode failure must fail the call, never become null (F4). |

## 10. Views on the open questions in the draft's section 7 that this survey informs

- Q1 (are the six statements rulings): see section 9; register them with the changes stated.
- Q3 (null in the canonical constructor): option A for record entries, with invariants checked at write time by a separate hook, because lookup and qa idioms construct templates with nulls. The survey shows the idiom in all 54 qa entry classes and in `LookupAttributes.update`.
- Q4 (component order is identity): yes. The legacy hash uses alphabetical order, which hides reorderings; a record's declared order should count. The cost is that a reorder is a new type.
- Q5 (class-free values): the codec has a class-free scalar decode only (`decodeScalarSliceValueClassFree`, EntryRepV2Codec ~560, signature only). Survey: String, Integer, Long, Boolean dominate (section A.5). So version 1 of `get(name)` should return scalars, `byte[]` and enum names, and an opaque holder for the rest.
- Q6 (must-understand mark): defer; not in 1.0.
- Q7 (keep class name and superclass names): yes for legacy entries; the legacy routing (isAtLeastA, typeCheck) needs them (F1, F3). Not needed for record entries.
- Q8 (where the new interface lives): the new local module at release 21 (section 4.3), not lib-dl.
- Q9 (lookup): not in 1.0. The two records are ratified as distinct (Appendix A.0) and the standard attributes must stay legacy (F7).
- Q10 (marker beyond Entry): no new marker; Java serialization of Entry is permission-gated today (F12); add a test.
- Q11 (nested records, records that are not entries): refuse in 1.0; the type table has no record type and whether `@AtomicSerial` applies to a record is NOT CHECKED.

## 11. Not checked

- Whether the new Derek (DER registrar) handles entries or `modifyAttributes` differently from Reggie; Reggie's service-side attribute code.
- Reggie `EntryRep`/`ClassMapper` beyond a grep of the lines that use `getFields`, `newInstance`, `@SerialEntry`.
- Whether `typeHashes` is rebuilt from the store on recovery (only the in-memory map was seen).
- Whether any client code in repo main uses abstract `Status` or `ServiceType` as a template.
- The count of qa tests that assign entry fields after construction; the count of qa tests that depend on a downloaded entry class.
- The number of main-source files importing `org.apache.river.*.proxy.` types (my grep timed out); qa gave 3 files, examples 2.
- JDK specification statements marked INFERRED (records, serialization, access rules); nothing was compiled or run.
- Behaviour of jgdms-lib-dl when compiled at release 8 against release-21 class files (no built lib-dl classes in the tree).
- Guidance document Part II (domain sections) and the design memo beyond D14 were not read.
- The scan is regex-based: name clashes and multi-declarator fields may be mis-parsed; the hand corrections are listed in section 3.
