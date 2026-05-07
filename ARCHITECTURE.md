# JGDMS Architecture Document

> **Version:** 3.1.1-SNAPSHOT  
> **License:** Apache Software License 2.0

---

## Table of Contents

1. [Overview and Design Philosophy](#1-overview-and-design-philosophy)
2. [JDK Dependency and DirtyChai](#2-jdk-dependency-and-dirtychai)
3. [Module Map and Dependency Layers](#3-module-map-and-dependency-layers)
4. [Core Components and Their Responsibilities](#4-core-components-and-their-responsibilities)
   - [4.1 Phoenix — Activation Daemon](#41-phoenix--activation-daemon)
   - [4.2 Reggie — Lookup Service](#42-reggie--lookup-service)
   - [4.3 JERI — Jini Extensible Remote Invocation](#43-jeri--jini-extensible-remote-invocation)
   - [4.4 JERI Server Threads and the Authenticated Subject](#44-jeri-server-threads-and-the-authenticated-subject)
   - [4.5 Discovery Providers](#45-discovery-providers)
   - [4.6 Platform Security Layer](#46-platform-security-layer)
   - [4.7 Bytecode Analysis Engine and Verdict Registry](#47-bytecode-analysis-engine-and-verdict-registry)
   - [4.8 Other Services](#48-other-services)
   - [4.9 Constrainable Proxies and Method Constraints](#49-constrainable-proxies-and-method-constraints)
5. [Communication Patterns](#5-communication-patterns)
   - [5.1 Discovery](#51-discovery-service-advertising-and-location)
   - [5.2 Service Registration and Lookup](#52-service-registration-and-lookup)
   - [5.3 Remote Method Invocation (JERI)](#53-remote-method-invocation-jeri)
   - [5.4 Event Delivery](#54-event-delivery)
6. [Service Boundaries and Isolation](#6-service-boundaries-and-isolation)
7. [Security and Defensive Features](#7-security-and-defensive-features)
   - [7.1 Transport Security](#71-transport-security)
   - [7.2 Deserialization Hardening](#72-deserialization-hardening)
   - [7.3 Authorization](#73-authorization)
   - [7.4 Proxy Trust](#74-proxy-trust)
   - [7.5 Codebase Safety Pipeline](#75-codebase-safety-pipeline)
   - [7.6 Tamper-Evident Data](#76-tamper-evident-data)
   - [7.7 ReliableLog — Persistence Safety](#77-reliablelog--persistence-safety)
8. [Gaps and Hardening Opportunities](#8-gaps-and-hardening-opportunities)
9. [Integrating a New Service](#9-integrating-a-new-service)
10. [Architectural Pattern Summary](#10-architectural-pattern-summary)

---

## 1. Overview and Design Philosophy

**JGDMS (Java/Jini Global Distributed Micro Services)** is a security-hardened fork of Apache River/Jini.
Its project descriptor (`JGDMS/pom.xml`, line 11) defines it as:

> *"Infrastructure for providing secured micro services, that are dynamically discoverable and
> searchable over IPv6 networks."*

Three pillars shape every design decision:

1. **Jini-model service discovery** — multicast/unicast lookup, lease-based registrations, event-driven
   service notifications.
2. **JERI (Jini Extensible Remote Invocation)** — a pluggable, constraint-based RPC layer that supersedes
   standard Java RMI.
3. **Defense-in-depth security** — hardened deserialization, TLSv1.3 transport, Java authorization,
   proxy trust verification, and (from 3.1.1) a novel codebase analysis pipeline.

Key security properties advertised in `README.md` lines 27–52:

* `ObjectInput`/`ObjectOutput` implementations hardening deserialization against untrusted input.
* TLSv1.3 stateless encrypted endpoints for RPC over untrusted networks.
* IPv6 multicast discovery using X.500 distinguished names with hash-function integrity checks.
* Unicast discovery over TLS with SHA-224/256/384/512 hash verification at both ends.
* Dynamic granting of `DownloadPermission` and `DeSerializationPermission` to authenticated lookup
  services during unicast discovery.
* Bootstrap-proxy authentication of third parties before granting download permission for service use.
* Invocation and method constraints.

---

## 2. JDK Dependency and DirtyChai

JGDMS currently requires **Java ≤ 23** (`README.md`, line 49). Java 24 and later remove the
`SecurityManager` API that underpins JGDMS's authorization model (`DelegateSecurityManager`,
`CombinerSecurityManager`).

To address this, **[pfirmstone/DirtyChai](https://github.com/pfirmstone/DirtyChai)** is being developed
as a security-hardened JDK replacement. DirtyChai reintroduces and extends the authorization
infrastructure that Oracle removed, ensuring that the principal-based and code-source-based permission
model that JGDMS relies on remains available on modern JVM versions. Running JGDMS on DirtyChai
restores full authorization semantics and allows the platform to evolve beyond the Java 23 ceiling.

---

## 3. Module Map and Dependency Layers

```
JGDMS/pom.xml  (groupId: au.net.zeus, artifact: jgdms, version: 3.1.1-SNAPSHOT)
│
├── jgdms-platform            Core APIs: security policy, atomic IO, discovery, URI, config
├── jgdms-collections         Concurrent, lock-free utility collections
├── jgdms-pref-class-loader   RFC3986-compliant URLClassLoader
├── jgdms-activation-parameters  ActivationDesc/GroupDesc value types
├── jgdms-activation          ActivationGroup, ActivationExporter, etc.
├── jgdms-jeri                JERI: endpoints (TCP/SSL/Kerberos/HTTP/HTTPS),
│                             invocation handlers & dispatchers
├── jgdms-discovery-providers Pluggable unicast/multicast formats
│                             (plaintext, SSL+sha224/256/384/512,
│                              X500+DSA/RSA/ECDSA variants, Kerberos)
├── jgdms-url-integrity       URL/codebase integrity checking
├── jgdms-lib                 ReliableLog, service-starter lifecycle
├── jgdms-lib-dl              Client-side: leases, Landlord, proxy trust,
│                             SafeServiceRegistrar, AbstractSmartProxy
├── service-starter           ServiceStarter (launches services)
├── services/
│   ├── reggie                Lookup service (ServiceRegistrar)
│   ├── outrigger             JavaSpace (tuple space)
│   ├── mahalo                Jini transaction manager
│   ├── mercury               Event mailbox
│   ├── norm                  Lease renewal service
│   ├── fiddler               Lookup discovery service
│   ├── group                 Shared activation group
│   ├── jgdms-service-support AbstractJiniService base class
│   ├── bytecode-analysis-engine  BAE: constant-pool bytecode scanner
│   └── verdict-registry      VerdictRegistry: quorum-based verdict aggregator
└── phoenix-activation/
    ├── phoenix               Activation daemon  (Activation.java)
    ├── phoenix-common        LocalAccess, AccessAtomicILFactory
    ├── phoenix-dl            Activator, AID, permission types
    ├── phoenix-group         ActivationGroupImpl, InstantiatorAccessExporter
    └── phoenix-init          ActivationGroupInit entry point
```

Module list: `JGDMS/pom.xml` lines 55–91.

---

## 4. Core Components and Their Responsibilities

### 4.1 Phoenix — Activation Daemon

**Primary file:** `JGDMS/phoenix-activation/phoenix/src/main/java/org/apache/river/phoenix/Activation.java`

Phoenix is JGDMS's service lifecycle manager (the Jini equivalent of `rmid`). It:

* Manages `ActivationGroup` JVMs as separate OS processes — one group per co-located service set.
  State is tracked in `idTable: Map<UID, ActivationGroupID>` and
  `groupTable: Map<ActivationGroupID, GroupEntry>` (`Activation.java` lines 150–153).
* Exports three remote interfaces via JERI:
  * **`ActivatorImpl`** — activates objects on demand.
  * **`SystemImpl`** — registers and modifies activation descriptors.
  * **`MonitorImpl`** — monitors group liveness.
  All three are exported with `BasicJeriExporter` or `AtomicILFactory`-backed exporters
  (`Activation.java` lines 344–383).
* Persists state via `ReliableLog` (`Activation.java` line 162), enabling crash recovery.
* Spawns activation group subprocesses running
  `org.apache.river.phoenix.init.ActivationGroupInit` (`Activation.java` lines 298–303).

**Configuration** is externalised via a Jini `Configuration` object: `groupThrottle`,
`persistenceSnapshotThreshold`, `instantiatorPreparer`, `groupOutputHandler`, and others
(`Activation.java` lines 280–329).

**Inter-service calls:** Activation groups call back to `MonitorImpl` to report active/inactive state.
Phoenix calls the group's `ActivationInstantiator` to instantiate registered activatable objects.

---

### 4.2 Reggie — Lookup Service

**Primary file:**
`JGDMS/services/reggie/reggie-service/src/main/java/org/apache/river/reggie/RegistrarImpl.java`

Reggie is the service registry. Services register their smart proxies here; clients discover them.

* Implements `Registrar`, `ProxyAccessor`, `ServerProxyTrust`, `ServiceProxyAccessor`,
  `ServiceAttributesAccessor`, `ServiceIDAccessor`, `CodebaseAccessor`
  (`RegistrarImpl.java` line 183).
* Handles **multicast discovery** (UDP announcements on IPv6 multicast) and
  **unicast discovery** (TCP with hash verification).
* Service registrations are stored as `SvcReg` entries in
  `serviceByID: Map<ServiceID, SvcReg>` (`RegistrarImpl.java` line 250).
* Exposes `SafeServiceRegistrar.lookUp()` — a secure alternative that returns *bootstrap proxies*
  rather than fully-unmarshalled service proxies, deferring deserialization until client trust is
  established (`SafeServiceRegistrar.java` lines 38–61).
* Available as `TransientRegistrarImpl`, `PersistentRegistrarImpl`, and `ActivatableRegistrarImpl`.

---

### 4.3 JERI — Jini Extensible Remote Invocation

**Primary packages:** `JGDMS/jgdms-jeri/src/main/java/net/jini/jeri/`

JERI is JGDMS's pluggable RPC layer.

**Client side** — `BasicInvocationHandler` / `AtomicInvocationHandler` (both implement
`java.lang.reflect.InvocationHandler`):

1. Check `MethodConstraints` (e.g., `Integrity`, `Confidentiality`, `ServerAuthentication`,
   `AtomicInputValidation`) against the endpoint's declared capabilities.
2. Open an `OutboundRequest` via the chosen `Endpoint` (TCP, SSL, Kerberos, HTTP, HTTPS).
3. Marshal arguments using `AtomicMarshalOutputStream` or `MarshalOutputStream`.
4. Read the response through `AtomicMarshalInputStream` when `AtomicInputValidation` is active.

**Server side** — `BasicInvocationDispatcher` / `AtomicInvocationDispatcher`:

1. Read protocol version and integrity/atomic-validation flags (`dispatch()` lines 604–642).
2. Populate the `ServerContext` collection with `IntegrityEnforcement`,
   `AtomicValidationEnforcement`, `ClientHost`, and `ClientSubject`
   (`dispatch()` line 655; `Util.populateContext()` lines 735–748).
3. Unmarshal the method hash and check `InvocationConstraints`.
4. Call `checkAccess()` → `checkClientPermission()` with a permission built from the client's
   authenticated Subject (`checkClientPermission()` lines 1057–1102).
5. Unmarshal arguments and invoke the method on the target object (`invoke()` line 710).
6. Marshal the return value or exception.

**Transport endpoints:**

| Endpoint | Description |
|---|---|
| `TcpEndpoint` / `TcpServerEndpoint` | Plain TCP — no authentication |
| `SslEndpoint` / `SslServerEndpoint` | TLS with X.509 certificates; supports `Confidentiality`, `Integrity`, `ServerAuthentication`, `ClientAuthentication` |
| `KerberosEndpoint` / `KerberosServerEndpoint` | Kerberos GSSAPI |
| `HttpEndpoint` / `HttpsEndpoint` | HTTP and HTTPS tunnelling |

**Invocation Layer Factories (`ILFactory`):**

| Factory | Description |
|---|---|
| `AbstractILFactory` | Base factory |
| `ProxyTrustILFactory` | Adds proxy trust handshake |
| `AtomicILFactory` | Adds `AtomicInputValidation` constraint, forcing `AtomicMarshalInputStream` server-side |
| `AccessAtomicILFactory` | Restricts to `LocalAccess` (phoenix-common) |
| `SystemAccessAtomicILFactory` / `SystemAccessProxyTrustILFactory` | Phoenix system-only access control |

---

### 4.4 JERI Server Threads and the Authenticated Subject

A security-critical feature of JGDMS's JERI implementation is that **every server-side dispatch thread
carries the fully-authenticated JAAS `Subject` of the calling client** for the lifetime of the
remote method invocation.

**How it works:**

1. When an SSL connection is accepted, `SslServerEndpointImpl.SslServerConnection.populateContext()`
   is called (`SslServerEndpointImpl.java` lines 1441–1447):
   ```java
   public void populateContext(InboundRequestHandle requestHandle, Collection context) {
       check(requestHandle);
       Util.populateContext(context, sslSocket.getInetAddress()); // ClientHost
       Util.populateContext(context, clientSubject);              // ClientSubject
   }
   ```
   `clientSubject` is the JAAS `Subject` built from the TLS client certificate presented during the
   handshake.

2. `Util.populateContext(context, Subject s)` wraps the Subject in a `ClientSubjectImpl` and adds it
   to the per-request `ServerContext` collection (`Util.java` lines 735–739):
   ```java
   public static void populateContext(Collection context, Subject s) {
       context.add(new ClientSubjectImpl(s));
   }
   ```

3. `BasicInvocationDispatcher.dispatch()` adds this context to the thread's `ServerContext` before
   calling `checkAccess()` and `invoke()` (`dispatch()` line 655–710).

4. Throughout the invocation, any code on the dispatch thread may call
   `BasicInvocationDispatcher.getClientSubject()` (lines 1489–1499) or directly
   `Util.getClientSubject()` (`Util.java` lines 812–816) to retrieve the caller's `Subject`:
   ```java
   // Util.java:812
   public static Subject getClientSubject() throws ServerNotActiveException {
       ClientSubject cs = (ClientSubject)
           ServerContext.getServerContextElement(ClientSubject.class);
       return (cs != null) ? cs.getClientSubject() : null;
   }
   ```

5. `checkClientPermission(Permission)` (`BasicInvocationDispatcher.java` lines 1057–1102)
   constructs a `ProtectionDomain` from the Subject's principals and evaluates the policy against
   it — effectively making every inbound method call subject to principal-based authorization.

**Consequence:** Service implementations running inside JGDMS do not need to perform their own
authentication — the JERI dispatch infrastructure guarantees that the authenticated client identity
is available on the thread. This enables fine-grained, principal-based per-method access control
without any additional boilerplate in service code.

---

### 4.5 Discovery Providers

**Package:** `JGDMS/jgdms-discovery-providers/src/main/java/org/apache/river/discovery/`

Pluggable SPI for unicast discovery encoding. Implementations are registered via
`java.util.ServiceLoader` / OSGi (`DiscoveryFormatProvider`).

| Format | Authentication |
|---|---|
| `plaintext` | None (legacy) |
| `ssl/sha224`, `ssl/sha256`, `ssl/sha384`, `ssl/sha512` | TLS + hash verification |
| `x500/sha1withdsa`, `sha256withdsa`, `sha256withrsa`, `sha512withrsa`, `sha512withecdsa` | X.500 + digital signature |
| `kerberos` | Kerberos GSSAPI |

Each format provides a `Client` (implements `UnicastDiscoveryClient`) and a `Server`
(implements `UnicastDiscoveryServer`). The client verifies a `MessageDigest`-based hash
(or TLS-based integrity) over the `UnicastResponse` before accepting it.

---

### 4.6 Platform Security Layer

**Package:** `JGDMS/jgdms-platform/src/main/java/org/apache/river/api/`

#### Policy Providers

* **`ConcurrentPolicyFile`** — a lock-free, high-throughput `java.security.Policy` replacement
  derived from Apache Harmony. Supports `grant` clauses with `SignedBy`, `CodeBase`, and `Principal`
  constraints. No permission cache by design — avoids stale permission checks
  (`ConcurrentPolicyFile.java` lines 61–85).
* **`RemotePolicyProvider`** — enables dynamic permission grants from remote sources (e.g., during
  trusted service discovery). Uses a volatile `remotePolicyGrants` array updated under `grantLock`
  (`RemotePolicyProvider.java` lines 72–79).
* **`DelegateSecurityManager`** / **`CombinerSecurityManager`** — SecurityManager implementations
  that intercept checks and delegate to policies.
* **`RevocablePolicy`** — supports runtime revocation of grants.
* `PermissionGrant` hierarchy: `URIGrant`, `PrincipalGrant`, `CertificateGrant`,
  `ClassLoaderGrant`, `ProtectionDomainGrant`.

#### Atomic Serialization (`@AtomicSerial`)

**File:** `JGDMS/jgdms-platform/src/main/java/org/apache/river/api/io/AtomicSerial.java`

`@AtomicSerial` is JGDMS's core defense against deserialization attacks. It requires every
annotated class to implement a public `(GetArg)` constructor that calls a static `check(GetArg)`
*before* any field is assigned (`AtomicSerial.java` lines 49–80). This prevents three classic
deserialization vulnerabilities:

1. Instantiation of arbitrary classes via stream manipulation.
2. Denial of service via circular reference / OOME.
3. Reference theft of a partially-constructed object before invariant checking.

The `Valid` utility class (`api/io/Valid.java`) provides defensive helpers: `Valid.copyMap()`,
`Valid.nullElement()`, `Valid.notNull()`, etc. These are used pervasively — for example in
`Activation`'s deserialization constructor (`Activation.java` lines 234–248).

`AtomicMarshalInputStream` / `AtomicMarshalOutputStream` enforce `@AtomicSerial` rules at the
transport level. When the `AtomicInputValidation.YES` constraint is in effect, the server-side
dispatcher switches to `AtomicMarshalInputStream`, ensuring every deserialized argument has passed
its invariant check.

---

### 4.7 Bytecode Analysis Engine and Verdict Registry
#### Under Development

**Interfaces:** `JGDMS/jgdms-platform/src/main/java/au/net/zeus/jgdms/api/codebase/`  
**Implementations:** `JGDMS/services/bytecode-analysis-engine/`,
`JGDMS/services/verdict-registry/`

Introduced in version 3.1.1, this pair forms a new architectural tier for safe codebase validation
before any untrusted service proxy is loaded.

#### BytecodeAnalysisEngine (`BytecodeAnalysisEngineImpl.java`)

* Runs in a **dedicated, restricted Phoenix activation group** — the only group permitted to make
  outbound HTTP(S) connections to codebase servers.
* Downloads JARs, scans every `CONSTANT_Utf8` entry in each `.class` constant pool for
  dangerous API references: `java/lang/Runtime`, `ProcessBuilder`, `ProcessImpl`,
  `sun/misc/Unsafe`, `jdk/internal/misc/Unsafe`, `java/lang/ClassLoader`
  (`BytecodeAnalysisEngineImpl.java` lines 70–79).
* Signs a `SignedVerdict` with its private key and submits it to the configured `VerdictRegistry`
  via `registry.submitVerdict()`.
* A BAE JVM crash is itself a security signal: Phoenix submits a `CrashReport` directly to the
  `VerdictRegistry`, bypassing the BAE entirely.

#### VerdictRegistry (`VerdictRegistryImpl.java`)

* **Authoritative, low-risk component** — never downloads or parses bytecode.
* Verifies `SignedVerdict` signatures against registered engine public keys.
* Applies a **quorum policy**: `SAFE` requires verdicts from ≥ K independent engines; a single
  `DANGEROUS` verdict or `CrashReport` triggers an immediate `DANGEROUS` publication
  (fail-safe semantics).
* Exposes event registration (`registerVerdictListener`) so clients can subscribe to codebase
  verdict changes via the standard Jini remote event mechanism (`VerdictRegistry.java` lines 197–201).
* Thread safety: `ConcurrentHashMap` for engine and verdict state; per-codebase `VerdictState`
  synchronised individually (`VerdictRegistryImpl.java` lines 70–78).

#### Wire Types: `SignedVerdict`, `CrashReport`, `RegistryVerdict`

All three are `@AtomicSerial`, `final`, and immutable, with invariants verified before construction.

* `CrashReport` enforces printable-ASCII and a 4 096-character bound on `stderrSummary` to prevent
  control-character injection (`CrashReport.java` lines 80–81, 320–329).
* `SignedVerdict` validates each URL as RFC3986-compliant via the `Uri` class
  (`SignedVerdict.java` lines 101–123).

---

### 4.8 Other Services

| Service | Module | Role |
|---|---|---|
| **Outrigger** | `services/outrigger` | JavaSpace (tuple-space) with transient and activatable variants |
| **Mahalo** | `services/mahalo` | Jini transaction manager (two-phase commit) |
| **Mercury** | `services/mercury` | Event mailbox — stores and delivers `RemoteEvent` objects asynchronously |
| **Norm** | `services/norm` | Lease renewal service — renews third-party leases on behalf of clients |
| **Fiddler** | `services/fiddler` | Lookup discovery service — manages group/locator discovery on behalf of clients |
| **Group** | `services/group` | Shared activation group for co-hosting services under Phoenix |

Every service follows the same structural pattern: a server-side `*Impl` (extending
`AbstractJiniService` or equivalent), a `*-dl` module with proxy, lease, and trust-verifier classes,
and `Activatable`, `NonActivatable`, and `Transient` variants.

---

### 4.9 Constrainable Proxies and Method Constraints

**Files:**
* `JGDMS/jgdms-lib-dl/src/main/java/au/net/zeus/jgdms/proxy/AbstractSmartProxy.java`
  (nested class `ConstrainableSmartProxy`)
* `JGDMS/jgdms-lib-dl/src/main/java/org/apache/river/proxy/ConstrainableProxyUtil.java`
* `JGDMS/jgdms-lib-dl/src/main/java/au/net/zeus/jgdms/proxy/AdminProxy.java`
  (nested class `ConstrainableAdminProxy`)
* `JGDMS/services/verdict-registry/verdict-registry-dl/.../VerdictRegistryProxy.java`
  (nested class `ConstrainableVerdictRegistryProxy`)
* `JGDMS/services/bytecode-analysis-engine/bytecode-analysis-engine-dl/.../BytecodeAnalysisEngineProxy.java`
  (nested class `ConstrainableBytecodeAnalysisEngineProxy`)

#### What are proxy method constraints?

Every JGDMS service proxy can optionally implement `net.jini.core.constraint.RemoteMethodControl`.
When it does, a client can call `proxy.setConstraints(MethodConstraints)` to specify security
requirements that must be satisfied for every outgoing call routed through the proxy.  Example
constraints include:

| Constraint | Effect |
|---|---|
| `ServerAuthentication.YES` | The remote endpoint must present a verifiable certificate |
| `ClientAuthentication.YES` | The client must authenticate itself to the server |
| `Confidentiality.YES` | All bytes in flight must be encrypted |
| `Integrity.YES` | All bytes in flight must be covered by a MAC or digital signature |
| `AtomicInputValidation.YES` | Server must use `AtomicMarshalInputStream` for all deserialized arguments |
| `ConfidentialityStrength.STRONG` | Cipher suite must meet a minimum key-strength threshold |

The constraints are enforced by the underlying `Endpoint` implementation (e.g. `SslEndpoint`) as
part of opening each `OutboundRequest`.  Any call that cannot satisfy its declared requirements
throws `UnsupportedConstraintException` before bytes leave the client JVM.

#### `AbstractSmartProxy.ConstrainableSmartProxy`

This nested abstract class (introduced in 3.1.1) provides the common boilerplate for all
constrainable JGDMS service proxies:

1. **Construction** — `ConstrainableSmartProxy(Object server, Uuid proxyID, MethodConstraints
   constraints)` calls `((RemoteMethodControl) server).setConstraints(constraints)` before storing
   the stub, so constraints are baked into the serialized server stub at proxy creation time
   (`AbstractSmartProxy.java` `applyConstraints()` helper).

2. **Deserialization validation** — the `(GetArg)` constructor verifies that the deserialized
   `server` field implements `RemoteMethodControl` *before* any field is assigned, satisfying the
   `@AtomicSerial` pre-construction contract (`AbstractSmartProxy.java`
   `checkConstrainable(GetArg)`, lines 517–526).

3. **`getConstraints()`** — delegates to `((RemoteMethodControl) server).getConstraints()`, returning
   the constraints currently set on the server stub.

4. **`getProxyTrustIterator()`** — private method found reflectively by `BasicJeriTrustVerifier`,
   returns a `SingletonProxyTrustIterator` wrapping the server stub; this enables the trust
   verification chain to reach the remote service for verification.

5. **`setConstraints()` is abstract** — concrete subclasses must implement it by constructing a
   new instance of themselves with the updated constraints.

#### The `methodMapArray` constraint-translation pattern

When a proxy method name differs from the corresponding server-side method name, the
`ConstrainableProxyUtil` utilities translate `MethodConstraints` between the two namespaces.

```
// Static array in the constrainable proxy class (pairs: proxy-method → server-method)
private static final Method[] methodMapArray = {
    getMethod(MyServiceInterface.class, "clientMethod", ...),  // proxy-visible
    getMethod(MyBackendInterface.class, "serverMethod", ...),  // server-side name
    ...
};

// In setConstraints():
MethodConstraints translated =
    ConstrainableProxyUtil.translateConstraints(constraints, methodMapArray);
return (Remote) ((RemoteMethodControl) server).setConstraints(translated);

// In the (GetArg) deserialization constructor:
ConstrainableProxyUtil.verifyConsistentConstraints(
    methodConstraints, server, methodMapArray);
```

The key utility methods in `ConstrainableProxyUtil` are:

| Method | Purpose |
|---|---|
| `translateConstraints(mc, mappings)` | Maps proxy-side method constraints to server-side names |
| `reverseTranslateConstraints(mc, mappings)` | Recovers logical constraints from constraints baked into the server stub |
| `verifyConsistentConstraints(mc, proxy, mappings)` | At deserialization: asserts that the stored `MethodConstraints` are consistent with the constraints already on the server stub |
| `equivalentConstraints(mc1, mc2, mappings)` | Tests equivalence of two `MethodConstraints` instances under a given method mapping |

When the proxy interface and server interface are identical (the common case in 3.1.1 services),
every element of `methodMapArray` maps a method to itself, and `translateConstraints` acts as a
pass-through — as seen in `ConstrainableAdminProxy` (`AdminProxy.java` lines 338–376).

#### Proxy factory pattern

Every constrainable service proxy uses the same static factory idiom:

```java
// Factory returns the constrainable variant when the server stub supports it
public static FooProxy create(FooService server, Uuid proxyID) {
    if (server instanceof RemoteMethodControl) {
        return new ConstrainableFooProxy(server, proxyID, null);
    }
    return new FooProxy(server, proxyID);
}
```

This allows the same service to be used:
* **Constraint-free** — over a plain TCP endpoint (e.g. in a testing or trusted-LAN environment).
* **Constrained** — over an SSL endpoint with full authentication and integrity requirements.

The returned proxy type is transparent to the caller; the difference is only observable through
`instanceof RemoteMethodControl` or by calling `getConstraints()`.

#### End-to-end constraint lifecycle

```
ProxyPreparer.prepareProxy(downloadedProxy)
  → BasicProxyPreparer.prepareProxy()
  → BasicProxyTrustVerifier.isTrustedObject()
      → proxy.getProxyTrustIterator() → server stub
      → server.getProxyVerifier() (remote call to verify trust)
  → proxy.setConstraints(clientRequiredConstraints)
      → ConstrainableProxyUtil.translateConstraints(constraints, methodMapArray)
      → server.setConstraints(translatedConstraints)        ← stored in stub
  → dynamicGranter.grant(proxy)                             ← e.g. DownloadPermission

Later, on each proxy method call:
  AtomicInvocationHandler.invoke()
  → stub.getConstraints() — retrieve translated constraints
  → endpoint.checkConstraints(constraints)
  → OutboundRequest opened only if all requirements are satisfiable
```

#### Serialized form and deserialization safety

In 3.1.1-style constrainable proxies (those extending `ConstrainableSmartProxy`), the constraints
are **not stored as a separate serial field** — they are baked into the server stub at construction
time by `applyConstraints()`.  The deserialization constructor recovers them from the stub via
`((RemoteMethodControl) server).getConstraints()` and optionally calls
`ConstrainableProxyUtil.verifyConsistentConstraints` to confirm nothing was tampered with during
serialization.

In older-style constrainable proxies (Fiddler, Mercury, Norm), a `methodConstraints` field *is*
stored separately, and `verifyConsistentConstraints` is called during `readObject` /
`(GetArg)` deserialization to ensure the separate field and the stub's baked-in constraints agree.

Both approaches guarantee that a tampered serialization stream that changes the constraints in only
one place — either the stub or the separate field — is detected and rejected before any field is
assigned.

---

## 5. Communication Patterns

### 5.1 Discovery — Service Advertising and Location

```
Registrar  ──UDP multicast──►  clients   (MulticastAnnouncement)
clients    ──UDP multicast──►  Registrar (MulticastRequest)
client     ──TCP unicast────►  Registrar (UnicastResponse, hash-verified)
```

For secure unicast discovery (e.g., `net.jini.discovery.ssl.sha256`):

1. Client establishes a TLS connection via `SslEndpoint`.
2. Client receives the `UnicastResponse`.
3. Client computes SHA-256 over the response bytes.
4. Client verifies the hash against the value included in the multicast announcement.
5. Only after hash verification is the Registrar proxy unmarshalled.

This two-stage verification prevents MITM response-substitution attacks that were possible in the
original Jini protocol.

---

### 5.2 Service Registration and Lookup

```
Service ──register()──► Registrar  (leased smart proxy)
Client  ──lookUp()────► Registrar  (bootstrap proxy [secure] or ServiceItem [insecure])
Client  ──prepareProxy()──────────  (trust check + constraints + permission grant)
Client  ──unmarshal codebase────►  (smart proxy loaded from codebase URL)
Client  ──JERI method call──────►  Service
```

`ServiceDiscoveryManager` (SDM) automates the client-side discovery lifecycle
(see also `PROXY_ISOLATION.md` for exhaustive per-phase detail):

* `DiscMgrListener.discovered()` (`ServiceDiscoveryManager.java:868`) is the entry point.
* **Secure-mode gate** (`ServiceDiscoveryManager.java:872–873`): when `useInsecureLookup` is false,
  any registrar that does not implement `SafeServiceRegistrar` is silently discarded.
* All registrar proxies are prepared via `registrarPreparer.prepareProxy()`
  (`ServiceDiscoveryManager.java:875–876`).
* Event-registration leases are prepared via `eventLeasePreparer.prepareProxy()`
  (`ServiceDiscoveryManager.java:2258`).

---

### 5.3 Remote Method Invocation (JERI)

```
Client proxy method call
  → AtomicInvocationHandler.invoke()
  → MethodConstraints checked against endpoint capabilities
  → OutboundRequest opened (TLS / TCP)
  → AtomicMarshalOutputStream serialises arguments
  ── network ──────────────────────────────────────────────
  → AtomicInvocationDispatcher.dispatch()
  → ServerContext populated: ClientHost, ClientSubject (authenticated),
      IntegrityEnforcement, AtomicValidationEnforcement
  → AtomicMarshalInputStream deserialises arguments
      (AtomicSerial invariants verified before object construction)
  → checkAccess(): permission check against client Subject's principals
  → method invoked on service implementation
      (service code may call Util.getClientSubject() at any time)
  → response marshalled back to client
```

The `AtomicInputValidation` constraint on the remote method switches the server to
`AtomicMarshalInputStream`, enforcing `@AtomicSerial` validation for all deserialized arguments.

---

### 5.4 Event Delivery

Jini remote events use a **push model**:

1. Client registers a `RemoteEventListener` with a service (returns a leased `EventRegistration`).
2. Service calls `listener.notify(RemoteEvent)` via JERI when a matching state change occurs.

**Mercury** provides a store-and-forward alternative: the service fires events to Mercury; the
client pulls them via `RemoteEventIterator`. This decouples producer and consumer lifetimes and
survives transient client unavailability.

---

## 6. Service Boundaries and Isolation

**Phoenix activation group isolation** is the primary process boundary. Each activation group runs
in a separate JVM with its own security policy file:

* The **BAE** group is granted only outbound HTTP(S) connections to codebase servers plus an
  inbound/outbound connection to the `VerdictRegistry` — no write-back to arbitrary targets.
* The **VerdictRegistry** group holds the registry's private signing key. The BAE never holds
  this key.
* Phoenix crash detection: abnormal group exit → `GroupOutputHandler` →
  (in the 3.1.1 codebase analysis architecture) `CrashReport` submitted to `VerdictRegistry`.

**Proxy isolation in the lookup service:**

* Services register a *smart proxy* (a serialized, downloadable Java object) plus, optionally, a
  *bootstrap proxy*.
* The bootstrap proxy implements `RemoteMethodControl`, `ServiceProxyAccessor`, and
  `ServiceAttributesAccessor` — sufficient to authenticate the service and inspect its attributes
  without downloading the full smart proxy codebase.
* `DownloadPermission` and `DeSerializationPermission` are dynamically granted by
  `RemotePolicyProvider` only after trust verification succeeds (the `SafeServiceRegistrar` path).

**`AbstractSmartProxy`** (`jgdms-lib-dl/src/main/java/au/net/zeus/jgdms/proxy/AbstractSmartProxy.java`)
encapsulates the boilerplate every smart proxy must implement:

* `@AtomicSerial` deserialization with field-level invariant checking.
* `ProxyAccessor` — exposes the inner server stub.
* `ReferentUuid` — stable UUID-based identity.
* `ProxyTrustIterator` chain for trust verification.

Its nested **`ConstrainableSmartProxy`** class adds `RemoteMethodControl` support (see §4.9), enabling
clients to attach per-method security constraints to any proxy whose server stub also implements
`RemoteMethodControl`.

---

## 7. Security and Defensive Features

### 7.1 Transport Security

* TLS via `SslEndpoint` / `SslServerEndpoint`. Constraint classes enforce `ServerAuthentication`,
  `ClientAuthentication`, `Confidentiality`, `Integrity`, and `ConfidentialityStrength.STRONG`
  (`SslEndpoint.java` lines 46–63).
* `FilterX509TrustManager` provides additional certificate validation beyond the JDK default.
* HTTP proxies are rejected by default for SSL endpoints.

### 7.2 Deserialization Hardening

* `@AtomicSerial` annotation + `(GetArg)` constructor pattern enforces pre-construction
  invariant validation.
* `Valid` utilities applied in all `@AtomicSerial` deserialization constructors.
* `AtomicMarshalInputStream` rejects deserialization of classes that violate `@AtomicSerial`
  invariants.
* Phoenix `Activation(GetArg)` constructor (`Activation.java` lines 234–248) uses `Valid.copyMap()`
  for both `idTable` and `groupTable`, preventing type-confusion attacks in recovered state.
* `CrashReport` and `SignedVerdict` are `final @AtomicSerial` with exhaustive invariant checks.

### 7.3 Authorization

* `RemotePolicyProvider` — dynamic permission grants without service restart.
* `RevocablePolicy` — runtime revocation of previously-granted permissions.
* `ConcurrentPolicyFile` — lock-free policy evaluation for high throughput.
* `DelegatePermission` and `SubjectDomain` — JAAS Subject-based permission grants.
* JERI server threads carry the client's authenticated `Subject` for the duration of each
  invocation, enabling principal-based per-method access control (see §4.4).
* Phoenix exports with `SystemAccessAtomicILFactory`, combining `AccessPermission` checks with
  `AtomicInputValidation`.
* **`Security.getCurrentPrincipals()` — user-Subject-first principal resolution.**
  `Security.grant(Class, Permission[])` calls this helper to scope dynamic grants.  It checks
  `Subject.current()` first (the human user Subject bound via `Subject.callAs()`, a ScopedValue),
  and falls back to the Subject on the `AccessControlContext` only when no user Subject is bound.
  This means grants made from a JERI dispatch thread are automatically scoped to the remote user's
  principals without any call-site changes.
* **`GrantPermission.checkGuard(Object)` — user-Subject-aware permission guard.**
  When `Subject.current()` returns a non-null user Subject, the `SecurityManager.checkPermission`
  call is wrapped in `Subject.doAs(user, …)` so the user's principals are injected into the
  `AccessControlContext` for the duration of the check.  Falls back to a direct
  `checkPermission` call on daemon threads where no user Subject is bound.

### 7.4 Proxy Trust

* `BasicProxyTrustVerifier` validates that a received proxy's server stub is reachable and that the
  stub's trust chain can be verified.
* `ConstrainableProxyUtil` (`verifyConsistentConstraints`, `translateConstraints`,
  `reverseTranslateConstraints`) ensures that method constraints on a received proxy are
  consistent, correctly translated between proxy-visible and server-side method names, and not
  tampered with during deserialization (see §4.9).
* `ProxyPreparer` / `BasicProxyPreparer` — called at proxy receipt time to: (a) verify trust,
  (b) apply method constraints via `setConstraints()`, (c) dynamically grant permissions.

### 7.5 Codebase Safety Pipeline

* BAE scans constant pools for dangerous API references before any service proxy is trusted.
* Quorum policy defends against a single compromised BAE instance.
* Fail-safe semantics: one `DANGEROUS` verdict or one `CrashReport` immediately revokes safety.
* A BAE crash is itself treated as a `DANGEROUS` vote for the codebase under analysis at crash
  time.

### 7.6 Tamper-Evident Data

* `SignedVerdict` carries a DER-encoded digital signature over URL set, verdict type, and timestamp
  (`SignedVerdict.java` lines 158–166).
* `CrashReport` carries a Phoenix identity signature over codebase URLs, exit code, incarnation
  number, and stderr summary (`CrashReport.java` lines 187–194).
* Clients are advised to apply `Integrity` and `ServerAuthentication` constraints when calling
  `VerdictRegistry.getVerdict()` (`VerdictRegistry.java` lines 243–246).

### 7.7 ReliableLog — Persistence Safety

`ReliableLog` (`JGDMS/jgdms-lib/src/main/java/org/apache/river/reliableLog/ReliableLog.java`) writes
a snapshot plus an update log with `fsync` to guarantee crash-safe recovery (`ReliableLog.java`
lines 44–64). A magic number (`0xf2ecefe7`) and versioned format detect corrupt log files
(`ReliableLog.java` lines 77–79). Both Phoenix and `PersistentRegistrarImpl` use `ReliableLog`.

---

## 8. Gaps and Hardening Opportunities

| Area | Gap |
|---|---|
| **ReliableLog tamper-evidence** | `ReliableLog` uses `fsync` but no HMAC or cryptographic integrity over log entries. A privileged local attacker could modify snapshot files before restart without detection. |
| **BAE dangerous-pattern completeness** | Constant-pool scanning checks only six class-name patterns. Reflection via `java.lang.reflect`, `MethodHandle`, obfuscated string construction, or dynamically-computed class references bypass this entirely. |
| **Multicast unauthenticated path** | `MulticastAnnouncement` and `MulticastRequest` in the plaintext discovery format carry no authentication. Services or clients using the legacy plaintext format are vulnerable to Registrar spoofing. |
| **`useInsecureLookup=true`** | When `useInsecureLookup` is true (`ServiceDiscoveryManager.java:872`), any `ServiceRegistrar` is accepted without the `SafeServiceRegistrar` gate. Non-authenticated proxies pass through `registrarPreparer`, which defaults to a no-op `BasicProxyPreparer` unless the operator configures trust verification. |
| **VerdictRegistry in-memory only** | `VerdictRegistryImpl` stores votes and published verdicts only in `ConcurrentHashMap` in memory. On restart, accumulated quorum votes are lost and `getVerdict()` may temporarily return `null` for previously-safe codebases. |
| **BAE submission identity** | `submitVerdict()` identifies the submitting engine by a plain `String engineId`. The registry verifies the signature, but `engineId` is not itself authenticated at the transport layer. Using `ClientAuthentication` on the VerdictRegistry's server endpoint would close this gap. |
| **Analysis task queue** | `BytecodeAnalysisEngineImpl` uses an unbounded `LinkedBlockingQueue`. Repeated `requestAnalysis()` calls could enqueue an unbounded number of tasks, enabling a denial-of-service against the BAE. |
| **JDK SecurityManager removal** | Java 24+ removes `SecurityManager`. Authorization enforcement relies on DirtyChai (see §2) or remaining on Java ≤ 23. |

---

## 9. Integrating a New Service

The `VerdictRegistry` implementation is the best existing template for a new Jini service in JGDMS.
Adding a new **observation reporting mechanism** (for example, anomaly detection or audit logging)
follows these steps:

1. **Define the service interface** in `jgdms-platform` or a new API module, extending
   `java.rmi.Remote`. Annotate all parameter and return-value types with `@AtomicSerial`.

2. **Implement `AbstractJiniService`** in a new `services/my-service/my-service-service` module.
   The base class wires: `Exporter`, `JoinManager`, `ReliableLog`, `ReadyState`, `CodebaseAccessor`,
   `ProxyAccessor`, `ServiceIDAccessor`
   (`AbstractJiniService.java` lines 63–80).

3. **Create a `*-dl` module** containing:
   * A smart proxy extending `AbstractSmartProxy` (`@AtomicSerial`, `ProxyTrustIterator`,
     `ReferentUuid`).  If the service should support per-method security constraints, also
     provide a nested `Constrainable*Proxy` extending `AbstractSmartProxy.ConstrainableSmartProxy`
     — returned by the factory when the server stub implements `RemoteMethodControl` (see §4.9).
   * A `ProxyVerifier` implementing `TrustVerifier`.
   * Lease classes extending `ConstrainableLandlordLease`.

4. **Choose a JERI exporter.** For an internet-facing service use `BasicJeriExporter` with
   `SslServerEndpoint` and `AtomicILFactory` to enforce TLS and `AtomicInputValidation`.

5. **Register with Phoenix.** Create an `ActivatableXxx.java` constructor mirroring
   `ActivatableMercuryImpl` or `ActivatableFiddlerImpl` — it receives an `ActivationID` +
   `MarshalledObject` and delegates to the shared implementation.

6. **Deliver events.** Implement `registerEventListener()` backed by the Jini
   `LeaseRenewalManager` + `Landlord` pattern (as in `VerdictRegistryImpl` and `MailboxImpl`).

7. **For tamper-evident audit logging.** Sign each log entry (as `SignedVerdict` and `CrashReport`
   do) using a service-private key, and store the DER signature alongside the payload in
   `ReliableLog`. Expose the signature for verification by auditors.

8. **Access the caller identity.** Inside any service method, retrieve the authenticated client
   `Subject` via `Util.getClientSubject()` (requires `ContextPermission` check) or use
   `BasicInvocationDispatcher.checkClientPermission(perm)` for declarative per-method
   authorization — both are available on the JERI dispatch thread with no additional setup.

---

## 10. Architectural Pattern Summary

| Pattern | Where Used |
|---|---|
| **Lease-based resource management** | All services (registrations, event subscriptions, space entries) |
| **Proxy + bootstrap proxy separation** | `SafeServiceRegistrar.lookUp()` / `ServiceDiscoveryManager` |
| **ProxyPreparer / trust verification** | Every proxy receipt point in SDM, Phoenix, services |
| **Atomic serialization (pre-construction validation)** | `@AtomicSerial` on all wire types |
| **Subject-per-dispatch-thread** | JERI `BasicInvocationDispatcher` via `ServerContext` / `ClientSubject` |
| **Pluggable constraints (invocation + discovery)** | JERI `MethodConstraints`, Discovery SPI |
| **Constrainable proxy pattern** | `ConstrainableSmartProxy` + `ConstrainableProxyUtil.translateConstraints` in every `*-dl` proxy |
| **Process isolation via Phoenix groups** | BAE, Reggie, services in separate JVM groups |
| **Quorum-based trust aggregation** | VerdictRegistry quorum policy |
| **Fail-safe DANGEROUS propagation** | VerdictRegistry + CrashReport path |
| **Append-only crash-safe log + snapshot** | `ReliableLog` in Phoenix and persistence services |
| **Dynamic permission grants** | `RemotePolicyProvider` during proxy preparation |
| **Subject-based authorization** | JAAS login context in Phoenix, Reggie, and every activatable service |
| **RFC3986 URI normalisation** | `Uri` class used in all codebase-related types |
| **User-Subject-first principal resolution** | `Security.getCurrentPrincipals()` prefers `Subject.current()` (ScopedValue) over ACC Subject, enabling automatic user-scoped grants from JERI dispatch threads |
| **User-Subject-aware `GrantPermission` guard** | `GrantPermission.checkGuard()` wraps `checkPermission` in `Subject.doAs(user)` when `Subject.current()` is non-null, injecting user principals into the ACC for the check |
