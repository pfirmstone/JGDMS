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
import java.util.Date;
import java.util.ArrayList;
import java.util.logging.Logger;


import net.jini.event.InvalidIteratorException;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.event.RemoteEventIterator;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;

import org.apache.river.constants.TimeConstants;

import org.apache.river.test.impl.mercury.EMSTestBase;
import org.apache.river.test.impl.mercury.TestUtils;
import org.apache.river.test.impl.mercury.TestListener;
import org.apache.river.test.impl.mercury.TestGenerator;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

public class PullTimeoutTest 
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

    private final long MAX_WAIT_GET_EVENT = 60 * 1000;
    private final long MAX_WAIT_SEND_EVENT = MAX_WAIT_GET_EVENT / 4;
    
    class MyEventGeneratorRunnable implements Runnable {
        final TestGenerator myGen;
        final long evid;
        final int maxTries;
        final int numEvents;
        final Logger logger;
        final long delay;
        MyEventGeneratorRunnable(TestGenerator myGen, long evid, int maxTries, 
                int numEvents, Logger logger, long delay) 
        {
            this.myGen = myGen;
            this.evid = evid;
            this.maxTries = maxTries;
            this.numEvents = numEvents;  
            this.logger = logger;
            this.delay = delay;
        }
        public void run() {
            try {
                logger.log(Level.FINEST, 
                    "MyEventGeneratorRunnable sleeping @ {0} for {1} ms", 
                    new Object[] { new Date(), new Long(delay)});
                Thread.sleep(delay);
                logger.log(Level.FINEST, 
                    "MyEventGeneratorRunnable awoken @ {0}", new Date());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                // ignore
                logger.log(Level.FINEST, 
                    "Sleep interrupted", ie);
            }
            for (int i = 0; i < numEvents; i++) {
                try {
                    myGen.generateEvent(evid, maxTries);                    
                    logger.log(Level.FINEST, "Sent event @ {0}", new Date());
                } catch (RemoteException re) {
                   logger.log(Level.FINEST, 
                       "Ignoring RemoteException from generating event",
                           re);
                } catch (UnknownEventException uee) {
                   logger.log(Level.FINEST, 
                       "Ignoring UnknownEventException from generating event",
                           uee);
                }
            }
        }
    }

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
        
        // Get events and verify
	logger.log(Level.INFO, "Getting events from empty mailbox.");
        RemoteEventIterator rei = mr.getRemoteEvents();
        RemoteEvent rei_event;
        Date before = new Date();
	logger.log(Level.INFO, "Calling next() on empty set @ {0}", before);        
        rei_event = rei.next(MAX_WAIT_GET_EVENT);
        Date after = new Date();
	logger.log(Level.INFO, "Returned from next() @ {0}", after);   
        //Verify that timeout was honored
        long delta = after.getTime() - before.getTime();
        if (delta < MAX_WAIT_GET_EVENT) {
             throw new TestException("Returned from next() before expected: "
                     + delta);
        }
        if (rei_event != null) {
            throw new TestException(
                "Received unexpected event from empty mailbox: " + rei_event);
        }

        // Start event generator thread with a delay of MAX_WAIT_SEND_EVENT
        Thread t = 
            new Thread(
                new MyEventGeneratorRunnable(myGen, evtReg.getID(), 3, 1, 
                                             logger, MAX_WAIT_SEND_EVENT ));
        t.start();
        before = new Date();
	logger.log(Level.INFO, "Calling next() on empty set @ {0}", before);        
        rei_event = rei.next(MAX_WAIT_GET_EVENT);
        after = new Date();
        //Verify that we returned before the timeout
        delta = after.getTime() - before.getTime();        
	logger.log(Level.INFO, "Returned from next() @ {0}, delta = {1}", 
            new Object[] {after, new Long(delta)});   
        if (delta >= MAX_WAIT_GET_EVENT) {
             throw new TestException("Returned from next() after expected: " 
                     + delta);
        } else if (delta <= MAX_WAIT_SEND_EVENT) {
             throw new TestException("Returned from next() before expected: "
                     + delta);            
        }
        if (rei_event == null) {
            throw new TestException(
                "Did not receive expected event from mailbox.");
        }
            
        //Generate a soon-to-be unknown event.
        myGen.generateEvent(evtReg.getID(), 3);
        
        /*
         * Add genereated event to unknown list and verify that they aren't 
         * subsequently received.
         */
        ArrayList unknowns = new ArrayList(1);
        unknowns.add(rei_event);
        mr.addUnknownEvents(unknowns);
        
        before = new Date();
	logger.log(Level.INFO, "Calling next() on empty set @ {0}", before);        
        rei_event = rei.next(MAX_WAIT_GET_EVENT);
        after = new Date();
        //Verify that we returned after the timeout
        delta = after.getTime() - before.getTime();        
	logger.log(Level.INFO, "Returned from next() @ {0}, delta = {1}", 
            new Object[] {after, new Long(delta)});   
        if (delta < MAX_WAIT_GET_EVENT) {
             throw new TestException("Returned from next() before expected: " 
                     + delta);
        } 
        if (rei_event != null) {
            throw new TestException(
                "Received unexpected event from mailbox: " + rei_event);
        }
            
        // Start event generator thread with a delay of MAX_WAIT_SEND_EVENT
        // from a "good" event source.
        t = new Thread(
            new MyEventGeneratorRunnable(myGen2, evtReg2.getID(), 3, 1, 
                                         logger, MAX_WAIT_SEND_EVENT ));      
        t.start();
        // Call with "infinite" timeout.
        before = new Date();
	logger.log(Level.INFO, "Calling next() on empty set @ {0}", before);        
        rei_event = rei.next(Long.MAX_VALUE);
        after = new Date();
        delta = after.getTime() - before.getTime();        
	logger.log(Level.INFO, "Returned from next() @ {0}, delta = {1}", 
            new Object[] {after, new Long(delta)}); 
        if (delta <= MAX_WAIT_SEND_EVENT) {
             throw new TestException("Returned from next() before expected: "
                     + delta);            
        }
        /*
         * No need to check delta > Long.MAX_VALUE since the test would 
         * have been interrupted well before then.
         */
        if (rei_event == null) {
            throw new TestException(
                "Did not receive expected event from mailbox.");
        }        
            
        // Call with zero timeout.
        before = new Date();
	logger.log(Level.INFO, "Calling next() on empty set @ {0}", before);        
        rei_event = rei.next(0);
        after = new Date();
        delta = after.getTime() - before.getTime();        
	logger.log(Level.INFO, "Returned from next() @ {0}, delta = {1}", 
            new Object[] {after, new Long(delta)});   
        if (rei_event != null) {
            throw new TestException(
                "Received unexpected event from empty mailbox: " + rei_event);
        }
            
        try {
            rei.next(-1);
            throw new TestException("Successfully called next() with -1");
        } catch (IllegalArgumentException iae) {
            logger.log(Level.FINEST, 
                "Caught IllegalArgumentException -- expected", iae);
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
