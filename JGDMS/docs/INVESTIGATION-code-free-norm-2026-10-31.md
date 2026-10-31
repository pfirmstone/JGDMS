Model: claude-sonnet-5-5 (Sonnet 5.5). Static reading only; nothing was built, run or changed. All paths absolute below are under
`C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\` (written `R\`). Labels: VERIFIED = line opened and read; RELAYED; PLAUSIBLE; UNDETERMINED.

# Investigation: can NORM use a code-free (dynamic) proxy?  2026-10-31

## Verdict: CAN WITH CHANGES (for the service proxy and admin proxy); the lease-set proxy needs a design decision first

- `NormProxy` and `AdminProxy` contain no logic beyond forwarding. They can be replaced by dynamic proxies once an authority problem (Q3) is fixed. VERIFIED.
- `SetProxy` (the lease renewal set) carries real client-side logic (Q2). It is the only part that blocks a plain swap. It can be made code-free, but only by
  moving that logic to the server or accepting small API-visible changes. That is the operator's call.
- The wire is not yet DER-only: the default exporter uses the JOSS factory (Q1), and the service stores and returns leases by JOSS in places (Q4, Q6).
- The service itself, as a lease renewer, must unmarshal arbitrary clients' leases and listeners. That is a server-side code-loading path unrelated to
  NORM's own proxy and stays open under any proxy tier (Q6). "Code-free proxy" does not make the NORM host code-free.

## Q1. What does the service export today?  (VERIFIED unless marked)

- One remote object is exported: `serverProxy = exporter.export(NormServerBaseImpl.this)`
  (`R\services\norm\norm-service\src\main\java\org\apache\river\norm\NormServerBaseImpl.java:2029`). It is a JERI stub implementing `NormServer`
  (`R\services\norm\norm-dl\src\main\java\org\apache\river\norm\proxy\NormServer.java:46-47`: extends `Landlord, LeaseRenewalService, Administrable, JoinAdmin, DestroyAdmin, ServiceProxyAccessor, Remote`).
- Default exporter: `BasicJeriExporter(TcpServerEndpoint.getInstance(0), new AtomicILFactory(null, null, NormServer.class.getClassLoader()))`
  (`...\norm-service\...\NormServerInitializer.java:143-150`). `AtomicILFactory` is the JOSS-atomic factory (`R\jgdms-jeri\src\main\java\net\jini\jeri\AtomicILFactory.java:67`,
  default reducing codec at `:77-78`). `AtomicDerILFactory` exists (`R\jgdms-jeri\src\main\java\net\jini\jeri\AtomicDerILFactory.java:46`) but no NORM file or config on disk names it
  (grep of norm-named files: no hit). The same `serverExporter` entry is configurable (`NormServerInitializer.java:145`, `ActivatableNormServerImpl.java:156`).
- Smart proxies built around that stub, on `start()` (`NormServerBaseImpl.java:2061-2062`):
  - `NormProxy.create(serverProxy, serverUuid)` is registered with the lookup service as the service proxy (`JoinState` at `:2088-2092`; returned by `getServiceProxy` `:1908-1911`).
  - `AdminProxy.create(serverProxy, serverUuid)` is returned by `getAdmin` (`:1608-1611`).
  - Per lease set: `SetProxy.create(serverProxy, newID, newLease)` returned by `createLeaseRenewalSet` (`:1080`, `:1111`) and `newSetProxy` (`:1124-1126`).
  - Leases: `LeaseFactory(serverProxy, serverUuid)` (`:2101`) builds `ConstrainableLandlordLease`
    (`R\jgdms-lib-dl\src\main\java\org\apache\river\landlord\LeaseFactory.java:79-83`).
  - Trust: `getProxyVerifier()` returns `new ProxyVerifier(serverProxy, serverUuid)` (`:1151-1153`).
- Concrete wire leaves in `org.apache.river.norm.proxy` (9 classes, `NormProxyMarshalDelegate.java:60-65`): `AbstractProxy, SetProxy, ConstrainableSetProxy, GetLeasesResult,
  ProxyVerifier, AdminProxy, ConstrainableAdminProxy, NormProxy, ConstrainableNormProxy`. Only `Constrainable*`, `GetLeasesResult` and `ProxyVerifier` are constructible leaves
  (`NormProxyMarshalDelegate.java:101-105`; abstract bases fail closed `:109-113`).
- Constrainability is mandatory: `create` throws unless the stub implements `RemoteMethodControl` (`NormProxy.java:56-60`, `AdminProxy.java:51-55`, `SetProxy.java:81-85`).
- Test coverage of the proxies themselves: none. Grep of every `src/test` tree in the repo for `SetProxy|NormProxy|NormServer` returned no file. VERIFIED (negative, scope: all `src/test/**/*.java`).
  The `qa` directory named in older instructions does not exist at `R\qa`.

## Q2. Does the client-visible proxy contain logic beyond forwarding?

`NormProxy` (VERIFIED): `createLeaseRenewalSet` -> `server.createLeaseRenewalSet` (`NormProxy.java:76-80`); `getAdmin` -> `server.getAdmin` (`:85-87`). Pure forwarding.
Constraint handling is `setConstraints` on the inner stub (`:120-126`).

`AdminProxy` (VERIFIED): 12 `JoinAdmin` methods and `destroy` each a one-line forward to `server` (`AdminProxy.java:71-139`). Pure forwarding.

`AbstractProxy` (VERIFIED): `equals` and `hashCode` are by `uuid` via `ReferentUuids.compare` (`AbstractProxy.java:101-108`); `getProxy` returns the stub (`:117-119`).
A bare dynamic proxy compares by its invocation handler instead. Both proxy kinds carry the same `uuid`, so identity can be kept if the uuid is a wire value, but it is a behaviour change (PLAUSIBLE that nothing depends on it; not searched).

`SetProxy` (VERIFIED) has all of the following, each of which a dynamic proxy would lose:
1. Overload folding. The client interface has 2-arg and 3-arg `renewFor`, plus `clear*Listener` methods, that map to one server method with fixed arguments:
   `renewFor(lease, dur)` -> `server2().renewFor(uuid, lease, dur, Lease.FOREVER)` (`SetProxy.java:114-124`); `clearExpirationWarningListener` -> `setExpirationWarningListener(uuid, null, NO_LISTENER, null)` (`:258-266`);
   `clearRenewalFailureListener` likewise (`:318-325`).
2. Uuid injection: every call prepends the set `uuid` (`:118, 132, 143, 157, 220, 246, 282, 306`). A dynamic proxy for the client interface cannot add it. (Resolved only if each set is its own exported object.)
3. `ThrowThis` translation to `RemoteException`: every method catches it and calls `throwRemoteException()` (e.g. `:120-122`). Server throws it at `NormServerBaseImpl.java:1051` and `:1070` (both `NoSuchObjectException`).
   `ThrowThis` exists because a checked `RemoteException` subtype could not otherwise be thrown by the server (`R\jgdms-lib-dl\src\main\java\org\apache\river\proxy\ThrowThis.java:40-47`). With a per-set exported object the server can throw `NoSuchObjectException` directly (PLAUSIBLE, not tested).
4. Client-side null checks on listeners (`:213-217, 239-243, 275-279, 299-303`).
5. `getLeases` unmarshals each returned `MarshalledInstance` on the client, collects per-element failures and throws `LeaseUnmarshalException` with the good leases, the bad `MarshalledInstance`s and the causes
   (`:154-203`; the unmarshal is `ml.get(result.verifyCodebaseIntegrity())` at `:170`). This is client-side decode logic with partial-success semantics.
6. Two inner stubs with different constraints (`methodMap1` and `methodMap2`, `:352-441`, `server2` `:457, 479, 563-566`) so the one server method gets different client constraints depending on which client overload was called.
   Constraints are derived by `ConstrainableProxyUtil.translateConstraints` (`:545-547`).
7. Note a defect in the folding: the 3-arg `renewFor` uses `server`, the 2-arg uses `server2()` (`:132` vs `:118`); only `methodMap2` entries are listed for the 2-arg variant (`:417-424`). Consistent, but fragile.
8. Trust verification: `ProxyVerifier.isTrustedObject` special-cases `ConstrainableSetProxy`, `ConstrainableAdminProxy`, `ConstrainableNormProxy` and `ConstrainableLandlordLease` (`ProxyVerifier.java:116-149`). It would need to be rewritten for dynamic proxies.

Leases (VERIFIED, library, not NORM): `LandlordLease` has local logic (`AbstractLease` expiration state, `canBatch` on `landlordUuid`, `equals` by cookie) (`R\jgdms-lib-dl\...\LandlordLease.java:150-190`). It is a library data+logic class, not NORM-specific. See ruling below.

## Q3. Which interfaces must exist on the client?

- Interfaces a client needs for NormProxy and AdminProxy: `LeaseRenewalService`, `Administrable`, `JoinAdmin`, `DestroyAdmin`, `LeaseRenewalSet`. `LeaseRenewalService`, `LeaseRenewalSet` and `Administrable` are in `jgdms-lib-dl`
  (`R\jgdms-lib-dl\src\main\java\net\jini\lease\LeaseRenewalSet.java:173`, `LeaseRenewalService.java:37`, `net\jini\admin\Administrable.java:26`). VERIFIED. `JoinAdmin` and `DestroyAdmin` I did not locate (UNDETERMINED).
- `NormServer` (private wire protocol) lives only in `norm-dl` (`NormServer.java:38-47`). It is the interface the stub implements today. A client holding the stub with a dynamic-proxy design would have to load `norm-dl` just to name it.
  So `norm-dl` is still a download for any design that keeps `NormServer` on the stub. VERIFIED that the stub type is `NormServer`; the client-jar packaging is UNDETERMINED (operator: what is on a code-free client's classpath; is `jgdms-lib-dl` there? Its pom calls it "Service DL Library", `R\jgdms-lib-dl\pom.xml:31`).
- AUTHORITY PROBLEM (VERIFIED from types): `NormServer` includes `destroy()` (via `DestroyAdmin`), the whole `JoinAdmin`, and the `Landlord` methods (`NormServer.java:46-47`). Today a client of `NormProxy` cannot reach them because `server` is package-private
  (`AbstractProxy.java:45`). If the lookup-registered proxy IS the `NormServer` stub, any holder can cast and call `destroy()`. The exporter passes `permissionClass = null` (`NormServerInitializer.java:148`), so there is no per-method server-side permission either.
  Required: export separate objects per role (service / admin / set), each with only its own interface, or supply a permission class and constraints.

## Q4. What crosses the wire?

Wire types of `NormServer` (read at `NormServer.java:66-191`) and their serial status:

| Type | Direction | Status |
|---|---|---|
| `Uuid`, `Lease` (client's own lease) | in | `Uuid` AtomicSerial (field of `AbstractProxy.serialForm`, `AbstractProxy.java:52-57`); `Lease` is any client-supplied class, UNDETERMINED per lease |
| `RemoteEventListener` | in | an exported stub of the client; any class |
| `MarshalledObject` handback (2 of 8 listener methods, `NormServer.java:117, 166`) | in | `java.rmi.MarshalledObject`, JOSS only. The DER-preserving `MarshalledInstance` overloads exist (`:145, 190`). 2 of 4 listener-setting server methods take `MarshalledObject`; both have a `MarshalledInstance` twin. |
| `MarshalledInstance` handback / `MarshalledInstance[]` in `GetLeasesResult` | in/out | `GetLeasesResult.serialForm` declares `MarshalledInstance[]` (`GetLeasesResult.java:41-45`); format of each element is chosen by whoever built it (Q6) |
| `GetLeasesResult` | out | AtomicSerial (`GetLeasesResult.java:32`) |
| `EventRegistration` | out | AtomicSerial (`R\jgdms-platform\...\EventRegistration.java:52`) |
| `Entry[]`, `LookupLocator[]`, `String[]` (JoinAdmin) | in/out | `LookupLocator` AtomicSerial (`LookupLocator.java:77`); `Entry` is any application class, UNDETERMINED |
| `ThrowThis` | out (exception) | AtomicSerial (`ThrowThis.java:54`), carries a `RemoteException` |
| `LeaseRenewalEvent` (sent to client listeners) | out | AtomicSerial (`R\jgdms-lib-dl\...\LeaseRenewalEvent.java:83`) |
| `RenewResults`, `Map` (`Landlord.renewAll`, `cancelAll`) | in/out | not read; UNDETERMINED |

- Every norm-dl proxy leaf is AtomicSerial with a `serialForm` (`NormProxyMarshalDelegate`; `AbstractProxy.java:39`, `SetProxy.java:50`, `ProxyVerifier.java:37`). VERIFIED.
- `ProxyVerifier` is itself a wire value that holds `serverProxy` (`ProxyVerifier.java:50-55`).

## Q5. Which proxies are passed as values?

- `SetProxy` is returned by `createLeaseRenewalSet` and carries a `Lease` (`SetProxy.java:56, 59`) and the set `methodConstraints` (`:459-468`). The `Lease` must be a `ConstrainableLandlordLease`, else construction fails (`:482-488`, read side `:519-522`).
  So a code-free `SetProxy` still needs the lease class, which is a library smart-ish class (Q2).
- `ConstrainableLandlordLease` is returned inside the set and from `remove` (the removed client lease, with `setSerialFormat(Lease.DURATION)` at `NormServerBaseImpl.java:582`) and from `getLeases`.
- `AdminProxy` is returned by `getAdmin`.
- Client listeners are passed in (`RemoteEventListener`); the service calls them with `LeaseRenewalEvent`.
- Each of these would also need to be code-free. The lease is the hard one: `Lease` is a local-state API (`getExpiration`, `setSerialFormat`, `canBatch`), not a call-forwarding API. UNDETERMINED whether a pure dynamic proxy can satisfy `LeaseRenewalManager` (a lease needs local expiration state). PLAUSIBLE that it cannot, which would mean leases stay a library class. This is exactly the operator's ruling below.

## Q6. Code loaded from a codebase; memo section 11 paths

Paths that load a class from an annotation in NORM (VERIFIED by reading each line unless marked):
1. `leasePreparer.prepareProxy(leaseToRenew)` (`NormServerBaseImpl.java:292`; also `:529` for remove). The incoming `Lease` arrives already unmarshalled by the call layer; its class came from the client's codebase. Default preparer `new BasicProxyPreparer()`
   (`NormServerInitializer.java:102`) which authenticates nothing (memo 11.1 item 4 applies, RELAYED). This is a server-side code-loading path caused by the service's job, not by its proxy.
2. `listenerPreparer.prepareProxy(listener)` (`:623, 684, 779, 817`), default `BasicProxyPreparer` (`NormServerInitializer.java:103`).
3. `ClientLeaseWrapper` stores each client lease as `MarshalledInstance`. It is created with `ATOMIC_DER` required (`ClientLeaseWrapper.java:207-208`), but re-created with the default format at `:340` (`new MarshalledInstance(cl)`, used by `getMarshalledClientLease` and so by `getLeases`)
   and at `:463` (`new MarshalledInstance(clientLease)`). So leases returned by `getLeases` are JOSS-format unless the default flips. The DER-specific tests for this exist:
   `R\services\norm\norm-service\src\test\java\org\apache\river\norm\ClientLeaseWrapperDerFormatTest.java` (6 tests, counted by method pattern, not run).
4. `SetProxy.getLeases` calls `ml.get(verify)` on whatever format each element declares (`SetProxy.java:170`). Memo 11.1 item 1 (sender chooses the decoder) and memo row 1 (Fix 1/2 enforce `ATOMIC_DER` only if the client set it) apply directly: with no required format set, a hostile NORM
   could return JOSS elements and the client decodes them. RELAYED from memo; I read the call site, not the enforcement path.
5. `EventType` holds the client's listener as `MarshalledInstance` (`EventType.java:78`, `:246`); tests in `...\event\EventTypeListenerDerFormatTest.java` (8 methods by pattern). Not read in depth; UNDETERMINED whether the listener decode path is DER-pinned.
6. Memo section 11 rows applying: 1 (constraint applied only on request), 2 (verifier OR), 8 (interface stripping: does not apply, `RemoteMethodControl` must be on the stub, enforced at `NormProxy.java:56-60`), 10 (event listener by JOSS factory: here the
   exporter is `AtomicILFactory`, `NormServerInitializer.java:148`, so row 10's concern is live for NORM's own server stub), 11 (ServiceID bound once: NORM derives its ServiceID from its own `serverUuid`, `NormServerBaseImpl.java:2091-2092`, so the discovery side, not NORM, is where binding happens).
   Rows 3-7, 9, 12, 13: do not apply to NORM's service side (they are about the lookup client / host).
7. `getClassAnnotation()` and codebase digests are served by the service (`NormServerBaseImpl.java:335-366`); this is the `CodebaseAccessor` path for a smart proxy. A code-free NORM would not need it for its own classes. UNDETERMINED whether removal is safe (it also serves JoinAdmin lookups).

## Q7. Tests, and what a failing-if-violated test looks like

Existing (VERIFIED by name, none run): 3 DER-format round-trip tests in `norm-service` (`ClientLeaseWrapperDerFormatTest`, `EventTypeListenerDerFormatTest`, `JoinStateAttributesDerFormatTest`) with `DerFormatTestSupport`
(`makeFormatUnresolvable` tampers with `payloadFormat`, `DerFormatTestSupport.java:64-80`). No test of the proxies or of a call through the exporter (Q1, negative). No `qa` tree on disk.

Proposed tests (each must fail with its mechanism removed):
1. Export `NormServerBaseImpl` through `AtomicDerILFactory`, call `createLeaseRenewalSet`, and assert the returned set was encoded as DER and that a JOSS-format carrier is refused when `ATOMIC_DER` is required (pattern of `AtomicDerRequiredFormatCallTest`, memo 11.2). Fails if the factory reverts to `AtomicILFactory`.
2. A `getLeases` response whose elements are JOSS `MarshalledInstance` must be rejected on a DER-pinned client (the call `SetProxy.java:170`). Fails if the format requirement is not in the context.
3. A client holding only the service proxy must not be able to call `destroy`, `addLookupGroups` or `renew(cookie,...)`. Fails if the registered proxy exposes the full `NormServer` stub.
4. Equivalence test: `renewFor(lease, d)` and `clearRenewalFailureListener()` on a dynamic/set proxy produce the same server-side calls as today (fold semantics, `SetProxy.java:114-124, 258-266, 318-325`).

## Q8. Smallest next changes, in order (nothing done)

1. Operator ruling needed first (cannot be settled from code): is `LandlordLease` (library class in `jgdms-lib-dl`, AtomicSerial data, local expiration logic) acceptable as "code-free" because it is already on every client? If no, the lease tier is CANNOT and NORM's output stays non-code-free through leases. 
2. Make the default exporter DER-only: `AtomicDerILFactory` instead of `AtomicILFactory` at `NormServerInitializer.java:146-149` (and the `ActivatableNormServerImpl.java:156` default, not read). Smallest change; closes Q1 and row 10.
3. Make `getLeases` and `ClientLeaseWrapper` emit `ATOMIC_DER`: `ClientLeaseWrapper.java:340` and `:463` use the default format.
4. Replace `NormProxy` and `AdminProxy` by separate exported objects (service export with `LeaseRenewalService + Administrable` only; admin export with `JoinAdmin + DestroyAdmin`), each with its own dynamic stub and a server-side permission class or constraints. Remove `normProxy`/`adminProxy` smart proxies (`NormServerBaseImpl.java:2061-2062`) and the `ProxyVerifier` branches that special-case them.
5. For `SetProxy`: export one remote object per lease set implementing `LeaseRenewalSet` directly (uuid becomes server state), move the overload folding and `ThrowThis` to the server, and replace `getLeases`' client-side `MarshalledInstance` unmarshal with a server-side partial-result type (or return `Lease[]` in a DER-only response). This is the largest change; it alters `LeaseUnmarshalException` semantics. Operator decision.
6. Add the four tests in Q7 first, with the load-bearing demonstration (break, fail, restore, `cmp`).

## What was not settled
- Operator: what the code-free client's classpath contains (is `jgdms-lib-dl` present?), and whether `LandlordLease` counts as code-free.
- `JoinAdmin`, `DestroyAdmin`, `Landlord.RenewResults` location and serial status were not read.
- The `ActivatableNormServerImpl` default exporter and any `.config` files of NORM (no `serverExporter` hit in norm-named files outside the two Java files) were not examined.
- Nothing here was run.
