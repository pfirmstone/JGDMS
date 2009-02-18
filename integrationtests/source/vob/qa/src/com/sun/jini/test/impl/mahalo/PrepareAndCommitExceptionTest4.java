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

/*
 * Test intended to exercise new prepareAndCommit semantics.
 * - Create single test participant that joins transaction
 * - then throws UnknownTransactionException on PAC call
 * - Test verifies that CannotCommitException is thrown from commit call 
 *   (with a timeout parameter)
 */
public class PrepareAndCommitExceptionTest4 extends TxnManagerTest {

     public void run() throws Exception {
        TransactionManager mgr = null;
        Transaction.Created cr = null;
        TestParticipant part = null;

        startTxnMgr();

        part = new TestParticipantImpl();

        mgr = manager();

        logger.log(Level.INFO, "PrepareAndCommitExceptionTest4: run: mgr = " + mgr);
        cr = TransactionFactory.create(mgr, Lease.FOREVER);
        logger.log(Level.INFO, "Created: cr = " + cr);        
        part.setBehavior(OP_JOIN);
        logger.log(Level.INFO, "Configured participant to join");        
        part.setBehavior(OP_EXCEPTION_ON_PREPARECOMMIT);
        logger.log(Level.INFO, "Configured participant to throw an exception");                
        part.setBehavior(EXCEPTION_TRANSACTION);
        logger.log(Level.INFO, "Configured participant to throw UTE");                                
        logger.log(Level.INFO, "Configuring participant to behave");
        part.behave(cr.transaction);

        logger.log(Level.INFO, "Committing transaction");
        try {
            cr.transaction.commit(1000);
            throw new TestException("CannotCommitException not thrown");
        } catch (CannotCommitException cce) {
            logger.log(Level.INFO, "Caught expected exception: " + cce);
        }

    }
}
