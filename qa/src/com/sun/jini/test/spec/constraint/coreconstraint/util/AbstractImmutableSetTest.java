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
package com.sun.jini.test.spec.constraint.coreconstraint.util;

import java.util.logging.Level;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig; // base class for QAConfig

// java.util
import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;
import java.util.Set;


/**
 * <pre>
 *
 * This is an auxiliary abstract class to test sets.
 *
 * This class is extended by the following tests:
 *   {@link com.sun.jini.test.spec.constraint.coreconstraint.PrincipalElementsTest}
 *   {@link com.sun.jini.test.spec.constraint.coreconstraint.constraintalternatives.ElementsTest}
 *   {@link com.sun.jini.test.spec.constraint.coreconstraint.invocationconstraints.PreferencesRequirementsTest}
 *
 * </pre>
 */
abstract public class AbstractImmutableSetTest extends QATestEnvironment implements Test {
    protected QAConfig config;

    /**
     * Checks the object specified as the first argument.
     * Checks the following:
     * <pre>
     *  - the class of retObj implements {@link java.util.Set} interface;
     *  - retObj is equal to iniSet, i.e. iniSet.equals(retObj) returns true;
     *  - retObj is an immutable set, i.e. any attempt to modify the set
     *    results in an {@link java.lang.UnsupportedOperationException}
     *    being thrown.
     * </pre>
     * This method is used in the tests mentioned in the class description to
     * verify results returned by the methods to be tested.
     *
     * @param retObj object to be tested
     * @param iniSet expected set
     * @return true if all checks pass or false otherwise
     *
     */
    public boolean checker(Object retObj, Set iniSet) {
        // Check that the class of retObj implements Set interface
        Class expClass = java.util.Set.class;
        logger.log(Level.FINE, "Expected type:: " + expClass.getName());
        logger.log(Level.FINE,
                "Returned type:: " + retObj.getClass().getName());

        if (!expClass.isInstance(retObj)) {
            logger.log(Level.FINE, "Set interface isn't implemented!");
            return false;
        }
        logger.log(Level.FINE, "\tpassed");
        Set retSet = (Set) retObj;

        // Check that retObj is equal to iniSet,
        // i.e. iniSet.equals(retObj) returns true
        logger.log(Level.FINE, "Expected set::\n" + iniSet);
        logger.log(Level.FINE, "Returned set::\n" + retSet);

        if (!iniSet.equals(retSet)) {
            logger.log(Level.FINE,
                    "The returned set (" + retSet + ") isn't equal to"
                    + " the expected set (" + iniSet + ")");
            return false;
        }
        logger.log(Level.FINE, "\tpassed");

        // Check that retObj is an immutable set (attempt to remove all of
        // the elements from this set results in an UnsupportedOperationException
        // being thrown)
        logger.log(Level.FINE, "Check that returned set is an immutable set");

        try {
            retSet.clear();
            logger.log(Level.FINE, "The returned set is a mutable set");
            return false;
        } catch (UnsupportedOperationException e) {
            logger.log(Level.FINE, "\tpassed");
        } catch (Exception e) {
            logger.log(Level.FINE,
                    e + " has been thrown while (" + retSet + ").clear()");
            return false;
        }
        return true;
    }
}
