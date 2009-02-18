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

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

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
import com.sun.jini.test.spec.policyprovider.AbstractTestBase;

// utility classes
import com.sun.jini.test.spec.policyprovider.util.Item;
import com.sun.jini.test.spec.policyprovider.util.Util;
import com.sun.jini.test.spec.policyprovider.util.QAPrincipal;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>DynamicPolicyProvider</code>
 * grants permissions (granted with no principals) properly.
 *
 * <b>Test Description</b><br><br>
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
 *    <li> Iterates over ProtectionDomains and call implies() on
 *         DynamicPolicyProvider.
 *      <ol>
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
 *      </ol>
 *    </li>
 *    <li> For all loaded classes call grant on DynamicPolicyProvider
 *         passing permissions that should NOT BE dynamic granted
 *         and verify that SecurityExceptions are thrown.
 *    </li>
 *    <li> For all loaded classes call grant on DynamicPolicyProvider
 *         passing permissions that should BE dynamic granted
 *         and verify that no exceptions are thrown.
 *    </li>
 *    <li> Iterates over ProtectionDomains and call implies() on
 *         DynamicPolicyProvider.
 *      <ol>
 *        <li> Call implies on DynamicPolicyProvider passing
 *             permissions that granted in the policy file. Verify that
 *             implies() returns false if ProtectionDomain is equal to null,
 *             and verify that implies() returns true for non-null
 *             ProtectionDomains.
 *        </li>
 *        <li> Call implies on DynamicPolicyProvider passing
 *             dynamic granted permissions. Verify that implies()
 *             returns true for null and non-null
 *             ProtectionDomains.
 *        </li>
 *        <li> Call implies on DynamicPolicyProvider passing
 *             not granted permissions. Verify that implies()
 *             returns false for null and non-null
 *             ProtectionDomains.
 *        </li>
 *      </ol>
 *    </li>
 * </ol>
 *
 */
public class GrantNoPrincipalCase01 extends DynamicPolicyProviderTestBase {

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
         * Iterates over ProtectionDomains and call implies() on
         * DynamicPolicyProvider.
         */
        for (int i = 0; i < protectionDomains.length; i++) {
            ProtectionDomain pd = protectionDomains[i];

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
            checkImplies(pd, pmNotGranted, false, false);
        }

        /*
         * For all loaded classes call grant on DynamicPolicyProvider
         * passing permissions that should NOT BE dynamic granted
         * and verify that SecurityExceptions are thrown.
         */
        checkGrant(classes, pmGranted, true);
        checkGrant(classes, pmNotGranted, true);
        checkGrant(classes, pmDynamicNotGranted, true);

        /*
         * For all loaded classes call grant on DynamicPolicyProvider
         * passing permissions that should BE dynamic granted
         * and verify that no exceptions are thrown.
         */
        checkGrant(classes, pmDynamicGranted, false);

        /*
         * Iterates over ProtectionDomains and call implies() on
         * DynamicPolicyProvider.
         */
        for (int i = 0; i < protectionDomains.length; i++) {
            ProtectionDomain pd = protectionDomains[i];

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
             * dynamic granted permissions. Verify that implies()
             * returns true for null and non-null
             * ProtectionDomains.
             */
            checkImplies(pd, pmDynamicGranted, true, false);

            /*
             * Call implies on DynamicPolicyProvider passing
             * not granted permissions. Verify that implies()
             * returns false for null and non-null
             * ProtectionDomains.
             */
            checkImplies(pd, pmDynamicNotGranted, false, false);
        }
    }
}
