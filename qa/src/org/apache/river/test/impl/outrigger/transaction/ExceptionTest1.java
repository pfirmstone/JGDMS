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
import net.jini.space.JavaSpace;
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionException;
import net.jini.core.lease.Lease;
import net.jini.core.event.RemoteEventListener;
import java.rmi.RemoteException;


/**
 * Force to generate <tt>TransactionException</tt>s
 * on <tt>space.write</tt> and <tt>space.notify</tt> with a terminated
 * transaction.
 *
 * <ol>
 *   <li> Creates a transaction.
 *   <li> commit/abort the transaction.
 *   <li> Writes an entry or registers an interest with the terminated
 *        transaction. This causes <tt>TransactionException</tt>.
 * </ol>
 *
 * @author H.Fukuda
 */
public class ExceptionTest1 extends TransactionTestBase {

    // commonly used entry & template
    SimpleEntry wentry;
    SimpleEntry template;

    public void run() throws Exception {
        simpleSetup();

        // create an entry
        wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(7);

        // create a template
        template = new SimpleEntry();
        template.string = "foo";

        //                       isNotify  useAbort  useDummy
        testTransactionException(false,    true,     true);
        testTransactionException(false,    false,    true);
        testTransactionException(true,     true,     true);
        testTransactionException(true,     false,    true);
        testTransactionException(false,    true,     false);
        testTransactionException(false,    false,    false);
        testTransactionException(true,     true,     false);
        testTransactionException(true,     false,    false);
    }

    private void testTransactionException(boolean isNotify, boolean useAbort,
            boolean useDummy) throws Exception {

        // cleanup the space
        scrubSpaces();

        // create a transaction object
        Transaction txn = createTransaction();

        if (useDummy) {
            pass("step-0: write a dummy entry under a transaction");
	    getSpace().write(wentry, txn, Lease.FOREVER);
        }

        // abort/commit this transaction
        if (useAbort) {
            pass("step-1: abort transaction");
            abortTransaction(txn);
        } else {
            pass("step-1: commit transaction");
            commitTransaction(txn);
        }

        // write an entry using stale transaction
        boolean bomb = false;

        // dummy event listener
        RemoteEventListener listener = null;

        try {
            if (isNotify) {

                // create event listener (dummy)
                pass("step-2: create a event listener");

		listener = 
		    new SimpleEventListener(getConfig().getConfiguration());
                pass("step-3: register the listener with the terminated"
                        + " transaction");
                getSpace().notify(template, txn, listener, Lease.FOREVER, null);
                fail("TransactionException has not thrown while registering"
                        + " notify with a terminated txn.");
            } else {
                pass("step-2: write an entry with the terminated transaction");
                getSpace().write(wentry, txn, Lease.FOREVER);
                fail("TransactionException has not thrown while writing with a"
                        + " terminated txn.");
            }
        } catch (TransactionException te) {

            // expected exception

            if (isNotify) {
                pass("[Pass]: TransactionException has thrown while registering"
                        + " a notify, as expected.");
            } else {
                pass("[Pass]: TransactionException has thrown while writing, as"
                        + " expected.");
            }
        }
    }
}
