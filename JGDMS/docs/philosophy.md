# JGDMS & DirtyChai: Design Philosophy

## Necessary Complexity, Not Accidental Complexity

Every system accumulates complexity over time. The question worth asking is not "is this complex?"
but "is this complexity load-bearing?" A feature is load-bearing when removing it would cost a
security property, a correctness guarantee, or a performance property that the problem domain
genuinely requires.

JGDMS and DirtyChai are the result of many years of research into distributed systems security on
the JVM. They are not simple in absolute terms — because the problem they solve is not simple in
absolute terms. But there is very little in the design that exists for reasons other than solving a
real, well-understood failure mode. That is a meaningful distinction.

---

## The Fallacies of Distributed Computing

The *Fallacies of Distributed Computing* were first articulated at Sun Microsystems in the 1990s.
L. Peter Deutsch identified the first four fallacies around 1991–1994 while working on distributed
systems at Sun. James Gosling, the creator of Java and a colleague of Deutsch's at Sun, is credited
with adding the remaining four. Bill Joy and Tom Lyon are also associated with the early
formulation. The complete list of eight was later written up and popularised by Arnon Rotem-Gal-Oz
in a widely cited 2006 essay, *"Fallacies of Distributed Computing Explained"*.

These eight assumptions are things that developers commonly believe about distributed systems —
and that are all false, every time, in every production environment:

1. The network is reliable.
2. Latency is zero.
3. Bandwidth is infinite.
4. The network is secure.
5. Topology doesn't change.
6. There is one administrator.
7. Transport cost is zero.
8. The network is homogeneous.

Nearly every piece of accidental complexity in distributed systems can be traced to a design that
believed one or more of these fallacies long enough to ship. JGDMS and DirtyChai were designed from
the outset to treat all eight as permanent, structural properties of the environment — not edge
cases to be handled later.

### Fallacy 1: The network is reliable

Networks drop packets, partition, and fail. A distributed service framework that assumes reliable
delivery will produce systems that hang silently when the network misbehaves. JGDMS addresses this
at the infrastructure level:

- **Lease-based registrations** — every service registration and event subscription expires unless
  actively renewed. A crashed or partitioned service cleans itself out of the registry
  automatically. There is no accumulation of stale state that requires manual intervention.
- **JERI's constraint system** — `UnsupportedConstraintException` is thrown *before* bytes leave
  the client JVM if the transport cannot satisfy the declared security requirements. Failure is
  explicit and local, not silent and remote.
- **Bootstrap proxy trust establishment** — the client verifies cryptographic trust *before*
  unmarshalling the full service proxy. A network-level attacker who intercepts the lookup
  response cannot cause the client to load and execute arbitrary code.

### Fallacy 2: Latency is zero

Every remote call has non-trivial latency. A framework that treats remote calls as syntactically
identical to local calls (as early Java RMI encouraged) trains developers to ignore this cost.
JGDMS makes the boundary explicit:

- **JERI is a distinct invocation layer** — remote calls go through `BasicInvocationHandler` and
  `BasicInvocationDispatcher`, not transparent stubs. The indirection is visible in the code.
- **`MethodConstraints` are declared per-method** — security requirements are attached to the
  specific calls that cross trust boundaries, not applied uniformly to all code. This encourages
  awareness of which calls are remote and what they cost.
- **`@AtomicSerial` over the wire** — marshalling and unmarshalling are explicit, fast, and
  auditable. The performance cost of the boundary is minimized but never hidden.

### Fallacy 3: Bandwidth is infinite

Serialized object graphs crossing a network are not free. JGDMS applies consistent pressure to
keep the wire format lean:

- **`@AtomicSerial` with a declared `serialForm()`** — the wire shape of every serialized class is
  explicit and auditable. There are no hidden fields, no surprise object graph expansions.
- **Content-hash verdict caching** — the Verdict Registry caches analysis results by SHA-256 hash.
  The same JAR is transmitted and analyzed once regardless of how many services reference it.
- **Bootstrap proxies** — clients receive a minimal bootstrap proxy first; the full proxy is only
  unmarshalled after trust is established. Bandwidth is not spent on objects the client may reject.

### Fallacy 4: The network is secure

This is the fallacy that costs the most when violated, and the one that most frameworks address
least seriously. JGDMS treats network insecurity as the baseline assumption, not an exceptional
case:

- **TLSv1.3 with mutual authentication via SPIFFE SVIDs** — every service-to-service call is
  encrypted and mutually authenticated. There is no "trusted internal network" that bypasses this.
- **Per-method `MethodConstraints`** — `ServerAuthentication.YES`, `ClientAuthentication.YES`,
  `Confidentiality.YES`, `Integrity.YES` are enforced by construction on every method that
  declares them. A call that cannot satisfy its constraints fails before bytes leave the JVM.
- **`DigestGrant` + `DigestCodeSource`** — the network cannot be trusted to deliver the same bytes
  at the same URL twice. Content-hash grants ensure that what was audited is what runs.
- **SCAP pipeline** — the network is the delivery mechanism for third-party JARs. Every JAR is
  treated as potentially hostile until it has been independently analyzed and a quorum verdict
  issued.
- **Hardened deserialization** — data arriving from the network is the primary attack surface for
  gadget chains. `@AtomicSerial` and `AtomicMarshalInputStream` treat all inbound serialized data
  as adversarial until proven otherwise.

### Fallacy 5: Topology doesn't change

Services come and go. IP addresses change. Hosts fail and are replaced. A system hardwired to a
fixed topology will require manual intervention every time the topology shifts. JGDMS is built for
topological change as the normal case:

- **Jini service discovery** — clients discover services by capability (interface + attributes),
  not by hard-wired address. When a service moves or is replaced, clients discover the new
  instance without configuration changes.
- **IPv6 multicast and unicast** — service announcement and discovery work across topological
  changes without NAT traversal or relay infrastructure.
- **SPIFFE/SPIRE workload identity** — service identity is bound to the *workload*, not to the
  host IP or DNS name. When a service migrates to a new host, its SPIFFE ID is unchanged; only the
  SVID is reissued.
- **Stateless BAE pool** — analysis engine instances self-register with the Lookup Service and are
  discovered for round-robin dispatch. Adding or removing instances requires no configuration
  change in the pipeline.

### Fallacy 6: There is one administrator

In any non-trivial distributed system, different components are managed by different teams with
different trust levels and different operational responsibilities. A security model that assumes a
single omniscient administrator cannot enforce separation of duties. JGDMS encodes administrative
separation into the authorization model:

- **Three-layer policy stack** — the bootstrap `SpiffePolicyFile` is controlled by the operator,
  `RemotePolicyProvider` grants are managed by the administrator, and `DynamicPolicyProvider`
  grants are issued per-proxy at runtime. No single administrator controls all three layers.
- **Three-way permission intersection** — `PERMISSIONS.LIST` (declared by the code author) ∩
  `GrantPermission` ceiling (set by the administrator) ∩ SPIFFE principal scope (issued by the
  SPIRE control plane) means the effective permission requires agreement across three independent
  authorities. A compromised administrator account cannot unilaterally escalate a component's
  privileges.
- **SPIRE as the identity control plane** — workload identity is managed by the SPIRE operator
  independently of the application administrator. Credential rotation and revocation do not require
  coordination with the application team.
- **`InMemoryPolicyService` live updates** — policy can be updated at runtime by an authorized
  administrator without restarting services, but the update is constrained by the bootstrap policy
  ceiling. Live policy authority is bounded.

### Fallacy 7: Transport cost is zero

Every byte serialized, every TLS handshake, every policy check, every class load has a cost.
Frameworks that hide these costs produce systems that perform well in benchmarks and poorly under
load. JGDMS makes transport costs explicit and minimizes them:

- **Lock-free `ConcurrentPolicyFile`** — policy checks under concurrent load add less than 1%
  overhead compared to no policy. Authorization is not the bottleneck.
- **JERI outperforms standard Java RMI** — the explicit invocation layer is faster than the
  transparent stub model it replaces.
- **`RFC3986URLClassLoader`** — faster than Java's built-in `URLClassLoader`; unnecessary DNS
  lookups have been eliminated throughout the codebase.
- **Content-hash verdict cache** — transport cost for JAR analysis is paid once per distinct JAR,
  not once per service instance or per client query.
- **Virtual Thread dispatch** — one virtual thread per request means carrier threads are never
  blocked waiting for a slow client. The cost of concurrency is paid by the scheduler, not by
  thread stack allocation.

### Fallacy 8: The network is homogeneous

In any real deployment, the network carries traffic between JVMs of different versions, services
deployed at different times, and components written by different teams. A framework that assumes
all participants use the same protocol version, the same JDK, and the same security configuration
will break silently when that assumption is violated. JGDMS handles heterogeneity explicitly:

- **JERI versioned wire protocol** — the dispatcher reads the protocol version byte and handles
  `PREVIOUS_VERSION`, `VERSION`, and `VERSION_WITH_PRINCIPALS_AND_ACC` explicitly. Unknown
  versions produce a `MISMATCH` response, not silent corruption.
- **DirtyChai / standard JDK code paths** — `CALL_AS_MULTI_SUBJECT` and `CURRENT_ALL_METHOD` are
  cached at class-load time via reflection. The multi-Subject dispatch path is used on DirtyChai;
  the single-Subject path is used on a standard JDK. Both are correct; neither silently degrades.
- **`AccessControlContextSerializer` anonymous placeholder domains** — when a serialized
  `AccessControlContext` crosses a JVM boundary, unverifiable `ProtectionDomain`s are
  reconstructed as anonymous placeholders. Their permission ceilings are preserved without
  asserting a specific identity that the receiving JVM cannot verify. The system degrades
  gracefully under heterogeneity.
- **Pluggable JERI transports** — the constraint system is transport-independent. A service can
  expose the same interface over TLS, Kerberos, or a future transport without changing the
  authorization model.

---

## The Industry Already Tried the Simpler Alternatives

The clearest evidence that JGDMS's complexity is necessary is that the industry spent decades
trying the simpler approaches and documenting their failures:

| Simpler alternative | Why it failed | JGDMS/DirtyChai answer |
|---|---|---|
| Network-level firewalls for service security | Lateral movement and supply-chain attacks bypass the perimeter | Per-method `MethodConstraints` enforced before bytes leave the client JVM |
| URL as trust boundary for remote code | CDN poisoning: same URL, different bytes after a compromise | `DigestGrant` + `DigestCodeSource`: grants conditioned on SHA-256 content hash |
| Standard `Serializable` / `readObject` | Gadget chains; billions in breach costs across the industry | `@AtomicSerial`: validation before construction, fail-fast, no field assigned until all invariants pass |
| Long-lived keystores for service identity | Certificate management toil, rotation failures, revocation gaps | SPIFFE/SPIRE: short-lived SVIDs, automatic rotation, no keystores |
| Removing `SecurityManager` (OpenJDK 17–24) | Lost the only code-source permission boundary on the JVM | DirtyChai restores, improves, and extends the authorization infrastructure |
| Ad-hoc service registries without trust | No cryptographic verification before code loads | Jini discovery + bootstrap proxy + Verdict Registry quorum |

None of the JGDMS answers are gold-plating. Each one replaces something that failed in production
systems, often at significant cost.

---

## Complexity That Cannot Be Removed

Some parts of the design look elaborate until you ask what attack they prevent. At that point it
becomes clear that simplification would not be a refactoring — it would be a vulnerability:

**The five-host SCAP topology.** Collapsing the Safe Codebase Audit Pipeline to fewer hosts seems
appealing until you ask: "what if the analysis engine (Host 2) is compromised?" The no-direct-path
from the BAE pool to the Verdict Registry exists precisely so that a compromised analysis engine
cannot write verdicts. Removing that isolation removes that guarantee.

**The three-layer Subject hierarchy.** `WorkerSubject`, `UserSubject`, and remote-process identity
carried in a serialized `AccessControlContext` look like over-engineering until you ask: "how do
you hold a JVM process identity, a human user identity, and a remote caller's identity on the same
dispatch thread, simultaneously, without conflating them?" Each layer exists because conflating any
two creates an exploitable ambiguity — a service that cannot tell whether a permission was granted
to the machine or to the user cannot enforce separation of duties.

**`@AtomicSerial`'s fail-fast `check(GetArg)` throw.** Accumulating validation errors (returning a
`Result<T>` with all field violations) is the right pattern for application-layer input validation
where the source is trusted. It is the wrong pattern for deserialization of potentially hostile
data. Every additional field read after a validation failure is an opportunity for a gadget chain
to fire. The exception thrown by `check(GetArg)` is not a design limitation — it is the security
mechanism. It is also uncircumventable by subclasses in a way that a return value is not.

**The three-way permission intersection.** `PERMISSIONS.LIST` ∩ `GrantPermission` ceiling ∩
SPIFFE principal scope means no single party controls the effective permission for a codebase. A
supply-chain compromise of one component cannot silently escalate that component's own privileges,
because the intersection still requires the other two axes to agree.

---

## Security and Performance Reinforce Each Other

A common assumption is that security and performance trade off against each other — that hardening
a system means slowing it down. JGDMS and DirtyChai were designed to disprove this assumption:

- `ConcurrentPolicyFile` is a lock-free, high-throughput policy provider with less than 1%
  overhead on policy checks compared to no policy at all. Authorization does not become a
  bottleneck under high concurrency.
- `@AtomicSerial` outperforms standard Java serialization in benchmarks, while being significantly
  safer.
- `RFC3986URLClassLoader` is faster than Java's built-in `URLClassLoader`.
- JERI outperforms standard Java RMI.
- The Verdict Registry is keyed by SHA-256 content hash, not URL. The same JAR analyzed once is
  cached permanently. Security analysis scales with the number of distinct JARs, not the number
  of services.
- SPIFFE/SPIRE short-lived SVIDs eliminate certificate management toil without adding latency to
  the request path — rotation happens out-of-band.

When security and performance reinforce each other in this way, it is a signal that the
abstractions are well-chosen. A badly chosen abstraction tends to leak in both directions.

---

## New Language Features Slot In, Not Bolt On

A design at the right level of abstraction absorbs new capabilities without structural rework. The
adoption of Java Virtual Threads is the clearest example of this property in JGDMS/DirtyChai:

- JERI dispatch was already one-request-one-thread — exactly the model Virtual Threads are
  designed for. No restructuring was needed.
- The `WorkerSubject`-as-ambient-in-every-`ProtectionDomain` design means identity propagation
  does not depend on thread-locals, so it survives carrier thread switches transparently.
- SCAP's `ClinitBlockingVisitor` turns the biggest Virtual Thread risk — carrier thread pinning by
  blocking `<clinit>` methods — into a solved, audited, pre-deployment problem rather than an
  operational unknown discovered under load.
- JPMS module boundaries reinforce `LoadClassPermission` as a gate: code that was never loaded
  cannot be exploited, and explicit `exports`/`requires` declarations replace the implicit
  classpath-everything trust model.

Each of these new platform features did not require JGDMS to be redesigned. They fit because the
existing design had the right shape. That is a strong signal that the abstractions were
well-chosen.

---

## The Trusted Codebase as a Security Property

The security property that is most often underappreciated in distributed systems is the size of
the trusted codebase. Every line of code that runs with elevated trust is a line that must be
audited, reasoned about, and defended. JGDMS and DirtyChai apply consistent pressure to minimize
this surface:

- `LoadClassPermission` as a gate: code that was never loaded cannot be exploited.
- SCAP pre-auditing: untrusted JARs are analyzed before any client ever deserializes an object
  from them. A signed `JarAnalysisReport` is a cryptographic record that the analysis was
  performed.
- The three-way permission intersection: no single component can expand its own trust
  unilaterally.
- JPMS modules: explicit trust boundaries at the module level replace the classpath's implicit
  "everything trusts everything" model.
- Policy file tooling: 1,000 lines of generated policy file are far easier to audit than
  1,000,000 lines of third-party library code. The policy file is the auditable artefact.

These mechanisms compound. A vulnerability in a third-party library that was never granted
`LoadClassPermission`, never cleared the Verdict Registry, and never appeared in a policy grant is
not a vulnerability in the running system.

---

## Pragmatic Simplicity

*Pragmatic simplicity* is not the same as *minimal line count* or *fewest moving parts*. A system
is pragmatically simple when:

1. Every component exists because a simpler alternative was tried and failed.
2. Removing any component would cost a property (security, correctness, or performance) that the
   problem domain genuinely requires.
3. New platform capabilities slot in without structural rework, because the abstractions were
   well-chosen.
4. Security and performance reinforce each other rather than trading off.

By these measures, JGDMS and DirtyChai are at an appropriate level of pragmatic simplicity for the
problem they solve: security in untrusted networks, safe remote class resolution, and distributed
computing at scale on the JVM.

The complexity that remains is the complexity of the problem domain itself, expressed as directly
as the language and platform allow.

---

## Design Principles Derived From This Philosophy

These principles guide decisions about what to add, what to change, and what to leave alone:

1. **Ask what attack a feature prevents, not just what capability it adds.** A feature that adds
   capability without closing an attack surface may be adding complexity without adding security.

2. **Fail-fast is a security mechanism, not a debugging limitation.** In adversarial contexts,
   early termination prevents gadget chains. Do not confuse fail-fast with poor error reporting —
   they serve different contexts.

3. **Isolation invariants are not over-engineering.** If removing an isolation boundary would give
   a compromised component write access to a trust boundary (verdicts, policy grants, identity
   assertions), the isolation must be preserved.

4. **The intersection of independent authorities is stronger than any single authority.** Requiring
   agreement across code identity, principal identity, and workload identity simultaneously means
   that compromising one axis is not sufficient to escalate privilege.

5. **The trusted codebase is a security metric.** Decisions that shrink the trusted codebase (more
   restrictive grants, stricter `LoadClassPermission` policies, smaller JPMS module surfaces) are
   security improvements even when they add operational friction.

6. **New platform features should fit, not require restructuring.** If a new Java feature (Virtual
   Threads, ScopedValues, JPMS, Records, Sealed Classes) requires significant structural rework to
   adopt, that is a signal that the existing abstraction may be at the wrong level. If it slots in
   naturally, it confirms the abstraction was well-chosen.

7. **Security and performance are not a trade-off.** When a security mechanism is also the fastest
   correct implementation (lock-free policy provider, content-hash verdict cache, fail-fast
   deserialization), that is not a coincidence — it is a consequence of choosing the right
   abstraction.

8. **Design for the fallacies, not against them.** The eight Fallacies of Distributed Computing
   are permanent properties of the environment, not edge cases. Every component that assumes the
   network is reliable, secure, homogeneous, or administered by a single party is a component that
   will fail in production. Design so that when the network misbehaves — and it will — the system
   fails explicitly, locally, and safely rather than silently and corruptly.
