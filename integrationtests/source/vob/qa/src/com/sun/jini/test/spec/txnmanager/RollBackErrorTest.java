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
package com.sun.jini.test.spec.txnmanager;

import java.util.logging.Level;
import com.sun.jini.mahalo.*;
import net.jini.core.lease.*;
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;
import java.io.*;
import java.rmi.*;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

// Shared classes
import com.sun.jini.test.share.TxnManagerTest;
import com.sun.jini.test.share.TxnTestUtils;
import com.sun.jini.test.share.TestParticipant;


public class RollBackErrorTest extends TxnManagerTest {
    private static final int NUM_PARTICIPANTS = 1000;

    public void run() throws Exception {
        TransactionManager mgr = null;
        Transaction.Created cr = null;
        ServerTransaction str = null;
        TestParticipant[] parts = null;
        TestParticipant tp = null;

        startTxnMgr();

        // Create a number of TestParticipants
        logger.log(Level.INFO, "RollBackErrorTest: creating " + NUM_PARTICIPANTS
                + " participants");
        parts = TxnTestUtils.createParticipants(NUM_PARTICIPANTS);

        /*
         * All Participants will join and vote prepared.  One, randomly
         * chosen Participant will throw an exception when asked
         * to roll back.  There are two major cases for this,
         * the first time a RemoteException is thrown.  The second
         * time, a TransactionException is thrown.
         */
        mgr = manager();

        if (DEBUG) {
            logger.log(Level.INFO, "RollBackErrorTest: run: mgr = " + mgr);
        }
        cr = TransactionFactory.create(mgr, Lease.FOREVER);
        str = (ServerTransaction) cr.transaction;
        logger.log(Level.INFO, "RollBackErrorTest: setting behavior for "
                + NUM_PARTICIPANTS + " participants");
        TxnTestUtils.setBulkBehavior(OP_JOIN, parts);
        TxnTestUtils.setBulkBehavior(OP_VOTE_PREPARED, parts);
        tp = TxnTestUtils.chooseOne(parts);
        tp.setBehavior(OP_EXCEPTION_ON_ABORT);
        tp.setBehavior(EXCEPTION_REMOTE);
        TxnTestUtils.doBulkBehavior(cr.transaction, parts);
        logger.log(Level.INFO, "RollBackErrorTest: aborting the txn");
        cr.transaction.abort();

        cr = TransactionFactory.create(mgr, Lease.FOREVER);
        str = (ServerTransaction) cr.transaction;
        logger.log(Level.INFO, "RollBackErrorTest: setting behavior for "
                + NUM_PARTICIPANTS + " participants");
        tp.clearBehavior(EXCEPTION_REMOTE);
        tp.setBehavior(EXCEPTION_TRANSACTION);
        TxnTestUtils.doBulkBehavior(cr.transaction, parts);
        logger.log(Level.INFO, "RollBackErrorTest: aborting the txn");
        cr.transaction.abort();

        return;
    }
}
