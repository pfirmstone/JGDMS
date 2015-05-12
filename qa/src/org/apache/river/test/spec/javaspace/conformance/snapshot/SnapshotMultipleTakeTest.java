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
import net.jini.space.JavaSpace;

// org.apache.river
import org.apache.river.qa.harness.TestException;

// org.apache.river.qa
import org.apache.river.test.spec.javaspace.conformance.SimpleEntry;


/**
 * SnapshotMultipleTakeTest asserts that for take with timeouts
 * other then NO_WAIT:
 * 1) If a take returns a non-null value, the entry has been removed
 *    from the space.
 * 2) If no match is found, null is returned.
 * 3) Passing a null reference for the template will match any entry.
 *
 * It tests multiple take operations for different templates.
 *
 * It tests these statements for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotMultipleTakeTest extends SnapshotTakeTestBase {

    /**
     * This method asserts that for take with timeouts other then NO_WAIT:
     * 1) If a take returns a non-null value, the entry has been removed
     *    from the space.
     * 2) If no match is found, null is returned.
     * 3) Passing a null reference for the template will match any entry.
     *
     * It tests multiple take operations for different templates.
     *
     * It tests these statements for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.5, 2.6.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);
        SimpleEntry template;
        String msg;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException("Space is not empty in the beginning.");
        }

        // write two sample entries twice to the space
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);

        /*
         * take 1-st entry twice from the space using snapshot of
         * the same one as a template
         */
        for (int i = 1; i <= 2; i++) {
            msg = testTemplate(sampleEntry1, null, Long.MAX_VALUE, i, 2,
                    false);

            if (msg != null) {
                throw new TestException(msg);
            }
        }

        // write taken entries to the space again
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);

        // write 3-rd sample entry twice to the space
        space.write(sampleEntry3, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);

        /*
         * Take entry from the space using snapshot of null as a template
         * 6 times to take all written entries from the space.
         */
        for (int i = 1; i <= 6; i++) {
            msg = testTemplate(null, null, checkTime, i, 6, false);

            if (msg != null) {
                throw new TestException(msg);
            }
        }

        // write all taken entries to the space again
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);

        /*
         * Take entry from the space using snapshot of template with
         * 1-st null field 4 times to take all matching written entries
         * from the space.
         */
        template = new SimpleEntry(null, 2);

        for (int i = 1; i <= 4; i++) {
            msg = testTemplate(template, null, timeout1, i, 4, false);

            if (msg != null) {
                throw new TestException(msg);
            }
        }

        // write taken entries back to the space
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);

        /*
         * Take entry from the space using snapshot of template with
         * 2-nd null field 4 times to take all matching written entries
         * from the space.
         */
        template = new SimpleEntry("TestEntry #1", null);

        for (int i = 1; i <= 4; i++) {
            msg = testTemplate(template, null, timeout2, i, 4, false);

            if (msg != null) {
                throw new TestException(msg);
            }
        }

        // write taken entries back to the space
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);

        /*
         * Take entry from the space using snapshot of template with both
         * null fields 6 times to take all matching written entries from
         * the space.
         */
        template = new SimpleEntry(null, null);

        for (int i = 1; i <= 6; i++) {
            msg = testTemplate(template, null, checkTime, i, 6, false);

            if (msg != null) {
                throw new TestException(msg);
            }
        }
    }
}
