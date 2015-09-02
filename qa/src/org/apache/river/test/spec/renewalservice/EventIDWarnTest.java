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

// net.jini
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.ExpirationWarningEvent;

// 
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.share.RememberingRemoteListener;
import org.apache.river.test.share.TestLease;
import org.apache.river.test.share.TestLeaseProvider;
import org.apache.river.test.share.FailingOpCountingOwner;

/**
 * Assert that because a given set will only have one expiration
 * warning event registration at a given time all expiration warning
 * events will have the same event ID.
 * 
 */
public class EventIDWarnTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The renewal set duration time 
     */
    long renewSetDur = 0;

    /**
     * Requested lease duration for the renewal set 
     */
    private final long RENEWAL_SET_LEASE_DURATION = 40 * 1000; // 40 seconds

    /**
     * Listeners of the RenewalFailureEvents 
     */
    private RememberingRemoteListener rrl = null;

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
       logger.log(Level.FINE, "EventIDWarnTest: In setup() method.");

       // capture grant time for the renewal set
       String prop = "org.apache.river.test.spec.renewalservice." +
	             "renewal_set_lease_duration";
       renewSetDur = getConfig().getLongConfigVal(prop, RENEWAL_SET_LEASE_DURATION);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }

    /**
     * Assert that because a given set will only have one expiration
     * warning event registration at a given time all expiration warning
     * events will have the same event ID.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "EventIDWarnTest: In run() method.");

	// Create three renewal sets with a lease of 40000 milliseconds
	LeaseRenewalService lrs = getLRS();
	logger.log(Level.FINE, "Creating the lease renewal set with duration" +
			  " of " + renewSetDur + " milliseconds");
	LeaseRenewalSet set01 = lrs.createLeaseRenewalSet(renewSetDur);
	set01 = prepareSet(set01);
	lrm.renewFor(prepareLease(set01.getRenewalSetLease()), renewSetDur, null);
	
	logger.log(Level.FINE, "Creating the lease renewal set with duration" +
			  " of " + renewSetDur + " milliseconds");
	LeaseRenewalSet set02 = lrs.createLeaseRenewalSet(renewSetDur);
	set02 = prepareSet(set02);
	lrm.renewFor(prepareLease(set02.getRenewalSetLease()), renewSetDur, null);

	logger.log(Level.FINE, "Creating the lease renewal set with duration" +
			  " of " + renewSetDur + " milliseconds");
	LeaseRenewalSet set03 = lrs.createLeaseRenewalSet(renewSetDur);
	set03 = prepareSet(set03);
	lrm.renewFor(prepareLease(set03.getRenewalSetLease()), renewSetDur, null);
	
	// register the listener to receive expiration events
	logger.log(Level.FINE, "Registering listener to receive " +
			  "ExpirationWarngingEvents for set #1.");
	long minWarning = renewSetDur / 3;
	logger.log(Level.FINE, "minWarning = " + minWarning + " milliseconds");
	EventRegistration reg01 = 
	    set01.setExpirationWarningListener(rrl, minWarning, null);
	reg01 = prepareRegistration(reg01);

	// validate the registration (just for grins)
	if (rstUtil.isValidExpWarnEventReg(reg01, set01) == false) {
	    String message = "Registration #1 is invalid because:\n" +
		rstUtil.getFailureReason();
	    throw new TestException(message);
	}

	logger.log(Level.FINE, "Registering listener to receive " +
			  "ExpirationWarngingEvents for set #2.");
	logger.log(Level.FINE, "minWarning = " + minWarning + " milliseconds");
	EventRegistration reg02 = 
	    set02.setExpirationWarningListener(rrl, minWarning, null);
	reg02 = prepareRegistration(reg02);

	// validate the registration (just for grins)
	if (rstUtil.isValidExpWarnEventReg(reg02, set02) == false) {
	    String message = "Registration #2 is invalid because:\n" +
		rstUtil.getFailureReason();
	    throw new TestException(message);
	}

	logger.log(Level.FINE, "Registering listener to receive " +
			  "ExpirationWarngingEvents for set #3.");
	logger.log(Level.FINE, "minWarning = " + minWarning + " milliseconds");
	EventRegistration reg03 = 
	    set03.setExpirationWarningListener(rrl, minWarning, null);
	reg03 = prepareRegistration(reg03);

	// validate the registration (just for grins)
	if (rstUtil.isValidExpWarnEventReg(reg03, set03) == false) {
	    String message = "Registration #3 is invalid because:\n" +
		rstUtil.getFailureReason();
	    throw new TestException(message);
	}

	// wait for the events to roll in ...
	rstUtil.waitForRemoteEvents(rrl, 3, renewSetDur);

	// ensure that we have exactly 3 events
	RemoteEvent[] events = rrl.getEvents();
	if (events.length != 3) {
	    String message = "Listener received " + events.length +
		" events but is required to receive exactly 3.";
	    throw new TestException(message);
	}

	// assert that each event holds the expected ID value
	for (int i = 0; i < events.length; ++i) {
	    ExpirationWarningEvent ewe = 
		(ExpirationWarningEvent) events[i];
	    if (ewe.getID() != 
		LeaseRenewalSet.EXPIRATION_WARNING_EVENT_ID) {
	    String message = "Event #" + i + " has the wrong event ID.\n" +
		"ID = " + ewe.getID() + " but it should be " +
		LeaseRenewalSet.EXPIRATION_WARNING_EVENT_ID;
	    throw new TestException(message);
	    }
	}
    }
} // EventIDWarnTest
