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

## Thirty-second section: "no free sessions" at many callers, the reservation race in `ConnectionManager.connect` (container, 4 cores, JDK 27 `27-internal`, server in its own JVM; re-run on the workstation before quoting)

**The bug.** `ConnectionManager.connect` put a mux in the list offered to the endpoint if `requestsInProgress() < 256`, called `ep.connect(handle, active, idle)` outside the lock, and only afterwards, in a second `synchronized` block, reserved the slot (`newRequestPending`). With many concurrent callers all of them pass the check (say at 255), the endpoint (TCP and UDS both return the first active connection) hands all of them the same mux, all reserve, and the surplus fails in `MuxClient.newRequest` with `IOException("no free sessions")`, which `ReqIterator.next` does not retry. The limit was 128 before 3.1.1 and became 256; the race is the same, only the threshold moved. 256 is the most the wire format allows (the session id is an unsigned byte).

**The fix** (`ConnectionManager.connect`; nothing else changed: not the endpoints, the mux, the 256 limit or the cold-burst behaviour). The room is checked again, in the same critical section as the reservation, on the mux the endpoint chose. If it is full (or dead, in which case it is also removed) `connect` goes round again: the scan now leaves that mux out, the endpoint is asked again, and if it returns null a new connection is opened. At most 16 passes, then a new connection is opened. `pendingConnects` is counted once per call, not per pass. DO NOT go back to checking only in the scan.

**Tests** (`ConnectionManagerCapacityTest`, 2 tests, about 1 s). (1) `testReservationNeverExceedsCapacity`, deterministic: 255 slots of one mux are reserved through `ConnectionManager.connect` with a fake endpoint that behaves like the real ones (first active connection) and makes 64 callers meet inside `ep.connect`, so that all of them have chosen the mux before any reserves. Without the fix 319 requests are reserved on one mux (8 of 8 runs fail); with it none exceeds 256 and a second connection is opened (8 of 8 pass). (2) `testMoreThanOneConnectionsWorthOfConcurrentRequests`, through the public API: a remote object exported over TCP, 255 callers blocked in the server on the first connection, then 245 more released together; all 500 calls must be in the server at the same time, none may fail, and more than one connection must have been opened. Without the fix it fails with `ConnectIOException ... no free sessions` in 4 of 8 runs (the window is a few microseconds, so this one is a probabilistic catch; a variant with a spin start caught 5 of 8); with the fix 8 of 8 pass. Mutant: replacing the re-check by `>= 0` is caught by (1) 8 of 8 and by (2) 1 of 8.

**Measurement.** `concurrency-curve.py --stacks DER --transports UDS,TCP --kinds virtual,platform --callers 64,256,512,1024 --payloads ping --warmup 3 --measure 6`, once with the shaded benchmark jar built from the jeri classes before the fix (`--jar before.jar`; the `ConnectionManager.class` in it is the unfixed one) and once after, in the order before, after, before, after. Each cell shows run 1 / run 2. 4-core container, one point at a time, noisy, short runs.

| transport | callers | run of | errors | calls/ms | p50 us | p99 us | p99.9 us | client sockets |
|---|---|---|---:|---:|---:|---:|---:|---:|
| **UDS, virtual-thread callers** | | | | | | | | |
| UDS | 64 | before | 0 / 0 | 31.5 / 48.9 | 1208 / 867 | 11051 / 7295 | 29508 / 12173 | 62 / 54 |
| UDS | 64 | after | 0 / 0 | 38.6 / 45.2 | 940 / 912 | 9101 / 7389 | 32801 / 12116 | 66 / 59 |
| UDS | 256 | before | 0 / 0 | 24.3 / 24.5 | 2922 / 4223 | 75956 / 43352 | 124977 / 62489 | 58 / 238 |
| UDS | 256 | after | 0 / 0 | 25.9 / 37.9 | 2839 / 2230 | 49840 / 46432 | 90243 / 73728 | 133 / 57 |
| UDS | 512 | before | 21 / 8 | 35.7 / 28.9 | 1882 / 2836 | 100401 / 118489 | 178127 / 205390 | 85 / 94 |
| UDS | 512 | after | 0 / 0 | 26.3 / 30.7 | 1065 / 1441 | 99025 / 105382 | 153092 / 180617 | 151 / 94 |
| UDS | 1024 | before | 0 / 16 | 19.6 / 29.7 | 55837 / 2824 | 204472 / 204079 | 297533 / 385090 | 252 / 94 |
| UDS | 1024 | after | 0 / 0 | 23.0 / 35.0 | 32457 / 1444 | 183239 / 178913 | 262144 / 245236 | 185 / 94 |
| **UDS, platform-thread callers** | | | | | | | | |
| UDS | 64 | before | 0 / 0 | 31.8 / 48.3 | 1043 / 1036 | 15098 / 6496 | 37618 / 12427 | 66 / 66 |
| UDS | 64 | after | 0 / 0 | 37.1 / 41.9 | 1222 / 1146 | 8569 / 7672 | 24789 / 18874 | 66 / 66 |
| UDS | 256 | before | 0 / 0 | 46.1 / 24.3 | 4424 / 11379 | 26493 / 51642 | 51839 / 74908 | 48 / 258 |
| UDS | 256 | after | 0 / 0 | 17.7 / 23.3 | 15974 / 12493 | 65274 / 51937 | 92275 / 77005 | 258 / 258 |
| UDS | 512 | before | 0 / 0 | 13.9 / 14.5 | 3602 / 38044 | 199754 / 144703 | 288621 / 198967 | 514 / 514 |
| UDS | 512 | after | 0 / 0 | 12.7 / 15.1 | 43385 / 39027 | 155845 / 135266 | 215351 / 184549 | 489 / 429 |
| UDS | 1024 | before | 0 / 0 | 8.8 / 7.9 | 126091 / 145621 | 423363 / 449839 | 619184 / 598213 | 941 / 1026 |
| UDS | 1024 | after | 0 / 0 | 10.3 / 11.9 | 108724 / 95748 | 368050 / 321651 | 544735 / 453771 | 642 / 537 |
| **TCP, virtual-thread callers** | | | | | | | | |
| TCP | 64 | before | 0 / 0 | 44.0 / 44.1 | 990 / 1056 | 7508 / 7111 | 13099 / 11035 | 66 / 66 |
| TCP | 64 | after | 0 / 0 | 44.0 / 48.6 | 1077 / 904 | 6746 / 6648 | 22479 / 10781 | 55 / 66 |
| TCP | 256 | before | 0 / 0 | 38.7 / 47.3 | 1239 / 1959 | 35946 / 33292 | 53641 / 51773 | 118 / 72 |
| TCP | 256 | after | 0 / 0 | 30.8 / 41.3 | 1223 / 1414 | 42861 / 37388 | 60654 / 55640 | 147 / 95 |
| TCP | 512 | before | 0 / 16 | 27.4 / 26.7 | 1456 / 2010 | 94896 / 97911 | 144441 / 133759 | 166 / 156 |
| TCP | 512 | after | 0 / 0 | 33.4 / 33.1 | 1156 / 1214 | 82903 / 89719 | 119472 / 128057 | 137 / 117 |
| TCP | 1024 | before | 0 / 13 | 28.9 / 29.3 | 922 / 1761 | 172360 / 260309 | 246940 / 479986 | 187 / 110 |
| TCP | 1024 | after | 0 / 0 | 19.3 / 26.9 | 2746 / 3525 | 218104 / 259391 | 312214 / 445383 | 304 / 100 |
| **TCP, platform-thread callers** | | | | | | | | |
| TCP | 64 | before | 0 / 0 | 44.9 / 49.1 | 1231 / 1145 | 5087 / 4456 | 9011 / 8282 | 66 / 66 |
| TCP | 64 | after | 0 / 0 | 50.5 / 45.0 | 1076 / 1245 | 4452 / 4960 | 7246 / 10297 | 66 / 66 |
| TCP | 256 | before | 0 / 0 | 26.2 / 25.5 | 6492 / 9601 | 46105 / 46563 | 84214 / 67633 | 258 / 258 |
| TCP | 256 | after | 0 / 0 | 24.5 / 23.5 | 4813 / 6083 | 50102 / 53281 | 73138 / 79495 | 258 / 258 |
| TCP | 512 | before | 0 / 0 | 13.0 / 14.9 | 46760 / 42074 | 178389 / 150077 | 255590 / 210239 | 514 / 514 |
| TCP | 512 | after | 0 / 0 | 29.9 / 13.6 | 1392 / 45154 | 90636 / 164233 | 135528 / 235799 | 202 / 514 |
| TCP | 1024 | before | 0 / 0 | 5.6 / 5.8 | 159908 / 185729 | 822608 / 715653 | 1231028 / 3531604 | 854 / 1026 |
| TCP | 1024 | after | 0 / 0 | 6.1 / 6.0 | 188088 / 187564 | 737149 / 606077 | 3181380 / 845152 | 1026 / 1026 |

**What changed.** The errors are gone. The unfixed jar had errors in 5 of the 16 point-runs with virtual-thread callers (UDS 512 callers: 21 and 8; UDS 1024: 16 in run 2; TCP 512: 16 in run 2; TCP 1024: 13 in run 2), every first error being `ConnectIOException ... nested exception is: java.io.IOException: no free sessions`; there were none at 64 and 256 callers, and none with platform-thread callers. With the fix all 32 point-runs had 0 errors. Platform-thread callers never failed, before or after: their client socket count is about the caller count (see below), so no connection fills.

**What did not change.** The throughput fall between 64 and 512 callers is still there. Calls/ms, run 1 / run 2, at 64 callers then at 512 callers:

| callers | before | after |
|---|---|---|
| UDS platform | 31.8 / 48.3 then 13.9 / 14.5 | 37.1 / 41.9 then 12.7 / 15.1 |
| TCP platform | 44.9 / 49.1 then 13.0 / 14.9 | 50.5 / 45.0 then 29.9 / 13.6 |
| UDS virtual | 31.5 / 48.9 then 35.7 / 28.9 | 38.6 / 45.2 then 26.3 / 30.7 |
| TCP virtual | 44.0 / 44.1 then 27.4 / 26.7 | 44.0 / 48.6 then 33.4 / 33.1 |

The failed calls were a small part of the collapse: at most 21 failed calls in a 6 s window of well over 100 000 calls, and calls/ms does not move beyond the run-to-run spread (the two runs of one point differ about as much as before and after do, in nearly every row). The one apparent gain, TCP virtual at 512 (27 to 33 calls/ms, in both runs of each), is the size of the spread seen elsewhere in the table and needs more runs before it is read as an effect. One run (TCP platform 512, after, run 1: 29.9 calls/ms, p50 1.4 ms) is twice the others and was not reproduced by run 2.

**One observation, no explanation attempted.** The client socket count in the driver output is the caller count plus a few with platform-thread callers at 512 (514 in 5 of 8 runs, before and after; 489, 429 and 202 in the others) and 85 to 166 with virtual-thread callers; the one fast run (29.9 calls/ms) had 202 sockets. The fix does not change how many connections a cold burst opens (each of N threads that find no connection opens its own: a separate known issue).

## Thirty-third section: why throughput falls between 64 and 512 callers, diagnosed (container, 4 cores, JDK 27 `27-internal`, server in its own JVM, DER, `ping`, closed loop; re-run on the workstation before quoting)

**The question.** Section 32 left the fall open: DER over UDS, platform-thread callers, 32 to 48 calls/ms at 64 callers and 13 to 15 at 512, p50 1 ms to 40 ms; the same shape over TCP and with virtual-thread callers, and less for RMI. Why, and what limits it? The target is low latency and high scalability, so a fall with the number of callers is the main thing to understand before the transport options are weighed (docs/DESIGN-OPTIONS-jeri-transport-architecture-2026-10-20.md, D8).

**Short answer.** The loss is in `ConnectionManager.connect` (and the `ConnectionManager` monitor it takes), not in the transport, the selection thread, the scheduler, the garbage collector or the server. Every call enters `connect`, which under the one `ConnectionManager` monitor walks **all** the muxes (a `BitSet.cardinality()` under each mux's lock, and two lists built) to offer the endpoint the active and idle ones; the endpoint returns the first active one. When 512 callers start together each finds no connection and opens its own (513 sockets), but the endpoint then sends every call to the same one or two muxes (strace: 100% of the client's writes on one descriptor at 64 callers, 52% + 48% on two at 512, the other 460 sockets idle), so the 500 extra connections carry no traffic and cost a longer scan, held under the lock, on every call. With the same callers and 2 to 4 connections throughput does not fall at all.

**Method.** `concurrency-curve.py` for the curve (3 s warm-up, 6 s measured); new tool `collapse-probe.py` (this section) for everything else: a fresh server and client JVM per point, and an external sampler that reads `/proc/<pid>/task/*/{schedstat,status}` of both JVMs before and after a 6 s interval inside the window (CPU, time runnable on a queue, context switches, per thread group) and `/proc/stat` for the machine, with options for `jcmd Thread.print` (`--dump`), a JFR recording of the client (`--jfr`: `jdk.JavaMonitorEnter` from 0 ms, execution samples at 10 ms), `strace -c -f` or per-descriptor `strace` of one JVM (`--strace`, `--strace-fd`; these slow the run a lot, so only ratios are used), and `--prime K`. `ConcurrencyCurve` gained `prime=K`: K calls before the callers start (1: from the main thread; K > 1: K concurrent threads that each open a connection), to set the number of connections apart from the number of callers. Without it nothing changed. **The first probe runs sampled the thread state ten times in the interval and perturbed the run (the sampler's python process made the machine look 87% busy while the JVMs used 1.8 cores); `--light` (two snapshots only; the sampler then costs 0.01 cores) is what every figure below uses.** One run per cell unless two are given; the container is noisy (differences under about 20% are not read).

**1. The curve (DER, UDS, `ping`; calls/ms run 1 / run 2; `concurrency-curve.py`, cold start = every caller finds no connection).**

| callers | platform calls/ms | p50 ms | client sockets | client / server CPU us per call | virtual calls/ms | virtual client sockets |
|---:|---|---|---|---|---|---|
| 64 | 39.9 / 55.2 | 1.2 / 0.9 | 65 / 41 | 55 / 36 and 23 / 20 | 35.7 / 41.0 | 64 / 53 |
| 128 | 32.1 / 34.7 | 1.1 / 1.4 | 129 / 129 | 62 / 57 and 24 / 28 | 40.8 / 38.5 | 91 / 99 |
| 192 | 28.2 / 31.6 | 1.3 / 0.7 | 193 / 193 | 63 / 61 and 37 / 33 | 41.3 / 32.0 | 85 / 95 |
| 256 | 26.6 / 21.7 | 2.3 / 12.2 | 223 / 257 | 64 / 79 and 40 / 50 | 36.6 / 30.4 | 88 / 54 |
| 320 | 19.1 / 18.2 | 19.0 / 18.8 | 321 / 321 | 86 / 92 and 40 / 53 | 39.2 / 36.9 | 93 / 93 |
| 384 | 16.4 / 16.2 | 26.4 / 26.5 | 385 / 385 | 99 / 93 and 54 / 56 | 24.7 / 28.0 | 188 / 156 |
| 512 | 13.2 / 13.4 | 42.0 / 41.7 | 513 / 513 | 122 / 121 and 61 / 59 | 19.1 / 25.5 | 299 / 68 |

The fall is **gradual from 64 callers, there is no cliff at 256** (the second connection is not a visible event). The client's CPU per call more than doubles (55 to 122 us) and the server's nearly triples (23 to 61 us): the work per call grows with the number of callers, which pure oversubscription would not do. The client socket count follows the caller count for platform threads (the cold burst), and is smaller and variable for virtual threads (they start one by one and find connections); the two runs of a point differ most where their socket counts differ.

**2. Number of connections against number of callers (512 platform callers, UDS; `--prime K` opens about K connections before the callers start; calls/ms run 1 / run 2).**

| connections opened first | client sockets | calls/ms | p50 ms | client / server CPU us per call |
|---|---|---|---|---|
| 1 (K = 1) | 5 / 6 | 53.1 / 62.1 | 6.8 / 6.9 | 40 / 33 and 19 / 17 |
| 16 | 18 / 18 | 49.9 / 51.7 | 7.8 / 8.1 | 40 / 38 and 17 / 18 |
| 64 | 28 / 66 | 51.0 / 52.4 | 7.3 / 7.9 | 44 / 44 and 18 / 16 |
| 128 | 68 / 130 | 45.7 / 34.2 | 7.6 / 0.8 | 47 / 61 and 17 / 28 |
| 256 | 258 / 258 | 21.4 / 23.4 | 27.7 / 25.6 | 78 / 74 and 44 / 42 |
| 512 | 148 / 218 | 33.7 / 24.8 | 0.5 / 1.1 | 57 / 71 and 32 / 42 |
| none (the cold burst, section 1) | 513 | 13.2 / 13.4 | 42 / 42 | 122 / 121 and 61 / 59 |

Throughput is a function of the number of connections: about 50 to 60 calls/ms with 5 to 66, then falling to about 22 at 258 and 13 at 513. The same 512 callers, the same server, the same lock and the same 4 cores. And the whole curve flattens when there is one connection first (`--prime 1`, calls/ms run 1 / run 2): 64 callers 62.7 / 64.1; 128: 59.1 / 60.8; 192: 56.9 / 59.3; 256: 62.3 / 59.9; 320: 64.2 / 61.8; 384: 65.3 / 56.8; **512: 64.5 / 60.5**, with 2 to 4 sockets, the second connection opening at 256 without any visible cost, and p50 growing only as N / throughput (0.8 ms at 64, 7 ms at 512; Little's law). Virtual callers: 42.2 / 43.0 (64) and 41.8 / 41.6 (512) cold in this setup, 60.9 / 60.4 and 78.8 / 79.6 with a connection first. So the 5-fold fall at 512 platform callers (13 against 60+) is the number of connections, and nearly all of it; the 64-caller point itself is also about 20% down (47 cold against 58 to 63 with `--prime 1`, 65 against 2 sockets).

**3. Where the callers are.** `jcmd Thread.print` of the client in the window, 512 platform callers, cold: **492 of 512 `BLOCKED` entering the `ConnectionManager` monitor in `ConnectionManager.connect`**, 16 `BLOCKED` in `OutboundMux.newRequest` (the same monitor, `newRequestPending`), 3 waiting for a reply in `MuxInputStream.read`. At 64 callers: 50 in `connect`, 6 in `newRequest`, 7 waiting for a reply. The server JVM at 512: 3 of its 4 dispatch workers parked, nothing else busy. JFR on the client (`jdk.JavaMonitorEnter` from 0 ms, recordings of about 20 s): the `ConnectionManager` monitor accounts for 215 868 contended enters and 358 s blocked in total at 64 callers (mean 1.7 ms), **157 490 enters and 7 724 s at 512 (mean 49 ms, about 390 threads blocked on average)**, and 79 112 enters and 1 772 s (mean 22 ms) at 512 with a connection first, which still contends but runs at 58 calls/ms; the sites are `connect` line 192 (`pendingConnects++`, the first entry), the scan (224), the reservation (243) and `newRequest` (445, 450): the same monitor entered about six times per call. Execution samples at 512 cold: self time `BitSet.cardinality` 27%, the loop at the start of `UdsEndpoint$ConnectionEndpointImpl.connect` 21% (line 441, which should return at its first element; why it is hot was not established, see below), `HashSet.isEmpty` 13% (at the `reaperSet` entry in `connect`), `LinkedList.node` 9%: 70% of the client's caller-thread CPU samples, against `Mux.readMessageBody` 11%; with a connection first `Mux.readMessageBody` is 58% and `cardinality` 1%.

The lock's capacity is the throughput: 1 / 14.4 calls/ms = 69 us of monitor time per call at 512 cold, against 1 / 47 = 21 us at 64 cold. A scan of 513 muxes with two monitor entries each (`ConnectionManager` is re-entered, the mux lock is taken in `requestsInProgress`) at roughly 100 ns each is about 50 us; consistent, not separately measured.

**4. Per-thread CPU (probe `--light`, 6 s interval, UDS, 512 vs 64 platform callers, cold).**

| | 64 callers | 512 callers |
|---|---|---|
| calls/ms | 46.1 | 14.4 |
| machine: user / system / idle | 57 / 25 / 16.5% | 35 / 26 / 37.5% |
| client JVM CPU, cores | 2.23 | 1.63 |
| callers (all threads) CPU, cores; us per call | 1.76; 38 | 1.32; 92 |
| client selection thread `(JSK) I/O SelectionManager-0` | 39% of a core; 8.5 us/call | 26% of a core; 18 us/call |
| client runnable-but-waiting (schedstat), cores | 6.8 | 1.35 |
| server JVM CPU, cores (4 dispatch workers / selection thread) | 0.93 (60% / 20%) | 0.79 (46% / 23%) |
| involuntary context switches/s, client | 13 189 | 4 910 |

**At 512 callers the machine is 37% idle (about 1.5 of 4 cores) and the client uses less CPU than at 64**: the system is not out of CPU, it is waiting on a lock. That rules out oversubscription as the dominant cause: the run queue at 512 is shorter than at 64.

**5. Hypotheses.**

| | verdict | evidence |
|---|---|---|
| (a) 512 platform threads on 4 cores, scheduler oversubscription | **ruled out** as the main cause (at most the part of the 20% that a CPU-bound control shows) | Machine 37% idle and client run-queue wait 1.35 cores at 512 against 6.8 at 64 (section 4). With a connection first, 512 platform callers on UDS reach 60.5 to 64.5 calls/ms against 62.7 to 64.1 at 64. RMI plain, the control with the same 514 threads on the same 4 cores and the machine 99% busy: 108.5 calls/ms at 64, 85.8 at 512, CPU per call 18 to 22 us (client) flat. Virtual callers with `-Djdk.virtualThreadScheduler.parallelism=` 1 / 4 / 16: 53.3 / 39.8 / 34.2 calls/ms at 512 (3 / 80 / 88 sockets): the scheduler size changes the cold burst (parallelism 1 never opens many connections), not the work. |
| (b) the single selection thread (client) or the dispatch pool (server) saturating | **ruled out** | Client selection thread 26 to 39% of one core, server's 20 to 23% (section 4); 3 of 4 server workers parked at 512. With a connection first the same thread carries 58 calls/ms. |
| (c) lock contention | **ruled in, the dominant cause**: the `ConnectionManager` monitor | Sections 2 and 3. `jdk.JavaMonitorEnter`: all the large entries are `ConnectionManager`. The mux lock was not the limit: with a connection first, where one or two muxes carry everything, throughput is highest. (Not examined: `JavaMonitorWait`, `ThreadPark`; the thread dump covers where callers wait.) |
| (d) garbage collection, memory | **ruled out** | `-Xlog:gc`, 16 s runs: client 31 young pauses, 204 ms in all (max 11 ms) at 64 callers, 39 pauses, 226 ms (max 21 ms) at 512; server 220 ms and 139 ms (max 16 ms): about 1% of the time. The allocation per call grows (about 17 KB to about 29 KB on the client, from the sizes of the young collections: the two lists of up to 513 nodes built per call) but at 15 calls/ms is no load. RSS 465 to 1 000 MB, not related to the throughput. |
| (e) flow control, the second connection at 256, all callers on one connection | **second connection: ruled out; concentration on one or two connections: confirmed, and it is the mechanism** | A connection first: the second connection opens at 256 and nothing falls (above). `strace -f -e write...` of the client, 4 s: 64 callers 14 921 writes, 100% on one descriptor (65 sockets); 512 callers 4 096 writes, 52% and 48% on two descriptors (463 sockets), i.e. the 256-session limit of each, the rest idle. Rations were not examined separately; nothing points at them. |
| (f) the server | **ruled out as the limit**; its CPU per call is a consequence | 0.8 to 1.0 cores, mostly idle at 512. Its CPU per call rises 20 to 55 us cold and stays 17 to 19 us with a connection first, tracking the number of connections in section 2; consistent with requests reaching it one at a time instead of in batches, not tested. |
| (g) the load generator | **ruled out** | Client CPU per call with a connection first is 25 to 35 us at 512 and 64 alike, and RMI's 18 to 22 us at 64 and 512 (same driver). The latency histograms are fixed in size (section 31). |
| strace, system calls per call | **consistent** | `strace -c -f` (slows the run, ratios only): client futex calls per `writev` 6.7 at 64 callers and 27.8 at 512; server 10.2 and 10.6. The client's extra is the park and unpark of callers on the monitor. |

**6. The other transports (probe, 1 run each, calls/ms at 64 then 512; "cold" = no connection first; "first" = `--prime 1`).**

| | cold 64 | cold 512 | first 64 | first 512 | sockets cold 512 / first 512 |
|---|---|---|---|---|---|
| DER UDS | 46.1 | 14.4 | 59.1 | 58.8 | 513 / 4 |
| DER TCP | 47.1 | 13.1 | 52.9 | 62.2 | 514 / 6 |
| DER TLS (CPU-bound here: machine 1 to 2% idle) | 24.6 | 9.1 | 33.1 | 31.6 | 514 / 41 |
| RMI plain (control) | 108.5 | 85.8 | | | 514 / - |

In this container TCP and TLS fall as UDS does, and all three hold at 512 with a connection first. TCP's client threads are two per connection (1 561 client threads cold at 512): the same lock, the same cause. The maintainer's workstation (12 logical CPUs, Windows) shows UDS 56 to 26, TCP 50 to 35, TLS 50 to 55 between 64 and 512; this container does not reproduce TLS holding. **The channel path (single selection thread, caller-thread drainer) is therefore not the cause here**: it is the same code in `ConnectionManager` for every transport, and the UDS selection thread is 26% busy at 512 callers. Why TCP falls less and TLS not at all on the workstation is **not established**; the quantity that would decide it is the `client_sockets` of those runs (if TLS, whose handshake takes long, ends the cold burst with far fewer connections than callers, the explanation is the number of connections), and a `--prime 1` run there.

**7. Ranked conclusion.**
1. (nearly all of the loss at 512 callers: 13 calls/ms cold against 60 with a connection first) The number of connections the cold burst leaves behind, times the per-call scan of all of them under the single `ConnectionManager` monitor; the lock is then the throughput (about 70 us per call at 513 muxes, about 20 us at 65, a few us at 2 to 4), and the callers queue on it (492 of 512 blocked). The extra connections carry no traffic; the endpoint picks the first active mux.
2. (the 20% at 64 callers, a share of the rest) The same lock at lower dose: 65 muxes cold against 2.
3. (a consequence, not a cause) The server's CPU per call rising 20 to 55 us, and the client's allocation per call rising.

Not causes: scheduler oversubscription, the selection thread, the dispatch pool, GC, memory, the second connection, the channel path, the load generator.

**8. What remains unexplained.** (i) Why `UdsEndpoint$ConnectionEndpointImpl.connect` line 441 (the loop over the offered active connections, which returns at its first element when there is no `SecurityManager`) is 21% of the samples at 512 cold; it may be the sample attribution of inlined list or monitor code, not established. (ii) How much of the lock's cost is the length of the scan and how much the convoy (a contended monitor hands over through the kernel: 28 futex calls per call): both fall together when the number of connections falls, the experiments here do not separate them. (iii) The distribution of latency at 100 to 250 connections: p50 0.5 to 1.1 ms with p99 about 90 ms (section 2, rows 128 and 512): most callers are served quickly and a few wait very long, an unfairness of the monitor not investigated. (iv) The workstation's TCP and TLS curves (above). (v) Whether the same holds on another JDK or another operating system: not tried. (vi) The server's CPU per call vs batching (f). (vii) What the next limit is with a connection first (about 60 calls/ms on UDS at both 64 and 512; the client uses 2 cores and the server 1.1 to 1.4 of the 4, the machine 18 to 21% idle, 58% of the caller samples still in `connect`): not examined.

**Reproduce.** `python3 collapse-probe.py --light --out probe-x --callers 512 --kind platform [--prime 1] [--transport TCP|TLS] [--stack RMI] [--dump] [--jfr] [--strace client --strace-fd] [--jvm-args '...']`; the curve with `concurrency-curve.py --stacks DER --transports UDS --kinds platform,virtual --callers 64,128,192,256,320,384,512 --warmup 3 --measure 6`.

## Thirty-fourth section: what the 12 ms of opening a TLS connection is made of, full against resumed handshakes, what resumption does to authentication, and how fast a listener accepts new connections (container, 4 cores, JDK 27 `27-internal`, server in its own JVM, TLS 1.3, ECDSA P-256, mutual authentication; re-run on the workstation before quoting)

**The questions.** (A) Section 30 measured 12 ms for the first call on a freshly opened TLS connection against 2.3 ms for UDS and TCP, and a `javax.net.debug` run showed that 18 of 19 handshakes were TLS 1.3 PSK resumptions. Where do the 12 ms go, on the client and on the server? (B) How much slower is a full handshake in this stack? (C) What does resumption do to authentication: is the server's trust manager called again, what is the session's creation time, can a client with a changed credential still resume, does `setEnableSessionCreation(false)` after the handshake lose the ticket, does `invalidate()` stop resumption? (D) How many new connections a second does the listener accept? The maintainer is deciding on the non-blocking TLS redesign (`docs/DESIGN-tls-nonblocking-connectionio-2026-10-20.md`).

**Short answer.**
1. **The 12 ms is mostly a JVM that has not finished compiling the handshake code, not a property of the protocol.** The same resumed open costs 11.4 ms in the first 100 reconnects after ConnectionCost's 15 warm-up reconnects, 7.4 ms after 600, 5.6 ms after 1 500 and **5.1 to 5.3 ms after 3 000 to 6 000** (flat from there). While it is falling the C1/C2 threads burn 12 to 19 ms of CPU per reconnect in the client JVM. The warm call is 0.16 to 0.46 ms, so a hot resumed open is about 4.7 ms, still twice the 2.3 ms of UDS and TCP.
2. **Resumed, hot (5.6 ms with tracing): the cost is a chain of small things on one critical path, none dominant.** Roughly: JERI set-up before the socket 0.2, TCP connect 0.14, engine set-up 0.13, ClientHello generation 0.7 (X25519 and ML-KEM key generation), client waits 1.6 for the server's flight (the server computes 1.2 of it, again X25519 and ML-KEM), client processes the flight 0.6, then the mux handshake 1.2 (two platform threads started on each side, 0.3 to 0.4 ms per side) and 0.65 for the call itself. **The JGDMS credential, permission and `AuthManager` work is under 0.1 ms; the `Selector` opened per handshake read is 0.03 ms per read (0.07 ms per open, 1 to 2%); neither is worth a change for latency.** What is real: the JDK 27 default key exchange is a hybrid (`X25519MLKEM768`) done even on a resumption (PSK with ECDHE), about 1.6 ms of the 5.6 hot (traced); limiting both JVMs to `-Djdk.tls.namedGroups=x25519` saves 0.4 ms (8%), `secp256r1` only is 0.5 ms slower. The two platform threads started per connection on each side cost 0.8 ms of the critical path (0.4 client, 0.4 server).
3. **Full against resumed (this stack): +3.5 ms cold (14.8 to 16.1 against 11.6 to 12.5 ms) and +2.7 to 3.4 ms hot (7.6 to 9.0 against 4.9 to 5.6 ms).** Resumed-hot to full-hot is 1.5 times; certificate work is the JSSE signature and chain verification inside the delegated tasks (client +1.2 ms, server +1.2 ms hot), not the JGDMS callbacks (`checkServerTrusted` 0.01 to 0.03 ms).
4. **Resumption facts (C), measured in memory and confirmed in the real stack's debug log:** the trust manager is not called on a resumed handshake; the session creation time stays that of the original full handshake along a chain of resumptions; a client that was trusted at ticket issue still resumes after the server stops trusting it, and with a switched credential it is still the OLD identity the server sees; `setEnableSessionCreation(false)` before or after the NewSessionTicket makes no difference; client-side `invalidate()` stops resumption, server-side `invalidate()` does not when the tickets are stateless (the JDK default, and what the real server issues).
5. **Accept rate (D, partial):** on this box a listener accepts about **385 resumed connections a second (210 full)** from raw clients that share the 4 cores, with the accept thread busy for ~97% of the time but about half of that on CPU (the rest is waiting for the client's handshake messages and starting two threads per connection); from about 64 simultaneous connectors the 50-entry listen queue overflows (kernel `ListenOverflows`) and connects take 1 to 9 s to complete (the Linux form of what Windows reports as `Connection refused`).

**Method.** New tools (all Linux, JDK 25+, shaded jar): `TlsOpenCost` (client: the first call on a reconnect, resumed or full), `tls-open-cost.py` (starts the server JVM, with JFR if asked, runs the client, calls `JfrOpenPhases` for the phase table from `jdk.MethodTrace` events of the connection set-up in both JVMs, correlated by wall clock, and ExecutionSample at 1 ms), `TlsAcceptRate` and `JfrAcceptThread` (D), `TlsResumption` (C). Reconnects as in `ConnectionCost`: `-Dorg.apache.river.jeri.connectionTimeout=30`, 120 ms between samples, 15 warm-up reconnects, median of 60 to 100 samples ("cold" below, the regime of section 30); and a "hot" regime: 3 000 warm-up reconnects, `connectionTimeout=10` and 50 ms between samples, 100 to 300 samples. Full handshakes are forced without any product change: before each reconnect `TlsOpenCost` invalidates, by reflection into the pool of client `SSLContext`s in `net.jini.jeri.ssl.Utilities`, every session in their client session caches, so the ClientHello offers no PSK and the server sends CertificateRequest/Certificate/CertificateVerify (confirmed in the debug log: a full run of 10 connections has 10 server Certificate messages, a resumed run one); everything else (same `SSLContext`, credentials, JIT state) is unchanged. JFR tracing costs up to 0.7 ms hot (4.9 to 5.6 ms resumed with the method traces, 6.4 with the extra JSSE traces), so phase numbers are for proportions; the untraced medians are the headline numbers. The machine was quiet during every timed run (no other benchmark process; checked before and after each run). A 6 000-reconnect run with `connectionTimeout=10` and 50 ms spacing lost no sample; with 30 ms spacing calls failed as the idle reaper closed the connection (`server sent bad magic number`, `Connection closed` while writing the mux header), see "Not established".

**A. Where the time goes (DER `ping`, resumed; median ms of 100 samples; JFR method traces; the offsets are on the client's clock from the start of the first call; the server's accept thread starts 1.26 ms (cold) or 0.60 ms (hot) after the call began).**

| phase (client unless stated) | resumed cold | resumed hot | full cold | full hot | critical path |
|---|---:|---:|---:|---:|---|
| first call on the new connection (`TlsOpenCost`, no JFR, 60 to 300 samples) | **12.2** (11.6 to 12.4) | **4.9** (5.1 to 5.3 in the 6 000-reconnect run) | **15.8** (14.8 to 15.8) | **7.6** | |
| same with the method traces on | 12.5 | 5.6 | 16.1 | 9.0 | |
| JERI before the socket (`ConnectionManager.connect` to `connectChannel`; includes `SslConnection.<init>` 0.18/0.05 and the credential pool + `checkAuthentication` 0.07/0.02) | 0.49 | 0.21 | 0.45 | 0.29 | yes |
| TCP connect (`connectChannel`) | 0.27 | 0.14 | 0.27 | 0.18 | yes |
| `SSLEngine` creation, parameters, `SslEngineChannel.<init>` | 0.27 | 0.13 | 0.26 | 0.13 | yes |
| ClientHello generation (`beginHandshake`: key shares, PSK binder) | 1.73 | 0.72 | 1.43 | 0.68 | yes |
| engine wraps and channel writes (ClientHello, Finished; 3 calls) | 0.25 | 0.13 | 0.28 | 0.18 | yes |
| wait for the server's flight (`fillNetIn`, one read; includes `Selector.open` 0.05/0.02 and register) | 3.74 | 1.59 | 4.11 | 1.94 | yes (the server's work, below) |
| client processes the flight (`runDelegatedTasks`, 2 tasks) | 1.54 | 0.59 | 3.92 | 1.82 | yes |
| handshake done at (`jdk.TLSHandshake` marker) | 8.86 | 3.70 | 10.98 | 5.53 | |
| mux handshake (`OutboundMux.newRequest` = `Mux.start`) of which 2 client `Thread.start` | 2.31 (0.43) | 1.23 (0.40) | 3.83 (0.44) | 2.53 (0.51) | yes |
| the call itself on the new connection (the rest) | 1.2 | 0.65 | 1.1 | 0.8 | yes |
| **server accept thread**, `serverConnection` | 8.74 | 3.54 | 12.58 | 6.31 | yes, mostly |
| - waiting for the client (`fillNetIn`, 3 reads; 3 `Selector.open` 0.11/0.04) | 3.72 | 1.55 | 6.10 | 2.89 | |
| - delegated tasks (ClientHello consume, ServerHello/EE/Finished produce; full: also the client Certificate and CertificateVerify) | 3.00 | 1.15 | 5.14 (2 tasks) | 2.33 (2 tasks) | yes |
| - wraps (4 calls), `chooseServerAlias`, `getClientSubject`, `getServerCertificate` | 0.23 + 0.13 | 0.14 + 0.02 | 0.28 + 0.10 | 0.19 + 0.02 | partly |
| `handleConnection` after the handshake (`ServerConnectionManager`, `MuxServer`, 2 `Thread.start` 0.43/0.38) | 0.55 | 0.48 | 0.57 | 0.55 | yes (the server's reader thread must exist to read the client's header) |

The phases add up to the handshake marker (hot resumed: 0.21 + 0.14 + 0.13 + 0.72 + 0.13 + 1.59 + 0.59 = 3.5 against 3.7). The server's work between receiving the ClientHello and writing its flight (unwrap 0.3 + delegated tasks 1.15 + wraps 0.14) is what the client waits 1.59 for; the server's `serverConnection` for a client that is slow to start (engine creation 0.14 ms before the ClientHello arrives) is overlapped with the client's ClientHello generation.

**Where the CPU goes (execution samples at 1 ms, hot resumed, 150 opens; share of the samples of the calling thread inside the first call, JIT and waiting not sampled).** Client: ClientHello generation 46% (`beginHandshake`); over the whole open X25519 `pointMultiply` alone is 42% of the samples (Java field arithmetic in `IntegerPolynomial25519.mult/square/carryReduce`, 32% of the samples as top frame); `ServerHello` consumption 30% (`KAKeyDerivation.t13DeriveKey` 24%, `Hybrid` decapsulation 21%, `ML_KEM.decapsulate` 7.5%), `Finished` 7%, HKDF 4%, JCA provider look-ups (`Provider.getService`, `GetInstance`) 5%, `AccessController.getContext` through `SubjectDomainCombiner.combine` and `Uri.urlToUri` 4% (the permission machinery, 0.15 ms). Server: ServerHello production 50% (`KAKeyDerivation.encapsulate` 38%: X25519 key generation 15%, X25519 agreement 12%, `ML_KEM.encapsulate` 5%), ClientHello consumption 22% of which `SessionTicketExtension.decrypt` 5%, NewSessionTicket production 5%, `handleConnection` 7%. The hybrid key exchange (`X25519MLKEM768` is the JDK 27 default share) is done on every resumption (JSSE only offers `psk_dhe_ke`). Method-trace times of the same pieces (hot): client key generation 0.32 (hybrid) + 0.41 (X25519, 2 calls), `t13DeriveKey` 0.43, server `encapsulate` 0.66.

**Cold against hot, resumed (`TlsOpenCost`, 120 ms spacing, 1 500 reconnects after 15 warm-up; median first-call ms per block of 100).** 11.4, 9.2, 8.4, 7.8, 7.5, 7.4, 7.4, 7.1, 6.7, 6.3, 6.4, 6.3, 6.1, 6.1, 5.6. With 50 ms spacing and `connectionTimeout=10` for 6 000 reconnects (blocks of 400): 8.6, 6.8, 6.1, 5.5, 5.6, 5.5, 5.3, 5.4, 5.5, 5.5, 5.3, 5.3, 5.3, 5.2, 5.1. CPU per reconnect (thread groups from `/proc/<pid>/task/*/schedstat`; the client's main thread, the server's accept thread which is a virtual thread on a ForkJoin carrier, and the compiler threads): cold resumed client main 6.2 ms + JIT 11.9 ms, server 6.0 ms + JIT 5.3; hot resumed client main 2.65 + JIT 3.4, server 2.56 + JIT 2.1; so even at ~5 ms the JVM is still compiling (3 to 5 ms of JIT CPU per reconnect after 3 000 reconnects).

**Named groups (hot, resumed, untraced, 300 samples, both JVMs).** default (`X25519MLKEM768`, `x25519`, `secp256r1`, ...) 4.9 ms; `-Djdk.tls.namedGroups=x25519` 4.5 ms; `-Djdk.tls.namedGroups=secp256r1` 5.4 ms. Dropping the hybrid group removes a post-quantum protection of the session key (the maintainer's call, not a recommendation); not measured cold.

**B. Full against resumed.** Pairs run back to back (median first call, ms): cold 12.2 / 15.8, 11.6 / 14.8, 12.5 / 16.1 (with traces); hot 4.9 / 7.6 (untraced), 5.6 / 9.0 (traced). So +3.3 to +3.6 cold, +2.7 to +3.4 hot. CPU of the two threads that do the work, per open (excluding JIT): cold client main 6.2 resumed / 8.3 full, server 6.0 / 7.9; hot client 2.65 / 4.0, server 2.56 / 3.80 (+1.4 and +1.2 ms). The extra is JSSE's certificate verification and signing: client delegated tasks 1.54 to 3.92 (cold), 0.59 to 1.82 (hot); server 3.00 to 5.14 (cold), 1.15 to 2.33 (hot); the mux handshake is also 1.3 to 1.5 ms longer on the client because the server finishes later. The JGDMS part of a full handshake is small: `checkServerTrusted` 0.03 / 0.01 ms, `chooseEngineClientAlias` 0.13 / 0.02 ms (cold / hot), `chooseServerAlias` 0.04 to 0.08 ms. **The real stack:** `-Djavax.net.debug=ssl:handshake` on both JVMs, 10 connections each: resumed run: server `Consuming ClientHello` 10, `Resuming session` 9 (the first connection is the only one with CertificateRequest, server Certificate, client Certificate and CertificateVerify), `Produced NewSessionTicket stateless post-handshake message` 10; forced-full run: 10 CertificateRequests, 10 server Certificates, 10 client Certificates, 0 `Resuming session`. **The server issues stateless tickets** (the JDK default `jdk.tls.server.enableSessionTicketExtension=true`), so its session cache holds no sessions (`getServerSessionContext().getIds()` is empty).

**C. Resumption and authentication (`TlsResumption`: client and server `SSLEngine` from one `SSLContext` each, TLS 1.3, `setNeedClientAuth(true)`, trust managers that count and log; run twice, with stateless tickets (the default) and with `-Djdk.tls.server.enableSessionTicketExtension=false` (stateful, the server keeps sessions in its cache)). Plain facts:**

| # | question | result |
|---|---|---|
| C1 | On a resumed handshake is the server's trust manager called to validate the client certificate again? | **No.** Connection 1 (full): `checkClientTrusted` 1 call, `checkServerTrusted` 1 call; connections 2 to 6 (resumed): 0 calls on both sides. The server's session still has the peer: `getPeerPrincipal()` is the client's (`CN=client1`) and `getPeerCertificates()` the original chain, carried inside the ticket (stateless) or the cache (stateful). A resumed handshake also sends no Certificate message: server to client 1 880 bytes in the handshake (plus 529 in the post-handshake ticket records) against 2 357 (plus 529) for a full one; the difference is the Certificate, CertificateVerify and CertificateRequest. |
| C2 | What is `getCreationTime()` of a resumed session, and does it change along a chain of resumptions? | **It is the original session's creation time on both sides, and it never changes** (6 links: client T+11 ms, server T+5 ms for all, `lastAccessedTime` advances). A chain of resumptions is not limited by the first session's age: with the server context's session timeout set to 3 s, connections every 1 s still resumed at 9 s (age 9.2 s). After 4.5 s idle (> 3 s) the stateless server STILL resumed (by reading the JDK source: it checks only the ticket-encryption key's life, `jdk.tls.server.statelessKeyTimeout` 1 h plus the session timeout, not the ticket's own lifetime or age); the stateful server did a full handshake. Consequence for JGDMS: `maxServerSessionDuration` and `maxClientSessionDuration` (`session.getCreationTime() + duration`, checked per request in `SslServerEndpointImpl` and per use in `SslConnection`) are anchored at the last FULL handshake, so a resumption chain cannot extend a session beyond them. |
| C3 | Can a client whose certificate is no longer accepted still resume, and what does the server see? | **Yes.** After connection 1 the server's trust manager was set to reject `CN=client1`: connection 2 and 3 (same `SSLContext`) resumed, trust manager not called, server sees `CN=client1`; a fresh client `SSLContext` (no ticket) and the same context toward another peer host (no ticket) were rejected (`certificate_unknown`). With the client's key manager switched to a second certificate (`client2`) in the same `SSLContext`, the next connection to the same host and port resumed the OLD session: server sees `CN=client1` (and the client's own `getLocalPrincipal()` is `CN=client1`), `client2` is used only when no ticket applies (new peer host: full handshake, server sees `CN=client2`). So trust revocation and credential replacement take effect for a client only at its next full handshake. **[code, not run in the real stack]** the real stack has three guards: `Utilities.getClientSSLContextInfo` calls `ClientAuthManager.checkAuthentication` (destroyed, expired or changed credentials) before reusing a pooled `SSLContext` and otherwise builds a new one with an empty session cache; `ServerAuthManager.checkCredentials` runs on every request and includes `certificatesValidUntil(clientChain)`, taken from the session's peer chain, so a client certificate that has expired is rejected per request even on a resumed session; and the creation-time limits of C2. Not rechecked on resumption: a revoked certificate or a removed trust anchor, until `maxServerSessionDuration` (24 h by default) from the last full handshake. |
| C4 | The client calls `engine.setEnableSessionCreation(false)` after the handshake and before the NewSessionTicket is processed: does it still store the ticket and resume? | **Yes, in every order.** Called before the ticket (as `SslConnection.establishEngine` does), after it, and never: connection 2 and 3 resume in all three; the client cache holds the same 2 entries. Why: `sun.security.ssl.NewSessionTicket$T13NewSessionTicketConsumer` never reads `enableSessionCreation`; the flag is consulted only when a handshake would create or establish a session (`ClientHello` lines 539 and 563, `ServerHello` lines 270, 507, 1149, 1168, 1307 in this JDK). It is per engine, and a new engine starts with it `true`. Called BEFORE the first handshake of a new engine with no cached session it fails: `No new session is allowed and no existing session can be resumed` (so the order in `SslConnection`, after `handshake()`, is required). The real server also sets it after its handshake and still produces tickets. |
| C5 | Does `SSLSession.invalidate()` prevent later resumption? | **Client side: yes** (`engine.getSession().invalidate()` on the original session invalidates its tickets: next connection full; invalidating a resumed session: the next one is full; invalidating every session in the client session context: full). **Server side: only if the server keeps sessions.** Stateless tickets (default): `invalidate()` on the server's original or resumed session, and on every session of the server session context (`getIds()` is empty: 0 sessions), change nothing, the next connection RESUMES. Stateful (`enableSessionTicketExtension=false`): server `invalidate()` of the original, of the resumed session, or of the whole context makes the next connection full. The real server's `session.invalidate()` calls (`ServerAuthManager.chooseServerAlias` over `getServerSessionContext().getIds()`, and the expiry in `processRequestData`) therefore cannot revoke already issued tickets with stateless tickets; the client's `maxClientSessionDuration` invalidation (23.5 h) does work. |

**D. New connections a second (optional, partial).** `TlsAcceptRate`: N raw `SSLSocket` client threads (own `SSLContext` each, so a ticket cache each; the benchmark credentials; they connect, handshake, send the 8-byte mux client header, read the 8-byte server header, close) against `ServerMain`'s TLS listener (`SslServerEndpointImpl.acceptLoop`, handshake on the accept thread). The client JVM shares the 4 cores, so the rates are lower bounds. 10 s measured after a warm-up; accepted connections per second, with the p50 and p99 of connect-to-mux-header and the kernel `ListenOverflows` during the run (whole machine).

| client threads | resumed: per second | p50 / p99 ms | ListenOverflows | full: per second | p50 / p99 ms |
|---:|---:|---|---:|---:|---|
| 1 | 199 | 4.0 / 12.9 | 0 | 124 | 6.7 / 18.4 |
| 4 | 374 | 9.6 / 23.8 | 0 | 213 | 17.7 / 28.7 |
| 16 | 384 to 392 | 39 / 81 | 0 | 211 | 73 / 108 |
| 64 | 289 to 415 | 121 to 146 / 200 to 1 340 (max 7 to 9 s) | +169 | 169 | 237 / 1 890 (max 4.3 s) |
| 128 | 332 (18 in a run with 3 s warm-up) | 135 / 3 700 (max 12 s) | +933 | 103 | 265 / 4 330 |
| 256 | 134 | 185 / 8 630 | +2 288 | | |

The accept thread (JFR method traces, 16 threads, resumed): per connection **3.4 ms traced, 2.6 ms untraced** (the inverse of 392 per second), made of `serverConnection` 2.79 ms (waiting for the client's handshake messages in `fillNetInByDeadline` 1.13 ms in 2.2 reads, delegated tasks 1.11, wraps 0.16, `Selector.open` 0.03), `handleConnection` 0.57 (two `Thread.start` 0.44) and `accept` 0.03; busy 97% of the time (traced), its carrier thread uses 0.4 to 0.55 of a core (about half of it on CPU). So the rate is the inverse of **handshake CPU + the waits for the peer's two flights + two thread starts**, serialised: the `bind(new InetSocketAddress(port))` default backlog of 50 overflows when more than about 50 connect together, the kernel drops the SYNs, and clients retry after 1, 3 and 7 s (hence the multi-second maxima; Windows refuses instead, which is the likely source of the 1 900 `Connection refused` at 4 096 callers; not reproduced on Windows). With a real network round trip the waits are larger than here. **The design's "about 80 new connections a second per listener" is the cold JVM (12 ms); a hot listener does 385 resumed or 210 full on this box, with the client on the same cores.**

**Not established.** (i) The first connection of a JVM (class loading and interpretation of the whole JSSE and JGDMS path) was not measured in a quiet run; hundreds of ms were seen once in a loaded test. (ii) The split of the hot 5 ms between pure computation and wake-up latencies of the 8 thread hand-offs of the open (accept, accept thread, selector wake-ups, two mux threads each side) is not separated; the traced waits include scheduling delays (0.1 to 0.2 ms per hop from the offsets). (iii) The 3 to 5 ms of JIT CPU per reconnect continues past 3 000 reconnects; where the plateau of 5.1 ms would settle with more is not known. (iv) The effect of `jdk.tls.namedGroups` was measured hot and resumed only; the ML-KEM share cold and for a full handshake is unknown. (v) With `connectionTimeout=10` and spacing near 30 ms, calls failed when the idle reaper closed the connection as the call started (`IOException: server sent bad magic number: 8C000005` once, `Connection closed` while writing the mux header); the race between `ConnectionManager`'s reaper shutdown and a new request was not investigated and may exist with larger timeouts. (vi) C3 and the per-request guards are from reading the code, not from a real-stack test with an expired certificate. (vii) D used raw clients on the same cores, no real network, no Windows; the accept thread's share on CPU is from `schedstat`, the rest from traces. (viii) A burst of N simultaneous first opens: by reading `Utilities.getClientSSLContextInfo`, a pooled client `SSLContext` is removed from the pool while a connection is being established, so concurrent opens each build a new `SSLContext` (empty ticket cache) and handshake in full until the pool has grown; not measured. (ix) Cold and hot numbers from the container are not the workstation's.

**Reproduce.** `python3 tls-open-cost.py open --mode resumed|full --samples 100 --warm 15 --sleep 120` (cold) or `--warm 3000 --sleep 50 --timeout 10` (hot); `--jfr trace|trace2|sample` for the phase table and the sampled profile; `--both=-Djdk.tls.namedGroups=x25519`; `python3 tls-open-cost.py accept --mode resumed|full --threads 1,4,16,64,128 --seconds 10 --warmup 5 [--jfr]`; `java -cp target/serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.TlsResumption [-v]`, once as is and once with `-Djdk.tls.server.enableSessionTicketExtension=false`. Build the jar first (`mvn -o -q -f jgdms-benchmarks/serialization/pom.xml package -DskipTests`).

## Thirty-fifth section: `ConnectionManager.connect` in constant time, one connection opened at a time, and the reaper race (container, 4 cores, JDK 27 `27-internal`, server in its own JVM, DER, `ping`, platform-thread callers, closed loop; re-run on the workstation before quoting)

**Short answer.** The per-call scan of all connections is gone and a cold burst now opens a few connections instead of one per caller. DER over TCP and UDS, cold start, 512 callers: 12.3 to 63.5 calls/ms (TCP) and 11.6 to 46.0 (UDS), p50 46 ms to 7 ms (TCP); 4096 callers: 0.9 to 33.1 (TCP), 9.7 to 27.6 (UDS); the cold curve is now close to the "one connection first" curve of section 33. TLS is CPU-bound in this container and gains less (512 callers 8.3 to 17.6, and every caller is served where 55% were starved; 4096 callers 7.6 to 8.7, inside the spread). With one connection opened first nothing changes at 64 and 4096 callers (it was already flat) and at 512 callers the change is within the run-to-run spread except TLS (+13%). **Not improved:** 4096 callers on any transport is still 25 to 40 calls/ms against 50 to 70 at 512, and with TLS 20 to 35% of 4096 callers complete no call in the 6 s window, before and after; that is not the connection manager (below, "Not established"). The same change fixes the reaper race of section 34 (v): `Connection closed`, `server sent bad magic number: 8C000005`, `channel already registered` and `Socket closed` each occurred in thousands of calls with a 2 ms idle timeout and now occur in none.

### 1. What changed

**The tool (commit "ConcurrencyCurve: mean latency by Little's law...").** Every point now also reports `mean_little_us` (callers / throughput, closed loop only; in an open loop the number of callers is not the concurrency, so null) and `callers_completed` (callers that completed at least one call inside the measured window), as the last two JSON fields and the last two columns of the summary table (`mean L us`, `callers done`); the progress line shows them too. `concurrency-curve.py` gained `--prime K` (passed to `ConcurrencyCurve prime=K`, recorded in the environment file). Existing fields and columns are unchanged. A starved point is now visible: the 4096-caller cold TCP point of the old build has, in two of its three runs, a p50 of about 2.7 s and `callers done` 2438 and 2806 of 4096.

**`ConnectionManager` (and a hook in `Mux`).**
1. *Constant-time common path.* The manager keeps `available`, the muxes that may have room, most recent first (a deque, with a flag per mux), and offers the endpoint the first `OFFER_WINDOW = 4` of them (active ones in one list, idle ones in the other, as before). If the endpoint returns null and there were more, it is offered all of them (so an endpoint that is selective, such as SSL with different subjects, still sees every candidate; that case costs as much as before). A mux met at the front that has gone down, was removed, or is full is dropped from `available`. `muxes` is a `LinkedHashSet` (O(1) removal).
2. *How a full mux comes back.* The end of a request on the mux calls a new, lock-free hook (`Mux.handleSessionRemoved()`, called from `removeSession` after `muxLock` is released, by a thread that may hold session locks): if the manager marked the mux `full` it is put (once) on a `ConcurrentLinkedQueue`, and the next call drains that queue under the monitor, re-checks the count and puts the mux back at the front of `available`. The mark is set and then the count re-checked (`confirmFull`), so a request that ended just before the mark cannot be lost; `newRequest`'s `finally` makes the same notification, because it can lower the count.
3. *Reservation unchanged in meaning.* The capacity check and `newRequestPending()` are still one critical section, on the mux the endpoint chose, with `MAX_REQUESTS = 256`; if the mux is full, down, or no longer the manager's the call goes round again (at most 16 times, then it opens).
4. *One open at a time; the monitor is not held across I/O.* A call that finds no mux with room and an open in flight (`opening > 0`) waits (`wait()` on the manager, uninterruptibly, restoring the interrupt flag at the end) and looks again when an open finishes; otherwise it opens (`ep.connect(handle)` and the mux construction, outside the monitor). A failed open throws its own exception to its own caller and wakes the others, which, having seen an open fail, no longer wait but open for themselves, together (so a dead host costs the failure time once or twice, not once per caller; and a `SecurityException` or credential failure of one caller's handle is not passed on to the others). The mux handshake still happens in `OutboundMux.newRequest` outside the monitor, as before.
5. *The global reaper set is entered once per manager*, not once per call (`registered` flag, reset by `checkIdle` when it tells the reaper to drop the manager); every call used to take `synchronized (reaperSet)`, which section 33 saw at 13% of the samples (`HashSet.isEmpty`).
6. *Fewer monitor entries per call:* two (offer; reserve and give up the pending-connect count together) instead of four on the common path.

**The reaper race (section 34 (v)); root cause.** `connect` built the lists under the monitor, called the endpoint outside it, and then looked the connection the endpoint returned up among `muxes` under the monitor; **if it was not there it assumed the endpoint had opened a new connection and wrapped it in a new `OutboundMux`.** It is not there when the idle reaper has removed that mux in the meantime (`checkIdle` removes it under the monitor, `shutdown("idle")` follows outside any lock). The new mux sits on a connection that is being closed ("Connection closed" writing the mux header; `Socket closed`; `ClosedChannelException`) or, if the reaper has not yet closed it, on a connection that the old mux is still reading: two muxes read one stream, the new one takes as its server header the first bytes of a frame meant for the old one (`8C 00 00 05` is a DATA message with the close and end-of-file bits set, session 0, 5 bytes: the response of the previous call: `server sent bad magic number: 8C000005`), or registers the same channel twice (`IllegalStateException: channel already registered`). The reaper itself is not at fault: a mux that `connect` has *reserved* cannot be reaped (`checkIdle` and the reservation are both under the monitor, and the reservation makes `requestsInProgress()` non-zero until the request is a session). The hole was only the not-found path. Now the manager never makes a mux of a connection it was offered: it keeps the offered muxes, and after the endpoint answers checks, with the reservation and under the monitor, that the mux is still the manager's (`removed` flag set by `checkIdle`, `remove`, and the down path); otherwise it goes round again.

### 2. Tests (jgdms-jeri, `ConnectionManagerScalingTest`, 8 tests, about 2 s; `ConnectionManagerCapacityTest`, 2 tests, unchanged and green)

| test | what it checks |
|---|---|
| `testWorkPerCallDoesNotGrowWithTheNumberOfMuxes` | 800 muxes with room (opened by refusing offers), then 4000 calls: no connection opened, and the endpoint is offered at most 8 connections per call (4 in the implementation); sum of offers bounded by 8 per call |
| `testMuxThatLosesARequestIsFoundAgain` | real muxes over a loopback socket to a real `MuxServer`: 256 requests fill connection 1, request 257 opens connection 2, one request on connection 1 is aborted, the next call must use connection 1 and open nothing |
| `testColdBurstOpensFewConnections` | 512 threads released together, opens take 20 ms: at most one open in flight at any time, at most 4 connections opened (2 needed), no mux over 256 |
| `testNoMuxEverHasMoreThanTheLimit` | 1536 threads: no error, no mux over 256, 6 to 10 connections opened |
| `testFailedOpenFailsOnlyItsCaller` | the first open fails after 100 ms with 16 callers waiting: exactly one caller gets the `IOException`, the other 15 get connections, and they open together (peak in flight above 1) |
| `testWaitingCallerKeepsItsInterruptStatus` | an interrupted waiter is not woken to fail, and has its interrupt flag set when it returns |
| `testConnectionClosedByTheReaperAfterItWasChosenIsNotUsed`, `testConnectionTakenOutByTheReaperAfterItWasChosenIsNotUsed` | the reaper's two passes run inside the endpoint's `connect`, after it has been offered the idle connection and before it hands it back; the connection is closed at once, or only after the call has returned: the call must open a new connection and its request must work |

**Mutation checks (each reverted afterwards).** Old `ConnectionManager` (the commit before): 7 of the 8 tests fail (all but `testFailedOpenFailsOnlyItsCaller`): offered 800 of 800, 512 opens at the same time, 36 to 47 connections opened for 1536 callers, the freed slot not used, and the two reaper tests fail with `Socket is closed` / `Socket closed` / `EOFException` writing into the stale mux. The offer window removed (`if (!everything && offer.offered.size() == OFFER_WINDOW)` made `if (false)`): `testWorkPerCall...` fails ("a call was offered 800 of 800 connections"). The wait for an open in flight removed (both `if (opening > 0 && !dontWait)` made `if (false)`): 3 tests fail (512 opens at the same time; 26 connections for 1536 callers; the waiter opens a second connection). The hook disabled (`if (full && freedQueued.compareAndSet(false, true))` made `if (false)`): `testMuxThatLosesARequestIsFoundAgain` fails ("the freed slot should be used"). The `removed` check in `reserve` taken out: `testConnectionTakenOutByTheReaper...` fails ("a new connection was opened expected 2 but was 1"; the other reaper test still passes because the closed mux reports itself down: the `removed` check is what covers the interval between the reaper's `checkIdle` and its `shutdown`). The previous no-free-sessions tests (section 32) still pass with the new code.

The whole jgdms-jeri suite: 178 tests; the 3 `AuthenticateServerTest` SPIFFE failures (on clean trunk) and, in 1 of 3 full runs with the new code and 4 of 4 with the old `ConnectionManager` and `Mux` put back, `MuxChannelBatchingTest.losingTheConnectionFailsWhatIsWaitingInsteadOfHanging` (sleeps 200 ms, then closes the channel under six 3 MB calls; passes alone, depends on the timing of the other tests; it does not use `ConnectionManager`). Not a result of this change.

### 3. Measurement: before and after

**What is measured.** `concurrency-curve.py --stacks DER --transports X --kinds platform --callers 64,512,4096 --payloads ping --warmup 3 --measure 6 [--prime 1]`, X in TCP, UDS, TLS; a fresh server JVM and a fresh client JVM for each point; closed loop; 3 runs of every cell, in the order before/after, after/before, before/after; medians with the range over the 3 runs (sockets: median). "Before" is the shaded benchmark jar with the old `ConnectionManager*.class` and `Mux.class` put into the jar of the new code, so the two builds differ in these classes only; "after" is the jar of the commit. Callers served = callers that completed at least one call in the 6 s window, one value per run (`callers_completed`). The old code had 0 errors and the new 0 errors in all 108 points.


### cold start (prime 0)

| transport | callers | build | calls/ms median (min-max) | p50 ms | p99 ms | mean per call (Little) ms | callers served, per run | client sockets, median |
|---|---:|---|---|---:|---:|---:|---|---:|
| TCP | 64 | before | **43.8** (42.9-45.3) | 1.23 | 5 | 1.5 | 64 / 64 / 64 | 66 |
| TCP | 64 | after | **50.0** (47.1-52.4) | 1.16 | 4 | 1.3 | 64 / 64 / 64 | 3 |
| TCP | 512 | before | **12.3** (11.9-17.7) | 46.33 | 187 | 41.5 | 512 / 512 / 512 | 514 |
| TCP | 512 | after | **63.5** (45.2-65.6) | 7.22 | 24 | 8.1 | 512 / 512 / 512 | 6 |
| TCP | 4096 | before | **0.9** (0.8-3.8) | 2688.55 | 8510 | 4744.4 | 4096 / 2438 / 2806 | 4098 |
| TCP | 4096 | after | **33.1** (24.1-35.7) | 32.60 | 910 | 123.7 | 4096 / 4094 / 4096 | 12 |
| UDS | 64 | before | **44.2** (40.0-45.3) | 1.04 | 8 | 1.4 | 64 / 64 / 64 | 66 |
| UDS | 64 | after | **53.1** (46.0-56.1) | 0.98 | 5 | 1.2 | 64 / 64 / 64 | 3 |
| UDS | 512 | before | **11.6** (10.4-16.7) | 48.43 | 171 | 44.2 | 512 / 512 / 512 | 514 |
| UDS | 512 | after | **46.0** (40.7-54.0) | 8.36 | 50 | 11.1 | 512 / 512 / 512 | 6 |
| UDS | 4096 | before | **9.7** (5.1-9.9) | 569.90 | 1817 | 421.7 | 4096 / 4096 / 4096 | 319 |
| UDS | 4096 | after | **27.6** (24.8-28.5) | 121.90 | 539 | 148.4 | 4096 / 4096 / 4096 | 21 |
| TLS | 64 | before | **16.4** (15.3-19.3) | 2.61 | 16 | 3.9 | 64 / 64 / 64 | 66 |
| TLS | 64 | after | **20.1** (15.5-22.7) | 2.20 | 13 | 3.2 | 64 / 64 / 64 | 3 |
| TLS | 512 | before | **8.3** (8.2-8.4) | 13.16 | 61 | 61.4 | 233 / 230 / 228 | 514 |
| TLS | 512 | after | **17.6** (7.0-17.9) | 20.07 | 251 | 29.1 | 512 / 512 / 512 | 6 |
| TLS | 4096 | before | **7.6** (7.1-8.2) | 65.70 | 2236 | 535.8 | 2651 / 2525 / 2925 | 989 |
| TLS | 4096 | after | **8.7** (8.2-9.1) | 53.28 | 2993 | 472.6 | 3902 / 3713 / 3081 | 15 |

### one connection first (prime 1)

| transport | callers | build | calls/ms median (min-max) | p50 ms | p99 ms | mean per call (Little) ms | callers served, per run | client sockets, median |
|---|---:|---|---|---:|---:|---:|---|---:|
| TCP | 64 | before | **50.0** (49.4-51.7) | 1.16 | 4 | 1.3 | 64 / 64 / 64 | 3 |
| TCP | 64 | after | **50.0** (49.1-50.1) | 1.16 | 4 | 1.3 | 64 / 64 / 64 | 3 |
| TCP | 512 | before | **61.4** (59.0-67.3) | 6.97 | 31 | 8.3 | 512 / 512 / 512 | 5 |
| TCP | 512 | after | **69.1** (59.3-72.3) | 6.87 | 20 | 7.4 | 512 / 512 / 512 | 7 |
| TCP | 4096 | before | **35.6** (26.4-35.7) | 36.50 | 1107 | 115.0 | 4096 / 3617 / 4096 | 14 |
| TCP | 4096 | after | **27.8** (23.5-37.6) | 25.23 | 1167 | 147.2 | 4096 / 4096 / 4096 | 13 |
| UDS | 64 | before | **54.7** (49.8-54.9) | 0.94 | 5 | 1.2 | 64 / 64 / 64 | 3 |
| UDS | 64 | after | **54.8** (51.1-55.4) | 0.97 | 5 | 1.2 | 64 / 64 / 64 | 3 |
| UDS | 512 | before | **47.3** (43.9-54.0) | 8.47 | 33 | 10.8 | 512 / 512 / 512 | 5 |
| UDS | 512 | after | **50.7** (48.6-57.1) | 8.32 | 32 | 10.1 | 512 / 512 / 512 | 5 |
| UDS | 4096 | before | **28.9** (25.2-30.7) | 127.60 | 556 | 142.0 | 4096 / 4096 / 4096 | 19 |
| UDS | 4096 | after | **26.8** (25.6-31.0) | 116.20 | 530 | 152.8 | 4096 / 4096 / 4096 | 20 |
| TLS | 64 | before | **25.4** (24.3-25.5) | 1.93 | 11 | 2.5 | 64 / 64 / 64 | 3 |
| TLS | 64 | after | **25.2** (24.0-26.7) | 1.88 | 12 | 2.5 | 64 / 64 / 64 | 3 |
| TLS | 512 | before | **16.8** (14.6-17.8) | 20.51 | 194 | 30.5 | 512 / 512 / 512 | 33 |
| TLS | 512 | after | **19.0** (18.0-20.0) | 18.97 | 207 | 27.0 | 512 / 512 / 512 | 6 |
| TLS | 4096 | before | **10.0** (9.0-10.4) | 47.84 | 2363 | 409.9 | 3212 / 3213 / 3074 | 61 |
| TLS | 4096 | after | **9.0** (8.8-10.6) | 54.13 | 2128 | 452.8 | 3273 / 2864 / 2613 | 12 |


(Cells with a range are only a little more than three runs on a noisy 4-core container; read differences under about 20% as unproven unless the ranges do not overlap. For 4096 callers on TCP with one connection first I ran 5 more runs of each build, interleaved: calls/ms before 42.1, 39.1, 37.0, 37.3, 41.2 and after 38.1, 40.8, 40.5, 32.7, 42.4; with the first 3 runs the medians of 8 are 37.2 and 37.9. The apparent loss in the 3-run table is noise.)

**What the table says.**
1. *Cold start, TCP and UDS:* the cold curve now looks like the "one connection first" curve of section 33: sockets at 512 callers fall from 514 to 6, at 4096 from 4098 (TCP) and 319 (UDS) to 12 and 21. 64 callers gain about 14 to 20% (TCP 43.8 to 50.0 and UDS 44.2 to 53.1, ranges do not overlap), 512 callers 4 to 5 times, 4096 callers 3 times (UDS) to more than 10 times (TCP: 0.9, range 0.8 to 3.8, to 33.1; the old cold burst opened up to 4098 sockets and stalled for most of the window, and in two of the three runs 1290 and 1660 callers completed nothing).
2. *Cold start, TLS:* 64 callers 16.4 to 20.1 (ranges 15.3 to 19.3 and 15.5 to 22.7 overlap), 512 callers 8.3 to 17.6 (one of the three new runs was 7.0, p99 664 ms; not explained) and the callers served rise from 228 to 233 of 512 to all 512; 4096 callers 7.6 to 8.7 (inside the spread; TLS is CPU-bound here, 4 cores shared by both JVMs), served 2525 to 2925 before and 3081 to 3902 after.
3. *One connection first:* 64 callers unchanged on all three; 512 callers TCP 61.4 to 69.1, UDS 47.3 to 50.7 (overlapping ranges), TLS 16.8 to 19.0 (ranges 14.6 to 17.8 and 18.0 to 20.0); 4096 callers unchanged within the spread (UDS 28.9 and 26.8, TLS 10.0 and 9.0).
4. *Mean per call by Little's law against the percentiles:* at TLS 512 cold before, the p50 is 13 ms and the p99 61 ms, but the mean per call is 61 ms and only 230 of 512 callers completed anything: the percentiles describe the 45% that were served. After, p50 is 20 ms, p99 251 ms, mean 29 ms, all served.
5. *Latency at 64 callers:* the p99 falls from 5.1 to 3.6 ms (TCP), 7.6 to 5.0 (UDS), 16.1 to 12.9 (TLS).

**What is not measured.** Virtual-thread callers (the old section 32 table shows they open fewer connections), JOSS, RMI, the `record` payload, open loop, a real network, Windows, more than 4 cores; the server side (`ServerConnectionManager`); how long a caller waits for an open in flight (the 4096-caller cold TLS point is the one place it could be seen, p99 2.2 s before and 3.0 s after); per-call CPU (not collected for this table); more than 3 runs per cell.

**Caveats.** The container is shared and noisy (4 cores for client and server JVMs; at 4096 platform threads the machine is 52% system time with 33 runnable threads on average, a probe of the new build, UDS, one connection first: 25.6 calls/ms, client 92 us/call against 44 at 512). Other work on the same checkout (commits to the TLS session limits) happened during the session; it does not touch the code path measured. The server is the same in both builds. Times in different sections of this file are not comparable.

### 4. The reaper race: reproduction and result

`ReaperRaceProbe` (new, in the benchmark module) calls a `ServerMain` from 2 threads with a random spacing and counts failures; the client JVM runs with `-Dorg.apache.river.jeri.connectionTimeout=2`, so the idle reaper (a thread that sleeps `connectionTimeout`) takes the connection about as often as calls arrive. With the default 15 s the window is hit as rarely as the 2 failures in 6 000 TLS reconnects of section 34. DER, `ping`; failures / calls, per run:

| transport | spacing | old code | new code |
|---|---|---|---|
| TCP | 3 to 12 ms, 2 x 6000 calls, 4 runs | 2, 0, 13, 3 of 12 000 each: `ConnectIOException / SocketException: Socket closed` (once `Socket is closed`) | 0 of 12 000, 4 runs |
| UDS | 3 to 12 ms, 2 x 6000 calls, 4 runs | 3, 3, 2, 7: `IllegalStateException: channel already registered` (11) and `ConnectIOException / ClosedChannelException` (4) | 0, 4 runs |
| TLS | 5 to 25 ms, 2 x 2500 calls, 2 runs old, 3 runs new | 4 and 5 of 5 000: `Connection closed` (5) and `server sent bad magic number: 8C000005` (4) | 0 of 5 000, 3 runs |

The TLS rows are the two messages of section 34, so that note was the same bug, not a server-side close. (With 8 threads and 1 to 8 ms spacing no failures occurred in 20 000 calls with either timeout 2 or 5: the connection is rarely idle then. The failure rate is a function of the phase of the reaper against the calls, not a property of the transport.) The unit tests of section 2 are deterministic.

### 5. Behaviour changes to be aware of

(a) A caller can now wait for an open in flight (up to the time of that open, or the other caller's handshake time) instead of opening its own; callers whose handle the connection that appears cannot serve (an SSL endpoint with different subjects) may open one after the other, not together. (b) `OFFER_WINDOW` limits what the endpoint sees in the common case; the endpoint is offered everything only after it has answered null to the window. For the TCP, UDS and SSL endpoints in this tree null with a non-empty offer happens only for SSL's `useFor` and for a `SecurityException` on reuse. A mixed-subject SSL workload was not tested. (c) `ConnectionEndpoint.connect(handle, active, idle)` is documented as never called concurrently by the manager; it has been called outside the lock, concurrently, since before this change (commit "Eliminate a contention hotspot in ConnectionManager"). Unchanged here; with a window the lists are now four long. (d) `Mux.handleSessionRemoved()` is a new protected no-op hook, called without `muxLock`.

### 6. Not established

(i) What limits 4096 platform-thread callers once the connection manager is out of the way (25 to 40 calls/ms against 50 to 70 at 512; the client's CPU per call doubles, the machine is mostly system time with 33 runnable threads: oversubscription of 4 cores by 4096 threads is the obvious suspect, but I did not get a thread dump of the client: `collapse-probe.py --dump` printed an empty dump for it). (ii) Why 20 to 35% of 4096 callers complete nothing in 6 s with TLS, in both builds (the monitor and the mux write lock are not fair; not examined). (iii) The one slow TLS 512 cold run of the new build. (iv) The workstation's TLS/TCP curves of section 33 (6). (v) The reaper race at the default 15 s timeout on a real workload: the rate scales with the chance that a call arrives in the few microseconds between the reaper's pass and a call's lookup, and was not measured there. (vi) Whether the reaper's `shutdown("idle")` of a mux taken out of the manager can still hit a request that is reserved later: it cannot reserve it (the `removed` flag), and no request can be on it, since `checkIdle` found it idle under the monitor; shown by the tests and the 0 failures, not by a proof for every endpoint. (vii) Mixed-subject SSL (5b).

**Reproduce.** `python3 concurrency-curve.py --out x --stacks DER --transports TCP,UDS,TLS --kinds platform --callers 64,512,4096 --warmup 3 --measure 6 [--prime 1]` with `--jar` pointing at a jar built before and one after commit "ConnectionManager.connect: constant time on the common path"; for the race, start `ServerMain <dir>` (with `-Dbench.tls.dir` for TLS) and run `java -Dorg.apache.river.jeri.connectionTimeout=2 [-Dbench.tls.dir=...] -cp target/serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.ReaperRaceProbe <dir> DER TCP 6000 3 12 2`. Tests: `mvn -o -pl jgdms-jeri test -Dtest='ConnectionManager*Test'`.

## Thirty-sixth section: the TLS accept path before and after S0 (handshake off the accept thread, total deadline, cap and queue, CPU permit, pinned groups, mux start deadline) (container, 4 cores, JDK 27 `27-internal`, server in its own JVM, TLS 1.3, ECDSA P-256, mutual authentication; client JVMs on the same cores; re-run on the workstation before quoting)

**The questions.** The S0 board (`docs/BOARD-REVIEW-S0-tls-accept-path-2026-10-20.md`) expected that moving the server handshake off the accept thread raises the accept rate by at least 1.3 times on 4 cores and scales with cores, that at 64 connectors there are no 1 to 9 s connects, and that silent and junk peers stop denying the listener; its transport seat (F8) warned that the removal of the implicit one-core limit on handshake CPU could raise the latency of calls on connections that already exist during a storm of new ones, and asked for that to be measured. "Before" is the build of commit `8be4a9adf` (the explicit backlog was already in: `ListenOverflows` is 0 in both builds, so the 50-entry queue overflow of section 34 is not part of the difference); "after" is the build of the S0 commits (`6393d4346`, `4751410fa`, `8d76e8134`, `d627f83de`, `6879a461d`). Same machine, same benchmark classes, a fresh server JVM for every point, 3 repetitions; the figures are medians with (minimum to maximum).

**Short answer.**
1. **Accept rate: confirmed for concurrent connectors, not for one.** Full handshakes: 83 to 98 a second with 1 connector (1.18 times), 104 to 212 with 64 (2.0 times), 148 to 389 with 512 (2.6 times). Resumed: 143 to 178 with 1 (1.24 times, below the board's 1.3), 212 to 350 with 64 (1.65 times), 312 to 547 with 512 (1.75 times, noisy). One connector cannot use more than the serial path's rate because it is itself a serial loop of connect, handshake and header; the gain needs concurrent clients, and here the clients take part of the same 4 cores, so the ratios are lower bounds on the server's. The scaling with cores was not measured (one machine).
2. **64 connectors: no 1 to 9 s connects in either build** (the maxima are 1.0 to 1.4 s before and 0.7 to 2.2 s after), because the explicit backlog was already in "before". At 512 connectors the connect-to-header latency falls from p50 3.6 s to 1.3 s (full) and from 1.5 s to 0.9 s (resumed). Overload shows a new failure mode: with 512 simultaneous first connections (all full handshakes) on 4 cores, one of three resumed runs lost 73 connections (and had a 12 s p99) because the 10 s total deadline from the accept cut off handshakes that were still queued for the CPU; the other five 512 runs of "after" and the six of "before" had no failures.
3. **Silent and junk peers: confirmed.** 12 junk connections then a legitimate open: 8.0 s before (the first open is delayed by the 10 s sleep of the throttle), at most 24 ms after. 12 junk every second while opening: 10 s for the open that meets the sleep before, at most 24 ms after. 1, 8 or 64 silent peers: every legitimate open fails (timeout 12 s) before, 20 to 25 ms after. 600 silent peers (above the cap of 512): the legitimate open waits 9.9 s, until the silent peers' deadline of 10 s frees slots, and then completes; that is the starvation at the cap that the board predicted (it limits resources, not the denial of service: a peer that holds every slot can still delay opens by T).
4. **Calls on established connections during a storm of new connections: refuted, S0 is worse.** At the same offered load (a paced storm of full handshakes, 8 client threads) the p99 of an open-loop `ping` (8 callers, 2 000 calls a second, DER over TLS) is 9.5 ms before and 102 ms after (median of three, 9 to 143 ms) at 30 connections a second, and 10.5 ms before and 773 ms after (446 to 818) at 60 a second; with an unpaced storm (the listener accepts as fast as it can: 65 a second before, 240 after) the p99 is 1.3 to 4.0 s before and 20 to 21 s after. Idle, the p99 is 2.2 to 2.4 ms before and 2.4 to 2.9 after. The cost scales with the number of handshakes running at once, not with the CPU permit: with `maxServerHandshakes=1` the p99 at 60 a second is back to 11 to 19 ms, with 2 it is 103 to 436 ms, `serverHandshakeCpuPermits=1` gives 289 to 387 ms, and `-Djdk.virtualThreadScheduler.parallelism=12` 255 to 394 ms. The cause was not found (a JFR recording showed no long monitor wait, pinning event or GC pause; thread dumps show the dispatch virtual threads waiting in `IOFuture.waitUntilDone` and `MuxInputStream.read` with the handshake threads running P-256 and X25519 arithmetic). Reading: the old serial accept thread was a limit that protected the dispatch of established calls, and the CPU permit, which covers the first flight only, does not replace it. (Section 37 re-measured this: most of the 30 and 60 a second figures were the first seconds of a cold server and a cold storm client, the 20 s one included clients that took the processors the callers need, and what remains is the handshake's tasks occupying the carriers of the virtual-thread scheduler.)
5. **Memory and threads (server JVM, peak over a run).** Operating-system threads 39 before and 51 to 59 after with 64 connectors, 51 to 122 with 512 (the carriers, the mux threads of the connections that completed, and the timer); resident memory 160 to 230 MB before and 245 to 410 MB (64), 760 to 1 040 MB (512) after, that is the 512 working handshakes (about 60 KiB each in buffers, more with the engine and the virtual thread's stack) plus the allocation rate; heap in use after the run is 5 to 6 MB in both. A stalled burst of 2 000 connections (unit test) held 512 working and 1 488 waiting with no platform thread added, one descriptor per connection on the server and about 45 MiB of heap.
6. **CPU of a replayed ClientHello (unit measurement, server CPU per replay, one captured ClientHello that offers only the group named, 600 replays, the server's first flight each time):** not pinned, x25519 2.3 ms, X25519MLKEM768 2.0, secp256r1 3.1, secp384r1 6.2, secp521r1 9.7, ffdhe2048 2.5, ffdhe8192 27.1; pinned to `X25519MLKEM768, x25519, secp256r1`: 1.7, 1.7, 2.8 and 0.3 (refused) for the others. A replay flood (6 threads, 6 s) against a listener with one CPU permit used 1.17 cores of server CPU (700 replays a second) against 2.27 cores (1 760 a second) without the permit; a legitimate client opened in 60 to 75 ms meanwhile.

**Method.** New tools: `tls-s0.py` (drives the three experiments, a fresh `ServerMain` per point, samples the server's threads and resident memory from `/proc` every 250 ms, heap in use from `jcmd GC.heap_info` after a `GC.run`, kernel `ListenOverflows`), `TlsAdversary` (K silent peers held open, J junk connections, then M legitimate opens timed from connect to mux header, as `TlsAcceptRate`), `TlsStorm` (the `TlsAcceptRate` loop paced to a fixed number of new connections a second, so a faster listener is not loaded harder) and, for the storm, the existing `ConcurrencyCurve` (open loop, `rate=2000`, 8 platform callers, 5 s warm-up and 20 s measured, 40 000 calls, the storm starting 3 s into the warm-up and covering the window). The accept points are `TlsAcceptRate` with 10 s measured after a warm-up of 5 s (1 and 64 connectors) or 15 s (512; with 5 s the 512 client threads were still building their `SSLContext`s and two runs measured nothing). "Before" and "after" are two shaded jars built from the same benchmark sources (the "before" modules from a worktree of `8be4a9adf`); `ListenOverflows` is for the whole machine. The replay measurements are from a test class run once, not through these tools.

**Accept rate, p50 / p99 / max connect-to-header (ms), server CPU (cores) and peak server threads.**

| connectors | mode | build | per second | p50 | p99 | max | server cores | threads | RSS MB |
|---:|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | resumed | before | 143 (139 to 154) | 6.0 | 14.6 | 28 | 0.95 | 39 | 177 |
| 1 | resumed | after | 178 (170 to 185) | 4.9 | 12.1 | 20 | 0.83 | 39 | 180 |
| 1 | full | before | 83 (82 to 85) | 10.8 | 23.6 | 39 | 0.94 | 39 | 161 |
| 1 | full | after | 98 (97 to 103) | 9.3 | 18.1 | 29 | 0.89 | 39 | 179 |
| 64 | resumed | before | 212 (191 to 213) | 272 | 1 097 | 1 400 | 0.87 | 41 | 200 |
| 64 | resumed | after | 350 (322 to 351) | 162 | 566 | 1 388 | 1.03 | 59 | 385 |
| 64 | full | before | 104 (98 to 108) | 595 | 1 004 | 1 010 | 0.85 | 39 | 185 |
| 64 | full | after | 212 (205 to 227) | 281 | 775 | 1 411 | 1.00 | 52 | 246 |
| 512 | resumed | before | 312 (300 to 343) | 1 544 | 3 297 | 3 504 | 0.76 | 40 | 225 |
| 512 | resumed | after | 547 (316 to 606) | 929 | 1 355 | 1 633 | 1.02 | 63 | 1 024 |
| 512 | full | before | 148 (142 to 161) | 3 630 | 4 140 | 4 171 | 0.83 | 39 | 184 |
| 512 | full | after | 389 (363 to 397) | 1 305 | 2 092 | 2 401 | 1.30 | 57 | 1 027 |

(Failures: none, except the 73 above in one 512 resumed run of "after". `ListenOverflows`: 0 everywhere. Server cores are from `schedstat` over warm-up plus run and include the idle part of the warm-up.)

**A legitimate open while peers misbehave (`TlsAdversary`, ms from connect to mux header; 3 repetitions; "all failed" is a 12 s client timeout).**

| silent | junk | opens | before p50 / max | after p50 / max |
|---:|---|---:|---|---|
| 0 | 0 | 20 | 17 / 24 | 19 / 31 |
| 0 | 12 once | 20 | 15 / **8 012** | 18 / 24 |
| 0 | 12 every second | 10 | 19 / **10 018** | 20 / 24 |
| 1 | 0 | 2 | **all failed** | 22 / 25 |
| 8 | 0 | 2 | **all failed** | 19 / 22 |
| 64 | 0 | 2 | **all failed** | 25 / 26 |
| 64 | 12 every second | 10 | **all failed** | 19 / 25 |
| 600 | 0 | 3 | **all failed** | 18 / **9 932** (first open waits for the silent peers' deadline) |

**Calls on a connection that already exists, during a storm of new full handshakes (`ConcurrencyCurve` ping, open loop 2 000 a second, 8 callers; microseconds, 3 repetitions).**

| storm | build | p50 | p99 | p99.9 | max |
|---|---|---:|---:|---:|---:|
| none | before | 417 to 440 | 2 228 to 2 396 | 5 329 to 7 107 | 9 476 to 17 543 |
| none | after | 416 to 457 | 2 415 to 2 947 | 4 936 to 20 873 | 9 619 to 34 850 |
| 30 a second | before | 442 to 467 | 9 470 to 13 853 | 15 573 to 42 074 | 22 370 to 56 235 |
| 30 a second | after | 433 to 483 | 9 175 / 102 367 / 143 393 | 17 056 to 177 209 | 26 058 to 218 879 |
| 60 a second | before | 481 to 599 | 9 765 to 25 281 | 18 170 to 56 197 | 28 339 to 73 020 |
| 60 a second | after | 549 to 574 | 445 645 to 817 889 | 486 801 to 952 631 | 496 738 to 979 004 |
| as fast as accepted | before (60 to 72 a second) | 1 338 to 2 783 | 1 307 575 to 3 990 880 | 1 395 655 to 4 068 475 | 1 416 321 to 4 096 290 |
| as fast as accepted | after (226 to 252 a second) | 12 171 871 to 13 312 721 | 20 182 991 to 21 223 178 | 20 384 317 to 21 357 396 | 20 437 199 to 21 377 467 |

**Not established, and caveats.** (i) The cause of the call latency under a storm (point 4): the handshake count is the variable; the permit, the scheduler parallelism and the cap of 2 do not remove it. A follow-up could gate the client's second flight (certificate verification) as well, bound the number of handshakes that compute at once rather than that wait, or look at the virtual-thread scheduler. (ii) The scaling with cores, Windows (a different backlog, a reset on overflow, no `UnixOperatingSystemMXBean`), macOS and a real network. (iii) The clients run on the same four cores as the server, so every rate is a lower bound and every latency includes client CPU; run-to-run spread on this container is 10 to 30% (more at 512), so differences under about 1.2 times are not established. (iv) Memory is peak resident size, not live heap, and includes the allocation rate of 512 working handshakes. (v) 512 simultaneous full handshakes on 4 cores can exceed the 10 s total deadline (73 failures in one run); a deployment with slower CPUs or a larger burst should raise `serverHandshakeTimeout`. (vi) The adversarial figures for "before" at 1 or more silent peers are lower bounds (the open timed out at 12 s; the real wait is the 60 s read deadline per silent peer, one after another). (vii) Replay CPU and the permit were not measured through the benchmark module.

**Reproduce.** Build two shaded jars (`mvn -q -o -pl jgdms-platform,jgdms-der,jgdms-jeri install -DskipTests`, then `mvn -q -o -f jgdms-benchmarks/serialization/pom.xml package -DskipTests`, once at each commit; `git checkout jgdms-benchmarks/serialization/dependency-reduced-pom.xml` afterwards), then for each jar: `python3 tls-s0.py accept --cp <jar> --label before --threads 1,64 --reps 3 --out accept.jsonl` and `--threads 512 --warmup 15`; `python3 tls-s0.py adversary --cp <jar> --label before --reps 3`; `python3 tls-s0.py storm --cp <jar> --label before --reps 3` (unpaced), and with `--storm-only --storm-threads 8 --storm-rate 30|60` and `TlsStorm` on the class path (`--cp <jar>:<dir with TlsStorm.class>` when the jar predates it) for the paced storm. JVM options for every JVM: `--both=-D...`.

## Thirty-seventh section: why calls on established connections slow down during a storm of new TLS connections after S0, isolated from the rig, and the fix (container, 4 cores, JDK 27 `27-internal`, server in its own JVM, TLS 1.3, ECDSA P-256, mutual authentication, DER `ping` at 2 000 a second open loop from 8 platform callers; re-run on the workstation before quoting)

**The questions.** Section 36 found that S0 made the p99 of calls on a connection that already exists worse while new connections arrive (9.5 to 102 ms at 30 a second, 10.5 to 773 ms at 60, 1.3 to 4 s before and 20 s after for a storm as fast as accepted), followed the number of concurrent handshakes, and had no known cause. The container has 4 cores and the clients share them with the server. Questions: how much is the rig, what is the cause of the rest, is there a sound fix that keeps the accept rate of S0. "Before" is the build of `8be4a9adf`, "S0" or "inline" the build of `36e5ac6c0`, "compute" the build with `HandshakeCompute`. Every figure is the median of the repetitions with (minimum to maximum), the number of repetitions after it; a p99 is of 40 000 calls in a 20 s window. Closed-loop and open-loop figures are not comparable (see 7).

**Short answer.**
1. **The rig explains most of the regression at 30 and 60 connections a second, and all of it at 30.** (a) *A cold start.* Every point of section 36 started a fresh server JVM and a fresh storm JVM 3 s into the callers' warm-up: the server had not compiled the handshake path, and a cold storm client falls behind its schedule and then fires a burst. Re-run that way on the shared cores, before and S0 are both erratic (30 a second: before 5 411 ms (12 to 10 494), S0 214 ms (108 to 347), 3 each; 60 a second: before 564 ms (15 to 2 416), S0 549 ms (359 to 1 311)); section 36's 9.5 ms for "before" is not reproduced. With the server and the cores of the callers and the storm held apart (server on cores 0 and 1, every client on 2 and 3) and still cold: 30 a second before 18.6 ms (14.5 to 24.9), S0 306 ms (20 to 713); 60 a second before 986 ms (50 to 2 433), S0 491 ms (307 to 519). (b) *Warm.* With the server warmed by 20 s of the same storm first: 30 a second before 9.1 ms (7.1 to 10.7), S0 9.4 ms (7.5 to 15.5), 4 each, no difference; 60 a second before 11.1 (9.4 to 15.6), S0 56.6 (41.7 to 88.1), 4 each, with the storm's JVM still starting cold in the callers' warm-up; and with the storm started 20 s before the callers (`--lead`) before 6.7 ms (5.1 to 10.1), S0 8.0 (6.2 to 12.9), 5 each. On the shared four cores, warm, storm first: 30 a second before 8.6 ms (5.2 to 10.1), S0 7.5 (6.0 to 15.4); 60 a second before 4.5 (4.0 to 12.6), S0 11.4 (4.2 to 11.8), 3 each. So at these rates S0 has no steady-state regression; what is left is the burst of the first second of the window, when the storm's cold JVM catches up (60 a second, storm starting in the callers' warm-up, 5 runs each, timelines of the calls over 100 ms: S0 has 1 389, 2 073 and 288 of them in three runs and none in the other two, all in the first second of the window but 91 of the 288; before has none in five runs). (c) *Clients that take the cores.* S0 accepts 1.6 to 2.6 times faster, so a storm as fast as accepted takes all the cores the clients are allowed (8 connectors): with the callers on their own core the S0 p99 is 347 ms (79 to 865) against 8.4 ms (6.9 to 15.7) before; with the callers and the storm sharing two cores it is 11 s against 11 ms, and the best server-side fix below still gives 1.7 s there. The 20 s of section 36 is mostly this.
2. **What remains is real, and it is the carriers.** Server on two cores, callers on a core of their own, the storm on another, storm as fast as accepted (8 connectors), server and storm warm. The established-call p99 over 10 runs (three series of runs pooled; the table in 3 gives one of them): before 13.7 ms (6.9 to 284, 2 runs over 100 ms of 10), S0 624 ms (79 to 1 811, 9 of 10 over 100 ms), 150 and 237 new connections a second. The virtual-thread scheduler's carriers run the handshakes' delegated tasks (key exchange and signature of the first flight, certificate chain and signature of the second: 2 to 4 ms of arithmetic each, no blocking) and also the virtual threads that dispatch calls, so a dispatch thread that is made ready waits for a carrier. Measured in the server JVM: a virtual thread that sleeps 1 ms wakes late by a median of 0.2 to 0.7 ms and a p99 of 12 to 23 ms under S0 (and completes only 100 to 240 of its 800 wake-ups a second), against 0.13 ms and 2 ms before; a platform thread doing the same stays at 0.1 ms and 2.5 to 3 ms under S0 and 1.5 ms before (2 runs of each). Nothing stalls the process: the platform thread is on time. Three interventions each end the effect (table in 3): one handshake at a time, dispatch on platform threads, and the handshake's tasks on a platform thread.
3. **The fix: the handshake's delegated tasks run on a shared pool of platform threads** (`HandshakeCompute`, `org.apache.river.jeri.ssl.serverHandshakeComputeThreads`, default a quarter of the processors, at least 1; 0 is S0). The handshake's virtual thread waits and gives up its carrier. It keeps the accept rate of S0 and the p99 of before.

| setting (server on 2 cores, callers on 1, storm on 1; 8 connectors; 3 runs each) | established-call p99, ms | new connections a second |
|---|---:|---:|
| before | 8.4 (6.9 to 15.7) | 154 |
| S0 (tasks inline) | 347 (79 to 865) | 237 |
| `maxServerHandshakes=1` | 7.5 (6.3 to 12.7) | 173 |
| `maxServerHandshakes=2` | 565 (163 to 649) | 239 |
| all delegated tasks gated by 1 CPU permit (prototype) | 226 (181 to 1 519) | 240 |
| dispatch on platform threads (`org.apache.river.thread.platform=true`) | 8.9 (8.7 to 11.2) | 242 |
| **tasks on 1 platform thread** | **13.8 (12.8 to 16.7)** | **233** |

On the same geometry with callers and storm sharing cores 2 and 3 (so the callers starve whatever the server does): S0 12.8 s, `maxServerHandshakes=1` 11 ms (188 a second), 2: 1.5 s (293), the CPU permit set to 1 (first flight only) 8.9 s, gating all tasks with 2 permits 11 s and with 1 permit 3.1 s, dispatch on platform threads 0.9 s, tasks on 1 platform thread 1.7 s (353), on 2 threads 2.1 s (373). Pooled over the repetitions made with the committed settings on the two-core geometry: tasks on one platform thread 19.7 ms (12.8 to 764; 3 of 17 runs over 100 ms), 231 a second. On one server core (callers on a core, storm on two) nothing helps, the server is the bottleneck: before 2.6 s, S0 20.9 s, one compute thread 3.4 s (129, 210 and 164 a second). On the shared four cores (nothing pinned; warm; storm as fast as accepted; 8 connectors): before 18.3 ms (14.0 to 21.1), 175 a second; S0 4 285 ms (3 018 to 4 446), 355; compute threads 1: 16.7 ms (12.7 to 134.7), 332; 2: 777 ms (665 to 1 279), 351; 4: 1 383 ms (1 103 to 1 570), 347; 4 runs each for 1 and 2. **The pool must leave cores to the carriers: a quarter of the processors, not half.** (The first build had half, two threads on four cores: 624 ms (197 to 716), 3 runs.)
4. **The cost in accept rate is small.** `TlsAcceptRate`, shared four cores, clients unpinned, 3 runs, new connections a second:

| connectors, mode | before | S0 (inline) | compute 1 thread | compute 2 threads |
|---|---:|---:|---:|---:|
| 64, full | 146 (133 to 150) | 257 (248 to 293) | 237 (228 to 294) | 294 (275 to 297) |
| 64, resumed | 244 (227 to 261) | 391 (384 to 456) | 370 (337 to 429) | 395 (376 to 419) |
| 512, full | 197 (188 to 199) | 399 (288 to 464) | 380 (353 to 387) | 394 (337 to 431) |
| 512, resumed | 200 (145 to 330) | 541 (476 to 582) | 561 (533 to 636) | 558 (13 to 635) |

One thread keeps 0.92 to 1.04 of S0's rate and 1.5 to 2.8 times the rate of before. (The 13 a second is a run of the 512-connector resumed point that collapsed to a few connections a second; it happened once in 12 repeats with the pool and once in 14 with S0's settings. The server was idle then, with 44 handshakes at work and idle compute threads, so the client's 512 threads were not connecting; a rig effect that was not chased further.) Storm accept rates in 3 are with the callers running.
5. **Closed loop, the open-loop amplifier.** 8 callers, each call after the last, same geometry, storm as fast as accepted, 3 runs: p99 before 5.2 ms (4.6 to 5.5), S0 8.2 (7.3 to 8.5), compute 1 thread 6.2 (5.9 to 6.4); calls a millisecond 10.4, 6.9, 8.0. The seconds in the open-loop figures are a queue: once a call takes longer than the arrival period the open loop piles up. S0 costs 1.6 times in p99 and 0.66 in throughput closed loop; the fix gets back most of it.
6. **Unauthenticated load.** Calls on a connection made before the flood starts, flood of 8 threads paced at a rate, server warmed by 15 s of the same flood first, p99 in ms of each of 2 runs (4 for replay 60 with timelines; replay 30 is from an earlier series that started the flood before the callers, which is harmless for replays): silent connections (nothing sent, held until the server closes them) 60 a second: S0 13.0, 95.2, compute 9.5, 10.6; 300 a second: S0 606, 11.9, compute 263, 12.0. Replayed ClientHello (key exchange and signature, then the flood closes) 60 a second: S0 513, 6.9, compute 223, 117 (4-run set: S0 75.6, 14.2, 67.8, 389; compute 38.8, 321, 86.1, 471); 300 a second: S0 483, 29.4, compute 73.5, 10.1; 30 a second, S0 15.6, 60.3, compute 9.0, 7.8. Before: no call completed with silent peers at 60 a second (the callers never got a connection: the accept thread was held), 59 ms in the one replay-300 run that connected. The slow calls are in the first 2.5 s of the window (flood start) in every run; after that the 4-run replay-60 sets have 0 calls over 100 ms of 35 000 for S0 and 0, 72, 0 and 35 for compute. **Neither flood reproduces a systematic regression at 30 or 60 a second, so it is not a pre-authentication amplification of the S0 kind; the bimodal figures are the rig's start-up.** A legitimate open while 60 silent connections a second arrive (600 held after 10 s, more than the 512 handshakes at work) waits for a permit: callers that opened their connection after the flood had started saw a p99 of 2.5 s (S0) and 1.2 to 1.5 s (compute) at 60 silent a second, and a p50 of 8.3 s (S0) and 1.8 s (compute) at 300; this is the cap behaviour of section 36 (point 3), not changed.
7. **Rejected hypotheses, each with the measurement.** *Carrier monopolisation* is the cause (point 2). *The second flight is ungated (b):* it is real, about 38% of the server's sampled Java CPU in handshake tasks (first flight 50%, set-up of the engine and sockets 7%, `handleConnection` 4%; JFR, 2 ms samples, 5 932 samples, full handshakes, storm as fast as accepted), but gating all delegated tasks, the second flight included, with one permit still gives 226 ms (3 above): the permit bounds how many compute at once, not that they occupy carriers. *More carriers:* `jdk.virtualThreadScheduler.parallelism=maxPoolSize=64` at 60 a second was not better (207 ms (15.5 to 218) against 17.6 (14.4 to 258) for the default, 3 each). *GC and allocation (c):* in 3 runs of S0 and 2 of before (G1, default heap, 60 a second) the pauses totalled 0.32 to 0.41 s a run with a longest of 6 to 16 ms, the same in the runs with a bad p99 and without. Peak resident memory is garbage, not retained memory: 512 connectors, full handshakes, `-Xmx128m`: 383 a second, no failures, 282 MB resident, 5 MB live after a collection (S0 settings: 419, 300 MB); at `-Xmx1g` 529 MB (S0 settings 501 MB). *A shared lock (d):* the platform timer thread of the server JVM is not delayed, and moving the same code to platform threads removes the effect, so no lock on the call path is involved (JFR in section 36 saw none). *The accept loop, watchdog and admission semaphore (e):* silent peers cost nothing but the cap (6), and the watchdog is not on the idle path: idle (no storm) p99.9 over 5 runs is before 24.7 ms (7.7 to 45.7), S0 28.8 (5.7 to 60.6), compute 23.0 (12.6 to 39.9), p99 4.4, 4.6 and 3.4 ms; section 36's idle difference is not reproduced. *Two platform threads started for each new connection:* `handleConnection` is 4% of the handshake CPU, and the platform timer is unaffected. *The kernel (f):* no `perf` here; a platform thread's wake-up delay is unaffected, which leaves no room for a kernel delay of the size seen, but softirq time was not measured.
8. **A client that offers `secp521r1` in its key share and `x25519` in supported_groups** gets a HelloRetryRequest and completes (S0 and compute): `secp521r1,x25519` ok, `secp521r1,secp256r1` ok, `ffdhe8192,x25519` ok; `secp521r1` alone and `secp384r1,secp521r1` are refused ("Remote host terminated the handshake").
9. **Two defects found on the way, fixed in their own commits.** (i) `Mux.start` scheduled the server's start timer after `connectionIO.start()`; a header processed or a connection taken down before that found no timer to cancel and the timer kept the Mux and its buffers for 15 s. Measured on the TLS server: 8 to 10 timers left after bursts at 270 and 435 connections a second (about 0.1 to 0.2% of the connections; the 8 000 retained connections that were feared at 550 a second are not what happens), 0 after the change; in the unit test, 1 500 starts with every processor busy leave 74 timers before and 0 after. (ii) `SslServerConnection.closeInternal` wrote `close_notify` and only then removed the connection from the listener's set; when the peer had gone the write threw (Broken pipe) and the connection stayed in the set, with its engine and buffers, until the listener closed (before S0 as well). A peer that resets after the handshake does it; the set now empties in a `finally`. The test resets 20 connections and fails within a few rounds without the `finally`.

**Method.** `tls-s0.py storm` gained `--server-cpus`, `--client-cpus`, `--caller-cpus`, `--storm-cpus` (taskset), `--server-opt` (server JVM only), `--server-main` (a wrapper class), `--prewarm N` (the storm alone first), `--lead N` (the storm's JVM starts N seconds before the callers', so its start-up and the server's JIT are over), `--storm-class TlsFlood` (new: ClientHello replays or silent connections at a fixed rate), `--idle-only`; `ConcurrencyCurve` can write the slow calls with their wall-clock time (`-Dcurve.timeline`). The server-side probe (a wrapper main in the scratch directory, not committed) ran a virtual and a platform thread that sleep 1 ms and recorded how late they wake, and read each thread group's CPU and run-queue delay from `/proc/<pid>/task/*/schedstat` every second. Runs were interleaved and repeated 3 to 5 times; each point started a fresh server JVM.

**Not established, and caveats.** (i) One machine, 4 virtual cores of a shared host; the spread between runs is large and bimodal at 60 a second (a few runs in ten have a p99 of hundreds of milliseconds in every build, before included), so differences under about 2 times at the paced rates are not established. (ii) The mechanism is shown by interventions and by the scheduling-delay probe, not by reading the scheduler's queues; the claim that local queues are drained before external submissions is from the JDK source and is not separately tested. (iii) The size of the pool: a quarter of the processors is supported by 1 against 2 and 4 threads on four cores and by two-core and one-core geometries; on a machine with many cores, the quarter was not run. (iv) The client (and a client that is a virtual-thread caller) still runs its handshake on the caller's thread; the same carrier effect on a client with many virtual-thread callers was not measured. (v) The open-loop callers make queueing visible as seconds; 5 shows what the closed loop sees. (vi) The first seconds after a burst of new connections, cold, remain visible with the pool: cold, 60 a second, one compute thread 626 ms (545 to 707), before 83 (17 to 149), S0 886 (747 to 1 026), 2 each; cold storm as fast as accepted: 368 ms (227 to 510), 264 (169 to 359), 7 044 (6 187 to 7 902). (vii) Windows and macOS were not run.

**Reproduce.** `python3 tls-s0.py storm --cp <jar> --label x --storm-only --storm-threads 8 --storm-rate 0 --lead 15 --server-cpus 0,1 --caller-cpus 2 --storm-cpus 3 [--server-opt=-Dorg.apache.river.jeri.ssl.serverHandshakeComputeThreads=0]` for the isolated points (`--storm-rate 30|60` for the paced ones, `--rate 0` for closed loop, `--storm-class TlsFlood --storm-mode replay|silent --prewarm 15` for the floods); `tls-s0.py accept` as in section 36. The "before" and "S0" jars are those of section 36; the compute jar is the build of the commit that adds `HandshakeCompute`.

## Thirty-eighth section: the TLS server accept path after the S0 follow-up (budgets shared by the JVM, first-record read, minimum budget, timer off blocking work, SSLSocket permits) (container, 4 cores, JDK 27 `27-internal`, server in its own JVM, TLS 1.3, ECDSA P-256, mutual authentication; client JVMs on the same cores unless pinned; re-run on the workstation before quoting)

**The questions.** The follow-up package changed the accept path in five ways (commits `03893dcf9`, `e002ed60c`, `752d6bfe3`, `dcb5eb823`, `9f563a353`): one set of budgets for every listener of the JVM; the scheduler's tasks only decide, and closes go to virtual threads; the SSLSocket path (HTTPS, a custom `ServerSocketFactory`) holds a permit for its whole handshake; a connection that waits for its turn reads its first TLS record, so only a peer that has sent a ClientHello takes one of the places to work; and a turn is given only if `serverHandshakeMinBudget` (2 s) of the 10 s is left. Questions: (1) is the accept rate unchanged; (2) do silent peers at 52 to 60 connections a second, and 600 held, stop starving a legitimate open; (3) what does a replay flood cost on the engine and the SSLSocket path; (4) is the p99 of calls on established connections during a warm storm unchanged. "Before" is the build of `6c827ad83` (S0 with `HandshakeCompute`, `fb7eb93ce`, and the two fixes `996d27c12`, `917c5f349`), "after" the build of `9f563a353`; the later commits do not touch the TLS path. Two shaded jars from the same benchmark sources, runs interleaved before/after, a fresh server JVM for every point; figures are medians with (minimum to maximum), 3 repetitions unless stated.

**Short answer.**
1. **Accept rate: not changed beyond the spread.** Full / resumed, per second: 1 connector 114 (107 to 120) / 179 (164 to 187) before, 109 (107 to 112) / 174 (163 to 185) after; 64 connectors 262 (219 to 266) / 302 (299 to 403) before, 223 (222 to 248) / 313 (309 to 315) after; 512 connectors 242 (208 to 284) / 348 (277 to 352) before, 307 (268 to 374) / 354 (277 to 456) after. The one median that is lower after is 64 full (15% below); the ranges overlap (the spread of "before" alone is 21%), so it is not established. No failures, `ListenOverflows` 0 in all 36 runs. Peak resident memory at 512 connectors is 947 MB (full) and 1 465 MB (resumed) after against 1 166 and 1 647 MB before, peak threads 49 to 55 against 53 (the memory is garbage, section 37 point 7).
2. **Silent peers: the starvation is gone.** `TlsFlood silent` at a fixed rate for 15 s (above the 512 places to work, which a silent peer held for the 10 s deadline before), then a closed loop of full opens for 20 s (`TlsAcceptRate`, 1 connector): at 52 a second 106 (104 to 107) opens, p50 180 ms, max 350 ms before, against 2 058 (1 977 to 2 183) opens, p50 7.6 ms, max 206 ms after; at 60 a second **12 opens in 20 s, p50 1.5 s, max 1.7 s** before, against 1 837 (1 837 to 2 031) opens, p50 8.5 ms, max 212 ms after (no failures in either: the opens wait in the queue until the silent peers' deadline frees a place). 600 silent peers held, then 3 opens: the first open waits **9.9 s** before (until the silent peers' deadline), 43 to 67 ms after (the whole point 1.6 to 1.9 s against 11.4 to 11.8 s).
3. **Replay flood.** Engine path, `TlsFlood replay` (6 threads, as fast as the shared four cores allow, `x25519`), server CPU over 6 s with a legitimate full open loop meanwhile: 1.67 cores (1.60 to 1.81) before, 1.72 (1.61 to 2.10) after; about 1 000 flights a second in both (the client JVM takes the other cores); the legitimate open p50 24 ms before and 28.8 ms after (max 224 to 333 and 200 to 435 ms), no failures. SSLSocket path (a listener with a `ServerSocketFactory`; unit-test flood of 6 threads against the default 2 permits, carrier and compute-thread CPU, 3 runs): **1.87, 2.02, 2.03 cores with the permit, 2.66, 3.28, 3.30 without it** (1 520 to 1 960 flights a second against 2 620 to 3 810), the engine path 1.49 to 1.63 in the same runs; a legitimate open took 48 to 67 ms in all.
4. **Calls on established connections in a warm storm: not worse than before in this session, but this session does not reproduce section 37's quiet figures for either build.** Isolated geometry of section 37 (server on cores 0 and 1, callers on 2, storm on 3; storm JVM started 15 s before the callers; 8 connectors; open loop 2 000 a second, 8 platform callers), p99 in ms: storm as fast as accepted 803 (35 to 1 122) before and 570 (13 to 1 113) after, 7 runs each, 6 of 7 over 100 ms in both (section 37 had 13.8 (12.8 to 16.7) for the same configuration, three runs); paced 60 a second 28 (13 to 1 779) before, 9.4 (9.0 to 23) after; paced 30 a second 97 (31 to 167) before, 13.7 (12.5 to 522) after; closed loop (`rate=0`) 6.4 (6.4 to 6.5) before, 6.2 (5.9 to 7.4) after. Accept rates in the storm: 204 / 206 a second as fast as accepted, 57 / 57 at 60, 28 / 29 at 30, 201 / 214 closed loop. The bimodal p99 at the open-loop rates is present in the build that carries only the follow-up's predecessor, so it is not something the follow-up added; its size here (hundreds of milliseconds where section 37 saw 14) means the host was noisier or the rig differently placed than for section 37, and that was not chased. The closed-loop figures, which do not amplify, agree with section 37 point 5 and are the same before and after.

**Method.** Accept: `tls-s0.py accept --threads 1,64` and `--threads 512 --warmup 15`, `--reps 1` in three interleaved rounds. Silent peers and replay: two small driver scripts in the scratch directory, not committed, using `Server`, `client` and `cpu_ns` of `tls-s0.py`: `TlsFlood silent 8 50 <rate>` started, 15 s later `TlsAcceptRate <dir> 1 20 full 0`; `TlsAdversary silent=600 opens=3 timeout=12000`; `TlsFlood replay` for a warm-up of 10 s, then for 14 s with `TlsAcceptRate 1 6 full 0` from 3 s in and the server's `schedstat` CPU read around it. The SSLSocket flood is the `SslServerHandshakeCpuTest.Flood` class run in a scratch test (not committed) against `S0Fixture.listenWithSocketFactory()` and `listen()`, alternating, three of each, once as built and once with the `beginSocket` call removed; the CPU is that of the `ForkJoinPool` carriers and the `TLS handshake compute` threads. Storm: `tls-s0.py storm --storm-only --storm-threads 8 --lead 15 --server-cpus 0,1 --caller-cpus 2 --storm-cpus 3 --storm-rate 0|60|30` (and `--rate 0` for the closed loop), three runs of each from the first round and four more of "as fast as accepted".

**Not established, and caveats.** (i) One machine, shared host, 4 virtual cores; clients share the cores, so rates are lower bounds. (ii) The p99 under a storm is bimodal on this rig for both builds, so a difference of less than about 2 times is not established. (iii) The HTTPS listener itself was not measured end to end, only the SSLSocket path through a server socket factory (the two share the code). (iv) Peers that send a ClientHello and then stall still take a place to work, and on the SSLSocket path any silent peer does; the minimum budget bounds how long, not whether. (v) A peer whose first record is complete and which waits for a place to work is not noticed if it leaves until it has its turn. (vi) Windows, macOS, a real network and machines with many cores were not run.

## Thirty-ninth section: the default for the handshakes at once (`maxServerHandshakes`) follows the processors (container, 4 cores, JDK 27 `27-internal`, server in its own JVM, TLS 1.3, ECDSA P-256, mutual authentication; client JVMs on the same cores; the Windows figures are the maintainer's and were not re-run)

**The question.** The default cap on the handshakes working at once was `min(512, maxFds/8)`. On the maintainer's Windows 10 machine (6 cores, 12 logical processors) `TlsAcceptRate` with 4096 connectors, full handshakes and the default 10 s timeout gave: cap 512, 314 and 620 connections a second with 809 and 70 failures and a p99 of 12 s; cap 96, 414 a second with 298 failures and a p99 of 10.3 s; cap 48, 658 and 517 a second with no failures and a p99 of 8.0 to 8.2 s (maximum 8.3 s). The server logs at cap 512 showed windows with no completion while hundreds of handshakes were in progress: the processor was shared among too many handshakes (processor sharing), each finished late, and at the deadline many were cut off after doing their work. A handshake on a local network is bound by the processor, so the cap belongs near a small multiple of the processors. The default is now `min(512, maxFds/8, max(16, 4 x availableProcessors))`: 48 on 12 logical processors, 16 on this 4-core container. The property still overrides it. On a link with a long round trip a handshake mostly waits for the peer, and the rate of new connections is about the cap divided by the time a handshake takes (48 at 300 ms round trip: roughly 80 a second), so such deployments should raise it; the Javadoc says so.

**Container check, accept rate not worse beyond the spread.** `tls-s0.py accept --modes full --reps 1 --seconds 10`, `--warmup 5` (64 connectors) and `--warmup 15` (512), three interleaved rounds, build of `7bf6cf91b` ("before", cap 512 at both sizes) against the change ("after", cap 16). Per second, median (range): 64 connectors 198 (168 to 224) before, 196 (185 to 199) after; 512 connectors 274 (188 to 316) before, 259 (181 to 309) after. The ranges overlap and the spread of "before" alone is 40% at 512, so no difference is established in either direction. p50 at 64: 301 ms (274 to 333) and 293 ms (291 to 315); p99 at 64: 781 ms (557 to 842) and 821 ms (776 to 857); p99 at 512: 2.50 s (2.04 to 4.11) and 2.87 s (2.50 to 4.63). Failures: one in the three "before" runs at 512, none otherwise; `ListenOverflows` 0 in all 12. Peak resident memory at 512 connectors: 1 000 MB (651 to 1 002) before, 307 MB (251 to 441) after (fewer handshakes hold their 60 KiB and their buffers at once); peak threads 48 to 63 against 51 to 55; server CPU 0.8 to 1.5 cores in both.

**Not established.** The 4-core container shows only that lowering the cap from 512 to 16 does not cost accept rate when the clients share the cores; the failures and the p99 that the cap of 512 had on the 12-logical-processor machine at 4096 connectors were not reproduced here (512 connectors do not overload 4 cores in 10 s), so that gain rests on the maintainer's measurement. Windows, macOS, a long round trip, and cap values between 16 and 48 on the container were not run.


## Fortieth section: the TLS server accept path after the second review of the follow-up (whole ClientHello in the queue phase, bound on the answer to the server's flight, TLS 1.3 only and HelloRetryRequest on the SSLSocket path) (container, 4 cores, JDK 27 `27-internal`, server in its own JVM, TLS 1.3, ECDSA P-256, mutual authentication; client JVMs on the same cores unless pinned; Windows, macOS and a real network were not run)

**The questions.** The second cold review of the follow-up (`docs/BOARD-REVIEW-S0-tls-accept-path-2026-10-20.md`, addendum item 9) found that a peer which sends six bytes of a ClientHello and stops was still given a place to work (both paths) and a permit to compute (SSLSocket path) until the deadline, that with the default cap following the processors (16 on this machine) a peer which replays a captured ClientHello, reads the flight and stops keeps every place taken at N / T = 1.6 connections a second, that a HelloRetryRequest and TLS 1.2 took the SSLSocket path's work out of its permit, and that a ClientHello in records of one byte costs the JDK 190 ms of processor time. The four stages: the queue phase reads the whole ClientHello (`31e07c84f`), the peer has `serverHandshakeClientFlightTimeout` (3 s) to answer each flight (`6d9f4489f`), TLS 1.3 only and a permit again after a HelloRetryRequest on the SSLSocket path (`1e60099cc`), and the small items (`0060fc266`). Questions: (1) is the accept rate with full handshakes unchanged; (2) do fragment peers and silent peers at the default cap leave a legitimate open alone; (3) how long does a peer that replays and stalls hold its place; (4) are calls on established connections in a warm storm unchanged. "Before" is the build of `e70b57aeb` (the head at the start of this work), "after" the build of `0060fc266`; two shaded jars from the same benchmark sources (`TlsFlood` has two new modes, `fragment` and `stall`, used for both), runs interleaved before / after, a fresh server JVM for every point; figures are medians with (minimum to maximum). The defaults throughout: cap N = 16 (4 processors), T = 10 s, minimum budget 2 s, X = 3 s.

**Short answer.**
1. **Accept rate, full handshakes: not changed beyond the spread.** 64 connectors: 236 (188 to 259) a second before, 233 (194 to 257) after, nine runs each (the first three of each were 233 (188 to 236) and 206 (194 to 215); six more interleaved runs were added to see whether that 12% was real: it was not); p50 247 and 244 ms, p99 526 and 569 ms. 512 connectors, three runs each: 312 (203 to 326) before, 329 (322 to 330) after; p99 2.32 s (2.08 to 8.88) and 2.16 s (1.83 to 2.39); one of the three "before" runs collapsed (203 a second, 12 failures, p99 8.9 s, 0.71 cores), none of "after". No failures otherwise, `ListenOverflows` 0 in all 24 runs. Peak server threads 48 to 49 (64 connectors) in both, 50 before and 54 after at 512; peak resident memory 307 MB before and 264 MB after at 512.
2. **Fragment peers: a legitimate open is no longer affected, where before the listener stopped.** `TlsFlood fragment` (six bytes `16 03 01 00 01 01` on each new connection, then held), started 12 s before a closed loop of full opens by one connector (15 s measured after 4 s of warm-up), three interleaved rounds. At 5 connections a second (3 times the 1.6 a second that fills 16 places held for 10 s): before 2 opens in 15 s in every round, the first of them after 7.0 s (0 opens a second, p50 7.0 s); after 102 (96 to 104) opens a second, p50 8.7 ms (8.7 to 9.6), no failures; without a flood 99 (93 to 105) before and 101 (100 to 104) after, p50 9.1 and 8.8 ms. At 50 a second: before 0, 0 and 1 opens in 15 s with 2, 2 and 1 failures, after 107 (91 to 107) a second. The time the server held the fragment connections: median 10.0 s (until the deadline, a working place each) before, 8.0 s (the queue phase, T less the minimum budget: a queue place each, no working place) after.
3. **Silent peers (60 a second, the rate of section 38): unchanged and fine in both.** 108, 114, 119 opens a second before and 118, 120, 121 after, p50 7.6 to 8.4 ms and 7.3 to 7.6 ms; the connections were held 8.0 s in both.
4. **A peer that replays a ClientHello, reads the flight and stops (`TlsFlood stall`) holds its place for X and not for T.** The time until the server closed the connection: median 10.0 s (10.001 to 10.014) before, 3.0 s (3.006; maximum 3.1 s) after. At 3 connections a second (N / T = 1.6 a second is exceeded, N / X = 5.3 a second is not): before 3 opens in 15 s in every round, the first after 4.9 to 5.0 s, after 104 (95 to 109) opens a second, p50 8.7 ms (8.3 to 9.6). At 8 a second (above N / X): both builds are stopped, 2 opens in 15 s with the first after 8.1 s before and 7.9 s after (so the cost of the attack at these defaults is between 3 and 8 connections a second, as the documents say: about 5). The docs' arithmetic, N / X, is confirmed; the attack is not removed, it is 3 times dearer.
5. **Calls on established connections in a warm storm: not different beyond the noise of this rig, which is bimodal for both builds.** Isolated geometry of section 37 (server on cores 0 and 1, callers on core 2, storm on core 3, the storm's JVM started 15 s before the callers, 8 connectors of full handshakes, open loop 2 000 calls a second from 8 platform callers, 20 s measured). Storm as fast as accepted (three runs each): p99 10.5, 9.2, 10.0 ms before and 10.9, 10.5, 19.4 ms after, 184 to 194 new connections a second in both. Storm paced at 60 a second, 15 runs each: p99 median 6.2 ms (5.0 to 62.8) before, 6.9 ms (5.5 to 118.2) after; over 30 ms in 1 of 15 runs before and 3 of 15 after; p99.9 median 12.2 and 22.2 ms. A difference of 0.7 ms at the median and 2 more runs in 15 with a tail over 30 ms is not established (the bimodal p99 at open-loop rates is in both builds, as in sections 37 and 38); it was not chased.
6. **What the tests found.** The negative tests of stages 1 to 3 fail when the old behaviour is put back (by a mutant, or the old code) with the review's numbers (socket path: `permits` fragment peers hold both permits, 2 free expected, 0 free; engine path: 2 working places held, 0 expected; the flight after a HelloRetryRequest written with 1 permit free, 0 expected; a TLS 1.2 client served), and each stage's mutants (read one record only, no cap on the records, bytes not handed on, the bound off, on each path alone, the HelloRetryRequest not recognised, TLS 1.3 not pinned) are killed. The engine path's gate was already entered again for the second ClientHello after a HelloRetryRequest (the test passes against the unchanged channel); only the SSLSocket path was wrong. A client that lists `secp384r1` and then `x25519` is not sent a HelloRetryRequest (the JDK sends a key share for `x25519` as well); one that lists `secp384r1` and then `secp256r1` is. Section 37's point 8 says that `secp521r1,x25519` got one; that pair was not re-checked here.

**Method.** `TlsFlood` modes `fragment` (six bytes, then held) and `stall` (the captured ClientHello, the flight read, then held), each connection on a virtual thread that also notes when the server closes it; the legitimate opens are `TlsAcceptRate <dir> 1 15 full 4` (one connector, full handshakes) started 12 s after the flood (drivers in the scratch directory, not committed, using `Server`, `client` and `PKG` of `tls-s0.py`; one per scenario and build, a fresh server JVM each). Accept: `tls-s0.py accept --threads 64 --modes full --reps 1 --seconds 10 --warmup 5` and `--threads 512 --warmup 15`. Storm: `tls-s0.py storm --storm-only --storm-threads 8 --lead 15 --server-cpus 0,1 --caller-cpus 2 --storm-cpus 3 --storm-rate 0|60`, one run for each build in turn, the order alternated between series.

**Not established, and caveats.** (i) One machine, shared host, 4 virtual cores; the client JVMs share them, so rates are lower bounds and the spread is 10 to 30%. (ii) The default X = 3 s, with the benchmark clients on the same four cores as the server, cut off no legitimate client in any run (no failures at 512 connectors, whose p50 is 1.6 s and p99 2.2 s from connect to mux header). A real deployment's clients must answer within the round trip plus their own computation; the Javadoc says so; the maintainer's Windows machine at 4096 connectors was not run. (iii) The cost of a stalled peer at the defaults is about N / X = 5 connections a second at N = 16 and 16 at N = 48; the 8 a second point is above the first and was not run against a cap of 48. (iv) The HelloRetryRequest with a real attacker's spliced second ClientHello (cookie copied, no cryptography) and a TLS 1.2 replay were reasoned, not run. (v) The slow-link table (rate about N divided by one to two round trips) is computed, not measured. (vi) Windows (the timing tests were written to be portable and use no Linux-only facility, but were run here only), macOS, a real OSGi framework, polpAudit-generated policies (the one new property read is one more `PropertyPermission`) and a link with a long round trip were not run.

**Reproduce.** Build the two jars as in section 36 (the "before" one from `e70b57aeb` with this section's `TlsFlood.java` copied in), then for each build: `python3 tls-s0.py accept --cp <jar> --label <name> --threads 64 --modes full --reps 1 --seconds 10 --warmup 5` (512 with `--warmup 15`), `python3 tls-s0.py storm --cp <jar> --label <name> --reps 1 --storm-only --storm-threads 8 --lead 15 --server-cpus 0,1 --caller-cpus 2 --storm-cpus 3 --storm-rate 60`; the floods: start `Server`, then `java -Dbench.tls.dir=<server>/tls -cp <jar> au.net.zeus.jgdms.bench.serial.TlsFlood <serverdir> 2 <seconds> fragment|stall|silent <rate>` and, 12 s later, `TlsAcceptRate <serverdir> 1 15 full 4`.
