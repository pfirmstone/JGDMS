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
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;


/**
 * Checks if read/take works well when a matching entry is written during the
 * operations. In this test, <tt>read</tt> and <tt>readIfExists</tt> should
 * work equally.
 *
 * <table border=1 bgcolor="honeydew">
 * <tr>
 * <td width=30%>
 * <td width=20% align=center><tt>read</tt>
 * <td width=20% align=center><tt>readIfExists</tt>
 * <tr>
 * <td>No matching entry exists
 * <td align=center>wait
 * <td align=center>null
 * <tr bgcolor="orange">
 * <td>Matching entry under transaction
 * <td align=center><b>wait</b>
 * <td align=center><b>wait</b>
 * <tr>
 * <td>Matching entry available
 * <td align=center>entry
 * <td align=center>entry
 * </table>
 *
 * <ol>
 *   <li> Thread #1 writes an entry into a space under a transaction.
 *   <li> Thread #2 tries to read/take the entry with no/another transaction.
 *        This thread will be blocked.
 *   <li> Thread #1 commits/aborts the transaction.
 *        This termination makes the entry visible from the outside of the
 *        transaction.
 *   <li> Read/take operation of thread #2 returns.
 *        In the case of abort, read/take gives up and null will be returned.
 *   <li> Check results.
 * </ol>
 *
 * @author H.Fukuda
 */
public class IfExistsTest extends TransactionTestBase {

    // commonly used entry & template
    SimpleEntry wentry;
    SimpleEntry template;

    /*
     * Var to detect that EntryGetter failed.
     * Has been added during porting.
     */
    private boolean isFailed = false;
    private String failMsg = "";

    public void run() throws Exception {
        simpleSetup();

        // create an entry
        wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(5);

        // create a template
        template = new SimpleEntry();
        template.string = "foo";

        // useRead IfExists Transaction_type
        testIfExists(new SpaceOperation(true, false,
                SpaceOperation.USE_NULL));
        testIfExists(new SpaceOperation(true, true,
                SpaceOperation.USE_NULL));
        testIfExists(new SpaceOperation(false, false,
                SpaceOperation.USE_NULL));
        testIfExists(new SpaceOperation(false, true,
                SpaceOperation.USE_NULL));
        testIfExists(new SpaceOperation(true, false,
                SpaceOperation.USE_DIFF));
        testIfExists(new SpaceOperation(true, true,
                SpaceOperation.USE_DIFF));
        testIfExists(new SpaceOperation(false, false,
                SpaceOperation.USE_DIFF));
        testIfExists(new SpaceOperation(false, true,
                SpaceOperation.USE_DIFF));
    }

    private void testIfExists(SpaceOperation ope) throws TestException {

        // cleanup the space
        scrubSpaces();

        // create another transaction object
        Transaction txn = null;

        if (ope.getTxnType() == SpaceOperation.USE_DIFF) {
            txn = createTransaction();
        }

        // invoke read/take thread
        EntryGetter getter = new EntryGetter(getSpace(), ope, txn, this);
        getter.start();

        // wait for a while,
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        // write matching entry
        if (ope.isIfExists()) {
            pass("step-3: thread #1: write a matching entry.");
        } else {
            pass("step-2: thread #1: write a matching entry.");
        }
        writeEntry(null, wentry);

        // wait for threads
        try {
            if (ope.isIfExists()) {
                pass("step-4: thread #1: wait until thread #2 die.");
            } else {
                pass("step-3: thread #1: wait until thread #2 die.");
            }
            getter.join();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        if (isFailed) {
            fail("EntryGetter failed with exception" + failMsg);
        }

        // check the result
        if (ope.isIfExists()) {

            // null should be retured for "IfExists" operation
            if (getter.isNullEntry()) {
                pass("[Pass]: " + ope);
            } else {
                fail("Non-null entry has returned. [" + ope + "]");
            }
        } else {
            if (getter.isNullEntry()) {
                fail("Null entry has returned. [" + ope + "]");
            } else {
                pass("[Pass]: " + ope);
            }
        }

        if (txn != null) {
            abortTransaction(txn);
        }
    }


    class EntryGetter extends Thread {
        private JavaSpace space;
        private SpaceOperation ope;
        private Transaction txn;
        private TransactionTestBase parent;
        private boolean exceptionOccurred = false;
        private Entry gotEntry = null;

        public EntryGetter(JavaSpace space, SpaceOperation ope, Transaction txn,
                TransactionTestBase parent) {
            this.space = space;
            this.ope = ope;
            this.txn = txn;
            this.parent = parent;
        }

        public void run() {
            try {

                // read/take access to empty JavaSpace
                if (txn == null) {
                    parent.pass("step-1: thread #2: try to get an entry without"
                            + " transcation.");
                } else {
                    parent.pass("step-1: thread #2: try to get an entry with a"
                            + " transcation.");
                }

                try {
                    gotEntry = ope.get(space, template, txn, 5000);
                } catch (Exception e) {
                    parent.fail("Unexpected exception has thrown", e);
                }

                if (ope.isIfExists()) {
                    parent.pass("step-2: thread #2: end of the thread.");
                } else {
                    parent.pass("step-4: thread #2: end of the thread.");
                }
            } catch (Exception e) {
                isFailed = true;
                failMsg = e.toString();
            }
        }

        public boolean isNullEntry() {
            if (gotEntry == null) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = IfExistsTest : \n"
                + "Checks if read/take works well when a matching entry is\n"
                + "written during the operations.";
    }
}
