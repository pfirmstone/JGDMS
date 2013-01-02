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


/**
 * Pass an entry between two JavaSpaces using one transaction.
 * <ol>
 *   <li> write an entry into JavaSpace #0 without a transaction.
 *   <li> take/read the entry from JavaSpace #0 with a transaction.
 *   <li> write the entry into JavaSpace #1 with the same transaction.
 *   <li> commit/abort the transaction.
 *   <li> check the results.
 * </ol>
 *
 * @author H.Fukuda
 */
public class TwoSpaceTest extends TransactionTestBase {

    // commonly used entry & template
    SimpleEntry wentry;
    SimpleEntry template;

    public void run() throws Exception {
        twoSpaceSetup();

        // create an entry
        wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(9);

        // create a template
        template = new SimpleEntry();
        template.string = "foo";

        // useRead IfExists Transaction_type useAbort
        testTwoSpace(new SpaceOperation(true, false,
                SpaceOperation.USE_NULL), true);
        testTwoSpace(new SpaceOperation(true, true,
                SpaceOperation.USE_NULL), true);
        testTwoSpace(new SpaceOperation(false, false,
                SpaceOperation.USE_NULL), true);
        testTwoSpace(new SpaceOperation(false, true,
                SpaceOperation.USE_NULL), true);
        testTwoSpace(new SpaceOperation(true, false,
                SpaceOperation.USE_NULL), false);
        testTwoSpace(new SpaceOperation(true, true,
                SpaceOperation.USE_NULL), false);
        testTwoSpace(new SpaceOperation(false, false,
                SpaceOperation.USE_NULL), false);
        testTwoSpace(new SpaceOperation(false, true,
                SpaceOperation.USE_NULL), false);
    }

    private void testTwoSpace(SpaceOperation ope, boolean useAbort)
            throws TestException {
        Entry entry;

        // cleanup the space
        scrubSpaces();

        // create a transaction object to write
        Transaction txn = createTransaction();

        // step-1: write entry into space #1 without transaction
        pass("step-1: write entry into space #1 without transaction");
        writeEntry(0, null, wentry);

        // step-2: read/take the entry with a transaction
        pass("step-2: read/take the entry with a transaction");

        try {
            entry = ope.get(getSpaces()[0], template, txn, 10000);
        } catch (Exception e) {
            fail("Unexpected exception has thrown", e);
        }

        // step-3: write the entry into space #2 with the same transaction
        pass("step-3: write the entry into space #2 under the same"
                + " transaction");
        writeEntry(1, txn, wentry);

        // step-4: commit/abort the transaction
        pass("step-4: commit/abort the transaction");

        if (useAbort) {
            abortTransaction(txn);
        } else {
            commitTransaction(txn);
        }

        // check:
        if (useAbort) {

            // in case of abort, entry must be stay in the space #1
            if (!entryCheck(0, wentry) || !entryCheck(1, null)) {
                fail("consistency of aborted transaction is broken. [" + ope
                        + "]");
            } else {
                pass("[Pass]: abort for [" + ope + "]");
            }
        } else {
            if (ope.isRead()) {

                /*
                 * in case of read then commit, entry must be copied
                 * to the space #2
                 */
                if (!entryCheck(0, wentry) || !entryCheck(1, wentry)) {
                    fail("atomicity of comitted transaction is broken. [" + ope
                            + "]");
                } else {
                    pass("[Pass]: commit for [" + ope + "]");
                }
            } else {

                /*
                 * in case of take then commit, entry must be moved
                 * to the space #2
                 */
                if (!entryCheck(0, null) || !entryCheck(1, wentry)) {
                    fail("consistency of comitted transaction is broken. ["
                            + ope + "]");
                } else {
                    pass("[Pass]: commit for [" + ope + "]");
                }
            }
        }
    }

    private boolean entryCheck(int id, Entry target) throws TestException {
        Entry entry = null;

        try {
            entry = getSpaces()[id].take(template, null, JavaSpace.NO_WAIT);
        } catch (Exception e) {
            fail("Unexpected exception thrown while taking an entry", e);
        }

        if (entry == null) {
            if (target == null) {
                return true;
            } else {
                return false;
            }
        } else {
            if (target == null) {
                return false;
            } else {
                return entry.equals(target);
            }
        }
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = TwoSpaceTest : \n"
                + "Pass an entry between two JavaSpaces using one transaction.";
    }
}
