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
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Represents a constraint on the server, such that if the server
 * authenticates itself, then it may only authenticate itself as one or more
 * of the specified principals. The mechanisms and credentials used to
 * authenticate the server as those principals are not specified by this
 * constraint. A server can use this constraint to limit how much of its
 * identity is exposed in remote calls. For example, if the server's subject
 * contains sufficient information to authenticate as two distinct
 * principals, the server might wish to limit authentication to just one of
 * the two, if the server believes the other is unnecessary for authorization
 * at the server and wants to avoid revealing that part of its identity to
 * the server.
 * <p>
 * The use of an instance of this class does not directly imply a
 * {@link ServerAuthentication#YES} constraint; that must be specified
 * separately to ensure that the server actually authenticates itself.
 * Because this constraint is conditional on server authentication, it does
 * not conflict with {@link ServerAuthentication#NO}.
 *
 * @author Sun Microsystems, Inc.
 * @see ServerAuthentication
 * @see ServerMinPrincipal
 * @see net.jini.security.AuthenticationPermission
 * @since 2.0
 */
@AtomicSerial
public final class ServerMaxPrincipal
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * @serialField principals Principal[] The principals.
     */
    private static final ObjectStreamField[] serialPersistentFields
            = serialForm();
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("principals", Principal[].class, true)
        };
    }
    
    public static void serialize(PutArg arg, ServerMaxPrincipal p) throws IOException{
        arg.put("principals", p.principals.clone());
        arg.writeArgs();
    }

    /**
     * The principals.
     */
    private final Principal[] principals;
    
    /**
     * AtomicSerial constructor.
     * 
     * @param arg atomic deserialization parameter 
     * @throws IOException if there are I/O errors while reading from GetArg's
     *         underlying <code>InputStream</code>
     * @throws java.lang.ClassNotFoundException
     * @throws InvalidObjectException if object invariants aren't satisfied.
     */
    public ServerMaxPrincipal(GetArg arg) throws IOException, ClassNotFoundException {
	this(check(arg.get("principals", null, Principal[].class)), true);
    }
    
    private ServerMaxPrincipal(Principal[] principals, boolean check){
	this.principals = principals;
    }

    /**
     * Creates a constraint containing the specified principal.  This
     * constructor is equivalent to calling a constructor with a
     * single-element array containing the specified principal.
     *
     * @param p the principal
     * @throws NullPointerException if the argument is <code>null</code>
     */
    public ServerMaxPrincipal(Principal p) {
	this(check(p), true);
    }
    
    private static Principal[] check(Principal p){
	if (p == null) {
	    throw new NullPointerException("principal cannot be null");
	}
	return new Principal[]{p};
    }
    
    private static Principal [] check(Principal[] p) throws InvalidObjectException{
	p = p.clone();
	Constraint.verify(p);
	return p;
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
    public ServerMaxPrincipal(Principal[] principals) {
	this(Constraint.reduce(principals), true);
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
    public ServerMaxPrincipal(Collection c) {
	this(Constraint.reduce(c), true);
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
    @Override
    public int hashCode() {
	return (ServerMaxPrincipal.class.hashCode() +
		Constraint.hash(principals));
    }

    /**
     * Two instances of this class are equal if they have the same principals
     * (ignoring order).
     */
    @Override
    public boolean equals(Object obj) {
	return (obj instanceof ServerMaxPrincipal &&
		Constraint.equal(principals,
				 ((ServerMaxPrincipal) obj).principals));
    }

    /**
     * Returns a string representation of this object.
     */
    @Override
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
