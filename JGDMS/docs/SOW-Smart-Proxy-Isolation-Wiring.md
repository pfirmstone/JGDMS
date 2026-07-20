# Scope of Work — Smart-proxy isolation wiring: making the mandatory UDS-isolated routing real

- **Drafted:** 2026-07-18.
- **Status (updated 2026-07-20): LANDED, T1-T5.** T1 (routing insertion point), T2 (subprocess spawn/
  pool/track/teardown, including the `SubProcessAdministrable`/`PolicyAdmin` authentication scaffold),
  and T3 (`ProxySerializer` interface-name field) landed 2026-07-19. T4 (wire-protocol handoff —
  `SubProcessWireHandoff`/`WireFraming`/`WireHandoffCodec`/`SubProcessReconstructionServer`) landed
  2026-07-20 after two board-review rounds that found and fixed real defects — see
  `SOW-T4-Wire-Handoff-Protocol.md` §10 for the full account (an uncaught client-side decode-failure
  path, an unbounded decode-recursion depth, and — found while re-verifying the depth-guard fix — a
  separate, more severe silent `Externalizable` field-loss bug in shared `jgdms-platform` codec classes,
  root-caused and fixed, not merely worked around). T5 (interface-distribution mechanism) was already
  resolved as a design decision at drafting time (§3 *T5 resolution*, below) — no code required, and
  nothing about that resolution changed this session. **T6 (this document's own closeout row) is what
  this edit is executing** — see that row for specifics. This status line summarizes; the task table in
  §3 and `SOW-T4-Wire-Handoff-Protocol.md` are authoritative for T4's detail.
- **Origin:** `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 3 (the "actual wiring is
  unimplemented" decision), and `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` §4 gap 2 (which
  names this as "the actual 'make routing real' work" and "the most concretely actionable" candidate
  next SOW). This document is that SOW. It **also absorbs and scopes** overview §4 gaps 4
  (interface-distribution mechanism), 5 (wire-protocol handoff mechanics), and 6 (STD-009 §6.4
  "interface stripping" not built) — all three explicitly flagged in the overview as likely to "fold
  into gap 2's future SOW rather than becoming its own document." They are folded in here (T5, T4, T3
  respectively).
- **Companions:**
  - `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 — **read that first, this SOW assumes it in full.**
    §12 point 1 (per-principal pooling grain), point 3 (the client/subprocess split, the `resolve()`
    insertion point, the `ProxySerializer` interface field), point 7 (DGC-driven lifecycle) are the
    source decisions this task breakdown operationalizes.
  - `SOW-SubProcessDynamicPolicy.md` — its **T4** (caller-side grant push) has an explicit external
    dependency on **this** SOW's subprocess-spawning/tracking task producing a registry/handle it can
    target a grant against; §T2 below states the shape of that handle so T4 is not blocked on guessing.
    Its **§2** decides the policy-grant interface is bundled onto the *orchestrating* party's local
    delegate stub only — never a downstream ServiceAPI consumer's independently-built stub; T2 below
    turns that *convention about who builds the stub* into an *enforced subprocess-side mechanism*.
  - `SOW-BAE-Timing-Sidechannel-Denial.md` — its **T7** (public/internal framing) is gated on this
    SOW's wiring landing: until the routing here is the default/enforced path, "mandatory isolation"
    is a decision on record, not a deployed guarantee. This SOW does not scope BAE's own tasks; the
    dependency direction is BAE-T7 → this-SOW-wiring, not the reverse.
  - `JGDMS-STD-009-Service-RemoteFunction-Annotation-Model-v0.1-DRAFT.md` §6.4 (**interface stripping**
    and **boomerang**) and §8.6 (the sidecar as a mobile-code container). STD-009 is the earlier,
    formally-defined source for these two concepts (overview §2.2a / §3 cross-reference table:
    **use STD-009's terminology going forward**). T3 below is a candidate concrete realization of
    STD-009 §6.4's "interface stripping" — it is named as such there, not re-derived under a new name.
- **No production code was written or modified in producing this document.** It is a task breakdown
  only; all task-level status below is DRAFT / not started.

---

## 1. What this closes, precisely

`SOW-Unix-Domain-Socket-JERI-Transport.md` §12 records the mandatory-isolation *decision* in full
detail, but §12 point 3 itself, and overview §4 gap 2, both note the same thing: the decision is on
record and the mechanics are described at the design level, but **nothing is broken into a task table
with agent/effort/risk assignments the way the other four documents in this set are.** This is the
"make routing real" work:

1. **Where the routing branch is inserted** — `PreferredProxyCodebaseProvider.resolve()`, the single
   choke point every downloaded/mobile proxy's `MarshalledInstance` already passes through (T1).
2. **How a per-principal isolated subprocess is spawned, pooled, tracked, and torn down** — including
   *who is authorized to push a policy grant into it* and *when it is safe to tear it down* (T2).
3. **How the client learns which interfaces to build its thin stub from without unmarshalling** — the
   new `ProxySerializer` interface-name field, i.e. STD-009 §6.4 "interface stripping" made concrete
   (T3).
4. **The byte/framing-level handoff** — client forwards the raw `MarshalledInstance`, subprocess
   reconstructs it (behind the same gates the in-process path uses today) and replies with enough to
   build the stub (T4).
5. **How the interface jars reach a consuming process's classpath in the first place** — **now
   substantially resolved** (§3 *T5 resolution*): for every *wired/known* consumer the answer already
   exists and needs no new mechanism — the `*-api.jar` is an ordinary Maven artifact each consumer
   declares as a plain build `<dependency>` (non-download-capable, resolved before process start); it
   stays genuinely open only for a *generic* runtime-discovered consumer, for which the recommendation
   is **deploy-time provisioning, not a client-side runtime fetch**, with one flagged residual
   (version/API skew) (T5).

It does **not** re-decide anything §12 already decided (per-principal pooling grain, CPU-affinity
requirement, unconditional-for-all-smart-proxies, DGC-based lifecycle). It turns those decisions into
buildable, reviewable tasks, and — per the six board-review findings baked in below (S1-S4 at
drafting; **S5-S6 from the 2026-07-19 adversarial review of T1's landed implementation**) — names the
enforcing *mechanism* for each security-relevant *outcome* rather than restating the outcome and
hoping review catches the gap later (the "outcome-vs-mechanism" defect class, Board Guidance G2).

---

## 2. Security lens applied while drafting (not deferred to review)

Six findings from `JGDMS-Board-Reviewer-Guidance.md` (Part I G1-G13, §2.2 Adversarial Security, §2.3
JERI Transport) are baked into specific tasks below as explicit design requirements or flagged open
questions, not left for a later review pass. Cross-referenced here so none goes missing:

- **(S1) Policy-grant-push authority is a mechanism gap, not a convention (G2/G4, confused-deputy).**
  `SubProcessDynamicPolicy` §2's "only bundle the grant interface onto the orchestrating party's stub"
  is a convention about who *builds* the stub — a malicious/buggy client can add that interface to any
  stub it builds itself, and **declaration-is-the-signal (G4) means the subprocess must never trust a
  runtime instance/interface-set to prove authority.** → **enforced in T2** as an acceptance criterion,
  via either or both (defense-in-depth) of: **(i) preferred — a new, dedicated interface
  `SubProcessAdministrable`** (in `au.net.zeus.jgdms.*` per UDS §12 point 6, never
  `org.apache.river.api.security`), with its own accessor (e.g. `getSubProcessPolicyAdmin()`) returning
  a separate `PolicyAdmin` proxy to a separately-exported subprocess-side *management* object, carrying
  its own stricter `MethodConstraints` (client authentication as the orchestrating admin principal,
  Integrity), so admin authority is proven by *authentication on the admin proxy's own endpoint*, not by
  the business stub's interface set — this also structurally satisfies `SubProcessDynamicPolicy` §2's
  caveat that policy calls dispatch to a separate trusted object, never the hosted proxy, and reuses the
  existing `RemotePolicyService` admin-principal authz shape (UDS §12 point 6) rather than inventing one.
  **It is the *same pattern* as `net.jini.admin.Administrable` — an accessor returning a
  separately-authenticated admin proxy — deliberately a parallel interface rather than a reuse of it.**
  The hosted smart proxy / backend service may already implement `Administrable`, whose `getAdmin()`
  returns the *remote service's own* admin (`JoinAdmin`/`DestroyAdmin`/`StorageLocationAdmin`, reached
  through to the backend) — a distinct authority from administering the *local isolated subprocess's*
  `DynamicPolicy` ceiling. Reusing `getAdmin()` would collide on the accessor and, worse, **clobber a
  surface an administrator legitimately still needs**: an operator may genuinely need to call the
  backend service's own `Administrable.getAdmin()`, so subprocess-policy admin must not shadow it. A
  parallel `SubProcessAdministrable` lets both admin surfaces coexist — backend-service admin via
  `Administrable.getAdmin()` (forwarded through to the backend), local-subprocess-policy admin via
  `SubProcessAdministrable.getSubProcessPolicyAdmin()` — neither clobbering the other, two orthogonal
  authorities kept distinct rather than conflated (a confused-deputy shape). **(ii)** binding the privileged dispatch to a specific spawn-time UDS
  connection/handle. **Residual hazard — the dedicated interface is hygiene, not the enforcement
  boundary (G4):** a malicious client can still build a stub declaring `SubProcessAdministrable`, and
  the subprocess sees only a UDS call, never the client's declared interface set — so `getSubProcess
  PolicyAdmin()` and every `PolicyAdmin` operation must **fail closed** to a caller that cannot
  authenticate as the admin principal (return nothing usable). Construction-time interface exclusion
  (§2(iii) of `SubProcessDynamicPolicy`) reduces reach; runtime admin authentication enforces — they are
  complementary layers, not alternatives. (This refinement — Peter, 2026-07-18 — feeds back into
  `SubProcessDynamicPolicy` §2, whose current "additional interface on the same stub" framing this
  supersedes as the preferred shape; that doc is not rewritten here, only cross-noted.)
- **(S2) Decode-surface / ungated-reconstruction door, now concentrated in one place (§2.2's
  reflex).** The isolated subprocess is now THE place a smart proxy's bytecode and `MarshalledInstance`
  get reconstructed — exactly the "wire-names-the-class" hazard where capability escalation hides. →
  **enforced in T4** (all existing gates — SCAP/BAE verdict, `DeSerializationPermission("ATOMIC")`,
  endpoint-assigned `ResolutionContext` — still run *inside* the subprocess before reconstruction) and
  **T3** (decode-size/depth bounds on the new interface-name field, since a few bytes can decode into a
  large or deeply-nested structure — the collection-codec / nested-`Any` war-story class).
- **(S3) Three-independent-axes check for the client↔subprocess UDS channel (§2.2 role-reversal
  hunt).** → **stated in T4** as a design requirement, not an assumption: byte-flow direction, request-
  origination direction (client-initiated only — the subprocess never originates a request back to the
  client), and whose identity authorizes what. If `SubProcessDynamicPolicy` shares this channel, its
  authorization is **separately re-derived**, not inherited from the business-call direction's trust.
- **(S4) DGC-ack semantics for teardown, not connection-close (§2.3 FIN-vs-acknowledgment war
  story).** → **acceptance criterion in T2**: liveness/teardown keys off the same "dispatcher actually
  processed" semantics the DGC acknowledgment contract already uses (ack fires after processing, not on
  stream FIN / connection close). Equating connection-close with "safe to tear down" is the same class
  of bug as the mux FIN-vs-ack war story and is explicitly disallowed.
- **(S5) The canonical pooling-key derivation from `serverPrincipals` is undefined and must be
  specified (G1 canonical-form, G4 declaration-is-the-signal).** T1 hands the isolation router the full
  `Principal[]` extracted from the TLS `ServerSubject`. That array is a `HashSet<Principal>`
  (`SslConnection.populateContext`, `SslConnection.java:762-789`) = one `X500Principal` ∪ zero-or-more
  SPIFFE principals (one per `spiffe://` URI SAN, `SpiffePrincipal.fromCertificate`), materialized to an
  array in **hash-iteration order**. There is currently **no stated contract** for which element — or
  what derived value — is *the* canonical key subprocess pooling keys on; keying on element `[0]`,
  `Principal.hashCode()`/identity, or raw `Set` order is non-deterministic across runs and across
  certificate content. The whole isolation property depends on pooling by the *right* key (under-
  distinguish → two principals share one subprocess = isolation breach; over-distinguish / non-
  deterministic → redundant subprocesses + unstable pooling). → **specified as a T2 acceptance criterion
  in §4's *Canonical pooling-key derivation* subsection**, and flagged as a design decision needing
  **explicit sign-off**, not merely code review (Finding, 2026-07-19 adversarial review of T1).
- **(S6) The pre-existing self-unmarshal fast path bypasses the isolation branch — a guard is owed
  before T2 (G6 fail-closed, G10 boundary-vs-interior fence).** T1 inserts the isolation branch *after*
  two "already local" fast paths in `resolve()` (`PreferredProxyCodebaseProvider.java:1709-1720`): the
  `SERVICES_EXP` lookup (safe — keyed on this process's own exports, cannot be attacker-echoed across a
  principal boundary) and the self-unmarshal special case
  `if (loader==null && path!=null && path.equals(loaderPath)) loader = parent;`, where `path` is the
  **wire-controlled** `bootstrapProxy.getClassAnnotation()` and
  `loaderPath = getLoaderAnnotation(parent,false,null)`. On a match, `loader = parent` is used and the
  isolation router, verdict gate, and principal binding are all skipped. **Analysis (2026-07-19): not
  cross-principal reachable in the *current* codebase** — `getLoaderAnnotation` returns `null` for the
  ordinary top-level app/system `parent` (`isLocalLoader`, `PreferredClassProvider.java:889-890`), so the
  match is false at the top level; the only way `parent` is a codebase-annotated (`ClassAnnotation`)
  loader is **nested deserialization** (`serviceProxy.get(loader, …)` at `:2040` passes the freshly-
  created `PreferredClassLoader` as the nested stream's default loader → an embedded proxy re-enters
  `resolve()` with `parent` = that loader), and there the nested call is handed the **same** `context` /
  `ServerSubject` → the **same** `serverPrincipals`, so the collapse stays within one principal (and
  `parent` resolves only classes it already holds → `ClassNotFoundException` for novel bytecode). **But
  T2 makes it live:** once per-principal subprocess loaders exist, one can be cached (`CACHE` /
  `SERVICES_EXP`) and later presented as `parent` to a `resolve()` whose current `serverPrincipals` are a
  *different* principal — then `path.equals(loaderPath)` collapses to the earlier principal's loader
  while a different identity is authenticated: a silent isolation + principal-binding bypass. →
  **T1 follow-up (§3), accepted-risk-today + mandatory-guard-before-T2** — closure sketched there.

---

## 3. Execution plan — task breakdown (agent type · effort · risk)

Effort follows the project convention (security → `xhigh`, class/wire-resolution → `max`, mechanical →
`medium`, test-debug → `xhigh`); orchestration follows *fresh implement → review gate*, with a
**parallel adversarial board** for high-blast-radius/security work. **T1, T2, and T4 are in the same
risk class as this session's adversarially-probed BAE work and `SubProcessDynamicPolicy`'s T2/T3** — a
novel choke-point control branch, a novel authority-binding + lifecycle mechanism, and the single
concentrated object-reconstruction door respectively. Hold them to that standard: build-and-run
adversarial probes against the actual implementation, do not let "the design reads sound" substitute.

### Summary

| Task | Deliverable | Implement (agent · effort) | Review | Depends |
|------|-------------|-----------------------------|--------|---------|
| **T1** · Routing insertion point | **LANDED 2026-07-19** (`net.jini.loader.pref.PreferredProxyCodebaseProvider`, `jgdms-pref-class-loader`; board-reviewed, 2 rounds, real legacy-path-bypass and principal-keying findings closed — see the S5/S6 findings in §2 above, both raised by this task's own board review). Insert the isolation branch at `net.jini.loader.pref.PreferredProxyCodebaseProvider.resolve(CodebaseAccessor, MarshalledInstance, ClassLoader, ClassLoader, Collection)` (`jgdms-pref-class-loader`). Extract the routing key from the already-available `serverPrincipals`/`serverSubjectFromContext` (derived from the TLS-layer `ServerSubject` in `resolve()`'s `context`, `PreferredProxyCodebaseProvider.java:1692-1704`) — the remote SPIFFE principal, **not** the existing ClassLoader-cache `Key` class (`:2139-2168`, which keys on `InvocationHandler`/`ObjectEndpoint` = object/export identity, the wrong grain per UDS §12 point 1's correction). On the isolation path, `resolve()` does **not** classload/deserialize in-process: it hands off to T2/T4 and returns the thin `Proxy` stub. Fail-closed: if the principal cannot be determined, refuse — do not fall through to the legacy in-process path (G6 — and check the refusal has no fail-open side effect, e.g. a partially-constructed loader left cached). **T1 follow-up (S6):** the two pre-existing "already local" fast paths (`SERVICES_EXP` and the self-unmarshal `path.equals(loaderPath)` collapse, `:1709-1720`) run *before* this isolation branch; the self-unmarshal one hands `loader = parent` on a wire-controlled annotation match. Not cross-principal reachable today, but a principal-consistency guard is owed **with/before T2** (which introduces the per-principal loaders that make it reachable) — see §3 *T1 follow-up — self-unmarshal fast-path principal guard (S6)*. | general-purpose · **XHIGH** (security-critical control branch at the codebase-download choke point) | **parallel board** (2–3 adversarial) — probe: can any input reach the legacy in-process classload path once isolation is meant to be unconditional; can the principal-keying be confused into pooling two distinct principals together | none — can start immediately; T4 fills in the handoff body it calls |
| **T2** · Subprocess spawn / pool / track / teardown | **LANDED 2026-07-19** (`SubProcessPool`, `SubProcessHandle`, `SubProcessAdministrable`, `SubProcessPolicyAdmin`/`GuardedPolicyAdminHandler`, `AdminPrincipalAuthenticator`, `SubProcessAdminRegistry`; board-reviewed, adversarially probed — a real TOCTOU bug in `SubProcessPool` was caught and fixed, per this session's own precedent-setting note in `SOW-Smart-Proxy-Isolation-Remaining-Work.md`). **Caveat, not a defect in this task's own scope:** the `backing PolicyAdmin` this scaffold dispatches to was, as landed here, a test double only — the real grant-application backend behind it is `SubProcessDynamicPolicy` T3(c), which landed later (2026-07-20, `SOW-Smart-Proxy-Isolation-Remaining-Work.md` T1). **Real OS-process spawning is separately out of scope and remains unbuilt**: `SubProcessLauncher`'s production `fork`/`exec` implementation is `UnsupportedSubProcessLauncher` (confirmed by multiple board reviewers across this and later sessions) — this task built the pool/registry/authentication machinery around wherever a real launcher will eventually plug in, not the launcher itself. Per-**remote-SPIFFE-principal** subprocess pooling (UDS §12 point 1) — one process per distinct exporting principal, shared across that principal's proxies, never per-object/per-consumer. Spawn on first need, reuse for subsequent proxies of the same principal. Internally the subprocess retains the existing per-proxy-object `Key`-based `ClassLoader` separation (UDS §12 point 1's "intra-process isolation" use), now scoped *within* one principal's process. Must expose a **subprocess registry/handle** (see §4) that `SubProcessDynamicPolicy` T4 can target a grant against. **Acceptance criteria (S1, S4, S5 — not optional):** (S1) admin authority is proven by an **enforced mechanism, not the business stub's interface set** (G4) — preferred realization: a new, dedicated `SubProcessAdministrable` (same accessor-returns-admin-proxy pattern as `net.jini.admin.Administrable`, but a parallel interface so it does not collide with or clobber the backend service's own `Administrable.getAdmin()`, which an operator still legitimately needs), whose `getSubProcessPolicyAdmin()` returns a separate `PolicyAdmin` proxy to a separately-exported trusted management object, gated by stricter `MethodConstraints` (authenticate as the orchestrating admin principal); the accessor and every admin op **fail closed** to any caller not so authenticated, so a mere business-stub holder gets nothing usable; alternatively/additionally bind the privileged dispatch to a spawn-time UDS connection/handle. This satisfies `SubProcessDynamicPolicy` §2's "dispatch to a separate trusted object, never the hosted proxy" caveat by construction. Never authorize by "the incoming call implements the policy-shaped interface" (declaration-is-the-signal). **Third layer — fail closed at reconstruction against escalation attempts:** if the *hosted* smart proxy's own resolved interface closure includes `SubProcessAdministrable`/`PolicyAdmin`, **reject the smart proxy outright** (refuse to host, audit) — no legitimate business proxy declares the subprocess's own management interface, so one that does is a TOCTOU / dispatch-confusion escalation attempt. **Rejection is the only fail-closed option here, not a preference over stripping:** unlike T3's client-side delegate stub (a dynamically-constructed `java.lang.reflect.Proxy` that legitimately exposes a *chosen subset* of interfaces), the hosted smart proxy is an ordinary concrete class — its implemented-interfaces closure is fixed at compile time by whoever wrote it, and cannot be reduced at runtime. There is no "strip the offending interface and host the rest" option for this object; hosting it at all means hosting exactly the class it is, admin-shaped interface included. So the only fail-closed move is refuse-to-host, at load time (before the proxy is ever exported or dispatched), which collapses the check-vs-use window entirely. Check on **resolved type identity** against the subprocess's own trusted interface `Class` (an `isAssignableFrom`/identity test — never a wire-name string match, which is both over- and under-inclusive), at T4's reconstruction boundary where the interface closure is already computed. This is a construction-time layer on top of (i) dispatch-to-a-separate-object and (ii) runtime admin authentication, not a substitute for either. (S4) teardown/liveness keys off DGC **acknowledgment-processed** semantics (dispatcher actually processed a clean-call), **never** stream FIN / connection close (the mux FIN-vs-ack war story). Lifecycle reuses JERI's existing DGC dirty-set/clean-call/lease machinery (UDS §12 point 7; Birrell 1993 / SRC-RR-116, STD-008 §6) — whole-subprocess teardown gated on the union of its hosted objects' DGC state. **(S5) canonical pooling-key derivation is not optional:** reduce T1's `serverPrincipals` array to a single canonical pooling key by the rule in §4 (*Canonical pooling-key derivation*) — deterministic given the same certificate regardless of `HashSet` iteration order; **never** key on element `[0]`, `Principal.hashCode()`/identity, or raw `Set` order. This is a security-relevant scoping choice needing **explicit sign-off** (not just code review): the isolation property fails *silently* if the key under-distinguishes principals. | general-purpose · **XHIGH** (novel authority-binding + cross-process lifecycle mechanism; same risk class as BAE T2/T5, `SubProcessDynamicPolicy` T3) | **parallel board**, adversarial-probing mandate — build real probes: (a) can hosted untrusted proxy code reach the policy-management dispatch by presenting the right interface shape; (b) does a connection-close (not a processed clean-call) ever trigger teardown while a live reference remains; (c) can two distinct principals ever be pooled into one process — including whether the S5 pooling-key derivation under- or over-distinguishes (multi-SAN certificate, `HashSet`-order dependence) | T1 (calls it); informs `SubProcessDynamicPolicy` T4 (which consumes the handle) |
| **T3** · `ProxySerializer` interface-name field (interface stripping, concretely) | **LANDED 2026-07-19** (`org.apache.river.api.io.ProxySerializer`, board-reviewed). Add a new field to `ProxySerializer`'s wire form (`org.apache.river.api.io.ProxySerializer`, `serialForm()` `:61-66`; populate at either `create()` overload `:141-187` from the live sender-side proxy's `getInterfaces()` closure — citations corrected 2026-07-18 after verification, original draft was off by ~9 lines). **This is the concrete realization of STD-009 §6.4 "interface stripping"** (each reading process resolves the wire-carried names against its *own* local, non-downloading classpath and silently drops what it cannot resolve — an interface a process cannot resolve is one it cannot use). Carry the set as `String[]` canonical/binary **names, never `Class[]`** (UDS §12 point 3(ii): `Class[]` would force ordinary AtomicSerial resolution through the download-capable path this architecture removes). Eager field (unlike lazy `serviceProxy`), available to routing before any `serviceProxy` decision. **Acceptance criteria:** (S2 decode-bounds) the field carries a bounded count of names each of bounded length — an explicit cap on element count and per-name length, enforced during decode, since a few wire bytes must not decode into a large/deeply-nested structure (collection-codec / nested-`Any` war-story class); (security, UDS §12 point 3(ii) note) the field may only ever select which of the smart proxy's **own business interfaces** a stub exposes — it must **never** cause a privileged/administrative interface (`SubProcessDynamicPolicy`-shaped) to be bundled onto a stub because a matching name appeared; run the explicit adversarial check "can a crafted name list build a stub with more than its intended business interfaces." Empty/absent field = "no advance hint" fallback (T4 stub built after subprocess reports the observed set — one extra round-trip, still correct). **Wire-schema discipline:** this is a third field on an `@AtomicSerial` `serialForm()` — exactly what the live `japicmp` + serial-schema CI gate (`jgdms-api-compat-tooling`) exists to review; not a silent edit even though the class is package-private. | general-purpose · **MAX** (wire/class-resolution + attacker-influenced wire content) | **parallel board** — adversarial: crafted/hostile/oversized name lists, unresolvable names, name resembling a privileged interface | none for the field shape; integrates with T4 |
| **T4** · Wire-protocol handoff mechanics | **LANDED 2026-07-20** (`SubProcessWireHandoff`/`WireFraming`/`WireHandoffCodec`/`SubProcessReconstructionServer`, commits `967a59799` merged via `10ab8690f`; full byte/framing specification and adversarial findings now recorded in the dedicated `SOW-T4-Wire-Handoff-Protocol.md`, which supersedes this row as the authoritative "what is built" reference — this row's own text below is what was *planned*, largely realized as described, with two board-review rounds finding and fixing real defects beyond what was planned: an uncaught client-side decode-failure path and an unbounded decode-recursion depth (live `StackOverflowError`), fixed with a `catch(Throwable)` backstop and a new `DecodeDepthGuard`; and, found while re-verifying that fix, a genuinely separate and more severe silent `Externalizable` field-loss bug in `AtomicMarshalInputStream`/`ObjOutputStream` — two compounding root causes, fully fixed, not merely contained). Specify at the byte/framing level: (i) client forwards the **raw, still-marshalled** `MarshalledInstance` (`serviceProxy` bytes) over UDS to the principal's subprocess — the client never deserializes it, even partially (UDS §12 point 3(i)); (ii) the subprocess reconstructs it and replies with enough for the client to build its thin `Proxy` stub (the observed interface set + a live object reference to forward to). **Acceptance criteria:** (S2 reconstruction gates) the SCAP/BAE `VerdictRegistry` verdict gate (`checkVerdictForJar`, fail-closed), the `DeSerializationPermission("ATOMIC")` gate, and the endpoint-assigned `ResolutionContext` all still run **inside the subprocess before reconstruction** — moving reconstruction across a process boundary must not move it *outside* any gate the in-process path enforces today (G13: the reconstruction door must not become ungated by relocation); at this same boundary, enforce T2/S1's fail-closed rejection — refuse to host any smart proxy whose *resolved* interface closure declares the subprocess's own `SubProcessAdministrable`/`PolicyAdmin` management interface (a TOCTOU / dispatch-confusion escalation attempt), checked on resolved type identity, not a wire-name match; (S3 three-axes, stated as requirement not assumption) — **byte flow:** request bytes client→subprocess, reply bytes subprocess→client; **request origination:** client-initiated **only** — the subprocess never originates a request back to the client (classic JERI caller = connection initiator; any callback need is a red flag to re-derive, not assume); **authorization:** the business-call channel authorizes business calls; **if `SubProcessDynamicPolicy` rides the same UDS connection, its authorization is separately re-derived per T2's S1 binding, never inherited from the business direction's trust.** | general-purpose · **XHIGH** (the single concentrated object-reconstruction door; same risk class as BAE work) | **parallel board** — adversarial: a `MarshalledInstance` crafted to reconstruct outside a gate; a subprocess-originated call back toward the client; oversized/deeply-nested marshalled payload | T1 (invokes handoff), T2 (subprocess must exist), T3 (interface hint feeds stub build) |
| **T5** · Interface-distribution mechanism (**resolved — recommendation + one flagged residual**; see §3 *T5 resolution*) | **Survey finding (the strongest candidate confirmed real): JGDMS already delivers `*-api.jar` as an ordinary Maven artifact each consumer declares as a plain build `<dependency>`** — verified against `hello-world-client` → `hello-world-api` (`au.net.zeus.jgdms.hello:hello-world-api`, `packaging jar`, same dependency block and same trust class as `jgdms-jeri`/`jgdms-platform`), and codified in STD-009 §6.5's module table (`-api` = interface module, an ordinary artifact; only `-dl` is the downloadable-behaviour jar). This is **candidate (a) — trusted shared API-jar distribution, resolved onto the classpath at build/deploy time, non-download-capable at runtime.** **For every wired/known consumer T5 therefore needs no new mechanism — it is already how things work.** It stays genuinely open only for a *generic* consumer that discovers a service at runtime holding no prior api jar (STD-009 §8.6 shape 2's "client provisions only the interface codebase", delivery path unspecified). **Recommendation for that case: candidate (b) — deploy-time provisioning** over the same Maven-coordinate convention, staged by the orchestration / `service-starter` tooling into the client's classpath location *before* the client process starts. **Recommend AGAINST candidate (c) — a narrow runtime interface-codebase fetch:** that is exactly the STD-009 §6.5 "served as the codebase iff shape 2 applies" download surface this whole architecture exists to concentrate in the sidecar; even an *inert, interface-only, never-`preferred`* download re-opens a class-resolution/decode path in the client heap (the surface T1/T4 remove), for marginal convenience. **Neither (a) nor (b) reintroduces any download-capable surface into the client process** — stated explicitly per this row's own mandate. **Residual, genuinely unresolved (does not fully close):** (i) **version/API skew** — the service's *actual* compiled interface vs the consumer's locally-provisioned `-api.jar` revision; `Class.forName(name, …)` resolves the *name* but binds to whatever method-set the local jar holds, and T3's name-based stripping cannot detect a same-name/different-shape mismatch; the japicmp + serial-schema gate (`jgdms-api-compat-tooling`) binds *JGDMS's own* artifacts but nothing binds a third-party consumer's provisioned api jar to the service it talks to. (ii) **coordinate discovery** — nothing on the lookup wire (UIDescriptor/service type) carries the Maven coordinate a generic consumer would resolve an unknown service's api jar *from*; deploy-time provisioning sidesteps this (the operator knows the coordinate), a pure-runtime generic consumer does not. **Deliverable is this design decision + its security analysis, not code.** | general-purpose · **MEDIUM** (design/analysis pass — resolved to a recommendation that adds **no** client-side download surface; the residual is a version-compatibility policy question, not a new download gate) | single reviewer + one adversarial pass confirming (b) stays non-download-capable and the version-skew residual is tracked, not silently assumed away | independent; the wired-consumer case is already satisfied, so T3 is complete for known consumers today; the generic-consumer residual (version skew) is tracked, not a T3 blocker |
| **T6** · Closeout / documentation | **IN PROGRESS 2026-07-20** — this edit, and the sibling edits to `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 3, `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` §1/§4, and `SOW-BAE-Timing-Sidechannel-Denial.md` T7, made in the same documentation-closeout pass (`SOW-Smart-Proxy-Isolation-Remaining-Work.md`'s own T7). **What "the gate can flip" actually means, precisely — not more:** T1-T4 above are real, board-reviewed, adversarially-tested mechanism, confirmed by direct code reading. It is **not** a deployed guarantee: `SubProcessLauncher`'s real OS-process spawning remains `UnsupportedSubProcessLauncher` (confirmed by multiple board reviewers), and zero production code anywhere constructs the T1-T4 machinery into an actual running service (confirmed by repo-wide grep). BAE T7's own public-facing framing pass must state this distinction precisely, not round it up to "deployed" — see that SOW's T7 row, which this row's dependency ledger (§5) feeds. Update `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 3 to describe the built wiring (currently describes the plan); update `SOW-Smart-Proxy-Isolation-Architecture-Overview.md` §1 closing caveat and §4 gap 2 to reflect landed status; confirm BAE §T7's "wiring in progress" gate can flip. Describe reality, not aspiration. | general-purpose · **MEDIUM** | single reviewer | T1–T5 substantially landed |

### Sequencing

- **Now, parallel:** **T1** (choke-point branch — the skeleton the others plug into), **T3** (wire-field
  shape is independent design). **T5 is no longer a long pole** — the interface-distribution design pass
  is resolved (§3 *T5 resolution*): the wired-consumer case is already the deployed convention (ordinary
  Maven `-api` dependency), and the generic case has a recommendation (deploy-time provisioning) that
  adds no client-side download surface; only the version-skew residual is carried forward, and it does
  **not** gate T3 for known consumers.
- **After T1:** **T2** (T1 calls into subprocess spawn/track), then **T4** (the handoff body T1
  invokes, needs T2's subprocess to exist and T3's interface hint to feed the stub build).
- **T1 follow-up (S6) gates T2, not T1:** the self-unmarshal fast-path principal guard (§3 *T1
  follow-up*) is **not** required for T1's own merge — it is not cross-principal reachable until T2's
  per-principal loaders exist — but it **must land with or before T2**, together with the S5 pooling-key
  binding the guard consults. Do not let it slip past T2.
- **S5 pooling-key rule needs sign-off before T2 code, not during review:** the canonical derivation
  (§4) is a security-relevant scoping decision; settle and sign it off *before* T2 is built against it,
  since T2's whole pooling correctness depends on it.
- **Cross-SOW:** `SubProcessDynamicPolicy` T4 unblocks once **T2** exposes its registry/handle (§4);
  BAE T7 unblocks once **T1/T2/T4** are the default path.
- **Last:** **T6**, once the wiring is real.

**What actually happened:** T1/T2/T3 landed together 2026-07-19, in roughly this planned order (T1
first, T2/T3 following). T4 landed 2026-07-20, one day later than the same wave, after two board-review
rounds — confirming the "T1, T2, T4 guarded hardest" note below was warranted: T4 was not a clean pass,
it took real defect-finding to close (see the T4 table row and `SOW-T4-Wire-Handoff-Protocol.md` §10).
BAE T7 unblocked as this sequencing predicted, once T1/T2/T4 landed — see that SOW's T7 row for how it
was actually worded once "landed" became true, including what it still does not claim.

### Notes

- **T1, T2, T4 guarded hardest** — a control branch at the download choke point (T1), a novel
  authority-binding + cross-process lifecycle mechanism (T2), and the single concentrated
  object-reconstruction door (T4) are all in the same risk class as `SubProcessDynamicPolicy`'s T2/T3
  and this session's adversarially-probed BAE work. Build-and-run probes against the real
  implementation, not design review alone.
- **T3 is attacker-influenced wire content** — treat every byte of the new field as hostile; the
  decode-bounds and the "never bundles a privileged interface" checks are load-bearing, not
  formalities.
- **T5 is resolved, not deferred (§3 *T5 resolution*).** The trust path is now stated, not assumed: the
  wired-consumer case is the existing ordinary Maven `-api`-dependency convention (already deployed,
  non-download-capable), and the generic case is recommended to deploy-time provisioning (also
  non-download-capable) rather than a runtime interface fetch. The one thing **not** assumed away is
  **version/API skew** between a consumer's provisioned api jar and the service's actual interface —
  that residual is flagged in §3, owed to whoever operationalizes provisioning, and must not be
  silently assumed to be handled by T3's name-based resolution (it is not).
- **QA implications (overview §4 gap 3 / UDS §12 point 8) are not scoped here** — deliberately. They
  need something concrete to investigate against, which is precisely what T1–T4 produce; that pass runs
  after, on its own terms, not folded into this SOW.

### T1 follow-up — self-unmarshal fast-path principal guard (S6)

`resolve()`'s self-unmarshal fast path (`PreferredProxyCodebaseProvider.java:1710-1720`) collapses
`loader = parent` whenever the wire-controlled `path` equals the `parent` loader's own annotation
(`loaderPath`), running **before** the isolation branch. Per S6 this is **not cross-principal reachable
today** (top-level `parent` annotation is `null`; a nested `parent` is same-principal by construction and
resolves no novel bytecode), so it **does not block T1's own merge**. It **must** be closed **with or
before T2**, because T2's per-principal subprocess loaders are precisely the precondition that makes a
cross-principal `parent` possible — an earlier principal's cached `PreferredClassLoader` presented as
`parent` to a later, different-principal `resolve()`. Two admissible closures (a decision for T2's board,
not left implicit):

1. **Reorder (preferred — cheapest structurally-correct fix).** Evaluate the isolation decision for the
   current call's derived pooling key (S5) **before** consulting the self-unmarshal fast path *and* the
   `CACHE` lookup (`:1721-1727`). When isolation is mandatory for this principal, never collapse to an
   in-process `parent`; route to the subprocess. Leave the `SERVICES_EXP` lookup (`:1709`) first — this is
   not merely assumed safe, it was **adversarially validated** (T1 board review, legacy-path-bypass
   angle, probes 1a/1b): `SERVICES_EXP` is keyed on `(handler, codebase)` via `Key.equals`, and dynamic-
   proxy handler identity is not attacker-echoable across a principal boundary, so a `SERVICES_EXP` hit
   cannot be forged to collide two different principals' proxies.
2. **Principal-consistency guard (only if the fast path is a *measured* same-principal win worth
   keeping).** Trust `loader = parent` only when the pooling key the `parent` loader was created under
   equals the current call's key. "Confirm" concretely requires a **loader→pooling-key binding**: at
   loader creation (`:1728`) record the S5-derived key in a loader-keyed weak map; the guard reads
   `parent`'s recorded key and requires it to `.equals()` the current key before collapsing. A `parent`
   with *no* recorded binding (ordinary app/system loader) is trusted only when the current call is
   itself non-isolated; otherwise **fail closed** to the isolation path (G6 — and verify the refusal
   leaves no partially-constructed loader cached). **Explicit T2 acceptance criterion if closure (2) is
   chosen over (1): every loader-creation site must populate this binding, with no exception — a single
   future loader-creation path that forgets to record it silently reopens the bypass (a fail-open
   landmine, not a footnote-level caveat). T2's adversarial review must explicitly enumerate every
   loader-creation site and confirm each populates the binding, not just the one this SOW cites.**

Either closure is an explicit acceptance gate on T2 (recorded in §5's ledger), not a silent assumption.
Reviewed and confirmed adequately scoped by the T1 board reviewer who found this gap (2026-07-19),
recommending closure (1) as cleaner given (2)'s fragility above.

### T5 resolution — interface-jar distribution mechanism

The wiring row (T5) asked *what delivers `*-api.jar` to a consuming JVM, and does that path stay
non-download-capable.* This subsection resolves it as far as the evidence allows, per Board Guidance G2
(name the mechanism, don't restate the open question).

**Survey of how JGDMS distributes interface jars today (the load-bearing finding).** The strongest
candidate answer is not hypothetical — it is already the deployed convention for ordinary,
non-isolation service consumption:

- **`*-api.jar` is an ordinary Maven artifact.** `services/hello-world/hello-world-api` builds
  `packaging jar` under `au.net.zeus.jgdms.hello:hello-world-api`; consuming modules declare it as a
  plain `<dependency>` in the same block, and at the **same trust class**, as `jgdms-jeri`,
  `jgdms-platform`, `jgdms-lib-dl` — verified in `services/hello-world/hello-world-client/pom.xml`
  (lines 40-48). It is resolved onto the compile/runtime classpath **at build/deploy time, before the
  process starts**; it is **not** downloaded at runtime and carries no download capability.
- **STD-009 §6.5 codifies exactly this split.** Its module table names `-api` as the interface module
  (an ordinary artifact) and `-dl` as *the* downloadable-behaviour jar; only `-dl` is ever served as a
  codebase. `-api` classes are inert interface types, never `preferred`. So "interfaces are a normal
  library dependency, only behaviour is mobile code" is a pre-existing, enforced JGDMS convention, not
  a new invention this SOW needs.

**What this means for T5.** For every **wired/known** consumer — a client built against a service, a
ServiceUI author, an orchestrator that already knows which services it hosts — T5 resolves to
**candidate (a), and no new mechanism is required**: the api jar is already an ordinary trusted
build/deploy dependency (non-download-capable, resolved before process start). This is the common case
and it is **already solved**. T5 is only genuinely open for the residual **generic** consumer:
STD-009 §8.6's shape 2, a process that *discovers a service at runtime* and does **not** already hold
its Service-API types.

**The three candidate delivery paths for the generic-consumer case, with trust properties:**

- **(a) Trusted shared API-jar distribution** (a curated Maven coordinate the consumer declares as an
  ordinary dependency). *Trust:* identical to any other library dependency — resolved at build/deploy,
  **non-download-capable at runtime**. *Limitation:* only works when the consumer knows, at build time,
  which service it will talk to — i.e. it is not actually "generic".
- **(b) Deploy-time provisioning** (the orchestration / `service-starter` tooling stages the api jar
  into the consumer's classpath location *before* the consumer process starts). *Trust:* still
  **non-download-capable at runtime** — the jar is present on the classpath at process start exactly as
  in (a); the operator/orchestrator, an already-trusted party, is the one who placed it. *Cost:*
  requires deploy-time tooling and the operator to know the service→coordinate mapping.
- **(c) A narrow non-download runtime fetch** (the client resolves the interface codebase at runtime,
  e.g. STD-009 §6.5's "`*-api.jar` served as the codebase iff shape 2 applies"). **Flagged, as this
  row's own mandate requires:** this is *precisely* the download surface the whole isolation
  architecture exists to remove from the client process. Even though it fetches only inert,
  never-`preferred` interface types (strictly less dangerous than downloading `-dl` behaviour), it
  **re-opens a class-resolution/decode path in the client heap** — the exact surface T1 routes away and
  T4 concentrates in the sidecar. **Recommend against.**

**Recommendation: (a) where the consumer is known at build time (already the status quo — nothing to
build), and (b) — deploy-time provisioning over the same Maven-coordinate convention — for the generic
runtime-discovered consumer.** Reasoning: (b) delivers the interface jar with the identical
runtime trust profile as (a) (present on the classpath at process start, placed by a trusted operator,
never fetched by the client at runtime), while covering the case (a) cannot (consumer not known at
build time). It keeps the client's own heap free of any download-capable surface — which is the entire
point of routing smart-proxy reconstruction into the isolated sidecar. **This recommendation
reintroduces *no* download-capable surface into the client process** (stated explicitly per the T5
mandate); the download of *behaviour* (`-dl`) remains where the architecture already puts it — inside
the sidecar only, never the client.

**What remains genuinely unresolved after this recommendation** (do not let it become a silent
assumption, per §3's Notes):

1. **Version / API skew.** A locally-provisioned `-api.jar` resolves an interface *name* but binds to
   whatever method-set *that jar's revision* holds. If the consumer's provisioned api jar is an older
   or newer revision than the service's actual compiled interface, `Class.forName(name, false,
   localOnlyLoader)` (T3) succeeds on the name yet yields a different shape — and T3's name-based
   stripping **cannot** detect a same-name/different-shape mismatch. JGDMS's japicmp + serial-schema CI
   gate (`jgdms-api-compat-tooling`) binds *JGDMS's own* artifacts' compatibility, but nothing binds a
   third-party consumer's provisioned api jar to the specific service instance it dials. This is a real
   residual, owed to whoever operationalizes (b): either a version-pinning discipline in the
   provisioning tooling, or a runtime interface-schema check at the sidecar handoff (T4) that compares
   the consumer's resolved interface shape against the service's — a possible future task, flagged here,
   not scoped in this SOW.
2. **Coordinate discovery.** Nothing on the lookup wire (the `UIDescriptor`, the service type) carries
   the Maven coordinate a generic consumer would resolve an unknown service's api jar *from*.
   Deploy-time provisioning (b) sidesteps this — the operator supplies the mapping — but a *pure*
   runtime-generic consumer with no operator in the loop has no in-band way to learn the coordinate.
   Closing that would require either an out-of-band service→coordinate registry or a lookup-wire schema
   addition; both are out of scope here and neither is needed for the recommended (a)/(b) path.

**Net:** T5 is resolved to a recommendation for the common and the generic cases, adding no client-side
download surface; the one substantive residual (version/API skew) is a compatibility-policy question,
tracked above, not a blocker on T3 for known consumers.

---

## 4. The subprocess registry/handle shape (for `SubProcessDynamicPolicy` T4)

`SubProcessDynamicPolicy.md` T4 (caller-side grant push) "cannot fully land until [this SOW's
subprocess-spawning wiring] exists" and today has to guess at the target's shape. T2 must therefore
expose, at minimum at the interface level, a handle with these properties so T4 is unblocked:

- **Keyed by remote SPIFFE principal**, matching the pooling grain (UDS §12 point 1) — one handle per
  live subprocess, resolvable from the same principal T1 extracts at `resolve()`.
- **Resolves to the authenticated admin surface** that T2's S1 binding established — in the preferred
  realization, the `PolicyAdmin` proxy from `SubProcessAdministrable.getSubProcessPolicyAdmin()` (a
  parallel interface to `Administrable`, not a reuse — so the backend service's own `getAdmin()` is
  neither collided with nor clobbered), whose `MethodConstraints` require the orchestrating admin
  principal; equivalently, the spawn-time privileged UDS connection/handle. This is the channel
  `SubProcessDynamicPolicy`'s grant-push must ride, and the *only* surface the subprocess-side dispatch
  will honor for policy management (S1). T4 does **not** get to present an arbitrary connection/proxy
  and assert authority over it — authority is the admin principal's authentication, not possession of a
  reference.
- **Exposes liveness state derived from DGC acknowledgment-processed semantics** (T2/S4), so T4 can
  tell whether a target subprocess is still live before pushing a (lease-scoped) grant, without racing
  a teardown.
- **Does not expose the hosted smart-proxy instances themselves** — consistent with
  `SubProcessDynamicPolicy` §2's caveat that policy-management dispatch must target the subprocess's
  own separate trusted management object, never the hosted proxy. The handle is a management-plane
  reference, not a business-plane one.

With that shape available, `SubProcessDynamicPolicy` T4's "push the computed grant to *this specific*
subprocess" resolves to "push it over *this handle's* spawn-time privileged connection" — the channel
is the target identity (that SOW's §2 core property), and no spoofable target-id parameter is needed.

### Canonical pooling-key derivation (S5)

**Design decision requiring explicit sign-off — not merely code review.** T1 hands the isolation router
the full `serverPrincipals` array; T2 must reduce it to a single canonical pooling key by the rule below,
which is **deterministic given the same certificate regardless of `HashSet` iteration order**. This rule
is the interface contract T2 is built against — it must not be left to an implicit "use element `[0]`"
assumption, which would under- or over-distinguish principals depending on certificate content and
`HashSet` ordering.

Given `Principal[] serverPrincipals` (from `SslConnection.populateContext`: one `X500Principal` ∪
zero-or-more SPIFFE principals):

1. **Partition by SPIFFE-ness on the *name*, not the class.** A principal is SPIFFE iff `getName()`
   starts with `spiffe://`. Match by name-scheme, **never `instanceof SpiffePrincipal`** — the
   authenticated peer identity may be the JDK's read-only `RemoteSubject` SPIFFE implementation, and
   `SpiffePrincipal`'s own contract is cross-implementation matching by canonical `getName()`, not
   class-exact `equals` (`SpiffePrincipal` class javadoc, "Canonical form and matching").
2. **Exactly one SPIFFE principal** — the SPIFFE-conformant SVID case (a valid X.509-SVID leaf carries
   exactly one `spiffe://` URI SAN): **key = `"spiffe:" + p.getName()`**. `getName()` is already the
   RFC-3986-canonical URI (normalized through `Uri`), so the key is a pure function of one canonical
   string, independent of iteration order.
3. **Zero SPIFFE principals** — **key = `"x500:" + x500.getName(X500Principal.CANONICAL)`** (the sole
   `X500Principal`; `CANONICAL` is the deterministic RFC-2253 canonical form). Log at `FINE` that
   isolation fell back to X.500 identity (no SVID present).
4. **More than one SPIFFE principal — fail closed** (G6): refuse to derive a key and refuse the isolation
   route. A conformant SVID has exactly one `spiffe://` SAN; multiplicity is non-conformant or ambiguous,
   and silently picking or unioning would let certificate content steer which pool a proxy lands in. Do
   **not** fall back to X.500 on multiplicity — that would let an attacker *suppress* SPIFFE-based pooling
   by adding a second SAN. Audit and refuse. If a federation deployment legitimately needs multi-SAN
   identities, that is a **separate signed-off revision** (e.g. canonical-sorted concatenation of all
   SPIFFE names), never a silent default.
   **Known operational sharp edge, not purely an attack shape (T1 board reviewer, principal-confusion
   angle, 2026-07-19):** fail-closed-on-multiplicity also makes a *legitimate* SVID rotation/federation
   certificate carrying two SANs un-serveable to isolation-enabled clients — an availability cost during
   normal SVID migration windows, not just a defense against a hostile cert. This is exactly the case the
   "separate signed-off revision" above exists for; flag it in deployment/operational runbooks as a known
   limitation during SVID rotation, not only as a security property.

**Precedent (stay consistent, don't invent a new identity notion).** This mirrors JGDMS's existing
principal-identity convention: `PrincipalGrant` / `UnresolvedPrincipal` identify a principal by its
`(class, getName())` pair and match resolved principals by `Principal.equals`, which for both
`X500Principal` and `SpiffePrincipal` reduces to canonical-name equality
(`PrincipalGrant.implies`, `PrincipalGrant.java:224-235`). The rule keys on that same canonical-name
basis. **Never** key on array index, `Principal.hashCode()` / identity, or raw `Set` iteration order —
all are order- or run-dependent and would make pooling itself non-deterministic (the core S5 hazard).

---

## 5. Explicit dependency ledger

| This SOW's task | Depends on / feeds | Direction |
|---|---|---|
| T1 routing branch | UDS §12 point 3 (insertion point decision) | consumes |
| T2 subprocess registry/handle (§4) | `SubProcessDynamicPolicy` T4 (grant push) | **feeds** — unblocks it |
| T2 authority binding (S1) | `SubProcessDynamicPolicy` §2 (convention → mechanism) | enforces |
| T3 `ProxySerializer` field | STD-009 §6.4 interface stripping (concrete realization) | realizes |
| T4 reconstruction gates (S2) | UDS §12 point 3(i); existing SCAP/BAE + `DeSerializationPermission` path | preserves across process boundary |
| T1/T2/T4 landed | BAE §T7 public-framing gate | **feeds** — flips "wiring in progress" → "deployed" |
| T5 interface distribution | STD-009 §8.6 (`*-api.jar` provisioning assumption); STD-009 §6.5 (`-api`=ordinary artifact / `-dl`=codebase split); `hello-world-client`→`hello-world-api` pom precedent | **resolves** — wired case = existing ordinary-Maven-dependency convention (no new mechanism); generic case → deploy-time provisioning (no client download surface); residual = version/API skew |
| T2 pooling-key (S5) | canonical rule §4; `SpiffePrincipal.getName()` / `X500Principal.CANONICAL`; `PrincipalGrant` precedent | **specifies** — needs explicit sign-off before T2 code |
| T1 self-unmarshal guard (S6) | T2 per-principal loaders (make the bypass cross-principal reachable) | **gated by / gates** — must close with or before T2 |

---

*Status: LANDED (T1-T5), documentation closeout in progress (T6). This document's original planning
prose (design rationale, sequencing, notes) is left intact below the landed-status annotations, per
this document set's convention of recording design history rather than deleting superseded framing —
the annotations are what distinguish the landed parts from the original plan. See
`SOW-T4-Wire-Handoff-Protocol.md` for T4's own authoritative built-state document.*
