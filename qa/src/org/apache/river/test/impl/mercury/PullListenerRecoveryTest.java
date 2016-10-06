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
package org.apache.river.test.impl.mercury;

import java.util.logging.Level;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.ServerException;

import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.io.MarshalledInstance;

import org.apache.river.constants.TimeConstants;

import org.apache.river.test.impl.mercury.EMSTestBase;
import org.apache.river.test.impl.mercury.TestUtils;
import org.apache.river.test.impl.mercury.TestListener;
import org.apache.river.test.impl.mercury.TestGenerator;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

public class PullListenerRecoveryTest 
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

	TestPullListener goodListener = TestUtils.createPullListener(getManager());
	int goodCount = 0;

	// Generate some events 
	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " events");
	RemoteEvent[] events = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}

	RemoteEvent[] bogus = {
	    new RemoteEvent(myGen, 9999, 9999, (MarshalledInstance)null),
	    new RemoteEvent(myGen, 5678, 1234, (MarshalledInstance)null),
	};

	// 
	// Kill event mailbox service
	//
	logger.log(Level.INFO, "Killing mailbox service ...");
	shutdown(0);

	goodCount = NUM_EVENTS;
	logger.log(Level.INFO, "Getting events after service recovery. Expect " + goodCount);
        getCollectedRemoteEvents(goodListener, mr,goodCount, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event count");
	assertCount(goodListener, goodCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvents(goodListener, events);
	
	// 
	// Kill event mailbox service
	//
	shutdown(0);

        // Won't be using recovered listener, but still want to exercise
	// recovery logic with a "pull" registration.
	logger.log(Level.INFO, "Generating another event");
	RemoteEvent re = myGen.generateEvent(evtReg.getID(), 3);
	goodCount++;
	logger.log(Level.INFO, "Getting events. Expecting " + goodCount);
        getCollectedRemoteEvents(goodListener, mr, goodCount, MAX_WAIT);
	logger.log(Level.INFO, "Asserting event count");
	assertCount(goodListener, goodCount);
	logger.log(Level.INFO, "Verifying events");
	assertEvent(goodListener, re);
	
	//Negative check for event count
	try {
	    logger.log(Level.INFO, "Checking bad event count");
	    assertCount(goodListener, goodCount++);
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
