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

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
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
public class ReferenceListTest {
    private List<String> instance;
    private String truck = "truck";
    private String shovel = "shovel";
    private String grader = "grader";
    private String waterTruck = "water truck";
    private String roller = "roller";
    
    public ReferenceListTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        instance = RC.list(new ArrayList<Referrer<String>>(), Ref.WEAK, 10000L);
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
     * Test of equals method, of class ReferenceList.
     */
     @Test
    public void testEquals() {
        System.out.println("testEquals");
        List<String> set1 = new ArrayList<String>(3);
        List<String> set2 = RC.list(new ArrayList<Referrer<String>>(), Ref.SOFT, 10000L);
        String s1 = "1", s2 = "2", s3 = "3";
        set1.add(s1);
        set1.add(s2);
        set1.add(s3);
        set2.add(s1);
        set2.add(s2);
        set2.add(s3);
        assertTrue(set1.equals(set2));
        assertTrue(set2.equals(set1));
        assertTrue(set1.hashCode() == set2.hashCode());
    }

    /**
     * Test of addAll method, of class ReferenceList.
     */
    @Test
    public void testAddAll() {
        System.out.println("addAll");
        int index = 5;
        String [] list = {"Bucyrus", "Marion", "Page"};
        Collection<String> c = Arrays.asList(list);
        boolean expResult = true;
        boolean result = instance.addAll(index, c);
        System.out.println(instance);
        assertEquals(expResult, result);
    }

    /**
     * Test of get method, of class ReferenceList.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        int index = 2;
        String expResult = grader;
        Object result = instance.get(index);
        assertEquals(expResult, result);
    }

    /**
     * Test of set method, of class ReferenceList.
     */
    @Test
    public void testSet() {
        System.out.println("set");
        int index = 2;
        String element = "dozer";
        String expResult = grader;
        String result = instance.set(index, element);
        assertEquals(expResult, result);
    }

    /**
     * Test of add method, of class ReferenceList.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        int index = 4;
        String element = "drill";
        instance.add(index, element);
        String result = instance.get(index);
        assertEquals(element , result);
    }

    /**
     * Test of remove method, of class ReferenceList.
     */
    @Test
    public void testRemove() {
        System.out.println("remove");
        int index = 3;
        String expResult = waterTruck;
        String result = instance.remove(index);
        assertEquals(expResult, result);
    }

       /**
     * Test of indexOf method, of class ReferenceList.
     */
    @Test
    public void testGC() {
        System.out.println("test Garbage collection");
        String o = "drill";
        int expResult = -1;
        int result = instance.indexOf(o);
        System.out.println(instance);
        assertEquals(expResult, result);
    }
    
    /**
     * Test of indexOf method, of class ReferenceList.
     */
    @Test
    public void testIndexOf() {
        System.out.println("indexOf");
        String o = "shovel";
        int expResult = 1;
        int result = instance.indexOf(o);
        assertEquals(expResult, result);
    }

    /**
     * Test of lastIndexOf method, of class ReferenceList.
     */
    @Test
    public void testLastIndexOf() {
        System.out.println("lastIndexOf");
        String o = roller;
        int expResult = 4;
        int result = instance.lastIndexOf(o);
        assertEquals(expResult, result);
    }

    /**
     * Test of listIterator method, of class ReferenceList.
     */
    @Test
    public void testListIterator_0args() {
        System.out.println("listIterator");
        List<String> expResult = new ArrayList<String>();
        ListIterator<String> i = instance.listIterator();
        while( i.hasNext()){
            expResult.add(i.next());
        }
        assertTrue(expResult.containsAll(instance));
    }

    /**
     * Test of listIterator method, of class ReferenceList.
     */
    @Test
    public void testListIterator_int() {
        System.out.println("listIterator");
        int index = 3;
        Collection<String> expResult = new ArrayList<String>(3);
        expResult.add(waterTruck);
        expResult.add(roller);
        Collection<String> result = new ArrayList<String>();
        ListIterator<String> i = instance.listIterator(index);
        while (i.hasNext()){
            result.add(i.next());
        }
        System.out.println(result);
        assertTrue(expResult.containsAll(result));
    }

    /**
     * Test of subList method, of class ReferenceList.
     */
    @Test
    public void testSubList() {
        System.out.println("subList");
        int fromIndex = 0;
        int toIndex = 1;
        List<String> expResult = new ArrayList<String>();
        expResult.add(truck);
        List<String> result = instance.subList(fromIndex, toIndex);
        assertTrue(result.containsAll(expResult));
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
