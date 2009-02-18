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

// Test harness specific classes
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import java.rmi.RemoteException;

import com.sun.jini.constants.TimeConstants;

import net.jini.event.EventMailbox;
import net.jini.event.MailboxPullRegistration;
import net.jini.event.MailboxRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;


public class LeaseTest extends MailboxTestBase implements TimeConstants {

    private final long DURATION = 3*HOURS;

    private int numRegs = 5;

    /**
     * Parse arguments specifically for this test.
     */
    protected void parse() throws Exception {
	 super.parse();
	 numRegs = 
	    getConfig().getIntConfigVal("com.sun.jini.test.impl.mercury.num_regs",
					5);
    }

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

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
	
	logger.log(Level.INFO, "Cancelling even indexed leases");
	for (i = 0; i < numRegs; i++) {
	    if (i % 2 == 0) leases[i].cancel();
	}

	logger.log(Level.INFO, "Renewing all leases");
	for (i = 0; i < numRegs; i++) {
	    try {
		leases[i].renew(DURATION);
		if (i % 2 == 0) {
		    throw new TestException("Successfully renewed a "
					  + "cancelled lease");
		}
	    } catch (UnknownLeaseException ule) {
		if (i % 2 != 0) // odd index? 
		    throw new TestException("Had trouble renewing a "
					  + "valid Lease object: " + ule);
		else  {
		    logger.log(Level.INFO, 
			       "Caught expected exception for lease " + i);
		}
	    }
	}
    }

    /**
     * Invoke parent's setup and invoke parser for this test
     * @exception TestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	this.parse();
    }
}
