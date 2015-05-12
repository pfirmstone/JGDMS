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
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;

// java.util
import org.apache.river.qa.harness.Test;
import java.util.logging.Level;
import java.util.Iterator;
import java.util.Set;

// java.lang.reflect
import java.lang.reflect.Method;

// Davis packages
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.InvocationConstraints;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link net.jini.core.constraint.InvocationConstraints#makeAbsolute()}
 *   method. This method returns an instance of
 *   {@link net.jini.core.constraint.InvocationConstraints} class constructed
 *   from all of the same requirements and preferences as this instance, but
 *   with every constraint that is an instance of
 *   {@link net.jini.core.constraint.RelativeTimeConstraint} replaced by the
 *   result of invoking the constraint's makeAbsolute() method with the current
 *   time (as given by {@link java.lang.System#currentTimeMillis()} method).
 *   Duplicate requirements, duplicate preferences, and preferences that are
 *   duplicates of requirements are all removed.
 *
 * Test Cases:
 *   TestCase #1
 *    It's verified that InvocationConstraints.makeAbsolute() method returns an
 *    InvocationConstraints object whose time values are equal to the time
 *    values of the InvocationConstraints object returned by
 *    InvocationConstraints.makeAbsolute(long baseTime) method (baseTime is
 *    the current time returned by {@link java.lang.System#currentTimeMillis()})
 *    within the limits of inaccuracy (10 seconds).
 *   TestCase #2
 *    It's verified that InvocationConstraints.makeAbsolute() method returns an
 *    InvocationConstraints object whose time values are equal to the time
 *    values of the InvocationConstraints object returned by
 *    InvocationConstraints.makeAbsolute(long baseTime) method (baseTime is
 *    the current time returned by {@link java.lang.System#currentTimeMillis()})
 *    within the limits of inaccuracy (10 seconds). Also it's verified in
 *    InvocationConstraints object returned by
 *    InvocationConstraints.makeAbsolute() method duplicate requirements,
 *    duplicate preferences, and preferences that are duplicates of
 *    requirements are all removed.
 *
 * Infrastructure:
 *     - {@link MakeAbsoluteTest}
 *         performs actions; this file
 *     - {@link MakeAbsoluteTest.TestCase}
 *         auxiliary class that describes a Test Case
 *
 * Actions:
 *   Test performs the following steps:
 *     - constructing {@link MakeAbsoluteTest.TestCase} objects for all test
 *       cases;
 *     - invoking
 *       {@link MakeAbsoluteTest.TestCase#callMakeAbsolute()} method on each
 *       created {@link MakeAbsoluteTest.TestCase} object to invoke
 *       InvocationConstraints.makeAbsolute() method;
 *     - comparing the result of the Test Case with the corresponding expected
 *       one.
 *
 * </pre>
 */
public class MakeAbsoluteTest extends QATestEnvironment implements Test {
    QAConfig config;


    /**
     * An auxiliary class that describes a Test Case.
     */
    public class TestCase {

        /**
         * Delta. The time interval in milliseconds used to compare time values.
         * (ExpectedTime - Delta) <= ReturnedTime <= (ExpectedTime + Delta)
         */
        final long DELTA = 10000L; // 10 seconds

        /**
         * Expected constraint.
         * Expected result of makeAbsolute() method.
         */
        private InvocationConstraints expected;

        /**
         * Constraint.
         * makeAbsolute() method is invoked on this constraint.
         */
        private InvocationConstraints constraint;

        /**
         * Returned constraint by makeAbsolute() method.
         */
        private InvocationConstraints returned;

        /**
         * Creates an instance of {@link MakeAbsoluteTest.TestCase}
         * object.
         *
         * @param constraint constraint on which makeAbsolute() method is invoked
         */
        public TestCase(InvocationConstraints constraint) {
            this.constraint = constraint;
        }

        /**
         * Prints out time values of the specified constraint.
         *
         * @param ic constraint
         */
        private void printConstraint(Object ic) {
            if (       ic instanceof ConnectionAbsoluteTime
                    || ic instanceof ConnectionRelativeTime
                    || ic instanceof DelegationAbsoluteTime
                    || ic instanceof DelegationRelativeTime) {
                printTime(ic);
            } else if (ic instanceof InvocationConstraints) {
                Set prefs = ((InvocationConstraints) ic).preferences();
                logger.log(Level.INFO,
                        "                    :  preferences: " + prefs);

                for (Iterator it = prefs.iterator(); it.hasNext();) {
                    Object el = it.next();
                    logger.log(Level.INFO,
                            "                      :  element: " + el);
                    printTime(el);
                }
                Set reqs = ((InvocationConstraints) ic).requirements();
                logger.log(Level.INFO,
                        "                    :  requirements: " + reqs);

                for (Iterator it = reqs.iterator(); it.hasNext();) {
                    Object el = it.next();
                    logger.log(Level.INFO,
                            "                      :  element: " + el);
                    printTime(el);
                }
            }
        }

        /**
         * Prints out time values of the specified constraint.
         *
         * @param ic constraint
         */
        private void printTime(Object ic) {
            if (ic instanceof ConnectionAbsoluteTime) {
                logger.log(Level.INFO,
                        "                        :  time= " + ((
                        ConnectionAbsoluteTime) ic).getTime());
            } else if (ic instanceof ConnectionRelativeTime) {
                logger.log(Level.INFO,
                        "                        :  time= " + ((
                        ConnectionRelativeTime) ic).getTime());
            } else if (ic instanceof DelegationAbsoluteTime) {
                logger.log(Level.INFO,
                        "                        :  minStart= "
                        + ((DelegationAbsoluteTime) ic).getMinStart());
                logger.log(Level.INFO,
                        "                        :  maxStart= "
                        + ((DelegationAbsoluteTime) ic).getMaxStart());
                logger.log(Level.INFO,
                        "                        :  minStop = "
                        + ((DelegationAbsoluteTime) ic).getMinStop());
                logger.log(Level.INFO,
                        "                        :  maxStop = "
                        + ((DelegationAbsoluteTime) ic).getMaxStop());
            } else if (ic instanceof DelegationRelativeTime) {
                logger.log(Level.INFO,
                        "                        :  minStart= "
                        + ((DelegationRelativeTime) ic).getMinStart());
                logger.log(Level.INFO,
                        "                        :  maxStart= "
                        + ((DelegationRelativeTime) ic).getMaxStart());
                logger.log(Level.INFO,
                        "                        :  minStop = "
                        + ((DelegationRelativeTime) ic).getMinStop());
                logger.log(Level.INFO,
                        "                        :  maxStop = "
                        + ((DelegationRelativeTime) ic).getMaxStop());
            }
            return;
        }

        /**
         * Checks that 2 {@link net.jini.core.constraint.InvocationConstraints}
         * instances are equal within the limits of inaccuracy that is DELTA.
         * So time values of the constraints that are an instances of
         * {@link net.jini.core.constraint.RelativeTimeConstraint} in the first
         * {@link net.jini.core.constraint.InvocationConstraints} object are
         * equal within the limits of inaccuracy to time values of the
         * constraints that are an instances of
         * {@link net.jini.core.constraint.RelativeTimeConstraint} in the second
         * {@link net.jini.core.constraint.InvocationConstraints} object.
         *
         * @param retC returned {@link net.jini.core.constraint.InvocationConstraints}
         * @param expC expected {@link net.jini.core.constraint.InvocationConstraints}
         * @throws TestException if 2 {@link net.jini.core.constraint.InvocationConstraints}
         * instances aren't equal within the limits of inaccuracy
         */
        private void compare(InvocationConstraints retC,
                InvocationConstraints expC) throws TestException {
            // logger.log(Level.INFO, "Checking requirements ...");
            Set exp_reqs = ((InvocationConstraints) expC).requirements();
            Set ret_reqs = ((InvocationConstraints) retC).requirements();
            // logger.log(Level.INFO, ":  Expected requirements: " + exp_reqs);
            // logger.log(Level.INFO, ":  Returned requirements: " + ret_reqs);
            if (ret_reqs.size() != exp_reqs.size()) {
                throw new TestException("Requirements have different size:: "
                        + " expected: " + exp_reqs.size() + " returned: "
                        + ret_reqs.size());
            }

            for (Iterator it = ret_reqs.iterator(); it.hasNext();) {
                Object el = it.next();
                // logger.log(Level.INFO, "                    :  returned element: " + el);
                if (!(el instanceof InvocationConstraint)) {
                    throw new TestException("Element of the set:: " + el
                            + " isn't instance of InvocationConstraint");
                }
                compare(exp_reqs, el);
            }

            // logger.log(Level.INFO, "Checking preferences ...");
            Set exp_prefs = ((InvocationConstraints) expC).preferences();
            Set ret_prefs = ((InvocationConstraints) retC).preferences();
            // logger.log(Level.INFO, ":  Expected preferences : " + exp_prefs);
            // logger.log(Level.INFO, ":  Returned preferences : " + ret_prefs);
            if (ret_prefs.size() != exp_prefs.size()) {
                throw new TestException("Preferences have different size:: "
                        + " expected: " + exp_prefs.size() + " returned: "
                        + ret_prefs.size());
            }

            for (Iterator it = ret_prefs.iterator(); it.hasNext();) {
                Object el = it.next();
                // logger.log(Level.INFO, "                    :  element: " + el);
                if (!(el instanceof InvocationConstraint)) {
                    throw new TestException("Element of the set:: " + el
                            + " isn't instance of InvocationConstraint");
                }
                compare(exp_prefs, el);
            }
            return;
        }

        /**
         * Checks that the specified {@link java.util.Set} contains constraint
         * which time values are equal to the time values of the specified
         * {@link net.jini.core.constraint.InvocationConstraint} object
         * within the limits of inaccuracy that is DELTA.
         *
         * @param set set of {@link net.jini.core.constraint.InvocationConstraint}
         * objects
         * @param ic  invocation constraint
         * @throws TestException if the set doesn't contain constraint
         * which time values are equal to the time values of the specified
         * {@link net.jini.core.constraint.InvocationConstraint} object
         * within the limits of inaccuracy that is DELTA.
         */
        private void compare(Set set, Object ic) throws TestException {
            if (!(ic instanceof ConnectionAbsoluteTime)
                    && !(ic instanceof DelegationAbsoluteTime)) {
                throw new TestException("Element of the returned set:: " + ic
                        + " is an instance of " + ic.getClass());
            }
            boolean fl = false;

            for (Iterator it = set.iterator(); it.hasNext();) {
                Object el = it.next();

                if (!(el instanceof ConnectionAbsoluteTime)
                        && !(el instanceof DelegationAbsoluteTime)) {
                    throw new TestException("Element of the expected set:: "
                            + el + " is an instance of " + el.getClass());
                }

                try {
                    if (ic instanceof ConnectionAbsoluteTime) {
                        if (!(el instanceof ConnectionAbsoluteTime)) {
                            continue;
                        }
                        compare((ConnectionAbsoluteTime) ic,
                                (ConnectionAbsoluteTime) el);
                        fl = true;
                        break;
                    } else if (ic instanceof DelegationAbsoluteTime) {
                        if (!(el instanceof DelegationAbsoluteTime)) {
                            continue;
                        }
                        compare((DelegationAbsoluteTime) ic,
                                (DelegationAbsoluteTime) el);
                        fl = true;
                        break;
                    }
                } catch (TestException e) {}
            }

            if (!fl) {
                throw new TestException(
                        "Returned constrain isn't equal to the expecte one");
            }
        }

        /**
         * Compares 2 {@link net.jini.core.constraint.ConnectionAbsoluteTime}
         * objects within the limits of inaccuracy that is DELTA. It's verified
         * that the first constraint ic is equal to the second one within the
         * limits of inaccuracy that is DELTA.
         *
         * @param ic returned {@link net.jini.core.constraint.ConnectionAbsoluteTime}
         * object
         * @param el expected {@link net.jini.core.constraint.ConnectionAbsoluteTime}
         * object
         * @throws TestException if the time values of the first constraint
         * aren't equal to the time values of the second constraint
         * within the limits of inaccuracy that is DELTA.
         */
        private void compare(ConnectionAbsoluteTime ic,
                ConnectionAbsoluteTime el) throws TestException {
            long ret_time = ic.getTime();
            long exp_time = el.getTime();
            long minVal = exp_time - DELTA;
            long maxVal =
                    (exp_time + DELTA < 0) ? Long.MAX_VALUE : exp_time + DELTA;

            if (ret_time < minVal || ret_time > maxVal) {
                logger.log(Level.INFO, "\nexp_time-DELTA: " + minVal);
                logger.log(Level.INFO, "ret_time: " + ret_time);
                logger.log(Level.INFO, "exp_time+DELTA: " + maxVal);
                throw new TestException("Expected that "
                        + "exp_time-DELTA <= ret_time <= exp_time+DELTA::"
                        + "\nexp_time-DELTA: " + minVal + "\nret_time: "
                        + ret_time + "\nexp_time+DELTA: " + maxVal);
            }
        }

        /**
         * Compares 2 {@link net.jini.core.constraint.DelegationAbsoluteTime}
         * objects within the limits of inaccuracy that is DELTA. It's verified
         * that the first constraint ic is equal to the second one within the
         * limits of inaccuracy that is DELTA.
         *
         * @param ic returned {@link net.jini.core.constraint.DelegationAbsoluteTime}
         * object
         * @param el expected {@link net.jini.core.constraint.DelegationAbsoluteTime}
         * object
         * @throws TestException if the time values of the first constraint
         * aren't equal to the time values of the second constraint
         * within the limits of inaccuracy that is DELTA.
         */
        private void compare(DelegationAbsoluteTime ic,
                DelegationAbsoluteTime el) throws TestException {
            long retMinStart = ic.getMinStart();
            long retMaxStart = ic.getMaxStart();
            long retMinStop = ic.getMinStop();
            long retMaxStop = ic.getMaxStop();
            long expMinStart = el.getMinStart();
            long expMaxStart = el.getMaxStart();
            long expMinStop = el.getMinStop();
            long expMaxStop = el.getMaxStop();
            long[] ret = { retMinStart, retMaxStart, retMinStop, retMaxStop };
            long[] exp = { expMinStart, expMaxStart, expMinStop, expMaxStop };
            String[] name = { "MinStart", "MaxStart", "MinStop", "MaxStop" };

            for (int i = 0; i < exp.length; i++) {
                long minVal = exp[i] - DELTA;
                long maxVal =
                        (exp[i] + DELTA < 0) ? Long.MAX_VALUE : exp[i] + DELTA;

                if (ret[i] < minVal || ret[i] > maxVal) {
                    logger.log(Level.INFO, "\nTime value: " + name[i]);
                    logger.log(Level.INFO, "\nexpected: " + exp[i]);
                    logger.log(Level.INFO, "expected-DELTA: " + minVal);
                    logger.log(Level.INFO, "expected+DELTA: " + maxVal);
                    logger.log(Level.INFO, "returned: " + ret[i]);
                    throw new TestException("ret[i] < exp[i] - DELTA ||"
                            + " ret[i] > exp[i] + DELTA isn't true");
                }
            }
        }

        /**
         * Runs makeAbsolute() method to be tested on the constraint.
         *
         * @throws TestException if any verification fails for the method
         */
        public void callMakeAbsolute() throws TestException {
            logger.log(Level.INFO,
                    "+++++ Invoking " + constraint.getClass().getName()
                    + ".makeAbsolute() method");
            logger.log(Level.INFO, "Constraint          :: " + constraint);
            // printConstraint(constraint);

            // Prepare expected object
            expected = constraint.makeAbsolute(System.currentTimeMillis());
            logger.log(Level.INFO, "Expected Constraint :: " + expected);
            // printConstraint(expected);

            // Invoke the method to be tested
            returned = constraint.makeAbsolute();
            logger.log(Level.INFO, "Returned Constraint :: " + returned);
            // printConstraint(returned);

            // Comparing
            compare(expected, returned);
            logger.log(Level.INFO, "Test Case passed");
            return;
        }
    }

    /**
     * Test Cases. The desciption of the test cases is in the class description.
     */
    public TestCase t_cases[] = {
        new TestCase(new InvocationConstraints(
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(0),
                                 new DelegationRelativeTime(0, 0, 0, 0)
                         },
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(1),
                                 new DelegationRelativeTime(1, 1, 1, 1)
                         })),
        new TestCase(new InvocationConstraints(
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(Long.MAX_VALUE),
                                 new ConnectionRelativeTime(Long.MAX_VALUE - 1),
                                 new DelegationRelativeTime(Long.MAX_VALUE,
                                         Long.MAX_VALUE, Long.MAX_VALUE,
                                         Long.MAX_VALUE)
                         },
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(Long.MAX_VALUE - 1),
                                 new ConnectionRelativeTime(Long.MAX_VALUE - 2),
                                 new DelegationRelativeTime(Long.MAX_VALUE - 1,
                                         Long.MAX_VALUE - 1, Long.MAX_VALUE - 1,
                                         Long.MAX_VALUE - 1)
                         }))
    };

    /**
     * This method runs all Test Cases specified in the class description.
     */
    public void run() throws TestException {
        config = getConfig();

        for (int i = 0; i < t_cases.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ TestCase #" + (i + (int) 1));

            t_cases[i].callMakeAbsolute();
        }
        return;
    }
}
