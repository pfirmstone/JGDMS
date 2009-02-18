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
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.RenewingRemoteListener;

/**
 * Assert that events of the WarningExpirationEvent type sent to a
 * newly registered listener are in the same sequence as those sent to
 * the previously registered listener. (weak test: because partial
 * failure may cause the test to fail when it really should
 * succeed. This is because retransmission of events is valid and in
 * some cases it is impossible to tell if two events with the same
 * sequence number are in fact the same event or different.)
 * 
 */
public class EventSequenceWarnTest extends AbstractLeaseRenewalServiceTest {
    
    /**
     * Requested lease duration for the renewal set 
     */
    private final long MIN_WARNING_DEFAULT = 10 * 1000;

    /**
     * listener that will log events as they arrive 
     */
    private RenewingRemoteListener rrl01 = null;
    private RenewingRemoteListener rrl02 = null;

    /**
     * the duration used for renewals of renewal set leases 
     */
    private long renewSetDur = 0;

    /**
     * the minimum warning time for ExpirationWarningEvents 
     */
    private long minWarning = 0;

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "EventSequenceWarnTest: In setup() method.");

       // minimum warning time required for test
       String prop = "com.sun.jini.test.spec.renewalservice.minWarning";
       minWarning = getConfig().getLongConfigVal(prop, MIN_WARNING_DEFAULT);

       // renewal value of set (relative to the minWarning)
       renewSetDur = minWarning * 3;

       // logs events as they arrive
       rrl01 = new RenewingRemoteListener(getExporter(), renewSetDur);
       rrl02 = new RenewingRemoteListener(getExporter(), renewSetDur);
    }


    /**
     * Assert that events of the WarningExpirationEvent type sent to a
     * newly registered listener are in the same sequence as those sent to
     * the previously registered listener. (weak test: because partial
     * failure may cause the test to fail when it really should
     * succeed. This is because retransmission of events is valid and in
     * some cases it is impossible to tell if two events with the same
     * sequence number are in fact the same event or different.)
     * 
     * @return the test Status (passed or failed)
     * 
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "EventSequenceWarnTest: In run() method.");

	// grab the ever popular LRS
	LeaseRenewalService lrs = getLRS();

	// create a renewal set for the requested duration
	logger.log(Level.FINE, "Creating renewal set w/ duration = " +
			  renewSetDur + " milliseconds.");
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(renewSetDur);
	set = prepareSet(set);

	// register listener #1 to receive events
	logger.log(Level.FINE, "Registering listener #1 for warning events.");
	logger.log(Level.FINE, "minWarning = " + minWarning + ".");
	set.setExpirationWarningListener(rrl01, minWarning, null);

	/* We want to wait for three renewals (or more)
	   before switching listeners. Some implementations that refuse
	   to give out leases for the request grant duration may send 
	   more events in that time. This is okay, it does not effect 
	   the validity of the test. */
	long timeOut = renewSetDur * 3;
	rstUtil.waitForRemoteEvents(rrl01, 3, timeOut);
	
	// start listening immediately with listener #2
	logger.log(Level.FINE, "Registering listener #2 for warning events.");
	logger.log(Level.FINE, "minWarning = " + minWarning + ".");
	set.setExpirationWarningListener(rrl02, minWarning, null);

	// now lets wait for 3 more events to arrive
	rstUtil.waitForRemoteEvents(rrl02, 3, timeOut);

	// report on how many events were received by each (3 = minimum)
	RemoteEvent[] events01 = rrl01.getEvents();
	RemoteEvent[] events02 = rrl02.getEvents();

	if (events01.length < 3) {
	    String message = "Listener #1 received " + events01.length +
		" ExpirationWarningEvents but is required to receive\n" +
		"at least 3";
	    throw new TestException(message);
	}
	logger.log(Level.FINE, "Listener #1 received " + events01.length +
			  " ExpirationWarningEvents.");

	if (events02.length < 3) {
	    String message = "Listener #2 received " + events02.length +
		" ExpirationWarningEvents but is required to receive\n" +
		"at least 3";
	    throw new TestException(message);
	}
	logger.log(Level.FINE, "Listener #2 received " + events02.length +
			  " ExpirationWarningEvents.");

	/* create a sorted collection of each of the event sets.
	   the assumption here is that the events need not arrive 
	   in order, but we are assuming a reliable network so they
	   all must arrive. */
	TreeSet sequenceSet01 = new TreeSet();
	TreeSet sequenceSet02 = new TreeSet();

	for (int i = 0; i < events01.length; ++i) {
	    sequenceSet01.add(new Long(events01[i].getSequenceNumber()));
	}

	for (int i = 0; i < events02.length; ++i) {
	    sequenceSet02.add(new Long(events02[i].getSequenceNumber()));
	}

	// assert that each sequence is in order with no gaps

	// first sequenceSet01
	Iterator iter = sequenceSet01.iterator();
	Long previousLong = (Long) iter.next();
	while (iter.hasNext()) {
	    Long nextLong = (Long) iter.next();
	    logger.log(Level.FINE, "previousID = " + previousLong + 
			      " nextID = " + nextLong);
	    long delta = nextLong.longValue() - previousLong.longValue();
	    if (delta != 1) {
		String message = "Event sequence numbers are not" +
		    " strictly increasing for listener #1.";
		throw new TestException(message);
	    }

	    previousLong = nextLong;
	}

	// first sequenceSet02
	iter = sequenceSet02.iterator();
	previousLong = (Long) iter.next();
	while (iter.hasNext()) {
	    Long nextLong = (Long) iter.next();
	    logger.log(Level.FINE, "previousID = " + previousLong + 
			      " nextID = " + nextLong);
	    long delta = nextLong.longValue() - previousLong.longValue();
	    if (delta != 1) {
		String message = "Event sequence numbers are not" +
		    " strictly increasing for listener #2.";
		throw new TestException(message);
	    }

	    previousLong = nextLong;
	}

	/* assert that there is no overlap in the event sequence numbers. 
	   Strictly speaking there can be an overlap because it is valid
	   for the LRS to send the same event twice. Our assumption here
	   is that the network is totally reliable (ie. partial failure
	   does not exist within out testing environment). */
	iter = sequenceSet01.iterator();
	while (iter.hasNext()) {
	    Long nextLong = (Long) iter.next();
	    if (sequenceSet02.contains(nextLong)) {
		String message = "Event sequence numbers overlap between" +
		    " listener #1 and listener #2.";
		throw new TestException(message);
	    }

	}	     
    }
} // EventSequenceWarnTest
