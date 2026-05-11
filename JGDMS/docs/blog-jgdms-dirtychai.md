# JGDMS & DirtyChai: Secure, Scalable, Dynamically Discoverable Microservices for the JVM

## Introduction

Modern distributed systems face an uncomfortable trade-off: the more open and composable a platform
is, the harder it becomes to secure. Remote code loading, serialized objects crossing trust
boundaries, and third-party service proxies all expand the attack surface. Most platforms paper over
these problems with network-level firewalls and hope for the best.

**JGDMS** (*Jini Global Distributed Micro Services*) and its companion OpenJDK fork **DirtyChai**
take a different path. They embed security deep into the infrastructure itself — in the transport,
the serialization mechanism, the policy engine, and the codebase loading pipeline — while
simultaneously delivering the performance and scalability needed for production distributed systems.

Improvements made by OpenJDK, such as TLSv1.3, Virtual threads, JPMS Modules and ScopedValues have opened
up opportunities to significantly improve support for high performance security infrastructure. Innovation happens
elsewhere too, such as SPIFFE|SPIRE.

**DirtyChai** Scales vertically, **JGDMS** Scales horizontally.

---

## What Is JGDMS?

**JGDMS** is a security-hardened fork of [Apache River](https://river.apache.org/) (née Jini), the Sun
Microsystems framework for self-organizing, dynamically-discoverable distributed services. Where
RPC frameworks stop at "call a remote method," Jini/JGDMS goes further: services announce
themselves on IPv6 networks, clients discover them by capability rather than by hard-wired address,
and trust is established cryptographically before any code runs. SPIFFE provides universal identity
control for process workflows.

**JGDMS** is described in its own project descriptor as:

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
OpenJDK deprecated in Java 17 and removed entirely in Java 24. Without these APIs, you cannot
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
- SpiffeX509TrustManager and SpiffeX509KeyManager - SPIFFE/SPIRE Zero Touch Certificate Management.

DirtyChai completes what Sun Microsystems and Bill Joy started. Running JGDMS on DirtyChai restores the full
authorization semantics and lets the platform evolve beyond the Java 23 ceiling.

![DirtyChai mascot: a tough chai mug in a hard hat with a SPIFFE badge](images/dirty-chai-mascot.svg)

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
automatically carries the **SPIFFE Worker `Subject`** of the calling client for the
lifetime of the remote method invocation. Service implementations do not write authentication
boilerplate — the infrastructure guarantees the authenticated identity is on the thread.

### SPIFFE/SPIRE: Zero-Touch Certificate Management

In a fleet of services, long-lived keystores are a management and security liability. JGDMS and DirtyChai
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

### Sealed Subject Hierarchy: Three Identity Layers

DirtyChai introduces a sealed `Subject` hierarchy with three distinct identity layers, each with
its own carrier, lifetime, and routing rule:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DirtyChai Subject Hierarchy                      │
│                                                                     │
│  Subject (vanilla, legacy)                                          │
│   ├── WorkerSubject  (sealed, permits SpiffeSubject only)           │
│   │     • Process workload identity (SPIFFE SVID)                   │
│   │     • Baked into every ProtectionDomain at class-load time      │
│   │     • AMBIENT — survives all doPrivileged boundaries            │
│   │     • Never passed to callAs() or doAs() — illegal              │
│   │                                                                 │
│   └── UserSubject  (final)                                          │
│         • Human user identity (JWT/OIDC — see below)               │
│         • Carried in SCOPED_SUBJECT ScopedValue<Subject[]>          │
│         • Installed per-request via Subject.callAs(...)             │
│         • Injected into ProtectionDomain array by AccessController  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│               Three Identity Layers on a Dispatch Thread            │
│                                                                     │
│  Layer 1 — Process Worker  (WorkerSubject, ambient)                 │
│    SpiffeSubject baked into every ProtectionDomain by               │
│    SecureClassLoader. Always present at every checkPermission.      │
│    Never reinstalled per-request.                                   │
│                                                                     │
│  Layer 2 — Remote Process  (serialized ACC ProtectionDomains)       │
│    Remote client's WorkerSubject principals travel inside a         │
│    serialized AccessControlContext over the JERI wire.              │
│    A DomainCombiner on the receiving JVM verifies and strips        │
│    unverifiable domains. Shed at doPrivileged boundaries.           │
│                                                                     │
│  Layer 3 — User  (UserSubject via callAs)                           │
│    Per-request human identity bound by JERI dispatcher via          │
│    Subject.callAs(userSubject, () -> invoke(...)).                  │
│    AccessController.getContext() bakes user principals directly     │
│    into the ProtectionDomain array. Survives doPrivileged.          │
└─────────────────────────────────────────────────────────────────────┘
```

**User Subject** (`UserSubject`) — represents a human user. User identity is established via
**JWT/OIDC** using `JwtLoginModule` from the `jgdms-security-jwt` module. The resulting
`UserSubject` carries `JwtPrincipal` instances (e.g. `"sub:alice@example.org"`,
`"group:admins"`) and is installed per-request via `Subject.callAs(jwtUserSubject, () -> ...)`.
Kerberos (`KerberosPrincipal` / `KerberosEndpoint`) is supported for **legacy deployments only**.

JGDMS's JERI layer reads all active user Subjects via `Subject.currentAll()` and transmits them
to the server in **JERI wire protocol version `0x02`** — an in-band channel distinct from and
independent of the TLS handshake. The protocol supports up to **16 user `Subject`s per call**,
each carrying up to **64 principals**:

```
0x02 user-Subject block:
  subjectCount : u16          (max 16)
  per Subject:
    principalCount : u16      (max 64)
    per Principal:
      className : UTF-8
      name      : UTF-8
```

On the server side, the `BasicInvocationDispatcher` reads this block, constructs a `Subject[]`,
wraps it in a `UserSubjectImpl` (which implements `ClientUserSubject`), and nests the entire
invocation inside a chain of `Subject.callAs()` calls — one per received Subject. Service code
retrieves *all* received user Subjects via:

```java
// inside a dispatched method:
ServerContext ctx = ServerContext.getServerContext();
ClientUserSubject cus = (ClientUserSubject) ctx.getServerContextElement(ClientUserSubject.class);
Subject[] users = cus.getUserSubjects();       // the full Subject[]
Subject  primary = cus.getUserSubject();        // subjects[0] — convenience for single-user callers
```

This makes it possible for a single RPC to carry, for example, both the end-user's JWT identity
and a delegation chain subject, without any out-of-band negotiation.

`Subject.current()` returns only what was bound via `callAs` — it never falls back to the
`AccessControlContext`. This ensures the server can always distinguish TLS-verified machine
identity from wire-asserted human identity.

![JERI multi-Subject party bus: 16 JWT-wielding passengers arrive at the service endpoint](images/multi-subject-party-bus.svg)

**Process Worker Subject** (`WorkerSubject`) — represents the JVM process itself, not any
particular end user. With SPIFFE/SPIRE, DirtyChai's `SpiffeCredentialManager` constructs a sealed
`SpiffeSubject extends WorkerSubject` and bakes it into every `ProtectionDomain` via
`SecureClassLoader` at class-load time. This Subject contains:

- A `SpiffePrincipal` derived from the SVID URI SAN (e.g.
  `spiffe://jgdms.example.org/host/bae/engine-2`)
- An `X500Principal` derived from the certificate Subject DN (e.g.
  `CN=bae-engine-2,O=example.org`)
- The short-lived X.509 credential (certificate chain + private key) — never written to disk

Because the `WorkerSubject` is **ambient** — present in every `ProtectionDomain` regardless of
`doPrivileged` nesting — the server never needs to reinstall it per request. When a service calls
another service (e.g. the Codebase Downloader submitting a JAR to a BAE instance), the JERI SSL
endpoint locates the outbound TLS credential via `SpiffeSubjectHolder` automatically.

**Remote process identity** travels differently: the remote client's `WorkerSubject` principals
are carried inside a serialized `AccessControlContext` transmitted over the JERI wire. A
`DomainCombiner` on the receiving JVM strips any domain whose `httpmd:` SHA-256 hash does not
verify or whose `SpiffePrincipal` is outside the trusted SPIFFE trust domain. Verified domains
participate in `RemotePolicy` checks as call-stack domains and are shed at `doPrivileged`
boundaries — they are scoped to *where the call came from*, not *who the local process is*.

This layering means a single server JVM simultaneously holds:

- Its own process worker identity (ambient `WorkerSubject` in every `ProtectionDomain`)
- Zero or more dispatch threads, each running a `UserSubject` scope for the human caller and
  carrying verified remote-process domains from the serialized incoming ACC

No session state, no thread-local leakage between calls, and no boilerplate in service code.

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

### Content-Hash Policy Grants: `DigestGrant` and `DigestCodeSource`

A URL is not a reliable security boundary — the same URL can serve different bytes after a CDN
update or supply-chain compromise. DirtyChai extends the policy language with two new types that
condition grants on the **SHA-256 content hash of the JAR**, not merely its URL:

- **`DigestCodeSource`** — a `CodeSource` subclass that carries the JAR's `byte[] digest` and
  `String digestAlgorithm`. `SecureClassLoader` populates this when loading from a
  content-verified JAR.
- **`DigestGrant extends URIGrant`** — a policy grant that checks the URI first and then verifies
  `DigestCodeSource.getDigest()` matches. Expressed in policy files as:

```
grant digest "SHA-256:4e07408562bedb8b60ce05c1decafe11",
      codeBase "https://repo.example.org/order-processor.jar"
      principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org" {
    permission net.jini.security.AccessPermission "submitOrder";
};
```

Even if an attacker replaces the JAR at the same URL, the content hash does not match and the
grant simply does not apply. `DigestGrant` is integrated with `PermissionGrantBuilder` and
`DefaultPolicyScanner`/`DefaultPolicyParser` so the full `SecurityPolicyWriter` → policy-file
round-trip works transparently.

The JERI transport also carries `DigestCodeSource`-backed `ProtectionDomain`s inside the
serialized `AccessControlContext` (`AccessControlContextSerializer`, v21+), so that
`DigestGrant` policy checks work correctly on the receiving JVM without any compile-time
dependency on DirtyChai.

### Three-Layer Authorization Stack

Authorization in JGDMS is not a single on/off switch. It is a composable stack of three policy
providers:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                  Three-Layer Policy Stack (outermost first)              │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  DynamicPolicyProvider  (per-proxy, GC-scoped grants)              │  │
│  │    • Security.grant() called at proxy-preparation time             │  │
│  │    • Grant dies when proxy is garbage-collected (auto-cleanup)     │  │
│  │    • Hot path is write-free (sweeper runs every 60 s)              │  │
│  │  ┌──────────────────────────────────────────────────────────────┐  │  │
│  │  │  RemotePolicyProvider  (djinn-wide, session grants)          │  │  │
│  │  │    • Grants pushed live from InMemoryPolicyService           │  │  │
│  │  │    • replace() via RemoteEvent — no service restart needed   │  │  │
│  │  │  ┌────────────────────────────────────────────────────────┐  │  │  │
│  │  │  │  SpiffePolicyFile  (bootstrap, JVM lifetime)           │  │  │  │
│  │  │  │    • Fetched from HTTPS server authenticated by SVID   │  │  │  │
│  │  │  │    • Fail-secure: JVM won't start if server down       │  │  │  │
│  │  │  │    • Refreshes on every SVID rotation (~1 hour)        │  │  │  │
│  │  │  └────────────────────────────────────────────────────────┘  │  │  │
│  │  └──────────────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│              Three-Way Permission Intersection at Grant Time             │
│                                                                          │
│   What the proxy's ClassLoader declares   (META-INF/PERMISSIONS.LIST)   │
│                       ∩                                                  │
│   What the caller is authorised to give   (GrantPermission ceiling       │
│                                            in RemotePolicyProvider)      │
│                       ∩                                                  │
│   What the SPIFFE principal scope permits (principal scoping on          │
│                                            DynamicPolicyProvider grant)  │
│                       =                                                  │
│   Effective dynamic grant, scoped to this specific authenticated         │
│   endpoint instance.  No single party controls the outcome.              │
└──────────────────────────────────────────────────────────────────────────┘
```

| Layer | Grant issuer | Grant lifetime | Revocation |
|---|---|---|---|
| `SpiffePolicyFile` | Operator (HTTPS bootstrap server) | JVM lifetime | `refresh()` on SVID rotation |
| `RemotePolicyProvider` | Administrator via `InMemoryPolicyService` | Djinn session | `replace()` via `RemoteEvent` |
| `DynamicPolicyProvider` | Client via `VerifyingProxyPreparer` + `GrantPermission` | Proxy reachability | Automatic on GC |

The effective permission for any codebase is the **intersection** of what the code declares it
needs (`PERMISSIONS.LIST`), what the grant ceiling allows (`GrantPermission`), and what the SPIFFE
principal scope permits. No single party controls the outcome unilaterally — a supply-chain
compromise of one component cannot silently escalate its own privileges.

A grant targeting both a SPIFFE workload principal and a human JWT principal prevents privilege
escalation even if one axis is compromised:

```
// JWT/OIDC — preferred user identity
grant codeBase "httpmd://repo.example.org/order-processor.jar#SHA256:abc123"
      principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org"
      principal net.jini.jeri.ssl.SpiffePrincipal "spiffe://.../svc/order-processor" {
    permission net.jini.security.AccessPermission "submitOrder";
};
```

This grant applies only when *alice* is using *specifically the order-processor workload* to access
the JAR with that exact SHA-256 content hash. An attacker who controls one axis (e.g. replaces the
JAR at the same URL) still cannot match all three.

![The Permission Burger: three-layer authorization stack as a colorful stacked burger](images/permission-burger.svg)

> **See also:** [Diagram 2 — Three-layer policy stack](<Big picture security architecture/diagram2_three_layer_policy_stack.svg>) · [Diagram 4 — GrantPermission & role management](<Big picture security architecture/diagram4_grantpermission_role_management.svg>)

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

It's worth noting that code repositories assembled prior to runtime are also subject to 
library vulnerabilities and transient dependency vulnerabilities.

![The JVM Club bouncer: SCAP turning away dangerous JARs at the door](images/jar-bouncer.svg)

### The Five Hosts

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                  Safe Codebase Audit Pipeline (SCAP)                         │
│                                                                              │
│  Host 1 — Jini Lookup Service                                                │
│    Stores marshalled service items opaquely.                                 │
│    Fires events when new services register.           ◄──── clients query   │
│           │                                                                  │
│           │ new service registered (event)                                   │
│           ▼                                                                  │
│  Host 4 — Codebase Downloader  ◄── ONLY component with outbound internet    │
│    Downloads JARs proactively on new service registrations.                  │
│           │                                                                  │
│           │ AnalysisRequest (JAR bytes + SHA-256)                            │
│           ▼                                                                  │
│  Host 2 — BAE Pool  (SELinux-isolated, stateless, replicated N×)            │
│    Analyzes JAR bytecode with ASM visitors.                                  │
│    Signs JarAnalysisReport with its own private key.                         │
│    No outbound internet. No exec. No JNI. No FFM.                           │
│    Abnormal exit → CrashReport condemns the codebase.                        │
│           │                                                                  │
│           │ signed JarAnalysisReport   (NO direct path from Host 2→Host 3   │
│           │ goes via Host 4 / client)  ← this isolation is intentional      │
│           ▼                                                                  │
│  Host 3 — Verdict Registry  ◄──────────────────── clients query before      │
│    Accumulates signed reports.                         unmarshalling proxy   │
│    Issues RegistryVerdict (SAFE / DANGEROUS / INCONCLUSIVE)                 │
│    only when a quorum of independent BAE engines agrees.                     │
│                                                                              │
│  Host 5 — JFR Telemetry Service  (reactive, NO connection to Hosts 2/4)     │
│    Receives VirtualThreadPinned JFR events from client JVMs.                 │
│    Triggers re-analysis without letting clients influence verdicts directly. │
└──────────────────────────────────────────────────────────────────────────────┘

Key isolation invariants:
  Host 2 → Host 3: NO direct connection  (compromised BAE cannot write verdicts)
  Host 4 ↔ Host 5: NO connection         (JFR flood cannot DoS analysis pipeline)
  Clients never interact with Host 2 or Host 4 directly
```

**Crucially:**
- Host 2 and Host 3 have no direct connection — a compromised analysis engine cannot write verdicts
  to the registry.
- Host 4 and Host 5 have no direct connection — a flood of JFR events from a misbehaving client
  cannot DoS the analysis pipeline.
- An abnormal JVM exit (Phoenix crash) on Host 2 is itself a security signal: Phoenix submits a
  `CrashReport` directly to the Verdict Registry, condemning the codebase that caused the crash.

> **See also:** [Diagram 1 — SCAP five-host pipeline](<Big picture security architecture/diagram1_scap_five_hosts.svg>)

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

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     JGDMS Service Discovery Flow                           │
│                                                                            │
│  Client                  Lookup Service (Host 1)       Target Service      │
│    │                           │                            │              │
│    │── LookupLocator ─────────►│                            │              │
│    │   ("jini://lookup.x:4160")│                            │              │
│    │                           │                            │              │
│    │◄── bootstrap proxy ───────│   (hash-verified unicast)  │              │
│    │    (trust established      │                            │              │
│    │     before unmarshalling)  │                            │              │
│    │                           │                            │              │
│    │── query Verdict Registry ─────────────────────────────►│ Host 3       │
│    │   (SHA-256 hash of proxy JAR)                          │              │
│    │◄── RegistryVerdict: SAFE ─────────────────────────────►│              │
│    │                                                        │              │
│    │── unmarshal full proxy ── (ProxyCodebaseSpi: ClassLoader, BAE gate)   │
│    │                                                        │              │
│    │── TLS 1.3 + SPIFFE SVID ──────────────────────────────►│              │
│    │   (mutual authentication; method constraints enforced)  │              │
│    │◄── response ──────────────────────────────────────────►│              │
└────────────────────────────────────────────────────────────────────────────┘
```

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

> **See also:** [Diagram 3 — Proxy lifecycle (export → dynamic grant)](<Big picture security architecture/diagram3_proxy_lifecycle.svg>)

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
OpenJDK never achieved. The SCAP architecture's `ClinitBlockingVisitor` ensures that JAR
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

### Tools to generate policy files

**JGDMS** provides tooling to generate policy files for auditing before deployment, 1,000 lines
of policy file are far easier to audit that 1,000,000 lines of code in third party libraries.

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

JGDMS **currently requires Java ≤ 23** (or DirtyChai). OpenJDK removed the `SecurityManager` API
in Java 24. Running JGDMS on standard OpenJDK 24+ is not supported. DirtyChai is the path forward
for modern JDK versions.  **DirtyChai** is required for SPIFFE support and enhanced security, such
as JarFile hardening against untrusted input and additional guards, BAE is used to cover security
gaps that authorization cannot defend against.

---

## Full Security Architecture

The diagram below shows how all the pieces fit together — SCAP pipeline, SPIFFE/SPIRE workload
identity, three-layer policy stack, proxy lifecycle, `GrantPermission` intersection, and the
`Subject` identity model — in a single view:

![](<Big picture security architecture/diagram5_full_security_architecture.svg>)

---

## Summary

| Concern | JGDMS / DirtyChai Answer |
|---|---|
| Transport security | TLSv1.3 with per-method constraints, mutual authentication via SPIFFE SVIDs |
| Deserialization safety | `@AtomicSerial`: validation before construction, atomic invariant checking |
| Authorization | Three-layer policy stack, lock-free `ConcurrentPolicyFile`, dynamic grants |
| Code integrity | SCAP five-host pipeline, quorum-based `RegistryVerdict`, signed `JarAnalysisReport` |
| Content-hash grants | `DigestGrant` + `DigestCodeSource`: grants conditioned on SHA-256 JAR hash, not just URL |
| User identity | JWT/OIDC via `JwtLoginModule`/`JwtPrincipal`; sealed `UserSubject`; per-request `callAs` |
| Multi-user calls | JERI protocol `0x02`: up to 16 `UserSubject`s × 64 principals per call; `ClientUserSubject.getUserSubjects()` |
| Workload identity | Sealed `WorkerSubject` (SPIFFE SVID), ambient in every `ProtectionDomain` |
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
