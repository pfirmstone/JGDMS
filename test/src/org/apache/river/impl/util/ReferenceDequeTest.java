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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.lang.ref.Reference;
import java.util.Deque;
import java.util.Iterator;
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
public class ReferenceDequeTest {
    private Deque<String> instance;
    public ReferenceDequeTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        instance = RC.deque(new LinkedList<Referrer<String>>(), Ref.WEAK);
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of addFirst method, of class ReferenceDeque.
     */
    @Test
    public void testAddFirst() {
        System.out.println("addFirst");
        String e = "addFirst";
        instance.addFirst(e);
        String result = instance.peekFirst();
        assertEquals(e, result);
    }

    /**
     * Test of addLast method, of class ReferenceDeque.
     */
    @Test
    public void testAddLast() {
        System.out.println("addLast");
        String e = "addLast";
        instance.addLast(e);
        String result = instance.peekLast();
        assertEquals(e, result);
    }

    /**
     * Test of offerFirst method, of class ReferenceDeque.
     */
    @Test
    public void testOfferFirst() {
        System.out.println("offerFirst");
        String e = "offerFirst";
        boolean expResult = true;
        boolean result = instance.offerFirst(e);
        assertEquals(expResult, result);
        String r = instance.pollFirst();
        assertEquals(e, r);
    }

    /**
     * Test of offerLast method, of class ReferenceDeque.
     */
    @Test
    public void testOfferLast() {
        System.out.println("offerLast");
        String e = "offerLast";
        boolean expResult = true;
        boolean result = instance.offerLast(e);
        assertEquals(expResult, result);
        String r = instance.peekLast();
        assertEquals(e, r);
    }

    /**
     * Test of removeFirst method, of class ReferenceDeque.
     */
    @Test
    public void testRemoveFirst() {
        System.out.println("removeFirst");
        String expResult = "removeFirst";
        instance.offerFirst(expResult);
        Object result = instance.removeFirst();
        assertEquals(expResult, result);
    }

    /**
     * Test of removeLast method, of class ReferenceDeque.
     */
    @Test
    public void testRemoveLast() {
        System.out.println("removeLast");
        String expResult = "removeLast";
        instance.offerLast(expResult);
        Object result = instance.removeLast();
        assertEquals(expResult, result);
    }

    /**
     * Test of pollFirst method, of class ReferenceDeque.
     */
    @Test
    public void testPollFirst() {
        System.out.println("pollFirst");
        String expResult = "pollFirst";
        instance.offerFirst(expResult);
        Object result = instance.pollFirst();
        assertEquals(expResult, result);
    }

    /**
     * Test of pollLast method, of class ReferenceDeque.
     */
    @Test
    public void testPollLast() {
        System.out.println("pollLast");
        String expResult = "pollLast";
        instance.offerLast(expResult);
        Object result = instance.pollLast();
        assertEquals(expResult, result);
    }

    /**
     * Test of getFirst method, of class ReferenceDeque.
     */
    @Test
    public void testGetFirst() {
        System.out.println("getFirst");
        String expResult = "getFirst";
        instance.offerFirst(expResult);
        Object result = instance.getFirst();
        assertEquals(expResult, result);
    }

    /**
     * Test of getLast method, of class ReferenceDeque.
     */
    @Test
    public void testGetLast() {
        System.out.println("getLast");
        String expResult = "getLast";
        instance.offerLast(expResult);
        Object result = instance.getLast();
        assertEquals(expResult, result);
    }

    /**
     * Test of peekFirst method, of class ReferenceDeque.
     */
    @Test
    public void testPeekFirst() {
        System.out.println("peekFirst");
        String expResult = "peekFirst";
        instance.offerFirst(expResult);
        Object result = instance.peekFirst();
        assertEquals(expResult, result);
    }

    /**
     * Test of peekLast method, of class ReferenceDeque.
     */
    @Test
    public void testPeekLast() {
        System.out.println("peekLast");
        String expResult = "peekLast";
        instance.offerLast(expResult);
        Object result = instance.peekLast();
        assertEquals(expResult, result);
    }

    /**
     * Test of removeFirstOccurrence method, of class ReferenceDeque.
     */
    @Test
    public void testRemoveFirstOccurrence() {
        System.out.println("removeFirstOccurrence");
        String o = "removeFirstOccurrence";
        instance.offerLast(o);
        boolean expResult = true;
        boolean result = instance.removeFirstOccurrence(o);
        assertEquals(expResult, result);
        expResult = false;
        result = instance.removeFirstOccurrence(o);
        assertEquals(expResult, result);
    }

    /**
     * Test of removeLastOccurrence method, of class ReferenceDeque.
     */
    @Test
    public void testRemoveLastOccurrence() {
        System.out.println("removeLastOccurrence");
        String o = "removeLastOccurrence";
        instance.offerFirst(o);
        boolean expResult = true;
        boolean result = instance.removeLastOccurrence(o);
        assertEquals(expResult, result);
        expResult = false;
        result = instance.removeLastOccurrence(o);
        assertEquals(expResult, result);
    }

    /**
     * Test of push method, of class ReferenceDeque.
     */
    @Test
    public void testPush() {
        System.out.println("push");
        String e = "push";
        instance.push(e);
        String result = instance.poll();
        assertEquals(e, result);
    }

    /**
     * Test of pop method, of class ReferenceDeque.
     */
    @Test
    public void testPop() {
        System.out.println("pop");
        String expResult = "pop";
        instance.push(expResult);
        Object result = instance.pop();
        assertEquals(expResult, result);
    }

    /**
     * Test of descendingIterator method, of class ReferenceDeque.
     */
    @Test
    public void testDescendingIterator() {
        System.out.println("descendingIterator");
        String [] e = {"1", "2", "3", "4"};
        for ( int i = 0; i < e.length; i++){
            instance.offer(e[i]);
        }
        Iterator<String> it = instance.descendingIterator();
        int i = 3;
        while (it.hasNext()){
            String r = it.next();
            assertEquals(e[i], r);
            if (i == 0) break;
            i--;
        }
    }
    
      
    /**
     * Test serialization
     */
    @Test
    @SuppressWarnings("unchecked")
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
        assertTrue(result instanceof Deque);
        assertTrue(instance.containsAll((Deque<String>)result));
    }
}
