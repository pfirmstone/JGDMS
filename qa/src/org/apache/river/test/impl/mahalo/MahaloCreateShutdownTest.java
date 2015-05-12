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
import org.apache.river.qa.harness.TestException;
import org.apache.river.test.share.*;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;
import net.jini.core.transaction.server.TransactionManager.Created;


public class MahaloCreateShutdownTest extends TxnMgrTestBase 
    implements TimeConstants, TxnManagerTestOpcodes 
{

    public void run() throws Exception {

        TransactionManager mb = getTransactionManager();
	logger.log(Level.INFO, "Got TransactionManager reference: " + mb);

	shutdown(0);

	Transaction.Created cr = 
	    TransactionFactory.create(mb, Lease.FOREVER);
        ServerTransaction str = (ServerTransaction) cr.transaction;
	logger.log(Level.INFO, "Got Transaction, id = " + str.id);

        TestParticipant[] testparts = 
	    TxnTestUtils.createParticipants(3);
	TxnTestUtils.setBulkBehavior(OP_JOIN, testparts);
	TxnTestUtils.setBulkBehavior(OP_TIMEOUT_COMMIT, testparts);
	TxnTestUtils.setBulkBehavior(OP_TIMEOUT_PREPARE, testparts);
	TxnTestUtils.setBulkBehavior(OP_VOTE_PREPARED, testparts);
	TxnTestUtils.setBulkBehavior(OP_TIMEOUT_VERYLONG, testparts);
	TxnTestUtils.doBulkBehavior(str, testparts);
	try {
	    mb.commit(str.id, 10*SECONDS);
	} catch (TimeoutExpiredException tee) {
	    logger.log(Level.INFO, "Caught expected TimeoutExpiredException");
	}

	shutdown(0);

	int state = mb.getState(str.id);
	logger.log(Level.INFO, "Txn State: " + state);

	logger.log(Level.INFO, "Done.");
    }
}
