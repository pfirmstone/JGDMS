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
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceCodec;
import au.net.zeus.jgdms.der.marshal.MarshalledInstanceRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.AtomicSerial;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Discriminating wire-invariant tests for STD-008 sec.17.3 (S7.6 deferral lifted with
 * STRICT canonicalization). The wire MUST be byte-identical for the same value across
 * all senders, enforced symmetrically on encoder AND decoder; tests prove each property.
 *
 * <p>All literals are ASCII + Unicode escapes -- no embedded multi-byte chars
 * (consistent with the project's ASCII-only source policy).
 */
class FloatDoubleCharCanonicalTest {

    // =========================================================================
    // Fixtures (one per primitive type)
    // =========================================================================

    @AtomicSerial
    public static final class FloatRecord {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("v", float.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, FloatRecord r) throws IOException {
            arg.put("v", r.v); arg.writeArgs();
        }
        private final float v;
        public FloatRecord(float v) { this.v = v; }
        public FloatRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.v = arg.get("v", 0.0f);
        }
        public float v() { return v; }
    }

    @AtomicSerial
    public static final class DoubleRecord {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("v", double.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, DoubleRecord r) throws IOException {
            arg.put("v", r.v); arg.writeArgs();
        }
        private final double v;
        public DoubleRecord(double v) { this.v = v; }
        public DoubleRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.v = arg.get("v", 0.0);
        }
        public double v() { return v; }
    }

    @AtomicSerial
    public static final class CharRecord {
        public static AtomicSerial.SerialForm[] serialForm() {
            return new AtomicSerial.SerialForm[]{ new AtomicSerial.SerialForm("v", char.class) };
        }
        public static void serialize(AtomicSerial.PutArg arg, CharRecord r) throws IOException {
            arg.put("v", r.v); arg.writeArgs();
        }
        private final char v;
        public CharRecord(char v) { this.v = v; }
        public CharRecord(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
            this.v = arg.get("v", (char) 0);
        }
        public char v() { return v; }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static <T> MarshalledInstanceRecord recordOf(T value, Class<T> cls) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(cls);
        byte[] payload = ObjectCodec.encodeHierarchy(value, chain);
        return MarshalledInstanceRecord.fromChain(chain, payload);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value, Class<T> cls) throws Exception {
        MarshalledInstanceRecord rec = recordOf(value, cls);
        return (T) MarshalledInstanceCodec.decodeMarshalledInstance(rec, cls).object();
    }

    /** Hand-builds a hostile record with a controlled inner field-value TLV. */
    private static <T> MarshalledInstanceRecord buildHostile(Class<T> cls, byte[] fieldValueTlv) throws Exception {
        SchemaChain.Result chain = SchemaGenerator.generateChain(cls);
        byte[] classSeq = DerWriter.writeSequence(List.of(fieldValueTlv));
        byte[] payload  = DerWriter.writeSequence(List.of(classSeq));
        return MarshalledInstanceRecord.fromChain(chain, payload);
    }

    private static byte[] floatOctets(int bits) {
        return new byte[]{
                (byte)(bits >>> 24), (byte)(bits >>> 16),
                (byte)(bits >>>  8), (byte) bits
        };
    }

    private static byte[] doubleOctets(long bits) {
        byte[] r = new byte[8];
        for (int i = 7; i >= 0; i--) { r[i] = (byte)(bits & 0xFF); bits >>>= 8; }
        return r;
    }

    // =========================================================================
    // FLOAT
    // =========================================================================

    @Test
    void floatRoundTrip_finiteAndSpecial() throws Exception {
        for (float v : new float[]{ 0.0f, 1.0f, -1.0f, Float.MAX_VALUE, Float.MIN_VALUE,
                Float.MIN_NORMAL, -Float.MAX_VALUE, 3.14159f, 1e-30f,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
            assertEquals(v, roundTrip(new FloatRecord(v), FloatRecord.class).v(), 0.0f,
                    "round-trip mismatch for " + v);
        }
        assertTrue(Float.isNaN(roundTrip(new FloatRecord(Float.NaN), FloatRecord.class).v()));
    }

    /** Encoder canonicalizes -0.0 to +0.0: BYTE-IDENTICAL wire encoding. */
    @Test
    void floatEncoder_canonicalizesNegativeZero() throws Exception {
        MarshalledInstanceRecord posZero = recordOf(new FloatRecord(+0.0f), FloatRecord.class);
        MarshalledInstanceRecord negZero = recordOf(new FloatRecord(-0.0f), FloatRecord.class);
        assertArrayEquals(posZero.payloadBytes(), negZero.payloadBytes(),
                "encoder must map -0.0 -> +0.0 (byte-identical wire) for Entry-matching determinism");
        // And -0.0 decodes as +0.0 bits, not -0.0 bits.
        FloatRecord r = roundTrip(new FloatRecord(-0.0f), FloatRecord.class);
        assertEquals(Float.floatToRawIntBits(+0.0f), Float.floatToRawIntBits(r.v()),
                "decoded value must have +0.0 bits, not -0.0 bits");
    }

    /** DISCRIMINATING: a hostile sender shipping non-canonical NaN bits is REJECTED. */
    @Test
    void floatDecoder_rejectsNonCanonicalNaN() throws Exception {
        // 0x7FC00001: same NaN class, different mantissa than canonical 0x7FC00000.
        MarshalledInstanceRecord hostile = buildHostile(FloatRecord.class,
                DerWriter.writeOctetString(floatOctets(0x7FC00001)));
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, FloatRecord.class),
                "non-canonical NaN MUST be rejected fail-secure (STD-008 sec.17.3.1)");
    }

    /** A signaling-NaN bit pattern (mantissa MSB clear) is non-canonical -> rejected. */
    @Test
    void floatDecoder_rejectsSignalingNaN() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(FloatRecord.class,
                DerWriter.writeOctetString(floatOctets(0x7F800001)));
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, FloatRecord.class));
    }

    /** DISCRIMINATING: -0.0 BIT PATTERN on the wire is REJECTED. */
    @Test
    void floatDecoder_rejectsNegativeZeroBits() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(FloatRecord.class,
                DerWriter.writeOctetString(floatOctets(0x80000000)));
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, FloatRecord.class),
                "-0.0 bit pattern MUST be rejected on decode (canonical is +0.0)");
    }

    @Test
    void floatDecoder_rejectsWrongLength() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(FloatRecord.class,
                DerWriter.writeOctetString(new byte[]{1, 2, 3})); // 3 bytes, must be 4
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, FloatRecord.class));
    }

    /** DETERMINISM: distinct NaN bit patterns produce IDENTICAL wire bytes (encoder canonicalizes). */
    @Test
    void floatEncoder_determinismAcrossNaNVariants() throws Exception {
        float nanA = Float.intBitsToFloat(0x7FC00000);   // canonical bit pattern
        float nanB = Float.intBitsToFloat(0x7FC00001);   // different NaN bit pattern
        MarshalledInstanceRecord recA = recordOf(new FloatRecord(nanA), FloatRecord.class);
        MarshalledInstanceRecord recB = recordOf(new FloatRecord(nanB), FloatRecord.class);
        assertArrayEquals(recA.payloadBytes(), recB.payloadBytes(),
                "encoder must canonicalize ANY NaN -> wire bytes identical (Entry-matching)");
    }

    // =========================================================================
    // DOUBLE
    // =========================================================================

    @Test
    void doubleRoundTrip_finiteAndSpecial() throws Exception {
        for (double v : new double[]{ 0.0, 1.0, -1.0, Math.PI, Double.MAX_VALUE,
                Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertEquals(v, roundTrip(new DoubleRecord(v), DoubleRecord.class).v(), 0.0,
                    "round-trip mismatch for " + v);
        }
        assertTrue(Double.isNaN(roundTrip(new DoubleRecord(Double.NaN), DoubleRecord.class).v()));
    }

    @Test
    void doubleEncoder_canonicalizesNegativeZero() throws Exception {
        MarshalledInstanceRecord pos = recordOf(new DoubleRecord(+0.0), DoubleRecord.class);
        MarshalledInstanceRecord neg = recordOf(new DoubleRecord(-0.0), DoubleRecord.class);
        assertArrayEquals(pos.payloadBytes(), neg.payloadBytes(),
                "encoder must map -0.0 -> +0.0 (byte-identical) for double");
    }

    @Test
    void doubleDecoder_rejectsNonCanonicalNaN() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(DoubleRecord.class,
                DerWriter.writeOctetString(doubleOctets(0x7FF8000000000001L)));
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, DoubleRecord.class));
    }

    @Test
    void doubleDecoder_rejectsNegativeZeroBits() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(DoubleRecord.class,
                DerWriter.writeOctetString(doubleOctets(0x8000000000000000L)));
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, DoubleRecord.class));
    }

    @Test
    void doubleDecoder_rejectsWrongLength() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(DoubleRecord.class,
                DerWriter.writeOctetString(new byte[]{1, 2, 3, 4, 5, 6, 7})); // 7 bytes, must be 8
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, DoubleRecord.class));
    }

    @Test
    void doubleEncoder_determinismAcrossNaNVariants() throws Exception {
        double nanA = Double.longBitsToDouble(0x7FF8000000000000L);
        double nanB = Double.longBitsToDouble(0x7FF8000000000001L);
        MarshalledInstanceRecord recA = recordOf(new DoubleRecord(nanA), DoubleRecord.class);
        MarshalledInstanceRecord recB = recordOf(new DoubleRecord(nanB), DoubleRecord.class);
        assertArrayEquals(recA.payloadBytes(), recB.payloadBytes(),
                "encoder must canonicalize ANY NaN -> wire bytes identical");
    }

    // =========================================================================
    // CHAR
    // =========================================================================

    @Test
    void charRoundTrip_validCodepoints() throws Exception {
        // ASCII + a few BMP non-surrogate codepoints by integer (no literal multi-byte chars).
        // U+D7FF = last code unit before surrogates; U+E000 = first code unit after.
        char[] samples = { (char)0x0020, (char)0x0041, (char)0x007A, (char)0x0030,
                (char)0x00E9, (char)0x4E2D, (char)0xD7FF, (char)0xE000, (char)0xFFFD };
        for (char c : samples) {
            assertEquals(c, roundTrip(new CharRecord(c), CharRecord.class).v(),
                    "round-trip mismatch for char 0x" + Integer.toHexString(c));
        }
    }

    /** Surrogate code units are NOT valid Unicode codepoints; encoder rejects. */
    @Test
    void charEncoder_rejectsSurrogate() {
        assertThrows(DerException.class,
                () -> recordOf(new CharRecord((char)0xD800), CharRecord.class),
                "encoder must reject high-surrogate code unit");
        assertThrows(DerException.class,
                () -> recordOf(new CharRecord((char)0xDFFF), CharRecord.class),
                "encoder must reject low-surrogate code unit");
    }

    @Test
    void charDecoder_rejectsSurrogateCodepoint() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(CharRecord.class,
                DerWriter.writeInteger(BigInteger.valueOf(0xD800)));
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, CharRecord.class));
    }

    /** Codepoint > 0xFFFF doesn't fit Java char and is rejected (supplementary plane needs String). */
    @Test
    void charDecoder_rejectsSupplementaryPlaneCodepoint() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(CharRecord.class,
                DerWriter.writeInteger(BigInteger.valueOf(0x10000)));
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, CharRecord.class));
    }

    @Test
    void charDecoder_rejectsNegativeCodepoint() throws Exception {
        MarshalledInstanceRecord hostile = buildHostile(CharRecord.class,
                DerWriter.writeInteger(BigInteger.valueOf(-1)));
        assertThrows(IOException.class,
                () -> MarshalledInstanceCodec.decodeMarshalledInstance(hostile, CharRecord.class));
    }
}
