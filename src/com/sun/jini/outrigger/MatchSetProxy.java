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
package com.sun.jini.outrigger;

import java.rmi.RemoteException;

import net.jini.core.entry.Entry;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.lease.Lease;
import net.jini.id.Uuid;
import net.jini.space.MatchSet;

/**
 * Outrigger's implementation of <code>MatchSet</code>.
 * Outrigger's implementation of the <code>JavaSpace05.contents</code>
 * method returns objects of this type. Created with an initial set of
 * entries from the match set and supports pre-fetching whenever it
 * needs additional entries. <code>RemoteException</code>s encountered
 * when making a remote call to fetch the next batch of entries from
 * the space generally do not invalidate the proxy or the match set.
 * <p>
 * Note, there is no way to serialize or otherwise copy instances of
 * this class so the default equals implementation should suffice.
 */
class MatchSetProxy implements MatchSet {
    /** The remote server this proxy works with. */
    final private OutriggerServer space;

    /** ID of the associated query (and lease) */
    final private Uuid uuid;

    /** Lease assocated with this query */
    final private Lease lease;

    /** Last batch fetched from server */
    private EntryRep[] reps;

    /** Last rep returned */
    private EntryRep lastRepReturned;

    /** Current index into reps */
    private int i;

    /** True if reps[i] could not be unpacked */
    private boolean unpackFailure = true; 

    MatchSetProxy(MatchSetData inital, SpaceProxy2 parent, OutriggerServer space) {
	uuid = inital.uuid;
	this.space = space;
	if (uuid != null) 
	    lease = parent.newLease(uuid, inital.intialLeaseDuration);
	else 
	    lease = null;
	reps = inital.reps;

	i=0;
    }

    public Lease getLease() {
	return lease;
    }

    public Entry next() throws RemoteException, UnusableEntryException {
	if (i >= reps.length) {
	    // Fetch another batch	    
	    i = 0;
	    reps = space.nextBatch(uuid, lastRepReturned.id());	    
	}

	if (reps[i] == null)
	    return null;

	unpackFailure = true;
	lastRepReturned = reps[i++];
	final Entry rslt = lastRepReturned.entry();
	unpackFailure = false;
	return rslt;
    }

    public Entry getSnapshot() {
	if (unpackFailure)
	    throw new IllegalStateException(
	        "getSnapshot - need successful next call first");

	return new SnapshotRep(lastRepReturned);
    }

    public String toString() {
	return getClass().getName() + " for " + uuid + 
	    " (through " + space + ")";
    }
}
