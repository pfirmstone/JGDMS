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
package com.sun.jini.test.spec.constraint.coreconstraint.invocationconstraints;

import java.util.logging.Level;
// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATest;

// java.util
import java.util.logging.Level;
import java.util.HashSet;

// Davis packages
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link net.jini.core.constraint.InvocationConstraints#combine(InvocationConstraints,InvocationConstraints)}
 *   method.
 *   The method should return an instance of
 *   {@link net.jini.core.constraint.InvocationConstraints} that has all of
 *   the requirements from each non-null argument added as requirements and
 *   has all of the preferences from each non-null argument added as
 *   preferences.
 *   Duplicate requirements, duplicate preferences, and preferences that are
 *   duplicates of requirements are all removed.
 *
 * Test Cases:
 *   TestCase #1
 *    both arguments are null; it's expected that the returned
 *    InvocationConstraints object is empty (has no requirements and no
 *    preferences);
 *   TestCase #2
 *    both arguments are empty InvocationConstraints objects;
 *    it's expected that the returned InvocationConstraints object is empty;
 *   TestCase #3
 *    the first argument is null, the second argument is empty; it's expected
 *    that the returned InvocationConstraints object is empty;
 *   TestCase #4
 *    the first argument is empty, the second argument is null; it's expected
 *    that the returned InvocationConstraints object is empty;
 *   TestCase #5
 *    the first argument is null; it's expected that the returned
 *    InvocationConstraints object is equal to the InvocationConstraints
 *    object specified as the second argument;
 *   TestCase #6
 *    the first argument is empty ; it's expected that the returned
 *    InvocationConstraints object is equal to the InvocationConstraints
 *    object specified as the second argument;
 *   TestCase #7
 *    the second argument is null; it's expected that the returned
 *    InvocationConstraints object is equal to the InvocationConstraints
 *    object specified as the first argument;
 *   TestCase #8
 *    the second argument is empty; it's expected that the returned
 *    InvocationConstraints object is equal to the InvocationConstraints
 *    object specified as the first argument;
 *   TestCase #9
 *    both arguments are non-null and non-empty InvocationConstraints objects
 *    without any duplicate requirements, preferences or preferences
 *    that are duplicates of requirements; it's expected that the returned
 *    InvocationConstraints object has all of the requirements and all of the
 *    preferences from both arguments;
 *   TestCase #10
 *    both arguments are non-null and non-empty InvocationConstraints objects
 *    with duplicate requirements, duplicate preferences, and preferences that
 *    are duplicates of requirements; it's expected that the returned
 *    InvocationConstraints object has all of the requirements and all of the
 *    preferences from both arguments without duplicate requirements, duplicate
 *    preferences, and preferences that are duplicates of requirements;
 *
 * Infrastructure:
 *     - {@link CombineTest}
 *         performs actions; this file
 *     - {@link CombineTest.TestCase}
 *         auxiliary class that describes a Test Case
 *
 * Actions:
 *   Test performs the following steps:
 *     - constructing {@link CombineTest.TestCase} objects for all test cases;
 *     - invoking {@link CombineTest.TestCase#combine()} method on each created
 *       {@link CombineTest.TestCase} object;
 *
 * </pre>
 */
public class CombineTest extends QATest {
    QAConfig config;


    /**
     * An auxiliary class that describes a Test Case.
     * {@link CombineTest.TestCase#combine()}  method launches
     * {@link net.jini.core.constraint.InvocationConstraints#combine(InvocationConstraints,InvocationConstraints)}
     * method and compares the returned result with the expected one.
     */
    public class TestCase {

        /**
         * {@link net.jini.core.constraint.InvocationConstraints} object #1.
         * This is the 1-st argument for InvocationConstraints.combine() method.
         */
        private InvocationConstraints ic_arg1;

        /**
         * {@link net.jini.core.constraint.InvocationConstraints} object #2.
         * This is the 2-nd argument for InvocationConstraints.combine() method.
         */
        private InvocationConstraints ic_arg2;

        /**
         * Expected result of combine() method.
         */
        private InvocationConstraints ic_expected;

        /**
         * Constructor. Creates an instance of {@link CombineTest.TestCase}
         * object.
         *
         * @param ic1 the 1-st arg for InvocationConstraints.combine() method
         * @param ic2 the 2-nd arg for InvocationConstraints.combine() method
         * @param ic_exp the expected result of InvocationConstraints.combine()
         *               method
         */
        public TestCase(InvocationConstraints ic1, InvocationConstraints ic2,
                InvocationConstraints ic_exp) {
            ic_arg1 = ic1;
            ic_arg2 = ic2;
            ic_expected = ic_exp;
        }

        /**
         * Runs
         * {@link net.jini.core.constraint.InvocationConstraints#combine(InvocationConstraints,InvocationConstraints)}
         * method for {@link net.jini.core.constraint.InvocationConstraints}
         * objects of this Test Case and compare the returned result with the
         * expected result of this Test Case.
         *
         * @return true if the result of InvocationConstraints.combine() method
         *         is equal to the expected one or false otherwise
         */
        public boolean combine() {
            logger.log(Level.FINE, "InvocationConstraints.combine(arg1, arg2)");
            logger.log(Level.FINE, "arg1:: " + ic_arg1);
            logger.log(Level.FINE, "arg2:: " + ic_arg2);
            InvocationConstraints ic_returned =
                    InvocationConstraints.combine(ic_arg1, ic_arg2);
            logger.log(Level.FINE, "Expected:: " + ic_expected);
            logger.log(Level.FINE, "Returned:: " + ic_returned);
            return (ic_expected.equals(ic_returned));
        }
    }

    /**
     * Test Cases. The desciption of the test cases is in the class description.
     */
    public TestCase tc[] = new TestCase[10];

    /**
     * This method performs all preparations.
     * Creates {@link CombineTest.TestCase} objects for all test cases specified
     * in the class description.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        // Requirements
        HashSet reqs = new HashSet();

        // Preferences
        HashSet prefs = new HashSet();

        // InvocationConstraints object #1 (argument #1)
        InvocationConstraints icArg1;

        // InvocationConstraints object #2 (argument #2)
        InvocationConstraints icArg2;

        // The expected InvocationConstraints object
        InvocationConstraints icExp;

        /*
         * +++++ TestCase #1 +++++
         */
        tc[0] = new TestCase(null, null, InvocationConstraints.EMPTY);

        /*
         * +++++ TestCase #2 +++++
         */
        tc[1] = new TestCase(InvocationConstraints.EMPTY,
                InvocationConstraints.EMPTY, InvocationConstraints.EMPTY);

        /*
         * +++++ TestCase #3 +++++
         */
        tc[2] = new TestCase(null, InvocationConstraints.EMPTY,
                InvocationConstraints.EMPTY);

        /*
         * +++++ TestCase #4 +++++
         */
        tc[3] = new TestCase(InvocationConstraints.EMPTY, null,
                InvocationConstraints.EMPTY);

        /*
         * +++++ TestCase #5 +++++
         */
        // Requirements
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);
        // Preferences
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);
        tc[4] = new TestCase(null, new InvocationConstraints(reqs, prefs),
                new InvocationConstraints(reqs, prefs));

        /*
         * +++++ TestCase #6 +++++
         */
        // Requirements
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);
        // Preferences
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);
        tc[5] = new TestCase(InvocationConstraints.EMPTY,
                new InvocationConstraints(reqs, prefs),
                new InvocationConstraints(reqs, prefs));

        /*
         * +++++ TestCase #7 +++++
         */
        // Requirements
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);
        // Preferences
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);
        tc[6] = new TestCase(new InvocationConstraints(reqs, prefs), null,
                new InvocationConstraints(reqs, prefs));

        /*
         * +++++ TestCase #8 +++++
         */
        // Requirements
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);
        // Preferences
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);
        tc[7] = new TestCase(new InvocationConstraints(reqs, prefs),
                InvocationConstraints.EMPTY, new InvocationConstraints(reqs,
                prefs));

        /*
         * +++++ TestCase #9 +++++
         */
        // Reqs for InvocationConstraints #1
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        // Prefs for InvocationConstraints #1
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);

        // InvocationConstraints #1
        icArg1 = new InvocationConstraints(reqs, prefs);
        
        // Reqs for InvocationConstraints #2
        reqs.clear();
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);
        // Prefs for InvocationConstraints #2
        prefs.clear();
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);

        // InvocationConstraints #2
        icArg2 = new InvocationConstraints(reqs, prefs);
        
        // Reqs for the expected InvocationConstraints object
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);
        // Prefs for the expected InvocationConstraints object
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);

        // The expected InvocationConstraints object
        icExp = new InvocationConstraints(reqs, prefs);
        tc[8] = new TestCase(icArg1, icArg2, icExp);

        /*
         * +++++ TestCase #10 +++++
         */
        // Reqs for InvocationConstraints #1
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        // Duplicate req
        reqs.add(Integrity.YES);

        // Prefs for InvocationConstraints #1
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);
        // Duplicate pref
        prefs.add(Integrity.NO);
        // Pref that is duplicate of reqs in InvocationConstraints #2
        prefs.add(Integrity.YES);

        // InvocationConstraints #1
        icArg1 = new InvocationConstraints(reqs, prefs);
        
        // Reqs for InvocationConstraints #2
        reqs.clear();
        // Duplicate req
        reqs.add(Delegation.YES);
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);

        // Prefs for InvocationConstraints #2
        prefs.clear();
        // Pref that is duplicate of reqs in InvocationConstraints #1
        prefs.add(ClientAuthentication.YES);
        // Duplicate pref
        prefs.add(Delegation.NO);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);

        // InvocationConstraints #2
        icArg2 = new InvocationConstraints(reqs, prefs);
        
        // Reqs for the expected InvocationConstraints object
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);

        // Prefs for the expected InvocationConstraints object
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);

        // The expected InvocationConstraints object
        icExp = new InvocationConstraints(reqs, prefs);
        tc[9] = new TestCase(icArg1, icArg2, icExp);
    }

    /**
     * This method performs all test cases mentioned in the class description.
     */
    public void run() throws TestException {
        for (int i = 0; i < tc.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ TestCase #" + (i + (int) 1));

            if (!tc[i].combine()) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }
}
