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

import com.sun.jini.constants.TimeConstants;

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lease.LeaseMapException;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager.Created;


import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class LeaseMapTest extends TxnMgrTestBase implements TimeConstants 
{

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

	if (numRegs < 2) {
	    throw new TestException( "Must have at least two registrations "
				   + "to run this test");
	}

        TransactionManager mb = getTransactionManager();

	long[] durations = new long[numRegs];
	int i = 0;
	for (i = 0; i < numRegs; i++) {
	    durations[i] = DURATION;
	}

	logger.log(Level.INFO, "Generating " + numRegs + " registrations");
	Created[] mbrs = getCreateds(mb, durations);

	logger.log(Level.INFO, "Checking leases");
	Lease[] leases = new Lease[numRegs];
	for (i = 0; i < numRegs; i++) {
	    leases[i] = getTransactionManagerLease(mbrs[i]); 
	    checkLease(leases[i], DURATION); 
	}

	logger.log(Level.INFO, "Generating LeaseMap to manage all leases");
	LeaseMap lm = leases[0].createLeaseMap(DURATION);
	for (i = 1; i < numRegs; i++) {
	    if (lm.canContainKey(leases[i])) {
		lm.put(leases[i], new Long(DURATION));
	    } else {
		throw new TestException( "Could not add valid lease to LeaseMap");
	    }
	}

	logger.log(Level.INFO, "Renewing all leases");
	try {
	    lm.renewAll();
	} catch (Exception e) {
	    throw new TestException( "Caught unexpected exception: " + e);
	}

	logger.log(Level.INFO, "Cancelling even numbered leases");
	int cancelCount = 0;
	for (i = 0; i < numRegs; i++) {
	    try {
		if ((i % 2) == 0) {
		    leases[i].cancel();
		    cancelCount++;
		}
	    } catch (Exception e) { 
		throw new TestException( "Caught unexpected excetion: " + e);
	    }
	}

	logger.log(Level.INFO, "Renewing all leases");
	try {
	    lm.renewAll();
	    throw new TestException( "Successfully renewed non-existent lease");
	} catch (LeaseMapException lme) { 
	    if (lme.exceptionMap.size() != cancelCount) {
		dumpLeaseMapException(lme);
		throw new TestException( "Received unexpected number of "
				       + "exceptions upon renewal");
	    } else  {
		logger.log(Level.INFO, "Received expected number of "
			             + "renewal exceptions");
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
		       "Note: Expect cancelAll() to produce an exception trace "
		     + "for the generated LeaseMapException");
	    lm.cancelAll();
	    throw new TestException("Successfully cancelled non-existent" 
				  + "lease");
	} catch (LeaseMapException lme) { 
	    if (lme.exceptionMap.size() != cancelCount) {
		dumpLeaseMapException(lme);
		throw new TestException( "Received unexpected number "
				       + "of exceptions upon cancellation");
	    } else  {
		logger.log(Level.INFO, 
			 "Received expected number of cancellation exceptions");
	    }
	}
    }

    /**
     * Invoke parent's setup and invoke parser for this test
     * @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public void setup(QAConfig sysConfig) throws Exception {
	super.setup(sysConfig);
	this.parse();
    }

    private void dumpLeaseMapException(LeaseMapException lme) {
            Map m = lme.exceptionMap;
                Collection keys = m.keySet();
                Iterator iter = keys.iterator();
                while (iter.hasNext()) {
                    Object lease = iter.next();
                    logger.log(Level.INFO, "Lease: " + lease);
                    logger.log(Level.INFO, "Reason: " + m.get(lease));
                }
    }

}
