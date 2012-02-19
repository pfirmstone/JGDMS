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

package org.apache.river.impl.util;

import java.util.TreeSet;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.SortedMap;
import java.lang.ref.Reference;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentNavigableMap;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class ReferenceConcurrentNavigableMapTest {
    private ConcurrentNavigableMap<Integer, String> instance;
    // strong references
    private Integer i1, i2, i3, i4, i5;
    private Comparator<Integer> comparator;
    public ReferenceConcurrentNavigableMapTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        comparator = new Comparator<Integer>(){

            @Override
            public int compare(Integer o1, Integer o2) {
                return o1.compareTo(o2);
            }
            
        };
        Comparator<Referrer<Integer>> ci = RC.comparator(comparator);
         ConcurrentNavigableMap<Referrer<Integer>, Referrer<String>> internal 
                = new ConcurrentSkipListMap<Referrer<Integer>, Referrer<String>>(ci);
        instance = RC.concurrentNavigableMap(internal, Ref.WEAK, Ref.STRONG);
        i1 = 1;
        i2 = 2;
        i3 = 3;
        i4 = 4;
        i5 = 5;
        instance.put(i1, "1");
        instance.put(i2, "2");
        instance.put(i3, "3");
        instance.put(i4, "4");
        instance.put(i5, "5");
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of comparator method, of class ReferenceSortedMap.
     */
    @Test
    public void testComparator() {
        System.out.println("comparator");
        Comparator<Integer> expResult = comparator;
        Comparator<? super Integer> result = instance.comparator();
        assertEquals(expResult, result);
    }

    /**
     * Test of subMap method, of class ReferenceSortedMap.
     */
    @Test
    public void testSubMap() {
        System.out.println("subMap");
        Integer fromKey = 2;
        Integer toKey = 4;
        SortedMap<Integer, String> expResult = new TreeMap<Integer, String>();
        expResult.put(2, "2");
        expResult.put(3, "3");
        SortedMap<Integer, String> result = instance.subMap(fromKey, toKey);
        assertEquals(expResult, result);
    }

    /**
     * Test of headMap method, of class ReferenceSortedMap.
     */
    @Test
    public void testHeadMap() {
        System.out.println("headMap");
        Integer toKey = 3;
        SortedMap<Integer, String> expResult = new TreeMap<Integer, String>();
        expResult.put(1, "1");
        expResult.put(2, "2");
        SortedMap<Integer, String> result = instance.headMap(toKey);
        assertEquals(expResult, result);
    }

    /**
     * Test of tailMap method, of class ReferenceSortedMap.
     */
    @Test
    public void testTailMap() {
        System.out.println("tailMap");
        Integer fromKey = 3;
        SortedMap<Integer, String> expResult = new TreeMap<Integer, String>();
        expResult.put(3, "3");
        expResult.put(4, "4");
        expResult.put(5, "5");
        SortedMap<Integer, String> result = instance.tailMap(fromKey);
        assertEquals(expResult, result);
    }

    /**
     * Test of firstKey method, of class ReferenceSortedMap.
     */
    @Test
    public void testFirstKey() {
        System.out.println("firstKey");
        Object expResult = 1;
        Object result = instance.firstKey();
        assertEquals(expResult, result);
    }

    /**
     * Test of lastKey method, of class ReferenceSortedMap.
     */
    @Test
    public void testLastKey() {
        System.out.println("lastKey");
        Object expResult = 5;
        Object result = instance.lastKey();
        assertEquals(expResult, result);
    }    
    
        /**
     * Test of lowerEntry method, of class ReferenceNavigableMap.
     */
    @Test
    public void testLowerEntry() {
        System.out.println("lowerEntry");
        Integer key = 2;
        NavigableMap<Integer, String> r = new TreeMap<Integer, String>();
        r.put(1, "1");
        Entry<Integer, String> expResult = r.pollFirstEntry();
        Entry<Integer, String> result = instance.lowerEntry(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of lowerKey method, of class ReferenceNavigableMap.
     */
    @Test
    public void testLowerKey() {
        System.out.println("lowerKey");
        Integer key = 3;
        Object expResult = 2;
        Object result = instance.lowerKey(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of floorEntry method, of class ReferenceNavigableMap.
     */
    @Test
    public void testFloorEntry() {
        System.out.println("floorEntry");
        Integer key = 4;
        NavigableMap<Integer, String> r = new TreeMap<Integer, String>();
        r.put(4, "4");
        Entry<Integer, String> expResult = r.pollFirstEntry();
        Entry<Integer, String> result = instance.floorEntry(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of floorKey method, of class ReferenceNavigableMap.
     */
    @Test
    public void testFloorKey() {
        System.out.println("floorKey");
        Integer key = 3;
        Object expResult = 3;
        Object result = instance.floorKey(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of ceilingEntry method, of class ReferenceNavigableMap.
     */
    @Test
    public void testCeilingEntry() {
        System.out.println("ceilingEntry");
        Integer key = 3;
        NavigableMap<Integer, String> r = new TreeMap<Integer, String>();
        r.put(3, "3");
        Entry<Integer, String> expResult = r.pollFirstEntry();
        Entry<Integer, String> result = instance.ceilingEntry(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of ceilingKey method, of class ReferenceNavigableMap.
     */
    @Test
    public void testCeilingKey() {
        System.out.println("ceilingKey");
        Integer key = 2;
        Object expResult = 2;
        Object result = instance.ceilingKey(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of higherEntry method, of class ReferenceNavigableMap.
     */
    @Test
    public void testHigherEntry() {
        System.out.println("higherEntry");
        Integer key = 4;
        NavigableMap<Integer, String> r = new TreeMap<Integer, String>();
        r.put(5, "5");
        Entry<Integer, String> expResult = r.pollFirstEntry();
        Entry<Integer, String> result = instance.higherEntry(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of higherKey method, of class ReferenceNavigableMap.
     */
    @Test
    public void testHigherKey() {
        System.out.println("higherKey");
        Integer key = 3;
        Object expResult = 4;
        Object result = instance.higherKey(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of firstEntry method, of class ReferenceNavigableMap.
     */
    @Test
    public void testFirstEntry() {
        System.out.println("firstEntry");
        NavigableMap<Integer, String> r = new TreeMap<Integer, String>();
        r.put(1, "1");
        Entry<Integer, String> expResult = r.pollFirstEntry();
        Entry<Integer, String> result = instance.firstEntry();
        assertEquals(expResult, result);
    }

    /**
     * Test of lastEntry method, of class ReferenceNavigableMap.
     */
    @Test
    public void testLastEntry() {
        System.out.println("lastEntry");
        NavigableMap<Integer, String> r = new TreeMap<Integer, String>();
        r.put(5, "5");
        Entry<Integer, String> expResult = r.pollFirstEntry();
        Entry<Integer, String> result = instance.lastEntry();
        assertEquals(expResult, result);
    }

    /**
     * Test of pollFirstEntry method, of class ReferenceNavigableMap.
     */
    @Test
    public void testPollFirstEntry() {
        System.out.println("pollFirstEntry");
        NavigableMap<Integer, String> r = new TreeMap<Integer, String>();
        r.put(1, "1");
        Entry<Integer, String> expResult = r.pollFirstEntry();
        Entry<Integer, String> result = instance.pollFirstEntry();
        instance.put(1, "1"); // For other tests.
        assertEquals(expResult, result);
    }

    /**
     * Test of pollLastEntry method, of class ReferenceNavigableMap.
     */
    @Test
    public void testPollLastEntry() {
        System.out.println("pollLastEntry");
        NavigableMap<Integer, String> r = new TreeMap<Integer, String>();
        r.put(5, "5");
        Entry<Integer, String> expResult = r.pollFirstEntry();
        Entry<Integer, String> result = instance.pollLastEntry();
        instance.put(5, "5"); // For other tests.
        assertEquals(expResult, result);
    }

    /**
     * Test of descendingMap method, of class ReferenceNavigableMap.
     */
    @Test
    public void testDescendingMap() {
        System.out.println("descendingMap");
        NavigableMap<Integer, String> result = instance.descendingMap();
        assertTrue(result.firstKey().equals(5));
        assertTrue(result.lastKey().equals(1));
    }

    /**
     * Test of navigableKeySet method, of class ReferenceNavigableMap.
     */
    @Test
    public void testNavigableKeySet() {
        System.out.println("navigableKeySet");
        NavigableSet<Integer> expResult = new TreeSet<Integer>();
        expResult.add(1);
        expResult.add(2);
        expResult.add(3);
        expResult.add(4);
        expResult.add(5);
        NavigableSet<Integer> result = instance.navigableKeySet();
        assertEquals(expResult, result);
    }

    /**
     * Test of descendingKeySet method, of class ReferenceNavigableMap.
     */
    @Test
    public void testDescendingKeySet() {
        System.out.println("descendingKeySet");
        NavigableSet<Integer> result = instance.descendingKeySet();
        assertTrue(result.first().equals(5));
        assertTrue(result.last().equals(1));
    }

}
