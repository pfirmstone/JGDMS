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
package org.apache.river.test.spec.security.security;

import java.util.logging.Level;

// java
import java.security.Policy;

// net.jini
import net.jini.security.Security;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.spec.security.util.BasePolicyProvider;
import org.apache.river.test.spec.security.util.TestDynamicPolicyProvider;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     'grantSupported' static method of Security class returns true if the
 *     installed security policy provider supports dynamic permission grants -
 *     i.e., if it implements the DynamicPolicy interface and calling its
 *     'grantSupported' method returns true. Returns false otherwise.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     NonDynamicPolicyProvider - policy provider which does not implement
 *             DynamicPolicy interface
 *     TestDynamicPolicyProvider - policy provider implementing DynamicPolicy
 *             interface whose 'grantSupported' method returns false/true
 *             depending on value set by 'setGrantSupported(boolean)' method.
 *             This provider is used to verify that grantSupported values
 *             aren't cached per-provider
 *
 * Action
 *   The test performs the following steps:
 *     1) set current policy provider to NonDynamicPolicyProvider
 *     2) invoke 'grantSupported' static method of Security class
 *     3) assert that false will be returned
 *     4) set current policy provider to TestDynamicPolicyProvider
 *     5) set value returned by 'grantSupported' method of
 *        TestDynamicPolicyProvider to false
 *     6) invoke 'grantSupported' static method of Security class
 *     7) assert that 'grantSupported' method of TestDynamicPolicyProvider will
 *        be invoked
 *     8) assert that false will be returned
 *     9) set value returned by 'grantSupported' method of
 *        TestDynamicPolicyProvider to true
 *     10) invoke 'grantSupported' static method of Security class
 *     11) assert that 'grantSupported' method of TestDynamicPolicyProvider will
 *         be invoked
 *     12) assert that true will be returned
 * </pre>
 */
public class GrantSupportedTest extends QATestEnvironment implements Test {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        Policy.setPolicy(new BasePolicyProvider());
        logger.fine("Policy provider is not an instance of DynamicPolicy.");
        boolean res = Security.grantSupported();

        if (res) {
            // FAIL
            throw new TestException(
                    "Called 'Security.grantSupported()' method returned "
                    + "true while false was expected.");
        }

        // PASS
        logger.fine("Called 'Security.grantSupported()' method returned "
                + "false as expected.");
        logger.fine("Set policy provider to TestDynamicPolicyProvider "
                + "with value returned by 'grantSupported' method set "
                + "to false.");
        TestDynamicPolicyProvider policy = new TestDynamicPolicyProvider();
        policy.setGrantSupported(false);
        Policy.setPolicy(policy);
        res = Security.grantSupported();

        if (res) {
            // FAIL
            throw new TestException(
                    "Called 'Security.grantSupported()' method returned "
                    + "true while false was expected.");
        }

        // PASS
        logger.fine("Called 'Security.grantSupported()' method returned "
                + "false as expected.");

        if (policy.getGrantSupNum() != 1) {
            // FAIL
            throw new TestException(
                    "'grantSupported()' method of installed policy "
                    + "provider was called " + policy.getGrantSupNum()
                    + " times while 1 call was expected.");
        }

        // PASS
        logger.fine("'grantSupported()' method of installed policy "
                + "provider was called as expected.");
        logger.fine("Set value returned by 'grantSupported' method "
                + "to true.");
        policy.setGrantSupported(true);
        res = Security.grantSupported();

        if (!res) {
            // FAIL
            throw new TestException(
                    "Called 'Security.grantSupported()' method returned "
                    + "false while true was expected.");
        }

        // PASS
        logger.fine("Called 'Security.grantSupported()' method returned "
                + "true as expected.");

        if (policy.getGrantSupNum() != 2) {
            // FAIL
            throw new TestException(
                    "'grantSupported()' method of installed policy "
                    + "provider was called " + (policy.getGrantSupNum() - 1)
                    + " times while 1 call was expected.");
        }

        // PASS
        logger.fine("'grantSupported()' method of installed policy "
                + "provider was called as expected.");
    }
}
