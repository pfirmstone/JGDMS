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
package org.apache.river.test.spec.jeri.util;

import net.jini.jeri.OutboundRequest;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.io.MarshalOutputStream;

import org.apache.river.test.spec.jeri.util.Util;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.Collection;
import java.util.ArrayList;

/**
 * A fake implementation of the <code>OutboundRequest</code>
 * interface.
 */
public class FakeOutboundRequest implements OutboundRequest {

    Logger logger = Logger.getLogger("org.apache.river.qa.harness.test");

    InputStream responseInput;
    OutputStream requestOutput;
    InvocationConstraints unfulfilledConstraintsReturn;
    Throwable unfulfilledConstraintsException;
    boolean deliveryStatus;
    boolean abortCalled;

    /**
     * Constructs a FakeOutboundRequest.
     */
    public FakeOutboundRequest() {
        init();
    }

    public void init() {
        requestOutput = new ByteArrayOutputStream();
        responseInput = new ByteArrayInputStream(new byte[] {});
        unfulfilledConstraintsReturn = InvocationConstraints.EMPTY;
        deliveryStatus = false;
        abortCalled = false;
    }

    /***
     *** The following methods configure the return values from the
     *** various methods.  Mapping from these "set" methods to
     *** the corresponding method should be obvious.
     ***/

    public void setDeliveryStatusReturn(boolean ds) {
        deliveryStatus = ds;
    }
    public void setUnfulfilledConstraintsReturn(InvocationConstraints ucr) {
        unfulfilledConstraintsReturn = ucr;
    }
    public void setUnfulfilledConstraintsException(Throwable uce) {
        unfulfilledConstraintsException = uce;
    }
    public void setRequestOutputStream(OutputStream os) {
        requestOutput = os;
    }
    public void setResponseInputStream(InputStream bais) {
        responseInput = bais;
    }
    public void setResponseInputStream(int firstResponseByte, 
        Object returnValue) throws IOException
    {
        logger.entering(getClass().getName(),"setResponseInputStream("
            + "firstResponseByte:" + firstResponseByte
            + ",returnValue:" + returnValue + ")");

        ByteArrayOutputStream responseOutput = new ByteArrayOutputStream();

        // write status byte
        responseOutput.write(firstResponseByte);
 
        // wrap responseOutput stream in a MarshalOutputStream
        MarshalOutputStream marshalledResponse = new MarshalOutputStream(
            responseOutput, new ArrayList());
 
        // write returnValue to response stream
        Util.marshalValue(returnValue, marshalledResponse);

        marshalledResponse.close();
 
        responseInput = new ByteArrayInputStream(responseOutput.toByteArray());
    }


    /**
     * No-op implementation of interface method.
     */
    public void populateContext(Collection context) {
        logger.entering(getClass().getName(),"populateContext");
    }

    /**
     * Implementation of interface method.
     *
     * return unfulfilledConstraintsReturn if
     *        unfulfilledConstraintsException is null
     * throw unfulfilledConstraintsException if 
     *       unfulfilledConstraintsException is not null
     * throw AssertionError if unfulfilledConstraintsException is not null and
     *        unfulfilledConstraintsException is not instanceof 
     *        RuntimeException or Error
     */
    public InvocationConstraints getUnfulfilledConstraints() {
        logger.entering(getClass().getName(),"getUnfulfilledConstraints");
        if (unfulfilledConstraintsException != null) {
            if (unfulfilledConstraintsException instanceof RuntimeException) {
                throw (RuntimeException) unfulfilledConstraintsException;
            } else if (unfulfilledConstraintsException instanceof Error) {
                throw (Error) unfulfilledConstraintsException;
            } else {
                throw new AssertionError();
            }
        }
        return unfulfilledConstraintsReturn;
    }

    /**
     * No-op implementation of interface method.
     */
    public void abort() {
        logger.entering(getClass().getName(),"abort");
        try {
            getRequestOutputStream().close();
        } catch (IOException ignore) {}
        try {
            getResponseInputStream().close();
        } catch (IOException ignore) {}
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
     * @return the delivery status passed to constructor or set using
     *         accessor method
     */
    public boolean getDeliveryStatus() {
        logger.entering(getClass().getName(),"getDeliveryStatus");
        return deliveryStatus;
    }

    /**
     * Implementation of interface method.
     *
     * @return the request Output stream
     */
    public OutputStream getRequestOutputStream() {
        logger.entering(getClass().getName(),"getRequestOutputStream");
        return requestOutput;
    }

    /**
     * Implementation of interface method.
     * 
     * @return the response Input stream
     */
    public InputStream getResponseInputStream() {
        logger.entering(getClass().getName(),"getResponseInputStream");
        return responseInput;
    }

    /**
     * Helper method that returns the request output stream as
     * a ByteArrayInputStream.
     *
     * @return the request Output stream dumped to a ByteArrayInputStream.
     */
    public ByteArrayInputStream getRequestStream() {
        logger.entering(getClass().getName(),"getRequestStream");
        return (requestOutput == null ? null :
            new ByteArrayInputStream(
                ((ByteArrayOutputStream)requestOutput).toByteArray()));
    }

}

