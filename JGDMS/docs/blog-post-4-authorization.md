# Part 4 — Authorization Without a Single Point of Trust

*This is the fourth post in a six-part series on JGDMS and DirtyChai.
Recommended prerequisites: [Part 2](blog-post-2-scap.md) and [Part 3a](blog-post-3a-identity-model.md).*
*The series index is at the bottom of this post.*

---

Part 2 described SCAP: a five-host pipeline that audits every JAR for dangerous bytecode before
any client deserializes an object from it. But the pipeline operates at the *network perimeter* —
it catches bad JARs as they arrive. What if a JAR passed SCAP at registration time, and then its
contents changed? What if SCAP was temporarily unavailable and a stale `SAFE` verdict was used?
What if the JAR was tampered with at the CDN, between the Verdict Registry lookup and the actual
class loading?

`DigestGrant` is the policy-level backstop: a permission grant that is conditioned on the SHA-256
content hash of the JAR, not merely its URL or a SCAP verdict. Even if a JAR somehow slipped past
SCAP, it cannot acquire any permissions unless its hash matches a policy entry. The two layers are
complementary defenses in depth — SCAP is the fast pre-filter; `DigestGrant` is the catch-all.

---

## Hardened Deserialization: `@AtomicSerial`

Before the authorization layers, there is a serialization layer. Java object deserialization is one
of the richest attack surfaces in enterprise software. JGDMS replaces standard
`Serializable`/`readObject` with `@AtomicSerial` — an annotation and constructor protocol that
requires:

1. A `(GetArg)` constructor whose **first action** is calling a static `check(GetArg)` method to
   validate all field values *before* any field is assigned.
2. A `static SerialForm[] serialForm()` method declaring the expected serial shape.
3. Complete prevention of three classic deserialization vulnerabilities:
   - Instantiation of arbitrary classes via stream manipulation
   - Denial of service via circular reference or OOME
   - Reference theft from a partially-constructed object

When the `AtomicInputValidation.YES` constraint is in effect, the JERI dispatcher switches to
`AtomicMarshalInputStream`, ensuring every deserialized argument across the wire has passed its
invariant checks. This ties directly to the authorization model: `AtomicInputValidation.YES` is
expressed as a method constraint, and method constraints are enforced by the policy layer before
any bytes leave the client JVM.

---

## Content-Hash Policy Grants: `DigestGrant` and `DigestCodeSource`

A URL is not a reliable security boundary — the same URL can serve different bytes after a CDN
update or supply-chain compromise. DirtyChai extends the policy language with two new types that
condition grants on the **SHA-256 content hash of the JAR**, not merely its URL:

- **`DigestCodeSource`** — a `CodeSource` subclass that carries the JAR's `byte[] digest` and
  `String digestAlgorithm`. `SecureClassLoader` populates this when loading from a
  content-verified JAR.
- **`DigestGrant extends URIGrant`** — a policy grant that checks the URI first and then verifies
  `DigestCodeSource.getDigest()` matches. Expressed in policy files as:

```
grant digest "SHA-256:4e07408562bedb8b60ce05c1decafe11",
      codeBase "https://repo.example.org/order-processor.jar"
      principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org" {
    permission net.jini.security.AccessPermission "submitOrder";
};
```

Even if an attacker replaces the JAR at the same URL, the content hash does not match and the
grant simply does not apply. `DigestGrant` is integrated with `PermissionGrantBuilder` and
`DefaultPolicyScanner`/`DefaultPolicyParser` so the full `SecurityPolicyWriter` → policy-file
round-trip works transparently.

> **The digest is a risk discriminator, not merely a grant key.** Because the hash names the
> *exact bytes*, it cuts both ways: a known-good digest can be granted authority, and a
> known-**vulnerable** digest can be refused or blocklisted regardless of who serves it. The
> corollary is the fail-closed default: **code with no identifiable digest is assumed
> worst-case** until proven otherwise. On DirtyChai every domain is identifiable (the
> `SecureClassLoader` stamps a `DigestCodeSource` into *every* `ProtectionDomain`); on a stock
> JVM, only `httpmd:` codebases carry an in-band digest — so serving *all* code, even "local"
> code, via `httpmd:` is what buys you full fidelity. Domains that arrive with no digest are
> never trusted on faith; they reduce, they do not elevate (see the receiving-side discussion
> below).

---

## Three-Layer Authorization Stack

Authorization in JGDMS is not a single on/off switch. It is a composable stack of three policy
providers.

> **A note on composition (status clarification).** The "three layers" below are a *wrapping
> and assembly convention*, **not a hard-wired chain**. `DynamicPolicyProvider` and
> `RemotePolicyProvider` each generically wrap **any** base `ScalableNestedPolicy`/`Policy` —
> they are not bound to the specific `SpiffePolicyFile` → `RemotePolicy` → `DynamicPolicy`
> ordering shown here. That ordering is a *deployment assembly* chosen by whoever wires the
> providers; the types themselves impose no fixed sequence. (Code review,
> `docs/agent-authority-code-review-2026-06-14.md` §2 claim 1, §5.) The arrangement below is
> the recommended assembly, not an invariant of the implementation.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                  Three-Layer Policy Stack (outermost first)              │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  DynamicPolicyProvider  (per-proxy, GC-scoped grants)              │  │
│  │    • Security.grant() called at proxy-preparation time             │  │
│  │    • Grant dies when proxy is garbage-collected (auto-cleanup)     │  │
│  │    • Hot path is write-free (sweeper runs every 60 s)              │  │
│  │  ┌──────────────────────────────────────────────────────────────┐  │  │
│  │  │  RemotePolicyProvider  (djinn-wide, session grants)          │  │  │
│  │  │    • Grants pushed live from InMemoryPolicyService           │  │  │
│  │  │    • replace() via RemoteEvent — no service restart needed   │  │  │
│  │  │  ┌────────────────────────────────────────────────────────┐  │  │  │
│  │  │  │  SpiffePolicyFile  (bootstrap, JVM lifetime)           │  │  │  │
│  │  │  │    • Fetched from HTTPS server authenticated by SVID   │  │  │  │
│  │  │  │    • Fail-secure: JVM won't start if server down       │  │  │  │
│  │  │  │    • Refreshes on every SVID rotation (~1 hour)        │  │  │  │
│  │  │  └────────────────────────────────────────────────────────┘  │  │  │
│  │  └──────────────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│              Three-Way Permission Intersection at Grant Time             │
│                                                                          │
│   What the proxy's ClassLoader declares   (META-INF/PERMISSIONS.LIST)    │
│                       ∩                                                  │
│   What the caller is authorised to give   (GrantPermission ceiling       │
│                                            in RemotePolicyProvider)      │
│                       ∩                                                  │
│   What the SPIFFE principal scope permits (principal scoping on          │
│                                            DynamicPolicyProvider grant)  │
│                       =                                                  │
│   Effective dynamic grant, scoped to this specific authenticated         │
│   endpoint instance.  No single party controls the outcome.              │
└──────────────────────────────────────────────────────────────────────────┘
```

| Layer | Grant issuer | Grant lifetime | Revocation |
|---|---|---|---|
| `SpiffePolicyFile` | Operator (HTTPS bootstrap server) | JVM lifetime | `refresh()` on SVID rotation |
| `RemotePolicyProvider` | Administrator via `InMemoryPolicyService` | Djinn session | `replace()` via `RemoteEvent` |
| `DynamicPolicyProvider` | Client via `VerifyingProxyPreparer` + `GrantPermission` | Proxy reachability | Automatic on GC |

The effective permission for any codebase is the **intersection** of what the code declares it
needs (`PERMISSIONS.LIST`), what the grant ceiling allows (`GrantPermission`), and what the SPIFFE
principal scope permits. No single party controls the outcome unilaterally.

> **Mechanism clarification (from the code review).** The "intersection" here is *conjunctive
> gating of grant applicability*, not an arithmetic set-intersection of permission lists.
> *Within* a single matching grant, permissions are unioned; the **meet** is across the
> *gating dimensions* — a grant contributes nothing unless **all** of {codebase/digest,
> all-principals-present, `GrantPermission` ceiling} hold. Adding a `principal`, `digest`, or
> `codebase` clause can therefore only *remove* matching domains — which is why "adding terms
> narrows" is true at the clause level. (See `docs/agent-authority-code-review-2026-06-14.md`
> §2 claim 2, §5.)

> **Scope clarification: this stack governs *proxy loading*, not inbound call authorization.**
> The three-layer stack and the three-way intersection above describe **dynamic grants issued
> while preparing a downloaded proxy** — `Security.grant(DownloadPermission + URLPermission)`
> at proxy-preparation time, scoped to a specific authenticated endpoint, dying when the proxy
> is garbage-collected. That is the *proxy-loading* path, and it happens on whichever side
> downloads a proxy (client **or** server). It is a **different mechanism** from authorizing an
> **inbound dispatched call** (an `AccessPermission` on a method a remote peer invokes on you).
> The inbound path makes **no dynamic grants at all** — the receiver's **static** policy is a
> hard ceiling, and an inbound call can only ever be *reduced within* that ceiling, never raise
> it. The next section is about that inbound path. (Design:
> `docs/DESIGN-spiffe-authorization-acc-transmission-2026-06-27.md` §3.)

### Authorizing an Inbound Call: Two Gates, Not One Merged Context

SPIFFE splits two things classic River conflated. The authenticated mTLS peer is a **workload**
(a SPIFFE SVID — the service process), *not* the human. The human is a separate **JWT** identity.
So an inbound `AccessPermission` is evaluated over **two separate gates, anchored on two separate
contexts — never one merged ACC**:

- **Gate 1 — workload.** The method's `AccessPermission` is tested against the **remote
  connection context**: the principals the mTLS connection actually authenticated (the
  `SpiffePrincipal`), plus the caller's transmitted codebase domains, which only ever *reduce*
  authority (an unidentifiable domain is kept as a reducer, never dropped — dropping it would
  *elevate*). This gate always runs; it answers *"may this workload, over this connection, reach
  this method at all?"*
- **Gate 2 — user.** Opt-in per method/interface. The method's `AccessPermission` is tested
  against the **validated user subject** (`Subject.callAs(userSubject, …)` — a `UserSubject` is
  bound with `callAs`, never `doAs`, which rejects it). It answers *"is this
  human authorized for this operation?"*
- **Admin / sensitive methods require BOTH** — a trusted workload **and** an authorized user.
  A trusted workload with no admin user is denied; an admin JWT arriving over an untrusted
  workload is denied.

The point of two anchors is that the workload's identity and the user's identity must **not**
silently merge into one elevated context: identity is *additive* (a principal only counts if it
was authenticated), whereas codebases are *subtractive* (they only reduce). Merging them would let
an authenticated workload's principals satisfy a check that should have required the user.

The static policy expresses both halves — a workload-scoped clause (matched in Gate 1) and a
user-scoped clause (matched in Gate 2), each optionally conditioned on the JAR digest:

```
// Gate 1 — workload reachability (matched against the authenticated connection)
grant codeBase "httpmd://repo.example.org/order-processor.jar#SHA256:abc123"
      principal net.jini.jeri.ssl.SpiffePrincipal "spiffe://.../svc/order-processor" {
    permission net.jini.security.AccessPermission "submitOrder";
};

// Gate 2 — user authorization (matched against the validated JWT subject under callAs)
grant principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org" {
    permission net.jini.security.AccessPermission "submitOrder";
};
```

> **The JWT is trusted as a *token*, not because of the *conduit*.** A user JWT is validated
> **per receiver, on every hop** — signature against the trusted issuer, plus `iss` / `aud`
> (this receiver's own audience) / `exp` / proof-of-possession — *before* it becomes the Gate-2
> subject. A token that does not validate is dropped, and Gate 2 fails closed. A receiver never
> re-mints a token at ingress to speak for the caller downstream; tokens are forwarded unchanged
> and re-validated at the next hop, and multi-hop reach comes from the IdP **issuing**
> appropriately-scoped tokens, not from an intermediary minting them. (The ambient workload
> `WorkerSubject`, by contrast, is *never* passed through `Subject.doAs`/`callAs` — it is reached
> only as the process's ambient identity.)

**Why `callAs`, not `doAs`.** Gate 2 binds the user with `Subject.callAs(...)`, never the older
`Subject.doAs(...)` — and DirtyChai is deprecating `doAs`/`doAsPrivileged` outright. It rests on the
same separation the two gates rely on: *who* you are (identity) and *what privileges the code runs
with* (the boundary) are orthogonal axes. Identity rides a `ScopedValue` and survives `doPrivileged`;
the boundary is `AccessController.doPrivileged(...)`, which truncates the stack and correctly sheds
the *remote caller's* `WorkerSubject` (it lives on those frames) without touching the user. `doAs`
fused the two — and that fusion is a footgun: use it to *run privileged* and you also drop the user,
because `doAs` rebinds the subject and suppresses the enclosing one. Dropping the user must be
deliberate (`callAs` with no user subject = run as the process only), never a side effect of wanting
a code boundary. So: `callAs` for *who*, `doPrivileged` for the *boundary*.

What can an attacker do with exactly one axis compromised? The combination still requires breaching
three independent systems — but now via two gates plus a digest condition, not one merged grant:

| Compromised axis | What the attacker can do | What they cannot do |
|---|---|---|
| JAR replaced at the URL (different SHA-256) | Serve a new JAR | Acquire `submitOrder` (hash mismatch; and a *known-bad* digest can be blocklisted outright) |
| SPIFFE workload credential stolen | Pass Gate 1 as the service process | Pass Gate 2 — `submitOrder` on an admin method still needs Alice's validated JWT |
| JWT credential stolen (Alice's token) | Present Alice's token | Pass Gate 1 from a different workload, or past per-receiver validation if the token is expired / wrong-audience / lacks PoP |

All axes must hold simultaneously for an administrative call to authorize — and because the gates
are separate, compromising the *workload* does not buy you the *user*, and vice-versa.

![The Permission Burger: decorative illustration of the three-layer authorization stack as a stacked burger](images/permission-burger.svg)

> **See also:** [Diagram 2 — Three-layer policy stack](<Big picture security architecture/diagram2_three_layer_policy_stack.svg>) · [Diagram 4 — GrantPermission & role management](<Big picture security architecture/diagram4_grantpermission_role_management.svg>)

---

## Dynamic Policy Updates

The `RemotePolicyProvider` and `InMemoryPolicyService` enable live policy updates — administrators
push a new policy without restarting any service. The `DynamicPolicyProvider` handles per-proxy
grants that are automatically cleaned up when proxies are garbage-collected. Neither requires a
service restart.

---

## Policy File Generation Tooling

JGDMS provides tooling to generate policy files for auditing before deployment. One thousand lines
of policy file are far easier to audit than one million lines of third-party library code. Generated
policy files can be committed to version control and reviewed in pull requests — the full permission
surface of a deployment is a reviewable artifact.

---

## No Keystore Management

With SPIFFE/SPIRE integration, administrators manage identities through SPIRE registration entries
— not Java keystores. The SPIFFE ID naming convention is human-readable:

- `spiffe://jgdms.example.org/host/lookup` → Lookup Service
- `spiffe://jgdms.example.org/host/bae/engine-2` → second BAE instance
- `spiffe://jgdms.example.org/client/alice` → client JVM for user Alice

Credential rotation is automatic and zero-touch. Revocation is immediate (short-lived SVIDs expire
within an hour). Scaling up adds a new SPIRE registration entry, not a certificate signing ceremony.

---

## Series Index

| # | Title | Depends on |
|---|---|---|
| 1 | [Overview: why fork both Apache River and OpenJDK?](blog-post-1-why-fork.md) | — |
| 2 | [SCAP: auditing JARs before they are loaded](blog-post-2-scap.md) | — (standalone) |
| 3a | [The identity model: three principals on every dispatch thread](blog-post-3a-identity-model.md) | 1 |
| 3b | [Multi-Subject dispatch and distributed transaction authorization](blog-post-3b-multi-subject.md) | 3a |
| **4** | **Authorization without a single point of trust** *(this post)* | 2, 3a |
| 5 | [Virtual threads, lock-free policy, and why security does not have to be slow](blog-post-5-performance.md) | 1–4 |

---

*GitHub repositories:*
- *JGDMS: <https://github.com/pfirmstone/JGDMS>*
- *DirtyChai: <https://github.com/pfirmstone/DirtyChai>*
