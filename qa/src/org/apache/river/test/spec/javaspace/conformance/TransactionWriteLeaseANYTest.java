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
package org.apache.river.test.spec.javaspace.conformance;

import java.util.logging.Level;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

/**
 * TransactionWriteLeaseANYTest asserts that write with <code>Lease.ANY</code>
 * value for lease time within the non null transaction:
 * 1) Places a copy of an entry into the given JavaSpaces service,
 *    even if the same Entry object is used in more than one write.
 * 2) IllegalArgumentException will not be thrown while this operations.
 * 3) The Entry passed to the write is not affected by this operations.
 * 4) If a write returns without throwing an exception, that entry is
 *    committed to the space, possibly within a transaction.
 *
 * @author Mikhail A. Markov
 */
public class TransactionWriteLeaseANYTest extends TransactionTest {

    /**
     * This method asserts that write with <code>Lease.ANY</code>
     * value for lease time within the non null transaction:
     * 1) Places a copy of an entry into the given JavaSpaces service,
     *    even if the same Entry object is used in more than one write.
     * 2) IllegalArgumentException will not be thrown while this operations.
     * 3) The Entry passed to the write is not affected by this operations.
     * 4) If a write returns without throwing an exception, that entry is
     *    committed to the space, possibly within a transaction.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.3.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry origEntry1;
        SimpleEntry origEntry2;
        SimpleEntry result;
        Transaction txn;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // init original entries for comparing
        origEntry1 = (SimpleEntry) sampleEntry1.clone();
        origEntry2 = (SimpleEntry) sampleEntry2.clone();

        // create the non null transaction
        txn = getTransaction();

        /*
         * write 1-st entry with Lease.ANY value for lease time
         * within the non null transaction
         */
        try {
            space.write(sampleEntry1, txn, Lease.ANY);
        } catch (IllegalArgumentException iae) {
            throw new TestException(
                    "IllegalArgumentException was thrown while trying to"
                    + " write " + sampleEntry1
                    + " with Lease.ANY value for lease time"
                    + " within the non null transaction.");
        }

        // check that original entry has not been changed
        if (!origEntry1.equals(sampleEntry1)) {
            throw new TestException(
                    "performed write operation with Lease.ANY value"
                    + " for lease time within the non null transaction"
                    + " has changed entry object: original entry "
                    + origEntry1 + " has been changed by " + sampleEntry1);
        }

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1
                    + " with Lease.ANY value for lease time"
                    + " within the non null transaction, written entry"
                    + " is not available in the space.");
        }
        logDebugText("Write of " + sampleEntry1
                + " with Lease.ANY lease time within the non null"
                + " transaction works as expected.");

        /*
         * write 2-nd entry with Lease.ANY value for lease time
         * within the non null transaction
         */
        try {
            space.write(sampleEntry2, txn, Lease.ANY);
        } catch (IllegalArgumentException iae) {
            throw new TestException(
                    "IllegalArgumentException was thrown while trying to"
                    + " write " + sampleEntry2
                    + " with Lease.ANY value for lease time"
                    + " within the non null transaction.");
        }

        // check that original entry has not been changed
        if (!origEntry2.equals(sampleEntry2)) {
            throw new TestException(
                    "performed write operation with Lease.ANY value"
                    + " for lease time within the non null transaction"
                    + " has changed entry object: original entry "
                    + origEntry2 + " has been changed by " + sampleEntry2);
        }

        /*
         * check that written entry is available
         * in the space within the transaction
         */
        result = (SimpleEntry) space.read(sampleEntry2, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry2
                    + " with Lease.ANY value for lease time"
                    + " within the non null transaction, written entry"
                    + " is not available in the space.");
        }
        logDebugText("Write of " + sampleEntry2
                + " with Lease.ANY lease time within the non null"
                + " transaction works as expected.");

        /*
         * write 1-st entry with Lease.ANY value for lease time again
         * within the same non null transaction
         */
        try {
            space.write(sampleEntry1, txn, Lease.ANY);
        } catch (IllegalArgumentException iae) {
            throw new TestException(
                    "IllegalArgumentException was thrown while trying to"
                    + " write " + sampleEntry1
                    + " with Lease.ANY value for lease time"
                    + " within the non null transaction again.");
        }

        // check that original entry has not been changed
        if (!origEntry1.equals(sampleEntry1)) {
            throw new TestException(
                    "performed 2-nd write operation for the same entry"
                    + " with Lease.ANY value for lease time"
                    + " within the non null transaction has changed"
                    + " entry object: original entry " + origEntry1
                    + " has been changed by " + sampleEntry1);
        }

        // check that written entries are available in the space
        result = (SimpleEntry) space.take(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2-nd write operation for the same "
                    + sampleEntry1 + " with Lease.ANY value for lease time"
                    + " within the non null transaction, 1-st written"
                    + " entry is not available in the space.");
        }
        result = (SimpleEntry) space.take(sampleEntry1, txn, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2-nd write operation for the same "
                    + sampleEntry1 + " with Lease.ANY value for lease time"
                    + " within the non null transaction, 2-nd written"
                    + " entry is not available in the space.");
        }
        logDebugText("2-nd write of the same " + sampleEntry1
                + " with Lease.ANY lease time within the same"
                + " non null transaction works as expected.");

        // commit the transaction
        txnCommit(txn);
    }
}
