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
import com.sun.jini.qa.harness.Test;
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
 *   This test verifies the behavior of the BasicProxyPreparer class
 *   getPermissions method:
 *   protected Permission[] getPermissions(Object proxy)
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeBasicProxyPreparer - accessor of the tested BasicProxyPreparer
 *        class, that is gateway to protected method access.
 *
 * Actions:
 *   Test verifies the following assertions and performs the following steps:
 *     1) Returns the permissions to grant to proxies ...  
 *        The default implementation returns the value of permissions.  
 *	steps:
 *	  construct a FakeBasicProxyPreparer object passing true for "verify",
 *	  some valid values for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call getPermissions method passing some proxy as an argument;
 *        assert the same permissions as passed to constructor
 *        is returned;
 *     2) Returns ... an empty array if no permissions should be granted..  
 *	steps:
 *	  construct a FakeBasicProxyPreparer object passing true for "verify",
 *	  null for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call getPermissions method passing some proxy as an argument;
 *        assert the empty array is returned;
 *	steps:
 *	  construct a FakeBasicProxyPreparer object passing true for "verify",
 *	  empty array for "permissions" and some non empty instance for
 *        "methodConstrains"; 
 *        call getPermissions method passing some proxy as an argument;
 *        assert the empty array is returned;
 *     3) Check invalid proxy argument.  
 *	steps:
 *	  repeat steps 1-3 passing null as proxy argument;
 * </pre>
 */
public class GetPermissions_Test extends QATestEnvironment implements Test {

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

    protected boolean equals(Permission[] a, Permission[] b) {
        loop1:
	for (int i = a.length; --i >= 0; ) {
	    Permission p = a[i];
	    for (int j = b.length; --j >= 0; ) {
		if (p.equals(b[j])) {
		    continue loop1;
		}
	    }
	    return false;
	}
        loop2:
	for (int i = b.length; --i >= 0; ) {
	    Permission p = b[i];
	    for (int j = a.length; --j >= 0; ) {
		if (p.equals(a[j])) {
		    continue loop2;
		}
	    }
	    return false;
	}
	return true;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        logger.log(Level.INFO, "======================================");

        // 1 Returns the permissions to grant to proxies
        boolean verify = true;
        Permission[] perm = new Permission[] {
                    new RuntimePermission("setClassLoader")};
        MethodConstraints mc = new FakeMethodConstraints(
                    new InvocationConstraint[] {Integrity.YES});
        FakeBasicProxyPreparer fbpp = callFakeConstructor(
                3, verify, mc, perm);
        Object proxy = new Object();
        if (!equals(fbpp.getPermissions(proxy), perm)) {
            throw new TestException(
                    "getPermissions method returns invalid value (1)");
        }

        // 2 Returns ... an empty array if no permissions should be granted
        perm = new Permission[] {};
        fbpp = callFakeConstructor(
                3, verify, mc, perm);
        if (fbpp.getPermissions(proxy).length != 0) {
            throw new TestException(
                    "getPermissions method should return empty array (2a)");
        }

        fbpp = callFakeConstructor(
                3, verify, mc, null);
        if (fbpp.getPermissions(proxy).length != 0) {
            throw new TestException(
                    "getPermissions method should return empty array (2b)");
        }

        // 3 Check invalid proxy argument
        perm = new Permission[] {
                new RuntimePermission("setClassLoader")};
        fbpp = callFakeConstructor(
                3, verify, mc, perm);
        if (!equals(fbpp.getPermissions(null), perm)) {
            throw new TestException(
                    "getPermissions method returns invalid value (3a)");
        }

        perm = new Permission[] {};
        fbpp = callFakeConstructor(
                3, verify, mc, perm);
        if (fbpp.getPermissions(null).length != 0) {
            throw new TestException(
                    "getPermissions method should return empty array (3b)");
        }

        fbpp = callFakeConstructor(
                3, verify, mc, null);
        if (fbpp.getPermissions(null).length != 0) {
            throw new TestException(
                    "getPermissions method should return empty array (3c)");
        }
    }
}
