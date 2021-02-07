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
 *   This test verifies the behavior of the getStringMethodDescs method of
 *   StringMethodConstraints class.
 *   public StringMethodConstraints.StringMethodDesc[] getMethodDescs()
 *
 * Actions:
 *   Test checks normal and exceptional variants of the
 *   parameters for getStringMethodDescs method and it's return value.
 *
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns the descriptors.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Call getStringMethodDescs method.
 *        Assert that result array has the same length as the array
 *        that was passed as an argument.
 *        Assert that result array contains equals elements with the array
 *        that was passed as an argument.
 *     2) Returns the descriptors. Another StringMethodConstraints constructor
 *        form.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Call getStringMethodDescs method.
 *        Assert that result array contains one element that equal to
 *        StringMethodDesc constructed with the same InvocationConstraints as
 *        an argument.
 *     3) Returns a new non-null array every time it is called.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Call getStringMethodDescs method.
 *        Call getStringMethodDescs method again.
 *        Assert that results are not the same instance.
 *     4) Repeat p. 3) for another StringMethodConstraints constructor
 *        form. (the same as in p. 2)
 * </pre>
 */
public class GetMethodDescs_Test extends QATestEnvironment implements Test {

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
        StringMethodDesc [] passedDescs = {methodDesc1, methodDesc2};
        StringMethodConstraints bmc = new StringMethodConstraints(passedDescs);
        StringMethodDesc [] resultDescs = bmc.getMethodDescs();
        if (resultDescs.length != passedDescs.length) {
            throw new TestException(
                    "returned StringMethodDesc array has invalid length");
        }
        for (int j = 0; j < passedDescs.length; ++j) {
            if (resultDescs[j] != passedDescs[j]) {
                throw new TestException(
                        "returned StringMethodDesc array invalid");
            }
        }
        
        // 2
        bmc = new StringMethodConstraints(constraints);
        resultDescs = bmc.getMethodDescs();
        if (resultDescs.length != 1) {
            throw new TestException(
                    "returned StringMethodDesc array has invalid length");
        }
        if (!resultDescs[0].getConstraints().equals(constraints)) {
            throw new TestException(
                    "returned StringMethodDesc array invalid");
        }
        
        // 3
        bmc = new StringMethodConstraints(passedDescs);
        StringMethodDesc [] resultDescs1 = bmc.getMethodDescs();
        StringMethodDesc [] resultDescs2 = bmc.getMethodDescs();
        if (resultDescs1 == resultDescs2) {
            throw new TestException(
                    "returned StringMethodDesc array should be the new every time"
                    + " it is called");
        }
        
        // 4
        bmc = new StringMethodConstraints(constraints);
        resultDescs1 = bmc.getMethodDescs();
        resultDescs2 = bmc.getMethodDescs();
        if (resultDescs1 == resultDescs2) {
            throw new TestException(
                    "returned StringMethodDesc array should be the new every time"
                    + " it is called");
        }
    }
}
