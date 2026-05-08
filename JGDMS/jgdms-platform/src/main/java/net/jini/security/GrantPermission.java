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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.security.AllPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import net.jini.security.policy.DynamicPolicy;

/**
 * Permission required to dynamically grant permissions by security policy
 * providers which implement the {@link DynamicPolicy} interface.  Each
 * <code>GrantPermission</code> instance contains a set of permissions that can
 * be granted by code authorized with the <code>GrantPermission</code>.  When
 * the {@link DynamicPolicy#grant DynamicPolicy.grant} method is invoked, the
 * <code>checkPermission</code> method of the installed security manager (if
 * any) is called with a <code>GrantPermission</code> containing the
 * permissions to grant; if the calling context does not have any permissions
 * which imply the <code>GrantPermission</code>, then the grant operation will
 * fail.
 * <p>
 * In addition to authorizing granting of contained permissions, each
 * <code>GrantPermission</code> also authorizes granting of
 * <code>GrantPermission</code>s for contained permissions, as well as granting
 * of permissions contained within nested <code>GrantPermission</code>s.  For
 * example, if <code>GrantPermission g1</code> contains <code>Permission
 * p</code>, <code>g1</code> authorizes granting of both <code>p</code> and
 * <code>GrantPermission(p)</code>; if <code>GrantPermission g2</code> contains
 * <code>GrantPermission(p)</code>, then <code>g2</code> also authorizes
 * granting of both <code>p</code> and <code>GrantPermission(p)</code>.
 * <p>
 * The name (also referred to as the "target name") of each
 * <code>GrantPermission</code> instance carries a string representation of the
 * permissions contained by the <code>GrantPermission</code>, while the actions
 * string of each <code>GrantPermission</code> is always the empty string.  If
 * a <code>GrantPermission</code> is serialized, only its name string is sent
 * (i.e., contained permissions are not themselves serialized).  Upon
 * deserialization, the set of contained permissions is reconstituted based on
 * information in the name string.  <code>GrantPermission</code>s constructed
 * explicitly with {@link UnresolvedPermission}s (through either the {@link
 * #GrantPermission(Permission)} or {@link #GrantPermission(Permission[])}
 * constructor) will have incomplete target names that cannot be used to
 * instantiate other <code>GrantPermission</code>s, and will not be
 * serializable--attempting to serialize such a <code>GrantPermission</code>
 * will cause a <code>java.io.NotSerializableException</code> to be thrown.
 * <p>
 * The syntax of the target name approximates that used for specifying
 * permissions in the default security policy file; it is listed below using
 * the same grammar notation employed by <i>The Java(TM) Language
 * Specification</i>:
 * <div>
 * <i>Target</i>:<br>
 * &nbsp;&nbsp;<i>DelimiterDeclaration</i><sub>opt</sub> <i>Permissions</i> ;<sub>opt</sub><br>
 * <br>
 * <i>DelimiterDeclaration</i>:<br>
 * &nbsp;&nbsp;delim = <i>DelimiterCharacter</i><br>
 * <br>
 * <i>Permissions</i>:<br>
 * &nbsp;&nbsp;<i>Permission</i><br>
 * &nbsp;&nbsp;<i>Permissions</i> ; <i>Permission</i><br>
 * <br>
 * <i>Permission</i>:<br>
 * &nbsp;&nbsp;<i>PermissionClassName</i><br>
 * &nbsp;&nbsp;<i>PermissionClassName Name</i><br>
 * &nbsp;&nbsp;<i>PermissionClassName Name</i> , <i>Actions</i><br>
 * <br>
 * <i>PermissionClassName</i>:<br>
 * &nbsp;&nbsp;<i>ClassName</i><br>
 * <br>
 * <i>Name</i>:<br>
 * &nbsp;&nbsp;<i>DelimitedString</i><br>
 * <br>
 * <i>Actions</i>:<br>
 * &nbsp;&nbsp;<i>DelimitedString</i><br>
 * </div>
 * The production for <i>ClassName</i> is the same as that used in <i>The
 * Java Language Specification</i>.  <i>DelimiterCharacter</i> can be any
 * unquoted non-whitespace character other than ';' (single and
 * double-quote characters themselves are allowed).  If
 * <i>DelimiterCharacter</i> is not specified, then the double-quote
 * character is the default delimiter.  <i>DelimitedString</i> is the same
 * as the <i>StringLiteral</i> production in <i>The Java Language
 * Specification</i>, except that it is delimited by the
 * <i>DelimiterDeclaration</i>-specified (or default) delimiter character
 * instead of the double-quote character exclusively.
 * <p>
 * Note that if the double-quote character is used as the delimiter and the
 * name or actions strings of specified permissions themselves contain nested
 * double-quote characters, then those characters must be escaped (or in some
 * cases doubly-escaped) appropriately.  For example, the following policy file
 * entry would yield a <code>GrantPermission</code> containing a
 * <code>FooPermission</code> in which the target name would include the word
 * "quoted" surrounded by double-quote characters:
 * <pre>
 * permission net.jini.security.GrantPermission
 *     "FooPermission \"a \\\"quoted\\\" string\"";
 * </pre>
 * For comparison, the following policy file entry which uses a custom
 * delimiter would yield an equivalent <code>GrantPermission</code>:
 * <pre>
 * permission net.jini.security.GrantPermission
 *     "delim=| FooPermission |a \"quoted\" string|";
 * </pre>
 * Some additional example policy file permissions:
 * <pre>
 * // allow granting of permission to listen for and accept connections
 * permission net.jini.security.GrantPermission
 *     "java.net.SocketPermission \"localhost:1024-\", \"accept,listen\"";
 *
 * // allow granting of permissions to read files under /foo, /bar directories
 * permission net.jini.security.GrantPermission 
 *     "delim=' java.io.FilePermission '/foo/-', 'read'; java.io.FilePermission '/bar/-', 'read'";
 *
 * // allow granting of permission for client authentication as jack, with or without delegation, to any server
 * permission net.jini.security.GrantPermission
 *     "delim=| net.jini.security.AuthenticationPermission |javax.security.auth.x500.X500Principal \"CN=jack\"|, |delegate|";
 * </pre>
 *
 * @author	Sun Microsystems, Inc.
 * @see		DynamicPolicy#grant(Class, Principal[], Permission[])
 * @since 2.0
 */
public final class GrantPermission extends Permission {
    //@AtomicSerial is not necessary, PermissionSerializer is ok.
    private static final long serialVersionUID = 4668259055340724280L;
    
    private static final Class[] PARAMS0 = {};
    private static final Class[] PARAMS1 = { String.class };
    private static final Class[] PARAMS2 = { String.class, String.class };
    
    private transient Permission[] grants;
    private transient boolean unserializable;
    private transient volatile Implier implier;
    private transient volatile Integer hash;

    /**
     * Creates a <code>GrantPermission</code> for the permission(s) specified
     * in the name string.
     * 
     * @param	name string describing contained permissions
     * @throws	NullPointerException if <code>name</code> is <code>null</code>
     * @throws	IllegalArgumentException if unable to parse target name
     */
    public GrantPermission(String name) {
	this(name, initFromName(name), false);
    }
    
    /**
     * Creates a <code>GrantPermission</code> for the given permission.
     * 
     * @param	permission permission to allow to be granted
     * @throws	NullPointerException if <code>permission</code> is
     * 		<code>null</code>
     */
    public GrantPermission(Permission permission) {
	this(new Permission[]{ permission });
    }

    /**
     * Creates a <code>GrantPermission</code> for the given permissions.  The
     * permissions array passed in is neither modified nor retained; subsequent
     * changes to the array have no effect on the <code>GrantPermission</code>.
     * 
     * @param	permissions permissions to allow to be granted
     * @throws	NullPointerException if <code>permissions</code> array or any
     * 		element of <code>permissions</code> array is <code>null</code>
     */
    public GrantPermission(Permission[] permissions) {
	this(constructName(permissions), flatten(permissions), unserializable(permissions));
    }
    
    private GrantPermission(String name, Permission[] grants, boolean unserializable){
	super(name);
	this.unserializable = unserializable;
	this.grants = grants;
    }
    
    private static boolean unserializable(Permission[] permissions){
	for (int i = 0, l = permissions.length; i < l; i++) {
	    if (permissions[i] instanceof UnresolvedPermission) {
		return true;
	    }
	}
	return false;
    }
    
    /**
     * Returns canonical string representation of this permission's actions,
     * which for <code>GrantPermission</code> is always the empty string
     * <code>""</code>.
     * 
     * @return	the empty string <code>""</code>
     */
    public String getActions() {
	return "";
    }
    
    /**
     * Returns a newly created empty mutable permission collection for
     * <code>GrantPermission</code> instances.  The <code>implies</code> method
     * of the returned <code>PermissionCollection</code> instance is defined as
     * follows: for a given <code>GrantPermission g</code>, let
     * <code>c(g)</code> denote the set of all permissions contained within
     * <code>g</code> or within arbitrarily nested
     * <code>GrantPermission</code>s inside <code>g</code>, excluding nested
     * <code>GrantPermission</code>s themselves. Then, a <code>GrantPermission
     * g</code> is implied by the <code>PermissionCollection pc</code> if and
     * only if each permission in <code>c(g)</code> is implied by the union of
     * <code>c(p)</code> for all <code>p</code> in <code>pc</code>.  
     * <p>
     * Implication of contained
     * <code>java.security.UnresolvedPermission</code>s is special-cased: an
     * <code>UnresolvedPermission p1</code> is taken to imply another
     * <code>UnresolvedPermission p2</code> if and only if the serialized
     * representations of <code>p1</code> and <code>p2</code> are identical.
     * 
     * @return	newly created empty mutable permission collection for
     *		<code>GrantPermissions</code>
     */
    public PermissionCollection newPermissionCollection() {
	return new GrantPermissionCollection();
    }
    
    /**
     * Returns <code>true</code> if the given permission is a
     * <code>GrantPermission</code> implied by this permission, or
     * <code>false</code> otherwise.  Implication is defined as follows: for a
     * given <code>GrantPermission g</code>, let <code>c(g)</code> denote the
     * set of all permissions contained within <code>g</code> or within
     * arbitrarily nested <code>GrantPermission</code>s inside <code>g</code>,
     * excluding nested <code>GrantPermission</code>s themselves.  Then, a
     * <code>GrantPermission g1</code> is implied by another
     * <code>GrantPermission g2</code> if and only if each permission in
     * <code>c(g1)</code> is implied by <code>c(g2)</code>.
     * <p>
     * Implication of contained
     * <code>java.security.UnresolvedPermission</code>s is special-cased: an
     * <code>UnresolvedPermission p1</code> is taken to imply another
     * <code>UnresolvedPermission p2</code> if and only if the serialized
     * representations of <code>p1</code> and <code>p2</code> are identical.
     * 
     * @param	permission permission to check
     * @return	<code>true</code> if given permission is implied by this
     *		permission, <code>false</code> otherwise
     */
    public boolean implies(Permission permission) {
	if (!(permission instanceof GrantPermission)) {
	    return false;
	}
	// perm -> perm implies infrequent, so construct implier lazily
	if (implier == null) {
	    Implier imp = new Implier();
	    imp.add(this);
	    implier = imp;
	}
	return implier.implies(permission);
    }
    
    /**
     * Returns <code>true</code> if the given object is a
     * <code>GrantPermission</code> which both implies and is implied by this
     * permission; returns <code>false</code> otherwise.
     * 
     * @param	obj object to compare against
     * @return	<code>true</code> if given object is a
     * 		<code>GrantPermission</code> which both implies and is implied
     * 		by this permission, <code>false</code> otherwise
     */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	}
	if (obj instanceof GrantPermission) {
	    GrantPermission gp = (GrantPermission) obj;
	    return this.implies(gp) && gp.implies(this);
	}
	return false;
    }
    
    public int hashCode() {
	if (hash == null) {
	    hash = Integer.valueOf(computeHashCode());
	}
	return hash.intValue();
    }

    /**
     * Returns hash code computed by summing hash codes of each distinct
     * permission class name.
     */
    private int computeHashCode() {
	int sum = 0;
	HashSet set = new HashSet(grants.length);
	for (int i = 0; i < grants.length; i++) {
	    Permission p = grants[i];
	    String pcn = p.getClass().getName();
	    if (p instanceof AllPermission) {
		return pcn.hashCode();
	    } else if (p instanceof UnresolvedPermission) {
		pcn += ":" + p.getName();    // add name of unresolved class
	    }
	    if (!set.contains(pcn)) {
		set.add(pcn);
		sum += pcn.hashCode();
	    }
	}
	return sum;
    }

    /**
     * Writes target name representing contained permissions.
     *
     * @throws	NotSerializableException if the <code>GrantPermission</code>
     * 		was constructed explicitly with
     * 		<code>java.security.UnresolvedPermission</code>s
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	if (unserializable) {
	    throw new NotSerializableException(
		GrantPermission.class.getName());
	}
	out.defaultWriteObject();
    }

    /**
     * Reconstitutes contained permissions based on the information in the
     * target name.
     * 
     * @throws	InvalidObjectException if the target name is <code>null</code>
     * 		or does not conform to the syntax specified in the
     * 		documentation for {@link GrantPermission}
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	try {
	    grants = initFromName(getName());
	} catch (RuntimeException e) {
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
    
    /**
     * Initializes GrantPermission to contain permissions described in the
     * given name.  Throws an IllegalArgumentException if the name is
     * misformatted, or specifies an invalid permission class.  Throws a
     * SecurityException if access to the class is not permitted.
     */
    private static Permission[] initFromName(String name) {
	PermissionInfo[] pia = parsePermissions(name);
	ArrayList l = new ArrayList();
	for (int i = 0; i < pia.length; i++) {
	    PermissionInfo pi = pia[i];

	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		int d = pi.type.lastIndexOf('.');
		if (d != -1) {
		    sm.checkPackageAccess(pi.type.substring(0, d));
		}
	    }
	    Class cl;
	    try {
		cl = Class.forName(pi.type);
	    } catch (ClassNotFoundException ex) {
		l.add(new UnresolvedPermission(
		    pi.type, pi.name, pi.actions, null));
		continue;
	    }
	    if (!Permission.class.isAssignableFrom(cl)) {
		throw new IllegalArgumentException(
		    "not a permission class: " + cl);
	    }
	    if (!Modifier.isPublic(cl.getModifiers())) {
		throw new IllegalArgumentException(
		    "non-public permission class: " + cl);
	    }
	    
	    if (pi.name == null) {
		try {
		    Constructor c = cl.getConstructor(PARAMS0);
		    l.add(c.newInstance(new Object[0]));
		    continue;
		} catch (Exception ex) {
                    if (ex instanceof RuntimeException) throw (RuntimeException) ex;
		}
	    } 
	    if (pi.actions == null) {
		try {
		    Constructor c = cl.getConstructor(PARAMS1);
		    l.add(c.newInstance(new Object[]{ pi.name }));
		    continue;
		} catch (Exception ex) {
                    if (ex instanceof RuntimeException) throw (RuntimeException) ex;
		}
	    } 
	    try {
		Constructor c = cl.getConstructor(PARAMS2);
		l.add(c.newInstance(new Object[]{ pi.name, pi.actions }));
		continue;
	    } catch (Exception ex) {
                if (ex instanceof RuntimeException) throw (RuntimeException) ex;
	    }
	    throw new IllegalArgumentException(
		"uninstantiable permission class: " + cl);
	}
	return flatten((Permission[]) l.toArray(new Permission[l.size()]));
    }

    /**
     * Parses permission information from given GrantPermission name string.
     * Throws an IllegalArgumentException if the name string is misformatted.
     */
    private static PermissionInfo[] parsePermissions(String s) {
	try {
	    ArrayList l = new ArrayList();
	    StreamTokenizer st = createTokenizer(s);
	    char delim = '"';

	    if (st.nextToken() == StreamTokenizer.TT_WORD &&
		st.sval.equals("delim"))
	    {
		if (st.nextToken() == '=') {
		    if (st.nextToken() == StreamTokenizer.TT_WORD) {
			if (st.sval.length() > 1) {
			    throw new IllegalArgumentException(
				"excess delimiter characters");
			}
			delim = st.sval.charAt(0);
		    } else {
			delim = (char) st.ttype;
		    }
		    if (delim == ';') {
			throw new IllegalArgumentException(
			    "illegal delimiter ';'");
		    }
		} else {	// rewind
		    st = createTokenizer(s);
		}
		st.nextToken();
	    }
	    st.quoteChar(delim);

	    do {
		String type, name = null, actions = null;

		if (st.ttype != StreamTokenizer.TT_WORD) {
		    throw new IllegalArgumentException(
			"expected permission type");
		}
		type = st.sval;
		
		// REMIND: allow unquoted name/actions?
		st.nextToken();
		if (st.ttype == StreamTokenizer.TT_EOF || st.ttype == ';') {
		    l.add(new PermissionInfo(type, null, null));
		    continue;
		} else if (st.ttype == delim) {
		    name = st.sval;
		} else {
		    throw new IllegalArgumentException(
			"expected permission name or ';'");
		}
		
		st.nextToken();
		if (st.ttype == StreamTokenizer.TT_EOF || st.ttype == ';') {
		    l.add(new PermissionInfo(type, name, null));
		    continue;
		} else if (st.ttype != ',') {
		    throw new IllegalArgumentException("expected ',' or ';'");
		}

		if (st.nextToken() != delim) {
		    throw new IllegalArgumentException(
			"expected permission actions");
		}
		actions = st.sval;
		
		st.nextToken();
		if (st.ttype == StreamTokenizer.TT_EOF || st.ttype == ';') {
		    l.add(new PermissionInfo(type, name, actions));
		    continue;
		} else {
		    throw new IllegalArgumentException("expected ';'");
		}

	    } while (st.nextToken() != StreamTokenizer.TT_EOF);

	    return (PermissionInfo[]) l.toArray(new PermissionInfo[l.size()]);
	} catch (IOException ex) {
	    throw (Error) new InternalError().initCause(ex);
	}
    }

    /**
     * Returns tokenizer for parsing given string.  The tokenizer is configured
     * similarly to that used by sun.security.provider.PolicyParser, except
     * that comments are disabled and no quote character is set (yet).
     */
    private static StreamTokenizer createTokenizer(String s) {
	StreamTokenizer st = new StreamTokenizer(new StringReader(s));
	st.resetSyntax();
	st.wordChars('a', 'z');
	st.wordChars('A', 'Z');
	st.wordChars('.', '.');
	st.wordChars('0', '9');
	st.wordChars('_', '_');
	st.wordChars('$', '$');
	st.wordChars(128 + 32, 255);
	st.whitespaceChars(0, ' ');
	st.lowerCaseMode(false);
	st.ordinaryChar('/');
	st.slashSlashComments(false);
	st.slashStarComments(false);
	return st;
    }

    /**
     * Constructs GrantPermission name/target string appropriate for given list
     * of permissions.
     */
    private static String constructName(Permission[] pa) {
	StringBuilder sb = new StringBuilder(60);
	for (int i = 0, l = pa.length; i < l; i++) {
	    Permission p = pa[i];
	    if (p instanceof UnresolvedPermission) {
		sb.append(p)
		  .append("; ");
	    } else {
		Class cl = p.getClass();
		int nargs = maxConsArgs(cl);
		String t = cl.getName(), n = p.getName(), a = p.getActions();
		if (nargs == 2 && a != null) {
		    // REMIND: handle null name?
		    sb.append(t)
		      .append(" ")
		      .append(quote(n))
		      .append(", ")
		      .append(quote(a))
		      .append("; ");
		} else if (nargs >= 1 && n != null) {
		    sb.append(t)
		      .append(" ")
		      .append(quote(n))
		      .append("; ");
		} else {
		    sb.append(t)
		      .append("; ");
		}
	    }
	}
	return sb.toString().trim();
    }

    /**
     * Returns the maximum number of String parameters (up to 2) accepted by a
     * constructor of the given class.  Returns -1 if no matching constructor
     * (including no-arg constructor) is defined by given class.
     */
    private static int maxConsArgs(Class cl) {
	try {
	    cl.getConstructor(PARAMS2);
	    return 2;
	} catch (Exception ex) {
	}
	try {
	    cl.getConstructor(PARAMS1);
	    return 1;
	} catch (Exception ex) {
	}
	try {
	    cl.getConstructor(PARAMS0);
	    return 0;
	} catch (Exception ex) {
	}
	return -1;
    }

    /**
     * Returns quoted string literal that, if parsed by
     * java.io.StreamTokenizer, would yield the given string.  This method is
     * essentially a copy of org.apache.river.config.ConfigUtil.stringLiteral; the
     * two methods are kept separate since ConfigUtil.stringLiteral could
     * conceivably escape unicode characters, while such escaping would be
     * incorrect for GrantPermission.
     */
    private static String quote(String s) {
	StringBuffer sb = new StringBuffer(s.length() + 2);
	sb.append('"');
	char[] ca = s.toCharArray();
	for (int i = 0; i < ca.length; i++) {
	    char c = ca[i];
	    if (c == '\\' || c == '"') {
		sb.append("\\").append(c);
	    } else if (c == '\n') {
		sb.append("\\n");
	    } else if (c == '\r') {
		sb.append("\\r");
	    } else if (c == '\t') {
		sb.append("\\t");
	    } else if (c == '\f') {
		sb.append("\\f");
	    } else if (c == '\b') {
		sb.append("\\b");
	    } else if (c < 0x20) {
		sb.append("\\").append(Integer.toOctalString(c));
	    } else {
		sb.append(c);
	    }
	}
	return sb.append('"').toString();
    }

    /**
     * Returns an array containing all non-GrantPermission permissions in the
     * given permission array, including those contained in nested
     * GrantPermissions in the array.
     */
    private static Permission[] flatten(Permission[] pa) {
	List l = new ArrayList(pa.length);
	for (int i = 0; i < pa.length; i++) {
	    Permission p = pa[i];
	    if (p instanceof GrantPermission) {
		l.addAll(Arrays.asList(((GrantPermission) p).grants));
	    } else {
		l.add(p);
	    }
	}
	return (Permission[]) l.toArray(new Permission[l.size()]);
    }

    /**
     * Parsed information about a permission.
     */
    private static class PermissionInfo {
	
	final String type;
	final String name;
	final String actions;
	
	PermissionInfo(String type, String name, String actions) {
	    this.type = type;
	    this.name = name;
	    this.actions = actions;
	}
    }
    
    /**
     * Class for checking implication of contained permissions.
     */
    private static class Implier {
	
	private final PermissionCollection perms = new Permissions();
	private final ArrayList unresolved = new ArrayList();

	void add(GrantPermission gp) {
	    for (int i = 0; i < gp.grants.length; i++) {
		Permission p = gp.grants[i];
		if (!impliesContained(p)) {
		    perms.add(p);
		    if (p instanceof UnresolvedPermission) {
			unresolved.add(p);
		    }
		}
	    }
	}
	
	boolean implies(Permission p) {
	    if (!(p instanceof GrantPermission)) {
		return false;
	    }
	    Permission[] pa = ((GrantPermission) p).grants;
	    for (int i = 0; i < pa.length; i++) {
		if (!impliesContained(pa[i])) {
		    return false;
		}
	    }
	    return true;
	}

	private boolean impliesContained(Permission p) {
	    if (p instanceof UnresolvedPermission) {
		for (Iterator i = unresolved.iterator(); i.hasNext();) {
		    if (implies((UnresolvedPermission) i.next(),
				(UnresolvedPermission) p))
		    {
			return true;
		    }
		}
		return false;
	    } else {
		return perms.implies(p);
	    }
	}
	
	private static boolean implies(UnresolvedPermission p1,
				       UnresolvedPermission p2)
	{
	    if (p1 == p2) {
		return true;
	    }
	    // REMIND: use UnresolvedPermission.equals() once 4513737 fixed
	    try {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		ObjectOutputStream oout = new ObjectOutputStream(bout);

		bout.reset();
		oout.writeObject(p1);
		oout.flush();
		byte[] b1 = bout.toByteArray();
		
		oout.reset();
		bout.reset();
		oout.writeObject(p2);
		oout.flush();
		byte[] b2 = bout.toByteArray();
		
		return Arrays.equals(b1, b2);
	    } catch (IOException ex) {
		throw (Error) new InternalError().initCause(ex);
	    }
	}
    }

    /**
     * PermissionCollection variant returned by newPermissionCollection().
     *
     * @serial include
     */
    static class GrantPermissionCollection extends PermissionCollection {

	private static final long serialVersionUID = 8227621799817733985L;

	/**
	 * @serialField perms List The permissions.
	 */
	private static final ObjectStreamField[] serialPersistentFields = {
	    new ObjectStreamField("perms", List.class, true)
	};
        
        // Serial form.
	private List<Permission> perms = new ArrayList<Permission>();
	private Implier implier = new Implier();

	public synchronized void add(Permission p) {
	    if (!(p instanceof GrantPermission)) {
		throw new IllegalArgumentException("invalid permission: " + p);
	    }
	    if (isReadOnly()) {
		throw new SecurityException(
		    "can't add to read-only PermissionCollection");
	    }
            // Cannot use TreeSet to ensure correctness, just don't
            // add twice, in other words check must be external.
            // Stack overflow may occur if permissions added without checking
            perms.add(p);
            implier.add((GrantPermission) p);
	    
	}
	
	public synchronized Enumeration<Permission> elements() {
	    return Collections.enumeration(perms);
	}
	
	public synchronized boolean implies(Permission p) {
	    return implier.implies(p);
        }

	public synchronized void setReadOnly() {
	    super.setReadOnly();
	}

	public synchronized boolean isReadOnly() {
	    return super.isReadOnly();
	}

	/**
	 * Writes the permissions list.
	 */
	private synchronized void writeObject(ObjectOutputStream s)
	    throws IOException
	{
	    s.defaultWriteObject();
	}

	/**
	 * Verifies the permissions list.
	 *
	 * @throws InvalidObjectException if the list is
	 * 	   <code>null</code> or any element is not an instance of
	 * 	   <code>GrantPermission</code>
	 */
	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    s.defaultReadObject();
	    if (perms == null) {
		throw new InvalidObjectException("list cannot be null");
	    }
	    if (!perms.getClass().equals(ArrayList.class)) {
		perms = new ArrayList(perms);
	    }
	    if (perms.contains(null)) {
		throw new InvalidObjectException(
		    "element must be a GrantPermission");
	    }

	    GrantPermission[] pa;
	    try {
		pa = (GrantPermission[]) 
		    perms.toArray(new GrantPermission[perms.size()]);
	    } catch (ArrayStoreException e) {
		throw new InvalidObjectException(
		    "element must be a GrantPermission");
	    }

	    implier = new Implier();
	    for (int i = 0; i < pa.length; i++) {
		implier.add(pa[i]);
	    }
	}
    }
}
