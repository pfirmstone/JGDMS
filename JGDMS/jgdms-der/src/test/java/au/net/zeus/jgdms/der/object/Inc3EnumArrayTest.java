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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.object.fixtures.ArrayRecord;
import au.net.zeus.jgdms.der.object.fixtures.Chain;
import au.net.zeus.jgdms.der.object.fixtures.ChainArrayHolder;
import au.net.zeus.jgdms.der.object.fixtures.EnumRecord;
import au.net.zeus.jgdms.der.object.fixtures.NestedArrayHolder;
import au.net.zeus.jgdms.der.object.fixtures.NestedValue;
import au.net.zeus.jgdms.der.object.fixtures.NestedValueSub;
import au.net.zeus.jgdms.der.object.fixtures.Status;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B1 inc-3 tests: enums and arrays (STD-008 sec.17).
 *
 * <p>Coverage:
 * <ul>
 *   <li>Enums: round-trip non-null; null enum field; unknown-name decode rejected.</li>
 *   <li>Arrays: round-trip {@code int[]}/{@code long[]}/{@code short[]}/{@code boolean[]};
 *       {@code String[]} with mixed null elements; {@code @AtomicSerial[]} with non-null
 *       elements, null elements, polymorphism, empty array, null array.</li>
 *   <li>Determinism: byte-identical encoding for equal values.</li>
 *   <li>Depth guard for {@code @AtomicSerial[]} elements: the depth is threaded through
 *       array element decoding (not reset to 0 per element -- same hazard as inc-2).</li>
 *   <li>Fail-secure: int[][] rejected; byte[] unchanged; float/double/char still deferred.</li>
 * </ul>
 */
class Inc3EnumArrayTest {

    // =========================================================================
    // ENUM TESTS
    // =========================================================================

    @Test
    void enumField_nonNull_roundTrip() throws Exception {
        EnumRecord original = new EnumRecord("test", Status.ACTIVE);
        SchemaChain.Result chain = SchemaGenerator.generateChain(EnumRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        EnumRecord decoded = ObjectCodec.decodeHierarchy(EnumRecord.class, chain, encoded);
        assertEquals(original, decoded);
        assertEquals(Status.ACTIVE, decoded.getStatus());
    }

    @Test
    void enumField_null_roundTrip() throws Exception {
        EnumRecord original = new EnumRecord("nullable-enum", null);
        SchemaChain.Result chain = SchemaGenerator.generateChain(EnumRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        EnumRecord decoded = ObjectCodec.decodeHierarchy(EnumRecord.class, chain, encoded);
        assertEquals(original, decoded);
        assertNull(decoded.getStatus());
    }

    @Test
    void enumField_allConstants_roundTrip() throws Exception {
        for (Status s : Status.values()) {
            EnumRecord original = new EnumRecord("s=" + s, s);
            SchemaChain.Result chain = SchemaGenerator.generateChain(EnumRecord.class);
            byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
            EnumRecord decoded = ObjectCodec.decodeHierarchy(EnumRecord.class, chain, encoded);
            assertEquals(s, decoded.getStatus(), "round-trip failed for " + s);
        }
    }

    @Test
    void enumField_unknownConstant_isRejected() throws Exception {
        // Hand-build a payload where the Status field is a UTF8String "DELETED" (unknown constant).
        // Wire for EnumRecord: SEQUENCE { outer hierarchy SEQUENCE { EnumRecord SEQUENCE {
        //   UTF8String("label"), UTF8String("DELETED") } } }
        SchemaChain.Result chain = SchemaGenerator.generateChain(EnumRecord.class);

        byte[] labelTlv  = DerWriter.writeUtf8String("bad-enum");
        byte[] statusTlv = DerWriter.writeUtf8String("DELETED"); // unknown constant
        byte[] classSeq  = DerWriter.writeSequence(List.of(labelTlv, statusTlv));
        byte[] outerSeq  = DerWriter.writeSequence(List.of(classSeq));

        assertThrows(Exception.class,
                () -> ObjectCodec.decodeHierarchy(EnumRecord.class, chain, outerSeq),
                "decoding an unknown enum constant must be rejected fail-secure");
    }

    @Test
    void enumField_deterministic() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(EnumRecord.class);
        EnumRecord r1 = new EnumRecord("x", Status.CLOSED);
        EnumRecord r2 = new EnumRecord("x", Status.CLOSED);
        // Two separate equal objects should encode to byte-identical bytes
        byte[] enc1 = ObjectCodec.encodeHierarchy(r1, chain);
        byte[] enc2 = ObjectCodec.encodeHierarchy(r2, chain);
        assertArrayEquals(enc1, enc2, "enum encoding must be deterministic");
    }

    // =========================================================================
    // PRIMITIVE ARRAY TESTS
    // =========================================================================

    @Test
    void intArray_roundTrip() throws Exception {
        int[] ints = {-100, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE};
        ArrayRecord original = new ArrayRecord(ints, null, null, null, null);
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        ArrayRecord decoded = ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encoded);
        assertArrayEquals(ints, decoded.getInts());
    }

    @Test
    void longArray_roundTrip() throws Exception {
        long[] longs = {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};
        ArrayRecord original = new ArrayRecord(null, longs, null, null, null);
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        ArrayRecord decoded = ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encoded);
        assertArrayEquals(longs, decoded.getLongs());
    }

    @Test
    void shortArray_roundTrip() throws Exception {
        short[] shorts = {Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE};
        ArrayRecord original = new ArrayRecord(null, null, shorts, null, null);
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        ArrayRecord decoded = ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encoded);
        assertArrayEquals(shorts, decoded.getShorts());
    }

    @Test
    void booleanArray_roundTrip() throws Exception {
        boolean[] bools = {true, false, true, true, false};
        ArrayRecord original = new ArrayRecord(null, null, null, bools, null);
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        ArrayRecord decoded = ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encoded);
        assertArrayEquals(bools, decoded.getBooleans());
    }

    @Test
    void emptyPrimitiveArray_roundTrip() throws Exception {
        int[] empty = new int[0];
        ArrayRecord original = new ArrayRecord(empty, null, null, null, null);
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        ArrayRecord decoded = ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encoded);
        assertNotNull(decoded.getInts(), "empty array must decode as non-null");
        assertEquals(0, decoded.getInts().length, "empty array must have length 0");
    }

    @Test
    void nullPrimitiveArray_roundTrip() throws Exception {
        ArrayRecord original = new ArrayRecord(null, null, null, null, null);
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        ArrayRecord decoded = ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encoded);
        assertNull(decoded.getInts(), "null array must decode as null");
        assertNull(decoded.getLongs());
        assertNull(decoded.getShorts());
        assertNull(decoded.getBooleans());
        assertNull(decoded.getStrings());
    }

    @Test
    void nullVsEmptyArray_distinct() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        ArrayRecord withNull  = new ArrayRecord(null, null, null, null, null);
        ArrayRecord withEmpty = new ArrayRecord(new int[0], null, null, null, null);
        byte[] encNull  = ObjectCodec.encodeHierarchy(withNull,  chain);
        byte[] encEmpty = ObjectCodec.encodeHierarchy(withEmpty, chain);
        assertFalse(Arrays.equals(encNull, encEmpty),
                "null array and empty array must have distinct DER encodings");
        assertNull(ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encNull).getInts());
        assertNotNull(ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encEmpty).getInts());
    }

    // =========================================================================
    // STRING[] TESTS
    // =========================================================================

    @Test
    void stringArray_mixedNullElements_roundTrip() throws Exception {
        String[] strings = {"hello", null, "world", null, ""};
        ArrayRecord original = new ArrayRecord(null, null, null, null, strings);
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        ArrayRecord decoded = ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encoded);
        assertArrayEquals(strings, decoded.getStrings());
    }

    @Test
    void stringArray_allNull_roundTrip() throws Exception {
        String[] strings = {null, null};
        ArrayRecord original = new ArrayRecord(null, null, null, null, strings);
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        ArrayRecord decoded = ObjectCodec.decodeHierarchy(ArrayRecord.class, chain, encoded);
        assertArrayEquals(strings, decoded.getStrings());
    }

    @Test
    void arrayRecord_deterministic() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(ArrayRecord.class);
        int[]    ints1 = {1, 2, 3};
        int[]    ints2 = {1, 2, 3};
        String[] strs1 = {"a", null, "b"};
        String[] strs2 = {"a", null, "b"};
        byte[] enc1 = ObjectCodec.encodeHierarchy(
                new ArrayRecord(ints1, null, null, null, strs1), chain);
        byte[] enc2 = ObjectCodec.encodeHierarchy(
                new ArrayRecord(ints2, null, null, null, strs2), chain);
        assertArrayEquals(enc1, enc2, "identical arrays must produce identical DER bytes");
    }

    // =========================================================================
    // @AtomicSerial[] TESTS
    // =========================================================================

    @Test
    void nestedArray_nonNull_roundTrip() throws Exception {
        NestedValue[] elements = {
            new NestedValue(1, "one"),
            new NestedValue(2, "two"),
            new NestedValue(3, "three"),
        };
        NestedArrayHolder original = new NestedArrayHolder("test", elements);
        SchemaChain.Result chain = SchemaGenerator.generateChain(NestedArrayHolder.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        NestedArrayHolder decoded = ObjectCodec.decodeHierarchy(
                NestedArrayHolder.class, chain, encoded);
        assertEquals("test", decoded.getTag());
        assertArrayEquals(elements, decoded.getElements());
    }

    @Test
    void nestedArray_nullArray_roundTrip() throws Exception {
        NestedArrayHolder original = new NestedArrayHolder("null-arr", null);
        SchemaChain.Result chain = SchemaGenerator.generateChain(NestedArrayHolder.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        NestedArrayHolder decoded = ObjectCodec.decodeHierarchy(
                NestedArrayHolder.class, chain, encoded);
        assertNull(decoded.getElements());
    }

    @Test
    void nestedArray_emptyArray_roundTrip() throws Exception {
        NestedArrayHolder original = new NestedArrayHolder("empty", new NestedValue[0]);
        SchemaChain.Result chain = SchemaGenerator.generateChain(NestedArrayHolder.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        NestedArrayHolder decoded = ObjectCodec.decodeHierarchy(
                NestedArrayHolder.class, chain, encoded);
        assertNotNull(decoded.getElements());
        assertEquals(0, decoded.getElements().length);
    }

    @Test
    void nestedArray_withNullElement_roundTrip() throws Exception {
        NestedValue[] elements = {new NestedValue(1, "one"), null, new NestedValue(3, "three")};
        NestedArrayHolder original = new NestedArrayHolder("with-null", elements);
        SchemaChain.Result chain = SchemaGenerator.generateChain(NestedArrayHolder.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        NestedArrayHolder decoded = ObjectCodec.decodeHierarchy(
                NestedArrayHolder.class, chain, encoded);
        NestedValue[] result = decoded.getElements();
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(new NestedValue(1, "one"), result[0]);
        assertNull(result[1]);
        assertEquals(new NestedValue(3, "three"), result[2]);
    }

    @Test
    void nestedArray_polymorphism_runtimeSubtypePerElement() throws Exception {
        // Declared type NestedValue; runtime element is NestedValueSub (from inc-2 fixtures)
        NestedValue base = new NestedValue(1, "base");
        NestedValueSub sub = new NestedValueSub(2, "sub", "extra-data");
        NestedValue[] elements = {base, sub};
        NestedArrayHolder original = new NestedArrayHolder("poly", elements);
        SchemaChain.Result chain = SchemaGenerator.generateChain(NestedArrayHolder.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(original, chain);
        NestedArrayHolder decoded = ObjectCodec.decodeHierarchy(
                NestedArrayHolder.class, chain, encoded);
        NestedValue[] result = decoded.getElements();
        assertNotNull(result);
        assertEquals(2, result.length);
        assertInstanceOf(NestedValue.class, result[0]);
        assertInstanceOf(NestedValueSub.class, result[1],
                "runtime subtype must be preserved via embedded schema");
        assertEquals(base, result[0]);
        assertEquals(sub, result[1]);
    }

    @Test
    void nestedArray_deterministic() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(NestedArrayHolder.class);
        NestedValue[] elems1 = {new NestedValue(7, "x")};
        NestedValue[] elems2 = {new NestedValue(7, "x")};
        byte[] enc1 = ObjectCodec.encodeHierarchy(new NestedArrayHolder("d", elems1), chain);
        byte[] enc2 = ObjectCodec.encodeHierarchy(new NestedArrayHolder("d", elems2), chain);
        assertArrayEquals(enc1, enc2, "identical @AtomicSerial[] must produce identical DER bytes");
    }

    // =========================================================================
    // ARRAY-ELEMENT DEPTH GUARD TEST
    // =========================================================================

    /**
     * Verifies that the cumulative depth guard is threaded through
     * {@code @AtomicSerial[]} element decode (the inc-3 variant of the inc-2
     * NestedDepthGuardTest hazard).
     *
     * <p>Strategy: decode a {@link ChainArrayHolder} AS A NESTED FIELD from within
     * a hand-built outer nested record at depth {@code outerDepth}. The holder's
     * hierarchy is decoded at depth {@code outerDepth + 1}. Array elements start
     * at that same depth. Each link in the {@code Chain} element consumes one
     * depth level: a chain of {@code k} links at starting element-decode depth
     * {@code d} causes {@code decodeHierarchy} to be called with depth
     * {@code d + k} for the innermost link.
     *
     * <p>With {@code outerDepth = 0}:
     * <ul>
     *   <li>Holder's hierarchy is at depth 1; element decode starts at depth 1.</li>
     *   <li>{@code k = MAX_NESTING - 1} links: innermost {@code decodeHierarchy} at
     *       depth {@code 1 + (MAX_NESTING - 1) = MAX_NESTING} -- exactly allowed.</li>
     *   <li>{@code k = MAX_NESTING} links: innermost at depth {@code 1 + MAX_NESTING
     *       = MAX_NESTING + 1} -- must be rejected.</li>
     * </ul>
     *
     * <p>Discriminating proof: if depth were NOT threaded (elements reset to 0),
     * a chain of {@code MAX_NESTING} links starting at depth 0 would succeed
     * (innermost at exactly {@code MAX_NESTING}), defeating the guard.
     */
    @Test
    void arrayElementDepthGuard_isThreaded() throws Exception {
        byte[] chainSchema  = chainSchemaBytes();
        byte[] holderSchema = chainArrayHolderSchemaBytes();

        // outerDepth = 0 means the holder's hierarchy is decoded at depth 1,
        // and each array element starts decode at depth 1.
        // Safe chain length = MAX_NESTING - 1 (element depth 1 + k links = MAX_NESTING).
        int safeLen = ObjectCodec.MAX_NESTING - 1;
        int tooLong = ObjectCodec.MAX_NESTING;

        byte[] safeChainTlv  = buildNestedChain(safeLen, chainSchema);
        byte[] safeHolder    = buildChainArrayHolderPayload(safeChainTlv);
        byte[] safeRecord    = buildNestedRecord(holderSchema, safeHolder);

        assertDoesNotThrow(() -> ObjectCodec.decodeNested(safeRecord, 0),
                "a holder containing a chain of MAX_NESTING-1 links, decoded from depth 0, "
                + "must NOT trip the guard (element depth 1 + " + safeLen
                + " = MAX_NESTING)");

        byte[] deepChainTlv = buildNestedChain(tooLong, chainSchema);
        byte[] deepHolder   = buildChainArrayHolderPayload(deepChainTlv);
        byte[] deepRecord   = buildNestedRecord(holderSchema, deepHolder);

        // With depth threaded: element starts at depth 1, chain of MAX_NESTING links
        // causes innermost decodeHierarchy at depth 1 + MAX_NESTING = MAX_NESTING+1 -> FAIL.
        // Without depth threaded: element starts at depth 0, chain of MAX_NESTING links
        // causes innermost decodeHierarchy at depth 0 + MAX_NESTING = MAX_NESTING -> PASS (wrong!).
        assertThrows(IOException.class,
                () -> ObjectCodec.decodeNested(deepRecord, 0),
                "a holder containing a chain of MAX_NESTING links, decoded from depth 0, "
                + "must be rejected (element depth 1 + " + tooLong
                + " = MAX_NESTING+1); if this PASSES, depth is NOT threaded through elements");
    }

    // =========================================================================
    // FAIL-SECURE TESTS
    // =========================================================================

    @Test
    void multiDimensionalArray_isRejected() {
        // int[][] -> "array:array:int" which toWireType must reject
        assertThrows(DerException.class,
                () -> SchemaGenerator.toWireType(int[][].class,
                        Inc3EnumArrayTest.class),
                "int[][] must be rejected with DerException -- multi-dim not yet supported");
    }

    @Test
    void byteArrayField_stillWorksAsOctetString() throws Exception {
        // byte[] must NOT become "array:byte"; it stays as "byte[]" -> OCTET STRING.
        String wt = SchemaGenerator.toWireType(byte[].class, Inc3EnumArrayTest.class);
        assertEquals("byte[]", wt,
                "byte[] must still map to 'byte[]' (OCTET STRING), not 'array:byte'");
    }

    @Test
    void enumWireType_format() throws Exception {
        String wt = SchemaGenerator.toWireType(Status.class, Inc3EnumArrayTest.class);
        assertEquals("enum:au.net.zeus.jgdms.der.object.fixtures.Status", wt);
    }

    @Test
    void intArrayWireType_format() throws Exception {
        String wt = SchemaGenerator.toWireType(int[].class, Inc3EnumArrayTest.class);
        assertEquals("array:int", wt);
    }

    @Test
    void nestedValueArrayWireType_format() throws Exception {
        String wt = SchemaGenerator.toWireType(NestedValue[].class, Inc3EnumArrayTest.class);
        assertEquals("array:@AtomicSerial:au.net.zeus.jgdms.der.object.fixtures.NestedValue", wt);
    }

    // =========================================================================
    // Private helpers (mirror NestedDepthGuardTest pattern)
    // =========================================================================

    /** Concatenated leaf-first schema bytes for {@link Chain}. */
    private static byte[] chainSchemaBytes() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(Chain.class);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord r : chain.chain()) {
            buf.write(r.encode());
        }
        return buf.toByteArray();
    }

    /** Concatenated leaf-first schema bytes for {@link ChainArrayHolder}. */
    private static byte[] chainArrayHolderSchemaBytes() throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(ChainArrayHolder.class);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (AtomicSerialSchemaRecord r : chain.chain()) {
            buf.write(r.encode());
        }
        return buf.toByteArray();
    }

    /**
     * Builds a nested-record TLV (as produced by {@code ObjectCodec.encodeNested})
     * for an object with the given schema chain bytes and hierarchy payload.
     * Wire format: {@code SEQUENCE { OCTET STRING(schemaBytes), OCTET STRING(payload) }}.
     */
    private static byte[] buildNestedRecord(byte[] schemaBytes, byte[] hierarchyPayload) {
        return DerWriter.writeSequence(List.of(
                DerWriter.writeOctetString(schemaBytes),
                DerWriter.writeOctetString(hierarchyPayload)));
    }

    /**
     * Builds a nested-record TLV for a {@code Chain} of {@code n} links
     * (innermost {@code next == null}). Mirrors {@code NestedDepthGuardTest.buildNestedChain}.
     */
    private static byte[] buildNestedChain(int n, byte[] schema) {
        byte[] nextTlv = new byte[]{0x05, 0x00}; // innermost: null
        byte[] record  = null;
        for (int i = 0; i < n; i++) {
            byte[] chainSeq = DerWriter.writeSequence(List.of(nextTlv));  // Chain class SEQUENCE
            byte[] payload  = DerWriter.writeSequence(List.of(chainSeq)); // outer hierarchy SEQUENCE
            record  = buildNestedRecord(schema, payload);
            nextTlv = record;
        }
        return record;
    }

    /**
     * Builds the hierarchy payload for a {@code ChainArrayHolder} with one element
     * whose pre-built nested-record TLV is {@code chainElementTlv}.
     *
     * <p>Wire structure:
     * <pre>
     * SEQUENCE {                         -- outer hierarchy SEQUENCE (encodeHierarchy)
     *   SEQUENCE {                       -- ChainArrayHolder class SEQUENCE
     *     SEQUENCE {                     -- "chains" array SEQUENCE (encodeArray)
     *       <chainElementTlv>            -- one element (nested-record TLV or NULL)
     *     }
     *   }
     * }
     * </pre>
     */
    private static byte[] buildChainArrayHolderPayload(byte[] chainElementTlv) {
        // "chains" array: SEQUENCE { <element> }
        byte[] arraySeq    = DerWriter.writeSequence(List.of(chainElementTlv));
        // ChainArrayHolder class SEQUENCE: { chains TLV }
        byte[] classSeq    = DerWriter.writeSequence(List.of(arraySeq));
        // outer hierarchy SEQUENCE
        return DerWriter.writeSequence(List.of(classSeq));
    }
}
