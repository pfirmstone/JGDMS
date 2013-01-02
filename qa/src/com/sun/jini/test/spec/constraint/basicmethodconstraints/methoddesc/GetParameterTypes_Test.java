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
package com.sun.jini.test.spec.constraint.basicmethodconstraints.methoddesc;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.constraint.BasicMethodConstraints.MethodDesc;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.InvocationConstraints;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the getParameterTypes method of
 *   MethodDesc class.
 *         public Class[] getParameterTypes()
 *
 * Test Cases:
 *   This test contains three test cases - one for each form of MethodDesc
 *   constructor:
 *    a) MethodDesc(InvocationConstraints constraints)
 *    b) MethodDesc(String name,
 *               InvocationConstraints constraints)
 *    c) MethodDesc(String name, Class[] types,
 *               InvocationConstraints constraints)
 *   Each case goes through actions described
 *   below in case if they have a sense for that form.
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns the parameter types, or null if this descriptor matches
 *        all parameter types or all methods.
 *          test case c) only:
 *        Descriptor that only matches methods with exactly the
 *        specified name and parameter types.
 *      steps:
 *        Construct MethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters.
 *        Call getParameterTypes method without parameters.
 *        Assert that returned array contains the same elements and in
 *        the same order that an array in arguments.
 *     2)   test case a) only:
 *        Default descriptor that matches all methods.
 *          test case b) only:
 *        Descriptor that matches all methods with names that
 *        equal the specified name or that match the specified pattern,
 *        regardless of their parameter types.
 *      steps:
 *        Construct MethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Call getParameterTypes method without parameters.
 *        Assert that null is returned.
 *     3)   test case b) only:
 *        If the specified name starts with the character '*', then this
 *        descriptor matches all methods with names that end with specified
 *        the rest of the specified name.
 *      steps:
 *        Construct MethodDesc type object instance passing some valid
 *        constraints and some method name which starts with the character
 *        '*' and syntactically valid method name as a rest of name as
 *        parameters.
 *        Call getParameterTypes method without parameters.
 *        Assert that null is returned.
 *        Construct MethodDesc type object instance passing some valid
 *        constraints and some method name which starts with the character
 *        '*' and has JavaLetterOrDigit but not JavaLetter as a second
 *        character as parameters.
 *        Call getParameterTypes method without parameters.
 *        Assert that null is returned.
 *     4)   test case b) only:
 *        If the name ends with the character '*', then this descriptor
 *        matches all methods with names that start with the rest of the
 *        specified name.
 *      steps:
 *        Construct MethodDesc type object instance passing some valid
 *        constraints and some method name which starts with syntactically
 *        valid method name and ends with the character '*' as parameters.
 *        Call getParameterTypes method without parameters.
 *        Assert that null is returned.
 *     5) Returns a new non-null array every time it is called.
 *          test case c) only:
 *      steps:
 *        Construct MethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters.
 *        Call getParameterTypes method without parameters.
 *        Call getParameterTypes method without parameters secont time.
 *        Assert that returned arrays are not the same instance.
 * </pre>
 */
public class GetParameterTypes_Test extends Constructor_Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");
        for (int i = 0; i < cases.length; ++i) {
            int testCase = cases[i];
            logger.log(Level.INFO, "--> " + testCase);
            String name = "someMethod";
            Class[] types = new Class[] {int.class, Object.class};
            InvocationConstraint ic = Delegation.YES;
            InvocationConstraints constraints = new InvocationConstraints(
                    ic, null);
            MethodDesc md = null;
            Class[] returnedTypes = null;
            
            // 1
            if (testCase == case3arg) {
                md = callConstructor(testCase, name, types, constraints);
                returnedTypes = md.getParameterTypes();
                if (returnedTypes == null) {
                    throw new TestException(
                            "result should not be null");
                }
                for (int j = 0; j < types.length; ++j) {
                    if (returnedTypes[j] != types[j]) {
                        throw new TestException(
                                "invalid result types array");
                    }
                }
            }
            
            // 2
            if ((testCase == case1arg) || (testCase == case2arg)) {
                md = callConstructor(testCase, name, types, constraints);
                returnedTypes = md.getParameterTypes();
                if (returnedTypes != null) {
                    throw new TestException("result should be null");
                }
            }
            
            // 3
            if (testCase == case2arg) {
                name = "*someMethod";
                md = callConstructor(testCase, name, types, constraints);
                returnedTypes = md.getParameterTypes();
                if (returnedTypes != null) {
                    throw new TestException("result should be null");
                }

                name = "*5someMethod";
                md = callConstructor(testCase, name, types, constraints);
                returnedTypes = md.getParameterTypes();
                if (returnedTypes != null) {
                    throw new TestException("result should be null");
                }
            }
            
            // 4
            if (testCase == case2arg) {
                name = "someMethod*";
                md = callConstructor(testCase, name, types, constraints);
                returnedTypes = md.getParameterTypes();
                if (returnedTypes != null) {
                    throw new TestException("result should be null");
                }
            }
            
            // 5
            if (testCase == case3arg) {
                md = callConstructor(testCase, name, types, constraints);
                returnedTypes = md.getParameterTypes();
                Class[] returnedTypes2 = md.getParameterTypes();
                if (returnedTypes == returnedTypes2) {
                    throw new TestException(
                            "should be not the same (new) array");
                }
            }
            
        }
    }
}
