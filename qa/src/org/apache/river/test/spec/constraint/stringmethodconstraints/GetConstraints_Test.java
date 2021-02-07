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
 *   This test verifies the behavior of the getConstraints method of
 *   StringMethodConstraints class.
 *   public InvocationConstraints getConstraints(Method method)
 *
 * Actions:
 *   Test checks normal and exceptional variants of the
 *   parameters for getConstraints method and it's return value.
 *
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns the constraints for the specified remote method as a
 *        non-null value.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Call getConstraints method passing method that exists in some
 *        descriptor in the StringMethodDesc array as an argument.
 *        Assert that result is the same as in this corresponding StringMethodDesc.
 *     2) Searches the descriptors in order, and returns an empty constraints
 *        instance if there is no match.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Call getConstraints method passing method that does not exists in any
 *        descriptor in the StringMethodDesc array as an argument.
 *        Assert that result is the empty constraints.
 *     3) Searches the descriptors in order, and returns the constraints
 *        in the first descriptor that matches the method.
 *      steps:
 *        Construct StringMethodDesc array type object with three elements where
 *        two last matches some method and with the different invocation
 *        constraints all.
 *        Construct StringMethodConstraints type object instance passing this
 *        StringMethodDesc array as an argument.
 *        Call getConstraints method passing this method as an argument.
 *        Assert that result is the constraints from the second descriptor.
 *     4) Same as 3 but StringMethodDesc instances differ by parameter types
 *        instead of method names.
 *      steps:
 *        Construct StringMethodDesc array type object with three elements where
 *        all matches some method name but with the different parameter types
 *        all and with the different invocation constraints all.
 *        Construct StringMethodConstraints type object instance passing this
 *        StringMethodDesc array as an argument.
 *        Call getConstraints method passing this method that matches third
 *        descriptor as an argument.
 *        Assert that result is the constraints from the third descriptor.
 *     5) Throws: NullPointerException - if the argument is null.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Call getConstraints method passing null as an argument.
 *        Assert that NullPointerException is thrown.
 * </pre>
 */
public class GetConstraints_Test extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");
        
        // 1
        String name = "equals";
        InvocationConstraint ic = Delegation.YES;
        InvocationConstraints constraints = new InvocationConstraints(
                ic, null);
        StringMethodDesc methodDesc = new StringMethodDesc(name, constraints);
        StringMethodDesc [] descs = {methodDesc};
        StringMethodConstraints bmc = new StringMethodConstraints(descs);
        Method mEquals = Object.class.getDeclaredMethod(name,
                new Class[] { Object.class });
        InvocationConstraints returnedConstraints =
                bmc.getConstraints(mEquals);
        if (!constraints.equals(returnedConstraints)) {
            throw new TestException(
                    "Invalid constraints was returned");
        }

        // 2
        String name1 = "hashCode";
        Method mHashCode = Object.class.getDeclaredMethod(name1,
                new Class[] {});
        returnedConstraints = bmc.getConstraints(mHashCode);
        InvocationConstraints emptyConstraints = new InvocationConstraints(
                (InvocationConstraint) null, null);
        if (!emptyConstraints.equals(returnedConstraints)) {
            throw new TestException(
                    "Empty constraints should be returned");
        }

        // 3
        String name2 = "*ashCode";
        InvocationConstraint ic2 = Delegation.NO;
        InvocationConstraints constraints2 = new InvocationConstraints(
                ic2, null);
        StringMethodDesc methodDesc2 = new StringMethodDesc(name2, constraints2);
        String name3 = "*shCode";
        InvocationConstraint ic3 = Integrity.NO;
        InvocationConstraints constraints3 = new InvocationConstraints(
                ic3, null);
        StringMethodDesc methodDesc3 = new StringMethodDesc(name3, constraints3);
        StringMethodDesc [] descs3 = {methodDesc, methodDesc2, methodDesc3};
        bmc = new StringMethodConstraints(descs3);
        returnedConstraints = bmc.getConstraints(mHashCode);
        if (!returnedConstraints.equals(constraints2)) {
            throw new TestException(
                    "Invalid constraints was returned:"
                    + returnedConstraints
                    + ", should be:" + constraints2);
        }

        // 4
        StringMethodDesc methodDesc1 = new StringMethodDesc(name,
                new Class[] {}, constraints2);
        methodDesc2 = new StringMethodDesc(name,
                new Class[] { Object.class, Class.class }, constraints2);
        methodDesc3 = new StringMethodDesc(name,
                new Class[] { Object.class }, constraints3);
        StringMethodDesc [] descs4 = {methodDesc1, methodDesc2, methodDesc3};
        bmc = new StringMethodConstraints(descs4);
        returnedConstraints = bmc.getConstraints(mEquals);
        if (!returnedConstraints.equals(constraints3)) {
            throw new TestException(
                    "Invalid constraints was returned:"
                    + returnedConstraints
                    + ", should be:" + constraints2);
        }

        // 5
        try {
            bmc.getConstraints(null);
            throw new TestException(
                    "NullPointerException should be thrown");
        } catch (NullPointerException ignore) {
        }
    }
}
