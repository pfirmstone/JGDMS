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
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.BasicLeaseOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

/**
 * Assert that the getLeases method returns all the client leases in
 * the set at the time of the call as an array of type Lease.
 */ 
public class GetLeasesTest extends AbstractLeaseRenewalServiceTest {
    
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
    private final long DEFAULT_RENEW_GRANT = 30 * 1000; // 30 seconds

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
       logger.log(Level.FINE, "GetLeasesTest: In setup() method.");

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(3);

       // create an owner for the lease
       leaseOwner = new BasicLeaseOwner(renewGrant);

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
    }

    /**
     * Assert that the getLeases method returns all the client leases in
     * the set at the time of the call as an array of type Lease.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "GetLeasesTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set
	logger.log(Level.FINE, "Creating renewal set with lease duration of " +
			  "Lease.FOREVER.");
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), renewSetDur, null);

	// create a test lease to be managed and add to renewal set
	Lease testLease[] = new Lease[3];
	for (int i = 0; i < 3; ++i) {
	    logger.log(Level.FINE, "Creating the lease to be managed.");
	    logger.log(Level.FINE, "Duration == " + renewGrant);
	    testLease[i] = leaseProvider.createNewLease
		(leaseOwner, rstUtil.durToExp(renewGrant));

	    long membershipDur = renewGrant * (i+1);
	    // start managing the lease 
	    logger.log(Level.FINE, "Adding lease with membership of " +
			      membershipDur + " milliseconds.");
	    set.renewFor(testLease[i], membershipDur);
	}

	// take a snap shot of the leases (should have them all)
	Lease[] leaseArray01 = set.getLeases();

	// sleep until the membership expiration ends for lease #1
	rstUtil.sleepAndTell(renewGrant,
			     "for membership duration on lease #1 " +
			     "to expire.");

	// take another snap shot (all but #1 should be present)
	Lease[] leaseArray02 = set.getLeases();

	// sleep again so that that lease #2 membership expires
	rstUtil.sleepAndTell(renewGrant,
			     "for membership duration on lease #2 " +
			     "to expire.");

	// take another snap shot (only #3 should be left)
	Lease[] leaseArray03 = set.getLeases();

	// sleep again so that that lease #3 membership expires
	rstUtil.sleepAndTell(renewGrant,
			     "for membership duration on lease #3 " +
			     "to expire.");

	// take another snap shot (the array should be empty)
	Lease[] leaseArray04 = set.getLeases();

	// assert that array #1 has all three leases
	int numberOfLeases = leaseArray01.length;
	if (numberOfLeases != 3) {
	    String message = "Lease array #1 should contain 3 leases" +
		" but instead contains " + numberOfLeases + " leases.";
	}

	// ensure that the leases are unique
	for (int i = 0; i < 3; ++i) {
	    for (int j = i+1; j < 3; ++j) {
		if (leaseArray01[i].equals(leaseArray01[j])) {
		    String message = "Lease array elements [" + i + "]" +
			" and [" + j + "] are equal in array #1.";
		    throw new TestException(message);
		}
	    }
	}

	// assert that the leases are the orginal ones
	for (int i = 0; i < 3; ++i) {
	    if (rstUtil.indexOfLease(testLease[i], leaseArray01) == -1) {
		String message = "Test lease #" + i + " is missing from" +
		    " the array #1 returned by getLeases().";
		throw new TestException(message);
	    }
	}

	// assert that array #2 has only two leases
	numberOfLeases = leaseArray02.length;
	if (numberOfLeases != 2) {
	    String message = "Lease array #2 should contain 2 leases" +
		" but instead contains " + numberOfLeases + " leases.";
	    throw new TestException(message);
	}

	// ensure that the leases are unique
	if (leaseArray02[0].equals(leaseArray02[1])) {
	    String message = "The two leases in array #2 are the same.";
	    throw new TestException(message);
	}

	// assert that the leases are the orginal ones
	for (int i = 1; i < 3; ++i) {
	    if (rstUtil.indexOfLease(testLease[i], leaseArray02) == -1) {
		String message = "Test lease #" + i + " is missing from" +
		    " the array #2 returned by getLeases().";
		throw new TestException(message);
	    }
	}

	// assert that array #3 has only one lease
	numberOfLeases = leaseArray03.length;
	if (numberOfLeases != 1) {
	    String message = "Lease array #3 should contain 1 lease" +
		" but instead contains " + numberOfLeases + " leases.";
	    throw new TestException(message);
	}

	/* since there is only one, no need to
	   ensure that the leases are unique. */


	// assert that the lease is the orginal one
	if (rstUtil.indexOfLease(testLease[2], leaseArray03) == -1) {
		String message = "Test lease #3 is missing from" +
		    " the array #3 returned by getLeases().";
		throw new TestException(message);
	    }

	// lastly just ensure that array #4 is empty
	if (leaseArray04.length != 0) {
	    String message = "Array #4 should be empty but instead\n" +
		"it contains " + leaseArray04.length + " leases.";
		throw new TestException(message);
	}
    }
} // GetLeasesTest


