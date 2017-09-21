/* Copyright (c) 2010-2012 Zeus Project Services Pty Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.concurrent;

import org.junit.*;

import java.io.*;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 *
 * @author peter
 */
public class ReferenceSortedSetTest {
    private SortedSet<String> instance;
    private Comparator<String> comparator;
    public ReferenceSortedSetTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        
        comparator = new StringComparator();
        Comparator<Referrer<String>> rc = RC.comparator(comparator);
        instance = RC.sortedSet(new TreeSet<Referrer<String>>(rc), Ref.STRONG, 10000L);
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
     * Test of comparator method, of class ReferenceSortedSet.
     */
    @Test
    public void testComparator() {
        System.out.println("comparator");
        Comparator<String> expResult = comparator;
        Comparator<String> result = (Comparator<String>) instance.comparator();
        assertEquals(expResult, result);
    }

    /**
     * Test of subSet method, of class ReferenceSortedSet.
     */
    @Test
    public void testSubSet() {
        System.out.println("subSet");
        String fromElement = "ccc";
        String toElement = "eee";
        SortedSet<String> expResult = new TreeSet<String>();
        expResult.add("ccc");
        expResult.add("ddd");
        SortedSet<String> result = instance.subSet(fromElement, toElement);
        assertEquals(expResult, result);
    }

    /**
     * Test of headSet method, of class ReferenceSortedSet.
     */
    @Test
    public void testHeadSet() {
        System.out.println("headSet");
        String toElement = "ccc";
        SortedSet<String> expResult = new TreeSet<String>();
        expResult.add("aaa");
        expResult.add("bbb");
        SortedSet<String> result = instance.headSet(toElement);
        assertEquals(expResult, result);
    }

    /**
     * Test of tailSet method, of class ReferenceSortedSet.
     */
    @Test
    public void testTailSet() {
        System.out.println("tailSet");
        String fromElement = "eee";
        SortedSet<String> expResult = new TreeSet<String>();
        expResult.add("eee");
        expResult.add("fff");
        SortedSet<String> result = instance.tailSet(fromElement);
        assertEquals(expResult, result);
    }

    /**
     * Test of first method, of class ReferenceSortedSet.
     */
    @Test
    public void testFirst() {
        System.out.println("first");
        String expResult = "aaa";
        Object result = instance.first();
        assertEquals(expResult, result);
    }

    /**
     * Test of last method, of class ReferenceSortedSet.
     */
    @Test
    public void testLast() {
        System.out.println("last");
        Object expResult = "fff";
        Object result = instance.last();
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

    @Test
    public void testEqualsIsImplemented() {
        final SortedSet<Referrer<String>> set = new ConcurrentSkipListSet<Referrer<String>>();
        final ReferenceSortedSet<String> item1 = new ReferenceSortedSet<String>(set, Ref.STRONG, false, 0);
        final ReferenceSortedSet<String> item2 = new ReferenceSortedSet<String>(set, Ref.STRONG, false, 0);
        assertThat(item1, equalTo(item2));
    }
}
