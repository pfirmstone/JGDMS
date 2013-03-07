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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Represents permission to use the private credentials of subjects for the
 * purpose of authenticating as any subset of the local principals specified
 * in the target name, during secure remote calls with any peer that
 * authenticates as at least the set of peer principals specified in the
 * target name. In general, security providers check for this permission
 * instead of checking for
 * {@link javax.security.auth.PrivateCredentialPermission}. This
 * permission does not need to be granted for anonymous communication;
 * it only needs to be granted if an entity needs to authenticate itself.
 * <p>
 * An instance of this class contains a name (also referred to as a "target
 * name") and a set of actions. The target name specifies both the maximum
 * set of principals that an entity can authenticate as, and the minimum
 * set of principals that the peer must authenticate as. The actions specify
 * whether the permission is granted for making outbound remote calls with or
 * without delegation, listening for incoming remote calls, receiving
 * incoming remote calls, or some combination.
 * <p>
 * The syntax of the target name is either:
 * <pre><i>LocalPrincipals</i></pre>
 * or:
 * <pre><i>LocalPrincipals</i> <code>peer</code> <i>PeerPrincipals</i></pre>
 * where <i>LocalPrincipals</i> specifies the maximum set of principals that
 * an entity can authenticate as (that is, the entity can authenticate as any
 * subset of these principals), and <i>PeerPrincipals</i> specifies the
 * minimum set of principals that the peer must authenticate as (that is,
 * the peer must authenticate as at least all of these principals). If the
 * first syntactic form is used, the peer can authenticate as anyone (and can
 * be anonymous). The syntax of both <i>LocalPrincipals</i> and
 * <i>PeerPrincipals</i> is:
 * <pre><i>PrincipalClass</i> "<i>PrincipalName</i>" ...</pre>
 * That is, alternating principal classes and principal names, separated by
 * spaces, with each principal name surrounded by quotes. The order in which
 * principals are specified does not matter, but both class names and
 * principal names are case sensitive. For <i>LocalPrincipals</i>, in any
 * given principal specification, a wildcard value of "*" can be used for
 * both <i>PrincipalClass</i> and <i>PrincipalName</i> or for just
 * <i>PrincipalName</i>, but it is illegal to use a wildcard value for just
 * <i>PrincipalClass</i>. Explicit wildcard values cannot be used in
 * <i>PeerPrincipals</i>; only complete wildcarding of the peer is supported,
 * and is expressed by using the first syntactic form instead.
 * <p>
 * The syntax of the actions is a comma-separated list of any of the following
 * (case-insensitive) action names: <code>listen</code>, <code>accept</code>,
 * <code>connect</code>, <code>delegate</code>. The <code>listen</code> action
 * grants permission to authenticate as the server when listening for
 * incoming remote calls; in this case, the peer principals are ignored
 * (because it is assumed that in general servers authenticate themselves
 * before clients do). The <code>accept</code> action grants permission to
 * receive authenticated incoming remote calls; in this case, the entity has
 * authenticated as the server, and the peer has authenticated as the client.
 * If the <code>accept</code> action is specified, the <code>listen</code>
 * action is implied and need not be specified explicitly. The
 * <code>connect</code> action grants permission to authenticate when
 * making outgoing remote calls; in this case, the entity authenticates as
 * the client, and the peer authenticates as the server. The
 * <code>delegate</code> action grants permission to authenticate with
 * (or without) delegation when making outgoing remote calls. If the
 * <code>delegate</code> action is specified, the <code>connect</code>
 * action is implied and need not be specified explicitly.
 * <p>
 * A principal <code>p</code> matches <i>LocalPrincipals</i> if
 * <i>LocalPrincipals</i> has any of the following principal specifications:
 * <ul>
 * <li>"*" for both <i>PrincipalClass</i> and <i>PrincipalName</i>
 * <li>a <i>PrincipalClass</i> equal to the value of
 * <code>p.getClass().getName()</code> and a <i>PrincipalName</i> equal to "*"
 * <li>a <i>PrincipalClass</i> equal to the value of
 * <code>p.getClass().getName()</code> and a <i>PrincipalName</i> equal to
 * the value of <code>p.getName()</code>
 * </ul>
 * A principal <code>p</code> matches <i>PeerPrincipals</i> if
 * <i>PeerPrincipals</i> has a <i>PrincipalClass</i> equal to the value of
 * <code>p.getClass().getName()</code> and a <i>PrincipalName</i> equal to
 * the value of <code>p.getName()</code>.
 * <p>
 * Some example policy file permissions:
 * <pre>
 * // client authenticate as jack, with or without delegation, to any server
 * permission net.jini.security.AuthenticationPermission
 *     "javax.security.auth.x500.X500Principal \"CN=jack\"", "delegate";
 *
 * // client authenticate as joe and/or sue, without delegation, to any server
 * permission net.jini.security.AuthenticationPermission
 *     "javax.security.auth.x500.X500Principal \"CN=joe\" javax.security.auth.x500.X500Principal \"CN=sue\"", "connect";
 *
 * // client authenticate as any X500 principals, without delegation, to jack
 * permission net.jini.security.AuthenticationPermission
 *     "javax.security.auth.x500.X500Principal \"*\" peer javax.security.auth.x500.X500Principal \"CN=jack\"", "connect";
 *
 * // authenticate as jack to jack, bi-directional, with or without delegation
 * permission net.jini.security.AuthenticationPermission
 *     "javax.security.auth.x500.X500Principal \"CN=jack\" peer javax.security.auth.x500.X500Principal \"CN=jack\"", "accept,delegate";
 *
 * // authenticate as anyone to jack, bi-directional, without delegation
 * permission net.jini.security.AuthenticationPermission
 *     "* \"*\" peer javax.security.auth.x500.X500Principal \"CN=jack\"", "accept,connect";
 * </pre>
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public final class AuthenticationPermission extends Permission {
    private static final long serialVersionUID = -4733723479228998183L;

    /**
     * The listen action.
     */
    private final static int LISTEN = 0x1;
    /**
     * The connect action.
     */
    private final static int CONNECT = 0x2;
    /**
     * The accept action (includes the listen action).
     */
    private final static int ACCEPT = 0x4 | LISTEN;
    /**
     * The delegate action (includes the connect action).
     */
    private final static int DELEGATE = 0x8 | CONNECT;

    /**
     * The actions.
     *
     * @serial
     */
    private String actions;
    /**
     * The parsed elements of the local principals with wildcard principal
     * names replaced by null, or null if there is a principal with both a
     * wildcard class and a wildcard name. If there is an element with a
     * null principal name, no other element with the same class name will
     * exist.
     */
    private transient String[] me;
    /**
     * The parsed elements of the peer principals, or null if no peer
     * principals were specified.
     */
    private transient String[] peer;
    /**
     * The parsed actions as a bitmask.
     */
    private transient int mask;

    /**
     * Creates an instance with the specified target name and actions.
     *
     * @param name the target name
     * @param actions the actions
     * @throws NullPointerException if the target name or actions string is
     * <code>null</code>
     * @throws IllegalArgumentException if the target name or actions string
     * does not match the syntax specified in the comments at the beginning
     * of this class
     */
    public AuthenticationPermission(String name, String actions) {
	super(name);
	this.actions = actions;
	init();
    }

    /**
     * Creates an instance with the specified actions and a target name
     * constructed from the specified local and peer principals.
     *
     * @param local the local principals
     * @param peer the peer principals, or <code>null</code>
     * @param actions the actions
     * @throws NullPointerException if the local principals set or the
     * actions string is <code>null</code>
     * @throws IllegalArgumentException if the local principals set is
     * empty, or either set contains objects that are not
     * <code>java.security.Principal</code> instances, or the actions string
     * does not match the syntax specified in the comments at the beginning
     * of this class
     */
    public AuthenticationPermission(Set local, Set peer, String actions) {
	this(parseName(local, peer), actions);
    }

    /**
     * Internal structure to work around the fact that you can't do
     * computation on this prior to calling super() in a constructor.
     */
    private static final class Data {
	/**
	 * The target name.
	 */
	String name;
	/**
	 * The parsed elements of the local principals.
	 */
	String[] me;
	/**
	 * The parsed elements of the peer principals.
	 */
	String[] peer;

	/**
	 * Simple constructor.
	 */
	Data() {}
    }

    /**
     * Creates an instance with the specified data and actions.
     */
    private AuthenticationPermission(Data data, String actions) {
	super(data.name);
	this.me = data.me;
	this.peer = data.peer;
	this.actions = actions;
	parseActions();
    }

    /**
     * Parses the target name and actions, and initializes the transient
     * fields.
     */
    private void init() {
	parseActions();
	parseName(new StringTokenizer(getName(), " ", true), false);
    }

    /**
     * Parses the actions field and initializes the transient mask field.
     */
    private void parseActions() {
	StringTokenizer st = new StringTokenizer(actions, " ,", true);
	boolean comma = false;
	while (st.hasMoreTokens()) {
	    String act = st.nextToken();
	    if (act.equals(" ")) {
		continue;
	    } else if (comma) {
		if (!act.equals(",")) {
		    comma = false;
		    break;
		}
	    } else if (act.equalsIgnoreCase("connect")) {
		mask |= CONNECT;
	    } else if (act.equalsIgnoreCase("accept")) {
		mask |= ACCEPT;
	    } else if (act.equalsIgnoreCase("delegate")) {
		mask |= DELEGATE;
	    } else if (act.equalsIgnoreCase("listen")) {
		mask |= LISTEN;
	    } else {
		break;
	    }
	    comma = !comma;
	}
	if (!comma) {
	    throw new IllegalArgumentException("invalid actions");
	}
    }

    /**
     * Parses what's left of the target name in the specified tokenizer,
     * and initializes the transient fields. Peer is false when parsing
     * the local principals, true when parsing the peer principals.
     */
    private void parseName(StringTokenizer st, boolean peer) {
	List vals = new ArrayList(2);
    outer:
	while (true) {
	    String cls;
	    do {
		if (!st.hasMoreTokens()) {
		    break outer;
		}
		cls = st.nextToken();
	    } while (cls.equals(" "));
	    if (!peer && cls.equalsIgnoreCase("peer")) {
		parseName(st, true);
		break;
	    }
	    if (cls.equals("*")) {
		if (peer) {
		    throw new IllegalArgumentException(
						   "peer class cannot be *");
		}
		cls = null;
		vals = null;
	    }
	    String nm;
	    do {
		if (!st.hasMoreTokens()) {
		    throw new IllegalArgumentException(
						 "missing name after class");
		}
		nm = st.nextToken();
	    } while (nm.equals(" "));
	    if (!nm.startsWith("\"")) {
		throw new IllegalArgumentException("name must be in quotes");
	    }
            StringBuilder sb = new StringBuilder(120);
            sb.append(nm);
	    while (!sb.substring(sb.length()-1).equals("\"")) {
		if (!st.hasMoreTokens()) {
		    throw new IllegalArgumentException(
						   "name must be in quotes");
		}
		sb.append(st.nextToken());
	    }
            nm = sb.toString();
	    if (nm.equals("\"*\"")) {
		if (peer) {
		    throw new IllegalArgumentException(
						"peer name cannot be \"*\"");
		}
		if (cls == null) {
		    continue;
		}
		nm = null;
	    } else if (cls == null) {
		throw new IllegalArgumentException(
				   "class cannot be * unless name is \"*\"");
	    } else {
		nm = nm.substring(1, nm.length() - 1);
	    }
	    if (vals != null) {
		for (int i = vals.size(); i > 0; ) {
		    String onm = (String) vals.get(--i);
		    String ocls = (String) vals.get(--i);
		    if (cls.equals(ocls)) {
			if (onm == null || (onm != null && onm.equals(nm))) {
			    continue outer;
			} else if (nm == null) {
			    vals.remove(i);
			    vals.remove(i);
			}
		    }
		}
		vals.add(cls);
		vals.add(nm);
	    }
	}
	String[] res = null;
	if (vals != null) {
	    if (vals.isEmpty()) {
		throw new IllegalArgumentException(
					  "target name is missing elements");
	    }
	    res = (String[]) vals.toArray(new String[vals.size()]);
	}
	if (peer) {
	    this.peer = res;
	} else {
	    this.me = res;
	}
    }

    /**
     * Returns an array of alternating class and principal names for the
     * specified set of principals, and appends all of those strings to
     * the specified buffer, separated by spaces, with the principal
     * names in quotes.
     */
    private static String[] cons(Set s, StringBuffer b) {
	String[] vals = new String[s.size() * 2];
	int i = 0;
	for (Iterator iter = s.iterator(); iter.hasNext(); ) {
	    Principal p;
	    try {
		p = (Principal) iter.next();
	    } catch (ClassCastException e) {
		throw new IllegalArgumentException(
					    "sets must contain Principals");
	    }
	    String v = p.getClass().getName();
	    if (i > 0) {
		b.append(' ');
	    }
	    b.append(v);
	    vals[i++] = v;
	    v = p.getName();
	    b.append(" \"");
	    b.append(v);
	    b.append('"');
	    vals[i++] = v;
	}
	return vals;
    }

    /**
     * Constructs the target name and transient field data for the
     * specified principal sets.
     */
    private static Data parseName(Set me, Set peer) {
	if (me == null) {
	    throw new NullPointerException(
					"local principals must be non-empty");
	} else if (me.isEmpty()) {
	    throw new IllegalArgumentException(
					"local principals must be non-empty");
	}
	Data data = new Data();
	StringBuffer b = new StringBuffer();
	data.me = cons(me, b);
	if (peer != null && !peer.isEmpty()) {
	    b.append(" peer ");
	    data.peer = cons(peer, b);
	}
	data.name = b.toString();
	return data;
    }

    /**
     * Returns <code>true</code> if the specified permission is an instance
     * of <code>AuthenticationPermission</code>, and every action included in
     * the specified permission is included as an action of this permission,
     * and every principal that matches the local principals of the specified
     * permission also matches the local principals of this permission, and
     * (if the specified permission has any action besides
     * <code>listen</code>) every principal that matches the peer principals
     * of this permission also matches the peer principals of the specified
     * permission; returns <code>false</code> otherwise.
     *
     * @param perm the permission to check
     * @return <code>true</code> if the specified permission is an instance
     * of <code>AuthenticationPermission</code>, and every action included in
     * the specified permission is included as an action of this permission,
     * and every principal that matches the local principals of the specified
     * permission also matches the local principals of this permission, and
     * (if the specified permission has any action besides
     * <code>listen</code>) every principal that matches the peer principals
     * of this permission also matches the peer principals of the specified
     * permission; <code>false</code> otherwise
     */
    public boolean implies(Permission perm) {
	if (!(perm instanceof AuthenticationPermission)) {
	    return false;
	}
	AuthenticationPermission ap = (AuthenticationPermission) perm;
	return (mask & ap.mask) == ap.mask && implies0(ap);
    }

    private boolean implies0(AuthenticationPermission ap) {
	return ((me == null || (ap.me != null && covers(me, ap.me))) &&
		(ap.mask == LISTEN || peer == null ||
		 (ap.peer != null && covers(ap.peer, peer))));
    }

    /**
     * Returns true if every principal that matches sub also matches sup.
     */
    private static boolean covers(String[] sup, String[] sub) {
    outer:
	for (int i = sub.length; i > 0; ) {
	    String onm = sub[--i];
	    String ocls = sub[--i];
	    for (int j = sup.length; j > 0; ) {
		String nm = sup[--j];
		String cls = sup[--j];
		if (cls.equals(ocls) &&
		    (nm == null || (onm != null && nm.equals(onm))))
		{
		    continue outer;
		}
	    }
	    return false;
	}
	return true;
    }

    /**
     * Returns the actions.
     */
    public String getActions() {
	return actions;
    }

    /**
     * Returns an empty <code>PermissionCollection</code> for storing
     * <code>AuthenticationPermission</code> instances.
     *
     * @return an empty <code>PermissionCollection</code> for storing
     * <code>AuthenticationPermission</code> instances
     */
    public PermissionCollection newPermissionCollection() {
	return new AuthenticationPermissionCollection();
    }

    /**
     * Two instances of this class are equal if each implies the other;
     * that is, both instances have the same actions, every principal that
     * matches the local principals of one instance matches the local
     * principals of the other instance, and (if the instances have any
     * action besides <code>listen</code>) every principal that matches the
     * peer principals of one instance matches the peer principals of the
     * other instance.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof AuthenticationPermission)) {
	    return false;
	}
	AuthenticationPermission ap = (AuthenticationPermission) obj;
	return (mask == ap.mask && same(me, ap.me) &&
		(mask == LISTEN || same(peer, ap.peer)));
    }

    /**
     * Returns true if both arrays are null, or both arrays are the same
     * length and contain the same pairs (ignoring order).
     */
    private static boolean same(String[] s1, String[] s2) {
	if (s1 == null) {
	    return s2 == null;
	} else if (s2 == null || s1.length != s2.length) {
	    return false;
	}
    outer:
	for (int i = s2.length; i > 0; ) {
	    String onm = s2[--i];
	    String ocls = s2[--i];
	    for (int j = s1.length; j > 0; ) {
		String nm = s1[--j];
		String cls = s1[--j];
		if (cls.equals(ocls) &&
		    (nm == null ? onm == null : nm.equals(onm)))
		{
		    continue outer;
		}
	    }
	    return false;
	}
	return true;
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	int h = mask;
	if (me != null) {
	    for (int i = me.length; --i >= 0; ) {
		if (me[i] != null) {
		    h += me[i].hashCode();
		}
	    }
	}
	if (mask != LISTEN && peer != null) {
	    for (int i = peer.length; --i >= 0; ) {
		h += peer[i].hashCode();
	    }
	}
	return h;
    }

    /**
     * Verifies the syntax of the target name and recreates any transient
     * state.
     *
     * @throws java.io.InvalidObjectException if the target name or actions
     * string is <code>null</code>, or if the target name or actions string
     * does not match the syntax specified in the comments at the beginning
     * of this class
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	try {
	    init();
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
     * @serial include
     */
    static class AuthenticationPermissionCollection
				extends PermissionCollection
    {
	private static final long serialVersionUID = -2967578431368213049L;

	/**
	 * @serialField permissions List The permissions.
	 */
	private static final ObjectStreamField[] serialPersistentFields = {
	    new ObjectStreamField("permissions", List.class, true)
	};

	private List permissions = new ArrayList();

	AuthenticationPermissionCollection() {
	}

	public synchronized void add(Permission perm) {
	    if (!(perm instanceof AuthenticationPermission)) {
		throw new IllegalArgumentException(
			       "element must be an AuthenticationPermission");
	    } else if (isReadOnly()) {
		throw new SecurityException("collection is read-only");
	    }
	    permissions.add(perm);
	}

	public synchronized boolean implies(Permission perm) {
	    if (!(perm instanceof AuthenticationPermission)) {
		return false;
	    }
	    AuthenticationPermission ap = (AuthenticationPermission) perm;
	    int needed = ap.mask;
	    for (int i = permissions.size(); --i >= 0; ) {
		AuthenticationPermission cp =
		    (AuthenticationPermission) permissions.get(i);
		if ((needed & cp.mask) != 0 && cp.implies0(ap)) {
		    needed &= ~cp.mask;
		    if (needed == 0) {
			return true;
		    }
		}
	    }
	    return false;
	}

	public synchronized Enumeration elements() {
	    return Collections.enumeration(permissions);
	}

	public synchronized void setReadOnly() {
	    super.setReadOnly();
	}

	public synchronized boolean isReadOnly() {
	    return super.isReadOnly();
	}

	/**
	 * Verifies the permissions list.
	 *
	 * @throws java.io.InvalidObjectException if the list is
	 * <code>null</code> or any element is not an instance of
	 * <code>AuthenticationPermission</code>
	 */
	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    s.defaultReadObject();
	    if (permissions == null) {
		throw new InvalidObjectException("list cannot be null");
	    }
	    if (!permissions.getClass().equals(ArrayList.class)) {
		permissions = new ArrayList(permissions);
	    }
	    for (int i = permissions.size(); --i >= 0; ) {
		if (!(permissions.get(i) instanceof AuthenticationPermission))
		{
		    throw new InvalidObjectException(
			       "element must be an AuthenticationPermission");
		}
	    }
	}

	/**
	 * Writes the state to the stream.
	 */
	private synchronized void writeObject(ObjectOutputStream s)
	    throws IOException
	{
	    s.defaultWriteObject();
	}
    }
}
