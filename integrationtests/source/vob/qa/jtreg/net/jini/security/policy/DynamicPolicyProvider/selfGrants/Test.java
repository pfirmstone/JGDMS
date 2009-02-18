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
 * @summary Test basic permission grants (to self with no principals, for
 *          simplicity)
 * @run main/othervm/policy=policy.0 Test
 */

import java.io.File;
import java.security.*;
import net.jini.security.policy.DynamicPolicyProvider;

public class Test {

    static SecurityManager sm;

    public static void main(String[] args) throws Exception {
	if ((sm = System.getSecurityManager()) == null) {
	    System.setSecurityManager(sm = new SecurityManager());
	}
	DynamicPolicyProvider policy = new DynamicPolicyProvider();
	Policy.setPolicy(policy);

	sm.checkPermission(new RuntimePermission("A"));
	checkPermissionFail(new RuntimePermission("B"));
	checkPermissionFail(new RuntimePermission("C"));

	try {
	    policy.grant(Test.class, null,
			 new Permission[]{
			     new RuntimePermission("B"),
			     new RuntimePermission("C")
			 });
	    throw new Error("grant of B, C should not succeed");
	} catch (SecurityException e) {
	}

	sm.checkPermission(new RuntimePermission("A"));
	checkPermissionFail(new RuntimePermission("B"));
	checkPermissionFail(new RuntimePermission("C"));
	checkPermissionFail(new RuntimePermission("D"));

	policy.grant(Test.class, null, 
		     new Permission[]{ new RuntimePermission("B") });
	try {
	    policy.grant(Test.class, null,
			 new Permission[]{ new RuntimePermission("C") });
	    throw new Error("grant of C should not succeed");
	} catch (SecurityException e) {
	}

	sm.checkPermission(new RuntimePermission("A"));
	sm.checkPermission(new RuntimePermission("B"));
	checkPermissionFail(new RuntimePermission("C"));
	checkPermissionFail(new RuntimePermission("D"));

	System.setProperty("java.security.policy",
	    System.getProperty("test.src", ".") + File.separator + "policy.1");
	policy.refresh();

	checkPermissionFail(new RuntimePermission("A"));
	sm.checkPermission(new RuntimePermission("B"));
	checkPermissionFail(new RuntimePermission("C"));
	sm.checkPermission(new RuntimePermission("D"));

	try {
	    policy.grant(Test.class, null,
			 new Permission[]{ new RuntimePermission("A") });
	    throw new Error("grant of A should not succeed");
	} catch (SecurityException e) {
	}
	policy.grant(Test.class, null, 
		     new Permission[]{ new RuntimePermission("C") });

	checkPermissionFail(new RuntimePermission("A"));
	sm.checkPermission(new RuntimePermission("B"));
	sm.checkPermission(new RuntimePermission("C"));
	sm.checkPermission(new RuntimePermission("D"));
    }

    static void checkPermissionFail(Permission p) {
	try {
	    sm.checkPermission(p);
	    throw new Error("checkPermission incorrectly passed for " + p);
	} catch (SecurityException e) {
	}
    }
}
