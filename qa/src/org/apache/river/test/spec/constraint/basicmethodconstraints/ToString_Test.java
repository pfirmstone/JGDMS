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
package org.apache.river.test.spec.constraint.basicmethodconstraints;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.constraint.BasicMethodConstraints.MethodDesc;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the toString method of
 *   BasicMethodConstraints class.
 *   public String toString()
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns a string representation of this object.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid MethodDesc array as an argument.
 *        Call toString method.
 *        Assert that not empty string was returned.
 *     2) Another BasicMethodConstraints constructor form.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Call toString method.
 *        Assert that not empty string was returned.
 * </pre>
 */
public class ToString_Test extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");
        
        // 1
        String name = "someMethod";
        InvocationConstraint ic = Delegation.YES;
        InvocationConstraints constraints = new InvocationConstraints(
                ic, null);
        MethodDesc methodDesc1 = new MethodDesc(name, constraints);
        MethodDesc methodDesc2 = new MethodDesc(constraints);
        MethodDesc [] descs = {methodDesc1, methodDesc2};
        BasicMethodConstraints bmc1 = new BasicMethodConstraints(descs);
        if (bmc1.toString().length() == 0) {
            throw new TestException(
                    "result should not be empty string");
        }
        
        // 2
        bmc1 = new BasicMethodConstraints(constraints);
        if (bmc1.toString().length() == 0) {
            throw new TestException(
                    "result should not be empty string");
        }
    }
}
