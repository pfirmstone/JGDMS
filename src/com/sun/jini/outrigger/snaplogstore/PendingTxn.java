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
package com.sun.jini.outrigger.snaplogstore;

import com.sun.jini.outrigger.Recover;
import com.sun.jini.outrigger.StoredObject;

import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Enumeration;
import java.util.Hashtable;

import net.jini.core.transaction.server.TransactionConstants;
import net.jini.space.InternalSpaceException;

/**
 * This object represents a pending transaction in a <code>BackEnd</code>.
 * As operations are performed under the transaction, they are logged into
 * this object.  When the transaction is committed, each operation is
 * committed into the DB.
 *
 * @author Sun Microsystems, Inc.
 *
 */
class PendingTxn implements Serializable {

    /** A superclass for the objects that represent pending operations. */
    static abstract class PendingOp implements Serializable {
	/**
	 * Commit the operation by invoking the relevant method on the
	 * <code>processor</code> object.
	 */
	abstract void commit(BackEnd processor);
    }

    /** An object that represents a pending write. */
    static class WriteOp extends PendingOp implements Serializable {
	Resource entry;

	WriteOp(Resource entry) {
	    this.entry = entry;
	}

	void commit(BackEnd processor) {
	    processor.writeOp(entry, null);
	}
    }

    /** An object that represents a pending take. */
    static class TakeOp extends PendingOp implements Serializable {
	byte cookie[];

	TakeOp(byte cookie[]) {
	    this.cookie = cookie;
	}

	void commit(BackEnd processor) {
	    processor.takeOp(cookie, null);
	}
    }

    private long		id;		// the transaction ID
    private int			state;		// current state
    private Hashtable		ops;		// list of pending operations
    private StoredObject	transaction;	// the transaction object
                                                // itself
    /**
     * Create a new <code>PendingTxn</code> for the given <code>id</code>.
     */
    PendingTxn(Long id) {
	this.id = id.longValue();
	state = TransactionConstants.ACTIVE;
	ops = new Hashtable();
    }

    /**
     * Add a new pending <code>write</code> operation.
     */
    void addWrite(Resource entry) {
	ops.put(entry.getCookieAsWrapper(), new WriteOp(entry));
    }

    /**
     * Add a new pending <code>take</code> operation.
     */
    void addTake(byte cookie[]) {

	// Remove entry if the take is for a previous write in this
	// transaction. If it is for an entry written outside the transaction,
	// save the take for later. Note that this allows the pending elements
	// to be processed out of order (during a commit).
	//
	ByteArrayWrapper baw = new ByteArrayWrapper(cookie);
	if (ops.remove(baw) == null)
	    ops.put(baw, new TakeOp(cookie));
    }

    /**
     * Get a pending write resource.
     */
    Resource get(ByteArrayWrapper cookie) {
	PendingOp po = (PendingOp)ops.get(cookie);

	// Both pending writes and pending takes are stored in the table.
	// We only interested in entries from pending writes.
	//
	if (po instanceof WriteOp)
	    return ((WriteOp)po).entry;

	return null;
    }

    /**
     * Remove a pending write.
     */
    Resource remove(ByteArrayWrapper cookie) {
	Resource entry = get(cookie);

	if (entry != null)
	    ops.remove(cookie);

	return entry;
    }

    /**
     * Recover prepared transactions. This method returns true if this
     * pending transaction was recovered.
     */
    boolean recover(Recover space) throws Exception {
	if (state != TransactionConstants.PREPARED)
	    return false;

	space.recoverTransaction(new Long(id), transaction);

	Enumeration e = ops.elements();
	while (e.hasMoreElements()) {
	    PendingTxn.PendingOp op = (PendingTxn.PendingOp)e.nextElement();

	    if (op instanceof PendingTxn.WriteOp) {
		space.recoverWrite(((PendingTxn.WriteOp)op).entry, 
				   new Long(id));
	    } else if (op instanceof PendingTxn.TakeOp) {
		space.recoverTake(
		    ByteArrayWrapper.toUuid(((PendingTxn.TakeOp)op).cookie), 
		    new Long(id));
	    } else {
		throw new InternalSpaceException("unknown operation type: " +
						 op.getClass().getName());
	    }
	}
	return true;
    }

    /**
     * Set the <code>Transaction</code> object.
     */
    void prepare(StoredObject tr) {
	transaction = tr;
        state = TransactionConstants.PREPARED;
    }

    /**
     * Commit all the operations by invoking the relevant method
     * on the <code>processor</code> object.
     */
    void commit(BackEnd processor) {
	Enumeration e = ops.elements();

	while (e.hasMoreElements())
	    ((PendingOp)e.nextElement()).commit(processor);
	state = TransactionConstants.COMMITTED;
    }

    public int hashCode() {
	return (int)id;
    }

    public boolean equals(Object o) {
	try {
	    return ((PendingTxn)o).id == id;
	} catch (Exception e) {}
	return false;
    }
}
