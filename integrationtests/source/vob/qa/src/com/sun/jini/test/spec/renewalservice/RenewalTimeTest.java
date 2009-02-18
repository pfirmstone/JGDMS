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
import com.sun.jini.test.share.OpCountingOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

/**
 * Assert that the expiration time of a returned lease reflects either
 * the original time or the time set as a result of the last
 * successful renewal.
 * 
 */
public class RenewalTimeTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     */
    private OpCountingOwner leaseOwner = null;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 60 * 1000; // 60 seconds

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
       logger.log(Level.FINE, "RemoveCancelLease: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(2);

       // create an owner for the lease
       leaseOwner = new OpCountingOwner(renewGrant);

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
    }

    /**
     * Assert that the expiration time of a returned lease reflects either
     * the original time or the time set as a result of the last
     * successful renewal.
     * 
     * @return the test Status (passed or failed)
     * 
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "RenewalTimeTest: In run() method.");

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
	logger.log(Level.FINE, "Duration == " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(leaseOwner, 
					 rstUtil.durToExp(renewGrant));
	long originalExpTime = testLease.getExpiration();

	// start managing the lease 
	logger.log(Level.FINE, "Adding lease with membership of " +
			  "Lease.FOREVER");
	long time01 = System.currentTimeMillis();
	set.renewFor(testLease, Lease.FOREVER);

	// Assert that the managed lease has original expiration time
	Lease managedLease = set.remove(testLease);
	long time02 = System.currentTimeMillis();
	long actualExpTime = managedLease.getExpiration();
	long deltaExpTime = actualExpTime - originalExpTime;
	long roundTrip = time02 - time01;
	if (deltaExpTime >= roundTrip) {
	    String message = "Expiration time was permaturely altered.";
	    throw new TestException(message);
	}

	// just to make certain assert that there are no renew or cancels
	if (leaseOwner.getRenewCalls() > 0) {
	    String message = "LRS made an erronous call to renew.";
	    throw new TestException(message);
	}
	if (leaseOwner.getCancelCalls() > 0) {
	    String message = "LRS made a forbidden call to cancel.";
	    throw new TestException(message);
	}

	// create a test lease to be managed
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == " + renewGrant);
	long leaseCreation = System.currentTimeMillis();
	testLease = 
	    leaseProvider.createNewLease(leaseOwner, 
					 rstUtil.durToExp(renewGrant));
	originalExpTime = testLease.getExpiration();

	// start managing the lease 
	long membershipDuration = renewGrant + (renewGrant / 2);
	logger.log(Level.FINE, "Adding lease with membership of " +
			  membershipDuration + " millseconds.");
	set.renewFor(testLease, membershipDuration);

	// wait for client lease to become renewed
	rstUtil.waitForLeaseExpiration(testLease,
				       "for client lease to renew.");

	// remove the lease to prevent any further action
	managedLease = set.remove(testLease);

	/* By now the lease has been renewed (exactly) once. 
	   To show that the expiration time of the lease reflects
	   the time set as a result of the last successful renew,
	   we will show that the renewal time is between the time
	   the test lease was originally created and its original
	   expiration time.
	    T0---------------T1----------T2-------------T3
	     \lease          \renewal    \original      \current
	      creation        time        expriation     expiration
	      
	    Calculate T1 (T3-renewalGrant) and show that T0 < T1 < T2 */
	long renewalTime = managedLease.getExpiration() - renewGrant;
	boolean inRange = renewalTime > leaseCreation &&
			  renewalTime < originalExpTime;
	if (inRange == false) {
	    String message = "Lease expiration does not reflect the\n" +
			     "latest successful renewal.";
	    throw new TestException(message);
	}
    }
} // <XXXclass_nameXXX>













