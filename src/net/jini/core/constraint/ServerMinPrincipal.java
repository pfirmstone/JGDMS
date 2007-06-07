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
 * Represents a constraint on the server, such that if the server
 * authenticates itself, then it must authenticate itself as at least all of
 * the specified principals. The mechanisms and credentials used to
 * authenticate the server as those principals are not specified by this
 * constraint.
 * <p>
 * The use of an instance of this class does not imply directly a
 * {@link ServerAuthentication#YES} constraint; that must be specified
 * separately to ensure that the server actually authenticates itself.
 * Because this constraint is conditional on server authentication, it does
 * not conflict with {@link ServerAuthentication#NO}.
 * <p>
 * It is important to understand that specifying
 * <code>ServerAuthentication.YES</code> as a requirement <i>does not</i>
 * ensure that a server is to be trusted; it <i>does</i> ensure that the
 * server authenticates itself as someone, but it does not ensure that the
 * server authenticates itself as anyone in particular. Without knowing who
 * the server authenticated itself as, there is no basis for actually
 * trusting the server. The client generally needs to specify a
 * <code>ServerMinPrincipal</code> requirement in addition, or else verify
 * that the server has specified a satisfactory
 * <code>ServerMinPrincipal</code> requirement for each of the methods that
 * the client cares about.
 *
 * @author Sun Microsystems, Inc.
 * @see ServerAuthentication
 * @see net.jini.security.AuthenticationPermission
 * @since 2.0
 */
public final class ServerMinPrincipal
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 6082629466615675811L;

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
    public ServerMinPrincipal(Principal p) {
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
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     * @throws IllegalArgumentException if the argument is empty
     */
    public ServerMinPrincipal(Principal[] principals) {
	this.principals = Constraint.reduce(principals);
    }

    /**
     * Creates a constraint containing the specified principals, with
     * duplicates removed. The argument passed to the constructor is neither
     * modified nor retained; subsequent changes to that argument have no
     * effect on the instance created.
     *
     * @param c the principals
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     * @throws IllegalArgumentException if the argument is empty or the
     * elements do not all implement the <code>Principal</code> interface
     */
    public ServerMinPrincipal(Collection c) {
	principals = Constraint.reduce(c);
    }

    /**
     * Returns an immutable set of all of the principals. Any attempt to modify
     * the set results in an {@link UnsupportedOperationException} being
     * thrown.
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
	return (ServerMinPrincipal.class.hashCode() +
		Constraint.hash(principals));
    }

    /**
     * Two instances of this class are equal if they have the same principals
     * (ignoring order).
     */
    public boolean equals(Object obj) {
	return (obj instanceof ServerMinPrincipal &&
		Constraint.equal(principals,
				 ((ServerMinPrincipal) obj).principals));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return "ServerMinPrincipal" + Constraint.toString(principals);
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
