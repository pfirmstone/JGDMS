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
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.Tag;
import au.net.zeus.jgdms.der.object.fixtures.AnyFieldRecord;
import au.net.zeus.jgdms.der.object.fixtures.Foo;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field-position conformance tests for the {@code Any} form (STD-006 memo §2.1/§3/§4),
 * complementing {@link AnyElementCodecTest} (element position) -- SOW
 * {@code docs/SOW-RemoteEvent-Source-DER-Encoding.md}, "Concrete change" item 4.
 *
 * <p>A serial field declared exactly {@code Object.class} (the same declared shape as
 * {@code net.jini.core.event.RemoteEvent.source}) now resolves to the {@code "any"} wire-type
 * (previously a hard {@link DerException} at schema-generation time). These tests confirm:
 * <ol>
 *   <li>{@link SchemaGenerator#generate(Class)} really does resolve the field to {@code "any"}
 *       (not a hand-built schema -- the field-level rule itself, item 1);</li>
 *   <li>every closed-subset category round-trips through a real field (not just as a collection
 *       element);</li>
 *   <li>the four AnyCodec fences (a)-(d) apply identically at field position.</li>
 * </ol>
 */
class AnyFieldPositionConformanceTest {

    // =========================================================================
    // Item 1: SchemaGenerator really resolves an Object.class FIELD to "any".
    // =========================================================================

    @Test
    void schemaGenerator_objectTypedField_resolvesToAny() throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(AnyFieldRecord.class);
        assertEquals(1, schema.fields().size());
        assertEquals("value", schema.fields().get(0).wireName());
        assertEquals("any", schema.fields().get(0).wireType(),
                "a field declared exactly Object.class must resolve to the Any wire-type token");
    }

    // =========================================================================
    // Real end-to-end round trip (real schema, real DerFieldStore/DerGetArg dispatch).
    // =========================================================================

    private static byte[] encodeReal(Object value) throws DerException {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(AnyFieldRecord.class);
        return ObjectCodec.encode(new AnyFieldRecord(value), AnyFieldRecord.class, schema);
    }

    private static Object decodeReal(byte[] bytes) throws Exception {
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(AnyFieldRecord.class);
        return ObjectCodec.decode(AnyFieldRecord.class, schema, bytes).getValue();
    }

    private static Object roundTripReal(Object value) throws Exception {
        return decodeReal(encodeReal(value));
    }

    @Test
    void roundTrip_integerScalar_viaRealSchema() throws Exception {
        assertEquals(42, roundTripReal(42), "Integer must round-trip via the [3] scalar arm");
    }

    @Test
    void roundTrip_stringScalar_viaRealSchema() throws Exception {
        assertEquals("hello", roundTripReal("hello"), "String must round-trip via the [8] scalar arm");
    }

    @Test
    void roundTrip_booleanAndLongScalars_viaRealSchema() throws Exception {
        assertEquals(Boolean.TRUE, roundTripReal(Boolean.TRUE));
        assertEquals(9_000_000_000L, roundTripReal(9_000_000_000L));
    }

    @Test
    void roundTrip_byteArrayScalar_viaRealSchema() throws Exception {
        byte[] in = {1, 2, 3};
        Object out = roundTripReal(in);
        assertArrayEquals(in, (byte[]) out);
    }

    @Test
    void roundTrip_nullValue_viaRealSchema() throws Exception {
        assertNull(roundTripReal(null));
    }

    @Test
    void roundTrip_atomicSerialObject_viaRealSchema() throws Exception {
        Foo in = new Foo(7, "seven");
        Object out = roundTripReal(in);
        assertEquals(in, out, "an @AtomicSerial value must round-trip through the [20] arm");
    }

    @Test
    void roundTrip_dynamicProxy_viaRealSchema() throws Exception {
        au.net.zeus.jgdms.der.object.fixtures.Greeter proxy =
                (au.net.zeus.jgdms.der.object.fixtures.Greeter) java.lang.reflect.Proxy.newProxyInstance(
                        au.net.zeus.jgdms.der.object.fixtures.Greeter.class.getClassLoader(),
                        new Class<?>[]{au.net.zeus.jgdms.der.object.fixtures.Greeter.class},
                        new au.net.zeus.jgdms.der.object.fixtures.GreeterHandler("hi"));
        Object out = roundTripReal(proxy);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(out.getClass()),
                "a dynamic Proxy source must reconstruct as a Proxy (Any [20] wrapping a [8] record)");
        assertEquals("hi: world",
                ((au.net.zeus.jgdms.der.object.fixtures.Greeter) out).greet("world"),
                "the reconstructed proxy must actually dispatch through its decoded handler");
    }

    @Test
    void determinism_twoIndependentEncodes_byteIdentical() throws Exception {
        byte[] a = encodeReal(42);
        byte[] b = encodeReal(Integer.valueOf(42));
        assertArrayEquals(a, b, "two independent encodes of an equal Integer must be byte-identical");

        byte[] sa = encodeReal("same");
        byte[] sb = encodeReal(new String("same"));
        assertArrayEquals(sa, sb, "two independent encodes of an equal String must be byte-identical");
    }

    // =========================================================================
    // Hand-built-schema helpers for the adversarial fence tests (direct TLV control),
    // mirroring AnyElementCodecTest's pattern at field (not element) position.
    // =========================================================================

    private static AtomicSerialSchemaRecord fieldSchema() {
        return new AtomicSerialSchemaRecord(
                AnyFieldRecord.class.getName(), (byte[]) null,
                List.of(new AtomicSerialFieldDef("value", "any")));
    }

    /** Wraps a raw AnyElement TLV as the single field of an AnyFieldRecord private SEQUENCE. */
    private static byte[] wrapFieldRecordSequence(byte[] anyElementTlv) {
        return DerWriter.writeSequence(List.of(anyElementTlv));
    }

    private static Object decodeCrafted(byte[] anyElementTlv) throws Exception {
        byte[] payload = wrapFieldRecordSequence(anyElementTlv);
        return ObjectCodec.decode(AnyFieldRecord.class, fieldSchema(), payload).getValue();
    }

    private static void assertRejected(byte[] anyElementTlv, String what) {
        assertThrows(IOException.class, () -> decodeCrafted(anyElementTlv),
                "field-position fence: " + what + " must be hard-rejected");
    }

    // =========================================================================
    // FENCE (c) at field position: reserved / unregistered / mismatched-form tag -> reject.
    // =========================================================================

    @Test
    void fenceC_reservedGapTag_rejectedAtFieldPosition() {
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 15),
                DerWriter.writeInteger(BigInteger.valueOf(1)));
        assertRejected(elem, "reserved-gap tag [15]");
    }

    @Test
    void fenceC_tagAbove31_rejectedAtFieldPosition() {
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, 40),
                DerWriter.writeInteger(BigInteger.valueOf(1)));
        assertRejected(elem, "tag [40] > [31]");
    }

    // =========================================================================
    // FENCE (c) at field position: constructed/primitive form mismatch -> reject.
    // =========================================================================

    @Test
    void fenceC_scalarTagWithConstructedForm_rejectedAtFieldPosition() {
        // A scalar arm ([3] int) MUST be primitive; a CONSTRUCTED [3] is a form mismatch.
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, AnyCodec.TAG_INT),
                DerWriter.writeInteger(BigInteger.valueOf(1)));
        assertRejected(elem, "constructed scalar tag [3]");
    }

    @Test
    void fenceC_atomicObjectTagPrimitiveForm_rejectedAtFieldPosition() {
        // [20] atomicSerialObject MUST be EXPLICIT/constructed; a primitive [20] is a mismatch.
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, false, AnyCodec.TAG_ATOMIC),
                new byte[]{0x01});
        assertRejected(elem, "primitive form of [20] atomicSerialObject");
    }

    // =========================================================================
    // FENCE (d) at field position: non-canonical (non-minimal) tag encoding -> reject.
    // =========================================================================

    @Test
    void fenceD_nonMinimalTagEncoding_rejectedAtFieldPosition() throws Exception {
        // Encode [3] int in the high-tag-number (long) form even though 3 fits the short form.
        byte[] intContent = contentOf(DerWriter.writeInteger(BigInteger.valueOf(1)));
        byte[] lengthAndValue = new byte[1 + intContent.length];
        lengthAndValue[0] = (byte) intContent.length;
        System.arraycopy(intContent, 0, lengthAndValue, 1, intContent.length);
        byte[] elem = new byte[2 + lengthAndValue.length];
        elem[0] = (byte) 0x9F; // context, primitive, high-tag-number flag
        elem[1] = (byte) 0x03; // tag number 3 in long form (non-minimal for < 31)
        System.arraycopy(lengthAndValue, 0, elem, 2, lengthAndValue.length);
        assertRejected(elem, "non-minimal high-tag-number encoding of [3]");
    }

    @Test
    void fenceC_canonicalCollectionTagOverWrongInnerDiscipline_rejectedAtFieldPosition() {
        // [30] canonicalCollection MUST wrap a SET OF (0x31); wrapping a SEQUENCE OF (0x30) is a
        // mismatched/lying encoding.
        byte[] innerSeqOf = DerWriter.writeSequence(List.of());
        byte[] elem = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, AnyCodec.TAG_CANONICAL_COLL),
                innerSeqOf);
        assertRejected(elem, "[30] over a SEQUENCE OF");
    }

    // =========================================================================
    // FENCE (a) at field position: MAX_NESTING bound on a deeply-nested Any chain.
    // =========================================================================

    @Test
    void fenceA_deeplyNestedAnyField_rejectedBeforeStackOverflow() throws Exception {
        int depthLevels = ObjectCodec.MAX_NESTING + 40;
        byte[] node = wrapCanonicalCollection(DerWriter.writeSet(List.of()));
        for (int i = 0; i < depthLevels; i++) {
            byte[] setOfOne = DerWriter.writeSet(List.of(node));
            node = wrapCanonicalCollection(setOfOne);
        }
        // node IS the field's own AnyElement TLV (field position, not wrapped as a collection
        // element this time -- the field itself carries the deeply nested Any chain).
        byte[] deepNode = node;
        IOException ex = assertThrows(IOException.class, () -> decodeCrafted(deepNode),
                "a deeply-nested Any FIELD must be rejected by the MAX_NESTING guard, "
                + "not by StackOverflow");
        assertTrue(messageChainContains(ex, "nesting"),
                "rejection must be the depth-bound guard, got: " + ex);
    }

    // =========================================================================
    // FENCE (b) at field position: malformed [20] atomicSerialObject body -> reject, never silent.
    // =========================================================================

    @Test
    void fenceB_malformedAtomicObjectBody_rejectedAtFieldPosition() {
        byte[] bogus = DerWriter.writeOctetString(new byte[]{0x01, 0x02});
        byte[] anyObj = DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, AnyCodec.TAG_ATOMIC), bogus);
        assertRejected(anyObj, "malformed [20] atomicSerialObject body");
    }

    // -------------------------------------------------------------------------
    // Helpers.
    // -------------------------------------------------------------------------

    private static byte[] wrapCanonicalCollection(byte[] setOfTlv) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, true, AnyCodec.TAG_CANONICAL_COLL), setOfTlv);
    }

    private static byte[] contentOf(byte[] tlv) throws DerException {
        DerReader r = new DerReader(tlv);
        DerReader.TlvHeader hdr = r.readTlvHeader();
        return r.readRawContent(hdr.contentLength());
    }

    private static boolean messageChainContains(Throwable t, String needle) {
        String lc = needle.toLowerCase();
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (m != null && m.toLowerCase().contains(lc)) {
                return true;
            }
            if (c.getCause() == c) break;
        }
        return false;
    }
}
