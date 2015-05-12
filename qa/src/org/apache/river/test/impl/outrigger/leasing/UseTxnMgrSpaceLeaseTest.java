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
package org.apache.river.test.impl.outrigger.leasing;

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
import net.jini.core.transaction.server.TransactionManager;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;

// Shared classes
import org.apache.river.test.share.TestBase;
import org.apache.river.test.share.UninterestingEntry;


/**
 * Tests binding between leases and transactions in TransactionManagers.
 */
public class UseTxnMgrSpaceLeaseTest extends LeaseUsesTestBase {
    final private Entry aEntry = new UninterestingEntry();
    private volatile Transaction resource;
    private volatile JavaSpace space;

    protected Lease acquireResource() throws TestException {
        specifyServices(new Class[] {
            TransactionManager.class, JavaSpace.class});
        prep(0);
        Lease lease = null;

        try {
            space = (JavaSpace) services[1];
            final Transaction.Created txnHolder =
                    TransactionFactory.create(((TransactionManager)
                    services[0]), durationRequest);
            resourceRequested();
            resource = txnHolder.transaction;
            lease = (Lease) getConfig().prepare("test.mahaloLeasePreparer",
						txnHolder.lease);
        } catch (Exception e) {
            throw new TestException("creating transaction", e);
        }
        return lease;
    }

    protected boolean isAvailable() throws TestException {
        try {
            addOutriggerLease(space.write(aEntry, resource, 1000), true);
        } catch (TransactionException e) {

            // This is ok...probably means it is a bad transactions
            return false;
        } catch (Exception e) {
            throw new TestException("Testing for availability", e);
        }
        return true;
    }
}
