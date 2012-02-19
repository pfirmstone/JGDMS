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

import edu.illinois.imunit.Schedules;
import edu.illinois.imunit.Schedule;
import org.junit.Test;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Before;
import java.util.concurrent.ConcurrentMap;
import edu.illinois.imunit.IMUnitRunner;
import org.junit.runner.RunWith;
import static edu.illinois.imunit.IMUnit.fireEvent;
import static edu.illinois.imunit.IMUnit.schAssertEquals;
import static edu.illinois.imunit.IMUnit.schAssertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Peter Firmstone.
 */
@RunWith(IMUnitRunner.class)
public class ReferenceConcurrentMapConcurrencyTest {
    private ConcurrentMap<Integer,String> map;
    private ConcurrentMap<Referrer<Integer>,Referrer<String>> internal;
    private String t1, t2, t3, t4;
    @Before
    public void setup() {
        internal = new ConcurrentHashMap<Referrer<Integer>, Referrer<String>>();
        map = RC.concurrentMap( internal, Ref.STRONG, Ref.STRONG);
        t1 = null;
        t2 = null;
        t3 = null;
        t4 = null;
    }
    
    @Test
    @Schedule("startingPutIfAbsent1->finishPutIfAbsent1,finishPutIfAbsent1->startingPutIfAbsent2,finishPutIfAbsent1->startingPutIfAbsent3")
    public void testPut() throws InterruptedException {
        System.out.println("test putIfAbsent");
        performParallelPutIfAbsent();
        assertEquals("Forty-two", map.get(42));
    }
    
    @Test
    @Schedule("startingPut1->finishPut1,finishPut1->startingClear1,startingClear1->finishClear1,finishClear1->startingPut2,finishClear1->startingPut3,finishClear1->startingPut4")
    public void testPutClearPut() throws InterruptedException {
        String exp = "Forty-seven";
        System.out.println("test put Clear put");
        putClearMultiPut();
        assertEquals(exp, map.get(new Integer(42)));
        assertNull(t1);
        boolean success = t2 == null? t3.equals(exp) && t4.equals(exp) : 
                t3 == null ? t2.equals(exp) && t4.equals(exp):
                t4 == null ? t2.equals(t3) && t3.equals(exp): false;
        assertTrue(success);
    }
    
    private void performParallelPutIfAbsent() throws InterruptedException {
        Thread putIfAbsentThread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingPutIfAbsent1");
                map.putIfAbsent(42, "Forty-two");
                fireEvent("finishPutIfAbsent1");
            }
        });
        Thread putIfAbsentThread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingPutIfAbsent2");
                map.putIfAbsent(42, "Forty-seven");
                fireEvent("finishPutIfAbsent2");
            }
        });
        Thread putIfAbsentThread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingPutIfAbsent3");
                map.putIfAbsent(42, "Fifty-one");
                fireEvent("finishPutIfAbsent3");
            }
        });
        putIfAbsentThread1.start();
        putIfAbsentThread2.start();
        putIfAbsentThread3.start();
        putIfAbsentThread1.join();
        putIfAbsentThread2.join();
        putIfAbsentThread3.join();
    }
    
    private void putClearMultiPut() throws InterruptedException {
        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingPut1");
                t1 = map.putIfAbsent(new Integer(42), "Forty-two");
                fireEvent("finishPut1");
            }
        });
        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingPut2");
                t2 = map.putIfAbsent(new Integer(42), "Forty-seven");
                fireEvent("finishPut2");
            }
        });
        Thread thread3 = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingPut3");
                t3 = map.putIfAbsent(new Integer(42), "Forty-seven");
                fireEvent("finishPut3");
            }
        });
        Thread thread4 = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingPut4");
                t4 = map.putIfAbsent(new Integer(42), "Forty-seven");
                fireEvent("finishPut4");
            }
        });
        Thread thread5 = new Thread(new Runnable() {
            @Override
            public void run() {
                fireEvent("startingClear1");
                System.out.println("staring clear");
                Referrer<String> ref = internal.get(ReferenceFactory.singleUseForLookup(new Integer(42), Ref.STRONG));
                assertNotNull( ref);
                ref.clear();
                assertNull( ref.get());
                fireEvent("finishClear1");
            }
        });
        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        thread5.start();
        thread1.join();
        thread2.join();
        thread3.join();
        thread4.join();
        thread5.join();
    }
    
}
