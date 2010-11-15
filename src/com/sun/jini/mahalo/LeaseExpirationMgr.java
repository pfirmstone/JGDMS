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

package com.sun.jini.mahalo;

import java.lang.ref.WeakReference;

import net.jini.core.lease.UnknownLeaseException;
import net.jini.id.Uuid;
import com.sun.jini.thread.WakeupManager;
import com.sun.jini.collection.WeakTable;
import com.sun.jini.landlord.LeasedResource;


/**
 * Lease Mgr implementation that aggressively expires leases as they
 * expiration times occur.  Synchronizes on resource before canceling it.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see com.sun.jini.mahalo.LeaseManager
 */
class LeaseExpirationMgr implements LeaseManager, WeakTable.KeyGCHandler {
    /**
     * Interface that allows LeaseExpirationMgr to expire resources.
     * This is the same as the
     * <code>com.sun.jini.landlord.Landlord.cancel()<code> method less
     * the <code>RemoteException</code> in the throws clause. Mixing
     * this interface into a <code>Landlord</code> implementation
     * allows the <code>LeaseExpirationMgr</code> to cancel leases
     * without having to deal with <code>RemoteException</code>.
     */
    static interface Expirer {
	/**
	 * Called by a LeaseExpirationMgr when it needs to expire a
	 * resource. The value of the <code>cookie</code> parameter is
	 * obtained from <code>getCookie()</code> method of the
	 * <code>LeasedResource</code> being expired.
         */
	public void cancel(Uuid cookie) throws UnknownLeaseException;
    }


    // Map of resources to tickets
    private WeakTable		ticketMap = new WeakTable(this); 
    private Expirer		landlord;
    private WakeupManager expirationQueue
        = new WakeupManager(new WakeupManager.ThreadDesc(null, true));

    /**
     * Create a <code>LeaseExpirationMgr</code> to aggressively expire
     * the leases of the passed landlord (implementing
     * <code>Expirer</code> is trivial for a <code>Landlord</code>.
     */
    LeaseExpirationMgr(Expirer landlord) {
	this.landlord = landlord;
    }

    /**
     * Terminate the <code>LeaseExpirationMgr</code>, killing any 
     * threads it has started
     */
    void terminate() {
        expirationQueue.stop();
        expirationQueue.cancelAll();
    }

    
    // purposefully inherit doc comment from supertype
    public void register(LeasedResource resource) {
	schedule(resource);
    }

    // purposefully inherit doc comment from supertype
    public void renewed(LeasedResource resource) {
	// Remove the old event
	expirationQueue.cancel(
	    (WakeupManager.Ticket)ticketMap.remove(resource)
	);
	// Schedule the new event
	schedule(resource);
    }

    /** 
     * Schedule a leased resource to be reaped in the future. Called
     * when a resource gets a lease, or a lease is renewed.
     */
    private void schedule(LeasedResource resource) {
	final WakeupManager.Ticket ticket =
	    expirationQueue.schedule(resource.getExpiration(),
				     new Canceler(resource));
	ticketMap.getOrAdd(resource, ticket);
    }

    // purposefully inherit doc comment from supertype
    // Called when LeaseResource we are tracking is garbage collected
    public void keyGC(Object value) {
	final WakeupManager.Ticket ticket = (WakeupManager.Ticket)value;
	expirationQueue.cancel(ticket);
    }

    /**
     * Objects that do the actually cancel the resource in question, stuck
     * in <code>WakeupManager</code>
     */
    private class Canceler implements Runnable {
	private final WeakReference resourceRef;
	
	/**
	 * Create a <code>Canceler</code> for the passed resource
	 */
	Canceler(LeasedResource resource) {
	    resourceRef = new WeakReference(resource);
	}

	/**
	 * Check the associated resource's expiration against the
 	 * current time, canceling the resource if its time has
	 * passed.  Synchronize on the resource before checking the
	 * expiration time.
	 */
	public void run() {
	    final LeasedResource resource = (LeasedResource)resourceRef.get();
	    if (resource == null)
		// Already gone
		return;

	    synchronized (resource) {
		if (resource.getExpiration() <= System.currentTimeMillis()) {
		    try {
			ticketMap.remove(resource);
			landlord.cancel(resource.getCookie());
		    } catch (UnknownLeaseException e) {
		        // Don't care, probably already gone
		    }
		}
		// else Someone must have just renewed the resource,
		// don't need to re-register since that will be done
		// by the renewer
	    }
	}
    }
}
