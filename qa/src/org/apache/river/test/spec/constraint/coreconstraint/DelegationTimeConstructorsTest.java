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
package org.apache.river.test.spec.constraint.coreconstraint;

import java.util.logging.Level;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.TestException;

// java.util
import java.util.logging.Level;
import java.util.Date;

// java.lang.reflect
import java.lang.reflect.Method;

// Davis packages
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the following constructors:
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#DelegationAbsoluteTime(Date,Date,Date,Date)}
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#DelegationAbsoluteTime(long,long,long,long)}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#DelegationRelativeTime(long,long,long,long)}
 *   DelegationAbsoluteTime() constructors create a constraint with the
 *   specified dates/absolute times.
 *   Parameters:
 *     minStart - the minimum start date/time in milliseconds from midnight,
 *                January 1, 1970 UTC
 *     maxStart - the maximum start date/time in milliseconds from midnight,
 *                January 1, 1970 UTC
 *     minStop  - the minimum stop date/time in milliseconds from midnight,
 *                January 1, 1970 UTC
 *     maxStop  - the maximum stop date/time in milliseconds from midnight,
 *                January 1, 1970 UTC
 *   DelegationRelativeTime() constructor creates a constraint with the
 *   specified durations.
 *   Parameters:
 *     minStart - the minimum start duration in milliseconds
 *     maxStart - the maximum start duration in milliseconds
 *     minStop  - the minimum stop duration in milliseconds
 *     maxStop  - the maximum stop duration in milliseconds
 *   DelegationAbsoluteTime(Date,Date,Date,Date) constructor throws
 *   {@link java.lang.NullPointerException} - if any argument is null
 *   All of these constructor throws {@link java.lang.IllegalArgumentException}
 *   in the following cases:
 *     - if minStart is later/greater than maxStart,
 *     - if maxStart is later/greater than minStop,
 *     - if minStop is later/greater than maxStop.
 *   DelegationRelativeTime() constructor also throws
 *   {@link java.lang.IllegalArgumentException} - if minStop is less than zero
 *
 * Test Cases:
 *   TestCase #1
 *    It's verified that DelegationAbsoluteTime(Date minStart, Date maxStart,
 *    Date minStop, Date maxStop) constructor creates a
 *    {@link net.jini.core.constraint.DelegationAbsoluteTime} constraint with
 *    the specified dates.
 *   TestCase #2-5
 *    It's verified that DelegationAbsoluteTime(Date minStart, Date maxStart,
 *    Date minStop, Date maxStop) constructor throws
 *    java.lang.NullPointerException if any argument is null.
 *   TestCase #6
 *    It's verified that DelegationAbsoluteTime(Date minStart, Date maxStart,
 *    Date minStop, Date maxStop) constructor throws
 *    java.lang.IllegalArgumentException if minStart is later than maxStart.
 *   TestCase #7
 *    It's verified that DelegationAbsoluteTime(Date minStart, Date maxStart,
 *    Date minStop, Date maxStop) constructor throws
 *    java.lang.IllegalArgumentException if maxStart is later than minStop
 *   TestCase #8
 *    It's verified that DelegationAbsoluteTime(Date minStart, Date maxStart,
 *    Date minStop, Date maxStop) constructor throws
 *    java.lang.IllegalArgumentException if minStop is later than maxStop
 *   TestCase #9
 *    It's verified that DelegationAbsoluteTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor creates a
 *    {@link net.jini.core.constraint.DelegationAbsoluteTime} constraint with
 *    the specified absolute times.
 *   TestCase #10
 *    It's verified that DelegationAbsoluteTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor throws
 *    java.lang.IllegalArgumentException if minStart is greater than maxStart
 *   TestCase #11
 *    It's verified that DelegationAbsoluteTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor throws
 *    java.lang.IllegalArgumentException if maxStart is greater than minStop
 *   TestCase #12
 *    It's verified that DelegationAbsoluteTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor throws
 *    java.lang.IllegalArgumentException if minStop is greater than maxStop
 *   TestCase #13
 *    It's verified that DelegationRelativeTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor creates a
 *    {@link net.jini.core.constraint.DelegationRelativeTime} constraint with
 *    the specified durations.
 *   TestCase #14
 *    It's verified that DelegationRelativeTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor throws
 *    java.lang.IllegalArgumentException if minStart is greater than maxStart
 *   TestCase #15
 *    It's verified that DelegationRelativeTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor throws
 *    java.lang.IllegalArgumentException if maxStart is greater than minStop
 *   TestCase #16
 *    It's verified that DelegationRelativeTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor throws
 *    java.lang.IllegalArgumentException if minStop is greater than maxStop
 *   TestCase #16
 *    It's verified that DelegationRelativeTime(long minStart, long maxStart,
 *    long minStop, long maxStop) constructor throws
 *    java.lang.IllegalArgumentException if minStop is less then zero
 *
 * Infrastructure:
 *     - {@link DelegationTimeConstructorsTest}
 *         performs actions; this file
 *     - {@link DelegationTimeConstructorsTest.TestCase}
 *         auxiliary class that describes a Test Case
 *     - {@link ConnectionTimeConstructorsTest}
 *         is extended by {@link DelegationTimeConstructorsTest}
 *     - {@link ConnectionTimeConstructorsTest.TestCase}
 *         is extended by {@link DelegationTimeConstructorsTest.TestCase}
 *
 * Actions:
 *   Test performs the following steps:
 *     - constructing {@link DelegationTimeConstructorsTest.TestCase} objects
 *       for all test cases;
 *     - invoking {@link DelegationTimeConstructorsTest.TestCase#callConstructor()}
 *       method on each created {@link DelegationTimeConstructorsTest.TestCase}
 *       object to invoke the corresponding constructor specified by this
 *       {@link DelegationTimeConstructorsTest.TestCase} object;
 *     - comparing the result of the Test Case with the corresponding expected
 *       one.
 *
 * </pre>
 */
public class DelegationTimeConstructorsTest
        extends ConnectionTimeConstructorsTest {

    /**
     * An object to point to constructor:
     * {@link net.jini.core.constraint.DelegationAbsoluteTime#DelegationAbsoluteTime(Date,Date,Date,Date)}
     */
    Object DELEGATION_ABSOLUTE_TIME__DATE = new Object() {
        public String toString() {
            return "public DelegationAbsoluteTime(Date minStart, Date maxStart,"
                    + " Date minStop, Date maxStop)";
        }
    };

    /**
     * An object to point to constructor:
     * {@link net.jini.core.constraint.DelegationAbsoluteTime#DelegationAbsoluteTime(long,long,long,long)}
     */
    Object DELEGATION_ABSOLUTE_TIME__LONG = new Object() {
        public String toString() {
            return "public DelegationAbsoluteTime(long minStart, long maxStart,"
                    + " long minStop, long maxStop)";
        }
    };

    /**
     * An object to point to constructor:
     * {@link net.jini.core.constraint.DelegationRelativeTime#DelegationRelativeTime(long,long,long,long)}
     */
    Object DELEGATION_RELATIVE_TIME__LONG = new Object() {
        public String toString() {
            return "public DelegationAbsoluteTime(long minStart, long maxStart,"
                    + " long minStop, long maxStop)";
        }
    };


    /**
     * An auxiliary class that describes a Test Case.
     */
    protected class TestCase extends ConnectionTimeConstructorsTest.TestCase {

        /**
         * Argument to be passed to the constructor to be tested.
         */
        private long minStart;

        /**
         * Argument to be passed to the constructor to be tested.
         */
        private long maxStart;

        /**
         * Argument to be passed to the constructor to be tested.
         */
        private long minStop;

        /**
         * Argument to be passed to the constructor to be tested.
         */
        private long maxStop;

        /**
         * Creates an instance of
         * {@link ConnectionTimeConstructorsTest.TestCase} object.
         *
         * @param type     type of the constructor to be tested
         * @param expExcCl expected type of exception while invoking the
         * constructor
         */
        public TestCase(Object type, long arg1, long arg2, long arg3, long arg4,
                Class expExcCl) {
            super(type, 0, expExcCl);
            minStart = arg1;
            maxStart = arg2;
            minStop = arg3;
            maxStop = arg4;
        }

        /**
         * Invokes the constructor specified by this
         * {@link ConnectionTimeConstructorsTest.TestCase} object with
         * the argument specified by this
         * {@link ConnectionTimeConstructorsTest.TestCase} object.
         *
         * @throws TestException if any verification fails
         */
        public void callConstructor() throws TestException {
            logger.log(Level.FINE,
                    "+++++ invoking constructor " + constructorType.toString()
                    + "with the arguments:: ");

            if (constructorType != DELEGATION_ABSOLUTE_TIME__DATE) {
                logger.log(Level.FINE, "\tminStart:: " + minStart);
                logger.log(Level.FINE, "\tmaxStart:: " + maxStart);
                logger.log(Level.FINE, "\tminStop :: " + minStop);
                logger.log(Level.FINE, "\tmaxStop :: " + maxStop);
            }
            logger.log(Level.FINE,
                    "Expected type of exception:: " + expExceptionClass);

            /*
             * Try to call the constructor
             */
            InvocationConstraint constraint = null;

            try {
                
                if (       constructorType == DELEGATION_ABSOLUTE_TIME__DATE) {
                    Date minStartDate = (minStart == Long.MIN_VALUE) ?
                                        null : new Date(minStart);
                    Date maxStartDate = (maxStart == Long.MIN_VALUE) ?
                                        null : new Date(maxStart);
                    Date minStopDate = (minStop == Long.MIN_VALUE) ?
                                        null : new Date(minStop);
                    Date maxStopDate  = (maxStop == Long.MIN_VALUE) ?
                                        null : new Date(maxStop);
                    logger.log(Level.FINE,"\tminStart:: " + minStartDate);
                    logger.log(Level.FINE,"\tmaxStart:: " + maxStartDate);
                    logger.log(Level.FINE,"\tminStop :: " + minStopDate);
                    logger.log(Level.FINE,"\tmaxStop :: " + maxStopDate);
                    constraint = new DelegationAbsoluteTime(minStartDate,
                                                            maxStartDate,
                                                            minStopDate,
                                                            maxStopDate);
                } else if (constructorType == DELEGATION_ABSOLUTE_TIME__LONG) {
                    constraint = new DelegationAbsoluteTime(minStart, maxStart,
                                                            minStop, maxStop);
                } else if (constructorType == DELEGATION_RELATIVE_TIME__LONG) {
                    constraint = new DelegationRelativeTime(minStart, maxStart,
                                                            minStop, maxStop);
                }
                logger.log(Level.INFO, "Created constraint: " + constraint);
            } catch (Exception e) {
                logger.log(Level.FINE,
                        "Exception while invoking the constructor: " + e);
                // If no exception is expected
                if (expExceptionClass == null) {
                    throw new TestException("No exception is expected, but"
                            + " really " + e + " exception has been thrown"
                            + " while invoking the constructor");
                }

                // An exception is expected
                if (!(e.getClass()).equals(expExceptionClass)) {
                    throw new TestException("Instead of exception of "
                            + expExceptionClass + " exception of " + e.getClass()
                            + " has been thrown");
                }
                return;
            }

            // If an exception is expected
            if (expExceptionClass != null) {
                throw new TestException("Exception of type "
                        + expExceptionClass + " is expected, but really no"
                        + " exception has been thrown while invoking the"
                        + " constructor");
            }

            // No exception is expected
            try {
                Class cl = constraint.getClass();
                Method getMinStartMethod = cl.getMethod("getMinStart", null);
                Method getMaxStartMethod = cl.getMethod("getMaxStart", null);
                Method getMinStopMethod = cl.getMethod("getMinStop", null);
                Method getMaxStopMethod = cl.getMethod("getMaxStop", null);
                // Get the values from the created constraint and verify them
                checkTime("getMinStart()",
                        ((Long) getMinStartMethod.invoke(constraint,
                        null)).longValue(), minStart);
                checkTime("getMaxStart()",
                        ((Long) getMaxStartMethod.invoke(constraint,
                        null)).longValue(), maxStart);
                checkTime("getMinStop()",
                        ((Long) getMinStopMethod.invoke(constraint,
                        null)).longValue(), minStop);
                checkTime("getMaxStop()",
                        ((Long) getMaxStopMethod.invoke(constraint,
                        null)).longValue(), maxStop);
            } catch (TestException e) {
                throw e;
            } catch (Exception e) {
                logger.log(Level.FINE,
                        "Exception is thrown while invoking"
                        + " getMinStart()/getMaxStart()/getMinStop()/"
                        + "getMaxStop() methods using reflection: " + e);
                throw new TestException("Exception is thrown while invoking"
                        + " getMinStart()/getMaxStart()/getMinStop()/"
                        + "getMaxStop() methods using reflection: ", e);
            }
            return;
        }
        private void checkTime(String name, long d, long exp)
                throws TestException {
            logger.log(Level.INFO,
                    "\"" + name + "\" returned: " + d + "; expected: " + exp);

            if (d != exp) {
                throw new TestException("Expected that " + name + " returns: "
                        + exp + " but really " + d + " has been returned");
            }
            return;
        }
    }

    /**
     * Test Cases. The desciption of the test cases is in the class description.
     */

    public TestCase t_cases[] = {
        /*
         * DelegationAbsoluteTime(Date minStart, Date maxStart,
         *                        Date minStop, Date maxStop)
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__DATE, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                     null),
        /*
         * DelegationAbsoluteTime(Date minStart, Date maxStart,
         *                        Date minStop, Date maxStop)
         *   where minStart is null
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__DATE, Long.MIN_VALUE,
                     Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                     NullPointerException.class),
        /*
         * DelegationAbsoluteTime(Date minStart, Date maxStart,
         *                        Date minStop, Date maxStop)
         *   where maxStart is null
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__DATE, Long.MAX_VALUE,
                     Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                     NullPointerException.class),
        /*
         * DelegationAbsoluteTime(Date minStart, Date maxStart,
         *                        Date minStop, Date maxStop)
         *   where minStop is null
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__DATE, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE,
                     NullPointerException.class),
        /*
         * DelegationAbsoluteTime(Date minStart, Date maxStart,
         *                        Date minStop, Date maxStop)
         *   where maxStop is null
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__DATE, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MAX_VALUE, Long.MIN_VALUE,
                     NullPointerException.class),
        /*
         * DelegationAbsoluteTime(Date minStart, Date maxStart,
         *                        Date minStop, Date maxStop)
         *   where minStart is later than maxStart
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__DATE, Long.MAX_VALUE,
                     (long) 0, Long.MAX_VALUE, Long.MAX_VALUE,
                     IllegalArgumentException.class),
        /*
         * DelegationAbsoluteTime(Date minStart, Date maxStart,
         *                        Date minStop, Date maxStop)
         *   where maxStart is later than minStop
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__DATE, Long.MAX_VALUE,
                     Long.MAX_VALUE, 0, Long.MAX_VALUE,
                     IllegalArgumentException.class),
        /*
         * DelegationAbsoluteTime(Date minStart, Date maxStart,
         *                        Date minStop, Date maxStop)
         *   where minStop is later than maxStop
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__DATE, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MAX_VALUE, 0,
                     IllegalArgumentException.class),
        /*
         * DelegationAbsoluteTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__LONG, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                     null),
        /*
         * DelegationAbsoluteTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         *   where minStart is greater than maxStart
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__LONG, Long.MAX_VALUE,
                     0, Long.MAX_VALUE, Long.MAX_VALUE,
                     IllegalArgumentException.class),
        /*
         * DelegationAbsoluteTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         *   where maxStart is greater than minStop
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__LONG, Long.MAX_VALUE,
                     Long.MAX_VALUE, 0, Long.MAX_VALUE,
                     IllegalArgumentException.class),
        /*
         * DelegationAbsoluteTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         *   where minStop is greater than maxStop
         */
        new TestCase(DELEGATION_ABSOLUTE_TIME__LONG, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MAX_VALUE, 0,
                     IllegalArgumentException.class),
        /*
         * DelegationRelativeTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         */
        new TestCase(DELEGATION_RELATIVE_TIME__LONG, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                     null),
        /*
         * DelegationRelativeTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         *   where minStart is greater than maxStart
         */
        new TestCase(DELEGATION_RELATIVE_TIME__LONG, Long.MAX_VALUE,
                     0, Long.MAX_VALUE, Long.MAX_VALUE,
                     IllegalArgumentException.class),
        /*
         * DelegationRelativeTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         *   where maxStart is greater than minStop
         */
        new TestCase(DELEGATION_RELATIVE_TIME__LONG, Long.MAX_VALUE,
                     Long.MAX_VALUE, 0, Long.MAX_VALUE,
                     IllegalArgumentException.class),
        /*
         * DelegationRelativeTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         *   where minStop is greater than maxStop
         */
        new TestCase(DELEGATION_RELATIVE_TIME__LONG, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MAX_VALUE, 0,
                     IllegalArgumentException.class),
        /*
         * DelegationRelativeTime(long minStart, long maxStart,
         *                        long minStop, long maxStop)
         *   where minStop is less then zero
         */
        new TestCase(DELEGATION_RELATIVE_TIME__LONG, Long.MAX_VALUE,
                     Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE,
                     IllegalArgumentException.class)
    };

    /**
     * Auxiliary method to obtain the array of Test Cases.
     * @return array of Test Cases
     */
    public ConnectionTimeConstructorsTest.TestCase[] getTestCases() {
        return t_cases;
    }
}
