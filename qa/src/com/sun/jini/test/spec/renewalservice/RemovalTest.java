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

// 
import com.sun.jini.qa.harness.TestException;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// com.sun.jini
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.OpCountingOwner;

/**
 * RemovalTest asserts that the removal of a lease actually results in
 * it being removed (according to the remove method) and no further
 * action is taken on the lease for a period of up to and including
 * its expiration time plus one half.
 * 
 */
public class RemovalTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     */
    private OpCountingOwner owner = null;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 60 * 1000; // 60 seconds

    /**
     * The maximum time granted for the set renewal set's lease
     */
    private long setLeaseGrant = 0;

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "RemovalTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create an owner for the lease
       owner = new OpCountingOwner(renewGrant);

       // calculate the renewal time for the renewal set's lease
       setLeaseGrant = renewGrant * 5 / 3;

    }

    /**
     * This method asserts that the removal of a lease actually results in
     * it being removed (according to the remove method) and no further
     * action is taken on the lease for a period of up to and including
     * its expiration time plus one half.
     * 
     * <P>Notes:<BR>For more information see the LRS specification 
     * section 9.3 page 108.</P>
     * 
     * @return the test Status (passed or failed)
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "RemovalTest: In run() method.");

	// get a lease renewal set w/ duration of setLeaseGrant
	logger.log(Level.FINE, "Creating the lease renewal set.");
	logger.log(Level.FINE, "Duration = " + setLeaseGrant + 
			  " milliseconds.");
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(setLeaseGrant);
	set = prepareSet(set);
	
	// get a lease for some time less than the renewal set
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration = " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(owner, 
					 rstUtil.durToExp(renewGrant));

	// start managing the lease for as long as we can
	logger.log(Level.FINE, "Adding managed lease to lease renewal set.");
	set.renewFor(testLease, Long.MAX_VALUE);
	
	// Remove the lease. Removal should succeed.
	logger.log(Level.FINE, "Removing the managed lease from the set.");
	Lease managedLease = set.remove(testLease);
	if (managedLease == null) {
	    String message = "Lease could not be removed from\n";
	    message += "renewal set.";
	    throw new TestException(message);
	}

	// wait the lease to expire
	boolean isExpired  = 
	    rstUtil.waitForLeaseExpiration(testLease, 
					   "for client lease to expire.");
	if (isExpired == false) {
	    rstUtil.waitForLeaseExpiration(testLease, 
					   "a second time " +
					   "for client lease to expire.");
	}

	// assert that renew was never called on the lease
	logger.log(Level.FINE, "Checking # of calls to renew.");
	if (owner.getRenewCalls() > 0) {
	    String message = "An invalid call to renew was made on \n";
	    message += "a lease that was not in a renewal set.";
	    throw new TestException(message);
	}

	logger.log(Level.FINE, "Checking # of calls to batch renew.");
	if (owner.getRenewCalls() > 0) {
	    String message = "An invalid call to batchRenew was made\n" +
			     "on \n" + "a lease that was not in a" +
		"renewal set.";
	    throw new TestException(message);
	}

	// assert that cancel was never called on the lease
	logger.log(Level.FINE, "Checking # of calls to cancel.");
	if (owner.getCancelCalls() > 0) {
	    String message = "An invalid call to cancel was made on \n";
	    message += "a lease that was not in a renewal set.";
	    throw new TestException(message);
	}

	// also just to be certain check that the lease is expired.
	if (rstUtil.isExpired(testLease) == false) {
	    String message = "Lease did not expire as expected.\n";
	    message += "The renew method was somehow called in error.";
	    throw new TestException(message);
	}
    }
} // RemovalTest


