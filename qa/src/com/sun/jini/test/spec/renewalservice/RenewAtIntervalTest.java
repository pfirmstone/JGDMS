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

package com.sun.jini.test.spec.renewalservice;

import java.util.logging.Level;

// net.jini
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.OpCountingOwner;
import com.sun.jini.test.share.RememberingRemoteListener;

/**
 * Assert that the renewFor/3 method causes leases to be renewed with
 * a value that is equal to the lease duration specified in the call
 * (3rd argument).
 * 
 */
public class RenewAtIntervalTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * Listeners of the RenewalFailureEvents 
     */
    private RememberingRemoteListener rrl = null;

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     */
    private DeterminingOwner[] shortGrantor = new DeterminingOwner[3];
    private DeterminingOwner[] exactGrantor = new DeterminingOwner[3];
    private DeterminingOwner[] longGrantor = new DeterminingOwner[3];

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 30 * 1000; // 30 seconds

    /**
     * The initial duration time interval between leases
     */
    private long renewDelta = 0;

    /**
     * The default value renewDelta
     */
    private final long DEFAULT_RENEW_DELTA = 30 * 1000; // 30 seconds

    /*
     * The renewal interval requested in the renewFor/3 call 
     */
    private long renewDuration = 0;

    /**
     * The short membership duration 
     */
    private long shortMembership = 0;

    /**
     *  The LeaseRenewalManager used for LRS impls that grant only short leases
     */
    private LeaseRenewalManager lrm = null;

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "RenewAtIntervalTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(9);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // capture the delta renewal time
       property = "com.sun.jini.test.spec.renewalservice.renewDelta";
       renewDelta = getConfig().getLongConfigVal(property, DEFAULT_RENEW_DELTA);

       // calculate the short membership duration
       shortMembership = renewDelta * 3 * 2;

       // calculate the renewal duration (between exactGrantor and longGrantor)
       renewDuration = ((renewGrant * 2) + (renewGrant * 3)) / 2;
       logger.log(Level.FINE, "renewDuration = " + renewDuration);

       // create an owner for each lease
       for (int i = 0; i < 3; ++i) {
	   shortGrantor[i] = new DeterminingOwner(renewGrant / 2, 
						  renewDuration);
	   exactGrantor[i] = new DeterminingOwner(renewGrant,
						  renewDuration);
	   longGrantor[i] =  new DeterminingOwner(renewGrant * 2,
						  renewDuration);
       }

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
    }

    /**
     * Assert that the renewFor/3 method causes leases to be renewed with
     * a value that is equal to the lease duration specified in the call
     * (3rd argument).
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "RenewAtIntervalTest: In run() method.");

	// get a lease renewal set w/ a fixed expiration time
	logger.log(Level.FINE, "Creating the lease renewal set.");
	logger.log(Level.FINE, "Duration = Lease.FOREVER");
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), Lease.FOREVER, null);
	
	// register listener to receive events
	logger.log(Level.FINE, "Registering listener for renewal failure" +
			  " events.");
	set.setRenewalFailureListener(rrl  , null);

	// create 9 leases (3 for each type of owner)
	TestLease[] shortLease = new TestLease[3];
	TestLease[] exactLease = new TestLease[3];
	TestLease[] longLease = new TestLease[3];
	long duration = renewGrant;
	for (int i = 0; i < 3; ++i) {

	    logger.log(Level.FINE, "Creating the short owner lease #" + i + 
			      " to be managed.");
	    logger.log(Level.FINE, "Duration = " + duration);
	    shortLease[i] = 
		leaseProvider.createNewLease(shortGrantor[i], 
					     rstUtil.durToExp(duration));

	    logger.log(Level.FINE, "Creating the exact owner lease #" + i + 
			      " to be managed.");
	    logger.log(Level.FINE, "Duration = " + duration);
	    exactLease[i] = 
		leaseProvider.createNewLease(exactGrantor[i], 
					     rstUtil.durToExp(duration));

	    logger.log(Level.FINE, "Creating the long owner lease #" + i + 
			      " to be managed.");
	    logger.log(Level.FINE, "Duration = " + duration);
	    longLease[i] = 
		leaseProvider.createNewLease(longGrantor[i], 
					     rstUtil.durToExp(duration));

	    duration += renewDelta;
	}

	/* add all leases to the renewal set. The leases belonging to the
	   shortGrantor will terminate before the renewal set lease 
	   expires. */
	for (int i = 0; i < 3; ++i) {

	    logger.log(Level.FINE, "Adding short owner lease #" + i + 
			      " to renewal set.\n" +
			      "Renew duration = " + renewDuration + 
			      " milliseconds.\n" +
			      "Membership = Long.MAX_VALUE");
	    set.renewFor(shortLease[i], shortMembership, renewDuration);

	    logger.log(Level.FINE, "Adding exact owner lease #" + i + 
			      " to renewal set.\n" +
			      "Renew duration = " + renewDuration + 
			      " milliseconds.\n" +
			      "Membership = Long.MAX_VALUE");
	    set.renewFor(exactLease[i], Long.MAX_VALUE, renewDuration);

	    logger.log(Level.FINE, "Adding long owner lease #" + i + 
			      " to renewal set.\n" +
			      "Renew duration = " + renewDuration + 
			      " milliseconds.\n" +
			      "Membership = Long.MAX_VALUE");
	    set.renewFor(longLease[i], Long.MAX_VALUE, renewDuration);

	}

	// sleep past the short membership duration
	long sleepTime = shortMembership + (shortMembership / 4);
	rstUtil.sleepAndTell(sleepTime, 
			     "For short client leases to expire.");

	// grab all the remaining leases and assert that only 6 are left
	Lease[] leases = set.getLeases();
	if (leases.length != 6) {
	    String message = "After set lease expiration, the set " +
		"contains " + leases.length + " leases, but " +
		"is expected to contain exactly 6.";
	    throw new TestException(message);
	}

	// assert that they are the leases we expect
	for (int i = 0; i < 3; ++i) {
	    int leaseIndex = rstUtil.indexOfLease(exactLease[i], leases);
	    if (leaseIndex < 0) {
		String message = "Exact owner lease #" + i + 
		    " has expired unexpectedly.";
		throw new TestException(message);
	    }

	    leaseIndex = rstUtil.indexOfLease(longLease[i], leases);
	    if (leaseIndex < 0) {
		String message = "Long owner lease #" + i + 
		    " has expired unexpectedly.";
		throw new TestException(message);
	    }
	}
	
	/* assert that all the last renewal request durations made on
	   the shortGrantor were for less than the requested amount.
	   This is because the membership duration should have run out
	   approximately 2/3 of the way through the test. */
	for (int i = 0; i < 3; ++i) {	
	    if (shortGrantor[i].durationRequestIsSmallerThan() == false) {
		String message = "The LRS did not respect the " +
		    "membership duration value when requesting a client " +
		    "lease renewal";
		throw new TestException(message);
	    }
	}
	
	/* assert that all the last renewal request durations made
	   on the exactGrantor and longGrantor were for exactly
	   the requested amount.  This is because the membership
	   duration should have run out after the lease of the renewal
	   set expires */
	for (int i = 0; i < 3; ++i) {	

	    if (exactGrantor[i].hasSpecialState() == true) {
		String message = "The LRS did not request the interval " +
		    "as specified by the client.\n" + 
		    "Detail: " + exactGrantor[i].getStateText();
		throw new TestException(message);
	    }

	    if (longGrantor[i].hasSpecialState() == true) {
		String message = "The LRS did not request the interval " +
		    "as specified by the client.\n" + 
		    "Detail: " + longGrantor[i].getStateText();
		throw new TestException(message);
	    }

	}

	/* If we received any RenewalFailureEvents that would be
	   an error */
	RemoteEvent[] events = rrl.getEvents();
	if (events.length != 0) {
	    String message = "Listener received " + events.length +
		" events but is required to receive exactly 0.\n" +
		"This indicates that LRS did not respect the renewal" +
		" duration given by the short grantor.";
	    throw new TestException(message);
	}
    }

    /**
     * Determines whether the LRS has performed a bad action
     * leases.
     */
    class DeterminingOwner extends OpCountingOwner {

	/**
	 * flags that indicate interest state (if any)
	 */
	private boolean durationRequestTooLarge = false;
	private boolean durationRequestIsSmallerThan = false;
	private long durationRequestSmallerCount = 0;
	
	/**
	 * Constructor requiring
	 * <OL>
	 * <LI>a maximum time to grant for lease renewals</LI>
	 * <LI>the expected renewal grant request</LI>
	 * </OL>
	 * 
	 * @param maxGrant  the maximum time to grant for lease renewals.
	 * @param expectedDuration the duration that is expected to be 
	 *                         asked for.
	 * 
	 */
	public DeterminingOwner(long maxGrant, long expectedDuration) {
	    super(maxGrant);
	}

	// inherit the javadoc from parent class
	public Object renew(long extension) {

	    durationRequestIsSmallerThan = (extension < renewDuration);
	    if (durationRequestIsSmallerThan) {
		++durationRequestSmallerCount;
	    }

	    durationRequestTooLarge  = 
		(extension > renewDuration) || durationRequestTooLarge;

	    return super.renew(extension);
	}

	// inherit the javadoc from parent class
	public long batchRenew(long extension) {

	    durationRequestIsSmallerThan = (extension < renewDuration);
	    if (durationRequestIsSmallerThan) {
		++durationRequestSmallerCount;
	    }

	    durationRequestTooLarge  = 
		(extension > renewDuration) || durationRequestTooLarge;

	    return super.batchRenew(extension);
	}

	/**
	 * Query if the duration request was less than request interval
	 * on the last call to renew/batchRenew.
	 * 
	 */
	public boolean durationRequestIsSmallerThan() {
	    return durationRequestIsSmallerThan;
	}

	/**
	 * Query if the duration request was ever less than request interval.
	 * 
	 */
	public boolean durationRequestWasSmallerThan() {
	    return durationRequestSmallerCount > 1;
	}

	/**
	 * Query if the duration request was larger than requested interval
	 * 
	 */
	public boolean isDurationRequestTooLarge() {
	    return durationRequestTooLarge;
	}

	/**
	 * Query for any special conditions
	 * 
	 */
	public boolean hasSpecialState() {
	    return isDurationRequestTooLarge() || 
		   durationRequestIsSmallerThan() ||
		   durationRequestWasSmallerThan();
	}

	/**
         * has error condition
	 */
	public boolean hasAnErrorCondition() {
	    return isDurationRequestTooLarge() || 
		   durationRequestIsSmallerThan() ||
		   durationRequestWasSmallerThan();
	}

	/**
	 * Get a string explaining any unusal state
	 * 
	 */
	public String getStateText() {

	    String message = "No exception state noted.";
	    if (isDurationRequestTooLarge()) {
		message = "Renewal extension was greater than the " +
		    "requested renewal interval.";
	    }

	    if (durationRequestIsSmallerThan()) {
		message = "Renewal extension was smaller than the " +
		    "requested renewal interval just before expiration.";
	    }

	    if (durationRequestWasSmallerThan()) {
		message = "Renewal extension was smaller than the " +
		    "requested renewal interval sometime before expiration.";
	    }

	    return message;
	}

    }

} // RenewAtIntervalTest.java


