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

import com.sun.jini.constants.TimeConstants;
import com.sun.jini.qa.harness.TestException;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager.Created;


public class MahaloIFTest extends TxnMgrTestBase 
    implements TimeConstants 
{

    private final long DURATION = 1*MINUTES;
    private final int NUM_REGS = 5;

    public void run() throws Exception {
        int i;

        TransactionManager mb = getTransactionManager();
	logger.log(Level.INFO, "Got TransactionManager reference: " + mb);

	logger.log(Level.INFO, "Generating " + NUM_REGS + " created objects");
        long[] durations = new long[NUM_REGS];
	for (i=0; i < NUM_REGS; i++) {
	    durations[i] = DURATION;
	}
	Created[] mbrs = getCreateds(mb, durations);

	logger.log(Level.INFO, "Checking leases");
        Lease[] leases = new Lease[NUM_REGS];
	for (i=0; i < NUM_REGS; i++) {
            leases[i] = getTransactionManagerLease(mbrs[i]);
	    checkLease(leases[i], DURATION); 
	}

        Object admin = getTransactionManagerAdmin(mb);

	logger.log(Level.INFO, "Done.");
    }
}
