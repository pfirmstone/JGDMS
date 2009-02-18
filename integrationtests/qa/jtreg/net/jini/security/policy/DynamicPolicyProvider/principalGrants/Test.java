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
 * @summary Verify proper functioning of principal-based grants.
 * @library ../../../../../../testlibrary
 * @build Test StringPrincipal Foo
 * @run main/othervm/policy=policy Test
 */

import java.net.*;
import java.security.*;
import java.util.*;
import net.jini.security.policy.DynamicPolicyProvider;

/*
 * Permission key:
 *
 * RuntimePermission("all")    -- granted to all code
 * RuntimePermission("Xall")   -- granted to all code, principal X
 * RuntimePermission("XYall")  -- granted to all code, principals X, Y
 * RuntimePermission("XYZall") -- granted to all code, principals X, Y, Z
 * RuntimePermission("1")      -- granted to cl1
 * RuntimePermission("X1")     -- granted to cl1, principal X
 * RuntimePermission("XY1")    -- granted to cl1, principals X, Y
 * RuntimePermission("XYZ1")   -- granted to cl1, principals X, Y, Z
 * RuntimePermission("2")      -- granted to cl2
 * RuntimePermission("X2")     -- granted to cl2, principal X
 * RuntimePermission("XY2")    -- granted to cl2, principals X, Y
 * RuntimePermission("XYZ2")   -- granted to cl2, principals X, Y, Z
 */
public class Test {

    static DynamicPolicyProvider policy;
    static StringPrincipal[] principals = {
	new StringPrincipal("X"),
	new StringPrincipal("Y"),
	new StringPrincipal("Z")
    };

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	policy = new DynamicPolicyProvider();

	ClassLoader ldr1 = new URLClassLoader(new URL[]{
	    TestLibrary.installClassInCodebase("Foo", "cb1", false)});
	ClassLoader ldr2 = new URLClassLoader(new URL[]{
	    TestLibrary.installClassInCodebase("Foo", "cb2")});
	Class cl1 = Class.forName("Foo", true, ldr1);
	Class cl2 = Class.forName("Foo", true, ldr2);

	checkPermissions(null,
			 new String[0],
			 new String[]{ "all", "1", "2" });
	checkPermissions(cl1,
			 new String[0],
			 new String[]{ "all", "1", "2" });
	checkPermissions(cl2,
			 new String[0],
			 new String[]{ "all", "1", "2" });
	checkGetGrants(null,
		       new String[0],
		       new String[]{ "all", "1", "2" });
	checkGetGrants(cl1,
		       new String[0],
		       new String[]{ "all", "1", "2" });
	checkGetGrants(cl2,
		       new String[0],
		       new String[]{ "all", "1", "2" });

	grantPermissions(null, "all");
	grantPermissions(cl1, "1");
	grantPermissions(cl2, "2");

	checkPermissions(null,
			 new String[]{ "all" },
			 new String[]{ "1", "2" });
	checkPermissions(cl1,
			 new String[]{ "all", "1" },
			 new String[]{ "2" });
	checkPermissions(cl2,
			 new String[]{ "all", "2" },
			 new String[]{ "1" });
	checkGetGrants(null,
		       new String[]{ "all" },
		       new String[]{ "1", "2" });
	checkGetGrants(cl1,
		       new String[]{ "all", "1" },
		       new String[]{ "2" });
	checkGetGrants(cl2,
		       new String[]{ "all", "2" },
		       new String[]{ "1" });
    }

    static void grantPermissions(Class cl, String postfix) {
	for (int i = 0; i < principals.length; i++) {
	    Principal[] pra = new Principal[i];
	    System.arraycopy(principals, 0, pra, 0, i);
	    policy.grant(cl, pra, new Permission[]{ 
		new RuntimePermission(getPermissionName(i, postfix)) });
	}
    }

    static void checkPermissions(Class cl,
				 String[] passPostfix,
				 String[] failPostfix)
    {
	ClassLoader ldr = (cl != null) ? cl.getClassLoader() : null;

	for (int i = 0; i < principals.length; i++) {
	    Principal[] pra = new Principal[i];
	    System.arraycopy(principals, 0, pra, 0, i);
	    ProtectionDomain pd = new ProtectionDomain(null, null, ldr, pra);

	    for (int j = 0; j < principals.length; j++) {
		for (int k = 0; k < passPostfix.length; k++) {
		    Permission p = new RuntimePermission(
			getPermissionName(j, passPostfix[k]));
		    if (policy.implies(pd, p) != (j <= i)) {
			throw new Error();
		    }
		}
		for (int k = 0; k < failPostfix.length; k++) {
		    Permission p = new RuntimePermission(
			getPermissionName(j, failPostfix[k]));
		    if (policy.implies(pd, p)) {
			System.out.println("ldr: " + ldr);
			System.out.println("pra: " + Arrays.asList(pra));
			System.out.println("p: " + p);
			throw new Error();
		    }
		}
	    }
	}
    }

    static void checkGetGrants(Class cl,
			       String[] passPostfix,
			       String[] failPostfix)
    {
	for (int i = 0; i < principals.length; i++) {
	    Principal[] pra = new Principal[i];
	    System.arraycopy(principals, 0, pra, 0, i);
	    Set ps = new HashSet(Arrays.asList(policy.getGrants(cl, pra)));

	    for (int j = 0; j < principals.length; j++) {
		for (int k = 0; k < passPostfix.length; k++) {
		    Permission p = new RuntimePermission(
			getPermissionName(j, passPostfix[k]));
		    if (ps.contains(p) != (j <= i)) {
			System.out.println("pra: " + Arrays.asList(pra));
			System.out.println("p: " + p);
			System.out.println("ps: " + ps);
			throw new Error();
		    }
		}
		for (int k = 0; k < failPostfix.length; k++) {
		    Permission p = new RuntimePermission(
			getPermissionName(j, failPostfix[k]));
		    if (ps.contains(p)) {
			throw new Error();
		    }
		}
	    }
	}
    }

    static String getPermissionName(int topPrincipalIndex, String postfix) {
	StringBuffer sb = new StringBuffer();
	for (int i = 0; i < topPrincipalIndex; i++) {
	    sb.append(principals[i].getName());
	}
	return sb + postfix;
    }
}
