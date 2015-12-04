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

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.Test;

import org.apache.river.test.impl.mercury.EMSTestBase;
import org.apache.river.test.impl.mercury.TestUtils;
import org.apache.river.test.impl.mercury.TestListener;
import org.apache.river.test.impl.mercury.TestGenerator;


public class EMSST3 extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 2*MINUTES;

    private final int NUM_EVENTS = 5;

    private final long EVENT_ID = 1234;

    private final long MAX_WAIT = 10 * 1000;

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

	TestListener goodListener = TestUtils.createListener(getManager());
	int goodCount = 0;
	int badCount = 0;

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

	logger.log(Level.INFO, "Cancelling registration lease.");
	mrl.cancel();

	// 
	// Kill event mailbox service
	//
	shutdown(0);

	// Enable the first of our listener objects
	try { 
	    // Enable good listener
	    logger.log(Level.INFO, "Enabling good listener");
	    mr.enableDelivery(goodListener);
	    throw new TestException("Able to interact with "
				  + "expired registration");
	} catch (NoSuchObjectException ne) {
	    logger.log(Level.INFO, 
		       "Caught a NoSuchObjectException -- expected");
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
