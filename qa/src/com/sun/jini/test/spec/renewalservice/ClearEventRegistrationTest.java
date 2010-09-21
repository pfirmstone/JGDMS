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
import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
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
import com.sun.jini.test.share.OpCountingOwner;

import net.jini.export.Exporter;
import net.jini.config.ConfigurationException;

/**
 * Assert if a RemoteListener registered for renewal failure events or
 * expiration warning events throws an UnknownEventException, this
 * will only clear the specific event registration, it will not cancel
 * the lease on the renewal set, or affect any other event
 * registration on the set.
 * 
 */
public class ClearEventRegistrationTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * The renewal set duration time 
     */
    long renewSetDur = 0;

    /**
     * Requested lease duration for the renewal set 
     */
    private final long RENEWAL_SET_LEASE_DURATION = 120 * 1000; // 120 seconds

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * Listeners of the RenewalFailureEvents 
     */
    private RememberingRemoteListener normalListener01 = null;
    private RememberingRemoteListener normalListener02 = null;
    private UnknownEventListener failingListener01 = null;
    private UnknownEventListener failingListener02 = null;

    /**
     * Owner (aka Landlord) of the leases 
     */
    private FailingOpCountingOwner failingOwner = null;

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "ClearEventRegistrationTest: In setup() method.");

       // object from which test leases are obtained
       leaseProvider = new TestLeaseProvider(4);

       // create an owner for the lease that will throw a definite exception
       Exception except = 
	   new IllegalArgumentException("ClearEventRegistrationTest");
       failingOwner = new FailingOpCountingOwner(except, 0, renewSetDur);

       // capture grant time for the renewal set
       String prop = "com.sun.jini.test.spec.renewalservice." +
	             "renewal_set_lease_duration";
       renewSetDur = getConfig().getLongConfigVal(prop, RENEWAL_SET_LEASE_DURATION);

       // logs events as they arrive
       normalListener01 = new RememberingRemoteListener(getExporter());
       normalListener02 = new RememberingRemoteListener(getExporter());

       // log event and then fail
       failingListener01 = new UnknownEventListener();
       failingListener02 = new UnknownEventListener();

    }

    /**
     * Assert if a RemoteListener registered for renewal failure events or
     * expiration warning events throws an UnknownEventException, this
     * will only clear the specific event registration, it will not cancel
     * the lease on the renewal set, or affect any other event
     * registration on the set.
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "ClearEventRegistrationTest: In run() method.");

	// Create 2 renewal sets for the supplied duration
	LeaseRenewalService lrs = getLRS();

	logger.log(Level.FINE, "Creating the lease renewal set #1 with " +
			  "duration of " + renewSetDur + " milliseconds.");
	LeaseRenewalSet set01 = lrs.createLeaseRenewalSet(renewSetDur);
	set01 = prepareSet(set01);

	logger.log(Level.FINE, "Creating the lease renewal set #2 with " +
			  "duration of " + renewSetDur + " milliseconds.");
	LeaseRenewalSet set02 = lrs.createLeaseRenewalSet(renewSetDur);
	set02 = prepareSet(set02);

	// register a failing listener for expiration warning events
	long minWarning = renewSetDur * 2 / 10;
	logger.log(Level.FINE, "Registering failing listener #1 with set #1" +
			  " for\n" + "expiration warning events : " +
			  "minWarning = " + minWarning);
	set01.setExpirationWarningListener(failingListener01, minWarning,
					   null);

	// register a normal listener for expiration warning events
	logger.log(Level.FINE, "Registering normal listener #1 with set #2" +
			  " for\n" + "expiration warning events : " +
			  "minWarning = " + minWarning);
	set02.setExpirationWarningListener(normalListener01, minWarning,
					   null);

	// register a failing listener for renewal failure events
	logger.log(Level.FINE, "Registering failing listener #2 with set #2" +
			  " for renewal failure events");
	set02.setRenewalFailureListener(failingListener02, null);

	// register a normal listener for renewal failure events
	logger.log(Level.FINE, "Registering normal listener #2 with set #1" +
			  " for renewal failure events");
	set01.setRenewalFailureListener(normalListener02, null);

	// create a 2 leases that will fail on renewal
	long renewGrant = renewSetDur * 5 / 10;
	logger.log(Level.FINE, "Creating failing lease #1 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease01 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	
	logger.log(Level.FINE, "Creating failing lease # 2 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease02 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	
	// add lease01 to set01 and lease02 to set02
	logger.log(Level.FINE, "Adding lease #1 to set #1");
	set01.renewFor(lease01, Long.MAX_VALUE);
	logger.log(Level.FINE, "Adding lease #2 to set #2");
	set02.renewFor(lease02, Long.MAX_VALUE);
	
	/* wait for the expiration warning events to arrive. 
	   By the time they do, all RenewalFailureEvents should also 
	   have arrived. */
	rstUtil.waitForRemoteEvents(normalListener01, 1, renewSetDur);
	rstUtil.waitForRemoteEvents(failingListener01, 1, renewSetDur);
	
	// Assert that we had one call each on the failing listeners
	RemoteEvent[] events01 = failingListener01.getEvents();
	RemoteEvent[] events02 = failingListener02.getEvents();

	if (events01.length != 1) {
	    String message = "Failing Listener #1 received " + 
		events01.length + " events but is required to\n" +
		"receive exactly 1.";
	    throw new TestException(message);
	}

	if (events02.length != 1) {
	    String message = "Failing Listener #2 received " + 
		events02.length + " events but is required to\n" +
		"receive exactly 1.";
	    throw new TestException(message);
	}

	// Assert that we had one call each on the normal listeners
	events01 = normalListener01.getEvents();
	events02 = normalListener02.getEvents();

	if (events01.length != 1) {
	    String message = "Normal Listener #1 received " + 
		events01.length + " events but is required to\n" +
		"receive exactly 1.";
	    throw new TestException(message);
	}

	if (events02.length != 1) {
	    String message = "Normal Listener #2 received " + 
		events02.length + " events but is required to\n" +
		"receive exactly 1.";
	    throw new TestException(message);
	}

	// Assert we can renew both set's leases without error
	renewSetDur = renewSetDur * 6 / 10; // use a shorter time

	try {
	    logger.log(Level.FINE, "Renewing set #1's lease with duration " +
			      "of " + renewSetDur + " milliseconds.");
	    prepareLease(set01.getRenewalSetLease()).renew(renewSetDur);
	} catch (UnknownLeaseException ex) {
	    String message = "Attempt to renew lease for renewal set\n" +
		"#1 has failed due to " + ex;
	    throw new TestException(message, ex);
	}	    

	try {
	    logger.log(Level.FINE, "Renewing set #2's lease with duration " +
			      "of " + renewSetDur + " milliseconds.");
	    prepareLease(set02.getRenewalSetLease()).renew(renewSetDur);
	} catch (UnknownLeaseException ex) {
	    String message = "Attempt to renew lease for renewal set\n" +
		"#2 has failed due to " + ex;
	    throw new TestException(message, ex);
	}	    

	/* Assert that calls to remove do not result in a 
	   NoSuchObjectException. Given the renewal of the leases
	   above this assertion is probably somewhat redundant. */

	try {
	    Lease managedLease = set01.remove(lease01);
	} catch (NoSuchObjectException ex) {
	    String message = "Attempt to call remove on set\n" +
		"#1 has failed due to a NoSuchObjectException";
	    throw new TestException(message, ex);
	}	    

	try {
	    Lease managedLease = set02.remove(lease02);
	} catch (NoSuchObjectException ex) {
	    String message = "Attempt to call remove on set\n" +
		"#2 has failed due to a NoSuchObjectException";
	    throw new TestException(message, ex);
	}	    

	// Restore both sets to original configurations
	renewGrant = renewSetDur * 3 / 10;
	logger.log(Level.FINE, "Creating failing lease #3 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease03 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	logger.log(Level.FINE, "Adding lease #3 to set #1");
	set01.renewFor(lease03, Long.MAX_VALUE);
	
	logger.log(Level.FINE, "Creating failing lease #4 with duration of " +
			  renewGrant + " milliseconds.");
	TestLease lease04 = 
	    leaseProvider.createNewLease(failingOwner, 
					 rstUtil.durToExp(renewGrant));
	logger.log(Level.FINE, "Adding lease #4 to set #2");
	set02.renewFor(lease04, Long.MAX_VALUE);
	
	/*
	 * Wait until the expiration warning event rolls in.
	 * By that time the renewal failure event should also have arrived.
	 */
	rstUtil.waitForRemoteEvents(normalListener01, 2, renewSetDur);
	
	// Assert that each failingListener still has recorded only 1 event
	events01 = failingListener01.getEvents();
	events02 = failingListener02.getEvents();

	if (events01.length != 1) {
	    String message = "Failing Listener #1 received " + 
		events01.length + " events but is required to\n" +
		"receive exactly 1.\n";
	    message += "It appears that the registration was not cleared.";
	    throw new TestException(message);
	}

	if (events02.length != 1) {
	    String message = "Failing Listener #2 received " + 
		events02.length + " events but is required to\n" +
		"receive exactly 1.";
	    message += "It appears that the registration was not cleared.";
	    throw new TestException(message);
	}

	// Assert that we had two calls each on the normal listeners
	events01 = normalListener01.getEvents();
	events02 = normalListener02.getEvents();

	if (events01.length != 2) {
	    String message = "Normal Listener #1 received " + 
		events01.length + " events but is required to\n" +
		"receive exactly 2.";
	    message += "It appears the registration was cleared in error.";
	    throw new TestException(message);
	}

	if (events02.length != 2) {
	    String message = "Normal Listener #2 received " + 
		events02.length + " events but is required to\n" +
		"receive exactly 2.";
	    message += "It appears the registration was cleared in error.";
	    throw new TestException(message);
	}
    }

    /**
     * Special RemoteListener will throw an UnknownEventException
     */
    class UnknownEventListener extends RememberingRemoteListener {

	public UnknownEventListener() 
		throws ConfigurationException, RemoteException 
	{
	    super(getExporter());
	}

	public synchronized void notify(RemoteEvent event) 
	        throws UnknownEventException, RemoteException {
	    super.notify(event);
	    throw new UnknownEventException("ClearEventRegistrationTest");
	}
    }

} // ClearEventRegistrationTest
