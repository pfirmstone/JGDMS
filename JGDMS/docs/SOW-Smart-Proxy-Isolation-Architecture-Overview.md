# Overview — Smart-proxy process isolation architecture (index document)

- **Drafted:** 2026-07-17.
- **Status:** INDEX — ties together decisions and task breakdowns already captured elsewhere; this
  document defines nothing new on its own. Where it summarizes a decision, the summary is not
  authoritative — the source SOW/section cited is.
- **Why this exists:** four documents were built up over one continuous design session, each answering
  a question the previous one raised, but each only cross-references its neighbors piecemeal (a
  "Companions" line, a "§12 point N" reference buried in prose). There was no single place to start
  reading to get the whole shape. This is that place.
- **No production code was written or modified in producing this document, or any of the four it
  indexes.** All four remain DRAFT/design-captured; see each for its own task-level status.

---

## 1. The decision, in one place

JGDMS smart proxies (mobile/downloaded code executing locally, "next to a large data source" per the
original framing) are moving to **mandatory, unconditional process isolation**:

- Every downloaded/mobile smart proxy executes in an **isolated OS process, pooled one-per-distinct
  remote SPIFFE principal** (not per-object, not per-local-consumer) — cheap to key because
  `serverPrincipals`/`serverSubjectFromContext` are already extracted at the relevant call site.
- The **local client process never loads a smart proxy's implementation bytecode at all.** It holds
  only a thin `java.lang.reflect.Proxy` stub, built from locally-already-known interface types, whose
  `InvocationHandler` forwards every call over a Unix Domain Socket (UDS) to the real object living in
  the isolated process. The entire codebase-download/classload/SCAP-BAE-verdict-gating pipeline moves
  into the isolated process — it is the only place that ever runs a smart proxy's actual bytecode.
- This closes the **cross-principal** cache-timing side-channel attack (Flush+Reload/Evict+Reload class)
  **by construction** — no shared address space, no shared bytecode — narrowing the surviving concern to
  (a) same-principal intra-subprocess timing (argued harmless — bounded by the client's own
  `GrantPermission` ceiling, both proxies serve the same remote party) and (b) Prime+Probe-class
  cache attacks, which need CPU/core-affinity separation on top of process separation to close.
- A freshly-spawned isolated process needs its own permission ceiling *after* it determines its own
  SCAP verdict, which none of JGDMS's existing dynamic-policy machinery delivers cross-process today —
  closing that gap is its own new component, `SubProcessDynamicPolicy`.
- Subprocess lifecycle reuses JERI's existing DGC (dirty-set/lease) machinery rather than inventing new
  liveness tracking — a subprocess is spawned on first need and torn down once nothing holds a live
  reference to anything it hosts.
- All of the above is a **decision on record, not yet a deployed guarantee** — the actual wiring
  (where routing is inserted, how a subprocess is spawned/tracked, how a grant is pushed into one) is
  identified but not yet built. Don't let public-facing language describe this as already true of a
  running deployment.

---

## 2. The four documents, in dependency/read order

### 2.1 `SOW-Unix-Domain-Socket-JERI-Transport.md` — the anchor

**Read this first.** Everything else in this list either extends it or was spawned by a gap it found.

- Establishes what's actually built (Increment 1: `net.jini.jeri.uds.{UdsEndpoint,UdsServerEndpoint,
  Constraints,UdsPaths}` — filesystem-permission-gated, no peer authentication yet, nothing currently
  routes real proxy traffic through it) versus what §12 decides should be built on top of it.
- **§12** is where the mandatory-isolation decision itself lives, point by point: per-principal pooling
  and the `Key`-class endpoint-vs-principal correction (point 1); CPU/core-affinity requirement (point
  2); the candidate insertion point (`PreferredProxyCodebaseProvider.resolve()`) and the full mechanics
  of client/subprocess split — where unmarshalling happens, how the client determines which interfaces
  to build its stub with (including the new `ProxySerializer` interface field), and the
  per-consumer-JVM/third-party-ServiceAPI stub pattern (point 3); Increment 2 relevance (point 4); the
  **critical, still-unremediated finding** that the OSGi `ProxyBundleProvider` completely bypasses the
  SCAP/BAE verdict gate (point 5); the dynamic-policy gap investigation and the two decisions it produced
  — namespace relocation and `SubProcessDynamicPolicy` (point 6); subprocess lifecycle via DGC (point 7);
  QA implications, flagged not investigated (point 8).
- **Status:** design decisions recorded; wiring (point 3) not built.

### 2.2 `SOW-UDS-JERI-Increment-2-Peer-Authentication.md` — peer authentication

- Captured **2026-07-03**, before the §12 mandatory-isolation decision (2026-07-17) existed — it
  predates and does not reference §12. Read it as "how Increment 1's channel gets real peer
  authentication" (`SO_PEERCRED` cross-validated against a presented SPIFFE SVID), independent of why
  routing through that channel later became mandatory.
- Relevant to the overall picture because §12 point 4 explicitly says Increment 2 is not blocking for
  the isolation/side-channel property (address-space separation and peer authentication are different
  properties) — but it is still needed for honestly claiming `Confidentiality.YES` on the transport, and
  matters more once real traffic is mandatorily routed through UDS than it did when the channel was
  optional/dormant.
- **Status:** design captured, decided, not built. Not re-scoped by this session's later work — still
  accurate on its own terms.

### 2.2a `JGDMS-STD-009-Service-RemoteFunction-Annotation-Model-v0.1-DRAFT.md` — independently convergent prior art (added 2026-07-17)

- Not originally written as part of this set — captured **2026-07-03**, two weeks before the UDS
  isolation decision, starting from an unrelated question (the annotation model for `@JiniService`/
  `@RemoteFunction`). Its **§8.6** ("the sidecar as a mobile-code container [PROPOSED]") independently
  states the same governing principle §1 above arrived at from the timing-side-channel question: "never
  run downloaded code in your own process — quarantine it in a sidecar... over UDS." Its **§6.4**
  ("Interface stripping and boomerang") independently documents the identical `RMIClassLoader.
  loadProxyClass` all-or-nothing interface-resolution finding this session's own fresh code
  investigation re-derived while designing the `ProxySerializer` interface field (§12 point 3(ii) of the
  UDS SOW) — down to citing the same method.
- **What STD-009 has that the other four documents don't:** the `DYNAMIC`/`FUNCTION` taxonomy that
  explains *why* callback listeners never need sidecar treatment (they never carry a codebase — §7);
  the **filter** tenant (server-side predicate pushdown, §8) with its own, deliberately different,
  per-query-ephemeral lifecycle; the **boomerang** naming/mechanism for "relay the retained wire form,
  never re-derive from a live/stripped proxy" — directly applicable to, but not yet reconciled with,
  this session's local-delegate-stub-forwarding case; the ServiceUI kill-switch and disjoint-sidecar
  (UI + proxy) composition idea, explicitly untouched by anything else in this list.
- **What this session's four documents have that STD-009 doesn't:** a *built-out* task-level design for
  the smart-proxy tenant specifically (STD-009 §8.6 defers "full sidecar/worker design" as its own
  future surface); the CPU-affinity/Prime+Probe requirement, originated for the smart-proxy sidecar and
  **now extended to the filter sidecar too (decided 2026-07-17, recorded directly in STD-009 §8.4:
  separate physical cores, no shared L1/L2)** — still unbuilt for either tenant; the DGC-based lifecycle
  answer (for the smart-proxy tenant only — STD-009's own filter-lifecycle question, §14, remains
  genuinely open and is a different question).
- **Status:** cross-reference notes added directly to STD-009 (2026-07-17, at §0's editorial-note block,
  §6.4, §8.4, §8.6) pointing back at this set — flagged for reconciliation, not yet reconciled into
  STD-009's body text. Treat the two bodies of work as **complementary, not competing**: STD-009 owns
  the annotation/taxonomy layer and the filter tenant; this set owns the concrete smart-proxy-tenant
  build-out. Where they use different names for the same thing (this set's "interfaces the client
  doesn't locally know about are silently dropped," STD-009's "interface stripping") — **use STD-009's
  terminology going forward**, since it's the earlier, more careful, formally-defined source (§2).

### 2.3 `SOW-SubProcessDynamicPolicy.md` — cross-process dynamic permission grants

- Closes the gap `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 6 found: JGDMS's local
  `DynamicPolicy`/`DynamicPolicyProvider`/`DigestGrant`/`LeasedPermissionGrant` machinery is mature but
  entirely single-JVM; nothing today computes a permission set from a SCAP verdict at runtime, or
  delivers a grant into an already-running, separately-spawned child process.
- Two-part build: (1) relocate the real, working `RemotePolicyProvider` implementation out of the
  deprecating `org.apache.river.api.security` shared namespace into `au.zeus`, replacing a dead stub
  already sitting there; (2) build `SubProcessDynamicPolicy` itself — decided (§2 of that doc) to be an
  **additional interface on the client's own per-proxy local delegate stub**, not a second wire protocol,
  which also resolves the "which hosted object does this grant target" ambiguity by construction (the
  channel *is* the target identity). Carries an explicit, load-bearing caveat: the subprocess-side
  dispatch target for policy-management calls must be a separate, trusted object, never the hosted smart
  proxy itself — and (added this session) this interface must only ever be bundled onto the
  orchestrating/owning party's stub, never onto a downstream ServiceAPI consumer's independently-built
  stub, and never derived from data carried on the wire.
- Its T2 (verdict → permission-set function) and T3 (the interface + subprocess-side handler) are
  flagged as the two tasks to guard hardest — genuine security-policy decisions and a novel
  authority-granting mechanism respectively, in the same risk class as prior adversarially-probed BAE
  work.
- **Status:** DRAFT task breakdown, no implementation started. T4 has a real external dependency on
  §12 point 3's own (still-unscoped) subprocess-spawning wiring task.
- **Scope generalized 2026-07-17:** researching how STD-009's server-side **filter** sidecar (a different
  tenant, §8) gets permission to load/run turned up the same gap this SOW closes, with `ProxyPreparer`/
  `BasicProxyPreparer.grant()` ruled out as a fit (it only ever mutates the *calling* JVM's own policy).
  This SOW's remit is now understood to cover both tenant types, not client-side-only — see the note at
  the top of `SOW-SubProcessDynamicPolicy.md` for what's reconciled and what isn't (the transport shape
  in its §2 assumes a client *holding a proxy*, which doesn't obviously fit a service holding its own
  spawned filter sidecar — flagged there as unresolved, not assumed away).

### 2.4 `SOW-BAE-Timing-Sidechannel-Denial.md` — rescoped timing defense

- Originally scoped as a standalone response to the pron98 r/java debate point (categorical
  bytecode-level denial of `System.nanoTime`-class calls by mobile code). **Rescoped twice** as the UDS
  isolation decision developed: §1a notes process separation is no longer structurally unavailable;
  §1b narrows the residual claim once isolation becomes mandatory — cross-principal timing attacks are
  now closed by construction (see §1 above), so this SOW's tasks (T1–T6) now primarily defend the
  *isolated process's own hosting/platform code* from a co-resident downloaded proxy, not arbitrary
  co-resident principals from each other. T7 (public/internal framing) is explicitly told to make the
  **stronger** primary claim (closed by construction) without letting the **narrower** secondary claim
  (T1–T6 defend hosting code) get overstated by association.
- **Status:** DRAFT task breakdown, no implementation started; several tasks' effort/priority were
  revised downward (T4 HIGH→MEDIUM, T9 MEDIUM→LOW) or reframed (T3/T5) once the rescoping landed — read
  §1b before treating the original task table at face value.

---

## 3. Cross-cutting decisions that show up in more than one document

Quick pointers, so a specific decision doesn't have to be re-found by re-reading everything:

| Decision | Lives in | Also referenced by |
|---|---|---|
| Per-remote-SPIFFE-principal pooling grain (not per-object, not per-consumer) | UDS SOW §12 point 1 | SubProcessDynamicPolicy SOW §2 (grant-target identity argument); BAE SOW §1b (residual-harmlessness argument) |
| Zero-bytecode-in-client-process property | UDS SOW §12 point 3 | BAE SOW §1/§1b (the primary closed-by-construction claim); this overview §1 |
| `ProxySerializer` new interface-name field (how the client learns what to build its stub from without unmarshalling) | UDS SOW §12 point 3(ii) | SubProcessDynamicPolicy SOW §2 (the exclusion caveat for the privileged interface applies to whatever set this field ultimately produces) |
| Per-consumer-JVM stub / third-party ServiceAPI consumer pattern | UDS SOW §12 point 3(iii) | SubProcessDynamicPolicy SOW §2 (why that interface must not be bundled onto a downstream consumer's stub) |
| DGC-driven subprocess lifecycle | UDS SOW §12 point 7 | — (not yet referenced elsewhere; note here so it doesn't get lost) |
| `org.apache.river.api.security` is a deprecating shared package — nothing new goes there | UDS SOW §12 point 6 | SubProcessDynamicPolicy SOW §1/T1 (the concrete relocation this produced) — **standing rule for all future work in this area, not scoped to one class** |
| CPU/core-affinity needed for Prime+Probe closure, on top of process separation — applies to **both** sidecar tenant types (decided for filters 2026-07-17: separate physical cores, no shared L1/L2) | UDS SOW §8, §12 point 2 (smart proxy); STD-009 §8.4 (filter, decided 2026-07-17) | BAE SOW §1b (why SM/POLP is still a complementary layer, not made redundant) |
| "Interface stripping" / "boomerang" (STD-009's names for silently-dropped-unresolvable-interfaces and relay-the-retained-wire-form-not-the-live-proxy) | STD-009 §6.4 (originating definition, 2026-07-03) | UDS SOW §12 point 3(ii)/(iii) (independently re-derived, now reconciled — use STD-009's terminology) |
| Two sidecar tenant types have deliberately different lifecycles (filter: per-query ephemeral; smart proxy: DGC/lease-pooled) | STD-009 §8.4 (filter) + UDS SOW §12 point 7 (smart proxy) | STD-009 §8.6 ("the tenants differ" — anticipated the split before either lifecycle was designed) |

---

## 4. Known gaps — flagged, not yet scoped as their own task or SOW

These surfaced during the investigation behind the four documents above but don't yet have a task table
of their own. Listed here so they don't quietly disappear between documents:

1. **OSGi `ProxyBundleProvider` verdict-gate bypass** (UDS SOW §12 point 5) — a second, live,
   OSGi-wired `ProxyCodebaseSpi` implementation with zero references to `VerdictRegistry`/
   `checkVerdictForJar`/any download-gating mechanism. Dormant in a non-OSGi deployment today, but a
   live module in the build. Flagged as needing remediation at least as high a priority as the BAE SOW's
   T1. **No SOW owns this yet.**
2. **§12 point 3's own wiring task breakdown** — referenced repeatedly across all four documents as a
   dependency (`SubProcessDynamicPolicy` SOW's T4, the BAE SOW's T7 gate, this overview's §1 closing
   caveat) but never itself broken into a task table with agent/effort assignments the way the other
   four documents are. This is the actual "make routing real" work — insertion at
   `PreferredProxyCodebaseProvider.resolve()`, subprocess spawning/tracking, the `ProxySerializer` field
   addition from §12 point 3(ii). **Candidate next SOW to write, and probably the most concretely
   actionable one of everything listed here.**
3. **QA implications** (UDS SOW §12 point 8) — Peter's initial assessment is "mostly invisible," but
   explicitly flagged as needing dedicated investigation once points 3/6/7 have something concrete to
   investigate against, not assumed true by design alone.
4. **Interface-distribution mechanism source** — §12 point 3(ii)/(iii) establishes *how* a consuming
   process determines which interfaces it already has locally, but not *how those interface jars reach
   the consuming process's classpath in the first place* (trusted shared API jar vs. some other
   distribution path) — noted as still open in earlier working notes, not yet picked up in any of the
   four documents above.
5. **Wire-protocol handoff mechanics** — the exact bytes/framing of "client forwards a raw
   `MarshalledInstance` to the subprocess over UDS, subprocess replies with enough to build a stub" is
   described at the mechanism level (§12 point 3(i)) but not specified at the wire-protocol level.
   Likely belongs inside gap 2's future SOW rather than as its own document.
6. **STD-009 §6.4 "interface stripping" is not built** — the runtime resolves a dynamic proxy's
   interfaces all-or-nothing today (confirmed twice, independently, from STD-009's original 2026-07-03
   design pass and this session's fresh code investigation). §12 point 3(ii)'s `ProxySerializer` field
   design is a candidate concrete realization, but building it is unstarted, unscoped work — likely
   folds into gap 2's future SOW rather than becoming its own document.
7. **Filter-sidecar CPU-affinity — RESOLVED (Peter, 2026-07-17), not yet built.** Filter workers for
   different, mutually-untrusting clients/queries MUST run on separate physical CPU cores sharing no
   L1/L2 cache — the same requirement as the smart-proxy sidecar (UDS SOW §8/§12 point 2), not a
   separate mechanism. Decision recorded directly in STD-009 §8.4; no implementation exists yet for
   either sidecar type. Downgraded from "open question" to "known requirement, unbuilt" — leave it in
   this list only because nothing schedules the actual pinning work yet.
8. **STD-009 §14's own open items** (filter sidecar lifecycle specifics; the declarative
   query-parameter type; where the retained `MarshalledInstance` for boomerang actually lives) —
   unrelated to and unresolved by anything in this session's four documents; still open on STD-009's own
   terms.
9. **`SubProcessDynamicPolicy`'s scope generalization to the filter tenant is unreconciled** — researching
   the filter-permission question (STD-009 §8.3's 2026-07-17 note) found the same cross-process-policy
   gap on the server side, but the transport shape designed for the client-side smart-proxy case (§2 of
   that SOW: "an interface on the client's local delegate stub") doesn't obviously map onto a service
   holding its own spawned filter sidecar rather than a downloaded proxy. Flagged, not designed.
10. **Cross-language (non-JVM host) sidecar spawning** (STD-009 §8.5, flagged 2026-07-17) — a Rust (or
    other non-JVM) data-service host spawning a JVM filter sidecar raises real, unscoped questions: how
    the host's own JVM-launch invocation stays trusted/unspoofable, whether an existing Rust JERI
    implementation's DGC support is faithful enough for the existing lifecycle model, DER-encodability of
    `SubProcessDynamicPolicy`'s grant payload from a non-JVM caller, and — the sharpest one — whether
    per-query JVM spawn cost undermines the entire bandwidth rationale of predicate pushdown on a
    resource-constrained host, making STD-009's own still-open filter-lifecycle question (§14) more
    load-bearing here than in the pure-JVM case. Nothing in this list or STD-009 designs this yet.
11. **MAJOR, largest re-scope in this list — filters are DECIDED (Peter, 2026-07-17) not to be standard
    JVM bytecode at all**, and the same restricted, host-language-agnostic mechanism is meant to serve
    both Rust and Java services. Recorded at length in STD-009 §8's new decision note (right after the
    §8 heading, before §8.1). This plausibly **removes gap 10's whole "spawn a JVM sidecar from Rust"
    problem** for the filter case (a Rust host could run a small native evaluator for the same restricted
    format directly) and **may reclassify filters out of the `@RemoteFunction`/`FUNCTION` escalation
    entirely**, into RULE-B1's ordinary-parameter category (§9.1) — a data-encoded expression tree isn't
    "behaviour that has to cross the wire" the way a compiled class is. Large parts of STD-009 §8
    (RULE-F1/F3's bytecode-specific mechanism, §5's Filter row, §7's FUNCTION column, §11, §12) are now
    flagged in STD-009 itself as needing re-derivation, not silently rewritten — the concrete shape of the
    new format, BAE's replacement verification role, and whether a sidecar/process boundary is still
    wanted are all explicitly left open. **The largest unscoped design surface in this entire document
    set as of 2026-07-17.**

---

## 5. Suggested reading order

1. This document (orientation).
2. `SOW-Unix-Domain-Socket-JERI-Transport.md`, in full — it's the foundation everything else assumes.
2a. `JGDMS-STD-009-Service-RemoteFunction-Annotation-Model-v0.1-DRAFT.md` §6.4 and §8 (independently
    convergent prior art — read these two sections at minimum before treating §12 point 3(ii)'s
    interface-selection design as novel; it isn't, STD-009 got there first and named it better).
3. `SOW-SubProcessDynamicPolicy.md` and `SOW-BAE-Timing-Sidechannel-Denial.md`, either order — both
   are downstream of the UDS SOW and largely independent of each other.
4. `SOW-UDS-JERI-Increment-2-Peer-Authentication.md` — self-contained, can be read any time, just keep
   in mind it predates and doesn't reference the §12 decisions.
5. §4 above, when deciding what to scope next.

---

*End of index. No production code was written or modified in producing this document.*
