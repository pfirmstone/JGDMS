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
package org.apache.river.test.spec.jeri.basicinvocationdispatcher;

import java.util.logging.Level;

import org.apache.river.test.spec.jeri.util.FakeInboundRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationDispatcher.dispatch
 *   method when the first byte sent on the request stream is not 0.
 *
 * Test Cases
 *   This test iterates over a set of bytes.  Each byte
 *   denotes one test case and is defined by the variable:
 *      byte firstRequestByte
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeRemoteImpl
 *          -implements Remote
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
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeRemoteImpl and FakeServerCapabilities
 *     2) construct a BasicInvocationDispatcher, passing in the Collection of
 *        FakeRemoteImpl methods, FakeServerCapabilities,
 *        and null MethodConstraints and permission Class
 *     3) construct an empty FakeInboundRequest
 *     4) call BasicInvocationDispatcher.dispatch with FakeRemoteImpl,
 *        FakeInboundRequest, and an empty context
 *     5) assert FakeInboundRequest response stream returns EOF
 *     6) assert FakeInboundRequest abort method was called
 *     7) for each test case the test performs the following steps:
 *          1) construct a FakeInboundRequest, passing in firstRequestByte
 *          2) call BasicInvocationDispatcher.dispatch with FakeRemoteImpl,
 *             FakeInboundRequest, and an empty context
 *          3) assert FakeInboundRequest response stream receives 2
 *             bytes of 0x00 and then EOF
 * </pre>
 */
public class Dispatch_FirstRequestByteTest extends AbstractDispatcherTest {

    // test cases
    byte[] cases = {Byte.MIN_VALUE, -3, -2, -1, /* Now a legal version: 1,*/ 2, 3, Byte.MAX_VALUE};

    // inherit javadoc
    public void run() throws Exception {
        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": exception reading first byte");
        logger.log(Level.FINE,"");

        // initialize FakeInboundRequest
        request = new FakeInboundRequest(null);

        // call dispatch and verify the proper result
        dispatcher.dispatch(impl,request,context);
        response = request.getResponseStream();
        assertion(response.read() == -1);
        assertion(request.isAbortCalled());

        // iterate over test cases
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            byte firstRequestByte = cases[i];
            logger.log(Level.FINE,"test case " + (counter++)
                + ": " + firstRequestByte);
            logger.log(Level.FINE,"");

            // initialize FakeInboundRequest
            request = new FakeInboundRequest(0,null,firstRequestByte,0x00);

            // call dispatch and verify the proper result
            dispatcher.dispatch(impl,request,context);
            response = request.getResponseStream();
            assertion(response.read() == 0x00);
            assertion(response.read() == 0x00);
            assertion(response.read() == -1);
        }
    }

}

