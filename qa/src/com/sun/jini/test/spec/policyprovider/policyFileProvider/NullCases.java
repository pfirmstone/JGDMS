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

// java.security
import java.security.Permission;
import java.security.CodeSource;
import java.security.ProtectionDomain;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>PolicyFileProvider</code>
 * handles <code>null</code> inputs properly.
 *
 * <b>Test Description</b><br><br>
 *
 *  This test iterates over a set of <code>PolicyFileProvider</code>
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
 *    <li> construct PolicyFileProvider object using non-argument
 *         constructor.
 *    </li>
 *    <li> try to construct PolicyFileProvider object passing null as
 *         location of policy file and verify that NullPointerException is
 *         thrown.
 *    </li>
 *    <li> call getPermissions() passing null as ProtectionDomain
 *         and verify that NullPointerException is not thrown; also
 *         verify that returned PermissionCollection does not contain
 *         any element.
 *    </li>
 *    <li> call getPermissions() passing null as CodeSource
 *         and verify that NullPointerException is thrown.
 *    </li>
 *    <li> call implies() passing null as ProtectionDomain and null as
 *         Permission and verify that NullPointerException is thrown.
 *    </li>
 *    <li> verify that policy.implies(null, p) passing permission
 *         granted in a policy file returned false.
 *    </li>
 *    <li> verify that policy.implies(null, p) passing permission
 *         dynamic granted in a policy file returned false.
 *    </li>
 *    <li> verify that policy.implies(null, p) passing permission
 *         not granted in a policy file returned false.
 *    </li>
 * </ol>
 *
 */
public class NullCases extends PolicyFileProviderTestBase {

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

        /*
         * Create new PolicyFileProvider().
         */
        createPolicyFileProvider();

        /*
         * Call new PolicyFileProvider(null) and
         * verify that NullPointerException is thrown.
         */
        createPolicyFileProviderNPE("new PolicyFileProvider(null)");

        /*
         * Call getPermissions() passing null as ProtectionDomain
         * and verify that NullPointerException is not thrown;
         * also verify that returned collection does not contain
         * any element.
         */
        callGetPermissionsNPD();

        /*
         * Call getPermissions() passing null as CodeSource
         * and verify that NullPointerException is thrown;
         */
        msg = "policy.getPermissions((CodeSource) null)";
        callGetPermissionsNPE((CodeSource) null, msg);

        /*
         * Call policy.implies(null, null)
         * and verify that NullPointerException is thrown;
         */
        msg = "policy.implies(null, null)";
        callImpliesNPE(null, null, msg);

        /*
         * Verify that policy.implies(null, p) passing permission
         * granted in a policy file returned false.
         */
        msg = "policy.implies(null, <p granted in a policy file>)";
        callImplies((ProtectionDomain) null, pm1, false, msg);
        callImplies((ProtectionDomain) null, pm2, false, msg);

        /*
         * Verify that policy.implies(null, p) passing permission
         * dynamic granted in a policy file returned false.
         */
        msg = "policy.implies(null, <p dynamic granted in a policy file>)";
        callImplies((ProtectionDomain) null, pm3, false, msg);
        callImplies((ProtectionDomain) null, pm4, false, msg);

        /*
         * Verify that policy.implies(null, p) passing permission
         * not granted in a policy file returned false.
         */
        msg = "policy.implies(null, <p not granted in a policy file>)";
        callImplies((ProtectionDomain) null, pm5, false, msg);
        callImplies((ProtectionDomain) null, pm6, false, msg);
    }
}
