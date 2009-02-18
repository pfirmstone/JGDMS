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
import net.jini.space.JavaSpace;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.test.spec.javaspace.conformance.SimpleEntry;


/**
 * SnapshotReadIfExistsNO_WAITTest asserts that for readIfExists with NO_WAIT
 * timeout:
 * 1) A timeout of NO_WAIT means to return immediately, with no waiting,
 *    which is equivalent to using a zero timeout.
 * 2) If a match is found by readIfExists, a reference to a copy of the matching
 *    entry is returned.
 * 3) If no match is found, null is returned.
 * 4) Passing a null reference for the template will match any entry.
 *
 * It tests these statements for snapshots.
 *
 * @author Mikhail A. Markov
 */
public class SnapshotReadIfExistsNO_WAITTest
        extends SnapshotAbstractReadTestBase 
{
    /**
     * This method asserts that for readIfExists with NO_WAIT timeout:
     * 1) a timeout of NO_WAIT means to return immediately, with no waiting,
     *    which is equivalent to using a zero timeout.
     * 2) If a match is found by readIfExists, a reference to a copy of the
     *    matching entry is returned.
     * 3) If no match is found, null is returned.
     * 4) Passing a null reference for the template will match any entry.
     *
     * It tests these statements for snapshots.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * sections 2.4, 2.6.</P>
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

        // write three sample entries twice to the space
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);

        /*
         * readIfExists 1-st entry from the space using snapshot of
         * the same one as a template with JavaSpace.NO_WAIT timeout
         */
        msg = testTemplate(sampleEntry1, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        /*
         * readIfExists sample entry from the space using
         * snapshots of wrong template entries and JavaSpace.NO_WAIT timeout
         */
        template = new SimpleEntry("TestEntry #3", 1);
        msg = testWrongTemplate(template, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 2-nd wrong template
        template = new SimpleEntry("TestEntry #1", 3);
        msg = testWrongTemplate(template, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 3-rd wrong template
        template = new SimpleEntry("TestEntry #3", 3);
        msg = testWrongTemplate(template, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 4-th wrong template
        template = new SimpleEntry(null, 3);
        msg = testWrongTemplate(template, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 5-th wrong template
        template = new SimpleEntry("TestEntry #3", null);
        msg = testWrongTemplate(template, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        /*
         * readIfExists entry from the space using snapshot of null
         * as a template and JavaSpace.NO_WAIT timeout
         */
        msg = testTemplate(null, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        /*
         * readIfExists sample entries from the space using
         * snapshots for templates with null as a wildcard for different
         * fields and JavaSpace.NO_WAIT timeout
         */
        template = new SimpleEntry("TestEntry #1", null);
        msg = testTemplate(template, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        // try 2-nd template
        template = new SimpleEntry(null, 2);
        msg = testTemplate(template, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 3-rd template
        template = new SimpleEntry(null, null);
        msg = testTemplate(template, null, JavaSpace.NO_WAIT, 0, true);

        if (msg != null) {
            throw new TestException(msg);
        }
    }
}
