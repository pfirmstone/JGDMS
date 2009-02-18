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
 * @bug 6231770
 * @summary Untrusted code should not be able to instantiate a
 * PreferredClassProvider and obtain references to class loaders from
 * it (such as by subclassing it and overriding a protected method).
 *
 * @build ConstructorCheck
 * @run main/othervm/policy=security.policy ConstructorCheck
 */

import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import net.jini.loader.pref.PreferredClassProvider;

public class ConstructorCheck {
    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 6231770\n");

	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}

	ClassLoader secretLoader = new URLClassLoader(new URL[0], null) {
	    public String toString() { return "SECRET LOADER"; }
	};
	Thread.currentThread().setContextClassLoader(secretLoader);

	AccessControlContext noPermsAcc =
	    new AccessControlContext(new ProtectionDomain[] {
		new ProtectionDomain(null, null)
	    });
	try {
	    ClassLoader loader = (ClassLoader)
		AccessController.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			return (new PCP()).getContextClassLoader();
		    }
		}, noPermsAcc);
	    System.err.println("Obtained loader: " + loader);
	    throw new RuntimeException("TEST FAILED: provider instantiated");
	} catch (SecurityException e) {
	    e.printStackTrace();
	    System.err.println("TEST PASSED");
	}
    }

    private static class PCP extends PreferredClassProvider {
	private final ThreadLocal ccl = new ThreadLocal();
	PCP() { super(); }
	protected ClassLoader createClassLoader(URL[] urls,
						ClassLoader parent,
						boolean requireDlPerm)
	{
	    ccl.set(parent);
	    throw new EscapeException();
	}
	ClassLoader getContextClassLoader() {
	    try {
		loadClass("", "java.lang.Object", null);
	    } catch (EscapeException e) {
	    } catch (Exception e) {
		throw new AssertionError(e);
	    }
	    return (ClassLoader) ccl.get();
	}
	private static class EscapeException extends RuntimeException {
	    EscapeException() { super(); }
	}
    }
}
