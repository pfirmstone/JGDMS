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
import net.jini.core.transaction.Transaction;


/**
 * <code>SimpleTransactionTest</code> tests basic transactional features.
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
 * 
 */
public class SimpleTransactionTest extends TransactionTestBase {

    public void run() throws Exception {
        simpleSetup();

        // useRead IfExists Transaction_type isFail
        operationTest(new SpaceOperation(true, false,
                SpaceOperation.USE_NULL), true);
        operationTest(new SpaceOperation(true, true,
                SpaceOperation.USE_NULL), true);
        operationTest(new SpaceOperation(false, false,
                SpaceOperation.USE_NULL), true);
        operationTest(new SpaceOperation(false, true,
                SpaceOperation.USE_NULL), true);
        operationTest(new SpaceOperation(true, false,
                SpaceOperation.USE_SAME), false);
        operationTest(new SpaceOperation(true, true,
                SpaceOperation.USE_SAME), false);
        operationTest(new SpaceOperation(false, false,
                SpaceOperation.USE_SAME), false);
        operationTest(new SpaceOperation(false, true,
                SpaceOperation.USE_SAME), false);
        operationTest(new SpaceOperation(true, false,
                SpaceOperation.USE_DIFF), true);
        operationTest(new SpaceOperation(true, true,
                SpaceOperation.USE_DIFF), true);
        operationTest(new SpaceOperation(false, false,
                SpaceOperation.USE_DIFF), true);
        operationTest(new SpaceOperation(false, true,
                SpaceOperation.USE_DIFF), true);
    }

    // Try to read the entry with no transaction (fail)
    private void operationTest(SpaceOperation ope, boolean isFail)
            throws TestException {

        // cleanup the space
        scrubSpaces();

        /*
         * create an entry
         * pass("step-0: create an entry and template");
         */
        SimpleEntry wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(1);

        // create a template
        SimpleEntry template = new SimpleEntry();
        template.string = "foo";

        /*
         * create a transaction object to write
         * pass("step-1: create an transaction.");
         */
        Transaction txn = createTransaction();

        /*
         * write an entry under the transaction
         * pass("step-2: write an entry with the transaction.");
         */
        writeEntry(txn, wentry);
        Transaction txn2 = null;

        switch (ope.getTxnType()) {
          case SpaceOperation.USE_NULL:

            // pass("step-3: try to read/take the entry without transaction.");
            txn2 = null;
            break;
          case SpaceOperation.USE_SAME:

            /*
             * pass(
             * "step-3: try to read/take the entry with the same transaction.");
             */
            txn2 = txn;
            break;
          case SpaceOperation.USE_DIFF:

            /*
             * pass(
             * "step-3: try to read/take the entry with another transaction.");
             */
            txn2 = createTransaction();
            break;
        }

        // try to read/take the entry
        SimpleEntry entry = null;

        try {
            entry = (SimpleEntry) ope.get(space, template, txn2,
                    JavaSpace.NO_WAIT);
        } catch (Exception e) {
            fail("Exception thrown while reading/taking an entry", e);
        }

        // check the result
        if (isFail) {
            if (entry != null) {
                failOperation(ope, "Could access an entry out of transaction.");
            } else {
                passOperation(ope);
            }
        } else {
            if (entry == null) {
                failOperation(ope,
                        "Couldn't access an entry within the same"
                        + " transaction.");
            } else {
                if (!wentry.equals(entry)) {
                    failOperation(ope,
                            "Written and got entries are different.");
                } else {
                    passOperation(ope);
                }
            }
        }

        // abort current transaction
        abortTransaction(txn);
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = SimpleTransactionTest : \n"
                + "tests basic transactional features.";
    }
}
