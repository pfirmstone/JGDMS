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

package org.apache.river.api.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Regression coverage for the allocate-before-validate decode denial-of-service
 * fix in {@link AtomicMarshalInputStream} (three sites: the reference-array
 * branch of {@code readNewArray}, {@code readBlockDataLong}, and
 * {@code decodeUTF}).
 *
 * <p>The fix does two things:
 * <ul>
 *   <li>A <em>reference</em> ({@code Object[]}) array declares its element count
 *       on the wire and cannot be built incrementally (its identity is
 *       registered before elements are read, so a cyclic element may point back
 *       at it), so a coarse per-array ceiling {@code MAX_ARRAY_LEN = 2^20} is
 *       applied <em>before</em> allocation. A declared count above the ceiling
 *       is rejected before any memory is committed.</li>
 *   <li>Primitive arrays, raw byte blocks ({@code readBlockDataLong}) and long
 *       UTF strings ({@code decodeUTF}) are read incrementally in bounded chunks
 *       ({@code readPrimitiveArrayChunked} / {@code readBytesChunked}) so a
 *       hostile wire-declared length on a truncated payload fails fast with a
 *       bounded {@link EOFException} at the truncation point instead of forcing
 *       a huge up-front allocation ({@link OutOfMemoryError}). These paths have
 *       no hard ceiling, so legitimately large payloads still round-trip.</li>
 * </ul>
 *
 * <p>Each hostile case is reproduced the way an attacker would: take a real
 * serialized stream produced by {@link AtomicMarshalOutputStream}, patch its
 * length prefix to an excessive value, truncate the payload, and confirm the
 * decoder now fails fast with a bounded exception rather than attempting the
 * allocation. Before the fix these same inputs threw {@link OutOfMemoryError}
 * (or committed a multi-gigabyte allocation) at the patched length.
 */
public class AtomicMarshalInputStreamArrayBoundTest {

    /**
     * Mirror of the package-private {@code AtomicMarshalInputStream.MAX_ARRAY_LEN}
     * reference-array ceiling (2^20 = 1,048,576). Kept in sync deliberately: if
     * the production constant changes, the boundary tests below must be revisited.
     */
    private static final int MAX_ARRAY_LEN = 1 << 20; // 1,048,576

    // ------------------------------------------------------------------
    // round-trip helpers (same machinery the codec uses in production)
    // ------------------------------------------------------------------

    private static byte[] serialize(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new AtomicMarshalOutputStream(baos, null);
        oos.writeObject(o);
        oos.flush();
        return baos.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream in =
                AtomicMarshalInputStream.create(bais, null, false, null, null, false);
        return in.readObject();
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off]     = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    private static void putLong(byte[] b, int off, long v) {
        for (int i = 0; i < 8; i++) b[off + i] = (byte) (v >>> (56 - 8 * i));
    }

    /** Concatenate every message in the cause chain, for tolerant assertions. */
    private static String chainMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable x = t; x != null; x = x.getCause()) {
            sb.append(x.getClass().getName()).append(':').append(x.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    private static boolean chainHas(Throwable t, Class<? extends Throwable> type) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (type.isInstance(x)) return true;
        }
        return false;
    }

    private static final String SENTINEL = "QZQsentinelQZQ";

    /**
     * Builds a real {@code String[]} (reference array) stream, truncates it right
     * after the 4-byte element-count prefix, and patches that prefix to
     * {@code size}. Wire layout at the element boundary is
     * {@code [count:int][TC_STRING][utflen:short][utf bytes...]}, so the count
     * begins 7 bytes before the first sentinel byte.
     */
    private static byte[] referenceArrayWithPatchedCount(int size) throws Exception {
        byte[] full = serialize(new String[]{ SENTINEL });
        int p = indexOf(full, SENTINEL.getBytes("UTF-8"));
        assertTrue("sentinel element not found in stream", p >= 7);
        int countIdx = p - 7;
        byte[] out = Arrays.copyOf(full, countIdx + 4); // drop all element bytes
        putInt(out, countIdx, size);
        return out;
    }

    // ------------------------------------------------------------------
    // 1. Hostile truncated input now fails fast (was OutOfMemoryError)
    // ------------------------------------------------------------------

    /**
     * Site: reference-array branch of {@code readNewArray}. A wire-declared
     * element count far above the ceiling (300,000,000 ~ 2.4 GB of references)
     * is rejected before {@code Array.newInstance} is ever called.
     */
    @Test
    public void referenceArrayExcessiveCountRejectedBeforeAllocation() throws Exception {
        try {
            deserialize(referenceArrayWithPatchedCount(300_000_000));
            fail("expected a bounded exception, not a completed read / OOM");
        } catch (IOException e) {
            String m = chainMessages(e);
            assertTrue("expected DoS rejection, got: " + m, m.contains("denial of service"));
            assertTrue("expected the offending size reported: " + m, m.contains("300000000"));
            assertFalse("must be rejected before reading elements (no EOF): " + m,
                    chainHas(e, EOFException.class));
        }
    }

    /**
     * Site: {@code readBlockDataLong} (TC_BLOCKDATALONG raw-byte block).
     *
     * <p>The registered serializers never emit a &gt;255-byte primitive block, so
     * we start from a real object graph that genuinely reaches the block-data
     * read path <em>inside</em> a budgeted {@code readObject} (a boxed
     * {@link Long}, whose {@code LongSerializer.writeExternal} writes 8 raw bytes
     * as a short-form {@code TC_BLOCKDATA}). We promote that marker to the long
     * form {@code TC_BLOCKDATALONG} with a hostile ~2e9 length prefix and
     * truncate the payload -- exactly the shape a malicious stream would take.
     * The incremental {@code readBytesChunked} then fails fast with EOF instead
     * of committing {@code byte[2e9]} up front.
     */
    @Test
    public void blockDataLongExcessiveLengthFailsFastNotOom() throws Exception {
        // A distinctive 64-bit value so the 8 payload bytes are locatable.
        final long value = 0x1122334455667788L;
        byte[] full = serialize(Long.valueOf(value));
        // Short-form block header inside the graph: TC_BLOCKDATA (0x77), length
        // byte 0x08, then the 8 big-endian value bytes.
        byte[] needle = { 0x77, 0x08,
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88 };
        int idx = indexOf(full, needle);
        assertTrue("short-form block-data header not found in Long graph", idx >= 0);

        int hostileLen = 2_000_000_000; // below MAX_COMBINED_ARRAY_LEN, huge alloc pre-fix
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(full, 0, idx);            // everything up to the block-data marker
        bo.write(0x7a);                    // TC_BLOCKDATALONG
        bo.write((hostileLen >>> 24) & 0xff);
        bo.write((hostileLen >>> 16) & 0xff);
        bo.write((hostileLen >>> 8) & 0xff);
        bo.write(hostileLen & 0xff);
        bo.write(new byte[100]);           // 100 bytes delivered, ~2e9 declared -> truncated
        try {
            deserialize(bo.toByteArray());
            fail("expected EOFException on truncated block-data-long payload");
        } catch (IOException e) {
            assertTrue("expected bounded EOF, got: " + chainMessages(e),
                    chainHas(e, EOFException.class));
        }
    }

    /**
     * Site: {@code decodeUTF} via {@code readNewLongString} (TC_LONGSTRING).
     * The 8-byte length prefix is patched to ~2e9 and the payload truncated;
     * the incremental {@code readBytesChunked} read fails fast with EOF instead
     * of committing {@code byte[2e9]} + {@code char[2e9]} up front.
     */
    @Test
    public void longStringExcessiveLengthFailsFastNotOom() throws Exception {
        char[] c = new char[70000];
        Arrays.fill(c, 'A');
        byte[] full = serialize(new String(c));
        // TC_LONGSTRING (0x7C) sits immediately after the 4-byte stream header,
        // followed by the 8-byte length.
        assertEquals("expected TC_LONGSTRING at offset 4", 0x7c, full[4] & 0xff);
        putLong(full, 5, 2_000_000_000L);
        byte[] truncated = Arrays.copyOf(full, 5000); // far fewer bytes than declared
        try {
            deserialize(truncated);
            fail("expected EOFException on truncated long-string payload");
        } catch (IOException e) {
            assertTrue("expected bounded EOF, got: " + chainMessages(e),
                    chainHas(e, EOFException.class));
        }
    }

    // ------------------------------------------------------------------
    // 4. Exactly-at-boundary correctness for the reference-array ceiling
    // ------------------------------------------------------------------

    /**
     * One element above the ceiling is rejected before allocation, with the DoS
     * diagnostic naming the offending size.
     */
    @Test
    public void referenceArrayJustAboveCeilingIsRejected() throws Exception {
        try {
            deserialize(referenceArrayWithPatchedCount(MAX_ARRAY_LEN + 1));
            fail("expected rejection just above the ceiling");
        } catch (IOException e) {
            String m = chainMessages(e);
            assertTrue("expected DoS rejection, got: " + m, m.contains("denial of service"));
            assertTrue("expected offending size reported: " + m,
                    m.contains(Integer.toString(MAX_ARRAY_LEN + 1)));
            assertFalse("rejection must precede element reads (no EOF): " + m,
                    chainHas(e, EOFException.class));
        }
    }

    /**
     * Exactly at the ceiling the count passes the guard: the array is allocated
     * and element reads begin. Because our crafted stream is truncated after the
     * count, that read hits a bounded {@link EOFException} -- proving the value
     * was <em>accepted</em> (not rejected by the ceiling) while still not
     * over-allocating on a truncated payload.
     */
    @Test
    public void referenceArrayExactlyAtCeilingIsAccepted() throws Exception {
        try {
            deserialize(referenceArrayWithPatchedCount(MAX_ARRAY_LEN));
            fail("expected element-read EOF (truncated payload) after acceptance");
        } catch (IOException e) {
            String m = chainMessages(e);
            assertTrue("ceiling value must be accepted then EOF on elements: " + m,
                    chainHas(e, EOFException.class));
            assertFalse("must NOT be rejected as a DoS at the ceiling: " + m,
                    m.contains("denial of service"));
        }
    }

    // ------------------------------------------------------------------
    // 2. Legitimate large arrays / strings still round-trip (no false reject)
    // ------------------------------------------------------------------

    @Test
    public void largeReferenceArrayRoundTrips() throws Exception {
        String[] a = new String[50_000];
        for (int i = 0; i < a.length; i++) a[i] = "s" + (i % 137);
        String[] r = (String[]) deserialize(serialize(a));
        assertEquals(a.length, r.length);
        assertTrue("String[50000] must round-trip unchanged", Arrays.equals(a, r));
    }

    @Test
    public void largePrimitiveLongArrayRoundTrips() throws Exception {
        long[] a = new long[50_000];
        for (int i = 0; i < a.length; i++) a[i] = (long) i * 1_000_003L;
        long[] r = (long[]) deserialize(serialize(a));
        assertTrue("long[50000] must round-trip unchanged", Arrays.equals(a, r));
    }

    /**
     * A primitive byte array deliberately larger than {@code MAX_ARRAY_LEN}:
     * primitive arrays use the incremental chunked path with no hard ceiling, so
     * a 2,000,000-element array (above the reference-array ceiling) must still
     * round-trip -- the ceiling applies only to reference arrays.
     */
    @Test
    public void largePrimitiveByteArrayAboveCeilingRoundTrips() throws Exception {
        byte[] a = new byte[2_000_000];
        for (int i = 0; i < a.length; i++) a[i] = (byte) (i * 31);
        byte[] r = (byte[]) deserialize(serialize(a));
        assertTrue("byte[2000000] (> ceiling) must round-trip unchanged", Arrays.equals(a, r));
    }

    // ------------------------------------------------------------------
    // 3. Self-referential (cyclic) reference array preserves identity
    // ------------------------------------------------------------------

    /**
     * The reference-array path must still register the array's handle before
     * reading elements so a cyclic element can point back at the array being
     * filled. Identity of the back-reference must be preserved.
     */
    @Test
    public void selfReferentialObjectArrayPreservesIdentity() throws Exception {
        Object[] cyc = new Object[2];
        cyc[0] = "head";
        cyc[1] = cyc; // element points back at the array being filled
        Object[] r = (Object[]) deserialize(serialize(cyc));
        assertEquals(2, r.length);
        assertEquals("head", r[0]);
        assertSame("cyclic self-reference identity must be preserved", r, r[1]);
    }
}
