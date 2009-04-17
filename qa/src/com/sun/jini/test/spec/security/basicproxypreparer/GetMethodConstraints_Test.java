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
package com.sun.jini.test.spec.security.basicproxypreparer;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.security.Permission;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import net.jini.security.BasicProxyPreparer;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the BasicProxyPreparer class
 *   getMethodConstraints method:
 *   protected MethodConstraints getMethodConstraints(Object proxy)
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeBasicProxyPreparer - accessor of the tested BasicProxyPreparer
 *        class, that is gateway to protected method.
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) The default implementation returns the value of methodConstraints if
 *        methodConstraintsSpecified is true, ...
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call getMethodConstraints method passing some proxy as argument;
 *        assert the same methodConstraints as passed to constructor
 *        is returned;
 *     2) The default implementation ... if
 *        methodConstraintsSpecified is true, else 
 *        returns the constraints on the specified proxy if it implements
 *        RemoteMethodControl, ...                 
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and null for
 *        "methodConstrains"; 
 *        assert the methodConstraintsSpecified field is equal to false;
 *        call getMethodConstraints method passing some proxy that
 *        implements RemoteMethodControl as argument;
 *        assert the constraints on the specified proxy is returned;
 *     3) The default implementation returns ... if
 *        methodConstraintsSpecified is true, else 
 *        returns the constraints on the specified proxy if it implements
 *        RemoteMethodControl, else returns null. Check the null return case.
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and null for
 *        "methodConstrains"; 
 *        assert the methodConstraintsSpecified field is equal to false;
 *        call getMethodConstraints method passing some proxy that
 *        does not implements RemoteMethodControl as argument;
 *        assert null is returned;
 * </pre>
 */
public class GetMethodConstraints_Test extends QATest {

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
        
        // 1 returns the value of methodConstraints
        boolean verify = true;
        Permission[] perm = new Permission[] {
                    new RuntimePermission("setClassLoader")};
        MethodConstraints mc = new FakeMethodConstraints(
                    new InvocationConstraint[] {Integrity.YES});
        FakeBasicProxyPreparer fbpp = callFakeConstructor(
                3, verify, mc, perm);
        Object proxy = new Object();
        if (fbpp.getMethodConstraints(proxy) != mc) {
            throw new TestException(
                    "getMethodConstraints method returns invalid value");
        }

        // 2 returns the value of proxy.getConstraints
        MethodConstraints mc2 = new FakeMethodConstraints(
                    new InvocationConstraint[] {Integrity.NO});
        FakeRemoteMethodControl frmc = new FakeRemoteMethodControl();
        proxy = frmc.setConstraints(mc2);
        fbpp = callFakeConstructor(
                2, verify, mc, perm);
        logger.log(Level.INFO, fbpp.toString());
        MethodConstraints mc3 = fbpp.getMethodConstraints(proxy);
        logger.log(Level.INFO, mc3.toString());
        if (fbpp.getMethodConstraints(proxy) != mc2) {
            throw new TestException(
                    "getMethodConstraints method returns invalid value");
        }

        // 3 returns null
        fbpp = callFakeConstructor(
                2, verify, mc, perm);
        proxy = new Object();
        if (fbpp.getMethodConstraints(proxy) != null) {
            throw new TestException(
                    "getMethodConstraints method should return null");
        }
    }
}
