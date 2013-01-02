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
 * @summary Verify proper general functioning of LoaderSplitPolicyProvider
 * @library ../../../../../testlibrary
 * @build Test Foo Bar
 * @run main/othervm/policy=policy.all Test
 */
package com.sun.jini.test.impl.start.loadersplitpolicyprovider;

import com.sun.jini.start.LoaderSplitPolicyProvider;
import net.jini.security.policy.*;
import java.io.File;
import java.net.*;
import java.security.*;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class LoaderSplitPolicyProviderTest extends QATestEnvironment implements Test {
    private String ldrPolicyFile;
    private String defPolicyFile;
    private String fooJarFile;
    private String barJarFile;

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
        ldrPolicyFile = sysConfig.getStringConfigVal("ldrPolicyfile",
                sysConfig.getKitHomeDir() + File.separator + "policy"
                + File.separator
                + "policy.start.LoaderSplitPolicyProviderTest.loader");
        defPolicyFile = sysConfig.getStringConfigVal("defPolicyfile",
                sysConfig.getKitHomeDir() + File.separator + "policy"
                + File.separator
                + "policy.start.LoaderSplitPolicyProviderTest.default");
        fooJarFile = sysConfig.getStringConfigVal("fooJarFile",
                sysConfig.getKitHomeDir() + File.separator
		+ "lib" + File.separator
                + "qa1-start-cb1.jar");
        barJarFile = sysConfig.getStringConfigVal("barJarFile",
                sysConfig.getKitHomeDir() + File.separator
		+ "lib" + File.separator
                + "qa1-start-cb2.jar");
        return this;
    }

    public void run() throws Exception {
        ClassLoader parentLdr = new URLClassLoader(new URL[] {
            new URL("file", "", fooJarFile) });
        ClassLoader childLdr = new URLClassLoader(new URL[] {
            new URL("file", "", barJarFile) }, parentLdr);
	Class fooCl = Class.forName(
                "com.sun.jini.test.impl.start.loadersplitpolicyprovider.Foo",
                true, parentLdr);
	Class barCl = Class.forName(
                "com.sun.jini.test.impl.start.loadersplitpolicyprovider.Bar",
                true, childLdr);
	Policy ldrPolicy = new PolicyFileProvider(ldrPolicyFile);
	Policy defPolicy = new PolicyFileProvider(defPolicyFile);
	Policy dynLdrPolicy = new DynamicPolicyProvider(ldrPolicy);
	Policy dynDefPolicy = new DynamicPolicyProvider(defPolicy);

	DynamicPolicy[] dp = {
	    new LoaderSplitPolicyProvider(parentLdr, ldrPolicy, defPolicy),
	    new LoaderSplitPolicyProvider(parentLdr, dynLdrPolicy, defPolicy),
	    new LoaderSplitPolicyProvider(parentLdr, ldrPolicy, dynDefPolicy)
	};
	for (int i = 0; i < dp.length; i++) {
	    if (dp[i].grantSupported()) {
		throw new TestException("grant is not supported by " + dp[i]);
	    }
	    try {
		dp[i].grant(fooCl, null,
			    new Permission[]{ new RuntimePermission("Q") });
		throw new TestException("RuntimePermission is granted by "
                        + dp[i]);
	    } catch (UnsupportedOperationException e) {
	    }
	}

	LoaderSplitPolicyProvider pol = new LoaderSplitPolicyProvider(
	    parentLdr, dynLdrPolicy, dynDefPolicy);
	ProtectionDomain myPd =
                LoaderSplitPolicyProviderTest.class.getProtectionDomain(),
		fooPd = fooCl.getProtectionDomain(),
		barPd = barCl.getProtectionDomain(),
		nullPd = new ProtectionDomain(
		    new CodeSource((URL) null,
			(java.security.cert.Certificate[]) null), 
			null, null, null);
	Permission perm = new RuntimePermission("defaultPolicyStatic");

	if (!pol.implies(myPd, perm) ||
	    pol.implies(fooPd, perm) ||
	    pol.implies(barPd, perm) ||
	    pol.implies(nullPd, perm))
	{
	    throw new TestException(
                    "Does not satisfy implies conditions for " + perm + ".");
	}

	if (!contains(pol.getPermissions(myPd), perm) ||
	    contains(pol.getPermissions(fooPd), perm) ||
	    contains(pol.getPermissions(barPd), perm) ||
	    contains(pol.getPermissions(nullPd), perm))
	{
	    throw new TestException(
                    "Does not satisfy getPermissions conditions for " + perm
                    + ".");
	}

        // Not tested due to changes in ConcurrentPolicyFile.getPermissions(CodeSource)
//	if (!(contains(pol.getPermissions(myPd.getCodeSource()), perm) &&
//	      contains(pol.getPermissions(fooPd.getCodeSource()), perm) &&
//	      contains(pol.getPermissions(barPd.getCodeSource()), perm) &&
//	      contains(pol.getPermissions(nullPd.getCodeSource()), perm)))
//	{
//            throw new TestException(
//                    "Does not satisfy getPermissions & getCodeSource "
//                    + "conditions for " + perm + ".");
//	}

	perm = new RuntimePermission("defaultPolicyGrant");
	((DynamicPolicy) dynDefPolicy).grant(
	    null, null, new Permission[]{ perm });

	if (!pol.implies(myPd, perm) ||
	    pol.implies(fooPd, perm) ||
	    pol.implies(barPd, perm) ||
	    pol.implies(nullPd, perm))
	{
	    throw new TestException("Does not satisfy implies conditions for "
                    + perm + ".");
	}
        
	if (!contains(pol.getPermissions(myPd), perm) ||
	    contains(pol.getPermissions(fooPd), perm) ||
	    contains(pol.getPermissions(barPd), perm) ||
	    contains(pol.getPermissions(nullPd), perm))
	{
	    throw new TestException(
                    "Does not satisfy getPermissions conditions for " + perm
                    + ".");
	}

	perm = new RuntimePermission("loaderPolicyStatic");

	if (pol.implies(myPd, perm) ||
	    !pol.implies(fooPd, perm) ||
	    !pol.implies(barPd, perm) ||
	    !pol.implies(nullPd, perm))
	{
	    throw new TestException("Does not satisfy implies conditions for "
                    + perm + ".");
	}

	if (contains(pol.getPermissions(myPd), perm) ||
	    !contains(pol.getPermissions(fooPd), perm) ||
	    !contains(pol.getPermissions(barPd), perm) ||
	    !contains(pol.getPermissions(nullPd), perm))
	{
	    throw new TestException(
                    "Does not satisfy getPermissions conditions for " + perm
                    + ".");
	}
// Not tested due to changes in ConcurrentPolicyFile.getPermissions(CodeSource)
//	if (contains(pol.getPermissions(myPd.getCodeSource()), perm) ||
//	    contains(pol.getPermissions(fooPd.getCodeSource()), perm) ||
//	    contains(pol.getPermissions(barPd.getCodeSource()), perm) ||
//	    contains(pol.getPermissions(nullPd.getCodeSource()), perm))
//	{
//            throw new TestException(
//                    "Does not satisfy getPermissions & getCodeSource "
//                    + "conditions for " + perm + ".");
//	}

	perm = new RuntimePermission("loaderPolicyGrant");
	((DynamicPolicy) dynLdrPolicy).grant(
	    null, null, new Permission[]{ perm });

	if (pol.implies(myPd, perm) ||
	    !pol.implies(fooPd, perm) ||
	    !pol.implies(barPd, perm) ||
	    !pol.implies(nullPd, perm))
	{
	    throw new TestException("Does not satisfy implies conditions for "
                    + perm + ".");
	}

	if (contains(pol.getPermissions(myPd), perm) ||
	    !contains(pol.getPermissions(fooPd), perm) ||
	    !contains(pol.getPermissions(barPd), perm) ||
	    !contains(pol.getPermissions(nullPd), perm))
	{
	    throw new TestException(
                    "Does not satisfy getPermissions conditions for " + perm
                    + ".");
	}
    }

    /*
     * Dynamic policy no longer returns the permission directly, instead
     * it encapsulates it in a container Permission that implies nothing,
     * it is useful for debugging only since it delegates toString()
     * to the encapsulated Permission.
     * 
     * Dynamic policy does this to prevent the Permission becoming merged
     * in the ProtectionDomain.
     */
    static boolean contains(PermissionCollection pc, Permission p) {
//	return Collections.list(pc.elements()).contains(p);
        Set<String> perms = new HashSet<String>();
        Enumeration<Permission> e = pc.elements();
        while (e.hasMoreElements()){
            perms.add(e.nextElement().toString());
        }
        return perms.contains(p.toString());
    }
}
