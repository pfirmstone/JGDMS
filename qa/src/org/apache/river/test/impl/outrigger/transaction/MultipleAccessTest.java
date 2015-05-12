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
package org.apache.river.test.impl.outrigger.transaction;

import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;

// All other imports
import net.jini.core.lease.Lease;
import net.jini.space.JavaSpace;
import net.jini.core.transaction.Transaction;
import java.rmi.UnmarshalException;
import java.rmi.RemoteException;


/**
 * Three (or more) threads pass entries each other under a transaction.
 * These thread work independently, and accesses to the space will be done
 * simultaneously. While these accesses, another illegal client try to read
 * entries.
 * <ul>
 *   <li> Make sure that the illegal client can't read any entries.
 *   <li> Make sure all entries are passed correctly.
 * </ul>
 *
 * @author H.Fukuda
 */
public class MultipleAccessTest extends TransactionTestBase {
    private final static int NUM_WORKERS = 4;
    private final static int NUM_ENTRIES = 20;

    /*
     * Vars to detect that Peeker/Worker failed.
     * Has been added during porting.
     */
    private volatile boolean peekerFailed = false;
    private volatile boolean workerFailed = false;
    private volatile String peekerFailMsg = "";
    private volatile String workerFailMsg = "";

    public void run() throws Exception {
        simpleSetup();

        // create a transaction
        Transaction txn = createTransaction();

        // generate & write entries
        writeEntries(txn);

        // start illegal access thread
        Peeker peeker = new Peeker(getSpace(), this);
        peeker.start();

        // create & start worker threads
        Worker[] workers = new Worker[NUM_WORKERS];

        for (int i = 0; i < NUM_WORKERS; i++) {
            workers[i] = new Worker(getSpace(), txn, i, i + 1, NUM_ENTRIES,
                    this);
            workers[i].start();
        }

        // wait until the job has done
        try {
            for (int i = 0; i < NUM_WORKERS; i++) {
                workers[i].join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // stop illegal access thread and check the result
        peeker.exit();

        try {
            peeker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (peeker.getReadCount() > 0) {
            throw new TestException(
                    "Some entries are visible from the outside of"
                    + " transaction.");
        }

        // commit the transaction
        commitTransaction(txn);

        // check if workers pass all entries
        for (int i = 0; i < NUM_WORKERS; i++) {
            if (workers[i].getWriteCount() != NUM_ENTRIES) {
                throw new TestException(
                        "Some entries are missing.");
            }
        }

        if (peekerFailed) {
            throw new TestException(
                    "Peeker failed with exception" + peekerFailMsg);
        }

        if (workerFailed) {
            throw new TestException(
                    "Worker failed with exception" + workerFailMsg);
        }

        // check entries
        checkEntries();
    }

    private void writeEntries(Transaction txn) throws TestException {
        SimpleEntry entry = new SimpleEntry();
        entry.string = "foo";
        entry.stage = new Integer(0); // initial stage is "0"

        for (int i = 0; i < NUM_ENTRIES; i++) {
            entry.id = new Integer(i);

            // write an entry under the transaction
            writeEntry(txn, entry);
            pass("[writeEntries]: wrote an initial entry (" + entry + ")");
        }
    }

    private void checkEntries() throws TestException {
        SimpleEntry entry = new SimpleEntry();
        SimpleEntry template = new SimpleEntry();
        template.string = "foo";
        template.stage = new Integer(NUM_WORKERS);

        for (int i = 0; i < NUM_ENTRIES; i++) {
            template.id = new Integer(i);

            try {
                entry = (SimpleEntry) getSpace().takeIfExists(template, null,
                        JavaSpace.NO_WAIT);
            } catch (Exception e) {
                fail("[checkEntries]: Exception thrown while try to read an"
                        + " entry", e);
            }

            if (entry == null) {
                fail("Entry [" + template + "] is missing.");
            }
        }
    }


    class Peeker extends Thread {
        private final JavaSpace space;
        private final TransactionTestBase parent;
        private int readCount = 0;
	private volatile boolean shouldStop = false;

        public Peeker(JavaSpace space, TransactionTestBase parent) {
            this.space = space;
            this.parent = parent;
        }

        public void run() {
            SimpleEntry entry = null;
            SimpleEntry template = new SimpleEntry();
            template.string = "foo";

            try {
                while (!shouldStop) {
                    try {
                        entry = (SimpleEntry) space.readIfExists(template, null,
                                JavaSpace.NO_WAIT);
                    } catch (Exception e) {
                        parent.fail("[Peeker]: Exception thrown while trying to"
                                + " read an entry", e);
                    }

                    if (entry != null) {
                        synchronized (this){
                            readCount++;
                        }
                    }

                    try {
                        sleep(400);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
	        e.printStackTrace();
                peekerFailed = true;
                peekerFailMsg = e.toString();
            }
        }
	
	/**
	  * Stops the thread.
	  */
	public void exit() {
	    shouldStop = true;
	}

        public int getReadCount() {
            synchronized (this){
                parent.pass("[Peeker]: read " + readCount + " entries");
                return readCount;
            }
        }
    }


    class Worker extends Thread {
        private final JavaSpace space;
        private final Transaction txn;
        private final int fromStage, toStage;
        private volatile int writeCount = 0;
        private final int expectedEntries;
        private final TransactionTestBase parent;
        private final int MAX_WAIT = 80000; // 8sec.
        private final int WAIT_UNIT = 400; // 400 msec.
        private volatile boolean completed = false;
        private final Object lock = new Object();

        public Worker(JavaSpace space, Transaction txn, int fromStage,
                int toStage, int expectedEntries, TransactionTestBase parent) {
            this.space = space;
            this.txn = txn;
            this.fromStage = fromStage;
            this.toStage = toStage;
            this.expectedEntries = expectedEntries;
            this.parent = parent;
        }

        public void run() {
            SimpleEntry entry = null;
            SimpleEntry template = new SimpleEntry();
            template.string = "foo";
            template.stage = new Integer(fromStage);

            try {
                parent.pass("[Worker" + fromStage + "]: start running");
                int waitCount = 0;

                while (true) {
                    try {
                        entry = (SimpleEntry) space.takeIfExists(template, txn,
                                JavaSpace.NO_WAIT);

                        /*
                         * entry = (SimpleEntry) space.readIfExists(template,
                         * txn, JavaSpace.NO_WAIT);
                         */
                    } catch (Exception e) {
                        parent.fail("[Worker]: Exception thrown while reading"
                                + " an entry", e);
                    }

                    if (entry == null) {
                        try {
                            sleep(WAIT_UNIT);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }

                        if (++waitCount > (MAX_WAIT / WAIT_UNIT)) {
                            synchronized(lock) {
                                completed = true;
                                lock.notifyAll();
                            }
                            break; // timeout
                        }
                    } else {

                        // got an entry
                        parent.pass("[Worker" + fromStage + "]: got an entry ("
                                + entry + ")");

                        // clear wait count
                        waitCount = 0;

                        // change ID
                        entry.stage = new Integer(toStage);

                        // write back to the space
                        try {
                            space.write(entry, txn, Lease.ANY);
                        } catch (Exception e) {
                            parent.fail("[Worker]: Exception thrown while"
                                    + " writing an entry", e);
                        }
                        parent.pass("[Worker" + fromStage + "]: wrote an entry"
                                + " (" + entry + ")");
                        synchronized(lock) {
                            if (++writeCount >= expectedEntries) {
                                completed = true;
                                lock.notifyAll();
                                break; // ends thread
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                workerFailed = true;
                workerFailMsg = e.toString();
            }
        }

        public int getWriteCount() {
            int retry = 0;
            while ((completed!=true)&&(retry<3)) {
                synchronized(lock) {
                    try {
                        lock.wait(MAX_WAIT);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                }
            }
            return writeCount;
        }
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = MultipleAccessTest : \n" + "tests multiple access.";
    }
}
