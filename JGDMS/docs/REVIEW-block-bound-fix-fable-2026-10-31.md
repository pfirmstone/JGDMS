claude-fable-5-1

# Review: top-level TC_BLOCKDATALONG bound in AtomicMarshalInputStream (2026-10-31)

Skills loaded: handover-composition, production-code, jgdms-board-review (Part I read; G6/G10/G11 applied). Read-only: no file
touched except this one; no mvn/java run (nothing below needed a build; the one mutation run that would upgrade a grade is named).
Reviewed state: JGDMS HEAD af64aea0c + the uncommitted diff `git diff -- JGDMS/jgdms-platform/src/main` (2 files, +19/-3) + the two
new tests (129 and 75 lines, `wc -l`). The other 8 modified / 10 untracked files in `git status` are outside this brief and unreviewed.
Not done: the rule-7 corpus sweep (E:\ forbidden); AI_AGENT_* handover check (none named; UNDETERMINED whether one is mandated for this
family). DirtyChai not opened. Producer model not stated; the cited investigation is headed `claude-opus-5-5`, so this review is cross-model.

## Verdict
No regression found. Monotone-permissive at top level (old: refused every long-form block with length > 0; new: refuses length > 1024),
byte-identical inside readObject. Fix F2 (comment placement) and F3 (test wiring) before commit; F1 is a follow-up design gap.

## Findings, most severe first
**F1 PLAUSIBLE — the per-block ceiling exists only at top level; inside readObject a single block may still buffer ~2 GiB.**
`readBlockDataLong`: `long allowed = topLevel ? ObjOutputStream.MAX_BLOCK_DATA_LEN : arrayLenAllowedRemain;` (AtomicMarshalInputStream.java:813).
In-object `arrayLenAllowedRemain` starts at `MAX_COMBINED_ARRAY_LEN = Integer.MAX_VALUE - 8` (:151, :3197), and `readBytesChunked`
grows its buffer to the full declared length if the bytes arrive (:2130-2146). Scenario: a peer sends, inside an Externalizable's
block data, `TC_BLOCKDATALONG 0x7FFFFFF0` followed by the bytes; the reader buffers them whole (doubling copies ~3 GiB transient).
Pre-existing, acknowledged by AtomicMarshalInputStreamArrayBoundTest.java:50-56 ("no hard ceiling ... legitimately large payloads") —
a justification that holds for primitive ARRAYS, not block data: no known writer emits a block over 1024 at ANY level (Q3). The fix
leaves the reader stricter at top level than in-object, the inverted shape. Recommend the 1024 cap at every level, keeping the
in-object debit. UNVERIFIED-NEEDS-BUILD: that the platform+jeri suites stay green with the cap in-object.

**F2 VERIFIED — the new javadoc orphans `drain()`'s javadoc.** ObjOutputStream.java:438-452: the constant and its doc block were inserted
between drain()'s existing `/** Writes buffered data ... */` (:438-444) and `protected void drain()` (:452). The old block now attaches
to nothing; drain() is undocumented. Move `MAX_BLOCK_DATA_LEN` (with its javadoc) above :438.

**F3 VERIFIED — RemoteContextCodecProductionReaderTest does not use the production codec factory.** It reads via
`AtomicMarshalInputStream.create(new ByteArrayInputStream(wire), null, false, null, null, false)` (test :66-67) instead of
`new AtomicReducingContextCodec().createMarshalInputStream(...)` (AtomicReducingContextCodec.java:60-67, the dispatcher's path at
BasicInvocationDispatcher.java:2552-2554). Today the flags coincide (readAnnotations=false), so the test is honest now, but a later
change to the factory would not be seen, and its javadoc claim "read by the PRODUCTION JOSS reader" overstates it. Hand-writing the
wire bytes (KIND_URL mirror, :42) is an acceptable INPUT fixture. Fix: one line, call the codec factory.

**F4 VERIFIED — 1024 is a writer constant, not a protocol bound; say so in the comment.** The Object Serialization Stream Protocol
grammar allows TC_BLOCKDATALONG up to 2^31-1. Our writer caps at 1024 (ObjOutputStream.java:462-469) and so does the JDK's:
`private static final int MAX_BLOCK_SIZE = 1024;` (Zulu 25 src.zip java.base/java/io/ObjectOutputStream.java:1631; Zulu 11 :1739;
extracted to the scratchpad, not read from the forbidden DirtyChai tree). A non-JDK writer emitting a larger top-level block is
refused — no regression, since before the fix every top-level long block was refused — but the constant's javadoc should name the JDK
constant as the second source of the value so a reader knows what it is pinned to.

**F5 PLAUSIBLE, pre-existing, unchanged — TC_RESET spin.** `checkReadPrimitiveTypes` loops on TC_RESET (:518-541), one `resetState()`
per byte, no count; CPU bounded by bytes the attacker transmits, no allocation growth. Out of scope; it sits on the same path.

## The six questions
**Q1 DoS — VERIFIED no weakening.** (i) Hostile declared length: top level now rejects length > 1024 BEFORE any allocation (:812-818);
length <= 1024 is read with an exact-size buffer (`cap = min(length, READ_CHUNK)` = length, :2133). In-object branch unchanged;
ArrayBoundTest's blockDataLong case (test :187-215) is in-object (a boxed Long graph) and unaffected. (ii) Many blocks: consumption is
demand-driven. A block is fetched only by `checkReadPrimitiveTypes` when `primitiveData` is exhausted (:512), one block per call, and
`read()` (:732), `read(byte[],..)` (:775-776), `available()` (:497) and `skipBytes` each trigger at most one fetch per call; the previous
block is dropped. So top-level block bytes read <= bytes the application asks for + 1023, held one block (<= 1024 B) at a time, and the
attacker must transmit them (`readFully`). Short-form blocks were already unbounded in COUNT before the fix with the same shape; the
change is the per-fetch maximum, 255 -> 1024. Leftover bytes from an over-long block fail closed at the next readObject:
`throw new StreamCorruptedException("unexpected block data")` (:1036-1037). (iii) Other caps: the ACC side-band is a
ByteArrayInputStream of <= `MAX_ACC_BLOCK_BYTES = 1024 * 1024` (BasicInvocationDispatcher.java:207, enforced :2576-2579), and
RemoteContextCodec's ceilings bound its demand (MAX_DOMAINS 4096, MAX_CERTS 100, MAX_DIGEST_LEN 512, MAX_CERT_LEN 64 KiB, :93-96;
readUTF <= 65535 by format). The main request stream (AtomicInvocationDispatcher.java:163) has no byte cap, but its top-level demand is
one readLong (method hash, BasicInvocationDispatcher.java:1738) plus per-argument readObject (budgeted). A per-stream top-level budget
is NOT warranted for memory; it would only bound bandwidth/time, which the transport owns. Do add one comment line: the bound depends
on fetch being demand-driven, so a future read-ahead would reopen it.
**Q2 `nestedLevels == 0` — VERIFIED correct.** Field :219 (default 0); `++` at :3196 and `--` in `finally` at :3218, both only in
`readObject(boolean,boolean,Class)` (:3176), the sole caller of `readNonPrimitiveContent` (:3211; grep: one call). Custom readObject /
readExternal / readFields / defaultReadObject all run beneath `readNewObject` <- `readNonPrimitiveContent`, so nestedLevels >= 1
there; `discardData` (:901) is called only from those paths (:1333, :1899, :2290, :2409, :2462, :2828, :2901, :2926, :2978, :2981);
`resetState` throws if nestedLevels != 0 (:3572). The only place nestedLevels is 0 with graph state still live is the validateObject
loop (:3229-3241), which runs AFTER the decrement; a validator reading primitives there was already budget-0 before the fix and now
gets the 1024 bound — negligible. At the reducing-context read: a fresh instance per request (BasicInvocationDispatcher.java:2552 ->
AtomicReducingContextCodec.java:66) so nestedLevels is 0 at `in.readInt()` (RemoteContextCodec.java:300); digests/certs go through
`in.readObject()` (`readByteArray`, :374-377) and return nestedLevels to 0 before the next primitive. It cannot be non-zero there.
**Q3 writers — VERIFIED consistent.** `grep TC_BLOCKDATALONG **/*.java`: the only emission in src/main is ObjOutputStream.java:468
(drain). Every primitive write goes to the `primitiveTypes` buffer (write/writeBytes/writeChars/writeInt/writeLong/writeUTF, :734-846,
:1369, :1383, :2147-2149) and is framed only by drain(); the other `output.write*` sites are tags, class descriptors and strings
(e.g. writeNewString :1766), not block data. `DelegateObjectOutputStream.drain()` only calls super (AtomicMarshalOutputStream.java:
322-325); the Externalizable path drains through the same method. JDK writer: 1024, and its bulk `write(byte[],off,len)` emits whole
blocks of exactly MAX_BLOCK_SIZE (Zulu 25 :1738-1745). No writer emits a block the reader now refuses where it did not need to.
**Q4 tests — VERIFIED load-bearing for the bound, by reading.** The three hostile cases (2^31-1, 1025, -1) pass under old and new
code (old `allowed = 0` refuses them with the same message) — acceptable, because the 1025 case still pins the constant: if the bound
were raised to 1<<20, `readBytesChunked(input, 1025)` hits EOF on the empty remainder, the EOFException message is null,
`String.valueOf(null).contains("excessively long")` is false and `assertTrue` fails (test :104-113); with `blockAtTheWriterLimitIsAccepted`
(1024) the pair pins exactly 1024. So no bound test is missing. What is missing is pinning the test to the WRITER constant: use
`ObjOutputStream.MAX_BLOCK_DATA_LEN + 1` (same package) instead of the literal 1025, so a joint writer+reader change fails loudly.
The round-trip cases (300, 1100, 1024) traverse the production read path (read(byte[]) -> checkReadPrimitiveTypes -> readBlockDataLong)
and would fail on old code — relayed from the brief, consistent with the code read. UNVERIFIED-NEEDS-BUILD: the observed failure under
the mutation (set the constant to 1<<20; `mvn -o -pl jgdms-platform test -Dtest=AtomicMarshalInputStreamTopLevelBlockTest`; expect
exactly `blockOneOverTheWriterLimitIsRefused` to fail).
**Q5 DER — VERIFIED untouched.** `DerReducingContextCodec.createMarshalInputStream` returns `new DerMarshalInputStream(...)`
(:79-85); `public final class DerMarshalInputStream implements AtomicObjectInput` (DerMarshalInputStream.java:117) is not a subclass.
`grep AtomicMarshalInputStream jgdms-der/src/main`: 4 files, all comments (ProxyWireSupport:47, ObjectCodec:204, StreamSchemaDedup:282,
289, DerObjectStreamCodec:1755) — no construction or call. AtomicDerInvocationHandler/Dispatcher use DerMarshalInputStream (:188-201,
:110-116). Instrument: text search over jgdms-der/src/main and jeri *Der*.java; blind to a DER path via a non-DER-named jeri class (none known).
**Q6 other callers — VERIFIED monotone-permissive.** Old top-level condition refused length > 0 (`arrayLenAllowedRemain` default 0
:222, reset to 0 :3219); new refuses length > 1024; in-object byte-identical. Every previously accepted stream is still accepted.
Top-level construction sites in src/main (grep `AtomicMarshalInputStream.create(|new AtomicMarshalInputStream(`, 11 hits):
AtomicInvocationDispatcher:163, AtomicInvocationHandler:308, AtomicReducingContextCodec:66, DgcRequestDispatcher:113, Plaintext:483,
ProxyTrustVerifier:598, AtomicMarshalledInstance:412, WireHandoffCodec:176, ActivationGroupInit:65, bench Format:69, and :326 — each
now accepts a top-level 256..1024-byte run it previously refused (this also fixes, SecurityManager or not, any method whose primitive
arguments exceed 255 bytes in one run — the investigation's unverified item 4; a run over 1024 is split by the writer and read as
several blocks). Persisted streams from our writer or the JDK's: same direction. DecodeDepthGuard (pref-class-loader :424-448) skips
blocks of any length — a pre-scan that is lenient where the reader is strict, i.e. fail-closed overall. Tests asserting the old
refusal: grep `excessively long|readBlockDataLong|TC_BLOCKDATALONG|0x7a` in src/test -> 4 files; none asserts a top-level refusal of a
<= 1024 block (ArrayBoundTest's case is in-object; the other two hits are javadoc mentions).

## Recommended before commit
1. F2: move the constant above drain()'s javadoc. 2. F3: read through `AtomicReducingContextCodec` in the jeri test. 3. Q4: use
`ObjOutputStream.MAX_BLOCK_DATA_LEN + 1` for the literal 1025. 4. F4/Q1: javadoc the JDK source of the value and the demand-driven-fetch
dependency. 5. F1 as a follow-up with its own build: cap every block at 1024.
