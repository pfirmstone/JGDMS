# Investigation: can Outrigger (JavaSpace) use a code-free proxy? (2026-10-31)

Model: claude-sonnet-5-5 (Sonnet 5.5). Report only; no production code, test or other file was changed. Nothing was built or run: every VERIFIED below means "I read the cited line". Paths are under `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS`; `OUT` = `services\outrigger`, `DL` = `OUT\outrigger-dl\src\main\java\org\apache\river\outrigger\proxy`, `IMPL` = `OUT\outrigger-service\src\main\java\org\apache\river\outrigger\OutriggerServerImpl.java`.
Method: `docs\TRAINING-code-free-proxy-investigation-2026-10-31.md` (8 questions) and memo `docs\DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` sections 11, 11.1 (and 11.2 read).

## Verdict

**CAN WITH CHANGES, and only in one sense.** Read the two senses separately.

- **Sense A: a bare JERI dynamic proxy of `JavaSpace05` / `TupleSpace` with no resident adapter. CANNOT.** The client-visible proxy converts `Entry` to `EntryRep`, runs a timeout/cookie query loop, builds `MatchSet` and iterator objects, and turns `long[]` into leases. None of that is "forward the call to an endpoint" (Q2). A plain `InvocationHandler` would send raw `Entry` objects, which the server cannot decode because it never loads entry classes.
- **Sense B: nothing is downloaded; the service ships only `@AtomicSerial` data and the client already holds `outrigger-dl` and `jgdms-lib-dl` as resident libraries. CAN WITH CHANGES.** This is the "local wrapper" shape of memo 3.8. Outrigger is already most of the way: the wire stub is already a JERI dynamic proxy exported with `AtomicDerILFactory`, and the smart proxy is `@AtomicSerial` data (a stub, a Uuid, a long, a format) plus logic. The changes are about who supplies the classes and about three open paths (Q6), not about rewriting the proxy.

## Q1. What does the service export today?

1. The wire stub is a JERI dynamic proxy. `ourRemoteRef = (OutriggerServer) exporter.export(serverGate)` (`IMPL:844`), exporter `BasicJeriExporter(TcpServerEndpoint.getInstance(0), new AtomicDerILFactory(null, null, OutriggerServer.class.getClassLoader()), false, true)` (`IMPL:1292-1296`). It must be `RemoteMethodControl` or start-up fails (`IMPL:851-856`). VERIFIED.
2. The object registered with lookup is a smart proxy wrapping that stub: `new ConstrainableSpaceProxy2(ourRemoteRef, topUuid, maxServerQueryTimeout, entryFormat(), initialConstraints)` (`IMPL:875-876`), passed to `joinStateManager.startManager(...)` (`IMPL:888`). Admin: `ConstrainableAdminProxy` (`IMPL:878`); participant: `ConstrainableParticipantProxy` (`IMPL:881`); leases: `LeaseFactory(ourRemoteRef, topUuid)` (`IMPL:883`); events: `ConstrainableOutriggerAvailabilityEvent` (`OUT\outrigger-service\...\AvailabilityRegistrationWatcher.java:430`); trust: `new ProxyVerifier(ourRemoteRef, topUuid)` (`IMPL:4470`). VERIFIED.
3. The initial constraints require `ATOMIC_DER` on the stub (`IMPL:872-874`). A deployer-supplied `serverExporter` is checked only if it is a `BasicJeriExporter`; any other `Exporter` gets a warning, not a rejection (`IMPL:1256-1284`). VERIFIED.
4. The class annotation (codebase) for the proxy comes from config `Codebase_Annotation` (`IMPL:1164`, returned by `getClassAnnotation` at `IMPL:1027-1030`). So today the service does ship a codebase. VERIFIED.
5. The class is `SpaceProxy2` (abstract, `@AtomicSerial`, `DL\SpaceProxy2.java:77-79`); its wire form is four fields: `space`, `spaceUuid`, `serverMaxServerQueryTimeout`, `entryFormat` (`SpaceProxy2.java:119-126`). `ConstrainableSpaceProxy2` adds `methodConstraints` (`ConstrainableSpaceProxy2.java:330-334`). VERIFIED.

## Q2. Does the proxy carry logic beyond forwarding?

Yes. The logic, by site (all VERIFIED by reading):

| Logic | Where |
|---|---|
| Entry to `EntryRep` conversion (reflection, class hash, superclass names/hashes, DER field encoding via a `ServiceLoader` codec, codebase annotation of the entry class) | `DL\EntryRep.java:506-531`, `:570-610`; called from `SpaceProxy2.repFor` (`SpaceProxy2.java:1181-1187`) |
| `EntryRep` to `Entry` (loads the entry class, instantiates it, sets fields by reflection, collects bad fields into `UnusableEntryException`) | `EntryRep.java:837-940`, `:946-968` |
| Query loop: timeout slicing against `maxServerQueryTimeout`, `QueryCookie` carried between sub-queries, end-time arithmetic | `SpaceProxy2.java:414-458` (read; take/ifExists copies follow at `:461-601`; filtered copies `:867-973`) |
| Local override of server query timeout from the system property `org.apache.river.outrigger.maxServerQueryTimeout` | `SpaceProxy2.java:307-321`, `:359-388` |
| Multi-template take: unmarshals each rep against each template, collects `UnusableEntriesException` | `SpaceProxy2.java:699-783` |
| Lease construction from `long[]` returned by `write` (expiration arithmetic, overflow clamp) | `SpaceProxy2.java:401-411`, `:1107-1124` |
| Local `MatchSet` with prefetch from `nextBatch` | `DL\MatchSetProxy.java:42-110`, created at `SpaceProxy2.java:850-852` |
| Local admin iterator with `fetchSize`, `delete`, `close` | `DL\IteratorProxy.java:40-139`, created by `AdminProxy.contents` |
| `snapshot` (pre-marshalled entry) | `SpaceProxy2.java:604-609` |
| Deprecated `MarshalledObject` overloads: non-null handback throws, null delegates | `SpaceProxy2.java:630-647`, `:804-827` |
| `equals`/`hashCode` by Uuid (`ReferentUuids`) | `SpaceProxy2.java:287-300` |
| Constraint mapping client method to backend method (36-entry pair array) and `setConstraints` that keeps the born format | `ConstrainableSpaceProxy2.java:66-322`, `:442-448` |
| Trust verification (`ProxyVerifier`, a `TrustVerifier` supplied by the server) | `DL\ProxyVerifier.java` (class at `:45`); returned at `IMPL:4468-4470` |

`ParticipantProxy` is pure forwarding plus Uuid identity (`DL\ParticipantProxy.java:120-168`); `AdminProxy` is forwarding except `contents` (wraps `IteratorProxy`) and carries the Entry-to-rep call (`AdminProxy.contents`, uses `SpaceProxy2.repFor`). VERIFIED.

## Q3. Which interfaces must the client have, and where do they live?

- Client-facing: `JavaSpace`, `JavaSpace05`, `TupleSpace`, `FilteredTupleSpace`, `MatchSet`, `AvailabilityEvent`, `JavaSpaceAdmin`, `JoinAdmin`, `LandlordLease`, `Landlord` are all in `jgdms-lib-dl\src\main\java\...` (paths located by search). `Entry`, `RemoteEvent`, `EventRegistration`, `TransactionParticipant` are in `jgdms-platform`. VERIFIED (location). That a JavaSpace client has `jgdms-lib-dl` is PLAUSIBLE (it cannot compile against `JavaSpace` without it).
- Wire-only: `OutriggerServer` (`DL\OutriggerServer.java:48`, extends `TransactionParticipant, Landlord, OutriggerAdmin, ServiceProxyAccessor, Remote`), `OutriggerAdmin` (`DL\OutriggerAdmin.java`), `EntryRep`, `MatchSetData`, `OutriggerQueryCookie` are **only** in `outrigger-dl`. So the dynamic stub's own interface is downloadable-only today. This is the first thing that stops it being code-free: a dynamic proxy needs its interfaces locally (training section 2, item 6). VERIFIED.
- `outrigger-dl` does not depend on `jgdms-der` except in test scope (`OUT\outrigger-dl\pom.xml`, `jgdms-der` with `<scope>test</scope>`); `EntryRep` finds its codec with `ServiceLoader` and throws `IllegalStateException` if none (`EntryRep.java:321-335`). So a resident client also needs `jgdms-der` on its classpath. VERIFIED.

## Q4. What crosses the wire, and is it `@AtomicSerial`?

Read and found `@AtomicSerial`: `EntryRep` (`EntryRep.java:76`), `MatchSetData` (`MatchSetData.java:32`), `OutriggerQueryCookie` (`OutriggerQueryCookie.java:30`), `SpaceProxy2`, `AdminProxy`, `ParticipantProxy`, `ProxyVerifier`, `OutriggerAvailabilityEvent` (`OutriggerAvailabilityEvent.java:37`), `LandlordLease` (`LandlordLease.java:47`), `ConstrainableLandlordLease` (`:43`), `EventRegistration` (`jgdms-platform\...\EventRegistration.java:52`), `ServerTransaction` (`ServerTransaction.java:48`), `RemoteEvent` (`RemoteEvent.java:100`), `AvailabilityEvent` (`AvailabilityEvent.java:49`), `LookupLocator` (`LookupLocator.java:77`). Entry attributes `Name` and `ServiceInfo` are `@SerialEntry` (`Name.java:33`, `ServiceInfo.java:35`). VERIFIED.

Not established (UNDETERMINED, not read):
- exception types on the wire (`TransactionException`, `UnusableEntryException`, `FilterRejectedException`, `UnknownLeaseException` and their causes);
- other `Transaction` implementations than `ServerTransaction`;
- `RemoteEventListener` and handback payloads supplied by the client (a client proxy object; see Q5);
- the arbitrary `Entry[]` the admin methods carry (`addLookupAttributes(Entry[])`, `modifyLookupAttributes`, `OutriggerAdmin extends JoinAdmin`, `DL\OutriggerAdmin.java`): `Name`/`ServiceInfo` are `@SerialEntry`, but a user attribute class may not be.

Entry field values are decoded with `ResolutionContext.NONE` through a DER object stream (`jgdms-der\...\entry\EntryRepV2Codec.java:608-610`) or a schema chain (`:623-626`); a proxy-typed field is refused at encode (`:643-663`). VERIFIED. Which field types a user class may use, and what that decode does with a class not on the classpath, I did not trace (UNDETERMINED).

## Q5. Proxies passed as values (each must also be code-free)

- Leases: `ConstrainableLandlordLease` with renew/cancel logic, lease maps (`LandlordLease.java:153-222`) from `jgdms-lib-dl`. Shared with other Landlord services, so resident-library is the natural home. VERIFIED (logic read in part).
- `EventRegistration` (data, `@AtomicSerial`) holding a lease. PLAUSIBLE.
- `ConstrainableOutriggerAvailabilityEvent` / `OutriggerAvailabilityEvent`: data (`EntryRep rep`) plus logic `getEntry()` calling `rep.entry()` (`OutriggerAvailabilityEvent.java:89`), which can load a class (Q6). Delivered to a client-exported listener. VERIFIED.
- `ParticipantProxy` handed to a transaction manager (the manager must deserialise it): forwarding only (Q2), the easiest to make a bare dynamic proxy (it needs `equals`/`hashCode`/`ProxyAccessor` only for identity). VERIFIED.
- `IteratorProxy`, `MatchSetProxy`: never serialised (comments `MatchSetProxy.java:39-40`, `IteratorProxy.java:33-36`); local objects built by the client-side proxy. VERIFIED.
- The client's own `RemoteEventListener`: exported by the client; the server holds it. Its export form is the client's choice (memo 11 row 10 / R6 applies). RELAYED.
- The `ProxyVerifier`: a server-supplied `TrustVerifier` class (see Q2). In a code-free design the verifier must be resident, not taken from the server. PLAUSIBLE.

## Q6. Paths that load a class from a codebase annotation

1. **`EntryRep.entry()` loads the entry class named in the data**: `CodebaseProvider.loadClass(codebase, className, null, integrity, null)` (`EntryRep.java:846-849`). `codebase` and `className` are wire fields (`:723-724`, serialForm `:692-703`). This runs when `realClass == null`. VERIFIED. This is the main fail-open path for Outrigger: a client taking an entry written by another client of an unknown class downloads that class. It applies to `read`/`take`/`MatchSet.next`/`AvailabilityEvent.getEntry`/`IteratorProxy.next`.
2. **Priming misses nested entry classes (my finding, VERIFIED by reading, not run).** `className` is `realClass.getName()` (`EntryRep.java:511`, `$` for nested), but `primeEntryClass` compares it with `tmpl.getClass().getCanonicalName()` (`:820`, `.` for nested). For a nested or inner entry class these differ, so the template class is never primed and path 1 loads by codebase even though the class is local. A code-free host with a nested entry class would hit a class-load attempt on every read. Needs a test to confirm; not edited here.
3. **The stub's own class and interfaces** (`OutriggerServer` etc., Q3) load from the proxy codebase. VERIFIED.
4. **`ProxyVerifier`/`TrustVerifier`** loads from the proxy bundle. PLAUSIBLE.
5. Lookup side (not Outrigger code): the memo 11 paths (null filter downloads the proxy, trust verifiers OR'd, self-asserted ServiceID, `Resolve` admission) all apply to how a client receives `ConstrainableSpaceProxy2` from a registrar. RELAYED from memo 11.1. Memo 11.2 says the required-format fix covers the call path only when the client asks for `ATOMIC_DER`; Outrigger sets that on the stub (`IMPL:872-874`) but a client with no constraint is unprotected. RELAYED.
6. The server side never loads entry classes (comment at `EntryRep.java:296` and `IMPL`'s `EntryRep`-only handling; the handback is checked for format only, `IMPL:1895-1913`). VERIFIED for the handback check; the "never loads" claim for the server I read only as comments plus the absence of `loadClass` calls under `OUT\outrigger-service\src\main` (search of `CodebaseProvider`/`RMIClassLoader` found only `IMPL:1029`). PLAUSIBLE-to-VERIFIED.

## Q7. Tests, and what a failing-when-violated test looks like

Existing, by name (not run): `ConstrainableSpaceProxy2FormatTest` (born format survives `setConstraints`), `SpaceProxy2MOOverloadTest`, `EntryRepDerFormatTest`, `EntryFormatGetArgCompatTest`, `SnapshotRepAtomicSerialTest`, `OutriggerSerialEntryRoundTripTest` in `DL`'s test tree; `DerOnlyConfigRejectTest`, `HandbackFormatRejectTest`, `JoinStateManagerDerFormatTest`, `StorableReferenceDerFormatTest`, `RecoveryFailCleanTest` in the service tests. A search for "code-free"/"class-free" in Outrigger sources hit only `EntryProjection`, `FilterAdmission`, `FilterEval` and their tests (the server-side CEL filter), not the client proxy. So **no existing test asserts that no class is loaded from a codebase when the client uses the proxy**. VERIFIED (file listing and search).

Proposed tests (each must be shown to fail with its mechanism removed):
1. Round trip `ConstrainableSpaceProxy2` through the DER stream with a context that refuses `loadClass`; expect success when `outrigger-dl` is on the loader. Fails if any wire class is not resident.
2. `EntryRep.entry()` for an entry whose class is absent from the loader: expect `UnusableEntryException`, and assert `CodebaseProvider.loadClass` is never called (a stub class loader that records the call). Fails today (path 1).
3. Nested entry class in a take with a template: assert no codebase lookup occurs. Fails today if path 2 is right.
4. A stub whose handler is not the DER handler is refused when `ATOMIC_DER` is required (the memo 11.2 test shape, applied to a `SpaceProxy2` carrier).

## Q8. Verdict and the smallest next changes

**Verdict:** CAN WITH CHANGES (sense B); CANNOT as a bare dynamic proxy (sense A). The proxy is already a thin data stub over a dynamic proxy; what is not code-free is (a) its classes, (b) the entry-class load by codebase, and (c) the verifier. Separately verified vs inferred: Q1, Q2, Q3 (locations), Q6 items 1-3 are read in code; Q5 events/lease logic and Q6 items 4-6 are partly inferred.

Smallest next changes (smallest first; each is for a human or a later session, nothing done here):
1. **Decide the tier with the operator** (the artefact question below). Without it, "code-free" is ambiguous between A and B.
2. **Test first, no production change:** tests 1 and 2 above. They turn Q6 item 1 from a reading into a measurement.
3. **Close the entry-class load.** In `EntryRep.entry()` (`EntryRep.java:846-849`) a code-free host should not call `CodebaseProvider.loadClass` with the wire `codebase`; with no primed class it should throw `UnusableEntryException` (data-only, fail closed). Make this a mode, not a default change, because existing clients rely on entry-class download.
4. **Fix priming of nested classes** (`EntryRep.java:820`): compare with the same name that was stored (`getName()`), or store both. Test 3.
5. **Move the stub's interfaces to a resident jar.** `OutriggerServer`, `OutriggerAdmin`, `EntryRep`, `MatchSetData`, `OutriggerQueryCookie` live only in `outrigger-dl`; a client library jar that carries them (plus `jgdms-der` for the codec, `EntryRep.java:321-335`) is what makes the dynamic stub's interfaces local. This is packaging, not logic.
6. **Make the verifier resident.** The client should use its own `TrustVerifier` for `ConstrainableSpaceProxy2` rather than the server-supplied `ProxyVerifier` (`IMPL:4468-4470`).
7. **Require `ATOMIC_DER` on the client side** (memo 11.2 residual): the host wrapper must set it, because enforcement exists only where a requirement was set. Outrigger sets it on the stub (`IMPL:872-874`) but a client can replace constraints (`ConstrainableSpaceProxy2.setConstraints`, `:442-448`, keeps the born format but not the wire constraint).
8. **Optional, larger:** replace `ParticipantProxy` and the pure-forwarding part of `AdminProxy` by a bare dynamic proxy (Q5); this is the only place sense A is cheap.

## Only the operator can settle (stop here)

- Which tier is wanted: sense A (no resident adapter, which needs a new wire interface using `EntryRep`-style records, like `RemoteRegistrar` in memo 3.8) or sense B (resident `outrigger-dl`). This changes the whole change list.
- Whether a deployed client already has `outrigger-dl` and `jgdms-der` on its classpath, and whether `Codebase_Annotation` (`IMPL:1164`) is set in the deployments you care about (a config file I did not locate).
- Whether the code-free host runs with a SecurityManager, passes `verify=true` and sets `discoveryConstraints` (memo 11.1's open questions; they decide whether Q6 item 5 is closed).
- Whether existing Outrigger clients depend on downloading entry classes (Q6 item 1). Removing it is a behaviour change.

## Not done

Nothing was compiled or run. Not read: the 24 JOSS providers question for Outrigger, `ProxyCodebaseSpi` providers other than the default, `LeaseFactory`'s body, the server's `EventSender` listener preparation, the Entry[] admin attribute types, exception classes on the wire, `AtomicDerInvocationHandler` use of `ResolutionContext` for Outrigger calls. No reviewer pass was done; per the dispatch rule a Sonnet report goes to Opus and Fable.
