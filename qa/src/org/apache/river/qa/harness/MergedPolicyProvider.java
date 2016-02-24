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
package org.apache.river.qa.harness;

import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import net.jini.security.policy.PolicyInitializationException;
import net.jini.security.policy.PolicyFileProvider;
import org.apache.river.api.security.AbstractPolicy;
import org.apache.river.api.security.ConcurrentPolicyFile;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.ScalableNestedPolicy;

/**
 * Security policy provider that delegates to a collection of underlying
 * <code>PolicyFileProvider</code>s. The resulting policy implements
 * an imperfect union of the underlying policies. For example, if
 * one policy granted read access to a file and another granted write
 * access to the same file, a check for read,write access would still
 * fail.
 */
public class MergedPolicyProvider extends AbstractPolicy implements ScalableNestedPolicy{

    /** class state */
//    private static final Lock lock = new ReentrantLock();; // protects first
//    private static boolean first = false; // Why is first static?
    
    /** the collection of underlying policies */
    private final Collection<Policy> policies ;

    /**
     * Creates a new <code>MergedPolicyProvider</code> instance that wraps a
     * collection of underlying policies. If the
     * <code>java.security.policy</code> property is defined, it must supply the
     * name of a single security policy file. If the
     * <code>org.apache.river.qa.harness.policies</code> property is defined, it
     * must supply the names of one or more security policy files. The names
     * must be separated by white space and/or commas. A
     * <code>PolicyFileProvider</code> is created for each policy file in the
     * combined set of names.
     * 
     * @throws PolicyInitializationException if unable to construct a policy
     */
    public MergedPolicyProvider() throws PolicyInitializationException {
	String p1 = System.getProperty("java.security.policy");
	String p2 = System.getProperty("org.apache.river.qa.harness.policies");
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
        Collection<Policy> policyCol = new ArrayList<Policy>();
	try {
	    if (p1 != null) {
		policyCol.add(new ConcurrentPolicyFile());
	    }
	    if (p2 != null) {
		StringTokenizer tok = new StringTokenizer(p2, ", ");
		while (tok.hasMoreTokens()) {
		    String policyFile = tok.nextToken();
		    policyCol.add(new PolicyFileProvider(policyFile));
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
        this.policies = Collections.unmodifiableCollection(policyCol);
    }

    /**
     * Return the permissions allowed for code from the specified code
     * source. The permissions returned by the underlying policies are combined
     * and returned in a single collection.
     *
     * @param source the <code>CodeSource</code>
     */
    public PermissionCollection getPermissions(CodeSource source) {
        if (policies.isEmpty()) throw new IllegalStateException("No policies in provider");
        PermissionCollection pc = new Permissions();
        Iterator<Policy> it = policies.iterator();
        while (it.hasNext()){
            Policy policy = it.next();
            PermissionCollection col = policy.getPermissions(source);
            Enumeration<Permission> e = col.elements();
            while(e.hasMoreElements()){
                pc.add(e.nextElement());
            }
        }
        return pc;
    }

    /**
     * Return the permissions allowed for the given protection domain. The
     * permissions returned by the underlying policies are combined and returned
     * in a single collection.
     *
     * @param domain the <code>ProtectionDomain</code>
     */
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        if (policies.isEmpty()) throw new IllegalStateException("No policies in provider");
        Collection<PermissionGrant> grants = getPermissionGrants(domain);
        NavigableSet<Permission> perms = new TreeSet<Permission>(comparator);
        processGrants(grants, null, true, perms);
        return convert(perms, 32, 0.75F, 1, 8);
    }

    /**
     * Test whether the given <code>Permission</code> is granted to the 
     * <code>ProtectionDomain</code> by any of the underlying policies.
     *
     * @param domain the <code>ProtectionDomain</code>
     * @param permission the <code>Permission</code> to check
     * @return true if the permission is granted
     */
    public boolean implies(ProtectionDomain domain, Permission permission) {
	Iterator<Policy> it = policies.iterator();
	while (it.hasNext()) {
	    Policy p = it.next();
	    if (p.implies(domain, permission)) {
		return true;
	    }
		
	}
	return false;
    }

    /**
     * Refresh all of the underlying policies.
     */
    public void refresh() {
//	System.out.println("In REFRESH");
	Iterator<Policy> it = policies.iterator();
	while (it.hasNext()) {
	    Policy p = it.next();
//	    System.out.println("CALLING refresh on " + p);
	    p.refresh();
	}
    }

    public List<PermissionGrant> getPermissionGrants(ProtectionDomain domain) {
        if (policies.isEmpty()) throw new IllegalStateException("No policies in provider");
        List<PermissionGrant> perms = null;
        Iterator<Policy> it = policies.iterator();
        while (it.hasNext()){
            Policy p = it.next();
            if (p instanceof ScalableNestedPolicy){
                List<PermissionGrant> g = ((ScalableNestedPolicy)p).getPermissionGrants(domain);
                if ( !g.isEmpty() && g.get(0).isPrivileged()) return g;
                if (perms == null) {
                    perms = g;
                } else {
                    perms.addAll(g);
                }
            } else {
                if (perms == null ) perms = new LinkedList<PermissionGrant>();
                PermissionGrant pg = extractGrantFromPolicy(p, domain);
                if (pg.isPrivileged()){
                    perms.clear();
                    perms.add(pg);
                    return perms;
                }
                perms.add(pg);
            }
        }
        return perms;
    }

}
