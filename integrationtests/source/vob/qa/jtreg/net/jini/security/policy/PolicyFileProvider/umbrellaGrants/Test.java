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
 * @summary Test PolicyFileProvider expansion of UmbrellaGrantPermissions
 * @run main/othervm/policy=policy Test
 */

import java.net.URL;
import java.security.*;
import java.security.cert.Certificate;
import net.jini.security.GrantPermission;
import net.jini.security.policy.PolicyFileProvider;

public class Test {

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	Policy policy = new PolicyFileProvider();

	checkPermissions(policy, "file:/foo.jar",
	    new Permission[]{
		new GrantPermission(new RuntimePermission("A")),
		new GrantPermission(new RuntimePermission("B")),
		new GrantPermission(new RuntimePermission("C"))
	    },
	    new Permission[]{
		new GrantPermission(new RuntimePermission("D"))
	    });
	checkPermissions(policy, "file:/bar.jar",
	    new Permission[]{
		new GrantPermission(new RuntimePermission("C"))
	    },
	    new Permission[]{
		new GrantPermission(new RuntimePermission("A")),
		new GrantPermission(new RuntimePermission("B")),
		new GrantPermission(new RuntimePermission("D"))
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
