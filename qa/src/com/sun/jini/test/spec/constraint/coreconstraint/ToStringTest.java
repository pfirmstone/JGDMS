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
package com.sun.jini.test.spec.constraint.coreconstraint;

// java.util
import java.util.logging.Level;

// AbstractSimpleTest
import com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractSimpleTest;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the following methods:
 *     {@link net.jini.core.constraint.ClientAuthentication#toString()}
 *     {@link net.jini.core.constraint.ClientMaxPrincipal#toString()}
 *     {@link net.jini.core.constraint.ClientMaxPrincipalType#toString()}
 *     {@link net.jini.core.constraint.ClientMinPrincipal#toString()}
 *     {@link net.jini.core.constraint.ClientMinPrincipalType#toString()}
 *     {@link net.jini.core.constraint.Confidentiality#toString()}
 *     {@link net.jini.core.constraint.ConnectionAbsoluteTime#toString()}
 *     {@link net.jini.core.constraint.ConnectionRelativeTime#toString()}
 *     {@link net.jini.core.constraint.ConstraintAlternatives#toString()}
 *     {@link net.jini.core.constraint.Delegation#toString()}
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#toString()}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#toString()}
 *     {@link net.jini.core.constraint.Integrity#toString()}
 *     {@link net.jini.core.constraint.InvocationConstraints#toString()}
 *     {@link net.jini.core.constraint.ServerAuthentication#toString()}
 *     {@link net.jini.core.constraint.ServerMinPrincipal#toString()}
 *   All of these methods should return a string representation of the
 *   corresponding object. The following verifications are performed for each
 *   toString() method:
 *     - the method invoked twice on the same object produces equal
 *       {@link java.lang.String} objects;
 *     - the method invoked on equal objects produces equal
 *       {@link java.lang.String} objects.
 *
 * Test Cases:
 *   TestCase #1
 *    {@link net.jini.core.constraint.ClientAuthentication#toString()}
 *   TestCase #2
 *    {@link net.jini.core.constraint.ClientMaxPrincipal#toString()}
 *   TestCase #3
 *    {@link net.jini.core.constraint.ClientMaxPrincipalType#toString()}
 *   TestCase #4
 *    {@link net.jini.core.constraint.ClientMinPrincipal#toString()}
 *   TestCase #5
 *    {@link net.jini.core.constraint.ClientMinPrincipalType#toString()}
 *   TestCase #6
 *    {@link net.jini.core.constraint.Confidentiality#toString()}
 *   TestCase #7
 *    {@link net.jini.core.constraint.ConnectionAbsoluteTime#toString()}
 *   TestCase #8
 *    {@link net.jini.core.constraint.ConnectionRelativeTime#toString()}
 *   TestCase #9
 *    {@link net.jini.core.constraint.ConstraintAlternatives#toString()}
 *   TestCase #10
 *    {@link net.jini.core.constraint.Delegation#toString()}
 *   TestCase #11
 *    {@link net.jini.core.constraint.DelegationAbsoluteTime#toString()}
 *   TestCase #12
 *    {@link net.jini.core.constraint.DelegationRelativeTime#toString()}
 *   TestCase #13
 *    {@link net.jini.core.constraint.Integrity#toString()}
 *   TestCase #14
 *    {@link net.jini.core.constraint.InvocationConstraints#toString()}
 *   TestCase #15
 *    {@link net.jini.core.constraint.ServerAuthentication#toString()}
 *   TestCase #16
 *    {@link net.jini.core.constraint.ServerMinPrincipal#toString()}
 *
 * Infrastructure:
 *     - {@link ToStringTest}
 *         performs actions; this file
 *     - {@link com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractSimpleTest}
 *         auxiliary abstract class that contains run() method and is
 *         extended by {@link ToStringTest}
 *
 * Actions:
 *   Test performs the following steps in each Test Case:
 *     - creating 3 objects of the corresponding type;
 *     - invoking {@link #checker(Object,Object,Object)} method to verify
 *       the corresponding toString() method, i.e. that:
 *         - the method invoked twice on the same object produces equal
 *           {@link java.lang.String} objects;
 *         - the method invoked on equal objects produces equal
 *           {@link java.lang.String} objects.
 *
 * </pre>
 */
public class ToStringTest extends AbstractSimpleTest {

    /**
     * This method verifies toString() method on the objects specified as
     * arguments. All checks to be performed are specified in the class
     * description. This method returns true if toString() method runs
     * correctly.
     *
     * @param primObj  the primary object
     * @param equalObj object that is equal to the primary object
     * @param diffObj  object that isn't equal to the primary object
     * @return true if all checks for toString() method are satisfied or
     *         false otherwise
     */
    public boolean checker(Object primObj, Object equalObj, Object diffObj) {
        logger.log(Level.FINE, "Primary object:: " + primObj);
        logger.log(Level.FINE,
                "Object that is equal to the primary one:: " + equalObj);
        logger.log(Level.FINE,
                "Object that is different from the primary one:: " + diffObj);

        if (!primObj.equals(equalObj)) {
            logger.log(Level.FINE, primObj + " isn't equal to " + equalObj);
            return false;
        }

        if (primObj.equals(diffObj)) {
            logger.log(Level.FINE, primObj + " is equal to " + diffObj);
            return false;
        }

        /*
         * Check that toString() method invoked twice on the same object
         * produces equal String objects
         */
        logger.log(Level.FINE,
                "Check that toString() method invoked twice on the same object"
                + " produces equal String objects ...");
        String str1 = primObj.toString();
        String str2 = primObj.toString();

        if (!(str1.equals(str2))) {
            logger.log(Level.FINE,
                    "toString() method invoked twice on the same object"
                    + " produces non-equal String objects:\n" + str1 + "\n"
                    + str2);
            return false;
        }
        logger.log(Level.FINE, "\tpassed");

        /*
         * Check that 2 equal objects have equal string representations
         */
        logger.log(Level.FINE,
                "Check that 2 equal objects have equal string"
                + " representations ...");
        String primObj_str = primObj.toString();
        String equalObj_str = equalObj.toString();

        if (!(primObj_str.equals(equalObj_str))) {
            logger.log(Level.FINE,
                    "2 equal objects have non-equal string representations:\n"
                    + primObj_str + "\n" + equalObj_str);
            return false;
        }
        logger.log(Level.FINE, "\tpassed");
        return true;
    }
}
