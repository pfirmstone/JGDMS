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

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import org.apache.river.constants.TimeConstants;

import org.apache.river.test.impl.mercury.EMSTestBase;
import org.apache.river.test.impl.mercury.TestListener;
import org.apache.river.test.impl.mercury.TestGenerator;
import java.util.concurrent.atomic.AtomicInteger;


public abstract class StressTest extends EMSTestBase implements TimeConstants {
    private static final AtomicInteger genCount = new AtomicInteger();
    private static final AtomicInteger evtCount = new AtomicInteger();

    private class EventGeneratorThread extends Thread {

        private final TestGenerator tg;
        private final long eventID;
        private final AtomicInteger numEvents;

	private volatile RemoteEvent[] events = null;


        public EventGeneratorThread(TestGenerator tg, long eventID, 
				    int numEvents) 
	{
	    super("EventGeneratorThread-" + genCount.incrementAndGet());
            this.tg = tg;
            this.eventID = eventID;
            this.numEvents = new AtomicInteger(numEvents);
	    events = new RemoteEvent[numEvents];
        }

        public void run() {
	    int counter = 0;
            RemoteEvent[] events = this.events; // copy reference.
            try {
                while (numEvents.getAndDecrement() > 0) {
                    events[counter++] = tg.generateEvent(eventID, 2);
                    if (counter % 99 == 0) {
	                logger.log(Level.FINE, 
                            getName() + " has sent " + counter + " events.");
                    }
                    //yield();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) { /*ignore*/}
	        }
	        log(getName() + " finished after sending " 
		    + counter + " events.");
            } catch (Exception e) {
                logger.log(Level.FINE, getName()
		    + " caught unexpected exception after " 
                    + counter + " events and terminated:");
                e.printStackTrace();
            }
            this.events = events; // publish changes.
        }

	public RemoteEvent[] getEvents() {
	    return events;
	}
    }

    private class EventListenerThread extends Thread {

        private final TestListener tl;
        private final int numEvents;
        private final long wait;

        public EventListenerThread(TestListener tl, int numEvents, long wait) {
	    super("EventListenerThread-" + evtCount.incrementAndGet());
            this.tl = tl;
            this.numEvents = numEvents;
            this.wait = wait;
        }

        public void run() {
            try {
                waitForEvents(tl, numEvents, wait);
                assertCount(tl, numEvents);
	        log(getName() + " finished after receiving " 
		    + numEvents + " events");
            } catch (Exception e) {
                logger.log(Level.FINE, getName() 
		    + " caught unexpected exception and terminated:");
                e.printStackTrace();
            }
        }
    }

    // Pass through so that inner classes can access log
    // field from TestBase class.
    protected void log(String s) {
	logger.log(Level.FINE, s);
    }

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    protected final int NUM_REGS = 10;
    protected final int NUM_EVENTS_PER_REG = 1000;
    protected final long EVENT_ID_BASE = 0;
    protected final long MAX_WAIT = NUM_REGS * NUM_EVENTS_PER_REG * SECONDS;
    protected final long DURATION = MAX_WAIT * 3;
    protected final long SHUTDOWN_DELAY = MAX_WAIT / 10;

    protected void generateAndValidate(MailboxRegistration[] mr, 
            TestGenerator[] myGen, TestListener[] listeners,
            boolean synchronous, boolean shutdown) throws Exception 
    {
	int i = 0;
        int eventCount = 0;
        try {
	    logger.log(Level.FINE, "Generating events");
	    EventGeneratorThread[] genThreads = 
		new EventGeneratorThread[NUM_REGS];
            for (i = 0; i < NUM_REGS; i++) {
                genThreads[i] = 
                        new EventGeneratorThread(myGen[i], 
                                                 EVENT_ID_BASE + (i*1000), 
                                                 NUM_EVENTS_PER_REG); 
		genThreads[i].start();
	    }

            if (synchronous) {
	        logger.log(Level.FINE, "Waiting for completion of event generation");
                for (i = 0; i < NUM_REGS; i++) {
                    genThreads[i].join();
		}

		if (shutdown) {
	            logger.log(Level.FINE, "Shutting down after generation completion");
		    shutdown(0);
		}
	    }

            eventCount += NUM_EVENTS_PER_REG;

	    logger.log(Level.FINE, "Enabling listeners");
            for (i = 0; i < NUM_REGS; i++) {
                mr[i].enableDelivery(listeners[i]);
	    }

	    logger.log(Level.FINE, "Generating listener threads");
	    EventListenerThread[] evtThreads = 
		new EventListenerThread[NUM_REGS];
            for (i = 0; i < NUM_REGS; i++) {
                evtThreads[i] = 
                        new EventListenerThread(listeners[i],
                                                eventCount,
                                                MAX_WAIT); 
                evtThreads[i].start();
	    }

            if (!synchronous && shutdown) {
	        logger.log(Level.FINE, "Waiting " + SHUTDOWN_DELAY 
		    + " (ms) before shutdown");
	        try {
	            Thread.sleep(SHUTDOWN_DELAY);
		} catch (InterruptedException ie) {
	            logger.log(Level.FINE, "Woke up before waiting full delay");
		}
	        logger.log(Level.FINE, "Shutting down ...");
		shutdown(0);
	    }

	    logger.log(Level.FINE, "Waiting for event delivery of " 
	        + eventCount + " events per listener");
            for (i = 0; i < NUM_REGS; i++) {
                evtThreads[i].join();
	    }

	    logger.log(Level.FINE, "Verifying event delivery");
            for (i = 0; i < NUM_REGS; i++) {
		// getEvents() may contains nulls if listener timed out
                if(!listeners[i].verifyEvents(genThreads[i].getEvents()))
		    throw new TestException ("Received unexpected event "
			+ "set from set " + i);
	    }
	} catch (Exception e) {
	    throw new TestException ("Caught an unexpected exception", e);
	}
    
    }

    protected void printTime() {
	logger.log(Level.FINE, "+++++++++++++++++++++++++++++++++++++++++");
	logger.log(Level.FINE, "" + new java.util.Date(System.currentTimeMillis()));
	logger.log(Level.FINE, "+++++++++++++++++++++++++++++++++++++++++");
    }

}
