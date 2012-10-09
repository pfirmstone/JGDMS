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
 * <code>SimpleTransactionTest2</code> tests basic transactional features.
 * This test writes an entry without a transaction, then
 * <ul>
 *   <li> try to read/take it with a transaction, and make sure it works.
 * </ul>
 *
 * Test steps are as follows:
 * <ol>
 *   <li> Writes an entry without a transaction.
 *   <li> Read/take it with some transaction.
 *   <li> Check results.
 * </ol>
 *
 * 
 */
public class SimpleTransactionTest2 extends TransactionTestBase {

    public void run() throws Exception {
        simpleSetup();

        // useRead IfExists Transaction_type
        operationTest(new SpaceOperation(true, false,
                SpaceOperation.USE_SAME));
        operationTest(new SpaceOperation(true, true,
                SpaceOperation.USE_SAME));
        operationTest(new SpaceOperation(false, false,
                SpaceOperation.USE_SAME));
        operationTest(new SpaceOperation(false, true,
                SpaceOperation.USE_SAME));
    }

    // Try to read the entry with no transaction (fail)
    private void operationTest(SpaceOperation ope) throws TestException {

        // cleanup the space
        scrubSpaces();

        // create an entry
        SimpleEntry wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(2);

        // create a template
        SimpleEntry template = new SimpleEntry();
        template.string = "foo";

        // create a transaction object to write
        Transaction txn = createTransaction();

        // write an entry without a transaction
        writeEntry(null, wentry);
        Transaction txn2 = null;

        switch (ope.getTxnType()) {
          case SpaceOperation.USE_NULL:
            txn2 = null;
            break;
          case SpaceOperation.USE_SAME:
            txn2 = txn;
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
        if (entry == null) {
            failOperation(ope, "Couldn't get an entry.");
        } else {
            if (!wentry.equals(entry)) {
                failOperation(ope, "Written and got entries are different.");
            } else {
                passOperation(ope);
            }
        }

        // abort current transaction
        abortTransaction(txn);
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = SimpleTransactionTest2 : \n"
                + "tests basic transactional features.";
    }
}
