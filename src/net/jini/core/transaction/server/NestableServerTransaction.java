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

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.transaction.*;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Class implementing the <code>NestableTransaction</code> interface, for use
 * with transaction participants that implement the default transaction
 * semantics.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.transaction.NestableTransaction
 * @see NestableTransactionManager
 * @see net.jini.core.transaction.TransactionFactory
 *
 * @since 1.0
 */
@AtomicSerial
public class NestableServerTransaction extends ServerTransaction
				       implements NestableTransaction
{
    static final long serialVersionUID = -3438419132543972925L;

    /**
     * The parent transaction, if any.
     * @serial
     */
    public final NestableServerTransaction parent;
    
    public NestableServerTransaction(GetArg arg) throws IOException{
	super(check(arg));
	this.parent = arg.get("parent", null, NestableServerTransaction.class);
    }
    
    private static GetArg check(GetArg arg) throws IOException{
	arg.get("parent", null, NestableServerTransaction.class);
	return arg;
    }

    /**
     * Simple constructor.  Clients should not call this directly, but
     * should instead use <code>TransactionFactory</code> and
     * <code>NestableTransaction</code> methods.
     *
     * @param mgr the manager for this transaction
     * @param id the transaction id
     * @param parent the parent transaction, if any
     */
    public NestableServerTransaction(NestableTransactionManager mgr, long id,
				     NestableServerTransaction parent)
    {
	super(mgr, id);
        this.parent = parent;
    }

    // inherit javadoc
    public NestableTransaction.Created create(NestableTransactionManager mgr,
					      long leaseTime)
        throws UnknownTransactionException, CannotJoinException,
	       LeaseDeniedException, RemoteException
    {
	TransactionManager.Created rawTxn =
	    mgr.create((NestableTransactionManager)this.mgr, id, leaseTime);
        return new NestableTransaction.Created(
				     new NestableServerTransaction(mgr,
								   rawTxn.id,
								   this),
				     rawTxn.lease);
    }

    // inherit javadoc
    public NestableTransaction.Created create(long leaseTime)
        throws UnknownTransactionException, CannotJoinException,
	       LeaseDeniedException, RemoteException
    {
	return create((NestableTransactionManager)mgr, leaseTime);
    }

    /**
     * Promote the listed participants (from a subtransaction) into
     * this (the parent) transaction.  This method is for use by the
     * manager of a subtransaction when the subtransaction commits.
     * At this point, all participants of the subtransaction must become
     * participants in the parent transaction.  Prior to this point, the
     * subtransaction's manager was a participant of the parent transaction,
     * but after a successful promotion it need no longer be one (if it was
     * not itself a participant of the subtransaction), and so it may specify
     * itself as a participant to drop from the transaction.  Otherwise,
     * participants should not be dropped out of transactions.  For each
     * promoted participant, the participant's crash count is stored in the
     * corresponding element of the <code>crashCounts</code> array.
     *
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
     *
     * @see NestableTransactionManager#promote
     */
    public void promote(TransactionParticipant[] parts, long[] crashCounts,
			TransactionParticipant drop)
       	throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException
    {
	((NestableTransactionManager)mgr).promote(id, parts, crashCounts,
						  drop);
    }

    /**
     * Return true if the specified transaction is an ancestor of
     * this transaction.
     *
     * @param enclosing transaction to test for being an ancestor
     * @return true if the specified transaction is an ancestor of
     *         this transaction.
     */
    public boolean enclosedBy(NestableTransaction enclosing) {
	for (NestableServerTransaction ancestor = this.parent;
	     ancestor != null;
	     ancestor = ancestor.parent)
	{
	    if (ancestor.equals(enclosing))
		return true;
	}
        return false;
    }

    // inherit javadoc
    public boolean isNested() {
	return parent != null;
    }
    
    // inherit javadoc
    public String toString() {
	return this.getClass().getName() +
	    " [manager=" + mgr +
	    ", id=" + id +
            ", parent=" + parent +
	    "]";
    }    
}
