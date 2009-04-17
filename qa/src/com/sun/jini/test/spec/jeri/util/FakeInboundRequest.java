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
package com.sun.jini.test.spec.jeri.util;

import net.jini.jeri.InboundRequest;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.MarshalOutputStream;

import com.sun.jini.test.spec.jeri.util.Util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Collection;
import java.util.ArrayList;

/**
 * A fake implementation of the <code>InboundRequest</code>
 * interface.
 */
public class FakeInboundRequest implements InboundRequest {

    ByteArrayInputStream requestInput;
    ByteArrayOutputStream responseOutput = new ByteArrayOutputStream();
    Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");
    boolean abortCalled = false;
    Throwable checkConstraintsException;
    InvocationConstraints checkConstraintsReturn;

    /**
     * Constructs a FakeInboundRequest.
     */
    public FakeInboundRequest(ByteArrayInputStream request) {
        requestInput = request;
    }

    /**
     * Constructs a FakeInboundRequest.
     *
     * @param methodHash the hash of the method that is being called
     * @param methodArgs the arguments that the method is called with
     * @param firstRequestByte first byte to send on the request stream
     * @param secondRequestByte second byte to send on the request stream
     */
    public FakeInboundRequest(long methodHash, Object[] methodArgs, 
        int firstRequestByte, int secondRequestByte) throws IOException
    {
        logger.entering(getClass().getName(),"constructor("
            + "methodHash:" + methodHash + ",methodArgs:" + methodArgs 
            + ",firstRequestByte:" + firstRequestByte
            + ",secondRequestByte:" + secondRequestByte + ")");

        ByteArrayOutputStream requestOutput = new ByteArrayOutputStream();

        // write protocol version
        requestOutput.write(firstRequestByte);

        // write integrity yes/no
        requestOutput.write(secondRequestByte);

        // wrap requestOutput stream in a MarshalOutputStream
        MarshalOutputStream marshalledRequest = new MarshalOutputStream(
            requestOutput, new ArrayList());

        // write method hash
        marshalledRequest.writeLong(methodHash);

        // write method args
        if (methodArgs != null) {
            for (int i = 0; i < methodArgs.length; i++) {
                Util.marshalValue(methodArgs[i],marshalledRequest);
            }
        }
        marshalledRequest.close();

        requestInput = new ByteArrayInputStream(requestOutput.toByteArray());
    }

    public void setCheckConstraintsException(Throwable t) {
        checkConstraintsException = t;
    }
    public void setCheckConstraintsReturn(InvocationConstraints ic) {
        checkConstraintsReturn = ic;
    }

    /**
     * No-op implementation of interface method.
     */
    public void checkPermissions() {
        logger.entering(getClass().getName(),"checkPermissions");
    }

    /**
     * No-op implementation of interface method. If checkConstraintsException
     * is null and checkConstraintsReturn is null, 
     * InvocationConstraints.EMPTY is returned.
     */
    public InvocationConstraints checkConstraints(
        InvocationConstraints constraints) 
        throws UnsupportedConstraintException
    {
        logger.entering(getClass().getName(),"checkConstraints");
        if (checkConstraintsException != null) {
            if (checkConstraintsException instanceof 
                UnsupportedConstraintException) 
            {
                throw (UnsupportedConstraintException)checkConstraintsException;
            } else if (checkConstraintsException instanceof RuntimeException) {
                throw (RuntimeException) checkConstraintsException;
            } else if (checkConstraintsException instanceof Error) {
                throw (Error) checkConstraintsException;
            }
        }
        return (checkConstraintsReturn == null ? InvocationConstraints.EMPTY :
                                                 checkConstraintsReturn );
    }

    /**
     * No-op implementation of interface method.
     */
    public void populateContext(Collection context) {
        logger.entering(getClass().getName(),"populateContext");
    }

    /**
     * No-op implementation of interface method.
     */
    public void abort() {
        logger.entering(getClass().getName(),"abort");
        try { 
            getRequestInputStream().close();
        } catch (Throwable ignore) {}
        try { 
            getResponseOutputStream().close();
        } catch (Throwable ignore) {}
        abortCalled = true;
    }

    /**
     * Indicated whether or not the abort() method was called.
     */
    public boolean isAbortCalled() {
        return abortCalled;
    }

    /**
     * Implementation of interface method.
     * 
     * @return the request input stream
     */
    public InputStream getRequestInputStream() {
        logger.entering(getClass().getName(),"getRequestInputStream");
        return requestInput;
    }

    /**
     * Implementation of interface method.
     * 
     * @return the reponse Output stream
     */
    public OutputStream getResponseOutputStream() {
        logger.entering(getClass().getName(),"getResponseOutputStream");
        return responseOutput;
    }

    /**
     * Helper method that returns the response output stream as
     * a ByteArrayInputStream.
     * 
     * @return the reponse Output stream dumped to a ByteArrayInputStream.
     */
    public ByteArrayInputStream getResponseStream() {
        logger.entering(getClass().getName(),"getResponseStream");
        return (responseOutput == null ? null :
            new ByteArrayInputStream(responseOutput.toByteArray()));
    }

}
