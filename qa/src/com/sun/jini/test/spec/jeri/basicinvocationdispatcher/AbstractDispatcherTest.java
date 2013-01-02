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

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.io.MarshalInputStream;

import com.sun.jini.test.spec.jeri.util.FakeInboundRequest;
import com.sun.jini.test.spec.jeri.util.FakeBasicInvocationDispatcher;
import com.sun.jini.test.spec.jeri.util.FakeServerCapabilities;
import com.sun.jini.test.spec.jeri.util.FakeRemoteImpl;
import com.sun.jini.test.spec.jeri.util.FakeArgument;
import com.sun.jini.test.spec.jeri.util.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.lang.reflect.Method;
import java.rmi.ServerError;
import java.rmi.ServerException;
import java.rmi.UnmarshalException;
import java.rmi.RemoteException;

public abstract class AbstractDispatcherTest extends QATestEnvironment implements Test {

    protected int counter;
    protected ByteArrayInputStream response;
    protected ArrayList context;
    protected FakeInboundRequest request;
    protected FakeRemoteImpl impl;
    protected long methodHash;
    protected Method fakeMethod;
    protected FakeBasicInvocationDispatcher dispatcher;
    protected Object[] nullArgs;

    public Test construct(QAConfig sysConfig) throws Exception {
        // construct infrastructure needed by test
        counter = 1;
        context = new ArrayList();

        nullArgs = new Object[] {null};

        impl = new FakeRemoteImpl();
        fakeMethod = impl.getClass().getMethod(
            "fakeMethod",
            new Class[] {Object.class});
        ArrayList methods = new ArrayList();
        methods.add(fakeMethod);

        methodHash = Util.computeMethodHash(fakeMethod);

        dispatcher = new FakeBasicInvocationDispatcher(
                methods,                          //methods collection
                new FakeServerCapabilities(null), //serverCaps
                null,                             //serverConstraints
                null,                             //permClass
                null);                            //classLoader
        return this;
    }

    public void tearDown() {
    }

    /**
     * Verify that an exception thrown while unmarshalling
     * was properly wrapped and re-thrown.  This method returns
     * normally if the correct exception was thrown to this instance; otherwise
     * a <code>TestException</code> is thrown.
     *
     * @param thrown the exception thrown while unmarshalling
     * @param response the response stream containing the returned exception
     * @throws NullPointerException if <code>response</code> is null
     * @throws TestException if the returned exception is the wrong type
     */
    protected void checkUnmarshallingException(Throwable thrown, 
        ByteArrayInputStream response) throws TestException
    {
        Throwable caught = null;
        try {
            MarshalInputStream marshalledResponse = new MarshalInputStream(
                response, null, false, null, new ArrayList());
            caught = (Throwable) marshalledResponse.readObject();
            assertion(marshalledResponse.read() == -1);
        } catch(Throwable t) {
            assertion(false,t.toString());
        }

        if (thrown instanceof RuntimeException) {
            assertion(thrown.getClass() == caught.getClass(),caught.toString());
        } else if (thrown instanceof Error) {
            assertion(caught instanceof ServerError,caught.toString());
            assertion(thrown.getClass() == caught.getCause().getClass(),
                caught.toString());
        } else if (thrown instanceof IOException ||
                   thrown instanceof ClassNotFoundException ||
                   thrown instanceof NoSuchMethodException)
        {
            assertion(caught instanceof ServerException,caught.toString());
            assertion(caught.getCause() instanceof UnmarshalException,
                caught.toString());
            assertion(thrown.getClass() ==
                caught.getCause().getCause().getClass(),caught.toString());
        } else {
            assertion(false,caught.toString());
        }
    }

    /**
     * Verify that an exception thrown after unmarshalling successfully
     * completed was properly wrapped and re-thrown.  This method returns
     * normally if the correct exception was thrown to this instance; otherwise
     * a <code>TestException</code> is thrown.
     *
     * @param thrown the exception thrown after unmarshalling complete
     * @param response the response stream containing the returned exception
     * @throws NullPointerException if <code>response</code> is null
     * @throws TestException if the returned exception is the wrong type
     */
    void checkPostUnmarshallingException(Throwable thrown, 
        ByteArrayInputStream response) throws TestException
    {
        Throwable caught = null;
        try {
            MarshalInputStream marshalledResponse = new MarshalInputStream(
                response, null, false, null, new ArrayList());
            caught = (Throwable) marshalledResponse.readObject();
            assertion(marshalledResponse.read() == -1);
        } catch(Throwable t) {
            assertion(false,t.toString());
        }

        if (thrown instanceof Error) {
            assertion(caught instanceof ServerError,caught.toString());
            assertion(thrown.getClass() == caught.getCause().getClass(),
                caught.toString());
        } else if (thrown instanceof RemoteException) {
            assertion(caught instanceof ServerException,caught.toString());
            assertion(thrown.getClass() == caught.getCause().getClass(),
                caught.toString());
        } else {
            assertion(thrown.getClass() == caught.getClass(),caught.toString());
        }
    }

}
