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

import org.apache.river.api.security.AbstractPolicy;
import org.apache.river.api.security.ScalableNestedPolicy;
import org.apache.river.api.security.ConcurrentPolicyFile;
import org.apache.river.api.security.CachingSecurityManager;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Guard;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Security;
import java.security.UnresolvedPermission;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.security.GrantPermission;
import org.apache.river.api.security.PermissionGrant;
import org.apache.river.api.security.PermissionGrantBuilder;
import org.apache.river.api.security.RemotePolicy;
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
 * can only augment policy files; a Policy can only relax security
 * by granting additional permissions, this implementation adds an experimental 
 * feature for revoking permissions, however there are some caveats:</p>
 * 
 * <p>A ProtectionDomain must
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
 * this policy ensures that no dynamically grantable permissions are 
 * returned via the method:</p>
 * <p>
 * getPermissions(Codesource source) as a precaution.
 * </p>
 * <p>This is different to the behaviour of the previous Jini 2.0
 * DynamicPolicyProvider implementation where dynamically granted Permissions
 * could escape into the ProtectionDomain's private PermissionCollection.
 * /p>
 * <p>
 * It is thus recommended that Static policy files only be used for setting
 * up your privileged code and use UmbrellaGrantPermission's and grant 
 * all other Permission's using dynamic grants. 
 *
 * </p><p>
 * The underlying policy provider may be set using the System property:
 * </p>,<p>
 * net.jini.security.policy.PolicyFileProvider.basePolicyClass = 
 * org.apache.river.security.concurrent.ConcurrentPolicyFile
 * </p>
 * @since 2.0
 * @see ProtectionDomain
 * @see Policy
 * @see ConcurrentPolicyFile
 * @see net.jini.security.policy.PolicyFileProvider
 * @see CachingSecurityManager
 * @see RemotePolicy
 */

public class DynamicPolicyProvider extends AbstractPolicy implements 
        RevocablePolicy, ScalableNestedPolicy {
    private static final String basePolicyClassProperty =
	"net.jini.security.policy.DynamicPolicyProvider.basePolicyClass";
    private static final String defaultBasePolicyClass =
            "org.apache.river.api.security.ConcurrentPolicyFile";
//	"net.jini.security.policy.PolicyFileProvider";
//    private static final ProtectionDomain sysDomain = 
//	AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>() {
//        
//	    public ProtectionDomain run() { return Object.class.getProtectionDomain(); }
//	});
    private static final String revocationSupported = 
            "net.jini.security.policy.DynamicPolicyProvider.revocation";
    private static final Logger logger = Logger.getLogger("net.jini.security.policy");
    
    private static final ProtectionDomain policyDomain = 
            AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>(){
            
            public ProtectionDomain run() {
                return DynamicPolicyProvider.class.getProtectionDomain();
            }
        });
   
    private final Policy basePolicy; // refresh protected by transactionWriteLock
    // DynamicPolicy grant's for Proxy's.
    private final Collection<PermissionGrant> dynamicPolicyGrants;
    private final boolean revocable;
    
    private final boolean loggable;
    // do something about some domain permissions for this domain so we can 
    // avoid dead locks due to bug 4911907
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
        dynamicPolicyGrants = Collections.newSetFromMap(new ConcurrentHashMap<PermissionGrant,Boolean>(64));
        loggable = logger.isLoggable(Level.FINEST);
        if (basePolicy instanceof DynamicPolicy) {
            if (basePolicy instanceof RevocablePolicy ) {
                RevocablePolicy rp = (RevocablePolicy) basePolicy;
                revocable = rp.revokeSupported();
            } else {
                revocable = false;
            }
        } else {
            revocable = revoke.equals(tRue);
        }
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
        if (basePolicy == null) throw new NullPointerException("null basePolicy prohibited");
        this.basePolicy = basePolicy;
        dynamicPolicyGrants = Collections.newSetFromMap(new ConcurrentHashMap<PermissionGrant,Boolean>(64));
        loggable = logger.isLoggable(Level.FINEST);
         if (basePolicy instanceof DynamicPolicy) {
            if (basePolicy instanceof RevocablePolicy ) {
                RevocablePolicy rp = (RevocablePolicy) basePolicy;
                revocable = rp.revokeSupported();
            } else {
                revocable = false;
            }
        } else {
            revocable = true;
        }
        policyPermissions = basePolicy.getPermissions(policyDomain);
        policyPermissions.setReadOnly();
    }

    /*
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
        return revocable;
    }

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
	/* It is extremely important that dynamic grant's are not returned,
	 * to prevent them becoming part of the static permissions held
	 * by a ProtectionDomain.  In this case during construction of a
	 * ProtectionDomain.  Static Permissions are irrevocable.
	 */ 
        return basePolicy.getPermissions(codesource);
    }
    
    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
//        if (domain == policyDomain) return policyPermissions;
	/* Note: we can return revokeable permissions, the  ProtectionDomain
         * only temporarily merges the permissions for toString(), for debugging.
	 */
        Collection<PermissionGrant> pgc = getPermissionGrants(domain);  
        NavigableSet<Permission> permissions = new TreeSet<Permission>(comparator);
        processGrants(pgc, null, true, permissions);
        PermissionCollection pc = convert(permissions, 32, 0.75F, 1, 8);
	expandUmbrella(pc);
        return pc;
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        if (domain == policyDomain) return policyPermissions.implies(permission);
        // There's not much point to this check anymore, besides, it could allow 
        // a less privileged domain in an underlying policy to be checked
        // prior to a privileged domain in this policy.
//        if (basePolicyIsDynamic || basePolicyIsRemote){
//            if (basePolicy.implies(domain, permission)) return true;
//        }
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
        NavigableSet<Permission> permissions = new TreeSet<Permission>(comparator); // Keep as small as possible.
        /* If GrantPermission is being requested, we must get all Permission objects
         * and add them to the underlying collection.
         * 
         */
        Class permClass = permission instanceof GrantPermission ? null : permission.getClass();
        if (!(basePolicy instanceof ScalableNestedPolicy)) {
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
            List<PermissionGrant> grants = ((ScalableNestedPolicy) basePolicy).getPermissionGrants(domain);
            if ( !grants.isEmpty() && grants.get(0).isPrivileged()) return true;
            processGrants(grants, permClass, false, permissions);
        }
        Iterator<PermissionGrant> grants = dynamicPolicyGrants.iterator();
        // Check the privileged case first.
        while (grants.hasNext()){
            PermissionGrant g = grants.next();
            if (g.isPrivileged() && g.implies(domain)) return true;
        }
        grants = dynamicPolicyGrants.iterator();
        while (grants.hasNext()){
            PermissionGrant g = grants.next();
            if (!g.isPrivileged() && g.implies(domain)){
                Collection<Permission> perms = g.getPermissions();
                // But we only want to add relevant Permissions.
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
        PermissionCollection pc = null;
        if (permClass != null){
            pc =convert(permissions, 4, 0.75F, 1, 2);
        } else {
            // GrantPermission
            pc = convert(permissions, 4, 0.75F, 1, 2);
            expandUmbrella(pc);
        }
        return pc.implies(permission);
    }
    
    /**
     * Calling refresh doesn't remove any dynamic grant's, it only clears
     * the cache and refreshes the underlying Policy, it also removes any
     * grants for ProtectionDomains that no longer exist.
     * 
     * If a CachingSecurityManager has been set, this method will clear its 
     * cache.
     * 
     */
    
    @SuppressWarnings("unchecked")
    public void refresh() {
        basePolicy.refresh();
        // Clean up any void dynamic grants.
        Collection<PermissionGrant> remove = new LinkedList<PermissionGrant>();
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
        Guard g = new GrantPermission(permissions);
        g.checkGuard(null);
        final PermissionGrantBuilder pgb = PermissionGrantBuilder.newBuilder();
        pgb.principals(principals)
            .permissions(permissions)
            .context(PermissionGrantBuilder.CLASSLOADER);
        if (cl != null){
            AccessController.doPrivileged(
                new PrivilegedAction(){

                    public Object run() {
                        pgb.clazz(cl);
                        return null;
                    }

                });
        }
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

    public List<PermissionGrant> getPermissionGrants(ProtectionDomain domain) {
        List<PermissionGrant> grants = null;
        if (basePolicy instanceof ScalableNestedPolicy){
            grants = ((ScalableNestedPolicy)basePolicy).getPermissionGrants(domain);
            if ( !grants.isEmpty() && grants.get(0).isPrivileged()) return grants;
        } else {
            grants = new LinkedList<PermissionGrant>();
            PermissionGrant pg = extractGrantFromPolicy(basePolicy, domain);
            grants.add(pg);
            if (pg.isPrivileged()) return grants;
        }
        Iterator<PermissionGrant> it = dynamicPolicyGrants.iterator();
        while (it.hasNext()){
            PermissionGrant pg = it.next();
            /* Check privileged first */
            if (pg.isPrivileged() && pg.implies(domain)){
                grants.clear();
                grants.add(pg);
                return grants;
            }
        }
        it = dynamicPolicyGrants.iterator();
        while (it.hasNext()){
            PermissionGrant pg = it.next();
            /* Then check less privileged domains*/
            if ( !pg.isPrivileged() && pg.implies(domain)){
                grants.add(pg);
            }
        }
        return grants;
    }

    public boolean grant(PermissionGrant p) {
        Collection<Permission> perms = p.getPermissions();
        GrantPermission guard = new GrantPermission(perms.toArray(new Permission [perms.size()]));
        guard.checkGuard(null);
        return dynamicPolicyGrants.add(p);
    }

}
