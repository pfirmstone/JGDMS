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
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Guard;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.UnresolvedPermission;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.security.GrantPermission;
import org.apache.river.api.common.Beta;

/**
 * An implementation of RemotePolicy.
 * @author Peter Firmstone
 * @since 3.0.0
 */
@Beta
public class RemotePolicyProvider extends AbstractPolicy implements RemotePolicy,
        ScalableNestedPolicy{
    
    private static final Logger logger = Logger.getLogger("net.jini.security.policy");
    private static final ProtectionDomain policyDomain = 
            AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>(){
            
            public ProtectionDomain run() {
                return RemotePolicyProvider.class.getProtectionDomain();
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
    private final Permission remotePolicyPermission;
    private final Guard protectionDomainPermission;
    private final Policy basePolicy; // refresh protected by transactionWriteLock
    private final boolean basePolicyIsRemote;
    private final boolean basePolicyIsConcurrent;
    private final PermissionCollection policyPermissions;
    private final boolean loggable;
    
    /**
     * Creates a new <code>RemotePolicyProvider</code> instance that wraps
     * around the given non-<code>null</code> base policy object.
     *
     * @param   basePolicy base policy object containing information about
     *          non-dynamic grants
     * @throws  NullPointerException if <code>basePolicy</code> is
     * 		<code>null</code>
     */
    public RemotePolicyProvider(Policy basePolicy){
        this.basePolicy = basePolicy;
	remotePolicyGrants = new PermissionGrant[0];
        loggable = logger.isLoggable(Level.FINEST);
	grantLock = new Object();
        remotePolicyPermission = new PolicyPermission("Remote");
        protectionDomainPermission = new RuntimePermission("getProtectionDomain");
        basePolicyIsRemote = basePolicy instanceof RemotePolicy ?true: false;
        basePolicyIsConcurrent = basePolicy instanceof ScalableNestedPolicy ;
        policyPermissions = basePolicy.getPermissions(policyDomain);
        policyPermissions.setReadOnly();
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
        try {
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
        Set<ProtectionDomain> domains = new HashSet<ProtectionDomain>(32);
        for (int i = 0, l = grants.length; i < l; i++ ){
            if (grants[i] == null ) throw new NullPointerException("null PermissionGrant prohibited");
            // This causes a ProtectionDomain security check.
            final Class c = grants[i].getClass();
            domains.addAll( AccessController.doPrivileged(
                new PrivilegedAction<Set<ProtectionDomain>>() {
                    public Set<ProtectionDomain> run() {
                        Class[] classes = c.getDeclaredClasses();
                        Set<ProtectionDomain> domains = new HashSet<ProtectionDomain>();
                        int l = classes.length;
                        for ( int i = 0; i < l; i++ ){
                            domains.add(classes[i].getProtectionDomain());
                        }
                        return domains;
                    }
                }));
        }
        Iterator<ProtectionDomain> it = domains.iterator();
        while (it.hasNext()){
            if ( ! it.next().implies(remotePolicyPermission)) {
                throw new SecurityException("Missing permission: " 
                        + remotePolicyPermission.toString());
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
    
    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
        return basePolicy.getPermissions(codesource);
    }
    
    public PermissionCollection getPermissions(ProtectionDomain domain){
        Collection<PermissionGrant> grants = getPermissionGrants(domain);
        NavigableSet<Permission> perms = new TreeSet<Permission>(comparator);
        processGrants(grants, null, true, perms);
        return convert(perms, perms.size(), 0.75F, 1, 16);
    }
    
    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        if (domain == policyDomain) return policyPermissions.implies(permission);
        if (basePolicyIsRemote){
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
        NavigableSet<Permission> permissions = new TreeSet<Permission>(comparator); // Keep as small as possible.
        /* If GrantPermission is being requested, we must get all Permission objects
         * and add them to the underlying collection.
         * 
         */
        Class permClass = permission instanceof GrantPermission ? null : permission.getClass();
        if (!basePolicyIsConcurrent) {
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
            Collection<PermissionGrant> grants = ((ScalableNestedPolicy) basePolicy).getPermissionGrants(domain);
            processGrants(grants, permClass, true, permissions);
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

    @Override
    public List<PermissionGrant> getPermissionGrants(ProtectionDomain domain) {
        List<PermissionGrant> grants = null;
        if (basePolicy instanceof ScalableNestedPolicy){
            grants = ((ScalableNestedPolicy) basePolicy).getPermissionGrants(domain);
        } else {
            grants = new LinkedList<PermissionGrant>();
            grants.add(extractGrantFromPolicy(basePolicy, domain));
        }
        PermissionGrant[] rpg = remotePolicyGrants; // Copy volatile reference.
        grants.addAll(Arrays.asList(rpg));
        return grants;
    }

//    @Override
//    public Collection<PermissionGrant> getPermissionGrants(boolean recursive) throws UnsupportedOperationException {
//        Collection<PermissionGrant> grants = null;
//        if ( recursive ){ 
//            if (!(basePolicy instanceof ScalableNestedPolicy)){
//                throw new UnsupportedOperationException
//                        ("base policy doesn't implement ScalableNestedPolicy");
//            }
//            grants = ((ScalableNestedPolicy)basePolicy).getPermissionGrants(recursive);
//        } else {
//            grants = new LinkedList<PermissionGrant>();
//        }
//        PermissionGrant[] rpg = remotePolicyGrants; // Copy volatile reference.
//        grants.addAll(Arrays.asList(rpg));
//        return grants;
//    }
}
