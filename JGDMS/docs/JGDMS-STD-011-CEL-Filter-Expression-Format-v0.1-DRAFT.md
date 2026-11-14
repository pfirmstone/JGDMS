# JGDMS-STD-011 — DETERMINISTIC CEL Filter/Transform Expression Format

> **Format name.** This standard defines the expression format named
> **DETERMINISTIC CEL** (name proposed by Peter, 2026-07-20, following the
> STD-006 precedent: the format gets a proper name — here naming what it *is*, a
> deterministic refinement of a strict CEL subset — while the **standard document
> identifier** remains `JGDMS-STD-011`). Appendix A is the normative-adjacent
> ledger of exactly how it relates to CEL: every expression valid here is a valid
> CEL expression (subset), and with three flagged exceptions every pinned
> behaviour is one CEL itself permits (refinement). Throughout: "DETERMINISTIC
> CEL" is this format's proper name; bare "CEL" continues to mean the upstream
> Common Expression Language. *(Spelling confirmed by Peter, 2026-07-20: the
> "CELL" form in the original proposal was a typo; the name is DETERMINISTIC
> CEL.)*

**Status:** Draft (T1 deliverable of `SOW-CEL-Filter-Format.md`); board-reviewed 2026-07-20, fix list applied this revision — see editorial note below. **Ratified by Peter 2026-07-20:** the DETERMINISTIC CEL name (title note), the closed platform function registry (§7), and §7.5 option (a) — correctly-rounded transcendentals. Remaining open items are routed to B1/T2/T5/T6/T7 (§15).
**v0.2-DRAFT (2026-11-14) adds bounded local bindings** (new §5.5; the `cel.bind` text form in §5.3 and §5.3.4; the `REF` node in §11.1; the binding list on the wire, §11.3 item 7 and Appendix B). Direction ruled RL-JGDMS-0022 (bindings yes; `maxExprNodes` stays 1,024); text syntax ruled RL-JGDMS-0023 (option (b): `cel.bind` on the outer spine only). **The v0.2 text is NOT ratified and has NOT been re-reviewed.** It was edited from `STD011_v0.2_LET_DELTA.md` (Chisel, claude-opus-5-5; sections A-G revision 0 and R1-R26 revision 1, where R supersedes A-G), and every changed passage ends with a provenance tag `(v0.2: let delta ...)` naming the delta item; the tags are review scaffolding. The file name keeps `v0.1` so that existing links hold. Where the delta was ambiguous the text carries an `[[EDITOR-Q n: ...]]` marker instead of a decision.
**Version:** 0.2-DRAFT
**Date:** 2026-07-20 (v0.1); 2026-11-14 (v0.2 edit)
**Author:** Peter Firmstone + Claude
**Applies to:** JGDMS 4.0.0+, DirtyChai (JDK fork), and non-JVM JGDMS participants (Rust evaluator, T4)
**Depends on:** JGDMS-STD-006 (ATOMIC DER Wire Format — the type substrate, §3.12, and the profile ceilings, §4.5), JGDMS-STD-009 (Service & Remote-Function Annotation Model — §8's 2026-07-17/2026-07-20 decision notes, the consumer-facing contract)
**Related:** `SOW-CEL-Filter-Format.md` (task breakdown; this document is T1), `SOW-Entry-ATOMIC-DER-Migration.md` (candidate-data substrate adoption), `DESIGN-CorroborationFramework.md` (the confirmed Survey-zoot transform use case), `JGDMS-Board-Reviewer-Guidance.md` (review standard this document is written against)

---

> **Editorial note (v0.1-DRAFT).** This standard defines the expression language that
> realises STD-009 §8's ratified decision (Peter, 2026-07-20): filters and value
> transforms are **not bytecode** — they are expressions in a small, closed,
> non-Turing-complete language modeled on CEL (Common Expression Language), evaluated
> by independently written, spec-conformant evaluators in Java (T3) and Rust (T4)
> over the ATOMIC DER wire encoding (T2). This document is the **sole normative
> definition** of the language: T2 (wire encoding), T3/T4 (evaluators), T5
> (conformance suite), and T6 (verification gate) implement exactly what is written
> here and nothing else. Per board guidance G9, the test of this document is that a
> peer can implement a conformant evaluator **from this document alone**, without
> access to any JGDMS source.
>
> RFC 2119 keywords (MUST / MUST NOT / SHOULD / MAY) are used throughout and are
> normative. Sections and individual rules marked **[PROPOSED]** are recommended
> designs requiring Peter's ratification; **[OPEN]** marks questions deliberately
> not resolved here, each with its owning task or SOW named. Everything not so
> marked is normative as written (subject to the board review this draft is queued
> for).
>
> **Relationship to CEL.** The language borrows CEL's grammar shape, operator set,
> and error-absorption design, because CEL's bounded-evaluation properties are
> field-proven (Kubernetes admission control, Envoy). It is **not** CEL: it is a
> strict subset with pinned semantics where CEL leaves latitude, plus a closed
> platform function set CEL does not have. Divergences are individually flagged
> where they occur and collected in Appendix A. `cel-java`/`cel-rust` are **not**
> dependencies and MUST NOT be consulted as behavioural oracles — this document is
> the oracle.
>
> **Board review record.** Board-reviewed 2026-07-20 (two adversarial seats:
> boundedness/security, cross-language determinism; both SOUND-WITH-FIXES;
> synthesis applied the consolidated 10-item fix list — this revision).
> Substantive changes from the pre-review draft: §3.1's asymptotic cost claim
> corrected; §3.3/§11.3 now condition Claim 3 on an enforced `maxScalarBytes`;
> a new `maxSelectorSteps` ceiling bounds `FIELD_REF` selector chains
> (§10.3/§11.1/§11.3); §10.6 pins the cost-arithmetic width; native-op
> implementation traps are documented at their definition sites (§4.3.1,
> §4.3.4, §7.2); Appendix A (the CEL-divergence ledger) is written; and several
> cross-references/annotations are harmonized (§12.2, §7.2/§11.3, §10.3's
> fencepost convention, §5.2's −2⁶³ fold, §13's conformance corpus).

---

## 1. Purpose and Scope

### 1.1 What this defines

A single expression language serving two evaluation contexts:

- **Predicate context** (the "filter" of STD-009 §8): a boolean expression evaluated
  against a candidate (a Reggie service item / attribute set, an Outrigger entry, an
  F0-tier observation) to produce a match / no-match verdict.
- **Transform context** (the "computed" case of STD-009 §11, scoped by
  `DESIGN-CorroborationFramework.md`'s confirmed Survey-zoot application): a
  value-returning expression computing a bounded, pure, fixed-formula result from a
  candidate's fields — e.g. raw bearing/elevation/distance → local vector components
  at a survey instrument.

Both contexts use the same grammar, type system, evaluation semantics, and cost
model. The only differences are the required result type (predicate: `bool`) and the
disposition of errors (§9.4, §9.5).

### 1.2 What this deliberately is not

- **Not a scripting or query language.** There are no loops, no comprehensions, no
  user-defined functions, no assignment, no rebinding, no parameters, no nested
  scopes, no state (§3, §5.4). The only named values are the flat, backward-only
  bindings of §5.5. *(v0.2: let delta A.1)*
- **Not CEL-with-extensions.** CEL's macros (`all`, `exists`, `exists_one`, `map`,
  `filter`) and `matches()` are **excluded** (§5.4) — they are precisely the parts of
  CEL whose cost is not linear in expression size.
- **Not a smart proxy.** Per `SOW-Smart-Proxy-Isolation-Architecture-Overview.md`
  §1a, anything needing state, I/O, or iteration is outside this format by
  construction, not by policy.
- **Not the wire encoding** (T2 extends STD-006 with the expression-node tags; §11.3
  states the constraints T2 MUST honour), **not the evaluators** (T3/T4), **not the
  candidate-marshalling migration** (`SOW-Entry-ATOMIC-DER-Migration.md`).

### 1.3 Security context

This language is the sole safety boundary around adversary-authored input evaluated
inside two production runtimes. Its safety claim is **bounded-by-construction**
(§3): no well-formed expression can fail to terminate, perform a side effect, or
consume resources beyond a statically computable bound. That claim is stated as a
theorem with an argument (§3.2), not an aspiration, and every construct admitted by
the grammar is covered by that argument. A construct not covered by §3.2 MUST NOT
be added to this language by any future revision without extending the theorem.

---

## 2. Definitions

| Term | Meaning |
|---|---|
| **Expression** | A well-formed term of the grammar in §5, equivalently an AST per §11. |
| **Candidate** | The data object a single evaluation runs against: an ATOMIC DER `MarshalledInstanceRecord` (STD-006 §7.8) carrying `schemaBytes` + `payloadBytes`. |
| **Projection** | The class-free field map decoded from a candidate by the fail-closed STD-006 codec: `className → (fieldName → typed value)`, per STD-009 §8's 2026-07-20 verification note (`ObjectCodec.decodeToFieldMap`). The only data an evaluation can observe. |
| **Namespace** | One `@AtomicSerial` class's private field SEQUENCE within a candidate's schema chain (STD-006 §3.9). Field names are scoped per namespace; the same name in two namespaces denotes two unrelated fields. |
| **Field designator** | The restricted syntactic form naming a field: a root reference plus selector chain (§5.3.3). The only form admissible as the argument of `has()`. |
| **Expression type** | One of the types in §4.1 (`bool`, `int`, `double`, `string`, `bytes`, `null_t`, `list<T>`, `object`). |
| **Error value** | One of the eight members of the closed error set (§9.2). Evaluation is total: it yields a typed value or an error value, never anything else. |
| **Predicate context / Transform context** | The two consumer-declared evaluation contexts (§1.1, §9.4, §9.5). |
| **Platform function** | A member of the closed, platform-provided function registry (§7). Never user-supplied, never wire-extensible. |
| **Well-formed** | Passing every check in §12.1–§12.3 (structural well-formedness + allowlist + ceilings). Evaluation semantics are defined only for well-formed expressions. |
| **Verified** | Well-formed and, where a registration-time schema is available, statically typed against it (§12.4). |
| **Ceilings** | The profile constants bounding expression and data size: STD-006 §4.5's table plus this standard's `maxExprNodes`, `maxExprDepth`, `maxSelectorSteps`, `maxScalarBytes` (§10.3). |
| **Correctly rounded** | Producing, for given IEEE-754 binary64 argument(s), the binary64 value nearest the exact mathematical result (ties to even). |

---

## 3. Security Thesis — Bounded by Construction (NORMATIVE)

### 3.1 The claims

For every well-formed expression `E` and every candidate projection `P`:

1. **Termination.** Evaluation of `E` over `P` terminates.
2. **Side-effect freedom.** Evaluation observes only `E`, `P`, and the pinned
   constants of this standard, and affects nothing. Two evaluations of the same
   `E` over the same `P` yield the identical result (value or error), on any
   conformant implementation, in any language, on any hardware.
3. **Bounded cost.** Evaluation by a conformant evaluator (§6.9, resource rule 1)
   completes within `C(E)` abstract cost units, where
   `C(E)` is computed from `E` and the profile ceilings **alone** (no candidate
   needed), by the algorithm in §10. The normative bound is exactly what §10
   enforces: the statically computable `C(E) ≤ maxExprCost` gate (§10.5) and, for
   an expression with bindings, the second gate `xCost(M) ≤ maxExpandedCost`
   (§10.3.1, §10.5) — not
   an asymptotic characterization. (An earlier draft of this claim stated
   `C(E) ∈ O(|E| × S)`; that is false as written, because the `in`-over-list-
   field term additionally scales with `maxCollection` independent of `|E|`,
   §10.2/§10.4 — corrected here rather than left standing.) The second gate is
   the bound for a defective evaluator that re-evaluates a body at each use
   (§10.5). *(v0.2: let delta C.3, R5)*
4. **Bounded allocation.** Evaluation allocates memory bounded by
   `O(maxEffectiveDepth + |E| × S + maxBindings)` — proportional to expression size and input scalar
   sizes only, never to any adversary-scalable quantity beyond them. *(v0.2: let delta R20)*

These are the properties the SOW names "bounded-by-construction, not
bounded-by-runtime-gate": no watchdog, fuel meter, or timeout is required for the
properties to hold (an implementation MAY add one as defense in depth, §10.5, but
correctness never depends on it).

### 3.2 Theorem and argument

> **Theorem.** Claims 1–4 of §3.1 hold for every expression accepted by the
> well-formedness checks of §12.1–§12.3.

**Argument.** By structural induction over the closed AST node inventory of §11.1,
plus a closed-world premise:

*(i) The AST is a sequence of `n + 1` finite trees of bounded size* (the `n`
bindings, `0 ≤ n ≤ maxBindings`, and the main body; §5.5). T2's wire form is
acyclic and definite-length by STD-006 construction; §12.3 caps the node count,
summed over all trees, at `maxExprNodes` and each tree's depth at `maxExprDepth`.
A `REF` in tree `k` names a tree `j < k`, validated before tree `k` is accepted,
so the reference graph is acyclic and every index resolves to a completed,
immutable tree. The encoding has no other sharing, back-reference or cycle
(STD-006 §3.7 applies to the expression encoding as to every ATOMIC DER value,
with the binding list as its second permitted exception, argued in STD-006 §3.7
as amended for v0.2). *(v0.2: let delta C.4 (i), B.2)*

*(ii) Evaluation is structural recursion that visits each node at most once.*
The semantics in §6–§9 define each node's result purely as a function of its
children's results and `P`. Exhaustively, per node type (§11.1): a literal
evaluates no children; unary operators evaluate their one child once; binary
operators evaluate each child at most once (`&&`/`||` and `in` may skip a child;
none evaluates a child twice); the conditional evaluates its condition once and at
most one branch once; a function call evaluates each argument once; `has()` and
field references perform lookups, evaluating nothing; a `REF` evaluates no child
of its own, it yields the stored outcome of its body. **No node type's semantics
re-enters any node.** Under resource rule 1 (§6.9) each of the `n + 1` trees is
evaluated at most once per evaluation and a later `REF` reads the stored
outcome. Hence total node visits ≤ `|E|` (claim 1). The evaluation stack never
exceeds the effective depth (§10.3.1), whichever strategy is used: a lazy
walker's nesting, counted in node visits, equals the effective depth exactly
(a `REF` counts as one level), and an eager evaluator's is at most
`maxExprDepth`; so stack depth ≤ `maxEffectiveDepth` (the stack part of claim 4).
Without rule 1, node visits are bounded by the expanded node count, which
`maxExpandedCost` bounds because every node VISIT costs at least 1.
*(v0.2: let delta C.4 (ii), R1, R20)*

*(iii) Per-node work is bounded independently of anything adversary-scalable.*
Excluding child evaluation: scalar arithmetic, comparisons, boolean operators, and
conversions are O(1) fixed-width (64-bit) operations. String/bytes equality,
ordering, `startsWith`/`endsWith`, and `size` are linear in operand sizes;
`contains` is at worst `|haystack| × |needle|` with the needle restricted to a
literal (§6.6), so the needle's size is part of `|E|`; `in` is linear in the list
size times per-element comparison. Every one of these operand sizes is capped: list
sizes by `maxCollection` (STD-006 §4.5), scalar sizes by `maxScalarBytes` (§10.3),
literal sizes by `|E|`. Every platform function (§7) is a finite, loop-free-in-the-
adversary-controlled-dimension numeric procedure on fixed-width binary64 values
(argument reduction in a correctly-rounded `sin` iterates over the fixed 64-bit
exponent range, a constant, never over input length). A `REF`'s own work is one
slot read; its body's work is charged once, under rule 1 (§6.9). Summing the
per-node bounds gives `C(E)` (§10) — claim 3. *(v0.2: let delta R20)*

*(iv) Allocation.* The only allocating constructs are list-literal construction
(≤ `|E|` slots), boxing of scalar results (O(1) each, ≤ one per node visit), and
one outcome slot per binding (≤ `maxBindings`).
No node concatenates, copies, or builds strings/bytes/collections: `+` is numeric-
only (§6.3; string/list/bytes concatenation is excluded, a deliberate divergence
from CEL). The projection `P` is decoded once by the STD-006 codec under its own
ceilings, before evaluation, and is read-only during it. Claim 4.

*(v) Purity.* No node type has any semantics other than producing a value or
error: there is no assignment (no syntax for it), no mutation (the projection is
immutable; no node writes), no I/O (no node performs any), no clock, randomness,
or environment access (no such function is in the registry, and §7.1 requires
registry functions to be pure and deterministic). A binding's outcome slot is
written once per evaluation with its body's outcome and is invisible outside that
evaluation (§9.6); by claim 2 the write cannot be observed, so purity holds.
Claim 2's determinism
additionally requires the numeric semantics of §4.3 and §7 to be exact or
correctly rounded, which they are by construction (§4.3) or by mandate (§7.5).
*(v0.2: let delta C.4 (v))*

*(vi) Closed world.* The induction is exhaustive because the construct set is
closed at three independent layers: the grammar (§5) generates only the node
inventory of §11.1; T2 MUST reject any wire node type or function identifier
outside this standard (§11.3); §12.2 re-checks the function allowlist at
verification. There are **no loops, no comprehensions or macros, no recursion, no
user-defined or wire-supplied functions, no assignment, no parameters, no nested
scopes, no rebinding** — not as
banned features but as absent productions: the grammar has no syntax and the AST
no node type through which they could be expressed. The closed world gains exactly
one leaf kind, `REF`, which takes no operand and no parameter. A malformed encoding cannot
smuggle them in, because an unknown node type is a hard decode reject (§11.3),
never a skip or a default. ∎ *(v0.2: let delta C.4 (vi))*

**Precedent (informative).** This is the same boundedness design Kubernetes relies
on for CEL in admission control: cost is estimated statically from the expression
and schema bounds and enforced before admission, precisely because the language has
no construct whose cost escapes that estimate. This standard adopts the same shape
with a smaller language and pinned (rather than implementation-defined) semantics.

### 3.3 What the theorem does not claim (informative, honesty note)

- It does not claim **Claim 3 (bounded cost) unconditionally**. Claim 3 depends
  on a per-scalar length ceiling (`maxScalarBytes`, §10.3) being pinned — in
  STD-006 §4.5, or failing that, directly by T2 — **and enforced at
  wire-decode time on both sides**: candidate projected scalar fields (already
  the projection codec's job, §8.1) **and** expression literals
  (`LIT_STRING`/`LIT_BYTES`, §11.3). STD-006 §4.5 currently has no
  per-string/bytes ceiling (§10.3's footnote). Until such a ceiling is pinned
  and enforced on both sides, `S` in §10.2's cost table is an assumed
  constant, not an enforced one, and Claim 3 does not hold.
- It does not claim resistance to **timing side channels**: evaluation time may
  depend on candidate values. Whether co-residency of evaluation with other tenants
  needs process/core isolation is T7's question (`SOW-CEL-Filter-Format.md` §5.1),
  cross-referenced to `SOW-BAE-Timing-Sidechannel-Denial.md` — construction-safety
  and side-channel-safety are different properties.
- It does not claim the *projection decode* is free: the STD-006 codec's own
  ceilings and fail-closed rules (STD-009 §8, 2026-07-20 note) bound that stage;
  this standard composes with them (§8.1) and MUST NOT be evaluated over any other
  reader.
- It does not claim confidentiality of candidate data (STD-009 §8's honesty note:
  schema-bearing DER is readable by whoever holds the bytes).

---

## 4. Type System (NORMATIVE)

### 4.1 Expression types

The expression language has exactly eight types:

| Type | Values |
|---|---|
| `bool` | `true`, `false` |
| `int` | two's-complement signed 64-bit integers: −2⁶³ … 2⁶³−1 |
| `double` | IEEE-754 binary64, including −0.0, ±Inf, NaN |
| `string` | finite sequences of Unicode scalar values (code points excluding surrogates) |
| `bytes` | finite sequences of 8-bit octets |
| `null_t` | the single value `null` |
| `list<T>` | homogeneous finite sequences with element type `T` (a scalar expression type) |
| `object` | an opaque nested field map (a nested `@AtomicSerial` object); supports only field selection and `has()` (§8.4) |

There is **no** unsigned integer type (the wire has none — divergence from CEL's
`uint`, Appendix A), no timestamp/duration types (the wire carries epoch values as
`long`; compare as `int`), and no type type. Errors (§9.2) are not values of any
type; they are the second arm of the total evaluation result.

### 4.2 Widening-on-load: mapping the STD-006 §3.12 wire scalars

The candidate's wire values are the STD-006 §3.12 closed scalar set. On projection
load, each wire scalar is widened to exactly one expression type. Widening is
**exact** (value-preserving) in every case; the expression type system stays small
so operator semantics stay small.

| Wire scalar (STD-006 §3.12) | Expression type | Widening rule |
|---|---|---|
| `boolean` | `bool` | identity |
| `byte` | `int` | sign-extend (−128…127) |
| `short` | `int` | sign-extend |
| `int` | `int` | sign-extend |
| `long` | `int` | identity (both are int64) |
| `float` | `double` | IEEE binary32 → binary64 (exact: every binary32 value, including −0.0/±Inf/NaN, has an exact binary64 image; NaN widens to the canonical quiet NaN, §4.3.4) |
| `double` | `double` | identity |
| `char` | `string` | the one-scalar-value string of that code point (STD-006 pins wire `char` to a BMP non-surrogate code point, so this is total) |
| `java.lang.String` | `string` | UTF-8 decode per STD-006 §4.2 (the codec has already rejected non-canonical/ill-formed UTF-8; §8.1) |
| `byte[]` | `bytes` | identity |
| `Collection(E)` — `set:`/`bag:`/`orderedset:`/`list:` | `list<widen(E)>` | element-wise widening, in wire order (for canonicalise disciplines, the canonical DER order **is** the wire order — deterministic by STD-006 G1) |
| nested `@AtomicSerial` object | `object` | the nested field map, recursively subject to this table |
| wire `null` (a present field with null value) | `null_t` | identity |

**[OPEN → integration SOWs]** Wire `map:`/`orderedmap:` fields and `Any`-typed
fields have **no expression-level operations in v0.1**: a projection MAY carry
them, but any expression reference to such a field other than `has()` (which
reports presence) is `TYPE_MISMATCH`. Map indexing and `Any` inspection are
possible future additions (§15) and MUST NOT be improvised by an implementation.

**Rationale (informative).** Widening `char` to `string` rather than `int` keeps
character data textual (equality against one-character string literals, ordering
consistent with string ordering) and avoids a third numeric kind. Widening `float`
to `double` and all integrals to `int` follows CEL's int64/double model and makes
the operator tables in §6 total over two numeric types instead of eight.

### 4.3 Numeric semantics

#### 4.3.1 `int` arithmetic is checked; overflow is an error

Every `int` operation (`+ - * /`, unary `-`, `%`, `abs`) MUST detect overflow and
yield the error `OVERFLOW` (§9.2) instead of wrapping. Specifically, the following
MUST yield `OVERFLOW`: any `+`/`-`/`*` whose exact result lies outside int64
range; unary `-` and `abs` applied to −2⁶³; `(−2⁶³) / (−1)`; and `(−2⁶³) % (−1)`.

> The last case is pinned deliberately: mathematically `(−2⁶³) % (−1)` is 0, and a
> JVM's `%` returns 0, but Rust's checked remainder rejects it; pinning it as
> `OVERFLOW` costs one special case in the Java evaluator and keeps the two
> implementations trivially identical. (Aligned with CEL: "arithmetic operations
> raise an error when the results exceed the range of the integer type.")

**Implementation trap (normative warning): unchecked `abs`/negate at the
minimum.** `Math.abs(Long.MIN_VALUE)` and unary negation of `i64::MIN` in Rust
do **not** throw or panic by default — they silently return `Long.MIN_VALUE`/
`i64::MIN` unchanged (two's-complement wraparound). A conformant evaluator
MUST use a checked path (`Math.absExact`/`Math.negateExact` on the JVM,
`checked_abs`/`checked_neg` in Rust) or an explicit equality test against the
minimum value — never plain `Math.abs`/unary `-`/`wrapping_neg` — to raise
`OVERFLOW` per this section.

Integer division truncates toward zero. `%` is the truncated-division remainder:
`a % b == a − (a / b) * b`, so the result's sign follows the **dividend** and
`|a % b| < |b|`. (Java `%` and Rust `%` both already implement exactly this.)
`x / 0` and `x % 0` on `int` yield `DIVISION_BY_ZERO`.

**Implementation trap (normative warning): unchecked `/`/`%` at `−2⁶³ ÷ −1`.**
Java's raw `/` and `%` operators do **not** throw on `(−2⁶³) / (−1)` — they
silently produce `Long.MIN_VALUE` (an overflow gone unnoticed); Rust's raw
`/`/`%` panic in debug builds only, and wrap in release builds. Neither raw
operator may be used unaudited: the JVM evaluator MUST use `Math.divideExact`
or an explicit `−1`-divisor check to raise `OVERFLOW` per this section (the
`%` case is pinned above in the same way).

#### 4.3.2 `double` arithmetic is IEEE-total; operators never error

`+ - * /` and unary `-` on `double` are IEEE-754 binary64 operations,
round-to-nearest-even, and are **total**: `x / 0.0` yields ±Inf, `0.0 / 0.0`
yields NaN, operations involving NaN yield NaN, per IEEE-754. These four
operations plus `sqrt` and int↔double conversion are correctly rounded **by
IEEE-754 itself** on all mainstream hardware and languages — they are bit-exact
across conformant implementations with no further mandate needed.

`%` is **not defined** for `double` (aligned with CEL, which restricts `%` to
integers): `double % double` is `TYPE_MISMATCH`.

There is **no implicit conversion in arithmetic**: `int + double` is
`TYPE_MISMATCH` (statically rejected where types are known, §12.4; dynamically
otherwise). Use the explicit conversions `double(x)` / `int(x)` (§7.3).

#### 4.3.3 Cross-type numeric comparison is exact

The comparison operators (`< <= > >= == !=`) ARE defined between `int` and
`double` operands (in either order), and MUST compare the two operands **as exact
mathematical values** — never by first converting the int64 to binary64 (lossy
above 2⁵³) or the binary64 to int64. NaN compares unordered: every ordered
comparison involving NaN is `false`, `==` is `false`, `!=` is `true` (§4.3.4).

*Implementation note (informative, but the property is normative).* One exact
method: if the double is NaN apply the NaN rules; else if it is ≥ 2⁶³ the int is
smaller; else if it is < −2⁶³ the int is larger; otherwise the double's integral
and fractional parts are exactly representable and comparable against the int64
in integer arithmetic. T5's corpus includes the killer cases
(e.g. `9007199254740993 > 9007199254740992.0` MUST be `true`;
`9223372036854775807 < 9223372036854775808.0` MUST be `true`).

This adopts CEL's modern continuous-number-line comparison semantics while pinning
exactness, which CEL leaves to implementations.

#### 4.3.4 NaN, signed zero, and canonicalization

- All ordered comparisons (`< <= > >=`) in which either operand is NaN yield
  `false`. `NaN == x` is `false` for every `x` including NaN; `NaN != x` is `true`.
  (CEL specifies the equality half; this standard pins the ordering half, which CEL
  leaves implementation-defined — divergence-by-pinning, Appendix A.)
- `-0.0 == 0.0` is `true`; `-0.0 < 0.0` is `false` (IEEE equality). Sign of zero is
  nonetheless **preserved** through arithmetic and function results, per IEEE.
- **Implementation trap (normative warning): `Double.compare`.** Java's
  `Double.compare(double, double)` (and Rust's `f64::total_cmp`) imposes a
  **total order** that gets both rules above wrong: `Double.compare(-0.0, 0.0)
  < 0` is `true`, where this standard requires `-0.0 < 0.0` to be `false`; and
  it orders NaN relative to every other value, where this standard requires
  every ordered comparison involving NaN to be `false`. Verified by execution
  on OpenJDK 25. A conformant evaluator MUST NOT use `Double.compare` (or an
  equivalent total-order comparator) unaudited for `<`/`<=`/`>`/`>=`; it MUST
  implement the IEEE comparison predicates directly, or wrap the total order
  with explicit NaN and signed-zero corrections.
- **NaN payloads are never observable** inside evaluation (no bit-inspection
  operation exists). At every **boundary** where a `double` leaves the evaluator —
  a transform result being DER-encoded, a conformance-suite observation — a NaN
  MUST be canonicalized to the quiet NaN with bit pattern
  `0x7FF8000000000000`. Non-NaN values (including −0.0 and ±Inf) MUST be emitted
  with their exact bit pattern. This composes with STD-006's strict-canonical
  IEEE-754 encoding: one value, one encoding (G1).

### 4.4 String semantics

- A `string` is a sequence of Unicode scalar values. The wire form is UTF-8
  (STD-006 §4.2); the codec's canonical-form rejection guarantees well-formedness
  before evaluation begins.
- **Equality** is code-point-sequence equality. **No Unicode normalization is
  applied, ever** — `"é"` as U+00E9 and as U+0065 U+0301 are unequal. (Rationale:
  byte-level determinism, G1; normalization tables are version-dependent and would
  make the match verdict depend on a Unicode version.)
- **Ordering** (`< <= > >=`) is lexicographic by code point. This is byte-identical
  to lexicographic ordering of the UTF-8 encodings (a theorem of UTF-8's design),
  so a wire-level implementation may compare the UTF-8 bytes directly.
  **Implementation trap (normative warning):** Java's `String.compareTo` orders by
  UTF-16 code *unit* and disagrees with code-point order for supplementary
  characters (e.g. U+FFFF vs U+10000). A JVM evaluator MUST compare by code point
  (or by UTF-8 bytes), MUST NOT use `String.compareTo` unaudited. T5's corpus MUST
  include a supplementary-plane ordering case to catch exactly this.
- `size(s)` is the number of code points (not bytes, not UTF-16 units).
- String functions: `contains` / `startsWith` / `endsWith` (§6.6). There is **no**
  concatenation, no case conversion, no trimming, no regex in v0.1 (§5.4).

### 4.5 Bytes semantics

`bytes` values support `==`/`!=` (octet-sequence equality), ordering
(lexicographic by **unsigned** octet value — a JVM implementation must mask Java's
signed `byte`), and `size(b)` (octet count). Nothing else in v0.1. String and
bytes values are never comparable to each other (`TYPE_MISMATCH`); there is no
implicit UTF-8 bridging.

### 4.6 `null` and absence are distinct

Three situations MUST be kept distinct (worked examples §14.5):

1. **Present, non-null:** the candidate's schema declares the field and the decoded
   value is non-null. A reference yields the value; `has()` is `true`.
2. **Present, null:** the schema declares the field and the value is wire-null. A
   reference yields `null`; `has()` is `false` (null is treated as "not usefully
   present", following CEL's proto3 `has()` shape rather than its map
   key-presence shape, which would return `true` here — a divergence, Appendix A
   row 11; a guard `has(f) && f > 5`
   protects against both null and absence with one test).
3. **Absent:** no namespace in the candidate's schema chain declares the field. A
   reference yields the error `ABSENT_FIELD`; `has()` is `false`.

Operations on `null`: only `==` and `!=` are defined. `null == null` is `true`;
`null` compared with any non-null value under `==` is `false` (under `!=`,
`true`) — this cross-type equality is the **single** exception to §6.1's same-type
rule, so that `f == null` never errors on a present field of any type. Every
other operation with a `null` operand is `TYPE_MISMATCH`.

### 4.7 Collections

A `list<T>` value (a projected wire collection, §4.2, or a list literal, §5.3.2)
supports: `in` (membership, §6.5), `size` (§7.3), and — as a transform result — 
delivery as a whole. **No indexing, no equality between lists, no iteration** in
v0.1 (there is no construct that visits elements except `in`'s bounded scan). A
list's elements are scalars; a wire collection whose element type is itself a
collection, `object`, or `Any` may be projected but supports only `has()`
(reference beyond presence is `TYPE_MISMATCH` in v0.1).

---

## 5. Grammar (NORMATIVE)

### 5.1 Two concrete forms, one abstract syntax

The **canonical interchange form** of an expression is the T2 DER-encoded AST
(§11); that is what crosses the wire, what is verified (§12), and what is
evaluated. The **textual form** defined here is the authoring/display syntax.
Both denote exactly the abstract syntax of §11.1; a conformant toolchain MUST
parse the textual form to the same AST this section specifies, and text that does
not parse MUST be rejected (never partially accepted). Evaluators are not required
to include a text parser; wire-form consumption alone is conformant.

**Developer syntax is CEL syntax, one-directionally (informative but designed).**
The textual form is deliberately a subset of CEL's own concrete syntax: every
well-formed DETERMINISTIC CEL expression is character-for-character parseable by
a CEL parser (the platform functions and the `field(...)` qualified accessor ride
CEL's ordinary call syntax, as CEL extension functions would), so CEL editor
tooling, syntax highlighting, documentation, and developer familiarity carry over
unchanged. The converse does not hold: text a CEL developer might legally write
can be **rejected** here — the §5.4 exclusions (macros, `matches()`, `[]`
indexing, map literals, concatenating `+`, `u` suffixes, raw strings), chained
relations (a parse error here, a type error in CEL), and the §5.3.1 constraints
(literal-only `contains` needle, `field(...)` literal arguments). Every such
rejection is loud, at parse or verification time — never a silently different
runtime meaning. There is exactly **one** syntax; "DETERMINISTIC CEL syntax"
never means new notation, only fewer accepted sentences and pinned semantics
(Appendix A).

### 5.2 Lexical structure

```ebnf
WHITESPACE  ::= ( " " | TAB | CR | LF )+                    (* insignificant between tokens *)
COMMENT     ::= "//" (any character except CR/LF)* (CR|LF)  (* authoring form only; never on the wire *)

IDENT       ::= ( "_" | LETTER ) ( "_" | LETTER | DIGIT )*  (* LETTER = A-Z a-z; DIGIT = 0-9 *)

INT_LIT     ::= DIGIT+ | "0x" HEXDIGIT+
DOUBLE_LIT  ::= DIGIT+ "." DIGIT+ [ EXPONENT ] | DIGIT+ EXPONENT
EXPONENT    ::= ("e"|"E") ["+"|"-"] DIGIT+
STRING_LIT  ::= '"' ( CHAR_NO_DQUOTE | ESCAPE )* '"'
              | "'" ( CHAR_NO_SQUOTE | ESCAPE )* "'"
ESCAPE      ::= "\\" | "\"" | "\'" | "\n" | "\r" | "\t"
              | "\u" HEXDIGIT HEXDIGIT HEXDIGIT HEXDIGIT          (* BMP non-surrogate *)
              | "\U" HEXDIGIT{8}                                  (* any Unicode scalar value *)
BYTES_LIT   ::= "b" '"' ( PRINTABLE_ASCII_NO_DQUOTE_NO_BACKSLASH
              | "\\" | "\"" | "\x" HEXDIGIT HEXDIGIT )* '"'
```

Rules:

- **Reserved words**, usable only as shown in the grammar, never as bare field
  identifiers: `true false null in has field` plus — reserved for future
  compatibility with CEL, currently unused — `as break const continue else for
  function if import let loop package namespace return var void while`. A candidate
  field whose wire name collides with a reserved word (or is not an `IDENT`) is
  still reachable via the qualified form `field(...)` (§8.3), whose field name is a
  string literal.
- Integer literals are unsigned at the lexical level. A literal's value MUST lie in
  `[0, 2⁶³]`; the value `2⁶³` (`9223372036854775808`) is well-formed **only** as
  the immediate operand of unary `-` (the pair folds to −2⁶³); any other
  out-of-range literal is a well-formedness error (§12.1). Hex literals follow the
  same range rule. This fold happens at **text-parse time**, before AST
  construction: `NEG(LIT_INT(2⁶³))` is unrepresentable in the AST and on the
  wire, because `LIT_INT`'s value domain is exactly int64 (§4.1) — the fold is
  how the textual surface reaches −2⁶³ at all, not a special AST or wire form.
- A `DOUBLE_LIT` denotes the IEEE binary64 value **nearest** its decimal reading
  (ties to even). (Both `Double.parseDouble` and Rust's `str::parse::<f64>` are
  correctly rounded; the T2 wire form carries the 64-bit pattern, so this rule
  binds authoring tools, not evaluators.)
- A `\u` escape MUST NOT denote a surrogate code point; `\U` MUST denote a valid
  Unicode scalar value (≤ U+10FFFF, non-surrogate). Violations are lexical errors.
- There are **no** raw strings, no `\x` escape inside string literals (code points
  only; `\x` is bytes-literal-only), no triple quotes, no `uint` suffix.

### 5.3 Syntax

```ebnf
Expr            ::= ConditionalOr [ "?" ConditionalOr ":" Expr ]
ConditionalOr   ::= ConditionalAnd { "||" ConditionalAnd }
ConditionalAnd  ::= Relation { "&&" Relation }
Relation        ::= Addition [ RelOp Addition ]
RelOp           ::= "<" | "<=" | ">" | ">=" | "==" | "!=" | "in"
Addition        ::= Multiplication { ( "+" | "-" ) Multiplication }
Multiplication  ::= Unary { ( "*" | "/" | "%" ) Unary }
Unary           ::= "!" Unary | "-" Unary | Postfix
Postfix         ::= Primary { "." IDENT [ "(" [ ExprList ] ")" ] }
Primary         ::= Literal
                  | ListLiteral
                  | IDENT [ "(" [ ExprList ] ")" ]
                  | "(" Expr ")"
ListLiteral     ::= "[" ExprList "]"                      (* non-empty; see §5.3.2 *)
ExprList        ::= Expr { "," Expr }
Literal         ::= INT_LIT | DOUBLE_LIT | STRING_LIT | BYTES_LIT
                  | "true" | "false" | "null"
```

Precedence (highest first): postfix selection/call; unary `!` `-`; `* / %`;
`+ -`; relations/`in`; `&&`; `||`; `?:`. `&&` and `||` associate left; `?:`
associates right, exactly as the productions state.

**Relations do not chain.** `a < b < c` is a **syntax error** by the grammar
above (`Relation` admits at most one `RelOp`) — the mistake is unrepresentable
rather than merely ill-typed (divergence from CEL, which parses it and fails
type-checking; per G7, construction beats vigilance).

**Start symbol and bindings (v0.2).** The start symbol is `Program`; `Expr` above is
unchanged, and a text with no `cel.bind` is an `Expr` and denotes a v0.1 expression.

```ebnf
Program         ::= BindCall | Expr
BindCall        ::= "cel" "." "bind" "(" IDENT "," Expr "," Program ")"
```

`BindCall` is CEL's `cel.bind(name, init, result)` restricted to the **outer spine**:
the `result` position holds the next `BindCall` or the main body, and `init` is a
plain `Expr`, so a `Program` has the shape `cel.bind(a, e1, cel.bind(b, e2, M))`.
A `Program` of `n` nested `BindCall`s denotes the `n` bindings `B_0 … B_(n-1)`, in
source order, and the main body `M` (§5.5). A text that begins with the tokens
`cel` `.` `bind` `(` is a `BindCall`. The `Postfix` production would also derive
that text (a field reference `cel` followed by a method call `bind`), and §5.3.1
item 1 rejects that derivation, so the two derivations never both succeed. Names,
scope and the parse-time rejections are §5.3.4. *(v0.2: let delta R14, A.2)*

#### 5.3.1 Post-parse well-formedness constraints on the syntax

The grammar above over-generates; the following constraints are part of
well-formedness (§12.1) and MUST be enforced by parsers and by T6 on the wire
form alike:

1. A `Primary` of shape `IDENT "(" … ")"` (a global call) is well-formed only if
   `IDENT` is one of: `has`, `field`, `size`, or a global platform function name
   (§7.2). A `Postfix` call `. IDENT ( … )` (a method call) is well-formed only if
   `IDENT` is one of: `contains`, `startsWith`, `endsWith`, `field`. Arities per
   §6/§7. Any other call target is a well-formedness error — there is no
   user-function call form at all. `cel.bind(…)` is well-formed only as the
   `BindCall` of the start symbol (§5.3); anywhere else it is a method call `bind`
   on a field reference `cel`, which the previous sentence rejects.
   *(v0.2: let delta A.2)*
2. The argument of `has(...)` MUST be a field designator (§5.3.3).
3. Both arguments of every `field(...)` form (global or method) MUST be string
   literals (§8.3) — never computed.
4. The needle (argument) of `.contains(...)` MUST be a string literal (§6.6).
5. A bare `IDENT` not in call position is a field reference (§8.2), unless it
   spells a bound name that is in scope (§5.3.4), in which case it is a `REF`
   (§5.5). *(v0.2: let delta A.2)*
6. `in`'s right operand must be a `ListLiteral` or a field designator (§6.5).

#### 5.3.2 List literals

A `ListLiteral` MUST be non-empty (an empty literal has no element type and no
use; G11 degenerate-input discipline — excluded rather than special-cased) and
**homogeneous**: every element expression must have the same scalar type
(`bool`, `int`, `double`, `string`, or `bytes`; `null` and nested lists are not
admissible elements). Element expressions may be arbitrary expressions of that
type — this is what lets a transform return a vector (§14.3). Its element count
contributes to `|E|` and is additionally capped by `maxCollection`.

#### 5.3.3 Field designators

```ebnf
FieldDesignator ::= Root { "." ( IDENT | QualSel ) }
Root            ::= IDENT | "field" "(" STRING_LIT "," STRING_LIT ")"
QualSel         ::= "field" "(" STRING_LIT "," STRING_LIT ")"
```

A field designator is the only admissible argument to `has()` and the only
admissible right operand of `in` other than a list literal. Note that a field
designator is also an ordinary `Postfix` expression — this production defines a
*restriction*, not new syntax. Method-call selectors other than `field(...)`
disqualify a term from being a designator.

#### 5.3.4 Bindings in the text form: names, scope and parse-time rejections (v0.2; text form only)

Bound names exist only in the text form. A `REF` carries an index and names are
never on the wire (§11.1), so this subsection binds parsers and printers, not T2 or
T6, and nothing in it can be checked on a wire expression.

1. **Scope and resolution.** The name bound by the `k`-th `BindCall` of a spine
   (`k` counted from 0) is in scope in the `init` of every later `BindCall` of that
   spine and in the main body. It is not in scope in its own `init` or in an
   earlier one. A bare `IDENT` not in call position that spells a name in scope is
   `REF(k)`, fixed by the parser at parse time; any other bare `IDENT` is a field
   reference (§8.2). A name is resolved once, at parse, so no candidate and no
   schema can change what it means. This is CEL's own scoping rule for `cel.bind`.
2. **Parse-time rejections.** A parser MUST reject, loudly at parse and never by
   partial acceptance (§5.1):
   - (a) a name bound twice in one spine;
   - (b) a `cel.bind` anywhere but the outer spine (§5.3.1 item 1);
   - (c) a bound name that is never used (the text form of §5.5 item 2);
   - (d) a bound name used as a selector root (`p.x`, `p.field(…)`), as `has()`'s
     target, or as `in`'s list operand (the positions §5.5 item 4 excludes; a bound
     name in `contains`'s needle or in an argument of `field(…)` is already rejected
     by §5.3.1 items 3-4, because it is not a string literal);
   - (e) a bound name that also occurs as an unqualified field reference anywhere
     in the same text **[[EDITOR-Q 1: the delta (R15) says "unqualified field
     reference". Does a non-root selector step spelled like the bound name (`p.a`
     with `a` bound) count? The printer rule in item 4 counts every unqualified
     field STEP, so printed text never clashes either way; the delta does not say
     which the parser rejects.]]** This is a lexical no-clash rule, decidable
     without a schema. It removes the two meanings of one spelling in
     `cel.bind(a, a + 1, a)`, where the first `a` is a field and the other two are
     the binding. Under it a bound name hides no field; a field of that name stays
     reachable as `field(…)` (§8.3);
   - (f) `cel` as a bound name **[[EDITOR-Q 2: the delta rejects `cel` as a bound
     name but does not say whether `cel` becomes a reserved word (§5.2). This text
     does not reserve it, so a candidate field named `cel` stays reachable
     unqualified.]]**.

   A method call on a bound name (`a.startsWith("x")`) is not a selection and is
   not rejected by (d).
3. **Iterative spine.** The parser MUST parse the spine iteratively (a loop over
   the `BindCall`s), not with one parser recursion level per binding, so that the
   text form can express every binding count the wire admits, `maxBindings`
   (§10.3). *(Informative: the Java authoring parser meters three frames per nested
   argument against a 400-frame cap, so 256 nested binds would need about 768
   frames and E2's 22 bindings about 66; the reviewer's reading of the parser
   source, UNVERIFIED by a run: review F D3 (iii).)*
4. **Printing.** A decoder that prints a wire expression as text prints its
   bindings as a spine in index order and names binding `i` `b<i>` followed by the
   fewest `_` characters that make the name differ from every unqualified field
   step in the expression (every body, every selector chain), so that wire → text →
   wire is the identity. *(Informative: with `B_0 = x * 2` and
   `M = REF(0) + FIELD_REF(Unqual("b0"))`, plain `b0` would print
   `cel.bind(b0, x * 2, b0 + b0)`, which re-parses to a different AST: review F D3 (ii).)*
5. **Why `cel.bind` (informative; RL-JGDMS-0023, operator 2026-11-14).** It keeps the
   format's CEL-subset claim (§5.1): `cel.bind` is CEL's own extension (cel-go
   `ext/README.md`, as fetched by the delta's reviewer on 2026-11-14), and
   restricting it to the outer spine gives exactly the flat list of §5.5. Because
   names are resolved at parse into an index, §8.2's concern, a meaning that depends
   on the candidate or its schema, cannot arise. Whether a CEL parser accepts the
   text and whether CEL evaluates `cel.bind(a, 1/0, true)` to `true` are
   HYPOTHESES until one is run (§15 item 11). **The rules of item 2 are author
   tooling, not a security boundary.** The parser runs on the client, so a hostile
   client sends any bytes it likes; server safety rests only on the decode and
   verify checks (§12.3: T2, T6). Item 2 plays the part a compiler's diagnostics
   play: it stops an honest author writing an expression that means something
   other than it reads (operator, 2026-11-14: "helpful similar to how a compiler is
   helpful to write correct code").

*(v0.2: let delta A.2, R14, R15)*

### 5.4 Exclusions (NORMATIVE — the fence around the language)

The following MUST NOT be accepted, in text or on the wire, and no conformant
implementation may offer them as extensions (an "extended" evaluator is
non-conformant, full stop — same-mechanism-for-Rust-and-Java is the point of this
standard):

1. **CEL's comprehension macros** — `all`, `exists`, `exists_one`, `map`,
   `filter`. These are CEL's per-element iteration constructs; their cost scales
   with data in ways §3.2(iii) does not cover, and they are the unbounded-feeling
   part of CEL this standard exists to exclude. Stated here explicitly so no
   future "but CEL has it" argument reopens them without a new theorem.
2. **`matches()` / regular expressions.** Even RE2-class engines add a large,
   hard-to-make-bit-identical dependency; backtracking engines are ReDoS.
   Excluded in v0.1; §15 notes the only acceptable future shape (linear-time
   RE2-class engine, pinned syntax and semantics, its own cost model) —
   `contains`/`startsWith`/`endsWith` cover the surveyed Reggie/Outrigger needs
   (STD-009 §11's vocabulary check).
3. **User-defined functions, lambdas, closures** — no syntax exists.
4. **Assignment, rebinding, parameterised bindings, nested scopes** — no syntax
   exists; flat backward-only bindings are §5.5. A repeated subexpression is
   either written out (cost is syntactic, §10.2; evaluators MAY CSE since purity
   makes it unobservable) or bound once (§5.5). *(v0.2: let delta A.1)*
5. **Loops, recursion** — no syntax exists.
6. **String/bytes/list concatenation** (`+` is numeric-only) and all other
   string-building operations — keeps allocation trivially bounded (§3.2 iv).
   (Divergence from CEL, which overloads `+`.)
7. **Comprehension-free CEL features not carried over:** `uint` type and literals,
   timestamp/duration types, type values and `type()`, `dyn`, string `size` as a
   method (use the global `size()`), map literals, object construction, index
   operator `[]`. Each is either substrate-absent or deferred (§15).

**Where Turing-completeness would enter (v0.2).** A binding that takes a
parameter, or a reference to the binding being defined or to a later one. Neither
has a production, and either requires a new §3.2 extension. A second trap for a
later revision: if `in` ever accepted a `REF` as its list operand, charging each
use 1 (§10.2) would under-count, because an `in` over a list scales with the list
size (§10.2's `in` rows), so that change needs a revised cost rule as well as a
§3.2 extension. *(v0.2: let delta A.1, R23)*

### 5.5 Bounded local bindings (NORMATIVE, v0.2)

An expression is a sequence `B_0, …, B_(n-1), M` of `n + 1` trees (§11): the
**bindings** `B_i` and the **main body** `M`, with `0 ≤ n ≤ maxBindings` (§10.3).
`B_i` is binding `i`'s body. Indices are absolute, 0-based and in declaration
order. With `n = 0` the expression is a v0.1 expression. The meaning of a
binding is given by expansion (§6.9); the text form is §5.3 and §5.3.4; the wire
form is §11.3 item 7 and Appendix B.

1. **Backward-only references.** A `REF(j)` node may appear in `B_i` only if
   `j < i`, and in `M` only if `j < n`. So the reference graph is a finite DAG:
   there is no cycle and no recursion, and the language still denotes only
   fixed-size circuits.
2. **Every binding is referenced** at least once, from a later body or from `M`.
   An unreferenced binding is a well-formedness error. The rule is syntactic:
   `false ? a : 0` counts as a use of `a`. So every body is reachable and every
   body is statically checked.
3. **No alias bodies.** A binding body MUST NOT be a bare `REF`: it adds nothing
   and only multiplies ASTs with one meaning. The main body MAY be a bare `REF`
   (`cel.bind(a, e, a)`).
4. **Where a `REF` may appear.** A `REF` is an expression node of arity 0 and may
   appear wherever an expression may appear, EXCEPT where §5.3.1 and §11.1 already
   fix the operand's form: `has()`'s target (a `FIELD_REF`), `in`'s list operand
   (`LIST_LIT` or `FIELD_REF`), `contains`'s needle (a string literal),
   `field(...)`'s arguments (literals), and the root of a selector chain
   (selection exists only inside `FIELD_REF`). These restrictions are structural
   in v0.1; this item says so.
5. **Types.** A body may have any result type, including `list<T>` and `object`.
   A list-typed binding can only be returned, used as a `?:` branch, or passed to
   `size()`; an object-typed binding can only be returned or used as a `?:`
   branch. This is intended, not an omission. **[[EDITOR-Q 3: §4.6 and §6.1 also
   allow `== null` and `!= null` on a value of any type, so a list- or
   object-typed binding can be compared with `null` as well; the delta's "can
   only" omits it.]]**
6. **Still excluded:** parameters, lambdas, nested scopes, rebinding, shadowing of
   one binding by another, recursion, forward references, assignment (§5.4 items
   3-5).

*(Informative — the motivating case.)* E2, the camera projection
(`Stage1Expressions.e2()` in the Stage 1 benchmark), written out is 1,205,700
nodes, depth 37, `C(E)` 5,835,619, MEASURED by the Stage 1 run (benchmark log
`bench.log:56-57`, a J: scratch log outside the repository; lines 56 and 57 re-read
at the v0.2 edit: "E2: 1205700 nodes, depth 37" and "C(E) = 5835619"). Bound by hand it is
about 260-280 nodes with 22 bindings (the delta's hand count of
`Stage1Expressions.java:251-277`; a second hand count by the Sonnet reviewer also
finds 22; UNVALIDATED, because no bound form of E2 exists yet). Its effective
depth (§10.3.1) is 48, a hand trace of 37 + 11 hops by both reviewers
(UNVALIDATED), and its expanded cost is 5,835,619 by construction, since
expansion reproduces the written-out tree. Both new ceilings admit it with
margin (§10.3). *(v0.2: let delta A.1, R2, R3, R23)*

---

## 6. Operator Semantics (NORMATIVE)

Every operator's semantics is total over well-formed operands: it yields a typed
value or an error from §9.2. "Operands of different types" below always means
after §4.2 widening; static typing (§12.4) rejects what it can at verification
time, and evaluation enforces the same rules dynamically regardless.

### 6.1 Equality `==`, `!=`

Defined for: both operands the same scalar type (`bool`, `int`, `double`,
`string`, `bytes`); the numeric cross-pair `int`×`double` (exact mathematical
comparison, §4.3.3); and any operand paired with `null` (§4.6: `null` equals only
`null`). Any other pairing — including `list`×anything and `object`×anything —
is `TYPE_MISMATCH` (never a silent `false`; fail-closed, G6 — a deliberate
divergence from modern CEL, whose heterogeneous equality returns `false` for
mixed-type operands; Appendix A row 10). `!=` MUST equal
`!(==)` in every case, including NaN (`NaN != NaN` is `true`).

### 6.2 Ordering `<`, `<=`, `>`, `>=`

Defined for: `int`×`int`; `double`×`double` (IEEE, NaN unordered → `false`,
§4.3.4); `int`×`double` (exact, §4.3.3); `string`×`string` (code-point
lexicographic, §4.4); `bytes`×`bytes` (unsigned-octet lexicographic, §4.5).
Everything else — including `bool` ordering (divergence from CEL, which orders
booleans) — is `TYPE_MISMATCH`.

### 6.3 Arithmetic `+ - * / %`, unary `-`

Defined for `int` (checked, §4.3.1) and `double` (`+ - * /` and unary `-` only,
IEEE-total, §4.3.2). Mixed `int`×`double` arithmetic is `TYPE_MISMATCH` (§4.3.2;
use `int()`/`double()`). `+` on strings/bytes/lists is `TYPE_MISMATCH` (§5.4
item 6).

### 6.4 Boolean operators `&&`, `||`, `!`, and the error interaction

`!` requires a `bool` operand (else `TYPE_MISMATCH`) and negates it; `!` of an
error is that error.

`&&` and `||` use **CEL's commutative error-absorption semantics**, chosen over
strict left-to-right short-circuit, with error identity additionally pinned
(which CEL leaves open). Let `L`, `R` be the operands' evaluation results
(each a `bool` or an error; a non-`bool` value is first replaced by
`TYPE_MISMATCH` at its own position). The complete result matrices:

| `L && R` | `R` = `true` | `R` = `false` | `R` = error `e₂` |
|---|---|---|---|
| **`L` = `true`** | `true` | `false` | error `e₂` |
| **`L` = `false`** | `false` | `false` | `false` |
| **`L` = error `e₁`** | error `e₁` | `false` | error `e₁` |

| `L \|\| R` | `R` = `true` | `R` = `false` | `R` = error `e₂` |
|---|---|---|---|
| **`L` = `true`** | `true` | `true` | `true` |
| **`L` = `false`** | `true` | `false` | error `e₂` |
| **`L` = error `e₁`** | `true` | error `e₁` | error `e₁` |

In words: a **determining value** (`false` for `&&`, `true` for `||`) absorbs an
error in the other operand; if neither operand determines the result and at least
one is an error, the result is an error, and **the reported error is the left
operand's** when both are errors (pinned for cross-language determinism; CEL
permits either). An implementation MAY short-circuit (skip `R`) only when `L` is
the determining value — when `L` is an error it MUST still evaluate `R` to check
for absorption. The result is therefore evaluation-order-independent by
construction, which is why the conformance suite can assert it exactly.

**Why absorption over strict left-to-right (informative):** (1) it is CEL's
field-proven semantics — corpus and intuition transfer; (2) it makes `p && q`
symmetric at the verdict level, so authors need not order conjuncts defensively
around possibly-absent fields (`has(f) && f > 5` and `f > 5 && has(f)`… the
first is still the correct idiom, but reordering never *changes a verdict* from
match to no-match or vice versa); (3) in predicate context every error is already
no-match (§9.4), so absorption only ever *rescues* a verdict that a determining
`false`/`true` fully justifies — it never invents a match from an error.

### 6.5 Membership `x in L`

`L` must be a `list<T>` (a list literal or a list-typed field; anything else is
`TYPE_MISMATCH`). Semantics: evaluate `x`, then compare against elements
left-to-right (wire order / literal order) under §6.1 equality:

- If any element compares equal → `true` (later elements need not be examined,
  and any error a later comparison would produce is absorbed — same design as
  §6.4).
- Else, if any element comparison produced an error → the **first** (leftmost)
  such error.
- Else → `false`. Membership in an empty projected list is `false` (G11).

If `x` itself evaluates to an error, the result is that error.

### 6.6 String functions `contains`, `startsWith`, `endsWith`

Receiver-syntax methods on `string` (§5.3): `s.contains(sub)`,
`s.startsWith(pre)`, `s.endsWith(suf)`, each returning `bool`. All comparisons
are exact code-point-sequence comparisons — no normalization, no case folding.
`s.contains(sub)` is `true` iff `sub` is a contiguous (possibly empty)
subsequence of `s`; every string starts with, ends with, and contains the empty
string (G11: pinned, not left to library behaviour).

Receiver must be `string`; argument must be `string`; else `TYPE_MISMATCH`.
**`contains`'s argument MUST be a string literal** (well-formedness, §5.3.1 —
this makes the needle's length part of `|E|` and keeps §3.1's `O(|E| × S)` cost
claim true even for a naive quadratic search; §10.2). `startsWith`/`endsWith`
accept any `string` expression (their cost is linear regardless).

### 6.7 Conditional `c ? a : b`

`c` is evaluated first and must be `bool` (`TYPE_MISMATCH` otherwise); if `c`
errors, the result is that error. Exactly one branch is then evaluated — **the
untaken branch is never evaluated and its errors do not exist** (aligned with
CEL). Static typing requires both branches to have the same type (§12.4); that
common type is the conditional's type. Adopted (rather than excluded) because
transforms need conditional formulas (clamping, unit selection) and its bound is
covered by §3.2(ii).

### 6.8 Presence `has(d)`

`d` is a field designator (§5.3.3), resolved per §8 but **not** evaluated as a
value. Result table (see §4.6, §8.4):

| Situation at the leaf | `has(d)` |
|---|---|
| present, non-null | `true` |
| present, null | `false` |
| absent (no declaring namespace) | `false` |
| any step ambiguous (§8.2) | error `AMBIGUOUS_FIELD` |
| an intermediate step absent or null | `false` |
| an intermediate step resolves to a non-`object` value | error `TYPE_MISMATCH` |
| candidate undecodable | error `CANDIDATE_UNDECODABLE` (§9.2) |

`has()` exists precisely so that schema evolution is benign: STD-009 §8's
fail-closed rule ("absent referenced field ⇒ no-match") is the *unguarded*
behaviour; `has()` is the author's tool to make absence an explicit branch
instead.

### 6.9 Bindings: meaning, strategy and resource rules (NORMATIVE, v0.2)

- **Meaning by expansion.** The outcome of `REF(j)` is the outcome of `B_j`
  evaluated against the same projection. Equivalently, an expression's outcome is
  that of its **expansion** (each `REF` replaced by its body's expansion) under
  §6–§9. That is the whole definition; the evaluation strategy is free within the
  two resource rules below. The ceilings of §10.3 do not apply to the expansion,
  which can exceed every one of them (the written-out E2 is 1,205,700 nodes, §5.5).
- **`REF` is non-strict** (§9.3). A binding whose body would error affects the
  outcome only where the expansion would evaluate that body.
  `cel.bind(a, 1/0, true)` is `true`; `cel.bind(a, 1/0, c ? a : 0)` with `c` false
  is `0`.
- **Resource rule 1 (MUST).** A conformant evaluator evaluates each body **at most
  once per evaluation** and stores its OUTCOME, error or value, for reuse. An
  evaluator that does not store error outcomes is non-conformant, whether it is
  lazy or eager. A lazy evaluator evaluates a body at its first use; an eager one
  evaluates the bodies in declaration order, and then it MUST store a body's error
  and raise it only at a use: it MUST NOT abort on the first failing binding.
  Rule 1 is gated by two instrumented vectors (§13.1).
- **Resource rule 2 (MUST).** The evaluation stack never exceeds the effective
  depth (§10.3.1). Because a `REF` counts as one level there, this holds for every
  strategy.
- **Why lazy and eager cannot disagree on an outcome.** A body's outcome is a
  function of the expression and the projection alone (§3.1 claim 2; §8.1 item 4),
  so a stored outcome equals a re-evaluated one in any order. They can differ only
  outside the outcome: timing, stack, and the order of calls on the projection.
- **No provider-absent rule.** The registry is closed and evaluation is total
  (§9.1), so an evaluator that cannot evaluate a registry function is defective.
  Refusing an expression that calls such a function is a consumer policy
  (Appendix B §B.9), not a language rule.
- **Undecodable candidates.** This section inherits §8.1 item 2 as written (the
  whole evaluation); see §15 item 9.

*(v0.2: let delta A.3, R1, R4, R5, R6, R20, R22, R25)*

---

## 7. Platform Function Registry **[RATIFIED (Peter, 2026-07-20)]**

### 7.1 Registry principles (NORMATIVE)

- The registry is **closed and versioned with this standard**. An expression may
  call only functions listed here, by pinned registry identity (§11.3); T6 MUST
  reject any reference outside the list (never resolve a wire-supplied name
  dynamically). Adding a function is a spec revision with board review, and MUST
  come with: exact signature, domain, error behaviour, determinism class, cost
  entry (§10.2), and conformance vectors (T5).
- Every registry function MUST be **pure and deterministic**: its result is a
  function of its arguments and this standard's pinned constants only. No
  function may read time, randomness, locale, environment, or candidate state
  beyond its arguments.
- Uniform error policy for the numeric functions: **NaN argument ⇒ `DOMAIN`**;
  for the functions marked *finite-only* below, **±Inf argument ⇒ `DOMAIN`**;
  arguments outside the stated mathematical domain ⇒ `DOMAIN`. (Operators keep
  IEEE totality, §4.3.2, because candidate data may legitimately contain
  NaN/±Inf and comparisons must still work; functions are formula territory,
  where a non-finite input means upstream garbage — fail closed, explicitly.)

### 7.2 The function set

Derived from the confirmed Survey-zoot use case (bearing/elevation/distance →
vector components; `DESIGN-CorroborationFramework.md` §8 note) plus the minimal
general-purpose complement. **This closed list was ratified as-is by Peter,
2026-07-20** — deliberately small;
absence of a function here is a decision, not an oversight:

| # | Signature | Result | Domain / errors | Determinism class |
|---|---|---|---|---|
| 1 | `size(s: string) → int` | code-point count | total | exact |
| 2 | `size(b: bytes) → int` | octet count | total | exact |
| 3 | `size(l: list<T>) → int` | element count | total | exact |
| 4 | `int(x: double) → int` | truncate toward zero | NaN/±Inf ⇒ `DOMAIN`; truncated value outside int64 ⇒ `OVERFLOW` | exact |
| 5 | `double(x: int) → double` | IEEE round-to-nearest-even | total (deterministically rounded for \|x\| > 2⁵³) | exact |
| 6 | `abs(x: int) → int` | absolute value | `abs(−2⁶³)` ⇒ `OVERFLOW` | exact |
| 7 | `abs(x: double) → double` | clear sign bit | total (NaN allowed: sign-cleared NaN is still NaN; `abs(±Inf)` = +Inf) | exact |
| 8 | `min(x, y)` / `max(x, y)` — both `int` or both `double` | smaller/larger | double: either NaN ⇒ `DOMAIN`; `min(−0.0, 0.0)` = −0.0, `max(−0.0, 0.0)` = +0.0 (pinned: Rust's and Java's native min/max disagree on NaN — neither may be used unaudited) | exact |
| 9 | `sqrt(x: double) → double` | square root | *finite-only*; x < 0 ⇒ `DOMAIN`; `sqrt(−0.0)` = −0.0 | exact (IEEE-mandated correct rounding) |
| 10 | `radians(x: double) → double` | `x ⊗ C_DEG2RAD` (one IEEE multiply) | *finite-only* | exact given pinned constant |
| 11 | `degrees(x: double) → double` | `x ⊗ C_RAD2DEG` (one IEEE multiply) | *finite-only* | exact given pinned constant |
| 12 | `sin(x)`, `cos(x)`, `tan(x): double → double` | trigonometric | *finite-only* (tan is total over finite doubles — no finite double is an exact odd multiple of π/2) | **correctly rounded (§7.5)** |
| 13 | `asin(x)`, `acos(x): double → double` | inverse trig | *finite-only*; \|x\| > 1 ⇒ `DOMAIN` | **correctly rounded** |
| 14 | `atan(x: double) → double` | inverse tangent | *finite-only* | **correctly rounded** |
| 15 | `atan2(y: double, x: double) → double` | two-argument arctangent | *finite-only* (either argument non-finite ⇒ `DOMAIN`); zero/zero and signed-zero cases follow IEEE-754 `atan2` exactly (`atan2(±0, +x)` = ±0, `atan2(±0, −x)` = ±π, etc.) | **correctly rounded** |

**Wire identity (normative — corrected per Appendix B board review,
2026-07-20).** The rows above define registry **membership** (the ratified
closed function set); they are **not** themselves the wire ids. This
paragraph's original "each row is one wire id" rule was internally
inconsistent with its own table — row 8 bundles `min` and `max` (two distinct
operations that operand-type dispatch cannot distinguish) and rows 12–14 group
several unrelated functions for presentation — as flagged by T2 and confirmed
by its board review. The pinned wire function-registry identity referenced by
`CALL` nodes (§11.1) and enforced by T6's allowlist (§12.2) is **Appendix B
§B.8.3's table: one wire id per (function name × operand-type signature)** —
generalizing the pattern rows 6/7 (`abs` per numeric type) already used — with
ids 22–24 covering `contains`/`startsWith`/`endsWith`, which this table
declares members but never numbered. Membership here; id assignment in
Appendix B.

**Implementation trap (normative warning): native narrowing casts for `int(x:
double)`.** Java's `(long) x` cast and Rust's `x as i64` on a `double`
natively **saturate** (NaN → 0, out-of-range magnitude → `Long.MIN_VALUE`/
`MAX_VALUE` or `i64::MIN`/`MAX`) instead of raising an error. Row 4's `int(x:
double)` MUST NOT be implemented as a bare native cast: a conformant evaluator
MUST check for NaN/±Inf (⇒ `DOMAIN`) and an out-of-int64-range truncated
magnitude (⇒ `OVERFLOW`) **before** or **instead of** the native narrowing
operation, never rely on its saturating behaviour to stand in for the
required error.

String methods `contains`/`startsWith`/`endsWith` are specified in §6.6 and are
part of the same closed registry for allowlist purposes; `has` and `field` are
special forms (§6.8, §8.3), not functions, and take no place in the value
registry.

**Not included, deliberately** (each would need its own ratified row + vectors):
`pow`, `exp`, `log`, `hypot` (use `sqrt(x*x + y*y)`), `floor`/`ceil`/`round`
(4's truncation plus arithmetic covers the surveyed needs), a `pi()` constant
(`radians(180.0)` yields the double nearest π if ever needed). §15 lists these as
possible future additions.

### 7.3 Pinned constants

| Constant | Definition | Decimal (shortest round-trip) | binary64 bit pattern |
|---|---|---|---|
| `C_DEG2RAD` | the binary64 value nearest π/180 | `0.017453292519943295` | `0x3F91DF46A2529D39` |
| `C_RAD2DEG` | the binary64 value nearest 180/π | `57.29577951308232` | `0x404CA5DC1A63C1F8` |

`radians`/`degrees` are **defined as a single IEEE multiplication by the pinned
constant** — deterministic on every platform with no correctly-rounded-library
requirement. This deliberately differs from `java.lang.Math.toRadians` /
`toDegrees` (which compute `x / 180 * π` in two operations); a JVM evaluator MUST
implement the pinned single-multiply form, not delegate to `Math`. The bit
patterns above are normative; T5 MUST carry them in the conformance vectors.

### 7.4 The cross-language transcendental determinism problem (context)

IEEE-754 mandates correct rounding for `+ − × ÷ sqrt` and conversions — those are
bit-exact everywhere for free. It does **not** mandate correct rounding for
transcendentals (`sin`…`atan2`): ordinary platform libms (glibc, musl, MSVC,
Apple, and Java's `Math`/`StrictMath` — fdlibm-derived, ≈1 ulp) legitimately
differ from each other in the last bit(s). Left unaddressed, the two evaluators
would return different vectors for the same observation and T5's "identical
results" claim would be false at the last ulp — silently, exactly the divergence
gap `SOW-CEL-Filter-Format.md` §2 warns CEL's own two implementations have.

### 7.5 Decision **[RATIFIED (Peter, 2026-07-20): option (a) as recommended — T5-load-bearing]**

Three options were required to be weighed (SOW T1 item 4):

- **(a) Require correctly rounded implementations** for rows 12–14 (crlibm /
  CORE-MATH class). Result: bit-exact by mathematical definition; the spec needs
  no reference implementation as oracle (G9 — "the nearest binary64 to the exact
  real result" is implementable from the doc alone, and independently checkable
  against arbitrary-precision arithmetic).
- **(b) Specify a tolerance** (e.g. ≤1 ulp) in the conformance suite. Rejected:
  a tolerance regime makes the transform's DER-encoded result **many valid byte
  strings for one input** — breaking G1 (one encoding per value) for any
  downstream digest, signature, byte-match, or cache key over transform results,
  and turning T5 from equality assertions into fuzzy comparisons whose
  divergences accumulate across composed formulas. It also silently licenses
  verdict divergence for predicates that compare a transcendental result against
  a threshold at the tolerance boundary.
- **(c) Restrict the registry to bit-exact-for-free operations** (drop rows
  12–14). Rejected: it guts the ratified Survey-zoot use case — the confirmed
  reason transforms exist at all.

**RATIFIED (Peter, 2026-07-20), as recommended: (a) — correct rounding is
REQUIRED for `sin`, `cos`, `tan`,
`asin`, `acos`, `atan`, `atan2`.** Honest cost statement: this binds T3 and T4 to
ship correctly-rounded implementations of seven functions. For Rust, the
CORE-MATH project publishes correctly-rounded binary64 routines designed for
exactly this adoption path. For Java there is no correctly-rounded standard
library (`StrictMath` is reproducible-but-not-correctly-rounded fdlibm), so T3
must port or bind one — a real, bounded cost (seven functions), to be priced into
T3's estimate, not discovered there. The rejected fallback, for the record: pin
semantics to fdlibm bit-for-bit (`StrictMath` as oracle) — reproducible and
cheap on the JVM, but it defines the language by reference to a specific C
codebase rather than by mathematics, fails G9's "from the doc alone" test, and
exports a 1990s approximation error into a greenfield cross-language spec
forever. (The pre-ratification draft named (b)-with-≤1-ulp as the explicit
fallback had (a) been rejected; with (a) ratified, that fallback is retired —
any future move off correct rounding is a spec revision that must re-ratify
§13.1 and accept the G1 consequences above in writing.)

---

## 8. Field References and the Candidate Data Model

### 8.1 The projection contract (NORMATIVE)

Evaluation observes the candidate **only** through the projection: the
`className → (fieldName → value)` map produced by the STD-006 fail-closed codec
(STD-009 §8, 2026-07-20 note: `ObjectCodec.decodeToFieldMap` over the embedded
`AtomicSerialSchemaRecord` chain). Normative consequences:

1. The projection reader MUST be the same fail-closed canonical codec as every
   other ATOMIC DER decode path — canonical-form rejection, `schemaDigest`
   verified against `schemaBytes` before use, `maxFields`/`maxCollection`
   ceilings — never a second, lenient scanner (G1; STD-009 §8 already states
   this; restated here because T3/T4 implement against this document).
2. A candidate whose bytes fail decode, whose schema fails digest verification,
   or which otherwise cannot be projected yields `CANDIDATE_UNDECODABLE` for the
   whole evaluation (§9.2) — in predicate context, no-match, fail-closed
   (STD-009 §8's ratified rule).
3. No object is ever reconstructed: no `check(GetArg)` runs, no constructor, no
   `DeSerializationPermission` gate is crossed. The filter verdict is therefore
   **pre-selection only** — the taking client still performs full gated
   deserialization on anything it accepts; a filter false-positive costs
   bandwidth, never integrity (STD-009 §8). A transform result is computed from
   decoded scalars and is likewise not a reconstructed object.
4. The projection is immutable for the duration of an evaluation.

### 8.2 Unqualified resolution **[OPEN — final decision belongs to the Outrigger integration SOW's B1; the rule below is this standard's recommendation and is normative unless B1 overrides it]**

A bare `IDENT` field reference is resolved against the candidate's schema chain
(the ordered list of per-class namespaces, most-derived class first — "leaf
first"; STD-006 §3.9):

- If **exactly one** namespace in the chain declares the name → the reference
  denotes that field.
- If **two or more** namespaces declare the name → the error `AMBIGUOUS_FIELD`,
  unconditionally — in static checking (§12.4) *and* at evaluation, *and* inside
  `has()`. A shadowed name MUST be referenced qualified (§8.3). The leaf-first
  order defines the search/reporting order only; it never silently picks a
  winner.
- If **no** namespace declares it → absent (§4.6: reference ⇒ `ABSENT_FIELD`;
  `has` ⇒ `false`).

**Why uniqueness-or-error rather than leaf-first-wins (informative but
load-bearing):** under leaf-first-wins, a service adding a field named `x` to a
subclass would *silently change the meaning* of every deployed filter that
referenced a superclass's `x` — a remote, undetected semantics change to an
already-verified expression, precisely the declaration-vs-drift hazard G4 exists
to catch. Under uniqueness-or-error the same evolution turns those evaluations
into `AMBIGUOUS_FIELD` → no-match, fail-closed and observable, and the author
re-registers with the qualified form. Ambiguity is per-candidate (each candidate
carries its own schema chain): the same expression can be unambiguous for one
candidate and ambiguous for another — the error is then per-candidate (worked
example §14.4).

### 8.3 Qualified resolution: `field(className, fieldName)`

- Global form: `field("com.example.Alpha", "x")` — resolves `x` in class
  `com.example.Alpha`'s namespace within the **candidate's** chain.
- Method form: `p.field("com.example.Point", "x")` — the same, within the chain
  of the nested `object` value `p` (nested objects carry their own schema chains
  and have the same shadowing possibilities).

Both arguments MUST be string literals (§5.3.1) — resolution is static in the
expression, never data-driven. The class name matches the schema chain's wire
class name exactly (string equality, no normalization). If the named class is not
in the (relevant) chain, or the named field is not in that class's namespace, the
reference is **absent** (§4.6). The qualified form also reaches fields whose wire
names are not lexical `IDENT`s or collide with reserved words (§5.2).

`field(...)` never returns a "namespace object": its result is the field's value,
exactly as an unqualified reference's would be.

### 8.4 Nested access

`a.b.c` resolves `a` per §8.2/§8.3 on the candidate, requires the result to be an
`object` (else `TYPE_MISMATCH`), then resolves `b` per the same rules against
that object's own chain, and so on. Intermediate null/absent behaviour: for a
**value reference**, an absent or null intermediate yields `ABSENT_FIELD`; for
`has()`, `false` (§6.8's table).

### 8.5 Filter applicability key **[OPEN — B1, with STD-009 §8]**

Which candidates a registered filter is applied to needs a deliberate key —
`className` (coarse; survives schema evolution) vs `schemaDigest` (exact; pins
one schema version) — with blast radius confined to the sender's own entries
either way (a sender controls its own `schemaBytes`). This standard does not
decide it; it only requires (per §9) that whatever candidates arrive, evaluation
is total and fail-closed, so a mis-keyed filter degrades to no-match, never to
undefined behaviour.

---

## 9. Evaluation Semantics (NORMATIVE)

### 9.1 Totality

For every well-formed expression `E`, declared context, and candidate, evaluation
yields **exactly one** of: a typed value (of `E`'s type), or one error value from
§9.2. There is no third outcome — no exception escapes, no partial result, no
implementation-defined behaviour. Two conformant evaluators MUST yield the
identical outcome: the same value bit-for-bit (§4.3.4 canonicalization included)
or the same error code.

### 9.2 The closed error set

| Code | Raised when |
|---|---|
| `TYPE_MISMATCH` | an operand/argument/receiver has a type outside the operator's or function's definition (§6, §7), incl. operations on `null` beyond `==`/`!=`, on `object`, on `list` beyond §4.7, and a non-`bool` predicate result (§9.4) |
| `OVERFLOW` | checked `int` arithmetic out of range (§4.3.1), `abs(−2⁶³)`, `int(x)` out of range (§7.2 row 4) |
| `DIVISION_BY_ZERO` | `int` `/` or `%` with zero divisor |
| `ABSENT_FIELD` | a value reference to an absent field, or through an absent/null intermediate (§4.6, §8.4) |
| `AMBIGUOUS_FIELD` | an unqualified name declared by ≥2 namespaces in the relevant chain (§8.2) |
| `DOMAIN` | a platform-function argument outside its domain, incl. NaN, and ±Inf for finite-only functions (§7.1, §7.2) |
| `COST_BOUND` | the statically computed cost bound was exceeded — see §10.5 for why this cannot occur for a verified expression |
| `CANDIDATE_UNDECODABLE` | the candidate failed fail-closed projection (§8.1) |

The set is **closed**: no implementation may add codes, and every abnormal
condition in this standard is mapped above. Error values MAY carry an informative
message; conformance observes **only the code** (messages are explicitly outside
T5's equality assertions).

### 9.3 Deterministic error identity

Where more than one error could arise in one evaluation, the result is pinned:
operands/arguments are notionally evaluated **left-to-right, depth-first**; a
strict construct (arithmetic, comparison, function call, `?:`'s condition, list
literal) yields the **first** (leftmost-innermost) error among its parts and does
not evaluate further parts; the non-strict constructs (`&&`, `||`, `in`, `?:`'s
branches) follow their own pinned tables (§6.4, §6.5, §6.7), which are
order-independent for values and pin the left error when two errors compete.
Consequently the complete observable outcome — not merely "is error" — is
deterministic, and T5 asserts codes exactly.

**`REF` (v0.2).** `REF` is added to the non-strict constructs. A binding whose
body would error affects the outcome only where the expansion would evaluate that
body (§6.9): `cel.bind(a, 1/0, true)` is `true`, and `cel.bind(a, 1/0, c ? a : 0)`
with `c` false is `0`. A stored error outcome is raised at each use with its stored
code, so the left-error rule above carries through a slot unchanged: in `a && b`
where both bodies error with different codes, the result is the left code.
*(v0.2: let delta A.3)*

### 9.4 Predicate context

The expression's static type MUST be `bool` (§12.4; a dynamically non-`bool`
result — possible only for an unverifiable-schema expression — is
`TYPE_MISMATCH`). The verdict mapping is:

- result `true` → **match**;
- result `false` → no-match;
- **any error → no-match, fail-closed** (STD-009 §8's ratified rule: undecodable
  candidate or absent referenced field ⇒ no-match; this standard extends the same
  disposition to the entire closed error set — a filter can only ever *fail to
  select*, never fail open into selecting, and never disrupt the host's matching
  loop).

Implementations SHOULD make error verdicts observable to local diagnostics
(counters/logs) — silent mass no-match from a stale filter is an operability
trap — but the wire-visible verdict is exactly match/no-match.

### 9.5 Transform context

The declared result type is part of the transform's registration (T2 carries it;
§12.4 checks the expression against it). Outcome delivery:

- a typed value → delivered as the canonical ATOMIC DER encoding of that value
  (STD-006 rules; `double` results canonicalized per §4.3.4);
- **an error → delivered as an explicit, distinguishable error result carrying
  the §9.2 code — NEVER a default value, NEVER null, NEVER a sentinel in the
  value domain.** The wire shape of the error result is T2's to define; the
  requirement that it be disjoint from every value encoding is this standard's.

A NaN or ±Inf `double` result is a **value**, not an error (it arises only from
IEEE-total operator arithmetic, §4.3.2 — registry functions reject non-finite
inputs); consumers of transform results decide its meaning. Rationale: silently
mapping non-finite values to errors would make `x / 0.0` observable-vs-`DOMAIN`
inconsistent; the corroboration framework's consumers (F1+ tiers) apply their own
screening.

### 9.6 Evaluation-context isolation

An evaluation MUST NOT observe anything besides expression + projection + pinned
constants: no other candidate, no previous evaluation's outcome, no clock, no
randomness, no host state. Evaluations are therefore trivially parallelizable and
cacheable by (expression, candidate) — properties the host MAY exploit with no
semantic effect.

**Binding slots (v0.2).** A stored binding outcome (§6.9) belongs to ONE
evaluation of ONE expression against ONE candidate. It is invalid at the entry of
every evaluation, including after an evaluation that ended abnormally. Sharing slot
storage between expressions, or across candidates, is non-conformant. (The rule
above, "no previous evaluation's outcome", already implies this; the sentence makes
it explicit for the first evaluator state the language has.) *(v0.2: let delta A.4)*

---

## 10. Cost Model and Static Bounds (NORMATIVE)

### 10.1 Purpose

The cost model makes §3.1's claim 3 checkable: T6 computes `C(E)` at
verification time from the expression and the ceilings alone, and rejects any
expression whose bound exceeds the profile budget — before any candidate is ever
evaluated. (Precedent: Kubernetes' CEL cost estimation for admission policies —
same shape: static estimate against schema-derived bounds, enforced at admission,
not metered at runtime.)

### 10.2 Per-node costs

Let `S` = `maxScalarBytes` (§10.3), `K` = `maxCollection` (STD-006 §4.5),
`len(lit)` = a literal's length in code points/octets. Costs in abstract units
**[unit values PROPOSED — T6 pins the final table alongside `maxExprCost`]**:

**Length unit (board finding, 2026-07-20).** Every string/bytes length term in
this section — `len(lit)`, the `S`-bounded operand lengths, and any "needle
bound" — is measured in **wire octets** (for a string, its UTF-8 encoded
length; for bytes, its literal octet count). This is the language-neutral
upper bound consistent with `S = maxScalarBytes` itself being an octet count,
and it is what every conformant implementation — a Java evaluator scanning
UTF-16 code units, a Rust evaluator scanning UTF-8 bytes — can each cheaply
compute an accurate-or-conservative bound from. This closes the previously
ambiguous "code points/octets" phrasing above: octets, not code points, is
the pinned unit.

| Node | Cost (in addition to children's) |
|---|---|
| literal, field reference (per selector step), `has` | 1 |
| `bool` ops, `?:`, `int`/`double` arithmetic, numeric comparison, conversions (`int`, `double`), `abs`, `min`, `max`, `size` | 1 |
| `string`/`bytes` `==`/`!=`/ordering | 1 + S |
| `startsWith` / `endsWith` | 1 + min(S, needle bound) |
| `contains` (literal needle, §6.6) | 1 + S × len(needle literal) |
| `in` over a list literal | Σ per-element comparison cost (literal-sized) |
| `in` over a list field | 1 + K × (per-element comparison cost) |
| list literal of n elements | n |
| `sqrt`, `radians`, `degrees` | 4 |
| `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2` | 32 |

**Comparison-row tightening (board-ratified 2026-07-20; a MUST from v0.2).** A
conformant static cost computation MUST tighten the `string`/`bytes`
`==`/`!=`/ordering
row's `1 + S` to `1 + min(S, known literal octet length)` when at least one
operand has a static literal length (below) — this remains a sound upper
bound because the comparison's actual work is bounded by the shorter
operand (equality/lexicographic ordering on strings or byte arrays is
`O(min(len))`, never `O(S)` once a shorter length is already known
statically). The reference implementation applies this tightening; it is
the ratified, expected behavior, and from v0.2 it is mandatory. Without the MUST,
two conformant verifiers disagree on admission already in v0.1, and v0.2
multiplies the disagreement through `xCost` (§10.3.1). *(v0.2: let delta R10)*

**Static kind and static literal length (v0.2).** Cost is computed without a
schema from two attributes of each node, defined here so that a second
implementation computes the same integer. The **static kind** of a node is one of
numeric, bool, string, bytes, null, list, object, unknown, by node kind:

| Node | Static kind |
|---|---|
| `LIT_INT`, `LIT_DOUBLE`, `NEG`, `ADD`, `SUB`, `MUL`, `DIV`, `MOD` | numeric |
| `LIT_BOOL`, `HAS`, `NOT`, `AND`, `OR`, `EQ`, `NE`, `LT`, `LE`, `GT`, `GE`, `IN` | bool |
| `LIT_STRING` / `LIT_BYTES` / `LIT_NULL` / `LIST_LIT` | string / bytes / null / list |
| `FIELD_REF` | unknown |
| `COND` | its branches' kind if they are equal, else unknown |
| `CALL` | its registry signature's result kind (§7.2, §6.6): numeric for `size`, `int`, `double`, `abs`, `min`, `max`, `sqrt`, `radians`, `degrees` and the seven transcendental functions; bool for `contains`, `startsWith`, `endsWith` |
| `REF(j)` | the static kind of `B_j` |

No node kind yields `object` without a schema (the editor's reading of
`CostModel.categoryOf`, which never returns it). The **static literal length** is
defined only for a string or bytes literal: its length in wire octets (the length
unit above). A `REF(j)` has `B_j`'s static literal length; `?:` never has one; no
other node has one. A string/bytes comparison costs 1 if the static kind of either
operand is numeric, bool, null, list or object, and otherwise (both operands
string, bytes or unknown) costs `1 + min(S, the static literal lengths of the
operands that have one)`; `startsWith`/`endsWith` cost `1 + min(S, the
argument's static literal length, or S if it has none)`. **[[EDITOR-Q 4: the
delta (R11) pins the two definitions and says they are the table the Java cost
model implements, but does not write the rule that applies them to comparison
cost. That rule is transcribed here from `CostModel.comparisonCostByCategory`
and `callCost` (CostModel.java:230-239 and 261-292, at base `7ccf90bb9`). Confirm.]]**
*(v0.2: let delta R11)*

`C(E)` = Σ over all AST nodes of all bodies (the bindings and the main body,
§5.5), each body once and each `REF` node 1. Cost accounting is **syntactic**: a
repeated subexpression that is written out is charged per occurrence (no CSE
assumed; implementations MAY CSE — unobservable by purity — but MUST NOT rely on
it to pass the bound); a subexpression that is bound once is charged once plus 1
per use. That charge is a bound on work only for an evaluator that follows §6.9
rule 1; §10.5 gives the bound for one that does not. *(v0.2: let delta C.3, R5)*

### 10.3 Expression ceilings **[PROPOSED values — T2 pins them in an STD-006 §4.5-style table]**

| Constant | Proposed value | Applies to |
|---|---|---|
| `maxExprNodes` | 1024 | AST node count (every literal, operator, call, selector step, and list element is a node), summed over all bodies, each `REF` counting 1 (§10.3.1) |
| `maxExprDepth` | 32 | AST depth of each body separately, a `REF` being a leaf of depth 1 below its parent; the evaluation-stack bound is `maxEffectiveDepth` below (§3.2 ii, §10.3.1) |
| `maxSelectorSteps` **[PROPOSED]** | 16 | maximum selector-chain length of a single `FIELD_REF` node (§11.1) — a G10 interior-fence ceiling: the chain is internal to one arity-0 node, so neither `maxExprNodes` nor `maxExprDepth` bounds it |
| `maxScalarBytes` | 65536 | the per-scalar (string UTF-8 / bytes) size the cost model charges; **[OPEN → T2]** STD-006 §4.5 currently has no per-string ceiling — T2 MUST either pin this constant there or derive it from an enclosing-record bound, so that `S` in §10.2 is a real, enforced number, not an assumption. **This ceiling MUST be enforced on both candidate projected scalar fields and expression literals** (`LIT_STRING`/`LIT_BYTES`, §11.3); until it is pinned and enforced on both sides, §3.3 explicitly does not claim Claim 3 (bounded cost) holds. |
| `maxExprCost` | 1 000 000 | the verification-time ceiling on `C(E)` |
| `maxBindings` **[PROPOSED]** | 256 | `n`, the number of bindings of one expression (§5.5) |
| `maxEffectiveDepth` **[PROPOSED]** | 64 | `effDepth` of the main body `M` (§10.3.1) |
| `maxExpandedCost` **[PROPOSED]** | 8 388 608 (2^23) | `xCost` of the main body `M` (§10.3.1) |

These compose with STD-006 §4.5: an expression's wire encoding is itself an
ATOMIC DER value subject to that section's disciplines, and the candidate-side
quantities (`K`, `maxFields`) are already enforced by the projection codec.

**Evidence for the three v0.2 values** (all [PROPOSED]; set from one expression,
E2 (§5.5), and the Fable reviewer's suggestions, so a second use case could move
any of them).

| Constant | Value | Evidence | Grade |
|---|---|---|---|
| `maxBindings` | 256 | E2 needs 22 (hand count of `Stage1Expressions.java:251-277` by the delta's author and independently by the Sonnet reviewer). The node ceiling alone would admit at most 512 bindings: each binding needs a body with at least one non-`REF` node and a `REF` that uses it, so at least 2 nodes per binding (no alias bodies, §5.5 item 3), and 2 × 512 = 1,024 = `maxExprNodes`. **[[EDITOR-Q 5: delta R18 says the structural maximum is 511 ("a 2-node body that holds a use"). The editor derives 512: the body of `B_0` holds no use, so `B_0` is 1 node and every other binding 2, and the main body may be a bare `REF` (§5.5 item 3), giving 1 + 2 × 511 + 1 = 1,024 nodes for n = 512. Confirm which.]]** 256 is a policy value. | E2: HYPOTHESIS (hand count, no bound E2 exists) |
| `maxEffectiveDepth` | 64 | E2 needs 48 with the hop counted (37 + 11 hops; both reviewers traced the same path, e2, col, rpx, th2, a2, a1, s, rs, theta, rho, xc to px); written-out E2 depth is 37 (`bench.log:56`, MEASURED by the Stage 1 run). Without a ceiling a 1,024-node expression could nest 993 levels (the Fable review's F1 construction, `REVIEW_LET_FABLE.md`). | 48: HYPOTHESIS (hand trace); 37: MEASURED |
| `maxExpandedCost` | 8,388,608 = 2^23 | E2 needs 5,835,619 (`bench.log:57`, MEASURED by the Stage 1 run: written-out `C(E)`, which equals `xCost` of the bound form by construction). Review F: "at least that (for example 2^23)". | MEASURED (for E2 only) |

*(v0.2: let delta C.2, R3, R18)*

**Fencepost convention.** `maxExprNodes`, `maxExprDepth`, `maxSelectorSteps`
and, from v0.2, `maxBindings`, `maxEffectiveDepth` and `maxExpandedCost` follow
STD-006 §4.5's inclusive convention: a value
**exactly equal** to the ceiling is accepted; the ceiling **plus one** is
rejected. §13 requires the conformance corpus to test the boundary explicitly
in both directions, not merely to probe values comfortably past the limit.

#### 10.3.1 Bindings: per-body scope and the per-binding table (NORMATIVE, v0.2)

`maxExprDepth` applies to each body separately, with a `REF` as a leaf of depth 1
below its parent. `maxExprNodes` applies to the SUM over all bodies, each `REF`
counting 1. `maxSelectorSteps` is unchanged.

For each body, in declaration order, an implementation computes and stores once
the entries below. A `REF(j)` reads entry `j`; nothing walks into `B_j`.

| Entry | Definition |
|---|---|
| `effDepth(i)` | the maximum over the nodes `v` of `B_i` of `localDepth(v)` if `v` is not a `REF`, and of `localDepth(v) + effDepth(j)` if `v` is `REF(j)`. The root has `localDepth` 1. |
| `xCost(i)` | Σ of the §10.2 cost over the non-`REF` nodes of `B_i`, plus Σ `xCost(j)` over its `REF(j)` nodes. A `REF` node adds nothing else. Checked 64-bit arithmetic (§10.6): an overflow means reject. |
| static type | the type §11.2 synthesises for `B_i`; a `REF(j)` has `B_j`'s type |
| static kind and static literal length (§10.2) | those of `B_j` for a `REF(j)`, so `cel.bind(k, "ACTIVE", f == k)` is charged as `f == "ACTIVE"` |

`effDepth(M)` and `xCost(M)` are those of the main body. **A `REF` counts as one
level.** The depth of the expansion would treat a `REF` and its body's root as one
level; a tree walker has both in progress at once, so the hop is counted. Then a
lazy walker's nesting, in node visits, equals `effDepth` exactly, an eager
evaluator's is at most `maxExprDepth`, and §6.9 rule 2 is true for every strategy.

**Monotonicity.** Every binding is referenced (§5.5 item 2), so `effDepth(M)` and
`xCost(M)` are at least those of every binding. A ceiling checked on `M` therefore
gives the same verdict as a ceiling checked on every binding; checking each binding
as it is computed (§12.3) means no accumulator can overflow first.

**Static walkers (normative for T2 and T6).** A cost, type, kind, depth or
literal-length computation over an expression MUST read a `REF(j)`'s attributes
from the table and MUST NOT descend into `B_j`. Each body is walked exactly once,
in declaration order.

*(v0.2: let delta C.1, C.2, C.5, R1, R11, R12)*

### 10.4 The resulting bound

With the tables above, `C(E) ≤ maxExprNodes × (1 + S × max literal length)`; in
the special case with no `contains` and no `in`-over-list-field term,
`C(E) ∈ O(|E| × S)`, but that special case is not the normative bound (§3.1) —
the normative bound is the `C(E) ≤ maxExprCost` gate itself (§10.5), computed
exactly, never approximated asymptotically. `C(E)` is computable by one
post-order walk of the AST, with no candidate.

### 10.5 `COST_BOUND` and the no-runtime-gate property

Verification (T6) MUST reject any expression with `C(E) > maxExprCost`, and any
expression with bindings whose `xCost` exceeds `maxExpandedCost` (§10.3.1) — those
rejections are the primary and sufficient enforcement, and they are *registration*
failures, not evaluation errors. Because `C(E)` is a true upper bound (§3.2) for a
conformant evaluator (§6.9 rule 1),
**a verified expression can never exceed it at evaluation**; an implementation
MAY nonetheless meter actual cost at runtime as defense in depth (e.g. while
evaluating expressions from a peer whose verification it does not trust), and if
its meter — initialized to the same `C(E)` — is exhausted it MUST yield
`COST_BOUND`. T5 MUST include an assertion that verified corpus expressions never
yield `COST_BOUND` on any implementation, metered or not: a trip would falsify
either the implementation's accounting or this section's tables, and both are
conformance bugs.

**The two gates (v0.2).** A conformant evaluator does at most `C(E)` work. The
second gate, `maxExpandedCost`, is the bound for a defective DEMAND-DRIVEN
component, one that re-evaluates a body at each use: its work is at most `xCost(M)`
≤ `maxExpandedCost`. An eager pass that re-expands every body is bounded by
Σ `xCost(i)` ≤ `maxBindings` × `maxExpandedCost` = 2^8 × 2^23 = 2^31 =
2,147,483,648 (arithmetic; review F D7). The runtime meter, if used, is
initialised to `C(E)` only. *(Consequence for the operator: the defective
demand-driven component's bound is 8,388,608 / 1,000,000 = 8.39 times the v0.1
per-record work bound `maxExprCost`; a conformant evaluator stays within `C(E)`.)*
Every exponential chain is inadmissible by construction. In the doubling value
chain `b_k = b_(k-1) + b_(k-1)`, `xCost(k) = 1 + 2 × xCost(k-1)` with
`xCost(0) = 1`, so `xCost(k) = 2^(k+1) − 1`: `xCost(22) = 8,388,607` is admitted and
`xCost(23) = 16,777,215` is rejected, long before the node ceiling would stop a
chain (about 340 steps at 3 nodes per step, 1,024 / 3; arithmetic).
*(v0.2: let delta C.3, R4, R5)*

### 10.6 Cost-arithmetic width (NORMATIVE)

`C(E)` and every intermediate per-node cost term (§10.2) MUST be computed in
checked or saturating arithmetic of width **≥ 64 bits** (or arbitrary
precision). Overflow of any intermediate term MUST itself be treated as
`C(E) > maxExprCost` — i.e., reject; an accumulator overflow MUST NOT be
allowed to wrap silently into a small or zero value.

**Rationale.** `maxCollection × maxScalarBytes = 65536 × 65536 = 2^32`
exactly (§10.3): the worst-case `contains` term (`1 + S × len(needle
literal)`, §10.2) and the worst-case `in`-over-list-field term (`1 + K ×`
per-element comparison cost) both reach this order. A 32-bit accumulator
wraps a term of exactly this size to 0, silently bypassing the entire gate
whose purpose (§10.1) is to make such a case unreachable at evaluation. A
cost model whose own accounting can overflow fails open exactly where §10.1
requires it to fail closed — worse than having no cost model at all, because
it looks enforced. §13 requires a corpus case built to trip this.

---

## 11. Abstract Syntax (NORMATIVE) — what T2 encodes

### 11.1 Node inventory

The complete node-type inventory. T2 assigns each a pinned wire tag; there are no
other node types.

| Node | Components | Arity | Typing rule |
|---|---|---|---|
| `LIT_BOOL`, `LIT_INT`, `LIT_DOUBLE`, `LIT_STRING`, `LIT_BYTES`, `LIT_NULL` | the value (double as 64-bit pattern) | 0 | the literal's type (`LIT_NULL` : `null_t`) |
| `LIST_LIT` | element nodes | 1…maxCollection | `list<T>` where every element : `T`, `T` scalar (§5.3.2) |
| `FIELD_REF` | a non-empty selector chain; each step either `Unqual(name)` or `Qual(className, fieldName)` | 0 | the projected field's type (dynamic); against a schema: the schema-declared type after §4.2 widening |
| `HAS` | one `FIELD_REF` | 1 | `bool` |
| `NOT` | operand | 1 | `bool → bool` |
| `NEG` | operand | 1 | `int → int` (checked) \| `double → double` |
| `AND`, `OR` | left, right | 2 | `bool × bool → bool` with §6.4 error absorption |
| `EQ`, `NE` | left, right | 2 | per §6.1 → `bool` |
| `LT`, `LE`, `GT`, `GE` | left, right | 2 | per §6.2 → `bool` |
| `ADD`, `SUB`, `MUL` | left, right | 2 | `int × int → int` (checked) \| `double × double → double` |
| `DIV` | left, right | 2 | as ADD; `int` division checked + `DIVISION_BY_ZERO` |
| `MOD` | left, right | 2 | `int × int → int` only (§4.3.1, §4.3.2) |
| `IN` | needle, list operand (`LIST_LIT` or `FIELD_REF`) | 2 | `T × list<T'> → bool` per §6.5 |
| `COND` | condition, then, else | 3 | `bool × T × T → T` (§6.7) |
| `CALL` | registry function id (§7.2 / §6.6), argument nodes | per signature | per the function's signature |
| `REF` *(v0.2)* | the binding index `j`: absolute, 0-based, `0 … maxBindings − 1` (§5.5) | 0 | the type of binding `B_j` (the per-binding type entry, §10.3.1) |

A `FIELD_REF`'s selector chain MUST NOT exceed `maxSelectorSteps` (§10.3)
steps: the chain lives inside this single arity-0 node, so it is not bounded
by `maxExprNodes` or `maxExprDepth` the way a sequence or nesting of nodes
would be — a dedicated ceiling closes that gap. Each selector step costs 1 in
the cost model (§10.2), normatively, not merely as a table aside.

`FIELD_REF` selector steps and `CALL` function identities are **enumerated wire
values, not free-form strings** where possible (function ids MUST be from the
pinned registry; field/class *names* are necessarily strings, matched exactly per
§8.3). There is deliberately no `MACRO`, no `COMPREHENSION`, no `IDENT`-as-
variable (a `REF` carries a binding index, never a name, and names are never on
the wire), no `ASSIGN`, no generic `APPLY` node — §3.2(vi)'s closed world is this
table. *(v0.2: let delta A.1)*

**Expression carrier (v0.2).** An expression is the pair (binding list, main
body); a `REF` has no meaning outside its binding list. Two expressions are equal
iff their binding lists are equal element by element and their main bodies are
equal. Equality and hashing compare bodies index by index, never by expansion,
because an expansion can be exponentially larger than the expression (§5.5).
An entry point that accepts a bare expression node, without its binding list,
MUST reject a tree that contains a `REF`, and two filters with the same main body
and different binding lists MUST NOT compare equal. *(Informative: a `REF` is an
index leaf, never a shared reference to the body's node, because a shared node
would make structural equality and hashing exponential in the expansion size; the
written-out E2 is 1,205,700 nodes, §5.5. An implementation that builds expressions
in process can validate backward-only indices, every binding referenced and the
no-alias rule when it constructs the binding list, so that an invalid list is
unrepresentable.)* *(v0.2: let delta B.3, C.5, R21)*

### 11.2 Static typing rules

Where a candidate schema is available (§12.4), typing is standard bottom-up
synthesis over the table above, with: numeric cross-type allowed **only** at
`EQ/NE/LT/LE/GT/GE` (§4.3.3); `null_t` allowed only at `EQ`/`NE` (§4.6);
`COND` branches unified by type equality (no coercion); overload selection
(`abs`, `min`, `max`, `size`, arithmetic) by operand types with no implicit
conversion anywhere. An expression that fails typing is rejected at
verification; dynamic evaluation enforces identical rules regardless (§6).
**Through references (v0.2):** each body is typed once, in declaration order, and
the type is stored in the per-binding table (§10.3.1); a `REF(j)` has `B_j`'s type,
read from the table, and the checker never descends into `B_j` at a use. *(v0.2:
let delta C.2, C.5, R12)*

### 11.3 Constraints on T2 (NORMATIVE for T2; the encoding itself is T2's design)

1. **Canonical form.** One AST, one encoding: T2's encoding MUST be DER-canonical
   under STD-006's rules (G1) — a decoder MUST reject any non-canonical encoding
   of an expression, and re-encoding a decoded AST MUST reproduce the input
   byte-for-byte. `LIT_DOUBLE` carries the strict-canonical 64-bit IEEE pattern
   (STD-006 §3.12's float/double discipline), so text-form parsing never sits on
   the evaluation path. The binding list is part of the canonical form: its order
   is significant and a `REF` index is a minimal-encoding integer. A record carries
   the lowest `formatVersion` that can express it (item 7). *(v0.2: let delta B.1,
   R13)*
2. **Closed node set, hard reject.** An unknown/reserved node tag, an unknown
   registry function id, an arity violation, or any structural violation of
   §11.1 MUST be a hard decode error — never skipped, defaulted, or tolerated
   (STD-006 §3.12 fence 3's discipline applied to expression nodes). Registry
   function ids are Appendix B §B.8.3's pinned table — one id per (function
   name × operand-type signature), see §7.2's corrected wire-identity note —
   never a name string resolved dynamically. The wire tag of `REF` is Appendix B
   §B.4.1's `[27]`. A `REF` whose index is not below its own body's position (or,
   in the main body, below the number of bindings), a binding body that is a bare
   `REF`, a binding that no body references, and a binding count above
   `maxBindings` are structural violations of §5.5 and are hard decode errors.
   *(v0.2: let delta B.1, R2)*
3. **Ceilings.** T2 MUST enforce `maxExprNodes`/`maxExprDepth`/
   `maxSelectorSteps` and, from v0.2, `maxBindings` and `maxEffectiveDepth` (and the encoded size implied by them) at decode, before
   allocation where practicable, composing with STD-006 §4.5's table (where
   the pinned constants of §10.3 live once ratified). Depth MUST be metered at
   every recursion of the node decoder (G10: the interior recursion, not just
   the entry); a `FIELD_REF`'s selector-chain length MUST likewise be metered
   as it is decoded, not merely bounded after the fact — the chain is internal
   to a single arity-0 node (§10.3, §11.1), so neither the node-count nor the
   depth check reaches it by construction. With bindings, the node count is ONE
   counter across all bodies (each `REF` counts 1); depth restarts at 1 for the
   root of each body, and a `REF` is a leaf, so decoding a `REF` recurses into
   nothing; `effDepth` is computed per body as the bodies are decoded, in
   declaration order (§10.3.1), while `xCost` and `C(E)` belong to verification
   (§12.3). *(v0.2: let delta C.1, B.1, R12)*
4. **Context and result-type declaration.** The wire form MUST carry the declared
   evaluation context (predicate | transform) and, for transforms, the declared
   result type, so §12.4 and §9.5 are checkable at the receiver without side
   agreements.
5. **No behaviour on the wire.** The encoding carries structure only — no
   flags, hints, or options that alter evaluation semantics. Semantics live in
   this standard exclusively; two expressions with equal ASTs are semantically
   identical everywhere, forever.
6. **Literal scalar length ceiling.** `LIT_STRING` and `LIT_BYTES` node values
   MUST be validated against `maxScalarBytes` (§10.3) at wire-decode time —
   the same ceiling, and the same enforcement point, that STD-006 §4.5 already
   applies (or is required to apply, §10.3's `[OPEN → T2]` note) to candidate
   projected scalar fields. Without this check on the literal side, §3.2(iii)'s
   cost argument and §3.3's condition on Claim 3 are not met, regardless of any
   candidate-side enforcement.
7. **Binding list and `formatVersion` (v0.2).** The wire form carries the binding
   list as an ORDER-SIGNIFICANT sequence of expression trees in the filter record,
   placed so that it cannot be confused with a node (Appendix B §B.3), and `REF`
   as a leaf node kind whose content is one minimal-encoding integer, the absolute
   0-based binding index (Appendix B §B.4.1). `formatVersion` is 1 for an
   expression with no bindings and 2 for one with bindings. A version 1 record MUST
   NOT contain a `REF` or a binding list. A version 2 record MUST contain a binding
   list with at least one binding. So "no bindings" has exactly one encoding:
   version 1. The general rule is that a record carries the lowest `formatVersion`
   that can express it. A version 1 peer rejects version 2 by its existing version
   check, so the change fails closed. A consumer MAY refuse version 2 entirely
   (Appendix B §B.9); that is a registration rejection, not a decode defect. The
   decode-time checks, in the order a decoder can make them: the index of a `REF` in
   body `i` is `< i` (in the main body, `< n`); the binding count is at most
   `maxBindings`, enforced as the list is read; a binding body is not a bare `REF`;
   the per-body ceilings (§10.3.1) and `effDepth` against `maxEffectiveDepth`; and,
   after the main body is decoded, every binding is referenced. A violation is a
   hard reject (item 2). *(v0.2: let delta B.1, R2, R12, R13)*

---

## 12. Static Verification Obligations (NORMATIVE) — what T6 enforces

Verification is layered; every layer is fail-closed (reject on any doubt, G6).
Layers 1–3 need no schema and MUST always run at wire ingest; layer 4 runs
whenever a schema is available.

### 12.1 Structural well-formedness

The decoded AST satisfies §11.1's table (node kinds, arities, component
presence), §5.3.1's constraints (call-target restrictions, `has`'s
field-designator argument, `field(...)`'s literal arguments, `contains`'s
literal needle, `in`'s operand shape), §5.3.2 (list-literal homogeneity,
non-emptiness), §5.5's binding constraints (backward-only `REF`, every binding
referenced, no alias body; *v0.2: let delta A.1, R2*), and §5.2's literal range rules (int literals in range after
folding, `LIT_DOUBLE` patterns being any binary64 value — including, on the wire,
non-finite patterns, which are legal literals only if T2 admits them;
**[PROPOSED]** T2 SHOULD reject non-finite `LIT_DOUBLE` — no textual literal
produces one, and a hand-crafted `Inf` literal serves only to probe §7.1's
domain rules; rejecting it keeps text↔wire round-tripping total).

### 12.2 Allowlist enforcement

Every `CALL`'s function id and every method reference resolves within §7.2/§6.6's
pinned registry **as of this standard's version**. A wire-supplied identifier
outside it — including a plausible-looking future function — is a hard reject,
never a warning, never a dynamic lookup (SOW T6's mandate: never a
wire-supplied/dynamic function name). **Resolved per SOW §5.3 (T6
implementation ruling, 2026-07-20): genuinely redundant** — the reference
verifier does **not** re-implement this check; its only API surface is wire
bytes, so every AST it holds already passed T2's decode-time allowlist
enforcement (§11.3 item 2). The ruling is contingent on that byte-only
surface: an AST-accepting verify entry point would re-open this decision.
§12.3's ceiling annotation is resolved the same way.

### 12.3 Ceilings and cost

`maxExprNodes`, `maxExprDepth`, `maxSelectorSteps` (**resolved per SOW §5.3,
T6 implementation ruling 2026-07-20: genuinely redundant** — not
re-implemented by the verifier, which only ever holds decode-produced ASTs;
only `C(E) ≤ maxExprCost` is additive here, being a post-decode computed
quantity T2 never enforces), and `C(E) ≤ maxExprCost` per §10. The computed
`C(E)` SHOULD be retained with the registered expression for diagnostics and
for §10.5's optional runtime meter.

**Layer split for bindings (v0.2).** *T2 decode (at ingest):* `formatVersion`; the
range and direction of every `REF` index; alias bodies; `maxBindings`; the per-body
node and depth ceilings; `effDepth` and `maxEffectiveDepth`; and, AFTER the main
body is decoded, "every binding is referenced". *T6 verify (at ingest,
schema-free):* `C(E) ≤ maxExprCost` and `xCost ≤ maxExpandedCost`, each checked
per binding as it is computed, with §10.6's checked arithmetic (so, beyond
`C(E)`, `xCost` is also additive at this layer). By monotonicity (§10.3.1) the
verdict equals a check on `M`; checking per binding means no accumulator can
overflow first. *Type checking with a schema* (§12.4) reads the per-binding type
table (§10.3.1). Conformance vectors (§13.1) say "reject at ingest", not which
layer. *(v0.2: let delta R12)*

### 12.4 Type checking — static where schema known, dynamic always **[recommended resolution of the SOW's decide-point]**

- **At registration**, when the consumer holds a governing schema (e.g. a
  template's class schema in Reggie/Outrigger, the declared observation class at
  a survey instrument), the expression MUST be type-checked against it
  (§11.2), the predicate result type checked to be `bool`, a transform's result
  type checked against its declaration, and §8.2 ambiguity checked against that
  schema. Failures reject the registration — errors surface to the author at
  registration time, not as silent per-candidate no-matches.
- **At evaluation, always**: candidates carry their own schemas, which may differ
  from the registration-time schema (STD-006 §11 evolution is a feature), so the
  dynamic rules of §6–§9 are the enforcement of record and MUST be applied
  regardless of any prior static acceptance. Static checking is an
  author-experience and early-rejection layer; it never licenses an evaluator to
  skip a dynamic check.

Both together — never static-only (schema skew would bypass it), never
dynamic-only (authors would learn of type errors one silent no-match at a time).

---

## 13. Conformance Requirements (NORMATIVE) — what T5 asserts

1. **Observable equivalence.** For every corpus case (expression wire form +
   candidate bytes + context), every conformant implementation MUST produce the
   identical outcome: identical match verdict (predicate), or bit-identical
   canonical DER result encoding (transform value — §4.3.4/§9.5), or the
   identical §9.2 error code. **No tolerances anywhere**, including
   transcendental results, per §7.5(a). If §7.5's decision changes to (b), this
   clause and every downstream byte-equality consumer must be re-ratified
   together — they are one decision.
2. **Rejection conformance.** Implementations MUST reject (and the corpus MUST
   include): non-canonical expression encodings; unknown node tags and function
   ids; arity/shape violations; ceiling violations (node count, depth, cost,
   selector-chain length — §10.3's `maxExprNodes`/`maxExprDepth`/`maxExprCost`/
   `maxSelectorSteps`); per §10.3's inclusive fencepost convention, an
   exact-boundary pair for **every** ceiling — accepted at exactly the limit,
   rejected at limit+1 (e.g. depth exactly 32 accepted, 33 rejected;
   selector-chain length exactly `maxSelectorSteps` accepted, `maxSelectorSteps`
   + 1 rejected) — not merely a "≥ limit" probe; a generated
   ≥`maxExprDepth`-deep nesting probe run against the real decoder (G13's
   run-the-adversarial-input, not reasoned-about); a corpus case whose true
   `C(E)` is just over 2^32 (e.g. the worst-case `contains` term, §10.2) that
   MUST be rejected — catching any implementation whose cost accumulator is
   narrower than the ≥64-bit width §10.6 requires; ill-formed constructs per
   §12.1; and, from v0.2, the binding rejections and the three new ceiling
   pairs (`maxBindings`, `maxEffectiveDepth`, `maxExpandedCost`) of §13.1.
3. **Mandatory semantic coverage** (minimum categories, each with pinned
   expected outcomes): checked-`int` edges (±2⁶³ boundaries, `−2⁶³/−1`,
   `−2⁶³ % −1`, literal-folding of `−9223372036854775808`); `int`×`double` exact
   comparison killer cases (§4.3.3); NaN/−0.0/±Inf comparison and boundary-
   canonicalization cases; supplementary-plane string ordering (§4.4's Java
   trap); bytes unsigned ordering; null-vs-absent-vs-present matrix (§4.6) and
   `has()`'s full table (§6.8); `&&`/`||` absorption incl. pinned error
   identity (§6.4's table row by row); `in` incl. empty projected list and
   error absorption; `?:` untaken-branch error non-propagation; every registry
   function's domain edges (each `DOMAIN` row of §7.2, signed zeros,
   `min/max(−0.0, 0.0)`, `sqrt(−0.0)`, `atan2` sign cases); the pinned
   constants' bit patterns (§7.3); ambiguity and qualified-resolution cases
   (§8.2–§8.4); fail-closed candidate cases (undecodable candidate, absent
   field ⇒ no-match); degenerate inputs throughout (empty strings, empty
   projected lists, one-element cases — G11); and, from v0.2, the binding
   categories of §13.1 (non-strict bindings, error identity through a slot, cost
   read through a reference, and the two instrumented vectors that gate §6.9
   rule 1).
4. **Transcendental correct rounding** is asserted by exact expected bit
   patterns in the corpus, generated from an arbitrary-precision oracle
   (e.g. MPFR) **independent of both implementations** (G12: bound to known-good,
   not self-consistent), with hard cases (arguments near branch points, huge
   arguments exercising argument reduction) explicitly included.
5. **Corpus versioning.** The corpus is versioned with this standard; an
   implementation claims conformance to (standard version, corpus version)
   pairs. The corpus is the deferred Rust evaluator's safety net
   (`SOW-CEL-Filter-Format.md` status note) — T4 MUST pass the same corpus
   version as T3 before any cross-language deployment. The vectors of §13.1 are
   added with v0.2, so the corpus version is bumped with the standard: an
   implementation of v0.2 claims conformance to the (standard version, corpus
   version) pair that includes them. *(v0.2: let delta E)*

### 13.1 Binding vectors and tests (NORMATIVE, v0.2)

Each vector states what a healthy implementation reads and the fault that flips
it. "Reject at ingest" means T2 decode or T6 verify; the layer is not part of the
vector (§12.3). The corpus asserts a rejection, not its reason, so a vector meant
to isolate one gate is built so that no other ceiling fires. `B_k` is the body of
binding `k` and `M` the main body. Every figure below is the delta's arithmetic,
re-derived by the v0.2 editor by hand; **none of it has been run** (UNVALIDATED
until the corpus exists).

**Structure and wire.**

| Vector | Healthy | Flips when |
|---|---|---|
| `REF` index equal to its own body's position, above it, or negative; a non-minimal `INTEGER` index | reject at ingest | the index check is missing or off by one |
| `REF` in a version 1 record; version 2 with no binding list or an empty one; version 1 carrying a binding list; `formatVersion` 3 | reject | a version or canonical-form check is missing |
| an unreferenced binding | reject | the §5.5 item 2 check is missing |
| an alias body: `B_0` a literal, `B_1 = REF(0)`, `M = REF(1)` (so every binding is referenced and only the alias rule can fire) | reject | bare-`REF` bodies are admitted |
| `contains` whose needle is a `REF` (the one excluded position the wire can represent, because `CALL` is one generic node, Appendix B §B.6.5) | reject | the needle-is-literal check reads through `REF` |
| `REF` indices 127, 128 and 255 (128 and above need a leading `0x00`, Appendix B §B.5 item 3) | accept | a decoder over-applies "no leading zero" |
| two structurally equal bodies, each referenced | accept; both evaluated by index | sharing by instance is assumed |

**Ceilings, each pair or case tripping ONE gate.**

| Vector | Healthy | Flips when |
|---|---|---|
| `maxBindings` pair: 256 and 257 bindings, each body an int literal, the main body a balanced `ADD` tree over one `REF` to each (767 and 770 nodes; effective depth 10 and 11 by the §10.3.1 formula, the editor's derivation where the delta says "depth about 11") | accept; reject | the `SEQUENCE OF` bound is missing or off by one |
| hop-depth pair: `effDepth` exactly 64 and 65, every body at depth ≤ 32, ≤ 4 bindings | accept; reject | hops are not counted (§10.3.1) |
| per-body depth 32 in every body, with effective depth ≤ 64 | accept | depth is counted through references |
| `maxExpandedCost` pair: `B_0 = x`, `B_k = REF(k-1) + REF(k-1)` for k = 1..22; `M = -REF(22)` (`xCost` = 2^23) and `M = -(-REF(22))` (`xCost` = 2^23 + 1) | accept; reject | the fencepost is off, or there is no expanded-cost gate (23 bindings, `effDepth` 47 and 48, about 70 nodes: no other ceiling fires) |
| 31-step doubling chain, main body `REF(30) + REF(30)`: `xCost` = 2^32 − 1 | reject | an implementation that checks only `M` uses a 32-bit or wrapping accumulator (it reads −1); `effDepth` 63, 31 bindings |

**Semantics and cost.**

| Vector | Healthy | Flips when |
|---|---|---|
| `cel.bind(a, 1/0, true)`; `cel.bind(a, 1/0, c ? a : 0)` with `c` false | `true`; `0` | bindings are evaluated strictly |
| `a && b` where both bodies error with different codes | the left code | error identity through a slot is wrong |
| `cel.bind(k, "ACTIVE", …)` with 16 comparisons `f_i == k`, `S` = 65,536 | accept (cost 16 × 7 plus the rest) | the literal length is not read through the reference: 16 × 65,537 = 1,048,592 > 1,000,000 |
| E2-bound: E2 written with bindings, run on a sample of records that takes BOTH `?:` branches (the 240-record Stage 1 sample takes one: review F N6, `bench.log:61`) | admitted; outcome equal by raw bits to the written-out walker's result on the same sample | the bound form or a ceiling is wrong |

**Instrumented vectors, the gate on §6.9 rule 1.** The harness wraps the candidate
projection in a counter of `fieldValue` reads; the projection is the host
interface in every language, so these vectors are not Java-only. The candidate
carries the field present and small (for example `x` = 1), so that no sum
overflows and no strict error skips a read (the editor's precondition, not the
delta's).

| Vector | Healthy reads | If not memoised |
|---|---|---|
| value chain, 22 steps: `B_0 = x` (an int field), `B_k = REF(k-1) + REF(k-1)` for k = 1..21, `M = REF(21) + REF(21)`; `xCost(M)` = 2^23 − 1 = 8,388,607 (admitted) | 1 read of `x` | 2^22 = 4,194,304 reads |
| error chain, 20 steps: `B_0 = (f / 0) > 0` with `f` an int field, `B_k = REF(k-1) \|\| REF(k-1)`, the main body the 20th step; `xCost(M)` = 6 × 2^20 − 1 = 6,291,455 (admitted); the outcome is `DIVISION_BY_ZERO` either way | 1 read of `f` | 2^20 = 1,048,576 reads, if errors are not stored |

The recurrences are `xCost(k) = 1 + 2 × xCost(k-1)`, from `xCost(0)` = 1 for the
value chain (so `xCost(k)` = 2^(k+1) − 1) and from `xCost(0)` = 5 for the error
chain (so `xCost(k)` = 6 × 2^k − 1); the reads are the number of paths from `M` to
`B_0`.

**Implementation tests, NOT corpus vectors** (each implementation runs them
against its own evaluator; labelled so because a corpus vector must be runnable
in every language):

- slot scope: two expressions, one evaluator, one candidate; and a record that
  throws, then another. Each outcome equals a fresh evaluator's. Flips when slots
  are not scoped or not reset (§9.6).
- expansion equivalence: random small bound expressions against their
  substitution, equal by raw bits. Flips on any memo, index or order fault. The
  expander is keyed by index (a name-keyed substitution that handles only some node
  kinds does not suffice; review F D8).
- cost equivalence: `xCost(M)` equals the `C(E)` of the expansion, computed on the
  `REF`-free tree, on the same random population (review F D10).
- the generator prints a count per class and MUST reach: an erroring body absorbed
  by `||`; a body used twice; a body used only in an untaken branch; a `REF` as a
  list element, as an `in` needle and as a `?:` condition (review F D8).

*(v0.2: let delta E, R6, R7, R8, R9)*

---

## 14. Worked Examples (informative; expected outcomes normative)

Candidate class for §14.1–§14.2, §14.5–§14.6: `com.example.SensorReading` with
fields `pressureHpa: double`, `station: String`, `status: String`,
`sampleCount: long`, `note: String (nullable)`.

### 14.1 Range predicate over a numeric field

```
pressureHpa >= 950.0 && pressureHpa < 1050.0
```

Type: `bool`. Candidate with `pressureHpa = 1013.25` → `true` → match. Candidate
with `pressureHpa = NaN` → both comparisons `false` (§4.3.4) → `false` →
no-match. Candidate whose schema lacks `pressureHpa` → `ABSENT_FIELD` on the
left conjunct; right conjunct also errors; §6.4 yields the left error →
no-match, fail-closed.

### 14.2 Compound predicate with string operations

```
status == "ACTIVE" && station.startsWith("GLS-")
    && !note.contains("deprecated") && sampleCount >= 30
```

Note `note` is nullable: on a candidate with `note = null`, `.contains` on a
`null` receiver is `TYPE_MISMATCH` → whole conjunction errors unless another
conjunct is `false` (absorption §6.4) → no-match either way. The
author's correct idiom if null `note` should match:

```
status == "ACTIVE" && station.startsWith("GLS-")
    && (!has(note) || !note.contains("deprecated")) && sampleCount >= 30
```

(`has(note)` is `false` for null or absent `note` (§6.8), so the parenthesized
disjunct is `true` without evaluating `contains` on `null` — and even if
evaluated, `||`-absorption discards the error.)

### 14.3 The Survey-zoot transform: bearing/elevation/distance → vector components

Candidate class `au.geo.survey.RawObservation`, fields `bearingDeg: double`
(azimuth from north, clockwise), `elevationDeg: double`, `slopeDistM: double`.
Transform, declared result type `list<double>` — the local East/North/Up vector:

```
[ slopeDistM * cos(radians(elevationDeg)) * sin(radians(bearingDeg)),
  slopeDistM * cos(radians(elevationDeg)) * cos(radians(bearingDeg)),
  slopeDistM * sin(radians(elevationDeg)) ]
```

Notes: `radians(elevationDeg)` is written twice per component pair — written out,
as in v0.1; since v0.2 a repeated subexpression may instead be bound once with
`cel.bind` (§5.5), and the `C(E)` of such a bound form is a different number that
this note does not compute *(v0.2: let delta F)*. For the written-out form above,
cost is charged syntactically (§10.2: 5
trigonometric calls at 32, 5 `radians` at 4, 5 multiplies, 8 field references,
list literal 3 → `C(E)` = 196 — far under `maxExprCost`, statically known). On a
candidate with `elevationDeg = NaN` (a garbage observation), `radians(NaN)` →
`DOMAIN` → the transform delivers the explicit error result (§9.5), never a
poisoned vector. The F0 screening predicate that would precede it
(`DESIGN-CorroborationFramework.md`):

```
has(pdop) && pdop <= 6.0 && snr >= 35.0 && elevationDeg > 10.0
```

### 14.4 Ambiguous-field rejection

Hierarchy: `com.example.Alpha` declares `timestamp: long`;
`com.example.Beta extends Alpha` also declares `timestamp: long` (legal and
coordination-free per STD-006 §3.9 — two independent namespaces). Candidate is a
`Beta`. Expression:

```
timestamp > 1700000000000
```

→ `AMBIGUOUS_FIELD` (§8.2: two namespaces declare `timestamp`; leaf-first order
does **not** silently pick `Beta`'s) → predicate no-match; at registration
against `Beta`'s schema, rejected outright with the ambiguity surfaced to the
author. Correct form:

```
field("com.example.Beta", "timestamp") > 1700000000000
```

And note the per-candidate nature: the unqualified expression evaluates fine
against a plain `Alpha` candidate (one namespace) and errors against `Beta`
candidates — which is precisely why registration-time static checking (§12.4)
against the template's schema is required where available.

### 14.5 Absent field ⇒ no-match; `has()` as the guard

Expression `batteryMv < 3300` against a candidate of an older schema without
`batteryMv`: reference → `ABSENT_FIELD` → no-match (fail-closed; STD-009 §8).
Schema-evolution-tolerant author intent "match if the field is present and low":

```
has(batteryMv) && batteryMv < 3300
```

Old-schema candidates → `false && …` → `false` → no-match with no error;
new-schema candidates evaluate normally. Intent "treat missing telemetry as
suspect (match)":

```
!has(batteryMv) || batteryMv < 3300
```

### 14.6 Overflow error case

```
sampleCount * 86400000000000 > 0
```

Candidate with `sampleCount = 200000`: the exact product 1.728 × 10¹⁹ exceeds
2⁶³−1 ≈ 9.22 × 10¹⁸ → `OVERFLOW` (§4.3.1 — never a wrapped negative, which
under wrapping semantics would have made `> 0` **false-negative silently**; the
error surfaces at registration testing or as a diagnosable no-match). The
author's fix — do the arithmetic in `double` deliberately:

```
double(sampleCount) * 86400000000000.0 > 0.0
```

---

## 15. Open Questions

1. **[RATIFIED (Peter, 2026-07-20)]** The platform function registry (§7.2) —
   the closed 15-function list is ratified as proposed. What remains open is
   only the pre-planned re-verification: any additions the Reggie/Outrigger
   integration discovers it needs (the STD-009 §11 vocabulary check found none,
   but B1 re-verifies) arrive as spec revisions per §7.1, never ad hoc.
2. **[RATIFIED (Peter, 2026-07-20)]** §7.5's correctly-rounded transcendental
   requirement — option (a) adopted as recommended, **including its stated cost
   on T3** (a correctly-rounded 7-function port for the JVM; `StrictMath` does
   not qualify), which T3's estimate MUST price in. The ≤1-ulp fallback is
   retired (§7.5's closing note).
3. **[OPEN → Outrigger integration SOW, B1]** Final name-resolution rule (§8.2's
   uniqueness-or-error is this standard's recommendation and default) and the
   filter-applicability key (§8.5: `className` vs `schemaDigest`).
4. **[OPEN → T2]** Pinning `maxExprNodes` / `maxExprDepth` / `maxSelectorSteps` /
   `maxScalarBytes` / `maxExprCost` (§10.3's proposed values) and, from v0.2,
   `maxBindings` / `maxEffectiveDepth` / `maxExpandedCost` in STD-006 §4.5's
   table (Appendix B §B.7 says which rows go there), and the §12.1 non-finite-
   `LIT_DOUBLE` rejection decision. *(v0.2: let delta C.2)*
5. **[OPEN → T6 (SOW §5.3)]** Whether T6's re-checks of T2's decode-time
   rejections (§12.3's belt-and-braces note) stay redundant-by-design or are
   consolidated once T2's actual decode behaviour exists.
6. **[OPEN → T7]** Whether evaluation additionally gets a process/core-isolation
   boundary for side-channel and resource defense in depth (§3.3; SOW §5.1).
7. **[OPEN — future revisions, each requiring a §3.2 theorem extension]** RE2-
   class `matches()` with pinned linear-time semantics; list indexing; map-field
   indexing; list/`object` equality; `floor`/`ceil`/`round`, `pow`/`exp`/`log`,
   `hypot`; ~~bounded `let` bindings~~ (**specified in v0.2**: §5.5, text form
   `cel.bind`, RL-JGDMS-0022 and RL-JGDMS-0023; not yet ratified); `Any`-field
   inspection; a `pi()` constant. *(v0.2: let delta F)*
8. **[OPEN → T5]** Choice of the arbitrary-precision oracle tooling (§13.4) and
   the corpus's storage/versioning location.
9. **[OPEN — pre-existing, not settled by v0.2]** `CANDIDATE_UNDECODABLE` scope.
   §8.1 item 2 says the error is yielded "for the whole evaluation". The Java
   walker returns it only at a field reference (`Evaluator.resolveFieldValue`,
   `Evaluator.java:214`, re-read at the v0.2 edit, base `7ccf90bb9`), so
   `true || f > 0` on an undecodable candidate MATCHES in the walker. Outrigger masks
   this: `FilterEval.java:240` rejects an undecodable projection before evaluating
   (re-read at the v0.2 edit); a direct `Evaluator` caller is not masked.
   Bindings inherit §8.1 as written (the whole evaluation). Recorded as a defect
   candidate against the Java evaluator, not a change to this standard.
   *(v0.2: let delta R25)*
10. **[OPEN — pre-existing, not settled by v0.2]** `in` over a literal list with an
    erroring element before an equal one. The delta (R26, after review F N4)
    records that §6.5 and §9.3 do not pin whether the later equal element absorbs
    the error; the Java walker absorbs it (`Evaluator.scanMembershipExpressions`,
    `Evaluator.java:629-643`, re-read at the v0.2 edit). Bindings make erroring
    elements more common (`x in [REF(0), 5]`). *(v0.2: let delta R26)*
11. **[OPEN → verification of the `cel.bind` text form]** Two HYPOTHESES until a CEL
    parser and evaluator are run (R17): whether a CEL parser accepts `has(x)` with a
    bare identifier, which v0.1 already uses (review F N2: if it does not, the
    subset claim is already approximate); and whether CEL evaluates
    `cel.bind(a, 1/0, true)` to `true` (review S N-6). A variant not adopted
    (review F N3): accept `cel.bind` anywhere CEL allows it and flatten at parse,
    with the same wire form. *(v0.2: let delta R17)*

---

## 16. References

- `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` — §3.7 (acyclicity), §3.9
  (per-class namespaces), §3.12 (wire type model — the §4.2 source set), §4.2
  (string encoding), §4.5 (profile ceilings), §7.8 (`MarshalledInstanceRecord`).
- `JGDMS-STD-009-Service-RemoteFunction-Annotation-Model-v0.1-DRAFT.md` — §8:
  the 2026-07-17 not-bytecode decision, the format recommendation, the
  2026-07-20 class-free-projection verification note (fail-closed rules adopted
  in §8.1/§9.4 here), and the 2026-07-20 ratification note.
- `SOW-CEL-Filter-Format.md` — the governing task breakdown; this document is T1.
- `DESIGN-CorroborationFramework.md` — the confirmed Survey-zoot F0
  transform/screening use case (§14.3's provenance).
- `JGDMS-Board-Reviewer-Guidance.md` — Part I (G1–G13), the review standard this
  document cites inline.
- CEL — Common Expression Language: language definition (`cel.dev`;
  `github.com/cel-expr` — spec relocated June 2026), consulted for grammar
  shape, operator semantics, error-absorption design, and macro semantics
  (excluded here). Verified against the language definition 2026-07-20 for the
  claims cited in §6.4, §4.3.1, §4.3.4, §6.7.
- Kubernetes CEL cost-budgeting for admission policies — precedent for §10's
  static cost estimation (informative).
- CORE-MATH project / crlibm lineage — correctly-rounded binary64 elementary
  functions; the implementation path for §7.5(a) (informative).
- RFC 2119 — key words for requirement levels.
- IEEE 754-2019 — binary64 arithmetic, `sqrt`/conversion correct rounding,
  `atan2` special cases.

---

## Appendix A — CEL Divergence Ledger

This appendix is the consolidated index promised by the editorial note and by
§4.1 and §4.3.4's inline cross-references: every point at which this
standard's pinned behaviour differs from real CEL (`cel.dev`) is flagged
individually where it occurs in the body of this document, and collected here
in one table for reviewers and future implementers who want the whole set at
a glance. Appendix A is an **index, not a new source of normative rules** —
where this table and an inline note appear to conflict, the inline,
section-cited rule governs.

| # | Divergence | STD-011 behavior | CEL behavior | Rationale |
|---|---|---|---|---|
| 1 | Unsigned integers | No `uint` type; the wire has none (§4.1) | Has a first-class `uint` type | Substrate-absent: STD-006's wire type model has no unsigned integer (§4.1) |
| 2 | `+` operator scope | `+` is numeric-only (`int`/`double`); string/bytes/list concatenation is `TYPE_MISMATCH` (§3.2(iv), §5.4 item 6, §6.3) | Overloads `+` for string, bytes, and list concatenation | Keeps allocation trivially bounded (§3.2(iv)) — a concatenating `+` scales with operand size in a way the closed-form cost model does not charge for elsewhere |
| 3 | Relation chaining | `a < b < c` is a **syntax error** — `Relation` admits at most one `RelOp` (§5.3) | Parses `a < b < c` and fails only at type-checking | G7 (construction beats vigilance): the mistake is made unrepresentable rather than merely ill-typed |
| 4 | Comprehension macros and `matches()` | `all`, `exists`, `exists_one`, `map`, `filter`, and `matches()`/regex are excluded — no syntax exists (§5.4 items 1–2) | Provides all of the above | Their cost is not linear in expression size (macros), or they risk ReDoS / non-bit-identical engines (`matches()`) — precisely the unbounded-feeling part of CEL this standard exists to exclude (§1.2, §5.4) |
| 5 | `bool` ordering | `<`/`<=`/`>`/`>=` on `bool` operands is `TYPE_MISMATCH` (§6.2) | Orders booleans (`false < true`) | Not part of the surveyed Reggie/Outrigger vocabulary; excluded rather than special-cased (G11) |
| 6 | NaN ordering | Pins all four ordered NaN comparisons to `false`, matching the equality half CEL already specifies (§4.3.4) | Specifies NaN equality (`NaN == x` is `false`) but leaves NaN ordering implementation-defined | Cross-language determinism (§9.1) requires every comparison outcome to be pinned, not merely the ones CEL happens to specify |
| 7 | `&&`/`\|\|` error identity | When both operands of `&&`/`\|\|` are errors and neither is determining, the **left** operand's error is reported (§6.4) | Uses the same commutative absorption shape, but leaves the choice of which error to report — when both operands error — to the implementation | Cross-language determinism: two conformant evaluators must report the identical error code, not merely "an error" (§9.1, §9.3) |
| 8 | Integer overflow/division edge cases | `(−2⁶³) / (−1)` and `(−2⁶³) % (−1)` are pinned to `OVERFLOW`, even though `(−2⁶³) % (−1)` is mathematically `0` and a JVM's native `%` returns `0` (§4.3.1) | Requires an error on range overflow in general, but does not pin this specific dividend/divisor identity; native JVM and Rust behaviour already disagree here | Keeps the two reference implementations (T3 Java, T4 Rust) trivially identical at one extra special case, rather than importing a JVM/Rust behavioural split into the spec (§4.3.1) |
| 9 | List/map homogeneity | `list<T>` is homogeneous **statically, dynamically, and on the wire** — a non-scalar or mixed-type list literal is a well-formedness error (§5.3.2), and a heterogeneous wire collection cannot arise because the projection codec widens each wire `Collection(E)` to `list<widen(E)>` for one element type `E` (§4.2); there is no map type at all in v0.1 (§4.2's `[OPEN → integration SOWs]` note) | Enforces list/map element-type homogeneity only **statically**, via its checker; an unchecked or `dyn`-typed CEL program may evaluate a genuinely heterogeneous list or map at runtime | A deliberate restriction, not an oversight: homogeneity enforced at every layer keeps `list<T>`'s per-element cost and typing uniform (§3.2(iii), §4.7) without a dynamic per-element type test on every access |

| 10 | Cross-type equality | `==`/`!=` between different types — outside the `int`×`double` numeric pair and the `null` rules — is `TYPE_MISMATCH` (§6.1) | Modern CEL's heterogeneous equality evaluates mixed-type `==` to `false` (`1 == "1"` is `false`, not an error) | Fail-closed (G6): a type-confused comparison is an authoring bug or schema skew to surface, not a silent `false`. In predicate context both dispositions are no-match — **except under negation** (`!("a" == 1)`: CEL matches, STD-011 errors ⇒ no-match), which is exactly where fail-closed matters |
| 11 | `has()` on a present-but-null field | `false` — null is treated as unset, proto3-flavour (§4.6, §6.8) | For map values CEL's `has()` is key-presence: a key mapped to `null` yields `true` | One guard idiom (`has(f) && …`) covers both null and absence over a field-map substrate whose nulls mean "no value"; key-presence semantics would force every guard to also test `f != null` |

Row 9 is the divergence the board review found missing from the original
inline notes; it was not previously flagged anywhere else in this document.
Rows 10–11 were added in the same revision cycle while answering the
format-naming question (is this a strict subset of CEL?): both behaviours were
already normative in §6.1/§4.6 but not yet ledgered as divergences. Should
this ledger ever be split apart in a future revision, row 9's substance MUST
travel with §4.1/§4.7/§5.3.2, and rows 10–11 with §6.1/§4.6, not be dropped.

**Subset summary (the naming question, answered precisely).** DETERMINISTIC CEL
is: (i) a **strict syntactic subset** of CEL — every well-formed DETERMINISTIC
CEL expression parses as CEL, treating §7's registry as CEL extension functions
(CEL's own sanctioned mechanism); (ii) a **deterministic refinement** — wherever
CEL leaves latitude (NaN ordering, competing error identity, evaluation order,
transcendental accuracy), this standard pins one behaviour, and with the
exceptions below the pinned behaviour is one a conformant CEL implementation
could exhibit; (iii) **not a pure semantic subset**: rows 8, 10, and 11 are the
only points where a DETERMINISTIC CEL evaluation can disagree with every
conformant CEL evaluation of the same expression — each deliberate, each
fail-closed or cross-language-alignment, each individually flagged inline.
Anything found to diverge from CEL and not in this ledger is a **defect in this
standard** (or in the implementation under test) — to be fixed or ledgered,
never tolerated as drift.

---

*End of v0.2-DRAFT. No production code was written or modified in producing this
document. The v0.1 text was board-reviewed 2026-07-20 (two adversarial seats:
boundedness/security, cross-language determinism; both SOUND-WITH-FIXES; synthesis
applied the consolidated 10-item fix list) per `SOW-CEL-Filter-Format.md` §4. The
v0.2 additions (every passage tagged `(v0.2: let delta ...)`) were reviewed on
2026-11-14 by a Sonnet and a Fable reviewer as a delta, before this text was
edited from it; this edited text has NOT been re-reviewed.*
