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

package net.jini.security.policy;

import org.apache.river.api.security.AbstractPolicy;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jini.security.GrantPermission;

/**
 * Security policy provider that wraps the J2SE(TM) default
 * <a href="http://java.sun.com/j2se/1.4/docs/guide/security/PolicyFiles.html">
 * "PolicyFile" security policy provider</a> distributed as part of the
 * Java(TM) 2 Platform, Standard Edition.  This provider augments the J2SE
 * default policy provider in two ways: it provides an additional constructor
 * for creating a policy based on an explicitly named policy file, and supports
 * the use of {@link UmbrellaGrantPermission}s as shorthand notation for
 * {@link GrantPermission}s covering all permissions authorized to given
 * protection domains.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 *
 * @org.apache.river.impl <!-- Implementation Specifics -->
 *
 * This implementation's no-argument constructor uses a default class name of
 * <code>"org.apache.river.impl.security.policy.se.ConcurrentPolicyFile"</code> 
 * to instantiate base policy objects, if the
 * <code>net.jini.security.policy.PolicyFileProvider.basePolicyClass</code>
 * security property is not set.
 */
public class PolicyFileProvider extends AbstractPolicy {

    private static final String basePolicyClassProperty =
	"net.jini.security.policy.PolicyFileProvider.basePolicyClass";
    private static final String defaultBasePolicyClass =
        // Having our own implementation removes a platform dependency
       "org.apache.river.api.security.ConcurrentPolicyFile";
//	"sun.security.provider.PolicyFile";
    private static final String policyProperty = "java.security.policy";
    private static final Object propertyLock = new Object();

    private final String policyFile;
    private final Policy basePolicy;

    /**
     * Creates a <code>PolicyFileProvider</code> whose starting set of
     * permission mappings is the same as those that would result from
     * constructing a new instance of the J2SE default security policy provider
     * with the current <code>java.security.policy</code> system property
     * setting (if any), except that <code>UmbrellaGrantPermission</code>s are
     * expanded into <code>GrantPermission</code>s as described in the
     * documentation for {@link UmbrellaGrantPermission}.
     * <p>
     * The constructed <code>PolicyFileProvider</code> contains an instance of
     * the J2SE default security policy provider, which is created as follows:
     * if the
     * <code>net.jini.security.policy.PolicyFileProvider.basePolicyClass</code>
     * security property is set, then its value is interpreted as the class
     * name of the base (underlying) J2SE default policy provider; otherwise,
     * an implementation-specific default class name is used.  The base policy
     * is then instantiated using the no-arg public constructor of the named
     * class.  If the base policy class is not found or is not instantiable via
     * a public no-arg constructor, or if invocation of its constructor fails,
     * then a <code>PolicyInitializationException</code> is thrown.
     * <p>
     * Note that this constructor requires the appropriate
     * <code>"getProperty"</code> {@link java.security.SecurityPermission} to
     * read the
     * <code>net.jini.security.policy.PolicyFileProvider.basePolicyClass</code>
     * security property, and may require <code>"accessClassInPackage.*"</code>
     * {@link RuntimePermission}s, depending on the package of the base policy
     * class.
     *
     * @throws  PolicyInitializationException if unable to construct the base
     *          policy
     * @throws  SecurityException if there is a security manager and the
     *          calling context does not have adequate permissions to read the
     *          <code>net.jini.security.policy.PolicyFileProvider.basePolicyClass</code>
     *          security property, or if the calling context does not have
     *          adequate permissions to access the base policy class
     * @deprecated DynamicPolicyProvider now supports Umbrella grants directly.
     */
    @Deprecated
    public PolicyFileProvider() throws PolicyInitializationException {
	policyFile = null;

	String cname = Security.getProperty(basePolicyClassProperty);
	if (cname == null) {
	    cname = defaultBasePolicyClass;
	}
	try {
	    basePolicy = (Policy) Class.forName(cname).newInstance();
	} catch (SecurityException e) {
	    throw e;
	} catch (Exception e) {
	    throw new PolicyInitializationException(
		"unable to construct base policy", e);
	}
	ensureDependenciesResolved();
    }

    /**
     * Creates a <code>PolicyFileProvider</code> whose starting set of
     * permission mappings is the same as those that would result from
     * constructing a new instance of the J2SE default security policy provider
     * with the <code>java.security.policy</code> system property set to the
     * value of <code>policyFile</code>, except that
     * <code>UmbrellaGrantPermission</code>s are expanded into
     * <code>GrantPermission</code>s as described in the documentation for
     * {@link UmbrellaGrantPermission}.
     * <p>
     * The constructed <code>PolicyFileProvider</code> contains an instance of
     * the J2SE default security policy provider, which is created as described
     * in the documentation for {@link #PolicyFileProvider()}.  Before
     * instantiating the base (underlying) J2SE default policy provider, this
     * constructor sets the <code>java.security.policy</code> system property
     * to the value of <code>policyFile</code>; after instantiation of the base
     * policy instance has completed (normally or otherwise), the
     * <code>java.security.policy</code> system property is reset to its prior
     * value.  Internal synchronization ensures that concurrent calls to this
     * constructor and/or the {@link #refresh} method of this class (which may
     * also modify <code>java.security.policy</code>) will not interfere with
     * the <code>java.security.policy</code> values set and restored by each.
     * No synchronization is done with any other accesses or modifications to
     * <code>java.security.policy</code>.
     * <p>
     * Note that this constructor requires {@link java.util.PropertyPermission}
     * to read and write the <code>java.security.policy</code> system property,
     * {@link java.security.SecurityPermission} to read the
     * <code>net.jini.security.policy.PolicyFileProvider.basePolicyClass</code>
     * security property, and may require <code>"accessClassInPackage.*"</code>
     * {@link RuntimePermission}s, depending on the package of the base policy
     * class.
     *
     * @param	policyFile URL string specifying location of the policy file to
     * 		use
     * @throws	NullPointerException if <code>policyFile</code> is
     * 		<code>null</code>
     * @throws  PolicyInitializationException if unable to construct the base
     *          policy
     * @throws  SecurityException if there is a security manager and the
     *          calling context does not have adequate permissions to read and
     *          write the <code>java.security.policy</code> system property, to
     *          read the
     *          <code>net.jini.security.policy.PolicyFileProvider.basePolicyClass</code>
     *          security property, or to access the base policy class
     */
    public PolicyFileProvider(String policyFile) 
	throws PolicyInitializationException
    {
	if (policyFile == null) {
	    throw new NullPointerException();
	}
	this.policyFile = policyFile;

	String cname = Security.getProperty(basePolicyClassProperty);
	if (cname == null) {
	    cname = defaultBasePolicyClass;
	}
	try {
	    Class cl = Class.forName(cname);
	    synchronized (propertyLock) {
		String oldp = System.getProperty(policyProperty);
                System.setProperty(policyProperty, policyFile);
		try {
		    basePolicy = (Policy) cl.newInstance();
		} finally {
		    resetPolicyProperty(oldp);
		}
	    }
	} catch (SecurityException e) {
	    throw e;
	} catch (Exception e) {
	    throw new PolicyInitializationException(
		"unable to construct base policy", e);
	}
	ensureDependenciesResolved();
    }

    /**
     * Behaves as specified by {@link Policy#getPermissions(CodeSource)}.
     */
    public PermissionCollection getPermissions(CodeSource source) {
	PermissionCollection pc = basePolicy.getPermissions(source);
	expandUmbrella(pc);
	return pc;
    }


    /**
     * Behaves as specified by {@link Policy#getPermissions(ProtectionDomain)}.
     */
    public PermissionCollection getPermissions(ProtectionDomain domain) {
	PermissionCollection pc = basePolicy.getPermissions(domain);
	expandUmbrella(pc);
	return pc;
    }

    /**
     * Behaves as specified by {@link Policy#implies}.
     */
    public boolean implies(ProtectionDomain domain, Permission permission) {
	// REMIND: cache expanded permission collections?
	return (basePolicy.implies(domain, permission) ||
		(permission instanceof GrantPermission &&
		 getPermissions(domain).implies(permission)));
    }

    /**
     * Refreshes the policy configuration by calling <code>refresh</code> on
     * the base policy.  If this <code>PolicyFileProvider</code> instance was
     * constructed with an explicit policy file value, then the
     * <code>java.security.policy</code> system property is set to that value
     * prior to invoking <code>refresh</code> on the base policy; once the base
     * policy <code>refresh</code> call has completed, the
     * <code>java.security.policy</code> system property is reset to its prior
     * value.  Internal synchronization ensures that concurrent invocations of
     * this method and/or the {@link #PolicyFileProvider(String)} constructor
     * (which also modifies <code>java.security.policy</code>) will not
     * interfere with the <code>java.security.policy</code> values set and
     * restored by each. No synchronization is done with any other accesses
     * or modifications to <code>java.security.policy</code>.
     */
    public void refresh() {
	if (policyFile != null) {
	    synchronized (propertyLock) {
		String oldp = System.getProperty(policyProperty);
                System.setProperty(policyProperty, policyFile);
		try {
		    basePolicy.refresh();
		} finally {
		    resetPolicyProperty(oldp);
		}
	    }
	} else {
	    basePolicy.refresh();
	}
    }

    /**
     * Ensures that any classes depended on by this policy provider are
     * resolved.  This is to preclude lazy resolution of such classes during
     * operation of the provider, which can result in deadlock as described by
     * bug 4911907.
     */
    private void ensureDependenciesResolved() {
	// force resolution of GrantPermission and UmbrellaGrantPermission
	new GrantPermission(umbrella);
    }
    
    /** Resets policyProperty system property, removing it if the value to set
     * is null. We do this in a privileged block to make sure that the operation
     * does not fail, even if the calling context may not have the requisite
     * permissions anymore (could happen in the refresh case).
     */
    private static void resetPolicyProperty(final String value) {
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		if (value == null) {
		    // TODO: Use System.clearProperty when we move to 1.5
                    System.clearProperty(policyProperty);
		    //System.getProperties().remove(policyProperty);
		} else {
		    System.setProperty(policyProperty, value);
		}
		return null;
	   } 
	});
    }
}
