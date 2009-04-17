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
package com.sun.jini.test.spec.javaspace.conformance.snapshot;

import java.util.logging.Level;

// net.jini
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionParticipant;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;
import com.sun.jini.test.spec.javaspace.conformance.ParticipantImpl;
import com.sun.jini.test.spec.javaspace.conformance.Committer;


/**
 * SnapshotTransactionCommitTakeIfExistsTest asserts that a takeIfExists is
 * considered to be successful only if all enclosing transactions commit
 * successfully.
 *
 * It tests this statement for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotTransactionCommitTakeIfExistsTest
        extends SnapshotAbstractTestBase {

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
     * It tests this statement for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.5, 2.6.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry = new SimpleEntry("TestEntry #1", 1);
        Entry snapshot;
        SimpleEntry result;
        Transaction txn;
        long leaseFor = timeout2;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create snapshot
        snapshot = space.snapshot(sampleEntry);

        // write snapshot of sample entry to the space
        space.write(snapshot, null, leaseForeverTime);

        // create the non null transaction
        txn = getTransaction();

        /*
         * takeIfExists written entry from the space using it's snapshot
         * within the transaction
         */
        space.takeIfExists(snapshot, txn, checkTime);

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

        // create the non null transaction with finite lease time
        txn = getTransaction(leaseFor);

        /*
         * takeIfExists written entry from the space using it's snapshot
         * within the transaction
         */
        space.takeIfExists(snapshot, txn, checkTime);

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

        /*
         * takeIfExists written entry from the space using it's snapshot
         * within the transaction
         */
        space.takeIfExists(snapshot, txn, checkTime);

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
