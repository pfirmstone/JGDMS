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

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.DerException;
import au.net.zeus.jgdms.der.DerReader;
import au.net.zeus.jgdms.der.DerWriter;
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.object.fixtures.IntBox;
import au.net.zeus.jgdms.der.object.immutable.ImmutableList;
import au.net.zeus.jgdms.der.object.immutable.ImmutableMap;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSequencedMap;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSequencedSet;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSet;
import org.junit.jupiter.api.Test;

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
 * COLL-1 unit tests at the {@link ObjectCodec} layer: the top-level collection VALUE codec
 * ({@link ObjectCodec#encodeTopLevelCollection}/{@link ObjectCodec#decodeTopLevelCollection}) and
 * the {@code SequencedSet}/{@code SequencedMap} decode shapes for the PRESERVE_ORDERED disciplines.
 *
 * <p>In package {@code au.net.zeus.jgdms.der.object} to reach the package-private
 * {@link ObjectCodec#encodeCollection} used to prove the byte-identity CRUX (a top-level collection
 * value and the same value carried as an {@code @AtomicSerial} collection FIELD share one encoding).
 */
class TopLevelCollectionCodecTest {

    private static final ResolutionContext RES = ResolutionContext.NONE;

    /** The collection TLV portion of a [16] content ({@code UTF8String(token) ++ collectionTLV}). */
    private static byte[] collTlvOf(byte[] topLevelContent) throws DerException {
        DerReader r = new DerReader(topLevelContent);
        r.readUtf8String();
        return Arrays.copyOfRange(topLevelContent, r.position(), topLevelContent.length);
    }

    // =========================================================================
    // 1. Byte-identity CRUX (G1) — pure function of (token, value)
    // =========================================================================

    @Test
    void crux_sameMultisetUnderSetToken_identicalBytes_acrossImpls() throws Exception {
        List<Integer> vals = List.of(3, 1, 2, 20, 7);
        Set<Integer> hash   = new HashSet<>(vals);
        Set<Integer> tree   = new TreeSet<>(vals);
        Set<Integer> linked = new LinkedHashSet<>(List.of(20, 7, 1, 3, 2)); // different insertion order

        byte[] a = ObjectCodec.encodeTopLevelCollection(hash,   "set:int");
        byte[] b = ObjectCodec.encodeTopLevelCollection(tree,   "set:int");
        byte[] c = ObjectCodec.encodeTopLevelCollection(linked, "set:int");

        assertArrayEquals(a, b, "HashSet vs TreeSet same multiset under set: -> identical [16] bytes");
        assertArrayEquals(a, c, "LinkedHashSet (any insertion order) under set: -> identical [16] bytes");
    }

    @Test
    void crux_topLevelValueBytesEqualCollectionFieldSlice() throws Exception {
        // The collectionTLV inside a [16] top-level value MUST be byte-identical to the same value
        // encoded as an @AtomicSerial collection FIELD (encodeCollection is exactly what a field
        // slice uses): one encoding per value across carriage positions (board guidance H1).
        Set<Integer> value = new HashSet<>(List.of(5, 1, 9, 2));
        String token = "set:int";

        byte[] topLevel  = ObjectCodec.encodeTopLevelCollection(value, token);
        byte[] fieldSlice = ObjectCodec.encodeCollection(value, token, "<field>", 0);

        assertArrayEquals(fieldSlice, collTlvOf(topLevel),
                "top-level [16] collection TLV must equal the @AtomicSerial FIELD slice bytes");
    }

    @Test
    void crux_atomicSerialElements_identicalBytes_acrossImpls() throws Exception {
        List<IntBox> vals = List.of(new IntBox(3), new IntBox(1), new IntBox(2));
        Set<IntBox> hash = new HashSet<>(vals);
        Set<IntBox> linked = new LinkedHashSet<>(List.of(new IntBox(2), new IntBox(3), new IntBox(1)));

        byte[] a = ObjectCodec.encodeTopLevelCollection(hash,   "set:@AtomicSerial");
        byte[] b = ObjectCodec.encodeTopLevelCollection(linked, "set:@AtomicSerial");
        assertArrayEquals(a, b, "@AtomicSerial-element set under set: canonicalises identically");
    }

    // =========================================================================
    // 2. Declared Set vs SequencedSet (Q3) — canonicalise vs preserve differ
    // =========================================================================

    @Test
    void q3_sameLinkedHashSet_setVsOrderedset_differentBytes() throws Exception {
        LinkedHashSet<Integer> v = new LinkedHashSet<>(List.of(3, 1, 2));
        byte[] canon    = ObjectCodec.encodeTopLevelCollection(v, "set:int");
        byte[] preserve = ObjectCodec.encodeTopLevelCollection(v, "orderedset:int");
        assertFalse(Arrays.equals(canon, preserve),
                "set: (octet-sorted) vs orderedset: (insertion order 3,1,2) must differ");
        // outer tag: canonicalise -> 0x31 SET OF; preserve -> 0x30 SEQUENCE OF
        assertEquals(0x31, collTlvOf(canon)[0] & 0xFF);
        assertEquals(0x30, collTlvOf(preserve)[0] & 0xFF);
    }

    @Test
    void q3_disciplineFor_sequencedSet_isPreserveOrdered() {
        assertEquals(CollectionWireTypes.Discipline.PRESERVE_ORDERED,
                CollectionWireTypes.disciplineFor(SequencedSet.class),
                "a declared SequencedSet must be PRESERVE_ORDERED (orderedset:)");
        assertTrue(CollectionWireTypes.setToken(SequencedSet.class, "int").startsWith("orderedset:"),
                "SequencedSet-declared -> orderedset: token");
        // And a plain HashSet stays canonicalise (regression guard for the Q3 change).
        assertEquals(CollectionWireTypes.Discipline.CANONICALISE,
                CollectionWireTypes.disciplineFor(HashSet.class));
    }

    @Test
    void q3_disciplineFor_sequencedMap_isPreserveOrdered() {
        assertEquals(CollectionWireTypes.Discipline.PRESERVE_ORDERED,
                CollectionWireTypes.disciplineFor(SequencedMap.class),
                "a declared SequencedMap must be PRESERVE_ORDERED (orderedmap:)");
        assertTrue(CollectionWireTypes.mapToken(SequencedMap.class, "int", "java.lang.String")
                        .startsWith("orderedmap:"),
                "SequencedMap-declared -> orderedmap: token");
        assertEquals(CollectionWireTypes.Discipline.CANONICALISE,
                CollectionWireTypes.disciplineFor(java.util.HashMap.class));
    }

    // =========================================================================
    // 3. Round-trip — all six tokens; typed / any / @AtomicSerial / nested
    // =========================================================================

    @Test
    void roundTrip_set_int() throws Exception {
        Set<Integer> v = new HashSet<>(List.of(9, 1, 5));
        Object back = decode(ObjectCodec.encodeTopLevelCollection(v, "set:int"));
        assertInstanceOf(ImmutableSet.class, back);
        assertFalse(back instanceof SequencedSet, "set: must be a plain Set, not a SequencedSet");
        assertEquals(Set.of(1, 5, 9), new HashSet<>((Set<?>) back));
    }

    @Test
    void roundTrip_orderedset_int_isSequencedSet_preservesOrder() throws Exception {
        LinkedHashSet<Integer> v = new LinkedHashSet<>(List.of(3, 1, 2));
        Object back = decode(ObjectCodec.encodeTopLevelCollection(v, "orderedset:int"));
        assertInstanceOf(SequencedSet.class, back, "orderedset: MUST decode to a SequencedSet");
        assertInstanceOf(ImmutableSequencedSet.class, back);
        assertEquals(List.of(3, 1, 2), new ArrayList<>((Set<?>) back), "insertion order preserved");
    }

    @Test
    void roundTrip_list_int() throws Exception {
        List<Integer> v = List.of(3, 1, 2, 3);
        Object back = decode(ObjectCodec.encodeTopLevelCollection(v, "list:int"));
        assertInstanceOf(ImmutableList.class, back);
        assertEquals(v, back);
    }

    @Test
    void roundTrip_bag_int_retainsDuplicates() throws Exception {
        List<Integer> v = List.of(5, 1, 5); // multiset with a duplicate
        Object back = decode(ObjectCodec.encodeTopLevelCollection(v, "bag:int"));
        assertInstanceOf(ImmutableList.class, back);
        assertEquals(3, ((List<?>) back).size(), "bag: retains the duplicate");
        assertEquals(List.of(1, 5, 5), back, "bag: is octet-sorted (non-decreasing)");
    }

    @Test
    void roundTrip_map_isPlainMap() throws Exception {
        Map<Integer, String> v = new java.util.HashMap<>();
        v.put(3, "c"); v.put(1, "a"); v.put(2, "b");
        Object back = decode(ObjectCodec.encodeTopLevelCollection(v, "map:{int}{java.lang.String}"));
        assertInstanceOf(ImmutableMap.class, back);
        assertFalse(back instanceof SequencedMap, "map: must be a plain Map, not a SequencedMap");
        assertEquals(v, back);
    }

    @Test
    void roundTrip_orderedmap_isSequencedMap_preservesOrder() throws Exception {
        LinkedHashMap<Integer, String> v = new LinkedHashMap<>();
        v.put(3, "c"); v.put(1, "a"); v.put(2, "b");
        Object back = decode(ObjectCodec.encodeTopLevelCollection(v, "orderedmap:{int}{java.lang.String}"));
        assertInstanceOf(SequencedMap.class, back, "orderedmap: MUST decode to a SequencedMap");
        assertInstanceOf(ImmutableSequencedMap.class, back);
        assertEquals(List.of(3, 1, 2), new ArrayList<>(((Map<?, ?>) back).keySet()),
                "insertion order preserved");
    }

    @Test
    void roundTrip_atomicSerialElements_gateExercised() throws Exception {
        Set<IntBox> v = new HashSet<>(List.of(new IntBox(7), new IntBox(3)));
        Object back = decode(ObjectCodec.encodeTopLevelCollection(v, "set:@AtomicSerial"));
        assertInstanceOf(ImmutableSet.class, back);
        assertEquals(v, new HashSet<>((Set<?>) back));
    }

    @Test
    void roundTrip_nestedCollection() throws Exception {
        // list:set:int — a list whose elements are canonicalise sets.
        List<Set<Integer>> v = List.of(new HashSet<>(List.of(2, 1)), new HashSet<>(List.of(9, 5)));
        Object back = decode(ObjectCodec.encodeTopLevelCollection(v, "list:set:int"));
        assertInstanceOf(ImmutableList.class, back);
        List<?> outer = (List<?>) back;
        assertEquals(2, outer.size());
        assertEquals(Set.of(1, 2), new HashSet<>((Set<?>) outer.get(0)));
        assertEquals(Set.of(5, 9), new HashSet<>((Set<?>) outer.get(1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void roundTrip_sequencedSet_reversed_isImmutableAndOrdered() throws Exception {
        LinkedHashSet<Integer> v = new LinkedHashSet<>(List.of(3, 1, 2));
        SequencedSet<Integer> back = (SequencedSet<Integer>) decode(
                ObjectCodec.encodeTopLevelCollection(v, "orderedset:int"));
        assertEquals(List.of(2, 1, 3), new ArrayList<>(back.reversed()));
        assertEquals(3, back.getFirst());
        assertEquals(2, back.getLast());
        assertThrows(UnsupportedOperationException.class, () -> back.addFirst(0));
    }

    // =========================================================================
    // 4. Adversarial (G10-G13)
    // =========================================================================

    @Test
    void adversarial_lyingOuterTag_setTokenOver0x30_rejected() throws Exception {
        // Build a PRESERVE-shaped body (0x30 SEQUENCE OF) but label it with a set: (canonicalise)
        // token -> the AnyCodec Option-A fence must reject it.
        byte[] preserveContent = ObjectCodec.encodeTopLevelCollection(
                new LinkedHashSet<>(List.of(3, 1, 2)), "orderedset:int");
        byte[] lyingContent = relabelToken(preserveContent, "set:int");
        DerException ex = assertThrows(DerException.class,
                () -> ObjectCodec.decodeTopLevelCollection(lyingContent, null, RES));
        assertTrue(ex.getMessage().toLowerCase().contains("lying")
                        || ex.getMessage().contains("outer tag"),
                "expected an Option-A outer-tag rejection, got: " + ex.getMessage());
    }

    @Test
    void adversarial_shuffledSetBody_rejected() throws Exception {
        // A set: body whose elements are NOT strictly ascending must be rejected on decode.
        byte[] elem1 = DerWriter.writeInteger(java.math.BigInteger.valueOf(1));
        byte[] elem2 = DerWriter.writeInteger(java.math.BigInteger.valueOf(2));
        // deliberately out of order: 2 then 1
        byte[] body = DerWriter.writeSet(List.of(elem2, elem1)); // writeSet does NOT reorder its input
        byte[] content = concat(DerWriter.writeUtf8String("set:int"), body);
        DerException ex = assertThrows(DerException.class,
                () -> ObjectCodec.decodeTopLevelCollection(content, null, RES));
        assertTrue(ex.getMessage().contains("ascending") || ex.getMessage().contains("order"),
                "expected an order rejection, got: " + ex.getMessage());
    }

    @Test
    void adversarial_duplicateUnderSet_rejected() throws Exception {
        byte[] e1 = DerWriter.writeInteger(java.math.BigInteger.valueOf(1));
        byte[] body = DerWriter.writeSet(List.of(e1, e1)); // duplicate encoding
        byte[] content = concat(DerWriter.writeUtf8String("set:int"), body);
        assertThrows(DerException.class,
                () -> ObjectCodec.decodeTopLevelCollection(content, null, RES),
                "a duplicate element encoding under set: must be rejected (not strictly ascending)");
    }

    @Test
    void adversarial_deepNestedBeyondMaxNesting_guardedNotStackOverflow() throws Exception {
        // A GENUINELY nested value+token far beyond MAX_NESTING must be rejected fail-secure by the
        // depth guard (symmetric encode/decode) -- never a StackOverflowError.
        int levels = ObjectCodec.MAX_NESTING + 3;
        Object v = List.of(1);                 // innermost: list:int
        StringBuilder tok = new StringBuilder("list:int");
        for (int i = 1; i < levels; i++) {
            v = List.of(v);                    // wrap one more list: level
            tok.insert(0, "list:");
        }
        final Object deep = v;
        DerException ex = assertThrows(DerException.class,
                () -> ObjectCodec.encodeTopLevelCollection(deep, tok.toString()));
        assertTrue(ex.getMessage().contains("MAX_NESTING") || ex.getMessage().contains("nesting"),
                "expected a nesting-depth guard, got: " + ex.getMessage());
    }

    @Test
    void adversarial_emptyCollection_bothDisciplines() throws Exception {
        // Empty SET OF (set:) vs empty SEQUENCE OF (orderedset:) — G11 boundary.
        byte[] emptySet = ObjectCodec.encodeTopLevelCollection(new HashSet<>(), "set:int");
        byte[] emptyOrdered = ObjectCodec.encodeTopLevelCollection(new LinkedHashSet<>(), "orderedset:int");
        assertEquals(0x31, collTlvOf(emptySet)[0] & 0xFF, "empty set: is an empty SET OF (0x31)");
        assertEquals(0x30, collTlvOf(emptyOrdered)[0] & 0xFF, "empty orderedset: is an empty SEQUENCE OF (0x30)");
        assertTrue(((Set<?>) decode(emptySet)).isEmpty());
        Object ordered = decode(emptyOrdered);
        assertTrue(((Set<?>) ordered).isEmpty());
        assertInstanceOf(SequencedSet.class, ordered, "even empty, orderedset: is a SequencedSet");
    }

    @Test
    void adversarial_trailingBytesAfterCollectionTlv_rejected() throws Exception {
        byte[] good = ObjectCodec.encodeTopLevelCollection(new HashSet<>(List.of(1, 2)), "set:int");
        byte[] withTrailer = concat(good, new byte[] { 0x00, 0x00 });
        assertThrows(DerException.class,
                () -> ObjectCodec.decodeTopLevelCollection(withTrailer, null, RES),
                "trailing bytes after the collection TLV must be rejected");
    }

    @Test
    void adversarial_nonCollectionToken_rejected() throws Exception {
        byte[] content = concat(DerWriter.writeUtf8String("int"),
                DerWriter.writeSet(List.<byte[]>of()));
        DerException ex = assertThrows(DerException.class,
                () -> ObjectCodec.decodeTopLevelCollection(content, null, RES));
        assertTrue(ex.getMessage().contains("not a collection"),
                "a non-collection token must be rejected, got: " + ex.getMessage());
    }

    @Test
    void adversarial_malformedMapToken_emptyValueSubtoken_rejected() {
        Map<Integer, String> v = new java.util.HashMap<>();
        v.put(1, "a");
        assertThrows(DerException.class,
                () -> ObjectCodec.encodeTopLevelCollection(v, "map:{int}{}"),
                "an empty value sub-token must be rejected by the grammar check");
    }

    @Test
    void adversarial_oversizedGarbageToken_rejected() throws Exception {
        String garbage = "x".repeat(100_000); // not a known collection prefix
        byte[] content = concat(DerWriter.writeUtf8String(garbage), DerWriter.writeSet(List.<byte[]>of()));
        assertThrows(DerException.class,
                () -> ObjectCodec.decodeTopLevelCollection(content, null, RES),
                "an oversized non-collection token must be rejected (fail-secure)");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Object decode(byte[] topLevelContent) throws Exception {
        return ObjectCodec.decodeTopLevelCollection(topLevelContent, null, RES);
    }

    /** Rebuilds a [16] content with the same collection TLV but a different token string. */
    private static byte[] relabelToken(byte[] content, String newToken) throws DerException {
        return concat(DerWriter.writeUtf8String(newToken), collTlvOf(content));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
