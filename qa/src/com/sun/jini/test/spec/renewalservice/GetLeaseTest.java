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

/**
 * Assert that the getLease method returns the lease of the set itself.
 * 
 */
public class GetLeaseTest extends AbstractLeaseRenewalServiceTest {
    
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
       logger.log(Level.FINE, "GetLeaseTest: In setup() method.");

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
    }


    /**
     * Assert that the getLease method returns the lease of the set itself.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "GetLeaseTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set
	long renewSetDur = 30000L; // 30 second lease
	logger.log(Level.FINE, "Creating renewal set with lease duration of " +
			  renewSetDur + " milliseconds.");
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), renewSetDur, null);

	// grab two instances of the renewal set lease
	Lease lease01 = prepareLease(set.getRenewalSetLease());
	Lease lease02 = prepareLease(set.getRenewalSetLease());

	// they should be the same
	if (lease01.equals(lease02) == false) {
	    String message = "GetRenewalSetLease has returned an\n" +
			     "invalid lease.";
	    throw new TestException(message);
	}

	/* okay they are the same lease but are they the correct lease??
	   well, we can't be sure but we can check to make sure that the
	   lease will expire in at least setRenewDur time. */
	logger.log(Level.FINE, "Removing lease from the LRM.");
	lrm.remove(lease01);
	lease01 = prepareLease(set.getRenewalSetLease());
	rstUtil.sleepAndTell(renewSetDur, "for client lease to expire.");
	if (rstUtil.isExpired(lease01) == false) {
	    String message = "getRenewalSetLease seems to have\n" +
			     "returned an invalid lease based upon\n" +
			     "the expiration time.";
	    throw new TestException(message);
	}
    }
} 
