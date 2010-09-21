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

// java.util
import java.util.Iterator;
import java.util.TreeSet;

// net.jini
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.RememberingRemoteListener;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.FailingOpCountingOwner;

/**
 * Assert that events arrive in strictly increasing order.<BR>
 * (This is a weak test because it is possible to fail if events are lost
 * on the network and the test is not and can not be exhaustive).
 * 
 */
public class EventSequenceTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * the number of renewal sets used in this test
     */
    private final int NUMBER_OF_RENEWAL_SETS = 10;

    /**
     * an array to hold references to the renewal sets 
     */
    private LeaseRenewalSet[] renewalSet = 
	new LeaseRenewalSet[NUMBER_OF_RENEWAL_SETS];

    /**
     * the number of test leases used in this test
     */
    private final int NUMBER_OF_TEST_LEASES = 10;

    /**
     * an array to hold references to the renewal sets 
     */
    private Lease[] testLease = new Lease[NUMBER_OF_TEST_LEASES];

    /**
     * The renewal set duration time 
     */
    private long renewSetDur = 0;

    /**
     * Requested lease duration for the renewal set 
     */
    private final long RENEWAL_SET_LEASE_DURATION = 40 * 1000; // 40 seconds

    /**
     * Requested lease duration for the renewal set 
     */
    private final long MIN_WARNING_DEFAULT = 10 * 1000;

    /**
     * the minimum warning time for ExpirationWarningEvents 
     */
    private long minWarning = 0;

    /**
     * The maximum time granted for a lease by a renew operation. 
     */
    private long renewGrant = 0;

    /**
     * The default value renewGrant 
     */
    private final long DEFAULT_RENEW_GRANT = 30 * 1000; // 30 seconds

    /**
     * Listeners of the ExpirationWarningEvents
     */
    private RememberingRemoteListener rrl = null;

    /**
     *  The LeaseRenewalManager used for LRS impls that grant only short leases
     */
    private LeaseRenewalManager lrm = null;

    /**
     * Owner (aka Landlord) of the leases 
     */
    private FailingOpCountingOwner owner = null;

    /**
     * Provides leases for this test. 
     */
    private TestLeaseProvider leaseProvider = null;

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "EventSequenceTest: In setup() method.");

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());

       // capture the renewal set lease duration
       String prop = "com.sun.jini.test.spec.renewalservice." +
	   "renewal_set_lease_duration";
       renewSetDur = getConfig().getLongConfigVal(prop, RENEWAL_SET_LEASE_DURATION);

       // minimum warning time required for test
       prop = "com.sun.jini.test.spec.renewalservice.minWarning";
       minWarning = getConfig().getLongConfigVal(prop, MIN_WARNING_DEFAULT);

       // logs events as they arrive and renews the lease
       rrl = new RememberingRemoteListener(getExporter());

       // create lease renewal manager for wider use across implementations
       lrm = new LeaseRenewalManager(sysConfig.getConfiguration());

       // capture the renewal time
       prop = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(prop, DEFAULT_RENEW_GRANT);

       // create an owner for the lease that will throw a definite exception
       // create an owner to for testing definite exceptions
       Exception ex = new IllegalArgumentException("EventSequenceTest");
       owner = new FailingOpCountingOwner(ex, 0, renewGrant);

       // object from which test leases are obtained
       leaseProvider = new TestLeaseProvider(NUMBER_OF_TEST_LEASES);

    }


    /**
     * Assert that events arrive in strictly increasing order.<BR>
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "EventSequenceTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create all required renewal sets and register listener
	for (int i = 0; i < NUMBER_OF_RENEWAL_SETS; ++i) {
	    logger.log(Level.FINE, "Creating renewal set #" + i + 
			      " with lease" + " duration of " + 
			      renewSetDur + " milliseconds.");
	    renewalSet[i] = prepareSet(lrs.createLeaseRenewalSet(renewSetDur));
	    lrm.renewFor(prepareLease(renewalSet[i].getRenewalSetLease()), 
			 renewSetDur,
			 null);

	    // register listener to receive events
	    logger.log(Level.FINE, "Registering listener for warning events.");
	    logger.log(Level.FINE, "minWarning = " + minWarning + ".");
	    renewalSet[i].setExpirationWarningListener(rrl, minWarning, 
						       null);

	}

	// wait until the renewal set leases have expired
	rstUtil.waitForRemoteEvents(rrl, NUMBER_OF_RENEWAL_SETS,
				    renewSetDur * 2);

	// Ensure that we received the correct number of events
	RemoteEvent[] events = rrl.getEvents();
	if (events.length < 1) {
	    String message = "Listener did not receive any events.\n" +
		"Should have received exactly " + NUMBER_OF_RENEWAL_SETS;
	    throw new TestException(message);
	} else if (events.length != NUMBER_OF_RENEWAL_SETS) {
	    String message = "Listener received " + events.length +
		" events.\n" + "Should have received exactly " + 
		NUMBER_OF_RENEWAL_SETS;
	    throw new TestException(message);
	}

	/* create a sorted collection of the event set.
	   the assumption here is that the events need not arrive 
	   in order, but we are assuming a reliable network so they
	   all must arrive. */
	TreeSet sequenceSet = new TreeSet();

	for (int i = 0; i < events.length; ++i) {
	    sequenceSet.add(new Long(events[i].getSequenceNumber()));
	}

	// assert that each sequence is in order with no gaps
	Iterator iter = sequenceSet.iterator();
	Long previousLong = (Long) iter.next();
	while (iter.hasNext()) {
	    Long nextLong = (Long) iter.next();
	    logger.log(Level.FINE, "previousID = " + previousLong + 
			      " nextID = " + nextLong);
	    long delta = nextLong.longValue() - previousLong.longValue();
	    if (delta != 1) {
		String message = "Event sequence numbers are not" +
		    " strictly increasing for WarningExpirationEvents.";
		throw new TestException(message);
	    }

	    previousLong = nextLong;
	}

	// clear the listener so we can reuse it
	rrl.clear();

	// create a new set
	LeaseRenewalSet set  = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	lrm.renewFor(prepareLease(set.getRenewalSetLease()), Lease.FOREVER, null);
	logger.log(Level.FINE, "Created renewal set with lease" +
			  "duration of Lease.FOREVER.");

	// register listener to receive events
	logger.log(Level.FINE, "Registering listener for renewal failure " +
			  "events.");
	set.setRenewalFailureListener(rrl, null);
	
	/* create 10 test leases which will throw definite exceptions
	   and add each to the renewal set. */
	for (int i = 0; i < NUMBER_OF_TEST_LEASES; ++i) {
	    logger.log(Level.FINE, "Creating lease with duration of " +
			      renewGrant + " milliseconds.");
	    testLease[i] = 
		leaseProvider.createNewLease(owner, 
					     rstUtil.durToExp(renewGrant));
	    set.renewFor(testLease[i], Lease.FOREVER);
	}

	// wait until the test leases have expired
	rstUtil.waitForRemoteEvents(rrl, NUMBER_OF_TEST_LEASES,
				    renewGrant * 2);

	// Ensure that we received the correct number of events
	events = rrl.getEvents();
	if (events.length < 1) {
	    String message = "Listener did not receive any events.\n" +
		"Should have received exactly " + NUMBER_OF_TEST_LEASES;
	    throw new TestException(message);
	} else if (events.length != NUMBER_OF_TEST_LEASES) {
	    String message = "Listener received " + events.length +
		"events.\n" + "Should have received exactly " + 
		NUMBER_OF_TEST_LEASES;
	    throw new TestException(message);
	}

	/* create a sorted collection of the event set.
	   the assumption here is that the events need not arrive 
	   in order, but we are assuming a reliable network so they
	   all must arrive. */
	sequenceSet = new TreeSet();

	for (int i = 0; i < events.length; ++i) {
	    sequenceSet.add(new Long(events[i].getSequenceNumber()));
	}

	// assert that each sequence is in order with no gaps
	iter = sequenceSet.iterator();
	previousLong = (Long) iter.next();
	while (iter.hasNext()) {
	    Long nextLong = (Long) iter.next();
	    logger.log(Level.FINE, "previousID = " + previousLong + 
			      " nextID = " + nextLong);
	    long delta = nextLong.longValue() - previousLong.longValue();
	    if (delta != 1) {
		String message = "Event sequence numbers are not" +
		    " strictly increasing for RenewalFailureEvents.";
		throw new TestException(message);
	    }

	    previousLong = nextLong;
	}
    }
} // EventSequenceTest
