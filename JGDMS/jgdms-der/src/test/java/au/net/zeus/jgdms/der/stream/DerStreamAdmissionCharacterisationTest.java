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

import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B0 (SOW-DER-Streaming-Encoder, Part B): TODAY's admission behaviour, pinned as tests so B2 (whole-stream pre-admission) changes
 * the expectations deliberately and visibly. The reader buffers to EOF under {@code maxInputBytes} (all-or-nothing) but
 * everything else is checked lazily, per item, inside {@code readObject()}. So for a stream {@code [good1, good2, bad]}:
 * <ul>
 *   <li>good1 and good2 are DELIVERED (their constructors ran) and only then does the read of {@code bad} reject;</li>
 *   <li>an unread malformed trailing item is ACCEPTED;</li>
 *   <li>a decode unit that failed runs none of its completion callbacks (the DGC fix), so leases for the delivered
 *       objects are never issued.</li>
 * </ul>
 * Each "delivers k, then rejects" expectation below becomes "delivers 0, rejects the stream" in B2. Reject classes whose
 * stream-level vector needs an input window larger than a unit test should build (per-item and cumulative reconstitution
 * ceilings, table ceilings, chain-record ceilings, depth) are pinned at the walker level by
 * {@code StreamSchemaDedupReconstitutionBombTest}, {@code StreamSchemaDedupCumulativeWorkBoundTest},
 * {@code StreamSchemaDedupTableTest} and {@code StreamSchemaDedupEquivalenceTest}; they share the same per-item mechanism.
 */
class DerStreamAdmissionCharacterisationTest {

    static final AtomicInteger CONSTRUCTED = new AtomicInteger();

    /** Counts every deserialization constructor run: "a constructor ran" is what B2 must make zero for a rejected stream. */
    @AtomicSerial
    public static final class Counted {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[] { new AtomicSerial.SerialForm("n", int.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, Counted c) throws IOException {
            arg.put("n", c.n);
            arg.writeArgs();
        }
        final int n;
        public Counted(int n) { this.n = n; }
        public Counted(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            CONSTRUCTED.incrementAndGet();
            this.n = arg.get("n", 0);
        }
    }

    private static final Tag CTX_ATOMIC = new Tag(Tag.CLASS_CONTEXT, true, 1);
    private static final Tag CTX_PROXY = new Tag(Tag.CLASS_CONTEXT, true, 8);
    private static final Tag CTX_COLLECTION = new Tag(Tag.CLASS_CONTEXT, true, 16);
    private static final Tag REF = new Tag(Tag.CLASS_CONTEXT, false, 1);
    private static final String FORMAT = "JGDMS-STD-006/ATOMIC-DER";

    @BeforeEach
    void reset() {
        CONSTRUCTED.set(0);
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }

    /** Stream bytes for the given good objects: version octet + one item each, chain sent once (then chainRefs). */
    private static byte[] goodStream(int... ns) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        for (int n : ns) c.writeObject(new Counted(n));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    /** Just the item TLV(s) of a stream, i.e. without the 3-byte version octet. */
    private static byte[] itemsOf(byte[] stream) {
        return java.util.Arrays.copyOfRange(stream, 3, stream.length);
    }

    private static byte[] emptyHierarchy() {
        return DerWriter.writeSequence(List.of());
    }

    /** What reading {@code bytes} item by item does today. */
    private record Run(int delivered, Throwable failure, int constructed, int callbacksFired) {}

    private static Run readAll(byte[] bytes, int maxItems) throws Exception {
        AtomicInteger fired = new AtomicInteger();
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes));
        in.registerValidation(fired::incrementAndGet, 0);
        int delivered = 0;
        Throwable failure = null;
        try {
            for (int i = 0; i < maxItems; i++) {
                in.readObject();
                delivered++;
            }
        } catch (IOException | RuntimeException e) {
            failure = e;
        }
        in.close();
        return new Run(delivered, failure, CONSTRUCTED.get(), fired.get());
    }

    private static void assertDeliversTwoThenRejects(byte[] stream, String messagePart) throws Exception {
        Run r = readAll(stream, 3);
        assertNotNull(r.failure, "the third item must be rejected");
        assertEquals(2, r.delivered, "today the two good items ARE delivered before the reject (B2: zero)");
        assertEquals(2, r.constructed, "and their constructors ran (B2: zero)");
        assertEquals(0, r.callbacksFired, "but a failed decode unit runs no completion callbacks (the DGC fix)");
        String all = chain(r.failure);
        assertTrue(all.contains(messagePart), "expected '" + messagePart + "' in: " + all);
    }

    private static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable x = t; x != null; x = x.getCause()) sb.append(x.getClass().getSimpleName()).append(": ").append(x.getMessage()).append(" | ");
        return sb.toString();
    }

    // ------------------------------------------------------------------ per-item reject classes

    @Test
    void unknownDigest_deliversTwoThenRejects() throws Exception {
        byte[] bad = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(emptyHierarchy()), DerWriter.writeTlv(REF, new byte[32]), DerWriter.writeUtf8String(FORMAT))));
        assertDeliversTwoThenRejects(cat(goodStream(1, 2), bad), "unknown-digest");
    }

    @Test
    void duplicateFullChain_deliversTwoThenRejects() throws Exception {
        byte[] first = goodStream(1);                       // carries the fullChain
        byte[] dup = itemsOf(goodStream(9));                // a fresh stream's first item: ALSO a fullChain for the same identity
        assertDeliversTwoThenRejects(cat(first, refItem(2), dup), "duplicate fullChain");
    }

    /** A second item of the same class as a real encoder writes it: the chainRef form. */
    private static byte[] refItem(int n) throws IOException {
        byte[] two = goodStream(1, n);
        byte[] one = goodStream(1);
        return java.util.Arrays.copyOfRange(two, one.length, two.length);
    }

    @Test
    void wrongArmTag_deliversTwoThenRejects() throws Exception {
        byte[] bad = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(emptyHierarchy()),
                DerWriter.writeOctetString(new byte[40]),         // a universal OCTET STRING where a SchemaChainRef belongs
                DerWriter.writeUtf8String(FORMAT))));
        assertDeliversTwoThenRejects(cat(goodStream(1, 2), bad), "SchemaChainRef");
    }

    @Test
    void nonCanonicalLengthFraming_deliversTwoThenRejects() throws Exception {
        byte[] bad = {(byte) 0xA1, (byte) 0x81, 0x05, 0x30, 0x03, 0x04, 0x01, 0x00};   // length 5 in long form
        assertDeliversTwoThenRejects(cat(goodStream(1, 2), bad), "Non-canonical");
    }

    @Test
    void truncatedItem_deliversTwoThenRejects() throws Exception {
        byte[] bad = {(byte) 0xA1, 0x20, 0x30, 0x03};         // declares 32 bytes, carries 2
        assertDeliversTwoThenRejects(cat(goodStream(1, 2), bad), "overruns");
    }

    @Test
    void proxyInteriorReject_arrivesAfterEarlierItems() throws Exception {
        byte[] bad = DerWriter.writeTlv(CTX_PROXY, new byte[] {0x02, 0x01, 0x05, 0x00});    // interface count 5, nothing follows
        Run r = readAll(cat(goodStream(1, 2), bad), 3);
        assertEquals(2, r.delivered);
        assertNotNull(r.failure, "the [8] interior is checked only when that item is read");
        assertEquals(2, r.constructed);
        assertEquals(0, r.callbacksFired);
    }

    @Test
    void collectionInteriorReject_arrivesAfterEarlierItems() throws Exception {
        byte[] content = cat(DerWriter.writeUtf8String("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(DerWriter.writeOctetString(new byte[3]))));   // an element that is not a record
        byte[] bad = DerWriter.writeTlv(CTX_COLLECTION, content);
        Run r = readAll(cat(goodStream(1, 2), bad), 3);
        assertEquals(2, r.delivered);
        assertNotNull(r.failure, "the [16] interior is checked only when that item is read");
        assertEquals(2, r.constructed);
        assertEquals(0, r.callbacksFired);
    }

    // ------------------------------------------------------------------ what is accepted today

    @Test
    void anUnreadMalformedTrailingItemIsAcceptedToday() throws Exception {
        byte[] bad = {(byte) 0xA1, 0x20, 0x30, 0x03};         // truncated, never read
        byte[] stream = cat(goodStream(1, 2), bad);
        Run r = readAll(stream, 2);                           // the dispatcher reads only what the method needs
        assertNull(r.failure, "today nothing looks at an item nobody reads (B2 rejects the whole stream up front)");
        assertEquals(2, r.delivered);
        assertEquals(1, r.callbacksFired, "and the unit commits normally");
    }

    @Test
    void aTrailingGoodItemNobodyReadsIsAlsoAcceptedToday() throws Exception {
        Run r = readAll(goodStream(1, 2, 3), 2);
        assertNull(r.failure);
        assertEquals(2, r.constructed, "the third item is never constructed");
    }

    @Test
    void oversizeInputIsAllOrNothingToday() throws Exception {
        byte[] stream = goodStream(1, 2, 3);
        assertThrows(IOException.class, () -> new DerMarshalInputStream(new ByteArrayInputStream(stream),
                au.net.zeus.jgdms.der.getarg.ResolutionContext.NONE, au.net.zeus.jgdms.der.DerInputLimits.maxBytes(stream.length - 1)));
        assertEquals(0, CONSTRUCTED.get(), "the cap is enforced while buffering: nothing is constructed");
    }

    @Test
    void aBadVersionOctetIsRejectedUpFrontToday() throws Exception {
        byte[] stream = goodStream(1);
        stream[2] = 0x7F;                                      // unsupported stream-format version
        assertThrows(IOException.class, () -> new DerMarshalInputStream(new ByteArrayInputStream(stream)));
        assertEquals(0, CONSTRUCTED.get());
    }

    // ------------------------------------------------------------------ the DGC finding, at the stream seam

    @Test
    void completionCallbacksOfDeliveredObjectsAreDroppedWhenALaterItemFails() throws Exception {
        List<String> log = new ArrayList<>();
        byte[] bad = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(emptyHierarchy()), DerWriter.writeTlv(REF, new byte[32]), DerWriter.writeUtf8String(FORMAT))));
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(cat(goodStream(1, 2), bad)));
        in.registerValidation(() -> log.add("dirty issued"), 0);
        in.readObject();
        in.readObject();
        assertThrows(IOException.class, in::readObject);
        in.close();                                            // what the dispatcher's finally does on the failure path
        assertEquals(List.of(), log, "no lease may be issued for objects thrown away with the stream");
    }
}
