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

// java.rmi
import java.rmi.RemoteException;

// net.jini
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.FailingOpCountingOwner;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

/**
 * Asserts that an event get delivered for an indefinite exception
 * only after the expiration time of the client lease.
 * 
 */
public class RenewalFailureIndefiniteTest 
    extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The default value time for which client leases are renewed
     */
    private final long DEFAULT_RENEW_GRANT = 30 * 1000; // 30 seconds

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
    private FailingOpCountingOwner indefiniteOwner = null;

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
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "RenewalFailureIndefiniteTest: In setup() method.");

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // create an owner to for testing definite exceptions
       Exception indefiniteException = 
	   new RemoteException("RenewalFailureIndefiniteTest");
       indefiniteOwner = 
	   new FailingOpCountingOwner(indefiniteException, 0, renewGrant);
    }

    /**
     * Asserts that an event get delivered for an indefinite exception
     * only after the expiration time of the client lease.
     * 
     * @return the test Status (passed or failed)
     * 
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "RenewalFailureIndefiniteTest: In run() method.");
	// service under test
	LeaseRenewalService lrs = getLRS();

	// create a lease renewal set that hangs around a long time
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	logger.log(Level.FINE, "Created Set with lease duration of " +
			  "Lease.FOREVER.");

	// register listener to receive events
	EventRegistration evReg = set.setRenewalFailureListener(rrl, null);
	evReg = prepareRegistration(evReg);

	// check event registration (not formally part of this test)
	if (rstUtil.isValidRenewFailEventReg(evReg, set) == false) {
	    String message = "Registration is invalid because:\n" +
		rstUtil.getFailureReason();
	    throw new TestException(message);
	}

	/* create a lease that will fail to renew with an indefinite
	   exception */
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(indefiniteOwner, 
					 rstUtil.durToExp(renewGrant));

	// start managing the lease for as long as we can
	logger.log(Level.FINE, "Adding managed lease to lease renewal set.");
	set.renewFor(testLease, Long.MAX_VALUE);
	
	// wait for the event to arrive
	long timeout = renewGrant * 2;
	boolean gotEvent = rstUtil.waitForRemoteEvents(rrl, 1, timeout);
	if (gotEvent == false) {
	    String message = "RenewalFailureEvent was not received\n" +
		"within the " + timeout + " millisecond window.";
	    throw new TestException(message);
	}

	/* Assert that the notification was received after
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

	/* TESTING ASSERTION
	   was the event received after lease expiration? */
	long leaseExpiration = testLease.getExpiration();

	long actualArrivalTime = arrivalTimes[0].longValue();

	// events should not arrive before expiration
	if (actualArrivalTime < leaseExpiration) {
	    // apparently some sort of clock skew or spurious event.
	    String message = "Assertion failed ...\n";
	    message += "Event was received before client lease";
	    message += " expiration time.";
	    throw new TestException(message);
	}
    }
} // RenewalFailureIndefiniteTest













