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
 *   This test verifies the behavior of the equals method of
 *   StringMethodConstraints class.
 *   public boolean equals(Object obj)
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) Two instances of this class are equal if they have the same
 *        descriptors in the same order.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Construct second StringMethodConstraints type object instance passing
 *        the same StringMethodDesc array as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert true is returned.
 *     2) Another StringMethodConstraints constructor form.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints instance as an argument.
 *        Construct second StringMethodConstraints type object instance passing
 *        the same InvocationConstraints instance as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert true is returned.
 *     3) Equal different StringMethodConstraints constructors form.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Construct StringMethodDesc type object instance passing
 *        the same InvocationConstraints as an argument.
 *        Construct StringMethodConstraints type object instance passing
 *        array with only constructed StringMethodDesc object as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert true is returned.
 *        Call equals method from second object passing first as an
 *        argument.
 *        Assert true is returned.
 *     4) Different descriptors.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Construct second StringMethodConstraints type object instance passing
 *        different StringMethodDesc array as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *     5) Different constraints.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints instance as an argument.
 *        Construct second StringMethodConstraints type object instance passing
 *        different InvocationConstraints instance as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *     6) Unequal different StringMethodConstraints constructors form.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Construct StringMethodDesc type object instance passing
 *        the different InvocationConstraints as an argument.
 *        Construct StringMethodConstraints type object instance passing
 *        array with only constructed StringMethodDesc object as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *        Call equals method from second object passing first as an
 *        argument.
 *        Assert false is returned.
 *     7) Different descriptors order.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Construct StringMethodDesc array object instance with the same descriptors
 *        but in different order.
 *        Construct second StringMethodConstraints type object instance passing
 *        constructed StringMethodDesc array as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 * </pre>
 */
public class Equals_Test extends QATestEnvironment implements Test {

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
        StringMethodConstraints bmc2 = new StringMethodConstraints(descs);
        if (!bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return true");
        }
        
        // 2
        bmc1 = new StringMethodConstraints(constraints);
        bmc2 = new StringMethodConstraints(constraints);
        if (!bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return true");
        }
        
        // 3
        StringMethodDesc [] simpleDescs = {methodDesc2};
        bmc1 = new StringMethodConstraints(constraints);
        bmc2 = new StringMethodConstraints(simpleDescs);
        if (!bmc1.equals(bmc2) || !bmc2.equals(bmc1)) {
            throw new TestException(
                    "equals method should return true");
        }
        
        // 4
        String name2 = "someDifferentMethod";
        StringMethodDesc methodDesc3 = new StringMethodDesc(name2, constraints);
        StringMethodDesc [] descs2 = {methodDesc3, methodDesc2};
        bmc1 = new StringMethodConstraints(descs);
        bmc2 = new StringMethodConstraints(descs2);
        if (bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return false");
        }
        
        // 5
        InvocationConstraint ic2 = Integrity.YES;
        InvocationConstraints constraints2 = new InvocationConstraints(
                ic2, null);
        bmc1 = new StringMethodConstraints(constraints);
        bmc2 = new StringMethodConstraints(constraints2);
        if (bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return false");
        }
        
        // 6
        bmc1 = new StringMethodConstraints(constraints2);
        bmc2 = new StringMethodConstraints(simpleDescs);
        if (bmc1.equals(bmc2) || bmc2.equals(bmc1)) {
            throw new TestException(
                    "equals method should return false");
        }
        
        // 7
        StringMethodDesc [] descs3 = {methodDesc3, methodDesc1};
        StringMethodDesc [] descs4 = {methodDesc1, methodDesc3};
        bmc1 = new StringMethodConstraints(descs3);
        bmc2 = new StringMethodConstraints(descs4);
        if (bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return false");
        }
    }
}
