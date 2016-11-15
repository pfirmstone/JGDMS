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

package net.jini.security.proxytrust;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.DomainCombiner;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import net.jini.security.Security;
import net.jini.security.SecurityContext;

/**
 * A basic trust verifier context element that provides a security
 * context to use to restrict privileges when invoking methods on untrusted
 * objects, based on a specified set of permissions.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.1
 */
public final class BasicUntrustedObjectSecurityContext
    implements UntrustedObjectSecurityContext
{
    /** Empty codesource. */
    private static final CodeSource emptyCS =
	new CodeSource(null, (Certificate[]) null);
    /** ProtectionDomain containing the empty codesource. */
    private static final ProtectionDomain emptyPD =
	new ProtectionDomain(emptyCS, null, null, null);

    /** Permissions. */
    private final Permission[] permissions;
    /** EmptyPD plus specified permissions. */
    private final ProtectionDomain restrictedPD;

    /**
     * Constructs an instance with the specified permissions. The security
     * context returned by {@link #getContext getContext} will be restricted
     * to the permissions granted by default to all code plus the additional
     * specified permissions. The argument passed to the constructor is
     * neither modified nor retained; subsequent changes to that argument
     * have no effect on the instance created.
     *
     * @param permissions additional permissions to allow, or
     * <code>null</code> if no additional permissions should be allowed
     * @throws NullPointerException if any element of the argument is
     * <code>null</code>
     */
    public BasicUntrustedObjectSecurityContext(Permission[] permissions) {
	if (permissions == null || permissions.length == 0) {
	    this.permissions = new Permission[0];
	    restrictedPD = emptyPD;
	} else {
	    this.permissions = (Permission[]) permissions.clone();
	    PermissionCollection pc = new Permissions();
	    for (int i = this.permissions.length; --i >= 0; ) {
		Permission p = this.permissions[i];
		if (p == null) {
		    throw new NullPointerException(
					       "permission cannot be null");
		}
		pc.add(p);
	    }
	    restrictedPD = new ProtectionDomain(emptyCS, pc, null, null);
	}
    }

    /**
     * Returns a security context to use to restrict privileges when
     * invoking methods on untrusted objects. The returned context is
     * equivalent to the current security context (as returned by
     * {@link Security#getContext Security.getContext}) with an additional
     * protection domain combined into the access control context that
     * contains an empty {@link CodeSource} (<code>null</code> location and
     * certificates), a permission collection containing the permissions
     * used to construct this instance, <code>null</code> class loader, and
     * <code>null</code> principals.
     *
     * @return a security context to use to restrict privileges when
     * invoking methods on untrusted objects
     */
    public SecurityContext getContext() {
	final AccessControlContext acc0 = AccessController.getContext();
	final AccessControlContext racc = (AccessControlContext)
	    AccessController.doPrivileged(new PrivilegedAction() {
		public Object run() {
		    DomainCombiner comb0 = acc0.getDomainCombiner();
		    AccessControlContext acc1 =
			new AccessControlContext(acc0, new Combiner(comb0));
		    AccessControlContext acc2 = (AccessControlContext)
			AccessController.doPrivileged(new PrivilegedAction() {
				public Object run() {
				    return AccessController.getContext();
				}
			    }, acc1);
		    return new AccessControlContext(acc2, comb0);
		}
	    });
	return (SecurityContext) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    return Security.getContext();
		}
	    }, racc);
    }

    /**
     * DomainCombiner that uses specified combiner (if any) to combine in
     * restrictedPD.
     */
    private class Combiner implements DomainCombiner {
	private final DomainCombiner combiner;

	Combiner(DomainCombiner combiner) {
	    this.combiner = combiner;
	}

	public ProtectionDomain[] combine(ProtectionDomain[] current,
					  ProtectionDomain[] assigned)
	{
	    if (combiner != null) {
		return combiner.combine(new ProtectionDomain[]{restrictedPD},
					assigned);
	    } else if (assigned == null) {
		return new ProtectionDomain[]{restrictedPD};
	    }
	    ProtectionDomain[] pds = new ProtectionDomain[assigned.length + 1];
	    pds[0] = restrictedPD;
	    System.arraycopy(assigned, 0, pds, 1, assigned.length);
	    return pds;
	}
    }

    /** Returns a string representation of this object. */
    public String toString() {
	StringBuffer sb =
	    new StringBuffer("BasicUntrustedObjectAccessController{");
	for (int i = 0; i < permissions.length; i++) {
	    if (i > 0) {
		sb.append(", ");
	    }
	    sb.append(permissions[i]);
	}
	sb.append("}");
	return sb.toString();
    }

    /**
     * Returns <code>true</code> if the specified object and this object
     * are both instances of this class that were constructed with equivalent
     * permissions. The order of permissions in the array that was passed to
     * the constructor is not significant.
     */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (!(obj instanceof BasicUntrustedObjectSecurityContext)) {
	    return false;
	}
	Permission[] otherPerms =
	    ((BasicUntrustedObjectSecurityContext) obj).permissions;
	if (permissions.length != otherPerms.length) {
	    return false;
	}
	otherPerms = (Permission[]) otherPerms.clone();
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
	int hash = 0;
	for (int i = permissions.length; --i >= 0; ) {
	    hash += permissions[i].hashCode();
	}
	return hash;
    }
}
