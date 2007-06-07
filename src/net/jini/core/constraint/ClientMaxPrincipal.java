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

package net.jini.core.constraint;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.Set;

/**
 * Represents a constraint on the client, such that if the client
 * authenticates itself, then it may only authenticate itself as one or more
 * of the specified principals. The mechanisms and credentials used to
 * authenticate the client as those principals are not specified by this
 * constraint. A client can use this constraint to limit how much of its
 * identity is exposed in remote calls. For example, if the client's subject
 * contains sufficient information to authenticate as two distinct
 * principals, the client might wish to limit authentication to just one of
 * the two, if the client believes the other is unnecessary for authorization
 * at the server and wants to avoid revealing that part of its identity to
 * the server.
 * <p>
 * The use of an instance of this class does not directly imply a
 * {@link ClientAuthentication#YES} constraint; that must be specified
 * separately to ensure that the client actually authenticates itself.
 * Because this constraint is conditional on client authentication, it does
 * not conflict with {@link ClientAuthentication#NO}.
 *
 * @author Sun Microsystems, Inc.
 * @see ClientAuthentication
 * @see ClientMaxPrincipalType
 * @see ClientMinPrincipal
 * @see ClientMinPrincipalType
 * @see net.jini.security.AuthenticationPermission
 * @since 2.0
 */
public final class ClientMaxPrincipal
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 8583596881949005395L;

    /**
     * @serialField principals Principal[] The principals.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("principals", Principal[].class, true)
    };

    /**
     * The principals.
     */
    private final Principal[] principals;

    /**
     * Creates a constraint containing the specified principal.  This
     * constructor is equivalent to calling a constructor with a
     * single-element array containing the specified principal.
     *
     * @param p the principal
     * @throws NullPointerException if the argument is <code>null</code>
     */
    public ClientMaxPrincipal(Principal p) {
	if (p == null) {
	    throw new NullPointerException("principal cannot be null");
	}
	principals = new Principal[]{p};
    }

    /**
     * Creates a constraint containing the specified principals, with
     * duplicates removed. The argument passed to the constructor is neither
     * modified nor retained; subsequent changes to that argument have no
     * effect on the instance created.
     *
     * @param principals the principals
     * @throws IllegalArgumentException if the argument is empty
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     */
    public ClientMaxPrincipal(Principal[] principals) {
	this.principals = Constraint.reduce(principals);
    }

    /**
     * Creates a constraint containing the specified principals, with
     * duplicates removed. The argument passed to the constructor is neither
     * modified nor retained; subsequent changes to that argument have no
     * effect on the instance created.
     *
     * @param c the principals
     * @throws IllegalArgumentException if the argument is empty or the
     * elements do not all implement the <code>Principal</code> interface
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     */
    public ClientMaxPrincipal(Collection c) {
	principals = Constraint.reduce(c);
    }

    /**
     * Returns an immutable set of all of the principals. Any attempt to
     * modify the set results in an {@link UnsupportedOperationException}
     * being thrown.
     *
     * @return an immutable set of all of the principals
     */
    public Set elements() {
	return new ArraySet(principals);
    }

    /**
     * Returns the principals, without copying.
     */
    Principal[] getPrincipals() {
	return principals;
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return (ClientMaxPrincipal.class.hashCode() +
		Constraint.hash(principals));
    }

    /**
     * Two instances of this class are equal if they have the same principals
     * (ignoring order).
     */
    public boolean equals(Object obj) {
	return (obj instanceof ClientMaxPrincipal &&
		Constraint.equal(principals,
				 ((ClientMaxPrincipal) obj).principals));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return "ClientMaxPrincipal" + Constraint.toString(principals);
    }

    /**
     * Verifies that there is at least one principal, that none of the
     * principals is <code>null</code>, and that there are no duplicates.
     *
     * @throws java.io.InvalidObjectException if there are no principals,
     * or any principal is <code>null</code>, or if there are duplicate
     * principals
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	Constraint.verify(principals);
    }
}
