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

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.lease.Lease;
import net.jini.space.JavaSpace05;
import net.jini.space.MatchSet;

/**
 * TransactionContentsTest05 tests JavaSpace05.contents method
 * with usage of transactions.
 * See comments to run() method for details.
 *
 * @author Pavel Bogdanov
 */
public class TransactionContentsTest05 extends AbstractTestBase {

    private final long MAX_ENTRIES = 5;

    private ArrayList templates = new ArrayList();
    private ArrayList expectedResult = new ArrayList();

    private SimpleEntry sampleEntry1 = new SimpleEntry("TestEntry #1", 1);
    private SimpleEntry sampleEntry2 = new SimpleEntry("TestEntry #2", 2);
    private SimpleEntry sampleEntry3 = new SimpleEntry("TestEntry #1", 2);

    /**
     * Sets up the testing environment.
     *
     * @param config
     * @throws Exception
     */
    public void setup(QAConfig config) throws Exception {

        // mandatory call to parent
        super.setup(config);

        // get an instance of Transaction Manager
        mgr = getTxnManager();
    }

    /**
     * This method asserts that for JavaSpace05's "contents" operation:<br>
     * 1) The tmpls parameter must be a Collection of Entry instances to be
     *    used as templates. All of the entries placed in the match set will
     *    match one or more of these templates. tmpls may contain null values
     *    and may contain duplicates.<br>
     * 2) The resulting match set must initially contain all of the visible
     *    matching entries in the space. [During the lifetime of the match set
     *    an Entry may be, but is not required to be, added to the match set if
     *    it becomes visible.] - this (in square brackets) can not be tested as
     *    the implementation is not required.<br>
     * 3) If the match set becomes empty, no more entries can be added and the
     *    match set enters the exhausted state.<br>
     * 4) Normally there are three conditions under which an Entry might be
     *    removed from the match set:<br>
     *    a) Any Entry yielded by an invocation of the MatchSet.next method on
     *       the match set (either as the return value of a successful call or
     *       embedded in an UnusableEntryException) must be removed from the
     *       match set.<br>
     *    b) Any Entry that remains in the match set after maxEntries entries
     *       are yielded by next invocations must be removed from the match set.
     *       In such a case, the criteria used to select which entries are
     *       yielded by next calls and which get removed from the set at the
     *       end is unspecified.<br>
     *    c) [Any Entry that during the lifetime of the match set becomes
     *       invisible may at the discretion of the implementation be removed
     *       from the match set.] - this(in square brackets) can not be tested
     *       as the fulfilment of this statement may or may not be required by
     *       the implementation.<br>
     * 5) If the match set is leased and leaseDuration is positive, the initial
     *    duration of the lease must be less than or equal to leaseDuration.<br>
     * 6) If txn is non-null and still active, any Entry removed from the match
     *    set by a next call must be locked as if it had been returned by a
     *    read operation using txn. An implementation may establish the read
     *    lock on the Entry any time between when the Entry is added to the
     *    match set and when the Entry is removed from the match set by an
     *    invocation of next. These read locks are not released when the match
     *    set reaches either the exhausted state or the invalidated state.
     *    If from the space's perspective the txn leaves the active state,
     *    the space must remove from the match set any entries in the match
     *    set that have not yet been read locked.[<- no way to test this
     *    statement(previous sentense) as it is not known what entries have
     *    not been locked]. This may require the match set to be invalidated.
     *    <br>
     * 7) Throws:
     *      TransactionException - if txn is non-null and
     *      is not usable by the space.<br>
     *      IllegalArgumentException - if any non-null element of tmpls
     *      is not an instance of Entry, if tmpls is empty, if leaseDuration
     *      is neither positive nor Lease.ANY, or if maxEntries is non-positive.
     *      <br>
     *      NullPointerException - if tmpls is null
     *
     * <P>Notes:<BR>For more information see the JavaSpaces05 javadoc </P>
     *
     * @throws Exception
     */
    public void run() throws Exception {
        reset();

        templates.add((SimpleEntry) sampleEntry1.clone());
        expectedResult.add(sampleEntry1);
        Transaction txn = getTransaction();
        testTemplates(templates, txn, Lease.ANY, MAX_ENTRIES, expectedResult,
                      "Reading one entry");
        checkEntriesExist(expectedResult, txn, "Reading one entry");
        txn.commit();
        reset();

        templates.add(new SimpleEntry(null, 2));
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        txn = getTransaction();
        testTemplates(templates, txn, Lease.ANY, MAX_ENTRIES, expectedResult,
                      "Reading two entries with one template");
        checkEntriesExist(expectedResult, txn,
                          "Reading two entries with one template");
        txn.commit();
        reset();

        templates.add(new SimpleEntry(null, null));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        txn = getTransaction();
        testTemplates(templates, txn, Lease.ANY, MAX_ENTRIES, expectedResult,
                      "Reading three entries with one template");
        checkEntriesExist(expectedResult, txn,
                          "Reading three entries with one template");
        txn.commit();
        reset();

        templates.add(null);
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        txn = getTransaction();
        testTemplates(templates, txn, Lease.ANY, MAX_ENTRIES, expectedResult,
                      "Reading with template containing null value");
        checkEntriesExist(expectedResult, txn,
                          "Reading with template containing null value");
        txn.commit();
        reset();

        templates.add((SimpleEntry) sampleEntry1.clone());
        templates.add((SimpleEntry) sampleEntry1.clone());
        expectedResult.add(sampleEntry1);
        txn = getTransaction();
        testTemplates(templates, txn, Lease.ANY, MAX_ENTRIES, expectedResult,
                      "Reading with template containing duplicates");
        checkEntriesExist(expectedResult, txn,
                          "Reading with template containing duplicates");
        txn.commit();
        reset();

        /*
         * No more entries can be added to exhausted match set. It also tests
         * that an entry is removed from match set after performing
         * MatchSet.next() method
         */
        JavaSpace05 space05 = (JavaSpace05) space;
        templates.add(sampleEntry1);
        txn = getTransaction();
        MatchSet matchSet = space05.contents(templates, txn, Lease.ANY,
                                             MAX_ENTRIES);
        if (matchSet.next() == null) {
            throw new TestException(
                    "Match set is exhausted. But it shouldn't be.");
        }

        // -- now it is supposed to be exhausted
        if (matchSet.next() != null) {
            throw new TestException(
                    "Match set was not exhausted. But should have been.");
        }
        space.write(sampleEntry1, txn, leaseForeverTime);
        Thread.sleep(1000);
        if (matchSet.next() != null) {
            throw new TestException("An entry added to exhausted match set. "
                                    + "But must not have been.");
        }
        txn.commit();
        reset();

        /*
         * After maxEntries entries are yielded by next invocations the match
         * set becomes empty. For this test case let maxEntries = 2
         */
        templates.add(null);    // for 3 existing entries
        txn = getTransaction();
        matchSet = space05.contents(templates, txn, Lease.ANY, 2);
        matchSet.next();
        matchSet.next();
        if (matchSet.next() != null) {
            throw new TestException("Match set is not empty after maxEntries "
                                    + "are yielded by next invocations");
        }
        txn.commit();
        reset();

        /*
         * The initial duration of the lease must be less than or equal to
         * leaseDuration. Let leaseDuration = instantTime.
         */
        templates.add(null);    // for 3 existing entries
        txn = getTransaction();
        matchSet = space05.contents(templates, txn, leaseForeverTime,
                                    MAX_ENTRIES);
        Lease lease = matchSet.getLease();
        if (lease != null) {
            long initialDuration = lease.getExpiration()
                    - System.currentTimeMillis();
            if (initialDuration > leaseForeverTime) {
                throw new TestException("Initial duration of the lease "
                                        + "is more than leaseDuration");
            }
        } // else Lease is not tested as the matchset is not leased
        txn.commit();
        reset();

        //corresponds to item #6 in this method's comments
        txn = getTransaction();
        templates.add((SimpleEntry) sampleEntry1.clone());
        matchSet = space05.contents(templates, txn, leaseForeverTime,
                                    MAX_ENTRIES);
        matchSet.next();
        SimpleEntry entry = (SimpleEntry) space.take(
                (SimpleEntry) sampleEntry1.clone(), null, instantTime);
        if (entry != null) {
            throw new TestException("Locked entry can be taken "
                                    + "from the space.");
        }
        txn.commit();
        reset();

        //corresponds to item #7 in this method's comments
        txn = getTransaction();
        templates.add((SimpleEntry) sampleEntry1.clone());
        templates.add("not an Entry");
        try {
            space05.contents(templates, txn, Lease.ANY, MAX_ENTRIES);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when a non-null element of tepmlates "
                                    + "is not an instance on Entry");
        } catch (IllegalArgumentException e) {}

        templates.clear();
        try {
            space05.contents(templates, txn, Lease.ANY, MAX_ENTRIES);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when templates is empty");
        } catch (IllegalArgumentException e) {}

        templates.add((SimpleEntry) sampleEntry1.clone());
        try {
            space05.contents(templates, txn, 0, MAX_ENTRIES);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when leaseDuration is nither positive "
                                    + "nor Lease.ANY (0)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.contents(templates, txn, Lease.ANY - 1, MAX_ENTRIES);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when leaseDuration is nither positive "
                                    + "nor Lease.ANY (Lease.ANY-1)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.contents(templates, txn, Lease.ANY, 0);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when maxEntries is non-positive (0)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.contents(templates, txn, Lease.ANY, -1);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when maxEntries is non-positive (-1)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.contents(null, txn, Lease.ANY, MAX_ENTRIES);
            throw new TestException("NullPointerException is not thrown "
                                    + "when tmpls is null");
        } catch (NullPointerException e) {}
        txn.commit();

        // transaction txn is not null and is not usable by the space
        try {
            space05.contents(templates, txn, instantTime, MAX_ENTRIES);
            throw new TestException("TransactionException is not thrown "
                                    + "when getting match set from the space "
                                    + "with transaction which is not null "
                                    + "and is not usable by the space");
        } catch (TransactionException e) {}

        cleanSpace(space);
    }

    /**
     * Reads entries from the space by specified templates and
     * makes sure the taken entries are equal to the expected result.
     * Reading utilizes JavaSpace05.contents() method.
     *
     * @param templates      Templates
     * @param txn            Transaction
     * @param leaseDuration  Requested initial lease time for the resulting
     *                       match set
     * @param maxEntries     maximal number of entries to be read
     * @param expectedResult List of expected entries
     * @param testName       Name of the test
     * @throws Exception
     */
    private void testTemplates(List templates,
                               Transaction txn,
                               long leaseDuration,
                               long maxEntries,
                               List expectedResult,
                               String testName) throws Exception
    {
        if (txn == null) {
            throw new TestException("Transaction is null for test " + testName);
        }
        JavaSpace05 space05 = (JavaSpace05) space;
        MatchSet matchSet = space05.contents(templates, txn, leaseDuration,
                                             maxEntries);
        ArrayList result = new ArrayList();
        SimpleEntry entry = (SimpleEntry) matchSet.next();
        while (entry != null) {
            result.add(entry);
            entry = (SimpleEntry) matchSet.next();
        }

        if (result.size() != expectedResult.size()) {
            throw new TestException(testName + " failed. "
                                    + "Taken result is not as expected.");
        }

        for (Iterator expectedItr = expectedResult.iterator();
             expectedItr.hasNext();) {
            SimpleEntry expectedEntry = (SimpleEntry) expectedItr.next();
            if (result.contains(expectedEntry)) {
                result.remove(expectedEntry);
            } else {
                throw new TestException(
                        testName + " failed. Taken result is not as expected.");
            }
        }
    }

    /**
     * Checks that the specified entries still exist in the space.
     *
     * @param entries  Entries to be checked for existence
     * @param testName Name of the test
     * @throws Exception
     */
    private void checkEntriesExist(List entries, Transaction txn,
                                   String testName) throws Exception {
        Iterator entriesItr = entries.iterator();
        while (entriesItr.hasNext()) {
            SimpleEntry se = (SimpleEntry) entriesItr.next();
            SimpleEntry result = (SimpleEntry) space.readIfExists(se, txn, 0);
            if (result == null) {
                throw new TestException(testName + " failed. "
                        + "Entry(ies) no longer available in the space.");
            }
        }
    }

    /**
     * Clears the templates and expectedResult lists,
     * clears the space and fills it with initial data
     *
     * @throws Exception
     */
    private void reset() throws Exception {
        templates.clear();
        expectedResult.clear();
        cleanSpace(space);
        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry2, null, leaseForeverTime);
        space.write(sampleEntry3, null, leaseForeverTime);
    }
}
