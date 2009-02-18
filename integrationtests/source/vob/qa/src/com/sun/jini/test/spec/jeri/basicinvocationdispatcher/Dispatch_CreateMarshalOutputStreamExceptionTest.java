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

import java.util.logging.Level;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.rmi.UnknownHostException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.ConnectIOException;
import java.rmi.activation.ActivationException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationDispatcher.dispatch
 *   method when createMarshalOutputStream throws an exception.
 *
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exceptions
 *   denotes one test case and is defined by the variable:
 *      Exception createMarshalOutputStreamException
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
 *          -overloaded createMarshalOutputStream method can
 *           throw a configurable exception
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeRemoteImpl and FakeServerCapabilities
 *     2) construct a FakeBasicInvocationDispatcher, passing in
 *        createMarshalOutputStreamException
 *     3) construct a FakeInboundRequest, passing in method hash of
 *        FakeRemoteImpl's method
 *     4) call BasicInvocationDispatcher.dispatch with FakeRemoteImpl,
 *        FakeInboundRequest, and an empty context
 *     5) assert FakeInboundRequest response stream returns the byte 0x01
 *        and closes the stream
 * </pre>
 */
public class Dispatch_CreateMarshalOutputStreamExceptionTest 
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
    };

    // inherit javadoc
    public void run() throws Exception {
        // iterate over test cases
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable createMarshalOutputStreamException = cases[i];
            logger.log(Level.FINE,"test case " + (counter++) + ": " 
                + createMarshalOutputStreamException);
            logger.log(Level.FINE,"");

            // initialize FakeBasicInvocationDispatcher
            dispatcher.setCreateMarshalOutputStreamException(
                createMarshalOutputStreamException);

            // initialize FakeInboundRequest
            request = new FakeInboundRequest(methodHash,nullArgs,0x00,0x00);

            // call dispatch and verify the proper result
            dispatcher.dispatch(impl,request,context);
            response = request.getResponseStream();
            int read = response.read();
            assertion(read == 0x01,"actual byte: " + read);
            read = response.read();
            assertion(read == -1,"actual byte: " + read);
        }
    }

}

