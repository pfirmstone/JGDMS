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
package com.sun.jini.test.spec.constraint.coreconstraint.constraintalternatives;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

// AbstractConstructorsTest
import com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractConstructorsTest;

// java.util
import java.util.logging.Level;
import java.util.Collections;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collection;

// Davis packages
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the following constructors:
 *     {@link net.jini.core.constraint.ConstraintAlternatives#ConstraintAlternatives(InvocationConstraint[])}
 *     {@link net.jini.core.constraint.ConstraintAlternatives#ConstraintAlternatives(Collection)}
 *   All these constructors create the an instance containing the specified
 *   alternative constraints, with duplicate constraints removed.
 *   The arguments passed to these constructors are neither modified nor
 *   retained; subsequent changes to that arguments have no effect on
 *   the instances created.
 *   Parameters: the alternative constraints
 *   Each constructor throws:
 *     {@link java.lang.NullPointerException} - if the argument is null or any
 *     element is null
 *     {@link java.lang.IllegalArgumentException} - if any of the elements are
 *     instances of ConstraintAlternatives, or if fewer than two elements remain
 *     after duplicate constraints are removed
 *   public ConstraintAlternatives(Collection c) constructor also throws
 *     {@link java.lang.IllegalArgumentException} - if the elements are not all
 *     instances of InvocationConstraint
 *
 * Test Cases:
 *   TestCase #1
 *     invoking constructor
 *       public ConstraintAlternatives(InvocationConstraint[] constraints)
 *     it's expected that ConstraintAlternatives object is created and contains
 *     the specified constraints, with duplicate constraints removed; it's
 *     expected that the argument passed to the constructor is neither modified
 *     nor retained; subsequent changes to that argument have no effect on the
 *     instance created.
 *   TestCase #2
 *     invoking constructor
 *       public ConstraintAlternatives((InvocationConstraint[]) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #3
 *     invoking constructor
 *       public ConstraintAlternatives(InvocationConstraint[] constraints),
 *       where constraints contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #4
 *     invoking constructor
 *       public ConstraintAlternatives(InvocationConstraint[] constraints),
 *       where constraints contains element that is an instance of
 *             ConstraintAlternatives
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #5
 *     invoking constructor
 *       public ConstraintAlternatives(InvocationConstraint[] constraints),
 *       and fewer than two elements remain after duplicate constraints
 *       are removed
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #6
 *     invoking constructor
 *       public ConstraintAlternatives(Collection c)
 *     it's expected that ConstraintAlternatives object is created and contains
 *     the specified constraints, with duplicate constraints removed; it's
 *     expected that the argument passed to the constructor is neither modified
 *     nor retained; subsequent changes to that argument have no effect on the
 *     instance created.
 *   TestCase #7
 *     invoking constructor
 *       public ConstraintAlternatives((Collection) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #8
 *     invoking constructor
 *       public ConstraintAlternatives(Collection c),
 *       where c contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #9
 *     invoking constructor
 *       public ConstraintAlternatives(Collection c),
 *       where c contains element that is an instance of
 *             ConstraintAlternatives
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #10
 *     invoking constructor
 *       public ConstraintAlternatives(Collection c),
 *       and fewer than two elements remain after duplicate constraints
 *       are removed
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #11
 *     invoking constructor
 *       public ConstraintAlternatives(Collection c),
 *       where c contains the element that isn't an instance of
 *             InvocationConstraint
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *
 * Infrastructure:
 *     - {@link ConstructorsTest}
 *         performs actions; this file
 *     - {@link com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractConstructorsTest}
 *         auxiliary abstract class that defines some methods
 *
 * Actions:
 *   Test performs the following steps in each Test Case:
 *     - constructing the argument for the constructor;
 *     - invoking the corresponding constructor;
 *     - checking that the corresponding object is created with the constraints
 *       specified as the argument or the corresponding exception of the
 *       expected type is thrown (see a Test Case description);
 * </pre>
 */
public class ConstructorsTest extends AbstractConstructorsTest {

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(InvocationConstraint[] constraints)
     */
    Object CONSTRUCTOR__ARRAY = new Object() {
        public String toString() {
            return "public ConstraintAlternatives("
                    + "InvocationConstraint[] constraints)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives((InvocationConstraint[]) null)
     *     NullPointerException is expected
     */
    Object CONSTRUCTOR__ARRAY_NULL = new Object() {
        public String toString() {
            return "public ConstraintAlternatives("
                    + "(InvocationConstraint[]) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(InvocationConstraint[] constraints),
     *     where constraints contains null element
     *     NullPointerException is expected
     */
    Object CONSTRUCTOR__ARRAY_NULL_EL = new Object() {
        public String toString() {
            return "public ConstraintAlternatives("
                    + "InvocationConstraint[] constraints),"
                    + " where constraints contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(InvocationConstraint[] constraints),
     *     where constraints contains element that is an instance of
     *           ConstraintAlternatives
     *     IllegalArgumentException is expected
     */
    Object CONSTRUCTOR__ARRAY_ILL_EL_ALT = new Object() {
        public String toString() {
            return "public ConstraintAlternatives("
                    + "InvocationConstraint[] constraints),"
                    + " where constraints contains element that is an instance"
                    + " of ConstraintAlternatives";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(InvocationConstraint[] constraints),
     *     and fewer than two elements remain after duplicate constraints
     *     are removed
     *     IllegalArgumentException is expected
     */
    Object CONSTRUCTOR__ARRAY_EL_NOT_ENOUGH = new Object() {

        public String toString() {
            return "public ConstraintAlternatives("
                    + "InvocationConstraint[] constraints),"
                    + " and fewer than two elements remain after duplicate"
                    + " constraints are removed";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(Collection c)
     */
    Object CONSTRUCTOR__COLL = new Object() {
        public String toString() {
            return "public ConstraintAlternatives(Collection c)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives((Collection) null)
     *     NullPointerException is expected
     */
    Object CONSTRUCTOR__COLL_NULL = new Object() {
        public String toString() {
            return "public ConstraintAlternatives((Collection) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(Collection c),
     *     where c contains null element
     *     NullPointerException is expected
     */
    Object CONSTRUCTOR__COLL_NULL_EL = new Object() {
        public String toString() {
            return "public ConstraintAlternatives(Collection c),"
                    + " where c contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(Collection c),
     *     where c contains element that is an instance of
     *           ConstraintAlternatives
     *     IllegalArgumentException is expected
     */
    Object CONSTRUCTOR__COLL_ILL_EL_ALT = new Object() {
        public String toString() {
            return "public ConstraintAlternatives(Collection c),"
                    + " where c contains element that is an instance of"
                    + " ConstraintAlternatives";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(Collection c),
     *     and fewer than two elements remain after duplicate constraints
     *     are removed
     *     IllegalArgumentException is expected
     */
    Object CONSTRUCTOR__COLL_EL_NOT_ENOUGH = new Object() {
        public String toString() {
            return "public ConstraintAlternatives(Collection c),"
                    + " and fewer than two elements remain after duplicate"
                    + " constraints are removed";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ConstraintAlternatives(Collection c),
     *     where c contains the element that isn't an instance of
     *           InvocationConstraint
     *     IllegalArgumentException is expected
     */
    Object CONSTRUCTOR__COLL_EL_NOT_IC = new Object() {
        public String toString() {
            return "public ConstraintAlternatives(Collection c),"
                    + " where c contains the element that isn't an instance of"
                    + " InvocationConstraint";
        }
    };

    /**
     * Test Cases.
     */
    Object[] testCases = new Object[] {
            CONSTRUCTOR__ARRAY,
            CONSTRUCTOR__ARRAY_NULL,
            CONSTRUCTOR__ARRAY_NULL_EL,
            CONSTRUCTOR__ARRAY_ILL_EL_ALT,
            CONSTRUCTOR__ARRAY_EL_NOT_ENOUGH,
            CONSTRUCTOR__COLL,
            CONSTRUCTOR__COLL_NULL,
            CONSTRUCTOR__COLL_NULL_EL,
            CONSTRUCTOR__COLL_ILL_EL_ALT,
            CONSTRUCTOR__COLL_EL_NOT_ENOUGH,
            CONSTRUCTOR__COLL_EL_NOT_IC
    };
    
    /**
     * Collection used to save argument to be passed to a constructor
     * before invoking the constructor.
     */
    Collection argBeforeInvoke = new ArrayList();

    /**
     * Collection used to verify that argument passed to a constructor
     * isn't modified.
     */
    Collection argAfterInvoke = new ArrayList();

    /**
     * Auxiliary method to obtain the array of the Objects that describe
     * Test Cases.
     * @return array of the Objects that describe Test Cases
     */
    public Object[] getTestCases() {
        return testCases;
    }

    /**
     * Execution of a Test Case.
     * For each Test Case the corresponding callConstructor() method is invoked.
     */
    public void runTestCase(Object testCase) throws TestException {
        logger.log(Level.INFO,
                "===== invoking constructor: " + testCase.toString());

        try {
            if (       testCase == CONSTRUCTOR__ARRAY) {
                callConstructor(testCase,
                        new InvocationConstraint[] {
                                Delegation.YES,
                                Delegation.YES,
                                Delegation.NO
                        }, null);
            } else if (testCase == CONSTRUCTOR__COLL) {
                Collection coll = new ArrayList();
                coll.add(Delegation.YES);
                coll.add(Delegation.YES);
                coll.add(Delegation.NO);
                callConstructor(testCase, coll, null);
            } else if (testCase == CONSTRUCTOR__ARRAY_NULL) {
                callConstructor(testCase, (InvocationConstraint[]) null,
                        NullPointerException.class);
            } else if (testCase == CONSTRUCTOR__COLL_NULL) {
                callConstructor(testCase, (Collection) null,
                        NullPointerException.class);
            } else if (testCase == CONSTRUCTOR__ARRAY_NULL_EL) {
                callConstructor(testCase,
                        new InvocationConstraint[] {
                                Delegation.YES,
                                null,
                                Delegation.NO
                        }, NullPointerException.class);
            } else if (testCase == CONSTRUCTOR__COLL_NULL_EL) {
                Collection coll = new ArrayList();
                coll.add(Delegation.YES);
                coll.add(null);
                coll.add(Delegation.NO);
                callConstructor(testCase, coll, NullPointerException.class);
            } else if (testCase == CONSTRUCTOR__ARRAY_ILL_EL_ALT) {
                callConstructor(testCase,
                        new InvocationConstraint[] {
                                Delegation.YES,
                                new ConstraintAlternatives(
                                        new InvocationConstraint[] {
                                                Delegation.YES,
                                                Delegation.NO
                                        }),
                                Delegation.NO
                        }, IllegalArgumentException.class);
            } else if (testCase == CONSTRUCTOR__COLL_ILL_EL_ALT) {
                Collection coll = new ArrayList();
                coll.add(Delegation.YES);
                coll.add(new ConstraintAlternatives(
                        new InvocationConstraint[] {
                                Delegation.YES,
                                Delegation.NO
                        }));
                coll.add(Delegation.NO);
                callConstructor(testCase, coll, IllegalArgumentException.class);
            } else if (testCase == CONSTRUCTOR__ARRAY_EL_NOT_ENOUGH) {
                callConstructor(testCase,
                        new InvocationConstraint[] {
                                Delegation.YES,
                                Delegation.YES
                        }, IllegalArgumentException.class);
            } else if (testCase == CONSTRUCTOR__COLL_EL_NOT_ENOUGH) {
                Collection coll = new ArrayList();
                coll.add(Delegation.YES);
                coll.add(Delegation.YES);
                callConstructor(testCase, coll, IllegalArgumentException.class);
            } else if (testCase == CONSTRUCTOR__COLL_EL_NOT_IC) {
                Collection coll = new ArrayList();
                coll.add(Delegation.YES);
                coll.add(Delegation.YES);
                coll.add("Not an instance of InvocationConstraint");
                callConstructor(testCase, coll, IllegalArgumentException.class);
            } else {
                logger.log(Level.FINE, "Bad Test Case: " + testCase.toString());
                throw new TestException(
                        "" + " test failed");
            }
        } catch (TestException e) {
            logger.log(Level.FINE, "Test Case failed: " + e);
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }

    /**
     * This method invokes the constructor
     * 'public ConstraintAlternatives(InvocationConstraint[] constraints)' and
     * checks the result.
     * <pre>
     * Then the following verifications are performed:
     *   - verify that ConstraintAlternatives object is created with
     *     duplicates removed;
     *   - verify that the argument passed to the constructor isn't modified;
     *   - verify that the argument passed to the constructor isn't retained,
     *     i.e. subsequent changes to that argument have no effect on the instance
     *     created.
     * </pre>
     * @param tc Test Case object
     * @param ic constraints to be used as the argument for the constructor
     * @param ex expected type of exception that should be thrown by the
     * constructor or null if no exception is expected
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc, InvocationConstraint[] ic,
            Class ex) throws TestException {

        /*
         * Copy object specified as an argument for the constructor before
         * invoking the constructor.
         */
        if (tc == CONSTRUCTOR__ARRAY) {
            argBeforeInvoke.clear();

            for (int i = 0; i < ic.length; i++) {
                argBeforeInvoke.add(ic[i]);
            }
        }
        ConstraintAlternatives constraint = null;

        try {
            constraint = new ConstraintAlternatives(ic);
            // If some Exception is expected
            if (       tc == CONSTRUCTOR__ARRAY_NULL
                    || tc == CONSTRUCTOR__ARRAY_NULL_EL
                    || tc == CONSTRUCTOR__ARRAY_ILL_EL_ALT
                    || tc == CONSTRUCTOR__ARRAY_EL_NOT_ENOUGH) {
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception while invoking constructor " + e);
            // If no Exception is expected
            if (tc == CONSTRUCTOR__ARRAY) {
                throw new TestException("Exception while invoking constructor ",
                        e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor");
            } else {
                return;
            }
        }

        // logger.log(Level.INFO, "Returned object: " + constraint.toString());

        /*
         * Verify that the corresponding constraint object is created.
         */
        if (constraint == null) {
            throw new TestException("Constraint object hasn't been created");
        }
        checkElements(constraint, argBeforeInvoke);

        /*
         * Verify that the argument passed to the constructor isn't modified.
         * Compare argument for the constructor before and after invoking the
         * constructor.
         */
        argAfterInvoke.clear();

        for (int i = 0; i < ic.length; i++) {
            argAfterInvoke.add(ic[i]);
        }
        logger.log(Level.INFO, "argAfterInvoke:: " + argAfterInvoke);

        if (!argBeforeInvoke.equals(argAfterInvoke)) {
            throw new TestException("The argument passed to the constructor"
                    + " is modified");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't modified");

        /*
         * Verify that the argument passed to the constructor isn't retained;
         * subsequent changes to that argument have no effect on the instance
         * created. Create ConstraintAlternatives object using create() method
         * and compare it with the ConstraintAlternatives object created with
         * constructor.
         */
        InvocationConstraint constraintBefore = null;

        try {
            constraintBefore =
                    ConstraintAlternatives.create((InvocationConstraint[]) ic);
        } catch (Exception e) {
            throw new TestException("Exception is thrown while invoking"
                    + " ConstraintAlternatives.create() method", e);
        }

        // Change argument passed to the constructor
        for (int i = 0; i < ic.length; i++) {
            ic[i] = null;
        }

        // Comparing 2 ConstraintAlternatives objects
        if (!constraint.equals(constraintBefore)) {
            throw new TestException("The argument passed to the"
                    + " constructor is retained");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't retained");

        /*
         * Verify that duplicates are removed.
         */
        checkDuplicates(constraint.elements());
        logger.log(Level.FINE, "Duplicates have been removed");
    }

    /**
     * This method invokes the constructor
     * 'public ConstraintAlternatives(Collection c)' and checks the result.
     * <pre>
     * Then the following verifications are performed:
     *   - verify that ConstraintAlternatives object is created with
     *     duplicates removed;
     *   - verify that the argument passed to the constructor isn't modified;
     *   - verify that the argument passed to the constructor isn't retained,
     *     i.e. subsequent changes to that argument have no effect on the instance
     *     created.
     * </pre>
     * @param tc Test Case object
     * @param c  constraints to be used as the argument for the constructor
     * @param ex expected type of exception that should be thrown by the
     * constructor or null if no exception is expected
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc, Collection c, Class ex)
            throws TestException {

        /*
         * Copy object specified as an argument for the constructor before
         * invoking the constructor.
         */
        if (tc == CONSTRUCTOR__COLL) {
            argBeforeInvoke.clear();
            argBeforeInvoke.addAll(c);
        }
        ConstraintAlternatives constraint = null;

        try {
            constraint = new ConstraintAlternatives(c);
            // If some Exception is expected
            if (       tc == CONSTRUCTOR__COLL_NULL
                    || tc == CONSTRUCTOR__COLL_NULL_EL
                    || tc == CONSTRUCTOR__COLL_ILL_EL_ALT
                    || tc == CONSTRUCTOR__COLL_EL_NOT_ENOUGH
                    || tc == CONSTRUCTOR__COLL_EL_NOT_IC) {
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception while invoking constructor " + e);
            // If no Exception is expected
            if (tc == CONSTRUCTOR__COLL) {
                throw new TestException("Exception while invoking constructor ",
                        e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor");
            } else {
                return;
            }
        }

        // logger.log(Level.INFO, "Returned object: " + constraint.toString());

        /*
         * Verify that the corresponding constraint object is created.
         */
        if (constraint == null) {
            throw new TestException("Constraint object hasn't been created");
        }
        checkElements(constraint, argBeforeInvoke);

        /*
         * Verify that the argument passed to the constructor isn't modified.
         * Compare argument for the constructor before and after invoking the
         * constructor.
         */
        argAfterInvoke.clear();
        argAfterInvoke.addAll(c);

        if (!argBeforeInvoke.equals(argAfterInvoke)) {
            throw new TestException("The argument passed to the constructor"
                    + " is modified");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't modified");

        /*
         * Verify that the argument passed to the constructor isn't retained;
         * subsequent changes to that argument have no effect on the instance
         * created. Create ConstraintAlternatives object using create() method
         * and compare it with the ConstraintAlternatives object created with
         * constructor.
         */
        InvocationConstraint constraintBefore = null;

        try {
            constraintBefore = ConstraintAlternatives.create((Collection) c);
        } catch (Exception e) {
            throw new TestException("Exception is thrown while invoking"
                    + " ConstraintAlternatives.create() method", e);
        }

        // Change argument passed to the constructor
        c.clear();
        // Comparing 2 ConstraintAlternatives objects
        if (!constraint.equals(constraintBefore)) {
            throw new TestException("The argument passed to the"
                    + " constructor is retained");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't retained");

        /*
         * Verify that duplicates are removed.
         */
        checkDuplicates(constraint.elements());
        logger.log(Level.FINE, "Duplicates have been removed");
    }

    /**
     * Verify if the specified set contains duplicates.
     *
     * @param set set to be verified
     * @throws TestException if there are duplicates in the specified set
     */
    private void checkDuplicates(Set set) throws TestException {
        Object[] arr = set.toArray();

        for (int j = 0; j < arr.length - 1; j++) {
            for (int i = j + 1; i < arr.length; i++) {
                if (arr[i].equals(arr[j])) {
                    logger.log(Level.FINE, "Duplicates aren't removed");
                    logger.log(Level.FINE, "arr[" + j + "]:: " + arr[j]);
                    logger.log(Level.FINE, "arr[" + i + "]:: " + arr[i]);
                    throw new TestException("Duplicates aren't removed");
                }
            }
        }
    }

    /**
     * Verify if the specified ConstraintAlternatives object contains the
     * specified constraints only.
     *
     * @param obj object to be verified (if this object contains the
     *            elements specified by the second argument only)
     * @param constraints only these elements should be in obj
     * @throws TestException if obj doesn't contain all the elements
     * specifed by constraints argument or does contain an element that
     * doesn't exist in constraints argument
     */
    private void checkElements(ConstraintAlternatives obj,
            Collection constraints) throws TestException {
        Set elements = obj.elements();
        logger.log(Level.FINE, "Class of elements: " + elements.getClass());
        logger.log(Level.FINE,
                "Elements in ConstraintAlternatives object: " + elements);
        HashSet set = new HashSet(constraints);
        logger.log(Level.FINE,
                "Elements that should be in ConstraintAlternatives object: "
                + set);

        if (!set.equals(elements)) {
            logger.log(Level.FINE,
                    "Expected elements in ConstraintAlternatives object: "
                    + set);
            logger.log(Level.FINE,
                    "Elements in ConstraintAlternatives object: " + elements);
            throw new TestException("ConstraintAlternatives object doesn't"
                    + " contain all the expected elements or does contain an"
                    + " unexpected element");
        }
    }
}
