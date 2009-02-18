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
import com.sun.jini.test.spec.javaspace.conformance.EntryWriter;


/**
 * SnapshotTransactionReadWaitTest asserts that a read request will wait
 * until a matching entry is found or until transactions settle,
 * whichever is longer, up to the timeout period.
 *
 * It tests this statement for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotTransactionReadWaitTest extends SnapshotAbstractTestBase {

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
     * This method asserts that a read request will wait until a matching entry
     * is found or until transactions settle, whichever is longer,
     * up to the timeout period.
     *
     * It tests this statement for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.4, 2.6.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        Entry snapshot1;
        SimpleEntry result;
        long curTime1;
        long curTime2;
        Transaction txn;
        long timeout = leaseForeverTime;
        long leaseTime;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create snapshot of 1-st sample entry
        snapshot1 = space.snapshot(sampleEntry1);

        // write sample entry to the space
        space.write(sampleEntry1, null, leaseForeverTime);

        // create the non null transaction with finite time
        leaseTime = timeout2;
        txn = getTransaction(leaseTime);

        /*
         * take written entry from the space within the transaction
         * to lock read operations outside the transaction
         */
        space.take(sampleEntry1, txn, timeout);

        /*
         * read an entry from the space using it's snapshot
         * with timeout less then transaction's lease time,
         * check that read request will return null value
         * in time specified in read request
         */
        curTime1 = System.currentTimeMillis();
        result = (SimpleEntry) space.read(snapshot1, null, timeout1);
        curTime2 = System.currentTimeMillis();

        if (result != null) {
            throw new TestException(
                    "performed read with template " + sampleEntry1 + " and "
                    + timeout1 + " timeout using it's snapshot,"
                    + " expected null result but read " + result);
        }

        if ((curTime2 - curTime1) < timeout1
                || (curTime2 - curTime1) >= leaseTime) {
            throw new TestException(
                    "performed read with " + timeout1
                    + " timeout using snapshot,"
                    + " expected return time is greater then " + timeout1
                    + " and less then " + leaseTime
                    + ", but has returned in " + (curTime2 - curTime1));
        }
        logDebugText("Read with " + timeout1 + " timeout has returned in "
                + (curTime2 - curTime1));

        // commit the transaction and clean the space
        try {
            txnCommit(txn);
        } catch (Exception ex) {}
        cleanSpace(space);

        // write sample entry to the space
        space.write(sampleEntry1, null, leaseForeverTime);

        // create the non null transaction with finite time
        leaseTime = timeout1;
        curTime1 = System.currentTimeMillis();
        txn = getTransaction(leaseTime);

        /*
         * take written entry from the space within the transaction
         * to lock read operations outside the transaction
         */
        space.take(sampleEntry1, txn, timeout);

        /*
         * read an entry from the space using it's snapshot
         * with timeout greater then transaction's lease time,
         * check that read request will return
         * non null entry after transaction's expiration
         */
        result = (SimpleEntry) space.read(snapshot1, null, timeout2);
        curTime2 = System.currentTimeMillis();

        if (!sampleEntry1.equals(result)) {
            throw new TestException(
                    "performed read with template " + sampleEntry1 + " and "
                    + timeout2 + " timeout using it's snapshot, expected "
                    + sampleEntry1 + " result but read " + result);
        }

        if ((curTime2 - curTime1) < leaseTime
                || (curTime2 - curTime1) >= timeout2) {
            throw new TestException(
                    "performed read with " + timeout2
                    + " timeout using snapshot,"
                    + " expected return time is greater then " + leaseTime
                    + " and less then " + timeout2
                    + ", but has returned in " + (curTime2 - curTime1));
        }
        logDebugText("Read with " + timeout2 + " timeout has returned in "
                + (curTime2 - curTime1));

        // clean the space
        cleanSpace(space);

        // write sample entry to the space
        space.write(sampleEntry1, null, leaseForeverTime);

        // create the non null transaction with finite time
        leaseTime = timeout2 * 2;
        curTime1 = System.currentTimeMillis();
        txn = getTransaction(leaseTime);

        /*
         * take written entry from the space within the transaction
         * to lock read operations outside the transaction
         */
        space.take(sampleEntry1, txn, leaseForeverTime);

        /*
         * start thread which will write 2-nd sample entry
         * to the space in timeout1 ms
         */
        EntryWriter writer = new EntryWriter(sampleEntry2, timeout1, space);
        writer.start();

        // read any entry from the space using snapshot of null
        result = (SimpleEntry) space.read(space.snapshot(null), null,
                timeout2 * 2);
        curTime2 = System.currentTimeMillis();

        // check that read request has returned required entry
        if (!sampleEntry2.equals(result)) {
            throw new TestException(
                    "performed read with snapshot of null template and "
                    + (timeout2 * 2) + " timeout, expected " + sampleEntry1
                    + " result but read " + result);
        }

        /*
         * check that read request returns 2-nd written entry before
         * transaction's and read's expirations
         */
        if ((curTime2 - curTime1) > leaseTime
                || (curTime2 - curTime1) < timeout1) {
            throw new TestException(
                    "performed read with " + (timeout2 * 2)
                    + " timeout, expected return time is greater then "
                    + leaseTime + " and less then " + timeout1
                    + ", but has returned in " + (curTime2 - curTime1));
        }
        logDebugText("Read with " + (timeout2 * 2)
                + " timeout has returned in " + (curTime2 - curTime1));

        // commit the transaction to let the test finish
        try {
            txnCommit(txn);
        } catch (Exception ex) {}
    }
}
