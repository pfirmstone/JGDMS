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
 * This test is additional test to {@link GrantPrincipal} <br>
 *
 * This test verifies that
 * <ol>
 *  <li>
 *   a permissions granted to class C1 with loader L1 and principals P1...Pn
 *   does not get granted to some other class C2 with loader C2 but the same
 *   principals P1...Pn.
 *  </li>
 * </ol>
 *
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
 *    <li> Create class loaders then load classes from qa1.jar file using
 *         system class loader and load classes from qa1-policy-provider.jar
 *         file using PreferredClassLoader and http protocol.
 *         Store class loaders, loaded classes and ProtectionDomains for
 *         loaded classes into arrays.
 *    </li>
 *    <li> Create new DynamicPolicyProvider()
 *    </li>
 *    <li> Verify that size of array of class loaders is less then
 *         the size of array of permissions that should be dynamic
 *         granted.
 *    </li>
 *    <li> Verify that size of array of class loaders is less then
 *         the size of base array of principals.
 *    </li>
 *    <li>  Create array of array of Permissions so that the ith element of
 *          the array contains Permissions pr_1 ... pr_i from array of
 *          Permissions that should be dynamic granted.
 *    </li>
 *    <li>  Iterate over class loaders and call grant() on
 *          DynamicPolicyProvider (for classes that were loaded
 *          by this class loader) passing base array of principals and
 *          array of permissions so that
 *          index of class loader shoud be equal to
 *          index of created array of array of Permissions
 *          and verify that no exceptions are thrown.
 *    </li>
 *    <li>  Iterate over class loaders,
 *          get classes loaded by this class loader,
 *          iterate over loaded classes,
 *          call getGrants() passing base array of QAPrincipals.
 *          Verify that getGrants() returns corresponding array of
 *          permissions that are dynamic granted earlier.
 *    </li>
 * </ol>
 *
 */
public class GrantPrincipalSame extends DynamicPolicyProviderTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        QAPrincipal pr01 = new QAPrincipal("01");
        QAPrincipal pr02 = new QAPrincipal("02");
        QAPrincipal pr03 = new QAPrincipal("03");
        QAPrincipal pr04 = new QAPrincipal("04");
        Permission pm3 = new RuntimePermission("C");
        Permission pm4 = new RuntimePermission("D");
        Permission pm7 = new RuntimePermission("C1");
        Permission pm8 = new RuntimePermission("D1");
        Permission[] pmDynamicGranted = new Permission[] { pm3, pm4, pm7, pm8 };

        /*
         * Create base array of QAPrincipals.
         */
        QAPrincipal[] praBase = new QAPrincipal[] { pr01, pr02, pr03, pr04 };

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
         * Verify that size of array of class loaders is less then
         * the size of base array of principals.
         */
        if (classLoaders.length >= praBase.length) {
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
         * by this class loader) passing base array of principals and
         * array of permissions so that
         * index of class loader shoud be equal to
         * index of created array of array of Permissions
         * and verify that no exceptions are thrown.
         */
        for (int i = 0; i < classLoaders.length; i++) {
            checkGrant(classAA[i], praBase, pmaa[i], false);
        }

        /*
         * Iterate over class loaders,
         * get classes loaded by this class loader,
         * iterate over loaded classes,
         * call getGrants() passing base array of QAPrincipals.
         * Verify that getGrants() returns corresponding array of
         * permissions that are dynamic granted earlier.
         */
        for (int i = 0; i < classLoaders.length; i++) {
            checkGetGrants(classAA[i], praBase, pmaa[i]);
        }
    }
}
