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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SOW-DER-Codec-Performance T2: {@link DerWriter} must emit exactly the bytes
 * the frozen pre-T2 builder ({@link DerWriterReference}) emits, for every entry
 * point, tag form and length form, including randomly nested trees.
 */
class DerWriterEquivalenceTest {

    private static final int[] LENGTHS = {
        0, 1, 2, 126, 127, 128, 129, 254, 255, 256, 257, 65535, 65536, 65537, 70000
    };

    private static final Tag[] TAGS = {
        Tag.BOOLEAN, Tag.INTEGER, Tag.OCTET_STRING, Tag.UTF8STRING, Tag.SEQUENCE,
        new Tag(Tag.CLASS_UNIVERSAL, true, 17),
        new Tag(Tag.CLASS_CONTEXT, false, 0), new Tag(Tag.CLASS_CONTEXT, true, 1),
        new Tag(Tag.CLASS_CONTEXT, true, 30), new Tag(Tag.CLASS_CONTEXT, false, 31),
        new Tag(Tag.CLASS_APPLICATION, true, 127), new Tag(Tag.CLASS_APPLICATION, false, 128),
        new Tag(Tag.CLASS_PRIVATE, true, 16383), new Tag(Tag.CLASS_PRIVATE, false, 16384),
        new Tag(Tag.CLASS_CONTEXT, true, 2097151), new Tag(Tag.CLASS_CONTEXT, false, 2097152),
        new Tag(Tag.CLASS_CONTEXT, true, Integer.MAX_VALUE),
    };

    private static byte[] randomBytes(Random r, int n) {
        byte[] b = new byte[n];
        r.nextBytes(b);
        return b;
    }

    @Test
    void writeTlvAllTagsAllLengthBoundaries() {
        Random r = new Random(1);
        for (Tag t : TAGS) {
            for (int n : LENGTHS) {
                byte[] content = randomBytes(r, n);
                assertArrayEquals(DerWriterReference.writeTlv(t, content),
                        DerWriter.writeTlv(t, content), "tag=" + t + " len=" + n);
            }
        }
    }

    @Test
    void primitivesMatch() {
        Random r = new Random(2);
        assertArrayEquals(DerWriterReference.writeBoolean(true), DerWriter.writeBoolean(true));
        assertArrayEquals(DerWriterReference.writeBoolean(false), DerWriter.writeBoolean(false));
        long[] longs = {0, 1, -1, 127, 128, -128, -129, 255, 256, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long v : longs) {
            assertArrayEquals(DerWriterReference.writeInteger(v), DerWriter.writeInteger(v));
        }
        for (int i = 0; i < 200; i++) {
            BigInteger v = new BigInteger(1 + r.nextInt(600), r);
            if (r.nextBoolean()) v = v.negate();
            assertArrayEquals(DerWriterReference.writeInteger(v), DerWriter.writeInteger(v));
        }
        for (int n : LENGTHS) {
            byte[] b = randomBytes(r, n);
            assertArrayEquals(DerWriterReference.writeOctetString(b), DerWriter.writeOctetString(b));
            assertArrayEquals(DerWriterReference.writeSequence(b), DerWriter.writeSequence(b));
        }
        String[] strings = {"", "a", "héllo", "€€", "😀 emoji", "x".repeat(300)};
        for (String s : strings) {
            assertArrayEquals(DerWriterReference.writeUtf8String(s), DerWriter.writeUtf8String(s));
        }
    }

    @Test
    void sequenceAndSetOfListsMatch() {
        Random r = new Random(3);
        for (int n : LENGTHS) {
            for (int parts : new int[] {0, 1, 2, 5}) {
                List<byte[]> kids = new ArrayList<>();
                for (int i = 0; i < parts; i++) kids.add(randomBytes(r, n / (parts == 0 ? 1 : parts)));
                assertArrayEquals(DerWriterReference.writeSequence(kids), DerWriter.writeSequence(kids));
                assertArrayEquals(DerWriterReference.writeSet(kids), DerWriter.writeSet(kids));
            }
        }
    }

    @Test
    void nullArgumentsStillRejected() {
        assertThrows(NullPointerException.class, () -> DerWriter.writeTlv(null, new byte[0]));
        assertThrows(NullPointerException.class, () -> DerWriter.writeTlv(Tag.SEQUENCE, null));
        assertThrows(NullPointerException.class, () -> DerWriter.writeSequence((List<byte[]>) null));
        assertThrows(NullPointerException.class, () -> DerWriter.writeSet((List<byte[]>) null));
        List<byte[]> withNull = new ArrayList<>();
        withNull.add(new byte[1]);
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> DerWriter.writeSequence(withNull));
        assertThrows(NullPointerException.class, () -> DerWriter.writeSet(withNull));
    }

    /** Builds the same random nested tree with both implementations and compares. */
    @Test
    void randomNestedTreesMatch() {
        for (long seed = 0; seed < 300; seed++) {
            Random a = new Random(seed), b = new Random(seed);
            assertArrayEquals(tree(a, 0, true), tree(b, 0, false), "seed=" + seed);
        }
    }

    private static byte[] tree(Random r, int depth, boolean reference) {
        int kind = depth >= 5 ? r.nextInt(4) : r.nextInt(8);
        switch (kind) {
            case 0: return reference ? DerWriterReference.writeBoolean(r.nextBoolean())
                                     : DerWriter.writeBoolean(r.nextBoolean());
            case 1: { long v = r.nextLong() >> r.nextInt(64);
                      return reference ? DerWriterReference.writeInteger(v) : DerWriter.writeInteger(v); }
            case 2: { byte[] x = randomBytes(r, pick(r));
                      return reference ? DerWriterReference.writeOctetString(x) : DerWriter.writeOctetString(x); }
            case 3: { String s = "s".repeat(r.nextInt(200)) + "é";
                      return reference ? DerWriterReference.writeUtf8String(s) : DerWriter.writeUtf8String(s); }
            case 4: case 5: case 6: {
                int n = r.nextInt(6);
                List<byte[]> kids = new ArrayList<>();
                for (int i = 0; i < n; i++) kids.add(tree(r, depth + 1, reference));
                boolean set = kind == 6;
                if (reference) {
                    return set ? DerWriterReference.writeSet(kids) : DerWriterReference.writeSequence(kids);
                }
                return set ? DerWriter.writeSet(kids) : DerWriter.writeSequence(kids);
            }
            default: {
                Tag t = TAGS[r.nextInt(TAGS.length)];
                byte[] inner = tree(r, depth + 1, reference);
                return reference ? DerWriterReference.writeTlv(t, inner) : DerWriter.writeTlv(t, inner);
            }
        }
    }

    private static int pick(Random r) {
        return r.nextInt(8) == 0 ? LENGTHS[r.nextInt(LENGTHS.length)] : r.nextInt(300);
    }
}
