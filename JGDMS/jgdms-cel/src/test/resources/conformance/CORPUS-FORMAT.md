# DETERMINISTIC CEL Conformance Corpus — Format Specification

**Status:** v1 (T5 of `SOW-CEL-Filter-Format.md`).
**Normative references:** `JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md`
§13 (conformance requirements this corpus discharges) and Appendix B
(`JGDMS-STD-011-Appendix-B-DER-Wire-Encoding-v0.1-DRAFT.md`, especially §B.5/§B.6/§B.11 —
canonical-form rejects and the vectors that appendix requires this corpus to carry).

## 0. Purpose (read this before writing a runner)

This corpus is the falsifiable form of the claim "the same mechanism serves Rust and
Java" (SOW §2). It is **pure data** — every file under `vectors/` is plain JSON, with
no reference to any Java class, Rust crate, or test framework. A conformant runner in
*any* language:

1. Reads a vector file as JSON.
2. Builds the wire bytes / candidate / schema the vector describes, from the JSON alone.
3. Drives its own implementation's public decode/verify/evaluate entry points against
   that input.
4. Asserts its outcome matches the vector's `expected` field **exactly** — bit-exact for
   doubles, exact error-code identity, exact accept/reject identity. No tolerances
   anywhere (STD-011 §13 item 1; §7.5 ratified correct rounding removes the one place a
   tolerance might otherwise have crept in).

A runner MUST NOT special-case a vector's `id` or `description` — those fields are for
humans (failure reports, `git blame`, cross-referencing this spec) and MUST NOT affect
which code path a runner takes. The only fields with runtime meaning are `category`,
`wireHex`, `candidate`, `schema`, `expected`, and `requires`.

## 1. File layout

```
src/test/resources/conformance/
  CORPUS-FORMAT.md              -- this file
  vectors/
    decode-accept.json          -- category "decode-accept"
    decode-reject.json          -- category "decode-reject"
    verify-reject.json          -- category "verify-reject"
    verify-accept-defer.json    -- category "verify-accept-defer"
    verify-accept.json          -- category "verify-accept"
    eval.json                   -- category "eval", requires: [] (unconditional)
    eval-transcendental.json    -- category "eval", requires: ["conformant-transcendentals"]
  oracle/
    transcendental_oracle.py    -- independent MPFR-class oracle that generated
                                    eval-transcendental.json's expected bit patterns
```

Each `vectors/*.json` file is a JSON array of **vector objects** (§2). The filename is
not itself load-bearing — a runner discovers vectors by reading every `*.json` file
under `vectors/` and dispatching each vector object by its own `category` field, not by
which file it came from. The split above exists for human navigability and per-category
pinned minimums (§4), not because the runtime cares.

A future Rust runner consumes this directory **unchanged** — same JSON, same field
names, same hex/bit-pattern encodings. Nothing under `vectors/` or `oracle/` may assume
a JVM, and nothing in this document does.

## 2. Vector object schema

Every vector is a JSON object with these common fields:

| Field | Type | Meaning |
|---|---|---|
| `id` | string | **Unique across the entire corpus** (not merely within its own file) — a vector may be moved between files without a rename, and a runner MUST fail the whole run if it discovers the same `id` twice anywhere under `vectors/` (§4.1). Stable across corpus revisions where possible (referenced in bug reports, board reviews). |
| `category` | string | One of the six category names in §3. Determines every other field's shape. |
| `description` | string | Human-readable, spec-cross-referenced (e.g. `"Appendix B §B.10.1 worked example"`). |
| `provenance` | string | `"spec"` (default if absent): every field of `expected` is read directly off the standard's normative text or worked examples. `"oracle"`: `expected` was computed by an independent arbitrary-precision oracle (`oracle/transcendental_oracle.py`), not by either evaluator under test. `"implementation-derived"`: the expected value could not be sourced from the spec/oracle and was instead taken from this session's own `jgdms-cel` implementation — flagged so a reviewer knows this vector is self-referential (G12) and cannot by itself detect a shared-misunderstanding bug; every such vector's `description` explains *why* no independent source existed. See §7 for this corpus's current honesty-ledger status. |
| `requires` | array of strings, optional | Feature gates. Empty/absent = the vector always runs. The only *defined* gate today is `"conformant-transcendentals"` (§13.4/§7.5): a vector tagged with it exercises `sin`/`cos`/`tan`/`asin`/`acos`/`atan`/`atan2`'s *correctly-rounded value*, which requires a `MathProvider.conformant(...)` implementation (T3 phase 2) to be installed. A runner with no conformant provider installed MUST skip such a vector loudly (§5), never silently pass it. **Any gate name a vector carries that is not one this runner recognizes at all MUST be treated identically: unsatisfiable, hence skipped loudly (naming the unrecognized gate), never silently run and never a hard failure of the whole suite** (§4.2) — a future vector tagged with a gate a given runner build predates is exactly as "not yet available" as a known gate with no provider installed, not a corpus error. |
| `wireHex` | string | Lowercase hex, no spaces or `0x` prefix, of the complete canonical `CelFilterRecord` DER encoding (Appendix B §B.3/§B.9) this vector exercises. Present in every category. |
| `candidate` | object, optional | Present only for `"eval"` vectors — see §2.3. |
| `schema` | object or `null`, optional | Meaningful only for the three `CelVerifier`-driven categories (`"verify-reject"`/`"verify-accept-defer"`/`"verify-accept"`) — see §2.4. `null`/absent means "verify with no governing schema" (`CelVerifier.verify(wire)`). |
| `expected` | object | The outcome — shape depends on `category`, see §3. |

### 2.1 Hex and bit-pattern conventions (G1: exact string forms, never decimal floats)

- **Wire bytes** (`wireHex`): lowercase hex, e.g. `"301b0201018000b214..."`. Decoded byte
  count is always even; a runner MUST treat an odd-length string as a malformed corpus
  file (fail the whole run, §5), not as a single vector's failure.
- **`double` values, everywhere in this corpus** (candidate field values, expected
  results): always the exact IEEE-754 binary64 bit pattern, as a `"0x"`-prefixed,
  16-hex-digit string, e.g. `"0x3ff0000000000000"` for `1.0`, `"0x8000000000000000"` for
  `-0.0`, `"0x7ff8000000000000"` for the canonical quiet NaN. **Never** a JSON number —
  a JSON/JavaScript-style float literal cannot losslessly round-trip every bit pattern
  this corpus needs (signed zero, NaN payloads, subnormals near precision boundaries),
  and "shortest round-trip decimal" is a different, weaker guarantee than "these exact
  64 bits." A runner reads the hex string, parses it as an unsigned 64-bit integer, and
  reinterprets those bits as binary64 (Java: `Double.longBitsToDouble(Long.parseUnsignedLong(hex,16))`).
- **`int` values**: a JSON string holding the exact decimal representation, e.g.
  `"-9223372036854775808"`. Never a bare JSON number — `int64`'s full range is not
  guaranteed representable as a JSON number in every parser (JavaScript's `number` is a
  float64 and loses precision above 2^53), and this corpus's own checked-int edge
  vectors specifically probe values at the `int64` boundary where that loss would be
  silent and exactly wrong.
- **`bytes` values**: lowercase hex string (same convention as `wireHex`), e.g.
  `"deadbeef"`. Empty bytes is `""`.
- **`string` values**: an ordinary JSON string. Supplementary-plane code points are
  written as their UTF-16 surrogate pair, exactly as JSON already requires — no special
  convention needed.
- **`bool`**: JSON `true`/`false`.
- **`null_t`**: represented by `{"type": "NULL"}` with no `value` field.

### 2.2 AST node-kind names

Where a vector needs to name an AST node kind (`decode-accept`'s optional
`expected.rootNodeKind`), it uses Appendix B §B.4.1's own node names verbatim:
`LIT_BOOL`, `LIT_INT`, `LIT_DOUBLE`, `LIT_STRING`, `LIT_BYTES`, `LIT_NULL`, `LIST_LIT`,
`FIELD_REF`, `HAS`, `NOT`, `NEG`, `AND`, `OR`, `EQ`, `NE`, `LT`, `LE`, `GT`, `GE`, `ADD`,
`SUB`, `MUL`, `DIV`, `MOD`, `IN`, `COND`, `CALL`. These are the spec's own tag-table
names (§B.4.1's "Node" column) — not a Java class name — so a Rust runner names its own
enum arms identically without inventing a mapping.

### 2.3 Candidate projection as pure data

A `"candidate"` object is either:

- `{"undecodable": true}` — the whole candidate is fail-closed-undecodable
  (`CandidateProjection.isUndecodable() == true`; STD-011 §8.1 item 2); every field
  access errors `CANDIDATE_UNDECODABLE`. No other keys are present.
- Or a projection object:

  ```json
  {
    "namespaceChain": ["com.example.Sub", "com.example.Base"],
    "fields": {
      "com.example.Sub":  { "note": {"type": "STRING", "value": "hello"} },
      "com.example.Base": { "sampleCount": {"type": "INT", "value": "30"} }
    }
  }
  ```

  `namespaceChain` is the ordered list of class names, **leaf (most-derived) first**
  (STD-006 §3.9 / STD-011 §8.2) — this order is what makes an unqualified name declared
  by two classes `AMBIGUOUS_FIELD` rather than "first match wins." `fields` maps
  `className -> fieldName -> typed value`; a `className` not listed in `namespaceChain`
  is meaningless (a runner MAY reject a corpus file that does this — it indicates the
  vector itself is malformed).

  A **typed value** is `{"type": T, ...}` where `T` and the rest of the shape are:

  | `type` | extra fields | notes |
  |---|---|---|
  | `BOOL` | `value`: JSON bool | |
  | `INT` | `value`: decimal string | §2.1 |
  | `DOUBLE` | `value`: `"0x..."` bit-pattern string | §2.1 — never `NaN`/`Infinity` JSON literals, which do not exist in JSON at all |
  | `STRING` | `value`: JSON string | |
  | `BYTES` | `value`: hex string | |
  | `NULL` | (none) | the field is present, wire-null (STD-011 §4.6 case 2) |
  | `LIST` | `elementType`: one of `BOOL`/`INT`/`DOUBLE`/`STRING`/`BYTES`; `elements`: JSON array of bare values in that element type's own convention (e.g. an `INT` list's `elements` is an array of decimal strings) | §4.7 |
  | `OBJECT` | `projection`: a nested candidate-projection object, recursively in this same shape (never `{"undecodable":true}` — an undecodable *nested* object is not expressible by this corpus's vectors; STD-011 doesn't need it, since `isUndecodable()` is a whole-candidate concept applied at the root) | §8.4 nested access |

  A field simply **absent** from a class's `fields` map is not declared by that
  namespace at all (STD-011 §4.6 case 3) — there is no explicit "absent" typed value;
  absence is the absence of the JSON key.

### 2.4 Schema as pure data (verify-reject / verify-accept-defer / verify-accept only)

```json
{
  "namespaceChain": ["com.example.Sub", "com.example.Base"],
  "fields": {
    "com.example.Sub":  {"note": "STRING"},
    "com.example.Base": {"sampleCount": "INT"}
  },
  "unresolvedTypeFields": {
    "com.example.Base": ["anyField"]
  }
}
```

`fields` maps `className -> fieldName -> CelType name` (`BOOL`/`INT`/`DOUBLE`/`STRING`/
`BYTES`/`NULL_T`/`LIST`/`OBJECT`) — the statically known type. `unresolvedTypeFields`
(optional) lists fields the schema declares present but whose type cannot be statically
pinned (STD-011 §12.4's "Any"-shaped field) — `SchemaView.declaresField` is `true` for
these, `fieldType` is empty (defers to dynamic evaluation, never a rejection). Nested
object schemas are out of scope for this corpus's vectors (no vector needs a
statically-known nested class); `schema: null` or an absent `schema` field both mean
"verify with no governing schema at all."

## 3. Categories and their `expected` shapes

### 3.1 `decode-accept`

Drives `CelDecoder.decode(wireBytes)` and asserts it returns successfully (no
exception/error of any kind).

```json
"expected": {
  "outcome": "accept",
  "context": "predicate",
  "rootNodeKind": "GE"
}
```

`context` is `"predicate"` or `"transform"`. `rootNodeKind` (optional, §2.2) is checked
against the decoded expression's own top-level node kind when present — a light
structural sanity check, not a full AST equality (this corpus has no generic AST
serialization; a full re-serialization check is unnecessary because Appendix B's
canonical-form obligations mean any two conformant decoders that both accept the same
bytes necessarily built the same tree — there is exactly one legal parse).

### 3.2 `decode-reject`

Drives `CelDecoder.decode(wireBytes)` and asserts it throws/returns a decode error (any
decode-failure signal the runner's own decoder API uses — `CelDecodeException` on the
JVM).

```json
"expected": {
  "outcome": "reject",
  "reasonClass": "non-minimal-length"
}
```

`reasonClass` is a short, stable, human/documentation label (§B.5/§B.6's own item
names, kebab-cased) for *why* this specific byte string is illegal — it exists so a
failing run's report groups regressions by rejection category, and so this corpus's own
minimum-count pinning (§4) can be audited per reason. **A runner MUST NOT pattern-match
`reasonClass` against its own error message** — the only asserted fact is "decode
rejected," never "decode rejected for this specific internally-worded reason." Requiring
message-text matching would over-couple the corpus to one implementation's wording,
exactly the anti-pattern this corpus exists to avoid.

### 3.3 `verify-reject`

Drives `CelVerifier.verify(wireBytes, schema)` (`schema` per §2.4, or the no-schema
overload when `schema` is absent/`null`) and asserts rejection with a specific reason.

```json
"expected": {
  "outcome": "reject",
  "reason": "COST_EXCEEDED"
}
```

`reason` is one of the four closed `VerificationResult.Reason` values named in STD-011
§12: `DECODE_REJECTED`, `COST_EXCEEDED`, `STATIC_TYPE_MISMATCH`, `RESULT_TYPE_MISMATCH`.
Unlike `decode-reject`'s free-text `reasonClass`, these four names **are** normative —
they are STD-011 §12's own layer names, not an implementation's internal wording, so an
exact match is asserted.

### 3.4 `verify-accept-defer`

Same call as §3.3, asserting acceptance instead (used for the "unknown/deferred field
type still verifies" cases STD-011 §12.4 requires: absent schema, or a schema present
but declaring the referenced field's type unresolved).

```json
"expected": {"outcome": "accept"}
```

### 3.5 `verify-accept`

Same call and `expected` shape as §3.4 (`{"outcome": "accept"}`) but for plain
acceptance vectors that are **not** about schema-deferred type checking — §3.4 exists
specifically for the "unknown/deferred field type still verifies" cases STD-011 §12.4
requires, and folding an unrelated acceptance vector into that category would misname
*why* it accepts. The motivating case (board-review fix list item 3): the exact
`maxExprCost` fencepost, `C(E) == maxExprCost` exactly, asserting *acceptance* at the
boundary — paired with a `verify-reject` vector at `C(E) == maxExprCost + 1` (§3.3) so
the fencepost is pinned from both sides. A runner with no separate `verify-accept-defer`
special-casing needed dispatches this identically to §3.4 (both are simply "call
`CelVerifier.verify`, assert accepted"); the two categories exist for corpus
readability/pinned-minimum auditing (§4), not because the runtime behaviour differs.

### 3.6 `eval`

Drives `CelDecoder.decode(wireBytes)` (must succeed — a malformed `eval` vector is a
corpus bug, §5) followed by `Evaluator.evaluate(record.expression(), candidate)`, where
`candidate` is built per §2.3. Every `eval` vector whose `requires` includes
`"conformant-transcendentals"` additionally needs a `MathProvider.conformant(...)`
installed (§5).

```json
"expected": {"outcome": "value", "type": "DOUBLE", "value": "0x3ff0000000000000"}
```
or
```json
"expected": {"outcome": "value", "type": "INT", "value": "30"}
```
or (`BOOL`/`STRING`/`BYTES` follow the same `type`/`value` shape as §2.1's candidate
typed-value convention; `NULL` has no `value`), or:
```json
"expected": {"outcome": "error", "code": "ABSENT_FIELD"}
```

`code` is one of the eight closed STD-011 §9.2 error names: `TYPE_MISMATCH`,
`OVERFLOW`, `DIVISION_BY_ZERO`, `ABSENT_FIELD`, `AMBIGUOUS_FIELD`, `DOMAIN`,
`COST_BOUND`, `CANDIDATE_UNDECODABLE`.

`LIST` and `OBJECT` (board-review fix list item 1) reuse §2.3's own candidate
typed-value shapes for these two types directly, rather than inventing a separate
"expected value" convention:

```json
"expected": {"outcome": "value", "type": "LIST", "elementType": "DOUBLE",
             "elements": ["0x3ff0000000000000", "0x4000000000000000"]}
```
```json
"expected": {"outcome": "value", "type": "OBJECT",
             "projection": {"namespaceChain": ["com.example.Leaf"],
                            "fields": {"com.example.Leaf": {"leaf": {"type": "INT", "value": "42"}}}}}
```

`LIST`'s `elementType`/`elements` are exactly §2.3's LIST typed-value fields (one of the
five scalar types; `elements` is a bare array in that element type's own convention —
never `NULL_T`/`LIST`/`OBJECT`, matching `CelValue.ListV`'s own restriction). `OBJECT`'s
`projection` is exactly §2.3's nested candidate-projection shape (`namespaceChain` +
`fields`); a runner checks every field the expected `projection` declares is present on
the actual evaluated object with a matching value — fields the expected `projection`
omits are not checked (an "at least this shape" assertion, since there is no
`CandidateProjection` enumeration API to compare against exhaustively). As of this
writing every `OBJECT`-result vector's underlying `CandidateProjection` happens to be
one level deep; a runner MUST still apply the same field-by-field check recursively for
any future vector nesting further, per this same rule.

An `eval` vector's own `wireHex` may wrap its expression in either
`EvaluationContext.predicate` or `EvaluationContext.transform` (§2.2's `decode-accept`
`context` field names both) — `Evaluator.evaluate` never reads `EvaluationContext` at
all (only `CelDecoder`/`CelVerifier` do), so which envelope an `eval` vector uses changes
nothing about how it evaluates. A conformant runner MUST NOT special-case `eval`
dispatch on `context` (consistent with §0's "no field but `category`/`wireHex`/
`candidate`/`schema`/`expected`/`requires` has runtime meaning" rule) — vectors using the
`transform` envelope exist purely to exercise that envelope and the `LIST`/`OBJECT`
result shapes above structurally, not because evaluation differs.

**Constructing `bytes` comparison vectors** (board-review fix list item 2): plain
literal-vs-literal comparisons, no candidate needed. §4.5's unsigned lexicographic
ordering is the trap to cover explicitly — an octet pair like `0x80` vs `0x7f` gives an
opposite answer under unsigned comparison (128 > 127) than under a naive signed-byte
comparison (-128 < 127), so at least one such pair belongs in the corpus alongside the
ordinary cases.

**Constructing `+-Inf` comparison-operator vectors** (board-review fix list item 4):
Appendix B §B.5 item 5 rejects any `LIT_DOUBLE` wire literal carrying a non-finite bit
pattern at *decode* time — there is no legal wire encoding for a literal `+Inf`/`-Inf`
operand at all. `+-Inf` values for these vectors are therefore always sourced from a
`candidate` field (§2.3's `DOUBLE` typed value never finiteness-checks) compared against
a finite wire literal, or against another field — never as a `LIT_DOUBLE` operand. This
is distinct from — and exercises a different code path than — this corpus's own
`DOMAIN` function-argument vectors (e.g. `sin(+Inf)` ⇒ `DOMAIN`, §5), which test a
platform function's own domain check rather than a comparison operator.

## 4. Pinned per-category minimum vector counts (G6)

A runner MUST fail the whole test run (not merely report a warning) if any category's
**total vector count across all files** falls below its pinned minimum. This is the
corpus's own tamper/truncation detector: a future edit that accidentally deletes most of
a category's vectors (a bad merge, an overzealous cleanup) is caught immediately, not
discovered months later as "conformance coverage quietly eroded."

| Category | Pinned minimum |
|---|---|
| `decode-accept` | 50 |
| `decode-reject` | 30 |
| `verify-reject` | 9 |
| `verify-accept-defer` | 3 |
| `verify-accept` | 1 |
| `eval` (unconditional, `requires` empty) | 100 |
| `eval` with `requires: ["conformant-transcendentals"]` | 150 |

These minimums are pinned in the runner (`CorpusRunnerTest`), not only in this document,
so a CI failure is the enforcement mechanism, not a documentation promise. Raising a
minimum is always safe (more coverage); lowering one is a decision that should be as
visible as a spec-conformance regression, because that is what it is. Deliberately kept
below the corpus's actual current count in every row (slack, not an exact-count pin) —
the point is catching *significant* truncation, not flagging every single-vector net
change as a minimum-update chore.

### 4.1 Corpus-wide vector-id uniqueness

A runner MUST fail the whole run (not merely the one affected `DynamicTest`) if the same
`id` appears on more than one vector anywhere under `vectors/` — across files, not only
within one (§2's `id` row). A per-file-only check would miss a duplicate introduced by
copy-pasting a vector from one category's file into another's while renumbering only
locally.

### 4.2 Unrecognized `requires` gates

A runner MUST treat any `requires` gate name it does not recognize as unsatisfiable —
skipped loudly, naming the gate, exactly like `conformant-transcendentals` with no
provider installed (§5) — **never** silently run (that would be running an
unvalidated assumption) and **never** a hard failure of the whole suite (a corpus using
a gate name from a newer format revision than a given runner build is a forward-
compatible situation, not a corpus bug). §2's `requires` row states this as the general
rule; §5 remains the one gate actually defined today.

### 4.3 Truncation-detector reliability caveat

§4's minimum-count check is reliable **only on a clean build**. A runner that reads
vector files from a build output directory (e.g. Java's `target/test-classes`) can read
a stale copy left over from before a vector file was pruned or a vector was deleted, on
an *incremental* build that did not re-copy test resources — silently defeating the
exact truncation detection this section exists to provide. Always force a clean
resource copy (`mvn clean test`, not merely `mvn test`) before trusting a passing
minimum-count check as proof nothing was silently deleted.

## 5. The `conformant-transcendentals` feature gate

STD-011 §7.5 ratified *correct rounding required* for `sin`/`cos`/`tan`/`asin`/`acos`/
`atan`/`atan2` — but this repository has not yet built the correctly-rounded provider
(`MathProvider.conformant(...)` is T3 phase 2, sequenced after this corpus per
`SOW-CEL-Filter-Format.md`'s status note). Every `eval` vector whose value depends on
one of those seven functions' *actual numeric result* is tagged
`requires: ["conformant-transcendentals"]`.

**An implementation nuance that widens this gate beyond "needs a rounded value," found
by this corpus rather than reasoned about in advance:** `Evaluator`'s dispatch for the
six single-argument functions (`sin`/`cos`/`tan`/`asin`/`acos`/`atan`) builds each CALL's
handler as a *bound* method reference, `mathProvider.get()::fn` — and `mathProvider.get()`
is evaluated *eagerly*, as part of preparing that reference, **before** the function's
own finite/domain check ever runs. So a DOMAIN vector for one of these six functions
(e.g. `asin(1.5)` ⇒ `DOMAIN`, an out-of-domain argument that never actually needs a
rounded result) still requires a provider to be installed — it is gated identically to a
genuine correctly-rounded-value vector, even though its *expected outcome* does not
itself depend on correct rounding. `atan2` is the one exception: its finite check runs on
plain values inside its own body *before* it ever touches `mathProvider.get()`, so an
`atan2(NaN, ...)` ⇒ `DOMAIN` vector carries no `requires` gate at all. A runner MUST NOT
assume a `DOMAIN`-outcome vector is provider-independent merely because its result
doesn't look numeric — check the vector's own `requires` field, never re-derive gating
from the function name.

A runner MUST:

- **Skip** every vector carrying this tag, with a **loud, aggregated** report line
  (vector count and the reason, e.g. `"SKIPPED 23 eval vectors requiring
  conformant-transcendentals: MathProvider.none()/nonConformantForTestingOnly()
  installed, no MathProvider.conformant(...) provider available (awaiting T3 phase 2)"`)
  when it has no conformant provider — never silently pass them, never count them as
  failures, and never omit the skip count from the run's summary.
- **Run** them for real, asserting bit-exact equality against the oracle-derived
  expected value, the moment a conformant provider **is** installed and claims
  conformance (`MathProvider.isConformant() == true`). A provider that claims
  conformance and fails even one of these vectors is a real conformance regression, not
  a skip.

This is the corpus's own enforcement of STD-011 §13 item 4's "independent of both
implementations" oracle requirement: these vectors' expected bit patterns come from
`oracle/transcendental_oracle.py` (arbitrary-precision, MPFR-class), never from running
either evaluator and recording what it happened to return.

## 6. What this corpus deliberately does not include

- **No octet-sort/canonical-collection-order vectors.** DETERMINISTIC CEL's AST has no
  construct analogous to STD-006's canonicalising `SET OF`/`SEQUENCE OF` collections —
  every multi-element structure here is order-preserving and semantically ordered
  (Appendix B §B.5 item 7). Manufacturing such a vector to satisfy a checklist would be
  dishonest; this is the same call Appendix B itself makes.
- **No vectors for STD-011 §5.3.1 items structurally eliminated by the wire grammar**
  (`has()`'s argument being a field designator; `in`'s list operand being a list literal
  or field designator; `field(...)`'s arguments being string literals) — Appendix B
  §B.2.3 shows there is no adversarial byte string that could violate these and still
  decode as the node in question, so a "reject" vector for them would be untestable by
  construction, not merely redundant.
- **No T4 (Rust)-specific vectors** — this corpus is language-neutral by design; there is
  nothing "Java-flavoured" to strip out. The same files are this corpus's Rust-side
  safety net per STD-011 §13 item 5 and the SOW's status note, unchanged.

## 7. Honesty ledger (G12)

As of this board-review pass, the corpus contains **zero** vectors carrying
`provenance: "implementation-derived"` — every vector's `expected` field traces to
either the standard's own normative text/worked examples (`provenance: "spec"`, the
default) or the independent arbitrary-precision oracle (`provenance: "oracle"`,
`oracle/transcendental_oracle.py`). This is a load-bearing claim about the whole corpus,
not merely a per-vector label default, so it is recorded here explicitly rather than
left to be inferred from the absence of the label anywhere: a reviewer auditing G12
compliance can grep every `vectors/*.json` file for `"implementation-derived"` and
expect zero matches today.

This is a point-in-time fact, not a permanent guarantee — a future contributor MAY add
a genuinely `implementation-derived` vector (§2's `provenance` row already defines the
label and requires an explanatory `description` for why no independent source existed),
and doing so honestly is entirely legitimate when a spec or oracle source really does
not exist for some behaviour. What is **not** legitimate is adding such a vector without
the label, or mislabeling an implementation-derived value as `"spec"`/`"oracle"` — either
would silently reintroduce exactly the shared-misunderstanding blind spot this ledger
exists to keep visible. Whenever this ledger's zero count changes, update this section
to say so plainly, with the vector `id`(s) and the reason no independent source existed.
