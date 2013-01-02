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

import net.jini.event.InvalidIteratorException;
import net.jini.event.RemoteEventIterator;
import net.jini.event.PullEventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import com.sun.jini.constants.TimeConstants;
import com.sun.jini.qa.harness.Test;

import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;
import com.sun.jini.test.impl.mercury.TestPullListener;
import com.sun.jini.test.impl.mercury.TestPullListenerImpl;
import com.sun.jini.test.impl.mercury.TestGenerator;


public class EMSRIFT_PULL3 extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 3*MINUTES;

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
	logger.log(Level.INFO, "Mailbox lease good until" 
		    + new Date(mrl.getExpiration()));

	// Get the mailbox service provided listener
	RemoteEventListener mbRel = getPullMailboxListener(mr);

	// Create an event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen = TestUtils.createGenerator(getManager());
	EventRegistration evtReg = 
	    myGen.register(EVENT_ID,     	// Event ID to use
			   null,  		// handback
			   mbRel,	        // Notification target
			   DURATION1);	// Lease duration
	Lease tgl = evtReg.getLease();
	checkLease(tgl, DURATION1); 

	// Generate some events 
	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " events");
	RemoteEvent[] events = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}

        // Get iterator handle and exercise it
        RemoteEventIterator rei = mr.getRemoteEvents();
        rei.next(MAX_WAIT); // ignore result
        
	// Generate some more events 
	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " events");
	events = new RemoteEvent[NUM_EVENTS];
	for (i = 0; i < NUM_EVENTS; i++) {
	    events[i] = myGen.generateEvent(evtReg.getID(), 3);
	}
        
        // Get new iterator handle and exercise it
        RemoteEventIterator rei_2 = mr.getRemoteEvents();
        rei_2.next(MAX_WAIT); // ignore result
        
        if (rei == rei_2 ||
            rei.equals(rei_2) ||
            rei_2.equals(rei)) {
                throw new TestException(
                    "Remote event iterators weren't unique");
        }
        
        //New iterator should eventually invalidate the old iterator
        try {
            RemoteEvent ev;
            while ((ev = rei.next(MAX_WAIT)) != null) {
	        logger.log(Level.INFO, 
                    "Retreived event through invalid iter: {0}", ev);
            }
            throw new TestException(
                "Successfully iterated through invalid iterator");
        } catch (InvalidIteratorException iie) {
	    logger.log(Level.INFO, 
                "Caught InvalidIteratorException -- expected", iie);
        }
        
        //New iterator should still be valid
        rei_2.next(MAX_WAIT);
        
        //Verify that old iterator is still invalid for all methods
        try {
            RemoteEvent ev;
            while ((ev = rei.next(MAX_WAIT)) != null) {
	        logger.log(Level.INFO, 
                    "Retreived event through invalid iter: {0}", ev);
            }
            throw new TestException(
                "Successfully iterated through invalid iterator");
        } catch (InvalidIteratorException iie) {
	    logger.log(Level.INFO, 
                "Caught InvalidIteratorException on next() call -- expected", 
                iie);
        }
        
        //Verify that old iterator is still invalid for all methods
        try {
            rei.close();
            throw new TestException(
                "Successfully called close on invalid iterator");
        } catch (InvalidIteratorException iie) {
	    logger.log(Level.INFO, 
                "Caught InvalidIteratorException on close() call -- expected", 
                iie);
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
