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

import org.apache.river.api.security.ConcurrentPolicy;
import java.io.IOException;
import java.rmi.RemoteException;
import org.apache.river.api.security.CachingSecurityManager;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Guard;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Security;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.security.ConcurrentPermissions;
import net.jini.security.GrantPermission;
import org.apache.river.api.security.PermissionComparator;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.apache.river.api.security.RemotePolicy;
import org.apache.river.api.security.PolicyPermission;
import org.apache.river.api.security.RevocablePolicy;

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
 * <p>This is a Dynamic Policy Provider that supports concurrent access,
 * for instances where a Policy provider is used for a distributed network
 * of computers, or where there is a large number of ProtectionDomains and
 * hence the opportunity for concurrency exists, concurrency comes with a 
 * cost however, that of increased memory usage.</p>
 * 
 * <p>Due to the Java 2 Security system's static design, a Policy Provider
 * can only augment the policy files utilised, a Policy can only relax security
 * by granting additional permissions, this implementation adds an experimental 
 * feature for revoking permissions, however there are some caveats:</p>
 * 
 * <p>Background: if ProtectionDomain.toString(), is called a ProtectionDomain will
 * merge Permissions, from the policy with those in the ProtectionDomain,
 * in a new private instance of Permissions, thus a ProtectionDomain cannot have 
 * Permission's removed, only additional merged.  A ProtectionDomain must
 * be created with the dynamic constructor otherwise it will never consult
 * the policy.  The AccessController.checkPermission(Permission) method
 * consults the current AccessControlContext, which contains all
 * ProtectionDomain's on the current thread's stack, before consulting the
 * AccessControllContext.checkPermission(Permission), it calls
 * AccessControllContext.optimize() which  removes all duplicate ProtectionDomains
 * in the ProtectionDomain array[]'s from the
 * enclosing AccessContolContext for the execution domain and the nested
 * AccessControlContext for the privileged domain (the privileged domain is
 * an array of ProtectionDomain's on the stack since the last 
 * AccessController.doPriveleged() call).  The optimize() method also calls
 * the DomainCombiner, which, for example, gives the SubjectDomainCombiner the 
 * opportunity to manipulate the ProtectionDomain's in the privileged array, in the
 * SubjectDomainCombiner's case, it creates new copies of the ProtectionDomain's
 * with new Principal[]'s injected.  The optimize() method returns a new
 * optimized AccessControlContext.
 * </p><p>
 * Now the AccessController calls the new AccessControlContext.checkPermission(Permission),
 * at this stage, each ProtectionDomain, if created with the dynamic constructor
 * consults the Policy, calling Policy.implies(ProtectionDomain, Permission).
 * </p><p>
 * If any calls to the policy return false, the ProtectionDomain then checks its
 * internal Permissions and if they return false, it returns false.  The first
 * ProtectionDomain in the AccessControlContext to return false causes the 
 * AccessController.checkPermission(Permission) to throw an AccessControlException
 * </p><p>
 * To optimise the time taken to check Permission's the ProtectionDomain's
 * should either be static, which excludes the Policy, or dynamic with
 * a null PermissionCollection in it's constructor, </p>
 * 
 * <p>So in order to prevent dynamic grants from finding
 * their way into a ProtectionDomain's private PermissionCollection,
 * one would have to ensure that no dynamically grantable permissions are 
 * returned via the method:</p>
 * <p>
 * getPermissions(ProtectionDomain domain) and
 * getPermissions(Codesource source) as a precaution.
 * </p>
 * <p>This is different to the behaviour of the existing Jini 2.0
 * DynamicPolicyProvider implementation where dynamically granted Permissions
 * are added and can escape into the ProtectionDomain's private PermissionCollection.
 * 
 * However when a Policy is checked via implies(ProtectionDomain d, Permission p)
 * this implementation checks the dynamic grants
 * 
 * This means that if a DynamicPolicy is utilised as the base Policy class
 * and if it returns dynamically granted permissions, then those permissions
 * cannot be revoked.</p>
 * <p>
 * It is thus recommended that Static policy files only be used for setting
 * up your privileged code and use UmbrellaGrantPermission's and grant 
 * all other Permission's using dynamic grants.  This minimises the double 
 * checking of Permission, that occurs when a ProtectionDomain is constructed
 * so it contains a default PermissionCollection that is not null.
 *
 * </p><p>
 * To make the best utilisation of this Policy provider, set the System property:
 * </p>,<p>
 * net.jini.security.policy.PolicyFileProvider.basePolicyClass = 
 * org.apache.river.security.concurrent.ConcurrentPolicyFile
 * </p>
 * @author Peter Firmstone
 * @version 1
 * @since 2.2
 * @see ProtectionDomain
 * @see Policy
 * @see ConcurrentPolicyFile
 * @see net.jini.security.policy.PolicyFileProvider
 * @see ConcurrentPermissionCollection
 * @see CachingSecurityManager
 * @see RemotePolicy
 */

public class DynamicPolicyProvider extends Policy implements RemotePolicy, 
        RevocablePolicy {
    private static final Permission ALL_PERMISSION = new AllPermission();
    private static final String basePolicyClassProperty =
	"net.jini.security.policy.DynamicPolicyProvider.basePolicyClass";
    private static final String defaultBasePolicyClass =
            "org.apache.river.api.security.ConcurrentPolicyFile";
//	"net.jini.security.policy.PolicyFileProvider";
    private static final ProtectionDomain sysDomain = 
	AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>() {
        
	    public ProtectionDomain run() { return Object.class.getProtectionDomain(); }
	});
    private static final String revocationSupported = 
            "net.jini.security.policy.DynamicPolicyProvider.revocation";
    private static final Logger logger = Logger.getLogger("net.jini.security.policy");
    
    private static final ProtectionDomain policyDomain = 
            AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>(){
            
            public ProtectionDomain run() {
                return DynamicPolicyProvider.class.getProtectionDomain();
            }
        });
    
    /* 
     * Copy referent before use.
     * 
     * Reference update Protected by grantLock, this array reference must only 
     * be copied or replaced, it must never be read directly or operated on 
     * unless holding grantLock.
     * Local methods must first copy the reference before using the array in
     * loops etc in case the reference is updated.
     * This is important, to prevent the update of the remotePolicyGrant's from
     * causing executing threads from being blocked.
     */
    private volatile PermissionGrant[] remotePolicyGrants; // Write protected by grantLock.
    /* This lock protects write updating of remotePolicyGrants reference */
    private final Object grantLock;
    private final Policy basePolicy; // refresh protected by transactionWriteLock
    // DynamicPolicy grant's for Proxy's.
    private final Collection<PermissionGrant> dynamicPolicyGrants;
    private final boolean basePolicyIsDynamic; // Don't use cache if true.
    private final boolean revokeable;
    private final boolean basePolicyIsRemote;
    private final boolean basePolicyIsConcurrent;
    private final Comparator<Permission> comparator = new PermissionComparator();
    
    private final boolean loggable;
    // do something about some domain permissions for this domain so we can 
    // avoid dead locks due to bug 4911907

    private final Guard revokePermission;
    private final Permission implementsPermissionGrant;
    private final Guard protectionDomainPermission;
    
    
    private final PermissionCollection policyPermissions;
    
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
    public DynamicPolicyProvider() throws PolicyInitializationException{
        String cname = Security.getProperty(basePolicyClassProperty);
	if (cname == null) {
	    cname = defaultBasePolicyClass;
	}
        String tRue = "TRUE";
        String revoke = Security.getProperty(revocationSupported);
        if (revoke == null){
            revoke = tRue;
        }
	try {
	    basePolicy = (Policy) Class.forName(cname).newInstance();
	} catch (SecurityException e) {
	    throw e;
	} catch (Exception e) {
	    throw new PolicyInitializationException(
		"unable to construct base policy", e);
	}
        dynamicPolicyGrants = new CopyOnWriteArrayList<PermissionGrant>();
        
	remotePolicyGrants = new PermissionGrant[0];
        loggable = logger.isLoggable(Level.FINEST);
	grantLock = new Object();
	revokePermission = new PolicyPermission("REVOKE");
        implementsPermissionGrant = new PolicyPermission("implementPermissionGrant");
        protectionDomainPermission = new RuntimePermission("getProtectionDomain");
        if (basePolicy instanceof DynamicPolicy) {
            DynamicPolicy dp = (DynamicPolicy) basePolicy;
            basePolicyIsDynamic = dp.grantSupported();
            if (basePolicy instanceof RevocablePolicy ) {
                RevocablePolicy rp = (RevocablePolicy) basePolicy;
                revokeable = rp.revokeSupported();
            } else {
                revokeable = false;
            }
        } else {
            basePolicyIsDynamic = false;
            revokeable = revoke.equals(tRue);
        }
        basePolicyIsRemote = basePolicy instanceof RemotePolicy ?true: false;
        basePolicyIsConcurrent = basePolicy instanceof ConcurrentPolicy 
                ? ((ConcurrentPolicy) basePolicy).isConcurrent() : false;
        policyPermissions = basePolicy.getPermissions(policyDomain);
        policyPermissions.setReadOnly();
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
    public DynamicPolicyProvider(Policy basePolicy){
        this.basePolicy = basePolicy;
        dynamicPolicyGrants = new CopyOnWriteArrayList<PermissionGrant>();
	remotePolicyGrants = new PermissionGrant[0];
        loggable = logger.isLoggable(Level.FINEST);
	grantLock = new Object();
	revokePermission = new PolicyPermission("REVOKE");
        implementsPermissionGrant = new PolicyPermission("implementPermissionGrant");
        protectionDomainPermission = new RuntimePermission("getProtectionDomain");
         if (basePolicy instanceof DynamicPolicy) {
            DynamicPolicy dp = (DynamicPolicy) basePolicy;
            basePolicyIsDynamic = dp.grantSupported();
            if (basePolicy instanceof RevocablePolicy ) {
                RevocablePolicy rp = (RevocablePolicy) basePolicy;
                revokeable = rp.revokeSupported();
            } else {
                revokeable = false;
            }
        } else {
            basePolicyIsDynamic = false;
            revokeable = true;
        }
        basePolicyIsRemote = basePolicy instanceof RemotePolicy ?true: false;
        basePolicyIsConcurrent = basePolicy instanceof ConcurrentPolicy 
                ? ((ConcurrentPolicy) basePolicy).isConcurrent() : false;
        policyPermissions = basePolicy.getPermissions(policyDomain);
        policyPermissions.setReadOnly();
    }

    /**
     * OLD COMMENT:
     * 
     * Ensures that any classes depended on by this policy provider are
     * resolved.  This is to preclude lazy resolution of such classes during
     * operation of the provider, which can result in deadlock as described by
     * bug 4911907.
     * 
We are seeing intermittent deadlocks between class resolution (of classes
referred to by our policy providers) and other synchronization performed in
or around our security policy providers. A specific example is with the
synchronization on subPolicies in AggregatePolicyProvider.getCurrentSubPolicy.

Thread 1:
    calls ClassLoader.loadClass on the system class loader
    which locks the system class loader
    and then calls SecurityManager.checkPackageAccess
    <Thread 2 runs here>
    which ultimately causes a call to AggregatePolicyProvider.implies
    which calls getCurrentSubPolicy
    which hangs waiting to lock subPolicies

Thread 2:
    calls AggregatePolicyProvider.implies
    which locks subPolicies
    and then attempts to resolve the anonymous PrivilegedAction
    which hangs waiting to lock the system class loader

A similar deadlock occurs due to synchronization in SecurityManager.checkPackageAccess.

Thread 1:
    calls ClassLoader.loadClass on the system class loader
    which calls SecurityManager.checkPackageAccess
    which locks the SecurityManager.packageAccess  customer 
    and then calls SecurityManager.checkPermission
    <Thread 2 runs here>
    which eventually calls AggregatePolicyProvider.implies
    which calls getCurrentSubPolicy
    which calls WeakIdentityMap.get
    which tries to resolve the internal Key class
    which hangs waiting to lock the extension class loader

Thread 2:
    references a class in the extension class loader
    which locks the extension class loader
    and then calls ClassLoader.checkPackageAccess
    which calls SecurityManager.checkPackageAccess
    which hangs waiting to lock the SecurityManager.packageAccess lock

Work Around 	

Put the policy providers and all referenced classes in the bootstrap class loader.
     */
//    private void ensureDependenciesResolved() 

    public boolean revokeSupported() {
        return revokeable;
    }
    
    private PermissionCollection convert(NavigableSet<Permission> permissions, int initialCapacity, float loadFactor, int concurrencyLevel, int unresolvedCapacity){
        PermissionCollection pc = 
                new ConcurrentPermissions(initialCapacity, loadFactor, 
                                        concurrencyLevel, unresolvedCapacity);
        // The descending iterator is for SocketPermission.
        Iterator<Permission> it = permissions.descendingIterator();
        while (it.hasNext()) {
            pc.add(it.next());
        }
        return pc;
    }
    
    private NavigableSet<Permission> processGrants(PermissionGrant[] grant, Class permClass, boolean stopIfAll)
    {
        NavigableSet<Permission> set = new TreeSet<Permission>(comparator);
        int l = grant.length;
        if (permClass == null)
        {
            for (int i = 0; i < l; i++)
            {
                if ( stopIfAll && grant[i].isPrivileged()){
                    set.add(ALL_PERMISSION);
                    return set;
                }
                Iterator<Permission> it = grant[i].getPermissions().iterator();
                while (it.hasNext())
                {
                    Permission p = it.next();
                    set.add(p);
                }
            }
        } 
        else 
        {
            for (int i = 0; i < l; i++)
            {
                if ( stopIfAll && grant[i].isPrivileged()){
                    set.add(ALL_PERMISSION);
                    return set;
                }
                Iterator<Permission> it = grant[i].getPermissions().iterator();
                while (it.hasNext())
                {
                    Permission p = it.next();
                    if (permClass.isInstance(p)|| p instanceof UnresolvedPermission) 
                    {
                        set.add(p);
                    }
                }
            }
        }
        return set;
    }

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
	/* It is extremely important that dynamic grant's are not returned,
	 * to prevent them becoming part of the static permissions held
	 * by a ProtectionDomain.  In this case during construction of a
	 * ProtectionDomain.  Static Permissions are irrevocable.
	 */ 
        NavigableSet<Permission> permissions = null;
        if (!basePolicyIsConcurrent || codesource == null) {
            permissions = new TreeSet<Permission>(comparator);
            PermissionCollection pc = basePolicy.getPermissions(codesource);
            Enumeration<Permission> enu = pc.elements();
            while (enu.hasMoreElements()){
                permissions.add(enu.nextElement());
            }
        }else{
            ProtectionDomain pd = new ProtectionDomain(codesource, null);
            PermissionGrant [] grants = ((ConcurrentPolicy) basePolicy).getPermissionGrants(pd);
            permissions = processGrants(grants, null, true);
        }
//        if (revokeable == true) return convert(permissions);
//        Iterator<PermissionGrant> dynamicGrants = dynamicPolicyGrants.iterator();
//        while (dynamicGrants.hasNext()){
//            PermissionGrant p = dynamicGrants.next();
//            if ( p.implies(codesource, null) ){
//		// Only use the trusted grantCache.
//		Collection<Permission> perms = p.getPermissions();
//                Iterator<Permission> it = perms.iterator();
//                while (it.hasNext()){
//                    permissions.add(it.next());
//                }
//	    }
//        }
	return convert(permissions, 16, 0.75F, 16, 16);
    }

    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        if (domain == policyDomain) return policyPermissions;
	/* Note: we can return revokeable permissions, the  ProtectionDomain
         * only temporarily merges the permissions for toString(), for debugging.
	 */
        NavigableSet<Permission> permissions = null;
        if (!basePolicyIsConcurrent) {
            permissions = new TreeSet<Permission>(comparator);
            PermissionCollection pc = basePolicy.getPermissions(domain);
            Enumeration<Permission> enu = pc.elements();
            while (enu.hasMoreElements()){
                permissions.add(enu.nextElement());
            }
        }else{
            PermissionGrant [] grants = 
                    ((ConcurrentPolicy) basePolicy).getPermissionGrants(domain);
            permissions = processGrants(grants, null, false);
        }
	PermissionGrant [] grantsRefCopy = remotePolicyGrants; // Interim updates not seen.
	int l = grantsRefCopy.length;
	for ( int i = 0; i < l; i++ ){
	    if ( grantsRefCopy[i].implies(domain) ){
		Collection<Permission> perms = grantsRefCopy[i].getPermissions();
		Iterator<Permission> it = perms.iterator();
                while (it.hasNext()){
                    permissions.add(it.next());
                }
	    }
	}
        Iterator<PermissionGrant> dynamicGrants = dynamicPolicyGrants.iterator();
        while (dynamicGrants.hasNext()){
            PermissionGrant p = dynamicGrants.next();
            if ( p.implies(domain) ){
		// Only use the trusted grantCache.
                Collection<Permission> perms = p.getPermissions();
                Iterator<Permission> it = perms.iterator();
                while (it.hasNext()){
                    permissions.add(it.next());
                }
	    }
        }
	return convert(permissions, 16, 0.75F, 16, 16);	
    }
    
    /* River-26 Mark Brouwer suggested making UmbrellaPermission's expandable
     * from Dynamic Grants.
     */ 
    private void expandUmbrella(PermissionCollection pc) {
	PolicyFileProvider.expandUmbrella(pc);
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        if (domain == policyDomain) return policyPermissions.implies(permission);
        if (basePolicyIsDynamic || basePolicyIsRemote){
            if (basePolicy.implies(domain, permission)) return true;
        }
	if (permission == null) throw new NullPointerException("permission not allowed to be null");
        /* If com.sun.security.provider.PolicyFile:
         * Do not call implies on the base Policy, if
         * there are UnresolvedPermission's that are undergoing resolution
         * while another Permission within that collection is already
         * resolved, the Enumeration will cause a ConcurrentModificationException.
         */ 
        
        /* Be mindful of static Permissions held by the 
         * ProtectionDomain, a Permission may be implied by the 
         * the combination of Permission's in the ProtectionDomain and 
         * the base policy, but not by either individually.
         * The ProtectionDomain merge is only perfomed if
         * ProtectionDomain.toString() is called, this is purely for debugging
         * the policy permissions are never merged back into the
         * ProtectionDomain, the underlying policy
         * performs the merge.
         * 
         * Furthermore it is commonly understood that when
         * ProtectionDomain.implies(Permission) is called, it first checks
         * it's own private Permissions, then calls Policy.implies, however
         * this is incorrect, the Policy is checked first.
         */ 
       /* Don't use the underlying policy permission collection otherwise
        * we can leak grants in to the underlying policy from our cache,
        * this could then be inadvertantly cached and passed to a ProtectionDomain
        * constructor, preventing Revocation.
        */
        NavigableSet<Permission> permissions = null; // Keep as small as possible.
        /* If GrantPermission is being requested, we must get all Permission objects
         * and add them to the underlying collection.
         * 
         */
        Class permClass = permission instanceof GrantPermission ? null : permission.getClass();
        if (!basePolicyIsConcurrent) {
            permissions = new TreeSet<Permission>(comparator);
            PermissionCollection pc = basePolicy.getPermissions(domain);
            Enumeration<Permission> enu = pc.elements();
            while (enu.hasMoreElements()){
                Permission p = enu.nextElement();
                if (p instanceof AllPermission) return true; // Return early.
                if ( permClass == null){
                    permissions.add(p);
                } else if ( permClass.isInstance(permission) || permission instanceof UnresolvedPermission){
                    permissions.add(p);
                }
            }
        }else{
            PermissionGrant [] grants = ((ConcurrentPolicy) basePolicy).getPermissionGrants(domain);
            permissions = processGrants(grants, permClass, true);
            if (permissions.contains(ALL_PERMISSION)) return true;
        }
	PermissionGrant[] grantsRefCopy = remotePolicyGrants; // In case the grants volatile reference is updated.       
//        if (thread.isInterrupted()) return false;
	int l = grantsRefCopy.length;
	for ( int i = 0; i < l; i++){
	    if (grantsRefCopy[i].implies(domain)) {
		Collection<Permission> perms = grantsRefCopy[i].getPermissions();
		Iterator<Permission> it = perms.iterator();
                while (it.hasNext()){
                    Permission p = it.next();
                    if ( permClass == null){
                        permissions.add(p);
                    } else if ( permClass.isInstance(permission) || permission instanceof UnresolvedPermission){
                        permissions.add(p);
                    }
                }
	    }
	}
//        if (thread.isInterrupted()) return false;
        Iterator<PermissionGrant> grants = dynamicPolicyGrants.iterator();
        while (grants.hasNext()){
            PermissionGrant g = grants.next();
            if (g.implies(domain)){
                Collection<Permission> perms = g.getPermissions();
                Iterator<Permission> it = perms.iterator();
                while (it.hasNext()){
                    Permission p = it.next();
                    if ( permClass == null){
                        permissions.add(p);
                    } else if ( permClass.isInstance(permission) || permission instanceof UnresolvedPermission){
                        permissions.add(p);
                    }
                }
            }
        }
//        if (thread.isInterrupted()) return false;
        
        PermissionCollection pc = null;
        if (permClass != null){
            pc =convert(permissions, 1, 0.75F, 1, 16);
        } else {
            // GrantPermission
            pc = convert(permissions, 24, 0.75F, 1, 16);
            expandUmbrella(pc);
        }
        return pc.implies(permission);
    }
    
    /**
     * Calling refresh doesn't remove any dynamic grant's, it only clears
     * the cache and refreshes the underlying Policy, it also removes any
     * grants for ProtectionDomains that no longer exist.
     * 
     * If a CachingSecurityManager has been set, this method will clear it's 
     * checked cache.
     * 
     */
    
    @SuppressWarnings("unchecked")
    public void refresh() {
        basePolicy.refresh();
        // Clean up any void dynamic grants.
        Collection<PermissionGrant> remove = new ArrayList<PermissionGrant>(40);
	Iterator<PermissionGrant> i = dynamicPolicyGrants.iterator();
        while (i.hasNext()){
            PermissionGrant p = i.next();
            if (p.isVoid()){
                remove.add(p);
            }
        }
        dynamicPolicyGrants.removeAll(remove);
        // Don't bother removing void from the remotePolicy, it get's replaced anyway.
        // Policy file based grant's don't become void, only dynamic grant's
        // to ProtectionDomain or ClassLoader.
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null){
            if (sm instanceof CachingSecurityManager){
                ((CachingSecurityManager) sm).clearCache();
            }
        }
        
    }

    public boolean grantSupported() {
        return true;
    }

    public void grant(final Class cl, Principal[] principals, Permission[] permissions) {
        if (principals == null){ principals = new Principal[0];}
        checkNullElements(principals);
        // This has to be after checkNullElements principals or we fail the NullCases test.
        if (permissions == null || permissions.length == 0) {return;}
        checkNullElements(permissions);
        // Not delgated to base policy.
        SecurityManager sm = System.getSecurityManager();
        if (sm != null){
            sm.checkPermission(new GrantPermission(permissions));
        }
        final PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        pgb.principals(principals)
            .permissions(permissions)
            .context(PermissionGrantBuilder.CLASSLOADER);
        AccessController.doPrivileged(
            new PrivilegedAction(){
            
                public Object run() {
                    pgb.clazz(cl);
                    return null;
                }
                 
            });
        PermissionGrant pe = pgb.build();
	dynamicPolicyGrants.add(pe);
	if (loggable){
	    logger.log(Level.FINEST, "Granting: {0}", pe.toString());
	}
    }
    
    // documentation inherited from DynamicPolicy.getGrants
    public Permission[] getGrants(Class cl, Principal[] principals) {
        ClassLoader loader = null;
        if( cl != null ) {
            loader = cl.getClassLoader();
        }
        // defensive copy array
        if (principals != null && principals.length > 0) {
	    principals = principals.clone();
	    checkNullElements(principals);
	}
        Collection<Permission> dPerms = new HashSet<Permission>();
        Iterator<PermissionGrant> grants = dynamicPolicyGrants.iterator();
	while ( grants.hasNext()){
            PermissionGrant g = grants.next();
	    if ( g.implies(loader, principals) ){
		// Only use the trusted grantCache.
		dPerms.addAll(g.getPermissions());
	    }
	}	
        Permission[] perms = dPerms.toArray(new Permission[dPerms.size()]);        
        return perms;
    }

    public Permission[] revoke(Class cl, Principal[] principals) {
	revokePermission.checkGuard(null);
        ClassLoader loader = null;
        if( cl != null ) {
            loader = cl.getClassLoader();
        }
        // defensive copy array
        if (principals != null && principals.length > 0) {
	    principals = principals.clone();
	    checkNullElements(principals);
	}
	HashSet<Permission> removed = new HashSet<Permission>();
	Iterator<PermissionGrant> grants = dynamicPolicyGrants.iterator();
	while ( grants.hasNext()){
            PermissionGrant g = grants.next();
	    if ( g.implies(loader, principals) ){
		// Only use the trusted grantCache.
		removed.addAll(g.getPermissions());
                grants.remove();
	    }
	}
        
        SecurityManager sm = System.getSecurityManager();
        if (sm instanceof CachingSecurityManager) {
            ((CachingSecurityManager) sm).clearCache();
        }
       return removed.toArray(new Permission[removed.size()]);
    }
    
    private static void checkNullElements(Object[] array) {
        int l = array.length;
	for (int i = 0; i < l; i++) {
	    if (array[i] == null) {
		throw new NullPointerException();
	    }
	}
    }

    public void replace(PermissionGrant[] grants) throws IOException {
        /* If the base policy is also remote, each will manage their own
         * permissions independantly, so we do not delegate to the underlying policy.  
         * Any underlying local policy file permissions should be propagated up
         * into each policy, which means there will be duplication of some 
         * policy information.
         * It seems logical in the case of multiple remote policies that each
         * could be the responsiblity of a different administrator.  If these
         * separate policy's were to be combined, there may be some cases
         * where two permissions combined also implied a third permission, that
         * neither administrator intended to grant.
         */ 
        // because PermissionGrant's are given references to ProtectionDomain's
        // we must check the caller has this permission.
        try {
        protectionDomainPermission.checkGuard(null); 
        // Delegating to the underlying policy is not supported.
	processRemotePolicyGrants(grants);
        // If we get to here, the caller has permission.
        } catch (SecurityException e){
            throw new RemoteException("Policy update failed", (Throwable) e);
        } catch (NullPointerException e) {
            throw new RemoteException("Policy update failed", (Throwable) e);
        }
    }
    
    /**
     * This method checks that the PermissionGrant's are authorised to be
     * granted by it's caller, if it Fails, it will throw a SecurityException
     * or AccessControlException.
     * 
     * 
     * 
     * The PermissionGrant should not be requested for it's Permission's 
     * again, since doing so would risk an escallation of privelege attack if the
     * PermissionGrant implementation was mutable.
     * 
     * @param grants
     * @return map of checked grants.
     */
    private void 
	    checkCallerHasGrants(Collection<PermissionGrant> grants) throws SecurityException {
        Iterator<PermissionGrant> grantsItr = grants.iterator();
        while (grantsItr.hasNext()){
            PermissionGrant grant = grantsItr.next();
	    Collection<Permission> permCol = grant.getPermissions();
            Permission[] perms = permCol.toArray(new Permission [permCol.size()]);
	    checkNullElements(perms);
            Guard g = new GrantPermission(perms);
	    g.checkGuard(this);
        }
    }
    
    /**
     * Any grants must first be checked for PermissionGrants, checkCallerHasGrants has
     * been provided for this purpose, then prior to calling this method,
     * the PermissionGrant's must be added to the grantsCache.
     * 
     * processRemotePolicyGrants places the PermissionGrant's in the remotePolicyGrants array. It is
     * recommended that only this method be used to update the remotePolicyGrants
     * reference.
     * 
     * @param grants
     */
    private void processRemotePolicyGrants(PermissionGrant[] grants) {
	// This is slightly naughty calling a remotePolicyGrants method, however if it
	// changes between now and gaining the lock, only the length of the
	// HashSet is potentially not optimal, keeping the HashSet creation
	// outside of the lock reduces the lock held duration.
        Set<ProtectionDomain> domains = new HashSet<ProtectionDomain>();
        int l = grants.length;
        for (int i = 0; i < l; i++ ){
            if (grants[i] == null ) throw new NullPointerException("null PermissionGrant prohibited");
            // This causes a ProtectionDomain security check.
            final Class c = grants[i].getClass();
            List<ProtectionDomain> doms = AccessController.doPrivileged(
                new PrivilegedAction<List<ProtectionDomain>>() {
                    public List<ProtectionDomain> run() {
                        Class[] classes = c.getDeclaredClasses();
                        List<ProtectionDomain> domains = new ArrayList<ProtectionDomain>();
                        int l = classes.length;
                        for ( int i = 0; i < l; i++ ){
                            domains.add(classes[i].getProtectionDomain());
                        }
                        return domains;
                    }
                });
            domains.addAll(doms);
        }
        Iterator<ProtectionDomain> it = domains.iterator();
        while (it.hasNext()){
            if ( ! it.next().implies(implementsPermissionGrant)) {
                throw new SecurityException("Missing permission: " 
                        + implementsPermissionGrant.toString());
            }
        }
	HashSet<PermissionGrant> holder 
		    = new HashSet<PermissionGrant>(grants.length);
	    holder.addAll(Arrays.asList(grants));
            checkCallerHasGrants(holder);
        PermissionGrant[] old = null;
	synchronized (grantLock) {
            old = remotePolicyGrants;
	    PermissionGrant[] updated = new PermissionGrant[holder.size()];
	    remotePolicyGrants = holder.toArray(updated);
	}
        Collection<PermissionGrant> oldGrants = new HashSet<PermissionGrant>(old.length);
        oldGrants.addAll(Arrays.asList(old));
        oldGrants.removeAll(holder);
        // Collect removed Permission's to notify CachingSecurityManager.
        Set<Permission> removed = new HashSet<Permission>(120);
        Iterator<PermissionGrant> rgi = oldGrants.iterator();
        while (rgi.hasNext()){
            PermissionGrant g = rgi.next();
                    removed.addAll(g.getPermissions());
        }
        
        SecurityManager sm = System.getSecurityManager();
        if (sm instanceof CachingSecurityManager) {
            ((CachingSecurityManager) sm).clearCache();
        }
        // oldGrants now only has the grants which have been removed.
    }

}
