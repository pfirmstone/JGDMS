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

package org.apache.river.imp.security.policy.se;

import java.io.Serializable;
import java.security.AllPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Example Implementation alternative of Permissions implemented for concurrency, 
 * note this couldn't extend Permissions as it is declared final.
 * 
 * Note that if there is heavy contention for one Permission class
 * type, due to synchronization, concurrency will
 * suffer.  This is due to the original PermissionsCollection spec requiring
 * that all implementations do their own synchronization, this is a design
 * mistake, similar to Vector. 
 * 
 * This is an example class without optimisation, it will be slower
 * for single threaded applications and consume more memory.
 * 
 * It would also be possible to create an unlock method that permitted
 * adding or revoking permissions to this collection while it is referenced
 * from within a PermissionDomain, however that would break the contract of
 * setReadOnly().  This might make sense if implementing a SecurityManager or
 * it might not, it's just an observation that Permissions defined in policy
 * files, which are not dynamically granted, are not revokeable as a
 * Policy only augments the PermissionCollection associated with (referenced by)
 * a PermissionDomain.  However it would probably be best to extend
 * ProtectionDomain to alter this behaviour as it merges PermissionCollection's,
 * so you would end up with the Permissions implementation again, unless using the
 * constructor that sets the permissions as static, which is totally
 * contradictory.  So the best way to make a Permission revokeable is to 
 * grant it dynamically.
 * 
 * TODO: Serialization properly
 * @version 0.4 2009/11/10
 * 
 * @author Peter Firmstone
 * @serial permsMap
 */
public final class ConcurrentPermissions extends PermissionCollection 
implements Serializable, RevokeablePermissionCollection  {

    private static final long serialVersionUID=1L;
    /* unresolved is never returned or allowed to escape, it's elements() method
     * isn't used to return an Enumeration yet 
     * Duplicate Permissions could potentially be returned if unresolved is
     * enumerated first.
     * For a ProtectionDomain object, duplicates are dropped by 
     * java.security.AccessControlContext
     * This creates issues with java.security.AccessControlContext and
     * causes it to throw an exception.
     */ 
    private transient final PermissionPendingResolutionCollection unresolved;
    private ConcurrentHashMap<Class<?>, PermissionCollection> permsMap;
    private transient volatile boolean allPermission;
    
    /* Let Permissions, UnresolvedPermission and 
     * UnresolvedPermissionCollection resolve all unresolved permission's
     * it saves reimplementing package private methods.  This is done by adding
     * a Permissions object instance to handle all UnresolvedPermissions.
     */    
    
    public ConcurrentPermissions(){
        permsMap = new ConcurrentHashMap<Class<?>, PermissionCollection>();
        // Bite the bullet, get the pain out of the way in the beginning!
        unresolved = new PermissionPendingResolutionCollection();
        allPermission = false;      
    }
    
    /**
     * Threadsafe
     * @param permission
     */   
    @Override
    public void add(Permission permission) {
        if (permission == null){return;}
        if (super.isReadOnly()) {
            throw new SecurityException("attempt to add a Permission to a readonly Permissions object");
        } 
        if (permission instanceof AllPermission) {allPermission = true;}
        if (permission instanceof UnresolvedPermission) {          
            unresolved.add(new PermissionPendingResolution((UnresolvedPermission)permission));            
        }
        // this get saves unnecessary object creation.
        PermissionCollection pc = permsMap.get(permission.getClass());
        if (pc != null){                       
            pc.add(permission);
            return;             
        }
        // A null instance occurs when a PermissionCollection was absent.
        pc = permsMap.putIfAbsent(permission.getClass(), new MultiReadPermissionCollection(permission));                                  
        // If This is the first time the PermissionCollection was added
        // we must still add the permission, see Add the permission below.
        // The putIfAbsent will succeed, and as a result pc is null, provided another
        // thread doesn't add the permission between this put and the last get.
        // Just in case, we check the return value of the putIfAbsent, if
        // pc != null then another thread has alreaddy added a PermissionCollection
        if (pc != null){            
            pc.add(permission);
            return;
        }
        // Add the permission if it missed out due to unlucky timing.
        pc = permsMap.get(permission.getClass());
        pc.add(permission);        
    }    
    
    /**
     * Returns true if Permission is implied for this PermissionDomain.
     * Threadsafe this method is also a mutator method for internal state
     * 
     * @see Permission
     * @param permission
     * @return boolean
     */
    @Override
    public boolean implies(Permission permission) {
        if (permission == null){return false;}
        if (allPermission == true){return true;}
        if (permission instanceof UnresolvedPermission){return false;}        
        PermissionCollection pc = permsMap.get(permission.getClass()); // To stop unnecessary object creation
        if (pc != null && pc.implies(permission)) { return true;}
        if (unresolved.awaitingResolution() == 0 ) { return false; }
        PermissionCollection existed = null;
        if (pc == null){
            pc = new MultiReadPermissionCollection(permission); // once added it cannot be removed atomically.
            existed = permsMap.putIfAbsent(permission.getClass(), pc);
            if (existed != null) {
                pc = existed;
                }
        }
        unresolved.resolveCollection(permission, pc); // drop the returned collection its the same as the one passed in.
        return pc.implies(permission);
    }
    
    /**
     * This Enumeration is not intended for concurrent access,
     * PermissionCollection's underlying state is protected by defensive copying, 
     * it wont affect the thread safety of ConcurrentPermission.
     * 
     * Any number of these Enumerations may be utilised , each accessed by 
     * a separate thread.
     * 
     * This Enumeration may contain duplicates
     * 
     * @return Enumeration<Permission>
     */
    @Override
    public Enumeration<Permission> elements() {
        Enumeration<PermissionCollection> perms = permsMap.elements();
        return new PermissionEnumerator(perms);                 
    }
    
    /**
     * Attempt to resolve any unresolved permissions whose class is visible
     * from within this protection domain.
     * @param pd 
     */
    public void resolve(ProtectionDomain pd){
        if (unresolved.awaitingResolution() == 0){return;}
        Enumeration<Permission> perms = unresolved.resolvePermissions(pd);
        while (perms.hasMoreElements()){
            add(perms.nextElement());
        }
    }
    
    /*
     * This Enumeration is not intended for concurrent access,
     * PermissionCollection's underlying state is protected by defensive copying, 
     * it wont affect the thread safety of ConcurrentPermission.
     * 
     * This enumeration doesn't include unresolved permissions is that a problem?
     * 
     * Any number of these Enumerations may be utilised , each accessed by 
     * a separate thread.
     * 
     * @author Peter Firmstone
     */
    private final static class PermissionEnumerator implements Enumeration<Permission> {
        private final Enumeration<PermissionCollection> epc;
        private volatile Enumeration<Permission> currentPermSet;

        PermissionEnumerator(Enumeration<PermissionCollection> epc){
            this.epc = epc;
            currentPermSet = getNextPermSet();
        }

        private Enumeration<Permission> getNextPermSet(){
            Set<Permission> permissionSet = new HashSet<Permission>();
            if (epc.hasMoreElements()){
                PermissionCollection pc = epc.nextElement();               
                /* Local copy of the set containing a snapshot of 
                 * references to Permission objects present at an instant in time,
                 * we can Enumerate over, without contention or exception.  
                 * We only take what we need as we need it, minimising memory.
                 * Each object gets its own Enumeration.
                 */
                if ( pc instanceof Permissions){
                    synchronized (pc){
                        Enumeration<Permission> e = pc.elements();
                        while (e.hasMoreElements()) {
                            permissionSet.add(e.nextElement());
                        }
                    }
                } else {
                    Enumeration<Permission> e = pc.elements();
                    while (e.hasMoreElements()) {
                        permissionSet.add(e.nextElement());
                    }
                }
            }
            return Collections.enumeration(permissionSet);
        }

        public boolean hasMoreElements() {        
            if (currentPermSet.hasMoreElements()){return true;}          
            currentPermSet = getNextPermSet();
            return currentPermSet.hasMoreElements();           
        }

        public Permission nextElement() {
            if (hasMoreElements()){              
                return currentPermSet.nextElement();               
            } else {
                throw new NoSuchElementException("PermissionEnumerator");
            }
        }
    }

    public int revoke(Permission ... permissions) {
        
        // build a hashmap to put all permissions into, grouped by class.
        HashMap<Class<?>, Integer> results = new HashMap<Class<?>, Integer>();
        HashMap<Class<?>, ArrayList<Permission>> groups = new HashMap<Class<?>, ArrayList<Permission>>();
        int size = permissions.length;
        for (int i = 0; i < size; i++ ){
            Permission permission = permissions[i];
            //Make sure that any unresolved permissions have been resolved first
            //Don't bother revoking if it doesn't imply
            if ( !implies(permission)){
                continue;
            }
            ArrayList<Permission> permissionClassGroup = groups.get(permission.getClass());
            if (permissionClassGroup == null){
                permissionClassGroup = new ArrayList<Permission>();
                groups.put(permissions[i].getClass(), permissionClassGroup);
            }
            permissionClassGroup.add(permission); // still holds a reference
        }
        // process each group by class, if all succeed return 1, if one or more has
        // an issue with an intervening write return 0.
        // If any fail, return -1
        Set<Class<?>> classes = groups.keySet();
        Iterator<Class<?>> itr = classes.iterator();
        while (itr.hasNext()){
            Class<?> permissionClass = itr.next();
            RevokeablePermissionCollection current = 
                    (RevokeablePermissionCollection) permsMap.get(permissionClass);
            int result = 1; // If it doesn't exist then it must succeed.
            if (current != null){
                ArrayList<Permission> permissionGroupToRevoke = groups.get(permissionClass);
                Permission permissionList[] = new Permission[permissionGroupToRevoke.size()];
                permissionGroupToRevoke.toArray(permissionList);
                result = current.revoke( permissionList );
            }
            results.put(permissionClass, result);
        }
        // Now determine the results and return
        int result = 1;
        Iterator<Integer> itresult = results.values().iterator();
        while (itresult.hasNext()){
            int thisResult = itresult.next();
            if (thisResult < result) { result = thisResult; }
        }
        return result;
    }
    
    
    public void revokeAll(Permission permission) {
        PermissionCollection pc = permsMap.get(permission.getClass());
        if ( pc == null ){
            pc = new MultiReadPermissionCollection(permission);
            PermissionCollection exists = permsMap.putIfAbsent(permission.getClass(),pc);
            if ( exists != null ){
                pc = exists;
            }
        unresolved.resolveCollection(permission, pc);          
        }
        // Any permissions added by other threads in the interim will be
        // revoked when this method completes.
        // We won't avoid permission loss by only emptying the collection either,
        // however since
        // the intent will be probably to temporarily revoke a permission
        // when a proxy is required to be refreshed, it should be more efficient.
        //permsMap.remove(permission.getClass(), pc);
        RevokeablePermissionCollection rpc = (RevokeablePermissionCollection) pc;
        rpc.revokeAll(permission);
    }

}
