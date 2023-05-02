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
package org.apache.river.test.spec.constraint.stringmethodconstraints.methoddesc;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.constraint.StringMethodConstraints.StringMethodDesc;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.InvocationConstraints;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the constructor of
 *   StringMethodDesc class. There are three forms of constructor:
 *    a) StringMethodDesc(InvocationConstraints constraints)
 *    b) StringMethodDesc(String name,
 *               InvocationConstraints constraints)
 *    c) StringMethodDesc(String name, Class[] types,
 *               InvocationConstraints constraints)
 *
 * Test Cases:
 *   This test contains three test case - one for each form
 *   of constructor. Each case goes through actions described below
 *   in case if they have a sense for that form.
 *
 * Actions:
 *   Test checks normal and exceptional variants of the
 *   parameters for StringMethodDesc constructor.
 *
 *   Test verifies the following assertions and performs the following steps:
 *     1)   test case a) only:
 *        Creates a default descriptor that matches all methods.
 *          test case b) only:
 *        Creates a descriptor that matches all methods with names that
 *        equal the specified name or that match the specified pattern,
 *        regardless of their parameter types.
 *          test case c) only:
 *        Creates a descriptor that only matches methods with exactly the
 *        specified name and parameter types.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Assert that object is constructed and no exception was thrown.
 *     2)   test case b) only:
 *        If the specified name starts with the character '*', then this
 *        descriptor matches all methods with names that end with specified
 *        the rest of the specified name.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints and some method name which starts with the character
 *        '*' and syntactically valid method name as a rest of name as
 *        parameters.
 *        Assert that object is constructed and no exception was thrown.
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints and some method name which starts with the character
 *        '*' and has JavaLetterOrDigit but not JavaLetter as a second
 *        character as parameters.
 *        Assert that object is constructed and no exception was thrown.
 *     3)   test case b) only:
 *        If the name ends with the character '*', then this descriptor
 *        matches all methods with names that start with the rest of the
 *        specified name.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints and some method name which starts with syntactically
 *        valid method name and ends with the character '*' as parameters.
 *        Assert that object is constructed and no exception was thrown.
 *     4)   test case c) only:
 *        The array passed to the constructor is neither modified nor
 *        retained.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters.
 *        Assert that array of classes and all it's elements was not modified.
 *     5)   test case c) only:
 *        subsequent changes to that array have no effect on the instance
 *        created.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters.
 *        Construct second StringMethodDesc type object instance passing same valid
 *        constraints, same syntactically valid method name, and another but
 *        with the same content  array of classes as parameters.
 *        Modify second array of classes.
 *        Assert that the first StringMethodDesc type object is equal to the second
 *        one.
 *     6) The constraints can be null, which is treated the same as an empty
 *        instance.
 *      steps:
 *        Construct StringMethodDesc type object instance passing empty
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Construct second StringMethodDesc type object instance passing null for
 *        constraints, same syntactically valid method name, and same non
 *        empty array of classes as parameters if needed.
 *        Assert that the first StringMethodDesc type object is equal to the second
 *        one.
 *     7)   test case b) or c) only:
 *        NullPointerException - if name ... is null.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, null for the method name, and some non
 *        empty array of classes as parameters if needed.
 *        Assert NullPointerException is thrown
 *     8)   test case c) only:
 *        NullPointerException - if ... types is null....
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and null for the
 *        type as parameters.
 *        Assert NullPointerException is thrown
 *     9)   test case c) only:
 *        NullPointerException - if ... any element of types is null.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and array
 *        containing null as first element for the type as parameters.
 *        Assert NullPointerException is thrown
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and array
 *        containing valid value as first element  and null as a second
 *        element for the type as parameters.
 *        Assert NullPointerException is thrown
 *     10)  test case b) or c) only:
 *        IllegalArgumentException - if name is not a syntactically valid
 *        method name and is not a syntactically valid method name with
 *        a '*' appended and cannot be constructed from
 *        some syntactically valid method name containing more than two
 *        characters by replacing the first character of that name with '*'.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically invalid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Assert IllegalArgumentException is thrown
 *        Syntactically invalid method name cases:
 *        - starts not with JavaLetterOrDigit;
 *        - starts with JavaDigit;
 *        - starts with '*' and not JavaLetterOrDigit;
 *        - starts with '*' and ends with '*';
 * </pre>
 */
public class Constructor_Test extends QATestEnvironment implements Test {
    /**
     * Test cases that correspond to one argument StringMethodDesc constructor
     */
    protected final int case1arg = 1;

    /**
     * Test cases that correspond to two arguments StringMethodDesc constructor
     */
    protected final int case2arg = 2;

    /**
     * Test cases that correspond to three arguments StringMethodDesc constructor
     */
    protected final int case3arg = 3;

     /**
     * Test cases description.
     * Elemens: amount of arguments in StringMethodDesc constructor
     */
    protected final int [] cases = { case1arg, case2arg, case3arg };

   /**
     * Run StringMethodDesc constructor for valid test case.
     *
     * @param testCase value according to test cases description
     * @param constraints InvocationConstraints to use when verifying
     *	      and setting constraints
     */
    protected StringMethodDesc callConstructor(
            int testCase,
            String name,
            Class[] types,
            InvocationConstraints constraints) {
        if (testCase == 1) { // constructor without arguments
            return new StringMethodDesc(constraints);
        } else if (testCase == 2) { // constructor with 2 arguments
            return new StringMethodDesc(name, constraints);
        } else { // constructor with 3 arguments
            return new StringMethodDesc(name, types, constraints);
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
            
            // 1
            String name = "someMethod";
            Class[] types = new Class[] {int.class, Object.class};
            InvocationConstraint ic = Delegation.YES;
            InvocationConstraints constraints = new InvocationConstraints(
                    ic, null);
            callConstructor(testCase, name, types, constraints);
            
            // 2
            if (testCase == case2arg) {
                name = "*someMethod";
                callConstructor(testCase, name, types, constraints);

                name = "*5someMethod";
                callConstructor(testCase, name, types, constraints);
            }
            
            // 3
            if (testCase == case2arg) {
                name = "someMethod*";
                callConstructor(testCase, name, types, constraints);
            }
            
            // 4
            if (testCase == case3arg) {
                name = "someMethod";
                Class[] storedTypes = new Class[types.length];
                for (int j = 0; j < types.length; ++j) {
                    storedTypes[j] = types[j];
                }
                callConstructor(testCase, name, types, constraints);
                if (storedTypes.length != types.length) {
                    throw new TestException(
                            "types array length was modified");
                }
                for (int j = 0; j < types.length; ++j) {
                    if (storedTypes[j] != types[j]) {
                        throw new TestException(
                                "types array was modified");
                    }
                }
                
            }
            
            // 5
            if (testCase == case3arg) {
                name = "someMethod";
                Class[] types2 = new Class[types.length];
                for (int j = 0; j < types.length; ++j) {
                    types2[j] = types[j];
                }
                StringMethodDesc md1 =
                    callConstructor(testCase, name, types, constraints);
                StringMethodDesc md2 =
                    callConstructor(testCase, name, types2, constraints);
                if (!md1.equals(md2)) {
                    throw new TestException(
                            "StringMethodDesc objects should be equal");
                }
                types2[0] = long.class;
                if (!md1.equals(md2)) {
                    throw new TestException(
                            "StringMethodDesc objects should be equal");
                }
            }
            
            // 6
            name = "someMethod";
            InvocationConstraints emptyConstraints = new InvocationConstraints(
                    (InvocationConstraint) null, null);
            StringMethodDesc md1 =
                callConstructor(testCase, name, types, emptyConstraints);
            StringMethodDesc md2 =
                callConstructor(testCase, name, types, null);
            if (!md1.equals(md2)) {
                throw new TestException(
                        "StringMethodDesc objects should be equal");
            }
            
            // 7
            if (testCase == case2arg || testCase == case3arg) {
                try {
                    callConstructor(testCase, null, types, constraints);
                    throw new TestException(
                            "NullPointerException should be thrown");
                } catch (NullPointerException ignore) {
                }
            }
            
            // 8
            if (testCase == case3arg) {
                try {
                    callConstructor(testCase, name, null, constraints);
                    throw new TestException(
                            "NullPointerException should be thrown");
                } catch (NullPointerException ignore) {
                }
            }
            
            // 9
            if (testCase == case3arg) {
                Class[] brokenTypes = new Class[] {null, Object.class};
                try {
                    callConstructor(testCase, name, brokenTypes, constraints);
                    throw new TestException(
                            "NullPointerException should be thrown");
                } catch (NullPointerException ignore) {
                }
                brokenTypes = new Class[] {Object.class, null};
                try {
                    callConstructor(testCase, name, brokenTypes, constraints);
                    throw new TestException(
                            "NullPointerException should be thrown");
                } catch (NullPointerException ignore) {
                }
            }
            
            // 10
            if (testCase == case2arg || testCase == case3arg) {
                String [] names = new String [] {
                    "#someMethod",
                    "2someMethod",
                    "*#someMethod",
                    "*someMethod*"
                };
                for (int j = 0; j < names.length; ++j) {
                    String brokenName = names[j];
                    try {
                        callConstructor(testCase, brokenName, types,
                                constraints);
                        throw new TestException(
                                "IllegalArgumentException should be thrown");
                    } catch (IllegalArgumentException ignore) {
                    }
                }
            }
        }
    }
}
