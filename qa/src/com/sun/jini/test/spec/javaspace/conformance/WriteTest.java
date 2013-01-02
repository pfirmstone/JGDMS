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

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

/**
 * WriteTest asserts that write with positive finite lease times
 * without transactions:
 * 1) Places a copy of an entry into the given JavaSpaces service,
 *    even if the same Entry object is used in more than one write.
 * 2) The Entry passed to the write is not affected by this operations.
 * 3) If a write returns without throwing an exception, that entry is
 *    committed to the space, possibly within a transaction.
 *
 * @author Mikhail A. Markov
 */
public class WriteTest extends JavaSpaceTest {

    /**
     * This method asserts that write with positive finite lease times
     * without transactions:
     * 1) Places a copy of an entry into the given JavaSpaces service,
     *    even if the same Entry object is used in more than one write.
     * 2) The Entry passed to the write is not affected by this operations.
     * 3) If a write returns without throwing an exception, that entry is
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
        long leaseTime1 = timeout1;
        long leaseTime2 = timeout2;
        long leaseTime3 = leaseTime1 + checkTime;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // init original entries for comparing
        origEntry1 = (SimpleEntry) sampleEntry1.clone();
        origEntry2 = (SimpleEntry) sampleEntry2.clone();

        /*
         * write 1-st entry without transactions
         * with positive finite lease time
         */
        space.write(sampleEntry1, null, leaseTime1);

        // check that original entry has not been changed
        if (!origEntry1.equals(sampleEntry1)) {
            throw new TestException(
                    "performed write operation with " + leaseTime1
                    + " lease time has changed entry object: "
                    + " original entry " + origEntry1
                    + " has been changed by " + sampleEntry1);
        }

        // check that written entry is available in the space
        result = (SimpleEntry) space.read(sampleEntry1, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1 + " with "
                    + leaseTime1 + " lease time,"
                    + " written entry is not available in the space.");
        }
        logDebugText("Write of " + sampleEntry1 + " with " + leaseTime1
                + " lease time works as expected.");

        /*
         * write 2-nd entry without transactions
         * with positive finite lease time
         */
        space.write(sampleEntry2, null, leaseTime2);

        // check that original entry has not been changed
        if (!origEntry2.equals(sampleEntry2)) {
            throw new TestException(
                    "performed write operation with " + leaseTime2
                    + " lease time has changed entry object: "
                    + " original entry " + origEntry2
                    + " has been changed by " + sampleEntry2);
        }

        // check that written entry is available in the space
        result = (SimpleEntry) space.read(sampleEntry2, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry2 + " with "
                    + leaseTime2 + " lease time,"
                    + " written entry is not available in the space.");
        }
        logDebugText("Write of " + sampleEntry2 + " with " + leaseTime2
                + " lease time works as expected.");

        // write 1-st entry with another finite lease time again
        space.write(sampleEntry1, null, leaseTime3);

        // check that original entry has not been changed
        if (!origEntry1.equals(sampleEntry1)) {
            throw new TestException(
                    "performed 2-nd write operation with " + leaseTime3
                    + " lease time has changed entry object: "
                    + " original entry " + origEntry1
                    + " has been changed by " + sampleEntry1);
        }

        // check that written entries are available in the space
        result = (SimpleEntry) space.take(sampleEntry1, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2-nd write operation for the same "
                    + sampleEntry1 + " with " + leaseTime3 + " lease time"
                    + ", 1st written entry is not available in the space.");
        }
        result = (SimpleEntry) space.take(sampleEntry1, null, checkTime);

        if (result == null) {
            throw new TestException(
                    "performed 2-nd write operation for the same "
                    + sampleEntry1 + " with " + leaseTime3 + " lease time"
                    + ", 2nd written entry is not available in the space.");
        }
        logDebugText("2-nd write of the same " + sampleEntry1 + " with "
                + leaseTime3 + " lease time works as expected.");
    }
}
