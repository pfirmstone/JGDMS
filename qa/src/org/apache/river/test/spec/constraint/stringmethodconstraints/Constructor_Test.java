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
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.constraint.StringMethodConstraints;
import net.jini.constraint.StringMethodConstraints.StringMethodDesc;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.InvocationConstraints;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the constructor of
 *   StringMethodConstraints class. There are two forms of constructor:
 *   a) StringMethodConstraints(StringMethodConstraints.StringMethodDesc[] descs)
 *   b) StringMethodConstraints(InvocationConstraints constraints)
 *
 * Actions:
 *   Test checks normal and exceptional variants of the
 *   parameters for StringMethodConstraints constructor.
 *
 *   Test verifies the following assertions and performs the following steps:
 *     1) Creates an instance with the specified ordered array of descriptors.    *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Assert that object is constructed and no exception was thrown.
 *     2) The array passed to the constructor is not modified
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Assert that array passed to the constructor is not modified.
 *     3) The subsequent changes to array passed to the constructor have no
 *        effect on the instance created.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid StringMethodDesc array as an argument.
 *        Construct second StringMethodConstraints type object instance passing
 *        another but with the same content StringMethodDesc array as an argument.
 *        Change second array. It should remains valid.
 *        Assert that first constructed StringMethodConstraints object is equal
 *        to second.
 *     4) Throws: NullPointerException - if the argument is null.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing null
 *        as an argument.
 *        Assert that NullPointerException is thrown
 *     5) Throws: NullPointerException - if any element of the argument is null.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing array
 *        with first null element as an argument.
 *        Assert that NullPointerException is thrown
 *        Construct StringMethodConstraints type object instance passing array
 *        with first non null element and second null element as an argument.
 *        Assert that NullPointerException is thrown
 *     6) Throws: IllegalArgumentException - if the descriptors array is empty.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing empty
 *        array as an argument.
 *        Assert that IllegalArgumentException is thrown
 *     7) Throws: IllegalArgumentException - if any descriptor is preceded
 *        by another descriptor that matches at least the same methods.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing invalid
 *        array with second descriptor that matches the same methods as first
 *        descriptor as an argument.
 *        Assert that IllegalArgumentException is thrown
 *     8) Creates an instance that maps all methods to the specified
 *        constraints.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Assert that object is constructed and no exception was thrown.
 *     9) The constraints can be null, which is treated the same as
 *        an empty instance.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        empty InvocationConstraints as an argument.
 *        Construct StringMethodConstraints type object instance passing null
 *        as an argument.
 *        Assert that first constructed object is equal to second one.
 *     10) Calling this constructor is equivalent to constructing an instance
 *        of this class with an array containing a single default descriptor
 *        constructed with the specified constraints.
 *      steps:
 *        Construct StringMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Construct StringMethodDesc type object instance passing
 *        the same InvocationConstraints as an argument.
 *        Construct StringMethodConstraints type object instance passing
 *        array with only constructed StringMethodDesc object as an argument.
 *        Assert that first constructed StringMethodConstraints object
 *        is equal to second one.
 * </pre>
 */
public class Constructor_Test extends QATestEnvironment implements Test {

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
        new StringMethodConstraints(descs);

        // 2
        StringMethodDesc [] storedDescs = {methodDesc1, methodDesc2};
        StringMethodDesc [] passedDescs = {methodDesc1, methodDesc2};
        new StringMethodConstraints(passedDescs);
        for (int j = 0; j < passedDescs.length; ++j) {
            if (storedDescs[j] != passedDescs[j]) {
                throw new TestException(
                        "StringMethodDesc array was modified");
            }
        }

        // 3
        String name2 = "*someMethod";
        StringMethodDesc methodDesc3 = new StringMethodDesc(name2, constraints);
        StringMethodDesc [] descs1 = {methodDesc1, methodDesc2};
        StringMethodDesc [] descs2 = {methodDesc1, methodDesc2};
        StringMethodConstraints bmc1 = new StringMethodConstraints(descs1);
        StringMethodConstraints bmc2 = new StringMethodConstraints(descs2);
        descs2[1] = methodDesc3;
        if (!bmc1.equals(bmc2)) {
            throw new TestException(
                    "StringMethodConstraints objects should be equal");
        }

        // 4
        try {
            new StringMethodConstraints((StringMethodDesc []) null);
            throw new TestException(
                    "NullPointerException should be thrown");
        } catch (NullPointerException ignore) {
        }

        // 5
        try {
            StringMethodDesc [] descs1null = {null, methodDesc2};
            new StringMethodConstraints(descs1null);
            throw new TestException(
                    "NullPointerException should be thrown");
        } catch (NullPointerException ignore) {
        }
        try {
            StringMethodDesc [] descs2null = {methodDesc1, null};
            new StringMethodConstraints(descs2null);
            throw new TestException(
                    "NullPointerException should be thrown");
        } catch (NullPointerException ignore) {
        }

        // 6
        try {
            StringMethodDesc [] emptyDescs = {};
            new StringMethodConstraints(emptyDescs);
            throw new TestException(
                    "IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ignore) {
        }

        // 7
        try {
            StringMethodDesc [] invalidDescs = {methodDesc2, methodDesc1};
            new StringMethodConstraints(invalidDescs);
            throw new TestException(
                    "IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ignore) {
        }

        // 8
        new StringMethodConstraints(constraints);

        // 9
        InvocationConstraints emptyConstraints = new InvocationConstraints(
                (InvocationConstraint) null, null);
        bmc1 = new StringMethodConstraints(emptyConstraints);
        bmc2 = new StringMethodConstraints((InvocationConstraints) null);
        if (!bmc1.equals(bmc2)) {
            throw new TestException(
                    "StringMethodConstraints objects should be equal");
        }

        // 10
        StringMethodDesc [] simpleDescs = {methodDesc2};
        bmc1 = new StringMethodConstraints(constraints);
        bmc2 = new StringMethodConstraints(simpleDescs);
        if (!bmc1.equals(bmc2)) {
            throw new TestException(
                    "StringMethodConstraints objects should be equal");
        }
    }
}
