/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.der.stream;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The hash keys of the dedup tables: equal content is one key, anything else is another. */
class ByteArrayKeyTest {

    private static byte[] bytes(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    @Test
    void equalContentInDifferentArraysIsOneKey() {
        byte[] a = bytes(200, 1);
        byte[] b = a.clone();
        assertEquals(ByteArrayKey.ofBytes(a), ByteArrayKey.ofBytes(b));
        assertEquals(ByteArrayKey.ofBytes(a).hashCode(), ByteArrayKey.ofBytes(b).hashCode());
        byte[] d = bytes(32, 2);
        assertEquals(ByteArrayKey.ofDigest(d), ByteArrayKey.ofDigest(d.clone()));
        assertEquals(ByteArrayKey.ofDigest(d).hashCode(), ByteArrayKey.ofDigest(d.clone()).hashCode());
    }

    @Test
    void anyDifferenceIsAnotherKey() {
        byte[] d = bytes(32, 3);
        for (int i = 0; i < d.length; i++) {
            byte[] near = d.clone();
            near[i] ^= 0x40;
            assertNotEquals(ByteArrayKey.ofDigest(d), ByteArrayKey.ofDigest(near), "byte " + i);
            assertNotEquals(ByteArrayKey.ofBytes(d), ByteArrayKey.ofBytes(near), "byte " + i);
        }
        assertNotEquals(ByteArrayKey.ofBytes(d), ByteArrayKey.ofBytes(java.util.Arrays.copyOf(d, 31)));
        assertNotEquals(ByteArrayKey.ofBytes(d), ByteArrayKey.ofBytes(java.util.Arrays.copyOf(d, 33)));
    }

    @Test
    void digestsThatShareTheHashedPrefixStillDifferInAMap() {
        // the digest hash looks at the first eight bytes only: a collision must not be an equality
        Map<ByteArrayKey, Integer> map = new HashMap<>();
        byte[] base = bytes(32, 4);
        for (int i = 0; i < 40; i++) {
            byte[] d = base.clone();
            d[8 + i % 24] ^= (byte) (i + 1);
            d[31] = (byte) i;
            map.put(ByteArrayKey.ofDigest(d), i);
        }
        assertEquals(40, map.size());
        for (int i = 0; i < 40; i++) {
            byte[] d = base.clone();
            d[8 + i % 24] ^= (byte) (i + 1);
            d[31] = (byte) i;
            assertEquals(i, map.get(ByteArrayKey.ofDigest(d)));
        }
        assertNull(map.get(ByteArrayKey.ofDigest(base)));
    }

    @Test
    void aDigestShorterThanEightBytesStillWorks() {
        byte[] s = {1, 2, 3};
        assertEquals(ByteArrayKey.ofDigest(s), ByteArrayKey.ofDigest(new byte[]{1, 2, 3}));
        assertNotEquals(ByteArrayKey.ofDigest(s), ByteArrayKey.ofDigest(new byte[]{1, 2, 4}));
        assertEquals(ByteArrayKey.ofDigest(new byte[0]), ByteArrayKey.ofDigest(new byte[0]));
    }

    @Test
    void aKeyIsNotEqualToOtherTypes() {
        byte[] b = bytes(8, 5);
        assertNotEquals(ByteArrayKey.ofBytes(b), b);
        assertNotEquals(ByteArrayKey.ofBytes(b), null);
    }
}
