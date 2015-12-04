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

// Test harness specific classes

import org.apache.river.qa.harness.TestException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import org.apache.river.constants.TimeConstants;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

public class RegistrationIFTest extends MailboxTestBase 
    implements TimeConstants 
{
    private final long DURATION1 = 3*HOURS;
    private final long DURATION2 = 2*DURATION1;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	EventMailbox mb = getMailbox();

	MailboxRegistration mr1 = getRegistration(mb, DURATION1);
	checkLease(getMailboxLease(mr1), DURATION1); 

	RemoteEventListener rel = getMailboxListener(mr1);

	try {
	    mr1.enableDelivery(rel);
	    throw new TestException("Successfully submitted service's "
				  + "listener back to itself");
	} catch (IllegalArgumentException iae) {
	}

	rel =  TestUtils.createListener(getManager());
	mr1.enableDelivery(rel);
	mr1.disableDelivery();
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
