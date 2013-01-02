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

import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;

import com.sun.jini.test.impl.mercury.TestUtils;
import com.sun.jini.test.impl.mercury.TestListener;
import com.sun.jini.test.impl.mercury.TestGenerator;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

public class EMSLST2 extends StressTest {

    public void run() throws Exception {
	logger.log(Level.INFO, "Getting Mailbox reference");
	EventMailbox mb = getConfiguredMailbox();        
	int i = 0;

	// Register and check lease
	logger.log(Level.INFO, "Creating " + NUM_REGS + " registrations");
	MailboxRegistration[] mr =  new MailboxRegistration[NUM_REGS];
	for (i = 0; i < NUM_REGS; i++) {
	    mr[i] = getRegistration(mb, DURATION);
	    checkLease(getMailboxLease(mr[i]), DURATION); 
	    addLease(getMailboxLease(mr[i]), false);
	}

	// Get the mailbox service provided listener
	logger.log(Level.INFO, "Getting mailbox listeners");
	RemoteEventListener[] mbRel = new RemoteEventListener[NUM_REGS];
	for (i = 0; i < NUM_REGS; i++) {
	    mbRel[i] = getMailboxListener(mr[i]);
	}

	// Creating event generators that use the mailbox listeners
	TestGenerator[] myGen = TestUtils.createGenerators(NUM_REGS, getManager());
	EventRegistration[] evtReg = new EventRegistration[NUM_REGS]; 
	for (i = 0; i < NUM_REGS; i++) {
	    evtReg[i] = myGen[i].register(
			    EVENT_ID_BASE + (i * 1000),	// Event ID to use
			    null,				// handback
			    mbRel[i],			// Notification target
			    DURATION);			// Lease duration
	    checkLease(evtReg[i].getLease(), DURATION); 
	    addLease(evtReg[i].getLease(), false);
	}

	// Create listener objects
	TestListener[] listeners = TestUtils.createListeners(NUM_REGS, getManager());

	boolean sync = false;
	boolean shutdown = true;
	printTime();
	generateAndValidate(mr, myGen, listeners, sync, shutdown);
	printTime();
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
