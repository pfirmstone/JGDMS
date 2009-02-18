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
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException; 
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.ConnectIOException;
import java.rmi.server.ExportException;
import java.rmi.server.SocketSecurityException;
import java.rmi.UnexpectedException;
import java.rmi.ServerException;
import java.rmi.ServerError;
import java.rmi.activation.ActivateFailedException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationHandler.invoke
 *   method when ObjectEndpoint.executeCall returns a non-null value.
 *
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      RemoteException executeCallReturn
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface
 *          -an interface which declares these methods:
 *             a) one declares an Exception
 *             b) one declares an IOException
 *             c) one declares a RemoteException
 *     2) FakeObjectEndpoint
 *          -newCall returns OutboundRequestIterator passed to constructor
 *          -executeCall method returns executeCallReturn
 *     3) FakeOutboundRequestIterator
 *          -hasNext method returns true on first call and false after that
 *          -next method returns OutboundRequest passed to constructor
 *           and throws NoSuchElementException if called more than once
 *     4) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus method returns false
 *          -getRequestOutputStream method returns a ByteArrayOutputStream
 *          -getResponseInputStream method throws AssertionError
 *          -getUnfulfilledConstraints method return InvocationConstraints.EMPTY
 *          -populateContext method does nothing
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeOutboundRequest
 *     2) construct a FakeOutboundRequestIterator,passing in FakeOutboundRequest
 *     3) construct a FakeObjectEndpoint, passing in FakeOutboundRequestIterator
 *     4) construct a BasicInvocationHandler, passing in FakeObjectEndpoint
 *        and FakeMethodConstraints
 *     5) create a dynamic proxy for the FakeInterface using the
 *        BasicInvocationHandler
 *     6) invoke FakeObjectEndpoint.setExecuteCallReturn
 *        passing in executeCallReturn
 *     7) for each method in FakeInterface do the following:
 *          a) invoke the method on the dynamic proxy
 *          b) assert executeCallReturn is thrown directly or wrapped
 *             in a UndeclaredThrowableException as appropriate
 * </pre>
 */
public class Invoke_ExecuteCallNonNullReturnTest extends AbstractInvokeTest {

    // test cases
    RemoteException[] cases = {
        new RemoteException(),
        new MarshalException(""),
        new UnmarshalException(""),
        new ConnectIOException(""),
        new ExportException(""),
        new SocketSecurityException(""),
        new UnexpectedException(""),
        new ActivateFailedException(""),
        new ServerException(""),
        new ServerError("",null)
    };

    interface FakeInterface {
        public void throwsException() throws Exception;
        public void throwsIOException() throws IOException;
        public void throwsRemoteException() throws RemoteException;
    }

    // inherit javadoc
    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            RemoteException executeCallReturn = cases[i];
            logger.log(Level.FINE,"test case " + (counter++) + ": " 
                + executeCallReturn);
            logger.log(Level.FINE,"");

            // setup infrastructure needed by test
            objectEndpoint.setExecuteCallReturn(executeCallReturn);
            FakeInterface impl = (FakeInterface) Proxy.newProxyInstance(
                FakeInterface.class.getClassLoader(),
                new Class[] { FakeInterface.class },
                handler);

            // call each method and verify the proper result
            iterator.init();
            try {
                impl.throwsException();
                assertion(false);
            } catch (Throwable t) {
                check(executeCallReturn, t, Exception.class);
            }

            iterator.init();
            try {
                impl.throwsIOException();
                assertion(false);
            } catch (Throwable t) {
                check(executeCallReturn, t, IOException.class);
            }

            iterator.init();
            try {
                impl.throwsRemoteException();
                assertion(false);
            } catch (Throwable t) {
                check(executeCallReturn, t, RemoteException.class);
            }
        }
    }

    /**
     * Verify that an exception returned by 
     * <code>ObjectEndpoint.executeCall</code>
     * was properly wrapped and thrown.  This method returns normally
     * if the correct exception was thrown to this instance; otherwise
     * a <code>TestException</code> is thrown.
     *
     * @param returned the exception returned by ObjectEndpoint.executeCall
     * @param caught the exception caught by this instance
     * @param the type of the exception that the invoked method declares
     * @throws NullPointerException if either of returned or caught is null
     * @throws TestException if the caught exception is the wrong type
     */
    void check(RemoteException returned, Throwable caught, Class methodThrow) 
        throws TestException 
    {
        if (methodThrow != null &&
            methodThrow.isAssignableFrom(returned.getClass()))
        {
            assertion(returned.equals(caught),caught.toString());
        } else {
            assertion(caught instanceof UndeclaredThrowableException,
                caught.toString());
            assertion(returned.equals(caught.getCause()),caught.toString());
        }
    }

}

