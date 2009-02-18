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
package com.sun.jini.test.impl.outrigger.matching;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// All other imports
import java.util.List;
import java.rmi.RemoteException;
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import com.sun.jini.thread.TaskManager;
import com.sun.jini.constants.TimeConstants;
import net.jini.admin.Administrable;
import com.sun.jini.outrigger.JavaSpaceAdmin;
import com.sun.jini.outrigger.AdminIterator;


/**
 * Stress test class. This class can run multiple, concurrent
 * read/take and write threads. The user is able to configure the
 * number of entries written as well as the number of read and write
 * tasks.
 * Note: This test does not use an audited space like
 * <code>MatchTestBase</code>.
 */
public class StressTest extends MatchTestCore {

    /**
     * If true, turns on verbose debugging information to console.
     */
    private static boolean debug = false;

    /**
     * Collection of <code>Entry</code> objects written
     * to the space by writer tasks.
     */
    private RandomList writeList = new RandomList();

    /**
     * True if test should interleave read and write operations.
     * Otherwise, all write operations will complete before
     * before the read operations take place.
     */
    private boolean interleave = false;

    /*
     * TODO: Add -interleave <chunk size> processing (eg
     * blast <chunk size> writes then <chunk size> reads).
     */

    /**
     * Number of <code>Entry</code> objects to put into the space.
     */
    private int numEntries = 1000;

    /**
     * Running count of <code>Entry</code> objects to put into the space.
     * This number will be used to schedule some casualty processing.
     */
    private Counter writeCount = new Counter();

    /**
     * Running count of <code>Entry</code> objects taken from the space.
     * This number will be used to schedule some casualty processing.
     */
    private Counter takeCount = new Counter();

    /**
     * Number of writer tasks to schedule.
     */
    private int numWriters = 1;

    /**
     * Number of reader tasks to schedule.
     */
    private int numReaders = 1;

    /**
     * The <code>TaskManager</code> handling read/write tasks.
     * Not valid until <code>setup()</code> is called.
     * @see StressTest#setup
     */
    private TaskManager taskMgr = null;

    /**
     * Maintains number of task objects created.  Used to
     * provide a unique identifier for each task object.
     * If static data could be contained in inner classes,
     * this would be declared in the StressTask class.
     */
    private static int taskCounter = 0;

    /**
     * Flag value for determining when a shutdown request has been issued.
     * If true, trap RemoteExceptions on read/write/take operations and
     * reissue them one more time. Fail test RemoteExceptions are caught
     * when the flag is false or on second tries.
     */
    private boolean shutdownAlready = false;

    /**
     * Flag value which is set after a space restart notification.
     * If true, prevent subsequent threads from waiting on this object.
     */
    private boolean restartNotificationSent = false;

    /**
     * If set, compute timing statistics for reads, takes, and
     * and write operations.
     * Not valid until <code>setup()</code> is called.
     */
    protected boolean timingStats;

    /**
     * If set, verify each take operation with a subsequent read operation.
     * That is, try reading an entry after it was taken in order
     * to verify that the take took place.
     * Not valid until <code>setup()</code> is called.
     */
    protected boolean verifyingTakes;

    /**
     * fields which are added during porting for correct failure messages
     */
    protected boolean WriteRandomEntryTaskOK = true;
    protected String WriteRandomEntryTaskMSG;
    protected boolean ReadAndTakeEntryTaskOK = true;
    protected String ReadAndTakeEntryTaskMSG;

    /**
     * Method called for parsing command line arguments.
     * Accepted options are:
     * <DL>
     *
     * <DT>-interleave</DT>
     * <DD> Sets interleaving to true. All read and write operations will
     * run concurrently. Otherwise, read operations are scheduled after
     * all write operations have completed. Defaults to false.
     * </DD>
     *
     * <DT>-num_entries <var>int</var> </DT>
     * <DD> The total number of entries to be written to the space.
     * Defaults to 1000.
     * </DD>
     *
     * <DT>-num_readers <var>int</var> </DT>
     * <DD> The total number of reader tasks to schedule.
     * Defaults to 1.
     * </DD>
     *
     * <DT>-num_writers <var>int</var> </DT>
     * <DD> The total number of writer tasks to schedule.
     * Defaults to 1.
     * </DD>
     *
     * <DT>-timing_stats </DT>
     * <DD> Enables the collection of timing statistics.
     * Defaults to false.
     * </DD>
     *
     * <DT>-verify_takes </DT>
     * <DD> Enables take verification via a subsequent read operation
     * on the same entry.
     * Defaults to false.
     * </DD>
     *
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        interleave = getConfig().getBooleanConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.StressTest.interleave", false);
        numEntries = getConfig().getIntConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.StressTest.num_entries", 1000);
        numReaders = getConfig().getIntConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.StressTest.num_readers", 1);
        numWriters = getConfig().getIntConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.StressTest.num_writers", 1);
        timingStats = getConfig().getBooleanConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.StressTest.timing_stats", false);
        verifyingTakes = getConfig().getBooleanConfigVal("com.sun.jini.test.impl."
                + "outrigger.matching.StressTest.verify_takes", false);
        debug = getConfig().getBooleanConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.StressTest.debug", false);
    }

    /**
     * Method for initializing test environment.
     * Sets up a TaskManager with enough threads to run all tasks concurrently
     * as well as requesting a space to be started.
     */
    public void setup(QAConfig config) throws Exception {
        if (debug) {
            logger.log(Level.INFO, "setup()");
        }

        // Get the space for testing
        super.setup(config);

        if (debug) {
            logger.log(Level.INFO, "\tHave a JavaSpace");
        }
        this.parse();

        // Reset shutdown flag.
        shutdownAlready = false;

        // Determine allocation of write requests per write task
        int entryAllocation = numEntries / numWriters;

        // If there are more writers than requests then fail()
        if (entryAllocation == 0) {
            throw new TestException("Too many writer tasks requested");
        }

        // Determine allocation of write requests per write task
        entryAllocation = numEntries / numReaders;

        // If there are more readers than requests then fail()
        if (entryAllocation == 0) {
            throw new TestException("Too many reader tasks requested");
        }
        taskMgr = new TaskManager(numReaders + numWriters,
                1000 * 60, // idle timeout -- 60 secs
                1.0f); // load factor
    }

    /**
     * Method that runs the stress test.
     * It consists of:
     * <UL>
     * <LI> Creating <code>numWriters</code> write tasks and evenly allocate
     * a number of write requests to each one.
     * <LI> Creating <code>numReaders</code> read tasks and evenly allocate
     * a number of read requests to each one.
     * </UL>
     * If any task fails, it will call the <code>failed</code> method
     * with the appropriate message. Otherwise, the program will exit
     * normally.
     * @see StressTest#failed
     */
    public void run() throws Exception {
        // Reset variables
        int i = 0;
        int alloc = 0;
        writeCount.reset();
        takeCount.reset();

        /*
         * Determine allocation of write requests per write task
         * Note: There is already a pre-check for entryAllocation == 0
         * in the setup() method. If entryAllocation == 0 the program
         * should fail earlier than here.
         */
        int entryAllocation = numEntries / numWriters;

        // Determine allocation overflow
        int remainder = numEntries % numWriters;

        if (debug) {
            logger.log(Level.INFO, "\tAllocating " + numWriters + " write tasks.");
            logger.log(Level.INFO, "\tAllocation = " + entryAllocation
                    + ", remainder = " + remainder);
        }

        for (i = 0; i < numWriters; ++i) {

            // Add "overflow" requests to first task.
            if (i == 0) {
                alloc = entryAllocation + remainder;
            } else {
                alloc = entryAllocation;
            }

            // Create write task and add to taskManager
            taskMgr.add(new WriteRandomEntryTask(alloc));
        }

        /*
         * Determine allocation of read requests per read task.
         * Note: There is already a pre-check for entryAllocation == 0
         * in the setup() method. If entryAllocation == 0 the program
         * should fail earlier than here.
         */
        entryAllocation = numEntries / numReaders;

        // Determine allocation overflow
        remainder = numEntries % numReaders;

        if (debug) {
            logger.log(Level.INFO, "\tAllocating " + numReaders + " read tasks.");
            logger.log(Level.INFO, "\tAllocation = " + entryAllocation
                    + ", remainder = " + remainder);
        }

        for (i = 0; i < numReaders; ++i) {

            // Add "overflow" requests to first task.
            if (i == 0) {
                alloc = entryAllocation + remainder;
            } else {
                alloc = entryAllocation;
            }

            // Create read task and add to taskManager
            taskMgr.add(new ReadAndTakeEntryTask(alloc));
        }

        // Determine writeCount at which spaceSet() will be called.
        int shutdownCount = 0;

        if (interleave) {

            // Wait until at least 1/2 of the writes are committed
            shutdownCount = numEntries / 2;
        } else {

            // Wait until at least all the writes are committed
            shutdownCount = numEntries;
        }

        if (debug) {
            logger.log(Level.INFO, "\tShutting down after " + shutdownCount
                    + " writes.");
        }

        // Loop until all writes and takes have been processed.
        while ((writeCount.getCount() < numEntries)
                || (takeCount.getCount() < numEntries)) {

            /*
             * Check to see if it's time to shutdown
             * Should probably check MatchTestCore::tryShutdown > 0
             * but it's currently a private member.
             */
            if (!shutdownAlready
                    && writeCount.getCount() >= shutdownCount) {

                // Reset (one-shot) flag.
                shutdownAlready = true;

                if (debug) {
                    logger.log(Level.INFO, "\tBefore spaceSet call ...");
                }

                // Shutdown space, if requested.
                spaceSet();

                if (debug) {
                    logger.log(Level.INFO, "\tAfter spaceSet call ...");
                }

                // Notify any waiting tasks/threads.
                shutdownNotify();
            }

            if (debug) {
                logger.log(Level.INFO, "WriteCount   : " + writeCount.getCount());
                logger.log(Level.INFO, "TakeCount    : " + takeCount.getCount());
                logger.log(Level.INFO, "WriteListSize: " + writeList.size());
            }

            // Delay before checking again.
            try {
                Thread.sleep(1000 * 1); // 1.0 sec
            } catch (InterruptedException ie) {

                // trap, but do nothing.
            }
        }

        /*
         * Verify that the space is "clean". All written entries
         * should have been taken by this point.
         */
        JavaSpaceAdmin admin = (JavaSpaceAdmin) ((Administrable)
                space).getAdmin();
        admin = (JavaSpaceAdmin) getConfig().prepare("test.outriggerAdminPreparer",
                                                     admin);

        final AdminIterator it = admin.contents(null, null, 1);

        if (it.next() != null) {
            throw new TestException(
                    "Space was not empty upon termination.");
        }

        if (!ReadAndTakeEntryTaskOK) {
            throw new TestException(ReadAndTakeEntryTaskMSG);
        }

        if (!WriteRandomEntryTaskOK) {
            throw new TestException(WriteRandomEntryTaskMSG);
        }

        if (debug) {
            logger.log(Level.INFO, "Test completed.");
        }
    }

    /**
     * Method for reporting an unsuccessful exit status.
     * This is a workaround that allows inner classes to
     * access the protected fail() methods from <code>TestBase</code>.
     * @param msg Error message to report
     * @param t   Associated exception, if any.
     */
    private void failed(String msg, Throwable t) throws TestException {
        fail(msg, t);
    }

    /**
     * Method for reporting an unsuccessful exit status.
     * This is a workaround that allows inner classes to
     * access the protected fail() methods from <code>TestBase</code>.
     * @param msg Error message to report
     */
    private void failed(String msg) throws TestException {
        fail(msg, null);
    }

    /**
     * Method for synchronizing tasks after a shutdown operation.
     * Callers will wait() until a subsequent notifyAll() is called
     * after the shutdown has completed.
     */
    private synchronized void shutdownWait() {
        try {
            if (!restartNotificationSent) {
                wait();
            }
        } catch (InterruptedException ie) {

            // Not a fatal exception, but note it anyway.
            if (debug) {
                logger.log(Level.INFO, "Task wait was interrupted");
            }
        }
    }

    /**
     * Method for synchronizing tasks after a shutdown operation.
     * The main thread will call this method to notify all waiting
     * Tasks after the shutdown process has completed.
     */
    private synchronized void shutdownNotify() throws TestException {
        try {
            if (debug) {
                logger.log(Level.INFO, "shutdownNotify(): notifying waiting threads.");
            }
            restartNotificationSent = true;
            notifyAll();
        } catch (IllegalMonitorStateException ie) {
            fail("Task notify failed", ie);
        }
    }

    /**
     * Pass through function that provides inner classes with
     * access to <code>TestBase's</code> logging facilities.
     */
    private void log(String s) {
        logger.log(Level.INFO, s);
    }


    /**
     * WriteRandomEntryTask encapsulates the writing of an entry
     * into a space and placing it in the <code>writeList</code> for
     * subsequent comsumption by the reader tasks.
     */
    class WriteRandomEntryTask extends StressTask
            implements TaskManager.Task, TimeConstants {

        /**
         * Allocated number of write requests for this task.
         */
        private int numWrites = 0;

        // Timeout of 1 week
        static private final long writeLeaseRequest = (7 * DAYS);

        /**
         * Object for computing running statistics.
         */
        private TimingStatistics writeStats = null;

        /**
         * Constructor.
         * @param numWrites The number of write operations to attempt.
         */
        public WriteRandomEntryTask(int numWrites) {
            super("WriteTask");
            this.numWrites = numWrites;

            if (timingStats) {
                writeStats = new TimingStatistics();
            }
        }

        /**
         * <code>run</code> attempts to write <code>numWrites</code> entries
         * into the space as well placing each successful write into the
         * <code>writeList</code>.
         */
        public void run() {
            try {
                taskMessage("Started ...");

                if (timingStats) {
                    writeStats.reset();
                }
                Entry entry = null;

                for (int i = 0; i < numWrites; i++) {

                    // Create a unique Entry
                    entry = RandomEntryFactory.getUniqueEntry();

                    // Write Entry to space and writeList
                    spaceWrite(entry, null, writeLeaseRequest, false);
                    taskMessage("Successfully wrote: " + entry);

                    // Not explicitly necessary, but just to be safe.
                    Thread.yield();
                }

                if (timingStats) {
                    taskMessage("Write Summary: " + writeStats.getStats());
                }
                taskMessage("done ...");
            } catch (Exception ex) {
                WriteRandomEntryTaskOK = false;
                WriteRandomEntryTaskMSG = ex.toString();
            }
        }

        /**
         * Writes the given entry to the space as well as adding it to the
         * list of written <code>Entry</code> objects. If a write fails
         * due to a RemoteException after a call to <code>spaceSet()</code>
         * then a single attempt to re-write the entry will take place.
         * @see WriteRandomEntryTask#retryWrite
         * @see WriteRandomEntryTask#addEntryToList
         */
        private void spaceWrite(Entry entry, Transaction txn, long leaseReq,
                boolean retryAttempt) throws TestException {
            Lease granted = null;
            long before = 0;
            long after = 0;

            try {
                if (timingStats) {
                    before = System.currentTimeMillis();
                }

                // Write Entry to space and writeList
                granted = space.write(entry, txn, leaseReq);

                if (timingStats) {
                    after = System.currentTimeMillis();
                    writeStats.computeStats(before, after);
                }
                addOutriggerLease(granted, true);
                addEntryToList(entry);
            } catch (RemoteException re) {
                if (shutdownAlready && !retryAttempt) {

                    // Wait for main thread to notify us of shutdown completion.
                    shutdownWait();
                    retryWrite(entry, txn, leaseReq);
                } else {
                    taskFailed("Caught an unexpected Exception during a retry",
                            re);
                }
            } catch (Exception e) {
                taskFailed("Received an unexpected Exception", e);
            }
        }

        /**
         * Attempts to re-write an entry after a failed write call due
         * to a RemoteException after a spaceSet() call.
         * An initial read is performed to check if the entry was in
         * written. If so, no write is performed. Otherwise,
         * a second write is attempted.
         */
        private void retryWrite(Entry entry, Transaction txn, long lease)
                throws TestException {
            Entry ent = null;

            try {

                // Check if entry was written.
                ent = space.readIfExists(entry, txn, 0);

                /*
                 * If we get an entry, then the previous
                 * write succeeded, but we jumped over the increment code.
                 */
                if (ent != null) {
                    addEntryToList(ent);
                } else {

                    // Retry the write operation with the retry flag enabled.
                    spaceWrite(entry, txn, lease, true);
                }
            } catch (Exception e) {
                taskFailed("Caught an unexpected Exception "
                        + "during a retry attempt", e);
            }
        }

        /**
         * Adds an <code>Entry</code> object to <code>writeList</code>
         * and increments the internal <code>writeCount</code> object.
         */
        private void addEntryToList(Entry e) {

            // Add entry to writeList
            synchronized (writeList) {
                writeList.add(e);
            }

            // Increment write counter.
            writeCount.increment();
        }

        /**
         * Determines whether this task can be scheduled.
         * The given lists of tasks can be queried for any
         * dependency conditions.
         * Called prior to scheduling this task object.
         * Currently, writeTasks have no dependency on any other
         * tasks, so it just returns false.
         */
        public boolean runAfter(java.util.List tasks, int size) {

            // Write tasks are not dependent on any other tasks
            return false;
        }
    }


    /**
     * ReadAndTakeEntryTask encapsulates the obtainment of a random entry
     * from the <code>writeList</code> and subesequent read/take of that entry
     * from the space.
     */
    class ReadAndTakeEntryTask extends StressTask implements TaskManager.Task {

        /**
         * Number of requested read operations.
         */
        private int numReads = 0;

        /**
         * Object for maintaining "read" timing statistics.
         */
        TimingStatistics readStats = null;

        /**
         * Object for maintaining "read" timing statistics.
         */
        TimingStatistics takeStats = null;

        /**
         * Object for maintaining "read" timing statistics.
         */
        TimingStatistics verifyStats = null;

        /**
         * Constructor.
         * @param numReads The number of read operations to attempt.
         */
        public ReadAndTakeEntryTask(int numReads) {
            super("ReadTask");
            this.numReads = numReads;

            if (timingStats) {
                readStats = new TimingStatistics();
                takeStats = new TimingStatistics();
                verifyStats = new TimingStatistics();
            }
        }

        /**
         * <code>run</code> attempts to read <code>numReads</code> entries
         * from the space. It then tries to take each entry from the space
         * and attempt another read operation (which should fail).
         */
        public void run() {
            taskMessage("Started ...");

            if (timingStats) {
                readStats.reset();
                takeStats.reset();
                verifyStats.reset();
            }
            Entry template = null;
            Entry entry = null;
            int count = 0;

            try {
                while (count < numReads) {

                    // Reset template reference
                    template = null;
                    synchronized (writeList) {
                        if (writeList.size() > 0) {
                            template = (Entry) writeList.removeRandomItem();
                        }
                    }

                    // Check if a template was read.
                    if (template != null) {
                        ++count;

                        // Attempt to read a unique entry
                        entry = spaceReadWithRetry(template, null, queryTimeOut,
                                false, false);

                        // Fail if no entry was returned
                        if (entry == null) {
                            failed("Could not read existing entry from space");
                        }
                        taskMessage("Successfully read: " + template);

                        // Attempt to take a unique entry
                        entry = spaceTakeWithRetry(template, null, queryTimeOut,
                                false);

                        // Fail if no entry was returned
                        if (entry == null) {
                            taskFailed("Could not take existing entry "
                                    + "from space");
                        }
                        taskMessage("Successfully took: " + template);

                        // Increment take counter.
                        takeCount.increment();

                        if (verifyingTakes) {

                            /*
                             * Attempt to re-read the taken entry
                             * -- should fail.
                             */
                            entry = spaceReadWithRetry(template, null,
                                    queryTimeOut, false, true);

                            if (entry != null) {
                                taskFailed("Duplicate unique entry obtained.");
                            }
                        }
                    }
                    Thread.yield();
                }
            } catch (Exception e) {
                ReadAndTakeEntryTaskOK = false;
                ReadAndTakeEntryTaskMSG = "Caught exception after " + count
                        + " operations: " + e;
            }

            if (timingStats) {
                taskMessage("Read Summary:" + readStats.getStats());
                taskMessage("Take Summary:" + takeStats.getStats());

                if (verifyingTakes) {
                    taskMessage("Verify Summary:" + verifyStats.getStats());
                }
            }
            taskMessage("done ...");
        }

        /**
         * Reads the given <code>Entry</code> object from the space.
         * If the read/take operation fails due to a RemoteException after
         * a call to spaceSet(), then a second, single read attempt is made.
         * @see StressTest#spaceRead
         */
        private Entry spaceReadWithRetry(Entry tmpl, Transaction txn,
                long timeout, boolean retryAttempt, boolean verificationAttempt)
                throws TestException {
            Entry entry = null;
            long before = 0, after = 0;

            try {
                if (timingStats) {
                    before = System.currentTimeMillis();
                }
                entry = spaceRead(tmpl, txn, timeout);

                if (timingStats) {
                    after = System.currentTimeMillis();

                    if (verificationAttempt && verifyingTakes) {
                        verifyStats.computeStats(before, after);
                    } else {
                        readStats.computeStats(before, after);
                    }
                }
            } catch (RemoteException re) {
                if (shutdownAlready && !retryAttempt) {

                    // Wait for main thread to notify us of shutdown completion.
                    shutdownWait();
                    entry = spaceReadWithRetry(tmpl, txn, timeout, true,
                            verificationAttempt);
                } else {
                    taskFailed("Caught remote exception.", re);
                }
            } catch (Exception e) {
                taskFailed("Caught exception.", e);
            }
            return entry;
        }

        /**
         * Takes the given <code>Entry</code> object from the space.
         * If the take operation fails due to a RemoteException after
         * a call to spaceSet(), then a second, single take attempt is made.
         * @see StressTest#spaceTake
         * @see retryTake
         */
        private Entry spaceTakeWithRetry(Entry tmpl, Transaction txn,
                long timeout, boolean retryAttempt) throws TestException {
            Entry entry = null;
            long before = 0, after = 0;

            try {
                if (timingStats) {
                    before = System.currentTimeMillis();
                }
                entry = spaceTake(tmpl, txn, timeout);

                if (timingStats) {
                    after = System.currentTimeMillis();
                    takeStats.computeStats(before, after);
                }
            } catch (RemoteException re) {
                if (shutdownAlready && !retryAttempt) {

                    // Wait for main thread to notify us of shutdown completion.
                    shutdownWait();
                    entry = retryTake(tmpl, txn, timeout);
                } else {
                    taskFailed("Caught  remote exception.", re);
                }
            } catch (Exception e) {
                taskFailed("Caught exception.", e);
            }
            return entry;
        }

        /**
         * Attempts to re-take the given Entry object after a failed
         * take operation due to a RemoteException after a call to
         * spaceSet(). An initial read operation is performed to see
         * if the given entry was taken. If so, no take operation is
         * attempted. Otherwise, another take attempt is made.
         * @see spaceTakeWithRetry
         */
        private Entry retryTake(Entry tmpl, Transaction txn, long timeout)
                throws TestException {
            Entry entry = null;

            try {
                entry = spaceReadWithRetry(tmpl, txn, timeout, true, true);

                if (entry != null) { // Entry still exists, so re-take it.
                    entry = spaceTakeWithRetry(tmpl, txn, timeout, true);
                } else { // Return template in the absence of a real entry.
                    entry = tmpl;
                }
            } catch (Exception e) {
                taskFailed("Caught exception.", e);
            }
            return entry;
        }

        /**
         * <code>runAfter</code> notifies the <code>TaskManager</code>
         * whether or not this read task should wait for any existing
         * task to complete before executing. If <code>interleave</code>
         * is set to true then it can execute without condition. Otherwise,
         * read tasks will wait for all write tasks to complete
         * before executing.
         */
        public boolean runAfter(java.util.List tasks, int size) {

            /*
             * If not interleaving then check to see if any
             * write tasks exist. If so, then return true.
             */
            if (!interleave) {
                for (int i = 0; i < size; ++i) {
                    if (tasks.get(i) instanceof WriteRandomEntryTask) {
                        return true;
                    }
                }
            }
            return false;
        }
    }


    /**
     * Base class for <code>StressTest.WriteRandomEntryTask</code> and
     * <code>StressTest.ReadAndTakeRandomEntryTask</code>, which provides
     * a unique identifier for each instantiation.
     * It also provides print utiility functions.
     */
    class StressTask {

        /**
         * Unique identifier for each StressTask object
         */
        private final String name;

        /**
         * Unique identifier for each StressTask object
         */
        private final int id;

        /**
         * Constructor.
         */
        public StressTask(String prefix) {

            // Generate unique id
            id = ++taskCounter;

            // Generate unique prefix string for output messages.
            name = ((prefix == null) ? "???Task" : prefix) + "_" + id + ": ";
        }

        /**
         * Utility method for pre-pending Task specific information
         * to status messages.
         * @param msg Description of condition that required the message.
         */
        protected void taskMessage(String msg) {
            if (debug) {
                log(name + msg);
            }
        }

        /**
         * Utility method for pre-pending Task specific information
         * to failure messages.
         * @param msg Description of condition that caused the failure.
         * @param t Exception associated with <code>msg</code> description.
         */
        protected void taskFailed(String msg, Throwable t)
                throws TestException {
            failed(name + msg, t);
        }

        /**
         * Utility method for pre-pending Task specific information
         * to failure messages.
         * @param msg Description of condition that caused the failure.
         */
        protected void taskFailed(String msg) throws TestException {
            taskFailed(msg, null);
        }
    }


    /**
     * Counter is a utility class that encapsulates an integer counter
     * variable. A counter object is used over a counter variable due to
     * synchronization issues. The issues are:
     * <DL>
     * <DT>Object locking</DT>
     * <DD>The counter object can be locked without having to
     * lock the entire encapsulating object.
     * </DD>
     * <DT>Protection</DT>
     * <DD>An unsynchronized method can be accessed while another thread
     * is running in a synchronized method. Using a smaller class reduces
     * the chances for inadvertently accessing the counter variables.
     * </DD>
     * </DL>
     */
    private class Counter {

        /**
         * Internal counter variable.
         */
        private int _count = 0;

        /**
         * Constructor. Declared to enforce protection level.
         */
        Counter() {

            // Do nothing.
        }

        /**
         * Resets internal counter to zero.
         */
        void reset() {

            // Integer assignment is atomic.
            _count = 0;
        }

        /**
         * Increments internal counter by one.
         */
        synchronized void increment() {
            ++_count;
        }

        /**
         * Returns current value of this <code>Counter</code> object.
         */
        int getCount() {

            // Returning an integer is atomic.
            return _count;
        }
    }
}
