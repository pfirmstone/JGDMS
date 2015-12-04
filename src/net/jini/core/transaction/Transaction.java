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

import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;
import java.rmi.RemoteException;

/**
 * Interface for classes representing transactions returned by
 * <code>TransactionManager</code> servers for use with transaction
 * participants that implement the default transaction semantics.
 * The semantics: <p>
 *
 * The overall effect of executing a set of sibling pure transactions
 * concurrently is always equivalent to some sequential execution. <p>
 *
 * Ancestor transactions can execute concurrently with child transactions,
 * subject to the locking rules below. <p>
 *
 * Every transactional operation is described in terms of acquiring locks
 * on objects; these locks are held until the transaction completes.  Whatever
 * the lock rules are, conflict rules are defined such that if two operations
 * do not commute, then they acquire conflicting locks.  A transaction can
 * acquire a lock only if the conflicting locks are those held by ancestor
 * transactions (or itself). When a subtransaction commits, its locks are
 * inherited by the parent transaction. <p>
 *
 * If an object is defined to be created under a transaction, then the
 * existence of the object is only visible within that transaction and its
 * inferiors, but will disappear if the transaction aborts.  If an object is
 * defined to be deleted under a transaction, then the object is not visible
 * to any transaction (including the deleting transaction) but will reappear
 * if the transaction aborts.  When a nested transaction commits, visibility
 * state is inherited by the parent transaction. <p>
 *
 * Once a transaction reaches the <code>VOTING</code> stage, if all
 * execution under the transaction (and its subtransactions) has finished,
 * then the only reasons the transaction can abort are: the manager crashes
 * (or has crashed); one or more participants crash (or have crashed); or
 * an explicit abort. <p>
 *
 * Transaction deadlocks are not guaranteed to be prevented or even detected,
 * but managers and participants are permitted to break known deadlocks by
 * aborting transactions. <p>
 *
 * An orphan transaction (it or one of its ancestors is guaranteed to abort)
 * is not guaranteed to be detected. <p>
 *
 * Causal ordering information about transactions is not guaranteed to be
 * propagated. <p>
 *
 * As long as a transaction persists in attempting to acquire a lock that
 * conflicts with another transaction, the participant will persist in
 * attempting to resolve the outcome of the transaction that holds the
 * conflicting lock.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see NestableTransaction
 * @see net.jini.core.transaction.server.TransactionManager
 * @see net.jini.core.transaction.server.NestableTransactionManager
 * @see TransactionFactory
 *
 * @since 1.0
 */
public interface Transaction {

    /** Class that holds return values from create methods. */
    public static class Created implements java.io.Serializable {
	static final long serialVersionUID = -5199291723008952986L;

	/**
	 * The transaction.
	 * @serial
	 */
        public final Transaction transaction;
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
	public Created(Transaction transaction, Lease lease) {
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
     * Commit the transaction. Commit asks the transaction manager to
     * execute the voting process with the participants.  Returns if the
     * transaction successfully reaches either the <code>NOTCHANGED</code>
     * or the <code>COMMITTED</code> state, without waiting for the
     * transaction manager to notify all participants of the decision.
     * If the transaction must be aborted (because one or more participants
     * are unable to prepare), <code>CannotCommitException</code> is thrown
     * without waiting for the transaction manager to notify all participants
     * of the decision.
     *
     * @throws UnknownTransactionException if the transaction is unknown 
     *         to the manager. This may be because the transaction ID was 
     *         incorrect, or because the transaction has proceeded to 
     *         cleanup due to an earlier commit or abort, and has been 
     *         forgotten.
     * @throws CannotCommitException if the transaction reaches the ABORTED 
     *         state, or is known to have previously reached that state due 
     *         to an earlier commit or abort.
     * @throws RemoteException if a communication error occurs.
     */
    void commit()
	throws UnknownTransactionException, CannotCommitException,
	       RemoteException;

    /**
     * Commit the transaction, waiting for participants to be notified of
     * the decision. Commit asks the transaction manager to execute the
     * voting process with the participants.  Returns if the transaction
     * successfully reaches either the <code>NOTCHANGED</code> or the
     * <code>COMMITTED</code> state, and the transaction manager has
     * notified all participants of the decision, before the specified
     * timeout expires.  If the transaction must be aborted (because one
     * or more participants are unable to prepare),
     * <code>CannotCommitException</code> is thrown if the transaction
     * manager is able to notify all participants of the decision before
     * the specified timeout expires.  If the transaction manager reaches
     * a decision, but is unable to notify all participants of that
     * decision before the specified timeout expires, then
     * <code>TimeoutExpiredException</code> is thrown.  If the specified
     * timeout expires before the transaction manager reaches a decision,
     * <code>TimeoutExpiredException</code> is not thrown until the
     * manager reaches a decision.
     *
     * @param waitFor timeout to wait, from the start of the call until
     * all participants have been notified of the transaction manager's
     * decision
     *
     * @throws UnknownTransactionException if the transaction is unknown 
     *         to the manager. This may be because the transaction ID was 
     *         incorrect, or because the transaction has proceeded to 
     *         cleanup due to an earlier commit or abort, and has been 
     *         forgotten.
     * @throws CannotCommitException if the transaction reaches the ABORTED 
     *         state, or is known to have previously reached that state due 
     *         to an earlier commit or abort.
     * @throws TimeoutExpiredException if the timeout expires before all 
     *         participants have been notified.
     * @throws RemoteException if a communication error occurs.
     */
    void commit(long waitFor)
	throws UnknownTransactionException, CannotCommitException,
	       TimeoutExpiredException, RemoteException;

    /**
     * Abort the transaction. This can be called at any time by
     * any object holding a reference to the transaction.  Abort asks
     * the transaction manager to abort the transaction and to notify
     * each participant of the decision, resulting in them rolling back
     * any state changes made as part of the transaction.  Returns as
     * soon as the transaction manager records the abort decision, without
     * waiting for the transaction manager to notify all participants of
     * the decision.
     *
     * @throws UnknownTransactionException if the transaction is unknown 
     *         to the manager. This may be because the transaction ID was 
     *         incorrect, or because the transaction has proceeded to 
     *         cleanup due to an earlier commit or abort, and has been 
     *         forgotten.
     * @throws CannotAbortException if the transaction is known to have 
     *         previously reached the COMMITTED state due to an earlier 
     *         commit.
     * @throws RemoteException if a communication error occurs.
     */
    void abort()
	throws UnknownTransactionException, CannotAbortException,
	       RemoteException;

    /**
     * Abort the transaction, waiting for participants to be notified of
     * the decision. This can be called at any time by any object holding
     * a reference to the transaction.  Abort asks the transaction manager
     * to abort the transaction and to notify each participant of the
     * decision, resulting in them rolling back any state changes made as
     * part of the transaction.  Returns if the transaction manager records
     * the decision and is able to notify all participants of the decision
     * before the specified timeout expires.  If the transaction manager
     * is unable to notify all participants of the decision before the
     * specified timeout expires, then <code>TimeoutExpiredException</code>
     * is thrown.
     *
     * @param waitFor timeout to wait, from the start of the call until
     * all participants have been notified of the transaction manager's
     * decision.
     *
     * @throws UnknownTransactionException if the transaction is unknown 
     *         to the manager. This may be because the transaction ID was 
     *         incorrect, or because the transaction has proceeded to 
     *         cleanup due to an earlier commit or abort, and has been 
     *         forgotten.
     * @throws CannotAbortException if the transaction is known to have 
     *         previously reached the COMMITTED state due to an earlier 
     *         commit.
     * @throws TimeoutExpiredException if the timeout expires before all 
     *         participants have been notified.
     * @throws RemoteException if a communication error occurs.
     */
    void abort(long waitFor)
       throws UnknownTransactionException, CannotAbortException,
	      TimeoutExpiredException, RemoteException;
}
