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
import java.util.Set;
import java.util.Collection;

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
 *   This test verifies that it is possible for an instance of
 *   {@link net.jini.core.constraint.InvocationConstraints} class to contain
 *   both requirements that conflict with each other, and preferences that
 *   conflict with each other and with requirements.
 *
 * Test Cases:
 *   TestCase #1
 *    It's verified that {@link net.jini.core.constraint.InvocationConstraints}
 *    object can contain requirements that conflict with each other.
 *   TestCase #2
 *    It's verified that {@link net.jini.core.constraint.InvocationConstraints}
 *    object can contain preferences that conflict with each other.
 *   TestCase #3
 *    It's verified that {@link net.jini.core.constraint.InvocationConstraints}
 *    object can contain preferences that conflict with requirements.
 *
 * Infrastructure:
 *     - {@link ConflictTest}
 *         performs actions; this file
 *     - {@link ConflictTest.TestCase}
 *         auxiliary class that describes a Test Case and contains requirements
 *         and preferences
 *
 * Actions:
 *   Test performs the following steps:
 *     - constructing {@link ConflictTest.TestCase} objects for all test cases;
 *     - invoking {@link ConflictTest.TestCase#createObject()} method on each
 *       created {@link ConflictTest.TestCase} object to try to create
 *       {@link net.jini.core.constraint.InvocationConstraints} object
 *       corresponding to the requirements and preferences specified by this
 *       {@link ConflictTest.TestCase} object; it's verified that the
 *       corresponding {@link net.jini.core.constraint.InvocationConstraints}
 *       object has been created successfully.
 *
 * </pre>
 */
public class ConflictTest extends QATestEnvironment implements Test {
    QAConfig config;


    /**
     * An auxiliary class that describes a Test Case.
     */
    public class TestCase {

        /**
         * Requirements.
         */
        private HashSet reqs;

        /**
         * Preferences.
         */
        private HashSet prefs;

        /**
         * Constructor. Creates an instance of {@link ConflictTest.TestCase}
         * object.
         *
         * @param r requirements
         * @param p preferences
         */
        public TestCase(HashSet r, HashSet p) throws TestException {
            reqs = new HashSet((Collection) r);
            prefs = new HashSet((Collection) p);
        }

        /**
         * Tries to create {@link net.jini.core.constraint.InvocationConstraints}
         * object from the requirements and preferences specified by this
         * {@link ConflictTest.TestCase} object and verifies that the created
         * {@link net.jini.core.constraint.InvocationConstraints} object
         * contains requirements and preferences equal to the requirements and
         * preferences specified by this {@link ConflictTest.TestCase} object.
         *
         * @return true if {@link net.jini.core.constraint.InvocationConstraints}
         * object has been created successfully with the specified requirements
         * and preferences or false otherwise
         */
        public boolean createObject() {
            logger.log(Level.FINE, "new InvocationConstraints(reqs, prefs)");
            logger.log(Level.FINE, "reqs :: " + reqs);
            logger.log(Level.FINE, "prefs:: " + prefs);
            InvocationConstraints ic = null;

            /*
             * Try to create InvocationConstraints object
             */
            try {
                ic = new InvocationConstraints(reqs, prefs);
            } catch (Exception e) {
                logger.log(Level.FINE,
                        "Exception has been thrown while creating"
                        + "InvocationConstraints object: " + e);
                return false;
            }

            /*
             * Get requirements from the created InvocationConstraints object
             * and compare with the requirements specified while creation of
             * this object.
             */
            Set r = ic.requirements();
            logger.log(Level.FINE,
                    "reqs in InvocationConstraints object:: " + r);

            if (!reqs.equals(r)) {
                return false;
            }

            /*
             * Get preferences from the created InvocationConstraints object
             * and compare with the preferences specified while creation of
             * this object.
             */
            Set p = ic.preferences();
            logger.log(Level.FINE,
                    "prefs in InvocationConstraints object:: " + p);

            if (!prefs.equals(p)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Test Cases. The desciption of the test cases is in the class description.
     */
    public TestCase tc[] = new TestCase[3];

    /**
     * This method performs all preparations.
     * Creates {@link ConflictTest.TestCase} objects for all test cases specified
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
        reqs.add(ClientAuthentication.YES);
        reqs.add(ClientAuthentication.NO);
        reqs.add(Confidentiality.YES);
        reqs.add(Confidentiality.NO);
        reqs.add(Delegation.YES);
        reqs.add(Delegation.NO);
        reqs.add(Integrity.YES);
        reqs.add(Integrity.NO);
        reqs.add(ServerAuthentication.YES);
        reqs.add(ServerAuthentication.NO);
        prefs.clear();
        tc[0] = new TestCase(reqs, prefs);

        /*
         * +++++ TestCase #2 +++++
         */
        reqs.clear();
        prefs.clear();
        prefs.add(ClientAuthentication.YES);
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.YES);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.YES);
        prefs.add(Delegation.NO);
        prefs.add(Integrity.YES);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.YES);
        prefs.add(ServerAuthentication.NO);
        tc[1] = new TestCase(reqs, prefs);

        /*
         * +++++ TestCase #3 +++++
         */
        reqs.clear();
        reqs.add(ClientAuthentication.YES);
        reqs.add(Confidentiality.YES);
        reqs.add(Delegation.YES);
        reqs.add(Integrity.YES);
        reqs.add(ServerAuthentication.YES);
        prefs.clear();
        prefs.add(ClientAuthentication.NO);
        prefs.add(Confidentiality.NO);
        prefs.add(Delegation.NO);
        prefs.add(Integrity.NO);
        prefs.add(ServerAuthentication.NO);
        tc[2] = new TestCase(reqs, prefs);
        return this;
    }

    /**
     * This method performs all test cases mentioned in the class description.
     */
    public void run() throws TestException {
        for (int i = 0; i < tc.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ TestCase #" + (i + (int) 1));

            if (!tc[i].createObject()) {
                throw new TestException(
                        "" + " test failed");
            }
        }
        return;
    }
}
