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

---

## Three-Layer Authorization Stack

Authorization in JGDMS is not a single on/off switch. It is a composable stack of three policy
providers:

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

### The Three-Principal Grant: Defending Against a Compromised Axis

Now layer in `DigestGrant` and `UserSubject`. A grant targeting a SPIFFE workload principal, a
human JWT principal, *and* a SHA-256 JAR hash means an attacker who controls exactly one axis
cannot escalate privileges:

```
// JWT/OIDC — preferred user identity
grant codeBase "httpmd://repo.example.org/order-processor.jar#SHA256:abc123"
      principal net.jini.security.jwt.JwtPrincipal "sub:alice@example.org"
      principal net.jini.jeri.ssl.SpiffePrincipal "spiffe://.../svc/order-processor" {
    permission net.jini.security.AccessPermission "submitOrder";
};
```

What can an attacker do with exactly one axis compromised?

| Compromised axis | What the attacker can do | What they cannot do |
|---|---|---|
| JAR replaced at the URL (different SHA-256) | Serve a new JAR | Acquire `submitOrder` permission (hash mismatch) |
| SPIFFE workload credential stolen | Impersonate the service process | Gain `submitOrder` without also being Alice |
| JWT credential stolen (Alice's token) | Impersonate Alice | Gain `submitOrder` from a different workload or with a different JAR |

All three axes must be simultaneously compromised for the grant to apply. That combination requires
a breach of three independent security systems.

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
