# SOW — Federated Malicious-Payload Digest Registry

**Status:** design / scoping, captured 2026-07-10. Not yet implemented. JGDMS-side,
committable as a design doc. No code in this SOW. This is a genuinely open design
problem — several of the sections below state options and tradeoffs rather than a
single recommendation, and say so explicitly rather than picking a winner to look
decisive.

## 0. Thesis

JGDMS's Atomic DER marshalling stack (`jgdms-der`, JGDMS-STD-006) produces
**deterministic, canonical wire encodings**: the DER TLV rules leave no room for the
padding/field-order/alternate-encoding freedom that plagues BER or hand-rolled
formats, so the same logical object always serializes to the same bytes. This
determinism is not a new claim invented for this SOW — it is the property the
codebase already leans on twice:

- **Code identity**: `DigestGrant`/`DigestCodeSource` (§1) grants permissions to a
  `ProtectionDomain` keyed on a SHA-256 digest of the code bytes, not the URL.
- **Schema identity**: `AtomicSerialSchemaRecord.schemaDigest()` (§1) is a SHA-256
  digest of a class's DER-encoded *shape* (field names/types/order), used to detect
  schema drift across a wire-compatible chain.

**Proposed extension: a third use of the same determinism, this time for payload
*content* rather than code or schema** — if a malicious payload ever gets past every
existing admission control (§1: `DeSerializationPermission`, collections-by-contract
reconstruction, constructor-gated `check(GetArg)` validation), a SHA-256 digest of its
canonical DER encoding is a stable, content-addressed identifier for *that exact
attack*. A deployment that discovers a novel malicious payload can record its digest;
other deployments can check incoming payloads against that digest before or alongside
the existing checks, so an attack discovered once becomes blocked everywhere the
registry is shared — without a human hand-authoring a signature.

This is explicitly a **defense-in-depth, reactive** layer. It does not replace or
weaken deny-by-default admission control — it exists for the "something novel got
through anyway" case, and its coverage is honestly narrow (§5): it catches
byte-identical recurrences of a payload already seen once, nothing more.

**Correction to a premise going in:** there is **no existing SHA-256 content digest
of payload *values*** anywhere in the codebase today to build on directly. The two
existing digest mechanisms are adjacent, not identical:
`DigestGrant`/`DigestCodeSource` digests *code bytes* (a jar), and `schemaDigest`
digests a class's *schema* (field shape), not the field *values* of a particular
instance. The closest existing thing to a payload-content hash,
`MarshalledInstance.computeHash(byte[])` (`jgdms-platform/.../net/jini/io/
MarshalledInstance.java:207-213`), is a plain Java `31*h+b` rolling hash over the
payload bytes — kept deliberately weak-but-compatible because its only job is
matching `java.rmi.MarshalledObject`'s `hashCode()` contract for VM-comparable
`equals()`, not resisting a deliberate collision search. **A cryptographic
(SHA-256) digest of payload bytes, computed for this specific purpose, does not
exist yet and would need to be built** — the design is sound (DER's determinism
supports it), but "already used elsewhere for data" overstates what is actually
there today; it is proven for *code* and *schema*, not yet for *values*.

## 1. What already exists (grounds this SOW)

Read for yourself: `jgdms-der/src/main/java/au/net/zeus/jgdms/der/object/
ObjectCodec.java`, `jgdms-platform/src/main/java/org/apache/river/api/io/
DeSerializationPermission.java`, `jgdms-platform/src/main/java/org/apache/river/api/
security/DigestGrant.java`, `jgdms-platform/src/main/java/org/apache/river/api/
security/LeasedPermissionGrant.java`, `docs/JGDMS-STD-002-Safe-Codebase-Audit-
Pipeline-Standard-v1.3.md`.

- **`DeSerializationPermission`** (`ATOMIC`/`EXTERNALIZABLE`/`MARSHALLED`/`PROXY`) —
  a `BasicPermission` checked against the *class's* protection domain (not the
  caller's) before a class hierarchy is allowed to participate in decode at all.
  This is allowlist admission control at class granularity, independent of the
  specific field values in the stream.
- **`ObjectCodec.decode(Class, AtomicSerialSchemaRecord, byte[])`**
  (`ObjectCodec.java:298-353`) — the DER decode entry point for one class's private
  SEQUENCE. Numbered steps in the method body itself:
  1. `DerFieldStore store = new DerFieldStore(schema, payloadSequence)` — parses the
     raw bytes into typed fields (line 307).
  2. Assembles the `DerGetArg` (lines 309-312).
  3. **`checkAtomicDeSerializationPermitted(map.keySet())`** — the per-class
     `DeSerializationPermission("ATOMIC")` gate (line 315) — *then* the
     `(GetArg)` constructor runs (lines 316-326).
  - `checkAtomicDeSerializationPermitted` (lines 198-233) builds an
    `AccessControlContext` from the protection domains of the classes about to be
    constructed and calls `sm.checkPermission(ATOMIC, ctx)` — no-op when no
    `SecurityManager` is installed (line 220).
- **`ObjectCodec.decodeNested`** (lines 1601-1658) and the private
  `decodeHierarchy(...)` overload (lines 1733+) show the same shape recursively:
  raw bytes (`nestedRecordBytes` / `hierarchyPayload`) arrive, get parsed into a
  schema chain + payload split, *then* get gated and constructed.
- **`check(GetArg)`** — every `@AtomicSerial` class's own invariant-enforcement
  constructor gate (referenced throughout `ObjectCodec.java`'s comments as the
  "parameter objects have already been checked" line in `DeSerializationPermission`'s
  own javadoc, line 33-35 of that file) — this is the "collections-by-contract /
  immutable-parameter reconstruction" defense the prompt references: fields are
  rebuilt through the class's own validating constructor, not handed to it as
  attacker-controlled mutable structures.
- **`DigestGrant`** (`jgdms-platform/.../org/apache/river/api/security/
  DigestGrant.java`) — matches a `ProtectionDomain` only when its `CodeSource` is a
  DirtyChai `java.security.DigestCodeSource` (accessed by reflection, lines 61-75,
  so this class still compiles/links on a vanilla JDK and fails closed —
  `implies` returns `false` — when `DigestCodeSource` is absent, lines 122-129,
  142-155) with matching algorithm and digest bytes. **Allowlist, keyed by
  content digest, already shipped.** The registry proposed here is the same key
  shape (SHA-256 digest → identity), inverted in polarity (denylist) and moved
  from code identity to payload-value identity.
- **`LeasedPermissionGrant`** (`jgdms-platform/.../org/apache/river/api/security/
  LeasedPermissionGrant.java`) — a decorator adding a `net.jini.core.lease.Lease`-
  driven dead-man switch to any `PermissionGrant`: void on `cancel()`, void when
  `clock.millis() >= lease.getExpiration()` (local-clock, fail-secure `>=`), void
  when the wrapped grant is already void (composes, doesn't replace). Renewal is
  entirely the caller's responsibility; the decorator caches nothing and re-reads
  `getExpiration()` on every check (see `docs/DESIGN-LeasedPermissionGrant.md` for
  the as-built notes, especially §1.2 on clock skew being a landlord
  responsibility and §1.11(b) on rejecting `Lease.FOREVER`). This is the directly
  relevant time-bounded/revocable-delegation precedent the prompt asked about —
  it already solves "how does JGDMS represent a grant that expires and can be
  cancelled by its own holder, with GC and lease as two independent dead-man
  switches" for *permission* grants; §3 below asks whether the same shape fits
  *registry entries*.
- **`JGDMS-STD-002` (SCAP), the Verdict Registry (Host 3)** — the closest thing in
  this codebase to "a shared registry that gates untrusted content by SHA-256
  content hash," but for **code** (JARs), not payload values, and built on a
  fundamentally different trust shape than what §2 needs (see §2.4 for why the
  difference matters, not just the similarity).

## 2. Where this hooks into the existing pipeline

### 2.1 The hook point: before per-class permission checks, on raw bytes

The natural insertion point is **inside `ObjectCodec.decode`/`decodeHierarchy`/
`decodeNested`, before step 3** (the `DeSerializationPermission("ATOMIC")` check) —
i.e. right after the raw bytes are in hand (`payloadSequence` / `hierarchyPayload` /
`nestedRecordBytes`) but before any of:

- class resolution beyond what's needed to know which schema chain applies,
- the per-class `DeSerializationPermission` check,
- the `(GetArg)` constructor running.

**Why before, not after or in parallel:**

- **Before the permission check**, a digest hit can short-circuit the *entire*
  per-class permission evaluation and constructor invocation — the whole point is
  to avoid re-doing (or re-risking) work on a payload already known to be bad. A
  digest hit is cheaper than an `AccessControlContext` build + `checkPermission`
  call, and vastly cheaper than running an attacker-controlled `check(GetArg)`
  constructor even once.
- **Before construction, categorically** — the entire premise of "this exact
  payload got past every other defense" (§0) implies the failure, if there is one,
  happens *inside* construction or immediately after (a `check(GetArg)` that fails
  to catch a crafted-but-technically-valid combination of fields, or a
  constructor whose side effects are the attack itself, e.g. resource
  exhaustion during a nested collection walk). Gating on the digest of the raw
  bytes, computed before the constructor runs, means the registry check happens
  even for a payload whose *construction itself* is the exploit — there is no
  version of this defense that can safely run "after" construction for that
  class of attack.
- **Not purely in parallel with the permission check** — a parallel check still
  has to complete (or the caller has to wait for it) before construction is safe
  to proceed, which is operationally identical to "before" from the constructor's
  point of view; the two checks are logically independent (one gates on class
  identity + protection domain, the other gates on payload content) and can be
  evaluated in either order or concurrently without correctness issues, but
  **construction must not start until both have cleared**. Stated as "before" in
  this SOW for concreteness, but the real requirement is "gates construction,"
  not a specific check ordering relative to the `ATOMIC` permission gate.

### 2.2 What is being digested: whole-graph or one class's SEQUENCE?

This is not fully settled and interacts with §4 (scope/boundaries). Two candidate
digest granularities, both computable from what `ObjectCodec` already has in hand
at the hook point:

- **Per-class SEQUENCE digest** — SHA-256 of `payloadSequence` inside `decode()`
  (line 298). Cheap, available at the innermost hook point, and — per §4 — this is
  the granularity that could catch a *dangerous sub-component* reused inside a
  structurally different outer envelope, if the sub-component's own encoding is
  byte-identical across attacks.
- **Whole-object-graph digest** — SHA-256 of the complete top-level encoding
  (the outer SEQUENCE `decodeHierarchy` is ultimately called for, or the
  equivalent top-level bytes at the `AtomicMarshalInputStream`/JOSS layer above
  `ObjectCodec`). Only available at the outermost entry point, not inside the
  per-class/per-nested-field recursion.
- **Practical recommendation, not a final decision**: check at *both*
  granularities where cheap to do so — at minimum the whole-graph digest at the
  top-level decode entry (catches an exact-replay of a known attack in one
  lookup, no recursion needed) and, for nested `@AtomicSerial` fields
  specifically (`decodeNested`, `ObjectCodec.java:1601`), the per-record digest
  of `nestedRecordBytes` — because that is exactly the "same dangerous gadget,
  different envelope" case §4 asks about. Whether doing both is worth the
  double lookup cost per decode is an open question for the build phase (§8),
  not resolved here.

### 2.3 Fail-open vs fail-closed on registry unavailability

The registry is by design a network-reachable service (or a locally cached
snapshot of one, per §3). If it is unreachable at decode time, does decode
proceed (fail-open) or refuse (fail-closed)?

**Recommendation, stated as a recommendation not a settled decision:**
fail-open against registry *unavailability*, fail-closed against every other
existing check. Rationale: this layer is explicitly reactive defense-in-depth
(§0) *on top of* deny-by-default admission control that already runs
unconditionally — `DeSerializationPermission`, class-identity checks, and
`check(GetArg)` do not depend on network reachability and must not be weakened
by this addition. A registry outage should degrade the deployment back to
"exactly as safe as it was before this SOW," not stall decode of every incoming
object waiting on a remote lookup, and not (worse) block on a network call
inside a decode path that other JGDMS design work (`no-ThreadLocal`,
virtual-thread carrier-pinning concerns referenced in STD-002's own motivation
section) is already careful about. A deployment with a stricter risk posture MAY
choose fail-closed-on-unavailable as a local policy knob; this SOW does not
mandate one default over the other, but leans fail-open-on-unavailable /
fail-closed-on-everything-else as the sane baseline.

### 2.4 Why this is not "just SCAP for data" — the trust shape is different

STD-002's Verdict Registry (§1) already solves a structurally similar-looking
problem for code: a SHA-256-keyed registry of verdicts, gating consumption. It is
tempting to reuse its shape wholesale. Two properties of SCAP's trust model do
**not** carry over, and the difference is exactly why §3 (the registry's own
threat model) is the hard part of this SOW rather than a rehash of STD-002:

1. **SCAP's reporters are a closed, operator-vetted set.**
   `registerAnalysisEngine(engineId, engineKey, sigAlgorithm)` is an **operator**
   action (STD-002 §"Host 3" API table) — the set of keys the Verdict Registry
   trusts is curated in advance, by the same administrative authority that runs
   the registry. SCAP's "single `DANGEROUS` verdict from any engine immediately
   condemns" policy (STD-002 Motivation §2) is safe *because* "any engine" means
   "any of a small, pre-vetted, sandboxed set" — not "any deployment on the
   internet." The malicious-digest registry this SOW scopes is explicitly meant
   to let **arbitrary participant deployments** submit reports (§0: "lets a
   deployment which discovers a novel malicious payload record its digest"). That
   is a fundamentally more open, more adversarial reporter population than
   SCAP's, and SCAP's single-report-condemns policy would be a live poisoning
   vector if copied directly (§3.1).
2. **SCAP's analysis engines are architecturally low-value targets; a malicious-
   digest reporter is not.** BAE instances (Host 2) have no outbound network
   access, never fetch JARs themselves, and have no direct connection to Host 3
   (STD-002 §"Host 2", "Explicitly forbidden connections") — compromising one
   gains an attacker very little. A deployment able to submit to the proposed
   registry, by contrast, is *reporting on its own local observations* — there is
   no equivalent isolation to fall back on; the report itself, and only the
   report, is the input.

The one SCAP mechanism that *is* a close, worth-reusing precedent is
`CrashReport` (§1): "a Phoenix-signed record of an abnormal JVM exit," trusted
as an implicit `DANGEROUS` vote *specifically because* it is a tamper-evident
artifact of the JVM itself crashing, signed by infrastructure the deployment
controls (Phoenix), not a bare assertion by a human or a remote party. §3.2
returns to this as a model for what a well-formed submission to the malicious-
digest registry should look like.

## 3. Threat model of the registry itself (the hard part)

Think adversarially about the registry as its own attack surface, not just about
what it defends against.

### 3.1 The core problem: a false report is a denial-of-service primitive

If any deployment's unilateral claim "digest D is malicious" is trusted and
propagated, an attacker (or a deployment with a broken local detector) can submit
the digest of a **legitimate, benign** payload and have it globally blocklisted —
a denial-of-service against every honest deployment that trusts the registry,
requiring no compromise of any target, only a network-reachable submission
endpoint. This is strictly easier than attacking the payload-admission pipeline
itself (§0/§1) — the attacker doesn't need to construct a working exploit, only a
plausible-looking false claim.

Two distinct causes produce the identical poisoning outcome and need to be told
apart in the design, because the appropriate mitigation differs:

- **Malicious submission** — an attacker deliberately reports a benign digest as
  malicious, either to deny service to a specific known-legitimate payload (if
  the attacker can predict or observe it) or opportunistically to erode trust in
  the registry generally.
- **Honest false positive** — a deployment's own local detection is wrong (a
  buggy heuristic, a misconfigured policy, a benign payload that happens to
  trigger some local alarm) and it reports in good faith. The registry cannot
  distinguish this from malicious submission by content alone — a false report
  looks the same regardless of intent.

**This SOW does not resolve which mitigation combination to build — it lays out
the candidates and their costs, for the build phase to decide with (§8).**

### 3.2 Candidate mitigations, none free

- **(a) Unilateral single-reporter trust (no mitigation).** Simplest, fastest to
  propagate, and — per §3.1 — trivially poisonable. Only defensible if the
  *reporter set* is closed and vetted (an operator-curated federation of mutually
  trusting deployments, analogous to SCAP's engine-key registration), not an open
  one. Not recommended as the general-purpose default; may be acceptable as an
  explicit deployment-local policy for a small, high-trust federation (e.g. an
  organization's own fleet).
- **(b) Rank-weighted corroboration / quorum.** Reporters are not trusted
  equally. Each reporter carries a **rank/weight**, and the registry tracks,
  per digest, both a **raw count of distinct corroborating reporters** and a
  **rank-weighted sum** of their reports (not N reports from one reporter
  re-submitting — a report is keyed by `(digest, reporter identity)`, and a
  reporter re-submitting updates its own entry rather than adding a second
  vote). This generalizes SCAP's engine-key registration (STD-002 §"Host 3":
  `registerAnalysisEngine(engineId, engineKey, sigAlgorithm)`) from a *binary*
  trusted/not-trusted set into a *graded* weight — the same operator-curation
  precedent, one axis richer. **What the registry does *not* do is decide, on
  the federation's behalf, how much weighted signal is enough to call a digest
  `MALICIOUS`** — that decision is resolved in §3.2.1 as a per-consumer local
  policy, not a registry-wide threshold. Costs and open sub-questions specific
  to this mitigation are worked through in §3.2.1, not restated here.
- **(c) Attestation of reproduction, not bare assertion.** Modelled directly on
  SCAP's `CrashReport` (§2.4): require a submission to carry evidence that the
  reporting deployment actually attempted to admit the payload through its own
  real decode pipeline and observed a concrete, specific failure mode (an
  exception type/class from a real `check(GetArg)` rejection, a resource-limit
  trip, a crash) — not merely a human's or an automated tool's *opinion* that a
  digest is bad. This raises the bar from "assert anything" to "prove you ran it
  and it did something identifiably bad," but is not airtight: a submission can
  still attest to a real-but-overcautious local rejection (an honest false
  positive per §3.1) that isn't actually a widely-dangerous payload, and the
  "specific failure mode" evidence needs its own schema and validation logic
  that is itself new attack surface (a crafted "attestation" designed to look
  like a real crash report). Best combined with (b), not as a substitute for it.
- **(d) Reporter identity cost (Sybil resistance).** If reporter identity is
  cheap to mint (an anonymous or self-issued key), (b)'s "independent reporters"
  requirement is defeated by one attacker minting many identities. JGDMS already
  has an identity primitive with real minting cost and revocability — SPIFFE/
  SPIRE-issued SVIDs (per the SPIFFE work referenced throughout this repo's other
  design docs) — which could be required as the reporter credential, moving the
  Sybil-resistance question to "how hard is it to mint many SPIFFE identities,"
  a question this SOW does not answer (it depends entirely on the SPIRE trust
  domain's own issuance policy, which is a deployment/federation operational
  decision, not something this registry design controls).
- **(e) Revocability of a bad entry (the honest-false-positive escape hatch).**
  Whatever the trust/quorum policy, a wrongly-added entry needs a way to be
  removed — both for the honest-false-positive case (§3.1) and as a bound on the
  blast radius of any poisoning that does get through. §3.3 covers this via the
  same lease/expiry shape as `LeasedPermissionGrant` (§1).

**No combination of (a)-(e) makes the registry's own trust problem go away —
they trade speed of propagation against resistance to poisoning, and every
option on that tradeoff curve is a real, stated cost, not a solved problem.**
§3.2.1 resolves the *sharpest* instance of that tradeoff — who decides "how
much corroboration is enough" — by refusing to make the federation agree on a
single answer at all.

### 3.2.1 Resolution: ranked reporters supply signal, each consumer sets its own acceptance threshold

**This is Peter's resolution to what was, in an earlier pass of this SOW, its
sharpest open question.** The registry does **not** decide, federation-wide,
when a digest is "proven malicious." It supplies a **raw, ranked signal**
— per digest, the set of distinct corroborating reporters, each reporter's
rank, and the resulting rank-weighted sum — and **each consuming deployment
locally configures its own acceptance threshold** against that signal before
treating a digest as condemned. A conservative operator sets a high bar
(auto-block only above some high weighted-quorum percentage, or require
manual review below it); an aggressive operator sets a low one. Same shared
data, different local risk tolerance, **no federation-wide consensus on "what
counts as proven malicious" is required** — which sidesteps what is otherwise
a plausibly unsolvable governance problem for an open, cross-organizational
registry (getting every participating organization to agree on one correct
global threshold).

This reframes §3.3 below and substantially resolves the "who can revoke a
quorum-approved entry" question that an earlier pass of this SOW left open:
**there is no single quorum-approved entry to revoke.** There is only a live,
per-digest weighted signal, recomputed from whichever individual reports are
currently live (not expired, not cancelled — §3.3). Revocation is scoped to a
single report, symmetric with `LeasedPermissionGrant.cancel()`'s single-owner-
cancels shape (§1): the reporter who submitted a report can retract it, which
simply removes that report's contribution to the weighted sum going forward.
No federation-wide arbitration mechanism is needed for the common case. (An
operator-override case — forcibly retracting *another* reporter's report,
e.g. because that reporter's identity was later found to be compromised — is
a residual case this SOW does not resolve; see the open-question list at the
end of this subsection.)

Four design questions Peter's resolution raises, worked through honestly
rather than restated as slogans:

**1. How is rank established and updated, without becoming a new manipulation
surface?** Recommended as a **hybrid, not a pure choice of one**:

- An **operator-curated floor** is the bootstrap and the safe default — the
  direct generalization of SCAP's `registerAnalysisEngine` (§3.2(b)) from a
  binary trust bit to a graded weight, assigned by the same administrative act
  (an operator vouching for a reporter identity, e.g. a SPIFFE SVID from a
  known trust domain — see (d) below) that SCAP already relies on. This alone
  is sufficient to run the registry (P1/P2 in §8) without solving dynamic
  adjustment at all.
- **Track-record adjustment** (a reporter whose reports are later confirmed
  accurate gains rank; one whose reports turn out to be honest false positives
  or malicious loses it) is the more powerful, more dangerous extension, and
  **this SOW is honest that it has a real, unresolved recursion problem, not
  a solved one**: confirming that a past report was *wrong* requires a signal
  itself, and if that confirming signal is just another deployment's unilateral
  "I investigated and unblocked it," that report needs its own trust weighting
  — recursively, with no obviously well-founded base case. This is a genuine
  open problem, not papered over here. Two partial mitigations, neither a full
  answer: (i) anchor track-record adjustment only to *high-confidence* ground
  truth that doesn't itself need weighting — e.g. a CVE being published/patched
  for the underlying gadget, or an operator's own manual confirmation — rather
  than to other deployments' unilateral unblock reports, which avoids the
  recursion by refusing to close the loop automatically; (ii) treat dynamic
  rank adjustment as a **later-phase refinement** (§8, not P1/P2) built and
  evaluated once the operator-curated-floor-only version has real operational
  experience behind it, rather than something the initial design needs to
  solve up front.
- **This SOW's recommendation**: operator-curated floor as the load-bearing
  mechanism from day one; track-record adjustment explicitly deferred, flagged
  as unresolved rather than designed prematurely.

**2. Raw distinct-reporter count, rank-weighted sum, or both?** **Both,
tracked separately, with the raw count enforced as a hard floor, not just an
input to the weighted sum.** Rationale: a purely weighted-sum model lets a
*single* very-high-rank reporter cross a threshold alone if that reporter's
rank is high enough — which reintroduces exactly the "one party's unilateral
claim gets trusted" problem §3.1 opened with, just gated by rank instead of by
nothing. **Recommended invariant, enforced structurally by the registry, not
left as consumer-side policy**: no single reporter, regardless of rank, can
by itself make a digest cross any consumer's configured threshold — a minimum
raw distinct-reporter count (small, e.g. 2 or 3, exact number an open
build-phase decision) is required *in addition to* whatever weighted-sum bar
a consumer sets, so a compromised or malicious high-rank reporter is bounded
to "advisory," never "unilaterally condemns," no matter how a consumer
configures its threshold. This is the direct fix for the failure mode
Peter's framing implicitly guards against by introducing rank at all: rank
should raise confidence *among corroborating reports*, not create a
back door to single-reporter unilateral trust at high rank.

**3. Does a per-consumer configurable threshold reopen poisoning in a new
shape — targeting one victim's known-low threshold instead of the whole
federation?** **Yes, this is a real residual risk, only partially mitigated,
not eliminated.** An attacker who cannot get a digest condemned federation-
wide could instead target a specific deployment known (or guessed) to run a
low threshold, submitting just enough weighted signal to clear *that* bar
specifically. Assessment, not a dismissal:
- The point-2 raw-count floor bounds this: the attacker still needs to control
  or compromise *multiple distinct* reporter identities (not just one
  high-rank one) to clear even a low-threshold victim's bar, which reopens the
  Sybil-cost question (§3.2(d)) as the actual line of defense here, not the
  threshold policy itself.
- The narrowness of digest-exact matching (§4) bounds the blast radius even of
  a successful targeted false condemnation to *one specific payload identity*
  being wrongly blocked for *one specific victim* — a real, but contained,
  harm, not a general DoS against arbitrary legitimate traffic.
- Whether a victim's configured threshold value is itself observable by an
  attacker (letting them calibrate a targeted attack precisely) is a real
  question this SOW flags but does not resolve — it depends on whether the
  registry protocol exposes a consumer's local threshold to the registry or
  other participants at all (a purely local, never-transmitted configuration
  value is not observable this way; a threshold reported back to the registry
  for any reason — e.g. federation-wide analytics — would be).
- **Net assessment**: a deployment choosing a low threshold is making the same
  kind of local risk-tolerance tradeoff as a deployment choosing to trust an
  unvetted single reporter (§3.2(a)) — the registry's job is to make that
  tradeoff explicit and locally-owned, not to eliminate the consequence of
  choosing it. Acceptable as a stated, bounded residual risk; not fully solved.

**4. Interaction with the lease/expiry shape (§3.3): does the threshold
re-evaluate continuously, and can a digest cross back below it?** **Yes to
both, and this reframes §3.3's unit of expiry from "the entry" to "each
individual report."** The weighted sum for a digest is a **live, derived
quantity**, recomputed from whichever individual reports are currently
un-expired and un-cancelled at the moment a consumer checks (§2's decode-path
hook queries the current state, not a cached verdict decided once). Concretely:
- **Going-forward only, never retroactive.** If new corroboration arrives and
  a digest crosses a consumer's threshold, that consumer's *future* decodes
  are checked against the now-higher signal; decodes already completed before
  the crossing are not (cannot be) retroactively unwound — the same
  going-forward-only posture `LeasedPermissionGrant` already takes toward
  renewal (§1: a late renewal does not retroactively grant authority for
  actions checked during the void window).
- **Symmetrically reversible, in principle** — but *how* reversibility should
  actually behave (passive decay vs. requiring a positive act) is genuinely
  contested, not a settled "deliberate, desired property" as an earlier pass
  of this SOW stated it. §3.2.1 point 5 below treats this properly: passive
  weight decay (a report's lease lapsing) and an active exonerating signal are
  **not interchangeable**, and conflating them is exactly the fail-open risk
  point 5 works through.

**5. Peter's report-leasing resolution: does a passively-decayed report
auto-lift a block, and is that safe?** Building on (not replacing) points 1-4:
**individual reports are themselves leased**, reusing `LeasedPermissionGrant`'s
dead-man-switch shape (§1, §3.3) not just for "a registry entry" as a whole
but for *each contributing report* — a report that isn't renewed lapses on
its own, no active retraction required. This gives a passive, low-friction
answer to "how does a digest's weighted sum come back down over time": it
just does, automatically, as unrenewed reports expire. That much is a real,
useful simplification over requiring every stale or wrong report to be
actively walked back (§3.2.1's cancellation path above still exists for the
*fast* case — a reporter who positively knows they were wrong — but leasing
means correction no longer *depends* on someone taking that action).

**The tension this opens has to be treated honestly, not credited as pure
upside.** When a digest's weighted sum decays below a consumer's threshold
purely because reports expired — nobody retracted anything, nobody
disproved anything, the reporters simply went quiet — what should happen to
that consumer's block?

- **Auto-lift on decay** is self-correcting for false positives with zero
  operator effort, but it is a **real fail-open failure mode**: report
  expiry is caused indistinguishably by "this was never actually a threat"
  *and* by "the reporting organizations lost interest, had staff turnover,
  or let their own credentials/leases lapse for reasons that have nothing to
  do with whether the payload is still dangerous." Silence is evidence of
  neglect, not evidence of safety, and treating it as the latter directly
  contradicts the deny-on-uncertainty posture this SOW's own grounding leans
  on elsewhere — `DigestGrant` fails closed the moment `DigestCodeSource` is
  absent (§1), STD-002's client policy is "verdict absent → refuse" (§2.3),
  and §2.3's own fail-open carve-out for *this* mechanism was deliberately
  scoped narrowly to registry *unavailability*, not to "the registry says
  less than it used to."
- **No auto-lift** (decay only lowers a *displayed/tracked* confidence
  score; an actual block persists until something positive removes it) does
  not fail open, but reintroduces the exact friction Peter's leasing idea was
  meant to remove: a block that was always a false positive, whose original
  reporters have long since gone quiet, sits there forever unless a human
  proactively goes looking for it to review — stale-block accumulation as a
  standing maintenance burden, indefinitely.

**Where this SOW lands, reasoned rather than picked arbitrarily**: **decay is
passive and automatic, but by default it is *not*, on its own, sufficient to
lift a block.** Expiry of unrenewed reports continuously lowers the live
weighted sum (Peter's mechanism works exactly as described, and this is the
number a consumer's threshold is compared against going forward — point 4
still holds for the *live signal*). But whether that live signal alone drives
a consumer's actual admission decision, or whether dropping to/below
threshold merely demotes a digest to a **quarantine state** requiring a
distinct, explicit positive signal to fully lift, is treated here as itself a
consumer-local policy choice, in the same spirit as §3.2.1's core move
(pushing "how much is enough" to the consumer rather than the registry):
- **Default recommendation (fail-closed-leaning): decay alone does not
  auto-lift.** A digest whose live weight has decayed below threshold moves
  to quarantine — no longer actively *reinforced*, flagged as needing review,
  but still blocked by default — until either (i) at least one **exonerating
  report** (a reporter positively asserting "investigated, confirmed benign,"
  itself rank-weighted and subject to its own — likely *stricter*, not
  lower — corroboration bar; §3.4.2 now proposes a concrete shape for this
  asymmetry, at medium confidence on the structure and low confidence on
  the exact numbers — still a build-phase calibration question, not fully
  settled) crosses the
  consumer's own configured exoneration bar, or (ii) the consumer has locally
  opted into a more aggressive policy that treats decay alone as sufficient
  (available for a deployment that has decided low friction is worth the
  fail-open exposure — the same locally-owned risk tradeoff §3.2.1 point 3
  already accepts for threshold configuration generally).
- **This default has a real, accepted cost, stated plainly rather than
  hidden**: a false-positive block whose reporters go quiet *and* nobody ever
  submits a positive exoneration sits blocked indefinitely — the "(b)" cost
  the tension opens with is not eliminated by this recommendation, only
  bounded (a demoted/quarantined digest is at least visibly flagged as
  needing review, rather than either silently staying fully "confirmed
  malicious" or silently disappearing). Whether that residual staleness is
  acceptable is itself federation/deployment-dependent, and this SOW does not
  claim otherwise.
- **Lease duration**: recommended as **rank-scaled and polarity-asymmetric**,
  not a single fixed TTL — a higher-rank reporter's corroboration reasonably
  needs less frequent reconfirmation than a low-rank one, and (deliberately
  biasing the passive-decay default toward *retaining* a block rather than
  losing it) a **condemning** report's default lease should outlast an
  **exonerating** report's default lease at the same rank, so the system
  drifts toward quarantine/review rather than toward silent auto-clearance as
  time passes with no activity either way. Exact multipliers are a build-phase
  parameter, not fixed here.
- **Renewal semantics**: recommended as **tiered, not a single bare
  heartbeat**. A pure liveness heartbeat only proves the reporter's
  infrastructure is still running, not that the finding is still believed
  true — treating heartbeat-only renewal as equivalent to reconfirmation
  would quietly weaken the "renewal re-confirms" property this whole
  mechanism depends on. Recommended: a cheap heartbeat sufficient to prevent
  short-interval decay-by-mere-neglect, plus a less-frequent mandatory full
  re-attestation ("yes, still confirmed, here is why") required to keep a
  report's *substantive* weight rather than just its bare presence. Exact
  cadence is a build-phase parameter.

**What remains genuinely open after this resolution** (updating, not
duplicating, §7): the recursion problem in point 1 (dynamic rank adjustment's
feedback loop); the exact raw-count floor value in point 2; the targeted-
victim residual risk in point 3, specifically whether threshold values are
ever observable to an attacker; the operator-override-revocation case noted
above (forcibly retracting another party's report); and, from point 5, the
concrete lease-duration/renewal-cadence parameters. The quarantine-vs-block
distinction's operational shape and the relative quorum bar for exonerating
vs. condemning reports now have a proposed shape (§3.4.2) — a confidence-
band-tail test plus an asymmetric raw-reporter floor, and an "elevated-
scrutiny, visibly-flagged block" minimum operational meaning for
quarantine — at medium/medium-high confidence on structure but low
confidence on exact numeric parameters, so they move from *unaddressed* to
*proposed-but-not-yet-calibrated*, not to fully closed. None of these are resolved by
introducing rank + local threshold + leased reports — that combination
resolves the *governance* problem (no federation-wide consensus needed on
either "how much corroboration" or "when does decay actually clear a block")
but does not, by itself, resolve the remaining mechanism-level questions.

### 3.3 Reports should expire and be revocable, not permanent — reusing the lease shape

`LeasedPermissionGrant` (§1) already solves "a grant that is void on cancellation,
void on expiry (local-clock, fail-secure `>=`), and composes with other void
conditions" for *permission* grants. Per §3.2.1, the unit this shape applies to
is **each individual report** ("reporter R asserts digest D is malicious, as of
timestamp T, with evidence E"), not a single federation-wide "entry" — the
weighted sum a consumer evaluates (§3.2.1 point 4) is a live aggregate over
whichever reports are currently un-expired and un-cancelled:

- **Expiry** bounds how long any single wrongly-submitted report (§3.1's
  honest-false-positive case, or a poisoning attempt) can contribute to the
  weighted sum before it lapses on its own, without requiring anyone to notice
  and manually act. A report that is never revisited is itself a slow-motion
  risk to the aggregate signal's accuracy if it turns out to be wrong. **This
  is Peter's report-leasing resolution (§3.2.1 point 5): passive, no active
  retraction required.** But — worked through in full in §3.2.1 point 5, not
  restated here — expiry lowering the live weighted sum does **not**, by this
  SOW's default recommendation, by itself lift a consumer's block; it demotes
  a digest to a quarantine/review state. Treating mere non-renewal as proof of
  safety would be a fail-open failure mode indistinguishable from "the
  reporters just went quiet," which is not the same fact as "this was never
  actually dangerous."
- **Cancellation** (an explicit "this report was wrong, retract it now") gives
  an immediate, dead-man-switch-shaped remedy once a false report is
  identified — exactly `LeasedPermissionGrant.cancel()`'s local, monotone-void
  semantics (§1), transplanted from "revoke a permission" to "retract a
  report." Because a report has a single author (§3.2.1), **self-cancellation
  is well-defined without a governance question** — the open question left
  in an earlier pass of this SOW ("who can revoke a quorum-approved entry")
  is resolved for the common case by there being no quorum-approved entry to
  revoke, only individually-authored, individually-cancellable reports. The
  residual case — an operator or the registry itself forcibly retracting
  *another* reporter's report (e.g. after that reporter's identity is found
  compromised) — is not resolved here (§3.2.1's closing list). Cancellation is
  the *fast* correction path; expiry (above) is the *passive* one; per §3.2.1
  point 5, a fully-cleared block additionally wants a *positive* exonerating
  report, not just the absence of a condemning one — three distinct signals,
  not one.
- **Re-verification on renewal**, not silent permanence — a report that has
  survived N expiry cycles without being renewed/re-confirmed by its own
  reporter should lapse, the same way `LeasedPermissionGrant` requires the
  *caller* to actively renew before expiry rather than assuming permanence
  (§1: "Renewal is entirely the caller's responsibility"). This is a
  deliberate asymmetry with SCAP's `RegistryVerdict`, which (as far as
  STD-002 v1.3 defines it) does not appear to have a built-in expiry — a code
  verdict is comparatively stable over a jar's lifetime, whereas a single
  malicious-payload report's ongoing relevance (is this still actively used
  in attacks? was the original report even correct?) is much less durable and
  benefits from forcing periodic reconfirmation. Per §3.2.1 point 5, lease
  **duration** is recommended rank-scaled and asymmetric by polarity
  (a condemning report's default TTL outlasts an exonerating report's at the
  same rank, biasing passive drift toward review rather than silent
  clearance), and **renewal** is recommended tiered — a cheap liveness
  heartbeat between full re-attestations, so bare infrastructure uptime is
  never mistaken for a substantive "still confirmed" reassertion.

**Recommendation, not a final decision**: individual reports are structured
similarly in spirit to `LeasedPermissionGrant` — time-bounded by construction,
re-confirmable, explicitly self-cancellable — rather than modelled as
permanent `RegistryVerdict`-style records, and the per-digest weighted sum
(§3.2.1) is always computed live over currently-valid reports, never cached as
a settled verdict. Whether the *mechanism* is literally a `Lease`-bearing
object (reusing `net.jini.core.lease.Lease` directly, the way
`LeasedPermissionGrant` does) or a simpler TTL-with-timestamp field per report
in a purpose-built registry record type is an implementation decision for the
build phase — the *design property* (expiring, not permanent; explicitly
self-revocable, not just "eventually stale") is the thing this SOW is
asserting should hold either way. Whether a "block" and its "live weighted
signal" are the same tracked value or two distinct fields (the former sticky
by default, the latter decaying continuously per §3.2.1 point 5) is an
implementation decision, not resolved here either — but the *design property*
that they can diverge (a block can outlive the live signal that originally
justified it, deliberately, pending an explicit exoneration) is asserted.

### 3.4 Related work: what the literature says about sizing the quorum
asymmetry and shaping the quarantine state

The two questions §3.2.1 point 5 left open — how much harder should it be to
clear a digest than to block one, and what does "quarantine" actually mean
operationally — were researched properly for this revision, not just
re-skimmed from earlier search snippets. Five sources were pursued; three
were read in full (fetched and read page-by-page, formulas and all), one was
read only through a secondary summarization pass and is flagged as such, and
one could not be obtained in full text at all and is cited only at second
hand. That distinction is preserved deliberately below — this SOW does not
want to present secondhand paraphrase as if it were primary reading.

**Peer prediction and the ground-truth spot-check alternative (read in
full).** Miller, Resnick & Zeckhauser's peer-prediction method (2005) —
described here via how Gao et al. (below) frame the lineage, not read
directly — rewards a reporter for how well their report predicts *other*
reporters' reports on the same object, sidestepping the need for a
mechanism designer to know ground truth at all. Xi Alice Gao, James R.
Wright & Kevin Leyton-Brown, "Incentivizing Evaluation via Limited Access
to Ground Truth: Peer-Prediction Makes Things Worse"
(<https://arxiv.org/abs/1606.07042>, read in full) directly attacks the
premise that peer-prediction is the right tool once *any* ground truth is
available, even sparingly. Their formal setup: a *spot-checking mechanism*
$M=(p,y,z)$ obtains, with probability $p$, a noisy-but-unbiased *trusted
report* (their running example is a teaching assistant grading a sampled
subset of peer-graded essays) and rewards the agent by comparing their
report to it (function $y$); otherwise the agent is rewarded by an
*unchecked* mechanism $z$. A *peer-insensitive* mechanism is the special
case where $z$ is a flat constant — i.e., "get spot-checked sometimes and
rewarded for matching ground truth; otherwise get paid the same regardless
of what you report." Their Theorem 1/2 first show that no universal
peer-prediction mechanism can guarantee the truthful equilibrium is Pareto
dominant once agents have access to more than one observable signal about
the object (in peer grading: an essay's true quality *and* cheap, largely
uninformative cues like length or grammar) — agents can coordinate on
reporting the cheap, uninformative signal instead, and no peer-prediction
scheme can rule that out. Their central result (Theorem 3 and the
corollaries following it) is then that, for every universal peer-prediction
mechanism they are aware of in the literature, the *peer-insensitive*
mechanism achieves a **strictly stronger** incentive guarantee (dominant-
strategy truthfulness, vs. mere Pareto-dominance of the truthful
equilibrium) while requiring **less**, not more, spot-check probability
$p$ — i.e., less ground-truth access. Their own explanation for why this is
not paradoxical: peer-prediction mechanisms only work as a *group*
phenomenon — an agent's incentive to be honest depends on believing every
other agent will also be honest, which means the same logic that
sustains the honest equilibrium also sustains a coordinated dishonest one
equally well; a peer-insensitive mechanism reduces to an effectively
single-agent decision problem for each reporter, with no equivalent
group-coordination failure mode to defend against.

This maps onto this registry directly, and in a specific way: §3.2.1 point
1(i) had already, independently, proposed anchoring track-record rank
adjustment to "high-confidence ground truth that doesn't itself need
weighting — e.g. a CVE being published/patched for the underlying gadget,
or an operator's own manual confirmation" rather than to other reporters'
unilateral claims, precisely to avoid the rank-adjustment recursion. Gao et
al.'s result is a formal argument *for* exactly that choice, and *against*
ever building an inter-reporter peer-prediction-style consistency score as
a cheaper substitute for it — this SOW should treat that path as
foreclosed by the literature, not merely undesigned. The honest limit on
how far this transfers: Gao et al.'s asymptotic guarantees are built on
*many* agents repeatedly evaluating the *same* pool of objects (peer
grading: every student grades several essays; every essay gets several
grades) — a repeated-game structure this registry does not have at the
*per-digest* level (most digests will draw reports from a handful of
reporters at most, not "every reporter evaluates every digest"). It *does*
have that repeated structure at the *per-reporter, over time, across many
digests* level, which is exactly where point 1(i) already operates. The
transferable conclusion is scoped to that level, not to scoring a single
digest's evidence in isolation.

**Dirichlet/Beta-distribution trust models for collaborative intrusion
detection (read in full — this is the most load-bearing source found).**
Carol J. Fung, Jie Zhang, Issam Aib & Raouf Boutaba, "Dirichlet-Based Trust
Management for Effective Collaborative Intrusion Detection Networks," IEEE
Trans. Network and Service Management 8(2), 79–91, June 2011
(DOI 10.1109/TNSM.2011.050311.100028;
<https://ieeexplore.ieee.org/document/5871350/>, full text obtained via
<https://citeseerx.ist.psu.edu/document?repid=rep1&type=pdf&doi=ee6c65958124f49159eb28988718048da335a63e>)
models each collaborating intrusion-detection node's *reliability* as a
Dirichlet-distributed posterior over a discrete set of satisfaction levels,
built from a background-knowledge vector $\vec\gamma$ that is a
recency-weighted sum of past evidence (a *forgetting factor* $\lambda$
decays old observations; a *prior constant* $c_0$ weights an initial
uniform belief). The expected trust $T^{uv}=\sum_i w_i\gamma_i^{uv}/\gamma_0$
is a point estimate, but the paper also derives the *variance* of the
underlying random variable and from it a 95%-confidence-interval-style
**confidence level** $C^{uv}=1-4\sigma[Y^{uv}]$ (Eq. 12). This point
estimate plus confidence band is then used, not to make a single
threshold decision, but to sort every peer into **four** categories —
Highly Trustworthy / Trustworthy / Untrustworthy / Highly Untrustworthy —
by comparing the confidence interval's lower bound $T_l$ and upper bound
$T_h$ against a single trust threshold $th$ (Table I). The two *middle*
categories are exactly the "not confidently good, not confidently bad"
band — and the paper's acquaintance-management algorithm responds to that
band **operationally**, not just descriptively: it assigns a *higher rate
of verification (test) messages* to peers in the ambiguous middle bins and
a *lower* rate to peers the model is already confident about in either
direction (Table I: $R_l < R_m < R_h$, with the two confident extremes
both getting the low rate $R_l$). This is the single most directly
transferable structural finding for this SOW's quarantine question (§3.4.2
below).

Two further details from the same paper transfer more narrowly but still
usefully. First, the satisfaction function used to score a peer's answer
against a known-difficulty test (Eq. 1) has an explicit **asymmetric
penalty**: a constant $c_1>1$ (the paper uses $c_1=1.5$ in its
experiments) penalizes a peer more heavily for *underestimating* risk
(reporting a lower danger level than the true one) than for
*overestimating* it — a directly-cited, load-bearing precedent for baking
a fail-closed asymmetry into the scoring function itself, not just into a
downstream count threshold. Second, the paper's defenses against Sybil,
newcomer, and betrayal attacks map closely onto mechanisms this SOW had
already independently proposed: its *probation list* (a new node must
accumulate consistent good behavior before joining the trusted
acquaintance list, controlled by how large $c_0$ is set) is structurally
the same move as §3.2.1 point 1's operator-curated-floor-with-track-record
idea; and its *forgetting factor* is presented, in the paper's own
robustness discussion, as the mechanism that makes betrayal (a
previously-good peer suddenly turning malicious) get detected and
discounted *quickly* — which is the same justification this SOW already
gives, independently, for leasing individual reports rather than treating
a registry entry as permanent (§3.2.1 point 5, §3.3). Finding that
independent design choice re-derives a validated mechanism from this
literature, rather than contradicting it, is reassuring but was not
previously verified against a citable source.

Li, Meng & Kwok's 2022 survey (below) confirms the Fung/Boutaba Dirichlet
model is still regarded, as of 2022, as the field's reference "improved...
Bayesian approach" for CIDN trust — i.e. it is not a stale or superseded
choice to build from. But the same survey surfaces a genuine, concrete
caveat this SOW did not previously have: challenge-based/spot-check trust
systems exactly like Fung et al.'s are vulnerable to a named family of
evasion attacks once an adversary can *distinguish* a spot-check/test
interaction from a real one — the **Passive Message Fingerprint Attack**
(a malicious node learns to recognize test traffic and behaves honestly
only when it suspects it is being tested), the **Special On-Off Attack**
and a **Bayesian Poisoning Attack** (a malicious node calibrates a partial-
dishonesty rate designed to stay under a trust system's detection
sensitivity while still degrading it). Proposed countermeasures in that
literature (message-verification schemes that embed unpredictable
verification checks inside otherwise-normal-looking requests; a "Honey
Challenge" that makes real requests structurally indistinguishable from
test ones) exist but add real complexity. This is a concrete reason,
beyond the recursion problem already noted, that §3.2.1 point 1's deferral
of dynamic rank adjustment is the right call for P1/P2: *if* ground-truth
anchoring (point 1(i)) is ever built, the mechanism for deciding which past
reports get checked against a CVE/operator-confirmation must not be
predictable or observable to the reporter being checked, or a
sophisticated adversary gains exactly this evasion path. Whether that
distinguishability risk is even present in this registry's shape (a
reporter submits a report once; it is not obviously "requested" the way a
CIDN peer's response to a query is) is a real open sub-question this SOW
flags but does not resolve.

Reference: Wenjuan Li, Weizhi Meng & Lam For Kwok, "Surveying Trust-based
Collaborative Intrusion Detection: State-of-the-Art, Challenges and Future
Directions," IEEE Communications Surveys & Tutorials 24(1), 280–305, 2022
(DOI 10.1109/COMST.2021.3139052; full text read via
<https://backend.orbit.dtu.dk/ws/files/266752604/hkkr_Surveying_Trust_based_Collaborative_Intrusion_Detection_State_of_the_Art_Challenges_and_Future_Directions.pdf>).
Sections read directly: introduction/background, and the "Open Challenges"
and "Future Development" sections (survey §V). A search for 2020s work
beyond this survey did not surface anything more directly on-point for
*sizing* a quorum asymmetry or a middle trust state than what the survey
and the Fung et al. paper already give; more recent activity in this space
(blockchain-based CIDS, e.g. a 2024 survey "Collaborative Cybersecurity
Using Blockchain," arXiv:2403.04410, spot-checked but not read in full)
is mostly about consensus/architecture for tamper-evident sharing, not
about trust-threshold sizing, and is not cited further here for that
reason — it would genuinely help with §5's central-signer-vs-gossiped
question, not with §3.2.1's quorum question, and is flagged for whoever
picks up §5 rather than pursued further here.

**EigenTrust (read only via a secondary summarization pass over the
fetched primary source, not read page-by-page — flagged accordingly, lower
confidence than the two sources above).** Kamvar, Schlosser & Garcia-
Molina, "The EigenTrust Algorithm for Reputation Management in P2P
Networks," WWW 2003 (<https://nlp.stanford.edu/pubs/eigentrust.pdf>)
computes a *global* trust value per peer as the dominant eigenvector of a
row-normalized matrix of *pairwise local* trust scores, propagated
transitively ("trust the peers trusted by peers you trust"), seeded by a
small pre-trusted set to keep the iteration from converging on an
arbitrary or manipulated consensus. This does not transfer cleanly to this
registry, and it is worth being explicit about why: EigenTrust's whole
mechanism depends on a sufficiently connected graph of *bilateral*
interactions between peers who transact directly with each other. This
registry's topology (§5) is hub-mediated — reporters interact with the
registry, not with each other — so there is no natural "does reporter A
trust reporter B" edge for a power-iteration to run over, only "does the
operator/registry trust reporter A" (the existing rank concept). Read at
the level this secondary pass supports, EigenTrust's escape from its own
version of the rank-adjustment recursion (bootstrapping a *global* trust
value from purely *local*, peer-supplied trust assessments) is its
pre-trusted seed set — which is a formalization of "some trust has to come
from outside the peer system," i.e. it *confirms* rather than offers an
alternative to this SOW's point 1(i) conclusion (anchor to non-recursive,
outside-the-loop ground truth). It does not, on this reading, hand this
SOW a usable mechanism it didn't already have.

**"Design of Intrusion Sensitivity-Based Trust Management Model for
Collaborative Intrusion Detection Networks" (Li & Meng) — could not be
obtained in full text; the mirror found (inria HAL) blocks automated
fetches with a bot-challenge, and no other open copy was located in the
time available for this pass.** Cited here only at second hand, via the
Li/Meng/Kwok 2022 survey's own description: the paper evaluates whether
"intrusion sensitivity"-weighted CIDN trust models are robust against a
*pollution attack*, where colluding malicious nodes cooperate to return an
untruthful alarm-ranking list and compromise alarm aggregation —
structurally the same concern as this SOW's §3.2.1 point 3 (a targeted,
low-threshold victim). No further design conclusion is drawn from it here
beyond flagging it as a signpost for the build phase — this SOW is not
willing to present a secondhand survey paraphrase as if it were primary
reading, per this document's own sourcing discipline.

#### 3.4.1 What doesn't transfer, stated plainly

- Peer-prediction's core machinery (reward agreement between agents who
  each independently observed the *same* object) assumes dense, repeated
  multi-agent observation of shared objects. This registry's per-digest
  observation is typically sparse. Gao et al.'s result means this is not
  merely undesigned but actively contraindicated once any ground-truth
  spot-check capability exists at all (which point 1(i) already assumes) —
  don't build inter-reporter consistency scoring as a "cheaper" substitute
  for ground-truth anchoring.
- EigenTrust's transitive graph propagation needs a bilateral peer-
  interaction graph this registry's hub-mediated topology doesn't have,
  and isn't usable without inventing an interaction graph that otherwise
  serves no purpose here.
- Fung et al.'s Dirichlet machinery computes confidence in a *peer's*
  general reliability from many interactions accumulated over time; this
  SOW needs confidence in a specific *digest's* malice status, aggregated
  across a handful of differently-ranked reporters at a point in that
  digest's lifetime. The same underlying tool (a Bayesian posterior plus a
  variance-derived confidence band) is reusable — §3.4.2 proposes reusing
  it — but it must be re-derived pointing at "digest, aggregated over
  reporters" rather than "reporter, aggregated over time." That
  re-derivation is real work, not a drop-in reuse, and is left for the
  build phase.
- The CIDS on-off/fingerprinting evasion literature assumes an adversary
  that can distinguish spot-check traffic from real traffic in a
  request/response protocol. Whether an analogous distinguishability risk
  exists for this registry's point-1(i) ground-truth anchoring is not
  worked out here — flagged, not resolved.

#### 3.4.2 Proposed answers to the two open questions

**Question 1 — the relative quorum bar for exonerating vs. condemning
reports. Confidence: medium on the structural approach, low on the exact
numbers.** Recommendation: don't express the asymmetry as a single
arbitrary multiplier bolted onto §3.2.1 point 2's raw-count floor in
isolation. Instead, follow Fung et al.'s structural move and derive both
bars from *one* underlying live signal's confidence band, not two
independently-tuned pipelines:

- Track, per digest, not only the point-estimate rank-weighted sum (§3.2.1
  point 2) but a variance-derived confidence band around it — the same
  kind of quantity Fung et al. compute (their Eqs. 9–14) from the
  per-reporter weights contributing to the sum, re-pointed at "reporters
  contributing evidence about one digest" instead of "one node's history
  of interactions over time" (§3.4.1's honest caveat about re-derivation
  applies here).
- **Condemnation** is reached when the *lower* bound of that confidence
  band already clears the consumer's condemn threshold — i.e. even a
  pessimistic (toward "less evidence than it looks like") reading of the
  signal justifies blocking.
- **Exoneration** is reached only when the *upper* bound of the
  "still-dangerous" reading drops below the consumer's exoneration
  threshold — i.e. even an optimistic-for-still-dangerous reading must be
  low. Using opposite tails of the same distribution for the two decisions
  mechanically produces "harder to clear than to block" as a property of
  the confidence band's shape, rather than as two disconnected,
  arbitrarily-related numbers a designer has to separately justify.
- Layered on top, per §3.2.1 point 5's existing sketch: the raw-reporter-
  count floor should itself be asymmetric, with the exoneration floor set
  higher than the condemnation floor. As a concrete build-phase starting
  point (not derived from any cited source — an engineering estimate, same
  status as point 2's existing "small, e.g. 2 or 3" note): condemnation
  floor 2–3 distinct reporters, exoneration floor roughly double, 4–6.
  This piece is explicitly *not* claimed to follow from the literature —
  no source found here sizes an exact multiplier for this specific
  asymmetry — and should be treated as a first guess to validate against
  real P1/P2 operational experience, exactly as point 2 already treats its
  own floor value.

**Question 2 — the concrete operational shape of the quarantine state.
Confidence: medium-high on the structure, lower on deployment-specific
mechanics (which this SOW continues, deliberately, to not mandate).**
Recommendation: quarantine should not be a passive label; it should drive
an active, adaptive response, modeled directly on Fung et al.'s
categorization-driven verification-rate table. A digest whose confidence
band straddles the ambiguous middle — neither confidently condemned nor
confidently exonerated by §3.4.2's tail test above — should, for a
consuming deployment that implements this:

1. Remain blocked by default (§3.2.1 point 5's existing fail-closed-leaning
   default — unchanged).
2. Surface as an explicit, actionable operator alert, visibly distinct
   from a confidently-condemned block in whatever the deployment's admin
   surface is — a concrete, minimal requirement, not just an internal
   state bit nobody looks at.
3. Where a deployment has the infrastructure for it, route future
   encounters with *that specific* quarantined digest through additional
   local scrutiny rather than a flat reject with no extra signal captured
   — e.g. increased logging/telemetry on any decode attempt matching it,
   or (for a deployment sophisticated enough to have one) an instrumented
   or sandboxed decode attempt. This is Fung et al.'s "spend more
   verification effort on the peers you're least certain about"
   transplanted from "send more test messages to an ambiguous peer" to
   "spend more local scrutiny on an ambiguous digest."
4. Elevate that digest's priority for solicited re-investigation — a
   deployment could locally flag its own quarantined digests as priority
   candidates the next time it has spare capacity for the kind of
   manual/CVE-anchored ground-truth confirmation §3.2.1 point 1(i) already
   relies on, closing the loop between "quarantine" and the mechanism that
   can actually resolve one, rather than leaving quarantine a dead end
   waiting passively for someone else's report.

Caveat, stated as plainly as the rest of this SOW states its caveats:
(3) and (4) are deployment-capability-dependent, consistent with this
SOW's existing refusal to mandate deployment-side implementation (§2.3,
§3.2.1 point 5's own opt-in framing). What *is* newly asserted here is
narrower: "quarantine" should carry a minimum operational meaning of
*elevated-scrutiny, visibly-flagged block* for any deployment that
implements it at all, not merely "not yet fully confirmed" as an internal
label. A deployment that does only (1) is still compliant with this SOW's
fail-closed floor, but is discarding the value this section adds.

## 4. Scope/boundaries: what digest-exact matching does and does not catch

Be honest about the limits — oversetting expectations here would be worse than
not building this at all, because a deployment that believes this mechanism
catches more than it does may under-invest in the admission controls (§1) that
actually do the load-bearing work.

- **Digest-exact matching catches byte-identical recurrence, nothing more.**
  Because DER's canonical encoding means any change to the logical content — not
  just padding or field order, which DER already forecloses, but any actual
  change to a field value, an added/removed optional field, a different
  collection size, a different nested object — produces a **different** digest
  (that is the entire point of using a cryptographic digest: small input changes
  cause large, unpredictable output changes). An attacker who successfully
  evades this mechanism does not need to defeat the digest's cryptography — they
  only need to change *anything* about the logical payload and re-attack. This
  is a real, narrow limitation, not a hedge: this mechanism defends against
  **lazy or automated re-use of an already-caught payload** (a bot replaying a
  known-working exploit, a script kiddie reusing a public PoC verbatim), not
  against a motivated attacker willing to vary their payload per attempt.
- **Whole-graph digest vs. sub-component digest is a real scoping choice (§2.2),
  not just an optimization.** A whole-object-graph digest only catches the exact
  full payload recurring; a sub-component digest (of a nested `@AtomicSerial`
  field specifically, per `decodeNested`, §2.1) can catch "the same dangerous
  gadget in a structurally different outer envelope" — *if and only if* that
  gadget's own DER encoding happens to be byte-identical across the two attacks.
  An attacker who wraps the same gadget with even one different field elsewhere
  in the *gadget's own* encoding (not just the envelope) still evades the
  sub-component digest too. Sub-component matching narrows the "attacker has to
  change literally the whole graph" requirement down to "attacker has to change
  something about the gadget itself" — a real improvement in coverage, but not
  a qualitative shift from digest-exact to pattern/behavioral matching.
- **What this is explicitly not**: a substitute for structural/behavioral/
  heuristic detection (e.g. "flag any payload containing a `Comparator` field
  inside a `PriorityQueue`-shaped structure regardless of exact values" — a
  *pattern*, not a digest). Pattern-based detection is a different, much harder
  and much more general mechanism (higher false-positive risk, requires
  ongoing signature/rule authorship — exactly the human-signature-authoring
  cost this SOW's mechanism is explicitly trying to avoid, per §0). This SOW
  scopes digest-exact matching only; pattern detection, if ever wanted, is a
  separate design with a different, harder threat model (false-positive rate
  against legitimate traffic) and is out of scope here.

## 5. Distribution/propagation mechanism

STD-002's Verdict Registry (§1, §2.4) already implements **both** shapes for a
structurally similar (code-verdict) problem, and both are directly transferable
options here, not novel inventions:

- **Pull (poll)**: `getVerdictByHash(contentHash)` — a client queries the
  registry synchronously by digest. Directly analogous to a CRL fetch or an
  OCSP-without-stapling check. Simple, no persistent connection required,
  naturally handles registry unavailability as an ordinary RPC failure (§2.3).
  Cost: either a lookup on every decode (latency + registry load, unless
  cached — see below) or a periodic bulk pull (staleness window between pulls).
- **Push (event subscription)**: `registerVerdictListener(...)` — a deployment
  subscribes and receives updates as they're recorded, no polling latency,
  matches Jini's native remote-event model (`net.jini.core.event`) that JGDMS
  already uses pervasively elsewhere. Cost: a persistent listener registration
  per participating deployment, and (per §2.3) a decode-path decision about
  what to do if the local subscription has lagged or dropped events.
- **Recommendation, not a final decision**: local caching with push-driven
  invalidation/update, pull as the fallback/bootstrap path — a deployment
  subscribes for live updates (push) but also periodically reconciles
  (pull) against registry state to bound the damage of a missed event, the
  same "belt and braces" shape STD-002 itself uses (`RegistryVerdict
  (push/pull)` is stated explicitly as both, in the STD-002 topology diagram).
  The actual decode-path check (§2) should be a **local, in-memory (or locally
  persisted) cache lookup**, not a network round-trip per decode — the push/pull
  mechanism keeps that local cache warm, but the hot path itself must not block
  on network I/O per §2.3's fail-open-on-unavailability stance.
- **A precedent this SOW does *not* recommend copying wholesale**: STD-002's
  single, centrally-signing Host 3 (Verdict Registry) is itself a single
  administrative trust root — appropriate for SCAP because Host 3 is
  operator-controlled infrastructure signing verdicts derived from a vetted
  engine pool (§2.4). Whether the malicious-digest registry should have a
  single authoritative signer (simpler, but a central point of trust and
  failure across an open, multi-organization federation) or a fully
  peer-to-peer gossiped model (harder, avoids the central-trust-root problem,
  but reopens the Sybil/quorum questions of §3.2 at the transport layer too —
  who do you gossip with, and how do you weight what they tell you) is an open
  question, not resolved here, and is likely to have a different right answer
  for a closed single-organization fleet than for an open multi-party registry.

## 6. Privacy/sharing considerations

- **The digest itself is a one-way SHA-256 hash — it does not, by construction,
  reveal the original payload's content.** Sharing a bare digest across
  organizational boundaries is, on its face, safe in the same sense any content-
  addressed hash sharing is (comparable to sharing a malware hash via an
  industry threat-intel feed).
- **The submission metadata around the digest is where leakage risk actually
  lives, not the digest itself.** A well-formed submission per §3.2(c)
  (attestation of a real local rejection) plausibly needs to carry *some*
  context to be independently verifiable/corroborable — a timestamp, the
  reporting deployment's identity (if identity-weighted per §3.2(d)), possibly
  a description of the observed failure mode. Any of that metadata can leak
  information about the reporting deployment: that it exists, that it is
  running JGDMS/this registry integration, roughly when it was attacked, and
  (if the failure-mode description is too specific) internal configuration
  details. **This needs explicit minimization in the submission schema** — the
  build phase should treat "what is the minimum metadata needed for the chosen
  §3.2 trust policy to function" as a real design constraint, not an
  afterthought, rather than defaulting to attaching maximal diagnostic context
  "because it might be useful."
- **The digest cannot leak information about a victim's specific deployment on
  its own, but a *pattern* of digest submissions over time plausibly can** — a
  third party who can observe the registry (if it is not itself access-
  controlled) could infer that some deployment is under sustained attack, or
  correlate timing of submissions across organizations, even without seeing any
  payload content. Whether the registry itself should be openly readable
  (maximizes propagation speed, §0's core benefit) or access-controlled to
  participants only (reduces this inference surface, at the cost of excluding
  non-participants from the protection) is a federation-policy decision, not
  resolved here.
- **Opt-in/opt-out matters on both the submitting and consuming side, separately.**
  A deployment should be able to consume registry data (benefit from others'
  reports) without being required to submit its own (some organizations may be
  unable to share externally for compliance/confidentiality reasons even for a
  bare hash), and conversely a deployment that only wants to contribute without
  consuming (unusual, but not incoherent) should not be architecturally
  prevented from doing so. This argues for the submit and consume paths being
  **independently toggleable**, not a single all-or-nothing "participate in the
  registry" switch.

## 7. Honest summary of what is unresolved

Restated in one place, because this SOW deliberately does not resolve them:

1. **Resolved in principle by §3.2.1**: the registry supplies ranked/weighted
   corroboration signal (rank-weighted sum + raw distinct-reporter floor); each
   consumer sets its own local acceptance threshold, avoiding the need for
   federation-wide agreement on one correct bar. What remains genuinely open
   within that resolution: (a) the recursion problem in dynamic rank
   adjustment — confirming a past report was wrong needs its own trust-weighted
   signal, with no clean base case (§3.2.1 point 1); (b) the exact raw-count
   floor value (§3.2.1 point 2); (c) whether a targeted attacker can observe
   or infer a specific victim's configured threshold and calibrate a minimal-
   quorum attack against it (§3.2.1 point 3); (d) **now proposed, not fully
   calibrated, per §3.4.2** (added after literature research into
   peer-prediction and Dirichlet-based collaborative-intrusion-detection
   trust models): the quarantine state's operational shape (an
   elevated-scrutiny, visibly-flagged block, structurally modeled on Fung
   et al.'s confidence-driven verification-rate table) and the relative
   quorum bar for exonerating vs. condemning reports (a confidence-band
   tail test plus an asymmetric raw-reporter floor) — the *structure* is
   argued for at medium/medium-high confidence, the *exact numbers*
   (floor sizes, threshold placement) remain build-phase calibration, not
   literature-derived; and the concrete lease-duration/renewal-cadence
   parameters for leased reports remain entirely open (§3.2.1 point 5).
2. Whole-graph digest, sub-component digest, or both (§2.2) — and if both, at
   what performance cost per decode.
3. Fail-open vs. fail-closed on registry unavailability (§2.3) — recommended
   fail-open here, but not mandated. **Distinct from, and resolved
   differently than, §3.2.1 point 5's decay question**: §2.3 is about the
   registry being unreachable; point 5 is about the registry being reachable
   and correctly reporting that live corroboration has decayed. §2.3 leans
   fail-open (this layer is optional defense-in-depth on top of unconditional
   checks); point 5 leans fail-closed (a block, once justified, should not
   silently lift on mere silence) — the two are not in tension with each
   other, but it is worth being explicit that this SOW does not apply one
   uniform fail-open/fail-closed rule throughout; each case is reasoned
   separately against what "failing" actually means there.
4. **Largely resolved by §3.2.1/§3.3**: there is no single quorum-approved
   entry to revoke — only individually-authored reports, self-cancellable by
   their own reporter, feeding a live per-digest weighted sum. Still open: the
   operator-override case of forcibly retracting *another* reporter's report
   (e.g. after that reporter's identity is later found compromised) has no
   defined mechanism yet.
5. Central-signer vs. gossiped propagation topology (§5), and whether the right
   answer differs for a closed single-organization fleet vs. an open multi-party
   registry.
6. Submission metadata minimization schema (§6) — not designed here at all,
   only flagged as necessary.
7. Whether this warrants a formal `JGDMS-STD-0xx` standard document (in the
   style of STD-002/STD-006/STD-007) once the trust-policy question (1) is
   settled, given it is a cross-deployment interoperability protocol, not a
   single-module mechanism.

## 8. Rough phased plan (illustrative, not a commitment)

1. **P0 — local-only, no federation.** Build the digest computation (§2.2, start
   with whole-graph, SHA-256 over the top-level canonical DER encoding) and the
   decode-path hook (§2.1) as a **purely local** deny-list: a deployment can
   record a digest it discovered was malicious and have its *own* future decodes
   check against it, with no network component and no multi-party trust problem
   at all (§3 is entirely moot at this phase — there is exactly one trusted
   reporter: the deployment itself). This alone has real value (a deployment
   that gets hit twice by the same replayed payload is protected the second
   time) and is a clean, low-risk increment to validate the hook point and
   digest granularity decisions (§2.2) before any federation complexity.
2. **P1 — closed-federation, flat rank.** Extend P0 to a small, operator-
   curated federation (§3.2(b)/§3.2.1: every reporter gets the same
   operator-assigned rank, which degenerates to §3.2(a)'s unilateral-trust
   shape when the raw-count floor is set to 1, or a real corroboration
   requirement when set higher) — e.g. a single organization's fleet, or a
   small set of mutually-trusting JGDMS deployments, each locally configuring
   its own acceptance threshold (§3.2.1) even though every reporter is
   equally ranked at this phase. Exercises the propagation mechanism (§5) and
   the per-report lease/expiry shape (§3.3) — including a first cut at leased,
   renewable reports with a single fixed TTL (rank-scaled and polarity-
   asymmetric durations, §3.2.1 point 5, deferred to P2) and the simplest
   version of the quarantine distinction (block persists past decay; lifting
   is a manual operator action, not yet an automated exonerating-report
   quorum) — but P1's manual-review step should already give quarantine the
   minimum operational meaning §3.4.2 argues for (a visibly-flagged block
   distinct from a confirmed one, not just an internal state bit), even
   before P2 automates exoneration — without yet needing graded rank.
3. **P2 — graded rank + per-consumer threshold, open federation.** Build
   §3.2.1's actual resolution: an operator-curated *graded* rank per reporter
   (not flat), the raw-distinct-reporter-count floor enforced structurally
   alongside the rank-weighted sum (§3.2.1 point 2), the per-consumer
   local acceptance-threshold configuration that lets deployments outside a
   single administrative domain participate without agreeing on one global
   bar, and rank-scaled/polarity-asymmetric lease durations plus tiered
   (heartbeat + periodic re-attestation) renewal for individual reports
   (§3.2.1 point 5). This phase is also where the exonerating-report path
   (a positive corroborated signal, distinct from mere condemning-report
   decay, required by this SOW's recommended default to fully lift a
   quarantined block) gets built, not just the manual-operator-action
   version P1 shipped with — including §3.4.2's proposed shape for that
   path: tracking a variance-derived confidence band around the per-digest
   weighted sum (not just the point estimate), evaluating condemnation
   against the band's lower tail and exoneration against the band's upper
   tail, and an asymmetric raw-reporter floor (condemnation floor 2–3,
   exoneration floor roughly double — a build-phase starting estimate, not
   a literature-derived value, to be tuned against real P1/P2 operational
   data). Dynamic (track-record-based) rank adjustment is
   explicitly **out of scope for P2** — §3.2.1 point 1 flags its feedback-loop
   recursion as unresolved; P2 ships with the operator-curated floor as the
   only rank mechanism. Attestation-of-reproduction (§3.2(c)) and identity
   cost via SPIFFE SVIDs (§3.2(d)) are natural companions to build alongside
   P2, since both feed directly into what a rank/report is worth and how
   expensive a Sybil attack against the raw-count floor is (§3.2.1 point 3).
4. **P3 — formal standard, if warranted.** Once P2's trust policy is validated
   in practice, consider whether this belongs as a `JGDMS-STD-0xx` document
   (§7 item 7) so other implementations of the registry protocol (not just
   JGDMS's own) can interoperate — analogous to how STD-006 (DER wire format)
   and STD-002 (SCAP) are standards, not just implementation notes.

**Out of scope for all phases above:** pattern/behavioral detection (§4, explicitly
distinguished from digest-exact matching); any weakening of the existing
`DeSerializationPermission`/`check(GetArg)`/class-identity admission controls this
mechanism sits alongside (§0) — this SOW adds a layer, it does not touch the
existing ones.

## References

- `jgdms-der/src/main/java/au/net/zeus/jgdms/der/object/ObjectCodec.java` — decode
  entry points (`decode:298-353`, `decodeNested:1601-1658`,
  `decodeHierarchy:1733+`), the per-class `DeSerializationPermission("ATOMIC")`
  gate (`checkAtomicDeSerializationPermitted:198-233`), and the `PROXY` gate
  (`checkProxyDeSerializationPermitted:240-269`) — the hook point analysis (§2).
- `jgdms-platform/src/main/java/org/apache/river/api/io/
  DeSerializationPermission.java` — the existing per-class admission-control
  permission this mechanism sits alongside, not inside (§1).
- `jgdms-platform/src/main/java/org/apache/river/api/security/DigestGrant.java` —
  the existing SHA-256-content-digest-keyed *code* identity mechanism (allowlist);
  the key-shape precedent this SOW's registry inverts to a denylist over *payload*
  identity (§1).
- `jgdms-der/src/main/java/au/net/zeus/jgdms/der/schema/AtomicSerialSchemaRecord.java`,
  `jgdms-der/src/main/java/au/net/zeus/jgdms/der/schema/SchemaChain.java` —
  `schemaDigest()`, the existing SHA-256-digest-of-*schema* mechanism; cited in
  §0 to correct the premise that a payload-value content digest already exists
  (it does not — this digests class shape, not field values).
- `jgdms-platform/src/main/java/net/jini/io/MarshalledInstance.java` —
  `computeHash(byte[]):207-213`, the existing weak (non-cryptographic) payload
  hash used only for `MarshalledObject`-compatible `equals()`/`hashCode()`; cited
  in §0 as the closest-but-insufficient existing mechanism.
- `jgdms-platform/src/main/java/org/apache/river/api/security/
  LeasedPermissionGrant.java`, `docs/DESIGN-LeasedPermissionGrant.md` — the
  lease-driven dead-man-switch precedent this SOW's §3.3 proposes reusing in
  spirit for expiring/revocable registry entries.
- `docs/JGDMS-STD-002-Safe-Codebase-Audit-Pipeline-Standard-v1.3.md` — SCAP /
  the Verdict Registry (Host 3): the closest existing federated, digest-keyed
  trust registry in this codebase, for *code* rather than payload values; §2.4
  and §5 explain what does and does not carry over, and why its quorum polarity
  (single `DANGEROUS` condemns) is safe there but would be a poisoning vector
  here (§3.1-§3.2) given the different reporter trust shape.
- `SOW-AI-Agent-Authority-Support.md`, `docs/DESIGN-LeasedPermissionGrant.md` —
  companion precedent for time-bounded/revocable authority primitives generally.
- `docs/DESIGN-CorroborationFramework.md` — a general provenance/justified-belief
  framework already sketched elsewhere in this codebase's design history; not
  read in depth for this SOW, but flagged here as a plausibly relevant existing
  design line for whoever picks up §3.2(b)'s quorum/corroboration question in
  the build phase, since "how much should N independent, imperfect reports be
  trusted" is exactly the shape of question that framework is meant to address.
- `SOW-JMX-JERI-DER-Connector.md`, `SOW-DirtyChai-Serialization-SPI-DER.md` — the
  two prior SOWs in this same session this document matches tone/format/level-
  of-detail against.

### External research references (§3.4)

- Xi Alice Gao, James R. Wright & Kevin Leyton-Brown, "Incentivizing
  Evaluation via Limited Access to Ground Truth: Peer-Prediction Makes
  Things Worse," arXiv:1606.07042 — <https://arxiv.org/abs/1606.07042>.
  Read in full. Grounds §3.4's argument that ground-truth spot-checking
  (already assumed by §3.2.1 point 1(i)) beats inter-reporter
  peer-prediction-style consistency scoring, not just for this SOW's
  convenience but provably, once any ground-truth access exists at all.
- Carol J. Fung, Jie Zhang, Issam Aib & Raouf Boutaba, "Dirichlet-Based
  Trust Management for Effective Collaborative Intrusion Detection
  Networks," IEEE Trans. Network and Service Management 8(2), 79–91, 2011,
  DOI 10.1109/TNSM.2011.050311.100028 —
  <https://ieeexplore.ieee.org/document/5871350/> (full text obtained via
  <https://citeseerx.ist.psu.edu/document?repid=rep1&type=pdf&doi=ee6c65958124f49159eb28988718048da335a63e>).
  Read in full, including all formulas. The single most load-bearing
  external source for §3.4.2's proposed answers to both open questions —
  the confidence-band/tail-test structure and the confidence-driven
  differential-scrutiny idea both come from this paper.
- Wenjuan Li, Weizhi Meng & Lam For Kwok, "Surveying Trust-based
  Collaborative Intrusion Detection: State-of-the-Art, Challenges and
  Future Directions," IEEE Communications Surveys & Tutorials 24(1),
  280–305, 2022, DOI 10.1109/COMST.2021.3139052 —
  <https://backend.orbit.dtu.dk/ws/files/266752604/hkkr_Surveying_Trust_based_Collaborative_Intrusion_Detection_State_of_the_Art_Challenges_and_Future_Directions.pdf>.
  Introduction/background and Open-Challenges/Future-Development sections
  read directly. Confirms the Fung et al. Dirichlet model is still the
  field's reference Bayesian approach as of 2022, and surfaces the
  PMFA/on-off/Bayesian-poisoning evasion-attack caveat cited in §3.4.
- Sep Kamvar, Mario Schlosser & Hector Garcia-Molina, "The EigenTrust
  Algorithm for Reputation Management in P2P Networks," WWW 2003 —
  <https://nlp.stanford.edu/pubs/eigentrust.pdf>. Read only via a
  secondary summarization pass over the fetched source, not page-by-page —
  lower confidence than the two sources above; flagged as such at every
  point it is cited in §3.4.
- Li & Meng, "Design of Intrusion Sensitivity-Based Trust Management Model
  for Collaborative Intrusion Detection Networks" — full text not
  obtained (the located mirror, inria HAL, blocks automated fetches with a
  bot-challenge); cited in §3.4 only at second hand via the Li/Meng/Kwok
  2022 survey's description of it, explicitly marked as secondhand and
  not relied on for any design conclusion beyond that flag.
- Miller, Resnick & Zeckhauser, "Eliciting Informative Feedback: The
  Peer-Prediction Method," Management Science 51(9), 2005 — not read
  directly; described in §3.4 only via how Gao et al. (above) frame the
  peer-prediction lineage they are arguing against.
