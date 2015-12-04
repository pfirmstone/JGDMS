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

import org.apache.river.constants.TimeConstants;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import org.apache.river.qa.harness.TestException;

public class RegEnableShutdownTest extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION = 3*HOURS;

    private final int NUM_EVENTS = 5;

    private final long EVENT_ID = 1234;

    private final long MAX_WAIT = 60 * 1000;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	String mbType =
            getConfig().getStringConfigVal(
		MAILBOX_PROPERTY_NAME,
		MAILBOX_IF_NAME);

	logger.log(Level.INFO, "Getting ref to " + mbType);
        MailboxRegistration mr = null;
	Lease mrl = null;
	if (mbType.equals(MAILBOX_IF_NAME)) {
	    EventMailbox mb = getMailbox();
	    mr = getRegistration(mb, DURATION);
	    mrl = getMailboxLease(mr);
	} else if (mbType.equals(PULL_MAILBOX_IF_NAME)) {
            PullEventMailbox pmb = getPullMailbox();
	    MailboxPullRegistration mpr = getPullRegistration(pmb, DURATION);
	    mrl = getPullMailboxLease(mpr);
            mr = mpr;
 	} else {
            throw new TestException(
		"Unsupported mailbox type requested" + mbType);
	}

	// Register and check lease
	checkLease(mrl, DURATION); 

	// Get the mailbox service provided listener
	RemoteEventListener mbRel = getMailboxListener(mr);

	// Create an event generator and pass it the
	// mailbox's remote event listener.
	TestGenerator myGen = TestUtils.createGenerator(getManager());
	EventRegistration evtReg = 
	    myGen.register(EVENT_ID,	// Event ID to use
			   null,		// handback
			   mbRel,		// Notification target
			   DURATION);	// Lease duration
	Lease tgl = evtReg.getLease();
	checkLease(tgl, DURATION); 

	// Create our target listener
	TestListener listener = TestUtils.createListener(getManager());
	int evtCnt = 0;

	logger.log(Level.INFO, "Generating an event from " + myGen);
	myGen.generateEvent(evtReg.getID(), 3);

	logger.log(Level.INFO, "Enabling delivery to our REL " + listener);
	mr.enableDelivery(listener);

	logger.log(Level.INFO, "Wating for event delivery");
        evtCnt++;
        waitForEvents(listener, evtCnt, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(listener, evtCnt);

	shutdown(0);

	logger.log(Level.INFO, "Generating an event from " + myGen);
	myGen.generateEvent(evtReg.getID(), 3);

	logger.log(Level.INFO, "Wating for event delivery");
        evtCnt++;
        waitForEvents(listener, evtCnt, MAX_WAIT);
	logger.log(Level.INFO, "Verifying event delivery");
	assertCount(listener, evtCnt);

	logger.log(Level.INFO, "Cancelling registration lease");
	mrl.cancel();
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
