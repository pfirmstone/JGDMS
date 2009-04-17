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
package com.sun.jini.test.spec.security.security;

import java.util.logging.Level;

// java
import java.lang.SecurityManager;
import java.security.Policy;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

// net.jini
import net.jini.security.Security;
import net.jini.security.SecurityContext;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.spec.security.util.BasePolicyProvider;
import com.sun.jini.test.spec.security.util.SCSPolicyProvider;
import com.sun.jini.test.spec.security.util.SCSSecurityManager;
import com.sun.jini.test.spec.security.util.TestPrivilegedAction;
import com.sun.jini.test.spec.security.util.TestPrivilegedExceptionAction;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'getContext' static method of Security class returns a snapshot of the
 *     current security context, which can be used to restore the context at a
 *     later time.  If either the installed security manager or policy provider
 *     implements the SecurityContextSource interface, then this method
 *     delegates to the SecurityContextSource.getContext method of the
 *     implementing object, with precedence given to the security manager.
 *     If neither the security manager nor the policy provider implement
 *     SecurityContextSource, then a new default SecurityContext instance is
 *     returned whose methods have the following semantics: the wrap methods
 *     each return their respective PrivilegedAction and
 *     PrivilegedExceptionAction arguments, unmodified, the
 *     getAccessControlContext method returns the AccessControlContext
 *     in effect when the security context was created
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     NonSCSSecurityManager - security manager which does not implement
 *             SecurityContextSource interface
 *     NonSCSPolicyProvider - policy provider which does not implement
 *             SecurityContextSource interface
 *     SCSSecurityManager - security manager implementing SecurityContextSource
 *             interface
 *     SCSPolicyProvider - policy provider implementing SecurityContextSource
 *             interface
 *
 * Action
 *   The test performs the following steps:
 *     1) set current security manager to NonSCSSecurityManager
 *     2) set current policy provider to NonSCSPolicyProvider
 *     3) invoke 'getContext' static method of Security class
 *     4) assert that method will return SecurityContext instance whose wrap
 *        methods return arguments, unmodified.
 *     5) set current policy provider to SCSPolicyProvider
 *     6) invoke 'getContext' static method of Security class
 *     7) assert that 'getContext' method of installed SCSPolicyProvider will
 *        be invoked
 *     8) assert that method will return the same SecurityContext as
 *        'getContext' method of SCSPolicyProvider
 *     9) set current security manager to SCSSecurityManager
 *     10) invoke 'getContext' static method of Security class
 *     11) assert that 'getContext' method of installed SCSSecurityManager will
 *         be invoked
 *     12) assert that 'getContext' method of installed SCSPolicyProvider will
 *         not be invoked
 *     13) assert that method will return the same SecurityContext as
 *         'getContext' method of SCSSecurityManager
 * </pre>
 */
public class GetContextTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        PrivilegedAction testPA = new TestPrivilegedAction();
        PrivilegedExceptionAction testPEA =
                new TestPrivilegedExceptionAction();
        AccessControlContext testACC = AccessController.getContext();
        System.setSecurityManager(new SecurityManager());
        Policy.setPolicy(new BasePolicyProvider());
        logger.fine("Security manager and policy provider are not "
                + "instances of SecurityContextSource.");
        logger.fine("Calling 'Security.getContext()' method.");
        SecurityContext sc = Security.getContext();

        if (sc.wrap(testPA) != testPA) {
            // FAIL
            throw new TestException(
                    "Returned SecurityContext's 'wrap(PrivilegedAction)' "
                    + "method returned " + sc.wrap(testPA) + " while "
                    + testPA + " was expected.");
        }

        if (sc.wrap(testPEA) != testPEA) {
            // FAIL
            throw new TestException(
                    "Returned SecurityContext's "
                    + "'wrap(PrivilegedExceptionAction)' method returned "
                    + sc.wrap(testPEA) + " while " + testPEA
                    + " was expected.");
        }

        // PASS
        logger.fine("Method returned correct SecurityContext instance.");
        logger.fine("Set policy provider to SCSPolicyProvider.");
        SCSPolicyProvider policy = new SCSPolicyProvider();
        Policy.setPolicy(policy);
        sc = Security.getContext();

        if (policy.getCallsNum() != 1) {
            // FAIL
            throw new TestException(
                    "'getContext' method of installed policy provider was "
                    + "called " + policy.getCallsNum() + " times while 1 "
                    + "call was expected.");
        }

        if (!sc.equals(policy.getContext())) {
            // FAIL
            throw new TestException(
                    "'Security.getContext()' method returned " + sc
                    + " SecurityPolicy while " + policy.getContext()
                    + " was expected.");
        }

        // PASS
        logger.fine("Method works as expected.");
        logger.fine("Set security manager to SCSSecurityManager.");
        SCSSecurityManager sm = new SCSSecurityManager();
        System.setSecurityManager(sm);
        policy.resetCallsNum();
        sc = Security.getContext();

        if (sm.getCallsNum() != 1) {
            // FAIL
            throw new TestException(
                    "'getContext' method of installed security manager was "
                    + "called " + sm.getCallsNum() + " times while 1 "
                    + "call was expected.");
        }

        if (policy.getCallsNum() != 0) {
            // FAIL
            throw new TestException(
                    "'getContext' method of installed policy provider was "
                    + "called " + policy.getCallsNum()
                    + " times while no calls were expected.");
        }

        if (!sc.equals(sm.getContext())) {
            // FAIL
            throw new TestException(
                    "'Security.getContext()' method returned " + sc
                    + " SecurityPolicy while " + sm.getContext()
                    + " was expected.");
        }

        // PASS
        logger.fine("Method works as expected.");
    }
}
