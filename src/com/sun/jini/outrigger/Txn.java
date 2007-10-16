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
package com.sun.jini.outrigger;

import com.sun.jini.constants.TxnConstants;
import com.sun.jini.logging.Levels;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Iterator;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.server.ServerTransaction;
import net.jini.core.transaction.server.TransactionConstants;
import net.jini.core.transaction.server.TransactionManager;

import net.jini.space.InternalSpaceException;
import net.jini.security.ProxyPreparer;

/**
 * This class represents a space's state in a single transaction.
 *
 * Object of this class represent Jini transactions within outrigger.
 * These transactions hold "Transactables" -- things that represent
 * the actions that have been taken under this transaction. For example, 
 * if this transaction were to be cancelled, the Transactables are
 * examined and serve as the list of things to roll back in order to 
 * restore the state of the Space status quo ante.
 *
 * This is achieved by having the transactables notified of state changes 
 * to this transaction such as preparing, commit, etc. The 
 * transactables themselves are responsible for "doing the right thing."
 *
 * NB: some--but not all--of the actions one can take with a transaction
 * are managed internally by these objects. That is, all of the important
 * methods objects of these types are synchronized. Therefore, two
 * simultaneous calls to abort() and commit() are arbitrated properly. 
 *
 * However, care must be taken when add()ing a transactable. Even
 * though the add() method is synchronized if you check the state of
 * the transaction to ensure that is active and then call add() the
 * transaction could have left the active state between the check and
 * the add() call unless the call obtains the appropriate locks. This 
 * is even more likely if other work needs to be done in addition to
 * calling add() (e.g. persisting state, obtaining locks, etc.). The
 * caller of add() should lock the associated transaction object and
 * ensure that the transaction is still considered ACTIVE, do whatever
 * work is necessary to complete while the transaction is in the ACTIVE
 * state (including calling call add()) and then release the lock.
 * This can be done by :
 * <ul>
 * <li> holding the lock on this object while checking the
 *      state and carrying out the operation (including calling add()), or
 * <li> calling ensureActive() to check the state
 *      and obtain a non-exclusive lock, carrying out the operation
 *      (including calling add()), and then calling allowStateChange() to
 *      release the lock.
 * </ul>
 * The pair of ensureActive() and allowStateChange() allows for more
 * concurrency if the operation is expected to take a long time, in
 * that it will allow for other operations to be performed under the
 * same transaction and let aborts prevent other operations from
 * being started.
 *
 * @author Sun Microsystems, Inc.  
 */
class Txn implements TransactableMgr, TransactionConstants, StorableObject {

    /** The internal id Outrigger as assigned to the transaction */
    final private long id;

    /** What state we think the transaction is in */
    private int	state;

    /** 
     * The transaction manager associated with the transaction 
     * this object is fronting for.
     */
    private StorableReference trm;

    /**
     * Cached <code>ServerTransaction</code> object for
     * the transaction this object is fronting for.
     */
    private ServerTransaction tr;
    
    /** The id the transaction manager assigned to this transaction */
    private long  trId;

    /** 
     * The list of <code>Transactable</code> participating in 
     * this transaction.
     */
    final private List txnables = new java.util.LinkedList();

    /**
     * The task responsible for monitoring to see if this
     * transaction has been aborted with us being told, or
     * null if no such task as been allocated.
     */
    private TxnMonitorTask	monitorTask;

    /** Count of number of threads holding a read lock on state */
    private int stateReaders = 0;

    /** 
     * <code>true</code> if there is a blocked state change. Used
     * to give writers priority.
     */
    private boolean stateChangeWaiting = false;

    /** Logger for logging transaction related information */
    private static final Logger logger = 
	Logger.getLogger(OutriggerServerImpl.txnLoggerName);

    /**
     * Create a new <code>Txn</code> that represents our state in the
     * given <code>ServerTransaction</code>.
     */
    Txn(ServerTransaction tr, long id) {
	this(id);
	trId = tr.id;
	this.tr = tr;
	this.trm = new StorableReference(tr.mgr);
	state = ACTIVE;

	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "creating txn for transaction mgr:" +
		    "{0}, id:{1}, state:{2}", 
		new Object[]{tr, new Long(trId), TxnConstants.getName(state)});
	}
    }

    /** Used in recovery */
    Txn(long id) {
	this.id = id;		// the txn id is not persisted
    }

    /**
     * Get the id for this txn. Note that this id is NOT the same as
     * the ID of the transaction. Since that ID is not unique (it must
     * be qualified with the <code>ServerTransaction</code> object) we create
     * our own internal id to make txns unique. This is needed since we
     * may not have the <code>Transaction</code> unmarshalled.
     */
    Long getId() {
	return new Long(id);
    }

    /**
     * We keep the transaction ID around because we may need it
     * to identify a broken transaction after recovery.
     */
    long getTransactionId() {
	return trId;
    }

    /**
     * Return our local view of the current state. Need to be holding
     * the lock on this object or have called <code>ensureActive</code>
     * to get the current value.
     */
    int getState() {
	return state;
    }

    /**
     * Atomically checks that this transaction is in the active
     * state and locks the transaction in the active state.
     * The lock can be released by calling <code>allowStateChange</code>.
     * Each call to this method should be paired with a call to
     * <code>allowStateChange</code> in a finally block.
     * @throws CannotJoinException if the transaction
     * is not active or a state change is pending.  
     */
    synchronized void ensureActive() throws CannotJoinException {
	if (state != ACTIVE || stateChangeWaiting) {
	    final String msg = "transaction mgr:" + tr + ", id:" + trId +
		" not active, in state " + TxnConstants.getName(state);
	    final CannotJoinException e = new CannotJoinException(msg);
	    logger.log(Levels.FAILED, msg, e);
	    throw e; 
	}
	assert stateReaders >= 0;
	stateReaders++;
    }

    /**
     * Release the read lock created by an <code>ensureActive</code>
     * call. Does nothing if the transaction is not active or there is
     * a state change pending and thus is safe to call even if the
     * corresponding <code>ensureActive</code> call threw
     * <code>CannotJoinException</code>.  
     */
    synchronized void allowStateChange() {
	if (state != ACTIVE || stateChangeWaiting)
	    return;
	stateReaders--;
	assert stateReaders >= 0;
	notifyAll();
    }

    /**
     * Prevents new operations from being started under this
     * transaction and blocks until in process operations are
     * completed.
     */
    synchronized void makeInactive() {
	stateChangeWaiting = true;
	assert stateReaders >= 0;
	while (stateReaders != 0) {
	    try {
		wait();
	    } catch (InterruptedException e) {
		throw new AssertionError(e);
	    }
	    assert stateReaders >= 0;
	}
    }

    /**
     * Prepare for transaction commit. <code>makeInactive</code> must have 
     * been called on this transaction first.
     */
    synchronized int prepare(OutriggerServerImpl space) {
	assert stateChangeWaiting : "prepare called before makeInactive";
	assert stateReaders == 0 : "prepare called before makeInactive completed";

	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "prepare: transaction mgr:{0}, id:{1}, " +
		"state:{2}", 
		new Object[]{tr, new Long(trId), TxnConstants.getName(state)});
	}

	switch (state) {
	  case ABORTED:			       // previously aborted
	    return ABORTED;

	  case COMMITTED:		       // previously committed
	    throw new IllegalStateException(); // "cannot happen"

	  case NOTCHANGED:		       // previously voted NOTCHANGED
	  case PREPARED:		       // previously voted PREPARED
	    return state;		       // they are idempotent, and
	                                       // and we have nothing to do
	                                       // so return

	  case ACTIVE:			       // currently active
	    boolean changed = false;	       // did this txn change
	                                       // anything?

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "prepare:preparing transaction mgr:" +
		    "{0}, id:{1}, state:{2}", 
		new Object[]{tr, new Long(trId), TxnConstants.getName(state)});
	    }

	    // loop through Transactable members of this Txn
	    final Iterator i = txnables.iterator();
	    int c=0; // Counter for debugging message
	    while (i.hasNext()) {
		// get this member's vote
		final Transactable transactable = (Transactable)i.next();
		final int prepState =  transactable.prepare(this, space);
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "prepare:prepared " +
		        "transactable {0} for transaction mgr:{1}, id:{2}," +
			" transactable now in state {3}", 
			new Object[]{transactable, tr, new Long(trId), 
				     TxnConstants.getName(prepState)});
		}

		switch (prepState) {
		  case PREPARED:	     // has prepared state
		    changed = true;	     // this means a change
		    continue;

		  case ABORTED:		     // has to abort
		    abort(space);	     // abort this txn (does cleanup)
		    state = ABORTED;
		    return state;	     // vote aborted

		  case NOTCHANGED:	     // no change
		    i.remove();              // Won't need to call again
		    continue;

		  default:		     // huh?
		    throw new
			InternalSpaceException("prepare said " + prepState);
		}
	    }

	    if (changed) {
		state = PREPARED;
		// have to watch this since it's now holding permanent 
		// resources
		space.monitor(Collections.nCopies(1, this));
	    } else {
		state = NOTCHANGED;
	    }
	    break;

	  default:
	    throw new IllegalStateException("unknown Txn state: " + state);
	}

	return state;
    }

    /**
     * Abort the transaction.  This must be callable from
     * <code>prepare</code> because if a <code>Transactable</code>
     * votes <code>ABORT</code>, this method is called to make that
     * happen. <code>makeInactive</code> must have been called on this
     * transaction first.
     */
    synchronized void abort(OutriggerServerImpl space) {
	assert stateChangeWaiting : "abort called before makeInactive";
	assert stateReaders == 0 : "abort called before makeInactive completed";

	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "abort: transaction mgr:{0}, id:{1}, " +
		"state:{2}", 
		new Object[]{tr, new Long(trId), TxnConstants.getName(state)});
	}

	switch (state) {
	  case ABORTED:		// already aborted
	  case NOTCHANGED:	// nothing to abort
	    break;

	  case COMMITTED:	// "cannot happen"
	    throw new IllegalStateException("aborting a committed txn");

	  case ACTIVE:
	  case PREPARED:
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "abort:aborting transaction mgr:" +
		    "{0}, id:{1}, state:{2}", 
		new Object[]{tr, new Long(trId), TxnConstants.getName(state)});
	    }

	    final Iterator i = txnables.iterator();
	    while (i.hasNext())
		((Transactable) i.next()).abort(this, space);
	    state = ABORTED;
	    cleanup();
	    break;

	  default:
	    throw new IllegalStateException("unknown Txn state: " + state);
	}
    }

    /**
     * Having prepared, roll the changes
     * forward. <code>makeInactive</code> must have been called on
     * this transaction first.
     */
    synchronized void commit(OutriggerServerImpl space) {
	assert stateChangeWaiting : "commit called before makeInactive";
	assert stateReaders == 0 : "commit called before makeInactive completed";

	//!! Need to involve mgr here
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "commit: transaction mgr:{0}, id:{1}, " +
		"state:{2}", 
		new Object[]{tr, new Long(trId), TxnConstants.getName(state)});
	}

	switch (state) {
	  case ABORTED:		// "cannot happen" stuff
	  case ACTIVE:
	  case NOTCHANGED:
	    throw new IllegalStateException("committing "
		+ TxnConstants.getName(state) + " txn");

	  case COMMITTED:	// previous committed, that's okay
	    return;

	  case PREPARED:	// voted PREPARED, time to finish up
	    final Iterator i = txnables.iterator();
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "commit:committing transaction mgr:" +
		    "{0}, id:{1}, state:{2}", 
		new Object[]{tr, new Long(trId), TxnConstants.getName(state)});
	    }

	    while (i.hasNext())
		((Transactable) i.next()).commit(this, space);
	    state = COMMITTED;
	    cleanup();
	    return;

	  default:
	    throw new IllegalStateException("unknown Txn state: " + state);
	}
    }

    /**
     * Caution: see locking discussion at the class level.
     */
    public synchronized Transactable add(Transactable t) {
	txnables.add(t);
	return t;
    }

    // inherit doc comment
    public ServerTransaction getTransaction(ProxyPreparer preparer)
	throws IOException, ClassNotFoundException
    {
	if (tr == null) {
	    final TransactionManager mgr = 
		(TransactionManager)trm.get(preparer);
	    tr = new ServerTransaction(mgr, trId);
	}
	return tr;
    }

    /**
     * Return the manager associated with this transaction.
     * @return the manager associated with this transaction.
     * @throws IllegalStateException if this <code>Txn</code>
     *         is still broken.
     */
    TransactionManager getManager() {
	if (tr == null)
	    throw new IllegalStateException("Txn is still broken");
	return tr.mgr;
    }

    /**
     * Return the monitor task for this object. Note, this
     * method is unsynchronized because it (and 
     * <code>monitorTask(TxnMonitorTask)</code> are both called
     * from the same thread.
     */
    TxnMonitorTask monitorTask() {
	return monitorTask;
    }

    /**
     * Set the monitor task for this object. Note, this method is
     * unsynchronized because it (and <code>monitorTask()</code> are
     * both called from the same thread.
     */
    void monitorTask(TxnMonitorTask task) {
	monitorTask = task;
    }

    /**
     * Clean up any state when the transaction is finished.
     */
    private void cleanup() {
	if (monitorTask != null)
	    monitorTask.cancel();	// stop doing this
    }

    // -----------------------------------
    //  Methods required by StorableObject
    // -----------------------------------

    // inherit doc comment
    public void store(ObjectOutputStream out) throws IOException {
	/* There is a bunch of stuff we don't need to write. The
	 * Txn id not stored since it is handed back during
	 * recovery. The content is rebuilt txnables by the various
	 * recoverWrite and recoverTake calls. state is not written
	 * because it is always ACTIVE when we write, and always
	 * needs to be PREPARED when we read it back.
	 */
	out.writeObject(trm);
	out.writeLong(trId);
    }

    // inherit doc comment
    public void restore(ObjectInputStream in) 
	throws IOException, ClassNotFoundException 
    {
	/* Only transactions that got prepared and not committed or
	 * aborted get recovered
	 */
	state    = PREPARED;
	trm      = (StorableReference)in.readObject();
	trId     = in.readLong();
    }
}
