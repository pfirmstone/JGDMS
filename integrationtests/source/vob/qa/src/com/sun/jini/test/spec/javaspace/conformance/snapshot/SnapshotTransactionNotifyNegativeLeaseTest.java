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
import net.jini.core.transaction.Transaction;
import net.jini.core.event.EventRegistration;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;


/**
 * SnapshotTransactionNotifyNegativeLeaseTest asserts, that we will get
 * an IllegalArgumentException if the lease time requested
 * is not Lease.ANY and is negative within the non null transaction.
 *
 * It tests this statement for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotTransactionNotifyNegativeLeaseTest
        extends SnapshotAbstractTestBase {

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
     * This method asserts, that we will get
     * an IllegalArgumentException if the lease time requested
     * is not Lease.ANY and is negative within the non null transaction.
     *
     * It tests this statement for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.6, 2.7.</P>
     */
    public void run() throws Exception {
        SnapshotNotifyCounter[] ncs = new SnapshotNotifyCounter[12];
        EventRegistration er;
        long[] leaseMatrix = new long[] {
            -199, -5000, -13999, Long.MIN_VALUE, -2, Long.MIN_VALUE, -345,
            -8999, -15000, -16000000, Long.MIN_VALUE, -3 };
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);
        SimpleEntry template;
        Transaction txn;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create the non null transaction
        txn = getTransaction();

        /*
         * init 3 RemoteEvent counters for snapshot for each
         * of sample entries
         */
        ncs[0] = new SnapshotNotifyCounter(sampleEntry1, leaseMatrix[0],
                space);
        ncs[1] = new SnapshotNotifyCounter(sampleEntry2, leaseMatrix[1],
                space);
        ncs[2] = new SnapshotNotifyCounter(sampleEntry3, leaseMatrix[2],
                space);

        // init 5 counters with snapshots of wrong templates
        template = new SimpleEntry("TestEntry #3", 1);
        ncs[3] = new SnapshotNotifyCounter(template, leaseMatrix[3], space);

        // 2-nd wrong template
        template = new SimpleEntry("TestEntry #1", 3);
        ncs[4] = new SnapshotNotifyCounter(template, leaseMatrix[4], space);

        // 3-rd wrong template
        template = new SimpleEntry("TestEntry #3", 3);
        ncs[5] = new SnapshotNotifyCounter(template, leaseMatrix[5], space);

        // 4-th wrong template
        template = new SimpleEntry(null, 3);
        ncs[6] = new SnapshotNotifyCounter(template, leaseMatrix[6], space);

        // 5-th wrong template
        template = new SimpleEntry("TestEntry #3", null);
        ncs[7] = new SnapshotNotifyCounter(template, leaseMatrix[7], space);

        // init counter with snapshot of null entry as a template
        ncs[8] = new SnapshotNotifyCounter(null, leaseMatrix[8], space);

        /*
         * init 3 counters with snapshots for templates with null values
         * for different fields
         */
        template = new SimpleEntry("TestEntry #1", null);
        ncs[9] = new SnapshotNotifyCounter(template, leaseMatrix[9], space);

        // snapshot of 2-nd template
        template = new SimpleEntry(null, 2);
        ncs[10] = new SnapshotNotifyCounter(template, leaseMatrix[10],
                space);

        // snapshot 3-rd template
        template = new SimpleEntry(null, null);
        ncs[11] = new SnapshotNotifyCounter(template, leaseMatrix[11],
                space);

        // try to register them
        for (int i = 0; i < 12; i++) {
            try {
                er = space.notify(ncs[i].getSnapshot(), txn, ncs[i],
                        ncs[i].getLeaseTime(), null);
                throw new TestException(" Notify operation for "
                        + ncs[i]
                        + " has not thrown IllegalArgumentException"
                        + " and returned " + er.toString());
            } catch (IllegalArgumentException iae) {
                logDebugText("IllegalArgumentException has been catched"
                        + " while trying to register " + ncs[i]
                        + " as expected.");
            }
        }

        // commit the transaction
        txnCommit(txn);
    }
}
