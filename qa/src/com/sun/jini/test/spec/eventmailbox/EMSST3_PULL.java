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

import net.jini.event.PullEventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import com.sun.jini.constants.TimeConstants;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestPullListener;
import com.sun.jini.test.impl.mercury.TestPullListenerImpl;
import com.sun.jini.test.impl.mercury.TestGenerator;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

public class EMSST3_PULL extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 2*MINUTES;

    private final int NUM_EVENTS = 5;

    private final long EVENT_ID = 1234;

    private final long MAX_WAIT = 60 * 1000;

    public void run() throws Exception {
	PullEventMailbox mb = getPullMailbox();        
	int i = 0;

	// Register and check lease
	MailboxPullRegistration mr = getPullRegistration(mb, DURATION1);
	Lease mrl = getPullMailboxLease(mr);
	checkLease(mrl, DURATION1); 

	// Create "listener" to collect events for this test
	TestPullListener tpl = TestUtils.createPullListener(getManager());

	logger.log(Level.INFO, "Getting pull mailbox listener");
	RemoteEventListener mbRel = getPullMailboxListener(mr);

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

	int goodCount = 0;

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
        
	logger.log(Level.INFO, "Cancelling registration lease");
        mrl.cancel();
        
	// 
	// Kill event mailbox service
	//
	shutdown(0);
        
	// Enable the first of our listener objects
	try { 
	    // Attempt to obtain events from a cancelled registration
	    logger.log(Level.INFO, "Getting events");
	    getCollectedRemoteEvents(tpl, mr, goodCount, MAX_WAIT);
	    throw new TestException("Able to interact with cancelled registration");
	} catch (ServerException e) {
	    logger.log(Level.INFO, "Caught a ServerException", e);
	    if (e.detail == null ||
		!(e.detail instanceof NoSuchObjectException)) {
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
