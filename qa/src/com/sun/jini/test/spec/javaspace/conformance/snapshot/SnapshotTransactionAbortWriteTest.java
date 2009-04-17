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
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;


/**
 * SnapshotTransactionAbortWriteTest asserts that entries written under a
 * transaction that aborts are discarded.
 *
 * It tests this statement for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotTransactionAbortWriteTest
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
     * This method asserts that entries written under a transaction
     * that aborts are discarded.
     *
     * It tests this statement for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.6, 3.1</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        Entry snapshot1;
        Entry snapshot2;
        SimpleEntry result;
        Transaction txn;
        long leaseTime1 = timeout1;
        long leaseTime2 = timeout2;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create snapshots
        snapshot1 = space.snapshot(sampleEntry1);
        snapshot2 = space.snapshot(sampleEntry2);

        // create the non null transaction
        txn = getTransaction();

        /*
         * write snapshot of 1-st entry with Lease.FOREVER lease time
         * to the space within the transaction
         */
        space.write(snapshot1, txn, Lease.FOREVER);

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "written within the non null transaction snapshot of "
                    + sampleEntry1 + " with Lease.FOREVER lease time"
                    + " is not available in the space"
                    + " within the transaction.");
        }
        logDebugText(sampleEntry1.toString() + " with Lease.FOREVER"
                + " lease time has been successfully"
                + " written to the space using it's snapshot"
                + " within the non null transaction.");

        /*
         * write snapshot of 2-nd entry with Lease.ANY value for lease time
         * to the space within the transaction
         */
        space.write(snapshot2, txn, Lease.ANY);

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry2, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "written within the non null transaction snapshot of "
                    + sampleEntry2 + " with Lease.ANY value for lease time"
                    + " is not available in the space"
                    + " within the transaction.");
        }
        logDebugText(sampleEntry1.toString() + " with Lease.ANY value"
                + " for lease time has been successfully"
                + " written to the space using it's snapshot"
                + " within the non null transaction.");

        /*
         * write snapshot of 1-st and 2-nd entries to the space within the
         * transaction again with finite lease times
         */
        space.write(snapshot1, txn, leaseTime1);
        logDebugText(sampleEntry1.toString() + " with " + leaseTime1
                + " has been successfully written again to the space"
                + " using it's snapshot within the non null transaction.");
        space.write(snapshot2, txn, leaseTime2);
        logDebugText(sampleEntry2.toString() + " with " + leaseTime2
                + " has been successfully written again to the space"
                + " using it's snapshot within the non null transaction.");

        // abort the transaction
        txnAbort(txn);

        // check that there are no entries in the space
        result = (SimpleEntry) space.read(null, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "there is " + result + " still available in the"
                    + " space after transaction's aborting"
                    + " but null is expected.");
        }
        logDebugText("There are no entries in the space after"
                + " transaction's aborting, as expected.");
    }
}
