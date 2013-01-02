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
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 * TransactionAbortWriteTest asserts that entries written under a transaction
 * that aborts are discarded.
 *
 * @author Mikhail A. Markov
 */
public class TransactionAbortWriteTest extends TransactionTest {

    /**
     * This method asserts that entries written under a transaction
     * that aborts are discarded.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 3.1</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry result;
        Transaction txn;
        long leaseTime1 = timeout1;
        long leaseTime2 = timeout2;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create the non null transaction
        txn = getTransaction();

        /*
         * write 1-st entry with Lease.FOREVER lease time to the space
         * within the transaction
         */
        space.write(sampleEntry1, txn, Lease.FOREVER);

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry1 + " with Lease.FOREVER lease time"
                    + " is not available in the space"
                    + " within the transaction.");
        }
        logDebugText(sampleEntry1.toString() + " with Lease.FOREVER"
                + " lease time has been successfully"
                + " written to the space within the non null transaction.");

        /*
         * write 2-nd entry with Lease.ANY value for lease time
         * to the space within the transaction
         */
        space.write(sampleEntry2, txn, Lease.ANY);

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry2, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry2 + " with Lease.ANY value for lease time"
                    + " is not available in the space"
                    + " within the transaction.");
        }
        logDebugText(sampleEntry1.toString() + " with Lease.ANY value"
                + " for lease time has been successfully"
                + " written to the space within the non null transaction.");

        /*
         * write 1-st and 2-nd entries to the space within the
         * transaction again with finite lease times
         */
        space.write(sampleEntry1, txn, leaseTime1);
        logDebugText(sampleEntry1.toString() + " with " + leaseTime1
                + " has been successfully written again to the space"
                + " within the non null transaction.");
        space.write(sampleEntry2, txn, leaseTime2);
        logDebugText(sampleEntry2.toString() + " with " + leaseTime2
                + " has been successfully written again to the space"
                + " within the non null transaction.");

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
