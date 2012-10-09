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


/**
 * TransactionCommitWriteTest asserts that an entry that is written under the
 * non null transaction is not visible outside its transaction until the
 * transaction successfully commits.
 *
 * 
 */
public class TransactionCommitWriteTest extends AbstractTestBase {

    /**
     * Sets up the testing environment.
     *
     * @param config QAConfig from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {

        // mandatory call to parent
        super.setup(config);

        // get an instance of Transaction Manager
        mgr = getTxnManager();
    }

    /**
     * This method asserts that an entry that is written under the
     * non null transaction is not visible outside its transaction until the
     * transaction successfully commits.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 3.1</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry result;
        Transaction txn;
        long leaseTime = timeout2;

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

        /*
         * check that written entry is not available
         * outside the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry1 + " with Lease.FOREVER lease time"
                    + " is visible in the space outside the transaction.");
        }
        logDebugText("Written within the non null transaction "
                + sampleEntry1 + " with Lease.FOREVER lease time"
                + " is actually available inside the"
                + " transaction and not visible outside it.");

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

        /*
         * check that written entry is not available
         * outside the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry2, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry2 + " with Lease.ANY value for lease time"
                    + " is visible in the space outside the transaction.");
        }
        logDebugText("Written within the non null transaction "
                + sampleEntry2 + " with Lease.ANY value for lease time"
                + " is actually available inside the"
                + " transaction and not visible outside it.");

        /*
         * write 1-st entry to the space within the transaction again
         * with finite lease time
         */
        space.write(sampleEntry1, txn, leaseTime);

        /*
         * check that written entry is not available
         * outside the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry1 + " with " + leaseTime + " lease time"
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
                    + sampleEntry1 + ", there are no entries are available"
                    + " in the space within the transaction"
                    + " while 2 are expected");
        }
        result = (SimpleEntry) space.take(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + ", there is only 1 entry available"
                    + " in the space within the transaction"
                    + " while 2 are expected");
        }
        logDebugText("Written within the non null transaction "
                + sampleEntry1 + " with " + leaseTime + " lease time"
                + " is actually available inside the"
                + " transaction and not visible outside it.");

        // write 1-st entry to the space within the transaction twice
        space.write(sampleEntry1, txn, Lease.FOREVER);
        space.write(sampleEntry1, txn, leaseTime);

        // commit the transaction
        txnCommit(txn);

        // check that 2-nd sample entry is available in the space
        result = (SimpleEntry) space.read(sampleEntry2, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "written within the non null transaction "
                    + sampleEntry2 + " is not available in the space"
                    + " after transaction's committing.");
        }

        // check that both 1-st entries are available in the space
        result = (SimpleEntry) space.take(sampleEntry1, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + ", there are no entries are available"
                    + " in the space after transaction's committing"
                    + " while 2 are expected");
        }
        result = (SimpleEntry) space.take(sampleEntry1, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + ", there is only 1 entry available"
                    + " in the space after transaction's committing"
                    + " while 2 are expected");
        }
        logDebugText("All written entries are available in the space"
                + " after transaction's committing.");
    }
}
