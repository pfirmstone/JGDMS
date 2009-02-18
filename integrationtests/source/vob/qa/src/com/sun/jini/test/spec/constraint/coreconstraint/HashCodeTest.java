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
 *     {@link java.lang.Object#hashCode()} for the following classes:
 *       {@link net.jini.core.constraint.ClientAuthentication}
 *       {@link net.jini.core.constraint.Confidentiality}
 *       {@link net.jini.core.constraint.Delegation}
 *       {@link net.jini.core.constraint.Integrity}
 *       {@link net.jini.core.constraint.ServerAuthentication}
 *     {@link net.jini.core.constraint.ClientMaxPrincipal#hashCode()}
 *     {@link net.jini.core.constraint.ClientMaxPrincipalType#hashCode()}
 *     {@link net.jini.core.constraint.ClientMinPrincipal#hashCode()}
 *     {@link net.jini.core.constraint.ClientMinPrincipalType#hashCode()}
 *     {@link net.jini.core.constraint.ConnectionAbsoluteTime#hashCode()}
 *     {@link net.jini.core.constraint.ConnectionRelativeTime#hashCode()}
 *     {@link net.jini.core.constraint.ConstraintAlternatives#hashCode()}
 *     {@link net.jini.core.constraint.DelegationAbsoluteTime#hashCode()}
 *     {@link net.jini.core.constraint.DelegationRelativeTime#hashCode()}
 *     {@link net.jini.core.constraint.InvocationConstraints#hashCode()}
 *     {@link net.jini.core.constraint.ServerMinPrincipal#hashCode()}
 *   All of these methods should return hash code value for object.
 *   The following verifications are performed for each hashCode() method:
 *     - the method invoked twice on the same object produces equal hash codes;
 *     - the method invoked on equal objects produces equal hash codes;
 *     - the method invoked on different (non-equal) objects produces non-equal
 *       hash codes.
 *
 * Test Cases:
 *   TestCase #1
 *    {@link java.lang.Object#hashCode()} on
 *    {@link net.jini.core.constraint.ClientAuthentication} objects
 *   TestCase #2
 *    {@link net.jini.core.constraint.ClientMaxPrincipal#hashCode()}
 *   TestCase #3
 *    {@link net.jini.core.constraint.ClientMaxPrincipalType#hashCode()}
 *   TestCase #4
 *    {@link net.jini.core.constraint.ClientMinPrincipal#hashCode()}
 *   TestCase #5
 *    {@link net.jini.core.constraint.ClientMinPrincipalType#hashCode()}
 *   TestCase #6
 *    {@link java.lang.Object#hashCode()} on
 *    {@link net.jini.core.constraint.Confidentiality} objects
 *   TestCase #7
 *    {@link net.jini.core.constraint.ConnectionAbsoluteTime#hashCode()}
 *   TestCase #8
 *    {@link net.jini.core.constraint.ConnectionRelativeTime#hashCode()}
 *   TestCase #9
 *    {@link net.jini.core.constraint.ConstraintAlternatives#hashCode()}
 *   TestCase #10
 *    {@link java.lang.Object#hashCode()} on
 *    {@link net.jini.core.constraint.Delegation} objects
 *   TestCase #11
 *    {@link net.jini.core.constraint.DelegationAbsoluteTime#hashCode()}
 *   TestCase #12
 *    {@link net.jini.core.constraint.DelegationRelativeTime#hashCode()}
 *   TestCase #13
 *    {@link java.lang.Object#hashCode()} on
 *    {@link net.jini.core.constraint.Integrity} objects
 *   TestCase #14
 *    {@link net.jini.core.constraint.InvocationConstraints#hashCode()}
 *   TestCase #15
 *    {@link java.lang.Object#hashCode()} on
 *    {@link net.jini.core.constraint.ServerAuthentication} objects
 *   TestCase #16
 *    {@link net.jini.core.constraint.ServerMinPrincipal#hashCode()}
 *
 * Infrastructure:
 *     - {@link HashCodeTest}
 *         performs actions; this file
 *     - {@link com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractSimpleTest}
 *         auxiliary abstract class that contains run() method and is
 *         extended by {@link HashCodeTest}
 *
 * Actions:
 *   Test performs the following steps in each Test Case:
 *     - creating 3 objects of the corresponding type;
 *     - invoking {@link #checker(Object,Object,Object)} method to verify
 *       the corresponding hashCode() method, i.e. that:
 *         - the method invoked on the same object always returns the same
 *           hash code values;
 *         - the method invoked on equal objects returns equal hash code
 *           values;
 *         - the method invoked on different (non-equal) objects returns
 *           non-equal hash code values.
 *
 * </pre>
 */
public class HashCodeTest extends AbstractSimpleTest {

    /**
     * This method verifies hashCode() method on the objects specified as
     * arguments. All checks to be performed are specified in the class
     * description. This method returns true if hashCode() method runs
     * correctly.
     *
     * @param primObj  the primary object
     * @param equalObj object that is equal to the primary object
     * @param diffObj  object that isn't equal to the primary object
     * @return true if all checks for hashCode() method are satisfied or
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
         * Check that 2 invocations of hashCode() on the
         * same object produce the same hash codes
         */
        logger.log(Level.FINE,
                "Check that 2 invocations of hashCode() on the"
                + " same object produce the same hash codes ...");
        int primObj_hc1 = primObj.hashCode();
        int primObj_hc2 = primObj.hashCode();

        if (primObj_hc1 != primObj_hc2) {
            logger.log(Level.FINE,
                    "2 invocations of hashCode() on the"
                    + " same object produce different hash codes:\n"
                    + primObj_hc1 + "\n" + primObj_hc2);
            return false;
        }
        logger.log(Level.FINE, "\tpassed");

        /*
         * Check that 2 equal objects have equal hash codes
         */
        logger.log(Level.FINE, "Check that 2 equal objects have equal hash"
                + " codes ...");
        int primObj_hc = primObj.hashCode();
        int equalObj_hc = equalObj.hashCode();

        if (primObj_hc != equalObj_hc) {
            logger.log(Level.FINE,
                    "2 equal objects have the non-equal hash codes:\n"
                    + primObj_hc + "\n" + equalObj_hc);
            return false;
        }
        logger.log(Level.FINE, "\tpassed");

        /*
         * Check that 2 different objects have different hash codes
         */
        logger.log(Level.FINE, "Check that 2 different objects have different"
                + " hash codes ...");
        int diffObj_hc = diffObj.hashCode();

        if (primObj_hc == diffObj_hc) {
            logger.log(Level.FINE,
                    "2 different objects have the same hash codes: "
                    + primObj_hc);
            return false;
        }
        logger.log(Level.FINE, "\tpassed");
        return true;
    }
}
