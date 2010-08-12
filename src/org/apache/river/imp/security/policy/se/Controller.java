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

import java.lang.ref.WeakReference;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Permission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.apache.river.api.security.ExecutionContextManager;
import org.apache.river.imp.util.ConcurrentWeakIdentityMap;

/**
 * An implementation of ExecutionContextManager.
 * 
 * @author Peter Firmstone.
 */
class Controller implements ExecutionContextManager {
    /* The class manages revoke for all it's objects. */
    private static final ConcurrentMap<Permission, ExecutionContextManager> pool 
	    = new ConcurrentWeakIdentityMap<Permission, ExecutionContextManager>();
    
    private static final List<WeakReference<Controller>> cont =
	    new ArrayList<WeakReference<Controller>>(80);
    /**
     * This method is called by the RevokeableDynamicPolicy to get a List 
     * of Runnable clean up jobs that must be performed to complete the
     * revocation of Permissions.
     * 
     * A Thread pool can be used to perform the work, however the policy's
     * revoke call must not return until after all the jobs are complete.
     * 
     * @param classes - A set of Permission Class object's for the Permission's
     * being revoked.
     * @return A List of Runnable clean up tasks to be performed to complete
     * the revocation.
     */
    static List<Runnable> revoke(Set<Class> classes){
	List<Runnable> cleanupJobs;
	synchronized (cont){
	    cleanupJobs = new ArrayList<Runnable>(cont.size());
	    Iterator<WeakReference<Controller>> it =
		    cont.iterator();
	    while (it.hasNext()) {
		WeakReference<Controller> wf = it.next();
		Controller ecm = wf.get();
		if ( ecm != null ){
		    if (classes.contains(ecm.getPermission().getClass())){
			ecm.clear();
			cleanupJobs.addAll(ecm.getRunnable());
		    }
		} else {
		    it.remove();
		}
	    }
	}
	return cleanupJobs;	
    }
    
    static ExecutionContextManager getECManager(Permission p){
	ExecutionContextManager exm = pool.get(p);
	if ( exm != null ){
	    return exm;
	} 
	exm = new Controller(p);
	ExecutionContextManager existed = pool.putIfAbsent(p, exm);
	if ( existed != null){
	    exm = existed;
	}
	return exm;	
    }
    
    /* Object state and methods */
    private final Permission perm;
    private final List<WeakReference<Runnable>> cleanup;
    private final Set<AccessControlContext> checkedExecutionContext;
    
    /* A Revocation in progress will delay construction of a new Controller */
    private Controller(Permission p){
	perm = p;
	WeakReference<Controller> mac = 
		new WeakReference<Controller>(this);
	cleanup = new ArrayList<WeakReference<Runnable>>();
	checkedExecutionContext = new HashSet<AccessControlContext>(80);
	// this must be done last.
	synchronized (cont){
	    cont.add(mac);
	}
    } 

    public void checkPermission() throws AccessControlException {
	AccessControlContext executionContext = AccessController.getContext();
	synchronized (checkedExecutionContext){
	    if (checkedExecutionContext.contains(executionContext)) {
		return;
	    }
	}
	executionContext.checkPermission(perm);
	/* If we get to here without exception we can add it.
	 * The RevokeablyDynamicPolicy will revoke the Permission prior to
	 * clearing the current checked execution context as part of the revoke
	 * process, this means
	 * that while the revocation isn't yet complete, the checked context
	 * will still return true, but the actual permission check would return
	 * false, however at the completion of the revocation process, the
	 * Runnable provided will be executed to perform any necessary cleanupJobs
	 * to fully revoke.
	 * 
	 * This prevents invalid execution context's from finding their way
	 * into the checked Execution Context which would be a security breach.
	 * 
	 * Revoke's happen synchronuously to ensure that this contract is honored.
	 */ 
	synchronized (checkedExecutionContext){
	    checkedExecutionContext.add(executionContext);
	}
    }

    public Permission getPermission() {
	return perm;
    }

    public void addAction(Runnable r) {
	synchronized (cleanup){
	    cleanup.add(new WeakReference<Runnable>(r));
	}
    }
    
    private List<Runnable> getRunnable(){
	List<Runnable> tasks;
	synchronized (cleanup){
	    tasks = new ArrayList<Runnable>(cleanup.size());
	    Iterator<WeakReference<Runnable>> it = cleanup.iterator();
	    while (it.hasNext()){
		WeakReference<Runnable> wr = it.next();
		Runnable r = wr.get();
		if (r != null){
		    tasks.add(r);
		}else {
		    cleanup.remove(wr);
		}
	    }
	}
	return tasks;
    }
    
    private void clear(){
	synchronized (checkedExecutionContext){
	    checkedExecutionContext.clear();
	}
    }

    public void accessControlExit() {
	throw new UnsupportedOperationException("Not supported yet.");
    }

}
