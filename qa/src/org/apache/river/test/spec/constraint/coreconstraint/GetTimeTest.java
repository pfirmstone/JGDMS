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

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;

// java.util
import org.apache.river.qa.harness.Test;
import java.util.logging.Level;

// java.lang.reflect
import java.lang.reflect.Method;

// Davis packages
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.DelegationAbsoluteTime;
import net.jini.core.constraint.DelegationRelativeTime;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the following methods:
 *     {@link net.jini.core.constraint.ConnectionAbsoluteTime#getTime()}
 *     {@link net.jini.core.constraint.ConnectionRelativeTime#getTime()}
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#getMinStart()}
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#getMaxStart()}
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#getMinStop()}
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#getMaxStop()}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#getMinStart()}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#getMaxStart()}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#getMinStop()}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#getMaxStop()}
 *  ConnectionAbsoluteTime.getTime() method
 *    returns the deadline for connection establishment;
 *  ConnectionRelativeTime.getTime() method
 *    returns the maximum connection duration in milliseconds;
 *  DelegationAbsoluteTime.getMinStart() method
 *    returns the minimum start time in milliseconds from midnight, January 1,
 *            1970 UTC;
 *  DelegationAbsoluteTime.getMaxStart() method
 *    returns the maximum start time in milliseconds from midnight, January 1,
 *            1970 UTC;
 *  DelegationAbsoluteTime.getMinStop() method
 *    returns the minimum stop time in milliseconds from midnight, January 1,
 *            1970 UTC;
 *  DelegationAbsoluteTime.getMaxStop() method
 *    returns the maximum stop time in milliseconds from midnight, January 1,
 *            1970 UTC;
 *  DelegationRelativeTime.getMinStart() method
 *    returns the minimum start duration in milliseconds;
 *  DelegationRelativeTime.getMaxStart() method
 *    returns the maximum start duration in milliseconds;
 *  DelegationRelativeTime.getMinStop() method
 *    returns the minimum stop duration in milliseconds;
 *  DelegationRelativeTime.getMaxStop() method
 *    returns the maximum stop duration in milliseconds;
 *
 * Test Cases:
 *   TestCase #1-2
 *    It's verified that ConnectionAbsoluteTime.getTime() method returns
 *    the deadline for connection establishment.
 *   TestCase #3-4
 *    It's verified that ConnectionRelativeTime.getTime() method returns
 *    the maximum connection duration in milliseconds.
 *   TestCase #5-6
 *    It's verified that DelegationAbsoluteTime.getMinStart() method returns
 *    the minimum start time in milliseconds from midnight, January 1,
 *    1970 UTC.
 *   TestCase #7-8
 *    It's verified that DelegationAbsoluteTime.getMaxStart() method returns
 *    the maximum start time in milliseconds from midnight, January 1,
 *    1970 UTC.
 *   TestCase #9-10
 *    It's verified that DelegationAbsoluteTime.getMinStop() method returns
 *    the minimum stop time in milliseconds from midnight, January 1,
 *    1970 UTC.
 *   TestCase #11-12
 *    It's verified that DelegationAbsoluteTime.getMaxStop() method returns
 *    the maximum stop time in milliseconds from midnight, January 1,
 *    1970 UTC.
 *   TestCase #13-14
 *    It's verified that DelegationRelativeTime.getMinStart() method returns
 *    the minimum start duration in milliseconds.
 *   TestCase #15-16
 *    It's verified that DelegationRelativeTime.getMaxStart() method returns
 *    the maximum start duration in milliseconds.
 *   TestCase #17-18
 *    It's verified that DelegationRelativeTime.getMinStop() method returns
 *    the minimum stop duration in milliseconds.
 *   TestCase #19-20
 *    It's verified that DelegationRelativeTime.getMaxStop() method returns
 *    the maximum stop duration in milliseconds.
 *
 * Infrastructure:
 *     - {@link GetTimeTest}
 *         performs actions; this file
 *     - {@link GetTimeTest.TestCase}
 *         auxiliary class that describes a Test Case
 *
 * Actions:
 *   Test performs the following steps:
 *     - constructing {@link GetTimeTest.TestCase} objects for all test cases;
 *     - invoking {@link GetTimeTest.TestCase#callMethod()} method on each
 *       created {@link GetTimeTest.TestCase} object to invoke the corresponding
 *       method specified by this {@link GetTimeTest.TestCase} object;
 *     - comparing the result of the Test Case with the corresponding expected
 *       one.
 *
 * </pre>
 */
public class GetTimeTest extends QATestEnvironment implements Test {
    QAConfig config;


    /**
     * An auxiliary class that describes a Test Case.
     */
    public class TestCase {

        /**
         * Constraint. Methods to be tested is invoked on this object.
         */
        private InvocationConstraint constraint;

        /**
         * The name of the method to be tested.
         */
        private String methodName;

        /**
         * Expected time returned by the method to be tested.
         */
        private long expTime;

        /**
         * Creates an instance of {@link GetTimeTest.TestCase} object.
         *
         * @param ic    constraint on which the method is invoked
         * @param mName the name of the method to be tested
         * @param expT  expected result of the method to be tested
         */
        public TestCase(InvocationConstraint ic, String mName, long expT) {
            constraint = ic;
            methodName = new String(mName);
            expTime = expT;
        }

        /**
         * Runs the method to be tested on the constraint.
         *
         * @throws TestException if any verification fails for the method
         */
        public void callMethod() throws TestException {
            logger.log(Level.INFO,
                    "+++++ Invoking " + constraint.getClass().getName() + "."
                    + methodName + "() method");
            logger.log(Level.INFO, "Constraint    :: " + constraint);
            logger.log(Level.INFO, "Expected time :: " + expTime);

            /*
             * Invoke the method to be tested
             */
            try {
                Class cl = constraint.getClass();
                Method method = cl.getMethod(methodName, null);
                long retTime =
                        ((Long) method.invoke(constraint, null)).longValue();
                logger.log(Level.INFO, "Returned time :: " + retTime);

                if (retTime != expTime) {
                    throw new TestException("Expected that " + methodName
                            + "() invoked on " + constraint + " returns "
                            + expTime + " but really " + retTime
                            + " has been returned");
                }
            } catch (TestException e) {
                throw e;
            } catch (Exception e) {
                throw new TestException("Exception is thrown while invoking "
                        + methodName + "() method using reflection: ", e);
            }
            logger.log(Level.INFO, "Test Case passed");
            return;
        }
    }

    /**
     * Test Cases. The desciption of the test cases is in the class description.
     */
    public TestCase t_cases[] = {
        new TestCase(new ConnectionAbsoluteTime(Long.MAX_VALUE),
                     "getTime",
                     Long.MAX_VALUE),
        new TestCase(new ConnectionAbsoluteTime(0),
                     "getTime",
                     0),
        new TestCase(new ConnectionRelativeTime(Long.MAX_VALUE),
                     "getTime",
                     Long.MAX_VALUE),
        new TestCase(new ConnectionRelativeTime(0),
                     "getTime",
                     0),
        new TestCase(new DelegationAbsoluteTime(Long.MAX_VALUE - 1, Long.MAX_VALUE,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     "getMinStart",
                     Long.MAX_VALUE - 1),
        new TestCase(new DelegationRelativeTime(Long.MAX_VALUE - 1, Long.MAX_VALUE,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     "getMinStart",
                     Long.MAX_VALUE - 1),
        new TestCase(new DelegationAbsoluteTime(0, Long.MAX_VALUE,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     "getMinStart",
                     0),
        new TestCase(new DelegationRelativeTime(0, Long.MAX_VALUE,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     "getMinStart",
                     0),
        new TestCase(new DelegationAbsoluteTime(0, Long.MAX_VALUE - 1,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     "getMaxStart",
                     Long.MAX_VALUE - 1),
        new TestCase(new DelegationRelativeTime(0, Long.MAX_VALUE - 1,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     "getMaxStart",
                     Long.MAX_VALUE - 1),
        new TestCase(new DelegationAbsoluteTime(0, 0,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     "getMaxStart",
                     0),
        new TestCase(new DelegationRelativeTime(0, 0,
                                                Long.MAX_VALUE, Long.MAX_VALUE),
                     "getMaxStart",
                     0),
        new TestCase(new DelegationAbsoluteTime(0, 0, Long.MAX_VALUE - 1,
                                                Long.MAX_VALUE),
                     "getMinStop",
                     Long.MAX_VALUE - 1),
        new TestCase(new DelegationRelativeTime(0, 0, Long.MAX_VALUE - 1,
                                                Long.MAX_VALUE),
                     "getMinStop",
                     Long.MAX_VALUE - 1),
        new TestCase(new DelegationAbsoluteTime(0, 0, 0, Long.MAX_VALUE),
                     "getMinStop",
                     0),
        new TestCase(new DelegationRelativeTime(0, 0, 0, Long.MAX_VALUE),
                     "getMinStop",
                     0),
        new TestCase(new DelegationAbsoluteTime(0, 0, 0, Long.MAX_VALUE),
                     "getMaxStop",
                     Long.MAX_VALUE),
        new TestCase(new DelegationRelativeTime(0, 0, 0, Long.MAX_VALUE),
                     "getMaxStop",
                     Long.MAX_VALUE),
        new TestCase(new DelegationAbsoluteTime(0, 0, 0, 0),
                     "getMaxStop",
                     0),
        new TestCase(new DelegationRelativeTime(0, 0, 0, 0),
                     "getMaxStop",
                     0)
    };

    /**
     * This method runs all Test Cases specified in the class description.
     */
    public void run() throws Exception {
        config = getConfig();

        for (int i = 0; i < t_cases.length; i++) {
            logger.log(Level.FINE, "\n\t+++++ TestCase #" + (i + (int) 1));

            t_cases[i].callMethod();
        }
        //PASS
        return;
    }
}
