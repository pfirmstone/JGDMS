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
