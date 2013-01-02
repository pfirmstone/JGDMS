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
package com.sun.jini.test.impl.outrigger.transaction;

import java.util.logging.Level;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

// All other imports
import net.jini.core.entry.*;
import net.jini.core.transaction.Transaction;


/**
 * This tests to see if conflict transactions for reads are handled properly
 * when aborted.  It writes an entry into the space, takes it under one
 * transaction, starts a read under another transaction, and then aborts the
 * first transaction.  This should cause the conflict to be cleared up, and
 * the read to return the entry.
 *
 * @author Ken Arnold
 */
public class AbortToReadTest extends TransactionTestBase
        implements com.sun.jini.constants.TimeConstants {
    private SimpleEntry entry;
    private long start;
    private static final int SLEEP_TIME = 5;
    private static final int BLOCK_TIME = 15;

    /*
     * Var to detect that BlockAndRead failed.
     * Has been added during porting.
     */
    private Exception failException = null;

    public void run() throws Exception {
        simpleSetup();

        // create the entry we will write, which is also our template
        entry = new SimpleEntry();
        entry.string = "matcher";
        entry.stage = new Integer(1);
        entry.id = new Integer(2);
        writeEntry(null, entry);
        logger.log(Level.FINE, "wrote " + entry);

        // create a template that won't match
        SimpleEntry nonmatch = new SimpleEntry();
        nonmatch.string = "nonmatcher";
        nonmatch.stage = entry.stage;
        nonmatch.id = entry.id;
        start = System.currentTimeMillis();
        timeLog("started");

        // take the entry under one transaction
        Transaction t1 = createTransaction();
        Entry takeResult = getSpace().take(entry, t1, 0);
        assertEquals(entry, takeResult, "taken");
        timeLog("entry taken under t1");

        // try to read it under the matching entry
        BlockAndRead shouldMatch = new BlockAndRead(entry);
        shouldMatch.start();
        shouldMatch.waitUntilReading();

        // try to read it under the nonmatching entry
        BlockAndRead shouldNotMatch = new BlockAndRead(nonmatch);
        shouldNotMatch.start();
        shouldNotMatch.waitUntilReading();

        // give time for reads to get well and truly blocked, then abort
        timeLog("sleeping " + SLEEP_TIME + " seconds");
        Thread.sleep(SLEEP_TIME * SECONDS);
        timeLog("aborting");
        t1.abort();

        // now wait to see what happens to the blocked operation
        timeLog("checking shouldMatch");
        shouldMatch.checkResult(true);
        timeLog("checking shouldNotMatch");
        shouldNotMatch.checkResult(false);
        timeLog("checked");

        if (failException != null) {
            throw failException;
        }
    }

    void timeLog(String msg) {
        logger.log(Level.INFO, "elapsed ");
        logger.log(Level.INFO, "" + (System.currentTimeMillis() - start));
        logger.log(Level.INFO, ": ");
        logger.log(Level.INFO, msg);
    }


    private class BlockAndRead extends Thread {
        private Entry tmpl;             // the
        private Transaction t2;         // my transaction
        private boolean startedToRead;  // have I started the read?
        private boolean readReturned;   // has the read returned?
        private Entry readResult;       // the return from the read

        BlockAndRead(Entry tmpl) throws TestException {
            this.tmpl = tmpl;
            t2 = createTransaction();
        }

        public void run() {
            try {
                timeLog("BlockAndRead: run()");
                synchronized (this) {
                    startedToRead = true;
                    notifyAll();
                }
                timeLog("BlockAndRead: starting to read: timeout is "
                        + BLOCK_TIME + " seconds, tmpl = " + tmpl);
                readResult = getSpace().read(tmpl, t2, BLOCK_TIME * SECONDS);
                timeLog("BlockAndRead: read returned");
                synchronized (this) {
                    readReturned = true;
                    notifyAll();
                }
            } catch (Exception e) {
                failException = e;
            }
        }

        synchronized void waitUntilReading() throws Exception {
	    while (!startedToRead) {
		wait();
	    }
        }

        synchronized void checkResult(boolean shouldBe) throws Exception {
	    while (!readReturned) {
		wait();
	    }
	    assertEquals(tmpl, readResult, "read", shouldBe);
        }
    }
}
