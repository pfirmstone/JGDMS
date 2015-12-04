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
package org.apache.river.test.spec.jeri.basicinvocationhandler;

import java.util.logging.Level;

import java.util.logging.Level;
import java.lang.reflect.UndeclaredThrowableException;
import java.io.IOException;
import java.rmi.UnknownHostException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.ConnectIOException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationHandler.invoke
 *   method when an exception is thrown from the
 *   BasicInvocationHandler.unmarshalReturn method.
 *
 * Test Cases
 *   This test iterates over a set of exceptions and boolean delivery status
 *   values.  Each {exception,deliveryStatus} pair denotes one test case
 *   and is defined by the variables:
 *      Throwable unmarshalReturnException
 *      boolean   deliveryStatus
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface
 *          -an interface which declares one method that throws Throwable
 *     2) FakeObjectEndpoint
 *          -newCall returns OutboundRequestIterator passed to constructor
 *          -executeCall method returns null
 *     3) FakeOutboundRequestIterator
 *          -hasNext method returns true on first call and false after that
 *          -next method returns OutboundRequest passed to constructor
 *           and throws NoSuchElementException if called more than once
 *     4) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus method returns deliveryStatus
 *          -getRequestOutputStream method returns a ByteArrayOutputStream
 *          -getResponseInputStream method returns passed in ByteArrayInputStream
 *          -getUnfulfilledConstraints method return InvocationConstraints.EMPTY
 *          -populateContext method does nothing
 *     5) FakeBasicInvocationHandler
 *          -subclasses BasicInvocationHandler
 *          -overloaded unmarshalReturn throws unmarshalReturnException
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeOutboundRequest
 *     2) construct a FakeOutboundRequestIterator, passing in FakeOutboundRequest
 *     3) construct a FakeObjectEndpoint, passing in FakeOutboundRequestIterator
 *     4) construct a FakeBasicInvocationHandler, passing in FakeObjectEndpoint
 *        and FakeMethodConstraints
 *     5) create a dynamic proxy for the FakeInterface using the
 *        BasicInvocationHandler
 *     6) write 0x01 to stream returned from
 *        FakeOutboundRequest.getResponseInputStream 
 *     7) invoke FakeBasicInvocationHandler.setUnarshalReturnException
 *        passing in unmarshalReturnException
 *     8) invoke FakeOutboundRequest.setDeliveryStatusReturn
 *        passing in deliveryStatus
 *     9) invoke the method on the dynamic proxy
 *    10) assert unmarshalReturnException is thrown directly or
 *        appropriately wrapped
 * </pre>
 */
public class Invoke_UnmarshalReturnExceptionTest extends AbstractInvokeTest {

    // test cases
    Throwable[] cases = {
        new IOException(),
        new java.net.UnknownHostException(),    //IOException subclass
        new java.net.ConnectException(),        //IOException subclass
        new RemoteException(),                  //IOException subclass
        new java.rmi.UnknownHostException(""),  //RemoteException subclass
        new java.rmi.ConnectException(""),      //RemoteException subclass
        new MarshalException(""),               //RemoteException subclass
        new UnmarshalException(""),             //RemoteException subclass
        new ConnectIOException(""),             //RemoteException subclass
        new SecurityException(),                //RuntimeException subclass
        new ArrayIndexOutOfBoundsException(),   //RuntimeException subclass
        new UndeclaredThrowableException(null), //RuntimeException subclass
        new NullPointerException(),             //RuntimeException subclass
        new LinkageError(),                     //Error subclass
        new AssertionError(),                   //Error subclass
        new ClassNotFoundException()            //special case
    };

    boolean[] deliveryStatusCases = { true, false };

    // inherit javadoc
    public void run() throws Exception {
        //loop over deliveryStatusCases and cases arrays
        for (int h = 0; h < deliveryStatusCases.length; h++) {
            boolean deliveryStatus = deliveryStatusCases[h];

            for (int i = 0; i < cases.length; i++) {
                logger.log(Level.FINE,"=================================");
                Throwable unmarshalReturnException = cases[i];
                logger.log(Level.FINE,"test case " + (counter++) + ": " 
                    + "deliveryStatus:" + deliveryStatus
                    + ",exception:" + unmarshalReturnException);
                logger.log(Level.FINE,"");

                iterator.init();
                request.setDeliveryStatusReturn(deliveryStatus);
                request.setResponseInputStream(0x01,null);
                handler.setUnmarshalReturnException(
                    unmarshalReturnException);

                // call method and verify the proper result
                try {
                    impl.fakeMethod();
                    assertion(false);
                } catch (Throwable t) {
                    check(true,deliveryStatus,false,true,
                        unmarshalReturnException,t);
                }

            } //end inner loop
        } //end outter loop
    }

}

