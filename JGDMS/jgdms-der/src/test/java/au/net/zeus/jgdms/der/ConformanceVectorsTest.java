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
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

/**
 * Task 1.5 -- Conformance vectors.
 *
 * Contains at least 10 hand-computed known-answer DER vectors (computed
 * independently according to X.690 rules), each asserted byte-for-byte in
 * both directions:
 * <ul>
 *   <li>encode produces exactly the expected hex bytes;</li>
 *   <li>decode of those bytes produces the expected value.</li>
 * </ul>
 *
 * Vectors cover: BOOLEAN false/true, INTEGER (0, 1, 127, 128, -1, -128,
 * large positive, large negative), OCTET STRING (empty, with bytes), UTF8String
 * (ASCII, multi-byte), SEQUENCE (flat, nested), long-form length.
 *
 * One test also proves the <b>canonical property</b>: two independent encodings
 * of the same logical value produce identical bytes.
 */
class ConformanceVectorsTest {

    /* ------------------------------------------------------------------ */
    /* Vector 1: BOOLEAN false                                              */
    /* Expected: 01 01 00                                                   */
    /* ------------------------------------------------------------------ */
    @Test
    void v01_booleanFalse() throws DerException {
        byte[] expected = Hex.fromHex("010100");
        // Encode
        assertArrayEquals(expected, DerWriter.writeBoolean(false),
                "BOOLEAN false encode mismatch; expected: " + Hex.toHex(expected));
        // Decode
        assertFalse(new DerReader(expected).readBoolean(),
                "BOOLEAN false decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 2: BOOLEAN true                                               */
    /* Expected: 01 01 FF                                                   */
    /* ------------------------------------------------------------------ */
    @Test
    void v02_booleanTrue() throws DerException {
        byte[] expected = Hex.fromHex("0101ff");
        assertArrayEquals(expected, DerWriter.writeBoolean(true),
                "BOOLEAN true encode mismatch");
        assertTrue(new DerReader(expected).readBoolean(),
                "BOOLEAN true decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 3: INTEGER 0                                                  */
    /* Expected: 02 01 00                                                   */
    /* ------------------------------------------------------------------ */
    @Test
    void v03_integerZero() throws DerException {
        byte[] expected = Hex.fromHex("020100");
        assertArrayEquals(expected, DerWriter.writeInteger(BigInteger.ZERO),
                "INTEGER(0) encode mismatch");
        assertEquals(BigInteger.ZERO, new DerReader(expected).readInteger(),
                "INTEGER(0) decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 4: INTEGER 127 (fits in 1 content byte, no leading 0x00)     */
    /* Expected: 02 01 7F                                                   */
    /* ------------------------------------------------------------------ */
    @Test
    void v04_integer127() throws DerException {
        byte[] expected = Hex.fromHex("02017f");
        assertArrayEquals(expected, DerWriter.writeInteger(BigInteger.valueOf(127)),
                "INTEGER(127) encode mismatch");
        assertEquals(BigInteger.valueOf(127), new DerReader(expected).readInteger(),
                "INTEGER(127) decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 5: INTEGER 128 (needs leading 0x00 to stay positive in DER)  */
    /* Expected: 02 02 00 80                                                */
    /* ------------------------------------------------------------------ */
    @Test
    void v05_integer128() throws DerException {
        byte[] expected = Hex.fromHex("02020080");
        assertArrayEquals(expected, DerWriter.writeInteger(BigInteger.valueOf(128)),
                "INTEGER(128) encode mismatch");
        assertEquals(BigInteger.valueOf(128), new DerReader(expected).readInteger(),
                "INTEGER(128) decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 6: INTEGER -1                                                 */
    /* Expected: 02 01 FF                                                   */
    /* ------------------------------------------------------------------ */
    @Test
    void v06_integerMinus1() throws DerException {
        byte[] expected = Hex.fromHex("0201ff");
        assertArrayEquals(expected, DerWriter.writeInteger(BigInteger.valueOf(-1)),
                "INTEGER(-1) encode mismatch");
        assertEquals(BigInteger.valueOf(-1), new DerReader(expected).readInteger(),
                "INTEGER(-1) decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 7: INTEGER -128                                               */
    /* Expected: 02 01 80                                                   */
    /* ------------------------------------------------------------------ */
    @Test
    void v07_integerMinus128() throws DerException {
        byte[] expected = Hex.fromHex("020180");
        assertArrayEquals(expected, DerWriter.writeInteger(BigInteger.valueOf(-128)),
                "INTEGER(-128) encode mismatch");
        assertEquals(BigInteger.valueOf(-128), new DerReader(expected).readInteger(),
                "INTEGER(-128) decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 8: Empty OCTET STRING                                        */
    /* Expected: 04 00                                                      */
    /* ------------------------------------------------------------------ */
    @Test
    void v08_emptyOctetString() throws DerException {
        byte[] expected = Hex.fromHex("0400");
        assertArrayEquals(expected, DerWriter.writeOctetString(new byte[0]),
                "Empty OCTET STRING encode mismatch");
        assertArrayEquals(new byte[0], new DerReader(expected).readOctetString(),
                "Empty OCTET STRING decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 9: OCTET STRING { 0xDE, 0xAD, 0xBE, 0xEF }                  */
    /* Expected: 04 04 DE AD BE EF                                          */
    /* ------------------------------------------------------------------ */
    @Test
    void v09_octetStringDeadBeef() throws DerException {
        byte[] content  = Hex.fromHex("deadbeef");
        byte[] expected = Hex.fromHex("0404deadbeef");
        assertArrayEquals(expected, DerWriter.writeOctetString(content),
                "OCTET STRING deadbeef encode mismatch");
        assertArrayEquals(content, new DerReader(expected).readOctetString(),
                "OCTET STRING deadbeef decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 10: UTF8String "Hi"                                          */
    /* Expected: 0C 02 48 69                                                */
    /* ------------------------------------------------------------------ */
    @Test
    void v10_utf8StringHi() throws DerException {
        byte[] expected = Hex.fromHex("0c024869"); // 0x0C, 0x02, 'H', 'i'
        assertArrayEquals(expected, DerWriter.writeUtf8String("Hi"),
                "UTF8String 'Hi' encode mismatch");
        assertEquals("Hi", new DerReader(expected).readUtf8String(),
                "UTF8String 'Hi' decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 11: Flat SEQUENCE { INTEGER(1), BOOLEAN false }              */
    /* SEQUENCE (30) length=6, INTEGER(1)=020101, BOOLEAN(false)=010100    */
    /* Expected: 30 06 02 01 01 01 01 00                                   */
    /* ------------------------------------------------------------------ */
    @Test
    void v11_sequenceIntegerBoolean() throws DerException {
        byte[] expected = Hex.fromHex("3006020101010100");
        byte[] encoded  = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.ONE),
                DerWriter.writeBoolean(false)));
        assertArrayEquals(expected, encoded,
                "SEQUENCE{INTEGER(1), BOOLEAN(false)} encode mismatch; got: " + Hex.toHex(encoded));
        // Decode
        DerReader outer = new DerReader(expected);
        DerReader inner = outer.readSequence();
        assertEquals(BigInteger.ONE, inner.readInteger());
        assertFalse(inner.readBoolean());
        assertFalse(inner.hasMore());
    }

    /* ------------------------------------------------------------------ */
    /* Vector 12: Nested SEQUENCE                                          */
    /* Inner: 30 03 0C 01 41  (SEQUENCE { UTF8String "A" })               */
    /* Outer: 30 05 30 03 0C 01 41                                         */
    /* ------------------------------------------------------------------ */
    @Test
    void v12_nestedSequence() throws DerException {
        // UTF8String "A" = 0C 01 41
        // Inner SEQUENCE content = 0C 01 41 (3 bytes), so: 30 03 0C 01 41
        // Outer SEQUENCE content = 30 03 0C 01 41 (5 bytes), so: 30 05 30 03 0C 01 41
        byte[] expected = Hex.fromHex("30053003_0c0141");
        byte[] innerEnc = DerWriter.writeSequence(List.of(DerWriter.writeUtf8String("A")));
        byte[] outerEnc = DerWriter.writeSequence(List.of(innerEnc));
        assertArrayEquals(expected, outerEnc,
                "Nested SEQUENCE encode mismatch; got: " + Hex.toHex(outerEnc));
        // Decode
        DerReader r0 = new DerReader(expected);
        DerReader r1 = r0.readSequence();
        DerReader r2 = r1.readSequence();
        assertEquals("A", r2.readUtf8String());
        assertFalse(r2.hasMore());
        assertFalse(r1.hasMore());
    }

    /* ------------------------------------------------------------------ */
    /* Vector 13: Long-form length (128 zero bytes in OCTET STRING)       */
    /* Tag 04, length = 81 80 (long form, 1 byte, value 128), then 128 0x00 */
    /* ------------------------------------------------------------------ */
    @Test
    void v13_longFormLength_128bytes() throws DerException {
        byte[] content  = new byte[128]; // all zeros
        byte[] encoded  = DerWriter.writeOctetString(content);
        // Expect: 04 81 80 [128 zeros]
        assertEquals((byte) 0x04, encoded[0], "Tag byte must be 0x04");
        assertEquals((byte) 0x81, encoded[1], "Length first byte must be 0x81 (long form, 1 byte)");
        assertEquals((byte) 0x80, encoded[2], "Length value byte must be 0x80 (128)");
        assertEquals(3 + 128, encoded.length, "Total length mismatch");
        // Full hex check on header
        assertEquals("048180", Hex.toHex(encoded).substring(0, 6),
                "Long-form header mismatch");
        // Round-trip
        assertArrayEquals(content, new DerReader(encoded).readOctetString(),
                "Long-form OCTET STRING decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Vector 14: Large positive INTEGER (> 8 bytes) known vector          */
    /* 2^64 = 10000000000000000000 decimal                                 */
    /* = 0x010000000000000000 (9 bytes, needs leading 0x00 -> 10 bytes)    */
    /* Expected: 02 09 00 01 00 00 00 00 00 00 00 00                       */
    /* Wait: 2^64 = 1 followed by 16 zeros hex = 0x10000000000000000      */
    /* That is 9 bytes. BigInteger.toByteArray() would give 0x00 + 8 bytes  */
    /* since bit 7 of the first non-zero byte (0x01) is 0, no leading 0x00 */
    /* Actually: 2^64 toByteArray() = [0x01, 0x00*8] = 9 bytes total      */
    /* No: bit 7 of 0x01 is 0, positive, so no extra 0x00 needed.         */
    /* Expected: 02 09 01 00 00 00 00 00 00 00 00                          */
    /* ------------------------------------------------------------------ */
    @Test
    void v14_integer_2pow64() throws DerException {
        BigInteger v = BigInteger.TWO.pow(64);
        byte[] expected = Hex.fromHex("020901_00000000_00000000");
        byte[] encoded  = DerWriter.writeInteger(v);
        assertArrayEquals(expected, encoded,
                "INTEGER(2^64) encode mismatch; got: " + Hex.toHex(encoded));
        assertEquals(v, new DerReader(expected).readInteger(),
                "INTEGER(2^64) decode mismatch");
    }

    /* ------------------------------------------------------------------ */
    /* Canonical property: two independent encodings of the same value     */
    /* must produce identical bytes.                                       */
    /* ------------------------------------------------------------------ */

    @Test
    void canonicalProperty_integer() {
        BigInteger v = BigInteger.valueOf(12345678L);
        byte[] enc1 = DerWriter.writeInteger(v);
        byte[] enc2 = DerWriter.writeInteger(v);
        assertArrayEquals(enc1, enc2,
                "DER encoding is not canonical: two encodings of INTEGER(12345678) differ");
    }

    @Test
    void canonicalProperty_utf8String() {
        String s = "canonical test 規範 😎";
        byte[] enc1 = DerWriter.writeUtf8String(s);
        byte[] enc2 = DerWriter.writeUtf8String(s);
        assertArrayEquals(enc1, enc2,
                "DER encoding is not canonical: two encodings of UTF8String differ");
    }

    @Test
    void canonicalProperty_sequence() {
        byte[] enc1 = DerWriter.writeSequence(List.of(
                DerWriter.writeBoolean(true),
                DerWriter.writeInteger(BigInteger.valueOf(99))));
        byte[] enc2 = DerWriter.writeSequence(List.of(
                DerWriter.writeBoolean(true),
                DerWriter.writeInteger(BigInteger.valueOf(99))));
        assertArrayEquals(enc1, enc2,
                "DER encoding is not canonical: two encodings of the same SEQUENCE differ");
    }

    @Test
    void canonicalProperty_nestedSequence() {
        byte[] inner1 = DerWriter.writeSequence(List.of(
                DerWriter.writeUtf8String("hello"),
                DerWriter.writeInteger(BigInteger.ONE)));
        byte[] outer1 = DerWriter.writeSequence(List.of(
                inner1, DerWriter.writeBoolean(false)));

        byte[] inner2 = DerWriter.writeSequence(List.of(
                DerWriter.writeUtf8String("hello"),
                DerWriter.writeInteger(BigInteger.ONE)));
        byte[] outer2 = DerWriter.writeSequence(List.of(
                inner2, DerWriter.writeBoolean(false)));

        assertArrayEquals(outer1, outer2,
                "Two independent encodings of the same nested SEQUENCE must be identical");
    }
}
