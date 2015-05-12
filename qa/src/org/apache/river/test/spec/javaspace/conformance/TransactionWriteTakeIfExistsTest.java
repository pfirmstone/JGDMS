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
package org.apache.river.test.spec.javaspace.conformance;

import java.util.logging.Level;

// net.jini
import net.jini.core.transaction.Transaction;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

/**
 * TransactionWriteTakeIfExistsTest asserts that if the entry is written and
 * after that is taken by takeIfExists method within the non null
 * transaction, the entry will never be visible outside the transaction and
 * will not be added to the space when the transaction commits.
 *
 * @author Mikhail A. Markov
 */
public class TransactionWriteTakeIfExistsTest extends TransactionTest {

    /**
     * This method asserts that if the entry is written and
     * after that is taken by takeIfExists method within the non null
     * transaction, the entry will never be visible outside the transaction and
     * will not be added to the space when the transaction commits.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 3.1</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry result;
        Transaction txn;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create the non null transaction
        txn = getTransaction();

        /*
         * write 1-st sample and 2-nd sample entries twice
         * to the space within the transaction
         */
        space.write(sampleEntry1, txn, leaseForeverTime);
        space.write(sampleEntry1, txn, leaseForeverTime);
        space.write(sampleEntry2, txn, leaseForeverTime);
        space.write(sampleEntry2, txn, leaseForeverTime);

        /*
         * takeIfExists all written entries from the space
         * within the transaction
         */
        space.takeIfExists(sampleEntry1, txn, checkTime);
        space.takeIfExists(sampleEntry1, txn, checkTime);
        space.takeIfExists(sampleEntry2, txn, checkTime);
        space.takeIfExists(sampleEntry2, txn, checkTime);

        // commit the transaction
        txnCommit(txn);

        // check that there are no entries in the space
        result = (SimpleEntry) space.read(null, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "there is " + result + " still available in the"
                    + " space after transaction's committing"
                    + " but null is expected.");
        }
        logDebugText("There are no entries in the space after"
                + " transaction's committing, as expected.");
    }
}
