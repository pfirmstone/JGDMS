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

/**
 * Subclass of <code>QueryWatcher</code> for non-transactional reads.
 * Resolves with the first matching transition where the transaction
 * is <code>null</code> and the entry is visible (the entry's current
 * state is ignored).
 */
class ReadWatcher extends SingletonQueryWatcher {
    /**
     * Create a new <code>ReadWatcher</code>.
     * @param expiration the initial expiration time
     *        for this <code>TransitionWatcher</code> in 
     *        milliseconds since the beginning of the epoch.
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     */
    ReadWatcher(long expiration, long timestamp, long startOrdinal) {
	super(expiration, timestamp, startOrdinal);
    }

    boolean isInterested(EntryTransition transition, long ordinal) {
	/* Note, !isResolved() without the lock will result only in
	 * false positives, not false negatives - it will only
	 * cause isInterested() to return false if we are resolved,
	 * we may still return true if we are resolved though.
	 */
	return (ordinal>startOrdinal) && !isResolved() && 
	    transition.isVisible() && (transition.getTxn() == null);
    }

    synchronized void process(EntryTransition transition, long now) {
	// If isInterested was true then we can just resolve
	if (isResolved())
	    return; // Already done.

	// As long as it existed at one time we can return it
	resolve(transition.getHandle(), null);
    }

    synchronized boolean catchUp(EntryTransition transition, long now) {
	if (isResolved())
	    return true;

	if (transition.isVisible() && (transition.getTxn() == null)) {
	    /* As long as it existed at one time and we could have seen it
	     * we can return it
	     */
	    resolve(transition.getHandle(), null);
	    return true;
	}

	return false;
    }
}
