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

/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>PolicyFileProvider</code>
 * handles <code>null java.security.policy</code> property.
 *
 * <b>Test Description</b><br><br>
 *
 * This test ensures that PolicyFileProvider construction succeeds
 * even when no <code>java.security.policy</code> is set, but a
 * new policy file is set in the constructor. Additionally, policy
 * refresh for the created instance must also succeed.
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
 *    <li> Explicitly remove the <code>java.security.policy</code> property.
 *    <li> construct PolicyFileProvider instance using <code>String</code>
 *         constructor.
 *    </li>
 *    <li> invoke <code>refresh</code> method of the created instance.
 * </ol>
 *
 */
public class NullPolicy extends PolicyFileProviderTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {

	System.getProperties().remove(SECURITYPOLICY);
        logger.log(Level.FINEST, "removed " + SECURITYPOLICY);
        /*
         * Create new PolicyFileProvider using FILEPOLICY01 policy file
         */
        createPolicyFileProvider("FILEPOLICY01");
        /*
         * Call refresh() on PolicyFileProvider.
         */
        logger.log(Level.FINE, "policy.refresh()");
        policy.refresh();
    }
}
