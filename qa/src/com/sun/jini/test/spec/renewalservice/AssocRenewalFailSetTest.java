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
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.event.RemoteEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.FailingOpCountingOwner;
import com.sun.jini.test.share.OpCountingOwner;

/**
 * Assert that the set returned as the source of the RenewalFailureEvent is
 * the set on which the renewal attempt failed.
 * 
 */
public class AssocRenewalFailSetTest extends AbstractLeaseRenewalServiceTest {
    
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
    private TestLeaseProvider leaseProvider = null;

    /**
     * Listeners of the RenewalFailureEvents 
     */
    private RememberingRemoteListener rrl = null;

    /**
     * Owner (aka Landlord) of the leases 
     */
    private FailingOpCountingOwner failingOwner = null;
    private OpCountingOwner succeedingOwner = null;

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
       logger.log(Level.FINE, "AssocRenewalFailSetTest: In setup() method.");

       // object from which test leases are obtained
       leaseProvider = new TestLeaseProvider(3);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create an owner for the lease that will renewal successfully
       succeedingOwner = new OpCountingOwner(renewGrant);
       
       // create an owner for the lease that will throw a definite exception
       String testName = "AssocRenewalFailSetTest";
       Exception except = new UnknownLeaseException(testName);
       failingOwner = new FailingOpCountingOwner(except, 0, renewGrant*2);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
       return this;
    }


    /**
     * Assert that the set returned as the source of the RenewalFailureEvent is
     * the set on which the renewal attempt failed.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "AssocRenewalFailSetTest: In run() method.");

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

	// create two leases that will not fail on renewal
	logger.log(Level.FINE, "Creating lease #1 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease01 = 
	    leaseProvider.createNewLease(succeedingOwner, 
					 rstUtil.durToExp(renewGrant));
	set.renewFor(lease01, Long.MAX_VALUE);
	
	logger.log(Level.FINE, "Creating lease #2 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease02 = 
	    leaseProvider.createNewLease(succeedingOwner, 
					 rstUtil.durToExp(renewGrant));
	set.renewFor(lease02, Long.MAX_VALUE);

	// create the lease to be managed
	long longerGrant = renewGrant * 2;
	logger.log(Level.FINE, "Creating lease #3 with duration of " +
			  longerGrant + " milliseconds.");
	TestLease lease03 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(longerGrant));
	set.renewFor(lease03, Long.MAX_VALUE);

	// wait for the failing lease to renew
	rstUtil.waitForRemoteEvents(rrl, 1, longerGrant);

	// assert we only received 1 event
	RemoteEvent[] events = rrl.getEvents();
	if (events.length != 1) {
	    String message = "Listener received " + events.length +
		" events but is required to receive exactly 1.";
	    throw new TestException(message);
	}

	// assert it's the set that failed
	RenewalFailureEvent rfe = (RenewalFailureEvent) events[0];
	Lease failedLease = rfe.getLease();
	if (failedLease.equals(lease03) == false) {
	    String message = "The source of the event was not the set" +
		" that caused the RenewalFailureEvent.";
	    throw new TestException(message);
	}

	// just for grins, make certain that the exception is the right one
	Throwable exception = rfe.getThrowable();
	if (exception.getClass() != UnknownLeaseException.class) {
	    String message = "The Throwable returned was the wrong" + 
		" type.\n";
	    throw new TestException(message);
	}
    }
} // AssocRenewalFailSetTest













