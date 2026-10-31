Model: claude-fable-5-1 (Fable 5.1). Producer of the five reports: claude-sonnet-5-5 (each report's first line). Cross-model: yes.
Skills loaded: handover-composition, production-code, jgdms-board-review. Static reading only; no mvn, no java. Read-only except this file.

# Review: five code-free-proxy investigation reports (outrigger, mahalo, mercury, norm, fiddler), 2026-10-31

Paths below are under `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\` (`R\`). Every "read" below names the line I opened; a grep hit alone is marked as such.
Labels: VERIFIED = line opened and read end to end; PLAUSIBLE = inferred from read lines; UNDETERMINED = not settled on disk; UNVERIFIED-NEEDS-BUILD = needs a compile or run.

## F0 (most severe, cross-cutting): the tree moved one minute after the reports, and the move does not do what the reports' next-change lists imply

**VERIFIED.** `git status --short` (run 2026-10-31, repo `R\`): 8 modified, 6 untracked. Modified: `services/{fiddler/fiddler-service/.../FiddlerInit.java, mahalo/mahalo-service/.../TxnManagerImplInitializer.java,
mercury/mercury-service/{pom.xml,.../MailboxImplInit.java}, norm/norm-service/{pom.xml,.../ActivatableNormServerImpl.java,.../NormServerInitializer.java}}` plus the DESIGN memo. Untracked: the five reports and the training note.
`git log` on the five initializers shows no commit since 2018-2021, so the change is uncommitted. `git diff -U1`: in each initializer `AtomicILFactory` -> `AtomicDerILFactory` (Mahalo also drops the trailing `true`, useAnnotations);
the two poms move `jgdms-der` from `<scope>test</scope>` to compile with the comment "the same trap as outrigger-service".
mtimes (`stat -c %y`): reports 16:09:16 (mercury) .. 16:12:01 (mahalo); the five initializers all 16:13:13; the two poms 16:13:59.
Consequences:
1. Every report's Q1 statement "default exporter is `AtomicILFactory` / JOSS" (norm Q1, mahalo s1, mercury s1, fiddler Q1) was true at 16:09-16:12 and is false against the working tree now. Each report's
   "switch the default exporter" item (norm Q8.2, mahalo s8.3, fiddler item 2; mercury item 1 asked the operator) has been applied without a build, a test, or a commit. The reports should carry a strike-in-place note, not be read as current.
2. **The switch does not put a required `ATOMIC_DER` constraint on the stub.** Outrigger alone does that: `OutriggerServerImpl.java:872-876` builds `initialConstraints = new BasicMethodConstraints(new InvocationConstraints(MarshallingFormat.ATOMIC_DER, null))`
   and passes it to `ConstrainableSpaceProxy2`. The other four create their proxies with no constraints: `NormServerBaseImpl.java:2061` `NormProxy.create(serverProxy, serverUuid)`; `TxnManagerImpl.java:498-499` `TxnMgrProxy.create(serverStub, topUuid)`;
   `MailboxImpl.java:915-916` `MailboxProxy.create(serverStub, serviceID)`; `FiddlerImpl.java:5363` `FiddlerProxy.createServiceProxy(innerProxy, proxyID)` (all VERIFIED). `AtomicDerInvocationHandler.requiredPayloadFormat(Collection)` (`jgdms-jeri/.../AtomicDerInvocationHandler.java:156-162`)
   returns null unless the context holds an `InvocationConstraints` requiring a `MarshallingFormat`, and `:201-203` builds the `ResolutionContext` with that null. So after the edit, memo 11.2 "still not done (2)" (client with no constraint is unprotected) still describes norm, mahalo, mercury and fiddler:
   the server-side argument path is DER-only (memo 11.2 item 3, relayed), the client-side nested-`MarshalledInstance` path is not enforced. No report states this distinction, and the edit does not close it.
3. **UNVERIFIED-NEEDS-BUILD:** `mahalo-service/pom.xml` and `fiddler-service/pom.xml` declare no `jgdms-der` dependency at all (grep of the five service poms: hits only in norm:73, mercury:82, outrigger:91). `jgdms-jeri/pom.xml:51-54` declares `jgdms-der` at default (compile) scope, so it should arrive
   transitively; norm and mercury needed the explicit change only because their own `test` scope overrode it. I could not compile to confirm.

## F1 (severity high, cross-cutting): the smart proxy is not an authority boundary today, so NORM's "authority problem" premise is wrong and Mahalo's silence on it is a gap

**VERIFIED.** `ProxyAccessor.getProxy()` is public and returns the exported stub in all five: norm `AbstractProxy.java:117-120` (`return server`), mahalo `TxnMgrProxy.java:225-227` (`return backend`), mercury `MailboxProxy.java:191-193` (`return mailbox`),
fiddler `FiddlerProxy.java:284-286` (`return server`), outrigger `SpaceProxy2.java:324-326` (`return space`). The stub types carry the admin surface: `NormServer extends Landlord, LeaseRenewalService, Administrable, JoinAdmin, DestroyAdmin, ServiceProxyAccessor, Remote` (`NormServer.java:46-47`);
`TxnManager extends Remote, Landlord, DestroyAdmin, Administrable, JoinAdmin, ServiceProxyAccessor, TransactionManager` (`TxnManager.java:39-40`). `AbstractILFactory.getRemoteInterfaces` puts every `Remote`-extending interface of the impl on the proxy (`AbstractILFactory.java:165-178, 194-199`; this also
settles mahalo s3's "PLAUSIBLE, did not read the exporter's interface selection").
- NORM Q3 "Today a client of `NormProxy` cannot reach them because `server` is package-private (`AbstractProxy.java:45`)" is REFUTED: the field is package-private (`:45`) but `getProxy()` hands it out. A client holding `NormProxy` can call `destroy()` today with one cast. The swap to a dynamic proxy changes nothing about authority.
- Server-side permission class is null in all four non-Outrigger factories: norm `NormServerInitializer.java:148` `new AtomicDerILFactory(null, null, NormServer.class.getClassLoader())` matches ctor `(MethodConstraints, Class permissionClass, ClassLoader)` at `AtomicDerILFactory.java:60-62`; mahalo `:130,:146` `(null, null, TxnManager.class)` matches `(MethodConstraints, Class permissionClass, Class)` at `:111-113`;
  mercury `MailboxImplInit.java:138,:157` same shape as norm; fiddler `FiddlerInit.java:247` opens the call, arguments not read (RELAYED from the fiddler report). So the only thing between any client and `destroy()` is deployment policy/constraints. The mahalo report's change 1 (register the stub) and change 2 (return the stub from `getAdmin`) do not mention this; the norm report mentions it as a consequence of the swap rather than as the current state.

## F2 (severity high for Fiddler, functional): the `intValue()` truncation is live on a common LeaseRenewalManager path, not latent

Report: `FiddlerLeaseMap.java:192` truncates, "a latent bug, not a code-free issue". CONFIRMED at `:192` `dur[i] = durIt.next().intValue();`, and the consequence is worse than stated. Chain, each link read:
- `LeaseRenewalManager.renewFor(lease, desiredDuration, listener)` delegates with `renewDuration = Lease.FOREVER` (`jgdms-lib-dl/.../LeaseRenewalManager.java:541` javadoc; impl `jgdms-lib/.../LeaseRenewalManagerImpl.java:610`). With `desiredDuration = Lease.FOREVER`, `expiration = Lease.FOREVER` (`:629-632`).
- Batch duration is `e.getRenewDuration(now)` = `min(expiration - now, renewDuration)` = `Long.MAX_VALUE - now` (`LeaseRenewalManagerImpl.java:305-308`, called at `:1071` `e.lease.createLeaseMap(...)`); batching happens when `lease.canBatch` holds (`:193`, `:431-441`), and `FiddlerLease.canBatch` is true for any two leases with the same `serverID` (`FiddlerLease.java:245-248`).
- `(Long.MAX_VALUE - now).intValue()` is the bitwise NOT of the low 32 bits of `now`; its sign is NOT(bit 31 of `now`), which flips every 2^31 ms = 24.86 days. (Arithmetic is mine, UNVALIDATED by a run.) In the negative windows the server rejects every lease in the batch: `FiddlerImpl.java:6283-6284` `else if (duration <= 0) throw new IllegalArgumentException("non-positive lease duration")`,
  caught per lease at `:6413-6423` (`durations[i] = -1`, exception appended); the client then `remove(e)`s each lease and throws `LeaseMapException` (`FiddlerLeaseMap.java:207-216`). In the positive windows the renewal is for an arbitrary value up to 24.8 days, not FOREVER.
- Severity: a client that manages two or more leases from one Fiddler with the default `renewFor` loses all of them, intermittently, with no code-free change involved. Grade: path MEASURED by reading, runtime HYPOTHESIS. The report's proposed test 4 is the right test; its ordering (item 4 of 8) is not.

## F3 (severity medium, cross-cutting): the three client-side "decode later" sites are outside the stream context, so Fix 1/Fix 2 never reach them; no report maps its site to memo 11.2(4)

**VERIFIED.** `MarshalledInstance.get(boolean)` is `return get(null, verifyCodebaseIntegrity, null, null)` (`jgdms-platform/.../MarshalledInstance.java:741-745`); the format check is `requireFormatFromContext(ctext)` (`:904`, method `:677-683`) and matches only an `InvocationConstraints` in the context. With a null context nothing is required.
Sites: norm `SetProxy.java:170` `ml.get(result.verifyCodebaseIntegrity())`; fiddler `FiddlerRegistration.java:817-818` `marshalledInst.get(false)` (integrity also off); mercury `RemoteEventData.java:114` `mi.get(integrity)`. Memo 11.2 "still not done (4)" names exactly this class ("a `MarshalledWrapper` or `ServiceItem` field read later").
- Norm Q6.4 relays memo row 1 as applying "with no required format set"; in fact no setting on the call can reach this overload, so norm's proposed test 2 ("fails if the format requirement is not in the context") describes a mechanism that does not exist for `SetProxy`. PARTLY.
- Fiddler Q6 says the call "can load classes" (correct) but does not say the required-format fix is structurally out of reach. PARTLY.
- Mercury's change 3 ("require it on `getRemoteEvent`") is the right direction and is the only report that proposes a change at the site.
Also VERIFIED for norm Q6.3: `ClientLeaseWrapper.java:207-208` creates with `ATOMIC_DER`; `:340` and `:463` recreate with `new MarshalledInstance(cl)` (default factory), as the report says.

## F4 (severity medium, Mercury): the host loads every application event class, twice; the report frames host-side loading as listener-only

**VERIFIED.** `MailboxBackEnd.notify(Uuid, RemoteEvent)` arrives already unmarshalled (`MailboxImpl.java:1232-1238`): the dispatcher loads the event class on the host. On read-back `EventReader.java:158-161` does `mi.get(false)` and the result is re-wrapped by `new RemoteEventData(evt, cursor)` (`PersistentEventLog.java:482-484`; transient log `TransientEventLog.java:189-190`),
whose `convert` uses `new MarshalledInstance(re)` (`RemoteEventData.java:93`) -> `MarshalledInstance(Object)` -> `this(obj, EMPTY_SET, new MarshalFactoryInstance())` (`MarshalledInstance.java:374-397`) -> `MarshalledInstanceOutputStream extends MarshalOutputStream` (`:1089-1090`), JOSS. So the report's claim 5 is CONFIRMED (JOSS default factory, client-built iterator at `Registration.java:197-209`),
and the host-side picture in mercury Q5.7 ("relevant to the mailbox's own host, not to the client") understates it: the mailbox host is a code-loading host for every `RemoteEvent` subclass it relays. When the host cannot load the class, `PersistentEventLog.java:526-528` emits `RemoteEventData(null, cursor)`; the client's `getRemoteEvent` throws `ClassNotFoundException` (`RemoteEventData.java:109-111`)
and `RemoteEventIteratorImpl.getNextValidLocalRemoteEventData` recurses to the next element (`:138-139`, unbounded on consecutive failures). The report attributes the silent skip to the client's loader only. PARTLY.
Minor, server recovery: `MailboxImpl.java:3776-3784`, `EventID.java:91-99, :222-230` catch `Throwable` and return null for everything except non-Linkage/OOM/SOE `Error`s, the memo 11.1 item 7 shape; not in the report. PLAUSIBLE relevance (host side, not client code-free).

## The six briefed claims, verdicts

| # | Claim | Verdict | Lines read |
|---|---|---|---|
| 1 | NORM stub type includes `destroy()`, `JoinAdmin`, `Landlord`; null permission class; registered stub lets any client call `destroy()` | CONFIRMED for the type and the null; premise "cannot reach today" REFUTED (F1) | `NormServer.java:46-47`; `NormServerInitializer.java:143-151`; `AtomicDerILFactory.java:60-62`; `AbstractProxy.java:45, 117-120` |
| 2 | OUTRIGGER `getName()` vs `getCanonicalName()` mismatch; `entry()` loads by wire codebase | CONFIRMED mechanism; consequence PLAUSIBLE | `EntryRep.java:511` `className = realClass.getName()`; `:820` `className.equals(tmpl.getClass().getCanonicalName())`; `:846-849` `CodebaseProvider.loadClass(codebase, className, null, integrity, null)`; callers `SpaceProxy2.java:1192-1196`, `MatchSetProxy.java:94-99`; `CodebaseProvider.java:45-58` (`jgdms-lib-dl/.../proxy/`) delegates to `ClassLoading.loadClass`, whose preferred-list behaviour I did not read |
| 3 | MAHALO proxies pure forwarders; stub could be registered; equals moves from proxyID to endpoint | CONFIRMED forwarders and registration; equality PARTLY | `TxnMgrProxy.java:137-194, 207-227`; `TxnMgrAdminProxy.java:130-196`; `TxnManagerImpl.java:493-512, 555, 602-604`; `BasicInvocationHandler.java:779-785` (same proxy class required) and `:2003-2015` (object endpoint + `serverConstraints`; `clientConstraints` comparison commented out) |
| 4 | FIDDLER `intValue()`; `getRegistrars` logic; non-`RemoteException` methods cannot be forwarded | CONFIRMED all three; severity of the first raised (F2) | `FiddlerLeaseMap.java:174-218`; `FiddlerRegistration.java:294-337, 785-833`; `BasicInvocationHandler.java:713-723` (javadoc), `:733-764` (dispatch), `:868-873` (`Util.checkProxyRemoteMethod` on entry to `invokeRemoteMethod`) |
| 5 | MERCURY client-built iterator; events as JOSS `MarshalledInstance` | CONFIRMED (F4 for what it leaves out) | `Registration.java:197-209`; `RemoteEventData.java:90-98, 108-120`; `MarshalledInstance.java:374-397, 1052-1077, 1089-1090` |
| 6 | CAN verdicts resting on unread client behaviour; missed memo 11 paths | See below | |

Claim 2 additions. Priming is only an optimisation: `entryFrom(rep, null)` (null template) primes nothing, and a result whose class is a subclass of the template never matches, so `entry()`'s codebase load is the designed path for those reads regardless of nesting. The report's change 4 (fix priming) therefore cannot close its Q6 item 1; only change 3 can, and its proposed test 3 must use an exact-class template or it fails for the wrong reason.
Claim 3 addition. `Util.sameProxyClass` (`:784`) means two stubs of one service reconstructed in different class loaders are unequal where `ReferentUuids.compare` said equal; the report says "endpoint based" only.
Claim 4 addition. `ReferentUuid` is not special-cased by any JERI handler (grep `ReferentUuid` over `jgdms-jeri/src/main/java/net/jini/jeri/*InvocationHandler.java`: no hit), so the report's "cannot be forwarded" holds for `getReferentUuid()` too, as it says.

## Claim 6: verdicts that lean on unread client behaviour, and memo section 11 items missed

- All four CAN WITH CHANGES verdicts for the outer service proxy rest on "equality by handler is acceptable". None read a consumer (`ServiceDiscoveryManager`, `JoinManager`, `LeaseRenewalManager`, participants comparing `ServerTransaction.mgr`). UNDETERMINED in every report, correctly labelled; it remains the one unmeasured premise under each CAN.
- Memo 11.1 item 4 (defaults authenticate nothing): fiddler Q4 names `listenerPreparer.prepareProxy` but not that `FiddlerInit.java:134-178, 263-269` default eight preparers to `new BasicProxyPreparer()` (grep count 8 on `BasicProxyPreparer`). Norm, mahalo and mercury name their defaults.
- Memo 11.2(4): F3 above; missed by norm and fiddler, half-addressed by mercury.
- Memo row 1 / 11.2(2) after the exporter edit: F0 item 2; no report could have said it (the edit postdates them), but the coordinator's next step should.
- Memo 11.1 item 7 shape on the mercury host: F4, minor.
- Not a memo item but the same rule ("identity from the authenticated stub"): F1, `getProxy()` exposure, in all five services, in no report.

## Smaller corrections

- Norm Q3/"not settled": `JoinAdmin` is `R\jgdms-lib-dl\src\main\java\net\jini\admin\JoinAdmin.java`, `DestroyAdmin` is `R\jgdms-lib-dl\src\main\java\org\apache\river\admin\DestroyAdmin.java` (Glob). Mahalo s3 already placed them there.
- Norm Q1 negative "no NORM file or config on disk names `AtomicDerILFactory`" was true at 16:09 and is false now (`NormServerInitializer.java:36, 148`; `ActivatableNormServerImpl.java:34, 161`).
- Mahalo s1 "`AtomicILFactory(null, null, TxnManager.class, true)`, `useAnnotations`": gone in the working tree (`:130, :146` now `AtomicDerILFactory(null, null, TxnManager.class)`).

## UNVERIFIED-NEEDS-BUILD (not dropped)

1. Whether mahalo-service and fiddler-service compile with `AtomicDerILFactory` and no direct `jgdms-der` dependency (F0.3).
2. Whether the four edited services start and accept a call from a client with no constraints (F0.2 says the nested-payload check is inert; a run would show whether anything else refuses JOSS).
3. The F2 sign-flip arithmetic and the LRM batching trigger (a JUnit over `FiddlerLeaseMap.renewAll` with two leases and `Lease.FOREVER - now` as the duration).
4. Outrigger nested-class priming (a test with a `static` nested entry class and an exact-class template, asserting no `ClassLoading.loadClass` call).

## Provenance notes

- Rule 7: no `AI_AGENT_*` document exists under `R\docs` (Glob `**/AI_AGENT*`: none). The producers' bounding corpus was the training note and memo sections 11-11.2, both named in their briefs; nothing further was mandated that I can find.
- Counts in this file: 5 reports (the brief's list); 8 modified + 6 untracked paths (`git status --short`); 5 `getProxy()` sites (grep over the five named proxy classes); 4 services without a stub constraint (the four creation lines quoted in F0.2); 8 `BasicProxyPreparer` defaults in `FiddlerInit.java` (grep count).
- Nothing here was run. Where I and a report disagree on a mechanism (F1, F3), both readings are quoted with their lines; the discriminating measurement for F2 is item 3 above.
