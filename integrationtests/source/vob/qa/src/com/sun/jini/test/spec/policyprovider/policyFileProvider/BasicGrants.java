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
package com.sun.jini.test.spec.policyprovider.policyFileProvider;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

// java.io
import java.io.FilePermission;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// java.security
import java.security.Principal;
import java.security.Permission;

// utility classes
import com.sun.jini.test.spec.policyprovider.util.Item;
import com.sun.jini.test.spec.policyprovider.util.Util;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>PolicyFileProvider</code>
 * handles basic permission (except UmbrellaGrantPermission) granted
 * in the policy files properly.
 *
 * <b>Test Description</b><br><br>
 *
 *  This test is complex test using constructors and implies(), refresh(),
 *  getPermissions() methods of <code>PolicyFileProvider</code>.
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
 *         loaded classes into arrays.
 *    </li>
 *    <li> Create new PolicyFileProvider() using non-argument constructor.
 *    </li>
 *    <li> Iterate over ProtectionDomains, check implies() and
 *         check for permissions returned from
 *         PermissionCollection.elements():
 *      <ol>
 *        <li> Call implies() on PolicyFileProvider passing
 *             permissions that granted in the policy file. Verify that
 *             implies() returns false if ProtectionDomain is equal to null,
 *             and verify that implies() returns true for non-null
 *             ProtectionDomains.
 *        </li>
 *        <li> Call implies() on PolicyFileProvider passing
 *             not granted permissions. Verify that implies()
 *             returns false for null and non-null
 *             ProtectionDomains.
 *        </li>
 *        <li> For non-null ProtectionDomains that have
 *             PreferredClassLoader as ClassLoader
 *             call implies() on PolicyFileProvider passing
 *             permissions that granted to
 *             qa1-policy-provider.jar's codebase.
 *             Verify that implies() returns true.
 *        </li>
 *        <li> For non-null ProtectionDomains that have
 *             PreferredClassLoader as ClassLoader
 *             call implies() on PolicyFileProvider passing
 *             permissions that are not granted to
 *             qa1-policy-provider.jar's codebase.
 *             Verify that implies() returns false.
 *        </li>
 *        <li> Get CodeSource for ProtectionDomain.
 *        </li>
 *        <li> Call getPermissions() on PolicyFileProvider passing
 *             ProtectionDomain.
 *        </li>
 *        <li> Call getPermissions() on PolicyFileProvider passing
 *             CodeSource.
 *        </li>
 *        <li> Call implies() on returned PermissionCollections passing
 *             permissions that granted in the policy file. Verify that
 *             implies() returns true.
 *        </li>
 *        <li> Call implies() on returned PermissionCollections passing
 *             not granted permissions. Verify that implies()
 *             returns false.
 *        </li>
 *        <li> For ProtectionDomains that have
 *             PreferredClassLoader as ClassLoader
 *             call implies() on returned PermissionCollections passing
 *             permissions that granted to
 *             qa1-policy-provider.jar's codebase.
 *             Verify that implies() returns true.
 *        </li>
 *        <li> For ProtectionDomains that have
 *             PreferredClassLoader as ClassLoader
 *             call implies() on returned PermissionCollections passing
 *             permissions that are not granted to
 *             qa1-policy-provider.jar's codebase.
 *             Verify that implies() returns false.
 *        </li>
 *        <li> Verify that permissions that granted in the policy file
 *             are included in the Enumeration returned from
 *             PermissionCollection.elements() for permission collections
 *             returned from Policy.getPermissions(ProtectionDomain) and
 *             Policy.getPermissions(CodeSource).
 *        </li>
 *        <li> Verify that permissions that not granted in the policy file
 *             are not included in the Enumeration returned from
 *             PermissionCollection.elements() for permission collections
 *             returned from Policy.getPermissions(ProtectionDomain) and
 *             Policy.getPermissions(CodeSource).
 *        </li>
 *        <li> For ProtectionDomains that have
 *             PreferredClassLoader as ClassLoader
 *             verify that permissions that granted to
 *             qa1-policy-provider.jar's codebase
 *             are included in the Enumeration returned from
 *             PermissionCollection.elements() for permission collections
 *             returned from Policy.getPermissions(ProtectionDomain) and
 *             Policy.getPermissions(CodeSource).
 *        </li>
 *        <li> For ProtectionDomains that have
 *             PreferredClassLoader as ClassLoader
 *             verify that permissions that are not granted to
 *             qa1-policy-provider.jar's codebase
 *             are not included in the Enumeration returned from
 *             PermissionCollection.elements() for permission collections
 *             returned from Policy.getPermissions(ProtectionDomain) and
 *             Policy.getPermissions(CodeSource).
 *        </li>
 *      </ol>
 *    </li>
 *    <li> Reset java.security.policy to second policy file.
 *    </li>
 *    <li> Call refresh() on PolicyFileProvider.
 *    </li>
 *    <li> Again iterate over ProtectionDomains, check implies() and
 *         check for permissions returned from
 *         PermissionCollection.elements().
 *    </li>
 *    <li> Reset java.security.policy to first policy file.
 *    </li>
 *    <li> Create new PolicyFileProvider passing second policy file.
 *    </li>
 *    <li> Again iterate over ProtectionDomains, check implies() and
 *         check for permissions returned from
 *         PermissionCollection.elements().
 *    </li>
 *    <li> Call refresh() on PolicyFileProvider.
 *    </li>
 *    <li> Again iterate over ProtectionDomains, check implies() and
 *         check for permissions returned from
 *         PermissionCollection.elements().
 *    </li>
 * </ol>
 *
 */
public class BasicGrants extends PolicyFileProviderTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        Permission pm1 = new RuntimePermission("A");
        Permission pm2 = new RuntimePermission("B");
        Permission pm3 = new RuntimePermission("C");
        Permission pm4 = new RuntimePermission("D");
        Permission pm5 = new RuntimePermission("E");
        Permission pmX = new RuntimePermission("X");
        Permission pmY = new RuntimePermission("Y");
        Permission pmZ = new RuntimePermission("Z");
        Permission[][] pma0 = new Permission[IALL][];
        Permission[][] pma1 = new Permission[IALL][];
        pma0[IGRANTED] = new Permission[] { pm1, pm2 };
        pma0[INOTGRANTED] = new Permission[] { pm5, pm3, pm4 };
        pma0[ICODEBASEGRANTED] = new Permission[] { pmX, pmY, pmZ };
        pma0[ICODEBASENOTGRANTED] = null;
        pma1[IGRANTED] = new Permission[] { pm1 };
        pma1[INOTGRANTED] = new Permission[] { pm5, pm2, pm3, pm4 };
        pma1[ICODEBASEGRANTED] = new Permission[] { pmX, pmY };
        pma1[ICODEBASENOTGRANTED] = new Permission[] { pmZ };

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
         * Create new PolicyFileProvider using non-argument constructor.
         */
        createPolicyFileProvider();

        /*
         * Iterate over ProtectionDomains, check implies() and
         * check for permissions returned from
         * PermissionCollection.elements():
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
         * 5. Get CodeSource for ProtectionDomain.
         * 6. Call getPermissions() on PolicyFileProvider passing
         *    ProtectionDomain.
         * 7. Call getPermissions() on PolicyFileProvider passing
         *    CodeSource.
         * 8. Call implies() on returned PermissionCollections passing
         *    permissions that granted in the policy file. Verify that
         *    implies() returns true.
         * 9. Call implies() on returned PermissionCollections passing
         *    not granted permissions. Verify that implies()
         *    returns false.
         * 10. For ProtectionDomains that have
         *     PreferredClassLoader as ClassLoader
         *     call implies() on returned PermissionCollections passing
         *     permissions that granted to
         *     qa1-policy-provider.jar's codebase.
         *     Verify that implies() returns true.
         * 11. For ProtectionDomains that have
         *     PreferredClassLoader as ClassLoader
         *     call implies() on returned PermissionCollections passing
         *     permissions that are not granted to
         *     qa1-policy-provider.jar's codebase.
         *     Verify that implies() returns false.
         * 12. Verify that permissions that granted in the policy file
         *     are included in the Enumeration returned from
         *     PermissionCollection.elements() for permission collections
         *     returned from Policy.getPermissions(ProtectionDomain) and
         *     Policy.getPermissions(CodeSource)
         * 13. Verify that permissions that not granted in the policy file
         *     are not included in the Enumeration returned from
         *     PermissionCollection.elements() for permission collections
         *     returned from Policy.getPermissions(ProtectionDomain) and
         *     Policy.getPermissions(CodeSource)
         * 14. For ProtectionDomains that have
         *     PreferredClassLoader as ClassLoader
         *     verify that permissions that granted to
         *     qa1-policy-provider.jar's codebase
         *     are included in the Enumeration returned from
         *     PermissionCollection.elements() for permission collections
         *     returned from Policy.getPermissions(ProtectionDomain) and
         *     Policy.getPermissions(CodeSource)
         * 15. For ProtectionDomains that have
         *     PreferredClassLoader as ClassLoader
         *     verify that permissions that are not granted to
         *     qa1-policy-provider.jar's codebase
         *     are not included in the Enumeration returned from
         *     PermissionCollection.elements() for permission collections
         *     returned from Policy.getPermissions(ProtectionDomain) and
         *     Policy.getPermissions(CodeSource)
         */
        checkImpliesProtectionDomain(pma0);
        checkImpliesGetPermissions(pma0);
        checkElementsGetPermissions(pma0);

        /*
         * Reset java.security.policy to second policy file.
         */
        setPolicyFile("FILEPOLICY02");

        /*
         * Call refresh() on PolicyFileProvider.
         */
        logger.log(Level.FINE, "policy.refresh()");
        policy.refresh();

        /*
         * Again iterate over ProtectionDomains, check implies() and
         * check for permissions returned from
         * PermissionCollection.elements().
         */
        checkImpliesProtectionDomain(pma1);
        checkImpliesGetPermissions(pma1);
        checkElementsGetPermissions(pma1);

        /*
         * Reset java.security.policy to first policy file.
         */
        setPolicyFile("FILEPOLICY01");

        /*
         * Create new PolicyFileProvider passing second policy file.
         */
        createPolicyFileProvider("FILEPOLICY02");

        /*
         * Again iterate over ProtectionDomains, check implies() and
         * check for permissions returned from
         * PermissionCollection.elements().
         */
        checkImpliesProtectionDomain(pma1);
        checkImpliesGetPermissions(pma1);
        checkElementsGetPermissions(pma1);

        /*
         * Call refresh() on PolicyFileProvider.
         */
        logger.log(Level.FINE, "policy.refresh()");
        policy.refresh();

        /*
         * Again iterate over ProtectionDomains, check implies() and
         * check for permissions returned from
         * PermissionCollection.elements().
         */
        checkImpliesProtectionDomain(pma1);
        checkImpliesGetPermissions(pma1);
        checkElementsGetPermissions(pma1);
    }
}
