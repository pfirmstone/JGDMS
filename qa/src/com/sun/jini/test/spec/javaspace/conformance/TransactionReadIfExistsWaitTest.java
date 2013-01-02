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
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 * TransactionReadIfExistsWaitTest asserts, that for readIfExists method
 * if the only possible matches for the template have conflicting locks
 * from one or more other transactions, the timeout value specifies how long
 * the client is willing to wait for interfering transactions to settle
 * before returning a values and that if at the end of that time no value
 * can be returned that would not interfere with transactional state,
 * null is returned.
 *
 * @author Mikhail A. Markov
 */
public class TransactionReadIfExistsWaitTest extends TransactionTest {

    /**
     * This method asserts, that for readIfExists method
     * if the only possible matches for the template have conflicting locks
     * from one or more other transactions, the timeout value specifies how long
     * the client is willing to wait for interfering transactions to settle
     * before returning a values and that if at the end of that time no value
     * can be returned that would not interfere with transactional state,
     * null is returned.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.4.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry result;
        Transaction txn;
        long curTime1;
        long curTime2;
        long leaseTime;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // write sample entry to the space
        space.write(sampleEntry1, null, leaseForeverTime);

        // create the non null transaction with finite time
        leaseTime = timeout2;
        txn = getTransaction(leaseTime);

        /*
         * take written entry from the space within the transaction
         * to lock read operations outside the transaction
         */
        space.take(sampleEntry1, txn, checkTime);

        /*
         * readIfExists an entry from the space with timeout less then
         * transaction's lease time, check that readIfExists request will
         * return null value in time specified in this request
         */
        curTime1 = System.currentTimeMillis();
        result = (SimpleEntry) space.readIfExists(sampleEntry1, null,
                timeout1);
        curTime2 = System.currentTimeMillis();

        if (result != null) {
            throw new TestException(
                    "performed readIfExists of " + sampleEntry1 + " with "
                    + timeout1 + " timeout, expected null result but read "
                    + result);
        }

        if (((curTime2 - curTime1) < timeout1)
                || ((curTime2 - curTime1) >= leaseTime)) {
            throw new TestException(
                    "performed readIfExists with " + timeout1
                    + " timeout, transaction's lease time is " + leaseTime
                    + ", expected return time is greater then " + timeout1
                    + " and less then " + leaseTime
                    + ", but has returned in " + (curTime2 - curTime1));
        }
        logDebugText("readIfExists with " + timeout1 + " timeout and"
                + " transaction with " + leaseTime + " lease time"
                + " works as expected.");

        // commit the transaction and clean the space
        try {
            txnCommit(txn);
        } catch (Exception ex) {}
        cleanSpace(space);

        // write sample entry to the space
        space.write(sampleEntry1, null, leaseForeverTime);

        // create the non null transaction with finite time
        curTime1 = System.currentTimeMillis();
        leaseTime = timeout1;
        txn = getTransaction(leaseTime);

        /*
         * take written entry from the space within the transaction
         * to lock read operations outside the transaction
         */
        space.take(sampleEntry1, txn, checkTime);

        /*
         * readIfExists an entry from the space with timeout greater
         * then transaction's lease time, check that readIfExists request
         * will return non null entry after transaction's expiration
         */
        result = (SimpleEntry) space.readIfExists(sampleEntry1, null,
                timeout2);
        curTime2 = System.currentTimeMillis();

        if (result == null) {
            throw new TestException(
                    "performed readIfExists of " + sampleEntry1 + " with "
                    + timeout2 + " timeout, expected non null but read"
                    + " null result.");
        }

        if (((curTime2 - curTime1) < leaseTime)
                || ((curTime2 - curTime1) >= timeout2)) {
            throw new TestException(
                    "performed readIfExists with " + timeout2
                    + " timeout, transaction's lease time is " + leaseTime
                    + ", expected return time is greater then " + leaseTime
                    + " and less then " + timeout2
                    + ", but has returned in " + (curTime2 - curTime1));
        }
        logDebugText("readIfExists with " + timeout2 + " timeout and"
                + " transaction with " + leaseTime + " lease time"
                + " works as expected.");

        // clean the space
        cleanSpace(space);

        // write sample entry to the space
        space.write(sampleEntry1, null, leaseForeverTime);

        // create the non null transaction with finite time
        curTime1 = System.currentTimeMillis();
        leaseTime = timeout2 * 2;
        txn = getTransaction(leaseTime);

        /*
         * take written entry from the space within the transaction
         * to lock read operations outside the transaction
         */
        space.take(sampleEntry1, txn, checkTime);

        /*
         * start thread which will write 2-nd sample entry
         * to the space in timeout1 ms
         */
        EntryWriter writer = new EntryWriter(sampleEntry2, timeout1, space);
        writer.start();

        // readIfExists any entry from the space
        result = (SimpleEntry) space.readIfExists(null, null, timeout2);
        curTime2 = System.currentTimeMillis();

        /*
         * check that readIfExists request returns 2-nd written entry
         * before transaction's and readIfExists expirations
         */
        if (result == null) {
            throw new TestException(
                    "performed readIfExists with null template and "
                    + timeout2 + " timeout, expected non null but read"
                    + " null result.");
        }

        if (((curTime2 - curTime1) < timeout1)
                || ((curTime2 - curTime1) >= timeout2)) {
            throw new TestException(
                    "performed readIfExists with " + timeout2
                    + " timeout, transaction's lease time is " + leaseTime
                    + " 2-nd entry has been written to the space in "
                    + timeout1 + " ms,"
                    + " expected return time is greater then " + timeout1
                    + " and less then " + timeout2
                    + ", but has returned in " + (curTime2 - curTime1));
        }
        logDebugText("readIfExists with " + timeout2 + " timeout and"
                + " transaction with " + leaseTime + " lease time"
                + " and 2-nd entry written to the space in " + timeout1
                + " ms from the start time, works as expected.");

        // commit the transaction
        try {
            txnCommit(txn);
        } catch (Exception ex) {}
    }
}
