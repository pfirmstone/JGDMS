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


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>SecurityException</code> is thrown if
 * DynamicPolicyProvider's constructor does not have permissions to read the
 * <code>net.jini.security.policy.DynamicPolicyProvider.basePolicyClass</code>
 * security property or does not have
 * <code>accessClassInPackage.sun.security.provider<code> RuntimePermission.
 *
 *
 * <b>Test Description</b><br><br>
 *
 * This test call DynamicPolicyProvider() constructor
 * and verifies that SecurityException is thrown.
 * <ol><lh>This test should be run with the next policy files:</lh>
 *  <li>
 *    policy file that does not allow
 *    getProperty for net.jini.security.policy.*
 *    but allow accessClassInPackage RuntimePermission
 *    for sun.security.provider
 *  </li>
 *  <li>
 *    policy file that does not allow
 *    accessClassInPackage for sun.security.provider
 *    but allow SecurityPermission for getProperty for
 *    net.jini.security.policy.*
 *  </li>
 * </ol>
 *
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ul><lh>This test requires the following infrastructure:</lh>
 *  <li>
 *       policy.policyProviderNoGetProperty policy file that does not allow
 *       getProperty for net.jini.security.policy.*
 *  </li>
 *  <li>
 *       policy.policyProviderNoAccessClass policy file that does not allow
 *       accessClassInPackage for sun.security.provider
 *  </li>
 * </ul>
 *
 * <b>Actions</b><br><br>
 * <ol>
 *    <li> construct DynamicPolicyProvider object using non-argument
 *         constructor and verify that SecurityException is thrown.
 *    </li>
 * </ol>
 *
 */
public class SecurityExceptionConstructor
        extends DynamicPolicyProviderTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        createDynamicPolicyProviderSE("new DynamicPolicyProvider()");
    }
}
