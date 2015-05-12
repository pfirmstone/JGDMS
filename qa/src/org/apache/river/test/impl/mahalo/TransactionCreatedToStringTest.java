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
package org.apache.river.test.impl.mahalo;

import java.util.logging.Level;

import org.apache.river.constants.TimeConstants;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.server.TransactionManager;
import net.jini.core.transaction.server.TransactionManager;


import net.jini.core.transaction.UnknownTransactionException;
import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.TimeoutExpiredException;
import java.rmi.RemoteException;

public class TransactionCreatedToStringTest extends TxnMgrTestBase 
    implements TimeConstants 
{
    //
    // This should be long enough to sensibly run the test.
    // If the service doesn't grant long enough leases, then
    // we might have to resort to using something like the
    // LeaseRenewalManager to keep our leases current.
    //
    private final long DURATION = 5*MINUTES;
    
    static Lease noOpLease = new NoOpLease(10000L);
    
    static Transaction noOpTransaction = 
        new Transaction() {
            public void commit() 
                throws UnknownTransactionException, CannotCommitException,
                       RemoteException
                {}

            public void commit(long waitFor) 
                throws UnknownTransactionException, CannotCommitException,
                       TimeoutExpiredException, RemoteException
            {}

            public void abort()
                throws UnknownTransactionException, CannotAbortException,
                       RemoteException
            {}

            public void abort(long waitFor)
               throws UnknownTransactionException, CannotAbortException,
                      TimeoutExpiredException, RemoteException
            {}
               
            public Transaction.Created 
               create(TransactionManager mgr, long leaseTime)
               throws UnknownTransactionException, CannotJoinException,
                       LeaseDeniedException, RemoteException
            { return 
                  new Transaction.Created(
                      noOpTransaction, noOpLease); }
                       
            public Transaction.Created create(long leaseTime)
                throws UnknownTransactionException, CannotJoinException,
                       LeaseDeniedException, RemoteException    
            { return 
                  new Transaction.Created(
                      (Transaction)null, noOpLease); }            
    };
    
    public void run() throws Exception {
        /*
         * Mainly checking to see if the toString() method throws 
         * an exception (e.g. NullPointerException) with "bad" values.
         */
	logger.log(Level.INFO, "Starting up " + this.getClass().toString()); 
        Transaction.Created nullTxn = 
            new Transaction.Created((Transaction)null, noOpLease); 
        logger.log(Level.INFO, "nullTxn: " + nullTxn);
        Transaction.Created nullLease = 
            new Transaction.Created(noOpTransaction, null); 
        logger.log(Level.INFO, "nullLease: " + nullLease);
        Transaction.Created nullTxnLease = 
            new Transaction.Created((Transaction)null, null); 
        logger.log(Level.INFO, "nullTxnLease: " + nullTxnLease);
        Transaction.Created nonNullTxnLease = 
            new Transaction.Created(noOpTransaction, noOpLease); 
        logger.log(Level.INFO, "nonNullTxnLease: " + nonNullTxnLease);
}

    /**
     * Invoke parent's construct and parser
     * @exception QATestException will usually indicate an "unresolved"
     *  condition because at this point the test has not yet begun.
     */
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	parse();
        return this;
    }
}
