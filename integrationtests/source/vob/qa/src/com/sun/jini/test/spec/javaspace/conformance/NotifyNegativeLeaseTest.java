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
 * NotifyNegativeLeaseTest asserts, that we will get
 * an IllegalArgumentException if the lease time requested
 * is not Lease.ANY and is negative.
 *
 * @author Mikhail A. Markov
 */
public class NotifyNegativeLeaseTest extends AbstractTestBase {

    /**
     * This method asserts, that we will get
     * an IllegalArgumentException if the lease time requested
     * is not Lease.ANY and is negative.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.7.</P>
     */
    public void run() throws Exception {
        NotifyCounter[] ncs = new NotifyCounter[12];
        EventRegistration er;
        long[] leaseMatrix = new long[] {
            -199, -5000, -13999, Long.MIN_VALUE, -2, Long.MIN_VALUE, -345,
            -8999, -15000, -16000000, Long.MIN_VALUE, -3 };
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);
        SimpleEntry template;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // init 3 RemoteEvent counters for each of sample entries
        ncs[0] = new NotifyCounter(sampleEntry1, leaseMatrix[0]);
        ncs[1] = new NotifyCounter(sampleEntry2, leaseMatrix[1]);
        ncs[2] = new NotifyCounter(sampleEntry3, leaseMatrix[2]);

        // init 5 counters with wrong templates
        template = new SimpleEntry("TestEntry #3", 1);
        ncs[3] = new NotifyCounter(template, leaseMatrix[3]);

        // 2-nd wrong template
        template = new SimpleEntry("TestEntry #1", 3);
        ncs[4] = new NotifyCounter(template, leaseMatrix[4]);

        // 3-rd wrong template
        template = new SimpleEntry("TestEntry #3", 3);
        ncs[5] = new NotifyCounter(template, leaseMatrix[5]);

        // 4-th wrong template
        template = new SimpleEntry(null, 3);
        ncs[6] = new NotifyCounter(template, leaseMatrix[6]);

        // 5-th wrong template
        template = new SimpleEntry("TestEntry #3", null);
        ncs[7] = new NotifyCounter(template, leaseMatrix[7]);

        // init counter with null entry as a template
        ncs[8] = new NotifyCounter(null, leaseMatrix[8]);

        // init 3 counters with null values for different fields
        template = new SimpleEntry("TestEntry #1", null);
        ncs[9] = new NotifyCounter(template, leaseMatrix[9]);

        // 2-nd template
        template = new SimpleEntry(null, 2);
        ncs[10] = new NotifyCounter(template, leaseMatrix[10]);

        // 3-rd template
        template = new SimpleEntry(null, null);
        ncs[11] = new NotifyCounter(template, leaseMatrix[11]);

        // try to register them
        for (int i = 0; i < 12; i++) {
            try {
                er = space.notify(ncs[i].getTemplate(), null, ncs[i],
                        ncs[i].getLeaseTime(), null);
                throw new TestException( " Notify operation for "
                        + ncs[i]
                        + " has not thrown IllegalArgumentException"
                        + " and returned " + er.toString());
            } catch (IllegalArgumentException iae) {
                logDebugText("IllegalArgumentException has been caught"
                        + " while trying to register " + ncs[i]
                        + " as expected.");
            }
        }
    }
}
