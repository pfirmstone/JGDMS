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
 * Base class for objects that represent interest in particular entry
 * visibility transitions.  Each <code>TransitionWatcher</code> has a
 * time stamp and when compared <code>TransitionWatcher</code>s with a
 * lower time stamp are considered less than those with a larger time
 * stamp. If two <code>TransitionWatcher</code> have the same time
 * stamp their tie breaker values are used.
 * <p>
 * Unless otherwise noted, the <code>addTemplateHandle</code> method must
 * be called at least once before any other method.
 * Some implementations of this call only support calling
 * <code>addTemplateHandle</code> once on any particular instance.
 * <p>
 * Unless otherwise noted, all the package protected methods
 * are thread safe.  
 */
abstract class TransitionWatcher implements Comparable {
    /** The time stamp for this object */
    final private long timestamp;

    /** The tie breaker for this object */
    final private long tiebreaker;

    /**
     * The transitions with higher ordinals occurred
     * after the operation associated with this
     * watcher was started. Package protected
     * but should only be accessed by subclasses.
     */
    final long startOrdinal;

    /** Next tiebreaker to use */
    private static long nextTiebreaker = 0;

    /** Lock for nextTiebreaker */
    private static Object nextTiebreakerLock = new Object();

    /**
     * Create a new <code>TransitionWatcher</code>. 
     * @param timestamp the value that is used
     *        to sort <code>TransitionWatcher</code>s.
     * @param startOrdinal the highest ordinal associated
     *        with operations that are considered to have occurred 
     *        before the operation associated with this watcher.
     */
    TransitionWatcher(long timestamp, long startOrdinal) {
	this.timestamp = timestamp;
	this.startOrdinal = startOrdinal;

	synchronized (nextTiebreakerLock) {
	    tiebreaker = nextTiebreaker++;
	}

    }

    /**
     * Associate a <code>TemplateHandle</code> with this object. An
     * implementation may support calling this method only once on 
     * a given instance.
     * @param h The <code>TemplateHandle</code> associated
     *          with this watcher.
     * @return <code>true</code> if the handle was succfully added,
     *         and <code>false</code> if the watcher has already
     *         been removed.
     * @throws NullPointerException if <code>h</code> is 
     *        <code>null</code> 
     */
    abstract boolean addTemplateHandle(TemplateHandle h);

    /**
     * Compares this object with another. If the other object is not a
     * <code>TransitionWatcher</code> a
     * <code>ClassCastException</code> will be thrown. The object with
     * the lower time stamp will be considered less than the other. If they 
     * both have the same time stamp the object will the lower
     * tie breaker will be less than the other. If both have the same
     * tie breaker and time stamp they will be considered equal.
     * <p>
     * This method may be called before <code>addTemplateHandle</code> 
     * is called.
     *
     * @return a negative integer, zero, or a positive integer as this
     *         object is less than, equal to, or greater than the specified
     *         object.
     * @throws ClassCastException if <code>o</code> is not
     *         a <code>TransitionWatcher</code>.  
     */
    public int compareTo(Object o) {
	final TransitionWatcher other = (TransitionWatcher)o;
	if (timestamp < other.timestamp)
	    return -1;

	if (timestamp > other.timestamp)
	    return 1;

	if (tiebreaker < other.tiebreaker)
	    return -1;

	if (tiebreaker > other.tiebreaker)
	    return 1;

	assert o == this : 
	    "Two TransitionWatchers with the same tiebreaker"; 
	return 0;
    }

    /* Since every TransitionWatcher has its own tie breaker
     * we can use the default hashCode and equals.
     */

    /**
     * Get the expiration time of this object.  This method may be
     * called before <code>addTemplateHandle</code> is called.
     * Assumes locking is handled by the caller.
     * @return The time (in milliseconds since the beginning of
     *         the epoch) when this watcher is no longer relevant.
     */
    abstract public long getExpiration();

    /**
     * Remove this watcher from the system if it
     * has expired. Responsible
     * for calling the <code>remove</code> method
     * of the associated <code>TemplateHandle</code>.
     * This method can be called more than once,
     * though second and subsequent calls may have no effect.
     * @param now An estimate of the current time that must be
     *            less than or equal to the current time.
     */
    abstract void removeIfExpired(long now);

    /**
     * Return <code>true</code> if this watcher cares about a given
     * visibility transition. Assumes the transitioning entry matches
     * the template in the <code>TemplateHandle</code> associated with
     * this watcher. This method should return a value even if the
     * expiration time has been reached or <code>remove</code> has
     * been called. This call should not obtain any locks.
     * @param transition A <code>EntryTransition</code> that
     *              describes the transition and what
     *              entry is transitioning. This method
     *              will assume that <code>transition.getHandle</code>
     *              returns a non-null value.
     * @param ordinal The ordinal associated with <code>transition</code>.
     * @return <code>true</code> if this watcher is interested
     *         in the indicated transition and <code>false</code> 
     *         otherwise.  
     * @throws NullPointerException if <code>transition</code> is 
     *         <code>null</code>.  
     */
    abstract boolean isInterested(EntryTransition transition, long ordinal);

    /**
     * Process the given transition. Assumes the passed entry matches the
     * template in the <code>TemplateHandle</code> associated with this
     * watcher and that <code>isInterested</code> returned
     * <code>true</code>. This call may make changes to the passed
     * <code>EntryHandle</code> that will prevent it from being used by
     * other watchers. If <code>remove</code> has been called or the
     * expiration time of this watcher has passed, this call should have no
     * effect. This call may cause the watcher to be removed.
     *
     * @param transition A <code>EntryTransition</code> that
     *              describes the transition and what
     *              entry is transitioning. This method
     *              will assume that <code>transition.getHandle</code>
     *              returns a non-null value.
     * @param now   An estimate of the current time (not the time
     *              when the event occured).
     * @throws NullPointerException if <code>transition</code> is 
     *         <code>null</code>.  
     */
    abstract void process(EntryTransition transition, long now);
} 
