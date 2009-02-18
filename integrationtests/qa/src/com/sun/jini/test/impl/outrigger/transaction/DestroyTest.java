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
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.lease.Lease;
import net.jini.admin.Administrable;
import com.sun.jini.admin.DestroyAdmin;
import java.rmi.RemoteException;


/**
 * <code>RecoveryTest</code> tests basic transactional features.
 * This test writes an entry under a transaction, then
 * <ul>
 *   <li> try to read/take it with no transaction, and make sure it fails.
 *   <li> try to read/take it with the same transaction, and make sure it works.
 *   <li> try to read/take it with another transaction, and make sure it fails.
 * </ul>
 *
 * Test steps are as follows:
 * <ol>
 *   <li> Writes an entry under a transaction.
 *   <li> Read/take it with no/same/different transaction.
 *   <li> Check results.
 * </ol>
 *
 * @author H.Fukuda
 */
public class DestroyTest extends TransactionTestBase {

    public void run() throws Exception {
        simpleSetup();

        // create an entry
        SimpleEntry wentry = new SimpleEntry();
        wentry.string = "foo";
        wentry.stage = new Integer(1);
        wentry.id = new Integer(10);

        // create a template
        SimpleEntry template = new SimpleEntry();
        template.string = "foo";

        // create a transaction object to write
        pass("step-1: create a transaction object");
        Transaction txn = null;

	Transaction.Created tc = TransactionFactory.create(txmgr,
							   Lease.FOREVER);
	txn = tc.transaction;

        // write an entry under the transaction
        pass("step-2: write an entry with the transaction.");
        writeEntry(txn, wentry);

        // shutdown transaction manager.
        pass("step-3: destroy transaction manager.");

	DestroyAdmin admin = (DestroyAdmin) ((Administrable)
					     txmgr).getAdmin();
	admin = (DestroyAdmin) getConfig().prepare("test.mahaloAdminPreparer",
						   admin);
	
	admin.destroy();

        // wait for a while
	int destroyDelay = getConfig().getIntConfigVal(
	        "com.sun.jini.qa.harness.destroy.delay", 10);
	
        try {
            logger.log(Level.INFO, "Destroying worked, sleeping for "
                    + destroyDelay + " seconds...");
            Thread.sleep(destroyDelay * 1000);
            logger.log(Level.INFO, "...awake");
        } catch (InterruptedException e) {}

        // Check abort/commit cause RemoteException
        try {
            txn.commit();
	    throw new TestException("Expected exception was NOT thrown.");
        } catch (RemoteException e) {
            pass("step-4: RemoteException was thrown, as expected.");
        }
    }
}
