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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B0 pinned TODAY's admission behaviour; B2 flipped it. The class keeps its name so the diff of this file IS the proof of the
 * deliberate change. For a stream {@code [good1, good2, bad]} built through a public {@code DerMarshalInputStream}
 * constructor:
 * <ul>
 *   <li>B0: good1 and good2 were DELIVERED (constructors ran) and only the read of {@code bad} rejected.
 *       B2: the CONSTRUCTOR rejects the stream; nothing is delivered and no constructor runs.</li>
 *   <li>B0: an unread malformed trailing item was ACCEPTED. B2: rejected (an intended, documented change of the accept set,
 *       SOW B.2.7). An unread GOOD trailing item is still accepted.</li>
 *   <li>The DGC property is unchanged. It is vacuous for wire-level rejects (nothing was ever registered) and is pinned for a
 *       Layer-2 reject, which admission cannot see (B.2.3): there objects ARE delivered before the reject, and no callback runs.</li>
 * </ul>
 * Reject classes whose stream-level vector needs an input window larger than a unit test should build (per-item and cumulative
 * reconstitution ceilings, table ceilings, chain-record ceilings, depth) are covered at the walker level and, through the same
 * admission mechanism, by {@code DerStreamWholeStreamAdmissionTest}.
 */
class DerStreamAdmissionCharacterisationTest {

    static final AtomicInteger CONSTRUCTED = new AtomicInteger();

    /** Counts every deserialization constructor run: "a constructor ran" is what B2 makes zero for a rejected stream. */
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
            if (this.n == 99) throw new java.io.InvalidObjectException("refused by the constructor (Layer 2)");
        }
    }

    static final Tag CTX_ATOMIC = new Tag(Tag.CLASS_CONTEXT, true, 1);
    static final Tag CTX_PROXY = new Tag(Tag.CLASS_CONTEXT, true, 8);
    static final Tag CTX_COLLECTION = new Tag(Tag.CLASS_CONTEXT, true, 16);
    static final Tag REF = new Tag(Tag.CLASS_CONTEXT, false, 1);
    static final String FORMAT = "JGDMS-STD-006/ATOMIC-DER";

    @BeforeEach
    void reset() {
        CONSTRUCTED.set(0);
    }

    static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }

    /** Stream bytes for the given good objects: version octet + one item each, chain sent once (then chainRefs). */
    static byte[] goodStream(int... ns) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        for (int n : ns) c.writeObject(new Counted(n));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    /** Just the item TLV(s) of a stream, i.e. without the 3-byte version octet. */
    static byte[] itemsOf(byte[] stream) {
        return java.util.Arrays.copyOfRange(stream, 3, stream.length);
    }

    static byte[] emptyHierarchy() {
        return DerWriter.writeSequence(List.of());
    }

    /** An item whose chainRef names a digest never sent. */
    static byte[] unknownDigestItem() {
        return DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(emptyHierarchy()), DerWriter.writeTlv(REF, new byte[32]), DerWriter.writeUtf8String(FORMAT))));
    }

    /** What reading {@code bytes} through a public constructor does. */
    record Run(int delivered, Throwable failure, int constructed, int callbacksFired) {}

    static Run readAll(byte[] bytes, int maxItems) throws Exception {
        AtomicInteger fired = new AtomicInteger();
        DerMarshalInputStream in;
        try {
            in = new DerMarshalInputStream(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            return new Run(0, e, CONSTRUCTED.get(), 0);           // B2: rejected before anything is delivered
        }
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

    static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable x = t; x != null; x = x.getCause()) sb.append(x.getClass().getSimpleName()).append(": ").append(x.getMessage()).append(" | ");
        return sb.toString();
    }

    private static void assertRejectedWhole(byte[] stream, String messagePart) throws Exception {
        Run r = readAll(stream, 3);
        assertNotNull(r.failure, "the stream must be rejected");
        assertEquals(0, r.delivered, "B0 pinned 2 delivered before the reject; B2 delivers nothing");
        assertEquals(0, r.constructed, "B0 pinned 2 constructors run; B2 runs none");
        assertEquals(0, r.callbacksFired);
        String all = chain(r.failure);
        assertTrue(all.contains(messagePart), "expected '" + messagePart + "' in: " + all);
    }

    // ------------------------------------------------------------------ per-item reject classes: now whole-stream rejects

    @Test
    void unknownDigest_rejectsTheWholeStream() throws Exception {
        assertRejectedWhole(cat(goodStream(1, 2), unknownDigestItem()), "unknown-digest");
    }

    @Test
    void duplicateFullChain_rejectsTheWholeStream() throws Exception {
        byte[] first = goodStream(1);                       // carries the fullChain
        byte[] dup = itemsOf(goodStream(9));                // a fresh stream's first item: ALSO a fullChain for the same identity
        assertRejectedWhole(cat(first, refItem(2), dup), "duplicate fullChain");
    }

    /** A second item of the same class as a real encoder writes it: the chainRef form. */
    private static byte[] refItem(int n) throws IOException {
        byte[] two = goodStream(1, n);
        byte[] one = goodStream(1);
        return java.util.Arrays.copyOfRange(two, one.length, two.length);
    }

    @Test
    void wrongArmTag_rejectsTheWholeStream() throws Exception {
        byte[] bad = DerWriter.writeTlv(CTX_ATOMIC, DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(emptyHierarchy()),
                DerWriter.writeOctetString(new byte[40]),         // a universal OCTET STRING where a SchemaChainRef belongs
                DerWriter.writeUtf8String(FORMAT))));
        assertRejectedWhole(cat(goodStream(1, 2), bad), "SchemaChainRef");
    }

    @Test
    void nonCanonicalLengthFraming_rejectsTheWholeStream() throws Exception {
        byte[] bad = {(byte) 0xA1, (byte) 0x81, 0x05, 0x30, 0x03, 0x04, 0x01, 0x00};   // length 5 in long form
        assertRejectedWhole(cat(goodStream(1, 2), bad), "Non-canonical");
    }

    @Test
    void truncatedItem_rejectsTheWholeStream() throws Exception {
        byte[] bad = {(byte) 0xA1, 0x20, 0x30, 0x03};         // declares 32 bytes, carries 2
        assertRejectedWhole(cat(goodStream(1, 2), bad), "overruns");
    }

    @Test
    void proxyInteriorReject_rejectsTheWholeStream() throws Exception {
        byte[] bad = DerWriter.writeTlv(CTX_PROXY, new byte[] {0x02, 0x01, 0x05, 0x00});    // interface count 5, nothing follows
        assertRejectedWhole(cat(goodStream(1, 2), bad), "proxy");
    }

    @Test
    void collectionInteriorReject_rejectsTheWholeStream() throws Exception {
        byte[] content = cat(DerWriter.writeUtf8String("list:@AtomicSerial"),
                DerWriter.writeSequence(List.of(DerWriter.writeOctetString(new byte[3]))));   // an element that is not a record
        assertRejectedWhole(cat(goodStream(1, 2), DerWriter.writeTlv(CTX_COLLECTION, content)), "nested @AtomicSerial site");
    }

    @Test
    void anUnknownTopLevelTagIsRejectedUpFront() throws Exception {
        byte[] bad = {(byte) 0x9F, 0x21, 0x01, 0x00};          // [33] primitive: not an item tag
        assertRejectedWhole(cat(goodStream(1, 2), bad), "unexpected top-level tag");
        byte[] second15 = {(byte) 0x8F, 0x01, 0x01};           // a second version octet mid-stream
        assertRejectedWhole(cat(goodStream(1, 2), second15), "unexpected top-level tag");
    }

    // ------------------------------------------------------------------ what is accepted

    @Test
    void anUnreadMalformedTrailingItemIsNowRejected() throws Exception {
        byte[] bad = {(byte) 0xA1, 0x20, 0x30, 0x03};         // truncated, never read
        Run r = readAll(cat(goodStream(1, 2), bad), 2);
        assertNotNull(r.failure, "B0 pinned: accepted when unread. B2 admits the whole stream up front (SOW B.2.7)");
        assertEquals(0, r.delivered);
        assertEquals(0, r.constructed);
    }

    @Test
    void aTrailingGoodItemNobodyReadsIsStillAccepted() throws Exception {
        Run r = readAll(goodStream(1, 2, 3), 2);
        assertNull(r.failure);
        assertEquals(2, r.constructed, "the third item is admitted but never constructed");
    }

    @Test
    void oversizeInputIsAllOrNothing() throws Exception {
        byte[] stream = goodStream(1, 2, 3);
        assertThrows(IOException.class, () -> new DerMarshalInputStream(new ByteArrayInputStream(stream),
                au.net.zeus.jgdms.der.getarg.ResolutionContext.NONE, au.net.zeus.jgdms.der.DerInputLimits.maxBytes(stream.length - 1)));
        assertEquals(0, CONSTRUCTED.get(), "the cap is enforced while buffering: nothing is constructed");
    }

    @Test
    void aBadVersionOctetIsRejectedUpFront() throws Exception {
        byte[] stream = goodStream(1);
        stream[2] = 0x7F;                                      // unsupported stream-format version
        assertThrows(IOException.class, () -> new DerMarshalInputStream(new ByteArrayInputStream(stream)));
        assertEquals(0, CONSTRUCTED.get());
    }

    // ------------------------------------------------------------------ Layer 2 is still per object, bottom-up (SOW B.2.3)

    @Test
    void aLayerTwoRejectStillArrivesAtThatObjectAndDropsTheCompletionCallbacks() throws Exception {
        // 99 is refused by Counted's constructor: wire-level admission cannot see that, so the stream is admitted
        Run r = readAll(goodStream(1, 2, 99), 3);
        assertEquals(2, r.delivered, "admission guarantees structure, not that every object is accepted");
        assertNotNull(r.failure);
        assertTrue(chain(r.failure).contains("refused by the constructor"), chain(r.failure));
        assertEquals(3, r.constructed, "the third constructor ran and refused");
        assertEquals(0, r.callbacksFired, "a failed decode unit still issues no DGC dirty for the delivered objects");
    }
}
