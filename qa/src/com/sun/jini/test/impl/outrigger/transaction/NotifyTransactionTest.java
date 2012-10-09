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
import net.jini.space.JavaSpace;
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import java.rmi.RemoteException;


/**
 * <code>NotifyTransactionTest</code> tests basic functions of event/notify
 * under transaction.
 *
 * Test steps are as follows:
 * <ol>
 *   <li> Event listener (A) regsiters an interest with a transaction.
 *   <li> Event listener (B) regsiters an interest without transaction.
 *   <li> Writes some entries with the transaction(1), without transaction(2),
 *        and with another transaction(3).
 *   <li> Check number of delivered events to the listener-A.
 *        The number should be the same with events related (1) and (2).
 *   <li> Check number of delivered events to the listener-B.
 *        The number should be the same with events related (2).
 * </ol>
 *
 * 
 */
public class NotifyTransactionTest extends TransactionTestBase {

    // Number of writes with each condition
    private final static int NUM_SAME = 4;
    private final static int NUM_NULL = 8;
    private final static int NUM_DIFF = 16;

    public void run() throws Exception {
        simpleSetup();

        // create entry and template
        SimpleEntry wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(4);
        SimpleEntry template = new SimpleEntry();
        template.string = "foo";

        // create transaction
        Transaction txn = createTransaction();

        // generate EventListeners
        SimpleEventListener tCatcher = null;
        SimpleEventListener nCatcher = null;
        EventRegistration tReg = null;
        EventRegistration nReg = null;

        try {
            tCatcher = new SimpleEventListener(getConfig().getConfiguration());
            nCatcher = new SimpleEventListener(getConfig().getConfiguration());
            tReg = space.notify(template, txn, tCatcher, Lease.FOREVER,
                    null);
            nReg = space.notify(template, null, nCatcher, Lease.FOREVER,
                    null);
	    // preparation added for completeness. No remote calls are made on these
	    tReg = (EventRegistration)
                   getConfig().prepare("test.outriggerEventRegistrationPreparer",
				       tReg);
	    nReg = (EventRegistration)
                   getConfig().prepare("test.outriggerEventRegistrationPreparer",
				       nReg);
        } catch (Exception e) {
            throw new TestException(
                    "Exception thrown while registering interests" + e);
        }

        /*
         * write entries
         * (a) write with the same transaction
         */
        wentry.stage = new Integer(1);

        for (int i = 0; i < NUM_SAME; i++) {
            writeEntry(txn, wentry);
        }

        // (b) write without transaction
        wentry.stage = new Integer(2);

        for (int i = 0; i < NUM_NULL; i++) {
            writeEntry(null, wentry);
        }

        // (c) write with other transaction
        Transaction txn2 = createTransaction();
        wentry.stage = new Integer(3);

        for (int i = 0; i < NUM_DIFF; i++) {
            writeEntry(txn2, wentry);
        }

        // give time for events to arrive
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {}

        // check results
        if (tCatcher.getNotifyCount(tReg) != (NUM_SAME + NUM_NULL)) {
            throw new TestException(
                    "generated events for txn  ["
                    + tCatcher.getNotifyCount(tReg)
                    + "] != expected events [" + (NUM_SAME + NUM_NULL)
                    + "]");
        } else {
            pass("Pass: transaction control");
        }

        if (nCatcher.getNotifyCount(nReg) != NUM_NULL) {
            throw new TestException(
                    "generated events for null ["
                    + nCatcher.getNotifyCount(nReg)
                    + "] != expected events [" + NUM_NULL + "]");
        } else {
            pass("Pass: without transaction");
        }
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = NotifyTransactionTest : \n"
                + "tests basic functions of event/notify under transaction.";
    }
}
