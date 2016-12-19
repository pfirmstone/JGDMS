/*
 * Copyright 2012 Zeus Project Services Pty Ltd
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Firmstone.
 */
public class ReferrerTimeTest {
    private ConcurrentNavigableMap<Integer, String> instance;
    // strong references
    private Integer [] ints;
    private Comparator<Integer> comparator;
    public ReferrerTimeTest() {
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
         ConcurrentNavigableMap<Referrer<Integer>, Referrer<String>> internal 
                = new ConcurrentSkipListMap<Referrer<Integer>, Referrer<String>>(ci);
        instance = RC.concurrentNavigableMap(internal, Ref.TIME, Ref.STRONG, 600L, 600L);
        ints = new Integer[5];
        ints[0] = 0;
        ints[1] = 1;
        ints[2] = 2;
        ints[3] = 3;
        ints[4] = 4;
        instance.put(ints[0], "0");
        instance.put(ints[1], "1");
        instance.put(ints[2], "2");
        instance.put(ints[3], "3");
        instance.put(ints[4], "4");
    }
    
    @Test
    public void testCleanerIteration() throws InterruptedException{
        System.out.println("testCleanerIteration");
        long start = System.nanoTime();
        Thread.sleep(1800L);
        long finish = System.nanoTime();
        System.out.println(finish - start);
        assertTrue(instance.keySet().isEmpty());
        assertTrue(instance.values().isEmpty());
        assertFalse(instance.containsKey(ints[0]));
        assertFalse(instance.containsValue("1"));
    }
    
    @Test
    public void testKeyRemove(){
        System.out.println("testKeyRemove");
        Collection keys = instance.keySet();
        keys.remove(ints[1]);
        assertFalse(instance.containsKey(ints[1]));
    }
   
    @Test
    public void testCleanerRetains() throws InterruptedException{
        System.out.println("testCleanerRetains");
        instance.putIfAbsent(ints[0], "Zero");
        instance.putIfAbsent(ints[1], "One");
        instance.putIfAbsent(ints[2], "Two");
        instance.putIfAbsent(ints[3], "Three");
        instance.putIfAbsent(ints[4], "Four");
        for (int i=0; i<6; i++){
            Thread.sleep(200L);
            for( int j=0; j<5 ; j++){
                System.out.println(instance.get(ints[j]));
            }
        }
        for (int k = 0; k<5; k++){
            assertTrue(instance.containsKey(ints[k]));
        }
    }
    
    @Test
    public void testCleanerRetainsOnlyOne() throws InterruptedException{
        System.out.println("testCleanerRetainsOnlyOne");
        instance.putIfAbsent(ints[0], "Zero");
        instance.putIfAbsent(ints[1], "One");
        instance.putIfAbsent(ints[2], "Two");
        instance.putIfAbsent(ints[3], "Three");
        instance.putIfAbsent(ints[4], "Four");
        for (int i=0; i<6; i++){
            Thread.sleep(300L);
            System.out.println(instance.get(ints[1]));
        }
        assertTrue(instance.containsKey(ints[1]));
        assertFalse(instance.containsKey(ints[0]));
        assertFalse(instance.containsKey(ints[2]));
        assertFalse(instance.containsKey(ints[3]));
        assertFalse(instance.containsKey(ints[4]));
    }
   
    @Test
    public void TestQueueFuture() throws InterruptedException 
    {
        System.out.println("testQueueFuture");
        Queue<Future> que = RC.queue(
                new ConcurrentLinkedQueue<Referrer<Future>>(), 
                Ref.TIME, 20L);
        Future f = new F();
        que.offer(f);
        Thread.sleep(60L);
        assertTrue(f.isCancelled());
    }
    
    private static class F implements Future{
        private volatile boolean cancelled = false;
        
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            System.out.println("cancelled");
            return true;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public boolean isDone() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Object get() throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }
    
}
