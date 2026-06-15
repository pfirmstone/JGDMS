# Agent-Authority Model — Code Review vs. Implementation

*Read-only static review. 2026-06-14 (filed under the date requested in the brief; review performed 2026-06-15).*
*Reviewer: automated code-review pass over JGDMS + DirtyChai authorization sources.*
*Scope: `org.apache.river.api.security.*`, `net.jini.security.*`, `net.jini.jeri.ssl.*` (JGDMS) and
`au.zeus.jdk.authorization.*` + the modified `java.security`/`javax.security.auth` classes (DirtyChai).*
*Constraint honoured: no source/doc modified, no build/test run. `target/classes/OSGI-OPT/**` build
artifacts were ignored; all line references are to `src/main/java` / `src/java.base/share/classes`.*

> **Location note:** The brief requested this file at
> `C:\Users\peter\Documents\GitHub\JGDMS\JGDMS\docs\agent-authority-code-review-2026-06-14.md`, but
> Write to that path was denied by the environment. Written instead to `E:\Claude\` (the session
> working directory). Move it into the JGDMS `docs/` folder manually if desired.

---

## 1. Verdict

The implementation matches the reasoned model **closely and substantively**. The three structural
load-bearing claims — three-layer composition, GC-scoped per-proxy grants, and **intersection
(meet) semantics with all-principals-present conjunction** — are all real in code, not aspirational.
Attenuation is genuinely *structural*: every grant type composes its conditions with `&&`, the
principal test is `containsAll` (every required principal must be present), `DigestGrant` adds a
hard `DigestCodeSource`-instance gate, and `GrantPermission` is enforced as a ceiling on both the
dynamic-grant and remote-policy push paths. The `callAs`/`SubjectDomainCombiner` path is
**additive-only** and provably cannot widen a domain's permissions, so narrowing across a delegation
chain is structurally guaranteed.

Where the model overreaches is in the **two-tier baseline-playpen + checkpoint-escalation** pattern
and the **leased async user→agent delegation** primitive: these are *consequences of the primitives*
that **no code yet exercises** — there is no agent-authority class, no lease-bound user→SVID grant,
no derived/narrowed JWT, and no "notify-user" escalation channel anywhere in either tree. They are
buildable on what exists but are **not implemented**. Two smaller divergences: the model's "polpAudit
emits *scoped GrantPermission*" is imprecise (it emits scoped *permission grant blocks*, including
`digest`/`principal` clauses — `GrantPermission` only appears if observed); and peer-scoped network
*does* exist in the vocabulary (`AuthenticationPermission` with its `peer` clause), which the model
treated as an open question.

---

## 2. The Eight Claims

| # | Claim | Status | Evidence (file:line) | Notes |
|---|---|---|---|---|
| 1 | 3-layer stack (SpiffePolicyFile → RemotePolicy → DynamicPolicy), per-proxy grants GC-scoped | **CONFIRMED** | `DynamicPolicyProvider` wraps a base policy (`net/jini/security/policy/DynamicPolicyProvider.java:218-285`); GC-scoping via `WeakReference<ProtectionDomain>` in `PermissionGrantBuilderImp.clazz():117-126`; void-on-GC in `ProtectionDomainGrant.isVoid():192-196` and `impliesProtectionDomain():122` (`domain.get()==null → false`); 60 s sweeper `DynamicPolicyProvider.createSweeper():355-392` + `sweepVoidGrants():403-422`. `SpiffePolicyFile extends ConcurrentPolicyFile` (DirtyChai `…/policy/SpiffePolicyFile.java`), refreshes on SVID rotation. | Composition is by *wrapping* (`basePolicy`), not a fixed 3-name chain. DynamicPolicy and RemotePolicy each independently wrap a base `ScalableNestedPolicy`; the documented ordering is a deployment convention, not hard-wired. Grant is keyed to the proxy's *ProtectionDomain/ClassLoader*, GC of which voids it. |
| 2 | Effective grant = declared ∩ GrantPermission ceiling ∩ principal scope; a MEET (terms only narrow) | **CONFIRMED** | Per-grant condition AND: `ProtectionDomainGrant.implies():97` = `impliesProtectionDomain(pd) && implies(getPrincipals(pd))`; `URIGrant.implies(CodeSource,…):133` returns false unless `implies(p)` (principals) *and* URI match; `DigestGrant.implies():144-151` ANDs super (URI+principal) with DigestCodeSource+algorithm+bytes. Ceiling: §claim 4. | Subtlety the model blurs: *within* a single matching grant, permissions are collected as a set/union (`AbstractPolicy.processGrants:178-213`). The **meet** is across the three *gating dimensions* (codebase/digest, principal-set, grant-ceiling) — a grant contributes **nothing** unless *all* its conditions hold. Adding a `principal`/`digest`/`codebase` clause can only remove matches. So "adding terms narrows" is true at the clause level, which is what matters. |
| 3 | Multi-principal grants are CONJUNCTIVE (all present); DigestCodeSource is an additional required conjunct | **CONFIRMED** | `PrincipalGrant.implies(Principal[]):203-235` — empty pals ⇒ matches anything; otherwise resolved path `hasPrincipals.containsAll(pals)` (line 235) and unresolved path `matches == pals.size()` (line 232). All-present, never any-present. Digest conjunct: `DigestGrant.implies():146` `isInstance(DigestCodeSource)` required; plain CodeSource never matches. | A grant naming `SpiffePrincipal` **and** `JwtPrincipal` requires both in the Subject/PD. `SecureClassLoader.getProtectionDomain` stamps the workload SPIFFE principal into the PD (DirtyChai `SecureClassLoader.java:342-352`) while `callAs` injects the user principal — the conjunction spans both injection paths. |
| 4 | GrantPermission is a CEILING — a caller cannot dynamically grant beyond its own GrantPermission | **CONFIRMED** | Dynamic path: `DynamicPolicyProvider.grant(Class,…):604-605` builds `new GrantPermission(permissions)` and `g.checkGuard(null)`; `grant(PermissionGrant):684-688` same. Remote path: `AbstractPolicy.checkCallerHasGrants():79-89` builds a `GrantPermission` per grant and `checkGuard(this)`; called from `RemotePolicyProvider.processRemotePolicyGrants():177`. Ceiling logic: `GrantPermission.implies()` / `Implier.implies():713-739`. | Remote push has a *second* gate: the grant-class domains must imply `PolicyPermission("Remote")` (`RemotePolicyProvider.java:167-173`). So the descending ceiling chain operator⊇admin⊇…⊇code is real: `GrantPermission` content + `PolicyPermission("Remote")` bound the administrator; `GrantPermission` alone bounds the dynamic granter. |
| 5 | SecureClassLoader promotes network CodeSources to DigestCodeSource; `digest` grants match only DigestCodeSource domains | **CONFIRMED** | Promotion: `SecureClassLoader.getProtectionDomain():354-380` — for non-null codebase, computes `new DigestCodeSource(uri,certs,"SHA-256")` and rebuilds the PD against it. Match gate: `DigestGrant.implies(CodeSource,…):145-146` returns false if `DIGEST_CODE_SOURCE_CLASS==null` or `!isInstance(codeSource)`; `implies(ClassLoader,…):127-128` returns false (indeterminate). | `DigestGrant` reaches `DigestCodeSource` **by reflection** (`DigestGrant.java:61-75`) so jgdms-platform still compiles on stock JDK; on a non-DirtyChai JVM every DigestGrant fails closed. Algorithm hard-coded to SHA-256 in the loader (`:368`, flagged "configurable is a planned follow-up"). |
| 6 | LoadClassPermission gates the class-load path (admission control), not merely defined | **CONFIRMED** | `LoadClassPermission` (DirtyChai `…/guards/LoadClassPermission.java`, name forced to `"ALLOW"`). Checked in `SecureClassLoader.getProtectionDomain():379-383` `sm.checkPermission(LOAD_CLASS_ALLOW, ACC{newPd})`; reached from all four `defineClass` overloads (`SecureClassLoader.java:178,216,248,280`). | Nuance worth recording: the check fires on **first PD construction per CodeSource key** (cached in `pdcache`, `:330-331,392`), i.e. per distinct codebase, not literally per class. The check ACC contains *only the loaded code's own new domain*, so the code's domain must itself be granted `LoadClassPermission` — untrusted codebases are refused admission. Effective as admission control. |
| 7 | `getContext()` injects active scoped (non-Worker) subjects so callAs participates at every getContext(), not only at a doPrivileged boundary | **CONFIRMED** | `AccessController.getContext():980-1018` — after `optimize()`, reads `SubjectAccess.SCOPED.get():995`, skips `WorkerSubject:999`, builds a `SubjectDomainCombiner` per subject and combines into the returned ACC (and into the privileged context if present, `:1004-1011`). `callAs` binds the `ScopedValue` `SCOPED_SUBJECT` (`Subject.callAs:559-571,598-611` → `callNoCheck:629-636`). | `WorkerSubject`/`SpiffeSubject` are excluded from this scoped path (ambient via PD stamping instead) — Subject invariants 6/7 in `SECURITY_MODEL.md` hold in code. `callAs` rejects a `WorkerSubject` argument (`Subject.java:562-565`). |
| 8 | polpAudit/SecurityPolicyWriter: (a) emits scoped GrantPermission; (b) fail-closed on unexercised paths; (c) property substitution | **PARTIAL** | SM alias wired: `System.java:2484-2486` `case "polpAudit": setSecurityManager(new SecurityPolicyWriter())`. (a) emits scoped **grant blocks** with `codebase`/`digest`/`principal` clauses (`SecurityPolicyWriter.java:437-499`) + observed `permission` lines (`:502-550`) — **not** GrantPermission per se. (b) `checkPermission():334-356` only *records* permissions actually exercised and always `return true` (observe-only); shutdown hook skips perms already implied by existing policy (`:404`). (c) `replaceValuesWithProperties():576-593`, FilePermission `${/}` (`:531`), Socket/URL host→`${HOST}` (`:533`), props file via `polpAudit.path.properties` (`:186`). | (a) is the model's imprecision: the floor it generates is *scoped permission grants*; a `GrantPermission` only appears if the audited code itself exercised one. The scoping the model cares about (digest + principal + codebase clauses) **is** emitted. (b) fail-closed is correct: unexercised path ⇒ never recorded ⇒ deny-by-default in production; the class doc (`:128-135`) states subsequent runs only *append*, never widen. **Doc/code mismatch:** Javadoc `:124-125` still names the legacy property `SecurityPolicyWriter.path.properties`, but the code reads `polpAudit.path.properties` (`:186`). |

---

## 3. Open Questions

### A. Is there an attenuating, *leased* user→agent delegation primitive usable later *without* the user principal live in scope (async-agent case)?

**Not found — and the absence is structural, not incidental.** Grepping the entire DirtyChai
authorization tree and the JGDMS security/SSL trees for `lease|delegat|narrow|attenuat|derive` returns
**no** authority-delegation construct: the `delegat*` hits are the SM's `DelegatePermission`/
`DelegateDomainCombiner` parallel-check machinery (`CombinerSecurityManager.java:367-426`) and the
`AuthenticationPermission` TLS `delegate` *action* (client-credential delegation over the wire), and
the `derive` hits are SPIFFE policy-URL derivation (`SpiffeCredentialManager.java:256-277`). There is:

- **No derived/narrowed-JWT API.** `JwtPrincipal` (`net/jini/security/jwt/JwtPrincipal.java`) is an
  immutable `(claim:value)` name holder with no minting, narrowing, or attenuation method, and it is
  explicitly *not* serializable (transmitted only as a `(class,name)` pair over JERI, `:53-61`).
- **No user-scoped, lease-bound grant to the agent's SVID.** No `PermissionGrant` subtype binds a
  lease; `isVoid()` is driven by GC (`ProtectionDomainGrant.isVoid:192-196`) or empty perms
  (`PrincipalGrant.isVoid:313-316`), never by a lease/expiry.
- The **only** modelled co-presence mechanism is the live one: a user principal must be in the
  Subject scope at the moment of the action, injected by `callAs` → `getContext()`
  (`AccessController.java:995-1015`). Outside that scope the user principal is simply absent, so any
  `principal JwtPrincipal …` grant fails the `containsAll` test (`PrincipalGrant.java:235`).

So today the answer is exactly the model's fallback: **"the user principal must be live in the Subject
scope at the moment of the action."** A leased async delegation *could* be built on the existing
primitives (a `principal`-scoped grant to the agent SVID, void-on-lease-expiry via a new `isVoid()`
driver, or a SPIRE-minted short-TTL JWT carrying the user `sub` claim), but **none of that exists in
code.** Flag as **aspirational / not-yet-implemented.**

### B. Can authority WIDEN across a delegation / callAs chain, or is narrowing structurally guaranteed?

**Narrowing is structurally guaranteed; no widening path exists.** `callAs` does **not** confer the
subject's authority — it only makes the subject's principals *present* so that principal-conditioned
grants *can* match. The combiner is provably additive-only:

- `SubjectDomainCombiner.combine():241-249` rebuilds each non-static ProtectionDomain with the **same
  CodeSource, same permissions, same ClassLoader**, adding only principals. Privileged/`AllPermission`
  and static-permission domains are passed through **unchanged** (`:245-248`) — principals are never
  added to a privileged domain, so you cannot "callAs into AllPermission."
- The class contract states it outright (`:53-55`): *"Principals … are merged additively — neither
  replaces the other. A grant conditioned on both workload and user principals requires both to be
  present."*
- Because the rebuilt domain keeps its original CodeSource, policy re-evaluation against the new
  principal set still requires codebase/digest to match. Adding a principal can only make *more*
  principal-gated grants applicable, and every such grant is independently bounded by its own
  codebase/digest/ceiling. There is no construct that *removes* a required clause.

This is the meet-semantics consequence the model relies on, and it holds in code. **CONFIRMED** that
delegation attenuates end-to-end.

### C. Can a baseline network permission be PEER-SCOPED (pinned to specific peer principals) rather than network on/off?

**Yes — `net.jini.security.AuthenticationPermission` is exactly a peer-scoped network/credential
permission, and it is in the live vocabulary.** Its target name has the form
`LocalPrincipals [peer PeerPrincipals]` (`AuthenticationPermission.java:48-78`): *LocalPrincipals* =
the maximum set you may authenticate **as**, *PeerPrincipals* = the **minimum** set the peer must
authenticate as. Actions are `listen,accept,connect,delegate` (`:80-97`). The `peer` clause pins the
remote identity (`:109-112,124-130`). So a baseline egress grant *can* be scoped to a specific peer
principal rather than being on/off.

Qualifications vs. the model's phrasing ("pinned to specific *SPIFFE* peer principals"):
- Peer-scoping is by **any `Principal` class**, matched by class-name + name. In the SSL/SPIFFE
  endpoints the peer authenticates via an `X500Principal` derived from the SVID leaf, so in practice
  the peer pin is by **X500Principal**, not `SpiffePrincipal` directly — though `SpiffePrincipal` is a
  `Principal` and is usable in policy `principal` clauses (`SpiffePrincipal.java:40-46`).
- `AuthenticationPermission` governs **authenticated SSL/JERI calls** (who-as/who-to). Raw socket
  egress is still governed by stock `SocketPermission`/`URLPermission` (host:port scoping, not peer
  *identity* scoping). There is **no** dedicated "peer-SPIFFE-scoped raw network" permission type in
  the DirtyChai guards package (only `LoadClass/NativeInvocation/NativeMemory/DefineClass/SerialObject`).

So the *capability* the model wanted (peer-identity-scoped network) exists for the authenticated path;
it is not a brand-new SPIFFE-specific permission type. **CONFIRMED (with the X500-vs-SPIFFE and
authenticated-path qualifications).**

### D. Is the baseline-playpen + checkpoint-escalation pattern established/implemented, or purely a consequence of the primitives?

**Purely a consequence of the primitives — no code exercises it.** The model's "baseline = grant
*without* a user-principal term; escalation = same permission space *with* `∧ user JWT principal`" maps
cleanly onto what exists:

- A principal-less grant matches a domain regardless of scoped user (`PrincipalGrant.implies:204`
  empty-pals ⇒ true). That **is** a "baseline playpen" grant.
- A grant with a `JwtPrincipal` clause only applies when that principal is co-present
  (`PrincipalGrant.java:235`), and `callAs` makes it co-present only for the dynamic scope of the call;
  DynamicPolicy GC-scoping makes proxy-bound grants operation/lifetime-scoped. That **is** the
  "escalation only while the user is present, one-shot."

But there is **no** agent-authority abstraction, no "baseline playpen" policy template, no checkpoint
construct, and **no "notify-user" escalation-request channel** anywhere in either repository. The
pattern is a correct *interpretation* of the meet-semantics primitives, **not an implemented feature.**
Flag as **aspirational / design-intent (STD-007 §2.5 framing), not code.**

---

## 4. Most Important Divergences / Risks

1. **The agent-authority layer is entirely unbuilt (A + D).** Everything the model calls "the agent
   authority model" — baseline playpen, checkpoint escalation, notify-user channel, leased async
   user→agent delegation — has **zero** implementation. STD-007 §2.5 states the *intent* ("delegated
   authority can only narrow … because a participant cannot be relied upon to limit itself") but no
   class realises an agent identity, a lease-bound user→SVID grant, or a derived JWT. The primitives
   support it; nothing exercises it. This is the single largest gap between model and code.

2. **No lease-driven grant revocation.** `isVoid()` is GC-driven or empty-perms-driven only. The
   model's "leased playpen + dead-man switch (stop renewing → agent goes dark)" has no lease hook in
   `PermissionGrant`. A lease would need either a new `isVoid()` driver or a decorator updating a
   volatile flag (the `PermissionGrant` decorator pattern at `:170-196` is the intended seam, but it
   is unused for leasing).

3. **DigestGrant reflection coupling is a silent-misconfiguration footgun.** `DigestGrant` resolves
   `DigestCodeSource` reflectively (`:61-75`); on stock JDK the class is absent and **every**
   DigestGrant returns false (fail-closed, correct). The risk is the inverse of fail-open: a
   deployment that *believes* it has digest enforcement but runs on a non-DirtyChai JVM silently has
   **no** digest-matched grants at all — those permissions simply never apply, which could be misread
   as "policy broken" rather than "wrong JVM." Operationally safe, but a trap.

4. **`LoadClassPermission` granularity / promotion preconditions.** Admission control is
   per-CodeSource-key (cached), not per-class, and the SHA-256 algorithm is hard-coded in the loader
   (`SecureClassLoader.java:368`). The model's "promotes *every* network CodeSource" is true only
   while `VM.isBooted()` **and** a SecurityManager is active **and** the codebase is non-null
   (`:337,342,354`); pre-boot / no-SM / null-codebase paths skip promotion (documented fail-secure,
   but worth stating explicitly).

5. **polpAudit property-name doc/code drift** (claim 8): Javadoc says
   `SecurityPolicyWriter.path.properties`, code reads `polpAudit.path.properties`
   (`SecurityPolicyWriter.java:124-125` vs `:186`). A user following the Javadoc would get no
   substitutions. Low severity, easy fix.

---

## 5. What the Model Got Wrong or Missed (that the code reveals)

- **"polpAudit emits scoped *GrantPermission*"** (claim 8a / memory line 35) is imprecise. It emits
  scoped **permission grant blocks** — `codebase` + `digest` + `principal` clauses wrapping the
  *observed* permissions. A `GrantPermission` line appears only if the audited code itself exercised a
  `DynamicPolicy.grant`. The "scoped floor incl. scoped GrantPermission" should read "scoped
  least-privilege grant blocks (with digest/principal scoping)."

- **Peer-scoped network already exists** (question C). The model framed peer-scoped egress as an open
  question / desideratum; `AuthenticationPermission`'s `peer` clause has provided exactly this since
  Jini 2.x. The model under-credits the existing vocabulary.

- **The "3 layers" are a *wrapping convention*, not a fixed chain.** `DynamicPolicyProvider` and
  `RemotePolicyProvider` both generically wrap any base `ScalableNestedPolicy`/`Policy`
  (`DynamicPolicyProvider.java:267-285`, `RemotePolicyProvider.java:90-108`). The
  SpiffePolicyFile→Remote→Dynamic ordering is a deployment assembly, not enforced by the types. The
  model presents it as a fixed stack; the code is more flexible (and the ordering must be assembled
  correctly by whoever wires the providers — a configuration responsibility the model glosses).

- **The intersection is across *gating dimensions*, not a literal set-intersection of permission
  lists.** Within one matching grant, permissions are *unioned* (`processGrants`); the *meet* is that a
  grant contributes nothing unless all of {codebase/digest, all-principals-present, ceiling} hold. The
  model's "declared ∩ ceiling ∩ principal" is the right intuition but the mechanism is *conjunctive
  gating of grant applicability*, not arithmetic intersection of permission sets. Important when
  reasoning about *why* adding a clause narrows: it narrows the *set of domains the grant applies to*,
  which is monotone.

- **The conjunction spans two different injection mechanisms.** A `SpiffePrincipal ∧ JwtPrincipal`
  grant works because the SPIFFE principal is **stamped into the ProtectionDomain at class-load**
  (`SecureClassLoader.java:342-352`, ambient/`WorkerSubject`) while the JWT/user principal is
  **scoped-injected by `callAs`** (`AccessController.java:995-1015`, excludes `WorkerSubject`). The
  model treats "all principals present" as one bag; in code they arrive via two deliberately separate
  paths (ambient workload vs. scoped user), which is *why* `WorkerSubject` is barred from `callAs`
  (`Subject.java:562-565`) — to stop workload identity leaking into the user-scoped chain. This
  separation is a real and important design property the model didn't capture.

---

*End of review.*
