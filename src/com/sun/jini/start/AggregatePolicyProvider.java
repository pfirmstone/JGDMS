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
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Permissions;
import java.security.PermissionCollection;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.Security;
import java.security.SecurityPermission;
import java.util.Map;
import java.util.WeakHashMap;
import net.jini.security.SecurityContext;
import net.jini.security.policy.DynamicPolicy;
import net.jini.security.policy.PolicyInitializationException;
import net.jini.security.policy.SecurityContextSource;

/**
 * Security policy provider which supports associating security sub-policies
 * with context class loaders.  Permission queries and grants (if supported),
 * as well as <code>implies</code> and <code>refresh</code> operations are
 * delegated to the currently active sub-policy.
 * <p>
 * The currently active sub-policy is determined as follows: if the current
 * thread does not override the {@link Thread#getContextClassLoader
 * getContextClassLoader} method, then that method is called to obtain the
 * context class loader.  If the context class loader is associated with a
 * sub-policy (via a previous call to <code>setPolicy</code>), then that
 * sub-policy is the currently active sub-policy.  If no such association
 * exists, then the same check is performed on each non-<code>null</code>
 * parent of the context class loader, proceeding up the chain of class loader
 * delegation, until a sub-policy association is found, in which case the
 * associated sub-policy is the currently active sub-policy.  If no sub-policy
 * association is found for the context class loader or any of its parents,
 * then a fallback sub-policy, the main policy, is the currently active
 * sub-policy.  Also, if the current thread overrides the
 * <code>getContextClassLoader</code> method, then
 * <code>getContextClassLoader</code> is not called and the main policy is the
 * currently active sub-policy.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class AggregatePolicyProvider 
    extends Policy implements DynamicPolicy, SecurityContextSource
{
    private static final String mainPolicyClassProperty =
	"com.sun.jini.start.AggregatePolicyProvider.mainPolicyClass";
    private static final String defaultMainPolicyClass =
	"net.jini.security.policy.DynamicPolicyProvider";

    private static final Map trustGetCCL = new WeakHashMap();
    private static final ProtectionDomain myDomain = (ProtectionDomain)
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		return AggregatePolicyProvider.class.getProtectionDomain();
	    }
	});

    private WeakIdentityMap subPolicies = new WeakIdentityMap();
    private WeakIdentityMap subPolicyCache = new WeakIdentityMap();
    private Policy mainPolicy;

    /**
     * Creates a new <code>AggregatePolicyProvider</code> instance, containing
     * a main policy created as follows: if the
     * <code>com.sun.jini.start.AggregatePolicyProvider.mainPolicyClass</code>
     * security property is set, then its value is interpreted as the class
     * name of the main policy provider; otherwise, a default class name of
     * <code>"net.jini.security.policy.DynamicPolicyProvider"</code>
     * is used.  The main policy is then instantiated using the no-arg public
     * constructor of the named class.  If the main policy class is not found,
     * is not instantiable via a public no-arg constructor, or if invocation of
     * its constructor fails, then a <code>PolicyInitializationException</code>
     * is thrown.
     * <p>
     * Note that this constructor requires the appropriate
     * <code>"getProperty"</code> {@link SecurityPermission} to read the
     * <code>com.sun.jini.start.AggregatePolicyProvider.mainPolicyClass</code>
     * security property, and may require <code>"accessClassInPackage.*"</code>
     * {@link RuntimePermission}s, depending on the package of the main policy
     * class.
     *
     * @throws  PolicyInitializationException if unable to construct the main
     *          policy
     * @throws  SecurityException if there is a security manager and the
     *          calling context does not have <code>SecurityPermission</code>
     *          for reading the
     *          <code>com.sun.jini.start.AggregatePolicyProvider.mainPolicy</code>
     *          security property, or if the calling context does not have
     *          adequate permissions to access the main policy class
     */
    public AggregatePolicyProvider() throws PolicyInitializationException {
	String cname = Security.getProperty(mainPolicyClassProperty);
	if (cname == null) {
	    cname = defaultMainPolicyClass;
	}
	try {
	    mainPolicy = (Policy) Class.forName(cname).newInstance();
	} catch (SecurityException e) {
	    throw e;
	} catch (Exception e) {
	    throw new PolicyInitializationException(
		"unable to construct main policy", e);
	}
	ensureDependenciesResolved();
    }

    /**
     * Creates a new <code>AggregatePolicyProvider</code> instance with the
     * given main policy, which must be non-<code>null</code>.
     *
     * @param   mainPolicy main policy
     * @throws  NullPointerException if main policy is <code>null</code>
     */
    public AggregatePolicyProvider(Policy mainPolicy) {
	if (mainPolicy == null) {
	    throw new NullPointerException();
	}
	this.mainPolicy = mainPolicy;
	ensureDependenciesResolved();
    }

    /**
     * Delegates to the corresponding <code>getPermissions</code> method of the
     * currently active sub-policy to return the set of permissions allowed for
     * code from the specified code source, as a newly-created mutable
     * <code>PermissionCollection</code> which supports heterogeneous
     * permission types.
     * 
     * @param	source code source for which to look up permissions
     * @return	set of permissions allowed for the given code source
     */
    public PermissionCollection getPermissions(CodeSource source) {
	return getCurrentSubPolicy().getPermissions(source);
    }

    /**
     * If the given protection domain is the protection domain of this class,
     * then a newly-created <code>PermissionCollection</code> containing {@link
     * AllPermission} is returned.  Otherwise, delegates to the corresponding
     * <code>getPermissions</code> method of the currently active sub-policy to
     * return the set of permissions allowed for code in the specified
     * protection domain, as a newly-created mutable
     * <code>PermissionCollection</code> which supports heterogeneous
     * permission types.
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
	    return getCurrentSubPolicy().getPermissions(domain);
	}
    }

    /**
     * If the given protection domain is the protection domain of this class,
     * then <code>true</code> is returned.  Otherwise, delegates to the
     * <code>implies</code> method of the currently active sub-policy to
     * determine if the given permission is implied by the permissions for the
     * specified protection domain.
     * 
     * @param	domain protection domain in which to check implication
     * @param	permission permission to test implication of
     * @return	<code>true</code> if permission is implied by permissions of
     * 		given protection domain, <code>false</code> otherwise
     */
    public boolean implies(ProtectionDomain domain, Permission permission) {
	return (domain == myDomain) || 
	       getCurrentSubPolicy().implies(domain, permission);
    }

    /**
     * Refreshes the currently active sub-policy by delegating to its
     * <code>refresh</code> method.
     */
    public void refresh() {
	getCurrentSubPolicy().refresh();
    }

    /**
     * Changes sub-policy association with given class loader.  If
     * <code>subPolicy</code> is non-<code>null</code>, then it is used as a
     * new sub-policy to associate with the given class loader, overriding any
     * previous sub-policy associated with the loader.  If
     * <code>subPolicy</code> is <code>null</code>, then any previous
     * association between a sub-policy and the given class loader is removed.
     * If loader is <code>null</code>, then <code>subPolicy</code> is used as
     * the new main policy, and must be non-<code>null</code>.  If there is a
     * security manager, its <code>checkPermission</code> method is called with
     * the <code>"setPolicy"</code> {@link SecurityPermission}.
     * 
     * @param   loader class loader with which to associate sub-policy, or
     * 		<code>null</code> if setting main policy
     * @param   subPolicy sub-policy to associate with given class loader, or
     *          <code>null</code> if removing sub-policy association
     * @throws  NullPointerException if both <code>loader</code> and
     * 		<code>subPolicy</code> are <code>null</code>
     * @throws  SecurityException if there is a security manager and the
     * 		calling context does not have the <code>"setPolicy"
     * 		SecurityPermission</code>
     */
    public void setPolicy(ClassLoader loader, Policy subPolicy) {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(new SecurityPermission("setPolicy"));
	}
	synchronized (subPolicies) {
	    subPolicyCache.clear();
	    if (loader != null) {
		if (subPolicy != null) {
		    subPolicies.put(loader, subPolicy);
		    subPolicyCache.put(loader, subPolicy);
		} else {
		    subPolicies.remove(loader);
		}
	    } else {
		if (subPolicy == null) {
		    throw new NullPointerException();
		}
		mainPolicy = subPolicy;
	    }
	}
    }

    /**
     * Returns <code>true</code> if the currently active sub-policy supports
     * dynamic grants; this is determined by delegating to the
     * <code>grantSupported</code> method of the currently active sub-policy if
     * it implements the {@link DynamicPolicy} interface.  If the currently
     * active sub-policy does not implement <code>DynamicPolicy</code>, then
     * <code>false</code> is returned.
     *
     * @return <code>true</code> if the currently active sub-policy supports
     * dynamic grants, or <code>false</code> otherwise
     */
    public boolean grantSupported() {
	Policy p = getCurrentSubPolicy();
	return (p instanceof DynamicPolicy && 
		((DynamicPolicy) p).grantSupported());
    }

    /**
     * If the currently active sub-policy supports dynamic permission grants,
     * delegates to the corresponding <code>grant</code> method of the
     * currently active sub-policy to grant the specified permissions to all
     * protection domains (including ones not yet created) which are associated
     * with the class loader of the given class and possess at least the given
     * set of principals.
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
	Policy p = getCurrentSubPolicy();
	if (p instanceof DynamicPolicy) {
	    ((DynamicPolicy) p).grant(cl, principals, permissions);
	} else {
	    throw new UnsupportedOperationException("grants not supported");
	}
    }

    /**
     * If the currently active sub-policy supports dynamic permission grants,
     * delegates to the corresponding <code>getGrants</code> method of the
     * currently active sub-policy to return a new array containing the set of
     * permissions dynamically granted to protection domains which are
     * associated with the class loader of the given class and possess at least
     * the given set of principals.
     *
     * @param   cl {@inheritDoc}
     * @param   principals {@inheritDoc}
     * @return  {@inheritDoc}
     * @throws	UnsupportedOperationException {@inheritDoc}
     * @throws	NullPointerException {@inheritDoc}
     */
    public Permission[] getGrants(Class cl, Principal[] principals) {
	Policy p = getCurrentSubPolicy();
	if (p instanceof DynamicPolicy) {
	    return ((DynamicPolicy) p).getGrants(cl, principals);
	} else {
	    throw new UnsupportedOperationException("grants not supported");
	}
    }

    /**
     * Returns a snapshot of the current security context, which can be used to
     * restore the context at a later time.
     * <p>
     * The security context returned by this method contains the security
     * context of the currently active sub-policy (or an equivalent of the
     * default security context described in the documentation for {@link
     * net.jini.security.Security#getContext
     * Security.getContext}, if the currently active sub-policy does not
     * implement {@link SecurityContextSource}), as well as the current context
     * class loader.  The privileged action wrappers it creates restore the
     * saved context class loader before delegating to the action wrappers of
     * the underlying sub-policy security context.  The
     * <code>getAccessControlContext</code> method of the returned security
     * context delegates to the corresponding method of the sub-policy security
     * context.
     *
     * @return  {@inheritDoc}
     */
    public SecurityContext getContext() {
	Policy p = getCurrentSubPolicy();
	SecurityContext sc = (p instanceof SecurityContextSource) ? 
	    ((SecurityContextSource) p).getContext() :
	    new DefaultSecurityContext();
	return new AggregateSecurityContext(sc);
    }

    /**
     * Ensures that any classes depended on by this policy provider are
     * resolved.  This is to preclude lazy resolution of such classes during
     * operation of the provider, which can result in deadlock as described by
     * bug 4911907.
     */
    private void ensureDependenciesResolved() {
	// get any non-null class loader
	ClassLoader ldr = getClass().getClassLoader();
	if (ldr == null) {
	    ldr = ClassLoader.getSystemClassLoader();
	}

	// force class resolution by pre-invoking methods called by implies()
	trustGetContextClassLoader0(Thread.class);
	getContextClassLoader();
	synchronized (subPolicies) {
	    lookupSubPolicy(ldr);
	}
    }

    /**
     * Returns currently active sub-policy.
     */
    private Policy getCurrentSubPolicy() {
	final Thread t = Thread.currentThread();
	if (!trustGetContextClassLoader(t)) {
	    return mainPolicy;
	}
	ClassLoader ccl = getContextClassLoader();
	synchronized (subPolicies) {
	    Policy policy = (Policy) subPolicyCache.get(ccl);
	    if (policy == null) {
		policy = lookupSubPolicy(ccl);
		subPolicyCache.put(ccl, policy);
	    }
	    return policy;
	}
    }

    /**
     * Returns sub-policy associated with the given class loader.  This method
     * should only be called when already synchronized on subPolicies.
     */
    private Policy lookupSubPolicy(final ClassLoader ldr) {
	assert Thread.holdsLock(subPolicies);
	return (Policy) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    for (ClassLoader l = ldr; l != null; l = l.getParent()) {
			Policy p = (Policy) subPolicies.get(l);
			if (p != null) {
			    return p;
			}
		    }
		    return mainPolicy;
		}
	    });
    }

    /**
     * Returns current context class loader.
     */
    static ClassLoader getContextClassLoader() {
	return (ClassLoader) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    return Thread.currentThread().getContextClassLoader();
		}
	    });
    }

    /**
     * Returns true if the given thread does not override
     * Thread.getContextClassLoader(), false otherwise.
     */
    private static boolean trustGetContextClassLoader(Thread t) {
	Class cl = t.getClass();
	if (cl == Thread.class) {
	    return true;
	}
	
	Boolean b;
	synchronized (trustGetCCL) {
	    b = (Boolean) trustGetCCL.get(cl);
	}
	if (b == null) {
	    b = trustGetContextClassLoader0(cl);
	    synchronized (trustGetCCL) {
		trustGetCCL.put(cl, b);
	    }
	}
	return b.booleanValue();
    }

    private static Boolean trustGetContextClassLoader0(final Class cl) {
	return (Boolean) AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		try {
		    Method m = cl.getMethod(
			"getContextClassLoader", new Class[0]);
		    return Boolean.valueOf(m.getDeclaringClass() == Thread.class);
		} catch (NoSuchMethodException ex) {
		    throw new InternalError(
			"Thread.getContextClassLoader() not found");
		}
	    }
	});
    }

    /**
     * Stand-in "default" security context for sub-policies that do not
     * implement SecurityContextSource.
     */
    private static class DefaultSecurityContext implements SecurityContext {

	private final AccessControlContext acc =
	    AccessController.getContext();

	public PrivilegedAction wrap(PrivilegedAction a) {
	    if (a == null) {
		throw new NullPointerException();
	    }
	    return a;
	}

	public PrivilegedExceptionAction wrap(PrivilegedExceptionAction a) {
	    if (a == null) {
		throw new NullPointerException();
	    }
	    return a;
	}

	public AccessControlContext getAccessControlContext() {
	    return acc;
	}
    }

    /**
     * Security context that produces privileged action wrappers which restore
     * the context class loader before delegating to the sub-policy context's
     * wrapped action.
     */
    private static class AggregateSecurityContext implements SecurityContext {

	private final ClassLoader ccl = getContextClassLoader();
	private final SecurityContext sc;

	AggregateSecurityContext(SecurityContext sc) {
	    if (sc == null) {
		throw new NullPointerException();
	    }
	    this.sc = sc;
	}

	public PrivilegedAction wrap(PrivilegedAction a) {
	    final PrivilegedAction wa = sc.wrap(a);
	    return new PrivilegedAction() {
		public Object run() {
		    ClassLoader sccl = setCCL(ccl, false);
		    try {
			return wa.run();
		    } finally {
			setCCL(sccl, sccl != ccl);
		    }
		}
	    };
	}

	public PrivilegedExceptionAction wrap(PrivilegedExceptionAction a) {
	    final PrivilegedExceptionAction wa = sc.wrap(a);
	    return new PrivilegedExceptionAction() {
		public Object run() throws Exception {
		    ClassLoader sccl = setCCL(ccl, false);
		    try {
			return wa.run();
		    } finally {
			setCCL(sccl, sccl != ccl);
		    }
		}
	    };
	}

	public AccessControlContext getAccessControlContext() {
	    return sc.getAccessControlContext();
	}

	private ClassLoader setCCL(final ClassLoader ldr, final boolean force) {
	    return (ClassLoader) AccessController.doPrivileged(
		new PrivilegedAction() {
		    public Object run() {
			Thread t = Thread.currentThread();
			ClassLoader old = null;
			if (force || ldr != (old = t.getContextClassLoader())) {
			    t.setContextClassLoader(ldr);
			}
			return old;
		    }
		});
	}
    }
}
