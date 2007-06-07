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

import java.util.WeakHashMap;
import net.jini.core.transaction.TransactionException;
import net.jini.space.InternalSpaceException;

/**
 * Subclass of QueryWatcher for takes and transactional reads.
 * Resolves with the first matching transition where the entry is
 * visible to the associated transaction and the entry is still
 * available.
 */
class ConsumingWatcher extends SingletonQueryWatcher implements Transactable {
    /**
     * If non-null the transaction this query is
     * being performed under. If <code>null</code> 
     * this query is not associated with a transaction.
     */
    private final Txn txn;

    /**
     * <code>true</code> if this query is a take and
     * <code>false</code> otherwise.
     */
    private final boolean takeIt;

    /**
     * Set of entries (represented by <code>EntryHolder</code>s) that
     * we would have liked to return, but have been provisionally
     * removed.
     */
    private final WeakHashMap provisionallyRemovedEntrySet;

    /**
     * Create a new <code>ConsumingWatcher</code>.
     * @param expiration the initial expiration time
     *        for this <code>TransitionWatcher</code> in 
     *        milliseconds since the beginning of the epoch.
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     * @param provisionallyRemovedEntrySet If the watcher encounters
     *        an entry that can not be read/taken because it has been
     *        provisionally removed then its handle will be placed in
     *        this <code>WeakHashMap</code> as a key (with null as the
     *        value).  May be <code>null</code> in which case
     *        provisionally removed entries will not be
     *        recorded. Ensures that object is only accessed by one
     *        thread at a time
     * @param txn If the query is being performed under
     *        a transaction the <code>Txn</code> object
     *        associated with that transaction.
     * @param takeIt <code>true</code> if this query is a take and
     *        <code>false</code> otherwise.  
     */
    ConsumingWatcher(long expiration, long timestamp, long startOrdinal, 
		     WeakHashMap provisionallyRemovedEntrySet, Txn txn,
		     boolean takeIt)
    {
	super(expiration, timestamp, startOrdinal);
	this.txn = txn;
	this.takeIt = takeIt;
	this.provisionallyRemovedEntrySet = provisionallyRemovedEntrySet; 
    }

    boolean isInterested(EntryTransition transition, long ordinal) {
	/* Note, !isResolved() without the lock will result only in
	 * false positives, not false negatives - it will only
	 * cause isInterested() to return false if we are resolved,
	 * we may still return true if we are resolved though.
	 *
	 * Note, we don't bother with isVisible because isAvailable is
	 * the right test for a take, and using isAvailable for read too
	 * simplifies things a bit. The worst that will happen is we
	 * will return true for a transition that should not change
	 * anything - but we are unlikely to get these transitions
	 * (since the fact the transition happened at all suggests
	 * that there is some entry that would have resolved this
	 * watcher), if we do generate a false positive we do a
	 * positive check against the entry in question anyway - so it
	 * can't cause us to produce the wrong answer, and it may well be
	 * that such a "false positive" allows us to get legitimately
	 * resolved anyway - so it is not so "false" a positive after all.
	 */
	final TransactableMgr transitionTxn = transition.getTxn();
	return (ordinal>startOrdinal) && !isResolved() &&
	    transition.isAvailable() &&
	    ((null == transitionTxn) || (txn == transitionTxn));
    }

    synchronized void process(EntryTransition transition, long now) {
	if (isResolved())
	    return; // Already done.

	final EntryHandle handle = transition.getHandle();

	// Is it still available?
	if (getServer().attemptCapture(handle, txn, takeIt, null, 
				       provisionallyRemovedEntrySet, now, this)) 
	{
	    // Got it
	    resolve(handle, null);
	} 
    }

    synchronized boolean catchUp(EntryTransition transition, long now) {
	if (isResolved())
	    return true;

	final TransactableMgr transitionTxn = transition.getTxn();
	final EntryHandle handle = transition.getHandle();

	/* See note in isInterested about not calling isVisible */
	if (transition.isAvailable() &&
	    ((null == transitionTxn) || (txn == transitionTxn)) &&
	    (getServer().attemptCapture(handle, txn, takeIt, null,
		provisionallyRemovedEntrySet, now, this)))
	{
	    // Got it
	    resolve(handle, null);
	    return true;
	} 

	// Not interesting or could not get it, either way not resolved
	return false;
    }


    /**
     * If a transaction ends in the middle of a query we want
     * to throw an exception to the client making the query
     * not the <code>Txn</code> calling us here.)
     */
    public synchronized int prepare(TransactableMgr mgr,
				    OutriggerServerImpl space) 
    {
	assert txn != null:"Transactable method called on a " +
	    "non-transactional ConsumingWatcher";

	// only throw an exception if we are not resolved.
	if (!isResolved()) {
	    // Query still in progress, kill it
	    resolve(null, new TransactionException("completed while " +
						   "operation in progress"));
	}

	// If this object has made changes they have been recorded elsewhere
	return NOTCHANGED;
    }

    /**
     * This should never happen since we always return
     * <code>NOTCHANGED</code> from <code>prepare</code>.
     */
    public void commit(TransactableMgr mgr, OutriggerServerImpl space) {
	throw new InternalSpaceException("committing a blocking query");
			   
    }

    /**
     * If a transaction ends in the middle of a query we want
     * to throw an exception to the client making the query 
     * (not the <code>Txn</code> calling us here.)
     */
    public void abort(TransactableMgr mgr, OutriggerServerImpl space) {
	// prepare does the right thing, and should forever
	prepare(mgr, space);
    }
}

