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
package org.apache.river.test.share;

import java.io.IOException;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lease.LeaseDeniedException;

import net.jini.lease.RenewalFailureEvent;

/**
 * Owner implementation that throws a designated
 * throwable after a given number of renewals
 */
public class FailingOwner extends LeaseOwner {

    protected static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /** Throwable(s) to throw and indexes into the array */
    final private Throwable[] toThrow;
    private int throwIndex = 0; // index of next Throwable to throw
    private int lastIndex = -1; // index of the last Throwable thrown

    /** How many more renewals to wait before starting to throw exceptions */
    private int renewsUntilError;

    /**
     * Set to a non-<code>null</code> if the Lease Renewal Service screws up
     */
    private String rslt = null;

    /** Max time we are will to grant on a renewal request */
    final private long maxExtension;

    /** 
     * True if it is ok to see renewal tries after the exception is thrown
     */
    private boolean retryOk;

    /** Time of the last renewal call */
    private long lastRenewal;

    /** Time of the renewal call we threw the first exception from */
    private long thrownAt = -1;

    /** Time the failure event was recived at */
    private long failureEventAt = -1;

    /** 
     * Simple constructor 
     * @param toThrow Exception to throw after <code>renewsUntilError</code>
     *                renews
     * @param renewsUntilError Number of renews to wait for before throwing
     *                exceptions
     * @param maxExtension Maximum time this owner will be willing to extend 
     *                     the lease
     */
    public FailingOwner(Throwable toThrow, int renewsUntilError, 
			long maxExtension)
    {
	if (!((toThrow instanceof RemoteException) ||
	      (toThrow instanceof RuntimeException) ||
	      (toThrow instanceof Error) ||
	      (toThrow instanceof LeaseDeniedException) ||
	      (toThrow instanceof UnknownLeaseException)))
	    throw new IllegalArgumentException("FailingOwner:toThrow must " +
	        "an instance of RemoteException, RuntimeException, " +
		"Error, LeaseDeniedException, or UnknownLeaseException");

	throwIndex = 0;
	this.toThrow = new Throwable[1];
	this.toThrow[throwIndex] = toThrow;
	this.renewsUntilError = renewsUntilError;
	this.maxExtension = maxExtension;
    }

    /** 
     * Constructor allowing an array of different types of Throwables.
     * Each Throwable is used in order. If the end of the array is
     * reached, the index is reset to the beginning.
     *
     * @param toThrow Array of Exceptions to throw after 
     *                <code>renewsUntilError</code> renews
     * @param renewsUntilError Number of renews to wait for before throwing
     *                exceptions
     * @param maxExtension Maximum time this owner will be willing to extend 
     *                     the lease
     */
    public FailingOwner(Throwable[] toThrow, int renewsUntilError, 
			long maxExtension)
    {

	for (int i = 0; i < toThrow.length; ++i) {
	    if (!((toThrow[i] instanceof RemoteException) ||
		  (toThrow[i] instanceof RuntimeException) ||
		  (toThrow[i] instanceof Error) ||
		  (toThrow[i] instanceof LeaseDeniedException) ||
		  (toThrow[i] instanceof UnknownLeaseException))) {

		String eMessage = "FailingOwner:toThrow must " +
		    "an instance of RemoteException, RuntimeException, " +
		    "Error, LeaseDeniedException, or UnknownLeaseException";
		throw new IllegalArgumentException(eMessage);

	    }
	}

	this.toThrow = toThrow;
	this.renewsUntilError = renewsUntilError;
	this.maxExtension = maxExtension;
    }

    /**
     * Set rslt string if it is not already set
     */
    private void setRsltIfNeeded(String newResult) {
	if (rslt == null) {
	    rslt = newResult;
	}
    }

    // Inherit java doc from super type
    public synchronized Object renew(long extension) 
        throws LeaseDeniedException, UnknownLeaseException
    {
	final long now = System.currentTimeMillis();
	lastRenewal = now;

	if (renewsUntilError > 0) {
	    // Normal renewal

	    long grant;
	    if (extension == Lease.ANY) {
		grant = maxExtension;
	    } else {
		grant = Math.min(maxExtension, extension);
	    }

	    logger.log(Level.FINE, "Normal renewal");
	    renewsUntilError--;
	    return new Long(grant);
	} else {
	    // If we have already thrown the exception and there should 
	    // be no retry log an error
	    if (thrownAt > 0 && !retryOk) 
		setRsltIfNeeded("Tried to renew lease after a definite " +
				"exception:" + toThrow);

	    // We are in exception throwing mode
	    if (thrownAt < 0) {
		thrownAt = now;		
		logger.log(Level.FINE, "`Throwing' " + toThrow);
	    } 

	    /* this throwable will determine the retry status of the next one
	       $$$ should ServerErrors also be retryOk = false? */
	    if ((toThrow[throwIndex] instanceof RemoteException) &&
		!(toThrow[throwIndex] instanceof NoSuchObjectException))
		retryOk = true;
	    else
		retryOk = false;

	    // determine index for next Throwable
	    lastIndex = throwIndex;
	    throwIndex = (lastIndex + 1) % toThrow.length;

	    if (toThrow[lastIndex] instanceof RemoteException) {
		return toThrow[lastIndex];
	    } else if (toThrow[lastIndex] instanceof RuntimeException) {
		throw (RuntimeException) toThrow[lastIndex];
	    } else if (toThrow[lastIndex] instanceof UnknownLeaseException) {
		throw (UnknownLeaseException) toThrow[lastIndex];
	    } else if (toThrow[lastIndex] instanceof LeaseDeniedException) {
		throw (LeaseDeniedException) toThrow[lastIndex];
	    } else if (toThrow[lastIndex] instanceof Error) {
		// Note: Because we throw this here (instead of returning to 
		// be throw by TestLease.doRenew(), it will be recived
		// as a server Error by the service.
		throw (Error) toThrow[lastIndex];
	    } else {
		setRsltIfNeeded("ERROR in test config:Can't throw " + 
				toThrow[lastIndex]);
		return new Long(now + maxExtension);
	    }
	}
    }

    public long batchRenew(long extension) throws UnknownLeaseException,
                                                  LeaseDeniedException {
	Long grant = (Long) renew(extension); 
	return grant.longValue();
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
     * Returns the time of the last renewal call
     */
    public synchronized long getLastRenewTime() {
	return lastRenewal;
    }

    /**
     * Log the exception of a renewal failure event.
     * @throws IOException if the event's throwable can't be unpacked
     * @throws ClassNotFoundException if the event's throwable can't be unpacked
     */
    public synchronized void logEvent(RenewalFailureEvent e) 
        throws IOException, ClassNotFoundException
    {
	if (failureEventAt < 0) {
	    failureEventAt = System.currentTimeMillis();

	    if (thrownAt < 0) {
		// We have not thrown an exception yet!
		setRsltIfNeeded("Failure event before failure");   		
	    }
	}

	// Can't use .equals here because Throwable does not have a reasonable
	// definition of equals, instead compare the result of calling 
	// getMessage() and their class names
	final String m1 = toThrow[lastIndex].getMessage();
	final String m2 = e.getThrowable().getMessage();
	final String cn1 = toThrow[lastIndex].getClass().getName();
	final String cn2 = e.getThrowable().getClass().getName();
	if (!m1.equals(m2) || !cn1.equals(cn2))
	    setRsltIfNeeded("Exception thrown did not match one returned");
    }
    
    /**
     * Return <code>null</code> if the lease assocated with this 
     * owner had all the right things and an error message otherwise.
     */
    public synchronized String didPass() {
	if (thrownAt < 0) {
	    setRsltIfNeeded("Never threw exception");   
	}

	if (failureEventAt < 0) {
        logger.log(Level.FINE, "failureEventAt:" + failureEventAt + " " + 
		   toThrow[lastIndex]);
	    setRsltIfNeeded("Never received failure event");   
	}
	    
	return rslt;
    }

    /**
     * Return the last throwable thrown by this owner.
     * 
     * @return the instance of the last throwable thrown by this owner or null
     *         if none has yet been thrown.
     * 
     */
    public Throwable getLastThrowable() {
        synchronized (this){
            return toThrow[lastIndex];
        }
    }

}






