# DESIGN — Agent Escalation Gate (Human-Gated Notify-User Approval) (Phase 2)

*Design artefact for `SOW-AI-Agent-Authority-Support.md` §3 Phase 2. Filed 2026-06-16;
revised 2026-06-17. Read-only design pass: NO Java source modified, NO build/test run.
This proposes the trusted human-gated escalation surface; it is not yet implemented.*

*Revision 2026-06-17 (post-review). Two structural changes folded in. **NB: point (a) below is
superseded by the 2026-06-18 revision — the atomic/capability split is not the right axis (the
decision cache affects both) and the cache is cleared via the policy, not `CombinerSecurityManager`;
see §3 and the 2026-06-18 block.***
*(a) **T3 one-shot** now distinguishes **atomic** permissions (revoke/TTL suffices) from
**capability-returning** ones (`SocketPermission`→live `Socket`, `FilePermission`→open
stream), whose escaped references survive a grant `revoke()`. Those must be approved as a
`DelegatePermission` exercised through a method-guard delegate, so revoke/expiry severs the
escaped reference on its next use — and revoke must call `CombinerSecurityManager.clearCache()`.
This is the SOW's flagged "revocation propagation across cached" item (§1, §3, §5, §7).*
*(b) **Approver authentication** is in-scope: T2 holds only if the install runs as the
authenticated approver `Subject`, so `EscalationAuthority` must return one and the install is
`Subject.callAs(approver)`-wrapped — an APPROVE without it is a DENY (§2, §4, §5; resolves Q5).*
*(c) **The remaining §6 questions are resolved** (§6): per-Permission approval (subset install),
a fixed/code-reviewed renderer catalogue that canonicalises true scope, ship Option (ii) first,
audit as a core emitted deliverable (distinct from `polpAudit`), and an ask-time
`EscalationGatePermission` class grammar symmetric with the install ceiling. The `EscalationAuthority`
SPI now returns the approved `Permission` subset rather than a binary decision.*

*Revision 2026-06-18 (post-code-review against the engine-swap tree; lens: does the
mechanism* attenuate-never-widen, *compose with the leased dead-man switch, and survive
in-flight authority across* restore *or* expiry, *and is each safety property structural
rather than a bolted-on guard). The install-path attenuation is confirmed structural
(approved&nbsp;&sube;&nbsp;requested&nbsp;&sube;&nbsp;approver-ceiling; `LeasedDelegation`
installs an* agent-principal *subset grant, not user impersonation; `getPermissions()` is
`final` so the wrapper cannot widen the ceiling). Seven corrections fold in; the four
load-bearing ones:*
*1. **The agent-callable ask must not return the live handle** (resolves a contradiction in
the old §4/C7). `EscalationGate.request` previously returned `Optional<LeasedDelegation>` to its
caller while also being "agent-callable" — so the agent, as caller, received `lease()`,
`renew()` and `revoke()`, defeating T4. T4 is now enforced **by reachability**: the ask
returns an opaque receipt; the live handle is delivered to a separate coordinator sink the
agent has no reference to (§4).*
*2. **One-shot needs cache invalidation for ATOMIC permissions too** (corrects the old §5
"atomic needs neither"). Under the production authorization SM (a `CachingSecurityManager`),
`checkPermission` memoises every* passed *check per-context with a ~10&nbsp;s TTL, so a
post-op `revoke()` that does not clear the cache re-authorises the same atomic operation for
up to the cache TTL. The escape is the* decision cache *(atomic + capability), distinct from
the* escaped object reference *(capability only). Revoke must invalidate the cache for both;
the method-guard `DelegatePermission` is the* additional *fix capability permissions need
(§1 T3, §3, §5).*
*3. **Cache invalidation routes through the policy, not a concrete SM.** The old text named
`CombinerSecurityManager.clearCache()` — a class that no longer exists in this tree (the
concrete SMs were removed; only the `CachingSecurityManager` interface remains, and
`clearCache()` is guarded "policy-only"). `EscalationGate.revoke` invalidates the cache via
`RevocablePolicy`/`DynamicPolicyProvider.refresh()`, which already clears it through the
`CachingSecurityManager` SPI (§3, §5).*
*4. **One-shot is made structurally single-use, and restore semantics are stated.** The old
one-shot rested on TTL + post-op revoke (temporal); nothing stopped a snapshotted/restored or
clock-rewound agent from re-presenting a prior approval. Each approval now carries a
single-use id consumed on first install; a replayed `Outcome` installs nothing, and an
in-flight escalation explicitly does **not** survive an agent restore — it must be
re-requested and re-approved (§3, §6-7). Naming: the coordinator type is named `EscalationGate`
(with `EscalationAuthority`, `EscalationGatePermission`); its prior name read as snapshot/restore,
which is exactly the sense this design does **not** mean (§6-10, applied 2026-06-18b).*
*Plus three smaller: the ask carries permission* tokens *not live `Permission` instances
(no agent-defined class is loaded in the trusted approval path, §4/§6-8); T2 binds to the
approver only if approver authority is modelled as principal-scoped `GrantPermission` grants
(§5); and tiny-TTL Option (ii) needs a co-located (zero-skew) landlord or a skew-tolerant
floor, per the Phase-3 primitive's own clock-skew limitation (§3). Test contract grows to
C1–C13 (§7).*

*Revision 2026-06-18b (cache-coherence model, converged in design dialogue). Correction (2) above
said `revoke()` must clear the SM decision cache. That is replaced by a cleaner, structural model:
the policy exposes two per-`(PD, Permission)` relations — `implies` (stable authority; feeds the
decision cache **and** the `SecurityPolicyWriter`/polpAudit recorder) and a new `impliesOnce`
(grant-only probe for one-shot authority). One-shot grants are invisible to `implies` and reported
only by `impliesOnce`, which the SM consults **after** a failed `implies`, outside the protected
per-PD recording method. One relation split yields three properties at once: a one-shot is never
cached, never **recorded into generated policy** (the worst failure mode — a recorded escalation
becomes a permanent baseline grant), and — because it can't leak into the floor — safe to express
as a `DelegatePermission` (the capability one-shot, now hack-free). `clearCache()`/`refresh()` keeps
its existing role for the reusable-revocable class only (e.g. a smart-proxy `DelegatePermission`
discarded mid-life). New §3 subsection "One-shot authority: `implies` vs `impliesOnce`"; §1 T3, §5,
§6-9 updated; tests grow to C1–C16. The `impliesOnce` addition to `java.security.Policy` (default
`false`) + the SM post-check land in DirtyChai's java.base.*

**Companion documents**
- SOW: `docs/SOW-AI-Agent-Authority-Support.md` (§3 Phase 2, §4–5)
- Review: `docs/agent-authority-code-review-2026-06-14.md` (Q-D: pattern unbuilt)
- Phase 3 (built): `docs/DESIGN-LeasedPermissionGrant.md`; `LeasedPermissionGrant`,
  `LeasedDelegation` on trunk (e1e89a47d, cc61cd0be)
- Memory: `jgdms-ai-agent-authority.md`

**Grounding (verified this pass).** Greppning both the JGDMS and DirtyChai authorization
trees for `checkpoint|notify.?user|escalat|approv|trusted.path` returns **no** existing
construct — the notify-user channel and trusted escalation gate are entirely greenfield, as the
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

> **Invariant T3 — one-shot, not a mode, and single-use.** An approved escalation authorises a
> single operation, never a standing capability. Two distinct things can outlive the grant, and
> both must be cut on revoke/expiry — they are different escape *channels*, not different
> permission *categories*:
> - **The decision cache** (affects atomic **and** capability permissions). The production
>   authorization SM (`CachingSecurityManager`) memoises every *passed* `checkPermission` per
>   execution context and serves a cache hit without re-consulting the policy. A grant that goes
>   void must not keep being served from cache. Rather than invalidate the cache on revoke (which
>   never fires on the eventless *expiry* path), one-shot decisions are **never cached in the first
>   place**: the policy reports a one-shot only via `impliesOnce`, which the SM consults *after* a
>   failed `implies` and never caches (§3). Expiry then denies on the very next check — no trigger
>   needed.
> - **An escaped object reference** (capability-returning permissions only). A void grant does not
>   sever a `Socket`/stream already acquired in the window — the reference keeps working with no
>   further check. Such escalations are approved as a `DelegatePermission(candidate)` exercised
>   through a method-guard delegate, so each use re-checks; because the one-shot is reported by
>   `impliesOnce` (never cached), that per-use re-check re-reads lease liveness every time and
>   denies the escaped reference the instant the grant voids (§3).
>
> "Single operation" is enforced **structurally**, not only by TTL: each approval carries a
> single-use id consumed on first install (§3), so a replayed or restored approval installs
> nothing.

> **Invariant T4 — the agent cannot self-approve or self-extend.** The approval decision, the
> renewal credential (`Lease`), and the returned `LeasedDelegation` handle never reach the agent
> (cf. the `LeasedDelegation` renewal-ownership rule). This is enforced **by reachability**: the
> agent-callable ask returns only an opaque receipt, and the live handle is delivered to a
> separate coordinator sink the agent has no reference to (§4). The agent only ever receives the
> *effect* of an approved grant — never an object it could use to renew, revoke, or inspect it.

## 2. Scope boundary — what JGDMS owns vs. the deployment

The human-facing **transport** (how the user is prompted: CLI, mobile push, chatops, an
operator console) is a deployment concern and is **out of scope** as a concrete
implementation — it is an SPI the deployer plugs in. JGDMS owns the **trust primitives**:

| In scope (this design) | Out of scope (deployment plugs in) |
|---|---|
| `EscalationRequest` value object (the structured ask = exact permission **tokens** `(class,name,actions)` + agent id; no live agent-defined `Permission` instance crosses into the trusted path, §6-8) | the actual notify channel / UI |
| trusted **rendering** of the request for human display | how/where it is displayed |
| `EscalationAuthority` **SPI** (approve/deny boundary) | the human decision transport impl |
| approval → **single-use one-shot install** of exactly the approved permissions, ceiling-bounded, with a cache-invalidating revoke (§3) | — |
| the **authenticated approver `Subject`** the decision is bound to — T2 needs the install to run as it | the *UI* by which the human authenticates |
| **emission of an immutable audit record** of every request + decision (§6-4) | where the record is stored / shipped |
| `EscalationGatePermission` (the baseline right to *request*, scoped per §6-6) | — |

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
*Clock-skew precondition:* a seconds-scale TTL is only safe when the lease landlord is
co-located with the holder (zero skew) or uses a skew-tolerant floor. Per the Phase-3
primitive's own limitation (`DESIGN-LeasedPermissionGrant.md` §1.2), a short TTL across
un-synchronised clocks is born-void on arrival or flaps the grant void; the holder trusts its
local clock and does not compensate. The common single-JVM escalation gate has zero skew; a
federated landlord needs tight NTP or a TTL well above worst-case skew.

**Recommendation: (ii) as the default**, because async agents are the whole point and (ii)
needs no user co-execution; offer (i) for the case where the user is genuinely driving the
session. (ii) makes the escalation gate a *synchronous, human-gated, single-Permission,
tiny-TTL delegation* — i.e. Phase 2 is Phase 3 + a human gate + trusted rendering.

**The two escape channels (the limit of one-shot).** Both (i) and (ii) bound *the grant*, not
everything the grant lets escape. Two independent channels would let authority outlive the void
grant, and each has a structural fix:

1. **The decision cache** — affects **every** permission, atomic or capability. The production
   `CachingSecurityManager` memoises passed checks per execution context and serves hits without
   re-consulting the policy, so a cached pass survives the grant going void. The fix is **not to
   cache one-shot decisions at all** (see the dedicated subsection below): the policy reports a
   one-shot only via `impliesOnce`, the SM consults it after a failed `implies`, and that result
   is never cached. There is then no window and no trigger to manage — expiry or revoke denies on
   the very next check.
2. **An escaped object reference** — capability-returning permissions only (`SocketPermission`→live
   `Socket`, `FilePermission`→open stream). Not caching cannot reach a reference already handed
   out, because *using* it bypasses `checkPermission` entirely. For those, the approved permission
   is a `DelegatePermission(candidate)` exercised through a method-guard delegate (Li Gong pattern):
   each *use* re-checks. Because the one-shot is reported by `impliesOnce` (never cached), that
   per-use re-check re-reads lease liveness every time, so the escaped reference dies the instant
   the grant voids.

With both, (i) and (ii) converge — (i) scopes authority to the call's dynamic extent (the
`JwtPrincipal` leaves the context on return), (ii) to the lease; in both, the non-cached
`impliesOnce` cuts the decision channel and the method guard cuts the reference channel. The
install side reuses `LeasedDelegation`/`callAs` unchanged. This is the SOW's open item "revocation
propagation across cached", resolved into the two channels above.

**One-shot authority: `implies` vs `impliesOnce`.** The cacheability of a decision is a property of
the *grant* that satisfies it, which the SM cannot read off the requested permission — and the
policy reasons strictly per `(ProtectionDomain, Permission)`, never per context (the
`AccessControlContext` is assembled and walked by the SM, never handed to the policy). So the signal
is carried by **splitting the policy's grant-evaluation into two per-`(PD, Permission)` relations**:

- `implies(PD, perm)` — granted by **stable** authority. One-shot grants do **not** contribute.
- `impliesOnce(PD, perm)` — granted, but only by a **one-shot** grant (a `LeasedDelegation` the
  EscalationGate flagged one-shot). New method on `java.security.Policy`, default `return false`,
  overridden by `DynamicPolicyProvider`; callable from the java.base SM (no `RevocablePolicy`
  exposure). The grant declares one-shot; the policy sorts it into this bucket.

The SM keeps its existing protected per-`(PD, perm)` check (the one `SecurityPolicyWriter`/polpAudit
hooks to **record** exercised permissions) **unchanged**, and simply **adds** an `impliesOnce`
consultation *after* a failed `implies`, outside that recording method. Per PD a domain grants if
`implies || impliesOnce`; the context is granted iff every PD grants; the context is **cacheable
iff no PD needed `impliesOnce`**. Sketch:

```
try {
    delegateContext.checkPermission(perm);   // implies-only; one-shots invisible; recorded; cacheable
    checkedPerms.add(perm);                   // passed on stable authority → cache
    return;
} catch (AccessControlException e) {
    if (everyDomainGrantsVia(implies || impliesOnce, perm)) return;  // granted one-shot, DON'T cache
    throw e;                                                         // genuine denial
}
```

One relation split delivers three properties at once:

1. **Never cached.** A one-shot is reported only by `impliesOnce`, which the SM never writes to the
   cache. Zero overhead on the stable path — `impliesOnce` is consulted only when `implies` fails
   (a one-shot, or a genuine denial that is already throwing). Passive lease *expiry* needs no
   trigger: `impliesOnce` re-reads liveness every call and the next check denies.
2. **Never recorded into generated policy** — the most important property. `SecurityPolicyWriter`
   (polpAudit) is *observe-only*: it records every exercised permission and filters already-granted
   ones at shutdown. That does **not** exclude a one-shot on its own — and a shutdown-time filter
   cannot, because the one-shot grant is ephemeral and gone by then. So the recorder makes the
   discrimination **at record time, while the grant is live**, with the same stable-first ordering
   the SM uses: it skips recording exactly when `!pd.implies(p) && pd.impliesOnce(p)` — stable
   denies but a live one-shot grants — and still allows the operation (observe-only). Were a one-shot
   recorded, the escalation would become a *permanent baseline grant* on the next deployment — the
   human-gated one-shot silently demoted to standing authority, the worst failure mode in the design.
   *Operational contract:* this excludes only a one-shot whose grant is live at check time, i.e. the
   escalation ran through the `EscalationGate` during the audit; an escalation-only permission
   exercised *without* the gate is indistinguishable from a genuine floor gap and is recorded
   (human review of the generated floor is the backstop). So: during polpAudit, drive escalation-gated
   operations through the gate, or do not exercise them.
3. **Safe to express as a `DelegatePermission`.** Because (2) keeps one-shots out of the floor, a
   one-shot capability can be a `DelegatePermission` without re-permanentising the very reference it
   exists to revoke; and `impliesOnce` is the same per-`(PD, Permission)` relation the
   `DelegateDomainCombiner` already evaluates the candidate at, so the method guard rides it per
   invocation. The capability one-shot is then hack-free: a grant flagged one-shot,
   permission = `DelegatePermission(candidate)`, reported only by `impliesOnce`, guarded per use.

Soundness corner, resolved exactly (not conservatively): `implies` is consulted first and returns
true if *any* stable grant covers the perm, so a permanently-granted permission stays cached even
when a one-shot also covers it — the SM falls to `impliesOnce` (and skips caching) only when the
one-shot is the *sole* grantor.

**Containment property (state it, don't trip over it).** Making `implies` blind to one-shots means
one-shot authority is honoured **only** through the cooperating SM's two-step
(`SecurityManager.checkPermission`). Code reaching `AccessController.checkPermission` directly
(bypassing the SM) sees only `implies` and *denies* the one-shot. For an escalation this is correct
and fail-secure: every approved-operation guard routes through `sm.checkPermission` in production,
and a one-shot deliberately cannot be exercised by code that goes around the authorization SM.

`clearCache()`/`refresh()` is **not** part of the one-shot path; it keeps its existing role for the
**reusable-revocable** class — e.g. a long-lived smart-proxy `DelegatePermission` (which *is* cached,
for the per-invocation guard to be viable) revoked when the proxy is discarded.

**Survival across agent restore / replay (one-shot must be single-use, not merely timed).** The
whole point of this layer is *async* agents — exactly the population that gets snapshotted,
migrated, or restarted. TTL + post-op revoke make one-shot *temporal*, which a restored or
clock-rewound agent defeats by re-presenting a previously-approved `Outcome`. One-shot is
therefore also made **structural**: `EscalationAuthority.decide` binds its `Outcome` to a
single-use approval id (a nonce), and `EscalationGate.request` consumes that id on first install and
refuses a second use — so a replayed approval installs nothing regardless of timing or clock
state. The companion contract: an in-flight escalation does **not** survive an agent restore. The
escalated authority is deliberately non-serializable (`LeasedPermissionGrant` is not
`Serializable`; the lease is intrinsically local) and lives only in the live policy's grant set;
a restored agent holds no valid escalation and must re-request and be re-approved. Deployments
that snapshot agent *logical* state must not treat "holds approval X" as restorable.

## 4. Components (signatures only — not a build)

```java
package org.apache.river.api.security;

/** A permission described as ground-truth TOKENS, not a live Permission instance. The trusted
 *  layer never instantiates an agent-defined Permission class to handle a request (§6-8): the
 *  renderer reads these fields, and the installer reconstructs ONLY catalogued known types from
 *  them. Immutable. */
public final class PermissionToken {
    public PermissionToken(String permissionClass, String name, String actions);
    public String permissionClass();   // e.g. "java.io.FilePermission"
    public String name();              // e.g. "/var/app/-"
    public String actions();           // e.g. "read,write" (may be null/empty)
}

/** The structured escalation ask. Immutable. The tokens are ground truth (T1); justification is
 *  advisory, agent-supplied, and rendered as untrusted. No live Permission instance crosses the
 *  boundary, so no agent-defined class is loaded or constructed in the trusted path (§6-8). */
public final class EscalationRequest {
    public EscalationRequest(Principal agent, PermissionToken[] requested, String justification);
    public Principal agent();
    public Collection<PermissionToken> requested();   // defensive copy
    public String justification();                    // advisory only
}

/** Trusted token-based rendering AND reconstruction for human display and install (T1). It is the
 *  single fixed, code-reviewed catalogue of KNOWN permission classes (NOT policy-controlled, §6-2):
 *  - render(): KNOWN token classes -> canonicalised human text (resolve relative paths, expand -/*
 *    breadth) rather than pretty-printing the literal; UNKNOWN/agent-defined classes are rendered
 *    raw (class + name + actions) and flagged "unrecognised". It works on TOKENS, so it never
 *    constructs an agent-named Permission to call its toString()/getName()/getActions().
 *  - reconstruct(): KNOWN token -> the corresponding Permission instance for install; an UNKNOWN
 *    class throws (it cannot be approved, because it cannot be safely rendered or instantiated). */
public interface EscalationCatalogue {
    String render(EscalationRequest request);                 // trusted human surface
    Permission reconstruct(PermissionToken token);            // known types only; else throws
}

/** The boundary the deployment implements: deliver the rendered request to the human and
 *  return their decision. JGDMS provides the request, rendering, reconstruction and install; the
 *  deployer provides the transport. MUST surface the rendered (trusted) text, not justification. */
public interface EscalationAuthority {
    /** Deliver the rendered (trusted) text to a human, authenticate them out-of-band, and return
     *  the APPROVED SUBSET of request.requested() (per-Permission, §6-1), the authenticated
     *  approver Subject, and the single-use approvalId ECHOED from the supplied nonce. An empty
     *  approved set is a denial. A non-empty approval is treated as DENY unless BOTH approver()
     *  is non-null (T2) AND approvalId() equals the supplied nonce (T3 single-use). The transport
     *  supplies the UI; authenticated approver identity and the echoed nonce are required. */
    Outcome decide(EscalationRequest request, String renderedTrustedText, ApprovalId nonce);

    /** The approved subset + the authenticated principal + the single-use id it is bound to. */
    final class Outcome {
        public Outcome(Collection<PermissionToken> approved,
                       javax.security.auth.Subject approver, ApprovalId approvalId);
        public Collection<PermissionToken> approved();   // subset of requested(); empty = deny
        public javax.security.auth.Subject approver();   // non-null required if approved() non-empty
        public ApprovalId approvalId();                  // must equal the nonce request() minted
    }
}

/** A single-use, process-local approval nonce. Minted by EscalationGate per request and consumed on
 *  first install; a value not minted by THIS live coordinator (a replayed or restored Outcome)
 *  is never honoured (§3 restore/replay). Opaque, unguessable, NOT Serializable. */
public final class ApprovalId { /* opaque token */ }

/** Where the live delegation handle is delivered: the COORDINATOR, never the agent. Supplied at
 *  construction so the handle never travels back along the agent's call path (T4 by reachability). */
public interface DelegationSink {
    void deliver(EscalationRequest request, LeasedDelegation handle);
}

/** The agent-callable ask. Holds no agent-reachable approval state (T4): the live handle goes to
 *  the DelegationSink, never to request()'s caller. */
public final class EscalationGate {
    public EscalationGate(RevocablePolicy policy, EscalationAuthority authority,
                      EscalationCatalogue catalogue, Duration oneShotTtl,
                      LeaseProvider leases, DelegationSink sink);
    /** Agent-callable: request escalation. Requires the caller to hold EscalationGatePermission whose
     *  grammar admits the requested token classes (ask-time scope, §6-6; an out-of-grammar request
     *  is denied before the human is prompted). Mints a single-use ApprovalId, renders via the
     *  catalogue, and calls authority.decide(request, rendered, nonce). It installs ONLY IF the
     *  Outcome has a non-empty approved subset, a non-null approver, AND approvalId() == the minted
     *  nonce; then it RECONSTRUCTS the approved Permissions from the fixed catalogue (never from an
     *  agent class), installs — under Subject.callAs(approver) so the approver's GrantPermission
     *  ceiling caps it (T2) — a one-shot short-TTL LeasedDelegation of exactly the approved subset
     *  to request.agent(). The grant is flagged ONE-SHOT, so the policy reports it only via
     *  impliesOnce: never cached, never recorded into generated policy (§3). CONSUMES the ApprovalId
     *  (single-use), and DELIVERS the handle to the DelegationSink. For a capability-returning
     *  permission the approved permission is a DelegatePermission guarded per-use, so the
     *  (uncached) impliesOnce re-check severs escaped references on expiry/revoke (T3). Emits an
     *  immutable audit record either way (§6-4). Returns only an opaque Receipt to the agent — never the handle,
     *  lease, or approver. The agent exercises the escalated authority through its own domain (the
     *  grant names the agent principal); it needs no handle to USE the effect, only to CONTROL it —
     *  and control stays with the coordinator. */
    public Receipt request(EscalationRequest request);

    /** Opaque outcome for the agent: granted/denied + the audit record id. Carries NO authority and
     *  NO reference to the lease, delegation, approver, or ApprovalId. */
    public static final class Receipt {
        public boolean granted();
        public String  auditId();
    }
}

/** Baseline capability: the right to *ask* (notify-user). Included in the playpen. Its target is
 *  an ask-time class scope — "request:<permission-class>" (e.g. "request:java.io.FilePermission")
 *  — bounding which permission CLASSES the agent may even surface a request for (§6-6), symmetric
 *  with T2's install-time ceiling. Holding it lets an agent call EscalationGate.request(...) for those
 *  classes; it does NOT confer any escalated authority — only the ability to request it. */
public final class EscalationGatePermission extends Permission { /* target e.g. "request:java.io.FilePermission" */ }
```

**Where the ceiling bites (T2).** `EscalationGate.request` installs via `RevocablePolicy.grant(...)`
**as the authenticated approver `Subject`** returned by `EscalationAuthority` — JGDMS wraps the
install in `Subject.callAs(approver, ...)`, so the existing `GrantPermission` guard caps it
against the *approver's* ceiling. This is why authenticated approver identity is in-scope (§2):
were the transport to return a bare APPROVE and the install run in the agent's or a generic
context, T2 would silently void. An APPROVE without an authenticated approver principal is
therefore treated as DENY. (Same guard exercised by `AgentDelegationCeilingTest`.)

**Deployment requirement for T2 (do not skip).** `callAs(approver)` makes the approver's
*principals present*; it does not by itself make the ceiling *the approver's*. The
`GrantPermission` guard is satisfied only if every domain on the install stack implies it, and it
is the *approver* that bounds the result only when approver authority is expressed as
**principal-scoped** grants (`principal <approver> { GrantPermission ... }`). If no such grant
exists, the only domain supplying `GrantPermission` is the JGDMS install code, and the effective
ceiling silently becomes *the infra's*, independent of who approved. The AND-across-domains
semantics keep this fail-secure (you can never widen *past* the approver — adding a domain can
only narrow), but T2's promise ("a phished user can only approve within the admin's ceiling")
degrades to "within infra's ceiling" unless the deployment models per-approver `GrantPermission`
as principal-scoped grants. This is a deployment obligation the build phase must document and a
deployment test must exercise (C13).

## 5. Trust analysis / anti-spoofing

- **Prose spoofing (T1):** the human approves the rendered ground-truth *tokens*, not the agent's
  `justification` (shown labelled untrusted). The catalogue works on tokens, so it never
  instantiates an agent-defined `Permission` to call its `toString()`/`getName()`/`getActions()`;
  unknown classes are rendered raw and flagged "unrecognised" and cannot be approved at all
  (`reconstruct()` throws). Even *known* types are canonicalised, not pretty-printed (resolve
  relative paths, expand `-`/`*` breadth), so a deceptive name (`"/app/../../etc/passwd"`,
  `"<<ALL FILES>>"`) shows its true reach (§6-2).
- **Untrusted class loading in the trusted path (T1):** because the ask carries tokens, not live
  `Permission` instances, no agent-codebase class is loaded, constructed, or deserialised in the
  approver/coordinator context. An agent-defined `Permission` subclass cannot run constructor or
  accessor code on the approval path and cannot be installed (only catalogued known types
  reconstruct). This makes "unknown ⇒ raw + flagged + un-approvable" structural, not a discipline
  about which method the renderer happens to call (§6-8).
- **Over-ask:** the human sees every requested token and approves a subset (per-Permission, §6-1);
  only the approved subset is reconstructed and installed. The install ceiling (T2) is the hard
  backstop even if the human over-approves. An out-of-grammar request (§6-6) never reaches the
  human.
- **Replay / standing-mode / restore (T3):** one-shot is enforced two ways. *Cache/expiry* — a
  one-shot decision is reported only by `impliesOnce` and is **never cached**, so it is never
  re-served after the grant voids; passive lease expiry denies on the very next check with no
  trigger, for **either** atomic or capability permissions (this replaces the earlier
  `clearCache`-on-revoke approach, which never fired on the eventless expiry path — §1/§3). For
  capability permissions the approved permission is a `DelegatePermission` through a method guard,
  and the uncached `impliesOnce` re-check severs an *escaped reference* on its next use.
  *Structurally* — each approval is bound to a single-use `ApprovalId` minted by `request` and
  consumed on first install; a replayed or restored `Outcome` carries an id this live coordinator
  never minted (or already consumed) and installs nothing. An in-flight escalation does not survive
  an agent restore (§3).
- **Policy-recording leak (T3):** a one-shot must never be written into generated least-privilege
  policy, or the next deployment promotes it to a permanent baseline grant. `polpAudit`
  (`SecurityPolicyWriter`) is observe-only — it records *everything* exercised — so exclusion is an
  explicit **record-time** decision (a shutdown filter can't help: the one-shot grant is gone by
  then). The recorder skips exactly when `!pd.implies(p) && pd.impliesOnce(p)` (stable denies, a live
  one-shot grants) and still allows the op. Caveat: this catches only a one-shot whose grant is live
  at check time (the escalation ran through the gate during the audit); an escalation perm exercised
  outside the gate looks like a floor gap and is recorded, with human review of the floor as the
  backstop (§3, §6-9).
- **Self-approval/extension (T4):** enforced by reachability. The agent never receives the
  `EscalationAuthority`, the decision, the `ApprovalId`, the `Lease`, or the `LeasedDelegation`
  handle — the handle goes to the `DelegationSink` (the coordinator), and `request(...)` returns
  the agent only an opaque `Receipt` (granted + audit id). The agent gets the *effect* of a new
  short grant (its own domain now implies the approved permission) or nothing; it holds no object
  it could renew, revoke, or inspect.
- **Confused-deputy on the channel:** the transport must authenticate the approver out-of-band and
  return their `Subject` and the echoed `ApprovalId`; JGDMS binds T2 to that authenticated
  principal by running the install as it (§4) and T3 to the echoed nonce. JGDMS does not assert the
  *channel/UI* is authentic — that UX is the deployer's — but the authenticated approver identity
  and the matching nonce are required SPI outputs, not optional (a mismatch is a DENY).

## 6. Decisions (resolved 2026-06-17)

1. **Approval is per-Permission, not all-or-nothing.** The human may approve a subset of the
   requested permissions; the install is of exactly that subset. All-or-nothing invites the
   agent to bundle a needed-benign permission with a dangerous one and have the human approve
   the bundle; per-Permission keeps the surface least-privilege-honest. The T2 ceiling remains
   the hard backstop. (Reflected in `EscalationAuthority.Outcome.approved()`, §4.)
2. **Renderer catalogue is fixed and code-reviewed, not policy-controlled** (initially), and it
   **canonicalises** the true scope rather than pretty-printing the literal. Unknown/agent-defined
   types are raw+flagged (T1); *known* types still deceive through their name/actions
   (`FilePermission "/app/../../etc/passwd"`, `"<<ALL FILES>>"`, wildcard hosts,
   `PropertyPermission "*"`), so the renderer resolves relative paths, expands `-`/`*` breadth and
   shows effective reach. Policy-controlling the catalogue is deferred — it adds a manipulation
   surface for marginal gain. The same fixed table also **reconstructs** install Permissions from
   tokens (§6-8): one code-reviewed catalogue, used for both render and reconstruct.
3. **Ship Option (ii) first; (i) later.** Async agents are the whole point and (ii) needs no user
   co-execution; (i) (live `callAs` co-presence) is the cleaner model when the user genuinely
   co-drives and can follow once there is demand. With the §3 method-guard wrap they converge.
4. **Audit is a core deliverable, not deferred.** `EscalationGate` **emits** an immutable record of
   every request + decision (agent, requested `Permission[]`, approved subset, approver principal,
   timestamp, outcome). This ledger is the governance artifact and the reason an escalation gate exists;
   it is distinct from `polpAudit` (which generates least-privilege policy *files*) and must not be
   folded into it. Only the record's storage/transport is a deployer SPI; emission is in-scope
   (§2/§4).
5. **Approver-context plumbing — RESOLVED:** the install runs as the authenticated approver
   `Subject` returned by `EscalationAuthority`, wrapped in `Subject.callAs`; an approval without
   an authenticated approver installs nothing. Authenticated approver identity is in-scope
   (§2/§4); only the authentication UX is the deployer's.
6. **`EscalationGatePermission` grammar is an ask-time class scope** — `"request:<permission-class>"`
   (e.g. `"request:java.io.FilePermission"`) — symmetric with T2's install-time ceiling: it bounds
   which permission *classes* the agent may even surface a request for. Two ceilings (ask-time +
   install-time) cut the human's exposure and blunt approval fatigue; an out-of-grammar request is
   denied before it reaches the human. (Frequent in-grammar escalations are themselves a signal the
   Phase-1 playpen is mis-scoped.)
7. **One-shot is single-use and does not survive restore (added 2026-06-18).** Each approval is
   bound to a single-use `ApprovalId` minted by `request` and consumed on first install; a replayed
   or restored `Outcome` installs nothing (§3). An in-flight escalation is non-serializable by
   construction and does not survive an agent restore — it must be re-requested and re-approved.
   TTL + revoke remain the temporal backstop; the nonce makes one-shot structural rather than
   timing-dependent. (This is the answer to "what happens to in-flight authority when an agent
   is restored": nothing carries over — by construction.)
8. **The ask carries permission TOKENS, not live `Permission` instances (added 2026-06-18).** The
   trusted approval path never loads, constructs, or deserialises an agent-defined `Permission`
   class. The fixed catalogue renders tokens for the human and reconstructs only catalogued known
   types for install; unknown classes are rendered raw + flagged and are un-approvable. This closes
   a class-loading / deserialisation surface in the most trust-sensitive path and makes the
   unknown-type handling structural (§4, §5).
9. **One-shot decisions are never cached and never recorded — `implies` vs `impliesOnce` (revised
   2026-06-18b).** Cacheability is a property of the satisfying *grant*, which the SM cannot read
   off the requested permission, and the policy reasons strictly per `(PD, Permission)` (never per
   context). So the policy splits grant-evaluation: `implies` reports **stable** authority (feeds
   the decision cache **and** the `SecurityPolicyWriter` recorder), and a new
   `impliesOnce(PD, Permission)` (on `java.security.Policy`, default `false`, overridden by
   `DynamicPolicyProvider`) reports one-shot authority. The SM leaves its protected per-PD recording
   check unchanged and **adds** an `impliesOnce` consultation *after* a failed `implies`, outside
   that method. One split, three properties: a one-shot is never cached (no overhead on the stable
   path; passive expiry needs no trigger), never written into generated policy — `SecurityPolicyWriter`
   is observe-only (it records *everything* exercised), so exclusion is an explicit **record-time**
   skip when `!pd.implies(p) && pd.impliesOnce(p)` (stable denies, a live one-shot grants), not a
   shutdown filter (the grant is gone by then); this keeps an escalation from being promoted to a
   permanent baseline grant (the worst failure mode). Only a one-shot whose grant is *live during the
   audit* is excluded, so escalations must run through the gate during polpAudit (human floor-review
   is the backstop). Third, safe to express as a `DelegatePermission` (no floor leak; the method guard
   rides the uncached `impliesOnce` per use). The soundness corner resolves exactly — `implies`-first
   means a permanently-granted perm stays cached even if a one-shot also covers it. *Supersedes the
   earlier `clearCache`-on-revoke approach, which never fired on the eventless expiry path;*
   `clearCache()`/`refresh()` keeps its role only for the **reusable-revocable** class (a cached
   smart-proxy `DelegatePermission` discarded mid-life). One-shot authority is honoured only through
   the cooperating SM's two-step (`AccessController.checkPermission` bypassing the SM denies it —
   deliberate fail-secure containment). DirtyChai dependency: `impliesOnce` + the SM post-check land
   in java.base. (§1/§3/§5.)
10. **Naming (RESOLVED 2026-06-18b, applied).** The prior name read as snapshot/restore — exactly
   the sense this design does *not* mean — so the public names are now `EscalationGate`,
   `EscalationAuthority`, `EscalationGatePermission` (the request/renderer/audit types were already
   `Escalation*`). This file is `DESIGN-AgentEscalationGate.md`. The SOW still uses "Phase 2
   checkpoint" in prose; update its class names when the build lands.

## 7. Test contract (for the build phase)

- **C1** Agent without `EscalationGatePermission` cannot call `request` (denied).
- **C2** `request` with an empty approved set (deny) installs nothing; agent still bounded to
  the playpen.
- **C3** `request` with an approved subset installs a grant for exactly that subset (not the full
  `requested()`) to the agent; the agent domain implies the approved permissions and not the
  denied ones; a different principal implies neither.
- **C4a** (atomic, enforced path) Approved escalation is one-shot via the *enforced* check path:
  after TTL (test clock) or post-op `revoke()`, `SecurityManager.checkPermission` — **not**
  `policy.implies`, which bypasses the SM and would give a false green — denies the escalated
  permission for the agent; the playpen survives. The test MUST run under an active
  `CachingSecurityManager` so the never-cached `impliesOnce` path is exercised (see C11).
- **C4b** (capability) For a capability-returning permission approved as a `DelegatePermission`
  through a method-guard delegate, a reference acquired during the window is denied on its next
  method call after `revoke()`/expiry — because the one-shot is reported via `impliesOnce` and never
  cached, the per-use guard re-check re-reads liveness and severs the escaped reference, not merely
  the grant (§3).
- **C5** Install ceiling (T2): with an active SecurityManager, an APPROVE for permissions
  beyond the approver's `GrantPermission` is refused (`SecurityException`) even though the
  human said yes. (Mirrors `AgentDelegationCeilingTest`.)
- **C6** Catalogue (T1): (a) a request carrying a token for an agent-defined `Permission` class
  renders raw class/name/actions + "unrecognised" and `reconstruct()` throws (un-approvable) —
  never the deceptive text, and the agent class is never loaded; (b) a *known* type with a
  deceptive name (`FilePermission "/app/../../etc/passwd"`) renders its canonicalised true scope,
  not the literal (§6-2).
- **C7** (T4 reachability) `request` returns only an opaque `Receipt` (granted + audit id); the
  `LeasedDelegation` is delivered to the `DelegationSink`, never to `request`'s caller. Assert the
  agent's return value exposes no lease/handle/approver/`ApprovalId` and the agent path cannot
  renew or revoke.
- **C8** (T2 / approver auth) A non-empty approved set returned with a null approver `Subject`
  installs nothing; and the ceiling check (C5) is bound to the *authenticated approver's*
  `GrantPermission`, not the agent's.
- **C9** (audit, §6-4) Every `request` — approved, partially approved, or denied — emits exactly
  one immutable audit record carrying agent, requested set, approved subset, approver principal
  and timestamp; emission does not depend on the deployer storage SPI.
- **C10** (ask-time grammar, §6-6) An agent whose `EscalationGatePermission` does not admit a
  requested permission's class is denied before the request reaches the `EscalationAuthority`
  (the human is never prompted).
- **C11** (T3 one-shot never cached, atomic) Under an active `CachingSecurityManager`: an approved
  atomic one-shot is exercised once, then the lease expires (test clock) with **no** explicit
  `revoke()` and **no** `clearCache()`. A second attempt of the *same* operation via
  `SecurityManager.checkPermission` is denied — proving the decision was never cached (it was
  reported by `impliesOnce`, which the SM does not cache), so the eventless expiry path is covered
  without a trigger. **Positive control:** the same perm granted *permanently* (via `implies`) IS
  served from cache on the second attempt — confirming caching still works for stable authority.
- **C12** (T3 single-use / restore) Replaying a previously-approved `Outcome` (same `ApprovalId`)
  installs nothing — the id was already consumed. Simulated restore: an `Outcome` whose
  `ApprovalId` this live coordinator never minted is rejected. A fresh `request` after "restart"
  mints a new id and requires fresh approval; no authority carries across the restart.
- **C13** (T2 deployment binding) With approver authority modelled as a principal-scoped
  `GrantPermission` grant, an in-ceiling APPROVE installs and an over-ceiling one is refused, bound
  to the *approver's* ceiling. **Control:** with NO principal-scoped approver grant, the effective
  ceiling collapses to the install code's domain — assert this is fail-secure (never wider than the
  approver) and documents the §5 deployment obligation.
- **C14** (`impliesOnce` coexists with a reusable `DelegatePermission` on one PD) A PD holds both a
  reusable smart-proxy `DelegatePermission` (stable, via `implies`) and a one-shot `DelegatePermission`
  (via `impliesOnce`). Assert: the reusable one is served from cache on repeat checks (still viable);
  the one-shot one is never cached and dies on expiry; neither taints the other. Confirms the marker
  granularity is the grant/`impliesOnce` relation, not the PD or the `DelegatePermission` class.
- **C15** (soundness corner) A perm covered by **both** a permanent grant (`implies`) and a one-shot
  (`impliesOnce`) is served from cache and survives the one-shot's expiry — `implies`-first means the
  stable grant wins and caching is correct; the one-shot does not force non-caching of an
  independently-permanent decision.
- **C16** (no policy-recording leak, T3) Under `polpAudit` (`SecurityPolicyWriter`): an agent
  exercises an escalation **through the `EscalationGate`** (so the one-shot grant is live during the
  audit), plus a genuinely-ungranted permission. Assert the generated policy file omits the escalated
  permission but includes the genuinely-missing one — i.e. the recorder's record-time skip
  (`!pd.implies(p) && pd.impliesOnce(p)`) excludes the live one-shot while still capturing real floor
  gaps. **Negative control:** the same escalation perm exercised *without* the gate (no live one-shot
  grant) IS recorded, documenting the operational contract that escalations must run through the gate
  during an audit. Guards the worst failure mode — a one-shot promoted to a permanent baseline grant.

---

*End of design.*
