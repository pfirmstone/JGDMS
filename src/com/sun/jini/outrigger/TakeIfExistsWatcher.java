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

import java.util.Set;
import java.util.WeakHashMap;
import net.jini.core.transaction.TransactionException;
import net.jini.space.InternalSpaceException;

/**
 * Subclass of <code>QueryWatcher</code> for <code>takeIfExists</code>
 * queries.  Resolves with the first matching transition where the
 * entry is visible to the associated transaction and the entry is
 * still available, or of the locked entry set goes empty.
 */
class TakeIfExistsWatcher extends SingletonQueryWatcher 
    implements IfExistsWatcher, Transactable
{
    /**
     * The set of entries that would match but are currently
     * unavailable (e.g. they are locked). We only keep
     * the ids, not the entries themselves.
     */
    private final Set lockedEntries;

    /**
     * Set <code>true</code> once the query thread is 
     * done processing the backlog. Once this is 
     * <code>true</code> it is ok to resolve if
     * <code>lockedEntries</code> is empty.
     */
    private boolean backlogFinished = false;

    /**
     * If non-null the transaction this query is
     * being performed under. If <code>null</code> 
     * this query is not associated with a transaction.
     */
    private final Txn txn;

    /**
     * Set of entries (represented by <code>EntryHolder</code>s) that
     * we would have liked to return, but have been provisionally
     * removed.
     */
    private final WeakHashMap provisionallyRemovedEntrySet;

    /**
     * Create a new <code>TakeIfExistsWatcher</code>.
     * @param expiration the initial expiration time
     *        for this <code>TransitionWatcher</code> in 
     *        milliseconds since the beginning of the epoch.
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     * @param lockedEntries Set of entries (by their IDs)
     *        that match but are unavailable. Must be non-empty.
     *        Keeps a reference to this object.
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
     * @throws NullPointerException if <code>lockedEntries</code> is
     *         <code>null</code>.
     */
    TakeIfExistsWatcher(long expiration, long timestamp, 
	 long startOrdinal, Set lockedEntries, 
         WeakHashMap provisionallyRemovedEntrySet, Txn txn)
    {
	super(expiration, timestamp, startOrdinal);

	if (lockedEntries == null) 
	    throw new NullPointerException("lockedEntries must be non-null");
	
	this.lockedEntries = lockedEntries;
	this.txn = txn;
	this.provisionallyRemovedEntrySet = provisionallyRemovedEntrySet; 
    }

    boolean isInterested(EntryTransition transition, long ordinal) {
	/* If we are unresolved pretty much all transitions are
	 * interesting because we may need to update
	 * lockedEntries.
	 * 
	 * Note, !isResolved() without the lock will result only in
	 * false positives, not false negatives - it will only
	 * cause isInterested() to return false if we are resolved,
	 * we may still return true if we are resolved though.  
	 */
	return (ordinal>startOrdinal) && !isResolved();
    }

    synchronized void process(EntryTransition transition, long now) {
	if (isResolved())
	    return; // Already done.

	final EntryHandle handle = transition.getHandle();
	final EntryRep rep = handle.rep();
	final boolean isAvailable = transition.isAvailable();
	final TransactableMgr transitionTxn = transition.getTxn();

	/* If it at one time it was available to our transaction
	 * it may still be, try to get it.
	 */
	if (isAvailable &&
	    ((null == transitionTxn) || txn == transitionTxn)) {
	    /* Is it still available? */
	    if (getServer().attemptCapture(handle, txn, true, null, 
		provisionallyRemovedEntrySet, now, this)) 
	    {
		// Got it
		resolve(handle, null);
	    } else {
		/* Must not have been able to get it. Either
		 * locked under a conflicting lock, in which
		 * case it should be in our lockedEntries set, or
		 * it has been removed, in which it still needs
		 * to be in our lockedEntries since it may have
		 * been replaced before being removed.
		 */
		lockedEntries.add(rep.id());		 
	    }
	} else if (isAvailable) { // but it is not visible to txn
	    /* If we are here then at one time it must have been was
	     * available but not visible to us, implying that it was an
	     * entry written under a transaction and is interesting to
	     * us, but not yet visible. We need to add it lockedEntries
	     * even if has been removed since it could have gotten
	     * replaced before it was removed. If we did not
	     * add it we would be acting on the future.
	     */
	    lockedEntries.add(rep.id());
	} else {
	    /* Must not be available, transition must mark
	     * the resolution of the transaction in such away
	     * that the entry has been removed, remove it 
	     * from the set and see if that makes the set empty.
	     */
	    lockedEntries.remove(rep.id());
	    if (backlogFinished && lockedEntries.isEmpty())
		resolve(null, null);
	}
    }

    synchronized boolean catchUp(EntryTransition transition, long now) {
	if (isResolved())
	    return true; // Already done.

	final EntryHandle handle = transition.getHandle();
	final EntryRep rep = handle.rep();
	final boolean isAvailable = transition.isAvailable();
	final TransactableMgr transitionTxn = transition.getTxn();

	/* If it at one time it was available to our transaction
	 * it may still be, try to get it.
	 */
	if (isAvailable &&
	    ((null == transitionTxn) || txn == transitionTxn)) {
	    /* Is it still available? Try to get it. attemptCapture will
	     * add the entry to lockedEntries for us if we could not
	     * get it and it is still in the space (but locked).
	     * Nothing will be added if it has been removed outright. 
	     * This is ok even though we are peaking into the future -
	     * we won't act on that information until the future
	     * comes to pass.
	     */
	    if (getServer().attemptCapture(handle, txn, true,
		lockedEntries, provisionallyRemovedEntrySet, now, this)) 
	    {
		// Got it
		resolve(handle, null);
		return true;
	    }

	    // did not resolve
	    return false;
	}

	if (isAvailable) { // but it is not visible to txn
	    /* If we are here then at one time it must have been was
	     * available but not visible to us, implying that it was
	     * an entry written under a transaction and is interesting
	     * to us, but not yet visible. We only add if it has not
	     * already been removed.  It might have gotten replaced
	     * before removal, but since we won't let this query get
	     * resolved with a definitive null before we get past the
	     * point in the journal where it was removed it is ok to
	     * never put it in (and if we did put it in it might never
	     * get removed since process() may have already processed
	     * the removal). We don't need to check to see it the
	     * entry has been provisionally removed since if has been
	     * provisional removal does not put in entries in the
	     * journal and if it is provisionally removed it has not
	     * yet been removed so the remove recored has not yet been
	     * created (must less processed).
	     */
	    synchronized (handle) {
		if (!handle.removed()) {
		    lockedEntries.add(rep.id());
		}

		/* If it has been removed, there is no way it
		 * will be interesting to us again, ever.
		 */
	    }
	    // Either way, still not resolved.
	    return false;
	}

	/* Must not be available, transition must mark
	 * the resolution of the transaction in such away
	 * that the entry has been removed, remove it 
	 * from the set (don't need to check for empty
	 * because we haven't gotten to the point
	 * where we can resolve with a definitive null.)
	 */
	lockedEntries.remove(rep.id());
	return false;
    }

    /**
     * Once the backlog is complete we can resolve if 
     * lockedEntries is/becomes empty.
     */
    public synchronized void caughtUp() {
	backlogFinished = true;
	
	if (isResolved())
	    return; // Don't much mater.

	if (lockedEntries.isEmpty())
	    resolve(null, null);	
    }

    public synchronized boolean isLockedEntrySetEmpty() {
	if (!isResolved())
	    throw new IllegalStateException("Query not yet resolved");	
	return lockedEntries.isEmpty();
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
	    "non-transactional TakeIfExistsWatcher";

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
