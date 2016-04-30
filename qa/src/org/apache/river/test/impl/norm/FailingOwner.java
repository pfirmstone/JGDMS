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

import java.io.PrintWriter;
import java.io.IOException;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.lease.LeaseDeniedException;

import net.jini.lease.RenewalFailureEvent;

import java.util.logging.Level;


/**
 * Owner implementation that throws a designated
 * throwable after a given number of renewals
 */
public class FailingOwner extends LeaseOwner {
    /** Throwable to throw */
    final private Throwable toThrow;

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
    final private boolean retryOk;

    /** Time of the last renewal call */
    private long lastRenewal;

    /** Time of the renewal call we threw the first exception from */
    private long thrownAt = -1;

    /** Time the failure event was recived at */
    private long failureEventAt = -1;

    /** Total number of attempts *after* exception was thrown */
    private long totalRetries;
	
    /** 
     * Simple constructor 
     * @param toThrow Exception to throw after <code>renewsUntilError</code>
     *                renews
     * @param definite Should be true if toThrow is definite
     * @param renewsUntilError Number of renews to wait for before throwing
     *                exceptions
     * @param maxExtension Maximum time this owner will be willing to extend 
     *                     the lease
     */
    public FailingOwner(Throwable toThrow, boolean definite, 
			int renewsUntilError, long maxExtension)
    {
	if (!((toThrow instanceof RemoteException) ||
	      (toThrow instanceof RuntimeException) ||
	      (toThrow instanceof Error) ||
	      (toThrow instanceof LeaseDeniedException) ||
	      (toThrow instanceof UnknownLeaseException)))
	    throw new IllegalArgumentException("FailingOwner:toThrow must " +
	        "an instance of RemoteException, RuntimeException, " +
		"Error, LeaseDeniedException, or UnknownLeaseException");

	this.toThrow = toThrow;
	this.renewsUntilError = renewsUntilError;
	this.maxExtension = maxExtension;
	
	retryOk = !definite;
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

	    logger.log(Level.INFO, "Normal renewal");
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
		logger.log(Level.INFO, "`Throwing' " + toThrow);
	    } else {
		logger.log(Level.INFO, "Again `Throwing' " + toThrow);
		totalRetries++;
	    }

	    if (toThrow instanceof RemoteException) {
		return toThrow;
	    } else if (toThrow instanceof RuntimeException) {
		throw (RuntimeException)toThrow;
	    } else if (toThrow instanceof UnknownLeaseException) {
		throw (UnknownLeaseException)toThrow;
	    } else if (toThrow instanceof LeaseDeniedException) {
		throw (LeaseDeniedException)toThrow;
	    } else if (toThrow instanceof Error) {
		// Note: Because we throw this here (instead of returning to 
		// be throw by TestLease.doRenew(), it will be recived
		// as a server Error by the service.
		throw (Error)toThrow;
	    } else {
		setRsltIfNeeded("ERROR in test config:Can't throw " + toThrow);
		return new Long(now + maxExtension);
	    }
	}
    }

    public long batchRenew(long extension) throws Throwable {
	final Object rslt = renew(extension);
	if (rslt instanceof Long)
	    return ((Long)rslt).longValue();

	if (rslt instanceof Throwable)
	    throw (Throwable)rslt;

	setRsltIfNeeded("TEST CODE ERROR::renew return wrong value");
	final long now = System.currentTimeMillis();
	return now + extension;
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

	final Throwable evt = e.getThrowable();

	if (evt == null) {	    
	    setRsltIfNeeded("Got an event with a null exception");
	    return;
	}

	// Can't use .equals here because Throwable does not have a reasonable
	// definition of equals, instead compare the result of calling 
	// getMessage() and their class names

	final String m1 = toThrow.getMessage();
	final String m2 = evt.getMessage();
        final String cn1 = toThrow.getClass().getName();
	final String cn2 = e.getThrowable().getClass().getName();
	if (m2 == null && m1 != null) {
	    setRsltIfNeeded("Exception thrown:\n"+ cn1+ "\ndid not match one returned:\n"+ cn2 + "\nmessages didn't match:\n " + m1 +"\n\nnull");
	    return;
	}
        if (m1 != null && !m1.equals(m2)){
            setRsltIfNeeded("Exception thrown:\n"+ cn1 +"\ndid not match one returned:\n"+ cn2 + "\nmessages didn't match:\n" + m1 +"\n\n" + m2);
	    return;
        }
        
	
	if (!cn1.equals(cn2))
	    setRsltIfNeeded("Exception thrown did not match one returned, class names different: " + cn1 + " " + cn2);
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
	    logger.log(Level.INFO, "failureEventAt:" + failureEventAt + " " + 
			       toThrow);
	    setRsltIfNeeded("Never received failure event");   
	}

	if (retryOk && totalRetries < 1) {
	    logger.log(Level.INFO, "LRS did not retry after " + toThrow);
	    setRsltIfNeeded("LRS did not retry after " + toThrow);   
	}
	    
	return rslt;
    }
}
