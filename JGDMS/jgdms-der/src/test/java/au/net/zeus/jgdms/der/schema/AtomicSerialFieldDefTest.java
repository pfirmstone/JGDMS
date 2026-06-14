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

package au.net.zeus.jgdms.der.schema;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AtomicSerialFieldDef} — Phase 2 Task 2.1.
 *
 * Covers:
 * <ul>
 *   <li>2.1-a  Round-trip through DER.</li>
 *   <li>2.1-b  Fail-secure: empty wireName / wireType rejected at construction.</li>
 *   <li>2.1-c  Fail-secure: over-limit byte lengths rejected at construction.</li>
 *   <li>2.1-d  Fail-secure: over-limit DER content rejected at decode time.</li>
 *   <li>2.1-e  jqwik property: random valid records round-trip.</li>
 * </ul>
 */
class AtomicSerialFieldDefTest {

    // ------------------------------------------------------------------
    // 2.1-a  Round-trip through DER
    // ------------------------------------------------------------------

    @Test
    void roundTrip_simple() throws DerException {
        AtomicSerialFieldDef original = new AtomicSerialFieldDef("myField", "java.lang.String");
        byte[] der = original.encode();
        DerReader reader = new DerReader(der);
        AtomicSerialFieldDef decoded = AtomicSerialFieldDef.decode(reader);
        assertEquals(original, decoded);
        assertFalse(reader.hasMore(), "no trailing bytes");
    }

    @Test
    void roundTrip_unicodeFieldName() throws DerException {
        // Field name with multi-byte UTF-8 characters
        String name = "fieldé"; // é = 2 UTF-8 bytes
        String type = "au.net.zeus.Example";
        AtomicSerialFieldDef original = new AtomicSerialFieldDef(name, type);
        byte[] der = original.encode();
        DerReader reader = new DerReader(der);
        AtomicSerialFieldDef decoded = AtomicSerialFieldDef.decode(reader);
        assertEquals(original, decoded);
        assertEquals(name, decoded.wireName());
        assertEquals(type, decoded.wireType());
    }

    @Test
    void equalsAndHashCode() {
        AtomicSerialFieldDef a = new AtomicSerialFieldDef("x", "int");
        AtomicSerialFieldDef b = new AtomicSerialFieldDef("x", "int");
        AtomicSerialFieldDef c = new AtomicSerialFieldDef("y", "int");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    // ------------------------------------------------------------------
    // 2.1-b  Fail-secure: empty strings rejected at construction
    // ------------------------------------------------------------------

    @Test
    void construction_emptyWireName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new AtomicSerialFieldDef("", "int"));
    }

    @Test
    void construction_emptyWireType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new AtomicSerialFieldDef("field", ""));
    }

    // ------------------------------------------------------------------
    // 2.1-c  Fail-secure: over-limit byte lengths at construction
    // ------------------------------------------------------------------

    @Test
    void construction_wireNameTooLong_throws() {
        // 256 ASCII bytes → over SIZE(255) limit
        String tooLong = "a".repeat(256);
        assertThrows(IllegalArgumentException.class,
                () -> new AtomicSerialFieldDef(tooLong, "int"));
    }

    @Test
    void construction_wireTypeTooLong_throws() {
        // 1025 ASCII bytes → over SIZE(1024) limit
        String tooLong = "a".repeat(1025);
        assertThrows(IllegalArgumentException.class,
                () -> new AtomicSerialFieldDef("field", tooLong));
    }

    @Test
    void construction_wireNameAtMaxLength_ok() {
        // Exactly 255 ASCII bytes is valid
        String maxName = "a".repeat(255);
        assertDoesNotThrow(() -> new AtomicSerialFieldDef(maxName, "int"));
    }

    @Test
    void construction_wireTypeAtMaxLength_ok() {
        // Exactly 1024 ASCII bytes is valid
        String maxType = "a".repeat(1024);
        assertDoesNotThrow(() -> new AtomicSerialFieldDef("field", maxType));
    }

    // ------------------------------------------------------------------
    // 2.1-d  Fail-secure: over-limit DER content rejected at decode time
    //
    // We craft a raw DER SEQUENCE that embeds a UTF8String whose declared
    // length exceeds the SIZE bound. This tests the pre-allocation check.
    // ------------------------------------------------------------------

    @Test
    void decode_wireNameExceedsMax_throwsDerException() throws Exception {
        // Build a field def with wireName of 256 bytes by crafting raw DER.
        // We bypass the constructor SIZE check by manually building the TLV.
        byte[] fakeDer = buildFieldDefDerWithNameLen(256);
        DerReader reader = new DerReader(fakeDer);
        assertThrows(DerException.class, () -> AtomicSerialFieldDef.decode(reader));
    }

    @Test
    void decode_wireTypeExceedsMax_throwsDerException() throws Exception {
        byte[] fakeDer = buildFieldDefDerWithTypeLenExceeding(1025);
        DerReader reader = new DerReader(fakeDer);
        assertThrows(DerException.class, () -> AtomicSerialFieldDef.decode(reader));
    }

    // ------------------------------------------------------------------
    // 2.1-e  jqwik property: random valid records round-trip
    // ------------------------------------------------------------------

    @Property(tries = 200)
    void property_roundTrip(@ForAll("validFieldDef") AtomicSerialFieldDef original)
            throws DerException {
        byte[] der = original.encode();
        DerReader reader = new DerReader(der);
        AtomicSerialFieldDef decoded = AtomicSerialFieldDef.decode(reader);
        assertEquals(original, decoded);
        assertFalse(reader.hasMore());
    }

    @Provide
    Arbitrary<AtomicSerialFieldDef> validFieldDef() {
        // wireName: 1..255 UTF-8 bytes (use simple ASCII to stay within byte count easily)
        Arbitrary<String> nameArb = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(50);  // 50 ASCII chars = 50 bytes, well within 255
        Arbitrary<String> typeArb = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(100); // 100 ASCII chars = 100 bytes, well within 1024
        return nameArb.flatMap(name ->
                typeArb.map(type -> new AtomicSerialFieldDef(name, type)));
    }

    // ------------------------------------------------------------------
    // Helpers for crafting pathological DER
    // ------------------------------------------------------------------

    /**
     * Builds a raw DER SEQUENCE { UTF8String(len=nameLen), UTF8String(len=1) }
     * without going through the bounds-checked constructor.
     * The content bytes are all 'a' (0x61).
     */
    private static byte[] buildFieldDefDerWithNameLen(int nameLen) {
        return buildFieldDefDer(nameLen, 1);
    }

    private static byte[] buildFieldDefDerWithTypeLenExceeding(int typeLen) {
        return buildFieldDefDer(1, typeLen);
    }

    private static byte[] buildFieldDefDer(int nameLen, int typeLen) {
        // UTF8String tag 0x0C, followed by length, followed by content
        byte[] nameTlv = buildUtf8StringTlv(nameLen);
        byte[] typeTlv = buildUtf8StringTlv(typeLen);
        int seqContentLen = nameTlv.length + typeTlv.length;
        byte[] seqLen = encodeLen(seqContentLen);
        byte[] result = new byte[1 + seqLen.length + seqContentLen];
        int pos = 0;
        result[pos++] = 0x30; // SEQUENCE tag
        System.arraycopy(seqLen, 0, result, pos, seqLen.length);
        pos += seqLen.length;
        System.arraycopy(nameTlv, 0, result, pos, nameTlv.length);
        pos += nameTlv.length;
        System.arraycopy(typeTlv, 0, result, pos, typeTlv.length);
        return result;
    }

    private static byte[] buildUtf8StringTlv(int contentLen) {
        byte[] lenBytes = encodeLen(contentLen);
        byte[] tlv = new byte[1 + lenBytes.length + contentLen];
        int pos = 0;
        tlv[pos++] = 0x0C; // UTF8String tag
        System.arraycopy(lenBytes, 0, tlv, pos, lenBytes.length);
        pos += lenBytes.length;
        for (int i = 0; i < contentLen; i++) {
            tlv[pos++] = (byte) 'a';
        }
        return tlv;
    }

    private static byte[] encodeLen(int len) {
        if (len <= 127) return new byte[]{ (byte) len };
        if (len <= 255) return new byte[]{ (byte) 0x81, (byte) len };
        return new byte[]{ (byte) 0x82, (byte)(len >> 8), (byte)(len & 0xFF) };
    }
}
