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
package com.sun.jini.test.impl.outrigger.leasing;

import java.util.logging.Level;

// java classes
import java.rmi.*;

// jini classes
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.server.TransactionManager;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

// Shared classes
import com.sun.jini.test.share.TestBase;


/**
 * Test that creates a transaction and checks the lease it gets back
 */
public class TxnMgrLeaseGrantTest extends LeaseGrantTestBase {

    /**
     * Run the test.  See LeaseGrantTestBase for details on the command
     * line arguments are parsed.
     * @see LeaseGrantTestBase#parse
     */
    public void run() throws Exception {
        specifyServices(new Class[] {TransactionManager.class});
        prep(0);
        final Transaction.Created txnHolder;

	txnHolder = 
	    TransactionFactory.create(((TransactionManager) services[0]), 
				      durationRequest);
        resourceRequested();
        Lease lease = null;
	lease = (Lease) getConfig().prepare("test.mahaloLeasePreparer", 
					    txnHolder.lease);
        addLease(lease, false);
        logRequest("transaction", lease);

        if (!isAcceptable(lease)) {
            throw new TestException(
                    "Got back lease with a improper duration");
        }
    }
}
