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
package org.apache.river.test.spec.javaspace.conformance.snapshot;

import java.util.logging.Level;

// net.jini
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

// org.apache.river.qa
import org.apache.river.qa.harness.Test;
import org.apache.river.test.spec.javaspace.conformance.SimpleEntry;
import org.apache.river.test.spec.javaspace.conformance.TransactionTest;


/**
 * SnapshotTransactionCommitWriteTest asserts that an entry that is written
 * under the non null transaction is not visible outside its transaction until
 * the transaction successfully commits.
 *
 * It tests this statement for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotTransactionCommitWriteTest
        extends TransactionTest {

    /**
     * This method asserts that an entry that is written under the
     * non null transaction is not visible outside its transaction until the
     * transaction successfully commits.
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
        long leaseTime = timeout2;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create snapshots of sample entries
        snapshot1 = space.snapshot(sampleEntry1);
        snapshot2 = space.snapshot(sampleEntry2);

        // create the non null transaction
        txn = getTransaction();

        /*
         * write 1-st entry using it's snapshot with Lease.FOREVER lease
         * time to the space within the transaction
         */
        space.write(snapshot1, txn, Lease.FOREVER);

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry1 + " using it's snapshot with"
                    + " Lease.FOREVER lease time is not available"
                    + " in the space within the transaction.");
        }

        /*
         * check that written entry is not available
         * outside the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry1 + " using it's snapshot with"
                    + " Lease.FOREVER lease time"
                    + " is visible in the space outside the transaction.");
        }
        logDebugText("Written within the non null transaction "
                + sampleEntry1 + " using it's snapshot with Lease.FOREVER"
                + " lease time is actually available inside the"
                + " transaction and not visible outside it.");

        /*
         * write 2-nd entry using it's snapshot with Lease.ANY value for
         * lease time to the space within the transaction
         */
        space.write(snapshot2, txn, Lease.ANY);

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry2, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry2 + " using it's snapshot with Lease.ANY"
                    + " value for lease time is not available in the space"
                    + " within the transaction.");
        }

        /*
         * check that written entry is not available
         * outside the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry2, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry2 + " using it's snapshot with Lease.ANY"
                    + " value for lease time"
                    + " is visible in the space outside the transaction.");
        }
        logDebugText("Written within the non null transaction "
                + sampleEntry2 + " using it's snapshot with Lease.ANY"
                + " value for lease time is actually available inside the"
                + " transaction and not visible outside it.");

        /*
         * write 1-st entry to the space using it's snapshot within
         * the transaction again with finite lease time
         */
        space.write(snapshot1, txn, leaseTime);

        /*
         * check that written entry is not available
         * outside the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry1 + " using it's snapshot with "
                    + leaseTime + " lease time"
                    + " is visible in the space outside the transaction.");
        }

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.take(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + " using it's snapshot, there are no"
                    + " entries are available in the space within the"
                    + " transaction while 2 are expected");
        }
        result = (SimpleEntry) space.take(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + " using it's snapshot, there is only"
                    + " 1 entry available in the space within the"
                    + " transaction while 2 are expected");
        }
        logDebugText("Written within the non null transaction "
                + sampleEntry1 + " using it's snapshot with " + leaseTime
                + " lease time is actually available inside the"
                + " transaction and not visible outside it.");

        /*
         * write 1-st entry to the space using it's snapshot
         * within the transaction twice
         */
        space.write(snapshot1, txn, Lease.FOREVER);
        space.write(snapshot1, txn, leaseTime);

        // commit the transaction
        txnCommit(txn);

        // check that 2-nd sample entry is available in the space
        result = (SimpleEntry) space.read(sampleEntry2, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry2 + " using it's snapshot is not"
                    + " available in the space"
                    + " after transaction's committing.");
        }

        // check that both 1-st entries are available in the space
        result = (SimpleEntry) space.take(sampleEntry1, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + " using it's snapshot, there are no"
                    + " entries are available in the space after"
                    + " transaction's committing while 2 are expected");
        }
        result = (SimpleEntry) space.take(sampleEntry1, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + " using it's snapshot, there is only"
                    + " 1 entry available in the space after transaction's"
                    + " committing while 2 are expected");
        }
        logDebugText("All written entries are available in the space"
                + " after transaction's committing.");
    }
}
