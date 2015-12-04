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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.security.Permission;
import java.util.Collections;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;

/**
 * A <code>ProxyPreparer</code> for verifying that proxies are trusted,
 * granting them dynamic permissions, and setting their constraints, as well as
 * for creating other proxy preparer subclasses that include those
 * operations. <p>
 *
 * Applications and configurations can use this class to create proxy preparers
 * for several common cases. Some examples include creating proxy preparers
 * that: <ul>
 *
 * <li> Verify trust, grant permissions, and set new constraints, to prepare a
 * proxy received from an untrusted source.
 *
 * <li> Use the proxy's existing constraints when verifying trust in the proxy,
 * to prepare a trusted proxy received with integrity protection from a trusted
 * source that supplies constraints, confirming that the proxy's implementation
 * is trusted locally, but allowing the constraints to be downloaded from a
 * third party.
 *
 * <li> Set new constraints, to prepare a trusted proxy received with integrity
 * protection from a trusted source that is not known to supply the appropriate
 * constraints.
 *
 * <li> Grant permissions, to prepare a trusted proxy received with integrity
 * protection from a trusted source that supplies appropriate constraints, if
 * the proxy needs permission grants.
 *
 * <li> Do nothing, to use as a default when retrieving an optional
 * configuration entry, or to prepare a non-secure proxy. </ul>
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class BasicProxyPreparer implements ProxyPreparer, Serializable {

    private static final long serialVersionUID = 4439691869768577046L;

    /**
     * @serialField verify boolean
     *		    Whether to verify if proxies are trusted.
     * @serialField methodConstraintsSpecified boolean
     *		    Whether to use <code>methodConstraints</code> when
     *		    verifying if proxies are trusted and for setting their
     *		    constraints.
     * @serialField methodConstraints MethodConstraints
     *		    Method constraints to use when verifying if proxies are
     *		    trusted and for setting their constraints, if
     *		    <code>methodConstraintsSpecified</code> is
     *		    <code>true</code>. Set to <code>null</code> if
     *		    <code>methodConstraintsSpecified</code> is
     *		    <code>false</code>.
     * @serialField permissions Permission[]
     *		    Permissions to grant to proxies, or an empty array if no
     *		    permissions should be granted. The value is always
     *		    non-<code>null</code>.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
	new ObjectStreamField("verify", boolean.class),
	new ObjectStreamField("methodConstraintsSpecified", boolean.class),
	new ObjectStreamField("methodConstraints", MethodConstraints.class),
	/* Make sure the deserialized permissions array is not shared */
        new ObjectStreamField("permissions", Permission[].class, true)
    };

    /** Whether to verify if proxies are trusted. */
    protected final boolean verify;

    /**
     * Whether to use {@link #methodConstraints} when verifying if proxies are
     * trusted and for setting their constraints.
     */
    protected final boolean methodConstraintsSpecified;

    /**
     * Method constraints to use when verifying if proxies are trusted and for
     * setting their constraints, if {@link #methodConstraintsSpecified} is
     * <code>true</code>. Set to <code>null</code> if
     * <code>methodConstraintsSpecified</code> is <code>false</code>.
     */
    protected final MethodConstraints methodConstraints;

    /**
     * Permissions to grant to proxies, or an empty array if no permissions
     * should be granted. The value is always non-<code>null</code>.
     */
    protected final Permission[] permissions;

    /**
     * Creates a proxy preparer that specifies not to verify proxies, grant
     * them permissions, or set their constraints.
     */
    public BasicProxyPreparer() {
	verify = false;
	methodConstraintsSpecified = false;
	methodConstraints = null;
	permissions = new Permission[0];
    }

    /**
     * Creates a proxy preparer that specifies whether proxies should be
     * verified, using the constraints on the proxy by default, and specifies
     * what permissions to grant to proxies.
     *
     * @param verify whether to verify if proxies are trusted
     * @param permissions permissions to grant, or <code>null</code> if no
     *	      permissions should be granted
     * @throws NullPointerException if <code>permissions</code> is not
     *	       <code>null</code> and any of its elements are <code>null</code>
     */
    public BasicProxyPreparer(boolean verify, Permission[] permissions) {
	this.verify = verify;
	methodConstraintsSpecified = false;
	methodConstraints = null;
	this.permissions = checkPermissions(permissions);
    }

    /* Copies the argument, if needed, and checks for null elements. */
    private static Permission[] checkPermissions(Permission[] permissions) {
	if (permissions == null) {
	    return new Permission[0];
	}
	permissions = (Permission[]) permissions.clone();
	for (int i = permissions.length; --i >= 0; ) {
	    if (permissions[i] == null) {
		throw new NullPointerException("Permission cannot be null");
	    }
	}
	return permissions;
    }

    /**
     * Creates a proxy preparer that specifies whether proxies should be
     * verified, specifies permissions to grant them, and specifies what method
     * constraints to use when verifying and setting constraints.
     *
     * @param verify whether to verify if proxies are trusted
     * @param methodConstraints method constraints to use when verifying 
     *	      and setting constraints
     * @param permissions permissions to grant, or <code>null</code> if no
     *	      permissions should be granted
     * @throws NullPointerException if <code>permissions</code> is not
     *	       <code>null</code> and any of its elements are <code>null</code>
     */
    public BasicProxyPreparer(boolean verify,
			      MethodConstraints methodConstraints,
			      Permission[] permissions)
    {
	this.verify = verify;
	this.methodConstraintsSpecified = true;
	this.methodConstraints = methodConstraints;
	this.permissions = checkPermissions(permissions);
    }

    /**
     * Returns the method constraints to use when verifying and setting
     * constraints on the specified proxy. <p>
     *
     * The default implementation returns the value of {@link
     * #methodConstraints} if {@link #methodConstraintsSpecified} is
     * <code>true</code>, else returns the constraints on the specified proxy
     * if it implements {@link RemoteMethodControl}, else returns
     * <code>null</code>. <p>
     *
     * Subclasses may wish to override this method, for example, to augment the
     * existing constraints on the proxy rather than replacing them.
     *
     * @param proxy the proxy being prepared
     * @return the method constraints to use when verifying and setting
     *	       constraints on the proxy
     */
    protected MethodConstraints getMethodConstraints(Object proxy) {
	if (methodConstraintsSpecified) {
	    return methodConstraints;
	} else if (proxy instanceof RemoteMethodControl) {
	    return ((RemoteMethodControl) proxy).getConstraints();
	} else {
	    return null;
	}
    }

    /**
     * Returns the permissions to grant to proxies, or an empty array if no
     * permissions should be granted. The return value need not be newly
     * created, but cannot be <code>null</code>. <p>
     *
     * The default implementation returns the value of {@link
     * #permissions}. <p>
     *
     * Subclasses may wish to override this method, for example, to grant
     * permissions that depend on principal constraints found on the proxy.
     *
     * @param proxy the proxy being prepared
     * @return the permissions to grant to the proxy
     */
    protected Permission[] getPermissions(Object proxy) {
	return permissions;
    }

    /**
     * Performs operations on a proxy to prepare it for use, returning the
     * prepared proxy, which may or may not be the argument itself. <p>
     *
     * The default implementation provides the following behavior. If
     * <code>proxy</code> is <code>null</code>, throws a {@link
     * NullPointerException}. Otherwise, calls {@link #verify(Object) verify}
     * with <code>proxy</code>. If the <code>verify</code> call succeeds, calls
     * {@link #grant grant} with <code>proxy</code>. If the <code>grant</code>
     * call succeeds, returns the result of calling {@link #setConstraints
     * setConstraints} with <code>proxy</code>. <p>
     *
     * Subclasses may wish to override this method, for example, to perform
     * additional operations, typically calling the default implementation via
     * <code>super</code>.
     *
     * @param proxy the proxy to prepare
     * @return the prepared proxy
     * @throws NullPointerException if <code>proxy</code> is <code>null</code>
     * @throws RemoteException if a communication-related exception occurs
     * @throws SecurityException if a security exception occurs
     * @see #verify(Object) verify
     * @see #grant grant
     * @see #setConstraints setConstraints
     */
    public Object prepareProxy(Object proxy) throws RemoteException {
	if (proxy == null) {
	    throw new NullPointerException("Proxy cannot be null");
	}
	verify(proxy);
	grant(proxy);
	return setConstraints(proxy);
    }

    /**
     * Verifies that the proxy is trusted. Called by the default implementation
     * of {@link #prepareProxy prepareProxy}. <p>
     *
     * The default implementation provides the following behavior. If
     * <code>proxy</code> is <code>null</code>, throws a {@link
     * NullPointerException}. Otherwise, if {@link #verify} is
     * <code>true</code>, calls {@link Security#verifyObjectTrust
     * Security.verifyObjectTrust}, with <code>proxy</code>, <code>null</code>
     * for the class loader, and, for the context, a collection containing the
     * result of calling {@link #getMethodConstraints getMethodConstraints}
     * with <code>proxy</code>, or an empty collection if the constraints are
     * <code>null</code>. <p>
     *
     * Subclasses may wish to override this method, for example, to specify a
     * different class loader or context when verifying the proxy.
     *
     * @param proxy the proxy to verify
     * @throws NullPointerException if <code>proxy</code> is <code>null</code>
     * @throws RemoteException if a communication-related exception occurs
     * @throws SecurityException if verifying that the proxy is trusted fails
     * @see #prepareProxy prepareProxy
     * @see #getMethodConstraints getMethodConstraints
     * @see Security#verifyObjectTrust Security.verifyObjectTrust
     */
    protected void verify(Object proxy) throws RemoteException {
	if (proxy == null) {
	    throw new NullPointerException("Proxy cannot be null");
	} else if (verify) {
	    MethodConstraints mc = getMethodConstraints(proxy);
	    Security.verifyObjectTrust(proxy, null,
				       (mc == null
					? Collections.EMPTY_SET
					: Collections.singleton(mc)));
	}
    }

    /**
     * Grants permissions to the proxy. Called by the default implementation of
     * {@link #prepareProxy prepareProxy} unless {@link #verify(Object) verify}
     * throws an exception. <p>
     *
     * The default implementation provides the following behavior. If
     * <code>proxy</code> is <code>null</code>, throws a {@link
     * NullPointerException}. Otherwise, calls {@link #getPermissions
     * getPermissions} with <code>proxy</code> to determine what permissions
     * should be granted. If the permissions are not empty, calls {@link
     * Security#grant(Class,Permission[]) Security.grant}, with the proxy's
     * class as the class argument and those permissions. If <code>grant</code>
     * discovers that dynamic permission grants are not supported and throws a
     * {@link UnsupportedOperationException}, catches that exception and throws
     * a {@link SecurityException}. <p>
     *
     * Subclasses may wish to override this method, for example, to alter the
     * principals for which permission grants are made.
     *
     * @param proxy the proxy to grant permissions
     * @throws SecurityException if a security exception occurs
     * @throws NullPointerException if proxy is <code>null</code>
     * @see #prepareProxy prepareProxy
     * @see #getPermissions getPermissions
     * @see Security#grant(Class,Permission[]) Security.grant
     */
    protected void grant(Object proxy) {
	if (proxy == null) {
	    throw new NullPointerException("Proxy cannot be null");
	}
	Permission[] perms = getPermissions(proxy);
	if (perms.length > 0) {
	    try {
		Security.grant(proxy.getClass(), perms);
	    } catch (UnsupportedOperationException e) {
		SecurityException se = new SecurityException(
		    "Dynamic permission grants are not supported");
		se.initCause(e);
		throw se;
	    }
	}
    }

    /**
     * Sets constraints on the proxy. Called by the default implementation of
     * {@link #prepareProxy prepareProxy} unless {@link #verify(Object) verify}
     * or {@link #grant grant} throw an exception. <p>
     *
     * The default implementation provides the following behavior. If
     * <code>proxy</code> is <code>null</code>, throws a {@link
     * NullPointerException}. Otherwise, if {@link #methodConstraintsSpecified}
     * is <code>false</code>, returns the proxy, else if object does not
     * implement {@link RemoteMethodControl}, throws a {@link
     * SecurityException}, else returns the result of calling {@link
     * RemoteMethodControl#setConstraints RemoteMethodControl.setConstraints}
     * on the proxy, using the value returned from calling {@link
     * #getMethodConstraints getMethodConstraints} with <code>proxy</code>. <p>
     *
     * Subclasses may wish to override this method, for example, to support
     * verifying objects that do not implement {@link RemoteMethodControl}.
     *
     * @param proxy the proxy
     * @return the proxy with updated constraints
     * @throws NullPointerException if <code>proxy</code> is <code>null</code>
     * @throws SecurityException if a security exception occurs
     * @see #prepareProxy prepareProxy
     * @see #getMethodConstraints getMethodConstraints
     * @see RemoteMethodControl#setConstraints
     *	    RemoteMethodControl.setConstraints
     */
    protected Object setConstraints(Object proxy) {
	if (proxy == null) {
	    throw new NullPointerException("Proxy cannot be null");
	} else if (!methodConstraintsSpecified) {
	    return proxy;
	} else if (!(proxy instanceof RemoteMethodControl)) {
	    throw new SecurityException(
		"Proxy must implement RemoteMethodControl");
	} else {
	    return ((RemoteMethodControl) proxy).setConstraints(
		getMethodConstraints(proxy));
	}
    }

    /** Returns a string representation of this object. */
    public String toString() {
	String className = getClass().getName();
	int dot = className.lastIndexOf('.');
	if (dot >= 0) {
	    className = className.substring(dot + 1);
	}
	StringBuffer sb = new StringBuffer(className).append('[');
	if (verify) {
	    sb.append("verify");
	}
	if (methodConstraintsSpecified) {
	    if (verify) {
		sb.append(", ");
	    }
	    sb.append(methodConstraints);
	}
	if (permissions.length > 0) {
	    if (verify || methodConstraintsSpecified) {
		sb.append(", ");
	    }
	    sb.append('{');
	    for (int i = 0; i < permissions.length; i++) {
		if (i > 0) {
		    sb.append(", ");
		}
		sb.append(permissions[i]);
	    }
	    sb.append('}');
	}
	sb.append(']');
	return sb.toString();
    }

    /**
     * Returns <code>true</code> if the given object is an instance of the same
     * class as this object, with the same value for <code>verify</code>, with
     * method constraints that are <code>equals</code> or similarly not
     * specified, and with <code>permissions</code> containing the same
     * elements, independent of order.
     */
    public boolean equals(Object object) {
	if (this == object) {
	    return true;
	} else if (object == null || object.getClass() != getClass()) {
	    return false;
	}
	BasicProxyPreparer other = (BasicProxyPreparer) object;
	if (verify != other.verify) {
	    return false;
	}
	if (methodConstraintsSpecified != other.methodConstraintsSpecified) {
	    return false;
	} else if (methodConstraintsSpecified
		   && (methodConstraints == null
		       ? other.methodConstraints != null
		       : !methodConstraints.equals(other.methodConstraints)))
	{
	    return false;
	}
	if (permissions.length != other.permissions.length) {
	    return false;
	}
	/*
	 * Determine if the permissions contain the same elements, including
	 * duplicates.
	 */
	Permission[] otherPermissions =
	    (Permission[]) other.permissions.clone();
      top:
	for (int i = permissions.length; --i >= 0; ) {
	    Permission p = permissions[i];
	    for (int j = i; j >= 0; j--) {
		if (p.equals(otherPermissions[j])) {
		    otherPermissions[j] = otherPermissions[i];
		    continue top;
		}
	    }
	    return false;
	}
	return true;
    }

    /** Returns a hash code value for this object. */
    public int hashCode() {
	int hash = getClass().getName().hashCode();
	if (verify) {
	    hash += 1;
	}
	if (methodConstraintsSpecified) {
	    hash += 1<<16;
	    if (methodConstraints != null) {
		hash += methodConstraints.hashCode();
	    }
	}
	for (int i = permissions.length; --i >= 0; ) {
	    hash += permissions[i].hashCode();
	}
	return hash;
    }

    /**
     * Verifies that fields have legal values.
     *
     * @throws InvalidObjectException if
     *	       <code>methodConstraintsSpecified</code> is <code>false</code>
     *	       and <code>methodConstraints</code> is not <code>null</code>, if
     *	       <code>permissions</code> is <code>null</code>, or if
     *	       <code>permissions</code> is not <code>null</code> and any of its
     *	       elements are <code>null</code>
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the class of a serialized object could
     *	       not be found
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	if (!methodConstraintsSpecified && methodConstraints != null) {
	    throw new InvalidObjectException(
		"Method constraints not specified but not null");
	} 
	if (permissions == null) {
	    throw new InvalidObjectException("Permissions cannot be null");
	}
	for (int i = permissions.length; --i >= 0; ) {
	    if (permissions[i] == null) {
		throw new InvalidObjectException("Permission cannot be null");
	    }
	}
    }

    /**
     * Throws an exception to insure that data was supplied in order to set the
     * permissions field to an empty array.
     *
     * @throws InvalidObjectException whenever this method is called
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException("Permissions must be specified");
    }
}
