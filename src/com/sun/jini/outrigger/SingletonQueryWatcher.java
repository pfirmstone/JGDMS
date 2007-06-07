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
 * Subclass of <code>QueryWatcher</code> for singleton queries. Most
 * of the usage model is laid out in <code>QueryWatcher</code> except
 * how the result of the query is obtained from the
 * watcher. <code>SingletonQueryWatcher</code> defines the
 * <code>resolvedWithEntry</code> and
 * <code>resolvedWithThrowable</code> methods which can be used to
 * obtain the entry or throwable the query was resolved with (and in
 * the case of <code>IfExistsWatcher</code>s
 * <code>isConflictSetEmpty</code>).
 */
abstract class SingletonQueryWatcher extends QueryWatcher {
    /** Set to true when this query is resolved */
    private boolean resolved = false;

    /** If resolved and an entry was found the entry to return */
    private EntryHandle handle;

    /** 
     * If resolved and an exception needs to be thrown the exception
     * to throw
     */
    private Throwable toThrow;

    /**
     * The <code>TemplateHandle</code> associated with this
     * watcher.
     */
    private TemplateHandle owner;

    /**
     * Create a new <code>SingletonQueryWatcher</code>.
     * @param expiration the initial expiration time
     *        for this <code>TransitionWatcher</code> in 
     *        milliseconds since the beginning of the epoch.
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     */
    SingletonQueryWatcher(long expiration, long timestamp, long startOrdinal) {
	super(expiration, timestamp, startOrdinal);
    }

    /**
     * Associate a <code>TemplateHandle</code> with this object.  May
     * only be called once on any given <code>SingletonQueryWatcher</code> 
     * instance.
     *
     * @param h The <code>TemplateHandle</code> associated
     *          with this watcher.
     * @return <code>true</code> if the handle was succfully added,
     *         and <code>false</code> if the watcher has already
     *         been removed.
     * @throws NullPointerException if <code>h</code> is 
     *        <code>null</code> 
     */
    boolean addTemplateHandle(TemplateHandle h) {
	if (h == null) 
	    throw 
		new NullPointerException("TemplateHandle must be non-null");

	if (owner != null)
	    throw new AssertionError("Can only call addTemplateHandle once");

	owner = h;
	return true;
    }
        
    /**
     * Block until the query this object represents is resolved.  If the
     * query is already resolved, return immediately. This method must be
     * called even if it is know that the query has been resolved.
     * This method should be called exactly once.     
     */
    void waitOnResolution() throws InterruptedException {
	final TemplateHandle owner;
	synchronized (this) {
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

	    owner = this.owner;
	    // Signal that we (well will be soon) removed from owner
	    this.owner = null;
	}

	// If we are called exactly once, owner should never be null
	owner.removeTransitionWatcher(this);	
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
    synchronized EntryHandle resolvedWithEntry() {
	if (!resolved)
	    throw new IllegalStateException("Query not yet resolved");

	return handle;
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

    /** 
     * Returns <code>true</code> if this query has been resolved.  If the
     * calling thread is owns the lock on this object the answer is
     * definitive. If the lock is not held only a <code>true</code> answer
     * can be considered definitive.
     * @return <code>true</code> if the query has been
     * resolved, <code>false</code> otherwise.
     */
    boolean isResolved() {
	return resolved;
    }

    /**
     * Mark this query as resolved. This method assumes
     * the calling thread own the lock on this object.
     * This method is intended only for use by subclasses.
     * @param handle  If being resolved by finding an entry
     *              the entry which was found and that should be returned
     *              by <code>resolvedWithEntry</code>. Otherwise should be
     *              <code>null</code>.  May only be non-null if throwable
     *              is <code>null</code>.
     * @param throwable If being resolved by an exception
     *              the throwable to be thrown and that should be returned
     *              by <code>resolvedWithThrowable</code> otherwise should
     *              be <code>null</code>.  May only be non-null if entry is
     *              <code>null</code>.
     * @throws IllegalArgumentException if both 
     *         <code>entry</code> and <code>throwable</code>
     *         are non-null.
     * @throws IllegalStateException if the query has already
     *         been resolved.
     */
    void resolve(EntryHandle handle, Throwable throwable) {
	assert Thread.holdsLock(this) : "Caller of resolve() must hold lock";

	if (resolved)
	    throw new IllegalStateException(
		"Can't call resolve on a resolved query ");

	if ((this.handle != null) || (toThrow != null))
	    throw new IllegalStateException(
		"At lease one argument must be null");

	resolved = true;
	this.handle = handle;
	toThrow = throwable;
	notifyAll();
    }    

    /**
     * Method to give sub-classes access to OutriggerServerImpl 
     * Assumes caller owns lock on <code>this</code> and that
     * this watcher has not been removed.
     */
    OutriggerServerImpl getServer() {
	assert Thread.holdsLock(this) : "getServer() called without lock";
	return owner.getServer();
    }
}
