# SOW — Schema-digest validation via the bootstrap `CodebaseAccessor` proxy

**Status:** Design **validated — already realized by existing mechanisms; no new
service required** (JGDMS; AI-authored design note). 2026-06-29. See §0.
**Depends on:** STD-006 DER codec, `@AtomicSerial` `SchemaChain`, the bootstrap
`CodebaseAccessor` codebase-download protocol, blog-post-6 (DER on the JERI wire),
blog-post-7 (no codebase annotations in the stream).

---

## 0. Conclusion (TL;DR)

This SOW set out to design schema-digest validation via the bootstrap proxy. The
design discussion converged on a stronger result: **the existing architecture
already realizes it, and no new "schema validation service" is needed.** The trust
and correctness properties are provided by mechanisms already in the tree:

1. **Code trust** — SCAP validates the codebase digest fetched over the
   authenticated bootstrap `CodebaseAccessor` (blog-post-2); the `@AtomicSerial`
   classes become *trusted code*.
2. **Shape correctness / versioning** — `MarshalledInstanceCodec.decode` **always**
   decodes the payload against the *embedded* schema chain that encoded it
   (**S7.8 normative**, Step 3); the receiver's local `serialForm()` is never used
   to interpret the bytes. The Merkle **leaf digest IS the schema version**, and
   the decoder classifies `A_MATCH` vs `B_OR_C_MISMATCH` against the receiver's own
   `generateChain(receiverClass).leafDigest()`.
3. **Shape trust** — the trusted code *defensively unmarshals the untrusted schema*:
   the schema is pure layout (cannot load classes — DER has no annotations, types
   resolve against the validated loader gated by `DeSerializationPermission`; cannot
   inject state — reconstruction is by-name/typed/default-tolerant `GetArg` with
   `check()` invariants, no reflective set). So a *schema validation service is
   redundant* — it would re-check a shape the trusted contract already reads safely.
4. **DoS bound** — the hardened DER decoder (`DerInputLimits`, `MAX_NESTING`,
   `MAX_DOMAINS`).

Two pre-existing disciplines carry it (not new work): the decoder's bounds, and
`check()` completeness per `@AtomicSerial` level.

**Residual optional item:** an *embedded-schema self-integrity recompute*
(`recompute(embeddedChain).leafDigest() == rec.schemaDigest()`) for the
**persisted / no-TLS** case as defense-in-depth. On the live path, TLS + the
matched (schema, payload) pair + defensive decode already cover it.

The sections below (§1–§8) are retained as the reasoning record that led here.

---

## 1. Motivation

The DER wire form is schema-bearing: each `@AtomicSerial` level's `serialForm()`
becomes an `AtomicSerialSchemaRecord`, the records are SHA-256-chained into a
`SchemaChain` whose `leafDigest()` is a Merkle root committing the whole type
hierarchy, and an `@AtomicSerial` value travels as a `MarshalledInstanceRecord`
that carries `payloadBytes` **and** the schema chain (`schemaBytes` /
`schemaDigest`). The schema therefore describes the data inline (polyglot,
archaeology — blog-post-6).

The codebase half of the bootstrap already has a content-addressed **trust** and
**integrity** story:

- **Integrity:** the `httpmd://…;sha-256=<digest>` URL pins the downloaded jar's
  bytes; `CodebaseAccessor` exposes `getCodebaseDigest()` /
  `getCodebaseDigestAlgorithm()` / `getDigestOffsets()`.
- **Trust:** the receiver's policy pins authorized digests (`DigestGrant` /
  boot-window `BootstrapPermission`), an *independent* anchor decided a priori.

The **schema** has no parallel. The inline `schemaDigest` is *self-consistent*
(it commits its own bytes) but is never cross-checked against an independent,
authenticated reference. This SOW specifies that parallel — obtaining the schema
Merkle chain / digest over the authenticated bootstrap `CodebaseAccessor` channel
— and, crucially, scopes *what it is actually worth*, which is narrower than the
codebase case.

## 2. The mechanism

Extend the bootstrap `CodebaseAccessor` (or a sibling bootstrap interface) with a
schema accessor, fetched over the same authenticated mTLS bootstrap call already
used for `getClassAnnotation()` / `getCodebaseDigest()`:

```java
/** The Merkle leaf digest committing this proxy's @AtomicSerial schema chain. */
byte[] getSchemaDigest() throws IOException;            // SchemaChain.Result.leafDigest()
String getSchemaDigestAlgorithm() throws IOException;   // "SHA-256"
/** Optional: the full chain, for a class-less / polyglot reader. */
byte[] getSchemaChain() throws IOException;             // encoded AtomicSerialSchemaRecord[]
```

The receiver, after authenticating the bootstrap proxy, fetches the authoritative
`schemaDigest` and compares it to the `leafDigest()` recomputed from the inline
schema in the received `MarshalledInstanceRecord`. Mismatch ⇒ reject.

This is the *same content-addressing* as the codebase digest: trust a thing by the
hash of its bytes, obtained from an authenticated source.

## 3. The real principle: never trust the stream — validate the artifact

The load-bearing reason for this is *not* corruption protection. It is the core
JGDMS rejection of the Java Serialization design flaw:

> If you trust the **stream** to tell you which classes to load (and which shape
> to read), you have handed class-loading and decode control to whoever wrote the
> bytes. That is the root of deserialization gadget chains, the Warres
> class-resolution pathologies, and the SCAP `<clinit>` DoS.

The fix is to validate the stream's *claims* against **authenticated,
content-addressed, externally-vouched references** before loading or decoding
anything — and to do it symmetrically across both dimensions of "an object":

| Dimension | Claim in the stream | Authenticated source | Trusted validation |
|---|---|---|---|
| **Code** | codebase annotation + digest | bootstrap `CodebaseAccessor` (mTLS) | **SCAP** bytecode-audit service (quorum of isolated engines, blog-post-2) |
| **Shape** | inline `MarshalledInstance` schema + `schemaDigest` | bootstrap `CodebaseAccessor` (mTLS) | **schema validation service** *(this SOW — the missing parallel)* |

So the schema digest is **trust, not merely integrity**: validated against an
independent **schema validation service**, the schema is vouched *independently of
the peer*, exactly as SCAP vouches for bytecode independently of where it was
downloaded. TLS authenticates the *peer*; SCAP and the schema service vouch for the
*artifact* — different jobs, and the second is precisely "don't trust the stream."

This **supersedes** an earlier framing of this SOW that called the digest
"integrity, not trust — low priority because TLS+isolation cover it." That holds
only *without* a validation service: a digest merely *fetched from the peer* is
circular against a buggy/compromised peer, and on a live call TLS already supplies
in-transit integrity. The whole point of a **validation service** is to break that
circularity — the trust root is the service, not the peer — which is why it belongs
on the live path, not just the persisted case.

Where each still applies:

- **Live call:** the schema service is the trust anchor (peer may be wrong even
  when authenticated); TLS+isolation handle confidentiality/auth/in-transit
  integrity; the service handles *should I trust this shape*.
- **Persisted `MarshalledInstance`:** no connection at all — the carried
  `schemaDigest` validated against the service is the only trust+integrity check on
  re-read (the archaeology case).

## 3a. CONCLUSION — no schema validation service is needed (the trusted codebase decodes the untrusted schema)

The validation-service framing in §2–§3 was a step on the way; the design converges
one step further. Once the **codebase** digest is SCAP-validated, the `@AtomicSerial`
classes are *trusted code*, and a trusted class can **defensively unmarshal an
untrusted schema** — so a separate *schema* validation service is redundant.

The schema is only a layout description (field name → DER type). It cannot do the
two dangerous things:

- **It can't dictate class loading.** DER carries no annotations; nested field
  types resolve against the validated codebase/endpoint loader (SCAP-vouched),
  gated by `DeSerializationPermission`. The stream never says "load X from Y."
- **It can't inject state or run code.** Reconstruction goes through the trusted
  `GetArg`: by-name, typed, default-tolerant reads, with `check(GetArg)` enforcing
  invariants *before* assignment and no reflective field set. A hostile schema can
  present extra fields (ignored), missing fields (defaulted), or wrong-typed fields
  (type-rejected); `check()` rejects bad combinations.

A malicious schema's blast radius therefore collapses to **(a)** resource
exhaustion during decode — bounded by the hardened decoder (`DerInputLimits`,
`MAX_NESTING`, `MAX_DOMAINS`); and **(b)** a type-valid-but-semantically-bad value —
which is `check()`'s job and which a schema validation service would not catch
anyway (it validates shape, not values).

**So the trust anchor is the codebase digest (SCAP); the untrusted schema is read
safely by the trusted contract + bounded decoder. No new service.** Two assumptions
carry it, and both are pre-existing JGDMS disciplines, not new work:

1. **The DER decoder is genuinely hardened** (bounded size/nesting/elements) so a
   pathological schema cannot DoS the decode.
2. **`check()` completeness** — semantic invariants are actually enforced per level
   (the authored-contract discipline of blog-post-6).

The `schemaDigest` keeps only its honest role: **integrity** (corruption detection
and version-matching of the inline schema), not a trust gate. §2's accessor and
§4–§6 below are retained as *design record* for the integrity/persistence use, but
are **not required for schema trust** and are not on the critical path.

## 4. Direction asymmetry

- **service → client** (the smart proxy and method return values): the client
  authenticates the *service's* bootstrap proxy and can validate the service's
  schema digest against it. Clean.
- **client → server** (method *parameters*, e.g. `write(EntryRep)`): the server
  validates an incoming parameter's schema, so it needs an anchor for the
  *client's* schema, rooted in the client's authenticated identity, not the
  service's bootstrap proxy. The bootstrap channel can carry the client-side
  schema digest (and it can be updated dynamically), but the trust root differs.
  Given §3, this direction is even lower priority: parameters arrive over the
  encrypted, authenticated, private connection and need no validation for trust;
  a digest would only catch stream corruption.

## 5. Why the live path is already safe — isolation

The property that actually makes the live path safe, independent of any schema
digest:

> Every smart proxy arrives as its own `MarshalledInstance`, is given its **own
> separate authenticated endpoint** with a **provisioned `ClassLoader`**, and
> **streams are not shared across services**.

So a service's reducing-context block, method arguments, and return values all
live in **one service's isolated, authenticated stream** — there is no
cross-service schema/loader confusion to validate against in the first place. (This
is also why the §7.3 reducing-context-over-DER block is safe: it rides inside that
isolated per-service stream.) The schema digest adds *integrity*, not the
*isolation* that provides the safety.

## 6. Scope & phasing

- **Phase 1 — accessor + opt-in integrity check.** Add the `CodebaseAccessor`
  schema-digest accessor; server returns `SchemaChain.Result.leafDigest()` for the
  exported type; client, when enabled, recomputes the inline `leafDigest` and
  compares. Default off / advisory on the live path (TLS+isolation already cover
  it); useful immediately for persisted-instance verification tooling.
- **Phase 2 — persisted-instance verification.** Use the carried `schemaDigest`
  to verify a stored `MarshalledInstance` on re-read (no connection); surface
  mismatches.
- **Phase 3 (optional) — policy-pinned schema trust.** A `SchemaDigestGrant`
  analogue of `DigestGrant`: the receiver's policy pins authorized schema digests
  for designated types — the genuine *trust* gate, for deployments that want it.
- **Out of scope here:** the DER codec *type-coverage* gaps surfaced by the DER
  matching test (`long[]` return values, dynamic proxy in an `@AtomicSerial`
  field). Those are mechanical codec work, tracked separately.

## 7. Touch points

- `net.jini.export.CodebaseAccessor` (jgdms-platform) — new schema accessor(s).
- The bootstrap exporter / proxy that implements `CodebaseAccessor` server-side.
- `au.net.zeus.jgdms.der.schema.SchemaChain` / `AtomicSerialSchemaRecord` —
  `leafDigest()` is the value to expose.
- `MarshalledInstanceRecord` — `decodeSchemaChain()` gives the inline chain to
  recompute the leaf digest from.
- Client validation hook — alongside `PreferredProxyCodebaseProvider.resolve`,
  where the bootstrap `getClassAnnotation()` / codebase-digest path already runs.

## 8. Open questions

1. New method on `CodebaseAccessor` vs. a separate `SchemaAccessor` bootstrap
   interface (keep `CodebaseAccessor` minimal?).
2. Whether the live-path check is ever worth enabling by default, given §3 says
   TLS+isolation already cover it — current answer: no, opt-in only.
3. Phase 3 policy syntax for pinned schema digests (reuse `DigestGrant` machinery
   keyed on schema digest vs. a distinct grant type).
