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
 * authenticates itself, then it must authenticate itself such that, for each
 * specified class, at least one authenticated principal is an instance of
 * that class. The mechanisms and credentials used to authenticate the client
 * as those principals are not specified by this constraint. This
 * constraint is intended for use by clients to control how much of their
 * identity is exposed in remote calls; it is not intended for use by servers
 * as an authorization mechanism. For example, if the client's subject contains
 * sufficient information to authenticate as two distinct principals of
 * different types, the client might wish to ensure that it authenticates as
 * one type, the other type, or both, depending on what the client believes
 * is necessary for authorization at the server.
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
 * @see ClientMinPrincipal
 * @see net.jini.security.AuthenticationPermission
 * @since 2.0
 */
@AtomicSerial
public final class ClientMinPrincipalType
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 2389386543834321065L;

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
     * AtomicSerial constructor.
     * @param arg
     * @throws IOException
     */
    public ClientMinPrincipalType(GetArg arg) throws IOException{
	this(verify(arg.get("classes", null, Class[].class)));
    }
    
    private static Class [] verify(Class [] classes) throws InvalidObjectException{
	classes = classes.clone();
	Constraint.verify(classes);
	return classes;
    }

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
    public ClientMinPrincipalType(Class clazz) {
	this(check(clazz), true);
    }
    
    private static Class [] check(Class clazz){
	Constraint.verify(clazz);
	return new Class[]{clazz};
    }
    
    private ClientMinPrincipalType(Class[] classes, boolean check){
	super();
	this.classes = classes;
    }

    /**
     * Creates a constraint containing the specified classes, with redundant
     * classes removed. Redundant classes are removed as follows: for any two
     * specified classes <code>c1</code> and <code>c2</code>, if
     * <code>c1.isAssignableFrom(c2)</code> is <code>true</code>, then
     * <code>c1</code> is removed. That is, duplicates and supertypes are
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
    public ClientMinPrincipalType(Class[] classes) {
	this(Constraint.reduce(classes, false), true);
    }

    /**
     * Creates a constraint containing the specified classes, with redundant
     * classes removed. Redundant classes are removed as follows: for any two
     * specified classes <code>c1</code> and <code>c2</code>, if
     * <code>c1.isAssignableFrom(c2)</code> is <code>true</code>, then
     * <code>c1</code> is removed. That is, duplicates and supertypes are
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
    public ClientMinPrincipalType(Collection c) {
	this(Constraint.reduce(c, false), true);
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
	return (ClientMinPrincipalType.class.hashCode() +
		Constraint.hash(classes));
    }

    /**
     * Two instances of this class are equal if they have the same classes
     * (ignoring order).
     */
    public boolean equals(Object obj) {
	return (obj instanceof ClientMinPrincipalType &&
		Constraint.equal(classes,
				 ((ClientMinPrincipalType) obj).classes));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return "ClientMinPrincipalType" + Constraint.toString(classes);
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
