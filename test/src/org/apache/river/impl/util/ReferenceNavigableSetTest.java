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
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.lang.ref.Reference;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.NavigableSet;
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
public class ReferenceNavigableSetTest {
    private NavigableSet<String> instance;
    public ReferenceNavigableSetTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        instance = RC.navigableSet(new TreeSet<Referrer<String>>(), Ref.STRONG);
        instance.add("eee");
        instance.add("bbb");
        instance.add("aaa");
        instance.add("ccc");
        instance.add("ddd");
        instance.add("fff");
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of lower method, of class ReferenceNavigableSet.
     */
    @Test
    public void testLower() {
        System.out.println("lower");
        String e = "ccc";
        String expResult = "bbb";
        String result = instance.lower(e);
        assertEquals(expResult, result);
    }

    /**
     * Test of floor method, of class ReferenceNavigableSet.
     */
    @Test
    public void testFloor() {
        System.out.println("floor");
        String e = "ccc";
        Object expResult = "ccc";
        Object result = instance.floor(e);
        assertEquals(expResult, result);
    }

    /**
     * Test of ceiling method, of class ReferenceNavigableSet.
     */
    @Test
    public void testCeiling() {
        System.out.println("ceiling");
        String e = "ddd";
        Object expResult = "ddd";
        Object result = instance.ceiling(e);
        assertEquals(expResult, result);
    }

    /**
     * Test of higher method, of class ReferenceNavigableSet.
     */
    @Test
    public void testHigher() {
        System.out.println("higher");
        String e = "bbb";
        Object expResult = "ccc";
        Object result = instance.higher(e);
        assertEquals(expResult, result);
    }

    /**
     * Test of pollFirst method, of class ReferenceNavigableSet.
     */
    @Test
    public void testPollFirst() {
        System.out.println("pollFirst");
        Object expResult = "aaa";
        String result = instance.pollFirst();
        instance.add(result);// put it back for other tests.
        assertEquals(expResult, result);
    }

    /**
     * Test of pollLast method, of class ReferenceNavigableSet.
     */
    @Test
    public void testPollLast() {
        System.out.println("pollLast");
        Object expResult = "fff";
        String result = instance.pollLast();
        instance.add(result);// Put it back for other tests.
        assertEquals(expResult, result);
    }

    /**
     * Test of descendingSet method, of class ReferenceNavigableSet.
     */
    @Test
    public void testDescendingSet() {
        System.out.println("descendingSet");
        NavigableSet<String> result = instance.descendingSet();
        assertTrue(!result.isEmpty()); // We only want to check the method works.
        assertTrue(result.first() instanceof String); // And the Set contains Strings, not references.
    }

    /**
     * Test of descendingIterator method, of class ReferenceNavigableSet.
     */
    @Test
    public void testDescendingIterator() {
        System.out.println("descendingIterator");
        NavigableSet<String> e = new TreeSet<String>();
        Iterator<String> i = instance.descendingIterator();
        while (i.hasNext()){
            e.add(i.next());
        }
        assertTrue(!e.isEmpty()); // Make sure we received strings.
        assertTrue(e.contains("ccc")); 
    }

    /**
     * Test of subSet method, of class ReferenceNavigableSet.
     */
    @Test
    public void testSubSet() {
        System.out.println("subSet");
        String fromElement = "bbb";
        boolean fromInclusive = false;
        String toElement = "eee";
        boolean toInclusive = false;
        NavigableSet<String> expResult = new TreeSet<String>();
        expResult.add("ccc");
        expResult.add("ddd");
        NavigableSet<String> result = instance.subSet(fromElement, fromInclusive, toElement, toInclusive);
        assertEquals(expResult, result);
    }

    /**
     * Test of headSet method, of class ReferenceNavigableSet.
     */
    @Test
    public void testHeadSet() {
        System.out.println("headSet");
        String toElement = "ccc";
        boolean inclusive = false;
        NavigableSet<String> expResult = new TreeSet<String>();
        expResult.add("aaa");
        expResult.add("bbb");
        NavigableSet<String> result = instance.headSet(toElement, inclusive);
        assertEquals(expResult, result);
    }

    /**
     * Test of tailSet method, of class ReferenceNavigableSet.
     */
    @Test
    public void testTailSet() {
        System.out.println("tailSet");
        String fromElement = "ccc";
        boolean inclusive = false;
        NavigableSet<String> expResult = new TreeSet<String>();
        expResult.add("ddd");
        expResult.add("eee");
        expResult.add("fff");
        NavigableSet<String> result = instance.tailSet(fromElement, inclusive);
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
