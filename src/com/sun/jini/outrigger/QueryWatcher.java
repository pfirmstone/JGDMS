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
 * Subclass of <code>TransitionWatcher</code> for blocking queries.
 * <code>QueryWatcher</code>s are waiting for a set of conditions (time,
 * transitions, etc.) that will <em>resolve</em> the query.  Threads can
 * block on resolution by calling the <code>waitOnResolution</code> method.
 * Once <code>waitOnResolution</code> than can use subclass specific 
 * methods to obtain the result.
 * <p>
 * Resolution may involve <em>capturing</em> one or more entries (that
 * is locking or removing an entry from the space). It is important
 * that a <code>QueryWatcher</code> never capture an entry after the
 * watcher enters the resolved state, since that could allow a caller
 * to believe that the call failed, when it fact it locked or removed
 * an entry and/or in entries being removed/locked that are never
 * returned to the client. Put another way, the decision to capture an
 * entry has to be done atomically with the check that ensures that
 * the related query is still unresolved.  
 */
abstract class QueryWatcher extends TransitionWatcher {
    /** When this query ends */
    private final long expiration;

    /**
     * Create a new <code>QueryWatcher</code>.
     * @param expiration the initial expiration time
     *        for this <code>QueryWatcher</code> in 
     *        milliseconds since the beginning of the epoch.
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     */
    QueryWatcher(long expiration, long timestamp, long startOrdinal) 
    {
	super(timestamp, startOrdinal);
	this.expiration = expiration;
    }

    public long getExpiration() {
	return expiration;
    }

    /**
     * Process a transition which was posted before the watcher was
     * placed in <code>TransitionWatchers</code> object. Assumes that
     * the entry in the transition matches matches the template in the
     * <code>TemplateHandle</code> associated with this watcher. Does
     * not assume <code>isInterested</code> has been called.
     * @param transition A <code>EntryTransition</code> that
     *              describes the transition and what
     *              entry is transitioning. This method
     *              will assume that <code>transition.getHandle</code>
     *              returns a non-null value.
     * @param now   An estimate of the current time (not the time
     *              when the event occured).
     * @return <code>true</code> if the query has been resolved, and
     *         <code>false</code> otherwise. Note, even if this
     *         call returns <code>true</code>, <code>waitOnResolution</code>
     *         must still be called.
     *        
     * @throws NullPointerException if <code>transition</code> is 
     *         <code>null</code>.  
     */
    abstract boolean catchUp(EntryTransition transition, long now);
    
    /**
     * This method does nothing. Since each <code>QueryWatcher</code>
     * has a thread that blocks until the expiration time is reached
     * it simpler to do the removal there instead of in the
     * reaping thread.
     * @param now An estimate of the current time that must be
     *            less than or equal to the current time.
     */
    void removeIfExpired(long now) { }
        
    /**
     * Block until the query this object represents is resolved.  If the
     * query is already resolved, return immediately. This method must be
     * called even if it is know that the query has been resolved.
     * This method should be called exactly once.     
     */
    abstract void waitOnResolution() throws InterruptedException;

    /** 
     * Returns <code>true</code> if this query has been resolved.  If the
     * calling thread is owns the lock on this object the answer is
     * definitive. If the lock is not held only a <code>true</code> answer
     * can be considered definitive.
     * @return <code>true</code> if the query has been
     * resolved, <code>false</code> otherwise.
     */
    abstract boolean isResolved();
}

