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
package com.sun.jini.test.spec.constraint.coreconstraint.constraintalternatives;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;

// java.util
import java.util.logging.Level;
import java.util.HashSet;

// AbstractImmutableSetTest
import com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest;

// Davis packages
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ConstraintAlternatives;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link net.jini.core.constraint.ConstraintAlternatives#elements()} method.
 *   The method should return an immutable set of all of the constraints. Any
 *   attempt to modify this set results in an
 *   {@link java.lang.UnsupportedOperationException} being thrown.
 *
 * Infrastructure:
 *     - {@link ElementsTest}
 *         performs actions; this file
 *     - {@link com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest}
 *         auxiliary abstract class that contains checker() method and is
 *         extended by {@link ElementsTest}
 *
 * Actions:
 *   Test performs the following steps:
 *     - creating {@link net.jini.core.constraint.ConstraintAlternatives}
 *       object containing the specified alternative constraints;
 *     - invoking
 *       {@link net.jini.core.constraint.ConstraintAlternatives#elements()}
 *       method on the created object;
 *     - invoking
 *       {@link com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest#checker(Object, Set)}
 *       method to verify the object returned by elements() method, i.e. that:
 *        - the returned object class implements {@link java.util.Set};
 *        - the returned set contains all of the constraints, i.e. is equal to
 *          to the set given to the constructor while creating this
 *          {@link net.jini.core.constraint.ConstraintAlternatives} object
 *          (this set doesn't contain duplicate constraints);
 *        - the returned set is an immutable set, i.e. attempt to modify this
 *          set (remove all of the elements from this set using
 *          {@link java.util.Set#clear()}) results in an
 *          {@link java.lang.UnsupportedOperationException} being thrown.
 *
 * </pre>
 */
public class ElementsTest extends AbstractImmutableSetTest {

    /**
     * {@link net.jini.core.constraint.ConstraintAlternatives} object to be
     * tested.
     */
    public ConstraintAlternatives obj;

    /**
     * Constraints for the
     * {@link net.jini.core.constraint.ConstraintAlternatives} object.
     */
    private HashSet set = new HashSet();

    /**
     * This method performs all preparations.
     * Creates {@link net.jini.core.constraint.ConstraintAlternatives} object
     * to be tested.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.config = (QAConfig) config; // or this.config = getConfig();
        
        // Create ConstraintAlternatives object to be tested
        try {
            set.add(ClientAuthentication.YES);
            set.add(ClientAuthentication.NO);
            set.add(Confidentiality.YES);
            set.add(Confidentiality.NO);
            set.add(Delegation.YES);
            set.add(Delegation.NO);
            set.add(Integrity.YES);
            set.add(Integrity.NO);
            set.add(ServerAuthentication.YES);
            set.add(ServerAuthentication.NO);
            obj = new ConstraintAlternatives(set);
        } catch (Exception e) {
            throw new TestException("Exception has been thrown while creating"
                    + " ConstraintAlternatives object", e);
        }
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws TestException {
        // net.jini.core.constraint.ConstraintAlternatives
        logger.log(Level.FINE,
                "\n\t+++++ net.jini.core.constraint.ConstraintAlternatives");
        logger.log(Level.FINE, "\n++++++ (" + obj + ").elements()");
        logger.log(Level.FINE, "obj:: " + obj);

        if (!checker(obj.elements(), set)) {
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }
}
