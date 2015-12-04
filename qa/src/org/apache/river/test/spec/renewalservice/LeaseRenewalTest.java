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
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

// 
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.Test;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.test.share.RememberingRemoteListener;
import org.apache.river.test.share.TestLease;
import org.apache.river.test.share.TestLeaseProvider;
import org.apache.river.test.share.FailingOpCountingOwner;

/**
 * Assert that if the rewnewal service was able to renew the lease
 * before the event occured the lease's expiration will reflect the
 * result of the last successful renewal call.
 * 
 */
public class LeaseRenewalTest extends AbstractLeaseRenewalServiceTest {
    
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
    private FailingOpCountingOwner owner = null;

    /**
     *  The LeaseRenewalManager used for LRS impls that grant only short leases
     */
    private LeaseRenewalManager lrm = null;

    /**
     * The original expiration of the test lease 
     */
    private long initialExpiration = 0;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "LeaseRenewalTest: In setup() method.");

       // object from which test leases are obtained
       leaseProvider = new TestLeaseProvider(1);

       // capture the renewal time
       String property = "org.apache.river.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create an owner for the lease that will throw a definite exception
       // create an owner to for testing definite exceptions
       Exception except = new IllegalArgumentException("LeaseRenewalTest");
       owner = new FailingOpCountingOwner(except, 1, renewGrant);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }


    /**
     * Assert that if the rewnewal service was able to renew the lease
     * before the event occured the lease's expiration will reflect the
     * result of the last successful renewal call.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "LeaseRenewalTest: In run() method.");

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
	Lease passFailLease = 
	    leaseProvider.createNewLease(owner, 
					 rstUtil.durToExp(renewGrant));
	initialExpiration = passFailLease.getExpiration();
	set.renewFor(passFailLease, Lease.FOREVER);

	// wait for the failure event to roll in ...
	rstUtil.waitForRemoteEvents(rrl, 1, renewGrant * 2);

	// we should have a failure event on record
	Long[] arrivalTimes = rrl.getArrivalTimes();
	RemoteEvent[] events = rrl.getEvents();
	if (events.length != 1 || arrivalTimes.length != 1) {
	    String message = "Listener received " + events.length +
		" events but is required to receive exactly 1.";
	    throw new TestException(message);
	}

	/* Assert that 
	   1) the expiration time of the lease whose renewal
	      attempt failed, has an expiration time greater than 
	      the original expiration time.
	   2) and greater than the arrival time of the renewal 
	      failure event 
	   3) and less than the expiration time that would have been 
	      granted had the renewal succeeded. */
	RenewalFailureEvent rfe = (RenewalFailureEvent) events[0];
	Lease managedLease = rfe.getLease();
	long currentExpiration = managedLease.getExpiration();
	long arrTime = arrivalTimes[0].longValue();
	boolean assert01 = currentExpiration > initialExpiration;
	boolean assert02 = currentExpiration > arrTime;
	boolean assert03 = currentExpiration < (arrTime + renewGrant);
	boolean correctExpiration = assert01 && assert02 && assert03;
	if (! correctExpiration) {
	    String message = "The lease expiration time does not reflect" +
		" the result of the last successful renewal call because";
	    if (assert01 == false || assert02 == false) {
		message += " the lease was never successfully renewed.";
	    }
	    if (assert03 == false) {
		message += " the lease was renewed in spite of the " +
		    "renewal failure.";
	    }

	    throw new TestException(message);
	}
    }
} // LeaseRenewalTest













