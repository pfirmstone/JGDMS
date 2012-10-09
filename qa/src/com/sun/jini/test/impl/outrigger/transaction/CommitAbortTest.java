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

// All other imports
import net.jini.space.JavaSpace;
import net.jini.core.transaction.Transaction;


/**
 * <code>CommitAbortTest</code> tests basic functions of commit/abort methods.
 * This test writes an entry under a transaction, then
 * <ul>
 *   <li> commits the transaction.
 *        Then try to read/take it without transaction and make sure it works.
 *   <li> aborts the transaction.
 *        Then try to read/take it without transaction and make sure it fails.
 * </ul>
 *
 * Test steps are as follows:
 * <ol>
 *   <li> Writes an entry with a transaction.
 *   <li> Commit/abort the transaction.
 *   <li> Read/take it without transaction.
 *   <li> Check results.
 * </ol>
 *
 * 
 */
public class CommitAbortTest extends TransactionTestBase {

    public void run() throws Exception {
        simpleSetup();

        // useAbort useRead IfExists Transaction_type
        operationTest(false, new SpaceOperation(true, false,
                SpaceOperation.USE_NULL));
        operationTest(true, new SpaceOperation(true, false,
                SpaceOperation.USE_NULL));
        operationTest(false, new SpaceOperation(true, true,
                SpaceOperation.USE_NULL));
        operationTest(true, new SpaceOperation(true, true,
                SpaceOperation.USE_NULL));
        operationTest(false, new SpaceOperation(false, false,
                SpaceOperation.USE_NULL));
        operationTest(true, new SpaceOperation(false, false,
                SpaceOperation.USE_NULL));
        operationTest(false, new SpaceOperation(false, true,
                SpaceOperation.USE_NULL));
        operationTest(true, new SpaceOperation(false, true,
                SpaceOperation.USE_NULL));
    }

    // Try to read the entry with no transaction (fail)
    private void operationTest(boolean useAbort, SpaceOperation ope)
            throws Exception {

        // cleanup the space
        scrubSpaces();

        // create an entry
        SimpleEntry wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(3);

        // create a template
        SimpleEntry template = new SimpleEntry();
        template.string = "foo";

        // create a transaction object
        Transaction txn = createTransaction();

        // write an entry under the transaction
        writeEntry(txn, wentry);

        // commit or abort the transaction
        if (useAbort) {
            abortTransaction(txn);
        } else {
            commitTransaction(txn);
        }

        // try to read/take the entry without transaction
        SimpleEntry entry = null;

	entry = (SimpleEntry) ope.get(space, template, null, JavaSpace.NO_WAIT);

        // check the result
        if (useAbort) {

            /*
             * when a transaction is aborted, the written entry should be
             * deleted from the space
             */
            if (entry != null) {
                failOperation(ope, "Could access an aborted entry.");
            } else {
                passOperation(ope);
            }
        } else {
            if (entry == null) {
                failOperation(ope, "Couldn't access a committed entry.");
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

        // abortTransaction(txn);
    }
}
