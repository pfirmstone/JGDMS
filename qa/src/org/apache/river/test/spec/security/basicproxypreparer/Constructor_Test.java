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
package org.apache.river.test.spec.security.basicproxypreparer;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.security.BasicProxyPreparer;
import java.security.Permission;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the constructor of
 *   BasicProxyPreparer class. There are three forms of constructor:
 *   1) public BasicProxyPreparer()
 *   2) public BasicProxyPreparer(boolean, Permission[])
 *   3) public BasicProxyPreparer(boolean, MethodConstrains, Permission[])
 *
 * Test Cases:
 *   This test contains three test case - one for each form
 *   of constructor. Each case goes through actions described below
 *   in case if they have a sense for that form.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeBasicProxyPreparer - accessor of the tested BasicProxyPreparer
 *        class, that is gateway to protected method.
 *     2) FakeMethodConstraints - a fake MethodConstraints object with
 *        configurable method return values.
 *
 * Actions:
 *   Test checks normal and exceptional variants of the
 *   parameters for BasicProxyPreparer constructor.
 *
 *   Test verifies the following assertions and performs the following steps:
 *   a) public BasicProxyPreparer():
 *     1) public BasicProxyPreparer() creates a proxy preparer that specifies
 *        not to verify proxies, grant them permissions, or set their     
 *        constraints.
 *      steps:
 *        construct a BasicProxyPreparer object without parameters;
 *   	  assert the object is constructed and no exception is thrown;
 *   	  assert the verify field is equal to false;
 *   	  assert the permissions field is equal to empty array;
 *   	  assert the methodConstraintsSpecified field is equal to false;
 *   b) public BasicProxyPreparer(boolean verify, Permission[] permissions):
 *     2) construct a BasicProxyPreparer object passing false for "verify"
 *        and null for "permissions";
 *   	  assert the object is constructed and no exception is thrown;
 *   	  assert the verify field is equal to false;
 *   	  assert the permissions field is equal to empty array;
 *   	  assert the methodConstraintsSpecified field is equal to false;
 *     3) construct a BasicProxyPreparer object passing false for "verify"
 *        and empty array for "permissions";
 *   	  assert the object is constructed and no exception is thrown;
 *   	  assert the verify field is equal to false;
 *   	  assert the permissions field is equal to empty array;
 *   	  assert the methodConstraintsSpecified field is equal to false;
 *     4) construct a BasicProxyPreparer object passing false for "verify"
 *        and array with one some item for "permissions";
 *   	  assert the object is constructed and no exception is thrown;
 *   	  assert the verify field is equal to false;
 *   	  assert the permissions field is equal to array with
 *        one element - the passed permission;
 *   	  assert the methodConstraintsSpecified field is equal to false;
 *     5) construct a BasicProxyPreparer object passing false for "verify"
 *        and array with two some items for "permissions";
 *   	  assert the object is constructed and no exception is thrown;
 *   	  assert the verify field is equal to false;
 *   	  assert the permissions field is equal to array with
 *        two elements - the passed permissions;
 *   	  assert the methodConstraintsSpecified field is equal to false;
 *     6) construct a BasicProxyPreparer object passing false for "verify"
 *        and array with one null item for "permissions";
 *   	  assert the NullPointerException is thrown;
 *     7) construct a BasicProxyPreparer object passing false for "verify"
 *        and array with two items, first null and second - valid,
 *        for "permissions";
 *   	  assert the NullPointerException is thrown;
 *     8) construct a BasicProxyPreparer object passing false for "verify"
 *        and array with two items, first valid and second - null
 *        for "permissions";
 *   	  assert the NullPointerException is thrown;
 *     9) repeat actions from 2 to 8 passing true instead of false for
 *        "verify" parameter;
 *   c) public BasicProxyPreparer(boolean verify,
 *        MethodConstrains methodConstraints, Permission[] permissions):
 *     10) repeat actions from 2 to 9 passing null for "methodConstrains",
 *        but assertion for methodConstraints fields
 *        should be:
 *        assert the methodConstraintsSpecified field is true;
 *        assert the methodConstraints field is equal to
 *        methodConstrains specified as parameter;
 *     11) repeat actions from 2 to 9 passing empty instance for
 *        "methodConstrains", but assertion for methodConstraints fields
 *        should be:
 *        assert the methodConstraintsSpecified field is true;
 *        assert the methodConstraints field is equal to
 *        methodConstrains specified as parameter;
 *     12) repeat actions from 2 to 9 passing some non empty instance for
 *        "methodConstrains", but assertion for methodConstraints fields
 *        should be:
 *        assert the methodConstraintsSpecified field is true;
 *        assert the methodConstraints field is equal to
 *        methodConstrains specified as parameter;
 * </pre>
 */
public class Constructor_Test extends QATestEnvironment implements Test {
    /**
     * Test cases description.
     * Elemens: amount of arguments in BasicProxyPreparer constructor
     * plus 10 if verify should be true
     * plus 100 if methodConstrains should be empty instance
     * plus 200 if methodConstrains should be some non empty instance
     */
    protected final int [] cases = { 0, 2, 3, 12, 13, 103, 113, 203, 213 };

    /**
     * Run BasicProxyPreparer constructor for valid test case.
     *
     * @param testCase int value 0, 2 or 3 according to
     * amount of arguments in BasicProxyPreparer constructor
     * @param verify whether to verify if proxies are trusted
     * @param methodConstraints method constraints to use when verifying 
     *	      and setting constraints
     * @param permissions permissions to grant, or <code>null</code> if no
     *	      permissions should be granted
     */
    protected FakeBasicProxyPreparer callFakeConstructor(
            int testCase,
            boolean verify,
            MethodConstraints methodConstraints,
            Permission[] permissions) {
        if (testCase == 0) { // constructor without arguments
            return new FakeBasicProxyPreparer();
        } else if (testCase == 2) { // constructor with 2 arguments
            return new FakeBasicProxyPreparer(verify, permissions);
        } else { // constructor with 3 arguments
            return new FakeBasicProxyPreparer(verify,
                    methodConstraints, permissions);
        }
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");

        for (int i = 0; i < cases.length; ++i) {
            int testCase = cases[i];
            logger.log(Level.INFO, "--> " + testCase);
            
            MethodConstraints mc = null;
            if (testCase > 200) {
                mc = new FakeMethodConstraints(
                        new InvocationConstraint[] {Integrity.NO});
                testCase -= 200;
            } else if (testCase > 100) {
                mc = new FakeMethodConstraints(null);
                testCase -= 100;
            }
            boolean verify = false;
            if (testCase > 10) {
                verify = true;
                testCase -= 10;
            }
            
            // 1, 2
            FakeBasicProxyPreparer bpp = callFakeConstructor(
                    testCase, verify, mc, null);
            if (bpp.getVerifyField() != verify) {
                throw new TestException(
                        "verify field should be equal to " + verify);
            }     
            if (bpp.getPermissionsField() == null) {
                throw new TestException(
                        "permissions field should not be null");
            }     
            if (bpp.getPermissionsField().length != 0) {
                throw new TestException(
                        "permissions field should be empty array");
            }
            boolean expectedMethodConstraintsSpecifiedField = (testCase == 3);
            if (bpp.getMethodConstraintsSpecifiedField()
                    != expectedMethodConstraintsSpecifiedField) {
                throw new TestException(
                        "methodConstraintsSpecified field should be equal to"
                        + expectedMethodConstraintsSpecifiedField);
            }
            if (expectedMethodConstraintsSpecifiedField) {
                if (bpp.getMethodConstraintsField() != mc) {
                    throw new TestException(
                            "methodConstraints field should be equal to " + mc);
                }
            } else {
                if (bpp.getMethodConstraintsField() != null) {
                    throw new TestException(
                            "methodConstraints field should be equal to null");
                }
            } 
            
            if (testCase == 0) { continue; }
            
            // 3
            bpp = callFakeConstructor(
                    testCase, verify, mc, new Permission[] {});
            if (bpp.getVerifyField() != verify) {
                throw new TestException(
                        "verify field should be equal to " + verify);
            }     
            if (bpp.getPermissionsField() == null) {
                throw new TestException(
                        "permissions field should not be null");
            }     
            if (bpp.getPermissionsField().length != 0) {
                throw new TestException(
                        "permissions field should be empty array");
            }
            if (bpp.getMethodConstraintsSpecifiedField()
                    != expectedMethodConstraintsSpecifiedField) {
                throw new TestException(
                        "methodConstraintsSpecified field should be equal to"
                        + expectedMethodConstraintsSpecifiedField);
            }
            if (expectedMethodConstraintsSpecifiedField) {
                if (bpp.getMethodConstraintsField() != mc) {
                    throw new TestException(
                            "methodConstraints field should be equal to " + mc);
                }
            } else {
                if (bpp.getMethodConstraintsField() != null) {
                    throw new TestException(
                            "methodConstraints field should be equal to null");
                }
            } 

            // 4
            Permission somePermission = new RuntimePermission("getClassLoader");
            bpp = callFakeConstructor(
                    testCase, verify, mc,
                    new Permission[] {somePermission});
            if (bpp.getVerifyField() != verify) {
                throw new TestException(
                        "verify field should be equal to " + verify);
            }     
            if (bpp.getPermissionsField() == null) {
                throw new TestException(
                        "permissions field should not be null");
            }     
            if (bpp.getPermissionsField().length != 1) {
                throw new TestException(
                        "permissions field should be one element array");
            }
            if (bpp.getPermissionsField()[0] != somePermission) {
                throw new TestException(
                        "permissions contains invalid element");
            }
            if (bpp.getMethodConstraintsSpecifiedField()
                    != expectedMethodConstraintsSpecifiedField) {
                throw new TestException(
                        "methodConstraintsSpecified field should be equal to"
                        + expectedMethodConstraintsSpecifiedField);
            }
            if (expectedMethodConstraintsSpecifiedField) {
                if (bpp.getMethodConstraintsField() != mc) {
                    throw new TestException(
                            "methodConstraints field should be equal to " + mc);
                }
            } else {
                if (bpp.getMethodConstraintsField() != null) {
                    throw new TestException(
                            "methodConstraints field should be equal to null");
                }
            } 

            // 5
            Permission someAnotherPermission =
                    new RuntimePermission("setClassLoader");
            bpp = callFakeConstructor(
                    testCase, verify, mc,
                    new Permission[] {
                        somePermission,
                        someAnotherPermission
                    } );
            if (bpp.getVerifyField() != verify) {
                throw new TestException(
                        "verify field should be equal to " + verify);
            }     
            if (bpp.getPermissionsField() == null) {
                throw new TestException(
                        "permissions field should not be null");
            }
            if (bpp.getPermissionsField().length != 2) {
                throw new TestException(
                        "permissions field should be two elements array");
            }
            if (bpp.getPermissionsField()[0] != somePermission) {
                throw new TestException(
                        "permissions contains invalid element");
            }
            if (bpp.getPermissionsField()[1] != someAnotherPermission) {
                throw new TestException(
                        "permissions contains invalid element");
            }
            if (bpp.getMethodConstraintsSpecifiedField()
                    != expectedMethodConstraintsSpecifiedField) {
                throw new TestException(
                        "methodConstraintsSpecified field should be equal to"
                        + expectedMethodConstraintsSpecifiedField);
            }
            if (expectedMethodConstraintsSpecifiedField) {
                if (bpp.getMethodConstraintsField() != mc) {
                    throw new TestException(
                            "methodConstraints field should be equal to " + mc);
                }
            } else {
                if (bpp.getMethodConstraintsField() != null) {
                    throw new TestException(
                            "methodConstraints field should be equal to null");
                }
            }

            // 6
            try {
                bpp = callFakeConstructor(
                        testCase, verify, mc,
                        new Permission[] {null});
                throw new TestException(
                        "NullPointerException should be thrown if"
                        + " permissions argument contains null element");
            } catch (NullPointerException ignore) {
            }

            // 7
            try {
                bpp = callFakeConstructor(
                        testCase, verify, mc,
                        new Permission[] {somePermission, null});
                throw new TestException(
                        "NullPointerException should be thrown if"
                        + " permissions argument contains null element");
            } catch (NullPointerException ignore) {
            }

            // 8
            try {
                bpp = callFakeConstructor(
                        testCase, verify, mc,
                        new Permission[] {null, somePermission});
                throw new TestException(
                        "NullPointerException should be thrown if"
                        + " permissions argument contains null element");
            } catch (NullPointerException ignore) {
            }
	}
    }
}
