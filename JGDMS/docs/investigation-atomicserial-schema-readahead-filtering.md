# Investigation — schema-driven read-ahead filtering for `@AtomicSerial` DER

**Status:** scoping / design memo. **No code has been written.** This is a "should we,
and how would it work" investigation of one idea, grounded in what is already built on
trunk. Where a claim rests on a spec section, the section is cited and — because STD-006
has a live version discrepancy — the version is named. Where the codebase could not
settle a point, it is flagged `[OPEN]`.

**The question (from Peter).** The canonical DER wire form for an `@AtomicSerial` object
carries its schema *in band* — class name, the ordered `(wireName, wireType)` field
chain, and a SHA-256 schema digest — and that schema is inspectable **without**
reconstructing the object or loading its class. Should `@AtomicSerial` therefore support
a **read-ahead filter**: a policy applied over the schema (and optionally projected field
values) **before** committing to reconstruction — deciding accept-and-reconstruct /
reject / skip per object, pre-constructor, class-free? Framed against Java's JEP 290
`ObjectInputFilter`, this would be the class-free, schema-precise, in-band-verified
successor: filter on the *actual declared shape*, not a resolvable class name over an
opaque stream.

**One-line answer.** The primitives already exist and one narrow instance of the idea is
already shipping (`ObjectCodec.admissibleConstructClass`, a fail-closed pre-construction
gate). What is *missing* is a **first-class, deployment-configurable filter SPI** on the
DER decode boundary, keyed on the verified schema (class-name / schema-digest allowlist).
Its value is twofold: it is the **polyglot-portable** admission mechanism — the SM-based
`DeSerializationPermission("ATOMIC")` gate is Java/DirtyChai-specific, so no-SM runtimes
(stock JDK 24+/JEP 486, and Rust/Haskell peers) have **no** such gate at all — and on
DirtyChai (where that permission gate **is** enforced) it is **defense-in-depth that
composes with** it, adding what the permission cannot: schema-**digest**-bound admission
that fires **pre-class-load** on the declared wire shape. Recommendation: **first-class it,
thin** — Tier A (digest/class-name allowlist) as a small new SPI; Tiers B–D reuse existing
machinery. It **composes with** CEL rather than duplicating it.

---

## 0. What was read to ground this memo

Read directly (file:line load-bearing):

- `jgdms-der/.../der/object/ObjectCodec.java` — the decode path every `@AtomicSerial`
  object goes through. Three facts anchor this memo:
  - **`decodeToFieldMap(SchemaChain.Result, byte[])`** (`ObjectCodec.java:959`) — decodes
    `className → (fieldName → value)` using **only** the embedded schema. Its own javadoc:
    "No class is loaded; no constructor is invoked; no `check(GetArg)` is run. The decode
    is purely structural." This is the existing **read-without-reconstruct** primitive.
  - **`admissibleConstructClass(expectedSupertype, constructClass)`**
    (`ObjectCodec.java:582`) — a **pre-construction admission gate**, run inside
    `decodeHierarchy` (`ObjectCodec.java:703`) *before any `(GetArg)` constructor or
    `check(GetArg)`*, that fails closed when the wire-named leaf is not admissible into the
    declared slot type. Its javadoc states the pivotal fact: "The
    `DeSerializationPermission("ATOMIC")` gate is a no-op under SM-less / DirtyChai
    deployments, so this type check — not that permission — is what closes concrete-typed
    fields." **This is a schema read-ahead filter already**, but a *fixed policy*
    (assignability + `@Serializer` `replaceObType` + `Resolve` proxy), keyed on the slot's
    declared Java type, not a deployment-configurable policy over the schema.
  - **`checkAtomicDeSerializationPermitted(...)`** (`ObjectCodec.java:210`, `:230`) — the
    `DeSerializationPermission("ATOMIC")` per-class gate; `if (sm == null) return;` at
    `:232` — **a no-op with no SecurityManager**. Same for the `PROXY` gate (`:252`).
- `jgdms-der/.../der/schema/AtomicSerialSchemaRecord.java`,
  `AtomicSerialFieldDef.java`, `SchemaChain.java` — the in-band schema model. The record is
  `SEQUENCE { className, parentSchemaHash OPTIONAL, fields SEQUENCE OF {wireName, wireType} }`
  (`AtomicSerialSchemaRecord.java:40`), schema version = `SHA-256(DER(record))`
  (`:45`, `:347`), and `SchemaChain.decodeChain` (`SchemaChain.java:187`) enforces the
  Merkle adjacent-pair cross-check and completeness (the "T6" chain-integrity work).
- `jgdms-der/.../der/marshal/MarshalledInstanceRecord.java` — the top-level record:
  `payloadBytes` beside `schemaBytes` beside a `schemaDigest`. `decodeSchemaChainAsResult()`
  (`:347`) decodes the chain and **verifies** `schemaDigest == SHA-256(leaf record)` before
  returning (`:350`) — "there is no local-schema or registry fallback." This is the
  natural filter-hook location: the verified schema is in hand *before*
  `ObjectCodec.decodeHierarchy` runs a constructor.
- `jgdms-cel/.../cel/verifier/DerSchemaChainView.java` — the CEL adapter over the **same**
  `AtomicSerialSchemaRecord` chain, mapping `wireType` tokens to `CelType`, evaluated via
  `decodeToFieldMap` (`DerSchemaChainView.java:30-33`). Confirms CEL and any value-tier
  read-ahead filter would share one projection primitive.

Read via the survey (board-guidance, STD-006 v0.13, STD-009, SOWs) — cited inline below.

**STD-006 version note.** The highest DER spec on disk is
`JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`, **not** v0.12. Several facts this memo
relies on — the consolidated **§4.5** ceilings table, the **§7.6.1** decode-admission gate
stack, the **§7.8 "T6"** chain-integrity + mandatory `schemaDigest`-before-use rule, and
the **§3.12** closed-subset wire-type model — **exist only in v0.13**. All §-citations
below are v0.13 unless stated.

---

## 1. Survey — what already exists (G3: cite, don't assume)

### 1.1 Class-free projection — the read-without-reconstruct substrate is built

`ObjectCodec.decodeToFieldMap` (`ObjectCodec.java:959`) is the executable form of STD-006
**§3.11 Data Independence from Code** (NORMATIVE): a conformant implementation "MUST be
able to decode a DER-encoded `@AtomicSerial` object into a named field map given only the
DER bytes and the corresponding `AtomicSerialSchemaRecord` chain, without loading any class
from the originating codebase." STD-006 **§3.12** (v0.13) names the closed wire-type subset
that makes this sound: `WireType ::= Scalar | AtomicSerialObject | Collection(WireType…) |
Any` — no raw `Object` graphs, no arbitrary `Serializable`, no cycles. So the substrate for
**every tier of read-ahead filtering already exists** and is a conformance requirement, not
an add-on.

### 1.2 The schema is in-band and *cannot lie* — digest + Merkle chain (§7.8, T6)

The schema record carries `className` + the ordered `(wireName, wireType)` list, digested
`SHA-256(DER(record))` (`AtomicSerialSchemaRecord.java:45,347`). STD-006 §7.8: schema
identity is the triple `(className, parentSchemaHash, fields)` — the **class name is inside
the hash**, so two classes with identical fields but different names produce different
versions. `parentSchemaHash` forms a Merkle chain; the **leaf digest captures the entire
hierarchy**. `MarshalledInstanceRecord.decodeSchemaChainAsResult()` (`:350`) enforces the
NORMATIVE §7.8 rule that `schemaDigest` "is a ROUTING HINT, not an integrity claim" and
**must be verified** against `schemaBytes` before use; `SchemaChain.decodeChain`
(`SchemaChain.java:213-249`) enforces the T6 adjacent-pair cross-check and completeness
(a truncated `{leaf}` must not share a digest with `{leaf,parent,root}`). This is the
property the idea leans on: **a filter keyed on the schema digest binds to the exact
declared shape, and the wire cannot present a schema whose digest it does not actually
hash to.** (Board G12: "verified" here *does* mean bound-to-known-good — the digest is over
the schema bytes themselves, and a deployment allowlist is the external trustworthy
reference.)

### 1.3 The existing pre-construction gate — `admissibleConstructClass`

Already the strongest evidence the idea is *sound and partly built*.
`decodeHierarchy` resolves the wire-named leaf class from the schema (via the endpoint
`ResolutionContext`, never the thread-context loader) and, **before any constructor runs**
(`ObjectCodec.java:703`), rejects it unless it is admissible into the declared slot:
ordinary polymorphism, a `@Serializer` standing in for a statically-declared
`replaceObType`, or a `java.io Resolve` proxy. This is a **Layer-1 structural, class-name /
type gate that fails closed pre-Layer-2**. Its acknowledged residual: genuinely
`Object`-typed or broad-interface slots are *not* closed by it (`:539-541`), and it is a
**fixed** policy — there is no deployment knob to say "admit only these schemas."

### 1.4 The `DeSerializationPermission` gate — SM-dependent; ENFORCED on DirtyChai, inert only on no-SM runtimes

`checkAtomicDeSerializationPermitted` (`ObjectCodec.java:230`) checks
`DeSerializationPermission("ATOMIC")` against the decoded classes' protection domains
before construction — the DER counterpart of `AtomicMarshalInputStream`'s per-class check.
STD-006 §7.6.1 lists the full admission stack: "`admissibleConstructClass` **+**
`DeSerializationPermission("ATOMIC")` (**SM-dependent**) **+** each class's `check(GetArg)`
**+** decode depth/size bounds (§4.5)."

**Where this gate is live vs inert (authoritative — corrected on trunk).** The gate's
short-circuit is `if (sm == null) return;` (`:232`), so its enforcement follows exactly
whether a `SecurityManager` is installed:

- **DirtyChai — ENFORCED.** DirtyChai installs its `CombinerSecurityManager` via the
  standard `-Djava.security.manager=default`, so `System.getSecurityManager()` returns
  **non-null**, the null-guard does **not** short-circuit, and
  `DeSerializationPermission("ATOMIC")` **is enforced**. (A second mode,
  `-Djava.security.manager=polpAudit`, captures missing permissions into policy files.)
- **Inert only on no-SM runtimes** — stock **JDK 24+ (JEP 486, which removed the SM)** and
  **non-JVM peers** (Rust/Haskell), where no SecurityManager exists to consult.

An earlier draft of this memo — inheriting a since-corrected STD-006 line — wrongly said
this gate was "inert under DirtyChai." It is not. The load-bearing point for the filter is
therefore **not** "replace an inert gate," but: (a) the permission gate is a **Java/SM-only**
mechanism, so **no-SM and non-JVM runtimes have no equivalent at all**; and (b) even where it
is enforced (DirtyChai) it keys on **codebase / `ProtectionDomain`** and runs
**post-class-resolution**, which a schema-digest read-ahead filter complements (§5, §6).

### 1.5 Structural ceilings already bound the decode (§4.5)

DoS is already handled structurally, *before* any value is built, independent of any
filter: `MAX_NESTING = 16` (`ObjectCodec.java:150`), `MAX_COLLECTION = 65536` (`:162`),
`MAX_FIELDS = 65535`, `MAX_CHAIN_RECORDS = 64`, `MAX_CHAIN_BYTES = 65536`
(`SchemaChain.java:71,85`) — the consolidated STD-006 **§4.5** table, all inclusive, all
"metered during the decode loop." **A size/depth filter tier would largely duplicate §4.5**
— noted so we do not re-invent it.

### 1.6 CEL — the field-value predicate machinery, but scoped to *matching*, not *admission*

This is the crux of the compose-vs-duplicate question, so it is pinned precisely.

STD-011 (CEL Filter Expression Format) and STD-009 **§8 "The Filter Contract (server-side
predicate pushdown)"** define a schema-projected field-predicate evaluator. STD-009 §8's
dated verification note (2026-07-20) states plainly what it is and is not:

- It reuses **the same substrate** — `MarshalledInstanceRecord` schema bytes +
  `ObjectCodec.decodeToFieldMap`, "no `Class.forName`, no constructor, no `check(GetArg)`."
- Its verdict is **"pre-selection only"**: "The filter path reads scalar values out of
  validated TLVs and never reconstructs objects — no `DeSerializationPermission` gate
  crossed, no gadget surface… the taking client still performs the full gated
  deserialization on anything it accepts, so filter false-positives cost bandwidth, never
  integrity."
- Its **scope is matching stored entries against a query/template**, not admitting incoming
  objects at a deserialization boundary. `SOW-Entry-ATOMIC-DER-Migration.md:250` — "Filter
  verdicts are pre-selection only"; the wiring is Outrigger/Reggie **entry matching** (Part
  B / "Reggie filter integration — attribute-predicate pushdown wiring").

So CEL answers the question *"does this already-stored entry's field X satisfy predicate
P?"* for query pushdown. It does **not** answer *"should I reconstruct this incoming object
graph at all?"* at an RPC or deserialization boundary. **Same projection primitive; a
different decision, at a different boundary.** (And STD-009 §8's caveat: class-free
filtering "only works over `ATOMIC_DER`-marshalled entries — today neither first consumer
produces them by default"; Reggie's `useDerForEntries` defaults `FALSE`,
`SOW-Entry-ATOMIC-DER-Migration.md:90`.)

### 1.7 BAE — code trust, not data/shape trust (distinct axis)

The Bytecode Analysis Engine (`services/bytecode-analysis-engine/`, STD-002) is a
**JAR-digest-scoped, cache-after-first-verdict** admission check keyed by JAR SHA-256,
reporting to a content-addressed `VerdictRegistry` — "not something invoked per RPC call"
(`investigation-jeri-der-vs-jsonrpc-benchmark-scope.md:49-55`). BAE governs **which code
may load**; a schema read-ahead filter governs **which data shapes may be reconstructed**.
Orthogonal axes: BAE can say "this codebase is safe to load" while a shape filter still
says "but I did not expect a graph of this shape here." Both compose.

### 1.8 Board framing — this is a Layer-1 operation, and the seam it guards is named

Board **G5** (`JGDMS-Board-Reviewer-Guidance.md` §G5): "**Layer 1 (wire/schema)** —
structural facts… baked into the schema token and digest-covered; **Layer 2
(`check(GetArg)`/constructor)** — semantic facts." A schema read-ahead filter is a
**pure Layer-1 operation** — it reads declarations, never runs a constructor — which places
it principled: it must not try to enforce semantic invariants (that is Layer 2's job), only
structural admission. Board **§2.2 attack shape 4 "Ungated reconstruction doors"** is the
exact seam it strengthens: "A polymorphic/self-describing form that lets the *wire* name the
class to reconstruct is exactly where capability escalation hides." A digest/class-name
allowlist is a direct, fail-closed answer to that hazard. Board **G1** (canonical form is
the contract) and STD-009 §8 both mandate that any projection reader **be the same
fail-closed canonical codec** — never a lenient side-scanner.

---

## 2. Gap analysis

**Does a general (non-Outrigger-matching) `@AtomicSerial` deserialization-boundary
read-ahead filter exist today? — No, with one partial exception.**

| Capability | Exists? | Where |
|---|---|---|
| Class-free schema+value projection | **Yes** | `decodeToFieldMap` (`:959`); STD-006 §3.11 |
| Fixed pre-construction type-admission gate | **Yes** | `admissibleConstructClass` (`:582`) |
| Deployment-configurable, digest-keyed, polyglot-portable schema allowlist | **No** | — (the gap) |
| Field-value predicate evaluation | **Yes**, but for *matching* | CEL / STD-009 §8 (pre-selection, not admission) |
| Per-class reconstruct gate (SM/`ProtectionDomain`-keyed) | **Yes** — enforced on DirtyChai; inert on no-SM runtimes | `DeSerializationPermission("ATOMIC")` (`:230`) |
| Structural size/depth ceilings | **Yes** | STD-006 §4.5 |

The gap is precisely a **deployment-configurable, fail-closed, digest-keyed admission
policy over the verified schema** (class name / digest, and optionally declared shape) —
one that runs **on every runtime, SM or not, JVM or not**, unlike the SM-only
`DeSerializationPermission` gate. Every *mechanism* it needs is built; what is missing is
the **policy seam and the SPI** that lets a deployment install one. This is a small
increment over a large amount of existing machinery — not a green-field feature.

---

## 3. Positioning vs JEP 290 `ObjectInputFilter`

`ObjectInputFilter` is **not used anywhere in load-bearing code** — it appears only in three
doc references (`marshalling-security-properties-catalog.md`,
`SOW-DirtyChai-Serialization-SPI-DER.md`, `SOW-RemoteEvent-Source-DER-Encoding.md`), never
in `src`. So this would be a genuinely new capability, not a re-skin.

**What schema-in-band + digest + class-free projection buys that JEP 290 cannot:**

1. **Filter the *real declared shape*, not a name.** JEP 290 filters on a resolved
   `Class<?>` / class name / array length / depth over an *opaque* JOSS stream — it must
   often *resolve the class* to decide, and the stream does not tell it the object's
   declared field shape. The DER filter reads the **actual `(className, ordered field
   name/type chain)`** off the wire, **without loading the class**.
2. **The schema cannot lie.** JEP 290 trusts the stream's class name; a mismatch between
   the named class and the bytes is not something the filter can detect. Here the schema is
   **digest-bound** (`SHA-256(DER(record))`, Merkle-chained, verified at `:350`) — an
   allowlist keyed on the digest binds to bytes that *provably* hash to it (Board G12).
3. **Pre-construction *and* pre-class-load.** JEP 290's filter runs during
   `readObject`, interleaved with resolution; a rejected class may already have been
   resolved. `admissibleConstructClass`/`decodeToFieldMap` run with **no `Class.forName`,
   no constructor, no `check(GetArg)`** — the reject decision precedes the class touching
   the JVM at all.
4. **Runs everywhere, SM or not.** JEP 290 needs no SM (a point in its favour). The JGDMS
   equivalent of "restrict which classes deserialize" — the ATOMIC permission — *does* need
   one: it is enforced under DirtyChai (which installs an SM) but has no effect on a no-SM
   runtime (stock JDK 24+, non-JVM peers). A schema filter provides that control uniformly
   **regardless of whether an SM is present** — the same admission decision on DirtyChai,
   stock JDK, and a Rust/Haskell peer.
5. **Language-neutral.** The projection is DER + schema, "identical for a non-JVM host"
   (STD-009 §8) — a Rust JERI peer can run the same allowlist. Both JEP 290 and the SM-based
   ATOMIC permission are JVM-only; the schema filter is the one admission mechanism that
   ports to the polyglot peers.

**What JEP 290 does that this would not (honest asymmetries):**

- JEP 290 is a **built, standard, documented JDK API** with a process-wide default filter,
  pattern syntax, and ecosystem familiarity. This is bespoke.
- JEP 290 covers **arbitrary `Serializable`** graphs. The DER filter only ever sees the
  closed `@AtomicSerial` DER subset (STD-006 §3.12) — which is the *point* (that subset is
  what excludes gadget chains) but it is not a general JOSS defence.
- JEP 290 can bound **total stream references / array lengths / depth** dynamically. Here
  those are already fixed structural ceilings (§4.5), not a per-deployment tunable — less
  flexible, but fail-closed by construction.

Net: this is best described as **"the class-free, schema-precise, in-band-verified
successor for the `@AtomicSerial` DER subset,"** not a drop-in JEP 290 replacement across
all serialization.

---

## 4. Design — where the hook lives, and the filter granularity tiers

### 4.1 Hook location

The natural seam is **`MarshalledInstanceRecord` → `ObjectCodec.decodeHierarchy`**, at the
point the verified schema is in hand and before construction:

```
decode(MarshalledInstanceRecord)
  → decodeSchemaChainAsResult()      // schema verified vs schemaDigest (MarshalledInstanceRecord.java:350)
  → [FILTER HOOK]  ← install here: verified SchemaChain.Result + expectedSupertype in hand
  → ObjectCodec.decodeHierarchy(...) // admissibleConstructClass:703, then constructor
```

A `DerDeserializationFilter` SPI, per-object, returning `ACCEPT / REJECT / (SKIP)`, invoked
once per top-level object and once per nested `@AtomicSerial` field (the same recursion
`admissibleConstructClass` already guards — Board G10: guard the interior frame, not just
the boundary). It receives the **verified** `SchemaChain.Result` (class name + digest +
field chain) and the declared `expectedSupertype`; for value-tier decisions it may lazily
call `decodeToFieldMap`. Default filter = accept-all (status quo), so it is opt-in and
composes ahead of, not instead of, `admissibleConstructClass` + the ATOMIC gate +
`check(GetArg)`.

### 4.2 Granularity tiers

| Tier | Keys on | Cost | Verdict |
|---|---|---|---|
| **A. class-name / schema-digest allowlist** | `className`, `schemaDigest` (verified) | ~free (already decoded) | **First-class this.** Cheapest strong win: admit only known schemas; reject unknown shapes pre-construction, on every runtime (SM or not, JVM or not). Direct answer to Board §2.2#4. |
| **B. declared field SHAPE** | ordered `(wireName, wireType)` chain, nesting | cheap (schema only, no values) | **Worth exposing**, but largely subsumed by A: an allowlisted digest *is* a fixed shape (the digest covers the field chain). Useful for wildcard/structural rules ("no field typed `Any`") without pinning exact digests. |
| **C. sizes / depth** | element counts, nesting depth | cheap | **Do not first-class.** Already enforced by STD-006 §4.5 ceilings (`MAX_NESTING`, `MAX_COLLECTION`, chain bounds) before construction. Overlap; a per-deployment *tightening* knob is the only marginal value. |
| **D. field VALUES** | projected values via `decodeToFieldMap` | moderate (must project values) | **Reuse CEL, do not rebuild.** This is exactly CEL/STD-011 territory. Compose: let a filter clause delegate to a CEL predicate over the same projection. |

**First-class: Tier A (and a thin slice of B).** Tiers C and D reuse existing machinery
(§4.5 ceilings; CEL) rather than being reimplemented.

---

## 5. Security analysis

- **Fail-closed.** Unknown/unfilterable schema → REJECT (Board G6: fail-closed on
  ambiguity; STD-009 §8: "a candidate whose bytes fail canonical decode, or whose schema
  lacks a referenced field, MUST be no-match, fail-closed"). The default *installed* filter
  is accept-all for back-compat, but any *configured* allowlist must reject the unlisted —
  never a permissive fallback (Board G1; STD-006 principle 6).
- **Strengthens the no-gadget story.** It rejects unwanted graphs **before any constructor
  runs** — closing exactly the "genuinely `Object`-typed / broad-interface slot" residual
  that `admissibleConstructClass` acknowledges it cannot close (`:539-541`), and the
  "ungated reconstruction door" of Board §2.2#4. It is strictly additive: it can only
  *narrow* what reconstructs.
- **Composition — complementary to the SM gate, never its replacement.** Runs *ahead of*
  the ATOMIC permission gate and `check(GetArg)`. On DirtyChai the ATOMIC gate **is**
  enforced (§1.4), so the filter is **defense-in-depth** that adds what that gate
  structurally cannot: the ATOMIC gate keys on **codebase / `ProtectionDomain`** and runs
  **post-class-resolution**, whereas the filter is **schema-digest-bound** and fires
  **pre-class-load** on the declared wire shape — two different axes, both fail-closed. On
  no-SM runtimes (stock JDK 24+, non-JVM peers) the ATOMIC gate is absent entirely and the
  filter is the *only* admission control at this boundary — which is why it is the
  **polyglot-portable** layer (§6.1). Orthogonal to BAE (code trust) and to CEL (query
  matching). It must be the **same fail-closed canonical codec** as decode — reuse
  `decodeToFieldMap` / `SchemaChain.decodeChain`, never a second lenient scanner
  (Board G1; STD-009 §8).
- **New attack surface — bounded.** The filter parses attacker bytes to get the schema. But
  it parses **only** the schema chain and (for Tier D) projected values through the *same*
  §4.5-bounded reader — `MAX_CHAIN_RECORDS`/`MAX_CHAIN_BYTES`/`MAX_FIELDS`/`MAX_COLLECTION`/
  `MAX_NESTING` all bind before the filter sees anything, and `schemaDigest` is verified
  first (`:350`). No new unbounded parse is introduced; the filter runs *inside* the
  existing ceilings, not before them. (Board G13 caveat: a HIGH claim of "bounded" should
  be backed by running adversarial input through a built filter — not yet possible, this is
  scoping.)
- **Digest-pinning caveat (Board G12).** A digest allowlist binds to an exact schema
  version; legitimate class evolution changes the digest and must be re-listed. This is a
  feature (unexpected shapes rejected) but an operational cost — Tier B wildcard rules
  mitigate it where exact-version pinning is too strict.

---

## 6. Use cases beyond Outrigger matching

1. **★ Polyglot-portable deserialization-admission control (strongest).** The single most
   compelling case, and it serves the polyglot goal directly. The `DeSerializationPermission`
   gate is a **Java/SM-only** mechanism: it is enforced on DirtyChai (which installs a
   `CombinerSecurityManager`), but the **Rust and Haskell peers, and any no-SM JVM runtime
   (stock JDK 24+/JEP 486), have no equivalent admission gate at all** — they consume the
   same in-band DER + schema but cannot consult a Java `Permission`/`ProtectionDomain`. An
   in-band, schema-**digest**-keyed read-ahead filter is the **portable, language-neutral
   admission mechanism**: the identical allowlist decision runs on every peer from the same
   wire bytes (STD-009 §8: the projection is "identical for a non-JVM host"). Where the SM
   gate is absent this is the *only* pre-construction admission control; where it is present
   (DirtyChai) it is **defense-in-depth** (see #1a below). This is the case that turns
   "nice-to-have" into "the missing cross-language deserialization boundary."
   - **1a. On DirtyChai — defense-in-depth complementing the enforced SM gate.** The ATOMIC
     permission gate *is* live under DirtyChai and keys on **codebase / `ProtectionDomain`,
     post-class-resolution**. The read-ahead filter adds an **orthogonal** axis the SM gate
     structurally cannot: **schema-digest-bound admission that fires pre-class-load** on the
     declared wire shape. The two compose (a peer must satisfy *both*); the filter is a
     complement, never a replacement.
2. **RPC / remote-method argument hardening.** A JERI endpoint admitting only the expected
   `@AtomicSerial` argument shapes for a given method before reconstruction — a per-endpoint
   allowlist of argument schema digests. Narrows the "wire names the class" escalation
   surface (Board §2.2#4) at the transport boundary, class-free, and works identically on a
   Java or a Rust endpoint.
3. **General untrusted-`@AtomicSerial` admission** at any deserialization boundary (space
   `take`, event delivery, discovery) where the receiver knows the small set of shapes it
   expects and wants everything else rejected pre-construction.

---

## 7. Cost / worth — and the compose-vs-duplicate verdict

**Increment is small.** Tier A is: one SPI interface, one invocation site in
`decodeHierarchy` (beside the existing `admissibleConstructClass` call at `:703`) plus its
nested counterpart, and a default accept-all implementation. It **reuses** the verified
`SchemaChain.Result`, `schemaDigest`, `decodeToFieldMap`, and the §4.5 ceilings — no new
parsing, no new wire format, no new digest scheme. Most of the risk (canonical decode,
digest verification, recursion bounds) is already paid for on trunk.

**It does not duplicate CEL — it composes.** CEL is the **value-predicate for query
matching** (STD-009 §8, "pre-selection only," over stored entries); the read-ahead filter
is the **shape/identity admission decision at a deserialization boundary**. They share one
projection primitive (`decodeToFieldMap`) and one schema view (`DerSchemaChainView`). Tier D
of the filter is literally "invoke a CEL predicate," so value-level filtering is a reuse,
not a rebuild. The two occupy different boundaries (matching vs admission) and different
decisions (satisfies-predicate vs may-reconstruct) — building the filter does **not**
re-do CEL's work, and CEL does **not** already cover admission.

---

## 8. Recommendation

**Proceed — first-class the thin version.**

**`[PROPOSED]` scope (increment):**

- **Tier A — `DerDeserializationFilter` SPI** on the DER decode path, invoked per top-level
  and per nested `@AtomicSerial` object in `ObjectCodec.decodeHierarchy` after schema
  verification and before construction; keyed on the **verified** `className` /
  `schemaDigest`; `ACCEPT`/`REJECT`; default installed filter = accept-all (opt-in, back-
  compat). Deployment supplies an allowlist (digests and/or class-name patterns).
  **Fail-closed:** a *configured* filter rejects the unlisted; no permissive fallback.
- **Tier B (thin)** — allow structural/wildcard rules over the declared `(wireName,
  wireType)` chain for deployments that cannot pin exact digests.
- **Tiers C, D — reuse, do not build.** C is STD-006 §4.5 (optionally expose a
  per-deployment *tightening* of the existing ceilings); D delegates to a CEL predicate over
  `decodeToFieldMap`.
- **Spec home:** a new STD-006 §7.6.x clause ("schema read-ahead admission filter") sitting
  beside the §7.6.1 gate stack, framing it as the **portable, polyglot companion** to
  `DeSerializationPermission("ATOMIC")` (which it complements on DirtyChai and substitutes
  for on no-SM/non-JVM peers) and as the deployment-configurable extension of
  `admissibleConstructClass`.
- **Board gates to honour:** G1 (same fail-closed canonical codec — reuse, no lenient
  scanner), G5 (Layer-1 only — structural admission, never semantic invariants), G6
  (fail-closed), G10 (guard the nested frame too), G12 (digest binds to known-good), G13
  (validate the bound with real adversarial input before any HIGH safety claim).

**Rationale in one line:** the mechanism is already built and one fixed instance already
ships; what is missing is the *policy seam*, and that seam gives "which shapes may
reconstruct" a **portable, digest-bound, pre-class-load** control that runs on **every**
peer — complementing DirtyChai's enforced `ATOMIC` permission and supplying the admission
control the no-SM/non-JVM peers otherwise lack entirely.

---

## 9. Open items / could not be determined

- `[OPEN — verify at build]` Exact nested-object invocation count and whether a per-nested
  filter call needs a distinct signature from the top-level call — inferred from the
  `admissibleConstructClass` recursion (`ObjectCodec.java:703` + `decodeNested`), not traced
  through every nested path in this memo (the file is ~2780 lines; nested decode past line
  991 was not exhaustively read). Confirm before finalising the SPI shape.
- `[OPEN]` Whether Tier D should be a first-class filter clause type or left entirely to a
  caller-supplied CEL predicate is a design choice deferred to implementation; STD-011's
  clause grammar would decide it.
- `[NOTE]` Two survey sub-agents (CEL scope; gate/BAE inventory) produced no retrievable
  transcript; their subject matter was instead grounded by direct reads
  (`DerSchemaChainView.java`, `ObjectCodec.java` gates) and by the STD-009 §8 / STD-006
  §7.6.1 citations surfaced by the other two agents. No claim here rests solely on an
  unrecovered agent report.
- `[NOTE]` This memo cites **STD-006 v0.13-DRAFT**, the highest version on disk. If a
  reader is working from v0.12, §4.5 / §7.6.1 / §7.8-T6 / §3.12 do not exist there and the
  `MarshalledInstanceRecord` shape differs (extra `codebaseAnnotation`, `/DER` format tag,
  `schemaDigest` framed as trusted fast-compare rather than a to-be-verified routing hint).
- `[CORRECTED]` The STD-006 §7.6.1 phrase "inert under DirtyChai" (≈line 2228) was a stale
  error conflating DirtyChai with stock JDK 24+/JEP 486 (which really did remove the SM).
  It has been **corrected on trunk (commit `acf57de58`)**: `DeSerializationPermission("ATOMIC")`
  is **enforced** on DirtyChai (which installs a `CombinerSecurityManager` via
  `-Djava.security.manager=default`; a second mode, `-Djava.security.manager=polpAudit`,
  captures missing permissions into policy files) and is inert **only** on no-SM runtimes —
  stock JDK 24+ and non-JVM peers. An earlier revision of *this* memo inherited the stale
  wording and framed the filter as the SM gate's "replacement"; that framing has been
  removed throughout — the filter is a **complement** on DirtyChai and the **portable
  substitute** only where no SM gate exists.
