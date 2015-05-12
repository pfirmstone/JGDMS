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
/* @test
 * @summary Verify that AggregatePolicyProvider handles null inputs properly
 * @run main/othervm/policy=policy Test
 */
package org.apache.river.test.impl.start.aggregatepolicyprovider;

import org.apache.river.start.AggregatePolicyProvider;
import java.security.*;
import net.jini.security.policy.*;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

public class NullCasesTest extends QATestEnvironment implements Test {
    public void run() throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	AggregatePolicyProvider policy = new AggregatePolicyProvider();

	try {
	    policy.setPolicy(null, null);
	    throw new TestException("Successfully set null policy.");
	} catch (NullPointerException e) {
	}
	policy.setPolicy(null, new DynamicPolicyProvider());
	policy.setPolicy(NullCasesTest.class.getClassLoader(), null);

	policy.grant(null, null, null);
	try {
	    policy.grant(null, new Principal[]{ null }, null);
	    throw new TestException("Successfully granted: "
                    + "null, new Principal[]{ null }, null");
	} catch (NullPointerException e) {
	}
	try {
	    policy.grant(null, null, new Permission[]{ null });
	    throw new TestException("Successfully granted: "
                    + "null, null, new Permission[]{ null }");
	} catch (NullPointerException e) {
	}
	policy.getGrants(null, null);
	try {
	    policy.getGrants(null, new Principal[]{ null });
	    throw new TestException("Successfully called "
                    + "getGrants(null, new Principal[]{ null })");
	} catch (NullPointerException e) {
	}
	PermissionCollection pc = 
	    policy.getPermissions((ProtectionDomain) null);
	if (pc.elements().hasMoreElements()) {
	    throw new TestException(
                    "permissions returned for null protection domain");
	}
	if (policy.implies(null, new RuntimePermission("foo"))) {
	    throw new TestException(
                    "policy implies(null, new RuntimePermission(\"foo\"))");
	}
    }
}
