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
import com.sun.jini.qa.harness.Test;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.OpCountingOwner;

/**
 * Assert that if the rewnewal service was unable to call renew before the
 * lease expired, then the Throwable returned in the event object is null.
 * 
 */
public class IndefiniteRenewalTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 10 * 1000; // 10 seconds

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
    private OpCountingOwner owner = null;

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
       logger.log(Level.FINE, "IndefiniteRenewalTest: In setup() method.");

       // object from which test leases are obtained
       leaseProvider = new TestLeaseProvider(1);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create an owner for the lease that will throw a definite exception
       // create an owner to for testing definite exceptions
       Exception except = new IllegalArgumentException("IndefiniteRenewalTest");
       owner = new OpCountingOwner(renewGrant);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }


    /**
     * Assert that if the rewnewal service was unable to call renew before the
     * lease expired, then the Throwable returned in the event object is null.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "IndefiniteRenewalTest: In run() method.");

	// get a lease renewal set w/ duration for as long as we can
	logger.log(Level.FINE, "Creating the lease renewal set with duration" +
			  " of Lease.FOREVER");
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), Lease.FOREVER, null);

	// register listener to receive events
	logger.log(Level.FINE, "Registering listener for renewal failure" +
			  " events.");
	set.setRenewalFailureListener(rrl  , null);

	// create the lease to be managed
	logger.log(Level.FINE, "Creating lease with duration of " +
			  renewGrant + " milliseconds.");
	Lease testLease = 
	    leaseProvider.createNewLease(owner, 
					 rstUtil.durToExp(renewGrant));

	// wait for the lease to expire
	rstUtil.waitForLeaseExpiration(testLease,
				       "for client lease to expire.");

	/* this should trigger an immediate renewal failure event.
	   (implementation dependent? I think so.) */
	set.renewFor(testLease, Lease.FOREVER);

	// wait for an event to arrive
	rstUtil.waitForRemoteEvents(rrl, 1, 30000L);

	// assert we received exactly one event
	RemoteEvent[] events = rrl.getEvents();
	if (events.length != 1) {
	    String message = "Listener received " + events.length +
		" events but is required to receive exactly 1.";
	    throw new TestException(message);
	}

	// the encapsulated Throwable should be null
	RenewalFailureEvent rfe = (RenewalFailureEvent) events[0];
	Throwable except = rfe.getThrowable();
	if (except != null) {
	    String message = "The event encapsulated a Throwable of " +
		"type " + except.getClass() + " but should have returned" +
		" a value of null.";
	    throw new TestException(message);
	}
    }
}
