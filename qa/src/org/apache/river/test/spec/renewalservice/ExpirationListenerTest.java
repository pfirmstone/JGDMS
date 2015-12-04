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

import java.util.logging.Level;

// java.rmi
import java.rmi.MarshalledObject;

// net.jini
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.share.RememberingRemoteListener;

import java.util.logging.Level;

/**
 * Asserts the following from the LRS spec.<P>
 * <OL> 
 * <LI>An event gets delivered to a RemoteEventListener before a lease 
 * in the lease renewal set expires.</LI> 
 * <LI>The handback in the ExpirationWarningEvent is rational.</LI>
 * <LI>The listener must be non-null.</LI>
 * <LI>THe minWarning must be 0 or greater.</LI>
 * <LI>A minWarning value that exceeds the current duration of the set's lease
 * causes immediate delivery of the ExpirationWarningEvent.</LI>
 * </OL>
 * 
 */
public class ExpirationListenerTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The renewal set duration time 
     */
    private long renewSetDur = 0;

    /**
     * Requested lease duration for the renewal set 
     */
    private final long RENEWAL_SET_LEASE_DURATION = 120 * 1000; // 2 minutes

    /**
     * The minimum time for sending ExpirationWarningEvents
     */
    private long minWarning = 0;

    /**
     * Minimum warning time for lease expiration.
     */
    private final long MIN_WARNING = 60 * 1000; // one minute

    /**
     * The time allowed for network transfers to take place 
     */
    private long latencySlop = 0;

    /**
     * Time to allow for network lag
     */
    private final long DEFAULT_SLOP = 30 * 1000; // 30 seconds

    /**
     * Percentage + or minus allowed for slop
     */
    private final int SLOP_PERCENT_TOLERANCE = 10;

    /**
     * listener that will log events as they arrive 
     */
    RememberingRemoteListener rrl = null;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "ExpirationListenerTest: In setup() method.");

       // capture the duration for the lease renewal set
       String prop = "org.apache.river.test.spec.renewalservice." +
	   "renewal_set_lease_duration";
       renewSetDur = getConfig().getLongConfigVal(prop, RENEWAL_SET_LEASE_DURATION);

       // capture the minimum warning ...
       prop = "org.apache.river.test.spec.renewalservice.minWarning";
       minWarning = getConfig().getLongConfigVal(prop, MIN_WARNING);

       // capture the max time allowed for network transfer
       prop = "org.apache.river.test.spec.renewalservice.latencySlop";
       latencySlop = getConfig().getLongConfigVal(prop, DEFAULT_SLOP);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());
       return this;
    }

    /**
     * Asserts the following from the LRS spec.<P>
     * <OL> 
     * <LI>An event gets delivered to a RemoteEventListener before a lease 
     * in the lease renewal set expires.</LI> 
     * <LI>The handback in the ExpirationWarningEvent is rational.</LI>
     * <LI>The listener must be non-null.</LI>
     * <LI>THe minWarning must be 0 or greater.</LI>
     * <LI>A minWarning value that exceeds the current duration of the 
     * set's lease causes immediate delivery of the ExpirationWarningEvent.</LI>
     * </OL>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "ExpirationListenerTest: In run() method.");

	// get the service for test
	LeaseRenewalService lrs = getLRS();

	// create a lease renewal set
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);

	// create a handback object
	MarshalledObject handback = new MarshalledObject(new Integer(99));

	// register listener to receive events
	EventRegistration evReg = 
	    set.setExpirationWarningListener(rrl, minWarning, handback);
	evReg = prepareRegistration(evReg);

	if (rstUtil.isValidExpWarnEventReg(evReg, set) == false) {
	    String message = "Event Registration is invalid.";
	    message += rstUtil.getFailureReason();
	    throw new TestException(message);
	}

	// allow the set's lease to expire
	rstUtil.waitForRemoteEvents(rrl, 1, renewSetDur);

	/* Assert that the notification was received at approximately
	   the lease expiration time - minWarning */
	Long[] arrivalTimes = rrl.getArrivalTimes();

	// must at lease have received one event
	if (arrivalTimes.length == 0) {
	    String message = "ExpirationWarning event never received.";
	    throw new TestException(message);
	}

	// must NOT have received more than one event
	if (arrivalTimes.length > 1) {
	    String message = "Too many events received.";
	    throw new TestException(message);
	}

	/* TESTING ASSERTION #1
	   was the event received around the right time? */
	String prop = "org.apache.river.test.spec.renewalservice." +
	       "slop_percent_tolerance";
	int tolerance = 
	    getConfig().getIntConfigVal(prop, SLOP_PERCENT_TOLERANCE);
	logger.log(Level.FINE, "Allowing a slop tolerance of (+/-)" + 
			  tolerance + "% of expected warning time."); 

	long leaseExpiration = prepareLease(set.getRenewalSetLease()).getExpiration();
	long idealArrival = leaseExpiration - minWarning;
	long arrivalSlop = minWarning * tolerance / 100;
	long minAllowed = idealArrival - arrivalSlop;
	long maxAllowed = idealArrival + arrivalSlop;
	long actualArrivalTime = arrivalTimes[0].longValue();

	if (actualArrivalTime > minAllowed && 
	    actualArrivalTime < maxAllowed) {
	    // we say event was received around the right time
	    logger.log(Level.FINE, "Assertion #1 passes!");
	} else {
	    /* There was a lag. This could be network problem or an
	       overloaded cpu but we will just have to assume that the
	       specification was not met. */
	    String message = "Assertion #1 failed ...\n";
	    message += "Event was not received +/- " + arrivalSlop;
	    message += " milliseconds.";
	    throw new TestException(message);
	} 

	/* TESTING ASSERTION #2
	   the handback object is the one we expect. */
	RemoteEvent[] events = rrl.getEvents();
	MarshalledObject mObj = events[0].getRegistrationObject();
	if (handback.equals(mObj) == false) {
	    String message = "Assertion #2 failed ...\n";
	    message += "Handback object does not match original.";
	    throw new TestException(message);
	}
	logger.log(Level.FINE, "Assertion #2 passes!");
	
	/* TESTING ASSERTION #3
	   a null listener results in a NullPointException. */
	set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	logger.log(Level.FINE, "Created Set with lease duration of " +
			  renewSetDur + " milliseconds");
	try {
	    evReg = set.setExpirationWarningListener(null, minWarning, 
						     handback);
	    evReg = prepareRegistration(evReg);
	    if (rstUtil.isValidExpWarnEventReg(evReg, set) == false) {
		String message = "Assertion #3 failed ...\n";
		message += "Event Registration is invalid.";
		throw new TestException(message);
	    }

	    String message = "Assertion #3 failed ...\n";
	    message += "Registration of null listener was allowed.";
	    throw new TestException(message);

	} catch (NullPointerException ex) {
	    // success, continue with the rest of the test
	    logger.log(Level.FINE, "Assertion #3 passes!");
	}
	
	/* TESTING ASSERTION #4
	   a negative value for minWarning results in an 
	   IllegalArgumentException */
	try {
	    evReg = set.setExpirationWarningListener(rrl, -10, handback);
	    evReg = prepareRegistration(evReg);
	    if (rstUtil.isValidExpWarnEventReg(evReg, set) == false) {
		String message = "Assertion #4 failed ...\n";
		message += "Event Registration is invalid.";
		throw new TestException(message);
	    }

	    String message = "Assertion #4 failed ...\n";
	    message += "A negative minWarning value was allowed.";
	    throw new TestException(message);
	} catch (IllegalArgumentException ex) {
	    // success, continue with the rest of the test
	}
	
	// edge case (try 0 to ensure it is allowed)
	try {
	    evReg = set.setExpirationWarningListener(rrl, 0, handback);
	    evReg = prepareRegistration(evReg);
	    if (rstUtil.isValidExpWarnEventReg(evReg, set) == false) {
		String message = "Assertion #4 failed ...\n";
		message += "Event Registration is invalid.";
		throw new TestException(message);
	    }

	    // success, continue with the rest of the test

	} catch (IllegalArgumentException ex) {
	    String message = "Assertion #4 failed ...\n";
	    message += "A minWarning value of 0 was not allowed.";
	    logger.log(Level.SEVERE, message, ex);
	    throw new TestException(message, ex);
	}
	logger.log(Level.FINE, "Assertion #4 passes!");
	
	/* TESTING ASSERTION #5
	   a minWarning value greater than the duration of the set's 
	   lease causes an immediate delivery of the 
	   ExpirationWarningEvent */

	// forget about all previous events
	rrl.clear();
	events = rrl.getEvents();
	if (events.length > 0) {
	    String message = "Assertion #5 failed ...\n";
	    message += "An expected error. RemoteListener did";
	    message += " not reset properly.";
	    throw new TestException(message);
	}
	
	// create a renewal set
	set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	logger.log(Level.FINE, "Created Set with lease duration of " +
			  renewSetDur + " milliseconds");

	// register with minWarning after lease expiration
	leaseExpiration = prepareLease(set.getRenewalSetLease()).getExpiration();
	long duration = rstUtil.expToDur(leaseExpiration);
	long lateWarning = duration + 10000;
	evReg = 
	    set.setExpirationWarningListener(rrl, lateWarning, handback);
	evReg = prepareRegistration(evReg);
	logger.log(Level.FINE, "minWarning on lease expiration is " +
			  lateWarning + " milliseconds");
	
	if (rstUtil.isValidExpWarnEventReg(evReg, set) == false) {
	    String message = "Assertion #5 failed ...\n";
	    message += "Event Registration is invalid.";
	    throw new TestException(message);
	}

	/* we should have the event already but still, perform check ...*/
	rstUtil.waitForRemoteEvents(rrl, 1, renewSetDur);

	// check to see if event has arrived there
	arrivalTimes = rrl.getArrivalTimes();
	if (arrivalTimes.length == 0) {
	    String message = "Assertion #5 failed ...\n";
	    message += "Remote event was never delivered.";
	    throw new TestException(message);
	}
	
	// check for extra event received
	if (arrivalTimes.length > 1) {
	    String message = "Assertion #5 failed ...\n";
	    message += "Spurious RemoteEvent(s) detected.";
	    throw new TestException(message);
	}
	
	// Check that the arrival time is in range
	idealArrival = prepareLease(set.getRenewalSetLease()).getExpiration();
	arrivalSlop = latencySlop;
	maxAllowed = idealArrival + arrivalSlop;
	actualArrivalTime = arrivalTimes[0].longValue();

	if (actualArrivalTime < maxAllowed) {
	    // we say event was received around the right time
	    logger.log(Level.FINE, "Assertion #5 passes!");
	} else {
	    /* There was a lag. This could be network problem or an
	       overloaded cpu but we will just have to assume that the
	       specification was not met. */
	    String message = "Assertion #5 failed ...\n";
	    message += "Event was not received +/- " + arrivalSlop;
	    message += " milliseconds.";
	    throw new TestException(message);
	} 
    }
} 
