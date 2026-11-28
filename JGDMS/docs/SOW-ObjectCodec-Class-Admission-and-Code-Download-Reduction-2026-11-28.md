# SOW: class admission in the Atomic DER decoder, and design options for replacing code download with CEL

- **Status:** scope, revision 2 (after board review; see A.6). Part A is a code change with tests. Part B is an investigation and design proposal. D1 to D3 are rulings; D5 to D10 are open and block steps A3 to A7.
- **Date:** 2026-11-28. **Written by:** Claude Fable 5.2, proxy-board review session, at the operator's request.
- **Commit read:** JGDMS trunk `45f719aa7f342f69ba224c0820ff49e839c87db9`. Line numbers below are at that commit.
- **Reviewer of the results:** the proxy-board review session (the operator passes results back). Part A's code is also reviewed by a model other than the one that wrote it.
- **Parts A and B are independent.** They can go to different sessions. Only one session writes code at a time.

## 1. Why

**Part A.** The object-constructing decode in `jgdms-der` loads classes whose names come from the wire, and checks them afterwards. The operator has ruled that the remote end cannot dictate which classes are loaded (RL-JGDMS-0244) and that field stores are keyed by class name, handed only to the class the endpoint's assigned loader resolves for that name (RL-JGDMS-0245). The code does not yet do either. Reading it, and a board review of this scope, found a conflict with STD-006 and further defects (A.2).

**Part B.** The operator's position (RL-JGDMS-0246) is that code owned by the same entity is not a threat and that, with DER and CEL, few cases should still need downloaded code. A sort of the 234 source files in the download modules found 63 that do work in the client, 40 that only forward calls, 130 that are data, exceptions or declarations, and 1 uncertain (`JoinManager`). Two earlier scopes cover the lookup service and Outrigger with Mahalo. Code-free investigations of Norm, Fiddler, Mercury, Mahalo and Outrigger dated 2026-10-31 exist, with two reviews and operator rulings in the class-free lookup design memo. Nobody has asked whether CEL has a role in Mercury, Norm and Fiddler (the Outrigger scope, section 6, says so).

## 2. Read first

| File | Why |
|---|---|
| `docs/HANDOVER-proxy-board-review-2026-11-15.md`, add.21 and add.22 | The findings this scope acts on, with what was checked and what was not. |
| `docs/JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` sections 3.9 to 3.11 and the evolution cases near line 3355 | Field namespaces, data independence from code, "class absent locally". |
| `docs/JGDMS-STD-008-AtomicSerial-Serialization-Uncoupling-v0.1-DRAFT.md` sections 15 to 17, in particular 16.2 and 17.1.1 | Nested values, arrays, the per-class permission gate, the broad-slot residual, enums in polymorphic slots. |
| `jgdms-der/src/main/java/au/net/zeus/jgdms/der/object/ObjectCodec.java`, `DerGetArg.java`, `ProxyWireSupport.java`; `der/getarg/WireTypes.java`, `ResolutionContext.java`; `der/stream/DerObjectStreamCodec.java`; `der/serial/DerReplacer.java` | Part A's code. |
| `jgdms-platform/src/main/java/org/apache/river/api/io/AtomicSerial.java` (`GetArg.callerClass`, near line 395) | Caller resolution shared with the JOSS-side stream. |
| `jgdms-der/src/test/java/au/net/zeus/jgdms/der/conformance/Std006DataIndependenceClassNamingFieldsTest.java` | The existing test that pins class-free decoding and pins that the constructing path still resolves the class. |
| `docs/RESULTS-dl-module-class-sort-2026-11-28-part1-lib-dl-1-59.md`, `-part2-lib-dl-60-117.md`, `-part3-services.md` | Part B's starting table. Every row is a sub-agent's reading. |
| `docs/RESULTS-lookup-cel-investigation.md`; `docs/SOW-Lookup-CEL-Code-Free-Investigation-2026-11-20.md`; `docs/SOW-Outrigger-Mahalo-CEL-Code-Free-Investigation-2026-11-20.md` | What is already answered or already scoped. Part B does not repeat them. |
| `docs/JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` sections 1, 3, 5.4, 9.4 to 9.6 | What CEL is, its theorem, and what it must not become. |
| `docs/SOW-Smart-Proxy-Isolation-Architecture-Overview.md` section 1a | An open idea, not decided: no state and no I/O in an expression. "No iteration" is STD-011 section 5.4. |
| `docs/INVESTIGATION-code-free-norm-2026-10-31.md`, `-fiddler-`, `-mercury-`, `-mahalo-`, `-outrigger-`; `docs/REVIEW-code-free-service-reports-fable-2026-10-31.md` and `-opus-` | Part B: work already done on these services. The Norm report covers `SetProxy.getLeases`. |
| `docs/DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` sections 12, 13 and 17 | Part B: operator rulings that bound the RP and LIB options (resident proxy implementation; a record-style wire interface plus a resident wrapper per service; generic library classes with local state are acceptable). RELAYED from the board; re-open before relying on them. |
| The tests in A.2a | Part A: existing tests that pin behaviour the change touches. |

## 3. Rules for both parts

1. **No commit and no push** unless the operator says so.
2. **Do not write to the claim register.** Report findings; the reviewing session registers them.
3. **Label every factual statement:** VERIFIED (you opened the line or ran the command in this session; give file, line and anchor text), RELAYED (from a sub-agent or another document, not re-opened), INFERRED, or NOT CHECKED. A sub-agent's report is a lead, not a fact.
4. **Record the commit** (`git rev-parse HEAD`, branch) at the top of each results file. If it is not the commit above, re-find every line number before using it.
5. **DirtyChai is not changed.** If a step seems to need a new permission class or any other DirtyChai change, stop and report it. DirtyChai's own rules reserve that work for a person.
6. **Standards are not edited.** Where a standard and the code disagree, write the proposed wording in the results file for the standard's owner.
7. **Stop and report** instead of guessing if a file in section 2 is missing, if a ruling and the code cannot both be satisfied, or if a step cannot be finished.
8. Plain language. Short sentences. Tables where items are compared.

---

## Part A. Class admission in the Atomic DER decoder

**Output:** code and tests in `jgdms-der` (and `jgdms-platform` only for A7), plus `docs/RESULTS-objectcodec-class-admission.md`.

### A.1 Decisions

The operator accepted D1 to D3 on 2026-11-28 ("Your recommended decisions are accepted."). The rulings are RL-JGDMS-0247 (D1), RL-JGDMS-0248 (D2) and RL-JGDMS-0249 (D3).

**The board review (section A.6) found that D1 as first proposed is incomplete and that the gate has no reliable declared type to check against. D5 to D10 are open. A0, A1, A2 and A8 may start now. A3 to A7 do not start until the operator has decided D5 to D10.**

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | How is a wire-named class admitted into an open slot (a non-final, non-sealed class, an interface, or `Object`) before it is loaded? | A build-time index of `@AtomicSerial` class names, in a resource under `META-INF` in each jar that defines such classes, read through the endpoint's assigned loader. (RL-JGDMS-0247) | DirtyChai's `LoadClassPermission` takes no class name; it gates which code source may define classes. A name-bearing permission would be a new DirtyChai class. The index needs none. |
| D2 | A chain record names a class that cannot be resolved by the endpoint's loader. Read its fields and carry on, or fail? | Read and carry on, as STD-006 says. (RL-JGDMS-0248) | The standard already promises it. It is not free: see O11 and D8. |
| D3 | `GetArg.callerClass` is shared with the JOSS-side stream. Fix its one-class shortcut there too? | Yes, as its own commit. (RL-JGDMS-0249) | The defect is the same on both paths. A separate commit keeps the JOSS-side risk visible. |
| D4 | Who writes the code, and which model reviews it? | The operator's choice, made when Part A is dispatched. | Same-model review is not wanted. |

**Open decisions. Each has a recommendation from the reviewing session; none is a ruling.**

| # | Question | Options | Recommended |
|---|---|---|---|
| D5 | A slot declared `Object` or `Any`, or one whose declared type cannot be found. Every indexed class lists `Object` as a supertype, so the index does not narrow it: the remote end still chooses among all indexed classes. Is that acceptable under RL-JGDMS-0244? | (a) Admit any indexed class; state it as the residual; RL-JGDMS-0244 is then met as "the remote end chooses among indexed classes assignable to the declared type". (b) Fail closed when the declared type is `Object` or unknown. | (a), together with threading real declared types to every site (O10) so that few slots are left at `Object`. (b) breaks `Object`-typed collections and `Any`. |
| D6 | Whose index may admit a name? A union of every copy visible through the loader lets any jar admit a name another jar defines, and claim supertypes for it. `DerReplacer` reads one resource from its own loader for that reason. | (a) Union of all copies. (b) An index line admits a name only for the code source that defines that class. | (b). Before the load, the index resource and the class file must come from the same location through the same loader; after the load, the class's code source is compared. `ResolutionContext.NONE` (null loader) fails closed for open slots. |
| D7 | A `[8]` proxy value names its interfaces on the wire. The index lists `@AtomicSerial` classes, not interfaces. What admits an interface name before it is loaded? | (a) Reject `[8]` in any slot that is not an interface type or `Object`; in an interface slot require the declared interface among the wire names, and admit other names only from a second index section listing interfaces. (b) Leave the proxy path ungated and state it. | (a). Names not admitted are dropped, as the existing narrowing does (`ProxyWireSupport.java` 139 to 144). The handler is decoded against `InvocationHandler` on both paths. |
| D8 | A field of type `java.lang.Class`, or `enum:<name>`, carries a class name as its value. Today the store resolves it when the store is built, before any caller or declared type exists (O11). | (a) Build stores class-free; resolve enum and `Class` values only when a typed `get` asks, through the A3 gate, with the bound taken from the declared field type or generic signature. An unbounded `Class<?>` is a D5 slot. (b) Keep eager resolution. | (a). (b) cannot satisfy RL-JGDMS-0244 or RL-JGDMS-0248. |
| D9 | Existing tests pin behaviour that Part A changes (list in A.2a). May the coding agent change them? | (a) Yes, each change listed and justified in the results file. (b) No; the agent reports and a person edits. | (a), with the list reviewed before merge. |
| D10 | Composition. A class C that used to extend S now holds an S built with `new S(arg)`. When the wire has no record for S, S's constructor reads C's record today (`ClassEvolutionTest` line 196 pins it). STD-006 section 3.9 says no other class can read a class's SEQUENCE and no refactoring relocates a field. | (a) Keep: a composed class with no record of its own reads the composing class's record. STD-006 3.9 needs new wording. (b) A composed class with no record of its own gets an empty store, as a superclass absent from the wire does. Line 196's expectation changes. | (b). It is the rule STD-006 already states, and DER already gives the default in the mirror case (`ClassEvolutionTest` lines 183 to 190). Composition still works when the wire has an S record (line 174). |

**The index (D1). Details marked open are for A3 to settle and report, not to assume.**

- **Location.** One resource per jar, for example `META-INF/jgdms/atomic-serial-index` (name illustrative).
- **`DerReplacer` is not a precedent for merging.** Its list `META-INF/jgdms/der-serializers` (`DerReplacer.java` 98) is read as ONE resource from the codec's own loader, and its Javadoc (lines 127 to 149) rejects `ClassLoader.getResources()` because an open merge would let any jar on the class path contribute. The index is per jar and must be read from several jars, so it needs the D6 rule instead.
- **Read through the endpoint's loader.** No static table keyed by name. What `ResolutionContext.NONE` does is part of D6.
- **Line format.** The class name, then the names of its supertypes. With supertypes listed, "is this name admissible into a slot declared T" is decided on names alone, before any load. The real assignability check after the load stays. Under D6 (a) a false line could cause a load of a class that is then rejected; under D6 (b) a line can only speak for its own jar.
- **What the index must also carry.** The codec admits three things today that a plain list of `@AtomicSerial` classes cannot: enums in polymorphic slots (STD-008 section 17.1.1; they are not `@AtomicSerial`); `@Serializer` classes standing in for their `replaceObType`, which is an annotation value and unreadable before the load; and `Resolve` proxies (clause 3 of `admissibleConstructClass`). The index needs a marked entry for each, and a section for proxy interfaces if D7 (a) is chosen.
- **A missing index fails closed, for open slots only.** Final, enum, record and sealed slots need no index.
- **A generator exists to extend.** `jgdms-marshal-delegate-processor` (`MarshalDelegateProcessor.java`, `@SupportedAnnotationTypes("org.apache.river.api.io.AtomicSerial")`) already writes `META-INF/services/org.apache.river.api.io.MarshalDelegate` per module. Read at lines 56 to 77 and 348 only.
- **Open: merged or shaded jars.** Under D6 (b) a shaded jar is one code source and its index is the concatenation.
- **Open: OSGi.** A bundle sees only its own resource. The `Service` lookup used for delegates ignores the loader argument under OSGi (RELAYED from the board, `MarshalDelegates.java` Javadoc).
- **NOT CHECKED: whether a jar's signature covers a resource under `META-INF`.** Do not rely on "the signature covers the index" until this is confirmed.
- **The index is the jar's own statement.** It guards against other local classes being named into a slot. It does not vouch for the jar that carries it.

### A.2 The defects

Status key: VERIFIED = the reviewing session opened the lines. BOARD = a board seat reported it and the reviewing session re-opened the cited lines. RELAYED = a board seat reported it; not re-opened.

| # | Defect | Where | Status |
|---|---|---|---|
| O1 | A class named by the wire is loaded before any check. The assignability check runs after. | `ObjectCodec.java` 709 and 2480 (leaf), 737 and 2508 (each chain record), 2124 (enum), 2697 (array component); checks at 715, 2489, 2131 | VERIFIED |
| O2 | Field stores are keyed by `Class` objects obtained by loading wire names. | `ObjectCodec.java` 735 to 740 and 2506 to 2511; `DerGetArg.java` 83, 274 | VERIFIED |
| O3 | A chain record naming a class that cannot be loaded fails the constructing decode with "cannot load class". STD-006 says no error occurs. | `ObjectCodec.java` 737, 2508, 1856 to 1868; STD-006 near line 3368 | By reading. NOT RUN for chain records. The enum-leaf case is already run by `EnumInPolymorphicSlotTest.ctxEnumNamingAbsentClass_failsClosed` (216). |
| O4 | The wire's array component class name decides the allocated array's type. The declared component type is known at the call site but is only used to admit elements. | `ObjectCodec.java` 2697; `DerGetArg.java` 325 to 334 | VERIFIED |
| O5 | One overload and one caller pass no declared type, so elements are admitted at `Object`. | `ObjectCodec.java` 2616 to 2627; `DerObjectStreamCodec.java` 1641 to 1642 | VERIFIED |
| O6 | When one class is registered, caller resolution returns it without looking at the stack. | `AtomicSerial.java` 395 | VERIFIED |
| O7 | Other decode-side loads: `WireTypes.java` 507, 703, 781; `ProxyWireSupport.java` 129, 136, 160; `DerObjectStreamCodec.java` 1724, 2113, 2157; `DerReplacer.java` 188. | as listed | Anchors confirmed by the board. See O11 to O13 for what they do. |
| O8 | Whether loading initialises the class depends on the installed provider. `PreferredClassProvider` passes initialise false (622, 654, 705, 722) and `LoadClass.forName` is `Class.forName(name, initialize, loader)` (RELAYED, `LoadClass.java` 97 to 100). The JDK `RMIClassLoader` fallback was not read. STD-008 section 17.1.1 says admission runs "BEFORE resolution"; the code loads first (2124) and relies on a load that does not initialise. | `ClassLoading.java` 343; `PreferredClassProvider.java` | Partly read |
| O9 | `DerReader` has no nesting-depth limit of its own. `ObjectCodec.MAX_NESTING` (16) and `DerInputLimits` exist. Whether every reader of untrusted bytes is covered is not known. | `DerReader.java`; `ObjectCodec.java` 153; `DerInputLimits.java` 58 | NOT CHECKED |
| O10 | The gate has no reliable declared type. The typed `get(name, val, type)` does not pass `type` to the decode; it checks `type.isInstance` afterwards. `DerGetArg` guesses the slot type by reflecting on a field of the wire record's field name and falls back to `Object`. Element types fail open to `Object`. `readObject(Class)` on the stream decodes and constructs, then checks. | `AtomicSerial.java` 691 to 697; `DerGetArg.java` 300 to 302, 353 to 354, 454 to 458; `DerMarshalInputStream.java` 399 to 403 | BOARD |
| O11 | Building a field store resolves classes named as field values: an `enum:<name>` field loads the enum class and calls `Enum.valueOf`, which initialises it; a `java.lang.Class` field loads whatever class the wire names; enum arrays load their component. This runs for every chain record, with no caller and no declared type. A class-free mode exists (`DerFieldStore.classFree`, line 326) and is not used on this path. | `DerFieldStore.java` 418 (from the constructors at 241 and 283, used at `ObjectCodec.java` 737 and 2508); `WireTypes.java` 128 to 129, 507 to 513, 703 | BOARD |
| O12 | A nested `[8]` proxy value is decoded without the slot's declared type. The wire's interface names are loaded and a proxy class is defined. On the stream path the handler is decoded with no `InvocationHandler` bound and checked afterwards. | `ObjectCodec.java` 2308 to 2309; `ProxyWireSupport.java` 129, 136; `DerObjectStreamCodec.java` 1769 to 1770 | BOARD |
| O13 | The stream's top-level object path loads the wire's leaf name and passes that class as the expected type, so the leaf is admitted against itself. The stream's `[7]` enum path loads the named class and calls `Enum.valueOf` with no admission check. | `DerObjectStreamCodec.java` 2113 to 2116, 2157 to 2159, 1724 to 1730 | BOARD |
| O14 | More sites that decode at `Object` or with `ResolutionContext.NONE` (null loader): `AnyCodec.java` 364; `EntryRepV2Codec.java` 609; `MarshalledInstanceCodec.java` 185; the public `decodeNested` overloads at `ObjectCodec.java` 2171 to 2203. | as listed | `EntryRepV2Codec` 609 and `MarshalledInstanceCodec` 185 BOARD; the rest RELAYED |
| O15 | A chain that names one class twice is accepted; the second store silently replaces the first. Two encodings for one value. | `ObjectCodec.java` 737 to 739 | BOARD |
| O16 | Composition and the stack walk: the walk returns the first frame that is a registered class. A composed class with no record on the wire is skipped, so its constructor reads the composing class's store. See D10. | `AtomicSerial.java` 396 to 400 | Lines BOARD; the behaviour INFERRED, and consistent with `ClassEvolutionTest` 196 |
| O17 | Names beginning `[` are handled specially by the provider, so a wire name can be an array descriptor. | `PreferredClassProvider.java` 596 to 609 | RELAYED |
| O18 | After stores are keyed by name, the set of classes the `DeSerializationPermission("ATOMIC")` check covers is undefined: today it is the store map's key set. A composed class's constructor runs inside the composing class's with no check of its own. | `ObjectCodec.java` 768, 2530 | BOARD (lines); the consequence INFERRED |

Not a defect: the `DeSerializationPermission("ATOMIC")` check (`ObjectCodec.java` 213 to 247; called at 330, 768, 2530) is made against the protection domains of loaded classes, so it cannot run before the load. It already runs before any constructor. It stays where it is. It does nothing without a security manager; a test seam exists at `ObjectCodec.java` 229 (RELAYED).

Checked by the board and found sound (RELAYED): the chain bounds in `SchemaChain.java` 195 to 262; that a dropped record's nested, array and collection fields are stored raw and never decoded (`DerFieldStore.java` 391 to 411); `decodeEnumLeaf` checks admission before `Enum.valueOf` (`ObjectCodec.java` 2128 to 2137); `MarshalDelegates` requires the delegate to be loaded by the construct class's loader.

### A.2a Existing tests that Part A touches

Found by the board; names confirmed by grep, bodies opened where a line is given. None was run in the review.

| Test | What it pins | Effect |
|---|---|---|
| `Std006DataIndependenceClassNamingFieldsTest.constructingPath_stillResolvesTheClass` (240 to 248) | The constructing store throws for an absent enum class. | Contradicts T3 once D8 (a) is taken. Needs the owner's decision (D9). |
| `EnumInPolymorphicSlotTest.ctxEnumNamingAbsentClass_failsClosed` (216) and `nonAssignableEnum_rejectedBeforeClassInitialization` (238) | Message text "cannot load class" and "admissible". | Wording may change with A3. |
| `DecodeAdmissionGateTest.objectTypedSlotIsTheDocumentedResidual_foreignSerializerConstructs` (155); `CollectionElementAdmissionGateTest.rawCollectionElementIsTheDocumentedObjectResidual` (227) | The `Object`-slot residual. | Stay under D5 (a); change under D5 (b). |
| `ClauseThreeResolveProxyTest` | The set of `Resolve` classes reachable through clause 3. | The index's `Resolve` entries must match it. |
| `ClassEvolutionTest.compositionReadsAnOldInheritanceStreamWithoutLoss` (174) | Composition reading an old inheritance stream, both codecs, classes in separate loaders. | This is T4 already. It passes today and must pass after. |
| `ClassEvolutionTest.aFlattenedWriterIsReadByTheOldHierarchyAndByTheSplitOne` (196) | "`new Base(arg)` resolves to Sub, the one registered class". | Changes under D10 (b) and under A7. |
| `GetArgCallerResolutionTest.oneRegisteredClassIsTheCallerWhoeverCalls` (98) and `oneRegisteredClassServesDefaultedToo` (115), in `jgdms-platform` | The one-class shortcut: an unregistered `Stranger` reads the one registered class's store. | A7 removes what they pin. |
| `EvolutionMatrixTest` (the STD-006 11.7 case near 593) and `NamespaceLeakRegressionTest` (58) | A loadable class removed from the hierarchy; the empty-store rule. RELAYED. | Existing cover; must keep passing. |

### A.3 Steps

**A0. Inventory. Read-only. Agent: Sonnet, general-purpose; the coordinator re-opens every row.**
List every place in `jgdms-der` main code that turns a string from the wire, or from a schema record, into a `Class`: `loadClass`, `loadProxyClass`, `resolveClass`, `Class.forName`, `Enum.valueOf`, `Array.newInstance`, `Proxy.newProxyInstance`. Start from O1 and O7 to O14. Include `EntryRepV2Codec`, `MarshalledInstanceCodec` and `AnyCodec`, although the entry redesign itself is not in this scope. For each site give: file, line, where the name comes from, **where the declared type of the receiving slot comes from and whether that source can fall back to `Object`**, what check exists and whether it runs before or after the load, which `ResolutionContext` is used, and whether the path is decode or encode. Encode-side sites that only resolve classes of a live local object are listed and marked out of scope.

**A1. What runs code, and when? Read-only.**
Follow `ResolutionContext.loadClass` through `ClassLoading.loadClass` to each provider that can be installed (`PreferredClassProvider`, the OSGi-aware provider if any, the `RMIClassLoader` fallback). State for each whether static initialisers can run on load, and which loader defines the class when the default loader does not (the provider falls back to the codebase or context loader). State which provider a proxy JVM and a host each install. Then list every call that runs code of a wire-named class: `Enum.valueOf`, `Proxy.newProxyInstance`, constructors, and delegate instantiation in `MarshalDelegates`; give each one's position relative to admission and to the ATOMIC check. If a provider initialises on load, STD-008 section 17.1.1 is wrong as well as O8; say so.

**A2. Tests that show the defects. Written before any fix.**
Each test states the invariant it defends. Each must FAIL on the commit above unless the table says it is a pin. Record the output in the results file. The tripwire is a class whose static initialiser and whose loader both record being touched.

| Test | Invariant | Before |
|---|---|---|
| T1 | A leaf name that is not admissible into the declared slot is rejected with no class loaded. | fails |
| T2 | The same for an enum's name and an array's component name. For a chain record that is not the leaf: its name is never loaded at all. | fails |
| T3 | A chain with a record for a class the endpoint's loader cannot resolve decodes with no error, including when that record carries `enum:` and `Class` fields naming unresolvable types. | fails |
| T4 | Composition reads an old inheritance stream. This is `ClassEvolutionTest` line 174; do not duplicate it. | pin: passes before and after |
| T5 | A wire component name that is not the declared component type or an admissible subtype, or that is itself an array descriptor, is rejected before allocation. | fails |
| T6 | A `GetArg` used with no frame of a registered class on the stack (captured and used after construction, or from another thread) cannot read any store. | fails where one class is registered |
| T7 | Two loaders each define a class of the same name. A store written for the name is handed only to the class the endpoint's loader resolves. | show it fails, or report that existing cover (`ClassEvolutionTest`) already holds and drop it |
| T8 | A `java.lang.Class` field naming the tripwire: the tripwire's loader is not touched when the store is built. | fails |
| T9 | A `[8]` value in a final-typed slot touches no loader. | fails |
| T10 | `DeSerializationPermission("ATOMIC")` is checked for every class whose constructor runs, composed classes included, before any constructor. Run under a security manager with a real policy; not `java.security.manager=allow`. | to be shown |
| T11 | `readObject(X.class)` on the stream with a wire leaf that is not an X: nothing is loaded or constructed. | fails |
| T12 | A chain naming one class twice is rejected. | fails |
| T13 | An index line in one jar naming a class defined by another jar admits nothing (D6 (b)). | new behaviour |

**A3. One admission gate, before the load (O1, O7, O10 to O14, O17).**
Every decode-side site from A0 goes through a single method that takes the wire name, the declared type of the receiving slot, and the `ResolutionContext`, and decides on names alone before loading. Before coding, put in the results file a table giving, for every A0 site, the source of the declared type after the change. At least:

- The `type` argument of `GetArg.get(name, val, type)` reaches the decode. This is a `jgdms-platform` change and goes with A7's commit. A first untyped read must not let a later typed read see a value admitted more widely.
- `readObject(Class)` reaches `DerObjectStreamCodec` and from there the leaf, `[7]`, `[8]` and `[9]` paths.
- The wire-named class is never its own bound.
- An unknown declared type is handled as D5 decides.

The rules, by declared type:

- Only binary class names are accepted. A leading `[`, a `/`, an empty name, or a primitive name in an object slot is rejected.
- Final, enum or record: the name equals the declared type's name, and the declared `Class` is used without loading the wire name; or the index marks the name as a `@Serializer` whose `replaceObType` is the declared type, confirmed against the annotation after the load. (`X500PrincipalSerializer` for an `X500Principal` slot is the existing example.)
- Sealed: the name is the declared type or one of its permitted subclasses, compared by name, recursively. At a permitted subclass that is itself open, the open rule applies.
- Open: the name is in the index with the declared type among its listed supertypes, under D6.
- `Resolve` proxies in narrow slots (clause 3): admitted only by an index entry marked as such. The set must match `ClauseThreeResolveProxyTest`.
- `[8]` proxies: as D7 decides.
- `Class`-valued and `enum:` field values: as D8 decides.

The existing assignability check stays, after the load.

**A4. Stores keyed by name, built class-free (O2, O3, O11, O15, O16).**
`decodeHierarchy` builds its stores keyed by the schema record's class name. It loads no class to do so, and resolves no enum or `Class` value while building (D8). State which store type a record for an unresolvable class uses; `DerFieldStore.classFree` exists. A chain naming one class twice is rejected. `DerGetArg` hands a store to a caller class only when the caller's name matches and the caller is the class that `ResolutionContext.loadClass` returns for that name. A composed class with no record of its own is handled as D10 decides. The empty-store rule for hierarchy classes absent from the wire (`ObjectCodec.java` 746 to 758, and the same loop near 2519) is kept. T3, T7, T8 and T12 pass; T4 still passes.

**A5. Array component (O4, O5). Test: T5.**
When the declared component type is known, it bounds the wire component name through the A3 gate. Remove or deprecate the overload that passes `Object.class`, or make its callers supply the declared type. Give `DerObjectStreamCodec` 1641 a declared type or record why it has none.

**A6. Permission check order (O18). Test: T10.**
Name the set of classes the ATOMIC check covers once stores are keyed by name: the construct class and its `@AtomicSerial` superclasses taken from the local hierarchy, never from chain names. State whether a composed class is checked when its store is handed out or is the composing class's responsibility. List delegate instantiation (`MarshalDelegates`) in the order.

**A7. Caller resolution (O6). Test: T6.**
Remove the one-class shortcut or give it the same identity test as the stack-walk path. This is in `jgdms-platform` and is shared with the JOSS-side stream. Separate commit, with the `type` threading from A3. `GetArgCallerResolutionTest` (two tests) and `ClassEvolutionTest` line 196 pin the shortcut and change with it (D9). Report, without implementing, whether a `GetArg` could refuse reads once the construct class's constructor has returned; that is a stronger rule than the stack test.

**A8. Depth and size limits (O9). Read-only unless a gap is found.**
List every entry point that reads untrusted DER, including inspection without object building, and show which limit covers it. Report gaps; do not fix them without asking.

**A9. No wire change, no slowdown unnoticed.**
- The bytes on the wire do not change. Show it with the existing conformance tests.
- Run the existing codec benchmark before and after and report the figures. The runnable benchmark and its results are in `jgdms-benchmarks/serialization` (`RESULTS.md`); the scope for it is `docs/SOW-DER-Codec-Performance.md`.
- If the admission verdict is cached, the cache is per loader. A static cache keyed by class name alone is not acceptable: two loaders can hold different classes of one name.

### A.4 Done when

1. T1 to T3, T5, T6, T8, T9, T11 and T12 fail before and pass after, with both outputs in the results file. T4 passes before and after. T7, T10 and T13 are reported as the table says.
2. The `jgdms-der` suite passes, and the `jgdms-platform` suite. State the command, the policy the security manager ran under, and the counts. Every existing test that was changed is listed with the reason (A.2a, D9).
3. Every A0 row is marked gated, out of scope with a reason, or open with a reason, and the declared-type table from A3 is in the results file.
4. A1's answer per provider and its list of calls that run code are in the results file.
5. A6's set and A8's coverage list are in the results file. A9's before and after figures are in the results file.
6. The results file has proposed wording for the owners of: STD-006 section 3.9 (may a constructor hand its `GetArg` to another class's constructor, and on what condition; D10); STD-006 section 11.8 ("absent" means not resolvable by the endpoint's loader, as well as removed from the hierarchy); STD-008 sections 16.2 and 17.1.1 (the broad-slot residual after D5; "before resolution").

### A.5 Not in Part A

- Any change to the wire format, to STD-006 or STD-008 text, or to DirtyChai.
- The index generator for production jars. Part A's tests use a hand-written index for every jar they touch. **Because a missing index fails closed for open slots, the gate must not be switched on for production jars until every jar carries an index.** Part A delivers the gate with a stated switch or ordering; the generator (an extension of `MarshalDelegateProcessor` is the likely place) is scoped separately and lands first.
- The JOSS-side stream beyond A7.
- The entry redesign (rev 3) and anything in the lookup or Outrigger scopes. `EntryRepV2Codec`'s decode site is still listed in A0.
- Any fix to how a decoded proxy carrier is resolved. That is CL-PROXYBOARD-0007 and has its own test scope, `SOW-CL-PROXYBOARD-0007-Proxy-Carrier-Decode-Test-2026-11-28.md`.

### A.6 Review record

Revision 2, 2026-11-28. The scope was reviewed by a two-seat board: an Opus seat (adversarial security, DER and module boundary) and a Sonnet seat (rule fidelity and anchor checking). Neither ran a test. The reviewing session re-opened the lines behind every HIGH finding and the MEDIUM findings marked BOARD above before changing this text.

- Taken in full: the missing declared-type sources (O10), eager class resolution in stores (O11), the proxy path (O12), the stream top level (O13), the further sites (O14), duplicate chain names (O15), composition and the stack walk (O16, D10), the ATOMIC set (O18), the `DerReplacer` and generator corrections, the existing-test list (A.2a), the "Done when" gaps, and the arithmetic and wording corrections in section 1 and Part B.
- Taken as an option, not a requirement: a `GetArg` that refuses reads after its constructor returns (A7).
- Not taken: returning a class name in place of a `Class` value for an unbounded `Class` field. It changes the value's type; D8 treats the field as a D5 slot instead.
- Not re-opened, marked RELAYED above: O17, part of O14, the `LoadClass.forName` reading, and the "found sound" list.

---

## Part B. Design options for replacing code download

**Output:** one file, `docs/RESULTS-code-download-reduction-options.md`. Read-only. No other file is created or changed. Existing tests may be run.

### B.1 The question

For each class in the download modules that does work in the client or only forwards calls: can the downloaded code go, and what replaces it?

The operator asked about CEL. CEL can only replace code that is a pure function of data: a test, a selection, a projection. It cannot hold state, loop without bound, or do I/O. So the answer for each class is one of six, and CEL is two of them:

| Code | Replacement | Fits |
|---|---|---|
| DER | Data only. The class becomes a closed record the client already has, or is decoded class-free. | Holders, results, events without behaviour. |
| CEL-S | An expression the client writes and the service evaluates. | Filters, templates, selection on the server. |
| CEL-C | An expression the service supplies and the client evaluates, bound to the authenticated server. Binding does not make it trusted: unless the service is the same entity by digest (RL-JGDMS-0246) it is third-party input the client runs. | Argument checks, choosing among returned values, formatting. |
| RP | A `java.lang.reflect.Proxy` over an interface the client already has, with a resident invocation handler. The endpoint, object ID, constraints and interface list still arrive as data from the service, on the `[8]` path (O12, D7). | Pure forwarders. |
| LIB | Resident client library. The logic is generic and belongs to the platform, not to one service. | Lease batching, paging, retry loops, lease arithmetic. |
| PM | It stays code, and runs in a proxy module when it is a third party's. | Whatever is left. |

### B.2 Sections

Agent types are recommendations. The session that runs this coordinates and writes the results file; it should be Opus or Fable.

**B0. Check the starting table. Agent: Sonnet, general-purpose, in parallel by module.**
The three class-sort files are relayed. Re-open every L and P row and the one U row. Correct any bin. Add the effective behaviour of each `Constrainable*` leaf that inherits client logic from its parent. Classify the three generated proxy sets (`jfr-telemetry-dl`, `hello-world-dl`, `bytecode-analysis-engine-dl`): the generator is `ServiceProxyProcessor` (`jgdms-service-proxy-processor`) and the annotation is `@JiniService` (`jgdms-service-annotations`), placed on `HelloWorldServiceImpl` in `hello-world-service`, not on the interface (RELAYED from the board, except the `HelloService.java` Javadoc at 41 to 47, which was opened). Confirm, and say whether the `-dl` directories are stale build output and whether they are tracked in git.

**B1. Pure forwarders. Agent: Opus.**
For the 40 P rows: what would a reflective proxy over a resident interface need that the forwarder has today? Cover constraint translation (`ConstrainableProxyUtil`, the method-mapping tables), proxy trust (`ProxyVerifier` classes, `TrustEquivalence`), `ThrowThis` translation, `ReferentUuid` equality, and `Administrable`. State which interface must be resident for each. `AdminProxy.DynamicAdminProxy` in `jgdms-lib-dl` already does this for admin interfaces; say how far it generalises. State which of the design memo's rulings (sections 12, 13, 17) bound the answer. For RP say: who supplies the endpoint, the object ID, the constraints and the interface list; how the client prepares and verifies the proxy; which interface set is allowed for each declared service type, given that client code tests marker interfaces such as `RemoteMethodControl`, `TrustEquivalence` and `Administrable` with `instanceof`; and that RP depends on D7.

**B2. Someone else's object, handed over by a service. Agent: Opus.**
Six classes unmarshal a third party's object in the client: Norm `SetProxy.getLeases`, `BasicRenewalFailureEvent` (in `jgdms-lib-dl`, used by Norm), Fiddler `FiddlerRegistration.getRegistrars`, Mercury `RemoteEventData`, Phoenix `AID.activate`, `UIDescriptor.getUIFactory`. For each: who owns the object, who authenticates it, what the client does with it, and whether it could be delivered as a class-free DER value, a bootstrap proxy prepared like a lookup result, or only through a proxy module. Two of them pass integrity `false` (`FiddlerRegistration.java` 818, `UIDescriptor.java` 219): say whether that is a defect today. Keep the service that delivers an object apart from the owner of the object's code: classify by the digest of the object's code, say which resolution context decodes the nested object (the delivering service's endpoint loader is the wrong one), and say whether the object is prepared with the third party's principal. `UIDescriptor.factory` already has an answer in the lookup result (E.8, lines 1077 to 1090: not in the new API); cite it and do not reopen it. The Norm investigation of 2026-10-31 already covers `SetProxy.getLeases`.

**B3. Client behaviour. Agent: Opus.**
For the behaviour rows (the paging iterators, the batching lease maps, `PolicyUpdateListener`, the Fiddler and Norm convenience logic; `SpaceProxy2` is the Outrigger scope's): is the logic generic enough to be LIB, or is it the service's own? `PolicyUpdateListener` exports a listener (297) and starts a thread (401); under RL-JGDMS-0038 a host exports no listener. No ruling was found that covers starting a thread; report it as a question. Say what replaces it on a host.

**B4. Where CEL fits outside lookup and Outrigger. Agent: Fable.**
Go through every L and P row not covered by the two earlier scopes and name each place a CEL-S or CEL-C expression could replace code. For each: the expression's inputs, who writes it, who evaluates it, what it is bound to, and whether today's language can express it. Use the lookup result's rules: no addition without a use; an addition needs the text of the extension to the STD-011 section 3.2 argument; a client-side expression is bound to the server's authenticated principal (the lookup result's finding F5; the design memo has a different F5). For every CEL-C use also state: the subject and access-control context the evaluation runs under (none of the client's capabilities), the function set, cost and size limits, whether evaluation ever turns a type name into a class (RL-JGDMS-0244), and what a rejecting expression can deny the client. If the honest answer for a row is "CEL does not fit", say so and give the code from B.1 that does.

**B5. The count. Agent: the coordinator.**
One table, one row per L and P class: today's bin, the replacement code, what must exist first, and whether the residue is same-entity or third-party under RL-JGDMS-0246. Then the totals: how many classes still need downloaded code, and of those how many need a proxy module.

**B6. Blind review of B1 to B4. Agent: a model that wrote none of them.**
Findings, most severe first. In particular: any replacement that moves trust without saying so, any CEL use that needs state or a clock, and any "resident" interface that a third party would in fact have to supply.

### B.3 The results file

1. Header: commit, authors and models per section, which statements the coordinator re-opened.
2. A one-page answer: the B5 totals and the three or four design options with what each costs.
3. Sections B0 to B6.
4. Recommendations, each marked adopt, option, defer or reject, with what the operator must decide.
5. Not determined.
6. Deviations from this scope.

### B.4 Not in Part B

- The lookup service and Reggie's entry machinery (answered in `RESULTS-lookup-cel-investigation.md`).
- Outrigger and Mahalo (scoped in `SOW-Outrigger-Mahalo-CEL-Code-Free-Investigation-2026-11-20.md`). If that scope has not been run, say so in the one-page answer; the B5 totals then exclude those rows and name them.
- Any code change, any change to STD-011, and any new language construct beyond assessing a need.
- The mailbox precautions, which wait on the operator.
