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
import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link DerMarshalOutputStream} and
 * {@link DerMarshalInputStream} (JGDMS-STD-008 sec.15, Increment 1).
 *
 * <h2>Test inventory</h2>
 * <ol>
 *   <li>Primitive round-trips: boolean, byte (neg + boundary), short, int
 *       (MIN/MAX), long, writeUTF/readUTF, write(byte[])/readFully.</li>
 *   <li>Null object round-trip.</li>
 *   <li>Flat {@code @AtomicSerial} fixture ({@link VersionedRecord}) round-trip.</li>
 *   <li>{@code String} and {@code byte[]} round-trips through
 *       {@code writeObject/readObject}.</li>
 *   <li>Shared-reference test: same {@code @AtomicSerial} instance written twice;
 *       second item encodes as a back-reference (discriminating assertion on stream
 *       length).</li>
 *   <li>Mixed-sequence mimicking a JERI call: {@code writeLong(hash)},
 *       {@code writeObject(@AtomicSerial arg)}, {@code writeInt(primitive arg)}.</li>
 *   <li>Fail-secure: writing a non-{@code @AtomicSerial}, non-value object
 *       ({@code new Object()}) throws {@link UnsupportedOperationException}.</li>
 *   <li>Deferred-type guards: {@code writeFloat}, {@code writeDouble},
 *       {@code writeChar} throw {@link UnsupportedOperationException}.</li>
 * </ol>
 */
class DerObjectStreamTest {

    // =========================================================================
    // 1. Primitive round-trips
    // =========================================================================

    @Test
    void roundTrip_boolean_true() throws Exception {
        byte[] bytes = encode(out -> out.writeBoolean(true));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertTrue(in.readBoolean());
        }
    }

    @Test
    void roundTrip_boolean_false() throws Exception {
        byte[] bytes = encode(out -> out.writeBoolean(false));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertFalse(in.readBoolean());
        }
    }

    @Test
    void roundTrip_byte_positive() throws Exception {
        byte[] bytes = encode(out -> out.writeByte(42));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals((byte) 42, in.readByte());
        }
    }

    @Test
    void roundTrip_byte_negative() throws Exception {
        byte[] bytes = encode(out -> out.writeByte(-1));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals((byte) -1, in.readByte());
        }
    }

    @Test
    void roundTrip_byte_min() throws Exception {
        byte[] bytes = encode(out -> out.writeByte(Byte.MIN_VALUE));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(Byte.MIN_VALUE, in.readByte());
        }
    }

    @Test
    void roundTrip_byte_max() throws Exception {
        byte[] bytes = encode(out -> out.writeByte(Byte.MAX_VALUE));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(Byte.MAX_VALUE, in.readByte());
        }
    }

    @Test
    void roundTrip_short() throws Exception {
        byte[] bytes = encode(out -> out.writeShort(Short.MIN_VALUE));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(Short.MIN_VALUE, in.readShort());
        }
    }

    @Test
    void roundTrip_int_positive() throws Exception {
        byte[] bytes = encode(out -> out.writeInt(123456));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(123456, in.readInt());
        }
    }

    @Test
    void roundTrip_int_min() throws Exception {
        byte[] bytes = encode(out -> out.writeInt(Integer.MIN_VALUE));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(Integer.MIN_VALUE, in.readInt());
        }
    }

    @Test
    void roundTrip_int_max() throws Exception {
        byte[] bytes = encode(out -> out.writeInt(Integer.MAX_VALUE));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(Integer.MAX_VALUE, in.readInt());
        }
    }

    @Test
    void roundTrip_long() throws Exception {
        long val = Long.MIN_VALUE;
        byte[] bytes = encode(out -> out.writeLong(val));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(val, in.readLong());
        }
    }

    @Test
    void roundTrip_writeUTF_readUTF() throws Exception {
        String s = "hello, DER stream!";
        byte[] bytes = encode(out -> out.writeUTF(s));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(s, in.readUTF());
        }
    }

    @Test
    void roundTrip_writeUTF_empty() throws Exception {
        byte[] bytes = encode(out -> out.writeUTF(""));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals("", in.readUTF());
        }
    }

    @Test
    void roundTrip_byteArray() throws Exception {
        byte[] original = {1, 2, 3, (byte) 0xFF, (byte) 0x80};
        byte[] bytes = encode(out -> out.write(original));
        byte[] result = new byte[original.length];
        decode(bytes, in -> { in.readFully(result); return null; });
        assertArrayEquals(original, result);
    }

    @Test
    void roundTrip_byteArray_empty() throws Exception {
        byte[] original = new byte[0];
        byte[] bytes = encode(out -> out.write(original));
        byte[] result = new byte[0];
        decode(bytes, in -> { in.readFully(result); return null; });
        assertArrayEquals(original, result);
    }

    // =========================================================================
    // 2. Null object round-trip
    // =========================================================================

    @Test
    void roundTrip_null() throws Exception {
        byte[] bytes = encode(out -> out.writeObject(null));
        Object result = decode(bytes, in -> in.readObject());
        assertNull(result);
    }

    // =========================================================================
    // 3. Flat @AtomicSerial fixture round-trip
    // =========================================================================

    @Test
    void roundTrip_atomicSerial_flat() throws Exception {
        VersionedRecord orig = new VersionedRecord(42, "label-val", "extra-val");
        byte[] bytes = encode(out -> out.writeObject(orig));
        Object result = decode(bytes, in -> in.readObject());
        assertInstanceOf(VersionedRecord.class, result);
        assertEquals(orig, result);
    }

    // =========================================================================
    // 4. String and byte[] through writeObject/readObject
    // =========================================================================

    @Test
    void roundTrip_stringAsObject() throws Exception {
        String s = "a string via writeObject";
        byte[] bytes = encode(out -> out.writeObject(s));
        Object result = decode(bytes, in -> in.readObject());
        assertEquals(s, result);
    }

    @Test
    void roundTrip_byteArrayAsObject() throws Exception {
        byte[] orig = {10, 20, 30};
        byte[] bytes = encode(out -> out.writeObject(orig));
        Object result = decode(bytes, in -> in.readObject());
        assertArrayEquals(orig, (byte[]) result);
    }

    // =========================================================================
    // 5. Shared-reference test (discriminating)
    // =========================================================================

    /**
     * Write the same {@code @AtomicSerial} instance twice. The second item MUST
     * be a back-reference [2] TLV, not a full [1] record.
     *
     * <p>Discriminating assertion: the stream produced by writing the object twice
     * is strictly SHORTER than the stream produced by writing two independent
     * records of the same content. The back-reference encodes as a tiny context TLV
     * (tag + length + INTEGER); a full record includes a complete
     * {@code MarshalledInstanceRecord} SEQUENCE (schema chain + payload). For any
     * non-trivial {@code @AtomicSerial} object the difference is large.
     */
    @Test
    void sharedReference_secondWriteIsBackReference() throws Exception {
        VersionedRecord v = new VersionedRecord(1, "shared", "ref");

        // Stream 1: same instance twice (second should be a back-reference)
        byte[] sharedStream = encode(out -> {
            out.writeObject(v);
            out.writeObject(v);
        });

        // Stream 2: two DIFFERENT instances with identical content (both full records)
        VersionedRecord v2 = new VersionedRecord(1, "shared", "ref");
        byte[] twoFullRecords = encode(out -> {
            out.writeObject(v);
            out.writeObject(v2);
        });

        // The shared-reference stream MUST be shorter (back-ref is tiny vs full record)
        assertTrue(sharedStream.length < twoFullRecords.length,
                "shared stream (" + sharedStream.length + " bytes) should be shorter than "
                + "two-full-record stream (" + twoFullRecords.length + " bytes)");

        // Both objects round-trip correctly
        VersionedRecord[] results = new VersionedRecord[2];
        decode(sharedStream, in -> {
            results[0] = (VersionedRecord) in.readObject();
            results[1] = (VersionedRecord) in.readObject();
            return null;
        });
        assertEquals(v, results[0]);
        assertEquals(v, results[1]);
    }

    // =========================================================================
    // 6. Mixed-sequence: method hash + @AtomicSerial arg + int arg
    // =========================================================================

    @Test
    void mixedSequence_methodHashAndArgs() throws Exception {
        long methodHash = 0x1234567890ABCDEFL;
        VersionedRecord arg1 = new VersionedRecord(7, "arg-label", "arg-extra");
        int arg2 = 99;

        byte[] bytes = encode(out -> {
            out.writeLong(methodHash);
            out.writeObject(arg1);
            out.writeInt(arg2);
        });

        long readHash = decode(bytes, in -> {
            long h = in.readLong();
            Object a1 = in.readObject();
            int a2 = in.readInt();
            assertEquals(arg1, a1, "arg1 mismatch");
            assertEquals(arg2, a2, "arg2 mismatch");
            return h;
        });
        assertEquals(methodHash, readHash);
    }

    // =========================================================================
    // 7. Fail-secure: non-@AtomicSerial, non-value object
    // =========================================================================

    @Test
    void failSecure_plainObject() {
        assertThrows(UnsupportedOperationException.class, () ->
                encode(out -> out.writeObject(new Object())));
    }

    @Test
    void failSecure_ArrayList() {
        assertThrows(UnsupportedOperationException.class, () ->
                encode(out -> out.writeObject(new ArrayList<>())));
    }

    // =========================================================================
    // 8. Deferred-type guards
    // =========================================================================

    @Test
    void deferred_writeFloat() throws Exception {
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(new ByteArrayOutputStream())) {
            assertThrows(UnsupportedOperationException.class, () -> out.writeFloat(1.0f));
        }
    }

    @Test
    void deferred_writeDouble() throws Exception {
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(new ByteArrayOutputStream())) {
            assertThrows(UnsupportedOperationException.class, () -> out.writeDouble(1.0));
        }
    }

    @Test
    void deferred_writeChar() throws Exception {
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(new ByteArrayOutputStream())) {
            assertThrows(UnsupportedOperationException.class, () -> out.writeChar('A'));
        }
    }

    @Test
    void deferred_readFloat() throws Exception {
        // Need a non-empty stream to avoid premature exhaustion
        byte[] bytes = encode(out -> out.writeBoolean(true));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertThrows(UnsupportedOperationException.class, in::readFloat);
        }
    }

    @Test
    void deferred_readDouble() throws Exception {
        byte[] bytes = encode(out -> out.writeBoolean(true));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertThrows(UnsupportedOperationException.class, in::readDouble);
        }
    }

    @Test
    void deferred_readChar() throws Exception {
        byte[] bytes = encode(out -> out.writeBoolean(true));
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            assertThrows(UnsupportedOperationException.class, in::readChar);
        }
    }

    // =========================================================================
    // 9. Cycle / forward-reference rejection (security -- NO cycles by design, sec.15.3)
    // =========================================================================

    /**
     * A back-reference to a not-yet-registered handle -- a FORWARD reference, the shape a
     * cycle necessarily requires -- MUST be rejected. The read side registers an object in
     * the handle table only AFTER it is fully constructed, so a cyclic/forward-referencing
     * stream is undecodable by construction (STD-008 sec.15.3). This proves the security
     * property (no partially-constructed object is ever exposed via a handle), not merely
     * the happy path.
     */
    @Test
    void forwardReference_isRejected() {
        byte[] malicious = derBackRef(0); // handle 0, but nothing has been registered yet
        IOException ex = assertThrows(IOException.class, () ->
                decode(malicious, DerMarshalInputStream::readObject));
        assertTrue(ex.getMessage().contains("back-reference"),
                "forward/cyclic back-reference must be rejected fail-secure: " + ex.getMessage());
    }

    /** An in-range object followed by an out-of-range back-reference is rejected. */
    @Test
    void outOfRangeBackReference_isRejected() throws Exception {
        byte[] valid = encode(out -> out.writeObject(new VersionedRecord(1, "a", "b")));
        byte[] malicious = concat(valid, derBackRef(5)); // only handle 0 exists
        assertThrows(IOException.class, () ->
                decode(malicious, in -> { in.readObject(); return in.readObject(); }));
    }

    /** Builds a raw [2] context-tagged back-reference TLV with the given handle. */
    private static byte[] derBackRef(int handle) {
        return DerWriter.writeTlv(
                new Tag(Tag.CLASS_CONTEXT, false, 2),
                DerWriter.writeInteger(BigInteger.valueOf(handle)));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // =========================================================================
    // Helper lambdas / utilities
    // =========================================================================

    @FunctionalInterface
    interface WriteAction {
        void write(DerMarshalOutputStream out) throws Exception;
    }

    @FunctionalInterface
    interface ReadAction<T> {
        T read(DerMarshalInputStream in) throws Exception;
    }

    /**
     * Encodes using a {@link DerMarshalOutputStream} into a
     * {@link ByteArrayOutputStream} and returns the bytes.
     */
    private byte[] encode(WriteAction action) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(baos)) {
            action.write(out);
        }
        return baos.toByteArray();
    }

    /**
     * Decodes using a {@link DerMarshalInputStream} from the given bytes and
     * returns the result of {@code action}.
     */
    private <T> T decode(byte[] bytes, ReadAction<T> action) throws Exception {
        try (DerMarshalInputStream in =
                new DerMarshalInputStream(new ByteArrayInputStream(bytes))) {
            return action.read(in);
        }
    }
}
