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

import com.sun.jini.constants.TimeConstants;
import com.sun.jini.admin.DestroyAdmin;

import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;
import com.sun.jini.test.impl.mercury.TestGenerator;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

/* 
 * Test blocks on getNext() with a timeout of 4 minute (on an empty 
 * registration). The test then tries to call the service's destroy() method
 * before the timeout expires. The implementation attempts to notify blocking
 * calls at the start of the destroy process, so we should return early (i.e.
 * after the destroy call but before the requested timeout period).
 */
public class PullTimeoutTest6A 
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

    /* Note: Service has a 2 minute unexport timeout */
    private final long MAX_WAIT_GET_EVENT = 60 * 4 * 1000; // 4 minutes
    private final long MAX_WAIT_SEND_DESTROY = 10 * 1000;   // 10 seconds
    
    class MyDestroyerRunnable implements Runnable {
        final DestroyAdmin admin;
        final Logger logger;
        final long delay;
        MyDestroyerRunnable(DestroyAdmin admin, Logger logger, long delay) 
        {
            this.admin = admin;
            this.logger = logger;
            this.delay = delay;
        }
        public void run() {
            try {
                logger.log(Level.FINEST, 
                    "MyDestroyerRunnable sleeping @ {0} for {1} ms", 
                    new Object[] { new Date(), new Long(delay)});
                Thread.sleep(delay);
                logger.log(Level.FINEST, 
                    "MyDestroyerRunnable awoken @ {0}", new Date());
            } catch (InterruptedException ie) {
                // ignore
                logger.log(Level.FINEST, 
                    "Sleep interrupted", ie);
            }

            try {
                admin.destroy();                    
                logger.log(Level.FINEST, "Called destroy @ {0}", new Date());
            } catch (RemoteException re) {
               logger.log(Level.FINEST, 
                   "Ignoring RemoteException from calling destroy",
                       re);
            }
        }
    }

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	PullEventMailbox mb = getPullMailbox();
	Object admin = getMailboxAdmin(mb);
        DestroyAdmin dAdmin = (DestroyAdmin)admin;
        int i = 0;

	// Register and check lease
	MailboxPullRegistration mr = getPullRegistration(mb, DURATION1);
	Lease mrl = getPullMailboxLease(mr);
	checkLease(mrl, DURATION1); 

        // Start event generator thread with a delay of MAX_WAIT_SEND_DESTROY
        Thread t = 
            new Thread(
                new MyDestroyerRunnable(
                    dAdmin, logger, MAX_WAIT_SEND_DESTROY ));
        t.start();
        
        // Get events and verify
	logger.log(Level.INFO, "Getting events from empty mailbox.");
        RemoteEventIterator rei = mr.getRemoteEvents();
        RemoteEvent rei_event = null;
        Date before = new Date();
	logger.log(Level.INFO, "Calling next() on empty set @ {0}", before); 
        try {
            rei_event = rei.next(MAX_WAIT_GET_EVENT);
            throw new TestException("Did not receive expected exception");
        } catch (NoSuchObjectException nsoe) {
            logger.log(Level.INFO, "Caught expected exception", nsoe);
        }
        Date after = new Date();

        //Verify that we returned before the timeout
        long delta = after.getTime() - before.getTime();        
	logger.log(Level.INFO, "Returned from next() @ {0}, delta = {1}", 
            new Object[] {after, new Long(delta)});   
        if (delta >= MAX_WAIT_GET_EVENT) {
             throw new TestException("Returned from next() after expected: " 
                     + delta);
        } 
        if (rei_event != null) {
            throw new TestException(
                "Received unexpected event from mailbox: " + rei_event);
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
