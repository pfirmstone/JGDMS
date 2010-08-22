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

import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Permission;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.river.api.security.ExecutionContextManager;
import org.apache.river.imp.util.ConcurrentSoftMap;

/**
 * Only a Single instance of ECM is required per policy, it is threadsafe.
 * Threads will wait until revoke is complete.
 * 
 * Implementers Note:  It hasn't been determined if the cache should be
 * Map<AccessControlContext,Set<Permission>> or the opposite as implemented,
 * in any case the emphasis needs to be placed on the permission check
 * since these are called the most.  There will be less exposure to hashCode
 * and equals() implementation issues if the current cache structure is reversed.
 * However the performance of AccessControlContext.hashCode needs to be 
 * determined first.
 * 
 * @author Peter Firmstone
 */
class ECM implements ExecutionContextManager{
    
    private final ConcurrentMap<Permission,Set<AccessControlContext>> checkedCache;
    private final ReadWriteLock revokeLock;
    private final Lock rl; // This lock is held briefly by callers of begin and end.
    private final Lock wl; // This lock is held by revocation.
    
    ECM(){
	/* The clients control garbage collection, the Permission objects
	 * are those passed by clients.*/
	checkedCache = new ConcurrentSoftMap<Permission, Set<AccessControlContext>>(300);
	/* This lock guards revocation */ 
	revokeLock = new ReentrantReadWriteLock();
	rl = revokeLock.readLock();
	wl = revokeLock.writeLock();
    }
    
    void revoke(Set<Permission> perms) throws InterruptedException, ExecutionException{
	wl.lock();
	try {
	    // Identify Permission's with matching class files to those revoked.
	    Set<Class> permClasses = new HashSet<Class>();
	    Iterator<Permission> itp = perms.iterator();
	    while (itp.hasNext()){
		permClasses.add(itp.next().getClass());
	    }
	    // Remove Permission's and AccessControlContexts from the checked cache.
	    Iterator<Permission> keysIt = checkedCache.keySet().iterator();
	    while (keysIt.hasNext()){
		Permission p = keysIt.next();
		if (permClasses.contains(p.getClass())){
		    Set<AccessControlContext> a = checkedCache.get(p);
		    keysIt.remove();
		}		
	    }
	    /* We're done, go home & rest */
	} finally {
	    wl.unlock();
	}
    }

    public void checkPermission(Collection<Permission> perms) throws AccessControlException {
	if (perms == null ) throw new NullPointerException("Permission Collection null");
	if (perms.isEmpty()) return; // Should we do this or is it a bug?
	//Thread currentThread = Thread.currentThread();
	AccessControlContext executionContext = AccessController.getContext();
	HashSet<Permission> permissions = new HashSet<Permission>(perms.size());
	permissions.addAll(perms);
	rl.lock();
	try {
	    // checkedCache - the permission check, fast for repeated calls.
	    Iterator<Permission> permiter = permissions.iterator();
	    while (permiter.hasNext()){
		Permission p = permiter.next();
		Set<AccessControlContext> checked = checkedCache.get(p);
		if (checked == null ){
		    checked = Collections.synchronizedSet(new HashSet<AccessControlContext>());
		    Set<AccessControlContext> existed = checkedCache.putIfAbsent(p, checked);
		    if (existed != null){
			checked = existed;
		    }
		}
		if ( checked.contains(executionContext)) continue; // it's passed before.
		executionContext.checkPermission(p); // Throws AccessControlException
		// If we get here cache the AccessControlContext.
		checked.add(executionContext);
	    }
	} finally {
	    rl.unlock();
	}
    }
}
