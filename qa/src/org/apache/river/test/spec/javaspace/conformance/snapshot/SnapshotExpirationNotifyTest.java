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

import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;
import java.util.logging.Level;

// net.jini
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;
import net.jini.core.event.EventRegistration;

// org.apache.river
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa
import org.apache.river.test.spec.javaspace.conformance.SimpleEntry;
import org.apache.river.test.spec.javaspace.conformance.NotifyCounter;
import org.apache.river.test.spec.javaspace.conformance.TransactionTest;


/**
 * SnapshotExpirationNotifyTest asserts, that the request specified by a
 * successful notify is as persistent as the entries of the space. They will be
 * remembered as long as an un-taken entry would be, until the lease expires,
 * or until any governing transaction completes, whichever is shorter.
 *
 * It tests this statement for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotExpirationNotifyTest extends TransactionTest {

    /**
     * This method asserts, that the request specified by a
     * successful notify is as persistent as the entries of the space.
     * They will be remembered as long as an un-taken entry would be,
     * until the lease expires, or until any governing transaction completes,
     * whichever is shorter.
     *
     * It tests this statement for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.6, 2.7.</P>
     */
    public void run() throws Exception {
        NotifyCounter[] ncs = new NotifyCounter[10];
        SnapshotNotifyCounter[] snsh_ncs = new SnapshotNotifyCounter[10];
        EventRegistration[] ers = new EventRegistration[10];
        EventRegistration[] snsh_ers = new EventRegistration[10];
        boolean[] failMatrix = new boolean[10];
        boolean[] snsh_failMatrix = new boolean[10];
        boolean failed = false;
        long[] evTxnMatrix = new long[] {
            2, 6, 8, 10, 10, 2, 4, 4, 4, 4 };
        SimpleEntry sampleEntry = new SimpleEntry("TestEntry #1", 1);
        Entry snapshot;
        Transaction txn;
        long leaseTime1 = timeout2;
        long leaseTime2 = timeout2 * 2;
        long leaseTime3 = timeout2 * 3;
        long txnLeaseTime = leaseTime1 + timeout1;
        int i;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // snapshot sample entry
        snapshot = space.snapshot(sampleEntry);

        /*
         * init 5 RemoteEvent counters with different lease times
         * and snapshot to use with null transaction parameter
         */
        snsh_ncs[0] = new SnapshotNotifyCounter(sampleEntry, leaseTime1,
                space);
        snsh_ncs[1] = new SnapshotNotifyCounter(sampleEntry, leaseTime2,
                space);
        snsh_ncs[2] = new SnapshotNotifyCounter(sampleEntry, leaseTime3,
                space);
        snsh_ncs[3] = new SnapshotNotifyCounter(sampleEntry,
                leaseForeverTime, space);
        snsh_ncs[4] = new SnapshotNotifyCounter(sampleEntry, Lease.FOREVER,
                space);

        /*
         * init 5 RemoteEvent counters with different lease times
         * and snapshot to use with non null transaction parameter
         */
        snsh_ncs[5] = new SnapshotNotifyCounter(sampleEntry, leaseTime1,
                space);
        snsh_ncs[6] = new SnapshotNotifyCounter(sampleEntry, leaseTime2,
                space);
        snsh_ncs[7] = new SnapshotNotifyCounter(sampleEntry, leaseTime3,
                space);
        snsh_ncs[8] = new SnapshotNotifyCounter(sampleEntry,
                leaseForeverTime, space);
        snsh_ncs[9] = new SnapshotNotifyCounter(sampleEntry, Lease.FOREVER,
                space);

        // now register all counters with null transaction parameter
        for (i = 0; i < 5; i++) {
            snsh_ers[i] = space.notify(snsh_ncs[i].getSnapshot(), null,
                    snsh_ncs[i], snsh_ncs[i].getLeaseTime(), null);
        }

        // create the non null transaction with finite lease time
        txn = getTransaction(txnLeaseTime);

        // now register all counters with non null transaction parameter
        for (i = 5; i < 10; i++) {
            snsh_ers[i] = space.notify(snsh_ncs[i].getSnapshot(), txn,
                    snsh_ncs[i], snsh_ncs[i].getLeaseTime(), null);
        }

        // write sample entry twice
        space.write(sampleEntry, null, leaseForeverTime);
        space.write(sampleEntry, null, leaseForeverTime);

        // sleep for a while to let some listeners expire
        Thread.sleep(timeout2 + instantTime);
        logDebugText("now sleeping for " + (timeout2 + instantTime)
                + " to to let some listeners expire...");

        // write sample entry twice again
        space.write(sampleEntry, null, leaseForeverTime);
        space.write(sampleEntry, null, leaseForeverTime);

        // sleep again for a while to let transaction expires
        Thread.sleep(timeout1);
        logDebugText("now sleeping for " + timeout1
                + " to let to let transaction expires...");

        // write sample entry twice again
        space.write(sampleEntry, null, leaseForeverTime);
        space.write(sampleEntry, null, leaseForeverTime);

        // sleep again for a while to let some listeners expire
        Thread.sleep(timeout2 - timeout1);
        logDebugText("now sleeping for " + (timeout2 - timeout1)
                + " to to let some listeners expire...");

        // write sample entry twice again
        space.write(sampleEntry, null, leaseForeverTime);
        space.write(sampleEntry, null, leaseForeverTime);

        // sleep again for a while to let some listeners expire
        Thread.sleep(timeout2);
        logDebugText("now sleeping for " + timeout2
                + " to to let some listeners expire...");

        // write sample entry twice again
        space.write(sampleEntry, null, leaseForeverTime);
        space.write(sampleEntry, null, leaseForeverTime);

        // wait for a while to let all listeners get notifications
        logDebugText("now sleeping for " + timeout2
                + " to let all listeners get notifications.");
        Thread.sleep(timeout2);

        // check, that listeners got required number of notifications
        for (i = 0; i < 10; i++) {
            if (snsh_ncs[i].getEventsNum(snsh_ers[i]) != evTxnMatrix[i]) {
                failed = true;
                snsh_failMatrix[i] = true;
            } else {
                snsh_failMatrix[i] = false;
            }
        }

        for (i = 0; i < 10; i++) {
            if (snsh_failMatrix[i]) {
                logDebugText("FAILED: " + snsh_ncs[i] + " has got "
                        + snsh_ncs[i].getEventsNum(snsh_ers[i])
                        + " notifications instead of " + evTxnMatrix[i]
                        + " required.");
            } else {
                logDebugText(snsh_ncs[i].toString() + " has got "
                        + snsh_ncs[i].getEventsNum(snsh_ers[i])
                        + " notifications as expected");
            }
        }
        logDebugText("Stage 1 with writing ordinal entries has been"
                + " completed.");
        logDebugText("------------------------------\n");
        logDebugText("Starting 2-nd stage with snapshots.");

        // clean the space
        cleanSpace(space);

        /*
         * init 5 RemoteEvent counters with different lease times
         * to use with null transaction parameter
         */
        ncs[0] = new NotifyCounter(sampleEntry, leaseTime1);
        ncs[1] = new NotifyCounter(sampleEntry, leaseTime2);
        ncs[2] = new NotifyCounter(sampleEntry, leaseTime3);
        ncs[3] = new NotifyCounter(sampleEntry, leaseForeverTime);
        ncs[4] = new NotifyCounter(sampleEntry, Lease.FOREVER);

        /*
         * init 5 RemoteEvent counters with different lease times
         * and snapshot to use with null transaction parameter
         */
        snsh_ncs[0] = new SnapshotNotifyCounter(sampleEntry, leaseTime1,
                space);
        snsh_ncs[1] = new SnapshotNotifyCounter(sampleEntry, leaseTime2,
                space);
        snsh_ncs[2] = new SnapshotNotifyCounter(sampleEntry, leaseTime3,
                space);
        snsh_ncs[3] = new SnapshotNotifyCounter(sampleEntry,
                leaseForeverTime, space);
        snsh_ncs[4] = new SnapshotNotifyCounter(sampleEntry, Lease.FOREVER,
                space);

        /*
         * init 5 RemoteEvent counters with different lease times
         * to use with non null transaction parameter
         */
        ncs[5] = new NotifyCounter(sampleEntry, leaseTime1);
        ncs[6] = new NotifyCounter(sampleEntry, leaseTime2);
        ncs[7] = new NotifyCounter(sampleEntry, leaseTime3);
        ncs[8] = new NotifyCounter(sampleEntry, leaseForeverTime);
        ncs[9] = new NotifyCounter(sampleEntry, Lease.FOREVER);

        /*
         * init 5 RemoteEvent counters with different lease times
         * and snapshot to use with non null transaction parameter
         */
        snsh_ncs[5] = new SnapshotNotifyCounter(sampleEntry, leaseTime1,
                space);
        snsh_ncs[6] = new SnapshotNotifyCounter(sampleEntry, leaseTime2,
                space);
        snsh_ncs[7] = new SnapshotNotifyCounter(sampleEntry, leaseTime3,
                space);
        snsh_ncs[8] = new SnapshotNotifyCounter(sampleEntry,
                leaseForeverTime, space);
        snsh_ncs[9] = new SnapshotNotifyCounter(sampleEntry, Lease.FOREVER,
                space);

        // now register all counters with null transaction parameter
        for (i = 0; i < 5; i++) {
            ers[i] = space.notify(ncs[i].getTemplate(), null, ncs[i],
                    ncs[i].getLeaseTime(), null);
            snsh_ers[i] = space.notify(snsh_ncs[i].getSnapshot(), null,
                    snsh_ncs[i], snsh_ncs[i].getLeaseTime(), null);
        }

        // create the non null transaction with finite lease time
        txn = getTransaction(txnLeaseTime);

        // now register all counters with non null transaction parameter
        for (i = 5; i < 10; i++) {
            ers[i] = space.notify(ncs[i].getTemplate(), txn, ncs[i],
                    ncs[i].getLeaseTime(), null);
            snsh_ers[i] = space.notify(snsh_ncs[i].getSnapshot(), txn,
                    snsh_ncs[i], snsh_ncs[i].getLeaseTime(), null);
        }

        // write sample entry using it's snapshot twice
        space.write(snapshot, null, leaseForeverTime);
        space.write(snapshot, null, leaseForeverTime);

        // sleep for a while to let some listeners expire
        Thread.sleep(timeout2 + instantTime);
        logDebugText("now sleeping for " + (timeout2 + instantTime)
                + " to to let some listeners expire...");

        // write sample entry using it's snapshot twice again
        space.write(snapshot, null, leaseForeverTime);
        space.write(snapshot, null, leaseForeverTime);

        // sleep again for a while to let transaction expires
        Thread.sleep(timeout1);
        logDebugText("now sleeping for " + timeout1
                + " to let to let transaction expires...");

        // write sample entry using it's snapshot twice again
        space.write(snapshot, null, leaseForeverTime);
        space.write(snapshot, null, leaseForeverTime);

        // sleep again for a while to let some listeners expire
        Thread.sleep(timeout2 - timeout1);
        logDebugText("now sleeping for " + (timeout2 - timeout1)
                + " to to let some listeners expire...");

        // write sample entry using it's snapshot twice again
        space.write(snapshot, null, leaseForeverTime);
        space.write(snapshot, null, leaseForeverTime);

        // sleep again for a while to let some listeners expire
        Thread.sleep(timeout2);
        logDebugText("now sleeping for " + timeout2
                + " to to let some listeners expire...");

        // write sample entry using it's snapshot twice again
        space.write(snapshot, null, leaseForeverTime);
        space.write(snapshot, null, leaseForeverTime);

        // wait for a while to let all listeners get notifications
        logDebugText("now sleeping for " + timeout2
                + " to let all listeners get notifications.");
        Thread.sleep(timeout2);

        // check, that listeners got required number of notifications
        for (i = 0; i < 10; i++) {
            if (ncs[i].getEventsNum(ers[i]) != evTxnMatrix[i]) {
                failed = true;
                snsh_failMatrix[i] = true;
            } else {
                snsh_failMatrix[i] = false;
            }

            if (snsh_ncs[i].getEventsNum(snsh_ers[i]) != evTxnMatrix[i]) {
                failed = true;
                failMatrix[i] = true;
            } else {
                failMatrix[i] = false;
            }
        }

        for (i = 0; i < 10; i++) {
            if (failMatrix[i]) {
                logDebugText("FAILED: " + ncs[i] + " has got "
                        + ncs[i].getEventsNum(ers[i])
                        + " notifications instead of " + evTxnMatrix[i]
                        + " required.");
            } else {
                logDebugText(ncs[i].toString() + " has got "
                        + ncs[i].getEventsNum(ers[i])
                        + " notifications as expected");
            }

            if (snsh_failMatrix[i]) {
                logDebugText("FAILED: " + snsh_ncs[i] + " has got "
                        + snsh_ncs[i].getEventsNum(snsh_ers[i])
                        + " notifications instead of " + evTxnMatrix[i]
                        + " required.");
            } else {
                logDebugText(snsh_ncs[i].toString() + " has got "
                        + snsh_ncs[i].getEventsNum(snsh_ers[i])
                        + " notifications as expected");
            }
        }
        logDebugText("Stage 2 with writing snapshots has been"
                + " completed.\n");

        // check: we fail of pass
        if (failed) {
            throw new TestException(
                    "Not all listeners've got expected number of events.");
        }
    }
}
