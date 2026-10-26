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

package au.net.zeus.jgdms.der;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B1: the item-framed bounded read used by the object-stream reader. Same bytes as the plain bounded read for every stream
 * it accepts; refuses an item that declares more than the budget has left BEFORE buffering any of it.
 */
class DerFramedReadTest {

    /** Counts the bytes actually pulled from the stream and delivers at most {@code chunk} per read. */
    private static final class Counting extends InputStream {
        final byte[] data; final int chunk; int pos; long avail;
        Counting(byte[] data, int chunk, long avail) { this.data = data; this.chunk = chunk; this.avail = avail; }
        @Override public int read() { return pos < data.length ? data[pos++] & 0xFF : -1; }
        @Override public int read(byte[] b, int off, int len) {
            if (pos >= data.length) return -1;
            int n = Math.min(Math.min(len, chunk), data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
        @Override public int available() { return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, avail)); }
    }

    private static byte[] item(int contentLen) {
        return DerWriter.writeOctetString(new byte[contentLen]);
    }

    private static byte[] cat(byte[]... p) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] x : p) o.writeBytes(x);
        return o.toByteArray();
    }

    @Test
    void framedStreamsReadIdenticallyToThePlainRead() throws Exception {
        byte[] s = cat(new byte[] {(byte) 0x8F, 1, 1}, item(0), item(5), item(127), item(128), item(255), item(256), item(70000),
                DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, 31), new byte[40]),      // high-tag-number item
                DerWriter.writeSequence(List.of(item(3))));
        for (int chunk : new int[] {1, 7, 4096, 1 << 20}) {
            assertArrayEquals(s, DerInputLimits.readFramedBounded(new Counting(s, chunk, 0), 1 << 20), "chunk " + chunk);
            assertArrayEquals(DerInputLimits.readAllBytesBounded(new Counting(s, chunk, 0), 1 << 20),
                    DerInputLimits.readFramedBounded(new Counting(s, chunk, 0), 1 << 20));
        }
    }

    @Test
    void theBoundaryTrioHolds() throws Exception {
        for (int max : new int[] {10, 255, 256, 257, 1000, 65536}) {
            byte[] atMax = new byte[max];
            Arrays.fill(atMax, (byte) 0);
            // one item filling the budget exactly: header (2 or 3 or 4 octets) + content
            byte[] s = fill(max);
            assertEquals(max, s.length);
            assertArrayEquals(s, DerInputLimits.readFramedBounded(new Counting(s, 33, 0), max), "at max=" + max);
            byte[] over = cat(s, new byte[] {0x05});                       // one byte more
            assertThrows(IOException.class, () -> DerInputLimits.readFramedBounded(new Counting(over, 33, 0), max), "over max=" + max);
            byte[] under = fill(max - 1);
            assertArrayEquals(under, DerInputLimits.readFramedBounded(new Counting(under, 33, 0), max), "under max=" + max);
        }
    }

    /** A single OCTET STRING item of exactly {@code total} bytes on the wire. */
    private static byte[] fill(int total) {
        for (int content = Math.max(0, total - 5); content <= total; content++) {
            byte[] it = item(content);
            if (it.length == total) return it;
        }
        throw new AssertionError("cannot build an item of " + total);
    }

    @Test
    void anItemDeclaringMoreThanTheBudgetIsRefusedHavingReadOnlyItsHeader() {
        int max = 1 << 20;
        byte[] header = {0x04, (byte) 0x84, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};       // declares ~2 GiB
        byte[] stream = cat(header, new byte[max + 100]);
        Counting c = new Counting(stream, 4096, 0);
        IOException e = assertThrows(IOException.class, () -> DerInputLimits.readFramedBounded(c, max));
        assertTrue(e.getMessage().contains("declares"), e.getMessage());
        assertTrue(c.pos <= header.length + 128, "no more than one transport buffer beyond the header may have been pulled: " + c.pos);
        // the plain read, by contrast, buffers up to max + 1 before it can say no
        Counting plain = new Counting(stream, 4096, 0);
        assertThrows(IOException.class, () -> DerInputLimits.readAllBytesBounded(plain, max));
        assertTrue(plain.pos > max, "plain read pulled " + plain.pos);
    }

    @Test
    void anItemThatFitsOnlyBecauseEarlierItemsAreCountedIsStillBounded() {
        int max = 1000;
        byte[] first = item(600);                                         // 604 bytes
        byte[] second = item(500);                                        // declares 500 with 396 left
        Counting c = new Counting(cat(first, second), 64, 0);
        assertThrows(IOException.class, () -> DerInputLimits.readFramedBounded(c, max));
        assertTrue(c.pos <= first.length + 4 + 128, "stopped at the second header (04 82 01 F4), one buffer at most beyond it: " + c.pos);
    }

    @Test
    void truncatedFinalItemIsKeptAsThePlainReadKeepsIt() throws Exception {
        byte[] s = cat(item(10), Arrays.copyOf(item(100), 40));          // declares 100, carries 36
        assertArrayEquals(s, DerInputLimits.readFramedBounded(new Counting(s, 9, 0), 1000));
        assertArrayEquals(s, DerInputLimits.readAllBytesBounded(new Counting(s, 9, 0), 1000));
    }

    @Test
    void streamsThatCannotBeFramedAreBufferedUnderTheSameCap() throws Exception {
        byte[] garbage = cat(item(3), new byte[] {0x04, (byte) 0x80, 1, 2, 3, 4, 5}, new byte[50]);   // indefinite length
        assertArrayEquals(garbage, DerInputLimits.readFramedBounded(new Counting(garbage, 5, 0), 1000));
        assertThrows(IOException.class, () -> DerInputLimits.readFramedBounded(new Counting(garbage, 5, 0), garbage.length - 1));
        byte[] reserved = cat(new byte[] {0x04, (byte) 0xFF}, new byte[20]);
        assertArrayEquals(reserved, DerInputLimits.readFramedBounded(new Counting(reserved, 5, 0), 100));
        byte[] hugeTag = cat(new byte[] {(byte) 0x1F, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, 1}, new byte[20]);
        assertArrayEquals(hugeTag, DerInputLimits.readFramedBounded(new Counting(hugeTag, 5, 0), 100));
    }

    @Test
    void emptyAndHeaderOnlyStreams() throws Exception {
        assertEquals(0, DerInputLimits.readFramedBounded(new ByteArrayInputStream(new byte[0]), 10).length);
        byte[] cut = {0x04};                                              // EOF inside a header
        assertArrayEquals(cut, DerInputLimits.readFramedBounded(new ByteArrayInputStream(cut), 10));
        byte[] cut2 = {0x04, (byte) 0x82, 0x01};
        assertArrayEquals(cut2, DerInputLimits.readFramedBounded(new ByteArrayInputStream(cut2), 10));
    }

    @Test
    void availableHintCannotBypassTheLimit() throws Exception {
        byte[] s = fill(300);
        assertArrayEquals(s, DerInputLimits.readFramedBounded(new Counting(s, 64, Integer.MAX_VALUE), 300));
        assertArrayEquals(s, DerInputLimits.readFramedBounded(new Counting(s, 64, -5), 300));
        assertThrows(IOException.class, () -> DerInputLimits.readFramedBounded(new Counting(cat(s, new byte[1]), 64, Integer.MAX_VALUE), 300));
    }

    @Test
    void capDisabledFallsBackToReadAll() throws Exception {
        byte[] s = fill(50);
        assertArrayEquals(s, DerInputLimits.readFramedBounded(new ByteArrayInputStream(s), Integer.MAX_VALUE));
    }

    /**
     * Differential against the plain read over random byte soup and random framed streams: whenever the plain read
     * returns bytes the framed read returns the same bytes, EXCEPT for the one deliberate difference (an item whose
     * declared length exceeds what is left, which the framed read refuses with "declares"); whenever the plain read
     * refuses (oversize) the framed read refuses too.
     */
    @Test
    void differentialAgainstThePlainRead() throws Exception {
        Random r = new Random(0xB1);
        int sameBytes = 0, deliberate = 0, bothRefuse = 0;
        for (int i = 0; i < 20000; i++) {
            byte[] data;
            if (r.nextBoolean()) {
                data = new byte[r.nextInt(400)];
                r.nextBytes(data);
            } else {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                for (int k = r.nextInt(6); k > 0; k--) o.writeBytes(item(r.nextInt(300)));
                data = o.toByteArray();
                if (r.nextInt(3) == 0 && data.length > 0) data = Arrays.copyOf(data, r.nextInt(data.length + 1));   // truncate
                if (r.nextInt(3) == 0) { byte[] t = new byte[r.nextInt(20)]; r.nextBytes(t); data = cat(data, t); }  // trailing noise
            }
            int max = 1 + r.nextInt(500);
            byte[] plain = null, framed = null;
            IOException plainEx = null, framedEx = null;
            try { plain = DerInputLimits.readAllBytesBounded(new Counting(data, 1 + r.nextInt(50), 0), max); } catch (IOException e) { plainEx = e; }
            try { framed = DerInputLimits.readFramedBounded(new Counting(data, 1 + r.nextInt(50), 0), max); } catch (IOException e) { framedEx = e; }
            if (plainEx != null) {
                assertNotNull(framedEx, "plain refused (oversize) but framed accepted: len=" + data.length + " max=" + max);
                bothRefuse++;
            } else if (framedEx == null) {
                assertArrayEquals(plain, framed);
                sameBytes++;
            } else {
                assertTrue(framedEx.getMessage().contains("declares"), "the only permitted difference: " + framedEx.getMessage());
                assertTrue(plain.length <= max);
                deliberate++;
            }
        }
        assertTrue(sameBytes > 1000 && deliberate > 20 && bothRefuse > 1000,
                "the generator must exercise every branch: " + sameBytes + "/" + deliberate + "/" + bothRefuse);
    }

    @Test
    void theObjectStreamReaderUsesTheFramedReadButRecordCaptureDoesNot() throws Exception {
        int max = 4096;
        byte[] header = {0x04, (byte) 0x84, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] hostile = cat(new byte[] {(byte) 0x8F, 1, 1}, header, new byte[max]);
        Counting c = new Counting(hostile, 512, 0);
        assertThrows(IOException.class, () -> new au.net.zeus.jgdms.der.stream.DerMarshalInputStream(c,
                au.net.zeus.jgdms.der.getarg.ResolutionContext.NONE, DerInputLimits.maxBytes(max)));
        assertTrue(c.pos <= 3 + header.length + 128, "the object-stream reader refused at the header, one transport buffer at most beyond it: " + c.pos);
        // a record-level capture is not item-framed: the same bytes are buffered under the plain cap and left to fail later
        byte[] notFramed = cat(header, new byte[100]);
        assertDoesNotThrow(() -> au.net.zeus.jgdms.der.stream.DerMarshalInputStream.recordLevelCapture(
                new ByteArrayInputStream(notFramed), au.net.zeus.jgdms.der.getarg.ResolutionContext.NONE));
    }

    @Test
    void aStalledItemPinsOnlyWhatActuallyArrived() throws Exception {
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        int max = 16 << 20;
        // declares max - 5 content bytes (so it passes the budget check), then delivers 10 and stops
        byte[] header = {0x04, (byte) 0x84, (byte) ((max - 5 - 5) >>> 24), (byte) ((max - 10) >>> 16), (byte) ((max - 10) >>> 8), (byte) (max - 10)};
        byte[] s = cat(header, new byte[10]);
        long a0 = mx.getCurrentThreadAllocatedBytes();
        byte[] out = DerInputLimits.readFramedBounded(new Counting(s, 1, 0), max);
        long allocated = mx.getCurrentThreadAllocatedBytes() - a0;
        assertArrayEquals(s, out);
        assertTrue(allocated < 1 << 20, "a stalled declaration of ~16 MiB allocated " + allocated + " bytes");
    }

    @Test
    void headerOnlyItemsDoNotCostOneTransportReadPerOctet() throws Exception {
        byte[] two = {0x05, 0x00};                                    // 2-byte items
        byte[] s = new byte[2 * 50000];
        for (int i = 0; i < 50000; i++) System.arraycopy(two, 0, s, 2 * i, 2);
        final int[] reads = {0};
        InputStream counting = new ByteArrayInputStream(s) {
            @Override public synchronized int read() { reads[0]++; return super.read(); }
            @Override public synchronized int read(byte[] b, int o, int l) { reads[0]++; return super.read(b, o, l); }
        };
        assertArrayEquals(s, DerInputLimits.readFramedBounded(counting, 1 << 20));
        assertTrue(reads[0] < s.length / 100, "transport reads: " + reads[0] + " for " + s.length + " bytes");
    }

    @Test
    void tinyAndZeroCapsBehaveLikeThePlainRead() throws Exception {
        for (int max : new int[] {0, 1, 2, 5, 100, 255}) {
            for (int len : new int[] {0, 1, 2, 5, 6, 100, 101, 255, 256, 300}) {
                byte[] data = new byte[len];
                new Random(max * 31L + len).nextBytes(data);
                byte[] plain = null, framed = null;
                boolean plainFail = false, framedFail = false;
                try { plain = DerInputLimits.readAllBytesBounded(new Counting(data, 3, 0), max); } catch (IOException e) { plainFail = true; }
                try { framed = DerInputLimits.readFramedBounded(new Counting(data, 3, 0), max); } catch (IOException e) { framedFail = true; }
                if (plainFail) assertTrue(framedFail, "max=" + max + " len=" + len);
                else if (!framedFail) assertArrayEquals(plain, framed, "max=" + max + " len=" + len);
            }
        }
    }
}
