# Attack model: identity-based authorization vs. kernel isolation

Working notes from an ongoing r/java discussion (thread starting at
https://www.reddit.com/r/java/comments/1ui2dyh/, exchange with u/pron98)
defending the design decision to keep/refactor an authorization layer
after SecurityManager's removal, rather than relying solely on
kernel-level isolation. Source material for a planned article series.

## The core framing

Kernel-level isolation (namespaces, seccomp, capabilities) and
identity-based authorization are not competing for the same job:

- **Containment** (kernel): given code is already running, limit the
  blast radius. This is what pron98's "kernel is more effective and
  cheaper" argument is correct about, and it's why we use process
  isolation for untrusted third-party proxies.
- **Admission control** (this framework): given a digest (code
  identity), a SPIFFE workload identity (process identity), and a JWT
  (user identity), decide whether to load this code / parse this
  stream *at all*, before anything runs. The kernel has no visibility
  into any of that — it can contain a process beautifully once started,
  but can't tell you whether you should have started it, or accepted
  this byte stream, in the first place.

The granularity argument matters for the cost comparison: kernel
mechanisms operate at process/uid granularity. A single JVM process
legitimately hosting many remote parties' code and data at once (the
entire premise of a distributed object system — discover a service,
load its proxy, talk to it, discover another) would need one process
per remote identity to get kernel-grade isolation between them. A
per-connection identity check (digest + SPIFFE + JWT) inside one
process is the cheaper alternative for that workload shape — **for the
access-control dimension only**. It is not a substitute for process
isolation where actual containment is the requirement: it doesn't give
separate address spaces (a tenant's authorized code can still reach
whatever object references it's handed, or shared statics), doesn't
give fault containment (a JVM-fatal condition in one tenant's code
takes down the whole process, all tenants included), and doesn't give
resource containment (no cgroup-equivalent limits between tenants
sharing one heap — this is why resource exhaustion stays out of scope
below). Where containment, not just admission, is the actual
requirement, policy routes that party to a real OS process instead.
These are complementary tiers, not one replacing the other.

## Attack table

| Attack | Classic mechanism / CVE | Why it recurs | How the model closes it structurally |
|---|---|---|---|
| DNS cache poisoning / URL replay / TOCTOU on code identity | RMI codebase annotation, HTTP(S) codebase URLs | A URL/DNS name was never proof of identity, only a location — still true wherever codebase URLs remain enabled | Digest-based code identity (deterministic bytecode normalization); a URL locates bytes, it never establishes trust |
| Deserialization gadget-chain RCE / invariant bypass | Commons-Collections/ysoserial gadget chains (e.g. CVE-2015-4852); stream dictates classes, `readObject` runs before validation | Filtering (JEP 290/415) is attack-specific and opt-in, not structural — new gadgets keep surfacing (Apache MINA CVE-2026-41635, patched, then bypassed again as CVE-2026-42779) | No `Serializable`; construction goes through validating constructors; deny-by-default permission check against the full inheritance hierarchy before unmarshalling |
| RMI-registry-as-malicious-server tricking a client | An Trinh's Black Hat EU finding; JEP 290 has no default filter for client-side deserialization from a registry | Classic RMI's trust model is asymmetric — the server filters clients, nothing filters what a client trusts back | Mutual TLS 1.3 authentication before any download in either direction; permission-checked deserialization applied symmetrically to both endpoints |
| JNDI/codebase remote class loading (Log4Shell-class) | `trustURLCodebase` flag, LDAP/RMI reference factories (CVE-2021-44228) | Defaulted off since 2013, but the mechanism still ships and still gets flipped back on by legacy interop configs | No codebase-URL-as-identity concept exists at all — not even as an opt-in escape hatch |
| Reflection-based sandbox escape / AllPermission-by-default | CVE-2012-4681, CVE-2013-0422 — a trusted platform class with an exploitable reflection primitive disables or routes around SM entirely | Stack-based "am I on the trusted call path" trust collapses the moment any one trusted class has a usable gadget — single point of failure | `LoadClassPermission` gated on digest, not code-source trust; no single class's compromise implies platform-wide trust; bytecode analysis prior to load |
| Confused deputy / ambient authority | Viral `doPrivileged` (classic SM); generalized case: SSRF → cloud-metadata credential theft (Capital One, 2019) | Authority bound to code/process rather than to the identity making the request; any privileged boundary that doesn't check "on whose behalf" is exploitable | User identity travels via `ScopedValue` and survives privileged boundaries; process identity (SPIFFE) and code identity (digest) travel with `ProtectionDomain` — authority is always evaluated against who's actually asking |
| Cross-tenant privilege bleed inside one shared JVM | No specific CVE — a structural gap in kernel/process isolation, which sees only one uid for a multi-tenant process | Kernel isolation is process-granularity; a JVM legitimately hosting many remote parties' code/data has no OS-visible sub-process boundary | Per-connection identity check (digest + SPIFFE + JWT) gates class load and object construction inside the shared process — stops unauthorized cross-tenant capability use (access control), but does **not** replicate memory, fault, or resource containment; where containment itself is required, policy routes that party to a real OS process instead |
| *(explicitly out of scope)* Resource exhaustion (CPU/memory/GPU) | — | Authorization has no visibility into consumption, only admission | Not claimed — handled by bytecode analysis + process isolation + kill-on-violation, not the authorization layer |
| *(explicitly out of scope)* Native memory corruption / native code vulnerabilities | — | Authorization can't verify memory safety | Not claimed — native access routed through process-isolated proxies, same reasoning |

The last two rows are deliberately negative and worth keeping in any
published version — they pre-empt the "SM never handled DoS/native
code either" counter before it's raised again.

## Case study: Subject credential guarding, unreplaced

Verified 2026-07-10 against Oracle's own JDK docs — a concrete, current
example of the "cooperation-fragility" critique pron98 leveled at SM
now reappearing in the API that replaced the piece of SM it removed,
with nothing filling the gap:

- `Subject.getPrivateCredentials()` / `getPublicCredentials()` gate
  *mutation* of the credential sets behind
  `AuthPermission("modifyPrivateCredentials")` /
  `("modifyPublicCredentials")`. The javadoc conditions this
  explicitly: *"If a security manager is installed, the caller must
  have [the permission]... or a SecurityException will be thrown."*
  JEP 486 (delivered, JDK 24) makes it permanently impossible to
  install a SecurityManager — so that condition is now permanently
  false and the check can never fire.
- `Subject.getSubject(AccessControlContext)`, which gated *obtaining* a
  Subject reference at all behind `AuthPermission("getSubject")`, is
  deprecated for removal and replaced by `Subject.current()` /
  `Subject.callAs()` (JDK 18+). Oracle's own migration guide confirms
  the replacement is deliberately decoupled from
  `AccessControlContext`-based enforcement — no equivalent gate is
  documented on it.
- Net effect: both the layer controlling who could reach a Subject
  reference, and the layer controlling who could tamper with its
  credential sets, are gone. Read access to `getPrivateCredentials()`
  was never separately permission-gated even under SM (only mutation
  was), so the accurate claim is about the combination — reference
  acquisition plus mutation guarding both disappearing — not "reads
  were newly exposed."
- Modules don't fill this gap. JEP 403 (Strongly Encapsulate JDK
  Internals) is scoped to hiding JDK-internal implementation classes
  from reflection, not general application authorization. Oracle's own
  "The Security Manager Is Permanently Disabled" page, when it states
  what to use instead, does not mention modules — it names containers,
  hypervisors, and OS sandboxing (macOS App Sandbox, Linux seccomp).

Sources: [Subject javadoc (JDK 23)](https://docs.oracle.com/en/java/javase/23/docs/api/java.base/javax/security/auth/Subject.html),
[Migrating Subject.getSubject/doAs → current/callAs](https://docs.oracle.com/en/java/javase/23/security/migrating-deprecated-removal-methods-subject-getsubject-and-subject-doas-subject-current-and-s.html),
[JEP 486](https://openjdk.org/jeps/486),
[Security Manager Is Permanently Disabled (JDK 25 docs)](https://docs.oracle.com/en/java/javase/25/security/security-manager-is-permanently-disabled.html),
[JEP 403](https://openjdk.org/jeps/403).

### Second instance: SQLPermission, admitted outright rather than inferred

Verified 2026-07-10. Stronger than the `Subject` case because it isn't
an inference from conditional javadoc wording — it's a direct
first-party admission in the class's own deprecation notice.
`java.sql.SQLPermission` is `@Deprecated(since="26", forRemoval=true)`;
the javadoc states: *"There is no replacement for this class. This
class was only useful in conjunction with the SecurityManager, which
is no longer supported."* It guarded `DriverManager.deregisterDriver`,
`DriverManager.setLogWriter`/`setLogStream`,
`Connection.setNetworkTimeout`, `Connection.abort`, and
`SyncFactory.setJNDIContext`/`setLogger` — deregistering a driver out
from under other code, redirecting or suppressing the JDBC log stream,
forcibly aborting a connection you don't own, and JNDI context control
for RowSet sync are all now unguarded, by the platform's own account,
with nothing filling the gap. A second concrete data point for the
same pattern as `Subject`, and a cleaner citation since no inference is
required.

Sources: [SQLPermission javadoc (JDK 26)](https://docs.oracle.com/en/java/javase/26/docs/api/java.sql/java/sql/SQLPermission.html).

## AI agent authority: the same failure mode, a new deputy

Verified 2026-07-10. Gives the article a three-point historical arc,
not just a Java retrospective: classic SM's viral `doPrivileged` →
Capital One's SSRF-to-cloud-metadata breach (2019) → AI agents
executing an attacker's instructions with the operator's full tool
authority via prompt injection (2026). Same failure shape each time —
a deputy holding ambient authority gets tricked into exercising it on
behalf of someone who never should have had it. The deputy changes (a
stack frame, a WAF process, an LLM); the failure mode doesn't.

"Confused deputy" is the industry's own current term for the top
emerging AI-agent threat pattern, independently converged on by CSA,
SANS, and BeyondTrust in 2026 writeups — not an analogy reached for
here. Prompt injection is the mechanism, confused deputy is the
consequence: an agent with broad tool authority processes an
attacker-controlled input (email, doc, tool output) and exercises the
operator's authority on the attacker's behalf.

The industry's converging answer — MCP's own authorization spec
(Anthropic, Microsoft, Okta/Auth0), OAuth 2.1, Resource Indicators
(RFC 8707) for audience binding, Token Exchange (RFC 8693) for
delegation, tokens carrying both agent and user identity (`sub` +
`act.sub` claims) — amounts to: least privilege, policy checks on
sensitive actions, step-up approval, and scoped/short-lived/revocable
authority rather than a session-start check that then holds
indefinitely.

Strip the REST branding and that's a leased permission grant with a
dead-man switch — i.e. [[jgdms-leased-permission-grant]], already
built. The `OneShot` escalation (implies-once, never cached or
recorded) is ahead of what the current OAuth-for-agents literature
describes — nothing found does genuinely single-use, unlogged
escalation. And this framework answers a harder question than MCP
auth does: which code gets to run at all (digest identity), not just
which scoped token an already-running, already-vetted agent binary
holds. MCP auth doesn't touch code identity.

**Caveat, same shape as the kernel-isolation caveat earlier — don't
overclaim:** this doesn't prove authorization belongs inside a
language runtime specifically. MCP's answer lives in OAuth middleware,
not a JVM. What it proves is narrower and still strong: the axis
itself — identity-scoped, time-bounded, revocable admission control,
distinct from containment — isn't a Java-specific relic being defended
out of nostalgia. It's what a large, currently very well-funded part
of the industry is racing to rebuild from scratch, under active
attack, right now.

Sources: [AI Agent Confused Deputy Problem 2026 (safeguard.sh)](https://safeguard.sh/resources/blog/ai-agent-tool-confused-deputy-problem-2026),
[Confused Deputy Attacks on Autonomous AI Agents (CSA)](https://labs.cloudsecurityalliance.org/research/csa-research-note-ai-agent-confused-deputy-prompt-injection/),
[Your AI Agent Is an Easily Confused Deputy (SANS)](https://www.sans.org/blog/your-ai-agent-easily-confused-deputy-why-cloud-security-needs-credential-broker),
[Agent Authentication & Delegated Access (Zylos Research, 2026)](https://zylos.ai/research/2026-04-11-agent-authentication-delegated-access-oauth-scoped-tokens),
[MCP authorization and AI agent access control (nhimg.org)](https://nhimg.org/community/agentic-ai-and-nhis/mcp-authorization-and-ai-agent-access-control-what-changes/).

## What OpenJDK actually has, vs. what's already built here

Verified 2026-07-10. Three comparison points for "what do they have that
we don't" — in each case OpenJDK's own sources either admit the gap
outright or show the legacy mechanism is still load-bearing today.

**Secure remote invocation:** nothing beyond legacy RMI. No active
successor project exists in OpenJDK itself. Telling: searching for "the
modern secure RPC successor to RMI" surfaces a description of
JGDMS/JERI itself (via the ["Security Baked Into the JVM" collaboration
piece](https://blog.frankel.ch/security-baked-into-jvm/1/)) — pluggable
transport, per-method security requirements declared in deployment
config rather than scattered in app code, hardened deserialization via
`AtomicInputValidation`, `LoadClassPermission`-gated code loading before
anything runs, cryptographic trust before any code executes. There is
no second, competing answer to point to from the OpenJDK side.

**Deterministic/atomic wire format:** explicitly out of scope, in
writing. OpenJDK's own Amber design note, ["Towards Better
Serialization"](https://github.com/openjdk/amber-docs/blob/master/site/design-notes/towards-better-serialization.md),
states: *"Much ink has been spilled over the choice of
bytestream-encoding format, but in reality this is the least of our
concerns."* It also states it's *"not a goal of this effort to address
resource-consumption attacks,"* and leaves gadget chains and
class-identity verification as *"implementation concerns for frameworks
building atop this foundation."* A genuinely quotable admission that
the exact problem DER-based deterministic identity solves isn't one
OpenJDK is solving.

**Cross-hierarchy invariant validation:** same design note, same
pattern. It states plainly: *"every class must carry its own
serialization behavior, rather than inheriting it from a supertype that
implements Serializable"* — no concrete mechanism is given for
coordinating validation across a multi-level class hierarchy; each
class author is left to correctly delegate to superclass construction
on their own. Contrast with GetArg's actual mechanism: each class in
the hierarchy gets its own private namespace, and the superclass
instance is constructed and validated *before* the subclass constructor
proceeds, so a subclass can rely on a genuinely-validated superclass
rather than hoping every class author remembered to coordinate
correctly. State this as "we have a concrete mechanism," not "solved" —
hierarchy coordination is exactly the kind of thing that stays subtle
in practice.

**RMI isn't legacy cruft nobody touches — it's live infrastructure
today.** `javax.management.remote.rmi` remains the standard,
out-of-the-box connector for remote JMX management; VisualVM's remote
connections go through it via JRMP, configured with the same
`com.sun.management.jmxremote.rmi.port` properties that have existed
for decades. Oracle's own JDK 25 JMX connector docs, updated as
recently as April 2026, still describe it as the default with no
replacement connector in the platform. Undercuts any "RMI doesn't
matter, nobody uses it" framing — it's the thing still running under
JMX/VisualVM right now, carrying every weakness already covered in this
document (RMI registry/JRMP deserialization surface, no built-in mutual
auth, no code identity model).

**Open design question worth pursuing separately:** the JMX Remote API
(JSR 160) was explicitly built pluggable from the start —
`JMXConnectorProvider`/`JMXConnectorServerProvider`, discoverable via
`ServiceLoader` or the `jmx.remote.protocol.provider.pkgs` property. RMI
is only the *mandatory* default; an optional JMXMP connector already
proved the model (JSSE/JAAS/SASL instead of RMI's security model,
TCP transport + native Java serialization for object wrapping), though
it was never bundled in the JDK proper — it shipped as a separate
optional jar (`jmxremote_optional.jar`) that had to be manually added,
and never displaced RMI as the practical default despite being more
secure. That's a cautionary precedent, not just an encouraging one: a
better-designed connector existing isn't sufficient for adoption if it
isn't the path of least resistance. A `service:jmx:jeri://` connector
built on JERI (transport) + Atomic DER (object wrapping) is
architecturally straightforward to build — the generic connector's
transport/object-wrapping split maps directly onto JERI's own
extensible transport layer plus the DER marshalling stack — but the
JMXMP precedent says the hard part isn't the engineering, it's making
it the easy/obvious choice (e.g., a genuinely trivial drop-in
experience with VisualVM/JConsole, not just an available alternative).

Sources: [JMXConnectorFactory (JDK 23) javadoc](https://download.java.net/java/early_access/valhalla/docs/api/java.management/javax/management/remote/JMXConnectorFactory.html),
[Using JMX Connectors to Manage Resources Remotely (JDK 21 docs)](https://docs.oracle.com/en/java/javase/21/jmx/using-jmx-connectors-manage-resources-remotely.html),
[JMX Remote API Specification (JSR 160), Appendix A](https://docs.oracle.com/javase/7/docs/technotes/guides/jmx/overview/appendixA.html),
[JMX Connectors (JDK 25 docs)](https://docs.oracle.com/en/java/javase/25/jmx/jmx-connectors.html).

## Corrections / caveats for future editing

- CVE-2024-47197 is **not** related to JGDMS or `PermissionSerializer`
  — it's an unrelated Maven Archetype Plugin sensitive-information CVE.
  The `PermissionSerializer` deserialization-gadget removal has no
  public CVE of its own; cite the mechanism (deserializing a
  `Permission` object let an attacker manufacture unearned grants), not
  a CVE number.
- MINA CVE-2026-41635 / CVE-2026-42779 and Oracle CVE-2026-21925 are
  patched upstream as of writing (2026-07), but recent enough that
  patch adoption lag likely leaves them open in most real deployments
  — frame accordingly, don't call them "unpatched."
- The RMI-registry-client gap is the one item here that is genuinely
  open by design (no fix exists in JEP 290 itself for that direction),
  not just patch-lag.
