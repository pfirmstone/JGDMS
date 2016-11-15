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

package net.jini.security;

import java.rmi.RemoteException;
import java.security.Permission;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import org.apache.river.api.security.AdvisoryDynamicPermissions;
import static org.apache.river.api.security.AdvisoryDynamicPermissions.DEFAULT_PERMISSIONS;

/**
 * A <code>ProxyPreparer</code> for verifying that proxies are trusted,
 * dynamically granting permissions to trusted proxies, and optionally
 * setting the client constraints on trusted proxies.
 *
 * Determining permissions granted to verified and trusted proxies may be 
 * either passed explicitly via a constructor or if null may be advised by the
 * proxy using {@link AdvisoryDynamicPermissions}
 *
 * @author Sun Microsystems, Inc.
 * @since 2.1
 */
public final class VerifyingProxyPreparer implements ProxyPreparer {

    /** Set constraints on proxy from context. */
    private static final int SET_CONSTRAINTS = 1;
    /** No change to proxy or context. */
    private static final int AS_IS = 2;
    /** Add proxy's constraints to context. */
    private static final int ADD_CONSTRAINTS = 3;

    /** SET_CONSTRAINTS, AS_IS, or ADD_CONSTRAINTS. */
    private final int type;
    /** Class loader to pass to verifyObjectTrust. */
    private final ClassLoader loader;
    /** Trust verifier context elements. */
    private final Object[] contextElements;
    /** Principals to scope the permission grant, if any. */
    private final Principal[] principals;
    /** Permissions to dynamically grant. */
    private final Permission[] permissions;

    /**
     * Creates a proxy preparer that verifies proxies using the context
     * class loader and specified trust verifier context elements, dynamically
     * grants the specified permissions to trusted proxies for the principals
     * of the preparing thread's subject, and returns trusted proxies with
     * their client constraints set to the constraints specified as a trust
     * verifier context element. The arrays passed to this constructor are
     * neither modified nor retained; subsequent changes to the arrays have no
     * effect on the instance created.
     *
     * @param contextElements the trust verifier context elements
     * @param permissions the permissions to dynamically grant, or
     * <code>null</code> if no permissions should be granted
     * @throws NullPointerException if <code>contextElements</code> is
     * <code>null</code> or any element of <code>permissions</code> is
     * <code>null</code>
     * @throws IllegalArgumentException if no element of
     * <code>contextElements</code> is an instance of {@link MethodConstraints}
     */
    public VerifyingProxyPreparer(Object[] contextElements,
				  Permission[] permissions)
    {
	this(null, contextElements, null, permissions);
    }

    /**
     * Creates a proxy preparer that verifies proxies using the specified
     * class loader and trust verifier context elements, dynamically grants
     * the specified permissions to trusted proxies for the specified
     * principals, and returns trusted proxies with their client constraints
     * set to the constraints specified as a trust verifier context element.
     * The arrays passed to this constructor are neither modified nor
     * retained; subsequent changes to the arrays have no effect on the
     * instance created.
     *
     * @param loader the class loader for finding trust verifiers, or
     * <code>null</code> to use the context class loader
     * @param contextElements the trust verifier context elements
     * @param principals minimum set of principals to which grants apply, or
     * <code>null</code> to use the principals of the preparing thread's
     * subject
     * @param permissions the permissions to dynamically grant, or
     * <code>null</code> if no permissions should be granted
     * @throws NullPointerException if <code>contextElements</code> is
     * <code>null</code> or any element of <code>principals</code> or
     * <code>permissions</code> is <code>null</code>
     * @throws IllegalArgumentException if no element of
     * <code>contextElements</code> is an instance of {@link MethodConstraints}
     */
    public VerifyingProxyPreparer(ClassLoader loader,
				  Object[] contextElements,
				  Principal[] principals,
				  Permission[] permissions)
    {
	type = SET_CONSTRAINTS;
	this.loader = loader;
	this.contextElements = (Object[]) contextElements.clone();
	this.principals = checkPrincipals(principals);
	this.permissions = checkPermissions(permissions);
	for (int i = this.contextElements.length; --i >= 0; ) {
	    if (this.contextElements[i] instanceof MethodConstraints) {
		return;
	    }
	}
	throw new IllegalArgumentException("no MethodConstraints in context");
    }

    /**
     * Creates a proxy preparer that verifies proxies using the specified
     * class loader and trust verifier context elements (optionally with
     * the proxy's client constraints as an additional context element),
     * dynamically grants the specified permissions to trusted proxies for the
     * specified principals, and returns trusted proxies with their original
     * client constraints intact. The arrays passed to this constructor are
     * neither modified nor retained; subsequent changes to the arrays have no
     * effect on the instance created.
     *
     * @param addProxyConstraints <code>true</code> if the proxy's client
     * constraints should be included as a trust verifier context element,
     * <code>false</code> otherwise
     * @param loader the class loader for finding trust verifiers, or
     * <code>null</code> to use the context class loader
     * @param contextElements the trust verifier context elements, or
     * <code>null</code> if no elements need to be supplied
     * @param principals minimum set of principals to which grants apply, or
     * <code>null</code> to use the principals of the preparing thread's
     * subject
     * @param permissions the permissions to dynamically grant, or
     * <code>null</code> if no permissions should be granted
     * @throws NullPointerException if any element of <code>principals</code>
     * or <code>permissions</code> is <code>null</code>
     */
    public VerifyingProxyPreparer(boolean addProxyConstraints,
				  ClassLoader loader,
				  Object[] contextElements,
				  Principal[] principals,
				  Permission[] permissions)
    {
	type = addProxyConstraints ? ADD_CONSTRAINTS : AS_IS;
	this.loader = loader;
	this.contextElements = contextElements == null ?
	    new Object[0] : (Object[]) contextElements.clone();
	this.principals = checkPrincipals(principals);
	this.permissions = checkPermissions(permissions);
    }

    /** Clones the argument, checks for null elements, returns non-null. */
    private static Permission[] checkPermissions(Permission[] permissions) {
	if (permissions == null) return DEFAULT_PERMISSIONS;
	permissions = (Permission[]) permissions.clone();
	for (int i = permissions.length; --i >= 0; ) {
	    if (permissions[i] == null) {
		throw new NullPointerException("permission cannot be null");
	    }
	}
	return permissions;
    }

    /** Clones the argument, checks for null elements. */
    private static Principal[] checkPrincipals(Principal[] principals) {
	if (principals == null) {
	    return null;
	}
	principals = (Principal[]) principals.clone();
	for (int i = principals.length; --i >= 0; ) {
	    if (principals[i] == null) {
		throw new NullPointerException("principal cannot be null");
	    }
	}
	return principals;
    }

    /**
     * Performs operations on a proxy to prepare it for use, returning the
     * prepared proxy, which may or may not be the argument itself.
     * <p>
     * If this preparer was created using the two-argument or four-argument
     * constructor, or using the five-argument constructor
     * with <code>addProxyConstraints</code> set to <code>true</code>, and if
     * the specified proxy is not an instance of {@link RemoteMethodControl},
     * then a <code>SecurityException</code> is thrown. Otherwise,
     * {@link Security#verifyObjectTrust Security.verifyObjectTrust} is
     * invoked with the specified proxy, the class loader that was passed to
     * the constructor of this preparer (or <code>null</code> if the
     * two-argument constructor was used), and a trust verifier context
     * collection containing all of the context elements that were passed to
     * the constructor of this preparer. If this preparer was created using
     * the five-arguent constructor with <code>addProxyConstraints</code>
     * set to <code>true</code>, then the proxy's client constraints (obtained
     * by calling {@link RemoteMethodControl#getConstraints getConstraints} on
     * the proxy) are included as an additional context element. Any exception
     * thrown by <code>verifyObjectTrust</code> is thrown by this method. If
     * this preparer was created with a non-<code>null</code> array of
     * principals and one or more permissions, then
     * {@link Security#grant(Class,Principal[],Permission[]) Security.grant}
     * is invoked with the proxy's class and those principals and permissions.
     * If this preparer was created with no array of principals (either
     * <code>null</code> was specified or the two-argument constructor was
     * used) but one or more permissions, then
     * {@link Security#grant(Class,Permission[]) Security.grant} is invoked
     * with the proxy's class and those permissions. In either case, if
     * <code>grant</code>
     * throws an {@link UnsupportedOperationException}, this method throws
     * a <code>SecurityException</code>. Finally, if this preparer was
     * created using the five-argument constructor, then the original proxy
     * is returned, otherwise what is returned is the result of calling
     * {@link RemoteMethodControl#setConstraints
     * RemoteMethodControl.setConstraints} on the proxy, passing the first
     * trust verifier context element that is an instance of
     * {@link MethodConstraints}.
     *
     * @param proxy the proxy to prepare
     * @return the prepared proxy
     * @throws NullPointerException if <code>proxy</code> is <code>null</code>
     * @throws RemoteException if a communication-related exception occurs
     * @throws SecurityException if a security exception occurs
     */
    public Object prepareProxy(Object proxy) throws RemoteException {
	if (proxy == null) {
	    throw new NullPointerException("proxy cannot be null");
	} else if (type != AS_IS && !(proxy instanceof RemoteMethodControl)) {
	    throw new SecurityException(
				  "proxy must implement RemoteMethodControl");
	}
	Object[] elts = contextElements;
	if (type == ADD_CONSTRAINTS) {
	    elts = new Object[contextElements.length + 1];
	    elts[0] = ((RemoteMethodControl) proxy).getConstraints();
	    System.arraycopy(contextElements, 0, elts, 1,
			     contextElements.length);
	}
	Security.verifyObjectTrust(proxy, loader,
				   Collections.unmodifiableCollection(
							Arrays.asList(elts)));
	if (permissions.length > 0) {
	    try {
		if (principals == null) {
		    Security.grant(proxy.getClass(), permissions);
		} else {
		    Security.grant(proxy.getClass(), principals, permissions);
		}
	    } catch (UnsupportedOperationException e) {
		SecurityException se = new SecurityException(
			       "dynamic permission grants are not supported");
		se.initCause(e);
		throw se;
	    }
	} else {
	    Permission [] perms;
	    Class klass = proxy.getClass();
	    ClassLoader ldr = klass.getClassLoader();
	    if (ldr instanceof AdvisoryDynamicPermissions) {
		perms = ((AdvisoryDynamicPermissions) ldr).getPermissions();
		if (perms.length > 0){
		    try {
			if (principals == null) {
			    Security.grant(klass, perms);
			} else {
			    Security.grant(klass, principals, perms);
	}
		    } catch (UnsupportedOperationException e) {
			Logger.getLogger("net.jini.security")
			    .config("Local configuration doesn't allow advisory dynamic permission grants, consider using a DynamicPolicy provider");
		    }
		}
	    }
	}
	if (type == SET_CONSTRAINTS) {
	    for (int i = 0; i < contextElements.length; i++) {
		Object elt = contextElements[i];
		if (elt instanceof MethodConstraints) {
		    return ((RemoteMethodControl) proxy).setConstraints(
						     (MethodConstraints) elt);
		}
	    }
	}
	return proxy;
    }

    /** Returns a string representation of this object. */
    public String toString() {
	StringBuffer sb = new StringBuffer("VerifyingProxyPreparer[");
	if (type != SET_CONSTRAINTS) {
	    sb.append(type == AS_IS ? "false, " : "true, ");
	}
	sb.append(loader);
	sb.append(", {");
	for (int i = 0; i < contextElements.length; i++) {
	    if (i > 0) {
		sb.append(", ");
	    }
	    sb.append(contextElements[i]);
	}
	if (principals == null) {
	    sb.append("}, null, {");
	} else {
	    sb.append("}, {");
	    for (int i = 0; i < principals.length; i++) {
		if (i > 0) {
		    sb.append(", ");
		}
		sb.append(principals[i]);
	    }
	    sb.append("}, {");
	}
	for (int i = 0; i < permissions.length; i++) {
	    if (i > 0) {
		sb.append(", ");
	    }
	    sb.append(permissions[i]);
	}
	sb.append("}]");
	return sb.toString();
    }

    /**
     * Returns <code>true</code> if the specified object and this object
     * are both instances of this class that were constructed with equivalent
     * arguments. The order of trust verifier context elements, principals,
     * and permissions in the arrays that were passed to the constructor is
     * not significant.
     */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (!(obj instanceof VerifyingProxyPreparer)) {
	    return false;
	}
	VerifyingProxyPreparer other = (VerifyingProxyPreparer) obj;
	if (type != other.type ||
	    loader != other.loader ||
	    contextElements.length != other.contextElements.length ||
	    (principals == null) != (other.principals == null) ||
	    (principals != null &&
	     principals.length != other.principals.length) ||
	    permissions.length != other.permissions.length)
	{
	    return false;
	}
	Object[] otherElts = (Object[]) other.contextElements.clone();
    context:
	for (int i = contextElements.length; --i >= 0; ) {
	    Object elt = contextElements[i];
	    for (int j = i; j >= 0; j--) {
		if (elt.equals(otherElts[j])) {
		    otherElts[j] = otherElts[i];
		    continue context;
		}
	    }
	    return false;
	}
	if (principals != null) {
	    Principal[] otherPrins = (Principal[]) other.principals.clone();
	prins:
	    for (int i = principals.length; --i >= 0; ) {
		Principal p = principals[i];
		for (int j = i; j >= 0; j--) {
		    if (p.equals(otherPrins[j])) {
			otherPrins[j] = otherPrins[i];
			continue prins;
		    }
		}
		return false;
	    }
	}
	Permission[] otherPerms = (Permission[]) other.permissions.clone();
    perms:
	for (int i = permissions.length; --i >= 0; ) {
	    Permission p = permissions[i];
	    for (int j = i; j >= 0; j--) {
		if (p.equals(otherPerms[j])) {
		    otherPerms[j] = otherPerms[i];
		    continue perms;
		}
	    }
	    return false;
	}
	return true;
    }

    /** Returns a hash code value for this object. */
    public int hashCode() {
	int hash = type;
	if (loader != null) {
	    hash += loader.hashCode();
	}
	for (int i = contextElements.length; --i >= 0; ) {
	    hash += contextElements[i].hashCode();
	}
	if (principals != null) {
	    for (int i = principals.length; --i >= 0; ) {
		hash += principals[i].hashCode();
	    }
	}
	for (int i = permissions.length; --i >= 0; ) {
	    hash += permissions[i].hashCode();
	}
	return hash;
    }
}
