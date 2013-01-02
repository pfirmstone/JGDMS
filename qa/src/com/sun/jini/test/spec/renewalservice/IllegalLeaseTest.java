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
import com.sun.jini.qa.harness.Test;

// com.sun.jini.qa
import com.sun.jini.test.share.BasicLeaseOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;

import java.util.logging.Level;


/**
 * Assert that attempts to add a non-expired set lease into a set from the same
 * service throws an IllegalArgumentException.  Adding the same lease to more
 * than one set should succeed.
 * 
 */
public class IllegalLeaseTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The default value time for which client leases are renewed
     */
    private final long DEFAULT_RENEW_GRANT = 3600 * 1000; // 1 hour

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     *  The owner (aka landlord) of the test leases 
     */
    private BasicLeaseOwner leaseOwner = null;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * Sets up the testing environment.
     */
    public Test construct(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);

       // Announce where we are in the test
       logger.log(Level.FINE, "IllegalLeaseTest: In setup() method.");

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // create an owner to for testing definite exceptions
       leaseOwner = new BasicLeaseOwner(renewGrant);
       return this;
    }


    /**
     * Assert that attempts to add a non-expired set lease into a set from the
     * same service throws an IllegalArgumentException.  Adding the same lease
     * to more than one set should succeed.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "IllegalLeaseTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create 2 renewal sets
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set01 = lrs.createLeaseRenewalSet(renewSetDur);
	set01 = prepareSet(set01);
	logger.log(Level.FINE, "Created Set 01 with lease duration of " +
			  "Lease.FOREVER.");
	LeaseRenewalSet set02 = lrs.createLeaseRenewalSet(renewSetDur);
	set02 = prepareSet(set02);
	logger.log(Level.FINE, "Created Set 02 with lease duration of " +
			  "Lease.FOREVER.");

	/* assert that attempting to add the set's lease to itself
	   generates an IllegalArgumentException */
	Lease setLease = prepareLease(set01.getRenewalSetLease());
	logger.log(Level.FINE, "Lease is " + setLease);
	try {
	    set01.renewFor(setLease, renewGrant);
	    String message = "An attempt to add a renewal set's lease\n";
	    message += " to itself has succeeded.";
	    throw new TestException(message);
	} catch (IllegalArgumentException ex) {
	    // success, keep on trucking ...
	}	    

	/* assert that attempting to add the set01's lease to set02
	   generates an IllegalArgumentException */
	try {
	    set02.renewFor(setLease, renewGrant);
	    String message = "An attempt to add a renewal set's lease\n";
	    message += " to another set has succeeded.";
	    throw new TestException(message);
	} catch (IllegalArgumentException ex) {
	    // success, keep on trucking ...
	}	    

	// create a lease that renews normally
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(leaseOwner, 
					 rstUtil.durToExp(renewGrant));

	// add the lease to set01
	set01.renewFor(testLease, renewGrant);

	/* assert that attempting to add the test lease to set02
	   succeeds. */
	try {
	    set02.renewFor(testLease, renewGrant);
	    // success
	} catch (IllegalArgumentException ex) {
	    String message = "An attempt to add a client lease\n";
	    message += " to two different sets has failed.";
	    throw new TestException(message, ex);
	}	    

	/*
	 * Assert that adding set01's lease to set02 succeeds if set01's
	 * lease has been cancelled.
	 */
	setLease.cancel();
	try {
	    set02.renewFor(setLease, renewGrant);
	} catch (IllegalArgumentException ex) {
		throw new TestException("Failed to add an expired set "
					+ "lease to another set", 
					ex);
	}
    }
} 
