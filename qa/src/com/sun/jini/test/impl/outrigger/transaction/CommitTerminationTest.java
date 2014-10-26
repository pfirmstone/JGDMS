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
package com.sun.jini.test.impl.outrigger.transaction;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

// All other imports
import net.jini.space.JavaSpace;
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;


/**
 * <code>CommitTerminationTest</code> test checks
 * if <tt>commit</tt> is blocked until all operations under the transaction
 * have done.
 *
 * <ol>
 *   <li> Thread #1 tries to read/take an entry with a transaction.
 *        A space is empty, and this thread will be blocked.
 *   <li> Thread #2 attempts to commit the transaction.
 *        Because thread #1 is in progress, this thread will be blocked.
 *   <li> Thread #3 writes a matching entry into the space with/without the
 *        transaction.
 *   <li> read/take of thread #1 gets a TransactionException
 *        because it is operating under a completed transaction.
 * </ol>
 *
 * @author H.Fukuda
 */
public class CommitTerminationTest extends TransactionTestBase {

    // commonly used entry & template
    SimpleEntry wentry;
    SimpleEntry template;

    /*
     * Var to detect that Committer failed.
     * Has been added during porting.
     */
    private Exception failException = null;

    public void run() throws Exception {
        simpleSetup();

        // create an entry
        wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(6);

        // create a template
        template = new SimpleEntry();
        template.string = "foo";

        // read/commit/NoException  useRead  IfExists  Transaction_type
        testCommitTransaction(new SpaceOperation(true, false,
                SpaceOperation.USE_SAME));
        testCommitTransaction(new SpaceOperation(true, true,
                SpaceOperation.USE_SAME));
        testCommitTransaction(new SpaceOperation(false, false,
                SpaceOperation.USE_SAME));
        testCommitTransaction(new SpaceOperation(false, true,
                SpaceOperation.USE_SAME));
    }

    private void testCommitTransaction(SpaceOperation ope) throws Exception {

        // cleanup the space
	scrubSpaces();
        // create a dummy transaction
        Transaction txn2 = createTransaction();

        // create a transaction object to write
        Transaction txn = createTransaction();

        // write a dummy entry
        pass("step-1: thread #1: write a dummy entry to block \"IfExists\""
                + " operations.");
        writeEntry(txn2, wentry);

        // invoke commit thread
        Committer committer = new Committer(txn, txn2, this);
        committer.start();

        // wait for a while,
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        pass("step-2: thread #1: try to get an entry (blocked).");
        Entry gotEntry = new SimpleEntry();

        try {
            gotEntry = ope.get(getSpace(), template, txn, 10000);
            throw new TestException("get did not get a TransactionException");
        } catch (TransactionException e) {
        }
        pass("step-4: thread #1: get operation has returned");

        // wait for threads
        try {
            committer.join();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        if (failException != null) {
            throw failException;
        }
        pass("[Pass]: ");
    }


    class Committer extends Thread {
        private Transaction txn;
        private Transaction txn2;
        private TransactionTestBase parent;

        public Committer(Transaction txn, Transaction txn2,
                TransactionTestBase parent) {
            this.txn = txn;
            this.txn2 = txn2;
            this.parent = parent;
        }

        public void run() {
            try {
                try {
                    sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // commit transaction
                parent.pass("step-3: thread #2: commit transaction");
                commitTransaction(txn);

                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // commit transaction
                parent.pass("step-5: thread #2: commit dummy transaction");
                commitTransaction(txn2);
            } catch (Exception e) {
                failException = e;
            }
        }
    }
}
