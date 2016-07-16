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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Basic implementation of {@link MethodConstraints}, allowing limited
 * wildcard matching on method names and parameter types. Methods can be
 * specified by exact name and parameter types (matching a single method),
 * by exact name (matching all methods with that name), by name prefix
 * (matching all methods with names that start with that prefix), by name
 * suffix (matching all methods with names that end with that suffix), and
 * by a default that matches all methods. Normally instances of this class
 * should be obtained from a
 * {@link net.jini.config.Configuration} rather than being
 * explicitly constructed.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
@AtomicSerial
public final class BasicMethodConstraints
			implements MethodConstraints, Serializable
{
    private static final long serialVersionUID = 1432234194703790047L;

    /**
     * @serialField descs MethodDesc[] The ordered method descriptors.
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("descs", MethodDesc[].class, true)
    };

    /**
     * The ordered method descriptors.
     */
    private final MethodDesc[] descs;

    /**
     * Descriptor for specifying the constraints associated with one or
     * more methods allowing limited wildcard matching on method names and
     * parameter types. Methods can be specified by exact name and parameter
     * types (matching a single method), by exact name (matching all methods
     * with that name), by name prefix (matching all methods with names that
     * start with that prefix), by name suffix (matching all methods with
     * names that end with that suffix), and by a default that matches all
     * methods.
     *
     * @since 2.0
     */
    @AtomicSerial
    public static final class MethodDesc implements Serializable {
	private static final long serialVersionUID = 6773269226844208999L;

	/**
	 * @serialField name String
	 * The name of the method, with prefix or suffix '*' permitted
	 * if <code>types</code> is <code>null</code>, or <code>null</code>
	 * for a descriptor that matches all methods (in which case
	 * <code>types</code> must also be <code>null</code>).
	 * @serialField types Class[]
	 * The parameter types for the specified method, or <code>null</code>
	 * for wildcard parameter types.
	 * @serialField constraints InvocationConstraints
	 * The non-empty constraints for the specified method or methods, or
	 * <code>null</code> if there are no constraints.
	 */
	private static final ObjectStreamField[] serialPersistentFields = {
	    new ObjectStreamField("name", String.class),
	    new ObjectStreamField("types", Class[].class, true),
	    new ObjectStreamField("constraints", InvocationConstraints.class)
	};

	/**
	 * The name of the method, with prefix or suffix '*' permitted
	 * if <code>types</code> is <code>null</code>, or <code>null</code>
	 * for a descriptor that matches all methods (in which case
	 * <code>types</code> must also be <code>null</code>.
	 */
	final String name;
	/**
	 * The formal parameter types of the method, in declared order,
	 * or <code>null</code> for wildcard parameter types.
	 */
	final Class[] types;
	/**
	 * The non-empty constraints for the specified method or methods, or
	 * <code>null</code> if there are no constraints.
	 */
	final InvocationConstraints constraints;

	private MethodDesc(boolean check, 
			   String name,
			   Class[] types,
			   InvocationConstraints constraints)
	{
	    this.name = name;
	    this.types = types == null ? null : (Class[]) types.clone();
	    if (constraints != null && constraints.isEmpty()) {
		constraints = null;
	    }
	    this.constraints = constraints;
	}
	
	public MethodDesc(GetArg arg) throws IOException{
	    this(checkSerial(
		    arg.get("name", null, String.class),
		    arg.get("types", null, Class[].class),
		    arg.get("constraints", null, InvocationConstraints.class)
		),
		(String) arg.get("name", null),
		(Class[]) arg.get("types", null),
		(InvocationConstraints) arg.get("constraints", null)
	    );
	}

	/**
	 * Creates a descriptor that only matches methods with exactly the
	 * specified name and parameter types. The constraints can be
	 * <code>null</code>, which is treated the same as an empty
	 * instance. The array passed to the constructor is neither modified
	 * nor retained; subsequent changes to that array have no effect on
	 * the instance created.
	 *
	 * @param name the name of the method
	 * @param types the formal parameter types of the method, in declared
	 * order
	 * @param constraints the constraints, or <code>null</code>
	 * @throws NullPointerException if <code>name</code> or
	 * <code>types</code> is <code>null</code> or any element of
	 * <code>types</code> is <code>null</code>
	 * @throws IllegalArgumentException if <code>name</code> is not a
	 * syntactically valid method name
	 */
	public MethodDesc(String name,
			  Class[] types,
			  InvocationConstraints constraints)
	{
	    this(check(name, types),
		name,
		types,
		constraints
	    );
	    }

	/**
	 * Creates a descriptor that matches all methods with names that
	 * equal the specified name or that match the specified pattern,
	 * regardless of their parameter types. If the specified name starts
	 * with the character '*', then this descriptor matches all methods
	 * with names that end with the rest of the specified name. If the
	 * specified name ends with the character '*', then this descriptor
	 * matches all methods with names that start with the rest of the
	 * specified name. Otherwise, this descriptor matches all methods
	 * with names that equal the specified name. The constraints can be
	 * <code>null</code>, which is treated the same as an empty instance.
	 *
	 * @param name the name of the method, with a prefix or suffix '*'
	 * permitted for pattern matching
	 * @param constraints the constraints, or <code>null</code>
	 * @throws NullPointerException if <code>name</code> is
	 * <code>null</code>
	 * @throws IllegalArgumentException if <code>name</code> does not
	 * match any syntactically valid method name
	 */
	public MethodDesc(String name, InvocationConstraints constraints) {
	    this(check(name, null),
		name,
		null,
		constraints
	    );
	}
	
	/**
	 * Invariant checks for de-serialization.
	 * @param name
	 * @param types
	 * @param constraints
	 * @return
	 * @throws InvalidObjectException 
	 */
	private static boolean checkSerial(
		String name, 
		Class[] types,
		InvocationConstraints constraints) throws InvalidObjectException
	{
	    if (name == null) {
		if (types != null) {
		    throw new InvalidObjectException(
					 "cannot have types with null name");
		}
	    } else {
		try {
		    check(name, types);
		} catch (RuntimeException e) {
		    rethrow(e);
		}
	    }
	    if (constraints != null && constraints.isEmpty()) {
		throw new InvalidObjectException(
					     "constraints cannot be empty");
	    }
	    return true;
	}

	/**
	 * Verifies that the name is a syntactically valid method name, or
	 * (if types is null) if the name is a syntactically valid method name
	 * with a '*' appended or could be constructed from some syntactically
	 * valid method name containing more than two characters by replacing
	 * the first character of that name with '*', and verifies that none
	 * of the elements of types are null.
	 */
	private static boolean check(String name, Class[] types) {
	    boolean star = types == null;
	    int len = name.length();
	    if (len == 0) {
		throw new IllegalArgumentException(
					       "method name cannot be empty");
	    }
	    char c = name.charAt(0);
	    if (!Character.isJavaIdentifierStart(c) &&
		!(star && c == '*' && len > 1))
	    {
		throw new IllegalArgumentException("invalid method name");
	    }
	    if (star && c != '*' && name.charAt(len - 1) == '*') {
		len--;
	    }
	    while (--len >= 1) {
		if (!Character.isJavaIdentifierPart(name.charAt(len))) {
		    throw new IllegalArgumentException("invalid method name");
		}
	    }
	    if (types != null) {
		for (int i = types.length; --i >= 0; ) {
		    if (types[i] == null) {
			throw new NullPointerException("class cannot be null");
		    }
		}
	    }
	    return true;
	}

	/**
	 * Creates a default descriptor that matches all methods. The
	 * constraints can be <code>null</code>, which is treated the same as
	 * an empty instance.
	 *
	 * @param constraints the constraints, or <code>null</code>
	 */
	public MethodDesc(InvocationConstraints constraints) {
	     this(false,
		null,
		null,
		constraints
	    );
	    }

	/**
	 * Returns the name of the method, with a prefix or suffix '*' if the
	 * name is a pattern, or <code>null</code> if this descriptor matches
	 * all methods.
	 *
	 * @return the name of the method, with a prefix or suffix '*' if the
	 * name is a pattern, or <code>null</code> if this descriptor matches
	 * all methods
	 */
	public String getName() {
	    return name;
	}

	/**
	 * Returns the parameter types, or <code>null</code> if this
	 * descriptor matches all parameter types or all methods. Returns a
	 * new non-<code>null</code> array every time it is called.
	 *
	 * @return the parameter types, or <code>null</code> if this
	 * descriptor matches all parameter types or all methods
	 */
	public Class[] getParameterTypes() {
	    return types == null ? null : (Class[]) types.clone();
	}

	/**
	 * Returns the constraints as a non-<code>null</code> value.
	 *
	 * @return the constraints as a non-<code>null</code> value
	 */
	public InvocationConstraints getConstraints() {
	    return (constraints == null ?
		    InvocationConstraints.EMPTY : constraints);
	}

	/**
	 * Returns a hash code value for this object.
	 */
	public int hashCode() {
	    int h = 0;
	    if (name != null) {
		h += name.hashCode();
	    }
	    if (types != null) {
		h += hash(types);
	    }
	    if (constraints != null) {
		h += constraints.hashCode();
	    }
	    return h;
	}

	/**
	 * Two instances of this class are equal if they have the same
	 * name, the same parameter types, and the same constraints.
	 */
	public boolean equals(Object obj) {
	    if (this == obj) {
		return true;
	    } else if (!(obj instanceof MethodDesc)) {
		return false;
	    }
	    MethodDesc od = (MethodDesc) obj;
	    return ((name == null ?
		     od.name == null : name.equals(od.name)) &&
		    Arrays.equals(types, od.types) &&
		    (constraints == null ?
		     od.constraints == null :
		     constraints.equals(od.constraints)));
	}

	/**
	 * Returns a string representation of this object.
	 */
	public String toString() {
	    StringBuffer buf = new StringBuffer("MethodDesc[");
	    toString(buf, true);
	    buf.append(']');
	    return buf.toString();
	}

	/**
	 * Appends a string representation of this object to the buffer.
	 */
	void toString(StringBuffer buf, boolean includeConstraints) {
	    buf.append(name == null ? "default" : name);
	    if (types != null) {
		buf.append('(');
		for (int i = 0; i < types.length; i++) {
		    if (i > 0) {
			buf.append(", ");
		    }
		    buf.append(types[i].getName());
		}
		buf.append(')');
	    }
	    if (includeConstraints) {
		buf.append(" => ").append(constraints);
	    }
	}

	/**
	 * Verifies that the method name, parameter types, and constraints are
	 * valid.
	 *
	 * @throws InvalidObjectException if <code>types</code> is
	 * non-<code>null</code> and <code>name</code> is either
	 * <code>null</code> or is not a syntactically valid method name;
	 * or if <code>types</code> is <code>null</code> and <code>name</code>
	 * is neither a syntactically valid method name, a syntactically
	 * valid method name with a '*' appended, nor a name constructed from
	 * some syntactically valid method name containing more than two
	 * characters by replacing the first character of that name with '*';
	 * or if any element of <code>types</code> is <code>null</code>; or
	 * if <code>constraints</code> is non-<code>null</code> but empty
	 */
	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    s.defaultReadObject();
	    checkSerial(name, types, constraints);
            }
        }

    /**
     * Creates an instance with the specified ordered array of descriptors.
     * The {@link #getConstraints getConstraints} method searches the
     * descriptors in the specified order. For any given descriptor in the
     * array, no preceding descriptor can match at least the same methods as
     * the given descriptor; that is, more specific descriptors must precede
     * less specific descriptors. The array passed to the constructor is
     * neither modified nor retained; subsequent changes to that array have
     * no effect on the instance created.
     *
     * @param descs the descriptors
     * @throws NullPointerException if the argument is <code>null</code> or
     * any element of the argument is <code>null</code>
     * @throws IllegalArgumentException if the descriptors array is empty, or
     * if any descriptor is preceded by another descriptor that matches at
     * least the same methods
     */
    public BasicMethodConstraints(MethodDesc[] descs) {
	this(check(descs), descs);
    }
    
    /**
     * Constructor for {@link AtomicSerial}.
     * 
     * @see AtomicSerial
     * @param arg
     * @throws IOException 
     */
    public BasicMethodConstraints(GetArg arg) throws IOException{
	this(checkSerial(arg.get("descs", null, MethodDesc[].class)),
	    (MethodDesc[]) arg.get("descs", null));
    }
    
    /**
     * Private constructor to call after invariant checks.
     * @param check
     * @param descs 
     */
    private BasicMethodConstraints(boolean check, MethodDesc[] descs){
	this.descs = (MethodDesc[]) descs.clone();
    }

    private static boolean checkSerial(MethodDesc[] descs) throws InvalidObjectException{
	try {
	    return check(descs);
	} catch (RuntimeException e) {
	    rethrow(e);
	}
	return true;
    }

    /**
     * Throws IllegalArgumentException if the descriptors array is empty, or
     * if any descriptor is preceded by another descriptor that matches at
     * least the same methods. Throws NullPointerException if the array or
     * any element is null.
     */
    private static boolean check(MethodDesc[] descs) {
	if (descs.length == 0) {
	    throw new IllegalArgumentException(
					 "must have at least one descriptor");
	}
	for (int i = 0; i < descs.length; i++) {
	    MethodDesc desc = descs[i];
	    String dname = desc.name;
	    if (dname == null) {
		if (i < descs.length - 1) {
		    throw new IllegalArgumentException(
					   "default descriptor must be last");
		}
	    } else if (dname.charAt(0) == '*') {
		int dlen = dname.length() + 1;
		for (int j = 0; j < i; j++) {
		    MethodDesc prev = descs[j];
		    String pname = prev.name;
		    if (pname.charAt(0) == '*' &&
			pname.regionMatches(1, dname, dlen - pname.length(),
					    pname.length() - 1))
		    {
			check(prev, desc);
		    }
		}
	    } else if (dname.charAt(dname.length() - 1) == '*') {
		for (int j = 0; j < i; j++) {
		    MethodDesc prev = descs[j];
		    String pname = prev.name;
		    int plen = pname.length() - 1;
		    if (pname.charAt(plen) == '*' &&
			pname.regionMatches(0, dname, 0, plen))
		    {
			check(prev, desc);
		    }
		}
	    } else {
		for (int j = 0; j < i; j++) {
		    MethodDesc prev = descs[j];
		    String pname = prev.name;
		    int plen = pname.length() - 1;
		    if (pname.charAt(0) == '*') {
			if (dname.regionMatches(dname.length() - plen,
						pname, 1, plen))
			{
			    check(prev, desc);
			}
		    } else if (pname.charAt(plen) == '*') {
			if (dname.regionMatches(0, pname, 0, plen)) {
			    check(prev, desc);
			}
		    } else {
			if (pname.equals(dname)) {
			    check(prev, desc);
			}
		    }
		}
	    }
	}
	return true;
    }

    /**
     * Throws IllegalArgumentException if the parameter types of prev cover
     * those of desc.
     */
    private static void check(MethodDesc prev, MethodDesc desc) {
	if (prev.types == null || Arrays.equals(prev.types, desc.types)) {
	    StringBuffer buf = new StringBuffer();
	    prev.toString(buf, false);
	    buf.append(" cannot precede ");
	    desc.toString(buf, false);
	    throw new IllegalArgumentException(buf.toString());
	}
    }

    /**
     * Creates an instance that maps all methods to the specified constraints.
     * The constraints can be <code>null</code>, which is treated the same as
     * an empty instance. Calling this constructor is equivalent to
     * constructing an instance of this class with an array containing a
     * single default descriptor constructed with the specified constraints.
     *
     * @param constraints the constraints, or <code>null</code>
     */
    public BasicMethodConstraints(InvocationConstraints constraints) {
	descs = new MethodDesc[]{new MethodDesc(constraints)};
    }

    /**
     * Returns the constraints for the specified remote method as a
     * non-<code>null</code> value. Searches the descriptors in order, and
     * returns the constraints in the first descriptor that matches the
     * method, or an empty constraints instance if there is no match.
     *
     * @throws NullPointerException {@inheritDoc}
     */
    public InvocationConstraints getConstraints(Method method) {
	String name = method.getName();
	Class[] types = null;
	InvocationConstraints sc = null;
    outer:
	for (int i = 0; i < descs.length; i++) {
	    MethodDesc desc = descs[i];
	    String dname = desc.name;
	    if (dname == null) {
		sc = desc.constraints;
		break;
	    } else if (desc.types != null) {
		if (!name.equals(dname)) {
		    continue;
		}
		if (types == null) {
		    types = method.getParameterTypes();
		}
		if (types.length != desc.types.length) {
		    continue;
		}
		for (int j = types.length; --j >= 0; ) {
		    if (types[j] != desc.types[j]) {
			continue outer;
		    }
		}
		sc = desc.constraints;
		break;
	    } else {
		int dlen = dname.length() - 1;
		if (dname.charAt(0) == '*') {
		    if (name.regionMatches(name.length() - dlen,
					   dname, 1, dlen))
		    {
			sc = desc.constraints;
			break;
		    }
		} else if (dname.charAt(dlen) == '*') {
		    if (name.regionMatches(0, dname, 0, dlen)) {
			sc = desc.constraints;
			break;
		    }
		} else if (name.equals(dname)) {
		    sc = desc.constraints;
		    break;
		}
	    }
	}
	if (sc == null) {
	    sc = InvocationConstraints.EMPTY;
	}
	return sc;
    }

    /* inherit javadoc */
    public Iterator possibleConstraints() {
	return new Iterator() {
	    private int i = descs.length;
	    private boolean empty = descs[i - 1].name != null;

	    public boolean hasNext() {
		return i > 0 || empty;
	    }

	    public Object next() {
		if (i == 0) {
		    if (empty) {
			empty = false;
			return InvocationConstraints.EMPTY;
		    }
		    throw new NoSuchElementException("no more elements");
		}
		InvocationConstraints sc = descs[--i].constraints;
		if (sc == null) {
		    sc = InvocationConstraints.EMPTY;
		}
		return sc;
	    }

	    public void remove() {
		throw new UnsupportedOperationException("immutable object");
	    }
	};
    }

    /**
     * Returns the descriptors. Returns a new non-<code>null</code> array
     * every time it is called.
     *
     * @return the descriptors as a new non-<code>null</code> array
     */
    public MethodDesc[] getMethodDescs() {
	return (MethodDesc[]) descs.clone();
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return hash(descs);
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	StringBuffer buf = new StringBuffer("BasicMethodConstraints{");
	for (int i = 0; i < descs.length; i++) {
	    if (i > 0) {
		buf.append(", ");
	    }
	    descs[i].toString(buf, true);
	}
	buf.append('}');
	return buf.toString();
    }

    /**
     * Two instances of this class are equal if they have the same descriptors
     * in the same order.
     */
    public boolean equals(Object obj) {
	return (this == obj ||
		(obj instanceof BasicMethodConstraints &&
		 Arrays.equals(descs, ((BasicMethodConstraints) obj).descs)));
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }

    /**
     * Verifies legal descriptor ordering.
     *
     * @throws InvalidObjectException if any descriptor is <code>null</code>,
     * or the descriptors array is empty, or if any descriptor is preceded by
     * another descriptor that matches at least the same methods
     * 
     * Default de-serialization is vulnerable to finalizer and reference
     * stealer attacks.
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	checkSerial(descs);
	}

    /**
     * Returns the sum of the hash codes of all elements of the given array.
     */
    private static int hash(Object[] elements) {
	int h = 0;
	for (int i = elements.length; --i >= 0; ) {
	    h += elements[i].hashCode();
	}
	return h;
    }

    /**
     * If the exception is a NullPointerException or IllegalArgumentException,
     * wrap it in an InvalidObjectException and throw that, otherwise rethrow
     * the exception as is.
     */
    private static void rethrow(RuntimeException e)
	throws InvalidObjectException
    {
	if (e instanceof NullPointerException ||
	    e instanceof IllegalArgumentException)
	{
	    InvalidObjectException ee =
		new InvalidObjectException(e.getMessage());
	    ee.initCause(e);
	    throw ee;
	}
	throw e;
    }
}
