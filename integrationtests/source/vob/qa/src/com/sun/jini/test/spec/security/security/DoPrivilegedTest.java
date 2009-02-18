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
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.Permission;
import java.security.Principal;

// javax
import javax.security.auth.Subject;
import javax.security.auth.SubjectDomainCombiner;

// net.jini
import net.jini.security.Security;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.spec.security.util.FakeCaller;
import com.sun.jini.test.spec.security.util.FakePrincipal;
import com.sun.jini.test.spec.security.util.TestPrivilegedAction;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'doPrivileged(PrivilegedAction)' static method of Security class executes
 *     the specified action's run method with privileges enabled, preserving the
 *     domain combiner (if any) of the calling context and return the object
 *     returned by this method. This method throws NullPointerException if the
 *     action is null.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestPrivilegedAction - test PrivilegedAction
 *
 * Action
 *   The test performs the following steps:
 *     1) invoke 'doPrivileged' static method of Security class with null
 *        PrivilegedAction
 *     2) assert that NullPointerException will be thrown
 *     3) invoke 'doPrivileged' static method of Security class with
 *        TestPrivilegedAction as a parameter
 *     4) assert that 'run' method of TestPrivilegedAction will be invoked
 *     5) assert that domain combiner will be null
 *     6) assert that 'doPrivileged' method will return value returned by this
 *        'run' method
 *     7) assert that privileges will be enabled inside 'doPrivileged' method
 *     8) set combiner for the invoking context of 'doPrivileged' method to a
 *        non-null value (SubjectDomainCombiner)
 *     9) invoke 'doPrivileged' static method of Security class with
 *        TestPrivilegedAction as a parameter
 *     10) assert that 'run' method of TestPrivilegedAction will be invoked
 *     11) assert that domain combiner will be the same as created one
 *     12) assert that 'doPrivileged' method will return value returned by this
 *        'run' method
 *     13) assert that privileges will be enabled inside 'doPrivileged' method
 * </pre>
 */
public class DoPrivilegedTest extends QATest {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        PrivilegedAction nullPa = null;
        Permission perm = new RuntimePermission("DoPrivilegedTEST");
        Permission perm1 = new RuntimePermission("DoPrivilegedTEST1");
        Object obj;

        try {
            System.getSecurityManager().checkPermission(perm);
            throw new TestException(perm.toString()
                    + " is granted to DoPrivilegedTest.");
        } catch (SecurityException se) {
            logger.fine(perm.toString()
                    + " is not granted to DoPrivilegedTest.");
        }

        try {
            System.getSecurityManager().checkPermission(perm1);
            throw new TestException(perm1.toString()
                    + " is granted to DoPrivilegedTest.");
        } catch (SecurityException se) {
            logger.fine(perm1.toString()
                    + " is not granted to DoPrivilegedTest.");
        }

        try {
            callDoPrivileged(nullPa);

            // FAIL
            throw new TestException(
                    "Method did not throw any exception while "
                    + "NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("NullPointerException was thrown as expected.");
        }
        final TestPrivilegedAction pa = new TestPrivilegedAction(perm);
        obj = callDoPrivileged(pa);

        if (pa.getCombiner() != null) {
            // FAIL
            throw new TestException(
                    "DomainCombiner inside 'doPrivileged' method of "
                    + pa + " was " + pa.getCombiner()
                    + " while null was expected.");
        }

        // PASS
        logger.fine("DomainCombiner inside 'doPrivileged' method of "
                + pa + " was null as expected.");

        if (!pa.isGrantedPerm()) {
            // FAIL
            throw new TestException(
                    "Privileges are not enabled inside 'doPrivileged' "
                    + "method.");
        }

        // PASS
        logger.fine("Privileges are enabled inside 'doPrivileged' "
                + "method as expected.");

        if (pa.getObject() != obj) {
            // FAIL
            throw new TestException(
                    "'doPrivileged' method returned " + obj + " while "
                    + pa.getObject() + " was expected.");
        }

        // PASS
        logger.fine("'doPrivileged' method returned " + obj
                + " as expected.");
        final TestPrivilegedAction pa1 = new TestPrivilegedAction(perm1);
        Subject subj = new Subject();
        Principal prin = new FakePrincipal("DoPrivilegedTest");
        subj.getPrincipals().add(prin);
        SubjectDomainCombiner comb = new SubjectDomainCombiner(subj);
        AccessControlContext acc = new AccessControlContext(
                AccessController.getContext(), comb);
        obj = AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return callDoPrivileged(pa1);
            }
        }, acc);

        if (pa1.getCombiner() != comb) {
            // FAIL
            throw new TestException(
                    "DomainCombiner inside 'doPrivileged' method of "
                    + pa1 + " was " + pa1.getCombiner()
                    + " while " + comb + " was expected.");
        }

        // PASS
        logger.fine("DomainCombiner inside 'doPrivileged' method of "
                + pa1 + " was " + comb + " as expected.");

        if (!pa1.isGrantedPerm()) {
            // FAIL
            throw new TestException(
                    "Privileges are not enabled inside 'doPrivileged' "
                    + "method.");
        }

        // PASS
        logger.fine("Privileges are enabled inside 'doPrivileged' "
                + "method as expected.");

        if (pa1.getObject() != obj) {
            // FAIL
            throw new TestException(
                    "'doPrivileged' method returned " + obj + " while "
                    + pa1.getObject() + " was expected.");
        }

        // PASS
        logger.fine("'doPrivileged' method returned " + obj
                + " as expected.");
    }

    /**
     * Logs incoming action and call FakeCaller's method.
     *
     * @param act PrivilegedAction for 'doPrivileged' method
     * @return result of this call
     */
    protected Object callDoPrivileged(PrivilegedAction act) {
        logger.fine("Call 'Security.doPrivileged(" + act + ")'.");
        return FakeCaller.callDoPrivileged(act);
    }
}
