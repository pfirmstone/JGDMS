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
package org.apache.river.test.spec.jeri.basicobjectendpoint;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.id.UuidFactory;
import net.jini.id.Uuid;
import net.jini.core.constraint.InvocationConstraints;

import org.apache.river.test.spec.jeri.util.FakeEndpoint;
import org.apache.river.test.spec.jeri.util.FakeOutputStream;
import org.apache.river.test.spec.jeri.util.FakeOutboundRequest;
import org.apache.river.test.spec.jeri.util.FakeOutboundRequestIterator;

import java.util.logging.Level;
import java.lang.reflect.UndeclaredThrowableException;
import java.io.IOException;
import java.rmi.UnknownHostException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.rmi.ConnectIOException;
//import java.net.UnknownHostException;
//import java.net.ConnectException;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of BasicObjectEndpoint.newCall
 *   when null InvocationConstraints are passed in.
 *
 *   This test verifies the behavior of the OutboundRequestIterator
 *   returned from the BasicObjectEndpoint newCall method when the
 *   iterator's next method throws an exception or an exception
 *   occurs writing to the request output stream.
 *
 * Test Cases
 *   This test iterates over a set of exceptions.  Each exception
 *   denotes one test case and is defined by the variable:
 *      Throwable nextException
 *   where nextException is restricted to instances of:
 *      IOException
 *      RuntimeException
 *      Error
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeEndpoint
 *          -implements Endpoint
 *          -newRequest method returns OutboundRequestIterator
 *           passed to constructor
 *     2) FakeOutboundRequestIterator
 *          -hasNext method returns true on first call and false after that
 *          -next method returns OutboundRequest passed to constructor
 *           and throws NoSuchElementException if called more than once
 *     3) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus method returns false
 *          -getRequestOutputStream method returns a ByteArrayOutputStream
 *          -getResponseInputStream method throws AssertionError
 *          -getUnfulfilledConstraints method return InvocationConstraints.EMPTY
 *          -populateContext method does nothing
 *     4) FakeOutputStream
 *          -write methods throws exception passed to constructor
 *
 * Actions
 *   The test performs the following steps:
 *       1) construct a BasicObjectEndpoint, passing in a FakeEndpoint
 *          and a Uuid
 *       2) call BasicObjectEndpoint.newCall(null) and assert that
 *          NullPointerException is thrown
 *   For each test case the test performs the following steps:
 *     OutboundRequestIterator.next exception
 *       3) construct a FakeOutboundRequestIterator, passing in nextException
 *       4) construct a FakeEndpoint, passing in FakeOutboundRequestIterator
 *       5) construct a BasicObjectEndpoint, passing in the FakeEndpoint
 *          and a Uuid
 *       6) call BasicObjectEndpoint.newCall method
 *       7) call next on the obtained OutboundRequestIterator
 *       8) assert nextException is thrown directly
 *     Uuid.write exception
 *       9) construct a FakeOutputStream, passing in nextException
 *      10) construct a FakeOutboundRequest, passing in FakeOutputStream
 *      11) construct a FakeOutboundRequestIterator,
 *          passing in FakeOutboundRequest
 *      12) construct a FakeEndpoint, passing in the FakeOutboundRequestIterator
 *      13) construct a BasicObjectEndpoint, passing in the FakeEndpoint
 *          and a Uuid
 *      14) call BasicObjectEndpoint.newCall method
 *      15) call next on the obtained OutboundRequestIterator
 *      16) assert nextException is thrown directly
 * </pre>
 */
public class NewCall_ExceptionTest extends QATestEnvironment implements Test {

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
        new AssertionError()                    //Error subclass
    };

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
        return this;
    }

    // inherit javadoc
    public void run() throws Exception {
        int counter = 1;
        Uuid uuid;
        FakeEndpoint endpoint;
        FakeOutboundRequestIterator fakeIterator;
        OutboundRequestIterator iterator;
        BasicObjectEndpoint boe;
        FakeOutputStream fos;
        FakeOutboundRequest request;

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case " + (counter++)
            + ": newCall with null InvocationConstraints");
        logger.log(Level.FINE,"");

        uuid = UuidFactory.create(1,2);
        endpoint = new FakeEndpoint(new FakeOutboundRequestIterator(null));
        boe = new BasicObjectEndpoint(endpoint,uuid,false);
        try {
            boe.newCall(null);
            throw new AssertionError("newCall(null) should fail");
        } catch (NullPointerException ignore) {}

        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Throwable nextException = cases[i];
            logger.log(Level.FINE,"test case " + (counter++)
                + ": nextException:" + nextException);
            logger.log(Level.FINE,"");

            // Test Case: OutboundRequestIterator.next throws exception

            uuid = UuidFactory.create(1,2);
            fakeIterator = new FakeOutboundRequestIterator(null);
            fakeIterator.setNextException(nextException);
            endpoint = new FakeEndpoint(fakeIterator);
            boe = new BasicObjectEndpoint(endpoint,uuid,false);

            iterator = boe.newCall(InvocationConstraints.EMPTY);
            try {
                iterator.next();
                throw new AssertionError("next() should fail");
            } catch (Throwable caught) {
                assertion(nextException.equals(caught),
                    caught.toString());
            }

            // Test Case: Uuid.write throws exception

            fos = new FakeOutputStream(nextException);
            request = new FakeOutboundRequest();
            request.setRequestOutputStream(fos);
            fakeIterator = new FakeOutboundRequestIterator(request);
            endpoint = new FakeEndpoint(fakeIterator);
            boe = new BasicObjectEndpoint(endpoint,uuid,false);

            iterator = boe.newCall(InvocationConstraints.EMPTY);
            try {
                iterator.next();
                throw new AssertionError("next() should fail");
            } catch (Throwable caught) {
                assertion(nextException.equals(caught),
                    caught.toString());
            }

        }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

