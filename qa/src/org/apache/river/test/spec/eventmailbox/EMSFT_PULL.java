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

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.Test;

import net.jini.event.PullEventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import org.apache.river.test.impl.mercury.EMSTestBase;
import org.apache.river.test.impl.mercury.TestUtils;
import org.apache.river.test.impl.mercury.TestListener;
import org.apache.river.test.impl.mercury.TestGenerator;
import org.apache.river.test.impl.mercury.TestPullListener;
import org.apache.river.test.impl.mercury.TestPullListenerImpl;

/**
 * EventMailboxServiceFunctionalTest
 * Tests various modes of operation. Tests that events are properly
 * receiveed by the client using the pull interface.
 */
public class EMSFT_PULL extends EMSTestBase implements TimeConstants {

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
	int i = 0;
	PullEventMailbox mb = getPullMailbox();        

	// Register and check lease
	MailboxPullRegistration mr = getPullRegistration(mb, DURATION1);
        Lease mrl = getPullMailboxLease(mr);
	checkLease(mrl, DURATION1); 

	logger.log(Level.INFO, "Getting pull mailbox listener");
	RemoteEventListener mbRel = getPullMailboxListener(mr);

	// Create an event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen = TestUtils.createGenerator(getManager());
	EventRegistration evtReg = 
	    myGen.register(EVENT_ID,     	// Event ID to use
			   null,  		// handback
			   mbRel,	        // Notification target
			   DURATION1);	        // Lease duration
	Lease tgl = evtReg.getLease();
	checkLease(tgl, DURATION1); 

	// Create another event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen2 = TestUtils.createGenerator(getManager());
	EventRegistration evtReg2 = 
	    myGen2.register(EVENT_ID2,     	// Event ID to use
			    null,  		// handback
			    mbRel,	        // Notification target
			    DURATION1);	        // Lease duration
	Lease tgl2 = evtReg2.getLease();
	checkLease(tgl2, DURATION1); 

	// Create "listener" to collect events for this test
	TestPullListener tpl = TestUtils.createPullListener(getManager());
	int myTplCount = 0;

	// Generate some events from first generator
	RemoteEvent[] events = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}
	myTplCount += NUM_EVENTS;

	RemoteEvent[] bogus = {
	    new RemoteEvent(myGen, 9999, 9999, null),
	    new RemoteEvent(myGen2, 1234, 1, null),
	};

        // Collect events and verify
	logger.log(Level.INFO, "Getting events");
	getCollectedRemoteEvents(tpl, mr, myTplCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(tpl, myTplCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvents(tpl, events);

        // Generate some more events
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}
	myTplCount += NUM_EVENTS;
	logger.log(Level.INFO, "Getting more events");
	getCollectedRemoteEvents(tpl, mr, myTplCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(tpl, myTplCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvents(tpl, events);
        
	// Generate some more events from second generator
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen2.generateEvent(evtReg2.getID(), 3);
	}
	myTplCount += NUM_EVENTS;
	logger.log(Level.INFO, "Getting more events");
	getCollectedRemoteEvents(tpl, mr, myTplCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(tpl, myTplCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvents(tpl, events);

	// Generate some more events from each generator
	logger.log(Level.INFO, "Generating more events");
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}

	RemoteEvent[] events2 = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events2[i] = myGen2.generateEvent(evtReg2.getID(), 3);
	}

	myTplCount += NUM_EVENTS*2;
	// Collect events and verify
	logger.log(Level.INFO, "Getting more events");
	getCollectedRemoteEvents(tpl, mr, myTplCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(tpl, myTplCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvents(tpl, events);
	assertEvents(tpl, events2);

	logger.log(Level.INFO, "Cancelling registration lease");
	mrl.cancel();

	logger.log(Level.INFO, "Generating another event");
        RemoteEvent evt;
	try {
	    evt = myGen.generateEvent(evtReg.getID(), 3);
	    throw new TestException("Successfully sent an event to "
				  + "an expired registration");
	} catch (ServerException e) {
	    logger.log(Level.INFO, "Caught ServerException", e);
	    Throwable detail = e.getCause();
	    if (detail == null || 
		!(detail instanceof NoSuchObjectException)) {
		throw new TestException("Unexpected ServerException", e);
	    }
	}
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
