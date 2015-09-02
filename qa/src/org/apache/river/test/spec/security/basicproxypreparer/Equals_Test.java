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
package org.apache.river.test.spec.security.basicproxypreparer;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.security.BasicProxyPreparer;
import java.security.Permission;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the BasicProxyPreparer class
 *   equals method:
 *   boolean equals(Object object)
 *
 * Test Cases:
 *   This test contains several test case - one combination for each form
 *   of constructor, verify value true and false and methodConstrains value
 *   null, empty and not empty. Each case goes through actions described below
 *   in case if they have a sense for that form.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeBasicProxyPreparer - accessor of the tested BasicProxyPreparer
 *        class, that is gateway to protected method.
 *     2) FakeMethodConstraints - a fake MethodConstraints object with
 *        configurable method return values.
 *
 * Actions:
 *   Test checks normal and exceptional variants of the
 *   parameters for the getMethodConstraints method.
 *
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns true if the given object is an instance of the same
 *	  class as this object, with the same value for verify, with	
 *	  method constraints that are equals ...,
 *	  and with permissions containing the same elements, independent 
 *	  of order.
 *     a) returns true for the same object;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        call equals method passing the same object as argument;
 *        assert true is returned;
 *     b) returns true for another equal object;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        construct another BasicProxyPreparer object passing true for "verify",
 *        the same values for "permissions" and the same instance for
 *        "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert true is returned;
 *     c) an instance of not the same class;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        construct FakeBasicProxyPreparer object passing true for "verify",
 *        the same values for "permissions" and the same instance for
 *        "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert false is returned;
 *        invoke equals method from the first object passing null
 *        as an argument;
 *        assert false is returned;
 *     d) not the same value for "verify";
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        construct another BasicProxyPreparer object passing false for
 *        "verify", the same values for "permissions" and the same instance for
 *        "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert false is returned;
 *        invoke equals method from the second object passing the first object
 *        as an argument;
 *        assert false is returned;
 *     e) not equal method constraints;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        construct another BasicProxyPreparer object passing true for "verify",
 *        the same values for "permissions" and some another different
 *        instance for "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert false is returned;
 *     f) specified and not specified method constraints;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        construct another BasicProxyPreparer object passing true for "verify",
 *        the same values for "permissions" and null for "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert false is returned;
 *        invoke equals method from the second object passing the first object
 *        as an argument;
 *        assert false is returned;
 *     g) permissions containing not the same elements;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        construct another BasicProxyPreparer object passing true for "verify",
 *        some another different values for "permissions" and the same
 *        instance for "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert false is returned;
 *     h) permissions containing the same elements, but in the another order;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        construct another BasicProxyPreparer object passing true for "verify",
 *        some another values for "permissions" that contains the same
 *        permissions but in the different order and the same instance for
 *        "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert true is returned;
 *     2) Returns true if the given object is an instance of the same
 *	  class as this object, with the same value for verify, with	
 *	  method constraints that ... similarly not specified,
 *	  and with permissions containing the same elements, independent 
 *	  of order.
 *	steps:
 *     a) returns true for the same object;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some null for
 *        "methodConstrains";
 *        call equals method passing the same object as argument;
 *        assert true is returned;
 *     b) returns true for another equal object;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and null for "methodConstrains";
 *        construct another BasicProxyPreparer object passing true for "verify",
 *        the same values for "permissions" and null for "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert true is returned;
 *     c) an instance of not the same class;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" null for "methodConstrains";
 *        construct FakeBasicProxyPreparer object passing true for "verify",
 *        the same values for "permissions" and null for "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert false is returned;
 *        invoke equals method from the first object passing null
 *        as an argument;
 *        assert false is returned;
 *     d) not the same value for "verify";
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and null for "methodConstrains";
 *        construct another BasicProxyPreparer object passing false for
 *        "verify", the same values for "permissions" and null for
 *        "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert false is returned;
 *        invoke equals method from the second object passing the first object
 *        as an argument;
 *        assert false is returned;
 *     g) permissions containing not the same elements;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and null for "methodConstrains";
 *        construct another BasicProxyPreparer object passing true for "verify",
 *        some another different values for "permissions" and null
 *        for "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert false is returned;
 *     h) permissions containing the same elements, but in the another order;
 *	steps:
 *        construct a BasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and null for
 *        "methodConstrains";
 *        construct another BasicProxyPreparer object passing true for "verify",
 *        some another values for "permissions" that contains the same
 *        permissions but in the different order and null for
 *        "methodConstrains";
 *        invoke equals method from the first object passing the second object
 *        as an argument;
 *        assert true is returned;
 * </pre>
 */
public class Equals_Test extends QATestEnvironment implements Test {
    /**
     * Test cases description.
     * Elemens: amount of arguments in BasicProxyPreparer constructor
     * plus 10 if verify should be true
     * plus 100 if methodConstrains should be empty instance
     * plus 200 if methodConstrains should be some non empty instance
     * plus 1000 if permissions should be some empty instance
     * plus 2000 if permissions should be some non empty instance
     */
    protected final int [] cases = {
           0,    2,    3,   12,   13,  103,  113,  203,  213,
        1000, 1002, 1003, 1012, 1013, 1103, 1113, 1203, 1213,
        2000, 2002, 2003, 2012, 2013, 2103, 2113, 2203, 2213 };

    /**
     * Run BasicProxyPreparer constructor for valid test case.
     *
     * @param testCase int value 0, 2 or 3 according to
     * amount of arguments in BasicProxyPreparer constructor
     * @param verify whether to verify if proxies are trusted
     * @param methodConstraints method constraints to use when verifying 
     *	      and setting constraints
     * @param permissions permissions to grant, or <code>null</code> if no
     *	      permissions should be granted
     */
    protected FakeBasicProxyPreparer callFakeConstructor(
            int testCase,
            boolean verify,
            MethodConstraints methodConstraints,
            Permission[] permissions) {
        if (testCase == 0) { // constructor without arguments
            return new FakeBasicProxyPreparer();
        } else if (testCase == 2) { // constructor with 2 arguments
            return new FakeBasicProxyPreparer(verify, permissions);
        } else { // constructor with 3 arguments
            return new FakeBasicProxyPreparer(verify,
                    methodConstraints, permissions);
        }
    }


    /**
     * Run BasicProxyPreparer constructor for valid test case.
     *
     * @param testCase int value 0, 2 or 3 according to
     * amount of arguments in BasicProxyPreparer constructor
     * @param verify whether to verify if proxies are trusted
     * @param methodConstraints method constraints to use when verifying 
     *	      and setting constraints
     * @param permissions permissions to grant, or <code>null</code> if no
     *	      permissions should be granted
     */
    protected BasicProxyPreparer callConstructor(
            int testCase,
            boolean verify,
            MethodConstraints methodConstraints,
            Permission[] permissions) {
        if (testCase == 0) { // constructor without arguments
            return new BasicProxyPreparer();
        } else if (testCase == 2) { // constructor with 2 arguments
            return new BasicProxyPreparer(verify, permissions);
        } else { // constructor with 3 arguments
            return new BasicProxyPreparer(verify,
                    methodConstraints, permissions);
        }
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");

        for (int i = 0; i < cases.length; ++i) {
            int testCase = cases[i];
            logger.log(Level.INFO, "--> " + testCase);
            
            MethodConstraints mc = null;
            Permission[] perm = null;
            if (testCase > 2000) {
                perm = new Permission[] {
                        new RuntimePermission("getClassLoader")};
                testCase -= 2000;
            } else if (testCase > 1000) {
                perm = new Permission[] {};
                testCase -= 1000;
            }
            if (testCase > 200) {
                mc = new FakeMethodConstraints(
                        new InvocationConstraint[] {Integrity.NO});
                testCase -= 200;
            } else if (testCase > 100) {
                mc = new FakeMethodConstraints(null);
                testCase -= 100;
            }
            boolean verify = false;
            if (testCase > 10) {
                verify = true;
                testCase -= 10;
            }
            
            // a - same object
            BasicProxyPreparer bpp = callConstructor(
                    testCase, verify, mc, perm);
            if (!bpp.equals(bpp)) {
                throw new TestException(
                        "equals method should return true (a)");
            }     
            
            // b - eqaul object
            bpp = callConstructor(
                    testCase, verify, mc, perm);
            BasicProxyPreparer bpp2 = callConstructor(
                    testCase, verify, mc, perm);
            if (!bpp.equals(bpp2)) {
                throw new TestException(
                        "equals method should return true (b)");
            }
            
            // c - different class
            bpp = callFakeConstructor(
                    testCase, verify, mc, perm);
            BasicProxyPreparer fbpp = callConstructor(
                    testCase, verify, mc, perm);
            if (bpp.equals(bpp2)) {
                throw new TestException(
                        "equals method should return false (c)");
            }
            if (bpp2.equals(bpp)) {
                throw new TestException(
                        "equals method should return false (c)");
            }

            if (testCase == 0) { continue; }
            
            // d - not the same value for "verify"
            bpp = callConstructor(
                    testCase, verify, mc, perm);
            bpp2 = callConstructor(
                    testCase, !verify, mc, perm);
            if (bpp.equals(bpp2)) {
                throw new TestException(
                        "equals method should return false (d)");
            }
            if (bpp2.equals(bpp)) {
                throw new TestException(
                        "equals method should return false (d)");
            }
            
            // g - permissions containing not the same elements
            bpp = callConstructor(
                    testCase, verify, mc, perm);
            Permission[] perm2 = new Permission[] {
                        new RuntimePermission("setClassLoader")};
            bpp2 = callConstructor(
                    testCase, verify, mc, perm2);
            if (bpp.equals(bpp2)) {
                throw new TestException(
                        "equals method should return false (g)");
            }
            if (bpp2.equals(bpp)) {
                throw new TestException(
                        "equals method should return false (g)");
            }
            
            // h - permissions containing the same elements, but in
            //      the another order
            Permission[] perm3 = new Permission[] {
                        new RuntimePermission("getClassLoader"),
                        new RuntimePermission("setClassLoader")};
            bpp = callConstructor(
                    testCase, verify, mc, perm3);
            Permission[] perm4 = new Permission[] {
                        new RuntimePermission("setClassLoader"),
                        new RuntimePermission("getClassLoader")};
            bpp2 = callConstructor(
                    testCase, verify, mc, perm4);
            if (!bpp.equals(bpp2)) {
                throw new TestException(
                        "equals method should return true (h)");
            }
            if (!bpp2.equals(bpp)) {
                throw new TestException(
                        "equals method should return true (h)");
            }
            
            if (testCase == 2) { continue; }
            
            // e, f - not equal method constraints
            bpp = callConstructor(
                    testCase, verify, mc, perm);
            MethodConstraints mc2 = new FakeMethodConstraints(
                        new InvocationConstraint[] {Integrity.YES});
            bpp2 = callConstructor(
                    testCase, verify, mc2, perm);
            if (bpp.equals(bpp2)) {
                throw new TestException(
                        "equals method should return false (e)");
            }
            if (bpp2.equals(bpp)) {
                throw new TestException(
                        "equals method should return false (e)");
            }
        }            
    }
}
