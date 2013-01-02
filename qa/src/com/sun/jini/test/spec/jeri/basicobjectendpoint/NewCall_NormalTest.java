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

import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.id.UuidFactory;
import net.jini.id.Uuid;
import net.jini.core.constraint.InvocationConstraints;

import com.sun.jini.test.spec.jeri.util.FakeEndpoint;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequest;
import com.sun.jini.test.spec.jeri.util.FakeOutboundRequestIterator;

import java.util.logging.Level;
import java.util.NoSuchElementException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the OutboundRequestIterator
 *   returned from the BasicObjectEndpoint newCall method.
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
 *          -next method throws exception passed to constructor, or if null,
 *           returns OutboundRequest passed to constructor
 *           and throws NoSuchElementException if called more than once
 *          -hasNext method returns true on first call and false after that
 *     3) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus method returns true
 *          -getRequestOutputStream returns a ByteArrayOutputStream 
 *          -getResponseInputStream method throws AssertionError
 * 
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeOutboundRequest
 *     2) construct a FakeOutboundRequestIterator, passing in FakeOutboundRequest
 *     3) construct a FakeEndpoint, passing in the FakeOutboundRequestIterator
 *     4) construct a BasicObjectEndpoint, passing in the FakeEndpoint
 *        and a Uuid
 *     5) call BasicObjectEndpoint.newCall method
 *     6) assert hasNext on the obtained OutboundRequestIterator returns true
 *     7) call next on the obtained OutboundRequestIterator
 *     8) assert return value is the FakeOutboundRequest
 *     9) assert FakeOutboundRequest's request output stream contains the
 *        serialized Uuid
 *    10) assert hasNext on the obtained OutboundRequestIterator returns false
 *    11) assert next on the obtained OutboundRequestIterator 
 *        throws NoSuchElementException
 * </pre>
 */
public class NewCall_NormalTest extends QATestEnvironment implements Test {

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        int counter = 1;
        Uuid uuid = UuidFactory.create(1,2);
        FakeOutboundRequest request = new FakeOutboundRequest();
        BasicObjectEndpoint boe = new BasicObjectEndpoint(
             new FakeEndpoint(new FakeOutboundRequestIterator(request)),
             uuid,false);
        OutboundRequestIterator iterator = 
            boe.newCall(InvocationConstraints.EMPTY);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": hasNext returns true");
        logger.log(Level.FINE,"");

        assertion(iterator.hasNext() == true);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": next returns correct OutboundRequest");
        logger.log(Level.FINE,"");

        assertion(iterator.next() == request);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": uuid writen to OutboundRequest output stream");
        logger.log(Level.FINE,"");

        Uuid writtenUuid = UuidFactory.read(request.getRequestStream());
        assertion(uuid.equals(writtenUuid));

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": hasNext returns false");
        logger.log(Level.FINE,"");

        assertion(iterator.hasNext() == false);

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": next throws NoSuchElementException");
        logger.log(Level.FINE,"");

        try {
            iterator.next();
            throw new AssertionError("next() should fail");
        } catch (NoSuchElementException ignore) {
        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

