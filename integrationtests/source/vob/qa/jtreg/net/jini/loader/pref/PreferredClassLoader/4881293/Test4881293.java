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
 * @bug 4881293
 *
 * @build Test4881293
 * @run main/othervm/policy=security.policy Test4881293
 */

import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import net.jini.loader.pref.PreferredClassLoader;

public class Test4881293 {
    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 4881293\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	String restrictedClassName = "sun.misc.Launcher";
	final String restrictedArrayClassName =
	    "[L" + restrictedClassName + ";";

	final ClassLoader pcl =
	    PreferredClassLoader.newInstance(new URL[0], null, null, false);

	try {
	    Class c = Class.forName(restrictedClassName, false, pcl);
	    System.err.println("restricted class loaded: " + c);
	} catch (ClassNotFoundException e) {
	    throw new RuntimeException(
		"TEST ERROR: restricted class not found", e);
	}

	AccessControlContext restrictedACC = new AccessControlContext(
	    new ProtectionDomain[] { new ProtectionDomain(null, null) });

	try {
	    try {
		Class c = (Class) AccessController.doPrivileged(
		    new PrivilegedExceptionAction() {
			public Object run() throws ClassNotFoundException {
			    return pcl.loadClass(restrictedArrayClassName);
			}
		    }, restrictedACC);
		System.err.println("restricted array class loaded: " + c);
	    } catch (PrivilegedActionException e) {
		throw (ClassNotFoundException) e.getCause();
	    }
	    throw new RuntimeException(
		"TEST FAILED: load of restricted array class succeeded");
	} catch (SecurityException e) {
	    e.printStackTrace();
	    System.err.println(
		"TEST PASSED: load of restricted array class failed");
	} catch (ClassNotFoundException e) {
	    e.printStackTrace();
	    System.err.println(
		"TEST PASSED: array class name not recognized by loadClass");
	}
    }
}
