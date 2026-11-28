# RESULTS: can client-side and server-side CEL remove code download from the lookup service?

Status: investigation result for the operator and the scoping session; nothing in it is a decision. Not committed.

## 1. Header


- Commit: JGDMS trunk 83c4d4c7af7bcb14bd33e07a68bbaa4ee83f36d3 (branch trunk), recorded by the coordinator with `git rev-parse HEAD` before dispatch. Date 2026-11-20.
- Coordinator and author of this file: Chisel (claude-opus-5-5, STD-011 owner). Sub-agent reports are RELAYED unless a line says the coordinator re-opened it (VERIFIED).
- Authors (model from the transcript's model field where marked "checked"; otherwise self-reported):

| Section | Model | Agent id | Helpers |
|---|---|---|---|
| A1 filters and templates | claude-sonnet-5-5 (self-reported) | a1218cb248b798e32 | none |
| A2 entry classes and fields | claude-sonnet-5-5 (self-reported) | aee733d50d27a241b | none |
| A3 class-load sites | claude-sonnet-5-5 (self-reported) | a772fd4bdf8bfe82c | none |
| B CEL as built | claude-sonnet-5-5 (self-reported); B.6 test run by the coordinator | a87e6071bc7c6e39b | none |
| C expressibility | claude-opus-5-5 (self-reported) | abd6278a52c8ed4b8 | none |
| D language additions | claude-fable-5-2 (checked) | ab652fcba9612c654 | a0af6e1857e41bdbd, a1667b3d2cdf15363 (Sonnet) |
| E API proposal + addendum E.1a | claude-fable-5-2 (checked) | a5afe1509e6a0978c | none |
| F blind review of D and E | claude-opus-5-5 (checked) | a158677848cc4d8cf | afb13a3c67fdeba6a, a3a25686766d50cfd (Sonnet) |


## 2. One-page answer


**Question 1: which lookup operations work in CEL as it stands?**
- Expressible today, with no language change (C; checked by D and F):
  - accept-all;
  - ServiceID equality;
  - equality on scalar entry fields, including the IEEE cases for floats;
  - numeric ranges;
  - and, or, not;
  - prefix, suffix and substring;
  - presence tests.
  This covers every filter and template the survey found in JGDMS and qa (A1). Real use is almost all equality, wildcard, service type and ServiceID.
- Expressible with a projection convention, not a language change:
  - service-type and entry-class tests, as marker fields tested with `has()`;
  - non-scalar fields compared as canonical DER bytes (D c2). F found that "canonical DER bytes" still needs a definition (F3) and that DER folds -0.0 into +0.0 (F4).
- Done by resident host code, not by one expression:
  - "for every template, some entry matches" over any number of entries (C, E.1a M2);
  - maxMatches, ordering, distinct values, comparing two items;
  - the three event transitions. These are computed from the predicate's verdict before and after a change, exactly as Reggie does (C section 3).
- Not a CEL job:
  - filters that read the service object or call the proxy (the four qa TestFilters);
  - proxy preparation and trust;
  - anything needing a clock or state (C section 4, E.8).

**Question 2: are additions needed, and are they safe?**
- No lookup need for any language addition was found (D).
- The operator-named candidates:
  - string concatenation: rejected for lookup. There is no use; a transform can return a list of parts for the client to join (H2).
  - glob with a literal pattern: safe, deferred. Its bound was confirmed by a probe and by F.
  - regex subset: rejected. About 15 automaton states fit the cost row.
  - ASCII case-insensitive compare: safe, deferred.
- Two limits sit in the current ceilings, not the language:
  - **Needle size.** At S = 65536, the total needle octets of `contains` per predicate is at most 15 (F9 corrects D's "per call"). A string needle of 15 octets or more for `in` over a list field is rejected.
  - **Cost profile.** A lookup service needs a lower cost profile. If it uses a different S, every evaluator must enforce the same S (F2).

**Where the risk is.** F found no HIGH issue. It did find eight MEDIUM issues in the API paths, not in the language:
- F1: exclusion fails open on a skipped entry.
- F2: a cost profile breaks the client re-check.
- F3: the DER bytes are not yet defined.
- F5: the client-side expression is not bound to a principal.
- F6: Derek's outbound listener call is a confused deputy.
- F7: registrants can read clients' predicates.
- F8: ServiceID ownership is not bound.

Each has a fix in F. Those fixes are preconditions for Derek's API, not reasons against it.


## 3. Sections A to F

### A1 Filters and templates

*Author: claude-sonnet-5-5 (self-reported), agent a1218cb248b798e32. Full report: J:\lookup-cel-2026-11-20\sections\A1_filters.md.*

Model id: claude-sonnet-5-5. Repo at 83c4d4c7a (git rev-parse --short HEAD, read only). Nothing was checked out, edited, built or written.

#### Totals

Search paths were `JGDMS` (the `JGDMS/JGDMS` module tree) and `qa`. `target/` hits were excluded from the ServiceTemplate counts; `target/` only holds copies of `ServiceTemplate.java`.

| Item | JGDMS main source | JGDMS tests | qa |
|---|---|---|---|
| Lines mentioning `ServiceItemFilter` (129 lines in 26 files) | 68 | 0 | 61 |
| Implementations of `ServiceItemFilter` | 1 (anonymous) | 0 | 4 (named) |
| Lambdas or method references passed as a filter | 0 | 0 | 0 |
| `new ServiceTemplate(` constructions (85 total) | 8 (7 code, 1 in a javadoc example) | 3 | 74 |

- **Mapping:** the SOW's "JGDMS tests" is the 3 constructions in `RealWireDerRoundTripTest` (JGDMS/services/reggie/reggie-dl/src/test); all 11 non-qa constructions come from this split.
- **Hits under the cap:** 85 constructions plus about 12 filter rows is under ~150. So the full table below covers JGDMS main and tests, and the qa constructions (74) are grouped by pattern.
- **Filter hits by kind:** of the 129 filter lines, about 80 are javadoc, imports or signatures. All are listed under the "other mentions" row.

#### Table 1a: Filter implementations and their callers (all VERIFIED)

| file:line | what it tests | operation labels | status |
|---|---|---|---|
| JGDMS/extra/src/main/java/org/apache/river/extra/discovery/MulticastServiceFinder.java:80-84 | Anonymous `ServiceItemFilter`; `check()` returns `true` for every item (default filter). | OTHER (accept-all, no test) | VERIFIED |
| qa/src/org/apache/river/test/spec/servicediscovery/AbstractBaseTest.java:255 (`TestFilter2`, check at :256-259) | Casts `item.service` to `TestService`, tests `i % 2 == 0` via `srvcValDivisibleByN`. | PROXY-CALL (reads the field `i` of the service object, not a method call), OTHER (cast to the service class; integer remainder test; null item returns false) | VERIFIED |
| qa/.../servicediscovery/AbstractBaseTest.java:263 (`TestFilter3`) | Same with `i % 3 == 0`. | PROXY-CALL (field read), OTHER (cast, remainder) | VERIFIED |
| qa/.../servicediscovery/AbstractBaseTest.java:273 (`TestFilter3Not2`) | `i % 2 != 0` and `i % 3 == 0`. | PROXY-CALL (field read), OTHER (cast, remainder, AND/NOT of two tests) | VERIFIED |
| qa/.../servicediscovery/AbstractBaseTest.java:282 (`TestFilter4`) | `i % 4 == 0`. | PROXY-CALL (field read), OTHER (cast, remainder) | VERIFIED |
| JGDMS/jgdms-lib/src/main/java/au/net/zeus/jgdms/discovery/ServiceDiscoveryManagerImpl.java:2022-2060 (`check(Object, ServiceItemFilter, ProxyPreparer)`) | Not an implementation. Builds a `ServiceItem` from a bootstrap proxy and calls `filter.check(item)` at :2047. If that throws `SecurityException` or `ClassCastException`, it downloads the service proxy and retries. | PROXY-CALL (`ServiceAttributesAccessor.getServiceAttributes`, `ServiceIDAccessor.serviceID`, `ServiceProxyAccessor.getServiceProxy`) | VERIFIED |
| JGDMS/jgdms-lib/src/main/java/au/net/zeus/jgdms/discovery/ServiceDiscoveryManagerImpl.java:2423-2431 (`filterPassed`) | Caller. Item or service null returns false; filter null returns true; otherwise `filter.check(item)`. | OTHER (filter invocation) | VERIFIED |
| JGDMS/jgdms-lib/src/main/java/au/net/zeus/jgdms/discovery/LookupCacheImpl.java:808-850 (`getServiceItems`, `FilteredItems.accept`) | Caller. Clones the item, applies `filter2.check` at :833. A null `service` after the check means "indefinite", and the item is discarded. | OTHER (filter invocation) | VERIFIED |
| JGDMS/jgdms-lib/src/main/java/au/net/zeus/jgdms/discovery/LookupCacheImpl.java:696 and :710 | `lookup(ServiceItemFilter)` and `lookup(ServiceItemFilter, int)` pass the filter to `getServiceItems`. | OTHER (pass-through) | VERIFIED |
| JGDMS/extra/src/main/java/org/apache/river/extra/discovery/MulticastServiceFinder.java:95-96 | Passes the caller's filter to `serviceDiscovery.lookup(template, 1, filter)`. | OTHER (pass-through) | VERIFIED |
| qa/.../servicediscovery/lookup/LookupMinEqualsMax.java:270-271 and lookup/LookupWait.java:152-153 | `firstStageFilter != null && firstStageFilter instanceof ServiceItemFilter`, then the expected-result list is adjusted with `srvcValOdd` (not opened further). | OTHER (test bookkeeping) | VERIFIED (lines only) |
| qa/.../servicediscovery/AbstractBaseTest.java:711-724 (`countSrvcsByVal`) | `instanceof TestFilter3Not2/2/3/4`, then counts with `i % modN`. | OTHER (test bookkeeping) | VERIFIED |
| qa filter factories: cache/CacheLookupFilterFilter.java:55 (`TestFilter3`) and :63 (`TestFilter3Not2`); cache/CacheLookupFilterNoFilter.java:52 (`TestFilter2`); cache/CacheLookupNoFilterFilter.java:51 (`TestFilter4`); lookup/LookupFilter.java:56, LookupMaxFilter.java:54, LookupMinEqualsMaxFilter.java:56, LookupMinLessMaxFilter.java:55, LookupMinMaxNoBlockFilter.java:62, LookupWaitFilter.java:52 (all `TestFilter2`) | Each overrides `getFirstStageFilter()` or `getSecondStageFilter()` and returns one of the four named filters. 10 lines. | same as the named filter | VERIFIED |
| qa/.../servicediscovery/AbstractBaseTest.java:531 and :539 | Base `getFirstStageFilter()` and `getSecondStageFilter()` return null. | WILDCARD (null = no filter) | VERIFIED |
| JGDMS/jgdms-lib-dl/src/main/java/au/net/zeus/jgdms/client/ServiceDiscoveryHelper.java:195 and :242 | Passes a null filter: `sdm.lookup(template, null, timeoutMs)` and `sdm.lookup(template, 1, maxServices, null, timeoutMs)`. | WILDCARD (null filter) | VERIFIED |
| JGDMS/services/codebase-downloader/codebase-downloader-service/src/main/java/au/net/zeus/jgdms/downloader/ActivatableCodebaseDownloaderImpl.java:303-306 | `createLookupCache(new ServiceTemplate(null,null,null), null, listener)`. | WILDCARD (null filter) | VERIFIED |
| Other mentions of `ServiceItemFilter` (about 80 lines): jgdms-lib-dl `ServiceItemFilter.java`, `LookupCache.java`, `ServiceDiscoveryManager.java`, `ServiceDiscoveryManagerSpi.java`; jgdms-lib `ServiceDiscoveryManagerImpl.java` and `LookupCacheImpl.java` (signatures, javadoc); qa `*/cache/CacheLookup*.java`, `*/lookup/Lookup*.java` (imports and javadoc) | Interface, signatures, javadoc and imports only; no test logic. | n/a | NOT CHECKED (grep hit only) |

#### Table 1b: ServiceTemplate constructions, JGDMS main and tests (all VERIFIED)

Paths are under JGDMS/. `ServiceTemplate` arguments are (serviceID, types, attrs).

| file:line | what it tests | operation labels | status |
|---|---|---|---|
| browser/src/main/java/org/apache/river/example/browser/Browser.java:324 | `(null, new Class[0], new Entry[0])`, an empty template that matches all. | WILDCARD | VERIFIED |
| browser/.../ServiceBrowser.java:125 | `(item.serviceID, {item.service.getClass()}, new Entry[]{})`, looks up one known item by id and class, max 10. | SERVICEID, SERVICE-TYPE | VERIFIED |
| browser/.../ServiceEditor.java:140 | Same arguments, used for an event-notify template. | SERVICEID, SERVICE-TYPE | VERIFIED |
| jgdms-lib/src/main/java/au/net/zeus/jgdms/discovery/ServiceDiscoveryManagerImpl.java:2105 | Default when the template is null: `(null, null, null)`. | WILDCARD | VERIFIED |
| jgdms-lib-dl/src/main/java/au/net/zeus/jgdms/client/ServiceDiscoveryHelper.java:193 | `(null, {serviceType}, null)`. | SERVICE-TYPE | VERIFIED |
| jgdms-lib-dl/.../ServiceDiscoveryHelper.java:240 | `(null, {serviceType}, null)`. | SERVICE-TYPE | VERIFIED |
| services/codebase-downloader/.../ActivatableCodebaseDownloaderImpl.java:304 | `(null, null, null)`. | WILDCARD | VERIFIED |
| services/hello-world/hello-world-api/.../HelloService.java:85 | Javadoc example `(null, {HelloService.class}, null)`. Not executable code. | SERVICE-TYPE | VERIFIED |
| services/reggie/reggie-dl/src/test/java/org/apache/river/reggie/proxy/RealWireDerRoundTripTest.java:252, :441, :472 | `(null, null, new Entry[]{ new MixedEntry("server1", null, null) })`. Entry class `MixedEntry` with the first field "server1" and the other two null. Wire-format round-trip test. | EQUALITY (field 1), WILDCARD (fields 2 and 3 are null), COLLECTION/ARRAY-FIELD (not confirmed that `MixedEntry` has an array field; NOT CHECKED) | VERIFIED (constructor lines) |

Fiddler `FiddlerImplDerFormatTest.java:243-249` only declares `ServiceTemplate` parameters; it constructs none. `extra/.../selfhealing/*` and `ServiceFinder.java` only pass templates through.

#### Table 2: qa ServiceTemplate constructions, grouped (74 total)

I opened every constructor line. The argument shape is VERIFIED. I did not open how the `Entry[]` contents are built, so field-level EQUALITY and WILDCARD content is NOT CHECKED, except in the two servicediscovery rows noted below.

| Pattern (id, types, attrs) | Count | Labels | Example locators | Status |
|---|---|---|---|---|
| `(serviceID, null, null)` | 21 | SERVICEID | qa/.../lookupservice/test_set00/LookupBySrvcID.java:74; qa/.../joinmanager `impl/joinmanager/LeaseRenewDurRFE.java:334`; qa/.../lookupservice/test_set04/ModifyAttributesOverlap.java:104 | VERIFIED |
| `(null, null, null)` | 9 | WILDCARD | qa/.../lookupservice/test_set01/EmptyLookup.java:66; qa/.../test_set02/LookupNegMatches.java:36; qa/.../test_set00/EvntLeaseExpiration.java:96 | VERIFIED |
| `(null, types, null)` | 10 | SERVICE-TYPE | qa/.../lookupservice/test_set00/LookupByClass.java:72; qa/src/org/apache/river/qa/harness/RunningServiceAdmin.java:145; qa/.../spec/servicediscovery/AbstractBaseTest.java:522 | VERIFIED |
| `(null, null, attrs)` | 11 | EQUALITY and WILDCARD (entry-field content NOT CHECKED); one is an empty array (`GetFieldValuesBadIndex.java:37`) = WILDCARD | qa/.../test_set01/ReturnOnLookupByAttr.java:124; qa/.../test_set01/GetServiceTypesAttr.java:116; qa/.../test_set02/GetFieldValuesBadIndex.java:37 | VERIFIED (shape) |
| `(null, types, attrs)` | 23 | SERVICE-TYPE, EQUALITY and WILDCARD (content NOT CHECKED) | qa/.../test_set01/NotifyOnComboSrvcReg.java:248 and :250 (18 of the 23 are in the NotifyOnCombo* files, two lines each); qa/.../test_set01/ReturnOnLookupByTypeAttr.java:128 | VERIFIED (shape) |

- **Servicediscovery attribute templates:** at `qa/.../servicediscovery/lookup/LookupFilter.java:130` and `event/ModifyAttrServiceChanged.java:370`, `attrs` is one `TestServiceIntAttr(int)`. That is EQUALITY on an int. I did not open `LookupWait.java:166` beyond its constructor line.
- **Lookups without a filter:** `joinmanager/AbstractBaseTest.java:381` and `servicediscovery/AbstractBaseTest.java:522` build `(null, {TestService}, null)`. Tests that supply a filter use that template and pass the filter separately (see the factory rows in Table 1a).

#### Commands run

Run from C:\Users\peter\Documents\GitHub\JGDMS in Git Bash. They can be rerun to recheck the counts.

```
git rev-parse --short HEAD
grep -rnE "ServiceItemFilter" --include=*.java JGDMS qa                      # 129 lines; add `| wc -l`
grep -rlE  "ServiceItemFilter" --include=*.java JGDMS qa | wc -l            # 26 files
grep -rnE "ServiceItemFilter" --include=*.java JGDMS qa | awk -F: '{print $1}' | sed -E 's#^(JGDMS|qa)/.*#\1#' | sort | uniq -c   # 68 JGDMS, 61 qa
grep -rnE "boolean +check *\( *(final +)?ServiceItem" --include=*.java JGDMS qa   # implementations: 1 anonymous + 4 named in qa (the 6th hit is the interface declaration)
grep -rnE "\.(lookup|createLookupCache)\(" --include=*.java JGDMS qa | grep -E "\->|::"   # 0 filter lambdas (2 unrelated RMI-registry javadoc hits in jgdms-pref-class-loader)
grep -rnE "new +ServiceTemplate *\(" --include=*.java JGDMS qa               # 85 non-target; 8 JGDMS main (7 code + 1 javadoc), 3 JGDMS test, 74 qa
grep -rnE "new +ServiceTemplate *\(" --include=*.java qa -A2                 # to read arguments
grep -rlE "ServiceItemFilter|ServiceTemplate" JGDMS qa --include=*.groovy --include=*.kt --include=*.scala --include=*.config --include=*.xml --include=*.properties --include=*.md --include=*.td --include=*.policy --include=*.html   # only docs, javadoc html and one package.html; no other source language
```

#### Limits (all NOT CHECKED)

- **No wildcard-import check:** the search was by the literal name `ServiceItemFilter`. An implementer using a wildcard import would still name the type in `implements` or `new`, so such a use would appear in the grep.
- **No indirect filters:** nothing was found that goes through a generic functional interface or a differently named class.
- **No multi-line construction check:** a `new` and `ServiceTemplate` split across lines (e.g. `new` at end of line) was not found. This was checked with an ad hoc multi-line grep whose output I did not re-inspect, so it is weaker evidence than the other searches.
- **Other templates not enumerated:** templates built through other APIs (for example the Reggie `Template` constructor, deserialization) were not enumerated.
- **Entry class and field contents** for the qa attribute arrays were not opened.


### A2 Entry classes and fields

*Author: claude-sonnet-5-5 (self-reported), agent aee733d50d27a241b. Full report: J:\lookup-cel-2026-11-20\sections\A2_entries.md.*

Model: claude-sonnet-5-5 (Sonnet 5.5). Section A2, item 3. Trunk 83c4d4c7a, read-only, nothing written or checked out.

NOTE ON LABELS: every row below is VERIFIED at declaration level only. I opened the `class` line and every `public` field line of each class through grep output with line numbers. I did not read every method body of every class. A few classes I read in more detail: SnapshotRep, FiddlerStatus, the two fiddler/mahalo/mercury TestAttr/TestEntry classes, Status, UIDescriptor, ServiceTypes, Attr00/01/02. Where a class has no row of its own fields, it declares none that are public, non-static and non-transient (and non-final unless noted).

Scalar means String, boxed primitives, enums. Everything else is NON-SCALAR, with the type named. `public final` fields are listed as "excluded final", per your definition.

====================================================================
1. MAIN SOURCE (JGDMS/**/src/main), 16 Entry classes, all VERIFIED
====================================================================
Base types, not counted as classes: `net.jini.core.entry.Entry` is an interface (jgdms-platform/.../Entry.java:77). `CloneableEntry` is an interface extending it (CloneableEntry.java:27). `SerialEntry` is an annotation (SerialEntry.java:192) and is not an Entry.

| # | FQN : file:line | Public non-static non-transient non-final fields |
|---|---|---|
| 1 | net.jini.entry.AbstractEntry : jgdms-lib-dl/src/main/java/net/jini/entry/AbstractEntry.java:42 (abstract, implements CloneableEntry) | none |
| 2 | net.jini.lookup.entry.Address : jgdms-lib-dl/.../net/jini/lookup/entry/Address.java:38 | street, organization, organizationalUnit, locality, stateOrProvince, postalCode, country: all String, SCALAR |
| 3 | net.jini.lookup.entry.Comment : Comment.java:33 | comment: String, SCALAR |
| 4 | net.jini.lookup.entry.Host : Host.java:28 | hostName: String, SCALAR |
| 5 | net.jini.lookup.entry.Location : Location.java:38 | floor, room, building: all String, SCALAR |
| 6 | net.jini.lookup.entry.Name : Name.java:34 | name: String, SCALAR |
| 7 | net.jini.lookup.entry.ServiceInfo : ServiceInfo.java:36 | name, manufacturer, vendor, version, model, serialNumber: all String, SCALAR |
| 8 | net.jini.lookup.entry.ServiceType : ServiceType.java:32 (abstract) | none |
| 9 | net.jini.lookup.entry.ServiceTypes : ServiceTypes.java:31 | serviceType: java.lang.Class, NON-SCALAR (Class object) |
| 10 | net.jini.lookup.entry.Status : Status.java:31 (abstract) | severity: net.jini.lookup.entry.StatusType, NON-SCALAR (plain class, `public class StatusType implements Serializable, Resolve`, StatusType.java:50, not an enum) |
| 11 | net.jini.lookup.entry.UIDescriptor : UIDescriptor.java:43 | role: String SCALAR; toolkit: String SCALAR; attributes: java.util.Set, NON-SCALAR (collection); factory: java.rmi.MarshalledObject, NON-SCALAR (other object) |
| 12 | net.jini.lookup.entry.jmx.JMXProperty : jmx/JMXProperty.java:28 | name, value: String, SCALAR |
| 13 | net.jini.lookup.entry.jmx.JMXProtocolType : jmx/JMXProtocolType.java:28 | protocolType: String, SCALAR (RMI/IIOP/JMXMP are static final, excluded) |
| 14 | org.apache.river.lookup.entry.BasicServiceType : jgdms-lib-dl/.../org/apache/river/lookup/entry/BasicServiceType.java:35 (extends ServiceType) | type: String, SCALAR (`bundle` and `inited` are transient, excluded) |
| 15 | org.apache.river.fiddler.FiddlerImpl.FiddlerStatus : services/fiddler/fiddler-service/src/main/java/org/apache/river/fiddler/FiddlerImpl.java:621 (private static class, extends Status) | none declared; inherits `severity` (StatusType, NON-SCALAR) from Status |
| 16 | org.apache.river.outrigger.proxy.SnapshotRep : services/outrigger/outrigger-dl/src/main/java/org/apache/river/outrigger/proxy/SnapshotRep.java:83 (package-private, `implements Entry` with `import net.jini.core.entry.Entry`) | none (only `private final EntryRep rep`) |

Main-source Entry classes with at least one NON-SCALAR field:
- Declaring a non-scalar field: 3, namely ServiceTypes (Class), Status (StatusType), UIDescriptor (Set, MarshalledObject).
- Also counting classes that inherit one: 4, adding FiddlerStatus (inherits Status.severity).

EXAMPLES (JGDMS/examples/wire-protocol-showcase). These are not in src/main; I report them separately and do not include them in the 16.
| FQN : file:line | Fields |
|---|---|
| au.net.zeus.jgdms.showcase.entry.SensorReadingEntry : demo2-match-without-the-class/src/au/net/zeus/jgdms/showcase/entry/SensorReadingEntry.java:31 | stationName String, measuredQuantity String, sequenceNumber Integer: all SCALAR |
| au.net.zeus.jgdms.showcase.pushdown.records.WeatherReading : demo7-filter-pushdown/src/.../records/WeatherReading.java:38 | temperatureCelsius Double, stationName String: SCALAR |
| ...records.NorthStationReading : NorthStationReading.java:32 (extends WeatherReading) | elevationMetres Integer: SCALAR |
Examples: 3 classes, 0 with a non-scalar field. All VERIFIED.

====================================================================
2. TESTS under JGDMS (src/test), 46 Entry classes, all VERIFIED
====================================================================
Paths below are relative to C:\Users\peter\Documents\GitHub\JGDMS\JGDMS.

jgdms-lib-dl/src/test/java:
- net.jini.entry.AbstractEntrySerialEntryTest.PlainEntry :53: host String SCALAR (finalIgnored is final, excluded).
- ...AbstractEntrySerialEntryTest.LocationEntry :63: host and floor are `public final`, so no counted fields (excluded final: String, Integer).
- net.jini.lookup.entry.StatusTypeDerEncodabilityTest.TestStatus :45: none declared; inherits Status.severity (StatusType, NON-SCALAR).
- org.apache.river.lookup.entry.LookupAttributesSerialEntryTest.LocationEntry :57 (final host String, final floor Integer; none counted); ServiceEntry :98 (final name, description, String; none counted); BrokenSerialEntry :131 (final value String; none counted); PlainEntry :149 (host String SCALAR).

services/*:
- fiddler FiddlerImplDerFormatTest.TestAttr :268 (fiddler-service/src/test/.../org/apache/river/fiddler/): `public final String value`; none counted.
- mahalo AtomicMarshalledInstanceDerMigrationTest.TestAttr :344: label String, value Integer: SCALAR.
- mercury DerFixtures.TestEntry :153: value String SCALAR.
- norm JoinStateAttributesDerFormatTest.TestAttr :59: value String SCALAR.
- outrigger-cel-authoring EntryFilterRoundTripTest.Doc :45: a, b String SCALAR.
- outrigger-cel-authoring EntrySchemaViewTest.Base :45: a, b String SCALAR. Sub :52 (extends Base): c String, n Long, SCALAR.
- outrigger-dl EntryRepDerFormatTest: Note :52 (text String); NumericEntry :67 (id Integer, seq Long, flag Boolean); SerialNumericEntry :86 (same three); BadFieldEntry :352 (value: EntryRepDerFormatTest.PlainValue, NON-SCALAR, other object, a Serializable with `public final int x`).
- outrigger-dl MatchSetWireSizeProbeTest: OrderEntry :89 (customer, product String; quantity, priority Integer); StampedOrderEntry :149 (customer String, quantity Integer, stamp: MatchSetWireSizeProbeTest.Stamp, NON-SCALAR other object).
- outrigger-dl OutriggerSerialEntryRoundTripTest: LocationEntry :45 and LocationEntryRenamed :92 are final fields only (none counted).
- outrigger-dl SnapshotRepAtomicSerialTest.Note :41: text String SCALAR.
- outrigger-service EntryFieldIndexTest: Base :42 (a, b String), Sub :50 (c String).
- outrigger-service EntryProjectionTest: Doc :60 (a, b String); Located :93 (where: EntryProjectionTest.Point, NON-SCALAR other object); HasColorEntry :433 (hc: EntryProjectionTest.HasColor, NON-SCALAR other object); Base :504 (base String); Derived :511 (derived String).
- outrigger-service FilterAdmissionTest: Doc :53 (a, b String); Other :65 (z String).
- outrigger-service FilterEvalTest: Reading :50 (temperatureCelsius Double, stationName String); DetailedReading :58 (sensorId String).
- outrigger-service FilteredWatcherRecoveryTest: Reading :77 (Double, String); Alert :87 (stationName, severity String).
- outrigger-service HandbackFormatRejectTest.TestEntry :77: value String.
- outrigger-service JoinStateManagerDerFormatTest.Attr :79: value String.
- reggie-dl EntryRepDerFormatTest: PayloadEntry :102 (payload: EntryRepDerFormatTest.Payload, NON-SCALAR other object); NumericEntry :119 and SerialNumericEntry :138 (Integer id, Long seq, Boolean flag).
- reggie-dl RealWireDerRoundTripTest.MixedEntry :141: active Boolean, count Integer, name String.
- reggie-dl SerialEntryRoundTripTest: LocationEntry :46, LocationEntryRenamed :94, LocationEntryDifferentType :135: all final fields only (none counted).
- reggie-service PayloadFormatGuardTest.Attr :66: name String.

Count by module group: lib-dl 7, fiddler 1, mahalo 1, mercury 1, norm 1, outrigger-cel-authoring 3, outrigger-dl 9, outrigger-service 15, reggie-dl 7, reggie-service 1. Total 46.

Test Entry classes with at least one NON-SCALAR field:
- Declaring one: 5. These are EntryRepDerFormatTest.BadFieldEntry (outrigger-dl), MatchSetWireSizeProbeTest.StampedOrderEntry (outrigger-dl), EntryProjectionTest.Located, EntryProjectionTest.HasColorEntry (outrigger-service), and EntryRepDerFormatTest.PayloadEntry (reggie-dl).
- Counting TestStatus (inherits severity): 6.

Looked at and not Entry classes: jgdms-der EntrySchemaGeneratorFieldOrderTest.Base/Sub and ClassEvolutionTest/StatelessSubclassRoundTripTest `Base` (plain classes, no Entry in the supertype chain, verified at the class line). Also the jgdms-benchmarks `Base`, the platform ProxySerializerInterfaceHintTest interfaces, Map.Entry implementers (AbstractIDLeaseMap, AbstractLeaseMap, ConsistentMapEntry, MapSerializer.Ent, ClientLeaseMapWrapper.Entry, jgdms-collections EntryFacadeConverter/ReferenceEntryFacade/MutableMap) and `org.apache.river.reggie.proxy.ServiceType` (reggie-dl main, `public class ServiceType {` with no supertype). I confirmed the Map.Entry files do not import net.jini.core.entry.Entry.

====================================================================
3. QA (C:\Users\peter\Documents\GitHub\JGDMS\qa), 54 Entry classes, all VERIFIED
====================================================================
Paths are relative to qa/src/org/apache/river/test.

impl/outrigger/api/AbstractEntryTest.java:
- NameAE :49 (extends AbstractEntry): name String SCALAR (`transient int ignored` excluded).
- FriendlyAE :62 (extends NameAE): friends java.util.HashMap, NON-SCALAR (map).
- NameE :73 (implements Entry directly): name String SCALAR (transient excluded).
- FriendlyE :86 (extends NameE): friends HashMap, NON-SCALAR (map).

impl/outrigger/javaspace05 (the javaspace05 files all say `extends AbstractEntry implements TestEntry`; TestEntry is not an Entry itself):
- A0 :29, B0 :29, C0 :29: id net.jini.id.Uuid, NON-SCALAR (other object, an interface); color String SCALAR.
- A00, A01 (extend A0); A000, A001 (extend A00); A010, A011 (extend A01); B00, B01 (extend B0): all :23, no own fields; they inherit A0.id or B0.id (Uuid) and color. That is 11 classes in total.

impl/outrigger/matching:
- LoneEntry :32 (implements net.jini.core.entry.Entry): originatingHostVM String; entryID Integer: SCALAR.
- NBEUniqueEntry :58 (implements Entry): originatingHostVM String, entryID Integer.
- NBEComplex :27 (extends NBEUniqueEntry): aString String, aFloat Float SCALAR; arrayOfShorts short[], NON-SCALAR (array).
- NBEEmpty :27: none.
- UniqueEntry :53 (extends net.jini.entry.AbstractEntry): originatingHostVM String, entryID Integer.
- IntegerMatch :27 (aInt Integer); ChildOfIntegerMatch :27 (none); GrandChildOfIntegerMatch :27 (none).
- RemoteMatch :27: aRI ARemoteInterface, NON-SCALAR (other object, remote interface).
- StringMatch :27 (aString String); ChildOfStringMatch :27 (aFloat Float); GrandChildOfStringMatch :27 (arrayOfShorts short[], NON-SCALAR array; `final float ANOTHERFIELD` at :58 is not public, excluded).
- UserDefMatch :27: aRUDO RandomUserDefinedClass, NON-SCALAR (other object).

impl/outrigger/transaction/SimpleEntry.java:30: string String, stage Integer, id Integer: SCALAR.

share:
- AttrOne :23 (aString String); AttrTwo :23 (aLong Long); SunOfAttrOne :23 (extends AttrOne; aLong Long, shadowing nothing in AttrOne); UninterestingEntry :20 (none).
- AttributesUtil.TestAttr00 :61 (val0, val1, val2 String); AttributesUtil.TestAttrSC00 :76 (val0, val1 String).

spec/javaspace/conformance/SimpleEntry.java:29 (implements Entry, Cloneable): name String, id Integer.

spec/lookupservice/attribute (Attr is an interface with `matches(Object)`, not an Entry):
- Attr00 :21 (implements Entry, Attr), Attr01 :20, Attr02 :21: no public fields.
- Attr03 :20 (extends Attr02), Attr04 :21, Attr05 :20 (extends Attr04), Attr06 :21, Attr07 :21, Attr08 :21, Attr09 :21, Attr10 :20 (extends Attr09), Attr11 :20 (extends Attr10), Attr12 :20 (extends Attr11): public fields are only Integer i*_NN, Boolean b*_NN, String s*_NN and Long l*_NN; all SCALAR.
- That is 13 classes in total, none with a non-scalar field.

spec/joinmanager/AbstractBaseTest.java:
- TestServiceIntAttr :164 (val Integer); TestServiceStringAttr :214 (val String); SlowTestServiceIntAttr :200 (slowAttr: AbstractBaseTest.SlowAttrField, NON-SCALAR other object, a Serializable); ServiceControlledAttr :246 (none).
spec/servicediscovery/AbstractBaseTest.java: TestServiceIntAttr :225 (val Integer).

Count: AbstractEntryTest 4, javaspace05 11, matching 13, transaction 1, share 6, conformance 1, lookupservice/attribute 13, joinmanager 4, servicediscovery 1. Total 54.

QA Entry classes with at least one NON-SCALAR field:
- Declaring one: 10. These are FriendlyAE, FriendlyE (HashMap); A0, B0, C0 (Uuid); NBEComplex, GrandChildOfStringMatch (short[]); RemoteMatch, UserDefMatch; SlowTestServiceIntAttr.
- Counting inheritors: 18, adding A00, A000, A001, A01, A010, A011, B00, B01 (inherited Uuid id).

====================================================================
4. TOTALS
====================================================================
| Population | Entry classes | With a non-scalar field (declared) | Including inherited |
|---|---|---|---|
| main (JGDMS/**/src/main) | 16 | 3 (ServiceTypes, Status, UIDescriptor) | 4 (+FiddlerStatus) |
| examples (JGDMS/examples) | 3 | 0 | 0 |
| JGDMS tests (src/test) | 46 | 5 | 6 (+TestStatus) |
| qa | 54 | 10 | 18 |
Overall across all of them: 119 Entry classes (16 + 3 + 46 + 54), 18 declaring a non-scalar field (3 + 0 + 5 + 10), 28 including inheritors (4 + 0 + 6 + 18).

Classes with only `public final` fields (counted as Entry classes, with zero counted fields): tests AbstractEntrySerialEntryTest.LocationEntry, LookupAttributesSerialEntryTest (LocationEntry, ServiceEntry, BrokenSerialEntry), FiddlerImplDerFormatTest.TestAttr, OutriggerSerialEntryRoundTripTest (LocationEntry, LocationEntryRenamed), SerialEntryRoundTripTest (LocationEntry, LocationEntryRenamed, LocationEntryDifferentType). Their excluded final fields are String, Integer and Long, all scalar, so the non-scalar counts above do not change either way.

====================================================================
5. COMMANDS USED (run from C:\Users\peter\Documents\GitHub\JGDMS in Git Bash). The same results were obtained at commit 83c4d4c7a.
====================================================================
Target and build copies are excluded with `--exclude-dir=target --exclude-dir=build`. Four `.java` files under target/ matched the Entry patterns; they are copies of the Map.Entry classes (and MapSerializer), not Entry classes. `export LC_ALL=C.UTF-8` is needed because `grep -P` fails in the POSIX locale.

```
export LC_ALL=C.UTF-8
# (a) roots: every declaration naming Entry/AbstractEntry/CloneableEntry/ServiceType in extends/implements (multi-line aware)
grep -rlPz --include=*.java --exclude-dir=target --exclude-dir=build '\b(class|interface)\s+\w+(<[^{;]*>)?\s+(extends|implements)\s+[^{;]*?(?<![.\w])(Entry|AbstractEntry|CloneableEntry|ServiceType)\b[^{;]*\{' JGDMS qa | sort
# (b) roots via fully qualified names
grep -rlPz --include=*.java --exclude-dir=target --exclude-dir=build '\b(class|interface)\s+\w+(<[^{;]*>)?\s+(extends|implements)\s+[^{;]*?(net\.jini\.core\.entry\.(Entry|CloneableEntry|SerialEntry)|net\.jini\.entry\.AbstractEntry|net\.jini\.lookup\.entry\.\w+)\b[^{;]*\{' JGDMS qa | sort
# (c) closure: repeat with a name list N of every Entry class found so far, until no new class appears (4 rounds were needed: roots, A00/Attr01.., A000/Attr11.., GrandChildOf*/Attr12; Status, ServiceType also grepped with `extends\s+(net\.jini\.lookup\.entry\.)?(Status|ServiceType|StatusType)\b`)
grep -rnPz -o --include=*.java --exclude-dir=target --exclude-dir=build "\b(class|interface)\s+\w+(<[^{;]*>)?\s+(extends|implements)\s+[^{;]*?\b($N)\b[^{;]*\{" JGDMS qa | tr '\0' '\n'
# (d) fields: per file, the class lines and the public non-paren lines
grep -nE "^\s*(public |protected |private )?(static |abstract |final )*(class|interface) \w+|^\s*public\s+[^(]*;" <file>
# (e) false-positive filter: implementers of "Entry" that do not import net.jini.core.entry.Entry (these are Map.Entry)
for f in $(grep -rlE --include=*.java --exclude-dir=target --exclude-dir=build "implements .*\bEntry\b" JGDMS qa); do grep -LE "net\.jini\.core\.entry\.(Entry|\*)" $f; done
```

Limits of this method:
- It is name-based and grep-based. The closure step matches simple class names, so two classes sharing a simple name (Base, Sub, Doc, Attr, SimpleEntry, TestEntry) were separated by opening the declarations.
- No compiler or reflection was used. An Entry class reached only through a supertype name that is absent from the N list would be missed. After the final round no new names appeared.
- Only `.java` files were searched. Other file types and generated sources were not.
- Anonymous `new Entry(...)` was searched: the only hits are `FunctionRegistry.Entry` constructor calls in jgdms-cel, not net.jini.core.entry.Entry.


### A3 Class-load sites

*Author: claude-sonnet-5-5 (self-reported), agent a772fd4bdf8bfe82c. Full report: J:\lookup-cel-2026-11-20\sections\A3_classload.md.*

Model: claude-sonnet-5-5. Commit 83c4d4c7a (git rev-parse --short HEAD in JGDMS/JGDMS). Read-only. Every row below is VERIFIED (I opened the line) unless marked NOT CHECKED.

Abbreviations. SDM = C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\jgdms-lib\src\main\java\au\net\zeus\jgdms\discovery\ServiceDiscoveryManagerImpl.java. LC = same directory, LookupCacheImpl.java. RD = C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\services\reggie\reggie-dl\src\main\java\org\apache\river\reggie\proxy. Real paths: EntryClass, EntryRep, Item, Registrar, RegistrarProxy, ServiceType, RegistrarEvent and Matches are all in RD. Outrigger has a separate EntryRep at services/outrigger/outrigger-dl/.../EntryRep.java (not on the lookup path, not opened).

#### 1. Memo locator check (design memo DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md)

| Memo line | Memo text | Verdict | Evidence |
|---|---|---|---|
| 367, 453 | B2: "the comment 'getServiceAttributes may try to download code' (ServiceDiscoveryManagerImpl.java:2034)" | CONFIRMED | SDM:2034 is `// getServiceAttributes may try to download code, the bootstrapPreparer`. The call is at SDM:2037: `((ServiceAttributesAccessor) preparedProxy).getServiceAttributes()`. |
| 453 | B2 names ServiceDiscoveryManagerImpl and LookupCacheImpl as locators | CONFIRMED | SDM:2034/2037 and LC:872, LC:1036 (see section 2). |
| 72, 142 | "ServiceDiscoveryManagerImpl.java:877-878" skips a registrar that is not `instanceof SafeServiceRegistrar` unless `useInsecureLookup()` | CONFIRMED | SDM:877 `if ( !useInsecureLookup() &&`; SDM:878 `!(proxys[i] instanceof SafeServiceRegistrar)) continue;`. |
| 73 | "legacy lookup/notify run only in insecure mode: SDM 1329, 1741, 2249" | CONFIRMED (line and branch) | SDM:1329 `proxy.lookup(tmpl, maxMatches)` is under `if (useInsecureLookup())`, with `lookUp` at 1332. SDM:1741 `proxy.lookup(tmpl, max)` is under the same test, with `lookUp` at 1744. SDM:2249 `proxy.notify(...)` is under `if (useInsecureLookup())`. The memo's "inside catch (Exception) blocks that discard the registrar" part: I did not check it. |
| 73 | "LookupCacheImpl.java:1276" | CONFIRMED | LC:1276 `proxy.lookup(tmpl, Integer.MAX_VALUE)` under `if (useInsecureLookup)` at 1275. |
| 63 | "EntryClass.java:277 CodebaseProvider.loadClass(codebase, name, null, integrity, null)" | CONFIRMED | RD/EntryClass.java:276-277 (`Class cls =` / `CodebaseProvider.loadClass(codebase, name, null, integrity, null);`) in `toClass(String codebase)`. |
| 86 | "EntryClass.java:94, 106" hash and numFields | CONFIRMED | RD/EntryClass.java:94 `protected final long hash;`; :106 `protected final int numFields;`. |
| 56 (B5) | "RegistrarProxy.java:186-194: the lookUp result filter" | CORRECTED to RD/RegistrarProxy.java:269-280 | Lines 186-194 are `check(GetArg)` and the constructor. The filter is in `lookUp(ServiceTemplate,int)` at 269-280: `RemoteMethodControl` (275), `TrustEquivalence` (276), `ServiceProxyAccessor` (277), `ServiceAttributesAccessor` (278), `Proxy.isProxyClass` (279). |
| 62 | "Registrar.java:52" for the internal Registrar taking Item, Template, EntryRep[] | CORRECTED to RD/Registrar.java:60, 71, 78, 80, 167, 176-177, 185 | Line 52 is `public interface Registrar`. Item is at 60 (`register(Item, long)`). Template is at 71, 78 and 80 (`lookup`, `lookUp`). EntryRep[] is at 167, 176-177 and 185. |
| 627 | "A null filter downloads the proxy on the main path (SDM, check, filter == null)" | CONFIRMED | SDM:2040-2043: `if (filter == null){ item.service = ((ServiceProxyAccessor) preparedProxy).getServiceProxy();`. |
| 627 | "the retry exists a second time in LookupCacheImpl" | CONFIRMED | LC:526, 549, 2113, 2133, 2146 (see section 2). |
| 456 | EntryRep.get failure semantics (field becomes null; handleException swallows StackOverflowError and OutOfMemoryError) | CONFIRMED | EntryRep.java:296-316 catches Throwable per field and calls `RegistrarProxy.handleException`. RegistrarProxy.java:~385-395 rethrows Errors except LinkageError, OutOfMemoryError and StackOverflowError (the latter two are not rethrown). |
| 60 | `Registrar.lookup` and `RegistrarProxy.lookup` Item/Template wire types | CONFIRMED | RegistrarProxy.java:248, 265, 272 build `new Template(tmpl, useDer())`. |

#### 2. Client-path class-load and unmarshal sites

Within one method row, the "call path" column names the chain that reaches the site.

| # | file:line | Method | What is loaded or unmarshalled | Call path | Status |
|---|---|---|---|---|---|
| 1 | jgdms-platform/.../org/apache/river/discovery/Plaintext.java:482-489 | `readV2UnicastResponse` | `AtomicMarshalInputStream.create(in, defaultLoader, verifyCodebaseIntegrity, verifierLoader, context, false).readObject(ServiceRegistrar.class)`. This unmarshals the registrar proxy, so the RegistrarProxy class and its dependencies load from the codebase. | `LookupLocator.getRegistrar` (LookupLocator.java:520-535) -> `MultiIPDiscovery.getResponse` -> `Discovery.doUnicastDiscovery` (DiscoveryV2.java:502) -> `UnicastDiscoveryClient.doUnicastDiscovery`. The provider clients are plaintext/Client.java:113-121 (passes `false` for integrity), https/Client.java:94 and ssl/sha{224,256,384,512}/Client.java (readUnicastResponse overrides). EndpointBasedClient.java:174-186 is the default (kerberos). Same path for unicast discovery via AbstractLookupDiscovery. | VERIFIED (Plaintext, plaintext/Client, EndpointBasedClient, https/Client:94, sha256/Client:101). Other ssl/shaNNN clients: NOT CHECKED beyond the grep hit. |
| 2 | RD/Item.java:381 | `Item.get()` | `service.get()` is `MarshalledWrapper.get()`, which unmarshals the service proxy with codebase download. Then `EntryRep.toEntry(attributeSets)` (Item.java:~388). | `ServiceRegistrar.lookup(ServiceTemplate,int)` -> `RegistrarProxy.lookup` (RD/RegistrarProxy.java:265) -> `Matches.get` (Matches.java:93) -> `Item.toServiceItem` (Item.java:~453) -> `Item.get`. Also `RegistrarEvent.getServiceItem` (RegistrarEvent.java:166). | VERIFIED |
| 3 | RD/Item.java:395-401 | `Item.get(Collection context)` | `service.get(null, null, context)` and `EntryRep.toEntry`. | `ConstrainableRegistrarEvent.getServiceItem` (ConstrainableRegistrarEvent.java:140-141) -> `Item.get(context)`. | VERIFIED |
| 4 | RD/Item.java:421-423 | `Item.getProxy()` | `bootstrapProxy.get()` unmarshals the bootstrap proxy. The result is kept only if it is a `java.lang.reflect.Proxy`. | `Matches.getProxys` (Matches.java:96-108); `RegistrarEvent.getBootstrapProxy` (RegistrarEvent.java:188-195) and `getServiceItem` (:165, 176). The `Matches.getProxys` caller is NOT CHECKED (RegistrarImpl-side; the memo says `lookUp` fulfils it server-side). | VERIFIED (lines); caller chain for `getProxys` NOT CHECKED. |
| 5 | RD/EntryRep.java:288-289 | `EntryRep.get()` | `eclass.toClass(codebase)`, which loads the entry class from the codebase annotation. | `Item.get` -> `EntryRep.toEntry` (EntryRep.java:436-441) -> `EntryRep.get`. | VERIFIED |
| 6 | RD/EntryClass.java:274-277 | `EntryClass.toClass(String codebase)` | `CodebaseProvider.loadClass(codebase, name, null, integrity, null)`. | From EntryRep.java:289. Also `EntryClassBase.toClass(EntryClassBase[])` (called from `RegistrarProxy.getEntryClasses`, RegistrarProxy.java:~300-305). The EntryClassBase body is NOT CHECKED. | VERIFIED (EntryClass); EntryClassBase NOT CHECKED. |
| 7 | RD/EntryRep.java:301 | `EntryRep.get()` field loop | `((MarshalledInstance) val).get(integrity)` unmarshals each entry field value. | After site 5. | VERIFIED |
| 8 | RD/EntryRep.java:338-340 | `getViaSerialEntry` | `((MarshalledInstance) val).get(integrity)` for each wire field of a `@SerialEntry` class, after `clazz.getMethod("entryForm")` (:~333) and the `(GetEntryArg)` constructor. | After site 5 when `clazz.isAnnotationPresent(SerialEntry.class)` (EntryRep.java:290). | VERIFIED |
| 9 | RD/ServiceType.java:286-299 | `ServiceType.toClass(String codebase)` | `CodebaseProvider.loadClass(...)` (:290) for a class name, or `CodebaseProvider.loadProxyClass(...)` (:298) when the name starts with `;`. | `ServiceTypeBase.toClass(ServiceTypeBase[])` (ServiceTypeBase.java:82-96, calling `stype.type.toClass(stype.codebase)` at :90) <- `RegistrarProxy.getServiceTypes` (RegistrarProxy.java:~375-380). | VERIFIED |
| 10 | RD/RegistrarProxy.java:~340-355 (`values[i] = ((MarshalledWrapper) values[i]).get()`) | `getFieldValues` | Unmarshals each returned field value. It also calls `ClassMapper.getFields(tmpl.attributeSetTemplates[setIndex].getClass())` first (local class). | `ServiceRegistrar.getFieldValues` -> `RegistrarProxy.getFieldValues`. | VERIFIED. The exact line is NOT pinned in my notes (the block starts near the "unmarshal each value" comment in `getFieldValues`). |
| 11 | RD/RegistrarProxy.java:~253-256 | `lookup(ServiceTemplate)` (single-object legacy) | `wrapper.get()` unmarshals the service. | `ServiceRegistrar.lookup(ServiceTemplate)` -> `RegistrarProxy.lookup`. | VERIFIED |
| 12 | RD/RegistrarProxy.java:269-282 | `lookUp(ServiceTemplate,int)` | `server.lookUp(...)` returns `Object[]` of already-unmarshalled bootstrap proxies. They are filtered only by `instanceof` checks and `Proxy.isProxyClass` (the memo's B5 filter). Unmarshalling happens in the RMI/JERI return path (not opened). | `SafeServiceRegistrar.lookUp` <- SDM:1332, SDM:1744, LC:1279. | VERIFIED (the filter). The unmarshal mechanism is NOT CHECKED. |
| 13 | jgdms-lib-dl/.../org/apache/river/proxy/MarshalledWrapper.java:205, 239, 275 | `get()`, `get(ClassLoader,...)`, `get(..., context)` | `MarshalledInstance.get(...)` (JOSS or DER per `MarshalledInstance`). | Called from sites 2, 3, 4, 11. | VERIFIED |
| 14 | SDM:2034-2037 | `check(Object bootstrapProxy, ServiceItemFilter, ProxyPreparer)` | `bootstrapPreparer.prepareProxy(bootstrapProxy)` (:2033), then `getServiceAttributes()` returns `Entry[]`. The memo's comment says this may download code. | `ServiceDiscoveryManagerImpl.lookup` -> `check` at SDM:2004 (single-item lookup); `lookup(tmpl, maxMatches, filter)` -> `check` at SDM:1767. | VERIFIED |
| 15 | SDM:2040-2043 | `check`, `filter == null` | `((ServiceProxyAccessor) preparedProxy).getServiceProxy()` downloads and unmarshals the service proxy. | From site 14. | VERIFIED |
| 16 | SDM:2052-2064 | `check`, `catch (SecurityException)` | Retry `getServiceProxy()` (:2063) then `filter.check(item)`. | From site 14. | VERIFIED |
| 17 | SDM:2066-2079 | `check`, `catch (ClassCastException)` | Retry `getServiceProxy()` (:2078) then `filter.check(item)`. | From site 14. | VERIFIED |
| 18 | SDM:881 | discovery listener | `registrarPreparer.prepareProxy(proxys[i])` on the registrar proxy from unicast discovery (site 1). | `DiscoveryListener` callback. | VERIFIED |
| 19 | SDM:2263 | notify registration | `eventLeasePreparer.prepareProxy(eventLease)` prepares the event-registration lease proxy. This unmarshals nothing itself. | Insecure/secure `notify` branch. | VERIFIED (line) |
| 20 | LC:871-872 | `processBootStrapProxys(Object[])` | `sdm.bootstrapProxyPreparer.prepareProxy(proxys[i])`, then `((ServiceAttributesAccessor) bootstrap).getServiceAttributes()`. | `LookupCacheImpl` registrar-add path using `lookUp` results (LC:1279 `lookUp` call; the call from there to `processBootStrapProxys` was not traced). | VERIFIED (lines); call-path link to 1279 NOT CHECKED. |
| 21 | LC:1018 | `HandleServiceEventTask.run`, insecure branch | `theEvent.getServiceItem()` -> `RegistrarEvent.getServiceItem` -> `Item.get` (sites 2/3). | `RemoteEvent` callback via `notify`. | VERIFIED |
| 22 | LC:1025-1036 | `HandleServiceEventTask.run`, secure branch | `theEvent.getBootstrapProxy()` (site 4), `bootstrapProxyPreparer.prepareProxy(proxy)` (:1029), then `((ServiceAttributesAccessor) proxy).getServiceAttributes()` (:1036). | Same event path. | VERIFIED |
| 23 | LC:526, 549 | `ServiceDiscoveryTask`, `catch (SecurityException)` / `catch (ClassCastException)` | `((ServiceProxyAccessor) filteredItem.service).getServiceProxy()` downloads the service proxy, then re-filters. | Registrar-event or `lookUp` item processing in `LookupCacheImpl`. | VERIFIED |
| 24 | LC:2113, 2133, 2146 | `ItemTask`/filter task, `filter == null`, and SecurityException and ClassCastException retries | `getServiceProxy()` downloads the service proxy. At 2113 it is the null-filter case. At 2133 and 2146 it is the retry after the filter throws. | Same family as site 23. | VERIFIED |
| 25 | LC:1724-1725 | item equality helper | `new MarshalledInstance(service0)` and `service1`, then `fullyEquals`. This marshals (no `.get`). Included because it is a MarshalledInstance use on the client path. | Item-change comparison. | VERIFIED |
| 26 | LC:1271-1279 | registrar query | `proxy.lookup(...)` (insecure, :1276) or `((SafeServiceRegistrar) proxy).lookUp(...)` (:1279). See sites 2/12. | `LookupCacheImpl.cache.lookup(reg)` (LC:322). | VERIFIED |
| 27 | SDM:1329-1332, 1741-1744, 2249 | `lookup` / `notify` | The same `lookup` (insecure) or `lookUp` / `notiFy` (secure) selection. See sites 2/12. | `ServiceDiscoveryManager.lookup(...)` and `createLookupCache`. | VERIFIED |
| 28 | jgdms-lib-dl/.../org/apache/river/proxy/CodebaseProvider.java:45-58, 61-74 | `loadClass`, `loadProxyClass` | `ClassLoading.loadClass` / `ClassLoading.loadProxyClass`; on `NoSuchMethodError` falls back to `RMIClassLoader.loadClass` / `loadProxyClass`. `getClassAnnotation` is at :38 / :42. This is the single class-load funnel for sites 6 and 9. The `ClassLoading` class body itself is NOT CHECKED. | From sites 6 and 9. | VERIFIED |
| 29 | services/reggie/reggie-service/.../RegistrarImpl.java:742 (`getServiceAttributes`), :3456 (`getServiceProxy`) | Server-side implementations of `ServiceAttributesAccessor` / `ServiceProxyAccessor` for Reggie itself | Return values for the accessors (server side, not a client load). Reggie is also a registrant. Listed only to locate the implementation. | n/a | VERIFIED (lines) |
| 30 | jgdms-lib-dl/.../org/apache/river/proxy/Bootstrap.java:85 | `Bootstrap.create` | `new ObjectInputStream(...).readObject()` on the service's own serialised bytes (local, server side, creating a bootstrap proxy). Not a client lookup path. | Service-side registration. | VERIFIED (line); classification (server-side) INFERRED from `create` being a creation helper. |

#### 3. Not found / not checked
- Multicast announcements: the discovery path carries no proxy (the registrar is obtained by site 1). NOT CHECKED in `DiscoveryV2` decoders (DiscoveryV2.java:232-367 read only).
- `UnicastResponse` (jgdms-platform/.../org/apache/river/discovery/UnicastResponse.java) construction: a plain holder; NOT CHECKED beyond the class header.
- `ClassLoading` implementation, `AtomicMarshalInputStream.readObject` internals, `MarshalledInstance.get`: NOT CHECKED (funnels called from the sites above).
- qa tree and other callers of these APIs (browser, Fiddler, Mercury, UnicastServiceFinder): NOT CHECKED, outside the SDM/LC client path.
- Ground-truth probes of the memo's quote at line 56 ("RegistrarProxy.java:186-194") and line 62 ("Registrar.java:52") are the two CORRECTED items; all other memo locators are CONFIRMED.


### B CEL as built

*Author: claude-sonnet-5-5 (self-reported), agent a87e6071bc7c6e39b; B.6 test run by the coordinator. Full report: J:\lookup-cel-2026-11-20\sections\B_inventory.md.*

Model: claude-sonnet-5-5. Trunk 83c4d4c7a (git log -1, VERIFIED). Read-only, nothing run or edited. Paths below are relative to C:\Users\peter\Documents\GitHub\JGDMS\JGDMS. B.6 skipped as instructed.

HEADLINE. The code implements STD-011 v0.1 only. The v0.2 bindings (`REF` node, `maxBindings`, `maxEffectiveDepth`, `maxExpandedCost`, per-binding table) are absent. Of 17 spec node kinds, 16 are in code (`REF` is not). `maxScalarBytes` is enforced at decode on expression literals only, not on candidate fields, in jgdms-cel itself. Outrigger bounds candidate bytes in aggregate, per candidate, instead.

SPEC SOURCE (VERIFIED). STD-011 section 7.2 is at lines 1073-1134, 10.3 at 1500-1570, 11.1 at 1644-1700 of docs/JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md.

1. NODE INVENTORY AND FUNCTION REGISTRY

Node kinds. Code locator for the node types is jgdms-cel/src/main/java/au/net/zeus/jgdms/cel/ast/ExprNode.java ("public sealed interface ExprNode", line 37). Decoder tag dispatch is wire/CelDecoder.java lines 166-184 ("case 0: return decodeLitBool" through "case 26: return decodeCallBody"), with the tag range check at lines 158-162 ("peeked.tagNumber() > 26"). The evaluator and CompiledGraph switch over the same sealed type. VERIFIED.

| Spec item (11.1) | Code locator | Status |
|---|---|---|
| LIT_BOOL, LIT_INT, LIT_DOUBLE, LIT_STRING, LIT_BYTES, LIT_NULL | ExprNode.java:41,43,46,49,56,69; tags 0-5 at CelDecoder:166-173 | MATCH |
| LIST_LIT | ExprNode:74 `ListLit`; tag 6; count check at CelDecoder:249 | MATCH |
| FIELD_REF | ExprNode:83; tag 7 | MATCH |
| HAS | ExprNode:92; tag 8 | MATCH |
| NOT, NEG | ExprNode:100,104; tags 9,10 | MATCH |
| AND, OR | ExprNode:110,111; tags 11,12 | MATCH |
| EQ, NE | ExprNode:112,113; tags 13,14 | MATCH |
| LT, LE, GT, GE | ExprNode:114-117; tags 15-18 | MATCH |
| ADD, SUB, MUL, DIV, MOD | ExprNode:118-122; tags 19-23 (CelDecoder:346-358) | MATCH |
| IN | ExprNode:126 with `InListOperand` LiteralList/FieldList (168-172); tag 24 | MATCH |
| COND | ExprNode:133; tag 25 | MATCH |
| CALL | ExprNode:145 `Call(int functionId, List arguments)`; tag 26 | MATCH |
| REF (v0.2) | No such record in ExprNode.java. The decoder range is 0..26, so there is no tag for it. `grep -i 'binding'` in jgdms-cel/src/main and jgdms-cel-authoring/src/main finds no binding code. `cel.bind` is absent from the parser. | SPEC-ONLY |
| Expression carrier (binding list plus main body) | wire/CelFilterRecord.java:1 is `record CelFilterRecord(int formatVersion, EvaluationContext context, ExprNode expression)`, with no binding list | SPEC-ONLY |
| (code node not in spec) | none found; the sealed interface is the closed set | no CODE-ONLY |

Function registry. wire/FunctionRegistry.java:56-81 (TABLE, ids 1-24) and the Name enum at 44-52. VERIFIED.

| Spec item (7.2) | Code | Status |
|---|---|---|
| 1 size(string) | id 1 SIZE_STRING | MATCH |
| 2 size(bytes) | id 2 | MATCH |
| 3 size(list) | id 3 | MATCH |
| 4 int(double) | id 4 | MATCH |
| 5 double(int) | id 5 | MATCH |
| 6, 7 abs(int), abs(double) | ids 6, 7 | MATCH |
| 8 min/max (int, double) | ids 8, 9 (MIN_INT, MIN_DOUBLE), 10, 11 (MAX_INT, MAX_DOUBLE) | MATCH. The spec row bundles four wire ids, as the spec text itself notes. |
| 9 sqrt | id 12 | MATCH |
| 10, 11 radians, degrees | ids 13, 14 | MATCH |
| 12 sin, cos, tan | ids 15, 16, 17 | MATCH |
| 13 asin, acos | ids 18, 19 | MATCH |
| 14 atan | id 20 | MATCH |
| 15 atan2 | id 21 (arity 2) | MATCH |
| contains, startsWith, endsWith (6.6; not numbered in 7.2) | ids 22, 23, 24, arity 2 (FunctionRegistry.java:78-80) | MATCH with spec text at 7.2 ("ids 22-24"), which defers the numbering to Appendix B section B.8.3 |
| has, field (special forms) | Not in the registry. `HAS` is a node. | MATCH |
| pow, exp, log, hypot, floor, ceil, round, pi | absent from the table | MATCH (deliberately absent) |

Lookup is a real table lookup: `byId` returns null for an unassigned id, per the javadoc at lines 22-34. No CODE-ONLY functions.

2. EVALUATOR ENTRY POINTS, CandidateProjection, IMPLEMENTATIONS

Entry points (all VERIFIED):
- `public final class Evaluator`, jgdms-cel/src/main/java/au/net/zeus/jgdms/cel/eval/Evaluator.java:73. Constructors at 112 (`Evaluator()`) and 116 (`Evaluator(MathProvider)`). The only evaluation method is `public EvalOutcome evaluate(ExprNode node, CandidateProjection candidate)` at line 130. This is the tree-walking reference evaluator.
- `public final class CompiledExpression`, eval/CompiledExpression.java:50. `public static CompiledExpression compile(ExprNode root, MathProvider math)` at line 80. `public Session newSession()` at 98. Inner `public final class Session` at 134 with `public EvalOutcome evaluate(CandidateProjection candidate)` at 147. A Session is one thread only (the assert at line 148).
- CompiledGraph (eval/CompiledGraph.java:51) and CompiledNodes are package-private.
- Related public gates: `CelDecoder.decode(byte[])` (wire/CelDecoder.java:74), `CelVerifier.verify(byte[])` and `verify(byte[], SchemaView)` (verifier/CelVerifier.java:128, 149), `CostModel.computeCost(ExprNode)` (cost/CostModel.java:103).
- "FilterEval" exists once: services/outrigger/outrigger-service/src/main/java/org/apache/river/outrigger/FilterEval.java. It creates `new Evaluator()` per candidate at line 267 and calls `evaluator.evaluate(f.expr(), projection)` at 269.

CandidateProjection (eval/CandidateProjection.java:46) has four methods, all VERIFIED:
- `boolean isUndecodable()` (line 57)
- `List<String> namespaceChain()` (66)
- `boolean declaresField(String className, String fieldName)` (75)
- `CelValue fieldValue(String className, String fieldName)` (114). It carries a normative reference-stability precondition and a well-formed-UTF-16 precondition.

Implementations in the whole repository (Grep over `*.java` under C:\Users\peter\Documents\GitHub\JGDMS; all lines VERIFIED). Production: 2 (3 counting a showcase). Test or example: 4.
1. services/outrigger/outrigger-service/src/main/java/org/apache/river/outrigger/EntryProjection.java:126, `public final class EntryProjection implements CandidateProjection`. This is the reference implementation (production).
2. The same file, line 829, nested `private static final class FieldMapProjection implements CandidateProjection` (production).
3. jgdms-benchmarks/cel/src/main/java/au/net/zeus/jgdms/bench/cel/RecordProjection.java:40 (benchmark module).
4. examples/wire-protocol-showcase/demo5-filter-by-a-rule/src/au/net/zeus/jgdms/showcase/rule/Reader.java:331, `private static final class Candidate` (example).
5. examples/wire-protocol-showcase/src/test/java/au/net/zeus/jgdms/showcase/FilterByARuleTest.java:234, `private record Proj` (test).
6. jgdms-cel/src/test/.../testsupport/FakeCandidateProjection.java:33 (test).
7. jgdms-cel/src/test/.../eval/GenProjection.java:38 (test).
8. jgdms-cel/src/test/.../conformance/PureCandidateProjection.java:36 (test).

I opened only the declaration lines of these, not their bodies. The wt-b3 worktree at C:\Users\peter\Documents\GitHub\wt-b3 was not searched.

3. SECTION 10.3 CEILINGS

Constants live in jgdms-cel/src/main/java/au/net/zeus/jgdms/cel/wire/CelCeilings.java (VERIFIED, lines 35-64).

| Ceiling (spec 10.3) | Code value | Enforced where | Status |
|---|---|---|---|
| maxExprNodes 1024 | MAX_EXPR_NODES = 1024 (CelCeilings:35) | Decode: CelDecoder.java:118-119 `if (s.nodeCount > CelCeilings.MAX_EXPR_NODES) reject(...)` in `consumeNode`, called at line ~162 | ENFORCED at decode. The count is per decode and has no binding bodies. |
| maxExprDepth 32 | MAX_EXPR_DEPTH = 32 (CelCeilings:38) | Decode: CelDecoder.java:125-126 `if (s.depth > CelCeilings.MAX_EXPR_DEPTH) reject(...)` in `enterDepth` | ENFORCED at decode |
| maxSelectorSteps 16 | MAX_SELECTOR_STEPS = 16 (CelCeilings:41) | Decode: CelDecoder.java:273-274 `if (local > CelCeilings.MAX_SELECTOR_STEPS) reject(...)` | ENFORCED at decode |
| maxScalarBytes 65536 | MAX_SCALAR_BYTES = ObjectCodec.MAX_COLLECTION (65536; CelCeilings:52; jgdms-der ObjectCodec.java:165) | See below | PARTIAL |
| maxExprCost 1,000,000 | MAX_EXPR_COST = 1_000_000L (CelCeilings:64) | Verify: CostModel.java:105 `if (total > CelCeilings.MAX_EXPR_COST) throw new CostExceededException`. Called from CelVerifier.java:162 `cost = CostModel.computeCost(record.expression())`. | ENFORCED at verify only. CelDecoder does not call it. The CelVerifier javadoc (lines 73-80) says so. Evaluation has no runtime cost gate, which is the design (spec 10.5). |
| maxBindings 256 | none | none | NOT ENFORCED, because bindings do not exist in code |
| maxEffectiveDepth 64 | none | none | NOT ENFORCED (no bindings) |
| maxExpandedCost 2^23 | none | none | NOT ENFORCED (no bindings) |
| maxCollection (STD-006, reused for LIST_LIT) | MAX_COLLECTION (CelCeilings:55) | Decode: CelDecoder.java:249-250 `if (count > CelCeilings.MAX_COLLECTION) reject("ListLitNode exceeds maxCollection")` | ENFORCED at decode. This is not a 10.3 row; it is listed for completeness. |

maxScalarBytes in detail:
- Expression literals, at decode: ENFORCED. CelDecoder.java:169 `new ExprNode.LitString(decodeUtf8Raw(r, len, 0, CelCeilings.MAX_SCALAR_BYTES, "LIT_STRING"))` and line 170 `new ExprNode.LitBytes(decodeBytesRaw(r, len, 0, CelCeilings.MAX_SCALAR_BYTES, "LIT_BYTES"))`. The check is at lines 559-562 and 567-570: `if (len < minLen || len > maxLen) reject(what + ": length ... outside [...]")`. This check runs before the content is read.
- Authoring side, also on literals: jgdms-cel-authoring/.../CelAstValidator.java:208-209 `if (len > CelCeilings.MAX_SCALAR_BYTES) reject(... "exceed maxScalarBytes")`. CelTextParser.java:343-344 (string literal) and 397-398 (bytes literal) do the same.
- Candidate projected scalar fields, in jgdms-cel: NOT ENFORCED. The only non-test uses of `MAX_SCALAR_BYTES` in jgdms-cel are CostModel.java:93 (`private static final long S = CelCeilings.MAX_SCALAR_BYTES`), the literal checks above, and the constant itself. The Evaluator (eval/Evaluator.java:647, `str.codePointCount(0, str.length())`) and CompiledExpression have no length check. CandidateProjection.java (lines 103-112) states only a well-formedness precondition on implementations and says it is "not enforced at runtime by the evaluator". It states no length precondition.
- Candidate side in Outrigger (not a per-scalar check): EntryProjection.java:151 `public static final int MAX_PROJECTION_DECODE_BYTES = 64 * 1024;`. Its javadoc (lines 145-149) says it is "deliberately equal to CostModel.MAX_SCALAR_BYTES". Enforcement is at lines 516 `final long[] budgetRemaining = { MAX_PROJECTION_DECODE_BYTES };` and 735-738 `budgetRemaining[0] -= payload.length; if (budgetRemaining[0] < 0) { throw new ProjectionBudgetExceeded(); }`. This is a per-candidate aggregate budget over the referenced fields' slice payload bytes. It fail-closes the candidate (the projection becomes undecodable and is counted as `filter.rejected.projectionBudget`). It therefore bounds every single scalar to at most 64 KiB of payload octets, but only on the Outrigger path. I did not open the scalar decoder `EntryRepV2Codec.decodeScalarSliceValueClassFree` to see whether it has its own per-string limit (NOT CHECKED). Any other CandidateProjection implementation, including a client-side one, has no jgdms-cel-level enforcement.
- Verdict: literals only at decode in jgdms-cel, with candidate fields bounded in aggregate by Outrigger's EntryProjection. STD-011 10.3 requires "both" (candidate scalar fields and literals). Whether the Outrigger aggregate budget counts as the candidate-side enforcement is a ruling for the coordinator. Note also that the budget bounds payload octets, while `S` in the cost model is in UTF-8 octets, so the two are consistent in units.

4. EVALUATION OVER SOMETHING OTHER THAN A STORED CANDIDATE ENTRY

Callers of the evaluator entry points, outside jgdms-cel tests (VERIFIED by grep):
- services/outrigger/outrigger-service/.../FilterEval.java:267-269. This evaluates over `projection`, an `EntryProjection` built from the stored entry. Outrigger also uses `CelVerifier.verify` at FilterAdmission.java:330-331 and holds CompiledFilter. I did not open how the projection arises for templates, so FieldMapProjection (EntryProjection.java:829) is described only from its declaration.
- examples/wire-protocol-showcase/demo5-filter-by-a-rule/.../Reader.java: `new Evaluator()` at 138, `evaluator.evaluate(expr, c)` at 146, plus lines 206, 225-229, 260 and 406. The projection is a `Candidate` built by `readCandidate(DataInputStream)` (line 311) from a field map read from a stream, so it is a client-side, example-local record, not a stored entry. Line 406 `evaluator.evaluate(transform, c)` evaluates a transform over the `survey` candidate. This is an example and the only non-Outrigger evaluation site found in production-style code.
- jgdms-benchmarks/cel/.../CelRunner.java:41,49 (`new Evaluator(math)`, `evaluator.evaluate(root, projection)`) and CompiledRunner.java:37,44 (`CompiledExpression.compile(root, math).newSession()`, `session.evaluate(projection)`), driven by CelEvalBenchmark over `RecordProjection` (a benchmark record).
- Tests: the many `*Test.java` in jgdms-cel and the showcase FilterByARuleTest (a `Proj` record), the Outrigger FilterEvalTest, and similar. Not itemised.
- No code evaluates over call arguments, a template, or a ServiceItem. Nothing outside Outrigger, the benchmarks and the showcase calls the evaluator. Search was by name `Evaluator(`, `.evaluate(`, `newSession(`, `CompiledExpression.compile`, `CelDecoder.decode`, `CelVerifier.verify` in services, examples, jgdms-benchmarks and jgdms-cel-authoring main. I did not search the repository-level `qa` directory or the other JGDMS modules by this pattern (NOT CHECKED), though the whole-repo CandidateProjection search in item 2 found nothing there.

5. MODULE DEPENDENCIES (pom.xml, VERIFIED)
- jgdms-cel/pom.xml: compile dependencies jgdms-platform (line 52) and jgdms-der (line 57). Test: junit-jupiter (62-64). Parent `au.net.zeus:jgdms`.
- jgdms-cel-authoring/pom.xml: compile dependencies jgdms-cel (line 50) and jgdms-der (55). Test: junit-jupiter.
- Modules that name jgdms-cel or jgdms-cel-authoring (grep of every pom.xml with "jgdms-cel"):
  - pom.xml (root): `<module>jgdms-cel</module>` and `<module>jgdms-cel-authoring</module>`, lines 65-66.
  - services/outrigger/outrigger-service/pom.xml: jgdms-cel (line 107); jgdms-cel-authoring (line 115) with `<scope>test</scope>` (line 117). The comment at lines 98-99 says outrigger-service targets release 21 and jgdms-cel is release 25.
  - services/outrigger/outrigger-cel-authoring/pom.xml: jgdms-cel-authoring (line 54) and jgdms-cel (line 63).
  - jgdms-benchmarks/cel/pom.xml: jgdms-cel (49) and jgdms-cel-authoring (54).
  - examples/wire-protocol-showcase/pom.xml: jgdms-cel (line 62) with `<scope>test</scope>` (line 64). I did not check whether the demo directories add their own dependencies.
- No other module (jgdms-jeri, discovery, reggie, other services) names either artifact. Registrar and ServiceDiscoveryManager code has no CEL dependency in the build graph.

CAVEATS
- The line for the `consumeNode` call and the exact line of `enterDepth` use are approximate where I wrote "~".
- The unqualified `Reggie`/`Registrar` question (does any lookup-service module use CEL) is answered by item 5 only: no pom dependency.
- STD-011's file name says v0.1 but the text contains v0.2 binding material. The code does not implement that part.


#### B.6 Test run (coordinator, Chisel)

The coordinator ran the jgdms-cel and jgdms-cel-authoring test suites; result as saved in J:\lookup-cel-2026-11-20\sections\B6_tests.txt (log J:\lookup-cel-2026-11-20\logs6_test.log):

```
openjdk version "27-internal" 2026-09-15
INSTALL 0
TEST 0
jgdms-cel run 1324 fail 0 err 0 skip 0
jgdms-cel-authoring run 74 fail 0 err 0 skip 0
909853aaa0351f75517c0beb58a58cfc */j/lookup-cel-2026-11-20/logs/b6_test.log

[exited with code 0]
```

### C Expressibility

*Author: claude-opus-5-5 (self-reported), agent abd6278a52c8ed4b8. Full report: J:\lookup-cel-2026-11-20\sections\C_expressibility.md.*

Model: claude-opus-5-5 (Opus 5.5). Section C (Expressibility). Trunk 83c4d4c7a, verified with `git rev-parse --short HEAD`. I checked nothing out, edited nothing, built nothing and wrote no files.

Helpers: none dispatched. All inputs were short, so there was no bulk menial work to hand off. No agent IDs to list.

Labels: V = VERIFIED (I opened the line in this session). R = RELAYED (from the A1, A2, A3 or B reports). I = INFERRED. NC = NOT CHECKED. "Spec" means `JGDMS/docs/JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md`, the v0.2 text. Spec line numbers are current.

#### 0. Section numbering and moved memo citations (the architect's note)

**SOW to spec.** Every STD-011 section the SOW cites exists in the current text with the same meaning (V):
- 3.2 is at 171.
- 3.3 is at 266.
- 4.4 to 4.7 run from 446 to 504.
- 5.4 is at 768.
- 6.5 is at 946 and 6.6 at 961.
- 8 is at 1201.
- 9.4 to 9.6 run from 1344 to 1394.
- 10.2 is at 1409.
- 15 item 7 is at 2208.

One stale phrase: SOW D.3 says "what it does to `O(|E| x S)`". Spec 3.1 claim 3 (lines 156-161) withdraws that form as "false as written" (V). The same stale phrase also survives inside the spec, at 6.6 lines 972-973 (V).

**Memo citations that moved.** The memo is `DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md`. I matched each citation by content (all V):

| Memo line | Memo cites | Content | Now at |
|---|---|---|---|
| 97 | STD-011 lines 206-223 | closed function set; no loops, macros or I/O | 3.2(iii) 212-225, 3.2(vi) 247-258, 7.2 table 1073-1133 |
| 99 | lines 242-246 | no timing side-channel claim | 3.3, 277-281 |
| 101 | lines 1592-1595 | open list: list indexing, map indexing, list equality | 15 item 7, 2208-2214 |
| 460 (B9) | lines 233-241 | bounded cost needs `maxScalarBytes` enforced on both sides | 3.3, 268-276 |

The memo's references by section number (8.2 and 8.5 in its 3.5, and O7) still point at the same content: 8.2 at 1227, 8.5 at 1281 (V).

#### 1. Table: one row per need

Notation for the "item" projection (illustrative names, I):
- `field("#item","sidMost")` and `field("#item","sidLeast")` hold the ServiceID.
- `field("#item","e0")` … are the entries, as `object` values.
- Marker namespaces `#types` and `#isa` are explained in section 2.

| Need (source) | Verdict | Predicate, or the rule that blocks it |
|---|---|---|
| Accept all: null template, null filter, `MulticastServiceFinder` accept-all (A1 R) | Expressible today | `true` |
| ServiceID test (A1: 21 qa templates, Browser) | Expressible today, given a projection | `field("#item","sidMost") == 123 && field("#item","sidLeast") == -456`. Two int64 values compare exactly (4.1, 6.1). (I) |
| Service type test, with subtype meaning (A1: 10 + 23 qa, ServiceDiscoveryHelper) | Needs a projection convention. **Blocked if done as `in` over a list field.** | Written as `"com.example.Foo" in serviceTypes`, verification rejects it. Spec 10.2 line 1434 charges `1 + K × (per-element cost)` with K = `maxCollection` = 65536. The per-element cost against a literal of L octets is `1 + L` (tightening rule, 1445-1456). So the term costs more than `maxExprCost` = 1,000,000 (10.3 line 1507) whenever L ≥ 15. Java type names are longer than 14 octets. The code does the same: `CostModel.java:94` `K = CelCeilings.MAX_COLLECTION` and :254 `add(1L, mul(K, perElement))` (V). Arithmetic: 1 + 65536 × 16 = 1,048,577 (I). **Works today:** one marker field per name in the type closure, tested with `has(field("#types","com.example.Foo"))` (6.8, 8.3; cost about 3). |
| Equality on a scalar entry field: String, Integer, Long, Boolean (A1: MixedEntry, TestServiceIntAttr; A2: 13 of 16 main classes are all-scalar) | Expressible today | `e.field("pkg.Name","name") == "server1"`, where `e` is the entry designator. Integral types widen to `int` and `char` to `string` (4.2 lines 322-330). |
| Float or Double field equal to a template value | Expressible today, with care | Jini compares with Java `equals`. CEL `==` is IEEE equality (4.3.4, 422-427), so `NaN != NaN` and `-0.0 == 0.0`. Translate as follows. NaN: `f != f`. -0.0: `f == 0.0 && 1.0/f < 0.0`. +0.0: `f == 0.0 && 1.0/f > 0.0`. NaN payloads cannot be observed (4.3.4 line 438), so two NaNs with different payloads are never told apart. (I) |
| Null field in a template (A1) | Expressible today | Leave the conjunct out. |
| Entry is of class C or a subclass (Jini `isAssignableFrom`; A1 attribute templates) | Needs a projection convention | The language cannot read class names. `type()` is excluded (5.4 item 7, line 797). `CandidateProjection.namespaceChain()` is used only inside name resolution (8.2). `field(C,f)` finds C's namespace when C is in the chain (8.3 line 1265-1267), so it is an implicit class test. That fails for classes with no fields (qa Attr00-02, UninterestingEntry, NBEEmpty; R A2) and for templates whose fields are all null. **Works today:** marker fields in a synthetic namespace, `has(e.field("#isa","net.jini.lookup.entry.Name"))`. |
| "Some entry matches each attribute template", over any number of entries | **Not expressible as one predicate.** Expressible with a fixed slot count, or as a host loop. | The comprehension `exists` is excluded (5.4 item 1, line 775). A list of `object` supports only `has()` (4.7 lines 502-504). A bindings spine does not help, because bindings take no parameters (5.5 item 6, line 853). Two routes work, in section 2. |
| Numeric range | Expressible today | `x >= 10 && x < 20` (6.2) |
| Remainder, AND and NOT (qa TestFilter2/3/3Not2/4: `i % 2 == 0`, `i % 2 != 0 && i % 3 == 0`) | The operators are expressible today; the operand is not a CEL job | `v % 2 != 0 && v % 3 == 0` (6.3, 4.3.1). But A1 shows that `i` is a field of the **service object**, not an entry (R). That is in section 4, unless the service publishes `i` as an attribute (qa already has `TestServiceIntAttr.val`, R A2). |
| Substring, prefix, suffix | Expressible today | `s.contains("lit")`, `s.startsWith(x)`, `s.endsWith(x)` (6.6). The needle of `contains` must be a literal (5.3.1 item 4, line 654). |
| Case-insensitive compare (named by the operator) | Needs an addition, for example an ASCII fold (D, H6) | "no normalization, no case folding" (6.6 line 965). "no case conversion" (4.4 lines 464-465). Partial workaround: `s in ["abc","ABC","Abc"]` lists only the variants you expect. A full fold needs 2^n variants. |
| Pattern matching: glob or regex (named by the operator) | Needs an addition | `matches()` and regular expressions are excluded (5.4 item 2, line 780). A glob with one `*` can be written today: `s.startsWith("a") && s.endsWith("b") && size(s) >= 2`. `a*b*c` cannot be written exactly: `contains("b")` does not order `b` between `a` and `c`, nor stop it overlapping them. `?` needs position or indexing, and the index operator is excluded (5.4 item 7, line 798). (I) |
| String concatenation or string building (named by the operator) | Blocked; **no lookup need found** | 5.4 item 6, line 793. 6.3, line 902. 3.2(iv), line 230: "No node concatenates, copies, or builds strings". A1 found no filter that builds a string (R). A string built from constants is built by the client before sending, as a literal. |
| Membership in a collection field | Expressible, but the cost model limits it | `x in f` (6.5) costs about 65536 × (1 + L) per term. So at most about 7 terms fit with a 1-octet needle, and none with a needle of 15 octets or more (I). `size(f)` is fine. |
| Template value on a non-scalar field: Class, StatusType, Set, MarshalledObject, HashMap, Uuid, short[], user objects (A2: 18 declaring classes, 28 counting inheritors, R) | Needs an addition or a projection widening rule | List and object equality give `TYPE_MISMATCH` (6.1 line 884; 4.7 lines 500-501). Maps have no operations (4.2 lines 336-340). Ways to meet the need are in section 2. |
| Any Java filter on the proxy (`ServiceItemFilter.check`), or the SDM download-and-retry | Not a CEL job | Section 4. |
| Event transitions | Computed by the host from two evaluations | Section 3. |

#### 2. One predicate from a ServiceTemplate

Jini's rule as Reggie implements it (V, `RegistrarImpl.java`):
- `matchItem` (4355-4359): the serviceID is null or equal; and `matchType`; and `matchAttributes`.
- `matchType` (4363-4370): every template type `isAssignableFrom` the item's type.
- `matchAttributes` (4385-4400): for every template, some entry satisfies `matchEntry`.
- `EntryRep.matchEntry` (reggie-dl `EntryRep.java:389-398`): the template class `isAssignableFrom` the entry class, and every non-null template field `equals` the entry field, compared by position.
- Class identity is compared by `hash`, not by name: `EntryClass.isAssignableFrom` (:245-250), `ServiceType.isAssignableFrom` (:255).

**What the item projection must expose (I; names illustrative):**
1. A namespace `#item` with `sidMost` and `sidLeast` (int).
2. A namespace `#types` with one marker field, value `true`, per name in the closure of the proxy's type: its class, all superclasses and all interfaces.
3. Entries as `object` fields `e0` … `e(n-1)`, plus `entryCount`. Each entry object carries:
   - its own class chain, leaf first (8.2, 8.4);
   - a marker namespace `#isa` with one field per class in that chain.

Names that start with `#` cannot collide with a real Java field or class. `field()` accepts any string literal (8.3 lines 1267-1268, V).

**The translation, with Ks slots:**
```
T(tmpl) =  [sid]   field("#item","sidMost") == M && field("#item","sidLeast") == L
        && [types] has(field("#types","T1")) && has(field("#types","T2")) ...
        && field("#item","entryCount") <= Ks
        && [each attribute template a] ( S(0,a) || S(1,a) || ... || S(Ks-1,a) )
S(i,a)  =  has(field("#item","ei").field("#isa","C_a"))
        && field("#item","ei").field("D_1","f_1") == lit(v_1) && ...   (non-null fields only; D_j = declaring class)
```
An empty template becomes `true`.

How the edge cases behave (I, from the 6.4 and 6.8 tables):
- **Slot index past the last entry.** `has()` returns `false` through an absent intermediate, and that `false` decides the `&&`. So no error arises.
- **Item with more than Ks entries.** It fails closed through the `entryCount` guard. Without the guard, a match in a later slot would be missed silently.
- **Size.** A slot costs about 5 + 5F nodes, so a template costs about Ks × (5 + 5F) nodes. The limit that binds is `maxExprNodes` = 1024 (10.3 line 1504), not cost. Example: Ks = 16 with two-field templates allows about 4 templates.

**Answers to the SOW's sub-questions:**
- **How type and class names are reached.** `in` over a list field (6.5) is ruled out by cost, as shown in the table (V arithmetic). The 8.2 namespace chain cannot be read as data. Marker fields tested with `has(field(ns, name))` work today with no change to the language.
- **Name versus Reggie's hash.** Matching by name is weaker than Reggie's hash identity. Keying the marker on name plus schema digest would close the gap; it needs the D5 digests (I).
- **Two entries of the same class.** Yes. They sit in different slots, and two templates may be satisfied by the same entry, as in Jini (I).
- **Subclass of the template class.** Yes, through `#isa`. Reading a field through a subclass entry also works, because `field(D,f)` resolves D anywhere in the chain (8.3).
- **Simpler alternative (recommended to E, I).** Derek, in resident code, evaluates one small predicate Q_a for each (attribute template, entry) pair. Each entry is one candidate, as in Outrigger's `EntryProjection`. Derek then combines the results as "for all templates, some entry", beside one item-level predicate for the ServiceID and types. There is then no slot cap and no 1024-node squeeze. Cost is templates × entries × C(Q). This is not one predicate. The quantifiers stay in host code, never in the expression.

**What A2's non-scalar fields do (I):**
- **Null in the template, the usual case.** No effect, **provided the projection does not fail on them**. Under 8.1 item 2 (lines 1215-1218), a candidate that cannot be projected is `CANDIDATE_UNDECODABLE` for the whole evaluation. That is no-match for every predicate, including ones that never read the field. This would hit 4 of the 16 main-source classes (R A2): `ServiceTypes.serviceType` (Class), `Status.severity` (StatusType), and `UIDescriptor.attributes` (Set) and `factory` (MarshalledObject). The projection must carry such fields as `has()`-only values (allowed by 4.2 lines 336-340 and 4.7 lines 502-504) or leave them out. It must not reject the whole entry.
- **Non-null in the template.** Not expressible today (6.1 line 884). Options:
  - Class as a name string.
  - StatusType as a string or int, which needs a projection rule.
  - A canonical set as a list compared by list equality. This is an addition (15 item 7). For canonicalise disciplines, wire order is canonical (4.2 line 332), so list equality would equal set equality.
  - MarshalledObject as opaque `bytes` compared by octet equality (4.5). That needs a new widening row, and is sound only for canonical bytes. JOSS bytes are not canonical.

#### 3. Event transitions

Yes. Reggie already computes all three transitions from a boolean on each side (V, `RegistrarImpl.generateEvent`, 6436-6453):
- NOMATCH_MATCH = (pre null or !match(pre)) && post non-null && match(post)
- MATCH_NOMATCH = pre non-null && match(pre) && (post null or !match(post))
- MATCH_MATCH = match(pre) && match(post)

Replace `matchItem` with the verdict of the predicate, or of the host combination in section 2, and the same three rules hold unchanged (I).

Notes (I):
- An error maps to no-match (9.4, 1352-1356). So an entry that becomes undecodable gives MATCH_NOMATCH, which fails closed.
- 9.6 (1383-1387) forbids an evaluation from seeing an earlier outcome. So the host keeps either the pre verdict, which may be cached by (expression, candidate), or the pre projection.
- Each change costs two evaluations per registration. That is the cross-tenant timing question (B9, T7), which is E's to assess.
- MATCH_MATCH fires on any change while the item matches, as in Reggie today.

#### 4. What no pure expression can do (resident client code or isolation)

1. **Read the service object.** The qa TestFilter2/3/3Not2/4 cast `item.service` and read `i` (R A1, `AbstractBaseTest.java:255-282`). Any call on the proxy is the same case.
2. **The SDM `check()` path.** It calls `getServiceAttributes` and `getServiceProxy` on the bootstrap proxy, and downloads again on SecurityException or ClassCastException (R A1/A3: SDM 2022-2079; LC 526, 549, 2113-2146).
3. **Proxy trust and preparation.** `verify`, grants and constraints, and the `lookUp` result filter (`instanceof`, `Proxy.isProxyClass`; R A3, `RegistrarProxy.java:269-280`).
4. **Anything comparing two candidates.** Item-change equality (`LookupCacheImpl:1724-1725` `fullyEquals`, R) and de-duplication across registrars. 9.6 forbids it.
5. **Counting and aggregation.** `maxMatches`, ordering, and the distinct-value queries that would replace `getEntryClasses`, `getServiceTypes` and `getFieldValues`. A transform can deliver the values of one item; the host combines them.
6. **The quantifier over an unbounded number of entries** (section 2). This is host code, unless D proposes a bounded `exists` with a 3.2 extension.
7. **Anything needing a clock or state.** For example "registered in the last N minutes", or lease expiry (7.1 lines 1062-1065; 9.6). A literal "now" from the client works for a one-shot lookup but goes stale in an event registration (I).
8. **Unicode-aware case folding or regex in their full forms.** These stay out unless D admits a pinned subset.

#### 5. Points for D and E

- The `in`-over-list-field cost ceiling. It fails at a needle of 15 octets with K = 65536. Verification is schema-free (`CostModel.java:94` and :254, V). This makes every "list of names" design inadmissible today. Either use marker fields or have a schema-aware K, which is a cost-model change, not a language change.
- `maxScalarBytes` is not enforced on candidate fields in jgdms-cel (R, coordinator note). A Derek or client-side projection must enforce it itself, or claim 3 does not hold (3.3 lines 268-276, V).
- Not determined:
  - how qa `(null,null,attrs)` contents are built (A1 NC);
  - how enum and `short[]` fields project (NC);
  - whether the code accepts `field(...).field(...)` designators of two qualified steps. The spec grammar allows it (5.3.3); the code is NC.


### D Language additions

*Author: claude-fable-5-2 (checked), agent ab652fcba9612c654; helpers a0af6e1857e41bdbd, a1667b3d2cdf15363 (Sonnet). Full report: J:\lookup-cel-2026-11-20\sections\D_additions.md.*

Model: claude-fable-5-2 (self-reported). Section D (Language additions: safety analysis). Trunk 83c4d4c7a (VERIFIED, `git rev-parse --short HEAD`). Nothing checked out, edited, built or committed. No file written by me; one probe file written by a helper outside the repository.

Helpers (Sonnet, self-reported claude-sonnet-5-5, no further agents): a0af6e1857e41bdbd (code and spec facts, bulk grep), a1667b3d2cdf15363 (probe write and run).

Labels: V = VERIFIED (I opened the line this session), R = RELAYED (helper, C, or coordinator), I = INFERRED, NC = NOT CHECKED. "Spec" = STD-011 v0.2 text in the v0.1-named file; line numbers current.

#### 0. Headline

1. **Lookup needs no language addition.** Every surveyed need is met by today's language plus projection conventions and host code (I, from C and the checks below).
2. The one real gap, equality on non-scalar fields, is closed by a **projection rule** (field as canonical DER `bytes`), not by a new operator. No 3.2 claim changes.
3. Concatenation, glob and ASCII case folding **can** be made safe. None has a surveyed use, so none is recommended now.
4. New finding: at the proposed ceilings, `contains` admits a needle of at most **15 octets**, and one such call uses 98% of `maxExprCost`. This is the same arithmetic as C's `in` finding. It limits "substring is expressible today".

#### 1. Facts used throughout

| Fact | Label |
|---|---|
| 3.2(iv): "No node concatenates, copies, or builds strings/bytes/collections" (spec 230-232) | V |
| Claim 4 bound is `O(maxEffectiveDepth + |E| × S + maxBindings)` (spec 163-165) | V |
| Claim 3 is the gate `C(E) ≤ maxExprCost` plus `xCost(M) ≤ maxExpandedCost`; the `O(|E| × S)` form is withdrawn (spec 150-161) | V |
| 10.2 rows: `contains` = `1 + S × len(needle)`; `in` over a field = `1 + K × per-element`; string compare = `1 + min(S, literal length)` | V (spec table and tightening rule) |
| Ceilings: S = K = 65536, `maxExprCost` 1,000,000, `maxExprNodes` 1024, `maxBindings` 256, `maxExpandedCost` 2^23, all [PROPOSED] | V (spec 10.3) |
| Code: `CostModel.java:274-279` `add(1L, mul(S, needleLen))`; `:255` `add(1L, mul(K, perElement))` | R (a0af…; C verified :94 and :254) |
| `contains` needle: 15 octets costs 983,043 with receiver and literal; 16 octets costs 1,048,579 and is rejected. Test javadoc `CelVerifierCostGateTest.java:40-45` states the 15-octet figure; no test pins 16. | arithmetic I; test R |
| Function ids 1-24 are used (Appendix B B.8.3, a separate file); node tags 0-27 used, 28-30 reserved | R |
| Closed error set, 8 codes, "no implementation may add codes" (spec 1304-1317) | R |
| No production projection yields a list: Outrigger `EntryProjection` fails closed on collection fields; jgdms-cel never checks a projected list's length against K | R |
| Lookup filter and matching code: no case-insensitive compare, regex, glob or format call (39 files; one unrelated hit, `Browser.java:1419`, command-line parsing). String `+` not grepped exhaustively. | R |
| Jini matching is equality of serialized form: `EntryRep.java:389-398` `tmpl.flds.get(i).equals(flds.get(i))`; `MarshalledInstance.equals` (:993-1003) compares `hash` then `Arrays.equals(payloadBytes, …)` | V |

#### 2. Candidate (a): string concatenation

1. **Semantics.** `+` on `string × string` and `bytes × bytes`. If the result exceeds `maxScalarBytes` wire octets, the result is `OVERFLOW`. This follows checked `int` arithmetic (4.3.1), and needs no new error code. Narrower alternatives: (i) allowed only at the root of a transform (H3); (ii) none in the language, a transform returns a list of parts (H2).
2. **Termination.** Unchanged. One visit per node.
3. **Cost.** `1 + S` per node (copy of at most S octets; the length check is O(1) before the copy). Every string value stays ≤ S, so all other rows stay true. `C(E)` gate unchanged in form.
4. **Allocation.** New: up to S octets per concat visit, so up to `|E| × S` = 64 MiB per evaluation at the proposed ceilings. This is inside the letter of claim 4 and is a large practical regression from "no node builds a string". The sentence at spec 230-232 would have to be rewritten.
5. **Determinism.** Exact. Concatenating two well-formed scalar-value sequences is well-formed in UTF-8 and UTF-16. The length test must be in UTF-8 octets on every platform.
6. **Timing.** Copy time depends on candidate lengths. Same class as string comparison today.
7. **Wire and verifier.** No new node or id. 6.3, 5.4 item 6, 11.1 `ADD` typing, 10.2 static kind (`ADD` becomes numeric-or-string), Appendix A, new vectors at S and S+1. Code: `Evaluator`, `CompiledNodes`, `CostModel.categoryOf`, `StaticTypeChecker`.
8. **Recommendation: reject for lookup.** No use found (A1, C, helper grep; R). Rule 6 is met only by the operator naming it.
9. **Without it.** A string built from constants is built by the client as a literal. A display string is delivered as a list of parts and joined by resident client code (H2).

#### 3. Candidate (b1): glob with a literal pattern

1. **Semantics.** `s.glob(p)` returns `bool`. `p` MUST be a string literal. `*` matches any run of code points, including none. `?` matches exactly one Unicode scalar value. `\*`, `\?`, `\\` are literals; any other `\` is a well-formedness error. No classes, no case folding, no normalisation. Whole-string match. Non-string receiver: `TYPE_MISMATCH`. Canonical form: `**` and `*?` are rejected (`?*` is the one spelling). Narrower alternatives: `*` only, which needs no code-point rule and can run on UTF-8 octets; or nothing, since a one-star glob is already `startsWith && endsWith && size(s) >= n` (C, I).
2. **Termination.** One node visit. The matcher never revisits a segment.
3. **Cost.** `1 + (S + 1) × len(pattern)` in wire octets. Same class as `contains`. At the proposed ceilings the pattern is limited to 15 octets, as `contains` is.
4. **Allocation.** None per evaluation. The split of the pattern into segments is fixed per expression.
5. **Determinism.** Exact, given the code-point rule. The result is membership in a set of strings, so it does not depend on the algorithm.
6. **Timing.** Depends on the candidate string, like `contains`.
7. **Wire and verifier.** Function id 25. 5.3.1 gains "the argument of `.glob` MUST be a string literal" and the escape and canonical rules; 12.1 enforces them; 5.5 item 4 lists the argument as a position a `REF` may not take. 7.1 obligations: signature, errors, cost row, vectors (supplementary-plane `?`, empty string, escape cases, boundary cost).
8. **Recommendation: defer.** Safe, but no surveyed use. Draft 3.2 text if it is ever added, for (iii): "`glob` is at worst `(|subject| + 1) × |pattern|` code-point comparisons with the pattern restricted to a literal (§6.6), so the pattern's size is part of `|E|`; the matcher places the head at the start, the tail at the end, and each middle segment at its leftmost fit after the previous one, and never revisits a segment." (iv) and (vi) need only the function added to the closed registry.
9. **Without it.** One-star globs as above. Ordered multi-part matches cannot be written exactly today (C, I).

#### 4. Candidate (b2): regular expressions

1-7 in brief. A pinned subset (literals, `.`, explicit code-point ranges, alternation, grouping, `*`, `+`, `?`, bounded `{n,m}`; whole-string boolean match; no captures, back-references, look-around, Unicode classes or flags) is safe: Thompson construction gives at most `2 × e` states for an expanded pattern of `e` items, where `{n,m}` counts `m` copies and nesting multiplies. Simulation with two state sets costs `1 + S × states`. Allocation is two bit sets of `states` bits per evaluation. Determinism holds because a boolean whole match is language membership, so each platform may write its own simulator. New wire content: a pattern grammar, its canonical form, a state ceiling, and a corpus of its own.
8. **Recommendation: reject now; keep 5.4 item 2.** At S = 65536 the cost row admits about 15 states, which is too small to be useful, and there is no use.
9. **Without it.** `contains`, `startsWith`, `endsWith`; glob if ever added.

#### 5. Candidate (c1): ASCII case-insensitive compare

1. **Semantics.** `a.equalsIgnoreAsciiCase(b)` returns `bool`: code-point equality after mapping U+0041..U+005A to U+0061..U+007A, nothing else (U+212A and U+0130 are not folded). Alternative, wider: `lowerAscii(s)` returning a string, which composes with `contains` and `in` but allocates. The comparison form is the narrower one.
2-4. One visit; cost `1 + min(S, literal length)` as the string `==` row; no allocation.
5. Exact on all three platforms. ASCII letters are single octets in UTF-8 and never occur inside a multi-octet sequence.
6. Same as string equality.
7. Function id 25 or 26; 4.4 and 6.6 wording; vectors for the two non-folded code points.
8. **Recommendation: defer.** No surveyed need: Jini matching is exact (V) and no filter folds case (R).
9. **Without it.** The client lists the expected spellings: `s in ["abc","ABC"]`.

#### 6. Candidate (c2), PRIMARY for non-scalar fields: project the field as canonical DER `bytes`

This addresses the coordinator's note and the operator's rulings (RL-JGDMS-0132, R): Derek and the new API carry DER-born `MarshalledInstance` values only; no `MarshalledObject`; no JOSS path. Legacy Reggie is out of scope.

1. **What it is.** Not a language addition. `bytes == bytes` exists (4.5, 6.1; V). The projection exposes a non-scalar field's canonical DER octets as a `bytes` value. I recommend a companion designator (for example a synthetic namespace such as `#der`) so that the field's own row in 4.2 is untouched and a `Collection` field can still be a `list`. The client encodes the template value with the same codec and sends it as a `LIT_BYTES`.
2. **Meaning against Jini.** Same in kind: Jini already compares serialized form (V). DER makes it better defined: `HashSet`/`HashMap` are canonicalised, octet-sorted (STD-006 197-204, V), so two equal hash sets give equal bytes. Byte equality is order-sensitive for preserved types and stricter than `equals` for `LinkedHashSet`/`LinkedHashMap` (STD-006 206-209, V).
3. **Termination, cost, allocation.** No node changes. Cost `1 + min(S, literal length)`. No evaluation-time allocation; the octets are a slice made at projection.
4. **3.2 claims.** None changes, on one condition: the projection enforces `maxScalarBytes` on these values, as 3.3 already requires of every projected scalar (spec 268-276, V).
5. **Oversize rule (must be pinned, for claim 2).** A field whose encoding exceeds S must not make the whole candidate undecodable, because 8.1 item 2 would then fail predicates that never read it (spec 1215-1218, V). Proposed: such a field is carried as a `has()`-only value, so a comparison yields `TYPE_MISMATCH` and the predicate does not match. Alternative for E to weigh: expose a SHA-256 of the encoding (32 octets, cost 33); equality then holds only up to collision resistance.
6. **JOSS-born fields.** Not supported in Derek (R, ruling). A field whose payload format is not DER is rejected at registration or carried `has()`-only; E decides which. JOSS bytes are not canonical, so byte equality on them would not be value equality anyway (C, R).
7. **`MarshalledObject` fields** (`UIDescriptor.factory`): not exposed by the new API (operator, R). The projection omits the field; it must not reject the entry.
8. **`java.lang.Class` fields** (`ServiceTypes.serviceType`): how DER encodes a `Class` is NC. A grep of STD-006 for `java.lang.Class` found no hit (V, grep only). Fallback that needs nothing new: project the class name as a `string`.
9. **Spec text needed** (4.2 and 8.1, not 3.2): a note that a projection MAY expose the canonical DER octets of a non-scalar field as `bytes` under a companion designator, subject to `maxScalarBytes`, with the oversize rule above.
10. **Code.** jgdms-cel: none. Derek's projection: new. Outrigger's `EntryProjection` today fails closed on such fields (R) and would need the same rule only if Outrigger wants it.
11. **Recommendation: adopt as the projection rule.** Condition that would change it: the bytes of a field slice turn out not to be reproducible by a client encoding the same value (NC, see section 11).

#### 7. Candidate (c3): list equality

1. **Semantics.** `f == [lit, …]` with one operand a list literal. Sizes are compared first; then elements left to right under 6.1; the first unequal pair gives `false`, the first error gives that error. Wider alternative: field against field, which costs `K × (1 + S)` = 2^32 and is inadmissible.
2-6. One node; cost `1 + Σ literal element comparison costs`, independent of K; no allocation; exact; timing as string equality.
7. No new node; 6.1 and 4.7 change; a syntactic rule that one operand is a `LIST_LIT`; 10.2 needs the row it does not pin today (spec 1472-1475, V).
8. **Recommendation: defer; superseded by (c2).** It covers only lists of scalars. `UIDescriptor.attributes` is a set of objects and `short[]` appears only in qa classes (A2, R).
9. **Without it.** (c2).

#### 8. Candidate (c4): cheaper membership for `in` over a field

This is a **cost-model change, not a language change**.

- Today: `1 + K × c`. A string literal needle of 15 or more octets is rejected; a field needle costs 2^32 and is always rejected; a numeric needle costs 65,537 and is admitted (I, from the 10.2 rows; C verified the code lines).
- A schema-aware K is unsound: layers 1-3 are schema-free and candidates carry their own schemas (12.4, V), so a registration-time bound is not enforced on the candidate. Reject that form.
- Sound form: pin a projection ceiling R on the total wire octets of a projected list field. Each DER element takes at least `2 + len` octets, so element count plus total element length is at most R, and the scan's true work is at most R (I). Row becomes `1 + min(K × c, R)`. With R = 65536, about 15 such terms fit.
- Changes: 10.2 row, 10.3 new constant, 8.1 contract, 3.3 (a second "enforced on the candidate side" condition), 3.2(iii) ("list sizes by `maxCollection`" gains "and list octets by R"), 13 vectors; `CostModel.java:255`; every projection that yields a list. Outrigger's 64 KiB budget on referenced fields (`EntryProjection.java:151, 516, 735-738`, R) is already this kind of ceiling.
- Timing: unchanged; the scan itself does not change.
- **Recommendation: defer.** Lookup does not need it: type and class tests use marker fields, `has(field("#types", name))`, at cost about 3 (C, I). Condition: E projects a set-valued scalar field that must be searched with a string needle.
- Note: jgdms-cel does not check a projected list's length against K (R). Any projection that yields lists must enforce K itself, or claim 3 fails for `in`.

#### 9. Candidate (c5): bounded `exists`

Not warranted. Reasons (I):
- It needs a bound variable of `object` type with selection, which 5.5 item 4 and 5.4 items 3-4 exclude (V).
- It breaks 3.2(ii) "visits each node at most once"; node visits become `N × |body|`.
- Its cost is `N × C(body)`; with N = K it is inadmissible for any body of cost 16 or more, so it also needs a literal bound in the syntax.
- The quantifier shape in lookup is fixed by Jini ("for every template, some entry"), so it belongs in resident host code evaluating one small predicate per entry (C's recommended route, R).
**Recommendation: reject.** Condition: a need to compare two entries of one item inside one predicate. None was surveyed.

#### 10. Hypotheses

| H | Verdict | Reasons |
|---|---|---|
| H1 | PARTLY | The growth premise is confirmed against the spec: node count does not limit it, and with concat priced 1 the `xCost` gate admits 22 doublings (spec 10.5 gives `xCost(22)` admitted for the same chain shape, V), a factor of 2^22 on a 64 KiB string (I). Bindings are in the spec (5.5, 10.3.1) and not in code (R). The conclusion is too narrow: a **static** bound gives every field reference the bound S, so any concatenation that includes a field is rejected, which makes it useless. The workable safe form is a **checked** result length that yields `OVERFLOW` above S, as `int` arithmetic does. Then every value stays ≤ S and the doubling chain errors at run time. |
| H2 | CONFIRMED, with two limits | A list literal's elements "may be arbitrary expressions" and a list is delivered whole as a transform result (spec 5.3.2 and 4.7, V). Code agrees: `evalListLit` evaluates each element; `DeclaredResultType.ListType`; the 14.3 test returns a list computed from field references (R). Limits: the list is homogeneous, so numbers and strings cannot mix and there is no number-to-string conversion; a null element gives `TYPE_MISMATCH` for the whole list (`Evaluator.java:275-295`, R), so parts need `has(f) ? f : ""`. No test returns a list of string fields (R). |
| H3 | CONFIRMED as scope; the restricted form is redundant | No surveyed filter uses a built string as a comparison operand (R). A concatenation allowed only at a transform's root delivers what a list of parts plus a client join delivers. So it adds nothing over H2. |
| H4 | CONFIRMED | Algorithm: split the pattern at `*`. With no `*`, lengths must be equal and positions match. Otherwise the head must match at the start, the tail at the end without overlap, and each middle segment is placed at its leftmost fit after the previous one, by direct comparison. No segment is revisited. Bound: `(|s| + 1) × |p| + 1` comparisons. Probe: 300,000 random cases, 0 mismatches against a recursive oracle, 0 bound violations; near-miss case 982,831 steps at 65,536 code points and 17 tokens, bound 1,114,130; the recursive matcher exceeds 50,000,000 steps at 64 code points. Code-point rules: `?` is one scalar value (U+1F600 matches `?`, not `??`); no normalisation; offsets are tried only at code-point boundaries. Same cost class as `contains`, and the same 15-octet practical limit. |
| H5 | PARTLY | A small pinned subset removes the determinism objection, because a boolean whole match has no engine-dependent result. Automaton bound: at most twice the expanded pattern size in states; cost `1 + S × states`. What remains: a second grammar with its own canonical form and corpus in three languages, and a cost row that admits about 15 states at S = 65536. |
| H6 | CONFIRMED on determinism; usefulness: none surveyed | An ASCII fold is version-independent. No surveyed lookup need is case-insensitive at all (R; Jini matching is exact, V). It would suit class names and identifiers, not human-readable names with non-ASCII letters (I). |

#### 11. Summary

| Candidate | Recommendation | Cost class | The one condition that would change it |
|---|---|---|---|
| (a) Concatenation | Reject for lookup | `1 + S` per node; allocates up to `|E| × S` | A consumer with no resident code needs one built string |
| (b1) Glob, literal pattern | Defer (safe) | `1 + (S+1) × len(pattern)` | A surveyed filter needs an ordered multi-part match |
| (b2) Regex subset | Reject now | `1 + S × states` | A use that glob cannot meet, and a smaller S or larger cost ceiling |
| (c1) ASCII case-insensitive equality | Defer (safe) | `1 + min(S, literal length)` | A surveyed case-insensitive match |
| (c2) Non-scalar field as canonical DER `bytes` | Adopt, as a projection rule; no language change | `1 + min(S, literal length)` | Field-slice bytes not reproducible by the client encoder |
| (c3) List equality against a literal | Defer; superseded by (c2) | `1 + Σ literal elements` | (c2) fails and a list-of-scalars field must be matched |
| (c4) `in` over a field, bound by list octets R | Defer; cost-model change | `1 + min(K × c, R)` | E projects a searched set-valued scalar field |
| (c5) Bounded `exists` | Reject | `N × C(body)` | A predicate must compare two entries of one item |

No candidate is "add now" as a language change, so rule 7 requires no 3.2 extension text. Draft text is given above for (b1) and (c4) in case either is taken up.

#### 12. Not settled

1. How DER encodes `java.lang.Class` (NC).
2. Whether a field's stored DER slice is self-contained and byte-identical to what a client produces by encoding the same value alone, including schema version effects (NC). (c2) depends on it.
3. The oversize rule for (c2): `has()`-only, digest, or reject at registration. E's choice.
4. Whether Derek has a transform context at all. H2 and H3 matter only if it does (NC).
5. All ceiling values are [PROPOSED]. The 15-octet limits on `contains`, glob and `in` move with S and `maxExprCost`.
6. `contains` in the Java evaluator is `String.contains` (R). Its worst case is within the `S × len` row; I did not measure it.
7. The probe is Java only and counts code-point comparisons. Nothing was run in Rust or Haskell.
8. String `+` building in lookup paths was not grepped exhaustively (R). The contents of the qa `(null, null, attrs)` templates remain NC (A1).
9. Glob and fold naming against CEL's extension libraries was not checked against a CEL source this session (NC).
10. The stale `O(|E| × S)` phrase at spec 972-973 (V) should be corrected with any 6.6 edit.

#### 13. Probe

Probe source and output: J:lookup-cel-2026-11-20probesGlobProbe.java; output as reported in sectionsD_additions.md section 13

Reading the output: in table 1 the segment matcher stops after 3 steps because the tail `b` fails at once; that table shows the recursive matcher's blow-up, not the segment matcher's worst case. Table 2 is the segment matcher's near-worst case. The probe has no escape handling; the escape rule in section 3 is untested.

Files: `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md`, `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`, `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\jgdms-platform\src\main\java\net\jini\io\MarshalledInstance.java`, `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\services\reggie\reggie-dl\src\main\java\org\apache\river\reggie\proxy\EntryRep.java`, `J:\lookup-cel-2026-11-20\probes\GlobProbe.java`.


### E API proposal

*Author: claude-fable-5-2 (checked), agent a5afe1509e6a0978c. Full report: J:\lookup-cel-2026-11-20\sections\E_api_main.md.*

Model: claude-fable-5-2 (self-reported). Section E (API proposal: `RemoteRegistrar` with CEL in both directions). Trunk 83c4d4c7a (VERIFIED, `git rev-parse --short HEAD`). Nothing checked out, edited, built or committed. No file written.

Helpers: none dispatched. The inputs were read directly and there was no bulk extraction to hand off, so there are no agent ids to list.

Labels: V = VERIFIED (I opened the line this session), R = RELAYED (A1 to D, coordinator notes, or the memo's own relayed rows), I = INFERRED, NC = NOT CHECKED. "Spec" is STD-011 v0.2 text in the v0.1-named file. "Memo" is `DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md`. **Every interface, method, record and field name below is illustrative.** Everything here is an option with reasons. Nothing is a decision.

#### E.0 Headline

1. Derek's query is a small **structured record that carries CEL predicates**, not one predicate and not a template record. Derek's resident code owns the quantifiers ("for every entry predicate, some entry"). (I, from C section 2.)
2. Lookup needs **no transform context in Derek at first**. A named-field selection list meets the surveyed needs and is cheaper to bound. (I)
3. A service-authored expression that runs on the client is safe to *execute* whoever wrote it. What it is not safe for is *disclosure*: every projected field must be treated as fully disclosed to that service. (I)
4. The rule "evaluate only over entries the caller may read in full" **removes the content channel** (result and timing) and **only narrows** the existence, activity and co-residency channels. It also depends on a read-authorisation model that Derek does not have yet. (I)
5. Derek and every client projection must enforce `maxScalarBytes` and the list bound K themselves. jgdms-cel does not (R, coordinator note; B section 3).

#### E.0a Who authors, who evaluates, what binds (one row per path)

| Path | Author | Carrier | Verifier | Evaluator, process, Subject | Data it reads | What binds it to an authenticated party | Where the output goes |
|---|---|---|---|---|---|---|---|
| P1 lookup predicate | Client C | Argument of C's authenticated JERI call to Derek, DER | Derek (`CelVerifier`, Derek's own ceilings) | Derek's resident loop, Derek's JVM, Derek's Subject, no privileged block | Items visible to the caller | The client Subject of the call, taken from the JERI server context. Never a field of the request. | C |
| P2 event predicate | C | As P1; stored by Derek with the registering principals | Derek, at registration | Derek, later, on another registrant's change | As P1, with visibility re-checked at event time against the stored principals | As P1 at registration; the lease | C's listener stub (hostile data to Derek) |
| P3 field selection | C | As P1 | Derek (structural only; it is not an expression) | Derek resident code | As P1 | As P1 | C |
| P4 argument expression | Service S | Return value of an accessor call on S's **prepared** bootstrap proxy | C (`CelVerifier`, C's own ceilings) | C's resident loop, C's JVM, C's Subject, no privileged block | C's row projection, filled from an allow-list | The authenticated server principal of that proxy, plus "use only on calls to the same proxy", plus a layout digest | S, as call arguments |
| P5 requirement predicate | Registrant R | Derek (a claim) or R's accessor (authentic) | C | C's resident code | C's capability record | None when it comes from Derek | C's behaviour: contact or no contact, which R can see |
| P6 decorator predicate | Resident decorator code, from a local `ServiceTemplate` | As P1 | Derek | As P1 | As P1 | As P1 | C |

All rows I. The evaluator needs no permission, because evaluation is pure (spec 3.1 claim 2, lines 145-148, V).

#### E.1 Server-side predicate

**Options**

| # | Shape | For | Against |
|---|---|---|---|
| 1A | One CEL predicate replaces the template | One object to verify | Needs entry slots `e0..eK`. Fails closed above Ks entries. `maxExprNodes` = 1024 allows about 4 two-field templates at Ks = 16 (R, C section 2). Depends on two-step `field().field()` designators, NC in code (R, C). Opaque to any index. |
| 1B | Query record: one optional item predicate plus a list of entry predicates. Derek combines them. | No slot cap. Each entry is one candidate, as in Outrigger. Quantifiers stay in resident code (SOW overview 1a: no iteration in CEL, V lines 67-76). No nested designators needed. | It is not "one predicate". Cost is per (predicate, entry) pair. |
| 1C | 1B plus two structured fields that Derek can index: a ServiceID and a list of required type names | Derek can select candidates from an index before any evaluation. ServiceID lookup is the commonest template (R, A1: 21 qa uses). | Two ways to say the same thing (a type name as a field, or as `has(field("#types", n))`). The record must say the fields are conjuncts. |
| 1D | A legacy-shaped template record beside the predicate | The decorator needs no CEL dependency | It lets `ServiceRegistrar` shape Derek's API, which the operator ruled out. |

**Recommended: 1C.** The same record serves `lookUp`, the item-view lookup and `notiFy`.

**Semantics (I).** An item matches when all of these hold:
- the ServiceID field is absent or equal;
- every required type name has a marker in `#types`;
- the item predicate is absent or yields `true` over the item projection;
- for every entry predicate, some visible entry yields `true`.

**Projections Derek must provide (I, extending C section 2):**
- Item projection: namespace `#item` (`sidMost`, `sidLeast`, `entryCount`, `proxyKind`) and `#types` (one marker per name in the registrant's declared type closure).
- Entry projection: the entry's own schema chain, plus `#isa` (one marker per class in the chain), plus a companion namespace for non-scalar fields (see E.2a).
- Names starting with `#` cannot be Java class names. Spec 8.3 matches the class name by exact string equality (V, lines 1262-1266). The spec does not reserve the `#` prefix; that needs a sentence in 8.1 or 8.3.

**Rules Derek's resident loop must follow (I unless marked):**
1. Verify every expression at call entry, in Predicate context. A verification failure fails the call with a typed exception. It is never an empty result (memo hazard 2, V line 391).
2. Check `isUndecodable()` before evaluating. The Java walker reports `CANDIDATE_UNDECODABLE` only at a field reference, so `true` would match an undecodable candidate (spec 15 item 9, V lines 2217-2225).
3. Any error is no-match (spec 9.4, V lines 1350-1356).
4. Ceilings on the record: number of entry predicates, total expression octets.
5. `#types` and `#isa` are the registrant's claims. The client's real type check is on the authenticated proxy (the `lookUp` result filter, R, A3: `RegistrarProxy.java:269-280`).

**`notiFy`.** Same record. Derek computes the three transitions from the verdict before and after the change, with Reggie's rules unchanged (R, C section 3). The handback is a DER `MarshalledInstance` that Derek never decodes, under a size ceiling. The listener is a DER stub.

**The decorator's translation of a `ServiceTemplate`** is in E.11.

#### E.2 Server-side transform, and what replaces the three introspection calls

**Options**

| # | Shape | For | Against |
|---|---|---|---|
| 2A | No selection. The item view returns whole class-free items. | Simplest | Largest replies. The whole of O8 falls on the client. |
| 2B | A selection list of (class name, field name) pairs, plus flags. Not an expression. | No verification, no cost model, no error results. Reply size is computable from the request. | No derived values. |
| 2C | A CEL transform per entry, with a declared result type | Derived values; a list of parts (R, D H2) | Derek gains a transform context and the 9.5 error result, whose wire shape is still T2's (V, lines 1368-1372). A list literal of field references can repeat a field up to about 1023 times, so the reply can be far larger than the stored data (I, from `maxExprNodes` and R, D H2). It needs its own result-size ceiling. No surveyed need. |

**Recommended: 2B now; 2C deferred.** The condition that would change it: a real client that needs a derived value and has no resident code to compute it.

**Replacements (I; memo 10.5 O16 asks for "schema, type-name and named-field calls", V line 571):**

| Legacy call | Replacement on Derek | Returns |
|---|---|---|
| `getServiceTypes(tmpl, prefix)` | type-name query with a prefix | distinct type names (strings) |
| `getEntryClasses(tmpl)` | entry-class-name query | distinct (class name, schema digest) pairs |
| `getFieldValues(tmpl, setIndex, field)` | field-value query, by entry-predicate index, class name and field name | distinct DER scalar values, or the companion digest for a non-scalar field |

- Derek computes distinctness in resident code (R, C section 4 item 5). Each call has a result-count ceiling and an explicit "truncated" flag.
- These are aggregate queries across registrants. They run only over visible items (E.6).
- Alternative: leave all three out of Derek and let the decorator compute them from a page of item views. That gives a smaller API and costs bandwidth. It is a fair first increment.

**Effect on O8 (I).** O8 is the missing aggregate decode ceiling across a whole item (V, memo line 371).
- Selection makes the item-view reply bound a function of the request: items × entries × selected fields × S. The client can set a per-call decode limit from what it asked for.
- It does not remove O8. Derek's reply is hostile data, so the client still enforces the limit during decode whatever it asked for.
- O8 is still needed in three other places: at registration on Derek (entries per item, octets per item, markers per item); on `lookUp` results and events at the client; and on the service's own attribute accessor (memo R14).

##### E.2a The three choices D left to E

| Choice | Options | Recommended, with reason |
|---|---|---|
| Oversize non-scalar field | (i) reject at registration; (ii) `has()`-only; (iii) digest when oversize; (iv) **always** project the companion as the SHA-256 of the canonical DER encoding | **(iv)** for the companion; **(i)** for a true scalar over S. One rule, no size branch, 32 octets, cost 33. Option (iii) is two rules, and the client cannot know which applies. Option (ii) makes a template silently never match. The field itself stays retrievable through selection. Equality holds up to collision resistance. It depends on D's open point 12.2 (client-reproducible bytes) exactly as byte equality does. |
| JOSS-born field | reject at registration; or `has()`-only | **Reject at registration.** A stored JOSS payload is later returned to clients, which is a JOSS path. RL-JGDMS-0132 says Derek carries DER only (R). Derek checks the payload-format tag of every `MarshalledInstance` it accepts and never calls `get()`. |
| Transform context in Derek | yes; no | **No, at first** (2B). |

#### E.3 Client-side expression (service to client)

**Where the expression comes from**

| # | Source | Binding | Assessment |
|---|---|---|---|
| 3A | An accessor on the service's bootstrap proxy, called after the proxy is prepared | The authenticated channel: the client's constraints (server authentication, integrity, `ATOMIC_DER`) on that call | **Recommended.** No extra mechanism. |
| 3B | Derek, as registration data | None. It is Derek's or the registrant's claim. | Usable only as a cache: the bytes come from Derek and the authenticated service confirms the digest. Little gain. |
| 3C | Fixed in the service interface the client already has | Local code | The degenerate case of 3A. No fetch, not changeable. |

Execution is safe in all three cases, because the language is bounded whoever wrote it (spec 1.3, V lines 108-114). The source matters for integrity and disclosure only.

**Design (I):**
1. **Identity binding.** An expression obtained from prepared proxy P is used only to prepare arguments for calls on P. The client keeps (P's authenticated server principal, expression digest) together. It is never reused for another service.
2. **Layout.** The row layout (namespace name, field names, types) is part of the service's interface contract and is resident on the client. The accessor returns the layout digest with the expression. A digest the client does not hold locally is refused. This is the client-side analogue of the spec 8.5 applicability key.
3. **Verification.** The client verifies the bytes with its own ceilings before use. A failure fails the call. It **never** falls back to sending unfiltered rows.
4. **Projection.** A generic resident row projection over an explicit field map. It must itself enforce:
   - `maxScalarBytes` on every string and bytes value, in UTF-8 octets;
   - K on every list, and scalar elements only;
   - well-formed UTF-16 (a stated precondition of `CandidateProjection`, R, B section 2).
   A row that breaks a rule is undecodable. It is not sent, and it is counted locally. The loop checks this before evaluating (spec 15 item 9).
5. **The loop is resident code.** For each row: evaluate the predicate; on `true`, send the row or the transform result. An error is no-match, so the row is not sent. A transform error drops the row or sends an explicit error marker, never the raw row.
6. **Total cost.** At most rows × (C(predicate) + C(transform)). Both C values are static (spec 3.1 claim 3, V lines 149-155), so the client computes the total **before** evaluating anything and refuses if it exceeds the client's own budget. The client should use ceilings well below the proposed 1,000,000. C(E) is an upper bound in abstract units, not a time.
7. **The service must not rely on it.** A client can ignore the expression. The service treats the arguments as untrusted and re-checks whatever matters.

#### E.4 Disclosure rule

**Rule: projected means disclosed.** The client fills the projection only with data it is willing to disclose in full to that authenticated service principal. (I)

Reason: a transform returns values directly. A predicate returns one bit per row through which rows are sent. A service that changes its expression between fetches can read any projected field bit by bit (for example with `startsWith`), including fields it never receives.

Consequences the client must follow:
1. Fill from an explicit allow-list, which is the layout. Never by reflection over a client object.
2. No ambient values: no client identity, host name, environment, clock, or data held for another service.
3. One projection per service principal. No reuse across services.
4. Keep per-row error codes local. An error code is another output.
5. Client-side CEL gives **data minimisation and bandwidth saving**, not confidentiality, against a service that can change its expression. If a client wants more, an option is approved-digest mode: the client's operator approves an expression digest and a change needs re-approval. The leak is then what that one expression reveals.

#### E.5 Two-sided matching

- **What it is.** A registrant publishes a requirement predicate. The client evaluates it over its own capability record and skips services it does not satisfy.
- **Useful?** Marginally. It avoids a connection to an incompatible service, and it keeps the client's capabilities out of the query that Derek sees. The usual alternative needs nothing new: the service publishes plain attributes (for example a minimum version) and the client writes its own predicate over them.
- **What it leaks (I):**
  1. To the registrant: one bit per predicate, through whether the client then makes contact. A registrant that registers many fake services with different predicates can read the capability record bit by bit. So E.4's rule applies: the record holds only what the client would tell any registrant in that lookup service.
  2. To every reader: the service's requirement itself.
  3. To nobody else, if evaluation is local.
- **It is not access control.** The client can lie or ignore it. The service enforces on authenticated calls.
- **Recommendation: defer.** No surveyed need. It needs no language change. Note that an expression can exceed `maxScalarBytes`, so it would not always fit a `bytes` attribute and would need its own registration field.

#### E.6 Evaluation on Derek and timing (memo B9, spec open item T7)

**The rule protects the result first, then the timing.** If Derek evaluated a caller's predicate over entries the caller cannot read and returned the match, the predicate would be a direct read oracle. Timing is the smaller problem. (I)

**What the rule removes.** With the rule, a caller's expression only ever touches data the caller could fetch outright. Data-dependent evaluation time then reveals nothing new. This holds only if:
- the visibility decision is made from item metadata (owner, access list) **before** any entry is projected or decoded;
- it uses the authenticated Subject of the call;
- "in full" means the whole unit handed to the evaluator. With per-entry visibility, the item projection (`entryCount`, the entry loop) must be built from the visible entries only.

**What it only narrows (I):**

| Channel | Why it remains | What reduces it |
|---|---|---|
| Existence and count of unreadable items | Skipping them takes time | Index or partition by reader, so they are not visited |
| Other tenants' activity | Lock and CPU contention; event timing | Per-principal budgets. This is T7 (V, spec lines 277-281 and 2208-2209). |
| Co-residency and microarchitectural leakage | The caller's expression runs in the address space that holds other tenants' data | Process or core isolation. T7, not settled here. |
| Visibility changes | An item that becomes unreadable looks like MATCH_NOMATCH | Accept, or suppress the event. Operator's call. |
| A hostile registrant taxing readers | Entries built to make comparisons take their worst case | Registration ceilings; bounded per pair by C(E) |

**What else is needed (I):**
1. **A read-authorisation model for Derek.** None is specified in the memo (V, sections 3.8 and 5). If every permitted caller may read every item, as in Jini lookup, the rule is satisfied trivially and only the last four rows above remain.
2. **Per-call cost budget.** Derek charges the static C(E) for each evaluation it performs. No runtime meter is needed. When the budget is spent, the reply carries an explicit "truncated" flag or the call fails. Never a silent short result.
3. **Per-principal rate limit**, in the same cost units, keyed by the authenticated principal. An option for anonymous callers: index-only queries, with no CEL.
4. **Lower ceilings for lookup** than the proposed profile. A translated template costs tens of units; `maxExprCost` = 1,000,000 is far more than lookup needs (I, from the 10.2 rows as relayed by D).
5. **Event registrations:** a cap per principal on count and on the summed C(E); bound to the lease. Every registry change costs two evaluations per registration (R, C section 3), so this is the standing amplification. When a budget is exceeded, cancel the registration loudly rather than skip events silently.
6. **Registration ceilings:** entries per item, octets per item, markers per item, items per principal; `maxScalarBytes` and K enforced on every value at registration, so no stored item can fail projection later.
7. **Project once.** Decode and check at registration, and keep the projection. Projection decode is not free (spec 3.3, V lines 282-285).
8. **The listener stub is hostile data to Derek.** Derek pins `ATOMIC_DER` and the handler class on it, as the client does on Derek's stub (memo R1, R6).

#### E.7 Proxy kind

- **The field.** `proxyKind` on the registration record, with values DYNAMIC and SMART (the STD-009 terms, R via the memo). It is projected as `field("#item","proxyKind")`. A code-free client adds `field("#item","proxyKind") == 0` to its item predicate.
- **It is a claim** by the registrant, relayed by a lookup service the client may not trust. Derek could check it against the structure of the stored proxy bytes; whether a class-free reader for proxy records exists is NC. Even then it would remain Derek's claim.
- **What enforces it on the client (R, memo R1, R2, R12, R16; I for the combination):**
  1. `MarshallingFormat.ATOMIC_DER` as a required constraint on the bootstrap stub and on every later call.
  2. An exact-class check of the invocation handler.
  3. No code download: JOSS providers omitted, `DownloadPermission` denied, and no codebase loader on the DER decode path. The memo records that denying `DownloadPermission` alone is not sufficient (V, lines 515).
  4. A payload-format check that refuses a JOSS-tagged stored proxy.
  5. In code-free mode the client does not call `getServiceProxy()` unless the kind says dynamic, and it decodes the result under 1 to 4.
- **Cost of a lie.** "Dynamic" on a smart proxy: the decode fails closed and the item is dropped and counted. "Smart" on a dynamic proxy: the service excludes itself. So the field saves round trips. It protects nothing.

#### E.8 What stays outside CEL

| Item | Where it goes |
|---|---|
| Smart proxies with state, shared logic or backend calls | Isolation (overview 1a, V lines 67-76) |
| Legacy Reggie | Unchanged; trusted pair (memo D9); keeps JOSS values for now (R, RL-JGDMS-0132 scope note) |
| JOSS-only clients | Stay on Reggie |
| `MarshalledObject` values, such as `UIDescriptor.factory` | Not in the new API (R, ruling). Standard host UIs (memo D10). |
| Java `ServiceItemFilter` code that reads the service object | Resident client code, after the proxy is obtained (R, C section 4) |
| Proxy preparation and trust verification | Resident code |
| Quantifiers, `maxMatches`, ordering, distinct values, de-duplication across registrars, comparing two items | Host code on Derek or the client |
| Clock and lease expiry | Host code |
| The authorisation decision | Derek's resident code, on the authenticated Subject. Never an expression the caller wrote. |
| Discovery | Memo D12, R11 |

#### E.9 Preconditions

| Precondition | Why this API needs it | Status |
|---|---|---|
| D5 schema chains in Derek's entry records | Field names for the projection | Not built (V, memo line 37) |
| The `@SerialEntry` branch of `EntrySchemaGenerator` | Registrants and the decorator must produce entry records | Unwritten (V in the memo, lines 90 and 234; not re-opened by me) |
| Spec 8.2 (name resolution), open | Hand-written predicates with bare names depend on it. Decorator-generated predicates always use `field(D, f)` and do not. | Open (V, lines 1227 and 2193-2195) |
| Spec 8.5 (applicability key), open | With `#isa` markers by name, plus an optional schema-digest field, the client chooses per query. Derek needs no global answer. | Open (V, lines 1281-1290); the workaround is I |
| `maxScalarBytes` pinned, and enforced by Derek's projection and the client's row projection; K on lists | Claim 3 (V, spec lines 268-276) | Ceilings [PROPOSED]; no candidate-side enforcement in jgdms-cel (R) |
| Spec text: reserved `#` namespaces; the companion-digest rule in 4.2 and 8.1 | Otherwise two implementations will differ | Not written |
| Client-reproducible canonical bytes for a field value; DER form of `java.lang.Class` | Non-scalar template values | NC (R, D section 12 items 1 and 2) |
| Canonical collections (memo B4) | The same | Not on any existing path (R) |
| A module for client-side evaluation | The memo's R13 keeps `jgdms-lookup` free of CEL and splits "when client-side CEL evaluation lands" (V, line 516). The decorator must emit CEL bytes, so its module needs jgdms-cel. jgdms-cel is release 25 and outrigger-service targets 21 (R, B section 5). The release level of `jgdms-lib-dl` is NC. D14 put the wrapper in lib-dl "probably" (V, line 46). | Needs a decision |
| DER stub trust verifier (R2), discovery fixes (R4, R5), DER discovery (D12), the class-free attribute accessor (R14) | Without them the client cannot reach or trust Derek code-free | Open in the memo |
| A read-authorisation model and an identity model for registrants in Derek | E.6 and per-principal quotas | Not designed |
| Transform error wire shape (spec 9.5) | Only if option 2C is taken | T2's, open |
| Bindings (`REF`) | Not needed by anything here | Spec only (R) |

#### E.10 Method sketch (all names illustrative; every method declares `RemoteException`; every record is closed, DER, with no `Object` field)

```
interface RemoteRegistrar extends Remote {
  Registration   register(ItemRecord item, long leaseMillis);
  BootstrapPage  lookUp(Query q, int maxProxies);                  // bootstrap stubs + truncated flag
  ItemViewPage   lookUpViews(Query q, Selection sel, int maxItems);
  EventReg       notiFy(Query q, int transitions, <DER listener stub>,
                        MarshalledInstance handback, long leaseMillis);
  NamePage       typeNames(Query q, String prefix, int max);       // may be left to the decorator
  SchemaNamePage entryClassNames(Query q, int max);
  ValuePage      fieldValues(Query q, int entryPredicateIndex,
                             String className, String fieldName, int max);
  void addAttributes(ServiceIdRecord id, LeaseId lease, List<EntryRecord> sets);
  void modifyAttributes(ServiceIdRecord id, LeaseId lease,
                        List<byte[]> entryPredicates, List<EntryRecord> sets);
  void setAttributes(ServiceIdRecord id, LeaseId lease, List<EntryRecord> sets);
  long[] renewLeases(...);  void cancelLeases(...);                // by id, batched (memo R8)
  ServiceIdRecord getServiceID();  LocatorRecord getLocator();  String[] getMemberGroups();
  QueryLimits getQueryLimits();                                    // a hint; Derek still enforces
}

record Query(ServiceIdRecord id /*optional*/, List<String> requiredTypeNames,
             byte[] itemPredicate /*optional, STD-011 record, Predicate context*/,
             List<byte[]> entryPredicates /*each a STD-011 record, Predicate context*/)
record Selection(List<FieldName> fields, boolean includeBootstrapProxy)
record ItemRecord(ServiceIdRecord id, int proxyKind, List<String> typeNames,
                  MarshalledInstance bootstrapProxy /*DER*/, MarshalledInstance serviceProxy /*DER*/,
                  List<EntryRecord> entries)
record EntryRecord(<schema chain>, <field values, DER>)
```

Service-side accessors, on the bootstrap proxy. The existing accessors are `Remote` interfaces with one method each: `ServiceAttributesAccessor.getServiceAttributes()` returns `Entry[]` (V, `jgdms-lib-dl/src/main/java/net/jini/lookup/ServiceAttributesAccessor.java:37, 46`), `ServiceProxyAccessor.getServiceProxy()` (V, `ServiceProxyAccessor.java:54, 63`), `ServiceIDAccessor.serviceID()` (V, `ServiceIDAccessor.java:34, 44`).

```
interface ServiceAttributeRecordsAccessor extends Remote {          // memo R14
  AttributeRecords getServiceAttributeRecords(Selection sel) throws IOException;
}
interface ArgumentExpressionAccessor extends Remote {               // E.3, option 3A
  ArgumentExpression getArgumentExpression(String methodName, int parameterIndex) throws IOException;
}
record ArgumentExpression(byte[] layoutDigest, byte[] predicate /*optional*/, byte[] transform /*optional*/)
```

Client resident helper (not remote): a reducer that takes an `ArgumentExpression`, the local layout, the rows and a cost budget, and returns the reduced rows or refuses.

#### E.11 Decorator mapping (`ServiceRegistrar` and `SafeServiceRegistrar` over Derek; resident code)

| Legacy member | Mapping | Notes |
|---|---|---|
| `register(ServiceItem, long)` | Build an `ItemRecord`: the proxy as a DER `MarshalledInstance`; type names from the proxy class's closure; `proxyKind` from `Proxy.isProxyClass`; each `Entry` to an `EntryRecord` | An entry or field with no DER form fails the registration loudly. The service can still register with Reggie. |
| `lookUp(tmpl, max)` | Translate, call Derek's `lookUp`, then apply the existing result filter | The secure path |
| `lookup(tmpl)`, `lookup(tmpl, max)` | `lookUp`, then prepare each bootstrap proxy and fetch attributes and the proxy from the service | Returns live objects. In a code-free host: throw, or return dynamic proxies only. Operator's choice. |
| `notiFy(..., MarshalledInstance handback, ...)` | Pass through; refuse a handback whose payload format is not DER | |
| `notify(..., MarshalledObject handback, ...)` | Throw `UnsupportedOperationException` | Re-wrapping would make the client deserialise JOSS bytes handed back by Derek. |
| `getEntryClasses` | `entryClassNames`, then resolve names with the client's own loader only | Unknown names are dropped or raise. No codebase. |
| `getServiceTypes` | `typeNames`, then the same | |
| `getFieldValues(tmpl, setIndex, field)` | `fieldValues(q, setIndex, declaringClass, field)` | Scalars decode. Other values only if they are locally resolvable, approved types (memo 3.6). |
| `getServiceID`, `equals`, `hashCode` | Cached once from the prepared stub and cross-checked (memo R7) | |
| `getLocator`, `getGroups` | Direct | |
| `ServiceRegistration`, leases, events | Resident classes around ids and records (memo R8, R6) | |

**Template translation (R, C section 2; adapted to the 1C record; I):**

| `ServiceTemplate` part | Becomes |
|---|---|
| `serviceID` | `Query.id` |
| `serviceTypes[i]` | `requiredTypeNames` gets the class name. Subtype meaning comes from the registrant's declared closure. |
| each attribute template | one entry predicate: `has(field("#isa","<class>"))`, and one conjunct for each non-null field |
| scalar field | `field("<declaring class>","<name>") == literal` |
| `Float` or `Double` field | C's three forms for NaN, -0.0 and +0.0 |
| non-scalar field | companion digest `==` a bytes literal that the decorator computes with the same codec |
| null field | no conjunct |
| null template, or all parts null | an empty `Query`, which matches every visible item |

Matching by name is weaker than Reggie's hash identity (R, C). A schema-digest conjunct closes that gap when D5 digests exist.

#### E.12 Operator decisions needed

1. Query shape: 1B, or 1C with indexed ServiceID and type names, or one predicate (1A).
2. Does Derek get a transform context (2C), or named-field selection only (2B)?
3. Do the three introspection calls exist on Derek, or does the decorator compute them from item views?
4. Non-scalar fields: a companion digest always, byte equality with an oversize rule, or `has()`-only.
5. A JOSS-born or non-DER field at registration: reject (recommended), or keep it `has()`-only.
6. Does Derek have per-item or per-entry read control at all? If so, at which granularity? If not, the E.6 rule is trivially met.
7. Budgets: when the per-call budget is spent, fail the call or return a flagged partial result? May anonymous callers send CEL at all? What happens to an event registration that exceeds its budget?
8. Lookup-specific ceilings below the proposed profile, and who pins them.
9. Client-side expressions: accept the source and binding in 3A? Is approved-digest mode wanted?
10. Adopt "projected means disclosed" as the stated client rule?
11. Two-sided matching: defer, or scope it.
12. `proxyKind`: the value set, and whether Derek checks it against the stored proxy's structure.
13. Decorator: the legacy `lookup` methods in a code-free host (throw, or dynamic proxies only), and the `MarshalledObject` `notify` (unsupported is recommended).
14. Module: where the decorator and the client-side evaluator live, given the jgdms-cel dependency and its release level. D14's "probably lib-dl" needs confirming or changing.
15. Reserve the `#` namespace prefix in STD-011 (an edit outside this scope).
16. T7: whether evaluation on Derek gets process or core isolation. This section does not settle it.

#### E.13 Not determined

- Whether the code accepts two-step `field().field()` designators (NC, from C). The recommended shape avoids the need.
- Whether a class-free reader exists that could classify a stored proxy as dynamic or smart (NC).
- How Derek identifies a registrant across calls, and whether Reggie's per-method permissions are a usable starting point (NC).
- The per-call input limit on the DER stub (`DerInputLimitControl` is named in memo R2; not opened, NC).
- Release level and preferred-list consequences of a jgdms-cel dependency in `jgdms-lib-dl` (NC).
- Every runtime behaviour. Nothing was built or run.

Files read: `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\SOW-Lookup-CEL-Code-Free-Investigation-2026-11-20.md`; `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md` (sections 0 to 10 and 14); `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` (1.3, 3.1, 3.3, 4.7, 8.1 to 8.5, 9.4 to 9.6, 10.4, 10.5, 15); `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\SOW-Smart-Proxy-Isolation-Architecture-Overview.md` (1a); `J:\lookup-cel-2026-11-20\sections\C_expressibility.md`, `D_additions.md`, `A3_classload.md`, `B_inventory.md` in full; `J:\lookup-cel-2026-11-20\COORDINATOR_NOTES.md`. `A1_filters.md` and `A2_entries.md` were used only through C's and D's citations.


### E.1a (addendum) General logical comparison at Derek

*Author: claude-fable-5-2 (checked), agent a5afe1509e6a0978c. Full report: J:\lookup-cel-2026-11-20\sections\E_addendum.md.*

ADDENDUM to section E (same author, claude-fable-5-2, self-reported; trunk 83c4d4c7a; nothing edited, built or written; no helpers). It answers the operator requirement relayed after my first report: "We also want to use logical comparison of entry data for lookup filters at the server, to reduce downloads." Insert it after E.1 as E.1a; it also amends E.6, E.10 and E.12. Labels as before: V, R, I, NC. All names illustrative. Options with reasons, not decisions.

#### E.1a General logical comparison at Derek

**Short answer.** The recommended query shape (1C) already carries general CEL predicates, not only translated equality. Today's language covers the requirement with no addition. Two things change: the query needs a way to say "no entry matches", with a fail-closed rule for errors, and Derek needs its own cost profile, because general predicates can cost about 10,000 times more than a translated template.

##### What today's language gives an entry predicate

| Need | Today's language | Limit |
|---|---|---|
| and, or, not | `&&`, `\|\|`, `!` (spec 6.4) | none |
| Ordering and numeric ranges | `<`, `<=`, `>`, `>=`; exact cross-type comparison (spec 6.2, 4.3.3) | none |
| Arithmetic in a comparison | `+ - * / %`, checked; overflow is an error, so no-match (spec 4.3.1, 6.3) | none |
| One of several values | `x in ["a","b"]`, a literal list (spec 6.5) | cheap |
| Prefix, suffix | `startsWith`, `endsWith` (spec 6.6) | cost row for these two is NC by me; D gives string comparison as `1 + min(S, literal length)` (R) |
| Substring | `contains` with a literal needle | needle of at most 15 octets at the proposed ceilings; one call is about 983,000 of the 1,000,000 units (R, D section 0 item 4) |
| Presence | `has(f)` (spec 6.8) | none |
| Class test | `has(field("#isa","<class>"))`, a projection convention (R, C) | none |
| Either-or values | `c ? a : b` (spec 6.7) | none |
| Membership in a list field | `x in f` | unusable for string needles of 15 octets or more (R, C) |
| Case-insensitive match, glob, regex | not available | D: defer or reject; no surveyed use (R) |

All rows R from C and D except where marked; I did not re-open spec section 6.

##### How a predicate over several entries of one item is expressed

| # | Form | Expresses | Does not express | Cost and risk |
|---|---|---|---|---|
| M1 | A list of entry predicates; the host requires each to match some entry (my 1C as first written) | "has an entry like A **and** an entry like B". **Or** across entries also works inside one predicate: `(has(#isa A) && condA) \|\| (has(#isa B) && condB)` evaluated per entry means "some A-entry meets condA, or some B-entry meets condB" (I). | "no entry like X"; mixed forms such as "(A and B) or C" | Lowest. Host may stop at the first matching entry. |
| M2 | M1 plus a second list, `excludedEntryPredicates`: no visible entry may match | and, or (as M1), and **not-exists** | "(A and B) or C" across entries | Same per-pair cost. Needs the error rule below. |
| M3 | Entry predicates Q0..Qn plus a small host-evaluated combination in a fixed structure (a list of clauses, each a list of signed indices) | Any and/or/not over "some entry matches Qi" | Comparing two entries with each other | Same per-pair cost. No short-circuit across predicates unless the host orders them. |
| M4 | As M3, but the combination is a second CEL predicate over a host-built namespace of booleans (for example `field("#any","0") && !field("#any","1")`) | As M3, plus item fields in the same formula | As M3 | No new structure, but the second stage reads outcomes of earlier evaluations. Spec 9.6 says an evaluation must not observe "no previous evaluation's outcome" (V, lines 1383-1385). This needs the spec owner's reading before it is proposed. |
| M5 | One item-level predicate over entry slots `e0..eK` (C's slot form) | Everything above, and comparison between two entries | Items with more than Ks entries (fail closed) | 1024-node limit; two-step designators NC in code (R, C). D rejected bounded `exists` for want of this need (R). |

**Recommended: M2 now; M3 if a real filter needs a mixed form.** Reasons: M2 covers and, or and not with one added list. M3 is a strict superset and can replace M2 later without changing the predicates. M4 has a spec question. M5 is needed only to compare two entries of one item, and no surveyed filter does that (R, C section 4 item 4; D section 9).

**Error rule for exclusion (I; this is a fail-open hazard if missed).** In predicate context an error is no-match (spec 9.4, V lines 1350-1356). If the host treats an erroring excluded predicate as "did not match", the item is *selected* because of an error. That inverts the spec's rule that a filter "can only ever fail to select". So:
- If any evaluation of an excluded predicate yields an error, the item is no-match.
- The author therefore guards an excluded predicate with the class marker, for example `has(field("#isa","Location")) && field("Location","floor") == 3`, so that entries of other classes yield `false`, not `ABSENT_FIELD`. C relies on the same short-circuit (R, C section 2 edge cases; spec 6.4 not re-opened by me).
- The same three-valued care applies to M3 and M4.

##### What this does and does not move to Derek

- **Moves:** any `ServiceItemFilter` logic that reads only attribute values the service registers. Each false positive removed at Derek saves a bootstrap-proxy preparation, an attribute call to the service and, in the code-bearing tier, a proxy download (R, A3 sites 14 to 17 and 20 to 24).
- **Does not move:** a filter that reads the service object (R, C section 4 item 1), and anything the service does not publish as an attribute. A service that wants such filtering must publish the value (qa already has `TestServiceIntAttr.val`, R).
- **Authenticity.** Derek's attributes are claims. The verdict at Derek is pre-selection only: "a filter false-positive costs bandwidth, never integrity" (spec 8.1 item 3, V lines 1217-1223). A client that needs the answer to be true evaluates **the same predicate bytes** again, in resident code, over the class-free attribute records it gets from the authenticated service (memo R14). That uses the same client-side evaluator as E.3, so it adds no new mechanism, but it does make the client-side CEL module a dependency of ordinary lookup, not only of the argument-reduction idea.

##### Effect on Derek's per-call cost bound

Bound for one call (I): `N × ( C(item predicate) + Emax × Σ C(Qi) )`, where N is the number of visible candidate items after the index step, Emax is the registration ceiling on entries per item, and the sum is over required and excluded entry predicates.

- A required predicate may stop at its first match. An excluded predicate must scan every entry unless it matches. The worst case is the same.
- Translated templates cost tens of units each. A general predicate with one `contains` costs about 983,000 (R). At the proposed profile, 16 predicates over 64 entries is about 10^9 units **per item**. So the proposed STD-011 ceilings are far too loose to be Derek's only limit.
- Options for Derek:
  1. A lookup profile with a much lower `maxExprCost` per predicate and a ceiling on the summed cost of a query. Simple; it shuts out `contains` unless the ceiling stays near 10^6.
  2. A smaller scalar ceiling for lookup attributes, enforced at registration (for example 1 KiB), with the cost computed against that ceiling. Real work then falls about 64-fold and `contains` becomes affordable. But `CostModel` uses S as a fixed constant (R, B: `CostModel.java:93`). Whether it can be given a profile value is NC. This is a cost-model change of the kind D describes for (c4), not a language change.
  3. Keep the profile and rely on the per-call and per-principal budgets of E.6. Simplest to specify; a single call can still be large.
- In every option Derek charges the static C(E) for each evaluation it performs and stops at the budget with an explicit flag or a failure (E.6 item 2). No runtime meter is needed (spec 10.5, V lines 1582-1590).
- Event registrations: a general predicate is re-evaluated on every relevant change, twice per registration (R, C section 3). The cap on the summed C(E) of a principal's registrations (E.6 item 5) becomes necessary, not optional.

##### Effect on the timing and cross-tenant analysis (amends E.6)

1. **The visibility rule becomes mandatory.** With ordering, prefix and `contains`, a predicate is an exact read oracle through the result alone. Over unreadable entries it would read out a field in a few dozen calls. Timing is secondary.
2. **Exclusion adds an absence oracle.** "No entry matches" reveals that something is absent. Over visible entries that is by design. With per-entry visibility, hidden entries must be invisible to both lists. An excluded predicate then answers for the visible entries only, which is the correct behaviour.
3. **Richer data-dependent timing.** `contains` scans, short-circuit order and first-match stops all depend on entry values. Under the rule this reveals only readable data. It does make the co-residency channel (T7) more usable, because a caller now has a cheap way to create long, value-dependent work next to other tenants' calls.
4. **The budget is a signal.** A "truncated" flag shows how many evaluations ran. That is a function of visible data only, provided hidden items are not visited or charged.
5. **A registrant can tax readers more.** Long strings near the scalar ceiling make every reader's `contains` take its worst case. Option 2 above (a small lookup scalar ceiling at registration) limits this directly.
6. **Unchanged:** the content channel is removed by the rule; existence, activity and co-residency channels are only narrowed; T7 is not settled here.

##### Amendments to the earlier text

- **E.10 sketch:** `Query` gains `List<byte[]> excludedEntryPredicates` (M2). If M3 is chosen instead, `Query` carries one predicate list plus the clause structure.
- **E.9 preconditions:** the client-side evaluation module is needed for authenticated re-checking of lookup predicates as well as for E.3.
- **E.12 decisions, added:**
  17. Multi-entry form: M1, M2 (recommended), M3, or M5; and whether M4 is acceptable under spec 9.6.
  18. Confirm the fail-closed rule: an error in an excluded predicate makes the item no-match.
  19. Derek's cost profile for general predicates: lower `maxExprCost`, a smaller lookup scalar ceiling with a parameterised cost model, or budgets only.
  20. Whether the client re-evaluates the predicate over the service's authenticated attribute records by default, or treats Derek's verdict as sufficient pre-selection.
- **Not determined, added:** the cost rows for `startsWith` and `endsWith` (not opened by me); whether `CostModel` can take a profile-specific S (NC); the wall-clock time of one worst-case `contains` evaluation (not measured; D section 12 item 6 says the same).


### F Blind review of D and E

*Author: claude-opus-5-5 (checked), agent a158677848cc4d8cf; helpers afb13a3c67fdeba6a, a3a25686766d50cfd (Sonnet). Full report: J:\lookup-cel-2026-11-20\sections\F_review.md.*

claude-opus-5-5

Section F: blind adversarial review of D (D_additions.md) and E (E_api_main.md + E_addendum.md). Trunk 83c4d4c7af7bcb14bd33e07a68bbaa4ee83f36d3 (VERIFIED, `git rev-parse HEAD`, branch trunk). I checked out, edited, built and committed nothing, wrote no file, ran no probe and no Python, and wrote nothing to the register. The producer was Fable and I am Opus, so this review is cross-model.

Skills loaded: jgdms-board-review (SKILL.md, plus Guidance Part I G1-G13), handover-composition, production-code. I did not load rag-file or code-comments: this work touches no scan data and asks no judgement of code comments. TRAINING_DOCS_INDEX.md has no JGDMS or CEL entry (I grepped for jgdms|cel|std-011|std-006|AI_AGENT), so no rule-7 handover finding applies. The bounding corpus here is the board guidance, which the brief named.

#### Summary

- **No HIGH findings.** Eight MEDIUM and nine LOW.
- **The bounds in D hold.** I re-derived the glob bound, the 15-octet `contains` limit, and the `in`, `exists` and regex numbers. All are correct.
- **The weak point is D's only "adopt", option (c2), and E's SHA-256 variant (iv).**
  - Both rest on "the client encodes the template value with the same codec". That is not a definition: the DER codec has three entry points that give different bytes for the same value (F3).
  - DER maps -0.0 to +0.0, so DER byte equality is LOOSER than Jini equality in one direction, not only stricter (F4).
- **E's security analysis covers the evaluation well. The gaps are in the PATHS:**
  - the exclusion rule fails open when no error is raised (F1);
  - a Derek-side cost profile breaks the client's re-check of the same predicate (F2);
  - the client-side expression has no named mechanism binding it to an authenticated principal (F5);
  - Derek's outbound listener call is a confused deputy (F6);
  - registrants can read clients' predicates (F7);
  - ServiceID ownership is not bound (F8).

#### Findings, most severe first

**F1. MEDIUM. CONFIRMED (text). E.1a exclusion error rule, together with E.1 rule 2.**
- E.1a: "If any evaluation of an excluded predicate yields an error, the item is no-match."
- E.1 rule 2: "Check `isUndecodable()` before evaluating." It states no outcome for that check.
- The obvious implementation skips the undecodable entry. No evaluation runs, so no error is produced, and the item passes the exclusion.
- Two other paths do the same without any error: a per-call budget that runs out partway through an item's entries (E.6 item 2), and a companion value that compares `false` because of encoding differences (F3, F4).
- Scenario: the item holds Status{state="REVOKED"} and that entry is undecodable. The excluded predicate is `has(field("#isa","Status")) && field("Status","state")=="REVOKED"`. The host skips the entry and the item is SELECTED.
- This breaks spec 9.4 (STD-011 L1352-1356): "a filter can only ever fail to select".
- Fix (true by construction, G7): an item passes the exclusion only if the excluded predicate yields exactly `false` on every visible entry. Any other outcome makes the item no-match: an error, undecodable, not evaluated, or out of budget.

**F2. MEDIUM. CONFIRMED (arithmetic and code). E.1a option 2 ("a smaller scalar ceiling for lookup") against E.1a "Authenticity" (the client re-runs "the same predicate bytes") and E.3 item 4.**
- `CostModel.java:93` has `private static final long S = CelCeilings.MAX_SCALAR_BYTES;`. S is a compile-time constant, and `CelCeilings.java:52` ties it to `ObjectCodec.MAX_COLLECTION`. This answers E's NC: CostModel cannot take a profile value of S today.
- Worked example, a `contains` needle of 900 octets:
  - At S=1024 the cost is 1+1024×900+2 = 921,603. Derek admits it.
  - A client verifier at the default S=65536 computes 1+65536×900+2 = 58,982,403 and REJECTS the same bytes.
  - If the client instead verifies at S=1024 but its projection does not enforce 1 KiB on the service's records, a 65,536-octet string costs up to 58,982,400 comparisons against a bound of 921,603. Claim 3 is then broken by a factor of 64.
- The service's attribute accessor is not bound by Derek's registration ceiling.
- Fix: the profile (S, maxExprCost) travels with the predicate. Every projection that evaluates it enforces that S: Derek at registration, and the client over records and rows. A larger value makes the record undecodable. Option 2 needs a change to jgdms-cel.

**F3. MEDIUM. CONFIRMED (text and code) for the break; one link UNVERIFIED. D §6 items 1 and 11 ("adopt") and E.2a (iv) and E.11.**
- Quoted: "The client encodes the template value with the same codec". The DER codec has three entry points, and they produce different bytes for the same value:
  1. **Untyped `writeObject`.** It "deliberately still rejects a bare collection with `UnsupportedOperationException`" (`DerObjectStreamCodec.java:1243-1246`). In stream format it writes an @AtomicSerial value as a dedup record (`:1218-1229`).
     - Appendix C §C.5.3 L541: `DedupNestedAtomicRecord ::= SEQUENCE { schema SchemaChainRef, ...}`.
     - Appendix C L829-831: a chainRef's bytes "depend on what appeared earlier in the stream".
     - Only `[16]` collection interiors are kept a "pure function of (declaredToken, value)".
  2. **Typed `writeCollection(coll, declaredToken)`.** Its bytes depend on the declared token.
  3. **The field form inside a record.** Its bytes depend on the declared field type: the Any category tag versus the declared tag, and the declaration-keyed collection discipline (STD-006 L833-847).
- Further gaps:
  - `java.lang.Class` has no wire type: the closed type model at STD-006 L816-826 lists none. This answers D's NC 12.1.
  - Arrays other than byte[] have no general rule. STD-006 open item 10 is unresolved (RELAYED, helper).
  - Reggie's DER mode marshals each field with `new MarshalledInstance(val, …ATOMIC_DER)` (`EntryRep.java:236-241`, VERIFIED). That is the untyped, runtime-type path.
- Scenario: `Doc{Point a; Point b;}` is encoded as one stream record, so b's schema chain is a chainRef to a's. A client that encodes template b standalone writes the full chain. The bytes differ, so the template never matches. Under exclusion (F1) the item is selected instead.
- D's own reversal condition (§11, "Field-slice bytes not reproducible by the client encoder") is therefore met for a slice of a shared-stream record.
- Fix: define the companion bytes, or the bytes hashed under (iv), as the record-level canonical form of the value under the field's DECLARED wire type, with one value per encoding. This is STD-006 §7.8 / STD-008 §16, not the stream form. Derek computes it by strict decode then re-encode, never by slicing. Arrays and Class are rejected or mapped explicitly. Add conformance vectors.
- UNVERIFIED: that `MarshalledInstance(val, ATOMIC_DER)` routes through `DerObjectStreamCodec.writeObject`.

**F4. LOW. CONFIRMED (code). D §6 item 2 and E.11, the Float/Double row.**
- `DerObjectStreamCodec.java:902`: `else if (Double.doubleToRawLongBits(v) == 0x8000000000000000L) bits = 0x0000000000000000L;`. Float does the same at :886. STD-006 L2015-2017 states the same rule.
- So DER encodes Double -0.0 and +0.0 identically. Java `equals` and JOSS bytes distinguish them.
- D presents DER equality only as "stricter than equals" (LinkedHashSet). It is also looser:
  - (c2) matches Point(-0.0) against a template of Point(0.0).
  - E.11 uses C's -0.0 form, `f == 0.0 && 1.0/f < 0.0`. It can never match a value stored through DER.
  - The +0.0 form matches entries that were registered as -0.0.
- Pre-existing G8 conflict to send to the spec owner (outside D and E):
  - STD-011 4.3.4 L442-444: -0.0 "MUST be emitted with their exact bit pattern. This composes with STD-006…"
  - STD-006 decode rejects -0.0 bits (L2016-17; `DerObjectStreamCodec.java:1408, 1434`).
  - So a transform that returns -0.0 (E option 2C, D H2) produces bytes a strict STD-006 reader rejects.

**F5. MEDIUM. PLAUSIBLE (API facts VERIFIED). E.0a row P4 and E.3 item 1.**
- E.3 item 1: "The client keeps (P's authenticated server principal, expression digest) together." E names no mechanism.
- The only client-side API is `BasicInvocationHandler.authenticateServer(Method)` (`BasicInvocationHandler.java:1867-1891`):
  - it opens its own connection, then aborts it;
  - it returns `Collections.emptySet()` for TCP, HTTP and UDS endpoints (:1883-1890).
- Every later call authenticates again. Without a `ServerMinPrincipal` constraint, the default accepts any trusted principal on each connection.
- So the recorded principal, the expression's author and the receiver of the reduced rows can be three different parties. E.4's rule "disclose only to that principal" cannot then be enforced.
- Fix, mandatory and in order:
  1. Call `authenticateServer`. If it returns the empty set, refuse client-side expressions.
  2. Set `ServerMinPrincipal` to that set on the prepared proxy BEFORE calling `getArgumentExpression`.
  3. Send every argument call through that constrained proxy.
- Reuse: `ServerIdentityPin` in jgdms-pref-class-loader already calls `authenticateServer` (RELAYED).

**F6. MEDIUM. PLAUSIBLE (Reggie behaviour CONFIRMED). E.6 item 8 and E.0a row P2.**
- E.6 item 8: "Derek pins `ATOMIC_DER` and the handler class on it". Nothing is said about the Subject or constraints of Derek's OUTBOUND notify call.
- Reggie, VERIFIED:
  - default `listenerPreparer` is `new BasicProxyPreparer()`: no constraints, no verification (`RegistrarImpl.java:5227, 5352-5353, 5834-5835`);
  - event tasks run under `context` captured in the Initializer (:5226, :6462-6466);
  - the Initializer runs inside `Subject.callAs(loginContext.getSubject(), …)` (:575-578).
- So notify runs under Reggie's login Subject, with whatever constraints the client-supplied stub carries.
- Scenario: client C registers an event whose listener stub names a third party T's endpoint and object, with ClientAuthentication.YES. Derek connects to T authenticated as Derek and delivers an event carrying C's chosen handback.
- Derek also becomes an SSRF origin, and C can observe the result through lease cancellation when delivery fails.
- This is the board's role-reversal hunt.
- Fix: Derek's listener preparer sets constraints that include ClientAuthentication.NO, notify runs under no Subject, and listener endpoint classes are restricted.
- NOT VERIFIED: the body of `Security.withContext`.

**F7. MEDIUM. PLAUSIBLE. E.6 and E.1a "Effect on the timing…" miss a reverse channel: registrants can read clients' predicates.**
- The visibility rule protects data from the caller. The secret at risk here is the CALLER's predicate, and the registrant controls the candidate data.
- (a) Result channel. Registrant R registers items with chosen values. A matching client then prepares R's bootstrap proxy and calls R's service, so R learns which value matched.
  - With `<`, `startsWith` or `contains`, a literal falls to bisection: about 8 registrations per octet with `<`.
  - Jini's equality templates need an exact guess per registration.
- (b) Timing channel. If event predicates are evaluated inside R's `modifyAttributes` call, R's latency depends on R's own data against W's literals.
- (c) Cost. Per-principal caps (E.6 item 5) do not bound the total work per change across principals.
- Fix:
  - state that "a lookup or event predicate is not confidential from registrants";
  - evaluate event predicates off the registrant's call path, with a global per-change ceiling.

**F8. MEDIUM. CONFIRMED (Reggie code); the Derek scenario is PLAUSIBLE. E.1 1C (indexed ServiceID), E.6 ("owner, access list"), E.10 `register(ItemRecord…)`.**
- `RegistrarImpl.registerDo` :5710-5712: `SvcReg reg = serviceByID.get(nitem.getServiceID()); if (reg != null) deleteService(reg, now);`.
- I grepped :5660-5705 for Subject, principal and owner and found nothing.
- So any caller replaces any ServiceID's registration. E makes ServiceID the commonest indexed query but never binds an owner (E.13: identity model NC).
- Scenario: an attacker re-registers a victim's ServiceID with its own proxy. Every ServiceID lookup is redirected until the victim re-registers. Only F5's pinning protects the client.
- Fix: the first registration binds the ServiceID to the registering principal. Replacement, modify and lease operations require that same principal.

**F9. LOW. CONFIRMED (arithmetic from `CostModel.callCost` :274-279 and And :165-167). D §0 item 4 and the E.1a table.**
- The 15-octet `contains` limit applies to the SUM of needle lengths in a predicate, not to each call.
  - Needles 8 and 8: 2×(1+65536×8+2)+1 = 1,048,583. Rejected.
  - Needles 7 and 8: 458,755+524,291+1 = 983,047. Admitted.
- Fix the wording to "Σ needle octets ≤ 15 per predicate".

**F10. LOW. CONFIRMED. E.1: "Names starting with `#` cannot be Java class names."**
- At the wire, STD-006 L2296 is `className UTF8String (SIZE(1..1024))`. I grepped STD-006 for 4 syntax patterns (binary name, valid Java name, identifier syntax, "class name MUST") and found no rule.
- A registrant can therefore name a class "#isa" or "#der…" and collide with Derek's synthetic namespaces.
- Fix: Derek rejects at registration any class name that starts with '#' or is not a JVM binary name.

**F11. LOW. CONFIRMED. The `#isa` / `#types` markers (E.1, E.11, C).**
- These markers put a class name in the qualified selector's fieldName slot. That slot is capped at 255 octets:
  - STD-011 Appendix B L286 `fieldName UTF8String (SIZE(1..255))`;
  - `CelDecoder.java:295` and `CelCeilings.java:58`.
- Class names may be up to 1024 octets (Appendix B; `CelCeilings.java:61`; STD-006 L2296). A class name of 256-1024 octets cannot be tested, so its translation fails verification (loudly).
- Fix: put the name in the className slot instead, `has(field("<class>","#isa"))`. Spec 8.3 already compares class names exactly.

**F12. LOW. CONFIRMED (text). D and E disagree on fields that have no DER form.**
- D §6 item 7: a `MarshalledObject` field means "The projection omits the field; it must not reject the entry."
- E.2a rejects such fields at registration, and E.11 `register` says "An entry or field with no DER form fails the registration loudly."
- Under E, a legacy service with a UIDescriptor, or with `ServiceType`/`Class` fields (no DER wire type, see F3), cannot register through the decorator.
- Neither document notes the disagreement. Operator decision.

**F13. LOW. CONFIRMED (text). E.2 replacements table against E.11 `getFieldValues`.**
- E.2: `fieldValues` returns "the companion digest for a non-scalar field".
- E.11: "Other values only if they are locally resolvable".
- A digest cannot be decoded back into a value, so legacy `getFieldValues` on non-scalar fields cannot be implemented.
- Fix: `fieldValues` returns the record-level DER value; the digest stays in the projection only.

**F14. LOW. PLAUSIBLE. 2B selection.**
- E.2 rejects 2C because a list literal can repeat a field about 1023 times. But E.10 `Selection(List<FieldName> fields, …)` is a List with no dedupe and no cap, so the same amplification is possible.
- Fix: set semantics (sorted, duplicates rejected) and a count ceiling.

**F15. LOW. D (b1) item 4 and D (a) item 3.**
- D (b1) item 4: "Allocation. None per evaluation". The probe does not show this: `GlobProbe.parse` calls `codePoints().toArray()` on the subject, up to 65,536 ints (256 KiB). An allocation-free matcher is plausible but untested.
- D (a) item 3: "the length check is O(1)". This is false in Java for a UTF-8 octet length, which needs a scan. The effect is a constant-factor under-charge.

**F16. LOW. E.10 and E.11 omit `ServiceMatches.totalMatches`.**
- The legacy `lookup(tmpl, max)` returns a total count. `BootstrapPage` carries only a truncated flag.

**F17. LOW. PLAUSIBLE. E.2a: "Derek checks the payload-format tag … and never calls get()".**
- `payloadFormat` is a String set by the sender and ignored by `equals` (`MarshalledInstance.java:190-192`, RELAYED).
- For values Derek never decodes (bootstrapProxy, serviceProxy, handback), a registrant can label JOSS bytes "DER". Only the client's DER decoder then stops them, so RL-JGDMS-0132's "DER only" is not enforced by Derek for these fields.
- Reuse: Reggie already has `checkItemPayloadFormats` (`RegistrarImpl.java:5647`, called at :5683). I did not read its body.

**Minor notes (PLAUSIBLE):**
- E.11's legacy `lookup` returns attributes fetched from the SERVICE, and nothing re-checks them against the template.
- If Derek gains per-item read control, a decorator or LookupCache shared across callers' Subjects, or calling from background threads, bypasses E.6's per-Subject rule.

#### What both D and E assume that is false or unproven

1. That "canonical DER bytes of a value" is one well-defined thing. False: the bytes depend on the entry point and on the declaration (F3).
2. That DER equality refines Jini equality. False for -0.0 (F4).
3. That '#' names cannot collide with real class names. False at the wire (F10).
4. That a predicate's cost bound holds wherever the bytes are evaluated. True only if the same S is enforced on candidates (F2).
5. That "the language is bounded whoever wrote it" makes the PATH safe. It does not: the gaps are in Subjects and bindings on calls (F5, F6, F8).

#### Checked and found sound

- **`contains` 15-octet arithmetic.** 1+65536×15+2 = 983,043 and 16 octets gives 1,048,579. This matches `CostModel.java:274-279` and `CelVerifierCostGateTest.java:43-45` (RELAYED, helper), and the spec 10.2 rows at L1427-1433 (VERIFIED).
- **H4 glob bound.** My own derivation agrees with (|s|+1)×|p|+1:
  - across all middle segments the matcher tries at most m positions, at most |p| comparisons each;
  - greedy leftmost placement is correct because every segment has a fixed length;
  - the cost row in octets is sound because code points ≤ octets.
  - Glob limit is 15 octets: 1+65537×15+2 = 983,058; 16 gives 1,048,595. The probe output itself is RELAYED.
- **(c4) `in` over a field.** A 14-octet string needle is admitted and a 15-octet one rejected. A field needle costs about 2^32.
- **(c5) `exists`.** 65536×16 > 10^6.
- **(b2) regex.** About 15 states fit the cost row.
- **H1 refutation and H6 determinism.** Both correct.
- **E.1a M1.** ∃ distributes over ∨, so "or" across entries works inside one predicate. The guard idiom `has(#isa) && …` is correct under spec 6.4 absorption (`false && error` = `false`).
- **E.1a cost arithmetic:**
  - 1024 × 983,043 ≈ 1.007×10^9, as stated;
  - the 64-fold reduction for S = 1 KiB is right;
  - "tens of units" for a translated template holds: by my count `has(field("#isa","Location")) && field("Location","floor")==3` is 6 units, and a digest compare is 35.
- **E.1 rule 2.** Matches spec 15 item 9 (L2216-2224) and Outrigger `FilterEval.java:240`.
- **P1 binding.** `ClientSubject` / `ServerContext` exist (RELAYED).
- **Other E points.** E.7 "protects nothing" is accurate. The citations D relies on are correct (spec 230-231, 162-164).
- **Precedent for (c2).** Reggie's DER mode already matches per-field DER `MarshalledInstance` payloads byte for byte (`EntryRep.java:236-241, 389-399`, VERIFIED). (c2) is not a new matching regime.
- **E.13 NC on two-step `field().field()` designators.** Likely answerable as "supported": `CelDecoder.java:267-284` builds step lists (up to 16) and `Evaluator.java:233-239` walks them (RELAYED).
- **Reuse, from the production-code duplication check.** E should extend existing code rather than write new projections: Outrigger `EntryProjection` and its private `FieldMapProjection` (:829), `FilterEval:240`, benchmarks `RecordProjection`, Reggie `checkItemPayloadFormats`, `authenticateServer` and `ServerIdentityPin`.

#### UNVERIFIED-NEEDS-BUILD or not done

- **No runtime probes.** jgdms-cel has no `target/classes`, and I have no build grant, so no CostModel run. jgdms-der's classes exist but may be stale, so I did not run the -0.0 encode either. Both rest on reading the source at trunk.
- **Not established:**
  - that `MarshalledInstance(val, ATOMIC_DER)` routes through the stream `writeObject` path;
  - the body of `Security.withContext`;
  - the body of `checkItemPayloadFormats`;
  - a glob probe re-run and escape handling;
  - any Rust or Haskell behaviour.
- **Which E file I reviewed.** E_api.md and E_api_main.md are both 33,570 bytes and 327 lines; I did not diff them. I reviewed E_api_main.md as briefed.

#### Helpers (Sonnet; neither spawned further agents)

- **afb13a3c67fdeba6a** (self-reported claude-sonnet-5-5): STD-006 / Appendix C facts on back-references, schema chains, Any, collections, Class, arrays and floating point.
- **a3a25686766d50cfd** (self-reported claude-sonnet-5-5): code facts on JERI server-principal APIs, Reggie listener handling, MarshalledInstance.equals, EntryRep, jgdms-cel undecodable handling and steps, and CostGateTest lines.

Every line cited above as VERIFIED I re-opened myself.


### G. Authors' replies to F

Added 2026-11-28, on the operator's word ("Yes D and E authors should reply to section F."). The authors are the same agents that wrote D and E. They were resumed, and each replied to the findings that touch its own section. F's findings above stand unedited (SOW 4.F). Full texts of the replies: `J:\lookup-cel-2026-11-20\sections\D_reply_F.md` and `E_reply_F.md`. Model: claude-fable-5-2 for both, checked in the transcripts. Neither used helpers. Both read the source at the pin and checked with `git diff` that the cited files had not changed by df1e7e4e.

**Coordinator summary**
- **Disputes.** Neither author disputes a finding. D: 7 ACCEPT and 1 ACCEPT WITH AMENDMENT (F3). E: 11 ACCEPT and 5 ACCEPT WITH AMENDMENT (F2, F3, F6, F11, F16).
- **F3: D refutes F's UNVERIFIED link.** `MarshalledInstance(val, ATOMIC_DER)` writes the record-level capture, `new DerObjectStreamCodec(false)`, which has no SchemaChainRef. So the `Doc{Point a; Point b}` shared-chain case cannot arise on that path. Coordinator VERIFIED at 83c4d4c7a: `DerMarshalOutputStream.java:107-109` and the `DerObjectStreamCodec` javadoc at lines 313-320.
  - D proposes the definition: canonical bytes = `schemaDigest ++ payloadBytes` of the value's own record-level capture, never a slice.
  - Still open: collection-valued fields, arrays other than `byte[]`, `Class` fields, and the conformance vectors.
  - E, which did not see D's reply, withdraws digest matching until such a form is pinned. Non-scalar fields stay `has()`-only, and the decorator throws on a template with a non-null non-scalar field.
- **F12: the authors now disagree in the opposite direction.**
  - D now makes no recommendation, and leans to rejecting the registration in the native API, with the decorator choosing whether to strip.
  - E now recommends that the decorator omits the field (configurable). Its reason: rejection would bar every service that carries a `UIDescriptor`.
  - Both agree it is the operator's decision. This affects R14.
- **Changes to recommendations (section 4), not applied to its table:**
  - R6: "adopt in principle" once D's definition is pinned with vectors.
  - R16: E withdraws the smaller-S lookup profile (E.1a option 2) for now. Derek uses a lower `maxExprCost` at the standard S. A different S waits until STD-011 carries S in the expression record.
  - R17: E makes F5's three-step binding mandatory: authenticateServer, then ServerMinPrincipal, then the constrained proxy.
  - R11, R22, R23, R24 and R25: accepted as written, with F11's marker form `has(field("<class>","#isa"))`.
- **New operator decisions (from E):**
  - an event-sender identity, if events are to be authenticated (F6);
  - the meaning of "same principal" for ServiceID ownership (F8);
  - the handback as plain bytes or as a `MarshalledInstance` (F17);
  - whether to state that predicates are not confidential from registrants (F7).
- **Spec-owner item** (both authors): the -0.0 conflict between STD-011 4.3.4 L442-444 and STD-006 L2014-2018. A template cannot select -0.0 in a value stored as DER, and C's `1.0/f < 0.0` idiom never matches.

#### G.1 Section D author (ab652fcba9612c654)

claude-fable-5-2 (self-reported)

##### Section D author's reply to review F

Pin: 83c4d4c7af7bcb14bd33e07a68bbaa4ee83f36d3. The working tree is now at df1e7e4e, so I ran `git diff --stat` from the pin to HEAD over every file cited below (`DerObjectStreamCodec.java`, `MarshalledInstance.java`, `EntryRep.java`, STD-006, STD-011, `jgdms-cel`). It printed nothing, so the lines I opened are the pinned lines. I edited, built and committed nothing, and used no helpers this round.

Labels: V = VERIFIED (I opened it this session), R = RELAYED.

###### Summary

| Finding | Verdict | Effect on D |
|---|---|---|
| F3 | ACCEPT WITH AMENDMENT | (c2) moves from "adopt" to "adopt in principle; not ready until the bytes are defined". One link in F's scenario is refuted. |
| F4 | ACCEPT | D §6 item 2 corrected: DER equality is also looser than Jini equality for -0.0. |
| F9 | ACCEPT | The 15-octet limit is per predicate, summed over needles. |
| F12 | ACCEPT | The D/E disagreement is real and is an operator decision. |
| F15 | ACCEPT | Two claims downgraded; no recommendation changes. |
| F10, F11 | ACCEPT | D §8 relayed C's marker form; the form changes. |
| F1, F2 | ACCEPT as they bear on D | Notes added to (c2) and to the cost statements. |

###### F3. ACCEPT WITH AMENDMENT

**Accepted.** "The client encodes the template value with the same codec" is not a definition. The entry points differ:
- Untyped `writeObject` rejects a bare collection (`DerObjectStreamCodec.java:1243-1246`, V).
- STD-006's closed type model has no `java.lang.Class` (L816-826, V). This settles my open item 12.1: a `Class` field has no DER form.
- D §6 item 3 said the bytes are "a slice made at projection". I withdraw that. A slice of an entry record is the declared-type field form, which is not what a client produces by encoding the value alone.

**Amendment: F's unverified link is refuted.** `new MarshalledInstance(val, …ATOMIC_DER)` does not use the stream dedup form:
- For an `@AtomicSerial` value, `DerMarshalInstanceOutput.java:207-212` writes `ObjectCodec.encodeHierarchy(obj, chain)` as the payload. The schema travels as separate `schemaBytes` and `schemaDigest` fields (V).
- Other items go through `DerMarshalOutputStream.recordLevelCapture` (`:229-237`), which builds `new DerObjectStreamCodec(false)` (`DerMarshalOutputStream.java:108-109`, V).
- That mode is "canonical record-level full forms only", with "no `SchemaChainRef`" (`DerObjectStreamCodec.java:315-320`, V).

So the `Doc{Point a; Point b}` chainRef scenario cannot arise on the path Reggie's DER mode uses (`EntryRep.java:236-241`, V). One well-defined form already exists in the code; D failed to name it.

**Proposed definition.** The canonical DER bytes of a field value are `schemaDigest ++ payloadBytes` of the record-level capture of that value encoded on its own. That is exactly what `new MarshalledInstance(value, context, ATOMIC_DER constraints)` holds. Derek takes them from the per-field `MarshalledInstance`, or computes them by strict decode then re-encode; it never slices a record.

The digest must be included. `MarshalledInstance.equals` compares only `hash` and `payloadBytes` (`:993-1003`, V), and the payload of an `@AtomicSerial` value carries no schema.

**Still needed to settle it:**
1. Collection-valued fields. The untyped path rejects them, so the rule must name the typed route and the token taken from the field's declaration.
2. Arrays other than `byte[]`. The code has a `[9]` array branch (`:1060-1065`, V); STD-006 open item 10 is unresolved (R).
3. `Class` fields: map to the name string by an explicit rule, or leave unmatched.
4. Conformance vectors, and a home for the rule in STD-006 §7.8 or STD-008.
5. Whether Derek's registration form carries per-field `MarshalledInstance` values (E's design).

**Corrected D text (§6 items 1, 3, 11):** "The companion value is `schemaDigest ++ payloadBytes` of the value's record-level capture, never a slice. Recommendation: adopt in principle; not ready until that form is pinned with vectors, including the collection and array rules."

###### F4. ACCEPT

- The encoder maps -0.0 to +0.0 for `float` and `double` (`DerObjectStreamCodec.java:882-886, 899-902`, V). STD-006 L2014-2018 says the same and has the decoder reject -0.0 bits (V).
- So DER equality is looser than Java `equals` for signed zero, as well as stricter for insertion-ordered sets. D named only the stricter case.
- C's -0.0 idiom, `f == 0.0 && 1.0/f < 0.0`, can never match a value stored through DER. D relayed C's table without noticing this.
- The spec conflict is real on the text: STD-011 4.3.4 L442-444 says -0.0 "MUST be emitted with their exact bit pattern" (V), and a strict STD-006 reader rejects it. That is for the spec owner, outside D.

**Corrected D text (§6 item 2):** "DER byte equality is neither finer nor coarser than Jini equality. It is stricter for `LinkedHashSet`/`LinkedHashMap` order and looser for signed zero: -0.0 and +0.0 encode alike. A template cannot select -0.0 in a DER-stored value."

###### F9. ACCEPT

- `And` adds 1 to its children's costs (`CostModel.java:165-167`, V).
- F's arithmetic is right: two 8-octet needles cost 2 × 524,291 + 1 = 1,048,583, which is rejected.

**Corrected D text (§0 item 4, and the glob and `in` limits):** "At the proposed ceilings, the needle octets of all `contains` calls in one predicate sum to at most 15, and to fewer when other terms are present."

###### F12. ACCEPT

- The disagreement is real. D §6 item 7 says omit the field; E rejects the registration.
- Both satisfy the operator's ruling that the new API does not expose `MarshalledObject` (RL-JGDMS-0132, R).
- D chose "omit" to avoid 8.1 item 2 failing the whole entry. It did not weigh that omitting drops data silently.
- Since `Class` also has no DER form (F3), the same choice applies to `ServiceTypes.serviceType`.

**Corrected D text:** "A field with no DER form is either rejected at registration (loud; such services cannot register with Derek) or omitted (they register; the field is silently unavailable). Operator decision. D no longer recommends one."

If asked, I lean to rejection in the native API, with the legacy decorator deciding whether to strip, because it fails loudly.

###### F15. ACCEPT

- **(b1) allocation.** The probe copies the subject to an `int[]`, so it does not show an allocation-free matcher. Corrected text: "No allocation is required, since a matcher can walk the stored string in place. Untested."
- **(a) length check.** A UTF-8 octet length of a Java `String` needs a scan. Corrected text: "Cost `1 + 3S` (two length scans and one copy), or `1 + S` if string values carry their octet length." The recommendation (reject) is unchanged.

###### F10 and F11. ACCEPT (D §8 relayed the marker form)

- The field-name slot is capped at 255 octets and the class-name slot at 1024 (`CelCeilings.java:58, 61`, V). A type name over 255 octets cannot be tested in the form D §8 quoted.
- The collision with a registrant's class named `#…` rests on STD-006 L2296 (R; I did not open it).

**Corrected D text (§8):** "Type and class tests put the name in the class-name slot, `has(field("<name>", "#isa"))`, and Derek rejects any registered class name that starts with `#`."

###### F1 and F2, as they bear on D

- **F1.** D did not consider exclusion. Under it, a (c2) mismatch caused by an encoding gap yields `false`, not an error, so the item passes. That makes the F3 definition a precondition for any use of (c2) in an excluded predicate. D's oversize rule yields `TYPE_MISMATCH`, which is safe under F1's fix.
- **F2.** D's cost figures assume the same S at every evaluation site. D said so for (c2) and for `in`, but not in general. Added note: "Every bound in D holds only where the candidate side enforces the S the verifier used."

###### "What both D and E assume"

1. **Canonical bytes are one thing.** Accepted as a fault in D's wording. One such form does exist (record-level capture); see F3.
2. **DER equality refines Jini equality.** Accepted; false for -0.0 (F4).
3. **`#` names cannot collide.** Accepted; D relayed it from C (F10).
4. **A cost bound holds wherever the bytes run.** Accepted with the note under F2.
5. **Bounded language makes the path safe.** This is E's; D made no path claim.

###### Not settled by this reply

- The collection, array and `Class` rules in the F3 definition.
- STD-006 L2296 and open item 10, which I did not open.
- Nothing was built or run; all of the above rests on reading source at the pin.

Helpers this round: none.

#### G.2 Section E author (a5afe1509e6a0978c)

claude-fable-5-2 (self-reported). Author's reply to review F, for section E and addendum E.1a.

- **Read-only.** Nothing edited, built, committed or written.
- **Helpers:** none dispatched. The checks were eight short line reads, so there are no agent ids to list.
- **Commit.** The working tree is now at `df1e7e4ece25`, not `83c4d4c7af`. `git diff --name-only 83c4d4c7af HEAD` over the files I cite below returned nothing, so my line reads hold at 83c4d4c7af (VERIFIED). The files are `jgdms-cel`, `DerObjectStreamCodec.java`, `RegistrarImpl.java`, `BasicInvocationHandler.java`, `MarshalledInstance.java`, STD-006 and STD-011. `PayloadFormatGuard.java` I read with `git show 83c4d4c7af:`.
- **Labels:** V = I opened the line this session. R = relayed from F and not re-opened.

**Overall:** I dispute no finding. Eleven are ACCEPT and five are ACCEPT WITH AMENDMENT. Three change a recommendation of mine: F2 (I withdraw the smaller-S option for now), F3 (I withdraw digest matching until the encoding is defined) and F12 (I now lean towards omitting the field in the decorator).

###### Replies

**F1. ACCEPT.** F's fix is the one I would adopt.
- My rule keyed on "an evaluation yields an error". Three paths produce no evaluation: an undecodable entry that is skipped, a budget that runs out, and an entry not visited. Spec 9.4 requires that a filter can only fail to select (STD-011 L1350-1356, V in my first pass).
- Corrected E.1a text: "An item passes an excluded predicate only if that predicate was evaluated on every visible entry of the item and yielded exactly `false` each time. Any other outcome makes the item no-match: `true`, an error, an undecodable entry, an entry not evaluated, or a spent budget."
- Corrected E.1 rule 2: "An undecodable entry cannot satisfy a required predicate, and it fails every excluded predicate."

**F2. ACCEPT WITH AMENDMENT.**
- `CostModel.java:93` is `private static final long S = CelCeilings.MAX_SCALAR_BYTES;` (V). `CelCeilings.java:52` ties it to `ObjectCodec.MAX_COLLECTION` (V). This settles my NC: the cost model cannot take a profile S today.
- F's arithmetic for the 900-octet needle follows from the same row D gives (R).
- Amendment: I would not adopt "the profile travels with the predicate" now. It changes the STD-011 record and jgdms-cel. I withdraw E.1a option 2 as a near-term option.
- Corrected E.1a recommendation: "Derek uses option 1 (a lower `maxExprCost` per predicate and per query, at the standard S) with the E.6 budgets. The same bytes then verify under any conformant verifier. A smaller lookup S is deferred until STD-011 makes S a profile value carried in the expression record."
- I also accept the second half. E.3 item 4 stated the scalar ceiling for row projections only. Corrected: "Every client projection that evaluates a predicate enforces the S the predicate was verified under, including the projection over the service's attribute records. A larger value makes the record undecodable."

**F3. ACCEPT WITH AMENDMENT.**
- E.2a said option (iv) "depends on D's open point 12.2" and recommended it anyway. F shows the condition fails for a slice of a shared-stream record. The recommendation was premature.
- The three entry points and the Appendix C citations are R. I did not re-open `DerObjectStreamCodec.java:1218-1246`.
- I adopt F's definition: the hashed bytes are the record-level canonical form of the value under the field's declared wire type, and Derek produces them by strict decode then re-encode, never by slicing.
- Amendment, on what E offers until that form is specified with conformance vectors:
  - "A non-scalar field has no companion. It is `has()`-only and retrievable by selection."
  - "The decorator throws on a template with a non-null non-scalar field. It never emits a predicate that cannot match." This also closes the exclusion path F names.
  - "A `java.lang.Class` field is projected as its name string. Arrays other than `byte[]` are rejected at registration."

**F4. ACCEPT.** F's reading is right, and my E.11 row was wrong for values stored through DER.
- `DerObjectStreamCodec.java:902` maps the -0.0 bit pattern to +0.0, and :885 does the same for float (V).
- Corrected E.11 row: "`Float` or `Double` field: NaN becomes `f != f`. A template of +0.0 or -0.0 becomes `f == 0.0`. Derek cannot tell the two zeros apart, so this match is looser than Jini `equals`. The decorator documents it."
- The STD-011 4.3.4 versus STD-006 conflict on emitting -0.0 belongs to the spec owner. It is one more reason to defer transform option 2C.

**F5. ACCEPT.** F's three-step fix is the one I would adopt, as mandatory.
- `BasicInvocationHandler.authenticateServer` returns `Collections.emptySet()` for any endpoint class other than `SslEndpoint`, `HttpsEndpoint` and `KerberosEndpoint` (V, :1867-1891). E.3 item 1 named a binding without naming this mechanism.
- Corrected E.3 item 1:
  1. "Call `authenticateServer`. An empty set means no client-side expression."
  2. "Set `ServerMinPrincipal` to the returned set on the prepared proxy before calling the accessor."
  3. "Send every argument call through that constrained proxy."
- The same binding applies to the authenticated re-check of a lookup predicate (E.1a) and to the attribute-records accessor.
- Reuse of `ServerIdentityPin` is R.

**F6. ACCEPT WITH AMENDMENT.**
- Reggie's default listener preparer is `new BasicProxyPreparer()` (V, `RegistrarImpl.java:5227`). The Subject facts at :575-578 and :6462-6466 are R.
- E.6 item 8 treated the listener stub as hostile data but said nothing about the Subject of Derek's outbound call. That is the gap.
- I accept as the default: Derek's listener preparer imposes `ClientAuthentication.NO`, the notify call runs under no Subject, and endpoint classes and target addresses are restricted.
- Amendment: with no client authentication a listener cannot tell Derek's events from anyone else's. That is tolerable, because an event is only a prompt to look again and the client then authenticates the bootstrap proxy. A deployment that wants authenticated events needs a second option: a dedicated event-sender identity with no other authority, never Derek's service identity. That is an operator decision.
- Corrected E.0a row P2, output column: "C's listener stub, called under no Subject with `ClientAuthentication.NO` by default."

**F7. ACCEPT.** F's two fixes are the ones I would adopt.
- I analysed the caller reading registrants' data. I missed the registrant reading the caller's predicate. No code is needed to see it: the registrant controls the candidate values and sees who then calls its service.
- Ordering and prefix tests make a literal recoverable by bisection. That is a cost of the operator's requirement for logical comparison at the server, and it should be stated.
- Added to E.6: "A lookup or event predicate is not confidential from registrants or from Derek. A client that must hide a literal sends a coarser predicate and applies the exact one locally."
- Added to E.6 "what else is needed": "Event predicates are evaluated off the registrant's call path, under a global per-change ceiling as well as the per-principal caps."

**F8. ACCEPT.** F's fix is the one I would adopt.
- `registerDo` replaces an existing registration with the same ServiceID and makes no owner check in that block (V, `RegistrarImpl.java:5710-5712`). E.13 listed the registrant identity model as NC. It did not draw the consequence for the indexed ServiceID query.
- Added to E.6 and E.9: "The first registration of a ServiceID binds it to the registering principals. Replacement, attribute changes and lease operations require the same principals. An unauthenticated registrant gets a Derek-assigned ServiceID."
- New operator decision: what "the same principal" means (exact set, or a configured mapping).

**F9. ACCEPT.** The limit is on the sum, not per call (arithmetic R, consistent with D's row). Corrected E.1a table cell: "Σ needle octets ≤ 15 per predicate at the proposed ceilings, shared with every other cost in that predicate."

**F10. ACCEPT.** My sentence was false at the wire.
- STD-006 L2296 is `className UTF8String (SIZE(1..1024))` with no syntax rule (V).
- Corrected E.1: "Derek rejects at registration any class name or field wire name that begins with `#`, and any class name that is not a JVM binary name. A client projection treats such a record as undecodable." Field names are included because of F11.

**F11. ACCEPT WITH AMENDMENT.**
- The field-name slot is capped at 255 octets and the class-name slot at 1024 (V, `CelCeilings.java:58, 61`; `CelDecoder.java:294-295`).
- I adopt F's form: `has(field("<class>","#isa"))`, and likewise `has(field("<type>","#type"))` for service types.
- Amendment: the marker now sits in the real class's namespace, so:
  - field wire names beginning with `#` must be rejected (as under F10);
  - a class in the chain with no fields must still expose its namespace with the marker.
- E.11's translation rows change to this form.

**F12. ACCEPT.** The disagreement is real and neither text noted it.
- Derek's API cannot carry a `MarshalledObject` or a JOSS-born value (RL-JGDMS-0132). So the only question is what the decorator does with such a field.
- Options for the operator:
  - (a) Fail the registration loudly. The service stays on Reggie only. This was E's text.
  - (b) The decorator omits the field, registers the rest and logs it. This is D's position, applied in the decorator.
  - (c) The decorator omits the whole entry.
- Revised recommendation: (b), made configurable. Under (a) any service that carries a `UIDescriptor` could not register through the decorator at all. Under (b) a template that names the omitted field throws (as in F3).
- `Class` fields are not in this group. They map to the name string.

**F13. ACCEPT.** F's fix is the one I would adopt. Corrected E.2 row: "`fieldValues` returns distinct record-level DER values, under the selection ceilings. No digest is returned." With F3 accepted there is no digest to return in any case.

**F14. ACCEPT.** F's fix is the one I would adopt. Corrected E.10: "`Selection.fields` has set semantics: sorted, duplicates rejected, with a count ceiling." My objection to 2C applied equally to my own sketch.

**F16. ACCEPT WITH AMENDMENT.**
- The item-view page gains a total. A bare count is wrong in two cases: when the call budget is spent, and when read control hides items.
- Corrected E.10: "`ItemViewPage` carries `totalMatches` and an `exact` flag. The count covers visible items only. When the budget is spent the count is a lower bound and `exact` is false."
- The decorator copies it into `ServiceMatches.totalMatches`. `lookUp` returns bootstrap proxies only and needs no total.

**F17. ACCEPT.**
- `PayloadFormatGuard.checkWrapper` and `checkFormat` compare the `getPayloadFormat()` string with the expected format and never inspect the bytes (V, `git show 83c4d4c7af:` lines 125-138). The tag is the sender's label. F's point holds for Reggie's guard too.
- Corrected E.2a: "Derek checks the tag and also strictly parses the bytes as canonical DER, within ceilings and without constructing any object. The client's DER pin remains the enforcing control." Whether a class-free validating walker for whole values exists is not checked.
- Option for the handback: carry it as plain bytes that Derek never interprets, with the decorator wrapping the caller's `MarshalledInstance`. That removes the format question for that field. Operator's call, given the ruling names `MarshalledInstance`.

**Minor notes. ACCEPT both.**
- Legacy `lookup` through the decorator: "The decorator re-evaluates the translated predicate over the service's attribute records before it returns an item." This is the E.1a re-check, made mandatory on that path.
- Shared caches: "If Derek has per-item read control, a decorator instance and any lookup cache are bound to one Subject. Background threads call under that Subject."

###### "What both D and E assume"

| # | Assumption | Verdict | Reply |
|---|---|---|---|
| 1 | "Canonical DER bytes of a value" is one thing | ACCEPT | See F3. E now offers no byte or digest matching until the form is defined. |
| 2 | DER equality refines Jini equality | ACCEPT | See F4. |
| 3 | `#` names cannot collide | ACCEPT | See F10 and F11. |
| 4 | The cost bound holds wherever the bytes are evaluated | ACCEPT | See F2. The bound holds only where the same S is enforced on candidates. |
| 5 | A bounded language makes the path safe | ACCEPT WITH AMENDMENT | E.0a did name an author, evaluator and Subject for each path. What it lacked was the enforcing mechanism on three of them: P4 (F5), P2 outbound (F6) and registration ownership (F8). Those are now stated. |

###### Changes to E.12 (operator decisions)

- **Decision 4** (non-scalar matching) becomes: specify the record-level canonical form with vectors, or leave non-scalar fields `has()`-only.
- **Decision 5** becomes the F12 choice: (a), (b) or (c).
- **Decision 19** loses the smaller-S option for now.
- **New decisions:**
  - event-sender identity (F6);
  - the meaning of "same principal" for ServiceID ownership (F8);
  - the handback as plain bytes or as a `MarshalledInstance` (F17);
  - whether to state that predicates are not confidential from registrants (F7).

###### Not re-opened by me

- The stream-codec lines and Appendix C text behind F3.
- The Subject capture lines behind F6.
- `ServerIdentityPin`.
- The arithmetic in F9.

These are relayed from F.

## 4. Recommendations


| # | Recommendation | Status | Operator must decide |
|---|---|---|---|
| R1 | No language addition for lookup | adopt | confirm |
| R2 | Concatenation | reject (for lookup) | confirm |
| R3 | Glob, literal pattern | defer (safe) | confirm |
| R4 | Regex subset | reject | confirm |
| R5 | ASCII case-insensitive equality | defer (safe) | confirm |
| R6 | Non-scalar field as canonical DER bytes (D c2), with F3's definition (record-level canonical form under the declared type, Derek re-encodes, never slices) and F4's -0.0 note | adopt after F3 is defined | whether to adopt; companion bytes or always SHA-256 (E.2a iv) |
| R7 | List equality (c3) | defer, superseded by R6 | none |
| R8 | `in` over a field bounded by list octets (c4) | defer; this is a cost-model change | none now |
| R9 | Bounded `exists` (c5) | reject | confirm |
| R10 | Query shape 1C (indexed ServiceID and type names, item predicate, entry predicates) plus M2 exclusion list | option | E.12 items 1 and 17 |
| R11 | Exclusion passes only on exactly `false` for every visible entry (F1 fix) | adopt | E.12 item 18 |
| R12 | Named-field selection (2B), no transform at Derek yet; set semantics and a count cap (F14) | option | E.12 item 2 |
| R13 | Introspection calls on Derek, or computed by the decorator | option | E.12 item 3 |
| R14 | A JOSS-born or non-DER field at registration: reject (E) or omit the field (D). F12 notes D and E disagree. | open | E.12 item 5 and F12 |
| R15 | Read-authorisation model for Derek; evaluate only over readable entries | precondition | E.12 item 6 |
| R16 | Per-call and per-principal budgets; a lookup cost profile, carried with the predicate and enforced by every projection (F2) | adopt in principle | E.12 items 7, 8, 19 |
| R17 | Client-side expression from the prepared proxy, bound by ServerMinPrincipal after authenticateServer (F5 fix); "projected means disclosed" | option | E.12 items 9, 10 |
| R18 | Two-sided matching | defer | E.12 item 11 |
| R19 | proxyKind field: it saves round trips and protects nothing | option | E.12 item 12 |
| R20 | Decorator: legacy lookup in a code-free host; MarshalledObject notify unsupported (RL-JGDMS-0132) | option | E.12 item 13 |
| R21 | Module for the decorator and client evaluator (jgdms-cel release 25) | open | E.12 item 14 |
| R22 | Reserve the `#` prefix and reject `#` or non-binary class names at registration (F10); put class names in the className slot (F11) | adopt | STD-011 edit, owner |
| R23 | Derek's listener calls carry ClientAuthentication.NO and no Subject (F6) | adopt | none |
| R24 | ServiceID bound to its first registering principal (F8) | adopt | none |
| R25 | State that lookup and event predicates are not confidential from registrants; evaluate them off the registrant's call path (F7) | adopt | none |
| R26 | T7 isolation for evaluation on Derek | open | E.12 item 16 |
| R27 | Client re-check of Derek's verdict over authenticated attributes, by default or not | open | E.12 item 20 |


## 5. Not determined


- The definition of a field's canonical DER bytes, and whether a client can reproduce them (F3, D 12.2). `java.lang.Class` has no DER wire type (F3).
- Whether `MarshalledInstance(val, ATOMIC_DER)` goes through the stream `writeObject` path. F did not verify it.
- The bodies of `Security.withContext` and `checkItemPayloadFormats`.
- Wall-clock time of a worst-case `contains`; runtime probes of CostModel and of -0.0 encoding. Nothing was built for F.
- Rust and Haskell behaviour: nothing was run.
- An identity model for registrants in Derek (E.13).
- The contents of the qa `(null, null, attrs)` templates (A1).
- The STD-011 / STD-006 conflict on -0.0 emission (F4; spec 4.3.4 L442-444 against STD-006 L2015-2017). This is an STD-011 owner item.


## 6. Deviations from this scope


1. **Results file.** Sub-agents cannot write report files in this harness, so each section was returned as text, saved under `J:\lookup-cel-2026-11-20\sections\`, and assembled here by the coordinator's Sonnet scribe. Sections A to F below are those reports, condensed where marked. The full texts stay in that folder.
2. **B.6 test run.** Run by the coordinator, not the B agent. Worktree `J:\std011-speed-2026-11-11\stage3g\wt` at 83c4d4c7a, JDK 27-internal (DirtyChai):
   - jgdms-cel: 1324 run, 0 failures, 0 errors, 0 skipped;
   - jgdms-cel-authoring: 74 run, 0 failures, 0 errors, 0 skipped.
   Log `J:\lookup-cel-2026-11-20\logs\b6_test.log`, md5 909853aaa0351f75517c0beb58a58cfc.
3. **Operator input during the work** (verbatim, in `COORDINATOR_NOTES.md`):
   - RL-JGDMS-0132: no MarshalledObject and no JOSS in Derek's API. Legacy Reggie is unchanged.
   - A decorator will serve ServiceRegistrar users.
   - "logical comparison of entry data for lookup filters at the server" (answered by E.1a).
4. **D and E authors' replies to F.** Collected 2026-11-28 on the operator's word; recorded in section 3.G. F's findings are unedited.
5. **Spec numbering.** STD-011 numbers refer to the v0.2 text at 83c4d4c7a, as the architect confirmed. C mapped the memo's moved v0.1 citations. The SOW's phrase `O(|E| x S)` is stale: spec 3.1 claim 3 withdrew it.
6. **Coordinator spot checks** (VERIFIED by the coordinator):
   - CelDecoder.java:156: tag range ends at 26 (no REF);
   - MAX_SCALAR_BYTES is used in jgdms-cel/src/main only by CostModel, CelCeilings and CelDecoder;
   - CostModel.java:94 and :255;
   - UIDescriptor and ServiceTypes fields;
   - EntryRep.java:110 and 389-398;
   - DerObjectStreamCodec.java:902 (-0.0 becomes +0.0).

7. Assembly: sections A-F transcribed by a Sonnet scribe (Sonnet scribe, agent id not known) from the saved reports; condensed: only the GlobProbe source listing in D section 13 (replaced by a pointer line). Heading levels inside each section report were demoted by two levels so they nest under section 3; nothing else changed.
