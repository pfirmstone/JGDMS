# Scope of Work — DER streaming encoder (byte-identical, verification-preserving) and optional stream read-ahead

- **Drafted:** 2026-10-18.
- **Status:** DRAFT — scoped at Peter's request following the closeout of
  `SOW-DER-Codec-Performance.md`; task breakdown for a code agent; no implementation started.
  Part A (the streaming encoder) is dispatchable under the same rules as the previous SOW.
  Part B (read-ahead) changes *delivery semantics*, not only speed, and is gated on a board
  decision (§B.2) — it is scoped here so that decision can be made on facts.
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
  change observable behaviour, and it is therefore opt-in and separately gated.
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

# Part B — Stream read-ahead (optional; gated)

"Read-ahead" can mean three different things here. They differ sharply in risk, so they are
separated, and only B1 is proposed for direct dispatch.

## B.1 The three meanings, and where each stands

1. **Framing-aware, exactly-sized input buffering with early rejection (B1).** DER is
   length-prefixed: the first bytes of each top-level item declare its length. Today the reader
   buffers to EOF in a doubling buffer (T5 of the previous SOW made it start small) and rejects
   oversize only after reading `max + 1` bytes. A framed reader peeks the next item's header,
   **rejects an item whose declared length exceeds the remaining input budget before buffering
   it**, and allocates each item's buffer at its exact size. It still returns nothing to the
   application until the whole stream has been read and framed (so the observable semantics are
   unchanged: either the whole stream is well-framed and within budget, or no object is
   delivered). Benefits: cheaper rejection of oversize/garbage input (no 16 MiB allocation to
   learn that a header lies), no doubling copies, exact allocation. This is "read ahead" in the
   sense of looking at the next header before committing memory.
2. **Pipelined decode (B2) — decode item *n* while item *n+1* is still arriving.** This is what
   most people mean by read-ahead. It is possible (the stream is a sequence of self-delimiting
   items and the dedup table is already sequential per item), but it changes semantics, see B.2.
3. **Write-ahead (B3, the mirror image): send item *n* while item *n+1* is still being
   marshalled.** Enabled by Part A (items are finished into one encoder in order) and by
   `flush()` per item. Same class of semantic questions on the receiving side.

## B.2 Why B2/B3 are gated on a board decision, not just a benchmark

Today's atomicity is a security property, not an accident: because the whole stream is buffered
and bounded first, **no constructor, `check(GetArg)`, class load or DGC `dirty` registration runs
until the entire stream has been accepted as well-framed and within budget.** With pipelined
decode, item 1's object is constructed (and its side effects occur) before bytes that may later
prove malformed, oversize, or abusive have been seen. The board guidance applies directly:

- **G6 (fail-closed on ambiguity; check the cleanup path):** a later-item reject now leaves
  earlier items' side effects in place. Which of those must be undone, and by what?
- **G10/G13:** the cumulative-work ceiling (`maxStreamReconstitutedBytes`) and the input budget are
  per window and currently checked against a known total; they would become running counters
  that must fail at the same boundary. Needs an adversarial test, not reasoning.
- **Decode units:** DGC completion batching (`DeserializationCompletion`) assumes a decode unit
  spans an accepted stream; pipelining makes "accepted" mean "accepted so far".
- **Blocking and timeouts:** decode would now block on the transport mid-item; today it blocks
  only in the constructor. Thread, interrupt and timeout behaviour must be specified.
- **Benefit is bounded by the transport:** JERI's mux delivers 1 KiB fragments with a synchronous
  wait per fragment, and a typical call's arguments are a few items. B2/B3 only pay off for
  multi-item or very large requests, and only if the receive side is the bottleneck. **This has
  not been measured**; B4 below measures it before anyone designs B2/B3.

## B.3 Task breakdown

| Task | Deliverable | Agent · effort | Model tier | Review | Depends |
|------|-------------|----------------|-----------|--------|---------|
| **B1** · Framed, exactly-sized, early-rejecting input read | In `DerInputLimits`/`DerMarshalInputStream`: read the stream item by item — peek the header, check the declared length against the remaining budget (reject *before* buffering with the identical message class), read exactly that many bytes, accumulate into one exactly-grown buffer; then decode as today. Still no object delivered until EOF and the whole stream is framed. Tests: all `DerInputLimitsTest` boundary pairs unchanged; a header declaring `max+1` is rejected without allocating it (assert via a counting stream: bytes read ≤ header + small); trickle and slow streams; truncated item; trailing garbage; zero-length stream; the 16 MiB−1 / 16 MiB / 16 MiB+1 trio. | general-purpose · HIGH | sonnet | security-literate reviewer (it touches the DoS gate) | — (independent of Part A) |
| **B4** · Measurement of what pipelining could buy | Instrument, do not implement: on the JERI loopback benchmark, record time from first byte received to last byte received vs time spent decoding, for `record`, `batch100`, `blob64k`, and a synthetic 8-item request. Report the fraction of call latency decode could overlap. | general-purpose · MEDIUM | sonnet | single reviewer | B1 optional |
| **B2/B3** · Pipelined decode / write-ahead | **Not scoped for dispatch.** Becomes a design memo plus a board decision on B.2's questions *only if* B4 shows overlap is a material fraction of call latency. If it does, the memo must specify: delivery contract on a later-item reject, what is rolled back, decode-unit semantics, budget accounting, timeouts; and an opt-in switch, default off. | — | — | board | B4 |

## B.4 Recommendation

Do **B1** (cheap, independent, strictly safer than today: it rejects hostile input sooner and
allocates less) and **B4** (a measurement). Do not design B2/B3 until B4 says they are worth the
semantic cost; the loss of "nothing runs until the stream is accepted" is a real security
trade, and the mux fragment size may dominate anyway.

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
> `JGDMS-Board-Reviewer-Guidance.md` G1/G3/G6/G10/G13. Implement task **A<n>** (or **B<n>**) only.
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
