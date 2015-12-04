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
import org.apache.river.qa.harness.QAConfig;

// All other imports
import net.jini.space.*;
import net.jini.core.entry.*;
import net.jini.core.transaction.Transaction;
import net.jini.admin.Administrable;
import org.apache.river.admin.DestroyAdmin;
import org.apache.river.qa.harness.Test;
import java.rmi.UnmarshalException;


/**
 * This tests to see if conflicting transactions that are abandoned are
 * monitored.
 *
 * @author Ken Arnold
 */
public class BlockingOnDeadTransactionTest extends TransactionTestBase
        implements org.apache.river.constants.TimeConstants {
    private SimpleEntry entry;
    private long start;
    private boolean usePolling;
    private static final long SLEEP_TIME = 2 * MINUTES;
    private static final long BLOCK_TIME = 5 * MINUTES;
    private static final long POLL_TIME = 25 * SECONDS;

    /*
     * Var to detect that BlockAndTake failed.
     * Has been added during porting.
     */
    private Exception failException = null;

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for construct.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.parse();
        return this;
    }

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

        // read the entry under one transaction
        Transaction t1 = createTransaction();
        Entry readResult = getSpace().read(entry, t1, 0);
        assertEquals(entry, readResult, "read");
        timeLog("entry read under t1");

        // try to take it under the matching entry
        BlockAndTake shouldMatch = new BlockAndTake(entry);
        shouldMatch.start();
        shouldMatch.waitUntilTaking();

        // give time for takes to get well and truly blocked, then destroy
        timeLog("sleeping " + SLEEP_TIME);
        Thread.sleep(SLEEP_TIME);

        // read the entry under another transaction
        Transaction t2 = createTransaction();
        readResult = getSpace().read(entry, t2, 0);
        assertEquals(entry, readResult, "read");
        timeLog("entry read under t2");
        timeLog("destroying");

        try {
            DestroyAdmin admin = (DestroyAdmin) ((Administrable)
                    getTxmgr()).getAdmin();
            admin = (DestroyAdmin) getConfig().prepare("test.mahaloAdminPreparer",
                                                         admin);

            admin.destroy();
        } catch (UnmarshalException ue) {

            /*
             * Ignore. Can happen if the "destroy" thread actually
             * kills the service before we return from this call.
             */
            logger.log(Level.FINE, "Ignoring destroy call exeception <" + ue
                    + ">");
        }
        timeLog("checking shouldMatch");
        shouldMatch.checkResult(true);
        timeLog("checked");

        if (failException != null) {
            throw failException;
        }
    }

    /**
     * Parse our args.
     * <code>argv[]</code> is parsed to control various options
     * <DL>
     * <DT>-poll<DD> Sets the test to use serveral polls instead of a
     * blocking take.
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        usePolling = getConfig().getBooleanConfigVal("org.apache.river.qa.outrigger."
                + "transaction.BlockingOnDeadTransactionTest.poll", false);
    }

    void timeLog(String msg) {
        logger.log(Level.INFO, "elapsed " + (System.currentTimeMillis() - start) + ":" + msg);
    }


    private class BlockAndTake extends Thread {
        private Entry tmpl;             // the
        private Transaction txn;        // my transaction
        private boolean startedToTake;  // have I started the take?
        private boolean takeReturned;   // has the take returned?
        private Entry takeResult;       // the return from the take

        BlockAndTake(Entry tmpl) throws TestException {
            this.tmpl = tmpl;
            txn = createTransaction();
        }

        public void run() {
            try {
                timeLog("BlockAndTake: run()");
                synchronized (this) {
                    startedToTake = true;
                    notifyAll();
                }

                if (!usePolling) {
                    timeLog("BlockAndTake: starting to take: timeout is "
                            + BLOCK_TIME + ", tmpl = " + tmpl);
                    takeResult = getSpace().take(tmpl, txn, BLOCK_TIME);
                } else {
                    timeLog("BlockAndTake: starting to take: polling time is "
                            + BLOCK_TIME + ", tmpl = " + tmpl);
                    long endTime = System.currentTimeMillis() + BLOCK_TIME;
                    int pollNum = 0;

                    while (System.currentTimeMillis() < endTime) {
                        timeLog("poll #" + pollNum++);
                        takeResult = getSpace().takeIfExists(tmpl, txn,
                                JavaSpace.NO_WAIT);

                        if (takeResult != null) {
                            break;
                        } else {
                            Thread.sleep(POLL_TIME);
                        }
                    }
                }
                timeLog("BlockAndTake: take returned");
                synchronized (this) {
                    takeReturned = true;
                    notifyAll();
                }
            } catch (Exception e) {
                failException = e;
            }
        }

        synchronized void waitUntilTaking() throws Exception {
	    while (!startedToTake) {
		wait();
	    }
        }

        synchronized void checkResult(boolean shouldBe) throws Exception {
	    while (!takeReturned) {
		wait();
	    }
	    assertEquals(tmpl, takeResult, "take", shouldBe);
        }
    }
}
