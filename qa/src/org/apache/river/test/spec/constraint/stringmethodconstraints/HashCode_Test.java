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
package org.apache.river.test.spec.constraint.stringmethodconstraints;

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
import net.jini.constraint.StringMethodConstraints;
import net.jini.constraint.StringMethodConstraints.StringMethodDesc;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraints;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the hashCode method of
 *   StringMethodConstraints class.
 *   public int hashCode()
 *
 * Actions:
 *   Test checks normal and exceptional variants of the
 *   parameters for hashCode method and it's return value.
 *
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns a hash code value for this object.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Call hashCode method.
 *        Assert that no exception was thrown.
 *     2) Check hashCode equality for equal objects.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Construct second StringMethodConstraints type object instance passing
 *        the same StringMethodDesc array as an argument.
 *        Call hashCode method.
 *        Assert that hashCode method result from both objects are equal.
 *     3) Another StringMethodConstraints constructor form.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Call hashCode method.
 *        Assert that no exception was thrown.
 *     4) Repeat p. 2) for another StringMethodConstraints constructor
 *        form. (the same as in p. 3)
 *     5) Equality for different StringMethodConstraints constructors form.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Construct StringMethodDesc type object instance passing
 *        the same InvocationConstraints as an argument.
 *        Construct StringMethodConstraints type object instance passing
 *        array with only constructed StringMethodDesc object as an argument.
 *        Assert that hashCode method result from both objects are equal.
 * </pre>
 */
public class HashCode_Test extends QATestEnvironment implements Test {

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
        StringMethodDesc methodDesc1 = new StringMethodDesc(name, constraints);
        StringMethodDesc methodDesc2 = new StringMethodDesc(constraints);
        StringMethodDesc [] descs = {methodDesc1, methodDesc2};
        StringMethodConstraints bmc1 = new StringMethodConstraints(descs);
        bmc1.hashCode();
        
        // 2
        StringMethodConstraints bmc2 = new StringMethodConstraints(descs);
        if (bmc1.hashCode() != bmc2.hashCode()) {
            throw new TestException(
                    "hashCode for equal objects should be equal");
        }
        
        // 3
        bmc1 = new StringMethodConstraints(constraints);
        bmc1.hashCode();
        
        // 4
        bmc2 = new StringMethodConstraints(constraints);
        if (bmc1.hashCode() != bmc2.hashCode()) {
            throw new TestException(
                    "hashCode for equal objects should be equal");
        }

        // 5
        StringMethodDesc [] simpleDescs = {methodDesc2};
        bmc1 = new StringMethodConstraints(constraints);
        bmc2 = new StringMethodConstraints(simpleDescs);
        if (bmc1.hashCode() != bmc2.hashCode()) {
            throw new TestException(
                    "hashCode for equal objects should be equal");
        }
    }
}
