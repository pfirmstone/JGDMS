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
package com.sun.jini.test.spec.eventmailbox;

import java.util.logging.Level;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.ServerException;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.constants.TimeConstants;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;
import com.sun.jini.test.impl.mercury.TestGenerator;

/**
 * EventMailboxServiceFunctionalTest
 * Tests various modes of operation. Tests that events are sent only to
 * the currently enabled event listener, if any. 
 */
public class EMSFT extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 3*HOURS;

    private final int NUM_EVENTS = 5;

    private final long EVENT_ID = 1234;
    private final long EVENT_ID2 = 2468;

    private final long MAX_WAIT = 60 * 1000;

    public void run() throws Exception {
	EventMailbox mb = getConfiguredMailbox();        
	int i = 0;

	// Register and check lease
	MailboxRegistration mr = getRegistration(mb, DURATION1);
	Lease mrl = getMailboxLease(mr);
	checkLease(mrl, DURATION1); 

	// Get the mailbox service provided listener
	RemoteEventListener mbRel = getMailboxListener(mr);

	// Create an event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen = TestUtils.createGenerator(manager);
	EventRegistration evtReg = 
	    myGen.register(EVENT_ID,     	// Event ID to use
			   null,  		// handback
			   mbRel,	        // Notification target
			   DURATION1);	// Lease duration
	Lease tgl = evtReg.getLease();
	checkLease(tgl, DURATION1); 

	// Create another event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen2 = TestUtils.createGenerator(manager);
	EventRegistration evtReg2 = 
	    myGen2.register(EVENT_ID2,     	// Event ID to use
			    null,  		// handback
			    mbRel,	        // Notification target
			    DURATION1);	// Lease duration
	Lease tgl2 = evtReg2.getLease();
	checkLease(tgl2, DURATION1); 

	// Create two listener objects
	TestListener myRel = TestUtils.createListener(manager);
	TestListener myRel2 = TestUtils.createListener(manager);
	int myRelCount = 0, myRelCount2 = 0;

	// Generate some events from first generator
	RemoteEvent[] events = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}
	myRelCount += NUM_EVENTS;

	RemoteEvent[] bogus = {
	    new RemoteEvent(myGen, 9999, 9999, null),
	    new RemoteEvent(myGen2, 1234, 1, null),
	};

	// Enable the first of our listener objects
	assertCount(myRel, 0);
	logger.log(Level.INFO, "Enabling delivery to our REL" + myRel);
	mr.enableDelivery(myRel);

	    // Wait for events to arrive and verify
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(myRel, myRelCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(myRel, myRelCount);
	assertEvents(myRel, events);
	if (myRel.verifyEvents(bogus)) {
	    throw new TestException("Successfully verified bogus events");
	}

	// Re-submit listener again
	logger.log(Level.INFO, "Re-enabling delivery to our REL");
	mr.enableDelivery(myRel);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(myRel, myRelCount);

	    // Generate some more events
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}
	myRelCount += NUM_EVENTS;
	waitForEvents(myRel, myRelCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(myRel, myRelCount);
	assertEvents(myRel, events);
	// enable second listener object
	logger.log(Level.INFO, "Enabling delivery to our REL2" + myRel2);
	mr.enableDelivery(myRel2);
	assertCount(myRel2, myRelCount2);

	// Generate some more events 
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}
	myRelCount2 += NUM_EVENTS;

	    // Wait for events to arrive and verify
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(myRel2, myRelCount2, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(myRel2, myRelCount2);
	assertEvents(myRel2, events);

	// Generate some more events from second generator
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen2.generateEvent(evtReg2.getID(), 3);
	}
	myRelCount2 += NUM_EVENTS;
	// Wait for events to arrive and verify
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(myRel2, myRelCount2, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(myRel2, myRelCount2);
	assertEvents(myRel2, events);
	// disable delivery
	mr.disableDelivery();
	assertCount(myRel, myRelCount);
	assertCount(myRel2, myRelCount2);
	logger.log(Level.INFO, "Disabled delivery");

	    // Generate some more events from each generator
	logger.log(Level.INFO, "Generating more events");
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}

	RemoteEvent[] events2 = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events2[i] = myGen2.generateEvent(evtReg2.getID(), 3);
	}

	logger.log(Level.INFO, "Verifying that event counts haven't changed");
	assertCount(myRel, myRelCount);
	assertCount(myRel2, myRelCount2);

	logger.log(Level.INFO, "enabling delivery to REL");
	mr.enableDelivery(myRel);
	myRelCount += NUM_EVENTS*2;

	// Wait for events to arrive and verify
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(myRel, myRelCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(myRel, myRelCount);
	assertEvents(myRel, events);
	assertEvents(myRel, events2);

	// Submit the null listener
	logger.log(Level.INFO, "Calling enableDelivery(null)");
	mr.enableDelivery(null);
	logger.log(Level.INFO, "Asserting that event counts haven't changed");
	assertCount(myRel, myRelCount);
	assertCount(myRel2, myRelCount2);

	// Generate another event 
	logger.log(Level.INFO, "Generating another event");
	RemoteEvent evt = myGen.generateEvent(evtReg.getID(), 3);

	logger.log(Level.INFO, "Asserting that event counts haven't changed");
	assertCount(myRel, myRelCount);
	assertCount(myRel2, myRelCount2);

	logger.log(Level.INFO, "Enabling REL2");
	myRelCount2 += 1;
	mr.enableDelivery(myRel2);
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(myRel2, myRelCount2, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event counts ");
	assertCount(myRel2, myRelCount2);
	assertEvent(myRel2, evt);
	assertCount(myRel, myRelCount);

	logger.log(Level.INFO, "Disabling delivery");
	mr.disableDelivery();
	logger.log(Level.INFO, "Asserting event counts ");
	assertCount(myRel, myRelCount);
	assertCount(myRel2, myRelCount2);

	    // Generate another event 
	logger.log(Level.INFO, "Generating another event");
	evt = myGen.generateEvent(evtReg.getID(), 3);

	logger.log(Level.INFO, "Disabling delivery again");
	mr.disableDelivery();
	logger.log(Level.INFO, "Asserting event counts ");
	assertCount(myRel, myRelCount);
	assertCount(myRel2, myRelCount2);

	// Generate another event 
	logger.log(Level.INFO, "Generating another event");
	RemoteEvent evt2 = myGen2.generateEvent(evtReg2.getID(), 3);
	assertCount(myRel, myRelCount);
	assertCount(myRel2, myRelCount2);

	logger.log(Level.INFO, "Enabling REL2 ");
	mr.enableDelivery(myRel2);
	myRelCount2 += 2;

	logger.log(Level.INFO, "Waiting for event delivery");
	waitForEvents(myRel2, myRelCount2, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(myRel2, myRelCount2);
	assertEvent(myRel2, evt);
	assertEvent(myRel2, evt2);
	logger.log(Level.INFO, "Cancelling registration lease");
	mrl.cancel();

	logger.log(Level.INFO, "Generating another event");
	try {
	    evt = myGen.generateEvent(evtReg.getID(), 3);
	    throw new TestException("Successfully sent an event to "
				  + "an expired registration");
	} catch (ServerException e) {
	    logger.log(Level.INFO, "Caught ServerException");
	    if (e.detail == null || 
		!(e.detail instanceof NoSuchObjectException)) {
		throw new TestException("Unexpected exception", e);
	    }
	}
	assertCount(myRel, myRelCount);
	assertCount(myRel2, myRelCount2);
    }

    /**
     * Invoke parent's setup and parser
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig config) throws Exception {
	super.setup(config);
	parse();
    }
}
