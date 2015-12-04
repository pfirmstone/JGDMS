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
package org.apache.river.test.spec.javaspace.conformance;

import java.util.logging.Level;

// net.jini
import net.jini.core.transaction.Transaction;
import net.jini.space.JavaSpace;

// org.apache.river
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

/**
 * TransactionTakeNO_WAITTest asserts that for take within the non null
 * transaction with NO_WAIT timeout:
 * 1) A timeout of NO_WAIT means to return immediately, with no waiting,
 *    which is equivalent to using a zero timeout.
 * 2) If a take returns a non-null value, the entry has been removed
 *    from the space.
 * 3) If no match is found, null is returned.
 * 4) Passing a null reference for the template will match any entry.
 *
 * @author Mikhail A. Markov
 */
public class TransactionTakeNO_WAITTest extends AbstractTransactionTakeTest {
    
    /**
     * This method asserts that for take within the non null transaction
     * with NO_WAIT timeout:
     * 1) a timeout of NO_WAIT means to return immediately, with no waiting,
     *    which is equivalent to using a zero timeout.
     * 2) If a take returns a non-null value, the entry has been removed
     *    from the space.
     * 3) If no match is found, null is returned.
     * 4) Passing a null reference for the template will match any entry.
     *
     * <P>Notes:<BR>For more information see the JavaSpaces specification
     * section 2.5.</P>
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);
        SimpleEntry template;
        Transaction txn;
        String msg;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException("Space is not empty in the beginning.");
        }

        // create the non null transaction
        txn = getTransaction();

        // write three sample entries into the space
        space.write(sampleEntry1, txn, leaseForeverTime);
        space.write(sampleEntry2, txn, leaseForeverTime);
        space.write(sampleEntry3, txn, leaseForeverTime);

        /*
         * take 1-st entry from the space using the same one as a template
         * with JavaSpace.NO_WAIT timeout within the transaction
         */
        msg = testTemplate(sampleEntry1, txn, JavaSpace.NO_WAIT, 0, 0,
                false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // write taken entry back to the space
        space.write(sampleEntry1, txn, leaseForeverTime);

        /*
         * take sample entry from the space using
         * wrong template entries and JavaSpace.NO_WAIT timeout
         * within the transaction
         */
        template = new SimpleEntry("TestEntry #3", 1);
        msg = testWrongTemplate(template, txn, JavaSpace.NO_WAIT, 0, 0,
                false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 2-nd wrong template
        template = new SimpleEntry("TestEntry #1", 3);
        msg = testWrongTemplate(template, txn, JavaSpace.NO_WAIT, 0, 0,
                false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 3-rd wrong template
        template = new SimpleEntry("TestEntry #3", 3);
        msg = testWrongTemplate(template, txn, JavaSpace.NO_WAIT, 0, 0,
                false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 4-th wrong template
        template = new SimpleEntry(null, 3);
        msg = testWrongTemplate(template, txn, JavaSpace.NO_WAIT, 0, 0,
                false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // 5-th wrong template
        template = new SimpleEntry("TestEntry #3", null);
        msg = testWrongTemplate(template, txn, JavaSpace.NO_WAIT, 0, 0,
                false);

        if (msg != null) {
            throw new TestException(msg);
        }

        /*
         * take entry from the space using null as a template
         * and JavaSpace.NO_WAIT timeout within the transaction
         */
        msg = testTemplate(null, txn, JavaSpace.NO_WAIT, 0, 0, false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // clean the space and write 3 entries again
        cleanSpace(space, txn);
        space.write(sampleEntry1, txn, leaseForeverTime);
        space.write(sampleEntry2, txn, leaseForeverTime);
        space.write(sampleEntry3, txn, leaseForeverTime);

        /*
         * take sample entries from the space using templates with
         * null as a wildcard for different fields
         * and JavaSpace.NO_WAIT timeout within the transaction
         */
        template = new SimpleEntry("TestEntry #1", null);
        msg = testTemplate(template, txn, JavaSpace.NO_WAIT, 0, 0, false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // clean the space and write 3 entries again
        cleanSpace(space, txn);
        space.write(sampleEntry1, txn, leaseForeverTime);
        space.write(sampleEntry2, txn, leaseForeverTime);
        space.write(sampleEntry3, txn, leaseForeverTime);

        // try 2-nd template
        template = new SimpleEntry(null, 2);
        msg = testTemplate(template, txn, JavaSpace.NO_WAIT, 0, 0, false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // clean the space and write 3 entries again
        cleanSpace(space, txn);
        space.write(sampleEntry1, txn, leaseForeverTime);
        space.write(sampleEntry2, txn, leaseForeverTime);
        space.write(sampleEntry3, txn, leaseForeverTime);

        // 3-rd template
        template = new SimpleEntry(null, null);
        msg = testTemplate(template, txn, JavaSpace.NO_WAIT, 0, 0, false);

        if (msg != null) {
            throw new TestException(msg);
        }

        // commit the transaction
        txnCommit(txn);
    }
}
