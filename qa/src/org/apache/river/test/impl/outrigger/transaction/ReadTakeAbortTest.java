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

import org.apache.river.qa.harness.Test;
import java.util.logging.Level;

// Test harness specific classes
import org.apache.river.qa.harness.TestException;

// All other imports
import java.rmi.*;
import java.io.File;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.TransactionFactory;
import net.jini.core.transaction.Transaction;
import net.jini.space.JavaSpace;
import org.apache.river.test.share.TestBase;


/**
 * Writes and entry, opens a transaction, reads and then takes
 * the entry under the transaction, aborts the transaction and then
 * checks to see if the entry is present.
 */
public class ReadTakeAbortTest extends TestBase implements Test {

    /** Space under test */
    protected JavaSpace space;

    /** Transaction Manager we are using */
    protected TransactionManager txnMgr;

    /** Entry to manipulate */
    private SimpleEntry entry = new SimpleEntry("King", 1, 1);

    public void run() throws Exception {
        specifyServices(new Class[] {
            TransactionManager.class, JavaSpace.class});
        space = (JavaSpace) services[1];
        txnMgr = (TransactionManager) services[0];
        final Lease el = space.write(entry, null, 1000 * 60 * 60);
        addOutriggerLease(el, true);
        final Transaction.Created txnHolder =
                TransactionFactory.create(txnMgr, 1000 * 60 * 60);
        final Transaction txn = txnHolder.transaction;
        addMahaloLease(txnHolder.lease, true);

        if (null == space.read(entry, txn, 0)) {
            throw new TestException(
                    "Could not perform initial read");
        }

        if (null == space.take(entry, txn, 0)) {
            throw new TestException( "Could not perform take");
        }
        txn.abort(60000);

        if (null == space.read(entry, null, 0)) {
            throw new TestException(
                    "Confirming read returned null value");
        }
    }

    /**
     * Return a String which describes this test
     */
    public String getDescription() {
        return "Test Name = ReadTakeAbortTest.";
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
