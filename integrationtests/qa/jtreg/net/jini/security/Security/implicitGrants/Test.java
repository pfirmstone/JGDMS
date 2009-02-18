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
 * @summary Verify proper basic functionality of Security.grant(Class, Class)
 * @library ../../../../../testlibrary
 * @build Test Setup StringPrincipal Foo
 * @run main/othervm/policy=policy Test
 */

import java.net.*;
import java.security.*;
import java.util.*;
import javax.security.auth.Subject;
import net.jini.security.Security;
import net.jini.security.policy.DynamicPolicyProvider;

public class Test {

    public static Class cl1, cl2;
    public static Permission pA = new RuntimePermission("A"),
			     pB = new RuntimePermission("B"),
			     pC = new RuntimePermission("C");

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	final Policy policy = new DynamicPolicyProvider();
	Policy.setPolicy(policy);

	ClassLoader ldr1 = new URLClassLoader(new URL[]{
	    TestLibrary.installClassInCodebase("Foo", "cb1", false)});
	ClassLoader ldr2 = new URLClassLoader(new URL[]{
	    TestLibrary.installClassInCodebase("Foo", "cb2")});
	cl1 = Class.forName("Foo", true, ldr1);
	cl2 = Class.forName("Foo", true, ldr2);

	ClassLoader ldr3 = new URLClassLoader(new URL[]{
	    TestLibrary.installClassInCodebase("Setup", "cb3")});
	TestLibrary.installClassInCodebase("Setup$Action", "cb3");
	((Runnable) Class.forName("Setup", true, ldr3).newInstance()).run();

	ProtectionDomain pd1 = cl1.getProtectionDomain();
	if (!(policy.implies(pd1, pA) &&
	      policy.implies(pd1, pB) &&
	      policy.implies(pd1, pC)))
	{
	    throw new Error();
	}
	ProtectionDomain pd2 = cl2.getProtectionDomain();
	if (policy.implies(pd2, pA) ||
	    policy.implies(pd2, pB) ||
	    policy.implies(pd2, pC))
	{
	    throw new Error();
	}

	final Principal prX = new StringPrincipal("X"),
			prY = new StringPrincipal("Y"),
			prZ = new StringPrincipal("Z");
	Subject subj = new Subject(true, 
				   new HashSet(Arrays.asList(
				       new Principal[]{ prX, prY })),
				   Collections.EMPTY_SET,
				   Collections.EMPTY_SET);
	Subject.doAs(subj, new PrivilegedAction() {
			 public Object run() {
			     Security.grant(cl1, cl2);
			     return null;
			 }
		     });

	if (policy.implies(pd2, pA) ||
	    policy.implies(pd2, pB) ||
	    policy.implies(pd2, pC))
	{
	    throw new Error();
	}

	pd2 = new ProtectionDomain(pd2.getCodeSource(),
				   pd2.getPermissions(),
				   pd2.getClassLoader(),
				   new Principal[]{ prX, prY });
	if (!policy.implies(pd2, pA) ||
	    !policy.implies(pd2, pB) ||
	    policy.implies(pd2, pC))
	{
	    throw new Error();
	}

	pd2 = new ProtectionDomain(pd2.getCodeSource(),
				   pd2.getPermissions(),
				   pd2.getClassLoader(),
				   new Principal[]{ prX, prY, prZ });
	if (!policy.implies(pd2, pA) ||
	    !policy.implies(pd2, pB) ||
	    policy.implies(pd2, pC))
	{
	    throw new Error();
	}
    }
}
