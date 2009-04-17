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

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.test.spec.jeri.util.Util;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequest;
import com.sun.jini.test.spec.jeri.util.FakeBasicInvocationHandler;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequestIterator;
import com.sun.jini.test.spec.jeri.util.FakeObjectEndpoint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.ServerError;
import java.rmi.ServerException;
import java.rmi.UnmarshalException;
import java.rmi.ConnectIOException;
import java.rmi.MarshalException;
import java.rmi.RemoteException;
import java.net.ProtocolException;

public abstract class AbstractInvokeTest extends QATest {
    protected ByteArrayInputStream requestStream;
    protected int counter;
    protected long methodHash;
    protected FakeOutboundRequest request;
    protected FakeOutboundRequestIterator iterator;
    protected FakeObjectEndpoint objectEndpoint;
    protected FakeBasicInvocationHandler handler;
    protected FakeInterface impl;

    interface FakeInterface {
        public void fakeMethod() throws Throwable;
    }

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
        // setup infrastructure needed by test
        counter = 1;

        request = new FakeOutboundRequest();
        iterator = new FakeOutboundRequestIterator(request);
        objectEndpoint = new FakeObjectEndpoint(iterator);
        handler = new FakeBasicInvocationHandler(
            objectEndpoint,      // objectEndpoint
            null);               // serverConstraints

        impl = (FakeInterface) Proxy.newProxyInstance(
            FakeInterface.class.getClassLoader(),
            new Class[] { FakeInterface.class },
            handler);

        methodHash = Util.computeMethodHash(
            FakeInterface.class.getMethod("fakeMethod", null));
    }

    // inherit javadoc
    public void tearDown() {
    }

    /**
     * Verify that an exception thrown while communicating a remote call
     * was properly wrapped and re-thrown.  This method returns
     * normally if the correct exception was thrown to this instance; otherwise
     * a <code>TestException</code> is thrown.
     *
     * @param marshalMethodInvoked true if BIH.marshalMethod was invoked
     * @param deliveryStatus the expected return value of 
     *        OutboundRequest.getDeliveryStatus
     * @param protocolMismatch protocol version problem was induced by test
     * @param executeCallInvoked true if BIH.executeCall was invoked
     * @param thrown the test controlled exception thrown during remote call
     * @param caught the exception caught during remote call invocation
     * @throws TestException if the returned exception is the wrong type
     */
    protected void check(
        boolean marshalMethodInvoked,
        boolean deliveryStatus, 
        boolean protocolMismatch, 
        boolean executeCallInvoked, 
        Throwable thrown, Throwable caught) throws TestException
    {
        if (thrown instanceof RuntimeException || thrown instanceof Error) {
            assertion(thrown.equals(caught),caught.toString());
            return;
        }

        if (thrown instanceof ClassNotFoundException) {
            assertion(caught instanceof java.rmi.UnmarshalException,
                    caught.toString());
            assertion(thrown.equals(caught.getCause()),caught.toString());
            return;
        }

        assertion(thrown instanceof IOException, thrown.toString());
        if (!marshalMethodInvoked || !deliveryStatus || protocolMismatch) {
            if (thrown instanceof java.net.UnknownHostException) {
                assertion(caught instanceof java.rmi.UnknownHostException,
                    caught.toString());
                assertion(thrown.equals(caught.getCause()),caught.toString());
            } else if (thrown instanceof java.net.ConnectException) {
                assertion(caught instanceof java.rmi.ConnectException,
                    caught.toString());
                assertion(thrown.equals(caught.getCause()),caught.toString());
            } else {
                assertion(caught instanceof ConnectIOException,
                    caught.toString());
                assertion(thrown.getClass() == caught.getCause().getClass(),
                    caught.toString());
            }
        } else if (!executeCallInvoked) {
            assertion(caught instanceof MarshalException,caught.toString());
            assertion(thrown.equals(caught.getCause()),caught.toString());
        } else {
            assertion(caught instanceof java.rmi.UnmarshalException,
                caught.toString());
            assertion(thrown.equals(caught.getCause()),caught.toString());
        }
    }

}
