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

package au.net.zeus.jgdms.der.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Hex} test utility. Locks down the
 * {@code byte[] <-> String} round-trip (Task 0.1 acceptance) and the
 * decode-side tolerances and rejections.
 */
class HexTest {

    @Test
    void emptyRoundTrips() {
        assertEquals("", Hex.toHex(new byte[0]));
        assertArrayEquals(new byte[0], Hex.fromHex(""));
    }

    @Test
    void knownVectorEncodes() {
        byte[] in = {0x00, 0x01, 0x7F, (byte) 0x80, (byte) 0xFF, 0x30, 0x2A};
        assertEquals("00017f80ff302a", Hex.toHex(in));
    }

    @Test
    void knownVectorDecodes() {
        byte[] expected = {0x00, 0x01, 0x7F, (byte) 0x80, (byte) 0xFF};
        assertArrayEquals(expected, Hex.fromHex("00017f80ff"));
    }

    @Test
    void roundTripThroughEncodeThenDecode() {
        byte[] in = new byte[256];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte) i;
        }
        assertArrayEquals(in, Hex.fromHex(Hex.toHex(in)));
    }

    @Test
    void decodeIsCaseInsensitive() {
        assertArrayEquals(Hex.fromHex("deadBEEF"), Hex.fromHex("DEADbeef"));
        assertArrayEquals(new byte[] {(byte) 0xDE, (byte) 0xAD},
                Hex.fromHex("DEAD"));
    }

    @Test
    void decodeIgnoresPermittedSeparators() {
        byte[] expected = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        assertArrayEquals(expected, Hex.fromHex("DE AD BE EF"));
        assertArrayEquals(expected, Hex.fromHex("de:ad:be:ef"));
        assertArrayEquals(expected, Hex.fromHex("dead_beef"));
        assertArrayEquals(expected, Hex.fromHex("  de ad\n be\tef\r\n"));
    }

    @Test
    void decodeRejectsOddDigitCount() {
        assertThrows(IllegalArgumentException.class, () -> Hex.fromHex("abc"));
    }

    @Test
    void decodeRejectsIllegalCharacter() {
        assertThrows(IllegalArgumentException.class, () -> Hex.fromHex("zz"));
        assertThrows(IllegalArgumentException.class, () -> Hex.fromHex("12g4"));
    }

    @Test
    void dumpRendersOffsetHexAndAscii() {
        byte[] in = "DER".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String dump = Hex.dump(in);
        assertTrue(dump.contains("00000000"), dump);
        assertTrue(dump.contains("44 45 52"), dump);
        assertTrue(dump.contains("|DER|"), dump);
    }

    @Test
    void dumpEmptyIsEmpty() {
        assertEquals("", Hex.dump(new byte[0]));
    }
}
