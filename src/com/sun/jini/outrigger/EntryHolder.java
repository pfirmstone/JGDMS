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

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.core.entry.UnusableEntryException;
import net.jini.core.transaction.CannotJoinException;
import net.jini.core.transaction.server.TransactionConstants;

/**
 * <code>EntryHolder</code>s hold all the entries of a exact given
 * class. <code>OutriggerServerImpl</code> has one
 * <code>EntryHolder</code> for each entry class it knows about. A
 * simple implementation is used that simply stores the entries in a
 * list.
 *
 * @author Sun Microsystems, Inc.
 */
class EntryHolder implements TransactionConstants {
    /** The list that holds the handles */
    private final FastList<EntryHandle> contents = new FastList<EntryHandle>();

    /** 
     * The map of cookies to handles, shared with the
     * <code>EntryHolderSet</code> and every other
     * <code>EntryHolder</code>.  
     */
    private final Hashtable idMap;

    /** The server we are working for */
    private final OutriggerServerImpl space;

    /** Logger for logging information about entry matching */
    private static final Logger matchingLogger = 
	Logger.getLogger(OutriggerServerImpl.matchingLoggerName);

    /** Logger for logging information about iterators */
    private static final Logger iteratorLogger =
	Logger.getLogger(OutriggerServerImpl.iteratorLoggerName);

    /**
     * Create a new <code>EntryHolder</code> with the shared
     * <code>idMap</code>, and which will hold classes of the given
     * <code>className</code>.  The <code>idMap</code> is shared with
     * <code>EntryHolderSet</code> so that there is one table that can
     * map ID to <code>EntryRep</code>
     */
    EntryHolder(OutriggerServerImpl space, Hashtable idMap) {
	this.space = space;
	this.idMap = idMap;
    }

    /**
     * Return an <code>EntryHandle</code> object that matches the given
     * template, or <code>null</code> if none does. Optionally
     * removes (perhaps provisionally) the found entry.
     *
     * @param tmpl	The template to match against
     * @param txn       If non-null the transaction (represented as
     *                  a <code>TransactableMgr</code> to perform
     *                  the operation under. May be <code>null</code>
     *                  if the operation is not to be done under
     *                  a transaction.
     * @param takeIt	<code>true</code> if <code>hasMatch</code> should
     *			remove the matching entry.
     * @param conflictSet If non-null the <code>TransactableMgr</code>
     *                  objects of any transactions that prevent 
     *                  a non-null value from being retured will
     *                  be added to <code>conflictSet</code>. May
     *                  be <code>null</code> in which case
     *                  conflicting transaction will not be recorded.
     *                  This method assumes that any concurrent access
     *                  is being arbitrated by the set or by the caller.
     * @param lockedEntrySet If non-null the ID of any entries that
     *                  can't be retured because of conflicting
     *                  transaction will be added to
     *                  <code>lockedEntrySet</code>. May be
     *                  <code>null</code> in which case unavailable
     *                  entries will not be recorded.  This method 
     *                  assumes that any concurrent access is being
     *                  arbitrated by the set or by the caller.
     * @param provisionallyRemovedEntrySet If the entry can not be
     *              read/taken because it has been provisionally
     *              removed then its handle will be placed in the
     *              passed <code>WeakHashMap</code> as a key (with
     *              null as the value).  May be <code>null</code> in
     *              which case provisionally removed entries will not
     *              be recorded. This method assumes that any
     *              concurrent access is being arbitrated by the set
     *              or by the caller.
     * @throws CannotJoinException if a match is found and
     *         the operation is to be performed under a transaction,
     *         but the transaction is no longer active.
     * @see #attemptCapture 
     */
    EntryHandle hasMatch(EntryRep tmpl, TransactableMgr txn, boolean takeIt,
            Set conflictSet, Set lockedEntrySet,
            WeakHashMap provisionallyRemovedEntrySet)
            throws CannotJoinException {
        matchingLogger.entering("EntryHolder", "hasMatch");
        EntryHandleTmplDesc desc = null;
        long startTime = 0;

        for (EntryHandle handle : contents) {

            if (startTime == 0) {
                // First time through
                desc = EntryHandle.descFor(tmpl, handle.rep().numFields());
                startTime = System.currentTimeMillis();
            }

            if (handle.removed())
                continue;

            // Quick reject -- see the if handle mask is incompatible
            if ((handle.hash() & desc.mask) != desc.hash)
                continue;

            final EntryRep rep = handle.rep();

            if (!tmpl.matches(rep))
                continue;

            final boolean available = confirmAvailabilityWithTxn(rep, handle,
                    txn, takeIt, startTime, conflictSet, lockedEntrySet,
                    provisionallyRemovedEntrySet);

            if (available)
                return handle;
        }

        return null;
    }
    

    /**
     * Debug method:  Dump out the state of this holder, printing out
     * the name of the dump first.
     */
    void dump(String name) {
	try {
	    System.out.println(name);
	    for (EntryHandle handle : contents)
	    {
		EntryRep rep = handle.rep();
		System.out.println("    " + rep + ", " + rep.entry());
	    }
	} catch (UnusableEntryException e) {
	    e.printStackTrace();
	}
    }

    /**
     * Atomically check to see if the passed entry can be read/taken by
     * the specified operation using the specified transaction and if
     * it can read/take it and return <code>true</code>, otherwise
     * return <code>false</code>. If the entry is removed. Note,
     * if the entry is removed, removal is logged.
     * @param handle The <code>EntryHandle</code> of the entry
     *              the caller wants to read/take.
     * @param txn   If non-null the transaction to perform
     *              this operation under. Note, if non-null and 
     *              <code>txn</code> is not active <code>false</code>
     *              will be returned.
     * @param takeIt <code>true</code> if the caller is trying
     *              take the passed entry, <code>false</code>
     *              otherwise.
     * @param conflictSet If non-null and the entry can not be
     *              read/taken because of transaction conflicts the
     *              conflicting transaction(s) will be added to this set.
     *              This method assumes that any concurrent access is
     *              being arbitrated by the set or by the caller.
     * @param lockedEntrySet If the entry can not be read/taken
     *              because of a transaction conflict, the ID of the
     *              entry will be added to this set. This method
     *              assumes that any concurrent access is being arbitrated
     *              by the set or by the caller.
     * @param provisionallyRemovedEntrySet If the entry can not be
     *              read/taken because it has been provisionally
     *              removed then its handle will be placed in the
     *              passed <code>WeakHashMap</code> as a key (with
     *              null as the value).  May be <code>null</code> in
     *              which case provisionally removed entries will not
     *              be recorded. This method assumes that any
     *              concurrent access is being arbitrated by the set
     *              or by the caller.
     * @param now an estimate of the current time in milliseconds
     *            since the beginning of the epoch.
     * @return <code>true</code> if the entry could be read/taken and
     *         <code>false</code> otherwise.
     * @throws NullPointerException if entry is <code>null</code>.  
     */
    boolean attemptCapture(EntryHandle handle, TransactableMgr txn,
	boolean takeIt, Set conflictSet, Set lockedEntrySet, 
        WeakHashMap provisionallyRemovedEntrySet, long now)
    {
	try {
	    return confirmAvailabilityWithTxn(handle.rep(), handle,
		txn, takeIt, now, conflictSet, lockedEntrySet, 
		provisionallyRemovedEntrySet);
	} catch (CannotJoinException e) {
	    return false;
	}
    }

    /* Now that we know we have a match, make sure that the the
     * item in question is still in the space and hasn't been
     * subject to other transactional-interference...
     */
    private boolean confirmAvailabilityWithTxn(EntryRep rep, 
	      EntryHandle handle, TransactableMgr txnMgr, boolean takeIt, 
	      long time, Set conflictSet, Set lockedEntrySet,
	      WeakHashMap provisionallyRemovedEntrySet)
	throws CannotJoinException
    {
	// Now that we know we have a match, make sure that the the
	// item in question is still in the space and hasn't been
	// subject to other transactional-interference...

	// Lock the txn first (since that what everybody else does that)
	// to ensure that the locking always happens in A cannonical
	// order. We lock the transaction (txn) object to ensure that the
	// transaction hasn't been altered behind our backs. Other, cheaper
	// actions with the space (write, etc.) are handled nicely in 
	// OutriggerServerImpl. But, this one (reading/taking) was expensive
	// enough that we wanted to do it here, where the window is
	// smallest.
	// Bug 4394263...
	
	final Txn txn = (Txn)txnMgr;
	try {
	    if (txn != null) 
		txn.ensureActive();

	    return confirmAvailability(rep, handle, txn,
		takeIt, time, conflictSet, lockedEntrySet, 
		provisionallyRemovedEntrySet);
	} finally {
	    if (txn != null) 
		txn.allowStateChange();
	}
    }

    /**
     * With the EntryRep <code>rep</code> passed in, verify that the
     * entry hasn't been taken by someone else, hasn't expired, etc.
     * Also, verify that the entry is really (legally) visible to this
     * transaction at this time. If this is a <code>take</code>, it it
     * is removed or provisionally removed. If this operation is under
     * a transaction, the entry is locked appropriately.
     */
    // @see grab -- a helper routine
    private boolean
	confirmAvailability(EntryRep rep, EntryHandle handle,
	      TransactableMgr txn, boolean takeIt, long time,
	      Set conflictSet, Set lockedEntrySet,
	      WeakHashMap provisionallyRemovedEntrySet)
    {
	if (handle.removed())
	    return false;

	synchronized (handle) {
	    // get rid of stale entries
	    if (isExpired(time, handle))
		return false;
	    if (handle.removed())    // oh, well -- someone got it first
		return false;
	    if (handle.isProvisionallyRemoved()) {
		if (provisionallyRemovedEntrySet != null)
		    provisionallyRemovedEntrySet.put(handle, null);
		return false;
	    }

	    int op = (takeIt ? TransactableMgr.TAKE : TransactableMgr.READ);
	    if (!handle.canPerform(txn, op)) {
		// Before deciding this is a conflict, we have to
		// make sure we are not conflicting with ourselves.
		if (handle.onlyMgr(txn))
		    // We are just conflicting with ourselves, this entry
		    // must have been taken by this txn already ... oh well
		    return false;
	       
		// Must be a real conflict, log if we were told too
		if (matchingLogger.isLoggable(Level.FINER)) {
		    matchingLogger.log(Level.FINER, "match, but can''t " +
			"perform {0}; handle.knownMgr(txn) == {1}", 
			new Object[] {new Integer(op), 
				      new Boolean(handle.knownMgr(txn))});
		}

		if (conflictSet != null) {
		    handle.addTxns(conflictSet);
		} 

		if (lockedEntrySet != null) {
		    lockedEntrySet.add(rep.id());
		}

		return false;
	    }

	    if (grab(handle, txn, op, takeIt, false))
		return true;
	    else 
		throw 
		    new AssertionError("entry became non-available while locked");
	}
    }

    /**
     * Given an entry that we want to return as the result of a query
     * and we have confirmed we can return it, make the results of the
     * query visible to the rest of the service. We must either [a]
     * remove it from the holder (if the operation is a take under the
     * same transaction the entry was written under), [b] mark it as
     * provisionally removed pending commiting the operation to disk
     * (if the operation is a take without a transaction), [c]
     * lock it under the transaction passed into this method (if the
     * operation is a read or take under a transaction), or [d] do
     * nothing (read not under a transaction) as appropriate. 
     * <p>
     * Also used during log recovery to recover takes.
     *
     * @param handle The handle attached to the particular EntryRep
     * @param txn The Txn object
     * @param op TAKE or READ (as a TransactableMgr constant)
     * @param takeIt Is this a TAKE (or is it [false] a READ)
     * @param recovery <code>true</code> if being called as
     *        part of store recovery.
     * @return <code>true</code> if the entry could be grabbed.
     * @throws NullPointerException if <code>handle</code> is
     *         <code>null</code>.  
     */
    private boolean grab(EntryHandle handle, TransactableMgr txn, int op,
			 boolean takeIt, boolean recovery)
    {
	assert op==(takeIt?TransactableMgr.TAKE:TransactableMgr.READ);
	assert !recovery || takeIt;

	/*
	 * If this manager (txn) is known to the handle, then txn
	 * is a transaction which can operate directly on the
	 * existing handle.  If so, then we can operate on it
	 * directly.  Note that if txn is null and the handle is
	 * not managed, then txn is "known" in this sense.
	 *
	 * Else, this operation is new relative to the
	 * known transactions (possibly null) and txn.  
	 */

	if (handle.knownMgr(txn)) {    // added or read in this txn if taking
	    // take it if we have to (otherwise it must be a read
	    // there is no house keeping to be done since must already
	    // be read locked)

	    if (takeIt) {
		// is this an entry that was written under this
		// transaction or another?

		if (handle.managed()) {
		    // It is locked by a transaction and it must
		    // be the one this take is under.

		    if (!handle.promoteToTakeIfNeeded()) {
			// this transaction wrote the entry (note txn
			// must be non-null, otherwise either
			// handle.managed or handle.knownMgr(txn)
			// would have returned null), just need to try
			// and remove it
			assert txn != null;
			
			// It is ok to call remove before committing this
			// op to disk since a crash will cause the original
			// write to be undone and the entry will still 
			// end up being removed.
			if (!remove(handle, recovery))
			    // Someone got to it first
			    return false;
		    }

		    // Otherwise it was read locked (now take
		    // locked and needs to still be semi-visible
		    // to ifExists queries outside of the
		    // transaction, leave it in place with its new
		    // lock type (aka "state")

		} else {
		    // Nether the entry nor this take are under a
		    // transaction (txn must be null because the
		    // that is the only way knownMgr return true, and
		    // managed() false)
		    assert txn == null;
		    if (handle.removed() || handle.isProvisionallyRemoved())
			// Someone got to it first
			return false;
		    handle.provisionallyRemove();
		}
	    }
	} else {
	    if (txn != null) {
		// We need to add this txn to this handle and
		// add this handle to this txn.
		handle.add(txn, op, this); // add manager to handle's list
		txn.add(handle);	    // add handle to mgr's list
	    }
	}
	return true;
    }

    /**
     * Recover a logged take.
     * @param handle The <code>EntryHandle</code> of the entry who's
     *               take is being logged.
     * @param txn    If non-null the transaction the take was performed
     *               under.
     * @throws NullPointerException if <code>handle</code> is 
     *         <code>null</code>.
     */
    void recoverTake(EntryHandle handle, Txn txn) {
	if (!grab(handle, txn, TransactableMgr.TAKE, true, true))
	    throw 
		new AssertionError("match not found while recovering take");
    }


    /**
     * Return <code>true</code> if the entry held by the given
     * <code>handle</code> has expired by the time in <code>now</code>.
     */
    private boolean isExpired(long now, EntryHandle handle) {
	/* Some callers already own the lock on handle when
	 * they call us, but the general feeling is that
	 * re-synchronizing is not costly and it is important
	 * to only call remove if we own the lock.
	 */
	synchronized (handle) {
	    final EntryRep rep = handle.rep();
	    synchronized (rep) {
		if (rep.getExpiration() > now) {
		    // Not expired
		    return false;
		}

		/* Expired, set expiration to before the beginning of
		 * time so renew() (which does not lock on the handle,
		 * but the rep...) can't renew the lease once we leave
		 * this block, even it already has a ref to the
		 * rep. Everyone else locks on the handle so
		 * and makes sure the handle has not been removed
		 * before doing anything important.
		 */
		rep.setExpiration(Long.MIN_VALUE);
	    }

	    // If we are here the lease must have expired
	    if (matchingLogger.isLoggable(Level.FINER)) {
		matchingLogger.log(Level.FINER, "expired {0} at {1} (now {2})",
		    new Object[] {rep.id(), 
				  new Long(rep.getExpiration()),
				  new Long(now)});
	    }

	    if (!handle.isProvisionallyRemoved() && remove(handle, false)) {
		/* If we got to do the remove, schedule
		 * logging of the removal, otherwise
		 * someone else already did it, or will do it
		 */
		space.scheduleCancelOp(rep.id());
	    }

	    return true;
	}
    }
    
    /**
     * Get the head of the contents list
     * @return The head of the contents list, if it exists.
     * null if the list is empty.
     */
    private EntryHandle getContentsHead(){
        for(EntryHandle head : contents){
            return head;
        }
        return null;
    }
    
    /**
     * Add new new entry to the holder. Assumes the lock
     * on the handle is held if there is a possibility
     * of concurrent access.
     * @param handle The <code>EntryHandle</code> for the
     *               entry being added.
     * @param txn If the add is being done under a 
     *            transaction the <code>TransactableMgr</code> for
     *            that transaction.
     * @throws NullPointerException if <code>handle</code> is 
     *         <code>null</code>.
     */
    void add(EntryHandle handle, TransactableMgr txn) {
	final EntryRep rep = handle.rep();

	/* Make sure info duplicated across all the handles in this
	 * holder is shared.
	 *
	 * Because this thread will usually hold the lock on handle,
	 * we must call contents.head before calling contents.add
	 * otherwise during the head call this thread will attempt to
	 * obtain a lock on a 2nd FastList node in the same FastList,
	 * and make that attempt in the wrong order. In particular
	 * this could lead to deadlocks when SimpleRepEnum or
	 * ContinuingQuery call into FastList.Node.restart ( restart
	 * locks the node its called on (which can be the same node
	 * head locks) and then tries to lock the tail (which can be
	 * handle if add has already been called)). By calling head
	 * before calling add we have locks on two FastList.Node
	 * objects, but they are not in the same list so we are ok.
	 *
	 * We make the head call before calling txn.add because
	 * it seems like better hygiene to fix reps state before
	 * exposing it to others (even though shareWith will
	 * not change handle's state materially).  
	 */
	final EntryHandle head = getContentsHead();
	if (head != null && head != handle)
	    rep.shareWith(head.rep());

	if (txn != null) 
	    txn.add(handle);

	contents.add(handle);
	idMap.put(rep.getCookie(), handle);
    }

    /**
     * Return an array of the class names of the super classes of the
     * entries in this holder, or <code>null</code> if the holder is
     * empty.
     */
    String[] supertypes() {
	final EntryHandle head = getContentsHead();
	if (head == null)
	    return null;
	return head.rep().superclasses();
    }

    /**
     * Return an enumerator over the contents of this space that are visible
     * from the given mgr.
     */
    RepEnum contents(TransactableMgr mgr) {
	return new SimpleRepEnum(mgr);
    }

    /**
     * The class that implements <code>RepEnum</code> for this class.
     */
    private class SimpleRepEnum implements RepEnum {
        private Iterator<EntryHandle> contentsIterator;
	private TransactableMgr mgr;
	private long startTime;

        SimpleRepEnum(TransactableMgr mgr) {
            this.mgr = mgr;
            startTime = System.currentTimeMillis();
            contentsIterator = contents.iterator();
        }

        // inherit doc comment from superclass
        public EntryRep nextRep() {
            iteratorLogger.entering("SimpleRepEnum", "nextRep");
            while (contentsIterator.hasNext()) {
                EntryHandle handle = contentsIterator.next();
                iteratorLogger.log(Level.FINEST,
                        "advanced current handle to {0}", handle);
                /*
                 * Skip over handles which are either removed or unable to
                 * perform a READ operation.
                 */
                if (handle.canPerform(mgr, TransactableMgr.READ)
                        && !isExpired(startTime, handle) && !handle.removed()) {
                    return handle.rep();
                }

            }

            return null;
        }
    }

    
    /**
     * Return an object that can be used to perform a query that can
     * return multiple matches and be restarted in another thread.
     *
     * @param tmpls     An array of templates. Query will yield any 
     *                  entry that matches one or more of the templates.
     * @param txn       Transaction that should be used with the query.
     *                  May be <code>null</code>. If
     *                  non-<code>null</code> any entries yielded by the
     *                  query will be locked under the transaction.
     * @param takeThem  If <code>true</code> any entries yielded by
     *                  the query should be removed.
     * @param now       Estimate of current time used to weed out
     *                  expired entries, ok if old
     * @return a new ContinuingQuery object.
     */
    ContinuingQuery continuingQuery(EntryRep[] tmpls, TransactableMgr txn,
				    boolean takeThem, long now)
    {
	return new ContinuingQuery(tmpls, txn, takeThem, now);
    }

    /**
     * Object that can be used to perform a query that can
     * return multiple matches and be restarted in another thread.
     * Assumes that is being invoked from only one thread
     * at a time, but does handle synchronization between concurrent
     * queries on the parent <code>EntryHolder</code>.
     */
    class ContinuingQuery {
	/** Templates being used for the query */
	final private EntryRep[] tmpls;

	/** Transaction (if any) being used for the query */
	final private TransactableMgr txn;

	/**
	 * <code>true</code> if entries yielded by the query should 
	 * removed.
	 */
	final private boolean takeThem;

	/** <code>EntryHandleTmplDesc</code> for the templates */
	private EntryHandleTmplDesc[] descs;
	    

	/** Time used to weed out expired entries, ok if old */
	long now;

	/** 
	 * Current position in parent <code>EntryHolder</code>'s
	 * <code>contents</code> 
	 */
	private Iterator<EntryHandle> contentsIterator;

	/**
	 * Create a new <code>ContinuingQuery</code> object.
	 *
	 * @param tmpls    An array of templates. Query will yield any 
	 *                 entry that matches one or more of the templates.
	 * @param txn      Transaction that should be used with the query.
	 *                 May be <code>null</code>. If
	 *                 non-<code>null</code> any entries yielded by the
	 *                 query will be locked under the transaction.
	 * @param takeThem If <code>true</code> any entries yielded by
	 *                 the query should be removed.
	 * @param now      Estimate of current time used to weed out
	 *                 expired entries, ok if old
	 */
        // 	 * @return a new ContinuingQuery object. (?)
	private ContinuingQuery(EntryRep[] tmpls, TransactableMgr txn,
				boolean takeThem, long now)
	{
	    this.tmpls = tmpls;
	    this.txn = txn;
	    this.takeThem = takeThem;
	    this.now = now;
	    contentsIterator = contents.iterator();
	}

	/**
	 * <code>EntryHolder</code> queries have thread local state
	 * that get clobbered if another query (on a different or same
	 * <code>EntryHolder</code>, including <code>hasMatch</code>
	 * calls) is started in the same thread. The state also needs
	 * to be restored if it the query is continued in another
	 * thread. The <code>restart</code> method must be called to
	 * restore the thread local state.
	 * @param now      Estimate of current time used to weed out
	 *                 expired entries, ok if old
	 */
	void restart(long now) {
	    if (!contentsIterator.hasNext())
		return;

	    this.now = now;
	}

	/**
	 * Return the next matching entry. Returns <code>null</code>
	 * if there are no matches remaining. Call {@link #restart
	 * restart} first if query is being used in a different thread
	 * from the last <code>next</code> call and/or the current
	 * thread has done any form of <code>EntryHolder</code> query
	 * on any holder since the last next call.
	 *
	 * @param conflictSet If non-null the <code>TransactableMgr</code>
	 *              objects of any transactions that prevent 
	 *              a entry from being retured will
	 *              be added to <code>conflictSet</code>. May
	 *              be <code>null</code> in which case
	 *              conflicting transaction will not be recorded.
	 *              This method assumes that any concurrent access
	 *              is being arbitrated by the set or by the caller.
	 * @param lockedEntrySet If non-null the ID of any entries that
	 *              can't be retured because of conflicting
	 *              transaction will be added to
	 *              <code>lockedEntrySet</code>. May be
	 *              <code>null</code> in which case unavailable
	 *              entries will not be recorded.  This method 
	 *              assumes that any concurrent access is being
	 *              arbitrated by the set or by the caller.
	 * @param provisionallyRemovedEntrySet If the entry can not be
	 *              read/taken because it has been provisionally
	 *              removed then its handle will be placed in the
	 *              passed <code>WeakHashMap</code> as a key (with
	 *              null as the value).  May be <code>null</code> in
	 *              which case provisionally removed entries will not
	 *              be recorded. This method assumes that any
	 *              concurrent access is being arbitrated by the set
	 *              or by the caller.
	 * @return a matching entry or <code>null</code>.
	 * @throws CannotJoinException if a match is found and
	 *         the operation is to be performed under a transaction,
	 *         but the transaction is no longer active.
	 */
	EntryHandle next(Set conflictSet, Set lockedEntrySet,
			 WeakHashMap provisionallyRemovedEntrySet) 
	    throws CannotJoinException
	{
	    matchingLogger.entering("ContinuingQuery", "next");


	    while (contentsIterator.hasNext()) {
	        EntryHandle handle = contentsIterator.next();
	        if(descs == null){
	            // first time
	            descs = new EntryHandleTmplDesc[tmpls.length];
	            for (int i=0; i<tmpls.length; i++) {
	                descs[i] = EntryHandle.descFor(tmpls[i], 
	                                               handle.rep().numFields());
	            }
	        }
		if (handleMatch(handle)) {
		    
		    final boolean available =
			confirmAvailabilityWithTxn(handle.rep(), handle, txn, 
			    takeThem, now, conflictSet, lockedEntrySet, 
			    provisionallyRemovedEntrySet);

		    if (available) {
			return handle;
		    }
		}
	    }

	    return null;
	}

	/**
	 * Returns <code>true</code> if handle has not been removed
	 * and matches one or more of the templates 
	 */
	private boolean handleMatch(EntryHandle handle) {
	    if (handle.removed())
		return false;

	    for (int i=0; i<tmpls.length; i++) {
		final EntryRep tmpl = tmpls[i];
		final EntryHandleTmplDesc desc = descs[i];

		// Quick reject
		if ((handle.hash() & desc.mask) != desc.hash)
		    continue;
		if (!tmpl.matches(handle.rep()))
		    continue;
		return true;
	    }

	    return false;
	}
    }

    /**
     * Remove the given handle from this holder and the <code>idMap</code>.
     * If the handle isn't in this holder, this does nothing.
     * @param h the <code>EntryHandle</code> to remove.
     * @param recovery <code>true</code> if being called as part
     *        of log recovery.
     * @return <code>true</code> if this call removed <code>h</code> and
     *         <code>false</code> otherwise.  
     */
    boolean remove(EntryHandle h, boolean recovery) {
	assert (recovery || Thread.holdsLock(h));

	final boolean ok = contents.remove(h);
	h.removalComplete();
	if (ok) {
	    idMap.remove(h.rep().getCookie());
	    /* This may cause an ifExists query to be resolved,
	     * even if this entry was not locked under a transaction 
	     */
	    if (!recovery) {
		space.recordTransition(
		    new EntryTransition(h, null, false, false, false));
	    }
	}

	return ok;
    }

    /**
     * Return the handle for the given <code>EntryRep</code> object.  
     * This is done via lookup in the <code>idMap</code>.  The 
     * <code>idMap</code> is doing double duty here.  The 
     * <code>LeaseDesc</code> associated with an <code>EntryRep</code> 
     * is also the rep's <code>EntryHandle</code>.
     */
    private EntryHandle handleFor(EntryRep rep) {
	return (EntryHandle) idMap.get(rep.getCookie());
    }

    /**
     * Reap the expired elements (and the underlying FastList)
     */
    void reap() {

	// Examine each of the elements within the FastList to determine if
	// any of them have expired. If they have, ensure that they are
	// removed ("reaped") from the collection. 

	long now = System.currentTimeMillis();
	for(EntryHandle handle : contents){
	    // Don't try to remove things twice
	    if (handle.removed()) {
		continue;
	    }

	    // Calling isExpired() will both make the check and remove it
	    // if necessary.
	    isExpired(now, handle);
	}

	// This provides the FastList with an opportunity to actually
	// excise the items identified as "removed" from the list.
	contents.reap();
    }
}

