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
import com.sun.jini.qa.harness.Test;
import com.sun.jini.test.share.TestBase;


/**
 * This class provides a common facilities of tests in this package.
 *
 * @author H.Fukuda
 */
public abstract class TransactionTestBase extends TestBase implements Test {
    private boolean verbose = true;
//    private boolean disableTxn = false;

    // for the convenience of simple tests
    private JavaSpace space;
    private TransactionManager txmgr;

    // for two space test
    private JavaSpace[] spaces = new JavaSpace[2];

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

//    /**
//     * Parse non-generic option(s).
//     */
//    protected void parse() throws Exception {
//        super.parse();
//
//        /*
//         * schuldy 5-Nov-1999 bug 4288354 - always be verbose
//         * verbose = line.getBoolean("verbose");
//         */
//        setDisableTxn(getConfig().getBooleanConfigVal("com.sun.jini.qa.outrigger."
//                 + "transaction.disableTxn", false));
//    }

    /**
     * Makes sure the designated service is activated by getting its admin
     */
    protected void prep(int serviceIndex) throws TestException {
        try {
            ((Administrable) services[serviceIndex]).getAdmin();
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            throw new TestException("Could not pre-activate service"
                    + " under test", e);
        }
    }

    protected void spaceOnlySetup() throws TestException {

        // construct servers
        specifyServices(new Class[] {
            JavaSpace.class});
        prep(0);
        setSpace(getSpaces()[0] = (JavaSpace) services[0]);
    }

    protected void simpleSetup() throws TestException {

        // construct servers
        specifyServices(new Class[] {
            TransactionManager.class, JavaSpace.class});
        prep(0);
        prep(1);
        setTxmgr((TransactionManager) services[0]);
        setSpace(getSpaces()[0] = (JavaSpace) services[1]);
    }

    protected void twoSpaceSetup() throws TestException {

        // construct servers
        specifyServices(new Class[] {
            TransactionManager.class, JavaSpace.class, JavaSpace.class});
        prep(0);
        prep(1);
        prep(2);
        setTxmgr((TransactionManager) services[0]);
        getSpaces()[0] = (JavaSpace) services[1];
        getSpaces()[1] = (JavaSpace) services[2];
    }

    protected void scrubSpaces() throws TestException {
        for (int i = 0; i < 2; i++) {
            if (getSpaces()[i] != null) {
                super.scrubSpace(getSpaces()[i]);
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
            Lease l = getSpaces()[id].write(entry, txn, Lease.FOREVER);
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
            Lease l = getSpace().write(entry, txn, Lease.FOREVER);
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
            tc = TransactionFactory.create(getTxmgr(), Lease.FOREVER);
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
        if (isVerbose()) {
            logger.log(Level.FINE, "[" + ope + "]: Pass");
        }
    }

    public void fail(String msg, Throwable e) throws TestException {
        throw new TestException(msg, e);
    }

    public void pass(String msg) {
        if (isVerbose()) {
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

    /**
     * @return the verbose
     */
    protected boolean isVerbose() {
        return verbose;
    }

//    /**
//     * @param disableTxn the disableTxn to set
//     */
//    protected void setDisableTxn(boolean disableTxn) {
//        this.disableTxn = disableTxn;
//    }

    /**
     * @return the space
     */
    protected JavaSpace getSpace() {
        return space;
    }

    /**
     * @param space the space to set
     */
    protected void setSpace(JavaSpace space) {
        this.space = space;
    }

    /**
     * @return the txmgr
     */
    protected TransactionManager getTxmgr() {
        return txmgr;
    }

    /**
     * @param txmgr the txmgr to set
     */
    protected void setTxmgr(TransactionManager txmgr) {
        this.txmgr = txmgr;
    }

    /**
     * @return the spaces
     */
    protected JavaSpace[] getSpaces() {
        return spaces;
    }

}
