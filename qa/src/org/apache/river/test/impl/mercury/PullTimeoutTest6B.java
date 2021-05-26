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
import org.apache.river.admin.DestroyAdmin;

import org.apache.river.test.impl.mercury.EMSTestBase;
import org.apache.river.test.impl.mercury.TestUtils;
import org.apache.river.test.impl.mercury.TestListener;
import org.apache.river.test.impl.mercury.TestGenerator;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

/* 
 * Test blocks multiple threads on getNext() with a timeout of 1 minute 
 * (on an empty registration). 
 * The test then tries to call the service's destroy() method
 * before the minute is up. The implementation attempts to notify blocking
 * calls at the start of the destroy process, so we should return early (i.e.
 * after the destroy call but before the requested timeout period).
 */
public class PullTimeoutTest6B 
    extends EMSTestBase implements TimeConstants 
{

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 3*HOURS;

    private final int NUM_WORKERS = 5;

    private final long EVENT_ID = 1234;
    private final long EVENT_ID2 = 5678;

    /* Note: Service has a 2 minute unexport timeout */
    private final long MAX_WAIT_GET_EVENT = 60 * 1000;
    private final long MAX_WAIT_SEND_DESTROY = MAX_WAIT_GET_EVENT / 5;
    
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
                Thread.currentThread().interrupt();
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
    class MyWorker extends Thread {
        final MailboxPullRegistration reg;
        final Logger logger;
        final long delay;
        boolean failed = false;
        MyWorker(MailboxPullRegistration reg, Logger logger, long delay) 
        {
            this.reg = reg;
            this.logger = logger;
            this.delay = delay;
        }
        public void run() {
            try {
                 // Get events and verify
                logger.log(Level.INFO, "Getting events from empty mailbox.");
                RemoteEventIterator rei = reg.getRemoteEvents();
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
                logger.log(Level.INFO, "Worker done."); 
            } catch (Exception e) {
                logger.log(Level.INFO, "Got unexpected exception.", e);
                failed = true;
            }

        }
        public boolean failed() { return failed; }
    }

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	PullEventMailbox mb = getPullMailbox();
	Object admin = getMailboxAdmin(mb);
        DestroyAdmin dAdmin = (DestroyAdmin)admin;
        int i = 0;

	// Register and check lease
        long[] durations = 
            new long[] {DURATION1, DURATION1, DURATION1, DURATION1, DURATION1};
	MailboxPullRegistration[] mrs = getPullRegistrations(mb, durations);
        Lease mrl = null;
        for (i=0; i < durations.length; i++) {
            mrl = getPullMailboxLease(mrs[i]);
	    checkLease(mrl, DURATION1);
        }

        // Start destroyer thread with a delay of MAX_WAIT_SEND_EVENT
        Thread t = 
            new Thread(
                new MyDestroyerRunnable(
                    dAdmin, logger, MAX_WAIT_SEND_DESTROY ));
        t.start();
        
        // Start worker threads
        MyWorker[] workers = new MyWorker[mrs.length];
        for (i=0; i < mrs.length; i++) {
            workers[i] = 
                new MyWorker(mrs[i], logger, MAX_WAIT_GET_EVENT);
            workers[i].start();
        }
        
        for (i=0; i < mrs.length; i++) {
            workers[i].join();
            if (workers[i].failed()) {
                throw new TestException(
                    "Worker failed");
            }
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
