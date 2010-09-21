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
import net.jini.core.event.EventRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.FailingOpCountingOwner;

/**
 * Assert that because a given set will only have one renewal
 * failure event registration at a given time all renewal failure
 * events will have the same event ID.
 * 
 */
public class EventIDFailTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 30 * 1000; // 30 seconds

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * Listeners of the RenewalFailureEvents 
     */
    private RememberingRemoteListener rrl = null;

    /**
     * Owner (aka Landlord) of the leases 
     */
    private FailingOpCountingOwner failingOwner = null;

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
       logger.log(Level.FINE, "AssocRenewalFailSetTest: In setup() method.");

       // object from which test leases are obtained
       leaseProvider = new TestLeaseProvider(3);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create an owner for the lease that will throw a definite exception
       String testName = "AssocRenewalFailSetTest";
       Exception except = new IllegalArgumentException(testName);
       failingOwner = new FailingOpCountingOwner(except, 0, renewGrant);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
    }


    /**
     * Assert that because a given set will only have one expiration
     * warning event registration at a given time all expiration warning
     * events will have the same event ID.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "EventIDFailSetTest: In run() method.");

	// get a lease renewal set w/ duration for as long as we can
	logger.log(Level.FINE, "Creating the lease renewal set with duration" +
			  " of Lease.FOREVER");
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), Lease.FOREVER, null);

	// register a listener to receive renewal failure events
	logger.log(Level.FINE, "Registering listener to receive " +
			  "RenewalFailureEvents.");
	EventRegistration reg = set.setRenewalFailureListener(rrl, null);
	reg = prepareRegistration(reg);

	// validate the registration (just for grins)
	if (rstUtil.isValidRenewFailEventReg(reg, set) == false) {
	    String message = "Registration is invalid because:\n" +
		rstUtil.getFailureReason();
	    throw new TestException(message);
	}

	// create the three leases to be managed
	logger.log(Level.FINE, "Creating lease #1 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease01 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	set.renewFor(lease01, Long.MAX_VALUE);

	logger.log(Level.FINE, "Creating lease #2 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease02 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	set.renewFor(lease02, Long.MAX_VALUE);

	logger.log(Level.FINE, "Creating lease #3 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease03 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	set.renewFor(lease03, Long.MAX_VALUE);

	// sleep until the leases have expired.
	rstUtil.waitForRemoteEvents(rrl, 3, renewGrant * 2);

	// ensure that we have exactly 3 events
	RemoteEvent[] events = rrl.getEvents();
	if (events.length != 3) {
	    String message = "Listener received " + events.length +
		" events but is required to receive exactly 3.";
	    throw new TestException(message);
	}

	// assert that each event holds the expected ID value
	for (int i = 0; i < events.length; ++i) {
	    RenewalFailureEvent rfe = (RenewalFailureEvent) events[i];
	    if (rfe.getID() != 
		LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID) {
	    String message = "Event #" + i + " has the wrong event ID.\n" +
		"ID = " + rfe.getID() + " but it should be " +
		LeaseRenewalSet.RENEWAL_FAILURE_EVENT_ID;
	    throw new TestException(message);
	    }
	}
    }
} // EventIDFailTest
