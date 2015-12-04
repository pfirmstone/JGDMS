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
package org.apache.river.test.spec.txnmanager;

import java.util.logging.Level;
import org.apache.river.mahalo.*;
import net.jini.core.lease.*;
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;
import java.io.*;
import java.rmi.*;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;

// Shared classes
import org.apache.river.test.share.TxnManagerTest;
import org.apache.river.test.share.TxnTestUtils;
import org.apache.river.test.share.TestParticipant;
import org.apache.river.test.share.TestParticipantImpl;


public class JoinWhileActiveTest extends TxnManagerTest {
    private static final int NUM_PARTICIPANTS = 10;

    public void run() throws Exception {
        TransactionManager mgr = null;
        Transaction.Created cr = null;
        ServerTransaction str = null;
        TestParticipant part = null;
        TestParticipant[] parts = null;
        int state = 0;

        startTxnMgr();

        logger.log(Level.INFO, 
		   "JoinWhileActiveTest: creating " + NUM_PARTICIPANTS
                + " participants");
        parts = TxnTestUtils.createParticipants(NUM_PARTICIPANTS);
        part = new TestParticipantImpl(TestParticipant.DEFAULT_NAME + "-"
                + NUM_PARTICIPANTS);

        /*
         * A specified number of participants will join the transaction
         * and vote PREPARED.  Another participant will attempt to
         * join the transaction once its state is no longer ACTIVE.
         */
        try {
            mgr = manager();

            if (DEBUG) {
                logger.log(Level.INFO, "JoinWhileActiveTest: run: mgr = " + mgr);
            }
            cr = TransactionFactory.create(mgr, Lease.FOREVER);
            str = (ServerTransaction) cr.transaction;
            logger.log(Level.INFO, "JoinWhileActive: setting behavior for "
                    + "odd-ball participant");
            part.setBehavior(OP_JOIN);
            logger.log(Level.INFO, "JoinWhileActive: setting behavior for "
                    + NUM_PARTICIPANTS + " participants");
            TxnTestUtils.setBulkBehavior(OP_JOIN, parts);
            TxnTestUtils.setBulkBehavior(OP_VOTE_PREPARED, parts);
            TxnTestUtils.setBulkBehavior(OP_TIMEOUT_PREPARE, parts);
            TxnTestUtils.doBulkBehavior(str, parts);
            Thread committer = new CommitThread(str);
            committer.start();

            /*
             * First make sure the transaction's state
             * is no longer in the ACTIVE state
             */
            state = str.mgr.getState(str.id);

            while (state == ACTIVE) {
                if (DEBUG) {
                    int st = state;
                    logger.log(Level.INFO, "JoinWhileActiveTest: run: state = "
                            + org.apache.river.constants.TxnConstants.getName(st));
                }
                state = str.mgr.getState(str.id);
            }

            /*
             * Now instruct the odd-ball participant to join
             * the non ACTIVE transaction
             */
            try {
                part.behave(str);
		throw new TestException( "CannotJoinException is not raised");
            } catch (CannotJoinException cje) {
                if (DEBUG) {
                    cje.printStackTrace();
                    logger.log(Level.INFO, cje.getMessage());
                }
            }
        } catch (TransactionException te) {
            logger.log(Level.INFO, "state = "
                    + org.apache.river.constants.TxnConstants.getName(state));
            logger.log(Level.INFO, "Really a TransactionException");
            throw new TestException( "Unexpected TransactionException", te);
        }
    }
}
