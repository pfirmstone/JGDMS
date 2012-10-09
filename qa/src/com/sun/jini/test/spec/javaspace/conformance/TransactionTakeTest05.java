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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import net.jini.space.JavaSpace05;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;

/**
 * TransactionTakeTest05 tests JavaSpace05.take method
 * with usage of transactions.
 * See comments to run() method for details.
 *
 * 
 */
public class TransactionTakeTest05 extends AbstractTestBase {

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
     * This method asserts that for take operation:<br>
     * 1) Each Entry taken will match one or more elements of
     *    the passed Collection of templates.<br>
     * 2) If there are initially no matches in the space that
     *    are visible to the passed Transaction, an invocation
     *    of this method will block for up to a specified timeout
     *    for one or more matches to appear.<br>
     * 3) If the method succeeds, a non-null Collection will be returned.
     *    The Collection will contain a copy of each Entry that was taken.
     *    If no entries were taken, the Collection will be empty.
     *    Each Entry taken will be represented by a distinct Entry instance
     *    in the returned Collection, even if some of the entries are equivalent
     *    to others taken by the operation. There will be no null elements
     *    in the returned Collection.<br>
     * 4) If there is at least one matching Entry available in the space,
     *    an invocation of this method must take at least one Entry.
     *    If more than one matching Entry is available, the invocation
     *    may take additional entries. It must not take more than maxEntries,
     *    but an implementation may chose to take fewer entries from the space
     *    than the maximum available or the maximum allowed by maxEntries.<br>
     * 5) If there are initially no matching entries in the space, an invocation
     *    of this method should block for up to timeout milliseconds for a match
     *    to appear. If one or more matches become available before timeout
     *    expires, one or more of the newly available entries should be taken
     *    and the method should return without further blocking.<br>
     * 6) Throws:
     *      TransactionException - if txn is non-null and is
     *      not usable by the space.<br>
     *      IllegalArgumentException - if any non-null element of tmpls
     *      is not an instance of Entry, if tmpls is empty, if timeout
     *      is negative, or if maxEntries is non-positive.<br>
     *      NullPointerException - if tmpls is null.<br>
     * 7) If a TransactionException, IllegalArgumentException,
     *    or NullPointerException is thrown, no entries will have been taken.
     *    <br>
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
        testTemplates(templates, txn, timeout1, MAX_ENTRIES, expectedResult,
                      "Taking one entry");
        txn.commit();
        reset();

        templates.add((SimpleEntry) sampleEntry1.clone());
        templates.add((SimpleEntry) sampleEntry2.clone());
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        txn = getTransaction();
        testTemplates(templates, txn, timeout1, MAX_ENTRIES, expectedResult,
                      "Taking two entries");
        txn.commit();
        reset();

        templates.add(new SimpleEntry("TestEntry #1", null));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry3);
        txn = getTransaction();
        testTemplates(templates, txn, timeout1, MAX_ENTRIES, expectedResult,
                      "Taking two entries with one template");
        txn.commit();
        reset();

        templates.add(new SimpleEntry(null, 2));
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        txn = getTransaction();
        testTemplates(templates, txn, timeout1, MAX_ENTRIES, expectedResult,
                      "Taking two entries with one template: test2");
        txn.commit();
        reset();

        templates.add(new SimpleEntry(null, null));
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        txn = getTransaction();
        testTemplates(templates, txn, timeout1, MAX_ENTRIES, expectedResult,
                      "Taking three entries with one template");
        txn.commit();
        reset();

        space.write(sampleEntry1, null, leaseForeverTime);

        // now the space contains two copies of sampleEntry1
        templates.add((SimpleEntry) sampleEntry1.clone());
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry1);
        txn = getTransaction();
        testTemplates(templates, txn, timeout1, MAX_ENTRIES, expectedResult,
                      "Taking duplicate entries");
        txn.commit();
        reset();

        templates.add(null);
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        txn = getTransaction();
        testTemplates(templates, txn, timeout1, MAX_ENTRIES, expectedResult,
                      "Taking entries with null template");
        txn.commit();
        reset();

        // no such entry in the space
        SimpleEntry unavailableEntry = new SimpleEntry("TestEntry #1", 3);

        // It should block for "checkTime" time
        templates.add(unavailableEntry);

        // expected result is empty
        txn = getTransaction();
        testTemplates(templates, txn, checkTime, MAX_ENTRIES, expectedResult,
                      "Taking with wrong template");
        txn.commit();
        reset();

        ArrayList entriesToAdd = new ArrayList();
        ArrayList leasesToAdd = new ArrayList();

        /*
         * Taking unavailable entry.
         * It should block for "checkTime" time.
         * In the meanwhile (in instantTime) an entry
         * is added which then should be taken
         */
        templates.add((SimpleEntry) unavailableEntry.clone());
        expectedResult.add(unavailableEntry);
        entriesToAdd.add(unavailableEntry);
        leasesToAdd.add(new Long(leaseForeverTime));
        txn = getTransaction();
        JSWriter jsWriter = new JSWriter(entriesToAdd, txn, leasesToAdd,
                                         instantTime);
        Thread jsWriterThread = new Thread(jsWriter);
        jsWriterThread.start();
        testTemplates(templates, txn, checkTime, MAX_ENTRIES, expectedResult,
                      "Taking unavailable entry");
        if (jsWriter.e != null) {
            throw new TestException("Taking unavailable entry failed.",
                                    jsWriter.e);
        }
        txn.commit();
        reset();

        entriesToAdd.clear();
        leasesToAdd.clear();

        /*
         * Taking unavailable entry. Test 2.
         * It should block for "timeout1" time.
         * In the meanwhile  3 entries are added: 2 in instantTime
         * (one of them matches) and another (also matches) in checkTime.
         * 1 entry should be taken - that one which is added to the space first.
         */
        cleanSpace(space);
        templates.add(new SimpleEntry(null, 2));
        expectedResult.add(sampleEntry2);
        entriesToAdd.add(sampleEntry1);
        entriesToAdd.add(sampleEntry2);     // this one should match
        leasesToAdd.add(new Long(leaseForeverTime));
        leasesToAdd.add(new Long(leaseForeverTime));

        txn = getTransaction();
        JSWriter jsWriter2 = new JSWriter(entriesToAdd, txn, leasesToAdd,
                                          instantTime);
        Thread jsWriterThread2 = new Thread(jsWriter2);

        ArrayList entryToAdd = new ArrayList();
        ArrayList leaseToAdd = new ArrayList();
        entryToAdd.add(sampleEntry3);
        leaseToAdd.add(new Long(leaseForeverTime));

        JSWriter jsWriter3 = new JSWriter(entryToAdd, txn, leaseToAdd,
                                          checkTime);
        Thread jsWriterThread3 = new Thread(jsWriter3);

        jsWriterThread2.start();
        jsWriterThread3.start();
        testTemplates(templates, txn, timeout1, MAX_ENTRIES, expectedResult,
                      "Taking unavailable entry: test2");

        // make sure that jsWriterThread3 won't write after further reset()
        jsWriterThread3.join();
        if (jsWriter2.e != null) {
            throw new TestException("Taking unavailable entry: test2 failed.",
                                    jsWriter2.e);
        }
        if (jsWriter3.e != null) {
            throw new TestException("Taking unavailable entry: test2 failed.",
                                    jsWriter3.e);
        }
        txn.commit();
        reset();

        space.write(sampleEntry1, null, leaseForeverTime);
        space.write(sampleEntry1, null, leaseForeverTime);

        // now space contains three sampleEntry1 entries
        templates.add((SimpleEntry) sampleEntry1.clone());
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry1);
        txn = getTransaction();
        testTemplates(templates, txn, timeout1, 2, expectedResult,
                      "Taking not more than maxEntries");
        txn.commit();
        reset();

        txn = getTransaction();
        templates.add((SimpleEntry) sampleEntry1.clone());
        templates.add("Not an antry");
        JavaSpace05 space05 = (JavaSpace05) space;
        try {
            space05.take(templates, txn, instantTime, MAX_ENTRIES);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when templates contain not an instance "
                                    + "of Entry element");
        } catch (IllegalArgumentException e) {}

        templates.clear();
        try {
            space05.take(templates, txn, instantTime, MAX_ENTRIES);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when templates is empty");
        } catch (IllegalArgumentException e) {}

        templates.add((SimpleEntry) sampleEntry1.clone());
        try {
            space05.take(templates, txn, -1, MAX_ENTRIES);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when timout is negative (-1)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.take(templates, txn, instantTime, 0);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when maxEntries is non-positive (0)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.take(templates, txn, instantTime, -1);
            throw new TestException("IllegalArgumentException is not thrown "
                                    + "when maxEntries is non-positive (-1)");
        } catch (IllegalArgumentException e) {}

        try {
            space05.take(null, txn, instantTime, MAX_ENTRIES);
            throw new TestException("NullPointerException is not thrown "
                                    + "when templates is null");
        } catch (NullPointerException e) {}

        txn.commit();

        // transaction txn is not null and is not usable by the space
        try {
            space05.take(templates, txn, instantTime, MAX_ENTRIES);
            throw new TestException("TransactionException is not thrown "
                                    + "when taking from the space with "
                                    + "transaction which is not null "
                                    + "and is not usable by the space");
        } catch (TransactionException e) {}

        /*
         * Corresponds to comments #7 to this method.
         * No entries are supposed to be taken after last reset().
         * To check this - we will try to take the entries.
         */
        txn = getTransaction();
        templates.clear();
        templates.add(null);
        expectedResult.add(sampleEntry1);
        expectedResult.add(sampleEntry2);
        expectedResult.add(sampleEntry3);
        testTemplates(templates, txn, instantTime, MAX_ENTRIES, expectedResult,
                      "Previous exceptions should not have affected the space");
        txn.commit();
    }

    /**
     * Takes entries from the space by specified templates and
     * makes sure the taken entries are equal to the expected result
     *
     * @param templates      Templates
     * @param txn            Transaction
     * @param timeout        Timeout
     * @param maxEntries     maximal number of entries to be taken
     * @param expectedResult List of expected entries
     * @param testName       Name of the test
     * @throws Exception
     */
    private void testTemplates(List templates,
                               Transaction txn,
                               long timeout,
                               long maxEntries,
                               List expectedResult,
                               String testName) throws Exception
    {
        if (txn == null) {
            throw new TestException("Transaction is null for test " + testName);
        }
        JavaSpace05 space05 = (JavaSpace05) space;
        Collection result = space05.take(templates, txn, timeout, maxEntries);
        maxEntries -= result.size();
        if (result.size() > 0) {
            while (maxEntries > 0) {
                Collection anyMore = space05.take(templates, txn, 1000,
                                                  maxEntries);
                if (anyMore.size() == 0) break;
                result.addAll(anyMore);
                maxEntries -= anyMore.size();
            }
        }
        if (result.size() != expectedResult.size()) {
            throw new TestException(testName + " failed. "
                                    + "Taken result is not as expected.");
        }
        for (Iterator expectedItr = expectedResult.iterator();
             expectedItr.hasNext();) {
            SimpleEntry entry = (SimpleEntry) expectedItr.next();
            if (result.contains(entry)) {
                result.remove(entry);
            } else {
                throw new TestException(testName + " failed. "
                                        + "Taken result is not as expected.");
            }
        }
    }

    /**
     * Clears the templates and expectedResult lists,
     * clears the spaces and fills it with initial data
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

    /**
     * Writes entries to a space with predefined delay
     */
    private class JSWriter implements Runnable {

        List entries;
        Transaction txn;
        List leases;
        long delay;
        Exception e;

        public JSWriter(List entries, Transaction txn,
                        List leases, long delay)
        {
            this.entries = entries;
            this.txn = txn;
            this.leases = leases;
            this.delay = delay;
        }

        /**
         * Writes entries to a space with predefined delay
         */
        public void run() {
            try {
                Thread.sleep(delay);
                JavaSpace05 space05 = (JavaSpace05) space;
                space05.write(entries, txn, leases);
            }  catch (Exception e) {
                this.e = e;
            }
        }
    }

}
