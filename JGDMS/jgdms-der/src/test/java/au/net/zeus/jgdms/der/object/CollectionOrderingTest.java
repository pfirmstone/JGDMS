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
import au.net.zeus.jgdms.der.getarg.CollectionWireTypes;
import au.net.zeus.jgdms.der.object.fixtures.CollectionRecord;
import au.net.zeus.jgdms.der.object.fixtures.IntBox;
import au.net.zeus.jgdms.der.object.fixtures.PlainCollectionFieldRecord;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STD-006 §3.8 collection encounter-order codec tests (memo §6.4 conformance sketch plus the
 * §0.1 value-equality-proxy / §3a tension properties).
 *
 * <p>Each test builds a single-field {@code @AtomicSerial} schema whose {@code "coll"} field
 * carries a specific collection wire-type token, encodes a {@link CollectionRecord} holding a
 * collection value, and inspects the bytes / round-trip. The token's discipline (preserve vs
 * canonicalise) is derived from the DECLARED collection class via
 * {@link SchemaGenerator#collectionWireType} / {@link SchemaGenerator#mapWireType}, exactly as
 * a schema generator would at marshal time.
 */
class CollectionOrderingTest {

    // -------------------------------------------------------------------------
    // Helpers: build a single-"coll"-field schema with a given token, encode, decode.
    // -------------------------------------------------------------------------

    private static AtomicSerialSchemaRecord schema(String collToken) {
        return new AtomicSerialSchemaRecord(
                CollectionRecord.class.getName(),
                (byte[]) null,
                List.of(new AtomicSerialFieldDef("coll", collToken)));
    }

    private static byte[] encode(Object collectionValue, String collToken) throws Exception {
        return ObjectCodec.encode(new CollectionRecord(collectionValue),
                CollectionRecord.class, schema(collToken));
    }

    private static Object decode(byte[] bytes, String collToken) throws Exception {
        CollectionRecord r = ObjectCodec.decode(CollectionRecord.class, schema(collToken), bytes);
        return r.getColl();
    }

    private static Object roundTrip(Object value, String collToken) throws Exception {
        return decode(encode(value, collToken), collToken);
    }

    // Convenience token builders keyed on declared collection class + element type.
    private static String setTok(Class<?> declared, String elemWT) {
        return SchemaGenerator.collectionWireType(declared, elemWT);
    }
    private static String mapTok(Class<?> declared, String keyWT, String valWT) {
        return SchemaGenerator.mapWireType(declared, keyWT, valWT);
    }

    // =========================================================================
    // Test 1 -- Determinism: HashSet, two insertion orders -> byte-identical (octet-sort).
    // =========================================================================

    @Test
    void hashSet_twoInsertionOrders_byteIdentical() throws Exception {
        String tok = setTok(HashSet.class, "int");
        assertTrue(tok.startsWith("set:"), "HashSet must map to canonicalise token, got " + tok);

        Set<Integer> h1 = new HashSet<>();
        h1.add(1); h1.add(2); h1.add(3);
        Set<Integer> h2 = new HashSet<>();
        h2.add(3); h2.add(1); h2.add(2);

        byte[] e1 = encode(h1, tok);
        byte[] e2 = encode(h2, tok);
        assertArrayEquals(e1, e2,
                "HashSet built in two insertion orders must encode byte-identically (octet-sort "
                + "erases insertion order) -- STD-006 §3.8 / memo §6.4(1)");
    }

    @Test
    void hashSet_differentInitialCapacities_byteIdentical() throws Exception {
        String tok = setTok(HashSet.class, "int");
        Set<Integer> small = new HashSet<>(2);
        Set<Integer> big   = new HashSet<>(256);
        for (int v : new int[]{5, 17, 42, 8, 99}) { small.add(v); big.add(v); }
        assertArrayEquals(encode(small, tok), encode(big, tok),
                "HashSet with different capacities must encode byte-identically (bucket order "
                + "is not part of the value) -- memo §6.4(1)");
    }

    @Test
    void hashMap_twoInsertionOrders_byteIdentical() throws Exception {
        String tok = mapTok(HashMap.class, "int", "java.lang.String");
        assertTrue(tok.startsWith("map:"), "HashMap must map to canonicalise token, got " + tok);

        Map<Integer, String> m1 = new HashMap<>();
        m1.put(1, "a"); m1.put(2, "b"); m1.put(3, "c");
        Map<Integer, String> m2 = new HashMap<>();
        m2.put(3, "c"); m2.put(1, "a"); m2.put(2, "b");

        assertArrayEquals(encode(m1, tok), encode(m2, tok),
                "HashMap keyed in two insertion orders must encode byte-identically (entries "
                + "octet-sorted by encoded key) -- memo §6.4(2)");
    }

    // =========================================================================
    // Test 2 -- Value-equality-proxy property (§0.1): two .equals HashSets -> identical DER;
    // byte-equality tracks .equals.
    // =========================================================================

    @Test
    void equalHashSets_produceEqualBytes_valueEqualityProxy() throws Exception {
        String tok = setTok(HashSet.class, "java.lang.String");
        Set<String> a = new HashSet<>(Arrays.asList("x", "y", "z"));
        Set<String> b = new HashSet<>(Arrays.asList("z", "y", "x"));
        assertEquals(a, b, "precondition: the two sets are .equals");
        assertArrayEquals(encode(a, tok), encode(b, tok),
                "two .equals HashSets must produce identical DER (byte-equality tracks .equals) "
                + "-- STD-006 §0.1 value-equality proxy");
    }

    @Test
    void unequalHashSets_produceDifferentBytes() throws Exception {
        String tok = setTok(HashSet.class, "int");
        byte[] e1 = encode(new HashSet<>(Arrays.asList(1, 2, 3)), tok);
        byte[] e2 = encode(new HashSet<>(Arrays.asList(1, 2, 4)), tok);
        assertFalse(Arrays.equals(e1, e2),
                "sets that are NOT .equals must NOT encode byte-identically");
    }

    // =========================================================================
    // Test 3 -- The documented tension (§3a): two .equals LinkedHashSets in DIFFERENT insertion
    // orders -> DIFFERENT bytes (preserve keeps order; byte-equality stricter than .equals).
    // =========================================================================

    @Test
    void linkedHashSet_equalButDifferentInsertionOrder_differentBytes_intendedTension() throws Exception {
        String tok = setTok(LinkedHashSet.class, "int");
        assertTrue(tok.startsWith("orderedset:"),
                "LinkedHashSet must map to preserve token, got " + tok);

        Set<Integer> abc = new LinkedHashSet<>();
        abc.add(1); abc.add(2); abc.add(3);
        Set<Integer> cba = new LinkedHashSet<>();
        cba.add(3); cba.add(2); cba.add(1);
        assertEquals(abc, cba, "precondition: LinkedHashSet.equals ignores insertion order");

        byte[] e1 = encode(abc, tok);
        byte[] e2 = encode(cba, tok);
        assertFalse(Arrays.equals(e1, e2),
                "two .equals LinkedHashSets built in DIFFERENT insertion order MUST encode to "
                + "DIFFERENT bytes -- preserve keeps order; byte-equality is stricter than "
                + ".equals here (STD-006 §3a, intended not a bug)");

        // And the transmitted order round-trips exactly (insertion order preserved).
        @SuppressWarnings("unchecked")
        Collection<Integer> back = (Collection<Integer>) decode(e1, tok);
        assertEquals(List.of(1, 2, 3), new ArrayList<>(back),
                "LinkedHashSet insertion order must round-trip");
    }

    // =========================================================================
    // Test 4 -- Element-derived preserve (no tension): two .equals TreeSets / EnumSets ->
    // identical bytes (order is a function of the elements).
    // =========================================================================

    @Test
    void treeSet_equalDifferentInsertionOrder_identicalBytes_noTension() throws Exception {
        String tok = setTok(TreeSet.class, "int");
        assertTrue(tok.startsWith("orderedset:"), "TreeSet must map to preserve token, got " + tok);
        Set<Integer> t1 = new TreeSet<>(Arrays.asList(3, 1, 2));
        Set<Integer> t2 = new TreeSet<>(Arrays.asList(2, 3, 1));
        assertArrayEquals(encode(t1, tok), encode(t2, tok),
                "two .equals TreeSets must encode identically -- element-derived order, no §3a tension");
    }

    @Test
    void enumSet_equalDifferentInsertionOrder_identicalBytes_noTension() throws Exception {
        String tok = setTok(EnumSet.class, "enum:" + Day.class.getName());
        assertTrue(tok.startsWith("orderedset:"), "EnumSet must map to preserve token, got " + tok);
        Set<Day> s1 = EnumSet.of(Day.WED, Day.MON);
        Set<Day> s2 = EnumSet.of(Day.MON, Day.WED);
        assertArrayEquals(encode(s1, tok), encode(s2, tok),
                "two .equals EnumSets must encode identically -- ordinal order, no §3a tension");
        // Round-trips in ordinal order.
        @SuppressWarnings("unchecked")
        Collection<Day> back = (Collection<Day>) roundTrip(s1, tok);
        assertEquals(List.of(Day.MON, Day.WED), new ArrayList<>(back));
    }

    @Test
    void concurrentSkipListSet_isPreserve_sortedOrder() throws Exception {
        String tok = setTok(ConcurrentSkipListSet.class, "int");
        assertTrue(tok.startsWith("orderedset:"),
                "ConcurrentSkipListSet must map to preserve token (comparator-derived), got " + tok);
        Set<Integer> s = new ConcurrentSkipListSet<>(Arrays.asList(5, 1, 3));
        @SuppressWarnings("unchecked")
        Collection<Integer> back = (Collection<Integer>) roundTrip(s, tok);
        assertEquals(List.of(1, 3, 5), new ArrayList<>(back),
                "ConcurrentSkipListSet preserves its ascending comparator order");
    }

    // =========================================================================
    // Test 5 -- List/array preserve: order round-trips exactly; [a,b] != [b,a].
    // =========================================================================

    @Test
    void list_orderPreservedExactly_andOrderIsSignificant() throws Exception {
        String tok = setTok(ArrayList.class, "int");
        assertTrue(tok.startsWith("list:"), "ArrayList must map to list (preserve) token, got " + tok);

        List<Integer> ab = new ArrayList<>(List.of(10, 20));
        List<Integer> ba = new ArrayList<>(List.of(20, 10));
        assertFalse(Arrays.equals(encode(ab, tok), encode(ba, tok)),
                "[a,b] and [b,a] must be DIFFERENT encodings for a List -- order is the value");

        @SuppressWarnings("unchecked")
        List<Integer> back = new ArrayList<>((Collection<Integer>) roundTrip(ab, tok));
        assertEquals(List.of(10, 20), back, "List order must round-trip exactly");
    }

    @Test
    void list_allowsDuplicates() throws Exception {
        String tok = setTok(LinkedList.class, "int");
        assertTrue(tok.startsWith("list:"));
        List<Integer> withDup = new LinkedList<>(List.of(7, 7, 8));
        @SuppressWarnings("unchecked")
        List<Integer> back = new ArrayList<>((Collection<Integer>) roundTrip(withDup, tok));
        assertEquals(List.of(7, 7, 8), back, "a list field must retain duplicates");
    }

    @Test
    void arrayDeque_isPreserveList() throws Exception {
        String tok = setTok(ArrayDeque.class, "int");
        assertTrue(tok.startsWith("list:"), "ArrayDeque must map to preserve token, got " + tok);
        ArrayDeque<Integer> dq = new ArrayDeque<>(List.of(1, 2, 3));
        @SuppressWarnings("unchecked")
        List<Integer> back = new ArrayList<>((Collection<Integer>) roundTrip(dq, tok));
        assertEquals(List.of(1, 2, 3), back, "ArrayDeque head->tail order must round-trip");
    }

    // =========================================================================
    // Test 6 -- Concurrent family: COWArraySet canonicalises; ConcurrentSkipListSet preserves;
    // a concurrent queue canonicalises.
    // =========================================================================

    @Test
    void copyOnWriteArraySet_canonicalises_reversalCase() throws Exception {
        String tok = setTok(CopyOnWriteArraySet.class, "int");
        assertTrue(tok.startsWith("set:"),
                "CopyOnWriteArraySet must map to CANONICALISE token (a Set, order not the value; "
                + "concurrent insertion is non-deterministic) -- got " + tok);
        Set<Integer> abc = new CopyOnWriteArraySet<>(List.of(1, 2, 3));
        Set<Integer> cab = new CopyOnWriteArraySet<>(List.of(3, 1, 2));
        assertArrayEquals(encode(abc, tok), encode(cab, tok),
                "CopyOnWriteArraySet must canonicalise (insertion order erased) -- memo §6.4(6)");
    }

    @Test
    void concurrentLinkedQueue_canonicalisesAsMultiset() throws Exception {
        String tok = setTok(ConcurrentLinkedQueue.class, "int");
        assertTrue(tok.startsWith("bag:"),
                "ConcurrentLinkedQueue must map to canonicalise-multiset token, got " + tok);
        Collection<Integer> q1 = new ConcurrentLinkedQueue<>(List.of(1, 2, 3, 2));
        Collection<Integer> q2 = new ConcurrentLinkedQueue<>(List.of(2, 3, 2, 1));
        assertArrayEquals(encode(q1, tok), encode(q2, tok),
                "ConcurrentLinkedQueue with the same multiset in different orders must canonicalise "
                + "byte-identically (duplicates retained) -- memo §6.4(6)");
    }

    @Test
    void priorityQueue_canonicalisesAsMultiset_retainsDuplicates() throws Exception {
        String tok = setTok(PriorityQueue.class, "int");
        assertTrue(tok.startsWith("bag:"),
                "PriorityQueue must map to canonicalise-multiset token (iterator disclaims order), got " + tok);
        PriorityQueue<Integer> p1 = new PriorityQueue<>(List.of(5, 1, 5, 3));
        PriorityQueue<Integer> p2 = new PriorityQueue<>(List.of(3, 5, 1, 5));
        assertArrayEquals(encode(p1, tok), encode(p2, tok),
                "two PriorityQueues with the same multiset must canonicalise byte-identically "
                + "(heap order irrelevant, duplicate retained) -- memo §6.4(5)");
        // The duplicate survives the round-trip (multiset -> list container on decode).
        @SuppressWarnings("unchecked")
        Collection<Integer> back = (Collection<Integer>) decode(encode(p1, tok), tok);
        List<Integer> sortedBack = new ArrayList<>(back);
        sortedBack.sort(Integer::compare);
        assertEquals(List.of(1, 3, 5, 5), sortedBack, "duplicate 5 must be retained (multiset)");
    }

    // =========================================================================
    // Test 7 -- Recursive: a canonicalise-set whose elements are themselves collections.
    // Inner canonicalised first, outer sorted over the inner encodings; round-trips.
    // =========================================================================

    @Test
    void recursive_setOfCanonicaliseSets_bottomUp() throws Exception {
        // Outer HashSet<HashSet<Integer>>: each inner set canonicalised, then the outer set
        // octet-sorted over the inner encodings.
        String innerTok = setTok(HashSet.class, "int");            // set:int
        String outerTok = setTok(HashSet.class, innerTok);          // set:set:int

        Set<Set<Integer>> a = new HashSet<>();
        a.add(new HashSet<>(List.of(3, 1)));   // inner built {3,1}
        a.add(new HashSet<>(List.of(2, 4)));
        Set<Set<Integer>> b = new HashSet<>();
        b.add(new HashSet<>(List.of(4, 2)));   // inner built {4,2} (same value, diff order)
        b.add(new HashSet<>(List.of(1, 3)));

        assertEquals(a, b, "precondition: the two nested sets are .equals");
        assertArrayEquals(encode(a, outerTok), encode(b, outerTok),
                "a canonicalise-set of canonicalise-sets must encode byte-identically regardless "
                + "of any insertion order at either level (bottom-up §11.6) -- memo §6.4(3)");

        // Round-trips to the same logical value.
        @SuppressWarnings("unchecked")
        Collection<Object> back = (Collection<Object>) decode(encode(a, outerTok), outerTok);
        Set<Set<Integer>> reconstructed = new HashSet<>();
        for (Object o : back) {
            @SuppressWarnings("unchecked")
            Collection<Integer> inner = (Collection<Integer>) o;
            reconstructed.add(new HashSet<>(inner));
        }
        assertEquals(a, reconstructed, "recursive canonicalise-set must round-trip logical value");
    }

    @Test
    void recursive_mapWithSetValues_canonicalises() throws Exception {
        // Map<String, HashSet<Integer>> canonicalise: entries sorted by key encoding, each value
        // set canonicalised.
        String valTok = setTok(HashSet.class, "int");
        String mapTok = mapTok(HashMap.class, "java.lang.String", valTok);

        Map<String, Set<Integer>> m1 = new HashMap<>();
        m1.put("a", new HashSet<>(List.of(1, 2)));
        m1.put("b", new HashSet<>(List.of(3, 4)));
        Map<String, Set<Integer>> m2 = new HashMap<>();
        m2.put("b", new HashSet<>(List.of(4, 3)));
        m2.put("a", new HashSet<>(List.of(2, 1)));

        assertArrayEquals(encode(m1, mapTok), encode(m2, mapTok),
                "a canonicalise map with canonicalise-set values must encode byte-identically "
                + "regardless of insertion order at either level");
    }

    // =========================================================================
    // Test 8 -- Round-trip decode -> equals the original logical value for every discipline.
    // =========================================================================

    @Test
    void roundTrip_hashSet_logicalValue() throws Exception {
        String tok = setTok(HashSet.class, "java.lang.String");
        Set<String> original = new HashSet<>(Arrays.asList("alpha", "beta", "gamma"));
        @SuppressWarnings("unchecked")
        Collection<String> back = (Collection<String>) roundTrip(original, tok);
        assertEquals(original, new HashSet<>(back), "HashSet round-trips its logical value");
    }

    @Test
    void roundTrip_hashMap_logicalValue_withNulls() throws Exception {
        String tok = mapTok(HashMap.class, "java.lang.String", "java.lang.String");
        Map<String, String> original = new HashMap<>();
        original.put("k1", "v1");
        original.put("k2", null);   // null value
        original.put(null, "vNull"); // null key (HashMap permits one)
        @SuppressWarnings("unchecked")
        Map<String, String> back = (Map<String, String>) roundTrip(original, tok);
        assertEquals(original, back,
                "HashMap round-trips with null key/value (null encodes as NULL, sorts first) -- memo §6.4(2)");
    }

    @Test
    void roundTrip_treeMap_logicalValueAndOrder() throws Exception {
        String tok = mapTok(TreeMap.class, "int", "java.lang.String");
        assertTrue(tok.startsWith("orderedmap:"), "TreeMap must map to preserve token, got " + tok);
        Map<Integer, String> original = new TreeMap<>();
        original.put(3, "c"); original.put(1, "a"); original.put(2, "b");
        @SuppressWarnings("unchecked")
        Map<Integer, String> back = (Map<Integer, String>) roundTrip(original, tok);
        assertEquals(original, back);
        assertEquals(new ArrayList<>(original.keySet()), new ArrayList<>(back.keySet()),
                "TreeMap ascending key order round-trips");
    }

    @Test
    void roundTrip_atomicSerialElements_hashSet() throws Exception {
        // @AtomicSerial element type: each element's concrete class travels in its embedded schema.
        String tok = setTok(HashSet.class, "@AtomicSerial");
        Set<IntBox> original = new HashSet<>(List.of(new IntBox(1), new IntBox(2), new IntBox(3)));
        @SuppressWarnings("unchecked")
        Collection<IntBox> back = (Collection<IntBox>) roundTrip(original, tok);
        assertEquals(original, new HashSet<>(back), "@AtomicSerial-element HashSet round-trips");
    }

    @Test
    void atomicSerialElements_hashSet_deterministicAcrossInsertionOrder() throws Exception {
        String tok = setTok(HashSet.class, "@AtomicSerial");
        Set<IntBox> a = new HashSet<>();
        a.add(new IntBox(3)); a.add(new IntBox(1)); a.add(new IntBox(2));
        Set<IntBox> b = new HashSet<>();
        b.add(new IntBox(1)); b.add(new IntBox(2)); b.add(new IntBox(3));
        assertArrayEquals(encode(a, tok), encode(b, tok),
                "a canonicalise-set of @AtomicSerial values octet-sorts by the nested canonical "
                + "encoding -- deterministic regardless of insertion order");
    }

    @Test
    void roundTrip_list_logicalValue() throws Exception {
        String tok = setTok(ArrayList.class, "java.lang.String");
        List<String> original = new ArrayList<>(List.of("one", "two", "three"));
        @SuppressWarnings("unchecked")
        List<String> back = new ArrayList<>((Collection<String>) roundTrip(original, tok));
        assertEquals(original, back);
    }

    @Test
    void copyOnWriteArrayList_preservesOrder_contrastWithSet() throws Exception {
        String tok = setTok(CopyOnWriteArrayList.class, "int");
        assertTrue(tok.startsWith("list:"),
                "CopyOnWriteArrayList must PRESERVE (a List, order is the value) -- contrast "
                + "CopyOnWriteArraySet which canonicalises. Got " + tok);
        List<Integer> original = new CopyOnWriteArrayList<>(List.of(9, 7, 8));
        @SuppressWarnings("unchecked")
        List<Integer> back = new ArrayList<>((Collection<Integer>) roundTrip(original, tok));
        assertEquals(List.of(9, 7, 8), back, "CopyOnWriteArrayList order preserved");
    }

    // =========================================================================
    // Null field + empty collection.
    // =========================================================================

    @Test
    void nullCollectionField_roundTrips() throws Exception {
        String tok = setTok(HashSet.class, "int");
        byte[] bytes = encode(null, tok);
        assertNull(decode(bytes, tok), "a null collection field round-trips as null");
    }

    @Test
    void emptyCollection_roundTrips() throws Exception {
        String tok = setTok(HashSet.class, "int");
        @SuppressWarnings("unchecked")
        Collection<Integer> back = (Collection<Integer>) roundTrip(new HashSet<Integer>(), tok);
        assertTrue(back.isEmpty(), "an empty collection round-trips empty");
    }

    // =========================================================================
    // Duplicate-encoding rejection for canonicalised set-typed fields (memo §6.4(10)).
    // =========================================================================

    @Test
    void duplicateElementEncoding_inSetField_isRejected() throws Exception {
        // Hand-craft a set:int field payload with two identical element encodings (INTEGER 7).
        String tok = setTok(HashSet.class, "int");
        byte[] intSeven = au.net.zeus.jgdms.der.DerWriter.writeInteger(java.math.BigInteger.valueOf(7));
        byte[] collSeq = au.net.zeus.jgdms.der.DerWriter.writeSequence(List.of(intSeven, intSeven.clone()));
        byte[] classSeq = au.net.zeus.jgdms.der.DerWriter.writeSequence(List.of(collSeq));
        // The DerException from decodeCollection is surfaced through the GetArg accessor as an
        // InvalidObjectException with the DerException as its cause -- either way the object is
        // NOT constructed (fail-secure). Assert on the whole cause chain for the "duplicate" reason.
        Exception ex = assertThrows(Exception.class,
                () -> ObjectCodec.decode(CollectionRecord.class, schema(tok), classSeq),
                "a set-typed field with duplicate element encodings must be rejected fail-secure");
        assertTrue(messageChainContains(ex, "duplicate"),
                "rejection reason should mention the duplicate; got: " + describe(ex));
    }

    /** True if {@code t} or any cause in its chain has a message containing {@code needle}. */
    private static boolean messageChainContains(Throwable t, String needle) {
        String lower = needle.toLowerCase();
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (m != null && m.toLowerCase().contains(lower)) return true;
        }
        return false;
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    // =========================================================================
    // §11.6 octet-sort mechanics (unit-level, independent of the object codec).
    // =========================================================================

    @Test
    void octetSort_shorterEncodingSortsFirst_whenPrefix() {
        // {0x02,0x01,0x05} (INTEGER 5) vs {0x02,0x02,0x01,0x00} (INTEGER 256): compare byte 0
        // equal (0x02), byte 1: 0x01 < 0x02 -> first sorts before second.
        byte[] five  = new byte[]{0x02, 0x01, 0x05};
        byte[] two56 = new byte[]{0x02, 0x02, 0x01, 0x00};
        assertTrue(CollectionWireTypes.compareOctets(five, two56) < 0);
        assertTrue(CollectionWireTypes.compareOctets(two56, five) > 0);
    }

    @Test
    void octetSort_unsignedByteComparison() {
        // 0x80 (128 unsigned) must sort AFTER 0x7F (127), not before (which a signed compare would do).
        byte[] hi = new byte[]{0x04, 0x01, (byte) 0x80};
        byte[] lo = new byte[]{0x04, 0x01, 0x7F};
        assertTrue(CollectionWireTypes.compareOctets(lo, hi) < 0,
                "octet comparison must be UNSIGNED (0x7F < 0x80)");
    }

    @Test
    void octetSort_prefixShorterFirst() {
        // A genuine proper-prefix pair: the shorter, padded at its tail with 0x00, sorts first
        // because the longer has a non-zero octet (0x42) beyond the common prefix.
        byte[] shortEnc = new byte[]{0x04, 0x01, 0x41};
        byte[] longEnc  = new byte[]{0x04, 0x01, 0x41, 0x42};
        assertTrue(CollectionWireTypes.compareOctets(shortEnc, longEnc) < 0,
                "when one encoding is a proper prefix of the other, the shorter sorts first (0x00 pad)");
        assertTrue(CollectionWireTypes.compareOctets(longEnc, shortEnc) > 0, "and symmetric");
    }

    // =========================================================================
    // No-hashCode guard (memo §6.4(11)) -- source-level assertion.
    // =========================================================================

    @Test
    void collectionCodec_referencesNoHashCode_sourceGuard() throws Exception {
        // The determinism guarantee rests on octet-sort NEVER consulting hashCode/identityHashCode
        // or bucket iteration. Assert the collection-ordering source contains no such reference.
        for (String rel : new String[]{
                "jgdms-der/src/main/java/au/net/zeus/jgdms/der/getarg/CollectionWireTypes.java"}) {
            java.nio.file.Path p = locate(rel);
            if (p == null) continue; // build layout without src on cp -> skip (covered by behaviour tests)
            String code = stripCommentsAndStrings(java.nio.file.Files.readString(p));
            // Look for an actual API USE (a call), not the word in prose: ".hashCode(" or
            // "identityHashCode(" as a token. The canonical order must be a pure function of the
            // element encodings, never a hash (memo §3.0).
            assertFalse(code.contains(".hashCode(") || code.contains("identityHashCode("),
                    "CollectionWireTypes must not CALL hashCode/identityHashCode -- the canonical "
                    + "order is a pure function of element encodings (memo §3.0)");
        }
    }

    /**
     * Crude source scrubber: removes // line comments, block comments, and String/char literals
     * so the no-hashCode guard inspects executable code only (the word "hashCode" appears freely
     * in the class Javadoc). Sufficient for this build-gate assertion.
     */
    private static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i += 2;
            } else if (c == '"') {
                i++;
                while (i < n && src.charAt(i) != '"') { if (src.charAt(i) == '\\') i++; i++; }
                i++;
            } else if (c == '\'') {
                i++;
                while (i < n && src.charAt(i) != '\'') { if (src.charAt(i) == '\\') i++; i++; }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static java.nio.file.Path locate(String relative) {
        java.nio.file.Path dir = java.nio.file.Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            java.nio.file.Path cand = dir.resolve(relative);
            if (java.nio.file.Files.exists(cand)) return cand;
            dir = dir.getParent();
        }
        return null;
    }

    // =========================================================================
    // Fail-closed lock (review-board Q4): the AUTOMATIC schema generator must NOT
    // silently downgrade a plain collection field to a non-canonical encoding. A
    // concrete-collection field throws at schema-gen; an interface-collection field
    // throws at encode (no DER collection serializer is registered). These lock the
    // "opt-in" boundary so a future refactor cannot introduce a non-canonical fallback.
    // =========================================================================

    @Test
    void plainConcreteCollectionField_schemaGen_throwsFailClosed() {
        // SchemaGenerator.toWireType(HashSet.class) has no mapping -> DerException. If a future
        // change made it emit a token it must emit a COLLECTION token (canonical), never fall
        // through to a non-canonical @AtomicSerial/array path; asserting the throw locks that.
        assertThrows(DerException.class,
                () -> SchemaGenerator.toWireType(HashSet.class, PlainCollectionFieldRecord.class),
                "a concrete HashSet field type must NOT auto-map to a non-collection wire type "
                + "(fail-closed: no silent non-canonical fallback)");
    }

    @Test
    void plainInterfaceCollectionField_encode_throwsFailClosed() throws Exception {
        // A Set-typed field auto-maps to "@AtomicSerial"; encoding a HashSet value then fails
        // fast (HashSet is neither @AtomicSerial nor a registered DER serializer). This proves a
        // plain interface-collection field cannot be silently encoded NON-canonically.
        PlainCollectionFieldRecord rec =
                new PlainCollectionFieldRecord(new HashSet<>(List.of(1, 2, 3)));
        SchemaChain.Result chain = SchemaGenerator.generateChain(PlainCollectionFieldRecord.class);
        Exception ex = assertThrows(Exception.class,
                () -> ObjectCodec.encodeHierarchy(rec, chain),
                "a plain Set field holding a HashSet must fail-closed at encode (no DER Set "
                + "serializer registered) rather than emit a non-canonical encoding");
        assertTrue(messageChainContains(ex, "no @AtomicSerial")
                        || messageChainContains(ex, "@AtomicSerial"),
                "encode must reject the non-@AtomicSerial collection value; got: " + describe(ex));
    }

    @Test
    void schemaGenerator_toWireType_neverEmitsCollectionTokenAutomatically() throws Exception {
        // Belt-and-braces: confirm the auto path emits NO collection token for the interface
        // Set/Map/Collection field types (it returns "@AtomicSerial"), so the ONLY way to reach a
        // collection token is the explicit collectionWireType/mapWireType builders. This documents
        // the opt-in boundary the board asked about.
        assertEquals("@AtomicSerial", SchemaGenerator.toWireType(Set.class, CollectionRecord.class));
        assertEquals("@AtomicSerial", SchemaGenerator.toWireType(Map.class, CollectionRecord.class));
        assertEquals("@AtomicSerial",
                SchemaGenerator.toWireType(Collection.class, CollectionRecord.class));
    }

    // =========================================================================
    // Map canonicalise sorts by the KEY encoding, not the whole entry (review-board Q6).
    // Two maps that are .equals but whose entries were built in different orders AND whose
    // values differ in size must still encode byte-identically, ordered by key -- proving the
    // value bytes never participate in the ordering.
    // =========================================================================

    @Test
    void map_canonicalise_ordersByKeyEncoding_notWholeEntry() throws Exception {
        String tok = mapTok(HashMap.class, "int", "java.lang.String");
        // key 1 -> a LARGE value; key 2 -> a SMALL value. Whole-entry octet-sort would put the
        // smaller entry (key 2) first; key-only octet-sort puts key 1 first. The decoded order
        // must be key-ascending (1 then 2), independent of value size.
        Map<Integer, String> m = new HashMap<>();
        m.put(1, "xxxxxxxxxxxxxxxxxxxx"); // 20-char value
        m.put(2, "y");                    // 1-char value
        @SuppressWarnings("unchecked")
        Map<Integer, String> back = (Map<Integer, String>) roundTrip(m, tok);
        assertEquals(List.of(1, 2), new ArrayList<>(back.keySet()),
                "canonicalise map must order entries by KEY encoding (key 1 before key 2), "
                + "independent of value size -- proves value bytes do not participate (§2/§11.6)");

        // And it is deterministic across insertion order (value-equality proxy still holds).
        Map<Integer, String> m2 = new HashMap<>();
        m2.put(2, "y");
        m2.put(1, "xxxxxxxxxxxxxxxxxxxx");
        assertArrayEquals(encode(m, tok), encode(m2, tok),
                "the same map built in a different insertion order must encode byte-identically");
    }

    /** Enum for the EnumSet test. */
    public enum Day { MON, TUE, WED, THU, FRI }
}
