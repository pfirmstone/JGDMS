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
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATest;

// java.util
import java.util.logging.Level;

// Davis packages
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the following constructors:
 *     {@link net.jini.core.constraint.ConnectionAbsoluteTime#ConnectionAbsoluteTime(long)}
 *     {@link net.jini.core.constraint.ConnectionRelativeTime#ConnectionRelativeTime(long)}
 *   ConnectionAbsoluteTime() constructor creates a constraint with the
 *   specified deadline for connection establishment.
 *   Parameters:
 *     time - the deadline for connection establishment in milliseconds from
 *            midnight, January 1, 1970 UTC
 *   ConnectionRelativeTime() constructor creates a constraint with the
 *   specified duration.
 *   Parameters:
 *     time - the maximum connection duration in milliseconds
 *   Throws:
 *     {@link java.lang.IllegalArgumentException} - if the argument is less than
 *     zero
 *
 * Test Cases:
 *   TestCase #1
 *    It's verified that ConnectionAbsoluteTime() constructor creates a
 *    {@link net.jini.core.constraint.ConnectionAbsoluteTime} constraint with
 *    the specified deadline for connection establishment.
 *   TestCase #2
 *    It's verified that ConnectionRelativeTime() constructor creates a
 *    {@link net.jini.core.constraint.ConnectionRelativeTime} constraint with
 *    the specified duration.
 *   TestCase #3
 *    It's verified that ConnectionRelativeTime() constructor invoked with the
 *    argument that is less than zero throws java.lang.IllegalArgumentException.
 *
 * Infrastructure:
 *     - {@link ConnectionTimeConstructorsTest}
 *         performs actions; this file
 *     - {@link ConnectionTimeConstructorsTest.TestCase}
 *         auxiliary class that describes a Test Case
 *
 * Actions:
 *   Test performs the following steps:
 *     - constructing {@link ConnectionTimeConstructorsTest.TestCase} objects
 *       for all test cases;
 *     - invoking {@link ConnectionTimeConstructorsTest.TestCase#callConstructor()}
 *       method on each created {@link ConnectionTimeConstructorsTest.TestCase}
 *       object to invoke the corresponding constructor specified by this
 *       {@link ConnectionTimeConstructorsTest.TestCase} object;
 *     - comparing the result of the Test Case with the corresponding expected
 *       one.
 *
 * </pre>
 */
public class ConnectionTimeConstructorsTest extends QATest {
    QAConfig config;

    /**
     * An object to point to constructor:
     * {@link net.jini.core.constraint.ConnectionAbsoluteTime#ConnectionAbsoluteTime(long)}
     */
    Object CONNECTION_ABSOLUTE_TIME = new Object() {
        public String toString() {
            return "public ConnectionAbsoluteTime(long time)";
        }
    };

    /**
     * An object to point to constructor:
     * {@link net.jini.core.constraint.ConnectionRelativeTime#ConnectionRelativeTime(long)}
     */
    Object CONNECTION_RELATIVE_TIME = new Object() {
        public String toString() {
            return "public ConnectionRelativeTime(long time)";
        }
    };


    /**
     * An auxiliary class that describes a Test Case.
     */
    protected class TestCase {

        /**
         * Type of the constructor to be tested.
         * Possible types:
         *  CONNECTION_ABSOLUTE_TIME
         *  CONNECTION_RELATIVE_TIME
         */
        protected Object constructorType;

        /**
         * Argument to be passed to the constructor to be tested.
         */
        private long argument;

        /**
         * Expected time returned by the corresponding getTime() methods:
         * {@link net.jini.core.constraint.ConnectionAbsoluteTime#getTime()}
         * {@link net.jini.core.constraint.ConnectionRelativeTime#getTime()}
         */
        private long expTime;

        /**
         * Expected exception type thrown while invoking the constructor.
         */
        protected Class expExceptionClass;

        /**
         * Creates an instance of
         * {@link ConnectionTimeConstructorsTest.TestCase} object.
         *
         * @param type     type of the constructor to be tested
         * @param arg      argument to the constructors
         * @param expExcCl expected type of exception while invoking the
         * constructor
         */
        public TestCase(Object type, long arg, Class expExcCl) {
            constructorType = type;
            argument = arg;
            expTime = argument;
            expExceptionClass = expExcCl;
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
                    + " with argument: " + argument);
            logger.log(Level.FINE, "Expected time :: " + expTime);
            logger.log(Level.FINE,
                    "Expected type of exception:: " + expExceptionClass);

            /*
             * Try to call the constructor
             */
            long retTime = (long) -1;
            InvocationConstraint constraint = null;

            try {
                
                if (       constructorType == CONNECTION_ABSOLUTE_TIME) {
                    constraint = new ConnectionAbsoluteTime(argument);
                    retTime = ((ConnectionAbsoluteTime) constraint).getTime();
                } else if (constructorType == CONNECTION_RELATIVE_TIME) {
                    constraint = new ConnectionRelativeTime(argument);
                    retTime = ((ConnectionRelativeTime) constraint).getTime();
                }
                logger.log(Level.INFO, "Created constraint: " + constraint);
                logger.log(Level.INFO,
                        "getTime() on created constraint returned: " + retTime);
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
            if (retTime != expTime) {
                throw new TestException("Expected that getTime() on the"
                        + " created object returns " + expTime
                        + " but really " + retTime + " has been returned");
            }
            return;
        }
    }

    /**
     * Test Cases. The desciption of the test cases is in the class description.
     */
    public TestCase t_cases[] = {
            new TestCase(CONNECTION_ABSOLUTE_TIME, Long.MAX_VALUE, null),
            new TestCase(CONNECTION_RELATIVE_TIME, Long.MAX_VALUE, null),
            new TestCase(CONNECTION_RELATIVE_TIME, Long.MIN_VALUE,
                         IllegalArgumentException.class)
    };

    /**
     * Auxiliary method to obtain the array of Test Cases.
     * @return array of Test Cases
     */
    public TestCase[] getTestCases() {
        return t_cases;
    }

    /**
     * This method performs all test cases mentioned in the class description.
     */
    public void run() throws Exception {
        config = getConfig();
        TestCase tc[] = getTestCases();

        for (int i = 0; i < tc.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ TestCase #" + (i + (int) 1));

            tc[i].callConstructor();
        }

        // PASS
        return;
    }
}
