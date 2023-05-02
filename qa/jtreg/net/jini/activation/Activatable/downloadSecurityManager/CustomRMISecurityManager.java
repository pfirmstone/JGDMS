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

import java.security.*;

public class CustomRMISecurityManager extends SecurityManager {

    /**
     * Constructs a new <code>CustomRMISecurityManager</code>.
     * @since JDK1.1
     */
    public CustomRMISecurityManager() {
    }
    
    // P.F. 2020-05-27 inClassLoader() has been removed and details for
    // bug ID 4116138 are not available.

    /**
     * Checks access to threads.
     */
//    public synchronized void checkAccess(Thread t) {
//	// added back check for inClasLoader() until 
//	// we work out the details for 4116138
//	if (inClassLoader()) {
//	    super.checkAccess(t);
//	}
//    }

    /**
     * Checks access to threads.
     */
//    public synchronized void checkAccess(ThreadGroup g) {
//	// added back check for inClasLoader() until 
//	// we work out the details for 4116138
//	if (inClassLoader()) {
//	    super.checkAccess(g);
//	}
//    }

    /**
     * Checks access to classes of a given package.
     */
    public synchronized void checkPackageAccess(final String pkg) {
	try {
	    // allow it if the AccessController does
	    super.checkPackageAccess(pkg);
	    return;
	} catch (SecurityException se) {
	}

	int i = pkg.indexOf('.');
	if (i != -1) {
	    if (AccessController.doPrivileged(
		new CheckPackage("package.restrict.access.", pkg)) != null) {
		    throw new AccessControlException(
			"checkaccessdefinition " + pkg);
	    }
	}
    }

    /**
     * Checks access to defining classes of a given package.
     */
    public synchronized void checkPackageDefinition(String pkg) {
	try {
	    // allow it if the AccessController does
	    super.checkPackageDefinition(pkg);
	    return;
	} catch (SecurityException se) {
	}

	int i = pkg.indexOf('.');
	if (i != -1) {
	    if (AccessController.doPrivileged(
	      new CheckPackage("package.restrict.definition.",pkg)) != null) {
		    throw new AccessControlException(
			"checkpackagedefinition " + pkg);
	    }
	}
    }

    class CheckPackage implements PrivilegedAction {
	private String prefix;
	private String pkg;

	CheckPackage(String prefix, String pkg) {
	    this.prefix = prefix;
	    this.pkg = pkg;
	}
	
	public Object run() {
	    int i = pkg.indexOf('.');
	    
	    while (i > 0) {
		String subpkg = pkg.substring(0, i);
		if (Boolean.getBoolean(prefix + subpkg)) {
		    return new Object();
		}
		i = pkg.indexOf('.', i + 1);
	    }
	    return null;
	}
    }
}
