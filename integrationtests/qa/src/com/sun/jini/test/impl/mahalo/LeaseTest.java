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
package com.sun.jini.test.impl.mahalo;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

import java.rmi.RemoteException;

import com.sun.jini.constants.TimeConstants;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager.Created;



public class LeaseTest extends TxnMgrTestBase implements TimeConstants {

    private final long DURATION = 3*HOURS;

    private int numRegs = 5;

    /**
     * Parse arguments specifically for this test.
     */
    protected void parse() throws Exception {
	 super.parse();
	 numRegs = 
	     getConfig().getIntConfigVal("com.sun.jini.test.impl.mahalo.num_regs",
					 5);
    }

    public void run() throws Exception {

	TransactionManager mb = getTransactionManager(); 

	long[] durations = new long[numRegs];
	int i = 0;
	for (i = 0; i < numRegs; i++) {
	    durations[i] = DURATION;
	}

	logger.log(Level.INFO, "Generating " + numRegs + " transactions");
	Created[] mbrs = getCreateds(mb, durations);

	logger.log(Level.INFO, "Checking leases");
	Lease[] leases = new Lease[numRegs];
	for (i = 0; i < numRegs; i++) {
	    leases[i] = getTransactionManagerLease(mbrs[i]); 
	    checkLease(leases[i], DURATION); 
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
		    throw new TestException( "Successfully renewed a "
					   + "cancelled lease");
		}
	    } catch (UnknownLeaseException ule) {
		if (i % 2 != 0) // odd index? 
		    throw new TestException( "Had trouble renewing a "
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
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	this.parse();
    }
}
