# Service and Proxy Life Cycles

This document illustrates two complete, interlocking life cycles in JGDMS:

1. **[Service Discovery Life Cycle](#1-service-discovery-life-cycle)** — how a service moves from
   registration in a lookup service through discovery, bootstrap preparation, filtering, and caching
   inside `ServiceDiscoveryManager` and `LookupCacheImpl`.

2. **[ProxyCodebaseSpi Life Cycle](#2-proxycodebasespi-life-cycle)** — how a smart proxy's codebase
   is recorded at export time, serialised into a stream, and resolved back into a typed object on the
   client side with per-service `ClassLoader` isolation.

---

## 1. Service Discovery Life Cycle

### 1.1 Overview Flowchart

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

### 1.2 Extension Points Quick Reference

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

## 2. ProxyCodebaseSpi Life Cycle

### 2.1 Overview Flowchart

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

### 2.2 ClassLoader Resolution Decision Tree

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

### 2.3 Phase Summary

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

## 3. How the Two Life Cycles Connect

The two life cycles meet at **Phase 3 of the Service Discovery life cycle** (Snapshot / Bootstrap
Proxy Processing) and at **Phase 5 / Filter download** (`ServiceProxyAccessor.getServiceProxy()`).

```mermaid
flowchart LR
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

    SD4 -- "AtomicMarshalInputStream\ndeserialises ProxySerializer" --> SP1
    SP5 -- "Typed proxy\nhanded back to filter\nas item.service" --> SD5

    style SDM fill:#dbeafe,stroke:#1d4ed8
    style SPI fill:#dcfce7,stroke:#15803d
```

The `ServiceItemFilter` is responsible for driving the download:

1. It calls `((ServiceProxyAccessor) item.service).getServiceProxy()` (a remote call to the
   bootstrap proxy's endpoint).
2. The result is a serialised stream that `AtomicMarshalInputStream` deserialises.
3. `AtomicMarshalInputStream` encounters a `ProxySerializer` object and calls its `readResolve`.
4. `readResolve` delegates to the active `ProxyCodebaseSpi.resolve`, which provisions the correct
   `ClassLoader` and returns the fully typed, constrained smart proxy.
5. The filter replaces `item.service` with this proxy and returns pass.
6. The prepared proxy is stored as `filteredItem` and returned to callers of `LookupCache.lookup`.
