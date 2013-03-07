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
package com.sun.jini.test.share;

import net.jini.core.lease.Lease;
import com.sun.jini.qa.harness.QAConfig;

/**
 * TrackingOwner for leases who have a membership expiration of
 * Lease.FOREVER
 */
public class ForeverOwner extends TrackingOwner {
    /** 
     * True if the associated lease should be added to the set with
     * the two arg form of renewFor
     */
    final private boolean isTwoArg;

    /** 
     * Simple constructor 
     * @param initialExpiration Initial expiration time for lease.
     * @param maxExtension Maximum time this owner will be willing to extend 
     *                     the lease
     * @param slop Allowable variance from desired expiration when making a
     *             renewal request.
     * @param desiredRenewal
     *             Expect value of the renewDuration parameter
     * @param notifyOnFailure
     *             Object to notify if there is a failure
     * @param isTwoArg Should the assocated lease be registered
     *             with the one or two arg form
     * @param config QAConfig object
     */
    public ForeverOwner(long initialExpiration, long maxExtension,
			long slop, long desiredRenewal, Object notifyOnFailure,
			boolean isTwoArg, QAConfig config) 
    {
	super(initialExpiration, maxExtension, Lease.FOREVER,
	      slop, desiredRenewal, notifyOnFailure, config);

	this.isTwoArg = isTwoArg;
    }

    // Inherit java doc from super type
    boolean isTwoArg() {
	return isTwoArg;
    }

    // Inherit java doc from super type
    protected boolean isValidExtension(long extension) {
	// Since the desired expiration is forever, the LRS 
	// should always be asking for exactly desiredRenewal, or
	// something when added to now is close to Long.MAX_VALUE
	if (extension == desiredRenewal) {
	    return true;
	}
        synchronized (this){
            final long requestedExpiration = now + extension;
            if (requestedExpiration < 0)
                // They asked for at least FOREVER
                return true;

            if ((Long.MAX_VALUE - requestedExpiration) < slop)
                return true;
        }
	setRsltIfNeeded("Forever Owner:LRS asked for " + extension + 
			" when it should have asked for " + desiredRenewal);
	return false;
    }
}
