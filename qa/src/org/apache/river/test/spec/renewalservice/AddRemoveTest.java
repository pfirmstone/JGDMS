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

// 
import org.apache.river.qa.harness.TestException;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// org.apache.river
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.share.TestLease;
import org.apache.river.test.share.TestLeaseProvider;

/**
 * AddRemoveTest asserts that the Add and Remove methods operate
 * according to specification.
 * 
 */
public class AddRemoveTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "AddRemoveTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);
       return this;
    }


    /**
     * This method asserts that the Add and Remove methods operate
     * according to specification.
     * 
     * <P>Notes:<BR>For more information see the LRS specification 
     * section 9.3 page 109-110.</P>
     * 
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "AddRemoveTest: In run() method.");

	// get a lease renewal set w/ duration for as long as we can
	logger.log(Level.FINE, "Creating the lease renewal set.");
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	
	// get a lease for 2 hours (that ought to suffice).
	logger.log(Level.FINE, "Creating the lease to be managed.");
	long duration = 120 * 60 * 1000;
	TestLease testLease = 
	    leaseProvider.createNewLease(rstUtil.durToExp(duration));

	// start managing the lease for as long as we can
	logger.log(Level.FINE, "Adding managed lease to lease renewal set.");
	set.renewFor(testLease, Lease.FOREVER);
	
	// remove the lease and make certain it is the same one we added
	logger.log(Level.FINE, "Removing the managed lease from the set.");
	Lease managedLease = set.remove(testLease);
	if (managedLease.equals(testLease) == false) {
	    String message = "Remove failed to return the lease whose\n";
	    message += "removal was requested.";
	    throw new TestException(message);
	}

	// assuming a successful remove it better be gone now ...
	logger.log(Level.FINE, "Checking that removal was successful.");
	Lease duplicateLease = set.remove(testLease);
	if (duplicateLease != null) {
	    String message = "Removal of the same lease was\n";
	    message += "successfully performed twice.";
	    throw new TestException(message);
	}
    }
} 


