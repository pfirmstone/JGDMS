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
 * @summary Verify basic functionality of AggregatePolicyProvider.getContext()
 * @library ../../../../../../testlibrary
 * @build Test GetContext RestoreContext CheckContextAction
 * @run main/othervm/policy=policy Test
 */
package com.sun.jini.test.impl.start.aggregatepolicyprovider;

import com.sun.jini.start.AggregatePolicyProvider;
import java.io.File;
import java.net.*;
import java.security.Permission;
import java.security.Policy;
import java.security.PrivilegedAction;
import net.jini.security.*;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/*
 * Permission key:
 *
 * RuntimePermission("A") -- granted to GetContext and RestoreContext
 * RuntimePermission("B") -- granted to GetContext
 * RuntimePermission("C") -- granted to RestoreContext
 * AllPermission          -- granted to CheckContextAction
 */
public class GetContextTest extends QATestEnvironment implements Test {

    public static volatile SecurityContext securityContext;
    public static final Permission[] passPermissions = {
	new RuntimePermission("A")
    };
    public static final Permission[] failPermissions = { 
	new RuntimePermission("B"),
	new RuntimePermission("C"),
	new RuntimePermission("D")
    };
    public static volatile PrivilegedAction checkContextAction;
    public static volatile ClassLoader contextClassLoader;
    private volatile String getContextJarFile;
    private volatile String restoreContextJarFile;
    private volatile String checkContextActionJarFile;

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
        getContextJarFile = sysConfig.getStringConfigVal("getContextJarFile",
                sysConfig.getKitHomeDir() + File.separator
		+ "lib" + File.separator
                + "qa1-start-cb1.jar");
        restoreContextJarFile =
                sysConfig.getStringConfigVal("restoreContextJarFile",
                sysConfig.getKitHomeDir() + File.separator
		+ "lib" + File.separator
                + "qa1-start-cb2.jar");
        checkContextActionJarFile =
                sysConfig.getStringConfigVal("checkContextActionJarFile",
                sysConfig.getKitHomeDir() + File.separator
		+ "lib" + File.separator
                + "qa1-start-cb3.jar");
        return this;
    }

    public void run() throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	Policy.setPolicy(new AggregatePolicyProvider());

	ClassLoader ldr1 = new URLClassLoader(new URL[]{
	    new URL("file", "", getContextJarFile) });
	Runnable getContext = (Runnable) Class.forName(
                "com.sun.jini.test.impl.start.aggregatepolicyprovider"
                + ".GetContext", true, ldr1).newInstance();

	ClassLoader ldr2 = new URLClassLoader(new URL[]{
	    new URL("file", "", restoreContextJarFile) });
	Runnable restoreContext = (Runnable) Class.forName(
                "com.sun.jini.test.impl.start.aggregatepolicyprovider"
                + ".RestoreContext", true, ldr2).newInstance();

	ClassLoader ldr3 = new URLClassLoader(new URL[]{
	    new URL("file", "", checkContextActionJarFile) });
	checkContextAction = (PrivilegedAction) Class.forName(
                "com.sun.jini.test.impl.start.aggregatepolicyprovider"
                + ".CheckContextAction", true, ldr3).newInstance();

	contextClassLoader = ldr3;
	Thread.currentThread().setContextClassLoader(contextClassLoader);
	getContext.run();
	Thread.currentThread().setContextClassLoader(ldr2);
	restoreContext.run();
    }
}
