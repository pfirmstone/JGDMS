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

// net.jini
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.Test;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.test.share.BasicLeaseOwner;
import org.apache.river.test.share.TestLease;
import org.apache.river.test.share.TestLeaseProvider;

import java.util.logging.Level;

/**
 * Assert that the membership duration argument to the addLease method
 * is interpreted correctly.
 * 
 */
public class MembershipDurationTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     *  The owner (aka landlord) of the test leases 
     */
    private BasicLeaseOwner leaseOwner = null;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "MembershipDurationTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // create an owner to for testing definite exceptions
       leaseOwner = new BasicLeaseOwner(Lease.FOREVER);
       return this;
    }


    /**
     * Assert that the membership duration argument to the addLease method
     * is interpreted correctly.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "MembershipDurationTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	logger.log(Level.FINE, "Created Set 01 with lease duration of " +
			  "Lease.FOREVER.");

	// create a test lease to be managed
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == Lease.FOREVER");
	TestLease testLease = 
	    leaseProvider.createNewLease(leaseOwner, Lease.FOREVER);

	/* assert that using a membership duration of 0
	   does NOT generate an IllegalArgumentException */
	try {
	    set.renewFor(testLease, 0);
	    // success, keep on trucking ...
	} catch (IllegalArgumentException ex) {
	    String message = "An attempt to add a client lease\n";
	    message += " with a 0 membership duration has failed.";
	    throw new TestException(message, ex);
	}	    

	/* assert that using a membership duration of -99 (negative test)
	   does NOT generate an IllegalArgumentException */
	try {
	    set.renewFor(testLease, -99);
	    // success, keep on trucking ...	
	} catch (IllegalArgumentException ex) {
	    String message = "An attempt to add a client lease\n";
	    message += "with a negative membership duration has ";
	    message += "failed.";
	    throw new TestException(message, ex);
	}	    

	// trying to remove the lease should result in null value
	Lease managedLease = set.remove(testLease);
	if (managedLease != null) {
	    String message = "Lease was added to renewal set in error\n";
	    message += "from a call to renewFor with 0 membership.";
	    throw new TestException(message);
	}
	
	/* assert that using a membership duration of Lease.ANY
	   does NOT generate an IllegalArgumentException */
	try {
	    set.renewFor(testLease, Lease.ANY);
	    set.remove(testLease);
	    // success, keep on trucking ...
	} catch (IllegalArgumentException ex) {
	    String message = "An attempt to add a client lease\n";
	    message += "with a Lease.ANY membership duration\n";
	    message += "has failed with an IllegalArgumentException.";
	    throw new TestException(message, ex);
	}	    

	/* assert that using a membership duration of Lease.FOREVER
	   does NOT generate an IllegalArgumentException */
	try {
	    set.renewFor(testLease, Lease.FOREVER);
	    set.remove(testLease);
	    // success, keep on trucking ...
	} catch (IllegalArgumentException ex) {
	    String message = "An attempt to add a client lease\n";
	    message += "with a Lease.FOREVER membership duration\n";
	    message += "has failed with an IllegalArgumentException.";
	    throw new TestException(message, ex);
	}	    

	/* assert that using a membership duration of Long.MAX_VALUE
	   does NOT generate an IllegalArgumentException */
	try {
	    set.renewFor(testLease, Long.MAX_VALUE);
	    set.remove(testLease);
	    // success, keep on trucking ...
	} catch (IllegalArgumentException ex) {
	    String message = "An attempt to add a client lease\n";
	    message += "with a Long.MAX_VALUE membership duration\n";
	    message += "has failed with an IllegalArgumentException.";
	    throw new TestException(message, ex);
	}	    

	/* assert that using a membership duration of 1 (edge case)
	   does NOT generate an IllegalArgumentException */
	try {
	    set.renewFor(testLease, 1);
	    set.remove(testLease);
	    // success, keep on trucking ...
	} catch (IllegalArgumentException ex) {
	    String message = "An attempt to add a client lease\n";
	    message += "with a 1 millisecond membership duration\n";
	    message += "has failed with an IllegalArgumentException.";
	    throw new TestException(message, ex);
	}	    

	/* assert that using a membership duration of 30000 (normal case)
	   does NOT generate an IllegalArgumentException */
	try {
	    set.renewFor(testLease, 30000L);
	    set.remove(testLease);
	    // success, keep on trucking ...
	} catch (IllegalArgumentException ex) {
	    String message = "An attempt to add a client lease\n";
	    message += "with a 30000 millisecond membership duration\n";
	    message += "has failed with an IllegalArgumentException.";
	    throw new TestException(message, ex);
	}	    
    }
} // <XXXclass_nameXXX>
