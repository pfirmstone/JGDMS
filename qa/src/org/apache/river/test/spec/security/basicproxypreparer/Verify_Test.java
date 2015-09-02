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
import java.util.logging.Logger;
import java.util.logging.Level;
import java.security.Permission;
import java.security.Policy;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.RemoteException;
import net.jini.security.BasicProxyPreparer;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.Integrity;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.test.spec.security.util.Util;
import org.apache.river.test.spec.security.util.TrueTrustVerifier;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the BasicProxyPreparer class
 *   verify method:
 *   protected void verify(Object proxy)
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
 *        call verify method passing null as an argument;
 *        assert the NullPointerException is thrown;
 *     2) If proxy is not null, if verify is true, calls
 *        Security.verifyObjectTrust, with proxy, null for the class
 *        loader, and, for the context, a collection containing the
 *        result of calling getMethodConstraints with proxy, or an empty
 *        collection if the constraints are null.
 *      steps:
 *        adjust Security to trust to any object;
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call verify method passing some proxy that should be trusted as
 *        an argument;
 *        assert that no exception is thrown;
 *     3) If proxy is not null, if verify is false, do nothing.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing false for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call verify method passing some proxy as an argument;
 *        assert that no exception is thrown;
 *     4) RemoteException - if a communication-related exception occurs.  
 *      steps:
 *        ?;
 *     5) SecurityException - if verifying that the proxy is trusted fails.  
 *      steps:
 *        construct a FakeBasicProxyPreparer object passing true for "verify",
 *        some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call verify method passing some proxy that should not be trusted as
 *        an argument;
 *        assert the SecurityException is thrown;
 * </pre>
 */
public class Verify_Test extends Constructor_Test {

    /** Resource name method */
    protected static String resName =
            "META-INF/services/net.jini.security.TrustVerifier";

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
            fbpp.verify(null);
            throw new TestException(
                    "NullPointerException should be thrown if proxy"
                    + " argument is null");
        } catch (NullPointerException ignore) {
        }

        // 3 If proxy is not null, if verify is false, do nothing
        fbpp = callFakeConstructor(3, false, mc, perm);
        FakeRemoteMethodControl frmc = new FakeRemoteMethodControl();
        fbpp.verify(frmc);

        // 5 SecurityException - if verifying that the proxy
        // is trusted fails
        fbpp = callFakeConstructor(3, verify, mc, perm);
        frmc = new FakeRemoteMethodControl();
        try {
            fbpp.verify(frmc);
            throw new TestException(
                    "SecurityException should be thrown if proxy"
                    + " is not trusted");
        } catch (SecurityException ignore) {
        }

        // 2 If proxy is not null, if verify is true, if trusted
        File jarFile = Util.createResourceJar(resName,
                new Class[] { TrueTrustVerifier.class });
        URL[] urls = { jarFile.toURI().toURL() };
        ClassLoader testCl = new URLClassLoader(urls);
        Thread.currentThread().setContextClassLoader(testCl);

        fbpp = callFakeConstructor(3, verify, mc, perm);
        fbpp.verify(frmc);
    }
}
