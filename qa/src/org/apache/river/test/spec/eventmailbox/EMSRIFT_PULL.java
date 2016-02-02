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
import java.util.Date;

import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

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

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.Test;

import org.apache.river.test.impl.mercury.EMSTestBase;
import org.apache.river.test.impl.mercury.TestUtils;
import org.apache.river.test.impl.mercury.TestListener;
import org.apache.river.test.impl.mercury.TestPullListener;
import org.apache.river.test.impl.mercury.TestPullListenerImpl;
import org.apache.river.test.impl.mercury.TestGenerator;


public class EMSRIFT_PULL extends EMSTestBase implements TimeConstants {

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

	// Create "listener" to collect events for this test
	TestPullListener tpl = TestUtils.createPullListener(getManager());
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

	logger.log(Level.INFO, "Wating for event delivery");
	eventCount = NUM_EVENTS;
        getCollectedRemoteEvents(tpl, mr, eventCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery count of " + eventCount);
	assertCount(tpl, eventCount);
	logger.log(Level.INFO, "Verifying events ");
	assertEvents(tpl, events);
	if(tpl.verifyEvents(bogus)) {
	    throw new TestException("Successfully verified bogus events");
	}

        // Get iterator handle before cancelling associated reg
        RemoteEventIterator rei = mr.getRemoteEvents();
        
	logger.log(Level.INFO, "Cancelling registration lease");
	mrl.cancel();

	logger.log(Level.INFO, "Generating " + NUM_EVENTS + " more events");
	try {
	    for (i = 0; i < NUM_EVENTS; i++) {
	        events[i] = myGen.generateEvent(evtReg.getID(), 3);
	    }
	} catch (ServerException se) {
	    Throwable detail = se.getCause();
	    if (detail != null &&
	        detail instanceof NoSuchObjectException) {
		// can safely ignore this since we expect
		// that the registration has expired.
	        logger.log(Level.INFO, "Caught NoSuchObjectException - expected");
	    } else { throw se; }
	}
        
        // Would like to assert that the event count hasn't changed, but
        // invoking getRemoteEvents should fail with NSOE, as above.

	try {
	    logger.log(Level.INFO, "Re-cancelling registration lease");
	    mrl.cancel();
	    throw new TestException("Successfully cancelled a cancelled registration");
	} catch (UnknownLeaseException ule) {
	    logger.log(Level.INFO, "Caught UnknownLeaseException - expected");
	}
        
	try {
	    logger.log(Level.INFO, "Calling getRemoteEvents on expired reg");
	    mr.getRemoteEvents();
	    throw new TestException("Successfully called a cancelled registration");
	} catch (NoSuchObjectException nsoe) {
	    logger.log(Level.INFO, "Caught NoSuchObjectException - expected");
	}
        
	try {
	    logger.log(Level.INFO, "Calling addUnknownEvents on expired reg");
	    mr.addUnknownEvents(new java.util.ArrayList());
	    throw new TestException("Successfully called a cancelled registration");
	} catch (NoSuchObjectException nsoe) {
	    logger.log(Level.INFO, "Caught NoSuchObjectException - expected");
	}
        
        logger.log(Level.INFO, "Calling next() on expired reg iterator");
        RemoteEvent re;
        boolean done = false;
        int exceptionCount = 0;
        while (!done) {
           try {
               re = rei.next(MAX_WAIT);
               if (re != null) {
                   logger.log(Level.INFO, "Got RemoteEvent", re);
               } else {
                   throw new TestException(
                        "Successfully iterated through an expired iter ref");
               }
            } catch (NoSuchObjectException nsoe) {
                logger.log(Level.INFO, "Caught NoSuchObjectException - expected");
                done = true;
            } catch (RemoteException rex) {
                // log and retry
                logger.log(Level.INFO, "Caught RemoteException", rex);
                if (exceptionCount++ >= 10) {
                    throw new TestException(
                        "Too many remote exceptions received");
                }
            } 
        }

        logger.log(Level.INFO, "Calling close() on expired reg iterator");
	rei.close();
       	try {
	    logger.log(Level.INFO, "Calling close() again on expired reg iterator");
	    rei.close();
	    throw new TestException("Successfully called a close twice");
	} catch (InvalidIteratorException iie) {
	    logger.log(Level.INFO, "Caught InvalidIteratorException - expected");
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
