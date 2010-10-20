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
import com.sun.jini.constants.TxnConstants;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

// Shared classes
import com.sun.jini.test.share.TxnManagerTest;
import com.sun.jini.test.share.TxnTestUtils;
import com.sun.jini.test.share.TestParticipant;
import com.sun.jini.test.share.TestParticipantImpl;


public class GetStateTest extends TxnManagerTest {

    public void run() throws Exception {
        TransactionManager mgr = null;
        Transaction.Created cr = null;
        ServerTransaction str = null;
        TestParticipant part = null;
        int state = 0;

        startTxnMgr();

        part = new TestParticipantImpl();

        /*
         * Create the transaction, extract the semantic object
         * and query the state.  Next, commit the transaction
         * and query the state. Success means that the
         * ACTIVE state is read in the first test and the
         * TransactionException is thrown in the second test.
         */
        mgr = manager();

        if (DEBUG) {
            logger.log(Level.INFO, "GetStateTest: run: mgr = " + mgr);
        }
        cr = TransactionFactory.create(mgr, Lease.FOREVER);
        str = (ServerTransaction) cr.transaction;
        state = str.mgr.getState(str.id);
        part.setBehavior(OP_JOIN);
        part.setBehavior(OP_VOTE_PREPARED);
        part.behave(cr.transaction);

        if (state != ACTIVE) {
            cr.transaction.commit();
            throw new TestException( "couldn't read ACTIVE state");
        }

        // So far, the test has passed.
        cr.transaction.commit();

        /*
         * Test that getState for a non-existant transaction
         * causes the TransactionException to be thrown.
         */
        try {
            state = str.mgr.getState(str.id);

            if (state != TransactionConstants.COMMITTED) {
                throw new TestException("Illegal transaction state observed: "
                        + TxnConstants.getName(state));
            }
        } catch (UnknownTransactionException ute) {
            // So far, the test has passed
        }

        /*
         * get the manager, tell the test participant to pause a
         * long time (30sec) during the commit.
         * At this point, the transaction will be in the prepare
         * phase with a state of VOTING.
         */
        try {
            mgr = manager();

            if (DEBUG) {
                logger.log(Level.INFO, "GetStateTest: run: mgr = " + mgr);
            }
            cr = TransactionFactory.create(mgr, Lease.FOREVER);
            str = (ServerTransaction) cr.transaction;
            state = str.mgr.getState(str.id);
            part.setBehavior(OP_JOIN);
            part.setBehavior(OP_VOTE_PREPARED);
            part.setBehavior(OP_TIMEOUT_PREPARE);
            part.behave(cr.transaction);
            Thread commiter = new CommitThread(cr.transaction);
            commiter.start();

            /*
             * By looping on the first ocurrance of
             * the VOTING state, we know that
             * the transaction is cycling from ACTIVE
             * to voting.  The issue in this test is
             * to give the commiter thread enough time
             * to do its thang.
             */
            while (true) {
                state = str.mgr.getState(str.id);

                if (state == VOTING) {
                    break;
                }
            }
        } catch (UnknownTransactionException ute) {
            // Things happened quickly and transaction now gone
        }

        // Expect no exception at this point. First test passed.

        /*
         * since the commit was done in a thread,
         * we need to wait until the transaction
         * has been scrubbed before proceeding.
         */
        try {
            while (true) {
                state = str.mgr.getState(str.id);
            }
        } catch (TransactionException bte) {
        } catch (RemoteException re) {}

        /*
         * get the manager, tell the test participant to pause a
         * long time (30sec) during the commit.
         * At this point, the transaction will be in the prepare
         * phase with a state of VOTING.
         */
        try {
            mgr = manager();

            if (DEBUG) {
                logger.log(Level.INFO, "GetStateTest: run: mgr = " + mgr);
            }
            cr = TransactionFactory.create(mgr, Lease.FOREVER);
            str = (ServerTransaction) cr.transaction;
            part.clearBehavior(OP_TIMEOUT_PREPARE);
            part.setBehavior(OP_JOIN);
            part.setBehavior(OP_VOTE_PREPARED);
            part.setBehavior(OP_TIMEOUT_ABORT);
            part.behave(cr.transaction);
            Thread aborter = new AbortThread(cr.transaction);
            aborter.start();

            /*
             * By looping on the first ocurrance of
             * the VOTING state, we know that
             * the transaction is cycling from ACTIVE
             * to aborted.  The issue in this test is
             * to give the commiter thread enough time
             * to do its thang.
             */
            while (true) {
                state = str.mgr.getState(str.id);

                if (DEBUG) {
                    int st = state;
                    logger.log(Level.INFO, "state = "
                            + com.sun.jini.constants.TxnConstants.getName(st));
                }

                if (state == ABORTED) {
                    break;
                }
            }
        } catch (UnknownTransactionException ute) {
            // things happened too quickly so transaction now gone
        }

        long timeout = System.currentTimeMillis() + 10 * 60 * 1000;
        try {
            while (System.currentTimeMillis() < timeout) {
                state = str.mgr.getState(str.id);
                if(state != ABORTED) {
                	throw new TestException("Non-aborted state after abort call");
                }
            }
        } catch (UnknownTransactionException bte) {
            // Expected exception. Second test passed.
            return;
        } catch (RemoteException re) {}
        throw new TestException( "UnknownTransactionException is not raised");
    }
}
