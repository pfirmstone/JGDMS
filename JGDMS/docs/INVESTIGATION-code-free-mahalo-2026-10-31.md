# Investigation: can Mahalo (transaction manager) use a code-free proxy?

Model: claude-sonnet-5-5 (Sonnet 5.5). Date 2026-10-31. Static reading only; nothing was built or run. No production file, test or other file was changed.
Labels: VERIFIED = I read the line. RELAYED = taken from the memo. PLAUSIBLE = inferred. UNDETERMINED = not settled.
Method in `TRAINING-code-free-proxy-investigation-2026-10-31.md`; fail-open rows refer to memo sections 11 and 11.1 of `DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md`.
Paths below are relative to `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\services\mahalo\` unless they start with `jgdms-`, which are relative to `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\`.
`M-svc` = `mahalo-service\src\main\java\org\apache\river\mahalo\`, `M-dl` = `mahalo-dl\src\main\java\org\apache\river\mahalo\proxy\`.

## Verdict: CAN WITH CHANGES

The Mahalo smart proxies are pure forwarders over a JERI dynamic proxy that Mahalo already exports. The one genuine local-logic class a client holds is the lease, and it is a shared library class, not Mahalo code.
What stops code-free today is not logic but three other things: (1) the stub implements an interface set that lives partly in the Mahalo `-dl` jar, (2) Mahalo exports with JOSS and never sets `ATOMIC_DER`, (3) the host unmarshals client-supplied participant stubs by JOSS with a default (non-verifying) preparer.
Two items need the operator (end of report).

## 1. What does the service export today?

- One JERI stub: `serverStub = (TxnManager) exporter.export(TxnManagerImpl.this)` (`M-svc\TxnManagerImpl.java:493`). VERIFIED.
- Default exporter: `new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new AtomicILFactory(null, null, TxnManager.class, true), false, true)` (`M-svc\TxnManagerImplInitializer.java:139-150`; the activatable branch at `:124-134` is the same factory inside an `ActivationExporter`). `AtomicILFactory` is the JOSS-atomic factory; the last argument `true` is `useAnnotations`, so codebase annotations are written. VERIFIED (constructor signature at `jgdms-jeri\src\main\java\net\jini\jeri\AtomicILFactory.java:393-401`).
- The shipped demo config says so in words: "AtomicILFactory (JOSS is fine for Mahalo)" (`examples\wire-protocol-showcase\demo7-filter-pushdown\configs\mahalo.config:22-24`, exporter at `:42-47`). VERIFIED.
- Three smart-proxy objects are built around that stub at start-up (`TxnManagerImpl.java:498-512`):
  - `TxnMgrProxy.create(serverStub, topUuid)` (`:498-499`), the object registered in lookup (`joinStateManager.startManager(config, txnMgrProxy, ...)` at `:555`), returned by `getServiceProxy()` (`:1856`) and `manager()` (`:1368`).
  - `TxnMgrAdminProxy.create(serverStub, topUuid)` (`:505-506`), returned by `getAdmin()` (`:1723`).
  - `LeaseFactory(serverStub, topUuid)` (`:512`), whose `newLease` returns a `ConstrainableLandlordLease` when the landlord is a `RemoteMethodControl` (`jgdms-lib-dl\...\landlord\LeaseFactory.java:79-83`). `create` hands one to the client inside `TransactionManager.Created` (`TxnManagerImpl.java:609-611`, `:655-656`). VERIFIED.
- Both proxies are abstract; the only concrete wire forms are `Constrainable*` `@Stateless` subclasses (`M-dl\TxnMgrProxy.java:100-107, 229-232`; `M-dl\TxnMgrAdminProxy.java:90-97, 227-230`). A non-constrainable server is refused (`TxnMgrProxy.java:101-105`). VERIFIED.
- Trust: `getProxyVerifier()` returns `new ProxyVerifier(serverStub, topUuid)` (`TxnManagerImpl.java:2029`). `ProxyVerifier` handles `ConstrainableTxnMgrProxy`, `ConstrainableLandlordLease`, `ConstrainableTxnMgrAdminProxy` and a bare constrainable `TxnManager` (`M-dl\ProxyVerifier.java:130-151`). VERIFIED.
- Serialization wiring is a `MarshalDelegate` listing the five `-dl` classes (`M-dl\MahaloProxyMarshalDelegate.java:50-54`). VERIFIED.

## 2. Logic in the client-visible proxy beyond forwarding

`TxnMgrProxy` (`M-dl\TxnMgrProxy.java`):
- `create`, `join`, `getState`, `commit`, `abort` (both overloads) and `getAdmin` each call `backend.<same method>(...)` and nothing else (`:137-194`). VERIFIED. No cache, no retry, no local state.
- The only logic is identity and constraints: `hashCode` = `proxyID.hashCode()` (`:211-213`); `equals` = `ReferentUuids.compare` (`:220-222`); `getReferentUuid` (`:207-209`); `getProxy` (`:225-227`); `setConstraints`/`getConstraints` delegate to the backend (`:276-286`); the constructor refuses a backend that is not `RemoteMethodControl` (`:253-259`). VERIFIED.

`TxnMgrAdminProxy` (`M-dl\TxnMgrAdminProxy.java:130-196`): every `JoinAdmin` and `destroy` method forwards to `server.<same>`; identity as above. VERIFIED.

`ConstrainableLandlordLease`/`LandlordLease` (`jgdms-lib-dl\...\landlord\LandlordLease.java`): this is real client-side logic.
- `doRenew` and `cancel` forward to the landlord (`:153-168`), but the lease also holds mutable expiration (`setExpiration` `:211-213`), batching rule `canBatch` by `landlordUuid` (`:187-193`), `createLeaseMap` (`:216-218`) and cookie-based `equals`/`hashCode` (`:176-184, :221-223`). VERIFIED.
- It lives in `jgdms-lib-dl`, is shared with other landlord services, and is not in `mahalo-dl`. Whether a shared library class counts as "code-free" is a policy call, not a code fact (see Q8).

Where Mahalo itself does anything with the proxies: nothing beyond handing them out (`TxnManagerImpl.java:1365-1368, :1712-1723, :1844-1856`). VERIFIED.

Client use of the Mahalo-specific classes: a repo search for `TxnMgrProxy`, `TxnMgrAdminProxy` and `mahalo.proxy` found no reference outside `services\mahalo` (the only other hits were `.vs\CopilotSnapshots` binary snapshots, which I did not open). So nothing in this repo casts a lookup result to `TxnMgrProxy`. VERIFIED for this repo; external clients UNDETERMINED.

## 3. Interfaces the client must have, and where they are

`TxnManager` (`M-dl\TxnManager.java:39-40`) extends `Remote, Landlord, DestroyAdmin, Administrable, JoinAdmin, ServiceProxyAccessor, TransactionManager`. VERIFIED. Locations (found by file search):

| Interface | Module |
|---|---|
| `TransactionManager`, `ReferentUuid`, `ProxyAccessor`, `TransactionParticipant`, `ServerTransaction`, `LeaseMap` | `jgdms-platform` |
| `TxnManager` | `mahalo-dl` (the Mahalo download jar) |
| `Landlord`, `DestroyAdmin`, `JoinAdmin`, `Administrable`, `ServiceProxyAccessor`, `AbstractLease`, `LandlordLease` | `jgdms-lib-dl` |

- `TxnManagerImpl` implements `TxnManager` (`TxnManagerImpl.java:133`), so the stub is a `TxnManager`. VERIFIED. That the JERI proxy implements every `Remote`-extending interface of the impl is PLAUSIBLE (I did not read the exporter's interface selection).
- Therefore a dynamic proxy needs `TxnManager` (from `mahalo-dl`) and `jgdms-lib-dl` on the client. The `-dl` jar is today the codebase; if it must stay a download, the proxy is not code-free in the sense of "no downloaded class". Whether every client already has both is UNDETERMINED (operator).
- `ServerTransaction` is a platform class that forwards to `mgr` (`jgdms-platform\...\ServerTransaction.java:119-151`), with `hashCode`/`equals` that use `mgr.hashCode()`/`mgr.equals` (`:97-115`). So if `mgr` becomes the bare stub, the transaction's identity follows the stub's equality (see Q5). VERIFIED.

## 4. Arguments, returns and exceptions across the wire

| Type | Where | AtomicSerial? | Label |
|---|---|---|---|
| `TransactionManager.Created` (id + `Lease`) | return of `create` | yes, `jgdms-platform\...\TransactionManager.java:46-60` | VERIFIED |
| `TransactionManager.TransactionConfig` | argument of `create(long, config)` | yes, `:344` | VERIFIED |
| `LandlordLease`/`ConstrainableLandlordLease` | inside `Created` | yes, `LandlordLease.java:47` | VERIFIED |
| `Landlord.RenewResults` | return of `renewAll` | yes, `Landlord.java:129` | VERIFIED |
| `Map` | return of `cancelAll` (`Landlord.java:120`, raw `Map`) | content type not read | UNDETERMINED |
| `Uuid`, `long`, `int`, `String[]` | `renew`, `cancel`, `getState`, lookup groups | `Uuid` not read | UNDETERMINED |
| `LookupLocator[]` | `JoinAdmin` (admin only) | `LookupLocator` carries `@AtomicSerial` (1 occurrence, `jgdms-platform\...\LookupLocator.java`) | PLAUSIBLE |
| `Entry[]` | `JoinAdmin.getLookupAttributes` etc. (admin only) | user-defined classes; unbounded | UNDETERMINED |
| `TransactionParticipant` (stub) | argument of `join` | client-defined; may be any stub | see Q6 |
| Exceptions (`UnknownTransactionException`, `CannotJoinException`, `CrashCountException`, `LeaseDeniedException`, ...) | thrown by the interface | not read | UNDETERMINED. `jgdms-jeri` has `AtomicDerFaultRoundTripTest`, which suggests DER fault encoding exists (not read). |

Count by noun, from the table: of 6 rows with Mahalo/lease types that I could check, 4 types are `@AtomicSerial` (`Created`, `TransactionConfig`, `LandlordLease`, `RenewResults`); 0 are shown to be Serializable-only. The unchecked ones are the `Map`, `Uuid`, `Entry[]` and the exception set.
`jgdms-lib-dl` has a generated `GeneratedMarshalDelegate` for the landlord package (`jgdms-lib-dl\target\generated-sources\annotations\org\apache\river\landlord\GeneratedMarshalDelegate.java`, existence only; contents not read), so the lease is probably DER-encodable. PLAUSIBLE.

## 5. Proxies passed as values (each must itself be code-free)

1. The lease. `LandlordLease.landlord` is the stub (`LandlordLease.java:62, :130-132`), so a lease carries the stub. If the stub is a dynamic proxy, the lease is data plus a library class. VERIFIED.
2. The admin object returned by `getAdmin()`. Today a `TxnMgrAdminProxy` (`TxnManagerImpl.java:1723`). The stub already implements `JoinAdmin` and `DestroyAdmin` (via `TxnManager`), so it could be returned directly. Loss: `ReferentUuid` equality and `ProxyAccessor`. PLAUSIBLE.
3. The manager passed to participants. Mahalo calls `par.prepare(tr.mgr, tr.id)` (`PrepareJob.java:201`), `prepareAndCommit` (`PrepareAndCommitJob.java:217`), `commit` (`CommitJob.java:183`), `abort` (`AbortJob.java:213`). `tr.mgr` is the `TxnMgrProxy`, because the transaction is built with `txnMgrProxy` (`TxnManagerImpl.java:602-604`). Participants therefore unmarshal a `TxnMgrProxy` today. VERIFIED. Passing the stub instead means participants need `TxnManager` on their classpath.
4. Inbound: the participant stub supplied by the client in `join` (`TxnManagerImpl.java:696-725`). It is a client proxy, so it is the largest code-free question; see Q6.
5. `ServiceProxyAccessor.getServiceProxy()` returns `txnMgrProxy` (`:1844-1857`) and `ProxyAccessor.getProxy()` returns `serverStub` (`:1831-1840`). Both would return the stub. VERIFIED.

Equality: JERI `BasicInvocationHandler` answers `hashCode`/`equals`/`toString` locally (`jgdms-jeri\...\BasicInvocationHandler.java:776-788`), equality being handler equality (endpoint based, `:779-785`). Today `equals` is by `proxyID` (`TxnMgrProxy.java:220-222`). VERIFIED that the rule changes; whether any client relies on the old rule is UNDETERMINED.

## 6. Paths that load a class from a codebase annotation, and which memo section 11 rows apply

Server side:
- Exporter writes annotations (`useAnnotations=true`, `TxnManagerImplInitializer.java:146`). `Codebase_Annotation` is read from config (`:169-170`) and used by `getClassAnnotation()` (`TxnManagerImpl.java:1440-1443`). A whitespace annotation produces an empty codebase (`mahalo.config:38-40`, comment). VERIFIED.
- Inbound participant: `participantPreparer.prepareProxy(part)` (`TxnManagerImpl.java:711-712`); the default preparer is `new BasicProxyPreparer()` (`TxnManagerImplInitializer.java:159`). Unmarshalling of `part` happens in the dispatcher before this line, by whichever format the caller chose. A default `BasicProxyPreparer` does not verify trust (RELAYED from memo 11.1 item 4). VERIFIED that the default is `BasicProxyPreparer`; PLAUSIBLE that the host would load the participant's classes by JOSS.
- Stored participants: `StorableObject` writes DER (`StorableObject.java:105-110`, constraint `ATOMIC_DER`) but still reads legacy JOSS `MarshalledObject`/`MarshalledInstance` (`:63-72, :83-86`). `ParticipantHandle` calls `recoveredListenerPreparer.prepareProxy(storedpart.get())` (`ParticipantHandle.java:176`). Recovery is therefore a code-loading path if a JOSS record is on disk. VERIFIED (lines), applicability PLAUSIBLE.
- Lookup attributes: `JoinStateManager` writes each entry as a `MarshalledInstance` (`JoinStateManager.java:740`) and reads with `mi.get(false)` (`:770`); `false` is the verify flag. PLAUSIBLE that `false` skips integrity verification (not traced).

Client side (what a code-free Mahalo client does with the registered object):
- Memo row 1 (hostile registrar chooses JOSS): applies, because Mahalo exports JOSS and nothing sets `ATOMIC_DER` (Q2 above; memo 11.2 says the requirement exists only when the client asks). Also memo 11.1 added item 1 (`Resolve` admission, unchanged).
- Row 2 (trust verifier rejects DER stub): Mahalo's own `ProxyVerifier` is for the smart classes (`ProxyVerifier.java:130-151`). A bare dynamic proxy is verified by `BasicJeriTrustVerifier` (`jgdms-jeri\...\BasicJeriTrustVerifier.java:96-104`: proxy class loader must be covered, then handler endpoint must be trusted). VERIFIED. Row 8 (OR'd verifiers, `Security.java:1765-1771`) is RELAYED and not re-read.
- Row 5 (smart proxy in a lookup result): applies to Mahalo today, since `TxnMgrProxy` is what is registered (`TxnManagerImpl.java:555`). Closed by registering the stub.
- Row 11 (ServiceID bound after prepare): Mahalo derives the registered `ServiceID` from `topUuid` (`:556-557`), so the pair is consistent at the source; client-side binding is not Mahalo-specific.
- Rows 3, 4, 6, 7, 9, 10, 12, 13: not specific to Mahalo; not examined.

## 7. Tests, and what a failing-when-violated test looks like

Existing, in `mahalo-service\src\test\java\org\apache\river\mahalo\`:
- `GranolaOptimizationsTest.java` (25 matches for Mahalo/proxy terms) and `AtomicMarshalledInstanceDerMigrationTest.java` (storage DER round trip and the legacy-JOSS reader, lines 99-195). Neither tests the shape of the client-visible object. `mahalo-dl` has no test directory (file listing). VERIFIED.

A test that fails when code-free is violated (to be written by the operator or a later brief; not written here):
1. Export a `TransientMahaloImpl` with `AtomicDerILFactory`, take the registered service object, and assert `Proxy.isProxyClass(obj.getClass())` (fails today: it is `ConstrainableTxnMgrProxy`).
2. Walk the encoded graph of `create(...)`'s `Created` and the admin object; assert every class resolves from the application loader, none from a codebase loader, and none is under `org.apache.river.mahalo.proxy`.
3. Call with `ATOMIC_DER` required and a JOSS-encoded participant argument; expect rejection (pattern of `AtomicDerRequiredFormatCallTest`, memo 11.2). Break test: remove the exporter change; the test must fail.
4. Control: with nothing required, the same bytes decode (guard against a fake that fails the wrong way, training section 4).

## 8. Verdict and smallest next changes

CAN WITH CHANGES. Separate lists:

Verified blockers: (a) the registered/returned objects are `TxnMgrProxy`/`TxnMgrAdminProxy`, not the stub (`TxnManagerImpl.java:498-506, :555, :1368, :1723, :1856`); (b) the exporter is JOSS with annotations (`TxnManagerImplInitializer.java:139-150`); (c) the stub's interface set includes `TxnManager` from `mahalo-dl`.

Inferred: the lease is acceptable as a local library class; the admin could be the stub; participants can take the stub.

Smallest next change, in order:
1. In `TxnManagerImpl`, return `serverStub` where `txnMgrProxy` is returned or registered (`:555, :1368, :1856`) and where the transaction is built (`:602-604`), leaving `TxnMgrProxy` in place for legacy clients. Behaviour change to flag: equality moves from `proxyID` to endpoint (Q5).
2. Return `serverStub` from `getAdmin()` (`:1723`) instead of `TxnMgrAdminProxy`. Behaviour change: loses `ReferentUuid` equality.
3. Change the default exporter in `TxnManagerImplInitializer` (`:139-150`) from `AtomicILFactory` to `AtomicDerILFactory`, drop `useAnnotations`, and require `ATOMIC_DER` in the dispatcher. Behaviour change to flag: a JOSS-only participant can no longer join (compare memo 11.2 item 3). The Outrigger demo already runs `AtomicDerILFactory` (`Server.java:161`, comment text only; the factory use was not traced).
4. Decide the interface question: either ship `TxnManager` plus the `jgdms-lib-dl` interfaces as a client library jar, or export through a narrower remote interface made only of platform types. This needs the operator (below).
5. Replace the default `participantPreparer` (`TxnManagerImplInitializer.java:159`) with a verifying preparer for the code-free mode; stop `mi.get(false)` (`JoinStateManager.java:770`) loading code.
6. Add the four tests in Q7, each shown to fail with its mechanism removed.

Not done / not proven: no build and no test were run; the exact interface set of the JERI proxy, `Uuid`/`Map`/exception encodability, and the DER behaviour of `LandlordLease` are not read.

## Questions only the operator can settle

1. Do the intended Mahalo clients have `jgdms-lib-dl` and `mahalo-dl` (for `TxnManager`) on their own classpath, or is the `-dl` jar a download for them? If a download, the proxy is not code-free until item 4 is done.
2. Is a shared `jgdms-lib-dl` class (`LandlordLease`) acceptable as "local library", or must the lease also become a dynamic proxy? Look at: the intended code-free tier definition in the memo (section 1/3) against `LandlordLease.java:176-218`.
3. May JOSS-only participants be refused at `join` (change 3)?
