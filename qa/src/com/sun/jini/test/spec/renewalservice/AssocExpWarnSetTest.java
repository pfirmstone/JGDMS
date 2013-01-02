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
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.FailingOpCountingOwner;

/**
 * Assert that the set returned as the source of the ExpirationWarningEvent is
 * the set which is about to expire.
 * 
 */
public class AssocExpWarnSetTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The renewal set duration time 
     */
    long renewSetDur = 0;

    /**
     * Requested lease duration for the renewal set 
     */
    private final long RENEWAL_SET_LEASE_DURATION = 30 * 1000; // 60 seconds

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
    public Test construct(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);

       // Announce where we are in the test
       logger.log(Level.FINE, "AssocExpWarnTest: In setup() method.");

       // capture grant time for the renewal set
       String prop = "com.sun.jini.test.spec.renewalservice." +
	             "renewal_set_lease_duration";
       renewSetDur = getConfig().getLongConfigVal(prop, RENEWAL_SET_LEASE_DURATION);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }

    /**
     * Assert that the set returned as the source of the
     * ExpirationWarningEvent is the set which is about to expire.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "AssocExpWarnTest: In run() method.");

	// Create two renewal sets with a lease of forever
	LeaseRenewalService lrs = getLRS();
	logger.log(Level.FINE, "Creating the lease renewal set #1 " +
			  "with duration of Lease.FOREVER");
	LeaseRenewalSet set01 = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set01 = prepareSet(set01);
	
	logger.log(Level.FINE, "Creating the lease renewal set #2 with " +
			  "duration of Lease.FOREVER");
	LeaseRenewalSet set02 = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set02 = prepareSet(set02);
	
	// create a set with a lease of 30 milliseconds
	logger.log(Level.FINE, "Creating the lease renewal set #3 with " +
			  "duration of " + renewSetDur + " milliseconds.");
	LeaseRenewalSet set03 = lrs.createLeaseRenewalSet(renewSetDur);
	set03 = prepareSet(set03);
	
	// register listener to receive expiration warning events
	long minWarning = renewSetDur / 3;
	logger.log(Level.FINE, "Registering listener for expiration" +
			  " warning events.");
	logger.log(Level.FINE, "minWarning = " + minWarning + " milliseconds");
	set01.setExpirationWarningListener(rrl, minWarning, null);
	set02.setExpirationWarningListener(rrl, minWarning, null);
	set03.setExpirationWarningListener(rrl, minWarning, null);

	// wait for the lease on set03 to expire
	rstUtil.waitForRemoteEvents(rrl, 1, renewSetDur);

	// assert we received exactly one event
	RemoteEvent[] events = rrl.getEvents();
	if (events.length != 1) {
	    String message = "Listener received " + events.length +
		" events but is required to receive exactly 1.";
	    throw new TestException(message);
	}

	// assert that the source is the one that we expect
	LeaseRenewalSet expiredSet = 
	    (LeaseRenewalSet) events[0].getSource();
	if (expiredSet.equals(set03) == false) {
	    String message = "The source of the event was not the set" +
		" that caused the ExpirationWarningEvent.";
	    throw new TestException(message);
	}
    }
} // AssocExpWarnSetTest













