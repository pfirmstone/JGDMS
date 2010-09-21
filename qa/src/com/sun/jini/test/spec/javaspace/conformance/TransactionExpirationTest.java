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
import net.jini.space.JavaSpace;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;


/**
 * TransactionExpirationTest asserts, that when the lease expires,
 * the entry is removed from the space within the transaction.
 *
 * @author Mikhail A. Markov
 */
public class TransactionExpirationTest extends AbstractTestBase {

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
     * This method asserts, that when the lease expires,
     * the entry is removed from the space within the transaction.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.3.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry result;
        long leaseTime1 = timeout1;
        long leaseTime2 = timeout2;
        Lease lease1 = null;
        Lease lease2 = null;
        Transaction txn;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create the non null transaction
        txn = getTransaction();

        /*
         * write two sample entries with different
         * finite lease times into the space within the transaction
         */
        lease1 = space.write(sampleEntry1, txn, leaseTime1);
        lease1 = prepareLease(lease1);
        lease2 = space.write(sampleEntry2, txn, leaseTime2);
        lease2 = prepareLease(lease2);

        // check that returned leases are not equal to null
        if (lease1 == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1 + " with "
                    + leaseTime1 + " lease time within the non null"
                    + " transaction returned null lease.");
        }

        if (lease2 == null) {
            throw new TestException(
                    "performed write of " + sampleEntry2 + " with "
                    + leaseTime2 + " lease time within the non null"
                    + " transaction returned null lease.");
        }

        /*
         * check that written entries are available in the space
         * within the transaction
         */
        result = (SimpleEntry) space.readIfExists(sampleEntry1, txn,
	        JavaSpace.NO_WAIT);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1 + " with "
                    + leaseTime1 + " lease time within the non null"
                    + " transaction, written entry is not available"
                    + " in the space.");
        }
        result = (SimpleEntry) space.readIfExists(sampleEntry2, txn,
	        JavaSpace.NO_WAIT);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry2 + " with "
                    + leaseTime2 + " lease time within the non null"
                    + " transaction, written entry is not available"
                    + " in the space.");
        }
        logDebugText(sampleEntry1.toString() + " with " + leaseTime1
                + " lease time and " + sampleEntry2 + " with " + leaseTime2
                + " lease time has been successfully written to the space"
                + " within the non null transaction.");

        // sleep to let 1-st lease expires
        logDebugText("Sleeping for " + (leaseTime1 + instantTime) + " ...");
        Thread.sleep(leaseTime1 + instantTime);
        logDebugText("awakening...");

        /*
         * check that 1-st entry is not available in the space
         * within the transaction
         */
        result = (SimpleEntry) space.readIfExists(sampleEntry1, txn,
	        JavaSpace.NO_WAIT);

        if (result != null) {
            throw new TestException(
                    "performed write within the non null transaction of "
                    + sampleEntry1 + " with " + leaseTime1
                    + " lease time still available in the"
                    + " space after expiration time.");
        }
        logDebugText("Expiration for written " + sampleEntry1 + " with "
                + leaseTime1 + " lease time within the non null transaction"
                + " works as expected.");

        /*
         * check that 2-nd entry is still available in the space
         * within the transaction
         */
        result = (SimpleEntry) space.readIfExists(sampleEntry2, txn,
	        JavaSpace.NO_WAIT);

        if (result == null) {
            throw new TestException(
                    "performed write within the non null transaction of "
                    + sampleEntry2 + " with " + leaseTime2
                    + " lease time is not available in the space after "
                    + leaseTime1 + " ms.");
        }

        // sleep to let 2-nd lease expires
        logDebugText("Sleeping for " + (leaseTime2 - leaseTime1) + " ...");
        Thread.sleep(leaseTime2 - leaseTime1);
        logDebugText("awakening...");

        /*
         * check that 2-nd entry is not available in the space
         * within the transaction
         */
        result = (SimpleEntry) space.readIfExists(sampleEntry1, null,
	        JavaSpace.NO_WAIT);

        if (result != null) {
            throw new TestException(
                    "performed write within the non null transaction of "
                    + sampleEntry2 + " with " + leaseTime2
                    + " lease time still available in the"
                    + " space after expiration time.");
        }
        logDebugText("Expiration for written " + sampleEntry2 + " with "
                + leaseTime2 + " lease time within the non null transaction"
                + " works as expected.");

        /*
         * write 1-st entry twice with different finite lease times
         * within the non null transaction
         */
        lease1 = space.write(sampleEntry1, txn, leaseTime1);
        lease1 = prepareLease(lease1);
        lease2 = space.write(sampleEntry1, txn, leaseTime2);
        lease2 = prepareLease(lease2);

        // check that returned leases are not equal to null
        if (lease1 == null) {
            throw new TestException(
                    "performed write within the non null transaction of "
                    + sampleEntry1 + " with " + leaseTime1
                    + " lease time returned null lease.");
        }

        if (lease2 == null) {
            throw new TestException(
                    "performed write within the non null transaction of "
                    + sampleEntry1 + " with " + leaseTime2
                    + " lease time returned null lease.");
        }
        logDebugText(sampleEntry1.toString() + " with " + leaseTime1
                + " and " + leaseTime2 + " lease times within the non null"
                + " transaction have been successfully written"
                + " to the space.");

        // sleep to let 1-st lease expires
        logDebugText("Sleeping for " + (leaseTime1 + instantTime) + " ...");
        Thread.sleep(leaseTime1 + instantTime);
        logDebugText("awakening...");

        /*
         * check that only one of the 1-st entries
         * is available in the space within the transaction
         */
        result = (SimpleEntry) space.takeIfExists(sampleEntry1, txn,
	        JavaSpace.NO_WAIT);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + " with " + leaseTime1 + " and "
                    + leaseTime2 + " lease times"
                    + ", there are no entries available in the space"
                    + " after " + leaseTime1 + " ms, while 1 is expected.");
        }
        result = (SimpleEntry) space.takeIfExists(sampleEntry1, txn,
	        JavaSpace.NO_WAIT);

        if (result != null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + " with " + leaseTime1 + " and "
                    + leaseTime2 + " lease times"
                    + ", there are 2 entries available in the space"
                    + " after " + leaseTime1 + " ms, while 1 is expected.");
        }
        logDebugText("Expiration for written " + sampleEntry1 + " with "
                + leaseTime1 + " lease time within the non null transaction"
                + " works as expected.");

        /*
         * write 1-st entry twice with different finite lease times
         * within the non null transaction again
         */
        lease1 = space.write(sampleEntry1, txn, leaseTime1);
        lease1 = prepareLease(lease1);
        lease2 = space.write(sampleEntry1, txn, leaseTime2);
        lease2 = prepareLease(lease2);

        // check that returned leases are not equal to null
        if (lease1 == null) {
            throw new TestException(
                    "performed write within the non null transaction of "
                    + sampleEntry1 + " with " + leaseTime1
                    + " lease time returned null lease.");
        }

        if (lease2 == null) {
            throw new TestException(
                    "performed write within the non null transaction of "
                    + sampleEntry1 + " with " + leaseTime2
                    + " lease time returned null lease.");
        }
        logDebugText(sampleEntry1.toString() + " with " + leaseTime1
                + " and " + leaseTime2 + " lease times within the non null"
                + " transaction have been successfully written"
                + " to the space.");

        // sleep to let 2-nd lease expires
        logDebugText("Sleeping for " + (leaseTime2 + instantTime) + " ...");
        Thread.sleep(leaseTime2 + instantTime);
        logDebugText("awakening...");

        /*
         * check that there are no entries available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.readIfExists(sampleEntry1, txn,
	        JavaSpace.NO_WAIT);

        if (result != null) {
            throw new TestException(
                    "performed 2 writes within the non null transaction of "
                    + sampleEntry1 + " with " + leaseTime1 + " and "
                    + leaseTime2 + " lease times"
                    + ", there are at lease 1 entry still available in"
                    + " the space after " + leaseTime2
                    + " ms, while 0 is expected.");
        }
        logDebugText("Expiration for written " + sampleEntry1 + " with "
                + leaseTime1 + " and " + leaseTime2
                + " lease times within the non null transaction"
                + " works as expected.");

        // commit the transaction
        txnCommit(txn);
    }
}
