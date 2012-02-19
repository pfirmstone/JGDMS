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
package com.sun.jini.test.spec.policyprovider.dynamicPolicyProvider;

import java.util.logging.Level;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// java.security
import java.security.Policy;
import java.security.Principal;
import java.security.Permission;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.PermissionCollection;

// java.util
import java.util.Arrays;
import java.util.Enumeration;

// davis packages
import net.jini.security.policy.DynamicPolicyProvider;
import net.jini.security.policy.PolicyInitializationException;

// test base class
import com.sun.jini.test.spec.policyprovider.AbstractTestBase;

// utility classes
import com.sun.jini.test.spec.policyprovider.util.Util;


/**
 * This class is base class for all
 * com.sun.jini.test.spec.policyprovider.dynamicPolicyProvider tests.
 * This class has some helper methods and constants.
 */
public abstract class DynamicPolicyProviderTestBase extends AbstractTestBase {

    /** DynamicPolicyProvider to be tested */
    protected DynamicPolicyProvider policy = null;

    /**
     * Try to create DynamicPolicyProvider using non-argument constructor.
     * If exception is thrown then test failed.
     *
     * @throws TestException if failed
     *
     */
    protected void createDynamicPolicyProvider() throws TestException {
        try {
            policy = new DynamicPolicyProvider();
        } catch (Exception e) {
            msg = "new DynamicPolicyProvider()";
            throw new TestException(Util.fail(msg, e, msg));
        }
    }

    /**
     * Try to create DynamicPolicyProvider using non-argument constructor.
     * Expect SecurityException. If no exception or another
     * exception is thrown then test failed.
     *
     * @throws TestException if failed
     *
     */
    protected void createDynamicPolicyProviderSE(String msg)
            throws TestException {
        try {
            DynamicPolicyProvider policy = new DynamicPolicyProvider();
            throw new TestException(Util.fail(msg, msg, SE));
        } catch (SecurityException se) {
            logger.log(Level.FINE, Util.pass(msg, se));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, SE));
        }
    }

    /**
     * Try to create DynamicPolicyProvider using non-argument constructor.
     * Expect PolicyInitializationException. If no exception or another
     * exception is thrown then test failed.
     *
     * @throws TestException if failed
     *
     */
    protected void createDynamicPolicyProviderPIE(String msg)
            throws TestException {
        try {
            DynamicPolicyProvider policy = new DynamicPolicyProvider();
            throw new TestException(Util.fail(msg, msg, PIE));
        } catch (PolicyInitializationException pie) {
            logger.log(Level.FINE, Util.pass(msg, pie));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, PIE));
        }
    }

    /**
     * Try to create DynamicPolicyProvider passing null as base policy class.
     * Expect NullPointerException. If no exception or another
     * exception is thrown then test failed.
     *
     * @throws TestException if failed
     *
     */
    protected void createDynamicPolicyProviderNPE(String msg)
            throws TestException {
        try {
            DynamicPolicyProvider policy = new DynamicPolicyProvider(null);
            throw new TestException(Util.fail(msg, msg, NPE));
        } catch (NullPointerException npe) {
            logger.log(Level.FINE, Util.pass(msg, npe));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NPE));
        }
    }

    /**
     * Call grant() on DynamicPolicyProvider.
     *
     * @param cl class to grant permissions to the class loader or null.
     * @param pa set of principals to which grants apply or null.
     * @param p  permissions to grant or null.
     * @param msg  string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGrant(Class cl, Principal[] pa, Permission[] p,
            String msg) throws TestException {
        try {
            policy.grant(cl, pa, p);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }
        logger.log(Level.FINE, Util.pass(msg, "grants permission(s)"));
    }

    /**
     * Call grant() on DynamicPolicyProvider and verify that
     * NullPointerException is thrown.
     *
     * @param cl class to grant permissions to the class loader or null.
     * @param pa set of principals to which grants apply or null.
     * @param p  permissions to grant or null.
     * @param msg  string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGrantNPE(Class cl, Principal[] pa, Permission[] p,
            String msg) throws TestException {
        try {
            policy.grant(cl, pa, p);
            throw new TestException(Util.fail(msg, NOException, NPE));
        } catch (NullPointerException npe) {
            logger.log(Level.FINE, Util.pass(msg, npe));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NPE));
        }
    }

    /**
     * Call grant() on DynamicPolicyProvider and verify that
     * NullPointerException is thrown.
     *
     * @param cl class to grant permissions to the class loader or null.
     * @param pa set of principals to which grants apply or null.
     * @param p  permissions to grant or null.
     * @param msg  string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGrantSE(Class cl, Principal[] pa, Permission[] p,
            String msg) throws TestException {
        try {
            policy.grant(cl, pa, p);
            throw new TestException(Util.fail(msg, NOException, SE));
        } catch (SecurityException se) {
            logger.log(Level.FINE, Util.pass(msg, se));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, SE));
        }
    }

    /**
     * Call getGrants() on DynamicPolicyProvider and if passing
     * Permission[] p is not null then verify that an array
     * containing p (granted earlier) is returned.
     *
     * @param cl class to query the permissions or null.
     * @param pa principals to query dynamic grants or null.
     * @param p  permissions granted earlier or null.
     * @param msg  string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGetGrants(Class cl, Principal[] pa, Permission[] p,
            String msg) throws TestException {
        // Returned permissions.
        Permission[] pReturned = null;

        try {
            pReturned = policy.getGrants(cl, pa);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }

        if (p == null) {
            logger.log(Level.FINE, Util.pass(msg, "passed"));
            return;
        }

        if (pReturned == null) {
            throw new TestException(Util.fail(msg, SNULL, "Permission[]"));
        }

        for (int i = 0; i < p.length; i++) {
            boolean pass = false;

            for (int j = 0; j < pReturned.length; j++) {
                if (p[i].equals(pReturned[j])) {
                    pass = true;
                    break;
                }
            }

            if (!pass) {
                String prm = p[i].toString();
                String ret = "Permission[] does not contain " + prm;
                String exp = "Permission[] contains " + prm;
                throw new TestException(Util.fail(msg, ret, exp));
            }
        }
        logger.log(Level.FINE, Util.pass(msg, "returned permission(s)"));
    }

    /**
     * Call getGrants() on DynamicPolicyProvider and if passing
     * Permission[] p is not null then verify that returned array
     * does not contain any permissions from Permission[] p.
     *
     * @param cl class to query the permissions or null.
     * @param pa principals to query dynamic grants or null.
     * @param p  permissions do not granted earlier or null.
     * @param msg  string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGetGrantsNegative(Class cl, Principal[] pa,
            Permission[] p, String msg) throws TestException {
        // Returned permissions.
        Permission[] pReturned = null;

        try {
            pReturned = policy.getGrants(cl, pa);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }

        if (p == null) {
            logger.log(Level.FINE, Util.pass(msg, "passed"));
            return;
        }

        if (pReturned == null) {
            throw new TestException(Util.fail(msg, SNULL, "Permission[]"));
        }

        for (int i = 0; i < p.length; i++) {
            for (int j = 0; j < pReturned.length; j++) {
                if (p[i].equals(pReturned[j])) {
                    String prm = p[i].toString();
                    String ret = "Permission[] contains " + prm;
                    String exp = "Permission[] does not contain " + prm;
                    throw new TestException(Util.fail(msg, ret, exp));
                }
            }
        }
        String parray = java.util.Arrays.asList(p).toString();
        logger.log(Level.FINE, Util.pass(msg,
                "returned permission(s) does not contain " + parray));
    }

    /**
     * Call getGrants() on DynamicPolicyProvider and verify that
     * NullPointerException is thrown.
     *
     * @param cl class to grant permissions to the class loader or null.
     * @param pa set of principals to which grants apply or null.
     * @param msg  string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGetGrantsNPE(Class cl, Principal[] pa, String msg)
            throws TestException {
        try {
            policy.getGrants(cl, pa);
            throw new TestException(Util.fail(msg, NOException, NPE));
        } catch (NullPointerException npe) {
            logger.log(Level.FINE, Util.pass(msg, npe));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NPE));
        }
    }

    /**
     * Call getPermissions() on DynamicPolicyProvider and if passing
     * Permission[] p is not null then verify that an PermissionCollection
     * containing p (granted earlier) is returned.
     *
     * @param pd the ProtectionDomain or null.
     * @param p  permissions granted earlier or null.
     * @param dynamicallyGranted   This indicates that these permissions 
     * have been dynamically granted.
     * If the policy being tested supports revoking 
     * permissions, dynamically granted permissions passed in must not be present, as this
     * will remove the ability to revoke the permissions as they will become
     * merged into the PermissionDomain's cached PermissionCollection.
     * @param msg  string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGetPermissions(ProtectionDomain pd, Permission[] p,
                                            String msg) throws TestException {
        // Returned permissions.
        PermissionCollection pReturned = null;

        try {
            pReturned = policy.getPermissions(pd);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }

        if (p == null) {
            logger.log(Level.FINE, Util.pass(msg, "passed"));
            return;
        }

        if (pReturned == null) {
            throw new TestException(Util.fail(msg, SNULL,
                    "PermissionCollection"));
        }
        
        for (int i = 0; i < p.length; i++) {
            if (!pReturned.implies(p[i])) {
                String prm = p[i].toString();
                String ret = "PermissionCollection does not contain " + prm;
                String exp = "PermissionCollection contains " + prm;
                throw new TestException(Util.fail(msg, ret, exp));
            }
        }
        logger.log(Level.FINE, Util.pass(msg, "returned permission(s)"));
    }

    /**
     * Call getPermissions() on DynamicPolicyProvider and if passing
     * Permission[] p is not null then verify that an PermissionCollection
     * containing p (granted earlier) is returned.
     *
     * @param cs the CodeSource or null.
     * @param p  permissions granted earlier or null.
     * @param msg  string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGetPermissions(CodeSource cs, Permission[] p,
            boolean dynamicallyGranted, String msg) throws TestException {
        // Returned permissions.
        PermissionCollection pReturned = null;

        if (msg == null) {
            msg = "policy.getPermissions(cs)";
        }

        try {
            pReturned = policy.getPermissions(cs);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }

        if (p == null) {
            logger.log(Level.FINE, Util.pass(msg, "passed"));
            return;
        }

        if (pReturned == null) {
            throw new TestException(Util.fail(msg, SNULL,
                    "PermissionCollection"));
        }
        if ( dynamicallyGranted && policy.revokeSupported()){
            for (int i = 0; i < p.length; i++) {
                if (pReturned.implies(p[i])) {
                    String prm = p[i].toString();
                    String exp = "PermissionCollection does not contain " + prm;
                    String ret = "PermissionCollection contains " + prm;
                    throw new TestException(Util.fail(msg, ret, exp));
                }
            }
            logger.log(Level.FINE, Util.pass(msg, "permission(s) not present"));
            return;
        }
        for (int i = 0; i < p.length; i++) {
            if (!pReturned.implies(p[i])) {
                String prm = p[i].toString();
                String ret = "PermissionCollection does not contain " + prm;
                String exp = "PermissionCollection contains " + prm;
                throw new TestException(Util.fail(msg, ret, exp));
            }
        }
        logger.log(Level.FINE, Util.pass(msg, "returned permission(s)"));
    }

    /**
     * Call getPermissions() on DynamicPolicyProvider and if passing
     * Permission[] p is not null then verify that returned
     * PermissionCollection does not contain any permission from p.
     *
     * @param cs the CodeSource or null.
     * @param p  permissions.
     *
     * @throws TestException if failed
     *
     */
    protected void callGetPermissionsNoGranted(CodeSource cs, Permission[] p)
            throws TestException {
        // Returned permissions.
        PermissionCollection pReturned = null;
        msg = "policy.getPermissions(cs)";

        try {
            pReturned = policy.getPermissions(cs);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }

        if (p == null) {
            logger.log(Level.FINE, Util.pass(msg, "passed"));
            return;
        }

        if (pReturned == null) {
            throw new TestException(Util.fail(msg, SNULL,
                    "PermissionCollection"));
        }

        for (int i = 0; i < p.length; i++) {
            if (pReturned.implies(p[i])) {
                String prm = p[i].toString();
                String ret = "PermissionCollection contains " + prm;
                String exp = "PermissionCollection does not contain " + prm;
                throw new TestException(Util.fail(msg, ret, exp));
            }
        }
        logger.log(Level.FINE, Util.pass(msg, "returned permission(s)"));
    }

    /**
     * Call getPermissions() on DynamicPolicyProvider and verify that
     * NullPointerException is thrown.
     *
     * @param cs the CodeSource or null.
     * @param msg string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callGetPermissionsNPE(CodeSource cs, String msg)
            throws TestException {
        try {
            policy.getPermissions(cs);
            throw new TestException(Util.fail(msg, NOException, NPE));
        } catch (NullPointerException npe) {
            logger.log(Level.FINE, Util.pass(msg, npe));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NPE));
        }
    }

    /**
     * Call implies() on DynamicPolicyProvider and verify that
     * NullPointerException is thrown.
     *
     * @param pd the ProtectionDomain or null.
     * @param pm permission granted earlier or null.
     * @param msg string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callImpliesNPE(ProtectionDomain pd, Permission pm,
            String msg) throws TestException {
        try {
            policy.implies(pd, pm);
            throw new TestException(Util.fail(msg, NOException, NPE));
        } catch (NullPointerException npe) {
            logger.log(Level.FINE, Util.pass(msg, npe));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NPE));
        }
    }

    /**
     * Call implies() on DynamicPolicyProvider. Verify that
     * NullPointerException is not thrown. Verify that returned result
     * is equal to expected result.
     *
     * @param pd the ProtectionDomain or null.
     * @param pm permission granted earlier or null.
     * @param exp expected result.
     * @param msg string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callImplies(ProtectionDomain pd, Permission pm, boolean exp,
            String msg) throws TestException {
        boolean ret = false;

        try {
            ret = policy.implies(pd, pm);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }

        if (exp != ret) {
            throw new TestException(Util.fail(msg, "" + ret, "" + exp));
        }
        logger.log(Level.FINE, Util.pass(msg, "returned " + ret));
    }

    /**
     * Call implies() on ProtectionDomain passing Permission[].
     *
     * @param pd the ProtectionDomain or null.
     * @param pma permissions granted earlier or null.
     * @param expected expected result.
     * @param ifNull then expected result should be false for null
     *        ProtectionDomain.
     *
     * @throws TestException if failed
     *
     */
    protected void checkImplies(ProtectionDomain pd, Permission[] pma,
            boolean expected, boolean ifNull) throws TestException {
        for (int j = 0; j < pma.length; j++) {
            if (ifNull && pd == null) {
                expected = false;
            }
            msg = "policy.implies(" + str(pd) + ", " + pma[j].getName() + ")";
            callImplies(pd, pma[j], expected, msg);
        }
    }

    /**
     * Iterates over array of classes and call grant() on DynamicPolicyProvider
     * passing null as Principal[] and various arrays of Permission[].
     * Arrays of Permission[] will be created as sequential arrays from
     * first element of  Permission[] pma parameter to last element of
     * Permission[] pma parameter.
     * Verify that SecurityException is thrown
     * if expectSecurityException is true.
     *
     * @param classes array of classes.
     * @param pma permissions to be granted.
     * @param expectSecurityException if true verify that SecurityException
     *        is thrown.
     *
     * @throws TestException if failed
     *
     */
    protected void checkGrant(Class[] classes, Permission[] pma,
            boolean expectSecurityException) throws TestException {
        checkGrant(classes, null, pma, expectSecurityException);
    }

    /**
     * Iterates over array of classes and call grant() on DynamicPolicyProvider
     * passing array of Principals and various arrays of Permissions.
     * Arrays of Permissions will be created as sequential arrays from
     * first element of  Permission[] pma parameter to last element of
     * Permission[] pma parameter.
     * Verify that SecurityException is thrown
     * if expectSecurityException is true.
     *
     * @param classes array of classes.
     * @param pa principals to be granted.
     * @param pma permissions to be granted.
     * @param expectSecurityException if true verify that SecurityException
     *        is thrown.
     *
     * @throws TestException if failed
     *
     */
    protected void checkGrant(Class[] classes, Principal[] pa, Permission[] pma,
            boolean expectSecurityException) throws TestException {
        for (int i = 0; i < classes.length; i++) {
            Class cl = classes[i];
            String cStr = (cl != null) ? cl.getName() : SNULL;
            String pStr = (pa != null) ? Arrays.asList(pa).toString() : SNULL;
            msg = "policy.grant(" + cStr + ", " + pStr + ", " + "Permissions:";

            for (int j = 0; j < pma.length; j++) {
                Permission[] p = new Permission[j + 1];

                for (int k = 0; k <= j; k++) {
                    p[k] = pma[k];
                }
                String str = msg + java.util.Arrays.asList(p).toString() + ")";

                if (expectSecurityException) {
                    callGrantSE(cl, pa, p, str);
                } else {
                    callGrant(cl, pa, p, str);
                }
            }
        }
    }

    /**
     * Iterates over array of classes and call getGrants() on
     * DynamicPolicyProvider passing Principal[] pra array.
     * Check that returned array contains the same permissions as
     * Permission[] pma parameter.
     *
     * @param classes array of classes.
     * @param pra principals to query dynamic grants or null.
     * @param pma permissions to be checked or null.
     *
     * @throws TestException if failed
     *
     */
    protected void checkGetGrants(Class[] classes, Principal[] pra,
            Permission[] pma) throws TestException {
        for (int i = 0; i < classes.length; i++) {
            Class cl = classes[i];
            String className = (cl != null) ? cl.getName() : SNULL;
            msg = "policy.getGrants(" + className + ", ";

            if (pra != null) {
                msg += java.util.Arrays.asList(pra).toString() + ")";
            } else {
                msg += SNULL + ")";
            }
            Permission[] p = policy.getGrants(cl, pra);

            if (p == null) {
                throw new TestException(Util.fail(msg, SNULL, "non" + SNULL));
            }
            String ret = java.util.Arrays.asList(p).toString();
            String exp = java.util.Arrays.asList(pma).toString();

            if (p.length != pma.length) {
                throw new TestException(Util.fail(msg, ret, exp));
            }

            for (int j = 0; j < pma.length; j++) {
                boolean pass = false;

                for (int k = 0; k < p.length; k++) {
                    if (pma[j].equals(p[k])) {
                        pass = true;
                        break;
                    }
                }

                if (!pass) {
                    throw new TestException(Util.fail(msg, ret, exp));
                }
            }
            logger.log(Level.FINE, Util.pass(msg, "returned " + ret));
        }
    }
}
