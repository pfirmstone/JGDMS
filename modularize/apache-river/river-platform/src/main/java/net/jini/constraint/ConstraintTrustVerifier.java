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

package net.jini.constraint;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Iterator;
import java.util.Set;
import javax.security.auth.x500.X500Principal;
import javax.security.auth.kerberos.KerberosPrincipal;
import net.jini.core.constraint.*;
import net.jini.security.TrustVerifier;

/**
 * Trust verifier for instances of the constraint classes defined in the
 * {@link net.jini.core.constraint} package, and for the
 * {@link BasicMethodConstraints}, {@link X500Principal} and
 * {@link KerberosPrincipal} classes. This class is intended to be specified
 * in a resource to configure the operation of
 * {@link net.jini.security.Security#verifyObjectTrust
 * Security.verifyObjectTrust}.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class ConstraintTrustVerifier implements TrustVerifier {

    /**
     * Creates an instance.
     */
    public ConstraintTrustVerifier() {
    }

    /**
     * Returns <code>true</code> if the specified object is known to be
     * trusted to correctly implement its contract; returns <code>false</code>
     * otherwise. Returns <code>true</code> if any of the following conditions
     * holds, and returns <code>false</code> otherwise:
     * <ul>
     * <li>The object is an instance of any of the following classes:
     * <ul>
     * <li>{@link ClientAuthentication}
     * <li>{@link ClientMaxPrincipalType}
     * <li>{@link ClientMinPrincipalType}
     * <li>{@link Confidentiality}
     * <li>{@link DelegationAbsoluteTime}
     * <li>{@link DelegationRelativeTime}
     * <li>{@link Delegation}
     * <li>{@link Integrity}
     * <li>{@link ServerAuthentication}
     * </ul>
     * <li>The object is an instance of any of the following classes:
     * <ul>
     * <li>{@link ClientMinPrincipal}
     * <li>{@link ClientMaxPrincipal}
     * <li>{@link ServerMinPrincipal}
     * </ul>
     * and all of the principals in that object are trusted (determined by
     * calling the <code>isTrustedObject</code> method on the specified context
     * with each principal)
     * <li>The object is an instance of {@link ConstraintAlternatives} and all
     * of the constraint alternatives in that object are trusted (determined
     * by calling the <code>isTrustedObject</code> method on the specified
     * context with each constraint alternative)
     * <li>The object is an instance of {@link BasicMethodConstraints} and all
     * the {@link InvocationConstraints} instances in that object are trusted
     * (determined by calling the <code>isTrustedObject</code> method on the
     * specified context with each instance)
     * <li>The object is an instance of <code>InvocationConstraints</code> and
     * all of the constraints (both requirements and preferences) in that
     * object are trusted (determined by calling the
     * <code>isTrustedObject</code> method on the specified context with each
     * constraint)
     * <li>The object is an instance of {@link X500Principal} or
     * {@link KerberosPrincipal}
     * </ul>
     *
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
	throws RemoteException
    {
	if (obj == null || ctx == null) {
	    throw new NullPointerException();
	} else if (obj instanceof InvocationConstraint) {
	    Class c = obj.getClass();
	    if (c == ServerAuthentication.class ||
		c == ClientAuthentication.class ||
		c == Confidentiality.class ||
		c == Integrity.class ||
		c == ClientMinPrincipalType.class ||
		c == ClientMaxPrincipalType.class ||
		c == Delegation.class ||
		c == DelegationRelativeTime.class ||
		c == DelegationAbsoluteTime.class)
	    {
		return true;
	    } else if (c == ServerMinPrincipal.class) {
		return trusted(((ServerMinPrincipal) obj).elements(), ctx);
	    } else if (c == ClientMinPrincipal.class) {
		return trusted(((ClientMinPrincipal) obj).elements(), ctx);
	    } else if (c == ClientMaxPrincipal.class) {
		return trusted(((ClientMaxPrincipal) obj).elements(), ctx);
	    } else if (c == ConstraintAlternatives.class) {
		return trusted(((ConstraintAlternatives) obj).elements(), ctx);
	    } else {
		return false;
	    }
	} else if (obj instanceof BasicMethodConstraints) {
	    return trusted(
		       ((BasicMethodConstraints) obj).possibleConstraints(),
		       ctx);
	} else if (obj instanceof InvocationConstraints) {
	    InvocationConstraints ic = (InvocationConstraints) obj;
	    return (trusted(ic.requirements(), ctx) &&
		    trusted(ic.preferences(), ctx));
	} else if (obj instanceof Principal) {
	    Class c = obj.getClass();
	    return c == X500Principal.class || c == KerberosPrincipal.class;
	}
	return false;
    }


    /**
     * Returns true if all elements of the set are trusted instances.
     */
    private static boolean trusted(Set set, TrustVerifier.Context ctx)
	throws RemoteException
    {
	return trusted(set.iterator(), ctx);
    }

    /**
     * Returns true if all elements of the iterator are trusted instances.
     */
    private static boolean trusted(Iterator iter, TrustVerifier.Context ctx)
	throws RemoteException
    {
	while (iter.hasNext()) {
	    if (!ctx.isTrustedObject(iter.next())) {
		return false;
	    }
	}
	return true;
    }
}
