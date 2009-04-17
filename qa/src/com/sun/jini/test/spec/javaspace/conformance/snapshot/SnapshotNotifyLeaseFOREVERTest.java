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
import net.jini.core.lease.Lease;
import net.jini.core.event.EventRegistration;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;
import com.sun.jini.test.spec.javaspace.conformance.NotifyCounter;


/**
 * SnapshotNotifyLeaseFOREVERTest asserts, that for notify with
 * <code>Lease.FOREVER</code> lease time:
 * 1) A notify request's matching is done as it is for read
 * 2) Writing an entry into a space might generate notifications
 *    to registered objects.
 * 3) When matching entries arrive, the specified RemoteEventListener will
 *    eventually be notified by invoking listener's notify method.
 *
 * It tests these statements for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotNotifyLeaseFOREVERTest extends SnapshotAbstractTestBase {

    /**
     * This method asserts, that for notify with <code>Lease.FOREVER</code>
     * lease time:
     * 1) A notify request's matching is done as it is for read
     * 2) Writing an entry into a space might generate notifications
     *    to registered objects.
     * 3) When matching entries arrive, the specified RemoteEventListener will
     *    eventually be notified by invoking listener's notify method.
     *
     * It tests these statements for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.6, 2.7.</P>
     */
    public void run() throws Exception {
        NotifyCounter[] ncs = new NotifyCounter[12];
        SnapshotNotifyCounter[] snsh_ncs = new SnapshotNotifyCounter[12];
        EventRegistration[] ers = new EventRegistration[12];
        EventRegistration[] snsh_ers = new EventRegistration[12];
        boolean[] failMatrix = new boolean[12];
        boolean[] snsh_failMatrix = new boolean[12];
        boolean failed = false;
        long[] evMatrix = new long[] {
            3, 3, 3, 0, 0, 0, 0, 0, 9, 6, 6, 9 };
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);
        Entry snapshot1;
        Entry snapshot2;
        Entry snapshot3;
        SimpleEntry template;
        int i;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create snapshots of sample entries
        snapshot1 = space.snapshot(sampleEntry1);
        snapshot2 = space.snapshot(sampleEntry2);
        snapshot3 = space.snapshot(sampleEntry3);

        /*
         * init 3 RemoteEvent counters for each of sample entries
         * and their snapshots
         */
        snsh_ncs[0] = new SnapshotNotifyCounter(sampleEntry1, Lease.FOREVER,
                space);
        snsh_ncs[1] = new SnapshotNotifyCounter(sampleEntry2, Lease.FOREVER,
                space);
        snsh_ncs[2] = new SnapshotNotifyCounter(sampleEntry3, Lease.FOREVER,
                space);

        // init 5 counters with wrong templates and their snapshots
        template = new SimpleEntry("TestEntry #3", 1);
        snsh_ncs[3] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 2-nd wrong template and it's snapshot
        template = new SimpleEntry("TestEntry #1", 3);
        snsh_ncs[4] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 3-rd wrong template and it's snapshot
        template = new SimpleEntry("TestEntry #3", 3);
        snsh_ncs[5] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 4-th wrong template and it's snapshot
        template = new SimpleEntry(null, 3);
        snsh_ncs[6] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 5-th wrong template and it's snapshot
        template = new SimpleEntry("TestEntry #3", null);
        snsh_ncs[7] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // init counter with null entry as a template and it's snapshot
        snsh_ncs[8] = new SnapshotNotifyCounter(null, Lease.FOREVER, space);

        /*
         * init 3 counters for templates with null values for different
         * fields and their snapshots
         */
        template = new SimpleEntry("TestEntry #1", null);
        snsh_ncs[9] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 2-nd template and it's snapshot
        template = new SimpleEntry(null, 2);
        snsh_ncs[10] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 3-rd template and it's snapshot
        template = new SimpleEntry(null, null);
        snsh_ncs[11] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // now register all counters
        for (i = 0; i < 12; i++) {
            snsh_ers[i] = space.notify(snsh_ncs[i].getSnapshot(), null,
                    snsh_ncs[i], snsh_ncs[i].getLeaseTime(), null);
        }

        // sleep for a while to let all listeners register properly
        Thread.sleep(timeout1);
        logDebugText("now sleeping for " + timeout1
                + " to let all listeners register properly.");

        // write 3 sample entries to the space 3 times
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
                + " to the space 3 times.");

        // wait for a while to let all listeners get notifications
        logDebugText("now sleeping for " + timeout1
                + " to let all listeners get notifications.");
        Thread.sleep(timeout1);

        // check, that listeners got required number of notifications
        for (i = 0; i < 12; i++) {
            if (snsh_ncs[i].getEventsNum(snsh_ers[i]) != evMatrix[i]) {
                failed = true;
                snsh_failMatrix[i] = true;
            } else {
                snsh_failMatrix[i] = false;
            }
        }

        for (i = 0; i < 12; i++) {
            if (snsh_failMatrix[i]) {
                logDebugText("FAILED: " + snsh_ncs[i] + " has got "
                        + snsh_ncs[i].getEventsNum(snsh_ers[i])
                        + " notifications instead of " + evMatrix[i]
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
         * init 3 RemoteEvent counters for each of sample entries
         * and their snapshots
         */
        ncs[0] = new NotifyCounter(sampleEntry1, Lease.FOREVER);
        ncs[1] = new NotifyCounter(sampleEntry2, Lease.FOREVER);
        ncs[2] = new NotifyCounter(sampleEntry3, Lease.FOREVER);
        snsh_ncs[0] = new SnapshotNotifyCounter(sampleEntry1, Lease.FOREVER,
                space);
        snsh_ncs[1] = new SnapshotNotifyCounter(sampleEntry2, Lease.FOREVER,
                space);
        snsh_ncs[2] = new SnapshotNotifyCounter(sampleEntry3, Lease.FOREVER,
                space);

        // init 5 counters with wrong templates and their snapshots
        template = new SimpleEntry("TestEntry #3", 1);
        ncs[3] = new NotifyCounter(template, Lease.FOREVER);
        snsh_ncs[3] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 2-nd wrong template and it's snapshot
        template = new SimpleEntry("TestEntry #1", 3);
        ncs[4] = new NotifyCounter(template, Lease.FOREVER);
        snsh_ncs[4] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 3-rd wrong template and it's snapshot
        template = new SimpleEntry("TestEntry #3", 3);
        ncs[5] = new NotifyCounter(template, Lease.FOREVER);
        snsh_ncs[5] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 4-th wrong template and it's snapshot
        template = new SimpleEntry(null, 3);
        ncs[6] = new NotifyCounter(template, Lease.FOREVER);
        snsh_ncs[6] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 5-th wrong template and it's snapshot
        template = new SimpleEntry("TestEntry #3", null);
        ncs[7] = new NotifyCounter(template, Lease.FOREVER);
        snsh_ncs[7] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // init counter with null entry as a template and it's snapshot
        ncs[8] = new NotifyCounter(null, Lease.FOREVER);
        snsh_ncs[8] = new SnapshotNotifyCounter(null, Lease.FOREVER, space);

        /*
         * init 3 counters for templates with null values for different
         * fields and their snapshots
         */
        template = new SimpleEntry("TestEntry #1", null);
        ncs[9] = new NotifyCounter(template, Lease.FOREVER);
        snsh_ncs[9] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 2-nd template and it's snapshot
        template = new SimpleEntry(null, 2);
        ncs[10] = new NotifyCounter(template, Lease.FOREVER);
        snsh_ncs[10] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // 3-rd template and it's snapshot
        template = new SimpleEntry(null, null);
        ncs[11] = new NotifyCounter(template, Lease.FOREVER);
        snsh_ncs[11] = new SnapshotNotifyCounter(template, Lease.FOREVER,
                space);

        // now register all counters
        for (i = 0; i < 12; i++) {
            ers[i] = space.notify(ncs[i].getTemplate(), null, ncs[i],
                    ncs[i].getLeaseTime(), null);
            snsh_ers[i] = space.notify(snsh_ncs[i].getSnapshot(), null,
                    snsh_ncs[i], snsh_ncs[i].getLeaseTime(), null);
        }

        // sleep for a while to let all listeners register properly
        Thread.sleep(timeout1);
        logDebugText("now sleeping for " + timeout1
                + " to let all listeners register properly.");

        // write 3 sample entries using their snapshots to the space 3 times
        space.write(snapshot1, null, leaseForeverTime);
        space.write(snapshot1, null, leaseForeverTime);
        space.write(snapshot1, null, leaseForeverTime);
        space.write(snapshot2, null, leaseForeverTime);
        space.write(snapshot2, null, leaseForeverTime);
        space.write(snapshot2, null, leaseForeverTime);
        space.write(snapshot3, null, leaseForeverTime);
        space.write(snapshot3, null, leaseForeverTime);
        space.write(snapshot3, null, leaseForeverTime);
        logDebugText("Snapshots of 3 sample entries have been written"
                + " to the space 3 times.");

        // wait for a while to let all listeners get notifications
        logDebugText("now sleeping for " + timeout1
                + " to let all listeners get notifications.");
        Thread.sleep(timeout1);

        // check, that listeners got required number of notifications
        for (i = 0; i < 12; i++) {
            if (ncs[i].getEventsNum(ers[i]) != evMatrix[i]) {
                failed = true;
                snsh_failMatrix[i] = true;
            } else {
                snsh_failMatrix[i] = false;
            }

            if (snsh_ncs[i].getEventsNum(snsh_ers[i]) != evMatrix[i]) {
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
                        + " required.");
            } else {
                logDebugText(ncs[i].toString() + " has got "
                        + ncs[i].getEventsNum(ers[i])
                        + " notifications as expected");
            }

            if (snsh_failMatrix[i]) {
                logDebugText("FAILED: " + snsh_ncs[i] + " has got "
                        + snsh_ncs[i].getEventsNum(snsh_ers[i])
                        + " notifications instead of " + evMatrix[i]
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
