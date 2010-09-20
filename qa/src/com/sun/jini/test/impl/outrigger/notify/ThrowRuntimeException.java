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
package com.sun.jini.test.impl.outrigger.notify;

import java.util.logging.Level;

// Test harness specific classes
import java.io.PrintWriter;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// java.rmi
import java.rmi.*;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;

// com.sun.jini
import com.sun.jini.test.share.TestBase;
import com.sun.jini.test.share.UninterestingEntry;

import java.io.Serializable;
import java.io.ObjectStreamException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import com.sun.jini.proxy.BasicProxyTrustVerifier;

/**
 * Check to see how space deals with RuntimeException being thrown by
 * event handlers.
 *
 * Register a handler that will throw a runtime exception
 * Write a matching entry
 * Once the handler runs check to make sure its lease was canceled
 * Register a 2nd handler, and write a 2nd entry.  Make sure the
 * first handler is not called and the second one is.
 */
public class ThrowRuntimeException extends TestBase {

    /**
     * How long to wait before giving up on an event notification to
     * come through
     */
    private long wait = 10000;


    /**
     * Listener that rembers the sequence number of the last event and
     * can be told to throw a runtime exception.  Call notifyAll() on itself
     * when an event is received.
     */
    private static class Listener
	implements RemoteEventListener, ServerProxyTrust, Serializable
    {
        private long lastSeqNum = -1;
        final private boolean throwRuntime;
	private Object proxy;

        /** Simple constructor */
        Listener(Configuration c, 
		 boolean throwRuntime) throws RemoteException 
	{
	    try {
		Exporter exporter = QAConfig.getDefaultExporter();
		if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
		    exporter =
		    (Exporter) c.getEntry("test", "outriggerListenerExporter", Exporter.class);
		}
		proxy = exporter.export(this);
	    } catch (ConfigurationException e) {
		throw new RemoteException("Bad configuration", e);
	    }
            this.throwRuntime = throwRuntime;
        }

        public Object writeReplace() throws ObjectStreamException {
	    return proxy;
	}

	public TrustVerifier getProxyVerifier() {
	    return new BasicProxyTrustVerifier(proxy);
	}

        /**
         * Return the sequence number of the last event received, if
         * no event has been received return -1
         */
        long lastEvent() {
            return lastSeqNum;
        }

        public synchronized void notify(RemoteEvent theEvent) {
            logger.log(Level.INFO, 
		       "Received event " + theEvent.getSequenceNumber());
            lastSeqNum = theEvent.getSequenceNumber();
            this.notifyAll();

            if (throwRuntime) {
                logger.log(Level.INFO, "Throwing RuntimeException");
                throw new RuntimeException("HA HA");
            }
        }
    }

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.parse();
    }

    /**
     * Parse the command line args
     * <DL>
     * <DT>-notify_wait<DD> Set the amount of time (in milliseconds)
     * the test will wait for after the writes are done before
     * checking to see if the test has passed
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        wait = getConfig().getLongConfigVal("com.sun.jini.test.impl.outrigger."
                + "notify.notify_wait", 10000);
    }

    public void run() throws Exception {
        specifyServices(new Class[] {
            JavaSpace.class});
	Configuration c = getConfig().getConfiguration();
        final Listener listener1 = new Listener(c, true);
        final Listener listener2 = new Listener(c, false);
        final JavaSpace space = (JavaSpace) services[0];
        final Entry aEntry = new UninterestingEntry();

        // Register ill-behaved handler  and write matching entry
        EventRegistration reg = space.notify(aEntry, null, listener1,
                                             Lease.ANY, null);
	reg = (EventRegistration)
              getConfig().prepare("test.outriggerEventRegistrationPreparer", 
                                  reg);
        logger.log(Level.INFO, "Registered first event handler");
        Lease lease1 = reg.getLease();
        lease1 = (Lease) 
                 getConfig().prepare("test.outriggerLeasePreparer", lease1);
        /*
         * The sequence number the first event should have, this would
         * be dicey in a real application but the QA tests assume a
         * very controled enviroment
         */
        final long ev1 = reg.getSequenceNumber() + 1;
        addOutriggerLease(lease1, true);
        addOutriggerLease(space.write(aEntry, null, Lease.ANY), false);
        logger.log(Level.INFO, "Wrote first Entry");

        /*
         * Wait for event and check to see if it is the right one and
         * that the lease was canceled
         */
        long listener1Rcvd;
        synchronized (listener1) {

            // Did it already happen?
            listener1Rcvd = listener1.lastEvent();

            if (listener1Rcvd < ev1) {

                // No, wait
                listener1.wait(wait);
                listener1Rcvd = listener1.lastEvent();

                if (listener1Rcvd < 0) {
                    throw new TestException(
                            "First listener never received event");
                } else if (ev1 < listener1Rcvd) {
                    throw new TestException(
                            "First listener received too many events");
                }
                logger.log(Level.INFO, "Received correct event");
            }
        }

        // Give the cancel a chance to happen
        Thread.sleep(10000);

        try {
            lease1.cancel();
            throw new TestException(
                    "Lease on first registion not cancled by"
                    + " Runtime exception");
        } catch (UnknownLeaseException e) {

            // Result we are looking for
        }
        logger.log(Level.INFO, "Lease on first registration is gone");

        // Register second handler and write second entry2
        EventRegistration reg2 = space.notify(aEntry, null, listener2,
                Lease.ANY, null);
	reg2 = (EventRegistration)
               getConfig().prepare("test.outriggerEventRegistrationPreparer", 
                                   reg2);
        logger.log(Level.INFO, "Registered 2nd handler");
        Lease lease2 = reg2.getLease();
        lease2 = (Lease) 
                 getConfig().prepare("test.outriggerLeasePreparer", lease2);
        addOutriggerLease(lease2, false);

        /*
         * The sequence number the first event should have, this would
         * be dicey in a real application but the QA tests assume a
         * very controled enviroment
         */
        final long ev2 = reg2.getSequenceNumber() + 1;
        addOutriggerLease(space.write(aEntry, null, Lease.ANY), false);
        logger.log(Level.INFO, "Wrote 2nd Entry");

        // Wait for event and check to see if it is the right one
        synchronized (listener2) {

            // Did it already happen?
            if (listener2.lastEvent() < ev1) {

                // No wait
                listener2.wait(wait);

                if (listener2.lastEvent() < 0) {
                    throw new TestException(
                            "Second listener never received event");
                } else if (ev2 < listener1.lastEvent()) {
                    throw new TestException(
                            "Second listener received too many events");
                }
            }
        }
        logger.log(Level.INFO, "2nd handler received correct event");

        // Make sure first listener never received second event
        Thread.sleep(wait);

        if (listener1.lastEvent() != listener1Rcvd) {
            throw new TestException(
                    "Listener1 received at least one other event");
        }
        logger.log(Level.INFO, "1st handler received no additional events");
    }
}
