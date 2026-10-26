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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A2 hardening of {@link DerEncoder}, from the board review of the ObjectCodec migration: the failure
 * latch, primitive-tag opening, exact-TLV elements in sorted sets, and the key-only map order.
 */
class DerEncoderHardeningTest {

    @Test
    void throwingComparatorPoisonsTheEncoder() {
        DerEncoder enc = DerEncoder.create();
        enc.beginSortedSet((a, b) -> { throw new IllegalStateException("boom"); });
        enc.leaf(Tag.UTF8STRING, new byte[]{1});
        enc.leaf(Tag.UTF8STRING, new byte[]{2});
        assertThrows(IllegalStateException.class, enc::end);
        // the set is gone but the depth looks balanced: without the latch this would "succeed" and omit it
        assertThrows(IllegalStateException.class, enc::toByteArray);
        assertThrows(IllegalStateException.class, () -> enc.leaf(Tag.UTF8STRING, new byte[0]));
        enc.reset();
        enc.leaf(Tag.UTF8STRING, new byte[]{7});
        assertArrayEquals(new byte[]{0x0C, 1, 7}, enc.toByteArray());
    }

    @Test
    void misuseAlsoPoisons() {
        DerEncoder enc = DerEncoder.create();
        assertThrows(IllegalStateException.class, enc::end);
        assertThrows(IllegalStateException.class, enc::toByteArray);
    }

    @Test
    void beginRefusesPrimitiveTagAndOctetStringWrapsAFramedPayload() {
        DerEncoder enc = DerEncoder.create();
        assertThrows(IllegalArgumentException.class, () -> enc.begin(Tag.INTEGER));
        assertThrows(IllegalArgumentException.class, () -> enc.begin(Tag.OCTET_STRING));

        byte[] inner = DerWriter.writeSequence(List.of(DerWriter.writeUtf8String("a"), DerWriter.writeInteger(5)));
        DerEncoder e2 = DerEncoder.create();
        e2.begin(Tag.SEQUENCE).beginOctetString();
        e2.begin(Tag.SEQUENCE).utf8("a").leaf(Tag.INTEGER, new byte[]{5}).end();
        e2.end().end();
        assertArrayEquals(DerWriter.writeSequence(List.of(DerWriter.writeOctetString(inner))), e2.toByteArray());
    }

    @Test
    void sortedSetRawMustBeExactlyOneTlv() {
        byte[] one = DerWriter.writeUtf8String("x");
        byte[] two = concat(one, one);
        for (byte[] bad : new byte[][]{ new byte[0], two, Arrays.copyOf(one, one.length - 1),
                new byte[]{0x0C, (byte) 0x80}, new byte[]{0x1F} }) {
            DerEncoder enc = DerEncoder.create();
            enc.beginSortedSet(CollectionWireTypes.OCTET_SORT);
            assertThrows(IllegalArgumentException.class, () -> enc.raw(bad), Arrays.toString(bad));
        }
        DerEncoder ok = DerEncoder.create();
        ok.beginSortedSet(CollectionWireTypes.OCTET_SORT).raw(DerWriter.writeUtf8String("b")).raw(one).end();
        assertArrayEquals(DerWriter.writeSet(List.of(DerWriter.writeUtf8String("b"), one)), ok.toByteArray());
    }

    /** The old algorithm: parallel key list, stable sort by CollectionWireTypes.compareOctets over the KEY TLV. */
    private static byte[] referenceMap(List<byte[][]> kv) {
        List<byte[]> keys = new ArrayList<>(), entries = new ArrayList<>();
        for (byte[][] e : kv) {
            keys.add(e[0]);
            entries.add(DerWriter.writeSequence(List.of(e[0], e[1])));
        }
        Integer[] order = new Integer[entries.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (p, q) -> CollectionWireTypes.compareOctets(keys.get(p), keys.get(q)));
        List<byte[]> sorted = new ArrayList<>();
        for (int i : order) sorted.add(entries.get(i));
        return DerWriter.writeSet(sorted);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }

    private static byte[] randomKey(Random r) {
        int[] lens = {0, 1, 2, 5, 126, 127, 128, 129, 255, 256, 300};
        int len = lens[r.nextInt(lens.length)];
        byte[] content = new byte[len];
        // low entropy so keys share long prefixes and ties on the first octets are common
        for (int i = 0; i < len; i++) content[i] = (byte) r.nextInt(3);
        Tag tag = switch (r.nextInt(4)) {
            case 0 -> Tag.UTF8STRING;
            case 1 -> Tag.INTEGER;
            case 2 -> new Tag(Tag.CLASS_CONTEXT, false, 3 + r.nextInt(2));
            default -> new Tag(Tag.CLASS_CONTEXT, false, 31 + r.nextInt(200));   // high-tag-number form
        };
        return DerWriter.writeTlv(tag, content);
    }

    @Test
    void sortedMapMatchesKeyOnlyReferenceOrder() {
        Random r = new Random(0xA2);
        for (int round = 0; round < 300; round++) {
            int n = r.nextInt(12);
            List<byte[][]> kv = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                byte[] value = DerWriter.writeOctetString(new byte[r.nextInt(400)]);   // flips whole-entry order
                kv.add(new byte[][]{ randomKey(r), value });
            }
            DerEncoder enc = DerEncoder.create();
            enc.beginSortedMap();
            for (byte[][] e : kv) enc.begin(Tag.SEQUENCE).raw(e[0]).raw(e[1]).end();
            enc.end();
            assertArrayEquals(referenceMap(kv), enc.toByteArray(), "round " + round);
        }
    }

    @Test
    void sortedMapIsKeyOnlyNotWholeEntry() {
        // keys "a" < "b", but the entry for "a" has the longer value, so its LENGTH octet sorts after b's
        byte[] a = DerWriter.writeUtf8String("a"), b = DerWriter.writeUtf8String("b");
        byte[] bigValue = DerWriter.writeOctetString(new byte[100]), smallValue = DerWriter.writeOctetString(new byte[1]);
        DerEncoder enc = DerEncoder.create();
        enc.beginSortedMap();
        enc.begin(Tag.SEQUENCE).raw(b).raw(smallValue).end();
        enc.begin(Tag.SEQUENCE).raw(a).raw(bigValue).end();
        enc.end();
        byte[] expect = DerWriter.writeSet(List.of(
                DerWriter.writeSequence(List.of(a, bigValue)), DerWriter.writeSequence(List.of(b, smallValue))));
        assertArrayEquals(expect, enc.toByteArray());
    }

    @Test
    void sortedMapInsideSortedSetAndEmpty() {
        DerEncoder enc = DerEncoder.create();
        enc.beginSortedSet(CollectionWireTypes.OCTET_SORT);
        enc.beginSortedMap().end();
        enc.beginSortedMap();
        enc.begin(Tag.SEQUENCE).utf8("k").utf8("v").end();
        enc.end();
        enc.end();
        byte[] empty = DerWriter.writeSet(List.of());
        byte[] one = DerWriter.writeSet(List.of(DerWriter.writeSequence(
                List.of(DerWriter.writeUtf8String("k"), DerWriter.writeUtf8String("v")))));
        assertArrayEquals(DerWriter.writeSet(List.of(empty, one)), enc.toByteArray());
    }

    @Test
    void sortedMapDuplicateKeysKeepInsertionOrder() {
        byte[] k = DerWriter.writeUtf8String("k");
        byte[] v1 = DerWriter.writeUtf8String("1"), v2 = DerWriter.writeUtf8String("2");
        DerEncoder enc = DerEncoder.create();
        enc.beginSortedMap();
        enc.begin(Tag.SEQUENCE).raw(k).raw(v2).end();
        enc.begin(Tag.SEQUENCE).raw(k).raw(v1).end();
        enc.end();
        assertArrayEquals(DerWriter.writeSet(List.of(
                DerWriter.writeSequence(List.of(k, v2)), DerWriter.writeSequence(List.of(k, v1)))), enc.toByteArray());
    }

    @Test
    void integerBoolNullMatchDerWriter() {
        long[] edge = {0, 1, -1, 127, 128, -128, -129, 255, 256, 32767, 32768, -32768, -32769, 65535, 65536,
                Integer.MAX_VALUE, Integer.MIN_VALUE, (long) Integer.MAX_VALUE + 1, (long) Integer.MIN_VALUE - 1,
                Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE - 1, Long.MIN_VALUE + 1, 0x7FFFFFFFFFFFFFL, 0x80000000000000L};
        List<Long> all = new ArrayList<>();
        for (long v : edge) all.add(v);
        Random r = new Random(7);
        for (int i = 0; i < 20000; i++) all.add(r.nextLong() >> r.nextInt(64));
        for (long v : all) {
            assertArrayEquals(DerWriter.writeInteger(v), DerEncoder.create().integer(v).toByteArray(), "value " + v);
        }
        assertArrayEquals(DerWriter.writeBoolean(true), DerEncoder.create().bool(true).toByteArray());
        assertArrayEquals(DerWriter.writeBoolean(false), DerEncoder.create().bool(false).toByteArray());
        assertArrayEquals(new byte[]{5, 0}, DerEncoder.create().nul().toByteArray());
    }
}
