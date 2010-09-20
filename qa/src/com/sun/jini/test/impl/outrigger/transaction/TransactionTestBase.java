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
import net.jini.admin.Administrable;
import net.jini.space.JavaSpace;
import net.jini.core.entry.Entry;
import net.jini.entry.AbstractEntry;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.server.TransactionManager;
import com.sun.jini.outrigger.JavaSpaceAdmin;
import com.sun.jini.outrigger.AdminIterator;
import com.sun.jini.test.share.TestBase;


/**
 * This class provides a common facilities of tests in this package.
 *
 * @author H.Fukuda
 */
public abstract class TransactionTestBase extends TestBase {
    protected boolean verbose = true;
    protected boolean disableTxn = false;

    // for the convenience of simple tests
    protected JavaSpace space;
    protected JavaSpaceAdmin admin;
    protected TransactionManager txmgr;

    // for two space test
    protected JavaSpace[] spaces = new JavaSpace[2];

    /**
     * Sets up the testing environment.
     *
     * @param config Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.parse();
    }

    /**
     * Parse non-generic option(s).
     */
    protected void parse() throws Exception {
        super.parse();

        /*
         * schuldy 5-Nov-1999 bug 4288354 - always be verbose
         * verbose = line.getBoolean("verbose");
         */
        disableTxn = getConfig().getBooleanConfigVal("com.sun.jini.qa.outrigger."
                + "transaction.disableTxn", false);
    }

    /**
     * Makes sure the designated service is activated by getting its admin
     */
    protected void prep(int serviceIndex) throws TestException {
        try {
            ((Administrable) services[serviceIndex]).getAdmin();
        } catch (Throwable e) {
            throw new TestException("Could not pre-activate service"
                    + " under test", e);
        }
    }

    protected void spaceOnlySetup() throws TestException {

        // setup servers
        specifyServices(new Class[] {
            JavaSpace.class});
        prep(0);
        space = spaces[0] = (JavaSpace) services[0];
    }

    protected void simpleSetup() throws TestException {

        // setup servers
        specifyServices(new Class[] {
            TransactionManager.class, JavaSpace.class});
        prep(0);
        prep(1);
        txmgr = (TransactionManager) services[0];
        space = spaces[0] = (JavaSpace) services[1];
    }

    protected void twoSpaceSetup() throws TestException {

        // setup servers
        specifyServices(new Class[] {
            TransactionManager.class, JavaSpace.class, JavaSpace.class});
        prep(0);
        prep(1);
        prep(2);
        txmgr = (TransactionManager) services[0];
        spaces[0] = (JavaSpace) services[1];
        spaces[1] = (JavaSpace) services[2];
    }

    protected void scrubSpaces() throws TestException {
        for (int i = 0; i < 2; i++) {
            if (spaces[i] != null) {
                super.scrubSpace(spaces[i]);
            }
        }
    }

    protected void shutdownTxnMgr() throws TestException {
        try {
            shutdown(0);
        } catch (Throwable t) {
            throw new TestException("Failed to shutdown Transaction manager.",
                    t);
        }
    }

    /**
     * Writes an entry with a specified transaction into specified JavaSpace
     *
     * @param txn transaction object to be used to write
     * @param entry entry object to be written
     */
    protected void writeEntry(int id, Transaction txn, Entry entry)
            throws TestException {
        try {
            Lease l = spaces[id].write(entry, txn, Lease.FOREVER);
            addOutriggerLease(l, true);
        } catch (Exception e) {
            throw new TestException("Can't write an entry with a transaction",
                    e);
        }
    }

    /**
     * Writes an entry with a specified transaction into specified JavaSpace
     *
     * @param txn transaction object to be used to write
     * @param entry entry object to be written
     */
    protected void writeEntry(Transaction txn, Entry entry)
            throws TestException {
        try {
            Lease l = space.write(entry, txn, Lease.FOREVER);
            addOutriggerLease(l, true);
        } catch (Exception e) {
            throw new TestException("Can't write an entry with a transaction",
                    e);
        }
    }

    /**
     * Creates a transaction object using default transaction manager.
     *
     * @return created transaction obect
     */
    protected Transaction createTransaction() throws TestException {

        // Create a Transaction object
        Transaction.Created tc = null;

        try {
            tc = TransactionFactory.create(txmgr, Lease.FOREVER);
            addMahaloLease(tc.lease, true);
        } catch (Exception e) {
            throw new TestException("Can't make transaction object", e);
        }
        return tc.transaction;
    }

    /**
     * Commits the transaction.
     * If any exception thrown by this commit operation, test will be
     * terminated.
     *
     * @param txn transaction object
     */
    protected void commitTransaction(Transaction txn) throws TestException {
        try {
            txn.commit(10000); // up to ten seconds to settle all participants
        } catch (Exception e) {
            throw new TestException("Commit transaction failed", e);
        }
    }

    /**
     * Aborts the transaction.
     * If any exception thrown by this abort operation, test will be terminated.
     *
     * @param txn transaction object
     */
    protected void abortTransaction(Transaction txn) throws TestException {
        try {
            txn.abort(10000); // up to ten seconds to settle all participants
        } catch (Exception e) {
            throw new TestException("Abort transaction failed", e);
        }
    }

    /**
     * Creates output string if test fails.
     *
     * @param ope operation wrapper object
     * @param msg error description
     */
    public void failOperation(SpaceOperation ope, String msg)
            throws TestException {
        fail("[" + ope + "]: " + msg);
    }

    public void passOperation(SpaceOperation ope) {
        if (verbose) {
            logger.log(Level.FINE, "[" + ope + "]: Pass");
        }
    }

    public void fail(String msg, Throwable e) throws TestException {
        throw new TestException(msg, e);
    }

    public void pass(String msg) {
        if (verbose) {
            logger.log(Level.FINE, msg);
        }
    }

    /**
     * Return an array of String whose elements comprise the
     * categories to which this test belongs.
     */
    public String[] getCategories() {
        return new String[] {
            "outrigger" };
    }
}
