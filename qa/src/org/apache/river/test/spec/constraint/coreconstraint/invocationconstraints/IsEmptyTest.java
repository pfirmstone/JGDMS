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
package org.apache.river.test.spec.constraint.coreconstraint.invocationconstraints;

import java.util.logging.Level;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;

// java.util
import org.apache.river.qa.harness.Test;
import java.util.logging.Level;
import java.util.HashSet;

// Davis packages
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.Delegation;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link net.jini.core.constraint.InvocationConstraints#isEmpty()} method.
 *   The method should return true if the instance has no requirements and no
 *   preferences and false otherwise.
 *
 * Test Cases:
 *   TestCase #1
 *    InvocationConstraints object has no requirements and no
 *    preferences; it's expected that isEmpty() method return true
 *   TestCase #2
 *    InvocationConstraints object has requirements and no
 *    preferences; it's expected that isEmpty() method return false
 *   TestCase #3
 *    InvocationConstraints object has no requirements, but has
 *    preferences; it's expected that isEmpty() method return false
 *   TestCase #4
 *    InvocationConstraints object has requirements and
 *    preferences; it's expected that isEmpty() method return false
 *
 * Infrastructure:
 *     - {@link IsEmptyTest}
 *         performs actions; this file
 *     - {@link IsEmptyTest.TestCase}
 *         auxiliary class that describes a Test Case
 *
 * Actions:
 *   Test performs the following steps:
 *     - constructing {@link IsEmptyTest.TestCase} objects for all test cases;
 *     - invoking {@link IsEmptyTest.TestCase#isEmpty()} method on each created
 *       {@link IsEmptyTest.TestCase} object;
 *
 * </pre>
 */
public class IsEmptyTest extends QATestEnvironment implements Test {
    QAConfig config;


    /**
     * An auxiliary class that describes a Test Case.
     */
    public class TestCase {

        /**
         * {@link net.jini.core.constraint.InvocationConstraints} object
         */
        private InvocationConstraints obj;

        /**
         * Expected result of
         * {@link net.jini.core.constraint.InvocationConstraints#isEmpty()}
         */
        private boolean expected;

        /**
         * Constructor. Creates an instance of {@link IsEmptyTest.TestCase}
         * object.
         *
         * @param o {@link net.jini.core.constraint.InvocationConstraints}
         *          object
         * @param exp the expected result of InvocationConstraints.isEmpty()
         *            method
         */
        public TestCase(InvocationConstraints o, boolean exp) {
            obj = o;
            expected = exp;
        }

        /**
         * Perform {@link net.jini.core.constraint.InvocationConstraints#isEmpty()}
         * method on {@link net.jini.core.constraint.InvocationConstraints}
         * object of this Test Case and compare the obtained result with the
         * expected result of this Test Case.
         *
         * @return true if the result of InvocationConstraints.isEmpty() is
         *         equal to the expected one or false otherwise
         */
        public boolean isEmpty() {
            logger.log(Level.FINE, "obj.isEmpty()");
            logger.log(Level.FINE, "obj:: " + obj);
            boolean returned = obj.isEmpty();
            logger.log(Level.FINE, "Expected:: " + expected);
            logger.log(Level.FINE, "Returned:: " + returned);
            return (returned == expected);
        }
    }

    /**
     * Test Cases. The desciption of the test cases is in the class description.
     */
    public TestCase tc[] = new TestCase[4];

    /**
     * This method performs all preparations.
     * Creates {@link IsEmptyTest.TestCase} objects for all test cases specified
     * in the class description.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();
        
        // Requirements
        HashSet reqs = new HashSet();

        // Preferences
        HashSet prefs = new HashSet();

        /*
         * +++++ TestCase #1 +++++
         */
        reqs.clear();
        prefs.clear();
        tc[0] = new TestCase(new InvocationConstraints(reqs, prefs), true);

        /*
         * +++++ TestCase #2 +++++
         */
        reqs.clear();
        prefs.clear();
        reqs.add(Delegation.YES);
        tc[1] = new TestCase(new InvocationConstraints(reqs, prefs), false);

        /*
         * +++++ TestCase #3 +++++
         */
        reqs.clear();
        prefs.clear();
        prefs.add(Delegation.YES);
        tc[2] = new TestCase(new InvocationConstraints(reqs, prefs), false);

        /*
         * +++++ TestCase #4 +++++
         */
        reqs.clear();
        prefs.clear();
        reqs.add(Delegation.YES);
        prefs.add(Delegation.YES);
        tc[3] = new TestCase(new InvocationConstraints(reqs, prefs), false);
        return this;
    }

    /**
     * This method performs all test cases mentioned in the class description.
     */
    public void run() throws TestException {
        for (int i = 0; i < tc.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ TestCase #" + (i + (int) 1));

            if (!tc[i].isEmpty()) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }
}
