# DESIGN — Attested Role-Neutral Worker Bootstrap (DirtyChai + SPIFFE + httpmd)

*Status: DESIGN / direction (2026-07-02). The trust primitives referenced here
exist; the end-to-end bootstrap pipeline (bootstrap `main`, HTTPS plan server,
role→plan mapping, and copying the httpmd provider into `java.base`) is not yet
built. Portions in `java.base` are **DirtyChai** — advisory only here; the DirtyChai
maintainers write that source (OpenJDK no-AI-contribution policy).*

## 1. Summary

A single, immutable base image — **DirtyChai (the hardened JVM) plus a minimal
bootstrap** — is a **role-neutral worker**. Nothing role-specific is baked in. What
the worker *becomes* — a Reggie, a Mahalo, an Outrigger, a client, anything — is
decided entirely at runtime by two attested inputs:

1. the **SVID** it is issued by SPIRE (who it *is* — its SPIFFE identity encodes its
   role), and
2. the **content-verified, SPIFFE-authorized plan** it downloads for that identity
   (what it *does* — its codebases, configuration, and start sequence).

The whole JGDMS runtime and the role code stream in at runtime, content-verified and
authorization-gated; only the JVM/TCB is installed. This is the logical endpoint of
the same "download for compatibility and easier updating" rationale that motivates
downloadable proxy (`-dl`) jars — pushed all the way down to the runtime itself.

## 2. Goals / non-goals

**Goals.** One image to build, sign, scan, and patch (patch the JVM/TCB, not *N*
service images); role code and configuration never baked into the image; role,
config, and state selected by attested identity; supply chain and authorization
enforced in an unoverridable TCB; precise, content-addressed revocation.

**Non-goals.** This does not replace SPIRE (attestation/issuance) or the Jini
discovery/lookup fabric (used *after* a worker is running). It is the *bootstrap*
that turns an attested identity into a running role.

## 3. The two pre-existing trust primitives

- **httpmd — content integrity.** `net.jini.url.httpmd` (`Handler`,
  `HttpmdIntegrityVerifier`, `HttpmdUtil`, in `jgdms-url-integrity`) is a URL protocol
  handler whose `;sha-256=` parameter pins the fetched jar's content hash. It
  guarantees *these exact bytes*; it says nothing about authenticity/authorization.
- **SPIFFE + DirtyChai — identity & authorization.** DirtyChai's `java.base` carries
  the SPIFFE/SPIRE stack (`au.zeus.jdk.authorization.spire.SpiffeCredentialManager`,
  `SpiffeX509KeyManager`, `SpiffeX509TrustManager`, `SpiffeSubject`), the policy
  (`au.zeus.jdk.authorization.policy.SpiffePolicyFile`), `DigestGrant`, and the
  `LoadClassPermission` guard (`au.zeus.jdk.authorization.guards.LoadClassPermission`;
  JGDMS references it as `net.jini.loader.LoadClassPermission` via `UnresolvedPermission`
  in `PreferredProxyCodebaseProvider`, which already grants `URLPermission` +
  `LoadClassPermission` per codebase). SPIFFE IDs are already used as principal
  patterns in service grants (e.g. `spiffe://jgdms.example.org/host/policy`,
  `…/host/telemetry`, caller `…/client/<id>`), keyed via `PrincipalGrant`/`SpiffePrincipal`.

## 4. The two-factor load gate (the core security property)

A class from a downloaded codebase is *defined* only if **both** hold:

1. **Content integrity (httpmd):** the fetched bytes match the pinned `;sha-256=`.
2. **Authorization (SPIFFE policy):** that digest holds `LoadClassPermission` per
   DirtyChai's SPIFFE-anchored policy (`DigestGrant` keyed on the content digest,
   vouched by an SVID).

The digest thus does double duty — **integrity key *and* authorization key**. The
entire gate lives in `java.base`: boot-loaded, and **unoverridable by the very code
it gates**. The downloaded runtime *uses* these primitives; it cannot weaken them.
This is a verified-boot property extended from integrity to authorization.

## 5. The bootstrap sequence

```
1. JVM (DirtyChai) starts → runs the baked-in bootstrap main class.
2. Worker attests to SPIRE (node/workload attestation) → receives its SVID
   (SPIFFE ID = its role, e.g. spiffe://td/reggie), auto-rotated, short-lived.
3. Bootstrap main uses the SPIFFE Subject's credentials (the SVID) as its mTLS
   client identity to contact the bootstrap HTTPS server, mutually authenticating:
     - worker presents its SVID  → server returns the plan scoped to that identity
     - worker verifies the server's SVID (e.g. spiffe://td/host/bootstrap) against
       the trust bundle → decides whether to trust the plan
4. Server returns the PLAN for this identity:
     (a) codebase list  = the digest-keyed policy grants (LoadClassPermission)
     (b) configuration  = the net.jini.config.Configuration for this instance
     (c) start sequence = declarative launch plan (entry codebase/main, ordering)
5. Bootstrap fetches each granted digest (httpmd/CAS), verifies the digest, and the
   two-factor gate (§4) admits only granted content; installs the config; runs the
   sequence → the role is running (now it enters the JERI/Jini world).
```

The bootstrap tier deliberately speaks **plain HTTPS + SPIFFE mTLS, not a Jini call**
— so no proxy codebase must be downloaded to make the *first* request. That is the
chicken-and-egg breaker: you cannot `java -cp httpmd://…` (the system class loader
takes file paths, not protocol URLs), so a minimal launcher must exist statically;
making its first hop plain HTTPS avoids needing any downloaded code to perform it.

## 6. What the SVID conveys — identity, not a payload

The SVID carries a **SPIFFE ID** (`spiffe://trust-domain/path`) and little else — for
X.509-SVIDs, that ID in the single URI SAN plus a short validity window and the
signing chain; for JWT-SVIDs, `sub`/`aud`/`exp`. It is a **key, not a container**: the
role rides in the *path*, and everything else (grants, codebases, config) is looked up
*from* the identity. Stuffing the codebase list into the SVID would couple issuance to
authorization data, break under rotation, and violate SPIFFE's minimalism.

**Role = the SVID.** The role is assigned at SPIRE registration from *attestable*
selectors (node / container-image digest / k8s projected SAT — never a shareable join
token), so a worker cannot self-select a role: it gets the SVID its attestation
entitles it to, and the policy scopes it accordingly. The real authorization decision
("which workloads may become Reggie") lives in the SPIRE registration policy and is
only as strong as the attestor.

## 7. The codebase list = the policy grants, keyed on digest only

There is **no separate manifest**: the downloaded policy's grant list *is* the codebase
list. Filter the grants applicable to the SVID principal for those containing
`LoadClassPermission`; each such grant names its codebase.

Crucially, the grant keys on the **content digest only** — the `grant codeBase "<url>"`
clause is **commented out / kept only as a fetch hint**, not part of the trust
decision. Authorization is therefore pure content-addressing: a class defines iff its
exact content matches a granted digest, *regardless of source*. The URL moves from the
**authorization layer** to the **fetch layer** (the codebase annotation / a
content-addressed store / mirror / peer resolves digest→bytes).

Consequences:

- **Enumeration and authorization are the same object** — they cannot drift. Add /
  update / revoke a codebase = add / edit / drop a grant; revocation is content-precise.
- **Location independence** — any httpmd mirror, CAS, local cache, or offline peer can
  serve the bytes; only the digest gates loading. This is what makes the disconnected
  HaLOW/mesh profile viable (a peer hands you bytes; if the digest is granted, run it).
- **Untrusted mirrors by design** — a hostile mirror can only serve content that
  matches a granted digest (the exact authorized bytes) or does not (rejected). No
  URL/DNS/host enters the trust decision.
- **Dedup** — identical content across roles is one digest, one grant.

The digest's own trustworthiness comes from the **SVID-authenticated channel** the
policy arrived on — which is precisely why the URL can be treated as untrusted.

Seed from the role's **entry codebase** (the SVID path → the primary jar); its
dependency closure is the rest of the granted digests, each grant-covered so the gate
stays satisfied all the way down. Class resolution / the preferred class loader pulls
the closure; `CodebaseAccessor.getClassAnnotation()` supplies URLs and
`getCodebaseDigest()`/`getDigestOffsets()` the per-jar digests (computed by
`AbstractJiniService` at export via `CodebaseDigestUtil`).

## 8. The configuration

The plan's third leg is the **`net.jini.config.Configuration`** — the same thing the
ServiceStarter takes today (the `AtomicILFactory`, export/import codebases, invocation
constraints, discovery groups, persistence directory), except sourced dynamically
rather than as a static file.

- **Per-instance, not just per-role.** Two workers both attesting as `…/reggie` can
  share identical code digests but receive different configs (different discovery
  groups, persistence store, endpoint). The axis is `role = SVID`, `state = SVID`, and
  `config = SVID`.
- **Security-sensitive → code-grade integrity.** The config sets the exporter,
  ILFactory, and *constraints*; a tampered config could silently drop the mTLS
  requirement. So it is **content-pinned and delivered over the SVID-mTLS channel**,
  and it must stay **within the policy grants** — a config requesting a capability the
  policy does not grant fails closed under the SecurityManager. Config is a request
  bounded by the grant ceiling, never an escalation.
- **No secrets in the config.** The SVID *is* the credential (issued by SPIRE), so the
  config references "authenticate as my SVID" rather than embedding keys; keys never
  travel in the plan.

## 9. The bootstrap main — the seed

The one role-agnostic executable baked into the image. Properties:

- **Minimal — it is TCB** (it decides what loads). A declarative *interpreter* of the
  start sequence; no role logic.
- **Plain HTTPS + SPIFFE mTLS** for its first hop (§5).
- **Mutual authentication mandatory**; **fail closed** (become no role) on any auth or
  fetch failure — never fall back to loading arbitrary code.
- Applies the config, drives the (ServiceStarter-like) instantiation from the entry
  codebase, respecting the two-factor gate throughout.

## 10. Static image root vs dynamic

**Baked into the image (the static root):** the DirtyChai JVM + `java.base` river
security primitives; the httpmd URL verifier; the SPIRE trust anchor (bootstrap trust
bundle) + attestation client + SVID machinery
(`SpiffeCredentialManager`/`KeyManager`/`TrustManager`); the **bootstrap main**; and a
minimal static grant authorizing exactly those to run before any policy is fetched.

**Dynamic (fetched at runtime, content-verified, SPIFFE-authorized):** the SVID, the
SPIFFE policy (grants), the codebase digests + bytes (the whole JGDMS runtime and the
role code), the configuration, and the start sequence.

## 11. Security analysis

Trust roots reduce to: (i) the **SPIRE control plane / its trust bundle** (the apex —
whoever issues SVIDs and vouches for digests owns the fleet; centralised, so a single
high-value thing to secure and rotate); (ii) the **bootstrap main** (minimal, in TCB);
and (iii) the **two-factor gate** in `java.base` (unoverridable).

| Threat | Outcome |
|---|---|
| Compromised codebase mirror / CDN | Can only serve granted-digest content (the exact authorized bytes) or non-matching bytes (rejected). URL is not trusted. |
| Tampered configuration | Content-pinned + delivered over SVID-mTLS; a weakened config still cannot exceed the policy grants (SM fails closed). |
| Rogue bootstrap HTTPS server | Rejected — the worker verifies the server's SVID against the trust bundle before trusting any plan. |
| Attacker-chosen digest on the command line / plan | No `LoadClassPermission` for it → classes never define. The digest is not self-authorizing. |
| Rogue container tries to assume a role | Gets only the SVID its *attestation* entitles it to; the policy scopes `LoadClassPermission` to that identity. Weak attestation (leakable join token) is the real risk — use hardware/cloud/SAT roots. |
| Downloaded runtime tries to weaken enforcement | Cannot — the gate, policy, verifier, and SM live in `java.base`, boot-loaded, unoverridable by downloaded code. |

## 12. Sharp edges / open considerations

1. **Bootstrap window.** Before the SVID/policy exist, only the statically-granted root
   set may load; the static grant must cover exactly the bootstrap main + SVID
   machinery + httpmd verifier (same strictness as the existing DigestGrant boot-window
   gate). Keep this set minimal.
2. **Role-assignment authorization** happens at SPIRE registration (attestable
   selectors → role SVID); guard that surface and root it in strong attestation.
3. **Stateful roles.** Compute is fungible; state and identity are not. A worker
   adopting a stateful role (Mahalo log, Reggie registrations, Outrigger space) needs
   its persistent store bound to its identity (SVID-scoped volume/decryption). Cleanest
   for stateless/replica roles; stateful roles are "fungible compute + identity-pinned
   state."
4. **Offline / disconnected (HaLOW mesh).** The plan fetch assumes control-plane
   reachability; the disconnected profile needs a cached last-known `{SVID, policy,
   config, sequence}` (with expiry tolerance) or hardware-anchored self-issuance.
5. **The base image is the crown jewel** (it *is* the TCB): reproducible + signed
   build, audited `java.base` classes.

## 13. Relationship to existing JGDMS mechanisms

This composes primitives that already exist rather than inventing new ones:
`net.jini.export.CodebaseAccessor` (codebase URLs + per-jar digests, computed by
`AbstractJiniService`); `net.jini.loader.pref.PreferredProxyCodebaseProvider` (per-URL
`URLPermission` + `LoadClassPermission`, auth-before-download);
`org.apache.river.api.security.{PermissionGrant, PrincipalGrant, DigestGrant}`;
`SpiffePrincipal`; the policy service (`spiffe://…/host/policy`); the ServiceStarter
(`org.apache.river.start`) and `net.jini.config.Configuration`; and `net.jini.url.httpmd`.

## 14. Implementation status

**Exists:** the two-factor primitives (httpmd; SPIFFE policy + `LoadClassPermission` +
`DigestGrant` in DirtyChai); per-codebase grants; `CodebaseAccessor` digest publication;
SPIFFE-principal-keyed grants; the policy service.

**Missing / to build:** copy the httpmd provider into DirtyChai `java.base` (small,
bounded dep set — `net.jini.security.{IntegrityVerifier, Security}`,
`org.apache.river.logging.*`, optional `net.pack200.Pack200`; the OSGi annotations are
compile-time only); the **bootstrap main**; the **bootstrap HTTPS server** and the
role→plan (codebases/config/sequence) mapping keyed by SVID identity; the static
root-grant; the digest-only grant convention (comment the URL) in the policy generation
(`tools/policy-condenser`).

**DirtyChai portions are advisory only** — humans write `java.base` source.
