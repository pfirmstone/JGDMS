# Scope of Work — `SubProcessDynamicPolicy`: verdict-driven, cross-process dynamic permission grants

- **Drafted:** 2026-07-17.
- **Status:** DRAFT — task breakdown for review, no implementation started.
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
  What's **not yet reconciled**: §2 below describes the transport as "an additional interface on the
  client's own local delegate dynamic proxy stub" — that framing assumes a client *holding a proxy* to
  a remote object, which fits the smart-proxy case but not obviously the filter case (a service holds a
  handle to *its own spawned filter sidecar*, not a downloaded proxy over a business interface). Whether
  the filter tenant reuses the same "ride an existing per-tenant channel" shape with the service's own
  sidecar-handle standing in for the client's stub, or needs a materially different transport, is real,
  unresolved design work — flagged here rather than assumed away. Expected to be minimal in practice:
  STD-009 §8.4's "zero ambient authority" means the filter's own grant is expected to be near-empty, so
  getting this exactly right matters less operationally than it does for the smart-proxy case, but the
  mechanism should still be designed once, correctly, for both tenants rather than forked.

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

- **Refined transport decision (Peter, 2026-07-17), superseding the "separate remote service" framing
  below: `SubProcessDynamicPolicy` is one of the interfaces implemented by the *local delegate dynamic
  proxy* itself** — the same client-side `java.lang.reflect.Proxy` stub (§ the UDS SOW's "local
  interfaces suffice" decision) that already forwards the smart proxy's own business-interface calls
  over UDS to the isolated subprocess. It is **not** a second, standalone JERI-remote service with its
  own distinct wire protocol and endpoint. `Proxy.newProxyInstance` already supports one dynamic proxy
  instance implementing multiple interfaces simultaneously — the client's per-proxy stub gets built with
  the smart proxy's own remote interface(s) *plus* a `SubProcessDynamicPolicy`-shaped interface, and
  calls to *either* route through the exact same `InvocationHandler`/UDS-forwarding machinery
  (`AtomicDerInvocationHandler` or its extension) already being built for ordinary calls. This
  eliminates the need for a second wire protocol, a second endpoint registration inside the subprocess,
  and — see below — most of open question 2's authentication problem.
  **A second, equally important benefit (Peter, 2026-07-17): this removes an entire class of "which
  proxy does this grant target" ambiguity by construction.** A subprocess can host multiple proxy
  objects pooled from the same principal (§ the UDS SOW's point 1); if `SubProcessDynamicPolicy` were a
  standalone service, every grant-application call would need an explicit parameter identifying *which*
  hosted object it applies to — precisely the kind of "is this identifier correctly bound/keyed" problem
  that recurred repeatedly across this whole investigation (the `Key` class's endpoint-vs-principal
  mixup, the routing-key corrections). Riding the *same* per-proxy stub/channel that is already
  correctly, structurally scoped to exactly one proxy object removes the need for such a parameter at
  all — the channel itself *is* the target identification, not a value that could be spoofed, mismatched,
  or applied to the wrong hosted object.
  **This directly simplifies the caller-authentication requirement below**: the authorized caller is
  simply whichever trusted client-side code legitimately holds a reference to *that specific proxy's*
  local delegate stub — an ordinary Java object-reference/encapsulation boundary within the client's own
  trusted code, governed by whatever already controls who gets handed that stub, not a new remote
  identity/authentication scheme to design from scratch. The untrusted smart proxy code itself, running
  entirely inside the subprocess, never has access to *its own* client-side stub object at all (that
  object lives in a different process's heap) — it cannot reach this interface through normal means.
- **Caveat this refinement does *not* eliminate, and needs to stay explicit at T3's design time: on the
  *subprocess* side, the object that answers policy-management calls must not be the same object as the
  smart-proxy business object itself**, even though both may be reached over the same UDS
  connection/transport-layer plumbing from the client's single stub. If the subprocess multiplexes
  ordinary business calls and policy-management calls onto literally the same exported object, there is
  a real risk of conflating "the untrusted proxy's own business logic" with "the subprocess's own
  trusted management surface" — e.g. if the smart proxy class itself were made to implement (accidentally
  or via a crafted class) the `SubProcessDynamicPolicy` interface shape, dispatch could route a
  policy-management call to attacker-controlled code, or attacker code could otherwise interfere with
  it. The subprocess-side implementation must dispatch policy-management calls to its own,
  separate, trusted hosting/management object — never to (or through) the smart proxy instance being
  hosted, regardless of what interfaces the client-side stub happens to expose as one bundle.
- **The caller is still a more-trusted party than the subprocess's own bootstrap/hosting code for
  *deciding* the grant** — even with the transport simplification above, the subprocess-side handler
  should not itself decide *what* to grant (that's T2's job, computed elsewhere and carried in the
  call), only apply an already-computed instruction after confirming its own caller-side authenticity
  checks (see the caveat above) — this framing from the original design still holds, just over the
  simplified transport.
- **Grants should be lease-scoped by default**, using the existing `LeasedPermissionGrant`/
  `LeasedDelegation` machinery, so a stale or misbehaving proxy's ceiling can expire or be explicitly
  revoked without a hard subprocess kill.
- **Only the orchestrating party's stub gets the `SubProcessDynamicPolicy` interface bundled in — decided
  (Peter, 2026-07-17), following directly from the UDS SOW §12 point 3(iii) finding that the local
  delegate stub is a per-consumer-JVM construct, not a single universal stub.** A subprocess-hosted proxy
  can legitimately be reached by more than one local delegate stub in more than one process — e.g. the
  orchestrating client that spawned the subprocess and is entitled to administer its policy, versus a
  separate downstream process consuming the same proxy through its own "ServiceAPI" (possibly
  third-party-defined). Each such stub is built independently, using only the interfaces relevant to
  whoever built it. **When a proxy is serialized/exposed to a ServiceAPI-consumer's process, the stub
  built there must not include the `SubProcessDynamicPolicy`-shaped interface at all** — only the stub
  held by the party that actually spawned/owns the subprocess (or is otherwise explicitly entrusted with
  administering it) should ever be constructed with it bundled in. This is not an access-control check to
  add at call time; it is a construction-time decision about which interface set a given stub is built
  with in the first place — consistent with §2's core "the channel itself is the target identification"
  property above, extended one level further: which *interfaces a given channel's stub exposes* is itself
  part of that channel's identity and must not default to "everything available," only to what the
  building process is entitled to and needs.

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
| **T3** · `SubProcessDynamicPolicy` interface + subprocess-side handler | **Revised shape (§2): an additional interface implemented by the client-side local delegate dynamic proxy stub itself** (not a standalone remote service) — the "apply this grant" operation rides the same `InvocationHandler`/UDS-forwarding path already built for the smart proxy's own business calls. On the subprocess side, must: (a) dispatch policy-management calls to the subprocess's own, *separate*, trusted hosting/management object — **never** to or through the hosted smart proxy instance itself, even though both are reached over the same UDS connection (§2's caveat — this is the one thing the transport simplification does *not* solve for free); (b) apply the grant verbatim via the subprocess's own local `DynamicPolicyProvider`/`LeasedDelegation`, performing no independent judgment about *what* to grant; (c) confirm the incoming call is genuinely from the client's own trusted stub-holding code, not something the hosted smart proxy engineered access to. | general-purpose · **XHIGH** (novel security-critical mechanism; same class of risk as BAE's T2/T5 from the companion SOW) | **parallel board**, adversarial-probing mandate — build-and-run real probes attempting to reach the policy-management path from the subprocess's own untrusted smart-proxy code (e.g. via a crafted class shape, or by exploiting object-dispatch confusion between the two interfaces on one exported endpoint), not just design review | T1 (transport relocation may still inform wire conventions), T2 (needs a grant to apply — can stub during development) |
| **T4** · Caller-side wiring | The orchestrating logic that, once a verdict is known for a smart proxy about to run in a given subprocess, computes the grant (T2) and pushes it via `SubProcessDynamicPolicy` (T3) to that specific subprocess. Integrates with whatever spawns/tracks isolated subprocesses (UDS SOW §12 point 3's own task) — this task cannot fully land until that exists, but the caller-side logic itself can be built and tested against a stub subprocess. | general-purpose · **HIGH** | single reviewer + integration test against T3 | T2, T3; full integration blocked on UDS SOW §12 point 3's subprocess-spawning wiring |
| **T5** · Adversarial test pass | Dedicated adversarial probing of the whole T2→T3→T4 chain as a system, not just each piece in isolation — e.g. can the hosted smart proxy itself, given only its own (deliberately narrow) permissions, ever reach or influence `SubProcessDynamicPolicy`'s endpoint; can a grant be replayed or applied to the wrong subprocess; does a lease-scoped grant actually expire/revoke correctly under `LeasedPermissionGrant`'s existing semantics. Same standing brief as this session's BAE adversarial rounds: build and run real probes against the actual implementation, don't accept "the design reads sound." | general-purpose · **XHIGH** | **parallel board** | T3, T4 substantially implemented |
| **T6** · Documentation | Update `SOW-Unix-Domain-Socket-JERI-Transport.md` §12 point 6 to describe the finished mechanism accurately (currently describes the *plan*); note in `SOW-BAE-Timing-Sidechannel-Denial.md` §1b that SM/POLP's permission ceiling inside the isolated subprocess is now concretely sourced from this mechanism, not just asserted to exist. | general-purpose · **MEDIUM** | single reviewer | T1–T5 substantially landed (should describe reality, not aspiration) |

### Sequencing

- **Now, parallel:** **T1** (independent, quick), **T2** (independent design work, the real policy
  decision — start early since it's likely the long pole given it needs explicit sign-off on the
  mapping, not just code review).
- **After T1 (transport) and T2 (something to apply, can stub):** **T3**.
- **After T2, T3:** **T4** — full integration blocked on the UDS SOW's own subprocess-spawning task, but
  the caller-side logic can be built and tested against a stub before that lands.
- **After T3, T4 substantially done:** **T5** — system-level adversarial pass, not just per-component.
- **Last:** **T6**, once the mechanism is real, not planned.

### Notes

- **T2 and T3 are the ones to guard hardest** — T2 because it's a genuine security-policy decision (what
  gets granted, not just how), T3 because it's a novel remote-callable grant-application mechanism, the
  exact shape of component (new authority-granting logic) that produced real, adversarially-found
  bypasses repeatedly in this session's BAE work. Do not let "the design reads sound" substitute for
  build-and-run adversarial probes against the actual implementation for either.
- **T1 has no reason to wait** — cheap, mechanical, unblocks nothing else technically but is a clean
  quick win and removes one more thing from the deprecating `org.apache.river.api.security` namespace.
- This SOW's T4 has a real external dependency (UDS SOW §12 point 3's subprocess-spawning wiring) that
  is itself unscoped as a concrete task yet — don't let T4 block T1–T3 from proceeding, but don't
  consider this SOW "done" until that integration actually works end-to-end, not just in isolation.

---

## 5. Open questions, not resolved here

1. **Resolved by §2's transport refinement, no longer open**: `SubProcessDynamicPolicy` does not reuse
   `RemotePolicyService`'s whole-set-replace shape at all — it rides the client's own per-proxy local
   delegate stub as an additional implemented interface. Kept here struck-through-in-spirit as a record
   of the earlier framing this superseded, for anyone auditing the design history.
2. **Substantially narrowed by §2's transport refinement, not fully closed**: the client-side half of
   "who is authorized to call this" is now just "whoever legitimately holds the client-side stub" — an
   ordinary object-reference boundary, not a new identity scheme. What's still open is the
   **subprocess-side** half: which specific object inside the subprocess is authorized to *answer* the
   policy-management calls (T3's caveat — must be a separate, trusted hosting/management object, never
   the smart proxy instance itself) — and how that object authenticates that an incoming call over the
   shared UDS connection is genuinely the policy-management interface being invoked by trusted
   client-side code, not the ordinary business interface, or a confused/crafted dispatch. Worth a
   concrete design pass at T3's start, not assumed solved by the transport simplification alone.
3. **Does a subprocess ever need its grant *updated* after initial application** (e.g. a verdict changes
   because the `VerdictRegistry` revises it, or a lease needs renewing before expiry) — or is this
   strictly a one-shot "grant once at spawn/first-load time" mechanism? If updates are needed, T3's API
   surface needs an explicit update/renew operation, not just initial-apply — worth deciding at T3's
   design time.

---

*End of DRAFT. No production code was written or modified in producing this document.*
