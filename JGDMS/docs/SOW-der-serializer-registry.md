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

## 3.1 Post-approval amendment (2026-07-18) — decode-admission true fix + framing correction

The two re-reviews of the implemented WI-1…WI-6 raised **one blocking security finding** on the *decode* path (R2) and a **framing correction** (R1+R2). Both are implemented in the same working tree as this SOW, ahead of commit. This section is normative and supersedes any conflicting wording below.

### 3.1(a) The registry is NOT a decode-admission boundary (framing correction — supersedes the WI-2 "admission boundary" gloss)

The implemented WI-2 note (and the memory ledger's "registry is the admission boundary since the ATOMIC gate is a no-op under no-SM") **overstated** the registry's role. Verified against the code:

- **Decode never consults the registry.** `DerReplacer.resolve` (decode) only honours the java.io `Resolve` interface; the nested-record path `ObjectCodec.decodeNested → decodeHierarchy` reconstructs **whatever `@AtomicSerial` leaf the transmitted schema chain names**, registered or not. `serializerFor`/`isRegistered` run **only** on encode (`replace`) and schema-generation.
- Therefore the registry is an **encode-substitution + schema-generation-determinism control**, *not* a decode gate.

**Corrected statement of decode admission (post-fix), for a nested field:**
> declared-type assignability gate (§3.1(b), `ObjectCodec.admissibleConstructClass`, pre-construction) **+** `DeSerializationPermission("ATOMIC")` (SM-dependent; no-op under DirtyChai/no-SM) **+** each class's `check(GetArg)` **+** decode depth/size bounds.

WI-2's closure of the registry remains valuable and stays — but for its **true** reasons: (i) `isRegistered` feeds `schemaDigest` (an open set diverges the wire form across deployments), and (ii) it bounds which serializer the sender will *encode*. It does **not** gate what a peer may reconstruct.

### 3.1(b) BLOCKING (R2) — thread the declared type into `decodeNested`; enforce assignability before construction *(implemented)*

**Hole (verified):** `ObjectCodec.decodeNested` reconstructed a nested `@AtomicSerial` field against `expectedSupertype = Object.class`; the declared field type known at the `DerGetArg.get(name, T)` call site was **not** threaded down, so the type mismatch was only caught by the caller's cast **after** the `(GetArg)` ctor + `check(GetArg)` ran. A hostile peer could name any `@AtomicSerial` class (e.g. `ThrowableSerializer`, which reflectively constructs an attacker-named `Throwable` subclass) in a slot the graph expects to be, e.g., `X500Principal`, and its ctor fired before the mismatch was caught. The ATOMIC gate is a no-op without an SM, so it did not save the no-SM deployment.

**Fix (implemented):** the receiver's declared type is threaded from `DerGetArg.lookup` (via `callerClass.getDeclaredField(name).getType()`, and the array component type for `@AtomicSerial[]`) into a new typed `ObjectCodec.decodeNested(bytes, expectedSupertype, …)` / `decodeNestedArray(…, expectedComponentType, …)` overload and enforced by `admissibleConstructClass(expectedSupertype, leaf)` **before** any `(GetArg)` ctor / `check`:

> **ACCEPT** iff `expectedSupertype.isAssignableFrom(leaf)` (ordinary polymorphism / `Object`/broad-interface slot) **OR** (`leaf` bears `@Serializer(replaceObType = R)` **AND** `expectedSupertype.isAssignableFrom(R)`) (legitimate serializer substitution) **OR** (`leaf` is not `@Serializer` but implements java.io `Resolve`) (serialization-proxy substitution whose resolved type is unknowable pre-construction). Otherwise **fail closed** (`DerException`) without constructing.

- Verified against the attack: declared `X500Principal`, wire `ThrowableSerializer` (`replaceObType = Throwable`) → `X500Principal.isAssignableFrom(Throwable)` false → **rejected before ctor**. A `@Serializer` leaf is decided solely by clauses 1/2 (never falls through to the `Resolve` clause), so `ThrowableSerializer`'s own `Resolve`-ness cannot re-admit it.
- Legitimate `X500PrincipalSerializer` substitution and legitimate polymorphic subtypes still decode (regression-tested; full jgdms-der suite green at 566).
- **Element-type extension (F1, 2026-07-19):** the gate is also threaded into **collection and map elements**. The receiver recovers the declared element type(s) from the field's generic signature (`getGenericType()`) — a `Collection<E>` gates elements at raw `E`; a `Map<K,V>` gates keys at `K`, values at `V` — fail-open to `Object.class`. So a concrete `Set<X500Principal>` / `Map<String,X500Principal>` closes its elements exactly as a scalar field does (the reggie `MethodConstraints` shape). Verified: a hostile `ThrowableSerializer`/foreign serializer element in `Set<X500Principal>` and in a `Map` key and value is rejected before its ctor runs.
- **Scope honesty (R2 blocked the prior over-claim; precise residual after F1):** this closes **concrete/narrowly-typed** scalar fields, array components, and **flat** generic collection/map elements (`C<Concrete>` / `Map<K,V>`). It does **NOT** close (still gated only by the ATOMIC gate (SM-dependent) + `check(GetArg)` + bounds): (i) genuinely `Object`-typed / broad-interface-typed slots; (ii) **raw** or **wildcard** (`?`, `? super X`) / type-variable collection & map elements; (iii) **nested-generic INNER** elements (`Set<List<Concrete>>` gates the outer element at the erasure `List`, inner `Concrete` stays residual); (iv) clause-3 `Resolve` proxies in a narrow slot (ctor runs; bounded by the post-`readResolve` typed cast — a wrong-typed resolved value cannot populate the slot). **Do not claim collections are fully closed.** All four are **documented residuals**, not universal closure.

### 3.1(c) OPEN design question for the re-reviewers (NOT built)

Should decode additionally **require the substituted serializer to be in the closed production registry** (not merely bear `@Serializer`)? That would re-establish a decode-time registry role and further narrow clause 2, but would make decode **registry-dependent** (today decode is registry-independent and name-driven). Recommendation: **leave out of this change**; the assignability gate already rejects the concrete-slot attack, and coupling decode to the registry is a larger design shift (and a new availability dependency) deserving its own review. Flagged for the board.

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
> **Framing correction (see §3.1(a)):** close the registry for its true reasons — `isRegistered`→`schemaDigest` determinism and bounding *encode* substitution. The registry is **not** a decode-admission boundary; do not justify WI-2 as "the admission control in no-SM deployments" (that is §3.1(b)'s declared-type gate + ATOMIC + `check`).

The registered set is **part of the wire contract**: `isRegistered` feeds `SchemaGenerator`. For a **polymorphic (interface/abstract) slot** the top-level token is `@AtomicSerial` regardless of registration, but a classpath-dependent set makes the **wire form of the substituted value** a function of deployment classpath (deployment A names serializer `S` in the embedded schema-chain/payload; B names the native class) → breaks Entry byte-matching (asn1-der §7.7.2) and signatures **over the value**. For a **concrete-typed field**, registration flips schema-gen between `@AtomicSerial` and a hard throw (encode-vs-fail). Either way an open set lets an unaudited third-party jar contribute a reconstruction/gadget door.

- Load the production set from a **platform-controlled resource only** (not an open `getResources()` merge of every classpath `der-serializers`), **and** formally declare the governance rule in the STD — now written as **STD-006 §7.6.1** (registered set is wire-affecting → `schemaDigest`; additions are a **versioned schema change requiring board review + digest coordination**; interface-keyed additions are a breaking wire change; flat-classpath shadowing residual documented). Code/resource citations now point to §7.6.1; the decode-admission rule is **STD-008 §16.2**.
- **Guard (R1 Guard B):** adding an **interface-keyed** serializer is explicitly a wire-compat/determinism change (it can make deployment A substitute where B encodes natively = two wire forms for one value). Documented in STD-006 §7.6.1(3).

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

### D-1 — `ThrowableSerializer` *(ACTIVE-BLOCKED; R2 §1 "the finding", R1 §3)*

> **Re-classification (2026-07-18): "deferred by non-registration" → ACTIVE-BLOCKED.** The prior status implied the registry not listing `ThrowableSerializer` kept it out of reach. **It does not.** Per §3.1(a), decode is **registry-independent**: a hostile peer can name `ThrowableSerializer` (or any `@AtomicSerial` serializer) directly in the transmitted schema chain and the codec will reconstruct it **regardless of the registry**. What now bounds it is §3.1(b)'s declared-type gate — which **closes it for a concrete/narrowly-typed slot** (a scalar/array/`C<Concrete>`/`Map<K,V>` element, e.g. `X500Principal`: `X500Principal.isAssignableFrom(Throwable)` is false → rejected before ctor) but **leaves it decode-reachable for the §3.1(b) residual slots** — genuinely `Object`-typed / broad-interface-typed slots, and raw/wildcard/nested-generic-inner collection & map elements. So `ThrowableSerializer` remains an **active, un-eliminated decode hazard for polymorphic slots**, not a dormant one gated by non-registration. It stays BLOCKED from admission and additionally must be treated as reachable when reasoning about broad slots until eligibility (below) is met. **Data-loss dormancy of the `perm` branch:** the `AccessControlException`→`arg.get("perm", …)` CVE-2024-47197 path is presently inert **only** because `ThrowableSerializer.serialForm()` omits the `perm` field (the DER path silently drops it) — an *accident of the SOW's own ordering hazard*, **not** a gate. Do not treat that as protection.

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
