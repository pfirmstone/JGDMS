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
import java.util.PropertyPermission;
import java.io.FilePermission;
import java.security.SecurityPermission;
import java.security.Policy;
import java.security.Permission;
import java.security.Principal;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;

// net.jini
import net.jini.security.Security;
import net.jini.security.GrantPermission;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.spec.security.util.BasePolicyProvider;
import com.sun.jini.test.spec.security.util.TestDynamicPolicyProvider;
import com.sun.jini.test.spec.security.util.FakePrincipal;
import com.sun.jini.test.spec.security.util.Util;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'grant(Class fromClass, Class toClass)' static method of Security class
 *     does the following: if the installed security policy provider implements
 *     the DynamicPolicy interface, takes the set of permissions dynamically
 *     granted to the class loader of fromClass with the current subject's
 *     principals, determines which of those permissions the calling context is
 *     authorized to grant, and dynamically grants that subset of the
 *     permissions to the class loader of toClass, qualified with the current
 *     subject's principals. The current subject is determined by calling
 *     Subject.getSubject method on the context returned by
 *     AccessController.getContext method; the permissions dynamically granted
 *     to fromClass are determined by calling the getGrants method of the
 *     currently installed policy, and the permission grant to toClass is
 *     performed by invoking the grant method of the current policy. It throws
 *     UnsupportedOperationException if currently installed policy does not
 *     support dynamic permission grants, throws NullPointerException if
 *     fromClass or toClass is null.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     NonDynamicPolicyProvider - policy provider which does not implement
 *             DynamicPolicy interface
 *     TestDynamicPolicyProvider - policy provider implementing DynamicPolicy
 *             interface having constructor with 1 parameter: Permission[],
 *             which is returned by 'getGrants' method
 *
 * Action
 *   The test performs the following steps:
 *     1) invoke 'grant(Class fromClass, Class toClass)' static method of
 *        Security class with null fromClass and non-null toClass
 *     2) assert that NullPointerException will be thrown
 *     3) invoke 'grant(Class fromClass, Class toClass)' static method of
 *        Security class with non-null fromClass and null toClass
 *     4) assert that NullPointerException will be thrown
 *     5) invoke 'grant(Class fromClass, Class toClass)' static method of
 *        Security class with null fromClass and null toClass
 *     6) assert that NullPointerException will be thrown
 *     7) set current policy provider to NonDynamicPolicyProvider
 *     8) invoke 'grant(Class fromClass, Class toClass)' static method of
 *        Security class with non-null fromClass and non-null toClass
 *     9) assert that UnsupportedOperationException will be thrown
 *     10) construct TestDynamicPolicyProvider1 with array of Permissions,
 *         containing only permissions the calling context is
 *         authorized to grant
 *     11) set current policy provider to TestDynamicPolicyProvider1
 *     12) invoke 'grant(Class fromClass, Class toClass)' static method of
 *         Security class with non-null fromClass and non-null toClass
 *     13) assert that 'getGrants' method of TestDynamicPolicyProvider1 will
 *         be invoked with fromClass, and Principal[] parameter
 *         containing the same set of principals as Subject.getSubject method
 *         on the context returned by AccessController.getContext method
 *     14) assert that 'grant(Class, Principal[], Permission[])' method of
 *         TestDynamicPolicyProvider1 will be invoked with class=toClass,
 *         Principal[] parameter containing the same set of principals as
 *         Subject.getSubject method on the context returned by
 *         AccessController.getContext method and the same array of Permissions
 *         which was returned by 'getGrants' method call
 *     15) construct TestDynamicPolicyProvider2 with array of Permissions,
 *         containing permissions both permissions the calling context is
 *         authorized to grant and ones which are not granted
 *     16) set current policy provider to TestDynamicPolicyProvider2
 *     17) invoke 'grant(Class fromClass, Class toClass)' static method of
 *         Security class with non-null fromClass and non-null toClass
 *     18) assert that 'getGrants' method of TestDynamicPolicyProvider2 will
 *         be invoked with fromClass, and Principal[] parameter
 *         containing the same set of principals as Subject.getSubject method
 *         on the context returned by AccessController.getContext method
 *     19) assert that 'grant(Class, Principal[], Permission[])' method of
 *         TestDynamicPolicyProvider2 will be invoked with class=toClass,
 *         Principal[] parameter containing the permissions array containing
 *         only those permissions which current context is authorized to grant
 * </pre>
 */
public class GrantTest3 extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        Class[] fromCls = new Class[] { null, Class.class, null };
        Class[] toCls = new Class[] { Class.class, null, null };

        for (int i = 0; i < fromCls.length; ++i) {
            try {
                grant(fromCls[i], toCls[i]);

                // FAIL
                throw new TestException(
                        "Method did not throw any exception while "
                        + "NullPointerException was expected.");
            } catch (NullPointerException npe) {
                // PASS
                logger.fine("Method threw NullPointerException "
                        + "as expected.");
            }
        }
        Policy.setPolicy(new BasePolicyProvider());
        Class fromClass = Class.class;
        Class toClass = Object.class;

        try {
            grant(fromClass, toClass);

            // FAIL
            throw new TestException(
                    "Method did not throw any exception while "
                    + "UnsupportedOperationException was expected.");
        } catch (UnsupportedOperationException uoe) {
            // PASS
            logger.fine("Method threw UnsupportedOperationException "
                    + "as expected.");
        }
        Permission[] testPerm = new Permission[] {
            new GrantPermission(new SecurityPermission("getPolicy")),
            new GrantPermission(new PropertyPermission("*", "read")),
            new GrantPermission(new FilePermission("*", "read")),
            new GrantPermission(new RuntimePermission("getClassLoader")) };
        Principal testPrin = new FakePrincipal();
        Subject subj = new Subject();
        subj.getPrincipals().add(testPrin);
        TestDynamicPolicyProvider policy =
                new TestDynamicPolicyProvider(testPerm);
        logger.fine("Policy provider is " + policy);
        Policy.setPolicy(policy);
        Subject.doAs(subj, new FakePrivilegedAction(fromClass, toClass));
        Object[] params = policy.getGetGrantsParams();

        if (params == null || params.length == 0) {
            // FAIL
            throw new TestException(
                    "'getGrants' method of " + policy + " was not called.");
        }

        if (!params[0].equals(fromClass)) {
            // FAIL
            throw new TestException(
                    "1-st parameter to 'getGrants' was " + params[0]
                    + " while " + fromClass + " was expected.");
        }

        if (params[1] == null || ((Object []) params[1]).length != 1
                || !((Object []) params[1])[0].equals(testPrin)) {
            // FAIL
            throw new TestException(
                    "2-nd parameter to 'getGrants' was "
                    + Util.arrayToString((Object []) params[1])
                    + " while " + testPrin + " was expected.");
        }

        // PASS
        logger.fine("'getGrants' method of installed policy provider "
                + " was called with correct parameters.");
        params = policy.getGrantParams();

        if (params == null || params.length == 0) {
            // FAIL
            throw new TestException(
                    "'grant' method of " + policy + " was not called.");
        }

        if (!params[0].equals(toClass)) {
           // FAIL
           throw new TestException(
                   "1-st parameter was " + params[0]
                   + " while " + toClass + " was expected.");
        }

        if (params[1] == null || ((Object []) params[1]).length != 1
                || !((Object []) params[1])[0].equals(testPrin)) {
            // FAIL
            throw new TestException(
                    "2-nd parameter was "
                    + Util.arrayToString((Object []) params[1])
                    + " while " + testPrin + " was expected.");
        }

        if (!Util.comparePermissions((Object []) params[2], testPerm)) {
          // FAIL
            throw new TestException(
                    "3-rd parameter was "
                    + Util.arrayToString((Object []) params[2])
                    + " while " + Util.arrayToString(testPerm)
                    + " was expected.");
        }

        // PASS
        logger.fine("'grant' method of installed policy provider "
                + " was called with correct parameters.");

        // not granted list of permissions
        Permission[] notGrantedPerm = new Permission[] {
            new GrantPermission(new PropertyPermission("*", "read")),
            new GrantPermission(new RuntimePermission("getClassLoader")) };
        policy = new TestDynamicPolicyProvider(testPerm, notGrantedPerm);
        logger.fine("Policy provider is " + policy);
        Policy.setPolicy(policy);
        Subject.doAs(subj, new FakePrivilegedAction(fromClass, toClass));
        params = policy.getGetGrantsParams();

        if (params == null || params.length == 0) {
            // FAIL
            throw new TestException(
                    "'getGrants' method of " + policy + " was not called.");
        }

        if (!params[0].equals(fromClass)) {
            // FAIL
            throw new TestException(
                    "1-st parameter to 'getGrants' was " + params[0]
                    + " while " + fromClass + " was expected.");
        }

        if (params[1] == null || ((Object []) params[1]).length != 1
                || !((Object []) params[1])[0].equals(testPrin)) {
            // FAIL
            throw new TestException(
                    "2-nd parameter to 'getGrants' was "
                    + Util.arrayToString((Object []) params[1])
                    + " while " + testPrin + " was expected.");
        }

        // PASS
        logger.fine("'getGrants' method of installed policy provider "
                + " was called with correct parameters.");
        params = policy.getGrantParams();

        if (params == null || params.length == 0) {
            // FAIL
            throw new TestException(
                    "'grant' method of " + policy + " was not called.");
        }

        if (!params[0].equals(toClass)) {
           // FAIL
           throw new TestException(
                   "1-st parameter was " + params[0]
                    + " while " + toClass + " was expected.");
        }

        if (params[1] == null || ((Object []) params[1]).length != 1
                || !((Object []) params[1])[0].equals(testPrin)) {
            // FAIL
            throw new TestException(
                    "2-nd parameter was "
                    + Util.arrayToString((Object []) params[1])
                    + " while " + testPrin + " was expected.");
        }

        if (!Util.comparePermissions((Object []) params[2],
                Util.excludeValues(testPerm, notGrantedPerm))) {
            // FAIL
            throw new TestException(
                    "3-rd parameter was "
                    + Util.arrayToString((Object []) params[2])
                    + " while " + Util.arrayToString(testPerm)
                    + " was expected.");
        }

        // PASS
        logger.fine("'grant' method of installed policy provider "
                + " was called with correct parameters.");
    }

    /**
     * Invokes 'Security.grant(Class, Class)' method with given arguments.
     * Rethrows any exception thrown by 'grant' method.
     *
     * @param fromClass 1-st class to 'grant' method
     * @param toClass 2-nd class to 'grant' method
     */
    protected void grant(Class fromClass, Class toClass) {
        logger.fine("Call 'Security.grant(" + fromClass + ", " + toClass
                + ")'.");
        Security.grant(fromClass, toClass);
    }


    /**
     * PrivilegedExceptionAction implementation whose purpose just call
     * 'Security.grant' method with parameters specified in constructor.
     */
    class FakePrivilegedAction implements PrivilegedAction {

        /** Stored fromClass */
        private Class fromClass;

        /** Stored toClass */
        private Class toClass;

        /**
         * Constructor whose parameters will be used in 'Security.grant' call.
         */
        public FakePrivilegedAction(Class fromClass, Class toClass) {
            this.fromClass = fromClass;
            this.toClass = toClass;
        }

        /**
         * Call 'Security.grant' with stored parameters.
         *
         * @return null in case of success
         */
        public Object run() {
            Security.grant(fromClass, toClass);
            return null;
        }
    }
}
