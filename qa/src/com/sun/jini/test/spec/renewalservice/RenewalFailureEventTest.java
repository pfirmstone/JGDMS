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

// java.rmi
import java.rmi.ConnectIOException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEvent;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.FailingOpCountingOwner;

/**
 * Assert that the RenewalFailureEvent object returns the excepted
 * lease and the last Throwable from a series of indefinite renewal
 * failures.
 * 
 */
public class RenewalFailureEventTest extends AbstractLeaseRenewalServiceTest {    
    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 60 * 1000; // 60 seconds

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
    protected FailingOpCountingOwner failingOwner = null;

    /**
     *  The LeaseRenewalManager used for LRS impls that grant only short leases
     */
    private LeaseRenewalManager lrm = null;

    /**
     * the array for storing exceptions to be thrown.
     */
    protected Throwable[] throwables = createExceptionArray();
    
    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "RenewalFailureEventTest: In setup() method.");

       // object from which test leases are obtained
       leaseProvider = new TestLeaseProvider(3);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // owner that will throw a succession of Exceptions
       failingOwner = new FailingOpCountingOwner(throwables, 0, renewGrant);

       // logs events as they arrive
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());
    }

    /**
     * Assert that the RenewalFailureEvent object returns the excepted
     * lease and the last Throwable from a series of indefinite renewal
     * failures.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "RenewalFailureEvent: In run() method.");

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
	set.setRenewalFailureListener(rrl, null);

	// create a lease that will ultimately fail renewal
	logger.log(Level.FINE, "Creating lease with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	logger.log(Level.FINE, "Adding lease to renewal set.");
	set.renewFor(lease, Long.MAX_VALUE);
	
	// wait for the failing lease to renew
	rstUtil.waitForRemoteEvents(rrl, 1, renewGrant*2);

	// Capture the event and ensure there is only one
	RemoteEvent[] events = rrl.getEvents();
	if (events.length != 1) {
	    String message = "Listener received " + events.length +
		" events but is required to receive exactly 1.";
	    throw new TestException(message);
	}

	// Assert that the Throwable is the one we expect.
	RenewalFailureEvent rfe = (RenewalFailureEvent) events[0];
	Throwable failException = rfe.getThrowable();
	Throwable targetException = getExpectedException();
	if (failException.getClass() != targetException.getClass()) {
	    String message = "The getThrowable() method returned an\n" +
		"exception of type " + failException.getClass().getName() +
		" but should have returned an instance\n" +
		"of " + targetException.getClass().getName() + ".";
	    throw new TestException(message);
	}

	if ((failException.getMessage().equals
		   (targetException.getMessage())) == false) {
	    String message = "Assertion #3 has failed.\n" +
		"Throwable encapsulated by the RenewalFailureEvent is" +
		" of type " + failException.getClass() + 
		"\nas expected but the instances are different.";
	    throw new TestException(message);
	}

	// Assert that getLease() returns the one we expect
	Lease managedLease = rfe.getLease();
	if (managedLease.equals(lease) == false) {
	    String message = "The getLease() method did not return\n" +
		"the expected lease.";
	    throw new TestException(message);
	}
    }

    /**
     * Creates an array of exceptions to be thrown
     * 
     * <P>Notes:</P>
     * Subclasses override this to test different combinations of Exceptions.
     * 
     * @return an array of Throwable containing Exceptions for FailingOwner
     * 
     */
    protected Throwable[] createExceptionArray() {

       Throwable[] throwArray = new Throwable[4];
       throwArray[3] = 
	   new NoSuchObjectException("NoSuchObjectException");
       throwArray[2] = 
	   new ConnectIOException("ConnectIOException");
       throwArray[1] = 
	   new RemoteException("RemoteException");
       throwArray[0] = 
	   new UnmarshalException("UnmarshalException");

       return throwArray;
    }

    /**
     * Get the last exception excepted to the thrown.
     * 
     * <P>Notes:</P>
     *  In this case its the last exception in array because its definite.
     * 
     * @return the Exception expected to be encapsulated in the event object
     * 
     */
    protected Throwable getExpectedException() {
       return throwables[throwables.length-1]; // last one thrown
    }

} // RenewalFailureEventTest
