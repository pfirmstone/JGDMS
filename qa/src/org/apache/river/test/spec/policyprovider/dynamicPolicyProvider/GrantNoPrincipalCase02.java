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
 * grants permissions (granted with no principals) properly.
 *
 * <b>Test Description</b><br><br>
 *
 * This test is additional test to {@link GrantNoPrincipalCase01} <br>
 *
 * This test verifies that
 * <ol>
 *  <li>
 *   grants to a class with class loader A only apply to
 *   protection domains with class loader A, and not to protection domains
 *   with some other class loader B.
 *  </li>
 *  <li>
 *   grants to a class with class loader A only apply to
 *   protection domains with class loader A, and not to protection domains
 *   with some other class loader C that delegates to A.
 *  </li>
 *  <li>
 *   grants to a class with class loader A apply to
 *   protection domains with class loader A that did not exist at the time of
 *   the grant.
 *  </li>
 *  <li>
 *   grants where the specified class is null apply to all
 *   protection domains, regardless of their associated class loaders.
 *  </li>
 *  <li>
 *   granted permissions (aside from those granted with a
 *   class value of null) are not included in PermissionCollections
 *   returned from Policy.getPermissions(CodeSource).
 *  </li>
 * </ol>
 *
 *  <br><br>
 *
 *  This test should be run with policy.ProviderGrant01 policy file.
 *  policy.ProviderGrant01 contains needed permissions.
 *
 *  <br><br>
 *
 *  This test uses three class loaders. First is system class loader that
 *  loads a couple of classes from qa1.jar file via class path, second and third
 *  are PreferredClassLoaders that loads a couple of classes from
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
 *  <li> policy.policyProviderGrant01 file that contains needed
 *       permissions</li>
 *  <li> {@link Util#listClasses} that contains class names to be loaded</li>
 * </ul>
 *
 * <b>Actions</b><br><br>
 * <ol>
 *    <li> Create class loaders then load classes from qa1.jar file using
 *         system class loader and load classes from qa1-policy-provider.jar
 *         file using PreferredClassLoader and http protocol.
 *         Store class loaders, loaded classes and ProtectionDomains for
 *         loaded classes into arrays. Resulting array of classes
 *         should contain null (at top index) to passing null as
 *         ProtectionDomain for implies() calls.
 *    </li>
 *    <li> Create new DynamicPolicyProvider()
 *    </li>
 *    <li> Verify that size of array of class loaders is less then
 *         the size of array of permissions that should be dynamic
 *         granted.
 *    </li>
 *    <li> Create array of array of Permissions so that the ith element of
 *         the array contains Permissions pr_1 ... pr_i from array of
 *         Permissions that should be dynamic granted.
 *    </li>
 *    <li> Iterate over class loaders and call grant() on
 *         DynamicPolicyProvider (for classes that were loaded
 *         by this class loader) passing array of permissions so that
 *         index of class loader shoud be equal to
 *         index of created array of array of Permissions
 *         and verify that no exceptions are thrown.
 *    </li>
 *    <li> Iterate over class loaders.
 *      <ol>
 *        <li> Get classes loaded by this class loader.
 *        </li>
 *        <li> Iterate over classes loaded by this class loader.
 *          <ol>
 *            <li> Get ProtectionDomain for loaded class.
 *            </li>
 *            <li> Get CodeSource for ProtectionDomain for loaded class.
 *            </li>
 *            <li> Create new ProtectionDomain passing code source,
 *                 null as PermissionCollection, class loader of
 *                 class and null as array of Principals.
 *            </li>
 *            <li> Create new ProtectionDomain passing null as code source,
 *                 null as PermissionCollection, class loader of
 *                 class and null as array of Principals.
 *            </li>
 *            <li> Iterate over dynamic granted permissions and
 *                 call implies() on DynamicPolicyProvider passing
 *                 ProtectionDomain for loaded class and granted permission,
 *                 call implies() on DynamicPolicyProvider passing
 *                 newly created ProtectionDomains and granted permission.
 *                 Verify that implies() returns true for permissions
 *                 that should be granted for these ProtectionDomains
 *                 and false otherwise
 *            </li>
 *          </ol>
 *        </li>
 *      </ol>
 *    </li>
 *    <li> Call grant() on DynamicPolicyProvider passing
 *         null as specified class and permissions so that
 *         some permissions are dynamic granted earlier and
 *         some permissions are not dynamic granted yet.
 *         Lets name these permissions pmAll.
 *    </li>
 *    <li> Iterate over ProtectionDomains (including null ProtectionDomain)
 *         and call implies() on DynamicPolicyProvider.
 *      <ol>
 *        <li> Call implies on DynamicPolicyProvider passing
 *             pmAll permissions. Verify that implies()
 *             returns true for null and non-null
 *             ProtectionDomains.
 *        </li>
 *        <li> Call implies on DynamicPolicyProvider passing
 *             permissions that granted in the policy file. Verify that
 *             implies() returns false if ProtectionDomain is equal to null,
 *             and verify that implies() returns true for non-null
 *             ProtectionDomains.
 *        </li>
 *        <li> Call implies on DynamicPolicyProvider passing
 *             not granted permissions. Verify that implies()
 *             returns false for null and non-null
 *             ProtectionDomains.
 *        </li>
 *        <li> Get CodeSource for ProtectionDomain.
 *        </li>
 *        <li> Iterate over class loaders.
 *          <ol>
 *            <li> Create new ProtectionDomain passing code source,
 *                 null as PermissionCollection, class loader and
 *                 null as array of Principals.
 *            </li>
 *            <li> Create new ProtectionDomain passing null as code source,
 *                 null as PermissionCollection, class loader
 *                 and null as array of Principals.
 *            </li>
 *            <li> Call implies() on DynamicPolicyProvider passing
 *                 newly created ProtectionDomains and pmAll
 *                 permissions and verify that implies() returns true.
 *            </li>
 *          </ol>
 *        </li>
 *        <li> Verify that granted permissions (aside from those granted
 *             with a class value of null) are not included in
 *             PermissionCollections returned from
 *             Policy.getPermissions(CodeSource).
 *        </li>
 *      </ol>
 *    </li>
 * </ol>
 *
 */
public class GrantNoPrincipalCase02 extends DynamicPolicyProviderTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
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
         * Verify that size of array of class loaders is less then
         * the size of array of permissions that should be dynamic
         * granted.
         */
        if (classLoaders.length >= pmDynamicGranted.length) {
            throw new TestException("Too many class loaders.");
        }

        /*
         * Create array of array of Permissions so that the ith element of
         * the array contains Permissions pr_1 ... pr_i from array of
         * Permissions that should be dynamic granted.
         */
        Permission[][] pmaa = new Permission[classLoaders.length][];

        for (int i = 0; i < classLoaders.length; i++) {
            pmaa[i] = new Permission[i + 1];

            for (int k = 0; k <= i; k++) {
                pmaa[i][k] = pmDynamicGranted[k];
            }
        }

        /*
         * Iterate over class loaders and call grant() on
         * DynamicPolicyProvider (for classes that were loaded
         * by this class loader) passing array of permissions so that
         * index of class loader shoud be equal to
         * index of created array of array of Permissions
         * and verify that no exceptions are thrown.
         */
        for (int i = 0; i < classLoaders.length; i++) {
            checkGrant(classAA[i], pmaa[i], false);
        }

        /*
         * Iterate over class loaders.
         */
        for (int i = 0; i < classLoaders.length; i++) {

            /*
             * Get classes loaded by this class loader.
             */
            Class[] cla = classAA[i];

            /*
             * Iterate over classes loaded by this class loader.
             */
            for (int j = 0; j < cla.length; j++) {

                /*
                 * Get ProtectionDomain for loaded class.
                 */
                ProtectionDomain pd = cla[j].getProtectionDomain();

                /*
                 * Get CodeSource for ProtectionDomain for loaded class.
                 */
                CodeSource s = pd.getCodeSource();

                /*
                 * Create new ProtectionDomain passing code source,
                 * null as PermissionCollection, class loader of
                 * class and null as array of Principals.
                 */
                ProtectionDomain pdNew01 = new ProtectionDomain(s, null,
                        classLoaders[i], null);

                /*
                 * Create new ProtectionDomain passing null as code source,
                 * null as PermissionCollection, class loader of
                 * class and null as array of Principals.
                 */
                ProtectionDomain pdNew02 = new ProtectionDomain(null, null,
                        classLoaders[i], null);

                /*
                 * Iterate over dynamic granted permissions.
                 */
                for (int k = 0; k < pmDynamicGranted.length; k++) {

                    /*
                     * Call implies() on DynamicPolicyProvider passing
                     * ProtectionDomain for loaded class and granted
                     * permission.
                     * Call implies() on DynamicPolicyProvider passing
                     * newly created ProtectionDomains and granted
                     * permission.
                     * Verify that implies() returns true for permissions
                     * that should be granted for these ProtectionDomains
                     * and false otherwise.
                     */
                    Permission[] p = new Permission[] {
                        pmDynamicGranted[k] };
                    boolean shouldReturn = (k <= i);
                        checkImplies(pd, p, shouldReturn, false);
                        checkImplies(pdNew01, p, shouldReturn, false);
                        checkImplies(pdNew02, p, shouldReturn, false);
                    }
                }
            }

        /*
         * Call grant() on DynamicPolicyProvider passing
         * null as specified class and permissions so that
         * some permissions are dynamic granted earlier and
         * some permissions are not dynamic granted yet.
         * Lets name these permissions pmAll.
         */
        Permission[] pmAll = new Permission[] { pm3, pm8 };
        Permission[] pmAsided = new Permission[] { pm4, pm7 };
        String nameAll = pm3.toString() + ", " + pm8.toString();
        msg = "policy.grant(null, null, " + nameAll + ")";
        callGrant(null, null, pmAll, msg);

        /*
         * Iterate over ProtectionDomains (including null ProtectionDomain)
         * and call implies() on DynamicPolicyProvider.
         */
        for (int i = 0; i < protectionDomains.length; i++) {
            ProtectionDomain pd = protectionDomains[i];

                /*
                 * Call implies on DynamicPolicyProvider passing
                 * pmAll permissions. Verify that implies()
                 * returns true for null and non-null
                 * ProtectionDomains.
                 */
                checkImplies(pd, pmAll, true, false);

                /*
                 * Call implies on DynamicPolicyProvider passing
                 * permissions that granted in the policy file. Verify that
                 * implies() returns false if ProtectionDomain is equal to null,
                 * and verify that implies() returns true for non-null
                 * ProtectionDomains.
                 */
                checkImplies(pd, pmGranted, true, true);

                /*
                 * Call implies on DynamicPolicyProvider passing
                 * not granted permissions. Verify that implies()
                 * returns false for null and non-null
                 * ProtectionDomains.
                 */
                checkImplies(pd, pmDynamicNotGranted, false, false);

                if (pd == null) {
                    continue;
                }

                /*
                 * Get CodeSource for ProtectionDomain.
                 */
                CodeSource s = pd.getCodeSource();

                /*
                 * Iterate over class loaders.
                 */
                for (int j = 0; j < classLoaders.length; j++) {

                    /*
                     * Create new ProtectionDomain passing code source,
                     * null as PermissionCollection, class loader and
                     * null as array of Principals.
                     */
                    ProtectionDomain pdNew01 = new ProtectionDomain(s, null,
                            classLoaders[j], null);

                    /*
                     * Create new ProtectionDomain passing null as code source,
                     * null as PermissionCollection, class loader
                     * and null as array of Principals.
                     */
                    ProtectionDomain pdNew02 = new ProtectionDomain(null, null,
                            classLoaders[j], null);

                    /*
                     * Call implies() on DynamicPolicyProvider passing
                     * newly created ProtectionDomains and pmAll
                     * permissions and verify that implies() returns true.
                     */
                    checkImplies(pdNew01, pmAll, true, false);
                    checkImplies(pdNew02, pmAll, true, false);
                }

                /*
                 * Verify that granted permissions (aside from those granted
                 * with a class value of null) are not included in
                 * PermissionCollections returned from
                 * Policy.getPermissions(CodeSource).
                 */
                callGetPermissionsNoGranted(s, pmAsided);
                callGetPermissions(s, pmAll, true, null);
            }
        }
    }
