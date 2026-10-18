# Scope of Work — DER codec performance (byte-identical, verification-preserving)

- **Drafted:** 2026-10-18.
- **Status:** DRAFT — scoped at Peter's direction ("DER is the future; if there's
  anything we can do to improve its performance without compromising security, we
  should consider it", 2026-10-18); task breakdown for a code agent; no
  implementation started.
- **Origin:** `jgdms-benchmarks/serialization/RESULTS.md` (JOSS vs DER, DirtyChai
  27, 2026-10-18) and `docs/investigation-der-codec-hotspots-2026-10-18.md` (JFR
  profiles of the DER paths, each hot spot traced to source). JOSS is to be removed,
  so its numbers serve only as a baseline for "what a stream library costs when it
  is not copying". The profiles show DER's time is dominated by *how bytes are
  built and rebuilt* around its verification, not by the verification itself.
- **Governing rule — byte-identical output.** Every task in this SOW must leave the
  encoder's output bytes and the decoder's accept/reject decisions **unchanged**.
  STD-006 (canonical form, G1), Appendix C (stream schema dedup: first-occurrence
  rule, per-stream table, fail-closed `chainRef` resolution, ceilings) and
  `DerInputLimits` are not touched. No wire-form change, no spec change, no
  negotiation. This is what makes the work dispatchable to a code agent without a
  wire-format board seat: the conformance corpus and the dedup/golden tests are
  the oracle, and any diff in bytes or verdicts is a defect.
- **Companions:** `JGDMS-STD-006-DER-WireFormat-v0.13-DRAFT.md`;
  `JGDMS-STD-006-Appendix-C-Stream-Schema-Dedup-v0.1-DRAFT.md`;
  `SOW-DER-Stream-Schema-Dedup.md` (the layer T3 restructures — its §3.1–3.3 rules
  are preserved verbatim); `JGDMS-Board-Reviewer-Guidance.md` (G1 canonical form,
  G3 verify independently, G13 run the adversarial input);
  `jgdms-benchmarks/serialization/` (the measurement harness; T0/T6 use it).

---

## 1. What this closes, precisely

Measured on DirtyChai 27 (`RESULTS.md`), marshal/unmarshal through a fresh stream:

| payload | marshal | unmarshal | marginal per extra `Record` in a batch |
|---|---:|---:|---|
| `record` (long + 3 strings) | 4.5 µs | 23.1 µs | marshal 3.7 µs, unmarshal 17 µs |
| `blob64k` (64 KiB `byte[]`) | 137 µs | 113 µs | — |

Profiled cost structure (`investigation-der-codec-hotspots-2026-10-18.md`):

| phase | marshal record | marshal batch100 | unmarshal record | unmarshal batch100 |
|---|---:|---:|---:|---:|
| `DerWriter` copy-per-nesting-level (self) | 41% | 54% | 3% | 24% |
| `StreamSchemaDedup.transformP1` post-pass (incl.) | 39% | 30% | 18% | 34% |
| schema chain re-encoded per object (incl.) | 24% | 23% | – | – |
| transmitted chain parsed per stream (incl.) | 16% | – | 21% | – |
| SHA-256 per object/stream (self) | 1.4% | 1.6% | 3% | 4.5% |
| `readNBytes(16 MiB + 1)` 16 KiB alloc per stream (incl.) | – | – | 24% | – |

Targets after T1–T3 (to be confirmed by T6, not asserted): small-object marshal
≈ halved; `blob64k` marshal within ~2× of a single copy (tens of µs, not 137);
multi-object streams ≈ 30% faster both ways; per-stream fixed decode cost
materially below today's 16 µs.

Explicitly **not** touched (these are the security): the `GetArg.callerClass`
stack walk (hierarchy namespace isolation), fail-closed schema verification,
canonical-form checks, input ceilings, per-stream dedup tables, class resolution
through `ResolutionContext`/`ClassLoading` (Warres discipline).

## 2. Verified ground truth (read in source, 2026-10-18)

- `SchemaGenerator.CHAIN_CACHE` (`ClassValue`) memoises the chain **model** per
  class. `ObjectCodec.encodeSchemaChainBytes` (`:3010`) calls
  `AtomicSerialSchemaRecord.encode()` on every record of the chain **per object
  marshalled**; `StreamSchemaDedup.encodeSite` recomputes
  `sha256(leafRecordBytes(...))` (`:1160`, `:1196`) with
  `MessageDigest.getInstance("SHA-256")` (`:1336`) each time.
- `DerWriter` is a functional builder: `writeTlv` (`:228`) allocates
  `tag+length+content` and `arraycopy`s; `writeSequence`/`writeSet`
  (`:150–209`) concatenate children into a new array. Each nesting level copies
  the whole content again.
- `StreamSchemaDedup` is a byte-level transform between the "canonical full
  form" and the Appendix C stream grammar, in both directions: encode writes the
  full form (chain inline) then `transformP1`/`dedupTopLevelAtomic` re-parses and
  re-emits; decode `reconstituteTopLevelAtomic` rebuilds full-form bytes per
  record before `MarshalledInstanceCodec.decodeMarshalledInstance` runs. Its
  class Javadoc states the invariant the decoder relies on: "everything above the
  transport observes exactly the bytes a canonical full-form stream would have
  delivered."
- `DerInputLimits.readAllBytesBounded` (`:113–123`) calls `in.readNBytes(max + 1)`
  with `max` = 16 MiB default; `InputStream.readNBytes` starts with a 16 KiB
  buffer, so every stream allocates ≥ 16 KiB and trims.
- Existing oracles: `jgdms-der` `ConformanceVectorsTest` (hand-computed DER
  known-answer vectors), `stream/DerStreamSchemaDedupTest` (round-trip
  byte-exactness, reject cases, cross-stream isolation), `DerInputLimitsTest`,
  the `object/`, `schema/`, `marshal/` test trees, `serial-schema.golden` +
  `check-serial-schema.sh` (schema evolution gate), and the jqwik property tests.

## 3. Design positions (the agent implements these; it does not relitigate them)

### 3.1 Byte-identity is the acceptance test, not a goal to approach

Each task adds, before any code change, a **golden-bytes fixture**: the current
encoder's output for a fixed corpus (the four benchmark payloads plus every
`@AtomicSerial` fixture class already in `jgdms-der/src/test`, each alone in a
stream and in a 3-element stream so dedup refs occur), stored as hex under
`src/test/resources`. The fixture is generated **once from HEAD before the task's
change** and committed; after the change the same corpus must reproduce it
`Arrays.equals`. A task that needs to regenerate the fixture has changed the wire
format and is out of scope (stop, report).

### 3.2 Decoder verdict-identity

For the decode-side tasks (T3, T4, T5) the oracle is also every existing reject
test (`DerStreamSchemaDedupTest` unknown-digest / duplicate-full-form / ceiling /
malformed-arm rejects; `DerInputLimitsTest` oversize rejects; evolution-mismatch
rejects under `evolution/`). Same inputs, same exceptions, same messages where
tests assert them.

### 3.3 Caches are keyed by `Class` only, hold only locally generated values

T1 extends the existing `ClassValue` pattern (GC-tied to the defining class,
never cross-loader, never keyed by name). Nothing received from the wire is ever
inserted into any cache. T4's fast path compares wire bytes **against** a cached
local value; it never stores wire bytes.

### 3.4 Ordering on the decode fast path (T4)

Class resolution (by name, through `ResolutionContext` — unchanged) happens
first; only then may the transmitted chain bytes be compared with the resolved
class's cached canonical encoding. On any inequality the existing full path runs
unchanged. The fast path can only ever accept what the slow path accepts.

### 3.5 Measurement is part of the deliverable

T0 records the "before" with the existing harness; T6 records the "after" on the
same machine and JDK (DirtyChai), and the numbers go into `RESULTS.md` as a dated
second run. Claims of improvement without that pair are not accepted (G3).

## 4. Task breakdown

Effort per project convention; model tier per the 2026-07-20 cost-tiering
guidance. All tasks run against the conformance corpus and the §3.1 golden-bytes
fixture; "review" means a reviewer who reads the diff *and* runs the fixture.

| Task | Deliverable | Agent · effort | Model tier | Review | Depends |
|------|-------------|----------------|-----------|--------|---------|
| **T0** · Baseline + fixture | (a) Run `jgdms-benchmarks/serialization` on DirtyChai, commit `results-before.json`. (b) Add `DerGoldenBytesTest` + hex fixtures per §3.1 generated from HEAD. (c) Add a JFR-capable JMH invocation note to the module README (`-prof jfr` + the aggregation script used in the investigation). | general-purpose · MEDIUM | sonnet | single reviewer (fixture generated from clean HEAD, not from a working tree with other changes) | — |
| **T1** · Cache encoded chain bytes + leaf digest per class | Extend `SchemaGenerator`'s `ClassValue` result (or a sibling `ClassValue` in `ObjectCodec`) to carry the DER encoding of the chain (`encodeSchemaChainBytes` output) and its leaf digest; make `ObjectCodec.encodeSchemaChainBytes` and `StreamSchemaDedup.encodeSite` use them. Replace per-call `MessageDigest.getInstance` with a cached/thread-confined digest (or drop the call entirely where the cached digest serves). The chain bytes must be treated as immutable (defensive copy on hand-out, or wrap so no caller can mutate the cached array). | general-purpose · HIGH | sonnet | single reviewer + golden fixture + `schema/` tests | T0 |
| **T2** · Single-buffer `DerWriter` | Replace the allocate-and-copy-per-level builder with a growable buffer encoder producing identical bytes: either **back-to-front** (write content, then length, then tag into a buffer that grows at the front; final array is one slice) or **two-pass** (size pass computing every TLV length, then one forward write). Keep the existing static API as a façade over the new encoder so call sites outside `DerWriter` do not change in this task (a follow-up can migrate them to a streaming API). `writeSequence`/`writeSet` ordering rules (X.690 §11.6 octet-sort for SET) unchanged. Property test: for random nested TLV trees, new encoder ≡ old encoder byte-for-byte (keep the old implementation in the test tree as the reference). | general-purpose · HIGH | sonnet | single reviewer + `ConformanceVectorsTest` + property test + golden fixture | T0 (T1 independent, can run in parallel) |
| **T3** · Direct dedup emission and resolution | Restructure `StreamSchemaDedup` so the encoder consults the per-stream table *while writing* the record (first occurrence → full chain, else `chainRef`), and the decoder resolves a `chainRef` to the already-verified chain and hands the record codec a chain **by reference** instead of reconstituting full-form bytes. Preserve, verbatim: pre-order first-occurrence rule (C.6.2), per-stream table created at open and discarded with the codec (C.7), fail-closed resolution (C.7.4: unknown digest, duplicate full form, malformed arm, ceiling breach → hard reject), all ceilings. The decoder-facing invariant ("observes exactly the bytes a canonical full-form stream would have delivered") is preserved as a *logical* equivalence: `MarshalledInstanceCodec` must receive the same `(schemaDigest, chain, payload)` triple it receives today, which may require giving it an entry point that accepts a resolved chain rather than bytes. | general-purpose · XHIGH (wire-format adjacent; the dedup layer is a security spine) | top-tier | security-literate reviewer + `DerStreamSchemaDedupTest` complete pass + golden fixture (3-element streams exercise refs) + adversarial rejects re-run (G13) | T1, T2 |
| **T4** · Decode-side schema fast path | After class resolution, compare transmitted chain bytes with the resolved class's cached canonical encoding (from T1). On `Arrays.equals`: use the cached parsed chain and cached digest, skip `SchemaChain.decodeChain`. On inequality: today's path, unchanged. Tests: equal → same decoded object as slow path (assert both paths on the same input); every existing evolution/mismatch test still takes the slow path and still rejects/evolves identically; a chain that is digest-equal but byte-unequal (constructed in test) takes the slow path. | general-purpose · HIGH | sonnet | security-literate reviewer (ordering per §3.4) | T1 |
| **T5** · Proportional bounded input read | Replace `readNBytes(max + 1)` with a bounded read that starts small (e.g. `Math.max(256, in.available())`) and grows geometrically, still capped at `max + 1` with the identical oversize rejection. Keep "buffer whole input before decode". `DerInputLimitsTest` boundary pairs (accepted at `max`, rejected at `max + 1`) must pass unchanged; add a test with a slow/chunked `InputStream` to prove the loop reads to EOF. | general-purpose · MEDIUM | sonnet | single reviewer | T0 |
| **T6** · Measurement + closeout | Re-run `jgdms-benchmarks/serialization` on the same DirtyChai machine; add the before/after pair as a dated section in `RESULTS.md` with the DER/before ratios; update `investigation-der-codec-hotspots-2026-10-18.md` status to reflect what landed and what each change actually bought (describe reality, not the targets in §1). Re-profile (`-prof jfr`) and list the new top-5 self-time frames so the next SOW starts from data. | general-purpose · MEDIUM | sonnet | single reviewer | T1–T5 landed |

**Sequencing:** T0 → {T1 ‖ T2 ‖ T5} → T4 → T3 → T6. T1 and T2 are independent and
small; T4 needs T1's cache; T3 is last because it is the largest and benefits
from T1 (no chain re-encoding to restructure around) and T2 (a writer that can
be driven incrementally). T6 may be run once after T1/T2/T5 for an interim data
point if T3 is deferred.

## 5. Non-goals

- **No wire-format change**, no new tags, no spec text, no version negotiation.
  Anything that would require regenerating the §3.1 fixture is out of scope.
- **No change to what is verified or when.** Schema verification, canonical-form
  checks, ceilings, dedup fail-closed rules and class-resolution discipline are
  unchanged; this SOW moves bytes less, it does not check less.
- **Not the JERI mux.** `maxFragmentSize = 1024` and the per-fragment synchronous
  `waitForIO` in `MuxOutputStream` are the ceiling for any payload over 1 KiB in
  both formats (`RESULTS.md` finding 5); that is a transport investigation with
  its own protocol-compatibility question and gets its own SOW.
- **Not `GetArg.callerClass`.** The stack walk is the hierarchy-namespace
  isolation. Not negotiable here.
- **Not class-resolution caching.** ~2% of decode; touches the Warres path; not
  worth it without a dedicated review.
- **Not JOSS.** `AtomicMarshalInputStream`'s construction overhead (`RESULTS.md`
  finding 1) is documented and left alone; JOSS is being removed.

## 6. Open questions (implementer proposes, maintainer decides)

1. **T2 encoder shape:** back-to-front single buffer (fewer passes, slightly
   unusual to read) vs two-pass size-then-write (simpler, computes lengths
   twice). Recommendation: back-to-front, with the old builder kept in the test
   tree as the reference oracle for the property test.
2. **T3 entry point for `MarshalledInstanceCodec`:** add an overload accepting a
   resolved chain, or keep the bytes-in signature and pass the cached canonical
   chain bytes by reference (no copy) — the latter keeps the "same bytes" invariant
   literal at the cost of one `Arrays.equals` the codec may already do.
3. **T1 cache placement:** inside `SchemaGenerator.CHAIN_CACHE`'s result object
   (one cache, one invalidation story) vs a parallel `ClassValue` in `ObjectCodec`.
   Recommendation: inside the generator's result, since the bytes are a pure
   function of the chain it already owns.
4. **Where the hex fixtures live and how large:** one file per payload class vs
   one file for the corpus. Recommendation: one file per class, so a diff names
   the class that changed.

## 7. Agent brief (paste-ready)

> You are working in `JGDMS/jgdms-der` (and `jgdms-benchmarks/serialization`).
> Read, in this order: `docs/SOW-DER-Codec-Performance.md` (this file),
> `docs/investigation-der-codec-hotspots-2026-10-18.md`,
> `jgdms-benchmarks/serialization/RESULTS.md`, `JGDMS-STD-006-Appendix-C-Stream-
> Schema-Dedup-v0.1-DRAFT.md` §C.5–C.7, and `JGDMS-Board-Reviewer-Guidance.md`
> G1/G3/G13. Implement task **T<n>** only. The output bytes of the encoder and the
> accept/reject decisions of the decoder must not change: the golden-bytes
> fixture from T0 and the existing `jgdms-der` test suite are the oracle, and a
> fixture mismatch means stop and report, not regenerate. Build and run the tests
> on DirtyChai from the JGDMS root (`.mvn/maven.config` is required for the
> Pack200 repository). Do not touch `GetArg`, `ResolutionContext`,
> `DerInputLimits` ceilings, or any spec document. Report: what changed, the test
> run, and (for T6) the before/after benchmark pair.

## 8. Implementation notes (2026-10-18; T0–T5 landed, T6 pending)

Recorded so the next reader works from what was built, not from the plan. All numbers are
from the Linux container on DirtyChai 27 and are indicative until T6 re-runs the full harness.

- **Oracle.** `DerGoldenBytesTest` holds 31 payloads (the 21 from T0 plus sets, a two-chain
  polymorphic set, map values, `Chain[]`, self-recursion, a proxy field whose handler holds a
  nested record, and top-level `[9]` arrays and a `[8]` proxy), each alone and three-in-a-stream.
  The 10 added in T3 were generated from the ORIGINAL trunk (`9adc8e6e3`) and the T1/T2/T4/T5 code
  reproduces all 31 byte-for-byte. A second test decodes each stream and re-encodes it to the same
  bytes (decode-side oracle).
- **T2 is only the façade half.** `DerWriter` now allocates once per call (no per-call tag/length
  arrays, no intermediate concatenation for `writeSequence(List)`/`writeSet(List)`). The copy per
  nesting level remains: callers pass already-built child arrays, and §4 forbids changing call
  sites in T2. Removing it needs the call sites (`ObjectCodec`, `StreamSchemaDedup`,
  `DerObjectStreamCodec`) migrated to a streaming encoder — a follow-up.
- **T4 keeps the validate-then-resolve order.** `DerObjectStreamCodec.decodeAtomicRecord` still
  fully validates the transmitted chain before resolving the class it names, so an attacker-named
  class is not resolved ahead of chain validation (§3.4 as written would resolve first). The fast
  path therefore removes the SECOND chain parse only. Skipping the first needs a security-board
  decision.
- **T3 as written is not implementable for the encoder; what was built instead.** §4 T3 has the
  encoder consult the per-stream table "while writing". That cannot preserve the wire form:
  a `SET OF` is octet-sorted (X.690 §11.6) over the canonical FULL-form element bytes, and
  Appendix C §C.6.2 defines "first occurrence" over that WIRE order. Dedup in iteration order would
  put the `fullChain` on a different element than the first one in wire order, and a conforming
  decoder would then see a `chainRef` before its `fullChain` and hard-reject the stream. The
  encode therefore stays two-phase (canonical encode and sort, then substitute sites in wire
  order), and decode must still resolve sites eagerly in wire order (a lazy per-field decode reads
  fields in constructor order, not wire order). Appendix C is unchanged. T3 instead removed the
  redundant work around that pass:
  - encode: a bytes-keyed index over the table (a repeated chain costs one content lookup, not a
    re-hash), and the record's own chain recognised as this node's locally generated chain
    (`SchemaGenerator.verifiedLocalChain`, verified through `SchemaChain.decodeChain` once per
    class) instead of re-parsed and re-hashed per stream;
  - decode: reconstitution returns the record's verified chain, handed to the record codec by
    reference (new `decodeMarshalledInstance` overload; the `schemaDigest` field is still checked),
    and nested records consult the stream's table through a `ScopedValue` so a chain byte-identical
    to one the table verified is not re-parsed and re-hashed per element. Anything else (including
    chains inside `[8]` regions) takes the unchanged path.
  What remains is the byte-level interface itself: reconstitution rebuilds bytes at every
  nesting level, and each distinct chain is parsed and hashed once per stream.
- **Measured so far (µs/op, DER, quick run, `-f 1 -wi 3 -i 4`).**

  | | baseline | after T1/T2/T5 | after T4 | after T3 |
  |---|---:|---:|---:|---:|
  | marshal point | 2.64 | 1.91 | – | 1.21 |
  | marshal record | 4.05 | 2.72 | – | 1.85 |
  | marshal batch100 | 454 | 317 | – | 217 |
  | unmarshal point | 36.1 (noisy) | 13.1 | 8.5 | 8.3 |
  | unmarshal record | 20.7 | 19.4 | 15.6 | 14.0 |
  | unmarshal batch100 | 1647 | 1608 | 1524 | 1424 |

  `blob64k` is dominated by copies the façade cannot remove and was too noisy to quote.

---
Drafted with AI assistance (Claude) under the direction of the JGDMS maintainer;
source line references verified against trunk at 754700b (2026-10-14).
