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
package com.sun.jini.test.spec.constraint.basicmethodconstraints;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
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
 *   This test verifies the behavior of the equals method of
 *   BasicMethodConstraints class.
 *   public boolean equals(Object obj)
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) Two instances of this class are equal if they have the same
 *        descriptors in the same order.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid MethodDesc array as an argument.
 *        Construct second BasicMethodConstraints type object instance passing
 *        the same MethodDesc array as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert true is returned.
 *     2) Another BasicMethodConstraints constructor form.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints instance as an argument.
 *        Construct second BasicMethodConstraints type object instance passing
 *        the same InvocationConstraints instance as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert true is returned.
 *     3) Equal different BasicMethodConstraints constructors form.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Construct MethodDesc type object instance passing
 *        the same InvocationConstraints as an argument.
 *        Construct BasicMethodConstraints type object instance passing
 *        array with only constructed MethodDesc object as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert true is returned.
 *        Call equals method from second object passing first as an
 *        argument.
 *        Assert true is returned.
 *     4) Different descriptors.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid MethodDesc array as an argument.
 *        Construct second BasicMethodConstraints type object instance passing
 *        different MethodDesc array as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *     5) Different constraints.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints instance as an argument.
 *        Construct second BasicMethodConstraints type object instance passing
 *        different InvocationConstraints instance as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *     6) Unequal different BasicMethodConstraints constructors form.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Construct MethodDesc type object instance passing
 *        the different InvocationConstraints as an argument.
 *        Construct BasicMethodConstraints type object instance passing
 *        array with only constructed MethodDesc object as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 *        Call equals method from second object passing first as an
 *        argument.
 *        Assert false is returned.
 *     7) Different descriptors order.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid MethodDesc array as an argument.
 *        Construct MethodDesc array object instance with the same descriptors
 *        but in different order.
 *        Construct second BasicMethodConstraints type object instance passing
 *        constructed MethodDesc array as an argument.
 *        Call equals method from first object passing second as an
 *        argument.
 *        Assert false is returned.
 * </pre>
 */
public class Equals_Test extends QATest {

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
        BasicMethodConstraints bmc2 = new BasicMethodConstraints(descs);
        if (!bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return true");
        }
        
        // 2
        bmc1 = new BasicMethodConstraints(constraints);
        bmc2 = new BasicMethodConstraints(constraints);
        if (!bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return true");
        }
        
        // 3
        MethodDesc [] simpleDescs = {methodDesc2};
        bmc1 = new BasicMethodConstraints(constraints);
        bmc2 = new BasicMethodConstraints(simpleDescs);
        if (!bmc1.equals(bmc2) || !bmc2.equals(bmc1)) {
            throw new TestException(
                    "equals method should return true");
        }
        
        // 4
        String name2 = "someDifferentMethod";
        MethodDesc methodDesc3 = new MethodDesc(name2, constraints);
        MethodDesc [] descs2 = {methodDesc3, methodDesc2};
        bmc1 = new BasicMethodConstraints(descs);
        bmc2 = new BasicMethodConstraints(descs2);
        if (bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return false");
        }
        
        // 5
        InvocationConstraint ic2 = Integrity.YES;
        InvocationConstraints constraints2 = new InvocationConstraints(
                ic2, null);
        bmc1 = new BasicMethodConstraints(constraints);
        bmc2 = new BasicMethodConstraints(constraints2);
        if (bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return false");
        }
        
        // 6
        bmc1 = new BasicMethodConstraints(constraints2);
        bmc2 = new BasicMethodConstraints(simpleDescs);
        if (bmc1.equals(bmc2) || bmc2.equals(bmc1)) {
            throw new TestException(
                    "equals method should return false");
        }
        
        // 7
        MethodDesc [] descs3 = {methodDesc3, methodDesc1};
        MethodDesc [] descs4 = {methodDesc1, methodDesc3};
        bmc1 = new BasicMethodConstraints(descs3);
        bmc2 = new BasicMethodConstraints(descs4);
        if (bmc1.equals(bmc2)) {
            throw new TestException(
                    "equals method should return false");
        }
    }
}
