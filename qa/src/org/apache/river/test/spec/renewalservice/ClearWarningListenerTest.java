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
import java.rmi.NoSuchObjectException;

// net.jini
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.share.RenewingRemoteListener;

/**
 * Assert that the clearExpirationWarningListener method operates as
 * expected. Assert that this method may be called multiple times with
 * no ill effect.
 * 
 */
public class ClearWarningListenerTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Requested lease duration for the renewal set 
     */
    private final long MIN_WARNING_DEFAULT = 20 * 1000; // 20 seconds

    /**
     * listener that will log events as they arrive 
     */
    private RenewingRemoteListener rrl = null;

    /**
     * the duration used for renewals of renewal set leases 
     */
    private long renewSetDur = 0;

    /**
     * the minimum warning time for ExpirationWarningEvents 
     */
    private long minWarning = 0;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "ClearWarningListenerTest: In setup() method.");

       // minimum warning time required for test
       String prop = "org.apache.river.test.spec.renewalservice.minWarning";
       minWarning = getConfig().getLongConfigVal(prop, MIN_WARNING_DEFAULT);

       // renewal value of set (relative to the minWarning)
       renewSetDur = minWarning * 3;

       // logs events as they arrive and renews the lease
       rrl = new RenewingRemoteListener(getExporter(), renewSetDur);
       return this;
    }


    /**
     * Assert that the clearExpirationWarningListener method operates as
     * expected. Assert that this method may be called multiple times with
     * no ill effect.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "ClearWarningListenerTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set for the requested duration
	logger.log(Level.FINE, "Creating renewal set w/ duration = " +
			  renewSetDur + " milliseconds.");
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);

	// register listener #1 to receive events
	logger.log(Level.FINE, "Registering listener for warning events.");
	logger.log(Level.FINE, "minWarning = " + minWarning + ".");
	set.setExpirationWarningListener(rrl, minWarning, null);

	// sleep the length of the lease duration
	rstUtil.waitForLeaseExpiration(prepareLease(set.getRenewalSetLease()), 
				       "for renewal set to be renewed.");
	
	/* by now it should have been renewed exactly once.
	   clear the listener multiple times to ensure no ill effect. */
	try {
	    for (int i = 0; i < 10; ++i) {
		set.clearExpirationWarningListener();
	    }
	} catch (Exception ex) {
	    String message = "Multiple calls to " +
		"clearExpirationWarningListener raises an exception.";
	    throw new TestException(message, ex);
	}

	RemoteEvent[] events = rrl.getEvents();
	if (events.length < 1) {
	    String message = "Listener did not receive any events.\n" +
		"Should have received exactly one.";
	    throw new TestException(message);
	} else if (events.length > 1) {
	    String message = "Listener received " + events.length +
		"events.\n" + "Should have received exactly one.";
	    throw new TestException(message);
	}

	// There is only one event received, now allow lease to expire
	Lease setLease = rrl.getLastLeaseRenewed();
	rstUtil.waitForLeaseExpiration(setLease,
				       "for renewal set to expire.");
	
	// lease should now be expired, prove it ...
	try {
	    set.setExpirationWarningListener(rrl, minWarning, null);
	    String message = "The set's lease did not expire as expected.";
	    throw new TestException(message);
	    // we should not get to this place
	} catch (NoSuchObjectException ex) {
	    // we are golden if we get to here so just keep on trucking ...
	    logger.log(Level.FINE, "Renewal set has expired as expected.");
	}	    

	// presumably there has only been one call to renew
	events = rrl.getEvents();
	if (events.length > 1) {
	    String message = "Listener has renewed the lease" + 
		events.length + "times.\n" + 
		"Should have renewed it exactly one.";
	    throw new TestException(message);
	}
    }
} // ClearWarningListenerTest
