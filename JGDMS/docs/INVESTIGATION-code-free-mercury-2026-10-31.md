# Investigation: can MERCURY (event mailbox) use a code-free proxy?

Model: claude-sonnet-5-5 (Sonnet 5.5). 2026-10-31. Static reading only. Nothing was run, no production file was changed.
Labels: VERIFIED = I read the cited line; RELAYED = from the memo or another report, not re-read; PLAUSIBLE; UNDETERMINED.
Short names: DL = `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\services\mercury\mercury-dl\src\main\java\org\apache\river\mercury\proxy\`;
SVC = `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\services\mercury\mercury-service\src\main\java\org\apache\river\mercury\`;
LIBDL = `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\jgdms-lib-dl\src\main\java\`.

## Verdict: CAN WITH CHANGES

The lookup-registered proxy is already a thin forwarder around a JERI dynamic proxy, so the top tier is nearly there. The blockers are
the return values: `Registration`, `ListenerProxy`, the lease and the event iterator are themselves downloaded classes with
client-side logic, and the events cross the wire as JOSS `MarshalledInstance`s that load classes from a codebase on the client.

## 1. What does the service export today? (VERIFIED)

- Server stub: `exporter.export(MailboxImpl.this)` cast to `MailboxBackEnd` (SVC `MailboxImpl.java:908`). Default exporter is a `BasicJeriExporter`
  with `new AtomicILFactory(null, null, MailboxBackEnd.class.getClassLoader())` (SVC `MailboxImplInit.java:150-160`, activatable variant `:132-142`).
  So the stub is a JERI dynamic proxy; its wire format depends on the ILFactory (AtomicILFactory, not `AtomicDerILFactory`; which codec it picks by default: UNDETERMINED, not read).
- Lookup proxy: `MailboxProxy.create(serverStub, serviceID)` (SVC `MailboxImpl.java:915-916`) returns `ConstrainableMailboxProxy` (DL `MailboxProxy.java:104`).
  Returned by `getServiceProxy()` (SVC `:466-476`).
- Admin: `MailboxAdminProxy.create(serverStub, serviceID)` (SVC `:923-924`), returned by `getAdmin()` (SVC `:490`).
- Per registration: `Registration.create(uuid, serverStub, l)` (SVC `:1866`) -> `ConstrainableRegistration` (DL `Registration.java:108`), which builds a
  `ListenerProxy` (DL `Registration.java:308`; `ListenerProxy.java:104`). The lease `l` is a `ConstrainableLandlordLease` from `LeaseFactory` (SVC `:931`;
  class check at DL `Registration.java:345`).
- Trust: `getProxyVerifier()` returns `new ProxyVerifier(serverStub, serviceID)` (SVC `:4969`). `ProxyVerifier` is a downloaded verifier class (DL `ProxyVerifier.java:41`).
- Wire codec dispatch for all of the above: `MercuryProxyMarshalDelegate` (DL `MercuryProxyMarshalDelegate.java:52-58`, `:100-119`).
  Four abstract bases are rejected as wire leaves (`:113-118`), which is a fail-closed downgrade guard.

## 2. Client-side logic beyond forwarding (VERIFIED unless noted)

`MailboxProxy` (DL `MailboxProxy.java`)
- `register`/`pullRegister` check `duration < 1 && != Lease.ANY` locally and then forward (`:136-155`). Pure argument validation; the server also validates (not read).
- `getAdmin` forwards (`:158-160`). `equals`/`hashCode` use `proxyID` / `ReferentUuids.compare` (`:178-188`). `getProxy()` exposes the stub (`:191-193`).
- `setConstraints`/`getConstraints` delegate to the stub (`:241-251`).

`ConstrainableRegistration` (DL `Registration.java`)
- Local logic that is NOT a forward: `getRemoteEvents()` builds a local `RemoteEventIteratorImpl` from server data (`:197-209`); `enableDelivery` refuses a
  `ListenerProxy` target equal to its own listener (`:174-177`); `getRemoteEvents(long,long)` ignores both arguments (`:373-380`, marked TODO).
- A 10-entry method map between client and backend methods (`:259-283`) used for constraint translation (`:336-337`, `:392-393`).
- Deserialisation invariants: lease must be `ConstrainableLandlordLease`, listener must be `ConstrainableListenerProxy`, listener ID must equal registration ID (`:345-368`).

`ListenerProxy` (DL `ListenerProxy.java`): `notify` forwards to `server.notify(registrationID, event)` (`:126-135`); a 2-entry method map (`:176-181`).

`RemoteEventIteratorImpl` (DL `RemoteEventIteratorImpl.java`): real client state: local iterator over the first batch, `lastEventCookie`, an `invalidated` flag,
a refill call `mailbox.getNextBatch(...)` (`:99-109`), and a loop that skips an event whose class cannot be loaded (`:138-140`). `timeout` is unimplemented (`:84`).

`RemoteEventData` (DL `RemoteEventData.java`): holds the event as a `MarshalledInstance` and unmarshals it on the client (`getRemoteEvent`, `:108-120`, `mi.get(integrity)`).

`ProxyVerifier` (DL `ProxyVerifier.java:138-198`): verifies by calling `serverProxy.setConstraints(mc)` and `checkTrustEquivalence`. It is a `TrustVerifier`,
so it needs the interfaces and `TrustEquivalence` on the client; it runs client-side logic that a dynamic proxy would have to replace with a fixed local verifier.

## 3. Which interfaces must exist on the client? (VERIFIED)

- `MailboxBackEnd` (DL `MailboxBackEnd.java:51`) lives in mercury-dl only. It extends `Landlord`, `Administrable`, `MailboxAdmin`, `PullEventMailbox`, `ServiceProxyAccessor`.
  Its own methods `enableDelivery`, `disableDelivery`, `getRemoteEvents(Uuid)`, `getNextBatch`, `addUnknownEvents`, `notify(Uuid, RemoteEvent)` (`:65-123`).
  A client-side dynamic proxy therefore needs mercury-dl or a copy of this interface in a library jar. This is a packaging change, not a code change.
- `MailboxAdmin` (DL `MailboxAdmin.java:32`) is in mercury-dl; it extends `DestroyAdmin` and `JoinAdmin`.
- In the library jar `jgdms-lib-dl` already (VERIFIED by Glob): `PullEventMailbox`, `MailboxRegistration`, `MailboxPullRegistration`, `EventMailbox` (`net/jini/event/`),
  `Landlord`, `LandlordLease`, `ConstrainableLandlordLease`, `LandlordProxyVerifier`, `LeaseFactory` (`org/apache/river/landlord/`), `ThrowThis`, `MarshalledWrapper`,
  `ConstrainableProxyUtil`, `AbstractLease`, `JoinAdmin`, `DestroyAdmin`, `ServiceProxyAccessor`.
- Whether a given client has `jgdms-lib-dl` on its classpath: UNDETERMINED (deployment fact). The mercury-dl pom depends on `jgdms-lib-dl` (mercury-dl `pom.xml:33-37`).

## 4. Arguments, return values, exceptions across the wire

Call parameters: `long`, `Uuid`, `Collection`, `Object` cookie, `RemoteEventListener`, `RemoteEvent`, `Entry[]`, `String[]`, `LookupLocator[]` (DL `MailboxBackEnd.java`, `MailboxAdminProxy.java:123-189`).

| Type | `@AtomicSerial`? | Evidence |
|---|---|---|
| `ConstrainableMailboxProxy`, `ConstrainableMailboxAdminProxy`, `ConstrainableRegistration`, `ConstrainableListenerProxy`, `ProxyVerifier` | yes | VERIFIED (class annotations and `serialForm` in DL files; delegate `:52-58`) |
| `RemoteEventIteratorData`, `RemoteEventData`, `RemoteEventDataCursor` | yes | VERIFIED for the first two (DL `RemoteEventIteratorData.java:36`, `RemoteEventData.java:34`); the Cursor is listed in the delegate (`:57`), file not read |
| `ConstrainableLandlordLease`, `LandlordLease` | yes | VERIFIED (LIBDL `landlord/LandlordLease.java:47`, `ConstrainableLandlordLease.java:43`) |
| `Uuid`, `MethodConstraints`, `Lease` | not read | UNDETERMINED |
| `RemoteEvent` and its subclasses, `Entry` implementations, `RemoteEventListener` (client-exported) | UNDETERMINED: they are application types, not Mercury's |
| `ThrowThis`, `InternalMailboxException`, `InvalidIteratorException`, `UnknownEventException`, `LeaseDeniedException`, `UnknownLeaseException` | UNDETERMINED, not read |

Cookie is `Object` (`RemoteEventData.java:46`, `MailboxBackEnd.java:95-97`); on the server it is cast to `RemoteEventDataCursor` (SVC `PersistentEventLog.java:634`), so
an `Object`-typed slot is fed one concrete class. Admission of other classes in that slot: UNDETERMINED.

## 5. Proxies passed as values (VERIFIED)

Every one of these must also be code-free:
1. `MailboxRegistration`/`MailboxPullRegistration` -> `ConstrainableRegistration` (returned by `register`/`pullRegister`, SVC `:1121-1137`, `:1866`).
2. Its `ListenerProxy`, which a client hands to event sources as their notification target (DL `Registration.java:165-167`).
3. The registration `Lease` -> `ConstrainableLandlordLease` (DL `Registration.java:345`).
4. `RemoteEventIterator` -> `RemoteEventIteratorImpl`, package-private, NOT serialisable, built on the client (DL `Registration.java:200-208`, `RemoteEventIteratorImpl.java:41`).
5. The admin object -> `ConstrainableMailboxAdminProxy` (SVC `:490`).
6. The `ProxyVerifier` itself (SVC `:4969`).
7. The other direction: a client-supplied `RemoteEventListener` is sent to the service in `enableDelivery` and prepared there with `listenerPreparer.prepareProxy(target)` (SVC `:1157-1159`).
   That is server-side loading of client-supplied code, relevant to the mailbox's own host, not to the client's code-free property.

Counts: 5 wire-visible `@AtomicSerial` proxy leaves plus 1 verifier; 1 client-only class (`RemoteEventIteratorImpl`).

## 6. Class loading from a codebase annotation; memo section 11 paths

- The event itself: the server wraps each outgoing event with `new MarshalledInstance(re)` (DL `RemoteEventData.java:93`), and that constructor chain uses
  `new MarshalFactoryInstance()` (jgdms-platform `MarshalledInstance.java:393-396`). That is the default (JOSS) factory: PLAUSIBLE (the factory class was not read).
  The client then calls `mi.get(integrity)` (DL `RemoteEventData.java:114`). Events are arbitrary application subclasses of `RemoteEvent`, so this path loads
  classes by codebase annotation on the client. This is the main reason the mailbox cannot be fully code-free as written: the payload is application-defined.
- The inner storage format is already DER: `EventWriter` writes with `MarshallingFormat.ATOMIC_DER` (SVC `EventWriter.java:167-168`); `EventReader` reads with `mi.get(false)`
  (SVC `EventReader.java:161`). So the log is DER but the wire re-wrap in `RemoteEventData.convert` is not (VERIFIED two sides, the re-wrap default is PLAUSIBLE as above).
- Silent skip of unloadable events: `RemoteEventIteratorImpl.java:138-140` drops an event on `ClassNotFoundException`. For a code-free host this is the safe direction (drop, do not load), but it hides data loss.
- Memo section 11 rows that apply (memo line numbers): row 10 (`:600`) and path 5 (`:630`), the listener exported with the JOSS factory: the mailbox's `ListenerProxy` is
  exported through the same stub, so it follows the `AtomicILFactory` default above; whether that defaults to DER is UNDETERMINED. Row 11 / path 3 (`:601`, `:628`), ServiceID impersonation:
  `MailboxProxy.proxyID` is a self-asserted `Uuid` in the wire form (DL `MailboxProxy.java:114`, `:173-175`); `ProxyVerifier` checks it against the verifier's own ID only when verification runs
  (DL `ProxyVerifier.java:184-187`). Path 1 (`:624`, `Resolve` classes admitted in any slot): applies to every slot above; UNCHANGED per memo, not re-traced here (RELAYED).
  Row 2 / path 8 (`:592`, `:633`), verifiers OR'd: Mercury ships its own `ProxyVerifier` (RELAYED that a DER-only host would need a fixed local one instead).
- Paths 4, 6, 12 and 13 (null filter, isolation, UI factory, unbounded array): not applicable to Mercury's source; no filter/UI code exists in mercury-dl (VERIFIED by file list).

## 7. Tests (VERIFIED by Glob and reading)

Existing, in `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\services\mercury\mercury-service\src\test\java\org\apache\river\mercury\`: `EventIDDerFormatTest`, `EventWriterDerFormatTest`,
`MailboxImplDerFormatTest`, `ServiceRegistrationDerFormatTest`, `DerFixtures`. These cover server-side persistence (log records, attributes). The header of `MailboxImplDerFormatTest.java:35-78`
names exactly two write sites. None tests the client-visible proxies. A search of the whole repository for Mercury/Mailbox in test paths found no other test (only a comment at
`GeneratedMarshalDelegateTest.java:39` referring to a `mercury/norm` qa run; that run is not on disk, NOT ON DISK).

Tests that would fail when code-free is violated (to be written; none exist):
1. Decode a `ConstrainableRegistration` / `ConstrainableMailboxProxy` through a DER call path under a required `ATOMIC_DER` and assert a JOSS-encoded variant is rejected (pattern: `MarshalledInstanceConstraintTest`, memo 11.1).
2. A client JVM test with no mercury-dl on the classpath that receives a `Registration` and must either use a local equivalent or fail; it fails if any `org.apache.river.mercury.*` class is loaded.
3. `RemoteEventData` with a DER inner payload vs a JOSS inner payload under a DER requirement.
Each must be shown failing with the enforcement removed.

## 8. Findings by tier

| Piece | Tier it needs today | Can it move down? |
|---|---|---|
| `MailboxProxy` | thin wrapper over the stub | yes, to a plain dynamic proxy of `PullEventMailbox`+`Administrable`+`ReferentUuid`; the `Uuid` identity (`ReferentUuid`, `equals`) is the part that needs a local rule |
| `MailboxAdminProxy` | thin wrapper | yes, same |
| `ListenerProxy` | thin wrapper | yes, but it is also exported as a target by event sources, so the remote caller needs `MailboxBackEnd` locally or a dedicated small listener interface |
| `Registration` | local logic (iterator, resubmission guard) | CAN WITH CHANGES: the iterator construction must move to a fixed local class or to a server call |
| `RemoteEventIteratorImpl` | client state | needs a fixed local class (it is not a downloaded proxy by itself, but lives in mercury-dl) |
| Lease | `LandlordLease` in `jgdms-lib-dl` | already data-like and in a library jar (UNDETERMINED whether clients have it) |
| Event payload | application class by codebase | CANNOT be code-free unless events are restricted to DER data; the application defines `RemoteEvent` subclasses |
| `ProxyVerifier` | downloaded verifier | replace with a fixed local verifier |

## Smallest next changes (smallest first, none made)

1. Settle with the operator (see below) which exporter and constraints the code-free host uses; the whole analysis depends on whether the `ILFactory` forces `ATOMIC_DER`.
2. Move the `MailboxBackEnd` interface (and `MailboxAdmin`, `InternalMailboxException` if exposed) into `jgdms-lib-dl` or another library jar the client always has. Packaging only.
3. Make `RemoteEventData.convert` (DL `RemoteEventData.java:93`) write with `MarshallingFormat.ATOMIC_DER`, and require it on `getRemoteEvent` (`:114`). Follows the pattern of SVC `EventWriter.java:167-168`.
4. Replace the client-built `RemoteEventIteratorImpl` (DL `Registration.java:203-204`) with a value that is data plus a fixed local class, or move iteration to the server.
5. Replace `ProxyVerifier` with a fixed local verifier so correct use never needs verification switched off (memo row 2).
6. Add the tests in section 7 first; they define done.

## Questions only the operator can settle

- Which `serverExporter` does a deployed Mercury use, and does it set `AtomicDerILFactory` or leave the `AtomicILFactory(null, null, loader)` default (SVC `MailboxImplInit.java:138`, `:157`)? No qa configuration for Mercury was found under the repository paths searched (the `qa` directory does not exist at `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\qa`).
- Do target clients already carry `jgdms-lib-dl`, and what do they register for `RemoteEvent` subclasses, i.e. are event classes under the operator's control?
- Is the `mercury/norm` strict-mode qa run (named at `GeneratedMarshalDelegateTest.java:39-40`) available, and what does it show?
- Do the preparers pass `verify=true` with constraints on a code-free host (memo 11.1 question, `:648`)?

## Not done

No test or build was run. `RemoteEventDataCursor`, `Uuid`, `MethodConstraints` and the server-side `registerDo`/`getNextBatch` bodies were not read. No reviewer-model pass was made on this report (it needs an Opus or Fable seat, since a Sonnet produced it).
