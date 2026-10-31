# Investigation: can Fiddler (lookup discovery service) use a code-free proxy?

Model: claude-sonnet-5-5 (Sonnet 5.5). Date 2026-10-31. Report only; no production code, test or other file was changed.
Method: static reading. Nothing was compiled or run. Labels: VERIFIED = I read the cited line; RELAYED = taken from the memo or training note
without re-reading the code; PLAUSIBLE = inference; UNDETERMINED = not settled by the files.
Abbreviations. DL = `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\services\fiddler\fiddler-dl\src\main\java\org\apache\river\fiddler\proxy\`.
SVC = `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\services\fiddler\fiddler-service\src\main\java\org\apache\river\fiddler\`.
LIBDL = `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\jgdms-lib-dl\src\main\java\org\apache\river\`.
PLAT = `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\jgdms-platform\src\main\java\net\jini\`.
JERI = `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\jgdms-jeri\src\main\java\net\jini\jeri\`.

## Verdict (short)

**CAN WITH CHANGES for the service proxy itself; CANNOT as a pure tier-1 dynamic proxy for the objects it hands out.**
The outer service proxy is a pure forwarder and can be replaced by a dynamic proxy. The registration object, the lease and `getRegistrars` hold
client-side logic or state that an `InvocationHandler` cannot carry, so they need either a resident class (tier 2/3) or a changed contract.
The registrar payload that Fiddler relays is itself a Reggie smart proxy, so the service is not fully code-free until the registrars are data.
Details and the smallest-next-change list are at the end.

## Q1. What does the service export today?

- Exporter. Default is a `BasicJeriExporter` over `TcpServerEndpoint` with `new AtomicILFactory(null, null, Fiddler.class.getClassLoader())`
  (`SVC FiddlerInit.java:245-255`). VERIFIED. That is the JOSS atomic factory, not `AtomicDerILFactory`. The default can be replaced by configuration entry
  `serverExporter` (`FiddlerInit.java:280-285`). VERIFIED. No reference to `AtomicDerILFactory` exists under `services/fiddler`
  (grep over the module returned only `FiddlerImpl.java` and `FiddlerInit.java`, matching `AtomicILFactory`/`Exporter` only). VERIFIED by grep.
- The exported (inner) stub is a dynamic proxy: `innerProxy = (Fiddler) serverExporter.export(FiddlerImpl.this)` (`FiddlerImpl.java:5360`). VERIFIED.
- Three more objects are built from it at start-up (`FiddlerImpl.java:5363-5365`): the outer service proxy
  `FiddlerProxy.createServiceProxy(innerProxy, proxyID)`, the admin proxy `FiddlerAdminProxy.createAdminProxy(...)`, and the `JoinManager` registers the outer proxy
  with lookup services (`:5371`). VERIFIED.
- Per registration the service creates `FiddlerLease.createLease(innerProxy, proxyID, regID, leaseID, expiration)`, an `EventRegistration(eventID, outerProxy, regLease, seqNum)`
  and `FiddlerRegistration.createRegistration(innerProxy, regID, eventReg)` (`FiddlerImpl.java:5603-5613`). VERIFIED.
- Client-visible classes (all `@AtomicSerial`, abstract base plus package-private `Constrainable*` leaf): `FiddlerProxy` (DL `FiddlerProxy.java:50-53`, leaf `:358-360`),
  `FiddlerAdminProxy` (DL `FiddlerAdminProxy.java:49-50`, leaf `:668`), `FiddlerRegistration` (DL `FiddlerRegistration.java:62-64`), `FiddlerLease`
  (DL `FiddlerLease.java:52-55`), `FiddlerLeaseMap` (DL `FiddlerLeaseMap.java:54`, not serialised), `FiddlerRenewResults`, and the trust verifier `ProxyVerifier`
  (DL `ProxyVerifier.java:42-43`). VERIFIED. All ten serialised classes are listed in `FiddlerProxyMarshalDelegate.SERVED` (DL `FiddlerProxyMarshalDelegate.java:61-67`). VERIFIED.
- Trust: `FiddlerImpl.getProxyVerifier()` returns `new ProxyVerifier(innerProxy, proxyID)` (`FiddlerImpl.java:2651-2654`). VERIFIED.
- Constrainability is mandatory: every factory throws if the server is not `RemoteMethodControl` (e.g. `FiddlerProxy.java:105-109`,
  `FiddlerRegistration.java:150-154`, `FiddlerLease.java:132-136`). VERIFIED. This is a fail-closed default and matches the code-free rule.

## Q2. Logic in the client-visible proxy beyond forwarding

Per class, with the verdict for a dynamic proxy:

| Class | Logic beyond forwarding | Dynamic proxy enough? |
|---|---|---|
| `FiddlerProxy` (outer) | `getAdmin`, `register` forward to `server` (`FiddlerProxy.java:153-155`, `:215-219`). `getReferentUuid` returns a local field (`:233-235`). `equals`/`hashCode` use the `proxyID` field (`:245-265`). Constraint mapping through `methodMapArray` (`:373-394`, `constrainServer` `:473-483`). `getProxy()` returns `server` (`:283-286`). | Forwarding: yes. `ReferentUuid`/`ProxyAccessor`: see below. |
| `FiddlerRegistration` | Forwards 11 methods keyed by `registrationID` (e.g. `:364-371`, `:703-709`). Two methods are local: `getEventRegistration()` and `getLease()` (`:211-228`). `getRegistrars()` fetches `MarshalledInstance[]`, then unmarshals each with `marshalledInst.get(false)` (`:294-337`, `:785-833`), tolerates partial failure by collecting exceptions into `LookupUnmarshalException`, de-duplicates through a `HashSet` (`:329-336`). | **No.** Local state (`eventReg`) and real client logic (unmarshal loop, partial-failure contract, dedup). |
| `FiddlerLease` | Extends `AbstractLease`, which holds a local, volatile `expiration`, converts duration to absolute time on decode, and computes expiry on `renew` (`LIBDL lease\AbstractLease.java:85`, `:93-107`, `:154-162`). `canBatch` compares `serverID` (`FiddlerLease.java:245-248`). `createLeaseMap` builds a local `FiddlerLeaseMap` (`:217-219`). | **No.** Local clock-dependent state and a client-side `LeaseMap`. |
| `FiddlerLeaseMap` | `renewAll`/`cancelAll` batch ids into `renewLeases`/`cancelLeases`, then update each lease's expiry locally (`FiddlerLeaseMap.java:174-218`, `:238-268`). Note `dur[i] = durations.next().intValue()` at `:192` truncates a `long` to `int` (a latent bug, not a code-free issue). | **No** (local). |
| `FiddlerAdminProxy` | Pure forwarding of `JoinAdmin` + `FiddlerAdmin` + `destroy` (`FiddlerAdminProxy.java:179-563`); local `getReferentUuid`/`equals`/`hashCode` (`:541`, `:576`, `:594`). | Yes, apart from `ReferentUuid`. |
| `ProxyVerifier` | Server-side object; compares input proxy with the canonical `innerProxy` through `checkTrustEquivalence` and compares `proxyID` (`ProxyVerifier.java:219-220`). | n/a (a verifier, not a proxy). |

All of the above VERIFIED by reading, except the line numbers inside `FiddlerAdminProxy` which were taken from a method-signature grep (the forwarding bodies one line below each signature were shown in the grep output). 

Why a dynamic proxy cannot carry the local methods (VERIFIED): `BasicInvocationHandler.invoke` handles only `Object`, `RemoteMethodControl` and `TrustEquivalence` methods locally
and sends every other method to `invokeRemoteMethod` (`JERI BasicInvocationHandler.java:733-764`). Its contract says it throws `IllegalArgumentException` when a
same-signature method in a superinterface does not declare `RemoteException` (`:713-720`). `ReferentUuid.getReferentUuid()` (`PLAT id\ReferentUuid.java:64`),
`Lease.getExpiration`, `LookupDiscoveryRegistration.getEventRegistration()` (`PLAT discovery\LookupDiscoveryRegistration.java:68`) and `getLease()` (`:86`) declare no `RemoteException`.
They cannot be forwarded. A subclass of the handler could override `invoke` (the javadoc invites it, `:706-708`), but that handler would then be a downloaded class, which is tier 4.

## Q3. Which interfaces must exist on the client

- Public client contracts: `LookupDiscoveryService`, `LookupDiscoveryRegistration`, `ReferentUuid` are in `jgdms-platform` (`PLAT discovery\...`, `PLAT id\ReferentUuid.java`). The client has these. VERIFIED (files exist there).
- The private protocol `Fiddler` (DL `Fiddler.java:54-55`) lives in `fiddler-dl`, together with `FiddlerRenewResults`. `Fiddler` extends `Remote, Administrable, FiddlerAdmin, ServiceProxyAccessor`. VERIFIED.
  `fiddler-dl` depends only on `jgdms-lib-dl` (`fiddler-dl\pom.xml:32-37`). VERIFIED.
- `FiddlerAdmin` (`LIBDL admin\FiddlerAdmin.java:119`) and `ThrowThis` (`LIBDL proxy\ThrowThis.java:54-55`) live in `jgdms-lib-dl`. VERIFIED.
- So `Fiddler` exists only in the service's `-dl` jar. A dynamic proxy exported with the `Fiddler` interface needs `fiddler-dl` and `jgdms-lib-dl` on the client.
  Whether clients carry `jgdms-lib-dl` is UNDETERMINED (its module name is "Service DL Library", `jgdms-lib-dl\pom.xml:31`; the memo treats its lease package as client-side code, RELAYED, memo line 64).
- Two ways to avoid needing `Fiddler` on the client: (a) export `LookupDiscoveryService` plus an admin interface directly, which is possible because `Fiddler.register` already
  has the same signature as `LookupDiscoveryService.register` (`Fiddler.java:111-116` against `FiddlerProxy.java:208-213`); (b) keep `Fiddler` and ship `fiddler-dl` as a library jar.
  PLAUSIBLE; neither was built.

## Q4. What crosses the wire; is it `@AtomicSerial`?

| Value | Where | `@AtomicSerial`? | Label |
|---|---|---|---|
| `Uuid` | ids in all calls | yes (`PLAT id\Uuid.java:125`) | VERIFIED |
| `LookupLocator` | `register`, locator methods | yes (`PLAT core\discovery\LookupLocator.java:77`) | VERIFIED |
| `EventRegistration` | field of registration | yes, final (`PLAT core\event\EventRegistration.java:52-53`) | VERIFIED |
| `RemoteDiscoveryEvent` | event pushed to client | yes (`PLAT discovery\RemoteDiscoveryEvent.java:112-113`) | VERIFIED |
| `ThrowThis` | thrown by 9 server methods | yes (`LIBDL proxy\ThrowThis.java:54`) | VERIFIED |
| `UnknownLeaseException`, `LeaseMapException` | lease calls | yes (`PLAT core\lease\UnknownLeaseException.java:34`, `LeaseMapException.java:46`) | VERIFIED |
| `FiddlerRenewResults` | `renewLeases` return | yes (`FiddlerRenewResults.java:38`) but holds `Exception[]` (`:54`) | VERIFIED |
| `Exception[]` elements in renew/cancel results | `FiddlerRenewResults.exceptions`, `cancelLeases` return | arbitrary `Exception`; whether DER encodes every `Throwable` is not shown by me. `ObjectCodec.java:572-574` mentions a `ThrowableSerializer`. | UNDETERMINED |
| `java.rmi.MarshalledObject handback` | `register` argument | the platform copy imports the `AtomicSerial` types (`PLAT java\rmi\MarshalledObject.java:44-47` per grep), not read further | UNDETERMINED |
| `RemoteEventListener listener` | `register` argument | whatever the client exports. Fiddler prepares it with `listenerPreparer.prepareProxy` (`FiddlerImpl.java:5584`) | VERIFIED (that it is client-supplied); format of the client's listener UNDETERMINED |
| `ServiceRegistrar registrar` | `discard` argument (`Fiddler.java:563-564`) | a Reggie proxy: smart proxy | VERIFIED (signature); class RELAYED (memo line 59) |
| `Entry[]` | admin `getLookupAttributes`/`addLookupAttributes`/`modifyLookupAttributes` (`FiddlerAdminProxy.java:289-339`) | user `Entry` classes are `Serializable`; count of non-`@AtomicSerial` ones in deployments is UNDETERMINED | UNDETERMINED |
| `MarshalledInstance[]` | `getRegistrars` return (`Fiddler.java:164-165`) | `MarshalledInstance` serialises (`PLAT io\MarshalledInstance.java:133`); the payload is a Reggie proxy | VERIFIED (type); payload see Q6 |

Non-`@AtomicSerial` count by noun: the Fiddler-owned types I read are all `@AtomicSerial` (10 serialised types in `SERVED`). Types outside Fiddler that are not shown to be `@AtomicSerial`: 1 argument type
(`RemoteEventListener`, client-supplied), 1 array element type (`Exception` in 2 results), 1 element type (`Entry` in 3 admin methods), 1 opaque field (`MarshalledObject`, unread). That is 4 type slots across 7 methods. PLAUSIBLE count; not exhaustive.

## Q5. Proxies passed as values

Each must also be code-free (VERIFIED from the factories listed in Q1):
1. Outer service proxy: registered with lookups and used as the `EventRegistration` source and as the event source (`FiddlerImpl.java:5606-5609`, `:6809`).
2. Admin proxy, returned by `getAdmin()` (`FiddlerImpl.java:2694-2702`).
3. `FiddlerRegistration`, returned by `register` (`:5612-5616`).
4. `FiddlerLease`, inside the `EventRegistration` and from `getLease()`.
5. `FiddlerLeaseMap`: created locally, never on the wire. Local class needed.
6. Inbound: the client's `RemoteEventListener` and, for `discard`, a `ServiceRegistrar`.
7. Outbound relay: the registrars inside events and `getRegistrars`.
Coupling that a rewrite must keep: `FiddlerRegistration.check` requires `eventReg.getClass()` equals `EventRegistration`, `source instanceof FiddlerProxy` and
`lease instanceof FiddlerLease` (`FiddlerRegistration.java:913-941`). VERIFIED. These checks would have to become checks on whatever the replacement types are, or they fail every decode.

## Q6. Code loading from annotations, and the memo section 11 paths

Code-loading paths:
- `FiddlerRegistration.unmarshalRegistrars` calls `marshalledInst.get(false)` (`FiddlerRegistration.java:817-818`). The `false` is `verifyCodebaseIntegrity`; the call can load classes named by the payload's codebase annotation. VERIFIED (the signature meaning is from the call's argument; I did not read `MarshalledInstance.get`).
- Fiddler's own persistence and event paths also marshal registrars. The server marshals with DER on `addToDiscoveredRegs` (`FiddlerImpl.java:939`, per grep), but `RemoteDiscoveryEvent`'s constructor uses `new AtomicMarshalledInstance(registrars[i])` (`PLAT discovery\RemoteDiscoveryEvent.java:397`), which memo section 11.2(b) says has JOSS as its `payloadFormat`
  (RELAYED). So events and `getRegistrars` may use different formats for the same registrar. PLAUSIBLE.
- The export default is JOSS (Q1), so the server's dispatcher decodes whatever format the client sent.

Section 11 rows that apply (memo lines 589-603):
- Row 1/#1 (required `ATOMIC_DER` not applied): applies. The default factory is `AtomicILFactory` (`FiddlerInit.java:247`). A client that sets no constraint is unprotected (memo 11.2 "Still not done" (2), RELAYED). VERIFIED for the default factory.
- Row 5 (a smart proxy appears in a lookup result): applies directly. `getRegistrars` and events hand the client a Reggie proxy, which the client instantiates. Fiddler cannot be code-free end to end while its registrars are smart proxies. PLAUSIBLE (follows from Q4/Q6; Reggie's class RELAYED).
- Row 10 (event listener exported with JOSS factory): applies. Events go to the client's listener (`FiddlerImpl.java:2174`); the format is the listener's exporter choice. VERIFIED (call); format UNDETERMINED.
- Row 11 (ServiceID not bound after `prepareProxy`): Fiddler derives its `ServiceID` from `proxyID` (`FiddlerImpl.java:5356-5357`) and `ProxyVerifier` compares `proxyID` (`ProxyVerifier.java:220`). That is the right binding on the Fiddler side. VERIFIED. Whether lookup-side discovery of Fiddler binds it is outside this module.
- Added path 8 (verifiers are OR'd): applies to the existing `ProxyVerifier` (RELAYED, memo line 633).
- Rows 3, 4 (fallback to JOSS, retry by download) apply to the discovery manager used by the service and by clients, not to Fiddler's proxy classes. UNDETERMINED for Fiddler's own `LookupDiscoveryManager` use.
- Row 6, 9, 12: not applicable to Fiddler (no node scheduler, no CEL, no UI roles in the module). VERIFIED by absence in the files read.

## Q7. Tests, and what a failing test would look like

Existing: only `SVC ...\src\test\java\org\apache\river\fiddler\FiddlerImplDerFormatTest.java`. VERIFIED (Glob returned one test file in `fiddler-service`; `fiddler-dl` has no test directory in the Glob).
It has 6 `@Test` methods: `site1_atomicSerializeListenerFieldRoundTrips` (`:400`), `site2_addToDiscoveredRegsMarshalsRegistrarAsDerTagged` (`:437`),
`site3_plainWriteObjectListenerFieldIsDerTaggedAndDecodes` (`:460`), `site3_plainReadObjectGracefullyDropsListenerWhenDerProviderMissing` (`:477`),
`site4_marshalAttributesProducesDerTaggedDecodableInstances` (`:523`), `site4_unmarshalAttributesGracefullyDropsOnlyUndecodableEntriesWhenDerProviderMissing` (`:540`). VERIFIED.
What it covers: the four persistence write sites and their read-side recovery (class header `:65-150`). It does not touch the wire proxies, the exporter, the lease, the registration or any
enforcement of a required format. It is therefore not evidence for code-free proxies. VERIFIED. Its read-back test needs the DirtyChai JDK (`:377-397`, comment); I did not run it.
No test names `FiddlerProxy`, `FiddlerLease` or `FiddlerRegistration` (the Glob of `services/fiddler/**/*.java` lists no other test). VERIFIED.

Tests that would fail if code-free is violated (to be written; the pattern is the three 2026-10-31 tests in the training note):
1. Exporter test: export `FiddlerImpl`'s stub with the default factory and with `AtomicDerILFactory`; a call whose arguments hold a JOSS-encoded listener must be rejected by the DER
   dispatcher (pattern `AtomicDerRequiredFormatCallTest.dispatcherRefusesJossSmartProxyArgument`, memo line 677). Break test: revert the factory to `AtomicILFactory`; the test must fail.
2. Round-trip test per class: encode a `FiddlerRegistration`/`FiddlerLease` as DER and decode it in a JVM whose classpath lacks `fiddler-dl`; decode must succeed using only resident classes. Break test: put a `fiddler-dl` class back in the payload; decode must fail.
3. `getRegistrars` test: a payload holding a smart-proxy registrar must yield data or a rejection, never an instantiated proxy.
4. Lease test: renewal with a server `long` duration, and `FiddlerLeaseMap.renewAll` with a duration above `Integer.MAX_VALUE`, to expose the `intValue()` truncation at `FiddlerLeaseMap.java:192`.

## Q8. Verdict and smallest next changes

**Verdict: CAN WITH CHANGES** for the service proxy and admin proxy (pure forwarders, VERIFIED in Q2).
**CANNOT** as tier-1 dynamic proxy for `FiddlerRegistration`, `FiddlerLease` and `getRegistrars` as the interfaces are specified today: they contain local state (`eventReg`, `expiration`) and client logic (unmarshal loop, dedup,
`LeaseMap`) that a handler cannot carry (Q2, VERIFIED). Those objects need a resident class (tier 2 DER data plus a fixed local class) or a changed contract.
The relayed payload (Reggie registrars) is a smart proxy until a data form exists (Q6).

Verified vs inferred. VERIFIED: the exporter default, the class list, the forwarding bodies, the local methods, the handler's dispatch rule, the existing test's scope. INFERRED (PLAUSIBLE): that a dynamic proxy for the outer
proxy is functionally enough (equality via `ReferentUuids.compare` at `FiddlerProxy.java:264` would become handler equality, which I did not compare against `ServiceDiscoveryManager` use); that exporting `LookupDiscoveryService` directly works with the JERI export (not built).

### Smallest next change list (ordered, smallest first; all would be human-authored in DirtyChai but JGDMS has no such rule)

1. Add a test that pins the exporter format (Q7 item 1). It fails today if anyone expects DER, because `FiddlerInit.java:247` uses `AtomicILFactory`. No production change needed to write it.
2. Switch the default exporter to `AtomicDerILFactory` (a one-line change at `FiddlerInit.java:246-251`) and require `ATOMIC_DER` from the wrapper. Risk: every current JOSS client breaks; the javadoc says "no other service code change is required" (`AtomicDerILFactory.java:30-32`), but that was not tested for Fiddler. UNDETERMINED until run.
3. Make `Fiddler` (or a new narrower interface) extend `LookupDiscoveryService` so that the inner stub already is the service proxy; then `FiddlerProxy` and `FiddlerAdminProxy` can be removed from the wire. Keep `proxyID` binding through the existing `ProxyVerifier`. Needs `ReferentUuid` equality settled (the `getReferentUuid` method cannot be forwarded, Q2).
4. Replace `FiddlerLease` by a DER record presented through a resident `AbstractLease` subclass and a resident `LeaseMap` (memo R8, line 511), and fix `FiddlerLeaseMap.java:192` while there.
5. Decide the registration contract. Either (a) a resident `LookupDiscoveryRegistration` implementation built from a DER record `{endpoint, registrationID, eventReg}`, or (b) export one `Remote` object per registration (per-registration DGC cost, memo R8 argues against this for leases).
6. Make registrars data. Return `LookupLocator` plus `ServiceID` and group names instead of `MarshalledInstance` of a Reggie proxy, and stop calling `get(false)` in the client. This changes the `LookupDiscoveryRegistration.getRegistrars` contract (`LookupUnmarshalException` semantics, `FiddlerRegistration.java:294-337`) and needs a decision.
7. Change `discard(ServiceRegistrar)` so the server receives an id or locator instead of a decoded registrar proxy (removes a server-side decode of client-supplied code).
8. Require `ATOMIC_DER` on event listeners at registration (row 10) and bound `Exception[]` elements to `@AtomicSerial` types in `FiddlerRenewResults`.

### Questions only the operator can settle

- Do target clients carry `jgdms-lib-dl` (so `AbstractLease` and `ThrowThis` count as resident)? If not, items 4 and the `Fiddler` interface need relocating.
- May the `LookupDiscoveryRegistration`/`getRegistrars` contract change (item 6), or must the legacy `ServiceRegistrar[]` result stay for existing clients (then Fiddler keeps a smart path for that contract)?
- Does the deployed configuration set `serverExporter` (`FiddlerInit.java:280-285`) in any QA or production config? I found no `.config` file in `services/fiddler`, and did not search the QA tree for configs.

Not done: no run of `FiddlerImplDerFormatTest`, no Maven build, no reading of `ServiceDiscoveryManager` equality use, no search of QA configs, no check of the `RegistrationInfo` classes beyond the lines cited.
