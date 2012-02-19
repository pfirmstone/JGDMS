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

import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.Reference;
import java.util.concurrent.ConcurrentMap;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Firmstone.
 */
public class ReferenceConcurrentMapTest {
    private ConcurrentMap<Integer, String> instance;
    // strong references
    private Integer i1, i2, i3, i4, i5;
    
    public ReferenceConcurrentMapTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        ConcurrentMap<Referrer<Integer>, Referrer<String>> internal 
                = new ConcurrentHashMap<Referrer<Integer>, Referrer<String>>(5);
        instance = RC.concurrentMap(internal, Ref.WEAK, Ref.STRONG);
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
     * Test of putIfAbsent method, of class ConcurrentReferenceMap.
     */
    @Test
    public void testPutIfAbsent() {
        System.out.println("putIfAbsent");
        Integer key = 5;
        String value = "Five";
        Object expResult = "5";
        Object result = instance.putIfAbsent(key, value);
        assertEquals(expResult, result);
    }

    /**
     * Test of remove method, of class ConcurrentReferenceMap.
     */
    @Test
    public void testRemove() {
        System.out.println("remove");
        Integer key = 4;
        String value = "4";
        boolean expResult = true;
        boolean result = instance.remove(key, value);
        assertEquals(expResult, result);
        assertFalse(instance.containsValue(value));
    }

    /**
     * Test of replace method, of class ConcurrentReferenceMap.
     */
    @Test
    public void testReplace_3args() {
        System.out.println("replace");
        Integer key = 3;
        String oldValue = "3";
        String newValue = "Three";
        boolean expResult = true;
        boolean result = instance.replace(key, oldValue, newValue);
        assertEquals(expResult, result);
        assertFalse(instance.containsValue(oldValue));
    }

    /**
     * Test of replace method, of class ConcurrentReferenceMap.
     */
    @Test
    public void testReplace_GenericType_GenericType() {
        System.out.println("replace");
        Integer key = 2;
        String value = "Two";
        String expResult = "2";
        String result = instance.replace(key, value);
        assertEquals(expResult, result);
    }
}
