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
 *   prepareProxy method:
 *   public Object prepareProxy(Object proxy)          
 *                     throws RemoteException
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeBasicProxyPreparer - accessor of the tested BasicProxyPreparer
 *        class, that is gateway to protected method access and emulator
 *        of good behavior of the other methods.
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) If proxy is null, throws a NullPointerException.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call prepareProxy method passing null as an argument;
 *        assert the NullPointerException is thrown;
 *     2) Otherwise, calls verify with proxy.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call prepareProxy method passing some proxy as an argument;
 *        assert the verify method with this proxy is called;
 *     3) If the verify call succeeds, calls grant with proxy.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call prepareProxy method passing some proxy as an argument;
 *        emulate normal returning from verify method when it will be called;
 *        assert the grant method with the proxy is called;
 *     4) If the grant call succeeds, returns the result of
 *        calling setConstraints with proxy.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call prepareProxy method passing some proxy as an argument;
 *        emulate normal returning from verify method when it will be called;
 *        emulate normal returning from grant method when it will be called;
 *        assert the setConstraints method with the proxy is called;
 *        emulate normal returning some other proxy from the setConstraints
 *        method when it will be called;
 *        assert the return from prepareProxy method is equal to value
 *        returned by setConstraints method;
 *     5) RemoteException - if a communication-related exception occurs.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call prepareProxy method passing some proxy as an argument;
 *        emulate normal returning from verify method when it will be called;
 *        emulate normal returning from grant method when it will be called;
 *        throw RemoteException from the verify method when it will
 *        be called;
 *        assert the RemoteException is thrown;
 *     6) SecurityException - if a security exception occurs.  
 *      steps:
 *      a) construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call prepareProxy method passing some proxy as an argument;
 *        throw SecurityException from the verify method when it will
 *        be called;
 *        assert the SecurityException is thrown;
 *      b) construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call prepareProxy method passing some proxy as an argument;
 *        emulate normal returning from verify method when it will be called;
 *        throw SecurityException from the grant method when it will
 *        be called;
 *        assert the SecurityException is thrown;
 *      c) construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call prepareProxy method passing some proxy as an argument;
 *        emulate normal returning from verify method when it will be called;
 *        emulate normal returning from grant method when it will be called;
 *        throw SecurityException from the setConstraints method when it will
 *        be called;
 *        assert the SecurityException is thrown;
 * </pre>
 */
public class PrepareProxy_Test extends Constructor_Test {

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
            fbpp.prepareProxy(null);
            throw new TestException(
                    "NullPointerException should be thrown if proxy"
                    + " argument is null");
        } catch (NullPointerException ignore) {
        }

        // 2 check verify call
        fbpp = callFakeConstructor(3, verify, mc, perm);
        RuntimeException e = new TraceException();
        fbpp.catchVerify(e);
        Object proxy = new Object();
        try {
            fbpp.prepareProxy(proxy);
            throw new TestException(
                    "verify method should be called");
        } catch (TraceException ignore) {
            if(fbpp.storedProxy != proxy) {
                throw new TestException(
                        "invalid proxy is passed as an argument of"
                        + " verify method");
            }
        }

        Policy.setPolicy(new DynamicPolicyProvider());

        // 3 check grant call
        fbpp = callFakeConstructor(3, verify, mc, perm);
        e = new TraceException();
        fbpp.skipVerify();
        fbpp.catchGrant(e);
        proxy = new FakeRemoteMethodControl();
        try {
            fbpp.prepareProxy(proxy);
            throw new TestException(
                    "grant method should be called");
        } catch (TraceException ignore) {
            if(fbpp.storedProxy != proxy) {
                throw new TestException(
                        "invalid proxy is passed as an argument of"
                        + " grant method");
            }
        }

        // 4a check setConstraints call
        fbpp = callFakeConstructor(3, verify, mc, perm);
        e = new TraceException();
        fbpp.skipVerify();
        fbpp.catchSetConstraints(e);
        proxy = new FakeRemoteMethodControl();
        try {
            fbpp.prepareProxy(proxy);
            throw new TestException(
                    "grant method should be called");
        } catch (TraceException ignore) {
            if(fbpp.storedProxy != proxy) {
                throw new TestException(
                        "invalid proxy is passed as an argument of"
                        + " grant method");
            }
        }

        // 4b check setConstraints return
        fbpp = callFakeConstructor(3, verify, mc, perm);
        e = new TraceException();
        fbpp.skipVerify();
        proxy = new FakeRemoteMethodControl();
        Object pp = fbpp.prepareProxy(proxy);
        if (pp != fbpp.storedProxy) {
            throw new TestException(
                    "invalid object was returned");
        }

        // 5 RemoteException
        fbpp = callFakeConstructor(3, verify, mc, perm);
        fbpp.remoteExceptionInVerify();
        proxy = new FakeRemoteMethodControl();
        try {
            fbpp.prepareProxy(proxy);
            throw new TestException(
                    "RemoteException should be thrown");
        } catch (RemoteException ignore) {
        }

        // 6a SecurityException in verify
        fbpp = callFakeConstructor(3, verify, mc, perm);
        SecurityException se = new SecurityException();
        fbpp.catchVerify(se);
        proxy = new FakeRemoteMethodControl();
        try {
            fbpp.prepareProxy(proxy);
            throw new TestException(
                    "SecurityException should be thrown");
        } catch (SecurityException ignore) {
        }

        // 6b SecurityException in grant
        fbpp = callFakeConstructor(3, verify, mc, perm);
        fbpp.skipVerify();
        fbpp.catchGrant(se);
        proxy = new FakeRemoteMethodControl();
        try {
            fbpp.prepareProxy(proxy);
            throw new TestException(
                    "SecurityException should be thrown");
        } catch (SecurityException ignore) {
        }

        // 6c SecurityException in setConstraints
        fbpp = callFakeConstructor(3, verify, mc, perm);
        fbpp.skipVerify();
        fbpp.catchSetConstraints(se);
        proxy = new FakeRemoteMethodControl();
        try {
            fbpp.prepareProxy(proxy);
            throw new TestException(
                    "SecurityException should be thrown");
        } catch (SecurityException ignore) {
        }
    }
}
