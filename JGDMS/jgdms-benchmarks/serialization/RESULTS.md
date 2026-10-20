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
