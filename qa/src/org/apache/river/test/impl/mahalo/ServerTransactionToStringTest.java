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
package org.apache.river.test.impl.mahalo;

import java.util.logging.Level;

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager.Created;

public class ServerTransactionToStringTest extends TxnMgrTestBase 
    implements TimeConstants 
{

    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION = 5*MINUTES;

    public void run() throws Exception {
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 

        // Get Mailbox references
	TransactionManager[] mbs = getTransactionManagers(2);
	TransactionManager txnmgr1 = mbs[0];
	TransactionManager txnmgr2 = mbs[1];

	Created txn1 = getCreated(txnmgr1, DURATION);
	ServerTransaction st1 = new ServerTransaction(txnmgr1, txn1.id);
	Created txn2 = getCreated(txnmgr2, DURATION);
	ServerTransaction st2 = new ServerTransaction(txnmgr2, txn2.id);

        logger.log(Level.INFO, "ServerTransaction #1: " + st1);
        logger.log(Level.INFO, "ServerTransaction #2: " + st2);
    }

    /**
     * Invoke parent's construct and parser
     * @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	parse();
        return this;
    }
}
