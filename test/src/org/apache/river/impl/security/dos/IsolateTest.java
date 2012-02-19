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

package org.apache.river.impl.security.dos;

import tests.support.StackOverflowTask;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import tests.support.PrintTask;
import org.junit.After;
import tests.support.ArrayListOverflow;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import tests.support.EndlessLoopTask;
import static org.junit.Assert.*;

/**
 *
 * @author peter
 */
public class IsolateTest {

    IsolatedExecutor executor;
    public IsolateTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        executor = new IsolatedExecutor();
    }
    
    @After
    public void tearDown() {
        executor.shutdownNow();
        executor = null;
    }
    
    @Test
    public void dummy(){
        System.out.println("IsolateTest disabled");
    }

    /**
     * Test of process method, of class IsolatedExecutor.
     * This test leaves stale threads consuming CPU.
     */
//    @Test
//    public void stackOverflow() {
//        System.out.println("Stack overflow");
//        Callable<Object> task = new StackOverflowTask();
//        long timeout = 10L;
//        Exception e = null;
//        Future result = executor.submit(task);
//        try {
//            result.get();
//        } catch ( Exception ex ){
//            e = ex;            
//            ex.printStackTrace(System.out);
//        } 
//        assertTrue((e instanceof Exception));
//        // TODO review the generated test code and remove the default call to fail.
//    }
//    
//   /**
//     * Test of process method, of class IsolatedExecutor.
//     */
//    @Test
//    public void arrayListOverflow() {
//        System.out.println("ArrayList overflow");
////         This leaves stale threads consuming CPU.
//        Callable<Object> task = new ArrayListOverflow();
//        long timeout = 120L;
//        Exception e = null;
//        try {
//            executor.process(task, timeout, TimeUnit.SECONDS);
//        } catch ( Exception ex ){
//            e = ex;            
//            ex.printStackTrace(System.out);
//            if (ex instanceof ExecutionException){
//                Throwable t = ((ExecutionException)ex).getCause();
//                if (t instanceof Error) executor = new IsolatedExecutor();
//            }
//        } 
//        assertTrue((e instanceof Exception));
//        Callable<Boolean> task2 = new PrintTask();
//        Boolean result = Boolean.FALSE;
//        try {
//            result = (Boolean) executor.process(task2, timeout, TimeUnit.MINUTES);
//        } catch (ExecutionException ex) {
//            
//        } catch (InterruptedException ex) {
//            
//        } catch (TimeoutException ex) {
//            
//        }
//        assertTrue(result);
////        // TODO review the generated test code and remove the default call to fail.
//    }

}