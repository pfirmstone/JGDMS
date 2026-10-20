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

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The discarding encoder is the output of the whole-stream admission walks. It must refuse exactly what the real encoder refuses
 * (so a walk that would have failed against a real encoder fails the same way), keep nothing, and fail loudly for the two things
 * it cannot do.
 */
class DiscardingDerEncoderTest {

    private static final Tag CONSTRUCTED = Tag.SEQUENCE;
    private static final Tag PRIMITIVE = Tag.INTEGER;

    @Test
    void followsTheRealEncodersProtocolOnRandomCallSequences() {
        Random r = new Random(0xD15CA);
        for (int run = 0; run < 3000; run++) {
            DerEncoder real = DerEncoder.create();
            DerEncoder none = DerEncoder.discarding();
            for (int step = 0; step < 25; step++) {
                int op = r.nextInt(9);
                byte[] buf = new byte[r.nextInt(8)];
                int off = r.nextInt(10) - 1;
                int len = r.nextInt(10) - 1;
                boolean realThrew = apply(real, op, buf, off, len);
                boolean noneThrew = apply(none, op, buf, off, len);
                String at = "run " + run + " step " + step + " op " + op + " off " + off + " len " + len + " buf " + buf.length;
                assertEquals(realThrew, noneThrew, "threw: " + at);
                assertEquals(real.isClean(), none.isClean(), "isClean: " + at);
            }
        }
    }

    /** One operation; returns whether it threw a RuntimeException. */
    private static boolean apply(DerEncoder e, int op, byte[] buf, int off, int len) {
        try {
            switch (op) {
                case 0 -> e.begin(CONSTRUCTED);
                case 1 -> e.begin(PRIMITIVE);                 // refused: needs a constructed tag
                case 2 -> e.beginOctetString();
                case 3, 4 -> e.end();
                case 5 -> e.leaf(PRIMITIVE, buf, off, len);
                case 6 -> e.raw(buf, off, len);
                case 7 -> e.reset();
                default -> e.leaf(Tag.OCTET_STRING, buf);
            }
            return false;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    @Test
    void keepsNothingAndCountsWhatItWasGiven() {
        DerEncoder e = DerEncoder.discarding();
        byte[] big = new byte[1 << 20];
        e.begin(CONSTRUCTED).leaf(PRIMITIVE, big).raw(big, 10, 1000).end();
        assertTrue(e.isClean());
        assertEquals((1 << 20) + 1000, ((DiscardingDerEncoder) e).discardedBytes());
        e.reset();
        assertEquals(0, ((DiscardingDerEncoder) e).discardedBytes());
    }

    @Test
    void whatItCannotDoFailsLoudly() {
        DerEncoder e = DerEncoder.discarding();
        assertThrows(UnsupportedOperationException.class, e::toByteArray);
        assertThrows(UnsupportedOperationException.class, e::size);
        assertThrows(UnsupportedOperationException.class, () -> e.beginSortedSet((a, b) -> 0));
        assertThrows(UnsupportedOperationException.class, e::beginSortedMap);
    }

    @Test
    void aFailureLatchesUntilReset() {
        DerEncoder e = DerEncoder.discarding();
        assertThrows(IllegalStateException.class, e::end);          // nothing open
        assertFalse(e.isClean());
        assertThrows(IllegalStateException.class, () -> e.begin(CONSTRUCTED));
        e.reset();
        assertTrue(e.isClean());
        e.begin(CONSTRUCTED).end();
        assertTrue(e.isClean());
    }

    @Test
    void anOpenConstructIsNotClean() {
        DerEncoder e = DerEncoder.discarding();
        e.begin(CONSTRUCTED);
        assertFalse(e.isClean());
        e.end();
        assertTrue(e.isClean());
    }
}
