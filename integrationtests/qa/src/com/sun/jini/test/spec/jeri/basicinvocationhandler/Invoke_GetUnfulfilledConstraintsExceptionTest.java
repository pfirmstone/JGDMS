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
package com.sun.jini.test.spec.jeri.basicinvocationhandler;

import java.util.logging.Level;

import java.util.logging.Level;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationHandler.invoke
 *   method when an exception is thrown by 
 *   OutboundRequest.getUnfulfilledConstraints
 *
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      Throwable getUnfulfilledConstraintsException
 *   and is restricted to instances of:
 *      RuntimeException
 *      Error
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface
 *          -an interface which declares one method that throws Throwable
 *     2) FakeObjectEndpoint
 *          -newCall returns OutboundRequestIterator passed to constructor
 *          -executeCall method throws AssertionError
 *     3) FakeOutboundRequestIterator
 *          -hasNext method returns true on first call and false after that
 *          -next method returns OutboundRequest passed to constructor
 *           and throws NoSuchElementException if called more than once
 *     4) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus method returns false
 *          -getRequestOutputStream method returns a ByteArrayOutputStream
 *          -getResponseInputStream method throws AssertionError
 *          -getUnfulfilledConstraints method
 *           throws getUnfulfilledConstraintsException
 *          -populateContext method does nothing
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeOutboundRequest
 *     2) construct a FakeOutboundRequestIterator, passing in 
 *        FakeOutboundRequest
 *     3) construct a FakeObjectEndpoint, passing in FakeOutboundRequestIterator
 *     4) construct a BasicInvocationHandler, passing in FakeObjectEndpoint
 *        and null MethodConstraints
 *     5) create a dynamic proxy for the FakeInterface using the
 *        BasicInvocationHandler
 *     6) invoke FakeOutboundRequest.setGetUnfulfilledConstraintsException,
 *        passing in getUnfulfilledConstraintsException
 *     7) invoke the method on the dynamic proxy
 *     8) assert getUnfulfilledConstraintsException is thrown directly or
 *        appropriately wrapped
 * </pre>
 */
public class Invoke_GetUnfulfilledConstraintsExceptionTest 
    extends AbstractInvokeTest 
{
    // test cases
    Throwable[] cases = {
        new SecurityException(),                //RuntimeException subclass
        new ArrayIndexOutOfBoundsException(),   //RuntimeException subclass
        new UndeclaredThrowableException(null), //RuntimeException subclass
        new NullPointerException(),             //RuntimeException subclass
        new LinkageError(),                     //Error subclass
        new AssertionError()                    //Error subclass
    };

    // inherit javadoc
    public void run() throws Exception {
        //loop over test cases arrays
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable getUnfulfilledConstraintsException = cases[i];
            logger.log(Level.FINE,"test case " + (counter++)
                + "getUnfulfilledConstraintsException:" 
                + getUnfulfilledConstraintsException);
            logger.log(Level.FINE,"");

            iterator.init();
            request.setUnfulfilledConstraintsException(
                getUnfulfilledConstraintsException);

            try {
                impl.fakeMethod();
                assertion(false);
            } catch (Throwable t) {
                check(false,false,false,false,
                    getUnfulfilledConstraintsException,t);
            }
            requestStream = request.getRequestStream();
            assertion(requestStream.read() == -1);
        }
    }

}

