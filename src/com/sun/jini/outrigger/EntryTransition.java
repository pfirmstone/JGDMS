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
 * Object that that represents a visibility transition of some
 * entry.
 */
class EntryTransition {
    /** The <code>EntryHandle</code> of the entry that changed */
    final private EntryHandle handle;

    /** 
     * If this is a transition to visibility and/or availability, what
     * transaction the entry is now available/visible to. If
     * <code>null</code> the entry transitioned to a state where it is
     * available/visible to all.
     */
    final private TransactableMgr txn;

    /**
     * <code>true</code> if the entry is transitioning from a state
     * where it could not be taken to one where it could be,
     * <code>false</code> otherwise.  
     */
    final private boolean available;

    /**
     * <code>true</code> if the entry is transitioning from a state
     * where it could not be read to one where it could be,
     * <code>false</code> otherwise.  
     */
    final private boolean visible;

    /**
     * <code>true</code> if the transition is a write or
     * the commit of a write, <code>false</code> otherwise.
     */
    final private boolean newEntry;

    /**
     * Set of watchers that need to remember that they have seen 
     * this transition already
     */
    final private Set watchers = new java.util.HashSet();

    /** 
     * Create a new <code>EntryTransition</code> object
     * with the specified content.
     * @param handle The <code>EntryHandle</code> of the 
     *               the entry who's visibility is changing.
     * @param txn    If the entry is only visible in a particular
     *               transaction, the <code>Txn</code> for that
     *               transaction, and <code>null</code> otherwise.
     * @param available <code>true</code> if the entry is
     *               transitioning from a state where it could not be
     *               taken to one where it could be,
     *               <code>false</code> otherwise.
     * @param visible <code>true</code> if the entry is transitioning
     *               from a state where it could not be read to one
     *               where it could be, <code>false</code> otherwise.
     * @param newEntry <code>true</code> if the entry is available and
     *               the transition is because of a write or a write
     *               that is being committed, <code>false</code>
     *               otherwise.
     * @throws NullPointerException if handle is null.  
     */
    EntryTransition(EntryHandle handle, TransactableMgr txn, boolean available,
		    boolean visible, boolean newEntry)
    {
	if (handle == null)
	    throw new NullPointerException("entry must be non-null");

	assert (!visible || (available && visible));
	assert (!newEntry || (available && visible));
	
	this.handle = handle;
	this.txn = txn;
	this.available = available;
	this.visible = visible;
	this.newEntry = newEntry;
    }

    /**
     * Return the <code>EntryHandle</code> for entry undergoing the
     * visibility transition.
     * @return The handle for the entry undergoing the visibility
     *         transition.  
     */
    EntryHandle getHandle() {
	return handle;
    }

    /**
     * If this is a transition to visibility and/or availability, what
     * transaction the entry is now available/visible to. If
     * <code>null</code> the entry transitioned to a state where it is
     * available/visible to all.
     * @return the transaction the associated entry is now
     *         available/visible too
     */
    TransactableMgr getTxn() {
	return txn;
    }

    /**
     * Returns <code>true</code> if this is a transition from a state
     * where the entry could not be taken to one where it could be.
     * Otherwise returns <code>false</code>. Note, <code>isVisible</code>
     * returning <code>true</code> implies that <code>isAvailable</code>
     * will also return <code>true</code>.
     * @return <code>true</code> if this is a transition from a state
     * where the entry could not be taken to one where it could be.
     * Otherwise returns <code>false</code>
     */
    boolean isAvailable() {
	return available;
    }

    /**
     * Returns <code>true</code> if this is a transition from a state
     * where the entry could not be read to one where it could be.
     * Otherwise returns <code>false</code>. Note, <code>isVisible</code>
     * returning <code>true</code> implies that <code>isAvailable</code>
     * will also return <code>true</code>.
     * @return <code>true</code> if this is a transition from a state
     * where the entry could not be read to one where it could be.
     * Otherwise returns <code>false</code>
     */
    boolean isVisible() {
	return visible;
    }

    /**
     * Return <code>true</code> if this transition represents a
     * new entry becoming visible, otherwise return <code>false</code>.
     * @return Return <code>true</code> if this transition represents a
     * new entry becoming visible, otherwise return <code>false</code>.
     */
    boolean isNewEntry() {
	return newEntry;
    }

    /**
     * Record that a given watcher has processed this Transition and
     * does not need to again. Assumes that the <code>processedBy</code> 
     * and <code>hasProcessed</code> methods are only called from
     * a single thread. <p>
     * 
     * Note, we never remove watchers from the set of watchers that
     * have processed this transition since in general we would only
     * know to remove a watcher when this transition had already
     * visited every template, at which point the entire
     * EntryTransition object is eligible for GC (internally we could
     * use a weak set, but once watchers start processing an
     * EntryTransition, its life time is limited so it hardly seems
     * worth the trouble) 
     */
    void processedBy(TransitionWatcher w) {
	watchers.add(w);
    }

    /**
     * Return <code>true</code> if the passed watcher has been passed
     * to <code>processedBy</code>. Assumes that the <code>processedBy</code> 
     * and <code>hasProcessed</code> methods are only called from
     * a single thread.
     */
    boolean hasProcessed(TransitionWatcher w) {
	return watchers.contains(w);
    }
}

