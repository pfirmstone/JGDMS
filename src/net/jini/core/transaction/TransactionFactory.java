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
package net.jini.core.transaction;

import net.jini.core.transaction.server.*;
import net.jini.core.lease.LeaseDeniedException;
import java.rmi.RemoteException;

/**
 * Factory methods for creating top-level transactions.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public class TransactionFactory {

    private TransactionFactory() {}

    /**
     * Create a new top-level transaction.  If the specified transaction
     * manager supports nested transactions, then the returned transaction
     * can be cast to a <code>NestableTransaction</code>.
     *
     * @param mgr the transaction manager to use for this transaction
     * @param leaseTime the requested lease time for the transaction
     * @return the created transaction and the lease granted
     *
     * @throws LeaseDeniedException if this manager is unwilling to 
     *         grant the requested lease time
     * @throws RemoteException if a communication error occurs.
     */
    public static Transaction.Created create(TransactionManager mgr,
					     long leaseTime) 
        throws LeaseDeniedException, RemoteException
    {
        TransactionManager.Created rawTxn = mgr.create(leaseTime);
	Transaction transaction;
	if (mgr instanceof NestableTransactionManager)
	    transaction = new NestableServerTransaction(
					     (NestableTransactionManager)mgr,
					     rawTxn.id, null);
	else
	    transaction = new ServerTransaction(mgr, rawTxn.id);
        return new Transaction.Created(transaction, rawTxn.lease);
    }

    /**
     * Create a new top-level transaction, under which nested transactions
     * can be created.
     *
     * @param mgr the transaction manager to use for this transaction
     * @param leaseTime the requested lease time for the transaction
     * @return the created transaction and the lease granted
     *
     * @throws LeaseDeniedException if this manager is unwilling to 
     *         grant the requested lease time
     * @throws RemoteException if a communication error occurs.
     */
    public static
    NestableTransaction.Created create(NestableTransactionManager mgr,
				       long leaseTime) 
        throws LeaseDeniedException, RemoteException
    {
        TransactionManager.Created rawTxn = mgr.create(leaseTime);
        return new NestableTransaction.Created(
				    new NestableServerTransaction(mgr,
								  rawTxn.id,
								  null),
				    rawTxn.lease);
    }
}
