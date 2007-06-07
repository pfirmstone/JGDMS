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

import net.jini.core.transaction.server.NestableTransactionManager;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import java.rmi.RemoteException;

/**
 * Interface for classes representing nestable transactions returned by
 * <code>NestableTransactionManager</code> servers for use with transaction
 * participants that implement the default transaction semantics.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see Transaction
 * @see net.jini.core.transaction.server.NestableTransactionManager
 * @see TransactionFactory
 *
 * @since 1.0
 */
public interface NestableTransaction extends Transaction {

    /** Class that holds return values from create methods. */
    public static class Created implements java.io.Serializable {
	static final long serialVersionUID = -2979247545926318953L;

	/**
	 * The transaction.
	 * @serial
	 */
        public final NestableTransaction transaction;
	/**
	 * The lease.
	 * @serial
	 */
        public final Lease lease;

	/**
	 * Simple constructor.
	 *
	 * @param transaction the transaction created
	 * @param lease the lease granted
	 */
	public Created(NestableTransaction transaction, Lease lease) {
	    this.transaction = transaction;
	    this.lease = lease;
        }
        
        // inherit javadoc
        public String toString() {
            return this.getClass().getName() 
                + "[lease=" + lease + ", transaction=" + transaction + "]";
        }
    }

    /**
     * Create a new nested transaction, with the current transaction as
     * parent, managed by the given transaction manager.
     *
     * @param mgr the transaction manager to use for this transaction
     * @param leaseTime the requested lease time for the transaction
     * @return the created transaction and the lease granted
     * 
     * @throws UnknownTransactionException if the parent transaction 
     *         is unknown to the parent transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is no longer active and its state has been 
     *         discarded by the manager.
     * @throws CannotJoinException if the parent transaction is known 
     *         to the manager but is no longer active.
     * @throws LeaseDeniedException if this manager is unwilling to 
     *         grant the requested lease time
     * @throws RemoteException if there is a communication error
     */
    Created create(NestableTransactionManager mgr, long leaseTime)
        throws UnknownTransactionException, CannotJoinException,
	       LeaseDeniedException, RemoteException;

    /**
     * Create a new nested transaction, with the current transaction as
     * parent, managed by the same transaction manager as the current
     * transaction.
     *
     * @param leaseTime the requested lease time for the transaction
     * @return the created transaction and the lease granted
     * 
     * @throws UnknownTransactionException if the parent transaction 
     *         is unknown to the parent transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is no longer active and its state has been 
     *         discarded by the manager.
     * @throws CannotJoinException if the parent transaction is known 
     *         to the manager but is no longer active.
     * @throws LeaseDeniedException if this manager is unwilling to 
     *         grant the requested lease time
     * @throws RemoteException if there is a communication error

     */
    Created create(long leaseTime)
        throws UnknownTransactionException, CannotJoinException,
	       LeaseDeniedException, RemoteException;
}
