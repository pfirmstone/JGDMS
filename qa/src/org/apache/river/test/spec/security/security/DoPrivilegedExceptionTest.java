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
package org.apache.river.test.spec.security.security;

import java.util.logging.Level;

// java
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.Permission;
import java.security.Principal;

// javax
import javax.security.auth.Subject;
import javax.security.auth.SubjectDomainCombiner;

// net.jini
import net.jini.security.Security;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.spec.security.util.FakeCaller;
import org.apache.river.test.spec.security.util.FakePrincipal;
import org.apache.river.test.spec.security.util.TestPrivilegedExceptionAction;
import org.apache.river.test.spec.security.util.PEAThrowingCheckedEx;
import org.apache.river.test.spec.security.util.PEAThrowingUncheckedEx;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'doPrivileged(PrivilegedExceptionAction)' static method of Security class
 *     executes the specified action's run method with privileges enabled,
 *     preserving the domain combiner (if any) of the calling context and return
 *     the object returned by this method. If the action's run method throws an
 *     unchecked exception, that exception is thrown by this method.
 *     'doPrivileged' method throws PrivilegedActionException if the action's
 *     run method throws a checked exception and throws NullPointerException
 *     if the action is null.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestPrivilegedExceptionAction - test PrivilegedExceptionAction whose
 *             run method does not throw any exception
 *     PEAThrowingCheckedEx - PrivilegedExceptionAction whose
 *             run method throws checked FakeException
 *     PEAThrowingUncheckedEx - PrivilegedExceptionAction whose
 *             run method throws unchecked RuntimeException
 *     FakeException - test exception
 *
 * Action
 *   The test performs the following steps:
 *     1) invoke 'doPrivileged' static method of Security class with null
 *        PrivilegedExceptionAction
 *     2) assert that NullPointerException will be thrown
 *     3) invoke 'doPrivileged' static method of Security class with
 *        TestPrivilegedExceptionAction as a parameter
 *     4) assert that 'run' method of TestPrivilegedExceptionAction will be
 *        invoked
 *     5) assert that domain combiner will be null
 *     6) assert that 'doPrivileged' method will return value returned by this
 *        'run' method
 *     7) assert that privileges will be enabled inside 'doPrivileged' method
 *     8) set combiner for the invoking context of 'doPrivileged' method to a
 *        non-null value (SubjectDomainCombiner)
 *     9) invoke 'doPrivileged' static method of Security class with
 *        TestPrivilegedExceptionAction as a parameter
 *     10) assert that 'run' method of TestPrivilegedExceptionAction will be
 *         invoked
 *     11) assert that domain combiner will be the same as created one
 *     12) assert that 'doPrivileged' method will return value returned by this
 *        'run' method
 *     13) assert that privileges will be enabled inside 'doPrivileged' method
 *     14) invoke 'doPrivileged' static method of Security class with
 *         PEAThrowingCheckedEx as a parameter
 *     15) assert that 'run' method of PEAThrowingCheckedEx
 *         will be invoked
 *     16) assert that domain combiner will be null
 *     17) assert that 'doPrivileged' method will throw
 *         PrivilegedActionException containing FakeException thrown by 'run'
 *         method of PEAThrowingCheckedEx
 *     18) assert that privileges will be enabled inside 'doPrivileged' method
 *     19) set combiner for the invoking context of 'doPrivileged' method to a
 *         non-null value (SubjectDomainCombiner)
 *     20) invoke 'doPrivileged' static method of Security class with
 *         PEAThrowingCheckedEx as a parameter
 *     21) assert that 'run' method of PEAThrowingCheckedEx will
 *         be invoked
 *     22) assert that domain combiner will be the same as created one
 *     23) assert that 'doPrivileged' method will throw
 *         PrivilegedActionException containing FakeException thrown by 'run'
 *         method of PEAThrowingCheckedEx
 *     24) assert that privileges will be enabled inside 'doPrivileged' method
 *     25) invoke 'doPrivileged' static method of Security class with
 *         PEAThrowingUncheckedEx as a parameter
 *     26) assert that 'run' method of PEAThrowingUncheckedEx will
 *         be invoked
 *     27) assert that 'doPrivileged' method will throw
 *         unwrapped RuntimeException thrown by 'run' method of
 *         PEAThrowingUncheckedEx
 * </pre>
 */
public class DoPrivilegedExceptionTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        PrivilegedExceptionAction nullPea = null;
        Permission perm = new RuntimePermission("DoPrivilegedExceptionTEST");
        Permission perm1 = new RuntimePermission("DoPrivilegedExceptionTEST1");
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
            callDoPrivileged(nullPea);

            // FAIL
            throw new TestException(
                    "Method did not throw any exception while "
                    + "NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("NullPointerException was thrown as expected.");
        }
        final TestPrivilegedExceptionAction pea =
                new TestPrivilegedExceptionAction(perm);
        obj = callDoPrivileged(pea);

        if (pea.getCombiner() != null) {
            // FAIL
            throw new TestException(
                    "DomainCombiner inside 'doPrivileged' method of "
                    + pea + " was " + pea.getCombiner()
                    + " while null was expected.");
        }

        // PASS
        logger.fine("DomainCombiner inside 'doPrivileged' method of "
                + pea + " was null as expected.");

        if (!pea.isGrantedPerm()) {
            // FAIL
            throw new TestException(
                    "Privileges are not enabled inside 'doPrivileged' "
                    + "method.");
        }

        // PASS
        logger.fine("Privileges are enabled inside 'doPrivileged' "
                + "method as expected.");

        if (pea.getObject() != obj) {
            // FAIL
            throw new TestException(
                    "'doPrivileged' method returned " + obj + " while "
                    + pea.getObject() + " was expected.");
        }

        // PASS
        logger.fine("'doPrivileged' method returned " + obj
                + " as expected.");
        final TestPrivilegedExceptionAction pea1
                = new TestPrivilegedExceptionAction(perm1);
        Subject subj = new Subject();
        Principal prin = new FakePrincipal("DoPrivilegedExceptionTest");
        subj.getPrincipals().add(prin);
        SubjectDomainCombiner comb = new SubjectDomainCombiner(subj);
        AccessControlContext acc = new AccessControlContext(
                AccessController.getContext(), comb);
        obj = AccessController.doPrivileged(
                new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        return callDoPrivileged(pea1);
                    }
                }, acc);

        if (pea1.getCombiner() != comb) {
            // FAIL
            throw new TestException(
                    "DomainCombiner inside 'doPrivileged' method of "
                    + pea1 + " was " + pea1.getCombiner()
                    + " while " + comb + " was expected.");
        }

        // PASS
        logger.fine("DomainCombiner inside 'doPrivileged' method of "
                + pea1 + " was " + comb + " as expected.");

        if (!pea1.isGrantedPerm()) {
            // FAIL
            throw new TestException(
                    "Privileges are not enabled inside 'doPrivileged' "
                    + "method.");
        }

        // PASS
        logger.fine("Privileges are enabled inside 'doPrivileged' "
                + "method as expected.");

        if (pea1.getObject() != obj) {
            // FAIL
            throw new TestException(
                    "'doPrivileged' method returned " + obj + " while "
                    + pea1.getObject() + " was expected.");
        }

        // PASS
        logger.fine("'doPrivileged' method returned " + obj
                + " as expected.");
        final PEAThrowingCheckedEx peaEx = new PEAThrowingCheckedEx(perm);

        try {
            callDoPrivileged(peaEx);

            // FAIL
            throw new TestException(
                    "Method did not throw any exception while "
                    + "PrivilegedActionException was expected.");
        } catch (PrivilegedActionException pae) {
            if (peaEx.getException() != pae.getException()) {
                // FAIL
                throw new TestException(
                        "'doPrivileged' method threw exception containing "
                        + pae.getException() + " while "
                        + peaEx.getException() + " was expected.");
            }
        }

        // PASS
        logger.fine("PrivilegedActionException containing FakeException "
                + "was thrown as expected.");

        if (peaEx.getCombiner() != null) {
            // FAIL
            throw new TestException(
                    "DomainCombiner inside 'doPrivileged' method of "
                    + peaEx + " was " + peaEx.getCombiner()
                    + " while null was expected.");
        }

        // PASS
        logger.fine("DomainCombiner inside 'doPrivileged' method of "
                + peaEx + " was null as expected.");

        if (!peaEx.isGrantedPerm()) {
            // FAIL
            throw new TestException(
                    "Privileges are not enabled inside 'doPrivileged' "
                    + "method.");
        }

        // PASS
        logger.fine("Privileges are enabled inside 'doPrivileged' "
                + "method as expected.");

        final PEAThrowingCheckedEx peaEx1 = new PEAThrowingCheckedEx(perm1);

        try {
            try {
                AccessController.doPrivileged(
                        new PrivilegedExceptionAction() {
                            public Object run() throws Exception {
                                return callDoPrivileged(peaEx1);
                            }
                        }, acc);
            } catch (PrivilegedActionException e) {
                throw e.getException();
            }

            // FAIL
            throw new TestException(
                    "Method did not throw any exception while "
                    + "PrivilegedActionException was expected.");
        } catch (PrivilegedActionException pae) {
            if (peaEx1.getException() != pae.getException()) {
                // FAIL
                throw new TestException(
                        "'doPrivileged' method threw exception containing "
                        + pae.getException() + " while "
                        + peaEx1.getException() + " was expected.");
            }
        }

        if (peaEx1.getCombiner() != comb) {
            // FAIL
            throw new TestException(
                    "DomainCombiner inside 'doPrivileged' method of "
                    + peaEx1 + " was " + peaEx1.getCombiner()
                    + " while " + comb + " was expected.");
        }

        // PASS
        logger.fine("DomainCombiner inside 'doPrivileged' method of "
                + peaEx1 + " was " + comb + " as expected.");

        if (!peaEx1.isGrantedPerm()) {
            // FAIL
            throw new TestException(
                    "Privileges are not enabled inside 'doPrivileged' "
                    + "method.");
        }

        // PASS
        logger.fine("Privileges are enabled inside 'doPrivileged' "
                + "method as expected.");
        final PEAThrowingUncheckedEx peaEx2 =
                new PEAThrowingUncheckedEx(perm);

        try {
            callDoPrivileged(peaEx2);

            // FAIL
            throw new TestException(
                    "Method did not throw any exception while "
                    + "RuntimeException was expected.");
        } catch (RuntimeException re) {
            if (peaEx2.getException() != re) {
                // FAIL
                throw new TestException(
                        "'doPrivileged' method threw " + re + " while "
                        + peaEx2.getException() + " was expected.");
            }
        }

        // PASS
        logger.fine("RuntimeException was thrown as expected.");
    }

    /**
     * Invokes 'Security.doPrivileged(PrivilegedExceptionAction)' method with
     * given argument. Rethrows any exception thrown by this method.
     *
     * @param act PrivilegedExceptionAction for 'doPrivileged' method
     */
    protected Object callDoPrivileged(PrivilegedExceptionAction act)
            throws PrivilegedActionException {
        logger.fine("Call 'Security.doPrivileged(" + act + ")'.");
        return FakeCaller.callDoPrivileged(act);
    }
}
