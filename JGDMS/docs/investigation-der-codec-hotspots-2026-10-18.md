# Investigation — where Atomic DER spends its time, and what can be removed without weakening it

**Status:** profiled, not yet changed. Follows from
`jgdms-benchmarks/serialization/RESULTS.md` (18 Oct 2026). JOSS is to be
removed, so this document only asks where DER's cost is and which parts of it
are implementation choices rather than STD-006 requirements.

**Method.** JFR execution samples (`-prof jfr`) on `CodecBenchmark`
marshal/unmarshal for `record`, `batch100`, `blob64k`, format `DER`, aggregated
by self time and by inclusive time of `au.net.zeus.jgdms.der.*` frames; stock
JDK 21 on Linux (DER code paths identical; DirtyChai ratios in RESULTS.md
agree). Every hot spot below was then read in the source to see *why* it is
hot. Percentages are of samples in the benchmark thread.

## The cost structure

| phase | marshal (record) | marshal (batch100) | unmarshal (record) | unmarshal (batch100) |
|---|---:|---:|---:|---:|
| `DerWriter` copy-per-level (`writeTlv`+`writeSequence`+`encodeLength`, self) | 41% | 54% | 3% | 24% (!) |
| Stream schema dedup transform (`StreamSchemaDedup.transformP1`, inclusive) | 39% | 30% | 18% | 34% |
| Schema chain re-encoded per object (`AtomicSerialSchemaRecord.encode`, inclusive) | 24% | 23% | – | – |
| Transmitted schema parsed per stream (`SchemaChain.decodeChain`, inclusive) | 16%\* | – | 21% | – |
| SHA-256 (`SHA2.implCompress0`, self) | 1.4% | 1.6% | 3% | 4.5% |
| Input buffering (`DerInputLimits.readAllBytesBounded` → `readNBytes`, inclusive) | – | – | 24% | – |
| `GetArg.callerClass` stack walk (inclusive) | – | – | 16% | ~2% |
| Class resolution via `ClassLoading`/`RMIClassLoader` (self) | – | – | 2% | 2% |

\* yes, in *marshal*: the dedup pass parses the schema it has just written.

`DerWriter` appearing in the **unmarshal** column is the tell: decoding
re-encodes. The dedup transform reconstitutes every record's canonical full
form as fresh bytes before the record codec sees it.

## Candidates, ranked

Each is marked by what it does to the wire bytes and to the security
argument. "Bytes identical" means the encoder emits exactly what it emits
today, so the conformance corpus is the regression test.

### 1. Cache the encoded schema chain and its leaf digest per class — bytes identical, no security change

`SchemaGenerator` memoises the schema chain *model* in a `ClassValue`, but
`ObjectCodec.encodeSchemaChainBytes` calls `AtomicSerialSchemaRecord.encode()`
on every record of the chain for **every object marshalled**, and
`StreamSchemaDedup.encodeSite` recomputes `sha256(leafRecordBytes(...))` each
time (`MessageDigest.getInstance` included). Both are pure functions of the
class. Caching the encoded bytes and the digest beside the model (same
`ClassValue`, so GC-tied to the class, never cross-loader) removes ~25% of
marshal CPU for small objects and all per-object hashing.

Security: the cached value is what the generator would produce anyway; the
cache key is the `Class` object itself, so a different class (or the same
name in another loader) never hits. Nothing received from the wire enters
the cache.

### 2. Single-buffer `DerWriter` — bytes identical, no security change

`DerWriter` is a functional builder: every `writeTlv` allocates `tag + length
+ content` and `System.arraycopy`s the content in; every enclosing
`writeSequence` concatenates its children into another new array. A value
nested *d* levels deep is copied *d* times — the 64 KiB blob about six times
(hence 137 µs vs 7.8 µs for the equivalent JOSS write), and every field of
every record likewise. This is 41–54% of marshal self time and the whole of
the blob gap.

Standard remedy for definite-length DER: encode **back to front** into one
growing buffer (write content, then its length, then its tag, so lengths are
known when needed), or a two-pass size computation then a single forward
write. Output is byte-identical by construction.

### 3. Emit the dedup stream grammar directly — bytes identical, verification unchanged

`StreamSchemaDedup` is a byte-level post-pass: the encoder first produces the
canonical full-form record (schema chain inline), then `transformP1`
re-parses that record (`DerReader`, `Tag.decode` in the marshal profile)
and re-emits it with the chain replaced by a `chainRef` when the chain was
already seen. For a 100-element batch the chain is encoded 100 times and
discarded 99 times, and the whole record is parsed and copied once more.
Decoding mirrors it: `reconstituteTopLevelAtomic` rebuilds full-form bytes
for every element (that is the 24% of `DerWriter` in the unmarshal profile)
so that `MarshalledInstanceCodec` can decode "exactly the bytes a canonical
full-form stream would have delivered".

Letting the encoder consult the per-stream table *as it writes* (first
occurrence → full chain, later → ref) and letting the decoder hand the record
codec a chain *resolved by reference* rather than a re-materialised copy
removes one full encode-parse-encode cycle on the way out and one
parse-encode-parse cycle on the way in. Appendix C's rules (first-occurrence
by pre-order traversal, fail-closed `chainRef` resolution, per-stream table
only, ceilings) are all preserved: the same decisions are made at the same
points, just without the intermediate byte arrays. This is the largest
refactor of the three but also the largest win on multi-object streams
(~30% marshal, ~30% unmarshal on `batch100`).

Do 1 and 2 first: 3 becomes simpler once chain bytes are cached and the
writer is a buffer.

### 4. Decode-side schema fast path — strictly stronger check, review wanted

On receipt, `SchemaChain.decodeChain` parses the transmitted chain into
records and field definitions (`String` construction, UTF-8 validation,
`HashMap`s — ~20% of a single-object unmarshal) before it is compared with
the receiver's class. With 1 in place the receiver holds the canonical
encoding of *its own* chain for the resolved class. If the transmitted chain
bytes are `Arrays.equals` to that cached encoding, the schemas are identical
by canonical-form uniqueness and the digest is known without hashing; parsing
can be skipped. Any mismatch falls through to today's full path (which is
where evolution handling and rejection already live).

Security: byte equality against a locally generated canonical encoding is a
stronger condition than digest equality, and the fast path only ever
*accepts* what the slow path would also accept. Worth a short review of the
ordering (class must be resolved — by name, through `ResolutionContext` —
before any comparison) rather than treated as obviously safe.

### 5. Proportional input buffering — no security change

`DerInputLimits.readAllBytesBounded` calls `in.readNBytes(max + 1)` with
`max` = 16 MiB; `InputStream.readNBytes` starts with a 16 KiB chunk, so every
stream — a 288-byte `record` included — allocates and then trims 16 KiB
(`readNBytes` is 21% of self time in `unmarshal record`). Reading in
proportion to what arrives (grow from a small buffer, still capped at `max +
1`) keeps the bound and drops the allocation. Buffering the whole bounded
input before decoding is a deliberate choice (bound before parse) and
should stay.

### 6. Class resolution — leave, or cache only with review

`ResolutionContext.loadClass` goes through `ClassLoading` →
`RMIClassLoader` → `LoaderHandler.getDefaultCodebaseURLs` for every class
name per stream (~2%). The Warres discipline (endpoint loader, never TCCL)
is the point of that path. A `(loader, name) → Class` cache with weak keys
would be semantically identical for a given loader instance, but class
resolution is exactly where this codebase has been burned before; 2% is not
worth touching without a dedicated review.

### Keep as is — these are the security, not overhead

- **`GetArg.callerClass` stack walk** (16% of a tiny unmarshal, ~2% of a
  batch). It is how each level of an `@AtomicSerial` hierarchy is confined to
  its own namespace during construction. The alternative — the constructor
  passing its own class — lets a subclass name its superclass. Not
  negotiable; the walk is already depth-limited.
- **Fail-closed schema verification, canonical-form checks, input ceilings,
  per-stream dedup tables.** None of these dominate the profile. The cost is
  in *how* the bytes are built and rebuilt around them.

## The transport is the ceiling for anything over 1 KiB (both formats)

Not a DER finding, but it bounds what the above can buy over JERI:

- `MuxClient`/`MuxServer` construct the mux with `maxFragmentSize = 1024`.
  `MuxOutputStream.write` fills a 1 KiB buffer and calls `writeBuffer`, which
  hands the fragment to the writer thread and **waits for it synchronously**
  (`futureSendData` → `waitForIO`) before accepting the next byte. A 64 KiB
  response is 64 hand-offs; the initial ration is 32 KiB, so the sender also
  stalls for an `IncrementRation` from the peer every 32 KiB.
- Measured: 128 KiB per `blob64k` call takes ~9.7 ms on Windows loopback
  (~13 MB/s), ~4.5 ms on Linux. The codec work is 0.14–0.26 ms of that.

Larger fragments and a writer that only blocks when the ration is exhausted
(rather than per fragment) would change bulk performance by an order of
magnitude for both formats. Whether the fragment size is free to change
without a protocol-level consequence (peer buffer expectations) needs
checking in the mux protocol spec before touching it.

DER's unexplained +2.4 ms on the `batch100` JERI call (RESULTS.md finding 4)
is most likely in this layer too: `DerMarshalOutputStream` encodes
everything then drains it in one burst of 1 KiB hand-offs, and
`DerMarshalInputStream` must receive EOF before decoding starts, so encode,
transfer and decode are serial, where `ObjectOutputStream` interleaves them.
Instrumenting `drainTo` and `readAllBytesBounded` with timestamps in the
JERI benchmark would settle it.

## Suggested order

1. Cache encoded chain + digest per class (small, isolated, immediate).
2. Single-buffer `DerWriter` (contained in one class; conformance corpus
   guards it).
3. Re-run `jgdms-benchmarks/serialization` — expect small-object marshal
   roughly halved and the blob gap gone.
4. Direct dedup emission/resolution (bigger; design review first).
5. Decode fast path and proportional buffering.
6. Mux fragment size / per-fragment wait, as a separate JERI investigation.

---
Written with AI assistance (Claude) under the direction of the JGDMS
maintainer; each hot spot was checked against the named source files.
