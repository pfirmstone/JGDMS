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
package net.jini.core.transaction.server;

import net.jini.core.transaction.*;
import net.jini.core.lease.LeaseDeniedException;
import java.rmi.RemoteException;

/**
 * The interface used for managers of the two-phase commit protocol for
 * nestable transactions.  All nestable transactions must have a
 * transaction manager that runs this protocol.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see NestableServerTransaction
 * @see TransactionParticipant
 *
 * @since 1.0
 */
public interface NestableTransactionManager extends TransactionManager {
    /**
     * Begin a nested transaction, with the specified transaction as parent.
     *
     * @param parentMgr the manager of the parent transaction
     * @param parentID the id of the parent transaction
     * @param lease the requested lease time for the transaction
     * @return the created transaction and the lease granted
     * 
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws CannotJoinException if the transaction is known 
     *         to the manager but is no longer active.
     * @throws LeaseDeniedException if this manager is unwilling to 
     *         grant the requested lease time
     * @throws RemoteException if there is a communication error
     *
     */
    TransactionManager.Created create(NestableTransactionManager parentMgr,
				      long parentID, long lease)
        throws UnknownTransactionException, CannotJoinException,
	       LeaseDeniedException, RemoteException;
 
    /**
     * Promote the listed participants into the specified transaction.
     * This method is for use by the manager of a subtransaction when the
     * subtransaction commits.  At this point, all participants of the
     * subtransaction must become participants in the parent transaction.
     * Prior to this point, the subtransaction's manager was a participant
     * of the parent transaction, but after a successful promotion it need
     * no longer be one (if it was not itself a participant of the
     * subtransaction), and so it may specify itself as a participant to
     * drop from the transaction.  Otherwise, participants should not be
     * dropped out of transactions.  For each promoted participant, the
     * participant's crash count is stored in the corresponding element of
     * the <code>crashCounts</code> array.
     *
     * @param id the id of the parent transaction
     * @param parts the participants being promoted to the parent
     * @param crashCounts the crash counts of the participants
     * @param drop the manager to drop out, if any
     *
     * @throws CrashCountException if the crash count provided 
     *         for at least one participant differs from the crash  
     *         count in a previous invocation of the same pairing  
     *         of participant and transaction
     * @throws UnknownTransactionException if a transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws CannotJoinException if a transaction is known 
     *         to the manager but is no longer active.
     * @throws RemoteException if there is a communication error
     * @see TransactionManager#join
     */
    void promote(long id, TransactionParticipant[] parts,
		 long[] crashCounts, TransactionParticipant drop)
       	throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException;
}
