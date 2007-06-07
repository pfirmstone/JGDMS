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

package net.jini.security.policy;

import com.sun.jini.collection.WeakIdentityMap;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Principal;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.jini.security.GrantPermission;

/**
 * Security policy provider that supports dynamic granting of permissions at
 * run-time.  This provider is designed as a wrapper to layer dynamic grant
 * functionality on top of an underlying policy provider.  If the underlying
 * provider does not implement the {@link DynamicPolicy} interface, then its
 * permission mappings are assumed to change only when its {@link
 * Policy#refresh refresh} method is called.  Permissions are granted on the
 * granularity of class loader; granting a permission requires (of the calling
 * context) {@link GrantPermission} for that permission.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public class DynamicPolicyProvider extends Policy implements DynamicPolicy {

    private static final String basePolicyClassProperty =
	"net.jini.security.policy." +
	"DynamicPolicyProvider.basePolicyClass";
    private static final String defaultBasePolicyClass =
	"net.jini.security.policy.PolicyFileProvider";
    private static final ProtectionDomain sysDomain = (ProtectionDomain)
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() { return Object.class.getProtectionDomain(); }
	});

    private final Policy basePolicy;
    private final boolean cacheBasePerms;
    private final WeakIdentityMap domainPerms = new WeakIdentityMap();
    private final WeakIdentityMap loaderGrants = new WeakIdentityMap();
    private final Grants globalGrants = new Grants();

    /**
     * Creates a new <code>DynamicPolicyProvider</code> instance that wraps a
     * default underlying policy.  The underlying policy is created as follows:
     * if the
     * <code>net.jini.security.policy.DynamicPolicyProvider.basePolicyClass</code>
     * security property is set, then its value is interpreted as the class
     * name of the base (underlying) policy provider; otherwise, a default
     * class name of
     * <code>"net.jini.security.policy.PolicyFileProvider"</code>
     * is used.  The base policy is then instantiated using the no-arg public
     * constructor of the named class.  If the base policy class is not found,
     * is not instantiable via a public no-arg constructor, or if invocation of
     * its constructor fails, then a <code>PolicyInitializationException</code>
     * is thrown.
     * <p>
     * Note that this constructor requires the appropriate
     * <code>"getProperty"</code> {@link java.security.SecurityPermission} to
     * read the
     * <code>net.jini.security.policy.DynamicPolicyProvider.basePolicyClass</code>
     * security property, and may require <code>"accessClassInPackage.*"</code>
     * {@link RuntimePermission}s, depending on the package of the base policy
     * class.
     *
     * @throws  PolicyInitializationException if unable to construct the base
     *          policy
     * @throws  SecurityException if there is a security manager and the
     *          calling context does not have adequate permissions to read the
     *          <code>net.jini.security.policy.DynamicPolicyProvider.basePolicyClass</code>
     *          security property, or if the calling context does not have
     *          adequate permissions to access the base policy class
     */
    public DynamicPolicyProvider() throws PolicyInitializationException {
	String cname = Security.getProperty(basePolicyClassProperty);
	if (cname == null) {
	    cname = defaultBasePolicyClass;
	}
	try {
	    basePolicy = (Policy) Class.forName(cname).newInstance();
	} catch (SecurityException e) {
	    throw e;
	} catch (Exception e) {
	    throw new PolicyInitializationException(
		"unable to construct base policy", e);
	}
	cacheBasePerms = !(basePolicy instanceof DynamicPolicy);
	ensureDependenciesResolved();
    }

    /**
     * Creates a new <code>DynamicPolicyProvider</code> instance that wraps
     * around the given non-<code>null</code> base policy object.
     *
     * @param   basePolicy base policy object containing information about
     *          non-dynamic grants
     * @throws  NullPointerException if <code>basePolicy</code> is
     * 		<code>null</code>
     */
    public DynamicPolicyProvider(Policy basePolicy) {
	if (basePolicy == null) {
	    throw new NullPointerException();
	}
	this.basePolicy = basePolicy;
	cacheBasePerms = !(basePolicy instanceof DynamicPolicy);
	ensureDependenciesResolved();
    }

    /**
     * Behaves as specified by {@link Policy#getPermissions(CodeSource)}.
     */
    public PermissionCollection getPermissions(CodeSource source) {
	PermissionCollection pc = basePolicy.getPermissions(source);
	Permission[] pa = globalGrants.get(null);
	for (int i = 0; i < pa.length; i++) {
	    Permission p = pa[i];
	    if (!pc.implies(p)) {
		pc.add(p);
	    }
	}
	return pc;
    }

    /**
     * Behaves as specified by {@link Policy#getPermissions(ProtectionDomain)}.
     */
    public PermissionCollection getPermissions(ProtectionDomain domain) {
	return getDomainPermissions(domain).getPermissions(domain);
    }

    /**
     * Behaves as specified by {@link Policy#implies}.
     */
    public boolean implies(ProtectionDomain domain, Permission permission) {
	return getDomainPermissions(domain).implies(permission, domain);
    }

    /**
     * Behaves as specified by {@link Policy#refresh}.
     */
    public void refresh() {
	basePolicy.refresh();
	if (cacheBasePerms) {
	    synchronized (domainPerms) {
		domainPerms.clear();
	    }
	}
    }

    // documentation inherited from DynamicPolicy.grantSupported
    public boolean grantSupported() {
	return true;
    }

    // documentation inherited from DynamicPolicy.grant
    public void grant(Class cl, 
		      Principal[] principals, 
		      Permission[] permissions) 
    {
	if (cl != null) {
	    checkDomain(cl);
	}
	if (principals != null && principals.length > 0) {
	    principals = (Principal[]) principals.clone();
	    checkNullElements(principals);
	}
	if (permissions == null || permissions.length == 0) {
	    return;
	}
	permissions = (Permission[]) permissions.clone();
	checkNullElements(permissions);

	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(new GrantPermission(permissions));
	}

	Grants g = (cl != null) ?
	    getLoaderGrants(getClassLoader(cl)) : globalGrants;
	g.add(principals, permissions);
    }

    // documentation inherited from DynamicPolicy.getGrants
    public Permission[] getGrants(Class cl, Principal[] principals) {
	if (cl != null) {
	    checkDomain(cl);
	}
	if (principals != null && principals.length > 0) {
	    principals = (Principal[]) principals.clone();
	    checkNullElements(principals);
	}

	List l = Arrays.asList(globalGrants.get(principals));
	if (cl != null) {
	    l = new ArrayList(l);
	    l.addAll(Arrays.asList(
			 getLoaderGrants(getClassLoader(cl)).get(principals)));
	}
	PermissionCollection pc = new Permissions();
	for (Iterator i = l.iterator(); i.hasNext(); ) {
	    Permission p = (Permission) i.next();
	    if (!pc.implies(p)) {
		pc.add(p);
	    }
	}
	l = Collections.list(pc.elements());
	return (Permission[]) l.toArray(new Permission[l.size()]);
    }

    /**
     * Ensures that any classes depended on by this policy provider are
     * resolved.  This is to preclude lazy resolution of such classes during
     * operation of the provider, which can result in deadlock as described by
     * bug 4911907.
     */
    private void ensureDependenciesResolved() {
	// force class resolution by pre-invoking method called by implies()
	getDomainPermissions(sysDomain);
    }

    private DomainPermissions getDomainPermissions(ProtectionDomain pd) {
	DomainPermissions dp;
	synchronized (domainPerms) {
	    dp = (DomainPermissions) domainPerms.get(pd);
	}
	if (dp == null) {
	    dp = new DomainPermissions(pd);
	    globalGrants.register(dp);
	    if (pd != null) {
		getLoaderGrants(pd.getClassLoader()).register(dp);
	    }
	    synchronized (domainPerms) {
		domainPerms.put(pd, dp);
	    }
	}
	return dp;
    }

    private Grants getLoaderGrants(ClassLoader ldr) {
	synchronized (loaderGrants) {
	    Grants g = (Grants) loaderGrants.get(ldr);
	    if (g == null) {
		loaderGrants.put(ldr, g = new Grants());
	    }
	    return g;
	}
    }

    private static ClassLoader getClassLoader(final Class cl) {
	return (ClassLoader) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() { return cl.getClassLoader(); }
	    });
    }

    private static void checkDomain(final Class cl) {
	ProtectionDomain pd = (ProtectionDomain) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() { return cl.getProtectionDomain(); }
	    });
	if (pd != sysDomain && pd.getClassLoader() == null) {
	    throw new UnsupportedOperationException(
		"ungrantable protection domain");
	}
    }

    private static void checkNullElements(Object[] array) {
	for (int i = 0; i < array.length; i++) {
	    if (array[i] == null) {
		throw new NullPointerException();
	    }
	}
    }

    /**
     * Class which holds permissions and principals of a ProtectionDomain. The
     * domainPerms map associates ProtectionDomain instances to instances of
     * this class.
     */
    private class DomainPermissions {

	private final Set principals;
	private final PermissionCollection perms;
	private final List grants = new ArrayList();

	DomainPermissions(ProtectionDomain pd) {
	    Principal[] pra;
	    principals = (pd != null && (pra = pd.getPrincipals()).length > 0)
		? new HashSet(Arrays.asList(pra)) : Collections.EMPTY_SET;
	    perms = cacheBasePerms ? basePolicy.getPermissions(pd) : null;
	}

	Set getPrincipals() {
	    return principals;
	}

	synchronized void add(Permission[] pa) {
	    for (int i = 0; i < pa.length; i++) {
		Permission p = pa[i];
		grants.add(p);
		if (perms != null) {
		    perms.add(p);
		}
	    }
	}

	synchronized PermissionCollection getPermissions(ProtectionDomain d) {
	    return getPermissions(true, d);
	}

	synchronized boolean implies(Permission p, ProtectionDomain domain) {
	    if (perms != null) {
		return perms.implies(p);
	    }
	    if (basePolicy.implies(domain, p)) {
		return true;
	    }
	    if (grants.isEmpty()) {
		return false;
	    }
	    return getPermissions(false, domain).implies(p);
	}

	private PermissionCollection getPermissions(boolean compact,
						    ProtectionDomain domain)
        {
	    // base policy permission collection may not be enumerable
	    assert Thread.holdsLock(this);
	    PermissionCollection pc = basePolicy.getPermissions(domain);
	    for (Iterator i = grants.iterator(); i.hasNext(); ) {
		Permission p = (Permission) i.next();
		if (!(compact && pc.implies(p))) {
		    pc.add(p);
		}
	    }
	    return pc;
	}
    }

    /**
     * Class which tracks dynamic permission grants.
     */
    private static class Grants {

	private final Map principalGrants = new HashMap();
	private final WeakGroup scope;

	Grants() {
	    PrincipalGrants pg = new PrincipalGrants();
	    principalGrants.put(Collections.EMPTY_SET, pg);
	    scope = pg.scope;
	}

	synchronized void add(Principal[] pra, Permission[] pa) {
	    Set prs = (pra != null && pra.length > 0) ?
		new HashSet(Arrays.asList(pra)) : Collections.EMPTY_SET;

	    PrincipalGrants pg = (PrincipalGrants) principalGrants.get(prs);
	    if (pg == null) {
		pg = new PrincipalGrants();
		for (Iterator i = scope.iterator(); i.hasNext();) {
		    DomainPermissions dp = (DomainPermissions) i.next();
		    if (containsAll(dp.getPrincipals(), prs)) {
			pg.scope.add(dp);
		    }
		}
		principalGrants.put(prs, pg);
	    }
	    
	    ArrayList l = new ArrayList();
	    for (int i = 0; i < pa.length; i++) {
		Permission p = pa[i];
		if (!pg.perms.implies(p)) {
		    pg.perms.add(p);
		    l.add(p);
		}
	    }
	    
	    if (l.size() > 0) {
		pa = (Permission[]) l.toArray(new Permission[l.size()]);
		for (Iterator i = pg.scope.iterator(); i.hasNext();) {
		    ((DomainPermissions) i.next()).add(pa);
		}
	    }
	}

	synchronized Permission[] get(Principal[] pra) {
	    Set prs = (pra != null && pra.length > 0) ?
		new HashSet(Arrays.asList(pra)) : Collections.EMPTY_SET;
	    List l = new ArrayList();

	    for (Iterator i = principalGrants.entrySet().iterator();
		 i.hasNext(); )
	    {
		Map.Entry me = (Map.Entry) i.next();
		if (containsAll(prs, (Set) me.getKey())) {
		    PrincipalGrants pg = (PrincipalGrants) me.getValue();
		    l.addAll(Collections.list(pg.perms.elements()));
		}
	    }
	    return (Permission[]) l.toArray(new Permission[l.size()]);
	}

	synchronized void register(DomainPermissions dp) {
	    Set prs = dp.getPrincipals();
	    for (Iterator i = principalGrants.entrySet().iterator(); 
		 i.hasNext(); ) 
	    {
		Map.Entry me = (Map.Entry) i.next();
		if (containsAll(prs, (Set) me.getKey())) {
		    PrincipalGrants pg = (PrincipalGrants) me.getValue();
		    pg.scope.add(dp);
		    List l = Collections.list(pg.perms.elements());
		    dp.add((Permission[]) l.toArray(new Permission[l.size()]));
		}
	    }
	}

	private static boolean containsAll(Set s1, Set s2) {
	    return (s1.size() >= s2.size()) && s1.containsAll(s2);
	}
	
	private static class PrincipalGrants {
	    final WeakGroup scope = new WeakGroup();
	    final PermissionCollection perms = new Permissions();
	    PrincipalGrants() {}
	}
    }

    /**
     * Grouping of non-null, weakly-referenced objects. The structure is a
     * doubly linked list. The resulting structure is not thread safe and
     * must be synchronized externally.
     */
    private static class WeakGroup {
	private final ReferenceQueue rq = new ReferenceQueue();
	private final Node head;
	private final Node tail;
	
	WeakGroup() {
	    head = Node.createEmptyList();
	    tail = head.getNext();
	}
	
	void add(Object obj) {
	    if (obj == null) {
		throw new NullPointerException();
	    }
	    processQueue();
	    Node newNode = new Node(obj, rq);
	    newNode.insertAfter(head);
	}
	
	Iterator iterator() {
	    processQueue();
	    return new Iterator() {
		private Node curNode = head.getNext();
		private Object nextObj = getNext();

		public Object next() {
		    if (nextObj == null) {
			throw new NoSuchElementException();
		    }
		    Object obj = nextObj;
		    nextObj = getNext();
		    return obj;
		}
		
		public boolean hasNext() {
		    return nextObj != null;
		}
		
		public void remove() {
		    throw new UnsupportedOperationException();
		}
		
		private Object getNext() {
		    while (curNode != tail) {
			Object obj = curNode.get();
			if (obj != null) {
			    curNode = curNode.getNext();
			    return obj;
			} else {
			    curNode.enqueue();
			    curNode = curNode.getNext();
			}
		    }
		    return null;
		}
	    };
	}
	
	private void processQueue() {
	    Node n;
	    while ((n = (Node) rq.poll()) != null) {
		n.remove();
	    }
	}

	private static class Node extends WeakReference {
	    private Node next;
	    private Node prev;
	    
	    static Node createEmptyList() {
		Node head = new Node(null);
		Node tail = new Node(null);
		head.next = tail;
		tail.prev = head;
		return head;
	    }
	    
	    // Constructor for initialization of head and tail nodes which
	    // should never be enqueued. 
	    private Node(Object obj) {
		super(obj);
	    }

	    Node(Object obj, ReferenceQueue rq) {
		super(obj, rq);
	    }
	    
	    /**
	     * Inserts this node between <code>pred</code> and its successor
	     */
	    void insertAfter(Node pred) {
		Node succ = pred.next;
		next = succ;
		prev = pred;
		pred.next = this;
		succ.prev = this;
	    }
	    
	    void remove() {
		prev.next = next;
		next.prev = prev;
	    }
	    
	    Node getNext() {
		return next;
	    }
	}
    }
}
