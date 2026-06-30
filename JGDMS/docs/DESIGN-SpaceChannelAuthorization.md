# Design — Space Channel Authorization (principal-level access control for Outrigger)

- **Status:** Provisional (pre-release; revisable on good cause)
- **Date:** 2026-06-19
- **Branch:** `secarch-take-commit-recon`
- **Grounded against:** JGDMS trunk `fe1861e69`
- **Related:** `DESIGN-AgentEscalationGate.md`, `DESIGN-LeasedPermissionGrant.md`, DirtyChai `SECURITY_MODEL.md`

---

## 1. Context and threat model

The tuple space is treated as a **trust boundary**: `write` / `read` / `take` / `notify` each cross it, and each is authority-bearing in proportion to data sensitivity.

This design assumes the surrounding platform guarantees:

- **No untrusted code.** All code passes a mandatory SCAP bytecode-analysis admission gate and is content-hashed (digest). The code-injection / deserialization-gadget / arbitrary-RCE class is closed by admission control — it is **not** the space's problem to solve.
- **Permission = a multi-principal ∧ multi-digest conjunction**, deny-by-default. A grant applies only when **all** recorded principals are simultaneously present — typically `user ∧ local-process-SVID ∧ remote-process-SVID` — **and** every codebase digest on the call stack is authorized. Missing any principal, or any digest, yields no permission. (DirtyChai `SECURITY_MODEL.md` §8, §8.1–8.2.)

Consequently the space's job is **principal-to-principal** authorization: two services running SCAP-vetted, digest-pinned code under *different principals* must not cross each other's data. Code trust says nothing about principal boundaries; this design defends those boundaries.

"Adversarial input" therefore means **adversarial data values, self-asserted provenance, and flood/DoS from a differently-privileged principal** — not gadget code. The bytecode is vetted; the runtime data and behaviour a principal drives through it is not.

---

## 2. The permission model in one line

```
(∀ required principals: user ∧ local-SVID ∧ remote-SVID)  ∧  (∀ codebase digests on stack)  ⇒  op
```

Carriers (why the conjunction spans both frames and subjects):

- **codebase digests + local-process SVID** — stamped per-frame on each `ProtectionDomain` at class-load (ambient).
- **remote-process SVID** — the verified TLS peer chain is reconstructed as a `RemoteSubject` (a `WorkerSubject`) whose principals sit in the JERI dispatch ACC's `ProtectionDomain`s (stack/ACC-borne). It is established by the connection, **not** via `Subject.doAs` (which rejects a `WorkerSubject`); retrieve it with `getClientSubject()`.
- **user principal(s)** — carried via `Subject.callAs` / `ScopedValue`, re-injected by `AccessController.getContext()` (ambient within scope).

This is multi-factor authority: forging it requires simultaneous control of every factor — each process SVID is unforgeable mTLS, each codebase is digest-pinned, the user is authenticated.

### `doPrivileged` interaction (load-bearing)

`doPrivileged` drops **stack-borne** context (codebases below the boundary + the remote-process SVID in the remote ACC) but keeps **ambient** context (local-process SVID via PD stamping, user principals via `callAs`/`ScopedValue`). Use naked `doPrivileged` **only** for small local privileged sections that do not need the remote ACC.

**Placement rule:** every authority decision that depends on the remote chain runs on the **live dispatch stack**, never inside `doPrivileged`. Capture any remote-dependent data (e.g. the grantee conjunction) *before* entering a privileged block; then `doPrivileged` only the narrow local mechanics.

---

## 3. Channel model

A **channel** is the unit at which trust is assigned: **the exact (most-derived) class of an entry** — i.e. the `EntryHolder` the entry physically lives in. Each channel carries three ACLs — `read` / `write` / `take` — and each ACL is one conjunctive grant of the form in §2, keyed on a `ChannelPermission(className, "{read|write|take}")`-style target. No new policy machinery: it is a river `PermissionGrant` evaluated by the live ACC, deny-by-default.

### Why exact-class, not declared-template-class (composition forces this)

An entry of `T extends S` *is* both an S and a T; its fields are the union (S's fields then T's, in canonical superclass-first offset order — the same alignment that lets a supertype template match a subtype entry). The space returns **whole entries** — `EntryHolder.hasMatch` → `handle.rep()` hands back all of T, S-portion and T-specific fields together. There is no partial/projected read.

Therefore, gating on the **declared template class** would be unsound: a caller holding only `channel[S].read` could issue an S-template, `find` would descend into `holder[T]`, and the caller would receive the **entire T-entry including T-specific fields**. A low-trust supertype channel would become a *skeleton key* to exfiltrate every subtype entry whole.

The sound rule: an entry's governing channel is its **most-derived (exact) class**, checked at **each holder the query traversal actually touches**. This separates the two roles inheritance was conflating:

- **template hierarchy → search scope** (which holders `TypeTree.subTypes` visits)
- **exact-class channel → visibility** (authorization per visited holder)

A caller cannot escalate by *declaring* a sensitive subtype either: declaring `T` still has to clear `channel[T]` at `holder[T]`. The declared type never grants — only the per-holder exact-class gate does.

---

## 4. Auth-before-match

The associative matcher is itself a read oracle: a template probe reveals whether a matching entry exists. Authorization must therefore gate **before** any match work runs.

### Grounded path (trunk `fe1861e69`)

- `read` (`OutriggerServerImpl.java:1956`), `take` (`:1968`), `readIfExists` (`:1980`), `takeIfExists` (`:1992`) → all funnel through `getMatch(...)` (`:2403`).
- `getMatch`: `typeCheck(tmpl)` (`:2407`) → `enterTxn(tr)` (`:2421`) → `find(...)` (`:2468`) → `completeTake` (`:2474`) / `handle.rep()` (`:2476`).
- `find` (`:2693`): `whichClass = tmplRep.classFor()` (`:2698`) → `subtypes = types.subTypes(whichClass)` (`:2704`, *class and all subtypes*) → loop: `holder = contents.holderFor(className)` (`:2715`) → `holder.hasMatch(...)` (`:2716`).
- `EntryHolder.hasMatch` (`:129`): scan `for (EntryHandle handle : content)` (`:137`) → quick-reject (`:149`) → `tmpl.matches(rep)` (`:154`) → `confirmAvailabilityWithTxn` (`:157`).

### Gate placement

1. **Coarse gate at the top of `getMatch`**, after `typeCheck(tmpl)` (`:2407`) and **before** `enterTxn` (`:2421`): an unauthorized caller never triggers the transaction join / outbound `mgr.join`. (Fast-path optimization only — see step 2.)
2. **Authoritative per-holder gate inside the `find` loop** (`:2710`–`:2715`), before each `holder.hasMatch` (`:2716`): check `channel[className].read` (or `take`) for the exact class. **Skip** holders the caller cannot read rather than failing the whole query.
3. The gate runs **strictly above** `hasMatch` and the field index, so an unauthorized caller triggers zero match work — no existence signal, no index timing signal.

### Skip, not deny

For a supertype query that spans channels, silently skip unreadable holders. Skip is both more usable and more confidential than a hard deny: a deny would itself signal "an unreadable subtype channel exists," leaking channel existence; skip makes an unreadable subtype indistinguishable from an empty one.

### No unguarded path

The same `authorizeChannelRead(tmpl)` / `authorizeChannelTake(tmpl)` helper must gate **all** query surfaces, not just `getMatch`:

- single-template read/take via `getMatch` (`:2403`)
- `take(EntryRep[]...)` takeMultiple (`:2004`, own subtypes loop `:2072`)
- `contents(...)` JS05 iterator (`:2740`, `ContentsQuery`)
- `notify` / event registration (separate entry point — see §6)

This is *finer* than JERI's `BasicInvocationDispatcher.checkAccess`, which authorizes the *method* ("may you call `take`?"). Channel auth ("may you read entry class C?") needs the template and so must live in the service.

---

## 5. Write, take, and notify

- **Write** (`write` → `addWrittenRep` → `EntryHolder.add`, `:543`): gate on `channel[exactClass].write`; then **stamp writer provenance** — the space records the writer's authenticated principal set onto the entry envelope. Readers trust the space's stamp, never a self-asserted claim in the payload.
- **Take**: the `getMatch` read path with `takeIt=true`, additionally requiring `channel[exactClass].take` (strictly ≥ read). For a capability offer, after a match is found, enforce the **target binding**: `offer.target_principal_set ⊆ caller's authenticated principals` (the generalized `target == caller_SVID`).
- **Notify** (event registration): apply the same `channel[exactClass].read` gate at registration **and** re-check at delivery time (the registrant's authority may have lapsed). Without this, `notify` is an async read oracle — a registrant learns of writes it could not read.

---

## 6. install-on-take (capability delivery)

A taken offer may itself *be* a capability. The grant materialises at take, bound to the verified grantee:

1. **On the dispatch thread (remote ACC live):** evaluate `channel.take` + the target binding, then **capture the grantee conjunction as data** — `{remote-SVID, user principals, grantee codebases} ∩ offer terms` — and build the ephemeral `PermissionGrant` predicate from it.
2. **Then** `doPrivileged` only the narrow local step: install the grant into the local ephemeral policy store under the *space's own* authority. This correctly does **not** require the remote caller to hold "modify-policy," and no longer needs the remote ACC because the remote binding is already baked into the grant's *content*.

The installed grant is an **ephemeral** `PermissionGrant` (`impliesEphemeral` = not-cached / not-recorded), deny-by-default on expiry. Because it requires the grantee's **process SVID**, a restored or migrated agent (new SVID) no longer satisfies it → deny: this structurally delivers the "in-flight escalation must not survive restore" property with no nonce. (See `DESIGN-AgentEscalationGate.md`.)

For the transactional path, the transaction manager is **outside the authority TCB** — it is a commit/abort timing trigger only; the grantee identity is captured at take, not derived at commit. A **non-transactional take** installs for the live `getClientSubject()` grantee inside the dispatch and avoids the manager entirely.

---

## 7. Performance and concurrency

Per-holder gating (§4 step 2) is cheap on the DirtyChai substrate:

- **Virtual threads** are fully supported and carry an ACC; dispatch runs on a vthread.
- **ACC is immutable and cached** — capture the caller's conjunction snapshot once at `getMatch` entry and reuse it for every holder in the `find` loop; no rebuild.
- DirtyChai `CombinerSecurityManager` **caches verified `(ACC, perm)` pairs** (§5.1, 20 s TTL), so repeated `ChannelPermission` checks across many subtypes mostly hit the cache.
- Permission checks are **non-blocking on Policy** (volatile grant-array read + thread-local work), so the gate does not serialize the lock-free `hasMatch` scan or contend with virtual-thread concurrency.

Net: the only real cost of per-holder enforcement (N checks per query) is absorbed by the cache and the non-blocking path.

---

## 8. Why the read oracle is closed

A probe leaks only if matching runs for an unauthorized principal. The gate is (a) above the matcher and the field index, (b) applied to every class actually touched, and (c) evaluated on the live stack so it sees the full conjunction. An unauthorized caller receives a uniform deny/empty with no match work performed — no existence signal, no timing signal from the index. The fast field index, living inside `hasMatch`, never runs upstream of the gate, so index speed cannot widen a leak the gate has already closed.

---

## 9. Residuals and open decisions

- **No partial read (accepted):** an `S`-only reader cannot read the S-portion of `T`-entries. Conservative-correct; if S-projection is genuinely needed it is an explicit "view" entry written into `channel[S]`, not a relaxation of the gate.
- **Confused-deputy at the principal-delegation level (discipline, not structure):** Outrigger holds broad space-running authority; when acting for a lower-privileged caller it must down-scope to the caller's verified principal + grantor-authored offer terms. Enforced by the §2 placement rule, not by the platform.
- **Un-taken offer renewal:** who renews an un-taken offer's residence-lease when the grantor is absent (reuses Outrigger entry-lease + landlord machinery).
- **Channel registry:** how `ChannelPermission` grants are administered and how a new entry class acquires its ACLs (deny-by-default means an un-provisioned class is unreadable).

---

## 10. Code references (trunk `fe1861e69`)

| Element | Location |
|---|---|
| read/take/readIfExists/takeIfExists | `OutriggerServerImpl.java:1956,1968,1980,1992` |
| `getMatch` (single-template chokepoint) | `:2403` |
| `enterTxn` (txn join) | `:2421` |
| `find` (subtypes traversal) | `:2693`, subTypes `:2704`, hasMatch call `:2716` |
| `take(EntryRep[]...)` takeMultiple | `:2004`, subtypes `:2072` |
| `contents` (JS05 iterator) | `:2740` |
| `EntryHolder.hasMatch` | `EntryHolder.java:129`, scan `:137` |
| per-class content queue | `EntryHolder.java:50` |
| `EntryHolder.add` (write sink) | `EntryHolder.java:543` |
| `TypeTree.subTypes` | `TypeTree.java:195` |
