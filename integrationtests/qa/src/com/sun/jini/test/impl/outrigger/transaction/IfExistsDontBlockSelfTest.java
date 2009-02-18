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
import com.sun.jini.qa.harness.QAConfig;

// All other imports
import java.rmi.*;
import java.io.File;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.Transaction;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import com.sun.jini.test.share.TestBase;


/**
 * Write an entry take it under a transaction and then do a
 * <code>readIfExits</code>/<code>takeIfExists</code> under the same
 * transaction.  Final query should return almost immediately.
 */
public class IfExistsDontBlockSelfTest extends TestBase {

    /** Space under test */
    protected JavaSpace space;

    /** Transaction Manager we are using */
    protected TransactionManager txnMgr;

    /** True if we should make the final query a <code>readIfExists</code> */
    private boolean useRead;

    /** Timeout period for final query */
    private long timeOut;

    /** The max time the final queuy should take */
    private long queryLimit;

    /** Entry to manipulate */
    private SimpleEntry entry = new SimpleEntry("King", 1, 1);

    /**
     * Sets up the testing environment.
     *
     * @param args Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.parse();
    }

    /**
     * Parse command line args
     * <DL>
     * <DT>-useRead <DD> Use a <code>readIfExists</code> insted of a
     * <code>takeIfExists</code> call to see if the transaction
     * blocks itself.
     *
     * <DT>-timeOut <var>long</var><DD> Length of time for
     * final query
     * </DL>
     *
     * <DT>-queryLimit <var>long</var><DD> The max length
     * of time the final query should take for a pass.
     */
    protected void parse() throws Exception {
        super.parse();
        useRead = getConfig().getBooleanConfigVal("com.sun.jini.qa.outrigger."
                + "transaction.IfExistsDontBlockSelfTest.useRead", false);
        timeOut = getConfig().getLongConfigVal("com.sun.jini.qa.outrigger."
                + "transaction.IfExistsDontBlockSelfTest.timeOut", 60000);
        queryLimit = getConfig().getLongConfigVal("com.sun.jini.qa.outrigger."
                + "transaction.IfExistsDontBlockSelfTest.queryLimit", 1000);
    }

    public void run() throws Exception {
        specifyServices(new Class[] {
            TransactionManager.class, JavaSpace.class});
        space = (JavaSpace) services[1];
        txnMgr = (TransactionManager) services[0];
        final Lease el = space.write(entry, null, 1000 * 60 * 60);
        logger.log(Level.INFO, "Wrote entry");
        addOutriggerLease(el, true);
        final Transaction.Created txnHolder =
                TransactionFactory.create(txnMgr, 1000 * 60 * 60);
        final Transaction txn = txnHolder.transaction;
        addMahaloLease(txnHolder.lease, true);

        if (null == space.take(entry, txn, 0)) {
            throw new TestException(
                    "Could not perform initial take");
        }
        logger.log(Level.INFO, "took entry under txn");
        Entry rslt;
        final long start = System.currentTimeMillis();

        if (useRead) {
            rslt = space.readIfExists(entry, txn, timeOut);
        } else {
            rslt = space.takeIfExists(entry, txn, timeOut);
        }
        final long interval = System.currentTimeMillis() - start;

        if (rslt != null) {
            throw new TestException(
                    "Final query returned a result");
        }

        if (interval > queryLimit) {
            throw new TestException(
                    "Final query took " + interval
                    + "ms, this is too long");
        }
        logger.log(Level.INFO, (useRead ? "readIfExists" :
                "takeIfExists") + " returned null");
    }

    /**
     * Return an array of String whose elements comprise the
     * categories to which this test belongs.
     */
    public String[] getCategories() {
        return new String[] {
            "outrigger" };
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = IfExistsDontBlockSelfTest : \n"
                + "Write an entry take it under a transaction and then do a\n"
                + "readIfExits/takeIfExists under the same transaction.";
    }
}
