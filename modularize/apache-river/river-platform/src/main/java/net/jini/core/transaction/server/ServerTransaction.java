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
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.transaction.*;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;


/**
 * Class implementing the <code>Transaction</code> interface, for use with
 * transaction participants that implement the default transaction semantics.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.transaction.Transaction
 * @see NestableServerTransaction
 * @see TransactionManager
 * @see net.jini.core.transaction.TransactionFactory
 *
 * @since 1.0
 */
@AtomicSerial
public class ServerTransaction implements Transaction, java.io.Serializable {
    static final long serialVersionUID = 4552277137549765374L;

    /**
     * The transaction manager.
     * @serial
     */
    public final TransactionManager mgr;
    /**
     * The transaction id.
     * @serial
     */
    public final long id;
    
    public ServerTransaction(GetArg arg) throws IOException{
	this(Valid.notNull(arg.get("mgr", null, TransactionManager.class), 
		"TransactionManager cannot be null"),
	     arg.get("id", 0L)
	);
    }

    /**
     * Simple constructor.  Clients should not call this directly, but
     * should instead use <code>TransactionFactory</code>.
     *
     * @param mgr the manager for this transaction
     * @param id the transaction id
     */
    public ServerTransaction(TransactionManager mgr, long id) {
	this.mgr = mgr;
	this.id = id;
    }

    // inherit javadoc
    public int hashCode() {
        return (int) id ^ mgr.hashCode();
    }

    /**
     * Two instances are equal if they have the same transaction manager
     * and the same transaction id.
     */
    public boolean equals(Object other) {
	if (this == other)
	   return true;

	if (!(other instanceof ServerTransaction)) 
	   return false;

        ServerTransaction t = (ServerTransaction) other;
        return (id == t.id && mgr.equals(t.mgr));
    }

    // inherit javadoc
    public void commit()
	throws UnknownTransactionException, CannotCommitException,
	       RemoteException
    {
        mgr.commit(id);
    }

    // inherit javadoc
    public void commit(long waitFor)
        throws UnknownTransactionException, CannotCommitException,
	       TimeoutExpiredException, RemoteException
    {
	mgr.commit(id, waitFor);
    }

    // inherit javadoc
    public void abort()
	throws UnknownTransactionException, CannotAbortException,
	       RemoteException
    {
	mgr.abort(id);
    }

    // inherit javadoc
    public void abort(long waitFor)
	throws UnknownTransactionException, CannotAbortException,
	       TimeoutExpiredException, RemoteException
    {
        mgr.abort(id, waitFor);
    }

    /**
     * Join the transaction. The <code>crashCount</code> marks the state of
     * the storage used by the participant for transactions. If the
     * participant attempts to join a transaction more than once, the crash
     * counts must be the same. Each system crash or other event that
     * destroys the state of the participant's unprepared transaction storage
     * must cause the crash count to increase by at least one.
     *
     * @param part the participant joining the transaction
     * @param crashCount the participant's current crash count
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws CannotJoinException if the transaction is known 
     *         to the manager but is no longer active.
     * @throws CrashCountException if the crash count provided 
     *         for the participant differs from the crash count 
     *         in a previous invocation of the same pairing of 
     *         participant and transaction
     * @throws RemoteException if there is a communication error
     */
    public void join(TransactionParticipant part, long crashCount)
        throws UnknownTransactionException, CannotJoinException,
	       CrashCountException, RemoteException
    {
        mgr.join(id, part, crashCount);
    }
 
    /**
     * Returns the current state of the transaction.  The returned
     * state can be any of the <code>TransactionConstants</code> values.
     *
     * @return an <code>int</code> representing the state of the transaction
     *
     * @throws UnknownTransactionException if the transaction 
     *         is unknown to the transaction manager, either 
     *         because the transaction ID is incorrect or because the 
     *         transaction is complete and its state has been 
     *         discarded by the manager.
     * @throws RemoteException if there is a communication error
     *
     * @see TransactionConstants
     */
    public int getState() throws UnknownTransactionException, RemoteException {
	return mgr.getState(id);
    }

    /**
     * Return true if the transaction has a parent, false if the transaction
     * is top level.
     *
     * @return true if the transaction has a parent, false if the transaction
     * is top level.
     */
    public boolean isNested() {
	return false;
    }

    // inherit javadoc
    public String toString() {
	return this.getClass().getName() +
	    " [manager=" + mgr +
	    ", id=" + id +
	    "]";
    }
}
