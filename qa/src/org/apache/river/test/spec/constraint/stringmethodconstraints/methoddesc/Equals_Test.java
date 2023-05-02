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
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.constraint.StringMethodConstraints.StringMethodDesc;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.InvocationConstraints;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the equals method of
 *   StringMethodDesc class.
 *         public boolean equals(Object obj)
 *
 * Test Cases:
 *   This test contains three test cases - one for each form of StringMethodDesc
 *   constructor:
 *    a) StringMethodDesc(InvocationConstraints constraints)
 *    b) StringMethodDesc(String name,
 *               InvocationConstraints constraints)
 *    c) StringMethodDesc(String name, Class[] types,
 *               InvocationConstraints constraints)
 *   Each case goes through actions described below in case if they have
 *   a sense for that form.
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *        Two instances of this class are equal if they have the same name,
 *        the same parameter types, and the same constraints.
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
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Construct second StringMethodDesc type object instance passing same valid
 *        constraints, same syntactically valid method name, and same non
 *        empty array of classes as parameters if needed.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert true is returned.
 *     2)   test case b) and c) only:
 *        Different names.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Construct second StringMethodDesc type object instance passing same valid
 *        constraints, different syntactically valid method name, and same non
 *        empty array of classes as parameters if needed.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *     3)   test case c) only:
 *        Different types.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Construct second StringMethodDesc type object instance passing same valid
 *        constraints, same syntactically valid method name, and different non
 *        empty array of classes as parameters if needed.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *     4)   test case b) and c) only:
 *        Different constraints.
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Construct second StringMethodDesc type object instance passing different
 *        valid constraints, same syntactically valid method name, and same
 *        non empty array of classes as parameters if needed.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *     5)   test case b) and c) only:
 *        Different constructors. Compare form a) with the forms b) and c).
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as parameters if needed.
 *        Construct second StringMethodDesc type object instance passing same
 *        constraints as an argument using constructor form a).
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *        Call equals method from second object passing first as an
 *        argument.
 *        Assert false is returned.
 *     6)   test case c) only:
 *        Different constructors. Compare forms b) and c).
 *      steps:
 *        Construct StringMethodDesc type object instance passing some valid
 *        constraints, some syntactically valid method name, and some non
 *        empty array of classes as an argumensts.
 *        Construct second StringMethodDesc type object instance passing same
 *        constraints and the same method name as an arguments.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *        Call equals method from second object passing first as an
 *        argument.
 *        Assert false is returned.
 * </pre>
 */
public class Equals_Test extends Constructor_Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");
        for (int i = 0; i < cases.length; ++i) {
            int testCase = cases[i];
            logger.log(Level.INFO, "--> " + testCase);
            
            // 1
            String name1 = "someMethod";
            Class[] types1 = new Class[] {int.class, Object.class};
            InvocationConstraint ic = Delegation.YES;
            InvocationConstraints constraints1 = new InvocationConstraints(
                    ic, null);
            String name2 = "someMethod";
            Class[] types2 = new Class[] {int.class, Object.class};
            InvocationConstraints constraints2 = new InvocationConstraints(
                    ic, null);
            StringMethodDesc md1 = callConstructor(testCase, name1, types1,
                    constraints1);
            StringMethodDesc md2 = callConstructor(testCase, name2, types2,
                    constraints2);
            if (!md1.equals(md2)) {
                throw new TestException(
                        "StringMethodDesc objects should be equal");
            }
            
            // 2
            if ((testCase == case2arg) || (testCase == case3arg)) {
                String name2diff = "anotherMethod";
                md1 = callConstructor(testCase, name1, types1,
                        constraints1);
                md2 = callConstructor(testCase, name2diff, types2,
                        constraints2);
                if (md1.equals(md2)) {
                    throw new TestException(
                            "StringMethodDesc objects should not be equal");
                }
            }
            
            // 3
            if (testCase == case3arg) {
                Class[] types2diff = new Class[] {long.class, Object.class};
                md1 = callConstructor(testCase, name1, types1,
                        constraints1);
                md2 = callConstructor(testCase, name2, types2diff,
                        constraints2);
                if (md1.equals(md2)) {
                    throw new TestException(
                            "StringMethodDesc objects should not be equal");
                }
            }
            
            // 4
            if ((testCase == case2arg) || (testCase == case3arg)) {
                InvocationConstraint ic2diff = Delegation.NO;
                InvocationConstraints constraints2diff =
                        new InvocationConstraints(ic2diff, null);
                Class[] types2diff = new Class[] {long.class, Object.class};
                md1 = callConstructor(testCase, name1, types1,
                        constraints1);
                md2 = callConstructor(testCase, name2, types2,
                        constraints2diff);
                if (md1.equals(md2)) {
                    throw new TestException(
                            "StringMethodDesc objects should not be equal");
                }
            }
            
            // 5
            if ((testCase == case2arg) || (testCase == case3arg)) {
                md1 = callConstructor(case1arg, name1, types1,
                        constraints1);
                md2 = callConstructor(testCase, name2, types2,
                        constraints2);
                if (md1.equals(md2) || md2.equals(md1)) {
                    throw new TestException(
                            "StringMethodDesc objects should not be equal");
                }
            }
            
            // 6
            if (testCase == case3arg) {
                md1 = callConstructor(case2arg, name1, types1,
                        constraints1);
                md2 = callConstructor(testCase, name2, types2,
                        constraints2);
                if (md1.equals(md2) || md2.equals(md1)) {
                    throw new TestException(
                            "StringMethodDesc objects should not be equal");
                }
            }
        }
    }
}
