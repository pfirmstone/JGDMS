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
package com.sun.jini.test.impl.norm;

import java.io.PrintWriter;

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

import java.util.logging.Level;


/**
 * Simple owner that lets the renews the lease when asked. Ensures that
 * the lease has not expired and that each time it is renewed it has
 * be asked for the correct duration
 */
public class RenewAtOwner extends LeaseOwner {
    /** String to be returned by <code>didPass</code> */
    private String rslt = null;

    /** Max time we are will to grant on a renewal request */
    final private long maxExtension;

    /** Desired expiration renewal service should be renewing towards */
    final private long desiredExpiration;

    /** The max value we expect for each renewal request */
    final private long desiredRenewal;

    /** Max acceptable varaion from desiredExpiration for a renewal request */
    final private long slop;

    /** Current expiration time of lease */
    private long expiration;

    /** Time of last renewal */
    private long lastRenewal;

    /** Object to notify if we fail */
    private Object notifyOnFailure;

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
     * @param desiredRenewal
     *             Expect value of the renewDuration parameter
     * @param notifyOnFailure
     *             Object to notify if there is a failure
     */
    public RenewAtOwner(long initialExpiration, long maxExtension,
			long desiredExpiration, long slop,
			long desiredRenewal, Object notifyOnFailure) 
    {
	this.desiredRenewal = desiredRenewal;
	this.maxExtension = maxExtension;
	this.desiredExpiration = desiredExpiration;
	this.slop = slop;
	expiration = initialExpiration;
	this.notifyOnFailure = notifyOnFailure;
    }

    /**
     * Retrun true if the lease has not expired
     */
    private boolean ensureCurrent(long now) {
	return now < expiration + slop;       
    }

    /**
     * Return null if we dected no error, and a disciptive string otherwise
     */
    String didPass() {
	if (rslt != null) 
	    return rslt;

	// Make sure the lease has not expired (unless it should have)
	synchronized (this) {
	    final long now = System.currentTimeMillis();
	    final long diff = Math.abs(desiredExpiration - expiration);

	    if (!ensureCurrent(now) && diff > slop)
		rslt = "Lease expired";
	}

	return rslt;	    
    }
    
    /**
     * Set rslt string if it is not already set and set renwals to zero
     */
    private void setRsltIfNeeded(String newResult) {
	if (rslt == null) {	    
	    rslt = newResult;
	}
    }

    private synchronized long batchRenewSync(long extension) 
	throws UnknownLeaseException, LeaseDeniedException 
    {
	final long now = System.currentTimeMillis();
	lastRenewal = now;

	logger.log(Level.INFO, "batchRenew(" + desiredExpiration + "," + 
		    desiredRenewal + ")" + ":now=" + now + " extension=" +
		    extension);

	// Has the lease expired?
	if (expiration < now) {
	    if (!ensureCurrent(now))
		setRsltIfNeeded("Renwal service let lease expire");
	    throw new UnknownLeaseException("Lease expired");
	}

	// Have the asked for the right renewal?
	
	// Are they supposted to ask for Lease.ANY, and if they
	// are did they?
	if (desiredExpiration == Lease.FOREVER && desiredRenewal == Lease.ANY) {
	    if (extension != Lease.ANY) {
		setRsltIfNeeded("LRS asked for " + extension +
				" when it should have asked for Lease.ANY");
		throw new LeaseDeniedException("Next time ask for Lease.ANY");
	    }
	} else if (extension > desiredRenewal) {
	    // If desiredRenewal is not Lease.ANY extension must be less
	    // than or equal to desiredRenewal
	    setRsltIfNeeded("LRS asked for " + extension + " when it " +
			    "should have asked for no more than " +
			    desiredRenewal);
	    throw new LeaseDeniedException("Next time ask for no more than " +
					   desiredRenewal);
	} else if (desiredRenewal + slop > desiredExpiration - now) {
	    // Make sure they are asking for the remander of the
	    // desiredExpiration
	    
	    final long requestedExp = now + extension;
	    if (requestedExp < 0) {
		// Overflow, ignore slop and just check to see if
		// desired expiration is forever
		if (desiredExpiration != Long.MAX_VALUE) {
		    setRsltIfNeeded("Renewal service asked for a new " +
		        "expiration of " + requestedExp + 
			" should have asked for " + desiredExpiration +
			" (a diffrence of " + 
			(requestedExp - desiredExpiration) + " milliseconds)");
		    throw new LeaseDeniedException("Too MUCH!");
		}
	    } else {
		// No overflow...no...forever check to see if the diff
		// is less than the slop, or if it asked for
		// desiredRenewal
		final long diff = Math.abs(requestedExp - desiredExpiration);
		if (diff > slop) {
		    setRsltIfNeeded("Renewal service asked for a new " +
			"expiration of " + requestedExp + 
			" should have asked for " + desiredExpiration +
			" (a diffrence of " + diff + " milliseconds)");
		    throw new LeaseDeniedException("A bit off");
		}
	    }
	} else if (desiredRenewal != extension) {
	    // Should have asked for exactly desiredRenewal
	    setRsltIfNeeded("Renewal service asked for an extention " +
		"of " + extension + " should have asked for " + desiredRenewal);
	    
	    throw new LeaseDeniedException("Next time ask for " +
					   desiredRenewal);	    
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
	
	logger.log(Level.INFO, "batchRenew(" + desiredExpiration + "," + desiredRenewal
		    + ") granted:" + grant +" new exp:" + expiration);
	return grant;
    }

    // Inherit java doc from super type
    public long batchRenew(long extension) 
	     throws UnknownLeaseException, LeaseDeniedException 
    {
	try {
	    return batchRenewSync(extension);
	} finally {
	    // If after all this rslt is non null we have to notify 
	    // notifyOnFailure
	    if (rslt != null) {
		synchronized (notifyOnFailure) {
		    notifyOnFailure.notifyAll();
		} 
	    }
	}
    }

    /**
     * Return the lease desired duration we are shotting for
     */
    long getDesiredDuration() {
	if (desiredExpiration  == Lease.FOREVER) 
	    return Lease.FOREVER;
	else
	    return desiredExpiration - System.currentTimeMillis();
    }

    /**
     * Return the desired renewal request
     */
    long getDesiredRenewal() {
	return desiredRenewal;
    }

    // Inherit java doc from super type
    public Object renew(long extension) 
	throws UnknownLeaseException, LeaseDeniedException
    {
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
