# JGDMS & DirtyChai: Secure, Scalable, Dynamically Discoverable Microservices for the JVM

## Introduction

Modern distributed systems face an uncomfortable trade-off: the more open and composable a platform
is, the harder it becomes to secure. Remote code loading, serialized objects crossing trust
boundaries, and third-party service proxies all expand the attack surface. Most platforms paper over
these problems with network-level firewalls and hope for the best.

**JGDMS** (*Java/Jini Global Distributed Micro Services*) and its companion JDK fork **DirtyChai**
take a different path. They embed security deep into the infrastructure itself — in the transport,
the serialization mechanism, the policy engine, and the codebase loading pipeline — while
simultaneously delivering the performance and scalability needed for production distributed systems.

---

## What Is JGDMS?

JGDMS is a security-hardened fork of [Apache River](https://river.apache.org/) (née Jini), the Sun
Microsystems framework for self-organizing, dynamically-discoverable distributed services. Where
standard Java RMI stops at "call a remote method," Jini/JGDMS goes further: services announce
themselves on the network, clients discover them by capability rather than by hard-wired address,
and trust is established cryptographically before any code runs.

JGDMS is described in its own project descriptor as:

> *"Infrastructure for providing secured micro services, that are dynamically discoverable and
> searchable over IPv6 networks."*

Three pillars shape every design decision:

1. **Jini-model service discovery** — lease-based registrations, multicast and unicast lookup,
   event-driven service notifications.
2. **JERI (Jini Extensible Remote Invocation)** — a pluggable, constraint-based RPC layer that
   supersedes standard Java RMI with pluggable transport, per-method security requirements, and
   authenticated dispatch.
3. **Defence-in-depth security** — hardened deserialization, TLSv1.3 transport, Java authorization,
   proxy trust verification, and a novel codebase safety pipeline that analyzes third-party
   bytecode before it is ever loaded.

---

## What Is DirtyChai?

JGDMS's authorization model depends on Java's `SecurityManager`, `AccessController`, and
`ProtectionDomain` APIs — a fine-grained, code-source-and-principal-based permission system that
Oracle deprecated in Java 17 and removed entirely in Java 24. Without these APIs, you cannot
restrict what code from a particular source can do once it is loaded.

**DirtyChai** is a community fork of OpenJDK that restores, improves, and extends this
authorization infrastructure. It is not a "sandbox" in the sense of safely running untrusted code —
its goal is to ensure that *trusted but independent* parties operate only within their declared and
granted privileges. Its key design goals are:

- Prevent loading of untrusted code (`LoadClassPermission`)
- Break deserialization gadget attack chains (`SerialObjectPermission`)
- Block native code injection (`NativeInvocationPermission`, `NativeMemoryPermission`)
- Maintain and extend permission guard hooks
- High performance and scalability
- Community redesign of the Authorization API for potential inclusion in OpenJDK mainline

DirtyChai completes what Oracle started and stopped. Running JGDMS on DirtyChai restores the full
authorization semantics and lets the platform evolve beyond the Java 23 ceiling.

---

## Security: Baked In, Not Bolted On

### TLSv1.3 Transport With Authenticated Dispatch

Every JGDMS service communication happens over JERI, the Jini Extensible Remote Invocation layer.
JERI is not just a wire protocol — it is a *constraint system*. Each service proxy carries a set of
`MethodConstraints` that declare the security requirements for every method call:

| Constraint | Effect |
|---|---|
| `ServerAuthentication.YES` | The remote endpoint must present a verifiable certificate |
| `ClientAuthentication.YES` | The client must authenticate itself to the server |
| `Confidentiality.YES` | All bytes in flight must be encrypted |
| `Integrity.YES` | All bytes in flight must be covered by a MAC or digital signature |
| `AtomicInputValidation.YES` | The server must use hardened deserialization for all arguments |
| `ConfidentialityStrength.STRONG` | Cipher suite must meet a minimum key-strength threshold |

Any call that cannot satisfy its declared requirements throws `UnsupportedConstraintException`
*before* bytes leave the client JVM. Security requirements are enforced by construction, not by
convention.

The SSL endpoint supports TLSv1.3 with X.509 certificates. Every server-side dispatch thread
automatically carries the **fully-authenticated JAAS `Subject`** of the calling client for the
lifetime of the remote method invocation. Service implementations do not write authentication
boilerplate — the infrastructure guarantees the authenticated identity is on the thread.

### SPIFFE/SPIRE: Zero-Touch Certificate Management

In a fleet of services, long-lived keystores are a management and security liability. JGDMS
integrates [SPIFFE](https://spiffe.io/) workload identity via SPIRE. Each host process and client
JVM receives a short-lived (~1 hour) X.509 SVID (SPIFFE Verifiable Identity Document) from a local
SPIRE agent.

The `SpiffeCredentialManager` component:
- Opens the SPIRE Workload API socket on startup
- Populates an in-memory `Subject` with the current X.509 certificate and private key (no
  filesystem keystore)
- Rotates credentials automatically when the SVID nears expiry
- Triggers policy refresh on each rotation

No `keytool`, no PKCS#12 files, no manual certificate renewal. Each service's identity is managed
by the SPIRE control plane — revocation and rotation happen without JVM restarts.

### Hardened Deserialization: `@AtomicSerial`

Java object deserialization is one of the richest attack surfaces in enterprise software. JGDMS
replaces standard `Serializable`/`readObject` with `@AtomicSerial` — an annotation and constructor
protocol that requires:

1. A `(GetArg)` constructor whose **first action** is calling a static `check(GetArg)` method to
   validate all field values *before* any field is assigned.
2. A `static SerialForm[] serialForm()` method declaring the expected serial shape.
3. Complete prevention of three classic deserialization vulnerabilities:
   - Instantiation of arbitrary classes via stream manipulation
   - Denial of service via circular reference or OOME
   - Reference theft from a partially-constructed object

When the `AtomicInputValidation.YES` constraint is in effect, the JERI dispatcher switches to
`AtomicMarshalInputStream`, ensuring every deserialized argument across the wire has passed its
invariant checks.

### Three-Layer Authorization Stack

Authorization in JGDMS is not a single on/off switch. It is a composable stack of three policy
providers:

1. **`SpiffePolicyFile`** (innermost/bootstrap) — fetches baseline grants from an HTTPS server
   authenticated with the JVM's own SVID. Fail-secure: the JVM will not start if the policy server
   is unreachable. Refreshes on every SVID rotation.
2. **`RemotePolicyProvider`** (middle) — holds session grants pushed from a central
   `InMemoryPolicyService`. Enables dynamic permission management without restarting services.
3. **`DynamicPolicyProvider`** (outermost) — holds per-proxy, GC-scoped grants. When a proxy is
   garbage-collected, its grants are automatically swept. Keeps the hot path write-free.

The effective permission for any codebase is the **intersection** of what the code declares it
needs (`PERMISSIONS.LIST`), what the grant ceiling allows (`GrantPermission`), and what the SPIFFE
principal scope permits. No single party controls the outcome unilaterally — a supply-chain
compromise of one component cannot silently escalate its own privileges.

---

## The Safe Codebase Audit Pipeline (SCAP)

One of JGDMS's most distinctive features is SCAP — a five-host architecture that audits every
third-party JAR for safety *before any client ever deserializes an object from it*.

### Why This Matters

A distributed Java system that loads remote code is exposed to supply-chain attacks: an attacker
replaces a legitimate JAR with one containing malicious or denial-of-service bytecode. Blocking
class initializers (`<clinit>`) are a particularly subtle liveness attack: they can pin virtual
thread carrier threads or hold the JVM class-loading lock, causing complete scheduler stalls with
as few concurrent requests as `Runtime.availableProcessors()`.

### The Five Hosts

```
Host 1 — Jini Lookup Service
  Stores marshalled service items opaquely. Fires events when new services register.

Host 2 — BAE Pool (SELinux-isolated, stateless, replicated N times)
  Analyzes JAR bytecode. Signs JarAnalysisReport with its private key.
  No outbound internet. No exec. No JNI. No FFM.

Host 3 — Verdict Registry
  Accumulates signed reports. Issues a RegistryVerdict (SAFE / DANGEROUS / INCONCLUSIVE)
  only when a quorum of independent engines agrees.
  Clients query here before unmarshalling any proxy.

Host 4 — Codebase Downloader (proactive)
  The ONLY component with outbound internet access.
  Downloads JARs on new service registrations and submits them to the BAE pool.

Host 5 — JFR Telemetry Service (reactive)
  Receives VirtualThreadPinned JFR events from client JVMs.
  Triggers re-analysis without allowing clients to directly influence verdicts.
```

**Crucially:**
- Host 2 and Host 3 have no direct connection — a compromised analysis engine cannot write verdicts
  to the registry.
- Host 4 and Host 5 have no direct connection — a flood of JFR events from a misbehaving client
  cannot DoS the analysis pipeline.
- An abnormal JVM exit (Phoenix crash) on Host 2 is itself a security signal: Phoenix submits a
  `CrashReport` directly to the Verdict Registry, condemning the codebase that caused the crash.

### The Bytecode Analysis Engine

The BAE uses [ASM](https://asm.ow2.io/)-based visitors to analyze every class in a JAR:

- **`ClinitBlockingVisitor`** — BFS traversal from `<clinit>` to a registry of blocking sinks
  (network I/O, file locks, thread creation, native calls). Classifies risk as `CLEAN`,
  `BLOCKING_GUARDED`, or `BLOCKING_DECLARED` (DANGEROUS).
- **`AtomicSerialComplianceVisitor`** — verifies that every `Serializable` class crossing a JERI
  wire follows the `@AtomicSerial` protocol. Violations produce a `DANGEROUS` verdict.
- **Cyclic `<clinit>` detector** — detects circular class-initializer dependency chains that would
  deadlock JVM class loading.
- **`PERMISSIONS.LIST` integration** — reads each JAR's declared permissions and upgrades
  `BLOCKING_GUARDED` to `BLOCKING_DECLARED` when the JAR already requests the permission that
  guards a blocking sink.

The analysis result is a signed `JarAnalysisReport` — signed by the engine's own private key, not
the registry's. The Verdict Registry verifies this signature before accepting the report. No client
trusts the BAE directly; they trust only the Verdict Registry's quorum-based `RegistryVerdict`.

---

## Discovery: Zero-Infrastructure to Enterprise Scale

JGDMS service discovery scales from a laptop LAN to a global IPv6 network.

### Entry Point: `lookup.<domain>:4160`

For any deployment, the entry point is a single DNS record: `lookup.example.org:4160`. A client
constructs `LookupLocator("jini://lookup.example.org:4160")` and discovers the entire service
ecosystem for that domain. IPv6 restores point-to-point connectivity — no NAT traversal, no relay,
no additional infrastructure.

### Unicast Discovery With Hash Verification

Unicast discovery (TCP) uses hash verification at both ends before any response is sent —
SHA-224, SHA-256, SHA-384, or SHA-512, depending on configuration. `DownloadPermission` and
`DeSerializationPermission` are granted dynamically to authenticated lookup services during
discovery, so a rogue or misconfigured lookup service cannot trick a client into loading arbitrary
code.

### Multicast Discovery (LAN/Enterprise)

For site-local and organization-local networks, JGDMS supports IPv6 multicast discovery signed
with X.500 distinguished names and digital signatures (DSA, RSA, ECDSA). Services announce
themselves; clients find them without any DNS lookup.

### Bootstrap Proxies: Trust Before Unmarshaling

The `SafeServiceRegistrar.lookUp()` method returns *bootstrap proxies* rather than
fully-unmarshaled service proxies. The client establishes cryptographic trust before
deserialization, not after. Only once trust is confirmed — and the Verdict Registry gives a `SAFE`
verdict for the JAR — does the client unmarshal the full proxy and connect directly to the service
over IPv6.

---

## Horizontal and Vertical Scalability

### Horizontal Scaling: Add BAE Instances

The BAE pool (Host 2) is stateless by design. `AnalysisRequest` is self-contained — it carries the
JAR bytes and their SHA-256 hash, so any engine instance can process any request without
coordination. Adding analysis capacity means starting additional BAE instances; they self-register
with the Jini Lookup Service and are automatically discovered by Host 4 for round-robin dispatch.

The Verdict Registry's quorum policy (`SAFE` requires ≥ K engines to agree) means that adding
engines *increases both throughput and confidence* simultaneously.

### Horizontal Scaling: Multiple Lookup Services

Multiple `LookupLocator` instances can be configured across availability zones. DNS-SD SRV records
can enumerate multiple named lookup instances. Services can register with multiple lookup groups,
and clients can query multiple locators with merge semantics.

### Vertical Scaling: World's Fastest Policy Provider

JGDMS's `ConcurrentPolicyFile` is a lock-free, high-throughput `java.security.Policy` replacement.
It uses RFC 3986 URI matching, performs no DNS lookups, and has less than 1% overhead on policy
checks compared to no policy at all. Authorization decisions do not become a bottleneck under high
concurrency.

JERI itself outperforms standard Java RMI. `RFC3986URLClassLoader` is significantly faster than
Java's built-in `URLClassLoader`. Unnecessary DNS calls have been eliminated throughout the
codebase. Atomic serialization (`@AtomicSerial`) outperforms standard Java serialization in
benchmarks.

### Virtual Thread Support

DirtyChai includes full virtual thread support with `SecurityManager` enabled — a combination that
Oracle's JDK never achieved. The SCAP architecture's `ClinitBlockingVisitor` ensures that JAR
files containing blocking class initializers — the main virtual-thread carrier-pin risk — are
flagged before they are ever loaded, enabling confident use of virtual threads at scale.

### Lease-Based Resource Management

All service registrations and event subscriptions in JGDMS are *leased*: they expire unless
actively renewed. This means a departed or crashed service eventually cleans itself from the
registry automatically. At large scale, you don't accumulate stale registrations that require
manual cleanup.

---

## Ease of Configuration and Management

### Externalised Configuration

Every JGDMS service is configured via a `Configuration` object (typically a Jini configuration
file). There are no scattered properties files or XML documents. `ServiceStarter` launches services
from a single configuration that specifies which services to start, which endpoints to export, and
what constraints to apply.

Phoenix (the activation daemon) manages service lifecycle, crash recovery, and group isolation
entirely from configuration — `groupThrottle`, `persistenceSnapshotThreshold`,
`instantiatorPreparer`, and output handlers are all configurable without code changes.

### No Keystore Management

With SPIFFE/SPIRE integration, administrators manage identities through SPIRE registration entries
— not Java keystores. The SPIFFE ID naming convention is human-readable:

- `spiffe://jgdms.example.org/host/lookup` → Lookup Service
- `spiffe://jgdms.example.org/host/bae/engine-2` → second BAE instance
- `spiffe://jgdms.example.org/client/alice` → client JVM for user Alice

Credential rotation is automatic and zero-touch. Revocation is immediate (short-lived SVIDs expire
within an hour). Scaling up adds a new SPIRE registration entry, not a certificate signing
ceremony.

### Dynamic Policy Updates

The `RemotePolicyProvider` and `InMemoryPolicyService` enable live policy updates — administrators
push a new policy without restarting any service. The `DynamicPolicyProvider` handles per-proxy
grants that are automatically cleaned up when proxies are garbage-collected.

### Verdict Registry: Content-Addressed Verdicts

The Verdict Registry is keyed by SHA-256 content hash, not URL. The same JAR served from different
URLs is analyzed once and cached forever. URL changes, CDN migrations, and service moves do not
invalidate existing verdicts. The analysis pipeline scales with the *number of distinct JARs* in
the ecosystem, not the number of services.

---

## What JGDMS Is Good For

JGDMS is the right foundation for:

**Enterprise service meshes** — where services need to be dynamically added and removed, where
authentication and authorization between services must be cryptographically enforced, and where a
compromised service must not be able to escalate its own privileges.

**Financial and regulated industries** — where separation of duties, audit trails, and
demonstrable code-integrity verification are compliance requirements. The SCAP pipeline provides a
cryptographically signed record of every JAR analysis result.

**High-throughput Java services** — where policy checks, deserialization, and service discovery
happen millions of times per second. JGDMS's lock-free policy provider and optimized class loader
mean security does not become a bottleneck.

**Multi-tenant platforms** — where independently-developed service proxies run in the same JVM and
must not be able to access each other's resources. The three-way permission intersection
(`PERMISSIONS.LIST` ∩ `GrantPermission` ceiling ∩ SPIFFE principal scope) prevents privilege
creep.

**Long-running JVM workloads with virtual threads** — where the risk of carrier-thread pinning by
third-party JARs must be detected and managed before those JARs are loaded.

---

## What JGDMS Is Not

JGDMS is **not** a sandbox for running untrusted code. It will not safely isolate malicious
bytecode. Its goal is the opposite: prevent untrusted code from ever being loaded, using
`LoadClassPermission` as the primary gate and SCAP as the pre-analysis pipeline. If you need to
run code you don't trust, you need a different tool (or a different approach).

JGDMS **currently requires Java ≤ 23** (or DirtyChai). Oracle removed the `SecurityManager` API
in Java 24. Running JGDMS on standard OpenJDK 24+ is not supported. DirtyChai is the path forward
for modern JDK versions.

---

## Summary

| Concern | JGDMS / DirtyChai Answer |
|---|---|
| Transport security | TLSv1.3 with per-method constraints, mutual authentication via SPIFFE SVIDs |
| Deserialization safety | `@AtomicSerial`: validation before construction, atomic invariant checking |
| Authorization | Three-layer policy stack, lock-free `ConcurrentPolicyFile`, dynamic grants |
| Code integrity | SCAP five-host pipeline, quorum-based `RegistryVerdict`, signed `JarAnalysisReport` |
| Credential management | SPIFFE/SPIRE: short-lived SVIDs, automatic rotation, no keystores |
| Service discovery | IPv6 unicast + multicast, `LookupLocator("jini://lookup.domain:4160")` |
| Horizontal scale | Stateless BAE pool (add instances freely), replicated Lookup Services |
| Vertical scale | Lock-free policy provider (<1% overhead), faster-than-RMI JERI, virtual thread support |
| Operational simplicity | SPIRE manages identities, externalised Jini configuration, lease-based cleanup, dynamic policy |

JGDMS and DirtyChai represent a rare combination: a platform that takes both security and
performance seriously, where the two properties reinforce rather than trade off against each other.
The result is a distributed services foundation that grows with your architecture — horizontally
across a cluster, vertically within a JVM, and operationally across a team — without compromising
on the security properties that make the whole system trustworthy.

---

*GitHub repositories:*  
- *JGDMS: <https://github.com/pfirmstone/JGDMS>*  
- *DirtyChai: <https://github.com/pfirmstone/DirtyChai>*
