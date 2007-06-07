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
package com.sun.jini.norm;

import com.sun.jini.thread.InterruptedStatusThread;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.lease.DesiredExpirationListener;
import net.jini.lease.LeaseRenewalEvent;

/**
 * Object that transfers events from the Lease Renewal Manager to the
 * rest of Norm Server.  Isolates the renewal manager from having to
 * block on the snapshot locks.
 *
 * @author Sun Microsystems, Inc.
 */
class LRMEventListener extends InterruptedStatusThread 
                       implements DesiredExpirationListener 
{
    /** Logger for logging messages for this class */
    private static final Logger logger = Logger.getLogger("com.sun.jini.norm");

    /** Ref to the main server object which has all the top level methods */
    final private NormServerBaseImpl server;

    /**
     * Queue we use to decouple the reception of events from the lease
     * renewal manager from the scheduling of the sending of remote
     * events and modifying our internal tables (which both require
     * obtaining serious locks).
     */
    final private Queue queue = new Queue();

    /** Any events that hold this object are ignored */
    final static LeaseDeniedException EXPIRED_SET_EXCEPTION = 
	new LeaseDeniedException("Set Expired");
	
    /**
     * Simple constructor
     *
     * @param server Object that will make the actual internal updates and
     * schedule the sending of remote events
     */
    LRMEventListener(NormServerBaseImpl server) {
	super("LRM Event Listener");
	setDaemon(true);
	this.server = server;	
    }

    //////////////////////////////////////////////////
    // Methods required by the LeaseListener interface 

    // Inherit java doc from super type
    public void notify(LeaseRenewalEvent e) {	
	// Drop if the exception field is == to EXPIRED_SET_EXCEPTION, this
	// implies that lease could not be renewed because the wrapper 
	// has determined that the set has expired.
	if (e.getException() == EXPIRED_SET_EXCEPTION) 
	    return;

	// Paranoia, check to make sure that lease is one of wrapped
	// client lease...if it's not, ignore the event
	final Lease l = e.getLease();
	if (l instanceof ClientLeaseWrapper) {
	    final ClientLeaseWrapper clw = (ClientLeaseWrapper) l;
	    queue.enqueue(new Discriminator(clw, true));
	}
    }

    //////////////////////////////////////////////////////////////
    // Methods required by the DesiredExpirationListener interface
	
    // Inherit java doc from super type
    public void expirationReached(LeaseRenewalEvent e) {
	// Paranoia, check to make sure that lease is one of wrapped
	// client lease...if it's not, ignore the event
	final Lease l = e.getLease();
	if (l instanceof ClientLeaseWrapper) {
	    final ClientLeaseWrapper clw = (ClientLeaseWrapper) l;
	    queue.enqueue(new Discriminator(clw, false)) ;
	}
    }

    public void run() {
	// Loop taking items off the queue and pass them to the server
	while (!hasBeenInterrupted()) {
	    try {
		final Discriminator d = (Discriminator) queue.dequeue();

		if (d.isFailure) {
		    server.renewalFailure(d.clw);
		} else {
		    server.desiredExpirationReached(d.clw);
		}

	    } catch (InterruptedException e) {
		// Someone wants this thread dead -- just return
		return;
	    } catch (RuntimeException e) {
		logger.log(Level.INFO,
			   "Exception in LRMEventListener Notifier while " +
			   "processing an event from the LRM -- " +
			   "attempting to continue",
			   e);

	    } catch (Error e) {
		logger.log(Level.INFO,
			   "Exception in LRMEventListener Notifier while " +
			   "processing an event from the LRM -- " +
			   "attempting to continue",
			   e);
	    }
	}
    }

    /**
     * Trivial container class to tell us if we are processing the given
     * wrapper because of a failure event or a desired expiration
     * reached event.
     */
    static private class Discriminator {
	/** true if this wrapper is associated with a renewal failure event */
	final private boolean isFailure;

	/** The wrapped leases associated with the event */
	final private ClientLeaseWrapper clw;

	private Discriminator(ClientLeaseWrapper clw, boolean isFailure) {
	    this.isFailure = isFailure;
	    this.clw = clw;
	}
    }
}
