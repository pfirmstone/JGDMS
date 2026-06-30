# Design note — SPIFFE/JWT authorization & ACC transmission over JERI

Status: agreed design (2026-06-27), pre-implementation. Captures the model converged in
discussion before changing `BasicInvocationDispatcher.checkClientPermission`, the SSL
endpoint's client-subject construction, and JERI ACC transmission.

## 1. Problem

With SPIFFE, a JERI/mTLS connection authenticates a **workload** (the process's SVID), not a
**human user**. Humans authenticate separately as **JWT subjects** that may ride along a call
and across process hops. Classic River conflated the two (the authenticated peer principal *was*
the user, an X500 identity), so per-method `AccessPermission` doubled as user authorization by
accident. That conflation must be undone deliberately: workload identity and user identity are
two different things with two different trust anchors.

## 2. Identity model

| Identity | Source / anchor | How it's trusted | Where it lives |
|---|---|---|---|
| **Workload** (SPIFFE) | the mTLS client cert / SVID (subject DN **and** URI SAN) | re-authenticated by the connection at **every hop** — never relayed hearsay | a principal in the transmitted ACC |
| **User** (human) | a JWT minted by an IdP | validated at the **receiver** (sig / iss / aud / exp), bound (aud + PoP + short TTL) | a separate validated *user subject*, NOT in the connection ACC |

Core asymmetry that governs everything below:

- **Identity is additive authority** → a forged/relayed principal escalates → principals must be
  *authenticated*. Only connection-authenticated principals enter the ACC; any subject that can't
  be validated is **dropped** (carried at most as unauthenticated provenance/context, never as a
  `ProtectionDomain` principal).
- **Codebases are subtractive authority** → in the AND-across-domains check, adding a domain can
  only hold-or-reduce the intersection → codebases need *not* be authenticated to be carried.

## 3. The transmitted ACC

`JERI` transmits, from the client call context, an ACC that the receiver folds into its own
context for the call. It contains exactly:

1. **Principals: the authenticated connection only.** The cert-bound workload principal(s) —
   both the X500 subject DN and the `SpiffePrincipal` from the URI SAN (`SpiffePrincipal.fromCertificate`).
   No user/JWT principals, no relayed/asserted principals.
2. **Remote `DomainIdentity`s from the client context, to reduce permission.** Carried because
   they can only subtract authority in the AND-across-domains check. **The *complete* domain set
   from the client's call context is transmitted — silently dropping any of it (e.g. the sender's
   local code) would *elevate* privilege, by removing a constraint that should apply.** Each is a
   remote
   **`DomainIdentity`** — DirtyChai's per-domain `DigestCodeSource` (URL plus content digest where
   present) — that is **never downloaded**: it is reconstructed as a `CodeSource`/`ProtectionDomain`
   purely to sit in the intersection, then scored by the receiver's own policy. Two grant classes
   apply, by design — it is **not** "digest ⇒ grants, no-digest ⇒ nothing":
   - **Operational, self-scoped permissions — granted by URL, with or without a digest.** A
     codebase needs certain permissions to *function as that codebase*: notably `URLPermission`
     to its own codebase URL (and `DownloadPermission`) so it can be fetched and can reach its
     origin. These are scoped to the codebase's own resources, only let it operate (not act with
     elevated trust), and are honored from the codebase's URL in the receiver's **static** policy,
     even when no digest is present — **provided that static policy permits `URLPermission` on that
     URL** (the ceiling below) — otherwise the intersection would strip the codebase's ability to do
     its legitimate job. The server issues **no dynamic grant** for these; transmitted codebases are
     scored against static policy only (dynamic grants are a proxy concern — see note).
   - **Elevated/trusted authority — granted only by digest.** Anything beyond "operate as this
     codebase" (broad file/socket/runtime authority) is conferred *iff* the codebase arrives with
     a content digest matching a `DigestGrant`. Integrity is pinned before the codebase is trusted
     with real authority.

   **The receiver's static policy is the ceiling.** Trusting the worker subject lets us *believe*
   the ACC's codebase list is **authentic** (the worker isn't fabricating which codebases it ran);
   it does **not** make those codebases **authorized** here. Whether a transmitted codebase carries
   `URLPermission` is decided solely by *this* receiver's **static** policy for that URL. If that
   policy does **not** grant `URLPermission` on the URL for this worker, the codebase URL simply
   **remains in the ACC as a reducing domain without `URLPermission`**, further restricting the
   call. So a codebase admissible on a permissive worker may be reduced on a hardened one — e.g. an
   SELinux-confined high-security process whose policy grants `URLPermission` to far fewer codebase
   URLs. The trusted client's transmission can only reduce *within* the receiver's posture; it can
   never pull the receiver above its own policy.

   **Dynamic grants belong to proxy *loading*, not to call *authorization* — including on the
   server.** The dynamic `Security.grant(DownloadPermission + URLPermission(codebaseUrl))`
   (URI-scoped, pre-digest, bounded by the per-codebase `GrantPermission` ceiling
   `ProxyPolicyGenerator` emits) fires in `PreferredProxyCodebaseProvider.resolve` whenever *any*
   process — client **or** server — unmarshals a proxy and must download its codebase so the proxy
   can operate. The server does download proxies: a remote object (proxy) arriving as a method
   argument **is** resolved server-side — but **after** the connection is authenticated, and in
   **isolation** — each unmarshalled proxy gets its own marshalling stream, its own endpoints, and
   its own resolving `ClassLoader` (a per-codebase `PreferredClassLoader`), separate from the
   inbound dispatch. So the two concerns never blur: inbound-call **authorization** is pure static
   reduction against the transmitted ACC (no grants issued); proxy **loading** — wherever it
   happens — is the isolated, post-auth, dynamic-grant path under the receiver's ceiling. The line
   is *authorization vs. proxy-loading*, not *client vs. server*.

   So a digest-less codebase keeps whatever operational URL-scoped permissions the receiver's
   policy allows it, but carries no `DigestGrant` authority; it still subtracts for everything else.
   Dropping it entirely would be fail-*open* (a lost constraint); keeping it as above is
   fail-*closed*.

   **Integrity bites where it matters — loading, not reduction.** The digest requirement is
   enforced when a codebase is *loaded* (fetched + classes defined — gated by `BootstrapPermission`
   + content verification, the boot window). For the permission-*reduction* role the codebase
   defines no classes, so an unverified URL domain is safe: it can only subtract, and a client
   that claims a permissive URL merely *declines to reduce* (the omission case in §3 caveat),
   never escalates.

   **Digests discriminate risk, not just confer trust.** A digest positively *identifies* a
   codebase, which the receiver's policy can use both ways: grant a known-good digest, **and reduce
   or refuse a known-vulnerable one** (a digest blocklist — e.g. a CVE'd library version). The
   corollary is the conservative default: **a domain without a digest is assumed to carry the
   vulnerability until proven otherwise** — you can't rule out the bad version, so you treat it as
   the bad version and reduce. So the no-digest case isn't merely "no elevated grant"; it's
   "worst-case risk assumed" — the fail-closed reading. This is exactly why a remote `DomainIdentity`
   is *present to reduce* even though it is never downloaded: the receiver needs it on the stack to
   apply the reduction, vulnerable-or-not.

   **On DirtyChai every domain is digest-stamped; off DirtyChai only httpmd codebases carry one.**
   DirtyChai's `SecureClassLoader` stamps a `DigestCodeSource` into every `ProtectionDomain` at
   definition (the `DIGEST_GRANT_PLAN` integration point), so a client on DirtyChai transmits an ACC
   in which *every* domain is identifiable by content — the receiver risk-scores each precisely
   (good / vulnerable / unknown). A client *not* on DirtyChai (e.g. an existing JERI 3.x install on
   Java 8) has no per-domain stamping, so its **only** digest-bearing domains are **httpmd
   codebases**, where the digest is in the `httpmd://…;sha-256=…` URL itself (the pre-existing JERI
   integrity mechanism, verified by the httpmd handler on download). Those stay identifiable and
   risk-scorable; its other domains — local `file:` code, plain `http:` codebases — carry no digest
   and fall to the worst-case assumption above. (The receiver therefore reads the digest from
   *either* a `DigestCodeSource` field *or* an httpmd URL parameter.) Net: DirtyChai endpoints get
   precise, more-permissive-where-warranted authorization across *every* domain; a non-DirtyChai
   endpoint still gets digest-based scoring for its downloaded httpmd codebases — precisely the part
   that carries third-party-code risk — with everything else conservative, fail-closed.

   **Two routes to universal digests — and why digest-less domains stay, not drop.** Because
   dropping a domain *elevates* (it removes a constraint), a digest-less local domain is **kept** as
   a reducer, never removed — its lack of identity merely means it scores to little and *reduces*
   the call (fail-closed), which is the correct outcome. The remedy for a legitimately-restricted
   call that still needs to *do* something is to make its code **identifiable**, not to drop the
   constraint. Two ways to get *every* domain identifiable: (a) run on DirtyChai (per-domain
   `DigestCodeSource` stamping), or (b) load all code — *including what would otherwise be local
   `file:` code* — from **httpmd URLs**, the code simply obtained from a codebase server elsewhere,
   with the digest riding in the URL. Route (b) yields full fidelity on a stock JVM with no DirtyChai
   dependency. Either way the invariant holds: nothing is dropped, unidentifiable code reduces, and
   identifiable code is authorized to exactly what the receiver's policy grants its digest.

Effective authority for a dispatched call:
```
authority = (receiver's own domains)
          ∩ for each transmitted codebase: (receiver's STATIC policy grants to it =
                URL-scoped operational permissions the static policy allows for that URL
              + DigestGrant authority iff its digest matches)
with principals = the authenticated connection only.   // server issues no dynamic grants
```

**Caveat (not a boundary):** the client chooses what it transmits, so a malicious client can
*omit* its reducing codebases (it can't add authority, but it can decline to subtract).
Transmitting them enforces least privilege for the *honest* confused-deputy case; the hard
authorization floor remains `receiver policy ∩ authenticated principals`.

**Resolved (D1, 2026-06-27):** the *complete* domain set crosses, and the receiver keeps **every**
domain as a reducer — dropping or not-crossing any would *elevate* (it removes a constraint). A
digest-less domain still reduces (fail-closed); the remedy for a legitimately-restricted call that
needs to act is to make its code identifiable (httpmd-served, or DirtyChai-stamped), never to omit
the domain. See §3.2.

## 4. Two-gate authorization

Per-method authorization is **two gates, two contexts, two anchors** — never one merged ACC.

**Gate 1 — workload/peer gate (always).**
`AccessPermission` evaluated against the transmitted connection ACC (§3). Intent: "may the
authenticated workload on the other end invoke this method at all?" This is the mesh-level
reachability decision, cryptographically bound to the connection. This is the corrected meaning
of `AccessPermission` going forward.

**Gate 2 — user gate (opt-in per method/interface).**
For methods that declare a user requirement (admin, destroy, config…), an additional check
against the **validated user subject**, e.g.:
```java
Subject.callAs(validatedUserSubject, () -> {
    sm.checkPermission(new AccessPermission("net.jini.admin.Administrable.getAdmin"));
    return null;
});
```
satisfied by `grant principal "<admin-role-or-jwt-id>" { permission net.jini.security.AccessPermission "...Admin.method"; }`,
i.e. only by the human's validated claims — never by the workload.

> **Use `callAs`, not `doAs`, for a sealed `UserSubject`.** DirtyChai's `Subject.doAs`/`doAsPrivileged`
> **throw `IllegalArgumentException` on a `UserSubject`** ("UserSubject must use callAs()") just as they
> do on a `WorkerSubject`. `doAs` accepts only a *plain* (legacy, non-sealed-subtype) `Subject`. Since a
> JWT-validated user subject is a sealed `UserSubject` (minted from the verified token), Gate 2 must bind
> it with `Subject.callAs(subject, Callable)` — which is also the primitive that `current()`/`currentAll()`
> read, and which folds the user principals into the check via `getContext()` without imposing a
> `doPrivileged` boundary. (See DirtyChai `SECURITY_MODEL.md` §10.)

**Constraint — the SPIFFE WorkerSubject is *ambient*, never a `doAs`/`callAs` argument.** DirtyChai's
worker subject is fetched via `Subject.processWorker()`, cannot be captured by `Subject.current()`,
and **throws** if handed to `Subject.doAs`/`callAs` (this is what caused the earlier
handshake/EOF regression — see the `isWorkerSubject` guard in `SslServerEndpointImpl.handleConnection`).
So worker identity must never travel as a subject argument:
- **Gate 1's** workload principals reach the check from the **transmitted ACC** — the
  connection-authenticated principals sit in the ACC's `ProtectionDomain`s, *not* established via
  `callAs(workerSubject, …)`.
- **Gate 2's** `doAs`/`callAs` takes **only validated *user* subjects** — regular JWT-derived
  `Subject`s, which are legal there. The example above passes `validatedUserSubject`, never a worker.
- The *server's own* worker is already ambient (`processWorker()`); nothing re-establishes it via
  `callAs`. Likewise any multi-subject `callAs` carries only user subjects, never the worker.

**Decision (2026-06-27): admin/sensitive methods require BOTH gates** — *trusted workload AND
admin user*. So:
- A compromised/authorized workload alone **cannot** self-administer (fails Gate 2).
- A valid admin token arriving over an unauthorized workload **cannot** administer (fails Gate 1).
- Ordinary service-to-service methods (DGC, lease renewal, join, discovery, space ops) declare
  no user requirement and run on Gate 1 only — they legitimately have no human.

Why not fold the user into Gate 1's ACC:
- **Different anchors** (cert vs JWT issuer); OR-ing them lets a workload principal accidentally
  named like a role become admin.
- **AND, not OR**: a single merged ACC can only express "any principal that grants it passes";
  admin wants conjunction.
- **No-user calls**: a Gate-1 that required user principals would break every internal call.

## 5. User-subject validation & transitive trust

- Validate JWTs **at the receiver**, statelessly (sig / iss / aud / exp). Trust the **token**, not
  the transmitting process — the process is a faithful *conduit of unforgeable credentials*, not
  an *oracle of identity*. "User U is here" with no verifiable token is hearsay → dropped.
- Bind tokens: **audience-scoped + proof-of-possession (DPoP-style) + short TTL**, so a relayed
  token can't be replayed to a different service and exfiltration alone isn't impersonation.
- Multi-hop: the **workload** is re-authenticated per hop (not relayed); **user tokens** are
  re-validated per hop. No hop is trusted for authenticity, only for forwarding.

**Resolved (D2, 2026-06-27): per-receiver validation.** Every receiving dispatcher independently
validates each JWT it is handed — signature / issuer / audience / expiry against the IdP's published
keys, plus the bound key (PoP) and that *its own* audience is named. No ingress minting and no
shared session state: a user token is **forwarded unchanged** and **re-validated at each hop**.
Rationale: stateless and trust-minimal (no hop is trusted for another's authenticity — only the IdP
is), and robust to multi-hop (a token validates identically regardless of how many processes relayed
it). Requirements: each receiver carries the IdP verification keys (JWKS) + its accepted
issuer/audience config; audience + PoP binding (§5) keep a forwarded token from being replayed to a
different audience or without its key. Where one user authority must legitimately reach several
services in a chain, that is handled by **token issuance** (appropriate audience(s), or per-service
tokens), not by intermediate minting — ingress-minting of *verifiable* attenuated capabilities
remains available as a later optimization *on top of*, never *in place of*, per-receiver validation.

## 6. Delegation / confused-deputy controls

Validation ≠ authorization. A compromised-but-authenticated workload holding a still-valid user
token is the canonical confused deputy; transmission can't fix it. Bound it with:
- **Attenuation-only**: a hop may pass down or reduce authority, never amplify.
- **Per-workload delegation policy** keyed on the SPIFFE principal (which workloads may carry which
  user authority).
- **Leasing + short TTL + revocation** — the `LeasedPermissionGrant` / dead-man-switch line of work.
- **Audit/provenance**: record "C acted as U via workload X, token T." Provenance is auditability,
  not authority.

## 7. Implementation touch-points (JGDMS)

1. `net.jini.jeri.ssl.SslServerEndpointImpl.getClientSubject` — build the client subject from the
   cert-authenticated principals: X500 subject DN **and** `SpiffePrincipal.fromCertificate(chain[0])`.
   (Today it adds only `getSubjectX500Principal()`, dropping the SPIFFE identity before authorization.)
2. `net.jini.jeri.BasicInvocationDispatcher.checkClientPermission` — Gate 1 against the transmitted
   connection ACC; ensure the check is **isolated to that ACC** (do not let `Security.create(...)`
   merge the dispatch stack — rely on the DirtyChai `checkAuthorized` fix returning null for an
   authorized caller, or build a non-merging ACC of the client PD).
3. JERI ACC transmission — carry the connection-authenticated principals (as ACC `ProtectionDomain`
   principals — **not** via `callAs(workerSubject)`) + the client's complete reducing domain set per
   §3 (digest-matched grants, digest-less domains kept as reducers scored by static policy); drop
   unvalidated subjects.
4. Gate 2 — a declarative per-method/interface user-authorization hook that runs `AccessPermission`
   against the **validated user subject** via `Subject.callAs` (a sealed `UserSubject` is **rejected by
   `doAs`** — "must use callAs()"; `doAs` is for plain/legacy subjects only);
   wire the validated user subject through the server context.
5. **WorkerSubject constraint (applies across 1–4):** the SPIFFE worker subject is ambient
   (`Subject.processWorker()`), cannot be captured by `Subject.current()`, and **throws** if passed
   to `doAs`/`callAs`. Worker identity is established ambiently and carried as ACC principals only;
   `doAs`/`callAs` ever take user subjects, never the worker. (Same property as the
   `SslServerEndpointImpl.handleConnection` `isWorkerSubject` guard — see
   [`jgdms-secure-codebase-download-grant`].)

## 8. Decisions (both resolved 2026-06-27 — model fully pinned)

- **D1 — RESOLVED (2026-06-27)**: the complete domain set crosses; receiver keeps **every** domain as a reducer (dropping/omitting elevates); digest-less ⇒ reduces (fail-closed); remedy for legitimate calls is httpmd-served / DirtyChai-stamped (identifiable) code, never omission.
- **D2 — RESOLVED (2026-06-27): per-receiver validation.** Each dispatcher independently validates every JWT (sig/iss/aud/exp + PoP + own-audience) against the IdP; tokens forwarded unchanged and re-validated per hop; no ingress minting; multi-hop reach handled by token issuance (audience/per-service), not intermediate minting.
