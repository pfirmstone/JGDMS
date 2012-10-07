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

package org.apache.river.api.security;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.Collections;
import java.util.Enumeration;
import java.util.TreeSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import au.net.zeus.collection.RC;
import au.net.zeus.collection.Ref;
import au.net.zeus.collection.Referrer;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.security.UnresolvedPermission;
import org.apache.river.api.security.DefaultPolicyScanner.PermissionEntry;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * A DelegatePermission represents another Permission, called a candidate
 * Permission.  A user granted a DelegatePermission does not have the privilege
 * of the candidate Permission, although a user with a candidate Permission
 * has the privilege of the DelegatePermission that represents the candidate, 
 * while the @ref DelegateSecurityManager is in force.
 * 
 * A DelegatePermission requires a security delegate to be of any
 * use or value. A security delegate has the responsibility to
 * prevent security sensitive objects guarded by the candidate permission from
 * escaping. Typically the candidate Permission is only checked during 
 * construction of a security sensitive object, who's reference may later escape 
 * into untrusted scope. Security delegates utilise Li Gong's 
 * method guard pattern. A security delegates ProtectionDomain is granted the 
 * candidate permission, the security delegate allows any user granted the
 * DelegatePermission to utilise the functions that the candidate Permission
 * guards, when the user no longer has the DelegatePermission, the security
 * delegate no longer allows the user to access the functions guarded by the
 * candidate permission.  
 *
 * Security Delegates enable sensitive objects to be used by code that isn't
 * fully trusted you may want to monitor, such as a 
 * file write that is limited by the number of bytes written, or Permission
 * to write a file you later decide to retract or revoke if a user
 * does something he or she shouldn't, such as exceed a pre set limit or behave
 * in a manner we would like to avoid, such as hogging network bandwidth.
 * 
 * The SecurityManager installed must implement DelegateSecurityManager,
 * otherwise DelegatePermission Guard's will be disabled.  This allows delegates
 * to be included in code, the decision to utilise delegate functionality may
 * delayed until runtime or deployment.
 * 
 * The DelegatePermissionCollection returned by newPermissionCollection() is not
 * synchronized, this decision was made because PermissionCollection's are 
 * usually accessed from within a heterogenous PermissionCollection like 
 * Permissions that synchronizes anyway.  The decision made for the
 * PermissionCollection contract to be synchronized has been broken deliberately
 * in this case, existing PermissionCollection implementatations don't cleanly
 * protect their internal state with synchronization, since the Enumeration
 * returned by elements() will throw a ConcurrentModificationException if in a 
 * loop when Permission's are being added to a PermissionCollection.  In this
 * case external synchronization must be used.
 * 
 * PermissionCollection's are used mostly read only.
 * 
 * Serialization has been designed so the implementation is not
 * tied to serialized form, by utilising a serialization proxy.
 * 
 * The candidate permission name (also referred to as the "target name") of each
 * <code>DelegatePermission</code> instance carries a string representation of the
 * permission represented by the <code>DelegatePermission</code>, while the actions
 * string of each <code>DelegatePermission</code> is always the empty string.  If
 * a <code>DelegatePermission</code> is serialized, only its name string is sent
 * (i.e., the candidate permission is not serialized).  Upon
 * deserialization, the candidate permission is reconstituted based on
 * information in the name string.  After deserialization a DelegatePermission
 * may be safely used in a policy, however it should not be used as a Guard to check
 * Permission as the candidate Permission may be unresolved.
 * <p>
 * The syntax of the target name approximates that used for specifying
 * permissions in the default security policy file, almost identical to 
 * <code>GrantPermission</code>, with the exception that only
 * one candidate permission can be specified; it is listed below using
 * the same grammar notation employed by <i>The Java(TM) Language
 * Specification</i>:
 * <pre>
 * <i>Target</i>:
 *   <i>DelimiterDeclaration</i><sub>opt</sub> <i>Permissions</i> ;<sub>opt</sub>
 *   
 * <i>DelimiterDeclaration</i>:
 *   delim = <i>DelimiterCharacter</i>
 *   
 * <i>Permission</i>:
 *   <i>PermissionClassName</i>
 *   <i>PermissionClassName Name</i>
 *   <i>PermissionClassName Name</i> , <i>Actions</i>
 *   
 * <i>PermissionClassName</i>:
 *   <i>ClassName</i>
 *   
 * <i>Name</i>:
 *   <i>DelimitedString</i>
 *   
 * <i>Actions</i>:
 *   <i>DelimitedString</i>
 * </pre>
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
 * entry would yield a <code>DelegatePermission</code> containing a
 * <code>FooPermission</code> in which the target name would include the word
 * "quoted" surrounded by double-quote characters:
 * <pre>
 * permission org.apache.river.api.security.DelegatePermission
 *     "FooPermission \"a \\\"quoted\\\" string\"";
 * </pre>
 * For comparison, the following policy file entry which uses a custom
 * delimiter would yield an equivalent <code>DelegatePermission</code>:
 * <pre>
 * permission org.apache.river.api.security.DelegatePermission
 *     "delim=| FooPermission |a \"quoted\" string|";
 * </pre>
 * Some additional example policy file permissions:
 * <pre>
 * // allow permission to listen for and accept connections
 * permission org.apache.river.api.security.DelegatePermission
 *     "java.net.SocketPermission \"localhost:1024-\", \"accept,listen\"";
 *
 * // allow permission to read files under /foo, /bar directories
 * permission org.apache.river.api.security.DelegatePermission
 *     "delim=' java.io.FilePermission '/foo/-', 'read'; java.io.FilePermission '/bar/-', 'read'";
 *
 * </pre>
 * 
 * @see DelegateSecurityManager
 * @author Peter Firmstone
 */
public final class DelegatePermission extends Permission{
    private static final long serialVersionUID = 1L;
    /* Object Pool ensures that equals performs very well in collections for 
     * optimum AccessControlContext result caching and minimises memory 
     * consumption.
     */
    private static final ConcurrentMap<String,DelegatePermission> instances 
        = RC.concurrentMap( new NonBlockingHashMap<Referrer<String>,Referrer<DelegatePermission>>()
            , Ref.STRONG, Ref.WEAK, 1000L, 1000L );
    private static final Class[] PARAMS0 = {};
    private static final Class[] PARAMS1 = { String.class };
    private static final Class[] PARAMS2 = { String.class, String.class };
        
    /**
     * Factory method to obtain a DelegatePermission, this is essential to 
     * overcome broken equals contract in some jvm Permission implementations
     * like SocketPermission and to allow caching.
     * 
     * @param p Permission to be represented.
     * @return DelegatePermission
     */
    public static DelegatePermission get(Permission p){
        String name = constructName(p);
	DelegatePermission del = instances.get(name);
	if ( del == null ){
	    del = new DelegatePermission(p);
            @SuppressWarnings("unchecked")
	    DelegatePermission existed = instances.putIfAbsent(name, del);
	    if ( existed != null ){
		del = existed;
	    }
	}
	return del;
    }
    
    private final Permission permission;
    private final int hashCode;
    
    private DelegatePermission(Permission p){
	super(constructName(p));
	permission = p;
	int hash = 5;
	hash = 41 * hash + (this.permission != null ? this.permission.hashCode() : 0);
	hashCode = hash;
    }
    
    /**
     * 
     * This constructor is provided for java policy instantiation, 
     * and is usually called reflectively.  
     * <p>
     * Do not use this constructor, use the static factory method
     * instead.
     * <p>
     * Objects created by this constructor will not be cached.
     * <p>
     * Candidate Permission may be unresolved, this is acceptable for policy
     * use, where the permission class can be resolved later, it is not
     * suitable for Guard or Permission checks.
     * 
     * @param name 
     */
    public DelegatePermission(String name){
        this(initFromName(name)); //Ensures getName() is always identical.
    }
    
    /**
     * Constructs GrantPermission name/target string appropriate for given list
     * of permissions.
     */
    private static String constructName(Permission p) {
	StringBuilder sb = new StringBuilder(60);
	    if (p instanceof UnresolvedPermission) {
                UnresolvedPermission u = (UnresolvedPermission)p;
                String t = u.getUnresolvedType(), n = u.getUnresolvedName(), a = u.getUnresolvedActions();
		sb.append(t);
                if (n != null) {
                    sb.append(" ").append(quote(n));
                    if (a != null){
                        sb.append(", ").append(quote(a));
                    }
                } 
                sb.append("; ");
	    } else {
		Class cl = p.getClass();
		int nargs = maxConsArgs(cl);
		String t = cl.getName(), n = p.getName(), a = p.getActions();
		if (nargs == 2 && a != null) {
		    // REMIND: handle null name?
		    sb.append(t).append(" ").append(quote(n)).append(", ").append(quote(a)).append("; ");
		} else if (nargs >= 1 && n != null) {
		    sb.append(t).append(" ").append(quote(n)).append("; ");
		} else {
		    sb.append(t).append("; ");
		}
	    }
	return sb.toString().trim();
    }
    
    /**
     * Returns the maximum number of String parameters (up to 2) accepted by a
     * constructor of the given class.  Returns -1 if no matching constructor
     * (including no-arg constructor) is defined by given class.
     */
    @SuppressWarnings("unchecked")
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
     * essentially a copy of com.sun.jini.config.ConfigUtil.stringLiteral; the
     * two methods are kept separate since ConfigUtil.stringLiteral could
     * conceivably escape unicode characters, while such escaping would be
     * incorrect for DelegatePermission.
     */
    private static String quote(String s) {
	StringBuilder sb = new StringBuilder(s.length() + 2);
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
     * Initializes DelegatePermission to contain permission described in the
     * given name.  Throws an IllegalArgumentException if the name is
     * misformatted, or specifies an invalid permission class.  Throws a
     * SecurityException if access to the class is not permitted.
     */
    private static Permission initFromName(String name) {
	PermissionEntry pi = parsePermission(name);

	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		int d = pi.getKlass().lastIndexOf('.');
		if (d != -1) {
		    sm.checkPackageAccess(pi.getKlass().substring(0, d));
		}
	    }
	    Class cl;
	    try {
		cl = Class.forName(pi.getKlass());
	    } catch (ClassNotFoundException ex) {
		return new UnresolvedPermission(
		    pi.getKlass(), pi.getName(), pi.getActions(), null);
	    }
	    if (!Permission.class.isAssignableFrom(cl)) {
		throw new IllegalArgumentException(
		    "not a permission class: " + cl);
	    }
	    if (!Modifier.isPublic(cl.getModifiers())) {
		throw new IllegalArgumentException(
		    "non-public permission class: " + cl);
	    }
	    
	    if (pi.getName() == null) {
		try {
                @SuppressWarnings("unchecked")
		    Constructor c = cl.getConstructor(PARAMS0);
		    return (Permission) c.newInstance(new Object[0]);
		} catch (Exception ex) {
                    if (ex instanceof RuntimeException) throw (RuntimeException) ex;
		}
	    } 
	    if (pi.getActions() == null) {
		try {
                @SuppressWarnings("unchecked")
		    Constructor c = cl.getConstructor(PARAMS1);
		    return (Permission) c.newInstance(new Object[]{ pi.getName() });
		} catch (Exception ex) {
                    if (ex instanceof RuntimeException) throw (RuntimeException) ex;
		}
	    } 
	    try {
            @SuppressWarnings("unchecked")
		Constructor c = cl.getConstructor(PARAMS2);
		return (Permission) c.newInstance(new Object[]{ pi.getName(), pi.getActions() });
	    } catch (Exception ex) {
                if (ex instanceof RuntimeException) throw (RuntimeException) ex;
	    }
	    throw new IllegalArgumentException(
		"uninstantiable permission class: " + cl);
    }
    
    /**
     * Parses permission information from given DelegatePermission name string.
     * Throws an IllegalArgumentException if the name string is misformatted.
     */
    private static PermissionEntry parsePermission(String s) {
	try {
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

            
            String type, name = null, actions = null;

            if (st.ttype != StreamTokenizer.TT_WORD) {
                throw new IllegalArgumentException(
                    "expected permission type");
            }
            type = st.sval;

            // REMIND: allow unquoted name/actions?
            st.nextToken();
            if (st.ttype == StreamTokenizer.TT_EOF || st.ttype == ';') {
                return new PermissionEntry(type, null, null, null);
            } else if (st.ttype == delim) {
                name = st.sval;
            } else {
                throw new IllegalArgumentException(
                    "expected permission name or ';'");
            }

            st.nextToken();
            if (st.ttype == StreamTokenizer.TT_EOF || st.ttype == ';') {
                return new PermissionEntry(type, name, null, null);
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
                return new PermissionEntry(type, name, actions, null);
            } else {
                throw new IllegalArgumentException("expected ';'");
            }
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
    
    public void checkGuard(Object object) throws SecurityException {
	SecurityManager sm = System.getSecurityManager();
	if (sm instanceof DelegateSecurityManager) sm.checkPermission(this);
    }

    @Override
    // This is implemented but never called.
    public boolean implies(Permission permission) {
	if (permission == null) return false;
	if (!(permission instanceof DelegatePermission)) return false;
	if ( permission.getClass() != this.getClass()) return false;
	return this.permission.implies(((DelegatePermission) permission).getPermission());
    }
    
    public Permission getPermission(){
	return permission;
    }

    @Override
    public boolean equals(Object obj) {
	if (obj == this) return true;
	if (obj == null) return false;
	if ( obj.hashCode() != hashCode ) return false;
	if ( obj.getClass() != this.getClass() ) return false;
	return getName().equals(((Permission)obj).getName());
    }

    @Override
    public int hashCode() {
	return hashCode;
    }

    @Override
    public String getActions() {
	return "";
    }
    
    @Override
    public PermissionCollection newPermissionCollection() {
	return new DelegatePermissionCollection();
    }
    
    /* Serialization Proxy */
    private static class SerializationProxy implements Serializable {
	private static final long serialVersionUID = 1L;
	private String perm;
	
	SerializationProxy(Permission p){
	    perm = constructName(p);
	}
        
        private void writeObject(ObjectOutputStream out) throws IOException{
            out.defaultWriteObject();
        }
        
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
            in.defaultReadObject();
        }
        
        private Object readResolve() {
            // perm is the field from the Serialization proxy.
            Permission p = instances.get(perm);
            if (p != null) return p;
            return new DelegatePermission(perm); // May contain Unresolved candidate
        }
    }
    
    /* Serialization */
    
    private Object writeReplace() {
	return new SerializationProxy(permission);
    }
    
    private void readObject(ObjectInputStream in) throws InvalidObjectException{
	throw new InvalidObjectException("Proxy required");
    }
    
    /* PermissionCollection */
    
    private static class DelegatePermissionCollection extends PermissionCollection {
	private static final long serialVersionUID = 1L;
	private final transient PermissionCollection candidates;
	private final Set<Permission> delegates;
	
	DelegatePermissionCollection(){
	    candidates = new Permissions();
	    delegates = new TreeSet<Permission>(new PermissionComparator());
	}

	@Override
	public void add(Permission permission) {
	    if (! (permission instanceof DelegatePermission))
	    throw new IllegalArgumentException("invalid permission: "+ permission);
	if (isReadOnly())
	    throw new SecurityException("attempt to add a Permission to a " +
		    "readonly PermissionCollection");
	    delegates.add(permission);
	    candidates.add(((DelegatePermission) permission).getPermission());
	}

	@Override
	public boolean implies(Permission permission) {
	    if ( !(permission instanceof DelegatePermission)) return false;
	    return candidates.implies(
		    ((DelegatePermission )permission).getPermission());
	}

	@Override
	public Enumeration<Permission> elements() {
	    return Collections.enumeration(delegates);
	}
	
	/* Serialization Proxy */
	 private static class CollectionSerializationProxy implements Serializable {
	    private static final long serialVersionUID = 1L;
	    private Permission[] perms;

	    CollectionSerializationProxy(Set<Permission> p){
		perms = p.toArray(new Permission[p.size()]);
	    }
            
            private Object readResolve() {
                // perms is the field from the Serialization proxy.
                DelegatePermissionCollection dpc = new DelegatePermissionCollection();
                int l = perms.length;
                for (int i = 0; i < l; i++ ){
                    dpc.add(perms[i]);
                }
                return dpc;
            }

	}

	/* Serialization */

	private Object writeReplace() {
	    return new CollectionSerializationProxy(delegates);
	}

	private void readObject(ObjectInputStream in) throws InvalidObjectException{
	    throw new InvalidObjectException("Proxy required");
	}

	
	
    }

}
