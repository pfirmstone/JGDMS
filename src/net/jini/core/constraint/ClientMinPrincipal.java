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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Represents a constraint on the client, such that if the client
 * authenticates itself, then it must authenticate itself as at least all of
 * the specified principals. The mechanisms and credentials used to
 * authenticate the client as those principals are not specified by this
 * constraint. This constraint is intended for use by clients to control how
 * much of their identity is exposed in remote calls; it is not intended for
 * use by servers as an authorization mechanism. For example, if the client's
 * subject contains sufficient information to authenticate as two distinct
 * principals, the client might wish to ensure that it authenticates as one,
 * the other, or both, depending on what the client believes is necessary for
 * authorization at the server.
 * <p>
 * The use of an instance of this class does not directly imply a
 * {@link ClientAuthentication#YES} constraint; that must be specified
 * separately to ensure that the client actually authenticates itself.
 * Because this constraint is conditional on client authentication, it does
 * not conflict with {@link ClientAuthentication#NO}.
 *
 * @author Sun Microsystems, Inc.
 * @see ClientAuthentication
 * @see ClientMaxPrincipal
 * @see ClientMaxPrincipalType
 * @see ClientMinPrincipalType
 * @see net.jini.security.AuthenticationPermission
 * @since 2.0
 */
@AtomicSerial
public final class ClientMinPrincipal
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 1645837366147709569L;

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
     * AtomicSerial constructor.
     * @param arg
     * @throws IOException
     */
    public ClientMinPrincipal(GetArg arg) throws IOException{
	this(verify(arg.get("principals", null, Principal[].class)));
    }
    
    private static Principal[] verify(Principal[] principals) throws InvalidObjectException{
	principals = principals.clone();
	Constraint.verify(principals);
	return principals;
    }

    /**
     * Creates a constraint containing the specified principal.  This
     * constructor is equivalent to calling a constructor with a
     * single-element array containing the specified principal.
     *
     * @param p the principal
     * @throws NullPointerException if the argument is <code>null</code>
     */
    public ClientMinPrincipal(Principal p) {
	this(check(p), true);
    }
    
    private static Principal[] check(Principal p){
	if (p == null) {
	    throw new NullPointerException("principal cannot be null");
	}
	return new Principal[]{p};
    }
    
    private ClientMinPrincipal(Principal[] principals, boolean check){
	this.principals = principals;
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
    public ClientMinPrincipal(Principal[] principals) {
	this(Constraint.reduce(principals), true);
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
    public ClientMinPrincipal(Collection c) {
	this(Constraint.reduce(c));
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
	return (ClientMinPrincipal.class.hashCode() +
		Constraint.hash(principals));
    }

    /**
     * Two instances of this class are equal if they have the same principals
     * (ignoring order).
     */
    public boolean equals(Object obj) {
	return (obj instanceof ClientMinPrincipal &&
		Constraint.equal(principals,
				 ((ClientMinPrincipal) obj).principals));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return "ClientMinPrincipal" + Constraint.toString(principals);
    }

    /**
     * Verifies that there is at least one principal, that none of the
     * principals is <code>null</code>, and that there are no duplicates.
     *
     * @throws java.io.InvalidObjectException if there are no principals,
     * or any principal is <code>null</code>, or if there are duplicate
     * principals
     * @param s ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if de-serialization problem occurs.
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	Constraint.verify(principals);
    }
}
