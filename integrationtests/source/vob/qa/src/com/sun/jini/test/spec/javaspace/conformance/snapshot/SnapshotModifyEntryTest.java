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

// com.sun.jini
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;


/**
 * SnapshotModifyEntryTest asserts, that:
 * 1) Modifications to the original entry will not affect the snapshot.
 * 2) You can snapshot a null template.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotModifyEntryTest extends SnapshotAbstractTestBase {

    /**
     * This method asserts, that:
     * 1) Modifications to the original entry will not affect the snapshot.
     * 2) You can snapshot a null template.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.6.</P>
     */
    public void run() throws Exception {
        Entry[] snapshots = new Entry[5];
        SimpleEntry[] sampleEntries = new SimpleEntry[5];
        SimpleEntry[] origEntries = new SimpleEntry[5];
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);
        SimpleEntry result;
        int i;

        // init sampleEntries with different templates
        sampleEntries[0] = (SimpleEntry) sampleEntry1.clone();
        sampleEntries[1] = new SimpleEntry("TestEntry #1", null);
        sampleEntries[2] = new SimpleEntry(null, 2);
        sampleEntries[3] = new SimpleEntry(null, null);
        sampleEntries[4] = null;

        // write 3 sample entries
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);

        // init origEntries
        for (i = 0; i < 4; i++) {
            origEntries[i] = (SimpleEntry) sampleEntries[i].clone();
        }
        origEntries[4] = null;

        // create snapshots
        for (i = 0; i < 5; i++) {
            snapshots[i] = null;
            snapshots[i] = space.snapshot(sampleEntries[i]);
            logDebugText("Template " + sampleEntries[i]
                    + " has been successfully snapshotted.");
        }

        // now change all sampleEntries
        for (i = 0; i < 5; i++) {
            sampleEntries[i] = new SimpleEntry("TEST", 100);
        }

        // check that snapshots have not been changed
        for (i = 0; i < 5; i++) {
            result = (SimpleEntry) space.read(snapshots[i], null,
                    checkTime);

            if (result == null) {
                throw new TestException(
                        "Snapshot for the following template: "
                        + origEntries[i] + " has been changed."
                        + " While trying to read from the space with this"
                        + " template we've got null result but non null"
                        + " is expected.");
            } else {
                logDebugText("Snapshot for " + origEntries[i]
                        + " template has not been changed as expected.");
            }
        }
    }
}
