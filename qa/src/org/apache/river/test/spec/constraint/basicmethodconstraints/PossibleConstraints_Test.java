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
import java.util.Iterator;
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
 *   This test verifies the behavior of the possibleConstraints method of
 *   BasicMethodConstraints class.
 *   public Iterator possibleConstraints()
 *
 * Actions:
 *   Test checks normal and exceptional variants of the
 *   parameters for possibleConstraints method and it's return value.
 *
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns an iterator that yields all of the possible distinct
 *        constraints that can be returned by getConstraints, in arbitrary
 *        order and with duplicates permitted.
 *      steps:
 *        Construct MethodDesc array type object with several elements where
 *        all matches some method names with the different parameter types
 *        all and with the different invocation constraints all.
 *        Construct BasicMethodConstraints type object instance passing this
 *        MethodDesc array as an argument.
 *        Call possibleConstraints method.
 *        Assert that result yield all used constraints.
 *     2) Returns an iterator. Another BasicMethodConstraints constructor
 *        form.
 *      steps:
 *        Construct BasicMethodConstraints type object instance passing some
 *        valid non empty InvocationConstraints as an argument.
 *        Call possibleConstraints method.
 *        Assert that result yield used constraints.
 *     3) The iterator throws an UnsupportedOperationException on any attempt
 *        to remove an element.
 *      steps:
 *        Construct MethodDesc array type object with several elements where
 *        all matches some method names with the different parameter types
 *        all and with the different invocation constraints all.
 *        Construct BasicMethodConstraints type object instance passing this
 *        MethodDesc array as an argument.
 *        Call possibleConstraints method.
 *        Call next method from iterator.
 *        Call remove method from iterator.
 *        Assert that UnsupportedOperationException is thrown.
 *     4) Repeat p. 3) for another BasicMethodConstraints constructor
 *        form. (the same as in p. 2)
 * </pre>
 */
public class PossibleConstraints_Test extends QATestEnvironment implements Test {
    public static class HaveFoundException extends Exception {};

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");
        
        // 1
        InvocationConstraints [] ics = new InvocationConstraints [] {
                new InvocationConstraints(Integrity.YES, null),
                new InvocationConstraints(Delegation.YES, null),
                new InvocationConstraints(Integrity.NO, null),
                new InvocationConstraints(Delegation.NO, null) };
        String name0 = "equals";
        MethodDesc methodDesc0 = new MethodDesc(name0, ics[0]);

        String name1 = "hashCode";
        MethodDesc methodDesc1 = new MethodDesc(name1,
                new Class[] { Object.class }, ics[1]);

        String name2 = "*ashCode";
        MethodDesc methodDesc2 = new MethodDesc(name2, ics[2]);

        MethodDesc methodDesc3 = new MethodDesc(ics[3]);

        MethodDesc [] descs = {methodDesc0, methodDesc1, methodDesc2,
                methodDesc3};

        BasicMethodConstraints bmc = new BasicMethodConstraints(descs);

        m1:
        for (int j = 0; j < ics.length; ++j) {
            InvocationConstraints ic = ics[j];
            Iterator i = bmc.possibleConstraints();
            while (i.hasNext()) {
                if (((InvocationConstraints)(i.next())).equals(ic)) {
                    continue m1;
                }
            }
            throw new TestException(
                    "Iterator doesn't contain constraint: " + ic);
        }

        // 2
        bmc = new BasicMethodConstraints(ics[2]);
        try {
            Iterator i = bmc.possibleConstraints();
            while (i.hasNext()) {
                if (((InvocationConstraints)(i.next())).equals(ics[2])) {
                    throw new HaveFoundException();
                }
            }
            throw new TestException(
                    "Iterator doesn't contain constraint: " + ics[2]);
        } catch (HaveFoundException ignore) {
        }

        // 3
        bmc = new BasicMethodConstraints(descs);
        Iterator iterator = bmc.possibleConstraints();
        iterator.hasNext();
        try {
            iterator.remove();
        } catch (UnsupportedOperationException ignore) {
        }

        // 4
        bmc = new BasicMethodConstraints(ics[2]);
        iterator = bmc.possibleConstraints();
        iterator.hasNext();
        try {
            iterator.remove();
        } catch (UnsupportedOperationException ignore) {
        }
    }
}
