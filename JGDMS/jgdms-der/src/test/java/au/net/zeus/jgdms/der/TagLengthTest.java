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

/**
 * Task 1.1 — Tag encoding/decoding and length encoding/decoding.
 *
 * Covers:
 * <ul>
 *   <li>Short-form lengths (0–127) round-trip.</li>
 *   <li>Long-form lengths (128–Integer.MAX_VALUE representative) round-trip.</li>
 *   <li>jqwik property: random non-negative int length → encode → decode → equals.</li>
 *   <li>Decoder rejects indefinite-length (0x80).</li>
 *   <li>Decoder rejects non-minimal long form (value ≤ 127 in long form; leading zero).</li>
 *   <li>Universal tag constants encode to expected octets.</li>
 *   <li>Tag encode/decode round-trip for all classes, both primitive and constructed,
 *       including high-tag-number form (≥ 31).</li>
 * </ul>
 */
class TagLengthTest {

    /* ------------------------------------------------------------------ */
    /* Universal tag constant encodings                                     */
    /* ------------------------------------------------------------------ */

    @Test
    void booleanTagEncodesTo0x01() {
        assertArrayEquals(new byte[]{0x01}, Tag.BOOLEAN.encode(),
                "BOOLEAN tag should encode to 0x01");
    }

    @Test
    void integerTagEncodesTo0x02() {
        assertArrayEquals(new byte[]{0x02}, Tag.INTEGER.encode(),
                "INTEGER tag should encode to 0x02");
    }

    @Test
    void octetStringTagEncodesTo0x04() {
        assertArrayEquals(new byte[]{0x04}, Tag.OCTET_STRING.encode(),
                "OCTET STRING tag should encode to 0x04");
    }

    @Test
    void utf8StringTagEncodesTo0x0C() {
        assertArrayEquals(new byte[]{0x0C}, Tag.UTF8STRING.encode(),
                "UTF8String tag should encode to 0x0C");
    }

    @Test
    void sequenceTagEncodesTo0x30() {
        assertArrayEquals(new byte[]{0x30}, Tag.SEQUENCE.encode(),
                "SEQUENCE tag should encode to 0x30 (universal, constructed, 16)");
    }

    /* ------------------------------------------------------------------ */
    /* Tag round-trips                                                      */
    /* ------------------------------------------------------------------ */

    @Test
    void lowTagNumberRoundTrips() throws DerException {
        for (int cls = 0; cls <= 3; cls++) {
            for (boolean constr : new boolean[]{false, true}) {
                for (int num = 0; num <= 30; num++) {
                    Tag original = new Tag(cls, constr, num);
                    byte[] encoded = original.encode();
                    assertEquals(1, encoded.length, "Low-tag-number form must be 1 byte");
                    Tag.DecodeResult result = Tag.decode(encoded, 0);
                    assertEquals(1, result.bytesRead(), "Should consume 1 byte");
                    assertEquals(original, result.tag(),
                            "Round-trip failed for " + original);
                }
            }
        }
    }

    @Test
    void highTagNumberRoundTrip_31() throws DerException {
        Tag t = new Tag(Tag.CLASS_CONTEXT, false, 31);
        byte[] encoded = t.encode();
        // First byte must have low 5 bits set to 0x1F
        assertEquals((byte) 0x9F, encoded[0],
                "Context primitive tag 31: first byte should be 0x9F");
        Tag.DecodeResult result = Tag.decode(encoded, 0);
        assertEquals(t, result.tag(), "Tag 31 round-trip failed");
    }

    @Test
    void highTagNumberRoundTrip_128() throws DerException {
        Tag t = new Tag(Tag.CLASS_APPLICATION, true, 128);
        byte[] encoded = t.encode();
        Tag.DecodeResult result = Tag.decode(encoded, 0);
        assertEquals(t, result.tag(), "Tag 128 round-trip failed");
        assertEquals(encoded.length, result.bytesRead());
    }

    @Test
    void highTagNumberRoundTrip_largeNumber() throws DerException {
        Tag t = new Tag(Tag.CLASS_PRIVATE, false, 16383);
        byte[] encoded = t.encode();
        Tag.DecodeResult result = Tag.decode(encoded, 0);
        assertEquals(t, result.tag(), "Tag 16383 round-trip failed");
    }

    @Test
    void highTagNumberNonMinimalIsRejected() {
        // Manually craft a high-tag-number encoding with value < 31 — invalid
        // e.g. context primitive, high-tag-number form, tag number 5
        // = 0x9F 0x05 — the 0x05 has no continuation bit
        byte[] nonMinimal = new byte[]{(byte) 0x9F, 0x05};
        assertThrows(DerException.class, () -> Tag.decode(nonMinimal, 0),
                "Should reject high-tag-number form for tag < 31");
    }

    /* ------------------------------------------------------------------ */
    /* Length encoding: short form                                          */
    /* ------------------------------------------------------------------ */

    @Test
    void shortFormLength_0() {
        byte[] enc = DerWriter.encodeLength(0);
        assertArrayEquals(new byte[]{0x00}, enc);
    }

    @Test
    void shortFormLength_1() {
        byte[] enc = DerWriter.encodeLength(1);
        assertArrayEquals(new byte[]{0x01}, enc);
    }

    @Test
    void shortFormLength_127() {
        byte[] enc = DerWriter.encodeLength(127);
        assertArrayEquals(new byte[]{0x7F}, enc);
    }

    /* ------------------------------------------------------------------ */
    /* Length encoding: long form                                           */
    /* ------------------------------------------------------------------ */

    @Test
    void longFormLength_128() {
        byte[] enc = DerWriter.encodeLength(128);
        // 0x81 0x80
        assertArrayEquals(new byte[]{(byte)0x81, (byte)0x80}, enc,
                "128 should use 2-byte long form: 0x81 0x80");
    }

    @Test
    void longFormLength_255() {
        byte[] enc = DerWriter.encodeLength(255);
        assertArrayEquals(new byte[]{(byte)0x81, (byte)0xFF}, enc,
                "255 should use 2-byte long form: 0x81 0xFF");
    }

    @Test
    void longFormLength_256() {
        byte[] enc = DerWriter.encodeLength(256);
        // 0x82 0x01 0x00
        assertArrayEquals(new byte[]{(byte)0x82, 0x01, 0x00}, enc,
                "256 should use 3-byte long form: 0x82 0x01 0x00");
    }

    @Test
    void longFormLength_65535() {
        byte[] enc = DerWriter.encodeLength(65535);
        assertArrayEquals(new byte[]{(byte)0x82, (byte)0xFF, (byte)0xFF}, enc,
                "65535 → 0x82 0xFF 0xFF");
    }

    @Test
    void longFormLength_65536() {
        byte[] enc = DerWriter.encodeLength(65536);
        assertArrayEquals(new byte[]{(byte)0x83, 0x01, 0x00, 0x00}, enc,
                "65536 → 0x83 0x01 0x00 0x00");
    }

    /* ------------------------------------------------------------------ */
    /* Length round-trip via DerReader decodeLength                        */
    /* ------------------------------------------------------------------ */

    private int roundTripLength(int length) throws DerException {
        // Wrap in a dummy TLV (INTEGER with zeros content) just to use the reader
        byte[] content = new byte[length];
        byte[] tlv = DerWriter.writeInteger(java.math.BigInteger.ZERO);
        // Actually, to test length round-trip independently, encode length
        // and decode it by reading it from a fake TLV header
        byte[] lengthBytes = DerWriter.encodeLength(length);
        // Build a fake buffer: tag (0x04) + length + zero-filled content
        byte[] buf = new byte[1 + lengthBytes.length + length];
        buf[0] = 0x04; // OCTET STRING tag
        System.arraycopy(lengthBytes, 0, buf, 1, lengthBytes.length);
        // content stays zeros
        DerReader reader = new DerReader(buf);
        DerReader.TlvHeader hdr = reader.readTlvHeader();
        return hdr.contentLength();
    }

    @Test
    void shortFormLengthsRoundTrip() throws DerException {
        for (int len = 0; len <= 127; len++) {
            assertEquals(len, roundTripLength(len),
                    "Short-form length " + len + " did not round-trip");
        }
    }

    @Test
    void longFormLength_128_roundTrip() throws DerException {
        assertEquals(128, roundTripLength(128));
    }

    @Test
    void longFormLength_255_roundTrip() throws DerException {
        assertEquals(255, roundTripLength(255));
    }

    @Test
    void longFormLength_256_roundTrip() throws DerException {
        assertEquals(256, roundTripLength(256));
    }

    @Test
    void longFormLength_1000_roundTrip() throws DerException {
        assertEquals(1000, roundTripLength(1000));
    }

    @Test
    void longFormLength_65536_roundTrip() throws DerException {
        assertEquals(65536, roundTripLength(65536));
    }

    /* ------------------------------------------------------------------ */
    /* Rejection of indefinite form                                         */
    /* ------------------------------------------------------------------ */

    @Test
    void indefiniteLengthIsRejected() {
        // 0x04 0x80 — OCTET STRING with indefinite-form length
        byte[] buf = new byte[]{0x04, (byte)0x80};
        DerReader reader = new DerReader(buf);
        DerException ex = assertThrows(DerException.class, reader::readTlvHeader,
                "Indefinite length (0x80) must be rejected");
        assertTrue(ex.getMessage().contains("ndefinite"), ex.getMessage());
    }

    /* ------------------------------------------------------------------ */
    /* Rejection of non-minimal long form                                   */
    /* ------------------------------------------------------------------ */

    @Test
    void nonMinimalLongForm_valueUnder128_isRejected() {
        // 0x04 0x81 0x01 — OCTET STRING, long form (n=1), value=1; non-minimal (should be 0x01)
        byte[] buf = new byte[]{0x04, (byte)0x81, 0x01, 0x00};
        DerReader reader = new DerReader(buf);
        DerException ex = assertThrows(DerException.class, reader::readTlvHeader,
                "Long form for length ≤ 127 must be rejected as non-canonical");
        assertTrue(ex.getMessage().contains("non-canonical") || ex.getMessage().contains("Non-canonical"),
                ex.getMessage());
    }

    @Test
    void nonMinimalLongForm_leadingZero_isRejected() {
        // 0x04 0x82 0x00 0x80 — long form with 2 length bytes, leading zero — non-minimal
        byte[] buf = new byte[]{0x04, (byte)0x82, 0x00, (byte)0x80};
        // Content would be 128 bytes but we add a few
        // The leading zero means we could represent 128 in just 1 long-form byte (0x81 0x80)
        DerReader reader = new DerReader(buf);
        assertThrows(DerException.class, reader::readTlvHeader,
                "Leading zero in multi-byte length must be rejected as non-canonical");
    }

    /* ------------------------------------------------------------------ */
    /* jqwik property: random length round-trips                            */
    /* ------------------------------------------------------------------ */

    @Property(tries = 200)
    void lengthRoundTrips(@ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 10000) int length)
            throws DerException {
        assertEquals(length, roundTripLength(length),
                "Length " + length + " failed round-trip");
    }
}
