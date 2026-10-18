# Scope of Work — DER streaming encoder (byte-identical, verification-preserving) and optional stream read-ahead

- **Drafted:** 2026-10-18.
- **Status:** DRAFT, revised 2026-10-18 after a three-seat board review (ASN.1/DER, adversarial
  security, transport/fidelity; findings folded in below) — scoped at Peter's request following the
  closeout of `SOW-DER-Codec-Performance.md`; no implementation started.
  Part A (the streaming encoder) is dispatchable under the same rules as the previous SOW.
  Part B (whole-stream pre-admission: read-ahead for defensive security, per maintainer
  direction "safe, or not — no partial rejection") closes a verified gap: today per-item rejects
  happen after earlier items have been delivered. Part B is a *security* change, not a performance
  change: B2 is the stream-level admission gate, **where it runs relative to authorization is an
  open design question (B.2.0)**, and B1/B2 reject some streams that are accepted today (B.2.7).
- **Origin:** `jgdms-benchmarks/serialization/RESULTS.md`, second run (after T0–T6 of the
  previous SOW), and `SOW-DER-Codec-Performance.md` §8. After that work the dominant remaining
  cost in both directions is **copying**: `DerWriter.frame`/`writeTlv` are 15% of marshal
  `record` self time and 25% of `batch100`, and the dedup pass's read-and-rebuild is the largest
  part of `batch100` unmarshal. Chain parsing and hashing no longer appear near the top.
- **Governing rule — byte-identical output, verdict-identical decode.** Identical to the previous
  SOW §0: every Part A task must leave the encoder's output bytes and the decoder's accept/reject
  decisions unchanged. STD-006 (canonical form, G1), Appendix C (first-occurrence rule over the
  pre-order record-entry traversal, with collection elements in wire element order; per-stream
  table; fail-closed `chainRef`; ceilings) and `DerInputLimits` ceilings are not touched. The 31-payload `DerGoldenBytesTest` corpus (alone and three-in-a-stream), its decode
  round-trip, `DerWriterEquivalenceTest` and the existing reject tests are the oracle; a diff in
  bytes or verdicts is a defect. Part B is the one place this SOW proposes anything that changes
  observable behaviour (streams rejected earlier, and a small class of streams rejected that are
  accepted today, B.2.7); it is reviewed as a security change.
- **Companions:** `SOW-DER-Codec-Performance.md` (§8 records what was built and why T2/T3 stopped
  where they did); `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`;
  `JGDMS-STD-006-Appendix-C-Stream-Schema-Dedup-v0.1-DRAFT.md` (§C.6.2 pre-order first
  occurrence, §C.7, §C.8); `JGDMS-Board-Reviewer-Guidance.md` (G1, G3, G6, G10, G13);
  `jgdms-benchmarks/serialization/`.

---

## 1. What this closes, precisely

Post-previous-SOW profile (JFR, DER, DirtyChai 27; `au.net.zeus.jgdms.der.*` self time; the
unmarshal columns rest on only ~200 samples and are indicative):

| | marshal `record` | marshal `batch100` | unmarshal `record` | unmarshal `batch100` |
|---|---:|---:|---:|---:|
| `DerWriter.frame` + `writeTlv` (+`lengthSize`) | 15.5% | 30.3% | ~2% | 8.2% |
| dedup read/rebuild (`StreamSchemaDedup.transform*`, `rebuildCollectionOuter`) | <5% | ~8% | ~5% | ~8% |

and the structural cause: every `DerWriter` call returns a fresh `byte[]` that the caller wraps in
the next call. A payload nested *k* levels deep is copied *k* times on encode, and again by the
dedup pass (read each TLV out as a slice, rebuild every enclosing level). `blob64k` marshal is
still ~190 µs (a handful of 64 KiB copies) against a theoretical one copy.

**Part A is a marshal story.** Even eliminating both rows entirely would remove only ~16% of
`batch100` unmarshal and ~7% of `record` unmarshal (board review, arithmetic on the table above),
so no unmarshal speed-up is promised: the unmarshal target is "no regression". Targets after
Part A, to be confirmed by A5 and not asserted: `blob64k` marshal ≤ ~half of today **only if**
A.1.1's single-pass approach (not shift-per-ancestor) is what is built; `batch100` marshal a
further ~20–30% faster; allocation per marshal of a 64 KiB blob down from several hundred KiB to
~2× the payload. Whether the gain shows up in a JERI call at all is unproven: the codec was
~1.6 ms of a ~7.5 ms `batch100` call (RESULTS.md), and A4 changes the drain step that
RESULTS.md hypothesises is part of the unexplained time — A4 is therefore preceded by timing the
drain and read phases separately.

Explicitly **not** touched: everything in the previous SOW §1's list (`GetArg.callerClass`, fail-
closed verification, canonical-form checks, ceilings, per-stream tables, class resolution through
`ResolutionContext`).

## 2. Verified ground truth (read in source, 2026-10-18)

- `DerWriter` (public API unchanged, single-allocation per call after T2 of the previous SOW)
  has ~105 call expressions in 9 files (comments and imports excluded): `DerObjectStreamCodec` 29,
  `ObjectCodec` 26, `StreamSchemaDedup` 18, `EntryRepV2Codec` 12, `AnyCodec` 9,
  `MarshalledInstanceRecord` 4, `DerFieldStore` 3, `AtomicSerialSchemaRecord` 3,
  `AtomicSerialFieldDef` 1. Other modules also call it (`CelEncoder` in jgdms-cel-authoring,
  `DerTbs` in jgdms-platform, two examples); they stay on the façade.
- Composition is bottom-up by value: leaf `DerWriter.writeX(...)` → collected in a
  `List<byte[]>` → `writeSequence(list)` → the parent's list. No call site holds a buffer it
  appends into; there is no writer *object*.
- **A `SET OF` (CANONICALISE collection) must octet-sort its children's full encodings**
  (X.690 §11.6) before framing. The sort key is the child's complete canonical bytes, so a sorted
  collection's elements are necessarily encoded to separate arrays first. A streaming encoder
  reduces copying elsewhere; it cannot remove the per-element array for sorted sets, and must not
  pretend to. (`SEQUENCE`/preserve-order collections have no such constraint.) Map entries have
  their own ordering rule; the golden corpus currently cannot tell key order from whole-entry
  order (every entry has the same length — board review F2, confirmed by breaking the sort and
  seeing 62/62 golden tests still pass), so A0 must add such a case.
- **Dedup is a pass over the canonical encoding, not part of encoding.** Appendix C §C.6.2 defines
  first occurrence over the **pre-order record-entry traversal** ("not over raw byte positions"):
  a record's own chain site precedes its payload's sites even though its bytes follow them, and
  collection elements are taken in *wire element order* — for a sorted set, the order *after*
  the sort. Deduplicating while encoding in iteration order would therefore put the `fullChain`
  on a different element than the first one in wire order. (Strictly, only subtrees under a
  sorted ancestor need their substitution deferred; the previous SOW's §8 states the conclusion
  more broadly than that, and C.6.2's non-normative note — register the record's identity when
  you begin encoding it — recommends the naive approach without mentioning `SET OF`; the spec note
  should be corrected, G8.) This SOW keeps the pass structure and makes the pass cheap (A3).
- `DerObjectStreamCodec` buffers every encoded item in `writeBuffer` until `flush()`/`drainTo`;
  `DerMarshalInputStream`'s constructor calls `DerInputLimits.readAllBytesBounded(in)`, i.e. it
  **reads the whole stream to EOF before the first `readObject()`**, then decodes from the array.
  On the server that constructor runs in `BasicInvocationDispatcher` **before**
  `request.checkConstraints` and `checkAccess` (lines ~1077 vs ~1097/1114), i.e. before
  authorization; the method descriptor is also read from the stream before authorization.
  The authorization-context block is a separate DER stream (`DerReducingContextCodec`),
  read before constraints are checked and described in the code as untrusted authorization input.
  (Part B starts from these facts.)
- Existing oracles: `DerGoldenBytesTest` (31 payloads × {1,3} items, encode + decode round-trip),
  `DerWriterEquivalenceTest` + frozen `DerWriterReference`, `ConformanceVectorsTest`,
  `DerStreamSchemaDedupTest`, the `StreamSchemaDedup*` ceiling/bomb/cumulative-work tests,
  `StreamSchemaDedupNonCanonicalChainTest`, `DerInputLimitsTest`, `MarshalledInstanceFastPathTest`,
  `StreamSchemaDedupVerifiedChainTest`, and the jqwik property tests.

# Part A — Streaming encoder

## A.1 Design positions (the agent implements these; it does not relitigate them)

### A.1.1 One growable buffer; lengths resolved without moving content at every ancestor

Introduce `DerEncoder`, a mutable single-thread encoder over one growable `byte[]`:
`beginConstructed(Tag)` records the content start; children are appended in order; `end()` fixes
the construct's content length. The DER minimal-length rule makes the number of length octets
depend on the content length, which is not known until the construct ends. **The earlier draft's
"reserve one octet and shift once per construct" is wrong** (board review, confirmed by
arithmetic): when a construct closes with ≥ 128 bytes, its whole content shifts, and so does
every enclosing construct's content when *it* closes, so a 64 KiB blob nested 7 deep is still moved
~7 times — the same O(depth × size) as today with a cheaper memmove. A1 must implement and
benchmark the two variants that avoid this, and ship the faster:

- (b) **two-pass sizing:** a size pass computes every TLV length, then one forward write; or
- (c) **reserve-maximum-and-compact-once:** each construct reserves a worst-case length field
  (e.g. 4 octets, enough for lengths below 16 MiB, the largest item `maxInputBytes` admits),
  recording `(headerOffset, contentLength)` in a side list; one left-to-right pass at
  `toByteArray()` closes the slack, so every byte moves at most once overall.

Either way the output is byte-identical; the old builder stays in the test tree as the oracle
(already there as `DerWriterReference`). Back-to-front is rejected as before (forces reverse
iteration through `ObjectCodec`'s field loop). A1's property test must include constructs whose
content length straddles each length-octet boundary, nested inside constructs that also straddle
one, because that is exactly where a slack-compaction bug would hide.

**A1 OUTCOME (2026-10-18): variant (c) reserve-and-compact is built and kept; variant (b) two-pass tree was
built, benchmarked and deleted.** Figures: `DerEncoderMicroBench` (test tree), DirtyChai 27, this Linux
container, median of 9 runs, allocation from `ThreadMXBean.getCurrentThreadAllocatedBytes`; ONE encoder reused
across operations (`DerEncoder.reset()`), against today's `DerWriter` composition producing identical bytes:

| workload (output bytes) | `DerWriter` today | `DerEncoder`, reused |
|---|---:|---:|
| 64 KiB blob nested 7 deep (65 576) | 83.8 us, 524 624 B allocated | **13.7 us, 65 592 B** (6.1x faster; one array the size of the output) |
| batch100-like, 100 x {INTEGER, 3 UTF8} (10 304) | 22.9 us, 68 872 B | **7.6 us, 27 920 B** (3.0x, 2.5x less) |
| small record, 4 fields (103) | 174 ns, 592 B | **73 ns, 296 B** (2.4x) |
| sorted set of 100 strings (1 290) | 6.9 us, 11 696 B | 8.7 us, 13 616 B (**26% slower**) |

Fresh (not reused) encoders: blob 22.5 us / 132 072 B, batch100 15.8 us, small record 211 ns (worse than today,
1 176 B: the initial buffer and frame arrays), sorted set 9.8 us: reuse is part of the design, which A4 provides
(one encoder per stream). The tree variant was ~equal on the blob (20.8 us) and worse on batch100 (18.5 us) and
allocation. **The sorted-set path is slower than today's; it is the A2 follow-up** (ObjectCodec already holds
sorted elements as encoded arrays, which would enter via `raw()` rather than a scratch pass per leaf). Validity: the
property test (`DerEncoderEquivalenceScaffoldTest`) matches `DerWriterReference` on 3 000 random trees
(nested sorted sets, boundary lengths, raw/leaf with offsets, 600 reused-encoder trees); it caught a real bug in
the first implementation (a parent's length included its children's unused reserved slack). UNVALIDATED for the
maintainer's workstation.

### A.1.2 The façade stays; the new API is additive

`DerWriter`'s static methods remain, with their current semantics, for callers outside the
migrated paths and for external users. `DerEncoder` is new API. A migrated call site stops
calling `writeSequence(list)` and instead brackets its children. No call site is *required* to
migrate in one task; each task migrates one file and must pass the full oracle on its own.

### A.1.3 Sorted sets: encode children to scratch, sort, append

For a CANONICALISE collection the encoder encodes each child into a **scratch region**, sorts by
unsigned lexicographic compare on those ranges (reuse the comparator already proven by the
octet-sort harness; do not reimplement), then appends the sorted ranges. **The scratch area must
be a stack (or per-collection regions), not one shared buffer:** an element of a sorted set can
itself contain a sorted set (or a map key/value can), and an inner collection reusing a single
scratch region would overwrite the outer element still being encoded in it (board review F3;
no current corpus payload exercises this, A0 adds one). Maps keep their existing ordering rule
(see §2). The saving over today is one copy and one allocation per element, not the removal of the
per-element encode. Ties (duplicate encodings) behave exactly as today.

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
| **A0** · Oracle extension | Extend `DerGoldenBytesTest` **before** A1, fixtures generated from HEAD (and cross-checked against trunk `9adc8e6e3` where the code path is unchanged, as in the previous SOW's T3 stage 0). Add: depth-`MAX_NESTING−1` chain; a 65 536-element `Rec` list (exactly `maxCollection`, length-octet boundary 3; DONE in A0, together with encoder rejection of 65 537+ elements, which the encoder previously emitted although every decoder rejects it); constructs of content length exactly 127/128/255/256/65535/65536 **inside** the stream, nested inside constructs that also straddle a boundary; a set with duplicate encodings; **map entries whose key order differs from whole-entry order**; **a sorted set inside a sorted-set element and inside a map key/value**; empty string, empty `byte[]`, a zero-field record (`30 00`), an empty collection, a `bag:` with duplicates; top-level `[16]`, `[7]`, scalar and null items; a mixed-class stream where a top-level chain is later referenced from a nested site; a 16 MiB−1-byte blob. Add a `DerEncoder`-vs-`DerWriterReference` property test scaffold (random nested trees incl. sorted sets and maps) that A1 fills in. **Mutation check required:** deliberately break the map sort, the set sort and a length boundary and show each is caught. | general-purpose · MEDIUM | sonnet | single reviewer (fixtures from clean HEAD) | — |
| **A1** · `DerEncoder` + sorted-set support — **DONE 2026-10-18, see A1 OUTCOME in A.1.1** | The class per A.1.1/A.1.3/A.1.4; façade `DerWriter` unchanged. Implement **both** (b) two-pass and (c) reserve-and-compact variants of A.1.1 behind the same API, property-test both against `DerWriterReference` (random nested trees, all tag forms, all length boundaries 127/128/255/256/65535/65536 including nested straddles, sorted sets, maps, nested sorted sets), microbenchmark both on a 64 KiB / 7-level payload and on `batch100`, keep the faster and delete the other. | general-purpose · HIGH | sonnet | single reviewer + property test + `ConformanceVectorsTest` | A0 |
| **A2** · Migrate the object encoders — **DONE 2026-10-18, see A2 OUTCOME below the table** | **Callee-first**, so no interim adapter adds copies: `AtomicSerialFieldDef`, `AtomicSerialSchemaRecord`, `MarshalledInstanceRecord`, `DerFieldStore`, `AnyCodec`, then `ObjectCodec`; and `EntryRepV2Codec` (12 sites — Entry byte-matching is a canonicity-critical path with its own tests, which must run per commit). Bracket children instead of building lists. One file per commit, each passing the full oracle. | general-purpose · HIGH | sonnet | single reviewer + full golden corpus per commit | A1 |
| **A3** · Migrate the dedup walker (both directions) — **DONE 2026-10-18, see A3 OUTCOME below the table** | Per A.1.5/A.1.6: offset-based reads, single output encoder, `appendRaw` for untouched sub-TLVs, ceilings metered before append. Keep the old byte[]-in/byte[]-out methods as thin wrappers (the ceiling/bomb/cumulative tests call them). Add an equivalence test: for the whole corpus, new walker output ≡ old walker output (old kept in the test tree) in both directions. | general-purpose · XHIGH (the dedup layer is the security spine) | top-tier | security-literate reviewer + `DerStreamSchemaDedupTest` + every `StreamSchemaDedup*` test + golden corpus + adversarial rejects re-run (G13) | A2 |
| **A4** · Migrate the stream codec — **DONE 2026-10-18, see A4 OUTCOME below the table** | **First**, time the drain and the read phases separately on the JERI loopback benchmark and report whether `drainTo`/input buffering is a material part of a call (RESULTS.md hypothesises the unexplained ~2.4 ms of a `batch100` call; if codec time cannot appear in a JERI call, stop and report). Then: `DerObjectStreamCodec` writes items into one encoder instead of `writeBuffer` of arrays; `drainTo` streams from it. Item framing, version octet, `[8]`/`[9]`/`[16]` branches unchanged. | general-purpose · HIGH | sonnet | single reviewer + golden corpus | A3 |
| **A5** · Measurement + closeout — **DONE 2026-10-18, see the third section of `jgdms-benchmarks/serialization/RESULTS.md`** | Re-run the harness on the same machine/JDK as `results-after.json`; add a dated third section to `RESULTS.md` with the before/after pair and ratios; re-profile and list the new top-5 self-time frames; update both SOWs' status. Report medians as well as means for any row whose error interval exceeds its score. Report allocation per op (`-prof gc`) for `blob64k` and `batch100`, since reducing garbage is a stated goal. | general-purpose · MEDIUM | sonnet | single reviewer | A2–A4 |

**A2 OUTCOME (2026-10-18).** Every encoder in the list now writes into one `DerEncoder` through an
`encodeXTo(..., DerEncoder)` twin; the `byte[]` methods remain as thin wrappers over a fresh encoder
(public API, tests, and the not-yet-migrated callers are unchanged). Commits, in order:
schema records (`012bd348d`), `MarshalledInstanceRecord` (`03e54527a`), encoder hardening (`ab5676555`),
`ObjectCodec` + `AnyCodec` (`c1c60b29b`), `EntryRepV2Codec` (`81a6ef556`). `DerFieldStore` needed no change (it only
calls `encodeLength`, not a composition site). `DerObjectStreamCodec` is A4; `[9]` top-level arrays stay a `byte[]`
boundary until A3 (the dedup walker consumes bytes).

- **Board review of the design** (three seats, read-only) found no design flaw and produced these changes, all in:
  an encoder that has thrown refuses further use until `reset()` (a throwing comparator used to leave a balanced-looking
  encoder that silently omitted the set); `beginSortedMap()` orders `SEQUENCE{key,value}` entries by the key TLV alone,
  extent found once per entry, instead of a header-parsing comparator; `beginOctetString()` is the only way to open a
  primitive tag (`begin()` now rejects one); a `raw()` that is a direct child of a sorted set must be exactly one TLV;
  `reset()` clears only begun frames; `leaf()` length arithmetic is overflow-checked.
- **Not done, deliberately** (PLAUSIBLE, not run): an encode-side total-size cap (a graph past ~2 GiB fails with
  `IllegalStateException`/`OutOfMemoryError`, not a `DerException`; the peer's `maxInputBytes` is the natural bound);
  translating those runtime exceptions to `DerException` at the wrapper; deep sorted-set nesting copies the innermost bytes
  once per sorted ancestor (about (depth+1)x, bounded by `MAX_NESTING`). A **maintainer decision** is needed on the first.
- **Oracle gap found by my own mutation check, not by the old oracle:** removing the map sort from `ObjectCodec` left every
  golden test green, because every golden map was iterated in canonical order already. Two fixtures with insertion-ordered
  maps were added (`map-iteration-order-differs-from-key-order`, `map-integer-keys-across-length-boundaries`), generated
  from the pre-A2 `ObjectCodec`; the existing 94 stream results are unchanged and the mutant now fails. Entry bytes had
  no frozen-bytes test at all: `EntryRepV2GoldenBytesTest` (5 fixtures in `src/test/resources/golden-entry`, body + every
  slice, generated from the pre-migration encoder) was added and matches.
- **Measured** (`CodecBenchmark.marshal`, DER, same container and JDK as `results-after.json`, JMH 2 forks x 5; file
  `results-a2-marshal.json`): `record` 1.55 us vs 1.76 us after the T-series (0.88x); `batch100` 159.3 us vs 224.0 us (0.71x);
  `blob64k` 118.8 us vs 189.6 us (0.63x); against the ORIGINAL baseline 0.38x / 0.35x / 0.43x. The `point` row is excluded: it was
  already noise in the earlier run (its "before" 2.64 us is below its "after" 5.40 us). One run, container not workstation, run-to-run
  drift here was about 12%, so only `batch100` and `blob64k` clear it. Unmarshal was not touched by A2. **Re-run on the workstation before quoting.**

**A3 OUTCOME (2026-10-18).** `StreamSchemaDedup` now reads one input buffer by offset (no slice copy per child, no list-and-join
per level) and writes one `DerEncoder`; a repeat chain site is recognised through a `ByteBuffer.wrap(in, off, len)` window
and only a first occurrence is copied (into the table). The `byte[]` entry points remain as wrappers; `dedupTopLevelAtomicTo`,
`reconstituteTopLevelAtomicTo`, `dedupTopLevelArrayTo`, `reconstituteTopLevelArrayTo` append to a caller's encoder (for A4).
`DerReader` gained a public window constructor, `skipTlv()`, `skip(int)`, `readOctetStringHeader()`, and `Tag.decode(buf, off, limit)`
so a reader over a window of a shared buffer cannot decode a tag into a following element (commits `0d406f603`, `4e753da84`).
Order of decisions is preserved: in a P1 record the chain field physically follows the payload, so the fields are located first,
the chain site processed (pre-order, C.6.2), the trailing-field checks run, and only then is anything emitted.

- **Oracle.** The pre-A3 walker is frozen in the test tree (`StreamSchemaDedupReference`, delete only together with its test) and
  run against the new one in `StreamSchemaDedupEquivalenceTest`: same output bytes, verified chain and table/ceiling counters on the
  whole golden corpus plus `any`-typed arms (including content lengths 120-300 around the 127/128 and 255/256 boundaries) and a
  two-level hierarchy with sites at both levels; same verdict on structure-aware and byte-level mutations (about 11 000 accepted and
  187 distinct reject messages reached; messages equal after normalising digits, because a reader's positions are now offsets into the
  shared buffer); same verdict across the `MAX_NESTING` boundary by splicing a nested site into the innermost null; same verdict and exact
  counts on the `chainRef` fan-out bomb. Mutation check: nine mutants of the new walker (depth guard, reconstitution metering, trailing-field
  check, NULL validation, duplicate `fullChain`, `[8]` descent, meter/append order, sorted rebuild, hierarchy order) are killed.
- **Board review** (Opus DER seat, Fable security seat, read-only): no accept/reject or byte regression found. Opus confirmed every
  re-emitted length and tag is identical and that `SET OF` order is not touched. Fable confirmed meter-before-append (`appendChain` follows
  `decodeSite`'s metering in both callers), no read or emit across a window boundary, and no aliasing of the input into the encode table.
  Corrections to claims made earlier: **"every reject message is unchanged" is false** for one case (a truncated high-tag-number form at a
  window end now fails inside `Tag.decode` with "truncated high-tag-number form" instead of in `decodeLength` with "Unexpected end of DER
  input"; same verdict, different text). `DerReader.requireBytes` now compares in `long` (hygiene; callers already pass validated lengths).
- **Not done / for later (PLAUSIBLE, not run):** (1) **A4 trap, must be designed in:** when A4 shares one encoder across the items of a stream,
  a failed item leaves open frames AND the encode table has already recorded its chains as emitted (table-before-emit is pre-existing), so a later
  item would carry a `chainRef` to a chain never sent. The receiver rejects it, so it fails closed but corrupts the stream; A4 needs an explicit
  poison latch on the codec after any encode failure, or mark/rollback of both the encoder and the table. `DerObjectStreamCodec` does not poison
  after an `IOException` today. (2) Peak memory is not "ceiling + O(input)": `CompactDerEncoder` reserves tag + 5 octets and ~28 bytes of frame
  arrays per construct, so nested-collection input made of 2-byte empty children is about 17x input (about 300 MB for a 16 MiB input); the old
  walker was of similar order. The B5 construct-count budget should count frames, not bytes. (3) `ensure()` at 2^31 throws `IllegalStateException`,
  which `readObject` does not catch (needs an operator-raised `maxInputBytes`; pre-existing shape). (4) `DerReader.slice` bounds only the upper end
  (unused by the walker).
- **Measured** (`CodecBenchmark`, DER, same container and JDK as `results-after.json`, JMH 2 forks x 5, file
  `results-a3-marshal-unmarshal.json`; ratios of means): marshal `record` 1.79 us vs 1.76 after the T-series (1.02x, and vs 1.55 after A2, within the ~12%
  drift seen here), `batch100` 124.8 vs 224.0 us (0.56x), `blob64k` 127.5 vs 189.6 us (0.67x); unmarshal `record` 8.99 vs 14.06 us (0.64x), `batch100` 688.9 vs
  1498.9 us (0.46x), `blob64k` 148.3 vs 200.9 us (0.74x). Against the ORIGINAL baseline: marshal 0.44x/0.27x/0.46x, unmarshal 0.44x/0.42x/0.76x. The `point`
  rows are excluded (noise in the earlier run). Micro-benchmark of the walker alone (`StreamSchemaDedupMicroBench`, one fresh instance per op, old to new): `bench-batch100`
  encode 72 to 44 us and 326 to 79 KB per op, decode 68 to 39 us and 348 to 133 KB; `deep-chain-max-nesting` encode 12.8 to 4.8 us and 66 to 10 KB; a small flat record is
  1253 to 781 ns encode but allocates about 35% MORE (a fresh encoder per call: its initial arrays), which A4's shared encoder removes. Container, one run: re-run on the workstation before quoting.

**A4 OUTCOME (2026-10-18).**

- **Pre-step result (measured before any code changed; `DerStreamPhaseBench`, plain loops, one fresh codec per op):** `drainTo` and input
  buffering are NOT a material part of a JERI call. `batch100`: drain 5.2 us, `readAllBytesBounded` 7.8 us, against a ~7.5 ms call (under 0.2%);
  `blob64k`: 22 us and 40 us. The unexplained ~2.4 ms RESULTS.md wondered about is not in these phases. Codec time does appear in a call
  (marshal + unmarshal of `batch100` is about 0.8 ms of ~7.5 ms), so the SOW's "stop" condition did not apply and A4 went ahead, but its wall-clock
  gain on a JERI call is necessarily small; its benefit is allocation.
- **Change (commits `134ab3ae1`, `086155f21`):** `DerObjectStreamCodec` keeps ONE `DerEncoder` for the write side (no per-item array, no list of
  chunks) plus a `scratch` encoder; `drainTo` compacts once and resets only after a successful write. `[16]` collections, `[8]` proxies, scalars,
  strings, enums are written in place; `[1]` records and `[9]` arrays are built canonical in `scratch` (the dedup walker reads them by offset) and the
  stream form is written straight into the shared output (`dedupTopLevelAtomicTo` / `dedupTopLevelArrayTo`). `MarshalledInstanceRecord.encodeCanonicalTo`
  writes the canonical record with its payload in place. Item framing, the version octet and the `[8]`/`[9]`/`[16]` branches are unchanged; the golden
  corpus (single and triple streams) is byte-identical.
- **The A3 trap, closed.** A failed item used to leave the stream usable even when it had tabled a chain as sent that never was. Now an item that threw
  AFTER changing the stream (partial item buffered in the shared encoder, or a dedup-table change) **poisons** it: later writes and the drain refuse. An item
  rejected before touching it (unsupported type, proxy interface count) still leaves the stream usable. Also poisoned: a write that arrives while an item is in
  progress (user `serialize()` reaching the stream would otherwise write inside an open frame, found by the Fable review), and a stream whose sink threw
  (the sink may have taken a prefix; a retry would duplicate it; the old chunk loop resent every chunk). `DerMarshalOutputStream.close()` now closes the
  underlying stream even when the flush fails. **Behaviour change for callers:** an `IOException` from `writeObject` after a mid-item failure now ends the stream;
  a caller that caught it and carried on will see "stream is unusable". Callers were not audited.
- **Tests:** `DerObjectStreamCodecWriteStateTest` (rejected-before-touch is invisible; failed after touch poisons; mid-collection failure; scratch failure in stream format
  leaves nothing; prefix-then-fail sink; version octet not repeated; several drains; re-entrant item and positional writes in both formats; `close()` after a failing flush;
  dedup `mutationCount`). Mutants: never-poison and reset-before-write are caught; ignoring the dedup-mutation clause survives and is documented as redundant today (every path
  that tables a chain has already opened its frame in the shared encoder).
- **Not done:** `drainTo` still holds the encoder buffer plus its compacted copy (peak about 3x the stream, against 1x for the old chunk list); a compact-in-place
  `writeTo(OutputStream)` would remove it but writes one small chunk per frame, bad for an unbuffered socket. Read side untouched (still one `byte[]` per TLV).
- **Measured** (`CodecBenchmark` DER, same container and JDK, JMH 2 forks x 5; files `results-a4.json`, `results-a4-marshal-rerun.json`). Marshal `record` 1.62 us (A3: 1.79),
  `batch100` 105.2 us (124.8), `blob64k` 85.7 us (127.5; the first run's 391 +/- 1449 us was an outlier caused by my running the unit-test suite concurrently, so it was re-run alone);
  unmarshal unchanged within noise (A4 does not touch it): `record` 9.7 us, `batch100` 738 us, `blob64k` 155 us. Against the ORIGINAL baseline marshal is now 0.40x / 0.23x / 0.31x.
  `JeriBenchmark` (loopback call, DER): `batch100` 4.90 ms, `blob64k` 10.6 ms, `record` 220 us, `point` 184 us, `ping` 140 us, against 9.39 / 15.79 / 0.36 / 0.32 / 0.23 ms originally.
  **Do not read the JERI ratios as codec gains:** the `ping` row, which carries no payload and so no codec work, also fell 38% (226 to 140 us) between runs on this
  container, so run-to-run drift here is of that size and the JERI rows cannot separate A4 from it. Phase timings (plain loops): write side `batch100` 132 to 107 us and 436 to 276 KB per op,
  `blob64k` 121 to 76 us and 792 to 463 KB, `record` 4.4 to 3.2 us and 7.6 to 5.7 KB; encode + drain together `batch100` 137 to 121 us. Container, one run: re-run on the workstation.

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
- **Not a public `DerWriter` removal or signature change**; other modules' use of the façade (`CelEncoder`, `DerTbs`, examples) is untouched.

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

Two further facts the board review traced in code (not run; B0 pins them as tests):

- **Unread trailing items are accepted today.** `DerObjectStreamCodec` has no end-of-stream or
  unread-item check (`available()` only tests `hasMore`). A stream with a valid prefix and a
  malformed *unread* trailing item decodes fine, because the dispatcher reads only the method and
  its arguments and the client reads one response item. Whole-stream admission would reject it
  (B.2.7).
- **DGC callbacks still fire after a reject.** `DerMarshalInputStream.close()` calls
  `decodeUnit.flush()` unconditionally, and `BasicInvocationDispatcher`'s `finally` closes the
  stream on the failure path too. So after a Layer-2 reject of object *k*, the DGC registrations
  made by objects 1…*k*−1 can still be sent. This is independent of pre-admission and applies to
  streams that pass it; if confirmed it is a bug in current code and should be fixed on its own
  (flush on success, discard on failure).

Pre-admission closes the structural part of the gap: every check that can be made **without
constructing anything**, over the part of the stream phase 1 can actually see (B.2.1).

## B.2 Design positions

### B.2.0 Where admission runs relative to authorization (open design question; board review finding)

The DER stream is constructed — and with it the whole input buffered — in
`BasicInvocationDispatcher` **before** `checkConstraints` and `checkAccess`, and the method
descriptor is read from it before authorization too. The same holds for the separate
authorization-context stream. If phase 1 simply ran in the stream constructor, an
**unauthorized peer would get the full dedup walk, chain hashing and reconstitution up to the
ceilings** (and, under B6, class loading and codebase downloads) before being refused. Today such
a peer costs only buffering up to `maxInputBytes`. Admission must not make the unauthenticated
cost larger. Two shapes, to be decided in B2's design step:

- **(i) Two-stage admission.** *Cheap admission* at construction (framing, the version octet,
  per-item and total budgets, item count, tag allow-list — no hashing, no dedup walk, no class
  names), and *full admission* (the dedup walk, B5 policy) invoked explicitly after `checkAccess`
  and before `unmarshalArguments` (an explicit `DerMarshalInputStream` call from the dispatcher,
  or a first-`readObject`-after-authorized hook). The method item that is read before authorization
  is then covered only by cheap admission, which is the existing exposure and does not get worse.
- **(ii) Admission at construction, unauthenticated-cost-bounded.** Only if (i) is rejected, and
  only with a measured bound showing the pre-authorization cost stays comparable to today's.

**Board recommendation (all three seats, 2026-10-18): (i) two-stage admission**, refined as follows.
Option (ii) loses because it adds, per unauthenticated request, SHA-256 over every chain, a dedup
table up to `MAX_DEDUP_TABLE_BYTES` and a full walk — a cost multiplier a peer can drive without
authenticating, and pre-authorization cost is the one thing this section must not grow.

- **Cheap stage (at construction), and only this:** the version octet; each item's tag against the
  allow-list; canonical length octets on each *item header* (`readTlvHeader` already rejects
  indefinite and non-minimal lengths); every item fits the remaining buffer and the items exactly
  cover it (no trailing bytes); per-item and total budgets; the item count; and **item 0 shape-pinned
  to the scalar `long` the dispatcher reads as the method hash**, so the one pre-authorization read
  can never touch a `[1]/[8]/[9]/[16]` item. **Not** in the cheap stage, although the earlier draft of
  B.2.1 listed them: nesting-depth bounds, collection-size bounds and canonical length forms *inside*
  payloads — these need schema and table state (payloads are OCTET STRINGs whose inner structure is
  driven by `wireType()`, and `chainRef` records come from the table) and belong to full admission.
- **API.** The state lives in `DerMarshalInputStream` (it owns the buffer and the table), exposed
  as an explicit, idempotent `admit()`; to let the dispatcher call it without depending on
  jgdms-der, add `default void admitStream() throws IOException {}` to `AtomicObjectInput` in
  jgdms-platform (the dispatcher already casts to that interface for `endDecodeUnit`). The
  dispatcher calls it between `checkAccess` and `unmarshalArguments`, inside the existing `try`.
  A failed `admit()` poisons the stream so later reads rethrow. A rejected stream's decode unit is
  empty, so no DGC callback fires (and see the abort fix for streams that fail later).
- **DECIDED (maintainer, 2026-10-18): fail-closed by default.** The seats differed on what a
  record-bearing read (`[1]`/`[8]`/`[9]`/`[16]`) does *before* `admit()` has been called on a
  dispatcher-created stream. *Fail-closed (security seat):* it throws. This closes a real trap:
  `unmarshalMethod` is `protected` and overridable, so a subclass that reads an object there
  would otherwise pull a dedup record pre-authorization with no error. *Lazy full admission
  (transport seat):* the first record-bearing read runs full admission itself — compatible with
  custom dispatchers, but an early read silently pays full-admission CPU before authorization.
  The maintainer chose fail-closed. Scalar/primitive reads (the method hash) stay allowed and cheap.
  Only streams created by the DER dispatcher's own `createMarshalInputStream` are in this mode;
  streams built through the public `DerMarshalInputStream` constructors (clients, tests, capture
  paths) take full admission at construction as before. Consequences accepted with the decision:
  a third-party dispatcher that overrides `dispatch`, or reads objects in `unmarshalMethod`,
  fails loudly (`IOException` with a message naming `admitStream()`) until it calls
  `admitStream()` after authorization; in-tree, `AtomicDerInvocationLayerTest` (~line 320) and
  `RawWireFormRetainingHandlerTest` obtain the stream from the dispatcher and read objects, so
  B2 must update them to call `admit()`; the stream gains a small state machine (not admitted →
  admitted, or poisoned by a failed admit) that needs tests and Javadoc.
- **Lazy admission is deliberately NOT built (maintainer, 2026-10-18).** There are no operators
  with custom dispatchers yet, so a switch is deferred until one exists — building it now would
  add a second code path, and a way to quietly spend pre-authorization CPU, for a user who does
  not exist. **How it could be supported, if one appears** (documented here so it need not be
  rediscovered; none of this is to be implemented speculatively):
  - A *documented* switch on the stream factory (a property read once and validated like the
    reconstitution factors, or an explicit constructor/factory argument, preferably the
    argument so it is per-dispatcher and not process-wide), selecting `LAZY` for a dispatcher
    stream: the first record-bearing read then runs full admission itself, exactly as it would
    have after `admit()`, and a later explicit `admit()` is a no-op (idempotent).
  - It changes only *when* full admission runs, never *what* is admitted: the same walk, the same
    ceilings, the same B5 policy, the same messages, and nothing is constructed before it
    finishes. That is why it is a compatibility mode and not a second accept set, and why it is
    acceptable where a lenient-UTF-8 flag was not.
  - It must log a warning whenever full admission is triggered lazily before authorization has
    been signalled, so the extra pre-authorization cost is visible to the operator, and it
    should be off by default in every distribution.
  - The test to add with it: an unauthorized request through a lazy-mode stream that reads an
    object early performs the full walk (and is counted), so the cost is a pinned, known
    property and not a surprise.
- **ACC stream.** `DerReducingContextCodec` decodes its block in full before authorization anyway,
  so deferral buys nothing: call `admit()` right after construction, and give that stream its own,
  smaller `DerInputLimits` (it currently inherits the dispatcher's) — it is authorization input,
  not argument payload.
- **Client response stream** (no authorization step): full admission at construction, via a flag
  from `AtomicDerInvocationHandler.createMarshalInputStream`; a failure surfaces as
  `UnmarshalException` with no retry (delivery status is already "maybe processed").
- **Compatibility.** Overrides of `unmarshalMethod`/`unmarshalArguments`/`checkAccess` are
  unaffected (the call lives in `dispatch`); a non-DER stream gets the no-op default. Tests to
  update: `DerObjectStreamTest`, `DerStreamSchemaDedupTest`, `AdversarialDecodeProbeTest`, the
  `StreamSchemaDedup*` ceiling tests, the showcase `HostileInputStopsPolitelyTest`; add one JERI
  test that an unauthorized request triggers no dedup walk.

The response stream on the client has no authorization step and takes full
admission at construction. B4 must report phase-1 cost for both stages, and the "synthetic
8-item request" must include an authorization-context stream.

### B.2.1 Two phases over the already-buffered bytes; nothing is constructed in phase 1

- **Phase 1 — admission (no object construction, no class loading, no constructor, no
  `check(GetArg)`, no DGC registration, no `ResolutionContext` use).** Walk the whole buffered
  stream and apply every wire-level check the decoder would apply: item framing and the version
  octet; item header length forms; the stream-level dedup resolution of every item in wire
  order against a *scratch* table (unknown digest, duplicate `fullChain`, malformed arm, chain
  and table ceilings, and the per-item and cumulative reconstitution ceilings metered at the same
  points as today, before the amplifying step); nesting-depth and collection-size bounds; item
  tag allow-list. Result: **admit** (a verified plan) or **reject the stream** with the first
  failure, with the same message class and exception type the lazy path throws today.
- **What phase 1 cannot see (board review F5/#2, confirmed by trace).** The dedup walker passes
  `[8]` proxy interiors and `[16]` collection interiors through **verbatim, without descent**
  (C.6.5/C.6.5a; `transformNestedSite`), and payload *values* (set order, UTF-8, integer ranges)
  are only checked by the record-level decoder. So a chain-ceiling breach, depth bomb or
  disallowed class name inside a proxy handler's nested record, or a malformed value, passes
  phase 1 and is rejected later, after earlier objects exist. The claims above and in B.1 are
  therefore limited to what the walker sees. Closing the `[8]`/`[16]` hole needs a separate
  **verify-only descent** into those regions that checks chains, depth and sizes but must NOT
  populate or consult the dedup table (C.6.5 exclusion stays) — specified in B2; value checks
  need B3.
- **Phase 2 — delivery.** `readObject()` decodes from the admitted plan exactly as today. Because
  phase 1 populated and verified the per-stream chain table, phase 2 does not re-verify chains
  (the verified-chain hand-off from the previous SOW's T3 already supports this).

### B.2.2 Phase 1 and phase 2 must not drift (G8) — and "same code, different output mode" needs care

Phase 1 must perform every decision the delivery walker performs and meter every ceiling, without
materialising reconstituted bytes. The naive reading — run the walker twice, the second time
against the table the first filled — does not work: the duplicate-`fullChain` check
(`StreamSchemaDedup`, around the `table.containsKey` reject) would reject every `fullChain` on the
second run. Avoiding that needs a "trust the table" mode, which is a second, weaker copy of the
security spine. The two honest options:

- **(a) Phase 1 builds and keeps a plan:** for each item, the reconstituted bytes (or an
  offsets-and-substitutions description of them). Phase 2 consumes the plan; the delivery walker
  is not re-run. Cost: the per-item memory bound (C.8.3) becomes a whole-stream bound, up to the
  window ceilings; viable only if bounded explicitly (e.g. plan only while the cumulative size is
  below a configured cap, otherwise fall to (b)).
- **(b) Phase 1 is a separate verify-only pass over a fresh scratch table, and phase 2 re-runs the
  full delivery walker against a fresh table of its own** (nothing shared between passes). Costs
  the dedup walk twice; has no shared state to drift, and the differential test (B2) asserts the
  two agree on every corpus and fuzz input.

B2 must pick by measurement (B4) and must not introduce a mode flag that makes the delivery
walker skip a check. The differential/fuzz test is the guard against drift in either case.

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
**Today's `close()` flushes DGC registrations even after a failed decode (B.1); that must be fixed
for the Layer-2 reject path to leave no network side effects, whether or not B2 lands.**
Payloads decoded *later* — `MarshalledInstance` and Entry payloads, which go through the
record-level capture path (no version octet, no dedup, `DerInputLimits.DEFAULT`) — and proxies
unmarshalled long after the response stream was admitted are outside Part B altogether.

### B.2.4 Class-free structural validation (optional extension, not in B2)

`ObjectCodec` already has a class-free schema-driven field decode
(`decodeToScalarFieldMapClassFree`). Phase 1 *may* be extended to validate field values against
the in-stream schema without loading classes (UTF-8 validity, canonical set ordering and
uniqueness, integer ranges, `byte[]` lengths) — catching more malformed streams before any
construction. It is listed separately (B3) because it duplicates decode logic and must be proven
equivalent to it (G8) rather than assumed.

### B.2.5 Security policy checks that only read-ahead makes possible (B5; changes the accept set)

B2 is deliberately *equivalence-preserving*: it moves existing rejects earlier. Because phase 1
sees the **whole** stream before anything is built, it can also apply checks that cannot be
applied item by item or mid-construction, each of which rejects the **entire** stream:

- **Class-name policy over the whole stream.** Walk every schema chain (and `[7]` enum, `[8]` proxy
  interface list, array component) and collect every class *name* the stream will ask the
  `ResolutionContext` to load, in wire order, without loading any of them (loading runs static
  initialisers and is a side effect). Check the set against an operator-configured policy: a
  deny-list / allow-list of names or packages, and a cap on the number of distinct classes. A
  single disallowed name rejects the stream before any class is loaded or any constructor runs —
  today the same name is only seen when its record's turn comes, after earlier objects exist.
- **Work bounds known in advance.** Count record sites (= constructors that will run), total
  collection elements, and total nested depth across the stream, and reject if they exceed a
  configured budget — a many-tiny-objects construction bomb is bounded *before* the first
  constructor, not detected after N have run. (Today only bytes are bounded, not objects.)
- **Cross-item consistency.** A name that appears with two different schema chains in one stream
  (same `className`, different leaf digests) can be rejected or flagged as a rebinding attempt.

Limits: the walk only sees what phase 1 sees (B.2.1: nothing inside `[8]`/`[16]` interiors
unless the verify-only descent is added, and neither the `MarshalledInstance` record nor later
capture-path payloads); the list above omits `[16]` collections and the `MarshalledInstance`
record for the same reason. Name-level checks cannot see what needs a `Class` object — the declared-slot-type
admission gate (`admissibleConstructClass`), `DeSerializationPermission("PROXY")` against the
interfaces' protection domains, codebase integrity — those still run at construction (B.2.3).

**These are new rejects, so they are a policy decision, not a performance change:** a stream valid
today could be rejected. They therefore ship **configurable, defaulting to values at or above
anything the golden/real corpora produce**, with each limit's default chosen from measurement
and each reject a distinct, logged, test-pinned message. They are a separate task (B5), reviewed
as a security change, and must not be folded into B2's "accept set unchanged" claim.

> **B5 as built (2026-10-18):** see the B5 OUTCOME under B.3. Differences from the text above: the walk also covers `Class`-valued fields, schema wire-type tokens and nested `[8]`/`[7]` (the verify-only descent exists since B2); depth has no knob; rebinding is judged per scope; deferred `MarshalledInstance`/`Entry` payloads remain outside.

### B.2.6 Hoisting the deserialization permission checks (B6; DEMOTED to optional, defence in depth only)

**Hypothesis (maintainer, 2026-10-18):** whole-stream pre-admission could remove the need to
check permissions *during* deserialization. **Board verdict: not as a replacement; at most an
additional, earlier check.**

Today `ObjectCodec` runs `DeSerializationPermission("ATOMIC")` at each `decodeHierarchy`
(`checkAtomicDeSerializationPermitted`) and `("PROXY")` at each `[8]` reconstruction
(`checkProxyDeSerializationPermitted`), each from an `AccessControlContext` built from the
`ProtectionDomain`s of the classes involved, once per constructed object, only when a
`SecurityManager` is installed. A name allow/deny list (B5) cannot replace them: the grant is
keyed on codebase and signers, not name. Hoisting the *check* into phase 1 is semantically
possible, because the context is built purely from `ProtectionDomain`s, not from the call stack,
so *when* the check runs does not change its answer. But the board found it is a poor trade:

- **The domain set is not just the classes named in the stream.** Today's set is
  `storeMap.keySet()`, which includes `@AtomicSerial` **superclasses the stream does not name**. A
  phase-1 set built from stream names would silently omit them unless it walks `getSuperclass()`
  of every resolved class — a G8 drift point.
- **Constructions the wire never names are not gated by ATOMIC at all today** and would stay
  outside a name-driven plan: the `@Serializer`/`Resolve` replacement in `DerReplacer.resolve`,
  enum and `Class`-field resolutions, `resolveTolerant` proxy narrowing. An identity assertion
  "the class being constructed is in the admitted set" is sound only if **every** `loadClass`
  site in `decodeHierarchy`/`DerFieldStore` is plan-fed; that is a wide, easy-to-regress surface.
- **The benefit is largely illusory.** "Whole-stream reject on a permission denial" is
  undercut by B.2.3: an attacker wanting partial delivery uses any Layer-2 reject at item *k+1*.
- **The cost is real.** Phase 1 would load classes (codebase downloads, class definition) for
  streams later rejected, before authorization unless B.2.0(i) is followed; it reverses B2's
  "phase 1 loads no class" rule. No new *class* of side effect exists (the leaf class is loaded per
  item today), but all of it is front-loaded.

**Recommendation:** do not remove the per-object checks. If measurement ever shows they cost
enough to matter, B6 may add a phase-1 check *in addition*, after authorization and after B5's
name policy, with the superclass walk and a test that its answer equals the lazy path's on the
corpus. Until then B6 is not dispatched.

### B.2.7 Streams that B1/B2 reject but are accepted today (the accept set is NOT unchanged)

The earlier draft said B2 "changes no accept set"; that was wrong (board review #2, traced).
Whole-stream admission rejects, up front, streams that today are accepted because the bad part is
never read: an unread trailing item that is malformed, non-canonical, over a ceiling or carries an
unresolvable `chainRef`; a trailing item after the items the dispatcher/handler actually read.
This is the intended defensive effect, but it is an observable change and is documented as one:
B0 pins today's behaviour for these streams, B2 changes the expectation deliberately, and the
release notes say so. Streams that are valid and fully consumed today stay valid.

## B.3 Task breakdown

| Task | Deliverable | Agent · effort | Model tier | Review | Depends |
|------|-------------|----------------|-----------|--------|---------|
| **B0** · Characterise today's behaviour — **DONE 2026-10-18, see B0 OUTCOME below the table** | Turn the 2026-10-18 probe into permanent tests that pin **current** behaviour: for each per-item reject class (unknown digest, duplicate full, malformed arm, chain ceiling, table ceiling, per-item and cumulative reconstitution, depth, non-canonical framing) record which items are delivered before the reject; **an unread malformed trailing item is accepted**; **DGC `flush()` on `close()` after a failed decode** (count dirty registrations with a test `DeserializationCompletion`); `[8]`/`[16]` interior rejects arrive after earlier items. These tests change in B2 from "delivers *k*, then rejects" to "delivers nothing" — the diff in expected behaviour is the deliverable's proof. If the DGC finding is confirmed, raise it as a separate fix. | general-purpose · MEDIUM | sonnet | single reviewer | — |
| **B1** · Framed, exactly-sized, early-rejecting input read — **DONE 2026-10-18, see B1 OUTCOME below the table** | **Only for the stream-format reader** (`DerMarshalInputStream`'s public constructors): `DerInputLimits.readAllBytesBounded` is also called by `DerMarshalInstanceInput` on payloads that are NOT item-framed and must keep working unchanged. Read item by item — peek the header, reject an item whose declared length exceeds the remaining budget **before buffering it**, read exactly that many bytes, accumulate into one exactly-grown buffer. Observable semantics unchanged (still reads to EOF; delivers nothing before the stream is framed). Honest benefit: today's reader already stops at `max+1`, so this saves buffering and a doubling copy on hostile input, nothing more — if a measurement shows no benefit, drop B1. Tests: all `DerInputLimitsTest` boundary pairs unchanged; a header declaring `max+1` is rejected having read only the header (counting stream); trickle and slow streams; truncated item; trailing garbage (expectation per B.2.7); empty stream; the 16 MiB−1 / 16 MiB / 16 MiB+1 trio. | general-purpose · HIGH | sonnet | security-literate reviewer (touches the DoS gate) | — |
| **B2** · Two-stage admission — **DONE 2026-10-18, see B2 OUTCOME below the table** | Per B.2.0(i), B.2.1, B.2.2, B.2.7: cheap admission at construction; full admission (verify-only pass or plan per B.2.2, plus the verify-only `[8]`/`[16]` descent) invoked after `checkAccess`/before `unmarshalArguments`, with a `DerMarshalInputStream` API and a `BasicInvocationDispatcher` call site. Every B0 test flips to "rejects the stream, delivers nothing" or is explicitly documented as out of reach (B.2.1). Add: an adversarial test per reject class placed as the **last** item of a long stream (and first, and middle) asserting zero objects delivered and zero constructors run (counting fixture class); a differential test asserting phase 1 and the lazy path agree on accept/reject for the whole golden corpus plus a fuzzed mutation corpus (bit flips and truncations); a test that an unauthorized request does not trigger the dedup walk; Javadoc per B.2.3. | general-purpose · XHIGH (this is the stream-level admission gate) | top-tier | security-literate reviewer + B0 flips + differential/fuzz test + G13 adversarial run | A3 (preferred), B0, B1 |
| **B3** · Class-free structural validation (optional) | Per B.2.4, only if the differential test shows a material class of malformed streams still reaching construction. | general-purpose · HIGH | top-tier | security-literate reviewer | B2 |
| **B5** · Whole-stream security policy checks — **DONE 2026-10-18, see B5 OUTCOME below the table** | Per B.2.5: class-name policy (allow/deny lists, distinct-class cap), object-count / element-count / depth budgets, cross-item chain-consistency check, all in phase 1, all configurable (system properties validated like the reconstitution factors: invalid or hostile value → documented default, never weaker), defaults chosen from measured corpus maxima with headroom. Tests: each limit's inclusive boundary pair; a stream with a disallowed class name as its LAST item loads no class and runs no constructor (counting fixture + a class whose static initialiser records loading); a construction-bomb stream (1 M tiny records within `maxInputBytes`) is rejected before the first constructor; every default accepts the full golden corpus. | general-purpose · HIGH | top-tier | security-literate reviewer | B2 |
| **B6** · (optional, NOT dispatched) Phase-1 permission check as defence in depth | Per B.2.6: only if measurement shows the per-object checks matter. Adds a phase-1 check after authorization and B5 name policy, with the superclass walk, never replacing the per-object gate. | — | — | security-literate reviewer | B2, B5 |
| **B4** · Cost measurement | Measure both admission stages on the benchmark payloads (`point`, `record`, `batch100`, `blob64k`, a synthetic 8-item request **including an authorization-context stream**, and a 16 MiB stream) against decode time, for B.2.2 options (a) and (b), and report the unauthenticated-peer cost with and without B2. Defensive cost is acceptable but must be reported. | general-purpose · MEDIUM | sonnet | single reviewer | B2 |

**B0 OUTCOME (2026-10-18).** `DerStreamAdmissionCharacterisationTest` (jgdms-der, 12 tests) and `AtomicDerAdmissionSeamTest` (jgdms-jeri, 1 test) pin
today's behaviour; all pass on current code, which is the point: B2 turns the "delivers 2" expectations into "delivers 0".
- **Per-item rejects deliver the earlier items first, then reject** (stream `[good, good, bad]`, counting constructor): unknown `chainRef` digest, duplicate
  `fullChain`, wrong arm tag at a chain site, non-canonical length framing, truncated item, an `[8]` proxy interior, a `[16]` collection interior. In every case 2 objects
  are delivered, 2 constructors ran, the third `readObject` throws.
- **Accepted today:** an unread malformed trailing item (the unit commits normally, 1 callback fired); an unread good trailing item (never constructed). B2 rejects the first up
  front; this is the B.2.7 accept-set change, now observable as a test diff.
- **All-or-nothing today:** the byte cap (enforced while buffering, nothing constructed) and a bad version octet (rejected in the constructor).
- **DGC finding, confirmed and pinned** at the stream level and at the dispatcher seam (a stream created by `AtomicDerInvocationDispatcher.createMarshalInputStream`, read and closed the way
  `BasicInvocationDispatcher.dispatch`'s `finally` closes it): after a failed decode, `close()` runs NO completion callbacks, so no `dirty` is issued for the delivered objects. The
  fix (`ef2fdc6a9`-era `DerDecodeUnit.abort()`) was already in; this closes open item 3 of the handover at the seam level. The full `dispatch(...)` call is not driven (it needs a method discriminator,
  constraints and a server context that do not bear on this question).
- **Not pinned at the stream level, covered at the walker level** (they need an input window a unit test should not build): the per-item and cumulative reconstitution ceilings, the table
  ceilings, chain-record ceilings and depth: `StreamSchemaDedupReconstitutionBombTest`, `...CumulativeWorkBoundTest`, `...TableTest`, `...EquivalenceTest`. They go through the same per-item
  mechanism; B2's adversarial tests should add stream-level vectors for them.

**B1 OUTCOME (2026-10-18).** `DerInputLimits.readFramedBounded` (commits `2d3dde9cf` and the review follow-up), used only by `DerMarshalInputStream`'s public
(stream-format) constructors; `recordLevelCapture` and `DerMarshalInstanceInput` keep `readAllBytesBounded`. It reads item by item through a 128-byte
read-ahead, refuses an item whose declared length exceeds `max - bytesSoFar` BEFORE buffering any of it, and grows its buffer only as content ARRIVES. Streams it cannot
frame (indefinite/reserved/more than 4 length octets, more than 5 tag octets) fall back to the plain bounded read from where they are, so framing errors are still reported when the item is read.
- **Deliberate difference from the plain read (documented in the Javadoc):** a header declaring more content than the budget has left is refused at construction even if the stream is
  actually shorter (a truncated final item). Streams that fit are never refused. A 20 000-case differential against the plain read (random soup and random framed streams, truncation, trailing noise)
  found no other difference. B0's pinned behaviours still hold (they truncate with small declared lengths).
- **Measured** (plain loops, `DerFramedReadMicroBench`, default 16 MiB cap, container): hostile single header declaring ~2 GiB followed by 16 MiB of bytes: **3.6 ms and 16.8 MB allocated
  to 40 us and 67 KB** (it stops at the header). Benign streams: 300 B 326 to 524 ns (1288 to 560 B); 14 KB / 100 items 8.3 to 15 us (53 to 14 KB); 66 KB single item 37 to 38 us (same). So benign
  streams pay up to a few us more CPU for much less allocation. **One shape is worse:** a stream of well-formed items just over the cap (17 x 1 MiB) takes 3.6 to 6.7 ms and 16.8 to 33.5 MB (geometric
  growth, no `available()` pre-size), at most about 2x max transient. The attacker still has to send over 16 MiB for that. Judged acceptable; recorded as a trade-off, not a free win.
- **Fable review (DoS gate) found two things, both fixed:** (1) the first version grew the buffer to the DECLARED size before content arrived, so a peer sending a 5-byte header declaring ~16 MiB and then stalling
  pinned 16 MiB per connection (the plain read allocates in proportion to bytes received): now geometric growth as bytes arrive (`aStalledItemPinsOnlyWhatActuallyArrived`, allocation under 1 MiB);
  (2) header octets were read with one `read()` per octet, a call on the transport each (CPU on an unbuffered/synchronised stream): now a 128-byte `BufferedInputStream` (over-read at most 128 bytes beyond a header),
  `headerOnlyItemsDoNotCostOneTransportReadPerOctet`. Checked sound: no overflow (`n + len` is long, `len <= 2^32-1`, `(int)` taken only after the budget check), a valid stream of at most `max` is never refused,
  no JERI framing declares more than remains, `readRest` at `n == max` blocks exactly where the plain read does. Three mutants (drop the budget check, drop the per-byte cap, drop the end-probe) are killed.

**B2 OUTCOME (2026-10-18).** Commits `558035770` (two-stage admission) and `f9be49b62` (review follow-ups).

- **API.** `AtomicObjectInput.admitStream()` (default no-op, `jgdms-platform`); `DerMarshalInputStream.forDispatcher(...)` and `admitStream()`;
  `BasicInvocationDispatcher.dispatch` calls `admitStream()` between `checkAccess` and `unmarshalArguments` (a failure is an `UnmarshalException`);
  `AtomicDerInvocationDispatcher.createMarshalInputStream` builds with `forDispatcher`. Every other stream-format reader (the client's response stream, the
  authorization-context block, tests) uses a public constructor, which performs FULL admission before it returns.
- **Cheap stage (construction, pre-authorization):** exact item framing, canonical headers, the tag allow-list (a 256-entry table over the identifier octet), items
  cover the input exactly. Allocation-free for short-form headers (anything else goes to `DerReader`, so error messages are the delivery path's); linear in the
  already-bounded input; no hashing, no table, no class names. **Full stage (`admitStream()`):** one walk of every item on a scratch
  decode-direction `StreamSchemaDedup.forAdmission` (the delivery code with its own table and counters, so there is no mode in which delivery skips a check;
  B.2.2 option (b)), plus a verify-only descent into every `[8]` and `[16]` region, including a proxy nested inside a record payload, a `[16]` element or an `Any`
  `[20]` arm (the dedup layer passes `[8]` through, C.6.5; delivery decodes the handler, so admission must), on a region verifier with a table of its own.
  It also runs the class-free wire checks delivery makes on the scalar, enum, null and positional items, through code shared with `readObject`
  (`parseBoxedScalar`, `parseEnumParts`, `parseProxy`, `requireArrayWireType`, `DerUtf8`, the reader's typed primitives), and walks the shell of every record
  (one SEQUENCE per class, one TLV per field), including hierarchies whose fields cannot hold a chain site.
- **Fail-closed.** A dispatcher-created stream is `PENDING`: a read of a record-bearing item (`[1]`, `[7]`, `[8]`, `[9]`, `[16]`) before `admitStream()` is refused, scalar
  reads (the method hash) are allowed. A rejection is permanent and abandons the decode unit (no DGC callback). **A failed read of any kind also ends the stream**
  (including that refusal; Fable): a typed read of the wrong kind consumes only the header, and a caller that caught the failure and carried on would read the rest of
  the item as a new item that admission never covered. `admit()` marks the stream rejected on an `Error` as well (a hostile deep input) and rethrows it.
- **What changed in the accept set (B.2.7):** a stream whose bad part is never read is now rejected up front. A failed read ends the stream. Nothing else. All B0
  characterisation tests flipped from "delivers 2, then rejects" to "delivers 0, constructs 0".
- **Tests.** `DerStreamWholeStreamAdmissionTest` (165): every reject class (unknown digest, wrong arm, non-canonical and truncated framing, unknown top-level tag, proxy and
  collection interiors, oversize chain, table ceiling, duplicate full, fan-out bomb at the stream level, oversize chain inside a top-level and a nested `[8]`, inside a
  `[16]` element and inside a `[8]` nested in a `[16]` element, nested `[8]` bounds, wrong P1 digest, a region site that reuses a tabled leaf record with different bytes,
  a site-free collection past `MAX_NESTING`, fourteen scalar/enum/positional shapes, two site-free shell errors) as the first, middle and last item of a long stream with zero
  objects delivered and zero constructors run; the two-stage dispatcher behaviour (cheap at construction, full at `admitStream()`, refusal and failure terminal and idempotent);
  admission loads no class (counting loader over the whole corpus); positive tests for what must NOT be rejected (a well-formed nested proxy, 17-level collection nesting, a
  100 000-level token over an empty value, a 300-distinct-chain region, a repeated chain); and a differential test against the lazy path over the golden corpus plus
  thousands of tree- and byte-mutated streams (admission never rejects what the lazy path reads to the end; every wire-level lazy failure is an admission reject).
  `AtomicDerAdmissionSeamTest`, `AtomicDerAdmissionDispatchTest` (an unauthorized request never triggers the dedup walk) in `jgdms-jeri`. `jgdms-der` 1352, `jgdms-jeri` + `jgdms-cel` green.
- **Mutation (G13).** Every check above has a mutant that the tests kill: nested `[8]` not descended, P1 digest not compared, digest hit trusted, site-free nested collection passed through,
  site-free shell not walked, region given the stream ceilings, token depth uncapped, each scalar/enum/positional check removed, poison a no-op, tag allow-list skipped on the fast path.
  In the first cut the proxy-handler descent was the mutant that survived and led to the oversize-chain-in-`[8]`-handler and in-`[16]`-element tests; one mutant (a redundant poison-dedup
  clause) is documented as redundant rather than killed. (A note for whoever repeats this: restoring a mutated source file leaves it OLDER than its compiled class, so Maven keeps the mutant's
  class; touch the file after restoring, or the next full run reports failures that are the mutant's.)
- **Review (Opus a0d4931, Fable acf480f, on `558035770`).** Both agreed the gate and the placement are sound. Confirmed gaps, all fixed above: nested `[8]` regions never descended;
  `verifyRegionRecord` not faithful (digest field not compared, stream table ceilings applied to a region); an encode-site digest hit accepted a chain with different bytes without
  verifying it; a site-free collection past `MAX_NESTING` passed through; `maybeContainsSites` recursed once per token level (a `[16]` token is bounded only by item size); cheap
  class-free scalar/enum checks left to delivery; the site-free shell not walked; `ITEM_TAGS.contains` linear per item and about three allocations per header; the PENDING refusal left
  the stream half-usable. Confirmed sound, no change: the tag allow-list equals the writer's emission set; the version-octet rule matches `readObject`'s; the capture contexts (`EntryRepV2Codec`,
  `DerMarshalInstanceInput`) are unchanged and see no admission; no main-code consumer reads a prefix of a longer stream.
- **Cost** (container, JMH 2 forks x 5 iterations, DER; **re-run on the workstation before quoting**; before = A5, results in `results-a5.json`, after = B2 with the review follow-ups):
A5 -> B2 final (`results-a5.json` -> `results-b2.json`; the first cut is `results-b2-firstcut.json`):

| benchmark (us/op, 99.9% error) | A5 | B2 | B2 - A5 | B2 / A5 |
|---|---:|---:|---:|---:|
| codec round trip batch100 | 865.58 +- 75.79 | 947.92 +- 78.46 | +82.34 (within noise) | 1.10 |
| codec round trip blob64k | 264.27 +- 26.97 | 299.96 +- 14.33 | +35.69 (within noise) | 1.14 |
| codec round trip point | 7.85 +- 0.88 | 9.58 +- 1.13 | +1.74 (within noise) | 1.22 |
| codec round trip record | 11.12 +- 1.08 | 13.96 +- 1.07 | +2.83 | 1.25 |
| codec unmarshal batch100 | 789.77 +- 82.90 | 842.70 +- 165.95 | +52.93 (within noise) | 1.07 |
| codec unmarshal blob64k | 176.13 +- 66.10 | 183.43 +- 10.51 | +7.30 (within noise) | 1.04 |
| codec unmarshal point | 6.82 +- 0.96 | 7.82 +- 0.86 | +1.00 (within noise) | 1.15 |
| codec unmarshal record | 9.39 +- 0.90 | 12.31 +- 1.88 | +2.92 | 1.31 |
| JERI batch100 | 4672.40 +- 341.23 | 4913.44 +- 337.84 | +241.04 (within noise) | 1.05 |
| JERI blob64k | 9961.05 +- 520.59 | 10323.90 +- 824.73 | +362.85 (within noise) | 1.04 |
| JERI ping | 134.00 +- 11.83 | 145.30 +- 14.26 | +11.30 (within noise) | 1.08 |
| JERI point | 182.54 +- 30.07 | 181.69 +- 14.11 | -0.85 (within noise) | 1.00 |
| JERI record | 198.49 +- 21.97 | 214.50 +- 23.41 | +16.01 (within noise) | 1.08 |

  Reading: full admission is a second dedup walk over the stream, so in-process unmarshal pays about **+1.0 us (`point`), +2.9 us (`record`), +53 us (`batch100`, 7%, within noise), +7 us (`blob64k`)**;
  small objects pay the most in relative terms (15-31%), large payloads little. **JERI end to end is within noise on every row**; `ping` (no codec work) itself moved +8% between the runs, which
  accounts for the 0-8% on the JERI rows. The follow-ups (nested-`[8]` descent, shell walk, scalar checks) cost nothing measurable over the first cut (ratios 0.86 to 1.09, all within noise), and reading the `[1]` record in
  place instead of copying it took `blob64k` unmarshal from 213 to 183 us. **Not measured here (B4):** the unauthenticated-peer cost with and without B2 on a synthetic 8-item request and a 16 MiB
  stream; by construction the pre-authorization part is the cheap stage only (linear, allocation-free for short-form headers, no hashing). **A possible saving, not taken:** chain verification (`decodeChain` plus SHA-256) runs once at
  admission and again at delivery on separate tables; a verified-chain cache keyed by the exact chain bytes (equality, not digest agreement) shared between the two would remove most of the added cost, at the price of coupling
  admission to delivery, which B.2.2 chose not to do. Left to B4 and the maintainer.
- **What admission does NOT do (limits, stated so nobody relies on more).** Layer 2 (constructors, `check(GetArg)`, class resolution) is per object, bottom-up (B.2.3). The interior of a record's field
  values (set order, ranges, element counts, an `OCTET STRING` that should be a float) is the record decoder's; this is the optional B3. No class-name policy and no object-count or element-count
  budget (B5); the number of items is bounded only by `maxInputBytes`, and the pass is linear in it. The walker verifies the dedup/wire structure of a nested `[8]`'s handler, not what the handler class
  is. `unmarshalMethod` is not shape-pinned to a scalar (Fable's suggestion): it is `protected`, but the fail-closed refusal already keeps a subclass that reads an object there from pulling a dedup
  record pre-authorization. The authorization-context block still decodes pre-authorization under the dispatcher's own limits; giving it a smaller `DerInputLimits` is an open suggestion.
- **Operators and integrators.** A custom dispatcher that does not use `BasicInvocationDispatcher.dispatch` and reads objects from a `forDispatcher` stream must call `admitStream()` itself or its
  record-bearing reads fail loudly. A subclass of `AtomicDerInvocationDispatcher` that overrides `createMarshalInputStream` with a public constructor pays full admission before authorization (a cost, not a hole).
  Not runnable offline in this container (missing Maven plugins): `examples/wire-protocol-showcase` and the Outrigger tests; a grep of main code finds no Outrigger/DGC user of either reader.
- **Remaining for Part B:** B5 (policy, xhigh), B3 only if a class of malformed streams still reaches construction, B4 (unauthenticated-peer cost on the synthetic 8-item and 16 MiB streams;
  the table above is the benchmark-payload half of it), B6 not dispatched.

**B5 OUTCOME (2026-10-18).** Commits `59ab51605`, `5ca6526a2`, `11dd6c2b4`, and the commit that removed the name lists (below). New rejects, so this is a policy change, not a performance one: configurable, with defaults far above anything measured.

- **Name lists were built, reviewed and then REMOVED (maintainer decision).** A name is a claim, not an identity (like a URL; a digest is an identity), and a list of names breaks encapsulation (implementation classes would have to be published to whoever configures the policy) and cannot see codebase or signer. Which classes may be reconstructed stays with the existing `DeSerializationPermission` (`ATOMIC`/`PROXY`) checks and the `ResolutionContext` machinery, which run per object. This also retires the board's HIGH finding (array-class names bypassing the scan) and the "chokepoint in `ResolutionContext.loadClass`" recommendation, which only mattered for lists.
- **What remains (`DerStreamPolicy`, `StreamPolicyMeter`; full admission only, so after `checkAccess` for a request):** a budget on **objects** built (record sites, enums, proxies, arrays, collections; default 262 144), on **elements** (collection, map and reference-array children; primitive arrays are not counted; default 4 194 304), on **distinct class names** the stream mentions (default 1 024; names are still collected, matched as the loader would receive them, array-class names by element class), and **rebinding** (one class name bound to two schemas within one scope, default refused; scopes are the stream's own table and each forwarded `[8]`/`[16]` region, since a re-forwarded proxy carries its origin's version of a class; a tripwire, not a boundary). Properties `au.net.zeus.jgdms.der.stream.policy.{maxObjects,maxElements,maxDistinctClasses,rejectRebinding}`; a bad value falls back to the default with a warning, so a mistake never switches a limit off. `DerStreamPolicy.defaults()` reads them once; explicit-policy overloads on `DerMarshalInputStream`/`forDispatcher`. Rejections are logged at WARNING (logger `au.net.zeus.jgdms.der.stream.policy`, five lines a second per JVM, names truncated and printable); FINE logs each admitted stream's class names. Depth keeps the fixed `MAX_NESTING`.
- **Defaults and the measurement behind them.** Golden corpus census: at most 102 objects, 100 elements, 4 classes in one stream, so the defaults are 2 500x, 42 000x and 256x above; a test pins at least 100x. A 16 MiB stream holds at most about 400 000 minimal nested sites, so 262 144 binds at the default cap; 4 194 304 elements is 64 collections at `MAX_COLLECTION` (Opus showed the first draft's 1 048 576 refused a legitimate `int[]` and was reached by 16 collections; primitive arrays are now uncounted). Both budgets are a deliberate new reject for the release notes.
- **Tests and mutation.** `DerStreamPolicyTest`, `DerStreamPolicyAdmissionTest` (28 routes by which a name can reach the loader are each noticed and counted against the class cap with zero class loads, zero initialisers, zero constructors; every budget's inclusive boundary including the default at exactly 262 144 / 262 145 records for a 20 MB bomb; exact object/element tallies per shape; rebinding in the stream, across a region, in one region, intra-chain, with a cached chain; forwarded regions keeping their own version; corpus headroom; policy absent from the cheap stage); the differential test runs with the policy off. `jgdms-der` 1414, `jgdms-jeri` 78 (1 skipped), `jgdms-cel` 692, 0 failures. The hooks, budgets, scopes, parser, normalisation and rate limiter were mutation-checked (all killed except the name set's de-duplication, a performance-only equivalent).
- **Cost** (container; `results-b5.json`, measured with the lists still present; the lists cost nothing, so removal can only lower it; **re-run on the workstation before quoting**): nothing measurable over B2 (all differences inside the 99.9% intervals; in-process unmarshal of `record` 11.96 us vs 12.31); total over A5 is still B2's, about +1 us (`point`), +3 us (`record`), JERI within noise.
- **Open for the maintainer.** (a) **Privileged types must not be deserialisable** (maintainer, 2026-10-18; for example a `ClassLoader`, including a child loader). Nothing rejects them today: `admissibleConstructClass` admits any `@AtomicSerial` class into an `Object`-typed slot, and `DeSerializationPermission` is keyed on codebase and signers, not on type. This is a rule on the RESOLVED TYPE, not the name, so it belongs in the pre-construction gate in `ObjectCodec.decodeHierarchy`/`decodeNested`/`decodeProxy` (and `[7]`/array component resolution), applied to the class the loader returned. The set of types (`ClassLoader`, `SecurityManager`, `Policy`, `Permission`/`ProtectionDomain`, `Thread`, `Runtime`, `Process`...) needs deciding. (b) Whether `rejectRebinding` defaults to refused. (c) Digest-keyed identity rather than names, if any per-class control is wanted (the schema digest already identifies a class's shape). (d) Deferred `MarshalledInstance`/`Entry` payloads remain outside the policy (B.2.3).
- **Remaining for Part B:** B4, B3 only if needed, B6 not dispatched.

**Sequencing:** B0 and B1 are independent of Part A and can run first. B2 should follow A3 so the
verify-only mode is the same code as the delivery walker; if the maintainer wants admission
sooner, B2 can ship on the existing walker run twice, with B4 reporting the cost and A3 later
removing it.

## B.4 Non-goals (Part B)

- **No pipelined decode, no write-ahead, no delivery before the stream is admitted.**
- **B2 changes no wire format**, and it rejects streams that are bad but currently unread (B.2.7);
  streams valid and fully consumed today stay valid. **B5 deliberately changes the accept set**
  further (new configurable limits and name policy) and is kept separate so that is explicit.
- **No class loading, resolution or constructor execution in phase 1 of B2** (B.2.3). B6, if ever dispatched, is the one task that would relax class *loading* (never initialisation, never construction).
- **Not a stronger all-objects-or-none contract** (B.2.3 explains the difference and the cost).

# Open questions (implementer proposes, maintainer decides)

1. **A.1.1:** two-pass sizing vs reserve-and-compact — settled by A1's benchmark, not by argument.
2. **A.1.3:** scratch strategy is a stack or per-collection regions (a single shared region is
   rejected); either must keep peak memory bounded by the existing input/ceiling limits.
3. **A4:** whether `drainTo` hands the internal array to the transport without a copy (requires
   the `OutputStream` to consume synchronously) or copies once. Recommendation: copy once unless
   the profile shows it matters.
4. **B.2.0:** two-stage admission (recommended) vs admission at construction, and the exact API
   by which the dispatcher invokes full admission after `checkAccess`.
5. **B.2.2:** keep a plan (bounded) vs re-run the delivery walker on a fresh table; decided by B4.
6. **B.2.1:** whether the `[8]`/`[16]` verify-only descent is in B2 or deferred to B3.
7. **B.2.3:** fix the DGC-flush-on-failure behaviour as a standalone change (recommended).
8. **B.2.7:** release-note wording for streams now rejected up front; and B0 must first pin
   whether any handler or peer legitimately leaves trailing optional items unread, before that
   becomes a reject (board: strongest argument against rejecting trailing items).
9. **B.2.0 — DECIDED:** fail-closed by default; the lazy-admission switch is documented, not built,
   until an operator with a custom dispatcher appears.
10. **Lenient UTF-8 (separate change, see below):** board recommends strict both directions.

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


# Companion change: strict UTF-8 (board recommendation, 2026-10-18; not part of Part A or B)

All three seats recommend, unanimously: **reject invalid UTF-8 on decode and unpaired surrogates on
encode, centrally, by default, with no compatibility flag, in the same release.**

- **Why.** `new String(bytes, UTF_8)` never throws; it substitutes U+FFFD, so many byte strings
  decode to one value (a G1 break) and the `catch` in `DerReader.readUtf8String` can never run.
  Lenient sites (CONFIRMED): `DerReader:320`, `AtomicSerialFieldDef:121,130`,
  `AtomicSerialSchemaRecord:292`, `DerObjectStreamCodec:1113,1485`. On encode, `getBytes(UTF_8)`
  maps an unpaired surrogate to `?`, so `"a\uD800"` and `"a?"` encode to identical bytes and the
  value silently mutates in transit — DER byte-equality no longer mirrors `.equals`.
- **Exploitability today is LOW,** not zero: schema chains are already fixed; class/enum/interface
  names containing U+FFFD never resolve, and B5 policy and the loader consume the same decoded
  String, so no allow-list bypass; the residual is Entry matching (stored `FF` and template
  `EF BF BD` decode to equal Strings but never match bytewise) and the encode-side `?` collision.
- **Fix.** One shared helper used by all six decode sites: decode with `new String` (keeps the
  intrinsic fast path) and only if the result contains U+FFFD re-check with a `CharsetDecoder` set
  to REPORT (a result without U+FFFD proves well-formed input); `DerWriter.writeUtf8String` rejects
  lone surrogates (precedent: `CelEncoder.strictUtf8`). The same helper serves B3's UTF-8 check so
  phase 1 and the decoder cannot drift.
- **Spec (G8).** STD-006 never states well-formedness; STD-011 Appendix B §B.5 item 10 mandates
  strict rejection and says STD-006 §4.2 "already requires" it, which is false. Add one normative
  sentence to STD-006.
- **Tests.** Reject `FF`, `C0 80`, `ED A0 80`, truncated `E2 82`, `F4 90 80 80`; accept U+FFFD,
  a 4-byte character, U+10FFFF, U+0000; encode rejects a lone high surrogate, a lone low surrogate
  and a reversed pair; mutation check (revert to lenient and every new test must fail); golden
  corpus unaffected (ASCII).
- **Rollout.** The Java encoder cannot emit invalid UTF-8, so newly rejected decode input comes
  only from foreign or hostile peers; the version is 4.0.0-SNAPSHOT with no released compatibility
  commitment. Release note; scan persisted stores (Outrigger) once.
- **Strongest argument against (all seats note it):** Java serialization round-trips lone
  surrogates, so classes migrating from Java serialization that carry such strings will now fail
  loudly at encode instead of silently losing data. The seats still recommend doing it.

---
Drafted with AI assistance (Claude) under the direction of the JGDMS maintainer; source facts
verified against the repository on 2026-10-18 (branch `claude/focused-bohr-t6bwal`).