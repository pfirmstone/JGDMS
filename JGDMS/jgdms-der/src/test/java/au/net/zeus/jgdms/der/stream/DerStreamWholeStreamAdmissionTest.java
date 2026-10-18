/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.stream;

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static au.net.zeus.jgdms.der.stream.DerStreamAdmissionCharacterisationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B2: whole-stream admission. A stream is admitted or rejected as a whole BEFORE any object is built from it.
 *
 * <ul>
 *   <li>every reject class, placed first / in the middle / last of a long stream, rejects the stream with zero objects
 *       delivered and zero constructors run;</li>
 *   <li>a dispatcher-created stream admits in two stages (cheap at construction, full at {@code admitStream()}), refuses
 *       record-bearing reads until then, and a failed admission is permanent;</li>
 *   <li>admission uses no class loader at all;</li>
 *   <li>differential against the lazy per-item path over the golden corpus and thousands of mutated streams: admission never
 *       rejects a stream the lazy path would read to the end, and every wire-level failure the lazy path hits is an admission
 *       reject.</li>
 * </ul>
 */
class DerStreamWholeStreamAdmissionTest {

    @BeforeEach
    void reset() {
        CONSTRUCTED.set(0);
    }

    // ------------------------------------------------------------------ the reject classes

    /** One way a stream can be structurally bad: the items appended (after the good prefix), and a message fragment. */
    private record BadTail(String name, byte[] bytes, String message) {}

    private static byte[] chain(int n, String className) {
        // a one-record, zero-field chain; distinct class names make distinct identities
        return new AtomicSerialSchemaRecord(className + n, List.of()).encode();
    }

    private static byte[] fullChainItem(byte[] chainBytes) {
        return DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of())))),
                DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 0), chainBytes),
                DerWriter.writeUtf8String(FORMAT))));
    }

    private static List<BadTail> badTails() throws Exception {
        List<BadTail> out = new ArrayList<>();
        out.add(new BadTail("unknown digest", unknownDigestItem(), "unknown-digest"));
        out.add(new BadTail("wrong arm tag", DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(emptyHierarchy()), DerWriter.writeOctetString(new byte[40]),
                DerWriter.writeUtf8String(FORMAT)))), "SchemaChainRef"));
        out.add(new BadTail("non-canonical length", new byte[] {(byte) 0xA1, (byte) 0x81, 0x05, 0x30, 0x03, 0x04, 0x01, 0x00}, "Non-canonical"));
        out.add(new BadTail("truncated item", new byte[] {(byte) 0xA1, 0x20, 0x30, 0x03},
                ""));       // a truncated header swallows what follows it, so WHICH check fires depends on the position
        out.add(new BadTail("unknown top-level tag", new byte[] {(byte) 0x9F, 0x21, 0x01, 0x00}, "unexpected top-level tag"));
        out.add(new BadTail("proxy interior", DerWriter.writeTlv(CTX_PROXY, new byte[] {0x02, 0x01, 0x05, 0x00}), "proxy"));
        out.add(new BadTail("collection interior", DerWriter.writeTlv(CTX_COLLECTION, cat(DerWriter.writeUtf8String("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(DerWriter.writeOctetString(new byte[3]))))), "nested @AtomicSerial site"));
        // a chain over the chain-bytes ceiling: 65 536 bytes is the cap, so 66 000 bytes of fields is over it
        List<AtomicSerialFieldDef> fat = new ArrayList<>();
        for (int i = 0; i < 330; i++) fat.add(new AtomicSerialFieldDef("f" + String.format("%03d", i) + "x".repeat(196), "int"));
        byte[] fatChain = new AtomicSerialSchemaRecord("Fat", fat).encode();
        assertTrue(fatChain.length > 65536, "chain size " + fatChain.length);
        out.add(new BadTail("chain bytes over the ceiling", fullChainItem(fatChain), "maxChainBytes"));
        // the SAME oversize chain, hidden where the dedup layer does not look (C.6.5): inside a [8] proxy handler record and inside
        // a [16] collection element. Only the verify-only descent can see these before an object is built.
        byte[] canonicalRecordWithFatChain = DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of())))),
                DerWriter.writeOctetString(fatChain), DerWriter.writeOctetString(new byte[32]), DerWriter.writeUtf8String(FORMAT)));
        out.add(new BadTail("oversize chain inside a [8] handler record", DerWriter.writeTlv(CTX_PROXY, cat(
                DerWriter.writeInteger(1), DerWriter.writeUtf8String("java.lang.Runnable"),
                DerWriter.writeTlv(CTX_ATOMIC, canonicalRecordWithFatChain))), "maxChainBytes"));
        byte[] nestedFat = DerWriter.writeSequence(List.of(DerWriter.writeOctetString(fatChain),
                DerWriter.writeOctetString(DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of()))))));
        out.add(new BadTail("oversize chain inside a [16] collection element", DerWriter.writeTlv(CTX_COLLECTION, cat(
                DerWriter.writeUtf8String("list:@AtomicSerial"), DerWriter.writeSequence(List.of(nestedFat)))), "maxChainBytes"));
        // 257 distinct chains: the table ceiling (256) is breached at the 257th insertion
        byte[][] items = new byte[257][];
        for (int i = 0; i < 257; i++) items[i] = fullChainItem(chain(i, "T"));
        out.add(new BadTail("distinct-chain table ceiling", cat(items), "maxDistinctChainsPerStream"));
        // a chainRef fan-out bomb is exercised below with a small input cap
        return out;
    }

    /** Builds [good x before, bad tail, good x after] so the bad part sits first, in the middle or last. */
    private static byte[] longStream(BadTail bad, String where) throws IOException {
        // the good items come from one real encoder so their first item carries the fullChain and the rest chainRefs
        int before = switch (where) { case "first" -> 0; case "middle" -> 10; default -> 20; };
        int after = switch (where) { case "first" -> 20; case "middle" -> 10; default -> 0; };
        int[] ns = new int[before + after];
        for (int i = 0; i < ns.length; i++) ns[i] = i + 1;
        byte[] good = goodStream(ns);
        byte[] items = itemsOf(good);
        // split the encoded items at the boundary: each good item is a [1] TLV; walk them
        au.net.zeus.jgdms.der.DerReader r = new au.net.zeus.jgdms.der.DerReader(items);
        int cut = items.length;
        try {
            int idx = 0;
            while (r.hasMore()) {
                if (idx == before) { cut = r.position(); break; }
                r.skipTlv();
                idx++;
            }
        } catch (au.net.zeus.jgdms.der.DerException e) {
            throw new IllegalStateException(e);
        }
        if (before == 0) cut = 0;
        byte[] head = java.util.Arrays.copyOfRange(items, 0, cut);
        byte[] tail = java.util.Arrays.copyOfRange(items, cut, items.length);
        return cat(new byte[] {(byte) 0x8F, 1, 1}, head, bad.bytes, tail);
    }

    @TestFactory
    Stream<DynamicTest> everyRejectClassAtEveryPositionRejectsTheWholeStreamBeforeAnythingIsBuilt() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (BadTail bad : badTails()) {
            for (String where : new String[] {"first", "middle", "last"}) {
                tests.add(DynamicTest.dynamicTest(bad.name + " / " + where, () -> {
                    CONSTRUCTED.set(0);
                    byte[] stream = longStream(bad, where);
                    // a bad item that is itself a fullChain is "bad" only because of its size/number; a stream whose bad part is a
                    // DUPLICATE needs an earlier good item, so every "first" case that depends on one is skipped by construction
                    Run r = readAll(stream, 1000);
                    assertNotNull(r.failure(), "must be rejected: " + bad.name + " at " + where);
                    assertEquals(0, r.delivered(), "nothing may be delivered");
                    assertEquals(0, r.constructed(), "no constructor may run");
                    assertEquals(0, r.callbacksFired());
                    assertTrue(DerStreamAdmissionCharacterisationTest.chain(r.failure()).contains(bad.message),
                            "expected '" + bad.message + "' in " + DerStreamAdmissionCharacterisationTest.chain(r.failure()));
                }));
            }
        }
        return tests.stream();
    }

    @Test
    void aDuplicateFullChainAnywhereAfterTheFirstRejectsTheWholeStream() throws Exception {
        byte[] first = goodStream(1);                              // fullChain for Counted
        byte[] dup = itemsOf(goodStream(9));                       // the same chain sent in full again
        for (int between : new int[] {0, 1, 15}) {
            int[] ns = new int[between + 1];
            for (int i = 0; i < ns.length; i++) ns[i] = i + 2;
            byte[] stream = cat(first, between == 0 ? new byte[0] : itemsOf(goodStreamAfter(between)), dup);
            CONSTRUCTED.set(0);
            Run r = readAll(stream, 1000);
            assertNotNull(r.failure(), "between=" + between);
            assertEquals(0, r.constructed());
            assertTrue(DerStreamAdmissionCharacterisationTest.chain(r.failure()).contains("duplicate fullChain"));
        }
    }

    /** {@code n} chainRef items for Counted, to follow a stream that already sent its chain. */
    private static byte[] goodStreamAfter(int n) throws IOException {
        int[] ns = new int[n + 1];
        for (int i = 0; i < ns.length; i++) ns[i] = i + 1;
        byte[] withN = goodStream(ns);
        byte[] one = goodStream(1);
        return cat(new byte[] {(byte) 0x8F, 1, 1}, java.util.Arrays.copyOfRange(withN, one.length, withN.length));
    }

    @Test
    void aChainRefFanOutBombIsRejectedBeforeAnythingIsBuilt() throws Exception {
        // a ~64 KB chain re-materialised at every chainRef; with a 256 KiB input cap the per-item ceiling is 2 MiB, i.e. ~32 refs.
        // The WHOLE-stream cumulative ceiling (x64 = 16 MiB) is the one that matters across items: ~256 refs.
        List<AtomicSerialFieldDef> fields = new ArrayList<>();
        for (int i = 0; i < 300; i++) fields.add(new AtomicSerialFieldDef("f" + String.format("%03d", i) + "x".repeat(196), "int"));
        byte[] big = new AtomicSerialSchemaRecord("Bomb", fields).encode();
        assertTrue(big.length < 65536 && big.length > 60000);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(big);
        byte[] hier = DerWriter.writeSequence(List.of(DerWriter.writeSequence(List.of())));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new byte[] {(byte) 0x8F, 1, 1});
        o.writeBytes(fullChainItem(big));
        byte[] refItem = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(hier), DerWriter.writeTlv(REF, digest), DerWriter.writeUtf8String(FORMAT))));
        for (int i = 0; i < 400; i++) o.writeBytes(refItem);      // 400 x ~45 B = 18 KB of input, 25 MB of reconstitution
        byte[] stream = o.toByteArray();
        DerInputLimits limits = DerInputLimits.maxBytes(256 * 1024);
        IOException e = assertThrows(IOException.class,
                () -> new DerMarshalInputStream(new ByteArrayInputStream(stream), ResolutionContext.NONE, limits));
        String all = DerStreamAdmissionCharacterisationTest.chain(e);
        assertTrue(all.contains("maxReconstitutedBytes") || all.contains("maxStreamReconstitutedBytes"), all);
    }

    // ------------------------------------------------------------------ the dispatcher-created (two-stage) stream

    private static DerMarshalInputStream dispatcherStream(byte[] bytes) throws IOException {
        return DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(bytes), ResolutionContext.NONE, DerInputLimits.DEFAULT);
    }

    /** [method hash as a long, Counted(1), Counted(2)] as a dispatcher's request stream looks. */
    private static byte[] requestStream(byte[]... extra) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeLong(0x1234_5678_9ABCL);
        c.writeObject(new Counted(1));
        c.writeObject(new Counted(2));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        for (byte[] e : extra) bos.writeBytes(e);
        return bos.toByteArray();
    }

    @Test
    void cheapStageRunsAtConstructionAndTheFullWalkDoesNot() throws Exception {
        // an unknown-digest item is invisible to the cheap stage: constructing is fine, admitStream() is what rejects
        DerMarshalInputStream in = dispatcherStream(requestStream(unknownDigestItem()));
        assertEquals(0x1234_5678_9ABCL, in.readLong(), "the method hash (a scalar) is readable before admission");
        IOException e = assertThrows(IOException.class, in::admitStream);
        assertTrue(DerStreamAdmissionCharacterisationTest.chain(e).contains("unknown-digest"));
        assertEquals(0, CONSTRUCTED.get());
        // framing errors are the cheap stage's: they reject at construction, before authorization could even run
        assertThrows(IOException.class, () -> dispatcherStream(requestStream(new byte[] {(byte) 0xA1, 0x20, 0x30, 0x03})));
        assertThrows(IOException.class, () -> dispatcherStream(requestStream(new byte[] {(byte) 0x9F, 0x21, 0x01, 0x00})));
    }

    @Test
    void recordBearingReadsAreRefusedUntilAdmittedAndTheRefusalConsumesNothing() throws Exception {
        DerMarshalInputStream in = dispatcherStream(requestStream());
        in.readLong();
        IOException e = assertThrows(IOException.class, in::readObject);
        assertTrue(e.getMessage().contains("admitStream()"), e.getMessage());
        assertEquals(0, CONSTRUCTED.get());
        in.admitStream();
        in.admitStream();                                             // idempotent
        assertEquals(1, ((Counted) in.readObject()).n, "the refused read consumed nothing");
        assertEquals(2, ((Counted) in.readObject()).n);
        assertEquals(2, CONSTRUCTED.get());
    }

    @Test
    void aFailedAdmissionIsPermanentAndAbandonsTheDecodeUnit() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        DerMarshalInputStream in = dispatcherStream(requestStream(unknownDigestItem()));
        in.registerValidation(fired::incrementAndGet, 0);
        assertThrows(IOException.class, in::admitStream);
        IOException again = assertThrows(IOException.class, in::admitStream, "a second call rethrows");
        assertTrue(again.getMessage().contains("rejected by admission"), again.getMessage());
        assertThrows(IOException.class, in::readLong, "even a scalar read rethrows once the stream is rejected");
        assertThrows(IOException.class, in::readObject);
        in.close();
        assertEquals(0, fired.get());
        assertEquals(0, CONSTRUCTED.get());
    }

    @Test
    void aStreamFromAPublicConstructorIsAlreadyAdmittedSoAdmitStreamIsANoOp() throws Exception {
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(requestStream()));
        in.admitStream();
        in.readLong();
        assertEquals(1, ((Counted) in.readObject()).n);
    }

    // ------------------------------------------------------------------ admission loads no class and builds nothing

    @Test
    void admissionNeverTouchesTheClassLoader() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        ClassLoader counting = new ClassLoader(DerStreamWholeStreamAdmissionTest.class.getClassLoader()) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                loads.incrementAndGet();
                return super.loadClass(name);
            }
        };
        ResolutionContext ctx = new ResolutionContext(counting, false, counting);
        for (Map.Entry<String, Supplier<Object>> e : DerGoldenBytesTest.corpus().entrySet()) {
            if (DerGoldenBytesTest.SINGLE_ONLY.contains(e.getKey())) continue;
            byte[] stream = encodeCorpus(e.getValue());
            if (stream == null) continue;
            loads.set(0);
            DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(stream), ctx, DerInputLimits.DEFAULT);
            assertEquals(0, loads.get(), e.getKey() + ": admission resolved a class");
            in.close();
        }
    }

    private static byte[] encodeCorpus(Supplier<Object> s) {
        try {
            DerObjectStreamCodec c = new DerObjectStreamCodec();
            Object o = s.get();
            if (o instanceof DerGoldenBytesTest.MixedStream m) {
                for (Object item : m.items) writeItem(c, item);
            } else {
                writeItem(c, o);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            c.drainTo(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeItem(DerObjectStreamCodec c, Object o) throws IOException {
        if (o instanceof DerGoldenBytesTest.TopLevelCollection t) c.writeCollection(t.coll, t.token); else c.writeObject(o);
    }

    // ------------------------------------------------------------------ differential against the lazy per-item path

    /** Reads every item the way the delivery path does, with no admission. Returns null if it read to the end, else the failure. */
    private static Throwable lazyFailure(byte[] stream) {
        try {
            DerObjectStreamCodec c = new DerObjectStreamCodec();
            c.initReader(stream);
            while (c.available() > 0) {
                c.readObject();
            }
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static Throwable admissionFailure(byte[] stream) {
        try {
            new DerMarshalInputStream(new ByteArrayInputStream(stream), ResolutionContext.NONE, DerInputLimits.DEFAULT).close();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    /**
     * Failures of the kind admission exists to catch: the dedup layer, the top-level item framing, the version octet. A framing
     * or value error INSIDE a record's payload (a field TLV that overruns, a bad set order) is the record decoder's to find when
     * that object is read (the walker passes field values through verbatim; payload value and interior-structure checks are the
     * optional B3 extension), so it is deliberately not in this list.
     */
    private static boolean isWireLevel(Throwable t) {
        String m = DerStreamAdmissionCharacterisationTest.chain(t);
        return m.contains("StreamSchemaDedup") || m.contains("stream schema-dedup") || m.contains("failed to read TLV header")
                || m.contains("readObject: failed to read");
    }

    @TestFactory
    Stream<DynamicTest> admissionAgreesWithTheLazyPathOnTheCorpusAndOnMutatedStreams() {
        int iterations = Integer.getInteger("admission.differential.iterations", 120);
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, Supplier<Object>> e : DerGoldenBytesTest.corpus().entrySet()) {
            if (DerGoldenBytesTest.SINGLE_ONLY.contains(e.getKey())) continue;
            tests.add(DynamicTest.dynamicTest(e.getKey(), () -> {
                byte[] valid = encodeCorpus(e.getValue());
                if (valid == null) return;                                   // not writable through the stream codec
                assertNull(lazyFailure(valid), "the corpus must read to the end lazily");
                assertNull(admissionFailure(valid), "the corpus must be admitted");
                Random r = new Random(0xB2 ^ e.getKey().hashCode());
                int wireRejects = 0;
                for (int i = 0; i < iterations; i++) {
                    StringBuilder how = new StringBuilder();
                    byte[] items = java.util.Arrays.copyOfRange(valid, 3, valid.length);
                    byte[] mutated = (i % 3 == 2) ? StreamSchemaDedupEquivalenceTest.mutateBytes(items, r, how)
                                                  : StreamSchemaDedupEquivalenceTest.mutateTree(items, r, how);
                    if (mutated == null) continue;
                    byte[] stream = cat(new byte[] {(byte) 0x8F, 1, 1}, mutated);
                    Throwable lazy = lazyFailure(stream);
                    Throwable admitted = admissionFailure(stream);
                    String ctx = e.getKey() + " #" + i + " [" + how + "]";
                    if (admitted != null) {
                        // never a false reject: the lazy path must also have failed somewhere
                        assertNotNull(lazy, ctx + ": admission rejected a stream the lazy path reads to the end: "
                                + DerStreamAdmissionCharacterisationTest.chain(admitted));
                        wireRejects++;
                    } else if (lazy != null) {
                        // admitted, yet the lazy path failed: only a Layer-2 / value failure is allowed
                        assertFalse(isWireLevel(lazy), ctx + ": a wire-level failure escaped admission: "
                                + DerStreamAdmissionCharacterisationTest.chain(lazy));
                    }
                }
                assertTrue(wireRejects > 0 || iterations < 10, e.getKey() + ": the mutator never produced a rejected stream");
            }));
        }
        return tests.stream();
    }
}
