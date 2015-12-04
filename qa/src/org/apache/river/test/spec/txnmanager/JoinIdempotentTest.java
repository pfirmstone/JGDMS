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


public class JoinIdempotentTest extends TxnManagerTest {

    public void run() throws Exception {
        TransactionManager mgr = null;
        Transaction.Created cr = null;
        TestParticipant part = null;

        startTxnMgr();

        part = new TestParticipantImpl();

        try {
            mgr = manager();

            if (DEBUG) {
                System.out.println("JoinIdempotentTest: run: mgr = " + mgr);
            }
            cr = TransactionFactory.create(mgr, Lease.FOREVER);
            part.setBehavior(OP_JOIN);
            part.setBehavior(OP_INCR_CRASHCOUNT);
            part.setBehavior(OP_JOIN_IDEMPOTENT);
            part.behave(cr.transaction);
        } catch (CrashCountException cce) {
            if (DEBUG) {
                cce.printStackTrace();
                logger.log(Level.INFO, cce.getMessage());
            }

            try {
                part.clearBehavior(OP_JOIN_IDEMPOTENT);
                part.clearBehavior(OP_INCR_CRASHCOUNT);
                cr.transaction.abort();
            } catch (Exception e) {

                // ignore any exceptions at this point
            }
        }

        mgr = manager();

        if (DEBUG) {
            System.out.println("JoinIdempotentTest: run: mgr = " + mgr);
        }
        cr = TransactionFactory.create(mgr, Lease.FOREVER);
        part.setBehavior(OP_JOIN);
        part.setBehavior(OP_VOTE_NOTCHANGED);
        part.behave(cr.transaction);
        cr.transaction.commit();

        return;
    }
}
