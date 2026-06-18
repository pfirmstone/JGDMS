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
import au.net.zeus.jgdms.der.object.fixtures.NestedValue;
import au.net.zeus.jgdms.der.object.fixtures.NestedValueSub;
import au.net.zeus.jgdms.der.object.fixtures.OuterWithNested;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B1 increment-2 acceptance tests: nested {@code @AtomicSerial} object fields
 * (JGDMS-STD-008 sec.16).
 *
 * <h2>Tests</h2>
 * <ul>
 *   <li><b>16.1</b> -- Round-trip: Outer with a non-null Inner; equals after encode/decode.</li>
 *   <li><b>16.2</b> -- Polymorphism: field declared as supertype holds a subtype at runtime;
 *       embedded schema drives decode to correct runtime class.</li>
 *   <li><b>16.3</b> -- Null nested field round-trips as null.</li>
 *   <li><b>16.4</b> -- Determinism: encoding the same nested value twice is byte-identical.</li>
 *   <li><b>16.5</b> -- Depth-bound DoS guard: a synthetically over-nested record is rejected
 *       on decode with DerException.</li>
 *   <li><b>16.6</b> -- Schema wireType: toWireType returns "@AtomicSerial" for a field whose
 *       declared type is @AtomicSerial.</li>
 *   <li><b>16.7</b> -- der.stream integration: DerMarshalOutputStream.writeObject /
 *       DerMarshalInputStream.readObject round-trips an OuterWithNested automatically.</li>
 * </ul>
 */
class NestedAtomicSerialTest {

    // =========================================================================
    // 16.1 -- Basic nested round-trip
    // =========================================================================

    /**
     * 16.1 -- Outer.inner holds a non-null NestedValue; full encode/decode round-trip.
     * Proves that the nested object is encoded as a self-describing record and
     * reconstructed via its own check(GetArg) before the outer constructor uses it.
     */
    @Test
    void test_16_1_NestedRoundTrip_Basic() throws Exception {
        NestedValue inner = new NestedValue(42, "hello");
        OuterWithNested outer = new OuterWithNested("outer-tag", inner);

        SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithNested.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(outer, chain);
        OuterWithNested decoded = ObjectCodec.decodeHierarchy(OuterWithNested.class, chain, encoded);

        assertEquals(outer, decoded, "Decoded OuterWithNested must equal original");
        assertNotSame(outer, decoded, "Decoded must be a new instance (value-tree copy)");
        assertNotNull(decoded.getInner(), "inner must not be null after round-trip");
        assertEquals(inner, decoded.getInner(), "inner value must be equal");
        assertNotSame(inner, decoded.getInner(), "inner must be a new instance (nested copy)");
        assertEquals("outer-tag", decoded.getTag());
        assertEquals(42,      decoded.getInner().getId());
        assertEquals("hello", decoded.getInner().getLabel());
    }

    // =========================================================================
    // 16.2 -- Polymorphism: field declared as supertype, value is a subtype
    // =========================================================================

    /**
     * 16.2 -- Polymorphism: the field 'inner' is declared as {@link NestedValue}
     * but holds a {@link NestedValueSub} at runtime. The embedded schema in the
     * nested record names {@code NestedValueSub}, so decode reconstructs it as
     * {@code NestedValueSub} (not the declared supertype).
     */
    @Test
    void test_16_2_Polymorphism_SubtypeRoundTrip() throws Exception {
        NestedValueSub innerSub = new NestedValueSub(7, "sub-label", "extra-data");
        OuterWithNested outer = new OuterWithNested("poly-outer", innerSub);

        SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithNested.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(outer, chain);
        OuterWithNested decoded = ObjectCodec.decodeHierarchy(OuterWithNested.class, chain, encoded);

        // outer.equals uses inner.equals; since inner is NestedValueSub, both sides must agree
        assertEquals(outer, decoded, "Decoded OuterWithNested must equal original (polymorphic inner)");

        // Runtime class of the decoded inner must be NestedValueSub (not just NestedValue)
        NestedValue decodedInner = decoded.getInner();
        assertNotNull(decodedInner, "inner must not be null");
        assertEquals(NestedValueSub.class, decodedInner.getClass(),
                "Embedded schema must drive decode to NestedValueSub, not just NestedValue");

        NestedValueSub decodedSub = (NestedValueSub) decodedInner;
        assertEquals(7,            decodedSub.getId());
        assertEquals("sub-label",  decodedSub.getLabel());
        assertEquals("extra-data", decodedSub.getExtra());
    }

    // =========================================================================
    // 16.3 -- Null nested field round-trips as null
    // =========================================================================

    /**
     * 16.3 -- A null nested field encodes as DER NULL (0x05 0x00) and decodes
     * back to null.
     */
    @Test
    void test_16_3_NullNestedField_RoundTrips() throws Exception {
        OuterWithNested outer = new OuterWithNested("null-inner", null);

        SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithNested.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(outer, chain);
        OuterWithNested decoded = ObjectCodec.decodeHierarchy(OuterWithNested.class, chain, encoded);

        assertEquals(outer, decoded, "Decoded must equal original (null inner)");
        assertNull(decoded.getInner(), "Decoded inner must be null");
        assertEquals("null-inner", decoded.getTag());
    }

    // =========================================================================
    // 16.4 -- Determinism: same value => byte-identical encoding
    // =========================================================================

    /**
     * 16.4 -- Encoding the same nested value twice (two independent instances
     * that are .equals()) must produce byte-identical output (canonical DER,
     * value-tree, no identity/order dependence).
     */
    @Test
    void test_16_4_Determinism_SameValueSameBytes() throws Exception {
        NestedValue inner1 = new NestedValue(99, "deterministic");
        NestedValue inner2 = new NestedValue(99, "deterministic");
        assertEquals(inner1, inner2, "Pre-condition: inner1 and inner2 must be .equals()");
        assertNotSame(inner1, inner2, "Pre-condition: must be distinct instances");

        OuterWithNested outer1 = new OuterWithNested("det", inner1);
        OuterWithNested outer2 = new OuterWithNested("det", inner2);

        SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithNested.class);
        byte[] encoded1 = ObjectCodec.encodeHierarchy(outer1, chain);
        byte[] encoded2 = ObjectCodec.encodeHierarchy(outer2, chain);

        assertArrayEquals(encoded1, encoded2,
                "Two .equals() OuterWithNested instances must produce byte-identical DER "
                + "(value-tree determinism, STD-008 sec.15.3)");
    }

    // =========================================================================
    // 16.5 -- Depth-bound DoS guard: over-nested record rejected
    // =========================================================================

    /**
     * 16.5 -- A synthetically constructed nested record that claims to be nested
     * beyond MAX_NESTING levels must be rejected with DerException on decode.
     *
     * <p>We build an ordinary nested record (depth 1) and then synthetically
     * wrap it in another level of nesting by embedding it inside itself
     * recursively, or simply by calling decodeNested with depth = MAX_NESTING + 1.
     */
    @Test
    void test_16_5_DepthBound_DoSGuard() throws Exception {
        // Build a valid depth-1 nested record bytes (NestedValue as nested field)
        NestedValue inner = new NestedValue(1, "guard-test");
        SchemaChain.Result innerChain = SchemaGenerator.generateChain(NestedValue.class);
        byte[] payload = ObjectCodec.encodeHierarchy(inner, innerChain);

        // Encode the schema chain bytes
        java.io.ByteArrayOutputStream schemaBuf = new java.io.ByteArrayOutputStream();
        for (au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord rec : innerChain.chain()) {
            byte[] enc = rec.encode();
            schemaBuf.write(enc, 0, enc.length);
        }
        byte[] schemaChainBytes = schemaBuf.toByteArray();

        // Build a valid nested record TLV: SEQUENCE{OCTET STRING(schema), OCTET STRING(payload)}
        java.util.List<byte[]> seqChildren = java.util.List.of(
                DerWriter.writeOctetString(schemaChainBytes),
                DerWriter.writeOctetString(payload));
        byte[] validNestedTlv = DerWriter.writeSequence(seqChildren);

        // Decoding at depth 0 must succeed (depth check: depth > MAX_NESTING, so 0 is fine)
        assertDoesNotThrow(() -> ObjectCodec.decodeNested(validNestedTlv, 0),
                "decodeNested at depth 0 must succeed for a valid record");

        // Decoding at depth MAX_NESTING - 1 must succeed (still within bound)
        assertDoesNotThrow(() -> ObjectCodec.decodeNested(validNestedTlv, ObjectCodec.MAX_NESTING - 1),
                "decodeNested at MAX_NESTING-1 must succeed");

        // Decoding at depth MAX_NESTING must throw DerException: the nested SEQUENCE would
        // be at depth MAX_NESTING+1 (decodeNested recurses into decodeHierarchy(depth+1)),
        // which exceeds the bound. This is the fail-secure DoS guard (STD-008 sec.16.2).
        DerException thrown = assertThrows(DerException.class,
                () -> ObjectCodec.decodeNested(validNestedTlv, ObjectCodec.MAX_NESTING),
                "decodeNested at MAX_NESTING must throw DerException (DoS guard: next level = MAX_NESTING+1)");
        assertTrue(thrown.getMessage().contains("MAX_NESTING"),
                "DerException message must mention MAX_NESTING; got: " + thrown.getMessage());
    }

    // =========================================================================
    // 16.6 -- SchemaGenerator.toWireType returns "@AtomicSerial"
    // =========================================================================

    /**
     * 16.6 -- {@code SchemaGenerator.toWireType} must return {@code "@AtomicSerial"}
     * for a field whose declared Java type is itself annotated {@code @AtomicSerial}.
     */
    @Test
    void test_16_6_SchemaGenerator_ToWireType_AtomicSerial() throws Exception {
        // NestedValue is @AtomicSerial
        String wireType = SchemaGenerator.toWireType(NestedValue.class, OuterWithNested.class);
        assertEquals("@AtomicSerial", wireType,
                "toWireType for an @AtomicSerial class must return '@AtomicSerial'");
    }

    // =========================================================================
    // 16.7 -- der.stream integration: DerObjectStream round-trips nested object
    // =========================================================================

    /**
     * 16.7 -- The DER object stream (inc-1) handles nested {@code @AtomicSerial}
     * graphs automatically: writing an {@code OuterWithNested} through
     * {@link DerMarshalOutputStream} and reading it back through
     * {@link DerMarshalInputStream} round-trips the full object graph including
     * the nested field.
     */
    @Test
    void test_16_7_DerStream_NestedObject_RoundTrip() throws Exception {
        NestedValue inner = new NestedValue(11, "stream-inner");
        OuterWithNested outer = new OuterWithNested("stream-outer", inner);

        // Write
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DerMarshalOutputStream out = new DerMarshalOutputStream(baos);
        out.writeObject(outer);
        out.flush();
        byte[] streamBytes = baos.toByteArray();

        // Read
        DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(streamBytes));
        Object decoded = in.readObject();

        assertInstanceOf(OuterWithNested.class, decoded,
                "readObject must return an OuterWithNested");
        OuterWithNested decodedOuter = (OuterWithNested) decoded;
        assertEquals(outer, decodedOuter,
                "DerStream round-trip of OuterWithNested must equal original");
        assertEquals(inner, decodedOuter.getInner(),
                "Nested inner value must survive DerStream round-trip");
    }
}
