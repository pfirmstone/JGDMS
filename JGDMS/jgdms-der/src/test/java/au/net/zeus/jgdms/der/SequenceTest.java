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
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Task 1.4 -- SEQUENCE encoding and decoding.
 *
 * Covers:
 * <ul>
 *   <li>Nested SEQUENCE-of-SEQUENCE round-trips.</li>
 *   <li>Child order preserved exactly (S3.8).</li>
 *   <li>Reader stops at the SEQUENCE boundary even with trailing bytes after it.</li>
 *   <li>jqwik property: list of random primitives -> SEQUENCE -> decode -> same list, same order.</li>
 *   <li>Empty SEQUENCE round-trips.</li>
 * </ul>
 */
class SequenceTest {

    /* ------------------------------------------------------------------ */
    /* Empty SEQUENCE                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    void emptySequenceRoundTrips() throws DerException {
        byte[] encoded = DerWriter.writeSequence(new byte[0]);
        assertArrayEquals(Hex.fromHex("3000"), encoded,
                "Empty SEQUENCE should encode as 30 00; got: " + Hex.toHex(encoded));
        DerReader reader = new DerReader(encoded);
        DerReader seq = reader.readSequence();
        assertFalse(seq.hasMore(), "Empty SEQUENCE reader should have no children");
    }

    /* ------------------------------------------------------------------ */
    /* Simple SEQUENCE with primitives                                      */
    /* ------------------------------------------------------------------ */

    @Test
    void sequenceWithBooleanAndInteger() throws DerException {
        byte[] boolEnc = DerWriter.writeBoolean(true);
        byte[] intEnc  = DerWriter.writeInteger(BigInteger.valueOf(42));
        byte[] seq     = DerWriter.writeSequence(List.of(boolEnc, intEnc));

        DerReader outer = new DerReader(seq);
        DerReader inner = outer.readSequence();
        assertTrue(inner.hasMore());
        assertTrue(inner.readBoolean());
        assertTrue(inner.hasMore());
        assertEquals(BigInteger.valueOf(42), inner.readInteger());
        assertFalse(inner.hasMore(), "SEQUENCE should have exactly 2 children");
    }

    /* ------------------------------------------------------------------ */
    /* Child order is preserved                                             */
    /* ------------------------------------------------------------------ */

    @Test
    void childOrderPreserved() throws DerException {
        // Encode three UTF8Strings in a specific order
        List<String> original = List.of("alpha", "beta", "gamma");
        List<byte[]> children = new ArrayList<>();
        for (String s : original) children.add(DerWriter.writeUtf8String(s));
        byte[] encoded = DerWriter.writeSequence(children);

        DerReader outer = new DerReader(encoded);
        DerReader inner = outer.readSequence();
        List<String> decoded = new ArrayList<>();
        while (inner.hasMore()) {
            decoded.add(inner.readUtf8String());
        }
        assertEquals(original, decoded,
                "SEQUENCE children must come back in the same order");
    }

    /* ------------------------------------------------------------------ */
    /* SEQUENCE boundary stop -- trailing bytes in outer buffer             */
    /* ------------------------------------------------------------------ */

    @Test
    void sequenceReaderStopsAtBoundary() throws DerException {
        // Build a SEQUENCE containing one INTEGER
        byte[] seqBytes = DerWriter.writeSequence(
                List.of(DerWriter.writeInteger(BigInteger.TEN)));

        // Append a spurious trailing TLV (another INTEGER) after the SEQUENCE
        byte[] trailingTlv = DerWriter.writeInteger(BigInteger.valueOf(99));
        byte[] buf = new byte[seqBytes.length + trailingTlv.length];
        System.arraycopy(seqBytes, 0, buf, 0, seqBytes.length);
        System.arraycopy(trailingTlv, 0, buf, seqBytes.length, trailingTlv.length);

        // The outer reader sits at the start; readSequence advances it past the SEQUENCE
        DerReader outer = new DerReader(buf);
        DerReader inner = outer.readSequence();

        // Inner reader sees only the one INTEGER inside the SEQUENCE
        assertTrue(inner.hasMore());
        assertEquals(BigInteger.TEN, inner.readInteger());
        assertFalse(inner.hasMore(),
                "SEQUENCE sub-reader must stop at boundary (not see trailing bytes)");

        // Outer reader still has the trailing TLV visible
        assertTrue(outer.hasMore(),
                "Outer reader must see trailing bytes after SEQUENCE");
        assertEquals(BigInteger.valueOf(99), outer.readInteger(),
                "Outer reader must read the trailing INTEGER correctly");
    }

    /* ------------------------------------------------------------------ */
    /* Nested SEQUENCE-of-SEQUENCE                                         */
    /* ------------------------------------------------------------------ */

    @Test
    void nestedSequenceRoundTrips() throws DerException {
        // Build:  SEQUENCE { SEQUENCE { INTEGER(1), INTEGER(2) }, SEQUENCE { UTF8String("abc") } }
        byte[] inner1 = DerWriter.writeSequence(List.of(
                DerWriter.writeInteger(BigInteger.ONE),
                DerWriter.writeInteger(BigInteger.TWO)));
        byte[] inner2 = DerWriter.writeSequence(List.of(
                DerWriter.writeUtf8String("abc")));
        byte[] outer  = DerWriter.writeSequence(List.of(inner1, inner2));

        DerReader outerReader = new DerReader(outer);
        DerReader topSeq = outerReader.readSequence();

        // First child SEQUENCE
        DerReader seq1 = topSeq.readSequence();
        assertEquals(BigInteger.ONE, seq1.readInteger());
        assertEquals(BigInteger.TWO, seq1.readInteger());
        assertFalse(seq1.hasMore());

        // Second child SEQUENCE
        DerReader seq2 = topSeq.readSequence();
        assertEquals("abc", seq2.readUtf8String());
        assertFalse(seq2.hasMore());

        assertFalse(topSeq.hasMore(), "Top SEQUENCE should have exactly 2 children");
    }

    @Test
    void deeplyNestedSequence() throws DerException {
        // Three levels: outer > middle > inner
        byte[] leaf  = DerWriter.writeBoolean(true);
        byte[] inner = DerWriter.writeSequence(List.of(leaf));
        byte[] mid   = DerWriter.writeSequence(List.of(inner));
        byte[] outer = DerWriter.writeSequence(List.of(mid));

        DerReader r0 = new DerReader(outer);
        DerReader r1 = r0.readSequence();
        DerReader r2 = r1.readSequence();
        DerReader r3 = r2.readSequence();
        assertTrue(r3.readBoolean());
        assertFalse(r3.hasMore());
        assertFalse(r2.hasMore());
        assertFalse(r1.hasMore());
        assertFalse(r0.hasMore());
    }

    /* ------------------------------------------------------------------ */
    /* Multiple SEQUENCE boundaries with trailing bytes (detailed)          */
    /* ------------------------------------------------------------------ */

    @Test
    void multipleSequencesInBuffer_boundaryRespected() throws DerException {
        // Two back-to-back SEQUENCEs in one buffer
        byte[] seq1 = DerWriter.writeSequence(List.of(DerWriter.writeBoolean(false)));
        byte[] seq2 = DerWriter.writeSequence(List.of(DerWriter.writeBoolean(true)));
        byte[] buf  = new byte[seq1.length + seq2.length];
        System.arraycopy(seq1, 0, buf, 0, seq1.length);
        System.arraycopy(seq2, 0, buf, seq1.length, seq2.length);

        DerReader outer = new DerReader(buf);

        DerReader inner1 = outer.readSequence();
        assertFalse(inner1.readBoolean()); // false
        assertFalse(inner1.hasMore());

        DerReader inner2 = outer.readSequence();
        assertTrue(inner2.readBoolean()); // true
        assertFalse(inner2.hasMore());

        assertFalse(outer.hasMore());
    }

    /* ------------------------------------------------------------------ */
    /* jqwik property: list of integers -> SEQUENCE -> decode -> same list    */
    /* ------------------------------------------------------------------ */

    @Property(tries = 200)
    void sequenceOfIntegersRoundTrips(
            @ForAll @Size(min = 0, max = 10) List<BigInteger> values)
            throws DerException {
        List<byte[]> encoded = new ArrayList<>();
        for (BigInteger v : values) {
            encoded.add(DerWriter.writeInteger(v));
        }
        byte[] seqBytes = DerWriter.writeSequence(encoded);

        DerReader outer = new DerReader(seqBytes);
        DerReader inner = outer.readSequence();

        List<BigInteger> decoded = new ArrayList<>();
        while (inner.hasMore()) {
            decoded.add(inner.readInteger());
        }
        assertEquals(values, decoded,
                "SEQUENCE-of-INTEGER round-trip failed for list=" + values);
    }

    @Property(tries = 200)
    void sequenceOfStringsRoundTrips(
            @ForAll @Size(min = 0, max = 8) List<String> values)
            throws DerException {
        List<byte[]> encoded = new ArrayList<>();
        for (String v : values) {
            encoded.add(DerWriter.writeUtf8String(v));
        }
        byte[] seqBytes = DerWriter.writeSequence(encoded);

        DerReader outer = new DerReader(seqBytes);
        DerReader inner = outer.readSequence();

        List<String> decoded = new ArrayList<>();
        while (inner.hasMore()) {
            decoded.add(inner.readUtf8String());
        }
        assertEquals(values, decoded,
                "SEQUENCE-of-UTF8String round-trip failed for list=" + values);
    }
}
