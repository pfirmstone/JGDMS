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
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;
import com.sun.jini.test.spec.javaspace.conformance.TransactionTest;


/**
 * SnapshotTransactionWriteTest asserts that write with positive finite lease
 * times within the non null transaction:
 * 1) Places a copy of an entry into the given JavaSpaces service,
 *    even if the same Entry object is used in more than one write.
 * 2) The Entry passed to the write is not affected by this operations.
 * 3) If a write returns without throwing an exception, that entry is
 *    committed to the space, possibly within a transaction.
 *
 * It tests these statements for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotTransactionWriteTest extends TransactionTest {

    /**
     * This method asserts that write with positive finite lease times
     * within the non null transaction:
     * 1) Places a copy of an entry into the given JavaSpaces service,
     *    even if the same Entry object is used in more than one write.
     * 2) The Entry passed to the write is not affected by this operations.
     * 3) If a write returns without throwing an exception, that entry is
     *    committed to the space, possibly within a transaction.
     *
     * It tests these statements for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.3, 2.6.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        Entry snapshot1;
        Entry snapshot2;
        SimpleEntry origEntry1;
        SimpleEntry origEntry2;
        SimpleEntry result;
        Transaction txn;
        long leaseTime1 = timeout1;
        long leaseTime2 = timeout2;
        long leaseTime3 = leaseTime1 + checkTime;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // init original entries for comparing
        origEntry1 = (SimpleEntry) sampleEntry1.clone();
        origEntry2 = (SimpleEntry) sampleEntry2.clone();

        // create snapshots of sample entries
        snapshot1 = space.snapshot(sampleEntry1);
        snapshot2 = space.snapshot(sampleEntry2);

        // create the non null transaction
        txn = getTransaction();

        /*
         * write 1-st entry using it's snapshot within the transaction
         * with positive finite lease time
         */
        space.write(snapshot1, txn, leaseTime1);

        // check that original entry has not been changed
        if (!origEntry1.equals(sampleEntry1)) {
            throw new TestException(
                    "performed write operation with " + leaseTime1
                    + " lease time within the non null"
                    + " transaction has changed entry object: "
                    + " original entry " + origEntry1
                    + " has been changed by " + sampleEntry1);
        }

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed write operation with " + leaseTime1
                    + " lease time within the non null transaction of "
                    + sampleEntry1 + " using it's snapshot,"
                    + " written entry is not available in the space.");
        }
        logDebugText("Write within the non null transaction of "
                + sampleEntry1 + " using it's snapshot with " + leaseTime1
                + " lease time works as expected.");

        /*
         * write 2-nd entry using it's snapshot within the transaction
         * with positive finite lease time
         */
        space.write(snapshot2, txn, leaseTime2);

        // check that original entry has not been changed
        if (!origEntry2.equals(sampleEntry2)) {
            throw new TestException(
                    "performed write operation with " + leaseTime2
                    + " lease time within the non null"
                    + " transaction has changed entry object: "
                    + " original entry " + origEntry2
                    + " has been changed by " + sampleEntry2);
        }

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry2, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed write operation with " + leaseTime2
                    + " lease time within the non null transaction of "
                    + sampleEntry2 + " using it's snapshot,"
                    + " written entry is not available in the space.");
        }
        logDebugText("Write within the non null transaction of "
                + sampleEntry2 + " using it's snapshot with " + leaseTime2
                + " lease time works as expected.");

        /*
         * write 1-st entry using it's snapshot with another finite lease
         * time within the transaction again
         */
        space.write(sampleEntry1, txn, leaseTime3);

        // check that original entry has not been changed
        if (!origEntry1.equals(sampleEntry1)) {
            throw new TestException(
                    "performed 2-nd write operation of the same entry"
                    + " using it's snapshot with " + leaseTime3
                    + " lease time within the non null"
                    + " transaction has changed entry object:"
                    + " original entry " + origEntry1
                    + " has been changed by " + sampleEntry1);
        }

        /*
         * check that written entries are available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.take(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2-nd write operation with " + leaseTime3
                    + " lease time within the non null"
                    + " transaction for the same " + sampleEntry1
                    + " using it's snapshot,"
                    + " 1-st written entry is not available in the space.");
        }
        result = (SimpleEntry) space.take(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2-nd write operation with " + leaseTime3
                    + " lease time within the non null"
                    + " transaction for the same " + sampleEntry1
                    + " using it's snapshot,"
                    + " 2-nd written entry is not available in the space.");
        }
        logDebugText("2-nd write with " + leaseTime3
                + " lease time within the non null transaction of the same "
                + sampleEntry1 + " using it's snapshot works as expected.");

        // commit the transaction
        txnCommit(txn);
    }
}
