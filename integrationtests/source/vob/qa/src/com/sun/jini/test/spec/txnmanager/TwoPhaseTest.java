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
import com.sun.jini.test.share.TestParticipantImpl;


public class TwoPhaseTest extends TxnManagerTest {

    public void run() throws Exception {
        TransactionManager mgr = null;
        Transaction.Created cr = null;
        TestParticipant part = null;

        startTxnMgr();

        part = new TestParticipantImpl();

        mgr = manager();

        if (DEBUG) {
            logger.log(Level.INFO, "TwoPhaseTest: run: mgr = " + mgr);
        }
        cr = TransactionFactory.create(mgr, Lease.FOREVER);
        part.setBehavior(OP_JOIN);
        part.setBehavior(OP_VOTE_NOTCHANGED);
        part.behave(cr.transaction);
        cr.transaction.commit();
        part.clearBehavior(OP_VOTE_NOTCHANGED);

        mgr = manager();

        if (DEBUG) {
            logger.log(Level.INFO, "TwoPhaseTest: run: mgr = " + mgr);
        }
        cr = TransactionFactory.create(mgr, Lease.FOREVER);
        part.setBehavior(OP_JOIN);
        part.setBehavior(OP_VOTE_PREPARED);
        part.behave(cr.transaction);
        cr.transaction.commit();
        part.clearBehavior(OP_VOTE_PREPARED);

        try {
            mgr = manager();

            if (DEBUG) {
                logger.log(Level.INFO, "TwoPhaseTest: run: mgr = " + mgr);
            }
            cr = TransactionFactory.create(mgr, Lease.FOREVER);
            part.setBehavior(OP_JOIN);
            part.setBehavior(OP_VOTE_ABORTED);
            part.behave(cr.transaction);
            cr.transaction.commit();
	    throw new TestException("CannotCommitException is not raised");
        } catch (CannotCommitException cce) {

            // Expected exception. Test passed.
            try {
                part.clearBehavior(OP_VOTE_ABORTED);
            } catch (RemoteException re1) {
                logger.log(Level.INFO, "TwoPhaseTest: run: " + re1.getMessage());
                re1.printStackTrace();
            }
        }
    }
}
