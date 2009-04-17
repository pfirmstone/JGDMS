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

// utility classes
import com.sun.jini.test.spec.policyprovider.util.Util;
import com.sun.jini.test.spec.policyprovider.util.QAPrincipal;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>DynamicPolicyProvider</code>
 * handles <code>null</code> inputs properly.
 *
 * <b>Test Description</b><br><br>
 *
 *  This test iterates over a set of <code>DynamicPolicyProvider</code>
 *  methods passing <code>null</code> inputs and verifies that
 *  NullPointerException is or is not thrown.
 *
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ul><lh>This test requires the following infrastructure:</lh>
 *  <li> infrastructure is not required</li>
 * </ul>
 *
 * <b>Actions</b><br><br>
 * <ol>
 *    <li> construct DynamicPolicyProvider object using non-argument
 *         constructor.
 *    </li>
 *    <li> try to construct DynamicPolicyProvider object passing null as
 *         basePolicy argument and verify that NullPointerException is thrown.
 *    </li>
 *    <li> call grant(null, null, null) on constructed DynamicPolicyProvider
 *         and verify that NullPointerException is not thrown.
 *    </li>
 *    <li> call grant(null, null, Permission[]) on constructed
 *         DynamicPolicyProvider passing array of permission
 *         and verify that NullPointerException is not thrown.
 *         This granted permissions will be used in some checks
 *         described later on.
 *    </li>
 *    <li> some times call grant() passing various array of Principal
 *         that contains <code>null</code> and verify that NullPointerExceptions
 *         are thrown.
 *    </li>
 *    <li> some times call grant() passing various array of Permission
 *         that contains <code>null</code> and verify that NullPointerExceptions
 *         are thrown.
 *    </li>
 *    <li> call getGrants(null, null) on constructed DynamicPolicyProvider
 *         and verify that NullPointerException is not thrown. Also verify that
 *         returned Permission[] contains permissions granted earlier.
 *    </li>
 *    <li> some times call getGrants() passing various array of Principal
 *         that contains <code>null</code> and verify that
 *         NullPointerExceptions are thrown.
 *    </li>
 *    <li> call getPermissions() passing null as ProtectionDomain
 *         and verify that returned PermissionCollection contains permissions
 *         granted earlier.
 *    </li>
 *    <li> call getPermissions() passing null as CodeSource
 *         and verify that NullPointerException is thrown.
 *    </li>
 *    <li> call implies() passing null as ProtectionDomain and null as
 *         Permission and verify that NullPointerException is thrown.
 *    </li>
 *    <li> verify that policy.implies(null, p) passing permission
 *         granted earlier returned true.
 *    </li>
 *    <li> verify that policy.implies(null, p) passing permission
 *         not granted earlier returned false.
 *    </li>
 * </ol>
 *
 */
public class NullCases extends DynamicPolicyProviderTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        Principal[] pra = null;
        Permission[] pma = null;
        QAPrincipal pr1 = new QAPrincipal("1");
        QAPrincipal pr2 = new QAPrincipal("1");
        QAPrincipal pr3 = new QAPrincipal("2");
        Permission pm1 = new FilePermission("1", "read");
        Permission pm2 = new FilePermission("1", "read");
        Permission pm3 = new FilePermission("2", "read");
        Permission pm4 = new FilePermission("4", "read");
        Permission[] pmGranted = new Permission[] { pm1, pm2, pm3 };

        // Create new DynamicPolicyProvider()
        createDynamicPolicyProvider();

        // Call new DynamicPolicyProvider(null) and
        // verify that NullPointerException is thrown.
        createDynamicPolicyProviderNPE("new DynamicPolicyProvider(null)");

        // Call policy.grant(null, null, null) and
        // verify that NullPointerException is not thrown.
        msg = "policy.grant(null, null, null)";
        callGrant(null, null, null, msg);

        // Call policy.grant(null, null, Permission[]) and
        // verify that NullPointerException is not thrown.
        msg = "policy.grant(null, null, Permission[])";
        callGrant(null, null, pmGranted, msg);

        // Some times call grant() passing various array of Principal
        // that contains null and verify that
        // NullPointerExceptions are thrown.
        msg = "policy.grant(null, new Principal[] {..., null,... }, null)";
        pra = new Principal[] { null };
        callGrantNPE(null, pra, null, msg);
        pra = new Principal[] { null, pr1, pr2, null, pr3 };
        callGrantNPE(null, pra, null, msg);
        pra = new Principal[] { pr1, pr2, pr3, null, pr3 };
        callGrantNPE(null, pra, null, msg);
        pra = new Principal[] { pr1, pr2, pr3, pr3, null };
        callGrantNPE(null, pra, null, msg);

        // some times call grant() passing various array of Permission
        // that contains null and verify that NullPointerExceptions
        // are thrown.
        msg = "policy.grant(null, null, new Permission[] {..., null,... })";
        pma = new Permission[] { null };
        callGrantNPE(null, null, pma, msg);
        pma = new Permission[] { null, pm1, pm2, pm3 };
        callGrantNPE(null, null, pma, msg);
        pma = new Permission[] { pm1, null, pm2, pm3 };
        callGrantNPE(null, null, pma, msg);
        pma = new Permission[] { pm1, null, pm2, pm3, null };
        callGrantNPE(null, null, pma, msg);

        // Call policy.getGrants(null, null) and
        // verify that NullPointerException is not thrown;
        // also verify that returned array contains Permissions granted
        // earlier.
        msg = "policy.getGrants(null, null)";
        callGetGrants(null, null, pmGranted, msg);

        // Some times call getGrants() passing various array of Principal
        // that contains null and verify that
        // NullPointerExceptions are thrown.
        msg = "policy.getGrants(null, new Principal[] {..., null,... })";
        pra = new Principal[] { null };
        callGetGrantsNPE(null, pra, msg);
        pra = new Principal[] { null, pr1, pr2, null, pr3 };
        callGetGrantsNPE(null, pra, msg);
        pra = new Principal[] { pr1, pr2, pr3, null, pr3 };
        callGetGrantsNPE(null, pra, msg);
        pra = new Principal[] { pr1, pr2, pr3, pr3, null };
        callGetGrantsNPE(null, pra, msg);

        // Call getPermissions() passing null as ProtectionDomain
        // and verify that NullPointerException is not thrown;
        // also verify that returned array contains Permissions granted
        // earlier.
        msg = "policy.getPermissions((ProtectionDomain) null)";
        callGetPermissions((ProtectionDomain) null, pmGranted, msg);

        // Call getPermissions() passing null as CodeSource
        // and verify that NullPointerException is thrown;
        msg = "policy.getPermissions((CodeSource) null)";
        callGetPermissionsNPE((CodeSource) null, msg);

        // Call policy.implies(null, null)
        // and verify that NullPointerException is thrown;
        msg = "policy.implies(null, null)";
        callImpliesNPE(null, null, msg);

        // Verify that policy.implies(null, p) passing permission
        // granted earlier returned true.
        msg = "policy.implies(null, <p granted earlier>)";
        callImplies(null, pmGranted[0], true, msg);

        // Verify that policy.implies(null, p) passing permission
        // not granted earlier returned false.
        msg = "policy.implies(null, <p not granted earlier>)";
        callImplies(null, pm4, false, msg);
    }
}
