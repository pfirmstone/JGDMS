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

import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.transaction.server.TransactionConstants;
import net.jini.space.InternalSpaceException;

/**
 * Class that manages transaction related state on behalf of
 * <code>EntryHandle</code>s. Can accommodate entries locked by
 * more than one transaction.  The synchronization of this object is
 * managed by the <code>EntryHandle</code> that owns it.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see EntryHandle
 */
class TxnState {
    /**
     * The list of known managers.  In order to keep things small in the
     * common case that there is only one known manager, <code>mgrs</code>
     * is managed as a ``list'' in one of two states -- it is either a
     * direct reference to the only manager for this handle, or a reference
     * to an <code>HashSet</code> with entries for each associated manager.
     */
    private Object		mgrs;

    /**
     * The current state of the handle, such as <code>READ</code> or
     * <code>TAKE</code>.
     */
    private int			state;

    /**
     * The holder the handle which owns this object is in
     */
    final private EntryHolder	holder;

    /** Logger for logging information about entry matching */
    private static final Logger matchingLogger = 
	Logger.getLogger(OutriggerServerImpl.matchingLoggerName);

    /** Logger for logging transaction related information */
    private static final Logger txnLogger = 
	Logger.getLogger(OutriggerServerImpl.txnLoggerName);

    /**
     * Create a new <code>TxnState</code>.  It will start initially
     * with the type of lock indicated by <code>state</code> under the
     * transaction managed by <code>mgr</code>.  <code>holder</code> is
     * the holder the associated entry handle is in.
     */
    TxnState(TransactableMgr mgr, int state, EntryHolder holder) {
	this.mgrs = mgr;
	this.state = state;
	this.holder = holder;
	txnLogger.log(Level.FINER, "TxnState: TxnState: state = {0}",
		      TransactableMgr.stateNames[state]);
    }

    /**
     * Prepare to commit this object's part of the transaction.  Return
     * the prepare's status.
     */
    int prepare(TransactableMgr mgr, OutriggerServerImpl space,
		EntryHandle owner) 
    {
	txnLogger.log(Level.FINEST, "TxnState: prepare: state = {0}",
		      TransactableMgr.stateNames[state]);

	if (state == TransactableMgr.READ) {
	    final int locksLeft = removeMgr(mgr);
	    /* If there is more than one lock left resolving this lock
	     * does not change anything (takes still can't take the
	     * entry and reads could already) Also need to make sure
	     * it has not be removed (say by expiration or a cancel)
	     * since this would cause to send events for entries that
	     * were removed before it became visible. Also could cause
	     * ifExists queries to hang on to the entry and block
	     * indefinitely.  If they had already send the EntryTransition
	     * for the removal.
	     */
	    if (locksLeft <= 1 && !owner.removed()) {
		// Has the potential to resolve conflicts/generate events
		if (locksLeft == 1) {
		    space.recordTransition(
 		         new EntryTransition(owner, mgr(), true, false, false));
		} else if (locksLeft == 0) {
		    space.recordTransition(
 		         new EntryTransition(owner, null, true, false, false));
		} else {
		    throw new AssertionError("Fewer than 0 locks left");
		}
	    }
	    return TransactionConstants.NOTCHANGED;
	}

	return TransactionConstants.PREPARED;
    }

    /**
     * Abort this object's part of the transaction.  Return true
     * if this clears the last transaction associated with this object.
     */
    boolean abort(TransactableMgr mgr, OutriggerServerImpl space, 
		  EntryHandle owner) 
    {
	boolean rslt = true;

	txnLogger.log(Level.FINEST, "TxnState: abort: state = {0}",
		      TransactableMgr.stateNames[state]);

	if (state == TransactableMgr.READ || state == TransactableMgr.TAKE) {
	    final int locksLeft = removeMgr(mgr);
	    rslt = (locksLeft == 0);
	    /* If there is more than one lock left resolving this
	     * lock does not change anything (takes still can't
	     * take the entry and reads could already). Also
	     * need to make sure it has not be removed (say
	     * by expiration or a cancel), otherwise
	     * we could log a transition to visible after
	     * the removal was logged, this could cause
	     * ifExists queries to hang on to the entry
	     * and block indefinitely. (This raises an
	     * issue if remote events were sent for re-appearance)
	     */
	    if (locksLeft <= 1 && !owner.removed()) {
		// Has the potential to resolve conflicts/generate events
		if (locksLeft == 1) {
		    /* There could have only been multiple locks if
		     * they were all read locks, thus the lock the
		     * abort resolves must h be a read lock, and the
		     * remaining lock must be a read lock too.
		     * Therefore this must be an availability but not a
		     * visibility transition.
		     */
		    assert state == TransactableMgr.READ;

		    space.recordTransition(
 		        new EntryTransition(owner, mgr(), true, false, false));
		} else if (locksLeft == 0) {
		    // Only a visibility transition if the lock being
		    // dropped was a take lock
		    final boolean visibility = (state == TransactableMgr.TAKE);

		    space.recordTransition(
			new EntryTransition(owner, null, true, visibility,
					    false));
		} else {
		    throw new AssertionError("Fewer than 0 locks left");
		}
	    }
	} else {
	    /* must be a write, make the entry disappear, will
	     * call recordTransition()
	     */
	    holder.remove(owner, false);	    
	}
	
	return rslt;
    }

    /**
     * Commit this object's part of the transaction.  The
     * <code>space</code> is the <code>OutriggerServerImpl</code> on
     * which the operation happens -- some commit operations have
     * space-wide side effects (for example, a commit of a
     * <code>write</code> operation can cause event notifications for
     * clients registered under the transaction's parent). Return true
     * if this clears the last transaction associated with this
     * object.
     */
    boolean commit(TransactableMgr mgr, OutriggerServerImpl space, 
		   EntryHandle owner) 
    {
	txnLogger.log(Level.FINEST, "TxnState: commit: state = {0}",
		      TransactableMgr.stateNames[state]);

	switch (state) {
	  case TransactableMgr.WRITE:
	      //!! assumption -- single level transactions:
	      //!! we pass null where we should pass the parent txn
	      if (owner.removed())
		  // if removed() then this entry must
		  // have been taken as well as written under this
		  // transaction.
		  return true;

	      space.recordTransition(
		  new EntryTransition(owner, null, true, true, true));
       	      return (removeMgr(mgr) == 0);

  	  case TransactableMgr.READ:
	      // Read locked entries should never get prepared.
	      throw new InternalSpaceException
	          ("committing a read locked entry");

	  case TransactableMgr.TAKE:
	      // remove calls recordTransition()
	      holder.remove(owner, false);
	      // Resolves a lock
	      return true; // Take-locked entries only have one Txn

	  default:
	      throw new InternalSpaceException("unexpected state in "
		  + "TxnState.commit(): " + state);
	}
    }

    /**
     * Remove the given mgr from the list of known managers.  Return the
     * number of mgrs still associated with this entry.
     */
    private int removeMgr(TransactableMgr mgr) {
	if (mgrs == null)
	    return 0;

	if (mgr == mgrs) {
	    mgrs = null;
	    return 0;
	}

	final HashSet tab = (HashSet) mgrs;
	tab.remove(mgr);
	return tab.size();
    }


    /**
     * Add <code>mgr</code> to the list of known managers, setting the
     * state of this handle to <code>op</code>.
     */
    void add(TransactableMgr mgr, int op) {
	if (mgr == mgrs)
	    return;	// already the only one known

	if (mgrs instanceof TransactableMgr) {
	    Object origMgr = mgrs;
	    mgrs = new HashSet(7);
	    ((HashSet) mgrs).add(origMgr);
	}

	// if mgr is in already this is harmless, and checking to prevent
	// a redundant addition is more expensive
	((HashSet) mgrs).add(mgr);
	monitor(mgr);
	state = op;
    }

    /**
     * If we need to, add this manager to the list of transactions that
     * need to be monitored because of conflicts over this entry.  Any
     * existing blocking txn is sufficient.
     *
     * @see TxnMonitorTask#addSibling
     */
    private void monitor(TransactableMgr mgr) {
	Txn txn = (Txn) mgr;
	Iterator it = ((HashSet) mgrs).iterator();
	while (it.hasNext()) {
	    Txn otherTxn = (Txn) it.next();
	    if (otherTxn != mgr && otherTxn.monitorTask() != null) {
		otherTxn.monitorTask().addSibling(txn);
		return;
	    }
	}
    }

    /**
     * It this entry is read locked promote to take locked and return
     * true, otherwise return false.  Assumes the take is being
     * performed under the one transaction that owns a lock on the
     * entry.  
     */
    boolean promoteToTakeIfNeeded() {
	// We can assume our state is ether WRITE or READ, if it was
	// take this method would not be called (since take blocks
	// other takes in the same transaction canPerform() would have
	// returned false)

	if (state == TransactableMgr.WRITE)
	    return false;

	state = TransactableMgr.TAKE;
	return true;
    }

    /**
     * Returns <code>true</code> if the operation <code>op</code>
     * under the transaction manager by <code>mgr</code> is legal on
     * the associated entry given the operations already performed on
     * the entry under other transactions.  It is legal to:
     * <ul>
     * <li>Read an entry that has been read in any other transaction,
     *     or that has been written under the same transaction (that is,
     *     if <code>mgr</code> is the same as the writing transaction).
     * <li>Take an entry that was written under the same transaction, or
     *     which was read <em>only</em> under the same transaction (that is,
     *     no other active transactions have also read it).
     * </ul>
     * All other operations are not legal, so <code>canPerform</code>
     * otherwise returns <code>false</code>.  
     */
    boolean canPerform(TransactableMgr mgr, int op) {
	//Note: If two transactions are performed
	//	within the same TransactableMgr
	//	(see Txn.java), that means its in
	//	the same Transaction (see Transaction.java).
	//      Operations under the same Transaction scope
	//	are the same as if under a null Transaction
	//	i.e. all operations are visible.
	//
	//	Semantics need to be added for multi-level
	//	transactions.

	if (matchingLogger.isLoggable(Level.FINER)) {
	    matchingLogger.log(Level.FINER,
                "TxnState: canPerform({0}, {1}): state = {2}",
		new Object[]{mgr, new Integer(op), 
			     TransactableMgr.stateNames[state]});
	}

	switch (state) {
	  case TransactableMgr.READ:
	    if (op == TransactableMgr.READ)
		return true;
	    return onlyMgr(mgr); // can only modify if I'm the only participant
	  case TransactableMgr.WRITE:
	    if ( (op == TransactableMgr.READ) ||
		 (op == TransactableMgr.TAKE) )
		return onlyMgr(mgr);
	    return false;	  // can't read unless I'm the writer
	  case TransactableMgr.TAKE:
	    return false;	  // can't take anything taken any time
	}
	return false;
    }

    /**
     * Return <code>true</code> if <code>mgr</code> is one of the managers
     * known to be managing this entry.
     */
    boolean knownMgr(TransactableMgr mgr) {
	if (mgr == null)
	    return false;
	if (mgr == mgrs)
	    return true;
	if (mgrs instanceof HashSet)
	    return ((HashSet) mgrs).contains(mgr);
	return false;
    }

    /**
     * Return <code>true</code> if the given manager is the only one
     * we know about.
     */
    boolean onlyMgr(TransactableMgr mgr) {
	if (mgr == null)
	    return false;
	if (mgr == mgrs)
	    return true;
	if (mgrs instanceof HashSet) {
	    HashSet tab = (HashSet) mgrs;
	    return (tab.size() == 1 && tab.contains(mgr));
	}
	return false;
    }

    /**
     * Add all the managers of this transaction to the given
     * collection.
     */
    void addTxns(java.util.Collection collection) {
	if (mgrs == null)
	    return;

	if (mgrs instanceof HashSet) {
	    final HashSet tab = (HashSet)mgrs;
	    collection.addAll((HashSet) mgrs);
	    return;
	}

	collection.add(mgrs);
    }

    /**
     * Return true if there are no more transactions associated with
     * this object.
     */
    boolean empty() {
	if (mgrs == null)
	    return true;

	if (mgrs instanceof HashSet) 
	    return ((HashSet)mgrs).isEmpty();
	
	return false;
    }

    /** Used by mgr() */
    final private TransactableMgr[] rslt = new TransactableMgr[1];

    /**
     * Returns the one manager associated with this transaction.
     * Throws an AssertionError if there is more or fewer than one
     * manager associated with this transaction.
     */
    private TransactableMgr mgr() {	
	if (mgrs == null)
	    throw new AssertionError
		("mgr() called on a TxnState with no manager");

	if (mgrs instanceof HashSet) { 
	    final HashSet tab = (HashSet)mgrs;
	    if (tab.size() != 1)
		throw new AssertionError
		    ("mgr() called on TxnState with more than one manager");
	    tab.toArray(rslt);
	    return rslt[0];
	}

	return (TransactableMgr)mgrs;
    }
}
