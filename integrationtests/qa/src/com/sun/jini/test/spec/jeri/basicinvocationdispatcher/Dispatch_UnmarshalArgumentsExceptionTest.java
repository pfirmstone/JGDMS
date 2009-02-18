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

import com.sun.jini.test.spec.jeri.util.FakeInboundRequest;
import com.sun.jini.test.spec.jeri.util.FakeArgument;

import java.util.logging.Level;
import java.lang.reflect.UndeclaredThrowableException;
import java.io.ByteArrayInputStream;
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
 *   This test verifies the behavior of the BasicInvocationDispatcher.dispatch
 *   method when an exception (possibly wrapped) is thrown while unmarshalling
 *   the method arguments.
 *
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      Throwable unmarshalArgumentsException
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeRemoteImpl
 *          -implements Remote
 *          -a class which declares one method that takes one argument
 *           and throws RemoteException
 *     2) FakeInboundRequest
 *          -constructor takes four parameters (first 2 bytes, method hash,
 *           and arguments) and writes them to request stream
 *          -abort method does nothing
 *          -checkConstraints does nothing
 *          -checkPermissions does nothing
 *          -populateContext does nothing
 *          -getClientHost returns local host name
 *          -getRequestInputStream and getResponseOutputStream methods
 *           return streams created in constructor
 *     3) FakeServerCapabilities
 *          -checkConstraints method returns InvocationConstraints.EMPTY
 *     4) FakeBasicInvocationDispatcher
 *          -subclasses BasicInvocationDispatcher
 *          -overloaded unmarshalArguments method can
 *           throw a configurable exception
 *     5) FakeArgument
 *          -extends Exception
 *          -if constructed with a read object exception (IOException,
 *           ClassNotFoundException, RuntimeException, or Error) then throw
 *           that exception in the custom readObject method
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeRemoteImpl and FakeServerCapabilities
 *     2) construct a FakeBasicInvocationDispatcher, passing in
 *        unmarshalArgumentsException
 *     3) construct a FakeInboundRequest, passing in method hash of
 *        FakeRemoteImpl's method
 *     4) construct a FakeArgument, passing in a readObject IOException
 *        (will be thrown if unmarshalArgumentsException is null)
 *     5) call BasicInvocationDispatcher.dispatch with FakeRemoteImpl,
 *        FakeInboundRequest, and an empty context
 *     6) assert FakeInboundRequest response stream returns the byte 0x02
 *        and the properly wrapped exception
 * </pre>
 */
public class Dispatch_UnmarshalArgumentsExceptionTest 
     extends AbstractDispatcherTest 
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
        new AssertionError(),                   //Error subclass
        new ClassNotFoundException()            //special case
    };

    // inherit javadoc
    public void run() throws Exception {
        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": IOException thrown from FakeArgument.readObject()");
        logger.log(Level.FINE,"");

        // initialize FakeInboundRequest
        Object[] args = { new FakeArgument(null, new IOException()) };
        request = new FakeInboundRequest(methodHash,args,0x00,0x00);

        // call dispatch and verify the proper result
        dispatcher.dispatch(impl,request,context);
        response = request.getResponseStream();
        assertion(response.read() == 0x02);
        checkUnmarshallingException(new IOException(), response);

        // iterate over test cases
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable unmarshalArgumentsException = cases[i];
            logger.log(Level.FINE,"test case " + (counter++)
                +": unmarshalArgs exception: "+unmarshalArgumentsException);
            logger.log(Level.FINE,"");

            // initialize FakeBasicInvocationDispatcher
            dispatcher.setUnmarshalArgumentsException(
                unmarshalArgumentsException);

            // initialize FakeInboundRequest
            request = new FakeInboundRequest(methodHash,nullArgs,0x00,0x00);

            // call dispatch and verify the proper result
            dispatcher.dispatch(impl,request,context);
            response = request.getResponseStream();
            assertion(response.read() == 0x02);
            checkUnmarshallingException(
                unmarshalArgumentsException, response);
        }
    }
}

