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
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.security.Permission;
import java.security.Policy;
import java.rmi.RemoteException;
import net.jini.security.BasicProxyPreparer;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;
import net.jini.security.policy.DynamicPolicyProvider;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the BasicProxyPreparer class
 *   setConstraints method:
 *   protected Object setConstraints(Object proxy)
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeBasicProxyPreparer - accessor of the tested BasicProxyPreparer
 *        class, that is gateway to protected method access.
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) If proxy is null, throws a NullPointerException.  
 *	steps:
 *	  construct a FakeBasicProxyPreparer object passing true for "verify",
 *	  some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call setConstraints method passing null as an argument;
 *        assert the NullPointerException is thrown;
 *     2) if methodConstraintsSpecified is false, returns the proxy.  
 *	steps:
 *	  construct a FakeBasicProxyPreparer object passing true for "verify",
 *	  some valid values for "permissions" and null for "methodConstrains"; 
 *        call setConstraints method passing some proxy as an argument;
 *        assert the methodConstraintsSpecified is false;
 *        assert the unchanged proxy is returned;
 *     3) if methodConstraintsSpecified is true, and if object does not
 *        implement RemoteMethodControl, throws a SecurityException.  
 *	steps:
 *	  construct a FakeBasicProxyPreparer object passing true for "verify",
 *	  some valid values for "permissions" and some non empty for
 *        "methodConstrains"; 
 *        call setConstraints method passing some proxy that does not
 *        implement RemoteMethodControl as an argument;
 *        assert the SecurityException is thrown;
 *     4) if methodConstraintsSpecified is true, and if object
 *        implements RemoteMethodControl, returns the result of
 *        calling RemoteMethodControl.setConstraints on the proxy,
 *        using the value returned from calling getMethodConstraints
 *        with proxy.  
 *        Returns: the proxy with updated constraints
 *      steps:
 *	  construct a FakeBasicProxyPreparer object passing true for "verify",
 *	  some valid values for "permissions" and some non empty for
 *        "methodConstrains"; 
 *        call setConstraints method passing some proxy that
 *        implement RemoteMethodControl as an argument;
 *        assert the proxy with updated constraints is returned;
 * </pre>
 */
public class SetConstraints_Test extends Constructor_Test {

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

        // 1 If proxy is null, throws a NullPointerException
        boolean verify = true;
        Permission[] perm = new Permission[] {
                    new RuntimePermission("setClassLoader")};
        MethodConstraints mc = new FakeMethodConstraints(
                    new InvocationConstraint[] {Integrity.YES});
        FakeBasicProxyPreparer fbpp = callFakeConstructor(
                3, verify, mc, perm);
        try {
            fbpp.setConstraints(null);
            throw new TestException(
                    "NullPointerException should be thrown if proxy"
                    + " argument is null");
        } catch (NullPointerException ignore) {
        }

        // 2 if methodConstraintsSpecified is false, returns the proxy
        fbpp = callFakeConstructor(
                2, verify, mc, perm);
        Object proxy = new Object();
        Object result = fbpp.setConstraints(proxy);
        if (result != proxy) {
            throw new TestException(
                    "Unchanged proxy should be returned if"
                    + " methodConstraints is not specified");
        }

        // 3) if methodConstraintsSpecified is true, and if object
        // does not implement RemoteMethodControl, throws a
        // SecurityException.
        fbpp = callFakeConstructor(
                3, verify, mc, perm);
        proxy = new Object();
        try {
            fbpp.setConstraints(proxy);
            throw new TestException(
                    "SecurityException should be thrown if object"
                    + " does not implement RemoteMethodControl");
        } catch (SecurityException ignore) {
        }

        // 4a returns the result of calling
        // RemoteMethodControl.setConstraints on the proxy.
        fbpp = callFakeConstructor(
                3, verify, mc, perm);
        FakeRemoteMethodControl frmc = new FakeRemoteMethodControl();
        RuntimeException e = new TraceException();
        frmc.catchSetConstraints(e);
        try {
            fbpp.setConstraints(frmc);
            throw new TestException(
                    "Proxy's setConstraints method should be called");
        } catch (TraceException ignore) {
        }

        // 4b check return
        fbpp = callFakeConstructor(
                3, verify, mc, perm);
        frmc = new FakeRemoteMethodControl();
        FakeRemoteMethodControl forReturn = new FakeRemoteMethodControl();
        frmc.setConstraintsReturn = forReturn;
        result = fbpp.setConstraints(frmc);
        if (result != forReturn) {
            throw new TestException(
                    "Updated proxy should be returned");
        }
    }
}
