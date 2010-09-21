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
import com.sun.jini.test.share.LeaseOwner;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.OpCountingOwner;

/**
 * ExpireRemoveTest asserts a lease whose membership expiration has
 * passed is removed from the set and that no further attempts to
 * renew the lease are made for at least half the lease duration time,
 * after the lease has expired.
 * 
 */
public class ExpireRemoveTest extends AbstractLeaseRenewalServiceTest {
    
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
    private final long DEFAULT_RENEW_GRANT = 2 * 60 * 1000; // 2 minutes

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "ExpireRemoveTest: In setup() method.");

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(1);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create an owner for the lease
       owner = new OpCountingOwner(renewGrant);

    }

    /**
     * This method asserts a lease whose membership expiration has
     * passed is removed from the set and that no further attempts to
     * renew the lease are made for at least half the lease duration time,
     * after the lease has expired.
     * 
     * <P>Notes:<BR>For more information see the LRS specification 
     * section 9.3 page 108.</P>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "ExpireRemoveTest: In run() method.");

	// get a lease renewal set w/ duration for as long as we can
	logger.log(Level.FINE, "Creating the lease renewal set.");
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	
	// get a lease for 2 hours (that ought to suffice).
	logger.log(Level.FINE, "Creating the lease to be managed.");
	logger.log(Level.FINE, "Duration == " + renewGrant);
	TestLease testLease = 
	    leaseProvider.createNewLease(owner, 
					 rstUtil.durToExp(renewGrant));

	// start managing the lease for only half of its grant time
	logger.log(Level.FINE, "Adding managed lease to lease renewal set.");
	long membership = renewGrant / 2;
	logger.log(Level.FINE, "Membership duration == " + membership);
	set.renewFor(testLease, membership);
	
	// sleep for 3/4 of the renewGrant time
	long sleeptime = renewGrant * 3 / 4;
	logger.log(Level.FINE, "Waiting " + sleeptime + " milliseconds for " +
			  "membership duration to expire.");
	Thread.sleep(sleeptime);

	// attempt to remove the lease. Removal should fail.
	logger.log(Level.FINE, "Removing the managed lease from the set.");
	Lease managedLease = set.remove(testLease);
	logger.log(Level.FINE, "Lease ==> " + managedLease);
	if (managedLease != null) {
	    String message = "Lease was not removed after membership\n";
	    message += "duration expired.";
	    throw new TestException(message);
	}

	// ensure that the initial grant for the lease has expired
	long extraTime = 30 * 1000;
	sleeptime = renewGrant - sleeptime + extraTime;
	logger.log(Level.FINE, "Waiting " + sleeptime + " milliseconds for " +
			  "membership duration to expire.");
	Thread.sleep(sleeptime);

	// assert that renew was never called on the lease
	logger.log(Level.FINE, "Checking # of calls to renew. Should be 0.");
	if (owner.getRenewCalls() > 0) {
	    String message = "An invalid call to renew was made on \n";
	    message += "a lease whose set membership has expired.";
	    throw new TestException(message);
	}
    }
} // ExpireRemoveTest
