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

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import com.sun.jini.constants.TimeConstants;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEventListener;

import com.sun.jini.test.impl.mercury.EMSTestBase;
import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;


public class EMSRIFT extends EMSTestBase implements TimeConstants {

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION1 = 3*HOURS;

    public void run() throws Exception {
	EventMailbox mb = getConfiguredMailbox();        

	// Register and check lease
	MailboxRegistration mr = getRegistration(mb, DURATION1);
	Lease mrl = getMailboxLease(mr);
	checkLease(mrl, DURATION1); 

	// Get the mailbox service provided listener
	RemoteEventListener mbRel = getMailboxListener(mr);

	// Re-submit the listener and verify that it is not accepted
	try { 
	    mr.enableDelivery(mbRel);
	    throw new TestException("Resubmission of mailbox "
				    + "RemoteEventListener was accepted");
	} catch (IllegalArgumentException iae) {
	    logger.log(Level.INFO, 
		       "Was not able to re-submit service provided REL - OK");
	}
	// Pass our listener to the mailbox
	TestListener myRel = TestUtils.createListener(manager);
	mr.enableDelivery(myRel);
	assertCount(myRel, 0);
	logger.log(Level.INFO, "Submitted our own REL");

	// Re-submit listener again
	mr.enableDelivery(myRel);
	assertCount(myRel, 0);
	logger.log(Level.INFO, "Submitted our own REL again");

	// Submit the null listener
	mr.enableDelivery(null);
	assertCount(myRel, 0);
	logger.log(Level.INFO, "Submitted a null REL");

	mr.disableDelivery();
	assertCount(myRel, 0);
	logger.log(Level.INFO, "Disabled delivery");

	mr.enableDelivery(myRel);
	assertCount(myRel, 0);
	logger.log(Level.INFO, "Submitted our own REL again");

	mr.disableDelivery();
	assertCount(myRel, 0);
	logger.log(Level.INFO, "Disabled delivery");

	mrl.cancel();
	assertCount(myRel, 0);
	logger.log(Level.INFO, "Cancelled Registration's lease");
    }

    /**
     * Invoke parent's setup and parser
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig config) throws Exception {
	super.setup(config);
	parse();
    }
}
