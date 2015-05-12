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

import java.util.logging.Level;

// net.jini
import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.space.JavaSpace;

// org.apache.river
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa
import org.apache.river.test.spec.javaspace.conformance.JavaSpaceTest;
import org.apache.river.test.spec.javaspace.conformance.SimpleEntry;


/**
 * SnapshotExpirationTest asserts, that when the lease expires,
 * the entry is removed from the space.
 *
 * It tests this statement for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotExpirationTest extends JavaSpaceTest {

    /**
     * This method asserts, that when the lease expires,
     * the entry is removed from the space.
     *
     * It tests this statement for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.3, 2.6.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        Entry snsh1;
        Entry snsh2;
        SimpleEntry result;
        long leaseTime1 = timeout1;
        long leaseTime2 = timeout2;
        Lease lease1 = null;
        Lease lease2 = null;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException(
                    "Space is not empty in the beginning.");
        }

        // create snapshots of sample entries
        snsh1 = space.snapshot(sampleEntry1);
        snsh2 = space.snapshot(sampleEntry2);

        /*
         * write two sample entries with different
         * finite lease times into the space.
         */
        lease1 = space.write(snsh1, null, leaseTime1);
        lease2 = space.write(snsh2, null, leaseTime2);

        // check that returned leases are not equal to null
        if (lease1 == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1
                    + " using it's snapshot with " + leaseTime1
                    + " lease time returned null lease.");
        }

        if (lease2 == null) {
            throw new TestException(
                    "performed write of " + sampleEntry2
                    + " using it's snapshot with " + leaseTime2
                    + " lease time returned null lease.");
        }

        // check that written entries are available in the space
        result = (SimpleEntry) space.readIfExists(snsh1, null,
	        JavaSpace.NO_WAIT);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1
                    + " using it's snapshot with " + leaseTime1
                    + " lease time,"
                    + " written entry is not available in the space.");
        }
        result = (SimpleEntry) space.readIfExists(snsh2, null,
	        JavaSpace.NO_WAIT);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry2
                    + " using it's snapshot with " + leaseTime2
                    + " lease time,"
                    + " written entry is not available in the space.");
        }
        logDebugText("Snapshots of " + sampleEntry1 + " with " + leaseTime1
                + " lease time and " + sampleEntry2 + " with " + leaseTime2
                + " lease time have been successfully"
                + " written to the space");

        // sleep to let 1-st lease expires
        logDebugText("Sleeping for " + (leaseTime1 + instantTime) + " ...");
        Thread.sleep(leaseTime1 + instantTime);
        logDebugText("awakening...");

        // check that 1-st entry is not available in the space
        result = (SimpleEntry) space.readIfExists(snsh1, null,
	        JavaSpace.NO_WAIT);

        if (result != null) {
            throw new TestException(
                    "performed write of " + sampleEntry1
                    + " using it's snapshot with " + leaseTime1
                    + " lease time, written entry is still available in the"
                    + " space after expiration time.");
        }
        logDebugText("Expiration for written " + sampleEntry1 + " with "
                + leaseTime1 + " lease time works as expected.");

        // check that 2-nd entry is still available in the space
        result = (SimpleEntry) space.readIfExists(snsh2, null,
	        JavaSpace.NO_WAIT);

        if (result == null) {
            throw new TestException(
                    "performed write of " + sampleEntry2
                    + " using it's snapshot with " + leaseTime2
                    + " lease time, written entry"
                    + " is not available in the space after " + leaseTime1
                    + " ms.");
        }

        // sleep to let 2-nd lease expires
        logDebugText("Sleeping for " + (leaseTime2 - leaseTime1) + " ...");
        Thread.sleep(leaseTime2 - leaseTime1);
        logDebugText("awakening...");

        // check that 2-nd entry is not available in the space
        result = (SimpleEntry) space.readIfExists(snsh1, null,
	        JavaSpace.NO_WAIT);

        if (result != null) {
            throw new TestException(
                    "performed write of " + sampleEntry2
                    + " using it's snapshot with " + leaseTime2
                    + " lease time, written entry"
                    + " is still available in the"
                    + " space after expiration time.");
        }
        logDebugText("Expiration for written " + sampleEntry2 + " with "
                + leaseTime2 + " lease time works as expected.");

        /*
         * write 1-st entry using it's snapshot twice with
         * different finite lease times
         */
        lease1 = space.write(snsh1, null, leaseTime1);
        lease2 = space.write(snsh1, null, leaseTime2);

        // check that returned leases are not equal to null
        if (lease1 == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1
                    + " using it's snapshot with " + leaseTime1
                    + " lease time returned null lease.");
        }

        if (lease2 == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1
                    + " using it's snapshot with " + leaseTime2
                    + " lease time returned null lease.");
        }
        logDebugText("Snapshots of " + sampleEntry1 + " with " + leaseTime1
                + " and " + leaseTime2 + " lease times has been"
                + " successfully written to the space.");

        // sleep to let 1-st lease expires
        logDebugText("Sleeping for " + (leaseTime1 + instantTime) + " ...");
        Thread.sleep(leaseTime1 + instantTime);
        logDebugText("awakening...");

        // check that only one of the 1-st entries is available in the space
        result = (SimpleEntry) space.takeIfExists(snsh1, null,
	        JavaSpace.NO_WAIT);

        if (result == null) {
            throw new TestException(
                    "performed 2 writes of " + sampleEntry1 + " with "
                    + leaseTime1 + " and " + leaseTime2
                    + " lease times using it's snapshot,"
                    + " there are no entries available in the space after "
                    + leaseTime1 + " ms, while 1 is expected.");
        }
        result = (SimpleEntry) space.takeIfExists(snsh1, null,
	        JavaSpace.NO_WAIT);

        if (result != null) {
            throw new TestException(
                    "performed 2 writes of " + sampleEntry1 + " with "
                    + leaseTime1 + " and " + leaseTime2
                    + " lease times using it's snapshot,"
                    + " there are 2 entries available in the space after "
                    + leaseTime1 + " ms, while 1 is expected.");
        }
        logDebugText("Expiration for written " + sampleEntry1 + " with "
                + leaseTime1 + " lease time works as expected.");

        /*
         * write 1-st entry using it's snapshot twice with different
         * finite lease times again
         */
        lease1 = space.write(snsh1, null, leaseTime1);
        lease2 = space.write(snsh1, null, leaseTime2);

        // check that returned leases are not equal to null
        if (lease1 == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1
                    + " using it's snapshot with " + leaseTime1
                    + " lease time returned null lease.");
        }

        if (lease2 == null) {
            throw new TestException(
                    "performed write of " + sampleEntry1
                    + " using it's snapshot with " + leaseTime2
                    + " lease time returned null lease.");
        }
        logDebugText(sampleEntry1 + " with " + leaseTime1 + " and "
                + leaseTime2 + " lease times has been"
                + " successfully written again to the space"
                + " using it's snapshot.");

        // sleep to let 2-nd(and thus 1-st) lease expires
        logDebugText("Sleeping for " + (leaseTime2 + instantTime) + " ...");
        Thread.sleep(leaseTime2 + instantTime);
        logDebugText("awakening...");

        // check that there are no entries available in the space
        result = (SimpleEntry) space.readIfExists(snsh1, null,
	        JavaSpace.NO_WAIT);

        if (result != null) {
            throw new TestException(
                    "performed 2 writes of " + sampleEntry1 + " with "
                    + leaseTime1 + " and " + leaseTime2
                    + " lease times using it's snapshot, there are"
                    + " at least 1 entry still available in the space"
                    + " after " + leaseTime2 + " ms, while 0 is expected.");
        }
        logDebugText("Expiration for written " + sampleEntry1 + " with "
                + leaseTime1 + " and " + leaseTime2
                + " lease times usint it's snapshot works as expected.");
    }
}
