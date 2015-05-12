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

import org.apache.river.qa.harness.TestException;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.MailboxRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

public class RegRenewExpireShutdownTest extends EMSTestBase 
                                        implements TimeConstants 
{
    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewExpirealManager to keep our leases current.
    //
    private final long DURATION = 60*SECONDS;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	String mbType =
            getConfig().getStringConfigVal(
		MAILBOX_PROPERTY_NAME,
		MAILBOX_IF_NAME);

	logger.log(Level.INFO, "Getting ref to " + mbType);
	Lease mrl = null;
	if (mbType.equals(MAILBOX_IF_NAME)) {
	    EventMailbox mb = getMailbox();
	    MailboxRegistration mr = getRegistration(mb, DURATION);
	    mrl = getMailboxLease(mr);
	} else if (mbType.equals(PULL_MAILBOX_IF_NAME)) {
            PullEventMailbox mb = getPullMailbox();
	    MailboxPullRegistration mr = getPullRegistration(mb, DURATION);
	    mrl = getPullMailboxLease(mr);
	} else {
            throw new TestException(
		"Unsupported mailbox type requested" + mbType);
	}
	checkLease(mrl, DURATION); 

	shutdown(0);
	
	logger.log(Level.INFO, "Sleeping for " + DURATION + " (ms)");
	Thread.sleep(DURATION + 1000);

	try {
	    logger.log(Level.INFO, "Renewing registration lease");
	    mrl.renew(DURATION);
	    throw new TestException("Successfully renewed an "
				  + "expired registration");
	} catch (UnknownLeaseException ule) {
	    logger.log(Level.INFO, "Could not renew expired lease - OK");
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
