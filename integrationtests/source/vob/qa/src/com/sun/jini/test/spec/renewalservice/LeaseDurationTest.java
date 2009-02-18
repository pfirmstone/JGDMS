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


/**
 * Asserts that for a range of lease duration values given to the
 * createLeaseRenewalSet method, the LRS will only grant times less
 * than or equal to the amount requested.
 * 
 */
public class LeaseDurationTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The number of lease values to be tested 
     */
    private final int NUMBER_OF_LEASES = 10;

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "LeaseDurationTest: In setup() method.");

    }


    /**
     * Asserts that for a range of lease duration values given to the
     * createLeaseRenewalSet method, the LRS will only grant times less
     * than or equal to the amount requested.
     * 
     * @return the test Status (passed or failed)
     * 
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "LeaseDurationTest: In run() method.");

	// get a lease renewal set w/ duration for as long as we can
	logger.log(Level.FINE, "Creating the lease renewal set.");
	LeaseRenewalService lrs = getLRS();

	// start with 1 and increase duration requests by power of 10

	// test 1000 - 10 ^ 9
	for (int i = 3; i < NUMBER_OF_LEASES; i++) {
	    long duration = (long) Math.pow(10, i); 
	    logger.log(Level.FINE, "Create renewal set with duration of " +
			      duration);
	    // short time in the past before creation
	    long preLease = System.currentTimeMillis();
	    LeaseRenewalSet set = lrs.createLeaseRenewalSet(duration);
	    set = prepareSet(set);
	    long now = System.currentTimeMillis();
	    long expTime = now + duration;
	    long leaseExpTime = prepareLease(set.getRenewalSetLease()).getExpiration();

	    // It is impossible to calc. expTime exactly, there is slop
	    logger.log(Level.FINE, "Calculated lease expiration ==> " + 
			      expTime);
	    logger.log(Level.FINE, "Actual lease expiration ======> " + 
			      leaseExpTime);
	    logger.log(Level.FINE, "slop =========================> " +
			      (expTime - leaseExpTime));

	    // lease must not be in the past
	    if (leaseExpTime < preLease) {
		logger.log(Level.FINE, "lease duration = " + duration);
		logger.log(Level.FINE, "leaseExpTime = " + leaseExpTime);
		logger.log(Level.FINE, "less than now = " + now);
		String message = 
		    "LRS granted a lease for a renewal set" +
		    " with an expiration in the past.";
		throw new TestException(message);
	    }

	    // lease expiration must be less or equal to calculated
	    if (prepareLease(set.getRenewalSetLease()).getExpiration() > expTime) {
		String message = 
		    "LRS granted a lease for a renewal set" +
		    " with an expiration time greater\n" +
		    "than requested.";
		throw new TestException(message);
	    } 
	}	    

	// test Lease.ANY
	logger.log(Level.FINE, "Create lease with duration of " +
			  "Lease.ANY");
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.ANY);
	set = prepareSet(set);
	long now = System.currentTimeMillis();
	long anyExp = prepareLease(set.getRenewalSetLease()).getExpiration();
	if ( anyExp < now) {
	    logger.log(Level.FINE, "Now        = " + now);
	    logger.log(Level.FINE, "Expiration = " + anyExp);
	    String message = "LRS granted a time in the past when";
	    message += " given Lease.ANY as duration request.";
	    throw new TestException(message);
	}

	// test Lease.FOREVER
	logger.log(Level.FINE, "Create lease with duration of " +
			  "Lease.FOREVER");
	set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	now = System.currentTimeMillis();
	long foreverExp = prepareLease(set.getRenewalSetLease()).getExpiration();
	if ( foreverExp < now) {
	    logger.log(Level.FINE, "Now        = " + now);
	    logger.log(Level.FINE, "Expiration = " + anyExp);
	    String message = "LRS granted a time in the past when";
	    message += " given Lease.FORVER as duration request.";
	    throw new TestException(message);
	}
    }
} // LeaseDurationTest



