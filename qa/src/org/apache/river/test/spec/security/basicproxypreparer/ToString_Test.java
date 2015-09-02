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
 *   toString method:
 *   String toString()
 *
 * Test Cases:
 *   This test contains three test case - one for each form
 *   of constructor.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeBasicProxyPreparer - accessor of the tested BasicProxyPreparer
 *        class, that is gateway to protected method access.
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns a string representation of this object.  
 *	steps:
 *	  construct a FakeBasicProxyPreparer object passing
 *        true or false for "verify",
 *        empty and not empty values for "permissions"
 *        empty and not empty instance for "methodConstrains"
 *        in all combinations; 
 *        call toString method;
 *        assert that non empty String is returned;
 * </pre>
 */
public class ToString_Test extends Equals_Test {

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
            
            BasicProxyPreparer bpp = callConstructor(
                    testCase, verify, mc, perm);
            if (bpp.toString().length() == 0){
                throw new TestException(
                        "toString method should return non empty string");
            };  
        }            
    }
}
