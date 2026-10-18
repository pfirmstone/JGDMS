# Scope of Work — DER streaming encoder (byte-identical, verification-preserving) and optional stream read-ahead

- **Drafted:** 2026-10-18.
- **Status:** DRAFT — scoped at Peter's request following the closeout of
  `SOW-DER-Codec-Performance.md`; task breakdown for a code agent; no implementation started.
  Part A (the streaming encoder) is dispatchable under the same rules as the previous SOW.
  Part B (whole-stream pre-admission: read-ahead for defensive security, per maintainer
  direction "safe, or not — no partial rejection") closes a verified gap: today per-item rejects
  happen after earlier items have been delivered. Part B2 is the stream-level admission gate and
  needs a security-literate reviewer.
- **Origin:** `jgdms-benchmarks/serialization/RESULTS.md`, second run (after T0–T6 of the
  previous SOW), and `SOW-DER-Codec-Performance.md` §8. After that work the dominant remaining
  cost in both directions is **copying**: `DerWriter.frame`/`writeTlv` are 15% of marshal
  `record` self time and 25% of `batch100`, and the dedup pass's read-and-rebuild is the largest
  part of `batch100` unmarshal. Chain parsing and hashing no longer appear near the top.
- **Governing rule — byte-identical output, verdict-identical decode.** Identical to the previous
  SOW §0: every task must leave the encoder's output bytes and the decoder's accept/reject
  decisions unchanged. STD-006 (canonical form, G1), Appendix C (first-occurrence rule over
  *wire* order, per-stream table, fail-closed `chainRef`, ceilings) and `DerInputLimits` are not
  touched. The 31-payload `DerGoldenBytesTest` corpus (alone and three-in-a-stream), its decode
  round-trip, `DerWriterEquivalenceTest` and the existing reject tests are the oracle; a diff in
  bytes or verdicts is a defect. Part B is the one place this SOW proposes anything that could
  change observable behaviour (earlier rejection, never different acceptance), and it is therefore
  reviewed as a security change.
- **Companions:** `SOW-DER-Codec-Performance.md` (§8 records what was built and why T2/T3 stopped
  where they did); `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`;
  `JGDMS-STD-006-Appendix-C-Stream-Schema-Dedup-v0.1-DRAFT.md` (§C.6.2 wire-order first
  occurrence, §C.7, §C.8); `JGDMS-Board-Reviewer-Guidance.md` (G1, G3, G6, G10, G13);
  `jgdms-benchmarks/serialization/`.

---

## 1. What this closes, precisely

Post-previous-SOW profile (JFR, DER, DirtyChai 27; `au.net.zeus.jgdms.der.*` self time):

| | marshal `record` | marshal `batch100` | unmarshal `record` | unmarshal `batch100` |
|---|---:|---:|---:|---:|
| `DerWriter.frame` + `writeTlv` (+`lengthSize`) | 15.5% | 30.3% | ~2% | 8.2% |
| dedup read/rebuild (`StreamSchemaDedup.transform*`, `rebuildCollectionOuter`) | <5% | ~8% | ~5% | ~8% |

and the structural cause: every `DerWriter` call returns a fresh `byte[]` that the caller wraps in
the next call. A payload nested *k* levels deep is copied *k* times on encode, copied again by
the dedup pass (read each TLV out as a slice, rebuild every enclosing level), and, on decode,
copied again by reconstitution. `blob64k` marshal is still ~190 µs (a handful of 64 KiB copies)
against a theoretical one copy.

Targets after Part A (to be confirmed by A5, not asserted): `blob64k` marshal ≤ ~half of today;
`batch100` marshal and unmarshal each a further ~20–30% faster; allocation per marshal of a
64 KiB blob down from several hundred KiB to ~2× the payload.

Explicitly **not** touched: everything in the previous SOW §1's list (`GetArg.callerClass`, fail-
closed verification, canonical-form checks, ceilings, per-stream tables, class resolution through
`ResolutionContext`).

## 2. Verified ground truth (read in source, 2026-10-18)

- `DerWriter` (public API unchanged, single-allocation per call after T2 of the previous SOW)
  has ~122 call sites in 9 files: `ObjectCodec` 36, `DerObjectStreamCodec` 33, `StreamSchemaDedup`
  19, `EntryRepV2Codec` 12, `AnyCodec` 10, `MarshalledInstanceRecord` 5,
  `AtomicSerialSchemaRecord` 4, `AtomicSerialFieldDef` 3, `DerFieldStore` 3.
- Composition is bottom-up by value: leaf `DerWriter.writeX(...)` → collected in a
  `List<byte[]>` → `writeSequence(list)` → the parent's list. No call site holds a buffer it
  appends into; there is no writer *object*.
- **A `SET OF` (CANONICALISE collection) must octet-sort its children's full encodings**
  (X.690 §11.6) before framing. The sort key is the child's complete canonical bytes, so a sorted
  collection's elements are necessarily encoded to separate arrays first. A streaming encoder
  reduces copying elsewhere; it cannot remove the per-element array for sorted sets, and must not
  pretend to. (`SEQUENCE`/preserve-order collections have no such constraint.)
- Dedup is a pass over the canonical encoding, not part of encoding: Appendix C §C.6.2 defines
  first occurrence over **wire** order, which for a sorted set is the order *after* the sort. The
  previous SOW's §8 records why dedup-while-encoding is wire-incompatible. This SOW keeps that
  structure; it makes the *pass* cheap (Part A, A3), it does not remove it.
- `DerObjectStreamCodec` buffers every encoded item in `writeBuffer` until `flush()`/`drainTo`;
  `DerMarshalInputStream`'s constructor calls `DerInputLimits.readAllBytesBounded(in)`, i.e. it
  **reads the whole stream to EOF before the first `readObject()`**, then decodes from the array.
  (Part B starts from these two facts.)
- Existing oracles: `DerGoldenBytesTest` (31 payloads × {1,3} items, encode + decode round-trip),
  `DerWriterEquivalenceTest` + frozen `DerWriterReference`, `ConformanceVectorsTest`,
  `DerStreamSchemaDedupTest`, the `StreamSchemaDedup*` ceiling/bomb/cumulative-work tests,
  `DerInputLimitsTest`, `MarshalledInstanceFastPathTest`, `StreamSchemaDedupVerifiedChainTest`,
  and the jqwik property tests.

# Part A — Streaming encoder

## A.1 Design positions (the agent implements these; it does not relitigate them)

### A.1.1 One growable buffer, lengths patched in place, minimal-length rule kept

Introduce `DerEncoder`, a mutable single-thread encoder over one growable `byte[]`:
`beginConstructed(Tag)` records the content start; children are appended in order;
`end()` computes the content length and writes the minimal DER length. Because the length-octet
count depends on the content length (1, 2, 3, … octets), `end()` either (a) reserves the common
case (a 1-octet length) and shifts the content once if more are needed (one memmove, only for
constructs ≥ 128 bytes, and *once per construct*, replacing a copy of the whole subtree at *every*
ancestor), or (b) uses the two-pass size-then-write variant. The previous SOW's open question 1
(back-to-front vs two-pass) is decided here: **forward write with reserve-and-shift**, because the
encode call graph is naturally forward and back-to-front would force reverse iteration through
`ObjectCodec`'s field loop. The implementer may choose (b) only with a benchmark showing it wins.
Either way the output is byte-identical; the old builder stays in the test tree as the oracle
(already there as `DerWriterReference`).

### A.1.2 The façade stays; the new API is additive

`DerWriter`'s static methods remain, with their current semantics, for callers outside the
migrated paths and for external users. `DerEncoder` is new API. A migrated call site stops
calling `writeSequence(list)` and instead brackets its children. No call site is *required* to
migrate in one task; each task migrates one file and must pass the full oracle on its own.

### A.1.3 Sorted sets: encode children to scratch, sort, append

For a CANONICALISE collection the encoder encodes each child into a reusable **scratch region**
(or a per-collection pooled array), sorts by unsigned lexicographic compare on those ranges
(the comparator already proven by the octet-sort harness; reuse it, do not reimplement), then
appends the sorted ranges. The saving over today is one copy and one allocation per element,
not the removal of the per-element encode. Ties (duplicate encodings) behave exactly as today.

### A.1.4 Ownership and defensive copying

`DerEncoder` never exposes its internal array. `toByteArray()` copies out; a package-private
`writeTo(OutputStream)` may stream from the internal array. A caller that keeps encoded bytes
(e.g. a cached chain encoding from the previous SOW's T1) receives a copy, and cached arrays
handed *into* an encoder are copied in, never aliased as the encoder's own storage.

### A.1.5 The dedup pass on a shared buffer

`StreamSchemaDedup`'s walker reads canonical bytes with `DerReader` and rebuilds. The migrated
walker (A3) reads from the canonical buffer by offset (no `readTlv` slice copies) and writes the
stream form into a `DerEncoder`, substituting only at chain sites; untouched sub-TLVs are
`appendRaw(src, off, len)`. The pre-order/wire-order logic, table, ceilings, depth checks, `[8]`
exclusion and every reject message are preserved verbatim (this is the security spine; the
change is *how bytes move*, not *what is decided*).

### A.1.6 Decode-side reconstitution gets the same treatment, with its fences intact

The reconstitution walker (decode direction) writes its output through the same encoder. The
two reconstitution ceilings (`maxReconstitutedBytes`, `maxStreamReconstitutedBytes`) are metered
at the same points, **before** the amplifying append, so the interior fence property (G10) is
unchanged. A new unit test asserts that an over-ceiling `chainRef` fan-out is rejected before any
append occurs (the encoder's size does not grow past the ceiling).

## A.2 Task breakdown

| Task | Deliverable | Agent · effort | Model tier | Review | Depends |
|------|-------------|----------------|-----------|--------|---------|
| **A0** · Oracle extension | Add to `DerGoldenBytesTest` the payloads that stress nesting depth and large lengths: depth-`MAX_NESTING−1` chain, a 70 000-element `Rec` list (length-octet boundary 3), a set with duplicate encodings, an empty collection, a 16 MiB−1-byte blob. Generate fixtures from HEAD **before** A1. Add a `DerEncoder`-vs-`DerWriterReference` property test scaffold (random nested trees incl. sorted sets) that A1 fills in. | general-purpose · MEDIUM | sonnet | single reviewer (fixtures from clean HEAD) | — |
| **A1** · `DerEncoder` + sorted-set support | The class per A.1.1/A.1.3/A.1.4; façade `DerWriter` unchanged. Property test: for random nested trees (incl. all tag forms, all length boundaries 127/128/255/256/65535/65536, sorted sets), `DerEncoder` ≡ `DerWriterReference` byte-for-byte. Microbenchmark vs the façade on a 64 KiB / 7-level payload. | general-purpose · HIGH | sonnet | single reviewer + property test + `ConformanceVectorsTest` | A0 |
| **A2** · Migrate the object encoders | `ObjectCodec`, `AnyCodec`, `DerFieldStore`, `AtomicSerialSchemaRecord`, `AtomicSerialFieldDef`, `MarshalledInstanceRecord`: bracket children instead of building lists. One file per commit, each passing the full oracle. | general-purpose · HIGH | sonnet | single reviewer + full golden corpus per commit | A1 |
| **A3** · Migrate the dedup walker (both directions) | Per A.1.5/A.1.6: offset-based reads, single output encoder, `appendRaw` for untouched sub-TLVs, ceilings metered before append. Keep the old byte[]-in/byte[]-out methods as thin wrappers (the ceiling/bomb/cumulative tests call them). Add an equivalence test: for the whole corpus, new walker output ≡ old walker output (old kept in the test tree) in both directions. | general-purpose · XHIGH (the dedup layer is the security spine) | top-tier | security-literate reviewer + `DerStreamSchemaDedupTest` + every `StreamSchemaDedup*` test + golden corpus + adversarial rejects re-run (G13) | A2 |
| **A4** · Migrate the stream codec | `DerObjectStreamCodec` writes items into one encoder instead of `writeBuffer` of arrays; `drainTo` streams from it. Item framing, version octet, `[8]`/`[9]`/`[16]` branches unchanged. | general-purpose · HIGH | sonnet | single reviewer + golden corpus | A3 |
| **A5** · Measurement + closeout | Re-run the harness on the same machine/JDK as `results-after.json`; add a dated third section to `RESULTS.md` with the before/after pair and ratios; re-profile and list the new top-5 self-time frames; update both SOWs' status. Report allocation per op (`-prof gc`) for `blob64k` and `batch100`, since reducing garbage is a stated goal. | general-purpose · MEDIUM | sonnet | single reviewer | A2–A4 |

**Sequencing:** A0 → A1 → A2 → A3 → A4 → A5. A2 may be split across several commits; A3 is last
among the encoders because it is the security-sensitive one and benefits from A2's encoder
being exercised first.

## A.3 Non-goals (Part A)

- **No wire-format change**, no new tags, no spec text, no negotiation.
- **No change to what is verified or when.** The dedup walker decides exactly what it decides
  today, in the same order, with the same messages.
- **Not removing the per-element encode of sorted sets** (A.1.3 explains why it cannot go).
- **Not dedup-while-encoding** (wire-incompatible; previous SOW §8).
- **Not the JERI mux** (`maxFragmentSize = 1024`, per-fragment synchronous `waitForIO`): still its
  own SOW; it bounds large remote calls in both formats and is independent of this work.
- **Not a public `DerWriter` removal or signature change.**

# Part B — Whole-stream pre-admission ("read-ahead" for defensive security)

**Principle (maintainer direction, 2026-10-18).** Read-ahead exists for defensive security: a
stream is **admitted or rejected as a whole**. It is safe, or it is not. There is no partial
delivery — no object is constructed from a stream that fails pre-admission. Pipelined decode
(decoding item *n* while item *n+1* is still arriving) and write-ahead are therefore **out of
scope and rejected by this principle**, not merely deferred; they were considered in an earlier
draft of this part and removed.

## B.1 What today guarantees, and what it does not (verified, not assumed)

Today's reader buffers to EOF in the constructor under `maxInputBytes`. That part is
all-or-nothing: an oversize stream, or one that fails to read, delivers nothing. **Everything
else is checked lazily, per item, inside `readObject()`**, and a probe run against the real codec
(2026-10-18) shows the consequence: a stream of `[good₁, good₂, item₃]` where `item₃` carries a
`chainRef` naming a digest never sent **delivers `good₁` and `good₂` (constructors run, class
loads done, DGC registrations made) and only then rejects the stream at `item₃`**
(`StreamSchemaDedup: chainRef names a digest with no prior verified fullChain … unknown-digest
hard reject`). The same holds for any per-item reject: malformed or non-canonical TLV framing
inside an item, a duplicate `fullChain`, an arm with the wrong tag, a chain-ceiling breach, a
table-ceiling breach, the per-item or cumulative reconstitution ceilings (the bomb defences), a
nesting-depth breach. Only the version octet is checked up front. So the stream-level
fail-closed property the Appendix C text describes ("a hard reject of the stream") is, in the
implementation, "a hard reject from that item on".

Pre-admission closes that gap for every check that can be made **without constructing anything**.

## B.2 Design positions

### B.2.1 Two phases over the already-buffered bytes; nothing is constructed in phase 1

- **Phase 1 — admission (no object construction, no class loading, no constructor, no
  `check(GetArg)`, no DGC registration, no `ResolutionContext` use).** Walk the whole buffered
  stream and apply every wire-level check the decoder would apply: item framing and the version
  octet; canonical DER length forms; the stream-level dedup resolution of every item in wire
  order against a *scratch* table (unknown digest, duplicate `fullChain`, malformed arm, chain
  and table ceilings, and the per-item and cumulative reconstitution ceilings metered at the same
  points as today, before the amplifying step); nesting-depth and collection-size bounds; item
  tag allow-list. Result: **admit** (a verified plan) or **reject the stream** with the first
  failure, with the same message class and exception type the lazy path throws today.
- **Phase 2 — delivery.** `readObject()` decodes from the admitted plan exactly as today. Because
  phase 1 populated and verified the per-stream chain table, phase 2 does not re-verify chains
  (the verified-chain hand-off from the previous SOW's T3 already supports this).

### B.2.2 Phase 1 reuses the dedup walker, in verify-only mode

The dedup walker (migrated in A3) gains a mode that performs every decision and meters every
ceiling but does not materialise the reconstituted bytes. This is cheaper than reconstituting
twice, and it is *the same code* running in a different output mode, so phase 1 and phase 2
cannot drift apart on what they accept (G8: one source of truth). If A3 has not landed, B2 can
run the existing walker twice and measure the cost; the cost is the reason to land A3 first.

### B.2.3 The honest limit of the guarantee

Pre-admission guarantees the stream is **structurally admissible** (wire level, Layer 1 in
G5's terms). It cannot guarantee that every *object* will be accepted: `check(GetArg)`, the
constructor's invariants, class-resolution failure and admission-gate rejects are Layer 2 /
policy decisions that need the class and run user code, and doing them in phase 1 would be
exactly the construction this part forbids. So the contract is: **no object is constructed from a
stream that is not structurally admissible**, and a Layer-2 reject of object *k* still occurs at
`readObject()` *k*. This is inherent in how objects are built: decode constructs **from the fields
up** — a nested value's constructor (and `check(GetArg)`) runs before its parent's — so once any
constructor has run, that stream can no longer be rejected whole. A "safe or not" verdict for the
stream is therefore only obtainable *before* construction starts, which is exactly what phase 1
is for; everything that can only be known by constructing (Layer 2) stays a per-object reject,
bottom-up. Documentation and the `DerMarshalInputStream` Javadoc must state this
plainly, because "the stream is safe" would otherwise be read as a stronger promise than the
implementation can make. (If a stronger, all-objects-or-none contract is wanted, that is a
different feature: decode every object first, then deliver; memory and side-effect cost to be
decided separately, and DGC registration would need to be deferred until delivery.)

### B.2.4 Class-free structural validation (optional extension, not in B2)

`ObjectCodec` already has a class-free schema-driven field decode
(`decodeToScalarFieldMapClassFree`). Phase 1 *may* be extended to validate field values against
the in-stream schema without loading classes (UTF-8 validity, canonical set ordering and
uniqueness, integer ranges, `byte[]` lengths) — catching more malformed streams before any
construction. It is listed separately (B3) because it duplicates decode logic and must be proven
equivalent to it (G8) rather than assumed.

## B.3 Task breakdown

| Task | Deliverable | Agent · effort | Model tier | Review | Depends |
|------|-------------|----------------|-----------|--------|---------|
| **B0** · Characterise partial delivery | Turn the 2026-10-18 probe into permanent tests that pin **current** behaviour for each per-item reject class (unknown digest, duplicate full, malformed arm, chain ceiling, table ceiling, per-item and cumulative reconstitution, depth, non-canonical framing): record which items are delivered before the reject. These tests change in B2 from "delivers *k*, then rejects" to "delivers nothing" — the diff in expected behaviour is the deliverable's proof. | general-purpose · MEDIUM | sonnet | single reviewer | — |
| **B1** · Framed, exactly-sized, early-rejecting input read | In `DerInputLimits`/`DerMarshalInputStream`: read item by item — peek the header, reject an item whose declared length exceeds the remaining budget **before buffering it**, read exactly that many bytes, accumulate into one exactly-grown buffer. Observable semantics unchanged (still reads to EOF and delivers nothing before the stream is framed). Tests: all `DerInputLimitsTest` boundary pairs unchanged; a header declaring `max+1` is rejected having read only the header (counting stream); trickle and slow streams; truncated item; trailing garbage; empty stream; the 16 MiB−1 / 16 MiB / 16 MiB+1 trio. | general-purpose · HIGH | sonnet | security-literate reviewer (touches the DoS gate) | — |
| **B2** · Two-phase admission | Per B.2.1/B.2.2: phase-1 verify-only pass over the whole buffered stream; phase-2 delivery from the admitted plan. Every B0 test flips to "rejects the stream, delivers nothing". Add: an adversarial test per reject class placed as the **last** item of a long stream (and as the first, and in the middle) asserting zero objects delivered and zero constructors run (a counting fixture class); a differential test asserting phase 1 and the lazy path agree on accept/reject for the whole golden corpus plus a fuzzed mutation corpus (bit flips and truncations of the golden streams). Javadoc per B.2.3. | general-purpose · XHIGH (this is the stream-level admission gate) | top-tier | security-literate reviewer + B0 flips + differential/fuzz test + G13 adversarial run | A3 (preferred), B0, B1 |
| **B3** · Class-free structural validation (optional) | Per B.2.4, only if the differential test shows a material class of malformed streams still reaching construction. | general-purpose · HIGH | top-tier | security-literate reviewer | B2 |
| **B4** · Cost measurement | Measure phase 1's cost on the benchmark payloads (`point`, `record`, `batch100`, `blob64k`, a synthetic 8-item request, and a 16 MiB stream) and report it against decode time, both with the existing walker run twice and with A3's verify-only mode. Defensive cost is acceptable but must be reported. | general-purpose · MEDIUM | sonnet | single reviewer | B2 |

**Sequencing:** B0 and B1 are independent of Part A and can run first. B2 should follow A3 so the
verify-only mode is the same code as the delivery walker; if the maintainer wants admission
sooner, B2 can ship on the existing walker run twice, with B4 reporting the cost and A3 later
removing it.

## B.4 Non-goals (Part B)

- **No pipelined decode, no write-ahead, no delivery before the stream is admitted.**
- **No change to wire format, to which streams are valid, or to reject messages** — pre-admission
  moves *when* the existing rejects fire (before any construction), not *what* is rejected. A
  stream that is valid today stays valid; a stream rejected at item *k* today is rejected up
  front.
- **No class loading, resolution or constructor execution in phase 1** (B.2.3).
- **Not a stronger all-objects-or-none contract** (B.2.3 explains the difference and the cost).

# Open questions (implementer proposes, maintainer decides)

1. **A.1.1 length handling:** reserve-one-octet-and-shift (recommended) vs two-pass sizing.
2. **A.1.3 scratch strategy for sorted sets:** one reusable scratch array per encoder vs a small
   pool; either must keep peak memory bounded by the existing input/ceiling limits.
3. **A4:** whether `drainTo` should hand the internal array to the transport without a copy
   (requires the `OutputStream` to consume synchronously) or always copy once. Recommendation:
   copy once unless the profile shows it matters.
4. **B1:** whether to treat a declared item length above the *remaining* budget and one above the
   *total* budget as the same failure (recommendation: yes, one message, as today).

# Agent brief (paste-ready)

> You are working in `JGDMS/jgdms-der` (and `jgdms-benchmarks/serialization`). Read, in order:
> `docs/SOW-DER-Streaming-Encoder.md` (this file), `docs/SOW-DER-Codec-Performance.md` §8,
> `jgdms-benchmarks/serialization/RESULTS.md` (third section after A5; second section before),
> `JGDMS-STD-006-Appendix-C-Stream-Schema-Dedup-v0.1-DRAFT.md` §C.5–C.8, and
> `JGDMS-Board-Reviewer-Guidance.md` G1/G3/G6/G10/G13. Implement task **A<n>** (or **B<n>**) only. For Part B the contract is "admitted whole or
> rejected whole": phase 1 must construct nothing, load no class and run no user code.
> Output bytes and accept/reject verdicts must not change: the golden corpus, its decode
> round-trip, `DerWriterEquivalenceTest` and the existing `jgdms-der` suite are the oracle, and a
> fixture mismatch means stop and report, not regenerate. Build and test on DirtyChai from the
> JGDMS root (Pack200 from the GitHub release; see the previous SOW run). Do not touch
> `GetArg`, `ResolutionContext`, `DerInputLimits` ceilings (except B1, which only changes *when*
> the existing ceiling fires), or any spec document. Report: what changed, the test run, and (for
> A5/B4) the measured numbers, including allocation per op.

---
Drafted with AI assistance (Claude) under the direction of the JGDMS maintainer; source facts
verified against the repository on 2026-10-18 (branch `claude/focused-bohr-t6bwal`).
