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
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.Test;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxRegistration;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.UnknownLeaseException;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class LeaseMapTest extends LeaseTestBase implements TimeConstants {

    private final long DURATION = 3*HOURS;

    private int numRegs = 5;

    /**
     * Parse arguments specifically for this test.
     */
    protected void parse() throws Exception {
	 super.parse();
	 numRegs = 
	    getConfig().getIntConfigVal("org.apache.river.test.impl.mercury.num_regs",
					5);
    }

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

	if (numRegs < 2) {
	    throw new TestException("Must have at least two registrations "
				  + "to run this test");
	}

	String mbType = 
            getConfig().getStringConfigVal(
		MAILBOX_PROPERTY_NAME,
		MAILBOX_IF_NAME);

	logger.log(Level.INFO, "Getting ref to " + mbType); 

        // Setup lease request durations
	long[] durations = new long[numRegs];
	int i = 0;
	for (i = 0; i < numRegs; i++) {
	    durations[i] = DURATION;
	}
	
	Lease[] leases = new Lease[numRegs];
        if (mbType.equals(MAILBOX_IF_NAME)) {
	    EventMailbox mb = getMailbox(); 
	    logger.log(Level.INFO, "Generating " + numRegs + " registrations");
	    MailboxRegistration[] mbrs = getRegistrations(mb, durations);
	    logger.log(Level.INFO, "Getting and checking registration leases");
	    for (i = 0; i < numRegs; i++) {
	        leases[i] = getMailboxLease(mbrs[i]); 
	        checkLease(leases[i], DURATION); 
	    }
	} else if (mbType.equals(PULL_MAILBOX_IF_NAME)) {
	    PullEventMailbox mb = getPullMailbox(); 
	    logger.log(Level.INFO, "Generating " + numRegs + " registrations");
	    MailboxPullRegistration[] mbrs = getPullRegistrations(mb, durations);
	    logger.log(Level.INFO, "Getting and checking registration leases");
	    for (i = 0; i < numRegs; i++) {
	        leases[i] = getPullMailboxLease(mbrs[i]); 
	        checkLease(leases[i], DURATION); 
	    }
	} else {
	    throw new TestException(
		"Unsupported mailbox type requested" + mbType);
	}
	

	logger.log(Level.INFO, "Generating LeaseMap to manage all leases");
	LeaseMap lm = leases[0].createLeaseMap(DURATION);
	for (i = 1; i < numRegs; i++) {
	    if (lm.canContainKey(leases[i])) {
		lm.put(leases[i], new Long(DURATION));
	    } else {
	        throw new TestException("Could not add valid lease to Map");
	    }
	}

	logger.log(Level.INFO, "Renewing all leases");
	lm.renewAll();

	logger.log(Level.INFO, "Cancelling even numbered leases");
	int cancelCount = 0;
	for (i = 0; i < numRegs; i++) {
	    if ((i % 2) == 0) {
		leases[i].cancel();
		cancelCount++;
	    }
	}

	logger.log(Level.INFO, "Renewing all leases");
	try {
	    lm.renewAll();
	    throw new TestException("Successfully renewed non-existent lease");
	} catch (LeaseMapException lme) { 
	    if (lme.exceptionMap.size() != cancelCount) {
		dumpLeaseMapException(lme);
		throw new TestException("Received unexpected number "
				      + "of exceptions upon renewal");
	    } else  {
		logger.log(Level.INFO, 
			   "Received expected number of renewal exceptions");
	    }
	}

	// Note that the LeaseMap will REMOVE the Leases that failed to 
	// to renew from the Map. We are working with a subset now. 

	logger.log(Level.INFO, "Cancelling another lease");
	leases[1].cancel();
	cancelCount = 1;

	logger.log(Level.INFO, "Cancelling remaining leases");
	try {
	    logger.log(Level.INFO, 
		       "Note: Expect cancelAll() to produce an exception trace"
		     + "for the generated LeaseMapException");
	    lm.cancelAll();
	    throw new TestException("Successfully cancelled "
				  + "non-existent lease");
	} catch (LeaseMapException lme) { 
	    if (lme.exceptionMap.size() != cancelCount) {
		dumpLeaseMapException(lme);
		throw new TestException("Received unexpected number of "
				      + "exceptions upon cancellation");
	    } else  {
		logger.log(Level.INFO, 
			   "Received expected number of exceptions");
	    }
	}
    }

    /**
     * Invoke parent's construct and invoke parser for this test
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	this.parse();
        return this;
    }
}
