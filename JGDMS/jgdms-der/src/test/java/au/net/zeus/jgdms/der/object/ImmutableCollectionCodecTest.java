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

import au.net.zeus.jgdms.der.object.fixtures.CollectionRecord;
import au.net.zeus.jgdms.der.object.fixtures.PlainMapFieldRecord;
import au.net.zeus.jgdms.der.object.fixtures.PlainSetFieldRecord;
import au.net.zeus.jgdms.der.object.fixtures.SortedMapFieldRecord;
import au.net.zeus.jgdms.der.object.fixtures.SortedSetFieldRecord;
import au.net.zeus.jgdms.der.object.fixtures.SpyElement;
import au.net.zeus.jgdms.der.object.immutable.ImmutableList;
import au.net.zeus.jgdms.der.object.immutable.ImmutableMap;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSet;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSortedMap;
import au.net.zeus.jgdms.der.object.immutable.ImmutableSortedSet;
import au.net.zeus.jgdms.der.schema.AtomicSerialFieldDef;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the immutable, array-backed collection/map wrappers ({@code
 * au.net.zeus.jgdms.der.object.immutable}) that {@link ObjectCodec#decodeCollection} now returns
 * for {@code set:}/{@code bag:}/{@code orderedset:}/{@code list:}/{@code map:}/{@code
 * orderedmap:}-typed {@code @AtomicSerial} fields, per {@code AtomicSerial.GetArg}'s documented
 * contract ("Instances of java.util.Collection will be replaced in the stream by a safe limited
 * functionality immutable Collection instance").
 *
 * <p>Covers: (a) genuine immutability, (b) ZERO {@code hashCode}/{@code equals}/{@code
 * compareTo} calls on elements during construction (the most important property here), (c)
 * declared-field-type-driven shape selection ({@code SortedSet}/{@code SortedMap} vs plain), and
 * (d) that {@code CollectionOrderingTest}'s existing round-trip assertions still pass with the new
 * concrete types (verified separately by that unmodified test suite; this class additionally
 * checks the concrete TYPE returned for each discipline).
 */
class ImmutableCollectionCodecTest {

    // -------------------------------------------------------------------------
    // Helpers (mirrors CollectionOrderingTest's pattern: hand-built single-field schema).
    // -------------------------------------------------------------------------

    private static AtomicSerialSchemaRecord schema(Class<?> declaringClass, String fieldName,
                                                     String wireType) {
        return new AtomicSerialSchemaRecord(
                declaringClass.getName(),
                (byte[]) null,
                List.of(new AtomicSerialFieldDef(fieldName, wireType)));
    }

    private static Object decodeColl(Object value, String collToken) throws Exception {
        byte[] bytes = ObjectCodec.encode(new CollectionRecord(value), CollectionRecord.class,
                schema(CollectionRecord.class, "coll", collToken));
        return ObjectCodec.decode(CollectionRecord.class,
                schema(CollectionRecord.class, "coll", collToken), bytes).getColl();
    }

    private static String setTok(Class<?> declared, String elemWT) {
        return SchemaGenerator.collectionWireType(declared, elemWT);
    }

    private static String mapTok(Class<?> declared, String keyWT, String valWT) {
        return SchemaGenerator.mapWireType(declared, keyWT, valWT);
    }

    // =========================================================================
    // (d) Concrete wrapper types returned per discipline.
    // =========================================================================

    @Test
    void set_decodesToImmutableSet() throws Exception {
        Object back = decodeColl(new HashSet<>(List.of(1, 2, 3)), setTok(HashSet.class, "int"));
        assertInstanceOf(ImmutableSet.class, back);
        assertEquals(Set.of(1, 2, 3), back);
    }

    @Test
    void orderedSetDeclaredPlain_decodesToImmutableSet_notSorted() throws Exception {
        // LinkedHashSet declared -> orderedset:, no SortedSet-declared field in play (the
        // declaringClass here is CollectionRecord, whose "coll" field is declared Object) ->
        // plain ImmutableSet, never ImmutableSortedSet.
        Object back = decodeColl(new LinkedHashSet<>(List.of(3, 1, 2)),
                setTok(LinkedHashSet.class, "int"));
        assertInstanceOf(ImmutableSet.class, back);
        assertFalse(back instanceof SortedSet, "a field with no declared SortedSet type must "
                + "never be wrapped as a SortedSet");
        assertEquals(List.of(3, 1, 2), new ArrayList<>((Collection<?>) back),
                "insertion order preserved");
    }

    @Test
    void list_decodesToImmutableList() throws Exception {
        Object back = decodeColl(new ArrayList<>(List.of(1, 2, 3)), setTok(ArrayList.class, "int"));
        assertInstanceOf(ImmutableList.class, back);
        assertEquals(List.of(1, 2, 3), back);
    }

    @Test
    void bag_decodesToImmutableList_notSet() throws Exception {
        // bag: (CANONICALISE_MULTISET) is documented as "reconstructed as a List" -- duplicates
        // must survive, which a Set-shaped wrapper could not represent faithfully.
        String tok = setTok(PriorityQueue.class, "int");
        assertTrue(tok.startsWith("bag:"));
        Object back = decodeColl(new PriorityQueue<>(List.of(5, 1, 5, 3)), tok);
        assertInstanceOf(ImmutableList.class, back,
                "a bag: field must decode to a List-shaped wrapper (duplicates retained)");
        List<Integer> sorted = new ArrayList<>((Collection<Integer>) back);
        sorted.sort(Integer::compare);
        assertEquals(List.of(1, 3, 5, 5), sorted, "duplicate 5 must survive in the List");
    }

    @Test
    void map_decodesToImmutableMap() throws Exception {
        Map<Integer, String> m = new java.util.HashMap<>();
        m.put(1, "a"); m.put(2, "b");
        Object back = decodeColl(m, mapTok(java.util.HashMap.class, "int", "java.lang.String"));
        assertInstanceOf(ImmutableMap.class, back);
        assertEquals(m, back);
    }

    @Test
    void orderedMapDeclaredPlain_decodesToImmutableMap_notSorted() throws Exception {
        TreeMap<Integer, String> m = new TreeMap<>();
        m.put(3, "c"); m.put(1, "a"); m.put(2, "b");
        // Wire-type derived from TreeMap.class (-> orderedmap:), but the RECEIVING declared field
        // (CollectionRecord.coll, declared Object) is not itself a SortedMap -> plain ImmutableMap.
        Object back = decodeColl(m, mapTok(TreeMap.class, "int", "java.lang.String"));
        assertInstanceOf(ImmutableMap.class, back);
        assertFalse(back instanceof SortedMap);
        assertEquals(m, back);
    }

    // =========================================================================
    // (a) Immutability: mutating methods throw (or are absent, per the verified base-class
    // behaviour), on every wrapper shape.
    // =========================================================================

    @Test
    void immutableList_mutatorsThrow() {
        ImmutableList<Integer> l = new ImmutableList<>(List.of(1, 2, 3));
        assertThrows(UnsupportedOperationException.class, () -> l.add(4));
        assertThrows(UnsupportedOperationException.class, () -> l.set(0, 9));
        assertThrows(UnsupportedOperationException.class, () -> l.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> l.remove(Integer.valueOf(1)));
        Iterator<Integer> it = l.iterator();
        assertTrue(it.hasNext());
        it.next();
        assertThrows(UnsupportedOperationException.class, it::remove);
        assertEquals(List.of(1, 2, 3), l, "the list must be unchanged after every rejected mutation");
    }

    @Test
    void immutableSet_mutatorsThrow() {
        ImmutableSet<Integer> s = new ImmutableSet<>(List.of(1, 2, 3));
        assertThrows(UnsupportedOperationException.class, () -> s.add(4));
        assertThrows(UnsupportedOperationException.class, () -> s.remove(1));
        Iterator<Integer> it = s.iterator();
        assertTrue(it.hasNext());
        it.next();
        assertThrows(UnsupportedOperationException.class, it::remove);
        assertEquals(Set.of(1, 2, 3), s);
    }

    @Test
    void immutableSortedSet_mutatorsThrow() {
        ImmutableSortedSet<Integer> s = new ImmutableSortedSet<>(List.of(1, 2, 3));
        assertThrows(UnsupportedOperationException.class, () -> s.add(4));
        assertThrows(UnsupportedOperationException.class, () -> s.remove(1));
        Iterator<Integer> it = s.iterator();
        assertTrue(it.hasNext());
        it.next();
        assertThrows(UnsupportedOperationException.class, it::remove);
    }

    @Test
    void immutableMap_mutatorsThrow() {
        List<Map.Entry<?, ?>> entries = List.of(
                new java.util.AbstractMap.SimpleImmutableEntry<>(1, "a"),
                new java.util.AbstractMap.SimpleImmutableEntry<>(2, "b"));
        ImmutableMap<Integer, String> m = new ImmutableMap<>(entries);
        assertThrows(UnsupportedOperationException.class, () -> m.put(3, "c"));
        assertThrows(UnsupportedOperationException.class, () -> m.remove(1));
        Iterator<Map.Entry<Integer, String>> it = m.entrySet().iterator();
        assertTrue(it.hasNext());
        it.next();
        assertThrows(UnsupportedOperationException.class, it::remove);
    }

    @Test
    void immutableSortedMap_mutatorsThrow() {
        List<Map.Entry<?, ?>> entries = List.of(
                new java.util.AbstractMap.SimpleImmutableEntry<>(1, "a"),
                new java.util.AbstractMap.SimpleImmutableEntry<>(2, "b"));
        ImmutableSortedMap<Integer, String> m = new ImmutableSortedMap<>(entries);
        assertThrows(UnsupportedOperationException.class, () -> m.put(3, "c"));
        assertThrows(UnsupportedOperationException.class, () -> m.remove(1));
    }

    // =========================================================================
    // (b) THE MOST IMPORTANT TEST: zero hashCode/equals/compareTo calls on elements during
    // construction of the decoded collection, proven directly with an instrumented element type.
    // =========================================================================

    @Test
    void decodeSet_invokesNoElementMethods_duringConstruction() throws Exception {
        Set<SpyElement> input = new HashSet<>(List.of(
                new SpyElement(3), new SpyElement(1), new SpyElement(2)));
        String tok = setTok(HashSet.class, "@AtomicSerial");
        byte[] bytes = ObjectCodec.encode(new CollectionRecord(input), CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok));

        SpyElement.resetCounters();
        Object back = ObjectCodec.decode(CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok), bytes).getColl();

        assertEquals(0, SpyElement.HASHCODE_CALLS.get(), "hashCode() must not be called during decode");
        assertEquals(0, SpyElement.EQUALS_CALLS.get(), "equals() must not be called during decode");
        assertEquals(0, SpyElement.COMPARETO_CALLS.get(), "compareTo() must not be called during decode");
        assertInstanceOf(ImmutableSet.class, back);
        assertEquals(3, ((Collection<?>) back).size());
    }

    @Test
    void decodeOrderedSet_invokesNoElementMethods_duringConstruction() throws Exception {
        LinkedHashSet<SpyElement> input = new LinkedHashSet<>(List.of(
                new SpyElement(3), new SpyElement(1), new SpyElement(2)));
        String tok = setTok(LinkedHashSet.class, "@AtomicSerial");
        byte[] bytes = ObjectCodec.encode(new CollectionRecord(input), CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok));

        SpyElement.resetCounters();
        Object back = ObjectCodec.decode(CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok), bytes).getColl();

        assertEquals(0, SpyElement.HASHCODE_CALLS.get());
        assertEquals(0, SpyElement.EQUALS_CALLS.get());
        assertEquals(0, SpyElement.COMPARETO_CALLS.get());
        assertInstanceOf(ImmutableSet.class, back);
    }

    @Test
    void decodeList_invokesNoElementMethods_duringConstruction() throws Exception {
        List<SpyElement> input = List.of(new SpyElement(3), new SpyElement(1), new SpyElement(2));
        String tok = setTok(ArrayList.class, "@AtomicSerial");
        byte[] bytes = ObjectCodec.encode(new CollectionRecord(input), CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok));

        SpyElement.resetCounters();
        Object back = ObjectCodec.decode(CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok), bytes).getColl();

        assertEquals(0, SpyElement.HASHCODE_CALLS.get());
        assertEquals(0, SpyElement.EQUALS_CALLS.get());
        assertEquals(0, SpyElement.COMPARETO_CALLS.get());
        assertInstanceOf(ImmutableList.class, back);
    }

    @Test
    void decodeBag_invokesNoElementMethods_duringConstruction() throws Exception {
        PriorityQueue<SpyElement> input = new PriorityQueue<>(List.of(
                new SpyElement(5), new SpyElement(1), new SpyElement(5)));
        String tok = setTok(PriorityQueue.class, "@AtomicSerial");
        assertTrue(tok.startsWith("bag:"));
        byte[] bytes = ObjectCodec.encode(new CollectionRecord(input), CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok));

        SpyElement.resetCounters();
        Object back = ObjectCodec.decode(CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok), bytes).getColl();

        assertEquals(0, SpyElement.HASHCODE_CALLS.get());
        assertEquals(0, SpyElement.EQUALS_CALLS.get());
        assertEquals(0, SpyElement.COMPARETO_CALLS.get());
        assertInstanceOf(ImmutableList.class, back);
        assertEquals(3, ((Collection<?>) back).size());
    }

    @Test
    void decodeMap_invokesNoKeyMethods_duringConstruction() throws Exception {
        Map<SpyElement, String> input = new java.util.HashMap<>();
        input.put(new SpyElement(1), "a");
        input.put(new SpyElement(2), "b");
        String tok = mapTok(java.util.HashMap.class, "@AtomicSerial", "java.lang.String");
        byte[] bytes = ObjectCodec.encode(new CollectionRecord(input), CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok));

        SpyElement.resetCounters();
        Object back = ObjectCodec.decode(CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok), bytes).getColl();

        assertEquals(0, SpyElement.HASHCODE_CALLS.get(), "key hashCode() must not be called during decode");
        assertEquals(0, SpyElement.EQUALS_CALLS.get(), "key equals() must not be called during decode");
        assertEquals(0, SpyElement.COMPARETO_CALLS.get());
        assertInstanceOf(ImmutableMap.class, back);
        assertEquals(2, ((Map<?, ?>) back).size());
    }

    @Test
    void decodeOrderedMap_invokesNoKeyMethods_duringConstruction() throws Exception {
        Map<SpyElement, String> input = new java.util.LinkedHashMap<>();
        input.put(new SpyElement(2), "b");
        input.put(new SpyElement(1), "a");
        String tok = mapTok(java.util.LinkedHashMap.class, "@AtomicSerial", "java.lang.String");
        byte[] bytes = ObjectCodec.encode(new CollectionRecord(input), CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok));

        SpyElement.resetCounters();
        Object back = ObjectCodec.decode(CollectionRecord.class,
                schema(CollectionRecord.class, "coll", tok), bytes).getColl();

        assertEquals(0, SpyElement.HASHCODE_CALLS.get());
        assertEquals(0, SpyElement.EQUALS_CALLS.get());
        assertEquals(0, SpyElement.COMPARETO_CALLS.get());
        assertInstanceOf(ImmutableMap.class, back);
    }

    /** Same as the direct-decode tests above, but at the wrapper-class level (no codec involved):
     *  constructing an {@link ImmutableSet}/{@link ImmutableSortedSet}/{@link ImmutableList} from
     *  a {@code List} of already-built spies must not touch any of the three instrumented methods. */
    @Test
    void wrapperConstruction_invokesNoElementMethods_directly() {
        List<SpyElement> spies = List.of(new SpyElement(1), new SpyElement(2), new SpyElement(3));
        SpyElement.resetCounters();
        new ImmutableList<>(spies);
        new ImmutableSet<>(spies);
        new ImmutableSortedSet<>(spies);
        assertEquals(0, SpyElement.HASHCODE_CALLS.get());
        assertEquals(0, SpyElement.EQUALS_CALLS.get());
        assertEquals(0, SpyElement.COMPARETO_CALLS.get());

        List<Map.Entry<?, ?>> entries = List.of(
                new java.util.AbstractMap.SimpleImmutableEntry<>(spies.get(0), "a"),
                new java.util.AbstractMap.SimpleImmutableEntry<>(spies.get(1), "b"));
        SpyElement.resetCounters();
        new ImmutableMap<>(entries);
        new ImmutableSortedMap<>(entries);
        assertEquals(0, SpyElement.HASHCODE_CALLS.get());
        assertEquals(0, SpyElement.EQUALS_CALLS.get());
        assertEquals(0, SpyElement.COMPARETO_CALLS.get());
    }

    // =========================================================================
    // (c) Declared-field-type-driven shape selection.
    // =========================================================================

    @Test
    void sortedSetDeclaredField_decodesToSortedSetShape() throws Exception {
        String tok = "orderedset:int";
        SortedSet<Integer> original = new java.util.TreeSet<>(List.of(3, 1, 2));
        byte[] bytes = ObjectCodec.encode(new SortedSetFieldRecord(original), SortedSetFieldRecord.class,
                schema(SortedSetFieldRecord.class, "items", tok));
        SortedSetFieldRecord decoded = ObjectCodec.decode(SortedSetFieldRecord.class,
                schema(SortedSetFieldRecord.class, "items", tok), bytes);

        assertInstanceOf(ImmutableSortedSet.class, decoded.getItems(),
                "a field DECLARED SortedSet must decode to the SortedSet-shaped wrapper");
        assertEquals(List.of(1, 2, 3), new ArrayList<>(decoded.getItems()));
        assertEquals(1, decoded.getItems().first());
        assertEquals(3, decoded.getItems().last());
        assertNull(decoded.getItems().comparator());
    }

    @Test
    void plainSetDeclaredField_decodesToPlainSetShape() throws Exception {
        String tok = "orderedset:int"; // SAME wire token as the SortedSet test above
        Set<Integer> original = new LinkedHashSet<>(List.of(3, 1, 2));
        byte[] bytes = ObjectCodec.encode(new PlainSetFieldRecord(original), PlainSetFieldRecord.class,
                schema(PlainSetFieldRecord.class, "items", tok));
        PlainSetFieldRecord decoded = ObjectCodec.decode(PlainSetFieldRecord.class,
                schema(PlainSetFieldRecord.class, "items", tok), bytes);

        assertFalse(decoded.getItems() instanceof SortedSet,
                "a field DECLARED plain Set must NOT decode to a SortedSet -- same wire token, "
                + "different declared Java type, different wrapper shape");
        assertInstanceOf(ImmutableSet.class, decoded.getItems());
        assertEquals(List.of(3, 1, 2), new ArrayList<>(decoded.getItems()),
                "encounter (insertion) order still preserved");
    }

    @Test
    void sortedMapDeclaredField_decodesToSortedMapShape() throws Exception {
        String tok = "orderedmap:{int}{java.lang.String}";
        SortedMap<Integer, String> original = new TreeMap<>();
        original.put(3, "c"); original.put(1, "a"); original.put(2, "b");
        byte[] bytes = ObjectCodec.encode(new SortedMapFieldRecord(original), SortedMapFieldRecord.class,
                schema(SortedMapFieldRecord.class, "items", tok));
        SortedMapFieldRecord decoded = ObjectCodec.decode(SortedMapFieldRecord.class,
                schema(SortedMapFieldRecord.class, "items", tok), bytes);

        assertInstanceOf(ImmutableSortedMap.class, decoded.getItems(),
                "a field DECLARED SortedMap must decode to the SortedMap-shaped wrapper");
        assertEquals(List.of(1, 2, 3), new ArrayList<>(decoded.getItems().keySet()));
        assertEquals(1, decoded.getItems().firstKey());
        assertEquals(3, decoded.getItems().lastKey());
        assertNull(decoded.getItems().comparator());
    }

    @Test
    void plainMapDeclaredField_decodesToPlainMapShape() throws Exception {
        String tok = "orderedmap:{int}{java.lang.String}"; // SAME wire token as the SortedMap test
        Map<Integer, String> original = new java.util.LinkedHashMap<>();
        original.put(3, "c"); original.put(1, "a"); original.put(2, "b");
        byte[] bytes = ObjectCodec.encode(new PlainMapFieldRecord(original), PlainMapFieldRecord.class,
                schema(PlainMapFieldRecord.class, "items", tok));
        PlainMapFieldRecord decoded = ObjectCodec.decode(PlainMapFieldRecord.class,
                schema(PlainMapFieldRecord.class, "items", tok), bytes);

        assertFalse(decoded.getItems() instanceof SortedMap,
                "a field DECLARED plain Map must NOT decode to a SortedMap");
        assertInstanceOf(ImmutableMap.class, decoded.getItems());
        assertEquals(List.of(3, 1, 2), new ArrayList<>(decoded.getItems().keySet()),
                "encounter (insertion) order still preserved");
    }

    // =========================================================================
    // SortedSet / SortedMap view correctness (headSet/tailSet/subSet, headMap/tailMap/subMap),
    // exercised as ordinary POST-construction usage (may use compareTo() at call time).
    // =========================================================================

    @Test
    void immutableSortedSet_views() {
        ImmutableSortedSet<Integer> s = new ImmutableSortedSet<>(List.of(1, 2, 3, 4, 5));
        assertEquals(List.of(1, 2), new ArrayList<>(s.headSet(3)));
        assertEquals(List.of(3, 4, 5), new ArrayList<>(s.tailSet(3)));
        assertEquals(List.of(2, 3), new ArrayList<>(s.subSet(2, 4)));
        assertThrows(IllegalArgumentException.class, () -> s.subSet(4, 2));
    }

    @Test
    void immutableSortedMap_views() {
        List<Map.Entry<?, ?>> entries = List.of(
                new java.util.AbstractMap.SimpleImmutableEntry<>(1, "a"),
                new java.util.AbstractMap.SimpleImmutableEntry<>(2, "b"),
                new java.util.AbstractMap.SimpleImmutableEntry<>(3, "c"));
        ImmutableSortedMap<Integer, String> m = new ImmutableSortedMap<>(entries);
        assertEquals(List.of(1), new ArrayList<>(m.headMap(2).keySet()));
        assertEquals(List.of(2, 3), new ArrayList<>(m.tailMap(2).keySet()));
        assertEquals(List.of(2), new ArrayList<>(m.subMap(2, 3).keySet()));
        assertThrows(IllegalArgumentException.class, () -> m.subMap(3, 1));
    }
}
