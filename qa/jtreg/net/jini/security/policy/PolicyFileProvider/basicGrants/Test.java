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
 * @summary Verify that PolicyFileProvider correctly handles basic permission
 *          grants
 * @run main/othervm/policy=policy.0 Test
 */

import java.io.File;
import java.net.URL;
import java.security.*;
import java.security.cert.Certificate;
import net.jini.security.policy.PolicyFileProvider;

public class Test {
    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	String policy0File =
	    System.getProperty("test.src", ".") + File.separator + "policy.0";
	String policy1File =
	    System.getProperty("test.src", ".") + File.separator + "policy.1";

	Policy policy = new PolicyFileProvider();
	checkPolicy0Permissions(policy);
	System.setProperty("java.security.policy", policy1File);
	policy.refresh();
	checkPolicy1Permissions(policy);

	System.setProperty("java.security.policy", policy0File);
	policy = new PolicyFileProvider(policy1File);
	checkPolicy1Permissions(policy);
	policy.refresh();
	checkPolicy1Permissions(policy);
    }

    static void checkPolicy0Permissions(Policy policy) throws Exception {
	checkPermissions(policy, "file:/foo/bar",
			 new Permission[]{
			     new RuntimePermission("A0"),
			     new RuntimePermission("B0"),
			     new RuntimePermission("C0")
			 },
			 new Permission[]{
			     new RuntimePermission("D0"),
			     new RuntimePermission("A1")
			 });
	checkPermissions(policy, "file:/foo/bar/baz",
			 new Permission[]{
			     new RuntimePermission("A0"),
			     new RuntimePermission("C0")
			 },
			 new Permission[]{
			     new RuntimePermission("B0"),
			     new RuntimePermission("D0"),
			     new RuntimePermission("A1")
			 });
	checkPermissions(policy, "file:/bar.jar",
			 new Permission[]{
			     new RuntimePermission("A0"),
			     new RuntimePermission("D0")
			 },
			 new Permission[]{
			     new RuntimePermission("B0"),
			     new RuntimePermission("C0"),
			     new RuntimePermission("A1")
			 });
	checkPermissions(policy, "file:/other",
			 new Permission[]{
			     new RuntimePermission("A0"),
			 },
			 new Permission[]{
			     new RuntimePermission("B0"),
			     new RuntimePermission("C0"),
			     new RuntimePermission("D0"),
			     new RuntimePermission("A1")
			 });
    }

    static void checkPolicy1Permissions(Policy policy) throws Exception {
	checkPermissions(policy, "file:/foo/bar",
			 new Permission[]{
			     new RuntimePermission("A1"),
			     new RuntimePermission("B1"),
			     new RuntimePermission("C1")
			 },
			 new Permission[]{
			     new RuntimePermission("D1"),
			     new RuntimePermission("A0")
			 });
	checkPermissions(policy, "file:/foo/bar/baz",
			 new Permission[]{
			     new RuntimePermission("A1"),
			     new RuntimePermission("C1")
			 },
			 new Permission[]{
			     new RuntimePermission("B1"),
			     new RuntimePermission("D1"),
			     new RuntimePermission("A0")
			 });
	checkPermissions(policy, "file:/bar.jar",
			 new Permission[]{
			     new RuntimePermission("A1"),
			     new RuntimePermission("D1")
			 },
			 new Permission[]{
			     new RuntimePermission("B1"),
			     new RuntimePermission("C1"),
			     new RuntimePermission("A0")
			 });
	checkPermissions(policy, "file:/other",
			 new Permission[]{
			     new RuntimePermission("A1"),
			 },
			 new Permission[]{
			     new RuntimePermission("B1"),
			     new RuntimePermission("C1"),
			     new RuntimePermission("D1"),
			     new RuntimePermission("A0")
			 });
    }

    static void checkPermissions(Policy policy,
				 String codebase,
				 Permission[] pass,
				 Permission[] fail)
	throws Exception
    {
	CodeSource cs =
	    new CodeSource(new URL(codebase), (Certificate[]) null);
	PermissionCollection pc = policy.getPermissions(cs);
	for (int i = 0; i < pass.length; i++) {
	    if (!pc.implies(pass[i])) {
		throw new Error(pass[i] + " not implied by " + cs);
	    }
	}
	for (int i = 0; i < fail.length; i++) {
	    if (pc.implies(fail[i])) {
		throw new Error(fail[i] + " implied by " + cs);
	    }
	}

	ProtectionDomain pd = new ProtectionDomain(cs, null, null, null);
	pc = policy.getPermissions(pd);
	for (int i = 0; i < pass.length; i++) {
	    if (!pc.implies(pass[i])) {
		throw new Error(pass[i] + " not implied by " + cs);
	    }
	}
	for (int i = 0; i < fail.length; i++) {
	    if (pc.implies(fail[i])) {
		throw new Error(fail[i] + " implied by " + cs);
	    }
	}

	for (int i = 0; i < pass.length; i++) {
	    if (!policy.implies(pd, pass[i])) {
		throw new Error(pass[i] + " not implied by " + cs);
	    }
	}
	for (int i = 0; i < fail.length; i++) {
	    if (policy.implies(pd, fail[i])) {
		throw new Error(fail[i] + " implied by " + cs);
	    }
	}
    }
}
