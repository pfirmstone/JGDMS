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
package com.sun.jini.test.spec.jeri.basicobjectendpoint;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

import net.jini.jeri.OutboundRequest;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.id.UuidFactory;
import net.jini.id.Uuid;
import net.jini.core.constraint.InvocationConstraints;

import com.sun.jini.test.spec.jeri.util.FakeEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeInputStream;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequest;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequestIterator;

import java.util.logging.Level;
import java.io.IOException;
import java.io.EOFException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicObjectEndpoint
 *   executeCall method when this method should throw an exception.
 * 
 * Test Cases
 *   Test cases are defined by the Actions section below.
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeEndpoint
 *          -implements Endpoint
 *          -newRequest method returns OutboundRequestIterator
 *           passed to constructor
 *     2) FakeOutboundRequestIterator
 *          -next method returns OutboundRequest passed to constructor
 *           and throws NoSuchElementException if called more than once
 *          -hasNext method returns true on first call and false after that
 *     3) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus method returns true
 *          -getRequestOutputStream returns a ByteArrayOutputStream 
 *          -getResponseInputStream method returns the FakeInputStream passed
 *           to the constructor
 *     4) FakeInputStream
 *          -extends InputStream
 *          -read method throws exception passed to constructor, or if null,
 *           returns the byte passed to the constructor
 * 
 * Actions
 *   The test performs the following steps:
 *     OutboundRequest.getResponseInputStream.read throws IOException
 *       1) construct a FakeInputStream, passing in IOException
 *       2) construct a FakeOutboundRequest, passing in FakeInputStream
 *       3) construct a FakeOutboundRequestIterator, u
 *          passing in FakeOutboundRequest
 *       4) construct a FakeEndpoint, passing in the FakeOutboundRequestIterator
 *       5) construct a BasicObjectEndpoint, passing in the FakeEndpoint
 *          and a Uuid
 *       6) call BasicObjectEndpoint.newCall.next method
 *       7) call BasicObjectEndpoint.executeCall method, passing 
 *          in obtained OutboundRequest
 *       8) assert IOException is thrown directly
 *     OutboundRequest.getResponseInputStream.read returns -1
 *       9) construct a FakeInputStream, passing in -1
 *      10) repeat steps 2 to 7 above
 *      11) assert EOFException is thrown directly
 * </pre>
 */
public class ExecuteCall_ExceptionTest extends QATestEnvironment implements Test {

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        int counter = 1;
        OutboundRequest request;

        FakeOutboundRequest fakeRequest = new FakeOutboundRequest();
        FakeOutboundRequestIterator iterator = 
            new FakeOutboundRequestIterator(fakeRequest);
        FakeEndpoint ep = new FakeEndpoint(iterator);
        Uuid uuid = UuidFactory.create(1,2);
        BasicObjectEndpoint boe = new BasicObjectEndpoint(ep,uuid,false);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": reading response input stream throws IOException");
        logger.log(Level.FINE,"");

        // OutboundRequest.getResponseInputStream.read throws IOException

        fakeRequest.setResponseInputStream(
            new FakeInputStream(new IOException(),0));
        request = boe.newCall(InvocationConstraints.EMPTY).next();
        try {
            boe.executeCall(request);
            throw new AssertionError("executeCall() should fail");
        } catch (IOException ignore) {
        }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": reading response input stream returns -1 (EOF)");
        logger.log(Level.FINE,"");

        // OutboundRequest.getResponseInputStream.read returns -1

        iterator.init();
        fakeRequest.setResponseInputStream(new FakeInputStream(null,-1));

        request = boe.newCall(InvocationConstraints.EMPTY).next();
        try {
            boe.executeCall(request);
            throw new AssertionError("executeCall() should fail");
        } catch (EOFException ignore) {
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

