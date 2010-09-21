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
 * Assert that if a lease is added to a renewal set with a membership
 * duration of Long.MAX_VALUE then its membership expiration is
 * Long.MAX_VALUE. The serial form for the lease is ABSOLUTE so that
 * clock skew between systems doesn't cause the expiration time of the
 * lease to be altered.
 * 
 */
public class MaxMembershipTest extends AbstractLeaseRenewalServiceTest {
    
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
       logger.log(Level.FINE, "RenewalTimeTest: In setup() method.");

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // Announce where we are in the test
       logger.log(Level.FINE, "MaxMembershipTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // create an owner for the lease
       leaseOwner = new BasicLeaseOwner(Long.MAX_VALUE);

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
    }


    /**
     * Assert that if a lease is added to a renewal set with a membership
     * duration of Long.MAX_VALUE then its membership expiration is
     * Long.MAX_VALUE.
     */
    public void run() throws Exception {

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
	testLease.setSerialFormat(Lease.ABSOLUTE);
	long originalExpTime = testLease.getExpiration();

	// start managing the lease 
	logger.log(Level.FINE, "Adding lease with membership of " +
			  "Long.MAX_VALUE");
	set.renewFor(testLease, Long.MAX_VALUE);

	// sleep until the test lease expires
	long sleepTime = renewGrant * 2;
	logger.log(Level.FINE, "Sleeping for " + sleepTime + " milliseconds");
	Thread.sleep(sleepTime);

	// assert that the current expiration time is Long.MAX_VALUE
	Lease managedLease = set.remove(testLease);
	long currentExpTime = managedLease.getExpiration();
	if (currentExpTime != Long.MAX_VALUE) {
	    String message = "The current expiration time of the lease" +
		" is " + currentExpTime + " but should be Long.MAX_VALUE" +
		" (" + Long.MAX_VALUE + ").";
	    throw new TestException(message);
	}
    }
} 
