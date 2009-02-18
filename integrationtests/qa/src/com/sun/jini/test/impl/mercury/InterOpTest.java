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

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.lookup.entry.Name;
import net.jini.space.JavaSpace;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.constants.TimeConstants;

public class InterOpTest extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //

    private final int NUM_EVENTS = 1;
    private final long MAX_WAIT = NUM_EVENTS * SECONDS * 80;
    private final long DURATION = MAX_WAIT * 10;

    public void run() throws Exception {
	int i;

	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	specifyServices(new Class[]{EventMailbox.class, JavaSpace.class});
	logger.log(Level.INFO, "Getting EventMailbox reference");
	EventMailbox mb = (EventMailbox)services[0];
	logger.log(Level.INFO, "Getting JavaSpace reference");
	JavaSpace space = (JavaSpace)services[1];

	// Register and check lease
	logger.log(Level.INFO, "Creating MailboxRegistration");
	MailboxRegistration mr =  getRegistration(mb, DURATION);
	checkLease(getMailboxLease(mr), DURATION);
	addLease(getMailboxLease(mr), false);


	logger.log(Level.INFO, "Getting mailbox listener");
	RemoteEventListener mbRel = getMailboxListener(mr);

	logger.log(Level.INFO, "Getting our own listener");
	TestListener listener = TestUtils.createListener(manager);

	//Setup space to generate events
	logger.log(Level.INFO, "Setting up space notifications");
	EventRegistration er = space.notify(null, null, mbRel, DURATION, null); 
	checkLease(er.getLease(), DURATION);
	addLease(er.getLease(), false);


	logger.log(Level.INFO, "Generating some events");
	for (i = 0; i < NUM_EVENTS; i++) {
	    addLease(space.write(new Name("Duh"), null, DURATION), false);
	}

	//Note: JavaSpaces is allowed to compact event notifications.
	// That is, if more than one event is scheduled to be delivered
	// for the same notification target, then the one with the latest
	// sequence number can be sent (alone). Therefore, for 
	// NUM_EVENTS > 1, you'll need to add some sort of delay factor
	// in order to prevent multiple notifications from beng queue'd
	// up together on the space side of the house.
	logger.log(Level.INFO, "Checking event count befor enabling");
	int eventCount = 0;
	assertCount(listener, eventCount); 

	logger.log(Level.INFO, "Enabling delivery");
	mr.enableDelivery(listener);
	eventCount += NUM_EVENTS;
	logger.log(Level.INFO, "Waiting for event delivery");
	waitForEvents(listener, eventCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(listener, eventCount);

	logger.log(Level.INFO, "Generating some more events");
	for (i = 0; i < NUM_EVENTS; i++) {
	    addLease(space.write(new Name("Yah"), null, DURATION), false);
	}

	eventCount += NUM_EVENTS;
	logger.log(Level.INFO, "Waiting for event delivery");
	waitForEvents(listener, eventCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(listener, eventCount);

	shutdown(0); // Mailbox is at index 0;

	logger.log(Level.INFO, "Generating some more events");
	for (i = 0; i < NUM_EVENTS; i++) {
	    addLease(space.write(new Name("Yah"), null, DURATION), false);
	}

	eventCount += NUM_EVENTS;
	logger.log(Level.INFO, "Waiting for event delivery");
	waitForEvents(listener, eventCount, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(listener, eventCount);
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
