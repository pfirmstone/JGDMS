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
import java.util.Date;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import com.sun.jini.constants.TimeConstants;

import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;
import com.sun.jini.test.impl.mercury.TestGenerator;

/**
 * EventMailboxServiceConcurrencyExceptionTest
 * Creates an event listener that (concurrently) calls disableDelivery 
 * in its notify() method. Test verifies that only the first event
 * gets delivered and that the concurrent disable call doesn't 
 * deadlock the mailbox service. The listener's notify() also 
 * throws an exception after calling disable.
 */
public class EMSCET extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 3*MINUTES;

    private final int NUM_EVENTS = 2;

    private final long EVENT_ID = 1234;

    private final long MAX_WAIT = 60 * 1000;

    public void run() throws Exception {
	EventMailbox mb = getConfiguredMailbox();        
	int i = 0;

	// Register and check lease
	MailboxRegistration mr = getRegistration(mb, DURATION1);
	logger.log(Level.INFO, "Got registration ref {0}", mr);
        Lease mrl = getMailboxLease(mr);
	logger.log(Level.INFO, "Got lease ref {0}", mrl);
        checkLease(mrl, DURATION1); 
	logger.log(Level.INFO, "Mailbox lease good until " 
		    + new Date(mrl.getExpiration()));

	// Get the mailbox service provided listener
	RemoteEventListener mbRel = getMailboxListener(mr);
	logger.log(Level.INFO, "Got RemoteEventListener ref {0}", mbRel);
        
	// Create an event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen = TestUtils.createGenerator(manager);
	logger.log(Level.INFO, "Got TestGenerator ref {0}", myGen);
	EventRegistration evtReg = 
	    myGen.register(EVENT_ID,     	// Event ID to use
			   null,  		// handback
			   mbRel,	        // Notification target
			   DURATION1);	// Lease duration
	logger.log(Level.INFO, "Got EventRegistration ref {0}", evtReg);
	Lease tgl = evtReg.getLease();
	checkLease(tgl, DURATION1); 
	logger.log(Level.INFO, "EventRegistration lease good until " 
		    + new Date(tgl.getExpiration()));

	// Create two listener objects
	TestListener myListener = 
	    TestUtils.createDisableNSOListener(manager, mr);
	logger.log(Level.INFO, "Got TestListener ref {0}", myListener);
	int eventCount = 0;

	// Generate some events 
	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " events");
	RemoteEvent[] events = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}

	RemoteEvent[] bogus = {
	    new RemoteEvent(myGen, 9999, 9999, null),
	    new RemoteEvent(myGen, 5678, 1234, null),
	};

	// Enable the first of our listener objects
	assertCount(myListener, eventCount);
	logger.log(Level.INFO, "Enabling delivery to our REL" + myListener);
	mr.enableDelivery(myListener);

        // Add an extra delay here since we'll only block
	// on the receipt of one event, but we really want 
	// to see if a second event is sent as well.
	logger.log(Level.INFO, "sleeping for 5 sec");
	try {
	    Thread.sleep(5000);
	} catch (InterruptedException ie) {
	    logger.log(Level.INFO, "waking up early - interrupted");
	}
	logger.log(Level.INFO, "awoken");

	logger.log(Level.INFO, "Wating for event delivery");
	eventCount = 1;
        waitForEvents(myListener, eventCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery count of " + eventCount);
	assertCount(myListener, eventCount);
	logger.log(Level.INFO, "Verifying events ");
	assertEvent(myListener, events[0]);
	if(myListener.verifyEvents(bogus)) {
	    throw new TestException("Successfully verified bogus events");
	}

	mrl.cancel();
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
