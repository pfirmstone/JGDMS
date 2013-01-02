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
 *   This test verifies the behavior of the getName method of
 *   MethodDesc class.
 *         public String getName()
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
 *        Returns the name of the method, with a prefix or suffix '*'
 *        if the name is a pattern, or null if this descriptor matches
 *        all methods.
 *     1)   test case a) only:
 *        Default descriptor that matches all methods.
 *          test case b) only:
 *        Descriptor that matches all methods with names that
 *        equal the specified name or that match the specified pattern,
 *        regardless of their parameter types.
 *          test case c) only:
 *        Descriptor that only matches methods with exactly the
 *        specified name and parameter types.
 *      steps:
 *        Construct MethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Call getName method without parameters.
 *          test case a) only:
 *        Assert that null is returned.
 *          test case b) and c) only:
 *        Assert that passed name is returned.
 *     2)   test case b) only:
 *        If the specified name starts with the character '*', then this
 *        descriptor matches all methods with names that end with specified
 *        the rest of the specified name.
 *      steps:
 *        Construct MethodDesc type object instance passing some valid
 *        constraints and some method name which starts with the character
 *        '*' and syntactically valid method name as a rest of name as
 *        parameters.
 *        Call getName method without parameters.
 *        Assert that passed name is returned.
 *        Construct MethodDesc type object instance passing some valid
 *        constraints and some method name which starts with the character
 *        '*' and has JavaLetterOrDigit but not JavaLetter as a second
 *        character as parameters.
 *        Call getName method without parameters.
 *        Assert that passed name is returned.
 *     3)   test case b) only:
 *        If the name ends with the character '*', then this descriptor
 *        matches all methods with names that start with the rest of the
 *        specified name.
 *      steps:
 *        Construct MethodDesc type object instance passing some valid
 *        constraints and some method name which starts with syntactically
 *        valid method name and ends with the character '*' as parameters.
 *        Call getName method without parameters.
 *        Assert that passed name is returned.
 * </pre>
 */
public class GetName_Test extends Constructor_Test {

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
            MethodDesc md = callConstructor(testCase, name, types, constraints);
            String result = md.getName();
            if (testCase == case1arg) {
                if (result != null) {
                    throw new TestException(
                            "null should be returned");
                }
            } else { 
                if (result != name) {
                    throw new TestException("invalid result");
                }
            }
            
            
            // 2
            if (testCase == case2arg) {
                name = "*someMethod";
                md = callConstructor(testCase, name, types, constraints);
                result = md.getName();
                if (result != name) {
                    throw new TestException("invalid result");
                }

                name = "*5someMethod";
                md = callConstructor(testCase, name, types, constraints);
                result = md.getName();
                if (result != name) {
                    throw new TestException("invalid result");
                }
            }
            
            // 3
            if (testCase == case2arg) {
                name = "someMethod*";
                md = callConstructor(testCase, name, types, constraints);
                result = md.getName();
                if (result != name) {
                    throw new TestException("invalid result");
                }
            }
        }
    }
}
