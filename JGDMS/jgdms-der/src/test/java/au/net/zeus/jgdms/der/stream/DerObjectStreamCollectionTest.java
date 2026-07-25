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

package au.net.zeus.jgdms.der.stream;

import au.net.zeus.jgdms.der.object.fixtures.IntBox;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * COLL-1 stream-level tests for the {@code [16] CTX_COLLECTION} top-level collection VALUE item on
 * a full DER object stream (version octet + mandatory schema-chain dedup), driving the
 * package-private {@link DerObjectStreamCodec#writeCollection}/{@link DerObjectStreamCodec#readObject}.
 *
 * <p>Focus: the {@code [16]} item emits and round-trips; the untyped {@code writeObject} still
 * rejects a bare collection (fallthrough intact); and the {@code [16]} interior is EXCLUDED from
 * schema-chain dedup — so two occurrences of the same collection on one stream are byte-identical
 * (the crux property held on a transport stream, not just as an isolated slice).
 */
class DerObjectStreamCollectionTest {

    private static byte[] writeColl(Object coll, String token) throws Exception {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeCollection(coll, token);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    private static Object read(byte[] bytes) throws Exception {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(bytes);
        return c.readObject();
    }

    /** The version-octet prefix a fresh stream begins with (a no-write codec drains just that). */
    private static byte[] versionPrefix() throws Exception {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        return bos.toByteArray();
    }

    // =========================================================================
    // Round-trip through the stream codec
    // =========================================================================

    @Test
    void stream_roundTrip_set_int() throws Exception {
        Set<Integer> v = new HashSet<>(List.of(9, 1, 5));
        Object back = read(writeColl(v, "set:int"));
        assertEquals(Set.of(1, 5, 9), new HashSet<>((Set<?>) back));
    }

    @Test
    void stream_emits_tag16_afterVersionOctet() throws Exception {
        byte[] bytes = writeColl(new HashSet<>(List.of(1, 2)), "set:int");
        int prefixLen = versionPrefix().length;
        assertEquals(0xB0, bytes[prefixLen] & 0xFF,
                "[16] constructed context tag encodes as 0xB0, right after the version octet");
    }

    @Test
    void stream_roundTrip_orderedset_isSequencedSet() throws Exception {
        LinkedHashSet<Integer> v = new LinkedHashSet<>(List.of(3, 1, 2));
        Object back = read(writeColl(v, "orderedset:int"));
        assertInstanceOf(SequencedSet.class, back);
        assertEquals(List.of(3, 1, 2), new ArrayList<>((Set<?>) back));
    }

    @Test
    void stream_roundTrip_orderedmap_isSequencedMap() throws Exception {
        LinkedHashMap<Integer, String> v = new LinkedHashMap<>();
        v.put(3, "c"); v.put(1, "a"); v.put(2, "b");
        Object back = read(writeColl(v, "orderedmap:{int}{java.lang.String}"));
        assertInstanceOf(SequencedMap.class, back);
        assertEquals(List.of(3, 1, 2), new ArrayList<>(((Map<?, ?>) back).keySet()));
    }

    @Test
    void stream_roundTrip_atomicSerialElements() throws Exception {
        Set<IntBox> v = new HashSet<>(List.of(new IntBox(7), new IntBox(3), new IntBox(11)));
        Object back = read(writeColl(v, "set:@AtomicSerial"));
        assertEquals(v, new HashSet<>((Set<?>) back));
    }

    // =========================================================================
    // Fallthrough intact: untyped writeObject still rejects a bare collection
    // =========================================================================

    @Test
    void writeObject_bareCollection_stillRejected() {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        assertThrows(UnsupportedOperationException.class,
                () -> c.writeObject(new HashSet<>(List.of(1, 2, 3))),
                "the untyped writeObject must still reject a bare collection (@AtomicSerial-restricted)");
    }

    @Test
    void writeObject_bareMap_stillRejected() {
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        assertThrows(UnsupportedOperationException.class,
                () -> c.writeObject(new java.util.HashMap<>(Map.of(1, "a"))));
    }

    // =========================================================================
    // Dedup interaction: the [16] interior is EXCLUDED from schema-chain dedup
    // =========================================================================

    @Test
    void streamDedup_excludesCollectionInterior_twoOccurrencesByteIdentical() throws Exception {
        // The SAME @AtomicSerial-element collection written twice on ONE stream: if the [16]
        // interior participated in schema-chain dedup, the second occurrence's @AtomicSerial
        // element chains would ride as chainRefs and the two [16] TLVs would DIFFER. They must be
        // byte-identical -- proving the [16] interior is dedup-excluded and the encoding stays a
        // pure function of (token, value) on a transport stream.
        Set<IntBox> v = new HashSet<>(List.of(new IntBox(1), new IntBox(2), new IntBox(3)));
        String token = "set:@AtomicSerial";

        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.writeCollection(v, token);
        c.writeCollection(v, token);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        c.drainTo(bos);
        byte[] two = bos.toByteArray();

        byte[] prefix = versionPrefix();
        byte[] single = writeColl(v, token);                       // version ++ one [16]
        byte[] item = Arrays.copyOfRange(single, prefix.length, single.length); // the [16] TLV alone

        // two == version ++ item ++ item
        byte[] expected = new byte[prefix.length + item.length * 2];
        System.arraycopy(prefix, 0, expected, 0, prefix.length);
        System.arraycopy(item, 0, expected, prefix.length, item.length);
        System.arraycopy(item, 0, expected, prefix.length + item.length, item.length);
        assertArrayEquals(expected, two,
                "both [16] occurrences must be byte-identical (interior excluded from dedup)");
    }

    @Test
    void stream_crux_sameMultisetAcrossImpls_identicalStreamBytes() throws Exception {
        // End-to-end crux on the stream: HashSet vs TreeSet, same multiset, same token -> identical
        // stream bytes (the property that lets a collection later serve as a match-contract slice).
        List<Integer> vals = List.of(4, 1, 9, 2);
        byte[] a = writeColl(new HashSet<>(vals), "set:int");
        byte[] b = writeColl(new TreeSet<>(vals), "set:int");
        assertArrayEquals(a, b);
    }

    // =========================================================================
    // Adversarial: a second / mid-stream [15] version octet is rejected as an item
    // =========================================================================

    @Test
    void stream_midStreamVersionOctet_rejected() throws Exception {
        // A [15] appearing where an item is expected (after the mandatory leading version octet)
        // must be rejected by readObject's catch-all.
        byte[] prefix = versionPrefix();
        byte[] bytes = new byte[prefix.length * 2];
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        System.arraycopy(prefix, 0, bytes, prefix.length, prefix.length);
        DerObjectStreamCodec c = new DerObjectStreamCodec();
        c.initReader(bytes); // consumes the first (legitimate) version octet
        assertThrows(Exception.class, c::readObject,
                "a second/mid-stream [15] version octet must be rejected as an item");
    }
}
