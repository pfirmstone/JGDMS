# Scope of Work — `SubProcessDynamicPolicy`: verdict-driven, cross-process dynamic permission grants

- **Drafted:** 2026-07-17.
- **Status (updated 2026-07-20 — this line was stale, see `SOW-Smart-Proxy-Isolation-Remaining-
  Work.md`'s own note about it): LANDED.** T1 (namespace relocation) and T2 (verdict-to-permission-set
  function) landed in the 2026-07-19 wave. T3 landed in two parts, at two different times: parts (a)/(b)
  (the `SubProcessAdministrable`/`PolicyAdmin` interface shape and the subprocess-side authentication/
  dispatch scaffold) landed 2026-07-19 as `SOW-Smart-Proxy-Isolation-Wiring.md`'s own T2; part (c) (the
  real grant-application backend behind it, `SubProcessLocalPolicyAdmin`) — the only part of T3 that was
  still open — landed 2026-07-20 as `SOW-Smart-Proxy-Isolation-Remaining-Work.md`'s own T1 (commits
  `3711727b0`/`e0abc35fc`/`36ca3291b`, board-reviewed). T4 (caller-side verdict->ceiling->grant wiring,
  `SubProcessGrantOrchestrator`) landed 2026-07-20 as that same document's T3 (commit `62e002668`,
  single-reviewer, clean). T5 (system-level adversarial pass across T3->T4) landed 2026-07-20 as that
  document's T4 — see the T5 row below for what it actually found (two real, previously-undiscovered
  defects, not a clean pass). T6 (this document's own closeout, the row below) is being executed now, as
  part of the remaining-work SOW's own T7. See each task row in §4 for specifics; this status line
  summarizes, the rows are authoritative.
- **Origin:** `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 6 (the dynamic-policy investigation
  that found the gap this SOW closes) and point 6's two decisions (2026-07-17): relocate
  `RemotePolicyProvider` out of the shared `org.apache.river.api.security` namespace, and build a new
  `SubProcessDynamicPolicy` component. This SOW is the detailed task breakdown for both.
- **Companions:** `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 (the mandatory UDS-isolated
  per-remote-SPIFFE-principal subprocess architecture this serves — read that first, this SOW assumes
  it); `SOW-BAE-Timing-Sidechannel-Denial.md` §1b (SM/POLP as the isolated subprocess's complementary
  defense layer — this SOW is *how* that layer gets its permission ceiling); `JGDMS-STD-009-...-DRAFT.md`
  §8.3's 2026-07-17 note (independently arrived at the same mechanism for the **server-side filter
  sidecar** — see the scope note below).
- **Scope generalized 2026-07-17, not yet reflected in §2-§4 below.** This SOW was originally scoped
  around the **client-side** smart-proxy sidecar only. Researching how a service grants a **filter**
  sidecar (STD-009 §8, server-side, a different tenant) permission to load/run turned up the identical
  cross-process-policy-delivery gap, with `BasicProxyPreparer`/`Security.grant()` ruled out as a fit
  (it only ever mutates the *calling* JVM's own installed policy, never a separate child process's) —
  so `SubProcessDynamicPolicy` is now understood to serve **both** tenant types, not client-side-only.
  What's **not yet reconciled**: §2 below (updated 2026-07-18 to the `SubProcessAdministrable`/
  `PolicyAdmin` shape — see that section) still describes the accessor as living on "the client's own
  local delegate dynamic proxy stub," which assumes a client *holding a proxy* to a remote object — fits
  the smart-proxy case but not obviously the filter case (a service holds a handle to *its own spawned
  filter sidecar*, not a downloaded proxy over a business interface). The new accessor-plus-separately-
  authenticated-admin-proxy shape is plausibly *easier* to generalize than the superseded "bundled onto
  the same stub, riding the business-call InvocationHandler" framing was — an `Administrable`-pattern
  accessor doesn't inherently require "a proxy to a remote business object" the way routing through the
  same `InvocationHandler` did, so a filter-owning service could plausibly implement
  `SubProcessAdministrable` on whatever handle/object it already uses to track its own spawned sidecar.
  That is a plausibility argument, not a design decision — whether the filter tenant reuses this shape
  with the service's own sidecar-handle standing in for the client's stub, or needs something materially
  different, is still real, unresolved design work, flagged here rather than assumed away. Expected to be
  minimal in practice: STD-009 §8.4's "zero ambient authority" means the filter's own grant is expected to
  be near-empty, so getting this exactly right matters less operationally than it does for the
  smart-proxy case, but the mechanism should still be designed once, correctly, for both tenants rather
  than forked.

---

## 1. What this closes, precisely

`SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 6 found: JGDMS's local dynamic-policy primitives
(`DynamicPolicy`/`DynamicPolicyProvider`, `PermissionGrant`/`PermissionGrantBuilder`, `DigestGrant`,
`LeasedPermissionGrant`/`LeasedDelegation`) are mature and directly reusable *inside one JVM*, but two
things needed for the new isolated-subprocess architecture do not exist:

1. **No runtime function from a SCAP/BAE verdict to a computed permission set.** The one component that
   maps audits to policy, `ProxyPolicyGenerator`, is offline/CI-tooling, not a callable
   `f(VerdictType, contentHash) → Permission[]`.
2. **No mechanism to deliver a grant into a separate, already-running child process.** The closest thing,
   `RemotePolicyService`/`RemotePolicyProvider`, is a real, working, JERI-remote policy-distribution
   service — but it's a whole-set replace, administered by one fixed human admin identity, unwired into
   any process-bootstrap path, and not shaped for "push this one computed grant to this one specific
   spawned process."

This SOW builds both, plus the namespace relocation §12 point 6 decided as a prerequisite.

---

## 2. Design shape (as decided so far — some of this is still open, see §5)

- **Superseded 2026-07-18 (Peter, refined further while reviewing `SOW-Smart-Proxy-Isolation-
  Wiring.md`'s T2): the "additional interface bundled onto the local delegate stub, riding the exact
  same `InvocationHandler`/UDS-forwarding as business calls" framing below (kept struck-through-in-spirit
  for design history) is replaced by a dedicated, separately-authenticated admin surface —
  `SubProcessAdministrable`.** This is the authoritative shape; see `SOW-Smart-Proxy-Isolation-
  Wiring.md` §2 (finding S1) and its T2 acceptance criteria for the fully-worked version this section
  now mirrors. Summary:
  - The client's per-proxy local delegate stub additionally implements a new, dedicated
    `SubProcessAdministrable` interface (`au.net.zeus.jgdms.*` — never `org.apache.river.api.security`,
    per the UDS SOW §12 point 6 namespace rule), whose accessor `getSubProcessPolicyAdmin()` follows the
    same pattern as `net.jini.admin.Administrable.getAdmin()` — an accessor returning a *separate* admin
    proxy — but is a **deliberately parallel, non-colliding interface**, not a reuse of `Administrable`
    itself. Reason: the hosted smart proxy / backend service may *already* legitimately implement
    `Administrable` for its own purposes (`JoinAdmin`/`DestroyAdmin`/`StorageLocationAdmin`, forwarded
    through to the backend) — reusing `getAdmin()` for subprocess-policy administration would collide
    with and potentially clobber a surface an operator still legitimately needs. The two admin surfaces
    (backend-service admin via `Administrable.getAdmin()`; local-subprocess-policy admin via
    `SubProcessAdministrable.getSubProcessPolicyAdmin()`) coexist as two orthogonal authorities, never
    conflated.
  - `getSubProcessPolicyAdmin()` returns a **separate `PolicyAdmin` proxy**, to a separately-exported
    subprocess-side trusted management object, carrying its **own, stricter `MethodConstraints`**
    (client authentication as the orchestrating admin principal, `Integrity.YES`). **This is the actual
    enforcement boundary** — admin authority is proven by authentication on the `PolicyAdmin` proxy's
    own endpoint, not by which interfaces the business stub happens to expose. This directly closes the
    gap the original framing below left open: that framing's "the authorized caller is simply whichever
    trusted client-side code legitimately holds a reference to that specific proxy's local delegate
    stub — an ordinary Java object-reference/encapsulation boundary... not a new remote
    identity/authentication scheme to design from scratch" was a **convention about who is expected to
    hold the reference, not an enforced mechanism** — declaration-is-the-signal (Board Guidance G4): a
    malicious or buggy client can build its own stub with the interface bundled in regardless of
    convention, and the subprocess sees only a UDS call, never the client's declared interface set. The
    `SubProcessAdministrable`/`PolicyAdmin` split fixes this by requiring genuine authentication as the
    admin principal on the returned proxy's own endpoint — not by trusting stub construction discipline
    alone.
  - **Residual hazard, must fail closed:** `getSubProcessPolicyAdmin()` and every `PolicyAdmin` operation
    must reject (return nothing usable / throw) any caller that cannot authenticate as the orchestrating
    admin principal — a mere business-stub holder calling the accessor must get nothing usable.
    Construction-time interface exclusion (the bulleted item below, retained) is hygiene that reduces
    reach; runtime admin authentication is what actually enforces. They are complementary layers, not
    alternatives — do not let either substitute for the other.
  - **A third, defense-in-depth layer belongs to `SOW-Smart-Proxy-Isolation-Wiring.md` T2, not this
    SOW's scope, but is load-bearing for this mechanism's soundness and is cross-noted here:** the
    subprocess must refuse to host any smart proxy whose own *resolved* interface closure includes
    `SubProcessAdministrable`/`PolicyAdmin` (checked on type identity, never a wire-name match) — no
    legitimate business proxy declares the subprocess's own management interface, so one that does is a
    TOCTOU/dispatch-confusion escalation attempt. Rejection is the *only* fail-closed option here (not a
    preference over "stripping" the interface): unlike the client-side delegate stub, which is a
    dynamically-constructed `java.lang.reflect.Proxy` that legitimately exposes a chosen interface
    subset, the hosted smart proxy is an ordinary concrete class whose implemented-interfaces closure is
    fixed at compile time by whoever wrote it — there is no way to reduce it at runtime, so hosting the
    object at all means hosting exactly the class it is.
  - **The "which proxy does this grant target" benefit is retained**, just re-derived over the new
    shape: a subprocess can host multiple proxy objects pooled from the same principal (UDS SOW §12
    point 1); riding the per-proxy stub's accessor to reach the target subprocess's own `PolicyAdmin`
    means the channel (which specific stub's `getSubProcessPolicyAdmin()` you called) still identifies
    *which* subprocess/management-object you're talking to — no separate spoofable target-id parameter
    is needed — but *authority to act on it* is no longer "whichever channel you reached," it is
    "whichever channel you reached, **and** whether you can authenticate as the admin principal on the
    proxy it returned." Channel narrows the target; authentication proves the authority. Both are now
    required, where the original framing below relied on the channel alone for both.
- **Superseded framing (2026-07-17, kept for design-history record, do not implement as written):**
  ~~`SubProcessDynamicPolicy` is one of the interfaces implemented by the local delegate dynamic proxy
  stub itself, calls routing through the exact same `InvocationHandler`/UDS-forwarding machinery as
  ordinary business calls, with the authorized caller being simply whichever trusted client-side code
  holds the stub reference.~~ This was superseded because it conflated "which channel reached the
  subprocess" with "who is authorized to administer it" — the second question needs its own
  authentication, not just object-reference encapsulation on the client side, which is a client-side
  discipline the subprocess has no way to verify.
- **Caveat retained unchanged: on the *subprocess* side, the object that answers policy-management calls
  must not be the same object as the smart-proxy business object itself**, even though both may be
  reachable via plumbing associated with the same client stub. If the subprocess multiplexes ordinary
  business calls and policy-management calls onto literally the same exported object, there is a real
  risk of conflating "the untrusted proxy's own business logic" with "the subprocess's own trusted
  management surface." The subprocess-side implementation must dispatch policy-management calls to its
  own, separate, trusted hosting/management object — the `PolicyAdmin` target above — never to (or
  through) the smart proxy instance being hosted.
- **The caller is still a more-trusted party than the subprocess's own bootstrap/hosting code for
  *deciding* the grant** — the subprocess-side handler should not itself decide *what* to grant (that's
  T2's job, computed elsewhere and carried in the call), only apply an already-computed instruction after
  the `PolicyAdmin` proxy's own authentication check passes.
- **Grants should be lease-scoped by default**, using the existing `LeasedPermissionGrant`/
  `LeasedDelegation` machinery, so a stale or misbehaving proxy's ceiling can expire or be explicitly
  revoked without a hard subprocess kill.
- **Only the orchestrating party's stub gets `SubProcessAdministrable` bundled in — decided
  (Peter, 2026-07-17), following directly from the UDS SOW §12 point 3(iii) finding that the local
  delegate stub is a per-consumer-JVM construct, not a single universal stub.** A subprocess-hosted proxy
  can legitimately be reached by more than one local delegate stub in more than one process — e.g. the
  orchestrating client that spawned the subprocess and is entitled to administer its policy, versus a
  separate downstream process consuming the same proxy through its own "ServiceAPI" (possibly
  third-party-defined). Each such stub is built independently, using only the interfaces relevant to
  whoever built it. **When a proxy is serialized/exposed to a ServiceAPI-consumer's process, the stub
  built there must not include `SubProcessAdministrable` at all** — only the stub held by the party that
  actually spawned/owns the subprocess (or is otherwise explicitly entrusted with administering it)
  should ever be constructed with it bundled in. **This is construction-time hygiene, not the enforcement
  boundary** — per the residual-hazard note above, a stub built without the interface reduces casual
  reach, but the actual authority proof is the `PolicyAdmin` proxy's own admin-principal authentication,
  which holds even if this construction-time discipline is ever violated (maliciously or by bug).

---

## 3. Non-goals

- **Not building the subprocess-spawning/lifecycle machinery itself** — that's `SOW-Unix-Domain-Socket-
  JERI-Transport.md` §12 point 3's task. This SOW assumes an isolated subprocess already exists and is
  reachable over UDS by the time `SubProcessDynamicPolicy` is invoked.
- **Not redesigning `VerdictRegistry`/SCAP** — T2 (§4) consumes an existing `RegistryVerdict`, it does
  not change how verdicts are computed or signed.
- **Not a general-purpose remote administration API** — deliberately narrow (apply one computed grant),
  not a rebuild of `RemotePolicyService`'s broader whole-set-replace/subscription model.

---

## 4. Execution plan — task breakdown (agent type + effort)

Effort follows the project convention (security → `xhigh`, class/wire-resolution → `max`,
mechanical → `medium`, test-debug → `xhigh`); orchestration follows *fresh implement → review gate*,
with a **parallel review board** for high-blast-radius/security work. This is a "who gets to grant what
to whom" mechanism — the same class of component that produced real, adversarially-found bypasses
repeatedly in this session's BAE work; hold it to the same standard.

### Summary

| Task | Deliverable | Implement (agent · effort) | Review | Depends |
|------|-------------|------------------------------|--------|---------|
| **T1** · Namespace relocation | Move `org.apache.river.api.security.RemotePolicyProvider` to replace the dead stub at `au.net.zeus.jgdms.api.policy.RemotePolicyProvider` (`jgdms-platform/.../RemotePolicyProvider.java:28-35`, currently `UnsupportedOperationException`); re-point all callers of the legacy copy; no parallel copy left behind. Low-risk, mechanical, but touches a real API surface — confirm no external callers outside this repo before deleting the legacy class outright (deprecate-then-remove if any are found). | general-purpose · **MEDIUM** | single reviewer | none — can start immediately |
| **T2** · Verdict-to-permission-set function | Build the missing `f(RegistryVerdict, contentHash, [declared needs from META-INF/PERMISSIONS.LIST]) → Permission[]` — a runtime-callable analogue of what `ProxyPolicyGenerator` does offline. Needs an explicit mapping policy, not just plumbing: SAFE → ceiling from declared needs (bounded by whatever `GrantPermission` the caller itself holds — never exceed that, regardless of verdict); INCONCLUSIVE → narrower ceiling, gated analogously to the existing `INCONCLUSIVEPermit` concept (`PreferredProxyCodebaseProvider.java:1550-1564`); DANGEROUS is already refused upstream (out of scope here — this function is never called for a DANGEROUS verdict). This is a **security policy decision**, not just code — the actual mapping needs explicit sign-off, not just implementation review. | general-purpose · **XHIGH** (establishes the actual authority ceiling logic for every isolated subprocess going forward) | **parallel board** (2–3 adversarial) — specifically probe: can a crafted `META-INF/PERMISSIONS.LIST` or a boundary-case verdict cause this function to compute a broader ceiling than intended | none; can run in parallel with T1 |
| **T3** · `SubProcessAdministrable`/`PolicyAdmin` interface + subprocess-side handler | **LANDED — parts (a)/(b) 2026-07-19 (`SOW-Smart-Proxy-Isolation-Wiring.md` T2), part (c) 2026-07-20 (`SOW-Smart-Proxy-Isolation-Remaining-Work.md` T1, commits `3711727b0`/`e0abc35fc`/`36ca3291b`).** Parts (a)/(b) below — the interface shape and the authentication/dispatch scaffold (`SubProcessAdministrable`, `SubProcessPolicyAdmin`, `GuardedPolicyAdminHandler`, `AdminPrincipalAuthenticator`) — were real from 2026-07-19, but the `backing` object `GuardedPolicyAdminHandler.invoke()` delegates to was, until 2026-07-20, only ever a test double (`IsolationSecurityCriticalTest.TrustedPolicyBacking`, a counter-incrementing stub) — repo-wide grep found zero real implementations. **Part (c) — the real backend, `SubProcessLocalPolicyAdmin`, implementing `PolicyAdmin`** — closed that gap 2026-07-20: `grant(PermissionGrant)` applies verbatim to the subprocess's own local `DynamicPolicyProvider`/`LeasedDelegation` (lease-scoped, per §2's decision), performing no independent judgment about what to grant; `refresh()`/`getGrants()` are real, `getGrants()` reading the backend's own live-install index rather than attempting `implies()`-based domain matching (which a `DigestGrant`/`URIGrant`/`CertificateGrant`/`ClassLoaderGrant` can never satisfy against a synthetic codesource-free domain — an early implementation bug the board caught and this describes as fixed, not as a residual). Board review (3 seats, `3711727b0`) returned BLOCK; `e0abc35fc` fixed three findings before merge: (1) a visibility gap — the class/constructors are now package-private, so `GuardedPolicyAdminHandler` is compiler-enforced as the only reachable path, not merely claimed by javadoc; (2) a HIGH universal-implies escalation — `PrincipalGrant.implies(Principal[])` treats a null/empty principal scope as "implies everything", and nothing required a caller-supplied `PermissionGrant` to be principal-scoped at all, so an absent scope silently became a universal grant; fixed by rebuilding every install via the grant's own `getBuilderTemplate()` with principals unconditionally overridden to this backend's own configured `scopePrincipals`, and rejecting an empty `scopePrincipals` array at construction; (3) a T1/T3 wire-serializability contradiction — `grant()` originally refused any grant that wasn't already a `LeasedPermissionGrant`, but `SubProcessGrantOrchestrator` (T4 below) builds a plain unwrapped DIGEST-context grant by design; `grant()` now owns lease-wrapping unconditionally instead of requiring the caller to have already done it. Re-verified clean on re-review. Below is the original design-shape description, kept as the authoritative *shape* record — it did land as designed; only the "no real backend exists yet" framing at the top of it is what's now stale. **Revised shape (§2, superseded 2026-07-18): a dedicated `SubProcessAdministrable` interface on the client-side local delegate stub, whose `getSubProcessPolicyAdmin()` accessor returns a separate, independently-authenticated `PolicyAdmin` proxy** (not a standalone JERI-remote service with its own wire protocol; not a bundled interface routing "apply this grant" through the same handler as business calls). On the subprocess side, must: (a) export the `PolicyAdmin` target as the subprocess's own, *separate*, trusted hosting/management object — **never** the hosted smart proxy instance itself; (b) gate every `PolicyAdmin` operation (and the `getSubProcessPolicyAdmin()` accessor itself) with `MethodConstraints` requiring client authentication as the orchestrating admin principal, **failing closed** (nothing usable returned) to any caller that cannot so authenticate — this is the actual enforcement mechanism, not the interface-bundling discipline; (c) apply the grant verbatim via the subprocess's own local `DynamicPolicyProvider`/`LeasedDelegation`, performing no independent judgment about *what* to grant. **Cross-SOW note:** the reject-on-load TOCTOU check (refuse to host a smart proxy whose own resolved interface closure declares `SubProcessAdministrable`/`PolicyAdmin`) is `SOW-Smart-Proxy-Isolation-Wiring.md` T2's task, not this task's — but T3 here must not be considered complete/adversarially-cleared independent of that check landing, since it's a defense-in-depth layer for the same authority boundary. | general-purpose · **XHIGH** (novel security-critical mechanism; same class of risk as BAE's T2/T5 from the companion SOW) | **parallel board**, adversarial-probing mandate — build-and-run real probes: can a caller reach `PolicyAdmin` operations without authenticating as the admin principal; does `getSubProcessPolicyAdmin()` ever return a usable proxy to an unauthenticated/wrongly-authenticated caller; does the subprocess ever conflate the `PolicyAdmin` target with the hosted business object | T1 (namespace relocation may still inform wire conventions), T2 (needs a grant to apply — can stub during development) |
| **T4** · Caller-side wiring | **LANDED 2026-07-20 (`SOW-Smart-Proxy-Isolation-Remaining-Work.md` T3, commit `62e002668`).** Built as `SubProcessGrantOrchestrator`, stub-based per this row's own original note (built and tested against a stub `PolicyAdmin`/stub subprocess before the real T1/T2/T3 wiring below existed — full end-to-end integration then confirmed once T3(c) above and `SOW-Smart-Proxy-Isolation-Wiring.md`'s subprocess-spawning machinery landed). Single reviewer, clean, merged with no changes needed at the time — see the T5 row below for the two real defects a *later*, system-level adversarial pass against the full T1->T2->T3->T4 chain found in this class specifically (it was not defect-free, just not caught by this task's own single-reviewer gate). The orchestrating logic that, once a verdict is known for a smart proxy about to run in a given subprocess, computes the grant (T2) and pushes it via `SubProcessDynamicPolicy` (T3) to that specific subprocess. Integrates with whatever spawns/tracks isolated subprocesses (UDS SOW §12 point 3's own task) — this task cannot fully land until that exists, but the caller-side logic itself can be built and tested against a stub subprocess. | general-purpose · **HIGH** | single reviewer + integration test against T3 | T2, T3; full integration blocked on UDS SOW §12 point 3's subprocess-spawning wiring |
| **T5** · Adversarial test pass | **LANDED 2026-07-20 (`SOW-Smart-Proxy-Isolation-Remaining-Work.md` T4).** Run as a system-level pass across the full T1(this doc's own T1/T2, landed prior)->T2(wire handoff)->T3(this row's T3(c))->T4(this row's own T4) chain, not each piece in isolation — and it was not a clean pass: it found two real, previously-undiscovered defects, both fixed and merged the same day. **(1) No grant-revocation mechanism** (`SubProcessLocalPolicyAdmin`, commit `86d56add1`): `grant()` was purely additive — a broad ceiling installed earlier stayed fully enforced after a narrower re-verdict, surviving `refresh()` and only expiring on its own independent lease TTL, not on being superseded. Fixed via `PermissionGrant#impliesEquivalent()`-based supersession: a new `grant()` call for the same `(scopePrincipals, digest)` target now cancels any prior LIVE grant it itself installed for that target, strictly *after* the new install succeeds (so a ceiling-denied regrant attempt never has the side effect of tearing down a still-valid different grant). A secondary audit-blindness bug in the same commit: `getGrants()` queried a synthetic codesource-free `ProtectionDomain` that a `DigestGrant`/`URIGrant`/`CertificateGrant`/`ClassLoaderGrant` can never `implies()`-match, so T3(c)'s own actual grant shape was silently reported as absent; fixed by reading the backend's own live-install index directly. **(2) No replay protection** (`SubProcessGrantOrchestrator`, commits `86d56add1` then `17b0e0551`): neither T3(c) nor T4 had any freshness check, so resubmitting a byte-for-byte identical `applyVerdictCeiling` call could silently reinstate an already-superseded/expired ceiling. First pass added a timestamp-based freshness generation, scoped per `(targetKey, contentHash)` pair, refusing any call whose `RegistryVerdict.getTimestamp()` was not strictly newer than the last one successfully applied — but a second board pass (2 seats) proved this alone was bypassable: `applyVerdictCeiling` read the timestamp without ever calling `RegistryVerdict.verifySignature`, and `RegistryVerdict`'s own `check(GetArg)` only validates structural well-formedness, not the cryptographic signature — a forged verdict with a fabricated later timestamp and garbage signature bytes sailed through the freshness gate as if genuinely fresher. Fixed (`17b0e0551`) by verifying the verdict's signature as the unconditional first step, before any other field (including the timestamp) is read or trusted, mirroring `PreferredProxyCodebaseProvider.checkVerdictForJar`'s existing pattern. **Two residuals accepted by the board as non-blocking, documented in `SubProcessGrantOrchestrator`'s own class javadoc, not silently dropped:** a narrow check-then-act race in the per-target generation map (two genuinely concurrent calls carrying the identical verdict timestamp can both pass the freshness check before either records its generation — judged acceptable for this class's current single-orchestrating-controller call pattern, since the realistic adversarial replay scenario is sequential resend of captured bytes, not a true race against the legitimate caller); and unbounded growth of that same generation map for the lifetime of a long-lived orchestrator instance (no hook to reclaim an entry when a subprocess is torn down/unregistered — a bounded/LRU map or an unregistration callback is flagged as a reasonable, currently-unbuilt follow-on). Dedicated adversarial probing of the whole T2→T3→T4 chain as a system, not just each piece in isolation — e.g. can the hosted smart proxy itself, given only its own (deliberately narrow) permissions, ever reach or influence `SubProcessDynamicPolicy`'s endpoint; can a grant be replayed or applied to the wrong subprocess; does a lease-scoped grant actually expire/revoke correctly under `LeasedPermissionGrant`'s existing semantics. Same standing brief as this session's BAE adversarial rounds: build and run real probes against the actual implementation, don't accept "the design reads sound." | general-purpose · **XHIGH** | **parallel board** | T3, T4 substantially implemented |
| **T6** · Documentation | **IN PROGRESS 2026-07-20**, as part of `SOW-Smart-Proxy-Isolation-Remaining-Work.md`'s own T7 documentation-closeout pass (this edit is part of that pass). Update `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 6 to describe the finished mechanism accurately (currently describes the *plan*); note in `SOW-BAE-Timing-Sidechannel-Denial.md` §1b that SM/POLP's permission ceiling inside the isolated subprocess is now concretely sourced from this mechanism, not just asserted to exist. | general-purpose · **MEDIUM** | single reviewer | T1–T5 substantially landed (should describe reality, not aspiration) |

### Sequencing (historical — this is what was planned; §4's rows above record what actually happened,
including the order defects were found in, which did not always match this plan)

- **Now, parallel:** **T1** (independent, quick), **T2** (independent design work, the real policy
  decision — start early since it's likely the long pole given it needs explicit sign-off on the
  mapping, not just code review).
- **After T1 (transport) and T2 (something to apply, can stub):** **T3**.
- **After T2, T3:** **T4** — full integration blocked on the UDS SOW's own subprocess-spawning task, but
  the caller-side logic can be built and tested against a stub before that lands.
- **After T3, T4 substantially done:** **T5** — system-level adversarial pass, not just per-component.
- **Last:** **T6**, once the mechanism is real, not planned.

**What actually happened (2026-07-20):** T3(c) and T4 both landed the same day T3(c) itself did — T4 had
already been built and tested against a stub per its own row's plan, so once T3(c) supplied a real
target, integration followed immediately rather than waiting on a separate cycle. T5's system-level pass
ran after both and found two real defects (grant-revocation, replay) spanning *both* T3(c) and T4 —
confirming this doc's own standing warning below (T2/T3 "the ones to guard hardest") extended to T4 too,
which the original plan did not flag as XHIGH.

### Notes

- **T2 and T3 are the ones to guard hardest** — T2 because it's a genuine security-policy decision (what
  gets granted, not just how), T3 because it's a novel remote-callable grant-application mechanism, the
  exact shape of component (new authority-granting logic) that produced real, adversarially-found
  bypasses repeatedly in this session's BAE work. Do not let "the design reads sound" substitute for
  build-and-run adversarial probes against the actual implementation for either. **Borne out 2026-07-20:**
  T3(c)'s own single-commit board review found three real blocking defects before merge (§4's T3 row);
  T5's later system-level pass found two more, one of them (T4/`SubProcessGrantOrchestrator`'s replay gap)
  in a component this plan had only rated HIGH with a single reviewer — a real instance of this doc's own
  standing worry that a lower-effort/single-reviewer gate can still miss a genuine security defect in
  authority-granting logic.
- **T1 has no reason to wait** — cheap, mechanical, unblocks nothing else technically but is a clean
  quick win and removes one more thing from the deprecating `org.apache.river.api.security` namespace.
- This SOW's T4 has a real external dependency (UDS SOW §12 point 3's subprocess-spawning wiring) that
  is itself unscoped as a concrete task yet — don't let T4 block T1–T3 from proceeding, but don't
  consider this SOW "done" until that integration actually works end-to-end, not just in isolation.
  **Resolved 2026-07-20:** that dependency (`SOW-Smart-Proxy-Isolation-Wiring.md`'s T1-T4) landed, and
  T4's end-to-end integration was confirmed against the real chain, not just the stub — see §4's T4 row.
  **What is still not true, and must not be conflated with the above:** "integration works end-to-end"
  describes the mechanism under test/board-review conditions, not a deployed one — zero production code
  anywhere in the repo currently constructs a `SubProcessGrantOrchestrator`/`SubProcessLocalPolicyAdmin`
  (confirmed by repo-wide grep, multiple reviewers, 2026-07-20), and the real OS-process launcher this
  whole chain assumes (`SubProcessLauncher`) is still `UnsupportedSubProcessLauncher` — see
  `SOW-Smart-Proxy-Isolation-Remaining-Work.md` §"Open items carried forward" for the full list.

---

## 5. Open questions, not resolved here

1. **Resolved by §2's transport refinement, no longer open**: `SubProcessDynamicPolicy` does not reuse
   `RemotePolicyService`'s whole-set-replace shape at all — it rides the client's own per-proxy local
   delegate stub as an additional implemented interface. Kept here struck-through-in-spirit as a record
   of the earlier framing this superseded, for anyone auditing the design history.
2. **Resolved 2026-07-18 by §2's `SubProcessAdministrable`/`PolicyAdmin` refinement — kept here, struck-
   through-in-spirit, as a record of what the transport-only refinement (2026-07-17) left open.** The
   original narrowing ("whoever legitimately holds the client-side stub" as an object-reference boundary)
   was itself identified as a convention, not an enforced mechanism (Board Guidance G4) — it gave no
   answer for how the *subprocess* verifies an incoming call is genuinely from trusted client-side code
   rather than the hosted proxy or a maliciously-constructed stub. The `PolicyAdmin` proxy's own
   `MethodConstraints`-based admin-principal authentication now answers this directly: the subprocess
   never trusts "which interface the caller declared," only whether the caller can authenticate as the
   orchestrating admin principal on that specific proxy's endpoint. See §2 for the full mechanism and its
   residual-hazard note.
3. **Partially resolved 2026-07-20, still not fully closed.** A subprocess's grant *can* now be updated
   after initial application — T5's Finding 1 fix gave `SubProcessLocalPolicyAdmin.grant()` supersession
   semantics (a narrower re-verdict for the same target now cancels the earlier broader grant rather than
   leaving it live until its own lease TTL), and T5's Finding 2 fix gave `SubProcessGrantOrchestrator` a
   freshness/anti-replay gate so a stale re-application can't silently reinstate an expired ceiling. What
   remains genuinely open: there is still no explicit *lease-renewal* operation distinct from "apply a
   new verdict" — a legitimately still-valid grant approaching its lease TTL has no update path other
   than a fresh `applyVerdictCeiling` call carrying a new, later-timestamped, signed `RegistryVerdict`;
   whether that is an acceptable permanent answer (re-verdict-to-renew) or whether a dedicated
   lightweight renewal operation is worth adding is not decided here.

---

*Status: LANDED (T1-T5), documentation closeout in progress (T6) — see the status line at the top of
this document and each task row in §4 for what changed and when. This document's original planning
prose (design rationale, sequencing, open questions) is left largely intact below the landed-status
annotations, per this document set's own convention of recording design history rather than deleting
superseded framing — the annotations, not silent edits, are what make the landed parts distinguishable
from the original plan.*
