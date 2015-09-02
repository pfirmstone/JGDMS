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
import org.apache.river.test.share.TestParticipant;
import org.apache.river.test.share.TestParticipantImpl;

/**
 * Create a transaction to be expired in fortey seconds
 * then sleep forty seconds.  Next commit the transaction.
 *
 * Expect: Either CannotCommitException exception or
 * UnknownTransactionException, depending on a race between
 * this test and the cancellation of the expired transaction.
 * If this thread wins, the transaction is still known to
 * the manager, but has expired, leading to a
 * CannotCommitException. If the expiration handling
 * in the manager wins, the transaction has been
 * canceled and discarded from the manager.
 */
public class CommitExpiredTest extends TxnManagerTest {

    private static final long FORTY_SECONDS = 40000;
    private static final long SLOP = 2000;

    public void run() throws Exception {
        TransactionManager mgr = null;
        Transaction.Created cr = null;
        ServerTransaction str = null;
        TestParticipant part = null;
        int state = 0;

        startTxnMgr();

        part = new TestParticipantImpl();

        try {
            mgr = manager();

            if (DEBUG) {
                System.out.println("CommitExpiredTest: run: mgr = " + mgr);
            }
            cr = TransactionFactory.create(mgr, FORTY_SECONDS);
            str = (ServerTransaction) cr.transaction;
            part.setBehavior(OP_JOIN);
            part.setBehavior(OP_VOTE_PREPARED);
            part.behave(cr.transaction);

            try {
                Thread.sleep(FORTY_SECONDS + SLOP);
            } catch (InterruptedException ie) {}

            if (DEBUG) {
                System.out.println("CommitExpiredTest: run: about to commit "
                    + "transaction that should have already been "
                    + "aborted due to timeout");
            }
            cr.transaction.commit();
	    throw new TestException("CannotCommitException is not raised");
        } catch (CannotCommitException cce) {

            // Expected exception. Test passed.
            if (DEBUG) {
                cce.printStackTrace();
                logger.log(Level.INFO, cce.getMessage());
            }
        }
        catch (UnknownTransactionException cce) {

            // Expected exception. Test passed.
            if (DEBUG) {
                cce.printStackTrace();
                logger.log(Level.INFO, cce.getMessage());
            }
        }
    }
}
