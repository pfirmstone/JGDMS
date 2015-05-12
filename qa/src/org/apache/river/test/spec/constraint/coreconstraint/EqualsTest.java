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
package org.apache.river.test.spec.constraint.coreconstraint;

// java.util
import java.util.logging.Level;

// AbstractSimpleTest
import org.apache.river.test.spec.constraint.coreconstraint.util.AbstractSimpleTest;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the following methods:
 *     {@link java.lang.Object#equals(Object)} for the following classes:
 *       {@link net.jini.core.constraint.ClientAuthentication}
 *       {@link net.jini.core.constraint.Confidentiality}
 *       {@link net.jini.core.constraint.Delegation}
 *       {@link net.jini.core.constraint.Integrity}
 *       {@link net.jini.core.constraint.ServerAuthentication}
 *     {@link net.jini.core.constraint.ClientMaxPrincipal#equals(Object)}
 *     {@link net.jini.core.constraint.ClientMaxPrincipalType#equals(Object)}
 *     {@link net.jini.core.constraint.ClientMinPrincipal#equals(Object)}
 *     {@link net.jini.core.constraint.ClientMinPrincipalType#equals(Object)}
 *     {@link net.jini.core.constraint.ConnectionAbsoluteTime#equals(Object)}
 *     {@link net.jini.core.constraint.ConnectionRelativeTime#equals(Object)}
 *     {@link net.jini.core.constraint.ConstraintAlternatives#equals(Object)}
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#equals(Object)}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#equals(Object)}
 *     {@link net.jini.core.constraint.InvocationConstraints#equals(Object)}
 *     {@link net.jini.core.constraint.ServerMinPrincipal#equals(Object)}
 *   All of these methods should return true for equal objects and false for
 *   non-equal object. The following verifications are performed for each
 *   equals() method:
 *     - the method for the same object returns true;
 *     - the method for equal objects returns true;
 *     - the method for different (non-equal) objects returns false.
 *   For different classes equals() method has different sense according to
 *   the classes descriptions.
 *
 * Test Cases:
 *   TestCase #1
 *    {@link java.lang.Object#equals(Object)} for
 *    {@link net.jini.core.constraint.ClientAuthentication} objects
 *   TestCase #2
 *    {@link net.jini.core.constraint.ClientMaxPrincipal#equals(Object)}
 *   TestCase #3
 *    {@link net.jini.core.constraint.ClientMaxPrincipalType#equals(Object)}
 *   TestCase #4
 *    {@link net.jini.core.constraint.ClientMinPrincipal#equals(Object)}
 *   TestCase #5
 *    {@link net.jini.core.constraint.ClientMinPrincipalType#equals(Object)}
 *   TestCase #6
 *    {@link java.lang.Object#equals(Object)} for
 *    {@link net.jini.core.constraint.Confidentiality} objects
 *   TestCase #7
 *    {@link net.jini.core.constraint.ConnectionAbsoluteTime#equals(Object)}
 *   TestCase #8
 *    {@link net.jini.core.constraint.ConnectionRelativeTime#equals(Object)}
 *   TestCase #9
 *    {@link net.jini.core.constraint.ConstraintAlternatives#equals(Object)}
 *   TestCase #10
 *    {@link java.lang.Object#equals(Object)} for
 *    {@link net.jini.core.constraint.Delegation} objects
 *   TestCase #11
 *    {@link net.jini.core.constraint.DelegationAbsoluteTime#equals(Object)}
 *   TestCase #12
 *    {@link net.jini.core.constraint.DelegationRelativeTime#equals(Object)}
 *   TestCase #13
 *    {@link java.lang.Object#equals(Object)} for
 *    {@link net.jini.core.constraint.Integrity} objects
 *   TestCase #14
 *    {@link net.jini.core.constraint.InvocationConstraints#equals(Object)}
 *   TestCase #15
 *    {@link java.lang.Object#equals(Object)} for
 *    {@link net.jini.core.constraint.ServerAuthentication} objects
 *   TestCase #16
 *    {@link net.jini.core.constraint.ServerMinPrincipal#equals(Object)}
 *
 * Infrastructure:
 *     - {@link EqualsTest}
 *         performs actions; this file
 *     - {@link org.apache.river.test.spec.constraint.coreconstraint.util.AbstractSimpleTest}
 *         auxiliary abstract class that contains run() method and is
 *         extended by {@link EqualsTest}
 *
 * Actions:
 *   Test performs the following steps in each Test Case:
 *     - creating 3 objects of the corresponding type;
 *     - invoking {@link #checker(Object,Object,Object)} method to verify
 *       the corresponding equals() method, i.e. that:
 *         - the method for the same object returns true;
 *         - the method for equal objects returns true;
 *         - the method for different (non-equal) objects returns false.
 *
 * </pre>
 */
public class EqualsTest extends AbstractSimpleTest {

    /**
     * This method verifies equals() method on the objects specified as
     * arguments. All checks to be performed are specified in the class
     * description. This method returns true if equals() method runs
     * correctly.
     *
     * @param primObj  the primary object
     * @param equalObj object that is equal to the primary object
     * @param diffObj  object that isn't equal to the primary object
     * @return true if all checks for equals() method are satisfied or
     *         false otherwise
     */
    public boolean checker(Object primObj, Object equalObj, Object diffObj) {
        logger.log(Level.FINE, "Primary object:: " + primObj);
        logger.log(Level.FINE,
                "Object that is equal to the primary one:: " + equalObj);
        logger.log(Level.FINE,
                "Object that is different from the primary one:: " + diffObj);

        /*
         * Check that equals() method for the same object returns true
         */
        logger.log(Level.FINE,
                "Check that equals() method for the same object returns"
                + " true ...");

        if (!(primObj.equals(primObj))) {
            logger.log(Level.FINE,
                    "equals() method for the same object returns false");
            return false;
        }
        logger.log(Level.FINE, "\tpassed");

        /*
         * Check that equals() method for 2 equal objects returns true
         */
        logger.log(Level.FINE,
                "Check that equals() method for 2 equal objects"
                + " returns true ...");

        if (!(primObj.equals(equalObj))) {
            logger.log(Level.FINE,
                    "equals() method for 2 equal objects returns false");
            return false;
        }
        logger.log(Level.FINE, "\tpassed");

        /*
         * Check that equals() method for 2 different objects returns false
         */
        logger.log(Level.FINE,
                "Check that equals() method for 2 different"
                + " objects returns false ...");

        if (primObj.equals(diffObj)) {
            logger.log(Level.FINE,
                    "equals() method for 2 different objects returns false");
            return false;
        }
        logger.log(Level.FINE, "\tpassed");
        return true;
    }
}
