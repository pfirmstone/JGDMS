# Proxy Isolation Analysis: ServiceDiscoveryManager and LookupCacheImpl

This document traces the complete proxy discovery, loading, caching, and preparation lifecycle in
`ServiceDiscoveryManager` (SDM) and `LookupCacheImpl` (LCI). It maps all extension points where
bytecode analysis or proxy isolation logic could be inserted, identifies where decisions about
per-proxy isolation can be made, and analyses the current caching strategy and lease management.

All file references use paths relative to `JGDMS/jgdms-lib-dl/src/main/java/net/jini/lookup/`.

---

## 1. Phase-by-Phase Lifecycle Trace

### Phase 1 — Lookup Service (Registrar) Discovery

The entry point is `DiscMgrListener`, a private inner class of `ServiceDiscoveryManager` that
implements `DiscoveryListener` and is registered at construction time
(`ServiceDiscoveryManager.java:1213`):

```java
discMgr.addDiscoveryListener(discMgrListener);
```

When `DiscoveryManagement` fires a discovery event, `DiscMgrListener.discovered()`
(`ServiceDiscoveryManager.java:868`) iterates over the newly discovered `ServiceRegistrar[]` proxies.
Two important gatekeeping decisions happen here:

1. **Secure-mode gate** (`ServiceDiscoveryManager.java:872–873`): When `useInsecureLookup` is false,
   any registrar that is not an instance of `SafeServiceRegistrar` is silently skipped. This is the
   very first per-proxy branching point.

2. **Registrar proxy preparation** (`ServiceDiscoveryManager.java:875–876`):
   ```java
   proxys[i] = (ServiceRegistrar) registrarPreparer.prepareProxy(proxys[i]);
   ```
   `registrarPreparer` is a `ProxyPreparer` fetched from `Configuration` during `init()`
   (`ServiceDiscoveryManager.java:2349–2352`). Preparation may verify trust, apply method
   constraints, and dynamically grant permissions. If preparation throws, the proxy is discarded
   via `discMgr.discard()` (`ServiceDiscoveryManager.java:889`).

After preparation the proxy is wrapped in a `ProxyReg` (`ServiceDiscoveryManager.java:892`) — a
thin wrapper (`ProxyReg.java`) that computes and caches `hashCode` based on the proxy reference —
and added to `proxyRegSet` (`ServiceDiscoveryManager.java:895–900`). All existing `LookupCacheImpl`
instances then receive the new registrar via `cacheAddProxy(reg)` → `cache.addProxyReg(reg)`
(`ServiceDiscoveryManager.java:901`, `LookupCacheImpl.java:915–917`).

---

### Phase 2 — Per-Cache Event Registration and Initial Snapshot

`LookupCacheImpl.addProxyReg(ProxyReg reg)` (`LookupCacheImpl.java:915`) immediately submits a
`RegisterListenerTask` to `CacheTaskDependencyManager`, which maintains a DAG of pending tasks to
enforce ordering (a `RegisterListenerTask` must complete before a `ProxyRegDropTask` for the same
`ProxyReg`).

`RegisterListenerTask.run()` (`LookupCacheImpl.java:279–337`) executes two sub-operations in strict
order:

#### a) Event registration

Called via `cache.sdm.registerListener(reg.getProxy(), cache.tmpl, cache.lookupListenerProxy, duration)`
(`LookupCacheImpl.java:290–295`).

Inside `registerListener()` (`ServiceDiscoveryManager.java:2234–2273`):

- Registers with the lookup service: `proxy.notify(...)` (insecure) or `proxy.notiFy(...)`
  (secure, `SafeServiceRegistrar.notiFy` — `ServiceDiscoveryManager.java:2246`).
- **Event lease preparation**: The lease returned from `EventRegistration` is immediately prepared
  (`ServiceDiscoveryManager.java:2258`):
  ```java
  eventLease = (Lease) eventLeasePreparer.prepareProxy(eventLease);
  ```
  `eventLeasePreparer` is configured as
  `net.jini.lookup.ServiceDiscoveryManager/eventLeasePreparer`
  (`ServiceDiscoveryManager.java:2353–2356`). The prepared lease is handed to
  `LeaseRenewalManager` (`ServiceDiscoveryManager.java:2265–2267`) to keep it alive, supervised
  by a `LeaseListenerImpl` that discards the registrar on renewal failure.
- Returns an `EventReg` (`ServiceDiscoveryManager.java:2269–2272`) encapsulating source, event ID,
  initial sequence number, and lease — stored in
  `eventRegMap: ConcurrentMap<ProxyReg, EventReg>` (`LookupCacheImpl.java:138`).

Events are suspended (`eventReg.suspendEvents()`, `LookupCacheImpl.java:305`) during the window
between registration and snapshot retrieval to prevent race conditions.

#### b) Initial snapshot lookup

Called via `cache.lookup(reg)` (`LookupCacheImpl.java:313`).

---

### Phase 3 — Snapshot Lookup and Bootstrap Proxy Processing

`LookupCacheImpl.lookup(ProxyReg reg)` (`LookupCacheImpl.java:1261–1324`) queries the registrar for
a snapshot:

- **Insecure path** (`LookupCacheImpl.java:1267–1268`): `proxy.lookup(tmpl, Integer.MAX_VALUE)`
  returns `ServiceMatches` with `ServiceItem[]`, each `item.service` is the already-unmarshalled
  service proxy.
- **Secure path** (`LookupCacheImpl.java:1270–1272`):
  `((SafeServiceRegistrar) proxy).lookUp(tmpl, Integer.MAX_VALUE)` returns `Object[]` of
  **bootstrap proxies** — lightweight authenticated stubs implementing `RemoteMethodControl`,
  `ServiceProxyAccessor`, and `ServiceAttributesAccessor`. The actual smart proxy (which may
  require code download) is NOT downloaded yet. These are passed to `processBootStrapProxys()`
  (`LookupCacheImpl.java:854–869`).

`processBootStrapProxys()` (`LookupCacheImpl.java:854`):
```java
bootstrap = sdm.bootstrapProxyPreparer.prepareProxy(proxys[i]);  // L862
attributes = ((ServiceAttributesAccessor) bootstrap).getServiceAttributes();  // L863
id = ((ServiceIDAccessor) bootstrap).serviceID();  // L864
result.add(new ServiceItem(id, bootstrap, attributes));  // L865
```
The `bootstrapProxyPreparer` — configured as
`net.jini.lookup.ServiceDiscoveryManager/bootstrapPreparer`
(`ServiceDiscoveryManager.java:2357–2360`) — prepares each bootstrap proxy. Crucially, the
resulting `ServiceItem.service` field holds **the prepared bootstrap proxy**, not the final
service proxy. Proxy download is deliberately deferred to after filtering.

---

### Phase 4 — New vs. Old Service Categorisation

`LookupCacheImpl.lookup()` iterates items and calls
`newOldService(reg, items[i].serviceID, items[i], false)` (`LookupCacheImpl.java:1323`).

`newOldService()` (`LookupCacheImpl.java:1353–1440`) checks `serviceIdMap` for an existing
`ServiceItemReg` with the given `ServiceID`:

- **Not previously seen**: creates `new ServiceItemReg(reg.getProxy(), item)`
  (`LookupCacheImpl.java:1377`) and inserts with `putIfAbsent`. If a concurrent insertion won the
  race, uses the winner and treats as old. If insertion succeeded, proceeds to first-stage filtering
  on the new item.
- **Previously seen**: routes to `itemMatchMatchChange()` (`LookupCacheImpl.java:1421`) which
  computes version equality via `sameVersion()` (`LookupCacheImpl.java:1704–1728`) and attribute
  equality, then conditionally re-filters.

`sameVersion()` uses `MarshalledInstance.fullyEquals()` including codebase comparison, with special
normalisation for `RemoteMethodControl` proxies (constraints are equalised before marshalling,
`LookupCacheImpl.java:1712–1714`).

---

### Phase 5 — First-Stage Filtering (`filterMaybeDiscard`)

`filterMaybeDiscard(ServiceID srvcID, ServiceItemReg itemReg, ServiceItem item, boolean sendEvent)`
(`LookupCacheImpl.java:2088`) is the central proxy transformation stage.

A defensive clone is made first (`LookupCacheImpl.java:2106`):
```java
ServiceItem filteredItem = item.clone();
```

**Null filter, secure mode** (`LookupCacheImpl.java:2109–2126`): The actual service proxy is
downloaded:
```java
filteredItem.service = ((ServiceProxyAccessor) filteredItem.service).getServiceProxy();
```
(`LookupCacheImpl.java:2116`). This is the only place where proxy download happens automatically
without a filter.

**Non-null filter** (`LookupCacheImpl.java:2127–2169`):

- Insecure path: `filter.check(filteredItem)` directly.
- Secure path: `filter.check(filteredItem)` with `filteredItem.service` still holding the bootstrap
  proxy. If the filter throws `SecurityException` or `ClassCastException` (older filter trying to
  cast the bootstrap proxy to the service type), the code automatically downloads the real proxy via
  `getServiceProxy()` (`LookupCacheImpl.java:2136–2158`) and retries. This fall-through exists to
  support legacy filters that do not understand bootstrap proxies.

The outcome of filtering is applied atomically to `serviceIdMap` via `PostEventState.apply()`
(`LookupCacheImpl.java:2218–2273`), a `BiFunction` passed to `computeIfPresent`. Inside this atomic
block:

- **Pass**: `itemReg.replaceProxyUsedToTrackChange(null, item)` and
  `itemReg.setFilteredItem(filteredItem)` store the prepared proxy.
- **Fail**: removes the entry from `serviceIdMap` (or sends removed event if this was a re-filter).
- **Indefinite** (`discardRetryLater = true`): marks item as discarded, schedules a
  `ServiceDiscardTimerTask` in `serviceDiscardTimerTaskMgr` after `discardWait` ms to retry the
  filter (`LookupCacheImpl.java:2260–2268`).

---

### Phase 6 — Cache Storage in `ServiceItemReg`

`ServiceItemReg` (`ServiceItemReg.java`) stores state per `ServiceID`:

| Field | Type | Purpose |
|-------|------|---------|
| `items` | `Map<ServiceRegistrar, ServiceItem>` (L32) | Maps each registrar to the raw item it contributed. Supports multiple registrars registering the same service. |
| `proxy` | `ServiceRegistrar` (L34) | The registrar currently used as the canonical change-tracking source. |
| `item` | `ServiceItem` (L38) | The current raw (pre-filter) item. |
| `filteredItem` | `ServiceItem` (L40) | The post-filter (post-preparation) item. |

`setFilteredItem()` (L142–144) and `getFilteredItem()` (L136–137) both perform defensive clones, so
external mutations of returned items do not corrupt cached state.

---

### Phase 7 — ServiceEvent Handling

Incoming events arrive at `LookupListener.notify()` (`LookupCacheImpl.java:208–213`) via RMI. The
remote call is immediately offloaded to
`incomingEventExecutor.submit(new HandleServiceEventTask(this, theEvent))`
(`LookupCacheImpl.java:973`), freeing the RMI thread.

`HandleServiceEventTask.run()` (`LookupCacheImpl.java:1002–1135`):

- **Secure mode**: extracts bootstrap proxy via `theEvent.getBootstrapProxy()`
  (`LookupCacheImpl.java:1016`), then prepares it:
  `cache.sdm.bootstrapProxyPreparer.prepareProxy(proxy)` (`LookupCacheImpl.java:1020`).
- Finds the matching `EventReg` in `eventRegMap` via source/eventID correlation
  (`LookupCacheImpl.java:1043–1062`).
- Manages event ordering: events that arrive out-of-order wait up to 500 ms for contiguous
  predecessor events (`LookupCacheImpl.java:1075–1113`). Non-contiguous events resubmit themselves.
- Acquires `eReg` monitor to suspend further event processing for this registrar while updating
  state (`LookupCacheImpl.java:1101`, `1117–1127`).
- Calls `notifyServiceMap(delta, sid, item, transition, reg)` (`LookupCacheImpl.java:1182–1255`):
  - `delta == 1`: no gap, processes single event via `handleMatchNoMatch`, `newOldService`.
  - `delta > 1`: gap detected, triggers full `lookup(reg)` snapshot.
  - `delta <= 0`: stale/duplicate event, ignored.

---

### Phase 8 — Second-Stage Filtering at Lookup Time

`LookupCacheImpl.lookup(ServiceItemFilter myFilter)` (`LookupCacheImpl.java:687–697`) and the array
variant (`LookupCacheImpl.java:701–720`) both call `getServiceItems(myFilter)`
(`LookupCacheImpl.java:799`).

`getServiceItems()` iterates `serviceIdMap` via `serviceIdMap.forEach(items)` with a `FilteredItems`
`BiConsumer` (`LookupCacheImpl.java:805`). For each entry:

- Skips discarded items (`LookupCacheImpl.java:820`).
- Clones `filteredItem` (`LookupCacheImpl.java:825`): `itemToFilter = itemToFilter.clone()`.
- Applies the second-stage filter:
  `(filter2 == null) || (filter2.check(itemToFilter))` (`LookupCacheImpl.java:827`).
- Indefinite result (pass=true, `itemToFilter.service == null`): triggers a `Discard` via
  `computeIfPresent` (`LookupCacheImpl.java:836–843`).

---

## 2. Extension Points Map

### A. `ServiceItemFilter` — Four Invocation Sites

| Site | Location | When | Notes |
|------|----------|------|-------|
| First-stage (cache-level) | `LookupCacheImpl.java:2129`, `2132` (`filterMaybeDiscard`) | Every new/changed service event and snapshot | Stored in `LookupCacheImpl.filter` for cache lifetime |
| First-stage retry | `LookupCacheImpl.java:493–558` (`ServiceDiscardTimerTask`) | After `discardWait` ms when indefinite | Re-applies to same raw item |
| Second-stage (caller-level) | `LookupCacheImpl.java:827` (`FilteredItems.accept`) | Every `LookupCache.lookup(filter)` call | Applied to already-filtered `filteredItem` clones |
| SDM non-blocking direct | `ServiceDiscoveryManager.java:1760`, `2044` | Every direct `SDM.lookup()` call | Applied to bootstrap proxy or full proxy depending on mode |

The `ServiceItemFilter.check(item)` contract (`ServiceItemFilter.java:99`): the filter MAY replace
`item.service` with a prepared (or isolated) proxy. This is the **canonical preparation hook**:
calling `ProxyPreparer.prepareProxy()` inside `check()` is the spec-recommended way to do proxy
preparation.

### B. `ProxyPreparer` — Three Distinct Preparers

| Preparer | Config Key | Applied at | Proxy type prepared |
|----------|-----------|------------|---------------------|
| `registrarPreparer` | `registrarPreparer` | `DiscMgrListener.discovered()` (`ServiceDiscoveryManager.java:876`) | `ServiceRegistrar` proxy |
| `eventLeasePreparer` | `eventLeasePreparer` | `SDM.registerListener()` (`ServiceDiscoveryManager.java:2258`) | `Lease` proxy from event registration |
| `bootstrapProxyPreparer` | `bootstrapPreparer` | `processBootStrapProxys()` (`LookupCacheImpl.java:862`), `HandleServiceEventTask.run()` (`LookupCacheImpl.java:1020`), `SDM.check()` (`ServiceDiscoveryManager.java:2028`) | Bootstrap proxy (implements `ServiceProxyAccessor`, `ServiceAttributesAccessor`) |

Note that there is **no dedicated `ProxyPreparer` for the final service proxy** — that
responsibility is delegated entirely to `ServiceItemFilter.check()`. The filter is expected to call
`ServiceProxyAccessor.getServiceProxy()` itself (downloading the proxy) and then prepare it.

### C. Configuration-Injected Executors (`initCache()`, `LookupCacheImpl.java:1866–2009`)

All executor services are configurable via `Configuration`, enabling replacement with custom
implementations:

| Config key | Default | Purpose |
|-----------|---------|---------|
| `eventListenerExporter` | `BasicJeriExporter` with `AtomicILFactory` (L1871–1876) | Exports `LookupListener` for remote event delivery |
| `eventNotificationExecutor` | Single-thread `ThreadPoolExecutor` (L1900–1906) | Executes `ServiceNotifyDo` for local `ServiceDiscoveryListener` callbacks |
| `cacheExecutorService` | 3-thread `ThreadPoolExecutor` (L1919–1923) | Runs `RegisterListenerTask`, `ProxyRegDropTask`, etc. |
| `discardExecutorService` | 4-thread `ScheduledThreadPoolExecutor` (L1958–1962) | Runs `ServiceDiscardTimerTask` |
| `ServiceEventExecutorService` | Single-thread `ThreadPoolExecutor` with `PriorityBlockingQueue` (L1974–1978) | Processes incoming `ServiceEvent`s via `HandleServiceEventTask` |

### D. `SafeServiceRegistrar` — Secure Lookup Path

`SafeServiceRegistrar` (`SafeServiceRegistrar.java:34`) extends `ServiceRegistrar` with two methods:

- `lookUp(ServiceTemplate, int): Object[]` (L61) — returns bootstrap proxies instead of
  `ServiceItem[]`
- `notiFy(ServiceTemplate, int, RemoteEventListener, MarshalledInstance, long): EventRegistration`
  (L85–89) — registers for events securely

The `useInsecureLookup` configuration flag (key `useInsecureLookup`,
`ServiceDiscoveryManager.java:2392–2395`) gates the entire secure-vs-insecure path globally, at
`ServiceDiscoveryManager.java:872–873`, `LookupCacheImpl.java:1270`, `ServiceDiscoveryManager.java:2246`,
`ServiceDiscoveryManager.java:1327`, and `ServiceDiscoveryManager.java:1739`.

---

## 3. Where Decisions About Per-Proxy Isolation Can Be Made

### Decision Point 1 — At Discovery Time (Registrar Granularity)

**Location**: `DiscMgrListener.discovered()`, `ServiceDiscoveryManager.java:874–876` (after
`registrarPreparer.prepareProxy()`).

A custom `registrarPreparer` can inspect the registrar proxy's class, class loader, or codebase
annotation and attach metadata (e.g., via dynamic proxy wrapping). This decision is at **registrar
granularity**: all services from a given lookup service would be treated identically. Too coarse for
per-service isolation.

### Decision Point 2 — At Bootstrap Proxy Preparation Time (Service Granularity)

**Location**: `processBootStrapProxys()` (`LookupCacheImpl.java:862`),
`HandleServiceEventTask.run()` (`LookupCacheImpl.java:1020`), and `SDM.check()`
(`ServiceDiscoveryManager.java:2028`).

`bootstrapProxyPreparer.prepareProxy(bootstrapProxy)` is called once per service item before any
filtering or proxy download. A custom preparer can:

- Inspect the bootstrap proxy's `CodebaseAccessor` (if implemented) to determine the service's
  codebase URL.
- Analyse class metadata from the bootstrap proxy's type hierarchy.
- Tag the returned (wrapped) proxy with isolation hints.

This is the earliest per-service decision point, occurring **before** class download. It cannot yet
perform bytecode analysis of the full proxy because the service proxy class has not been downloaded.

### Decision Point 3 — At Filtering Time (Per-Service-Item Granularity, Best Fit)

**Location**: `ServiceItemFilter.check(item)` (`LookupCacheImpl.java:2129`, `2132`,
`LookupCacheImpl.java:827`, `ServiceDiscoveryManager.java:2044`, `ServiceDiscoveryManager.java:1997`).

This is the natural isolation decision point because:

1. The filter receives the full `ServiceItem`, which in secure mode still holds the bootstrap proxy
   at entry. The filter can call `((ServiceProxyAccessor) item.service).getServiceProxy()` to
   download the proxy into a specific `ClassLoader`.
2. The filter **replaces `item.service`** with the result (`ServiceItemFilter.java:99`): a prepared,
   constrained, and optionally isolated proxy object.
3. The three-state semantics (pass/fail/indefinite) handle transient class-loading failures
   gracefully.
4. First-stage filters run in `cacheTaskMgr` threads (not event dispatch), so I/O-intensive class
   loading is off the critical path.

For per-caller isolation (where different callers need independent proxy instances), the
**second-stage filter** at `LookupCacheImpl.java:827` is ideal: the first-stage filter stores an
un-isolated or lightweight proxy as `filteredItem`, and each caller's second-stage filter creates an
isolated wrapper. The clone at `LookupCacheImpl.java:825`
(`itemToFilter = itemToFilter.clone()`) ensures the `ServiceItem` is private per filter invocation.

### Decision Point 4 — At `ServiceProxyAccessor.getServiceProxy()` Call

**Locations**: `LookupCacheImpl.java:2116` (null filter, secure mode), `LookupCacheImpl.java:2136`,
`LookupCacheImpl.java:2148` (filter fallback paths), `ServiceDiscoveryManager.java:2037–2039`,
`ServiceDiscoveryManager.java:2057–2058`, `ServiceDiscoveryManager.java:2072–2073`.

These are the only sites where the actual service proxy is downloaded. A client-side wrapping filter
could intercept the downloaded proxy here to load it into a custom class loader or inject bytecode
analysis before use.

---

## 4. Current Caching Strategy

### Structure: Two-Level Map

```
serviceIdMap: ConcurrentMap<ServiceID, ServiceItemReg>
    ServiceItemReg:
        items: HashMap<ServiceRegistrar, ServiceItem>  // per-registrar raw items
        proxy: ServiceRegistrar                         // change-tracking registrar
        item: ServiceItem                               // current raw item
        filteredItem: ServiceItem                       // current prepared item (defensive clone)
        bDiscarded: boolean

eventRegMap: ConcurrentMap<ProxyReg, EventReg>
    EventReg:
        source, eventID: long                           // event registration identity
        seqNo: long                                     // event sequence tracking
        lease: Lease                                    // prepared event lease
        suspended: boolean                              // mutex during lookup/event
        discarded: boolean
```

### Atomicity Model

All mutations to `serviceIdMap` are performed as atomic `computeIfPresent` calls with pre-built
`BiFunction` instances (`PostEventState`, `PreEventState`, `AddOrRemove`, `Discard`,
`DissociateLusCleanUpOrphan`). This avoids `synchronized` blocks on a shared lock and instead uses
per-entry atomicity.

The pattern (`LookupCacheImpl.java:2171–2174`):
```java
PostEventState pes = new PostEventState(...);
serviceIdMap.computeIfPresent(srvcID, pes);
// read pes.filteredItemPass, pes.notifyRemoved after atomic block
```

State read outside the atomic block (e.g., in `ProxyRegDropTask` at
`LookupCacheImpl.java:389–400`) may be stale. The code mitigates this via identity checks
(`expected.equals(itemReg)` inside BiFunction) and a discard guard.

### Multi-Registrar Tracking

`ServiceItemReg.items` (`ServiceItemReg.java:32`) maintains a `Map<ServiceRegistrar, ServiceItem>`
so that a service registered with multiple lookup services can survive the removal of one registrar.
When the tracking registrar is removed (`removeProxy(proxy)` at `ServiceItemReg.java:82–94`), the
map picks a different registrar and returns its cached item. This item is then re-filtered by
`itemMatchMatchChange` (`LookupCacheImpl.java:1294–1298`).

### Discard / Retry Lifecycle

1. Client calls `LookupCacheImpl.discard(serviceRef)` (`LookupCacheImpl.java:724`).
2. `Discard.apply()` (`LookupCacheImpl.java:760`) marks `itemReg.bDiscarded = true` and schedules
   `ServiceDiscardTimerTask` with `discardWait` delay.
3. Timer fires: if service ID is still in `serviceIdMap` (no `MATCH_NOMATCH` event), item is
   un-discarded and a `serviceAdded` event fires; otherwise re-filters or quietly removes.
4. If a `MATCH_NOMATCH` event arrives during the wait, `handleMatchNoMatch` →
   `DissociateLusCleanUpOrphan` → `cancelDiscardTask` wakes the timer and cleans up.

### Lease Management Integration

Event leases are managed entirely by `LeaseRenewalManager` (shared across all caches at the SDM
level, `ServiceDiscoveryManager.java:830`). Each event registration's prepared lease is renewed for
`duration` ms (`ServiceDiscoveryManager.java:2265–2267`). On renewal failure,
`LeaseListenerImpl.notify()` (`ServiceDiscoveryManager.java:774–776`) calls `fail()` →
`discMgr.discard(proxy)` → `DiscMgrListener.discarded()` → `removeProxyReg()` →
`ProxyRegDropTask`, which removes all services from that registrar from the cache.

Importantly, **service proxies themselves carry no lease state in this framework**. The `filteredItem`
stored in `serviceIdMap` is a value object that persists until an explicit MATCH_NOMATCH event,
client discard, or registrar drop. There is no per-service-proxy lease.

---

## 5. How Isolated Proxies Would Integrate with Existing State Management

### Challenge: Single `filteredItem` Slot Per ServiceID

`serviceIdMap` stores exactly one `filteredItem` per `ServiceID`. This is unsuitable for per-caller
isolation (different security contexts, class loaders, or constraint sets). The slot was designed
for a single canonical prepared proxy shared across all callers.

### Integration Strategies

#### Strategy A: Second-Stage Filter per Caller (Lowest Impedance)

- First-stage filter (attached to `LookupCacheImpl`) stores the **bootstrap proxy** or a **lightly
  prepared common proxy** as `filteredItem`.
- Each caller's second-stage filter (passed to `LookupCache.lookup(filter)`) performs the
  per-caller isolation: downloads the proxy into a specific class loader, applies
  caller-specific constraints.
- The clone at `LookupCacheImpl.java:825` means each second-stage filter invocation gets a private
  copy of `ServiceItem`.
- Indefinite results from the second-stage filter correctly discard-and-retry the item at
  `LookupCacheImpl.java:836–843`.
- **No changes** to `ServiceItemReg` or the core cache structure are required.

#### Strategy B: Modify `ServiceItemReg` for Per-Context Caching (More Complex)

- Add `Map<Object, ServiceItem>` keyed by isolation context (e.g., `ClassLoader`, `Subject`, or a
  custom isolation key) alongside the existing `filteredItem` slot.
- `getFilteredItem(context)` returns the context-specific prepared proxy, falling back to
  re-preparation on miss.
- Cache invalidation: all context-specific entries must be cleared when
  `replaceProxyUsedToTrackChange()` or `setFilteredItem(null)` is called on a version change.
- The existing `computeIfPresent` pattern (`PostEventState`, `AddOrRemove`) would need to clear the
  per-context map atomically.

#### Strategy C: Custom `bootstrapProxyPreparer` + First-Stage Filter

- `bootstrapProxyPreparer` inspects the bootstrap proxy's class hierarchy and codebase, tagging it
  with isolation metadata (e.g., by wrapping in a dynamic proxy that carries a
  `Map<String,Object>` of annotations).
- First-stage `ServiceItemFilter.check()` reads the metadata from the tagged proxy, creates the
  isolated proxy, and stores it as `filteredItem`.
- Works for a single isolation domain but not per-caller isolation.

#### Strategy D: `ServiceProxyAccessor` Wrapper at Download Time

- A client-side wrapper around `ServiceProxyAccessor` (applied in the filter) intercepts
  `getServiceProxy()` and loads the returned bytes into an isolated class loader.
- This is the most powerful approach for true class-level isolation.
- Works cleanly at `LookupCacheImpl.java:2116` (null filter path),
  `LookupCacheImpl.java:2136`/`2148` (filter fallback), and
  `ServiceDiscoveryManager.java:2037–2039`.
- Does not require changes to any infrastructure code.

### Lease Management Compatibility

Isolated proxies are purely client-side constructs and do not interact with lease management. The
`EventReg.lease` covers the `ServiceEvent` subscription, not the service proxy. No new lease
infrastructure is needed regardless of how many isolated proxy copies are created. The
discard/retry mechanism also remains unaffected because `ServiceDiscardTimerTask` only re-filters
the raw `item` from `itemReg.getItem()`, which always holds the original bootstrap or raw proxy.

### Thread Safety Compatibility

All three executor services (`cacheTaskMgr`, `incomingEventExecutor`,
`serviceDiscardTimerTaskMgr`) run tasks concurrently. The second-stage filter approach (Strategy A)
is naturally thread-safe because it runs entirely in the caller's `lookup()` thread and never writes
to shared state. Strategies B–D require careful coordination with the `computeIfPresent` pattern to
avoid publishing partially-isolated proxies.

---

## 6. Quick-Reference Table

| Lifecycle Point | File | Lines | Extension Interface | Isolation Granularity |
|----------------|------|-------|--------------------|-----------------------|
| Registrar discovered | `ServiceDiscoveryManager.java` | 868, 875–876 | `ProxyPreparer` (`registrarPreparer`) | Per lookup service |
| Event lease prepared | `ServiceDiscoveryManager.java` | 2258 | `ProxyPreparer` (`eventLeasePreparer`) | Per registration |
| Bootstrap proxy prepared (snapshot) | `LookupCacheImpl.java` | 854–869 | `ProxyPreparer` (`bootstrapProxyPreparer`) | Per service item |
| Bootstrap proxy prepared (event) | `LookupCacheImpl.java` | 1020 | Same `bootstrapProxyPreparer` | Per service event |
| Service proxy downloaded (no filter) | `LookupCacheImpl.java` | 2116 | `ServiceProxyAccessor.getServiceProxy()` | Per service item |
| First-stage filter applied | `LookupCacheImpl.java` | 2129, 2132 | `ServiceItemFilter` | Per service item (cached) |
| Filter retry (indefinite) | `LookupCacheImpl.java` | 493–558 | Same `ServiceItemFilter` | Per service item |
| Second-stage filter at cache lookup | `LookupCacheImpl.java` | 827 | `ServiceItemFilter` (per-call) | Per call (isolated instance) |
| SDM direct lookup filter | `ServiceDiscoveryManager.java` | 1760, 2044 | `ServiceItemFilter` | Per call (not cached) |
| Discard requested | `LookupCacheImpl.java` | 724–773 | — | Per service item |
| Registrar dropped | `LookupCacheImpl.java` | 926–945 | — | All services from that registrar |
