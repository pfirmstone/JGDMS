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
import net.jini.core.lease.UnknownLeaseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple owner that lets the lease be renewed a number of times.  Callers
 * of <code>waitForRenewals</code> will block until the lease has been 
 * renewed a give number of times or until the timeout expires.
 */
public class RenewingOwner extends LeaseOwner {

    private static Logger logger = Logger.getLogger("org.apache.river.qa.harness");

    /** Semaphore we use to count how many times the lease has been renewed */
    final private Semaphore renewCount;

    /** String to be returned by <code>waitForRenews</code> */
    private String rslt = null;

    /** Max time we are will to grant on a renewal request */
    final private long maxExtension;

    /** Desired expiration renewal service should be renewing towards */
    final private long desiredExpiration;

    /** Max acceptable varaion from desiredExpiration for a renewal request */
    final private long slop;

    /** Current expiration time of lease */
    private long expiration;

    /** Time of last renewal */
    private long lastRenewal;

    /** 
     * Simple constructor 
     * @param initialExpiration Initial expiration time for lease.
     * @param count Number of times the lease should be renewed
     * @param maxExtension Maximum time this owner will be willing to extend 
     *                     the lease
     * @param desiredExpiration Every lease a renewal service is renewing
     *              has a "membership expiration" associated with it.  The
     *              renewal service should all-ways pick a renewal duration
     *              that when added to the current time equals this membership
     *              expiration time.
     * @param slop Allowable variance from desired expiration when making a
     *             renewal request.
     */
    public RenewingOwner(long initialExpiration, int count, long maxExtension,
			 long desiredExpiration, long slop) 
    {
	renewCount = new Semaphore(count);
	this.maxExtension = maxExtension;
	this.desiredExpiration = desiredExpiration;
	this.slop = slop;
	expiration = initialExpiration;
    }

    /**
     * Wait until the passed number of milliseconds pass, the appropriate
     * number of renewals occure, or a error occures.
     * @param waitFor How long to wait for all of the renewals to occure
     * @return <code>null</code> if appropriate number of returns occurred
     * in the specified period, or a <code>String</code> describing the 
     * error condition.
     * @throws InterruptedException if this thread is interupted.
     */
    public String waitForRenewals(long waitFor) throws InterruptedException {
	final int count = renewCount.waitOnZero(waitFor);

	if (count != 0 && rslt == null) 
	    rslt = "Did not get enough renewal calls before timeout";

	return rslt;
    }

    /**
     * Set rslt string if it is not already set and set renwals to zero
     */
    private void setRsltIfNeeded(String newResult) {
	if (rslt == null) {
	    rslt = newResult;
	    renewCount.zero();
	}
    }

    // Inherit java doc from super type
    public synchronized long batchRenew(long extension) 
	     throws UnknownLeaseException {
	final long now = System.currentTimeMillis();
	logger.log(Level.FINER, "batchRenew called at " + now);
	lastRenewal = now;

	// Has the lease expired?
	if (expiration < now) {
	    if ((now - slop) > desiredExpiration)
		setRsltIfNeeded("Renwal service let lease expire");
	    throw new UnknownLeaseException("Lease expired");
	}

	// No, is the LRS renewing to the correct desired expiration?
	final long requestedExp = now + extension;

	if (requestedExp < 0) {
	    // Overflow, ignore slop and just check to see if
	    // desired expiration is forever
	    if (desiredExpiration != Long.MAX_VALUE) {
		setRsltIfNeeded("Renewal service asked for a new expiration " +
		    "of " + requestedExp + " should have asked for " +
		     desiredExpiration + " (a diffrence of " + 
		     (requestedExp - desiredExpiration) + 
		     " milliseconds)");
	    }
	} else {
	    // No overflow...no...forever check to see if the diff
	    // is less than the slop
	    final long diff = Math.abs(requestedExp - desiredExpiration);
	    if (diff > slop) {
		logger.log(Level.FINER, "expiration/desiredexpiration mismatch");
		setRsltIfNeeded("Renewal service asked for a new expiration " +
		    "of " + requestedExp + " should have asked for " +
		     desiredExpiration + " (a diffrence of " + diff +
		     " milliseconds)");
	    }
	}

	// Seem to ask for the right extension
	// Calculate the renewal grant __DURATION__
	
	long grant;
	if (extension == Lease.ANY) {
	    grant = maxExtension;
	} else {
	    grant = Math.min(maxExtension, extension);
	}

	expiration = now + grant;
	if (expiration < 0) {
	    // Overflow
	    expiration = Long.MAX_VALUE;
	    grant = Long.MAX_VALUE - now;
	}

	renewCount.dec();
	return grant;
    }

    // Inherit java doc from super type
    public Object renew(long extension) throws UnknownLeaseException {
	return new Long(batchRenew(extension));
    }

    // Inherit java doc from super type
    public void batchCancel() {
	setRsltIfNeeded("Renewal Service canceld lease!");
    }

    // Inherit java doc from super type
    public Throwable cancel() {
	batchCancel();
	return null;
    }

    /** 
     * Returns the expiration time of the lease
     */
    public synchronized long getExpiration() {
	return expiration;
    }

    /** 
     * Returns the time of the last renewal call
     */
    public synchronized long getLastRenewTime() {
	return lastRenewal;
    }    
}
