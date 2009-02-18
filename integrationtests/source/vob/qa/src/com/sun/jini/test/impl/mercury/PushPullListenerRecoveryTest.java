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

import net.jini.event.InvalidIteratorException;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.event.RemoteEventIterator;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import com.sun.jini.constants.TimeConstants;

import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;
import com.sun.jini.test.impl.mercury.TestGenerator;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

public class PushPullListenerRecoveryTest 
    extends EMSTestBase implements TimeConstants 
{

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 3*HOURS;

    private final int NUM_EVENTS = 5;

    private final long EVENT_ID = 1234;

    private final long MAX_WAIT = 600 * 1000;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	PullEventMailbox mb = getPullMailbox();
	int i = 0;

	// Register and check lease
	MailboxPullRegistration mr = getPullRegistration(mb, DURATION1);
	Lease mrl = getPullMailboxLease(mr);
	checkLease(mrl, DURATION1); 

	// Get the mailbox service provided listener
	RemoteEventListener mbRel = getPullMailboxListener(mr);

	// Create an event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen = TestUtils.createGenerator(manager);
	logger.log(Level.FINEST, 
	    "Test generator class tree" 
	    + getClassLoaderTree(myGen.getClass().getClassLoader()));
	EventRegistration evtReg = 
	    myGen.register(EVENT_ID,	// Event ID to use
			   null,		// handback
			   mbRel,		// Notification target
			   DURATION1);	// Lease duration
	Lease tgl = evtReg.getLease();
	checkLease(tgl, DURATION1); 

	TestListener goodListener = TestUtils.createListener(manager);
	int goodCount = 0;
        int goodPullCount = 0;

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

	// 
	// Kill event mailbox service
	//
	logger.log(Level.INFO, "Killing mailbox service ...");
	shutdown(0);

	// Enable good listener
	logger.log(Level.INFO, "Enabling good listener");
	mr.enableDelivery(goodListener);

	goodCount = NUM_EVENTS;
	logger.log(Level.INFO, "Wating for event delivery of " + goodCount);
        waitForEvents(goodListener, goodCount, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event count");
	assertCount(goodListener, goodCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvents(goodListener, events);

	// 
	// Kill event mailbox service
	//
	shutdown(0);

        // Should now be using recovered listener with
	// recovered preparer
	logger.log(Level.INFO, "Generating another event");
	RemoteEvent re = myGen.generateEvent(evtReg.getID(), 3);
	goodCount++;
	logger.log(Level.INFO, "Wating for event delivery" + goodCount);
        waitForEvents(goodListener, goodCount, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event count");
	assertCount(goodListener, goodCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvent(goodListener, re);

        // Switch to pull interface -- should disable event listener
	logger.log(Level.INFO, "Switching to pull listener -- not expecting any events.");
	goodPullCount = 0;        
        RemoteEventIterator rei = mr.getRemoteEvents();
        RemoteEvent rei_event = rei.next(MAX_WAIT);
        if (rei_event != null) {
            throw new TestException("Got event from empty iterator " + rei_event);
        }
	logger.log(Level.INFO, "Event iterator was empty -- OK");

	// 
	// Kill event mailbox service
	//
	shutdown(0);

        // Generate another event and verify that it isn't delivered
	logger.log(Level.INFO, "Generating another event");
	re = myGen.generateEvent(evtReg.getID(), 3);
	// Don't bump goodCount because it shouldn't be delivered
	logger.log(Level.INFO, "Wating for event delivery" + goodCount);
        // Force delay by waiting for goodCount+1 -- should not get an event, though
        waitForEvents(goodListener, goodCount+1, MAX_WAIT/NUM_EVENTS);
	logger.log(Level.INFO, "Asserting event count");
	assertCount(goodListener, goodCount);
        
	// 
	// Kill event mailbox service
	//
	shutdown(0);

	goodPullCount++;
	logger.log(Level.INFO, "Getting events. Expecting " + goodPullCount);
        rei_event  = rei.next(MAX_WAIT/NUM_EVENTS);
        if (rei_event == null ||
            !new RemoteEventHandle(rei_event).equals(new RemoteEventHandle(re))) {
            throw new TestException(
                "Didn't get expected event from iterator: " + rei_event);
        }
	logger.log(Level.INFO, "Got expected event from iterator");
	
        // Note checking for "empty" also forces the proxy the call the
        // the service back to see if there are anymore events, which will
        // also advance the read pointer past the last returned event. This
        // will prevent the "push" listener from seeing an extra event later on.
        rei_event  = rei.next(MAX_WAIT/NUM_EVENTS);
        if (rei_event != null) {
            throw new TestException(
                "Got unexpected event from empty iterator: " + rei_event);
        }
	logger.log(Level.INFO, "Verified iterator is empty");
	
        // Re-enable good listener. Should invalidate the iterator.
	logger.log(Level.INFO, "Re-enabling good listener");
	mr.enableDelivery(goodListener);
        
	logger.log(Level.INFO, "Trying to pull events from invalid iterator reference");
        try {
            rei.next(MAX_WAIT/NUM_EVENTS);
            throw new TestException("Successfully called invalid iterator.");
        } catch (InvalidIteratorException iie) {
 	    logger.log(Level.INFO, "Caught expected IllegalIteratorException", iie);           
        }
        
	// 
	// Kill event mailbox service
	//
	shutdown(0);
        
	logger.log(Level.INFO, "Generating another event");
	re = myGen.generateEvent(evtReg.getID(), 3);
	goodCount++;
	logger.log(Level.INFO, "Wating for event delivery" + goodCount);
        waitForEvents(goodListener, goodCount, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event count");
	assertCount(goodListener, goodCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvent(goodListener, re);

	//Negative check for event count
	try {
	    logger.log(Level.INFO, "Checking bad event count");
	    assertCount(goodListener, goodCount+1);
	} catch (TestException te) {
	    //ignore
	    logger.log(Level.INFO, "Caught expected exception");
	}
	//Negative check for event content
	try {
	    logger.log(Level.INFO, "Checking bad events");
	    assertEvents(goodListener, bogus);
	} catch (TestException te) {
	    //ignore
	    logger.log(Level.INFO, "Caught expected exception");
	}
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
