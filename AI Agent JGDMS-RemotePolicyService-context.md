# JGDMS — Remote In-Memory Policy Service — AI Agent Conversation Context

**Purpose:** This document continues the conversation from `AI-agent-JGDMS-DirtyChai-context.md`.
It covers a new design thread: the remote in-memory policy service, authenticated HTTPS bootstrap
policy loading, and the policy decoration stack. Hand this document to a future AI agent along
with the original context document to continue without loss of context.

**GitHub repositories:**
- DirtyChai: https://github.com/pfirmstone/DirtyChai
- JGDMS: https://github.com/pfirmstone/JGDMS

---

## 1. Starting Point — What Already Exists

### `RemotePolicy` (interface)
Located: `JGDMS/jgdms-platform/src/main/java/org/apache/river/api/security/RemotePolicy.java`

A single-method interface:
```java
@Beta
public interface RemotePolicy {
    void replace(PermissionGrant[] policyPermissions) throws IOException;
}
```

This is a **push/replace** model. A djinn administrator pushes a complete new `PermissionGrant[]`
to a node. The entire remote grant set is atomically replaced. No incremental add/remove.

The Javadoc explicitly states: *"No service implementation has been provided,
RemotePolicyProvider implements this interface to simplify creation of such a service."*

### `RemotePolicyProvider` (local-side implementation)
Located: `JGDMS/jgdms-platform/src/main/java/org/apache/river/api/security/RemotePolicyProvider.java`

This is the **client-side policy** that *receives* remote grants. It:
- Wraps a `basePolicy` (nested policy chain) implementing `ScalableNestedPolicy`
- Holds `volatile PermissionGrant[] remotePolicyGrants` — lock-free reads, write-locked replacement
- Implements `implies()` by combining base policy grants + remote grants
- Implements `ScalableNestedPolicy` for efficient grant traversal
- On `replace()`: validates caller holds `GrantPermission` for every permission being granted,
  checks `PermissionGrant` classes hold `PolicyPermission("Remote")`, atomically swaps reference
- Calls `CachingSecurityManager.clearCache()` on update

### `ConcurrentPolicyFile`
Located in both JGDMS and DirtyChai.

Key properties relevant to this work:
- `volatile PermissionGrant[] grantArray` — lock-free reads
- Delegates URL opening to `DefaultPolicyParser` via `PolicyUtils.URLLoader`
- Constructors accept `PolicyParser` and `URL[]` — both are subclass-accessible via
  `protected ConcurrentPolicyFile(PolicyParser dpr, Comparator<Permission> comp, URL[] policyLocations)`
- `refresh()` re-reads policy from original URLs

### `DefaultPolicyParser`
Located in both JGDMS and DirtyChai.

Key properties:
- `parse(URL location, Properties system)` opens the URL via `PolicyUtils.URLLoader` wrapped
  in `AccessController.doPrivileged()`
- All resolution methods (`resolveGrant`, `resolvePermission`, `initKeyStore` etc.) are
  `protected` or package-private — designed for extension
- **`scanner` field is `private final DefaultPolicyScanner`** — this is the one blocker
  for subclassing (see Section 4)
- Javadoc explicitly describes the class as designed for extension via overriding protected methods

### `CombinerSecurityManager`
Located in DirtyChai: `au.zeus.jdk.authorization.sm.CombinerSecurityManager`

Implements `CachingSecurityManager`. Key properties:
- Parallel permission checks via `Executors.newVirtualThreadPerTaskExecutor()` for 4+ domains
- Time-based permission cache (`Ref.TIME`, 20s TTL) keyed by `AccessControlContext`
- `ScopedValue<Integer> TRUSTED_RECURSIVE_CALL` limits recursion depth to 7 — safe for
  3-layer policy decoration stack (leaves headroom of 4)
- `clearCache()` guarded by `SecurityPermission("getPolicy")` — atomically replaces `checked` map
- In-flight checks complete against their already-captured `Set`; new checks start fresh
- Protected `checkPermission(ProtectionDomain, Permission)` extension point for future
  VerdictRegistry integration
- `contextCache` uses `Ref.TIME` (60s TTL) for `AccessControlContext` optimisation

---

## 2. The Policy Decoration Stack

The agreed three-layer decoration stack, outermost to innermost:

```
DynamicPolicy          (outermost — dynamic grants post proxy-authentication)
    └── RemotePolicyProvider   (djinn-wide grants from InMemoryPolicyService)
            └── SpiffePolicyFile       (bootstrap grants from HTTPS server)
                    └── (minimal local grant: SPIRE socket only)
```

Each layer implements `ScalableNestedPolicy`, so `getPermissionGrants(ProtectionDomain)`
flows efficiently up through the stack without redundant `PermissionCollection` reconstruction.

### Responsibilities at Each Layer

| Layer | Grant lifetime | Revocation mechanism |
|---|---|---|
| `SpiffePolicyFile` | JVM lifetime | `refresh()` on SVID rotation |
| `RemotePolicyProvider` | Djinn session | `replace()` from `InMemoryPolicyService` via `RemoteEvent` |
| `DynamicPolicy` | Proxy reachability | Automatic on GC via `WeakReference<ProtectionDomain>` |

### Construction Sequence
```java
// 1. Bootstrap — before SecurityManager fully active
SpiffeCredentialManager credentialManager =
    new SpiffeCredentialManager(SPIRE_SOCKET_PATH);

SpiffePolicyFile bootstrap = new SpiffePolicyFile(
    credentialManager,
    pinnedCACert,
    new URL[]{ new URL("https://policy.example.org/bootstrap.policy") }
);

// 2. Wrap with remote policy layer
RemotePolicyProvider remotePolicy = new RemotePolicyProvider(bootstrap);

// 3. Wrap with dynamic grant layer
DynamicPolicy dynamicPolicy = new DynamicPolicy(remotePolicy);

// 4. Install
Policy.setPolicy(dynamicPolicy);
System.setSecurityManager(new CombinerSecurityManager());

// 5. Connect to InMemoryPolicyService and sync
//    remotePolicy.replace(policyService.getCurrentGrants());
//    policyService.registerForPolicyUpdates(listener, handback, duration);
```

### ScalableNestedPolicy Traversal
```
DynamicPolicy.getPermissionGrants(domain)
    └── adds dynamic grants for domain (WeakReference-backed)
    └── delegates to RemotePolicyProvider.getPermissionGrants(domain)
            └── adds remotePolicyGrants[] applicable to domain
            └── delegates to SpiffePolicyFile.getPermissionGrants(domain)
                    └── adds bootstrap grants applicable to domain
                    └── adds static ProtectionDomain permissions
```

---

## 3. DynamicPolicy — Grant Lifecycle

`DynamicPolicy` garbage collects dynamic grants when the proxy's `ProtectionDomain`
becomes weakly reachable. This means:

- Grants are stored as `WeakReference<ProtectionDomain> → Set<PermissionGrant>`
- When the proxy object is GC'd, its `ProtectionDomain` becomes weakly reachable
- Grants are silently dropped on next `getPermissionGrants()` access
- No explicit revocation call needed; no session tracking; no lease management

### ReferenceQueue Cache Invalidation
A `ReferenceQueue<ProtectionDomain>` is used to detect cleared references and
invalidate the `CombinerSecurityManager` cache:

```java
private final ReferenceQueue<ProtectionDomain> deadDomains = new ReferenceQueue<>();

// On grant:
WeakReference<ProtectionDomain> ref = new WeakReference<>(domain, deadDomains);
grants.put(ref, permissionGrants);

// On implies() or lazily — drain queue and clear cache:
Reference<? extends ProtectionDomain> dead;
while ((dead = deadDomains.poll()) != null) {
    grants.remove(dead);
    cachingSecurityManager.clearCache(); // full clear — keyed by ACC not PD
}
```

Full `clearCache()` is required because `CombinerSecurityManager.checked` is keyed
by `AccessControlContext`, not `ProtectionDomain` directly.

### Quarantine Path
A proxy can be revoked by:
1. Removing all references to the proxy object
2. GC drops grants automatically via `WeakReference`
3. `RemotePolicyProvider` receives updated grants from `InMemoryPolicyService`
   that no longer include grants for that proxy's code source

Two independent revocation paths, neither requiring explicit per-proxy tracking.

---

## 4. New Classes To Be Implemented

### 4.1 `DefaultPolicyParser` — One-Line Change (both repos)

**Change `private` to `protected`:**
```java
// Before
private final DefaultPolicyScanner scanner;

// After
protected final DefaultPolicyScanner scanner;
```

Justification: class is documented as designed for extension; `scanner` is needed
by `HttpsClientAuthPolicyParser` to call `scanner.scanStream()` after opening an
authenticated stream. Commit in both JGDMS and DirtyChai.

---

### 4.2 `HttpsClientAuthPolicyParser extends DefaultPolicyParser`

Extends `DefaultPolicyParser` to intercept `https://` URLs and open them with
SPIFFE client certificate authentication and CA pinning. Non-HTTPS URLs are
delegated to `super.parse()` unchanged.

```java
public class HttpsClientAuthPolicyParser extends DefaultPolicyParser {

    private final SpiffeCredentialManager credentialManager;
    private final X509Certificate pinnedCACert;

    public HttpsClientAuthPolicyParser(
            SpiffeCredentialManager credentialManager,
            X509Certificate pinnedCACert) {
        super();
        this.credentialManager = credentialManager;
        this.pinnedCACert = pinnedCACert;
    }

    @Override
    public Collection<PermissionGrant> parse(URL location, Properties system)
            throws Exception {
        if (!"https".equalsIgnoreCase(location.getProtocol())) {
            return super.parse(location, system);
        }
        boolean resolve = PolicyUtils.canExpandProperties();
        InputStream authenticated = openAuthenticated(location);
        try {
            Reader r = new BufferedReader(
                new InputStreamReader(authenticated, "UTF-8"));
            Collection<DefaultPolicyScanner.GrantEntry> grantEntries = new HashSet<>();
            List<DefaultPolicyScanner.KeystoreEntry> keystores = new ArrayList<>();
            try {
                scanner.scanStream(r, grantEntries, keystores); // uses protected scanner
            } finally {
                r.close();
            }
            KeyStore ks = initKeyStore(keystores, location, system, resolve);
            Collection<PermissionGrant> result = new HashSet<>();
            for (DefaultPolicyScanner.GrantEntry ge : grantEntries) {
                try {
                    PermissionGrant pg = resolveGrant(ge, ks, system, resolve);
                    if (!pg.isVoid()) result.add(pg);
                } catch (Exception e) {
                    if (e instanceof SecurityException) throw (SecurityException) e;
                    log("security.1A9", new Object[]{ge}, e);
                }
            }
            return result;
        } finally {
            authenticated.close();
        }
    }

    private InputStream openAuthenticated(URL url) throws IOException {
        SSLContext ctx = credentialManager.getCurrentSSLContext();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(ctx.getSocketFactory());
        conn.setHostnameVerifier(new PinnedCAHostnameVerifier(pinnedCACert));
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        int status = conn.getResponseCode();
        if (status == 301 || status == 302 || status == 307 || status == 308)
            throw new IOException("Redirect refused for policy URL: " + url);
        if (status != 200)
            throw new IOException("Policy server returned HTTP " + status + " for: " + url);
        return conn.getInputStream();
    }

    private static class PinnedCAHostnameVerifier implements HostnameVerifier {
        private final X509Certificate pinnedCA;
        PinnedCAHostnameVerifier(X509Certificate pinnedCA) { this.pinnedCA = pinnedCA; }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            try {
                for (Certificate cert : session.getPeerCertificates()) {
                    if (cert instanceof X509Certificate x509)
                        if (x509.equals(pinnedCA)) return true;
                }
                return false;
            } catch (SSLPeerUnverifiedException e) {
                return false;
            }
        }
    }
}
```

---

### 4.3 `SpiffePolicyFile extends ConcurrentPolicyFile`

Trivial — just wires the parser into the superclass constructor:

```java
public class SpiffePolicyFile extends ConcurrentPolicyFile {

    public SpiffePolicyFile(
            SpiffeCredentialManager credentialManager,
            X509Certificate pinnedCACert,
            URL[] policies) throws PolicyInitializationException {
        super(
            new HttpsClientAuthPolicyParser(credentialManager, pinnedCACert),
            new PermissionComparator(),
            policies
        );
    }
}
```

On SVID rotation, `SpiffeCredentialManager` calls `refresh()` on this instance.
`HttpsClientAuthPolicyParser` calls `credentialManager.getCurrentSSLContext()` on
each `parse()` invocation, so rotation is picked up automatically.

---

### 4.4 `InMemoryPolicyService` — NOT YET IMPLEMENTED

This is the **next major work item**. The skeleton was agreed but not yet coded.

#### What it is
A JERI service registered at Host 1 (lookup service) that:
- Holds the canonical `PermissionGrant[]` in memory
- Exposes `RemotePolicy` for administrator writes (`replace()`)
- Exposes `PolicyEventRegistrar` for client node subscriptions
- Exposes `PolicyQueryService` for initial sync on client startup
- Fires `RemoteEvent` (specifically `PolicyUpdateEvent`) to all registered listeners
  when grants are updated
- Proxy is **reflective only** — no `DownloadPermission` required

#### Agreed interfaces

```java
// Admin writes — existing interface, unchanged
public interface RemotePolicy {
    void replace(PermissionGrant[] grants) throws IOException;
}

// Client subscription — note MarshalledInstance NOT MarshalledObject
public interface PolicyEventRegistrar {
    EventRegistration registerForPolicyUpdates(
        RemoteEventListener listener,
        MarshalledInstance handback,   // NOT MarshalledObject
        long leaseDuration
    ) throws RemoteException;
}

// Client pull — for initial sync on startup and missed-event recovery
public interface PolicyQueryService {
    PermissionGrant[] getCurrentGrants() throws RemoteException;
}
```

**Important:** `MarshalledInstance` must be used throughout instead of
`MarshalledObject`. `MarshalledInstance` carries codebase annotations, uses
`AtomicSerial`-aware deserialization, and respects the calling context's class
loader. The service stores handbacks opaquely and never unmarshals them.

#### In-memory state
```java
private volatile PermissionGrant[] canonicalGrants;  // current authoritative set
private final AtomicLong sequenceNumber;             // monotonically increasing
private final ConcurrentMap<EventRegistration, ListenerProxy> listeners; // lease-managed
```

#### Event delivery model — pull-on-notification
- `PolicyUpdateEvent` is a **signal only** — no grant payload embedded in the event
- Client receives event, detects sequence number gap if any, calls `getCurrentGrants()`
- Simpler, consistent: `getCurrentGrants()` is always the source of truth
- Missed event → gap in sequence number → client pulls current state to resync

#### Sequence number gap handling (client side)
```java
public void notify(RemoteEvent event) throws RemoteException {
    long received = event.getSequenceNumber();
    if (received != lastSequenceNumber + 1) {
        // Gap — missed one or more events, pull current state
        PermissionGrant[] current = policyQueryService.getCurrentGrants();
        localRemotePolicyProvider.replace(current);
    } else {
        // In order — still pull, getCurrentGrants() is source of truth
        PermissionGrant[] current = policyQueryService.getCurrentGrants();
        localRemotePolicyProvider.replace(current);
    }
    lastSequenceNumber = received;
}
```

#### SPIFFE identity
```
spiffe://jgdms.example.org/host/policy        → InMemoryPolicyService
spiffe://jgdms.example.org/admin/policy       → administrator principal
```

Only hosts with the admin SVID can call `replace()`. All other authenticated
JERI clients can call `getCurrentGrants()` and `registerForPolicyUpdates()`.

#### Lease management
Uses standard Jini `LandlordLease` infrastructure already in JGDMS.
Client uses `LeaseRenewalManager` for transparent renewal.
On lease expiry, client re-registers. If service unreachable: fail-secure —
keep last known grants, log alert.

---

## 5. Bootstrap Trust Chain

```
Minimal local policy (filesystem — SPIRE socket grant only)
    └── SpiffeCredentialManager reads SVID from SPIRE workload API
            └── HttpsClientAuthPolicyParser opens bootstrap policy URL
                    HTTPS with SPIFFE SVID as client cert + CA pinning
                    └── SpiffePolicyFile loaded with bootstrap grants
                            └── Node discovers InMemoryPolicyService from Host 1
                                    └── getCurrentGrants() → RemotePolicyProvider synced
                                            └── registerForPolicyUpdates() → live updates
```

### What the bootstrap policy grants
Only what is needed to reach `InMemoryPolicyService`:
- `SocketPermission` to lookup service (Host 1) port 4160
- `SocketPermission` to policy service host
- `UnixDomainSocketPermission` to SPIRE agent socket (for SVID rotation)
- Minimal `GrantPermission` for `RemoteEventListener` stub
- Nothing else — no `AllPermission`, no `DownloadPermission`, no file access

### What stays on the local filesystem
| Item | Purpose |
|---|---|
| SPIRE agent socket path | Provisioned by SPIRE, not secret |
| Bootstrap policy HTTPS URL | Not secret |
| Pinned bootstrap CA certificate | Public cert, not secret |
| Minimal local policy (SPIRE socket grant only) | Irreducible minimum |
| JVM startup config | Points to `SpiffePolicyFile` + URL |

### HTTPS fetch security properties
- Client certificate: SPIFFE SVID from `SpiffeCredentialManager`
- CA pinning: `PinnedCAHostnameVerifier` validates against pinned bootstrap CA
- No redirects: `setInstanceFollowRedirects(false)` + explicit redirect status check
- No local cache: if HTTPS server unreachable at startup, node does not start
- DNSSEC on policy server A/AAAA record recommended

---

## 6. Cache Invalidation Flow

```
InMemoryPolicyService.replace() called by administrator
    └── canonicalGrants reference atomically swapped
    └── sequenceNumber incremented
    └── PolicyUpdateEvent fired to all registered listeners
            └── client RemoteEventListener.notify()
                    └── client calls getCurrentGrants()
                            └── RemotePolicyProvider.replace(grants)
                                    └── remotePolicyGrants reference atomically swapped
                                    └── CombinerSecurityManager.clearCache()
                                            └── checked map cleared
                                            └── new permission checks re-evaluate
                                                full policy stack from scratch
```

`DynamicPolicy` also calls `clearCache()` when a `WeakReference<ProtectionDomain>`
is cleared via `ReferenceQueue` — same mechanism, triggered by GC rather than
remote update.

---

## 7. Key Design Decisions from This Session

| Decision | Rationale |
|---|---|
| `MarshalledInstance` not `MarshalledObject` | Carries codebase annotations, AtomicSerial-aware, correct class loader context |
| `DefaultPolicyParser.scanner` made `protected` | Enables `HttpsClientAuthPolicyParser` to call `scanner.scanStream()` after opening authenticated stream; class already designed for extension |
| Subclass `DefaultPolicyParser`, not add SPI | Minimal change surface; no new interfaces; non-HTTPS URLs unchanged |
| Subclass `ConcurrentPolicyFile` via custom `PolicyParser` | Hook is the parser, not URL opening; no changes to `ConcurrentPolicyFile` |
| Bootstrap policy fetched via HTTPS, no local cache | Fail-secure: if server unreachable, node does not start with stale policy |
| CA pinning on HTTPS fetch | Compromised system CA cannot serve malicious bootstrap policy |
| No redirects on HTTPS fetch | Prevents redirect to attacker-controlled URL bypassing client cert auth |
| Pull-on-notification for policy events | `getCurrentGrants()` always source of truth; simpler than embedded payload |
| `DynamicPolicy` revocation via `WeakReference` + `ReferenceQueue` | GC is the revocation mechanism; cannot forget to revoke; no window between abandonment and grant removal |
| Full `clearCache()` on dynamic grant GC | `CombinerSecurityManager.checked` keyed by ACC not PD; targeted invalidation not possible |
| Three-layer decoration stack | Clean separation of grant lifetimes; each layer revokes independently |
| Policy recursion depth safe at 3 layers | `TRUSTED_RECURSIVE_CALL` limit of 7; 3 layers leaves headroom of 4 |

---

## 8. Files To Read Before Continuing

The agent should fetch these before proposing code:

| File | Reason |
|---|---|
| `RemotePolicy.java` | Already read — interface is stable |
| `RemotePolicyProvider.java` | Already read — client-side implementation |
| `ConcurrentPolicyFile.java` | Already read — full source in context |
| `DefaultPolicyParser.java` | Already read — full source in context |
| `CombinerSecurityManager.java` | Already read — full source in context |
| `DynamicPolicy.java` | **Not yet read** — needed before implementing ReferenceQueue integration |
| `SpiffeCredentialManager.java` | **Does not yet exist** — to be implemented (Issue #205) |
| `PolicyParser.java` (interface) | **Not yet read** — needed to confirm `parse(URL, Properties)` signature |
| `LandlordLease` / lease infrastructure | Review before implementing `InMemoryPolicyService` lease management |

---

## 9. Next Work Items (in order)

1. **`DefaultPolicyParser.scanner` — `private` → `protected`** (one line, both repos)
   Prerequisite for everything below.

2. **`HttpsClientAuthPolicyParser`** — implement and test
   Depends on `SpiffeCredentialManager` interface being defined (can stub for now).

3. **`SpiffePolicyFile`** — trivial once `HttpsClientAuthPolicyParser` exists.

4. **`SpiffeCredentialManager`** — Issue #205
   Must be instantiable before `SecurityManager` is fully active.
   Reads SVID from SPIRE workload API socket.
   Provides `getCurrentSSLContext()` called on each policy refresh.
   Calls `SpiffePolicyFile.refresh()` on SVID rotation.

5. **`InMemoryPolicyService`** — the main outstanding item
   - JERI service skeleton
   - `RemotePolicy` + `PolicyEventRegistrar` + `PolicyQueryService` interfaces
   - `PolicyUpdateEvent extends RemoteEvent`
   - Lease management via `LandlordLease`
   - `replace()` permission validation (same pattern as `RemotePolicyProvider`)
   - Async event dispatch via bounded queue + dispatcher thread
   - SPIFFE SVID: `spiffe://jgdms.example.org/host/policy`

6. **`DynamicPolicy` — `ReferenceQueue` integration**
   Confirm current implementation; add `ReferenceQueue<ProtectionDomain>` drain
   on `getPermissionGrants()` calls; call `CachingSecurityManager.clearCache()`
   when references cleared.

7. **Client-side `RemoteEventListener` implementation**
   Sequence number tracking, gap detection, pull-on-notification, lease renewal.

---

## 10. Issues To Create

| Issue | Description |
|---|---|
| (existing) #205 | `SpiffeCredentialManager` — SPIRE Workload API integration |
| New | `InMemoryPolicyService` — remote in-memory policy service JERI skeleton |
| New | `HttpsClientAuthPolicyParser` + `SpiffePolicyFile` — authenticated bootstrap policy loading |
| New | `DefaultPolicyParser.scanner` visibility change (both repos) |
