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
package org.apache.river.test.spec.constraint.coreconstraint.invocationconstraints;

import java.util.logging.Level;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.QAConfig;

// java.util
import org.apache.river.qa.harness.Test;
import java.util.logging.Level;
import java.util.HashSet;

// AbstractImmutableSetTest
import org.apache.river.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest;

// Davis packages
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.InvocationConstraints;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of
 *   {@link net.jini.core.constraint.InvocationConstraints#requirements()} and
 *   {@link net.jini.core.constraint.InvocationConstraints#preferences()}
 *   method.
 *   requirements() method should return an immutable set of all of the
 *   requirements. Any attempt to modify this set results in an
 *   {@link java.lang.UnsupportedOperationException} being thrown.
 *   preferences() method should return an immutable set of all of the
 *   preferences. Any attempt to modify this set results in an
 *   {@link java.lang.UnsupportedOperationException} being thrown.
 *
 * Infrastructure:
 *     - {@link PreferencesRequirementsTest}
 *         performs actions; this file
 *     - {@link org.apache.river.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest}
 *         auxiliary abstract class that contains checker() method and is
 *         extended by {@link PreferencesRequirementsTest}
 *
 * Actions:
 *   Test performs the following steps:
 *     - creating {@link net.jini.core.constraint.InvocationConstraints}
 *       object containing the specified requirements and preferences;
 *     - invoking
 *       {@link net.jini.core.constraint.InvocationConstraints#requirements()}
 *       method on the created object;
 *     - invoking
 *       {@link org.apache.river.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest#checker(Object, Set)}
 *       method to verify the object returned by requirements() method,
 *       i.e. that:
 *        - the returned object class implements {@link java.util.Set};
 *        - the returned set contains all of the requirements, i.e. is equal to
 *          to the set given to the constructor as requirements while creating
 *          this {@link net.jini.core.constraint.InvocationConstraints} object
 *          (this set doesn't contain duplicate requirements);
 *        - the returned set is an immutable set, i.e. attempt to modify this
 *          set (remove all of the elements from this set using
 *          {@link java.util.Set#clear()}) results in an
 *          {@link java.lang.UnsupportedOperationException} being thrown;
 *     - invoking
 *       {@link net.jini.core.constraint.InvocationConstraints#preferences()}
 *       method on the created object;
 *     - invoking
 *       {@link org.apache.river.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest#checker(Object, Set)}
 *       method to verify the object returned by preferences() method,
 *       i.e. that:
 *        - the returned object class implements {@link java.util.Set};
 *        - the returned set contains all of the preferences, i.e. is equal to
 *          to the set given to the constructor as preferences while creating
 *          this {@link net.jini.core.constraint.InvocationConstraints} object
 *          (this set doesn't contain duplicate preferences);
 *        - the returned set is an immutable set, i.e. attempt to modify this
 *          set (remove all of the elements from this set using
 *          {@link java.util.Set#clear()}) results in an
 *          {@link java.lang.UnsupportedOperationException} being thrown.
 *
 * </pre>
 */
public class PreferencesRequirementsTest extends AbstractImmutableSetTest {

    /**
     * {@link net.jini.core.constraint.InvocationConstraints} object to be
     * tested.
     */
    public InvocationConstraints obj;

    /**
     * Requirements for the
     * {@link net.jini.core.constraint.InvocationConstraints} object.
     */
    private HashSet reqs = new HashSet();

    /**
     * Preferences for the
     * {@link net.jini.core.constraint.InvocationConstraints} object.
     */
    private HashSet prefs = new HashSet();

    /**
     * This method performs all preparations.
     * Creates {@link net.jini.core.constraint.InvocationConstraints} object
     * to be tested.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();
        
        // Create InvocationConstraints object to be tested
        try {
            reqs.add(ClientAuthentication.YES);
            reqs.add(Confidentiality.YES);
            reqs.add(Delegation.YES);
            reqs.add(Integrity.YES);
            reqs.add(ServerAuthentication.YES);
            prefs.add(ClientAuthentication.NO);
            prefs.add(Confidentiality.NO);
            prefs.add(Delegation.NO);
            prefs.add(Integrity.NO);
            prefs.add(ServerAuthentication.NO);
            obj = new InvocationConstraints(reqs, prefs);
        } catch (Exception e) {
            throw new TestException("Exception has been thrown while creating"
                    + " InvocationConstraints object", e);
        }
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws TestException {
        // net.jini.core.constraint.InvocationConstraints
        logger.log(Level.FINE,
                "\n\t+++++ net.jini.core.constraint.InvocationConstraints");
        // Verify InvocationConstraints.requirements() method
        logger.log(Level.FINE, "\n++++++ (" + obj + ").requirements()");
        logger.log(Level.FINE, "obj:: " + obj);

        if (!checker(obj.requirements(), reqs)) {
            throw new TestException(
                    "" + " test failed");
        }

        // Verify InvocationConstraints.preferences() method
        logger.log(Level.FINE, "\n++++++ (" + obj + ").preferences()");
        logger.log(Level.FINE, "obj:: " + obj);

        if (!checker(obj.preferences(), prefs)) {
            throw new TestException(
                    "" + " test failed");
        }
        return;
    }
}
