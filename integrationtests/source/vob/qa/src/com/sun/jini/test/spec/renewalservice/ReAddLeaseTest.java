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
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.BasicLeaseOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

/**
 * Assert that when a lease is added to a set in which it already
 * resides that the membership duration is updated appropriately.
 * 
 */
public class ReAddLeaseTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * the starting membership duration value for the lease.
     * subsequent duration values are relative to this one.
     */
    private static long MEMBERSHIP_DURATION = 20 * 1000; // 20 seconds

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     *  The owner (aka landlord) of the test leases 
     */
    private BasicLeaseOwner leaseOwner = null;

    /**
     * the base membership duration (starting point) 
     */
    private long membershipDuration = 0;

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "ReAddLeaseTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // create an owner to for testing definite exceptions
       leaseOwner = new BasicLeaseOwner(Lease.FOREVER);
       
       // capture the base (minimum) membership duration value
       String property = "com.sun.jini.test.spec.renewalservice." +
	                 "membershipduration";
       membershipDuration = 
	   getConfig().getLongConfigVal(property, MEMBERSHIP_DURATION);

    }

    /**
     * Assert that when a lease is added to a set in which it already
     * resides that the membership duration is updated appropriately.
     * 
     * @return the test Status (passed or failed)
     * 
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "ReAddLeaseTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	logger.log(Level.FINE, "Created renewal set with lease duration of " +
			  "Lease.FOREVER.");

	// create a test lease to be managed
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == Lease.FOREVER");
	TestLease testLease = 
	    leaseProvider.createNewLease(leaseOwner, Lease.FOREVER);

	// start managing the lease for the base membership time
	logger.log(Level.FINE, "Adding lease with membership of " +
			  membershipDuration + " milliseconds");
	set.renewFor(testLease, membershipDuration);

	// wait for 1/2 the membership time
	long sleepTime = membershipDuration / 2;
	rstUtil.sleepAndTell(sleepTime, "1/2 lease membership duration.");

	// now add the lease again doubling the membership duration
	long doubleDuration = membershipDuration * 2;
	logger.log(Level.FINE, "Adding lease with membership of " +
			  doubleDuration + " milliseconds");
	set.renewFor(testLease, doubleDuration);

	// sleep for 3/4 of the doubled membershipDuration
	sleepTime = doubleDuration * 3 / 4;
	rstUtil.sleepAndTell(sleepTime, "3/4 lease membership duration.");

	/* if the membership duration was honored we should be able
	   to remove the lease */
	Lease managedLease = set.remove(testLease);
	if (managedLease == null) { // new membership was not honored
	    String message = "The membership duration was apparently ";
	    message += "not updated to the longer value\n";
	    message += "when the lease was re-added to the set.";
	    throw new TestException(message);
	}

	/* now assert that a shorter time will also be honored.
	   start managing the lease for the base membership time
	   again */
	logger.log(Level.FINE, "Adding lease with membership of " +
			  doubleDuration + " milliseconds");
	set.renewFor(testLease, doubleDuration);
	logger.log(Level.FINE, "Re-adding lease with membership of " +
			  membershipDuration + " milliseconds");
	set.renewFor(testLease, membershipDuration);
	
	// sleep 1.5 times the membership to ensure expiration
	sleepTime = membershipDuration + (membershipDuration / 2);
	rstUtil.sleepAndTell(sleepTime, "1.5 X membership duration.");

	/* if the membership duration was honored we should NOT be able
	   to remove the lease because its membership has expired */
	managedLease = set.remove(testLease);
	if (managedLease != null) { // new membership was not honored
	    String message = "The membership duration was apparently\n";
	    message += "updated to a value that was longer than\n";
	    message += "the base membership duration when the lease\n";
	    message += "was re-added to the set.";
	    throw new TestException(message);
	}
    }
} // <XXXclass_nameXXX>













