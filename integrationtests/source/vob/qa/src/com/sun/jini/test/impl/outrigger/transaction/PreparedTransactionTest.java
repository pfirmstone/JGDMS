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
import net.jini.core.entry.*;
import net.jini.core.transaction.*;
import net.jini.core.transaction.server.*;
import com.sun.jini.constants.TxnConstants;
import com.sun.jini.test.share.TesterTransaction;
import com.sun.jini.test.share.TesterTransactionManager;


/**
 * This tests to see if a prepared transaction that is hanging
 * around after a crash behaves properly.
 * <dl>
 *
 * <dt><code>-abort</code>
 * <dd>Abort the transaction (default is to commit it).
 *
 * <dt><code>-wait</code>
 * <dd>Wait -- set the transaction status, but let the particpant query
 * for the state instead of sending it
 *
 * <dt><code>-active</code>
 * <dd>Active -- don't send the prepare method -- go straight to
 * <code>COMMITED</code> or <code>ABORTED</code>.  This implies
 * <code>-abort</code>, since any active transaction is aborted
 * by a crash.
 *
 * <dt><code>-throw_remote</code> <i>cnt</i>
 * <dd>Throw <code>RemoteException</code> <i>cnt</i> times
 * (implies <code>-wait</code>)
 *
 * </dl>
 *
 * @author Ken Arnold
 */
public abstract class PreparedTransactionTest extends TransactionTestBase
        implements TransactionConstants, com.sun.jini.constants.TimeConstants {

    /** Should we abort the transaction? */
    private boolean abort;

    /** Should we wait, letting the particpant query? */
    private boolean wait;

    /** Don't even send the prepared() message -- go straight from ACTIVE. */
    private boolean active;

    /**
     * Number of times that <code>getState</code> should throw
     * <code>RemoteException</code>.
     */
    private int throwRemote;

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
     * Parse non-generic option(s).
     */
    protected void parse() throws Exception {
        super.parse();
        abort = getConfig().getBooleanConfigVal("com.sun.jini.qa.outrigger."
                + "transaction.PreparedTransactionTest.abort", false);
        wait = getConfig().getBooleanConfigVal("com.sun.jini.qa.outrigger."
                + "transaction.PreparedTransactionTest.wait", false);
        active = getConfig().getBooleanConfigVal("com.sun.jini.qa.outrigger."
                + "transaction.PreparedTransactionTest.active", false);
        throwRemote = getConfig().getIntConfigVal("com.sun.jini.qa.outrigger."
                + "transaction.PreparedTransactionTest.throw_remote", 0);

        if (throwRemote > 0) {

            // implies -wait
            wait = true;
        }

        if (active) {

            // implies -abort
            abort = true;
        }
    }

    public void run() throws Exception {
        spaceOnlySetup();
        TesterTransactionManager mgr = new TesterTransactionManager();
        TesterTransaction tt = mgr.create();

        // an entry that should be there at the start
        SimpleEntry toTake = new SimpleEntry("toTake", 1, 13);
        writeEntry(null, toTake);
        logger.log(Level.INFO, "wrote entry " + toTake);

        // the entry written under the transaction
        SimpleEntry written = new SimpleEntry("tester", 17, 29);
        logger.log(Level.INFO, "wrote entry " + written);
        writeEntry(tt.transaction, written);
        tt.assertParticipants(1); // just to be sure
        Entry taken = space.take(toTake, tt.transaction, 0);
        assertEquals(taken, toTake, "reading 'toTake' entry");

        if (!active) {
            tt.sendPrepare(); // get the transaction prepared

            if (tt.localGetState() != PREPARED) {
                throw new TestException(
                        "state is "
                        + TxnConstants.getName(tt.localGetState())
                        + ", should be " + TxnConstants.getName(PREPARED));
            }
        }
        shutdown(0); // shut down the space

        /*
         * Only do this test if we're not active -- active txns should
         * be effectively aborted at this point, so they have no stage
         * that exists after a shutdown and before the transaction
         * completion
         */
        if (!active) {

            /*
             * try to see transacted stuff under a null transaction:
             * should fail
             */
            canSee(toTake, null, "txn not yet completed");
            canSee(written, null, "txn not yet completed");

            if (wait) {

                /*
                 * sleep long enough for the 15-second retries plus
                 * 5 for slop
                 */
                long sleepTime = (15 * SECONDS * (throwRemote + 1)
                        + 5 * SECONDS);
                tt.setState(abort ? ABORTED : COMMITTED);
                logger.log(Level.INFO, "transaction state set to "
                        + TxnConstants.getName(tt.localGetState())
                        + ", sleeping " + sleepTime);

                if (throwRemote > 0) {
                    tt.getStateFailCnt(throwRemote);
                }

                // give the participant a chance to ask
                Thread.sleep(sleepTime);
            } else {
                if (abort) {
                    tt.sendAbort();
                } else {
                    tt.sendCommit();
                }
            }
        }

        if (abort) {
            canSee(toTake, toTake, "txn aborted");
            canSee(written, null, "txn aborted");
        } else {
            canSee(toTake, null, "txn committed");
            canSee(written, written, "txn committed");
        }
    }

    private void canSee(Entry tmpl, Entry shouldMatch, String desc)
            throws Exception {
        Entry e = space.read(tmpl, null, 0);
        assertEquals(e, shouldMatch, desc);
    }
}
