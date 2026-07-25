# Design Memo — Outrigger CEL Filter Pushdown, Unit B1 (API + Envelope Wire + Admission Seam)

- **Status:** IMPLEMENTED on `feat/cel-filter-b1` (off trunk `87932373c`), awaiting parallel board review.
- **Scope:** SOW `SOW-Entry-ATOMIC-DER-Migration.md` §5 Part B, unit **B1**, re-scoped onto the merged
  **EntryRep-v2** foundation. This unit fixes the **permanent** filter API, the envelope wire format, and
  the server admission seam. It contains **no** predicate evaluation (B3), **no** field projection (B2),
  **no** write-path cost caps beyond the verifier's own (B4).
- **Companions:** `SOW-CEL-Filter-Format.md` (the CEL primitive B1 consumes); `JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md`; `JGDMS-STD-006-Appendix-EntryRep-v2-DRAFT.md`.

This memo records the decisions that outlive the implementation. Where the SOW left a first-class question
open (§5 B1, §9), the decision taken is stated here with its rationale.

---

## 1. The filter is an explicit OPERATION PARAMETER, never an EntryRep field

**Decision.** A filter travels as `byte[] filterEnvelope`, a distinct parameter on new operation
overloads — never as a field on the template `EntryRep`.

This resolves the SOW §2.4 / §5 B1 version-skew question in the **loud-break** direction, which for a
security-relevant feature is the only safe default:

- **Loud break under skew.** An old server does not implement the new `OutriggerServer` overloads, and its
  proxy cannot be cast to the new client interface. A filtered call therefore fails at dispatch, outright.
  The rejected alternative — an optional `@AtomicSerial` field on `EntryRep` — is **fail-open**: STD-006
  §11.8 says an old server silently drops an unknown field, so a new client's filter would vanish and the
  query would run **unfiltered**, over-returning. A filter that silently disappears is the one failure a
  fail-closed matching rule cannot catch, because nothing on the candidate side is wrong.
- **No write-path dual-use.** `EntryRep` serves both templates and stored entries. Because the filter is
  never an `EntryRep` field, there is no populated-filter-on-a-write hazard to detect and reject: a written
  entry's rep simply has no place to carry one.
- **No store()/restore() leak.** `EntryRep` has a second serialization path (`store()`/`restore()` →
  snaplogstore) distinct from the wire `serialForm()`/`serialize()`. An `EntryRep` filter field would have
  to be scrubbed from persistence; an operation parameter is structurally absent from both paths.

**Permanent API — `OutriggerServer` (outrigger-dl).** Eight filtered overloads, each mirroring its unfiltered
sibling with a trailing `byte[] filterEnvelope` and `throws FilterRejectedException`:

```
Object read       (EntryRep tmpl,  Transaction, long timeout, QueryCookie, byte[] filterEnvelope)
Object readIfExists(EntryRep tmpl, Transaction, long timeout, QueryCookie, byte[] filterEnvelope)
Object take       (EntryRep tmpl,  Transaction, long timeout, QueryCookie, byte[] filterEnvelope)
Object takeIfExists(EntryRep tmpl, Transaction, long timeout, QueryCookie, byte[] filterEnvelope)
EventRegistration notify(EntryRep tmpl, Transaction, RemoteEventListener, long lease,
                         MarshalledInstance handback, byte[] filterEnvelope)
EventRegistration registerForAvailabilityEvent(EntryRep[] tmpls, Transaction, boolean visibilityOnly,
                         RemoteEventListener, long leaseTime, MarshalledInstance handback,
                         byte[] filterEnvelope)
MatchSetData contents(EntryRep[] tmpls, Transaction, long leaseTime, long limit, byte[] filterEnvelope)
Object take       (EntryRep[] tmpls, Transaction, long timeout, int limit, QueryCookie,
                         byte[] filterEnvelope)
```

Peter chose to wire **both** the synchronous scan (`read`/`readIfExists`/`take`/`takeIfExists`) **and** the
standing-query path (`notify`, `registerForAvailabilityEvent`, `contents(...)`, and bulk `take(EntryRep[])`)
in B1. All eight overloads admit their filter in B1; none evaluate it yet — each throws
`FilterRejectedException(EVALUATION_NOT_WIRED)` at its match chokepoint until B3 (§4.1).

---

## 2. Client-facing interface: `FilteredJavaSpace` (proposed permanent name — OPEN)

**Decision (Peter).** `SpaceProxy2` additionally implements a new interface,
**`net.jini.space.FilteredJavaSpace`** (module **`jgdms-lib-dl`**, alongside `JavaSpace`, `JavaSpace05`,
`TupleSpace`, `MatchSet`), whose eight methods mirror the JavaSpace/`JavaSpace05`/`TupleSpace` operations with
a trailing `byte[] filter`:

```
Entry read/readIfExists/take/takeIfExists(Entry tmpl, Transaction, long timeout, byte[] filter)
EventRegistration notify(Entry, Transaction, RemoteEventListener, long lease,
                         MarshalledInstance handback, byte[] filter)
EventRegistration registerForAvailabilityEvent(Collection tmpls, Transaction, boolean,
                         RemoteEventListener, long lease, MarshalledInstance handback, byte[] filter)
MatchSet contents(Collection tmpls, Transaction, long leaseDuration, long maxEntries, byte[] filter)
Collection take(Collection tmpls, Transaction, long timeout, long maxEntries, byte[] filter)
```

A client obtains filtered semantics by casting its space proxy to `FilteredJavaSpace`. A `null` filter is a
caller error (`NullPointerException`) — the ordinary unfiltered methods exist for unfiltered queries.

`ConstrainableSpaceProxy2.methodMapArray` gains a client-method → backend-method pair for each of the eight,
so per-method constraints (including the space's `ATOMIC_DER` `MarshallingFormat` requirement) map onto the
filtered backend calls exactly as for the unfiltered siblings.

**DECISION (Peter): `net.jini.space` in `jgdms-lib-dl`, NOT the `-dl` proxy package.** `FilteredJavaSpace` is
a client-compile-time API — a client programs against it and casts its proxy to it — so it cannot live in
`org.apache.river.outrigger.proxy`, which ships in the **downloaded** codebase proxy jar. It belongs in the
public space-API namespace `net.jini.space` (module `jgdms-lib-dl`), the same place `SpaceProxy2` already gets
`JavaSpace05`/`TupleSpace` from — so this needs **no new module dependency**, only imports. Its companion
exception **`FilterRejectedException` moves with it**, to `net.jini.space` (next to `InternalSpaceException`,
the natural home for a space-API exception); every filtered method — both the client interface and the
`OutriggerServer` backend — throws that relocated exception. Neither type names any `jgdms-cel`/`jgdms-der`
type, so hosting them in the general API module introduces no CEL dependency there (the reason vocabulary is a
plain transport-neutral enum). The Outrigger-specific pieces stay in Outrigger: the opaque `FilterEnvelope`
codec (`outrigger-dl`), the admission seam and `CompiledFilter` (`outrigger-service`), and the authoring
adapter (`outrigger-cel-authoring`).

---

## 3. Envelope wire format (outrigger-dl, opaque, release-8)

**Decision.** The envelope is a minimal, canonical DER SEQUENCE, and nothing more:

```
FilterEnvelope ::= SEQUENCE {
    version   INTEGER,        -- currently 1; a different version is refused
    celWire   OCTET STRING    -- an OPAQUE CEL CelFilterRecord DER encoding
}
```

- **No bindings, no name table, no `targetClassName`.** Deliberately absent. The server resolves the CEL
  expression's field names against the **candidate's own v2 schema**, server-side; there is no client-
  supplied name mapping for the server to trust. This is the whole point of class-free filtering.
- **Applicability key = `entrySchemaDigest`.** Not `className`. The filter is admitted against a template's
  `entrySchemaDigest`, and (in B3) applies to a candidate only if the candidate's `entrySchemaDigest`
  matches — a fail-closed identity, canonical across senders because DER is canonical.
- **Layering.** The codec (`FilterEnvelope`) lives in `outrigger-dl` (release-8, **no** `jgdms-cel`/`jgdms-der`
  dependency) and treats `celWire` as opaque octets. Both the client authoring path and the server admission
  seam share this one grammar. Because `outrigger-dl` cannot use the JDK25-only `jgdms-der` DER primitives,
  the codec is a small **self-contained** canonical DER reader/writer that mirrors `DerReader`/`DerWriter`
  rules (definite length, minimal length, canonical INTEGER, SEQUENCE-boundary trailing-byte rejection).
- **Fail-closed decode.** Rejected with `FilterRejectedException(ENVELOPE_MALFORMED)`: non-SEQUENCE outer,
  indefinite/non-minimal length, non-canonical INTEGER, trailing bytes after the SEQUENCE, extra elements
  inside it, wrong element tags, unsupported `version`, or a ceiling breach.
- **Ceilings.** `MAX_ENVELOPE_BYTES = 1 MiB`, `MAX_CEL_WIRE_BYTES = 64 KiB` — coarse pre-verification DoS
  bounds only. The authoritative CEL size/cost ceilings are the verifier's, enforced at admission.
- **Multi-template ceiling.** `FilterAdmission.MAX_TEMPLATES = 256` (a fixed compile-time constant, not a
  runtime knob) bounds the number of templates a single multi-template filtered op may carry — see §4.2. This
  caps the admission-amplification factor; without it an unbounded template array forces one schema build +
  CEL verification per element.

---

## 4. Server admission seam (outrigger-service)

**Decision.** At operation entry, **before matching**, `FilterAdmission.admit(byte[] filterEnvelope, EntryRep tmpl)`
runs a fail-closed pipeline and returns an immutable `CompiledFilter`, or throws `FilterRejectedException`:

1. `FilterEnvelope.decode` → `{version, celWire}` (ENVELOPE_MALFORMED on any defect).
2. Build a typed `SchemaView` from the **template's own v2 schema**, class-free:
   `EntryRepV2Codec.decodeEntrySchemaChain(tmpl.bodyBytes())` (a new read-only helper in `jgdms-der`) yields
   `{entrySchemaDigest, List<AtomicSerialSchemaRecord>}`, wrapped in `DerSchemaChainView`. The server never
   loads the entry class. A **null / match-any template** (empty body) ⇒ schema-less `CelVerifier.verify(byte[])`
   — a legal full scan.
3. `CelVerifier.verify(celWire, schemaView)`. A non-accepted result maps 1:1 to a `FilterRejectedException.Reason`
   (`DECODE_REJECTED`→`FILTER_DECODE_REJECTED`, `COST_EXCEEDED`→`FILTER_COST_EXCEEDED`,
   `STATIC_TYPE_MISMATCH`→`FILTER_TYPE_MISMATCH`, `RESULT_TYPE_MISMATCH`→`FILTER_RESULT_TYPE_MISMATCH`).
4. Require `record.context() instanceof EvaluationContext.Predicate`. A `Transform` is refused
   (`NOT_A_PREDICATE`) — only a boolean predicate may gate a query.
5. Construct `CompiledFilter { ExprNode expr, long cost, byte[] applicabilitySchemaDigest }`. Immutable; the
   only producer is the seam.

If the template body is present but does not decode as a v2 body, admission fails closed with
`SCHEMA_UNAVAILABLE` — a filter that names fields is never admitted without a schema to check it against.

**Every rejection fails the operation loudly.** A bad filter is never downgraded to an unfiltered query.
`FilterAdmission.admit` does **not** evaluate the predicate against any entry.

Three filtered ops are multi-template (see §4.2): `registerForAvailabilityEvent`, filtered `contents`, and
filtered bulk `take`. Each admits the one filter against **every** template's own schema; any failure refuses
the whole op.

### 4.1 The unwired-chokepoint loud reject (why filtered ops fail in B1)

B1 admits filters but does **not** wire predicate **evaluation** at the match chokepoints (that is B3). To
honour "reject rather than run unfiltered", each filtered `OutriggerServerImpl` method admits the filter and
then throws `FilterRejectedException(EVALUATION_NOT_WIRED)` rather than execute the query unfiltered. When B3
lands, the `FilterAdmission.evaluationNotWired(...)` call at each chokepoint is replaced by threading the
`CompiledFilter` into `EntryRep.matches`/`TemplateHandle.matches`. This keeps B1 shippable and safe: a
filtered query is never silently unfiltered, at any layer.

### 4.2 Multi-template ops: decode-once seam + admission ceiling

Three filtered ops are **multi-template with a single filter**: `registerForAvailabilityEvent`, filtered
`contents(EntryRep[], …, byte[] filterEnvelope)`, and filtered bulk `take(EntryRep[], …, byte[] filterEnvelope)`.
Each admits the one filter against **every** template's own schema; any failure refuses the whole op. Two
properties keep that loop from becoming a DoS amplifier:

- **Shared ceiling.** Each op calls `FilterAdmission.checkTemplateCount(tmpls.length)` immediately after
  `checkForEmpty`, before any admission work. A collection larger than `MAX_TEMPLATES` (256) is rejected
  loudly with `FilterRejectedException(TEMPLATE_COUNT_EXCEEDED)` and counted in `filter.rejected.templateCount`
  — never truncated (silent truncation would under-filter, the "never downgrade" hazard). Worst-case admission
  work for one op is thus bounded by 256 × (one schema build + one CEL verify of a ≤64 KiB filter).
- **Decode-once seam.** The envelope is **template-invariant**, so decoding it per template is wasted work.
  `FilterAdmission.prepare(byte[])` unwraps it once into an immutable `PreparedFilter` (holding only the opaque
  `celWire`), and the op loops `admit(PreparedFilter, tmpl)` — the schema build + CEL verify are the only
  per-template steps. `admit(byte[], tmpl)` remains as a single-template convenience delegating to
  `admit(prepare(env), tmpl)`. **Scope note:** only the *envelope* decode is hoisted; the CEL AST is still
  decoded per template inside `CelVerifier.verify`, because reusing the decoded AST would bypass the verifier's
  deliberate byte[]-only admission gate (see `CelVerifier` class javadoc). The ceiling bounds that residual.

---

## 5. Observability (operator-only by default)

`FilterAdmission` maintains transport-neutral counters, snapshot via `metrics()` / `METRIC_NAMES`:

- `filter.admitted`, and one `filter.rejected.*` per rejection reason (envelope, templateCount,
  schemaUnavailable, decode, cost, typeMismatch, resultTypeMismatch, notPredicate);
- `filter.evaluationNotWired` (B1's unwired-chokepoint rejections);
- **`filter.failClosedExclusions`** — the SOW §5 B1 observability requirement: a candidate silently excluded
  during evaluation by a fail-closed rule (undecodable / wrong-format / **missing referenced field**), as
  distinct from a genuine "no entries match". This distinguishes a fail-closed no-match from an honest empty
  result. The counter/metric name is fixed here as permanent API; it is **populated by B3** (which does the
  evaluation), declared now so the name is stable.

**DECISION (Peter): operator-only.** These stay operator-facing counters, off the client result path. No
per-query fail-closed-exclusion signal is surfaced to the *calling client*: a client-visible exclusion count
is itself a mild information channel about data the client could not otherwise see. Operator-only is what B1
ships and the settled position.

---

## 6. Contract for B2 (so B2 can proceed in parallel)

B2 builds the lazy field projector — the class-free reader that decodes only predicate-referenced fields from
a candidate's v2 body. B1 fixes the seam B2 must satisfy so the two units compose:

**`CompiledFilter` is the hand-off object.** B3's evaluator will receive a `CompiledFilter` (verified
`ExprNode expr`, `long cost`, `byte[] applicabilitySchemaDigest`) and, per candidate, a projection of that
candidate's fields. B2's projector is the thing that produces that projection.

**Proposed `CandidateProjection` interface (B2 implements, B3 consumes).** A minimal, class-free, fail-closed
field reader keyed by the CEL field-reference model (STD-006 §3.9 per-class namespaces):

```
interface CandidateProjection {
    /** The candidate's entrySchemaDigest — B3 checks it equals the filter's applicability key. */
    byte[] entrySchemaDigest();

    /**
     * The decoded value of one predicate-referenced field, or a sentinel for "absent"
     * (wildcard/null slice) vs "not in this candidate's schema". Decodes ONLY on demand,
     * from the already-canonical v2 slice bytes (no second parser, no class load).
     * Fail-closed: a decode failure surfaces as an exclusion, never a lenient value.
     */
    ProjectedValue value(SelectorPath field);

    /** True if the candidate's schema declares the referenced field at all (missing ⇒ fail-closed no-match). */
    boolean declares(SelectorPath field);
}
```

Constraints B1 pins for B2 (from SOW §3.2/§3.3):
- The projection reader is the **existing fail-closed codec** (`EntryRepV2Codec` slice decode / the machinery
  behind `ObjectCodec.decodeToFieldMap`), never a second lenient scanner. Laziness (TLV-skip to the touched
  field) is an optimization *inside* that codec.
- Field-name resolution is against the **candidate's own** v2 schema, using the same
  `DerSchemaChainView`/`SchemaView` model the admission seam type-checked against — so the wire schema, the
  authoring schema (`EntrySchemaView`), and the projection schema are one model.
- A candidate whose bytes fail canonical decode, or whose schema lacks a referenced field ⇒ **no match**
  (fail-closed), counted in `filter.failClosedExclusions`, **never** an error.
- Evaluation must not run against a transactionally-unconfirmed candidate (SOW §3.2 confused-deputy rule).
  That gate is B3's, but the projection must not itself leak: it reads only bytes the server already holds.

### 6.1 Applicability rule — the pinned B3 contract (Peter's ruling, OPTION (b))

`CompiledFilter.applicabilitySchemaDigest()` is the key that decides *which candidates a filter applies to*:

- **Non-null key** (filter admitted against a concrete template): the filter applies **only** to candidates
  whose `entrySchemaDigest` equals the key. A candidate of any other schema is a non-match — it is not even
  a fail-closed exclusion, it is simply out of scope. Field names were already type-checked against that one
  schema at admission (a wrong *type* on an existing field was rejected loudly; an unknown field *name*
  deferred — see below).

  **`[AMENDED — RATIFIED Peter 2026-07-26]`** A candidate whose `entrySchemaDigest` matches no filter key is
  **no longer** treated as an unconditional non-match/out-of-scope. B3 established that this case is dominated
  by **subclass entries**: a subclass candidate byte-matches its superclass template on the template's own
  fields but carries its own, longer schema chain, so its digest differs from the template's key even though it
  is a legitimate byte-matched candidate — treating that as unconditional "out of scope" let such candidates
  through **entirely unfiltered**, contradicting this memo's own §1 rationale. Per B3, a digest-mismatched
  candidate is instead **resolved schema-less against its own schema chain**, exactly like the null-key case
  immediately below: each referenced field name is looked up in the candidate's own v2 schema;
  present-and-unambiguous ⇒ evaluated against the candidate's own value; absent-or-ambiguous ⇒ a fail-closed
  exclusion (counted in `filter.failClosedExclusions`), never an unconditional pass-through. This means subclass
  entries are now filtered by their inherited fields. See `DESIGN-Outrigger-CEL-Filter-B3.md` §5 (and §3, where
  the amended non-null-key rule is stated in full) for the ratified mechanism.
- **Null key** (filter admitted schema-lessly, against a null / match-any template): the filter applies to
  **ALL candidates**. There was no template schema to type-check against, so each referenced field name is
  resolved **per candidate**, against that candidate's own v2 schema, at evaluation time. A candidate whose
  schema lacks a referenced field is a **fail-closed exclusion** (counted in `filter.failClosedExclusions`),
  never a match and never an error.

**Why a schema-less filter may still reference fields.** The CEL verifier soundly *defers* a field reference
whose type it cannot statically determine to `UNKNOWN`, and an `UNKNOWN` operand never by itself triggers a
static rejection (`StaticTypeChecker`). So `verify(celWire)` with no schema admits `a == "x"`; and even
`verify(celWire, schema)` admits a reference to a field **name** absent from that schema (deferred), while it
rejects a **wrong type** on a field the schema *does* declare. Admission is therefore intentionally permissive
about unknown names (the safe direction: defer, then fail-closed-exclude at eval) and strict about provable
type errors (reject loudly). `CompiledFilter.isSchemaLess()` documents this; it does **not** mean "references
no fields".

### 6.2 Multi-template plurality (B3 obligation)

B1's `registerForAvailabilityEvent`, filtered `contents(...)`, and bulk `take(EntryRep[], ...)` each admit the
one filter against **every** template's schema (all must pass) and then throw `EVALUATION_NOT_WIRED`, so all
three currently retain only the last `CompiledFilter` (`OutriggerServerImpl` ~2341–2387) — harmless in B1
because nothing is evaluated. **B3 MUST retain one `CompiledFilter` per template, in all three operations**:
each template yields a distinct `applicabilitySchemaDigest` (or a null key, for a match-any template in the
collection), and a candidate is filtered by the `CompiledFilter` whose applicability key it matches (or by
every null-key filter in the registration/query, per §6.1). Collapsing any of the three to a single retained
filter would misapply one template's predicate/applicability to another template's candidates.

The exact `SelectorPath` / `ProjectedValue` shapes are B2's to finalize; B1 commits only to (a) `CompiledFilter`
as the verified-predicate carrier, (b) `entrySchemaDigest` as the applicability key with the null-key
all-candidates rule of §6.1, and (c) a class-free, fail-closed, schema-driven, decode-on-demand projection
contract.

---

## 7. Client authoring adapter (outrigger-cel-authoring — new module)

Layering forced a small new **client** module: `EntryFilter.compile(rule, type)` needs both the envelope codec
(`outrigger-dl`) and CEL authoring (`jgdms-cel-authoring`), and neither may depend on the other. A general CEL
module must not depend on an Outrigger service jar, and the server jar must not carry client authoring, so a
downstream client jar is the correct home.

- `EntrySchemaView.of(Class<? extends Entry>)` — a `SchemaView` over an entry class that **reuses**
  `EntrySchemaGenerator.forClass(...)` (the single reflective field-rule authority) wrapped in
  `DerSchemaChainView`. It duplicates **no** field rules; a drift-guard test pins it field-for-field to the
  generator. This guarantees the schema an author type-checks against is byte-identical to the wire schema the
  server re-derives.
- `EntryFilter.compile(String rule, Class<? extends Entry> type) → byte[]`:
  `CelTextParser.parse(rule, EntrySchemaView.of(type))` → `CelRecordBuilder.predicate(...)` →
  `CelEncoder.encode(...)` → `FilterEnvelope.encode(...)`.

The server nonetheless re-verifies from scratch and trusts nothing the client sent (the envelope carries no
name table); a well-typed rule is accepted at admission, a mistyped one rejected there (or earlier, at
authoring — both loud).

---

## 8. Module + dependency summary

| Module | Adds | Release | New dep |
|--------|------|---------|---------|
| `jgdms-der` | `EntryRepV2Codec.decodeEntrySchemaChain` (read-only helper + `EntrySchemaChain` record) | 25 | — |
| `jgdms-lib-dl` | `net.jini.space.FilteredJavaSpace` (client API), `net.jini.space.FilterRejectedException` (API exception) | 8 | none |
| `outrigger-dl` | `FilterEnvelope` (opaque codec); `OutriggerServer` filtered overloads; `SpaceProxy2` + `ConstrainableSpaceProxy2` plumbing; `EntryRep.bodyBytes()` | 8 | none (already deps `jgdms-lib-dl`) |
| `outrigger-service` | `CompiledFilter`, `FilterAdmission`; filtered `OutriggerServerImpl` + `OutriggerServerWrapper` methods | 21 | `jgdms-cel` (precedent: already deps release-25 `jgdms-der`) |
| `outrigger-cel-authoring` (new) | `EntrySchemaView`, `EntryFilter` | 25 | `outrigger-dl`, `jgdms-cel-authoring` |

The client-facing API (`FilteredJavaSpace` + `FilterRejectedException`) lives in the public `net.jini.space`
namespace (`jgdms-lib-dl`), not the downloaded `-dl` proxy package; both are CEL-free (opaque `byte[]` + a
transport-neutral reason enum). `outrigger-dl` keeps its release-8, CEL-free discipline: its only filter type
is the opaque `FilterEnvelope` codec. All CEL type contact is server-side (`outrigger-service`) or
client-authoring-side (`outrigger-cel-authoring`).

---

## 9. Explicitly deferred (not in B1)

Predicate evaluation at any chokepoint (B3); `EntryProjection`/`CandidateProjection` implementation (B2 — §6
fixes its contract); filtered-registration persistence/recovery; write-path cost caps beyond the verifier's
own (B4). B1 stops at "a verified filter exists".

## 10. Open questions carried to the board

1. **[RESOLVED — Peter]** `FilteredJavaSpace` name/home (§2): name kept; placed in **`net.jini.space`**
   (module `jgdms-lib-dl`), the public space-API namespace — NOT the downloaded `-dl` proxy package.
   `FilterRejectedException` moves with it to `net.jini.space` (next to `InternalSpaceException`).
2. **[RESOLVED — Peter]** Observability audience (§5): operator-only; no client-visible exclusion count.
3. **Envelope `version` forward-compatibility** — B1 refuses any `version ≠ 1` (fail-closed). If a future
   version must be introduced without a flag-day, the negotiation rule (advertise supported versions? refuse
   loudly and require redeploy?) is a decision to take when a v2 envelope is actually needed, not now.
4. **Multi-template availability filter semantics** (§4) — B1 admits the one filter against every template's
   schema (all must pass). Whether a per-template filter (or a filter scoped to a subset) is ever wanted is a
   B3/B-later semantics question; the wire shape (one envelope per registration) does not preclude it.
