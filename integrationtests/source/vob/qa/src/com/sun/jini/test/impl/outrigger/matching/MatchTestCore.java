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
import java.util.Iterator;
import java.util.LinkedList;
import java.rmi.*;
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;
import net.jini.core.entry.UnusableEntryException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import com.sun.jini.test.share.TestBase;


/**
 * Base class for matching tests. Sets up an audited JavaSpace and
 * provides a number of convince methods.
 */
public abstract class MatchTestCore extends TestBase {

    /**
     * True if test should use <code>takeIfExists</code> and
     * <code>readIfExists</code> over <code>take</code> and
     * <code>read</code>
     * Not valid until <code>setup()</code> is called.
     * @see MatchTestBase#setup
     */
    protected boolean useIfExists = false;

    /**
     * The space under test
     */
    protected JavaSpace space;

    /**
     * Time in milliseconds to wait for a read or take.
     * Not valid until <code>setup()</code> is called.
     */
    protected long queryTimeOut; // 10 seconds
    private long tryShutdown;

    /**
     * Convince method for doing a read on the JavaSpace, uses the
     * proper form of read based on <code>useIfExists</code>.
     * Not valid until <code>setup()</code> is called.
     * @see MatchTestBase#setup
     * @see MatchTestBase#useIfExists
     */
    protected Entry spaceRead(Entry tmpl, Transaction txn, long timeout)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException {
        if (useIfExists) {
            return space.readIfExists(tmpl, txn, timeout);
        } else {
            return space.read(tmpl, txn, timeout);
        }
    }

    /**
     * Convince method for doing a take on the JavaSpace, uses the
     * proper form of take based on <code>useIfExists</code>.
     * Not valid until <code>setup()</code> is called.
     * @see MatchTestBase#setup
     * @see MatchTestBase#useIfExists
     */
    protected Entry spaceTake(Entry tmpl, Transaction txn, long timeout)
            throws UnusableEntryException, TransactionException,
            InterruptedException, RemoteException {
        if (useIfExists) {
            return space.takeIfExists(tmpl, txn, timeout);
        } else {
            return space.take(tmpl, txn, timeout);
        }
    }

    /**
     * Convince method for dumping a UnusableEntryException to the
     * log.
     * Not valid until <code>setup()</code> is called.
     * @see TestBase#setup
     */
    protected void dumpUnusableEntryException(UnusableEntryException e) {
        logger.log(Level.INFO, "   Partial Entry:" + e.partialEntry, e);
        logger.log(Level.INFO, "   Unusable Fields");

        if (e.unusableFields == null) {
            logger.log(Level.INFO, "      none");
        } else {
            for (int i = 0; i < e.unusableFields.length; i++) {
                logger.log(Level.INFO, "     " + e.unusableFields[i]);
            }
        }
        logger.log(Level.INFO, "   Nested Exceptions");

        if (e.nestedExceptions == null) {
            logger.log(Level.INFO, "      none");
        } else {
            for (int i = 0; i < e.nestedExceptions.length; i++) {
                logger.log(Level.INFO, "     " + e.nestedExceptions[i]);
                e.nestedExceptions[i].printStackTrace();
            }
        }
    }

    /**
     * Parse the command line args
     * <DL>
     * <DT>-use_IfExists<DD> Sets the test to use
     * <code>takeIfExists</code> and <code>readIfExists</code> over
     * <code>take</code> and <code>read</code>.  Defaults to off.
     *
     * <DT>-query_timeout <var>long</var> <DD> Set the timeout in
     * milliseconds for read and takes on the JavaSpace.  Defaults
     * to 10,000.
     * </DL>
     */
    protected void parse() throws Exception {
        super.parse();
        useIfExists = getConfig().getBooleanConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.use_IfExists", false);
        queryTimeOut = getConfig().getLongConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.query_timeout", 10000);
        tryShutdown = getConfig().getLongConfigVal("com.sun.jini.test.impl.outrigger."
                + "matching.tryShutdown", 0);
    }

    /**
     * Sets up the testing environment.
     *
     * @param args Arguments from the runner for setup.
     */
    public void setup(QAConfig config) throws Exception {
        super.setup(config);
        this.parse();

        // Get the space for testing, and rebind it to audited space
        specifyServices(new Class[] {
            JavaSpace.class});
        space = (JavaSpace) services[0];
    }

    /**
     * Derived classes should call this method when they have put the
     * space in the desired configuration, but before they have
     * started to check the configuration
     */
    protected void spaceSet() throws TestException {
        if (tryShutdown < 0) {
            return;
        }

        for (int i = 0; i < tryShutdown; i++) {
            try {
                shutdown(0);
            } catch (Throwable e) {
                fail("Shutdown Failed", e);
            }
        }
    }
}
