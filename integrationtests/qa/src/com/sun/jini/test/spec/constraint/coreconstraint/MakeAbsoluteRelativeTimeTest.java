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

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATest;

// java.util
import java.util.logging.Level;
import java.util.Iterator;
import java.util.Set;

// java.lang.reflect
import java.lang.reflect.Method;

// Davis packages
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the following methods:
 *     {@link net.jini.core.constraint.ConnectionRelativeTime#makeAbsolute(long)}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#makeAbsolute(long)}
 *     {@link net.jini.core.constraint.ConstraintAlternatives#makeAbsolute(long)}
 *     {@link net.jini.core.constraint.InvocationConstraints#makeAbsolute(long)}
 *   ConnectionRelativeTime.makeAbsolute(long baseTime) method returns a
 *     {@link net.jini.core.constraint.ConnectionAbsoluteTime} instance with
 *     time obtained by adding the specified base time argument to the duration
 *     value from this instance. If the addition results in overflow, a time
 *     value of Long.MAX_VALUE is used.
 *   DelegationRelativeTime.makeAbsolute(long baseTime) method returns a
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime} instance with
 *     times obtained by adding the specified base time argument to the duration
 *     values from this instance. If an addition results in underflow or
 *     overflow, a time value of Long.MIN_VALUE or Long.MAX_VALUE is used,
 *     respectively.
 *   ConstraintAlternatives.makeAbsolute(long baseTime) method returns a
 *     constraint equal to the result of taking the constraints in this
 *     instance, replacing each constraint that is an instance of
 *     {@link net.jini.core.constraint.RelativeTimeConstraint} with the result
 *     of invoking that constraint's makeAbsolute() method with the specified
 *     base time, and invoking
 *     {@link net.jini.core.constraint.ConstraintAlternatives#create(Collection)}
 *     method with the revised collection of constraints.
 *   InvocationConstraints.makeAbsolute(long baseTime) method returns an
 *     instance of {@link net.jini.core.constraint.InvocationConstraints} equal
 *     to the result of taking the requirements and preferences in this
 *     instance, replacing each constraint that is an instance of
 *     {@link net.jini.core.constraint.RelativeTimeConstraint} with the result
 *     of invoking that constraint's makeAbsolute() method with the specified
 *     base time, and creating a new instance of
 *     {@link net.jini.core.constraint.InvocationConstraints} with duplicate
 *     requirements, duplicate preferences, and preferences that are duplicates
 *     of requirements all removed.
 *   All these methods accept the following parameter:
 *     baseTime - an absolute time, specified in milliseconds from midnight,
 *                January 1, 1970 UTC
 *
 * Test Cases:
 *   TestCase #1
 *    It's verified that ConnectionRelativeTime.makeAbsolute(long baseTime)
 *    method returns ConnectionAbsoluteTime instance with time obtained by
 *    adding the specified base time argument to the duration value from this
 *    ConnectionRelativeTime instance.
 *   TestCase #2
 *    It's verified that ConnectionRelativeTime.makeAbsolute(long baseTime)
 *    method returns ConnectionAbsoluteTime instance with time value of
 *    Long.MAX_VALUE if adding the specified base time argument to the
 *    duration value from this ConnectionRelativeTime instance results in
 *    overflow.
 *   TestCase #3
 *    It's verified that DelegationRelativeTime.makeAbsolute(long baseTime)
 *    method returns DelegationAbsoluteTime instance with times obtained by
 *    adding the specified base time argument to the duration values from this
 *    ConnectionRelativeTime instance.
 *   TestCase #4
 *    It's verified that DelegationRelativeTime.makeAbsolute(long baseTime)
 *    method returns DelegationAbsoluteTime instance with time value of
 *    Long.MIN_VALUE if adding the specified base time argument to the
 *    duration values from this ConnectionRelativeTime instance results in
 *    underflow.
 *   TestCase #5
 *    It's verified that DelegationRelativeTime.makeAbsolute(long baseTime)
 *    method returns DelegationAbsoluteTime instance with time value of
 *    Long.MAX_VALUE if adding the specified base time argument to the
 *    duration values from this ConnectionRelativeTime instance results in
 *    overflow.
 *   TestCase #6
 *    It's verified that ConstraintAlternatives.makeAbsolute(long baseTime)
 *    method returns ConstraintAlternatives object equal to the result of
 *    taking the constraints in this instance, replacing each constraint that
 *    is an instance of RelativeTimeConstraint with the result of invoking that
 *    constraint's makeAbsolute() method with the specified base time, and
 *    invoking create() method with the revised collection of constraints.
 *   TestCase #7
 *    It's verified that InvocationConstraints.makeAbsolute(long baseTime)
 *    method returns InvocationConstraints object equal to the result of taking
 *    the requirements and preferences in this instance, replacing each
 *    constraint that is an instance of RelativeTimeConstraint with the result
 *    of invoking that constraint's makeAbsolute() method with the specified
 *    base time, and creating a new instance of InvocationConstraints.
 *   TestCase #8
 *    It's verified that InvocationConstraints.makeAbsolute(long baseTime)
 *    method returns InvocationConstraints object equal to the result of taking
 *    the requirements and preferences in this instance, replacing each
 *    constraint that is an instance of RelativeTimeConstraint with the result
 *    of invoking that constraint's makeAbsolute() method with the specified
 *    base time, and creating a new instance of InvocationConstraints with
 *    preferences that are duplicates of requirements all removed.
 *   TestCase #9
 *    It's verified that InvocationConstraints.makeAbsolute(long baseTime)
 *    method returns InvocationConstraints object equal to the result of taking
 *    the requirements and preferences in this instance, replacing each
 *    constraint that is an instance of RelativeTimeConstraint with the result
 *    of invoking that constraint's makeAbsolute() method with the specified
 *    base time, and creating a new instance of InvocationConstraints with
 *    duplicate requirements all removed.
 *   TestCase #10
 *    It's verified that InvocationConstraints.makeAbsolute(long baseTime)
 *    method returns InvocationConstraints object equal to the result of taking
 *    the requirements and preferences in this instance, replacing each
 *    constraint that is an instance of RelativeTimeConstraint with the result
 *    of invoking that constraint's makeAbsolute() method with the specified
 *    base time, and creating a new instance of InvocationConstraints with
 *    duplicate preferences all removed.
 *
 * Infrastructure:
 *     - {@link MakeAbsoluteRelativeTimeTest}
 *         performs actions; this file
 *     - {@link MakeAbsoluteRelativeTimeTest.TestCase}
 *         auxiliary class that describes a Test Case
 *
 * Actions:
 *   Test performs the following steps:
 *     - constructing {@link MakeAbsoluteRelativeTimeTest.TestCase} objects for
 *       all test cases;
 *     - invoking
 *       {@link MakeAbsoluteRelativeTimeTest.TestCase#callMakeAbsolute()} method
 *       on each created {@link MakeAbsoluteRelativeTimeTest.TestCase} object to
 *       invoke the corresponding makeAbsolute() method specified by this
 *       {@link MakeAbsoluteRelativeTimeTest.TestCase} object;
 *     - comparing the result of the Test Case with the corresponding expected
 *       one.
 *
 * </pre>
 */
public class MakeAbsoluteRelativeTimeTest extends QATest {
    QAConfig config;


    /**
     * An auxiliary class that describes a Test Case.
     */
    public class TestCase {

        /**
         * Expected constraint.
         * Expected result of makeAbsolute(long baseTime) method.
         */
        private Object exp_constraint;

        /**
         * Constraint. makeAbsolute(long baseTime) method is invoked on this
         * object.
         */
        private Object constraint;

        /**
         * Argument for makeAbsolute(long baseTime) method.
         */
        private long argument;

        /**
         * Creates an instance of {@link MakeAbsoluteRelativeTimeTest.TestCase}
         * object.
         *
         * @param ic     constraint on which makeAbsolute(long baseTime) method
         *               is invoked
         * @param exp_ic expected result of makeAbsolute(long baseTime) method
         * @param arg    argument for makeAbsolute(long baseTime) method
         */
        public TestCase(Object ic, Object exp_ic, long arg) {
            constraint = ic;
            exp_constraint = exp_ic;
            argument = arg;
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
            } else if (ic instanceof ConstraintAlternatives) {
                Set elements = ((ConstraintAlternatives) ic).elements();

                for (Iterator it = elements.iterator(); it.hasNext();) {
                    Object el = it.next();
                    logger.log(Level.INFO,
                            "                    :  element: " + el);
                    printTime(el);
                }
            } else if (ic instanceof InvocationConstraints) {
                Set prefs = ((InvocationConstraints) ic).preferences();
                logger.log(Level.INFO,
                        "                    :  preferences: " + prefs);

                for (Iterator it = prefs.iterator(); it.hasNext();) {
                    Object el = it.next();
                    logger.log(Level.INFO,
                            "                    :  element: " + el);
                    printTime(el);
                }
                Set reqs = ((InvocationConstraints) ic).requirements();
                logger.log(Level.INFO,
                        "                    :  requirements: " + reqs);

                for (Iterator it = reqs.iterator(); it.hasNext();) {
                    Object el = it.next();
                    logger.log(Level.INFO,
                            "                    :  element: " + el);
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
            if (       ic instanceof ConnectionAbsoluteTime) {
                logger.log(Level.INFO, "                    :  time= "
                        + ((ConnectionAbsoluteTime) ic).getTime());
            } else if (ic instanceof ConnectionRelativeTime) {
                logger.log(Level.INFO, "                    :  time= "
                        + ((ConnectionRelativeTime) ic).getTime());
            } else if (ic instanceof DelegationAbsoluteTime) {
                logger.log(Level.INFO, "                    :  minStart= "
                        + ((DelegationAbsoluteTime) ic) .getMinStart());
                logger.log(Level.INFO, "                    :  maxStart= "
                        + ((DelegationAbsoluteTime) ic).getMaxStart());
                logger.log(Level.INFO, "                    :  minStop = "
                        + ((DelegationAbsoluteTime) ic).getMinStop());
                logger.log(Level.INFO, "                    :  maxStop = "
                        + ((DelegationAbsoluteTime) ic).getMaxStop());
            } else if (ic instanceof DelegationRelativeTime) {
                logger.log(Level.INFO, "                    :  minStart= "
                        + ((DelegationRelativeTime) ic) .getMinStart());
                logger.log(Level.INFO, "                    :  maxStart= "
                        + ((DelegationRelativeTime) ic).getMaxStart());
                logger.log(Level.INFO, "                    :  minStop = "
                        + ((DelegationRelativeTime) ic).getMinStop());
                logger.log(Level.INFO, "                    :  maxStop = "
                        + ((DelegationRelativeTime) ic).getMaxStop());
            }
            return;
        }

        /**
         * Runs makeAbsolute(long baseTime) method to be tested on the
         * constraint.
         *
         * @throws TestException if any verification fails for the method
         */
        public void callMakeAbsolute() throws TestException {
            logger.log(Level.INFO,
                    "+++++ Invoking " + constraint.getClass().getName()
                    + ".makeAbsolute(baseTime) method");
            logger.log(Level.INFO, "Constraint          :: " + constraint);
            printConstraint(constraint);
            logger.log(Level.INFO, "baseTime (argument) :: " + argument);
            logger.log(Level.INFO, "Expected Constraint :: " + exp_constraint);
            printConstraint(exp_constraint);

            /*
             * Invoke the method to be tested
             */
            try {
                Class cl = constraint.getClass();
                Method method = cl.getMethod("makeAbsolute", new Class[] {
                    long.class}
                );
                Object ret_constraint = method.invoke(constraint, new Object[] {
                    new Long(argument)}
                );
                logger.log(Level.INFO,
                        "Returned Constraint :: " + ret_constraint);
                printConstraint(ret_constraint);

                if (!ret_constraint.equals(exp_constraint)) {
                    throw new TestException("Constraint returned by"
                            + " makeAbsolute(long baseTime) method isn't"
                            + " equal to the expected constraint::"
                            + " expected: " + exp_constraint + " returned: "
                            + ret_constraint);
                }
            } catch (TestException e) {
                throw e;
            } catch (Exception e) {
                throw new TestException("Exception is thrown while invoking"
                        + " makeAbsolute(long baseTime) method using"
                        + " reflection: ", e);
            }
            logger.log(Level.INFO, "Test Case passed");
            return;
        }
    }

    /**
     * Test Cases. The desciption of the test cases is in the class description.
     */
    public TestCase t_cases[] = {
        new TestCase(new ConnectionRelativeTime(0),
                     new ConnectionAbsoluteTime(100),
                     100),
        new TestCase(new ConnectionRelativeTime(Long.MAX_VALUE),
                     new ConnectionAbsoluteTime(Long.MAX_VALUE),
                     Long.MAX_VALUE),
        new TestCase(new DelegationRelativeTime(0, 0, 0, 0),
                     new DelegationAbsoluteTime(100, 100, 100, 100),
                     100),
        new TestCase(new DelegationRelativeTime(Long.MIN_VALUE, Long.MIN_VALUE,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     new DelegationAbsoluteTime(Long.MIN_VALUE, Long.MIN_VALUE,
                                              Long.MAX_VALUE + Long.MIN_VALUE/2,
                                              Long.MAX_VALUE + Long.MIN_VALUE/2),
                     Long.MIN_VALUE/2),
        new TestCase(new DelegationRelativeTime(Long.MAX_VALUE, Long.MAX_VALUE,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     new DelegationAbsoluteTime(Long.MAX_VALUE, Long.MAX_VALUE,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     Long.MAX_VALUE),
        new TestCase(new ConstraintAlternatives(
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(0),
                                 new DelegationRelativeTime(0, 0, 0, 0)
                         }),
                     new ConstraintAlternatives(
                         new InvocationConstraint[] {
                                 new ConnectionAbsoluteTime(100),
                                 new DelegationAbsoluteTime(100, 100, 100, 100)
                         }),
                     100),
        new TestCase(new InvocationConstraints(
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(0),
                                 new DelegationRelativeTime(0, 0, 0, 0)
                         },
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(1),
                                 new DelegationRelativeTime(1, 1, 1, 1)
                         }),
                     new InvocationConstraints(
                         new InvocationConstraint[] {
                                 new ConnectionAbsoluteTime(100),
                                 new DelegationAbsoluteTime(100, 100, 100, 100)
                         },
                         new InvocationConstraint[] {
                                 new ConnectionAbsoluteTime(101),
                                 new DelegationAbsoluteTime(101, 101, 101, 101)
                         }),
                     100),
        new TestCase(new InvocationConstraints(
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(0)
                         },
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(1)
                         }),
                     new InvocationConstraints(
                         new InvocationConstraint[] {
                                 new ConnectionAbsoluteTime(Long.MAX_VALUE)
                         },
                         new InvocationConstraint[0]),
                     Long.MAX_VALUE),
        new TestCase(new InvocationConstraints(
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(0),
                                 new ConnectionRelativeTime(1)
                         },
                         new InvocationConstraint[0]),
                     new InvocationConstraints(
                         new InvocationConstraint[] {
                                 new ConnectionAbsoluteTime(Long.MAX_VALUE),
                         },
                         new InvocationConstraint[0]),
                     Long.MAX_VALUE),
        new TestCase(new InvocationConstraints(
                         new InvocationConstraint[0],
                         new InvocationConstraint[] {
                                 new ConnectionRelativeTime(0),
                                 new ConnectionRelativeTime(1)
                         }),
                     new InvocationConstraints(
                         new InvocationConstraint[0],
                         new InvocationConstraint[] {
                                 new ConnectionAbsoluteTime(Long.MAX_VALUE),
                         }),
                     Long.MAX_VALUE)
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
        // PASS
        return;
    }
}
