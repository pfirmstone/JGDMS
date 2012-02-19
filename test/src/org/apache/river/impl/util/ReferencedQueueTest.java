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
import java.util.LinkedList;
import java.util.Queue;
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
public class ReferencedQueueTest {
    Queue<String> instance;
    public ReferencedQueueTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        instance = RC.queue(new LinkedList<Referrer<String>>(), Ref.SOFT);
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of offer method, of class ReferencedQueue.
     */
    @Test
    public void testOffer() {
        System.out.println("offer");
        String e = "offer";
        boolean expResult = true;
        boolean result = instance.offer(e);
        assertEquals(expResult, result);
    }

    /**
     * Test of remove method, of class ReferencedQueue.
     */
    @Test
    public void testRemove() {
        String expResult = "remove";
        System.out.println(expResult);
        instance.add(expResult);
        Object result = instance.remove();
        assertEquals(expResult, result);
    }

    /**
     * Test of poll method, of class ReferencedQueue.
     */
    @Test
    public void testPoll() {
        String expResult = "poll";
        System.out.println(expResult);
        instance.add(expResult);
        String result = instance.poll();
        assertEquals(expResult, result);
    }

    /**
     * Test of element method, of class ReferencedQueue.
     */
    @Test
    public void testElement() {
        String expResult = "element";
        System.out.println(expResult);
        instance.add(expResult);
        Object result = instance.element();
        assertEquals(expResult, result);
    }

    /**
     * Test of peek method, of class ReferencedQueue.
     */
    @Test
    public void testPeek() {
        String expResult = "peek";
        System.out.println(expResult);
        instance.add(expResult);
        Object result = instance.peek();
        assertEquals(expResult, result);
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
        assertTrue(result instanceof Queue);
        assertTrue(instance.containsAll((Queue<String>)result));
    }
    
}
