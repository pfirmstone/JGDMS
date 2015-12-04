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
import net.jini.core.event.EventRegistration;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;


/**
 * NotifyNullTxnParamTest asserts, that:
 * 1) If the transaction parameter is null, the listener will be notified when
 *    matching entries are written either under a null transaction or when
 *    a transaction commits.
 * 2) When a transaction commits, any entries that were written under the
 *    transaction (and not taken) will cause appropriate notifications for
 *    registrations that were made under a null transaction.
 *
 * @author Mikhail A. Markov
 */
public class NotifyNullTxnParamTest extends TransactionTest {

    /**
     * This method asserts, that:
     * 1) If the transaction parameter is null, the listener will be notified
     *    when matching entries are written either under a null transaction
     *    or when a transaction commits.
     * 2) When a transaction commits, any entries that were written under the
     *    transaction (and not taken) will cause appropriate notifications for
     *    registrations that were made under a null transaction.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.7.</P>
     */
    public void run() throws Exception {
        NotifyCounter[] ncs = new NotifyCounter[12];
        EventRegistration[] ers = new EventRegistration[12];
        boolean[] failMatrix = new boolean[12];
        boolean failed = false;
        long[] evMatrix = new long[] {
            3, 3, 3, 0, 0, 0, 0, 0, 9, 6, 6, 9 };
        long[] evTxnMatrix = new long[] {
            2, 2, 2, 0, 0, 0, 0, 0, 6, 4, 4, 6 };
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);
        SimpleEntry template;
        Transaction txn;
        long leaseTime1 = timeout2 * 3;
        long leaseTime2 = Lease.FOREVER;
        long leaseTime3 = Lease.ANY;
        int i;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create the non null transaction
        txn = getTransaction();

        // init 3 RemoteEvent counters for each of sample entries
        ncs[0] = new NotifyCounter(sampleEntry1, leaseTime1);
        ncs[1] = new NotifyCounter(sampleEntry2, leaseTime2);
        ncs[2] = new NotifyCounter(sampleEntry3, leaseTime3);

        // init 5 counters with wrong templates
        template = new SimpleEntry("TestEntry #3", 1);
        ncs[3] = new NotifyCounter(template, leaseTime1);

        // 2-nd wrong template
        template = new SimpleEntry("TestEntry #1", 3);
        ncs[4] = new NotifyCounter(template, leaseTime2);

        // 3-rd wrong template
        template = new SimpleEntry("TestEntry #3", 3);
        ncs[5] = new NotifyCounter(template, leaseTime3);

        // 4-th wrong template
        template = new SimpleEntry(null, 3);
        ncs[6] = new NotifyCounter(template, leaseForeverTime);

        // 5-th wrong template
        template = new SimpleEntry("TestEntry #3", null);
        ncs[7] = new NotifyCounter(template, leaseTime1);

        // init counter with null entry as a template
        ncs[8] = new NotifyCounter(null, leaseForeverTime);

        // init 3 counters with null values for different fields
        template = new SimpleEntry("TestEntry #1", null);
        ncs[9] = new NotifyCounter(template, leaseTime1);

        // 2-nd template
        template = new SimpleEntry(null, 2);
        ncs[10] = new NotifyCounter(template, leaseTime2);

        // 3-rd template
        template = new SimpleEntry(null, null);
        ncs[11] = new NotifyCounter(template, leaseTime3);

        // now register all counters with null transaction parameter
        for (i = 0; i < 12; i++) {
            ers[i] = space.notify(ncs[i].getTemplate(), null, ncs[i],
                    ncs[i].getLeaseTime(), null);
	    ers[i] = prepareRegistration(ers[i]);
        }

        // sleep for a while to let all listeners register properly
        logDebugText("now sleeping for " + timeout1
                + " to let all listeners register properly.");
        Thread.sleep(timeout1);

        // write 3 sample entries 3 times to the space without transactions
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        logDebugText("3 sample entries have been written"
                + " to the space 3 times without transactions.");

        // write 3 sample entries twice to the space within the transaction
        space.write(sampleEntry1, txn, leaseForeverTime);
        space.write(sampleEntry1, txn, leaseForeverTime);
        space.write(sampleEntry2, txn, leaseForeverTime);
        space.write(sampleEntry2, txn, leaseForeverTime);
        space.write(sampleEntry3, txn, leaseForeverTime);
        space.write(sampleEntry3, txn, leaseForeverTime);
        logDebugText("3 sample entries have been written"
                + " to the space twice within the non null transaction.");

        // wait for a while to let all listeners get notifications
        logDebugText("now sleeping for " + timeout1
                + " to let all listeners get notifications.");
        Thread.sleep(timeout1);

        /*
         * check, that listeners got required number of notifications
         * before transaction's committing
         */
        for (i = 0; i < 12; i++) {
            if (ncs[i].getEventsNum(ers[i]) != evMatrix[i]) {
                failed = true;
                failMatrix[i] = true;
            } else {
                failMatrix[i] = false;
            }
        }

        for (i = 0; i < 12; i++) {
            if (failMatrix[i]) {
                logDebugText("FAILED: " + ncs[i] + " has got "
                        + ncs[i].getEventsNum(ers[i])
                        + " notifications instead of " + evMatrix[i]
                        + " required before transaction's committing.");
            } else {
                logDebugText(ncs[i].toString() + " has got "
                        + ncs[i].getEventsNum(ers[i])
                        + " notifications as expected"
                        + " before transaction's committing.");
            }
        }

        // commit the transaction
        txnCommit(txn);
        logDebugText("Transaction has been committed.");

        // wait for a while to let all listeners get notifications
        logDebugText("now sleeping for " + timeout2
                + " to let all listeners get notifications.");
        Thread.sleep(timeout2);

        /*
         * check, that listeners got required number of notifications
         * after transaction's committing
         */
        long evNum;

        for (i = 0; i < 12; i++) {
            evNum = evMatrix[i] + evTxnMatrix[i];

            if (ncs[i].getEventsNum(ers[i]) != evNum) {
                failed = true;
                failMatrix[i] = true;
            } else {
                failMatrix[i] = false;
            }
        }

        for (i = 0; i < 12; i++) {
            evNum = evMatrix[i] + evTxnMatrix[i];

            if (failMatrix[i]) {
                logDebugText("FAILED: " + ncs[i] + " has got "
                        + ncs[i].getEventsNum(ers[i])
                        + " notifications instead of " + evNum
                        + " required before transaction's committing.");
            } else {
                logDebugText(ncs[i].toString() + " has got "
                        + ncs[i].getEventsNum(ers[i])
                        + " notifications as expected"
                        + " before transaction's committing.");
            }
        }

        // check: we fail of pass
        if (failed) {
            throw new TestException(
                    "Not all listeners've got expected number of events.");
        }
    }
}
