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

import java.security.AllPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.UnresolvedPermission;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * ConcurrentPermissions is a drop in replacement for java.security.Permissions
 * 
 * ConcurrentPermissions was originally intended to be used as a policy cache, it turns out
 * that a policy cache was not needed, due to the efficiency of package private
 * URIGrant.implies(ProtectionDomain pd).  Scalability is better without
 * a policy cache because PermissionGrant's are immutable, have no mutable shared 
 * state and are therefore not likely to cause cache misses.
 * 
 * The first reason this class exists is due to an unknown bug in
 * java.security.Permissions not resolving 
 * permission org.apache.river.phoenix.ExecOptionPermission "*";
 * in UnresolvedPermission. This occurs in start tests using Phoenix and
 * defaultphoenix.policy in the qa suite.  The second reason is performance
 * tuning for concurrency or to avoid unnecessary collection resizing, 
 * a method in AbstractPolicy is provided so external policy providers can 
 * take advantage, without this class being public.
 * 
 * This class may be removed in a future version of River.
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
 * @version 0.6 2018/05/15
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 * @serial permsMap
 */
final class ConcurrentPermissions extends PermissionCollection {

    /* unresolved is never returned or allowed to escape, it's elements() method
     * isn't used to return an Enumeration yet 
     * Duplicate Permissions could potentially be returned if unresolved is
     * enumerated first.
     * For a ProtectionDomain object, duplicates are dropped by 
     * java.security.AccessControlContext
     * This creates issues with java.security.AccessControlContext and
     * causes it to throw an exception.
     */ 
    private final PermissionPendingResolutionCollection unresolved;
    private final ConcurrentMap<Class<?>, PermissionCollection> permsMap;
    private volatile boolean allPermission;
    
    /* Let Permissions, UnresolvedPermission and 
     * UnresolvedPermissionCollection resolve all unresolved permission's
     * it saves reimplementing package private methods.  This is done by adding
     * a Permissions object instance to handle all UnresolvedPermissions.
     */    
    
    ConcurrentPermissions(){
        permsMap = new ConcurrentHashMap<Class<?>, PermissionCollection>();
        // Bite the bullet, get the pain out of the way in the beginning!
        unresolved = new PermissionPendingResolutionCollection();
        allPermission = false;      
    }
    
    ConcurrentPermissions(int initialCapacity, float loadFactor, int concurrencyLevel, int unresolvedClassCount){
        permsMap = new ConcurrentHashMap<Class<?>, PermissionCollection>
                (initialCapacity, loadFactor, concurrencyLevel);
        // Bite the bullet, get the pain out of the way in the beginning!
        unresolved = new PermissionPendingResolutionCollection(unresolvedClassCount, loadFactor, concurrencyLevel);
        allPermission = false;
    }
    
    /**
     * @Threadsafe
     * @param permission
     */   
    @Override
    public void add(Permission permission) {
        if (permission == null){return;}
        if (super.isReadOnly()) {
            throw new SecurityException("attempt to add a Permission to a readonly Permissions object");
        } 
        if (allPermission == true) return; // Why bother adding another permission?
        if (permission instanceof AllPermission) {
            allPermission = true;
            permsMap.clear();
            unresolved.clear();
        }
        if (permission instanceof UnresolvedPermission) {          
            unresolved.add(new PermissionPendingResolution((UnresolvedPermission)permission));            
        }
        // this get saves unnecessary object creation.
        Class clas = permission.getClass();
        PermissionCollection pc = permsMap.get(clas);
        if (pc == null){
            pc = getPC(permission);
            PermissionCollection existed = 
                    permsMap.putIfAbsent(clas, pc);
            if (existed != null) {
                pc = existed;
            }
        } 
	pc.add(permission);
    }
    
    private PermissionCollection getPC(Permission p){
        if (p == null) throw new NullPointerException("null Permission");
        PermissionCollection pc = p.newPermissionCollection();
        if (pc == null) pc = new PC();
        return pc;
    }
    
    /**
     * Returns true if Permission is implied for this PermissionDomain.
     * @Threadsafe this method is also a mutator method for internal state
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
        Class clas = permission.getClass();
        PermissionCollection pc = permsMap.get(clas); // To stop unnecessary object creation
        if (pc != null && pc.implies(permission)) { return true;}
        if (unresolved.awaitingResolution() == 0 ) { return false; }
        if (pc == null){
            pc = getPC(permission); // once added it cannot be removed atomically.
            PermissionCollection existed = permsMap.putIfAbsent(clas, pc);
            if (existed != null) pc = existed;
        }
        unresolved.resolveCollection(permission, pc);
        return pc.implies(permission);
    }
    
    /**
     * This Enumeration is not intended for concurrent access, modification
     * of the underlying PermissionCollection will not cause a 
     * ConcurrentModificationException, but modifications made after this call
     * returns may not be included in the Enumeration.
     * 
     * Any number of these Enumerations may be utilised , each accessed by 
     * a separate thread.
     * 
     * This Enumeration may contain duplicates and it may contain UnresolvedPermission's
     * as well as their resolved form.
     * 
     * @return Enumeration&lt;Permission&gt;
     */
    @Override
    public Enumeration<Permission> elements() {
        if (allPermission == true){
            Permission [] pa = new Permission[1];
            pa [0] = new AllPermission();
            return Collections.enumeration(Arrays.asList(pa));
        }
        ArrayList<PermissionCollection> elem = 
                new ArrayList<PermissionCollection>(permsMap.size() 
                                    + unresolved.awaitingResolution() + 2);
	// Unresolved Permission's are added first in case they are resolved in 
	// the interim, meaning that they may also be present in resolved form
	// also.  To do the reverse would risk some Permission's being absent.
        if (unresolved.awaitingResolution() > 0) {
            elem.add(unresolved);
        }
        elem.addAll(permsMap.values());	
        Iterator<PermissionCollection> perms = elem.iterator();
        return new PermissionEnumerator(perms);                 
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
        private final static Enumeration<Permission> empty = 
        new Enumeration<Permission>(){

            public boolean hasMoreElements() {
                return false;
            }

            public Permission nextElement() {
                throw new NoSuchElementException("Empty enumeration");
            }
            
        };
        private final Iterator<PermissionCollection> epc;
        private volatile Enumeration<Permission> currentPermSet;

        PermissionEnumerator(Iterator<PermissionCollection> epc){
            this.epc = epc;
            currentPermSet = getNextPermSet();
        }

        private Enumeration<Permission> getNextPermSet(){
            Enumeration<Permission> result = null;
            if (epc.hasNext()){
                Enumeration<Permission> e = null;
                PermissionCollection pc = epc.next();               
                /* We only take what we need, as we need it, minimising memory use.
                 * Each underlying PermissionCollection adds its own Enumeration.
                 */
                if ( pc instanceof PermissionPendingResolutionCollection ){
		    Set<Permission> permissionSet = new HashSet<Permission>();
                    e = pc.elements();
                    while (e.hasMoreElements()) {
                        PermissionPendingResolution p = 
                                (PermissionPendingResolution) e.nextElement();
                        UnresolvedPermission up = p.asUnresolvedPermission();
                        permissionSet.add(up);
                    }
		    e = Collections.enumeration(permissionSet);
                } else if (pc != null ) {
                    e = pc.elements();
                }
                if ( e == null ) e = empty;
                result = e;
            }
            return result; // If null end.
        }

        public boolean hasMoreElements() {
            boolean result = false;
            if (currentPermSet != null ) result = currentPermSet.hasMoreElements();
            while (result == false){
                Enumeration<Permission> next = getNextPermSet();
                if (next == null) return false;
                currentPermSet = next;
                result = currentPermSet.hasMoreElements();
            }
            return result;           
        }

        public Permission nextElement() {        
            return currentPermSet.nextElement();               
        }
    }
    
    private static class PC extends PermissionCollection {
        private static final long serialVersionUID = 1L;
        private final Collection<Permission> perms;
        
        private PC(){
            perms = new ConcurrentSkipListSet<Permission>(new PermissionComparator());
        }

        @Override
        public void add(Permission permission) {
            perms.add(permission);
        }

        @Override
        public boolean implies(Permission permission) {
            if (perms.contains(permission)) return true;
            Iterator<Permission> it = perms.iterator();
            while (it.hasNext()){
                Permission p = it.next();
                if (p.implies(permission)) return true;
            }
            return false;
        }

        @Override
        public Enumeration<Permission> elements() {
            return Collections.enumeration(perms);
        }
        
    }
    
    private static class PermissionPendingResolution extends Permission {
            private static final long serialVersionUID = 1L;
            private String name; //Target name of underlying permission
            private String actions;
            /* We have our own array copy of certs, prevents unnecessary 
             * array creation every time .getUnresolvedCerts() is called.
             */ 
            private Certificate [] targetCerts;
            private UnresolvedPermission unresolvedPermission;

        PermissionPendingResolution(UnresolvedPermission up){
            super(up.getUnresolvedType());
            name = up.getUnresolvedName();
            actions = up.getUnresolvedActions();
            // don't need to defensive copy, UnresolvedPermission already does it.
            targetCerts = up.getUnresolvedCerts();
            unresolvedPermission = up;
        }

        Permission resolve(Class targetType) {
            // check signers at first
            if (PolicyUtils.matchSubset( targetCerts, targetType.getSigners())) {
                try {
                     return PolicyUtils.instantiatePermission(targetType, name, actions);
                } catch (Exception ignore) {
                    //TODO log warning?
                }
            }
            return null;
        }

        @Override
        public boolean implies(Permission permission) {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if ( obj == this ) {return true;}
            if ( !(obj instanceof PermissionPendingResolution)) {return false;}
            PermissionPendingResolution ob = (PermissionPendingResolution) obj;
            if (this.unresolvedPermission.equals(ob.unresolvedPermission)) {return true;}
            return false;
        }

        @Override
        public int hashCode() {
            return unresolvedPermission.hashCode();
        }

        @Override
        public String getActions() {
            return "";
        }

        @Override
        public PermissionCollection newPermissionCollection(){
            return new PermissionPendingResolutionCollection();
        }

        public UnresolvedPermission asUnresolvedPermission(){
            return unresolvedPermission;
        }
    }
    
    private static class PermissionPendingResolutionCollection  extends PermissionCollection {
        private static final long serialVersionUID = 1L;
        private ConcurrentHashMap<String,Collection<PermissionPendingResolution>> klasses;
        // This is a best effort counter, it doesn't try to identify duplicates.
        // If it equals 0, it definitely has no pendings, however it may be greater
        // than 0 and have no pending Permission's for resolution.
        private AtomicInteger pending;
        PermissionPendingResolutionCollection(){
            klasses = new ConcurrentHashMap<String,Collection<PermissionPendingResolution>>(2);
            pending = new AtomicInteger(0);
        }
        
        PermissionPendingResolutionCollection(int initialCapacity, float loadFactor, int concurrencyLevel){
            klasses = new ConcurrentHashMap<String,Collection<PermissionPendingResolution>>
                    (initialCapacity, loadFactor, concurrencyLevel);
            pending = new AtomicInteger(0);
        }

        public int awaitingResolution(){
            return pending.get();
        }

        void clear(){
            klasses.clear();
            pending.set(0);
        }


        public void add(Permission permission) {
            if (isReadOnly()) {
                throw new SecurityException("attempt to add a Permission to a readonly Permissions object"); //$NON-NLS-1$
            }
            if (permission == null) { throw new IllegalArgumentException("Null Permission");}
            if ( permission.getClass() != PermissionPendingResolution.class || permission.getClass() != PermissionPendingResolution.class ) {
                throw new IllegalArgumentException("Not instance of PermissionPendingResolution");
            }
            String klass = permission.getName();
            Collection<PermissionPendingResolution> klassMates = klasses.get(klass);
            if (klassMates != null){
                klassMates.add((PermissionPendingResolution) permission);
                pending.incrementAndGet();
                return;
            }
            Collection<PermissionPendingResolution> klassMatesExists = null;        
            Set<PermissionPendingResolution> pprs = new HashSet<PermissionPendingResolution>();
            klassMates = Collections.synchronizedSet(pprs);
            klassMatesExists  = klasses.putIfAbsent(klass, klassMates);       
            if (klassMatesExists == null){
                klassMates.add((PermissionPendingResolution) permission);
                pending.incrementAndGet();
            }else{
                klassMatesExists.add((PermissionPendingResolution) permission);
                pending.incrementAndGet();
            }
        }

        PermissionCollection resolveCollection(Permission target, PermissionCollection holder ){
            if (target == null || holder == null) throw new NullPointerException("target or holder cannot be null");
            if (pending.get() == 0) { return holder; }
            String klass = target.getClass().getName();
            Collection<PermissionPendingResolution> klassMates = klasses.remove(klass);
            if (klassMates != null) {       
                for (Iterator<PermissionPendingResolution> iter = klassMates.iterator(); iter.hasNext();) {
                    PermissionPendingResolution element = iter.next();
                    Permission resolved = element.resolve(target.getClass());
                    if (resolved != null) {
                        holder.add(resolved);
                        iter.remove();
                        pending.decrementAndGet();
                    }
                } 
                // If for some reason something wasn't resolved we better put it back
                // We should never get here, should I throw an exception instead?
                if (klassMates.size() > 0 ) {
                    Collection<PermissionPendingResolution> existed
                            = klasses.putIfAbsent(klass, klassMates);
                    if ( existed != null ) {
                        existed.addAll(klassMates);
                    }
                }
            }
            return holder;
        }

        @Override
        public boolean implies(Permission permission) {
            return false;
        }

        @SuppressWarnings("unchecked")
        public Enumeration<Permission> elements() {
            Collection all = new ArrayList();
            for (Iterator iter = klasses.values().iterator(); iter.hasNext();) {
                all.addAll((Collection)iter.next());
            }
            return Collections.enumeration(all);
        }
    }

}
