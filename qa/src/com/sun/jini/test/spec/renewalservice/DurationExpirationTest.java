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
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.share.BasicLeaseOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

/**
 * Assert that of the membership duration of a managed lease is less
 * than its expiration time then the lease will not be renewed but it
 * will remain in the set.
 * 
 */
public class DurationExpirationTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     */
    private BasicLeaseOwner leaseOwner = null;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 60 * 1000; // 30 seconds

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
       logger.log(Level.FINE, "DurationExpirationTest: In setup() method.");

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // create an owner for the lease
       leaseOwner = new BasicLeaseOwner(renewGrant);

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }

    /**
     * Assert that of the membership duration of a managed lease is less
     * than its expiration time then the lease will not be renewed but it
     * will remain in the set.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "DurationExpirationTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), renewSetDur, null);
	logger.log(Level.FINE, "Created renewal set with lease duration of " +
			  "Lease.FOREVER.");

	// create a test lease to be managed
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(leaseOwner, 
					 rstUtil.durToExp(renewGrant));
	long originalExpTime = testLease.getExpiration();

	// start managing the lease 
	long membershipDur = renewGrant / 2;
	logger.log(Level.FINE, "Adding lease with membership of " +
			  membershipDur + " milliseconds.");
	long startTrip = System.currentTimeMillis();
	set.renewFor(testLease, membershipDur);
	long endTrip = System.currentTimeMillis();
	// widen roundtrip to eliminate intermittent failures
	long roundTrip = (endTrip - startTrip) * 10;

	// sleep until the membership duration almost expires
	rstUtil.sleepAndTell(membershipDur * 2 / 3,
			     "2/3 membership duration.");

	// assert that the lease has not been removed from the set
	Lease[] leaseArray = set.getLeases();

	if (leaseArray.length < 1) {
	    String message = "The lease was removed from the renewal set" +
		" prematurely.";
	    throw new TestException(message);
	}

	if (leaseArray.length > 1) {
	    String message = "Error in test: More than one lease was" +
		"added to the lease renewal set.";
	    throw new TestException(message);
	}

	// expiration time must remain unaltered
	long currentExpTime = leaseArray[0].getExpiration();
	long deltaExpTime = currentExpTime - originalExpTime;

	if (deltaExpTime > roundTrip) {
	    String message = "The expiration of the lease has been altered."
		+ "\n   originalExpTime = " + originalExpTime 
		+ "\n   currentExpTime = " + currentExpTime
		+ "\n   deltaExpTime = " + deltaExpTime
		+ "\n   roundTrip = " + roundTrip;
	    throw new TestException(message);
	}

	// sleep again so that the test lease expires
	rstUtil.sleepAndTell(membershipDur / 3,
			     "1/3 membership duration time.");

	// assert that the lease was removed from the set
	leaseArray = set.getLeases();
	if (leaseArray.length != 0) {
	    String message = "The lease was not removed from the renewal" +
		" set as expected.";
	    throw new TestException(message);
	}
    }
} // DurationExpirationTest
