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
import java.util.Vector;

/**
 * <code>TemplateHandle</code> associates one or more
 * <code>TransitionWatcher</code>s with a template.
 * Unless otherwise noted all methods are thread safe.
 */
class TemplateHandle extends BaseHandle {
    /**
     * A cache of the <code>EntryHandleTmplDesc</code> indexed
     * by the number of fields.
     */
    final private Vector descs = new Vector();

    /**

     * The watchers. We use a <code>HashSet</code> because we will
     * probably do a fair number of removals for each traversal and
     * the number of watchers managed by one <code>TemplateHandle</code>
     * will probably never get very large. If this does become an
     * issue making <code>TransitionWatcher</code> extend
     * <code>FastList.Node</code> and using a <code>FastList</code>
     * here would probably be a good choice (though that would require
     * changing <code>FastList</code> to support overlapping traversals
     * of different lists from the same thread.)
     */
    final private Set watchers = new java.util.HashSet();

    /**
     * The <code>WatchersForTemplateClass</code> this object
     * belongs to.
     */
    final private WatchersForTemplateClass owner;

    /**
     * Create a handle for the template <code>tmpl</code>.
     */
    TemplateHandle(EntryRep tmpl, WatchersForTemplateClass owner) {
	super(tmpl);
	this.owner = owner;
    }

    /**
     * Return the description for the given field count.
     */
    //!! Since the mask/hash algorithm tops out at a certain number of fields,
    //!! we could avoid some overhead by topping out at the same count.
    EntryHandleTmplDesc descFor(int numFields) {
	/* Since setSize can truncate, test and set need to be atomic.
	 * Hold the lock after setting the size so don't calculate
	 * a given desc more than once (though that is only an optimization)
	 */
	synchronized (descs) {
	    // Make sure descs is big enough
	    if (numFields >= descs.size())
		descs.setSize(numFields + 1);

	    // Do we have a cached value?
	    EntryHandleTmplDesc desc = 
		(EntryHandleTmplDesc)descs.elementAt(numFields);

	    if (desc == null) {
		// None in cache, calculate one
		desc = EntryHandle.descFor(rep(), numFields);
		descs.setElementAt(desc, numFields);
	    }
	    return desc;
	}
    }

    /**
     * Return <code>true</code> if this template matches the given entry.
     */
    boolean matches(EntryRep entry) {
	return rep().matches(entry);
    }

    /** 
     * Add a watcher to this handle. Assumes that the handle has not
     * been removed from its <code>FastList</code> and that
     * the caller owns the lock on <code>this</code>.
     * @param watcher the watcher to be added.
     * @throws NullPointerException if watcher is <code>null</code>.
     */
    void addTransitionWatcher(TransitionWatcher watcher) {
	assert Thread.holdsLock(this) : 
	    "addTransitionWatcher() called without lock";

	if (watcher == null)
	    throw new NullPointerException("Watcher can not be null");

	assert !removed() : "Added watcher to a removed TemplateHandle";
	watchers.add(watcher);
    }

    /**
     * Remote a watcher from this handle. Does nothing 
     * if the specified watcher is not associated with
     * this <code>TemplateHandle</code>.
     * @param watcher the watcher to be removed.
     * @throws NullPointerException if watcher is <code>null</code>.
     */
    synchronized void removeTransitionWatcher(TransitionWatcher watcher) {
	if (watcher == null)
	    throw new NullPointerException("Watcher can not be null");
	watchers.remove(watcher);
    }

    /**
     * Iterate over the watchers associated with 
     * this handle calling <code>isInterested</code> on each
     * and if it returns <code>true</code> adding the watcher to the
     * passed set.
     *
     * @param set The set to accumulate interested watchers
     *            into.
     * @param transition The transition being processed.
     * @param ordinal The ordinal associated with <code>transition</code>.
     * @throws NullPointerException if either argument is <code>null</code>.
     */
    synchronized void collectInterested(Set set, EntryTransition transition,
					long ordinal) 
    {
	final Iterator i = watchers.iterator();
	while (i.hasNext()) {
	    final TransitionWatcher w = (TransitionWatcher)i.next();
	    if (w.isInterested(transition, ordinal)) {
		set.add(w);
	    }
	}
    }

    /**
     * Return the <code>OutriggerServerImpl</code> this 
     * handle is part of.
     * @return The <code>OutriggerServerImpl</code> this 
     * handle is part of.
     */
    OutriggerServerImpl getServer() {
	return owner.getServer();
    }

    /**
     * Visit each <code>TransitionWatcher</code> and check to see if
     * it has expired, removing it if it has.
     * @param now an estimate of the current time expressed as
     *            milliseconds since the beginning of the epoch.
     */
    void reap(long now) {
	/* This could take a while, instead of blocking all other
	 * access, clone the contents of watchers and
	 * iterate down the clone (we don't do this too often and
	 * watchers should never be that big so a shallow copy
	 * should not be that bad. If it does get bad may
	 * need to switch to a FastList for watchers.
	 * (we don't do this for collection of interested watchers
	 * because calls to isInterested() are designed to be cheap,
	 * calls to removeIfExpired() will grab locks and could
	 * write to disk))
	 */
	final TransitionWatcher content[];
	synchronized (this) {
	    content = new TransitionWatcher[watchers.size()];
	    watchers.toArray(content);
	}

	for (int i=0; i<content.length; i++) {
	    content[i].removeIfExpired(now);
	}
    }

    /**
     * Return <code>true</code> if there are no watchers associated
     * with this object and <code>false</code> otherwise. Assumes
     * the call holds the lock on <code>this</code>.
     * @return <code>true</code> if there are no watchers in this handle.
     */
    boolean isEmpty() {
	assert Thread.holdsLock(this) : "isEmpty() called without lock";
	return watchers.isEmpty();
    } 
}
