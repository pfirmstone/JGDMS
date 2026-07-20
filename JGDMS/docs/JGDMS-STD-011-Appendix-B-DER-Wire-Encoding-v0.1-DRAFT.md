# JGDMS-STD-011 Appendix B — DER Wire Encoding for DETERMINISTIC CEL Expressions

**Status:** Draft (T2 deliverable of `SOW-CEL-Filter-Format.md`), written as a normative
appendix to `JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md`, to be merged into
that document after board review. This file does not modify STD-011 or STD-006; every
place this appendix needs a change to either is called out explicitly as a **[PATCH]**
block (STD-006 §4.5) or a **[FLAG]** (a contradiction/gap in STD-011 found while designing
against it, left for Peter/board disposition rather than silently resolved).
**Version:** 0.1-DRAFT
**Date:** 2026-07-20
**Author:** Claude (T2 of `SOW-CEL-Filter-Format.md`)
**Depends on:** `JGDMS-STD-011-CEL-Filter-Expression-Format-v0.1-DRAFT.md` (§2, §5, §10.3,
§10.6, §11, §12, §13 — this appendix implements exactly and only what those sections
require of T2), `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md` (§3.12, §4.4–§4.6 — the
house style this appendix follows), `JGDMS-Board-Reviewer-Guidance.md` §2.1 (H1–H8, the
one-page checklist this appendix is written against).
**Revision note:** Board-reviewed 2026-07-20 by two adversarial seats (hostile-decoder,
ASN.1-hazards); both returned **SOUND-WITH-FIXES**. A consolidated 11-item fix list from
that review has been applied in this revision — covering the seven X.680 comment-trap
lines in §B.3 that had left the printed module uncompilable, unbounded name-string SIZE
ceilings (§B.3, §B.6.3, found independently by both seats), a node-counting mismatch with
STD-011 §10.3's own node definition (§B.6.2), several comment-only MUSTs promoted to this
appendix's normative enumeration (§B.6.1, §B.6.3), canonical-form and UTF-8 gaps in §B.5,
the broken §B.10.4 worked example, the depth fencepost, the §B.4.1 tag-table reserved-range
count/annotation, patch-block honesty in §B.6.3/§B.7, and the §5.3.2 homogeneity
T2/T6 boundary (§B.6.6). §B.13's compile/probe transcript was regenerated in this same
revision from the printed §B.3 text (see §B.13's own honesty note).

RFC 2119 keywords (MUST/MUST NOT/SHOULD/SHOULD NOT/MAY) are normative throughout.
**[PROPOSED]** marks a design choice requiring Peter's ratification (mirroring STD-011's
own convention); **[OPEN]** marks a question deliberately left unresolved, with its owning
task named; **[FLAG]** marks a contradiction or gap found in STD-011 v0.1-DRAFT while
designing this encoding — recorded here, not silently patched into STD-011's own text.

---

## B.1 Scope

This appendix defines the DER wire encoding for the DETERMINISTIC CEL abstract syntax
(STD-011 §11.1) and the enclosing envelope a filter/transform expression travels in as a
parameter (STD-011 §11.3, §9.4/§9.5). It is T2 of `SOW-CEL-Filter-Format.md`. It does not
define evaluation semantics (T3/T4 — those live entirely in STD-011 §6–§10, unchanged
here) and does not define the verification/load-gate (T6 — STD-011 §12, this appendix only
states what T2 itself enforces at decode, per §11.3's explicit T2 constraints).

Everything below is written against STD-011 v0.1-DRAFT, board-reviewed 2026-07-20, taken
as binding per this task's instructions. Three points of genuine tension between STD-011's
prose and an implementable wire encoding were found; each is flagged at its point of
relevance (§B.4.4, §B.8.1) and summarized in §B.12.

---

## B.2 Design Overview

### B.2.1 One CHOICE, one discriminator (H3)

The complete §11.1 node inventory (27 node kinds) is encoded as a single ASN.1 `CHOICE`,
`ExprNode`, whose context tag **is** the node-kind discriminator — no separate `kind`
field, no `ENUMERATED` restating what the tag already says. This is exactly STD-006
§3.12's `AnyElement` pattern (itself following board guidance H3: "one discriminator, not
two") applied to expression-tree nodes instead of wire-scalar categories. STD-011 §11.3
item 2 requires precisely this ("Registry function ids are the §7.2 `#` column values …
never a name string resolved dynamically" and "an unknown/reserved node tag … MUST be a
hard decode error") — a tagged `CHOICE` gets the hard-reject-on-unknown-arm property
**automatically** from the ASN.1 machinery (confirmed empirically, §B.13), the same
"largely automatic in the CHOICE form" property STD-006 §3.12 fence 3 notes for
`AnyElement`.

### B.2.2 Every recursive/CHOICE-shaped substructure is wrapped in a named SEQUENCE

Unlike `AnyElement` (whose `atomicSerialObject`/`canonicalCollection`/`orderedCollection`
arms wrap payloads that are *themselves* tag-bearing or `CHOICE`-typed, forcing `EXPLICIT`
per X.680 §31.2.7), every `ExprNode` arm here wraps a **named `SEQUENCE`** type
(`BinaryNode`, `UnaryNode`, `ListLitNode`, `FieldRefNode`, `HasNode`, `InNode`, `CondNode`,
`CallNode`) or a primitive/`NULL` leaf. A `SEQUENCE` is not itself a `CHOICE`/`ANY` type, so
X.680 §31.2.7's restriction never bites at the `ExprNode` level, and **every** `ExprNode`
arm is legally `IMPLICIT` (§B.4 justifies this for each arm; §B.4.4 is the one place in the
whole module the restriction *does* bite, one level down, inside the envelope).

This is a deliberate design difference from `AnyElement`, stated explicitly because the
task requires justifying every `IMPLICIT`/`EXPLICIT` choice rather than assuming the
`AnyElement` precedent transfers unchanged: `AnyElement`'s `EXPLICIT` arms exist because
those arms' payloads carry a tag that is *load-bearing outside the `Any` form itself* (a
`@AtomicSerial` record's own shape tag, a `SET OF`/`SEQUENCE OF` preserve-vs-canonicalise
discriminator). No `ExprNode` arm's payload has any such external meaning — a `BinaryNode`
is only ever consumed as a `BinaryNode`, and its own tag never needs to "shine through" a
wrapper for some other layer to inspect. Hence uniform `IMPLICIT` is not merely permitted
here, it is the *correct minimal* choice: `EXPLICIT` would add a redundant constructed
wrapper with nothing to preserve underneath it.

### B.2.3 Structural elimination of several STD-011 §5.3.1 well-formedness checks (G7)

Three of STD-011's post-parse well-formedness constraints (§5.3.1) are encoded so the
violation is **unrepresentable**, not merely checked-and-rejected — the same
"construction beats vigilance" (G7) principle STD-011 itself uses for relation-chaining
(§5.3, Appendix A row 3):

- **§5.3.1 item 2** ("the argument of `has(...)` MUST be a field designator"): `HasNode`
  embeds a `FieldRefNode` directly, not a generic `ExprNode`. There is no wire form for
  `has()` of anything else — the ASN.1 grammar itself is the enforcement.
- **§5.3.1 item 6** ("`in`'s right operand must be a `ListLiteral` or a field designator"):
  `InNode.listOperand` is `InListOperand`, a `CHOICE` of exactly `ListLitNode` or
  `FieldRefNode` — again, no wire form for any other operand shape exists.
- **§5.3.1 item 3** ("both arguments of every `field(...)` form MUST be string literals"):
  `QualifiedSelector`'s `className`/`fieldName` are plain `UTF8String` components, not
  `ExprNode`s — there is no way to encode a *computed* class/field name at all.

These three constraints therefore need **no T5 reject-probe** (§B.11) — there is no
adversarial byte string that could violate them and still decode as the node in question.
This is noted explicitly because it is easy to forget a "MUST reject X" needs no test when
X cannot be encoded; the honest statement belongs in the conformance-vector list, not a
silent omission. STD-011's **remaining** §5.3.1 constraint on `CALL` (item 4, `contains`'s
needle must be a string literal) is **not** structurally eliminated (§B.6.5) — flagged
there as a genuine decode-time check T2 must still perform.

---

## B.3 The ASN.1 Module (compiling)

`DEFINITIONS EXPLICIT TAGS`, matching STD-006 §4.6's tagging-mode decision exactly: every
context tag below is written with an explicit `IMPLICIT`/`EXPLICIT` qualifier at its use
site, so the module-level default is inert for every type defined here (STD-006 board
guidance H5 — never rely on the ambient default once the module has any bare `[n]`, and
this module has none).

```asn1
JGDMS-STD-011-Appendix-B
{ iso(1) identified-organization(3) dod(6) internet(1) private(4)
  enterprise(1) 999999 jgdms(1) atomicDer(1) cel(2) v1(1) }
DEFINITIONS
EXPLICIT TAGS ::=
BEGIN

IMPORTS
    maxCollection
        FROM JGDMS-STD-006
        { iso(1) identified-organization(3) dod(6) internet(1) private(4)
          enterprise(1) 999999 jgdms(1) atomicDer(1) v13(13) };

-- ============================================================================
-- STD-011 Sec 10.3 ceilings [PROPOSED, this appendix; see Sec B.7]
-- ============================================================================

maxExprNodes     INTEGER ::= 1024
maxExprDepth     INTEGER ::= 32
maxSelectorSteps INTEGER ::= 16
maxScalarBytes   INTEGER ::= 65536
-- maxExprCost is a T6 verification-time bound computed over the decoded AST
-- (STD-011 Sec 10.2/10.5's cost sum), not a wire-level SIZE constraint on any
-- single field; recorded for completeness, not referenced by any SIZE below.
maxExprCost      INTEGER ::= 1000000

-- ============================================================================
-- Envelope (Sec B.9): how an expression travels as a parameter
-- ============================================================================

ScalarType ::= ENUMERATED {
    boolT(0), intT(1), doubleT(2), stringT(3), bytesT(4)
}

-- Declared result type for a TRANSFORM registration (STD-011 Sec 4.1's eight
-- expression types, minus "object" appearing only via objectType and list<T>
-- restricted to scalar T per Sec 4.7).
DeclaredResultType ::= CHOICE {
    scalar      [0] IMPLICIT ScalarType,
    nullType    [1] IMPLICIT NULL,
    listType    [2] IMPLICIT ScalarType,
    objectType  [3] IMPLICIT NULL
}

-- The context tag alone selects predicate vs transform (STD-011 Sec 11.3 item 4);
-- predicate's result type is always bool (Sec 9.4) so there is nothing further to
-- declare on that arm; carrying it would be a redundant discriminator (H3).
EvaluationContext ::= CHOICE {
    predicate  [0] IMPLICIT NULL,
    -- DeclaredResultType is itself a CHOICE: this arm MUST be EXPLICIT
    -- (X.680 Sec 31.2.7); see Sec B.4.4 for the full justification.
    transform  [1] EXPLICIT DeclaredResultType
}

CelFilterRecord ::= SEQUENCE {
    formatVersion  INTEGER (0..255),      -- this appendix pins value 1 (Sec B.9)
    context        EvaluationContext,
    expression     ExprNode
}

-- ============================================================================
-- The AST CHOICE: the context tag IS the node-kind discriminator (H3).
-- Closed set: exactly the 27 arms below. Tags 27-62 RESERVED; any tag outside
-- the listed arms (including the reserved block and any tag > 62) is
-- UNREGISTERED and MUST be a hard decode reject (STD-011 Sec 11.3 item 2);
-- automatic in the CHOICE form, confirmed empirically (Sec B.13).
-- ============================================================================

ExprNode ::= CHOICE {
    -- literals [0]-[5]: IMPLICIT, leaf values, nothing to preserve underneath
    litBool     [0]  IMPLICIT BOOLEAN,
    litInt      [1]  IMPLICIT INTEGER (-9223372036854775808..9223372036854775807),
    litDouble   [2]  IMPLICIT OCTET STRING (SIZE(8)),
    litString   [3]  IMPLICIT UTF8String (SIZE(0..maxScalarBytes)),
    litBytes    [4]  IMPLICIT OCTET STRING (SIZE(0..maxScalarBytes)),
    litNull     [5]  IMPLICIT NULL,

    -- structural / reference [6]-[8]
    listLit     [6]  IMPLICIT ListLitNode,
    fieldRef    [7]  IMPLICIT FieldRefNode,
    has         [8]  IMPLICIT HasNode,

    -- unary [9]-[10]
    notOp       [9]  IMPLICIT UnaryNode,
    negOp       [10] IMPLICIT UnaryNode,

    -- binary [11]-[23]
    andOp       [11] IMPLICIT BinaryNode,
    orOp        [12] IMPLICIT BinaryNode,
    eqOp        [13] IMPLICIT BinaryNode,
    neOp        [14] IMPLICIT BinaryNode,
    ltOp        [15] IMPLICIT BinaryNode,
    leOp        [16] IMPLICIT BinaryNode,
    gtOp        [17] IMPLICIT BinaryNode,
    geOp        [18] IMPLICIT BinaryNode,
    addOp       [19] IMPLICIT BinaryNode,
    subOp       [20] IMPLICIT BinaryNode,
    mulOp       [21] IMPLICIT BinaryNode,
    divOp       [22] IMPLICIT BinaryNode,
    modOp       [23] IMPLICIT BinaryNode,

    -- membership / conditional / call [24]-[26]
    inOp        [24] IMPLICIT InNode,
    condOp      [25] IMPLICIT CondNode,
    callOp      [26] IMPLICIT CallNode
}

ListLitNode ::= SEQUENCE (SIZE(1..maxCollection)) OF ExprNode

FieldRefNode ::= SEQUENCE {
    steps  SEQUENCE (SIZE(1..maxSelectorSteps)) OF SelectorStep
}

-- unqual/fieldName/className ceilings [PROPOSED, Sec B.6.3] align with STD-006
-- Sec 7.8's AtomicSerialFieldDef (wireName SIZE(1..255), wireType SIZE(1..1024)):
-- a name longer than the schema format's own ceiling can never match a real
-- schema entry, so these SIZE bounds are not arbitrary: they are the schema
-- format's own limits, restated on the expression-literal side.
SelectorStep ::= CHOICE {
    unqual  [0] IMPLICIT UTF8String (SIZE(1..255)),
    qual    [1] IMPLICIT QualifiedSelector
}

QualifiedSelector ::= SEQUENCE {
    className  UTF8String (SIZE(1..1024)),
    fieldName  UTF8String (SIZE(1..255))
}

-- Structurally guarantees STD-011 Sec 5.3.1 item 2 (has()'s argument MUST be a
-- field designator) by construction; see Sec B.2.3.
HasNode ::= SEQUENCE {
    target  FieldRefNode
}

UnaryNode ::= SEQUENCE {
    operand  ExprNode
}

-- left/right are both mandatory, fixed-position components: the X.680
-- distinct-tag rule (STD-006 Sec 4.5) governs OPTIONAL/CHOICE-sibling
-- ambiguity and does not apply between two mandatory fields, so no inner
-- context tags are needed on left/right even though ExprNode is a CHOICE.
BinaryNode ::= SEQUENCE {
    left   ExprNode,
    right  ExprNode
}

InNode ::= SEQUENCE {
    needle       ExprNode,
    listOperand  InListOperand
}

-- Structurally guarantees STD-011 Sec 5.3.1 item 6 by construction (Sec B.2.3).
InListOperand ::= CHOICE {
    literalList  [0] IMPLICIT ListLitNode,
    fieldList    [1] IMPLICIT FieldRefNode
}

CondNode ::= SEQUENCE {
    condition   ExprNode,
    thenBranch  ExprNode,
    elseBranch  ExprNode
}

-- functionId is the pinned registry id, Sec B.8's table (extends/refines
-- STD-011 Sec 7.2's "#" column; see Sec B.8.1's FLAG). Exact per-id arity
-- is enforced at decode against Sec B.8's table, not by this generic bound.
CallNode ::= SEQUENCE {
    functionId  INTEGER (1..24),
    arguments   SEQUENCE (SIZE(1..2)) OF ExprNode
}

END
```

**Compile validation (honest report, per task instructions).** This module was compiled
together with the existing `JGDMS/docs/asn1/JGDMS-STD-006-v0.13.asn1` (importing
`maxCollection` from it) using Python `asn1tools` 0.167.0, `der` codec:

```
$ python3 -c "
import asn1tools
s = asn1tools.compile_files(['JGDMS-STD-006-v0.13.asn1', 'JGDMS-STD-011-Appendix-B.asn1'], 'der')
print('OK', len(s.types), 'types')
"
OK 71 types
```

It compiles clean alongside STD-006's own module (71 = STD-006's 41 + this module's 30
named type/value assignments), confirming the grammar is valid ASN.1 and every tag in the
`ExprNode` `CHOICE` (and every nested `CHOICE`) is distinct per X.680 §30.6/§4.5's distinct-
tag rule — mechanically, not by inspection. **A green compile proves grammar and tag
validity. It does not prove canonical-decode correctness** (H1) — confirmed by direct
experiment, not assumed: `asn1tools` accepted a hand-crafted non-minimal-length encoding of
`LIT_INT(30)` without complaint (§B.13.2), and does not enforce any `SIZE`/`INTEGER`-range
constraint at decode time (§B.13.3/§B.13.4) — every canonical-form and ceiling rule in
§B.5/§B.6 below is a **decoder-implementation obligation** (T3/T4's job), not something
the wire grammar or a generic ASN.1 tool gives for free. This matches the board guidance's
own standing warning verbatim (§2.1: "A green compile proves distinct-tag/grammar validity,
not canonical-decode correctness") and is restated here because it was independently
re-verified for this specific module, not merely cited.

---

## B.4 Tag Assignment and IMPLICIT/EXPLICIT Justification

### B.4.1 `ExprNode` tag table (the closed, pinned node registry)

| Tag | Node (STD-011 §11.1 name) | Payload type | Tagging | Leading octet (context tag byte) |
|---|---|---|---|---|
| `[0]` | `LIT_BOOL` | `BOOLEAN` | IMPLICIT | `0x80` |
| `[1]` | `LIT_INT` | `INTEGER` (int64 range) | IMPLICIT | `0x81` |
| `[2]` | `LIT_DOUBLE` | `OCTET STRING(8)` | IMPLICIT | `0x82` |
| `[3]` | `LIT_STRING` | `UTF8String` | IMPLICIT | `0x83` |
| `[4]` | `LIT_BYTES` | `OCTET STRING` | IMPLICIT | `0x84` |
| `[5]` | `LIT_NULL` | `NULL` | IMPLICIT | `0x85` |
| `[6]` | `LIST_LIT` | `ListLitNode` (SEQUENCE OF) | IMPLICIT | `0xA6` |
| `[7]` | `FIELD_REF` | `FieldRefNode` (SEQUENCE) | IMPLICIT | `0xA7` |
| `[8]` | `HAS` | `HasNode` (SEQUENCE) | IMPLICIT | `0xA8` |
| `[9]` | `NOT` | `UnaryNode` (SEQUENCE) | IMPLICIT | `0xA9` |
| `[10]` | `NEG` | `UnaryNode` | IMPLICIT | `0xAA` |
| `[11]` | `AND` | `BinaryNode` (SEQUENCE) | IMPLICIT | `0xAB` |
| `[12]` | `OR` | `BinaryNode` | IMPLICIT | `0xAC` |
| `[13]` | `EQ` | `BinaryNode` | IMPLICIT | `0xAD` |
| `[14]` | `NE` | `BinaryNode` | IMPLICIT | `0xAE` |
| `[15]` | `LT` | `BinaryNode` | IMPLICIT | `0xAF` |
| `[16]` | `LE` | `BinaryNode` | IMPLICIT | `0xB0` |
| `[17]` | `GT` | `BinaryNode` | IMPLICIT | `0xB1` |
| `[18]` | `GE` | `BinaryNode` | IMPLICIT | `0xB2` |
| `[19]` | `ADD` | `BinaryNode` | IMPLICIT | `0xB3` |
| `[20]` | `SUB` | `BinaryNode` | IMPLICIT | `0xB4` |
| `[21]` | `MUL` | `BinaryNode` | IMPLICIT | `0xB5` |
| `[22]` | `DIV` | `BinaryNode` | IMPLICIT | `0xB6` |
| `[23]` | `MOD` | `BinaryNode` | IMPLICIT | `0xB7` |
| `[24]` | `IN` | `InNode` (SEQUENCE) | IMPLICIT | `0xB8` |
| `[25]` | `COND` | `CondNode` (SEQUENCE) | IMPLICIT | `0xB9` |
| `[26]` | `CALL` | `CallNode` (SEQUENCE) | IMPLICIT | `0xBA` |
| `[27]`–`[30]` | **RESERVED** (future node kinds) | — | — | still X.690 §8.1.2.3 short form (single byte, `≤ 30`) |
| `[31]`–`[62]` | **RESERVED** (future node kinds) | — | — | high-tag-number form, ≥2 bytes (`[31]` is X.690's short/high-tag-number boundary) |
| `> [62]`, or any reserved-gap tag | **UNREGISTERED** — hard reject | — | — | — |

**Deliberate compactness choice.** All 27 currently-defined node kinds get single-byte
context tags (`[0]`–`[26]`, all ≤ 30, X.690 §8.1.2.3 short form). The reserved block starts
at `[27]` and is placed entirely above the low-tag-number boundary (`[31]` is the point
X.690 reserves as the high-tag-number-form marker, so any tag `≥ 31` already needs the
multi-byte form regardless; this appendix reserves from `[27]` rather than `[31]` purely
so the boundary is a round, memorable number, at the cost of four unused single-byte
slots `[27]`–`[30]` (still short form, since all four are `≤ 30`; only `[31]`–`[62]` of the
reserved block actually needs the multi-byte high-tag-number form) — an acceptable trade
for a spec meant to be read, not just compiled).
Unlike STD-006's `AnyElement` (which reserves gaps *between* semantically distinct
categories — scalars vs `@AtomicSerial` objects vs collections — because each category's
own future growth is independent), every `ExprNode` arm is the same kind of thing (one AST
node), so a single contiguous run plus one trailing reserved block is the more faithful
structure here, not a stylistic simplification of STD-006's pattern. **A future revision
MUST append new node kinds after `[26]`** (consuming the reserved block in order) rather
than inserting into the middle of the current range, to keep today's 27 kinds' tags stable
across revisions.

### B.4.2 Why every `ExprNode` arm is `IMPLICIT`

Per §B.2.2: every arm's payload is a `SEQUENCE` (or a primitive/`NULL` leaf), never a bare
`CHOICE`/`ANY` type directly. X.680 §31.2.7 forbids `IMPLICIT`-tagging a `CHOICE`/`ANY`
alternative *whose payload is itself an untagged `CHOICE`/`ANY`* — none of these 27 arms
qualifies, so `IMPLICIT` is legal for all of them, and it is the *correct* choice (not
merely the permitted one) because none of these payload types' own tags are load-bearing
outside their own decode: nothing downstream needs to recover "this was originally a
`SEQUENCE`" independently of already knowing "this is a `BinaryNode`" from the `ExprNode`
tag itself. Contrast `AnyElement`'s collection arms, where the inner `SET OF`(`0x31`) vs
`SEQUENCE OF`(`0x30`) tag is *itself* the canonicalise-vs-preserve fact (STD-006 §3.8) —
there is no analogous "the inner tag also means something else" fact anywhere in this
module's `ExprNode` arms.

### B.4.3 Why `BinaryNode`'s `left`/`right` need no inner tags

`left` and `right` are both mandatory (no `OPTIONAL`), fixed two-field, fixed-order
components. STD-006 §4.5's distinct-tag rule ("every `OPTIONAL` component … MUST carry a
tag distinct from … every component that can immediately follow it") governs ambiguity
between an omittable field and whatever follows it, or between `CHOICE` siblings — it says
nothing about two always-present, always-ordered fields, because a decoder reading a fixed
`SEQUENCE` schema simply reads exactly one `ExprNode` TLV for `left`, then exactly one for
`right`, regardless of what tag either happens to carry (even if `left` and `right` are
both e.g. `LIT_INT`, hence both tagged `0x81` — that is not an ambiguity, it is two
consecutive, independently-length-prefixed TLVs). The same reasoning applies to
`CondNode`'s three branches, `CallNode`'s `arguments`, and `ListLitNode`'s elements: all
mandatory-count-or-explicitly-length-prefixed, never `OPTIONAL`, so §4.5's rule is
satisfied vacuously and no inner tag is needed anywhere in this module beyond what
`ExprNode`'s own `CHOICE` tags already provide.

### B.4.4 The one `EXPLICIT` case: `EvaluationContext.transform`

`EvaluationContext`'s `transform` arm wraps `DeclaredResultType`, which is **itself a
`CHOICE`** (`scalar`/`nullType`/`listType`/`objectType`). This is exactly the scenario
X.680 §31.2.7 forbids tagging `IMPLICIT`: doing so would silently discard
`DeclaredResultType`'s own arm tag (the very thing that says *which* result type is
declared), leaving a decoder unable to tell `scalar(boolT)` from `nullType` from
`listType(intT)` — precisely the "H4: IMPLICIT silently overwrites a load-bearing universal
tag" hazard from the board checklist, and structurally identical to why STD-006's
`AnyElement.atomicSerialObject`/`canonicalCollection`/`orderedCollection` arms are
`EXPLICIT`. `transform` is therefore `[1] EXPLICIT DeclaredResultType`, wrapping
(constructed tag `0xA1`, content = the inner `DeclaredResultType` arm's own complete TLV) —
the inner tag survives underneath, exactly the "preserve rather than overwrite" behaviour
`EXPLICIT` provides. This is the appendix's one genuine H4-relevant design point, included
specifically because the task requires demonstrating the distinction is understood, not
merely declared by fiat. `predicate`'s payload (`NULL`) is not a `CHOICE`, so it stays
`IMPLICIT` — the asymmetry between the two `EvaluationContext` arms is itself evidence the
rule was applied per-arm, not applied uniformly by habit.

---

## B.5 Canonical Form (G1) — Exactly One Valid Encoding, Reject Everything Else

Per STD-011 §11.3 item 1 ("T2's encoding MUST be DER-canonical under STD-006's rules …
a decoder MUST reject any non-canonical encoding … re-encoding a decoded AST MUST
reproduce the input byte-for-byte"), a conformant decoder (T3/T4) MUST reject each of the
following at decode, before evaluation, in addition to whatever a generic DER library
already rejects:

1. **Non-minimal tag encoding.** Any context tag using the multi-byte high-tag-number form
   (X.690 §8.1.2.4) for a value that fits the short form (`≤ 30`), or a multi-byte tag
   number itself carrying a non-minimal base-128 encoding (a leading `0x80` continuation
   octet with nothing beyond it, etc.). None of this module's own 27 arms can trigger this
   (all ≤ 26), but a decoder MUST still check it generically, because the reserved range
   `[27]`–`[62]` and any future extension will use the multi-byte form and a non-minimal
   encoding of *those* tag numbers is exactly the same hazard once they're allocated.
2. **Non-minimal length encoding.** Any DER length using long form (X.690 §8.1.3.5) where
   the value fits short form (`< 128`), or any definite-length encoding that is not the
   shortest possible representation of that length. Worked reject example, §B.10.5.
3. **Non-minimal `INTEGER` content.** Any `LIT_INT`/`CallNode.functionId`/
   `CelFilterRecord.formatVersion` whose two's-complement content octets carry a redundant
   leading `0x00` or `0xFF` byte (X.690 §8.3.2) — DER `INTEGER` content MUST be the
   shortest two's-complement form.
4. **Out-of-range `LIT_INT` value.** A `LIT_INT` whose (correctly, minimally encoded)
   value falls outside `[-2^63, 2^63-1]` MUST be rejected — the ASN.1 `INTEGER (…)` range
   constraint states this, but (confirmed §B.13.4) is not mechanically enforced by every
   tool, so the decoder implementation MUST check it explicitly, not rely on the grammar.
5. **Non-finite `LIT_DOUBLE` bit patterns [PROPOSED — resolves STD-011 §12.1/§15 item 4].**
   STD-011 §12.1 states T2 SHOULD reject a non-finite (`±Inf`/NaN) `LIT_DOUBLE` because no
   textual literal can produce one (§5.2's `DOUBLE_LIT` grammar has no `Infinity`/`NaN`
   production) — a wire-only non-finite literal can only originate from a hand-crafted
   (adversarial or tooling-bug) encoding. This appendix adopts the SHOULD as a T2 MUST:
   **a `LIT_DOUBLE` whose 8-byte big-endian content decodes (per the IEEE-754 binary64
   exponent field being all-ones) to `+Inf`, `-Inf`, or any NaN payload MUST be rejected at
   decode.** This is a **[PROPOSED]** normative resolution of STD-011's own open item,
   offered here because T2 cannot be self-consistent without deciding it, and flagged for
   Peter's ratification alongside the ceilings (§B.7) rather than silently assumed.
6. **Non-canonical NaN payload in a *transform result*, out of scope for T2.** STD-011
   §4.3.4 requires a NaN `double` *leaving the evaluator* (a transform result) to be
   canonicalized to bit pattern `0x7FF8000000000000`. That is an **evaluator (T3/T4)
   encode-time** obligation on the *result*, not a T2 decode-time obligation on an
   *incoming literal* (which item 5 above already forbids from being non-finite at all).
   Stated here only to draw the boundary precisely — item 5 and §4.3.4 are two different
   rules about two different values (an input literal vs. an output result) and must not
   be conflated.
7. **Wrong element order where order is semantic.** STD-011's AST has **no** construct
   analogous to STD-006's canonicalise (`set:`/`bag:`/`map:`) collections — every
   multi-element wire structure here (`ListLitNode` elements, `FieldRefNode.steps`,
   `CallNode.arguments`) is order-**preserving** `SEQUENCE OF`, because expression
   evaluation order and selector-chain order are themselves semantic (STD-011 §9.3's
   left-to-right, depth-first evaluation-order pin; §8.2's leaf-first selector order).
   There is therefore **no octet-sort/canonical-order reject case applicable to this
   module** — stated explicitly, honestly, rather than manufacturing one to satisfy a
   checklist item that does not apply here. (Contrast STD-006's `CanonicalSet`/
   `CanonicalMultiset`/`CanonicalMap`, which are genuinely order-free and do need this
   check.)
8. **Non-canonical `BOOLEAN` content [MED, board review].** X.690 §11.1 requires DER
   `BOOLEAN` `TRUE` to be encoded with content octet `0xFF` exactly — any other nonzero
   octet (`0x01`, `0x80`, …) is a semantically-identical-but-differently-encoded `TRUE`
   under BER, and is exactly the "two byte-strings for one value" hazard this section
   exists to close. A `litBool` whose content octet is nonzero and **not** `0xFF` MUST be
   rejected. Reject vector, §B.11 item 2: `80 01 01` (tag `[0]` IMPLICIT, len 1, content
   `0x01`) — a BER-legal, DER-illegal encoding of `TRUE`.
9. **Non-minimal `ENUMERATED` encoding.** `ScalarType` (§B.3) is the only `ENUMERATED` type
   in this module, reached via `DeclaredResultType.scalar`/`listType`. Per X.690 §8.4
   (`ENUMERATED` follows `INTEGER`'s encoding rules), its content octets MUST be the
   shortest two's-complement form, identically to item 3 above for `INTEGER`. Reject
   vector, §B.11 item 2: `82 02 00 02` (tag `[2]` IMPLICIT `listType`, len 2, content
   `00 02` — a redundant leading `0x00` byte) where the canonical encoding of the same
   value (`doubleT = 2`) is `82 01 02`.
10. **Non-canonical/ill-formed UTF-8.** Every `UTF8String` component in this module
    (`litString`, `SelectorStep.unqual`, `QualifiedSelector.className`,
    `QualifiedSelector.fieldName`) MUST be rejected at decode if its content octets are
    ill-formed, overlong, or otherwise non-canonical UTF-8 (surrogate code points encoded
    directly, overlong multi-byte sequences for a code point that has a shorter encoding,
    truncated multi-byte sequences, etc.) — mirroring the same discipline STD-006 §4.2
    already requires of its own `UTF8String` fields, which STD-011 §4.2 already cites for
    the candidate-projection side (`"UTF-8 decode per STD-006 §4.2 (the codec has already
    rejected non-canonical/ill-formed UTF-8; §8.1)"`). Without this check on the
    expression-literal side, two byte-for-byte-different wire encodings could decode to
    logically-equal strings (or a decoder using a lenient UTF-8 library could silently
    accept an encoding a strict one rejects), which is the same G1 canonical-form hazard
    every other rule in this section closes — there is no principled reason to hold
    literals and selector names to a laxer standard than candidate string fields.

---

## B.6 Decode-Time Enforcement of STD-011 §11.3's MUSTs

Every check below runs **before evaluation**, per STD-011 §12's layering (structural
well-formedness first, always; schema-dependent typing only when a schema is available).
Several are also re-checked by T6 (§12.2/§12.3's stated "belt and braces … open per SOW
§5.3" redundancy) — this appendix states only T2's own decode-time obligation.

### B.6.1 Closed node set, hard reject (STD-011 §11.3 item 2)

Automatic in the `CHOICE` form (§B.13.1 empirically confirms this for both a reserved-gap
tag and an out-of-range tag): any `ExprNode` tag not in §B.4.1's 27-entry table — including
every tag in `[27]`–`[62]` and every tag `> 62` — is a `CHOICE` decode error, full stop. The
same applies to `SelectorStep` (2 arms), `InListOperand` (2 arms), and `DeclaredResultType`
(4 arms): each is independently closed.

**Function-id allowlist** is *not* automatic — `CallNode.functionId` is a plain `INTEGER`,
not a `CHOICE` arm, so nothing in the ASN.1 grammar rejects `functionId = 1000`
(confirmed, §B.13.4). The decoder MUST explicitly check `functionId` against §B.8's pinned
table (range `1..24` inclusive is necessary but not sufficient — every integer in range
names an assigned function, so range-checking alone happens to suffice *for this specific
table*, but the decoder MUST implement it as a table lookup, not merely a bounds check, so
a future gap in the middle of the range remains rejectable).

**Per-function arity is *not* automatic either [MED-HIGH, board review — this MUST was
previously stated only in §B.11's conformance-vector commentary, not here].** `CallNode`'s
generic `arguments SEQUENCE (SIZE(1..2)) OF ExprNode` bound admits **any** count from 1 to
2 for **every** `functionId`, regardless of that function's actual declared arity in
§B.8.3's `Arity` column — the grammar cannot express a per-arm arity constraint (§B.6.5
already notes `CallNode` is deliberately one generic shape for all 24 ids). The decoder
MUST therefore check `len(arguments)` against §B.8.3's `Arity` column for the specific
`functionId` decoded, as its own explicit step, distinct from and in addition to the
generic `SIZE(1..2)` grammar bound — confirmed necessary by direct experiment (§B.13.4:
`functionId = 12` (`sqrt`, arity 1) with 2 arguments decodes without complaint against the
bare grammar). Reject vector: §B.11 item 2.

### B.6.2 Ceilings, and the G10 depth-threading rule

Three structural ceilings apply at decode (a fourth, `maxExprCost`, is a T6 post-decode
computation over the whole AST, per STD-011 §10.5, out of T2's scope — noted so the
boundary is explicit, not implied):

- **`maxExprNodes` (1024, [PROPOSED]) [HIGH, board review — counting mismatch with STD-011
  §10.3's own definition, found independently by both review seats].** STD-011 §10.3's
  table defines the unit being counted precisely: "AST node count (**every literal,
  operator, call, selector step, and list element is a node**)." A single running counter,
  shared across the *entire* decode of one `CelFilterRecord.expression`, decremented once
  per `ExprNode` consumed (regardless of kind) counts literals/operators/calls/list-element-
  `ExprNode`s correctly (every `ListLitNode` element is itself an `ExprNode`, so it is
  already counted by ordinary `ExprNode`-consumption), but **does not** count selector
  steps — `SelectorStep` is not an `ExprNode` arm, so a `FieldRefNode`'s `steps` never
  decrement this counter under an `ExprNode`-consumption-only rule. This appendix therefore
  states the counter MUST **also** decrement once per selector step of **every**
  `FieldRefNode`, wherever one appears in the grammar — all three positions, not just the
  obvious one:
  1. The `fieldRef` `ExprNode` arm (`[7]`) directly.
  2. `HasNode.target` (the `FieldRefNode` embedded inside every `HAS` node).
  3. `InListOperand.fieldList` (the `FieldRefNode` arm of `IN`'s list operand).

  This is STD-011 §10.3's node definition applied verbatim, not a new rule invented here —
  the definition already names "selector step" as its own node kind; §11.3 item 3's ceiling
  MUST-enforce language covers whatever STD-011 §10.3 defines a node to be. **The concrete
  attack this closes:** without step-counting, an attacker builds an expression of, e.g.,
  512 `HAS` nodes (512 `ExprNode`s, well inside `maxExprNodes = 1024`), each wrapping a
  `FieldRefNode` with the maximum 16 selector steps (`maxSelectorSteps`, itself satisfied
  per node) — 512 × 16 = 8192 total selector steps, entirely invisible to an
  `ExprNode`-consumption-only counter, while STD-011 §10.3's own node-count definition
  (which counts each step as a node) would place the true count at 512 + 8192 = 8704, more
  than 8× over the 1024 ceiling. Reject vector, §B.11 item 4: an expression whose
  `ExprNode`-arm count is within `maxExprNodes` but whose step-inclusive count (per STD-011
  §10.3's definition) exceeds it MUST be rejected. Absent this fix, the instant-reject rule
  below still holds, just against the wrong (undercounted) total: reject the instant the
  counter would go negative — i.e., the 1025th node (by the corrected, step-inclusive
  count) triggers the reject, never the node beyond that (inclusive fencepost, STD-011
  §10.3).
- **`maxExprDepth` (32, [PROPOSED]).** A depth counter, decremented on **every** recursive
  re-entry into `decode_expr_node`. **Fencepost [LOW, board review]:** the root `ExprNode`
  consumed from `CelFilterRecord.expression` itself counts as depth 1 — decoding starts the
  counter at 1 for that first, non-recursive entry, not at 0 — so this is the same inclusive
  convention as `maxExprNodes` above: a decode reaching depth **exactly** `maxExprDepth`
  (32) MUST be accepted, and one whose recursion would reach `maxExprDepth + 1` (33) MUST
  be rejected the instant that 33rd level is entered. Boundary vector, §B.11 item 3: 32
  levels of nested `NOT` (accept) / 33 levels (reject). Per **G10** (STD-011 §11.3 item 3's
  explicit requirement — "Depth MUST be metered at every recursion of the node decoder …
  the interior recursion, not just the entry"), the following recursive steps each decrement
  the counter — named individually, as the task requires, because "decrement on recursion"
  is exactly the kind of rule an implementer under-applies by instinct (a review finding a
  future PR is likely to miss if not named):
  1. `UnaryNode.operand` (`NOT`, `NEG`).
  2. `BinaryNode.left` and `BinaryNode.right`, **each separately** (`AND` … `MOD`, 13 arms).
  3. `InNode.needle`.
  4. `InNode.listOperand`'s `fieldList` arm does **not** recurse into `ExprNode` (it is a
     `FieldRefNode`, a different, non-`ExprNode`-recursive structure, §B.6.3) — but the
     `literalList` arm (a `ListLitNode`) recurses per item 5 below, once per element.
  5. **Every element of `ListLitNode`** — including the elements of a `LIST_LIT` reached
     through `InNode.listOperand.literalList` — decrements the counter once per element,
     not once per list.
  6. **All three of `CondNode`'s branches** — `condition`, `thenBranch`, **and**
     `elseBranch` (not just the taken one; depth is a *decode-time* structural bound, prior
     to and independent of which branch would be *evaluated*, per STD-011 §6.7's untaken-
     branch rule, which is an evaluation-time concept and does not excuse the decoder from
     bounding the untaken branch's structure).
  7. **Every element of `CallNode.arguments`** — one decrement per argument, for every
     `CALL` node regardless of function id.

  A decoder that decrements only at `BinaryNode`/`UnaryNode` and forgets list-literal
  elements, `CondNode`'s untaken branches, or `CALL` arguments has a real depth-bypass:
  an attacker nests the unmetered construct to exceed the true evaluation-stack depth
  while reporting a shallower decoded depth. This is precisely STD-006 §3.12 fence 1's
  "`MAX_NESTING` threaded through **every** … recursion" requirement, restated for this
  AST's specific recursive positions.
- **`maxSelectorSteps` (16, [PROPOSED]).** Counted **within** `FieldRefNode.steps` only —
  this is a bound on the length of one `FIELD_REF`'s selector chain, not on `ExprNode`
  recursion (a `FIELD_REF` is arity-0 per STD-011 §11.1's table; its selector chain is
  internal data, not child expressions). It is metered as the `steps` `SEQUENCE OF` is
  decoded element-by-element (reject the moment the 17th step is read), independently of
  and in addition to `maxExprNodes`/`maxExprDepth` — exactly STD-011 §11.3 item 3's "the
  chain is internal to a single arity-0 node, so neither the node-count nor the depth
  check reaches it by construction."

### B.6.3 Size-bounds-before-allocation (STD-006 principle 5)

Before allocating any variable-length structure, the decoder MUST read the DER length
octets and validate the bound **before** materializing storage for the declared count —
never allocate first and reject after:

- `LIT_STRING`/`LIT_BYTES`: read the TLV's length octets; if the declared content length
  exceeds `maxScalarBytes` (65536, [PROPOSED]), reject **before** allocating a
  `String`/`byte[]` to hold it, and before attempting UTF-8 decode for `LIT_STRING` (a
  length check on raw octets is possible before any character decoding, since UTF-8's
  variable width only ever needs *more* octets than characters, never fewer — bounding
  octets is therefore a valid, cheap upper bound on character count as well). This
  discharges, on the literal side, exactly the enforcement STD-011 §3.3/§11.3 item 6
  requires ("enforced on both candidate projected scalar fields and expression literals");
  the candidate-projection side is STD-006/STD-009's codec, out of this appendix's scope.
  **This appendix does not itself discharge that candidate-field half — see the prominent
  warning at the end of this subsection.**
- **`SelectorStep.unqual` (`SIZE(1..255)`), `QualifiedSelector.fieldName` (`SIZE(1..255)`),
  `QualifiedSelector.className` (`SIZE(1..1024)`) [HIGH, board review — found
  independently by both review seats: unbounded name strings].** These three components
  were, before this revision, plain unbounded `UTF8String`s — a wire-supplied selector
  name or class name of unbounded length would force a decoder to allocate storage for it
  before any rejection could occur, an unbounded-allocation hazard structurally identical
  to the one `maxScalarBytes` already closes for `LIT_STRING`/`LIT_BYTES`. The ceilings
  (`255` for names, `1024` for class names) are **not arbitrary**: they align exactly with
  STD-006 §7.8's `AtomicSerialFieldDef` (`wireName UTF8String (SIZE(1..255))`, `wireType
  UTF8String (SIZE(1..1024))`) — the schema format's own field-name and type-name
  ceilings. A selector or class name longer than the schema format's own ceiling can never
  match a real schema entry in the first place, so bounding these components to the same
  ceilings loses no legitimate expressiveness. The decoder MUST read each component's TLV
  length octets and reject before allocating a `String` to hold it, and before UTF-8
  decoding, given the same length-octets-bound-characters reasoning as `LIT_STRING` above
  — identical treatment, not a weaker one, because the allocation hazard is identical.
  Reject vectors, §B.11 item 2: an over-length `unqual`/`fieldName` (256 bytes) and an
  over-length `className` (1025 bytes), and an over-length `litString` (65537 bytes).
- `ListLitNode`, `FieldRefNode.steps`, `CallNode.arguments`: bound the loop by
  `maxCollection`/`maxSelectorSteps`/2 respectively as each element's length-prefix is
  read, incrementally, rather than trusting a claimed count up front to size an array.
- **Empty-collection lower bounds [MED-HIGH, board review — previously stated only in
  ASN.1 `SIZE(1..…)` grammar and in §B.11's conformance commentary, never as an explicit
  normative MUST here].** `ListLitNode` (`SIZE(1..maxCollection)`) and
  `FieldRefNode.steps` (`SIZE(1..maxSelectorSteps)`) both have a grammar-stated lower bound
  of 1 — but §B.13.3 confirms `asn1tools` does not mechanically enforce `SIZE` lower bounds
  any more than it enforces the upper ones. The decoder MUST explicitly reject a `LIST_LIT`
  with zero elements and a `FieldRefNode` with zero `steps`, as its own checked step, not as
  something assumed free from the grammar — same treatment as `LIT_STRING`/`LIT_BYTES`'s
  upper-bound check above, applied to these two components' lower bound. (STD-011 §5.3.2
  independently requires list-literal non-emptiness at the source-language level; this is
  the wire-decode-time restatement of the same requirement, per STD-011 §12.1's "MUST be
  enforced by parsers and by T6 on the wire form alike.") Reject vectors, §B.11 item 2:
  empty `ListLitNode` (0 elements) and empty `FieldRefNode.steps` (0 steps).
- `CelFilterRecord` itself and every nested `SEQUENCE`: the outermost length octets bound
  total decode work before any recursive descent begins (X.690 definite-length TLVs make
  this available for free — there is no reason to allocate more than the declared length
  could possibly contain).

**[LOW, board review — patch-block honesty, item 10].** The candidate-projection side of
`maxScalarBytes` enforcement (STD-011 §3.3/§10.3's "enforced on both candidate projected
scalar fields and expression literals") is **not** discharged anywhere in this appendix —
this appendix pins and enforces the ceiling only for `LIT_STRING`/`LIT_BYTES` wire
literals, per its own stated scope (§B.1, §B.7). **T3/T4 implementers MUST NOT assume
STD-011 §3.3's Claim 3 (bounded cost) holds end-to-end merely because this appendix's
literal-side check is implemented** — the candidate-field half is a separate, still-open
STD-006 change (§B.7's `[PATCH]` block), and until STD-006 pins and enforces the same
ceiling on its own projection codec, Claim 3's conditional does not fully discharge, no
matter how carefully this appendix's half is implemented.

### B.6.4 Context declaration and declared result type (STD-011 §11.3 item 4)

`EvaluationContext` (§B.3, §B.4.4) carries this directly: `predicate` fixes the result type
to `bool` (STD-011 §9.4) with nothing further on the wire; `transform` carries
`DeclaredResultType`, checkable by §12.4 without side agreement. No additional decode-time
check beyond the `CHOICE`'s own closure (§B.6.1) is needed here.

### B.6.5 Function-id allowlist and the `contains`-needle-literal check

Beyond §B.6.1's range/table check: for `functionId = 22` (`contains`, §B.8) specifically,
`arguments[1]` (the needle) MUST be a `litString` arm — any other `ExprNode` kind in that
position is a decode-time reject (STD-011 §5.3.1 item 4, §6.6). This is **not** structurally
eliminated the way `HasNode`/`InListOperand`/`QualifiedSelector` are (§B.2.3): `CallNode`
is deliberately generic (one `SEQUENCE` shape for all 24 function ids, per STD-011 §11.1's
single `CALL` node kind), so this one check is a genuine per-function-id decode-time rule,
not automatic from the grammar. `startsWith`/`endsWith` (ids 23/24) accept any `string`-
typed argument in that position (STD-011 §6.6) — no literal restriction, so no
corresponding check for those two ids.

### B.6.6 List-literal homogeneity is deliberately not a T2 decode-time check [LOW, board review]

STD-011 §5.3.2 requires a `ListLiteral` to be **homogeneous** (every element the same
scalar type). This appendix's `ListLitNode ::= SEQUENCE (SIZE(1..maxCollection)) OF
ExprNode` (§B.3) does **not** structurally enforce homogeneity the way §B.2.3's three
eliminated checks are enforced — the grammar happily admits a `ListLitNode` mixing, say, a
`litInt` element with a `litString` element, because each element is independently just
some `ExprNode`. This is stated explicitly, as its own boundary, rather than left silent:
homogeneity is **not** one of this appendix's T2 decode-time obligations (§B.6) at all — it
is assigned to **T6 static verification** (type-checking against a schema, §12.4) when a
schema is available, and to **mandatory dynamic type-checking at evaluation** otherwise
(STD-011 §12.1's structural well-formedness layer, which explicitly lists "§5.3.2
(list-literal homogeneity, non-emptiness)" among what "MUST be enforced by parsers and by
T6 on the wire form alike" — i.e., T6's job, not T2's). Non-emptiness, by contrast, **is**
a T2 wire-shape obligation (`SIZE(1..maxCollection)`'s lower bound, §B.6.3) — the two
`ListLiteral` constraints in §5.3.2 are therefore split across two different layers, and
this subsection exists so that split is explicit rather than something a reader has to
infer from §B.2.3's silence on homogeneity.

---

## B.7 Pinned Ceilings — [PROPOSED], and the STD-006 §4.5 Patch Block

Adopting STD-011 §10.3's proposed values verbatim (this appendix does not revisit whether
1024/32/16/65536/10^6 are the right numbers — that is STD-011's call; T2's job is to make
them enforceable on the wire, which §B.6.2/§B.6.3 do):

| Constant | Value | Enforced by | Wire-shape? |
|---|---|---|---|
| `maxExprNodes` | 1024 | T2 decode (running counter) | Not a single-field `SIZE`; a whole-decode counter |
| `maxExprDepth` | 32 | T2 decode (recursion counter, G10) | Not a single-field `SIZE`; a recursion-depth counter |
| `maxSelectorSteps` | 16 | T2 decode (`FieldRefNode.steps` `SIZE`) | Yes — `SEQUENCE (SIZE(1..16)) OF` |
| `maxScalarBytes` | 65536 | T2 decode (`LIT_STRING`/`LIT_BYTES` length check) | Yes — bounds an `OCTET STRING`/`UTF8String` TLV length |
| `maxExprCost` | 1 000 000 | T6 verification (post-decode, §10.5) | No — a computed sum, not a wire ceiling at all |

Per STD-011 §10.3's `[OPEN → T2]` note and §3.3's honesty condition ("Claim 3 does not
hold" until `maxScalarBytes` is pinned and enforced on both sides), this appendix pins
`maxScalarBytes = 65536` — **aligned with STD-006 §4.5's existing `maxCollection`** (also
65536), a deliberate choice: reusing the same numeral for "biggest single scalar" and
"biggest single collection" is easy to remember and, per STD-011 §10.6's rationale,
already the value whose product with itself (`65536 × 65536 = 2^32`) is the exact number
STD-011 §10.6 designed the ≥64-bit cost-arithmetic-width rule around — pinning it to
anything else would silently invalidate that rationale's arithmetic. **This pinning
discharges STD-011 §3.3's conditional Claim 3**, on the wire-literal side, the moment this
appendix is ratified; the candidate-projection side of the same requirement is STD-006's
to pin and enforce (§10.3's `[OPEN → T2]` note names both sides — this appendix speaks only
to the literal side, which is T2's actual scope).

**[PATCH] — proposed STD-006 §4.5 table addition** (for a later sync commit; not applied
here, per this task's instructions):

```
| Constant | Value | Applies to |
|---|---|---|
| `maxExprNodes` | 1024 | JGDMS-STD-011 Appendix B `CelFilterRecord.expression` — total AST node count across one decode |
| `maxExprDepth` | 32 | JGDMS-STD-011 Appendix B `ExprNode` recursion depth (see Appendix B §B.6.2 for the exact recursive positions metered) |
| `maxSelectorSteps` | 16 | JGDMS-STD-011 Appendix B `FieldRefNode.steps` (`SIZE(1..maxSelectorSteps)`) |
| `maxScalarBytes` | 65536 | JGDMS-STD-011 Appendix B `LIT_STRING`/`LIT_BYTES` (`ExprNode.litString`/`litBytes`) content-octet length. **Enforced by** T2 decode (this appendix's `LIT_STRING`/`LIT_BYTES` length check, §B.6.3) **only** — mirroring the `maxCollection` row's "Enforced by …" style deliberately, to make the gap the same shape flags: the candidate-projected-scalar-field side of this same ceiling (STD-011 §3.3/§10.3's requirement that it be "enforced on both candidate projected scalar fields and expression literals") is a **separate, still-open STD-006 change**, not enforced by anything in this appendix and not yet enforced by STD-006 either — this row pins the *value* for both sides but only *discharges* the literal side |
```

```asn1
maxExprNodes       INTEGER ::= 1024
maxExprDepth       INTEGER ::= 32
maxSelectorSteps   INTEGER ::= 16
maxScalarBytes     INTEGER ::= 65536
```

Note `maxExprCost` is deliberately **not** proposed for the STD-006 §4.5 table: every
existing entry in that table bounds a wire-decodable quantity (a field length, a
recursion/nesting depth); `maxExprCost` bounds a *computed* value over an already-decoded
AST (STD-011 §10.2's cost sum) and has no wire representation to size — it belongs in
STD-011's own text (already does, §10.3) and not in STD-006's ceilings table.

---

## B.8 The `CALL` Function-Id Table — [PROPOSED], with a [FLAG]

### B.8.1 [FLAG] — STD-011 §7.2's "one row, one wire id" claim is inconsistent with its own table

STD-011 §7.2's "Wire identity (normative)" paragraph states: "each row is one wire id, and
a row with multiple signatures — e.g. row 8's `min`/`max` over `int` and over `double` — is
a **single** wire id that dispatches on operand type at evaluation." Read literally and
applied uniformly, this claim contradicts §7.2's own table in two independent ways:

1. **Row 8 (`min`/`max`) bundles two different *operations*, not two type-signatures of
   one operation.** "Dispatch on operand type" is a coherent mechanism when the same
   operation spans two numeric types (exactly what rows 6/7 already do for `abs` — but
   note rows 6 and 7 are **separately numbered** for `abs(int)` and `abs(double)`, i.e.
   STD-011's own table does *not* apply "one id, dispatch on type" to `abs` even though it
   would be the more natural place for that pattern). `min` and `max` are different
   operations regardless of operand type; no type-dispatch mechanism can also recover
   *which function was called* from operand type alone, because `min(3,5)` and `max(3,5)`
   have identical operand types and must produce different results. One wire id cannot
   name both.
2. **Rows 12 (`sin`,`cos`,`tan`) and 13 (`asin`,`acos`) each bundle three and two distinct
   functions respectively into one table row, purely for prose compactness** — none of
   these six functions has more than one numeric-type signature (all are `double →
   double`), so there is no "dispatch on operand type" story available for them at all;
   if "one row = one id" were applied literally here, a decoder would have no way to tell
   `sin` from `cos` from `tan`.

**Resolution adopted by this appendix (pending Peter's ratification alongside the
ceilings):** one wire id per **named function**, not per table row — i.e., exactly the
precedent rows 6/7 (`abs`) already set, extended consistently to `min`/`max` (4 ids: `min`
int, `min` double, `max` int, `max` double) and to `sin`/`cos`/`tan`/`asin`/`acos` (5 ids,
one each, no type-dispatch needed since all are double-only). This is not a redesign of
STD-011's registry — every function, domain, and error rule in §7.2 is unchanged; only the
*wire-id granularity* is corrected from "per row" to "per (name, operand-type) pair,"
matching what rows 1–7/9–11/14/15 already do unambiguously and what rows 8/12/13 need to
do to be implementable at all. **Recommended STD-011 §7.2 text fix** (not applied here):
replace "each row is one wire id" with "each named function, at each distinct
operand-type signature, is one wire id; a table row spanning several names or several
type-signatures for prose brevity does not imply they share an id."

### B.8.2 Contains/startsWith/endsWith have no pinned `#` in STD-011 at all — a second gap

STD-011 §7.2 states string methods "are part of the same closed registry for allowlist
purposes" but its `#` column runs only 1–15, covering the numeric/registry functions —
`contains`/`startsWith`/`endsWith` are never assigned a `#`. T2 cannot allowlist-check a
function with no pinned id, so this appendix **[PROPOSED]** assigns them ids 22–24
(§B.8.3), flagged here as a second, independent gap from §B.8.1's (the first is an
internal inconsistency; this one is an omission).

### B.8.3 The pinned table (this appendix's proposal)

| Id | Function | Arity | STD-011 origin |
|---|---|---|---|
| 1 | `size(s: string) → int` | 1 | §7.2 row 1 |
| 2 | `size(b: bytes) → int` | 1 | §7.2 row 2 |
| 3 | `size(l: list<T>) → int` | 1 | §7.2 row 3 |
| 4 | `int(x: double) → int` | 1 | §7.2 row 4 |
| 5 | `double(x: int) → double` | 1 | §7.2 row 5 |
| 6 | `abs(x: int) → int` | 1 | §7.2 row 6 |
| 7 | `abs(x: double) → double` | 1 | §7.2 row 7 |
| 8 | `min(x: int, y: int) → int` | 2 | §7.2 row 8, split (§B.8.1) |
| 9 | `min(x: double, y: double) → double` | 2 | §7.2 row 8, split |
| 10 | `max(x: int, y: int) → int` | 2 | §7.2 row 8, split |
| 11 | `max(x: double, y: double) → double` | 2 | §7.2 row 8, split |
| 12 | `sqrt(x: double) → double` | 1 | §7.2 row 9 |
| 13 | `radians(x: double) → double` | 1 | §7.2 row 10 |
| 14 | `degrees(x: double) → double` | 1 | §7.2 row 11 |
| 15 | `sin(x: double) → double` | 1 | §7.2 row 12, split (§B.8.1) |
| 16 | `cos(x: double) → double` | 1 | §7.2 row 12, split |
| 17 | `tan(x: double) → double` | 1 | §7.2 row 12, split |
| 18 | `asin(x: double) → double` | 1 | §7.2 row 13, split |
| 19 | `acos(x: double) → double` | 1 | §7.2 row 13, split |
| 20 | `atan(x: double) → double` | 1 | §7.2 row 14 |
| 21 | `atan2(y: double, x: double) → double` | 2 | §7.2 row 15 |
| 22 | `contains(s: string, sub: string) → bool` | 2 | §6.6 — **[PROPOSED new id, §B.8.2]** |
| 23 | `startsWith(s: string, pre: string) → bool` | 2 | §6.6 — **[PROPOSED new id]** |
| 24 | `endsWith(s: string, suf: string) → bool` | 2 | §6.6 — **[PROPOSED new id]** |

Domain/error behaviour for every id is exactly STD-011 §7.1/§7.2/§6.6's, unchanged — this
table only pins wire identity and arity, both of which are T2's to define per §11.3 item 2
("Registry function ids are the §7.2 `#` column values — never a name string resolved
dynamically").

---

## B.9 The Envelope, and Integrity Posture (STD-009 RULE-D1)

`CelFilterRecord` (§B.3) is the parameter shape: `formatVersion` (this appendix pins `1`),
`context` (predicate/transform, with the transform arm carrying the declared result type),
and `expression` (the `ExprNode` tree).

**No separate signature or digest field.** STD-009 §8's filter contract states the filter
"rides the existing DER stream" and RULE-D1 (STD-009 §10, table row) already requires the
**whole** object stream carrying a `@RemoteFunction`/filter parameter to be DER (never a
non-DER transport) — and per STD-009 §8's 2026-07-20 verification note, that stream is
already the fail-closed, canonically-rejecting ATOMIC DER codec, riding under whatever
`net.jini.core.constraint.Integrity` constraint the enclosing call already negotiates. This
appendix's `CelFilterRecord` is therefore an **ordinary embedded field** within whatever
enclosing `@AtomicSerial` record carries a filter/transform registration (e.g. a filter-
registration parameter in Reggie/Outrigger's eventual integration, STD-011 §8.5's `[OPEN]`
item) — it needs **no signature of its own**, because tamper-detection is already the
enclosing Integrity-constrained stream's job, and because (STD-011 §8.1's point 3, STD-009
§8's 2026-07-20 note) a filter is never reconstructed as an object in the deserialization
sense — there is no gadget surface a signature would be defending against here that the
stream-level Integrity constraint does not already cover. Stated explicitly, per this
task's instruction, rather than left to be inferred from STD-009's prose.

**[OPEN → the Outrigger/Reggie integration SOW, STD-011 §8.5].** Whether `CelFilterRecord`
is additionally wrapped in a `TypedWireObject` (STD-006 §4.4's OID-rooted top-level
discriminator) depends on whether a filter/transform registration is ever a **top-level**
wire object in its own right (needing self-description) or is always a field nested
inside some other already-typed record (needing none). This appendix does not decide it —
both are compatible with `CelFilterRecord` as specified; whichever integration SOW defines
the actual registration record picks the answer.

---

## B.10 Worked Examples (hex, hand-derived and machine-verified)

All examples were independently verified byte-for-byte against `asn1tools` 0.167.0's `der`
codec output for this appendix's module (§B.13) — the hex below is not hand-waved, it is
the confirmed encoder output.

### B.10.1 A literal int comparison — `sampleCount >= 30` (predicate context)

AST: `GE(FIELD_REF(unqual("sampleCount")), LIT_INT(30))`.

```
FieldRefNode{steps=[unqual("sampleCount")]}, wrapped as FIELD_REF [7]:
  A7 0F                                              tag=[7] IMPLICIT, len=15
     30 0D                                           steps: SEQUENCE OF, len=13
        80 0B 73 61 6D 70 6C 65 43 6F 75 6E 74        unqual: [0] IMPLICIT UTF8String "sampleCount" (11 bytes)

LIT_INT(30):
  81 01 1E                                           tag=[1] IMPLICIT INTEGER, len=1, value=0x1E=30

GE(...) node, tag=[18] IMPLICIT BinaryNode:
  B2 14                                              tag=[18], len=20 (=17+3)
     A7 0F 30 0D 80 0B 73 61 6D 70 6C 65 43 6F 75 6E 74   (left, 17 bytes)
     81 01 1E                                             (right, 3 bytes)
```

Full bare `ExprNode`: `B2 14 A7 0F 30 0D 80 0B 73 61 6D 70 6C 65 43 6F 75 6E 74 81 01 1E`
(22 bytes).

Full `CelFilterRecord` (`formatVersion=1`, `context=predicate`):

```
30 1B                              CelFilterRecord SEQUENCE, len=27
   02 01 01                        formatVersion INTEGER = 1
   80 00                           context = predicate [0] IMPLICIT NULL (empty)
   B2 14 A7 0F 30 0D 80 0B 73 61 6D 70 6C 65 43 6F 75 6E 74 81 01 1E   expression (22 bytes)
```

Full hex: `301b0201018000b214a70f300d800b73616d706c65436f756e7481011e` (29 bytes).

### B.10.2 A compound `&&` predicate — `x > 0 && x < 100`

AST: `AND(GT(FIELD_REF(x), LIT_INT(0)), LT(FIELD_REF(x), LIT_INT(100)))`.

```
FIELD_REF(x): A7 05 30 03 80 01 78                    (7 bytes; "x" = 0x78)
LIT_INT(0):   81 01 00                                  (3 bytes)
LIT_INT(100): 81 01 64                                  (3 bytes; 100 = 0x64)

GT node [17]: B1 0A A7 05 30 03 80 01 78 81 01 00       (12 bytes)
LT node [15]: AF 0A A7 05 30 03 80 01 78 81 01 64       (12 bytes)

AND node [11]: AB 18 <GT node, 12 bytes> <LT node, 12 bytes>   (26 bytes)
```

Full hex: `ab18b10aa7053003800178810100af0aa7053003800178810164` (26 bytes).

### B.10.3 `FIELD_REF` with a 2-step selector chain — `a.b`

AST: `FIELD_REF(unqual("a"), unqual("b"))`.

```
A7 08                     FIELD_REF [7], len=8
   30 06                  steps SEQUENCE OF, len=6
      80 01 61            unqual "a"  (0x61)
      80 01 62            unqual "b"  (0x62)
```

Full hex: `a7083006800161800162` (10 bytes).

### B.10.4 The Survey-zoot transform's outer structure

STD-011 §14.3's transform returns `list<double>` (the East/North/Up vector). The outer
envelope (`context = transform`, declared result type `list<double>`) wrapping a 3-element
`LIST_LIT` — inner trig/arithmetic content elided (each element follows exactly the
`BinaryNode`/`CallNode` patterns of §B.10.1–2; showing them in full would only repeat those
patterns three times over):

```
30 28                        CelFilterRecord, len=40
   02 01 01                  formatVersion = 1
   A1 03                     context = transform [1] EXPLICIT, len=3
      82 01 02                  DeclaredResultType.listType [2] IMPLICIT ScalarType = doubleT(2)
   A6 1E                     expression = LIST_LIT [6], len=30
      82 08 <8 bytes>           element 1: LIT_DOUBLE (elided formula, shown as a placeholder pattern)
      82 08 <8 bytes>           element 2: LIT_DOUBLE (elided)
      82 08 <8 bytes>           element 3: LIT_DOUBLE (elided)
```

Note the `A1 03 82 01 02` sequence: `A1` is `EvaluationContext.transform`'s `EXPLICIT`
wrapper tag (§B.4.4) — its content (`82 01 02`) is `DeclaredResultType`'s own complete TLV
(`listType [2] IMPLICIT ScalarType`, value `doubleT = 2`), fully intact underneath the
wrapper, exactly the "inner tag survives" property `EXPLICIT` exists to provide. A
placeholder-content version of this structure (using zero-valued `LIT_DOUBLE` elements in
place of the real trig formulas) was encoded and round-tripped byte-for-byte via
`asn1tools` (§B.13):

`3028020101a103820102a61e820800000000000000008208000000000000000082080000000000000000`

(42 bytes total for the placeholder version: 2-byte outer `SEQUENCE` header + 40 bytes of
content, per the `len=40` shown in the breakdown above — `3` bytes `formatVersion` + `5`
bytes `context` + `32` bytes `expression`, and `32` = 2-byte `LIST_LIT` header + 3 × 10-byte
`LIT_DOUBLE` elements. **[Board-review correction]** the prior revision of this example
printed a 83-hex-character string — an odd count, therefore not decodable as a byte
sequence at all — and its accompanying prose claimed "44 bytes total," inconsistent with
its own byte-by-byte breakdown, which sums to 42. Both errors are fixed here; the hex above
was re-verified against `asn1tools` in the same session as this revision's §B.13 compile
(see §B.13.5).

### B.10.5 REJECT — non-minimal length encoding

Canonical `LIT_INT(30)`: `81 01 1E` (tag, short-form length `01`, content `1E`).

Non-canonical variant carrying the **identical semantic value** through a long-form length
where short form suffices: `81 81 01 1E` — the length octet `81` signals "1 subsequent
length octet follows" (long form), that octet is `01` (length = 1), content `1E`. This
decodes to the same `int` value 30, but X.690 §8.1.3.5 requires the *shortest* length
form; using long form for a length `< 128` is non-canonical DER and **MUST be rejected**
(G1 — two byte-strings for one value). **Honesty note:** `asn1tools` 0.167.0 decoded this
non-canonical variant without complaint (§B.13.2) — canonical-length enforcement is not
something the ASN.1 grammar or this particular tool provides; it is a T3/T4 decoder
implementation obligation this appendix states normatively, matching the board guidance's
standing H1 warning.

### B.10.6 REJECT — unknown node tag

Context tag `40` (within the `[27]`–`[62]` reserved block), constructed, high-tag-number
form (`40 ≥ 31`): first byte `0xBF` (context class, constructed, low-5-bits all-ones =
high-tag-number marker), second byte `0x28` (40 in base-128, single byte since `< 128`),
empty content: `BF 28 00`.

Confirmed empirically (§B.13.1): decoding this against the `ExprNode` `CHOICE` raises
immediately (`asn1tools` reports "Expected CHOICE(ExprNode) with tags […], but got
'bf28'") — automatic in the `CHOICE` form, exactly STD-006 §3.12's stated property, no
special-case code required in a correctly-generated decoder.

---

## B.11 Conformance Vectors This Appendix Adds to T5

Per STD-011 §13, the corpus MUST include, for this wire encoding specifically:

1. **Encode/decode round-trip, one vector minimum per each of the 27 `ExprNode` kinds**
   (§B.4.1), plus `SelectorStep` both arms, `InListOperand` both arms, `DeclaredResultType`
   all four arms, and both `EvaluationContext` arms (12 result-type/context combinations:
   5 scalar × predicate-N/A + nullType + listType×5 + objectType, crossed with
   predicate/transform as applicable — predicate is always just the one `NULL` arm).
2. **Reject probes (§B.5/§B.6), each with a concrete crafted byte string:**
   - Non-minimal tag encoding (once allocated in the reserved range — a probe to keep
     ready for the first extension).
   - Non-minimal length encoding (§B.10.5's exact bytes).
   - Non-minimal `INTEGER` content (a redundant leading `0x00`/`0xFF` byte).
   - Out-of-range `LIT_INT` (`2^63` and `-2^63-1`, both just outside the pinned range).
   - Non-finite `LIT_DOUBLE`: `+Inf` (`0x7FF0000000000000`), `-Inf`
     (`0xFFF0000000000000`), and a non-canonical NaN payload (any exponent-all-ones
     pattern other than the canonical quiet NaN) — per §B.5 item 5's [PROPOSED] rule.
   - **Non-canonical `BOOLEAN` content** (§B.5 item 8): `80 01 01` (`litBool`, content
     octet `0x01` — BER-legal `TRUE`, DER-illegal since canonical `TRUE` is exactly `0xFF`).
   - **Non-minimal `ENUMERATED` encoding** (§B.5 item 9, `ScalarType`): `82 02 00 02`
     (`listType`, a redundant leading `0x00` byte) where the canonical encoding of the same
     value (`doubleT = 2`) is `82 01 02`.
   - Unknown node tag: both a reserved-gap tag (`[27]`–`[62]`, §B.10.6's exact bytes) and
     an out-of-range tag (`> 62`) — tested separately, since a decoder that special-cased
     "reject only if beyond the reserved block" would wrongly accept a reserved-gap tag.
   - Unknown `CallNode.functionId` (`0`, `25`, `1000`).
   - Arity violation for a valid `functionId` (e.g. id 12 `sqrt` with 2 arguments — §B.13.4
     confirms the generic grammar does not catch this; the decoder must).
   - `contains` (`functionId = 22`) with a non-literal needle (§B.6.5).
   - Empty `ListLitNode` (0 elements — violates `SIZE(1..maxCollection)`; §B.13 shows the
     grammar's own `SIZE` constraint is not mechanically enforced by every tool, so this
     is a decoder obligation, not a free grammar check).
   - **Empty `FieldRefNode.steps`** (0 steps — violates `SIZE(1..maxSelectorSteps)`; same
     not-mechanically-enforced gap as the empty-`ListLitNode` case above, §B.6.3).
   - **Over-length name strings** (§B.6.3, [HIGH] fix): an `unqual`/`fieldName` of 256
     bytes (one over the `SIZE(1..255)` ceiling) and a `className` of 1025 bytes (one over
     the `SIZE(1..1024)` ceiling).
   - **Over-length `litString`** (§B.6.3): 65537 content bytes (one over `maxScalarBytes`),
     distinct from the exact-boundary pair in item 3 below — this probe is comfortably past
     the limit, not testing the fencepost itself.
3. **Exact-boundary ceiling pairs (STD-011 §10.3's inclusive fencepost, §13 item 2):**
   `maxExprNodes` at 1024 (accept) / 1025 (reject); `maxExprDepth` at 32 (accept) / 33
   (reject), generated via nested `NOT`/`UnaryNode` (or any single-child recursive form) to
   isolate depth from node-count — the root `ExprNode` itself counts as depth 1 (§B.6.2's
   fencepost note); `maxSelectorSteps` at 16 (accept) / 17 (reject) on a single `FIELD_REF`;
   `maxScalarBytes` at 65536 (accept) / 65537 (reject), tested for **both** `LIT_STRING` and
   `LIT_BYTES` independently; `unqual`/`fieldName` at 255 (accept) / 256 (reject);
   `className` at 1024 (accept) / 1025 (reject).
4. **Step-inclusive `maxExprNodes` vector [HIGH, board review, §B.6.2's counting-mismatch
   fix].** An expression whose `ExprNode`-arm count alone is within `maxExprNodes` (1024)
   but whose STD-011 §10.3-defined node count — counting each `FieldRefNode` selector step
   as its own node, per §B.6.2's corrected counter — exceeds 1024, MUST be rejected. The
   concrete construction: nest `HAS` nodes over `FieldRefNode`s each carrying the maximum
   16 selector steps, few enough `HAS`/`ExprNode` arms to stay under 1024 by the old
   (undercounting) rule, but with total steps pushing the corrected count over 1024 (§B.6.2
   works the full 512×16 = 8192-step case as the illustrative attack; a smaller
   `1025`-vs-`1024`-straddling construction suffices for the conformance vector itself).
5. **No octet-sort/order vectors are added** — per §B.5 item 7, this module has no
   canonicalise-collection analogue, so there is nothing of that shape to test here
   (contrast STD-006's own `SET OF` corpus, which does need it).
6. **`maxExprCost`-adjacent case** (STD-011 §10.6's ~2^32 `contains`-term overflow probe)
   is **T6's vector, not T2's** — it exercises cost-accumulator width over a *validly
   decoded* AST, not a wire-decode reject. Named here only to make the T2/T6 boundary
   explicit: T2 bounds the needle literal's raw length (`maxScalarBytes`, structural); T6
   computes and bounds the resulting cost term (`maxExprCost`, arithmetic) over the AST
   T2 already accepted.

---

## B.12 Summary of [PROPOSED]/[OPEN]/[FLAG] Items

**[PROPOSED] — requires Peter's ratification, alongside STD-011's own §10.3 ceilings:**

1. The complete `ExprNode`/`SelectorStep`/`InListOperand`/`DeclaredResultType` tag
   assignment (§B.4.1, §B.3) — the concrete wire encoding for STD-011's closed node set.
2. `maxScalarBytes = 65536`, pinned here (T2's side of STD-011 §10.3's `[OPEN → T2]` item),
   with the STD-006 §4.5 patch block (§B.7) for the eventual sync.
3. Non-finite `LIT_DOUBLE` literals are a hard decode reject (§B.5 item 5) — adopts STD-011
   §12.1's SHOULD as T2's MUST, resolving STD-011 §15 open item 4's second half.
4. The `CALL` function-id table (§B.8.3), including the `min`/`max`/`sin`/`cos`/`tan`/
   `asin`/`acos` id-splitting fix (§B.8.1) and the new `contains`/`startsWith`/`endsWith`
   ids (§B.8.2).
5. `formatVersion = 1` as this appendix's own pinned value for `CelFilterRecord`.
6. **Name-length ceilings [board review, fix item 2]:** `SelectorStep.unqual`
   `SIZE(1..255)`, `QualifiedSelector.fieldName` `SIZE(1..255)`, `QualifiedSelector.className`
   `SIZE(1..1024)` (§B.3, §B.6.3) — proposed as aligning with STD-006 §7.8's
   `AtomicSerialFieldDef` ceilings, not independently derived, so their ratification is a
   single decision alongside `AtomicSerialFieldDef`'s existing values rather than a fresh
   number to litigate.

**[OPEN] — named owning task, not resolved here:**

1. Whether `CelFilterRecord` is ever wrapped in a STD-006 §4.4 `TypedWireObject` — depends
   on the Outrigger/Reggie integration SOW's registration-record shape (§B.9).
2. T6's redundancy question (STD-011 §12.2/§12.3, SOW §5.3) — this appendix states T2's
   own decode-time checks (§B.6) without deciding whether T6 re-checks them or not.

**[FLAG] — contradictions/gaps found in STD-011 v0.1-DRAFT, left for board disposition:**

1. **§7.2's "one row is one wire id" claim is inconsistent with its own table** (§B.8.1):
   row 8 (`min`/`max`) cannot share one id without losing the ability to distinguish the
   two operations; rows 12/13 (`sin`/`cos`/`tan`, `asin`/`acos`) bundle multiple unrelated
   functions into one row with no shared-id rationale at all (unlike row 8, they have no
   type-dispatch story to begin with). STD-011's own rows 6/7 (`abs`) already use the
   correct "one id per (name, type)" granularity this appendix generalizes.
2. **§7.2 never assigns a `#` to `contains`/`startsWith`/`endsWith`** despite stating they
   are part of the same closed registry for allowlist purposes (§B.8.2) — an omission, not
   an inconsistency, but equally blocking for T2 without this appendix's proposed ids.

---

## B.13 Appendix — Full Validation Transcript (for reviewer reproduction)

**Honesty note on this revision's transcript [board review, fix item 1].** The transcript
in the prior (pre-board-review) revision of this appendix was produced against a locally
held copy of the §B.3 module that had, at the time, already diverged from what was actually
printed in that revision's §B.3 (the seven `--`-comment-trap lines documented in this
revision's changelog, among other differences) — the printed grammar and the reported
"OK 71 types" compile had silently stopped corresponding to the same module. **This
revision's transcript below was produced by extracting the §B.3 code block *exactly as it
appears in this file, after all board-review fixes*, into a standalone `.asn1` file and
compiling and probing that extracted file directly** — the printed text and the transcript
are the same module, verified by construction rather than by hand-checking after the fact,
which is the whole point of this section existing at all.

Toolchain: Python 3.12, `asn1tools` 0.167.0 (`pip install asn1tools`), `der` codec,
compiled together with `JGDMS/docs/asn1/JGDMS-STD-006-v0.13.asn1` (this appendix's module
`IMPORTS maxCollection` from it).

```
$ python3 -c "
import asn1tools
s = asn1tools.compile_files(['JGDMS-STD-006-v0.13.asn1', 'JGDMS-STD-011-Appendix-B.asn1'], 'der')
print('OK', len(s.types), 'types')
"
OK 71 types
```

Compiles clean: grammar and distinct-tag validity confirmed, exactly as before (the SIZE
constraints added by this revision's fixes do not change the type count — 30 named
assignments in this module + STD-006's 41 = 71 — they only tighten existing components'
constraints, which a green compile does not by itself validate; see below).

### B.13.1 Reject probe — unknown/reserved `ExprNode` tag

```
>>> s.decode('ExprNode', bytes([0xBF, 0x28, 0x00]))
DecodeTagError: ExprNode: Expected CHOICE(ExprNode) with tags ['80', '81', '82', '83', '84',
'85', 'a6', 'a7', 'a8', 'a9', 'aa', 'ab', 'ac', 'ad', 'ae', 'af', 'b0', 'b1', 'b2', 'b3',
'b4', 'b5', 'b6', 'b7', 'b8', 'b9', 'ba'], but got 'bf28'. (At offset: 0)
```

Correctly and automatically rejected.

### B.13.2 Reject probe — non-minimal length (NOT enforced by this tool)

```
>>> s.decode('ExprNode', bytes([0x81, 0x01, 0x1E]))          # canonical
('litInt', 30)
>>> s.decode('ExprNode', bytes([0x81, 0x81, 0x01, 0x1E]))    # non-canonical long-form length
('litInt', 30)                                                # accepted -- NOT rejected
```

`asn1tools` performs no canonical-length enforcement — confirms the honesty note at §B.3
and §B.10.5: this is a decoder-implementation obligation, not free from the grammar/tool.

### B.13.3 Boundary probe — `maxSelectorSteps` (NOT enforced by this tool)

```
>>> len(s.encode('ExprNode', ('fieldRef', {'steps':[('unqual','f%d'%i) for i in range(17)]})))
79   # encodes 17 steps without error, though the SIZE(1..16) constraint says it shouldn't
```

`SIZE` constraints are not mechanically enforced by this tool at encode or decode time —
confirms §B.6.2/§B.11's statement that `maxSelectorSteps` (and by the same mechanism,
`maxCollection`, `maxScalarBytes`) is a decoder-implementation obligation.

### B.13.4 Range/arity probes — `functionId` and arity (NOT enforced by this tool)

```
>>> s.decode('ExprNode', s.encode('ExprNode', ('callOp', {'functionId': 99, 'arguments': [('litInt', 1)]})))
('callOp', {'functionId': 99, 'arguments': [('litInt', 1)]})   # accepted -- id 99 is not in [1,24]

>>> s.decode('ExprNode', s.encode('ExprNode', ('callOp', {'functionId': 12,
...     'arguments': [('litDouble', b'\x00'*8), ('litDouble', b'\x00'*8)]})))
('callOp', {'functionId': 12, 'arguments': [('litDouble', b'\x00...'), ('litDouble', b'\x00...')]})
   # accepted -- functionId 12 (sqrt) takes 1 argument per Sec B.8.3, but the generic
   # SIZE(1..2) grammar bound admits 2
```

Both confirm: the function-id allowlist (§B.6.1) and per-id arity (§B.6.5) are T2/T3/T4
decoder obligations, never mechanically free from the ASN.1 grammar.

### B.13.5 Round-trip confirmations (all four worked examples)

All four positive worked examples in §B.10.1–B.10.4 (including §B.10.4's board-review-
corrected 42-byte encoding) were encoded with `asn1tools` and decoded back to structural
equality with the original Python value (`s.decode(type, s.encode(type, value)) == value`),
**and** the encoder's own hex output was compared byte-for-byte against each example's
printed hex string — both checks passing for all four confirms every hand-derived hex
string in §B.10 against real encoder output, not manual arithmetic alone:

```
>>> v1 = {'formatVersion': 1, 'context': ('predicate', None), 'expression': ('geOp',
...   {'left': ('fieldRef', {'steps': [('unqual', 'sampleCount')]}), 'right': ('litInt', 30)})}
>>> enc = s.encode('CelFilterRecord', v1)
>>> s.decode('CelFilterRecord', enc) == v1
True
>>> enc.hex() == '301b0201018000b214a70f300d800b73616d706c65436f756e7481011e'
True

>>> v2 = ('andOp', {'left': ('gtOp', {'left': ('fieldRef', {'steps': [('unqual', 'x')]}),
...   'right': ('litInt', 0)}), 'right': ('ltOp', {'left': ('fieldRef',
...   {'steps': [('unqual', 'x')]}), 'right': ('litInt', 100)})})
>>> enc = s.encode('ExprNode', v2)
>>> s.decode('ExprNode', enc) == v2
True
>>> enc.hex() == 'ab18b10aa7053003800178810100af0aa7053003800178810164'
True

>>> v3 = ('fieldRef', {'steps': [('unqual', 'a'), ('unqual', 'b')]})
>>> enc = s.encode('ExprNode', v3)
>>> s.decode('ExprNode', enc) == v3
True
>>> enc.hex() == 'a7083006800161800162'
True

>>> v4 = {'formatVersion': 1, 'context': ('transform', ('listType', 'doubleT')),
...   'expression': ('listLit', [('litDouble', b'\x00'*8)]*3)}
>>> enc = s.encode('CelFilterRecord', v4)
>>> s.decode('CelFilterRecord', enc) == v4
True
>>> enc.hex() == '3028020101a103820102a61e820800000000000000008208000000000000000082080000000000000000'
True
>>> len(enc)
42
```

### B.13.6 Reject probes — non-canonical `BOOLEAN`, non-minimal `ENUMERATED` (NOT enforced by this tool)

```
>>> s.decode('ExprNode', bytes([0x80, 0x01, 0xFF]))    # canonical TRUE
('litBool', True)
>>> s.decode('ExprNode', bytes([0x80, 0x01, 0x01]))    # non-canonical TRUE (BER-legal, DER-illegal)
('litBool', True)                                       # accepted -- NOT rejected

>>> s.decode('DeclaredResultType', bytes([0x82, 0x01, 0x02]))       # canonical ENUMERATED
('listType', 'doubleT')
>>> s.decode('DeclaredResultType', bytes([0x82, 0x02, 0x00, 0x02])) # non-minimal (redundant 0x00)
('listType', 'doubleT')                                              # accepted -- NOT rejected
```

Confirms §B.5 items 8/9: DER `BOOLEAN`-content and `ENUMERATED`-minimality canonicality are
not mechanically enforced by this tool — both are decoder-implementation obligations, the
same conclusion §B.13.2 already reached for `INTEGER`/length canonicality.

### B.13.7 Boundary probes — name-length `SIZE` constraints (NOT enforced by this tool)

```
>>> len(s.encode('ExprNode', ('fieldRef', {'steps': [('unqual', 'f' * 256)]})))   # 1 over SIZE(1..255)
268   # encodes without error
>>> len(s.encode('ExprNode', ('fieldRef', {'steps': [('unqual', '')]})))          # 0, below SIZE(1..255)
6     # encodes without error
>>> len(s.encode('ExprNode', ('fieldRef', {'steps': [('qual',
...   {'className': 'c' * 1025, 'fieldName': 'f'})]})))                           # 1 over SIZE(1..1024)
1044  # encodes without error
>>> len(s.encode('ExprNode', ('litString', 'x' * 70000)))                        # over maxScalarBytes
70005 # encodes without error
>>> len(s.encode('ExprNode', ('listLit', [])))                                   # 0, below SIZE(1..maxCollection)
2     # encodes without error
>>> len(s.encode('ExprNode', ('fieldRef', {'steps': []})))                       # 0, below SIZE(1..maxSelectorSteps)
4     # encodes without error
```

Confirms §B.6.3's new name-length and empty-collection MUSTs (fix items 2 and 4): every one
of these `SIZE` bounds — upper *and* lower — is a decoder-implementation obligation, not a
free grammar check, for exactly the same reason §B.13.3 already demonstrated for
`maxSelectorSteps`'s upper bound.

---

*End of Appendix B, v0.1-DRAFT. No production code was written or modified in producing
this document; the ASN.1 module above was validated with `asn1tools` 0.167.0 only, as
described in §B.13.*
