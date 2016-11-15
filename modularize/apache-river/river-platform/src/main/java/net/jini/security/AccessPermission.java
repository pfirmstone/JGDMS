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

import java.security.Permission;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;

/**
 * Represents permission to call a method. An instance of this class
 * contains a name (also referred to as a "target name") but no actions list;
 * you either have the named permission or you don't. The target name can be
 * any of the following forms:
 * <pre>
 * *
 * <i>Identifier</i>
 * *<i>Suffix</i>
 * <i>Identifier</i>*
 * <i>QualifiedIdentifier</i>.*
 * <i>QualifiedIdentifier</i>.<i>Identifier</i>
 * <i>QualifiedIdentifier</i>.*<i>Suffix</i>
 * <i>QualifiedIdentifier</i>.<i>Identifier</i>*
 * </pre>
 * where <i>QualifiedIdentifier</i> and <i>Identifier</i> are as defined in
 * <i>The Java(TM) Language Specification</i> except that whitespace is not
 * permitted, and <i>Suffix</i> is defined to be one or more characters
 * that may be part of an <i>Identifier</i>. These forms are defined to
 * match fully qualified names of the form
 * <i>QualifiedIdentifier</i>.<i>Identifier</i> as follows:
 * <table border=1 cellpadding=5
 *        summary="Describes target name forms and matching semantics">
 * <tr>
 * <th>Target Name</th>
 * <th><i>QualifiedIdentifier</i> Match</th>
 * <th><i>Identifier</i> Match</th>
 * </tr>
 * <tr>
 * <td>*</td>
 * <td>any</td>
 * <td>any</td>
 * </tr>
 * <tr>
 * <td><b>method</b></td>
 * <td>any</td>
 * <td><b>method</b></td>
 * </tr>
 * <tr>
 * <td>*<b>suffix</b></td>
 * <td>any</td>
 * <td>any ending with <b>suffix</b></td>
 * </tr>
 * <tr>
 * <td><b>prefix</b>*</td>
 * <td>any</td>
 * <td>any starting with <b>prefix</b></td>
 * </tr>
 * <tr>
 * <td><b>type</b>.*</td>
 * <td><b>type</b></td>
 * <td>any</td>
 * </tr>
 * <tr>
 * <td><b>type</b>.<b>method</b></td>
 * <td><b>type</b></td>
 * <td><b>method</b></td>
 * </tr>
 * <tr>
 * <td><b>type</b>.*<b>suffix</b></td>
 * <td><b>type</b></td>
 * <td>any ending with <b>suffix</b></td>
 * </tr>
 * <tr>
 * <td><b>type</b>.<b>prefix</b>*</td>
 * <td><b>type</b></td>
 * <td>any starting with <b>prefix</b></td>
 * </tr>
 * </table>
 * <p>
 * This class, and simple subclasses of it, can be used (for example) with
 * {@link net.jini.jeri.BasicInvocationDispatcher}. It is
 * recommended that a simple subclass of this class be defined for each
 * remote object implementation class that can be exported using an
 * {@link net.jini.export.Exporter}, to allow separation of
 * grants in policy files.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class AccessPermission extends Permission {
    //@AtomicSerial is not implemented because PermissionSerializer will use
    // the single arg constructor.
    private static final long serialVersionUID = 7269818741475881138L;

    /**
     * The interface name, or null if wildcarded.
     */
    private /*final*/ transient String iface;
    /**
     * The name of the method, with prefix or suffix '*' permitted,
     * or null if wildcarded.
     */
    private /*final*/ transient String method;

    /**
     * Creates an instance with the specified target name.
     *
     * @param name the target name
     * @throws NullPointerException if the target name is <code>null</code>
     * @throws IllegalArgumentException if the target name does not match
     * the syntax specified in the comments at the beginning of this class
     */
    public AccessPermission(String name) {
	this(name,validate(name));
    }
    
    private AccessPermission(String name, String[] args){
	super(name);
	iface = args[0];
	method = args[1];
    }
    //TODO: Fix construction, susceptible to finalizer attack.
    /**
     * Parses the target name and initializes the transient fields.
     */
    private static String[] validate(String name) {
	String iface = null, method = null;
	if (name == null) {
	    throw new NullPointerException("name cannot be null");
	} else if (name.length() == 0) {
	    throw new IllegalArgumentException("name cannot be empty");
	}
	int i = name.lastIndexOf('.');
	if (i >= 0) {
	    iface = name.substring(0, i);
	    name = name.substring(i + 1);
	}
	if (!name.equals("*")) {
	    method = name;
	}
	if (iface != null && !validClass(iface)) {
	    throw new IllegalArgumentException("invalid interface name");
	} else if (method != null && !validMethod(method)) {
	    throw new IllegalArgumentException("invalid method name");
	}
	return new String[]{iface, method};
    }

    /**
     * Returns true if the name is a syntactically valid fully qualified
     * class name (no whitespace permitted), and returns false otherwise.
     */
    private static boolean validClass(String name) {
	int len = name.length();
    outer:
	for (int i = 0;
	     i < len && Character.isJavaIdentifierStart(name.charAt(i));
	     i++)
	{
	    while (++i < len) {
		char c = name.charAt(i);
		if (c == '.') {
		    continue outer;
		}
		if (!Character.isJavaIdentifierPart(c)) {
		    return false;
		}
	    }
	    return true;
	}
	return false;
    }

    /**
     * Returns true if the name is a syntactically valid method name, or
     * if the name is a syntactically valid method name with a '*' appended
     * or could be constructed from some syntactically valid method name
     * containing more than two characters by replacing the first character
     * of that name with '*', and returns false otherwise.
     */
    private static boolean validMethod(String name) {
	int len = name.length();
	if (len == 0) {
	    return false;
	}
	char c = name.charAt(0);
	if (!Character.isJavaIdentifierStart(c) && !(c == '*' && len > 1)) {
	    return false;
	}
	if (c != '*' && name.charAt(len - 1) == '*') {
	    len--;
	}
	while (--len >= 1) {
	    if (!Character.isJavaIdentifierPart(name.charAt(len))) {
		return false;
	    }
	}
	return true;
    }

    /**
     * Returns <code>true</code> if every fully qualified name that
     * matches the specified permission's name also matches this
     * permission's name; returns <code>false</code> otherwise.
     *
     * @param perm the permission to check
     * @return <code>true</code> if every fully qualified name that
     * matches the specified permission's name also matches this
     * permission's name; <code>false</code> otherwise
     */
    public boolean implies(Permission perm) {
	if (perm == null || perm.getClass() != getClass()) {
	    return false;
	}
	AccessPermission ap = (AccessPermission) perm;
	if (iface != null && !iface.equals(ap.iface)) {
	    return false;
	} else if (method == null) {
	    return true;
	} else if (ap.method == null) {
	    return false;
	}
	int len = method.length() - 1;
	if (method.charAt(0) == '*') {
	    return ap.method.regionMatches(ap.method.length() - len,
					   method, 1, len);
	} else if (method.charAt(len) == '*') {
	    return ap.method.regionMatches(0, method, 0, len);
	}
	return method.equals(ap.method);
    }

    /**
     * @return <code>true</code> if the specified object is an instance
     * of the same class as this permission and has the same target name
     * as this permission; returns <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (obj == null || obj.getClass() != getClass()) {
	    return false;
	}
	return getName().equals(((Permission) obj).getName());
    }

    /**
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
	return getName().hashCode();
    }

    /** 
     * @return an empty string.
     */
    @Override
    public String getActions() {
	return "";
    }

    /**
     * Verifies the syntax of the target name and recreates any transient
     * state.
     *
     * @throws InvalidObjectException if the target name is <code>null</code>,
     * or if the target name does not match the syntax specified in the
     * comments at the beginning of this class
     */
    private void readObject(ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	Exception cause;
	try {
	    String[] result = validate(getName());
	    iface = result[0];
	    method = result[1];
	    return;
	} catch (NullPointerException e) {
	    cause = e;
	} catch (IllegalArgumentException e) {
	    cause = e;
	}
	InvalidObjectException e =
	    new InvalidObjectException(cause.getMessage());
	e.initCause(cause);
	throw e;
    }
}
