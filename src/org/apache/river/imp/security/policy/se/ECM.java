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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.river.api.security.ExecutionContextManager;
import org.apache.river.api.security.Reaper;
import org.apache.river.imp.util.ConcurrentWeakIdentityMap;

/**
 * Only a Single instance of ECM is required per policy, it is threadsafe.
 * Threads will wait until revoke is complete.
 * 
 * @author Peter Firmstone
 */
class ECM implements ExecutionContextManager{
    
    private final ConcurrentMap<Permission,Set<AccessControlContext>> checkedCache;
    private final ConcurrentMap<AccessControlContext, Set<Thread>> executionCache;
    private final ConcurrentMap<Thread, Set<AccessControlContext>> threadAssociation;
    private final ConcurrentMap<Thread, Reaper> association;
    private final ExecutorService executor;
    private final ReadWriteLock blockLock;
    private final Lock rl; // This lock is held briefly by callers of begin and end.
    private final Lock wl; // This lock is held by revocation.
    
    ECM(){
	/* The clients control garbage collection, the Permission objects
	 * are those passed by clients.*/
	checkedCache = new ConcurrentWeakIdentityMap<Permission, Set<AccessControlContext>>();
	/* The thread association controls the removal of AccessContolContext
	 * keys from the executionCache, since the threadAssociation holds a 
	 * strong reference from the thread, which is removed when end()
	 * is executed.
	 */ 
	executionCache = new ConcurrentWeakIdentityMap<AccessControlContext, Set<Thread>>();
	/* Thread association is utilised to track a thread as it enters and
	 * leaves the ExecutionContextManager try finally block.
	 */ 
	threadAssociation = new ConcurrentHashMap<Thread, Set<AccessControlContext>>();
	/* The association is only made while threads are within the clients
	 * try finally block.
	 */ 
	association = new ConcurrentHashMap<Thread, Reaper>();
	// TODO: Analyse needs, enable client configuration of thread pool.
	executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()+1);
	/* This lock guards revocation */ 
	blockLock = new ReentrantReadWriteLock();
	rl = blockLock.readLock();
	wl = blockLock.writeLock();
    }
    
    void revoke(Set<Permission> perms) throws InterruptedException, ExecutionException{
	wl.lock();
	try {
	    /* This is where we determine what needs to be revoked, we first
	     * get all the AccessControlContexts for the Permission class,
	     * these are removed from the checkedCache, then we narrow
	     * down the AccessControlContext's to only those in the
	     * execution cache, each AccessControlContext in the execution cache
	     * has checkPermission(Permission) called for each permission
	     * with a Class identical to one of the revoked permission's.  
	     * Any AccessControlContext's that throw AccessControlException will be
	     * caught and have a Reaper run, when it exists, for all Threads referenced
	     * from that AccessControlContext.
	     * 
	     * The RevokeableDynamicPolicy will have updated the current
	     * Permission's after revocation, so the ProtectionDomain's in
	     * the AccessControlContext will now throw an AccessControlException
	     * if the revocation applied to them.
	     * 
	     * The wl lock will be released, any threads that were interrupted
	     * will exit through the finally block and remove themselves
	     * from the execution cache.  Those that aren't may throw
	     * an exception and bubble up the stack, due to closing sockets
	     * etc.
	     * 
	     * The execution cache is comprised of the following fields:
	     * executionCache
	     * threadAssociation
	     */ 
	    // Identify Permission's with matching class files to those revoked.
	    Set<Class> permClasses = new HashSet<Class>();
	    Iterator<Permission> itp = perms.iterator();
	    while (itp.hasNext()){
		permClasses.add(itp.next().getClass());
	    }
	    // Remove Permission's and AccessControlContexts from the checked cache.
	    Map<Permission, Set<AccessControlContext>> removed = 
		    new HashMap<Permission, Set<AccessControlContext>>();
	    Iterator<Permission> keysIt = checkedCache.keySet().iterator();
	    while (keysIt.hasNext()){
		Permission p = keysIt.next();
		if (permClasses.contains(p.getClass())){
		    Set<AccessControlContext> a = checkedCache.get(p);
		    keysIt.remove();
		    removed.put(p, a);
		}		
	    }
	    // Match the AccessControlContexts with the execution cache;
	    Set<AccessControlContext> exCache = executionCache.keySet();
	    // Get the AccessControlContext's in the execution cache that fail.
	    Set<AccessControlContext> accFails = new HashSet<AccessControlContext>();
	    Iterator<Permission> retests = removed.keySet().iterator();
	    while (retests.hasNext()){
		Permission p = retests.next();
		Set<AccessControlContext> rechecks = removed.get(p);
		Iterator<AccessControlContext> recheck = rechecks.iterator();
		while (recheck.hasNext()){
		    AccessControlContext a = recheck.next();
		    if (accFails.contains(a)) continue;
		    // This really narrows down the checks.
		    if (exCache.contains(a)){
			try { 
			    a.checkPermission(p);
			} catch (AccessControlException e){
			    accFails.add(a);
			}
		    }
		}
	    }
	    // Identify the threads and prepare reapers.
	    Set<Runnable> reapers = new HashSet<Runnable>();
	    Iterator<AccessControlContext> failedAcc = accFails.iterator();
	    while (failedAcc.hasNext()){
		AccessControlContext fail = failedAcc.next();
		Set<Thread> threads = executionCache.get(fail);
		Iterator<Thread> i = threads.iterator();
		while (i.hasNext()) {
		    Thread t = i.next();
		    Reaper r = association.get(t);
		    if ( r == null ) continue;
		    r.put(t);
		    reapers.add(r);
		}		
	    }
	    /* Process the reapers, this requires a thread pool, but we don't
	     * want to return until all reapers have completed.
	     */
	    Iterator<Runnable> reaper = reapers.iterator();
	    List<Future> results = new ArrayList<Future>(reapers.size());
	    while ( reaper.hasNext()) {
		results.add(executor.submit(reaper.next()));
	    }
	    Iterator<Future> result = results.iterator();
	    while (result.hasNext()){
		// Waits for result.
		result.next().get();
	    }
	    /* We're done, go home & rest */
	} finally {
	    wl.unlock();
	}
    }

    public void begin(Reaper r) {
	Thread currentThread = Thread.currentThread();
	if ( r == null ) return;
	association.put(currentThread, r);	
    }

    public void checkPermission(Permission p) throws AccessControlException {
	if (p == null ) throw new NullPointerException("Permission null");
	Thread currentThread = Thread.currentThread();
	AccessControlContext executionContext = AccessController.getContext();
	rl.lock();
	try {
	    // execution cache, fast for repeated calls.
	    Set<Thread> exCacheThreadSet = executionCache.get(executionContext);
	    if ( exCacheThreadSet == null ){
		exCacheThreadSet = Collections.synchronizedSet(new HashSet<Thread>());
		Set<Thread> existed = executionCache.putIfAbsent(executionContext, exCacheThreadSet);
		if (existed != null){
		    exCacheThreadSet = existed;
		}
	    }
	    exCacheThreadSet.add(currentThread);// end execution cache.
	    // thread association, fast for repeated calls.
	    Set<AccessControlContext> thAssocSet = threadAssociation.get(currentThread);
	    if ( thAssocSet == null ){
		thAssocSet = Collections.synchronizedSet(new HashSet<AccessControlContext>());
		Set<AccessControlContext> existed = threadAssociation.putIfAbsent(currentThread, thAssocSet);
		if (existed != null){
		    thAssocSet = existed;
		}
	    }
	    thAssocSet.add(executionContext); // end thread association.
	    // checkedCache - the permission check, fast for repeated calls.
	    Set<AccessControlContext> checked = checkedCache.get(p);
	    if (checked == null ){
		checked = Collections.synchronizedSet(new HashSet<AccessControlContext>());
		Set<AccessControlContext> existed = checkedCache.putIfAbsent(p, checked);
		if (existed != null){
		    checked = existed;
		}
	    }
	    if ( checked.contains(executionContext)) return; // it's passed before.
	    executionContext.checkPermission(p); // Throws AccessControlException
	    // If we get here cache the AccessControlContext.
	    checked.add(executionContext); // end checkedCache.	    
	} finally {
	    rl.unlock();
	}
    }

    public void end() {
	// Removal from execution cache.
	Thread t = Thread.currentThread();
	rl.lock();
	try {
	    association.remove(t);
	    Set<AccessControlContext> accSet = threadAssociation.remove(t);
	    Iterator<AccessControlContext> it = accSet.iterator();
	    while (it.hasNext()){
		AccessControlContext acc = it.next();
		executionCache.get(acc).remove(t);
	    }
	}finally {
	    rl.unlock();
	}	
    }
}
