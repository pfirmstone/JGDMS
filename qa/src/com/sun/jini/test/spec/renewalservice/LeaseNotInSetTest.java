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
import com.sun.jini.qa.harness.Test;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.test.share.BasicLeaseOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;


/**
 * Assert that the remove method returns null when the lease is not in the set.
 * 
 */
public class LeaseNotInSetTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     *  The owner (aka landlord) of the test leases 
     */
    private BasicLeaseOwner leaseOwner = null;

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
       logger.log(Level.FINE, "LeaseNotInSetTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // create an owner to for testing definite exceptions
       leaseOwner = new BasicLeaseOwner(Lease.FOREVER);

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }


    /**
     * Assert that the remove method returns null when the lease is
     * not in the set.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "LeaseNotInSetTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set
	logger.log(Level.FINE, "Creating renewal set with lease duration of " +
			  "Lease.FOREVER.");
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), renewSetDur, null);

	// create a test lease to be managed
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == Lease.FOREVER");
	TestLease testLease = 
	    leaseProvider.createNewLease(leaseOwner, Lease.FOREVER);

	// remove the lease
	Lease managedLease = set.remove(testLease);
	if (managedLease != null) {
	    String message = "Removal of non-managed lease does NOT\n" +
			     "return null as required.";
	    throw new TestException(message);
	}
    }
} // LeaseNotInSetTest
