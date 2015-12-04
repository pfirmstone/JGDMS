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
 *   hashCode method:
 *   public int hashCode()
 *
 * Test Cases:
 *   This test contains three test case - one for each form
 *   of constructor.
 *
 * Actions:
 *   a) public BasicProxyPreparer():
 *     1)
 *      steps:
 *        construct a BasicProxyPreparer object without parameters;
 *   	  call hashCode method;
 *   	  assert that some int value is returned and no exception is thrown;
 *   b) public BasicProxyPreparer(boolean verify, Permission[] permissions):
 *     2) construct a BasicProxyPreparer object passing false for "verify"
 *        and null for "permissions";
 *   	  call hashCode method;
 *   	  assert that some int value is returned and no exception is thrown;
 *     3) construct a BasicProxyPreparer object passing false for "verify"
 *        and empty array for "permissions";
 *   	  call hashCode method;
 *   	  assert that some int value is returned and no exception is thrown;
 *     4) construct a BasicProxyPreparer object passing false for "verify"
 *        and array with one some item for "permissions";
 *   	  call hashCode method;
 *   	  assert that some int value is returned and no exception is thrown;
 *     5) construct a BasicProxyPreparer object passing false for "verify"
 *        and array with two some items for "permissions";
 *   	  call hashCode method;
 *   	  assert that some int value is returned and no exception is thrown;
 *   c) public BasicProxyPreparer(boolean verify,
 *        MethodConstrains methodConstraints, Permission[] permissions):
 *     6) repeat actions from 2 to 5 passing null for "methodConstrains";
 *     7) repeat actions from 2 to 5 passing empty instance for
 *        "methodConstrains";
 *     8) repeat actions from 2 to 5 passing some non empty instance for
 * </pre>
 */
public class HashCode_Test extends Equals_Test {

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
            bpp.hashCode();  
        }            
    }
}
