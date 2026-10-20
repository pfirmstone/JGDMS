# JOSS vs DER: reference results, 18 October 2026

Run of the benchmarks in this directory on the maintainer's workstation,
with a follow-up profile of the three largest anomalies. Numbers are JMH
average time per operation; `±` is the 99.9% confidence interval across 2
forks × 5 iterations.

## Environment

| | |
|---|---|
| JDK | DirtyChai `27-internal` (`C:\Program Files\DirtyChai\jdk`), OpenJDK 64-Bit Server VM |
| OS / CPU | Windows, Intel Xeon E5-1650 v3 @ 3.5 GHz |
| JGDMS | trunk, jars built 13 Oct 2026 (`jgdms-platform`, `jgdms-der`, `jgdms-jeri` 4.0.0-SNAPSHOT) |
| SecurityManager | none installed |
| JMH | 1.37; 2 forks; 5 × 1 s warmup, 5 × 2 s measurement (codec); 5 × 2 s, 5 × 3 s (JERI); 1 thread |
| JERI transport | `TcpServerEndpoint` on 127.0.0.1, DGC off, connection warm |

A second, corroborating run was made on a Linux cloud VM with stock OpenJDK
21 (`jgdms-der` recompiled from trunk source with `--enable-preview` for
`ScopedValue`). Its ratios agree with the DirtyChai run on every row, so the
findings below are not Windows- or DirtyChai-specific. Only the DirtyChai
figures are quoted; the stock-JDK figures are noted where they add
information.

## Wire size (bytes per object, one fresh stream)

| payload  | JOSS   | DER    | DER/JOSS |
|----------|-------:|-------:|---------:|
| point    |     83 |    118 |     1.42 |
| record   |    264 |    288 |     1.09 |
| batch100 | 11 237 | 13 835 |     1.23 |
| blob64k  | 65 638 | 65 664 |     1.00 |

DER's self-describing schema costs ~35 bytes on the smallest object and
~2.6 KB on 100 records (the per-element field framing); on bulk bytes the
two are identical. Consistent with STD-006's own statement that DER is not
optimised for the smallest wire.

## Codec tier (no network)

| benchmark | payload  |             JOSS |              DER | DER/JOSS |
|-----------|----------|-----------------:|-----------------:|---------:|
| marshal   | point    |      6.07 ± 1.11 |      3.52 ± 0.33 | **0.58** |
| marshal   | record   |      8.25 ± 3.08 |      4.48 ± 0.20 | **0.54** |
| marshal   | batch100 |   194.86 ± 22.32 |   366.61 ± 20.02 |     1.88 |
| marshal   | blob64k  |      7.81 ± 0.69 |   137.08 ± 3.88  |    17.55 |
| unmarshal | point    |   102.60 ± 5.27  |    16.44 ± 1.66  | **0.16** |
| unmarshal | record   |   115.81 ± 8.34  |    23.13 ± 1.84  | **0.20** |
| unmarshal | batch100 | 1420.68 ± 180.20 | 1710.46 ± 114.19 |     1.20 |
| unmarshal | blob64k  |   126.74 ± 9.49  |   113.43 ± 3.61  |     0.90 |
| roundTrip | point    |   107.75 ± 7.51  |    19.62 ± 0.81  | **0.18** |
| roundTrip | record   |   121.37 ± 4.81  |    27.91 ± 1.06  | **0.23** |
| roundTrip | batch100 | 1514.06 ± 60.85  | 2086.49 ± 102.27 |     1.38 |
| roundTrip | blob64k  |   141.89 ± 6.47  |   258.55 ± 11.21 |     1.82 |

(µs/op.)

## JERI tier (TCP loopback, full remote call)

| method   |               JOSS |                DER | DER/JOSS | JOSS − ping | DER − ping |
|----------|-------------------:|-------------------:|---------:|------------:|-----------:|
| ping     |     240.47 ± 7.85  |     125.22 ± 3.47  | **0.52** |           – |          – |
| point    |     357.36 ± 17.73 |     191.02 ± 3.02  | **0.53** |         117 |         66 |
| record   |     373.57 ± 4.10  |    291.16 ± 113.81 |     0.78 |         133 |        166 |
| batch100 |  3382.34 ± 787.32  |  6689.49 ± 544.42  |     1.98 |        3142 |       6564 |
| blob64k  |  9909.70 ± 1737.06 |  9675.81 ± 1099.46 |     0.98 |        9669 |       9551 |

(µs/op. The last two columns are the payload's cost to the call: method
time minus the empty `ping` in the same format.)

## What the numbers say

**1. For small messages, DER calls are about half the cost of JOSS calls
— but mostly because of fixed per-stream overhead in `AtomicMarshalInputStream`,
not because of the protocol.**

Unmarshalling a two-int `Point` through a fresh JOSS stream costs ~103 µs;
the 64 KiB blob costs 127 µs. The ~100 µs is therefore a fixed cost paid per
input stream, and a stack profile (`-prof stack`, stock JDK 21) puts it in the
constructor: `AtomicMarshalInputStream.<init>` and `resetSeenObjects()`
account for ~37% of runnable samples between them, with `NoSuchMethodException`
construction and `SerializationConstructor` setup behind them. Reading the
code:

- the stream allocates `Object[MAX_OBJECT_CACHE]` with `MAX_OBJECT_CACHE =
  65664` (a quarter of a megabyte of references, zeroed by the allocator),
  and `resetSeenObjects()` then nulls every one of the 65 664 slots again by
  hand inside a `synchronized` block;
- `new SerializationConstructor()` builds a full `ObjectOutputStream` and
  `ObjectInputStream` pair on every stream, whether or not a replacement
  object is ever constructed;
- `ObjectStreamClassContainer.setClass` looks up `readObject`/`readResolve`
  reflectively and catches `NoSuchMethodException` (with a stack trace) for
  every class descriptor in every stream.

JERI creates two such streams per call (request on the dispatcher, response
on the handler), which is where the 115 µs gap between JOSS `ping` and DER
`ping` comes from. All three costs are removable without touching the wire
format (grow the handle table lazily, drop the redundant nulling, build the
`SerializationConstructor` on first use, cache the per-class reflective
lookups). After such a fix the small-message comparison would need
re-running; DER would likely still lead on `marshal` (0.54–0.58, which is a
protocol-level result) but the 5× on `unmarshal` should largely disappear.

**2. Per object, DER currently costs more CPU than JOSS: ~1.9× to marshal
and ~1.3× to unmarshal.**

Subtracting the single-record cost from `batch100` gives the marginal cost of
one more `Record` in a stream of the same class: JOSS ~1.9 µs marshal /
~13 µs unmarshal; DER ~3.7 µs / ~17 µs. JOSS writes a class descriptor once
and back-references it; DER re-frames every field of every element with
tag+length and canonicalises, and the schema is checked per element. Those
are the costs STD-006 chose to pay for canonical form and schema
verification, and they show up as expected. (~13 µs to unmarshal four fields
is itself slow for both formats; the profiles show `AtomicSerial$GetArg
.callerClass` walking the stack via `StackWalker` on field lookups, a shared
cost worth its own look.)

**3. `DerWriter` copies the payload once per nesting level.**

Marshalling the 64 KiB blob costs DER 137 µs against JOSS's 7.8 µs. The
profile is `DerWriter.writeTlv`/`writeSequence` at successive nesting
levels (`writeOctetString` → field SEQUENCE → `ObjectCodec.encodeHierarchy` →
`MarshalledInstanceRecord.encode` → stream unit). `DerWriter` is a functional
builder: each `writeTlv` allocates a new `byte[]` and `System.arraycopy`s
the content into it, so a definite-length encoding of a 64 KiB octet string
nested six deep allocates and copies ~400 KiB. The usual remedy is a
back-to-front encoder into one buffer (lengths are known once the inner
content is written) or a two-pass length computation. DER *unmarshal* of the
blob is fine (113 µs, 0.90× JOSS) because reading needs no such copies.

Also visible in that profile: `StreamSchemaDedup.sha256` calling
`MessageDigest.getInstance("SHA-256")` per encode site; a cached digest
instance (or a per-class cached digest) would remove a provider lookup from
every stream.

**4. Over JERI, DER's batch costs 2× JOSS's, and the codec tier does not
explain it.**

The codec round trip predicts a JERI batch call at roughly 2 × roundTrip +
ping: JOSS ≈ 3.3 ms (observed 3.4 ms, a good match); DER ≈ 4.3 ms, but
6.7 ms is observed. The extra ~2.4 ms is not in the codec. A stack profile of
the DER JERI batch call is dominated by socket reads, `EPoll.wait`,
`StreamConnectionIO$Writer` waiting and `MuxOutputStream.waitForIO`, i.e. the
call is waiting on the mux, not computing. The codecs drive the mux
differently: `ObjectOutputStream` emits 1 KiB block-data chunks as it goes,
whereas `DerMarshalOutputStream` accumulates the whole encoding and drains it
on `flush()`; and `DerMarshalInputStream` buffers its input up to
`DerInputLimits` before decoding. Which of these interactions with the mux's
flow control costs the 2.4 ms is **not established by this run** and is the
first thing to investigate (`-prof` with a mux-aware view, or timing the
`drainTo`/read phases separately).

**5. Bulk bytes are transport-bound in either format.**

The 64 KiB blob takes ~9.7 ms over JERI in both formats, against 0.14–0.26 ms
in the codec. 128 KiB moved per call in 9.7 ms is about 13 MB/s on Windows
loopback (the Linux run gave 4.3–5.4 ms, ~25–30 MB/s). The serialization
format is irrelevant at this size; the JERI mux is. That is a finding about
the transport, not about this comparison, but it bounds what any codec
change can buy for large payloads.

## What this means for the JOSS → DER migration

- As shipped today, a JERI call with a small payload is roughly half the
  cost over DER as over JOSS (`ping` 125 vs 240 µs, `point` 191 vs 357 µs),
  and DER encodes small objects faster (0.54–0.58×).
- The largest part of that advantage is an avoidable fixed cost in the JOSS
  atomic input stream (finding 1), not a protocol property. A claim that "DER
  outperforms Java serialization" should not be made on these numbers alone;
  the honest statement is that the DER stack is cheaper per call as
  implemented, and that the JOSS stack has a known, fixable ~100 µs/stream
  construction overhead.
- Where the protocol itself is the cost, DER is currently the more expensive
  format per object (finding 2) and has a clear O(depth × size) copying
  inefficiency on large fields (finding 3). Both are implementation choices
  in `jgdms-der`, not consequences of STD-006.
- For large payloads the question is moot until the mux's throughput is
  addressed (finding 5).

## Not covered

- No SecurityManager. With one installed, both formats pay the same policy
  and ACC costs on the dispatch path; `../harness` measures those.
- One thread. `-t N` gives throughput under concurrent callers.
- Allocation (`-prof gc`) was not collected on DirtyChai; the copying in
  findings 1 and 3 will show there directly.
- Only `TcpServerEndpoint`; TLS (`SslServerEndpoint`) adds a per-byte cost
  that would narrow the relative gap on bulk payloads further.
- Payload classes are simple; a `ServiceItem` with a proxy would exercise
  class resolution and the `MarshalledInstance` container, neither of which
  is on these paths.

## Reproducing

    JAVA_HOME=<DirtyChai> ./run.sh -rf json -rff results.json
    python3 summarise.py results.json

The DirtyChai run's raw JMH output is in `results.json`. The profiles quoted
above were taken on the stock-JDK-21 run with
`-prof "stack:lines=6;top=10"` on `CodecBenchmark.unmarshal` (JOSS, point),
`CodecBenchmark.marshal` (DER, blob64k) and `JeriBenchmark.batch100` (DER).

---
Written with AI assistance (Claude) under the direction of the JGDMS
maintainer; the profiling conclusions were checked against the source files
named above.


---

# Second run: after the DER codec performance work, 18 October 2026

`docs/SOW-DER-Codec-Performance.md` tasks T0–T5 landed (byte-identical output, verdict-identical
decode). This is the before/after pair that SOW requires, taken on the same machine and JDK, DER
only (JOSS is being removed and was not re-run).

| | |
|---|---|
| JDK | DirtyChai `27-internal`, OpenJDK 64-Bit Server VM |
| Host | Linux cloud container (NOT the workstation of the first run above, so absolute times are not comparable with it; only the before/after ratios below are) |
| JMH | 1.37; 2 forks; 5 × 1 s warmup, 5 × 2 s measurement (codec); 5 × 2 s, 5 × 3 s (JERI); 1 thread |
| Before | `results-before.json`: trunk `9adc8e6e3` (before T1) |
| After | `results-after.json`: T0–T5 (this branch) |

Average time per operation, µs, ± the 99.9% confidence interval:

| benchmark | payload | before | after | after/before |
|---|---|---:|---:|---:|
| marshal | point | 2.64 ± 0.09 | 5.40 ± 18.83 \* | – |
| marshal | record | 4.05 ± 0.24 | 1.76 ± 0.09 | 0.43 |
| marshal | batch100 | 454.06 ± 23.68 | 224.04 ± 13.92 | 0.49 |
| marshal | blob64k | 276.04 ± 6.12 | 189.55 ± 17.42 | 0.69 |
| unmarshal | point | 36.06 ± 102.12 \* | 10.26 ± 9.13 | – |
| unmarshal | record | 20.66 ± 1.71 | 14.06 ± 1.56 | 0.68 |
| unmarshal | batch100 | 1647.26 ± 75.56 | 1498.92 ± 494.50 | 0.91 |
| unmarshal | blob64k | 193.86 ± 6.97 | 200.94 ± 6.38 | 1.04 |
| roundTrip | record | 23.83 ± 1.54 | 19.23 ± 10.60 | 0.81 |
| roundTrip | batch100 | 2177.79 ± 107.87 | 1658.05 ± 97.96 | 0.76 |
| roundTrip | blob64k | 485.10 ± 16.08 | 411.36 ± 18.44 | 0.85 |
| JERI | ping | 226.08 ± 12.47 | 198.43 ± 13.85 | 0.88 |
| JERI | point | 320.19 ± 18.86 | 297.00 ± 37.46 | 0.93 |
| JERI | record | 359.30 ± 37.19 | 331.15 ± 43.34 | 0.92 |
| JERI | batch100 | 9391.63 ± 571.45 | 7521.43 ± 596.87 | 0.80 |
| JERI | blob64k | 15790.13 ± 486.55 | 14541.95 ± 1044.58 | 0.92 |

\* The `point` rows are unreliable: their error intervals exceed the score (the `point` cases
are dominated by JIT/GC jitter on this shared host). Shorter runs of the same code measured
`marshal point` at about 1.2 µs and `unmarshal point` at about 8.3 µs against 2.6 and 13 before,
but those are not the protocol-grade 2 × 5 run and are not quoted as results. `roundTrip point`
(12.82 ± 9.53 after vs 24.60 ± 34.08 before) and `roundTrip record` (19.23 ± 10.60 after, ratio
0.81) are likewise within noise. The raw per-iteration data show the `point` anomalies are single slow
first iterations (marshal `point` after: one fork opened at 40.8 µs; unmarshal `point` before: one
fork opened at 228 µs). Medians are the better summary for those rows: marshal `point` 2.64 µs
before, 1.27 µs after (0.48); unmarshal `point` 13.72 µs before, 8.35 µs after (0.61).

## What the numbers say

- **Marshal got about twice as fast** for `record` (0.43) and `batch100` (0.49). That is the encode
  side of T1 (chain bytes and digest cached per class), the cheaper dedup pass (T3: repeated chain
  sites cost one content lookup; the record's own chain is not re-parsed and re-hashed per stream)
  and the single-allocation writer (T2).
- **Unmarshal `record` is about a third faster** (0.68): T4 and T3 remove the repeated chain parses.
  `batch100` moved little (0.91, with a wide interval): its cost is the actual per-element decode
  plus the reconstitution pass that rebuilds bytes at every nesting level, which the SOW's
  byte-identical, call-sites-unchanged constraints leave in place.
- **`blob64k` unmarshal did not change** (1.04, within noise): T5 saves a fixed 16 KiB allocation
  per stream, small against a 64 KiB payload. `blob64k` marshal is about 30% faster; it is still
  several whole-array copies (one per nesting level), not the "within ~2× of a single copy" the
  SOW hoped for, because that needs the call sites migrated to a streaming encoder.
- **A remote call (JERI): only `batch100` clearly improves (0.80).** The other JERI rows moved
  0.88–0.93, but `JeriBenchmark.ping` (an empty call with essentially no codec work) moved 0.88
  between the two runs, so about 12% of any JERI ratio is run-to-run drift on this shared host;
  `point` (0.93), `record` (0.92) and `blob64k` (0.92) have overlapping confidence intervals and
  are not distinguishable from it. The transport is shared and unchanged, and the codec is a
  fraction of a loopback call (about 1.6 ms of a 7.5 ms `batch100` call).
- The SOW's targets (small-object marshal ≈ halved; multi-object ≈ 30% faster both ways; `blob64k`
  marshal in tens of µs) are met for marshal, partly met for unmarshal, and not met for `blob64k`.

## Where the time goes now (JFR `-prof jfr`, `jfr-hotspots.py`, `au.net.zeus.jgdms.der.*`, self time)

| marshal `record` | marshal `batch100` | unmarshal `record` | unmarshal `batch100` |
|---|---|---|---|
| `DerWriter.frame` 8.1% | `DerWriter.frame` 17.7% | `DerReader.decodeLength` 3.4% | `DerWriter.frame` 4.6% |
| `DerWriter.writeTlv` 7.4% | `DerWriter.writeTlv` 7.4% | `AtomicSerialSchemaRecord.decode` 3.0% | `DerWriter.writeTlv` 3.6% |
| `DerReader.decodeLength` 2.7% | `DerReader.decodeLength` 6.8% | `DerObjectStreamCodec.readObject` 2.6% | `StreamSchemaDedup.rebuildCollectionOuter` 3.1% |
| `DerObjectStreamCodec.drainTo` 2.6% | `DerWriter.lengthSize` 5.2% | `DerInputLimits.readAllBytesBounded` 2.2% | `DerReader.decodeLength` 3.1% |
| `DerPutArg.put` 2.6% | `StreamSchemaDedup.transformFieldTlv` 3.8% | `WireTypes.decode` 2.2% | `StreamSchemaDedup.transformClassSequence` 2.6% |

(Samples are few for the unmarshal rows, 196–232, so their order is indicative.) What dominates is
now copying: `DerWriter.frame`/`writeTlv` is the byte-array-per-nesting-level builder, and the
dedup pass's reading and rebuilding of the same bytes. Chain parsing and hashing no longer appear
near the top. The next SOW should start from the streaming-encoder migration (an encoder that
writes into one buffer, used by `ObjectCodec`, `StreamSchemaDedup` and `DerObjectStreamCodec`),
which removes the per-level copies both directions pay.

---

# Third run: after the streaming-encoder work (SOW-DER-Streaming-Encoder A0-A4), 18 October 2026

DER only, same container, JDK (DirtyChai `27-internal`) and JMH protocol as the second run; files `results-a5.json` (full run), `results-a5-gc.json`
(`-prof gc`), `results-before.json` / `results-after.json` unchanged. Average time per operation, us, +/- the 99.9% interval:

| benchmark | payload | before (`9adc8e6e3`) | after T0-T5 | now (A0-A4) | now/before | now/after T0-T5 |
|---|---|---:|---:|---:|---:|---:|
| marshal | record | 4.05 ± 0.24 | 1.76 ± 0.09 | 1.56 ± 0.10 | 0.39 | 0.89 |
| marshal | batch100 | 454.06 ± 23.68 | 224.04 ± 13.92 | 107.54 ± 9.49 | 0.24 | 0.48 |
| marshal | blob64k | 276.04 ± 6.12 | 189.55 ± 17.42 | 84.97 ± 3.78 | 0.31 | 0.45 |
| unmarshal | record | 20.66 ± 1.71 | 14.06 ± 1.56 | 9.39 ± 0.90 | 0.45 | 0.67 |
| unmarshal | batch100 | 1647.26 ± 75.56 | 1498.92 ± 494.50 | 789.77 ± 82.90 | 0.48 | 0.53 |
| unmarshal | blob64k | 193.86 ± 6.97 | 200.94 ± 6.38 | 176.13 ± 66.10 | 0.91 | 0.88 |
| roundTrip | record | 23.83 ± 1.54 | 19.23 ± 10.60 | 11.12 ± 1.08 | 0.47 | 0.58 |
| roundTrip | batch100 | 2177.79 ± 107.87 | 1658.05 ± 97.96 | 865.58 ± 75.79 | 0.40 | 0.52 |
| roundTrip | blob64k | 485.10 ± 16.08 | 411.36 ± 18.44 | 264.27 ± 26.97 | 0.54 | 0.64 |
| JERI | ping | 226.08 ± 12.47 | 198.43 ± 13.85 | 134.00 ± 11.83 | 0.59 | 0.68 |
| JERI | point | 320.19 ± 18.86 | 297.00 ± 37.46 | 182.54 ± 30.07 | 0.57 | 0.61 |
| JERI | record | 359.30 ± 37.19 | 331.15 ± 43.34 | 198.49 ± 21.97 | 0.55 | 0.60 |
| JERI | batch100 | 9391.63 ± 571.45 | 7521.43 ± 596.87 | 4672.40 ± 341.23 | 0.50 | 0.62 |
| JERI | blob64k | 15790.13 ± 486.55 | 14541.95 ± 1044.58 | 9961.05 ± 520.59 | 0.63 | 0.68 |

The `point` rows (error intervals larger than the score in the earlier runs) are left out of the table. `unmarshal blob64k` now has an interval of 66 us
on 176 us and is within noise of the second run.

## What the numbers say

- **Read the JERI rows as drift, not codec gain.** `ping` (an empty call, no codec work) fell from 226 to 134 us (0.59) between the first and this run, so this
  container was running about 40% faster than when the baseline was taken. Every JERI ratio, and to a smaller extent every codec ratio against `before`, carries
  that; the `now/after` column (second run to now, 0.6-0.7 for `ping`) shows the drift on its own. A fair JERI reading is "not distinguishable from drift except
  perhaps `batch100`", the same conclusion as the second run. Re-run on the workstation before quoting any ratio.
- **Marshal is where A0-A4 show up clearly:** `batch100` 0.48 and `blob64k` 0.45 of the second run (drift is at most 0.6-0.7 here, so these exceed it); `record` 0.89 is within
  drift. Against the original baseline marshal is 0.39 / 0.24 / 0.31 of before.
- **Unmarshal improved too** (A3 removed the per-level copies in reconstitution): `record` 0.67 and `batch100` 0.53 of the second run, comparable to the drift, so treat them as
  probable rather than proven; `blob64k` unchanged.
- **Targets of the first SOW** (small-object marshal about halved; multi-object about 30% faster both ways; `blob64k` marshal in tens of us): met for marshal, and now for
  multi-object unmarshal; `blob64k` marshal is 85 us, not tens.

## Allocation per operation (`-prof gc`, `gc.alloc.rate.norm`, B/op)

| payload | marshal | unmarshal |
|---|---:|---:|
| record | 5 864 | 16 336 |
| batch100 | 288 855 | 975 086 |
| blob64k | 528 520 | 927 657 |

There is no baseline `-prof gc` run (the baseline code was not re-built for this), so no before/after allocation ratio is claimed. For the write side only, the plain-loop phase
bench (`DerStreamPhaseBench`, thread allocation counters) measured before/after A4: `batch100` 436 to 276 KB, `blob64k` 792 to 463 KB, `record` 7.6 to 5.7 KB per encode. Unmarshal still
allocates about 15 bytes per wire byte for `batch100` and about 14x for `blob64k` (the read side still copies a `byte[]` per TLV and was not part of A4).

## Where the time goes now (JFR `-prof jfr`, `jfr-hotspots.py`, `au.net.zeus.jgdms.der.*`, self time)

| marshal `record` | marshal `batch100` | unmarshal `record` | unmarshal `batch100` |
|---|---|---|---|
| `CompactDerEncoder.leaf` 7.8% | `CompactDerEncoder.toByteArray` 7.8% | `CompactDerEncoder.<init>` 3.7% | `DerFieldStore.decodeAllFields` 2.9% |
| `CompactDerEncoder.beginFrame` 3.9% | `Tag.encodeInto` 5.9% | `StreamSchemaDedup.<init>` 3.2% | `CompactDerEncoder.toByteArray` 2.9% |
| `StreamSchemaDedup.<init>` 3.7% | `DerWriter.writeHeader` 4.6% | `DerObjectStreamCodec.readObject` 2.6% | `DerReader.decodeLength` 2.9% |
| `CompactDerEncoder.toByteArray` 3.1% | `CompactDerEncoder.leaf` 3.4% | `DerReader.decodeLength` 2.1% | `CompactDerEncoder.beginFrame` 2.9% |
| `CompactDerEncoder.<init>` 3.0% | `CompactDerEncoder.endFrame` 3.3% | `Tag.decode` 2.1% | `MarshalledInstanceRecord.decode` 2.4% |

Samples: 881 / 885 / 190 / 170 (the unmarshal rows are indicative only). Compared with the second run: `DerWriter.frame`/`writeTlv`, the byte-array-per-level builder, and the
dedup `rebuildCollectionOuter`/`transformFieldTlv` frames are gone from the top; what remains is the encoder's own work (header writing, the single compaction pass) and, for the
small `record`, per-stream construction cost: `CompactDerEncoder.<init>` and `StreamSchemaDedup.<init>` together are about 7% of a one-object marshal and of an unmarshal. That is the
next cheap target (lazily sized encoder arrays, a pooled or reused dedup table per connection rather than per stream), and it matters only for tiny objects.


# Fourth run: after whole-stream admission (SOW-DER-Streaming-Encoder B2), 18 October 2026

Same container, same JMH settings (2 forks x 5 iterations), DER only, `CodecBenchmark.(unmarshal|roundTrip)` and `JeriBenchmark.*`. Compared with the third run's
payload rows (`results-a5.json`; the marshal rows are unaffected by B2 and were not re-run). Data: `results-b2.json` (with the review follow-ups) and `results-b2-firstcut.json` (the first B2 commit).

What B2 adds to a stream read: a second dedup walk over the whole stream, run before the first object is built (and, for a server request stream, only after the caller is authorized).

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

## What the numbers say

- **The added work is a second pass over the stream, so it shows on in-process unmarshal and not on the JERI rows.** `record` unmarshal +2.9 us (31%), `point` +1.0 us (15%), `batch100` +53 us (7%, within noise),
  `blob64k` +7 us (4%, within noise). Every JERI row is within noise; `ping` (no codec work) moved +8% between the runs, which is the size of the drift on the JERI rows.
- **The review follow-ups cost nothing measurable** over the first B2 commit (all ratios 0.86 to 1.09, within noise), and reading the `[1]` record in place recovered about 30 us of `blob64k` unmarshal.
- **The unauthenticated-peer cost (what a peer pays before it is authorized) is the cheap stage only** and was not benchmarked here (SOW B4): it is linear in the already-bounded input and allocates nothing for
  short-form headers.
- **Where the added time is:** chain verification (`SchemaChain.decodeChain` and one SHA-256 of the leaf record) happens once in admission and again in delivery, on separate tables by design. A bytes-keyed verified-chain
  cache shared between the two would remove most of it; it is not built (it couples admission to delivery).
- Container numbers: re-run on the workstation before quoting.


# Fifth run: after the whole-stream policy (SOW-DER-Streaming-Encoder B5), 18 October 2026

Same container and settings as the fourth run (DER only; `results-b5.json`). B5 adds the class-name policy, the object, element and class budgets and the rebinding check to full admission.

| benchmark (us/op, 99.9% error) | A5 | B2 | B5 | B5 - B2 | B5 / A5 |
|---|---:|---:|---:|---:|---:|
| codec round trip batch100 | 865.58 +- 75.79 | 947.92 +- 78.46 | 911.19 +- 89.41 | -36.73 (within noise) | 1.05 |
| codec round trip blob64k | 264.27 +- 26.97 | 299.96 +- 14.33 | 296.38 +- 19.74 | -3.58 (within noise) | 1.12 |
| codec round trip point | 7.85 +- 0.88 | 9.58 +- 1.13 | 9.67 +- 0.50 | +0.09 (within noise) | 1.23 |
| codec round trip record | 11.12 +- 1.08 | 13.96 +- 1.07 | 13.64 +- 0.87 | -0.32 (within noise) | 1.23 |
| codec unmarshal batch100 | 789.77 +- 82.90 | 842.70 +- 165.95 | 763.38 +- 89.71 | -79.32 (within noise) | 0.97 |
| codec unmarshal blob64k | 176.13 +- 66.10 | 183.43 +- 10.51 | 197.40 +- 39.44 | +13.97 (within noise) | 1.12 |
| codec unmarshal point | 6.82 +- 0.96 | 7.82 +- 0.86 | 8.06 +- 0.53 | +0.24 (within noise) | 1.18 |
| codec unmarshal record | 9.39 +- 0.90 | 12.31 +- 1.88 | 11.96 +- 1.47 | -0.35 (within noise) | 1.27 |
| JERI batch100 | 4672.40 +- 341.23 | 4913.44 +- 337.84 | 4599.30 +- 264.59 | -314.14 (within noise) | 0.98 |
| JERI blob64k | 9961.05 +- 520.59 | 10323.90 +- 824.73 | 10243.52 +- 523.46 | -80.39 (within noise) | 1.03 |
| JERI ping | 134.00 +- 11.83 | 145.30 +- 14.26 | 133.27 +- 8.46 | -12.04 (within noise) | 0.99 |
| JERI point | 182.54 +- 30.07 | 181.69 +- 14.11 | 183.43 +- 12.17 | +1.74 (within noise) | 1.00 |
| JERI record | 198.49 +- 21.97 | 214.50 +- 23.41 | 195.41 +- 13.35 | -19.09 (within noise) | 0.98 |

- **No measurable change over B2 (the fourth run):** every difference is inside the intervals, and the in-process unmarshal of `record` (the case that shows an added pass most clearly) is 11.96 us against 12.31. The added work is per distinct chain, per record site and one header-skip pass per collection or reference array that is passed through.
- Against the third run (A5) the cost is still B2's: about +1 us (`point`) and +3 us (`record`) of in-process unmarshal, JERI within noise. Container numbers: re-run on the workstation before quoting.


# Sixth run: cost of admission by stage (SOW-DER-Streaming-Encoder B4), 19 October 2026

`AdmissionBenchmark` (`results-b4.json`), DirtyChai JDK 27, container, 2 forks x 5 iterations; **re-run on the workstation before quoting**. Times are per operation; `floor` is the framed bounded read alone (construction before B2); `cheap` is `forDispatcher` (B2's pre-authorization work); `cheapPlusFull` adds `admitStream()`; `decode` is the public constructor and all reads; `dispatcherPath` is cheap, admit, read; `context` is the authorization-context block (public constructor, full admission before authorization); `unauth+ctx` is `context` plus `cheap`.

| payload | floor | cheap | cheapPlusFull | decode | dispatcherPath | context | unauth+ctx |
|---|---|---|---|---|---|---|---|
| point (us) | 0.26 +- 0.03 | 0.71 +- 0.16 | 2.47 +- 0.47 | 9.13 +- 1.23 | 9.53 +- 1.75 | 27.76 +- 2.33 | 30.79 +- 4.84 |
| record (us) | 0.34 +- 0.03 | 0.82 +- 0.20 | 3.13 +- 0.51 | 13.97 +- 1.99 | 14.79 +- 1.99 | 30.10 +- 3.45 | 33.72 +- 5.30 |
| batch100 (us) | 3.44 +- 3.51 | 3.45 +- 0.82 | 50.62 +- 5.44 | 922.92 +- 138.34 | 1023.66 +- 180.35 | 29.15 +- 5.06 | 34.83 +- 3.44 |
| blob64k (us) | 45.78 +- 14.37 | 182.10 +- 668.62 | 333.86 +- 1241.97 | 226.90 +- 111.76 | 213.26 +- 41.06 | 30.23 +- 4.24 | 78.34 +- 7.20 |
| request8 (us) | 1.73 +- 0.11 | 2.24 +- 0.19 | 14.04 +- 1.43 | 165.52 +- 83.35 | 155.88 +- 26.53 | 33.13 +- 8.82 | 33.47 +- 4.76 |
| blob16m (us) | 9326 +- 756 | 9174 +- 888 | 18767 +- 1864 | 60619 +- 7589 | 61460 +- 9569 | 49 +- 93 | 9476 +- 781 |
| records16m (us) | 11112 +- 512 | 10889 +- 829 | 44929 +- 4019 | 1118809 +- 217732 | 1118776 +- 153503 | 34 +- 6 | 9445 +- 715 |

Streams: `request8` 3 286 bytes; `blob16m` 16 773 248; `records16m` 15 893 920. `blob64k` cheap and cheapPlusFull are GC-outlier noise (intervals larger than the scores).

- The cheap stage is below 1 us over the read on small streams and unmeasurable on 16 MiB streams (inside the intervals): an unauthenticated peer costs the byte read, as before B2.
- The full stage (after `checkAccess` only) is 1.8 to 2.3 us on `point`/`record`, 47 us on `batch100`, 11.8 us on `request8`, 9.6 ms on a 16 MiB blob and 34 ms on 16 MiB of small records (3% of delivering them).
- The side-band authorization-context block is the only full admission before authorization: about 30 us per request in this fixture, independent of the request size.


# Seventh run: cost of the record-interior admission checks (SOW-DER-Streaming-Encoder B3), 19 October 2026

`AdmissionBenchmark` again (`results-b3.json`, same container, JMH 2 forks x 5 iterations; **re-run on the workstation before quoting**), against the sixth run (`results-b4.json`, before B3). Times in microseconds per operation.

| payload | cheap (B4 / B3) | full stage = cheapPlusFull - cheap (B4 / B3) | dispatcherPath (B4 / B3) | decode (B4 / B3) |
|---|---|---|---|---|
| point | 0.71 / 0.70 | 1.76 / 1.91 | 9.5 / 9.9 | 9.1 / 9.4 |
| record | 0.82 / 0.78 | 2.31 / 2.72 | 14.8 / 16.4 | 14.0 / 14.3 |
| batch100 | 3.45 / 3.06 | 47.2 / 70.9 | 1 024 / 1 021 | 923 / 1 067 |
| request8 | 2.24 / 2.36 | 11.8 / 16.1 | 156 / 171 | 166 / 165 |
| blob16m | 9 174 / 8 070 | 9 592 / 9 401 | 61 460 / 54 394 | 60 619 / 53 839 |
| records16m | 10 889 / 9 708 | 34 040 / 61 593 | 1 118 776 / 1 162 404 | 1 118 809 / 1 136 136 |

- The cheap stage (an unauthenticated peer's cost) is unchanged. The full stage, paid only after `checkAccess`, grows by about 0.2 us per record (the decoder's own value checks run on every field, element and key before anything is built); `byte[]` content is checked without a copy, so a 16 MiB blob pays nothing. End to end (dispatcherPath, decode) every row is inside the noise (0.89 to 1.16, intervals overlapping).
- 16 MiB of small records: full stage 34 -> 62 ms (3% -> 5% of delivering them).


# Eighth run: the maintainer's workstation on the current branch, 19 October 2026

Same machine and JDK as the first run (Windows, Xeon E5-1650 v3, DirtyChai `27-internal`, no SecurityManager, JMH 2 forks x 5 iterations), jars installed from this branch
after B3 (`jgdms-platform`, `jgdms-der`, `jgdms-jeri` rebuilt and installed, then the benchmarks packaged). An earlier run of the same day on stale jars reproduced the first run within a few
percent on every row, which makes the first run a sound baseline. The raw file is `results-workstation.json` on the workstation (not committed). Times in microseconds per operation.

## DER against the first run (before any of the streaming-encoder work)

| benchmark | payload | first run | now | now / first |
|---|---|---:|---:|---:|
| marshal | point | 3.52 | 1.52 | 0.43 |
| marshal | record | 4.48 | 2.07 | 0.46 |
| marshal | batch100 | 366.6 | 151.1 | 0.41 |
| marshal | blob64k | 137.1 | 73.7 | 0.54 |
| unmarshal | point | 16.44 | 16.13 | 0.98 |
| unmarshal | record | 23.13 | 25.80 | 1.12 |
| unmarshal | batch100 | 1 710 | 1 547 | 0.90 |
| unmarshal | blob64k | 113.4 | 154.8 | **1.37** |
| roundTrip | point | 19.62 | 20.17 | 1.03 |
| roundTrip | record | 27.91 | 28.38 | 1.02 |
| roundTrip | batch100 | 2 086 | 1 685 | 0.81 |
| roundTrip | blob64k | 258.6 | 242.1 | 0.94 |
| JERI | ping | 125.2 | 131.7 | 1.05 |
| JERI | point | 191.0 | 212.5 | 1.11 |
| JERI | record | 291.2 (+-114) | 237.9 | 0.82 |
| JERI | batch100 | 6 689 | 5 859 | 0.88 |
| JERI | blob64k | 9 676 | 9 710 | 1.00 |

JOSS rows are the control and agree with the first run (for example JERI `ping` 249.6 against 240.5, `batch100` 3 181 against 3 382).

- **Marshal is roughly 2x faster everywhere** (0.41 to 0.54), as A1 to A4 intended. On this machine the gain is smaller for `blob64k` (0.54) than in the container (0.31).
- **Unmarshal has not improved on this machine**: whatever the read-side work saved, the whole-stream admission (B2, B3, B5) added back. Small objects are level (`point` 0.98, `record` 1.12); `batch100` is 10% better; **`blob64k` is 37% worse (113 to 155 us)**, beyond the interval (+-2.4).
- **Calls (JERI)**: small messages are within noise of the first run (`point` +11%, `record` -18%, `ping` +5%); `batch100` is 12% better; `blob64k` is level. DER against JOSS: `ping` 0.53, `point` 0.60, `record` 0.61, `batch100` 1.84, `blob64k` 0.98 (first run: 0.52, 0.53, 0.78, 1.98, 0.98).
- **`batch100` over JERI is still not explained by the codec.** Marshal plus unmarshal of `batch100` is about 1.7 ms and the call is 5.9 ms, 5.7 ms above `ping`, so about 4 ms of a DER call is outside the codec (JOSS: 1.4 ms outside). A4 showed the drain and the bounded read are not it. Not investigated.

## Admission, stage by stage (`AdmissionBenchmark`)

| payload | floor | cheap | full stage (cheapPlusFull - cheap) | dispatcherPath | full stage / dispatcherPath |
|---|---:|---:|---:|---:|---:|
| point | 0.29 | 0.59 | 2.78 | 16.6 | 17% |
| record | 0.36 | 0.66 | 4.09 | 25.1 | 16% |
| batch100 | 2.14 | 2.42 | 82.7 | 1 491 | 5.5% |
| blob64k | 31.3 | 32.4 | 28.7 | 153.5 | 19% |
| request8 | 1.50 | 1.92 | 22.1 | 253 | 8.7% |
| blob16m | 9 578 | 9 750 | 8 807 | 53 496 | 16% |
| records16m | 9 759 | 10 093 | 78 545 | 1 633 567 | 4.8% |

- **An unauthenticated peer** costs the byte read plus 0.3 to 0.4 us on small requests and about 2% (0.2 to 0.3 ms) on 16 MiB streams; the authorization-context block, the one full admission before authorization, is 52 to 57 us here (31 to 34 us in the container, so this machine is about 1.6x slower on that path).
- **The full stage costs about 0.8 us per record** on this machine (`batch100` 82.7 us for 100 records; 16 MiB of records 78.5 ms), 5% of delivering them. For `point` and `record` it is 16 to 17% of the in-process read, and for opaque bytes 16 to 19% (`blob64k` 28.7 us, `blob16m` 8.8 ms): about 0.45 ns a byte.
- **A lead on the `blob64k` unmarshal regression (a hypothesis, not tested):** the verifying walk writes its reconstituted output to a scratch encoder that is always discarded, so a large byte field is copied into that encoder (and again as the encoder grows) only to be thrown away. A discarding output for the verify pass, used where the bytes are not compared, would remove that copy.


# Ninth run: admission without materialising the reconstituted bytes (verify-only output), 19 October 2026

`AdmissionBenchmark` again (`results-verifyonly.json`, container, JMH 2 forks x 5 iterations; **re-run on the workstation before quoting**), against the seventh run (`results-b3.json`, same container). The change: the walks that exist only to prove a
stream admissible now write to `DerEncoder.discarding()`, an encoder that follows the call protocol and keeps nothing, instead of a real encoder whose buffer was filled, doubled and thrown away (SOW B.2.2 had promised a verify pass "without
materialising reconstituted bytes"; as first built it did). The workstation run of the same day showed `blob64k` unmarshal 37% over the first run, 30 of the extra 41 us being this copy.

| payload | full stage before | full stage now | now / before | decode before / now | dispatcherPath before / now |
|---|---:|---:|---:|---:|---:|
| point | 1.92 | 1.91 | 1.00 | 9.4 / 10.5 | 9.9 / 10.0 |
| record | 2.72 | 2.53 | 0.93 | 14.2 / 16.2 | 16.4 / 15.1 |
| batch100 | 70.95 | 47.24 | 0.67 | 1 067 / 1 050 | 1 021 / 1 099 |
| blob64k | 31.54 | 3.11 | **0.10** | 187.2 / 168.7 | 195.2 / 156.6 |
| request8 | 16.08 | 14.78 | 0.92 | 165.1 / 175.0 | 170.7 / 173.1 |
| blob16m | 9 401 | about 0 (-16, inside the noise) | 0.00 | 53 839 / 47 067 | 54 394 / 45 300 |
| records16m | 61 593 | 48 413 | 0.79 | 1 136 136 / 1 171 904 | 1 162 404 / 1 237 398 |

(us per operation; full stage = `cheapPlusFull` - `cheap`; the cheap stage is unchanged, 0.7 to 0.84 us on small requests.)

- Opaque bytes now cost admission nothing measurable: the full stage of a 64 KiB `byte[]` is 3.1 us (was 31.5), of a 16 MiB one nothing above the noise (was 9.4 ms), and the in-process read of the large blob is 13 to 19% faster.
- Streams of many small records gain 21 to 33% of the full stage (the bytes of every field are no longer copied), and still pay about 0.47 us a record: the value checks of B3 allocate a reader per field and element, a `BigInteger` per integer and a `String` per string.
  That is the remaining cost, and is not copying; see the follow-ups in the SOW.
- `point` and `record` read/delivery differences (9.4 -> 10.5, 14.2 -> 16.2) are inside the intervals of this container.


# Tenth run: the read path copies a record's payload once, not seven times (SOW-DER-Streaming-Encoder, read-side follow-up), 19 October 2026

`CodecBenchmark` DER `unmarshal` and `roundTrip` with `-prof gc` (container, JMH 2 forks x 5 iterations; `results-readpath-before.json` and `results-readpath-after.json`; **re-run on the workstation before quoting**).
Before = the code at `fb9bca845` (the discarding verify output); after = that plus the changes below, built and run back to back.

| benchmark | payload | time before | time after | after / before | alloc before | alloc after | after / before |
|---|---|---:|---:|---:|---:|---:|---:|
| unmarshal | point | 8.42 | 8.23 | 0.98 | 16 472 B | 14 552 B | 0.88 |
| unmarshal | record | 13.12 | 11.93 | 0.91 | 21 924 B | 19 120 B | 0.87 |
| unmarshal | batch100 | 872.8 | 826.0 | 0.95 | 1 007 308 B | 925 829 B | 0.92 |
| unmarshal | blob64k | 151.8 | **73.9** | **0.49** | 932 163 B | 471 680 B | **0.51** |
| roundTrip | point | 10.01 | 9.76 | 0.98 | 20 912 B | 19 312 B | 0.92 |
| roundTrip | record | 14.57 | 14.24 | 0.98 | 28 216 B | 25 452 B | 0.90 |
| roundTrip | batch100 | 970.5 | 950.5 | 0.98 | 1 301 062 B | 1 212 731 B | 0.93 |
| roundTrip | blob64k | 241.1 | **166.5** | **0.69** | 1 526 485 B | 1 065 954 B | **0.70** |

Where the 14 copies of a 64 KiB payload went (JFR, outside-TLAB allocation events on the pre-change code): the framed read of the input 4 (a 64 KiB seed, a doubling to 128 KiB because the stream is just over the seed, a trimming copy), the record copied out of
the input 1, the reconstitution encoder 4 (growth to the payload, growth again to append the chain, `toByteArray`), the record parse 3 (`readOctetString` and two defensive copies in `MarshalledInstanceRecord`), the field 1, the benchmark fixture's own
clone 1. Now: framed read 4 (unchanged, see below), payload 1, field 1, fixture 1. The read of a record no longer copies it out of the input, no longer rebuilds the canonical four-field record in an encoder to parse it again, and the record keeps the arrays it
was built from.

- A large opaque record reads 2.0 times faster in process (`blob64k` 151.8 to 73.9 us, below even the pre-streaming-encoder container figure) and allocates half; the allocation of small records falls 8 to 13% (fewer short-lived arrays and a record that is not re-parsed).
- Not touched: the framed read of the input (`DerInputLimits.readFramedBounded`) still grows its buffer geometrically as bytes arrive and trims at the end; that is the unauthenticated-peer memory rule of B1 (never size the buffer from a declared length), so changing
  it is a policy decision, not a copy to remove. It is 4 of the 7 remaining copies here.


# Eleventh run: the workstation with the verify-only admission output (`fb9bca845`), 20 October 2026

Same machine, JDK and settings as the first and eighth runs; jars installed from `fb9bca845` (the discarding verify output; the read-side change of the tenth section is NOT in this run). The raw file is `results-workstation1.json` on the workstation (not committed). Times in microseconds.

| DER | first run (pre-A1) | eighth run | now | now / first | now / eighth |
|---|---:|---:|---:|---:|---:|
| marshal point | 3.52 | 1.52 | 1.53 | 0.43 | 1.00 |
| marshal record | 4.48 | 2.07 | 2.05 | 0.46 | 0.99 |
| marshal batch100 | 366.6 | 151.1 | 147.6 | 0.40 | 0.98 |
| marshal blob64k | 137.1 | 73.7 | 73.8 | 0.54 | 1.00 |
| unmarshal point | 16.44 | 16.13 | 15.69 | 0.95 | 0.97 |
| unmarshal record | 23.13 | 25.80 | 23.11 | 1.00 | 0.90 |
| unmarshal batch100 | 1 710 | 1 547 | 1 464 | 0.86 | 0.95 |
| unmarshal blob64k | 113.4 | 154.8 | **129.0** | 1.14 | 0.83 |
| roundTrip point | 19.62 | 20.17 | 19.02 | 0.97 | 0.94 |
| roundTrip record | 27.91 | 28.38 | 27.90 | 1.00 | 0.98 |
| roundTrip batch100 | 2 086 | 1 685 | 1 564 | 0.75 | 0.93 |
| roundTrip blob64k | 258.6 | 242.1 | 211.3 | 0.82 | 0.87 |
| JERI ping | 125.2 | 131.7 | 122.4 | 0.98 | 0.93 |
| JERI point | 191.0 | 212.5 | 193.8 | 1.01 | 0.91 |
| JERI record | 291.2 | 237.9 | 211.7 | 0.73 | 0.89 |
| JERI batch100 | 6 689 | 5 859 | 5 147 | 0.77 | 0.88 |
| JERI blob64k | 9 676 | 9 710 | 9 135 | 0.94 | 0.94 |

The JOSS control moved too: JERI `ping` 239.7 (first run 240.5), `point` 352.0 (357.4), `batch100` 2 917 (3 382), `blob64k` 8 541 (9 910). The heavy JOSS rows are 8 to 14% down on the first run, so about that much of the JERI `batch100`/`blob64k` movement is the machine, not the code;
DER against JOSS is the safer reading: `ping` 0.51, `point` 0.55, `record` 0.55, `batch100` 1.76 (first run 1.98, eighth 1.84), `blob64k` 1.07 (0.98, 0.98).

Admission, stage by stage (full stage = `cheapPlusFull` - `cheap`):

| payload | cheap | full stage now | full stage, eighth run | dispatcherPath |
|---|---:|---:|---:|---:|
| point | 0.56 | 2.48 | 2.78 | 15.8 |
| record | 0.64 | 4.06 | 4.09 | 25.5 |
| batch100 | 2.35 | 64.1 | 82.7 | 1 416 |
| blob64k | 31.2 | **6.3** | 28.7 | 124.2 |
| request8 | 1.86 | 18.4 | 22.1 | 239.8 |
| blob16m | 9 564 | **about 0** (-162, inside the noise) | 8 807 | 43 568 |
| records16m | 9 912 | 65 292 | 78 545 | 1 456 946 |

- **The verify-only output does on the workstation what it did in the container**: admitting a 64 KiB `byte[]` costs 6.3 us (was 28.7), a 16 MiB one nothing measurable (was 8.8 ms), `batch100` 64 us (was 83), 16 MiB of records 65 ms (was 79). `unmarshal blob64k` is 129.0 us (was 154.8) and the in-process read of the 16 MiB blob is 43.7 ms (was 53.2).
- **`unmarshal blob64k` is still 14% over the first run** (129.0 against 113.4). The full stage is now 6.3 us of it; the rest is the read-side copying that the tenth section removes (a 64 KiB record was copied about seven times); that change is in the branch after this run and was not measured here.
- The cheap stage, which is what an unauthenticated peer costs, is unchanged (0.56 us on `point`, 9.6 ms for the 16 MiB read, which is the read itself: `floor` is 9.5 ms). The side-band context block, the one full admission before authorization, is 50 to 53 us on this machine (fixture-dependent: a two-record object).
- **JERI `batch100` is still not explained by the codec**: marshal plus unmarshal is about 1.6 ms of a 5.1 ms call, 5.0 ms above `ping`; JOSS: 1.5 ms of 2.9 ms. Investigated in the twelfth section: it is the mux fragment size, not the codec.

## Twelfth section: why a DER `batch100` JERI call costs more than its codec (container; re-run on the workstation before quoting)

The question: a DER `batch100` call is an echo, so the codec round trip (marshal + unmarshal, about 0.9 ms in the container) is paid twice, yet the call costs 4.4 to 4.6 ms; JOSS pays about 2.7 ms. Scratch harnesses (`JeriLoop`: a timed call loop with per-thread CPU; `JeriTime`: an `Echo` whose server method stamps `nanoTime`, which splits a call into request leg and response leg) against the installed jars; nothing in the branch changed.

What was ruled out, and what was found:

- **Not CPU in the codec.** Main-thread CPU per call is the same for both formats (1.49 ms). The extra CPU sits in the mux reader and writer threads (DER 1.5 ms against 1.05 ms) and in thread wake-ups; the DER JVM is about 35% busy.
- **Not the mux ration.** Client and server `initialInboundRation` at 32 KiB (default), 256 KiB and 4 MiB: DER 4.52 / 4.44 / 4.49 ms. The stream is smaller than the default ration.
- **Symmetric legs.** `JeriTime`: DER 2.07 to 2.13 ms to reach the server method and 2.27 to 2.34 ms to return; JOSS 1.29 to 1.35 and 1.36 to 1.37. The cost is in sending and receiving a stream, not in one side.
- **The fragment size is the cause.** `MuxClient` and `MuxServer` hard-code `maxFragmentSize` to 1024. `MuxOutputStream.writeBuffer` sends each full fragment with `futureSendData` and then waits for the writer thread to finish the socket write (`waitForIO`) before refilling the buffer. A stream of N KiB therefore costs N writer-thread handoffs on the calling thread. A JOSS stream pays them too, but interleaved with encoding; the DER encoder holds the stream and writes it all in `drainTo` at flush/close, so every handoff is exposed and nothing overlaps. (Same reason the server side shows it: the response is also one `drainTo`.)
- **Fragment sweep** (shadow `MuxClient`/`MuxServer` reading `-Djeri.frag`, both ends; us per call, 5 s runs after 5 s warm-up; the container is noisy, take the shape not the third digit):

| fragment | DER batch100 | JOSS batch100 | DER blob64k | JOSS blob64k | DER record | JOSS record |
|---:|---:|---:|---:|---:|---:|---:|
| 1 024 (today) | 4 559 | 2 928 | 9 979 | 8 978 | 193 | 524 |
| 2 048 | 3 590 | 2 683 | 5 586 | 5 204 | 192 | 544 |
| 4 096 | 3 638 | 2 827 | 3 753 | 3 098 | 178 | 498 |
| 8 192 | 3 436 | 3 098 | 2 597 | 1 948 | 183 | 602 |
| 16 384 | 3 383 | 3 043 | 2 350 | 1 613 | 192 | 559 |

An earlier, quieter run gave DER `batch100` 4 346 / 3 232 / 2 883 / 2 696 us at 1 024 / 4 096 / 16 384 / 65 000 and JOSS 2 648 / 2 649 / 2 968 / 2 929, with the request leg 1.46 ms and the response leg 1.42 ms at 16 KiB.

- **Reading it.** Small messages (`record`, `ping`) do not change: they fit one fragment. Large messages in both formats get 4 to 6 times cheaper (`blob64k` 10.0 to 2.35 ms for DER, 9.0 to 1.6 ms for JOSS): the 1 KiB fragment is a transport cost shared by both formats and is what makes `blob64k` JERI calls 9 to 10 ms. DER `batch100` falls 25 to 35%. JOSS `batch100` does not improve and may get a few percent worse above 4 KiB (the per-call buffer is allocated at the fragment size, and a pipelined writer already overlapped the handoffs). The DER codec work itself (about 1.8 ms of the call) is what remains.
- **Options (not done; a change to the shared JERI mux for both formats, awaiting a decision):** (1) raise the default fragment size (4 to 8 KiB captures most of the gain; it is local to the sender and the receiver already accepts up to 0xFFFF), ideally a system property like the ration ones; (2) in `MuxOutputStream.write(byte[], int, int)`, send a large array directly in ration-sized pieces (no copy into the fragment buffer) and wait once for the last future, so the cost of a bulk write is one handoff whatever its size. Option 2 helps only writers that write in bulk (DER's `drainTo`); option 1 helps everyone, but allocates a bigger buffer per request.

Option 2 prototyped and measured (2026-10-20, not in the source tree). The prototype (a patch to `MuxOutputStream` behind `-Djeri.bulk=true`; superseded by the implementation below and removed from `docs/`; it is in the history of commit `64af31df6`): `write(byte[], off, len)` with the fragment buffer empty and `len` at least the fragment size sends the array straight from the caller's array in pieces of `min(0xFFFF, ration)` (no copy into the fragment buffer, same ration wait, open/state handling and `fakeOKtoWrite` rule as `writeBuffer`), queues every piece with `futureSendData`, and waits once for all the futures before returning (the caller's array must not change until written). The fragment size stays 1 KiB. Container, four alternating runs of `JeriLoop` (5 s warm-up, 5 s measured), us per call:

| DER batch100 | run 1 | run 2 | run 3 | run 4 | mean |
|---|---:|---:|---:|---:|---:|
| 1 KiB fragments (today) | 4 529 | 4 286 | 4 452 | 4 207 | **4 368** |
| option 2 (bulk) | 3 222 | 3 043 | 2 928 | 2 872 | **3 016** (-31%) |
| option 2 + last fragment held back in the buffer | 3 175 | 3 437 | 2 937 | 2 817 | 3 091 (no gain, dropped) |
| option 1 (16 KiB fragments) | 2 851 | 2 785 | 2 683 | 2 683 | **2 750** (-37%) |

Other payloads (one run each, bulk off / bulk on / 16 KiB fragments): DER `blob64k` 10 015 / 2 480 / 2 049; JOSS `blob64k` 9 196 / **1 422** / 1 576 (JOSS writes a large array in one call too, so it gains from option 2 as well); DER `record` 176 / 177 / 192; JOSS `record` 477 / 528 / 543 (a record fits one fragment: unchanged within noise); JOSS `batch100` 2 715 / 2 916 / 3 115 (no gain; the change is inside the noise for bulk, and larger fragments may cost a little).

Reading it: option 2 gets most of what option 1 gets for DER (31% against 37% on `batch100`) without allocating a larger buffer per request, and on `blob64k` it is as good as or better than 16 KiB fragments in both formats. What it leaves is about 0.27 ms on `batch100`: the data and the end-of-stream marker are two messages (the marker is sent at `close()`; on the server the close is a synchronous send), where a 16 KiB fragment carries the stream and the marker together. Not tested: abort or interrupt while pieces are outstanding, the `fakeOKtoWrite` path, a session that goes down mid-write (the waits are in a `finally`, errors surface from the first failed future), the `jgdms-jeri` unit tests (the prototype was a shadow class on the benchmark class path). It would need those before it went in.

Option 2 implemented (2026-10-20, `MuxOutputStream.writeBulk`, always on). The shipped version differs from the prototype in what it does on failure: the queued pieces wrap the caller's array, so `write` never returns (or throws) until every piece has been written or has failed, even when the thread is interrupted (the wait is uninterruptible, the interrupt is then reported as `IOException("request I/O interrupted")` and takes the session down as before); a failure is added as a suppressed exception when another is already in flight. Two package-private constructors (`MuxClient`, `MuxServer`) take an explicit initial ration and fragment size, for the tests only. `MuxBulkWriteTest` (15 tests, a real `MuxClient` against a real `MuxServer` over loopback; 10 mutants of `writeBulk` each killed): sizes around the fragment size, the ration and 0xFFFF; a large write after buffered bytes, and alternating small and large writes; rations from 256 bytes to 16 MiB and fragments from 64 to 4096; the response direction; concurrent requests; the caller's array being reused at once (and, deterministically, `write` blocked while the client socket is gated); a peer that aborts, a peer that answers without reading (the `fakeOKtoWrite` path); a socket write that fails after the pieces were queued; the connection going down and an interrupt while waiting for ration; an interrupt while waiting for the pieces. `jgdms-jeri` 93 tests (1 skipped, as before), `jgdms-cel` 692, no failures.

Container, installed jars, `JeriLoop` (4 s after 5 s warm-up), three alternating runs each, mean us per call, the original `MuxOutputStream` against the new one:

| call | before | after | change |
|---|---:|---:|---:|
| DER batch100 | 4 508 | **3 163** | -30% |
| JOSS batch100 | 2 542 | 2 554 | none |
| DER blob64k | 8 749 | **1 478** | 5.9x faster |
| JOSS blob64k | 7 804 | **1 249** | 6.2x faster |
| DER record | 161 | 158 | none |
| JOSS record | 382 | 378 | none |

(Run `JeriBenchmark` on the workstation before quoting; the gain should be there for every payload larger than one fragment, and none for `ping`, `point` and `record`.)

## Thirteenth section: where DER `batch100` unmarshal time goes (container; re-run on the workstation before quoting)

Question: unmarshal of `batch100` is about 850 to 900 us in-process (100 records, 13.8 KB; JOSS 790 to 875 us), about 8 us a record. What is it spent on? Scratch harness `Unmarshal` (decode one pre-encoded stream in a loop, 6 s warm-up), JFR with `jdk.ExecutionSample#period=2ms` and `stackdepth=4096` (the default depth of 64 cuts off the roots of a recursive decode and hides the phase split).

- **The largest single cost is not in the codec: `AtomicSerial.GetArg.callerClass()`.** Every typed `get`/`defaulted` call resolves the calling `@AtomicSerial` class by walking the stack (`StackWalker.walk`, a stream, `findFirst` over `serialClasses()`), once per field read, for every object, in both formats. It showed as 24% of samples under JFR (the sampler under-counts native stack walks), and removing it took the loop from 890 to 345 us.
- **The identical-semantics shortcut.** `callerClass()` returns `found` if a frame matches, otherwise the single registered class if `serial.size() == 1`, otherwise throws; a matching frame is by definition a member of `serial`. So when exactly one class is registered it returns that class whatever the stack holds, and the walk can be skipped. Prototype (scratch only; a one-line early return in a shadow `AtomicSerial` ahead of the walk, switched by a system property), `Unmarshal`, us per op:

| | walk (today) | shortcut |
|---|---:|---:|
| DER batch100 (3 runs) | 964, 825, 883 | **330, 380, 328** |
| JOSS batch100 | 791 | **295** |
| DER record | 12.2 | **6.9** |

  About 60% off `batch100` unmarshal in both formats and 43% off `record`. Both formats use the same `GetArg` base, so the DER/JOSS ratio does not change; the absolute cost of every remote call that carries a value drops. A JERI `batch100` call pays two unmarshals (request and response), so roughly 1 ms of its 3.2 ms (container) if the in-process numbers carry over.
- **Hierarchies are not helped.** A class with an `@AtomicSerial` superclass registers two or more classes, and the stack is the only thing that says which constructor is asking (each level has its own field set; the memo is per `(caller, name)`). A stack query costs 380 to 620 ns even on a shallow stack (`SW` micro-loop: `walk` with a stream 590 to 620, with an iterator 550 to 590, `getCallerClass()` 380 to 420), so there is no cheap stack-based form. Resolving by field name alone would let a subclass read a superclass's field and is a semantic change, not an optimisation.
- **After the shortcut (320 us for 100 records, 3.2 us a record)** the profile is flat: `ByteBuffer.hashCode` is 10% self time (chain-bytes keys in `StreamSchemaDedup.encodeSite`, `verifiedChain` and `decodeSite` hash the whole chain on every lookup; a key that caches its hash, or one lookup per chain instead of three, would take most of it), `DerReader.decodeLength` 11%, `HashMap.putVal` 8% (the per-object field maps in `DerFieldStore`), `Class.forName` 3%, `validateClassFree` 7% (admission), `verifiedChain` 6%. Phase split of the 320 us: reconstitution of the stream (`reconstituteTopLevelAtomicParts`) 22%, admission (`admit`) 20%, the decode and construction 56%.
- **Not done:** the shortcut is a change to `jgdms-platform` (`org.apache.river.api.io.AtomicSerial`, shared by JOSS and DER); not applied.

## Fourteenth section: workstation run after the mux bulk write (`results-workstation2.json`), and the `GetArg` shortcut applied

**Workstation run (built from the mux change, before the shortcut below).** Full JMH run, 49 min. Codec rows are unchanged from the previous workstation run (DER `unmarshal batch100` 1 484 against 1 464, `marshal batch100` 156 against 148), as they should be, since only the mux changed. The JERI rows (us per call):

| JERI | DER, eleventh section | DER now | JOSS, eleventh section | JOSS now | DER/JOSS now (was) |
|---|---:|---:|---:|---:|---:|
| ping | 122.4 | 124.1 | 239.7 | 304.1 | 0.41 (0.51) |
| point | 193.8 | 205.2 | 352.0 | 380.7 | 0.54 (0.55) |
| record | 211.7 | 242.5 | - | 422.4 | 0.57 (0.55) |
| batch100 | 5 147 | **3 840** | 2 917 | 2 972 | **1.29 (1.76)** |
| blob64k | 9 135 | **1 409** | 8 541 | **1 238** | 1.14 (1.07) |

- **The mux bulk write is confirmed on the workstation:** DER `batch100` -25%, `blob64k` 6.5 times faster, JOSS `blob64k` 6.9 times faster; JOSS `batch100`, and the single-fragment rows (`ping`, `point`, `record`), unchanged within the noise. The container predicted -30% and 5.9 to 6.2 times.
- The JOSS control moved on the small rows (`ping` 304 +- 54 against 240 before), so the 0.41 and 0.57 ratios are not the code; the large-payload rows moved by a factor, not a few percent.
- The DER/JOSS gap on `batch100` fell from 1.76 to 1.29. The remainder is the unmarshal (DER 1.48 ms against JOSS 1.27 ms in `CodecBenchmark`), which the next change addresses in both formats.
- Admission is where the tenth and eleventh sections left it (full stage after `checkAccess`: `batch100` 58 us, `blob64k` 4 us, `records16m` 70 ms; `context` 52 to 55 us).

**`AtomicSerial.GetArg.callerClass()` returns the single registered class without walking the stack (applied, 2026-10-20).** Platform change, `GetArgCallerResolutionTest` (6 tests, passes on the old code as well, 4 mutants killed); `jgdms-der` 1 484, `jgdms-jeri` 93 (1 skipped), `jgdms-cel` 692, no failures (`jgdms-platform` has 3 failures in `CombinerDebugRecursionTest`, `DefaultPolicyParserTest` and `MultiSubjectGrantAuthorizationTest`, which fail identically on the unchanged tree in this container). Container, jars rebuilt, old `AtomicSerial` against new in the same session, three runs each, mean:

| | before | after |
|---|---:|---:|
| in-process unmarshal DER batch100 (us) | 742 | **289** (-61%) |
| in-process unmarshal JOSS batch100 | 711 | **276** (-61%) |
| in-process unmarshal DER record | 10.9 | **6.3** (-42%) |
| in-process unmarshal JOSS record | 73 | 69 (none: the JOSS record path does not read through `GetArg` per field) |
| in-process unmarshal DER blob64k | 72.5 | 68.3 (-6%, noise) |
| JERI call DER batch100 (us) | 3 171 | **1 808** (-43%) |
| JERI call JOSS batch100 | 2 748 | 2 389 (-13%, noisy: 2 880 2 670 2 696 against 2 689 2 218 2 261) |
| JERI call DER record | 182 | **160** (-12%) |
| JERI call JOSS record | 564 | 414 (-27%, noisy) |

Re-run on the workstation before quoting; the in-process unmarshal rows are the reliable ones (the JERI rows move with the container's noise). Expected there: `unmarshal batch100` about 1.48 ms to about 0.6 ms for DER, a similar fall for JOSS, and JERI `batch100` about 3.8 ms to about 2.4 ms for DER.

## Fifteenth section: chain-key hash caching (`ByteArrayKey`, container; re-run on the workstation before quoting)

The thirteenth section's profile put `ByteBuffer.hashCode` at 10% of the samples of a `batch100` unmarshal (the digest `table.get` on every `chainRef`, 5%, and `verifiedChain`'s content hash of each nested record's chain, 5%), because the dedup tables were keyed by `ByteBuffer` views and `ByteBuffer.hashCode()` reads a byte at a time through the buffer's bounds checks.

Change: `ByteArrayKey` (the digest table: first eight bytes of the SHA-256 folded to an `int`; the decode-side chain index: `Arrays.hashCode`, a vectorised intrinsic; equality is always a full `Arrays.equals`), and, on the encode side, `encodedByBytes` compares a site's chain with the previously matched one (`Arrays.equals` over the window) before any map lookup. A pure accelerator in both cases. Tests: `ByteArrayKeyTest` (5), three new `StreamSchemaDedupTableTest` cases (every single-bit change of a tabled digest is an unknown digest; every one-byte change, prefix and extension of a verified chain is not verified; 100 chains stay distinct) and an interleaving round trip in `DerStreamSchemaDedupTest` (60 random mixes of three classes decode as the classes encoded); mutants killed: equality by hash only, by an eight-byte prefix, the previous-chain check by length only or by first byte, and the wrong hash flavour for the chain index (replacing the digest hash with a constant survives, correctly: it is a performance change only). `jgdms-der` 1 493 tests, no failures.

In-process, three runs each, old `StreamSchemaDedup` against new, jars rebuilt, us per op:

| DER | before | after | |
|---|---:|---:|---:|
| marshal batch100 | 103.4 100.9 106.5 | **80.7 86.5 89.4** | -17% |
| marshal record | 1.6 | 1.5 | within noise |
| marshal blob64k | 97.6 | 95.3 | within noise |
| unmarshal batch100 | 270.7 270.3 269.7 | 259.2 277.6 249.9 | -3%, inside the run-to-run spread |
| unmarshal record | 6.1 | 5.8 | within noise |
| unmarshal blob64k | 67.5 | 68.2 | none |

- **The sampled share overstated the unmarshal gain.** `ByteBuffer.hashCode` is gone from the profile, but the work moved: `ArraysSupport.mismatch` (the `Arrays.equals` on a hit, about 200 bytes) is 6% self time now, and the probe is a fresh copy of the chain that `decodeNested` read out of the reconstituted record, so there is no identity shortcut to take. Marshal gained because the previous-chain comparison avoids the map lookup altogether for runs of one class.
- **What is left in the `batch100` unmarshal profile** (306 us under JFR, 100 records): `DerReader.decodeLength` 12.7% self, `HashMap.getNode` 7.7%, `ArraysSupport.mismatch` 6%, `Arrays.copyOf` 4%, `Class.forName` 3%, `HashMap.putVal` 3%. `decodeLength` is the one worth a look.

## Sixteenth section: is `DerReader.decodeLength` slow? No (investigation, no code change; container)

The fifteenth section left `DerReader.decodeLength` at 12.7% self time in the `batch100` unmarshal profile. Findings:

- **Its cost per call is about 3 ns.** A loop over 2 870 short TLVs (`skipTlv`, or `readTlvHeader` then `skip`) costs 2.7 to 3.4 ns per TLV, header decode and length included; the header records (`Tag`, `DecodeResult`, `TlvHeader`) are removed by escape analysis (no sample of any of them in `jdk.ObjectAllocationSample`).
- **It is called 2 870 times per `batch100` decode** (28.7 per record; `record` 60, `blob64k` 33; encoding `batch100` 425), instrumented with a counter in a shadow `DerReader`. At 3 ns that is 8 to 10 us, about 3% of a 270 to 306 us decode, not 12.7%.
- **The sampled share is skid, not cost.** 94% of the `decodeLength` samples (1 013 of 1 077) are on the line of `requireBytes(n)` in the long-form branch, which only lengths of 128 and more take. JFR attributes a sample to the nearest instruction with debug information, so a stall elsewhere in the inlined walk lands there. The same caution applies to the `ByteBuffer.hashCode` share in the fifteenth section, which also overstated its gain.
- **What the count does say:** 28.7 length decodes per record is about three passes over the record's TLVs (admission, reconstitution, decode), with an `Arrays.copyOf` and a `readRawContent` copy in each. Removing passes means fusing admission, reconstitution and decode, a design change, not a tuning of the reader; each pass is about 3 ns per TLV, so the gain from one fewer pass is of the order of 10 us in 270.
- **Allocation:** 75% of the sampled bytes are `byte[]` (the copies); the rest is `HashMap`/`LinkedHashMap` structure. The JFR allocation weights are not reliable for small objects (`LinkedKeySet`, one per object from `ObjectCodec.decodeHierarchy`, showed 11.5% of bytes).

Conclusion: no reader optimisation is worth making; the remaining unmarshal cost is spread across the three passes, the per-object `GetArg`/field-store maps and class resolution (`Class.forName` 3%), and none exceeds a few percent.

## Seventeenth section: workstation run with the `GetArg` shortcut and the chain-key hash caching (`results-workstation3.json`)

Built from the branch after the sixteenth section (mux bulk write, `AtomicSerial` shortcut, `ByteArrayKey`). 49 min. Against the previous workstation run (`results-workstation2.json`, fourteenth section: mux bulk write only):

| us per op | DER before | DER now | JOSS before | JOSS now | DER/JOSS now |
|---|---:|---:|---:|---:|---:|
| unmarshal point | 14.1 | 9.4 | 98.1 | 114.6 | 0.08 |
| unmarshal record | 21.7 | 12.9 | 108.9 | 113.1 | 0.11 |
| unmarshal batch100 | 1 483.6 | **491.2** (-67%) | 1 266.8 | **384.3** (-70%) | 1.28 |
| unmarshal blob64k | 73.5 | 67.0 | 120.4 | 135.1 | 0.50 |
| roundTrip batch100 | 1 736.3 | **672.9** | 1 556.9 | **651.4** | 1.03 |
| marshal batch100 | 156.0 | 165.4 | 214.6 | 213.8 | 0.77 |
| JERI ping | 124.1 | 125.7 | 304.1 | 273.0 | 0.46 |
| JERI point | 205.2 | 181.4 | 380.7 | 386.4 | 0.47 |
| JERI record | 242.5 | 197.0 | 422.4 | 416.7 | 0.47 |
| JERI batch100 | 3 839.9 | **1 859.8** (-52%) | 2 971.9 | **2 365.0** (-20%) | **0.79** |
| JERI blob64k | 1 409.0 | 1 442.1 | 1 238.5 | 1 216.4 | 1.19 |

Against the first run (before any of this work): JERI `batch100` DER 6 689 -> 1 860 us (3.6 times), the DER/JOSS ratio 1.98 -> 0.79; `blob64k` DER 9 676 -> 1 442 us.

- **The `GetArg` shortcut is confirmed on the workstation:** unmarshal `batch100` -67% (DER) and -70% (JOSS), the container predicted -61%; `request8` decode 244 -> 92 us; 16 MiB of small records decoded in 476 ms (was 1 597); the authorization-context block, which is pure `GetArg` use, 53 -> 28 us. Both formats gained, as predicted, so DER is still 28% slower than JOSS on unmarshalling a batch of 100 records (491 against 384 us, 4.9 against 3.8 us a record) while 8 to 12 times faster on a single small object. Round trip is level (673 against 651 us).
- **JERI `batch100` is now faster in DER than in JOSS** (1.86 against 2.37 ms) because DER's marshal is cheaper and the transport no longer penalises it.
- **The chain-key hash caching is NOT confirmed:** DER `marshal batch100` is 165.4 +- 10.7 us against 156.0 +- 5.3 and 148 in earlier runs, no gain where the container showed -17%. Do not quote the 17%. The change is correct and tested, and costs nothing measurable here; whether to keep it is the maintainer's call (it adds one class and a previous-chain check for a gain that did not reproduce).
- **Admission is unchanged:** full stage after `checkAccess` `batch100` 59 us (60.8 - 2.5 earlier 58), `blob64k` 6.8 us, `records16m` 80 ms; the cheap stage 0.6 us on `point`.
- **What is left:** the DER unmarshal of many small objects, 1.1 us a record slower than JOSS; the container profile spreads it over three passes over the stream, per-object maps and class resolution, none above a few percent (sixteenth section).

## Eighteenth section: against Java RMI (JRMP over JDK serialization) (container; re-run on the workstation before quoting)

New benchmarks: `JdkCodecBenchmark` (plain `ObjectOutputStream`/`ObjectInputStream`, the codec RMI marshals with) and `RmiBenchmark` (`UnicastRemoteObject.exportObject`, stub in the same JVM, TCP loopback, one thread, self-check that the call crosses the wire), measured as `CodecBenchmark` and `JeriBenchmark` are. `JdkPayloads` holds Serializable twins of the payloads (same fields, same values; `Payloads` is unchanged so earlier results stay comparable); `RmiEcho` is `Echo` over them; `WireSize` has a JDK column. Java RMI itself is not deprecated for removal (only RMI Activation was), so RMI is a live baseline. No authentication, encryption, constraints or input filter on either side.

Wire size (bytes): point 85 (JDK) / 83 (JOSS) / 118 (DER); record 234 / 264 / 288; batch100 10 955 / 11 237 / 13 835; blob64k 65 640 / 65 638 / 65 664. DER is +39%, +23%, +26% and +0% over JDK serialization.

Container, one session, JMH 1 to 2 forks, wide error bars on the JOSS and JERI rows; us per op:

| | RMI (JOSS) | JERI JOSS | JERI DER | JERI DER / RMI |
|---|---:|---:|---:|---:|
| ping | 53 | 344 | 137 | 2.6 |
| point | 61 | 395 | 153 | 2.5 |
| record | 64 | 419 | 162 | 2.5 |
| batch100 | 278 | 2 367 | 1 948 | 7.0 |
| blob64k | 168 | 1 520 | 1 672 | 10 |

| codec, us | JDK serialization | JGDMS JOSS | DER | DER / JDK |
|---|---:|---:|---:|---:|
| marshal record | 0.66 | 3.8 | 1.5 | 2.2 |
| unmarshal record | 2.8 | 75 | 5.6 | 2.0 |
| marshal batch100 | 25 | 175 | 86 | 3.4 |
| unmarshal batch100 | 61 | 301 | 266 | 4.4 |
| unmarshal blob64k | 16.5 | 111 | 82 | 5.0 |

Reading it. **JDK serialization and RMI are much faster than anything in JGDMS today**: a DER call costs 2.5 times an RMI call for small payloads (about 85 us more per call) and 7 to 10 times for `batch100` and `blob64k`. Of the 1 670 us gap on `batch100`, about 700 us is two DER codec round trips against about 120 us for JDK serialization; the remaining 1 ms is the JERI call path (invocation layers, the mux and its writer/reader threads) that RMI's single-socket JRMP does not have. The `blob64k` gap (1.7 ms against 0.17 ms) is almost all transport: both JERI formats are far slower than RMI on a 64 KiB array that the codec copies in 16 to 80 us, so the mux (fragmenting, rations, thread hand-offs) is the next cost to examine if bulk transfer matters. JGDMS JOSS is the slowest decoder (75 us for a record against 2.8 us for the JDK) because its atomic read validates the class descriptor and runs `GetArg` constructors.

What the cost buys is listed in the final report of this piece of work, not measured here; none of these properties is something Java RMI offers.

## Nineteenth section: workstation run 4 (`results-workstation4.json`), and the endpoint fix

Run 4 repeats run 3 on the same code for the performance paths (the only change since is the endpoint fix below, which no benchmark exercises). It agrees with run 3 within 2 to 10%: DER `unmarshal batch100` 451.9 (run 3: 491.2), JOSS 402.2 (384.3); DER `marshal batch100` 152.1 (165.4; the two runs bracket the 156 of run 2, so the hash-caching change is neither confirmed nor refuted by them); JERI `batch100` DER 1 920 (1 860), JOSS 2 052 (2 365); `blob64k` DER 1 480 (1 442), JOSS 1 244 (1 216); `ping` DER 125.4 (125.7), JOSS 272.5 (273.0); `record` DER 197.4 (197.0), JOSS 410.8 (416.7). JERI DER stays below JOSS on every row except `blob64k`. The JOSS `batch100` JERI row moved most (2 052 against 2 365, error bars +-32 and +-258): the figure to quote for it is the pair.

**Endpoint defect found and fixed.** The scalability sweep calls a service in another JVM through a proxy decoded from a stream. A DER-decoded `TcpEndpoint`, `UdsEndpoint` or `HttpEndpoint` threw a `NullPointerException` from its first `newRequest`, because `getInstance` interns an endpoint and sets up its connection state while the `@AtomicSerial` constructor does not, and `readResolve()` (which does) never runs on a DER or atomic read. Now `TcpEndpoint` and `UdsEndpoint` take the connection manager of the canonical interned instance on first use, and `HttpEndpoint(GetArg)` initialises its connection state; `DerDecodedEndpointTest` (3 tests, failing before the fix, passing after). `KerberosEndpoint` has no `GetArg` constructor and `SslEndpoint` sets its state in its constructor, so they are not affected; `HttpsEndpoint` is not covered by a test. `jgdms-jeri` 96 tests, no failures.

## Twentieth section: scalability sweep on the 6-core workstation (`sweep-6core`, separate server JVM, 1 to 48 caller threads)

Throughput in calls per millisecond summed over all callers (one fork, 5 iterations; error bars are 3 to 30% of the score, widest for RMI `record` and JERI JOSS):

| payload / stack | 1 | 2 | 4 | 8 | 12 | 24 | 48 |
|---|---:|---:|---:|---:|---:|---:|---:|
| ping, JERI DER | 7.90 | 14.92 | 22.37 | 33.48 | 38.60 | 49.02 | 38.23 |
| ping, JERI JOSS | 3.73 | 7.92 | 14.23 | 19.79 | 25.33 | 26.43 | 26.10 |
| ping, RMI | 12.89 | 25.48 | 45.80 | 75.06 | 83.39 | 105.04 | 98.64 |
| record, JERI DER | 5.21 | 10.34 | 16.50 | 25.68 | 28.70 | 31.19 | 30.76 |
| record, RMI | 9.10 | 18.78 | 34.00 | 51.29 | 58.73 | 69.88 | 61.98 |
| batch100, JERI DER | 0.54 | 1.22 | 2.30 | 3.23 | 3.91 | 3.81 | 3.78 |
| batch100, JERI JOSS | 0.46 | 0.87 | 1.36 | 2.15 | 2.02 | 1.84 | 1.76 |
| batch100, RMI | 2.44 | 4.95 | 9.14 | 12.90 | 12.91 | 14.30 | 14.15 |

- **The hypothesis that JERI DER outperforms RMI under heavy contention is not supported at this size.** DER/RMI throughput is 0.61 to 0.39 for `ping`, 0.57 to 0.50 for `record` and 0.22 to 0.30 for `batch100`, at every thread count up to 48: RMI is 2 to 4 times faster throughout, and the small-payload ratio gets worse with more callers.
- **What does hold:** DER scales much better than JERI JOSS (`batch100` speed-up 7.0 against 3.8 at 48 callers, throughput 2.1 times JOSS) and, on `batch100`, better than RMI (7.0 against 5.8) from a lower base, so the `batch100` ratio improves from 0.22 to 0.27-0.30. `ping` DER drops from 49 at 24 callers to 38 at 48.
- **CORRECTION (board gap review, 2026-10-20): the single-writer explanation below is not established.** 0.80 of a core is not saturation (a saturated thread reads about 1.0), the figure is Linux/JDK 21 while this sweep is Windows/JDK 27 on 12 logical CPUs (where `synchronized` no longer pins), and the knee at 24 callers is twice the logical CPUs, i.e. oversubscription with a server JVM on the same machine. Little's law gives about 490 us per call at 24 callers against 127 alone: a latency chain, not CPU. Read the next item as a hypothesis to test, not a finding.
- **A candidate cause for the ceiling, measured in the container (4 cores, 16 callers, DER `ping`):** the mux writer thread runs at 0.80 of a core and the reader at 0.64. JERI multiplexes all calls to an endpoint over one connection, and each mux connection has one writer and one reader thread, so small calls are limited by one writer thread; RMI opens connections as concurrency rises and has a thread per connection. For `batch100` (codec-bound) the mux threads are at 0.15 to 0.19 of a core and the callers and carriers dominate, which is why DER keeps scaling there. Not yet shown on the 6-, 32- or 80-core machines.
- **Untested remedy:** more than one connection per endpoint (a configurable pool in the connection manager) would raise the ceiling for small calls. It is a design change with ordering and constraint consequences, so it is an experiment to propose, not something done.

## Twenty-first section: stage 0 of the transport plan, TLS and thread hand-offs (container; re-run on the workstation before quoting)

New in the benchmarks: `Transport` (`TCP`, `UDS`, `TLS`), `TlsFixture` (EC P-256 server and client identities made with `keytool`, held as Subjects with X.500 principals and one shared trust store; SPIFFE is for production), `JeriBenchmark` and `RmiBenchmark` parameterised by transport (RMI over TLS uses the `jgdms-rmi-tls` factories with the same credentials; every TLS call runs `Subject.callAs(client)` on both stacks), `ServerMain` serving every transport from a separate JVM, and `sweep.py --transports TCP,TLS,UDS`.

**One caller, mutual TLS, in-process, JMH 5 x 3 s (container), us per call:**

| | JERI DER | JERI JOSS | RMI over TLS | DER / RMI |
|---|---:|---:|---:|---:|
| ping | 160 +- 34 | 444 +- 119 | 64 +- 6 | 2.5 |
| record | 231 +- 48 | 562 +- 201 | 74 +- 11 | 3.1 |
| batch100 | 1 971 +- 309 | 2 715 +- 867 | 414 +- 182 | 4.8 |

TLS adds about 25 us to a JERI DER `ping` (TCP: 127) and about 10 us to RMI (plain: 53); the ratio to RMI is the same as over plain TCP. The cost of the JERI call is not the TLS.

**Where the extra time goes: thread hand-offs.** `Hops` counts each thread's voluntary context switches per call from `/proc/self/task` (single caller, 5 s):

| call | voluntary context switches per call | us per call |
|---|---:|---:|
| JERI DER ping, TCP | 8.15 | 127 |
| JERI DER ping, TLS | 8.24 | 205 |
| JERI DER record, TLS | 8.10 to 8.16 | 261 to 273 |
| JERI JOSS ping, TCP | 8.13 | 262 |
| RMI ping/record, TLS | 2.01 | 54 to 66 |

A JERI call crosses about 8 thread wake-ups (mux writer 2, mux reader 2, the virtual-thread unblocker 1, carrier threads about 2, the caller 1) whatever the format and transport; an RMI call crosses 2. At 10 to 15 us per wake this is 60 to 90 of the roughly 75 us gap between JERI DER and RMI for `ping`, which supports the hand-off hypothesis for latency. It says nothing yet about the throughput ceiling at many callers (the gap review's reading of the 6-core sweep is oversubscription on 12 logical CPUs, not saturation).

**Defects found on the way:**
- `UdsServerEndpoint` cannot bind a socket on Linux: `restrictPermissions` sets the owner-only mode through a `NOFOLLOW_LINKS` attribute view, which the JDK implements by opening the file, and opening a socket file fails with ENXIO ("No such device or address"); the endpoint then refuses to bind. A following chmod works (confirmed in a scratch program). All UDS tests in the repository are commented out, which is why nothing caught it. UDS is a production transport, so there are no UDS numbers yet.
- `TlsRMIClientSocketFactory` was not `Serializable`, so an RMI stub exported with it could not be marshalled to another JVM (a registry bind or any remote use). Fixed (stateless, all instances equal; `TlsRMIClientSocketFactorySerializationTest`).

## Twenty-second section: UDS now binds; first UDS sweep (container, 4 cores; re-run on the workstation before quoting)

`UdsServerEndpoint` could not bind on Linux until the commits `f27eb98c9` and `81b138766` (see the handover, open item 6), so every earlier UDS row was empty ("skipping transport UDS"). First sweep with the separate server JVM, callers 1, 4 and 16, `sweep.py --transports UDS` (RMI has no UDS, so there is no ratio):

| calls/ms | 1 caller | 4 | 16 |
|---|---|---|---|
| `ping` JERI DER, UDS | 7.90 | 22.16 | 35.59 |
| `ping` JERI DER, TCP (same run set, before the fix) | 7.21 | 14.32 | 27.91 |
| `ping` JERI JOSS, UDS | 2.50 | 8.87 | 12.58 |
| `record` JERI DER, UDS | 6.12 | 17.05 | 27.20 |
| `record` JERI JOSS, UDS | 1.91 | 7.79 | 10.83 |
| `batch100` JERI DER, UDS | 0.63 | 1.94 | 2.57 |
| `batch100` JERI JOSS, UDS | 0.61 | 1.50 | 2.13 |

What it shows: UDS is 10% faster than TCP loopback for one caller and 28% to 55% for 4 to 16 callers (`ping`), but the shape is the same as on TCP: 4.5 times at 16 callers, far from linear on 4 cores, so the mux (8 voluntary context switches per call, twenty-first section) rather than the socket is what limits it. DER is 3.2 times JOSS on `ping` and 3.2 times on `record` at one caller, and 1.0 to 1.2 times on `batch100`, as on TCP. Single run, one machine type, 4 cores shared with the load generator: do not quote the absolute numbers. Remaining stage 0 work: the single-caller profile on UDS and TLS, per-thread CPU at the knee on the workstation, the connection-threshold experiment, connection counts, `VirtualThreadPinned`.

## Twenty-third section: stage 0 completed, per-thread CPU, the connection-threshold experiment, pinning (container, 4 cores, JDK 27; re-run on the workstation before quoting)

New tool: `ThreadProfile` (Linux only, reads `/proc/self/task`): per-thread CPU time from `schedstat`, voluntary and involuntary context switches, and socket descriptors, per call, for N callers on one proxy, client and server in one JVM. `java -cp target/serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.ThreadProfile DER UDS ping 16 8` (stack, transport, payload, callers, seconds). Warm-up 5 s, then the measured window.

**One caller, `ping`, per call:**

| | wall us | CPU us (all threads) | voluntary switches | threads that run |
|---|---:|---:|---:|---|
| JERI DER, UDS | 114 | 129 | 5.5 | caller 24, virtual-thread carriers 74, `MasterPoller` 24 |
| JERI DER, TCP | 146 | 195 | 8.2 | caller 22, carriers 43, unblocker 16, mux reader 49, mux writer 61 |
| JERI DER, TLS | 199 | 260 | 8.3 | caller 41, carriers 67, unblocker 16, mux reader 56, mux writer 66 |
| RMI over TLS | 64 | 66 | 2.0 | caller 32, server handler 32 |

**The channel path is already the shape the plan wants.** UDS connections are `SocketChannel`s, so they use `SocketChannelConnectionIO` (the caller writes with `writev`, one shared poller thread, no per-connection writer or reader thread). TCP and TLS use blocking streams, so each connection has a mux writer and a mux reader thread: 2.7 more hand-offs and 65 us more CPU per call. A JERI call on UDS is 114 us against 146 on TCP, though the kernel path also differs. JERI still spends twice RMI's CPU per call on its best transport.

**Sixteen callers, `ping`, 4 cores (CPU use = calls per second x CPU per call / 4 cores):**

| | calls/ms | CPU us/call | cores busy |
|---|---:|---:|---:|
| JERI DER, UDS | 37.6 | 65 | 2.4 of 4 (61%) |
| JERI DER, TCP | 27.1 | 104 | 2.8 (70%) |
| JERI DER, TLS | 19.2 | 129 | 2.5 (62%) |
| RMI over TLS | 102.6 | 36 | 3.7 (93%) |

RMI uses the machine; JERI leaves 30 to 40% of it idle. Under load the context switches per call fall (UDS 1.8, TCP 4.1, TLS 3.8), so the hand-off count is not what stops it; a serialization point on the one connection is. The ceiling in the 6-core sweep is therefore not CPU saturation of the mux writer thread (the earlier reading, withdrawn in the twentieth section), it is waiting.

**Connection-threshold experiment (E3).** A scratch patch (not committed) made the in-flight count at which `ConnectionManager.connect` stops offering a connection for reuse a property `K` (default 256, `OutboundMux.MAX_REQUESTS`); a smaller `K` makes the endpoint open another connection once every connection has `K` requests in flight. DER `ping`, 16 callers, calls/ms, 2 to 3 runs each:

| transport | K=256 (today) | K=8 | K=4 | K=2 | K=1 |
|---|---:|---:|---:|---:|---:|
| TCP | 30.2, 28.9, 27.1 | 42.9 | 41.1, 44.9 | 44.1 | 41.9 |
| UDS | 35.7, 38.4, 40.9, 41.1 | 41.5, 46.2, 46.9 | | 42.6 | 33.5 |
| TLS | 19.1, 20.8, 19.2 | 23.3, 22.7, 21.3 | 21.2, 18.9 | 15.0 | 18.1 |

The result is positive and the size depends on the I/O model: TCP (stream I/O, a writer and a reader thread per connection) gains about 50%, UDS (channel I/O) about 16%, TLS about 14% and noisy. Two to four connections capture all of the TCP gain; more does not help. Single runs per cell with a 10% spread, one 4-core machine: the 32- and 80-core machines decide the size. The decision gate for stage 3 is met for TCP and UDS, marginally for TLS (consistent with the plan of a pool of one for TLS until measured with real handshakes: here the burst of 16 simultaneous first calls already opened 16 connections whatever `K` was, so the cost of a handshake is not isolated by this experiment).

**Connection counts.** With 16 callers starting together the manager opens 16 connections in the burst (34 socket descriptors in the process, client and server ends) at every `K`; with `K=256` only the most recently opened one carries the calls and the others idle until the reaper closes them. The burst is a cost of the current code (16 handshakes on TLS) that a `connect` serialised per endpoint would avoid.

**Virtual-thread pinning (JDK 27, `jdk.VirtualThreadPinned` threshold 0).** TCP and UDS: 61 and 60 events, all in the first 50 to 110 ms (class loading in `ZipFile` under a contended monitor, and one wait for the class initialiser of `Mux`); none in steady state. TLS: none. JDK 24 and later no longer pin on `synchronized`, so this does not say how the same code behaves on JDK 21; the modules here need 25 or later to build, so it was not tested.

**What this changes in the plan (design section 9).** Stage 1 (one write per frame in `StreamConnectionIO.Writer`) and stage 4 stand, but the target is clearer: remove the writer thread from the stream path, as the channel path already does, while keeping an owned writer where an interrupt would close the socket. Stage 3 (striping inside `ConnectionManager`, endpoint-approved candidates) is justified by E3 for TCP and UDS, with a small bound (2 to 4) and a pool of one for TLS until a handshake-aware run says otherwise. Not yet measured: the same experiment on the 6-, 32- and 80-core machines.

### Twenty-third section, correction: server in its own JVM (the Fable review's first point)

`ThreadProfile` now takes `-Dthreadprofile.server=<ServerMain directory> -Dthreadprofile.serverpid=<pid>` and reports the client and the server JVM separately. In the in-process runs above the UDS client and server shared one `MasterPoller`, so the UDS row understated the cost. Separate JVMs, `ping`, container (4 cores), per call:

| | wall us | CPU us, client + server | voluntary switches, client + server |
|---|---:|---:|---:|
| JERI DER, UDS, 1 caller | 124 | 71 + 66 = 137 | 3.0 + 3.0 = 6.1 |
| JERI DER, TCP, 1 caller | 123 | 64 + 103 = 167 | 3.1 + 5.1 = 8.1 |
| JERI DER, TLS, 1 caller | 209 | 132 + 109 = 241 | 3.2 + 5.1 = 8.3 |
| RMI over TLS, 1 caller | 56 | 29 + 29 = 58 | 1.0 + 1.0 = 2.0 |
| JERI DER, UDS, 16 callers | 537 (29.8 calls/ms) | 48 + 39 = 87 | 1.7 + 1.4 = 3.1 |
| JERI DER, TCP, 16 callers | 595 (26.9) | 48 + 56 = 104 | 1.9 + 2.4 = 4.3 |
| JERI DER, TLS, 16 callers | 749 (21.4) | 64 + 73 = 137 | 1.7 + 2.1 = 3.9 |
| RMI over TLS, 16 callers | 147 (108.7) | 18 + 18 = 35 | 0.9 + 0.9 = 1.9 |

What stands and what does not:
* **Withdrawn:** "a JERI call on UDS is 114 us against 146 on TCP". With the server in its own JVM, one caller takes 124 us on UDS and 123 us on TCP. The stream transport's writer and reader threads still cost two more hand-offs and about 30 us more CPU per call, but at one caller they do not add wall time here.
* **Stands:** JERI over TLS spends 4 times RMI's CPU and 3.7 times its wall time for one caller; at 16 callers JERI DER uses 65% (UDS), 70% (TCP) and 73% (TLS) of the four cores, RMI over TLS 95%. JERI is waiting, not out of CPU.
* **Open:** whether the wait is one connection or the single `SelectionManager` poller (UDS), which the plan's two-poller experiment (stage 1b) is to separate.

## Twenty-fourth section: stage 1, one write per message on the stream path (container, 4 cores, server in its own JVM; re-run on the workstation before quoting)

`StreamConnectionIO.Writer` wrote every queued buffer on its own: a data message is a 4-byte header buffer and a payload buffer, so each message cost two writes to the connection's output stream, and on a TLS connection two TLS records (each with its own header, authentication tag and encryption call) and two system calls. The writer now gathers the buffers up to the next completion marker (at most one maximal message, 65 539 bytes) and writes them once; the reader's `read` no longer loops on `available()` (one blocking read; on TCP the loop cost a system call per read, on TLS a buffer inspection). UDS uses the channel path and is unchanged.

`ThreadProfile`, DER `ping`, 16 callers, calls/ms, alternating runs of the previous and the new jar:

| transport | before | writer gathers only | writer gathers and reader loop removed |
|---|---|---|---|
| TCP | 30.5, 28.5, 27.0, 26.6 | 44.1, 39.9 | 47.9, 42.7, 43.2, 42.0 |
| TLS | 21.7, 23.0, 20.8, 19.1 | 26.4, 27.3 | 32.1, 26.2, 30.3, 32.6 |

CPU per call at 16 callers falls from about 100 to about 68 us (TCP) and from 105 to 84 us (TLS); voluntary context switches from 4.0 to 3.1. One caller is within the noise of this container (TLS `ping` 185 to 208 us before, 178 to 259 after; TCP 127 before, 121 to 126 after; the machine was noisy: one `record` run took 630 us). So the gain is in throughput under concurrency, about 55% for TCP and 35% for TLS, and it is larger than the connection-threshold experiment's (about 50% for TCP, 14% for TLS), which should be re-measured on top of this change before stage 3 is judged.

Tests: `MuxWriteCoalescingTest` (a small request is one write; order is kept across message sizes up to 200 000 bytes and concurrent callers); the unmodified writer fails it with 100 writes for 50 requests. Mutants: not writing the gathered bytes before completing a future (hangs), not resetting the gathered length (fails) are killed; an early flush at the exact boundary and the defensive direct write of a buffer larger than any message are unobservable through the mux.

## Twenty-fifth section: the poller experiment (stage 1b) and the connection-threshold experiment repeated on stage 1 (container, 4 cores, server in its own JVM; re-run on the workstation before quoting)

**Stage 1b, more `SelectionManager` threads for UDS (negative).** A scratch patch (not committed) made the `SelectionManager` `concurrency` a property. DER `ping` over UDS, calls/ms, two runs each:

| concurrency | 1 caller, wall us | CPU us, both JVMs | voluntary switches | 16 callers |
|---|---:|---:|---:|---:|
| 1 (today) | 128, 124 | 144, 138 | 6.1 | 33.8, 36.2 |
| 2 | 145, 146 | 211, 210 | 10.6 | 31.2, 30.1 |
| 4 | 149, 156 | 219, 232 | 10.6 | 31.2, 31.3 |

More poller threads make it worse (the extra thread only takes over the handler call-backs, a hand-off per message), so the single poller is not what limits UDS at 16 callers on 4 cores. `concurrency` stays 1.

**What does differ: after stage 1 the stream path beats the channel path under concurrency.** DER `ping`, 16 callers, TCP 41 to 53 calls/ms against UDS 35 to 38 (UDS at one caller equals TCP, 121 to 126 us). The owned writer thread drains whatever the 16 callers queued and writes it at once; the channel path writes message by message under the mux lock. Batching, not the thread count, is what the stream path has. Not investigated further.

**Connection-threshold experiment repeated on top of stage 1** (scratch property `K`, DER `ping`, 16 callers, calls/ms, 2 runs):

| transport | K=256 (today) | K=8 | K=4 | K=2 |
|---|---|---|---|---|
| TCP | 41.3, 47.0 | 49.1, 46.2 | 51.3, 52.6 | 48.1, 45.1 |
| UDS | 37.3, 34.6 | 38.0, 38.4 | 44.5, 40.0 | 43.7, 43.9 |
| TLS | 29.8, 31.4 | 29.4, 29.4 | 24.2, 25.8 | 18.9, 19.7 |

Most of the TCP gain of the first experiment (+50%) was the two writes per message that stage 1 removed; what is left is +10 to 17% for TCP at K=4, +20% for UDS at K=2 to 4, and a loss for TLS that grows as K falls (-17% at K=4, -35% at K=2: more connections mean more TLS records and handshake state for no gain). So striping is justified for UDS at most, and TLS stays at a pool of one. TCP is a test transport.

## Twenty-sixth section: batching on the channel path (UDS) (container, 4 cores, server in its own JVM; re-run on the workstation before quoting)

`SocketChannelConnectionIO` wrote each message from the calling thread with `writev`, one system call per message, while holding the mux lock that the reader and every other caller also need. It now has one drainer at a time (`writing`, guarded by the lock): a caller queues its buffers and, if nobody is draining, becomes the drainer and writes outside the lock, up to 16 buffers per `writev`, taking whatever the other callers queued meanwhile; a write that meets a full socket buffer, or eight rounds, is handed to the selection thread (`awaitingWriteReady`), as before. UDS only; TCP and TLS use the stream path.

DER `ping` over UDS, calls/ms, alternating runs of the previous jar and the new one:

| callers | before | after |
|---|---|---|
| 16 | 32.3, 31.6, 33.7, 34.6, 32.2, 34.2 | 45.0, 34.3, 45.4, 41.5, 46.0, 45.3 |
| 1 (wall us per call) | 132, 133, 123, 126, 129, 131 | 139, 143, 136, 141, 136, 130 |

About +30% at 16 callers (CPU per call 80 to 64 us, voluntary switches 2.7 to 2.2) and about 5% slower for one caller (the drain adds a lock round trip and a queue pass; not removed). `record` at 16 callers moved from 26 to 29 (noisy). This brings UDS level with the stream path after stage 1 (TCP 41 to 53).

Tests: `MuxChannelBatchingTest` (4; they pass on the old code too, so they pin behaviour, not the change): sizes up to 2 MB in order, 24 threads sharing one connection, 8 threads of 100 to 300 KB with 4 KiB socket buffers (writes handed to the selection thread), and calls over a connection that is lost (6 x 3 MB) fail rather than hang. Mutants killed: no OP_WRITE interest on hand-over (hangs), callers ignoring `writing` (corrupts), futures never completed (hangs), futures not failed on a write error. Not killed: failing the pending futures when the drainer finds the mux down (defensive).

Test note: small socket buffers on loopback TCP stall the sender for tens of seconds (the receive window never reaches one segment, MSS 65 483), which looked like a mux deadlock for an hour; the tests use Unix domain sockets.

## Twenty-seventh section: where a call's CPU goes, by sampling and by system call (container, 4 cores, JDK 27, server in its own JVM, one caller; re-run on the workstation before quoting)

**Sampling the Java frames (JFR, 1 ms, UDS DER `ping`).** On the server's request-dispatch thread the Java-level samples add up to about 3 to 4 us per call (281 samples over a 13 s run of about 80 000 calls). Of those, building the DER input stream is 20%, the `BasicInvocationDispatcher` client-subject lookup 4%, stack-trace creation about 1%, `StreamSchemaDedup` 4%; `ServerContext.doWithServerContext` and `Subject.callAs` show high inclusive shares only because they wrap the whole dispatch. On the client the poller's samples sit in one line of `Mux.handleData` (the call into `Session.handleData`, which wakes the waiting caller). So the Java code on the call path (codec, remote context, security context, stack traces, constraints) costs a few microseconds of the 60 to 90 us of CPU each JVM spends per call. The rest is in the kernel and the scheduler.

**Counting system calls (`strace -c -f`, 5 s, per call, per JVM):**

| | futex | epoll_wait | epoll_ctl | read | write |
|---|---:|---:|---:|---:|---:|
| JERI DER, UDS, client | 6.1 | 3.0 | 1.0 | 1.0 | 1.0 (writev) |
| JERI DER, UDS, server | 6.1 | 3.0 | 1.0 | 1.0 | 1.0 (writev) |
| JERI DER, TCP, client | 6.1 | | | 1.0 | 1.0 |
| JERI DER, TCP, server | 13.3 | | | 1.0 | 1.0 |
| RMI, plain TCP, client and server | about 0.03 | | | 1 | 1 (and a rare poll) |

An RMI call is one write and one read per side and almost no futex: each thread blocks in its own socket and the kernel wakes it. A JERI call on UDS makes 12 system calls per side, 6 of them futex (cross-thread parking and waking) and 3 `epoll_wait` plus an `epoll_ctl` for each readiness event. On TCP the server makes 13 futex calls per call. The three `epoll_wait`s are the JDK's machinery for a virtual thread waiting in `Selector.select()` (the master poller wakes the virtual thread, whose carrier then polls the selector again and re-arms it): the `SelectionManager` loop runs on a virtual thread.

Conclusion for the design: the cost of a JERI call is its thread hand-offs and the selector machinery, not the codec or the context handling.

## Twenty-eighth section: the selection loop on a platform thread; platform or virtual threads for the pool (container, 4 cores, server in its own JVM; re-run on the workstation before quoting)

Commit 1821b2f25 (2026-05-12, "migrate all executors to virtual threads") replaced the system pool's `Executors.newCachedThreadPool` of platform daemon threads (stack size hint 228) with a virtual thread per task. That put `SelectionManager`'s select loop on a virtual thread, and a virtual thread in `Selector.select()` is woken through the JDK's poller (the 3 `epoll_wait`s and 6 futex calls per call per JVM of the twenty-seventh section). Three arrangements, DER `ping`, two or three alternating runs each:

| | UDS, 1 caller (wall us, switches) | UDS, 16 callers (calls/ms) | TCP, 1 caller (wall us) | TCP, 16 callers | TLS, 1 / 16 callers |
|---|---|---|---|---|---|
| all virtual (before) | 134, 132 (6.1) | 37.5, 40.3 | 157, 145 | 40.6, 40.2 | 208, 229 / 24.8, 24.0 |
| all platform (cached pool) | 92, 91 (4.0) | 36.6, 35.0 | 136, 126 | 28.5, 31.6 | 232, 205 / 21.3, 21.8 |
| select loop on a platform thread, rest virtual (now) | 90, 95, 88 (4.1) | 45.5, 56.7, 50.7 | unchanged | unchanged | unchanged |

The platform pool halves the UDS single-caller gap but costs 10 to 28% of the TCP and TLS throughput at 16 callers (more runnable threads than cores). Only the select loop needs to be a platform thread: it lives as long as the manager and blocks in `select()`. Done in `SelectionManager`; the pool stays virtual by default, and `-Dorg.apache.river.thread.platform=true` restores the platform-thread cached pool of before 1821b2f25 (keeping the later subject-clearing: the factory creates each thread with no ambient subject).

UDS DER `ping` now: one caller 90 us (was 132), 4.1 voluntary switches across both JVMs (was 6.1), CPU 79 to 99 us per call (was 150); 16 callers 45 to 57 calls/ms (was 33 to 40). For reference RMI over plain TCP is 53 to 80 us for one caller and about 121 calls/ms at 16.

## Twenty-ninth section: channel I/O for TCP, and how contended the mux lock is (container, 4 cores, server in its own JVM; re-run on the 32- and 80-core machines before deciding anything)

**TCP on `SocketChannel` (`-Dorg.apache.river.jeri.tcp.useNIO=true`, an existing switch, default off), DER `ping`/`record`, three alternating runs each:**

| | stream (default) | channel I/O |
|---|---|---|
| `ping`, 1 caller, wall us (voluntary switches) | 145, 129, 130 (8.1) | 98, 91, 107 (4.1) |
| `ping`, 16 callers, calls/ms | 42.2, 44.1, 41.3 | 37.4, 40.6, 38.1 |
| `record`, 16 callers, calls/ms | 30.5, 27.7, 29.2 | 22.9, 28.5, 29.2 |

Channel I/O takes 28% off the single-caller latency (the hand-offs halve) and is about 8% lower at 16 callers on 4 cores. TLS has no channel path: `SslConnection.getChannel()` is deliberately null because the raw channel carries ciphertext; it would need an `SSLEngine`-driven non-blocking `ConnectionIO`.

**Contended monitors at 16 callers (`jdk.JavaMonitorEnter`, threshold 0; `jfr-monitors.py`).** Only entries that had to wait are recorded. UDS DER `ping`, 46.7 calls/ms for about 13 s (about 600 000 calls):

| JVM | monitor | blocked entries | total blocked | per call |
|---|---|---:|---:|---:|
| server | mux lock (`Object`: `MuxServer.handleOpen`, `Session.handleOpen`, `getSession`) | 10 732 | 1 465 ms | 2.4 us |
| client | mux lock (`Session.handleData`, `getSession`) | 4 586 | 803 ms | 1.3 us |
| client | `ConnectionManager` (`connect`: `pendingConnects++`, the mux scan) | 19 976 | 4 312 ms | 7.2 us |
| client | `Util$TableCache.getTable` (per-call method-hash cache), `WeakIdentityMap` (`checkProxyRemoteMethod`), `HashSet` | 2 500 | 630 ms | 1.0 us |

At 343 us of wall time per call per caller these are 0.4% (mux lock, server), 0.4% (client), 2% (`ConnectionManager`) and 0.3%. TCP is the same shape (`StreamConnectionIO` writer lock waits 200 ms). So on 4 cores the mux lock does not limit throughput; per-call CPU (63 us against RMI's 36) does. The contended fraction grows with the number of cores, so the 32- and 80-core machines are where it would show: run the sweep with a recording and `python3 jfr-monitors.py` on the client and the server recordings.

## Thirtieth section: running the request on the reader (spike), and the cost of opening a connection (container, 4 cores, JDK 27 `27-internal`; re-run on the workstation before quoting)

**Spike: where should the handler run?** `InlineVirtualThreadSpike` and `InlineVirtualThreadHeadOfLineSpike` (run with `--add-opens java.base/java.lang=ALL-UNNAMED`). One platform reader thread blocked in a `Selector` over Unix sockets, one-byte request and reply. The handler runs (a) inline on the reader, (b) as a virtual thread on the default scheduler (what `MuxServer` does), (c) as a virtual thread built with `ThreadBuilders$VirtualThreadBuilder(Executor)` whose scheduler queues the continuation and the reader runs it itself.

| | quick handler, round trip (1 client) | 4 clients, quick handler (calls/ms) | 4 clients, handler parks 500 us (calls/ms) |
|---|---:|---:|---:|
| inline on the reader | 32.7 us | 79.5 | 1.5 (the reader is blocked) |
| virtual thread, default scheduler | 62.7 us | 40.5 | 4.0 |
| virtual thread scheduled on the reader | 43.0 us | 62.5 | 3.5 |

Scheduling the virtual thread on the reader recovers two thirds of the 30 us the default scheduler's hand-off costs (to 10 us over inline), is 54% faster than the default with four clients, and does not stall the reader when the handler blocks (3.5 against inline's 1.5 calls/ms; a slow handler is resumed by an enqueue and a selector wake-up, which is why it is 12% below the default there). It needs a package-private JDK constructor (`java.lang.ThreadBuilders$VirtualThreadBuilder(Executor)`), reachable here by reflection or by exposing it in the DirtyChai JDK.

**Cost of opening a connection** (`ConnectionCost`; DER `ping`, server in its own JVM; first call on a connection the reaper has just closed against a warm call, 30 samples after 15 warm-up reconnects; `-Dorg.apache.river.jeri.connectionTimeout=30`):

| transport | warm call | first call on a new connection | opening costs |
|---|---:|---:|---:|
| UDS | 118 us | 2.4 ms | 2.3 ms (about 20 calls) |
| TCP, stream | 127 us | 2.3 ms | 2.2 ms |
| TCP, channel I/O | 116 us | 2.4 ms | 2.3 ms |
| TLS (mutual, TLS 1.3, ECDSA P-256) | 275 us | 12.3 ms | 12 ms (about 45 calls) |

Resources per connection (64 callers with the scratch `K=1` property forcing a connection each; client JVM; the callers' own threads are counted in the thread deltas): UDS and channel-I/O TCP add no thread (the shared selection thread serves them); stream TCP and TLS add two platform threads (mux reader and writer) per connection; heap grows about 0.2 MB per UDS or TCP connection and about 0.6 MB per TLS connection (SSLEngine buffers and session), and resident memory by roughly 0.8 MB (UDS), 1.1 MB (TCP stream) and 1.9 MB (TLS) per connection including stacks. One file descriptor per connection on each side.

Consequences: striping must open connections lazily and keep them (a connection is worth 20 to 45 calls, so a pool that opens and closes is a loss, which is what the TLS striping result showed). The cold burst is the same cost paid N times: when N callers find no connection they each open one (16 callers on TLS spend 16 x 12 ms of CPU and handshakes), where one caller opening and the rest waiting would pay once.

## Thirty-first section: latency and high-concurrency tooling, `ConcurrencyCurve` and `concurrency-curve.py` (tooling only; the table is a smoke run, 4-core container, not for quoting)

The target of the JERI transport is low latency and high scalability, and the earlier sweeps report throughput and medians for 1 to 16 callers on 4 cores, which is RMI's best case. This tool measures what they do not: the latency distribution under load, thousands of callers (platform and virtual threads), and what each run costs in threads, sockets, memory and CPU.

**The tool.** `ConcurrencyCurve` (with `LatencyHistogram`, a log-bucketed histogram of 3 significant digits from 100 ns to 100 s, no dependency) runs one point: N callers on one proxy or stub against a server in its own JVM (`ServerMain`, as `ThreadProfile` does), warm-up, then a measured window. It records the latency of every call that completes in the window with `System.nanoTime`, one histogram per caller up to 64 callers and 64 shared histograms (atomic increments) above that, so memory does not grow with the caller count and does not distort the RSS figures. It prints calls/ms, p50, p90, p99, p99.9, max, the threads, socket descriptors, peak resident memory (VmRSS) and CPU per call of the client and of the server JVM (`/proc`, so Linux only; on other systems those fields are `n/a`, and CPU falls back to `ProcessHandle`), and one `curve-json:` line for the driver. Callers are `Thread.ofPlatform()` or `Thread.ofVirtual()`; if the platform threads cannot all be created (`OutOfMemoryError: unable to create native thread`) the run goes on with the callers that started and says so (`callers_started`). A call that throws is counted (`errors`, `first_error`) and does not stop the run.

`concurrency-curve.py` runs the sweep: a fresh server JVM and a fresh client JVM per point (so the server's threads and memory belong to that point; `--shared-server` reuses one), a timeout per point (a failed or timed-out point is recorded and the sweep goes on), `results.jsonl`, `summary.txt` and `environment.txt` in the output directory. Combinations that do not exist are skipped (RMI has no UDS; RMI's plain variant is `PLAIN`, and `TCP` is accepted for it).

```
python3 concurrency-curve.py --out curve-80core                       # DER,JOSS,RMI x TCP,UDS,TLS x platform,virtual x 1,8,64,512,4096
python3 concurrency-curve.py --out curve-x --stacks DER,RMI --transports UDS,TCP --callers 1,8,64,512 --kinds platform,virtual --warmup 3 --measure 4
python3 concurrency-curve.py --out curve-x --rate 20000 --callers 64,512     # open loop
java -Dthreadprofile.server=<ServerMain dir> [-Dthreadprofile.serverpid=<pid>] [-Dbench.tls.dir=<dir>] -cp target/serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.ConcurrencyCurve stack=DER transport=UDS payload=ping callers=64 kind=virtual warmup=5 measure=8   # one point by hand
```

On Windows use `py concurrency-curve.py --out curve-6core ...` (or `python`), with `JAVA_HOME` set or `java` on the path; the driver uses argument lists only. `--jvm-args '-Xmx8g -Djdk.virtualThreadScheduler.parallelism=N'` goes to both JVMs. Defaults are warm-up 5 s and measure 8 s per point; with 3 stacks, 3 transports (8 combinations), 2 kinds and 5 caller counts that is 80 points of about 25 s, so plan half an hour, and use `--kinds`, `--stacks` or `--transports` to cut it.

**What it measures, and the caveats.**
- The default is a **closed loop**: each caller calls again when the last call returns, so the offered load falls as the system slows down. When the system saturates, the percentiles understate what an independent arrival stream would see (coordinated omission); read them as "latency while N callers are in flight". With `rate=R` (`--rate R`) the run is an **open loop**: each caller has its own arrival schedule (one call every N/R seconds, staggered), the latency of a call runs from its intended start to its completion, so a late caller charges its delay to the calls behind it. N bounds the concurrency, a caller more than 10 s late is abandoned and its arrivals counted as `missed`, and `parkNanos` slop (50 us or more) is included in the latency of calls that waited. Only the closed loop has been smoke-tested more than briefly; the open loop was run at 5 000 calls/s (all arrivals completed) and at an overload (200 000 calls/s, where it reported latencies of seconds and the missed arrivals, as it should).
- The calls/ms and the percentiles are over the window the callers used (`measure_s`), whatever moment the sampling thread got to run. Threads, sockets and RSS are the largest of three samples taken inside the window. CPU per call is the CPU time of all threads (`schedstat`) between two snapshots divided by the calls completed between them.
- At thousands of platform-thread callers the machine is oversubscribed by construction (and the sampling thread competes with them); virtual-thread callers are the realistic model of a many-client server but share the JDK scheduler (parallelism = cores) with JERI's own virtual threads. Limits such as `ulimit -n`, `ulimit -u` and the memory of the machine are part of the result: record them (`environment.txt` does) and do not raise them to make a point pass.
- Verified in the container: DER on TCP, UDS and TLS, RMI plain and TLS, platform and virtual callers, 1 to 4096 callers (4096 on DER and RMI over TCP, both kinds), the timeout path, the open loop. Not verified: JOSS (the same code path as DER, not run), the 32- and 80-core machines, Windows (the `/proc` fields are guarded and print `n/a`, but nothing has been run there), 4096 callers on UDS and TLS beyond one DER UDS platform run.

**Smoke run, 4-core container, not for quoting** (`python3 concurrency-curve.py --out smoke --callers 1,8,64,512 --stacks DER,RMI --transports TCP,UDS --kinds platform,virtual --warmup 3 --measure 4`; ping, closed loop, JDK 27, one run per point, a noisy shared container, warm-up and measurement short): this proves the tool runs and shows its output shape; it is not a comparison of the stacks.

| stack | transport | callers | calls/ms | p50 us | p99 us | p99.9 us | max us | client / server threads | client / server RSS MB | errors |
|---|---|---|---:|---:|---:|---:|---:|---|---|---:|
| **platform-thread callers** | | | | | | | | | | |
| DER | TCP | 1 | 5.8 | 129 | 630 | 3813 | 113465 | 29 / 38 | 103 / 112 | 0 |
| DER | TCP | 8 | 29.4 | 239 | 961 | 3414 | 7081 | 49 / 51 | 186 / 213 | 0 |
| DER | TCP | 64 | 46.2 | 1202 | 4674 | 7287 | 14487 | 217 / 163 | 390 / 379 | 0 |
| DER | TCP | 512 | 10.6 | 49644 | 219939 | 325059 | 507093 | 1561 / 1059 | 601 / 430 | 0 |
| DER | UDS | 1 | 10.7 | 83 | 222 | 2080 | 6638 | 28 / 38 | 118 / 117 | 0 |
| DER | UDS | 8 | 36.1 | 184 | 766 | 4694 | 11793 | 35 / 37 | 325 / 357 | 0 |
| DER | UDS | 64 | 37.4 | 964 | 12935 | 26378 | 45331 | 91 / 37 | 931 / 484 | 0 |
| DER | UDS | 512 | 13.3 | 42140 | 148242 | 205390 | 309347 | 539 / 37 | 426 / 357 | 0 |
| RMI | PLAIN | 1 | 19.0 | 46 | 116 | 890 | 33649 | 25 / 36 | 135 / 124 | 0 |
| RMI | PLAIN | 8 | 103.4 | 65 | 243 | 1095 | 6332 | 32 / 43 | 145 / 311 | 0 |
| RMI | PLAIN | 64 | 99.1 | 467 | 4102 | 16097 | 75671 | 89 / 100 | 204 / 331 | 0 |
| RMI | PLAIN | 512 | 29.7 | 7569 | 159777 | 375128 | 4642132 | 538 / 548 | 862 / 862 | 0 |
| **virtual-thread callers** | | | | | | | | | | |
| DER | TCP | 1 | 5.6 | 153 | 431 | 3602 | 15472 | 32 / 38 | 107 / 108 | 0 |
| DER | TCP | 8 | 24.0 | 287 | 1551 | 4399 | 7037 | 45 / 51 | 151 / 170 | 0 |
| DER | TCP | 64 | 47.1 | 950 | 6668 | 12534 | 25368 | 157 / 163 | 382 / 338 | 0 |
| DER | TCP | 512 | 16.1 | 11125 | 193462 | 277610 | 441300 | 277 / 283 | 589 / 869 | 16 |
| DER | UDS | 1 | 8.0 | 107 | 280 | 3455 | 13479 | 30 / 38 | 113 / 113 | 0 |
| DER | UDS | 8 | 29.0 | 230 | 1206 | 4751 | 16415 | 30 / 37 | 239 / 284 | 0 |
| DER | UDS | 64 | 45.1 | 915 | 7729 | 16384 | 27038 | 30 / 37 | 450 / 445 | 0 |
| DER | UDS | 512 | 13.6 | 2408 | 216531 | 321913 | 625134 | 30 / 37 | 853 / 527 | 0 |
| RMI | PLAIN | 1 | 9.7 | 89 | 321 | 1315 | 3691 | 30 / 37 | 99 / 94 | 0 |
| RMI | PLAIN | 8 | 68.8 | 95 | 463 | 1680 | 10286 | 31 / 43 | 121 / 152 | 0 |
| RMI | PLAIN | 64 | 87.6 | 125 | 7430 | 46268 | 145057 | 32 / 99 | 300 / 322 | 0 |
| RMI | PLAIN | 512 | 56.9 | 1501 | 99025 | 886571 | 1795593 | 32 / 547 | 304 / 857 | 0 |

The 512-caller point of DER over TCP with virtual callers had 16 errors, `ConnectIOException ... no free sessions` from the mux (another run of DER UDS with virtual callers had 36); the tool counts those as errors and keeps the latency of the calls that succeeded, so a point with errors is not comparable with one without. At 4096 callers over TCP (one run each, 3 s + 4 s) every point completed: DER with platform callers 0.5 calls/ms, with virtual callers 13 calls/ms; RMI with platform callers 41 calls/ms and virtual callers 47 calls/ms, with up to about 8 000 client threads in the DER platform run. These too are a first look at what the tool shows, to be repeated on the machines the maintainer named before anyone reads anything into them.
