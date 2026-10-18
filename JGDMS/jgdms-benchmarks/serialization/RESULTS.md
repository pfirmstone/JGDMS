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
(12.82 ± 9.53 after vs 24.60 ± 34.08 before) is likewise within noise.

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
- **A remote call (JERI) improves 7–20%** because the transport is shared and unchanged: the codec is
  a fraction of a loopback call. `batch100` benefits most (0.80).
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
