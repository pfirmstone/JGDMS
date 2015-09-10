/*
 * Copyright 2012 peter.
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

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class TimedReferrerTest {
    String t = "test";
    TimedRefQueue que = new TimedRefQueue();
    public TimedReferrerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
    }

    /**
     * Test of get method, of class TimedReferrer.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        TimedReferrer instance = new TimedReferrer(t, que);
        Object expResult = t;
        Object result = instance.get();
        assertEquals(expResult, result);
    }

    /**
     * Test of clear method, of class TimedReferrer, it should ignore the
     * clear call.
     */
    @Test
    public void testClear() {
        System.out.println("clear");
        TimedReferrer instance = new TimedReferrer<String>(t, que);
        Object expResult = t;
        Object result = instance.get();
        assertEquals(expResult, result);
        instance.clear();
        result = instance.get();
        assertEquals(expResult, result);
    }

    /**
     * Test of isEnqueued method, of class TimedReferrer.
     */
    @Test
    public void testIsEnqueued() {
        System.out.println("isEnqueued");
        TimedReferrer instance = new TimedReferrer(t, que);
        boolean expResult = false;
        boolean result = instance.isEnqueued();
        assertEquals(expResult, result);
        instance.enqueue();
        assertEquals(instance.isEnqueued(), true);
    }

    /**
     * Test of updateClock method, of class TimedReferrer.
     */
    @Test
    public void testUpdateClock() {
        System.out.println("updateClock");
        long time = System.nanoTime();
        TimedReferrer instance = new TimedReferrer(t, que);
        instance.updateClock(time);
        assertFalse(instance.isEnqueued());
        instance.updateClock(time + 6000000000L);
        instance.updateClock(time + 9000000000L);
        assertTrue(instance.isEnqueued());
        Object result = que.poll();
        assertTrue(result instanceof Referrer);
        assertTrue(instance.get() != null);
    }

}
