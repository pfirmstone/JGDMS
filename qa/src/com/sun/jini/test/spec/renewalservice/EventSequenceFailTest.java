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

// java.io
import java.io.IOException;

// java.util
import java.util.Iterator;
import java.util.TreeSet;

// net.jini
import net.jini.core.event.RemoteEvent;
import net.jini.core.lease.Lease;
import net.jini.lease.LeaseRenewalService;
import net.jini.lease.LeaseRenewalSet;
import net.jini.lease.RenewalFailureEvent;

// 
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.test.share.RenewingRemoteListener;
import com.sun.jini.test.share.TestLease;
import com.sun.jini.test.share.TestLeaseProvider;
import com.sun.jini.test.share.FailingOpCountingOwner;

/**
 * Assert that the events of the RenewalFailureEvent type sent to a
 * newly registered listener are in the same sequence as those sent to
 * the previously registered listener.  
 *
 * <P>Note:<BR>This test is stronger than the EventSequenceWarnTest
 * because the RenewalFailureEvent carries a reference to the lease
 * whose renewal failed and thus imparts to the event an identity thay
 * cana be used to distinguish between two different events thay might
 * have the same sequence number).</P>
 * 
 */
public class EventSequenceFailTest extends AbstractLeaseRenewalServiceTest {
    
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
    private TestLeaseProvider earlyLeaseProvider = null;
    private TestLeaseProvider lateLeaseProvider = null;

    /**
     * Listeners of the RenewalFailureEvents 
     */
    private RenewingRemoteListener rrl01 = null;
    private RenewingRemoteListener rrl02 = null;

    /**
     * Owner (aka Landlord) of the leases 
     */
    FailingOpCountingOwner owner = null;

    /**
     * Sets up the testing environment.
     */
    public void setup(com.sun.jini.qa.harness.QAConfig sysConfig) throws Exception {

       // mandatory call to parent
       super.setup(sysConfig);
	
       // Announce where we are in the test
       logger.log(Level.FINE, "EventSequenceFailTest: In setup() method.");

       // object from which test leases are obtained
       earlyLeaseProvider = new TestLeaseProvider(4);
       lateLeaseProvider = new TestLeaseProvider(4);

       // capture the renewal time
       String property = "com.sun.jini.test.spec.renewalservice.renewGrant";
       renewGrant = getConfig().getLongConfigVal(property, DEFAULT_RENEW_GRANT);

       // create an owner for the lease that will throw a definite exception
       // create an owner to for testing definite exceptions
       Exception ex = new IllegalArgumentException("EventSequenceFailTest");
       owner = new FailingOpCountingOwner(ex, 0, renewGrant);

       // logs events as they arrive
       rrl01 = new RenewingRemoteListener(getExporter(), renewGrant);
       rrl02 = new RenewingRemoteListener(getExporter(), renewGrant);
    }


    /**
     * Assert that the events of the RenewalFailureEvent type sent to a
     * newly registered listener are in the same sequence as those sent to
     * the previously registered listener.
     * 
     * @return the test Status (passed or failed)
     */
    public void run() throws Exception {

	// Announce where we are in the test
	logger.log(Level.FINE, "EventSequenceFailTest: In run() method.");

	// get a lease renewal set w/ duration for as long as we can
	logger.log(Level.FINE, "Creating the lease renewal set with duration" +
			  " of Lease.FOREVER");
	LeaseRenewalService lrs = getLRS();
	LeaseRenewalSet set = lrs.createLeaseRenewalSet(Lease.FOREVER);
	set = prepareSet(set);
	
	// register listener #1 to receive events
	logger.log(Level.FINE, "Registering listener #1 for renewal failure" +
			  " events.");
	set.setRenewalFailureListener(rrl01, null);

	/* create 8 test leases which will throw definite exceptions
	   and add then to the renewal set. Each lease's expriation
	   time is 30 seconds greater than the previous one. */
	long duration = 0;
	TestLease[] lease = new TestLease[8];
	    TestLeaseProvider leaseProvider = null;
	for (int i = 0; i < 8; ++i) {
	    duration = renewGrant * (i+1);
	    logger.log(Level.FINE, "Creating lease #" + i + 
			      " with duration of " + duration + 
			      " milliseconds.");
	    leaseProvider = (i < 4) ? earlyLeaseProvider : 
				      lateLeaseProvider;
	    lease[i] = 
		leaseProvider.createNewLease(owner, 
					     rstUtil.durToExp(duration));
	    logger.log(Level.FINE, "Adding lease #" + i + " to renewal set " +
			      "with membership duration = Lease.FOREVER.");
	    set.renewFor(lease[i], Lease.FOREVER);
	}

	// wait for the listener to receive four events.
	long timeOut = duration;
	rstUtil.waitForRemoteEvents(rrl01, 4, timeOut);

	// assign the other listener to receive events
	logger.log(Level.FINE, "Replacing listener #1 with listener #2 to" +
			  " receive renewal failure events.");
	set.setRenewalFailureListener(rrl02, null);

	// wait for the other listener to receive four events.
	rstUtil.waitForRemoteEvents(rrl02, 4, timeOut);

	// report on how many events were received by each (must be >= 3)
	RemoteEvent[] events01 = rrl01.getEvents();
	RemoteEvent[] events02 = rrl02.getEvents();

	if (events01.length < 4) {
	    String message = "Listener #1 received " + events01.length +
		" events but is required to receive exactly 4.";
	    throw new TestException(message);
	}
	logger.log(Level.FINE, "Listener #1 received " + events01.length +
			  " RenewalFailureEvents.");

	if (events02.length < 4) {
	    String message = "Listener #2 received " + events02.length +
		" events but is required to receive exactly 4.";
	    throw new TestException(message);
	}
	logger.log(Level.FINE, "Listener #2 received " + events02.length +
			  " RenewalFailureEvents.");

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

	// next sequenceSet02
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
	   for the LRS to send the same event twice (for example in the 
	   case where a partial failure exists). If there is an overlap,
	   we will allow it if and only if the leases encapsulated by 
	   the two events are the same (ergo, the events are the same). */
	iter = sequenceSet01.iterator();
	while (iter.hasNext()) {
	    Long nextLong = (Long) iter.next();

	    if (sequenceSet02.contains(nextLong) && 
		(notSameEvent(events01, events02, nextLong.longValue()))) {
		String message = "Event sequence numbers overlap between" +
		    " listener #1 and listener #2.";
		throw new TestException(message);
	    }
	}	     
    }

    /**
     * Determines if two renewal failure events with the same sequence
     * number are in fact different events.
     * 
     * <P>Notes:</P>
     * 
     * @param set01  Set containing the sequence numbers (sorted).
     * @param evtArr01 An array containg events whose sequence numbers are in
     *                 set01.
     * @param set02  Set containing the sequence numbers (sorted).
     * @param evtArr02 An array containg events whose sequence numbers are in
     *                 set02.
     * @param seqNum The sequence number that is overlapping in the sets.
     * 
     * @return true if the overlap involves two different events with the
     *              same sequence number. false otherwise.
     * 
     */
    private boolean notSameEvent(RemoteEvent[] evtArr01, 
				 RemoteEvent[] evtArr02, long seqNum) 
                throws IOException, ClassNotFoundException {
	int evtIndex01 = indexOf(evtArr01, seqNum);
	int evtIndex02 = indexOf(evtArr02, seqNum);
	/* we assume the above two calls will always return a non-negative 
	   number since this is a private method only used in this class. */

	// capture the two event whose sequence numbers overlap
	RenewalFailureEvent evt01 = (RenewalFailureEvent) evtArr01[evtIndex01];
	RenewalFailureEvent evt02 = (RenewalFailureEvent) evtArr02[evtIndex02];
	
	// they are the same if the leases that failed are the same
	Lease lease01 = evt01.getLease();
	Lease lease02 = evt02.getLease();

	// return the truth of the matter
	return lease01.equals(lease02) == false;
    }

    /**
     * Return the index in the event array where the sequenceNumber is found.
     * 
     * @param eventArr the array of RemoteEvent containing events for search.
     * @param sequenceNumber the sequence number of the event whose index is 
     *                       sought in the array.
     *
     * @return the index in eventArr where sequenceNumber is located or -1 
     *         if not found.
     * 
     */
    private int indexOf(RemoteEvent[] eventArr, long sequenceNumber) {
	for(int i = 0; i < eventArr.length; ++i) {
	    if (eventArr[i].getSequenceNumber() == sequenceNumber) {
		return i;
	    }
	}

	// no can find (in the context of this test we should never be here)
	return -1;
    }

} // EventSequenceFailTest













