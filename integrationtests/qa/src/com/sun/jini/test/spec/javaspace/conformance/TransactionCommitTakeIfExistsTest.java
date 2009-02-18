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
package com.sun.jini.test.spec.javaspace.conformance;

import java.util.logging.Level;

// net.jini
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionParticipant;
import net.jini.space.JavaSpace;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

/**
 * TransactionCommitTakeIfExistsTest asserts that a takeIfExists is considered
 * to be successful only if all enclosing transactions commit successfully.
 *
 * @author Mikhail A. Markov
 */
public class TransactionCommitTakeIfExistsTest extends AbstractTestBase {

    /**
     * Sets up the testing environment.
     *
     * @param args Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {

        // mandatory call to parent
        super.setup(config);

        // get an instance of Transaction Manager
        mgr = getTxnManager();
    }

    /**
     * This method asserts that a takeIfExists is considered to be successful
     * only if all enclosing transactions commit successfully.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.5.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry result;
        Transaction txn;
        long leaseFor = timeout2;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // write sample entry to the space
        space.write(sampleEntry, null, leaseForeverTime);

        // create the non null transaction
        txn = getTransaction();

        // takeIfExists written entry from the space within the transaction
        SimpleEntry preCondition = (SimpleEntry)
            space.takeIfExists(sampleEntry, txn, checkTime);

        //Make sure that the entry was in the space
        if (preCondition != null) {
            // abort the transaction
            txnAbort(txn);

            // check that taken entry is still available in the space
            result = (SimpleEntry) space.read(sampleEntry, null, checkTime);

            if (result == null) {
                throw new TestException(
                        "TakeIfExists operation within the transaction has"
                        + " removed taken entry from the space after"
                        + " transaction's aborting.");
            }
            logDebugText("abort works as expected.");
        } else {
            throw new TestException( "TakeIfExists operation "
                                + "has not returned a valid entry.");
        }

        // create the non null transaction with finite lease time
        txn = getTransaction(leaseFor);

        // takeIfExists written entry from the space within the transaction
        space.takeIfExists(sampleEntry, txn, checkTime);

        // sleep to let the transaction expire
        Thread.sleep(leaseFor + 1000);

        // check that taken entry is still available in the space
        result = (SimpleEntry) space.read(sampleEntry, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "TakeIfExists operation within the transaction has"
                    + " removed taken entry from the space after"
                    + " transaction's expiration.");
        }
        logDebugText("transaction's expiration works as expected.");

        // create another non null transaction.
        txn = getTransaction();

        // takeIfExists written entry within the transaction.
        space.takeIfExists(sampleEntry, txn, checkTime);

        /*
         * create fake TransactionParticipant which
         * will prevent normal commit completion
         */
        TransactionParticipant tp = new ParticipantImpl();
        ((ServerTransaction) txn).join(tp, System.currentTimeMillis());

        // run thread which will prevent normal commit completion
        Committer committer = new Committer(tp, (ServerTransaction) txn,
                mgr);
        committer.start();

        // try to commit the operation
        try {
            txnCommit(txn);
            throw new TestException(
                    "Commit completes with no exceptions.");
        } catch (Exception ex) {
            logDebugText("commit produces"
                    + " the following exception, as expected: " + ex);
        }

        result = (SimpleEntry) space.read(sampleEntry, null, checkTime);
        if (result == null) {
            throw new TestException(
                    "TakeIfExists operation within the transaction has"
                    + " removed taken entry from the space after"
                    + " unsuccessfull transaction's committing.");
        }
    }
}
