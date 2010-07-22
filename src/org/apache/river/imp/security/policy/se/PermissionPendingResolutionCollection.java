/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.imp.security.policy.se;

import java.security.AccessController;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Peter Firmstone
 */
public class PermissionPendingResolutionCollection  extends PermissionCollection {
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
    
    public int awaitingResolution(){
        return pending.get();
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
    
    PermissionCollection resolveCollection(Permission target,
                                           PermissionCollection holder ){
        if (pending.get() == 0) { return holder; }
        String klass = target.getClass().getName();
        Collection<PermissionPendingResolution> klassMates = klasses.remove(klass);
        if (klassMates != null) {       
            for (Iterator<PermissionPendingResolution> iter = klassMates.iterator(); iter.hasNext();) {
                PermissionPendingResolution element = iter.next();
                Permission resolved = element.resolve(target.getClass());
                if (resolved != null) {
                    if (holder == null) {
                        holder = new MultiReadPermissionCollection(target);                             
                    }
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
    
    //Should I be performing a privileged action? Or should it run with
    // the caller thread's privileges?
    Enumeration<Permission> resolvePermissions(final ProtectionDomain pd){
        @SuppressWarnings("unchecked")
        ClassLoader cl = (ClassLoader) AccessController.doPrivileged(
                new PrivilegedAction(){
                public Object run(){
                    ClassLoader cL = pd.getClassLoader();
                    if (cL == null){
                        cL = this.getClass().getClassLoader();
                    }
                    return cL;
                }
        });
        
        
        List<Permission> perms = new ArrayList<Permission>();
        Enumeration enPending = elements();
        while (enPending.hasMoreElements()){
            PermissionPendingResolution pendPerm = 
                    (PermissionPendingResolution) enPending.nextElement();
            Permission resolved =  pendPerm.resolve(cl);
            if ( resolved != null ){
                perms.add(resolved);
            }           
        }
        return Collections.enumeration(perms);
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
