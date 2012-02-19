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

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.TreeMap;
import java.lang.ref.Reference;
import java.util.Comparator;
import java.util.SortedMap;
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
public class ReferenceSortedMapTest {
    private SortedMap<Integer, String> instance;
    // strong references
    private Integer i1, i2, i3, i4, i5;
    private Comparator<Integer> comparator;
    
    public ReferenceSortedMapTest() {
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
         SortedMap<Referrer<Integer>, Referrer<String>> internal 
                = new TreeMap<Referrer<Integer>, Referrer<String>>(ci);
        instance = RC.sortedMap(internal, Ref.WEAK, Ref.STRONG);
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
     * Test serialization
     */
    @Test
    public void serialization() {
        System.out.println("Serialization Test");
        Object result = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            out = new ObjectOutputStream(baos);
            out.writeObject(instance);
            // Unmarshall it
            in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
            result = in.readObject();
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
        } catch (ClassNotFoundException ex){
            ex.printStackTrace(System.out);
        }
        assertEquals(instance, result);
    }
    
}
