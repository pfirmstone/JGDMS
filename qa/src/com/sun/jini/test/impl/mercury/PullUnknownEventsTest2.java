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
import java.util.ArrayList;

import net.jini.event.InvalidIteratorException;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.event.RemoteEventIterator;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;

import com.sun.jini.constants.TimeConstants;

import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;
import com.sun.jini.test.impl.mercury.TestGenerator;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;
import com.sun.jini.qa.harness.TestException;

public class PullUnknownEventsTest2 
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
    private final long EVENT_ID2 = 5678;

    private final long MAX_WAIT = 60 * 1000;

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
	TestGenerator myGen = TestUtils.createGenerator(getManager());
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

	// Create another event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen2 = TestUtils.createGenerator(getManager());
	logger.log(Level.FINEST, 
	    "Test generator class tree" 
	    + getClassLoaderTree(myGen2.getClass().getClassLoader()));
	EventRegistration evtReg2 = 
	    myGen2.register(EVENT_ID2,	// Event ID to use
			   null,		// handback
			   mbRel,		// Notification target
			   DURATION1);	// Lease duration
	Lease tgl2 = evtReg2.getLease();
	checkLease(tgl2, DURATION1); 
        
        int expectedEventCount = 0;
	// Generate some events 
	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " events");
	ArrayList generatedEvents = new ArrayList();
        RemoteEvent[] genEvents = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    generatedEvents.add(myGen.generateEvent(evtReg.getID(), 3));
	}
        expectedEventCount += NUM_EVENTS;
        
	// Generate some events 
	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " events");
	for (i = 0; i < NUM_EVENTS; i++) {
	    generatedEvents.add(myGen2.generateEvent(evtReg2.getID(), 3));
	}
	logger.log(Level.INFO, "Sent events {0}", generatedEvents);        
        expectedEventCount += NUM_EVENTS;

        // Get events and verify
	logger.log(Level.INFO, "Getting events.");
        RemoteEventIterator rei = mr.getRemoteEvents();
        ArrayList receivedEvents = new ArrayList();
        RemoteEvent rei_event;
        while ((rei_event = rei.next(MAX_WAIT)) != null) {
            receivedEvents.add(rei_event);
        }
	logger.log(Level.INFO, "Received events {0}", receivedEvents);
        
 	logger.log(Level.INFO, "Verifying received events");
        assertEvents(generatedEvents, receivedEvents);

        if (receivedEvents.size() != expectedEventCount) {
            throw new TestException("Received " + receivedEvents.size()
                + " events, but expected " + expectedEventCount);
        }

	logger.log(Level.INFO, "Generating " + NUM_EVENTS 
            + " soon to be unknown events");
	for (i = 0; i < NUM_EVENTS; i++) {
	    genEvents[i] = myGen.generateEvent(evtReg.getID(), 3);
	}
	logger.log(Level.INFO, "Sent soon to be unknown events {0}", 
            java.util.Arrays.asList(genEvents));        
        
        // Set myGen events as unknown events
 	logger.log(Level.INFO, "Calling addUnknownEvents");
        mr.addUnknownEvents(java.util.Arrays.asList(genEvents));
        
        //Verify that events aren't delivered
        RemoteEvent re = null;
        if ((re = rei.next(MAX_WAIT)) != null) {
            throw new TestException("Unexpected event received " + re);
        }
  	logger.log(Level.INFO, "Did not receive any unknown events");

	// Generate some myGen2 events -- should be accepted
	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " events");
	for (i = 0; i < NUM_EVENTS; i++) {
	    generatedEvents.add(myGen2.generateEvent(evtReg2.getID(), 3));
	}
       expectedEventCount += NUM_EVENTS;
       
       logger.log(Level.INFO, "Sent events {0}", generatedEvents);                
        
        while ((rei_event = rei.next(MAX_WAIT)) != null) {
            receivedEvents.add(rei_event);
        }
	logger.log(Level.INFO, "Received events {0}", receivedEvents);
        
 	logger.log(Level.INFO, "Verifying received events");
        assertEvents(generatedEvents, receivedEvents);
        if (receivedEvents.size() != expectedEventCount) {
            throw new TestException("Received " + receivedEvents.size()
                + " events, but expected " + expectedEventCount);
        }

    }
    /**
     * Invoke parent's construct and parser
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	parse();
        return this;
    }
}
