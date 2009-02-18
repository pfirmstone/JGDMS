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
package com.sun.jini.test.impl.mercury;

import java.util.logging.Level;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.ServerException;

import com.sun.jini.qa.harness.TestException;

import com.sun.jini.constants.TimeConstants;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

public class UnknownEventRecoveryShutdownTest extends EMSTestBase 
                                              implements TimeConstants 
{
    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION = 3*HOURS;

    private final int NUM_EVENTS = 5;

    private final long EVENT_ID = 1000;
    private final long EVENT_ID2 = 2000;

    private final long MAX_WAIT = 60 * 1000;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	String mbType =
            getConfig().getStringConfigVal(
		MAILBOX_PROPERTY_NAME,
		MAILBOX_IF_NAME);

	logger.log(Level.INFO, "Getting ref to " + mbType);
        MailboxRegistration mr = null;
	Lease mrl = null;
	if (mbType.equals(MAILBOX_IF_NAME)) {
	    EventMailbox mb = getMailbox();
	    mr = getRegistration(mb, DURATION);
	    mrl = getMailboxLease(mr);
	} else if (mbType.equals(PULL_MAILBOX_IF_NAME)) {
            PullEventMailbox pmb = getPullMailbox();
	    MailboxPullRegistration mpr = getPullRegistration(pmb, DURATION);
	    mrl = getPullMailboxLease(mpr);
            mr = mpr;
 	} else {
            throw new TestException(
		"Unsupported mailbox type requested" + mbType);
	}
        
	// Register and check lease
	checkLease(mrl, DURATION); 

	// Get the mailbox service provided listener
	RemoteEventListener mbRel = getMailboxListener(mr);

	// Create an event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen = TestUtils.createGenerator(manager);
	EventRegistration evtReg = 
	    myGen.register(EVENT_ID,	// Event ID to use
			   null,		// handback
			   mbRel,		// Notification target
			   DURATION);	// Lease duration
	Lease tgl = evtReg.getLease();
	checkLease(tgl, DURATION); 

	// Create another event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen2 = TestUtils.createGenerator(manager);
	EventRegistration evtReg2 = 
	    myGen2.register(EVENT_ID2,	// Event ID to use
			    null,		// handback
			    mbRel,		// Notification target
			    DURATION);	// Lease duration
	Lease tgl2 = evtReg2.getLease();
	checkLease(tgl2, DURATION); 

	// Create two listener objects
	TestListener goodRel = TestUtils.createListener(manager);
	TestListener badRel = TestUtils.createUEListener(manager);
	int goodRelCount = 0, badRelCount = 0;

	// Generate some events from both generators
	logger.log(Level.INFO, "Generating some events");
	RemoteEvent event = myGen.generateEvent(evtReg.getID(), 2);
	RemoteEvent event2 = myGen2.generateEvent(evtReg2.getID(), 2);
	goodRelCount += 2;

	RemoteEvent[] bogus = {
	    new RemoteEvent(myGen, 9999, 9999, null),
	    new RemoteEvent(myGen2, 1234, 1, null),
	};

	// Enable the first of our listener objects
	assertCount(goodRel, 0);
	logger.log(Level.INFO, "Enabling delivery to our REL" + goodRel);
	mr.enableDelivery(goodRel);
	
	// Wait for events to arrive and verify
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(goodRel, goodRelCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(goodRel, goodRelCount);
	assertEvent(goodRel, event);
	assertEvent(goodRel, event2);
	if (goodRel.verifyEvents(bogus)) {
	    throw new TestException("Successfully verified bogus events");
	}
	
	logger.log(Level.INFO, "Disabling event delivery");
	mr.disableDelivery();
	
	// Generate some more events
	logger.log(Level.INFO, "Generating some more events");
	event = myGen.generateEvent(evtReg.getID(), 3);
	event2 = myGen2.generateEvent(evtReg2.getID(), 3);
	badRelCount += 2;

	    // enable second listener object
	logger.log(Level.INFO, "Enabling delivery to our (bad) REL2" + badRel);
	mr.enableDelivery(badRel);

	    // Wait for events to arrive and verify
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(badRel, badRelCount, MAX_WAIT);
	assertCount(badRel, badRelCount);
	assertEvent(badRel, event);
	assertEvent(badRel, event2);

	// Generate some more events
	// This is timing dependent. It's possible to have recieved
	// the event (above) and before the mailbox has time to 
	// process the exception, the same event type is sent again.
	// In this case, the latter event will be accepted because 
	// the mailbox hasn't processed the intial exception. 
	// In either case, though, the event should never be received
	// by our listener objects since our implementation will fully
	// process a delivery for a registration before processing
	// another event for that process.
	logger.log(Level.INFO, "Generating some unknown events");
	try {
	    event = myGen.generateEvent(evtReg.getID(), 3);
	    throw new TestException("Succesfully sent an unknown event"); 
	} catch (UnknownEventException ue) {
	}
	try {
	    event2 = myGen2.generateEvent(evtReg2.getID(), 3);
	    throw new TestException("Succesfully sent an unknown event"); 
	} catch (UnknownEventException ue) {
	}

	// Note: count should not change since they should be
	// on the unknown event list.
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(badRel, badRelCount, MAX_WAIT);
	assertCount(badRel, badRelCount);

	// Kill the mailbox service
	shutdown(0);

	// Generate some more events
	logger.log(Level.INFO, "Generating some more events");
	try {
	    event = myGen.generateEvent(evtReg.getID(), 3);
	    throw new TestException("Succesfully sent an unknown event");
	} catch (UnknownEventException ue) {
	}
	try {
	    event2 = myGen2.generateEvent(evtReg2.getID(), 3);
	    throw new TestException("Succesfully sent an unknown event");
	} catch (UnknownEventException ue) {
	}
	
	// Note: count should not change since they should be
	// on the unknown event list.
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(badRel, badRelCount, MAX_WAIT);
	assertCount(badRel, badRelCount);
	
	logger.log(Level.INFO, "Re-enabling (good) REL" + goodRel);
	mr.enableDelivery(goodRel);
	logger.log(Level.INFO, "Asserting that event counts haven't changed");
	assertCount(goodRel, goodRelCount);
	assertCount(badRel, badRelCount);
	
	// Generate another event 
	logger.log(Level.INFO, "Generating another event");
	event = myGen.generateEvent(evtReg.getID(), 3);
	goodRelCount++;
	
	logger.log(Level.INFO, "Wating for event delivery");
	waitForEvents(goodRel, goodRelCount, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event counts ");
	assertCount(goodRel, goodRelCount);
	assertCount(badRel, badRelCount);
	assertEvent(goodRel, event);
	
	logger.log(Level.INFO, "Cancelling registration lease");
	mrl.cancel();
    }

    /**
     * Invoke parent's setup and parser
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	parse();
    }
}
