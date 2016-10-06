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
package org.apache.river.test.spec.eventmailbox;

import java.util.logging.Level;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.ServerException;

import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.io.MarshalledInstance;

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.Test;

import org.apache.river.test.impl.mercury.EMSTestBase;
import org.apache.river.test.impl.mercury.TestUtils;
import org.apache.river.test.impl.mercury.TestListener;
import org.apache.river.test.impl.mercury.TestGenerator;


public class EMSRET extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 3*HOURS;

    private final int NUM_EVENTS = 5;

    private final long EVENT_ID = 1234;

    // This number seems high, but the retry algorithm backs off
    // rather quickly (5s, 10s, 1min, ...).  Need this delay 
    // in order to successfully retrieve events after the mailbox
    // has backed off.
    private final long MAX_WAIT = 3 * MINUTES;

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
	TestGenerator myGen = TestUtils.createGenerator(getManager());
	EventRegistration evtReg = 
	    myGen.register(EVENT_ID,	// Event ID to use
			   null,		// handback
			   mbRel,		// Notification target
			   DURATION1);	// Lease duration
	Lease tgl = evtReg.getLease();
	checkLease(tgl, DURATION1); 

	// Create two listener objects
	TestListener badListener = TestUtils.createREListener(getManager());
	TestListener goodListener = TestUtils.createListener(getManager());
	int badCount = 0;
	int goodCount = 0;

	// Generate some events 
	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " events");
	RemoteEvent[] events = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}

	RemoteEvent[] bogus = {
	    new RemoteEvent(myGen, 9999, 9999, (MarshalledInstance) null),
	    new RemoteEvent(myGen, 5678, 1234, (MarshalledInstance) null),
	};

	// Enable the first of our listener objects
	assertCount(badListener, badCount);
	logger.log(Level.INFO, "Enabling delivery to our bad REL" + badListener);
	mr.enableDelivery(badListener);

        // Wait for events to arrive and verify.
	// Note that the listener object throws a 
	// RemoteException and can receive multiple
	// events (duplicate) events from redelivery
	// attempts.
	logger.log(Level.INFO, "Wating for event delivery");
	badCount = 1;
        waitForEvents(badListener, badCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery count of " 
	    + badCount);
	assertCount(badListener, badCount);
	logger.log(Level.INFO, "Verifying events ");
	assertEvent(badListener, events[0]);
	if (badListener.verifyEvents(events) ||
	    badListener.verifyEvents(bogus)    ) 
	{
	    throw new TestException("Successfully verified bogus events");
	}

        // Delay to (hopefully) ensure that 
        // some retries are attempted. This test
        // is timing dependent since after MAX_RETRIES 
        // the mailbox will discard the
        // remote event and move onto the next one, if any. 
        // If this delay is long enough to exceed the
        // MAX_RETRIES then the count will get bumped and
        // the test will fail.
        long delay = 7000;
	logger.log(Level.INFO, "Sleeping for " + delay);
	try {
            Thread.sleep(delay);
	} catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
	    logger.log(Level.INFO, "Sleep interrupted");
	} 

        // Note that the count should still be 1 since
        // retries will try to deliver the same event.
        // Listener counts distinct events. See note above.
	logger.log(Level.INFO, "Verifying event delivery count of " 
	    + badCount);
	assertCount(badListener, badCount);
	// No need to assertEvents since it be the same one
	// as before.

	// Enable good listener
	assertCount(goodListener, goodCount);
	logger.log(Level.INFO, "Enabling good listener");
	mr.enableDelivery(goodListener);

	goodCount = 5;
	logger.log(Level.INFO, "Wating for event delivery of " + goodCount);
        waitForEvents(goodListener, goodCount, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event count");
	assertCount(goodListener, goodCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvents(goodListener, events);

	logger.log(Level.INFO, "Generating another event");
	RemoteEvent re = myGen.generateEvent(evtReg.getID(), 3);
	goodCount++;
	logger.log(Level.INFO, "Wating for event delivery" + goodCount);
        waitForEvents(goodListener, goodCount, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event count");
	assertCount(goodListener, goodCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvent(goodListener, re);

	logger.log(Level.INFO, "Cancelling registration");
	mrl.cancel();
    }

    /**
     * Invoke parent's construct and parser
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig config) throws Exception {
	super.construct(config);
	parse();
        return this;
    }
}
