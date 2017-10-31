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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * This test also validates ReferenceSet and most of ReferenceList.
 * 
 * @author peter
 */
public class ReferenceCollectionTest {
    
    private ReferenceCollection<String> instance;
    private String truck = "truck";
    private String shovel = "shovel";
    private String grader = "grader";
    private String waterTruck = "water truck";
    private String roller = "roller";
    
    public ReferenceCollectionTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        instance = new ReferenceCollection<String>(new ArrayList<Referrer<String>>(), Ref.WEAK_IDENTITY, false, 10000L);
        instance.add(truck);
        instance.add(shovel);
        instance.add(grader);
        instance.add(waterTruck);
        instance.add(roller);
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of size method, of class ReferenceCollection.
     */
    @Test
    public void testSize() {
        System.out.println("size");
        int expResult = 5;
        int result = instance.size();
        assertEquals(expResult, result);
    }

    /**
     * Test of isEmpty method, of class ReferenceCollection.
     */
    @Test
    public void testIsEmpty() {
        System.out.println("isEmpty");
        boolean expResult = false;
        boolean result = instance.isEmpty();
        assertEquals(expResult, result);
    }

    /**
     * Test of contains method, of class ReferenceCollection.
     */
    @Test
    public void testContains() {
        System.out.println("contains");
        String o = "truck";
        boolean expResult = true;
        boolean result = instance.contains(o);
        // This only passes because String uses object pooling. 
        // For other objects this would fail when identity and equality
        // are not the same.
        assertEquals(expResult, result);
    }

    /**
     * Test of iterator method, of class ReferenceCollection.
     */
    @Test
    public void testIterator() {
        System.out.println("iterator");
        Collection<String> expResult = new ArrayList<String>(5);
        expResult.add(truck);
        expResult.add(shovel);
        expResult.add(waterTruck);
        expResult.add(grader);
        expResult.add(roller);
        Collection<String> result = new ArrayList<String>(5);
        Iterator<String> it = instance.iterator();
        while (it.hasNext()){
            result.add(it.next());
        }
        assertTrue(expResult.containsAll(result));
    }

    /**
     * Test of toArray method, of class ReferenceCollection.
     */
    @Test
    public void testToArray_0args() {
        System.out.println("toArray");
        Object[] expResult = {truck, shovel, waterTruck, grader, roller};
        Object[] result = instance.toArray();
        assertTrue(expResult.length == result.length);
        Collection res = Arrays.asList(result);
        Collection exp = Arrays.asList(expResult);
        assertTrue(exp.containsAll(res));
    }

    /**
     * Test of toArray method, of class ReferenceCollection.
     */
    @Test
    public void testToArray_GenericType() {
        System.out.println("toArray");
        String[] a = new String [5];
        String[] expResult = {truck, shovel, waterTruck, grader, roller};
        String[] result = instance.toArray(a);
        assertTrue(expResult.length == result.length);
        Collection<String> res = Arrays.asList(result);
        Collection<String> exp = Arrays.asList(expResult);
        assertTrue(exp.containsAll(res));
    }

    /**
     * Test of add method, of class ReferenceCollection.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        truck = "cat797";
        boolean expResult = true;
        boolean result = instance.add(truck);
        assertEquals(expResult, result);
    }

    /**
     * Test of remove method, of class ReferenceCollection.
     */
    @Test
    public void testRemove() {
        System.out.println("remove");
        boolean expResult = true;
        boolean result = instance.remove(shovel);
        assertEquals(expResult, result);
    }

    /**
     * Test of containsAll method, of class ReferenceCollection.
     */
    @Test
    public void testContainsAll() {
        System.out.println("containsAll");
        Collection<String> c = new ArrayList<String>(4);
        c.add(truck);
        c.add(grader);
        c.add(waterTruck);
        c.add(roller);
        boolean expResult = true;
        boolean result = instance.containsAll(c);
        assertEquals(expResult, result);
    }

    /**
     * Test of addAll method, of class ReferenceCollection.
     */
    @Test
    public void testAddAll() {
        System.out.println("addAll");
        Collection<String> c = new ArrayList<String>(2);
        c.add("Kress");
        c.add("Bucyrus");
        boolean expResult = true;
        boolean result = instance.addAll(c);
        assertEquals(expResult, result);
        assertTrue(instance.containsAll(c));
    }

    /**
     * Test of removeAll method, of class ReferenceCollection.
     */
    @Test
    public void testRemoveAll() {
        System.out.println("removeAll");
        Collection<String> c = new ArrayList<String>(2);
        c.add(grader);
        c.add(roller);
        boolean expResult = true;
        boolean result = instance.removeAll(c);
        assertEquals(expResult, result);
        assertFalse(instance.containsAll(c));
    }

    /**
     * Test of retainAll method, of class ReferenceCollection.
     */
    @Test
    public void testRetainAll() {
        System.out.println("retainAll");
        Collection<String> c = new ArrayList<String>(2);
        c.add(truck);
        c.add(waterTruck);
        boolean expResult = true;
        boolean result = instance.retainAll(c);
        assertEquals(expResult, result);
        assertTrue( instance.size() == 2);
    }

    /**
     * Test of clear method, of class ReferenceCollection.
     */
    @Test
    public void testClear() {
        System.out.println("clear");
        instance.clear();
        assertTrue( instance.isEmpty() );
    }
}
