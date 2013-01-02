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
import net.jini.core.event.EventRegistration;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;


/**
 * NotifyLeaseFOREVERTest asserts, that for notify with
 * <code>Lease.FOREVER</code> lease time:
 * 1) A notify request's matching is done as it is for read
 * 2) Writing an entry into a space might generate notifications
 *    to registered objects.
 * 3) When matching entries arrive, the specified RemoteEventListener will
 *    eventually be notified by invoking listener's notify method.
 *
 * @author Mikhail A. Markov
 */
public class NotifyLeaseFOREVERTest extends JavaSpaceTest {

    /**
     * This method asserts, that for notify with <code>Lease.FOREVER</code>
     * lease time:
     * 1) A notify request's matching is done as it is for read
     * 2) Writing an entry into a space might generate notifications
     *    to registered objects.
     * 3) When matching entries arrive, the specified RemoteEventListener will
     *    eventually be notified by invoking listener's notify method.
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
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);
        SimpleEntry template;
        int i;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // init 3 RemoteEvent counters for each of sample entries
        ncs[0] = new NotifyCounter(sampleEntry1, Lease.FOREVER);
        ncs[1] = new NotifyCounter(sampleEntry2, Lease.FOREVER);
        ncs[2] = new NotifyCounter(sampleEntry3, Lease.FOREVER);

        // init 5 counters with wrong templates
        template = new SimpleEntry("TestEntry #3", 1);
        ncs[3] = new NotifyCounter(template, Lease.FOREVER);

        // 2-nd wrong template
        template = new SimpleEntry("TestEntry #1", 3);
        ncs[4] = new NotifyCounter(template, Lease.FOREVER);

        // 3-rd wrong template
        template = new SimpleEntry("TestEntry #3", 3);
        ncs[5] = new NotifyCounter(template, Lease.FOREVER);

        // 4-th wrong template
        template = new SimpleEntry(null, 3);
        ncs[6] = new NotifyCounter(template, Lease.FOREVER);

        // 5-th wrong template
        template = new SimpleEntry("TestEntry #3", null);
        ncs[7] = new NotifyCounter(template, Lease.FOREVER);

        // init counter with null entry as a template
        ncs[8] = new NotifyCounter(null, Lease.FOREVER);

        // init 3 counters with null values for different fields
        template = new SimpleEntry("TestEntry #1", null);
        ncs[9] = new NotifyCounter(template, Lease.FOREVER);

        // 2-nd template
        template = new SimpleEntry(null, 2);
        ncs[10] = new NotifyCounter(template, Lease.FOREVER);

        // 3-rd template
        template = new SimpleEntry(null, null);
        ncs[11] = new NotifyCounter(template, Lease.FOREVER);

        // now register all counters
        for (i = 0; i < 12; i++) {
            ers[i] = space.notify(ncs[i].getTemplate(), null, ncs[i],
                    ncs[i].getLeaseTime(), null);
	    ers[i] = prepareRegistration(ers[i]);
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
