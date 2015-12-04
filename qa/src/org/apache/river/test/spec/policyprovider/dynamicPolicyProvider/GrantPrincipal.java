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
package org.apache.river.test.spec.policyprovider.dynamicPolicyProvider;

import java.util.logging.Level;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.TestException;

// java.io
import java.io.FilePermission;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// java.security
import java.security.Policy;
import java.security.Principal;
import java.security.Permission;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.PermissionCollection;

// davis packages
import net.jini.security.policy.DynamicPolicyProvider;

// test base class
import org.apache.river.test.spec.policyprovider.AbstractTestBase;

// utility classes
import org.apache.river.test.spec.policyprovider.util.Item;
import org.apache.river.test.spec.policyprovider.util.Util;
import org.apache.river.test.spec.policyprovider.util.QAPrincipal;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>DynamicPolicyProvider</code>
 * grants permissions granted with principals properly.
 *
 * <b>Test Description</b><br><br>
 *
 *  This test is complex test using constructors and implies(), refresh()
 *  methods of <code>PolicyFileProvider</code>.
 *  This test should be run with policy.ProviderGrant01 policy file.
 *  policy.ProviderGrant01 contains needed permissions.
 *
 *  <br><br>
 *
 *  This test uses two class loaders. First is system class loader that
 *  loads a couple of classes from qa1.jar file via class path, second is
 *  PreferredClassLoader that loads a couple of classes from
 *  qa1-policy-provider.jar file via http protocol.
 *  There are two set of classes with the same names in the
 *  qa1.jar and qa1-policy-provider.jar files. These classes will be loaded
 *  for test purpose. This test iterates over set of loaded classes and
 *  makes actions according <b>Actions</b> section.
 *
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ul><lh>This test requires the following infrastructure:</lh>
 *  <li> qa1-policy-provider.jar file that contains classes to be loaded</li>
 *  <li> first policy.policyProviderGrant01 file that contains needed
 *       permissions</li>
 *  <li> second policy.policyProviderGrant02 file that contains needed
 *       permissions</li>
 *  <li> {@link Util#listClasses} that contains class names to be loaded</li>
 *  <li> {@link QAPrincipal} for test purpose</li>
 * </ul>
 *
 * <b>Actions</b><br><br>
 * <ol>
 *    <li> Create base array of QAPrincipals.
 *    </li>
 *    <li> Create array of array of QAPrincipals so that the ith element of the
 *         array contains QAPrincipals pr_1 ... pr_i
 *    </li>
 *    <li> Create class loaders then load classes from qa1.jar file using
 *         system class loader and load classes from qa1-policy-provider.jar
 *         file using PreferredClassLoader and http protocol.
 *         Store class loaders, loaded classes and ProtectionDomains for
 *         loaded classes into arrays.
 *    </li>
 *    <li> Create new DynamicPolicyProvider()
 *    </li>
 *    <li> Iterate over created class loaders.
 *      <ul>
 *      <li> Iterate over created array of array of QAPrincipals
 *        <ol>
 *          <li> Create ProtectionDomain passing null as CodeSource,
 *               null as PermissionCollection, class loader and created
 *               array of QAPrincipals.
 *          </li>
 *          <li> Call implies() on DynamicPolicyProvider passing
 *               created ProtectionDomain and permissions that are
 *               granted in the policy file.
 *               Verify that implies() returns false.
 *          </li>
 *          <li> Call implies() on DynamicPolicyProvider passing
 *               created ProtectionDomain and permissions that are
 *               dynamic granted in the policy file.
 *               Verify that implies() returns false.
 *          </li>
 *          <li> Call implies() on DynamicPolicyProvider passing
 *               created ProtectionDomain and permissions that are
 *               not granted in the policy file.
 *               Verify that implies() returns false.
 *          </li>
 *        </ol>
 *      </li>
 *      </ul>
 *    </li>
 *    <li> Iterate over created array of array of QAPrincipals.
 *      <ul>
 *        <li> For all loaded classes call grant() on
 *             DynamicPolicyProvider passing array of QAPrincipals and
 *             passing permissions that should NOT BE dynamic granted
 *            and verify that SecurityExceptions are thrown.
 *        </li>
 *      </ul>
 *    </li>
 *    <li> Verify that size of array of loaded classes is less then
 *         the size of created array of array of QAPrincipals.
 *    </li>
 *    <li> Iterate over loaded classes.
 *      <ul>
 *        <li> Call grant() on DynamicPolicyProvider
 *             passing class, array of QAPrincipals so that
 *             index of passing class shoud be equal to
 *             index of array of array of QAPrincipals and
 *             permissions that should BE dynamic granted
 *             and verify that no exceptions are thrown.
 *        </li>
 *      </ul>
 *    </li>
 *    <li> Iterate over loaded classes and check for
 *         dynamic permissions granted earlier using getGrants()
 *         method.
 *      <ul>
 *        <li> Iterate over array of array of QAPrincipals and
 *             call getGrants() passing array of QAPrincipals.
 *             Verify that getGrants() returns permissions that are
 *             dynamic granted earlier only for array of QAPrincipals
 *             that have index more or equal to upperbound index
 *             for classes that belongs to the same class loader.
 *             For indexes that less then upperbound index
 *             getGrants() should return empty array of permissions.
 *        </li>
 *      </ul>
 *    </li>
 *    <li> Iterate over loaded classes and check for
 *         dynamic permissions granted earlier using implies()
 *         method.
 *      <ol>
 *        <li> Get CodeSource of class.
 *        </li>
 *        <li> Get CodeSource of class.
 *        </li>
 *        <li> Iterate over array of array of QAPrincipals.
 *          <ol>
 *            <li> Create ProtectionDomain passing code source of class,
 *                 null as PermissionCollection, class loader of
 *                 class and created array of QAPrincipals.
 *            </li>
 *            <li> Call implies() on DynamicPolicyProvider passing
 *                 created ProtectionDomain and
 *                 permissions that granted in the policy file.
 *                 Verify that implies() returns true.
 *            </li>
 *            <li> Call implies() on DynamicPolicyProvider passing
 *                 created ProtectionDomain and
 *                 not granted permissions.
 *                 Verify that implies() returns true
 *                 only for array of QAPrincipals
 *                 that have index more or equal to upperbound index
 *                 for classes that belongs to the same class loader.
 *                 For indexes that less then upperbound index
 *                 implies() should return false.
 *            </li>
 *            <li> Call implies on DynamicPolicyProvider passing
 *                 not granted permissions. Verify that implies()
 *                 returns false.
 *            </li>
 *          </ol>
 *        </li>
 *      </ol>
 *    </li>
 *    <li> Set Policy to created DynamicPolicyProvider.
 *    </li>
 *    <li> Reset java.security.policy to new policy file.
 *    </li>
 *    <li> Call refresh() on DynamicPolicyProvider.
 *    </li>
 *    <li> Iterate over loaded classes and check for
 *         dynamic permissions granted earlier using getGrants()
 *         method.
 *      <ul>
 *        <li> Iterate over array of array of QAPrincipals and
 *             call getGrants() passing array of QAPrincipals.
 *             Verify that getGrants() returns permissions that are
 *             dynamic granted earlier only for array of QAPrincipals
 *             that have index more or equal to upperbound index
 *             for classes that belongs to the same class loader.
 *             For indexes that less then upperbound index
 *             getGrants() should return empty array of permissions.
 *        </li>
 *      </ul>
 *    </li>
 * </ol>
 *
 */
public class GrantPrincipal extends DynamicPolicyProviderTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        QAPrincipal pr01 = new QAPrincipal("01");
        QAPrincipal pr02 = new QAPrincipal("02");
        QAPrincipal pr03 = new QAPrincipal("03");
        QAPrincipal pr04 = new QAPrincipal("04");
        QAPrincipal pr05 = new QAPrincipal("05");
        QAPrincipal pr06 = new QAPrincipal("06");
        QAPrincipal pr07 = new QAPrincipal("07");
        QAPrincipal pr08 = new QAPrincipal("08");
        QAPrincipal pr09 = new QAPrincipal("09");
        QAPrincipal pr10 = new QAPrincipal("10");
        QAPrincipal pr11 = new QAPrincipal("11");
        QAPrincipal pr12 = new QAPrincipal("12");
        Permission pm1 = new RuntimePermission("A");
        Permission pm2 = new RuntimePermission("B");
        Permission pm3 = new RuntimePermission("C");
        Permission pm4 = new RuntimePermission("D");
        Permission pm5 = new RuntimePermission("E");
        Permission pm6 = new RuntimePermission("F");
        Permission pm7 = new RuntimePermission("C1");
        Permission pm8 = new RuntimePermission("D1");
        Permission[] pmGranted = new Permission[] { pm1, pm2 };
        Permission[] pmNotGranted = new Permission[] { pm5, pm6, pm3, pm4 };
        Permission[] pmDynamicGranted = new Permission[] { pm3, pm4, pm7, pm8 };
        Permission[] pmDynamicNotGranted = new Permission[] { pm5, pm6 };
        Permission[] pmEmpty = new Permission[] {};
        QAPrincipal[] praBase = null;

        /*
         * Create base array of QAPrincipals.
         */
        praBase = new QAPrincipal[] { pr01, pr02, pr03, pr04, pr05, pr06,
                                      pr07, pr08, pr09, pr10, pr11, pr12 };

        /*
         * Create array of array of QAPrincipals so that the ith element of the
         * array contains QAPrincipals pr_1 ... pr_i from base array of
         * QAPrincipals.
         */
        QAPrincipal[][] praa = new QAPrincipal[praBase.length + 1][];

        for (int i = 0; i <= praBase.length; i++) {
            praa[i] = new QAPrincipal[i];

            for (int j = 0; j < i; j++) {
                praa[i][j] = praBase[j];
            }
        }

        /*
         * Create class loaders then load classes from qa1.jar file using
         * system class loader and load classes from qa1-policy-provider.jar
         * file using PreferredClassLoader and http protocol.
         * Store class loaders, loaded classes and ProtectionDomains for
         * loaded classes into arrays.
         *
         */
        loadClasses();

        /*
         * Create new DynamicPolicyProvider().
         */
        createDynamicPolicyProvider();

        /*
         * Iterate over created class loaders.
         */
        for (int i = 0; i < classLoaders.length; i++) {

            /*
             * Iterate over created array of array of QAPrincipals.
             */
            for (int j = 0; j <= praBase.length; j++) {

                /*
                 * Create ProtectionDomain passing null as CodeSource,
                 * null as PermissionCollection, class loader and created
                 * array of QAPrincipals.
                 */
                ProtectionDomain pd = new ProtectionDomain(null, null,
                        classLoaders[i], praa[i]);

                /*
                 * Call implies() on DynamicPolicyProvider passing
                 * created ProtectionDomain and permissions that are
                 * granted in the policy file.
                 * Verify that implies() returns false.
                 */
                checkImplies(pd, pmGranted, false, false);

                /*
                 * Call implies() on DynamicPolicyProvider passing
                 * created ProtectionDomain and permissions that are
                 * dynamic granted in the policy file.
                 * Verify that implies() returns false.
                 */
                checkImplies(pd, pmDynamicGranted, false, false);

                /*
                 * Call implies() on DynamicPolicyProvider passing
                 * created ProtectionDomain and permissions that are
                 * not granted in the policy file.
                 * Verify that implies() returns false.
                 */
                checkImplies(pd, pmDynamicNotGranted, false, false);
            }
        }

        /*
         * Iterate over created array of array of QAPrincipals.
         */
        for (int i = 0; i <= praBase.length; i++) {

            /*
             * For all loaded classes call grant() on
             * DynamicPolicyProvider passing array of QAPrincipals and
             * passing permissions that should NOT BE dynamic granted
             * and verify that SecurityExceptions are thrown.
             */
            checkGrant(classes, praa[i], pmGranted, true);
            checkGrant(classes, praa[i], pmNotGranted, true);
            checkGrant(classes, praa[i], pmDynamicNotGranted, true);
        }

        /*
         * Verify that size of array of loaded classes is less then
         * the size of created array of array of QAPrincipals.
         */
        if (classes.length >= praBase.length) {
            throw new TestException("Too many loaded classes.");
        }

        /*
         * Iterate over loaded classes.
         */
        for (int i = 1; i < classes.length; i++) {

            /*
             * Call grant() on DynamicPolicyProvider
             * passing class, array of QAPrincipals so that
             * index of passing class shoud be equal to
             * index of array of array of QAPrincipals and
             * permissions that should BE dynamic granted
             * and verify that no exceptions are thrown.
             */
            Class[] cla = new Class[] { classes[i] };
            checkGrant(cla, praa[i], pmDynamicGranted, false);
        }

        /*
         * Iterate over loaded classes and check for
         * dynamic permissions granted earlier using getGrants()
         * method.
         */
        int boundCheck = Util.listClasses.length;
        for (int i = 1; i < classes.length; i++) {
            Class[] cla = new Class[] { classes[i] };

            /*
             * Iterate over array of array of QAPrincipals and
             * call getGrants() passing array of QAPrincipals.
             * Verify that getGrants() returns permissions that are
             * dynamic granted earlier only for array of QAPrincipals
             * that have index more or equal to upperbound index
             * for classes that belongs to the same class loader.
             * For indexes that less then upperbound index
             * getGrants() should return empty array of permissions.
             *
             */
            for (int j = 0; j <= praBase.length; j++) {
                int upperBound = 1 + ((i - 1) / boundCheck) * boundCheck;

                if (j < upperBound) {
                    checkGetGrants(cla, praa[j], pmEmpty);
                } else {
                    checkGetGrants(cla, praa[j], pmDynamicGranted);
                }
            }
        }

        /*
         * Iterate over loaded classes and check for
         * dynamic permissions granted earlier using implies()
         * method.
         */
        for (int i = 1; i < classes.length; i++) {

            /*
             * Get CodeSource of class.
             */
            CodeSource s = classes[i].getProtectionDomain().getCodeSource();

            /*
             * Iterate over array of array of QAPrincipals.
             * This is where ConcurrentDynamicPolicyProvider has some issues,
             * due to it's granting permission by ProtectionDomain instead
             * of ClassLoader.  When implies is called in the original spec
             * it grants by ClassLoader, such that multiple protection domains 
             * are given identical Permissions.
             * 
             */
            for (int j = 0; j < praBase.length; j++) {

                /*
                 * Create ProtectionDomain passing code source of class,
                 * null as PermissionCollection, class loader of
                 * class and created array of QAPrincipals.
                 */
                ProtectionDomain pd = new ProtectionDomain(s, null,
                        classes[i].getClassLoader(), praa[j]);

                /*
                 * Call implies() on DynamicPolicyProvider passing
                 * created ProtectionDomain and
                 * permissions that granted in the policy file.
                 * Verify that implies() returns true.
                 */
                checkImplies(pd, pmGranted, true, false);

                /*
                 * Call implies() on DynamicPolicyProvider passing
                 * created ProtectionDomain and
                 * not granted permissions.
                 * Verify that implies() returns true
                 * only for array of QAPrincipals
                 * that have index more or equal to upperbound index
                 * for classes that belongs to the same class loader.
                 * For indexes that less then upperbound index
                 * implies() should return false.
                 */
                int upperBound = 1 + ((i - 1) / boundCheck) * boundCheck;
                boolean shouldReturn;

                if (j < upperBound) {
                    shouldReturn = false;
                } else {
                    shouldReturn = true;
                }
                checkImplies(pd, pmDynamicGranted, shouldReturn, false);

                /*
                 * Call implies on DynamicPolicyProvider passing
                 * not granted permissions. Verify that implies()
                 * returns false.
                 */
                checkImplies(pd, pmDynamicNotGranted, false, false);
            }
        }

        /*
         * Set Policy to created DynamicPolicyProvider.
         */
        Policy.setPolicy(policy);

        /*
         * Reset java.security.policy to new policy file.
         */
        setPolicyFile("FILEPOLICY02");

        /*
         * Call refresh() on DynamicPolicyProvider.
         */
        logger.log(Level.FINE, "policy.refresh()");
        policy.refresh();

        /*
         * Iterate over loaded classes and check for
         * dynamic permissions granted earlier using getGrants()
         * method.
         */
        for (int i = 1; i < classes.length; i++) {
            Class[] cla = new Class[] { classes[i] };

            /*
             * Iterate over array of array of QAPrincipals and
             * call getGrants() passing array of QAPrincipals.
             * Verify that getGrants() returns permissions that are
             * dynamic granted earlier only for array of QAPrincipals
             * that have index more or equal to upperbound index
             * for classes that belongs to the same class loader.
             * For indexes that less then upperbound index
             * getGrants() should return empty array of permissions.
             *
             */
            for (int j = 0; j <= praBase.length; j++) {
                int upperBound = 1 + ((i - 1) / boundCheck) * boundCheck;

                if (j < upperBound) {
                    checkGetGrants(cla, praa[j], pmEmpty);
                } else {
                    checkGetGrants(cla, praa[j], pmDynamicGranted);
                }
            }
        }
    }
}
