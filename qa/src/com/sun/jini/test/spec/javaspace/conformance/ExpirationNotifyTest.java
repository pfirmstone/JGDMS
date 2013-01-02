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
import net.jini.core.event.EventRegistration;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;

import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.Test;

/**
 * ExpirationNotifyTest asserts, that the request specified by a
 * successful notify is as persistent as the entries of the space. They will be
 * remembered as long as an un-taken entry would be, until the lease expires,
 * or until any governing transaction completes, whichever is shorter.
 *
 * @author Mikhail A. Markov
 */
public class ExpirationNotifyTest extends TransactionTest {

    /**
     * This method asserts, that the request specified by a
     * successful notify is as persistent as the entries of the space.
     * They will be remembered as long as an un-taken entry would be,
     * until the lease expires, or until any governing transaction completes,
     * whichever is shorter.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.7.</P>
     */
    public void run() throws Exception {
        NotifyCounter[] ncs = new NotifyCounter[10];
        EventRegistration[] ers = new EventRegistration[10];
        boolean[] failMatrix = new boolean[10];
        boolean failed = false;
        long[] evTxnMatrix = new long[] {
            2, 6, 8, 10, 10, 2, 4, 4, 4, 4 };
        SimpleEntry sampleEntry = new SimpleEntry("TestEntry #1", 1);
        Transaction txn;
        long leaseTime1 = timeout2;
        long leaseTime2 = timeout2 * 2;
        long leaseTime3 = timeout2 * 3;
        long txnLeaseTime = leaseTime1 + timeout1;
        int i;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException("Space is not empty in the beginning.");
        }

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
         * to use with non null transaction parameter
         */
        ncs[5] = new NotifyCounter(sampleEntry, leaseTime1);
        ncs[6] = new NotifyCounter(sampleEntry, leaseTime2);
        ncs[7] = new NotifyCounter(sampleEntry, leaseTime3);
        ncs[8] = new NotifyCounter(sampleEntry, leaseForeverTime);
        ncs[9] = new NotifyCounter(sampleEntry, Lease.FOREVER);

        // now register all counters with null transaction parameter
        for (i = 0; i < 5; i++) {
            ers[i] = space.notify(ncs[i].getTemplate(), null, ncs[i],
                    ncs[i].getLeaseTime(), null);
            ers[i] = prepareRegistration(ers[i]);
        }

        // create the non null transaction with finite lease time
        txn = getTransaction(txnLeaseTime);

        // now register all counters with non null transaction parameter
        for (i = 5; i < 10; i++) {
            ers[i] = space.notify(ncs[i].getTemplate(), txn, ncs[i],
                    ncs[i].getLeaseTime(), null);
		ers[i] = prepareRegistration(ers[i]);
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
            if (ncs[i].getEventsNum(ers[i]) != evTxnMatrix[i]) {
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
        }

        // check: we fail of pass
        if (failed) {
            throw new TestException(
                    "Not all listeners've got expected number of events.");
        }
    }
}
