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
package com.sun.jini.test.impl.outrigger.leasing;

import java.util.logging.Level;

// java classes
import java.rmi.*;

// jini classes
import net.jini.space.JavaSpace;
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.TransactionException;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;

// Test harness specific classes
import com.sun.jini.qa.harness.TestException;

// Shared classes
import com.sun.jini.test.share.TestBase;
import com.sun.jini.test.share.UninterestingEntry;


/**
 * Tests binding between leases and transactions in TransactionManagers.
 */
public class UseTxnMgrLeaseTest extends LeaseUsesTestBase {
    final private Entry aEntry = new UninterestingEntry();
    private ServerTransaction resource;

    protected Lease acquireResource() throws TestException {
        specifyServices(new Class[] {
            TransactionManager.class});
        prep(0);
        Lease lease = null;

        try {
            final Transaction.Created txnHolder =
                    TransactionFactory.create(((TransactionManager)
                    services[0]), durationRequest);
            resourceRequested();
            resource = (ServerTransaction) txnHolder.transaction;
            lease = (Lease) getConfig().prepare("test.mahaloLeasePreparer",
						txnHolder.lease);
        } catch (Exception e) {
            throw new TestException("creating transaction", e);
        }
        return lease;
    }

    protected boolean isAvailable() throws TestException {
        int state;

        try {
            state = resource.getState();
        } catch (UnknownTransactionException e) {

            // This is ok...probably means it been canceled
            logger.log(Level.INFO, "isAvailable: false (" + e + ")");
            return false;
        } catch (Exception e) {
            logger.log(Level.INFO, "isAvailable: exception! (" + e+")");
            throw new TestException("Testing for availability", e);
        }

        if (state == TransactionConstants.ABORTED) {

            // We treat aborted transaction as unavailable
            logger.log(Level.INFO, "isAvailable: false (state = " + state + ")");
            return false;
        } else {
            // logger.log(Level.INFO, "isAvailable: true (state = " + state + ")");
            return true;
        }
    }
}
