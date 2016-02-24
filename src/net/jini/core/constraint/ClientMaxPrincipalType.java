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
import java.util.Collection;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Represents a constraint on the client, such that if the client
 * authenticates itself, then it may only authenticate itself as principals
 * that are instances of one or more of the specified classes. The mechanisms
 * and credentials used to authenticate the client as those principals are not
 * specified by this constraint. A client can use this constraint to limit how
 * much of its identity is exposed in remote calls. For example, if the
 * client's subject contains sufficient information to authenticate as two
 * distinct principals of different types, the client might wish to limit
 * authentication to just one of the two types, if the client believes the
 * other is unnecessary for authorization at the server and wants to avoid
 * revealing that part of its identity to the server.
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
 * @see ClientMinPrincipal
 * @see ClientMinPrincipalType
 * @see net.jini.security.AuthenticationPermission
 * @since 2.0
 */
@AtomicSerial
public final class ClientMaxPrincipalType
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = -2521616888337674811L;

    /**
     * @serialField classes Class[] The classes.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("classes", Class[].class, true)
    };

    /**
     * The classes.
     */
    private final Class[] classes;

    /**
     * Creates a constraint containing the specified class.  This
     * constructor is equivalent to calling a constructor with a
     * single-element array containing the specified class.
     *
     * @param clazz the class
     * @throws NullPointerException if the argument is <code>null</code>
     * @throws IllegalArgumentException if the argument is a primitive type,
     * an array type, or a <code>final</code> class that does not have
     * {@link java.security.Principal} as a superinterface
     */
    public ClientMaxPrincipalType(Class clazz) {
	this(check(clazz), true);
    }
    
    public ClientMaxPrincipalType(GetArg arg) throws IOException{
	this(verify(arg.get("classes", null, Class[].class)), true);
    }
    
    private static Class[] check(Class clazz){
	Constraint.verify(clazz);
	return new Class[]{clazz};
    }
    
    private static Class[] verify(Class[] classes) throws InvalidObjectException{
	classes = classes.clone();
	Constraint.verify(classes);
	return classes;
    }
    
    private ClientMaxPrincipalType(Class[] clazzs, boolean check){
	classes = clazzs;
    }

    /**
     * Creates a constraint containing the specified classes, with redundant
     * classes removed. Redundant classes are removed as follows: for any two
     * specified classes <code>c1</code> and <code>c2</code>, if
     * <code>c1.isAssignableFrom(c2)</code> is <code>true</code>, then
     * <code>c2</code> is removed. That is, duplicates and subtypes are
     * removed. The argument passed to the constructor is neither modified
     * nor retained; subsequent changes to that argument have no effect on
     * the instance created.
     *
     * @param classes the classes
     * @throws IllegalArgumentException if the argument is empty, or if any
     * element is a primitive type, an array type, or a <code>final</code>
     * class that does not have {@link java.security.Principal} as a
     * superinterface
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     */
    public ClientMaxPrincipalType(Class[] classes) {
	this(Constraint.reduce(classes, true), true);
    }

    /**
     * Creates a constraint containing the specified classes, with redundant
     * classes removed. Redundant classes are removed as follows: for any two
     * specified classes <code>c1</code> and <code>c2</code>, if
     * <code>c1.isAssignableFrom(c2)</code> is <code>true</code>, then
     * <code>c2</code> is removed. That is, duplicates and subtypes are
     * removed. The argument passed to the constructor is neither modified
     * nor retained; subsequent changes to that argument have no effect on
     * the instance created.
     *
     * @param c the classes
     * @throws IllegalArgumentException if the argument is empty, or if any
     * element is not a <code>Class</code>, or is a primitive type, an array
     * type, or a <code>final</code> class that does not have
     * {@link java.security.Principal} as a superinterface
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element is <code>null</code>
     */
    public ClientMaxPrincipalType(Collection c) {
	this(Constraint.reduce(c, true), true);
    }

    /**
     * Returns an immutable set of all of the classes. Any attempt to
     * modify the set results in an {@link UnsupportedOperationException}
     * being thrown.
     *
     * @return an immutable set of all of the classes
     */
    public Set elements() {
	return new ArraySet(classes);
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return (ClientMaxPrincipalType.class.hashCode() +
		Constraint.hash(classes));
    }

    /**
     * Two instances of this class are equal if they have the same classes
     * (ignoring order).
     */
    public boolean equals(Object obj) {
	return (obj instanceof ClientMaxPrincipalType &&
		Constraint.equal(classes,
				 ((ClientMaxPrincipalType) obj).classes));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return "ClientMaxPrincipalType" + Constraint.toString(classes);
    }

    /**
     * Verifies that there is at least one class, that none of the classes
     * is <code>null</code>, primitive types, array types, or
     * <code>final</code> classes that do not have <code>Principal</code> as
     * a superinterface, and that no class is assignable to any other class.
     *
     * @throws java.io.InvalidObjectException if there are no classes, or
     * any class is <code>null</code>, a primitive type, array type, or
     * <code>final</code> class that does not have <code>Principal</code> as
     * a superinterface, or if any class is assignable to any other class
     * @param s ObjectInputStream
     * @throws ClassNotFoundException if class not found.
     * @throws IOException if de-serialization problem occurs.
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	Constraint.verify(classes);
    }
}
