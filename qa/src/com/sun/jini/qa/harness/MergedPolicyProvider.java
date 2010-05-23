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
package com.sun.jini.qa.harness;

import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.jini.security.policy.PolicyInitializationException;
import net.jini.security.policy.PolicyFileProvider;

/**
 * Security policy provider that delegates to a collection of underlying
 * <code>PolicyFileProvider</code>s. The resulting policy implements
 * an imperfect union of the underlying policies. For example, if
 * one policy granted read access to a file and another granted write
 * access to the same file, a check for read,write access would still
 * fail.
 */
public class MergedPolicyProvider extends Policy {

    /** the collection of underlying policies */
    private ArrayList policies = new ArrayList();

    private static boolean first = false;

    /**
     * Creates a new <code>MergedPolicyProvider</code> instance that wraps a
     * collection of underlying policies. If the
     * <code>java.security.policy</code> property is defined, it must supply the
     * name of a single security policy file. If the
     * <code>com.sun.jini.qa.harness.policies</code> property is defined, it
     * must supply the names of one or more security policy files. The names
     * must be separated by white space and/or commas. A
     * <code>PolicyFileProvider</code> is created for each policy file in the
     * combined set of names.
     * 
     * @throws PolicyInitializationException if unable to construct a policy
     */
    public MergedPolicyProvider() throws PolicyInitializationException {
	String p1 = System.getProperty("java.security.policy");
	String p2 = System.getProperty("com.sun.jini.qa.harness.policies");
	if (p1 != null && p1.trim().length() == 0) {
	    p1 = null;
	}
	if (p2 != null && p2.trim().length() == 0) {
	    p2 = null;
	}
	if (p1 == null && p2 == null) {
	    throw new IllegalArgumentException("no policy files supplied");
	}
	// no-arg semantics for 'default policy' necessary for correct behavior 
	// of PolicyFileProvider.refresh
	try {
	    if (p1 != null) {
		policies.add(new PolicyFileProvider());
	    }
	    if (p2 != null) {
		StringTokenizer tok = new StringTokenizer(p2, ", ");
		while (tok.hasMoreTokens()) {
		    String policyFile = tok.nextToken();
		    policies.add(new PolicyFileProvider(policyFile));
		}
	    }
	} catch (SecurityException e) {
	    throw e;
	} catch (PolicyInitializationException e) {
	    throw e;
	} catch (Exception e) {
	    throw new PolicyInitializationException(
		"unable to construct base policy", e);
	}
    }

    /**
     * Return the permissions allowed for code from the specified code
     * source. The permissions returned by the underlying policies are combined
     * and returned in a single collection.
     *
     * @param source the <code>CodeSource</code>
     */
    public synchronized PermissionCollection getPermissions(CodeSource source) {
	Iterator it = policies.iterator();
	if (it.hasNext()) {
	    PermissionCollection pc = 
		((Policy) it.next()).getPermissions(source);
	    while (it.hasNext()) {
		PermissionCollection pc2 = 
		    ((Policy) it.next()).getPermissions(source);
		Enumeration en = pc2.elements();
		while (en.hasMoreElements()) {
		    Permission perm = (Permission) en.nextElement();
		    if (!pc.implies(perm)) {
			pc.add(perm);
		    }
		}
	    }
	    return  pc;
	} else {
	    throw new IllegalStateException("No policies in provider");
	}
    }

    /**
     * Return the permissions allowed for the given protection domain. The
     * permissions returned by the underlying policies are combined and returned
     * in a single collection.
     *
     * @param domain the <code>ProtectionDomain</code>
     */
    public synchronized PermissionCollection getPermissions(ProtectionDomain domain) {
	Iterator it = policies.iterator();
	ArrayList list = new ArrayList();
	if (it.hasNext()) {
	    PermissionCollection pc = 
		((Policy) it.next()).getPermissions(domain);
	    if (first) {
		first = false;
		Enumeration en = pc.elements();
		list.add("BASE PERMISSIONS for domain " + domain);
		while (en.hasMoreElements()) {
		    Permission perm = (Permission) en.nextElement();
		    list.add(perm.toString());
		}
		first = true;
	    }
	    while (it.hasNext()) {
		PermissionCollection pc2 = 
		    ((Policy) it.next()).getPermissions(domain);
		Enumeration en = pc2.elements();
		while (en.hasMoreElements()) {
		    Permission perm = (Permission) en.nextElement();
		    if (!pc.implies(perm)) {
			if (first) {
			    first = false;
			    list.add("checking " + perm + " and adding");
			    first = true;
			}
			pc.add(perm);
		    } else {
			if (first) {
			    first = false;
			    list.add("checking " + perm + " and not adding");
			    first = true;
			}
		    }
		}
	    }
	    if (first) {
		first = false;
		for (int i = 0; i < list.size(); i++) {
		    System.out.println((String) list.get(i));
		}
		first = true;
	    }
	    return pc;
	} else {
	    throw new IllegalStateException("No policies in provider");
	}
    }

    /**
     * Test whether the given <code>Permission</code> is granted to the 
     * <code>ProtectionDomain</code> by any of the underlying policies.
     *
     * @param domain the <code>ProtectionDomain</code>
     * @param permission the <code>Permission</code> to check
     * @return true if the permission is granted
     */
    public synchronized boolean implies(ProtectionDomain domain, Permission permission) {
	Iterator it = policies.iterator();
	while (it.hasNext()) {
	    Policy p = (Policy) it.next();
	    if (p.implies(domain, permission)) {
		return true;
	    }
		
	}
	return false;
    }

    /**
     * Refresh all of the underlying policies.
     */
    public synchronized void refresh() {
	System.out.println("In REFRESH");
	Iterator it = policies.iterator();
	while (it.hasNext()) {
	    Policy p = (Policy) it.next();
	    System.out.println("CALLING refresh on " + p);
	    p.refresh();
	}
    }
}
