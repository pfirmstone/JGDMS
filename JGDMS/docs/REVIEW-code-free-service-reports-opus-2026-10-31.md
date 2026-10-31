claude-opus-5-5

# Review: the five code-free proxy investigation reports (2026-10-31)

Reviewer: Opus 5.5 (`claude-opus-5-5`). The producer of all five reports is `claude-sonnet-5-5` (their own first lines), so this is a cross-model review.
Skills loaded before starting: `handover-composition`, `production-code`. `rag-file` and `code-comments` were not needed.
Method: static reading only. No build, no test, no Maven, no Java. The only file written is this one. Every VERIFIED below means I opened and read the cited line in this session.
Labels: VERIFIED (line read), PLAUSIBLE (inferred from lines read), UNDETERMINED. Each finding also carries a grade: MEASURED (traced end to end by reading) or HYPOTHESIS (needs a build or a run).
`E:\Claude\TRAINING_DOCS_INDEX.md` was checked. It lists no JGDMS or JERI corpus and no mandated `AI_AGENT_*` handover for this task family, so I raise no rule-7 finding.

## 0. The tree changed during this review. This is the section that ages fastest; re-read the disk before acting on it.

`git status` and `git diff HEAD -- services/` (run 16:13:27, 2026-10-31) show uncommitted edits, with mtimes 16:13:13, in `FiddlerInit.java`, `TxnManagerImplInitializer.java`, `MailboxImplInit.java`,
`NormServerInitializer.java` and `ActivatableNormServerImpl.java`. Each one replaces the default `AtomicILFactory` with `AtomicDerILFactory`. The five reports have mtimes from 16:09:16 to 16:12:01, so the edits came after them.
Consequence: every report statement "the default exporter is JOSS `AtomicILFactory`" (NORM Q1, Mahalo §1, Mercury §1, Fiddler Q1) is **CONFIRMED against HEAD 68acb611e**
(`git show HEAD:.../NormServerInitializer.java` lines 143-151 read: `new AtomicILFactory(null, null, NormServer.class.getClassLoader())`) and **stale against the working tree**.
A production change that follows the reports' "smallest next change" was made before their cross-model review finished. F4, F7 and F11 below bear on that change.

## 1. The five claims named in the brief

| # | Claim | Verdict | Line I read |
|---|---|---|---|
| 1 | NORM: the stub type includes `destroy`, `JoinAdmin` and `Landlord`; the permission class is null; so a registered stub lets any client call `destroy()` | **PARTLY** | `NormServer.java:46-47` `extends Landlord, LeaseRenewalService, Administrable, JoinAdmin, DestroyAdmin, ServiceProxyAccessor, Remote` CONFIRMED. `NormServerInitializer.java:148` second argument `null` is `permissionClass` (`AtomicILFactory.java:101-106`; the working tree keeps it null under `AtomicDerILFactory.java:60-62`) CONFIRMED. The report's premise "Today a client of `NormProxy` cannot reach them because `server` is package-private" is **REFUTED**: `AbstractProxy.java:40` `implements ReferentUuid, ProxyAccessor` and `:117-120` `public Object getProxy() { return server; }`. See F5. |
| 2a | OUTRIGGER: `className` is stored by `getName()` and compared by `getCanonicalName()`, so nested entry classes never prime | **CONFIRMED** | `EntryRep.java:511` `className = realClass.getName();`; `:820` `className.equals(tmpl.getClass().getCanonicalName())`. For a nested class these give `Outer$Inner` and `Outer.Inner`. For a local or anonymous class `getCanonicalName()` is null and the comparison is false. |
| 2b | …and so the entry "falls through to a codebase load … even though the class is local" | **PARTLY** | `EntryRep.java:846-849` `realClass = CodebaseProvider.loadClass(codebase, className, null, integrity, null);` CONFIRMED; it delegates to `ClassLoading`/`RMIClassLoader` (`CodebaseProvider.java:45-59`). Whether a locally present class is fetched from the wire codebase depends on the class provider (parent-first unless the entry jar's `PREFERRED.LIST` prefers it). That is HYPOTHESIS, not shown. See F10. |
| 2c | `EntryRep.entry()` loads from the wire codebase | **CONFIRMED** | `:846-849` as above. `codebase` and `className` come from the wire form. Priming happens only in `SpaceProxy2.entryFrom` (`SpaceProxy2.java:1192-1197`) and `MatchSetProxy.java:97`; `IteratorProxy.java:116` and `OutriggerAvailabilityEvent.java:90` call `entry()` without priming. |
| 3a | MAHALO: `TxnMgrProxy` and `TxnMgrAdminProxy` are pure forwarders | **CONFIRMED** | `TxnMgrProxy.java:136-194`: each method is `return backend.<same>(...)`. `TxnMgrAdminProxy.java:128-196`: each method is `server.<same>(...)`. Identity: `TxnMgrProxy.java:211-222`. |
| 3b | The registered or returned object could be `serverStub` instead | **PARTLY** | Mechanically yes (`TxnManagerImpl.java:555`, `:602-604`, `:1826-1858` read). But participants match transactions by manager equality, and a bare stub's equality does not survive a restart. See F2. |
| 3c | `equals` changes from `proxyID` to endpoint equality | **PARTLY** | Stub equality is the same handler class, an equal `ObjectEndpoint`, equal `serverConstraints` **and the same proxy interface list in the same order** (`BasicInvocationHandler.java:784` `Util.sameProxyClass(proxy, obj)`; `Util.java:704-708`; `BasicInvocationHandler.java:2003-2014`, with client constraints commented out at `:2013`). "Endpoint" alone understates it. |
| 4a | FIDDLER: `FiddlerLeaseMap` ~192 truncates a long with `intValue()` | **CONFIRMED** | `FiddlerLeaseMap.java:192` `dur[i] = durIt.next().intValue();` into a `long[]`. `Lease.FOREVER` (`Long.MAX_VALUE`) becomes `-1`, which is `Lease.ANY`. A duration of 2^32+1000 ms becomes 1000. Also `:211` `emap.put(...)` throws NPE if the server returns a negative duration with `exceptions == null`. |
| 4b | `getRegistrars` logic | **CONFIRMED** | `FiddlerRegistration.java:294-336` (unmarshal, partial failure into `LookupUnmarshalException`, `HashSet` dedup); `:817-818` `marshalledInst.get(false)`. Integrity verification is hard-coded off and the call carries no context. See F1. |
| 4c | Methods without `RemoteException` cannot be forwarded by `BasicInvocationHandler`, so a handler that answers them "would then be a downloaded class, which is tier 4" | **CONFIRMED / REFUTED** | Dispatch `BasicInvocationHandler.java:729-764` and its contract `:713-722`: CONFIRMED. The tier-4 conclusion is REFUTED: `AtomicDerInvocationHandler.java:281-291` already answers a non-remote interface method (`if (method.getDeclaringClass() == DerInputLimitControl.class)`, `:285`) locally, and that handler is resident. See F9. |
| 5a | MERCURY: `ConstrainableRegistration` builds `RemoteEventIteratorImpl` on the client | **CONFIRMED** | `Registration.java:196-208`: `new RemoteEventIteratorImpl(d.uuid, registrationID, mailbox, d.events)` inside `getRemoteEvents()`. |
| 5b | Events cross as `MarshalledInstance` built by the default (JOSS) factory | **CONFIRMED** (the report had it as PLAUSIBLE) | `RemoteEventData.java:93` `new MarshalledInstance(re)` → `MarshalledInstance.java:374-376` → `:393-397` `new MarshalFactoryInstance()`, which is the built-in JOSS factory per `:575-577`. The client side is `RemoteEventData.java:114` `mi.get(integrity)`. |

## 2. Findings, most severe first

**F1. Fix 1 cannot reach any one-argument `MarshalledInstance.get(boolean)`, which is every lazy client-side unmarshal the reports name. VERIFIED, MEASURED by reading.**
`MarshalledInstance.java:741-745` `return get(null, verifyCodebaseIntegrity, null, null);`. At `:890-898`, a null context becomes a singleton `IntegrityEnforcement`, and `:904` `requireFormatFromContext(ctext)` finds no `InvocationConstraints`, so it returns. Then `:916` runs `in.useCodebaseAnnotations()`.
Affected call sites: `SetProxy.java:170` (NORM), `BasicRenewalFailureEvent.java:183, :218` (NORM; see F6), `RemoteEventData.java:114` (Mercury), `FiddlerRegistration.java:817-818` (Fiddler), and the `mi.get(false)` reads that the Mahalo and Mercury reports cite.
Failure scenario: a client pins its stub to `ATOMIC_DER`. A hostile NORM returns a `GetLeasesResult` whose element is a JOSS `MarshalledInstance` with a codebase. `getLeases()` decodes it and resolves classes by codebase annotation, and the client's requirement never reaches that point.
NORM Q6 item 4 ("Fix 1/2 enforce ATOMIC_DER only if the client set it") implies that pinning would protect `:170`; it cannot. NORM's proposed test 2 ("Fails if the format requirement is not in the context") describes a mechanism that does not exist at that call site.
Memo §11.2 "Still not done (4)" names this class of gap in general; none of the five reports recognises it at its own call sites.

**F2. MAHALO: registering `serverStub` breaks two-phase-commit matching across a Mahalo restart and during a mixed rollout. Code path VERIFIED; end to end HYPOTHESIS.**
Participants key transactions on manager equality: Outrigger `TxnTable.java:104` `return o.manager.equals(manager);` and `ServerTransaction.equals` (`mgr.equals(t.mgr)`, jgdms-platform `ServerTransaction.java:105-115`). A miss throws `UnknownTransactionException` (`OutriggerServerImpl.java:4547-4568`).
`TxnMgrProxy` equality is `proxyID`, which is `topUuid` and persists. The default exporter is the four-argument `BasicJeriExporter` (`TxnManagerImplInitializer.java:143-150`, HEAD and working tree), which gets a fresh object id per export (`BasicJeriExporter.java:389-394` passes `null`; `:428` `UuidFactory.generate()`) and port 0.
Scenario: a persistent Mahalo restarts while a participant holds a prepared transaction keyed by the old stub. Mahalo's new stub is not equal to it, the participant's lookup misses, and the transaction cannot be resolved. The same miss happens when Mahalo report change 1 "leaves `TxnMgrProxy` in place for legacy clients": `ReferentUuids.compare(TxnMgrProxy, stub)` is false because `TxnManager` (`TxnManager.java:39-40`) is not a `ReferentUuid`.
The report filed this under "whether any client relies on the old rule is UNDETERMINED". The repository's own participant does rely on it.

**F3. Every report's "move the interfaces into a resident jar" step misses `PREFERRED.LIST`. VERIFIED (files read); consequence HYPOTHESIS.**
`outrigger-dl/src/main/resources/META-INF/PREFERRED.LIST` reads `Preferred: true`, and only `net/jini/-`, `org/apache/river/admin/*` and 3 `org/apache/river/outrigger/*Admin*` names are set false. `norm-dl`, `mahalo-dl`, `mercury-dl` and `fiddler-dl` also default to `Preferred: true` (5 files listed, 4 read in full).
Scenario: a client has `outrigger-dl` resident (Outrigger "sense B") and unmarshals a `ConstrainableSpaceProxy2` whose stream still carries the `Codebase_Annotation` (`OutriggerServerImpl.java:1164`, `:1027-1030`). The preferred class provider loads `SpaceProxy2`/`EntryRep` from the codebase jar, not the resident copy.
So "packaging, not logic" (Outrigger change 5, Mercury change 2, Fiddler Q3(b), Mahalo change 4) is not sufficient. The `-dl` preferred lists and the codebase annotation must also change. Not checked: whether the deployed `RMIClassLoaderSpi` is the preferred provider.

**F4. The DER switch and the "register the bare stub" changes fail `verify=true` clients: the only resident JERI verifier rejects the DER handler. Lines VERIFIED; end to end HYPOTHESIS.**
`BasicJeriTrustVerifier.java:319` `if (handlerClass != BasicInvocationHandler.class && handlerClass != AtomicInvocationHandler.class)` returns false. This is memo §11 row 2 (R2), at the same line.
`grep -rln AtomicDerInvocationHandler` over `src/main` finds 7 files, none of them a `TrustVerifier`. A verifier using an `instanceof` test would not show up in that grep; I read only `BasicJeriTrustVerifier`.
NORM change 2, Mahalo changes 1-3, Fiddler changes 2-3 and Mercury's tier table all recommend DER plus a bare stub. None ties that to row 2, though NORM and Fiddler list "row 2" only as "verifiers OR'd". The working-tree change in §0 now makes the default stub of four services one that this verifier rejects.

**F5. NORM's authority finding is mis-framed. VERIFIED.**
The `NormServer` stub, including `destroy()`, is reachable by any holder today in two ways: `ProxyAccessor.getProxy()` (`AbstractProxy.java:117-120`), and `NormProxy.getAdmin()` → `AdminProxy.destroy()` (NORM's own Q2). It is also in the proxy's serialized form (`AbstractProxy.java:52-56`, field `"server"`). Hiding the stub inside a smart proxy is not an access control.
The control that exists is server-side: the QA set `C:\Users\peter\Documents\GitHub\JGDMS\qa\harness\configs\der\norm\norm.config:244-261` exports with `ClientAuthentication.YES` and `AccessPermission.class`.
Mahalo has the same exposure (`TxnMgrProxy.java:225-227` `getProxy()` returns `backend`, which implements `DestroyAdmin`). The Mahalo report recommends registering that stub and does not mention it. NORM's recommendation "export separate objects per role" is a hardening, not a precondition created by code-free.

**F6. NORM missed a client-side code-loading path: renewal-failure events. VERIFIED.**
`ClientLeaseWrapper.java:340` `return new MarshalledInstance(cl);` (JOSS) and `:698` `mt = new MarshalledInstance(lastFailure);` (JOSS, an arbitrary `Throwable`) feed `BasicRenewalFailureEvent` (constructed at `ClientLeaseWrapper.java:750-753`). The client listener then calls `getLease()` / `getThrowable()` → `BasicRenewalFailureEvent.java:183` / `:218` `.get(verifyCodebaseIntegrity)`, which has no context (F1).
NORM Q6 lists `:340` and `:463` only as feeding `getLeases`. The Throwable at `:698` is absent from the report.

**F7. NORM: a DER-only dispatcher may refuse NORM's own job, renewing other services' leases. UNDETERMINED (HYPOTHESIS).**
`NormServer.renewFor(Uuid id, Lease leaseToRenew, …)` (`NormServer.java:66-68`) takes a third-party `Lease`. Commit 62670b2ad makes `AtomicDerInvocationDispatcher` require its own format on nested payloads (`AtomicDerInvocationDispatcher.java:117-118` `.withRequiredPayloadFormat(marshallingFormat())`).
Scenario: a lease from a JOSS-exported landlord service arrives inside a smart-proxy carrier in JOSS, and NORM refuses it. NORM change 2 calls the DER default the "smallest change" and does not flag this. The Mahalo report did flag the analogous `join(participant)` case.
Fiddler's `discard(ServiceRegistrar)` and Mercury's `notify(Uuid, RemoteEvent)` from arbitrary event sources face the same question. Settling it needs a build and a call with a real third-party lease.

**F8. MERCURY: the event iterator turns rejection into silent loss and recurses once per bad event. Code VERIFIED; stack overflow HYPOTHESIS.**
`RemoteEventData.java:114-118` converts any `IOException` from `mi.get` into `ClassNotFoundException`. `UnsupportedConstraintException extends IOException` (`UnsupportedConstraintException.java:49`), so a format rejection takes the same route. `RemoteEventIteratorImpl.java:139` `lrd = getNextValidLocalRemoteEventData(i);` then skips the event by recursing.
Batches are not capped by count: `MailboxImpl.java:2325, :2414` `readAhead(Integer.MAX_VALUE)`, and `TransientEventLog.java:184` caps at `entries.size()`. I did not read `PersistentEventLog`.
Scenario: Mercury change 3 is applied (require `ATOMIC_DER` at `:114`), and a source posts N JOSS events. The client's `next()` silently drops them; recursion depth N risks `StackOverflowError`, an `Error` on a client path in the manner of memo row 13. Because the dropped events' cookies are never recorded, the next `getNextBatch` starts from the same `lastEventCookie` and can fetch the same events again. The report called the skip "the safe direction".

**F9. FIDDLER (and the ReferentUuid point in all four other reports): a resident handler can answer local methods. VERIFIED.**
`AtomicDerInvocationHandler.java:285` is the precedent. A resident handler subclass carrying `@AtomicSerial` data (endpoint, Uuid, an `EventRegistration`) is training-note tier 1, not tier 4. So `getReferentUuid()`, `getEventRegistration()` and `getLease()` are not by themselves blockers.
The real blocker is trust: a Uuid answered from wire data is self-asserted, and memo §11.1 rule "identity from the authenticated stub, never from the wire" requires the verifier to bind it. `FiddlerLease` local expiry and `FiddlerLeaseMap` remain resident-class work, as the report says.

**F10. OUTRIGGER: the priming bug is real but marginal to code-free. VERIFIED as code; impact PLAUSIBLE.**
Priming succeeds only when the entry's class name equals the template's, so a subclass match (the normal JavaSpaces idiom), a null or match-any template, `contents()` (`IteratorProxy.java:116`) and availability events (`OutriggerAvailabilityEvent.java:90`) always reach `:846-849` whatever change 4 does. Change 3 (a no-download mode) is what closes path 1. Change 4 only removes one sub-case.
The report's proposed test 2 ("a stub class loader that records the call") has no place to plant its instrument: `EntryRep.java:847` passes `null` as the default loader to a static method. A recording `RMIClassLoaderSpi` or a counting codebase URL would be needed, otherwise the test reads zero by construction.

**F11. Negatives about `qa` are wrong in scope, and several "operator-only" questions are answerable on disk. VERIFIED.**
The `qa` tree is at `C:\Users\peter\Documents\GitHub\JGDMS\qa`, one level above where the NORM and Mercury reports looked. NORM: "does not exist at `R\qa`"; Mercury: "NOT ON DISK"; Fiddler: "did not search the QA tree".
`qa\harness\configs\der\` holds `norm`, `mahalo`, `mercury`, `fiddler` and `outrigger` configs. In that "der" set:
- NORM, Mahalo, Mercury and Fiddler export with JOSS `AtomicILFactory` (`norm.config:247`, `mahalo.config:220`, `mercury.config:221`, `fiddler.config:199`); only Outrigger uses `AtomicDerILFactory` (`outrigger.config:246`). So the §0 default change does not reach the QA "der" runs, which override it.
- `grep -c "MarshallingFormat\|ATOMIC_DER"` gives 0 in all 10 files (`*/*.config` plus `test.config`).
- The preparers read use the two-argument `BasicProxyPreparer(constraints, permissions)`, which means `verify=false` (`BasicProxyPreparer.java:233-236`): `norm.config:84,95,153` and `mahalo.config:68,125`.
That answers, for QA, the reports' questions "which `serverExporter`" and "`verify=true`?". It also shows memo §11.2 (2), unprotected when nothing requires the format, applies inside the DER QA suite itself.

**F12. Minor citation and method defects. VERIFIED.**
- Mahalo cites the `useAnnotations` constructor at `AtomicILFactory.java:393-401`. That line is the five-argument `Compression` overload; the four-argument `(…, Class, boolean useAnnotations)` constructor is at `:334-341`.
- Fiddler took the `FiddlerAdminProxy` line numbers from a grep, and Mahalo cites `Server.java:161` as "comment text only". The method rule is that a grep hit is not a citation (training note §3), and both reports disclose it.
- Mahalo's "endpoint based" equality: see claim 3c.
- `SetProxy.java:172` `catch (Throwable t)` swallows `Error`s into per-element "problems", the memo §11.1 path 7 pattern. NORM did not flag it.

## 3. Item 6: CAN verdicts that unread client-side behaviour would block

| Report and verdict | What blocks it | Status |
|---|---|---|
| Mahalo CAN WITH CHANGES (changes 1-2) | Participant `TxnTable` matching under restart or mixed rollout (F2); `PREFERRED.LIST` (F3); `BasicJeriTrustVerifier:319` (F4) | F2 code VERIFIED; end to end HYPOTHESIS |
| NORM CAN for `NormProxy`/`AdminProxy` | F3, F4. The authority point (F5) is not a blocker: it exists today | VERIFIED / HYPOTHESIS |
| Mercury CAN for `MailboxProxy`/admin; `ListenerProxy` "yes" | F3, F4. Event sources must hold `mercury-dl`; the report says so | VERIFIED / HYPOTHESIS |
| Fiddler CAN for the service proxy | F3, F4. `ReferentUuid` equality is recoverable (F9) | VERIFIED / HYPOTHESIS |
| Outrigger CAN, sense B | F3: the resident copy is shadowed while `Preferred: true` and the annotation remain; F1 for any lazy `get(boolean)` | VERIFIED / HYPOTHESIS |

**Memo §11 paths the reports missed.**
- One-argument `get` (F1; memo §11.2 (4)), in all five reports' service code.
- Row 2 at the exact line `BasicJeriTrustVerifier.java:319`, under the DER switch (F4).
- Errors swallowed or converted: `SetProxy.java:172` and `RemoteEventData.java:116-118` (F8, F12).
- A row-13-style `Error` on a client path: the Mercury recursion (F8).
- Defaults that authenticate nothing, measured in the QA "der" configs themselves (F11).
- Outrigger's own fail-open at configuration: a non-`BasicJeriExporter` (including an `ActivationExporter`) gets only a WARNING (`OutriggerServerImpl.java:1270-1282`). The report records the fact but does not propose a post-export handler-class check in its change list.
- Memo §11.1 item 9 (a null codebase may fall back to `java.rmi.server.codebase`) applies to `EntryRep.entry()` when the writer had no annotation. Untraced by all five reports and by me.

## 4. Proposed tests: does each gate read differently for right and wrong code?

- **Mahalo test 1** (`Proxy.isProxyClass(obj.getClass())`) also passes for a JOSS-handler stub whose interfaces come from `mahalo-dl` under `Preferred: true`. It checks shape, not code-free behaviour: it reads the same for the wrong change.
- **NORM test 2**: the "requirement in the context" it would remove does not exist at `SetProxy.java:170` (F1). It needs a production change before it can be load-bearing.
- **Mercury test 3**, as a check through `next()`: a rejected event and an empty mailbox both read "no event" (F8). The test must assert on `RemoteEventData.getRemoteEvent` directly, or count drops.
- **Outrigger test 2**: its instrument reads zero by construction (F10).
- **Fiddler test 4** (duration above `Integer.MAX_VALUE`) does discriminate: on HEAD it sends 1000 for 2^32+1000.

## 5. UNVERIFIED-NEEDS-BUILD

1. F2 end to end: restart a persistent Mahalo with a prepared Outrigger transaction while a bare stub is registered.
2. F3: whether the deployed class provider honours `PREFERRED.LIST` over a resident copy when a DER carrier is decoded.
3. F4: whether any path verifies a DER stub (for example through `ProxyTrustVerifier` and a service `ProxyVerifier`) despite `:319`.
4. F7: `renewFor` with a lease from a JOSS-exported landlord against a DER-exported NORM.
5. F8: the actual recursion depth against the persistent log, and whether `next()` refetches the dropped events.
6. Whether the §0 working-tree edits compile; I ran no build.

## 6. Commands and reads that performed the checks (so "described" is not mistaken for "performed")

- `git status --short`, `git diff HEAD -- services/`, `ls --time-style=full-iso` on the reports and edited files, `git show HEAD:…/NormServerInitializer.java | sed -n 143,151p`.
- Read: memo §11-11.2 (lines 582-680); the training note; all five reports; `NormServerInitializer.java:90-153`; `NormServer.java:30-69`; `AbstractProxy.java` (whole file);
  `AtomicDerILFactory.java:20-180`; `AtomicILFactory.java:40-135, 160-170, 210-250, 330-345, 380-420`; `MarshalledInstance.java:366-405, 560-760, 825-934`; `BasicRenewalFailureEvent.java:170-224`;
  `ClientLeaseWrapper.java:200-212, 334-344, 458-466, 685-712`; `SetProxy.java:150-175`; `EntryRep.java:318-336, 500-535, 800-870`; `SpaceProxy2.java:1185-1205`; `MatchSetProxy.java:85-105`; `CodebaseProvider.java:45-76`;
  `OutriggerServerImpl.java:864-890, 1250-1300, 4547-4569, 4667-4697`; `ConstrainableSpaceProxy2.java:425-460`; `TxnTable.java:60-130, 200-225, 300-345`; `TxnMgrProxy.java:95-300`; `TxnMgrAdminProxy.java:128-200`;
  `TxnManagerImpl.java:548-560, 598-612, 1826-1858`; HEAD `TxnManagerImplInitializer.java:120-175`; `BasicInvocationHandler.java:700-800, 1975-2056`; `Util.java:704-712`; `BasicJeriExporter.java:368-440`;
  `BasicJeriTrustVerifier.java:90-110, 300-330`; `ServerTransaction.java:95-116`; `Registration.java:160-212`; `RemoteEventData.java:80-125`; `RemoteEventIteratorImpl.java:36-150`; `FiddlerLeaseMap.java:165-225`;
  `FiddlerRegistration.java:285-340, 780-835`; `FiddlerImpl.java:932-944`; `RemoteDiscoveryEvent.java:390-400`; the five `-dl` `PREFERRED.LIST` files (outrigger and norm in full); `qa\harness\configs\der\norm\norm.config:40-100, 236-262`;
  `BasicProxyPreparer.java:151-236`.
- grep-located, then read: the `primeEntryClass` callers, the `entry()` callers, `readAhead`, `UnsupportedConstraintException`, the `AtomicDerInvocationDispatcher` required-format line, and the `ILFactory(` and `MarshallingFormat` counts in the QA configs.
- Not read: `PersistentEventLog.readAhead`, the `ProxyTrustVerifier` path, `ClassLoading.loadClass`, the Mahalo `JoinStateManager`/`StorableObject` lines, and the Fiddler `check` at `:913-941`. The report statements resting on these are neither confirmed nor refuted here.
