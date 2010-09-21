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
package com.sun.jini.test.spec.constraint.coreconstraint;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

// java.util
import java.util.logging.Level;

// java.rmi
import java.rmi.MarshalledObject;

// Davis packages
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies that serialization for the following classes:
 *     {@link net.jini.core.constraint.ClientAuthentication}
 *     {@link net.jini.core.constraint.Confidentiality}
 *     {@link net.jini.core.constraint.Delegation}
 *     {@link net.jini.core.constraint.Integrity}
 *     {@link net.jini.core.constraint.ServerAuthentication}
 *   is guaranteed to produce instances that are comparable with ==.
 *   This test verifies == and != operators.
 *
 * Test Cases:
 *   Objects to test:
 *   - ClientAuthentication.NO,
 *   - ClientAuthentication.YES,
 *   - Confidentiality.NO,
 *   - Confidentiality.YES,
 *   - Delegation.NO,
 *   - Delegation.YES,
 *   - Integrity.NO,
 *   - Integrity.YES,
 *   - ServerAuthentication.NO,
 *   - ServerAuthentication.YES.
 *
 * Infrastructure:
 *     - {@link SerializationAdvancedTest}
 *         this file (performs actions)
 *     - {@link com.sun.jini.test.spec.constraint.coreconstraint.SerializationAdvancedTest.TestCase}
 *         the auxiliary class that describes a test case
 *     - {@link SerializationTest}
 *         is extended by this class
 *
 * Actions:
 *   Test creates the {@link net.jini.core.constraint.InvocationConstraint}
 *   objects to test.
 *   In each test case the following steps are performed:
 *   - {@link net.jini.core.constraint.InvocationConstraint} object is
 *     serialized and then deserialized; it's performed with creation
 *     of a {@link java.rmi.MarshalledObject} that contains a byte stream
 *     with the serialized representation of the
 *     {@link net.jini.core.constraint.InvocationConstraint} object given
 *     to its constructor; then {@link java.rmi.MarshalledObject#get()} method
 *     returns a new copy of the original object, as deserialized from the
 *     contained byte stream,
 *   - the obtained object is compared with another using == and != operators.
 *
 *   @see SerializationTest
 *
 * </pre>
 */
public class SerializationAdvancedTest extends SerializationTest {


    /**
     * An auxiliary class that describes a Test Case.
     */
    public class TestCase {

        /**
         * The first {@link net.jini.core.constraint.InvocationConstraint}
         * object.
         */
        private InvocationConstraint iconstraint1;

        /**
         * The second {@link net.jini.core.constraint.InvocationConstraint}
         * object.
         */
        private InvocationConstraint iconstraint2;

        /**
         * Expected result of the Test Case.
         * The possible values:
         * true  - if expected that (iconstraint1 == iconstraint2) produces true
         * false - if expected that (iconstraint1 != iconstraint2) produces true
         */
        private boolean expResult;

        /**
         * Constructor.
         *
         * @param ic1 the first
         *           {@link net.jini.core.constraint.InvocationConstraint}
         * @param ic2 the second
         *           {@link net.jini.core.constraint.InvocationConstraint}
         * @param exp the expected result of the Test Case
         */
        public TestCase(InvocationConstraint ic1, InvocationConstraint ic2,
                boolean exp) {
            iconstraint1 = ic1;
            iconstraint2 = ic2;
            expResult = exp;
        }

        /**
         * Get the first {@link net.jini.core.constraint.InvocationConstraint}
         * object of this
         * {@link com.sun.jini.test.spec.constraint.coreconstraint.SerializationAdvancedTest.TestCase}
         * object.
         *
         * @return the first InvocationConstraint object of this TestCase object
         */
        public InvocationConstraint getInvocationConstraint1() {
            return iconstraint1;
        }

        /**
         * Get the second {@link net.jini.core.constraint.InvocationConstraint}
         * object of this
         * {@link com.sun.jini.test.spec.constraint.coreconstraint.SerializationAdvancedTest.TestCase}
         * object.
         *
         * @return the second InvocationConstraint object of this TestCase object
         */
        public InvocationConstraint getInvocationConstraint2() {
            return iconstraint2;
        }

        /**
         * Get the expected result of the Test Case.
         *
         * @return the expected result of the Test Case
         */
        public boolean getExpected() {
            return expResult;
        }
    }

    /**
     * Test Cases.
     */
    public final TestCase testCases[] = {
            new TestCase(ClientAuthentication.NO,
                         ClientAuthentication.NO,
                         true),
            new TestCase(ClientAuthentication.YES,
                         ClientAuthentication.YES,
                         true),
            new TestCase(ClientAuthentication.NO,
                         ClientAuthentication.YES,
                         false),
            new TestCase(ClientAuthentication.YES,
                         ClientAuthentication.NO,
                         false),
            new TestCase(Confidentiality.NO,
                         Confidentiality.NO,
                         true),
            new TestCase(Confidentiality.YES,
                         Confidentiality.YES,
                         true),
            new TestCase(Confidentiality.NO,
                         Confidentiality.YES,
                         false),
            new TestCase(Confidentiality.YES,
                         Confidentiality.NO,
                         false),
            new TestCase(Delegation.NO,
                         Delegation.NO,
                         true),
            new TestCase(Delegation.YES,
                         Delegation.YES,
                         true),
            new TestCase(Delegation.NO,
                         Delegation.YES,
                         false),
            new TestCase(Delegation.YES,
                         Delegation.NO,
                         false),
            new TestCase(Integrity.NO,
                         Integrity.NO,
                         true),
            new TestCase(Integrity.YES,
                         Integrity.YES,
                         true),
            new TestCase(Integrity.NO,
                         Integrity.YES,
                         false),
            new TestCase(Integrity.YES,
                         Integrity.NO,
                         false),
            new TestCase(ServerAuthentication.NO,
                         ServerAuthentication.NO,
                         true),
            new TestCase(ServerAuthentication.YES,
                         ServerAuthentication.YES,
                         true),
            new TestCase(ServerAuthentication.NO,
                         ServerAuthentication.YES,
                         false),
            new TestCase(ServerAuthentication.YES,
                         ServerAuthentication.NO,
                         false)
    };

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        config = getConfig();

        for (int i = 0; i < testCases.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ Test Case #" + (i + (int) 1));

            if (!checker(testCases[i])) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }

    /**
     * This method checks that serialization for the specified
     * {@link net.jini.core.constraint.InvocationConstraint} is guaranteed to
     * produce instances that are comparable with ==.
     *
     * @return true if the returned result is equal to the expected one or
     *         false otherwise
     */
    public boolean checker(TestCase tc) {
        InvocationConstraint ic_init = tc.getInvocationConstraint1();
        InvocationConstraint ic_toCompare = tc.getInvocationConstraint2();
        InvocationConstraint ic_after = null;
        logger.log(Level.FINE,
                "Invocation Constraint before serialization:: " + ic_init);
        logger.log(Level.FINE, "serialization ...");
        logger.log(Level.FINE, "deserialization ...");

        try {
            MarshalledObject mObj = new MarshalledObject(ic_init);
            ic_after = (InvocationConstraint) mObj.get();
        } catch (Exception e) {
            logger.log(Level.FINE, e + "has been thrown while serialization or"
                    + "subsequent deserialization of " + ic_init);
            return false;
        }
        logger.log(Level.FINE,
                "Invocation Constraint after deserialization:: " + ic_after);
        logger.log(Level.FINE,
                "Invocation Constraint to compare with:: " + ic_toCompare);

        if (tc.getExpected()) {
            logger.log(Level.FINE,
                    "Expected that the Invocation Constraint are"
                    + " equivalent");
            return (ic_after == ic_toCompare);
        }
        logger.log(Level.FINE,
                "Expected that the Invocation Constraint aren't"
                + " equivalent");
        return (ic_after != ic_toCompare);
    }
}
