# SOW — DER `@Serializer` registry: determinism hardening + minimal population

**Status:** APPROVED — R1 (APPROVED-WITH-CHANGES, applied) + R2 (APPROVED), 2026-07-18 · **Owner:** JGDMS DER
**Reviewers:** R1 (determinism / DER-fidelity lens), R2 (security-adversarial lens) — both board reviews complete and code-verified against trunk.
**Reference:** `docs/board-guidance/` (`rule-fidelity-determinism.md`, `security-adversarial.md`, `asn1-der-module.md`, `transport-jeri.md`), `docs/JGDMS-Board-Reviewer-Guidance.md`.

---

## 1. Background

Migrating the qa activatable path to marshal proxies with ATOMIC DER (`DerMarshalledInstance`). Encoding a constrained reggie proxy, the DER codec walks a `MethodConstraints` graph and fails on `javax.security.auth.x500.X500Principal` (a `principals[]` element):

```
ObjectCodec: nested field 'principals[0]' has wireType @AtomicSerial
  but runtime type javax.security.auth.x500.X500Principal has no @AtomicSerial class in its hierarchy
```

DER has two replace mechanisms: (1) the java.io `Replace`/`Resolve` interface (now honoured by the codec), and (2) a `@Serializer(replaceObType=X)` registry loaded from classpath resource `META-INF/jgdms/der-serializers`, resolved by `au.net.zeus.jgdms.der.serial.DerReplacer`. A set of `@AtomicSerial`+`Resolve` serializers for JDK types exists in jgdms-platform `org.apache.river.api.io` (X500Principal, URI, Throwable, Properties, MarshalledObject, StackTraceElement, File; plus `UIDSerializer`/`DateSerializer` which are `Externalizable`, DER-incompatible). **No production `der-serializers` resource exists** — the registry is empty at runtime — and `serializerFor` selects non-deterministically.

## 2. Objective

Make DER able to marshal constrained proxies by (a) making serializer selection **deterministic**, (b) **closing** the registry to a spec-pinned platform-controlled set, and (c) **populating** it with the minimal, security-vetted set required — while explicitly deferring the serializers that carry unresolved security or canonicality defects.

## 3. Decision (both reviewers)

Adopt **Option 1's mechanism** (registry + `@AtomicSerial` serializers), **reduced and hardened**. **Reject** registering all seven. **Reject** Option 2 (native per-type codecs) as the general answer — it duplicates tested serializers, doesn't scale, and would re-implement the ATOMIC-gate + endpoint-`ResolutionContext` threading the registry path already reuses. (`X500Principal` alone would also be safe as a native opaque-octet leaf, but the registry is less code and reuses the audited nested-`@AtomicSerial` decode door.)

---

## 4. IN SCOPE — work items

### WI-1 — Harden `DerReplacer.serializerFor` to a deterministic rule *(BLOCKING; R1 §1/§2, R2 MUST-FIX 1)*
Replace the "first `isAssignableFrom` wins over a `LinkedHashMap`" fallback with:

> **exact-match → unique most-specific assignable → fail-closed on ambiguity.**

- "Most-specific" is defined over the full **class *and* interface** assignability partial order: the unique registered key `K` assignable from `target` such that no other assignable registered key `K'` satisfies `K.isAssignableFrom(K')`.
- **Incomparable multiplicity ⇒ throw `DerException`** at encode/schema-generation time (never silently pick one).
- The **same predicate** already backs `DerReplacer.isRegistered` → `SchemaGenerator` (declared type) and `replace` (runtime `value.getClass()`); keep it shared so schema and encode never diverge.

**Acceptance:** selection is a pure function of `(registered set, target type)`, independent of `getResources()`/insertion order. Concrete-class-only keys can never be ambiguous (superclass chains are totally ordered); ambiguity is reachable only via an interface key → fail-closed.
**Note (no DoS):** `serializerFor` runs only at encode/schema-gen over the sender's own trusted outbound graph; decode is name-driven via `DerReplacer.resolve` and never calls `serializerFor`. Fail-closed's only availability effect is a misconfigured registry failing loudly at first export — the correct fail-secure outcome.

### WI-2 — Close the registry to a spec-pinned, platform-controlled set *(BLOCKING; R2 MUST-FIX 3, R1 Guard B)*
The registered set is **part of the wire contract**: `isRegistered` feeds `SchemaGenerator`. For a **polymorphic (interface/abstract) slot** the top-level token is `@AtomicSerial` regardless of registration, but a classpath-dependent set makes the **wire form of the substituted value** a function of deployment classpath (deployment A names serializer `S` in the embedded schema-chain/payload; B names the native class) → breaks Entry byte-matching (asn1-der §7.7.2) and signatures **over the value**. For a **concrete-typed field**, registration flips schema-gen between `@AtomicSerial` and a hard throw (encode-vs-fail). Either way an open set lets an unaudited third-party jar contribute a reconstruction/gadget door.

- Load the production set from a **platform-controlled resource only** (not an open `getResources()` merge of every classpath `der-serializers`), **or** formally declare in STD-006/008 that the registered set is wire-affecting and additions are a **versioned schema change requiring board review + digest coordination**.
- **Guard (R1 Guard B):** adding an **interface-keyed** serializer is explicitly a wire-compat/determinism change (it can make deployment A substitute where B encodes natively = two wire forms for one value). Document this gate.

**Acceptance:** two nodes on the same JGDMS version produce identical `schemaDigest` for the same value regardless of extra classpath jars; adding a serializer is a reviewed, versioned change.

### WI-3 — `registerByName` rejects non-`@AtomicSerial` serializers *(BLOCKING-minor; R2 MUST-FIX 4)*
`DerReplacer.registerByName` currently checks only for the `@Serializer` annotation, not `@AtomicSerial`-ness. A mis-listed `Externalizable` serializer (`UIDSerializer`/`DateSerializer`) would register and then fail deep in encode with the confusing `wireType @AtomicSerial but runtime type …` error.

**Acceptance:** loud reject/warn **at load time** when a listed serializer is not `@AtomicSerial` (i.e. not encodable by `ObjectCodec`). Negative test with an `Externalizable` serializer name.

### WI-4 — Populate the production registry with the minimal vetted set *(BLOCKING; R1 §1/§5, R2 §1/§5)*
Create the platform-controlled production `META-INF/jgdms/der-serializers` registering **`X500PrincipalSerializer` only** — the actual Layer-10 blocker (`principals[]` `X500Principal`).

- Add further **clean, inert value-type leaves** (`URISerializer`, `StackTraceElementSerializer`, `FileSerializer`) **reactively**, only as reruns surface each in a real graph, each vetted by the WI-6 criteria (empirical least-privilege — both reviewers).
- `X500Principal` wire form is `getEncoded()` (canonical ASN.1 DER of the DN) carried **verbatim**; it **must not** be re-encoded/RFC-canonicalized/sorted (would break signatures over the DN). The byte-stricter-than-`.equals` property is intended — do not "fix" it.

**Acceptance:** reggie proxy encode advances past `X500Principal`; registry contains only vetted entries; each addition traces to a real graph need.

### WI-5 — Harden `X500PrincipalSerializer` decode *(SHOULD; R2 §1, R1 §3)*
`X500PrincipalSerializer(GetArg)` does `new X500Principal(encoded)` on attacker bytes; a malformed DN escapes as unchecked `IllegalArgumentException` from the decode constructor.

**Acceptance:** wrap so malformed `encoded` throws `InvalidObjectException` (matching `URISyntaxException` handling in `URISerializer`).

### WI-6 — Tests & merge gates *(BLOCKING; R1 §5, R2 MUST-FIX 1 / §2)*
- `serializerFor`: (a) every registered type resolves to itself; (b) a `Throwable` subclass resolves uniquely to the nearest registered supertype; (c) a synthetic value assignable to both a class key and an interface key ⇒ **fail-closed** (negative assertion, not happy-path).
- **Resolution pins the endpoint `ResolutionContext.loadClass`, never TCCL / `latestUserDefinedLoader`** (Peter's hard directive; verified correct today at the decode path — pin it with a test).
- **Canonical round-trip + rejection:** two independently constructed byte-identical `X500Principal`s ⇒ identical wire; a hand-mangled (misordered/duplicate-field) serializer encoding ⇒ **rejected** on decode (inherits the `@AtomicSerial` canonical-decode rejection; asn1-der H1).

---

## 5. DEFERRED — out of scope for this merge (each a separate, individually-reviewed follow-up)

These are **not** needed for the reggie blocker and each carries an unresolved defect. Do **not** register any of them here.

### D-1 — `ThrowableSerializer` *(BLOCKED; R2 §1 "the finding", R1 §3)*
- `init()` reflectively constructs an **attacker-named `Throwable` subclass** (`clazz.getConstructors().newInstance(message, cause)`), narrowed only by `isAssignableFrom(Throwable)` + public-ctor + endpoint-loader visibility. This reconstruction runs **outside** `checkAtomicDeSerializationPermitted` — the `DeSerializationPermission("ATOMIC")` gate does **not** cover the named subclass (and is a no-op in SM-less deployments). Runs the subclass ctor + `<clinit>`.
- The `AccessControlException` branch does `arg.get("perm", Permission.class)` — **arbitrary `Permission` reconstruction from the stream**, re-opening the exact path that got `PermissionSerializer` deleted for **CVE-2024-47197**.
- Also: recursive `cause`/`suppressed` (fenced three ways — `MAX_NESTING=16` depth, `MAX_COLLECTION=65536` `suppressed[]` breadth, `DerInputLimits` 16 MiB byte cap — so DoS is bounded, but attacker-driven); stack traces are environment-derived → **not value-stable** (unfit for Entry-matching/signing); and `serialForm()` **omits the `perm` field** that `check()` reads (silent data loss on the DER path).
- **Eligibility to admit later:** (a) exclude or prove-inert the `perm`/`AccessControlException` reconstruction branch; (b) gate the named-`clazz` construction through the ATOMIC check for its own domain, or safelist instantiable subclasses; (c) STD-008 threat-model note documenting the reflective-construction residual and no-op-under-no-SM; (d) depth/DoS negative tests. `StackTraceElementSerializer` is clean and would be admitted **with** a hardened Throwable.
- **Ordering hazard (do not trade one defect for the other):** the `perm` data-loss (serialForm omits `perm`) currently *neutralises* the arbitrary-`Permission` reconstruction on the DER path. Therefore eligibility (a) must be satisfied **before** any change that restores `perm` to `serialForm` — naively adding `perm` to cure the data loss would **re-open** the CVE-2024-47197 path.

### D-2 — `PropertiesSerializer` / `MapSerializer` *(BLOCKED; R1 §4, R2 §1)*
- **Determinism defect:** `MapSerializer.convert` captures `entrySet()` in `Hashtable`/`Properties` **hash-bucket order** into a positional `Ent[]` (arrays are preserve/positional — no sort). Two `.equals` `Properties` ⇒ different element order ⇒ **different bytes** — violates DER "one encoding per value" (asn1-der §0/H1) and rule-fidelity §1.2 Hazard 1.
- Also drags in `MapSerializer.Ent`'s **polymorphic attacker-controlled `keyClass`/`valueClass`** slot (gated, but broad surface) for **zero** current benefit (no `Properties` in constraint graphs).
- **Eligibility:** octet-sort entries by encoded key + reject non-canonical order on decode (asn1-der H1/H7); re-assess the polymorphic slot.

### D-3 — `MarshalledObjectSerializer` *(BLOCKED; R1 §3/§4, R2 §1)*
- `readResolve` is inert (re-wraps raw bytes; nested deserialization deferred to the app's `.get()`, nesting bounded) — low decode-time risk — **but** the payload is an opaque marshalled graph with **no canonical-form guarantee**: not byte-matchable/signable. Not present in constraint graphs.
- **Eligibility:** a concrete use case + an explicit "non-canonical payload; not byte-matchable/signable" caveat.

### Permanently excluded
`UIDSerializer`, `DateSerializer` — `Externalizable`, not `@AtomicSerial`; jgdms-der has no Externalizable path. `java.rmi.server.UID` is already handled by deterministic primitive decomposition (Layer 8); `Date` would take the same route if it appears.

---

## 6. Design invariants (hold throughout)

- **Endpoint-assigned ClassLoader governs all stream class resolution** — never TCCL/`latestUserDefinedLoader`; DER carries no wire codebase (verified: `ResolutionContext.loadClass` → `net.jini.loader.ClassLoading` against the endpoint `defaultLoader`).
- **One canonical encoding per value** (asn1-der §0) — every admitted serializer's wire form must be byte-deterministic and canonical; verbatim opaque octets (`X500Principal.getEncoded()`) are the sanctioned carve-out and must not be re-canonicalized.
- **Least privilege at the seam** — register only what a real graph needs; the registry is a closed part of the wire contract, not an open extension point.
- **Schema/encode agreement (R1 Guard A)** — a value whose declared type and runtime type resolve differently must agree or **fail closed at encode** with a hard error; never emit a schema `@AtomicSerial` token that encode cannot fill.

---

## 7. Reviewer traceability

| Item | R1 (determinism/fidelity) | R2 (security-adversarial) |
|---|---|---|
| WI-1 serializerFor rule | §1(a), §2 (sound; scope over class+iface; encode-only) | MUST-FIX 1; §4 (sound; no attacker-DoS) |
| WI-2 close/spec-pin registry | Guard B (interface-key = wire change) | MUST-FIX 3 (classpath→schemaDigest divergence; gadget door) |
| WI-3 reject non-@AtomicSerial | — | MUST-FIX 4 |
| WI-4 minimal set (X500Principal + reactive clean leaves) | §1/§5 (X500Principal, URI, StackTraceElement) | §5 SHOULD (minimize; defer non-needed) |
| WI-5 X500Principal malformed→InvalidObjectException | §3 (concurs; clean leaf) | §1/§6 SHOULD (originator) |
| WI-6 tests (fail-closed / endpoint-loader / canonical round-trip) | §5 | MUST-FIX 1, §2 |
| D-1 Throwable blocked | §3 (exclude; perm data loss) | §1 "the finding", MUST-FIX 2 (CVE-2024-47197) |
| D-2 Properties blocked | §4 (hash-order defect) | §1 (polymorphic slot) |
| D-3 MarshalledObject blocked | §3/§4 (non-canonical) | §1 (defer) |

---

## 8. Divergence reconciliation

R1 proposed the initial clean set as {X500Principal, URI, StackTraceElement}; R2 as {X500Principal, StackTraceElement, hardened-Throwable}. Both hold least-privilege. Reconciled: the **only** type the current blocker requires is `X500Principal`; `StackTraceElement` is reachable **only** via `Throwable` (deferred), and `URI`/`File` do not appear in `MethodConstraints` graphs. Therefore the initial set is **`X500Principal` alone**, with clean inert leaves added reactively (WI-4). Throwable, Properties, MarshalledObject are deferred (§5). This satisfies both reviewers' least-privilege principle and requires no guess about the rest of the graph.
