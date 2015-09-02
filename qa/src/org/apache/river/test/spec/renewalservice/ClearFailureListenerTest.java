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
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.share.RememberingRemoteListener;
import org.apache.river.test.share.TestLease;
import org.apache.river.test.share.TestLeaseProvider;
import org.apache.river.test.share.FailingOpCountingOwner;

/**
 * Assert that the clearRenewalFailureListener method operates as
 * expected. Asset that this method may be called multiple times with
 * no ill effect.
 * 
 */
public class ClearFailureListenerTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 30 * 1000; // 30 seconds

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider shortLeaseProvider = null;
    private TestLeaseProvider longLeaseProvider = null;

    /**
     * Listeners of the RenewalFailureEvents 
     */
    private RememberingRemoteListener rrl = null;

    /**
     * Owner (aka Landlord) of the leases 
     */
    private FailingOpCountingOwner owner = null;

    /**
     *  The LeaseRenewalManager used for LRS impls that grant only short leases
     */
    private LeaseRenewalManager lrm = null;

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "ClearFailureListenerTest: In setup() method.");

       // object from which test leases are obtained
       shortLeaseProvider = new TestLeaseProvider(3);
       longLeaseProvider = new TestLeaseProvider(3);

       // capture the renewal time
       String property = "org.apache.river.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create owners for the leases that will throw a definite exception
       Exception ex = new IllegalArgumentException("ClearFailureListenerTest");
       owner = new FailingOpCountingOwner(ex, 0, renewGrant);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }


    /**
     * Assert that the clearRenewalFailureListener method operates as
     * expected. Asset that this method may be called multiple times with
     * no ill effect.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "ClearFailureListenerTest: In run() method.");

	// get a lease renewal set w/ duration for as long as we can
	logger.log(Level.FINE, "Creating the lease renewal set with duration" +
			  " of Lease.FOREVER");
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), Lease.FOREVER, null);

	
	// register listener to receive events
	logger.log(Level.FINE, "Registering listener for renewal failure" +
			  " events.");
	set.setRenewalFailureListener(rrl  , null);

	/* create 6 test leases which will throw definite exceptions
	   and add then to the renewal set. Three lease's expiration
	   times are 30 seconds, the rest are 60 seconds. */
	TestLease[] lease = new TestLease[6];

	for (int i = 0; i < 6; i += 2) {
	    logger.log(Level.FINE, "Creating lease with duration of " +
			      renewGrant + " milliseconds.");
	    lease[i] = 
		shortLeaseProvider.createNewLease(owner, 
					     rstUtil.durToExp(renewGrant));
	    set.renewFor(lease[i], Lease.FOREVER);
	    logger.log(Level.FINE, "Creating lease with duration of " +
			      renewGrant*3 + " milliseconds.");
	    lease[i+1] = 
		longLeaseProvider.createNewLease(owner, 
				     rstUtil.durToExp(renewGrant*3));
	    set.renewFor(lease[i+1], Lease.FOREVER);
	}

	// allow the leases with lower duration times to expire
	rstUtil.waitForLeaseExpiration(lease[4],
	    "for client leases with lower expiration times to expire.");

	// remove the listener many times to ensure no ill effect
	try {
	    for (int i = 0; i < 10; ++i) {
		set.clearRenewalFailureListener();
	    }
	} catch (Exception ex) {
	    String message = "Multiple calls to " +
		"clearRenewalFailureListener raises an exception.";
	    throw new TestException(message, ex);
	}

	// assert that only 3 calls to notify have been made
	int numberOfEvents = rrl.getEvents().length;
	if (numberOfEvents != 3) {
	    String message = "Check #1:\n" +
		"Listener received " + numberOfEvents +
		" events but is required to receive exactly 3.";
	    throw new TestException(message);
	}
	
	// wait for the rest of the leases to expire
	rstUtil.waitForLeaseExpiration(lease[5],
		   "for the remaining client leases to expire.");

	// assert that all leases have expired
	for (int i = 0; i < 6; ++i) {
	    Lease managedLease = set.remove(lease[i]);
	    if (managedLease != null) {
		String message = "Lease #" + i + " did not expire as " +
		    "expect.";
		throw new TestException(message);
	    }
	}

	// assert once again that only 3 calls to notify have been made
	numberOfEvents = rrl.getEvents().length;
	if (numberOfEvents != 3) {
	    String message = "Check #2:\n" +
		"Listener received " + numberOfEvents +
		" events but is required to receive exactly 3.";
	    throw new TestException(message);
	}
    }
} // ClearFailureListenerTest
