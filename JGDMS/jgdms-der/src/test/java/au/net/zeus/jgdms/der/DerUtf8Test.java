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

import au.net.zeus.jgdms.der.marshal.fixtures.VersionedRecord;
import au.net.zeus.jgdms.der.stream.DerObjectStreamTestSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Strict UTF-8 in both directions (board recommendation, 2026-10-18). */
class DerUtf8Test {

    private static byte[] b(int... v) {
        byte[] r = new byte[v.length];
        for (int i = 0; i < v.length; i++) r[i] = (byte) v[i];
        return r;
    }

    private static final byte[][] MALFORMED = {
        b(0xFF), b(0xFE), b(0xC0, 0x80), b(0xC1, 0xBF), b(0xE0, 0x80, 0x80),
        b(0xED, 0xA0, 0x80), b(0xED, 0xBF, 0xBF), b(0xE2, 0x82), b(0xE2), b(0xF0, 0x90, 0x80),
        b(0xF4, 0x90, 0x80, 0x80), b(0xF8, 0x88, 0x80, 0x80, 0x80), b(0x80), b(0xBF),
        b('a', 0xFF, 'b'), b('a', 0xC3),
    };

    @Test
    void decodeRejectsEveryMalformedForm() {
        for (byte[] m : MALFORMED) {
            assertThrows(DerException.class, () -> DerUtf8.decode(m), java.util.Arrays.toString(m));
        }
    }

    @Test
    void decodeAcceptsWellFormedIncludingEdgesAndRealReplacementChar() throws Exception {
        String[] ok = {"", "a", "h\u00E9llo", "\u20AC", "\u0000", "\uFFFD", "a\uFFFDb",
                "\uD83D\uDE00", new String(Character.toChars(0x10FFFF)), "\u07FF", "\u0800", "\uFFFF"};
        for (String s : ok) {
            byte[] enc = s.getBytes(StandardCharsets.UTF_8);
            assertEquals(s, DerUtf8.decode(enc), "decode " + s);
            assertArrayEquals(enc, DerUtf8.encode(s), "encode " + s);
        }
    }

    @Test
    void decodeHonoursOffsetAndLength() throws Exception {
        byte[] buf = b('x', 0xC3, 0xA9, 'y', 0xFF);
        assertEquals("\u00E9", DerUtf8.decode(buf, 1, 2));
        assertThrows(DerException.class, () -> DerUtf8.decode(buf, 3, 2));
    }

    @Test
    void encodeRejectsUnpairedAndReversedSurrogates() {
        String[] bad = {"\uD800", "a\uD800", "\uDC00", "\uDC00\uD800", "x\uD83D", "\uD83D\uD83D\uDE00"};
        for (String s : bad) {
            assertThrows(DerException.class, () -> DerUtf8.encode(s), "encode " + s.length());
        }
    }

    // ---- the readers/writers that use it ----

    @Test
    void readUtf8StringRejectsMalformedContent() {
        for (byte[] m : MALFORMED) {
            byte[] tlv = DerWriter.writeTlv(Tag.UTF8STRING, m);
            assertThrows(DerException.class, () -> new DerReader(tlv).readUtf8String(),
                    java.util.Arrays.toString(m));
        }
    }

    @Test
    void writeUtf8StringRejectsAnUnpairedSurrogate() {
        assertThrows(IllegalArgumentException.class, () -> DerWriter.writeUtf8String("a\uD800"));
        assertDoesNotThrow(() -> DerWriter.writeUtf8String("a\uD83D\uDE00"));
    }

    // ---- through the object stream ----

    @Test
    void streamStringItemWithMalformedUtf8IsRejected() throws Exception {
        for (byte[] m : MALFORMED) {
            assertThrows(IOException.class, () -> DerObjectStreamTestSupport.readStringItem(m),
                    java.util.Arrays.toString(m));
        }
        assertEquals("h\u00E9llo", DerObjectStreamTestSupport.readStringItem(
                "h\u00E9llo".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void encodingAStringWithALoneSurrogateFailsLoudlyInsteadOfWritingQuestionMark() {
        assertThrows(IOException.class, () -> DerObjectStreamTestSupport.writeString("a\uD800"));
        assertThrows(IOException.class,
                () -> DerObjectStreamTestSupport.writeObject(new VersionedRecord(1, "a\uD800", "x")));
    }
}
