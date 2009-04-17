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

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;


/**
 * SnapshotTransactionTakeReadTest asserts that when taken,
 * an entry may not be read or taken by any other transaction.
 *
 * It tests this statement for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotTransactionTakeReadTest extends SnapshotAbstractTestBase {

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
     * This method asserts that when taken, an entry may not be read or taken
     * by any other transaction.
     *
     * It tests this statement for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.6, 3.1.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry = new SimpleEntry("TestEntry #1", 1);
        Entry snapshot;
        SimpleEntry result;
        Transaction txn1, txn2;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create snapshot
        snapshot = space.snapshot(sampleEntry);

        // write sample entry to the space
        space.write(sampleEntry, null, leaseForeverTime);

        // create two non null transactions
        txn1 = getTransaction();
        txn2 = getTransaction();

        /*
         * take written entry from the space within 1-st transaction
         * using it's snapshot
         */
        space.take(snapshot, txn1, checkTime);

        // check that we can not read the entry outside both transactions
        result = (SimpleEntry) space.read(sampleEntry, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed read with template " + sampleEntry
                    + " outside both transactions while taking it within"
                    + " 1-st one, expected null but read " + result);
        }
        logDebugText("Entry can't be read outside both transactions.");

        /*
         * check that we can not read the entry outside both transactions
         * using it's snapshot
         */
        result = (SimpleEntry) space.read(snapshot, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed read with template " + sampleEntry
                    + " outside both transactions using it's snapshot"
                    + " while taking it within"
                    + " 1-st one, expected null but read " + result);
        }
        logDebugText("Entry can't be read outside both transactions"
                + " using it's snapshot.");

        // check that we can not read the entry within another transaction
        result = (SimpleEntry) space.read(sampleEntry, txn2, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed read with template " + sampleEntry
                    + " within 2-nd transaction while taking it"
                    + " within 1-st one, expected null but read " + result);
        }
        logDebugText("Entry can't be read in 2-nd transaction.");

        /*
         * check that we can not read the entry within another transaction
         * using it's snapshot
         */
        result = (SimpleEntry) space.read(snapshot, txn2, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed read with template " + sampleEntry
                    + " within 2-nd transaction using it's snapshot"
                    + " while taking it"
                    + " within 1-st one, expected null but read " + result);
        }
        logDebugText("Entry can't be read in 2-nd transaction"
                + " using it's snapshot.");

        // check that we can not take the entry outside both transactions
        result = (SimpleEntry) space.take(sampleEntry, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed take with template " + sampleEntry
                    + " outside both transactions while taking it"
                    + " within 1-st one, expected null but took " + result);
        }
        logDebugText("Entry can't be taken outside both transactions.");

        /*
         * check that we can not take the entry outside both transactions
         * using it's snapshot
         */
        result = (SimpleEntry) space.take(snapshot, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed take with template " + sampleEntry
                    + " outside both transactions using it's snapshot"
                    + " while taking it"
                    + " within 1-st one, expected null but took " + result);
        }
        logDebugText("Entry can't be taken outside both transactions"
                + " using it's snapshot.");

        // check that we can not take the entry within another transaction
        result = (SimpleEntry) space.take(sampleEntry, txn2, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed take with template " + sampleEntry
                    + " within 2-nd transaction while taking it"
                    + " within 1-st one, expected null but took " + result);
        }
        logDebugText("Entry can't be taken in 2-nd transaction.");

        /*
         * check that we can not take the entry within another transaction
         * using it's snapshot
         */
        result = (SimpleEntry) space.take(snapshot, txn2, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed take with template " + sampleEntry
                    + " within 2-nd transaction using it's snapshot"
                    + " while taking it"
                    + " within 1-st one, expected null but took " + result);
        }
        logDebugText("Entry can't be taken in 2-nd transaction"
                + " using it's snapshot.");

        // commit both transactions
        txnCommit(txn1);
        txnCommit(txn2);
    }
}
