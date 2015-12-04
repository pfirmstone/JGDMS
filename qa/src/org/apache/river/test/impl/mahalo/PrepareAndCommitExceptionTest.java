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

/*
 * Test intended to exercise new prepareAndCommit semantics.
 * - Create single test participant that joins transaction
 * - then throws RemoteException on prepareAndCommit (PAC) call
 * - then eventually throws UnknownTransactionException on PAC call
 * - Test verifies that RemoteException is thrown from commit call (no timeout)
 */
public class PrepareAndCommitExceptionTest extends TxnManagerTest {

      class Clearer implements Runnable {
         TestParticipant part;
         Clearer(TestParticipant part) {
             this.part = part;
         }
 
         public void run() {
            try {
                Thread.sleep(10000);
            } catch (Exception e) {
                logger.log(Level.INFO, "Caught sleep exception -- ignoring: " + e);                                    
            }
            try {
                part.clearBehavior(EXCEPTION_REMOTE);
            } catch (RemoteException re) {
                logger.log(Level.INFO, "Caught clear exception -- ignoring: " + re);                                    
            }
         }
     }  
      
     public void run() throws Exception {
        TransactionManager mgr = null;
        Transaction.Created cr = null;
        TestParticipant part = null;

        startTxnMgr();

        part = new TestParticipantImpl();

        mgr = manager();

        logger.log(Level.INFO, "PrepareAndCommitExceptionTest: run: mgr = " 
            + mgr);
        cr = TransactionFactory.create(mgr, Lease.FOREVER);
        logger.log(Level.INFO, "Created: cr = " + cr);        
        part.setBehavior(OP_JOIN);
        logger.log(Level.INFO, "Configured participant to join");        
        part.setBehavior(OP_EXCEPTION_ON_PREPARECOMMIT);
        logger.log(Level.INFO, "Configured participant to throw an exception");                
        part.setBehavior(EXCEPTION_REMOTE);        
        logger.log(Level.INFO, "Configured participant to throw RE 1st");                
        part.setBehavior(EXCEPTION_TRANSACTION);
        logger.log(Level.INFO, "Configured participant to throw UTE 2nd");                                
        logger.log(Level.INFO, "Configuring participant to behave");
        part.behave(cr.transaction);
        Clearer clearer = new Clearer(part);
        Thread t = new Thread(clearer);
        logger.log(Level.INFO, "Running clearer thread");                        
        t.start();
        logger.log(Level.INFO, "Committing transaction");                        
        try {
            cr.transaction.commit();
            throw new TestException("ServerException not thrown");
        } catch (ServerException se) {
            logger.log(Level.INFO, "Caught expected exception: " + se);                                    
        }

    }
}
