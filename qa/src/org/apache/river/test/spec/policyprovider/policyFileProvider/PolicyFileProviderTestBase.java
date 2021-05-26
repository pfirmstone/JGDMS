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
package org.apache.river.test.spec.policyprovider.policyFileProvider;

import java.util.logging.Level;

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

// java.io
import java.io.File;

// java.util
import java.util.Arrays;
import java.util.Enumeration;

// org.apache.river
import org.apache.river.qa.harness.TestException;

// davis packages
import net.jini.loader.pref.PreferredClassLoader;
import net.jini.security.policy.PolicyFileProvider;
import net.jini.security.policy.PolicyInitializationException;

// test base class
import org.apache.river.test.spec.policyprovider.AbstractTestBase;

// utility classes
import org.apache.river.test.spec.policyprovider.util.Util;


/**
 * This class is base class for all
 * org.apache.river.test.spec.policyprovider.policyFileProvider tests.
 * This class has some helper methods and constants.
 */
public abstract class PolicyFileProviderTestBase extends AbstractTestBase {

    /** Indexes for array of array of permissions */
    protected static final int IALL = 4;
    protected static final int IGRANTED = 0;
    protected static final int INOTGRANTED = 1;
    protected static final int ICODEBASEGRANTED = 2;
    protected static final int ICODEBASENOTGRANTED = 3;

    /** PolicyFileProvider to be tested */
    protected PolicyFileProvider policy = null;

    /**
     * Try to create PolicyFileProvider using non-argument constructor.
     * If exception is thrown then test failed.
     *
     * @throws TestException if failed
     *
     */
    protected void createPolicyFileProvider() throws TestException {
        try {
            policy = new PolicyFileProvider();
        } catch (Exception e) {
            msg = "new PolicyFileProvider()";
            throw new TestException(Util.fail(msg, e, msg));
        }
    }

    /**
     * Try to create PolicyFileProvider passing path to policy file.
     * If exception is thrown then test failed.
     *
     * @throws TestException if failed
     *
     */
    protected void createPolicyFileProvider(String policyFile)
            throws TestException {
//        String qahome = config.getStringConfigVal(QAHOMEPROPERTY, null);

//        if (qahome == null) {
//            throw new TestException("Cannot get property:" + QAHOMEPROPERTY);
//        }
//        String dirPolicy = qahome + File.separator + "policy" + File.separator;
//        String newPolicy = dirPolicy + policyFile;
	String newPolicy = config.getStringConfigVal(policyFile, null);
	if (newPolicy == null) {
	    throw new TestException("No policy found for name " + policyFile);
        }
        msg = "new PolicyFileProvider(" + newPolicy + ")";

        try {
            policy = new PolicyFileProvider(newPolicy);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, msg));
        }
        logger.log(Level.FINE, msg + " created");
    }

    /**
     * Try to create PolicyFileProvider using non-argument constructor.
     * Expect SecurityException. If no exception or another
     * exception is thrown then test failed.
     *
     * @param msg string to format log/fail message.
     *
     * @throws TestException if failed
     *
     */
    protected void createPolicyFileProviderSE(String msg)
            throws TestException {
        try {
            PolicyFileProvider policy = new PolicyFileProvider();
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
     * Try to create PolicyFileProvider passing location of the policy file.
     * Expect SecurityException. If no exception or another
     * exception is thrown then test failed.
     *
     * @param policyFile location of the policy file.
     * @param msg string to format log/fail message.
     *
     * @throws TestException if failed
     *
     */
    protected void createPolicyFileProviderSE(String policyFile, String msg)
            throws TestException {
        try {
            PolicyFileProvider policy = new PolicyFileProvider(policyFile);
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
     * Try to create PolicyFileProvider using non-argument constructor.
     * Expect PolicyInitializationException. If no exception or another
     * exception is thrown then test failed.
     *
     * @param msg string to format log/fail message.
     *
     * @throws TestException if failed
     *
     */
    protected void createPolicyFileProviderPIE(String msg)
            throws TestException {
        try {
            PolicyFileProvider policy = new PolicyFileProvider();
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
     * Try to create PolicyFileProvider passing location of the policy file.
     * Expect PolicyInitializationException. If no exception or another
     * exception is thrown then test failed.
     *
     * @param policyFile location of the policy file.
     * @param msg string to format log/fail message.
     *
     * @throws TestException if failed
     *
     */
    protected void createPolicyFileProviderPIE(String policyFile, String msg)
            throws TestException {
        try {
            PolicyFileProvider policy = new PolicyFileProvider(policyFile);
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
     * Try to create PolicyFileProvider passing null as path to policy pile.
     * Expect NullPointerException. If no exception or another
     * exception is thrown then test failed.
     *
     * @throws TestException if failed
     *
     */
    protected void createPolicyFileProviderNPE(String msg)
            throws TestException {
        try {
            PolicyFileProvider policy = new PolicyFileProvider(null);
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
     * Call getPermissions() on PolicyFileProvider passing null
     * as ProtectionDomain and verify that NullPointerException is not thrown;
     * also verify that returned collection does not contain
     * any permission.
     *
     * @throws TestException if failed
     *
     */
    protected void callGetPermissionsNPD() throws TestException {
        // Returned permissions.
        PermissionCollection pReturned = null;
        msg = "policy.getPermissions((ProtectionDomain) null)";

        try {
            pReturned = policy.getPermissions((ProtectionDomain) null);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }

        if (pReturned == null) {
            throw new TestException(Util.fail(msg, SNULL, "not a " + SNULL));
        }

        if (pReturned.elements().hasMoreElements()) {
            String ret = "Collection that contains an element(s)";
            String exp = "Collection that that does not contain any element";
            throw new TestException(Util.fail(msg, ret, exp));
        }
        logger.log(Level.FINE, Util.pass(msg, "passed"));
    }

    /**
     * Call getPermissions() on PolicyFileProvider and verify that
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
     * Call implies() on PolicyFileProvider and verify that
     * NullPointerException is thrown.
     *
     * @param pd the ProtectionDomain or null.
     * @param pm permission or null.
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
     * Call implies() on PolicyFileProvider. Verify that
     * Exception is not thrown. Verify that returned result
     * is equal to expected result.
     *
     * @param pd the ProtectionDomain or null.
     * @param pm permission or null.
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
     * Call implies() on PermissionCollection. Verify that
     * Exception is not thrown. Verify that returned result
     * is equal to expected result.
     *
     * @param pc the PermissionCollection.
     * @param pm permission.
     * @param exp expected result.
     * @param msg string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void callImplies(PermissionCollection pc, Permission pm,
            boolean exp, String msg) throws TestException {
        boolean ret = false;

        try {
            ret = pc.implies(pm);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NOException));
        }

        if (exp != ret) {
            throw new TestException(Util.fail(msg, "" + ret, "" + exp));
        }
        logger.log(Level.FINE, Util.pass(msg, "returned " + ret));
    }

    /**
     * Call implies() on PolicyFileProvider passing Permission[].
     *
     * @param pd the ProtectionDomain or null.
     * @param pma permissions to check.
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
     * Call implies() on PermissionCollection passing Permission[].
     *
     * @param pc the PermissionCollection.
     * @param pma permissions.
     * @param expected expected result.
     *
     * @throws TestException if failed
     *
     */
    protected void checkImplies(PermissionCollection pc, Permission[] pma,
            boolean expected) throws TestException {
        for (int j = 0; j < pma.length; j++) {
            msg = "pc.implies(" + pma[j].getName() + ")";
            callImplies(pc, pma[j], expected, msg);
        }
    }

    /**
     * Verify that permissions are or are not included in the Enumeration
     * returned from PermissionCollection.elements().
     *
     * @param pc the PermissionCollection.
     * @param pma permissions to verify.
     * @param expected if true then permissions should be included in the
     *        Enumeration, otherwise should not.
     *
     * @throws TestException if failed
     *
     */
    protected void checkElements(PermissionCollection pc, Permission[] pma,
            boolean expected) throws TestException {
        String msgFail = "PermissionCollection does not contain ";
        String msgPass = "PermissionCollection contains ";

        try {
            for (int j = 0; j < pma.length; j++) {
                Enumeration e = pc.elements();
                boolean returned = false;

                while (e.hasMoreElements()) {
                    Permission p = (Permission) e.nextElement();

                    if (p.equals(pma[j])) {
                        returned = true;
                    }
                }

                if (returned != expected) {
                    throw new TestException(msgFail + pma[j].getName());
                }
                logger.log(Level.FINE, Util.pass(msgPass, pma[j].getName()));
            }
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(e.getMessage());
        }
    }

    /**
     * <pre>
     *
     * Iterate over ProtectionDomains and and check implies().
     * 1. Call implies() on PolicyFileProvider passing
     *    permissions that granted in the policy file. Verify that
     *    implies() returns false if ProtectionDomain is equal to null,
     *    and verify that implies() returns true for non-null
     *    ProtectionDomains.
     * 2  Call implies() on PolicyFileProvider passing
     *    not granted permissions. Verify that implies()
     *    returns false for null and non-null
     *    ProtectionDomains.
     * 3. For non-null ProtectionDomains that have
     *    PreferredClassLoader as ClassLoader
     *    call implies() on PolicyFileProvider passing
     *    permissions that granted to
     *    qa1-policy-provider.jar's codebase.
     *    Verify that implies() returns true.
     * 4. For non-null ProtectionDomains that have
     *    PreferredClassLoader as ClassLoader
     *    call implies() on PolicyFileProvider passing
     *    permissions that are not granted to
     *    qa1-policy-provider.jar's codebase.
     *    Verify that implies() returns false.
     *
     * </pre>
     *
     * @param pma array of array of permissions.
     *
     * @throws TestException if failed
     */
    protected void checkImpliesProtectionDomain(Permission[][] pma)
            throws TestException {

        /*
         * Iterate over ProtectionDomains and and check implies().
         */
        for (int i = 0; i < protectionDomains.length; i++) {
            ProtectionDomain pd = protectionDomains[i];

            /*
             * Call implies() on PolicyFileProvider passing
             * permissions that granted in the policy file. Verify that
             * implies() returns false if ProtectionDomain is equal to null,
             * and verify that implies() returns true for non-null
             * ProtectionDomains.
             */
            checkImplies(pd, pma[IGRANTED], true, true);

            /*
             * Call implies() on PolicyFileProvider passing
             * not granted permissions. Verify that implies()
             * returns false for null and non-null
             * ProtectionDomains.
             */
            checkImplies(pd, pma[INOTGRANTED], false, false);

            /*
             * For non-null ProtectionDomains that have
             * PreferredClassLoader as ClassLoader
             * call implies() on PolicyFileProvider passing
             * permissions that granted to
             * qa1-policy-provider.jar's codebase.
             * Verify that implies() returns true.
             */
            if (pd == null) {
                continue;
            }

            if (pd.getClassLoader() instanceof PreferredClassLoader) {
                checkImplies(pd, pma[ICODEBASEGRANTED], true, false);
            }

            if (pma[ICODEBASENOTGRANTED] == null) {
                continue;
            }

            /*
             * For non-null ProtectionDomains that have
             * PreferredClassLoader as ClassLoader
             * call implies() on PolicyFileProvider passing
             * permissions that are not granted to
             * qa1-policy-provider.jar's codebase.
             * Verify that implies() returns false.
             */
            if (pd.getClassLoader() instanceof PreferredClassLoader) {
                checkImplies(pd, pma[ICODEBASENOTGRANTED], false, false);
            }
        }
    }

    /**
     * <pre>
     *
     * Iterate over ProtectionDomains and and check implies().
     * 1. Get CodeSource for ProtectionDomain.
     * 2. Call getPermissions() on PolicyFileProvider passing
     *    ProtectionDomain.
     * 3. Call getPermissions() on PolicyFileProvider passing
     *    CodeSource. - No longer tested as the sun policy
     *    provider is no longer accessible and ConcurrentPolicyFile
     *    returns empty Permissions, for all but privileged CodeSources
     *    as a scalability optimisation.
     * 4. Call implies() on returned PermissionCollections passing
     *    permissions that granted in the policy file. Verify that
     *    implies() returns true.
     * 5. Call implies() on returned PermissionCollections passing
     *    not granted permissions. Verify that implies()
     *    returns false.
     * 6. For ProtectionDomains that have
     *    PreferredClassLoader as ClassLoader
     *    call implies() on returned PermissionCollections passing
     *    permissions that granted to
     *    qa1-policy-provider.jar's codebase.
     *    Verify that implies() returns true.
     * 7. For ProtectionDomains that have
     *    PreferredClassLoader as ClassLoader
     *    call implies() on returned PermissionCollections passing
     *    permissions that are not granted to
     *    qa1-policy-provider.jar's codebase.
     *    Verify that implies() returns false.
     *
     * </pre>
     *
     * @param pma array of array of permissions.
     *
     * @throws TestException if failed
     */
    protected void checkImpliesGetPermissions(Permission[][] pma)
            throws TestException {

        /*
         * Iterate over ProtectionDomains and and check implies().
         */
        for (int i = 0; i < protectionDomains.length; i++) {
            ProtectionDomain pd = protectionDomains[i];

            if (pd == null) {
                continue;
            }

            /*
             * Get CodeSource for ProtectionDomain.
             */
            CodeSource cs = protectionDomains[i].getCodeSource();

            /*
             * Call getPermissions() on PolicyFileProvider passing
             * ProtectionDomain.
             */
            PermissionCollection pcPD = policy.getPermissions(pd);

            /*
             * Call getPermissions() on PolicyFileProvider passing
             * CodeSource.
             */
//            PermissionCollection pcCS = policy.getPermissions(cs);

            /*
             * Call implies() on returned PermissionCollections passing
             * permissions that granted in the policy file. Verify that
             * implies() returns true.
             */
            checkImplies(pcPD, pma[IGRANTED], true);
//            checkImplies(pcCS, pma[IGRANTED], true);

            /*
             * Call implies() on returned PermissionCollections passing
             * not granted permissions. Verify that implies()
             * returns false.
             */
            checkImplies(pcPD, pma[INOTGRANTED], false);
//            checkImplies(pcCS, pma[INOTGRANTED], false);

            /*
             * For ProtectionDomains that have
             * PreferredClassLoader as ClassLoader
             * call implies() on returned PermissionCollections passing
             * permissions that granted to
             * qa1-policy-provider.jar's codebase.
             * Verify that implies() returns true.
             */
            if (pd.getClassLoader() instanceof PreferredClassLoader) {
                checkImplies(pcPD, pma[ICODEBASEGRANTED], true);
//                checkImplies(pcCS, pma[ICODEBASEGRANTED], true);
            }

            if (pma[ICODEBASENOTGRANTED] == null) {
                continue;
            }

            /*
             * For ProtectionDomains that have
             * PreferredClassLoader as ClassLoader
             * call implies() on returned PermissionCollections passing
             * permissions that are not granted to
             * qa1-policy-provider.jar's codebase.
             * Verify that implies() returns false.
             */
            if (pd.getClassLoader() instanceof PreferredClassLoader) {
                checkImplies(pcPD, pma[ICODEBASENOTGRANTED], false);
//                checkImplies(pcCS, pma[ICODEBASENOTGRANTED], false);
            }
        }
    }

    /**
     * <pre>
     *
     * Iterate over ProtectionDomains and and check for
     * permissions returned from PermissionCollection.elements().
     * 1. Get CodeSource for ProtectionDomain.
     * 2. Call getPermissions() on PolicyFileProvider passing
     *    ProtectionDomain.
     * 3. Call getPermissions() on PolicyFileProvider passing
     *    CodeSource. - No longer tested as the sun policy
     *    provider is no longer accessible and ConcurrentPolicyFile
     *    returns empty Permissions, for all but privileged CodeSources
     *    as a scalability optimisation.
     * 4. Verify that permissions that granted in the policy file
     *    are included in the Enumeration returned from
     *    PermissionCollection.elements() for permission collections
     *    returned from Policy.getPermissions(ProtectionDomain) and
     *    Policy.getPermissions(CodeSource)
     * 5. Verify that permissions that not granted in the policy file
     *    are not included in the Enumeration returned from
     *    PermissionCollection.elements() for permission collections
     *    returned from Policy.getPermissions(ProtectionDomain) and
     *    Policy.getPermissions(CodeSource)
     * 6. For ProtectionDomains that have
     *    PreferredClassLoader as ClassLoader
     *    verify that permissions that granted to
     *    qa1-policy-provider.jar's codebase
     *    are included in the Enumeration returned from
     *    PermissionCollection.elements() for permission collections
     *    returned from Policy.getPermissions(ProtectionDomain) and
     *    Policy.getPermissions(CodeSource)
     * 7. For ProtectionDomains that have
     *    PreferredClassLoader as ClassLoader
     *    verify that permissions that are not granted to
     *    qa1-policy-provider.jar's codebase
     *    are not included in the Enumeration returned from
     *    PermissionCollection.elements() for permission collections
     *    returned from Policy.getPermissions(ProtectionDomain) and
     *    Policy.getPermissions(CodeSource)
     *
     * </pre>
     *
     * @param pma array of array of permissions.
     *
     * @throws TestException if failed
     */
    protected void checkElementsGetPermissions(Permission[][] pma)
            throws TestException {

        /*
         * Iterate over ProtectionDomains and and check implies().
         */
        for (int i = 0; i < protectionDomains.length; i++) {
            ProtectionDomain pd = protectionDomains[i];

            if (pd == null) {
                continue;
            }

            /*
             * Get CodeSource for ProtectionDomain.
             */
            CodeSource cs = protectionDomains[i].getCodeSource();

            /*
             * Call getPermissions() on PolicyFileProvider passing
             * ProtectionDomain.
             */
            PermissionCollection pcPD = policy.getPermissions(pd);

            /*
             * Call getPermissions() on PolicyFileProvider passing
             * CodeSource.
             */
//            PermissionCollection pcCS = policy.getPermissions(cs);

            /*
             * Verify that permissions that granted in the policy file
             * are included in the Enumeration returned from
             * PermissionCollection.elements() for permission collections
             * returned from Policy.getPermissions(ProtectionDomain) and
             * Policy.getPermissions(CodeSource).
             */
            checkElements(pcPD, pma[IGRANTED], true);
//            checkElements(pcCS, pma[IGRANTED], true);

            /*
             * Verify that permissions that not granted in the policy file
             * are not included in the Enumeration returned from
             * PermissionCollection.elements() for permission collections
             * returned from Policy.getPermissions(ProtectionDomain) and
             * Policy.getPermissions(CodeSource).
             */
            checkElements(pcPD, pma[INOTGRANTED], false);
//            checkElements(pcCS, pma[INOTGRANTED], false);

            /*
             * For ProtectionDomains that have
             * PreferredClassLoader as ClassLoader
             * verify that permissions that granted to
             * qa1-policy-provider.jar's codebase
             * are included in the Enumeration returned from
             * PermissionCollection.elements() for permission collections
             * returned from Policy.getPermissions(ProtectionDomain) and
             * Policy.getPermissions(CodeSource).
             */
            if (pd.getClassLoader() instanceof PreferredClassLoader) {
                checkElements(pcPD, pma[ICODEBASEGRANTED], true);
//                checkElements(pcCS, pma[ICODEBASEGRANTED], true);
            }

            if (pma[ICODEBASENOTGRANTED] == null) {
                continue;
            }

            /*
             * For ProtectionDomains that have
             * PreferredClassLoader as ClassLoader
             * verify that permissions that are not granted to
             * qa1-policy-provider.jar's codebase
             * are not included in the Enumeration returned from
             * PermissionCollection.elements() for permission collections
             * returned from Policy.getPermissions(ProtectionDomain) and
             * Policy.getPermissions(CodeSource).
             */
            if (pd.getClassLoader() instanceof PreferredClassLoader) {
                checkElements(pcPD, pma[ICODEBASENOTGRANTED], false);
//                checkElements(pcCS, pma[ICODEBASENOTGRANTED], false);
            }
        }
    }
}
