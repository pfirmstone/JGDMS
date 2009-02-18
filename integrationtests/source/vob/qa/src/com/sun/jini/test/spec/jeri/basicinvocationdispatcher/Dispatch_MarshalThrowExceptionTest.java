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

import net.jini.io.MarshalInputStream;

import com.sun.jini.test.spec.jeri.util.FakeInboundRequest;
import com.sun.jini.test.spec.jeri.util.FakeArgument;

import java.util.logging.Level;
import java.util.ArrayList;
import java.lang.reflect.UndeclaredThrowableException;
import java.io.IOException;
import java.rmi.UnknownHostException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.ServerException;
import java.rmi.ConnectIOException;

/**
 * <pre>

 * </pre>
 */
public class Dispatch_MarshalThrowExceptionTest extends AbstractDispatcherTest {

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
        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": IOException thrown from FakeArgument.writeObject()");
        logger.log(Level.FINE,"");

        // initialize FakeRemoteImpl
        impl.setFakeMethodException(
            new FakeArgument(new IOException(),null));

        // initialize FakeInboundRequest
        request = new FakeInboundRequest(methodHash,nullArgs,0x00,0x00);

        // call dispatch and verify the proper result
        dispatcher.dispatch(impl,request,context);
        response = request.getResponseStream();
        assertion(response.read() == 0x02);
        MarshalInputStream mis = new MarshalInputStream(
            response,null,false,null,new ArrayList());
        assertion(mis.read() == -1);

        // iterate over test cases
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable marshalThrowException = cases[i];
            logger.log(Level.FINE,"test case " + (counter++)
                + ": " + marshalThrowException);
            logger.log(Level.FINE,"");

            // initialize FakeBasicInvocationDispatcher
            dispatcher.setMarshalThrowException(marshalThrowException);

            // initialize FakeRemoteImpl
            impl.setFakeMethodException(new RemoteException());

            // initialize FakeInboundRequest
            request = new FakeInboundRequest(methodHash,nullArgs,0x00,0x00);

            // call dispatch and verify the proper result
            dispatcher.dispatch(impl,request,context);
            response = request.getResponseStream();
            assertion(response.read() == 0x02);
            mis = new MarshalInputStream(
                response,null,false,null,new ArrayList());
            assertion(mis.read() == -1);
        }
    }
}

