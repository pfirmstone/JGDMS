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

import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SOW-DER-Streaming-Encoder task A0: the scaffold A1 fills in. It describes random nested TLV trees
 * (primitives, SEQUENCEs, preserve-order and octet-SORTED SET OFs, tag forms, and constructs whose
 * content length straddles the length-octet boundaries 127/128, 255/256 and 65535/65536) as data, and
 * encodes them with the frozen {@link DerWriterReference}. A1's {@code DerEncoder} is to be encoded
 * from the SAME tree description and must produce identical bytes; {@link #derEncoderMatchesReference}
 * is the (disabled) hook for that.
 *
 * <p>Sorted sets are the point: a SET OF's children must be encoded to separate byte strings, octet
 * sorted (X.690 sec.11.6, via {@link CollectionWireTypes#octetSort}), then framed, so an encoder that
 * writes children in place must still reproduce that order, including a sorted set nested inside a
 * sorted set's element.
 */
class DerEncoderEquivalenceScaffoldTest {

    /** A tree node: a leaf TLV, or a constructed node with children. */
    sealed interface Node permits Leaf, Seq, SortedSet {}
    record Leaf(Tag tag, byte[] content) implements Node {}
    record Seq(Tag tag, List<Node> children) implements Node {}
    record SortedSet(List<Node> children) implements Node {}

    static final int[] BOUNDARY_LENGTHS = {
        0, 1, 125, 126, 127, 128, 129, 130, 253, 254, 255, 256, 257, 258,
        65531, 65532, 65533, 65534, 65535, 65536, 65537, 65538,
    };

    static Node randomTree(Random r, int depth) {
        int kind = depth >= 4 ? 0 : r.nextInt(5);
        switch (kind) {
            case 0: {
                int n = r.nextInt(6) == 0 ? BOUNDARY_LENGTHS[r.nextInt(BOUNDARY_LENGTHS.length)] : r.nextInt(300);
                byte[] c = new byte[n];
                r.nextBytes(c);
                return new Leaf(r.nextBoolean() ? Tag.OCTET_STRING : new Tag(Tag.CLASS_CONTEXT, false, r.nextInt(40)), c);
            }
            case 1: case 2: {
                List<Node> kids = new ArrayList<>();
                for (int i = r.nextInt(5); i > 0; i--) kids.add(randomTree(r, depth + 1));
                return new Seq(r.nextBoolean() ? Tag.SEQUENCE : new Tag(Tag.CLASS_CONTEXT, true, r.nextInt(40)), kids);
            }
            default: {
                List<Node> kids = new ArrayList<>();
                for (int i = r.nextInt(6); i > 0; i--) kids.add(randomTree(r, depth + 1));
                return new SortedSet(kids);
            }
        }
    }

    /** The oracle: the frozen pre-T2 builder, with sorted sets sorted exactly as ObjectCodec sorts them. */
    static byte[] encodeWithReference(Node n) {
        if (n instanceof Leaf l) {
            return DerWriterReference.writeTlv(l.tag(), l.content());
        }
        if (n instanceof Seq s) {
            List<byte[]> kids = new ArrayList<>();
            for (Node c : s.children()) kids.add(encodeWithReference(c));
            return DerWriterReference.writeTlv(s.tag(), concat(kids));
        }
        List<byte[]> kids = new ArrayList<>();
        for (Node c : ((SortedSet) n).children()) kids.add(encodeWithReference(c));
        CollectionWireTypes.octetSort(kids);
        return DerWriterReference.writeSet(kids);
    }

    private static byte[] concat(List<byte[]> parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, pos, p.length); pos += p.length; }
        return out;
    }

    @Test
    void referenceEncodingOfTheScaffoldTreesIsDeterministicAndSortsSets() {
        for (long seed = 0; seed < 200; seed++) {
            Node t1 = randomTree(new Random(seed), 0), t2 = randomTree(new Random(seed), 0);
            assertArrayEquals(encodeWithReference(t1), encodeWithReference(t2), "seed " + seed);
        }
        // a sorted set really is sorted: children come out in ascending octet order
        Node set = new SortedSet(List.of(new Leaf(Tag.OCTET_STRING, new byte[] {9}),
                new Leaf(Tag.OCTET_STRING, new byte[] {1}), new Leaf(Tag.OCTET_STRING, new byte[] {5})));
        assertArrayEquals(new byte[] {0x31, 9, 4, 1, 1, 4, 1, 5, 4, 1, 9}, encodeWithReference(set));
    }

    @Test
    void generatorReachesTheLengthBoundariesAndNestedSortedSets() {
        boolean[] seenLen = new boolean[BOUNDARY_LENGTHS.length];
        boolean nestedSet = false;
        for (long seed = 0; seed < 3000; seed++) {
            Node t = randomTree(new Random(seed), 0);
            nestedSet |= hasNestedSet(t, false);
            mark(t, seenLen);
        }
        for (int i = 0; i < seenLen.length; i++) assertTrue(seenLen[i], "never generated a leaf of length " + BOUNDARY_LENGTHS[i]);
        assertTrue(nestedSet, "never generated a sorted set inside a sorted set's element");
    }

    private static void mark(Node n, boolean[] seen) {
        if (n instanceof Leaf l) {
            for (int i = 0; i < BOUNDARY_LENGTHS.length; i++) if (l.content().length == BOUNDARY_LENGTHS[i]) seen[i] = true;
        } else if (n instanceof Seq s) {
            s.children().forEach(c -> mark(c, seen));
        } else {
            ((SortedSet) n).children().forEach(c -> mark(c, seen));
        }
    }

    private static boolean hasNestedSet(Node n, boolean inSet) {
        if (n instanceof SortedSet s) {
            if (inSet) return true;
            return s.children().stream().anyMatch(c -> hasNestedSet(c, true));
        }
        if (n instanceof Seq s) return s.children().stream().anyMatch(c -> hasNestedSet(c, inSet));
        return false;
    }

    /** A1 replaces the body: encode the same trees with DerEncoder (both variants) and compare. */
    @Disabled("A1: enable once DerEncoder exists; encode randomTree(seed) with it and compare to the reference")
    @Test
    void derEncoderMatchesReference() {
        for (long seed = 0; seed < 2000; seed++) {
            Node t = randomTree(new Random(seed), 0);
            byte[] expected = encodeWithReference(t);
            assertNotNull(expected);   // A1: assertArrayEquals(expected, <DerEncoder over t>, "seed " + seed);
        }
    }
}
