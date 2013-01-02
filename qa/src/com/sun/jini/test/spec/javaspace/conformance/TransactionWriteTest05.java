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

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

import java.util.ArrayList;

import net.jini.space.JavaSpace05;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.lease.Lease;

/**
 * TransactionWriteTest05 tests JavaSpace05.write method
 * with usage of transactions.
 * See comments to run() method for details.
 *
 * @author Pavel Bogdanov
 */
public class TransactionWriteTest05 extends TransactionTest {

    /**
     * This method asserts that write:<br>
     * 1) Allows new copies of multiple Entry instances to be stored
     *    in the space using a single call.<br>
     * 2) A new copy of each element of entries must have been stored
     *    in the space.<br>
     * 3) A new copy of each element will be stored even if there are
     *    duplicates (either in terms of object identity or of entry
     *    equivalence) in entries.<br>
     * 4) Throws:
     *      TransactionException - if txn is non-null and is not usable by
     *      the space.<br>
     *      IllegalArgumentException - if entries and leaseDurations are not
     *      the same length or are empty, if any element of entries is not an
     *      instance of Entry, if any element of leaseDurations is not an
     *      instance of Long, or if any element of leaseDurations is a negative
     *      value other than Lease.ANY.<br>
     *      NullPointerException - if either entries or leaseDurations is null
     *      or contains a null value.<br>
     * 5) If a TransactionException, IllegalArgumentException,
     *    or NullPointerException is thrown, no entries will have been added
     *    to the space by this operation.<br>
     *
     * <P>Notes:<BR>For more information see the JavaSpaces05 javadoc </P>
     *
     * @throws Exception
     */
    public void run() throws Exception {
        SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
        SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
        SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #3", 3);

        long leaseTime1 = timeout1;
        long leaseTime2 = timeout2;

        // first check that space is empty
        if (!checkSpace(space)) {
            throw new TestException("Space is not empty in the beginning.");
        }
        JavaSpace05 space05 = (JavaSpace05) space;

        ArrayList entriesList = new ArrayList();
        entriesList.add(sampleEntry1);
        entriesList.add(sampleEntry2);

        ArrayList leasesList = new ArrayList();
        leasesList.add(new Long(leaseTime1));
        leasesList.add(new Long(leaseTime1));

        Transaction txn = getTransaction();
        space05.write(entriesList, txn, leasesList);

        // check that written entries are available in the space
        SimpleEntry resultEntry1 = (SimpleEntry) space05.read(sampleEntry1,
                                                              txn, checkTime);
        SimpleEntry resultEntry2 = (SimpleEntry) space05.read(sampleEntry2,
                                                              txn, checkTime);
        txn.commit();
        if (resultEntry1 == null || resultEntry2 == null) {
            throw new TestException("performed write of " + sampleEntry1
                                    + " with " + leaseTime1
                                    + " lease time and "
                                    + sampleEntry2 + " with " + leaseTime2
                                    + " lease time and "
                                    + " written entry(ies) is(are) not "
                                    + "available in the space.");
        }

        /*
         * A new copy of each element will be stored
         * even if there are duplicates
         */
        entriesList.clear();
        entriesList.add(sampleEntry1);
        entriesList.add(sampleEntry1);
        entriesList.add(sampleEntry3);

        leasesList.clear();
        leasesList.add(new Long(leaseTime1));
        leasesList.add(new Long(leaseTime2));
        leasesList.add(new Long(leaseTime2));

        txn = getTransaction();
        space05.write(entriesList, txn, leasesList);

        /*
         * check that entry1 can be taken from the space 3 times,
         * and entries 2 and 3 only once.
         */
        SimpleEntry result = (SimpleEntry) space05.take(sampleEntry1, txn,
                                                        checkTime);
        result = (SimpleEntry) space05.take(sampleEntry1, txn, checkTime);
        result = (SimpleEntry) space05.take(sampleEntry1, txn, checkTime);
        if (result == null) {
            throw new TestException("performed 2-nd and 3-rd write "
                    + "operation for the same " + sampleEntry1
                    + " with " + leaseTime1 + " lease time and "
                    + leaseTime2 + " lease time and all or some "
                    + "of the entries can not be taken");
        }
        result = (SimpleEntry) space05.take(sampleEntry2, txn, checkTime);
        if (result == null) {
            throw new TestException(sampleEntry2 + " with " + leaseTime1
                                    + " lease time"
                                    + " is not available in the space.");
        }
        result = (SimpleEntry) space05.take(sampleEntry3, txn, checkTime);
        if (result == null) {
            throw new TestException(sampleEntry3 + " with " + leaseTime2
                                    + " lease time"
                                    + " is not available in the space.");
        }
        txn.commit();

        cleanSpace(space);
        txn = getTransaction();
        entriesList.clear();
        leasesList.clear();
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when using empty entries list and "
                                    + "empty lease list");
        } catch (IllegalArgumentException e) {}

        entriesList.add(sampleEntry1);
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when using empty lease list");
        } catch (IllegalArgumentException e) {}

        entriesList.add(sampleEntry1);          // add the second entry
        leasesList.add(new Long(leaseTime1));   // add the first lease time
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when using entries list and lease "
                                    + "list of different length");
        } catch (IllegalArgumentException e) {}

        entriesList.clear();
        leasesList.clear();
        entriesList.add(sampleEntry1);
        entriesList.add("not an entry");
        leasesList.add(new Long(leaseTime1));
        leasesList.add(new Long(leaseTime2));
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when using not instance of Entry "
                                    + "as an element of entries");
        } catch (IllegalArgumentException e) {}

        entriesList.clear();
        leasesList.clear();
        entriesList.add(sampleEntry1);
        entriesList.add(sampleEntry1);
        leasesList.add(new Long(leaseTime1));
        leasesList.add("not a Long");
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when using not instance of Long "
                                    + "as an element of leaseDurations");
        } catch (IllegalArgumentException e) {}

        entriesList.clear();
        leasesList.clear();
        entriesList.add(sampleEntry1);
        entriesList.add(sampleEntry1);
        leasesList.add(new Long(leaseTime1));
        leasesList.add(new Long(Lease.ANY - 1));
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when an element of leaseDurations is "
                                    + "a negative value other than Lease.ANY");
        } catch (IllegalArgumentException e) {}

        if (!checkSpace(space, txn)) {
            throw new TestException("Some entries were written to the space "
                                    + "despite write operation threw "
                                    + "IllegalArgumentException");
        }

        entriesList.clear();
        leasesList.clear();
        entriesList.add(sampleEntry1);
        leasesList.add(new Long(leaseTime1));
        try {
            space05.write(null, txn, leasesList);
            throw new TestException("NullPointerException is not thrown "
                                    + "when entries list in null");
        } catch (NullPointerException e) {}
        try {
            space05.write(entriesList, txn, null);
            throw new TestException("NullPointerException is not thrown "
                                    + "when leaseDurations list in null");
        } catch (NullPointerException e) {}

        entriesList.clear();
        leasesList.clear();
        entriesList.add(sampleEntry1);
        entriesList.add(null);
        leasesList.add(new Long(leaseTime1));
        leasesList.add(new Long(leaseTime1));
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("NullPointerException is not thrown "
                                    + "when entries list contains null");
        } catch (NullPointerException e) {}

        entriesList.clear();
        leasesList.clear();
        entriesList.add(sampleEntry1);
        entriesList.add(sampleEntry1);
        leasesList.add(new Long(leaseTime1));
        leasesList.add(null);
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("NullPointerException is not thrown "
                                    + "when leaseDurations list contains null");
        } catch (NullPointerException e) {}

        if (!checkSpace(space,txn)) {
            throw new TestException("Some entries were written to the space "
                                    + "despite write operation threw "
                                    + "NullPointerException");
        }

        txn.commit();

        // transaction txn is not null and is not usable by the space
        entriesList.clear();
        leasesList.clear();
        entriesList.add(sampleEntry1);
        leasesList.add(new Long(leaseTime1));
        try {
            space05.write(entriesList, txn, leasesList);
            throw new TestException("TransactionException is not thrown "
                                    + "when writing to the space with "
                                    + "transaction which is not null "
                                    + "and is not usable by the space");
        } catch (TransactionException e) {}

        if (!checkSpace(space)) {
            throw new TestException("Some entries were written to the space "
                                    + "despite write operation threw "
                                    + "TransactionException");
        }
    }
}
