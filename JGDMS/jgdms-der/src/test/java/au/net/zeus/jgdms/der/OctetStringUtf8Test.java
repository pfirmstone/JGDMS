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

import java.nio.charset.StandardCharsets;

/**
 * Task 1.3 — OCTET STRING and UTF8String encoding and decoding.
 *
 * Covers:
 * <ul>
 *   <li>Empty, ASCII, and multi-byte UTF-8 (emoji, CJK) round-trip for UTF8String.</li>
 *   <li>OCTET STRING preserves arbitrary bytes including 0x00 and high bytes.</li>
 *   <li>Empty OCTET STRING (04 00) round-trips.</li>
 *   <li>jqwik property: random Java String round-trips through UTF8String.</li>
 *   <li>jqwik property: random byte arrays round-trip through OCTET STRING.</li>
 * </ul>
 */
class OctetStringUtf8Test {

    /* ------------------------------------------------------------------ */
    /* OCTET STRING                                                         */
    /* ------------------------------------------------------------------ */

    @Test
    void octetString_empty() throws DerException {
        byte[] encoded = DerWriter.writeOctetString(new byte[0]);
        assertArrayEquals(Hex.fromHex("0400"), encoded,
                "Empty OCTET STRING should encode as 04 00; got: " + Hex.toHex(encoded));
        byte[] decoded = new DerReader(encoded).readOctetString();
        assertArrayEquals(new byte[0], decoded, "Empty OCTET STRING round-trip failed");
    }

    @Test
    void octetString_singleZeroByte() throws DerException {
        byte[] input = new byte[]{0x00};
        byte[] encoded = DerWriter.writeOctetString(input);
        assertArrayEquals(Hex.fromHex("040100"), encoded,
                "OCTET STRING {0x00} should encode as 04 01 00; got: " + Hex.toHex(encoded));
        assertArrayEquals(input, new DerReader(encoded).readOctetString());
    }

    @Test
    void octetString_highBytes() throws DerException {
        byte[] input = new byte[]{(byte)0xFF, (byte)0x80, (byte)0xFE, (byte)0x00};
        byte[] encoded = DerWriter.writeOctetString(input);
        assertArrayEquals(input, new DerReader(encoded).readOctetString(),
                "OCTET STRING with high bytes must round-trip");
    }

    @Test
    void octetString_allByteValues() throws DerException {
        byte[] input = new byte[256];
        for (int i = 0; i < 256; i++) input[i] = (byte) i;
        byte[] encoded = DerWriter.writeOctetString(input);
        assertArrayEquals(input, new DerReader(encoded).readOctetString(),
                "OCTET STRING with all 256 byte values must round-trip");
    }

    @Test
    void octetString_withNullBytes() throws DerException {
        byte[] input = new byte[]{0x01, 0x00, 0x02, 0x00, 0x03};
        assertArrayEquals(input, new DerReader(DerWriter.writeOctetString(input)).readOctetString(),
                "OCTET STRING with embedded nulls must round-trip");
    }

    @Property(tries = 300)
    void octetStringRoundTripsArbitraryBytes(@ForAll byte[] bytes) throws DerException {
        byte[] encoded = DerWriter.writeOctetString(bytes);
        byte[] decoded = new DerReader(encoded).readOctetString();
        assertArrayEquals(bytes, decoded,
                "OCTET STRING round-trip failed for "
                + Hex.toHex(bytes));
    }

    /* ------------------------------------------------------------------ */
    /* UTF8String                                                           */
    /* ------------------------------------------------------------------ */

    @Test
    void utf8String_empty() throws DerException {
        byte[] encoded = DerWriter.writeUtf8String("");
        assertArrayEquals(Hex.fromHex("0c00"), encoded,
                "Empty UTF8String should encode as 0C 00; got: " + Hex.toHex(encoded));
        assertEquals("", new DerReader(encoded).readUtf8String(), "Empty UTF8String round-trip failed");
    }

    @Test
    void utf8String_ascii() throws DerException {
        String s = "Hello, JGDMS!";
        byte[] encoded = DerWriter.writeUtf8String(s);
        // tag 0x0C, length = 13, then UTF-8 bytes
        assertEquals("0c", Hex.toHex(encoded).substring(0, 2),
                "UTF8String tag must be 0x0C");
        assertEquals(s, new DerReader(encoded).readUtf8String(),
                "ASCII UTF8String round-trip failed");
    }

    @Test
    void utf8String_cjk() throws DerException {
        String s = "中文"; // Chinese "中文"
        byte[] encoded = DerWriter.writeUtf8String(s);
        // Each CJK character is 3 UTF-8 bytes; total content 6 bytes
        byte[] expectedUtf8 = s.getBytes(StandardCharsets.UTF_8);
        assertEquals(expectedUtf8.length, encoded.length - 2,
                "UTF8String CJK content length mismatch");
        assertEquals(s, new DerReader(encoded).readUtf8String(),
                "CJK UTF8String round-trip failed");
    }

    @Test
    void utf8String_emoji() throws DerException {
        // U+1F600 GRINNING FACE = 4 UTF-8 bytes
        String s = "😀"; // emoji in Java surrogate form
        byte[] encoded = DerWriter.writeUtf8String(s);
        assertEquals(s, new DerReader(encoded).readUtf8String(),
                "Emoji UTF8String round-trip failed; encoded: " + Hex.toHex(encoded));
    }

    @Test
    void utf8String_mixedContent() throws DerException {
        String s = "ABC 中文 😀 123";
        assertEquals(s, new DerReader(DerWriter.writeUtf8String(s)).readUtf8String(),
                "Mixed ASCII/CJK/emoji UTF8String round-trip failed");
    }

    @Test
    void utf8String_longAscii() throws DerException {
        // Create a 200-char string → content length > 127 → long-form length
        StringBuilder sb = new StringBuilder(200);
        for (int i = 0; i < 200; i++) sb.append((char)('A' + (i % 26)));
        String s = sb.toString();
        byte[] encoded = DerWriter.writeUtf8String(s);
        // Long-form length: first byte 0x81, second 0xC8 (200)
        assertEquals((byte) 0x81, encoded[1],
                "200-char string needs long-form length; expected 0x81 as second byte");
        assertEquals((byte) 0xC8, encoded[2],
                "200-char string long-form length should be 0xC8 (200)");
        assertEquals(s, new DerReader(encoded).readUtf8String());
    }

    @Property(tries = 500)
    void utf8StringRoundTripsArbitraryString(@ForAll String s) throws DerException {
        byte[] encoded = DerWriter.writeUtf8String(s);
        String decoded = new DerReader(encoded).readUtf8String();
        assertEquals(s, decoded,
                "UTF8String round-trip failed for string (hex of UTF-8): "
                + Hex.toHex(s.getBytes(StandardCharsets.UTF_8)));
    }
}
