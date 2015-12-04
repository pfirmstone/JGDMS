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
package org.apache.river.test.impl.outrigger.transaction;

import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;

// All other imports
import net.jini.space.JavaSpace;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.lease.Lease;
import java.rmi.RemoteException;


/**
 * <code>RecoveryTest</code> tests basic transactional features.
 * This test writes an entry under a transaction, then
 * <ul>
 *   <li> try to read/take it with no transaction, and make sure it fails.
 *   <li> try to read/take it with the same transaction, and make sure it works.
 *   <li> try to read/take it with another transaction, and make sure it fails.
 * </ul>
 *
 * Test steps are as follows:
 * <ol>
 *   <li> Writes an entry under a transaction.
 *   <li> Read/take it with no/same/different transaction.
 *   <li> Check results.
 * </ol>
 *
 * @author H.Fukuda
 */
public class ReactivationTest extends TransactionTestBase {

    public void run() throws Exception {
        simpleSetup();

        // create an entry
        SimpleEntry wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(10);

        // create a template
        SimpleEntry template = new SimpleEntry();
        template.string = "foo";

        // create a transaction object to write
        pass("step-1: create a transaction object");
        Transaction txn = null;

        try {
            Transaction.Created tc = TransactionFactory.create(getTxmgr(),
                    Lease.FOREVER);
            txn = tc.transaction;
        } catch (Exception e) {
            setupFailure("Can't make transaction object", e);
        }

        // write an entry under the transaction
        pass("step-2: write an entry with the transaction.");
        writeEntry(txn, wentry);

        // shutdown transaction manager.
        pass("step-3: shutdown transaction manager.");
        shutdownTxnMgr();

        // Try to make a txn object with shutdowned TxnMgr
        pass("step-4: re-activate a transaction manager.");
        Transaction txn2 = null;

        try {
            Transaction.Created tc = TransactionFactory.create(getTxmgr(),
                    Lease.FOREVER);
            txn2 = tc.transaction;
        } catch (Exception e) {
            throw new TestException(
                    "Got unexpected exception:" + e);
        }

        // abort/commit current transaction
        try {
            txn.commit();
        } catch (TransactionException e) {
            pass("step-5: TransactionException was thrown, as expected.");
        } catch (Exception e) {
            throw new TestException(
                    "Unexpected exception was thrown:" + e);
        }
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = ReactivationTest : \n"
                + "tests basic transactional features.";
    }
}
