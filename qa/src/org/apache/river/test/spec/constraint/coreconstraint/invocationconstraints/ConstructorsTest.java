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

// AbstractConstructorsTest
import org.apache.river.test.spec.constraint.coreconstraint.util.AbstractConstructorsTest;

// java.util
import java.util.logging.Level;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;

// Davis packages
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the constructors of
 *   {@link net.jini.core.constraint.InvocationConstraints} class.
 *   There are 3 constructors:
 *     1) {@link net.jini.core.constraint.InvocationConstraints#InvocationConstraints(InvocationConstraint,InvocationConstraint)}
 *     2) {@link net.jini.core.constraint.InvocationConstraints#InvocationConstraints(InvocationConstraint[],InvocationConstraint[])}
 *     3) {@link net.jini.core.constraint.InvocationConstraints#InvocationConstraints(Collection,Collection)}
 *   All these constructors create InvocationConstraints object with the
 *   specified requirement(s) and preference(s).
 *   Constructors of the forms 2) and 3) throw:
 *     {@link java.lang.NullPointerException}
 *       if any element of an argument is null
 *     {@link java.lang.IllegalArgumentException}
 *       if any element of an argument is not an instance of
 *       {@link net.jini.core.constraint.InvocationConstraint}
 *
 * Test Cases:
 *   TestCase #1
 *     invoking constructor
 *       public InvocationConstraints(InvocationConstraint req,
 *                                    InvocationConstraint pref);
 *     it's expected that InvocationConstraints object is created with
 *     req added as requirements and pref added as preferences;
 *   TestCase #2
 *     invoking constructor
 *       public InvocationConstraints((InvocationConstraint) null,
 *                                    InvocationConstraint pref);
 *     it's expected that InvocationConstraints object is created with
 *     empty requirements and pref added as preferences;
 *   TestCase #3
 *     invoking constructor
 *       public InvocationConstraints(InvocationConstraint req,
 *                                    (InvocationConstraint) null);
 *     it's expected that InvocationConstraints object is created with
 *     req added as requirements and empty preferences;
 *   TestCase #4
 *     invoking constructor
 *       public InvocationConstraints(InvocationConstraint[] reqs,
 *                                    InvocationConstraint[] prefs);
 *     it's expected that InvocationConstraints object is created with
 *     reqs added as requirements and prefs added as preferences;
 *   TestCase #5
 *     invoking constructor
 *       public InvocationConstraints((InvocationConstraint[]) null,
 *                                    InvocationConstraint[] prefs);
 *     it's expected that InvocationConstraints object is created with
 *     empty requirements and prefs added as preferences;
 *   TestCase #6
 *     invoking constructor
 *       public InvocationConstraints(InvocationConstraint[] reqs,
 *                                    (InvocationConstraint[]) null);
 *     it's expected that InvocationConstraints object is created with
 *     reqs added as requirements and empty preferences;
 *   TestCase #7
 *     invoking constructor
 *       public InvocationConstraints(InvocationConstraint[] reqs,
 *                                    InvocationConstraint[] prefs);
 *       where reqs contains an element which is null;
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #8
 *     invoking constructor
 *       public InvocationConstraints(InvocationConstraint[] reqs,
 *                                    InvocationConstraint[] prefs);
 *       where prefs contains an element which is null
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #9
 *     invoking constructor
 *       public InvocationConstraints(Collection reqs,
 *                                    Collection prefs);
 *     it's expected that InvocationConstraints object is created with
 *     reqs added as requirements and prefs added as preferences;
 *   TestCase #10
 *     invoking constructor
 *       public InvocationConstraints((Collection) null,
 *                                    Collection prefs);
 *     it's expected that InvocationConstraints object is created with
 *     empty requirements and prefs added as preferences;
 *   TestCase #11
 *     invoking constructor
 *       public InvocationConstraints(Collection reqs,
 *                                    (Collection) null);
 *     it's expected that InvocationConstraints object is created with
 *     reqs added as requirements and empty preferences;
 *   TestCase #12
 *     invoking constructor
 *       public InvocationConstraints(Collection reqs,
 *                                    Collection prefs);
 *       where reqs contains an element which is null;
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #13
 *     invoking constructor
 *       public InvocationConstraints(Collection reqs,
 *                                    Collection prefs);
 *       where prefs contains an element which is null;
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #14
 *     invoking constructor
 *       public InvocationConstraints(Collection reqs,
 *                                    Collection prefs);
 *       where reqs contains an element which isn't an instance of
 *             InvocationConstraint;
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #15
 *     invoking constructor
 *       public InvocationConstraints(Collection reqs,
 *                                    Collection prefs);
 *       where prefs contains an element which isn't an instance of
 *             InvocationConstraint;
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *
 * Infrastructure:
 *     - {@link ConstructorsTest}
 *         performs actions; this file
 *     - {@link org.apache.river.test.spec.constraint.coreconstraint.util.AbstractConstructorsTest}
 *         auxiliary abstract class that defines some methods
 *
 * Actions:
 *   Test performs the following steps in each Test Case:
 *     - constructing arguments for the particular constructor;
 *     - invoking the corresponding constructor;
 *     - checking that {@link net.jini.core.constraint.InvocationConstraints}
 *       object is created with the constraint(s) specified as the first
 *       argument (if non-null) added as requirements and with the
 *       constraint(s) specified as the second argument (if non-null) added as
 *       preferences or the Exception of the expected type is thrown;
 * </pre>
 */
public class ConstructorsTest extends AbstractConstructorsTest {

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(InvocationConstraint req,
     *                                InvocationConstraint pref)
     */
    Object INVOC_CONSTRAINT__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(InvocationConstraint req,"
                    + " InvocationConstraint pref)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints((InvocationConstraint) null,
     *                                InvocationConstraint pref)
     */
    Object INVOC_CONSTRAINT_NULL_REQ__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints((InvocationConstraint) null,"
                    + " InvocationConstraint pref)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(InvocationConstraint req,
     *                                (InvocationConstraint) null)
     */
    Object INVOC_CONSTRAINT_NULL_PREF__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(InvocationConstraint req,"
                    + " (InvocationConstraint) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(InvocationConstraint[] reqs,
     *                                InvocationConstraint[] prefs)
     */
    Object INVOC_CONSTRAINT_ARRAY__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(InvocationConstraint[] reqs,"
                    + " InvocationConstraint[] prefs)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints((InvocationConstraint[]) null,
     *                                InvocationConstraint[] prefs)
     */
    Object INVOC_CONSTRAINT_ARRAY_NULL_REQ__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints((InvocationConstraint[]) null,"
                    + " InvocationConstraint[] prefs)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(InvocationConstraint[] reqs,
     *                                (InvocationConstraint[]) null)
     */
    Object INVOC_CONSTRAINT_ARRAY_NULL_PREF__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(InvocationConstraint[] reqs,"
                    + " (InvocationConstraint[]) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(InvocationConstraint[] reqs,
     *                                InvocationConstraint[] prefs)
     *   where reqs contains an element which is null
     */
    Object INVOC_CONSTRAINT_ARRAY_NULL_EL_REQ__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(InvocationConstraint[] reqs,"
                    + " InvocationConstraint[] prefs)"
                    + ", where reqs contains an element which is null";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(InvocationConstraint[] reqs,
     *                                InvocationConstraint[] prefs)
     *   where prefs contains an element which is null
     */
    Object INVOC_CONSTRAINT_ARRAY_NULL_EL_PREF__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(InvocationConstraint[] reqs,"
                    + " InvocationConstraint[] prefs)"
                    + ", where prefs contains an element which is null";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(Collection reqs,
     *                                Collection prefs)
     */
    Object INVOC_CONSTRAINT_COLL__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(Collection reqs,"
                    + " Collection prefs)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints((Collection) null,
     *                                Collection prefs)
     */
    Object INVOC_CONSTRAINT_COLL_NULL_REQ__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints((Collection) null,"
                    + " Collection prefs)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(Collection reqs,
     *                                (Collection) null)
     */
    Object INVOC_CONSTRAINT_COLL_NULL_PREF__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(Collection reqs,"
                    + " (Collection) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(Collection reqs,
     *                                Collection prefs)
     *   where reqs contains an element which is null
     */
    Object INVOC_CONSTRAINT_COLL_NULL_EL_REQ__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(Collection reqs,"
                    + " Collection prefs)"
                    + ", where reqs contains an element which is null";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(Collection reqs,
     *                                Collection prefs)
     *   where prefs contains an element which is null
     */
    Object INVOC_CONSTRAINT_COLL_NULL_EL_PREF__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(Collection reqs,"
                    + " Collection prefs)"
                    + ", where prefs contains an element which is null";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(Collection reqs,
     *                                Collection prefs)
     *   where reqs contains an element which isn't an instance of
     *         InvocationConstraint
     */
    Object INVOC_CONSTRAINT_COLL_ILL_EL_REQ__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(Collection reqs,"
                    + " Collection prefs)"
                    + ", where reqs contains an element which isn't an"
                    + " instance of InvocationConstraint";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public InvocationConstraints(Collection reqs,
     *                                Collection prefs)
     *   where prefs contains an element which isn't an instance of
     *         InvocationConstraint
     */
    Object INVOC_CONSTRAINT_COLL_ILL_EL_PREF__TEST_CASE = new Object() {
        public String toString() {
            return "public InvocationConstraints(Collection reqs,"
                    + " Collection prefs)"
                    + ", where prefs contains an element which isn't an"
                    + " instance of InvocationConstraint";
        }
    };

    /**
     * Test Cases.
     */
    Object[] testCases = new Object[] {
            INVOC_CONSTRAINT__TEST_CASE,
            INVOC_CONSTRAINT_NULL_REQ__TEST_CASE,
            INVOC_CONSTRAINT_NULL_PREF__TEST_CASE,
            INVOC_CONSTRAINT_ARRAY__TEST_CASE,
            INVOC_CONSTRAINT_ARRAY_NULL_REQ__TEST_CASE,
            INVOC_CONSTRAINT_ARRAY_NULL_PREF__TEST_CASE,
            INVOC_CONSTRAINT_ARRAY_NULL_EL_REQ__TEST_CASE,
            INVOC_CONSTRAINT_ARRAY_NULL_EL_PREF__TEST_CASE,
            INVOC_CONSTRAINT_COLL__TEST_CASE,
            INVOC_CONSTRAINT_COLL_NULL_REQ__TEST_CASE,
            INVOC_CONSTRAINT_COLL_NULL_PREF__TEST_CASE,
            INVOC_CONSTRAINT_COLL_NULL_EL_REQ__TEST_CASE,
            INVOC_CONSTRAINT_COLL_NULL_EL_PREF__TEST_CASE,
            INVOC_CONSTRAINT_COLL_ILL_EL_REQ__TEST_CASE,
            INVOC_CONSTRAINT_COLL_ILL_EL_PREF__TEST_CASE
    };
    
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
            if (       testCase == INVOC_CONSTRAINT__TEST_CASE) {
                callConstructor(testCase, Confidentiality.YES,
                                          Confidentiality.NO);
            } else if (testCase == INVOC_CONSTRAINT_NULL_REQ__TEST_CASE) {
                callConstructor(testCase, (InvocationConstraint) null,
                                          Confidentiality.YES);
            } else if (testCase == INVOC_CONSTRAINT_NULL_PREF__TEST_CASE) {
                callConstructor(testCase, Confidentiality.YES,
                                          (InvocationConstraint) null);
            } else if (testCase == INVOC_CONSTRAINT_ARRAY__TEST_CASE) {
                InvocationConstraint[] req = {
                          Confidentiality.YES,
                          Delegation.YES 
                };
                InvocationConstraint[] pref = {
                          Confidentiality.NO,
                          Delegation.NO 
                };
                callConstructor(testCase, req, pref, null);
            } else if (testCase 
                    == INVOC_CONSTRAINT_ARRAY_NULL_REQ__TEST_CASE) {
                InvocationConstraint[] pref = {
                          Confidentiality.NO,
                          Delegation.NO 
                };
                callConstructor(testCase, (InvocationConstraint[]) null, pref,
                                          null);
            } else if (testCase 
                    == INVOC_CONSTRAINT_ARRAY_NULL_PREF__TEST_CASE) {
                InvocationConstraint[] req = {
                          Confidentiality.YES,
                          Delegation.YES 
                };
                callConstructor(testCase, req, (InvocationConstraint[]) null,
                                          null);
            } else if (testCase 
                    == INVOC_CONSTRAINT_ARRAY_NULL_EL_REQ__TEST_CASE) {
                InvocationConstraint[] req = {
                          Confidentiality.YES,
                          Delegation.YES,
                          null 
                };
                InvocationConstraint[] pref = {
                          Confidentiality.NO,
                          Delegation.NO 
                };
                callConstructor(testCase, req, pref,
                                          NullPointerException.class);
            } else if (testCase 
                    == INVOC_CONSTRAINT_ARRAY_NULL_EL_PREF__TEST_CASE) {
                InvocationConstraint[] req = {
                          Confidentiality.YES,
                          Delegation.YES 
                };
                InvocationConstraint[] pref = {
                          Confidentiality.NO,
                          Delegation.NO, null 
                };
                callConstructor(testCase, req, pref,
                                          NullPointerException.class);
            } else if (testCase == INVOC_CONSTRAINT_COLL__TEST_CASE) {
                HashSet req = new HashSet();
                req.add(Confidentiality.YES);
                req.add(Delegation.YES);
                HashSet pref = new HashSet();
                pref.add(Confidentiality.NO);
                pref.add(Delegation.NO);
                callConstructor(testCase, (Collection) req, (Collection) pref,
                                          null);
            } else if (testCase 
                    == INVOC_CONSTRAINT_COLL_NULL_REQ__TEST_CASE) {
                HashSet pref = new HashSet();
                pref.add(Confidentiality.NO);
                pref.add(Delegation.NO);
                callConstructor(testCase, (Collection) null, (Collection) pref,
                                          null);
            } else if (testCase 
                    == INVOC_CONSTRAINT_COLL_NULL_PREF__TEST_CASE) {
                HashSet req = new HashSet();
                req.add(Confidentiality.YES);
                req.add(Delegation.YES);
                callConstructor(testCase, (Collection) req, (Collection) null,
                                          null);
            } else if (testCase 
                    == INVOC_CONSTRAINT_COLL_NULL_EL_REQ__TEST_CASE) {
                HashSet req = new HashSet();
                req.add(Confidentiality.YES);
                req.add(Delegation.YES);
                req.add(null);
                HashSet pref = new HashSet();
                pref.add(Confidentiality.NO);
                pref.add(Delegation.NO);
                callConstructor(testCase, (Collection) req, (Collection) pref,
                                          NullPointerException.class);
            } else if (testCase 
                    == INVOC_CONSTRAINT_COLL_NULL_EL_PREF__TEST_CASE) {
                HashSet req = new HashSet();
                req.add(Confidentiality.YES);
                req.add(Delegation.YES);
                HashSet pref = new HashSet();
                pref.add(Confidentiality.NO);
                pref.add(Delegation.NO);
                pref.add(null);
                callConstructor(testCase, (Collection) req, (Collection) pref,
                                          NullPointerException.class);
            } else if (testCase 
                    == INVOC_CONSTRAINT_COLL_ILL_EL_REQ__TEST_CASE) {
                HashSet req = new HashSet();
                req.add(Confidentiality.YES);
                req.add(Delegation.YES);
                req.add("Not an instance of InvocationConstraint");
                HashSet pref = new HashSet();
                pref.add(Confidentiality.NO);
                pref.add(Delegation.NO);
                callConstructor(testCase, (Collection) req, (Collection) pref,
                                          IllegalArgumentException.class);
            } else if (testCase 
                    == INVOC_CONSTRAINT_COLL_ILL_EL_PREF__TEST_CASE) {
                HashSet req = new HashSet();
                req.add(Confidentiality.YES);
                req.add(Delegation.YES);
                HashSet pref = new HashSet();
                pref.add(Confidentiality.NO);
                pref.add(Delegation.NO);
                pref.add("Not an instance of InvocationConstraint");
                callConstructor(testCase, (Collection) req, (Collection) pref,
                                          IllegalArgumentException.class);
            } else {
                logger.log(Level.FINE, "Bad Test Case: " + testCase.toString());
                throw new TestException(
                        "" + " test failed");
            }
        } catch (TestException e) {
            logger.log(Level.FINE, "Test Case failed: " + e);
            throw new TestException(
                    "" + " test failed");
        } catch (Exception e) {
            logger.log(Level.FINE, "Test Case failed: Unexpected Exception: "
                    + e);
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }

    /**
     * This method invokes constructor and checks the result.
     * <pre>
     * The method invokes constructor:
     *    public InvocationConstraints(InvocationConstraint req,
     *                                 InvocationConstraint pref)
     *
     * Then the following verifications are performed:
     *   - verify that {@link net.jini.core.constraint.InvocationConstraints}
     *     object is created;
     *   - verify that the first constraint, req, is added as a requirement
     *     if it is a non-null value;
     *   - verify that the second constraint, pref, is added as a preference
     *     if it is a non-null value.
     * </pre>
     * @param tc    test case object
     * @param req   constraint specified as requirement
     * @param pref  constraint specified as preference
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc,
                                   InvocationConstraint req,
                                   InvocationConstraint pref)
            throws TestException {
        InvocationConstraints ic = null;

        try {
            ic = new InvocationConstraints(req, pref);
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception while invoking constructor " + tc.toString()
                    + ":: " + e);
            throw new TestException("Exception while invoking constructor "
                    + tc.toString(), e);
        }

        /*
         * Verify that InvocationConstraints object is created.
         */
        if (ic == null) {
            logger.log(Level.FINE,
                    "InvocationConstraints object hasn't been created");
            throw new TestException("InvocationConstraints object hasn't"
                    + " been created");
        }
        logger.log(Level.FINE, "Returned object: " + ic.toString());

        /*
         * Verify that the first constraint, req, is added as a requirement
         * if it is a non-null value.
         */
        Set reqs = ic.requirements();

        if (req == null) {
            if (!reqs.isEmpty()) {
                String s = "Expected that no requirement has been added when"
                        + " req is null; Really requirements aren't empty";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        } else {
            if (!reqs.contains(req)) {
                String s = "Expected that requirement has been added when req"
                        + " is non-null; Really requirements doesn't contain"
                        + " the specified req";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        }

        /*
         * Verify that the second constraint, pref, is added as a preference
         * if it is a non-null value.
         */
        Set prefs = ic.preferences();

        if (pref == null || pref == req) {
            if (!prefs.isEmpty()) {
                String s = "Expected that no preference has been added"
                        + " when pref is null or is duplicate of req;"
                        + " Really preferences aren't empty";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        } else {
            if (!prefs.contains(pref)) {
                String s = "Expected that preference has been added when pref"
                        + " is non-null and isn't duplicate of req;"
                        + " Really preferences doesn't contain"
                        + " the specified pref";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        }
    }

    /**
     * This method invokes constructor and checks the result.
     * <pre>
     * The method invokes constructor:
     *    public InvocationConstraints(InvocationConstraint[] reqs,
     *                                 InvocationConstraint[] prefs)
     *
     * Then the following verifications are performed:
     *   - verify that {@link net.jini.core.constraint.InvocationConstraints}
     *     object is created or the Exception of the expected type is thrown;
     *   - verify that the first constraints, req, are added as a requirements
     *     if it is a non-null value;
     *   - verify that the second constraints, pref, are added as a preferences
     *     if it is a non-null value.
     * </pre>
     * @param tc    test case object
     * @param req   constraints specified as requirements
     * @param pref  constraints specified as preferences
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc,
                                   InvocationConstraint[] req,
                                   InvocationConstraint[] pref,
                                   Class ex)
            throws TestException {
        logger.log(Level.FINE, tc.toString());

        if (req != null) {
            for (int i = 0; i < req.length; i++) {
                logger.log(Level.FINE, "\treq[" + i + "]:: " + req[i]);
            }
        } else {
            logger.log(Level.FINE, "\treq:: null");
        }

        if (pref != null) {
            for (int i = 0; i < pref.length; i++) {
                logger.log(Level.FINE, "\tpref[" + i + "]:: " + pref[i]);
            }
        } else {
            logger.log(Level.FINE, "\tpref:: null");
        }
        InvocationConstraints ic = null;

        try {
            ic = new InvocationConstraints(req, pref);
            // If some Exception is expected
            if (ex != null) {
                logger.log(Level.FINE, "Expected Exception type:: " + ex);
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor "
                        + tc.toString());
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception while invoking constructor " + tc.toString()
                    + ":: " + e);
            // If no Exception is expected
            if (ex == null) {
                throw new TestException("Exception while invoking constructor "
                        + tc.toString(), e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor " + tc.toString());
            } else {
                return;
            }
        }

        /*
         * Verify that InvocationConstraints object is created.
         */
        if (ic == null) {
            logger.log(Level.FINE,
                    "InvocationConstraints object hasn't been created");
            throw new TestException("InvocationConstraints object hasn't"
                    + " been created");
        }
        logger.log(Level.FINE, "Returned object: " + ic.toString());

        /*
         * Verify that the first constraint, req, is added as a requirement
         * if it is a non-null value.
         */
        Set reqs = ic.requirements();

        if (req == null) {
            if (!reqs.isEmpty()) {
                String s = "Expected that no requirement has been added when"
                        + " req is null; Really requirements aren't empty";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        } else {
            for (int i = 0; i < req.length; i++) {
                if (!reqs.contains(req[i])) {
                    String s = "Expected that requirement has been added when"
                            + " req is non-null; Really requirements doesn't"
                            + " contain the specified req";
                    logger.log(Level.FINE, s);
                    throw new TestException(s);
                }
            }
        }

        /*
         * Verify that the second constraint, pref, is added as a preference
         * if it is a non-null value.
         */
        Set prefs = ic.preferences();

        if (pref == null) {
            if (!prefs.isEmpty()) {
                String s = "Expected that no preference has been added"
                        + " when pref is null;"
                        + " Really preferences aren't empty";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        } else {
            for (int i = 0; i < pref.length; i++) {
                if (!prefs.contains(pref[i])) {
                    String s = "Expected that preference has been added when"
                            + " pref is non-null;"
                            + " Really preferences doesn't contain"
                            + " the specified pref";
                    logger.log(Level.FINE, s);
                    throw new TestException(s);
                }
            }
        }
    }

    /**
     * This method invokes constructor and checks the result.
     * <pre>
     * The method invokes constructor:
     *    public InvocationConstraints(Collection reqs,
     *                                 collection prefs)
     *
     * Then the following verifications are performed:
     *   - verify that {@link net.jini.core.constraint.InvocationConstraints}
     *     object is created or the Exception of the expected type is thrown;
     *   - verify that the first constraints, req, is added as a requirements
     *     if it is a non-null value;
     *   - verify that the second constraints, pref, is added as a preferences
     *     if it is a non-null value.
     * </pre>
     * @param tc    test case object
     * @param req   constraints specified as requirements
     * @param pref  constraints specified as preferences
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc, Collection req, Collection pref,
            Class ex) throws TestException {
        logger.log(Level.FINE, tc.toString());

        if (req != null) {
            logger.log(Level.FINE, "\treq::");

            for (Iterator it = req.iterator(); it.hasNext();) {
                logger.log(Level.FINE, "\t\t" + it.next());
            }
        } else {
            logger.log(Level.FINE, "\treq:: null");
        }

        if (pref != null) {
            logger.log(Level.FINE, "\tpref::");

            for (Iterator it = pref.iterator(); it.hasNext();) {
                logger.log(Level.FINE, "\t\t" + it.next());
            }
        } else {
            logger.log(Level.FINE, "\tpref:: null");
        }
        InvocationConstraints ic = null;

        try {
            ic = new InvocationConstraints(req, pref);
            // If some Exception is expected
            if (ex != null) {
                logger.log(Level.FINE, "Expected Exception type:: " + ex);
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor "
                        + tc.toString());
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception while invoking constructor " + tc.toString()
                    + ":: " + e);
            // If no Exception is expected
            if (ex == null) {
                throw new TestException("Exception while invoking constructor "
                        + tc.toString(), e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor " + tc.toString());
            } else {
                return;
            }
        }

        /*
         * Verify that InvocationConstraints object is created.
         */
        if (ic == null) {
            logger.log(Level.FINE,
                    "InvocationConstraints object hasn't been created");
            throw new TestException("InvocationConstraints object hasn't"
                    + " been created");
        }
        logger.log(Level.FINE, "Returned object: " + ic.toString());

        /*
         * Verify that the first constraint, req, is added as a requirement
         * if it is a non-null value.
         */
        Set reqs = ic.requirements();

        if (req == null) {
            if (!reqs.isEmpty()) {
                String s = "Expected that no requirement has been added when"
                        + " req is null; Really requirements aren't empty";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        } else {
            if (!reqs.equals(req)) {
                String s = "Expected that requirement has been added when"
                        + " req is non-null; Really requirements doesn't"
                        + " contain the specified req";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        }

        /*
         * Verify that the second constraint, pref, is added as a preference
         * if it is a non-null value.
         */
        Set prefs = ic.preferences();

        if (pref == null) {
            if (!prefs.isEmpty()) {
                String s = "Expected that no preference has been added"
                        + " when pref is null;"
                        + " Really preferences aren't empty";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        } else {
            if (!prefs.equals(pref)) {
                String s = "Expected that preference has been added when"
                        + " pref is non-null;"
                        + " Really preferences doesn't contain"
                        + " the specified pref";
                logger.log(Level.FINE, s);
                throw new TestException(s);
            }
        }
    }
}
