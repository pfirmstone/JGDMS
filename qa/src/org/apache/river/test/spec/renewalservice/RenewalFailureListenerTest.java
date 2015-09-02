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
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.Test;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.test.share.FailingOpCountingOwner;
import org.apache.river.test.share.RememberingRemoteListener;
import org.apache.river.test.share.TestLease;
import org.apache.river.test.share.TestLeaseProvider;

/**
 * Asserts the following from the LRS spec.<P>
 * <OL> 
 * <LI>An event gets delivered to a RemoteEventListener when a lease 
 *     expires before its membership duration runs out.</LI> 
 * <LI>The handback in the RenewalFailureEvent is rational.</LI>
 * <LI>The lease for the EventRegistration is the same as the set's lease.</LI>
 * <LI>The listener must be non-null.</LI>
 * </OL>
 * 
 */
public class RenewalFailureListenerTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The default value time for which client leases are renewed
     */
    private final long DEFAULT_RENEW_GRANT = 60 * 1000; // 60 seconds

    /**
     * Max time allowed for a network transmission
     */
    long latencySlop = 0;

    /**
     * Time to allow for network lag
     */
    private final long DEFAULT_SLOP = 30 * 1000;

    /**
     * Percentage + allowed for slop
     */
    private final int SLOP_PERCENT_TOLERANCE = 10;

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     */
    private FailingOpCountingOwner definiteOwner = null;

    /**
     * listener that will log events as they arrive 
     */
    private RememberingRemoteListener rrl = null;

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     *  The LeaseRenewalManager used for LRS impls that grant only short leases
     */
    private LeaseRenewalManager lrm = null;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "RenewalFailureListenerTest: In setup() method.");

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // capture the renewal time
       String property = "org.apache.river.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // create an owner to for testing definite exceptions
       Exception definiteException = 
	   new IllegalArgumentException("RenewalFailureListenerTest");
       definiteOwner = 
	   new FailingOpCountingOwner(definiteException, 0, renewGrant);

       // capture the time allowed for network transfer
       String prop = "org.apache.river.test.spec.renewalservice latencySlop";
       latencySlop = getConfig().getLongConfigVal(prop, DEFAULT_SLOP);

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }

    /**
     * Asserts the following from the LRS spec.<P>
     * <OL> 
     * <LI>An event gets delivered to a RemoteEventListener when a lease 
     *     expires before its membership duration runs out.</LI> 
     * <LI>The handback in the RenewalFailureEvent is rational.</LI>
     * <LI>The lease for the EventRegistration is the same as the set's 
     *     lease.</LI>
     * <LI>The listener must be non-null.</LI>
     * </OL>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "In run() method.");

	// get the instance of the LRS proxy
	LeaseRenewalService lrs = getLRS();

	// create a lease renewal set that hangs around a long time
	logger.log(Level.FINE, "Creating Set with lease duration of " +
			  "Lease.FOREVER.");
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), Long.MAX_VALUE, null);

	// create a handback object
	MarshalledObject handback = new MarshalledObject(new Integer(99));

	// register listener to receive events
	EventRegistration evReg = 
	    set.setRenewalFailureListener(rrl, handback);
	evReg = prepareRegistration(evReg);

	// create a lease that will fail to renew
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(definiteOwner, 
					 rstUtil.durToExp(renewGrant));

	// start managing the lease for as long as we can
	logger.log(Level.FINE, "Adding managed lease to lease renewal set.");
	logger.log(Level.FINE, "Membership = Lease.FOREVER.");
	set.renewFor(testLease, Lease.FOREVER);
	
	// wait for the lease to expire
	rstUtil.waitForLeaseExpiration(testLease,
				       "for client lease to expire.");

	/* Assert that the notification was received at approximately
	   the lease expiration time */
	Long[] arrivalTimes = rrl.getArrivalTimes();

	// must at lease have received one event
	if (arrivalTimes.length == 0) {
	    String message = "RenewalFailure event never received.";
	    throw new TestException(message);
	}

	// must NOT have received more than one event
	if (arrivalTimes.length > 1) {
	    String message = "Too many events received.";
	    throw new TestException(message);
	}

	/* TESTING ASSERTION #1
	   was the event received around the right time? */
	long leaseExpiration = testLease.getExpiration();
	long maxAllowed = leaseExpiration + latencySlop;

	long actualArrivalTime = arrivalTimes[0].longValue();

	// event must arrive within our assumed constraints
	if (actualArrivalTime < maxAllowed) {
	    // we say event was received around the right time
	    logger.log(Level.FINE, "Assertion #1 passes!");
	} else {
	    /* There was a lag. This could be a network problem or an
	       overloaded cpu but we will just have to assume that the
	       specification was not met. */
	    String message = "Assertion #1 failed ...\n" +
		"Event was not received within " + latencySlop +
		" milliseconds of client lease expiration.";
	    throw new TestException(message);
	} 

	/* TESTING ASSERTION #2
	   the handback object is the one we expect. */
	RemoteEvent[] events = rrl.getEvents();
	MarshalledObject mObj = events[0].getRegistrationObject();
	if (handback.equals(mObj) == false) {
	    String message = "Assertion #2 failed ...\n" +
		"Handback object does not match original.";
	    throw new TestException(message);
	}
	logger.log(Level.FINE, "Assertion #2 passes!");
	
	/* TESTING ASSERTION #3
	   The lease for the event registration is the same as the event's
	   lease */
	if (rstUtil.isValidRenewFailEventReg(evReg, set) == false) {
	    String message = "Assertion #3 failed ...\n" +
		"Event Registration is invalid." +
		rstUtil.getFailureReason();
	    throw new TestException(message);
	}
	logger.log(Level.FINE, "Assertion #3 passes!");

	/* TESTING ASSERTION #4
	   a null listener results in a NullPointException. */
	set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	logger.log(Level.FINE, "Created Set with lease duration of " +
			  "Lease.FOREVER.");
	try {
	    evReg = set.setRenewalFailureListener(null, handback);
	    evReg = prepareRegistration(evReg);
	    if (rstUtil.isValidExpWarnEventReg(evReg, set) == false) {
		String message = "Assertion #4 failed ...\n" +
		    "Event Registration is invalid.";
		throw new TestException(message);
	    }

	    String message = "Assertion #4 failed ...\n" +
		"Registration of null listener was allowed.";
	    throw new TestException(message);

	} catch (NullPointerException ex) {
	    // success, continue with the rest of the test
	    logger.log(Level.FINE, "Assertion #4 passes!");
	}
    }
} // RenewalFailureListenerTest
