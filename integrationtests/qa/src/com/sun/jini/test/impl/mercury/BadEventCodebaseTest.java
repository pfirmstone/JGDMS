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
import java.util.Collection;

import java.rmi.RemoteException;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.ServerException;

import net.jini.event.InvalidIteratorException;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.event.RemoteEventIterator;
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import com.sun.jini.constants.TimeConstants;

import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;
import com.sun.jini.test.impl.mercury.TestGenerator;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

public class BadEventCodebaseTest 
    extends EMSTestBase implements TimeConstants 
{

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 1*HOURS;

    private final long EVENT_ID = 1234L;

    private final long MAX_WAIT = 60 * 1000;

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
	TestPullListener goodPullListener = TestUtils.createPullListener(manager);
        
       
        // Switch to pull interface -- should disable event listener
	logger.log(Level.INFO, "Using pull listener -- not expecting any events.");
	int goodPullCount = 0;        
        Collection events = goodPullListener.getRemoteEvents(mr);
        if (events.size() != 0) {
            throw new TestException("Got events from empty iterator " + events);
        }
	logger.log(Level.INFO, "Event iterator was empty -- OK");  
        
        Object src = new Integer(0);
        long id = EVENT_ID;
        long seqNum = 0L;
        MarshalledObject hbk = null;

        RemoteEvent[] badEvents = new RemoteEvent[] {
            new MyLocalRemoteEvent(src, id, seqNum++, hbk),
            new MyLocalRemoteEvent(src, id, seqNum++, hbk),
            new MyLocalRemoteEvent(src, id, seqNum++, hbk),
            new MyLocalRemoteEvent(src, id, seqNum++, hbk),
            new MyLocalRemoteEvent(src, id, seqNum++, hbk),
        };


        goodPullCount = 0;

	logger.log(Level.INFO, "Sending " + badEvents.length + " bad events ...");
	for (i = 0; i < badEvents.length; i++) {
	    mbRel.notify(badEvents[i]);
	}

        // Switch to pull interface -- should disable event listener
	logger.log(Level.INFO, "Using pull listener -- not expecting any events.");
	goodPullCount = 0;        
        events = goodPullListener.getRemoteEvents(mr);
        if (events.size() != 0) {
            throw new TestException("Got bad events from iterator " + events);
        }
	logger.log(Level.INFO, "Event iterator was empty -- OK");

        RemoteEvent[] goodEvents = new RemoteEvent[] {
            new RemoteEvent(src, id, seqNum++, hbk),
            new RemoteEvent(src, id, seqNum++, hbk),
            new RemoteEvent(src, id, seqNum++, hbk),
            new RemoteEvent(src, id, seqNum++, hbk),
            new RemoteEvent(src, id, seqNum++, hbk),
            new RemoteEvent(src, id, seqNum++, hbk),
        };

        goodPullCount = goodEvents.length;

	logger.log(Level.INFO, "Sending " + goodEvents.length + " good events ...");
	for (i = 0; i < goodEvents.length; i++) {
	    mbRel.notify(goodEvents[i]);
	}
        
        events = goodPullListener.getRemoteEvents(mr);
        if (events.size() != goodPullCount) {
            throw new TestException(
                "Got " + events.size() + " + events, but expected " +
                goodPullCount + ":" + events);
        }
	logger.log(Level.INFO, "Event iterator was not empty -- OK");     
        
        RemoteEvent[] mixedEvents = new RemoteEvent[] {
            new MyLocalRemoteEvent(src, id, seqNum++, hbk),
            new RemoteEvent(src, id, seqNum++, hbk),
            new MyLocalRemoteEvent(src, id, seqNum++, hbk),
            new RemoteEvent(src, id, seqNum++, hbk),
            new MyLocalRemoteEvent(src, id, seqNum++, hbk),
            new RemoteEvent(src, id, seqNum++, hbk),
        };
	logger.log(Level.INFO, "Sending " + mixedEvents.length + " mixed events ...");
	for (i = 0; i < mixedEvents.length; i++) {
	    mbRel.notify(mixedEvents[i]);
	}
        goodPullCount = 3;
        events = goodPullListener.getRemoteEvents(mr);
        if (events.size() != goodPullCount) {
            throw new TestException(
                "Got " + events.size() + " events, but expected " +
                goodPullCount + ":" + events);
        }
	logger.log(Level.INFO, "Event iterator was not empty -- OK");     
        // TODO - Check for expected events

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
