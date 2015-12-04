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

// java.rmi
import java.rmi.ConnectIOException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;

// net.jini
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;
import net.jini.lease.ExpirationWarningEvent;

// 
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import org.apache.river.test.share.RenewingRemoteListener;
import org.apache.river.test.share.RememberingRemoteListener;
import org.apache.river.test.share.TestLease;
import org.apache.river.test.share.TestLeaseProvider;
import org.apache.river.test.share.FailingOpCountingOwner;
import net.jini.export.Exporter;
import net.jini.config.ConfigurationException;


/**
 * Assert the following:
 * <OL>
 * <LI>When a RemoteEventListener's notify method is called, the event
 *     contains the expected lease. This test covers both 
 *     ExpirationWarningEvents and RenewalFailureEvents.</LI>
 * <LI>The expiration time will reflect the expiration of the lease when
 *     the event occured.</LI>
 * <LI>The Throwable object encapsulated by the RenewalFailureEvent is
 *     is of the correct type.</LI>
 * <LI>The lease that caused the renewal failure event is removed from the
 *     lease renewal set.</LI>
 * </OL>
 * 
 */
public class EventLeaseTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The renewal set duration time 
     */
    long renewSetDur = 0;

    /**
     * Requested lease duration for the renewal set 
     */
    private final long RENEWAL_SET_LEASE_DURATION = 60 * 1000; // 60 seconds

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
    private RememberingRemoteListener warnListener = null;
    private RememberingRemoteListener failListener = null;

    /**
     * Owner (aka Landlord) of the leases 
     */
    protected FailingOpCountingOwner owner = null;

    /**
     * leases used for integrity checks 
     */
    private Lease warnLease = null;
    private Lease failLease = null;

    /**
     * the array for storing exceptions to be thrown.
     */
    protected   Throwable[] throwables = createExceptionArray();

    /**
     * Sets up the testing environment.
     */
    public Test construct(org.apache.river.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.construct(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "EventLeaseTest: In setup() method.");

       // capture grant time for the renewal set
       String prop = "org.apache.river.test.spec.renewalservice." +
	             "renewal_set_lease_duration";
       renewSetDur = getConfig().getLongConfigVal(prop, RENEWAL_SET_LEASE_DURATION);

       // object from which test leases are obtained
       leaseProvider = new TestLeaseProvider(8);

       // capture the renewal time
       String property = "org.apache.river.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create an owner for testing definite exceptions
       owner = new FailingOpCountingOwner(throwables, 0, renewGrant);

       // logs events as they arrive
       warnListener = new RememberingRemoteListener(getExporter());
       failListener = new RememberingRemoteListener(getExporter());
       return this;
    }

    /**
     * Assert the following:
     * <OL>
     * <LI>When a RemoteEventListener's notify method is called, the event
     *     contains the expected lease. This test covers both 
     *     ExpirationWarningEvents and RenewalFailureEvents.</LI>
     * <LI>The expiration time will reflect the expiration of the lease when
     *     the event occured.</LI>
     * <LI>The Throwable object encapsulated by the RenewalFailureEvent is
     *     is of the correct type.</LI>
     * <LI>The lease that caused the renewal failure event is removed from the
     *     lease renewal set.</LI>
     * </OL>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "EventLeaseTest: In run() method.");

	// get a lease renewal set w/ duration for as long as we can
	logger.log(Level.FINE, "Creating the lease renewal set with duration" +
			  " of Lease.FOREVER");
	LeaseRenewalService lrs = getLRS();

	// capture times for roundTrip calculations
        // expand round-trip time to tolerate gc-delays, etc.
	long time01 = System.currentTimeMillis();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);
	long time02 = System.currentTimeMillis();
	long roundTrip = (time02 - time01) * 10;
	warnLease = prepareLease(set.getRenewalSetLease());
	long warnLeaseExpiration = warnLease.getExpiration();
	
	// create the lease to be managed
	logger.log(Level.FINE, "Creating lease with duration of " +
			  renewGrant + " milliseconds.");
	failLease = 
	    leaseProvider.createNewLease(owner, 
					 rstUtil.durToExp(renewGrant));
	long failLeaseLocalExpiration = failLease.getExpiration();
	set.renewFor(failLease, Lease.FOREVER);
	long nextFailExpiration = failLeaseLocalExpiration + renewGrant;

	// register listener to receive expiration warning events
	long minWarning = renewSetDur / 2;
	logger.log(Level.FINE, "Registering listener for expiration" +
			  " warning events.");
	logger.log(Level.FINE, "minWarning = " + minWarning + " milliseconds.");
	set.setExpirationWarningListener(warnListener, minWarning, null);

	// register listener to receive renewal failure events
	logger.log(Level.FINE, "Registering listener for renewal" +
			  " failure events.");
	set.setRenewalFailureListener(failListener, null);

	/* wait for the events to get arrive. We do this by creating
	   two threads that wait for the events to arrive and then
	   join with both. */
	Thread warnWaitThread = 
	    rstUtil.createRemoteEventWaitThread(warnListener, 1);
	Thread failWaitThread = 
	    rstUtil.createRemoteEventWaitThread(failListener, 1);

	long maxWaitTime = Math.max(renewSetDur, renewGrant);

	// wait for expiration warning event
	warnWaitThread.join(maxWaitTime);

	if (warnWaitThread.isAlive() == true) {
	    String message = "ExpirationWarningEvent was never received.";
	    throw new TestException(message);
	}
	
	// capture the expiration time of the warning event
	ExpirationWarningEvent warnEvent = 
	    (ExpirationWarningEvent) warnListener.getEvents()[0];
	long warnEventExpiration = 
	    prepareLease(warnEvent.getRenewalSetLease()).getExpiration();

	// wait for renewal failure event
	failWaitThread.join(maxWaitTime);

	// if the thread is still alive then the event was never received.
	if (failWaitThread.isAlive() == true) {
	    String message = "RenewalFailureEvent was never received.";
	    throw new TestException(message);
	}

	// capture the expiration time of the renewal failure event
	RenewalFailureEvent failEvent = 
	    (RenewalFailureEvent) failListener.getEvents()[0];
	long failEventExpiration = failEvent.getLease().getExpiration();

	logger.log(Level.FINE, "Number of failure events = " + 
			  warnListener.getEvents().length);
	logger.log(Level.FINE, "Number of warning events = " + 
			  failListener.getEvents().length);


	/* ASSERTION #1 :
	   assert that the leases are the ones we expect */
	if (warnLease.equals(prepareLease(warnEvent.getRenewalSetLease())) == false) {
	    String message = "Assertion #1 failed.\n" +
		"Expiration warning lease does not match\n" +
		"the lease encapsulated in the ExpirationWarningEvent " +
		"object.";
	    throw new TestException(message);
	}

	if (failLease.equals(failEvent.getLease()) == false) {
	    String message = "Assertion #1 has failed.\n" +
		"Renewal failure lease does not match the " +
		"lease encapsulated in the RenewalFailureEvent object.";
	    throw new TestException(message);
	}

	logger.log(Level.FINE, "Assertion #1 passed.");

	/* ASSERTION #2 :
	   the expiration will reflect the expiration of the lease
	   when the event occurred. */

	long delta = warnLeaseExpiration - warnEventExpiration;
	if (Math.abs(delta) > roundTrip) {
	    logger.log(Level.FINE, "Assertion #2 failed, delta = " + delta 
			    + "  10*roundTrip = " + roundTrip);
	    String message = "Assertion #2 has failed.\n" +
		"Expiration time of lease in warning event does not\n" +
		"reflect the expiration of the lease when the event " +
		"occurred.";
	    throw new TestException(message);
	} else {
	    logger.log(Level.FINE, "Assertion #2 passed, delta = " + delta 
			    + "  10*roundTrip = " + roundTrip);
	}

	/*
	 * This portion of the assertion is pretty weak. All it says
	 * is that the expiration time on the lease is at least
	 * within one renewGrant times worth of the original lease.
	 * That's pretty broad and may not be particularly useful.
	 * The problem is that there is no real way of determining
	 * exactly what the lease expiration was at the time the renewal
	 * failure event occurred and there is a significant (5 second)
	 * time interval between the delivery of the marshalling of the
	 * lease object and the delivery of the event.
	 */
	if (failEventExpiration > nextFailExpiration) {
	    String message = "Assertion #2 has failed.\n" +
		"Expiration time of lease in failure event does not\n" +
		"reflect the expiration of the lease when the event " +
		"occurred.";
	    throw new TestException(message);
	}

	logger.log(Level.FINE, "Assertion #2 passed.");

	/* ASSERTION #3 :
	   The Throwable Object is the one expected. */	       
	Throwable failException = failEvent.getThrowable();
	Throwable targetException = getExpectedException();

	if (! (failException.getClass() == targetException.getClass())) {
	    String message = "Assertion #3 has failed.\n" +
		"Throwable encapsulated by the RenewalFailureEvent is" +
		" of type " + failException.getClass() + 
		"\nbut an exception of type " + 
		targetException.getClass() + " is excepted.";
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

	logger.log(Level.FINE, "Assertion #3 passed.");

	/* ASSERTION #4
	   The lease whose renewal failed should have been removed
	   from the renewal set. */
	Lease managedLease = set.remove(failLease);
	if (managedLease != null) {
	    String message = "Assertion #4 failed.\n" +
		"Failed lease was never removed from renewal set.";
	    throw new TestException(message);
	}
	
	logger.log(Level.FINE, "Assertion #4 passed.");
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


} // EventLeaseTest
