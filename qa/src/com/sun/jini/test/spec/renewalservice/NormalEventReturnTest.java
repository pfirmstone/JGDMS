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
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

// 
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.Test;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.FailingOpCountingOwner;
import com.sun.jini.test.share.OpCountingOwner;

/**
 * Both the getLease and getThrowable methods are declared to throw
 * IOException and ClassNotFoundException. This declaration allows
 * implementations to delay unmarshalling this state until it is
 * actually needed. Assert that once either method of a given
 * RenewalFailureEvent object returns normally, future calls on that
 * method must return the same object and may not throw an exception.
 * 
 */
public class NormalEventReturnTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The default value time for which client leases are renewed
     */
    private final long DEFAULT_RENEW_GRANT = 40 * 1000; // 40 seconds

    /**
     * The "land lord" for the leases. Defines lease method behavior.
     */
    private FailingOpCountingOwner failingOwner = null;

    /**
     * listener that will log events as they arrive 
     */
    private RememberingRemoteListener normalListener = null;

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

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
       logger.log(Level.FINE, "NormalEventReturnTest: In setup() method.");

       // logs events as they arrive
       normalListener = new RememberingRemoteListener(getExporter());

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // instantiate a lease provider
       leaseProvider = new TestLeaseProvider(3);

       // create an owner to for testing definite exceptions
       Exception definiteException = 
	   new IllegalArgumentException("NormalEventReturnTest");
       failingOwner = 
	   new FailingOpCountingOwner(definiteException, 0, renewGrant);
       return this;
    }


    /**
     * Both the getLease and getThrowable methods are declared to throw
     * IOException and ClassNotFoundException. This declaration allows
     * implementations to delay unmarshalling this state until it is
     * actually needed. Assert that once either method of a given
     * RenewalFailureEvent object returns normally, future calls on that
     * method must return the same object and may not throw an exception.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "NormalEventReturnTest: In run() method.");

	// get the service for test
	LeaseRenewalService lrs = getLRS();

	// create a lease renewal set that hangs around a long time
	logger.log(Level.FINE, "Creating Set with lease duration of " +
			  "Lease.FOREVER.");
	long renewSetDur = Lease.FOREVER;
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);

	// register the listener to receive renewal failure events
	logger.log(Level.FINE, "Registering listener to receive " +
			  "RenewalFailureEvents.");
	set.setRenewalFailureListener(normalListener, null);

	// create a lease that will fail during renewal attempts
	logger.log(Level.FINE, "Creating lease with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	
	// add the lease to the renewal set w/ membership of renewGrant
	logger.log(Level.FINE, "Adding lease to renewal set.");
	long membership = renewGrant;
	logger.log(Level.FINE, "membership = " + membership + " milliseconds.");
	set.renewFor(lease, membership);

	// wait for the failure to roll in ...
	rstUtil.waitForRemoteEvents(normalListener, 1, renewGrant * 2);

	// capture the event
	RemoteEvent[] events = normalListener.getEvents();

	// assert that there is only one event
	if (events.length != 1) {
	    String message = "Listener received " + 
		events.length + " events but is required to\n" +
		"receive exactly 1.";
	    throw new TestException(message);
	}

	// get the lease and throwable objects from the event.
	RenewalFailureEvent rfe = (RenewalFailureEvent) events[0];
	Lease eventLease = rfe.getLease();
	IllegalArgumentException eventException = 
	    (IllegalArgumentException) rfe.getThrowable();

	/* try to recall 50 times and make certain that nothing changes
	   and no exceptions are thrown */
	for(int i = 0; i < 50; ++i) {

	    try {
		if (eventLease.equals(rfe.getLease()) == false) {
		    String message = "The lease returned by getLeases()" +
			"has changed.";
		    throw new TestException(message);
		}
	    } catch (Exception ex) {
		String message = "A successive call to getLease() has" +
		    " raised an Exception " + ex;
		throw new TestException(message, ex);
	    }

	    try {
		if (eventException.equals(rfe.getThrowable()) == false) {
		    String message = "The Exception returned by" +
			"getThrowable() has changed.";
		    throw new TestException(message);
		}
	    } catch (Exception ex) {
		String message = "A successive call to getThrowable()" +
		    "has raised an Exception " + ex;
		throw new TestException(message, ex);
	    }
	}
    }
} // NormalEventReturnTest
