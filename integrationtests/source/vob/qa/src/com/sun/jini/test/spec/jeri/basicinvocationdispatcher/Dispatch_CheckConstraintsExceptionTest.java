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
package com.sun.jini.test.spec.jeri.basicinvocationdispatcher;

import java.util.logging.Level;

import net.jini.io.UnsupportedConstraintException;

import com.sun.jini.test.spec.jeri.util.FakeInboundRequest;

import java.util.logging.Level;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationDispatcher.dispatch
 *   method when an exception (possibly wrapped) is thrown by the
 *   InboundRequest.checkConstraints method.
 *
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      Throwable checkConstraintsException
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeRemoteImpl
 *          -implements Remote
 *          -a class which declares one method that throws RemoteException
 *     2) FakeInboundRequest
 *          -constructor takes four parameters (first 2 bytes, method hash,
 *           and arguments) and writes them to request stream
 *          -abort method does nothing
 *          -checkConstraints throws a configurable exception
 *          -checkPermissions does nothing
 *          -populateContext does nothing
 *          -getClientHost returns local host name
 *          -getRequestInputStream and getResponseOutputStream methods
 *           return streams created in constructor
 *     3) FakeServerCapabilities
 *          -checkConstraints method returns InvocationConstraints.EMPTY
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeRemoteImpl and FakeServerCapabilities
 *     2) construct a BasicInvocationDispatcher, passing in the Collection of
 *        FakeRemoteImpl methods, FakeServerCapabilities,
 *        and null MethodConstraints and permission Class
 *     3) construct a FakeInboundRequest, passing in method hash of
 *        FakeRemoteImpl's method and checkConstraintsException
 *     4) call BasicInvocationDispatcher.dispatch with FakeRemoteImpl,
 *        FakeInboundRequest, and an empty context
 *     5) assert FakeInboundRequest response stream returns the byte 0x02
 *        and the properly wrapped exception checkConstraintsException
 * </pre>
 */
public class Dispatch_CheckConstraintsExceptionTest 
     extends AbstractDispatcherTest 
{

    // test cases
    Throwable[] cases = {
        new UnsupportedConstraintException(""), //IOException subclass
        new SecurityException(),                //RuntimeException subclass
        new ArrayIndexOutOfBoundsException(),   //RuntimeException subclass
        new UndeclaredThrowableException(null), //RuntimeException subclass
        new NullPointerException(),             //RuntimeException subclass
        new LinkageError(),                     //Error subclass
        new AssertionError(),                   //Error subclass
    };

    // inherit javadoc
    public void run() throws Exception {
        // iterate over test cases
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable checkConstraintsException = cases[i];
            logger.log(Level.FINE,"test case " + (counter++)
                +": checkConstraints exception:"+checkConstraintsException);
            logger.log(Level.FINE,"");

            // initialize FakeInboundRequest
            request = new FakeInboundRequest(methodHash,nullArgs,0x00,0x00);
            request.setCheckConstraintsException(checkConstraintsException);

            // call dispatch and verify the proper result
            dispatcher.dispatch(impl,request,context);
            response = request.getResponseStream();
            assertion(response.read() == 0x02);
            checkUnmarshallingException(checkConstraintsException,response);
        }
    }

}

