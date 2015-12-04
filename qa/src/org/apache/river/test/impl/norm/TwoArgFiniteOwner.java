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
package org.apache.river.test.impl.norm;

import net.jini.core.lease.Lease;

/**
 * BaseOwner for leases who have a finite membership expiration
 * and are added using the two arg form of renewFor
 */
public class TwoArgFiniteOwner extends BaseOwner {
    /** 
     * Simple constructor 
     * @param initialExpiration Initial expiration time for lease.
     * @param maxExtension Maximum time this owner will be willing to extend 
     *                     the lease
     * @param desiredExpiration Every lease a renewal service is renewing
     *              has a "membership expiration" associated with it.  The
     *              renewal service should all-ways pick a renewal duration
     *              that when added to the current time equals this membership
     *              expiration time.
     * @param slop Allowable variance from desired expiration when making a
     *             renewal request.
     * @param notifyOnFailure
     *             Object to notify if there is a failure
     */
    public TwoArgFiniteOwner(long initialExpiration, long maxExtension,
			long desiredExpiration, long slop,
			Object notifyOnFailure) 
    {
	super(initialExpiration, maxExtension, desiredExpiration,
	      slop, Lease.FOREVER, notifyOnFailure);
    }

    // Inherit java doc from super type
    boolean isTwoArg() {
	return true;
    }

    // Inherit java doc from super type
    boolean isValidExtension(long extension) {
	// LRS should always ask for the remander of the membership
	// duration

	// Get the diffrence between what they asked for and 
	// the desired expiration
	final long remander = desiredExpiration - now;
	final long diff = Math.abs(remander - extension);

	if (diff < slop) {
	    return true;
	} else {
	    setRsltIfNeeded("Two arg owner:LRS asked for " + extension +
                " when, it should have asked for " + remander +
		" (a diff of " + diff + ")");
	    return false;
	}
    }
}

