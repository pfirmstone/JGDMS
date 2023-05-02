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
package org.apache.river.test.spec.jeri.basicinvocationhandler;

import java.util.logging.Level;

import org.apache.river.qa.harness.TestException;

import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.Confidentiality;
import net.jini.io.UnsupportedConstraintException;
import net.jini.io.context.IntegrityEnforcement;

import java.util.logging.Level;
import java.util.ArrayList;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of the BasicInvocationHandler.invoke
 *   method for various constraints returned by
 *   OutboundRequest.getUnfulfilledConstraints
 *
 * Test Cases
 *   This test iterates over a set of InvocationConstraints.  Each 
 *   InvocationConstraints denotes one test case and is defined by 
 *   the variable:
 *      InvocationConstraints getUnfulfilledConstraintsReturn
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface
 *          -an interface which declares one method that throws Throwable
 *     2) FakeObjectEndpoint
 *          -newCall returns OutboundRequestIterator passed to constructor
 *          -executeCall method throws AssertionError
 *     3) FakeOutboundRequestIterator
 *          -hasNext method returns true on first call and false after that
 *          -next method returns OutboundRequest passed to constructor
 *           and throws NoSuchElementException if called more than once
 *     4) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus method returns false
 *          -getRequestOutputStream method returns a ByteArrayOutputStream
 *          -getResponseInputStream method throws AssertionError
 *          -getUnfulfilledConstraints method
 *           returns getUnfulfilledConstraintsReturn
 *          -populateContext method does nothing
 *     5) FakeBasicInvocationHandler
 *          -subclasses BasicInvocationHandler
 *          -getContext method returns context passed to
 *           createMarshalOutputStream
 *
 * Actions
 *   For each test case the test performs the following steps:
 *     1) construct a FakeOutboundRequest
 *     2) construct a FakeOutboundRequestIterator, passing in 
 *        FakeOutboundRequest
 *     3) construct a FakeObjectEndpoint, passing in FakeOutboundRequestIterator
 *     4) construct a FakeBasicInvocationHandler, passing in FakeObjectEndpoint
 *        and null MethodConstraints
 *     5) create a dynamic proxy for the FakeInterface using the
 *        FakeBasicInvocationHandler
 *     6) invoke FakeOutboundRequest.setGetUnfulfilledConstraintsReturn,
 *        passing in getUnfulfilledConstraintsReturn
 *     7) invoke the method on the dynamic proxy
 *     8) assert correct integrity byte is written to the output stream and
 *        correct IntegrityEnforcement element is added to the context,
 *        or correct exception is thrown
 * </pre>
 */
public class Invoke_IntegrityTest extends AbstractInvokeTest {

    // normal integrity test cases
    InvocationConstraint[][][] normalIntegrityCases = {
        { {Integrity.YES}, {} },
        { {Integrity.YES}, {Confidentiality.YES} },
        { {Integrity.YES}, {Integrity.NO} },
        { {Integrity.YES,Integrity.NO}, {} },
        { {Integrity.NO}, {Integrity.YES} },
        { {}, {Integrity.YES} },
        { {}, {Integrity.YES,Confidentiality.YES} },
        { {}, {Integrity.YES,Integrity.NO} }
    };

    // normal non-integrity test cases
    InvocationConstraint[][][] normalNoIntegrityCases = {
        { {Integrity.NO}, {} },
        { {}, {Integrity.NO} },
        { {}, {Confidentiality.YES} },
        { {}, {Integrity.NO,Confidentiality.YES} }
    };

    // exceptional test cases
    InvocationConstraint[][][] exceptionCases = {
        { {Confidentiality.YES}, {} },
        { {Confidentiality.YES,Integrity.YES}, {} },
        { {Confidentiality.YES}, {Integrity.YES} },
        { {Integrity.NO,Confidentiality.YES}, {} }
    };

    // inherit javadoc
    public void run() throws Exception {
        // iterate over normalIntegrityCases
        for (int i = 0; i < normalIntegrityCases.length; i++) {
            logger.log(Level.FINE,"=================================");
            InvocationConstraints constraints = new InvocationConstraints(
                normalIntegrityCases[i][0], normalIntegrityCases[i][1]);
            logger.log(Level.FINE,"integrity test case " + (counter++)
                + ": constraints:" + constraints);
            logger.log(Level.FINE,"");

            iterator.init();
            request.setUnfulfilledConstraintsReturn(constraints);

            try {
                impl.fakeMethod();
                assertion(false);
            } catch (Throwable ignore) {
            }
            requestStream = request.getRequestStream();
	    int version = requestStream.read();
	    System.out.println("Version: " + version);
            assertion(version == 0x00);
            assertion(requestStream.read() == 0x01);

            checkIntegrityEnforcement(true);
        }

        // iterate over normalNoIntegrityCases
        for (int i = 0; i < normalNoIntegrityCases.length; i++) {
            logger.log(Level.FINE,"=================================");
            InvocationConstraints constraints = new InvocationConstraints(
                normalNoIntegrityCases[i][0], normalNoIntegrityCases[i][1]);
            logger.log(Level.FINE,"no integrity test case " + (counter++)
                + ": constraints:" + constraints);
            logger.log(Level.FINE,"");

            iterator.init();
            request.setUnfulfilledConstraintsReturn(constraints);

            try {
                impl.fakeMethod();
                assertion(false);
            } catch (Throwable ignore) {
            }
            requestStream = request.getRequestStream();
	    int version = requestStream.read();
	    System.out.println("Version: " + version);
            assertion(version == 0x00);
            assertion(requestStream.read() == 0x00);

            checkIntegrityEnforcement(false);
        }

        // iterate over exceptionCases
        for (int i = 0; i < exceptionCases.length; i++) {
            logger.log(Level.FINE,"=================================");
            InvocationConstraints constraints = new InvocationConstraints(
                exceptionCases[i][0], exceptionCases[i][1]);
            logger.log(Level.FINE,"exception test case " + (counter++)
                + ": constraints:" + constraints);
            logger.log(Level.FINE,"");

            iterator.init();
            request.setUnfulfilledConstraintsReturn(constraints);

            try {
                impl.fakeMethod();
                assertion(false);
            } catch (Throwable t) {
                check(false,false,false,false,
                    new UnsupportedConstraintException(""),t);
            }
            requestStream = request.getRequestStream();
            assertion(requestStream.read() == -1);
        }
    }

    /**
     * Asserts that the correct IntegrityEnforcement element was added
     * to the BasicInvocationHandler's context.
     */
    private void checkIntegrityEnforcement(boolean expected)
        throws TestException
    {
        ArrayList context = new ArrayList(handler.getContext());
        Object contextElement = context.get(0);
        assertion(contextElement instanceof IntegrityEnforcement);
        boolean actual = 
            ((IntegrityEnforcement)contextElement).integrityEnforced();
        assertion(expected == actual);
    }
}

