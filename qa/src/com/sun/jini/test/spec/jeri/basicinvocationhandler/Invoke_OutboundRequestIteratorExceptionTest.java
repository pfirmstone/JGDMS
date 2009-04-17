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

import com.sun.jini.qa.harness.TestException;
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
 *   method when an exception is thrown or a bad value is returned from
 *   OutboundRequestIterator methods.
 *
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      Throwable nextException
 *   and is restricted to instances of:
 *      IOException
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
 *          -hasNext method returns configurable value
 *          -next method throws configurable exception
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeOutboundRequestIterator
 *     2) construct a FakeObjectEndpoint, passing in FakeOutboundRequestIterator
 *     3) construct a BasicInvocationHandler, passing in FakeObjectEndpoint
 *        and null MethodConstraints
 *     5) create a dynamic proxy for the FakeInterface using the
 *        BasicInvocationHandler
 *     6) invoke FakeOutboundRequestIterator.setHasNextReturn,
 *        passing in false
 *     7) invoke the method on the dynamic proxy
 *     8) assert ConnectIOException
 *     9) for each test cse do the following:
 *          a) invoke FakeOutboundRequestIterator.setHasNextReturn,
 *             passing in {true,true,true,false}
 *          b) invoke FakeOutboundRequestIterator.setNextException
 *             passing in nextException
 *          c) invoke the method on the dynamic proxy
 *          d) assert nextException is thrown directly or wrapped
 *             as appropriate
 *          e) check FakeOutboundRequestIterator.hasNext was called 4 times
 * </pre>
 */
public class Invoke_OutboundRequestIteratorExceptionTest 
     extends AbstractInvokeTest
{
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
        new AssertionError()                    //Error subclass
    };

    // inherit javadoc
    public void run() throws Exception {
        try {
            request.setDeliveryStatusReturn(true);

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case " + (counter++)
                + ": hasNext returns false");
            logger.log(Level.FINE,"");

            iterator.init();
            iterator.setHasNextReturn(new boolean[] {false});

            // call method and verify the proper result
            try {
                impl.fakeMethod();
                assertion(false);
            } catch (ConnectIOException ignore) {
            }

            for (int i = 0; i < cases.length; i++) {
                logger.log(Level.FINE,"=================================");
                Throwable nextException = cases[i];
                logger.log(Level.FINE,"test case " + (counter++)
                    + ": nextException:" + nextException);
                logger.log(Level.FINE,"");

                iterator.init();
                iterator.setHasNextReturn(new boolean[] {true,true,true,false});
                iterator.setNextException(nextException);

                // call method and verify the proper result
                try {
                    impl.fakeMethod();
                    assertion(false);
                } catch (Throwable t) {
                    check(false,true,false,false,nextException,t);
                }
                if (nextException instanceof Error) {
                    assertion(iterator.getHasNextCalls() == 1);
                } else {
                    assertion(iterator.getHasNextCalls() == 4);
                }

            }
        } catch (Throwable t) {
            logger.log(Level.FINE,"Caught unexpected exception",t);
            throw new TestException("Caught unexpected exception: ",t);
        }
    }

}

