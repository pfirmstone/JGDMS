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


package org.apache.river.test.spec.renewalservice;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.RememberingRemoteListener;
import java.io.IOException;
import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.core.event.EventRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.io.MarshalledInstance;
import net.jini.lease.ExpirationWarningEvent;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

/**
 * This class provides methods to perform often used convenience routines.
 * Helps make testing code more reliable, robust and (sometimes) easier to 
 * read.
 *
 */
public class LeaseRenewalServiceTestUtil extends Object {

    protected static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness.test");
    
    /**
     * A value to compensate for Latency
     */
    public static long LATENCY = 5 * 1000;

    /**
     * Utility class that provides access to testing framework methods.
     */
    private final QAConfig config;

    /**
     * error message (loaded by convenience routines) 
     */
    protected String failureReason = null;

    /**
     * Constructor requiring a QAConfig object.
     * 
     */
    public LeaseRenewalServiceTestUtil(QAConfig config) {
	super();
	this.config = config;
    }
    
    /**
     * A convenience method to determine if a lease has expired.
     * 
     * @param lease  the lease whose expiration time is checked.
     * 
     * @return true if the lease has expired, false otherwise
     * 
     */
    public boolean isExpired(Lease lease) {
	if (System.currentTimeMillis() >= lease.getExpiration()) {
	    return true;
	} else {
	    return false;
	}
    }

    /**
     * A convenience method to convert a duration to an expiration time.
     * 
     * @param duration  the duration time to be converted
     * 
     * @return System.currentTimeMillis() + duration
     * 
     */
    public long durToExp(long duration) {
	return System.currentTimeMillis() + duration;
    }

    /**
     * A convenience method to convert an expiration time to an duration.
     * Note: this method can return a negative number.
     * 
     * @param expiration  the expiration time to be converted
     * 
     * @return expiration - System.currentTimeMillis()
     * 
     */
    public long expToDur(long expiration) {
	return expiration - System.currentTimeMillis();
    }

    /**
     * A convenience method to validate an EventRegistration object
     * for ExpirationWarningEvents.
     * 
     * <P>Notes:</P>
     * 
     * @param evReg an EventRegistration object whose validity is tested.
     * 
     * @param lrSet a renewal set which is the expected source of
     * this event registration.  
     * 
     * @return true if the event registration is valid; false otherwise.
     * 
     */
    public boolean isValidExpWarnEventReg(EventRegistration evReg,
					  LeaseRenewalSet lrSet) 
	throws TestException
    {
	long eventID = LeaseRenewalSet.EXPIRATION_WARNING_EVENT_ID;
	return isValidEventReg(evReg, lrSet, eventID);
    }

    /**
     * A convenience method to validate an EventRegistration object
     * for RenewalFailureEvents.
     * 
     * @param evReg an EventRegistration object whose validity is tested.
     * 
     * @param lrSet a renewal set which is the expected source of
     * this event registration.  
     * 
     * @return true if the event registration is valid; false otherwise.
     * 
     */
    public boolean isValidRenewFailEventReg(EventRegistration evReg,
					    LeaseRenewalSet lrSet) 
	throws TestException
    {

	long eventID = LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID;
	return isValidEventReg(evReg, lrSet, eventID);
			       
    }

    /**
     * A method to generally check the validity of an EventRegistration.
     * 
     * <P>Notes:</P>
     * 
     * @param evReg an EventRegistration object whose validity is tested.
     * @param lrSet a renewal set which is the expected source of
     * this event registration.  
     * @param evID the expected event id.
     * 
     * @return true if the event registration is valid; false otherwise.
     * @throws TestException if a configuration error occurs while
     *                         preparing the renewal set
     * 
     */
    protected boolean isValidEventReg(EventRegistration evReg,
				      LeaseRenewalSet lrSet,
				      long evID) throws TestException {
	// evReg must have been prepared by the caller
	LeaseRenewalSet source = (LeaseRenewalSet) evReg.getSource();
	source = 
	    (LeaseRenewalSet) 
	    QAConfig.getConfig().prepare("test.normRenewalSetPreparer",
					      source);
	// lease isn't prepared, since only equals is called
	Lease lease = source.getRenewalSetLease(); 

	// test to ensure that all fields match
	boolean isSameSet = source.equals(lrSet);
	boolean isSameLease = lease.equals(lrSet.getRenewalSetLease());
	boolean isExpectedID = evReg.getID() == evID;

	// create a failure message (if necessary)
	failureReason = new String();
	if (! isSameSet) {
	    failureReason += "\nSource field does not match the renewal set.";
	}
	if (! isSameLease) {
	    failureReason += "\nLease field contains the wrong lease.";
	}
	if (! isExpectedID) {
	    failureReason += "\nThe event ID is not expected value.";
	}

	return isSameSet && isSameLease && isExpectedID;
    }

    /**
     * Returns the reason text of the last failure.
     * 
     */
    public String getFailureReason() {
	return failureReason;
    }


    /**
     * Sleep for the specified duration and print out a reason for the call
     * to sleep.
     * 
     * @param duration  the number of milliseconds to sleep
     * @param reason the String that is the reason for the call to sleep
     * 
     * @exception InterruptException
     *          If another thread calls interrupt on this Thread. 
     * 
     */
    public void sleepAndTell(long duration, String reason) 
               throws InterruptedException {
	
	// ensure duration is a sane value
	duration = duration < 0 ? 0 : duration;

	// compose a message telling why this thread is sleeping
	String message = "Sleeping " + duration + " milliseconds : ";
	if (reason != null) {
	    message += reason;
	}
	logger.log(Level.FINE, message);

	// sleep
	Thread.sleep(duration);
    }
    
    /**
     * This method determines how the renewal set lease is canceled.
     * 
     * <P>Notes:</P>
     * This method is sort of a template method to support reuse of test code.
     * 
     * @param lease the lease whose expiration is waited for
     * @param reason a String representing the reason for waiting
     * 
     * @return true if lease is expired, false otherwise.
     */
    protected boolean waitForLeaseExpiration(Lease lease, String reason) 
                       throws UnknownLeaseException, RemoteException,
                              InterruptedException {

	long duration = expToDur(lease.getExpiration()) + LATENCY;
	sleepAndTell(duration, reason);
	return isExpired(lease);
    }

    /**
     * Returns a running Thread that is waiting until the event count of the
     * RememberingRemoteListener reaches at least numberOfEvents.
     * 
     * <P>Notes:
     * <BR>The start() method has already been called on the
     * returned thread.
     * <BR>Also, the return decision is based soley on
     * the current internal count of the number of events
     * received. So, for example, if the RememberingRemoteListener has
     * already received 2 events and this method is called with value
     * of 2 or less, then the Thread will exit immediatelty from its
     * run method.</P>
     * 
     * @param listnr the RemoteListener to wait on
     * @param numberOfEvents the lower bound event count target
     * 
     * @return a running thread that is waiting for the event count of
     * the RememberingRemoteListener to reach at least numberOfEvents.
     *  
     */
    public Thread createRemoteEventWaitThread(
				     final RememberingRemoteListener listnr,
				     final long numberOfEvents) {

	// create a new Thread instance that waits for events
	final int WAIT_TIME = 5000; // 5 seconds
	Thread waitThread = new Thread(new Runnable() {
	    public void run() {
		long numberReceived = listnr.getEvents().length;
		while (numberReceived < numberOfEvents) {
		    try {
			Thread.sleep(WAIT_TIME);
			numberReceived = listnr.getEvents().length;
		    } catch (InterruptedException ex) {
			// quit the loop
			break;
		    }
		}
	    }
	});	

	// start the thread rolling
	waitThread.start();

	// return the Thread so caller can join on it
	return waitThread;

    }

    /**
     * Wait on a RememberingRemoteListener for its event count to go
     * to at least numberOfEvents.
     * 
     * <P>Notes:</P>
     * 
     * @param listnr the RemoteListener to wait on
     * @param numberOfEvents the number of events to wait for
     * @param timeOutInMillis the number of milliseconds to wait before timing out
     * 
     * @return true if all events were received, false if timeout occurred
     *  */
    public boolean waitForRemoteEvents(RememberingRemoteListener listnr,
				       long numberOfEvents,
				       long timeOutInMillis) 
             throws InterruptedException {

	// start waiting polling for events
	Thread waitThread = 
	    createRemoteEventWaitThread(listnr, numberOfEvents);

	// say what's going on
	logger.log(Level.FINE, "Waiting " + timeOutInMillis + " milliseconds" +
		               " for " + numberOfEvents + 
		               " RemoteEvent(s) to arrive.");

	// wait the requisite amount of time for events to arrive
	waitThread.join(timeOutInMillis);

	// return the status of event arrival
	return (listnr.getEvents().length >= numberOfEvents);

    }

    /**
     * Helper method to determine if a lease is in an array.
     * 
     * @param lease the Lease whose index is sought in the array
     * @param array the array containing leases to be searched
     * 
     * @return the index in array where lease is found or -1 if not found.
     * 
     */
    public int indexOfLease(Lease lease, Lease[] array) {

	for (int i = 0; i < array.length; ++i) {
	    if (lease.equals(array[i])) {
		return i;
	    }
	}

	return -1; // not found
    }

    /**
     * Helper method to determine if a marshalled lease is in an array.
     * 
     * @param lease the Lease whose marshalled object index is sought 
     *              in the array
     *
     * @param array the array containing marshalled objects for search
     * 
     * @return the index in array where lease is found or -1 if not found.
     * 
     */
    public int indexOfLease(Lease lease, MarshalledObject[] array) 
             throws IOException, ClassNotFoundException {

	Lease[] leaseArray = new Lease[array.length];
	for (int i = 0; i < array.length; ++i) {
	    leaseArray[i] = (Lease) new MarshalledInstance(array[i]).get(false);
	}

	return indexOfLease(lease, leaseArray);
    }

} // LeaseRenewalServiceTestUtil
