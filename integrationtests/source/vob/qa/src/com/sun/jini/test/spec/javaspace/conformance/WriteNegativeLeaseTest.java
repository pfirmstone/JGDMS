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
 * WriteNegativeLeaseTest asserts, that we will get an IllegalArgumentException
 * if the lease time requested is negative and is not equal to Lease.ANY.
 *
 * @author Mikhail A. Markov
 */
public class WriteNegativeLeaseTest extends AbstractTestBase {

    /**
     * This method asserts, that we will get an IllegalArgumentException if the
     * lease time requested is negative and is not equal to Lease.ANY.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.3.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry origEntry;
        SimpleEntry result;
        long nVal = -199;
        boolean caught = false;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        /*
         * try to write an entry with negative lease time other then
         * Lease.ANY value.
         */
        origEntry = (SimpleEntry) sampleEntry.clone();

        try {
            space.write(sampleEntry, null, nVal);
        } catch (IllegalArgumentException iae) {
            logDebugText("IllegalArgumentException is caught as expected"
                    + " while writing with " + nVal
                    + " value for lease time.");
            caught = true;
        } catch (Exception ex) {
            throw new TestException(
                    "Unexpected exception has been thrown while using "
                    + nVal + " value for lease time: " + ex);
        }

        if (!caught) {
            throw new TestException(
                    "IllegalArgumentException was not"
                    + " thrown while specifying " + nVal
                    + " value for lease time.");
        }

        // check that original entry has not been changed
        if (!origEntry.equals(sampleEntry)) {
            throw new TestException(
                    "performed write operation with " + nVal
                    + " value for lease time has changed entry object: "
                    + " original entry " + origEntry
                    + " has been changed by " + sampleEntry);
        }

        // check that entry has not been written to the space
        result = (SimpleEntry) space.read(sampleEntry, null, checkTime);

        if (result != null) {
            throw new TestException(
                    "performed write operation of " + sampleEntry + " with "
                    + nVal
                    + " value for lease time. In spite of throwing an"
                    + " IllegalArgumentException, entry has been written"
                    + " to the space.");
        }
        logDebugText("Write operation of " + sampleEntry + " with " + nVal
                + " value for lease time works as expected.");
    }
}
