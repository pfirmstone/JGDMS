# DESIGN — Agent Checkpoint & Notify-User Escalation (Phase 2)

*Design artefact for `SOW-AI-Agent-Authority-Support.md` §3 Phase 2. Filed 2026-06-16;
revised 2026-06-17. Read-only design pass: NO Java source modified, NO build/test run.
This proposes the trusted human-gated escalation surface; it is not yet implemented.*

*Revision 2026-06-17 (post-review). Two structural changes folded in:*
*(a) **T3 one-shot** now distinguishes **atomic** permissions (revoke/TTL suffices) from
**capability-returning** ones (`SocketPermission`→live `Socket`, `FilePermission`→open
stream), whose escaped references survive a grant `revoke()`. Those must be approved as a
`DelegatePermission` exercised through a method-guard delegate, so revoke/expiry severs the
escaped reference on its next use — and revoke must call `CombinerSecurityManager.clearCache()`.
This is the SOW's flagged "revocation propagation across cached" item (§1, §3, §5, §7).*
*(b) **Approver authentication** is in-scope: T2 holds only if the install runs as the
authenticated approver `Subject`, so `CheckpointAuthority` must return one and the install is
`Subject.callAs(approver)`-wrapped — an APPROVE without it is a DENY (§2, §4, §5; resolves Q5).*
*(c) **The remaining §6 questions are resolved** (§6): per-Permission approval (subset install),
a fixed/code-reviewed renderer catalogue that canonicalises true scope, ship Option (ii) first,
audit as a core emitted deliverable (distinct from `polpAudit`), and an ask-time
`CheckpointPermission` class grammar symmetric with the install ceiling. The `CheckpointAuthority`
SPI now returns the approved `Permission` subset rather than a binary decision.*

**Companion documents**
- SOW: `docs/SOW-AI-Agent-Authority-Support.md` (§3 Phase 2, §4–5)
- Review: `docs/agent-authority-code-review-2026-06-14.md` (Q-D: pattern unbuilt)
- Phase 3 (built): `docs/DESIGN-LeasedPermissionGrant.md`; `LeasedPermissionGrant`,
  `LeasedDelegation` on trunk (e1e89a47d, cc61cd0be)
- Memory: `jgdms-ai-agent-authority.md`

**Grounding (verified this pass).** Greppning both the JGDMS and DirtyChai authorization
trees for `checkpoint|notify.?user|escalat|approv|trusted.path` returns **no** existing
construct — the notify-user channel and trusted checkpoint are entirely greenfield, as the
review (Q-D) said. The primitives they build on **do** exist: conjunctive `∧ JwtPrincipal`
matching (`PrincipalGrant.implies` `containsAll`), `callAs` live co-presence
(`AccessController.getContext` injects scoped subjects), DynamicPolicy GC-scoping for
one-shot grants, the `GrantPermission` install ceiling, and now `LeasedDelegation` /
`LeasedPermissionGrant` for bounded grants.

---

## 1. Problem & threat model

An agent running in its baseline **playpen** reaches an operation beyond its baseline
authority (write to prod, spend, touch a restricted resource). It must be able to **ask**
the user and, only on approval, act — **once**, for that operation. Per the SOW the agent
*can always ask, but can never act beyond the playpen without the answer*.

The crux is **trust of the request surface**: the agent controls its own prose, so a human
who approves the agent's *description* ("update a config file") can be socially engineered
into granting something else (`FilePermission "/etc/-","write"`). Therefore:

> **Invariant T1 — approve ground truth, not prose.** The human approves the *actual
> `Permission`(s)* rendered by the trusted policy layer. Any agent-supplied text is
> advisory and clearly marked as untrusted.

> **Invariant T2 — the human is bounded.** Approval installs authority through the policy,
> so it is capped by the approver's own `GrantPermission` ceiling (a phished user cannot
> approve beyond the admin's ceiling). This falls out of the existing install guard; no new
> mechanism.

> **Invariant T3 — one-shot, not a mode.** An approved escalation authorises a single
> operation, never a standing capability. This holds trivially for **atomic** permissions
> (the check completes, nothing escapes). For **capability-returning** permissions a grant
> `revoke()` does **not** sever a reference already acquired in the window — the capability
> escapes and keeps working with no further check. Such escalations must be approved as a
> `DelegatePermission(candidate)` exercised through a method-guard delegate, so revoke/expiry
> denies the escaped reference on its next method call (§3, §5). Revoke must also invalidate
> the SecurityManager's result cache (`CombinerSecurityManager.clearCache()`), or a revoked
> grant is still served from cache.

> **Invariant T4 — the agent cannot self-approve or self-extend.** The approval decision
> and the renewal credential never reach the agent (cf. `LeasedDelegation` renewal-ownership
> rule). The agent only ever receives the *effect* of an approved grant.

## 2. Scope boundary — what JGDMS owns vs. the deployment

The human-facing **transport** (how the user is prompted: CLI, mobile push, chatops, an
operator console) is a deployment concern and is **out of scope** as a concrete
implementation — it is an SPI the deployer plugs in. JGDMS owns the **trust primitives**:

| In scope (this design) | Out of scope (deployment plugs in) |
|---|---|
| `EscalationRequest` value object (the structured ask = exact `Permission[]` + agent id) | the actual notify channel / UI |
| trusted **rendering** of the request for human display | how/where it is displayed |
| `CheckpointAuthority` **SPI** (approve/deny boundary) | the human decision transport impl |
| approval → **one-shot install** of exactly the approved permissions, ceiling-bounded | — |
| the **authenticated approver `Subject`** the decision is bound to — T2 needs the install to run as it | the *UI* by which the human authenticates |
| **emission of an immutable audit record** of every request + decision (§6-4) | where the record is stored / shipped |
| `CheckpointPermission` (the baseline right to *request*, scoped per §6-6) | — |

## 3. Key design decision — how an approved escalation is realised

Two mechanisms satisfy "escalation = baseline space ∧ user authority, one-shot":

**Option (i) — live `callAs` co-presence.** Run the approved operation inside the user's
`Subject.callAs` scope; the user `JwtPrincipal` becomes present for the dynamic scope of
that call, so a standing `principal AgentSvid ∧ JwtPrincipal { perms }` grant matches *only*
during the call. No new grant is installed at approval time; authority exists strictly while
the user-scoped call runs. Purest per the model, but requires the user's `Subject` to be
present and to *drive/wrap* the operation — i.e. the user is genuinely co-executing.

**Option (ii) — short-TTL one-shot `LeasedDelegation` (recommended default).** On approval,
install a `LeasedDelegation` of *exactly the approved `Permission[]`* to the **agent** SVID
with a **very short TTL** (operation-scoped, e.g. seconds), and revoke it the moment the
operation completes (TTL is the backstop). This **reuses the Phase-3 primitive unchanged**;
it decouples approval from execution timing (the agent may be mid-async-task, the user need
not co-drive), and the dead-man switch + immediate `revoke()` give one-shot semantics.

**Recommendation: (ii) as the default**, because async agents are the whole point and (ii)
needs no user co-execution; offer (i) for the case where the user is genuinely driving the
session. (ii) makes the checkpoint a *synchronous, human-gated, single-Permission,
tiny-TTL delegation* — i.e. Phase 2 is Phase 3 + a human gate + trusted rendering.

**Atomic vs capability-returning permissions (the limit of one-shot).** Both (i) and (ii)
bound *the grant*, not *references the grant lets the agent acquire*. For an atomic permission
that is enough. For a capability-returning permission the agent can acquire a live reference
(socket, stream) within the window that outlives revoke/TTL — the classic reference-escape
problem `DelegatePermission` was built for. For those, the approved permission must be a
`DelegatePermission(candidate)` exercised through a method-guard delegate (Li Gong pattern):
each use re-checks the revocable delegate, so the escaped reference dies when the grant is
revoked or the lease expires. With that wrap, (i) and (ii) converge — (i) scopes authority to
the call's dynamic extent (the `JwtPrincipal` leaves the context on return), (ii) to the lease,
and in both the method guard severs escaped references. The install side reuses
`LeasedDelegation`/`callAs` unchanged; the *resource* side needs the delegate guard, and
`revoke()` must call `clearCache()`. This is the SOW's open item "revocation propagation across
cached".

## 4. Components (signatures only — not a build)

```java
package org.apache.river.api.security;

/** The structured escalation ask. Immutable. The Permissions are ground truth (T1);
 *  justification is advisory, agent-supplied, and rendered as untrusted. */
public final class EscalationRequest {
    public EscalationRequest(Principal agent, Permission[] requested, String justification);
    public Principal agent();
    public Collection<Permission> requested();   // defensive copy
    public String justification();                // advisory only
}

/** Trusted rendering of a request for human display (T1). Renders well-known Permission types
 *  to plain descriptions that CANONICALISE the true scope (resolve relative paths, expand -/*
 *  breadth) rather than pretty-printing the literal; UNKNOWN/agent-defined Permission classes
 *  are rendered raw (class + name + actions) and flagged "unrecognised", never via a
 *  potentially-deceptive overridden toString(). The catalogue of known types is fixed and
 *  code-reviewed, not policy-controlled (§6-2). */
public interface EscalationRenderer {
    String render(EscalationRequest request);     // for the human surface
}

/** The boundary the deployment implements: deliver the rendered request to the human and
 *  return their decision. JGDMS provides the request, rendering and install; the deployer
 *  provides the transport. MUST surface the rendered (trusted) text, not justification alone. */
public interface CheckpointAuthority {
    /** Deliver the rendered (trusted) text to a human, authenticate them out-of-band, and
     *  return the APPROVED SUBSET of request.requested() (per-Permission, §6-1) together with
     *  the authenticated approver Subject. An empty approved set is a denial. T2 requires the
     *  install to run as that Subject; a non-empty approval whose approver() is null MUST be
     *  treated as empty (deny). The transport supplies the UI; the authenticated approver
     *  identity is required. */
    Outcome decide(EscalationRequest request, String renderedTrustedText);

    /** The approved subset + the authenticated principal it is bound to. */
    final class Outcome {
        public Outcome(Collection<Permission> approved, javax.security.auth.Subject approver);
        public Collection<Permission> approved();        // subset of requested(); empty = deny
        public javax.security.auth.Subject approver();   // non-null required if approved() non-empty
    }
}

/** The coordinator the agent calls. Holds no agent-reachable approval state (T4). */
public final class Checkpoint {
    public Checkpoint(RevocablePolicy policy, CheckpointAuthority authority,
                      EscalationRenderer renderer, Duration oneShotTtl, LeaseProvider leases);
    /** Agent-callable: request escalation. Requires the caller to hold CheckpointPermission
     *  whose grammar admits the requested permission classes (ask-time scope, §6-6). For an
     *  approved non-empty subset with an authenticated approver, installs — under
     *  Subject.callAs(approver) so the approver's GrantPermission ceiling caps it (T2) — a
     *  one-shot short-TTL LeasedDelegation of exactly the APPROVED subset to request.agent(),
     *  and returns a handle the COORDINATOR holds. An empty approved set, or a non-empty one
     *  without an authenticated approver, returns empty. For a capability-returning permission
     *  the installed grant is a DelegatePermission so revoke/expiry severs escaped references
     *  (T3). Emits an immutable audit record of the request + decision either way (§6-4). The
     *  returned handle is NOT given to the agent. */
    public Optional<LeasedDelegation> request(EscalationRequest request);
}

/** Baseline capability: the right to *ask* (notify-user). Included in the playpen. Its target is
 *  an ask-time class scope — "request:<permission-class>" (e.g. "request:java.io.FilePermission")
 *  — bounding which permission CLASSES the agent may even surface a request for (§6-6), symmetric
 *  with T2's install-time ceiling. Holding it lets an agent call Checkpoint.request(...) for those
 *  classes; it does NOT confer any escalated authority — only the ability to request it. */
public final class CheckpointPermission extends Permission { /* target e.g. "request:java.io.FilePermission" */ }
```

**Where the ceiling bites (T2).** `Checkpoint.request` installs via `RevocablePolicy.grant(...)`
**as the authenticated approver `Subject`** returned by `CheckpointAuthority` — JGDMS wraps the
install in `Subject.callAs(approver, ...)`, so the existing `GrantPermission` guard caps it
against the *approver's* ceiling. This is why authenticated approver identity is in-scope (§2):
were the transport to return a bare APPROVE and the install run in the agent's or a generic
context, T2 would silently void. An APPROVE without an authenticated approver principal is
therefore treated as DENY. (Same guard exercised by `AgentDelegationCeilingTest`.)

## 5. Trust analysis / anti-spoofing

- **Prose spoofing (T1):** mitigated by rendering the real `Permission[]`. `justification`
  is shown labelled untrusted. The renderer never trusts a Permission subclass's own
  `toString()` for unknown types — it renders class/name/actions and flags them, so an
  agent-defined `Permission` with a deceptive `toString()` cannot masquerade as a benign one.
  Even *known* types are canonicalised, not pretty-printed (resolve relative paths, expand
  `-`/`*` breadth), so a deceptive name (`"/app/../../etc/passwd"`, `"<<ALL FILES>>"`) shows its
  true reach (§6-2).
- **Over-ask:** the human sees every requested Permission and approves a subset (per-Permission,
  §6-1); only the approved subset is installed. The install ceiling (T2) is the hard backstop
  even if the human over-approves. An out-of-grammar request (§6-6) never reaches the human.
- **Replay / standing-mode (T3):** one-shot TTL + immediate post-op `revoke()`; the grant is
  agent-scoped and dies on the dead-man switch. *Capability-returning permissions:* a revoke
  removes the grant but not a reference already acquired in the window, so those are approved
  as a `DelegatePermission` through a method guard and revoke calls `clearCache()` — the
  escaped reference is denied on its next use (§1, §3). Atomic permissions need neither.
- **Self-approval/extension (T4):** the agent never receives the `CheckpointAuthority`, the
  decision, the lease, or the returned handle. It calls `request(...)` and either gets the
  *effect* of a new short grant or nothing.
- **Confused-deputy on the channel:** the transport must authenticate the approver out-of-band
  and return their `Subject`; JGDMS binds T2 to that authenticated principal by running the
  install as it (§4). JGDMS does not assert the *channel/UI* is authentic — that UX is the
  deployer's — but the *authenticated approver identity* is a required SPI output, not optional
  (an APPROVE without it is a DENY).

## 6. Decisions (resolved 2026-06-17)

1. **Approval is per-Permission, not all-or-nothing.** The human may approve a subset of the
   requested permissions; the install is of exactly that subset. All-or-nothing invites the
   agent to bundle a needed-benign permission with a dangerous one and have the human approve
   the bundle; per-Permission keeps the surface least-privilege-honest. The T2 ceiling remains
   the hard backstop. (Reflected in `CheckpointAuthority.Outcome.approved()`, §4.)
2. **Renderer catalogue is fixed and code-reviewed, not policy-controlled** (initially), and it
   **canonicalises** the true scope rather than pretty-printing the literal. Unknown/agent-defined
   types are raw+flagged (T1); *known* types still deceive through their name/actions
   (`FilePermission "/app/../../etc/passwd"`, `"<<ALL FILES>>"`, wildcard hosts,
   `PropertyPermission "*"`), so the renderer resolves relative paths, expands `-`/`*` breadth and
   shows effective reach. Policy-controlling the catalogue is deferred — it adds a manipulation
   surface for marginal gain.
3. **Ship Option (ii) first; (i) later.** Async agents are the whole point and (ii) needs no user
   co-execution; (i) (live `callAs` co-presence) is the cleaner model when the user genuinely
   co-drives and can follow once there is demand. With the §3 method-guard wrap they converge.
4. **Audit is a core deliverable, not deferred.** `Checkpoint` **emits** an immutable record of
   every request + decision (agent, requested `Permission[]`, approved subset, approver principal,
   timestamp, outcome). This ledger is the governance artifact and the reason a checkpoint exists;
   it is distinct from `polpAudit` (which generates least-privilege policy *files*) and must not be
   folded into it. Only the record's storage/transport is a deployer SPI; emission is in-scope
   (§2/§4).
5. **Approver-context plumbing — RESOLVED:** the install runs as the authenticated approver
   `Subject` returned by `CheckpointAuthority`, wrapped in `Subject.callAs`; an approval without
   an authenticated approver installs nothing. Authenticated approver identity is in-scope
   (§2/§4); only the authentication UX is the deployer's.
6. **`CheckpointPermission` grammar is an ask-time class scope** — `"request:<permission-class>"`
   (e.g. `"request:java.io.FilePermission"`) — symmetric with T2's install-time ceiling: it bounds
   which permission *classes* the agent may even surface a request for. Two ceilings (ask-time +
   install-time) cut the human's exposure and blunt approval fatigue; an out-of-grammar request is
   denied before it reaches the human. (Frequent in-grammar checkpoints are themselves a signal the
   Phase-1 playpen is mis-scoped.)

## 7. Test contract (for the build phase)

- **C1** Agent without `CheckpointPermission` cannot call `request` (denied).
- **C2** `request` with an empty approved set (deny) installs nothing; agent still bounded to
  the playpen.
- **C3** `request` with an approved subset installs a grant for exactly that subset (not the full
  `requested()`) to the agent; the agent domain implies the approved permissions and not the
  denied ones; a different principal implies neither.
- **C4a** (atomic) Approved escalation is one-shot: after TTL (test clock) or post-op
  `revoke()`, the agent no longer implies the escalated permission; playpen survives.
- **C4b** (capability) For a capability-returning permission approved as a `DelegatePermission`
  through a method-guard delegate, a reference acquired during the window is denied on its next
  method call after `revoke()`/expiry + `clearCache()` — the escaped reference is severed, not
  merely the grant.
- **C5** Install ceiling (T2): with an active SecurityManager, an APPROVE for permissions
  beyond the approver's `GrantPermission` is refused (`SecurityException`) even though the
  human said yes. (Mirrors `AgentDelegationCeilingTest`.)
- **C6** Renderer (T1): (a) a request carrying an agent-defined `Permission` with a deceptive
  `toString()` renders raw class/name/actions + "unrecognised", not the deceptive text; (b) a
  *known* type with a deceptive name (`FilePermission "/app/../../etc/passwd"`) renders its
  canonicalised true scope, not the literal (§6-2).
- **C7** The agent never obtains the returned `LeasedDelegation` handle (structural: `request`
  returns to the caller; the coordinator retains control) — assert the agent path cannot
  renew.
- **C8** (T2 / approver auth) A non-empty approved set returned with a null approver `Subject`
  installs nothing; and the ceiling check (C5) is bound to the *authenticated approver's*
  `GrantPermission`, not the agent's.
- **C9** (audit, §6-4) Every `request` — approved, partially approved, or denied — emits exactly
  one immutable audit record carrying agent, requested set, approved subset, approver principal
  and timestamp; emission does not depend on the deployer storage SPI.
- **C10** (ask-time grammar, §6-6) An agent whose `CheckpointPermission` does not admit a
  requested permission's class is denied before the request reaches the `CheckpointAuthority`
  (the human is never prompted).

---

*End of design.*
