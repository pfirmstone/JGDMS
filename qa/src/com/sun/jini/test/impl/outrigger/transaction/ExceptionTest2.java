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
 * Force to generate <tt>TransactionException</tt>s
 * by aborting the transaction while <tt>space.read</tt> or <tt>space.take</tt>
 * is in progress.
 *
 * <ol>
 *   <li> Thread #1 tries to read/take an entry with a transaction.
 *        A space is empty, and this thread will be blocked.
 *   <li> Thread #2 attempts to abort the transaction.
 *   <li> Thread #1 will be thrown <tt>TransactionException</tt>.
 * </ol>
 *
 * @author H.Fukuda
 */
public class ExceptionTest2 extends TransactionTestBase {

    // commonly used entry & template
    SimpleEntry wentry;
    SimpleEntry template;

    /*
     * Var to detect that EntryGetter failed.
     * Has been added during porting.
     */
    private Exception failException = null;

    public void run() throws Exception {
        simpleSetup();

        // create an entry
        wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(8);

        // create a template
        template = new SimpleEntry();
        template.string = "foo";

        // read/abort/TransactionException useRead IfExists Transaction_type
        testAbortTransaction(new SpaceOperation(true, false,
                SpaceOperation.USE_SAME));
        testAbortTransaction(new SpaceOperation(false, false,
                SpaceOperation.USE_SAME));
    }

    private void testAbortTransaction(SpaceOperation ope)
            throws Exception {

        // cleanup the space
        scrubSpaces();

        // create a transaction object to write
        Transaction txn = createTransaction();

        // invoke read/take thread
        EntryGetter getter = new EntryGetter(getSpace(), ope, txn, this);
        getter.start();

        // wait for a while,
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        // then commit/abort the transaction
        pass("step-2: thread #1: abort the transaction");
        abortTransaction(txn);

        // wait for the thread stops
        try {
            getter.join();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        if (failException != null) {
	    throw failException;
        }

        // check TransactionException has occurred
        if (!getter.isTxnExceptionOccurred()) {
            throw new TestException("TransactionException was not "
				  + "thrown as expected. [" + ope + "]");
        } else {
            pass("TransactionException was thrown as expected. [" + ope + "]");
        }
    }


    class EntryGetter extends Thread {
        private JavaSpace space;
        private SpaceOperation ope;
        private Transaction txn;
        private TransactionTestBase parent;
        private boolean exceptionOccurred = false;
        private Entry entry;

        public EntryGetter(JavaSpace space, SpaceOperation ope, Transaction txn,
                TransactionTestBase parent) {
            this.space = space;
            this.ope = ope;
            this.txn = txn;
            this.parent = parent;
            this.entry = null;
        }

        public synchronized void run() {
            try {

                // read/take access to empty JavaSpace
                parent.pass("step-1: thread #2: start read/take with a"
                        + " transaction (blocked)");

                try {
                    entry = ope.get(space, template, txn, 10000);
                } catch (TransactionException te) {
                    exceptionOccurred = true;
                    parent.pass("step-3: thread #2: TransactionException"
                            + " occurred as expected. [" + ope + "]");
                } catch (Exception e) {
                    parent.fail("Unexpected exception has been thrown", e);
                }

                /*
                 * parent.pass("[Thread #2]: got entry is [" + entry + "] ["
                 * + ope + "]");
                 */
            } catch (Exception e) {
                failException = e;
            }
        }

        public synchronized boolean isTxnExceptionOccurred() {
            return exceptionOccurred;
        }
    }
}
