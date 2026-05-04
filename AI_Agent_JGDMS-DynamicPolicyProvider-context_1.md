# JGDMS — DynamicPolicyProvider & Policy Stack — AI Agent Conversation Context

**Purpose:** This document continues the conversation from `AI_Agent_JGDMS-RemotePolicyService-context.md`.
It covers decisions made about `DynamicPolicyProvider`, void grant eviction, the ACC/PD reachability
chain, `ReferenceQueue` — confirming that neither `ReferenceQueue` nor `clearCache()` are needed
in `DynamicPolicyProvider` — and the smart proxy design for `RemotePolicy` across the JERI wire
using policy file syntax strings. Hand this document (and the previous context document) to a future
AI agent to continue without loss of context.

**GitHub repositories:**
- DirtyChai: https://github.com/pfirmstone/DirtyChai
- JGDMS: https://github.com/pfirmstone/JGDMS

---

## 1. Files Read This Session

| File | Key findings |
|---|---|
| `ContextCache.java` | Initialised by VM; creates weakly-valued `ConcurrentSkipListMap` for ACC cache with 2000ms cycle time; non-blocking requirement confirmed |
| `AccessControlContext.java` | ACC holds a **strong** reference to `ProtectionDomain` |
| `ClassLoaderGrant.java` | Subclass of `ProtectionDomainGrant`; holds `WeakReference<ProtectionDomain>`; `implies(ProtectionDomain)` extracts `ClassLoader` for comparison |
| `ProtectionDomainGrant.java` | Base of dynamic grant hierarchy; `protected final WeakReference<ProtectionDomain> domain`; `isVoid()` returns true when `domain.get() == null`; no strong ref to PD |
| `DynamicPolicyProvider.java` | Full source read — see Section 2 |

---

## 2. DynamicPolicyProvider — Current State

**Location:** `net.jini.security.policy.DynamicPolicyProvider`

**Grant store:**
```java
private final Collection<PermissionGrant> dynamicPolicyGrants;
// initialised as:
dynamicPolicyGrants = Collections.newSetFromMap(new ConcurrentHashMap<PermissionGrant,Boolean>(64));
```
A `ConcurrentHashMap`-backed `Set`. Concurrent, no weak values. The grant objects themselves
(`ProtectionDomainGrant`, `ClassLoaderGrant`) hold only weak refs to `ProtectionDomain` — so the
grant objects are the only strong references in this structure.

**Current void grant cleanup — `refresh()` only:**
```java
public void refresh() {
    basePolicy.refresh();
    Collection<PermissionGrant> remove = new LinkedList<PermissionGrant>();
    Iterator<PermissionGrant> i = dynamicPolicyGrants.iterator();
    while (i.hasNext()){
        PermissionGrant p = i.next();
        if (p.isVoid()) remove.add(p);
    }
    dynamicPolicyGrants.removeAll(remove);
    final SecurityManager sm = System.getSecurityManager();
    if (sm instanceof CachingSecurityManager){
        ((CachingSecurityManager) sm).clearCache();
    }
}
```

**Gap:** Void grants only evicted on `refresh()`. Between refreshes they accumulate and are
iterated on every `getPermissionGrants()` / `implies()` call (harmless but wasteful).

---

## 3. ACC / PD Reachability Chain — Key Insight

This is the most important design decision confirmed this session.

**Reachability chain:**
```
Live code (stack frame / field)
    └── strong ref → AccessControlContext (ACC)
            └── strong ref → ProtectionDomain (PD)
```

**ACC cache:** Weakly-valued — ACC entries survive only while live code holds a reference to the ACC.

**Lifecycle when a proxy is abandoned:**
1. Proxy goes out of scope → PD becomes weakly reachable
2. ACC holding that PD is no longer referenced by live code → ACC cache entry cleared
3. ACC is collected **before or concurrent with** PD weak ref being enqueued
4. By the time any `WeakReference<ProtectionDomain>` fires, the ACC cache entry is **already gone**

**Virtual thread / StructuredTaskScope caveat:**
A virtual thread builder or scope may hold an ACC (and therefore PD) alive longer than a normal
stack frame. However, once the builder/scope itself goes out of scope and is collected, the chain
unwinds identically. This is a delay, not a different lifecycle.

**Conclusion:** `clearCache()` is **never needed** from `DynamicPolicyProvider` on void grant
detection. When a PD's weak ref is enqueued, all ACC entries that could reference that PD are
already gone. The ACC eviction happens automatically.

---

## 4. ReferenceQueue — Not Needed

**Decided:** `DynamicPolicyProvider` does **not** need a `ReferenceQueue<ProtectionDomain>`.

**Reasons:**
- `ProtectionDomainGrant.isVoid()` already detects void state via `domain.get() == null`
- Void grants are **inert** — `implies()` returns false; no security consequence of leaving them
- ACC cache self-cleans before or concurrent with PD collection (Section 3)
- `clearCache()` from `DynamicPolicyProvider` would therefore be a no-op at best
- `ReferenceQueue` would add complexity with no correctness or security benefit

**The `ReferenceQueue` pattern discussed in the previous context document is superseded by this
analysis.** The context md document `AI_Agent_JGDMS-RemotePolicyService-context.md` Section 3
described a `ReferenceQueue` + `clearCache()` approach — **this is now confirmed unnecessary.**

---

## 5. Agreed Change — Lazy Void Eviction via Iterator

**Decision:** Add lazy void grant eviction using `it.remove()` on any iterator over
`dynamicPolicyGrants`, not just in `refresh()`.

**Rule:** Any iterator over `dynamicPolicyGrants` must call `it.remove()` on void grants as it
encounters them. The `ConcurrentHashMap` iterator is weakly consistent and supports `remove()` —
this is safe for concurrent access.

**Benign race:** If two threads both see and attempt to remove the same void grant, one `remove()`
is a no-op. Correctness is unaffected.

**Methods to update:**
1. `getPermissionGrants(ProtectionDomain)` — both the privileged and non-privileged iterator passes
2. `implies(ProtectionDomain, Permission)` — the iterator pass over `dynamicPolicyGrants`
3. `getGrants(Class, Principal[])` — iterator pass
4. `refresh()` — simplify to use `it.remove()` directly, eliminating the `LinkedList` accumulator

**Updated `refresh()` pattern:**
```java
public void refresh() {
    basePolicy.refresh();
    Iterator<PermissionGrant> i = dynamicPolicyGrants.iterator();
    while (i.hasNext()){
        if (i.next().isVoid()) i.remove();
    }
    final SecurityManager sm = System.getSecurityManager();
    if (sm instanceof CachingSecurityManager){
        ((CachingSecurityManager) sm).clearCache();
    }
}
```

**Updated iterator pattern (for all other methods):**
```java
Iterator<PermissionGrant> it = dynamicPolicyGrants.iterator();
while (it.hasNext()){
    PermissionGrant pg = it.next();
    if (pg.isVoid()) {
        it.remove();
        continue;
    }
    // ... existing logic
}
```

**Status:** Agreed, not yet implemented. A complete updated `DynamicPolicyProvider.java` is
the immediate next task.

---

## 6. Policy Decoration Stack — Unchanged

From the previous context document — still the agreed design:

```
DynamicPolicyProvider          (outermost — dynamic grants post proxy-authentication)
    └── RemotePolicyProvider   (djinn-wide grants from InMemoryPolicyService)
            └── SpiffePolicyFile       (bootstrap grants from HTTPS server)
                    └── (minimal local grant: SPIRE socket only)
```

Each layer implements `ScalableNestedPolicy`.

---

## 7. RemotePolicy Smart Proxy — String[] Wire Format

### Problem

`PermissionGrant` implementations in JGDMS use `@AtomicSerial` for safe deserialization.
DirtyChai copies of those classes do **not** implement `@AtomicSerial` and cannot — different
codebase, different constraints. `RemotePolicy.replace(PermissionGrant[])` as currently defined
would require `PermissionGrant` to cross the JERI wire via Java serialization, creating an
`@AtomicSerial` compatibility dependency at both ends that DirtyChai cannot satisfy.

### Solution — Smart Proxy with Policy File Syntax

The wire format is `String[]` — each element is a single `PermissionGrant` serialized to policy
file syntax via `PermissionGrant.toString()`. Plain Java serialization of `String[]` requires no
`@AtomicSerial` at either end.

```
Administrator (DirtyChai side)              JGDMS node (JERI server side)
    PermissionGrant[]                           PermissionGrant[]
         │                                           ▲
         │  PermissionGrant.toString()              │  DefaultPolicyParser / PermissionGrantBuilder
         ▼                                           │
    String[] ──────────── JERI wire ──────────► String[]
```

**Client-side smart proxy (DirtyChai):**
```java
public void replace(PermissionGrant[] grants) throws IOException {
    String[] policyStrings = new String[grants.length];
    for (int i = 0; i < grants.length; i++) {
        policyStrings[i] = grants[i].toString();
    }
    serverProxy.replace(policyStrings); // String[] over JERI wire
}
```

**Server-side skeleton (JGDMS):**
```java
public void replace(String[] policyStrings) throws IOException {
    // 1. Parse String[] → PermissionGrant[] using DefaultPolicyScanner fed via StringReader
    // 2. Validate caller holds GrantPermission for every permission (server-side — cannot trust client)
    // 3. Delegate to RemotePolicyProvider.replace(grants)
}
```

### Parsing on the Server Side

`DefaultPolicyParser.parse(URL, Properties)` opens a URL — not suitable for in-memory strings.
Instead, feed each string directly into `DefaultPolicyScanner.scanStream()` via a `StringReader`
wrapped in an `InputStream` adaptor, bypassing URL machinery entirely. This is the same reason
`DefaultPolicyParser.scanner` must be made `protected` — both `HttpsClientAuthPolicyParser` and
the server-side `InMemoryPolicyService` parsing path need direct scanner access.

### Validation Must Be Server-Side

`RemotePolicyProvider.replace()` already validates that the caller holds `GrantPermission` for
every permission being granted. The same validation pattern applies in `InMemoryPolicyService`
after parsing. The smart proxy client cannot be trusted to have pre-validated — validation is
always the server's responsibility.

### Wire Interface

The JERI remote interface exposed by `InMemoryPolicyService` uses `String[]`, not `PermissionGrant[]`:

```java
public interface RemotePolicyService {
    void replace(String[] policyStrings) throws IOException;
    String[] getCurrentGrants() throws IOException;
    EventRegistration registerForPolicyUpdates(RemoteEventListener listener,
                                               MarshalledInstance handback,
                                               long duration) throws IOException;
}
```

`getCurrentGrants()` returns `String[]` for the same reason — the client (DirtyChai side) parses
them back into `PermissionGrant[]` using its own (non-`@AtomicSerial`) implementations.

---

## 8. Next Work Items (in order)

1. **`DynamicPolicyProvider.java` — lazy void eviction** *(immediate)*
   Apply `it.remove()` pattern to all four iterator sites listed in Section 5.
   Produce complete updated file.

2. **`DefaultPolicyParser.scanner` — `private` → `protected`** (one line, both repos)
   Prerequisite for both `HttpsClientAuthPolicyParser` and `InMemoryPolicyService` server-side
   string parsing.

3. **`HttpsClientAuthPolicyParser`** — HTTPS + SPIFFE client cert URL opening.
   Subclass of `DefaultPolicyParser`. Depends on `SpiffeCredentialManager` interface.

4. **`SpiffePolicyFile`** — trivial wrapper once `HttpsClientAuthPolicyParser` exists.

5. **`SpiffeCredentialManager`** — Issue #205
   SPIRE Workload API socket integration. Must be instantiable before `SecurityManager`
   is fully active. Provides `getCurrentSSLContext()`. Calls `SpiffePolicyFile.refresh()`
   on SVID rotation.

6. **`RemotePolicyService` interface** — new JERI wire interface
   Uses `String[]` not `PermissionGrant[]` — see Section 7.
   ```java
   void replace(String[] policyStrings) throws IOException;
   String[] getCurrentGrants() throws IOException;
   EventRegistration registerForPolicyUpdates(RemoteEventListener, MarshalledInstance, long) throws IOException;
   ```

7. **`InMemoryPolicyService`** — JERI service skeleton
   - Implements `RemotePolicyService`
   - Server-side `String[]` → `PermissionGrant[]` parsing via `DefaultPolicyScanner.scanStream()`
     fed from `StringReader` (no URL machinery)
   - `replace()` permission validation after parsing (same pattern as `RemotePolicyProvider`)
   - `PolicyUpdateEvent extends RemoteEvent`
   - Lease management via `LandlordLease`
   - Async event dispatch via bounded queue + dispatcher thread
   - SPIFFE SVID: `spiffe://jgdms.example.org/host/policy`

8. **DirtyChai smart proxy client**
   - Implements `RemotePolicy` locally (DirtyChai side)
   - `PermissionGrant.toString()` → `String[]` → JERI wire
   - `getCurrentGrants()` response: `String[]` → parse back to `PermissionGrant[]`
   - No `@AtomicSerial` dependency at either end

9. **Client-side `RemoteEventListener`**
   Sequence number tracking, gap detection, pull-on-notification, lease renewal.

---

## 9. Files Still To Read Before Continuing

---

## 9. Files Still To Read Before Continuing

| File | Reason |
|---|---|
| `PolicyParser.java` (interface) | Confirm `parse(URL, Properties)` signature before implementing `HttpsClientAuthPolicyParser` |
| `DynamicPolicy.java` (interface) | Confirm interface contract before finalising `DynamicPolicyProvider` update |
| `DefaultPolicyScanner.java` | Confirm `scanStream()` signature and input type before implementing server-side `String[]` parsing in `InMemoryPolicyService` |
| `LandlordLease` / lease infrastructure | Review before implementing `InMemoryPolicyService` lease management |

---

## 10. Key Design Decisions — Cumulative

| Decision | Rationale |
|---|---|
| No `ReferenceQueue` in `DynamicPolicyProvider` | ACC is collected before or concurrent with PD weak ref enqueue; `clearCache()` would be a no-op |
| No `clearCache()` on dynamic grant GC | ACC self-evicts; no stale entries remain by the time void is detected |
| Lazy void eviction via `it.remove()` on all iterators | Void grants evicted as discovered on any access path; no separate cleanup pass needed |
| `refresh()` simplified to `it.remove()` | Eliminates `LinkedList` accumulator; same correctness guarantee |
| `ProtectionDomainGrant` / `ClassLoaderGrant` hold only weak refs | Grant objects are inert when PD collected; no strong pin of PD in grant store |
| `dynamicPolicyGrants` is `ConcurrentHashMap`-backed `Set` | Concurrent iterator supports `remove()`; weakly consistent iteration is safe for this use |
| `RemotePolicy` wire format is `String[]` not `PermissionGrant[]` | DirtyChai cannot implement `@AtomicSerial`; `String[]` requires no `@AtomicSerial` at either end |
| Policy file syntax as wire format | `PermissionGrant.toString()` already emits it; `DefaultPolicyScanner` already parses it; proven and low-maintenance |
| Validation is server-side after parsing | Client smart proxy cannot be trusted; `InMemoryPolicyService` validates `GrantPermission` after `String[]` → `PermissionGrant[]` parse |
| Server-side parsing via `scanStream()` + `StringReader` | Bypasses URL machinery; same reason `scanner` must be `protected` |
| `getCurrentGrants()` also returns `String[]` | Symmetric with `replace()`; client parses back to its own `PermissionGrant` implementations without `@AtomicSerial` |
| `MarshalledInstance` not `MarshalledObject` | Carries codebase annotations, AtomicSerial-aware, correct class loader context |
| `DefaultPolicyParser.scanner` made `protected` | Enables both `HttpsClientAuthPolicyParser` and `InMemoryPolicyService` string parsing to call `scanner.scanStream()` |
| Bootstrap policy fetched via HTTPS, no local cache | Fail-secure: node does not start if server unreachable |
| CA pinning on HTTPS fetch | Compromised system CA cannot serve malicious bootstrap policy |
| Pull-on-notification for policy events | `getCurrentGrants()` is always source of truth |
| Three-layer decoration stack | Clean separation of grant lifetimes; each layer revokes independently |
