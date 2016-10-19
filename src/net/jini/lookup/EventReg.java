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

package net.jini.lookup;

import net.jini.core.lease.Lease;

/**
 * Data structure used to group together the lease and event sequence
 * number. For each LookupCache, there is a HashMap that maps a ProxyReg to
 * an EventReg.
 *
 * EventReg's object lock is also used to maintain atomicity of
 * lookup service state.
 */
final class EventReg {
    /* The Event source from the event registration */
    final Object source;
    /* The Event ID */
    final long eventID;
    /* The current event sequence number for the Service template */
    private long seqNo;
    /* The Event notification lease */
    final Lease lease;
    /* Event Suspension */
    boolean suspended;
    /* Discarded Registrar */
    boolean discarded;

    EventReg(Object source, long eventID, long seqNo, Lease lease) {
	this.source = source;
	this.eventID = eventID;
	this.seqNo = seqNo;
	this.lease = lease;
    }

    /**
     * @param seqNo the seqNo to set, return a positive delta if successful,
     * otherwise a negative value indicates failure.
     */
    long updateSeqNo(long seqNo) {
	assert Thread.holdsLock(this);
	long difference;
	difference = seqNo - this.seqNo;
	if (difference > 0) {
	    this.seqNo = seqNo;
	    return difference;
	} else {
	    return difference; // Return a negative or zero value
	}
    }
    
    boolean nonContiguousEvent(long seqNo){
        assert Thread.holdsLock(this);
        long difference = seqNo = this.seqNo;
        this.notifyAll();
        if (difference > 1) return true;
        return false;
    }
    
    void suspendEvents() {
        suspended = true;
    }
    
    boolean eventsSuspended(){
        return suspended;
    }
    
    void releaseEvents() {
        suspended = false;
    }
    
    boolean discard() {
        if (discarded) return false;
        discarded = true;
        return true;
    }
    
    boolean discarded(){
        return discarded;
    }
    
} //end class ServiceDiscoveryManager.EventReg
