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
import java.util.Iterator;
import java.util.WeakHashMap;

import net.jini.core.transaction.TransactionException;
import net.jini.space.InternalSpaceException;

/** 
 * Subclass of <code>QueryWatcher</code> for blocking take multiple
 * queries. Most of the usage model is laid out in
 * <code>QueryWatcher</code> except how the result of the query is
 * obtained from the watcher. <code>SingletonQueryWatcher</code>
 * defines the <code>resolvedWithEntry</code> and
 * <code>resolvedWithThrowable</code> methods which can be used to
 * obtain the entries or throwable the query was resolved with.
 */
class TakeMultipleWatcher extends QueryWatcher implements Transactable {
    /** The entries (as handles) this watcher has captured */
    private final Set handles = new java.util.HashSet();

    /** The maxium number of entries that should be captured */
    private final int limit;

    /**
     * Set of entries (represented by <code>EntryHolder</code>s) that
     * we would have liked to return, but have been provisionally
     * removed.
     */
    private final WeakHashMap provisionallyRemovedEntrySet;

    /**
     * If non-null the transaction this query is
     * being performed under. If <code>null</code> 
     * this query is not associated with a transaction.
     */
    private final Txn txn;

    /** Set to true when this query is resolved */
    private boolean resolved = false;

    /** 
     * If resolved and an exception needs to be thrown the exception
     * to throw
     */
    private Throwable toThrow;

    /**
     * The <code>TemplateHandle</code>s associated with this
     * watcher.
     */
    private Set owners = new java.util.HashSet();
    
    /**
     * The OutriggerServerImpl we are part of.
     */
    private OutriggerServerImpl server;

    /**
     * <code>true</code> if we have processed the transition that
     * occurred during the initial search and are now blocking waiting
     * for a match to appear
     */
    private boolean blocking = false;

    
    /**
     * Create a new <code>TakeMultipleWatcher</code>.
     * @param limit the maximum number of entries that should
     *        be captured by this watcher
     * @param expiration the initial expiration time
     *        for this <code>TakeMultipleWatcher</code> in 
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
     *        associated with that transaction.  */
    TakeMultipleWatcher(int limit, long expiration, long timestamp, 
        long startOrdinal, WeakHashMap provisionallyRemovedEntrySet, Txn txn)
    {
	super(expiration, timestamp, startOrdinal);
	this.limit = limit;
	this.provisionallyRemovedEntrySet = provisionallyRemovedEntrySet;
	this.txn = txn;
    }

    /**
     * Associate a <code>TemplateHandle</code> with this object.  May
     * be called more than once.
     *
     * @param h The <code>TemplateHandle</code> to associate
     *          with this watcher.
     * @return <code>true</code> if the handle was succfully added,
     *         and <code>false</code> if the watcher has already
     *         been removed.
     * @throws NullPointerException if <code>h</code> is 
     *        <code>null</code> 
     */
    synchronized boolean addTemplateHandle(TemplateHandle h) {
	if (owners == null)
	    return false; // Already removed!

	owners.add(h);

	if (server == null)
	    server = h.getServer();

	return true;
    }

    synchronized boolean catchUp(EntryTransition transition, long now) {
	if (resolved)
	    return true;

	final TransactableMgr transitionTxn = transition.getTxn();
	final EntryHandle handle = transition.getHandle();

	if (handles.contains(handle))
	    return false;  // Already got it

	if (transition.isAvailable() &&
	    ((null == transitionTxn) || (txn == transitionTxn)) &&
	    (server.attemptCapture(handle, txn, true, null,
				   provisionallyRemovedEntrySet, now, this)))
	{
	    // Got it
	    captured(handle);
	    return resolved;
	} 

	// Not interesting, or could not get it, either way not resolved
	return false;
    }

    boolean isInterested(EntryTransition transition, long ordinal) {
	/* Note, !resolved without the lock will result only in
	 * false positives, not false negatives - it will only
	 * cause isInterested() to return false if we are resolved,
	 * we may still return true if we are resolved though.
	 */
	final TransactableMgr transitionTxn = transition.getTxn();
	return (ordinal>startOrdinal) && !resolved &&
	    transition.isAvailable() &&
	    ((null == transitionTxn) || (txn == transitionTxn));
    }

    synchronized void process(EntryTransition transition, long now) {
	if (resolved)
	    return; // Already done.

	final EntryHandle handle = transition.getHandle();

	if (handles.contains(handle))
	    return;  // Already got it

	// Is it still available?
	if (server.attemptCapture(handle, txn, true, null,
				  provisionallyRemovedEntrySet, now, this))
	{
	    // Got it
	    captured(handle);
	}
    }

    void waitOnResolution() throws InterruptedException {
	synchronized (this) {
	    blocking = true;

	    if (handles.size() > 0) {
		resolved = true;
	    } else {
		while (!resolved) {
		    final long sleepTime =
			getExpiration() - System.currentTimeMillis();
		    if (sleepTime <= 0) {
			// All done
			resolved = true;
		    } else {
			wait(sleepTime);
		    }
		}
	    }

	    for (Iterator i=owners.iterator(); i.hasNext(); ) {
		final TemplateHandle h = (TemplateHandle)i.next();
		h.removeTransitionWatcher(this);
	    }
	}
    }

    /** 
     * If the query has been resolved by finding an matching entry,
     * returns the <code>EntryHandle</code> for that entry. If the query has
     * been resolved but no entry is available (e.g. the expiration time has
     * been reached or an exception needs to be thrown) returns
     * <code>null</code>. Note, once resolution has been reached this
     * method can only return non-null if <code>resolvedWithThrowable</code>
     * returns <code>null</code>.
     *
     * @return The entry to be returned, or <code>null</code> if
     * no entry is available.
     * @throws IllegalStateException if the query has not
     * yet been resolved.
     */
    synchronized EntryHandle[] resolvedWithEntries() {
	if (!resolved)
	    throw new IllegalStateException("Query not yet resolved");

	if (handles.isEmpty())
	    return null;

	final EntryHandle[] rslt = new EntryHandle[handles.size()];
	return (EntryHandle[])handles.toArray(rslt);
    }

    /**
     * If the query has been resolved with an exceptional condition,
     * the exception that should be thrown to the client. Returns
     * <code>null</code> otherwise.  Note, once resolution has been
     * reached this method can only return non-null if
     * <code>resolvedWithEntry</code> returns <code>null</code>.
     * @return the exception (if any) that should
     * be thrown to the client.
     * @throws IllegalStateException if the query has not 
     * yet been resolved.  
     */
    synchronized Throwable resolvedWithThrowable() {
	if (!resolved)
	    throw new IllegalStateException("Query not yet resolved");

	return toThrow;
    }

    boolean isResolved() {
	return resolved;
    }

    /**
     * Mark this query as resolved. This method assumes
     * the calling thread own the lock on this object.
     * @param handle  If being resolved by finding an entry
     *              the entry which was found and that should be returned
     *              by <code>resolvedWithEntry</code>. Otherwise should be
     *              <code>null</code>.  May only be non-null if throwable
     *              is <code>null</code>.
     * @throws IllegalArgumentException if both 
     *         <code>entry</code> and <code>throwable</code>
     *         are non-null.
     * @throws IllegalStateException if the query has already
     *         been resolved.
     */
    private void captured(EntryHandle handle) {
	handles.add(handle);
	if (handles.size() == limit || blocking) {
	    resolved = true;
	    notifyAll();
	}

	assert (handles.size() <= limit);
    }    

    /**
     * If a transaction ends in the middle of a query we want
     * to throw an exception to the client making the query
     * (not to the <code>Txn</code> calling us here.)
     */
    public synchronized int prepare(TransactableMgr mgr,
				    OutriggerServerImpl space) 
    {
	assert txn != null:"Transactable method called on a " +
	    "non-transactional ConsumingWatcher";

	// only throw an exception if we are not resolved.
	if (!resolved) {
	    // Query still in progress, kill it
	    resolved = true;
	    toThrow = new TransactionException("completed while " +
					       "operation in progress"); 
	    notifyAll();

	    /* If we have a partial result we could try to return it,
	     * but if we are a persistent space the attempt to log
	     * the take will fail with a CannotJoinException, so it
	     * seems cleaner to just fail here (same thing might happen
	     * if we are resolved, but once we think we are resolved
	     * and may have returned from waitOnResolution seem
	     * cleaner to not try and set toThrow). 
	     */
	}

	// If this object has made changes they have been recorded elsewhere
	return NOTCHANGED;
    }

    /**
     * This should never happen since we always return
     * <code>NOTCHANGED</code> from <code>prepare</code>.  */
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

