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

// java.util
import java.util.Arrays;

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
 * This test verifies that <code>DynamicPolicyProvider</code> returns
 * dynamic granted permissions (granted with no principals) properly.
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
 *  <li> first policy.policyProviderGrant01 file that contains needed
 *       permissions</li>
 *  <li> second policy.policyProviderGrant02 file that contains needed
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
 *    <li> Iterate over loaded classes and call getGrants() on
 *         DynamicPolicyProvider passing null as principals.
 *         Verify that returned array is not null and does not contain
 *         any element.
 *    </li>
 *    <li> For all loaded classes call grant() on DynamicPolicyProvider
 *         passing permissions that should BE dynamic granted
 *         and verify that no exceptions are thrown.
 *    </li>
 *    <li> Iterate over loaded classes and call getGrants() on
 *         DynamicPolicyProvider passing null as principals.
 *         Verify that returned array is not null and contains
 *         only dynamic granted permissions.
 *    </li>
 *    <li> Set Policy to created DynamicPolicyProvider.
 *    </li>
 *    <li> Reset java.security.policy to new policy file.
 *    </li>
 *    <li> Call refresh() on DynamicPolicyProvider.
 *    </li>
 *    <li> Iterate over loaded classes and call getGrants() on
 *         DynamicPolicyProvider passing null as principals.
 *         Verify that returned array is not null and contains
 *         only dynamic granted permissions that was granted earlier.
 *    </li>
 *    <li> For all loaded classes call grant() on DynamicPolicyProvider
 *         passing permissions that should BE dynamic granted
 *         and verify that no exceptions are thrown.
 *    </li>
 *    <li> Iterate over loaded classes and call getGrants() on
 *         DynamicPolicyProvider passing null as principals.
 *         Verify that returned array is not null and contains
 *         only dynamic granted permissions that was granted earlier.
 *    </li>
 * </ol>
 *
 */
public class GetGrantsNoPrincipal extends DynamicPolicyProviderTestBase {

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
        Permission[] pmGranted01 = new Permission[] { pm3, pm4 };
        Permission[] pmGranted02 = new Permission[] { pm5 };
        Permission[] pmGrantedAll = new Permission[] { pm3, pm4, pm5 };

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
         * Iterate over loaded classes and call getGrants() on
         * DynamicPolicyProvider passing null as principals.
         * Verify that returned array is not null and does not contain
         * any element.
         */
        checkGetGrants(classes, null, new Permission[0]);

        /*
         * For all loaded classes call grant() on DynamicPolicyProvider
         * passing permissions that should BE dynamic granted
         * and verify that no exceptions are thrown.
         */
        checkGrant(classes, pmGranted01, false);

        /*
         * Iterate over loaded classes and call getGrants() on
         * DynamicPolicyProvider passing null as principals.
         * Verify that returned array is not null and contains
         * only dynamic granted permissions.
         */
        checkGetGrants(classes, null, pmGranted01);

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
         * Iterate over loaded classes and call getGrants() on
         * DynamicPolicyProvider passing null as principals.
         * Verify that returned array is not null and contains
         * only dynamic granted permissions that was granted earlier.
         */
        checkGetGrants(classes, null, pmGranted01);

        /*
         * For all loaded classes call grant() on DynamicPolicyProvider
         * passing permissions that should BE dynamic granted
         * and verify that no exceptions are thrown.
         */
        checkGrant(classes, pmGranted02, false);

        /*
         * Iterate over loaded classes and call getGrants() on
         * DynamicPolicyProvider passing null as principals.
         * Verify that returned array is not null and contains
         * only dynamic granted permissions that was granted earlier.
         */
        checkGetGrants(classes, null, pmGrantedAll);
    }
}
