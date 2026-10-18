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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The window / skip additions to {@link DerReader} and the bounded {@link Tag#decode(byte[], int, int)} (A3). */
class DerReaderWindowTest {

    @Test
    void windowReaderSeesOnlyItsRangeAndReportsAbsolutePositions() throws Exception {
        byte[] a = DerWriter.writeUtf8String("a"), b = DerWriter.writeUtf8String("bb"), c = DerWriter.writeInteger(7);
        byte[] all = concat(a, b, c);
        DerReader r = new DerReader(all, a.length, a.length + b.length);
        assertEquals(a.length, r.position());
        assertEquals(a.length + b.length, r.endBound());
        assertEquals("bb", r.readUtf8String());
        assertFalse(r.hasMore(), "the window must end where it was told to, not at the buffer end");
        assertThrows(DerException.class, r::readInteger);
    }

    @Test
    void windowConstructorRejectsBadRanges() {
        byte[] b = new byte[4];
        assertThrows(IllegalArgumentException.class, () -> new DerReader(b, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new DerReader(b, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> new DerReader(b, 0, 5));
        assertThrows(NullPointerException.class, () -> new DerReader(null, 0, 0));
        assertDoesNotThrow(() -> new DerReader(b, 4, 4));
    }

    @Test
    void skipTlvReturnsAbsoluteEndAndEnforcesTheSameHeaderRules() throws Exception {
        byte[] a = DerWriter.writeOctetString(new byte[200]);          // long-form length
        byte[] b = DerWriter.writeSequence(List.of(DerWriter.writeInteger(1)));
        byte[] all = concat(a, b);
        DerReader r = new DerReader(all);
        assertEquals(a.length, r.skipTlv());
        assertEquals(a.length, r.position());
        assertEquals(all.length, r.skipTlv());
        assertFalse(r.hasMore());
        // non-minimal length (long form for 5) and an overrunning length are rejected exactly as readTlvHeader does
        assertThrows(DerException.class, () -> new DerReader(new byte[] {0x04, (byte) 0x81, 0x05, 1, 2, 3, 4, 5}).skipTlv());
        assertThrows(DerException.class, () -> new DerReader(new byte[] {0x04, 0x05, 1, 2}).skipTlv());
    }

    @Test
    void readOctetStringHeaderLeavesTheCursorAtTheContent() throws Exception {
        byte[] os = DerWriter.writeOctetString(new byte[] {9, 8, 7});
        DerReader r = new DerReader(concat(new byte[] {0x30}, os), 1, 1 + os.length);
        assertEquals(3, r.readOctetStringHeader());
        assertEquals(1 + 2, r.position());
        r.skip(3);
        assertFalse(r.hasMore());
        assertThrows(DerException.class, () -> new DerReader(DerWriter.writeUtf8String("x")).readOctetStringHeader());
        assertThrows(DerException.class, () -> new DerReader(new byte[] {1, 2}).skip(3));
        assertThrows(DerException.class, () -> new DerReader(new byte[] {1, 2}).skip(-1));
    }

    @Test
    void tagDecodingNeverConsumesBytesBeyondTheWindow() throws Exception {
        // 0xBF 0x9F is a high-tag-number prefix that continues into the NEXT octet. In a window ending after it,
        // the continuation byte belongs to a following element and must not be read.
        byte[] buf = {(byte) 0xBF, (byte) 0x9F, 0x05, 0x00};
        assertThrows(DerException.class, () -> Tag.decode(buf, 0, 2));
        assertEquals(3, Tag.decode(buf, 0, 4).bytesRead());   // with the third octet in view it is a 3-octet tag
        assertThrows(DerException.class, () -> new DerReader(buf, 0, 2).peekTag());
        assertThrows(DerException.class, () -> Tag.decode(buf, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Tag.decode(buf, 0, 5));
        // the two-argument form is unchanged
        assertEquals(Tag.decode(buf, 0).bytesRead(), Tag.decode(buf, 0, buf.length).bytesRead());
    }

    private static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }
}
