# Design memo — class-free lookup and the ServiceRegistrar hierarchy split

- **Status:** DRAFT rev 3 (second board round and the seats' discussion recorded in section 10; D11 added: sections 3.1 to 3.3 and decisions D1 to D3 are SUPERSEDED by section 3.8;
  D9 is now interim). The board review of rev 1 is recorded in section 9; claims the board showed to be wrong are
  struck in place in sections 2 and 3, and the design gaps it found are open. **The headline outcome (the host
  loads no mobile code) is NOT yet achieved by this design; see section 9.** Nothing here is implemented. **No production code was written or
  changed in producing this memo.**
- **AS-OF:** 2026-10-31, JGDMS trunk as found on disk that day (`C:\Users\peter\Documents\GitHub\JGDMS\JGDMS`).
  No commit hash was recorded; **re-read the disk before relying on any locator** (section 8).
- **Authors:** Peter Firmstone (decisions, section 1) and Claude, Sonnet 5.5 (drafting, investigation
  coordination). ⛔ **Same-model review of this memo is not wanted:** the producer is Sonnet, so the
  board seats that review it should be Opus and Fable.
- **Why it exists:** `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` section 1a records the idea that
  bounded, stateless, I/O-free client logic can be expressed as a dynamic proxy plus DER data plus
  CEL-shaped expressions, so that no mobile code runs in the client. This memo takes the lookup service
  (Reggie) as the first case and records the design decisions reached in one working session.
- **Companion (outside this repo):** `C:\Users\peter\Documents\GitHub\DirtyChai\SECURITY_MODEL.md`
  section 14.1 ("Code-free proxy" bullet, conditions), section 14.4 and section 14.5. That document
  carries the security conditions; this one carries the JGDMS API and wire design.

## 0. How to read this memo

Every factual claim in section 2 is labelled **VERIFIED** (the author opened the cited line or ran the
grep in this session) or **RELAYED** (read from a sub-agent's report and not re-opened). Decisions in
section 1 are Peter's, quoted from the conversation; they are not findings. Where the author has no
evidence, the text says **NOT CHECKED**. The fastest-ageing parts are section 2's test results and
status lines; see section 8.

## 1. Decisions taken (Peter, this session)

| # | Decision | Where recorded |
|---|---|---|
| D1 | **SUPERSEDED by D11.** Move the safe lookup-side methods into new super-interfaces, with `ServiceRegistrar` extending them and keeping the legacy methods at the bottom of the hierarchy. | This memo, section 3.1 |
| D2 | **SUPERSEDED by D11.** `SafeServiceRegistrar` becomes an empty **marker** interface that extends only the new base (no longer `ServiceRegistrar`). | 3.1 |
| D3 | **SUPERSEDED by D11.** Methods pushed up the hierarchy use **default methods** (so existing implementers keep compiling). Unsuitable legacy methods are to be deprecated; the exact list awaits the caller audit (O3). | 3.1, 3.3 |
| D4 | The JOSS path is being deprecated because it cannot be fully secured. Peter's stated reasons: JOSS is opaque and cannot read ahead to check invariants before constructing the root; `@AtomicSerial` forbids circular references including over Atomic JOSS; Atomic JOSS creates the parameter objects it passes to the root constructor and DER does not; the object table makes the bytes depend on stream history (a length side channel). | 3.6 |
| D5 | Reggie's attribute wire form gains schema chains. | 3.5 |
| D6 | Only the `ServiceRegistrar` interface is the API contract with the client. Because the proxy is dynamic, this remains so; Reggie's internal wire types (`Registrar`, `Item`, `Template`, `EntryRep`) are server internals. | 3.3 |
| D7 | A DER serializer for `Entry` is to be created **without making `Entry` `@AtomicSerial`**. Entry fields must be `@AtomicSerial` where they are not scalars, so they check their own invariants; DER reads ahead and checks each field against the schema type; a field class that does not exist locally is not created (that would break the Entry contract). Most fields are strings and numbers and are supported. | 3.6 |
| D8 | Immutable collections, **including maps**, are allowed as Entry fields. | 3.6 |
| D9 | **INTERIM after D11, for the legacy Reggie only.** (2026-10-31, after the board review) The lookup service's registrar proxy is treated as a **trusted pair** under `SECURITY_MODEL.md` 14.1 (an approved SPIFFE ID and code digest for the operator's configured lookup service) and is not converted to a dynamic proxy. The class-free view is applied where the host meets hostile data: the **service's attribute path**, not the registrar. | 3.3, 9.1 |
| D10 | (2026-10-31) The trusted host ships **standard UIs that work with standard service interfaces**. The UI collects input and passes it to the proxy; the proxy returns values; the service supplies no UI code. | 3.7 |
| D11 | (2026-10-31, after the board review) Instead of retrofitting `ServiceRegistrar`, design a **new `Remote` interface, `RemoteRegistrar`** (name proposed by the owner as "DynamicRegistrar or perhaps RemoteRegistrar"; this memo uses `RemoteRegistrar`, see 3.8), for a dynamic proxy and DER, plus a **local wrapper** that decorates it as the existing `ServiceRegistrar` for compatibility. `ServiceRegistrar` and `SafeServiceRegistrar` stay untouched. The service that offers it is **Derek** (D13). | 3.8 |
| D12 | (2026-10-31, added while the rev 2 review was running) **Discovery is to be updated to use DER.** The owner does not consider JOSS safe for discovery. This covers the unicast discovery response, which today reads the registrar by Atomic JOSS (`Plaintext.java:482-489`, per the Opus seat), and the multicast packets where they carry serialized data. The form of the DER discovery response is open (O15, O19). | 3.8, 5 |
| D13 | **CONFIRMED by the owner, 2026-10-31.** The owner asked "perhaps that should be a different Registrar?" about legacy-client support. Both second-round seats and the coexistence check (section 10, R15) support: a **separate DER-only registrar** (a separate process and registry) alongside the unchanged legacy Reggie, instead of one registry serving both formats. **The new service is named Derek, and Derek is the service implementation** (owner, 2026-10-31): its own implementation of `RemoteRegistrar`, not Reggie's `RegistrarImpl` run in another mode. It is built using the lessons of Reggie's `RegistrarImpl` as a starting point and will change from it, and it is **class-free from the start** (D5); its location is `services/derek/derek-service` (owner, 2026-10-31). What it reuses is open (O21). The name (owner, 2026-10-31; a name that sits with Outrigger, since a derrick has outriggers); the interface stays `RemoteRegistrar` and the proposed module `jgdms-lookup`. Reggie keeps its name until the legacy instance is retired. | 3.8, 10 |
| D14 | (2026-10-31) The **local wrapper** (the trusted resident class that presents a `RemoteRegistrar` stub as `ServiceRegistrar` and `SafeServiceRegistrar`) goes in **`jgdms-lib-dl`**, in the owner's words "probably". Author's dependency check (VERIFIED from the poms): `jgdms-lib-dl` depends on `jgdms-platform` and `jgdms-jeri` (`jgdms-lib-dl/pom.xml:49, 55`); `jgdms-jeri` depends on `jgdms-der` (`jgdms-jeri/pom.xml:52`), so DER is available to it transitively; `jgdms-der` depends only on `jgdms-platform` (`jgdms-der/pom.xml:45`), so there is no cycle. **Double-checked after the owner asked ("perhaps, maybe double check that"), VERIFIED by reading `jgdms-lib-dl/src/main/resources/META-INF/PREFERRED.LIST`:** the file sets `Preferred: true` as the **default** for the whole jar, with `Preferred: false` only for `org/apache/river/admin/*`, `org/apache/river/resource/ServiceConfigurationError.class`, `org/apache/river/start/ServiceProxyAccessor.class`, `net/jini/-`, `org/apache/river/api/-` and `org/apache/river/landlord/-`. ~~As I understand Jini preferred-class loading (not re-read from its specification), a preferred class is loaded from the codebase jar and not from the local classpath when this jar is delivered as a codebase, and a non-preferred one resolves locally. So the package the wrapper is placed in matters: ... a codebase carrying a jar with a same-named class could shadow it in that loader's namespace. ... the safe course is a `net.jini.*` package or an explicit `Preferred: false` entry.~~ **Corrected by the owner ("preferred classes aren't classes that the proxy shares with the client, so the proxy can have its own static fields etc."):** a `Preferred: true` class is one the codebase's own loader defines, so the proxy has its **own private copy** (its own statics, not shared with the client); a `Preferred: false` class is resolved through the parent, so it is **shared with the client**. The author's "shadowing" framing treated preference as a risk to a host-side class and was **wrong**: a private copy in the proxy's loader does not affect the host's own instance. What matters is the reverse: any type that must be **the same class on both sides of the boundary** (the `RemoteRegistrar` interface, the record types the host reads, and any other type the wrapper exchanges with a proxy) must be `Preferred: false`, or the proxy gets a different `Class` and a cast fails; in the code-free tier no codebase is involved, so these are resolved locally in any case. The wrapper itself is instantiated only by the host's own provider and is not loaded through a codebase loader, so its own preference setting is not the issue. (Not re-verified against the Jini preferred-class specification; the owner's statement is taken as authoritative.) **Still not checked:** whether `jgdms-lib-dl` should declare `jgdms-der` directly; how the preferred-class loader treats a class already loaded by the application loader; and whether any server's `-dl` jar actually bundles lib-dl classes. **Owner (2026-10-31):** the wrapper is not part of `net.jini.*` or `org.apache.river`, so it goes under the `au` namespace. The owner wrote `au.zeus.jgdms` and later said "or it could be au.zeus.jgdms, I'm not sure". ~~The JGDMS repository's package is `au.net.zeus.jgdms`, and no `au/zeus` tree exists in `JGDMS/JGDMS`.~~ **Corrected by the author the same day: that "no `au/zeus` tree" claim was wrong** (the author's file glob only matched a top-level `au/zeus` directory and missed module source roots). **Both namespaces exist in the repository (VERIFIED by `^package` counts over `*.java` in `C:\Users\peter\Documents\GitHub\JGDMS`):** `au.net.zeus.*` in 512 files (for example `jgdms-der`, and `jgdms-lib-dl` has `au/net/zeus/jgdms/{api/codebase,client,spiffe,proxy}`), and `au.zeus.jgdms.security.jwt` in 9 files, all in the `jgdms-security-jwt` module. DirtyChai's JDK uses `au.zeus.jdk.*`, and `docs/SOW-SubProcessDynamicPolicy.md` (as read earlier in this session, not re-opened) speaks of relocating into `au.zeus`. Which is the convention for new code is **the owner's call and not established by the code** (O22). Either way, `jgdms-lib-dl` has no `au/zeus` tree today, and both are covered only by the file's default `Preferred: true`, so, per the owner's correction above, an explicit `Preferred: false` entry is required for the **types shared across the proxy and client boundary** (`RemoteRegistrar`, its records and any other shared type that lives in lib-dl), and is **not** needed for the wrapper itself. | 3.8, 5 |

Not decided: generating Entry classes dynamically from the schema (O1).

## 2. Findings this design rests on

Locators are relative to `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS`. Line numbers drift; the anchor text
is given.

### 2.1 Reggie's proxy today

| Claim | Status | Locator and anchor |
|---|---|---|
| Reggie's client proxy is an explicit smart-proxy class implementing `ServiceRegistrar, SafeServiceRegistrar, ProxyAccessor, Administrable, ReferentUuid`. | VERIFIED | `services/reggie/reggie-dl/src/main/java/org/apache/river/reggie/proxy/RegistrarProxy.java:65` "public abstract class RegistrarProxy" |
| The proxy converts public types to internal wire types on each call: `new Item(srvItem, Util.requiresDerFormat(this))` and `new Template(tmpl, Util.requiresDerFormat(this))`. | VERIFIED | same file, lines 150, 161, 178, 185, 207, 220, 230, 250, 289 |
| The server's export is already a JERI dynamic stub, `AtomicILFactory`; the smart part is the wrapper. | VERIFIED (export) / RELAYED (the wrapper's role) | `services/reggie/reggie-service/.../RegistrarImpl.java:5222` "new AtomicILFactory(null, null, Registrar.class.getClassLoader())" |
| The internal `Registrar` interface takes `Item`, `Template`, `EntryRep[]`, not `ServiceItem`/`ServiceTemplate`. | RELAYED | `reggie-dl/.../proxy/Registrar.java:52` per the agent |
| Entry class loading happens at `Item.get()`, `getEntryClasses`, `getServiceTypes`, `getFieldValues`, via `CodebaseProvider.loadClass`; receiving an `Item` loads nothing. | RELAYED (call chain); the final hop VERIFIED | `EntryClass.java:277` "CodebaseProvider.loadClass(codebase, name, null, integrity, null)" |
| Leases (`AbstractLease.renew`, local `expiration`) and `RegistrarEvent` (lazy `Item` to `ServiceItem`) carry client-side state and cannot be plain dynamic proxies. | RELAYED | reggie-dl proxy package, jgdms-lib-dl lease package |
| `useDerForEntries` defaults to FALSE in code; `reggie-der.config` sets it TRUE. | VERIFIED | `RegistrarImpl.java:5263`; `reggie-service/src/main/resources/reggie-der.config:102` |

### 2.2 SafeServiceRegistrar and the discovery gate

| Claim | Status | Locator and anchor |
|---|---|---|
| `SafeServiceRegistrar` extends `ServiceRegistrar` and adds `lookUp` (returns bootstrap proxies) and `notiFy` (handback is a `MarshalledInstance`). | RELAYED (members) / VERIFIED (javadoc) | `jgdms-lib-dl/src/main/java/net/jini/lookup/SafeServiceRegistrar.java:30` "authentication prior to download and delayed un-marshaling" |
| The discovery manager skips a registrar that is not `instanceof SafeServiceRegistrar` unless `useInsecureLookup()`. | VERIFIED | `jgdms-lib/src/main/java/au/net/zeus/jgdms/discovery/ServiceDiscoveryManagerImpl.java:877-878` |
| Secure mode calls only `lookUp`/`notiFy`; the legacy `lookup`/`notify` run only in insecure mode, inside `catch (Exception)` blocks that discard the registrar. | RELAYED | `ServiceDiscoveryManagerImpl.java:1329, 1741, 2249`; `LookupCacheImpl.java:1276` |
| `lookUp` and `notiFy` have identical signatures in the interface and in `RegistrarProxy`. | RELAYED | `SafeServiceRegistrar.java:85` vs `RegistrarProxy.java:182-183, 213-218` |
| Callers that would fail on `UnsupportedOperationException`: `UnicastServiceFinder.java:54`, and `browser/` (`Browser.java:637, 770, 988, 1138`, `ServiceEditor.java:439`, `ServiceBrowser.java:125`). ~~No test calls the legacy methods; one test double throws the same exception.~~ **Corrected by the board review (Fable, finding 3):** the QA harness under `C:\Users\peter\Documents\GitHub\JGDMS\qa\src` (outside this memo's locator root) has at least 44 call sites in 35 files that invoke `lookup`, `getEntryClasses`, `getServiceTypes` or `getFieldValues` on registrars (a grep limited to registrar-named receivers, so an undercount). One test double throws the same exception. | RELAYED (list); lines 637, ServiceEditor 439, ServiceBrowser 125 VERIFIED by grep; QA count RELAYED from the Fable seat | as listed |
| ~~Only two implementers exist: `RegistrarProxy` and the test double `TestRegistrar` in `FiddlerImplDerFormatTest.java:206`.~~ **Corrected (Fable finding 3, then checked by the author):** a third exists in the QA harness: `LookupSimulatorProxyInterface extends ServiceRegistrar` (`C:\Users\peter\Documents\GitHub\JGDMS\qa\src\org\apache\river\test\services\lookupsimulator\LookupSimulatorProxyInterface.java:53`, VERIFIED). `TestRegistrar` implements `ServiceRegistrar` only, not the marker (Opus). | RELAYED, with the third implementer VERIFIED | search by the agent over `JGDMS/JGDMS` only |

### 2.3 Wire and DER

| Claim | Status | Locator and anchor |
|---|---|---|
| Interface stripping exists on the DER path: unresolvable proxy **interfaces** are dropped; the original bytes are kept only if the handler implements `RawWireFormRetaining`. | VERIFIED (method) / RELAYED (behaviour) | `jgdms-der/.../object/ProxyWireSupport.java:126` "public static Resolved resolveTolerant"; callers `DerObjectStreamCodec.java:1753`, `ObjectCodec.java:2417` |
| The JOSS path has no such fallback. | RELAYED | `MarshalInputStream.resolveProxyClass` |
| An unresolvable **class** makes the DER decoder fail; it does not produce a generic record. | RELAYED | `ObjectCodec.loadClass` :1848 |
| Class-free readers are narrow, scalar-only functions; non-scalar fields fail closed. | VERIFIED (name, location) / RELAYED (limits) | `ObjectCodec.java:1057` "decodeToScalarFieldMapClassFree" |
| Reggie's `EntryClass` carries a name, a 64-bit hash and a field count; no field names, types or schema digest. | VERIFIED (hash, numFields) / RELAYED (the absences) | `EntryClass.java:94, 106` |
| Reggie's "DER entry" is not Outrigger's `EntryRepV2` body; `useDer` only changes how non-immutable fields are wrapped. | RELAYED | grep of `services/reggie` found no `EntryRepV2` |
| The collection wire tokens `set:`, `orderedset:`, `list:`, `map:` exist; ~~`orderedmap:` is named in a doc comment~~ **corrected by both seats: `orderedmap:` and `bag:` are live constants** (`CollectionWireTypes.java:96, 100`). | VERIFIED (names) / NOT CHECKED (implementation, tests, use by Entry matching; the Opus seat reports the JOSS and DER Entry paths do not carry the declared field type to the collection writer, see section 9, B4) | `jgdms-der/.../getarg/CollectionWireTypes.java:95-99`; `schema/SchemaGenerator.java:724, 741` |
| `der-collection-ordering-research.md` (dated 2026-07-04) calls the tokens "build-later"; its status is **stale** against the code above. | VERIFIED | the memo's section 5 and 6.3 |
| `EntrySchemaGenerator` refuses `@SerialEntry` classes, citing "unit 2" for the `entryForm()` branch. | VERIFIED | `jgdms-der/.../entry/EntrySchemaGenerator.java:153-156` |
| STD-006 EntryRep-v2 appendix section A.0 describes a Reggie/sidecar "EntryRecord"; no Java type of that name was found. | RELAYED (not opened) | per the inventory agent |

### 2.4 CEL

| Claim | Status | Locator and anchor |
|---|---|---|
| The language has a closed table of 24 pure functions (ids 1-24) and no loops, macros, variables, I/O, clock or randomness. | VERIFIED | `jgdms-cel/.../wire/FunctionRegistry.java` (table of 24 entries); `docs/JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` lines 206-223 |
| Proposed ceilings: 1024 nodes, depth 32, 16 selector steps, static cost 1,000,000. **Not ratified** (the file calls them `[PROPOSED]`). | VERIFIED | `jgdms-cel/.../wire/CelCeilings.java:35-64` |
| STD-011 makes **no timing-side-channel claim**; whether evaluation needs process or core isolation is its open item T7. | VERIFIED | `JGDMS-STD-011...md:242-246` and the open-items list |
| `CandidateProjection` has four methods (`isUndecodable`, `namespaceChain`, `declaresField`, `fieldValue`); `EvaluationContext` has `Predicate` and `Transform`. | VERIFIED (context) / RELAYED (projection API) | `wire/EvaluationContext.java:26`; `eval/CandidateProjection.java` |
| STD-011's open list includes list indexing, map-field indexing and list equality as future revisions each needing a theorem extension. | VERIFIED | `JGDMS-STD-011...md:1592-1595` |
| The local evaluation of a server-supplied expression over call arguments is **not known to exist**. The current module evaluates over a read-only projection of a candidate entry. | NOT CHECKED beyond the agent's inventory | — |

### 2.5 Tests run (one run each; the author did not re-run them)

| Run | Result | Source |
|---|---|---|
| `jgdms-cel`, `mvn -pl jgdms-cel -am test -Dtest="au.net.zeus.jgdms.cel.**"`, 2026-10-30 | 692 run, 0 failures, 0 errors, 0 skipped | sub-agent a79f4765ae9b877b9, Surefire output, DirtyChai JDK 27-internal |
| `jgdms-der`, same reactor, with `-Dmaven.test.failure.ignore=true` | 1520 run, 0 failures, 0 errors, 0 skipped (105 classes) | sub-agent a0d12fe1a3a71cf66 |
| Outrigger `FilterEvalTest` + `FilteredWatcherRecoveryTest` | 31 run, 0 failures | same agent |
| `jgdms-platform` (upstream of `jgdms-der`) | **16 of 462 failed** (3 failures, 13 errors, 1 skipped): `DigestDomainGrantTest` (a `NoSuchMethod` on `PermissionGrantBuilder.codeSources(CodeSource[])`) and `GrantSignerTest`. Cause **not investigated**; the agent's guess (test expects an API the compiled class lacks) is untested. | same agent |

⚠ The `jgdms-platform` failures concern digest-based grants, which the DirtyChai trust model (section 14.1
of its `SECURITY_MODEL.md`) relies on. They are unexplained, not shown to be unrelated (O9).

## 3. Design

### 3.1 The interface hierarchy

> **SUPERSEDED by D11 (section 3.8).** Kept for the record: sections 3.1 and 3.2 describe the hierarchy split and
> the default-method remedy that the board showed unworkable (section 9, B1, B6, B7, B8).

```
new base interface B        (safe lookup-side methods, default methods)   <-- D1, D3
   ^                  ^
   |                  |
SafeServiceRegistrar   ServiceRegistrar      (D2: marker, extends B only)
 (empty marker)        (extends B; keeps legacy methods)
```

- **B** holds the safe methods: `lookUp`, `notiFy`, `getLocator`, `getGroups` and, per section 3.5, the
  class-free view methods. It uses the **public types** (`ServiceTemplate`, `MarshalledInstance`), with
  signatures **identical** to today's `lookUp` and `notiFy`, so that old compiled implementers, which
  already define them, still satisfy B.
- **B's methods are defaults that throw `UnsupportedOperationException`** (never null or empty: a silent
  empty result reads as "no services found"). Reason: third-party classes that implement `ServiceRegistrar`
  keep compiling.
- **`register` is not in B.** Reggie stores a registering service's proxy as opaque marshalled bytes. A bare
  `ServiceItem` on the wire would make Reggie unmarshal the proxy and load its code. Registering is the
  service side, which is trusted code and keeps its client-side conversion.
- **`SafeServiceRegistrar` is an empty marker extending only B.** The discovery gate at
  `ServiceDiscoveryManagerImpl.java:877-878` keeps testing `instanceof SafeServiceRegistrar`. Its meaning is
  unchanged: an old registrar that does not implement the marker still fails the test.
- **The hazard this avoids.** If the gate tested B instead, every old `ServiceRegistrar` would pass (the new
  `ServiceRegistrar` extends B) without implementing B's methods, giving `AbstractMethodError`, or an
  `UnsupportedOperationException` under defaults. **The gate MUST test the marker, never B.** Whether any
  other site depends on `SafeServiceRegistrar` being a `ServiceRegistrar` is the next item.
- **Source breaks (RELAYED, partly VERIFIED).** About nine sites in `browser/` use a `SafeServiceRegistrar`-typed
  variable as a registrar (`Browser.java:637, 770, 988, 1138, 1255, 1452, 1635, 1746`; `ServiceEditor.java:439`)
  and would stop compiling. The main library holds `ServiceRegistrar`-typed variables and casts to the marker,
  and is unaffected.
- **The marker is a selection hint, not an assurance.** It is tested on an unprepared (unauthenticated) proxy,
  so a malicious lookup service can claim it. It means only "the client may call the safe methods". The
  client's own constraints and preparer do the protecting (`SafeServiceRegistrar` javadoc: it is secure only
  if the client uses atomic input validation and strong integrity and confidentiality). Document this on
  the interface.

### 3.2 What the dynamic proxy does and does not do (handler caveat)

A `java.lang.reflect.Proxy` sends **every** interface method, default ones included, to its
`InvocationHandler`; the default body does not run by itself. JERI's handler would send B's default methods
over the wire as remote calls, which is what we want for the safe methods. For the **legacy** methods that
should be unsupported, the handler must reject them itself or call `InvocationHandler.invokeDefault`
(Java 16 and later). ~~**NOT CHECKED:** the compile release of `jgdms-platform`, which decides whether `invokeDefault` is
available (O2).~~ **Corrected by the board review (both seats):** the release is 21 (`jgdms-platform/pom.xml:124`,
`jgdms-jeri/pom.xml:116`, as reported), so `invokeDefault` exists. But this paragraph's remedy does not work as
written: the legacy methods stay **abstract** in the design, and `InvocationHandler.invokeDefault` throws
`IllegalArgumentException` for a non-default method (DirtyChai `InvocationHandler.java:232-238`, as reported);
`BasicInvocationHandler.invoke` (:730-765) sends every method except the `Object`, `RemoteMethodControl` and
`TrustEquivalence` ones remote, and no `isDefault`/`invokeDefault` handling exists in the repository. And the
premise fails earlier: see section 9, B1.

### 3.3 Reggie as a dynamic proxy: kept, moved, lost

> **Re-scoped by D9 (2026-10-31).** The registrar is **not** converted: it runs as a trusted pair (this is the legacy Reggie; the code-free lookup is the new service Derek, D11 and D13). This section's
> analysis applies only if that decision is later reversed, and the registrar-specific findings (B1, B5, B6, B8)
> are off the critical path. What stays on it: B2 (the service attribute path), B4 (canonical collections), B9
> (CEL authorship and binding), and the residual in section 9.1 (a trusted registrar's bytes still arrive by JOSS).

**Verdict from the agent's review (RELAYED, not independently reviewed): can convert with changes; the
change is large.**

- **Kept, moved into local classes or the server:** `register`, `lookUp`, `notiFy`, `getLocator`,
  `getGroups`, the admin calls; the `ServiceItem`/`Template` conversions move server-side or into local
  classes (D6 frees the internal types); leases need a locally known lease class around a dynamic stub;
  `RegistrarEvent` needs a locally known class; `ProxyVerifier`'s hard-coded class checks need updating.
- **Lost on the code-free path, by design:** live `Entry` objects of classes the client lacks (they become
  class-free views, 3.5); the class-returning introspection calls `getEntryClasses`, `getServiceTypes`,
  `getFieldValues` (replaced by name/schema methods); `lookup(ServiceTemplate)` and
  `lookup(ServiceTemplate,int)` which return unmarshalled objects; the JOSS `notify` with a
  `MarshalledObject` handback; JOSS-only clients.
- **Not lost:** matching by class name and field values stays on the server; a client still builds templates
  from entry classes it has.
- **Unmarshalling for `lookUp`:** the secure path already returns bootstrap proxies, so it carries no service
  or entry unmarshalling. **NOT CHECKED:** whether the later "local attribute filtering" step after
  authentication loads entry classes on the host (O4).
- **Deprecations (D3):** candidates are the five methods above plus the `MarshalledObject` `notify`. They
  are deprecated with a pointer to the safe alternative. Callers that would break are in section 2.2.

### 3.4 Interface stripping and the mixed-version case

Stripping drops **names, not methods**. With D1, a host that does not resolve `ServiceRegistrar` can still
resolve B and the marker, so the rebuilt proxy has only the safe methods. Compatibility:

| Client | Server | Result |
|---|---|---|
| new | old | ~~works; the old server implements every method~~ **corrected (Fable finding 5, PLAUSIBLE):** the legacy methods work, but through the old server's downloaded `RegistrarProxy` smart proxy (mobile code on the new client), and the new B methods fail remotely on an old server (no such method), not through the `UnsupportedOperationException` default. |
| new, DER-capable | new | works; the safe path |
| old, JOSS-only | new | **fails**: the old client cannot resolve B and cannot strip (JOSS has no fallback). Needs a legacy proxy served during a sunset, selected by the marshalling-format constraint. **NOT CHECKED:** how that negotiation works in the code (O5). |

~~STD-009 section 6.4 and `SOW-Smart-Proxy-Isolation-Wiring.md:22` still say stripping is "not built"; the
code contradicts them on the DER path.~~ **Corrected (Fable finding 7, line VERIFIED by the author):** STD-009
section 6.4 itself says "BUILT 2026-07-17 (trunk `a20455e3e`)" (`JGDMS-STD-009...md:465`). Only its header (line 4)
and line 35 are stale, and `SOW-Smart-Proxy-Isolation-Wiring.md:22` still says "not built". Also (Opus):
the registrar proxy itself reaches the client by Atomic JOSS during unicast discovery (section 9, B1), so
stripping on the DER path does not reach it.

### 3.5 Class-free views and schema chains (D5)

- **New safe methods (in B)** return a **class-free view** of a service item and its entries: service ID,
  type names with schema digests, entry class names with schema digests, field values as DER scalars and
  records, and the service proxy as opaque marshalled bytes; plus introspection by names and schemas, in
  place of the `Class`-returning calls. CEL filters and transforms may be evaluated against the view.
- **Reuse unchanged (RELAYED):** `CandidateProjection`, `SchemaView`, `DerSchemaChainView`, `Evaluator`,
  `CelVerifier`, `CelCeilings`, `SchemaChain` with its ceilings (64 records, 64 KiB), `MarshalledInstanceRecord.decode`,
  the scalar class-free readers; the Outrigger `EntryProjection` pattern (not the class).
- **Missing (RELAYED):** a class-free `ServiceItem` view type; field names and types in Reggie's wire form (the
  reason for D5); a per-entry schema digest in Reggie (`EntryClass.hash` is not the STD-011 section 8.5
  `entrySchemaDigest`); a public way to read a `MarshalledInstance` payload without `get()`; a decoder for
  non-scalar fields; an **aggregate** ceiling across a whole item (today 64 KiB per entry).
- **Source of the schema.** A `@SerialEntry` class (JGDMS-STD-005 v1.2, Active) declares its wire schema through
  `entryForm()` (developer-controlled names and types; SHA-256 hash of the wire names), with a `(GetEntryArg)`
  constructor and `serialize()`. It needs no reflection. A plain `Entry` takes the reflective schema route
  (`EntrySchemaGenerator`). The generator's `@SerialEntry` branch is unwritten ("unit 2"). **Recommendation:**
  require `@SerialEntry` for entries in the code-free tier; plain entries stay on the legacy path.
- **Where the Entry-to-record conversion lives (open, O6).** With no downloaded proxy class there is no place
  for it. (a) In the marshalling layer, so the codec writes any `Entry` as the record; or (b) in new B methods
  that take pre-built records, with a local helper in the client library doing the conversion. **Lean: (b).**
- **STD-011 open items affect the view:** the field name-resolution rule (section 8.2, "uniqueness-or-error") and
  the applicability key (section 8.5, class name against schema digest) are both marked open.

### 3.6 Entry fields, DER, and JOSS (D4, D7, D8)

**Materialise an entry as an `Entry` object only if every field is one of:**
1. a scalar: `String`, a boxed number or boolean, or `byte[]` (Jini entry fields are objects, not primitives; as
   recalled, not checked against the Entry specification);
2. a locally resolvable, approved `@AtomicSerial` class, which checks its own invariants in its constructor;
3. an **immutable collection** (`Set`, `List`, `Map`) all of whose elements, keys and values are allowed types,
   with bounded nesting.

DER reads the whole entry ahead and checks each field against its schema type before constructing anything.
Any mismatch rejects the entry as a whole (failure atomicity). **Everything else stays a class-free view**; the
entry is never partially built, and a template for a class the client lacks cannot be built.

- **A local class wins.** If the client has the entry class and its digest matches, use it, so a `@SerialEntry`
  class's own `check` method runs. A generated data-only class has no invariants and must never stand in for
  a local class that has them.
- **Collections follow `der-collection-ordering-research.md`** (read in full; owner decision 2026-07-04). The
  declared type decides: if it guarantees a deterministic iteration order, the order is **preserved**
  (`List`, arrays, `SortedSet`/`TreeSet`, `SortedMap`/`TreeMap`, `EnumSet`/`EnumMap`, `LinkedHashSet`,
  `LinkedHashMap`); otherwise the elements are **canonicalised** by octet-sorting their encodings (X.690
  section 11.6). A `Map` is ~~`SEQUENCE OF SEQUENCE { key, value }`~~ `SET OF SEQUENCE { key, value }` in the ordering memo's section 2 ("Option A resolved: outer `SET OF` tag `0x31`", lines 335-336), sorted by the encoded key. **The source is internally inconsistent:** its open question 3 (lines 837-840) recommends keeping tag `0x30`. Corrected after the Fable seat (finding 6); the tag is unresolved. No `hashCode`
  is ever used. A duplicate element in a canonicalised set is rejected. Nulls use the `CHOICE { absent NULL,
  present ... }` form.
- **Entry matching** is by byte equality of field values (STD-006 section 7.7.2). For content matching use the
  canonicalised types; `LinkedHashSet`/`LinkedHashMap` make byte equality stricter than `.equals` (the memo's
  section 3a). Fields meant to be matched should therefore be `Set`/`Map` (canonicalised), not the linked types.
- **JDK immutable collections.** `Set.of`/`Map.of` have unspecified iteration order (as recalled, deliberately
  randomised per JVM run; **not checked**), so by the rule they canonicalise. They reject `null` elements, so
  a decoder materialising into them must reject an entry whose collection carries a null (fail closed). An
  order-preserving field cannot use `Set.of`; it needs an unmodifiable wrapper over a linked or sorted
  collection (JDK 21 has unmodifiable sequenced wrappers; **not checked**). The schema token
  (`set:` or `orderedset:`, `map:` or `orderedmap:`) tells the decoder which case applies.
- **Ceilings:** `MAX_COLLECTION` 65536 and nesting 16 exist (RELAYED); an aggregate ceiling across a whole
  `ServiceItem` is missing.

**JOSS (D4).** A proxy, entry or result arriving by JOSS is treated as mobile code, never as code-free data.
The rationale is Peter's (D4). Two supporting facts: the DER stream-dedup SOW already states the rule that
output length is a function of message structure and never of secret values, and that values are never
compressed (`SOW-DER-Stream-Schema-Dedup.md` section 3.1; **read, not verified against the code**); that SOW's
baseline is "JOSS+DEFLATE", which suggests compression on the old path (**inference**). Whether the JERI JOSS
object table spans calls is **NOT CHECKED**: the invocation handler appears to create a stream per call
(`BasicInvocationHandler.java:1356`, from a grep, not a read).

### 3.7 Standard host UIs (D10)

Design intent, not built and not reviewed by the board.

- **The host owns the UI.** For a service interface the client already has locally (the closed list of section
  3.5), the host ships a UI component bound to that interface. The UI collects the user's input, calls the proxy,
  and shows the values it returns. The service supplies no UI code, so a ServiceUI factory or descriptor is never
  loaded; it arrives as class-free data and is ignored unless its interface name matches a local UI.
- **What the proxy sees:** only the arguments the UI passes. What the UI sees: only the values the proxy returns,
  which pass the same admission as any DER value (section 3.6). Nothing the proxy returns is a class or an
  executable.
- **Returned values are hostile data when displayed.** The UI must display strings as data: no markup or
  command interpretation, bounded length, control characters and bidirectional-text controls neutralised
  (the author's list, not reviewed). Input is validated by the trusted UI before it is submitted, which matches
  the trusted-host input mediation in `SECURITY_MODEL.md` 14.1.
- **Limits:** this covers only services with a **standard interface** the host has a UI for. A service with a
  custom interface and a custom UI either gets no UI, or runs its UI in an isolated environment (a board or a
  separate display); that case is outside this memo.
- **Smart proxies still need isolation.** A standard UI removes the UI code, not the proxy code. A smart proxy
  that backs a standard interface still runs in the isolated environment, and the UI talks to it through the
  stub.

### 3.8 RemoteRegistrar, Derek and the local wrapper (D11, D13)

Design intent, **not reviewed by the board in this form**. Method names below are illustrative, not decided.

- **The interface.** `RemoteRegistrar` is a new `Remote` interface, every method declaring `RemoteException`,
  designed for a JERI dynamic proxy and DER. The name is the author's choice between the owner's two
  suggestions: in STD-009 `DYNAMIC` versus `SMART` is a per-implementation choice, so naming the interface
  "Dynamic..." would tie it to one proxy kind; and Reggie's internal wire interface is already named `Registrar`.
- **Relation to today's code.** It is the public, stable successor to Reggie's internal `Registrar` (D6 frees
  those internals). Its parameters and results are class-free records with schema chains (D5), not `ServiceItem`
  graphs. A sketch of the method set: register (a record plus a lease duration), `lookUp` (a template record,
  returns bootstrap proxies), a class-free item view lookup, notification registration, introspection by names
  and schemas, `getServiceID`, `getLocator`, `getGroups`, the admin methods; the lease and event objects are
  `Remote` interfaces too.
- **The local wrapper (client side).** A trusted local class implementing `ServiceRegistrar` and
  `SafeServiceRegistrar`, backed by a `RemoteRegistrar` stub. It carries the logic the board found in
  `RegistrarProxy` (section 9, B5): the `lookUp` result filter, identity by `ServiceID` (cached once from the
  stub), local lease state, event conversion, and the Entry-to-record conversion of 3.5, option (b). It is
  resident library code, never downloaded. It lives in a new module that depends on platform, DER and CEL
  (avoids the cycle of B8).
- **The server side.** ~~The existing Reggie implementation additionally exports `RemoteRegistrar`, serving the same
  registry, in a separate discovery group, which `reggie-der.config:9-36` already describes as the migration
  mechanism (RELAYED from the Opus seat, not opened by the author).~~ **Corrected in round 2 (section 10, R3, R10, R15):** `reggie-der.config` cannot export as written and describes a separate instance, not a shared registry; `RemoteRegistrar` must be a **separate facade export** implementing only that interface; and, per D13 (confirmed), served by **Derek**, a separate DER-only registrar with its own registry, running beside the unchanged legacy Reggie. **Derek is the service implementation** (owner, 2026-10-31): its own implementation of `RemoteRegistrar`, not Reggie's `RegistrarImpl` run in another mode. Whether it reuses Reggie code, and which, is open (O21).
- **What it resolves from the board:** B1 (legal for JERI by construction; `ServiceRegistrar` untouched, so the
  `getServiceID()` problem does not arise), B5 (the logic has a home), B6 (no binary break), B7 (the
  default-method question disappears), B8 (no module cycle). D1 to D3 are not needed.
- **What it does not resolve:** discovery still hands out `ServiceRegistrar`-typed objects read from Atomic JOSS, so
  it needs a DER response form (D12 decides the direction; the form is open, O15) and an adaptation point that wraps a `RemoteRegistrar` stub in the wrapper;
  B2 (the service attribute path), B4 (canonical collections) and B9 (CEL binding) are unchanged; JOSS-only
  clients keep the legacy service.
- **The DER pin (owner, 2026-10-31: "not if a DER constraint is used").** The host requires
  `MarshallingFormat.ATOMIC_DER` as a **required constraint** on the registrar stub, on every bootstrap stub it
  receives and on any listener it exports. With the requirement applied, a stub whose handler would use JOSS
  fails closed instead of downgrading the host (round 2, Opus finding 1: `BasicInvocationHandler.requireMarshallingFormat`
  fails closed "only when" the requirement is applied). The constraint is the enforcing construct; without it a
  hostile registrar can choose the handler class. Not settled by the pin: whether the stock trust verifier accepts
  the DER stub at all (Opus finding 2, UNVERIFIED-NEEDS-BUILD), and whether every transport distils the constraint
  (`FINDING-JERI-MarshallingFormat-Plaintext-Transport-Gap.md` records JOSS-prevention tests per transport; not
  re-run by the author).
- **Trust.** A code-free `RemoteRegistrar` carries no mobile code, so a lookup service that offers only it could be
  untrusted without isolation, which is stronger than D9. Conditions, not reviewed: the host reads only DER under
  ceilings, bootstrap proxies are authenticated before use, and the attribute path is class-free (B2). D9 stays the
  position for the legacy Reggie.

## 4. Non-goals

- Replacing smart proxies that hold state, share logic across methods or call their backend
  (`SOW-Smart-Proxy-Isolation-Architecture-Overview.md` section 1a: "Named trap: bolting an I/O-capable custom
  function onto CEL"). Those keep the isolation machinery.
- Making `Entry` `@AtomicSerial`.
- Changing the JOSS path other than to deprecate it.
- Any change to the `register` side of Reggie beyond the type split.

## 5. Open questions

| # | Question |
|---|---|
| O1 | Generate Entry classes from the schema, or class-free view only? If generated: data-only fields (scalar, approved `@AtomicSerial`, immutable collections), a per-principal loader, a distinct-class ceiling, a namespace that can never shadow a real class, and a rule that a matching local class always wins. NOT DECIDED. |
| O2 | ANSWERED by the board: release 21, so `invokeDefault` exists, but the section 3.2 remedy is unworkable and the premise fails earlier (section 9, B1, B7). |
| O3 | The caller audit for the deprecated methods is done (section 2.2); the **final deprecation list** is not. |
| O4 | ANSWERED YES by both seats: after authentication the client calls the service's own `getServiceAttributes()` and may download code (`ServiceDiscoveryManagerImpl.java:2034`, comment VERIFIED by the author). Section 9, B2. |
| O5 | The mixed-version path and the sunset date. Opus: `reggie-der.config:9-36` already describes the migration mechanism, a separate Reggie instance in a "der" discovery group; discovery is not a JERI call, so there is no marshalling-format negotiation to hook. ~~how the marshalling-format constraint selects it~~ (RELAYED, not opened by the author). **Superseded by round 2 (section 10, R3, R11, R12, D13):** `reggie-der.config` cannot export as written; discovery negotiates by format ID (R11); legacy clients are served by Reggie and new ones by Derek, a separate registry. |
| O6 | Where the Entry-to-record conversion lives (3.5): marshalling layer or new methods with a local helper. |
| O7 | STD-011 field name-resolution rule (8.2) and applicability key (8.5), both open. |
| O8 | An aggregate decode ceiling across a whole `ServiceItem`. |
| O9 | The 16 `jgdms-platform` test failures (digest grants): stale test, mismatch with the JDK in use, or a defect. |
| O10 | Which entry types do Reggie's users depend on that carry behaviour (UI descriptors and similar)? They survive only as data. |
| O11 | Is the `EntryRecord` of STD-006 section A.0 specified as a usable wire form for Reggie attributes? (The author did not open it.) |
| O12 | Whether `useDerForEntries` should default on once the JOSS sunset begins. |
| O13 | Standard host UIs (D10): which standard service interfaces exist, which get a UI first, and where the closed list of local interfaces is kept and by whom it is approved. |
| O14 | Display of returned strings (3.7): the exact neutralisation rules, and who reviews them. |
| O15 | D9 residual: a trusted registrar's bytes still arrive by JOSS (unicast discovery) and are parsed in the host. How is that read bounded (the "hostile data to an approved digest" case in `SECURITY_MODEL.md` 14.1), and when does discovery get a DER form? **Direction decided by D12: discovery moves to DER.** Still open: the form of the DER unicast response (it must carry a registrar proxy, for the legacy Reggie a smart proxy whose classes still load from a codebase, so DER read-ahead bounds the parse but does not remove the trusted-pair question), whether the multicast request and announcement packets are in scope (STD-006 section 7.7.7 describes signed forms as spec-only; not opened by the author), and the sunset for JOSS-only discovery clients. |
| O16 | `RemoteRegistrar` (3.8): the method set, the record types, and which parts of the legacy `ServiceRegistrar` contract (leases, events, `ServiceID` identity, group and locator calls) it must carry. |
| O17 | ANSWERED: the interface is `RemoteRegistrar`; the **service** is named **Derek** (owner, 2026-10-31; see D13). Checked by the author: a search of the JGDMS repository for "Derek", "Dexter" and "Regis" in java, md, xml, config and properties files found no matches. |
| O18 | The lease and event objects as `Remote` interfaces, and how the wrapper presents them as the existing lease and event classes. |
| O19 | PARTLY ANSWERED by D14: the wrapper lives in `jgdms-lib-dl`. Still open: where the `RemoteRegistrar` interface and the records live (the author's lean: also `jgdms-lib-dl`, so Derek and the wrapper share them) and where the DER discovery provider goes (lean: `jgdms-discovery-providers`, which the Fable seat says depends on platform, lib-dl and jeri, `pom:41-51`, RELAYED). Original question: where the wrapper module lives, what it depends on, and how discovery adapts a `RemoteRegistrar` stub into it without changing the `ServiceRegistrar`-typed discovery APIs. |
| O21 | Derek as its own service implementation (D13), at `services/derek/derek-service` (owner, 2026-10-31), built using the lessons of Reggie's `RegistrarImpl` as a starting point. Still open: what it may reuse from Reggie (the lease and registration bookkeeping, the store, the matching), and what it must not (the JOSS paths, class loading of entry classes). Derek's registry would be class-free from the start (D5, section 3.5), so the reuse boundary is mainly the lease, group and persistence logic. NOT DECIDED. |
| O22 | The package namespace for new JGDMS code, including the wrapper, `RemoteRegistrar`, its records and Derek: `au.zeus.jgdms` (used by `jgdms-security-jwt`, DirtyChai's `au.zeus.jdk` style, and the SubProcessDynamicPolicy SOW) or `au.net.zeus.jgdms` (512 files, `jgdms-der`, `jgdms-lib-dl`). Both exist today. The owner is not sure, and noted that "DirtyChai uses `au.zeus.jdk`". The author's lean, not a decision: `au.zeus.jgdms` for **new** code, for consistency with DirtyChai and with `jgdms-security-jwt`, leaving the 512 existing `au.net.zeus` files alone (a rename is large churn for no security gain); the cost is two namespaces in one code base. Whichever is chosen, the **shared** types (see D14) need `Preferred: false` entries in `jgdms-lib-dl`'s `PREFERRED.LIST`; the wrapper itself does not. |
| O20 | Whether Derek (a lookup service offering only `RemoteRegistrar`) may be untrusted (no isolation), and the exact conditions. |

## 6. Hazards, in the order the author would fix them

1. **The gate must test the marker, not B** (3.1). A change that makes the gate test the base interface breaks
   a security filter silently.
2. **A default that returns instead of throwing.** Any default on B returning null or an empty result turns an
   unsupported call into a plausible answer.
3. **Handler dispatch of defaults** (3.2): a default body is not run by a dynamic proxy; relying on it is a bug.
4. **A class generated or approved from the schema shadows a real class** (O1).
5. **Stale status lines** (3.4) will lead a reader to conclude stripping does not exist.

## 7. What the board is asked to review

Questions are phrased to attack the design from the reviewer's seat, using `JGDMS-Board-Reviewer-Guidance.md`:

- **Confused deputy and role reversal (section 2.2):** the host now reads attacker-influenced entry data from an
  untrusted lookup service and evaluates server-supplied expressions. Re-derive the caller, execution subject
  and principal for each path. Is any new direction of request ungated?
- **Ungated reconstruction door (section 2.2):** does the materialisation path (3.6) reach object
  reconstruction without the gate the primary path has? Where is the approved-class check enforced, and is it
  enforced before any constructor runs?
- **Canonical form (G1, section 2.1):** do the collection tokens, the schema digest for entries and Entry byte
  matching agree on one encoding per value? Does `Set.of`/`Map.of` (unspecified order) break "same value, same
  bytes" anywhere?
- **Fail-closed side effects (G6):** trace the cleanup path when read-ahead rejects an entry, a collection
  contains a null, or a type is unresolvable.
- **Outcome versus mechanism (G2):** "the host loads no mobile code" is stated. Which enforced construct
  guarantees it, and is it in this design?
- **Pre-deletion audit (section 2.3):** before the legacy methods are deprecated, what do they do beyond their
  headline job?
- **All sources of truth agree (G8):** this memo, STD-005, STD-006, STD-009, STD-011 and the built code disagree
  in at least two places (section 3.4, `der-collection-ordering-research.md` status). Find the rest.

## 8. Provenance and ageing

- **Fastest-ageing sections:** 2.5 (tests), 2.1 to 2.3 (locators). A staleness incident is recorded for this
  author's own earlier work in the same session: a status line stated a count that was true when written and
  false within the hour. **Re-read the disk before acting on section 2.**
- **Relayed material** comes from five read-only sub-agent investigations (Reggie proxy, `SafeServiceRegistrar`,
  stripping and caller audit, class-free readers, tests). All used Sonnet 5.5 (self-reported, not
  independently confirmed). Their reports are advice, not verified facts; the VERIFIED labels in section 2 mark
  what the author re-opened.
- **Author's corrections this session**, struck in place where they appeared elsewhere: (1) the clock-granularity
  section was cited as 14.2 and is 11.7 (fixed in the DirtyChai documents); (2) "interfaces must be local" was
  presented as a new condition and already exists as interface stripping (section 3.4); (3) an object-table
  attack on partially constructed objects was described for plain Java serialization and does not apply
  where `@AtomicSerial` forbids cycles (Peter's correction). **This is a list of known corrections, not a
  count checked against the session log.**
- **Triggers the author would apply next time:**
  - BEFORE asserting an interface relation, open the declaration and the call sites; a sub-agent's list is a
    lead, not a locator.
  - BEFORE calling a design document "current", grep the code for the thing it calls unbuilt.
  - BEFORE quoting a test result, say who ran it and that the author did not.

## 9. Board review, 2026-10-31 (two blind seats)

- **Seats:** Opus, agent `a0535cb19bf21fbf8`, self-reported `claude-opus-5-5`, reported JGDMS HEAD `5be0eb619` with
  this memo untracked (the author did not check the hash); Fable, agent `a499f54f694168e24`, self-reported
  `claude-fable-5-1`. Neither read the other's report. Both were read-only and ran no build, so the section 2.5
  test results and the O9 failures were **not** re-checked by either.
- **How verified:** each row says what the author re-opened. "RELAYED" means the author did not.
- **Overall:** the memo's headline outcome (the host loads no mobile code) is **not achieved** by this design. B1
  and B2 are each enough to change the design before any code is written.

| # | Finding | Seats | Author's check |
|---|---|---|---|
| B1 | The Reggie registrar cannot be delivered or typed as a JERI dynamic proxy over the public types. `ServiceRegistrar` does not extend `Remote`; `getServiceID()` declares no `RemoteException`; JERI's `Util.checkMethod` throws "illegal remote method" for such a method; a JERI proxy exposes only the implementation's direct `Remote` interfaces, and DER writes `proxy.getClass().getInterfaces()`, so B and the marker survive stripping only if they extend `Remote` and are implemented directly. Discovery reads the registrar by **Atomic JOSS** (`Plaintext.java:482-489`, `readObject(ServiceRegistrar.class)`) and every discovery API is `ServiceRegistrar`-typed, so a stripped `{B, marker}` proxy is unusable there, and under D4 the registrar proxy is itself mobile code until discovery has a DER form. B would live in `jgdms-platform`, the module that holds `ServiceRegistrar`. | Opus F1; Fable 2, 4 | VERIFIED: `ServiceRegistrar.java:44, 251`; the "illegal remote method" message at `Util.java:362`. RELAYED: the rest |
| B2 | The secure lookup path still loads entry classes: attributes come from the **service's** bootstrap proxy through `getServiceAttributes()` (returns `Entry[]`), after authentication and before any filter; on a filter error the client downloads the service proxy. A class-free view on Reggie does not touch this path. | Fable 1; Opus F3 | VERIFIED: the comment "getServiceAttributes may try to download code" (`ServiceDiscoveryManagerImpl.java:2034`). RELAYED: the accessor signature and the call sites |
| B3 | Role reversal: moving the `ServiceTemplate` conversion server-side makes Reggie see `Class[]` and `Entry[]` templates and resolve client-named classes (JOSS) or fail on unknown types (DER). The memo reasoned this only for `register`. | Opus F2 | RELAYED |
| B4 | D8 is canonical on no existing path: the JOSS form of `Set.of`/`Map.of` follows a salted per-JVM order (`ImmutableCollections`, salt from `System.nanoTime()`), Reggie compares raw bytes, so equal values registered and queried from different JVMs fail to match; the DER Entry path wraps each field as an untyped `MarshalledInstance` and nothing carries the declared field type to the collection writer. `Set.of(...).contains(null)` throws. | Opus F4 | RELAYED (Fable independently confirmed the `Set.of` salt in DirtyChai's `ImmutableCollections.java:93-97`) |
| B5 | `RegistrarProxy` holds client-side logic the memo's "kept or moved" list omits: the `lookUp` result filter (`RegistrarProxy.java:186-194`: the result must be `RemoteMethodControl`, `TrustEquivalence`, both accessor interfaces and a `Proxy` class), identity (`getServiceID` local, equality by `ServiceID`; a dynamic proxy compares handlers, which changes discovery's de-duplication), and `EntryRep.get`'s failure semantics (a failing field becomes null, a wildcard, and `handleException` swallows `StackOverflowError` and `OutOfMemoryError`), which break D7's failure-atomicity claim if reused. | Opus F5 | RELAYED |
| B6 | D2 is binary-incompatible: a precompiled third-party call through `SafeServiceRegistrar` to `lookup`, `register` or `getServiceID` fails at link time, and a class declaring only `implements SafeServiceRegistrar` stops being a `ServiceRegistrar`, so discovery rejects it. The memo covers source breaks only. | Opus F6 | RELAYED |
| B7 | Section 3.2 is unworkable as written (struck in place there). | both | RELAYED |
| B8 | Module cycle: `jgdms-der` and `jgdms-cel` depend on `jgdms-platform`, so B, which must live in platform, cannot name `SchemaChain`, `CandidateProjection` or the other types section 3.5 plans to reuse. | Opus F8 | RELAYED (poms) |
| B9 | CEL: STD-011 lines 233-241 say bounded cost does not hold until a per-scalar ceiling (`MAX_SCALAR_BYTES`) is pinned and enforced at decode on both sides; section 2.4 omitted this. The memo never says who authors the expression, who evaluates it, under which Subject, or what binds it to the authenticated service. Reggie evaluating client expressions over other registrants' entries is a cross-tenant timing channel (STD-011 T7); the host evaluating a registrar's expressions is the case in `SECURITY_MODEL.md` 14.1. `LookupCacheImpl`'s event task connects to a bootstrap proxy the lookup service chose, gated only by the bootstrap preparer. | Opus F9; Fable 8 | RELAYED |
| B10 | Errors in the memo's own claims, struck in place in sections 2 and 3: the QA harness (outside the locator root) and a third implementer; STD-009 section 6.4 says "BUILT"; the Map tag; `orderedmap:` and `bag:` are live constants. | both | the third implementer, STD-009 line 465 and the constants VERIFIED by the author |

**Settled by an existing artefact:** O2 (release 21), O4 (yes), O5 (a separate "der" Reggie instance, no negotiation).
**Not determined by either seat:** whether DER read-ahead constructs nested field objects or initialises classes before
it rejects a later field (this decides D7's atomicity claim); what happens if B extends `Remote`; STD-006 sections 7.7.2
and A.0; whether any third-party `SafeServiceRegistrar` implementers exist (only the owner knows).

### 9.1 What this does to the decisions (author's reading, for the owner)

- **D1/D2/D3** need rework: B and the marker must be `Remote` and implemented directly (or the registrar stays out of
  the dynamic-proxy scheme); B's module placement and the discovery typing are open; the default-method remedy is
  unworkable; D2's binary incompatibility must be accepted or avoided.
- **D4** has a consequence the memo had not drawn: the unicast discovery response is JOSS, so the registrar proxy is
  mobile code until discovery has a DER form (B1).
- **D5/D7/D8** have no existing path that carries the declared field type or the collection discipline (B4).
- **The outcome claim** needs a mechanism on the attribute path (B2): a class-free `getServiceAttributes`, and the
  download fallback disabled in the code-free tier.
- **ADOPTED by the owner as D9 (2026-10-31); originally the author's suggestion, not a board finding:** treat the registrar as a trusted pair under
  `SECURITY_MODEL.md` 14.1 (an approved SPIFFE ID and digest for the operator's configured lookup service, run as
  it runs today) and apply the class-free view where the host meets hostile data, which is the service's attribute
  path, not the registrar. That would remove B1, B5, B6 and B8 from the critical path.
- **What stays on the critical path after D9:** B2 (the service attribute path, where the host meets hostile
  data, needs a class-free `getServiceAttributes` and the download fallback disabled); B4 (a canonical collection
  path for entries); B9 (CEL authorship, evaluator and binding); and a **residual**: a trusted registrar's bytes
  still arrive by Atomic JOSS and are parsed in the host (O15). A compromised approved lookup service can send
  hostile bytes to that parse; that is the "hostile data to an approved digest" case in `SECURITY_MODEL.md` 14.1,
  and it is not closed by D9.

## 10. Second board round and the seats' discussion, 2026-10-31

- **Seats (fresh, blind in the first step):** Opus, agent `abccc0b8aef4e1413`, self-reported `claude-opus-5-5`;
  Fable, agent `a68fe6a3703fa1ed5`, self-reported `claude-fable-5-1`. They reviewed rev 2 (with D12 added mid-review),
  then each read the other's report and replied in one discussion round. A Sonnet 5.5 agent
  (`af59a446f98a91465`) checked DER and JOSS coexistence in parallel. All were read-only; **nothing was built or
  run**, so every runtime behaviour below is unverified. The author re-opened only the lines marked VERIFIED.
- **Verdict, both seats:** **D11 holds with changes.** The `RemoteRegistrar` interface plus a resident wrapper is the
  right shape and is legal for JERI and DER; the memo as written did not name the constructs that make "the host loads
  no mobile code" true.

### 10.1 Findings the seats agree on

| # | Finding | Check |
|---|---|---|
| R1 | A hostile registrar can choose the invocation handler class (`ObjectCodec.decodeProxy` decodes it as `InvocationHandler`), which would put the host on JOSS. Stopped only by a **required `MarshallingFormat.ATOMIC_DER` client constraint** (`BasicInvocationHandler.requireMarshallingFormat`, which throws only when applied), plus an exact-class check of the handler. | Both seats read the code; the author did not |
| R2 | `BasicJeriTrustVerifier.isTrustedInvocationHandler` accepts only `BasicInvocationHandler` and `AtomicInvocationHandler`, and rejects an interface that is not `Remote` (the DER stub's `DerInputLimitControl`). A verifying preparer would reject the DER stub and accept a JOSS one. The wrapper needs its own `TrustVerifier`, or the stock verifier must learn the DER handler. | VERIFIED by the author at `BasicJeriTrustVerifier.java:319`; both seats confirmed the rest. Runtime behaviour not run |
| R3 | `reggie-der.config` cannot export as written: it builds an `AtomicILFactory` (VERIFIED, line 74) with a required `ATOMIC_DER`, and the JOSS dispatcher makes `verifyAndStripMarshallingFormat` throw. It also describes a separate instance, not a shared registry. No test launches it. | Static trace by both seats; the operator can settle it by starting Reggie with that config |
| R4 | `DiscoveryV2.handleUnicastDiscovery` writes the last proposed format ID, not the selected one. A client proposing JOSS and DER to a JOSS-only server gets a DER header and a JOSS body. Not a silent downgrade (the DER decode fails), but the mixed rollout does not work until it is fixed. **Fix first.** | Both seats; not run; the author did not open it |
| R5 | `Plaintext.readV2UnicastResponse` allocates `new String[din.readInt()]` before any bound; `0x7FFFFFFF` gives `OutOfMemoryError`, which the `RuntimeException` catch does not catch. | VERIFIED by the author at `Plaintext.java:476` |
| R6 | Events and registrations carry the smart proxy (`ConstrainableRegistrarEvent((RemoteMethodControl) proxy, ...)`). A code-free client must export its listener with `AtomicDerILFactory` and require `ATOMIC_DER`, or events arrive by JOSS. The event record must be closed (no `Object` fields), and `getServiceItem()` needs a defined partial-attribute contract for classes the client lacks. | Both seats |
| R7 | **Identity:** no remote call on an unprepared stub to learn the `ServiceID`. It travels in the discovery record. After `prepareProxy`, bind it: Fable has the wrapper call `getServiceID` once on the authenticated stub and reject a mismatch; Opus has the facade's `ProxyVerifier` confirm it (the same pattern as Reggie's `registrarID.equals(...)`). These are compatible; Opus would drop `getServiceID` from the interface, Fable would keep it for this check (open, minor). | Both seats |
| R8 | **Leases are DER records** presented through a resident `AbstractLease` subclass with a local `LeaseMap`, not one `Remote` object per lease (per-lease DGC, no batching). The lease methods already exist on Reggie's `Registrar`, keyed by id. | Both seats |
| R9 | The wrapper needs `RemoteMethodControl`, `TrustEquivalence`, its own `TrustVerifier`, and `setConstraints` with a method-mapping table (as `ConstrainableRegistrarProxy.methodMappings` does). Reggie's `ProxyVerifier` compares against the `Registrar` stub, so a separate facade export needs **its own** verifier. | Both seats; the facade point PLAUSIBLE |
| R10 | `RemoteRegistrar` must be a **separate facade export** (on Derek) implementing only that interface, with `AtomicDerILFactory`. Adding it to the existing JOSS export puts `Registrar` on the wire, and JOSS interface resolution is all-or-nothing. | Both seats |
| R11 | **Discovery:** a DER unicast provider pair under a new format name and ID, using the existing negotiation (a provider SPI keyed by an 8-byte format ID from `computeFormatID`, with `checkUnicastDiscoveryConstraints` on the provider interfaces). Response shape: `{host, port, groups (bounded), registrarID, [8] stub}`, **no legacy-carrier arm** (both seats dropped it: a second door to JOSS). A legacy proxy travels only on legacy format IDs. | Both seats; the format-ID mechanism read by the author (`DiscoveryV2.java:645`) |
| R12 | **Refusing JOSS in a code-free host, layered:** (1) omit the JOSS providers, so negotiation fails closed with "no supported formats" (construction, not vigilance); (2) a `MarshallingFormat.ATOMIC_DER` discovery constraint at `checkUnicastDiscoveryConstraints`, for a JVM shared with legacy clients; (3) `ATOMIC_DER` required on the stub and the listener, and the handler class checked exactly; (4) `DownloadPermission` denied. **(4) alone is not sufficient** (Opus): its check runs only under `RequireDlPermProvider` and it does not stop JOSS reconstructing smart-proxy classes already on the host classpath. Whether DirtyChai's `LoadClassPermission` covers it was not checked. | Both seats |
| R13 | **Modules (O19):** (partly superseded by D14: the wrapper goes in `jgdms-lib-dl`, not a new module; where the interface and records live is open) the seats' layout was one `jgdms-lookup` module above `jgdms-der`, holding the interface, records, wrapper and provider, **without** a CEL dependency; split only when client-side CEL evaluation lands. (`jgdms-discovery-providers` depends on platform, lib-dl and jeri, not on DER, so the provider goes with the wrapper.) | Both seats |
| R14 | **B2:** a `Remote` `ServiceAttributeRecordsAccessor` on bootstrap proxies, implemented once in `AbstractJiniService`; in code-free mode `ServiceDiscoveryManagerImpl.check` calls only it and drops the download retry; the `ServiceID` is cross-checked. | Both seats |

### 10.2 The owner's question: a different registrar (D13, confirmed by the owner)

- **R15.** Entry matching across formats is **not possible** in one registry: `EntryRep.matchEntry` compares wrapped field bytes
  raw (`MarshalledInstance.equals`, VERIFIED at `MarshalledInstance.java:957`); only strings, boxed numbers and similar
  skip the wrapper, so scalar fields match across formats and every non-scalar field does not, **silently** (Reggie's own
  comments say so: `Util.java:53` and `reggie-der.config:34`, both VERIFIED). A neutral store would have the server
  unmarshal JOSS registrations and load classes (the B3 role reversal); a format tag with per-format matching needs a dual
  entry representation. Least invasive: **two registrars**, Reggie unchanged and Derek (the owner's separate service implementation; the seats assumed shared `RegistrarImpl` code, see O21), the DER one with DER-only exports and
  DER-only discovery providers; `JoinManager` registers with every discovered lookup service, so a service appears in both
  without extra configuration. A legacy service still joins only legacy groups unless it also registers with the DER one
  (the wrapper module is needed for that). **The deciding measurement, not made:** how many of the Entry classes actually
  deployed have non-scalar fields; if none, one registry is viable.
- **R16 (INFERRED, coexistence check):** stored service proxies carry a `payloadFormat` tag, but a DER-only client has no
  enforcement against reading a JOSS-tagged one (`Item.get` calls plain `service.get()`). That is an ungated door the code-free tier
  must close.
- **R17 (INFERRED):** Reggie's discovery server builds one `UnicastResponse` with one proxy for every connection
  (`RegistrarImpl.java:5024, 5058`, VERIFIED to exist); there is no hook that selects the proxy by negotiated format.
- **A format-ID model:** call-path format is fixed per export (`AtomicILFactory` versus `AtomicDerILFactory`), so one remote object
  cannot serve both; the required `ATOMIC_DER` is checked, not negotiated, at export and per call.

### 10.3 Disagreements that remain, and a retraction

- `getServiceID` in the interface: Opus drops it, Fable keeps it for the post-prepare check (R7). Minor.
- Opus weakened Fable's timing finding: `MarshalledInstance.equals` returns early when the hashes differ, so it is not a prefix
  oracle on marshalled fields; what remains is the field-by-field early exit in `EntryRep.matchEntry` and `String.equals` on
  scalars. **Still a hypothesis; needs a measurement.**
- Fable's verdict that the shape "is legal for JERI and DER" was incomplete (R1, R2): both seats now agree.
- Both seats changed their recommendations on the legacy carrier arm and on module layout; nothing else was retracted.

### 10.4 Recommended first increment and tests (both seats, merged)

- **Step 0, before any new design:** fix R4 and R5, make R2 work (verifier), and fix or delete `reggie-der.config`.
- **Smallest end-to-end increment:** a separate facade export with `getLocator`, `getMemberGroups`, `lookUp` (and `getServiceID` per R7); a wrapper that
  throws loudly elsewhere; a DER discovery provider pair; one QA run of discovery, the wrapper and the SDM secure lookup through the gate at
  `ServiceDiscoveryManagerImpl.java:877-878` with a `verify=true` preparer under a `DownloadPermission`-denied policy. Then leases, register,
  notification, the B2 accessor, item views and canonical collections (B4).
- **Tests, each shown to fail when its guard is removed:** handler-downgrade rejection; DER stub trust verification (positive and negative); format
  negotiation (client proposes A and B, server supports A, must announce A); group count `0x7FFFFFFF` rejected before allocation; a code-free
  leak test (zero classes defined by codebase loaders); export legality (a method without `RemoteException` is rejected); strip one unknown
  interface name and keep `RemoteRegistrar`, and all unknown gives `ClassNotFoundException`; `ServiceID` equality across two discoveries;
  `setConstraints` reaches the stub; a JOSS response rejected under the denied policy while DER is accepted; **length invariance** of the DER
  discovery response (equal length for registrars differing only in value) and a **timing measurement** of `matchEntry`.
- **Existing tests to extend:** `AtomicDerInvocationLayerTest`, `MarshallingFormatDeferralTest`, `AtomicDerInputLimitControlTest`, the
  `DerOnlyConfigRejectTest` pattern, `FiddlerImplDerFormatTest`, and the QA lookup simulator (the 44 legacy call sites must still pass on the
  legacy proxy). These names are from the seats' reports; the author did not open them.

### 10.5 Recommended answers to the open questions (the seats'; the owner decides)

O13 first standard interfaces: `Administrable`, `JoinAdmin`, `DiscoveryAdmin` and the browser; the closed list in client configuration approved by the
operator. O14 display: strip or replace Unicode Cc and Cf characters including U+202A-202E and U+2066-2069, cap the length, render plain text.
O15: R11. O16: the method set (carry `register`, `lookUp`, class-free item views, `notiFy` with an opaque handback, `getLocator`, member groups, the lease
methods, attribute add, modify and set over records, the admin interfaces; drop both `lookup` methods and the JOSS `notify`; replace `getEntryClasses`,
`getServiceTypes` and `getFieldValues` with schema, type-name and named-field calls; use field names, not indices). O17: `RemoteRegistrar`. O18: R8.
O19: R13. O20: yes, only with pinned SPIFFE constraints, `ATOMIC_DER` on stub and listener, `DownloadPermission` denied, the handler checked by exact class,
an aggregate decode ceiling, and B2 closed; until then no.

### 10.6 Not determined by anyone

Every runtime behaviour (nothing was built or run); whether `reggie-der.config` boots, and whether the QA configs that use `AtomicDerILFactory` pass
`verify=true` (the operator can settle both); `JoinManager`'s own marshalling and what legacy-version services do; the `DownloadPermission` check
site beyond `PreferredClassLoader`, and `LoadClassPermission`; STD-006 section 7.7.7; DER read-ahead atomicity (D7); third-party
`SafeServiceRegistrar` implementers; the size of the timing finding; the count of non-scalar Entry fields in deployed classes.

## 11. Fail-open paths in the code-free tier (2026-10-31, added at the owner's request)

**Status: author's analysis, not board-reviewed. Nothing here was run.** "Fail-open" means an error, a missing setting or an unknown input leaves the host
running code, or trusting decoded data, instead of rejecting. Items 1, 2, 4, 10, 11 and 13 rest on board findings (R1, R2, B2/R14, R6, R7, R5) that the
author relayed; the lines for R2 (`BasicJeriTrustVerifier.java:319`) and R5 (`Plaintext.java:476`) were opened by the author. Items 3, 5, 6, 7, 9 and 12 are the
author's inferences from the design. Item 8 is PLAUSIBLE and unchecked.

| # | Path | What opens | Closed form |
|---|---|---|---|
| 1 | Hostile registrar chooses a JOSS invocation handler (R1) | Stopped only if the required `ATOMIC_DER` constraint is applied; the check throws only when applied, so a missing constraint means the host decodes JOSS | Apply the constraint inside the wrapper and exact-class check the handler; do not leave it to configuration |
| 2 | Trust verifier rejects the DER stub and accepts a JOSS one (R2) | Operators may turn `verify` off to make DER work. Whether the QA configs pass `verify=true` is unsettled | The wrapper has its own `TrustVerifier`, so correct use never needs verification off |
| 3 | Fallback to Reggie and JOSS when Derek fails | A downgrade: breaking the DER path yields the weaker one | A code-free host has no fallback; the JOSS discovery providers are not on its classpath |
| 4 | A failed attribute check retries by downloading (B2/R14) | `ServiceDiscoveryManagerImpl.check` retry loads the codebase | The code-free mode drops the retry, tested where classes load, not only at start-up |
| 5 | A smart proxy appears in a lookup result | A host that instantiates what it does not recognise runs untrusted code | Unrecognised items stay bytes, handed only to an isolated node or rejected |
| 6 | No isolated node available | The easy fallback is to run the proxy in the host JVM | No node means the call fails; the scheduler has no local branch |
| 7 | Unknown digest, or SPIFFE ID with no grant | Trust is a (SPIFFE ID, digest) pair; a missing pair must not default to a generic grant | Default deny (ties to the 16 `jgdms-platform` failures, uninvestigated) |
| 8 | Interface stripping (`resolveTolerant`) drops an interface that carried a safeguard | If `RemoteMethodControl` or `TrustEquivalence` can be dropped, constraints may silently not apply (PLAUSIBLE, unchecked) | Strip only from an allowlist of functional interfaces; fail if a security-relevant one is missing |
| 9 | A CEL error or limit breach treated as "no filter" | A filter that throws, or breaches the node or cost ceiling, could deliver everything; `FilteredWatcherRecoveryTest` suggests recovery exists, not read | Any error or breach means no match and no delivery |
| 10 | Event listener exported with the JOSS factory (R6) | Events arrive by JOSS | Export with `AtomicDerILFactory`; require `ATOMIC_DER` on listeners |
| 11 | ServiceID not bound after `prepareProxy` (R7) | Impersonation: the discovery record names one service and the stub is another | Bind once on the authenticated stub; reject a mismatch |
| 12 | A standard UI role has no match, so the downloaded UI factory runs | UI code arrives by the back door | No match means no UI |
| 13 | Unbounded `String[din.readInt()]` (R5) | `OutOfMemoryError` escapes the `RuntimeException` catch; a crash, not an opening, but an uncaught error on a discovery path | Bound the count before allocating |

**One rule covers most of them:** there is no path from data to code. A missing, unknown or erroring input rejects, and the decision is taken at the point of
decoding or loading, not at configuration. Items 1, 3, 4, 5, 6 and 12 are fallbacks; a code-free host should have none.

**Not done:** a board review of this section (the dispatch rule is that a Sonnet-written list is reviewed by Opus and Fable seats); a check of items 3 to 9 against the code.

### 11.1 Board review of section 11 (Opus 5.5 and Fable 5.1 seats, blind, 2026-10-31) and the first fix

**Status: reviewers' findings, static reading only, nothing run except the unit test named below.** The author opened the cited lines for A1, A3 (ServiceID keying), the
`sm == null` return, the `Resolve` admission and `Plaintext.java:476`; the rest is relayed.

**Rows of section 11 corrected by the seats.** ~~Row 1 "the required `ATOMIC_DER` constraint stops a hostile handler"~~: `requireMarshallingFormat` is a method on
`BasicInvocationHandler` and trusts the handler's own overridable `marshallingFormat()`, so a handler that is not that class never runs it; build the handler locally from decoded
endpoint data instead. ~~Row 3 "JOSS providers are not on the classpath"~~: the 24 JOSS providers are registered in two modules (`jgdms-discovery-providers`, `jgdms-resources`),
`Plaintext.readV2UnicastResponse` is in `jgdms-platform`, and providers load through the context loader, so no one jar controls it. Row 8: stripping cannot drop
`RemoteMethodControl` (it always resolves); the sender omitting it is the risk, and the wrapper must require it. Row 9: Outrigger's server-side filters fail closed; the host-side
evaluator is not on disk, so the row stays undetermined. Row 6: no node scheduler is on disk. Row 12: no role matching exists today (`UIDescriptor.getUIFactory` unmarshals by JOSS).
Row 13: discovery catches `Throwable`; the real risk is a large allocation starving other threads.

**Paths the seats added.**
1. **The sender chooses the decoder (Opus, the most serious).** `ObjectCodec.admissibleConstructClass` (`ObjectCodec.java:604-618`) admits any `Resolve` class in any slot; `readResolve` runs
   inside `decodeNested` before the handler `instanceof` check (`:2355-2358`, `:2434-2439`); `DerProxySerializer.readResolve` carries a `MarshalledInstance` whose `payloadFormat` comes from
   the wire and defaults to JOSS. The `ATOMIC_DER` pin on the stub does not reach this. Not traced end to end.
2. **A null filter downloads the proxy on the main path** (`ServiceDiscoveryManagerImpl.java`, `check`, `filter == null`), and the retry exists a second time in `LookupCacheImpl`.
3. **Discovery keys on the proxy's self-asserted ServiceID** (`AbstractLookupDiscovery.java:2520-2523`); the announcement's ID is never compared.
4. **Defaults authenticate nothing:** preparers default to `new BasicProxyPreparer()` and `discoveryConstraints` to empty; a hostile responder can pick plaintext JOSS.
5. **The event listener acts before it checks:** it prepares the bootstrap proxy and calls `getServiceAttributes` before matching the event ID and source; the default exporter is JOSS.
6. **Isolation is off by default and off on error**; the trust grant is permissive at start-up with no `VerdictRegistry`.
7. **Swallowed `Error`s turn a hostile entry into a wildcard** (`RegistrarProxy.handleException`).
8. **Verifiers are OR'd** (`Security.java:1765-1771`), so adding a DER verifier leaves JOSS trusted.
9. HYPOTHESIS: a null codebase may fall back to `java.rmi.server.codebase`. Untraced.

**The closing rule, revised.** "No path from data to code" is not enough. Also: check before act; identity from the authenticated stub, never from the wire; no decoder chosen by the sender;
no reliance on a union of verifiers; defaults that authenticate nothing are a failure; and "not at configuration" is untrue where the SecurityManager, preparers and exporter are configuration.

**Fix 1 (owner's direction: apply the constraint during unmarshalling).** `MarshalledInstance.get(defaultLoader, verify, verifierLoader, context, type)` now calls
`requireFormatFromContext` before choosing a codec or reading a codebase annotation: if the context holds `InvocationConstraints` requiring a `MarshallingFormat`, the wire's `payloadFormat`
must equal it, else `UnsupportedConstraintException`. Preferences are not enforced. Three tests were added to `MarshalledInstanceConstraintTest`; the class ran 10 tests, 0 failures, and with the
call commented out `getRejectsJossPayloadWhenDerRequired` fails (VERIFIED by the author, one run each, `jgdms-platform` only).
**Not done:** (a) nothing yet puts the required format into the context on the DER decode path. In `BasicInvocationHandler` the context holds `clientConstraints` (the field is a `MethodConstraints`,
not an `InvocationConstraints`, so it would not match this check), and whether `DerGetArg.getObjectStreamContext()` returns the same collection to `DerProxySerializer` is untraced. A dedicated context
element, set by the wrapper and defaulting to ATOMIC_DER for a code-free host, is the likely shape. (b) `AtomicMarshalledInstance` has JOSS as its `payloadFormat` and would be rejected under a DER requirement, which is the intent.
(c) No end-to-end test through a DER call. (d) `Resolve` admission in a handler slot is unchanged.

**Questions only the operator can settle.** Does the code-free host run with a SecurityManager? Do its preparers pass `verify=true` with constraints, and what does it set for `discoveryConstraints`,
`eventListenerExporter`, the class provider and `smartProxyIsolation.enabled`? Does the deployed policy grant `LoadClassPermission` or `BootstrapPermission` broadly, and is a `VerdictRegistry` configured? Is `java.rmi.server.codebase` set, and is `jgdms-resources` on its classpath?

### 11.2 Trace of the required format through the DER call path, and Fix 2 (2026-10-31)

**Trace (author read the code; VERIFIED by reading the lines named).** Fix 1 alone never fired on a real DER call, for two reasons. (1) `DerGetArg.getObjectStreamContext()` returns only the decode-unit
completion token or an empty list (`DerGetArg.java:555`), so `DerProxySerializer` handed `smartProxy.get(...)` a context with no constraints. (2) `AtomicDerInvocationHandler.createMarshalInputStream`
ignored the `context` it was given and built `new ResolutionContext(proxyLoader, integrity, proxyLoader)`. Separately, `BasicInvocationHandler` adds `clientConstraints` to that context, but the field is a
`MethodConstraints` (`BasicInvocationHandler.java:223`), not the per-call `InvocationConstraints`, so a check keyed on `InvocationConstraints` would not have matched it. The one place the call's required
`MarshallingFormat` is known is the unfulfilled-constraints loop in `invokeRemoteMethodOnce` (`:979`), which calls `requireMarshallingFormat` and then discards the value.

**Fix 2 (four hops).** (a) `invokeRemoteMethodOnce` keeps the required `MarshallingFormat` and adds it to the context as an `InvocationConstraints`. (b) `AtomicDerInvocationHandler` reads it from the context
(`requiredPayloadFormat`; two different required formats throw `IllegalStateException`) and builds the `ResolutionContext` with it. (c) `ResolutionContext` gains a fourth record component,
`requiredPayloadFormat`, with the old three-argument constructor kept (null means none) and `withRequiredPayloadFormat`. (d) `DerGetArg.streamRequiredPayloadFormat()` (package-private, not broadcast, like the
loaders) is read by `DerProxySerializer`, which adds an `InvocationConstraints` for it to the context given to the nested `MarshalledInstance.get`, where Fix 1 enforces it.

**Evidence (one run each, author-run; modules `jgdms-platform`, `jgdms-der`, `jgdms-jeri` compile).** New `DerProxyRequiredFormatTest`, 3 tests, 0 failures: a carrier holding a JOSS smart proxy is rejected when
`ATOMIC_DER` is required; the same carrier decodes when nothing is required; a carrier holding a DER smart proxy is accepted when `ATOMIC_DER` is required. With the wiring in `DerProxySerializer` disabled,
`jossSmartProxyIsRejectedWhenDerRequired` fails ("Expected IOException ... but nothing was thrown"): **that run also shows the hole itself, a JOSS payload decoded inside a DER stream by the carrier and default provider
(in a unit test, no SecurityManager).** `DerProxyMarshalledInstanceTest` (7) and `AtomicDerInvocationLayerTest` (7) still pass.

**Jeri hops tested (2026-10-31, later).** `jgdms-jeri/.../AtomicDerRequiredFormatCallTest` (2 tests, 0 failures) calls a real `AtomicDerInvocationHandler` proxy over a fake endpoint whose request reports `ATOMIC_DER` as unfulfilled and whose
response is a `DerProxySerializer` carrier holding a JOSS `MarshalledInstance`. With `ATOMIC_DER` required the call throws an `UnmarshalException` caused by `UnsupportedConstraintException` ("payloadFormat ..."); with nothing required the same bytes decode to the payload. Load-bearing, each hop broken alone: removing the
`context.add(...)` in `BasicInvocationHandler` (hop a) makes the rejection test fail with "JOSS smart proxy was decoded in a call requiring ATOMIC_DER"; passing `null` in `AtomicDerInvocationHandler.createMarshalInputStream` (hop b) fails it identically. Both files restored byte-identical (`cmp`) and the pair re-run green.
`AtomicDerInvocationLayerTest` (7) still passes. Run as a reactor (`-pl jgdms-platform,jgdms-der,jgdms-jeri`): `-pl jgdms-jeri` alone resolves a stale `jgdms-der` jar from the local repo and fails with `NoSuchMethodError`. The carrier is built by reflection (its constructor is package-private in `jgdms-der`).

**Still not done.** ~~(1) No test drives the jeri hops (a) and (b): nothing yet shows a stub with `ATOMIC_DER` required passing the value from `invokeRemoteMethodOnce` to the `ResolutionContext`.~~ (1) is closed by the test above. (2) The requirement exists only when the
client asks for `ATOMIC_DER`; a client with no constraint is unprotected (the defaults finding). The wrapper must set it, and a code-free host must not run without it. ~~(3) Server-side unmarshalling of arguments
(`AtomicDerInvocationDispatcher`) is not covered.~~ (3) is closed (2026-10-31, later): `AtomicDerInvocationDispatcher.createMarshalInputStream` now requires its own `marshallingFormat()` (ATOMIC_DER), since a DER dispatcher is DER-only by construction.
`AtomicDerRequiredFormatCallTest.dispatcherRefusesJossSmartProxyArgument` passes with the change and fails with it removed ("JOSS smart proxy was decoded by a DER dispatcher"); file restored, 10 tests green with `AtomicDerInvocationLayerTest`.
Behaviour change to flag: a DER-exported service no longer accepts a JOSS smart proxy as a call argument. (4) Other places that call `MarshalledInstance.get` outside a stream context (for example a `MarshalledWrapper` or `ServiceItem` field read later) are not covered.
(5) `Resolve` admission in a handler slot is unchanged. (6) `ProxyCodebaseSpi` providers other than the default were not read; one that drops the context would drop the check. (7) The change is uncommitted and the
wider module test suites were not run.

## 12. Other services: operator rulings and the exporter default (2026-10-31, later)

Source for the five per-service reports: `INVESTIGATION-code-free-{outrigger,mahalo,mercury,norm,fiddler}-2026-10-31.md` (Sonnet 5.5, static reading, not yet reviewed; Opus and Fable reviews are being written to `REVIEW-code-free-service-reports-{opus,fable}-2026-10-31.md`). Method: `TRAINING-code-free-proxy-investigation-2026-10-31.md`.

**Rulings (operator, in chat, 2026-10-31, verbatim):**
1. *"ILFactory is a configuration item, yes we should change the default to DER."*
2. *"No the client is not allowed to determine the service proxy implementation code. The contract with the client is the service interface, not the serialized form of the classes."*
3. *"We may need to create new service interfaces, then provide local wrappers, like we did for ServiceRegistrar."*

**What follows from them (the author's reading, not a ruling):**
- Ruling 2 settles the tier question the reports raised ("bare dynamic proxy or resident adapter"): the proxy implementation is resident and local, and the wire carries data against a service interface. The "CANNOT if a bare dynamic proxy is required" branches in the Outrigger report no longer decide the verdict.
- It also bears on 11.1 path A: the format and the decoder must be fixed by the receiving host, not named by the sender or left to a client constraint. The `ATOMIC_DER` requirement added on 2026-10-31 closes the nested-payload hole for DER calls, but a host that relies on a client asking for it is still open; the wrapper or host configuration must set it.
- Ruling 3 gives the shape per service: a new record-style wire interface (as `RemoteRegistrar`, section 3.8) plus a resident wrapper presenting the legacy interface. The reports' blockers (SetProxy overload folding, the Mercury client-built iterator, the Fiddler registration and lease, the Outrigger EntryRep conversion) all move into the resident wrapper or onto the server.

**Change made (uncommitted):** the default exporter in Norm (`NormServerInitializer`, `ActivatableNormServerImpl`), Fiddler (`FiddlerInit`), Mercury (`MailboxImplInit`, two sites) and Mahalo (`TxnManagerImplInitializer`, two sites) changes from `AtomicILFactory` to `AtomicDerILFactory`. A `serverExporter` configuration entry still overrides it. Mahalo's `useAnnotations=true` argument is dropped (DER carries no codebase annotation). `jgdms-der` becomes compile scope in `mercury-service` and `norm-service` (it was test scope and shadowed the transitive dependency; `outrigger-service` documents the same trap). Compiles; the four service modules' own tests pass (22, 9, 10, 6 run, 0 failures). **Those tests do not exercise the exporter,** so the change is not shown to work end to end.
Behaviour change: a DER dispatcher requires ATOMIC_DER on call arguments, so a client that sends JOSS arguments to these services is refused. Not done: Reggie (`RegistrarImpl`, replaced by Derek), `group`, `verdict-registry`, `policy-service`; shipped configs that pin JOSS (for example `examples/wire-protocol-showcase/demo7-filter-pushdown/configs/mahalo.config:22-24` says "JOSS is fine for Mahalo") were not changed.

**Rulings and a candidate, later the same day.**
4. *"All services should have the DER default."* (operator, in chat, 2026-10-31). Applied (uncommitted) to the remaining four: Reggie (`RegistrarImpl`, both sites), `group` (`SharedGroupImplInit`), `verdict-registry` (`ReadReplicaVerdictRegistry`) and `policy-service` (`PolicyUpdateListener`, code and its javadoc). `jgdms-der` becomes compile scope in `reggie-service`. All four compile (`mvn -o -pl ... -am compile`). Their tests were not run. Outrigger already defaulted to `AtomicDerILFactory`. Test fixtures that name `AtomicILFactory` on purpose (`DerOnlyConfigRejectTest`) are unchanged.
5. **Candidate D15, from the operator's remark *"I'm thinking the RemoteRegistrar wrapper could be applied during discovery"* (a direction under consideration, not yet a decision):** the discovery layer decodes the registrar's stub and wraps it in the resident wrapper before any client code sees it. The author's reading: this is the adaptation point section 3.8 already calls for, and it fits R7 (the `ServiceID` travels in the discovery record and is bound after `prepareProxy`, which also answers 11.1 path 3, discovery keying on the proxy's self-asserted `ServiceID`). It does not cover the other services: their proxies arrive as lookup service items, so the equivalent hook is the `ServiceDiscoveryManager` / proxy-preparer path, not discovery. Open: whether the wrap happens before or after `prepareProxy` (R7 says after, so the stub is authenticated first), and what the wrapper does when a legacy smart-proxy registrar is announced.

**Candidate D16 (operator proposal, 2026-10-31, after the Fable review; a direction under consideration, not a decision).** Verbatim: *"the new service interfaces should have a default method that returns the wrapper, keeping the wrapper implementation private. This would be a standard that would allow clients to match a service using the original service interface, but it is decorated onto the service proxy when the old service interface was searched for ... the MarshalledInstance could already contain the wrapper class, that we make part of the platform locally, or downloaded dynamically if not available locally, such that the wrapper is not preferred ... the wrapper would belong in the lib-dl.jar"*.
Author's reading, in three parts (UNREVIEWED):
1. **A default method on the new interface that returns the old interface, implementation private in `jgdms-lib-dl`: adopted in principle.** It makes the contract the service interface, as ruling 2 requires. It needs three things that are not yet true: (a) `BasicInvocationHandler` forwards every method it does not recognise, and `Util.checkProxyRemoteMethod` requires a `RemoteException` declaration (Fiddler review: `BasicInvocationHandler.java:713-723`, `:733-764`, `:868-873`), so a default method on a `Proxy` would be refused or forwarded; the handler needs a case that runs default methods locally (`InvocationHandler.invokeDefault`, JDK 16+; **not checked against this build**). (b) The lookup service matches templates by the registered proxy's type names, so `instanceof ServiceRegistrar` is false for a `RemoteRegistrar` stub; the new interface needs a declared "presents as" type that Reggie/Derek adds to the item's type set (**Reggie's type computation not read**). (c) The wrapper must preserve `RemoteMethodControl`, `TrustEquivalence` and its own `TrustVerifier` (R9).
2. **"Downloaded dynamically if not available locally": conflicts with ruling 2 and with section 11 path A.** If the wrapper class is named by the sender and fetched from the sender's codebase, the sender chooses the code that runs; "not preferred" only means a local copy wins. Narrowing that keeps the intent: resident only on a code-free host (class absent means fail closed), and legacy hosts may keep today's download path.
3. **"The MarshalledInstance could already contain the wrapper class": this puts a class name on the wire.** It is the case 11.1 path A describes (the sender names the decoder, and `Resolve` classes are admitted in any slot). The alternative that keeps data on the wire is that the client calls the default method after decode and `prepareProxy`, which is also D15's adaptation point. The two proposals therefore differ: D15 and the default method apply the wrapper at the client; "already contains the wrapper" applies it at the registrar.
Open for the operator: which of the two (client-applied or registrar-applied), and whether code-free hosts forbid downloading the wrapper.

**Ruling 6 (operator, in chat, 2026-10-31), answering D16's open question:** the wrapper is applied **at the client only**; *"Yes, client-only"* (to the proposal: client-only, with no download of the wrapper on code-free hosts). Consequences recorded by the author: (a) the registrar never sends a wrapper class name, so the wire carries data against the service interface and the sender does not choose the decoder (11.1 path A, for the wrapper); (b) the wrapper is applied after decode and `prepareProxy`, through the new interface's default method, which is also D15's adaptation point; (c) on a code-free host a missing wrapper class fails closed, and legacy hosts may keep today's download. Still open from D16: the `BasicInvocationHandler` case that runs default methods locally, the "presents as" type in the lookup item's type set (Reggie's type computation unread), and keeping `RemoteMethodControl`/`TrustEquivalence`/`TrustVerifier` on the wrapper.

**Amendment to D16 and ruling 6 (operator, in chat, 2026-10-31):** the wrapper may be downloaded if it is not available locally, because *"if the class isn't available locally, the service won't work, but we could have a pre identified digest that marks the download as safe, since it's a published JGDMS class"*.
Author's reading (UNREVIEWED): this answers the objection to "downloaded dynamically" (the sender would choose the code), because the **receiver** holds the digest. It is acceptable only if all of these hold:
1. **The digest is local and the sender cannot supply or change it.** It comes from the host's own configuration or a signed JGDMS release manifest, never from the codebase annotation or the `MarshalledInstance`.
2. **The digest covers everything loaded.** It must bind the whole jar the wrapper comes from, and the class loader must load only from that digest-verified artifact. Verifying one jar and then resolving other classes by the sender's URL leaves the hole open. The wrapper's own dependencies must be inside the pinned set or resident.
3. **The URL may stay sender-named only if the digest check cannot be skipped.** Safer: fetch from a configured mirror, and use the sender's URL for nothing.
4. **Failure is closed.** A digest mismatch or an absent digest means no wrapper and the service is unusable, not a fallback to unverified code. This is the code-free host's rule; a legacy host may keep today's behaviour.
5. **Versioning.** The digest pins one JGDMS version of the wrapper; upgrades need the host's list to change, and the list should hold several.
Existing machinery (read as filenames only, behaviour not checked): `verifyCodebaseIntegrity` / `IntegrityVerifier` in `jgdms-platform` (`MarshalledInstance`, `ProxyCodebaseSpi`, `ClassLoading`, `Security`), and DirtyChai's `DIGEST_GRANT_PLAN.md` (`DigestCodeSource`, `DigestGrant`). **Caution:** section 4's earlier test run found 16 of 462 `jgdms-platform` failures concerning digest-based grants (`DigestDomainGrantTest`, O9, cause undetermined), so the digest path is not known to be sound until O9 is settled.

**Ruling 7 (operator, in chat, 2026-10-31):** *"The digest grant would be in the default policy."* That is, the pre-identified digest of the published wrapper jar lives in the platform's default policy as a digest grant. Author's reading (UNREVIEWED), with what was checked:
- **Exists in code (VERIFIED by reading the files, not run):** DirtyChai has `java/security/DigestCodeSource.java` and `org/apache/river/api/security/DigestGrant.java` (commit `11895e063fe`, #203), and `DefaultPolicyScanner.java:231-237` parses `grant ... digest "SHA-256:<hex>"` in a policy file, although `DIGEST_GRANT_PLAN.md` section 5 lists policy-file syntax as optional phase 2. Whether the parser passes the digest through to a working grant, and the shipped `java.policy` entry, were not read.
- **Satisfies condition 1** (the digest is local and the sender cannot supply it), provided the policy file is trusted and write-protected.
- **A grant is not a gate.** A digest grant gives permissions to code whose digest matches. It does not by itself stop a class with a different digest from being downloaded and run with fewer permissions. Condition 4 (fail closed) needs the class loading path to *require* a permission (for example `LoadClassPermission`, which DirtyChai defines) that only the pinned digest's code source is granted, and the unpinned download to be refused.
- **It needs a SecurityManager.** Memo 11.1 already records that with `sm == null` the checks return immediately. A code-free host that relies on the policy must refuse to start without one, or the digest protects nothing.
- Still open: whether the policy file may be replaced or extended by deployment configuration, and the 16 `DigestDomainGrantTest` failures (O9).

**Ruling 8 (operator, in chat, 2026-10-31):** *"The shipped java.policy should have a LoadClassPermission entry granted to the digest. This way the jar can be downloaded from anywhere. Whether the URLPermission and DownloadPermission are granted would depend on whether the service authenticated, if it didn't then the jar file won't be downloaded."*
Checked against DirtyChai's `java/security/SecureClassLoader.java` (read, not run; lines 336-412 of the file as read):
- **The two gates exist in this order.** For a remote codebase the loader first checks `URLPermission(uri, "GET:")` against an `AccessControlContext` made of the protection domain being built, then reads the jar to compute a `DigestCodeSource` (SHA-256, hard-coded) and builds a new protection domain from the digest's permissions, then checks `LoadClassPermission` against that digest domain. This is the shape the ruling describes: the network gate first (grantable by principal), the execution gate second (grantable by digest).
- **`LoadClassPermission` has a fixed target `"ALLOW"`** (the name is ignored), so the digest lives in the grant's codebase digest, not in the permission name. Its javadoc already recommends a signed jar or a message digest.
- **The shipped `src/java.base/share/conf/security/java.policy` has no `LoadClassPermission`, `URLPermission` or `DownloadPermission` entry** (grep returned nothing), so the entry in the ruling is an addition. Under DirtyChai's rules that file edit is human-authored; I have not made it.
- **Not established, and decisive for the security claim:** (a) whether the bytes hashed for the `DigestCodeSource` are the same bytes the loader defines classes from, or a second fetch of a remote URL (a TOCTOU gap if they differ); (b) what the `pals` principals passed to the protection domain are, and whether they are the authenticated service's principals, which the "granted depending on whether the service authenticated" rule needs; (c) that the service has authenticated before the download, which the client-only wrapper (ruling 6, applied after `prepareProxy`) supports but nothing yet enforces; (d) a SecurityManager must be installed (`sm != null` guards every check above).

**Ruling 9 (operator, in chat, 2026-10-31):** *"JGDMS isn't deployed in production without a SecurityManager."* This answers the SecurityManager question of memo 11.1 for production (an operator fact, not checked here). It means the `sm != null` guards in `SecureClassLoader` and the `DeSerializationPermission` check apply in production; the "return immediately when `sm == null`" paths matter only for tests and development runs, including the unit tests added on 2026-10-31, which run without one. Residual: the code-free host should still refuse to start without a SecurityManager rather than rely on deployment practice, since a mis-started host would otherwise fail open (row 1 of section 11). The `verify=true`, `discoveryConstraints` and class-provider questions of 11.1 are still open.

**Trace of the digest gate (2026-10-31, after rulings 8 and 9; read-only, nothing run).** Files: DirtyChai `java/security/SecureClassLoader.java` (SCL), `java/security/DigestCodeSource.java` (DCS); JGDMS `jgdms-platform/.../api/net/RFC3986URLClassLoader.java` (RFC).
1. **Which principals are on the downloaded code's domain (VERIFIED by reading).** SCL builds the domain with `pals` taken from the JVM's own SPIFFE subject (`SpiffeCredentialManager.getInstance().getSubject()`), merged with the `Principal[]` a loader subclass passes to `defineClass` (SCL, `getProtectionDomain`, lines 336-355). RFC embeds its constructor argument `serverPrincipals` through that overload (RFC, `defineClassWithPrincipals`, lines 1209-1243), by reflection and only when it is non-null and DirtyChai is present. Consequences: (a) a grant keyed on the service's authenticated principal is expressible, but only if whoever creates the loader passes the service's principals; **no caller of that constructor argument exists in `jgdms-platform`, `jgdms-lib` or `jgdms-lib-dl`** (grep, one pattern: `serverPrincipals`), so in this repository the "granted depending on whether the service authenticated" path is unused; (b) the JVM's own SPIFFE principals are merged into the same set, so a grant to one of them applies to every downloaded jar and cannot be told apart from the service's identity; (c) if the JVM has no read-only SPIFFE subject, `pals` stays `null` and the `Principal[]` passed in is **dropped** (the merge sits inside the `sub != null && sub.isReadOnly()` branch): a principal-keyed grant then matches nothing, which fails closed for the network gate but means the principal-gated model needs a SPIFFE subject.
2. **The network gate sees only the new code's domain (VERIFIED).** The `URLPermission(uri, "GET:")` check runs against an `AccessControlContext` made of that one domain (`AccessControlContext.create(new ProtectionDomain[]{pd}, false)`), so the calling thread's stack, and any `Subject` on it, are not consulted. Authentication state reaches the check only through `pals`.
3. **The digest is not obviously the digest of the classes (UNDETERMINED, the open risk).** DCS computes the digest by opening its own `URLConnection` with caches on (`computeDigest`, the `else` branch, `conn.setUseCaches(true)`); RFC reads class bytes by a separate `resURL.openStream()` (RFC line 359) and hands those bytes to `defineClass`. If both go through the same JVM response cache they see the same bytes; **not established.** SCL then caches the domain per codebase key (`pdcache`), so classes loaded later from the same URL inherit the first digest without a new check.
4. **DCS keeps its own first-seen digest per URI** (`digestCache.putIfAbsent`, with `SecurityException` on a later change). That is trust on first use, a tamper detector for a second download, **not the pin**: the pin is the policy grant matching the `DigestCodeSource`'s digest.
5. **Needed to make the gate sound (author's reading):** define classes from exactly the bytes that were digested (digest the downloaded copy and load from it, or hand `defineClass` the digested buffer); a test that serves a different jar on the second read and shows the load refused; and a loader factory that passes the authenticated service's principals, with a test showing that no principals means no download.

**Ruling 10 (operator, in chat, 2026-10-31):** *"URLPermission and DownloadPermission should [be] granted by ProxyPreparer following service authentication. Normally a file called Permissions.perms is contained within the proxy jar file."* This corrects the trace above: the authentication-dependent grants are **dynamic grants made by the preparer**, not principals fixed in the loader's constructor. Checked (read, not run):
- `BasicProxyPreparer.prepareProxy` runs `verify(proxy)`, then `grant(proxy)`, then `setConstraints(proxy)` (`jgdms-platform/.../net/jini/security/BasicProxyPreparer.java:330-337`). `grant` calls `Security.grant(proxy.getClass(), perms)` with the preparer's configured permissions and, if the proxy's class loader implements `AdvisoryDynamicPermissions`, with the permissions that loader advertises (lines 411, 420-424). So the grant does follow verification, as ruled.
- **File name:** the JGDMS sources I searched name the jar entry **`META-INF/PERMISSIONS.LIST`** (`ClinitVerdict`, `JarAnalysisReport` in `au/net/zeus/jgdms/api/codebase`). `Permissions.perms` appears in no `.java`, `.md`, `.xml`, `.config` or `.policy` file in JGDMS or DirtyChai (grep above, no hits). Either it is a name from another tool or version, or the entry is the same file; **to be settled with the operator.**
- **Open, and decisive for the ruling:** the grant is made to `proxy.getClass()` (the prepared stub's class). The wrapper jar is downloaded **later**, by a loader whose new domain is checked for `URLPermission` against a context containing only that domain (trace point 2), and `DownloadPermission` is checked against the stack of the code that starts the download. Whether a grant attached to the stub's class is visible to either check is **not established**; it needs a test: prepare an authenticated stub, then load the wrapper from a digest-pinned jar, and show the load succeeds only after preparation and is refused for an unauthenticated stub.

**Ruling 11 (operator, in chat, 2026-10-31):** *"We need to update dynamically granted permissions I suspect. Since we may have different levels of trust of authenticating services."* Checked (read, not run), `jgdms-platform`:
- **Today a preparer's grant is one level and additive.** `BasicProxyPreparer.grant` calls `Security.grant(Class, Permission[])` with a fixed list (the preparer's configuration, plus whatever the proxy's loader advertises through `AdvisoryDynamicPermissions`, `BasicProxyPreparer.java:411, 420-424`). It does not look at who authenticated, and `Security` has no revoke method.
- **Pieces that already exist:** `Security.grant(Class, Principal[], Permission[])` (`Security.java:1288`) grants against authenticated principals; `DynamicPolicy.getGrants(Class, Principal[])` reads them back (`DynamicPolicy.java:169`); `org.apache.river.api.security.RevocablePolicy` has `grant(PermissionGrant)` and `revokeSupported()`, and `DynamicPolicyProvider` implements it (`DynamicPolicyProvider.java:142`); `LeasedDelegation` exists for time-limited delegation. Whether the shipped policy reports `revokeSupported()` as true, and whether a `PermissionGrant` can be withdrawn or replaced after use, were **not read**.
- **Author's reading of the change:** the preparer needs named permission sets per trust level, chosen from the authenticated principal and the verified constraints, and its grants need to be replaceable (a re-authentication at a different level, or an expiry, must narrow them). Two stages must be kept apart: the download-time permissions (`URLPermission`, `DownloadPermission`) that must exist before the wrapper jar loads, and the run-time permissions of the loaded code. Open: what happens to classes already loaded when a grant is narrowed, and whether the grant is keyed to the stub's class, the principal, or the new loader (see ruling 10).

**Ruling 12 (operator, in chat, 2026-10-31), correcting my reading of where the grant goes:** *"The grant goes to the stub class's ClassLoader and digests of the CodeSources from the proxy's codebase annotation."* Also: *"`Permissions.perms` ... is in the proxy's jar file. URLPermission is required to obtain the proxy jar file I suspect - check this."*
Checked (read, not run):
- **The digest-scoped grant exists.** `Security.grant(Class cl, CodeSource[] codebase, Principal[] principals, Permission[] permissions)` (`Security.java` from line 1306; `@since 4.0.0`) grants "only to the protection domains of the verified code of the given class's class loader: those whose code source has the identity (location, signers and content digest) of one of the given code sources, **including domains not yet created**", throws `UnsupportedOperationException` rather than widening, and throws `IllegalStateException` for an inconclusive codebase verdict. `DynamicPolicyProvider.grant(Class, CodeSource[], ...)` implements it (line 674, building the grant with `.codeSources(...)`). This matches the ruling, and "including domains not yet created" answers the chicken-and-egg worry of ruling 10 (the grant can exist before the jar's domain is created).
- **But `BasicProxyPreparer.grant` does not call it.** It calls `Security.grant(proxy.getClass(), perms)` (lines 411, 424), the whole-class-loader overload, with no code source or digest. A grep of `jgdms-platform`, `jgdms-lib` and `jgdms-lib-dl` found **no caller** of the `CodeSource[]` overload other than its definitions. As read, the preparer's grants are wider than ruling 12 describes.
- **URLPermission is required to fetch the jar: confirmed in one place, unestablished in another.** In `SecureClassLoader.getProtectionDomain`, a remote codebase triggers an explicit `URLPermission(uri, "GET:")` check against the new domain (lines 380-383 of the file as read) before the digest is computed. Separately the JDK's own URL connection checks its permission against the calling stack when a SecurityManager is installed; `SecureClassLoader` contains no `doPrivileged`, and `RFC3986URLClassLoader` opens the class stream at line 359 outside any `doPrivileged` I found (its `doPrivileged` uses are at lines 138, 920, 1061, 1086, for other purposes). Whether the stack-based check is satisfied by the downloaded code's own grants or falls on the caller (the client application) was **not established**.
- **`Permissions.perms`:** no file by that name, and no text mention, in JGDMS or DirtyChai sources (`find -iname "*.perms"` and a case-insensitive grep, both empty). A scan of built jars for a `perms`/`PERMISSIONS` entry was started and has not returned.

**Ruling 13 (operator, in chat, 2026-10-31):** the entry is **`META-INF/PERMISSIONS.LIST`** (the operator first said `PERMISSIONS.PERM`, then corrected himself; the earlier names `Permissions.perms` and `PERMISSIONS.PERM` appear nowhere in the sources). It is the file the sources already name, so the earlier "different mechanism?" question is closed. Checked (read, not run):
- **Written by** `tools/policy-condenser/.../ProxyPolicyGenerator.writePermissionsList` (line 429), one per `-dl` artefact (tests name mahalo-dl, reggie-dl, norm-dl, outrigger-dl, fiddler-dl, mercury-dl), entries encoded by `AdvisoryPermissionParser.getEncoded`.
- **Read by** `services/bytecode-analysis-engine/.../JarAnalyzer.java:216` (it feeds `declaredPermissions`, line 260, into the analysis report, and line 323-331 cross-checks declared against detected permission classes). That is the analysis route, not the preparer.
- **At run time** both `BasicProxyPreparer` and `VerifyingProxyPreparer` take permissions from a proxy loader only if it `instanceof AdvisoryDynamicPermissions` (`BasicProxyPreparer.java:420`, `VerifyingProxyPreparer.java:282-283`), and both then call the whole-loader `Security.grant(Class, ...)` overloads (`VerifyingProxyPreparer.java:268, 270, 287, 289`). **No class in `jgdms-platform` main implements `AdvisoryDynamicPermissions`** (a grep for the interface name found only the interface, the parser, the two preparers and `VerdictPermissionMapper`), so I did not find the code that turns a jar's `PERMISSIONS.LIST` into the loader's advisory permissions at run time; it may live in another module or be wired through `VerdictPermissionMapper`. **Open.**
- Consequence for ruling 12: both preparers use the wide overload, and neither calls the digest-scoped `Security.grant(Class, CodeSource[], ...)`. Moving them to it, with the digests taken from the verified codebase, is the change that would make the grant as narrow as the operator describes.

**Run-time reader of `META-INF/PERMISSIONS.LIST` found (2026-10-31; read, not run).** It is `jgdms-pref-class-loader/.../net/jini/loader/pref/PreferredClassLoader.java`, a module outside `jgdms-platform` (which is why the earlier search missed it; the statement above that "no class implements `AdvisoryDynamicPermissions`" held only for `jgdms-platform` main). `PreferredClassLoader implements ClassAnnotation, AdvisoryDynamicPermissions` (line 242).
- **When it reads:** in the **constructor** (lines 595-621), under `AccessController.doPrivileged`, for the loader's first URL: `PreferredPermissionsPrivilegedAction` (lines 1568-1625) opens `META-INF/PERMISSIONS.LIST` from each URL through `getPreferredInputStream`, skips `#` and `//` lines, and parses each line with `AdvisoryPermissionParser.parse(line, loader)` using the **parent** loader, so permission classes not visible there stay unresolved until the policy resolves them. The same constructor fetches the preferred-class list (`PreferredResourcesPrivilegedExceptionAction`). `getPermissions()` returns the parsed array (a clone) or `DEFAULT_PERMISSIONS` when the jar has none (lines 1561-1566). Read failures are logged at `CONFIG` and treated as no advisory permissions.
- **What it means for the operator's rulings (author's reading):**
  1. **The list is the jar's own request.** Whoever builds the jar chooses its contents, so it must be treated as untrusted input to a grant. The only ceiling visible in the code I read is that `Security.grant` requires the caller to hold `GrantPermission` for what it grants (the `Security.grant(Class, CodeSource[], ...)` javadoc); whether the deployed policy sets that ceiling low enough was not checked.
  2. **The first fetch happens before authentication.** The constructor fetches `PERMISSIONS.LIST` and the preferred list when the loader is created, which is during unmarshalling and before `prepareProxy`. Because that read is `doPrivileged`, the permission to open the URL is checked against `PreferredClassLoader`'s own domain, which is a static grant, not a grant made after the service authenticated. So **"URLPermission and DownloadPermission granted by the preparer after authentication" cannot gate that first fetch** unless the loader is not created until after the stub is prepared. This is the argument for ruling 6 (wrapper applied after `prepareProxy`): the check has to happen before the loader for the wrapper jar is constructed.
  3. **The grant that follows is wide.** After the loader exists, `BasicProxyPreparer`/`VerifyingProxyPreparer` give these advisory permissions to the whole loader (lines noted above), not to the digest-scoped code sources of ruling 12.

**Ruling 14 (operator, in chat, 2026-10-31):** *"PERMISSIONS.LIST is copied for the codebases in question from a polpAudit. This is currently done manually, a tool would be a better option."* Checked (read, not run): a tool for the derivation already exists. `ProxyPolicyGenerator` (`tools/policy-condenser`, documented in `JGDMS/docs/ProxyPolicyGenerator.md`) takes the least-privilege policy file produced by the `polpAudit` workflow and writes the `GrantPermission` ceiling and `<out>/<artefact>/META-INF/PERMISSIONS.LIST` per proxy codebase (artefacts identified by the `*-dl.jar` convention), and `qa/build.xml` runs it from the `policy-update` target (`ant -Dpolicy.update=true policy-update`, best-effort, output under `<policy>.proxy-permissions/`). The generated file is marked "do not edit by hand". What I did **not** establish is the last step: whether anything puts that generated file into the `-dl` jar at build time, or whether the copy into the jar is the manual step. If it is, the tool to add is a build step (a Maven plugin execution or an Ant target) that places the generated entry into each `-dl` jar. Separately, the design point from the run-time reader stands: the list is the jar's own request (`PreferredClassLoader`), so a generated list is only as trustworthy as the audit run that produced it.
