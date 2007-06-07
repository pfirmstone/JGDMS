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

/**
 * Subclass of <code>QueryWatcher</code> for non-transactional if
 * exists reads. Resolves with the first matching
 * transition where the transaction is <code>null</code> and the entry
 * is visible (the entry's current state is ignored) or if 
 * the locked entry set goes empty.
 */
class ReadIfExistsWatcher extends SingletonQueryWatcher
    implements IfExistsWatcher 
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
     * Create a new <code>ReadIfExistsWatcher</code>.
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
     * @throws NullPointerException if <code>lockedEntries</code> is
     *         <code>null</code>.
     */
    ReadIfExistsWatcher(long expiration, long timestamp, long startOrdinal, 
			Set lockedEntries)
    {
	super(expiration, timestamp, startOrdinal);

	if (lockedEntries == null) 
	    throw new NullPointerException("lockedEntries must be non-null");
	
	this.lockedEntries = lockedEntries;
    }

    boolean isInterested(EntryTransition transition, long ordinal) {
	/* If we are unresolved pretty much all transitions are
	 * interesting because we may need to update
	 * lockedEntries. The only exception is read locks being
	 * resolved. It is important that transitions triggered by the
	 * release of read locks get filtered out - otherwise process
	 * could end up adding and removing elements to lockedEntries
	 * when it shouldn't.
	 * 
	 * Note, !isResolved() without the lock will result only in
	 * false positives, not false negatives - it will only
	 * cause isInterested() to return false if we are resolved,
	 * we may still return true if we are resolved though.  
	 */

	if (!transition.isVisible() && transition.isAvailable()) {
	    /* must be a transition triggered by a read lock release,
	     * wont change anything so ignore it.
	     */
	    return false;
	}

	return (ordinal>startOrdinal) && !isResolved();
    }

    synchronized void process(EntryTransition transition, long now) {
	if (isResolved())
	    return; // Already done.

	final EntryRep rep = transition.getHandle().rep();
	final boolean isVisible = transition.isVisible();

	/* If the entry was visible at one time to the null 
	 * transaction we can just resolve.
	 */
	if (isVisible && (transition.getTxn() == null)) {
	    resolve(transition.getHandle(), null);
	} else if (isVisible) { // && getTxn() != null
	    /* If we are here transition.getTxn() must be != null 
	     * and the entry was visible, implying that it was an entry
	     * written under a transaction and is interesting to 
	     * us, but not yet visible. We need to add it lockedEntries
	     * even if has been removed since it could have gotten
	     * replaced before it was removed. If we did not
	     * add it we would be acting on the future.
	     */
	    lockedEntries.add(rep.id());
	} else {
	    /* Must not be visible (and becaues of the test in
	     * isInteresting can't be available either - that is
	     * transition can't just be the release of a read lock),
	     * transition must mark the resolution of the transaction
	     * in such away that the entry has been removed, remove it
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
	final boolean isVisible = transition.isVisible();

	/* Was this the resolution of a read lock? if so ignore */
	if (!isVisible && transition.isAvailable())
	    return false;

	/* If the entry was visible at one time to the null 
	 * transaction we can just resolve.
	 */
	if (isVisible && (transition.getTxn() == null)) {
	    resolve(handle, null);
	    return true;
	} 

	if (isVisible) { // && getTxn() != null
	    /* If we are here transition.getTxn() must be != null and
	     * the entry is/was visible to someone, implying that it
	     * was an entry written under a transaction and is
	     * interesting to us, but not yet visible to us. We only
	     * add if it has not already been removed.  It might have
	     * gotten replaced before removal, but since we won't let
	     * this query get resolved with a definitive null before
	     * we get past the point in the journal where it was
	     * removed it is ok to never put it in (and if we did put
	     * it in it might never get removed since process() may
	     * have already processed the removal). We don't need to
	     * check to see it the entry has been provisionally
	     * removed since if has been provisional removal does not
	     * put in entries in the journal and if it is
	     * provisionally removed it has not yet been removed so
	     * the remove recored has not yet been created (must less
	     * processed).  
	     */
	    synchronized (handle) {
		if (!handle.removed()) {
		    lockedEntries.add(rep.id());
		}		
	    }

	    // Either way, still not resolved.
	    return false;
	}
	 
	/* Must not be visible (and because of the first test can't be
	 * available either - that is transition can't just be the
	 * release of a read lock), transition must mark the
	 * resolution of the transaction in such away that the entry
	 * has been removed, remove it from the set (don't need to
	 * check for empty because we haven't gotten to the point
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
}
