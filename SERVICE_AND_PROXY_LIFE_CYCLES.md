# Service and Proxy Life Cycles

This document illustrates three complete, interlocking life cycles in JGDMS:

1. **[Service Start / Runtime / Shutdown Life Cycle](#1-service-start--runtime--shutdown-life-cycle)** —
   how a service is launched by `ServiceStarter`, constructed, exported, joined to lookup services,
   kept alive through lease management, and ultimately destroyed via `DestroyAdmin`.

2. **[Service Discovery Life Cycle](#2-service-discovery-life-cycle)** — how a service moves from
   registration in a lookup service through discovery, bootstrap preparation, filtering, and caching
   inside `ServiceDiscoveryManager` and `LookupCacheImpl`.

3. **[ProxyCodebaseSpi Life Cycle](#3-proxycodebasespi-life-cycle)** — how a smart proxy's codebase
   is recorded at export time, serialised into a stream, and resolved back into a typed object on the
   client side with per-service `ClassLoader` isolation.

---

## 1. Service Start / Runtime / Shutdown Life Cycle

### 1.1 Overview Flowchart

```mermaid
flowchart TD
    subgraph ENTRY ["Phase 1 — ServiceStarter Entry Point"]
        E1(["ServiceStarter.main(args)\nor main(config)"]) --> E2
        E2[ensureSecurityManager\ninstall CombinerSecurityManager\nif none present] --> E3
        E3["ConfigurationProvider\n.getInstance(args)\n→ Configuration"] --> E4
        E4{"loginContext\nin config?"} -- Yes --> E5
        E4 -- No --> E6
        E5["LoginContext.login\nSubject.doAsPrivileged\n  → create(descs, config)\nLoginContext.logout"] --> E6
        E6["for each ServiceDescriptor:\n  desc.create(config)"] --> E7
        E7["maintainNonActivatableReferences\nstore strong refs to\nnon-activatable service proxies\n(prevent GC)"]
    end

    subgraph NASD ["Phase 2a — NonActivatableServiceDescriptor.create()"]
        NA1["Create ExportClassLoader:\n  importCodebase URLs (server impl)\n  exportCodebase URLs (stubs/proxy)\n  parent = current context loader"] --> NA2
        NA2["Wrap in DynamicPolicyProvider\nif not already dynamic"] --> NA3
        NA3["LoadClass.forName(implClassName,\n  false, newClassLoader)\nSet thread contextClassLoader"] --> NA4
        NA4{"Configuration\nor String[] args?"} -- Configuration --> NA5A
        NA4 -- String[] args --> NA5B
        NA5A["constructor(Configuration, LifeCycle)"] --> NA6
        NA5B["constructor(String[], LifeCycle)"] --> NA6
        NA6[constructor.newInstance\nService impl created] --> NA7
        NA7{"impl instanceof\nStartable?"} -- Yes --> NA8
        NA7 -- No --> NA9
        NA8["impl.start()\n(see Phase 3)\nthreads/export deferred\nuntil construction complete"] --> NA9
        NA9{"impl instanceof\nServiceProxyAccessor?"} -- Yes --> NA10
        NA9 -- No → ProxyAccessor? --> NA11
        NA9 -- Neither --> NA12
        NA10["impl.getServiceProxy()\n→ proxy"] --> NA13
        NA11["impl.getProxy()\n→ proxy"] --> NA13
        NA12["proxy = null"] --> NA13
        NA13["Marshal + unmarshal proxy\nvia AtomicMarshalledInstance\n(preserves codebase annotation)"] --> NA14
        NA14["servicePreparer\n.prepareProxy(proxy)"] --> NA15
        NA15["return Created(impl, proxy)\nServiceStarter holds\nstrong ref to impl"]
    end

    subgraph SASD ["Phase 2b — Activatable Path (SharedActivatableServiceDescriptor.create())"]
        AC0["SharedActivationGroupDescriptor\n.create() — run first if group\nnot yet registered"] --> AC1
        AC0A["ActivationSystem.registerGroup\n(ActivationGroupDesc)\n→ ActivationGroupID\nPersist gid to sharedGroupLog"] --> AC1
        AC1["ServiceStarter\n.getActivationSystem(host, port, config)\n→ prepared ActivationSystem proxy"] --> AC2
        AC2["Build ActivateWrapper.ActivateDesc\n(implClass, importURLs, exportURLs,\n policy, configArgs)"] --> AC3
        AC3["SharedActivationGroupDescriptor\n.restoreGroupID(sharedGroupLog)\n→ ActivationGroupID"] --> AC4
        AC4["ActivateWrapper.register\n(gid, adesc, restart, sys)\n→ raw ActivationID"] --> AC5
        AC5["activationIDPreparer\n.prepareProxy(rawAid)\n→ prepared ActivationID"] --> AC6
        AC6["aid.activate(true)\n(activates the service JVM\n in the shared group)"] --> AC7
        AC7["innerProxyPreparer\n.prepareProxy(innerProxy)"] --> AC8
        AC8{"innerProxy instanceof\nServiceProxyAccessor?"} -- Yes --> AC9
        AC8 -- No --> AC10
        AC9["innerProxy.getServiceProxy()\n→ outerProxy\nservicePreparer.prepareProxy\n(outerProxy)"] --> AC11
        AC10["Use innerProxy directly"] --> AC11
        AC11["return Created\n(gid, aid, proxy)"]
        ACERR["Exception during create:\nsys.unregisterObject(aid)\nre-throw"] -.-> AC11
    end

    subgraph START ["Phase 3 — Service Startup  Startable.start()"]
        ST1{"Persistent service?\nlog != null"} -- Yes --> ST2
        ST1 -- No, fresh start --> ST3
        ST2["log.recover(classLoader)\nrestore registrations,\nevents, attributes\nfrom persistent log"] --> ST3
        ST3["Generate ServiceID if new\ncomputeMaxLeases"] --> ST4
        ST4["serverExporter.export(this)\n→ stub / remote ref"] --> ST5
        ST5["Build proxy:\nRegistrarProxy.getInstance(stub, sid)"] --> ST6
        ST6["Self-register:\naddService(SvcReg(item,\n  myLeaseID, MAX_VALUE))\nlog.snapshot()"] --> ST7
        ST7["DiscoveryGroupManagement\n  .setGroups(lookupGroups)\n DiscoveryLocatorManagement\n  .setLocators(lookupLocators)"] --> ST8
        ST8["JoinManager(proxy, attrs,\n  serviceID, discoer, null, config)\nJoin discovered lookup services\nRenew lookup-service leases"] --> ST9
        ST9["Start daemon threads:\nserviceExpirer\neventExpirer\nunicaster\nmulticaster\nannouncer\neventNotifierExec\nsnapshotter"] --> ST10
        ST10["Register JVM shutdown hook:\nannouncer.interrupt+join\n(final multicast announcement)"]
    end

    subgraph RUNTIME ["Phase 4 — Service Runtime: Lease Management"]
        LM1(["Client: register(item, leaseDuration)\nor notify(tmpl, trans, listener, hand, dur)"])
        LM1 --> LM2["Create SvcReg or EventReg\nwith leaseID and leaseExpiration\nStore in serviceByID / eventByID maps\nordered by leaseExpiration"]
        LM2 --> LM3["Return ServiceRegistration\nor EventRegistration\nwith Lease"]
        LM3 --> LM4(["Client holds Lease\nmust call lease.renew() before expiry"])

        LE1(["serviceExpirer / eventExpirer\nthread wakes when\nnext expiry is due"]) --> LE2
        LE2["Check leaseExpiration\n<= currentTimeMillis"] --> LE3
        LE3{"Expired?"} -- Yes --> LE4
        LE3 -- No, sleep until next expiry --> LE1
        LE4["Remove SvcReg or EventReg\nfrom maps\nSend MATCH_NOMATCH event\nif service reg expired"]

        LR1(["Client: lease.renew(duration)"])
        LR1 --> LR2["renewLease(serviceID, leaseID, dur)\nor renewEventLease(eventID, leaseID, dur)"]
        LR2 --> LR3["Update leaseExpiration\nin map\nlog renewal record"]

        LC1(["Client: lease.cancel()"])
        LC1 --> LC2["cancelServiceLease / cancelEventLease\nRemove registration from maps\nlog cancellation"]
    end

    subgraph DESTROY ["Phase 5 — Destroy  DestroyAdmin.destroy()"]
        DV1(["Client proxy calls\nDestroyAdmin.destroy()"])
        DV1 --> DV2["RegistrarImpl.destroy:\nacquire priorityWriteLock\n(drains in-flight calls)"]
        DV2 --> DV3{"Activatable?\nactivationID != null"} -- Yes --> DV4
        DV3 -- No --> DV5
        DV4["activationSystem\n.unregisterObject(activationID)\n(deregister from activation daemon)"] --> DV5
        DV5["Spawn non-daemon Destroy thread\nrelease write lock\nreturn to caller\n(async destroy)"] --> DV6
        DV6["Graceful unexport:\nserverExporter.unexport(false)\nwait up to 10 × 1 s for\nin-flight RMI calls to finish"] --> DV7
        DV7{"Unexported?"} -- No, still busy --> DV8
        DV7 -- Yes --> DV9
        DV8["serverExporter.unexport(true)\n(force)"] --> DV9
        DV9["Interrupt daemon threads:\nserviceExpirer, eventExpirer,\nunicaster, multicaster,\nannouncer, snapshotter\nshutdown eventNotifierExec\nshutdownNow discoveryResponseExec"] --> DV10
        DV10["joiner.terminate()\ndiscoer.terminate()"] --> DV11
        DV11["Join all interrupted threads\n(wait for clean termination)"] --> DV12
        DV12{"Persistent?\nlog != null"} -- Yes --> DV13
        DV12 -- No --> DV14
        DV13["log.deletePersistentStore()\nremove snapshot + log files"] --> DV14
        DV14{"Activatable?\nactivationID != null"} -- Yes --> DV15
        DV14 -- No --> DV16
        DV15["ActivationGroup.inactive\n(activationID, serverExporter)\nSignal activation daemon\nservice is idle"] --> DV16
        DV16{"lifeCycle != null?"} -- Yes --> DV17
        DV16 -- No --> DV18
        DV17["lifeCycle.unregister(impl)\nServiceStarter releases\nstrong ref to impl"] --> DV18
        DV18{"loginContext != null?"} -- Yes --> DV19
        DV18 -- No --> DONE2
        DV19["loginContext.logout()"] --> DONE2([Shutdown complete])
    end

    ENTRY --> NASD
    ENTRY --> SASD
    NASD --> START
    SASD --> START
    START --> RUNTIME
    RUNTIME --> DESTROY

    style ENTRY fill:#dbeafe,stroke:#1d4ed8
    style NASD fill:#dcfce7,stroke:#15803d
    style SASD fill:#fef9c3,stroke:#a16207
    style START fill:#f3e8ff,stroke:#7e22ce
    style RUNTIME fill:#e0f2fe,stroke:#0369a1
    style DESTROY fill:#fce7f3,stroke:#9d174d
```

### 1.2 Deployment Path Comparison

| Aspect | Non-activatable (`NonActivatableServiceDescriptor`) | Shared-activatable (`SharedActivatableServiceDescriptor`) |
|---|---|---|
| **JVM hosting** | Caller's JVM (ServiceStarter process) | Separate JVM in shared `ActivationGroup` |
| **Constructor signature** | `(String[], LifeCycle)` or `(Configuration, LifeCycle)` | `ActivateWrapper` constructor: `(ActivationID, MarshalledObject)` |
| **Activation system** | Not used | Required — `ActivationSystem` must be running |
| **Restart on JVM crash** | No | Yes, if `restart=true` in descriptor |
| **Proxy acquisition** | `Startable.start()` → `ServiceProxyAccessor.getServiceProxy()` or `ProxyAccessor.getProxy()` then marshal/unmarshal | `ActivationID.activate(true)` → inner proxy → optionally `ServiceProxyAccessor.getServiceProxy()` |
| **Inner proxy preparer** | N/A (single preparer) | `innerProxyPreparer` applied before `getServiceProxy()` call |
| **GC protection** | `ServiceStarter.transient_service_refs` holds strong ref | Activation daemon holds registration; no strong ref needed |
| **Deregistration** | `LifeCycle.unregister(impl)` releases strong ref | `ActivationSystem.unregisterObject(aid)` during destroy |

### 1.3 Key Interfaces and Their Roles

| Interface / Class | Role in Lifecycle |
|---|---|
| `ServiceDescriptor` | Common contract: `create(Configuration) → Object` |
| `Startable` | Defers export and thread start until after construction (JMM-safe publication) |
| `LifeCycle` | Single method `unregister(impl)`: lets the service signal ServiceStarter to release its strong reference |
| `DestroyAdmin` | Remote admin interface: `destroy()` triggers async `Destroy` thread |
| `JoinManager` | Handles multicast discovery + event registration with lookup services; renews lookup leases |
| `ServiceExpire` / `EventExpire` | Daemon threads that expire service and event leases in the lookup service's own registry |
| `ActivateWrapper` | Wraps service impl for activatable deployment; enforces per-service import/export codebase and policy |
| `SharedActivationGroupDescriptor` | Creates and persists the `ActivationGroupID` log that `SharedActivatableServiceDescriptor` reads |

---

## 2. Service Discovery Life Cycle

### 2.1 Overview Flowchart

```mermaid
flowchart TD
    subgraph DISC ["Phase 1 — Lookup Service Discovery"]
        D1([DiscoveryManagement fires\ndiscovered event]) --> D2
        D2{"useInsecureLookup\n= false?"} -- Yes, secure path --> D3
        D2 -- No, insecure path --> D4
        D3{"registrar instanceof\nSafeServiceRegistrar?"} -- No --> SKIP([Skip registrar silently])
        D3 -- Yes --> D4
        D4["registrarPreparer\n.prepareProxy(registrar)\nverify trust, apply constraints"] --> D5
        D5{"prepareProxy\nthrows?"} -- Yes --> DISC2([discMgr.discard proxy])
        D5 -- No --> D6[Wrap in ProxyReg\ncache hashCode on proxy ref]
        D6 --> D7[Add to proxyRegSet\nNotify all LookupCacheImpl\nvia cacheAddProxy]
    end

    subgraph REG ["Phase 2 — Per-Cache Event Registration & Snapshot"]
        R1[LookupCacheImpl.addProxyReg\nsubmits RegisterListenerTask] --> R2
        R2["a  registerListener:\nproxy.notify / proxy.notiFy\nreturns EventRegistration"] --> R3
        R3["eventLeasePreparer\n.prepareProxy(eventLease)"] --> R4
        R4[Hand lease to\nLeaseRenewalManager] --> R5
        R5[Suspend events\nfor this registrar] --> R6
        R6["b  cache.lookup(reg)\ninitial snapshot"]
    end

    subgraph SNAP ["Phase 3 — Snapshot & Bootstrap Proxy Processing"]
        SN1{"useInsecureLookup?"} -- Insecure --> SN2
        SN1 -- Secure --> SN3
        SN2["proxy.lookup(tmpl, MAX)\nreturns ServiceItem[]\nservice = fully unmarshalled proxy"] --> SN4
        SN3["proxy.lookUp(tmpl, MAX)\nreturns Object[]\nbootstrap proxies only\n(RemoteMethodControl + ServiceProxyAccessor)"] --> SN5
        SN5["processBootStrapProxys:\nbootstrapProxyPreparer.prepareProxy\ngetServiceAttributes\ngetServiceID\nbuild ServiceItem(id, bootstrapProxy, attrs)"] --> SN4
        SN4[newOldService for each item]
    end

    subgraph CATEG ["Phase 4 — New vs Old Categorisation"]
        C1{"ServiceID in\nserviceIdMap?"} -- No, new --> C2
        C1 -- Yes, seen before --> C3
        C2[new ServiceItemReg\nputIfAbsent in serviceIdMap\nfirst-stage filter]
        C3["itemMatchMatchChange:\nsameVersion check\n(MarshalledInstance.fullyEquals)\nconditional re-filter"]
    end

    subgraph FILT ["Phase 5 — First-Stage Filtering  filterMaybeDiscard"]
        F1[Clone ServiceItem\nfor defensive isolation] --> F2
        F2{"filter == null\nAND secure mode?"} -- Yes --> F3
        F2 -- Non-null filter --> F4
        F3["Download real proxy:\n((ServiceProxyAccessor) item.service)\n.getServiceProxy()"] --> F6
        F4["filter.check(filteredItem)\nBootstrap proxy still in item.service"] --> F5
        F5{"check result?"} -- Pass --> F6
        F5 -- SecurityException\nor ClassCastException --> F3
        F5 -- Fail --> FILT_F([Remove from serviceIdMap\nor send removed event])
        F5 -- Indefinite --> FILT_I([Mark discarded\nschedule ServiceDiscardTimerTask\nafter discardWait ms])
        F6["PostEventState.apply\ncomputeIfPresent:\nitemReg.setFilteredItem(filteredItem)\nitemReg.replaceProxy(null, item)"]
    end

    subgraph CACHE ["Phase 6 — Cache Storage in ServiceItemReg"]
        K1["serviceIdMap: ConcurrentMap\n  ServiceID → ServiceItemReg"]
        K2["ServiceItemReg:\n  items: Map(registrar → raw ServiceItem)\n  proxy: tracking ServiceRegistrar\n  item: current raw item\n  filteredItem: prepared item (defensive clone)\n  bDiscarded: boolean"]
        K1 --- K2
    end

    subgraph EVT ["Phase 7 — Incoming ServiceEvent Handling"]
        E1["LookupListener.notify (RMI thread)\noffload immediately to\nHandleServiceEventTask"] --> E2
        E2{"Secure mode?"} -- Yes --> E3
        E2 -- No --> E4
        E3["event.getBootstrapProxy\nbootstrapProxyPreparer.prepareProxy"] --> E4
        E4["Correlate source + eventID\n→ EventReg in eventRegMap"] --> E5
        E5{"Event ordering:\ndelta = seqNo - lastSeqNo"} -- delta == 1, in order --> E6
        E5 -- delta > 1, gap --> E7
        E5 -- delta ≤ 0, stale --> STALE([Ignore])
        E6["notifyServiceMap:\nhandleMatchNoMatch / newOldService"] --> CATEG
        E7["Full lookup(reg)\nsnapshot re-sync"] --> SNAP
    end

    subgraph LOOK ["Phase 8 — Second-Stage Filtering at Lookup Time"]
        L1["LookupCache.lookup(filter2)\nor LookupCache.lookup(filter2, max)"] --> L2
        L2["getServiceItems(filter2)\niterates serviceIdMap.forEach"] --> L3
        L3["Skip discarded items\nClone filteredItem"] --> L4
        L4{"filter2 == null?"} -- Yes --> L5([Return clone])
        L4 -- No --> L6["filter2.check(clone)"]
        L6{"Result?"} -- Pass with non-null service --> L5
        L6 -- Indefinite\nitem.service == null --> L7(["computeIfPresent: Discard\n(schedules retry)"])
        L6 -- Fail --> L8([Skip item])
    end

    subgraph LEASE ["Lease & Discard Management"]
        LD1([LeaseRenewalManager\rrenewal failure]) --> LD2
        LD2["LeaseListenerImpl.notify\nfail → discMgr.discard(proxy)\nDiscMgrListener.discarded\nremoveProxyReg → ProxyRegDropTask"] --> LD3
        LD3[Remove all services\nfrom that registrar\nfrom serviceIdMap]

        LD4(["client: LookupCache.discard(svcRef)"]) --> LD5
        LD5["Discard.apply\nbDiscarded = true\nschedule ServiceDiscardTimerTask"] --> LD6
        LD6{"MATCH_NOMATCH event\narrives during wait?"} -- Yes --> LD7([DissociateLus cancelDiscardTask cleanup])
        LD6 -- No, timer fires --> LD8["Un-discard\nre-filter raw item\nfire serviceAdded if still registered"]
    end

    DISC --> REG
    REG --> SNAP
    SNAP --> CATEG
    CATEG --> FILT
    FILT --> CACHE
    EVT --> CATEG
    CACHE --> LOOK
    CACHE --> LEASE

    style DISC fill:#dbeafe,stroke:#1d4ed8
    style REG fill:#dcfce7,stroke:#15803d
    style SNAP fill:#fef9c3,stroke:#a16207
    style CATEG fill:#ffe4e6,stroke:#be123c
    style FILT fill:#f3e8ff,stroke:#7e22ce
    style CACHE fill:#e0f2fe,stroke:#0369a1
    style EVT fill:#fef3c7,stroke:#d97706
    style LOOK fill:#d1fae5,stroke:#047857
    style LEASE fill:#fce7f3,stroke:#9d174d
```

### 2.2 Extension Points Quick Reference

| Lifecycle Point | Class / File | Extension Interface | Isolation Granularity |
|---|---|---|---|
| Registrar discovered | `ServiceDiscoveryManager` L875–876 | `ProxyPreparer` (`registrarPreparer`) | Per lookup service |
| Event lease prepared | `ServiceDiscoveryManager` L2258 | `ProxyPreparer` (`eventLeasePreparer`) | Per registration |
| Bootstrap proxy prepared (snapshot) | `LookupCacheImpl` L854–869 | `ProxyPreparer` (`bootstrapProxyPreparer`) | Per service item |
| Bootstrap proxy prepared (event) | `LookupCacheImpl` L1020 | Same `bootstrapProxyPreparer` | Per service event |
| Service proxy downloaded (no filter) | `LookupCacheImpl` L2116 | `ServiceProxyAccessor.getServiceProxy()` | Per service item |
| First-stage filter applied | `LookupCacheImpl` L2129, 2132 | `ServiceItemFilter` | Per service item (cached) |
| Filter retry on indefinite | `LookupCacheImpl` L493–558 | Same `ServiceItemFilter` | Per service item |
| Second-stage filter at cache lookup | `LookupCacheImpl` L827 | `ServiceItemFilter` (per-call) | Per call (isolated instance) |
| SDM direct lookup filter | `ServiceDiscoveryManager` L1760, 2044 | `ServiceItemFilter` | Per call (not cached) |
| Client discard | `LookupCacheImpl` L724–773 | — | Per service item |
| Registrar dropped | `LookupCacheImpl` L926–945 | — | All services from that registrar |

---

## 3. ProxyCodebaseSpi Life Cycle

### 3.1 Overview Flowchart

```mermaid
flowchart TD
    subgraph SERVER ["Phase 1 — Export / record  AtomicILFactory"]
        S1["Service impl created\nService implements CodebaseAccessor\n± DynamicProxyCodebaseAccessor"] --> S2
        S2["BasicJeriExporter.export\nAtomicILFactory.createInstances\ncreates dynamic Proxy with\nAtomicInvocationHandler"] --> S3
        S3{"proxy instanceof\nCodebaseAccessor?"} -- Yes --> S4
        S3 -- No --> S5([Proxy exported\nno codebase caching])
        S4["ProxyCodebaseSpi discovered via\nService.providers(ProxyCodebaseSpi.class, loader)"] --> S6
        S6["provider.record(\n  service: CodebaseAccessor,\n  handler: InvocationHandler,\n  loader: ClassLoader\n)"] --> S7
        S7[("SERVICES_EXP cache\nKey = (handler, codebase[ ], null)\nSTRONG key / WEAK ClassLoader value\n60 s TTL\nSupports boomerang proxy")]
    end

    subgraph SERIAL ["Phase 2 — Serialisation  AtomicMarshalOutputStream"]
        W1["writeObject on\nProxyAccessor or\nDynamicProxyCodebaseAccessor"] --> W2
        W2["ProxySerializer.create(\n  proxy, streamLoader, context\n)"] --> W3
        W3["ProxyCodebaseSpi.substitute(\n  serviceClass, streamLoader\n)\nIs class invisible at remote end?"] --> W4
        W4{"substitute\nreturns true?"} -- No, class locally visible --> W5([Write proxy as-is\nno substitution])
        W4 -- Yes, needs codebase download --> W6
        W6["Narrow proxy interfaces to\n[CodebaseAccessor, RemoteMethodControl]\nusing same InvocationHandler\n→ bootstrapProxy"] --> W7
        W7["Marshal full service proxy\ninto AtomicMarshalledInstance\n→ serviceProxy"] --> W8
        W8["Write ProxySerializer to stream:\n{ bootstrapProxy, serviceProxy }"]
    end

    subgraph DESER ["Phase 3 — SPI Discovery  AtomicMarshalInputStream readResolve"]
        R1["Stream contains ProxySerializer\nfields populated by AtomicSerial"] --> R2
        R2["ProxySerializer.readResolve\ncalled after field validation"] --> R3
        R3["Service.providers(\n  ProxyCodebaseSpi.class,\n  defaultLoader\n)\nMETA-INF/services SPI lookup"] --> R4
        R4{"Provider\nfound?"} -- No --> R5
        R4 -- Yes --> R6
        R5["Default no-op provider:\nsmartProxy.get(parentLoader …)\nno codebase download, no isolation"]
        R6["provider.resolve(\n  bootstrapProxy,\n  serviceProxy: MarshalledInstance,\n  parentLoader, verifierLoader,\n  context: ObjectStreamContext\n)"]
    end

    subgraph RESOLVE ["Phase 4 — ClassLoader Provisioning  inside resolve"]
        L1["Extract from ObjectStreamContext:\nMethodConstraints mc\nIntegrityEnforcement ie"] --> L2
        L2["Apply client constraints:\n(RemoteMethodControl)\n  .setConstraints(mc)\nMinPrincipal auth happens here"] --> L3
        L3["bootstrapProxy\n  .getClassAnnotation()\n→ codebase URL string\n(remote call over authenticated channel)"] --> L4
        L4["Build lookup Key =\n(InvocationHandler, codebase[ ], null)"] --> L5
        L5{"SERVICES_EXP\ncontains Key?\nBoomerang proxy?"}
        L5 -- Yes, same export node --> L6[Use export ClassLoader\nno download needed]
        L5 -- No --> L7
        L7["loaderAnnotation =\nPreferredClassProvider\n  .getLoaderAnnotation(parent)"] --> L8
        L8{"loaderAnnotation\n== codebase?\nSelf-unmarshal?"} -- Yes --> L9[Use parent ClassLoader]
        L8 -- No --> L10
        L10["Build CACHE Key =\n(InvocationHandler, codebase[ ], parent)"] --> L11
        L11{"CACHE\ncontains Key?"} -- Yes, seen before --> L12[Use cached ClassLoader]
        L11 -- No, first time --> L13
        L13{"Certificates\npresent in\nbootstrapProxy\n.getEncodedCerts?"} -- Yes --> L14
        L13 -- No, integrity enforced --> L15
        L13 -- No integrity required --> L16
        L14["Verify JAR signature:\nCertificateFactory from encoded certs\nRead full JAR via JarURLConnection\nmatch actual certs vs required certs"] --> L16
        L15["Security\n  .verifyCodebaseIntegrity(\n    path, verifierLoader\n  )"] --> L16
        L16["Create new ClassLoader:\nPreferredClassLoader(codebase[], parent)\nOR BundleDelegatingClassLoader\n(OSGi: bc.installBundle(codebase[0]))"] --> L17
        L17["CACHE.putIfAbsent(key, loader)\nSTRONG key / WEAK value / 60 s TTL\nrace-safe: if existed use winner"]
    end

    subgraph HYDRATE ["Phase 5 — Proxy Hydration"]
        H1["serviceProxy.get(\n  loader, integrity=true,\n  verifierLoader, context\n)\nunmarshal smart proxy class\nusing provisioned ClassLoader"] --> H2
        H2{"MethodConstraints\nin context?"} -- No --> DONE
        H2 -- Yes --> H3
        H3["Merge constraints:\nStringMethodConstraints.combine(\n  proxy existing constraints,\n  client mc\n)"] --> H4
        H4["(RemoteMethodControl)\n  .setConstraints(mergedMc)\nReturn fully constrained proxy"] --> DONE([Typed smart proxy\nreturned to caller])
    end

    subgraph GC ["Phase 6 — ClassLoader Cache Eviction"]
        G1([Proxy / caller GC'd\nClassLoader weakly reachable]) --> G2
        G2["RC.concurrentMap TTL expires\nor weak reference cleared"] --> G3
        G3[Entry evicted from CACHE] --> G4([Next resolve for same proxy\ncreates fresh ClassLoader\nand re-downloads if needed])
    end

    SERVER --> SERIAL
    SERIAL --> DESER
    DESER --> RESOLVE
    RESOLVE --> HYDRATE
    HYDRATE --> GC

    L6 --> HYDRATE
    L9 --> HYDRATE
    L12 --> HYDRATE
    L17 --> HYDRATE
    R5 --> DONE2([Proxy resolved without\ncodebase isolation])

    style SERVER fill:#dbeafe,stroke:#1d4ed8
    style SERIAL fill:#dcfce7,stroke:#15803d
    style DESER fill:#fef9c3,stroke:#a16207
    style RESOLVE fill:#ffe4e6,stroke:#be123c
    style HYDRATE fill:#f3e8ff,stroke:#7e22ce
    style GC fill:#e0f2fe,stroke:#0369a1
```

### 3.2 ClassLoader Resolution Decision Tree

```mermaid
flowchart LR
    START([resolve called]) --> A
    A{"SERVICES_EXP\nhit?\nBoomerang?"} -- Yes --> USE_EXP[Use export\nClassLoader]
    A -- No --> B
    B{"Parent loader\nannotation ==\ncodebase?\nSelf-unmarshal?"} -- Yes --> USE_PARENT[Use parent\nClassLoader]
    B -- No --> C
    C{"CACHE hit?\nPreviously\nseen?"} -- Yes --> USE_CACHE[Use cached\nClassLoader]
    C -- No --> D
    D{"Encoded\ncerts\npresent?"} -- Yes --> VERIFY_CERT[Verify JAR\nsignatures]
    D -- No, integrity\nenforced --> VERIFY_URL[Security\n.verifyCodebase\nIntegrity]
    D -- No integrity\nrequired --> CREATE
    VERIFY_CERT --> CREATE
    VERIFY_URL --> CREATE
    CREATE[Create new\nClassLoader] --> PUT[CACHE.putIfAbsent\nweak value / 60 s TTL]
    PUT --> USE_NEW[Use new\nClassLoader]

    USE_EXP --> UNMARSHAL([serviceProxy.get\nloader, …])
    USE_PARENT --> UNMARSHAL
    USE_CACHE --> UNMARSHAL
    USE_NEW --> UNMARSHAL

    style USE_EXP fill:#bbf7d0,stroke:#15803d
    style USE_PARENT fill:#bbf7d0,stroke:#15803d
    style USE_CACHE fill:#bbf7d0,stroke:#15803d
    style USE_NEW fill:#bfdbfe,stroke:#1d4ed8
    style UNMARSHAL fill:#fde68a,stroke:#d97706
```

### 3.3 Phase Summary

| Phase | Key classes | Purpose |
|---|---|---|
| **① Export / record** | `AtomicILFactory.createInstances`, `ProxyCodebaseSpi.record` | Stores `(InvocationHandler, codebase) → ClassLoader` in `SERVICES_EXP` to support boomerang proxies returning to their export node. Not idempotent — throws `ExportException` if called twice. |
| **② Serialisation / substitute** | `AtomicMarshalOutputStream`, `ProxySerializer.create`, `ProxyCodebaseSpi.substitute` | Decides whether the proxy class is invisible at the remote end. If so, replaces it with a `ProxySerializer` holding a narrowed *bootstrapProxy* (`[CodebaseAccessor, RemoteMethodControl]`) and the full proxy inside an `AtomicMarshalledInstance`. |
| **③ SPI discovery** | `ProxySerializer.readResolve`, `Service.providers` | Locates the active `ProxyCodebaseSpi` implementation via `META-INF/services`. Falls back to a no-op default (no isolation, no download). |
| **④ Constraint application** | `RemoteMethodControl.setConstraints`, stream context | Client-side `MethodConstraints` and `IntegrityEnforcement` extracted from `ObjectStreamContext`, applied to `bootstrapProxy` before any remote calls (MinPrincipal authentication enforced here). |
| **⑤ ClassLoader provisioning** | `SERVICES_EXP`, `CACHE`, parent annotation check | Four-path priority: boomerang → self-unmarshal → cached → new. New loaders are verified before creation. |
| **⑥ Codebase integrity** | `Security.verifyCodebaseIntegrity`, `CertificateFactory`, `JarURLConnection` | URL-based or certificate-based JAR signature verification, depending on whether `bootstrapProxy.getEncodedCerts()` returns data. |
| **⑦ ClassLoader creation** | `PreferredClassLoader` (non-OSGi), `BundleDelegatingClassLoader` (OSGi) | New loader created as child of stream's parent loader. OSGi path installs a bundle. Stored with `putIfAbsent` to handle concurrent races safely. |
| **⑧ Proxy hydration** | `MarshalledInstance.get`, `RemoteMethodControl.setConstraints` | Smart proxy unmarshalled using provisioned `ClassLoader`. Client constraints merged with proxy's existing constraints via `StringMethodConstraints.combine()`. |
| **⑨ Cache eviction** | `RC.concurrentMap`, weak references, 60 s TTL | `ClassLoader` values held via weak references + 60 s expiry. On eviction the next `resolve()` recreates the loader. |

---

## 4. How the Three Life Cycles Connect

The **Service Start / Runtime / Shutdown** life cycle produces the running service and keeps it
registered in lookup services via `JoinManager`. The **Service Discovery** life cycle detects that
registration from the client side. The **ProxyCodebaseSpi** life cycle handles the class-loading of
the downloaded smart proxy.

```mermaid
flowchart LR
    subgraph SVCSTART ["Service Start Life Cycle"]
        SS1[Startable.start:\nexport + JoinManager] --> SS2
        SS2[JoinManager joins\nlookup services\nregisters ServiceItem]
    end

    subgraph SDM ["Service Discovery Life Cycle"]
        SD1[Registrar discovered] --> SD2
        SD2[Bootstrap proxy\nprepared] --> SD3
        SD3["ServiceItemFilter.check\nfirst stage"] --> SD4
        SD4["getServiceProxy\ndownloads ProxySerializer\nfrom stream"] --> SD5
        SD5["filter stores\nprepared proxy\nin filteredItem"] --> SD6
        SD6["LookupCache.lookup\nsecond-stage filter\nper-caller clone"]
    end

    subgraph SPI ["ProxyCodebaseSpi Life Cycle"]
        SP1["ProxySerializer\nin stream"] --> SP2
        SP2["readResolve calls\nprovider.resolve"] --> SP3
        SP3["ClassLoader provisioned\nor boomerang / cached"] --> SP4
        SP4["serviceProxy.get(loader)\nunmarshals typed proxy"] --> SP5
        SP5[Constraints merged\nproxy returned]
    end

    SS2 -- "Service appears in\nlookup service registry" --> SD1
    SD4 -- "AtomicMarshalInputStream\ndeserialises ProxySerializer" --> SP1
    SP5 -- "Typed proxy\nhanded back to filter\nas item.service" --> SD5

    style SVCSTART fill:#fef9c3,stroke:#a16207
    style SDM fill:#dbeafe,stroke:#1d4ed8
    style SPI fill:#dcfce7,stroke:#15803d
```

The three connection points are:

1. `JoinManager` (Phase 3 of the Start life cycle) registers the service's proxy in discovered
   lookup services. This is what the client-side `ServiceDiscoveryManager` observes as a new
   service (Phase 1 of the Discovery life cycle).

2. `ServiceItemFilter.check()` (Phase 5 of the Discovery life cycle) drives the proxy download by
   calling `((ServiceProxyAccessor) item.service).getServiceProxy()` — a remote call to the
   bootstrap proxy's authenticated endpoint.

3. The returned stream contains a `ProxySerializer` object. `AtomicMarshalInputStream` calls its
   `readResolve`, which delegates to the active `ProxyCodebaseSpi.resolve` (Phase 3 of the
   ProxyCodebaseSpi life cycle). The SPI provisions the correct `ClassLoader`, unmarshals the
   smart proxy, merges client constraints, and returns the fully typed, constrained proxy.
   The filter stores it as `filteredItem` and returns pass.
