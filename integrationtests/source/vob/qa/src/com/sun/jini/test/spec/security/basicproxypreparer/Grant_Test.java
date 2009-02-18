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
import java.security.Policy;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.policy.DynamicPolicyProvider;

/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the BasicProxyPreparer class
 *   grant method:
 *   protected void grant(Object proxy)
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeBasicProxyPreparer - accessor of the tested BasicProxyPreparer
 *        class, that is gateway to protected method access.
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) If proxy is null, throws a NullPointerException.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call grant method passing null as an argument;
 *        assert the NullPointerException is thrown;
 *     2) Otherwise, calls getPermissions with proxy to determine what
 *        permissions should be granted.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call grant method passing some proxy as an argument;
 *        assert getPermissions with this proxy is called;
 *     3) If the permissions are not empty, calls Security.grant, with the
 *        proxy's class as the class argument and those permissions.
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values values for "permissions" and some non empty
 *        instance for "methodConstrains";
 *        call grant method passing some proxy as an argument;
 *        assert no exception is thrown;
 *     4) If grant discovers that dynamic permission grants are not
 *        supported and throws a UnsupportedOperationException, catches that
 *        exception and throws a SecurityException.
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some values for "permissions" and some non empty instance for
 *        "methodConstrains";
 *        call grant method passing some proxy as an argument;
 *        Policy provider should not support dynamic policy providing;
 *        assert the SecurityException is thrown;
 * </pre>
 */
public class Grant_Test extends Constructor_Test {

    /**
     * Unique exception for tracing inner call.
     */
    static public class TraceException extends RuntimeException {
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");

        // 4 dynamic permission grants are not supported
        boolean verify = true;
        Permission[] perm = new Permission[] {
                    new RuntimePermission("setClassLoader")};
        MethodConstraints mc = new FakeMethodConstraints(
                    new InvocationConstraint[] {Integrity.YES});
        FakeBasicProxyPreparer fbpp = callFakeConstructor(
                3, verify, mc, perm);
        Object proxy = new Object();
        try {
            fbpp.grant(proxy);
            throw new TestException(
                    "SecurityException should be thrown if dynamic"
                    + " permission grants are not supported");
        } catch (SecurityException ignore) {
        }

        // 1 null proxy
        fbpp = callFakeConstructor(3, verify, mc, perm);
        try {
            fbpp.grant(null);
            throw new TestException(
                    "NullPointerException should be thrown if proxy"
                    + " is null");
        } catch (NullPointerException ignore) {
        }

        // 2 check getPermissions call
        fbpp = callFakeConstructor(3, verify, mc, perm);
        RuntimeException e = new TraceException();
        fbpp.catchGetPermissions(e);
        proxy = new Object();
        try {
            fbpp.grant(proxy);
            throw new TestException(
                    "getPermissions method should be called");
        } catch (TraceException ignore) {
            if(fbpp.storedProxy != proxy) {
                throw new TestException(
                        "invalid proxy is passed as an argument of"
                        + " getPermissions method");
            }
        }

        Policy.setPolicy(new DynamicPolicyProvider());

        // 3 normal granting
        fbpp = callFakeConstructor(3, verify, mc, perm);
        proxy = new Object();
        fbpp.grant(proxy);
    }
}
