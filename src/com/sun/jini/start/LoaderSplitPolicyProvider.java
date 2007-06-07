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

package com.sun.jini.start;

import com.sun.jini.collection.WeakIdentityMap;
import net.jini.security.policy.DynamicPolicy;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Permissions;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

/**
 * Security policy provider which handles permission queries and grants by
 * delegating to different policy providers depending on the class loader
 * involved.  Each <code>LoaderSplitPolicyProvider</code> instance wraps two
 * underlying policy providers:
 * <ul>
 * <li>a class-loader specific policy provider, consulted for permission
 * queries/grants pertaining to that class loader, any child class loaders that
 * delegate to it, or the <code>null</code> class loader, and
 * <li>a default policy provider, consulted for all other operations (aside
 * from {@link #refresh}, which applies to both policies).
 * </ul>
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class LoaderSplitPolicyProvider 
    extends Policy implements DynamicPolicy
{
    private static final ProtectionDomain myDomain = (ProtectionDomain)
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		return LoaderSplitPolicyProvider.class.getProtectionDomain();
	    }
	});

    private final ClassLoader loader;
    private final Policy loaderPolicy;
    private final Policy defaultPolicy;
    private final WeakIdentityMap delegateMap = new WeakIdentityMap();

    /**
     * Creates a new <code>LoaderSplitPolicyProvider</code> instance which
     * delegates to <code>loaderPolicy</code> any permission query/grant
     * operations involving protection domains or classes with the given class
     * loader, any child class loader of the given class loader, or the
     * <code>null</code> class loader; all other operations are delegated to
     * <code>defaultPolicy</code> (with the exception of <code>refresh</code>,
     * which applies to both policies).
     *
     * @param	loader class loader for which associated permission query/grant
     * 		operations should be forwarded to <code>loaderPolicy</code>
     * @param	loaderPolicy class loader-specific security policy provider
     * @param	defaultPolicy default security policy provider
     * @throws	NullPointerException if <code>loader</code>,
     * 		<code>loaderPolicy</code> or <code>defaultPolicy</code> is
     * 		<code>null</code>
     */
    public LoaderSplitPolicyProvider(ClassLoader loader,
				     Policy loaderPolicy,
				     Policy defaultPolicy)
    {
	if (loader == null || loaderPolicy == null || defaultPolicy == null) {
	    throw new NullPointerException();
	}
	this.loader = loader;
	this.loaderPolicy = loaderPolicy;
	this.defaultPolicy = defaultPolicy;
	ensureDependenciesResolved();
    }

    /**
     * Delegates to the corresponding <code>getPermissions</code> method of the
     * underlying default policy.
     * 
     * @param	source code source for which to look up permissions
     * @return	set of permissions allowed for the given code source
     */
    public PermissionCollection getPermissions(CodeSource source) {
	return defaultPolicy.getPermissions(source);
    }

    /**
     * If the given protection domain is the protection domain of this class,
     * then a newly-created <code>PermissionCollection</code> containing {@link
     * AllPermission} is returned.  If not, delegates to the corresponding
     * <code>getPermissions</code> method of the underlying policy associated
     * with the loader of the given class (the loader-specific policy if the
     * class loader is <code>null</code>, the same as or a child of the loader
     * specified in the constructor for this instance, or the default loader
     * otherwise).
     * 
     * @param	domain protection domain for which to look up permissions
     * @return	set of permissions allowed for given protection domain
     */
    public PermissionCollection getPermissions(ProtectionDomain domain) {
	if (domain == myDomain) {
	    PermissionCollection pc = new Permissions();
	    pc.add(new AllPermission());
	    return pc;
	} else {
	    return getDelegate(domain.getClassLoader()).getPermissions(domain);
	}
    }

    /**
     * If the given protection domain is the protection domain of this class,
     * then <code>true</code> is returned.  If not, delegates to the
     * <code>implies</code> method of the underlying policy associated with the
     * loader of the given class (the loader-specific policy if the class
     * loader is <code>null</code>, the same as or a child of the loader
     * specified in the constructor for this instance, or the default loader
     * otherwise).
     * 
     * @param	domain protection domain in which to check implication
     * @param	permission permission to test implication of
     * @return	<code>true</code> if permission is implied by permissions of
     * 		given protection domain, <code>false</code> otherwise
     */
    public boolean implies(ProtectionDomain domain, Permission permission) {
	return domain == myDomain ||
	       getDelegate(domain.getClassLoader()).implies(domain,
							    permission);
    }

    /**
     * Invokes <code>refresh</code> on both the loader-specific and default
     * underlying policy providers.
     */
    public void refresh() {
	loaderPolicy.refresh();
	defaultPolicy.refresh();
    }

    /**
     * Returns <code>true</code> if both of the underlying policy providers
     * implement {@link DynamicPolicy} and return <code>true</code> from calls
     * to <code>grantSupported</code>; returns <code>false</code> otherwise.
     *
     * @return	{@inheritDoc}
     */
    public boolean grantSupported() {
	return loaderPolicy instanceof DynamicPolicy &&
	       ((DynamicPolicy) loaderPolicy).grantSupported() &&
	       defaultPolicy instanceof DynamicPolicy &&
	       ((DynamicPolicy) defaultPolicy).grantSupported();
    }

    /**
     * If both underlying policy providers support dynamic grants, delegates to
     * the <code>grant</code> method of the underlying policy associated with
     * the loader of the given class (the loader-specific policy if the class
     * loader is <code>null</code>, the same as or a child of the loader
     * specified in the constructor for this instance, or the default loader
     * otherwise).  If at least one of the underlying policy providers does not
     * support dynamic grants, throws an
     * <code>UnsupportedOperationException</code>.
     *
     * @param   cl {@inheritDoc}
     * @param	principals {@inheritDoc}
     * @param	permissions {@inheritDoc}
     * @throws	UnsupportedOperationException {@inheritDoc}
     * @throws	SecurityException {@inheritDoc}
     * @throws	NullPointerException {@inheritDoc}
     */
    public void grant(Class cl, 
		      Principal[] principals, 
		      Permission[] permissions) 
    {
	if (!grantSupported()) {
	    throw new UnsupportedOperationException("grants not supported");
	}
	((DynamicPolicy) getDelegate(getClassLoader(cl))).grant(
						cl, principals, permissions);
    }

    /**
     * If both underlying policy providers support dynamic grants, delegates to
     * the <code>getGrants</code> method of the underlying policy associated
     * with the loader of the given class (the loader-specific policy if the
     * class loader is <code>null</code>, the same as or a child of the loader
     * specified in the constructor for this instance, or the default loader
     * otherwise).  If at least one of the underlying policy providers does not
     * support dynamic grants, throws an
     * <code>UnsupportedOperationException</code>.
     *
     * @param   cl {@inheritDoc}
     * @param   principals {@inheritDoc}
     * @return  {@inheritDoc}
     * @throws	UnsupportedOperationException {@inheritDoc}
     * @throws	NullPointerException {@inheritDoc}
     */
    public Permission[] getGrants(Class cl, Principal[] principals) {
	if (!grantSupported()) {
	    throw new UnsupportedOperationException("grants not supported");
	}
	return ((DynamicPolicy) getDelegate(getClassLoader(cl))).getGrants(
							       cl, principals);
    }

    /**
     * Ensures that any classes depended on by this policy provider are
     * resolved.  This is to preclude lazy resolution of such classes during
     * operation of the provider, which can result in deadlock as described by
     * bug 4911907.
     */
    private void ensureDependenciesResolved() {
	// force class resolution by pre-invoking method called by implies()
	getDelegate(loader);
    }

    private Policy getDelegate(final ClassLoader ldr) {
	if (ldr == null) {
	    /* This special case is needed so that implies queries from the
	     * BasicInvocationDispatcher.checkClientPermission method
	     * will be handled by the loader-specific (i.e., service) policy.
	     */
	    return loaderPolicy;
	}
	Policy p;
	synchronized (delegateMap) {
	    p = (Policy) delegateMap.get(ldr);
	}
	if (p == null) {
	    p = (Policy) AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    for (ClassLoader l = ldr; l != null; l = l.getParent())
		    {
			if (l == loader) {
			    return loaderPolicy;
			}
		    }
		    return defaultPolicy;
		}
	    });
	    synchronized (delegateMap) {
		delegateMap.put(ldr, p);
	    }
	}
	return p;
    }

    private static ClassLoader getClassLoader(final Class cl) {
	return (ClassLoader) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() { return cl.getClassLoader(); }
	    });
    }
}
