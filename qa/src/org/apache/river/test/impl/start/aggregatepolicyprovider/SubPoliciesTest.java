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
/* @summary Verify that AggregatePolicyProvider correctly delegates to
 *          sub-policies
 * @run main/othervm/policy=policy.0 Test
 */
package org.apache.river.test.impl.start.aggregatepolicyprovider;

import org.apache.river.start.AggregatePolicyProvider;
import java.io.File;
import java.net.*;
import java.security.*;
import net.jini.security.policy.DynamicPolicyProvider;
import net.jini.security.policy.PolicyFileProvider;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;
import org.apache.river.api.security.ConcurrentPolicyFile;

public class SubPoliciesTest extends QATestEnvironment implements Test {
    private String policy0File;
    private String policy1File;
    private static String jsk_home = System.getProperty("org.apache.river.jsk.home");

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
        policy0File = sysConfig.getStringConfigVal("policy0File",
                sysConfig.getKitHomeDir() + File.separator + "policy"
                + File.separator + "policy.start.SubPoliciesTest.0");
        policy1File = sysConfig.getStringConfigVal("policy1File",
                sysConfig.getKitHomeDir() + File.separator + "policy"
                + File.separator + "policy.start.SubPoliciesTest.1");
        return this;
    }

    public void run() throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	ClassLoader ldr1 = new URLClassLoader(
				   new URL[]{ new URL("file:" + jsk_home +"/foo") });
	ClassLoader ldr2 = new URLClassLoader(
				   new URL[]{ new URL("file:" + jsk_home + "/bar") }, ldr1);
	Thread thr = Thread.currentThread();

	AggregatePolicyProvider policy = new AggregatePolicyProvider();
	checkPolicy0Permissions(policy);
	thr.setContextClassLoader(ldr1);
	checkPolicy0Permissions(policy);
	System.setProperty("java.security.policy", policy1File);
	policy.refresh();
	checkPolicy1Permissions(policy);

	thr.setContextClassLoader(null);
	policy.setPolicy(null, new PolicyFileProvider(policy0File));
	policy.setPolicy(ldr1, 
	    new DynamicPolicyProvider(new PolicyFileProvider(policy1File)));

	checkPolicy0Permissions(policy);
	if (policy.grantSupported()) {
	    throw new TestException("1-st grant is supported.");
	}
	try {
	    policy.grant(null, null, 
			 new Permission[]{ new RuntimePermission("foo") });
	    throw new TestException("1-st grant does not throw exception.");
	} catch (UnsupportedOperationException e) {
	}

	thr.setContextClassLoader(ldr1);
	checkPolicy1Permissions(policy);
	if (!policy.grantSupported()) {
	    throw new TestException("2-nd grant is not supported.");
	}
	policy.grant(null, null, 
		     new Permission[]{ new RuntimePermission("foo") });

	thr.setContextClassLoader(ldr2);
	checkPolicy1Permissions(policy);
	if (!policy.grantSupported()) {
	    throw new TestException("3-rd grant is not supported.");
	}
	policy.grant(null, null, 
		     new Permission[]{ new RuntimePermission("foo") });

	policy.setPolicy(ldr2, new PolicyFileProvider(policy0File));
	checkPolicy0Permissions(policy);
	if (policy.grantSupported()) {
	    throw new TestException("4-th grant is supported.");
	}
	try {
	    policy.grant(null, null, 
			 new Permission[]{ new RuntimePermission("foo") });
	    throw new TestException("2-nd grant does not throw exception.");
	} catch (UnsupportedOperationException e) {
	}

	thr.setContextClassLoader(ldr1);
	checkPolicy1Permissions(policy);
	if (!policy.grantSupported()) {
	    throw new TestException("5-th grant is not supported.");
	}
	policy.grant(null, null, 
		     new Permission[]{ new RuntimePermission("foo") });

	policy.setPolicy(ldr2, null);
	thr.setContextClassLoader(ldr2);
	checkPolicy1Permissions(policy);
	if (!policy.grantSupported()) {
	    throw new TestException("6-th grant is not supported.");
	}
	policy.grant(null, null, 
		     new Permission[]{ new RuntimePermission("foo") });
    }

    static void checkPolicy0Permissions(Policy policy) throws Exception {
	checkPermissions(policy, "file:"+ jsk_home + "/foo/bar",
			 new Permission[]{
			     new RuntimePermission("A0"),
			     new RuntimePermission("B0"),
			     new RuntimePermission("C0")
			 },
			 new Permission[]{
			     new RuntimePermission("D0"),
			     new RuntimePermission("A1")
			 });
	checkPermissions(policy, "file:" + jsk_home + "/foo/bar/baz",
			 new Permission[]{
			     new RuntimePermission("A0"),
			     new RuntimePermission("C0")
			 },
			 new Permission[]{
			     new RuntimePermission("B0"),
			     new RuntimePermission("D0"),
			     new RuntimePermission("A1")
			 });
	checkPermissions(policy, "file:" + jsk_home + "/bar.jar",
			 new Permission[]{
			     new RuntimePermission("A0"),
			     new RuntimePermission("D0")
			 },
			 new Permission[]{
			     new RuntimePermission("B0"),
			     new RuntimePermission("C0"),
			     new RuntimePermission("A1")
			 });
	checkPermissions(policy, "file:" + jsk_home + "/other",
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
	checkPermissions(policy, "file:" +jsk_home + "/foo/bar",
			 new Permission[]{
			     new RuntimePermission("A1"),
			     new RuntimePermission("B1"),
			     new RuntimePermission("C1")
			 },
			 new Permission[]{
			     new RuntimePermission("D1"),
			     new RuntimePermission("A0")
			 });
	checkPermissions(policy, "file:" + jsk_home + "/foo/bar/baz",
			 new Permission[]{
			     new RuntimePermission("A1"),
			     new RuntimePermission("C1")
			 },
			 new Permission[]{
			     new RuntimePermission("B1"),
			     new RuntimePermission("D1"),
			     new RuntimePermission("A0")
			 });
	checkPermissions(policy, "file:" + jsk_home + "/bar.jar",
			 new Permission[]{
			     new RuntimePermission("A1"),
			     new RuntimePermission("D1")
			 },
			 new Permission[]{
			     new RuntimePermission("B1"),
			     new RuntimePermission("C1"),
			     new RuntimePermission("A0")
			 });
	checkPermissions(policy, "file:" + jsk_home + "/other",
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
	CodeSource cs = new CodeSource(new URL(codebase), 
            (java.security.cert.Certificate[]) null);
	PermissionCollection pc = policy.getPermissions(cs);
        // No longer tested due to ConcurrentPolicyFile calling
        // super.getPermissions(source) when non privileged domain
        // performance optimisation.
//	for (int i = 0; i < pass.length; i++) {
//	    if (!pc.implies(pass[i])) {
//                    throw new TestException(pass[i] + " not implied by " + cs);
//                }
//	    }
	for (int i = 0; i < fail.length; i++) {
	    if (pc.implies(fail[i])) {
		throw new TestException(fail[i] + " implied by " + cs);
	    }
	}

	ProtectionDomain pd = new ProtectionDomain(cs, null, null, null);
	pc = policy.getPermissions(pd);
	for (int i = 0; i < pass.length; i++) {
	    if (!pc.implies(pass[i])) {
		throw new TestException(pass[i] + " not implied by " + cs);
	    }
	}
	for (int i = 0; i < fail.length; i++) {
	    if (pc.implies(fail[i])) {
		throw new TestException(fail[i] + " implied by " + cs);
	    }
	}

	for (int i = 0; i < pass.length; i++) {
	    if (!policy.implies(pd, pass[i])) {
		throw new TestException(pass[i] + " not implied by " + cs);
	    }
	}
	for (int i = 0; i < fail.length; i++) {
	    if (policy.implies(pd, fail[i])) {
		throw new TestException(fail[i] + " implied by " + cs);
	    }
	}
    }
}
