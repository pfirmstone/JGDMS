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
import tests.support.MutableMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Firmstone.
 */
public class ReferenceMapTest {
    private Map<Integer, String> instance;
    // strong references
    private Integer i1, i2, i3, i4, i5;
    private String s1, s2, s3, s4, s5;
    
    public ReferenceMapTest() {
    }

    @Before
    public void setUp() {
        Map<Referrer<Integer>, Referrer<String>> internal 
                = new HashMap<Referrer<Integer>, Referrer<String>>(5);
        instance = RC.map(internal, Ref.WEAK, Ref.STRONG);
        i1 = 1;
        i2 = 2;
        i3 = 3;
        i4 = 4;
        i5 = 5;
        s1 = "One";
        s2 = "Two";
        s3 = "Three";
        s4 = "Four";
        s5 = "Five";
        instance.put(i1, s1);
        instance.put(i2, s2);
        instance.put(i3, s3);
        instance.put(i4, s4);
        instance.put(i5, s5);
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testEquals(){
        Map<Integer, String> m1 = new HashMap<Integer, String>();
        m1.put(i1, s1);
        m1.put(i2, s2);
        m1.put(i3, s3);
        Map<Integer, String> m2 = RC.map(
            new TreeMap<Referrer<Integer>, Referrer<String>>(),
            Ref.SOFT, Ref.SOFT
        );
        m2.put(i1, s1);
        m2.put(i2, s2);
        m2.put(i3, s3);
        assertTrue(m1.equals(m2));
        assertTrue(m2.equals(m1));
        assertEquals( m1.hashCode(), m2.hashCode());
    }
    
    @Test
    public void testEntrySetEquals(){
        Map<Integer, String> m1 = new HashMap<Integer, String>();
        m1.put(i1, s1);
        m1.put(i2, s2);
        m1.put(i3, s3);
        Map<Integer, String> m2 = RC.map(
            new TreeMap<Referrer<Integer>, Referrer<String>>(),
            Ref.SOFT, Ref.SOFT
        );
        m2.put(i1, s1);
        m2.put(i2, s2);
        m2.put(i3, s3);
        Set<Entry<Integer, String>> set1 = m1.entrySet();
        Set<Entry<Integer, String>> set2 = m2.entrySet();
        assertTrue(set1.equals(set2));
        assertTrue(set2.equals(set1));
        assertEquals(set1.hashCode(), set2.hashCode());
    }
    
    @Test 
    public void testEntrySetRemoveAll(){
        Map<Integer, String> m1 = new HashMap<Integer, String>();
        m1.put(i1, s1);
        m1.put(i2, s2);
        m1.put(i3, s3);
        Map<Integer, String> m2 = RC.map(
            new TreeMap<Referrer<Integer>, Referrer<String>>(),
            Ref.SOFT, Ref.SOFT
        );
        m2.put(i1, s1);
        m2.put(i2, s2);
        m2.put(i3, s3);
        Set<Entry<Integer, String>> set1 = m1.entrySet();
        Set<Entry<Integer, String>> set2 = m2.entrySet();
        set2.removeAll(set1);
        assertTrue(set2.isEmpty());
    }
    
    @Test
    public void testEntrySetRetainAll(){
        Map<Integer, String> m1 = new HashMap<Integer, String>();
        m1.put(i1, s1);
        m1.put(i2, s2);
        m1.put(i3, s3);
        Map<Integer, String> m2 = RC.map(
            new TreeMap<Referrer<Integer>, Referrer<String>>(),
            Ref.SOFT, Ref.SOFT
        );
        m2.put(i1, s1);
        m2.put(i2, s2);
        m2.put(i3, s3);
        Set<Entry<Integer, String>> set1 = m1.entrySet();
        Set<Entry<Integer, String>> set2 = m2.entrySet();
        set2.retainAll(set1);
        assertFalse(set2.isEmpty());
    }
    
    @Test
    public void testEntryContainsAll(){
        Map<Integer, String> m1 = new HashMap<Integer, String>();
        m1.put(i1, s1);
        m1.put(i2, s2);
        m1.put(i3, s3);
        Map<Integer, String> m2 = RC.map(
            new TreeMap<Referrer<Integer>, Referrer<String>>(),
            Ref.SOFT, Ref.SOFT
        );
        m2.put(i1, s1);
        m2.put(i2, s2);
        m2.put(i3, s3);
        Map<Integer, String> m3 = new HashMap<Integer, String>();
        m3.put(i1, s1);
        m3.put(10, "Ten");
        Set<Entry<Integer, String>> set1 = m1.entrySet();
        Set<Entry<Integer, String>> set2 = m2.entrySet();
        Set<Entry<Integer, String>> set3 = m3.entrySet();
        assertTrue(set2.containsAll(set1));
        assertFalse(set2.containsAll(set3));
    }

   @Test
    public void testEntrySetRemoveContains(){
        Map<Integer, String> m1 = new HashMap<Integer, String>();
        m1.put(i1, s1);
        m1.put(i2, s2);
        m1.put(i3, s3);
        Map<Integer, String> m2 = RC.map(
            new TreeMap<Referrer<Integer>, Referrer<String>>(),
            Ref.SOFT, Ref.SOFT
        );
        m2.put(i1, s1);
        m2.put(i2, s2);
        m2.put(i3, s3);
        Set<Entry<Integer, String>> set1 = m1.entrySet();
        Set<Entry<Integer, String>> set2 = m2.entrySet();
        assertTrue(set2.containsAll(set1));
        Iterator<Entry<Integer, String>> it1 = set1.iterator();
        while (it1.hasNext()){
            Entry<Integer, String> e = it1.next();
            set2.remove(e);
            assertFalse(set2.contains(e));
        }
        assertTrue(set2.isEmpty());
    }
    
   @Test
   public void testEntrySetAdd(){
       Map<Integer, String> m1 = new HashMap<Integer, String>();
        m1.put(i1, s1);
        m1.put(i2, s2);
        m1.put(i3, s3);
        Map<Integer, String> m2 = RC.map(
            new MutableMap<Referrer<Integer>, Referrer<String>>(),
            Ref.SOFT, Ref.SOFT
        );
        Set<Entry<Integer, String>> set1 = m1.entrySet();
        Set<Entry<Integer, String>> set2 = m2.entrySet();
        assertTrue(set2.isEmpty());
        Iterator<Entry<Integer, String>> it1 = set1.iterator();
        while (it1.hasNext()){
            Entry<Integer, String> e = it1.next();
            set2.add(e);
        }
        assertTrue(set2.containsAll(set1));
   }
   
    /**
     * Test of containsKey method, of class ReferenceMap.
     */
    @Test
    public void testContainsKey() {
        System.out.println("containsKey");
        instance.put(i1, s1);
        Integer key = i1;
        boolean expResult = true;
        boolean result = instance.containsKey(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of containsValue method, of class ReferenceMap.
     */
    @Test
    public void testContainsValue() {
        System.out.println("containsValue");
        Object value = "One";
        boolean expResult = true;
        boolean result = instance.containsValue(value);
        assertEquals(expResult, result);
    }

    /**
     * Test of entrySet method, of class ReferenceMap.
     * Tests the entry set iterator and the keySet and values methods too.
     */
    @Test
    public void testEntrySet() {
        System.out.println("entrySet");
        List<Integer> keys = new ArrayList<Integer>(5);
        List<String> values = new ArrayList<String>(5);
        Set<Entry<Integer, String>> result = instance.entrySet();
        Iterator<Entry<Integer, String>> i = result.iterator();
        while (i.hasNext()){
            Entry<Integer, String> e = i.next();
            keys.add(e.getKey());
            values.add(e.getValue());
        }
        Collection<Integer> k = instance.keySet();
        Collection<String> v = instance.values();
        System.out.println(k);
        System.out.println(v);
        assertTrue(k.containsAll(keys));
        assertTrue(v.containsAll(values));
    }
    
    @Test
    public void testEntrySetMutation(){
        instance.put(i1, s1);
        instance.put(i2, s2);
        instance.put(i3, s3);
        instance.put(i4, s4);
        instance.put(i5, s5);
        Set<Entry<Integer, String>> result = instance.entrySet();
        Iterator<Entry<Integer, String>> i = result.iterator();
        while (i.hasNext()){
            Entry<Integer, String> e = i.next();
            if (e.getKey().equals(i1)){
                e.setValue("Big One");
            }
        }
        assertTrue( instance.get(i1).equals("Big One"));
    }

    /**
     * Test of get method, of class ReferenceMap.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        Object key = 1;
        Object expResult = "One";
        Object result = instance.get(key);
        assertEquals(expResult, result);
    }

    /**
     * Test of isEmpty method, of class ReferenceMap.
     */
    @Test
    public void testIsEmpty() {
        System.out.println("isEmpty");
        boolean expResult = false;
        boolean result = instance.isEmpty();
        assertEquals(expResult, result);
    }

    /**
     * Test of put method, of class ReferenceMap.
     */
    @Test
    public void testPut() {
        System.out.println("put");
        Integer key = 5;
        String value = "5";
        Object expResult = "Five";
        Object result = instance.put(key, value);
        assertEquals(expResult, result);
    }

    /**
     * Test of putAll method, of class ReferenceMap.
     */
    @Test
    public void testPutAll() {
        System.out.println("putAll");
        Map<Integer,String> m = new HashMap<Integer, String>();
        Integer i6 = 6, i7 = 7, i8 = 8;
        m.put(6,"Six");
        m.put(7, "Seven");
        m.put(8, "Eight");
        instance.putAll(m);
        assertTrue( instance.containsKey(8));
        assertTrue( instance.containsValue("Seven"));
    }

    /**
     * Test of remove method, of class ReferenceMap.
     */
    @Test
    public void testRemove() {
        System.out.println("remove");
        Integer key = 4;
        Object expResult = "Four";
        String result = instance.remove(key);
        assertEquals(expResult, result);
        assertFalse(instance.containsKey(4));
        assertFalse(instance.containsValue("Four"));
    }

    /**
     * Test of size method, of class ReferenceMap.
     */
    @Test
    public void testSize() {
        System.out.println("size");
        Collection<Integer> keys = instance.keySet();
        int expResult = keys.size();
        int result = instance.size();
        System.out.println(instance);
        assertEquals(expResult, result);
    }



     /**
     * Test of clear method, of class ReferenceMap.
     */
    @Test
    public void testClear() {
        System.out.println("clear");
        instance.clear();
        assertTrue(instance.size() == 0);
        instance.put(i1, s1);
    }
    
//    /**
//     * Test serialization - not implemented yet
//     */
//    @Test
//    public void serialization() {
//        Object result = null;
//        ObjectOutputStream out = null;
//        ObjectInputStream in = null;
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        try {
//            out = new ObjectOutputStream(baos);
//            out.writeObject(instance);
//            // Unmarshall it
//            in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
//            result = in.readObject();
//        } catch (IOException ex) {
//            ex.printStackTrace(System.out);
//        } catch (ClassNotFoundException ex){
//            ex.printStackTrace(System.out);
//        }
//        assertEquals(instance, result);
//    }
}
