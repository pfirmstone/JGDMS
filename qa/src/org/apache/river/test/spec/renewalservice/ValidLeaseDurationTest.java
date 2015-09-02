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

import java.util.logging.Level;

/**
 * ValidLeaseDurationTest asserts that the Add and Remove methods operate
 * according to specification.
 * 
 */
public class ValidLeaseDurationTest extends AbstractLeaseRenewalServiceTest {
    
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
       logger.log(Level.FINE, "ValidLeaseDurationTest: In setup() method.");
       return this;
    }


    /**
     * This method asserts that the Add and Remove methods operate
     * according to specification.
     * 
     * <P>Notes:<BR>For more information see the LRS specification 
     * section 9.3 page 109-110.</P>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "ValidLeaseDurationTest: In run() method.");

	
	// IllegalArgumentException should be thrown with arg of -99
	logger.log(Level.FINE, "Creating the lease renewal set with " + 
			       "-99 duration.");
	try {
	    LeaseRenewalService lrs = getLRS();
	    LeaseRenewalSet set = lrs.createLeaseRenewalSet(-99);
	    set = prepareSet(set);
	    String message = "IllegalArgumentException expected from\n";
	    message += "createLeaseRenewalSet(-99)";
	    throw new TestException(message);
	} catch (IllegalArgumentException ex) {
	    // we passed so just continue
	    logger.log(Level.FINE, "Caught IllegalArgumentException as " +
			      "expected.");
	}
	
	// No exception thrown for Lease.ANY argument
	logger.log(Level.FINE, "Creating the lease renewal set with " + 
			       "Lease.ANY duration.");
	try {
	    LeaseRenewalService lrs = getLRS();
	    LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.ANY);
	    set = prepareSet(set);
	    // we passed so just continue
	    logger.log(Level.FINE, "Caught no exceptions as expected.");
	} catch (IllegalArgumentException ex) {
	    String message = "IllegalArgumentException caught from\n";
	    message += "createLeaseRenewalSet(Lease.ANY)";
	    throw new TestException(message, ex);
	}

	// No exception thrown for Lease.FOREVER argument
	logger.log(Level.FINE, "Creating the lease renewal set with " + 
			  "Lease.FOREVER duration.");
	try {
	    LeaseRenewalService lrs = getLRS();
	    LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	    set = prepareSet(set);
	    // we passed so just continue
	    logger.log(Level.FINE, "Caught no exceptions as expected.");
	} catch (IllegalArgumentException ex) {
	    String message = "IllegalArgumentException caught from\n";
	    message += "createLeaseRenewalSet(Lease.FOREVER)";
	    throw new TestException(message, ex);
	}

	// No exception thrown for argument value of 0
	logger.log(Level.FINE, "Creating the lease renewal set with " + 
			  "0 duration.");
	try {
	    LeaseRenewalService lrs = getLRS();
	    LeaseRenewalSet set = lrs.createLeaseRenewalSet(0);
	    set = prepareSet(set);
	    // we passed so just continue
	    logger.log(Level.FINE, "Caught no exceptions as expected.");
	} catch (IllegalArgumentException ex) {
	    String message = "IllegalArgumentException caught from\n";
	    message += "createLeaseRenewalSet(0)";
	    throw new TestException(message, ex);
	}

	// No exception thrown for argument value of 1
	logger.log(Level.FINE, "Creating the lease renewal set with " + 
			  "1 duration.");
	try {
	    LeaseRenewalService lrs = getLRS();
	    LeaseRenewalSet set = lrs.createLeaseRenewalSet(1);
	    set = prepareSet(set);
	    // we passed so just continue
	    logger.log(Level.FINE, "Caught no exceptions as expected.");
	} catch (IllegalArgumentException ex) {
	    String message = "IllegalArgumentException caught from\n";
	    message += "createLeaseRenewalSet(1)";
	    throw new TestException(message, ex);
	}
    }
} // ValidLeaseDurationTest


