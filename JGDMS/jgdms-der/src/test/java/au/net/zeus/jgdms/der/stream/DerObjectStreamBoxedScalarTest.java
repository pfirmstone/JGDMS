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
import au.net.zeus.jgdms.der.marshal.DerMarshalInstanceOutput;
import au.net.zeus.jgdms.der.marshal.DerMarshalledInstance;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Top-level boxed-primitive support in the DER object-stream codec (STD-008 sec.15.2.1).
 *
 * <p>Before this increment, {@link DerObjectStreamCodec#writeObject} rejected a top-level
 * boxed {@code Boolean}/{@code Byte}/{@code Short}/{@code Integer}/{@code Long}/
 * {@code Float}/{@code Double}/{@code Character} with {@link UnsupportedOperationException}
 * ("@AtomicSerial-restricted") -- the exact gap
 * {@code SOW-Entry-ATOMIC-DER-Migration.md} A1 names: Outrigger wraps each {@code Entry}
 * field in its own top-level {@code MarshalledInstance}, so a boxed-scalar-typed field
 * reaches {@code writeObject} as a top-level item, not as a declared-type field inside an
 * {@code @AtomicSerial} record. Reggie has the identical shipped gap.
 *
 * <h2>Test inventory</h2>
 * <ol>
 *   <li>Round-trip type preservation for all 8 boxed types (value and EXACT box preserved
 *       -- an {@code Integer} never decodes as a {@code Long}).</li>
 *   <li>Byte-identical determinism: the same value encodes to the same bytes, both across
 *       independent encode calls and across genuinely distinct object instances of an equal
 *       value.</li>
 *   <li>Canonical-form REJECT probes: non-minimal INTEGER, non-canonical BOOLEAN, wrong
 *       OCTET STRING length (float/double), non-canonical NaN / {@code -0.0}, an
 *       out-of-range decoded value for its box, a surrogate codepoint, and an unassigned
 *       context tag.</li>
 *   <li>String + enum top-level regression (the pre-existing paths, unchanged by this
 *       increment, still round-trip).</li>
 *   <li>The A1-motivating end-to-end: a boxed value through a full {@code ATOMIC_DER}
 *       {@code MarshalledInstance} round-trip, and byte-identical payloads for equal
 *       values (the property Outrigger's byte-compare entry matching needs).</li>
 * </ol>
 *
 * <p>In package {@code au.net.zeus.jgdms.der.stream} to reach the package-private
 * {@link DerObjectStreamCodec}, following the convention already used by
 * {@code DerObjectStreamEnumTest}.
 */
class DerObjectStreamBoxedScalarTest {

    private enum Regression { ALPHA, BETA }

    // =========================================================================
    // Helpers (mirrors DerObjectStreamEnumTest's write/read pattern)
    // =========================================================================

    private static byte[] write(Object o) throws IOException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeObject(o);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    private static Object read(byte[] b) throws IOException, ClassNotFoundException {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(b);
        return c.readObject();
    }

    /** Hand-crafts a single context-tagged TLV directly (bypassing the codec's own writer),
     *  for adversarial malformed-content probes. */
    private static byte[] craft(int tagNumber, boolean constructed, byte[] content) {
        return DerWriter.writeTlv(new Tag(Tag.CLASS_CONTEXT, constructed, tagNumber), content);
    }

    // =========================================================================
    // 1. Round-trip type preservation for all 8 boxed types
    // =========================================================================

    @Test
    void roundTrip_boxedBoolean_preservesTypeAndValue() throws Exception {
        for (Boolean v : new Boolean[]{ Boolean.TRUE, Boolean.FALSE }) {
            Object result = read(write(v));
            assertEquals(v, result);
            assertSame(Boolean.class, result.getClass());
        }
    }

    @Test
    void roundTrip_boxedByte_preservesTypeAndValue() throws Exception {
        for (Byte v : new Byte[]{ (byte) 0, (byte) 42, (byte) -1, Byte.MIN_VALUE, Byte.MAX_VALUE }) {
            Object result = read(write(v));
            assertEquals(v, result);
            assertSame(Byte.class, result.getClass());
        }
    }

    @Test
    void roundTrip_boxedShort_preservesTypeAndValue() throws Exception {
        for (Short v : new Short[]{ 0, 12345, -12345, Short.MIN_VALUE, Short.MAX_VALUE }) {
            Object result = read(write(v));
            assertEquals(v, result);
            assertSame(Short.class, result.getClass());
        }
    }

    @Test
    void roundTrip_boxedInteger_preservesTypeAndValue() throws Exception {
        for (Integer v : new Integer[]{ 0, 123456789, -123456789, Integer.MIN_VALUE, Integer.MAX_VALUE }) {
            Object result = read(write(v));
            assertEquals(v, result);
            assertSame(Integer.class, result.getClass());
        }
    }

    @Test
    void roundTrip_boxedLong_preservesTypeAndValue() throws Exception {
        for (Long v : new Long[]{ 0L, 123456789012345L, -123456789012345L, Long.MIN_VALUE, Long.MAX_VALUE }) {
            Object result = read(write(v));
            assertEquals(v, result);
            assertSame(Long.class, result.getClass());
        }
    }

    @Test
    void roundTrip_boxedFloat_preservesTypeAndValue() throws Exception {
        for (Float v : new Float[]{ 0.0f, 3.14159f, -2.71828f, Float.MIN_VALUE, Float.MAX_VALUE,
                                     Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN }) {
            Object result = read(write(v));
            assertEquals(v, result);
            assertSame(Float.class, result.getClass());
        }
    }

    @Test
    void roundTrip_boxedDouble_preservesTypeAndValue() throws Exception {
        for (Double v : new Double[]{ 0.0, Math.PI, -Math.E, Double.MIN_VALUE, Double.MAX_VALUE,
                                       Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN }) {
            Object result = read(write(v));
            assertEquals(v, result);
            assertSame(Double.class, result.getClass());
        }
    }

    @Test
    void roundTrip_boxedCharacter_preservesTypeAndValue() throws Exception {
        for (Character v : new Character[]{ 'A', '\u0000', '\u00E9', 'z' }) {
            Object result = read(write(v));
            assertEquals(v, result);
            assertSame(Character.class, result.getClass());
        }
    }

    /**
     * {@code -0.0} is intentionally NOT round-trip-equal (STD-008 sec.17.3.1 maps it to
     * {@code +0.0} on encode); this is a separate canonicalization property, covered below
     * ({@link #canonicalization_negativeZero_mapsToPositiveZero_float} /
     * {@code ..._double}), not the plain round-trip-equality property above.
     */
    @Test
    void canonicalization_negativeZero_mapsToPositiveZero_float() throws Exception {
        byte[] negZero = write(Float.valueOf(-0.0f));
        byte[] posZero = write(Float.valueOf(0.0f));
        assertArrayEquals(posZero, negZero,
                "encoding -0.0f and +0.0f must produce byte-identical wire bytes (STD-008 sec.17.3.1)");
        Object decoded = read(negZero);
        assertEquals(0, Float.compare(0.0f, (Float) decoded),
                "decoding a -0.0f-derived item must yield +0.0f, never -0.0f");
    }

    @Test
    void canonicalization_negativeZero_mapsToPositiveZero_double() throws Exception {
        byte[] negZero = write(Double.valueOf(-0.0));
        byte[] posZero = write(Double.valueOf(0.0));
        assertArrayEquals(posZero, negZero,
                "encoding -0.0 and +0.0 must produce byte-identical wire bytes (STD-008 sec.17.3.1)");
        Object decoded = read(negZero);
        assertEquals(0, Double.compare(0.0, (Double) decoded),
                "decoding a -0.0-derived item must yield +0.0, never -0.0");
    }

    // =========================================================================
    // 2. Byte-identical determinism
    // =========================================================================

    @Test
    void determinism_sameValue_twoIndependentEncodes_identicalBytes() throws Exception {
        Object[] values = {
            Boolean.TRUE, Boolean.FALSE,
            Byte.valueOf((byte) 42), Byte.valueOf((byte) -100),
            Short.valueOf((short) 12345), Short.valueOf((short) -12345),
            Integer.valueOf(123456789), Integer.valueOf(-123456789),
            Long.valueOf(123456789012345L), Long.valueOf(-123456789012345L),
            Float.valueOf(3.14159f), Float.valueOf(Float.NaN),
            Double.valueOf(Math.PI), Double.valueOf(Double.NaN),
            Character.valueOf('Q'), Character.valueOf(' '),
        };
        for (Object v : values) {
            byte[] a = write(v);
            byte[] b = write(v);
            assertArrayEquals(a, b,
                    "encoding " + v + " (" + v.getClass().getSimpleName()
                    + ") twice must yield byte-identical DER (determinism)");
        }
    }

    /**
     * Determinism is a function of VALUE, not object identity (mirrors
     * {@code DerObjectStreamTest.repeatedObject_encodedByValue_deterministic_noAliasing}
     * for {@code @AtomicSerial} objects). {@code Integer.valueOf} is not required to cache
     * outside {@code [-128, 127]}, so two independently-obtained boxes of the same
     * out-of-cache-range value are ordinarily distinct instances -- yet must still produce
     * identical bytes.
     */
    @Test
    void determinism_distinctInstances_equalValue_identicalBytes() throws Exception {
        Integer v1 = Integer.valueOf(20260720);
        Integer v2 = Integer.valueOf(Integer.parseInt("20260720"));
        assertNotSame(v1, v2, "sanity: these must be genuinely distinct Integer instances");
        assertArrayEquals(write(v1), write(v2),
                "encoding must depend on value, not object identity");
    }

    // =========================================================================
    // 3. Canonical-form REJECT probes (fail-secure decode)
    // =========================================================================

    @Test
    void reject_nonMinimalInteger_boxedInteger() {
        // [10] boxed Integer with a non-minimal leading 0x00 (value 5 needs only one byte).
        byte[] malicious = craft(10, false, new byte[] { 0x00, 0x05 });
        assertThrows(IOException.class, () -> read(malicious),
                "a non-minimal INTEGER content must be rejected fail-secure (H1)");
    }

    @Test
    void reject_nonMinimalInteger_negativeForm_boxedLong() {
        // [11] boxed Long with a non-minimal leading 0xFF (value -5 needs only one byte).
        byte[] malicious = craft(11, false, new byte[] { (byte) 0xFF, (byte) 0xFB });
        assertThrows(IOException.class, () -> read(malicious),
                "a non-minimal negative INTEGER content must be rejected fail-secure (H1)");
    }

    @Test
    void reject_emptyIntegerContent_boxedShort() {
        byte[] malicious = craft(6, false, new byte[0]);
        assertThrows(IOException.class, () -> read(malicious),
                "zero-length INTEGER content must be rejected fail-secure");
    }

    @Test
    void reject_nonCanonicalBooleanOctet() {
        // [2] boxed Boolean with content 0x01 -- DER requires exactly 0x00 or 0xFF.
        byte[] malicious = craft(2, false, new byte[] { 0x01 });
        assertThrows(IOException.class, () -> read(malicious),
                "a BOOLEAN octet other than 0x00/0xFF must be rejected fail-secure");
    }

    @Test
    void reject_wrongLengthBooleanContent() {
        byte[] malicious = craft(2, false, new byte[] { (byte) 0xFF, (byte) 0xFF });
        assertThrows(IOException.class, () -> read(malicious),
                "a boxed Boolean content length != 1 must be rejected fail-secure");
    }

    @Test
    void reject_wrongLengthFloatContent() {
        // [12] boxed Float requires exactly 4 content bytes.
        byte[] malicious = craft(12, false, new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00 });
        assertThrows(IOException.class, () -> read(malicious),
                "a boxed Float OCTET STRING of the wrong length must be rejected fail-secure");
    }

    @Test
    void reject_wrongLengthDoubleContent() {
        // [13] boxed Double requires exactly 8 content bytes.
        byte[] malicious = craft(13, false, new byte[] { 0, 0, 0, 0, 0, 0, 0 });
        assertThrows(IOException.class, () -> read(malicious),
                "a boxed Double OCTET STRING of the wrong length must be rejected fail-secure");
    }

    @Test
    void reject_nonCanonicalNaN_boxedFloat() {
        // A NaN bit pattern other than the canonical 0x7FC00000.
        byte[] malicious = craft(12, false, new byte[] { 0x7F, (byte) 0x80, 0x00, 0x01 });
        assertThrows(IOException.class, () -> read(malicious),
                "a non-canonical NaN bit pattern must be rejected fail-secure (STD-008 sec.17.3.1)");
    }

    @Test
    void reject_negativeZeroBits_boxedDouble() {
        // The raw -0.0 bit pattern (0x8000000000000000) must never be accepted on decode.
        byte[] malicious = craft(13, false,
                new byte[] { (byte) 0x80, 0, 0, 0, 0, 0, 0, 0 });
        assertThrows(IOException.class, () -> read(malicious),
                "-0.0 bits must be rejected on decode (STD-008 sec.17.3.1)");
    }

    @Test
    void reject_outOfRangeValue_boxedByte() {
        // [4] boxed Byte whose (canonical, minimal) INTEGER content decodes to 200 --
        // a valid DER INTEGER, but out of java.lang.Byte's [-128, 127] range.
        byte[] malicious = craft(4, false, BigInteger.valueOf(200).toByteArray());
        assertThrows(IOException.class, () -> read(malicious),
                "a decoded value outside the target box's numeric range must be rejected fail-secure");
    }

    @Test
    void reject_surrogateCodepoint_boxedCharacter() {
        // [14] boxed Character naming a surrogate codepoint (0xD800), not a valid Unicode codepoint.
        byte[] malicious = craft(14, false, BigInteger.valueOf(0xD800).toByteArray());
        assertThrows(IOException.class, () -> read(malicious),
                "a surrogate codepoint must be rejected fail-secure (STD-008 sec.17.3.2)");
    }

    @Test
    void reject_unassignedContextTag_inGapBetweenBoxedRuns() {
        // Tag 15 is unassigned: this codec's own boxed-scalar allocation stops at [14], and
        // the next assigned tag ([20]+) belongs to a different section of the numbering.
        byte[] malicious = craft(15, false, DerWriter.writeInteger(BigInteger.ZERO));
        assertThrows(IOException.class, () -> read(malicious),
                "an unassigned context tag must be rejected fail-secure, not silently ignored");
    }

    // =========================================================================
    // 4. String + enum top-level regression (pre-existing paths, unchanged)
    // =========================================================================

    @Test
    void regression_stringTopLevel_stillRoundTrips() throws Exception {
        String s = "boxed-scalar-regression-check";
        assertEquals(s, read(write(s)));
    }

    @Test
    void regression_enumTopLevel_stillRoundTrips() throws Exception {
        for (Regression r : Regression.values()) {
            assertEquals(r, read(write(r)));
        }
    }

    // =========================================================================
    // 5. A1-motivating end-to-end: full ATOMIC_DER MarshalledInstance round-trip
    // =========================================================================

    /**
     * The exact shape of Outrigger's {@code EntryRep} per-field marshalling
     * ({@code new MarshalledInstance(fieldValue)}): a boxed scalar wrapped directly in a
     * top-level {@code DerMarshalledInstance}, with no enclosing {@code @AtomicSerial}
     * class. Before this increment, constructing this {@code MarshalledInstance} threw
     * {@link UnsupportedOperationException} from {@link DerObjectStreamCodec#writeObject}.
     */
    @Test
    void a1_boxedScalar_throughFullAtomicDerMarshalledInstance_roundTrips() throws Exception {
        Object[] values = { Integer.valueOf(424242), Long.valueOf(9876543210L), Boolean.TRUE };
        for (Object v : values) {
            DerMarshalledInstance dmi = new DerMarshalledInstance(v);
            @SuppressWarnings("unchecked")
            Object result = dmi.get(false, (Class<Object>) v.getClass());
            assertEquals(v, result, "boxed scalar must round-trip through a full MarshalledInstance");
            assertSame(v.getClass(), result.getClass(), "decoded box must be the SAME type as the original");
        }
    }

    /**
     * Outrigger's byte-compare entry matching (
     * {@code MarshalledInstance.equals}/{@code hashCode}, {@code payloadBytes}-only, and
     * {@code EntryRep.matches}) requires that two equal boxed-scalar field values produce
     * byte-identical {@code ATOMIC_DER} payloads -- otherwise a template and a stored entry
     * carrying the "same" value would silently never match (the exact hazard
     * {@code SOW-Entry-ATOMIC-DER-Migration.md} sec.2.3 documents for the general case).
     */
    @Test
    void a1_boxedScalar_equalValues_produceByteIdenticalPayloadAndEqualMarshalledInstance() throws Exception {
        Integer v1 = Integer.valueOf(20260720);
        Integer v2 = Integer.valueOf(Integer.parseInt("20260720"));
        assertNotSame(v1, v2, "sanity: genuinely distinct Integer instances of the same value");

        byte[] p1 = payloadBytesOf(v1);
        byte[] p2 = payloadBytesOf(v2);
        assertArrayEquals(p1, p2,
                "equal boxed values must produce byte-identical ATOMIC_DER payload bytes");

        DerMarshalledInstance dmi1 = new DerMarshalledInstance(v1);
        DerMarshalledInstance dmi2 = new DerMarshalledInstance(v2);
        assertEquals(dmi1, dmi2,
                "MarshalledInstance.equals (payloadBytes-only) must hold for equal boxed values "
                + "-- the property Outrigger's EntryFieldIndex/EntryRep.matches byte-compare relies on");
    }

    /** Encodes {@code v} as a top-level {@code ATOMIC_DER} object-stream item via
     *  {@link DerMarshalInstanceOutput}, mirroring exactly what {@code EntryRep} does for a
     *  boxed-scalar entry field (no enclosing {@code @AtomicSerial} class). */
    private static byte[] payloadBytesOf(Object v) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DerMarshalInstanceOutput out = new DerMarshalInstanceOutput(bout, Collections.emptyList());
        out.writeObject(v);
        out.flush();
        return bout.toByteArray();
    }
}
