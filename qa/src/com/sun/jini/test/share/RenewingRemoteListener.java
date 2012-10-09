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

// java.*
import java.rmi.RemoteException;
import java.util.logging.Level;

// net.jini
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.core.lease.Lease;
import net.jini.lease.ExpirationWarningEvent;
import net.jini.lease.LeaseRenewalSet;

import net.jini.export.Exporter;

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/**
 * RenewingRemoteListener keeps track of each RemoteEvent it
 * receives and if the event is an ExpirationWarningEvent it will attempt
 * to renew the lease.
 *
 * 
 */
public class RenewingRemoteListener extends RememberingRemoteListener {
    
    /**
     * the duration for renewing the set's lease 
     */
    protected long duration = 0;

    /**
     * If lease could not be renewed then the exception is stored here.
     */
    protected Throwable renewalException = null;

    /**
     * The last lease that was successfully renewed.
     */
    protected Lease lastLeaseRenewed = null;

    /**
     * Constructor requiring a renewal duration value and an Exporter.
     * 
     * @exception RemoteException
     *          Remote initialization problem.  
     */
    public RenewingRemoteListener(Exporter exporter, long renewSetDur) 
	     throws RemoteException {
	super(exporter);
	duration = renewSetDur;
    }
    
    // inherit javadoc from parent class
    public synchronized void notify(RemoteEvent theEvent) 
           throws UnknownEventException, RemoteException {
	super.notify(theEvent);
	if (theEvent.getClass() == ExpirationWarningEvent.class) {
	    LeaseRenewalSet set = (LeaseRenewalSet) theEvent.getSource();
	    Lease lease = null;
	    try {
		set = (LeaseRenewalSet) 
		      QAConfig.getConfig().prepare("test.normRenewalSetPreparer",
							set);
		lease = set.getRenewalSetLease();
		lease =  (Lease)
			 QAConfig.getConfig().prepare("test.normLeasePreparer",
							   lease);
	    } catch (TestException e) {
		renewalException = e;
		logger.log(Level.INFO,"Configuration error", e);
		return;
	    }
	    // try to renew until lease is expired.
	    boolean canBeRenewed = true;
	    while (canBeRenewed) {
		try {
		    lease.renew(duration);
		    renewalException = null;
		    canBeRenewed = false;
		    lastLeaseRenewed = lease;
		    logger.log(Level.FINE, "Successfully renewed for " + duration +
				           " milliseconds.");
		} catch (Exception ex) {
		    // assign the exception for later retrieval
		    renewalException = ex;
		    canBeRenewed = 
			(lease.getExpiration() >= System.currentTimeMillis());
		    if (canBeRenewed) {
			logger.log(Level.FINE, "Failed to renew lease, trying again.");
		    } else {
			logger.log(Level.FINE, "Failed to renew lease, quitting.");
		    }
		    try {
			Thread.sleep(250L); // try every quarter of a second
		    } catch (InterruptedException interruptedEx) {
			canBeRenewed = false; // okay we'll stop, I guess??
		    }
		}	    
	    }
	}
    }

    /**
     * Return possible exception from renewal attempt.
     * 
     * <P>Notes:<BR>A value of null indicates renewal was successful.</P>
     * 
     * @return  the exception that resulted from failed renewal attempt.
     */
    public synchronized Throwable getRewewalException() {

	return renewalException;
    }

    /**
     * Return the last lease that was successfully renewed.
     * 
     * @return the last lease successfully renewed or null if none renewed yet.
     * 
     */
    public synchronized Lease getLastLeaseRenewed() {
	return lastLeaseRenewed;
    }

} // RenewingRemoteListener




