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
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;


/**
 * ConcurrentPermission's is a replacement for java.security.Permissions, 
 * it doesn't extend Permissions.
 * 
 * If there is heavy contention for one Permission class
 * type, concurrency may suffer due to internal synchronization.
 * This is due to the original PermissionsCollection spec requiring
 * that all implementations do their own synchronization, this is a design
 * mistake, similar to Vector. 
 * 
 * ConcurrentPermission's defined behaviour for #elements() differs from
 * PermissionCollection.  It is safe to alter ConcurrentPermissions while
 * Enumerating through it's elements.  ConcurrentPermission's keeps a cache
 * of elements, but makes no guarantees that new elements will be
 * added during an Enumeration.
 * 
 * TODO: Serialization properly
 * @version 0.4 2009/11/10
 * 
 * @author Peter Firmstone
 * @serial permsMap
 */
public final class ConcurrentPermissions extends PermissionCollection 
implements Serializable {

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
    private transient ConcurrentHashMap<Class<?>, PermissionCollection> permsMap;
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
        if (pc == null){                       
            pc = new MultiReadPermissionCollection(permission);   
            PermissionCollection existed = 
                    permsMap.putIfAbsent(permission.getClass(), pc);
            if (existed != null) {
                pc = existed;
            }
        } 
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
        unresolved.resolveCollection(permission, pc);
        return pc.implies(permission);
    }
    
    /**
     * This Enumeration is not intended for concurrent access,
     * PermissionCollection's underlying state is protected by defensive copying, 
     * performed by MultiReadPermissionCollection, which creates a shared cache
     * of the contained Permission's for each PermissionCollection, while holding a read lock
     * this prevents the client having to deal with a ConcurrentModificationException.
     * Thus allowing modification of the PermissionCollection while the
     * contents are being Enumerated.
     * 
     * Any number of these Enumerations may be utilised , each accessed by 
     * a separate thread.
     * 
     * This Enumeration may contain duplicates and it may contain UnresolvedPermission's
     * that have been resolved and are present in the returned Enumeration.
     * 
     * @return Enumeration<Permission>
     */
    @Override
    public Enumeration<Permission> elements() {
        ArrayList<PermissionCollection> elem = 
                new ArrayList<PermissionCollection>(permsMap.size() 
                                    + unresolved.awaitingResolution() + 2);
	// Unresolved Permission's are added first in case the are resolved in 
	// the interim, meaning that they may also be present in resolved form
	// also.  To do the reverse would risk some Permission's being absent.
        if (unresolved.awaitingResolution() > 0) {
            elem.add(unresolved);
        }
        elem.addAll(permsMap.values());	
        Iterator<PermissionCollection> perms = elem.iterator();
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
     * This Enumeration is not intended for concurrent access, underlying
     * PermissionCollection's need to be protected by MultiReadPermissionCollection's
     * cache, so updates wont affect the thread safety of ConcurrentPermission.
     * 
     * Any number of these Enumerations may be utilised , each accessed by 
     * a separate thread.
     * 
     * @author Peter Firmstone
     */   
    private final static class PermissionEnumerator implements Enumeration<Permission> {
        private final Iterator<PermissionCollection> epc;
        private volatile Enumeration<Permission> currentPermSet;

        PermissionEnumerator(Iterator<PermissionCollection> epc){
            this.epc = epc;
            currentPermSet = getNextPermSet();
        }

        private Enumeration<Permission> getNextPermSet(){
            if (epc.hasNext()){
                PermissionCollection pc = epc.next();               
                /* We only take what we need, as we need it, minimising memory use.
                 * Each underlying PermissionCollection adds its own Enumeration.
		 * MultiReadPermissionCollection caches the elements so we
		 * are protected from ConcurrentModificationException's
                 */
                if ( pc instanceof PermissionPendingResolutionCollection ){
		    Set<Permission> permissionSet = new HashSet<Permission>();
                    Enumeration<Permission> e = pc.elements();
                    while (e.hasMoreElements()) {
                        PermissionPendingResolution p = 
                                (PermissionPendingResolution) e.nextElement();
                        UnresolvedPermission up = p.asUnresolvedPermission();
                        permissionSet.add(up);
                    }
		    return Collections.enumeration(permissionSet);
                } else {
                    Enumeration<Permission> e = pc.elements();
                    return e;
                }
            } else {
		Vector<Permission> empty = new Vector<Permission>(0);
		return empty.elements();
	    }
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

}
