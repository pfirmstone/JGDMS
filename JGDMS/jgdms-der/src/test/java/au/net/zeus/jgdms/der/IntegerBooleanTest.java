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

import static org.junit.jupiter.api.Assertions.*;

import au.net.zeus.jgdms.der.util.Hex;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

/**
 * Task 1.2 — INTEGER and BOOLEAN encoding and decoding.
 *
 * Covers:
 * <ul>
 *   <li>Minimal-byte INTEGER verified against known vectors.</li>
 *   <li>Negative, zero, and large (&gt;8 byte) integers round-trip.</li>
 *   <li>BOOLEAN decode rejects any octet other than 0x00 or 0xFF.</li>
 *   <li>jqwik property: random BigInteger round-trips.</li>
 * </ul>
 */
class IntegerBooleanTest {

    /* ------------------------------------------------------------------ */
    /* INTEGER known-answer vectors (encoding)                              */
    /* ------------------------------------------------------------------ */

    @Test
    void integer_zero_vector() {
        byte[] encoded = DerWriter.writeInteger(BigInteger.ZERO);
        // Expected: 02 01 00
        assertEquals("020100", Hex.toHex(encoded),
                "INTEGER(0) should encode as 02 01 00");
    }

    @Test
    void integer_one_vector() {
        byte[] encoded = DerWriter.writeInteger(BigInteger.ONE);
        // Expected: 02 01 01
        assertEquals("020101", Hex.toHex(encoded),
                "INTEGER(1) should encode as 02 01 01");
    }

    @Test
    void integer_127_vector() {
        byte[] encoded = DerWriter.writeInteger(BigInteger.valueOf(127));
        // 127 = 0x7F, fits in 1 byte: 02 01 7F
        assertEquals("02017f", Hex.toHex(encoded),
                "INTEGER(127) should encode as 02 01 7F");
    }

    @Test
    void integer_128_vector() {
        // 128 = 0x80; in two's-complement, needs 0x00 0x80 (2 bytes) to keep positive
        byte[] encoded = DerWriter.writeInteger(BigInteger.valueOf(128));
        // Expected: 02 02 00 80
        assertEquals("02020080", Hex.toHex(encoded),
                "INTEGER(128) should encode as 02 02 00 80 (needs leading 0x00 to stay positive)");
    }

    @Test
    void integer_minus1_vector() {
        // -1 in two's complement minimal = 0xFF (1 byte)
        byte[] encoded = DerWriter.writeInteger(BigInteger.valueOf(-1));
        // Expected: 02 01 FF
        assertEquals("0201ff", Hex.toHex(encoded),
                "INTEGER(-1) should encode as 02 01 FF");
    }

    @Test
    void integer_minus128_vector() {
        // -128 = 0x80 (1 byte, bit 7 set → negative)
        byte[] encoded = DerWriter.writeInteger(BigInteger.valueOf(-128));
        // Expected: 02 01 80
        assertEquals("020180", Hex.toHex(encoded),
                "INTEGER(-128) should encode as 02 01 80");
    }

    @Test
    void integer_minus129_vector() {
        // -129 = two's complement = 0xFF7F (but minimal: 0xFF 0x7F is 2 bytes)
        byte[] encoded = DerWriter.writeInteger(BigInteger.valueOf(-129));
        // -129 in two's complement minimal = FF 7F
        assertEquals("0202ff7f", Hex.toHex(encoded),
                "INTEGER(-129) should encode as 02 02 FF 7F");
    }

    @Test
    void integer_256_vector() {
        // 256 = 0x0100 → two's complement minimal = 01 00
        byte[] encoded = DerWriter.writeInteger(BigInteger.valueOf(256));
        assertEquals("02020100", Hex.toHex(encoded),
                "INTEGER(256) should encode as 02 02 01 00");
    }

    /* ------------------------------------------------------------------ */
    /* INTEGER round-trip tests                                             */
    /* ------------------------------------------------------------------ */

    private BigInteger integerRoundTrip(BigInteger value) throws DerException {
        byte[] encoded = DerWriter.writeInteger(value);
        DerReader reader = new DerReader(encoded);
        return reader.readInteger();
    }

    @Test
    void integerRoundTrip_zero() throws DerException {
        assertEquals(BigInteger.ZERO, integerRoundTrip(BigInteger.ZERO));
    }

    @Test
    void integerRoundTrip_positiveSmall() throws DerException {
        assertEquals(BigInteger.valueOf(42), integerRoundTrip(BigInteger.valueOf(42)));
    }

    @Test
    void integerRoundTrip_negative() throws DerException {
        assertEquals(BigInteger.valueOf(-42), integerRoundTrip(BigInteger.valueOf(-42)));
    }

    @Test
    void integerRoundTrip_largeLong() throws DerException {
        BigInteger v = BigInteger.valueOf(Long.MAX_VALUE);
        assertEquals(v, integerRoundTrip(v));
    }

    @Test
    void integerRoundTrip_negLargeLong() throws DerException {
        BigInteger v = BigInteger.valueOf(Long.MIN_VALUE);
        assertEquals(v, integerRoundTrip(v));
    }

    @Test
    void integerRoundTrip_moreThan8Bytes() throws DerException {
        // 9-byte positive integer
        BigInteger v = new BigInteger("12345678901234567890123456789012345678901");
        assertEquals(v, integerRoundTrip(v));
    }

    @Test
    void integerRoundTrip_moreThan8Bytes_negative() throws DerException {
        BigInteger v = new BigInteger("-98765432109876543210987654321098765432109");
        assertEquals(v, integerRoundTrip(v));
    }

    /* ------------------------------------------------------------------ */
    /* INTEGER decoder rejects non-canonical forms                         */
    /* ------------------------------------------------------------------ */

    @Test
    void integerDecoder_rejectsZeroLength() {
        // 02 00 — INTEGER with zero-length content
        byte[] buf = Hex.fromHex("0200");
        DerReader reader = new DerReader(buf);
        assertThrows(DerException.class, reader::readInteger,
                "INTEGER with zero-length content must be rejected");
    }

    @Test
    void integerDecoder_rejectsNonMinimal_leading00() {
        // 02 02 00 01 — INTEGER(1) with unnecessary leading 0x00 byte (non-minimal)
        byte[] buf = Hex.fromHex("02020001");
        DerReader reader = new DerReader(buf);
        DerException ex = assertThrows(DerException.class, reader::readInteger,
                "INTEGER with non-minimal leading 0x00 must be rejected");
        assertTrue(ex.getMessage().contains("Non-canonical") || ex.getMessage().contains("non-canonical"),
                "Message should mention non-canonical: " + ex.getMessage());
    }

    @Test
    void integerDecoder_rejectsNonMinimal_leadingFF() {
        // 02 02 FF FF — would be -1 but non-minimal (FF alone suffices)
        // Actually -1 = FF, and -256 = FF 00, so FF FF = -257? No:
        // FF in 1 byte = -1; FF FF in 2 bytes: that's -1 too, non-minimal!
        // Actually 0xFF 0xFF signed: the first 0xFF with bit-7 set means negative.
        // Two's complement of 0xFF 0xFF = -1 (since 0xFFFF as signed 16-bit = -1)
        // So FF FF non-minimally encodes -1 (should be just FF)
        byte[] buf = Hex.fromHex("0202ffff");
        DerReader reader = new DerReader(buf);
        DerException ex = assertThrows(DerException.class, reader::readInteger,
                "INTEGER with non-minimal leading 0xFF must be rejected (0xFFFF = -1 = 0xFF)");
        assertTrue(ex.getMessage().contains("Non-canonical") || ex.getMessage().contains("non-canonical"),
                "Message should mention non-canonical: " + ex.getMessage());
    }

    /* ------------------------------------------------------------------ */
    /* BOOLEAN                                                              */
    /* ------------------------------------------------------------------ */

    @Test
    void booleanFalseEncodes() {
        byte[] enc = DerWriter.writeBoolean(false);
        assertArrayEquals(Hex.fromHex("010100"), enc,
                "false should encode as 01 01 00; got: " + Hex.toHex(enc));
    }

    @Test
    void booleanTrueEncodes() {
        byte[] enc = DerWriter.writeBoolean(true);
        assertArrayEquals(Hex.fromHex("0101ff"), enc,
                "true should encode as 01 01 FF; got: " + Hex.toHex(enc));
    }

    @Test
    void booleanRoundTrip_false() throws DerException {
        assertFalse(new DerReader(DerWriter.writeBoolean(false)).readBoolean());
    }

    @Test
    void booleanRoundTrip_true() throws DerException {
        assertTrue(new DerReader(DerWriter.writeBoolean(true)).readBoolean());
    }

    @Test
    void booleanDecoder_rejectsNonCanonical_0x01() {
        // 01 01 01 — BOOLEAN with content 0x01 (not 0x00 or 0xFF — BER-permitted, DER-illegal)
        byte[] buf = Hex.fromHex("010101");
        DerReader reader = new DerReader(buf);
        assertThrows(DerException.class, reader::readBoolean,
                "BOOLEAN content 0x01 must be rejected in DER");
    }

    @Test
    void booleanDecoder_rejectsNonCanonical_0x80() {
        // 01 01 80 — BER "true" but not canonical
        byte[] buf = Hex.fromHex("010180");
        DerReader reader = new DerReader(buf);
        assertThrows(DerException.class, reader::readBoolean,
                "BOOLEAN content 0x80 must be rejected in DER");
    }

    @Test
    void booleanDecoder_rejectsLengthNot1() {
        // 01 02 00 00 — BOOLEAN with length 2 (wrong)
        byte[] buf = Hex.fromHex("01020000");
        DerReader reader = new DerReader(buf);
        assertThrows(DerException.class, reader::readBoolean,
                "BOOLEAN with content length != 1 must be rejected");
    }

    /* ------------------------------------------------------------------ */
    /* jqwik property: random BigInteger round-trips                        */
    /* ------------------------------------------------------------------ */

    @Property(tries = 500)
    void integerRoundTripsArbitraryBigInteger(@ForAll BigInteger value)
            throws DerException {
        BigInteger decoded = integerRoundTrip(value);
        assertEquals(value, decoded,
                "INTEGER round-trip failed for " + value
                + "; encoded: " + Hex.toHex(DerWriter.writeInteger(value)));
    }
}
